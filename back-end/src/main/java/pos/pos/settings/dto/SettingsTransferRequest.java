package pos.pos.settings.dto;

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
    private List<ReservationRuleTransferRequest> reservationRules;

    @Valid
    private List<BranchBusinessHoursTransferRequest> businessHours;

    @Valid
    private List<BranchSpecialHoursTransferRequest> specialHours;
}
