package pos.pos.settings.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReceiptSettingsRequest {

    @NotNull(message = "autoPrintCustomerReceipt is required")
    private Boolean autoPrintCustomerReceipt;

    @NotNull(message = "autoPrintKitchenTicket is required")
    private Boolean autoPrintKitchenTicket;

    @NotNull(message = "receiptCopies is required")
    @Min(value = 1, message = "receiptCopies must be greater than 0")
    private Integer receiptCopies;

    @NotNull(message = "showLogo is required")
    private Boolean showLogo;

    @NotNull(message = "showTaxBreakdown is required")
    private Boolean showTaxBreakdown;

    @NotNull(message = "showServerName is required")
    private Boolean showServerName;

    @NotNull(message = "showTableName is required")
    private Boolean showTableName;

    @NotNull(message = "showOrderNumber is required")
    private Boolean showOrderNumber;

    @NotNull(message = "showQrCode is required")
    private Boolean showQrCode;

    @NotNull(message = "printVoidedItems is required")
    private Boolean printVoidedItems;

    @Size(max = 1000, message = "footerNote must be at most 1000 characters")
    private String footerNote;
}
