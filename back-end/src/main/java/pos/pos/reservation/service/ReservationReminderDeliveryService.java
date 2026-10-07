package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.reservation.repository.ReservationRepository;

import java.util.UUID;

/** Records an attendance reminder only after SMTP accepted the message. */
@Service
@RequiredArgsConstructor
public class ReservationReminderDeliveryService {

    private final ReservationRepository reservationRepository;
    private final ReservationEventRepository reservationEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordGuestReminderSent(UUID reservationId) {
        if (reservationEventRepository.existsByReservation_IdAndType(reservationId, ReservationEventType.GUEST_REMINDED)) {
            return;
        }
        ReservationEvent event = new ReservationEvent();
        event.setReservation(reservationRepository.getReferenceById(reservationId));
        event.setType(ReservationEventType.GUEST_REMINDED);
        event.setDetail("Guest asked to confirm by email");
        reservationEventRepository.save(event);
    }
}
