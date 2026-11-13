package pos.pos.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class OrderSplitRequest {

    @NotEmpty(message = "lineItemIds must not be empty")
    @Size(max = 500, message = "lineItemIds can have at most 500 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") UUID> lineItemIds;

    @Size(max = 50, message = "newOrderNumber must be at most 50 characters")
    private String newOrderNumber;

    private UUID targetTableId;

    @Max(value = 500, message = "guestCount must be at most 500")
    private Integer guestCount;

    @Size(max = 2000, message = "notes must be at most 2000 characters")
    private String notes;
}
