package pos.pos.payment.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.customer.entity.Customer;
import pos.pos.order.entity.Order;
import pos.pos.payment.enums.PaymentMethod;
import pos.pos.payment.enums.PaymentStatus;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.shift.entity.Shift;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_order_reference", columnNames = {"order_id", "reference_number"})
        },
        indexes = {
                @Index(name = "idx_payments_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_payments_branch_id", columnList = "branch_id"),
                @Index(name = "idx_payments_order_id", columnList = "order_id"),
                @Index(name = "idx_payments_shift_id", columnList = "shift_id"),
                @Index(name = "idx_payments_customer_id", columnList = "customer_id"),
                @Index(name = "idx_payments_method", columnList = "method"),
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_paid_at", columnList = "paid_at"),
                @Index(name = "idx_payments_created_by", columnList = "created_by"),
                @Index(name = "idx_payments_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(reference_number)) > 0
        AND char_length(currency) = 3
        AND method IN (
            'CASH',
            'CARD',
            'CONTACTLESS',
            'DIGITAL_WALLET',
            'GIFT_CARD',
            'HOUSE_ACCOUNT',
            'LOYALTY',
            'BANK_TRANSFER',
            'OTHER'
        )
        AND status IN (
            'PENDING',
            'AUTHORIZED',
            'CAPTURED',
            'PARTIALLY_REFUNDED',
            'REFUNDED',
            'FAILED',
            'VOIDED'
        )
        AND amount > 0
        AND tip_amount >= 0
        AND surcharge_amount >= 0
        AND refunded_amount >= 0
        AND refunded_amount <= (amount + tip_amount + surcharge_amount)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Payment extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_payments_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_payments_branch")
    )
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_payments_order")
    )
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "shift_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_payments_shift")
    )
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "customer_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_payments_customer")
    )
    private Customer customer;

    @Column(name = "reference_number", nullable = false, length = 50)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    private PaymentMethod method = PaymentMethod.CARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "tip_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal tipAmount = BigDecimal.ZERO;

    @Column(name = "surcharge_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal surchargeAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "gateway_name", length = 100)
    private String gatewayName;

    @Column(name = "card_brand", length = 40)
    private String cardBrand;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "receipt_number", length = 50)
    private String receiptNumber;

    @Column(name = "paid_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime paidAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_payments_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_payments_updated_by_user")
    )
    private User updatedByUser;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("processedAt ASC, createdAt ASC")
    private List<PaymentTransaction> transactions = new ArrayList<>();

    public void addTransaction(PaymentTransaction transaction) {
        if (transaction == null) {
            return;
        }

        transactions.add(transaction);
        transaction.setPayment(this);
    }

    public void removeTransaction(PaymentTransaction transaction) {
        if (transaction == null) {
            return;
        }

        transactions.remove(transaction);
        transaction.setPayment(null);
    }

    @Override
    protected void normalizeFields() {
        referenceNumber = NormalizationUtils.normalizeUpper(referenceNumber);
        currency = NormalizationUtils.normalizeUpper(currency == null && order != null ? order.getCurrency() : currency);
        externalReference = NormalizationUtils.normalize(externalReference);
        gatewayName = NormalizationUtils.normalize(gatewayName);
        cardBrand = NormalizationUtils.normalizeUpper(cardBrand);
        cardLast4 = NormalizationUtils.normalize(cardLast4);
        receiptNumber = NormalizationUtils.normalizeUpper(receiptNumber);
        notes = NormalizationUtils.normalize(notes);
        amount = defaultMoney(amount);
        tipAmount = defaultMoney(tipAmount);
        surchargeAmount = defaultMoney(surchargeAmount);
        refundedAmount = defaultMoney(refundedAmount);

        if (paidAt == null) {
            paidAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    protected void validateState() {
        validatePositive(amount, "amount");
        validateNonNegative(tipAmount, "tipAmount");
        validateNonNegative(surchargeAmount, "surchargeAmount");
        validateNonNegative(refundedAmount, "refundedAmount");

        if (refundedAmount.compareTo(amount.add(tipAmount).add(surchargeAmount)) > 0) {
            throw new IllegalStateException("refundedAmount must not exceed the captured amount");
        }

        if (currency == null || currency.length() != 3) {
            throw new IllegalStateException("currency must be a 3-letter code");
        }

        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("payment branch must belong to the same restaurant");
            }
        }

        if (restaurant != null && order != null && order.getRestaurant() != null) {
            if (!Objects.equals(order.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("payment order must belong to the same restaurant");
            }
        }

        if (branch != null && order != null && order.getBranch() != null) {
            if (!Objects.equals(order.getBranch().getId(), branch.getId())) {
                throw new IllegalStateException("payment order must belong to the same branch");
            }
        }

        if (restaurant != null && customer != null && customer.getRestaurant() != null) {
            if (!Objects.equals(customer.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("payment customer must belong to the same restaurant");
            }
        }

        if (restaurant != null && shift != null && shift.getRestaurant() != null) {
            if (!Objects.equals(shift.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("payment shift must belong to the same restaurant");
            }
        }

        if (branch != null && shift != null && shift.getBranch() != null) {
            if (!Objects.equals(shift.getBranch().getId(), branch.getId())) {
                throw new IllegalStateException("payment shift must belong to the same branch");
            }
        }
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validatePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException(fieldName + " must be greater than zero");
        }
    }

    private void validateNonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.signum() < 0) {
            throw new IllegalStateException(fieldName + " must not be negative");
        }
    }
}
