package pos.pos.reservation.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationValidationRequest {

    @NotNull(message = "reservationStart is required")
    private OffsetDateTime reservationStart;

    @NotNull(message = "reservationEnd is required")
    private OffsetDateTime reservationEnd;

    @NotNull(message = "partySize is required")
    @Min(value = 1, message = "partySize must be greater than 0")
    @Max(value = 1000, message = "partySize must be at most 1000")
    private Integer partySize;

    @Size(max = 50, message = "tableIds can have at most 50 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") UUID> tableIds;
    private UUID primaryTableId;
}
