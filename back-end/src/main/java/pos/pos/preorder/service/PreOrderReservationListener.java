package pos.pos.preorder.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pos.pos.exception.auth.AuthException;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.preorder.repository.PreOrderRepository;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.event.ReservationDeletingEvent;
import pos.pos.reservation.event.ReservationStatusChangedEvent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static pos.pos.preorder.service.PreOrderLifecycleService.LIVE;

// Keeps a reservation's pre-order in step with the booking.
@Component
@RequiredArgsConstructor
public class PreOrderReservationListener {

    private static final Logger logger = LoggerFactory.getLogger(PreOrderReservationListener.class);

    private final PreOrderRepository preOrderRepository;
    private final PreOrderLifecycleService preOrderLifecycleService;
    private final EntityManager entityManager;

    // Booking cancelled or guests never came: refund if the kitchen hadn't started, otherwise keep the payment.
    // Same transaction as the status change, so both commit or neither does.
    @EventListener
    public void onBookingEnded(ReservationStatusChangedEvent event) {
        // An unanswered request that expired is handled like a cancel: the guest isn't to blame.
        boolean cancelled = event.newStatus() == ReservationStatus.CANCELLED || event.newStatus() == ReservationStatus.EXPIRED;
        if (!cancelled && event.newStatus() != ReservationStatus.NO_SHOW) {
            return;
        }
        Reservation reservation = entityManager.find(Reservation.class, event.reservationId());
        if (reservation == null) {
            return;
        }
        preOrderLifecycleService.lockHeld(reservation);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        preOrderRepository.lockLiveByReservationId(event.reservationId(), LIVE).ifPresent(preOrder -> {
            if (preOrder.getStatus() == PreOrderStatus.SCHEDULED) {
                preOrderLifecycleService.refund(preOrder,
                        cancelled ? "Reservation cancelled before the kitchen started" : "Marked no-show before the kitchen started",
                        event.actorId(), now);
            } else {
                preOrderLifecycleService.forfeit(preOrder,
                        cancelled ? "Reservation cancelled after the kitchen started; payment kept" : "Guests did not arrive; payment kept",
                        event.actorId(), now);
            }
        });
    }

    // Guests checked in or sat down: fire an early arrival's pre-order and put the order on their table.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGuestsArrived(ReservationStatusChangedEvent event) {
        if (event.newStatus() != ReservationStatus.CHECKED_IN && event.newStatus() != ReservationStatus.SEATED) {
            return;
        }
        try {
            preOrderLifecycleService.handleArrival(event.reservationId(), event.actorId());
        } catch (RuntimeException error) {
            // The arrival itself is saved; the dispatch job still sends the pre-order when it's due.
            logger.warn("Could not update the pre-order for reservation {} on arrival", event.reservationId(), error);
        }
    }

    // A pre-order carries money, so its booking must be cancelled (which refunds or keeps it), never deleted.
    @EventListener
    public void onDeleting(ReservationDeletingEvent event) {
        if (preOrderRepository.existsByReservation_Id(event.reservationId())) {
            throw new AuthException("This reservation has a pre-order, so it can't be deleted. Cancel it instead.", HttpStatus.CONFLICT);
        }
    }
}
