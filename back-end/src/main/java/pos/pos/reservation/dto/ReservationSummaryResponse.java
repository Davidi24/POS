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
    // Requests nobody answered before their time.
    private Integer expiredCount;
    // Visits staff must look at: never seated before the booking ended, or still open from an earlier day.
    private Integer needsReviewCount;
    // Confirmed bookings whose guests haven't confirmed attendance yet, and those already past the deadline.
    private Integer attendanceNotConfirmedCount;
    private Integer notConfirmedDueCount;
    // Bookings from the approval size (7+) still to come, for staff to call.
    private Integer bigGroupCount;
    private Integer upcomingCount;
    // Party sizes: every booking still on (pending to completed), and those not arrived yet (pending, confirmed).
    private Integer presentGuests;
    private Integer guestsToArrive;
    // Pending to seated bookings with no table assigned.
    private Integer unassignedCount;
    // Pending or confirmed bookings starting between 15 minutes ago and 2 hours from now.
    private Integer arrivingSoonCount;
    private Integer arrivingSoonGuests;
}
