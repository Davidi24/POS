package pos.pos.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.settings.enums.DepositType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRuleTransferRequest {

    @Size(max = 100, message = "branchCode must be at most 100 characters")
    private String branchCode;

    @NotBlank(message = "ruleName is required")
    @Size(max = 120, message = "ruleName must be at most 120 characters")
    private String ruleName;

    @NotNull(message = "priority is required")
    @Min(value = 0, message = "priority must not be negative")
    @Max(value = 100000, message = "priority must be at most 100000")
    private Integer priority;

    @NotNull(message = "active is required")
    private Boolean active;

    private OffsetDateTime effectiveFrom;

    private OffsetDateTime effectiveTo;

    @NotNull(message = "advanceBookingDays is required")
    @Min(value = 0, message = "advanceBookingDays must not be negative")
    @Max(value = 3650, message = "advanceBookingDays must be at most 3650")
    private Integer advanceBookingDays;

    @NotNull(message = "minPartySize is required")
    @Min(value = 1, message = "minPartySize must be greater than 0")
    @Max(value = 1000, message = "minPartySize must be at most 1000")
    private Integer minPartySize;

    @NotNull(message = "maxPartySize is required")
    @Min(value = 1, message = "maxPartySize must be greater than 0")
    @Max(value = 1000, message = "maxPartySize must be at most 1000")
    private Integer maxPartySize;

    @NotNull(message = "defaultDurationMinutes is required")
    @Min(value = 1, message = "defaultDurationMinutes must be greater than 0")
    @Max(value = 1440, message = "defaultDurationMinutes must be at most 1440")
    private Integer defaultDurationMinutes;

    @NotNull(message = "bufferMinutes is required")
    @Min(value = 0, message = "bufferMinutes must not be negative")
    @Max(value = 1440, message = "bufferMinutes must be at most 1440")
    private Integer bufferMinutes;

    @NotNull(message = "allowOnlineReservations is required")
    private Boolean allowOnlineReservations;

    @NotNull(message = "requireDeposit is required")
    private Boolean requireDeposit;

    private DepositType depositType;

    @Digits(integer = 10, fraction = 2, message = "depositValue must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.0", inclusive = true, message = "depositValue must not be negative")
    private BigDecimal depositValue;

    @NotNull(message = "autoConfirmReservations is required")
    private Boolean autoConfirmReservations;

    @NotNull(message = "cancellationWindowHours is required")
    @Min(value = 0, message = "cancellationWindowHours must not be negative")
    @Max(value = 8760, message = "cancellationWindowHours must be at most 8760")
    private Integer cancellationWindowHours;
}
