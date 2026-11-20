package pos.pos.preorder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.preorder.enums.PreOrderPaymentStatus;
import pos.pos.preorder.enums.PreOrderSource;
import pos.pos.preorder.enums.PreOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreOrderResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;
    private UUID reservationId;
    private String reservationCode;
    private OffsetDateTime reservationStart;
    private Integer partySize;
    private String contactName;
    private PreOrderStatus status;
    private PreOrderPaymentStatus paymentStatus;
    private PreOrderSource source;
    private String currency;
    private BigDecimal subtotal;
    private BigDecimal taxTotal;
    private BigDecimal serviceChargeTotal;
    private BigDecimal total;
    private BigDecimal paidAmount;
    private Integer leadMinutes;
    // When it goes to the kitchen; until then it can be changed or cancelled for a refund.
    private OffsetDateTime sendAt;
    private UUID orderId;
    private String orderNumber;
    private String notes;
    private String cancellationReason;
    private OffsetDateTime sentAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime forfeitedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<PreOrderItemResponse> items;
}
