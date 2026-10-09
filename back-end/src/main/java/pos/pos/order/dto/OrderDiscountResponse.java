package pos.pos.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.order.enums.OrderDiscountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDiscountResponse {

    private UUID id;
    private String name;
    private OrderDiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal amountApplied;
    private String reason;
    private UUID appliedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
