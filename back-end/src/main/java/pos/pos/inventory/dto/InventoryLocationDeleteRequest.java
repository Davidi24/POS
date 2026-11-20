package pos.pos.inventory.dto;

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
public class InventoryLocationDeleteRequest {

    // true: its stock moves to the branch's Uncategorized place; false: its stock is taken out of stock.
    @NotNull(message = "keepStock is required")
    private Boolean keepStock;
}
