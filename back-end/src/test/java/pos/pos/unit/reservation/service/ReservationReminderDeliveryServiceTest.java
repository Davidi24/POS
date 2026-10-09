package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.Test;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.service.ReservationReminderDeliveryService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationReminderDeliveryServiceTest {

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ReservationEventRepository eventRepository = mock(ReservationEventRepository.class);
    private final ReservationReminderDeliveryService service =
            new ReservationReminderDeliveryService(reservationRepository, eventRepository);

    @Test
    void storesOneGuestRemindedEventAfterMailWasAccepted() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = new Reservation();
        when(eventRepository.existsByReservation_IdAndType(reservationId, ReservationEventType.GUEST_REMINDED)).thenReturn(false);
        when(reservationRepository.getReferenceById(reservationId)).thenReturn(reservation);

        service.recordGuestReminderSent(reservationId);

        verify(eventRepository).save(org.mockito.ArgumentMatchers.argThat(event ->
                event.getReservation() == reservation
                        && event.getType() == ReservationEventType.GUEST_REMINDED
                        && "Guest asked to confirm by email".equals(event.getDetail())));
    }

    @Test
    void repeatedDeliveryCallbackDoesNotCreateDuplicateEvents() {
        UUID reservationId = UUID.randomUUID();
        when(eventRepository.existsByReservation_IdAndType(reservationId, ReservationEventType.GUEST_REMINDED)).thenReturn(true);

        service.recordGuestReminderSent(reservationId);

        verify(eventRepository, never()).save(any(ReservationEvent.class));
        verify(reservationRepository, never()).getReferenceById(reservationId);
    }
}
