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
public class UpdateSettingsShiftsRequest {

    @NotNull(message = "clockInEarlyMinutes is required")
    @Min(value = 0, message = "clockInEarlyMinutes must not be negative")
    @Max(value = 720, message = "clockInEarlyMinutes must be at most 720")
    private Integer clockInEarlyMinutes;
}
