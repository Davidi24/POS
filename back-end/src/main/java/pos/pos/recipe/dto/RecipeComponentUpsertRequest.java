package pos.pos.recipe.dto;

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
    private BigDecimal quantity;

    @NotNull(message = "unit is required")
    private InventoryUnit unit;

    @DecimalMin(value = "0", message = "yieldLossPercent must be at least 0")
    @DecimalMax(value = "100", message = "yieldLossPercent must be at most 100")
    private BigDecimal yieldLossPercent;

    private Boolean optionalComponent;

    @PositiveOrZero(message = "displayOrder must not be negative")
    private Integer displayOrder;

    private String notes;
}
