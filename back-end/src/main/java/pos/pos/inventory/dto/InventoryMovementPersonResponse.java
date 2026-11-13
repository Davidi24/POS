package pos.pos.inventory.dto;

import java.util.UUID;

/** Someone who made stock changes, for History's person filter. */
public record InventoryMovementPersonResponse(UUID id, String name, long count) {
}
