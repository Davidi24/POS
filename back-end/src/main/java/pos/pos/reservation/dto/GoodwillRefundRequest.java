package pos.pos.reservation.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodwillRefundRequest {
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be more than zero")
    @Digits(integer = 12, fraction = 2, message = "amount must have at most 12 digits and 2 decimals")
    private BigDecimal amount;

    @NotBlank(message = "Give a reason for the goodwill refund")
    @Size(max = 1000)
    private String reason;
}
