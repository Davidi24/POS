package pos.pos.order.entity;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.inventory.entity.InventoryMovement;
import pos.pos.kds.entity.KdsTicketItem;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "order_line_items",
        indexes = {
                @Index(name = "idx_order_line_items_order_id", columnList = "order_id"),
                @Index(name = "idx_order_line_items_menu_item_id", columnList = "menu_item_id"),
                @Index(name = "idx_order_line_items_variant_id", columnList = "variant_id"),
                @Index(name = "idx_order_line_items_status", columnList = "status")
        }
)
@Check(constraints = """
        char_length(btrim(item_name_snapshot)) > 0
        AND (variant_name_snapshot IS NULL OR char_length(btrim(variant_name_snapshot)) > 0)
        AND (sku_snapshot IS NULL OR char_length(btrim(sku_snapshot)) > 0)
        AND quantity > 0
        AND unit_price_snapshot >= 0
        AND discount_total >= 0
        AND tax_total >= 0
        AND line_total >= 0
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class OrderLineItem extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_order_line_items_order")
    )
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "menu_item_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_order_line_items_menu_item")
    )
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "variant_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_order_line_items_variant")
    )
    private MenuVariant variant;

    @Column(name = "item_name_snapshot", nullable = false, length = 150)
    private String itemNameSnapshot;

    @Column(name = "variant_name_snapshot", length = 120)
    private String variantNameSnapshot;

    @Column(name = "sku_snapshot", length = 80)
    private String skuSnapshot;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPriceSnapshot = BigDecimal.ZERO;

    @Column(name = "price_delta_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceDeltaTotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderLineItemStatus status = OrderLineItemStatus.PENDING;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "orderLineItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<OrderItemOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "orderLineItem")
    @OrderBy("createdAt ASC")
    private List<KdsTicketItem> kdsTicketItems = new ArrayList<>();

    @OneToMany(mappedBy = "orderLineItem")
    @OrderBy("occurredAt ASC")
    private List<InventoryMovement> inventoryMovements = new ArrayList<>();

    public void addOption(OrderItemOption option) {
        if (option == null) {
            return;
        }

        options.add(option);
        option.setOrderLineItem(this);
    }

    public void removeOption(OrderItemOption option) {
        if (option == null) {
            return;
        }

        options.remove(option);
        option.setOrderLineItem(null);
    }

    @Override
    protected void normalizeFields() {
        if (itemNameSnapshot == null && menuItem != null) {
            itemNameSnapshot = menuItem.getName();
        }

        if (variantNameSnapshot == null && variant != null) {
            variantNameSnapshot = variant.getName();
        }

        if (skuSnapshot == null) {
            if (variant != null && variant.getSku() != null) {
                skuSnapshot = variant.getSku();
            } else if (menuItem != null) {
                skuSnapshot = menuItem.getSku();
            }
        }

        if (menuItem != null && unitPriceSnapshot == null) {
            unitPriceSnapshot = menuItem.getBasePrice();
        }

        if (variant != null && priceDeltaTotal == null) {
            priceDeltaTotal = variant.getPriceDelta();
        }

        itemNameSnapshot = NormalizationUtils.normalize(itemNameSnapshot);
        variantNameSnapshot = NormalizationUtils.normalize(variantNameSnapshot);
        skuSnapshot = NormalizationUtils.normalizeUpper(skuSnapshot);
        notes = NormalizationUtils.normalize(notes);
        unitPriceSnapshot = defaultMoney(unitPriceSnapshot);
        priceDeltaTotal = defaultSignedMoney(priceDeltaTotal);
        discountTotal = defaultMoney(discountTotal);
        taxTotal = defaultMoney(taxTotal);
        lineTotal = defaultMoney(lineTotal);
    }

    @Override
    protected void validateState() {
        if (itemNameSnapshot == null) {
            throw new IllegalStateException("itemNameSnapshot is required");
        }

        if (quantity <= 0) {
            throw new IllegalStateException("quantity must be greater than zero");
        }

        validateMoney(unitPriceSnapshot, "unitPriceSnapshot");
        validateMoney(discountTotal, "discountTotal");
        validateMoney(taxTotal, "taxTotal");
        validateMoney(lineTotal, "lineTotal");

        if (variant != null && variant.getMenuItem() != null && menuItem != null) {
            if (!Objects.equals(variant.getMenuItem().getId(), menuItem.getId())) {
                throw new IllegalStateException("variant must belong to the same menu item");
            }
        }

        if (order != null && order.getRestaurant() != null && menuItem != null
                && menuItem.getSection() != null
                && menuItem.getSection().getMenu() != null
                && menuItem.getSection().getMenu().getRestaurant() != null) {
            if (!Objects.equals(
                    order.getRestaurant().getId(),
                    menuItem.getSection().getMenu().getRestaurant().getId()
            )) {
                throw new IllegalStateException("order line item must stay within one restaurant");
            }
        }
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal defaultSignedMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateMoney(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + " is required");
        }

        if (value.signum() < 0) {
            throw new IllegalStateException(fieldName + " must not be negative");
        }
    }
}
