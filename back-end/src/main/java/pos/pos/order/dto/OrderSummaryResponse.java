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
public class OrderSummaryResponse {

    private UUID branchId;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private Integer totalOrders;
    private Integer openCount;
    private Integer closedCount;
    private Integer cancelledCount;
    private Integer voidedCount;
    private Integer paidCount;
    private Integer unpaidCount;
    private BigDecimal totalRevenue;
    private BigDecimal averageTicket;
    private BigDecimal openTicketTotal;
}
