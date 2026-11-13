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
        recordGuestReminderSent(reservationId, ReservationEventType.GUEST_REMINDED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordGuestReminderSent(UUID reservationId, ReservationEventType type) {
        var booking=reservationRepository.findById(reservationId).orElse(null);
        if(booking==null)return;
        boolean sent=booking.getScheduleChangedAt()==null
            ? reservationEventRepository.existsByReservation_IdAndType(reservationId,type)
            : reservationEventRepository.existsByReservation_IdAndTypeAndCreatedAtGreaterThanEqual(reservationId,type,booking.getScheduleChangedAt());
        if (sent) {
            return;
        }
        ReservationEvent event = new ReservationEvent();
        event.setReservation(booking);
        event.setType(type);
        event.setDetail(type == ReservationEventType.GUEST_REMINDED_AGAIN ? "Guest asked again to confirm by email" : "Guest asked to confirm by email");
        reservationEventRepository.save(event);
    }
}
