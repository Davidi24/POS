package pos.pos.recipe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pos.pos.inventory.enums.InventoryItemType;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.menu.recipe.DishRecipeDtos.IngredientKind;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A sub-recipe: something the kitchen makes from what it has (a sauce, a dough, a cream). It's an item in
 * Inventory → Items (name, kind, unit, cost per unit) with the recipe that says how much a batch makes and what goes in.
 */
public final class SubRecipeDtos {

    private SubRecipeDtos() {
    }

    /**
     * The whole sub-recipe, saved at once: its item's name and kind, how much one batch makes, and its ingredients in
     * order. [active] false switches it off (the item and the recipe); left out it stays as it is (on for a new one).
     */
    public record SubRecipeRequest(
            @NotBlank(message = "name is required")
            @Size(max = 150, message = "name must be at most 150 characters")
            String name,
            @NotNull(message = "itemType is required")
            InventoryItemType itemType,
            @NotNull(message = "yieldQuantity is required")
            @DecimalMin(value = "0", inclusive = false, message = "yieldQuantity must be more than 0")
            @DecimalMax(value = "1000000", message = "yieldQuantity must be at most 1,000,000")
            @Digits(integer = 9, fraction = 3, message = "yieldQuantity can have at most 3 decimals")
            BigDecimal yieldQuantity,
            @NotNull(message = "yieldUnit is required")
            InventoryUnit yieldUnit,
            @NotEmpty(message = "add at least one ingredient")
            @Size(max = 100, message = "a sub-recipe can have at most 100 ingredients")
            List<@Valid @NotNull SubRecipeIngredientRequest> ingredients,
            @Size(max = 2000, message = "description must be at most 2000 characters")
            String description,
            @Size(max = 20000, message = "instructions must be at most 20000 characters")
            String instructions,
            Boolean active
    ) {
    }

    /** One ingredient: exactly one of [inventoryItemId] (a stock item) and [subRecipeId] (another sub-recipe). */
    public record SubRecipeIngredientRequest(
            UUID inventoryItemId,
            UUID subRecipeId,
            @NotNull(message = "quantity is required")
            @DecimalMin(value = "0", inclusive = false, message = "quantity must be more than 0")
            @DecimalMax(value = "1000000", message = "quantity must be at most 1,000,000")
            @Digits(integer = 9, fraction = 3, message = "quantity can have at most 3 decimals")
            BigDecimal quantity,
            @NotNull(message = "unit is required")
            InventoryUnit unit
    ) {
    }

    /**
     * A sub-recipe as Items shows it: its item and recipe, what one batch costs from today's prices and what one unit
     * of it costs, and each ingredient with its share of the cost.
     */
    public record SubRecipeResponse(
            UUID recipeId,
            UUID itemId,
            String name,
            InventoryItemType itemType,
            BigDecimal yieldQuantity,
            InventoryUnit yieldUnit,
            List<SubRecipeIngredientResponse> ingredients,
            BigDecimal batchCost,
            BigDecimal costPerUnit,
            String imageUrl,
            String description,
            String instructions,
            boolean active
    ) {
    }

    /** One ingredient; [kind] PREPARATION is another sub-recipe (the same kinds the dish editor uses). */
    public record SubRecipeIngredientResponse(
            IngredientKind kind,
            UUID id,
            String name,
            BigDecimal quantity,
            InventoryUnit unit,
            BigDecimal cost,
            String imageUrl
    ) {
    }
}
