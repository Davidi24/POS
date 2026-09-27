package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDetailsResponse {

    private ReservationResponse reservation;
    private ReservationAuditResponse audit;
    private List<ReservationTimelineEventResponse> timeline;
    private ReservationDepositResponse deposit;
}
