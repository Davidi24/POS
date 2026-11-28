package pos.pos.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The till lock and the PIN pause (Admin Hub → Settings → Devices & printing; decided 2026-11-28). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsTillLockRequest {

    @NotNull
    @Min(value = 10, message = "A till locks after 10 seconds at the least")
    @Max(value = 1800, message = "A till locks after 30 minutes at the most")
    private Integer tillLockSeconds;

    @NotNull
    @Min(value = 30, message = "During important work a till locks after 30 seconds at the least")
    @Max(value = 3600, message = "During important work a till locks after an hour at the most")
    private Integer tillLockImportantSeconds;

    @NotNull
    @Min(value = 3, message = "At least 3 wrong PINs before a pause")
    @Max(value = 50, message = "At most 50 wrong PINs before a pause")
    private Integer pinPauseAfter;

    @NotNull
    @Min(value = 1, message = "A pause lasts at least 1 minute")
    @Max(value = 120, message = "A pause lasts at most 120 minutes")
    private Integer pinPauseMinutes;
}
