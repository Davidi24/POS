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
public class UpdateSettingsPreOrdersRequest {

    @NotNull(message = "preOrdersEnabled is required")
    private Boolean preOrdersEnabled;

    @NotNull(message = "preOrderLeadMinutes is required")
    @Min(value = 0, message = "preOrderLeadMinutes must not be negative")
    @Max(value = 240, message = "preOrderLeadMinutes must be at most 240")
    private Integer preOrderLeadMinutes;
}
