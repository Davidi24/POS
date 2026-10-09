package pos.pos.preorder.entity;

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
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.order.entity.Order;
import pos.pos.preorder.enums.PreOrderPaymentStatus;
import pos.pos.preorder.enums.PreOrderSource;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.reservation.entity.Reservation;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Dishes chosen together with a reservation. Paid up front, held until shortly before the booking,
// then turned into a real order for the kitchen (see PreOrderKitchenService).
@Entity
@Table(
        name = "pre_orders",
        indexes = {
                @Index(name = "idx_pre_orders_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_pre_orders_reservation_id", columnList = "reservation_id"),
                @Index(name = "idx_pre_orders_order_id", columnList = "order_id"),
                @Index(name = "idx_pre_orders_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class PreOrder extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false, columnDefinition = "uuid", foreignKey = @ForeignKey(name = "fk_pre_orders_restaurant"))
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, columnDefinition = "uuid", foreignKey = @ForeignKey(name = "fk_pre_orders_reservation"))
    private Reservation reservation;

    // The order the kitchen got; set when the pre-order is sent.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", columnDefinition = "uuid", foreignKey = @ForeignKey(name = "fk_pre_orders_order"))
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PreOrderStatus status = PreOrderStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PreOrderPaymentStatus paymentStatus = PreOrderPaymentStatus.PAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private PreOrderSource source = PreOrderSource.STAFF;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "service_charge_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal serviceChargeTotal = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    // Money given back: the paid amount minus the card fee on a timely cancel, all of it when the restaurant declined,
    // or a goodwill part of a kept payment.
    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    // Taken from the restaurant setting when the pre-order is first placed, so later setting changes don't move its cutoff.
    @Column(name = "lead_minutes", nullable = false)
    private int leadMinutes;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "sent_at", columnDefinition = "timestamptz")
    private OffsetDateTime sentAt;

    @Column(name = "cancelled_at", columnDefinition = "timestamptz")
    private OffsetDateTime cancelledAt;

    @Column(name = "forfeited_at", columnDefinition = "timestamptz")
    private OffsetDateTime forfeitedAt;

    @OneToMany(mappedBy = "preOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<PreOrderItem> items = new ArrayList<>();

    public void addItem(PreOrderItem item) {
        items.add(item);
        item.setPreOrder(this);
    }

    // Orphan removal deletes the old rows.
    public void clearItems() {
        items.clear();
    }

    // When the kitchen gets it (and the last moment to change or cancel for a refund). Follows the booking if it moves.
    public OffsetDateTime sendAt() {
        OffsetDateTime start = reservation == null ? null : reservation.getReservationStart();
        return start == null ? null : start.minusMinutes(leadMinutes);
    }

    @Override
    protected void normalizeFields() {
        currency = NormalizationUtils.normalizeUpper(currency);
        notes = NormalizationUtils.normalize(notes);
        cancellationReason = NormalizationUtils.normalize(cancellationReason);
    }

    @Override
    protected void validateState() {
        if (restaurant != null && reservation != null && reservation.getRestaurant() != null
                && !Objects.equals(reservation.getRestaurant().getId(), restaurant.getId())) {
            throw new IllegalStateException("pre-order reservation must belong to the same restaurant");
        }
        if ((status == PreOrderStatus.SENT || status == PreOrderStatus.FORFEITED) && (order == null || sentAt == null)) {
            throw new IllegalStateException("a sent pre-order must reference its order");
        }
        if (leadMinutes < 0) {
            throw new IllegalStateException("leadMinutes must not be negative");
        }
    }
}
