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
public class InventoryUnitConversionResponse {

    private UUID id;
    private UUID inventoryItemId;
    private InventoryUnit fromUnit;
    private InventoryUnit toUnit;
    private BigDecimal conversionFactor;
}
