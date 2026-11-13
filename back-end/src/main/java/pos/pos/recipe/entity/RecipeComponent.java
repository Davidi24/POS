package pos.pos.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.recipe.enums.RecipeComponentType;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(
        name = "recipe_components",
        indexes = {
                @Index(name = "idx_recipe_components_recipe_id", columnList = "recipe_id"),
                @Index(name = "idx_recipe_components_inventory_item_id", columnList = "inventory_item_id"),
                @Index(name = "idx_recipe_components_child_recipe_id", columnList = "child_recipe_id"),
                @Index(name = "idx_recipe_components_component_type", columnList = "component_type")
        }
)
@Check(constraints = """
        component_type IN ('INVENTORY_ITEM', 'SUB_RECIPE')
        AND char_length(btrim(component_name_snapshot)) > 0
        AND quantity > 0
        AND unit IN (
            'EACH',
            'GRAM',
            'KILOGRAM',
            'MILLILITER',
            'LITER',
            'OUNCE',
            'POUND',
            'CUP',
            'TABLESPOON',
            'TEASPOON',
            'PORTION',
            'CASE',
            'BOTTLE',
            'PACK',
            'TRAY'
        )
        AND yield_loss_percent >= 0
        AND yield_loss_percent <= 100
        AND display_order >= 0
        AND (
            (component_type = 'INVENTORY_ITEM' AND inventory_item_id IS NOT NULL AND child_recipe_id IS NULL)
            OR (component_type = 'SUB_RECIPE' AND child_recipe_id IS NOT NULL AND inventory_item_id IS NULL)
        )
        AND (
            child_recipe_id IS NULL
            OR child_recipe_id <> recipe_id
        )
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class RecipeComponent extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recipe_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_recipe_components_recipe")
    )
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "inventory_item_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_recipe_components_inventory_item")
    )
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "child_recipe_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_recipe_components_child_recipe")
    )
    private Recipe childRecipe;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 30)
    private RecipeComponentType componentType = RecipeComponentType.INVENTORY_ITEM;

    @Column(name = "component_name_snapshot", nullable = false, length = 150)
    private String componentNameSnapshot;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 30)
    private InventoryUnit unit = InventoryUnit.EACH;

    @Column(name = "yield_loss_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal yieldLossPercent = BigDecimal.ZERO;

    @Column(name = "optional_component", nullable = false)
    private boolean optionalComponent = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Override
    protected void normalizeFields() {
        if (componentNameSnapshot == null) {
            if (inventoryItem != null) {
                componentNameSnapshot = inventoryItem.getName();
            } else if (childRecipe != null) {
                componentNameSnapshot = childRecipe.getName();
            }
        }

        componentNameSnapshot = NormalizationUtils.normalize(componentNameSnapshot);
        quantity = quantity == null ? BigDecimal.ONE : quantity;
        yieldLossPercent = yieldLossPercent == null ? BigDecimal.ZERO : yieldLossPercent;
        notes = NormalizationUtils.normalize(notes);
    }

    @Override
    protected void validateState() {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalStateException("quantity must be greater than zero");
        }

        if (yieldLossPercent == null || yieldLossPercent.signum() < 0 || yieldLossPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException("yieldLossPercent must be between 0 and 100");
        }

        if (displayOrder < 0) {
            throw new IllegalStateException("displayOrder must not be negative");
        }

        if (componentType == RecipeComponentType.INVENTORY_ITEM) {
            if (inventoryItem == null || childRecipe != null) {
                throw new IllegalStateException("inventory item components must only reference inventoryItem");
            }
        }

        if (componentType == RecipeComponentType.SUB_RECIPE) {
            if (childRecipe == null || inventoryItem != null) {
                throw new IllegalStateException("sub recipe components must only reference childRecipe");
            }
        }

        if (recipe != null && childRecipe != null && Objects.equals(recipe.getId(), childRecipe.getId())) {
            throw new IllegalStateException("recipe components cannot reference the same recipe as a child");
        }

        if (recipe != null && inventoryItem != null
                && recipe.getRestaurant() != null
                && inventoryItem.getRestaurant() != null) {
            if (!Objects.equals(recipe.getRestaurant().getId(), inventoryItem.getRestaurant().getId())) {
                throw new IllegalStateException("recipe component inventory item must belong to the same restaurant");
            }
        }

        if (recipe != null && childRecipe != null
                && recipe.getRestaurant() != null
                && childRecipe.getRestaurant() != null) {
            if (!Objects.equals(recipe.getRestaurant().getId(), childRecipe.getRestaurant().getId())) {
                throw new IllegalStateException("recipe component child recipe must belong to the same restaurant");
            }
        }
    }
}
