package pos.pos.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationAvailabilitySearchRequest {

    @NotNull(message = "reservationStart is required")
    private OffsetDateTime reservationStart;

    @NotNull(message = "reservationEnd is required")
    private OffsetDateTime reservationEnd;

    @NotNull(message = "partySize is required")
    @Min(value = 1, message = "partySize must be greater than 0")
    private Integer partySize;

    @Min(value = 1, message = "maxOptions must be greater than 0")
    private Integer maxOptions;
}
