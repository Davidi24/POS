package pos.pos.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pos.pos.payment.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One payment towards an order's bill. {@code amount} pays the bill; the tip comes on top. For cash,
 * {@code tenderedAmount} is what the guest handed over and the change is worked out.
 */
public record TakePaymentRequest(
        @NotNull(message = "method is required")
        PaymentMethod method,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @DecimalMax(value = "999999999.99", message = "amount is too large")
        @Digits(integer = 9, fraction = 2, message = "amount must have at most 2 decimals")
        BigDecimal amount,

        @DecimalMin(value = "0.00", message = "tipAmount must not be negative")
        @DecimalMax(value = "999999999.99", message = "tipAmount is too large")
        @Digits(integer = 9, fraction = 2, message = "tipAmount must have at most 2 decimals")
        BigDecimal tipAmount,

        @DecimalMin(value = "0.00", message = "tenderedAmount must not be negative")
        @DecimalMax(value = "999999999.99", message = "tenderedAmount is too large")
        @Digits(integer = 9, fraction = 2, message = "tenderedAmount must have at most 2 decimals")
        BigDecimal tenderedAmount,

        @Size(max = 40, message = "cardBrand must be at most 40 characters")
        String cardBrand,

        @Pattern(regexp = "\\d{4}", message = "cardLast4 must be 4 digits")
        String cardLast4,

        @Size(max = 100, message = "externalReference must be at most 100 characters")
        String externalReference,

        @Size(max = 100, message = "gatewayName must be at most 100 characters")
        String gatewayName,

        @Size(max = 1000, message = "notes must be at most 1000 characters")
        String notes,

        // Close the order once nothing is left to pay; null follows the restaurant setting.
        Boolean closeOrderWhenPaid,

        // Paying by dishes: what this guest pays for. The amount must be what those dishes cost of the bill.
        @Valid
        @Size(max = 500, message = "lines must be at most 500")
        List<PaidLine> lines
) {
    public record PaidLine(
            @NotNull(message = "lineId is required")
            UUID lineId,

            @Min(value = 1, message = "quantity must be at least 1")
            @Max(value = 100000, message = "quantity is too large")
            int quantity
    ) {
    }
}
