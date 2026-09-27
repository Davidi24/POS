package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pos.pos.customer.entity.Customer;
import pos.pos.reservation.entity.GuestNoShowClear;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.entity.ReservationReminder;
import pos.pos.reservation.enums.AttendanceConfirmedVia;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.GuestNoShowClearRepository;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.reservation.repository.ReservationReminderRepository;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.service.GuestNoShowCounter;
import pos.pos.reservation.service.ReservationActor;
import pos.pos.reservation.service.ReservationLifecycleService;
import pos.pos.reservation.service.ReservationNotifications;
import pos.pos.reservation.service.ReservationPolicy;
import pos.pos.reservation.service.ReservationReminderJob;
import pos.pos.reservation.service.ReservationRuleResolver;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.repository.SettingsReservationRuleRepository;
import pos.pos.settings.repository.SettingsRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Phase 2: the guest's no-show warning (derived, cleared by a manager), "✓ Attendance confirmed", and the reminders.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Guest history, attendance and reminders")
class GuestHistoryAndAttendanceTest {

    // 20:00 in Berlin.
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 27, 18, 0, 0, 0, ZoneOffset.UTC);

    @Mock ReservationRepository reservationRepository;
    @Mock GuestNoShowClearRepository clearRepository;
    @Mock SettingsRepository settingsRepository;
    @Mock SettingsReservationRuleRepository ruleRepository;
    @Mock ReservationEventRepository eventRepository;
    @Mock ReservationReminderRepository reminderRepository;
    @Mock ReservationNotifications notifications;

    private final Restaurant restaurant = new Restaurant();
    private final Branch branch = new Branch();
    private ReservationPolicy policy;
    private ReservationSupport support;

    @BeforeEach
    void setUp() {
        restaurant.setId(UUID.randomUUID());
        restaurant.setTimezone("Europe/Berlin");
        branch.setId(UUID.randomUUID());
        branch.setRestaurant(restaurant);
        policy = new ReservationPolicy(settingsRepository, new ReservationRuleResolver(ruleRepository));
        support = new ReservationSupport(null, null, null, null, null, null, null, policy);
    }

    private Reservation booking(ReservationStatus status, OffsetDateTime start, String phone) {
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setRestaurant(restaurant);
        reservation.setBranch(branch);
        reservation.setStatus(status);
        reservation.setPartySize(2);
        reservation.setReservationStart(start);
        reservation.setReservationEnd(start.plusHours(2));
        reservation.setContactName("Maria");
        reservation.setContactPhone(phone);
        reservation.setCreatedAt(start.minusDays(3));
        return reservation;
    }

    private Reservation noShow(OffsetDateTime start, String phone) {
        Reservation reservation = booking(ReservationStatus.NO_SHOW, start, phone);
        reservation.setNoShowAt(start.plusMinutes(30));
        return reservation;
    }

    // ---- No-show warning ----

    @Test
    @DisplayName("counts the same guest's no-shows by phone, email or customer")
    void countsSameGuest() {
        Reservation today = booking(ReservationStatus.CONFIRMED, NOW.plusHours(1), "+39111");
        Reservation other = booking(ReservationStatus.CONFIRMED, NOW.plusHours(2), "+39222");
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        other.setCustomer(customer);
        Reservation byPhone = noShow(NOW.minusDays(10), "+39111");
        Reservation byCustomer = noShow(NOW.minusDays(5), "+39999");
        byCustomer.setCustomer(customer);
        when(reservationRepository.findGuestNoShows(eq(restaurant.getId()), eq(ReservationStatus.NO_SHOW), any(), any(), any()))
                .thenReturn(List.of(byPhone, byCustomer));

        Map<UUID, Integer> counts = new GuestNoShowCounter(reservationRepository, clearRepository).counts(List.of(today, other));

        assertThat(counts).containsEntry(today.getId(), 1).containsEntry(other.getId(), 1);
    }

    @Test
    @DisplayName("a manager's clear hides older no-shows; a later one warns again")
    void clearResetsTheWarning() {
        Reservation today = booking(ReservationStatus.CONFIRMED, NOW.plusHours(1), "+39111");
        Reservation old = noShow(NOW.minusDays(20), "+39111");
        Reservation newer = noShow(NOW.minusDays(2), "+39111");
        GuestNoShowClear clear = new GuestNoShowClear();
        clear.setContactPhone("+39111");
        clear.setClearedAt(NOW.minusDays(10));
        when(reservationRepository.findGuestNoShows(any(), any(), any(), any(), any())).thenReturn(List.of(newer, old));
        when(clearRepository.findForGuests(any(), any(), any(), any())).thenReturn(List.of(clear));

        List<Reservation> counted = new GuestNoShowCounter(reservationRepository, clearRepository).noShowsOf(today);

        assertThat(counted).containsExactly(newer);
    }

    @Test
    @DisplayName("the booking itself never counts, and a guest without details has none")
    void selfAndNoDetails() {
        Reservation self = noShow(NOW.minusDays(1), "+39111");
        when(reservationRepository.findGuestNoShows(any(), any(), any(), any(), any())).thenReturn(List.of(self));
        GuestNoShowCounter counter = new GuestNoShowCounter(reservationRepository, clearRepository);

        assertThat(counter.noShowsOf(self)).isEmpty();
        Reservation anonymous = booking(ReservationStatus.CONFIRMED, NOW.plusHours(1), null);
        assertThat(counter.noShowsOf(anonymous)).isEmpty();
    }

    // ---- Attendance ----

    @Test
    @DisplayName("attendance is waiting, then not confirmed 2 hours before; booked late shows 'confirm now'")
    void attendanceStates() {
        assertThat(support.attendance(booking(ReservationStatus.CONFIRMED, NOW.plusHours(3), "+1"), NOW)).isEqualTo("WAITING");
        assertThat(support.attendance(booking(ReservationStatus.CONFIRMED, NOW.plusMinutes(90), "+1"), NOW)).isEqualTo("NOT_CONFIRMED");

        Reservation bookedLate = booking(ReservationStatus.CONFIRMED, NOW.plusMinutes(90), "+1");
        bookedLate.setCreatedAt(NOW.minusMinutes(5));
        assertThat(support.attendance(bookedLate, NOW)).isEqualTo("CONFIRM_NOW");

        Reservation confirmed = booking(ReservationStatus.CONFIRMED, NOW.plusMinutes(90), "+1");
        confirmed.setAttendanceConfirmedAt(NOW.minusHours(1));
        assertThat(support.attendance(confirmed, NOW)).isEqualTo("CONFIRMED");
        assertThat(support.attendance(booking(ReservationStatus.PENDING, NOW.plusHours(3), "+1"), NOW)).isNull();
    }

    @Test
    @DisplayName("staff mark attendance confirmed once; the status stays confirmed")
    void confirmAttendance() {
        ReservationLifecycleService lifecycle = new ReservationLifecycleService(
                null, support, null, policy, null, eventRepository, null, null, event -> { });
        lifecycle.useClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        Reservation booking = booking(ReservationStatus.CONFIRMED, NOW.plusDays(1), "+1");
        ReservationActor staff = new ReservationActor(UUID.randomUUID(), false, false, false);

        lifecycle.confirmAttendance(booking, AttendanceConfirmedVia.STAFF, null, staff);
        lifecycle.confirmAttendance(booking, AttendanceConfirmedVia.STAFF, null, staff);

        assertThat(booking.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(booking.getAttendanceConfirmedAt()).isEqualTo(NOW);
        assertThat(booking.getAttendanceConfirmedVia()).isEqualTo(AttendanceConfirmedVia.STAFF);
        verify(eventRepository, times(1)).save(any(ReservationEvent.class));
        assertThatThrownBy(() -> lifecycle.confirmAttendance(booking(ReservationStatus.PENDING, NOW.plusDays(1), "+1"), AttendanceConfirmedVia.STAFF, null, staff))
                .hasMessageContaining("Only a confirmed booking");
    }

    // ---- Reminders ----

    private ReservationReminderJob reminderJob() {
        return new ReservationReminderJob(reservationRepository, reminderRepository, eventRepository, policy, support, notifications);
    }

    @Test
    @DisplayName("staff are told to call once, 2 hours before, when attendance isn't confirmed")
    void callReminderOnce() {
        Reservation soon = booking(ReservationStatus.CONFIRMED, NOW.plusMinutes(100), "+1");
        Reservation later = booking(ReservationStatus.CONFIRMED, NOW.plusHours(5), "+2");
        when(reservationRepository.findAwaitingAttendance(eq(ReservationStatus.CONFIRMED), any(), any())).thenReturn(List.of(soon, later));

        reminderJob().sendReminders(NOW);
        verify(notifications).callGuest(soon);
        verify(notifications, never()).callGuest(later);
        verify(eventRepository).save(org.mockito.ArgumentMatchers.argThat((ReservationEvent e) -> e.getType() == ReservationEventType.CALL_REMINDED));

        when(eventRepository.existsByReservation_IdAndType(soon.getId(), ReservationEventType.CALL_REMINDED)).thenReturn(true);
        reminderJob().sendReminders(NOW);
        verify(notifications, times(1)).callGuest(soon);
    }

    @Test
    @DisplayName("managers get tomorrow's summary once, after 15:00")
    void dayBeforeSummary() {
        Reservation tomorrow = booking(ReservationStatus.CONFIRMED, NOW.plusDays(1), "+1");
        Reservation request = booking(ReservationStatus.PENDING, NOW.plusDays(1).plusHours(1), "+2");
        when(reservationRepository.findAllByStatusInAndReservationStartBetween(any(), any(), any())).thenReturn(List.of(tomorrow, request));

        // 20:00 in Berlin is after the 15:00 reminder.
        reminderJob().sendReminders(NOW);
        verify(notifications).dayBefore(any(), eq(1), eq(1), anyInt());
        verify(reminderRepository).save(any(ReservationReminder.class));

        when(reminderRepository.existsById(any())).thenReturn(true);
        reminderJob().sendReminders(NOW);
        verify(notifications, times(1)).dayBefore(any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("no summary before the reminder time")
    void noSummaryBeforeReminderTime() {
        OffsetDateTime morning = OffsetDateTime.of(2026, 9, 27, 8, 0, 0, 0, ZoneOffset.UTC);
        when(reservationRepository.findAllByStatusInAndReservationStartBetween(any(), any(), any()))
                .thenReturn(List.of(booking(ReservationStatus.CONFIRMED, morning.plusDays(1), "+1")));

        reminderJob().sendReminders(morning);

        verify(notifications, never()).dayBefore(any(), anyInt(), anyInt(), anyInt());
    }
}
