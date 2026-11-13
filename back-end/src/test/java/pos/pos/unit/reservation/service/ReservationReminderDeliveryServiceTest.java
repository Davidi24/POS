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
        when(reservationRepository.findById(reservationId)).thenReturn(java.util.Optional.of(reservation));

        service.recordGuestReminderSent(reservationId);

        verify(eventRepository).save(org.mockito.ArgumentMatchers.argThat(event ->
                event.getReservation() == reservation
                        && event.getType() == ReservationEventType.GUEST_REMINDED
                        && "Guest asked to confirm by email".equals(event.getDetail())));
    }

    @Test
    void repeatedDeliveryCallbackDoesNotCreateDuplicateEvents() {
        UUID reservationId = UUID.randomUUID();
        when(reservationRepository.findById(reservationId)).thenReturn(java.util.Optional.of(new Reservation()));
        when(eventRepository.existsByReservation_IdAndType(reservationId, ReservationEventType.GUEST_REMINDED)).thenReturn(true);

        service.recordGuestReminderSent(reservationId);

        verify(eventRepository, never()).save(any(ReservationEvent.class));
        verify(reservationRepository, never()).getReferenceById(reservationId);
    }
    @Test void oldReminderDoesNotSuppressDeliveryForChangedSchedule() {
        UUID id=UUID.randomUUID(); Reservation booking=new Reservation();
        var revision=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);booking.setScheduleChangedAt(revision);
        when(reservationRepository.findById(id)).thenReturn(java.util.Optional.of(booking));
        when(eventRepository.existsByReservation_IdAndTypeAndCreatedAtGreaterThanEqual(id,ReservationEventType.GUEST_REMINDED,revision)).thenReturn(false);
        service.recordGuestReminderSent(id);
        verify(eventRepository).save(any(ReservationEvent.class));
        verify(eventRepository,never()).existsByReservation_IdAndType(id,ReservationEventType.GUEST_REMINDED);
    }
}
