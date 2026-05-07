package pos.pos.settings.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.settings.enums.ServiceChargeType;
import pos.pos.settings.enums.WeekStartDay;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsTransferCoreRequest {

    private String defaultBranchCode;

    @NotBlank(message = "defaultLanguage is required")
    private String defaultLanguage;

    @NotBlank(message = "dateFormat is required")
    private String dateFormat;

    @NotBlank(message = "timeFormat is required")
    private String timeFormat;

    @NotNull(message = "weekStartDay is required")
    private WeekStartDay weekStartDay;

    private String orderSequencePrefix;

    private String invoiceSequencePrefix;

    @NotNull(message = "reservationSlotMinutes is required")
    @Min(value = 1, message = "reservationSlotMinutes must be greater than 0")
    private Integer reservationSlotMinutes;

    @NotNull(message = "defaultTableTurnTimeMinutes is required")
    @Min(value = 1, message = "defaultTableTurnTimeMinutes must be greater than 0")
    private Integer defaultTableTurnTimeMinutes;

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

    @NotNull(message = "allowOpenTickets is required")
    private Boolean allowOpenTickets;

    @NotNull(message = "requireCustomerForInvoice is required")
    private Boolean requireCustomerForInvoice;

    @NotNull(message = "enableQrOrdering is required")
    private Boolean enableQrOrdering;

    @NotNull(message = "enableTakeaway is required")
    private Boolean enableTakeaway;

    @NotNull(message = "enableDelivery is required")
    private Boolean enableDelivery;
}
