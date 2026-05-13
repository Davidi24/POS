package pos.pos.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderExportResponse {

    private UUID restaurantId;
    private UUID branchId;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private OffsetDateTime exportedAt;
    private Integer orderCount;
    private List<OrderResponse> orders;
}
