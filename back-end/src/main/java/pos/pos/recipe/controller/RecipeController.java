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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.recipe.dto.RecipeComponentUpsertRequest;
import pos.pos.recipe.dto.RecipeCreateRequest;
import pos.pos.recipe.dto.RecipeExpansionResponse;
import pos.pos.recipe.dto.RecipeResponse;
import pos.pos.recipe.dto.RecipeUpdateRequest;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.recipe.enums.RecipeType;
import pos.pos.recipe.service.RecipeService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Management/setup only: defining recipes and their ingredients, and calculating theoretical
// cost. Nothing here consumes stock -- that depends on the Order module, which doesn't exist yet.
@Tag(name = "Recipes")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create a new recipe")
    @ApiResponse(responseCode = "201", description = "Recipe created")
    public ResponseEntity<RecipeResponse> createRecipe(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody RecipeCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recipeService.createRecipe(authentication, restaurantId, request));
    }

    @PutMapping("/{recipeId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace a recipe's own fields (not its components)")
    @ApiResponse(responseCode = "200", description = "Recipe updated")
    @ApiResponse(responseCode = "404", description = "Recipe not found")
    public ResponseEntity<RecipeResponse> updateRecipe(
            @PathVariable UUID restaurantId,
            @PathVariable UUID recipeId,
            @Valid @RequestBody RecipeUpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(recipeService.updateRecipe(authentication, restaurantId, recipeId, request));
    }

    @GetMapping("/{recipeId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one recipe, with its components")
    @ApiResponse(responseCode = "200", description = "Recipe found")
    @ApiResponse(responseCode = "404", description = "Recipe not found")
    public ResponseEntity<RecipeResponse> getRecipe(
            @PathVariable UUID restaurantId,
            @PathVariable UUID recipeId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(recipeService.getRecipe(authentication, restaurantId, recipeId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List recipes, optionally filtered by type and/or status")
    public ResponseEntity<List<RecipeResponse>> listRecipes(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) RecipeType type,
            @RequestParam(required = false) RecipeStatus status,
            Authentication authentication
    ) {
        return ResponseEntity.ok(recipeService.listRecipes(authentication, restaurantId, type, status));
    }

    // Upsert, not create: identity comes from the request body (which inventoryItemId or
    // childRecipeId it references), not a path variable, since a component isn't addressable
    // by a single simple id the way most other resources here are.
    @PutMapping("/{recipeId}/components")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Add or update one recipe component (identity comes from the request body)")
    @ApiResponse(responseCode = "200", description = "Component saved")
    public ResponseEntity<RecipeResponse> upsertComponent(
            @PathVariable UUID restaurantId,
            @PathVariable UUID recipeId,
            @Valid @RequestBody RecipeComponentUpsertRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(recipeService.upsertComponent(authentication, restaurantId, recipeId, request));
    }

    @DeleteMapping("/{recipeId}/components/{componentId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Remove a component from this recipe")
    @ApiResponse(responseCode = "200", description = "Component removed")
    public ResponseEntity<RecipeResponse> removeComponent(
            @PathVariable UUID restaurantId,
            @PathVariable UUID recipeId,
            @PathVariable UUID componentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(recipeService.removeComponent(authentication, restaurantId, recipeId, componentId));
    }

    @PostMapping("/{recipeId}/archive")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Archive a recipe (soft retirement, not a delete)")
    @ApiResponse(responseCode = "200", description = "Recipe archived")
    public ResponseEntity<RecipeResponse> archiveRecipe(
            @PathVariable UUID restaurantId,
            @PathVariable UUID recipeId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(recipeService.archiveRecipe(authentication, restaurantId, recipeId));
    }

    @GetMapping("/expand-menu-item/{menuItemId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Preview the raw inventory ingredients (and quantities) needed to make N of this menu item's active recipe")
    @ApiResponse(responseCode = "200", description = "Expansion computed")
    @ApiResponse(responseCode = "404", description = "No active recipe found for this menu item")
    public ResponseEntity<RecipeExpansionResponse> expandMenuItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID menuItemId,
            @RequestParam(name = "quantity", defaultValue = "1") BigDecimal quantity,
            Authentication authentication
    ) {
        return ResponseEntity.ok(recipeService.expandToInventoryConsumption(authentication, restaurantId, menuItemId, quantity));
    }

    @PostMapping("/{recipeId}/recalculate-cost")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Recalculate and save the recipe's theoretical cost, walking the full component tree")
    @ApiResponse(responseCode = "200", description = "Cost recalculated")
    public ResponseEntity<RecipeResponse> recalculateCost(
            @PathVariable UUID restaurantId,
            @PathVariable UUID recipeId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(recipeService.calculateTheoreticalCost(authentication, restaurantId, recipeId));
    }
}
