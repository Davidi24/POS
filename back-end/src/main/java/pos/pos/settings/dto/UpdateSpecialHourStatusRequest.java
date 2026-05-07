package pos.pos.settings.dto;

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
public class UpdateSpecialHourStatusRequest {

    @NotNull(message = "closed is required")
    private Boolean closed;

    private LocalTime openTime;

    private LocalTime closeTime;
}
