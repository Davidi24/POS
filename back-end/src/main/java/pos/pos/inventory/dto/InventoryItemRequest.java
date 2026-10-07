package pos.pos.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryItemType;
import pos.pos.inventory.enums.InventoryUnit;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemRequest {

    @Size(max = 80, message = "code must be at most 80 characters")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @Size(max = 2000, message = "description must be at most 2000 characters")
    private String description;

    @NotNull(message = "itemType is required")
    private InventoryItemType itemType;

    @NotNull(message = "baseUnit is required")
    private InventoryUnit baseUnit;

    @Size(max = 80, message = "barcode must be at most 80 characters")
    private String barcode;

    @Size(max = 150, message = "supplierName must be at most 150 characters")
    private String supplierName;

    @Size(max = 100, message = "supplierSku must be at most 100 characters")
    private String supplierSku;

    @NotNull(message = "costPerUnit is required")
    @PositiveOrZero(message = "costPerUnit must not be negative")
    @Digits(integer = 12, fraction = 4, message = "costPerUnit must have at most 12 digits and 4 decimals")
    private BigDecimal costPerUnit;

    @PositiveOrZero(message = "reorderPoint must not be negative")
    @Digits(integer = 9, fraction = 3, message = "reorderPoint must have at most 9 digits and 3 decimals")
    private BigDecimal reorderPoint;

    @PositiveOrZero(message = "parLevel must not be negative")
    @Digits(integer = 9, fraction = 3, message = "parLevel must have at most 9 digits and 3 decimals")
    private BigDecimal parLevel;

    private Boolean trackInventory;

    private Boolean active;

    @Size(max = 2000, message = "storageNotes must be at most 2000 characters")
    private String storageNotes;
}
