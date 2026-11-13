package pos.pos.menu.recipe;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.menu.recipe.DishRecipeDtos.DishRecipeRequest;
import pos.pos.menu.recipe.DishRecipeDtos.DishRecipeResponse;
import pos.pos.menu.recipe.DishRecipeDtos.IngredientChoiceResponse;

import java.util.List;
import java.util.UUID;

/** The dish editor's "Recipe / Ingredients": what a dish is made of from stock (its recipe underneath). */
@Tag(name = "Menu")
@Validated
@RestController
@RequestMapping("/menus/{menuId}")
@RequiredArgsConstructor
public class DishRecipeController {

    private final DishRecipeService dishRecipeService;

    @GetMapping("/sections/{sectionId}/items/{itemId}/recipe")
    @PreAuthorize("hasAnyAuthority('MENUS_READ', 'KDS_READ')")
    @Operation(summary = "The dish's ingredients from stock and what one portion costs")
    public ResponseEntity<DishRecipeResponse> getRecipe(
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(dishRecipeService.getRecipe(authentication, menuId, sectionId, itemId));
    }

    @PutMapping("/sections/{sectionId}/items/{itemId}/recipe")
    @PreAuthorize("hasAnyAuthority('MENUS_CREATE', 'MENUS_UPDATE')")
    @Operation(summary = "Saves the dish's ingredients from stock (its recipe); an empty list stops it taking stock")
    public ResponseEntity<DishRecipeResponse> saveRecipe(
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody DishRecipeRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(dishRecipeService.saveRecipe(authentication, menuId, sectionId, itemId, request));
    }

    @GetMapping("/ingredient-choices")
    @PreAuthorize("hasAnyAuthority('MENUS_CREATE', 'MENUS_UPDATE')")
    @Operation(summary = "Stock items and shared preparations a dish can be made with, with their unit costs")
    public ResponseEntity<List<IngredientChoiceResponse>> choices(
            @PathVariable UUID menuId,
            @RequestParam(required = false) @Size(max = 100, message = "search must be at most 100 characters") String search,
            Authentication authentication
    ) {
        return ResponseEntity.ok(dishRecipeService.choices(authentication, menuId, search));
    }
}
