package pos.pos.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineItemQuantityRequest {

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be greater than 0")
    @Max(value = 999, message = "quantity must be at most 999")
    private Integer quantity;
}
