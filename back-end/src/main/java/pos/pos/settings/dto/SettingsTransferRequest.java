package pos.pos.settings.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsTransferRequest {

    @Valid
    @NotNull(message = "core is required")
    private SettingsTransferCoreRequest core;

    @Valid
    @NotNull(message = "receipt is required")
    private UpdateReceiptSettingsRequest receipt;

    @Valid
    @NotNull(message = "orderRules is required")
    private UpdateOrderRuleSettingsRequest orderRules;

    @Valid
    @Size(max = 500, message = "reservationRules can have at most 500 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") ReservationRuleTransferRequest> reservationRules;

    @Valid
    @Size(max = 5000, message = "businessHours can have at most 5000 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") BranchBusinessHoursTransferRequest> businessHours;

    @Valid
    @Size(max = 5000, message = "specialHours can have at most 5000 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") BranchSpecialHoursTransferRequest> specialHours;
}
