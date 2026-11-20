package pos.pos.device.dto;

import java.util.UUID;

/** The device secret is returned once during pairing and is never readable from the admin API. */
public record DevicePairingRedeemResponse(
        UUID deviceId,
        UUID restaurantId,
        UUID branchId,
        String deviceName,
        String deviceType,
        String deviceSecret
) {
}
