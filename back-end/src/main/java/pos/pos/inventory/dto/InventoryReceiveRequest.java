package pos.pos.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
public class InventoryReceiveRequest {

    @NotNull(message = "locationId is required")
    private UUID locationId;

    @NotNull(message = "inventoryItemId is required")
    private UUID inventoryItemId;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than zero")
    private BigDecimal quantity;

    @PositiveOrZero(message = "unitCostOverride must not be negative")
    private BigDecimal unitCostOverride;

    private OffsetDateTime occurredAt;

    @Size(max = 50, message = "referenceType must be at most 50 characters")
    private String referenceType;

    private UUID referenceId;

    private String reason;
}
