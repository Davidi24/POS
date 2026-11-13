package pos.pos.order.event;

import java.math.BigDecimal;
import java.util.UUID;

// An order was closed without being paid; the people chosen in Settings → Payments are told.
public record OrderClosedUnpaidEvent(
        UUID restaurantId,
        UUID branchId,
        String branchName,
        UUID orderId,
        String orderNumber,
        String tableNumber,
        BigDecimal owed,
        String currency,
        String reason,
        UUID actorId
) {
}
