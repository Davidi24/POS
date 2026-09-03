package pos.pos.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Balance between the items
 */
@Entity
@Table(
        name = "inventory_levels",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_levels_location_item", columnNames = {"location_id", "inventory_item_id"})
        },
        indexes = {
                @Index(name = "idx_inventory_levels_location_id", columnList = "location_id"),
                @Index(name = "idx_inventory_levels_inventory_item_id", columnList = "inventory_item_id"),
                @Index(name = "idx_inventory_levels_last_counted_at", columnList = "last_counted_at"),
                @Index(name = "idx_inventory_levels_last_movement_at", columnList = "last_movement_at")
        }
)
@Check(constraints = """
        on_hand_quantity >= 0
        AND committed_quantity >= 0
        AND (par_quantity IS NULL OR par_quantity >= 0)
        AND (calculated_reorder_point IS NULL OR calculated_reorder_point >= 0)
        AND (manual_reorder_point IS NULL OR manual_reorder_point >= 0)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class InventoryLevel extends AbstractTimestampedEntity {

    /**
     * Which location this balance is in
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "location_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_levels_location")
    )
    private InventoryLocation location;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "inventory_item_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_levels_inventory_item")
    )
    private InventoryItem inventoryItem;

    @Column(name = "on_hand_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal onHandQuantity = BigDecimal.ZERO;


    /**
     * 	How much is already "reserved" for something, but hasn't physically left the shelf yet.
     */
    @Column(name = "committed_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal committedQuantity = BigDecimal.ZERO;


    /**
     * 	The target stock amount for this item, at this specific location.
     */
    @Column(name = "par_quantity", precision = 12, scale = 3)
    private BigDecimal parQuantity;

    /**
     * The "getting low, reorder now" threshold worked out automatically by the system.
     * The actual calculation logic is a later phase -- for now this is just a settable field.
     */
    @Column(name = "calculated_reorder_point", precision = 12, scale = 3)
    private BigDecimal calculatedReorderPoint;

    /**
     * An explicit reorder-point override a manager can set for this item at this location,
     * taking priority over calculatedReorderPoint whenever it's set.
     */
    @Column(name = "manual_reorder_point", precision = 12, scale = 3)
    private BigDecimal manualReorderPoint;

    /**
     * Last physical count
     */
    @Column(name = "last_counted_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastCountedAt;

    @Column(name = "last_movement_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastMovementAt;

    @Override
    protected void normalizeFields() {
        onHandQuantity = defaultQuantity(onHandQuantity);
        committedQuantity = defaultQuantity(committedQuantity);
        parQuantity = nullableQuantity(parQuantity);
        calculatedReorderPoint = nullableQuantity(calculatedReorderPoint);
        manualReorderPoint = nullableQuantity(manualReorderPoint);
    }

    @Override
    protected void validateState() {
        validateNonNegative(onHandQuantity, "onHandQuantity");
        validateNonNegative(committedQuantity, "committedQuantity");
        validateNonNegative(parQuantity, "parQuantity");
        validateNonNegative(calculatedReorderPoint, "calculatedReorderPoint");
        validateNonNegative(manualReorderPoint, "manualReorderPoint");

        if (location != null && inventoryItem != null && location.getRestaurant() != null && inventoryItem.getRestaurant() != null) {
            if (!Objects.equals(location.getRestaurant().getId(), inventoryItem.getRestaurant().getId())) {
                throw new IllegalStateException("inventory level item and location must belong to the same restaurant");
            }
        }
    }

    /**
     * Not a real column -- just a shortcut for "which reorder point actually applies right now."
     * A manager's manual override always wins if it's set; otherwise falls back to whatever the
     * system last calculated; otherwise null (nothing set at the level yet at all).
     */
    public BigDecimal getEffectiveReorderPoint() {
        if (manualReorderPoint != null) {
            return manualReorderPoint;
        }

        return calculatedReorderPoint;
    }

    private BigDecimal defaultQuantity(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal nullableQuantity(BigDecimal value) {
        return value == null ? null : value;
    }

    private void validateNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.signum() < 0) {
            throw new IllegalStateException(fieldName + " must not be negative");
        }
    }
}
