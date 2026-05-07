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
public class ReservationSummaryResponse {

    private UUID branchId;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private Integer totalReservations;
    private Integer totalGuests;
    private Integer pendingCount;
    private Integer confirmedCount;
    private Integer checkedInCount;
    private Integer seatedCount;
    private Integer completedCount;
    private Integer cancelledCount;
    private Integer noShowCount;
    private Integer upcomingCount;
}
