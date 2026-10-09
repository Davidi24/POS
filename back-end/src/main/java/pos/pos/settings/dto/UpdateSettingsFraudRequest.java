package pos.pos.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsFraudRequest {

    @NotNull(message = "fraudDiscountPercent is required")
    @Min(value = 1, message = "fraudDiscountPercent must be at least 1")
    @Max(value = 100, message = "fraudDiscountPercent must be at most 100")
    private Integer fraudDiscountPercent;

    @NotNull(message = "fraudRefundAmount is required")
    @DecimalMin(value = "0.00", message = "fraudRefundAmount must not be negative")
    @DecimalMax(value = "1000000.00", message = "fraudRefundAmount must be at most 1000000")
    @Digits(integer = 7, fraction = 2, message = "fraudRefundAmount must have at most 2 decimals")
    private BigDecimal fraudRefundAmount;

    @NotNull(message = "fraudVoidsPerDay is required")
    @Min(value = 1, message = "fraudVoidsPerDay must be at least 1")
    @Max(value = 1000, message = "fraudVoidsPerDay must be at most 1000")
    private Integer fraudVoidsPerDay;

    @NotNull(message = "fraudTipPercent is required")
    @Min(value = 1, message = "fraudTipPercent must be at least 1")
    @Max(value = 500, message = "fraudTipPercent must be at most 500")
    private Integer fraudTipPercent;

    @NotNull(message = "fraudCashRefundsPerDay is required")
    @Min(value = 1, message = "fraudCashRefundsPerDay must be at least 1")
    @Max(value = 1000, message = "fraudCashRefundsPerDay must be at most 1000")
    private Integer fraudCashRefundsPerDay;

    // Rule codes to switch off; unknown codes are refused.
    @NotNull(message = "fraudDisabledRules is required")
    @Size(max = 30, message = "fraudDisabledRules has too many values")
    private Set<@NotNull @Pattern(regexp = "[A-Z_]{3,40}", message = "fraudDisabledRules has an invalid rule code") String> fraudDisabledRules;
}
