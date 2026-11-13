package pos.pos.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
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
public class InventoryWasteRequest {

    @NotNull(message = "locationId is required")
    private UUID locationId;

    @NotNull(message = "inventoryItemId is required")
    private UUID inventoryItemId;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than zero")
    @Digits(integer = 9, fraction = 3, message = "quantity must have at most 9 digits and 3 decimals")
    private BigDecimal quantity;

    @NotBlank(message = "reason is required")
    @Size(max = 2000, message = "reason must be at most 2000 characters")
    private String reason;

    private OffsetDateTime occurredAt;
}
