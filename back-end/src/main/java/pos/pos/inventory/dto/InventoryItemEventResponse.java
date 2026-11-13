package pos.pos.inventory.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Something that happened to a stock item itself, for Inventory → History: [action] is ITEM_CREATED, ITEM_UPDATED or
 * ITEM_DELETED; [summary] says what ("Added", "Cost 2.50 → 3.00 · Supplier", "New photo", "Not in use", "Deleted").
 */
public record InventoryItemEventResponse(UUID id, UUID itemId, String itemName, String action, String summary, OffsetDateTime occurredAt,
                                         UUID createdById, String createdByUserName) {
}
