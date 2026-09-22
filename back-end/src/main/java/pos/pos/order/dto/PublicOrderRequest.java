package pos.pos.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicOrderRequest {

    @Min(value = 1, message = "guestCount must be greater than 0")
    private Integer guestCount;

    private String notes;

    @Valid
    @NotEmpty(message = "items must not be empty")
    private List<CreateOrderLineItemRequest> items;
}
