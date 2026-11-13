package pos.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryMovementType;
import pos.pos.inventory.enums.InventoryUnit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovementResponse {

    private UUID id;
    private UUID locationId;
    private String locationName;
    private UUID inventoryItemId;
    private String inventoryItemName;
    private UUID orderLineItemId;
    private InventoryMovementType movementType;
    private BigDecimal quantityDelta;
    private InventoryUnit unit;
    private BigDecimal unitCostSnapshot;
    private BigDecimal totalCostDelta;
    private String reason;
    private String referenceType;
    private UUID referenceId;
    private OffsetDateTime occurredAt;
    private String createdByUserName;
    private OffsetDateTime createdAt;
}
