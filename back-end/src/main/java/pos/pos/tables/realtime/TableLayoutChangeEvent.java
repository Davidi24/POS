package pos.pos.tables.realtime;

import java.util.UUID;

public record TableLayoutChangeEvent(
        UUID restaurantId,
        UUID branchId
) {
}
