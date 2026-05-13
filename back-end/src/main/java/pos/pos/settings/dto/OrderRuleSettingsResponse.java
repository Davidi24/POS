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
public class OrderRuleSettingsResponse {

    private UUID id;
    private UUID settingsId;
    private UUID restaurantId;
    private Boolean autoFireToKitchen;
    private Boolean allowItemVoid;
    private Boolean allowDiscountWithoutManager;
    private Boolean allowBackdatedOrders;
    private Boolean requireReasonForVoid;
    private Boolean requireReasonForDiscount;
    private Boolean mergeOrdersEnabled;
    private Boolean transferOrdersEnabled;
    private Boolean reopenClosedOrdersEnabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
