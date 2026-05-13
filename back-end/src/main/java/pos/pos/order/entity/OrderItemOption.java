package pos.pos.order.entity;

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
import pos.pos.menu.entity.OptionItem;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(
        name = "order_item_options",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_item_options_line_option", columnNames = {"order_line_item_id", "option_item_id"})
        },
        indexes = {
                @Index(name = "idx_order_item_options_order_line_item_id", columnList = "order_line_item_id"),
                @Index(name = "idx_order_item_options_option_item_id", columnList = "option_item_id")
        }
)
@Check(constraints = """
        char_length(btrim(option_name_snapshot)) > 0
        AND quantity > 0
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class OrderItemOption extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_line_item_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_order_item_options_order_line_item")
    )
    private OrderLineItem orderLineItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "option_item_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_order_item_options_option_item")
    )
    private OptionItem optionItem;

    @Column(name = "option_name_snapshot", nullable = false, length = 150)
    private String optionNameSnapshot;

    @Column(name = "price_delta_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceDeltaSnapshot;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Override
    protected void normalizeFields() {
        if (optionNameSnapshot == null && optionItem != null) {
            optionNameSnapshot = optionItem.getName();
        }

        if (optionItem != null && priceDeltaSnapshot == null) {
            priceDeltaSnapshot = optionItem.getPriceDelta();
        }

        optionNameSnapshot = NormalizationUtils.normalize(optionNameSnapshot);
        notes = NormalizationUtils.normalize(notes);
        priceDeltaSnapshot = priceDeltaSnapshot == null ? BigDecimal.ZERO : priceDeltaSnapshot;
    }

    @Override
    protected void validateState() {
        if (optionNameSnapshot == null) {
            throw new IllegalStateException("optionNameSnapshot is required");
        }

        if (quantity <= 0) {
            throw new IllegalStateException("quantity must be greater than zero");
        }

        if (orderLineItem != null && optionItem != null
                && orderLineItem.getOrder() != null
                && orderLineItem.getOrder().getRestaurant() != null
                && optionItem.getOptionGroup() != null
                && optionItem.getOptionGroup().getRestaurant() != null) {
            if (!Objects.equals(
                    orderLineItem.getOrder().getRestaurant().getId(),
                    optionItem.getOptionGroup().getRestaurant().getId()
            )) {
                throw new IllegalStateException("order item option must stay within one restaurant");
            }
        }
    }
}
