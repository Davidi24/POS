package pos.pos.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.order.enums.OrderFulfillmentStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderSource;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;
    private UUID tableId;
    private String tableNumber;
    private String tableName;
    private UUID reservationId;
    private String reservationCode;
    private UUID customerId;
    private String customerCode;
    private String customerName;
    private String orderNumber;
    private String currency;
    private OrderType orderType;
    private OrderSource source;
    private OrderStatus status;
    private OrderFulfillmentStatus fulfillmentStatus;
    private OrderPaymentStatus paymentStatus;
    private Integer guestCount;
    private String notes;
    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal taxTotal;
    private BigDecimal serviceChargeTotal;
    private BigDecimal total;
    private OffsetDateTime openedAt;
    private OffsetDateTime closedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private List<OrderLineItemResponse> lineItems;
    private List<OrderDiscountResponse> discounts;
    private List<OrderEventResponse> events;
}
