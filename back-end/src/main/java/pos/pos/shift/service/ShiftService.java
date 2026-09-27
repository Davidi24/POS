package pos.pos.shift.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.audit.entity.AuditLog;
import pos.pos.audit.enums.AuditSource;
import pos.pos.audit.repository.AuditLogRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.shift.dto.ShiftDtos.*;
import pos.pos.shift.entity.Shift;
import pos.pos.shift.entity.ShiftBreak;
import pos.pos.shift.enums.*;
import pos.pos.shift.repository.ShiftRepository;
import pos.pos.user.entity.User;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ShiftService {
    private final ShiftRepository shifts;
    private final RestaurantScopeService scope;
    private final EntityManager em;
    private final AuditLogRepository audits;
    private static final UUID NONE = new UUID(0, 0);

    @Transactional(readOnly=true)
    public Board board(Authentication auth, UUID restaurantId, UUID branchId, LocalDate from, LocalDate to, boolean mine) {
        require(auth, mine ? "SHIFT_SELF" : "SHIFT_READ");
        Branch branch = scope.requireAccessibleBranch(auth, restaurantId, branchId);
        UUID actor = scope.currentUserId(auth);
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(30))) bad("Choose a date range of up to 31 days.");
        ZoneId zone = ZoneId.of(branch.getRestaurant().getTimezone());
        OffsetDateTime now = now();
        List<Shift> items = shifts.inWindow(restaurantId, branchId, mine ? actor : null,
                from.atStartOfDay(zone).toOffsetDateTime(), to.plusDays(1).atStartOfDay(zone).toOffsetDateTime(), PageRequest.of(0, 5001));
        if (items.size() > 5000) bad("Too many shifts in this range. Choose a shorter date range.");
        Shift current = has(auth, "SHIFT_SELF") ? shifts.findActive(actor).filter(s -> s.getBranch().getId().equals(branchId)).orElse(null) : null;
        List<Staff> staff = List.of();
        if (!mine) {
            List<User> roster = shifts.staff(restaurantId, branchId);
            Map<UUID, List<String>> roleNames = new HashMap<>();
            if (!roster.isEmpty()) shifts.roleNames(roster.stream().map(User::getId).toList()).forEach(row ->
                    roleNames.computeIfAbsent((UUID) row[0], ignored -> new ArrayList<>()).add((String) row[1]));
            staff = roster.stream().map(u -> new Staff(u.getId(), name(u), roleNames.getOrDefault(u.getId(), List.of()))).toList();
        }
        Shift eligible = has(auth, "SHIFT_SELF") ? shifts.eligible(actor, branchId, now, now.plusHours(2)).stream().findFirst().orElse(null) : null;
        return new Board(zone.getId(), now, items.stream().map(s -> item(s, now)).toList(), current == null ? null : item(current, now), staff, eligible == null ? null : item(eligible, now));
    }

    public Item schedule(Authentication auth, UUID restaurantId, UUID branchId, UUID id, Schedule request) {
        require(auth, "SHIFT_MANAGE");
        Branch branch = scope.requireAccessibleBranch(auth, restaurantId, branchId);
        User user = lockUser(request.userId());
        if (!Objects.equals(user.getRestaurantId(), restaurantId) || (user.getDefaultBranchId() != null && !user.getDefaultBranchId().equals(branchId))) bad("Choose a staff member who belongs to this branch.");
        validateWindow(request.scheduledStart(), request.scheduledEnd());
        Shift shift;
        String before = null;
        if (id == null) {
            if (request.scheduledEnd().isBefore(now())) bad("A new scheduled shift must end in the future.");
            shift = new Shift(); shift.setId(UUID.randomUUID()); shift.setRestaurant(branch.getRestaurant()); shift.setBranch(branch); shift.setUser(user);
            shift.setStatus(ShiftStatus.SCHEDULED); shift.setCreatedBy(scope.currentUserId(auth));
        } else {
            shift = requireShift(restaurantId, branchId, id);
            checkVersion(shift, request.version());
            if (!shift.getUser().getId().equals(user.getId())) bad("The assigned staff member cannot be changed. Cancel this shift and create a new one.");
            if (shift.getStatus() != ShiftStatus.SCHEDULED) conflict("Only shifts that have not started can be rescheduled.");
            before = snapshot(shift);
        }
        if (shifts.overlaps(user.getId(), id == null ? NONE : id, request.scheduledStart(), request.scheduledEnd()) > 0) conflict("This staff member already has a shift during that time.");
        shift.setScheduledStart(request.scheduledStart()); shift.setScheduledEnd(request.scheduledEnd()); shift.setNotes(request.notes());
        return save(auth, shift, id == null ? "SHIFT_SCHEDULED" : "SHIFT_RESCHEDULED", before, null);
    }

    public Item clockIn(Authentication auth, UUID restaurantId, UUID branchId, ClockIn request) {
        require(auth, "SHIFT_SELF");
        Branch branch = scope.requireAccessibleBranch(auth, restaurantId, branchId);
        User user = lockUser(scope.currentUserId(auth));
        if (!has(auth, "SHIFT_MANAGE") && user.getDefaultBranchId() != null && !user.getDefaultBranchId().equals(branchId)) forbidden();
        if (shifts.findActive(user.getId()).isPresent()) conflict("You already have an active shift. Refresh to see it, or return to the branch where you clocked in.");
        Shift shift;
        String before = null;
        OffsetDateTime now = now();
        UUID scheduledId = request.shiftId();
        if (scheduledId == null) scheduledId = shifts.eligible(user.getId(), branchId, now, now.plusHours(2)).stream().findFirst().map(Shift::getId).orElse(null);
        if (scheduledId == null) {
            shift = new Shift(); shift.setId(UUID.randomUUID()); shift.setRestaurant(branch.getRestaurant()); shift.setBranch(branch); shift.setUser(user); shift.setCreatedBy(user.getId());
        } else {
            shift = requireShift(restaurantId, branchId, scheduledId);
            if (!shift.getUser().getId().equals(user.getId())) forbidden();
            if (shift.getStatus() != ShiftStatus.SCHEDULED) conflict("This shift is no longer scheduled. Refresh and try again.");
            if (now.isBefore(shift.getScheduledStart().minusHours(2)) || now.isAfter(shift.getScheduledEnd())) bad("Clock in from two hours before your scheduled start until the scheduled end.");
            before = snapshot(shift);
        }
        shift.setStatus(ShiftStatus.OPEN); shift.setStartedAt(now);
        return save(auth, shift, "SHIFT_CLOCKED_IN", before, null);
    }

    public Item startBreak(Authentication auth, UUID restaurantId, UUID branchId, UUID id, StartBreak request) {
        require(auth, "SHIFT_SELF");
        Shift shift = ownedLocked(auth, restaurantId, branchId, id, false);
        checkVersion(shift, request.version());
        if (shift.getStatus() != ShiftStatus.OPEN) conflict("Start a break from an active shift.");
        // Paid breaks may only be selected by staff with shift-management permission.
        if (request.type() == ShiftBreakType.PAID_BREAK && !has(auth, "SHIFT_MANAGE")) forbidden();
        String before = snapshot(shift);
        ShiftBreak br = new ShiftBreak(); br.setId(UUID.randomUUID()); br.setBreakType(request.type()); br.setPaid(request.type() == ShiftBreakType.PAID_BREAK); br.setStartedAt(now());
        shift.addBreak(br); shift.setStatus(ShiftStatus.ON_BREAK);
        return save(auth, shift, "SHIFT_BREAK_STARTED", before, null);
    }

    public Item resume(Authentication auth, UUID restaurantId, UUID branchId, UUID id, Action request) {
        require(auth, "SHIFT_SELF");
        Shift shift = ownedLocked(auth, restaurantId, branchId, id, false); checkVersion(shift, request.version());
        if (shift.getStatus() != ShiftStatus.ON_BREAK) conflict("This shift is not on a break.");
        String before = snapshot(shift); finishBreak(shift, now()); shift.setStatus(ShiftStatus.OPEN);
        return save(auth, shift, "SHIFT_BREAK_ENDED", before, null);
    }

    public Item close(Authentication auth, UUID restaurantId, UUID branchId, UUID id, Action request) {
        Shift shift = ownedLocked(auth, restaurantId, branchId, id, true); checkVersion(shift, request.version());
        if (shift.getStatus() != ShiftStatus.OPEN && shift.getStatus() != ShiftStatus.ON_BREAK) conflict("Only an active shift can be ended.");
        if (!shift.getUser().getId().equals(scope.currentUserId(auth)) && (request.notes() == null || request.notes().isBlank())) bad("Enter a reason when ending another staff member's shift.");
        String before = snapshot(shift); OffsetDateTime now = now(); finishBreak(shift, now); shift.setEndedAt(now); shift.setStatus(ShiftStatus.CLOSED);
        // Net worked time is not split into legal/payroll overtime without a configured policy.
        shift.setRegularMinutes(Math.toIntExact(workedMinutes(shift, now))); shift.setOvertimeMinutes(0);
        return save(auth, shift, "SHIFT_CLOCKED_OUT", before, request.notes());
    }

    public Item finishSchedule(Authentication auth, UUID restaurantId, UUID branchId, UUID id, Action request, boolean missed) {
        require(auth, "SHIFT_MANAGE");
        Shift shift = ownedLocked(auth, restaurantId, branchId, id, true); checkVersion(shift, request.version());
        if (shift.getStatus() != ShiftStatus.SCHEDULED) conflict("Only a scheduled shift can be cancelled or marked missed.");
        if (request.notes() == null || request.notes().isBlank()) bad("Enter a reason for this change.");
        if (missed && shift.getScheduledEnd().isAfter(now())) bad("Wait until the scheduled shift has ended before marking it missed.");
        String before = snapshot(shift); shift.setStatus(missed ? ShiftStatus.MISSED : ShiftStatus.CANCELLED);
        return save(auth, shift, missed ? "SHIFT_MISSED" : "SHIFT_CANCELLED", before, request.notes());
    }

    public Item correct(Authentication auth, UUID restaurantId, UUID branchId, UUID id, Correction request) {
        require(auth, "SHIFT_MANAGE");
        Shift shift = ownedLocked(auth, restaurantId, branchId, id, true); checkVersion(shift, request.version());
        if (!Set.of(ShiftStatus.CLOSED, ShiftStatus.SCHEDULED, ShiftStatus.MISSED).contains(shift.getStatus())) conflict("End this shift before correcting its attendance.");
        validateWindow(request.startedAt(), request.endedAt());
        if (request.endedAt().isAfter(now())) bad("The clock-out time cannot be in the future.");
        if (shift.getBreaks().stream().anyMatch(b -> b.getStartedAt().isBefore(request.startedAt()) || b.getEndedAt() == null || b.getEndedAt().isAfter(request.endedAt()))) bad("The corrected time must include all recorded breaks.");
        if (shifts.attendanceOverlaps(shift.getUser().getId(), id, request.startedAt(), request.endedAt()) > 0) conflict("The corrected attendance overlaps another shift.");
        String before = snapshot(shift); shift.setStartedAt(request.startedAt()); shift.setEndedAt(request.endedAt()); shift.setStatus(ShiftStatus.CLOSED);
        shift.setRegularMinutes(Math.toIntExact(workedMinutes(shift, request.endedAt()))); shift.setOvertimeMinutes(0);
        return save(auth, shift, "SHIFT_ATTENDANCE_CORRECTED", before, request.reason());
    }

    private Shift ownedLocked(Authentication auth, UUID restaurantId, UUID branchId, UUID id, boolean allowManager) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        Shift shift = requireShift(restaurantId, branchId, id);
        boolean owner = shift.getUser().getId().equals(scope.currentUserId(auth));
        if (!(owner && has(auth, "SHIFT_SELF")) && !(allowManager && has(auth, "SHIFT_MANAGE"))) forbidden();
        // All mutations for a staff member lock the same user row, preventing duplicate clock-ins and schedule races.
        lockUser(shift.getUser().getId()); em.refresh(shift);
        return shift;
    }
    private User lockUser(UUID id) {
        User user = em.find(User.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (user == null || user.getDeletedAt() != null || !user.isActive()) bad("This staff member is no longer active.");
        return user;
    }
    private Shift requireShift(UUID restaurantId, UUID branchId, UUID id) {
        return shifts.findById(id).filter(s -> s.getRestaurant().getId().equals(restaurantId) && s.getBranch().getId().equals(branchId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found."));
    }
    private Item save(Authentication auth, Shift shift, String action, String before, String reason) {
        UUID actor = scope.currentUserId(auth); shift.setUpdatedBy(actor); shifts.saveAndFlush(shift);
        AuditLog log = new AuditLog(); log.setRestaurant(shift.getRestaurant()); log.setBranch(shift.getBranch()); log.setActorUser(em.getReference(User.class, actor));
        log.setSource(AuditSource.API); log.setEntityType("SHIFT"); log.setEntityId(shift.getId()); log.setAction(action); log.setSummary(action.replace('_', ' '));
        log.setBeforeState(before); log.setAfterState(snapshot(shift)); log.setMetadataPayload(reason); audits.save(log);
        return item(shift, now());
    }
    private static void finishBreak(Shift shift, OffsetDateTime now) {
        shift.getBreaks().stream().filter(b -> b.getEndedAt() == null).forEach(b -> b.setEndedAt(now));
    }
    static long breakMinutes(Shift s, OffsetDateTime now, boolean unpaidOnly) {
        return s.getBreaks().stream().filter(b -> !unpaidOnly || !b.isPaid())
            .mapToLong(b -> Math.max(0, Duration.between(b.getStartedAt(), b.getEndedAt() == null ? now : b.getEndedAt()).getSeconds())).sum() / 60;
    }
    static long workedMinutes(Shift s, OffsetDateTime now) {
        if (s.getStartedAt() == null) return 0;
        long elapsed = Math.max(0, Duration.between(s.getStartedAt(), s.getEndedAt() == null ? now : s.getEndedAt()).getSeconds());
        long unpaid = s.getBreaks().stream().filter(b -> !b.isPaid()).mapToLong(b -> Math.max(0, Duration.between(b.getStartedAt(), b.getEndedAt() == null ? now : b.getEndedAt()).getSeconds())).sum();
        return Math.max(0, elapsed - unpaid) / 60;
    }
    static Item item(Shift s, OffsetDateTime now) {
        return new Item(s.getId(), s.getVersion(), s.getUser().getId(), name(s.getUser()), s.getStatus(), s.getScheduledStart(), s.getScheduledEnd(), s.getStartedAt(), s.getEndedAt(), workedMinutes(s, now), breakMinutes(s, now, false), s.getNotes(), s.getBreaks().stream().map(b -> new Break(b.getId(), b.getBreakType(), b.isPaid(), b.getStartedAt(), b.getEndedAt())).toList());
    }
    private static String snapshot(Shift s) { return "status=" + s.getStatus() + "; scheduled=" + s.getScheduledStart() + "/" + s.getScheduledEnd() + "; actual=" + s.getStartedAt() + "/" + s.getEndedAt() + "; user=" + s.getUser().getId() + "; notes=" + s.getNotes() + "; breaks=" + s.getBreaks().stream().map(b -> b.getId() + ":" + b.getBreakType() + ":" + b.isPaid() + ":" + b.getStartedAt() + "/" + b.getEndedAt()).toList(); }
    private static String name(User u) { return (Objects.toString(u.getFirstName(), "") + " " + Objects.toString(u.getLastName(), "")).trim(); }
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private static boolean has(Authentication auth, String permission) { return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission)); }
    private static void require(Authentication auth, String permission) { if (!has(auth, permission) && !has(auth, "SHIFT_MANAGE")) forbidden(); }
    private static void checkVersion(Shift s, long version) { if (s.getVersion() != version) conflict("This shift changed on another device. Refresh and try again."); }
    static void validateWindow(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null || !end.isAfter(start) || Duration.between(start, end).compareTo(Duration.ofHours(24)) > 0) bad("End time must be after start time, with a maximum shift length of 24 hours.");
    }
    private static void forbidden() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to change this shift."); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
