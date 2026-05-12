package pos.pos.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemOptionResponse {

    private UUID id;
    private UUID optionItemId;
    private String optionNameSnapshot;
    private BigDecimal priceDeltaSnapshot;
    private Integer quantity;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
