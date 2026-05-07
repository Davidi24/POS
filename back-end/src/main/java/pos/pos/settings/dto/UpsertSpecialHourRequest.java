package pos.pos.settings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertSpecialHourRequest {

    @NotNull(message = "specialDate is required")
    private LocalDate specialDate;

    private LocalTime openTime;

    private LocalTime closeTime;

    @NotNull(message = "closed is required")
    private Boolean closed;

    @Size(max = 255, message = "note must be at most 255 characters")
    private String note;
}
