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
public class ReservationTableAssignmentResponse {

    private UUID assignmentId;
    private UUID tableId;
    private String tableNumber;
    private String tableName;
    private String floor;
    private Integer capacity;
    private Boolean primary;
    private OffsetDateTime assignedAt;
    private UUID assignedBy;
}
