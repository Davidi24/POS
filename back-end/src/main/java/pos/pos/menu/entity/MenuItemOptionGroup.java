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

/**
 * Represents the link between a menu item and an option group.
 *
 * Connects modifiers (like toppings or side choices)
 * to a specific menu item.
 *
 * Also stores rules for that relationship,
 * such as required selections or selection limits.
 */

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "menu_item_option_groups",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_menu_item_option_groups_item_group", columnNames = {"menu_item_id", "option_group_id"})
        },
        indexes = {
                @Index(name = "idx_menu_item_option_groups_menu_item_id", columnList = "menu_item_id"),
                @Index(name = "idx_menu_item_option_groups_option_group_id", columnList = "option_group_id")
        }
)
@Check(constraints = """
        display_order >= 0
        AND (min_select_override IS NULL OR min_select_override >= 0)
        AND (max_select_override IS NULL OR max_select_override >= 0)
        AND (
            min_select_override IS NULL
            OR max_select_override IS NULL
            OR min_select_override <= max_select_override
        )
        """)
public class MenuItemOptionGroup extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id", nullable = false)
    private OptionGroup optionGroup;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "min_select_override")
    private Integer minSelectOverride;

    @Column(name = "max_select_override")
    private Integer maxSelectOverride;

    @Column(name = "is_required_override")
    private Boolean requiredOverride;

    @Override
    protected void validateState() {
        if (displayOrder != null && displayOrder < 0) {
            throw new IllegalStateException("displayOrder must be greater than or equal to zero");
        }

        if (minSelectOverride != null && minSelectOverride < 0) {
            throw new IllegalStateException("minSelectOverride must be greater than or equal to zero");
        }

        if (maxSelectOverride != null && maxSelectOverride < 0) {
            throw new IllegalStateException("maxSelectOverride must be greater than or equal to zero");
        }

        if (minSelectOverride != null && maxSelectOverride != null && minSelectOverride > maxSelectOverride) {
            throw new IllegalStateException("minSelectOverride must be less than or equal to maxSelectOverride");
        }
    }
}
