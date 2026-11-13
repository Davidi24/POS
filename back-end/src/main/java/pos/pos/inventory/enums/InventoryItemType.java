package pos.pos.inventory.enums;

public enum InventoryItemType {
    INGREDIENT,
    PREPARED_COMPONENT,
    FINISHED_GOOD,
    BEVERAGE,
    ALCOHOL,
    PACKAGING,
    SUPPLY,
    // Where items go when their kind is deleted.
    UNCATEGORIZED,
    // One of the restaurant's own kinds (InventoryItem.kind).
    CUSTOM
}
