package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.service.ReservationLifecycleService;
import pos.pos.reservation.service.ReservationNoShowJob;
import pos.pos.reservation.service.ReservationNotifications;
import pos.pos.reservation.service.ReservationPolicy;
import pos.pos.reservation.service.ReservationRuleResolver;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.repository.SettingsReservationRuleRepository;
import pos.pos.settings.repository.SettingsRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Confirmed bookings become no-shows when their hold runs out; unanswered requests expire at their start and are
// never no-shows; checked-in and seated guests are never touched.
@ExtendWith(MockitoExtension.class)
@DisplayName("Automatic no-show and expiry")
class ReservationNoShowJobTest {

    // 23:00 in Berlin.
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 25, 21, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private SettingsRepository settingsRepository;
    @Mock
    private SettingsReservationRuleRepository ruleRepository;

    private final ReservationNotifications notifications = mock(ReservationNotifications.class);
    private final Restaurant restaurant = restaurant();

    private ReservationNoShowJob job() {
        ReservationPolicy policy = new ReservationPolicy(settingsRepository, new ReservationRuleResolver(ruleRepository));
        ReservationSupport support = new ReservationSupport(null, null, null, null, null, null, null, policy);
        ReservationLifecycleService lifecycle = new ReservationLifecycleService(
                null, support, notifications, policy, null, null, null, null, event -> { });
        lifecycle.useClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        return new ReservationNoShowJob(reservationRepository, lifecycle, policy, support, notifications);
    }

    private static Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        restaurant.setTimezone("Europe/Berlin");
        return restaurant;
    }

    private Reservation booking(ReservationStatus status, int minutesAgo) {
        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setStatus(status);
        reservation.setReservationStart(NOW.minusMinutes(minutesAgo));
        reservation.setReservationEnd(NOW.minusMinutes(minutesAgo).plusHours(2));
        return reservation;
    }

    @Test
    @DisplayName("a confirmed booking becomes a no-show once its 30-minute hold has run out")
    void confirmedPastHold() {
        Reservation overdue = booking(ReservationStatus.CONFIRMED, 45);
        Reservation stillHeld = booking(ReservationStatus.CONFIRMED, 20);
        when(reservationRepository.findConfirmedPastStart(ReservationStatus.CONFIRMED, NOW, ReservationStatus.NO_SHOW))
                .thenReturn(List.of(overdue, stillHeld));

        assertThat(job().markOverdueReservations(NOW)).isEqualTo(1);

        assertThat(overdue.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(overdue.getNoShowAt()).isEqualTo(NOW);
        assertThat(overdue.getStatusHistory()).singleElement()
                .satisfies(entry -> assertThat(entry.getReason()).contains("held until 22:45"));
        assertThat(stillHeld.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(notifications).noShows(List.of(overdue));
    }

    @Test
    @DisplayName("a table staff held longer isn't released early")
    void extendedHoldWaits() {
        Reservation late = booking(ReservationStatus.CONFIRMED, 45);
        late.setHoldUntil(NOW.plusMinutes(5));
        when(reservationRepository.findConfirmedPastStart(any(), any(), any())).thenReturn(List.of(late));

        assertThat(job().markOverdueReservations(NOW)).isZero();
        assertThat(late.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("an unanswered request expires at its start and is never a no-show")
    void unansweredRequestExpires() {
        Reservation request = booking(ReservationStatus.PENDING, 1);
        when(reservationRepository.findUnansweredRequests(ReservationStatus.PENDING, NOW)).thenReturn(List.of(request));

        assertThat(job().markOverdueReservations(NOW)).isEqualTo(1);

        assertThat(request.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(request.getExpiredAt()).isEqualTo(NOW);
        assertThat(request.getNoShowAt()).isNull();
        verify(notifications).expired(List.of(request));
        verify(notifications, never()).noShows(any());
    }

    @Test
    @DisplayName("does nothing when nobody is overdue")
    void nothingOverdue() {
        assertThat(job().markOverdueReservations(NOW)).isZero();
        verify(reservationRepository, never()).saveAll(any());
        verify(reservationRepository).findConfirmedPastStart(eq(ReservationStatus.CONFIRMED), eq(NOW), eq(ReservationStatus.NO_SHOW));
    }
}
