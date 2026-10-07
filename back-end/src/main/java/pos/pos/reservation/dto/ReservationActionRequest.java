package pos.pos.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
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
public class ReservationActionRequest {

    @Size(max = 1000, message = "reason must be at most 1000 characters")
    private String reason;

    // Check-in and "N of M arrived": how many of the group are here. Empty means everyone.
    @jakarta.validation.constraints.Min(value = 1, message = "arrivedGuests must be at least 1")
    @Max(value = 1000, message = "arrivedGuests must be at most 1000")
    private Integer arrivedGuests;
}
