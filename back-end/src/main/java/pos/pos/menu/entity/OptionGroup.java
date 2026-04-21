package pos.pos.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a group of selectable modifiers
 * that can be attached to menu items.
 *
 * Examples include toppings, sauces, side choices etc.
 *
 * An option group contains one or more option items.
 */

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "`option-groups`",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_option_groups_restaurant_name", columnNames = {"restaurant_id", "name"})
        },
        indexes = {
                @Index(name = "idx_option_groups_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_option_groups_type_id", columnList = "type_id")
        }
)
@Check(constraints = """
        char_length(btrim(name)) > 0
        AND display_order >= 0
        AND (min_select IS NULL OR min_select >= 0)
        AND (max_select IS NULL OR max_select >= 0)
        AND (min_select IS NULL OR max_select IS NULL OR min_select <= max_select)
        """)
public class OptionGroup extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id", nullable = false)
    private OptionGroupType type;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "min_select")
    private Integer minSelect;

    @Column(name = "max_select")
    private Integer maxSelect;

    @Column(name = "is_required", nullable = false)
    private boolean required = false;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "optionGroup")
    private List<OptionItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "optionGroup")
    private List<MenuItemOptionGroup> menuItemLinks = new ArrayList<>();

    @Override
    protected void normalizeFields() {
        name = NormalizationUtils.normalize(name);
        description = NormalizationUtils.normalize(description);
    }

    @Override
    protected void validateState() {
        validateSelectionBounds(minSelect, maxSelect, "option group");

        if (displayOrder != null && displayOrder < 0) {
            throw new IllegalStateException("displayOrder must be greater than or equal to zero");
        }
    }

    private void validateSelectionBounds(Integer min, Integer max, String context) {
        if (min != null && min < 0) {
            throw new IllegalStateException(context + " minSelect must be greater than or equal to zero");
        }

        if (max != null && max < 0) {
            throw new IllegalStateException(context + " maxSelect must be greater than or equal to zero");
        }

        if (min != null && max != null && min > max) {
            throw new IllegalStateException(context + " minSelect must be less than or equal to maxSelect");
        }
    }
}
