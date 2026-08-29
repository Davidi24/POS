package pos.pos.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class InventoryTransferRequest {

    @NotNull(message = "fromLocationId is required")
    private UUID fromLocationId;

    @NotNull(message = "toLocationId is required")
    private UUID toLocationId;

    @NotNull(message = "inventoryItemId is required")
    private UUID inventoryItemId;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than zero")
    private BigDecimal quantity;

    private String reason;

    private OffsetDateTime occurredAt;
}
