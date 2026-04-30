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
public class UpdateOrderRuleDiscountPolicyRequest {

    @NotNull(message = "allowDiscountWithoutManager is required")
    private Boolean allowDiscountWithoutManager;

    @NotNull(message = "requireReasonForDiscount is required")
    private Boolean requireReasonForDiscount;
}
