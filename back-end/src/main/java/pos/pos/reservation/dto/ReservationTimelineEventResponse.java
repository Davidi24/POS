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
public class ReservationTimelineEventResponse {

    private UUID id;
    private String type;
    private OffsetDateTime occurredAt;
    private UUID actorId;
    private String message;
    private ReservationStatus oldStatus;
    private ReservationStatus newStatus;
    private UUID relatedTableId;
    private UUID relatedNoteId;
}
