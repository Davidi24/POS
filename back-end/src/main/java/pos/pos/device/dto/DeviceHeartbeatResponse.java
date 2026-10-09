package pos.pos.device.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeviceHeartbeatResponse(UUID deviceId, boolean online, OffsetDateTime lastSeenAt) {
}
