package pos.pos.inventory.dto;

import jakarta.validation.constraints.Digits;
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
    @Digits(integer = 9, fraction = 3, message = "quantity must have at most 9 digits and 3 decimals")
    private BigDecimal quantity;

    @PositiveOrZero(message = "unitCostOverride must not be negative")
    @Digits(integer = 12, fraction = 4, message = "unitCostOverride must have at most 12 digits and 4 decimals")
    private BigDecimal unitCostOverride;

    private OffsetDateTime occurredAt;

    @Size(max = 50, message = "referenceType must be at most 50 characters")
    private String referenceType;

    private UUID referenceId;

    private String reason;
}
