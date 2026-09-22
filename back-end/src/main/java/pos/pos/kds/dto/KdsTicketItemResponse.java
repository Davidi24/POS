package pos.pos.kds.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.kds.enums.KdsPriority;
import pos.pos.kds.enums.KdsTicketStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KdsTicketItemResponse {

    private UUID id;
    private UUID orderLineItemId;
    private UUID menuItemId;
    private String itemNameSnapshot;
    private Integer quantity;
    private KdsTicketStatus status;
    private KdsPriority priority;
    private String seatLabel;
    private String notes;
    private OffsetDateTime firedAt;
    private OffsetDateTime readyAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
