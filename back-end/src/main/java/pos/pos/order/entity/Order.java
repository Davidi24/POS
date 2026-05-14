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
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.customer.entity.Customer;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.order.enums.OrderFulfillmentStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderSource;
import pos.pos.order.enums.OrderType;
import pos.pos.payment.entity.Payment;
import pos.pos.reservation.entity.Reservation;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.tables.entity.RestaurantTable;
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
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_restaurant_order_number", columnNames = {"restaurant_id", "order_number"})
        },
        indexes = {
                @Index(name = "idx_orders_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_orders_branch_id", columnList = "branch_id"),
                @Index(name = "idx_orders_table_id", columnList = "table_id"),
                @Index(name = "idx_orders_reservation_id", columnList = "reservation_id"),
                @Index(name = "idx_orders_customer_id", columnList = "customer_id"),
                @Index(name = "idx_orders_status", columnList = "status"),
                @Index(name = "idx_orders_fulfillment_status", columnList = "fulfillment_status"),
                @Index(name = "idx_orders_payment_status", columnList = "payment_status"),
                @Index(name = "idx_orders_opened_at", columnList = "opened_at"),
                @Index(name = "idx_orders_created_by", columnList = "created_by"),
                @Index(name = "idx_orders_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(order_number)) > 0
        AND char_length(currency) = 3
        AND guest_count > 0
        AND subtotal >= 0
        AND discount_total >= 0
        AND tax_total >= 0
        AND service_charge_total >= 0
        AND total >= 0
        AND (closed_at IS NULL OR closed_at >= opened_at)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Order extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_orders_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_orders_branch")
    )
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "table_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_orders_table")
    )
    private RestaurantTable restaurantTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reservation_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_orders_reservation")
    )
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "customer_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_orders_customer")
    )
    private Customer customer;

    @Column(name = "order_number", nullable = false, length = 50)
    private String orderNumber;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 30)
    private OrderType orderType = OrderType.DINE_IN;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private OrderSource source = OrderSource.POS;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status = OrderStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 30)
    private OrderFulfillmentStatus fulfillmentStatus = OrderFulfillmentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private OrderPaymentStatus paymentStatus = OrderPaymentStatus.UNPAID;

    @Column(name = "guest_count", nullable = false)
    private int guestCount = 1;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "service_charge_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal serviceChargeTotal = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "opened_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime openedAt;

    @Column(name = "closed_at", columnDefinition = "timestamptz")
    private OffsetDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_orders_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_orders_updated_by_user")
    )
    private User updatedByUser;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<OrderLineItem> lineItems = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<OrderDiscount> discounts = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<OrderEvent> events = new ArrayList<>();

    @OneToMany(mappedBy = "order")
    @OrderBy("paidAt ASC")
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "order")
    @OrderBy("createdAt ASC")
    private List<KdsTicket> kdsTickets = new ArrayList<>();

    public void addLineItem(OrderLineItem lineItem) {
        if (lineItem == null) {
            return;
        }

        lineItems.add(lineItem);
        lineItem.setOrder(this);
    }

    public void removeLineItem(OrderLineItem lineItem) {
        if (lineItem == null) {
            return;
        }

        lineItems.remove(lineItem);
        lineItem.setOrder(null);
    }

    public void addDiscount(OrderDiscount discount) {
        if (discount == null) {
            return;
        }

        discounts.add(discount);
        discount.setOrder(this);
    }

    public void removeDiscount(OrderDiscount discount) {
        if (discount == null) {
            return;
        }

        discounts.remove(discount);
        discount.setOrder(null);
    }

    public void addEvent(OrderEvent event) {
        if (event == null) {
            return;
        }

        events.add(event);
        event.setOrder(this);
    }

    public void removeEvent(OrderEvent event) {
        if (event == null) {
            return;
        }

        events.remove(event);
        event.setOrder(null);
    }

    @Override
    protected void normalizeFields() {
        orderNumber = NormalizationUtils.normalizeUpper(orderNumber);
        currency = NormalizationUtils.normalizeUpper(currency == null && restaurant != null ? restaurant.getCurrency() : currency);
        notes = NormalizationUtils.normalize(notes);
        subtotal = defaultMoney(subtotal);
        discountTotal = defaultMoney(discountTotal);
        taxTotal = defaultMoney(taxTotal);
        serviceChargeTotal = defaultMoney(serviceChargeTotal);
        total = defaultMoney(total);

        if (openedAt == null) {
            openedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }

        if (status == OrderStatus.CLOSED && closedAt == null) {
            closedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    protected void validateState() {
        if (guestCount <= 0) {
            throw new IllegalStateException("guestCount must be greater than zero");
        }

        validateMoney(subtotal, "subtotal");
        validateMoney(discountTotal, "discountTotal");
        validateMoney(taxTotal, "taxTotal");
        validateMoney(serviceChargeTotal, "serviceChargeTotal");
        validateMoney(total, "total");

        if (currency == null || currency.length() != 3) {
            throw new IllegalStateException("currency must be a 3-letter code");
        }

        if (openedAt == null) {
            throw new IllegalStateException("openedAt is required");
        }

        if (closedAt != null) {
            if (status != OrderStatus.CLOSED) {
                throw new IllegalStateException("closedAt requires CLOSED status");
            }

            if (closedAt.isBefore(openedAt)) {
                throw new IllegalStateException("closedAt must not be before openedAt");
            }
        }

        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("order branch must belong to the same restaurant");
            }
        }

        if (restaurant != null && customer != null && customer.getRestaurant() != null) {
            if (!Objects.equals(customer.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("order customer must belong to the same restaurant");
            }
        }

        if (restaurant != null && restaurantTable != null && restaurantTable.getRestaurant() != null) {
            if (!Objects.equals(restaurantTable.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("order table must belong to the same restaurant");
            }
        }

        if (branch != null && restaurantTable != null && restaurantTable.getBranch() != null) {
            if (!Objects.equals(restaurantTable.getBranch().getId(), branch.getId())) {
                throw new IllegalStateException("order table must belong to the same branch");
            }
        }

        if (restaurant != null && reservation != null && reservation.getRestaurant() != null) {
            if (!Objects.equals(reservation.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("order reservation must belong to the same restaurant");
            }
        }

        if (branch != null && reservation != null && reservation.getBranch() != null) {
            if (!Objects.equals(reservation.getBranch().getId(), branch.getId())) {
                throw new IllegalStateException("order reservation must belong to the same branch");
            }
        }

        if (reservation != null && orderType != OrderType.DINE_IN) {
            throw new IllegalStateException("reservation-backed orders must use DINE_IN orderType");
        }

        if (reservation != null && customer != null && reservation.getCustomer() != null) {
            if (!Objects.equals(reservation.getCustomer().getId(), customer.getId())) {
                throw new IllegalStateException("order customer must match reservation customer");
            }
        }

        if (status == OrderStatus.CANCELLED || status == OrderStatus.VOIDED) {
            if (fulfillmentStatus != OrderFulfillmentStatus.CANCELLED) {
                throw new IllegalStateException("cancelled or voided orders must use CANCELLED fulfillmentStatus");
            }
        }

        if (fulfillmentStatus == OrderFulfillmentStatus.CANCELLED
                && status != OrderStatus.CANCELLED
                && status != OrderStatus.VOIDED) {
            throw new IllegalStateException("CANCELLED fulfillmentStatus requires CANCELLED or VOIDED order status");
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
