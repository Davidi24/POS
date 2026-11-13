package pos.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryItemType;
import pos.pos.inventory.enums.InventoryUnit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemResponse {

    private UUID id;
    private UUID restaurantId;
    private String code;
    private String name;
    private String description;
    private InventoryItemType itemType;
    // The restaurant's own kind (itemType CUSTOM).
    private UUID kindId;
    private String kindName;
    private InventoryUnit baseUnit;
    private String barcode;
    private String supplierName;
    private java.util.UUID supplierId;
    private String supplierSku;
    private BigDecimal costPerUnit;
    private BigDecimal reorderPoint;
    private BigDecimal parLevel;
    private boolean trackInventory;
    private boolean active;
    private String storageNotes;
    private String imageUrl;
    /** The sub-recipe that makes this item when it's made here (a sauce, a dough); null for bought items. */
    private UUID recipeId;
    private String createdByUserName;
    private String updatedByUserName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
