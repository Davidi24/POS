package pos.pos.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptSettingsResponse {

    private UUID id;
    private UUID settingsId;
    private UUID restaurantId;
    private Boolean autoPrintCustomerReceipt;
    private Boolean autoPrintKitchenTicket;
    private Integer receiptCopies;
    private Boolean showLogo;
    private Boolean showTaxBreakdown;
    private Boolean showServerName;
    private Boolean showTableName;
    private Boolean showOrderNumber;
    private Boolean showQrCode;
    private Boolean printVoidedItems;
    private String footerNote;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
