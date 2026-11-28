package pos.pos.tables.event;

import pos.pos.tables.enums.TableStatus;

import java.util.UUID;

// A table was seated or cleared by staff. Reservations follow it: seating a checked-in booking's table seats the
// booking, and clearing the table ends the visit.
public record TableStatusChangedEvent(
        UUID restaurantId,
        UUID branchId,
        UUID tableId,
        TableStatus previousStatus,
        TableStatus newStatus,
        UUID actorId
) {
}
