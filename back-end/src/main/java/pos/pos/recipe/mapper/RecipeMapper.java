package pos.pos.recipe.mapper;

import org.springframework.stereotype.Component;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.recipe.dto.RecipeComponentResponse;
import pos.pos.recipe.dto.RecipeCreateRequest;
import pos.pos.recipe.dto.RecipeResponse;
import pos.pos.recipe.dto.RecipeUpdateRequest;
import pos.pos.recipe.entity.Recipe;
import pos.pos.recipe.entity.RecipeComponent;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.user.entity.User;

import java.math.BigDecimal;
import java.util.List;

@Component
public class RecipeMapper {

    // Only the plain scalar fields -- menuItem is a relation that needs a restaurant-scoped
    // DB lookup and conditional validation (required only for FINISHED_DISH), so the service
    // resolves and sets it separately, same as how InventoryLocationService resolves and sets
    // branch outside of InventoryLocationMapper.applyRequest.
    public void applyRequest(Recipe recipe, RecipeCreateRequest request) {
        recipe.setCode(request.getCode());
        recipe.setName(request.getName());
        recipe.setDescription(request.getDescription());
        recipe.setRecipeType(request.getRecipeType());
        recipe.setStatus(request.getStatus() != null ? request.getStatus() : RecipeStatus.DRAFT);
        recipe.setYieldQuantity(request.getYieldQuantity() != null ? request.getYieldQuantity() : BigDecimal.ONE);
        recipe.setYieldUnit(request.getYieldUnit() != null ? request.getYieldUnit() : InventoryUnit.PORTION);
        recipe.setPrepTimeMinutes(request.getPrepTimeMinutes());
        recipe.setCookTimeMinutes(request.getCookTimeMinutes());
        recipe.setInstructions(request.getInstructions());
        recipe.setEffectiveFrom(request.getEffectiveFrom());
    }

    public void applyRequest(Recipe recipe, RecipeUpdateRequest request) {
        recipe.setCode(request.getCode());
        recipe.setName(request.getName());
        recipe.setDescription(request.getDescription());
        recipe.setRecipeType(request.getRecipeType());
        recipe.setStatus(request.getStatus() != null ? request.getStatus() : RecipeStatus.DRAFT);
        recipe.setYieldQuantity(request.getYieldQuantity() != null ? request.getYieldQuantity() : BigDecimal.ONE);
        recipe.setYieldUnit(request.getYieldUnit() != null ? request.getYieldUnit() : InventoryUnit.PORTION);
        recipe.setPrepTimeMinutes(request.getPrepTimeMinutes());
        recipe.setCookTimeMinutes(request.getCookTimeMinutes());
        recipe.setInstructions(request.getInstructions());
        recipe.setEffectiveFrom(request.getEffectiveFrom());
    }

    public RecipeResponse toResponse(Recipe recipe) {
        if (recipe == null) {
            return null;
        }

        List<RecipeComponentResponse> components = recipe.getComponents() == null
                ? List.of()
                : recipe.getComponents().stream().map(this::componentToResponse).toList();

        return RecipeResponse.builder()
                .id(recipe.getId())
                .restaurantId(recipe.getRestaurant() == null ? null : recipe.getRestaurant().getId())
                .menuItemId(recipe.getMenuItem() == null ? null : recipe.getMenuItem().getId())
                .menuItemName(recipe.getMenuItem() == null ? null : recipe.getMenuItem().getName())
                .code(recipe.getCode())
                .name(recipe.getName())
                .description(recipe.getDescription())
                .recipeType(recipe.getRecipeType())
                .status(recipe.getStatus())
                .version(recipe.getVersion())
                .yieldQuantity(recipe.getYieldQuantity())
                .yieldUnit(recipe.getYieldUnit())
                .prepTimeMinutes(recipe.getPrepTimeMinutes())
                .cookTimeMinutes(recipe.getCookTimeMinutes())
                .instructions(recipe.getInstructions())
                .theoreticalCost(recipe.getTheoreticalCost())
                .effectiveFrom(recipe.getEffectiveFrom())
                .retiredAt(recipe.getRetiredAt())
                .createdByUserName(displayName(recipe.getCreatedByUser()))
                .updatedByUserName(displayName(recipe.getUpdatedByUser()))
                .createdAt(recipe.getCreatedAt())
                .updatedAt(recipe.getUpdatedAt())
                .components(components)
                .build();
    }

    public RecipeComponentResponse componentToResponse(RecipeComponent component) {
        if (component == null) {
            return null;
        }

        return RecipeComponentResponse.builder()
                .id(component.getId())
                .componentType(component.getComponentType())
                .inventoryItemId(component.getInventoryItem() == null ? null : component.getInventoryItem().getId())
                .inventoryItemName(component.getInventoryItem() == null ? null : component.getInventoryItem().getName())
                .childRecipeId(component.getChildRecipe() == null ? null : component.getChildRecipe().getId())
                .childRecipeName(component.getChildRecipe() == null ? null : component.getChildRecipe().getName())
                .componentNameSnapshot(component.getComponentNameSnapshot())
                .quantity(component.getQuantity())
                .unit(component.getUnit())
                .yieldLossPercent(component.getYieldLossPercent())
                .optionalComponent(component.isOptionalComponent())
                .displayOrder(component.getDisplayOrder())
                .notes(component.getNotes())
                .build();
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }

        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }
}
