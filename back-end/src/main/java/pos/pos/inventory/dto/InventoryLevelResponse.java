package pos.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLevelResponse {

    private UUID id;
    private UUID locationId;
    private String locationName;
    private UUID inventoryItemId;
    private String inventoryItemName;
    private BigDecimal onHandQuantity;
    private BigDecimal committedQuantity;
    private BigDecimal availableQuantity;
    private BigDecimal parQuantity;
    private BigDecimal reorderQuantity;
    private OffsetDateTime lastCountedAt;
    private OffsetDateTime lastMovementAt;
    private boolean lowStock;
}
