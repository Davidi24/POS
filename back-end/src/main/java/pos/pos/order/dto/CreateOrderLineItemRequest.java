package pos.pos.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderLineItemRequest {

    @NotNull(message = "menuItemId is required")
    private UUID menuItemId;

    private UUID variantId;

    // Which InventoryLocation this item's ingredients should be reserved/deducted from.
    // Nullable for now (backward compatibility) -- if omitted, inventory reservation is
    // silently skipped for this line item rather than blocking order creation.
    private UUID locationId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be greater than 0")
    private Integer quantity;

    private String notes;

    @Valid
    private List<CreateOrderItemOptionRequest> options;
}
