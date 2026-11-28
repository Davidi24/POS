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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.inventory.enums.InventoryMovementType;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_movements",
        indexes = {
                @Index(name = "idx_inventory_movements_location_id", columnList = "location_id"),
                @Index(name = "idx_inventory_movements_inventory_item_id", columnList = "inventory_item_id"),
                @Index(name = "idx_inventory_movements_order_line_item_id", columnList = "order_line_item_id"),
                @Index(name = "idx_inventory_movements_movement_type", columnList = "movement_type"),
                @Index(name = "idx_inventory_movements_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_inventory_movements_created_by", columnList = "created_by"),
                @Index(name = "idx_inventory_movements_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        movement_type IN (
            'PURCHASE',
            'RECEIPT',
            'COUNT_ADJUSTMENT',
            'WASTE',
            'TRANSFER_IN',
            'TRANSFER_OUT',
            'SALE_CONSUMPTION',
            'RETURN',
            'VOID',
            'MANUAL_ADJUSTMENT',
            'PREP_PRODUCTION'
        )
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
        AND quantity_delta <> 0
        AND unit_cost_snapshot >= 0
        AND occurred_at IS NOT NULL
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class InventoryMovement extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "location_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_movements_location")
    )
    private InventoryLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "inventory_item_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_movements_inventory_item")
    )
    private InventoryItem inventoryItem;

    /**
     * Order linked to this change, customer order, missing amount...
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_line_item_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_movements_order_line_item")
    )
    private OrderLineItem orderLineItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private InventoryMovementType movementType = InventoryMovementType.MANUAL_ADJUSTMENT;

    /**
     * 	How much changed, plus or minus (e.g. +20 for a delivery, -2 for a sale).
     */
    @Column(name = "quantity_delta", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityDelta = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 30)
    private InventoryUnit unit = InventoryUnit.EACH;


    /**
     * How much it costed at the specific type
     */
    @Column(name = "unit_cost_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCostSnapshot = BigDecimal.ZERO;

    /**
     * the value of the changed stuff(quantityDelta*unitCosSnapshot)
     */
    @Column(name = "total_cost_delta", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCostDelta = BigDecimal.ZERO;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    /**
     * Purchase, order... + id
     */
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", columnDefinition = "uuid")
    private UUID referenceId;


    @Column(name = "occurred_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime occurredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_movements_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_movements_updated_by_user")
    )
    private User updatedByUser;

    @Override
    protected void normalizeFields() {
        reason = NormalizationUtils.normalize(reason);
        referenceType = NormalizationUtils.normalizeCode(referenceType, 50);
        quantityDelta = quantityDelta == null ? BigDecimal.ZERO : quantityDelta;
        unitCostSnapshot = unitCostSnapshot == null ? BigDecimal.ZERO : unitCostSnapshot;
        totalCostDelta = totalCostDelta == null ? quantityDelta.multiply(unitCostSnapshot) : totalCostDelta;
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    protected void validateState() {
        if (quantityDelta == null || quantityDelta.signum() == 0) {
            throw new IllegalStateException("quantityDelta must not be zero");
        }

        if (unitCostSnapshot == null || unitCostSnapshot.signum() < 0) {
            throw new IllegalStateException("unitCostSnapshot must not be negative");
        }

        if (location != null && inventoryItem != null && location.getRestaurant() != null && inventoryItem.getRestaurant() != null) {
            if (!Objects.equals(location.getRestaurant().getId(), inventoryItem.getRestaurant().getId())) {
                throw new IllegalStateException("inventory movement item and location must belong to the same restaurant");
            }
        }

        if (orderLineItem != null
                && orderLineItem.getMenuItem() != null
                && orderLineItem.getMenuItem().getSection() != null
                && orderLineItem.getMenuItem().getSection().getMenu() != null
                && orderLineItem.getMenuItem().getSection().getMenu().getRestaurant() != null
                && inventoryItem != null
                && inventoryItem.getRestaurant() != null) {
            if (!Objects.equals(
                    orderLineItem.getMenuItem().getSection().getMenu().getRestaurant().getId(),
                    inventoryItem.getRestaurant().getId()
            )) {
                throw new IllegalStateException("inventory movement order line item must belong to the same restaurant");
            }
        }
    }
}
