package pos.pos.reservation.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.event.ReservationStatusChangedEvent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// When a booking ends early its money follows, in the same transaction as the status change: a guest's cancel refunds
// what's still before its deadline (minus the card fee); a declined or expired request gives everything back; a
// no-show keeps it. (Pre-orders follow in PreOrderReservationListener with the same rules.)
@Component
@RequiredArgsConstructor
public class BookingMoneyListener {

    private final EntityManager entityManager;
    private final BookingMoneyService bookingMoneyService;

    @EventListener
    public void onBookingEnded(ReservationStatusChangedEvent event) {
        boolean ended = switch (event.newStatus()) {
            case CANCELLED, EXPIRED, NO_SHOW -> true;
            default -> false;
        };
        if (!ended) {
            return;
        }
        Reservation reservation = entityManager.find(Reservation.class, event.reservationId());
        if (reservation == null) {
            return;
        }
        bookingMoneyService.settle(reservation, outcomeOf(reservation), OffsetDateTime.now(ZoneOffset.UTC));
    }

    static BookingMoneyService.Outcome outcomeOf(Reservation reservation) {
        return switch (reservation.getStatus()) {
            case NO_SHOW -> BookingMoneyService.Outcome.NO_SHOW;
            case EXPIRED -> BookingMoneyService.Outcome.RESTAURANT_DECLINED;
            default -> ReservationLifecycleService.isDeclined(reservation)
                    ? BookingMoneyService.Outcome.RESTAURANT_DECLINED
                    : BookingMoneyService.Outcome.GUEST_CANCELLED;
        };
    }
}
