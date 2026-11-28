package pos.pos.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
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
    @Max(value = 500, message = "guestCount must be at most 500")
    private Integer guestCount;

    @Size(max = 1000, message = "notes must be at most 1000 characters")
    private String notes;

    @Valid
    @NotEmpty(message = "items must not be empty")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") CreateOrderLineItemRequest> items;
}
