package pos.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryUnit;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUnitConversionTestResponse {

    private BigDecimal originalQuantity;
    private InventoryUnit fromUnit;
    private BigDecimal convertedQuantity;
    private InventoryUnit toUnit;
}
