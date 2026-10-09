package pos.pos.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.order.enums.OrderSource;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderRequest {

    private UUID branchId;
    private UUID tableId;
    private UUID reservationId;
    private UUID customerId;

    @Size(max = 50, message = "orderNumber must be at most 50 characters")
    private String orderNumber;

    @Size(max = 3, message = "currency must be at most 3 characters")
    private String currency;

    private OrderType orderType;
    private OrderSource source;
    private OrderStatus status;

    @Min(value = 1, message = "guestCount must be greater than 0")
    @Max(value = 500, message = "guestCount must be at most 500")
    private Integer guestCount;

    @Size(max = 2000, message = "notes must be at most 2000 characters")
    private String notes;
    private OffsetDateTime openedAt;

    @Valid
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") CreateOrderLineItemRequest> items;

    @Valid
    @Size(max = 20, message = "discounts can have at most 20 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") CreateOrderDiscountRequest> discounts;
}
