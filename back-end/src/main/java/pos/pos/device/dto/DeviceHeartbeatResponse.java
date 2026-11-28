package pos.pos.device.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

// The till lock's times come with each heartbeat (seconds), so a locked till keeps the restaurant's (2026-11-28).
public record DeviceHeartbeatResponse(UUID deviceId, boolean online, OffsetDateTime lastSeenAt, Integer tillLockSeconds, Integer tillLockImportantSeconds) {
}
