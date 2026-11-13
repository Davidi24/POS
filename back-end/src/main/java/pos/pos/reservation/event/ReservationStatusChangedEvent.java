package pos.pos.reservation.event;

import pos.pos.reservation.enums.ReservationStatus;

import java.util.UUID;

// Published inside the transaction that changed the reservation's status, so listeners can react atomically
// (e.g. refunding a pre-order when the booking is cancelled) or after commit (e.g. firing it when guests arrive).
public record ReservationStatusChangedEvent(
        UUID reservationId,
        UUID restaurantId,
        ReservationStatus previousStatus,
        ReservationStatus newStatus,
        UUID actorId
) {
}
