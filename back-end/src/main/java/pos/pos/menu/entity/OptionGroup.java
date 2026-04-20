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
import lombok.Setter;
import pos.pos.restaurant.entity.Restaurant;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
        name = "option-groups",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_option_groups_restaurant_name", columnNames = {"restaurant_id", "name"})
        },
        indexes = {
                @Index(name = "idx_option_groups_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_option_groups_type_id", columnList = "type_id")
        }
)
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
}