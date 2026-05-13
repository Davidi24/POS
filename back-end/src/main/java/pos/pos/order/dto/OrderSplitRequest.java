package pos.pos.order.dto;

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
    private List<UUID> lineItemIds;

    @Size(max = 50, message = "newOrderNumber must be at most 50 characters")
    private String newOrderNumber;

    private UUID targetTableId;

    private Integer guestCount;

    private String notes;
}
