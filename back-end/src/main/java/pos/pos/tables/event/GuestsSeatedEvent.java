package pos.pos.tables.event;

import java.util.UUID;

// Guests were seated, or their number changed. tables is what people call them ("T01-T03"), capacity their seats together.
public record GuestsSeatedEvent(
        UUID restaurantId,
        UUID branchId,
        String branchName,
        UUID tableId,
        String tables,
        int capacity,
        int guests,
        int previousGuests,
        UUID actorId
) {
}
