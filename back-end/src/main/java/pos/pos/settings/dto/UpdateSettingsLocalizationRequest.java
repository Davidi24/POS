package pos.pos.settings.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.settings.enums.WeekStartDay;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsLocalizationRequest {

    @NotBlank(message = "defaultLanguage is required")
    @Size(max = 20, message = "defaultLanguage must be at most 20 characters")
    private String defaultLanguage;

    @NotBlank(message = "dateFormat is required")
    @Size(max = 30, message = "dateFormat must be at most 30 characters")
    private String dateFormat;

    @NotBlank(message = "timeFormat is required")
    @Size(max = 30, message = "timeFormat must be at most 30 characters")
    private String timeFormat;

    @NotNull(message = "weekStartDay is required")
    private WeekStartDay weekStartDay;

    @NotNull(message = "reservationSlotMinutes is required")
    @Min(value = 1, message = "reservationSlotMinutes must be greater than 0")
    private Integer reservationSlotMinutes;

    @NotNull(message = "defaultTableTurnTimeMinutes is required")
    @Min(value = 1, message = "defaultTableTurnTimeMinutes must be greater than 0")
    private Integer defaultTableTurnTimeMinutes;
}
