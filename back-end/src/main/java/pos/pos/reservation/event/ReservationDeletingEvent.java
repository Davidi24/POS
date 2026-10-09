package pos.pos.reservation.event;

import java.util.UUID;

// Published right before a reservation is hard-deleted; a listener may throw to stop the delete
// (e.g. when money is attached to it through a pre-order).
public record ReservationDeletingEvent(UUID reservationId, UUID restaurantId) {
}
