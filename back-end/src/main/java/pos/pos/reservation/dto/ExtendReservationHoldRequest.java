package pos.pos.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// "We'll be 20 minutes late": keep the table that much longer. The booking still ends on time.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtendReservationHoldRequest {

    @NotNull(message = "minutes is required")
    @Min(value = 1, message = "minutes must be at least 1")
    @Max(value = 240, message = "minutes must be at most 240")
    private Integer minutes;

    @Size(max = 1000, message = "reason must be at most 1000 characters")
    private String reason;
}
