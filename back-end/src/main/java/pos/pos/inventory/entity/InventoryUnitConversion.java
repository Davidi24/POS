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

import java.math.BigDecimal;

// One item-specific conversion rule: "1 fromUnit = conversionFactor toUnit" for this exact
// InventoryItem only (e.g. "1 CASE = 12 BOTTLE" for one particular product). Not a shared
// aggregate child like RecipeComponent/InventoryCountLine -- InventoryItem is intentionally
// left untouched, so this is a standalone entity with only the ManyToOne side, managed
// directly through its own repository rather than cascaded through the item.
@Entity
@Table(
        name = "inventory_unit_conversions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inventory_unit_conversions_item_from_to",
                        columnNames = {"inventory_item_id", "from_unit", "to_unit"}
                )
        },
        indexes = {
                @Index(name = "idx_inventory_unit_conversions_inventory_item_id", columnList = "inventory_item_id")
        }
)
@Check(constraints = """
        from_unit IN (
            'EACH', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER', 'OUNCE', 'POUND', 'CUP',
            'TABLESPOON', 'TEASPOON', 'PORTION', 'CASE', 'BOTTLE', 'PACK', 'TRAY'
        )
        AND to_unit IN (
            'EACH', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER', 'OUNCE', 'POUND', 'CUP',
            'TABLESPOON', 'TEASPOON', 'PORTION', 'CASE', 'BOTTLE', 'PACK', 'TRAY'
        )
        AND conversion_factor > 0
        AND from_unit <> to_unit
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class InventoryUnitConversion extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "inventory_item_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_unit_conversions_inventory_item")
    )
    private InventoryItem inventoryItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_unit", nullable = false, length = 30)
    private InventoryUnit fromUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_unit", nullable = false, length = 30)
    private InventoryUnit toUnit;

    @Column(name = "conversion_factor", nullable = false, precision = 19, scale = 6)
    private BigDecimal conversionFactor;

    @Override
    protected void validateState() {
        if (conversionFactor == null || conversionFactor.signum() <= 0) {
            throw new IllegalStateException("conversionFactor must be greater than zero");
        }

        if (fromUnit != null && fromUnit == toUnit) {
            throw new IllegalStateException("fromUnit and toUnit must be different");
        }
    }
}
