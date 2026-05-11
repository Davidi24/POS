package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationCapacityResponse {

    private UUID branchId;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private Integer totalRootTables;
    private Integer availableRootTables;
    private Integer totalSeats;
    private Integer availableSeats;
    private Integer maxSingleTableCapacity;
    private Integer maxAvailableTableCapacity;
    private Integer requestedPartySize;
    private Boolean canAccommodateRequestedPartySize;
}
