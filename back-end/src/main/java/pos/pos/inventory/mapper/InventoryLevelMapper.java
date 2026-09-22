package pos.pos.inventory.mapper;

import org.springframework.stereotype.Component;
import pos.pos.inventory.dto.InventoryLevelResponse;
import pos.pos.inventory.entity.InventoryLevel;

import java.math.BigDecimal;

@Component
public class InventoryLevelMapper {

    public InventoryLevelResponse toResponse(InventoryLevel level) {
        if (level == null) {
            return null;
        }

        BigDecimal onHand = level.getOnHandQuantity() == null ? BigDecimal.ZERO : level.getOnHandQuantity();
        BigDecimal committed = level.getCommittedQuantity() == null ? BigDecimal.ZERO : level.getCommittedQuantity();
        BigDecimal reorderQuantity = level.getReorderQuantity();

        return InventoryLevelResponse.builder()
                .id(level.getId())
                .locationId(level.getLocation() == null ? null : level.getLocation().getId())
                .locationName(level.getLocation() == null ? null : level.getLocation().getName())
                .inventoryItemId(level.getInventoryItem() == null ? null : level.getInventoryItem().getId())
                .inventoryItemName(level.getInventoryItem() == null ? null : level.getInventoryItem().getName())
                .onHandQuantity(onHand)
                .committedQuantity(committed)
                .availableQuantity(onHand.subtract(committed))
                .parQuantity(level.getParQuantity())
                .reorderQuantity(reorderQuantity)
                .lastCountedAt(level.getLastCountedAt())
                .lastMovementAt(level.getLastMovementAt())
                .lowStock(reorderQuantity != null && onHand.compareTo(reorderQuantity) < 0)
                .build();
    }
}
