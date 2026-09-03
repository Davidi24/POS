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
        BigDecimal effectiveReorderPoint = level.getEffectiveReorderPoint();

        // effectiveReorderPoint is null exactly when both calculatedReorderPoint and
        // manualReorderPoint are null -- that's the only case that falls back to the item's
        // own reorderPoint, matching the same three-tier priority the low-stock query uses.
        BigDecimal lowStockThreshold = effectiveReorderPoint != null
                ? effectiveReorderPoint
                : (level.getInventoryItem() == null ? null : level.getInventoryItem().getReorderPoint());

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
                .calculatedReorderPoint(level.getCalculatedReorderPoint())
                .manualReorderPoint(level.getManualReorderPoint())
                .effectiveReorderPoint(effectiveReorderPoint)
                .lastCountedAt(level.getLastCountedAt())
                .lastMovementAt(level.getLastMovementAt())
                .lowStock(lowStockThreshold != null && onHand.compareTo(lowStockThreshold) < 0)
                .build();
    }
}
