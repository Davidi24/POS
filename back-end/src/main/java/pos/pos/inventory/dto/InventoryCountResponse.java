package pos.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryCountStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCountResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;
    private UUID locationId;
    private String locationName;
    private String countNumber;
    private InventoryCountStatus status;
    private OffsetDateTime scheduledAt;
    private OffsetDateTime completedAt;
    private String approvedByUserName;
    private OffsetDateTime approvedAt;
    private BigDecimal varianceValue;
    private String notes;
    private String createdByUserName;
    private String updatedByUserName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<InventoryCountLineResponse> lines;
}
