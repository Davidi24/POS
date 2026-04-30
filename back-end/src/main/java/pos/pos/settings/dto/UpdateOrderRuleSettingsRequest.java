package pos.pos.settings.dto;

import jakarta.validation.constraints.NotNull;
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
public class UpdateOrderRuleSettingsRequest {

    @NotNull(message = "autoFireToKitchen is required")
    private Boolean autoFireToKitchen;

    @NotNull(message = "allowItemVoid is required")
    private Boolean allowItemVoid;

    @NotNull(message = "allowDiscountWithoutManager is required")
    private Boolean allowDiscountWithoutManager;

    @NotNull(message = "allowBackdatedOrders is required")
    private Boolean allowBackdatedOrders;

    @NotNull(message = "requireReasonForVoid is required")
    private Boolean requireReasonForVoid;

    @NotNull(message = "requireReasonForDiscount is required")
    private Boolean requireReasonForDiscount;

    @NotNull(message = "mergeOrdersEnabled is required")
    private Boolean mergeOrdersEnabled;

    @NotNull(message = "transferOrdersEnabled is required")
    private Boolean transferOrdersEnabled;

    @NotNull(message = "reopenClosedOrdersEnabled is required")
    private Boolean reopenClosedOrdersEnabled;
}
