package pos.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryLocationType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLocationResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;
    private String code;
    private String name;
    private InventoryLocationType locationType;
    private String iconCode;
    // The restaurant's own picture, shown instead of the icon when set.
    private String imageUrl;
    // The restaurant's own kind (locationType CUSTOM).
    private UUID kindId;
    private String kindName;
    private String notes;
    private boolean active;
    private String createdByUserName;
    private String updatedByUserName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
