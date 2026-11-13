package pos.pos.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class UpdateSettingsKitchenStatusRequest {

    @NotNull(message = "kitchenSlowAfterMinutes is required")
    @Min(value = 1, message = "kitchenSlowAfterMinutes must be at least 1")
    @Max(value = 240, message = "kitchenSlowAfterMinutes must be at most 240")
    private Integer kitchenSlowAfterMinutes;

    @NotNull(message = "kitchenReadyWaitingMinutes is required")
    @Min(value = 1, message = "kitchenReadyWaitingMinutes must be at least 1")
    @Max(value = 120, message = "kitchenReadyWaitingMinutes must be at most 120")
    private Integer kitchenReadyWaitingMinutes;
}
