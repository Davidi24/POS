package pos.pos.menu.recipe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pos.pos.inventory.enums.InventoryUnit;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** What a dish is made of from stock, edited in the dish editor ("Recipe / Ingredients"). */
public final class DishRecipeDtos {

    private DishRecipeDtos() {
    }

    /** STOCK: a stock item; PREPARATION: a sub-recipe (sauce, dough) the kitchen makes from its own recipe. */
    public enum IngredientKind { STOCK, PREPARATION }

    /** The dish's ingredients, in order; an empty list stops the dish taking anything from stock. */
    public record DishRecipeRequest(
            @NotNull(message = "ingredients is required")
            @Size(max = 100, message = "a dish can have at most 100 ingredients")
            List<@Valid @NotNull DishIngredientRequest> ingredients
    ) {
    }

    /** One ingredient: exactly one of [inventoryItemId] (a stock item) and [preparationId] (a shared preparation). */
    public record DishIngredientRequest(
            UUID inventoryItemId,
            UUID preparationId,
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
     * The dish's recipe as the dish editor shows it. [cost] is what one portion costs from today's stock prices;
     * [recipeId] is null when the dish takes nothing from stock.
     */
    public record DishRecipeResponse(
            UUID recipeId,
            List<DishIngredientResponse> ingredients,
            BigDecimal cost,
            BigDecimal price
    ) {
    }

    public record DishIngredientResponse(
            IngredientKind kind,
            UUID id,
            String name,
            BigDecimal quantity,
            InventoryUnit unit
    ) {
    }

    /**
     * Something a dish can be made with: a stock item or a shared preparation, the unit it's counted in, and what
     * one of each unit it can be measured in costs (grams and kilograms for an item counted in kilograms, ...).
     */
    public record IngredientChoiceResponse(
            IngredientKind kind,
            UUID id,
            String name,
            String group,
            InventoryUnit baseUnit,
            List<UnitCost> units,
            /* Its photo (the stock item's, or the sub-recipe's item's), when it has one. */
            String imageUrl
    ) {
    }

    public record UnitCost(InventoryUnit unit, BigDecimal cost) {
    }
}
