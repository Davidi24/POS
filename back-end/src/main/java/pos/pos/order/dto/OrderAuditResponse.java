package pos.pos.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.order.enums.OrderFulfillmentStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAuditResponse {

    private UUID orderId;
    private OrderStatus status;
    private OrderFulfillmentStatus fulfillmentStatus;
    private OrderPaymentStatus paymentStatus;
    private OffsetDateTime createdAt;
    private UUID createdBy;
    private OffsetDateTime updatedAt;
    private UUID updatedBy;
    private List<OrderLineItemResponse> lineItems;
    private List<OrderDiscountResponse> discounts;
    private List<OrderEventResponse> events;
}
