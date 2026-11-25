package pos.pos.device.connect;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Enter a code on a new device, and its maker agreeing (user, 2026-11-25). */
public final class DeviceConnectDtos {

    private DeviceConnectDtos() {
    }

    public record AskRequest(@NotBlank @Size(max = 200) String pairingToken) {
    }

    public record ClaimRequest(@NotBlank @Size(max = 200) String claimToken) {
    }

    /**
     * A request as both sides see it: WAITING, AGREED, REFUSED, CANCELLED, DONE or EXPIRED; [secondsLeft] counted by the server,
     * so the two devices' clocks don't matter; [deviceInUse] = the till is connected on some device now, which stops working.
     */
    public record RequestView(
            UUID id,
            String status,
            UUID deviceId,
            String deviceName,
            String deviceType,
            String askedBy,
            String askingDevice,
            String askingSystem,
            UUID madeById,
            String madeBy,
            boolean deviceInUse,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            long secondsLeft
    ) {
    }

    /** What the new device gets when it asks: the request and its claim, shown only this once. */
    public record Asked(RequestView request, String claimToken) {
    }
}
