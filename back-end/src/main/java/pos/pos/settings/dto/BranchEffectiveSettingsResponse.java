package pos.pos.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchEffectiveSettingsResponse {

    private UUID restaurantId;
    private UUID branchId;
    private String restaurantCode;
    private String restaurantTimezone;
    private Boolean defaultBranch;
    private SettingsResponse settings;
    private ReceiptSettingsResponse receipt;
    private OrderRuleSettingsResponse orderRules;
}
