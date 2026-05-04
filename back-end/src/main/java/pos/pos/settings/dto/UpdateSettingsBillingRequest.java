package pos.pos.settings.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.settings.enums.ServiceChargeType;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsBillingRequest {

    @NotNull(message = "serviceChargeEnabled is required")
    private Boolean serviceChargeEnabled;

    private ServiceChargeType serviceChargeType;

    @Digits(integer = 10, fraction = 2, message = "serviceChargeValue must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.0", inclusive = true, message = "serviceChargeValue must not be negative")
    private BigDecimal serviceChargeValue;

    @NotNull(message = "cashRoundingEnabled is required")
    private Boolean cashRoundingEnabled;

    @Digits(integer = 10, fraction = 2, message = "cashRoundingIncrement must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.0", inclusive = false, message = "cashRoundingIncrement must be greater than 0")
    private BigDecimal cashRoundingIncrement;

    @NotNull(message = "allowSplitBills is required")
    private Boolean allowSplitBills;

    @NotNull(message = "requireCustomerForInvoice is required")
    private Boolean requireCustomerForInvoice;
}
