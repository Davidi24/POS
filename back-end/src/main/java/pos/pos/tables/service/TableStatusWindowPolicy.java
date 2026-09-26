package pos.pos.tables.service;

import java.time.Duration;
import java.time.OffsetDateTime;

// A table's live status (occupied, dirty, out of service) says nothing about a reservation hours away,
// so it only counts when the reservation window starts soon or has already started.
public final class TableStatusWindowPolicy {

    public static final Duration CURRENT_STATUS_HORIZON = Duration.ofHours(2);

    private TableStatusWindowPolicy() {
    }

    public static boolean currentStatusApplies(OffsetDateTime windowStart) {
        return windowStart == null || windowStart.isBefore(OffsetDateTime.now().plus(CURRENT_STATUS_HORIZON));
    }
}
