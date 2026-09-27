package pos.pos.shift.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.audit.entity.AuditLog;
import pos.pos.audit.repository.AuditLogRepository;
import pos.pos.restaurant.entity.*;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.shift.dto.ShiftDtos.*;
import pos.pos.shift.entity.*;
import pos.pos.shift.enums.*;
import pos.pos.shift.repository.ShiftRepository;
import pos.pos.user.entity.User;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {
    @Mock ShiftRepository repository;
    @Mock RestaurantScopeService scope;
    @Mock EntityManager em;
    @Mock AuditLogRepository audits;
    ShiftService service;
    UUID r = UUID.randomUUID(), b = UUID.randomUUID(), actor = UUID.randomUUID(), other = UUID.randomUUID();
    Restaurant restaurant; Branch branch; User user; Shift shift;
    Authentication self, manager;

    @BeforeEach void setup() {
        service = new ShiftService(repository, scope, em, audits);
        restaurant = new Restaurant(); restaurant.setId(r); restaurant.setTimezone("Europe/Berlin");
        branch = new Branch(); branch.setId(b); branch.setRestaurant(restaurant);
        user = user(actor); shift = new Shift(); shift.setId(UUID.randomUUID()); shift.setRestaurant(restaurant); shift.setBranch(branch); shift.setUser(user); shift.setStatus(ShiftStatus.OPEN); shift.setVersion(3); shift.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
        self = auth("SHIFT_SELF"); manager = auth("SHIFT_SELF", "SHIFT_READ", "SHIFT_MANAGE");
        lenient().when(scope.currentUserId(any())).thenReturn(actor);
        lenient().when(scope.requireAccessibleBranch(any(Authentication.class), eq(r), eq(b))).thenReturn(branch);
        lenient().when(em.find(eq(User.class), eq(actor), eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(user);
        lenient().when(em.getReference(eq(User.class), eq(actor))).thenReturn(user);
        lenient().when(repository.findById(shift.getId())).thenReturn(Optional.of(shift));
        lenient().when(repository.saveAndFlush(any())).thenAnswer(call -> { Shift s = call.getArgument(0); s.setVersion(s.getVersion()+1); return s; });
    }
    private User user(UUID id) { User u = new User(); u.setId(id); u.setRestaurantId(r); u.setDefaultBranchId(b); u.setActive(true); u.setFirstName("Test"); u.setLastName("Staff"); return u; }
    private Authentication auth(String... permissions) { return new UsernamePasswordAuthenticationToken("test", "", Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()); }
    private void status(int expected, org.junit.jupiter.api.function.Executable action) { assertEquals(expected, assertThrows(ResponseStatusException.class, action).getStatusCode().value()); }

    @Test void waiterCannotReadTeamRoster() { status(403, () -> service.board(self, r,b,LocalDate.now(),LocalDate.now(),false)); verifyNoInteractions(repository); }
    @Test void waiterCannotSchedule() { status(403, () -> service.schedule(self,r,b,null,new Schedule(actor,OffsetDateTime.now(),OffsetDateTime.now().plusHours(4),null,0))); }
    @Test void tenantScopeCheckedBeforeReadingShifts() {
        when(scope.requireAccessibleBranch(self,r,b)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        status(403, () -> service.board(self,r,b,LocalDate.now(),LocalDate.now(),true)); verifyNoInteractions(repository);
    }
    @Test void dateRangeIsBounded() { status(400, () -> service.board(self,r,b,LocalDate.now(),LocalDate.now().plusDays(32),true)); }
    @Test void selfCannotChangeAnotherStaffShift() { shift.setUser(user(other)); status(403, () -> service.close(self,r,b,shift.getId(),new Action(3,null))); verify(repository,never()).saveAndFlush(any()); }
    @Test void branchMismatchIsNotFound() { Branch elsewhere = new Branch(); elsewhere.setId(UUID.randomUUID()); shift.setBranch(elsewhere); status(404, () -> service.close(self,r,b,shift.getId(),new Action(3,null))); }
    @Test void staleVersionCannotEndShift() { status(409, () -> service.close(self,r,b,shift.getId(),new Action(1,null))); verify(repository,never()).saveAndFlush(any()); }
    @Test void duplicateClockInRejectedWithUserLock() {
        when(repository.findActive(actor)).thenReturn(Optional.of(shift));
        status(409, () -> service.clockIn(self,r,b,new ClockIn(null)));
        verify(em).find(User.class,actor,LockModeType.PESSIMISTIC_WRITE); verify(repository,never()).saveAndFlush(any());
    }
    @Test void scheduledClockInBeforeWindowRejected() {
        shift.setStatus(ShiftStatus.SCHEDULED); shift.setStartedAt(null); shift.setScheduledStart(OffsetDateTime.now().plusHours(4)); shift.setScheduledEnd(OffsetDateTime.now().plusHours(8));
        status(400, () -> service.clockIn(self,r,b,new ClockIn(shift.getId())));
    }
    @Test void breakStartsAndIsAudited() {
        Item result = service.startBreak(self,r,b,shift.getId(),new StartBreak(3,ShiftBreakType.MEAL));
        assertEquals(ShiftStatus.ON_BREAK,result.status()); assertEquals(1,result.breaks().size()); assertFalse(result.breaks().getFirst().paid());
        ArgumentCaptor<AuditLog> log = ArgumentCaptor.forClass(AuditLog.class); verify(audits).save(log.capture()); assertEquals("SHIFT_BREAK_STARTED",log.getValue().getAction()); assertNotNull(log.getValue().getBeforeState());
    }
    @Test void staffCannotSelfAwardPaidBreak() { status(403, () -> service.startBreak(self,r,b,shift.getId(),new StartBreak(3,ShiftBreakType.PAID_BREAK))); }
    @Test void secondBreakCannotStart() { shift.setStatus(ShiftStatus.ON_BREAK); status(409, () -> service.startBreak(self,r,b,shift.getId(),new StartBreak(3,ShiftBreakType.REST))); }
    @Test void clockOutClosesRunningBreak() {
        shift.setStatus(ShiftStatus.ON_BREAK); ShiftBreak br = new ShiftBreak(); br.setStartedAt(OffsetDateTime.now().minusMinutes(20)); shift.addBreak(br);
        Item result = service.close(self,r,b,shift.getId(),new Action(3,null)); assertEquals(ShiftStatus.CLOSED,result.status()); assertNotNull(br.getEndedAt()); assertTrue(result.workedMinutes() >= 99 && result.workedMinutes() <= 100);
    }
    @Test void completedShiftCannotResume() { shift.setStatus(ShiftStatus.CLOSED); status(409, () -> service.resume(self,r,b,shift.getId(),new Action(3,null))); }
    @Test void schedulingOverlapRejected() {
        var from = OffsetDateTime.now().plusDays(1); when(repository.overlaps(eq(actor),any(),eq(from),eq(from.plusHours(8)))).thenReturn(1L);
        status(409, () -> service.schedule(manager,r,b,null,new Schedule(actor,from,from.plusHours(8),null,0)));
    }
    @Test void scheduleHasNoInventedClockIn() {
        var from = OffsetDateTime.now().plusDays(1); Item result = service.schedule(manager,r,b,null,new Schedule(actor,from,from.plusHours(8),"Terrace",0));
        assertEquals(ShiftStatus.SCHEDULED,result.status()); assertNull(result.startedAt()); assertEquals(0,result.workedMinutes());
    }
    @Test void staffOutsideBranchCannotBeAssigned() { user.setDefaultBranchId(UUID.randomUUID()); var from = OffsetDateTime.now().plusDays(1); status(400, () -> service.schedule(manager,r,b,null,new Schedule(actor,from,from.plusHours(8),null,0))); }
    @Test void overnightAndDstDurationUseInstants() {
        var zone = ZoneId.of("Europe/Berlin");
        var start = ZonedDateTime.of(2026,10,24,22,0,0,0,zone).toOffsetDateTime();
        var end = ZonedDateTime.of(2026,10,25,6,0,0,0,zone).toOffsetDateTime();
        ShiftService.validateWindow(start,end); shift.setStartedAt(start); shift.setEndedAt(end);
        assertEquals(540,ShiftService.workedMinutes(shift,end));
    }
    @Test void unpaidBreaksExcludedPaidBreaksIncluded() {
        var start = OffsetDateTime.parse("2026-09-25T09:00:00Z"); shift.setStartedAt(start); shift.setEndedAt(start.plusHours(8));
        ShiftBreak paid = new ShiftBreak(); paid.setStartedAt(start.plusHours(2)); paid.setEndedAt(start.plusHours(2).plusMinutes(15)); paid.setPaid(true); shift.addBreak(paid);
        ShiftBreak unpaid = new ShiftBreak(); unpaid.setStartedAt(start.plusHours(4)); unpaid.setEndedAt(start.plusHours(4).plusMinutes(30)); shift.addBreak(unpaid);
        assertEquals(450,ShiftService.workedMinutes(shift,shift.getEndedAt())); assertEquals(45,ShiftService.breakMinutes(shift,shift.getEndedAt(),false));
    }
    @Test void missedCannotBeMarkedBeforeScheduledEnd() { shift.setStatus(ShiftStatus.SCHEDULED); shift.setScheduledEnd(OffsetDateTime.now().plusHours(2)); status(400, () -> service.finishSchedule(manager,r,b,shift.getId(),new Action(3,"Absent"),true)); }
    @Test void cancellationRequiresReasonAndRetainsRecord() {
        shift.setStatus(ShiftStatus.SCHEDULED); status(400, () -> service.finishSchedule(manager,r,b,shift.getId(),new Action(3,""),false));
        Item result = service.finishSchedule(manager,r,b,shift.getId(),new Action(3,"Staff requested leave"),false); assertEquals(ShiftStatus.CANCELLED,result.status()); verify(repository,never()).delete(any());
    }
    @Test void attendanceCannotExcludeRecordedBreaks() {
        var start = OffsetDateTime.now().minusDays(1); shift.setStatus(ShiftStatus.CLOSED); shift.setStartedAt(start); shift.setEndedAt(start.plusHours(8));
        ShiftBreak br = new ShiftBreak(); br.setStartedAt(start.plusHours(2)); br.setEndedAt(start.plusHours(3)); shift.addBreak(br);
        status(400, () -> service.correct(manager,r,b,shift.getId(),new Correction(3,start.plusHours(4),start.plusHours(8),"Correction")));
    }
    @Test void clockInAutomaticallyUsesEligibleSchedule() {
        shift.setStatus(ShiftStatus.SCHEDULED); shift.setStartedAt(null);
        shift.setScheduledStart(OffsetDateTime.now().minusMinutes(30)); shift.setScheduledEnd(OffsetDateTime.now().plusHours(6));
        when(repository.eligible(eq(actor),eq(b),any(),any())).thenReturn(List.of(shift));
        Item result=service.clockIn(self,r,b,new ClockIn(null));
        assertEquals(shift.getId(),result.id()); assertEquals(ShiftStatus.OPEN,result.status()); assertNotNull(result.startedAt());
    }
    @Test void correctedAttendanceChecksActualOverlap() {
        shift.setStatus(ShiftStatus.CLOSED); var start=OffsetDateTime.now().minusDays(1); var end=start.plusHours(8);
        when(repository.attendanceOverlaps(actor,shift.getId(),start,end)).thenReturn(1L);
        status(409, () -> service.correct(manager,r,b,shift.getId(),new Correction(3,start,end,"Verified times")));
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void missedShiftCanHaveForgottenAttendanceRecorded() {
        shift.setStatus(ShiftStatus.MISSED); shift.setStartedAt(null);
        var start=OffsetDateTime.now().minusDays(1); var end=start.plusHours(8);
        Item result=service.correct(manager,r,b,shift.getId(),new Correction(3,start,end,"Manager verified attendance"));
        assertEquals(ShiftStatus.CLOSED,result.status()); assertEquals(480,result.workedMinutes());
        verify(audits).save(any());
    }

}
