package pos.pos.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidPaymentRequest(
        @NotBlank(message = "reason is required")
        @Size(min = 3, max = 500, message = "reason must be between 3 and 500 characters")
        String reason
) {
}
