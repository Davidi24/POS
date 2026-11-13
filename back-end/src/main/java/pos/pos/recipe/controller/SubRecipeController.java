package pos.pos.recipe.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.menu.recipe.DishRecipeDtos.IngredientChoiceResponse;
import pos.pos.recipe.dto.SubRecipeDtos.SubRecipeRequest;
import pos.pos.recipe.dto.SubRecipeDtos.SubRecipeResponse;
import pos.pos.recipe.service.SubRecipeService;

import java.util.List;
import java.util.UUID;

// Inventory → Items → sub-recipes: things the kitchen makes (a sauce, a dough), each an item with its recipe, saved
// together like a dish's ingredients.
@Tag(name = "Sub-recipes")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/sub-recipes")
@RequiredArgsConstructor
public class SubRecipeController {

    private final SubRecipeService subRecipeService;

    @GetMapping("/choices")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @Operation(summary = "What a sub-recipe can be made with: stock items and other sub-recipes, with unit costs")
    public List<IngredientChoiceResponse> choices(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) UUID forRecipeId,
            Authentication authentication
    ) {
        return subRecipeService.choices(authentication, restaurantId, forRecipeId);
    }

    @GetMapping("/{recipeId}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @Operation(summary = "A sub-recipe with its item, ingredients and cost")
    public SubRecipeResponse get(@PathVariable UUID restaurantId, @PathVariable UUID recipeId, Authentication authentication) {
        return subRecipeService.get(authentication, restaurantId, recipeId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Create a sub-recipe: its item and its recipe with every ingredient")
    @ApiResponse(responseCode = "201", description = "Sub-recipe created")
    public ResponseEntity<SubRecipeResponse> create(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody SubRecipeRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subRecipeService.create(authentication, restaurantId, request));
    }

    @PutMapping("/{recipeId}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Change a sub-recipe as a whole: name, kind, batch, ingredients, in use or not")
    public SubRecipeResponse update(
            @PathVariable UUID restaurantId,
            @PathVariable UUID recipeId,
            @Valid @RequestBody SubRecipeRequest request,
            Authentication authentication
    ) {
        return subRecipeService.update(authentication, restaurantId, recipeId, request);
    }
}
