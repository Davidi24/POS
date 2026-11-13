package pos.pos.recipe.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.recipe.enums.RecipeComponentType;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeComponentUpsertRequest {

    @NotNull(message = "componentType is required")
    private RecipeComponentType componentType;

    // Exactly one of these two must be present -- which one depends on componentType.
    // The service rejects both-supplied, neither-supplied, and a mismatch against componentType.
    private UUID inventoryItemId;

    private UUID childRecipeId;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than zero")
    @Digits(integer = 9, fraction = 3, message = "quantity must have at most 9 digits and 3 decimals")
    private BigDecimal quantity;

    @NotNull(message = "unit is required")
    private InventoryUnit unit;

    @DecimalMin(value = "0", message = "yieldLossPercent must be at least 0")
    // 100% would mean nothing is left to use, which can't be priced.
    @DecimalMax(value = "100", inclusive = false, message = "yieldLossPercent must be below 100")
    private BigDecimal yieldLossPercent;

    private Boolean optionalComponent;

    @PositiveOrZero(message = "displayOrder must not be negative")
    private Integer displayOrder;

    @Size(max = 2000, message = "notes must be at most 2000 characters")
    private String notes;
}
