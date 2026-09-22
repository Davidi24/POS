package pos.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryUnit;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCountLineResponse {

    private UUID id;
    private UUID inventoryItemId;
    private String itemNameSnapshot;
    private BigDecimal expectedQuantity;
    private BigDecimal countedQuantity;
    private BigDecimal varianceQuantity;
    private InventoryUnit unit;
    private BigDecimal unitCostSnapshot;
    private BigDecimal varianceValue;
    private String notes;
}
