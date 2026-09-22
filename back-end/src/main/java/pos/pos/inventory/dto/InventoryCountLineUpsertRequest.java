package pos.pos.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCountLineUpsertRequest {

    // Optional safety check only -- the item this line belongs to is really identified by the
    // {itemId} path variable. If both are sent, the service verifies they agree.
    private UUID inventoryItemId;

    @NotNull(message = "countedQuantity is required")
    @PositiveOrZero(message = "countedQuantity must not be negative")
    private BigDecimal countedQuantity;

    @PositiveOrZero(message = "expectedQuantity must not be negative")
    private BigDecimal expectedQuantity;

    private String notes;
}
