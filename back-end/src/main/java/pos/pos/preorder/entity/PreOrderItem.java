package pos.pos.preorder.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// One dish in a pre-order, with the prices the guest paid; the kitchen order is built from these snapshots.
@Entity
@Table(
        name = "pre_order_items",
        indexes = {
                @Index(name = "idx_pre_order_items_pre_order_id", columnList = "pre_order_id"),
                @Index(name = "idx_pre_order_items_menu_item_id", columnList = "menu_item_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class PreOrderItem extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pre_order_id", nullable = false, columnDefinition = "uuid", foreignKey = @ForeignKey(name = "fk_pre_order_items_pre_order"))
    private PreOrder preOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false, columnDefinition = "uuid", foreignKey = @ForeignKey(name = "fk_pre_order_items_menu_item"))
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", columnDefinition = "uuid", foreignKey = @ForeignKey(name = "fk_pre_order_items_variant"))
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

    @Column(name = "variant_price_delta_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal variantPriceDeltaSnapshot = BigDecimal.ZERO;

    @Column(name = "price_delta_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceDeltaTotal = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @OneToMany(mappedBy = "preOrderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<PreOrderItemOption> options = new ArrayList<>();

    public void addOption(PreOrderItemOption option) {
        options.add(option);
        option.setPreOrderItem(this);
    }

    @Override
    protected void normalizeFields() {
        itemNameSnapshot = NormalizationUtils.normalize(itemNameSnapshot);
        variantNameSnapshot = NormalizationUtils.normalize(variantNameSnapshot);
        skuSnapshot = NormalizationUtils.normalizeUpper(skuSnapshot);
        notes = NormalizationUtils.normalize(notes);
    }

    @Override
    protected void validateState() {
        if (quantity <= 0) {
            throw new IllegalStateException("quantity must be greater than zero");
        }
    }
}
