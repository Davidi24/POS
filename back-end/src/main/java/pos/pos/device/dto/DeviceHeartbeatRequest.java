package pos.pos.device.dto;

import jakarta.validation.constraints.Size;

/** What the app runs on, sent with each heartbeat (all optional; user, 2026-11-25: the device page shows it). */
public record DeviceHeartbeatRequest(
        @Size(max = 50, message = "platform is too long") String platform,
        @Size(max = 50, message = "osVersion is too long") String osVersion,
        @Size(max = 50, message = "appVersion is too long") String appVersion
) {
}
