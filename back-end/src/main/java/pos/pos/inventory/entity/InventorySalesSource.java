package pos.pos.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;

import java.util.Objects;

/** The branch-specific location from which a tracked ingredient is consumed when its menu item is fulfilled. */
@Entity
@Table(
        name = "inventory_sales_sources",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_sales_sources_branch_item",
                columnNames = {"branch_id", "inventory_item_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class InventorySalesSource extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inventory_sales_sources_restaurant"))
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inventory_sales_sources_branch"))
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inventory_sales_sources_item"))
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inventory_sales_sources_location"))
    private InventoryLocation location;

    @Override
    protected void validateState() {
        if (restaurant == null || branch == null || inventoryItem == null || location == null) {
            throw new IllegalStateException("A sale stock source requires its restaurant, branch, item, and location");
        }
        if (!Objects.equals(restaurant.getId(), branch.getRestaurant().getId())
                || !Objects.equals(restaurant.getId(), inventoryItem.getRestaurant().getId())
                || !Objects.equals(restaurant.getId(), location.getRestaurant().getId())) {
            throw new IllegalStateException("A sale stock source must use records from one restaurant");
        }
        if (location.getBranch() != null && !Objects.equals(branch.getId(), location.getBranch().getId())) {
            throw new IllegalStateException("A branch sale stock source cannot use another branch's location");
        }
    }
}
