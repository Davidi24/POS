package pos.pos.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.recipe.enums.RecipeType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Full replacement of a recipe's own fields (PUT-style). Does not touch components --
// those are managed separately through the components endpoints.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeUpdateRequest {

    @Size(max = 80, message = "code must be at most 80 characters")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    private String description;

    private UUID menuItemId;

    @NotNull(message = "recipeType is required")
    private RecipeType recipeType;

    private RecipeStatus status;

    @Positive(message = "yieldQuantity must be greater than zero")
    private BigDecimal yieldQuantity;

    private InventoryUnit yieldUnit;

    @PositiveOrZero(message = "prepTimeMinutes must not be negative")
    private Integer prepTimeMinutes;

    @PositiveOrZero(message = "cookTimeMinutes must not be negative")
    private Integer cookTimeMinutes;

    private String instructions;

    private OffsetDateTime effectiveFrom;
}
