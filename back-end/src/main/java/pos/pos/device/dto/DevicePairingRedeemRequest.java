package pos.pos.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DevicePairingRedeemRequest(
        @NotBlank(message = "pairingToken is required")
        @Size(max = 128, message = "pairingToken is too long")
        String pairingToken
) {
}
