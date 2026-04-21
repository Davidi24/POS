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
import lombok.Setter;

@Getter
@Setter
@Entity
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
}