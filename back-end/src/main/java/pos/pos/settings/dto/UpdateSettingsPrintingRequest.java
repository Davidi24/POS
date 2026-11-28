package pos.pos.settings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** When things print by themselves (Admin Hub → Settings → Devices & printing, 2026-11-27). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsPrintingRequest {

    @NotNull(message = "printKitchenOnSend is required")
    private Boolean printKitchenOnSend;

    @NotNull(message = "printBillOnRequest is required")
    private Boolean printBillOnRequest;

    @NotNull(message = "printReceiptAfterPayment is required")
    @Pattern(regexp = "ALWAYS|ASK|NEVER", message = "printReceiptAfterPayment must be ALWAYS, ASK or NEVER")
    private String printReceiptAfterPayment;
}
