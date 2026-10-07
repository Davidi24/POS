package pos.pos.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
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

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be greater than 0")
    @Max(value = 999, message = "quantity must be at most 999")
    private Integer quantity;

    @Size(max = 1000, message = "notes must be at most 1000 characters")
    private String notes;

    @Valid
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") CreateOrderItemOptionRequest> options;
}
