package pos.pos.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertBusinessHourRequest {

    @NotNull(message = "dayOfWeek is required")
    @Min(value = 1, message = "dayOfWeek must be between 1 and 7")
    @Max(value = 7, message = "dayOfWeek must be between 1 and 7")
    private Integer dayOfWeek;

    private LocalTime openTime;

    private LocalTime closeTime;

    @NotNull(message = "closed is required")
    private Boolean closed;

    @NotNull(message = "overnight is required")
    private Boolean overnight;
}
