package pos.pos.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCountCreateRequest {

    @NotNull(message = "locationId is required")
    private UUID locationId;

    @Size(max = 50, message = "countNumber must be at most 50 characters")
    private String countNumber;

    private UUID branchId;

    private OffsetDateTime scheduledAt;

    private String notes;
}
