package pos.pos.recipe.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeCreateRequest {

    @Size(max = 80, message = "code must be at most 80 characters")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @Size(max = 2000, message = "description must be at most 2000 characters")
    private String description;

    // Required only when recipeType is FINISHED_DISH -- validated in the service, since it's
    // a conditional requirement, not a plain "always required" one.
    private UUID menuItemId;

    @NotNull(message = "recipeType is required")
    private RecipeType recipeType;

    private RecipeStatus status;

    @Positive(message = "yieldQuantity must be greater than zero")
    @Digits(integer = 9, fraction = 3, message = "yieldQuantity must have at most 9 digits and 3 decimals")
    private BigDecimal yieldQuantity;

    private InventoryUnit yieldUnit;

    @PositiveOrZero(message = "prepTimeMinutes must not be negative")
    @Max(value = 10080, message = "prepTimeMinutes must be at most 10080")
    private Integer prepTimeMinutes;

    @PositiveOrZero(message = "cookTimeMinutes must not be negative")
    @Max(value = 10080, message = "cookTimeMinutes must be at most 10080")
    private Integer cookTimeMinutes;

    @Size(max = 10000, message = "instructions must be at most 10000 characters")
    private String instructions;

    private OffsetDateTime effectiveFrom;
}
