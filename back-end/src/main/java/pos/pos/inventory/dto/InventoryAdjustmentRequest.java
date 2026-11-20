package pos.pos.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
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
    @Digits(integer = 9, fraction = 3, message = "quantityDelta must have at most 9 digits and 3 decimals")
    private BigDecimal quantityDelta;

    @NotBlank(message = "reason is required")
    @Size(max = 2000, message = "reason must be at most 2000 characters")
    private String reason;

    private OffsetDateTime occurredAt;
}
