package pos.pos.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.recipe.enums.RecipeComponentType;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeComponentResponse {

    private UUID id;
    private RecipeComponentType componentType;
    private UUID inventoryItemId;
    private String inventoryItemName;
    private UUID childRecipeId;
    private String childRecipeName;
    private String componentNameSnapshot;
    private BigDecimal quantity;
    private InventoryUnit unit;
    private BigDecimal yieldLossPercent;
    private boolean optionalComponent;
    private int displayOrder;
    private String notes;
}
