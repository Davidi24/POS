package pos.pos.inventory.mapper;

import org.springframework.stereotype.Component;
import pos.pos.inventory.dto.InventoryUnitConversionResponse;
import pos.pos.inventory.entity.InventoryUnitConversion;

@Component
public class InventoryUnitConversionMapper {

    public InventoryUnitConversionResponse toResponse(InventoryUnitConversion conversion) {
        if (conversion == null) {
            return null;
        }

        return InventoryUnitConversionResponse.builder()
                .id(conversion.getId())
                .inventoryItemId(conversion.getInventoryItem() == null ? null : conversion.getInventoryItem().getId())
                .fromUnit(conversion.getFromUnit())
                .toUnit(conversion.getToUnit())
                .conversionFactor(conversion.getConversionFactor())
                .build();
    }
}
