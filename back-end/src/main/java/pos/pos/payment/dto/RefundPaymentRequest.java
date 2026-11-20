package pos.pos.payment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundPaymentRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @DecimalMax(value = "999999999.99", message = "amount is too large")
        @Digits(integer = 9, fraction = 2, message = "amount must have at most 2 decimals")
        BigDecimal amount,

        @NotBlank(message = "reason is required")
        @Size(min = 3, max = 500, message = "reason must be between 3 and 500 characters")
        String reason
) {
}
