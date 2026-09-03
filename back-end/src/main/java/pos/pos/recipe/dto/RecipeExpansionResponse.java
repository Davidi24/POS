package pos.pos.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeExpansionResponse {

    private UUID recipeId;
    private String recipeName;
    private UUID menuItemId;
    private String menuItemName;
    private BigDecimal quantityRequested;
    private List<RecipeExpansionLineResponse> lines;
}
