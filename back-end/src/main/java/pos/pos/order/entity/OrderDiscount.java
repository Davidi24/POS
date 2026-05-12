package pos.pos.order.entity;

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
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.order.enums.OrderDiscountType;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "order_discounts",
        indexes = {
                @Index(name = "idx_order_discounts_order_id", columnList = "order_id"),
                @Index(name = "idx_order_discounts_applied_by", columnList = "applied_by")
        }
)
@Check(constraints = """
        char_length(btrim(name)) > 0
        AND discount_value >= 0
        AND amount_applied >= 0
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class OrderDiscount extends AbstractTimestampedEntity {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_order_discounts_order")
    )
    private Order order;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private OrderDiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Column(name = "amount_applied", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountApplied = BigDecimal.ZERO;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "applied_by", columnDefinition = "uuid")
    private UUID appliedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "applied_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_order_discounts_applied_by_user")
    )
    private User appliedByUser;

    @Override
    protected void normalizeFields() {
        name = NormalizationUtils.normalize(name);
        reason = NormalizationUtils.normalize(reason);
        discountValue = defaultMoney(discountValue);
        amountApplied = defaultMoney(amountApplied);
    }

    @Override
    protected void validateState() {
        if (name == null) {
            throw new IllegalStateException("name is required");
        }

        if (discountType == null) {
            throw new IllegalStateException("discountType is required");
        }

        validateMoney(discountValue, "discountValue");
        validateMoney(amountApplied, "amountApplied");

        if (discountType == OrderDiscountType.PERCENTAGE && discountValue.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalStateException("percentage discounts must not exceed 100");
        }
    }

    private BigDecimal defaultMoney(BigDecimal value) {
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
