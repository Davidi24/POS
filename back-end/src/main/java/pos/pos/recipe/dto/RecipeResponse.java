package pos.pos.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.recipe.enums.RecipeType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID menuItemId;
    private String menuItemName;
    private String code;
    private String name;
    private String description;
    private RecipeType recipeType;
    private RecipeStatus status;
    private int version;
    private BigDecimal yieldQuantity;
    private InventoryUnit yieldUnit;
    private Integer prepTimeMinutes;
    private Integer cookTimeMinutes;
    private String instructions;
    private BigDecimal theoreticalCost;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime retiredAt;
    private String createdByUserName;
    private String updatedByUserName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<RecipeComponentResponse> components;
}
