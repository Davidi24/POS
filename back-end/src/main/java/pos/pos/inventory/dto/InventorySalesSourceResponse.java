package pos.pos.inventory.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class InventorySalesSourceResponse {
    UUID id;
    UUID restaurantId;
    UUID branchId;
    String branchName;
    UUID inventoryItemId;
    String inventoryItemName;
    UUID locationId;
    String locationName;
}
