package pos.pos.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustmentRequest {

    @NotNull(message = "locationId is required")
    private UUID locationId;

    @NotNull(message = "inventoryItemId is required")
    private UUID inventoryItemId;

    @NotNull(message = "quantityDelta is required")
    private BigDecimal quantityDelta;

    @NotBlank(message = "reason is required")
    private String reason;

    private OffsetDateTime occurredAt;
}
