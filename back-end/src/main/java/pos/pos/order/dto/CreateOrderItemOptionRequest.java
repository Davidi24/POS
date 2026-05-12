package pos.pos.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderItemOptionRequest {

    @NotNull(message = "optionItemId is required")
    private UUID optionItemId;

    @Min(value = 1, message = "quantity must be greater than 0")
    private Integer quantity;

    private String notes;
}
