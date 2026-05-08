package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.reservation.enums.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationStatusHistoryResponse {

    private UUID id;
    private ReservationStatus oldStatus;
    private ReservationStatus newStatus;
    private String reason;
    private UUID changedBy;
    private OffsetDateTime changedAt;
}
