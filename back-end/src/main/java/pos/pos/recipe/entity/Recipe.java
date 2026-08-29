package pos.pos.recipe.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.menu.entity.MenuItem;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.recipe.enums.RecipeType;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "recipes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_recipes_restaurant_code", columnNames = {"restaurant_id", "code"}),
                @UniqueConstraint(name = "uk_recipes_menu_item_version", columnNames = {"menu_item_id", "version"})
        },
        indexes = {
                @Index(name = "idx_recipes_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_recipes_menu_item_id", columnList = "menu_item_id"),
                @Index(name = "idx_recipes_recipe_type", columnList = "recipe_type"),
                @Index(name = "idx_recipes_status", columnList = "status"),
                @Index(name = "idx_recipes_created_by", columnList = "created_by"),
                @Index(name = "idx_recipes_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND recipe_type IN ('FINISHED_DISH', 'PREP_BATCH', 'SUB_RECIPE')
        AND status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')
        AND version > 0
        AND yield_quantity > 0
        AND yield_unit IN (
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
        AND (prep_time_minutes IS NULL OR prep_time_minutes >= 0)
        AND (cook_time_minutes IS NULL OR cook_time_minutes >= 0)
        AND theoretical_cost >= 0
        AND (
            menu_item_id IS NOT NULL
            OR recipe_type <> 'FINISHED_DISH'
        )
        AND (
            retired_at IS NULL
            OR effective_from IS NULL
            OR retired_at >= effective_from
        )
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Recipe extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_recipes_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "menu_item_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_recipes_menu_item")
    )
    private MenuItem menuItem;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipe_type", nullable = false, length = 30)
    private RecipeType recipeType = RecipeType.FINISHED_DISH;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RecipeStatus status = RecipeStatus.DRAFT;

    @Column(name = "version", nullable = false)
    private int version = 1;

    /**
     * How many portions this makes
     */
    @Column(name = "yield_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal yieldQuantity = BigDecimal.ONE;


    /**
     * Unit measured
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "yield_unit", nullable = false, length = 30)
    private InventoryUnit yieldUnit = InventoryUnit.PORTION;

    @Column(name = "prep_time_minutes")
    private Integer prepTimeMinutes;

    @Column(name = "cook_time_minutes")
    private Integer cookTimeMinutes;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    @Column(name = "theoretical_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal theoreticalCost = BigDecimal.ZERO;

    @Column(name = "effective_from", columnDefinition = "timestamptz")
    private OffsetDateTime effectiveFrom;

    @Column(name = "retired_at", columnDefinition = "timestamptz")
    private OffsetDateTime retiredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_recipes_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_recipes_updated_by_user")
    )
    private User updatedByUser;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, createdAt ASC")
    private List<RecipeComponent> components = new ArrayList<>();

    public void addComponent(RecipeComponent component) {
        if (component == null) {
            return;
        }

        components.add(component);
        component.setRecipe(this);
    }

    public void removeComponent(RecipeComponent component) {
        if (component == null) {
            return;
        }

        components.remove(component);
        component.setRecipe(null);
    }

    @Override
    protected void normalizeFields() {
        code = NormalizationUtils.normalizeCode(code == null ? name : code, 80);
        name = NormalizationUtils.normalize(name);
        description = NormalizationUtils.normalize(description);
        instructions = NormalizationUtils.normalize(instructions);
        yieldQuantity = yieldQuantity == null ? BigDecimal.ONE : yieldQuantity;
        theoreticalCost = theoreticalCost == null ? BigDecimal.ZERO : theoreticalCost;
    }

    @Override
    protected void validateState() {
        if (version <= 0) {
            throw new IllegalStateException("version must be greater than zero");
        }

        if (yieldQuantity == null || yieldQuantity.signum() <= 0) {
            throw new IllegalStateException("yieldQuantity must be greater than zero");
        }

        if (theoreticalCost == null || theoreticalCost.signum() < 0) {
            throw new IllegalStateException("theoreticalCost must not be negative");
        }

        if (prepTimeMinutes != null && prepTimeMinutes < 0) {
            throw new IllegalStateException("prepTimeMinutes must not be negative");
        }

        if (cookTimeMinutes != null && cookTimeMinutes < 0) {
            throw new IllegalStateException("cookTimeMinutes must not be negative");
        }

        if (recipeType == RecipeType.FINISHED_DISH && menuItem == null) {
            throw new IllegalStateException("finished dish recipes must be linked to a menu item");
        }

        if (restaurant != null
                && menuItem != null
                && menuItem.getSection() != null
                && menuItem.getSection().getMenu() != null
                && menuItem.getSection().getMenu().getRestaurant() != null) {
            if (!Objects.equals(restaurant.getId(), menuItem.getSection().getMenu().getRestaurant().getId())) {
                throw new IllegalStateException("recipe menu item must belong to the same restaurant");
            }
        }

        if (retiredAt != null && effectiveFrom != null && retiredAt.isBefore(effectiveFrom)) {
            throw new IllegalStateException("retiredAt must not be before effectiveFrom");
        }
    }
}
