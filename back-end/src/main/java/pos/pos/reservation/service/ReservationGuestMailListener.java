package pos.pos.reservation.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.event.ReservationStatusChangedEvent;
import pos.pos.reservation.repository.ReservationEventRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// Tells the guest by email when staff answer their request (accepted or declined), when it expired unanswered, and
// when their booking is cancelled, with the money lines. Runs after the change is saved.
@Component
@RequiredArgsConstructor
public class ReservationGuestMailListener {

    private static final Logger logger = LoggerFactory.getLogger(ReservationGuestMailListener.class);

    private final EntityManager entityManager;
    private final ReservationMailService reservationMailService;
    private final BookingMoneyService bookingMoneyService;
    private final ReservationPolicy reservationPolicy;
    private final ReservationEventRepository reservationEventRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStatusChanged(ReservationStatusChangedEvent event) {
        try {
            Reservation reservation = entityManager.find(Reservation.class, event.reservationId());
            if (reservation == null || reservation.getContactEmail() == null) {
                return;
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            var money = bookingMoneyService.lines(reservation, now);
            if (event.previousStatus() == ReservationStatus.PENDING && event.newStatus() == ReservationStatus.CONFIRMED) {
                boolean askNow = askToConfirmNow(reservation, now);
                reservationMailService.confirmed(reservation, true, askNow,
                        money.stream().filter(line -> "PENDING".equals(line.getStatus())).toList());
            } else if (event.newStatus() == ReservationStatus.EXPIRED) {
                reservationMailService.expired(reservation, money);
            } else if (event.newStatus() == ReservationStatus.CANCELLED) {
                if (ReservationLifecycleService.isDeclined(reservation)) {
                    reservationMailService.declined(reservation, money);
                } else {
                    reservationMailService.cancelled(reservation, money);
                }
            }
        } catch (RuntimeException error) {
            logger.warn("Could not email the guest about booking {}", event.reservationId(), error);
        }
    }

    // Booked less than the reminder time ahead: one email that already asks them to confirm.
    boolean askToConfirmNow(Reservation reservation, OffsetDateTime now) {
        int hours = reservationPolicy.values(reservation.getRestaurant()).guestReminderHours();
        return !now.isBefore(reservation.getReservationStart().minusHours(hours));
    }

}
