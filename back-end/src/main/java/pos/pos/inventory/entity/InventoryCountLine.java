package pos.pos.inventory.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(
        name = "inventory_count_lines",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_count_lines_count_item", columnNames = {"inventory_count_id", "inventory_item_id"})
        },
        indexes = {
                @Index(name = "idx_inventory_count_lines_inventory_count_id", columnList = "inventory_count_id"),
                @Index(name = "idx_inventory_count_lines_inventory_item_id", columnList = "inventory_item_id")
        }
)
@Check(constraints = """
        expected_quantity >= 0
        AND counted_quantity >= 0
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
        AND unit_cost_snapshot >= 0
        AND variance_value >= 0
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class InventoryCountLine extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "inventory_count_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_count_lines_inventory_count")
    )
    private InventoryCount inventoryCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "inventory_item_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_count_lines_inventory_item")
    )
    private InventoryItem inventoryItem;

    @Column(name = "item_name_snapshot", nullable = false, length = 150)
    private String itemNameSnapshot;

    @Column(name = "expected_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal expectedQuantity = BigDecimal.ZERO;

    @Column(name = "counted_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal countedQuantity = BigDecimal.ZERO;

    @Column(name = "variance_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal varianceQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 30)
    private InventoryUnit unit = InventoryUnit.EACH;

    @Column(name = "unit_cost_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCostSnapshot;

    @Column(name = "variance_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal varianceValue = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Override
    protected void normalizeFields() {
        if (itemNameSnapshot == null && inventoryItem != null) {
            itemNameSnapshot = inventoryItem.getName();
        }

        itemNameSnapshot = NormalizationUtils.normalize(itemNameSnapshot);
        expectedQuantity = defaultQuantity(expectedQuantity);
        countedQuantity = defaultQuantity(countedQuantity);
        varianceQuantity = countedQuantity.subtract(expectedQuantity);
        unitCostSnapshot = unitCostSnapshot == null
                ? inventoryItem != null && inventoryItem.getCostPerUnit() != null ? inventoryItem.getCostPerUnit() : BigDecimal.ZERO
                : unitCostSnapshot;
        varianceValue = varianceQuantity.abs().multiply(unitCostSnapshot);
        notes = NormalizationUtils.normalize(notes);
    }

    @Override
    protected void validateState() {
        validateNonNegative(expectedQuantity, "expectedQuantity");
        validateNonNegative(countedQuantity, "countedQuantity");
        validateNonNegative(unitCostSnapshot, "unitCostSnapshot");
        validateNonNegative(varianceValue, "varianceValue");

        if (inventoryCount != null && inventoryItem != null
                && inventoryCount.getRestaurant() != null
                && inventoryItem.getRestaurant() != null) {
            if (!Objects.equals(inventoryCount.getRestaurant().getId(), inventoryItem.getRestaurant().getId())) {
                throw new IllegalStateException("inventory count line item must belong to the same restaurant");
            }
        }
    }

    private BigDecimal defaultQuantity(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateNonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.signum() < 0) {
            throw new IllegalStateException(fieldName + " must not be negative");
        }
    }
}
