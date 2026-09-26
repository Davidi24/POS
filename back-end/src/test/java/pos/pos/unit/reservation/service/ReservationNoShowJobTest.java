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
import pos.pos.reservation.service.ReservationSupport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Automatic no-show")
class ReservationNoShowJobTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 25, 21, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private ReservationRepository reservationRepository;

    private ReservationNoShowJob job() {
        ReservationSupport support = new ReservationSupport(null, null, null, null, null, null, null);
        var notifications = org.mockito.Mockito.mock(pos.pos.reservation.service.ReservationNotifications.class);
        return new ReservationNoShowJob(reservationRepository, new ReservationLifecycleService(null, support, notifications, event -> { }), support, notifications);
    }

    private static Reservation waiting(ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setStatus(status);
        reservation.setReservationStart(NOW.minusMinutes(45));
        reservation.setReservationEnd(NOW.plusMinutes(75));
        return reservation;
    }

    @Test
    @DisplayName("marks pending and confirmed bookings no-show 30 minutes after their start")
    void marksOverdueBookings() {
        Reservation pending = waiting(ReservationStatus.PENDING);
        Reservation confirmed = waiting(ReservationStatus.CONFIRMED);
        when(reservationRepository.findOverdueForNoShow(any(), eq(NOW.minusMinutes(30)), eq(ReservationStatus.NO_SHOW)))
                .thenReturn(List.of(pending, confirmed));

        assertThat(job().markOverdueReservations(NOW)).isEqualTo(2);

        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(pending.getNoShowAt()).isNotNull();
        assertThat(pending.getStatusHistory()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getOldStatus()).isEqualTo(ReservationStatus.PENDING);
                    assertThat(entry.getNewStatus()).isEqualTo(ReservationStatus.NO_SHOW);
                    assertThat(entry.getReason()).contains("30 minutes");
                });
        verify(reservationRepository).saveAll(List.of(pending, confirmed));
    }

    @Test
    @DisplayName("marks a checked-in booking that was never seated no-show once it has ended")
    void marksCheckedInPastEnd() {
        Reservation checkedIn = waiting(ReservationStatus.CHECKED_IN);
        checkedIn.setCheckedInAt(NOW.minusHours(2));
        when(reservationRepository.findOverdueForNoShow(any(), any(), any())).thenReturn(List.of());
        when(reservationRepository.findCheckedInPastEnd(ReservationStatus.CHECKED_IN, NOW, ReservationStatus.NO_SHOW)).thenReturn(List.of(checkedIn));

        assertThat(job().markOverdueReservations(NOW)).isEqualTo(1);

        assertThat(checkedIn.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(checkedIn.getCheckedInAt()).isNull();
        assertThat(checkedIn.getStatusHistory()).singleElement()
                .satisfies(entry -> assertThat(entry.getReason()).contains("never seated"));
    }

    @Test
    @DisplayName("does nothing when nobody is overdue")
    void nothingOverdue() {
        when(reservationRepository.findOverdueForNoShow(any(), any(), any())).thenReturn(List.of());
        when(reservationRepository.findCheckedInPastEnd(any(), any(), any())).thenReturn(List.of());

        assertThat(job().markOverdueReservations(NOW)).isZero();
        verify(reservationRepository, never()).saveAll(any());
    }
}
