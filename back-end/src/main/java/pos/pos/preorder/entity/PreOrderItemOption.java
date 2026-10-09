package pos.pos.preorder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.menu.entity.OptionItem;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;

@Entity
@Table(
        name = "pre_order_item_options",
        indexes = @Index(name = "idx_pre_order_item_options_item_id", columnList = "pre_order_item_id")
)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class PreOrderItemOption extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pre_order_item_id", nullable = false, columnDefinition = "uuid", foreignKey = @ForeignKey(name = "fk_pre_order_item_options_item"))
    private PreOrderItem preOrderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_item_id", nullable = false, columnDefinition = "uuid", foreignKey = @ForeignKey(name = "fk_pre_order_item_options_option_item"))
    private OptionItem optionItem;

    @Column(name = "option_name_snapshot", nullable = false, length = 150)
    private String optionNameSnapshot;

    @Column(name = "price_delta_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceDeltaSnapshot = BigDecimal.ZERO;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Override
    protected void normalizeFields() {
        optionNameSnapshot = NormalizationUtils.normalize(optionNameSnapshot);
        notes = NormalizationUtils.normalize(notes);
    }

    @Override
    protected void validateState() {
        if (quantity <= 0) {
            throw new IllegalStateException("quantity must be greater than zero");
        }
    }
}
