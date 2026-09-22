package pos.pos.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.order.enums.OrderLineItemStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineItemResponse {

    private UUID id;
    private UUID menuItemId;
    private UUID variantId;
    private String itemNameSnapshot;
    private String variantNameSnapshot;
    private String skuSnapshot;
    private Integer quantity;
    private BigDecimal unitPriceSnapshot;
    private BigDecimal priceDeltaTotal;
    private BigDecimal discountTotal;
    private BigDecimal taxTotal;
    private BigDecimal lineTotal;
    private OrderLineItemStatus status;
    private String notes;
    private List<OrderItemOptionResponse> options;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
