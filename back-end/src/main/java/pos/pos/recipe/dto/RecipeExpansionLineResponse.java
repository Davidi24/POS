package pos.pos.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryUnit;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeExpansionLineResponse {

    private UUID inventoryItemId;
    private String inventoryItemName;
    private InventoryUnit baseUnit;
    private BigDecimal quantity;
}
