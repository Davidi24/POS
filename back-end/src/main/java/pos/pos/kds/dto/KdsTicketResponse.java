package pos.pos.kds.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.kds.enums.KdsPriority;
import pos.pos.kds.enums.KdsTicketStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KdsTicketResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;
    private UUID stationId;
    private String stationCode;
    private String stationName;
    private UUID deviceId;
    private String ticketNumber;
    private UUID orderId;
    private String orderNumber;
    private UUID tableId;
    private String tableNumber;
    private String tableName;
    private UUID customerId;
    private String customerName;
    private Integer guestCount;
    private KdsTicketStatus status;
    private KdsPriority priority;
    private String courseName;
    private String notes;
    private String voidReason;
    private OffsetDateTime firedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime readyAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime dueAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private List<KdsTicketItemResponse> items;
}
