package pos.pos.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.recipe.entity.Recipe;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents a selectable option inside an option group.
 *
 * An option item is an individual modifier choice,
 * such as Bacon, Cheese, Extra Sauce etc.
 *
 * An option item may add an additional price.
 */

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "`option-items`",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_option_items_group_name", columnNames = {"option_group_id", "name"})
        },
        indexes = {
                @Index(name = "idx_option_items_option_group_id", columnList = "option_group_id")
        }
)
@Check(constraints = """
        char_length(btrim(name)) > 0
        AND (code IS NULL OR char_length(btrim(code)) > 0)
        AND display_order >= 0
        AND ((inventory_recipe_id IS NULL AND inventory_recipe_quantity IS NULL)
             OR (inventory_recipe_id IS NOT NULL AND inventory_recipe_quantity > 0))
        """)
public class OptionItem extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id", nullable = false)
    private OptionGroup optionGroup;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "price_delta", precision = 19, scale = 2)
    private BigDecimal priceDelta;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /** Optional recipe consumed when this modifier is selected; usage quantity is in the recipe yield unit. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_recipe_id")
    private Recipe inventoryRecipe;

    @Column(name = "inventory_recipe_quantity", precision = 12, scale = 3)
    private BigDecimal inventoryRecipeQuantity;

    @Override
    protected void normalizeFields() {
        code = normalizeCode(code);
        name = NormalizationUtils.normalize(name);
    }

    @Override
    protected void validateState() {
        if (displayOrder != null && displayOrder < 0) {
            throw new IllegalStateException("displayOrder must be greater than or equal to zero");
        }
        if ((inventoryRecipe == null) != (inventoryRecipeQuantity == null)
                || (inventoryRecipeQuantity != null && inventoryRecipeQuantity.signum() <= 0)) {
            throw new IllegalStateException("inventory recipe and a positive usage quantity must be provided together");
        }
        if (inventoryRecipe != null && optionGroup != null && optionGroup.getRestaurant() != null
                && inventoryRecipe.getRestaurant() != null
                && !Objects.equals(optionGroup.getRestaurant().getId(), inventoryRecipe.getRestaurant().getId())) {
            throw new IllegalStateException("modifier inventory recipe must belong to the same restaurant");
        }
    }

    private String normalizeCode(String value) {
        return NormalizationUtils.normalizeCode(value);
    }
}
