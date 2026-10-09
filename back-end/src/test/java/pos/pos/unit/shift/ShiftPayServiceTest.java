package pos.pos.unit.shift;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.audit.repository.AuditLogRepository;
import pos.pos.restaurant.entity.*;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.shift.dto.ShiftDtos.*;
import pos.pos.shift.entity.*;
import pos.pos.shift.enums.*;
import pos.pos.shift.repository.ShiftRepository;
import pos.pos.shift.repository.StaffPayRateRepository;
import pos.pos.shift.service.ShiftPayService;
import pos.pos.user.entity.User;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Pay = worked hours × the wage the shift was clocked in at (or today's wage) + tips on the person's orders.
@ExtendWith(MockitoExtension.class)
class ShiftPayServiceTest {
    @Mock ShiftRepository shifts;
    @Mock StaffPayRateRepository rates;
    @Mock RestaurantScopeService scope;
    @Mock EntityManager em;
    @Mock AuditLogRepository audits;
    ShiftPayService service;
    UUID r = UUID.randomUUID(), b = UUID.randomUUID(), me = UUID.randomUUID();
    Restaurant restaurant; Branch branch; User user;

    @BeforeEach void setup() {
        service = new ShiftPayService(shifts, rates, scope, em, audits);
        restaurant = new Restaurant(); restaurant.setId(r); restaurant.setTimezone("Europe/Berlin"); restaurant.setCurrency("EUR");
        branch = new Branch(); branch.setId(b); branch.setRestaurant(restaurant);
        user = new User(); user.setId(me); user.setRestaurantId(r); user.setActive(true); user.setFirstName("Test"); user.setLastName("Waiter");
        lenient().when(scope.currentUserId(any())).thenReturn(me);
        lenient().when(scope.requireAccessibleBranch(any(Authentication.class), eq(r), eq(b))).thenReturn(branch);
    }

    @Test void wagesUseTheClockInWageOrTodaysWageAndTipsAreAdded() {
        Shift raised = shift(LocalDate.of(2026, 9, 22), 8, new BigDecimal("10.00"));   // worked at the old wage
        Shift noWage = shift(LocalDate.of(2026, 9, 23), 4, null);                     // no wage when clocked in
        when(shifts.inWindow(eq(r), eq(b), eq(me), any(), any(), any())).thenReturn(List.of(raised, noWage));
        when(em.find(User.class, me)).thenReturn(user);
        when(rates.rates(r)).thenReturn(Map.of(me, new BigDecimal("12.00")));
        when(rates.tips(eq(r), eq(b), eq(me), any(), any(), eq("Europe/Berlin"), eq("EUR")))
            .thenReturn(List.of(new StaffPayRateRepository.TipRow(me, "2026-09-22", new BigDecimal("5.50"))));

        PayReport report = service.pay(auth("SHIFT_SELF"), r, b, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27), true);
        StaffPay pay = report.staff().get(0);
        assertEquals(720, pay.workedMinutes());
        assertEquals(new BigDecimal("128.00"), pay.wages());      // 8h × 10 + 4h × 12
        assertEquals(new BigDecimal("5.50"), pay.tips());
        assertEquals(new BigDecimal("133.50"), pay.total());
        assertFalse(pay.missingRate());
    }

    @Test void teamPayNeedsShiftManagement() {
        assertThrows(ResponseStatusException.class, () -> service.pay(auth("SHIFT_SELF"), r, b, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27), false));
    }

    @Test void rangeIsLimitedToTwoMonths() {
        assertThrows(ResponseStatusException.class, () -> service.pay(auth("SHIFT_MANAGE"), r, b, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1), false));
    }

    private Shift shift(LocalDate day, int hours, BigDecimal rate) {
        Shift s = new Shift(); s.setId(UUID.randomUUID()); s.setRestaurant(restaurant); s.setBranch(branch); s.setUser(user); s.setStatus(ShiftStatus.CLOSED);
        OffsetDateTime start = day.atTime(10, 0).atZone(ZoneId.of("Europe/Berlin")).toOffsetDateTime();
        s.setStartedAt(start); s.setEndedAt(start.plusHours(hours)); s.setHourlyRate(rate);
        return s;
    }
    private Authentication auth(String... permissions) {
        return new UsernamePasswordAuthenticationToken("u", "p", Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
}
