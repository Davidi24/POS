package pos.pos.recipe.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.inventory.InventoryItemNotFoundException;
import pos.pos.exception.menu.MenuItemNotFoundException;
import pos.pos.exception.recipe.RecipeComponentValidationException;
import pos.pos.exception.recipe.RecipeNotFoundException;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.inventory.service.UnitConversionService;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.recipe.dto.RecipeComponentUpsertRequest;
import pos.pos.recipe.dto.RecipeCreateRequest;
import pos.pos.recipe.dto.RecipeExpansionLineResponse;
import pos.pos.recipe.dto.RecipeExpansionResponse;
import pos.pos.recipe.dto.RecipeResponse;
import pos.pos.recipe.dto.RecipeUpdateRequest;
import pos.pos.recipe.entity.Recipe;
import pos.pos.recipe.entity.RecipeComponent;
import pos.pos.recipe.enums.RecipeComponentType;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.recipe.enums.RecipeType;
import pos.pos.recipe.mapper.RecipeMapper;
import pos.pos.recipe.repository.RecipeRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Recipe management/setup only, for now: defining recipes, their components, and calculating
// theoretical cost. This deliberately does NOT touch stock -- there is no "consume ingredients
// on sale" logic here, and there won't be until the Order module exists to trigger it. Selling
// a dish and deducting its ingredients is a future feature that belongs in an Order/consumption
// service, not here; this service only ever reads InventoryItem.costPerUnit, it never creates
// an InventoryMovement or changes an InventoryLevel.
@Service
@RequiredArgsConstructor
public class RecipeService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final RestaurantScopeService restaurantScopeService;
    private final RecipeRepository recipeRepository;
    private final MenuItemRepository menuItemRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final RecipeMapper recipeMapper;
    private final UnitConversionService unitConversionService;


    // Takes the request from the API and checks the user, after that the method checks if this recipe
    // belongs to a FINISHED_DISH type and if so checks it belongs to a menu on the menu List
    // If so it allows the user to create a normal recipe and set the attributes based on the request
    @Transactional
    public RecipeResponse createRecipe(Authentication authentication, UUID restaurantId, RecipeCreateRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        MenuItem menuItem = resolveMenuItem(restaurantId, request.getRecipeType(), request.getMenuItemId());
        assertCodeAvailable(restaurantId, request.getCode(), null);

        Recipe recipe = new Recipe();
        recipe.setRestaurant(restaurant);
        recipe.setMenuItem(menuItem);
        recipe.setCreatedBy(actorId);
        recipe.setUpdatedBy(actorId);
        recipeMapper.applyRequest(recipe, request);

        return recipeMapper.toResponse(saveRecipe(recipe));
    }


    // Same as create recipe but only for updating changes
    @Transactional
    public RecipeResponse updateRecipe(
            Authentication authentication,
            UUID restaurantId,
            UUID recipeId,
            RecipeUpdateRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Recipe recipe = requireRecipe(restaurantId, recipeId);

        MenuItem menuItem = resolveMenuItem(restaurantId, request.getRecipeType(), request.getMenuItemId());
        assertCodeAvailable(restaurantId, request.getCode(), recipe.getCode());

        recipe.setMenuItem(menuItem);
        recipe.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        recipeMapper.applyRequest(recipe, request);

        return recipeMapper.toResponse(saveRecipe(recipe));
    }

    // Returns the recipe from ID
    @Transactional(readOnly = true)
    public RecipeResponse getRecipe(Authentication authentication, UUID restaurantId, UUID recipeId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return recipeMapper.toResponse(requireRecipe(restaurantId, recipeId));
    }

    //This method returns the Recipes, it also can return them based on type or status. Gets the parameters
    // and checks based on them through the repository functions and returns them
    @Transactional(readOnly = true)
    public List<RecipeResponse> listRecipes(
            Authentication authentication,
            UUID restaurantId,
            RecipeType type,
            RecipeStatus status
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        List<Recipe> recipes;
        if (type != null && status != null) {
            recipes = recipeRepository.findAllByRestaurant_IdOrderByNameAsc(restaurantId).stream()
                    .filter(recipe -> recipe.getRecipeType() == type && recipe.getStatus() == status)
                    .toList();
        } else if (type != null) {
            recipes = recipeRepository.findAllByRestaurant_IdAndRecipeTypeOrderByNameAsc(restaurantId, type);
        } else if (status != null) {
            recipes = recipeRepository.findAllByRestaurant_IdAndStatusOrderByNameAsc(restaurantId, status);
        } else {
            recipes = recipeRepository.findAllByRestaurant_IdOrderByNameAsc(restaurantId);
        }

        return recipes.stream().map(recipeMapper::toResponse).toList();
    }


    //This method is used to update or insert any Recipe Components for a specific Recipes
    @Transactional
    public RecipeResponse upsertComponent(
            Authentication authentication,
            UUID restaurantId,
            UUID recipeId,
            RecipeComponentUpsertRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Recipe recipe = requireRecipe(restaurantId, recipeId);

        RecipeComponentType componentType = request.getComponentType();
        boolean hasItem = request.getInventoryItemId() != null;
        boolean hasChildRecipe = request.getChildRecipeId() != null;

        // Checks if maybe there is a false input, the inout should either be Item + Item Type or ChildRecipe and Subrecipe Type
        if (hasItem == hasChildRecipe) {
            throw new RecipeComponentValidationException(
                    "Exactly one of inventoryItemId or childRecipeId must be supplied, not both or neither"
            );
        }

        if (componentType == RecipeComponentType.INVENTORY_ITEM && !hasItem) {
            throw new RecipeComponentValidationException("inventoryItemId is required when componentType is INVENTORY_ITEM");
        }

        if (componentType == RecipeComponentType.SUB_RECIPE && !hasChildRecipe) {
            throw new RecipeComponentValidationException("childRecipeId is required when componentType is SUB_RECIPE");
        }

        //After checking whether the input is right it takes the input needed
        InventoryItem inventoryItem = null;
        Recipe childRecipe = null;

        if (hasItem) {
            inventoryItem = requireItem(restaurantId, request.getInventoryItemId());
        } else {
            if (Objects.equals(request.getChildRecipeId(), recipeId)) {
                throw new RecipeComponentValidationException("A recipe cannot reference itself as a sub-recipe component");
            }
            childRecipe = requireChildRecipe(restaurantId, request.getChildRecipeId());
        }

        //After making the needed checks it sets the needed Information to the component and saves the recipe
        InventoryItem finalInventoryItem = inventoryItem;
        Recipe finalChildRecipe = childRecipe;
        RecipeComponent component = recipe.getComponents().stream()
                .filter(existing -> matchesSameReference(existing, componentType, request.getInventoryItemId(), request.getChildRecipeId()))
                .findFirst()
                .orElseGet(() -> {
                    RecipeComponent created = new RecipeComponent();
                    recipe.addComponent(created);
                    return created;
                });

        component.setComponentType(componentType);
        component.setInventoryItem(finalInventoryItem);
        component.setChildRecipe(finalChildRecipe);
        component.setComponentNameSnapshot(finalInventoryItem != null ? finalInventoryItem.getName() : finalChildRecipe.getName());
        component.setQuantity(request.getQuantity());
        component.setUnit(request.getUnit());
        component.setYieldLossPercent(request.getYieldLossPercent() != null ? request.getYieldLossPercent() : BigDecimal.ZERO);
        component.setOptionalComponent(request.getOptionalComponent() != null && request.getOptionalComponent());
        component.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        component.setNotes(request.getNotes());

        return recipeMapper.toResponse(saveRecipe(recipe));
    }




    //Gets the component and recipe Id and removes it from this recipe
    @Transactional
    public RecipeResponse removeComponent(Authentication authentication, UUID restaurantId, UUID recipeId, UUID componentId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Recipe recipe = requireRecipe(restaurantId, recipeId);

        RecipeComponent component = recipe.getComponents().stream()
                .filter(existing -> Objects.equals(existing.getId(), componentId))
                .findFirst()
                .orElseThrow(() -> new AuthException("Recipe component not found", HttpStatus.NOT_FOUND));

        recipe.removeComponent(component);

        return recipeMapper.toResponse(saveRecipe(recipe));
    }

    //Only changes the Status of the Recipe
    @Transactional
    public RecipeResponse archiveRecipe(Authentication authentication, UUID restaurantId, UUID recipeId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Recipe recipe = requireRecipe(restaurantId, recipeId);

        recipe.setStatus(RecipeStatus.ARCHIVED);
        recipe.setRetiredAt(OffsetDateTime.now(ZoneOffset.UTC));
        recipe.setUpdatedBy(restaurantScopeService.currentUserId(authentication));

        return recipeMapper.toResponse(saveRecipe(recipe));
    }


    //Tekes a specific recipe and calculates the theoretical cost of the recipe using the method acumulateCost and updates the recipe
    @Transactional
    public RecipeResponse calculateTheoreticalCost(Authentication authentication, UUID restaurantId, UUID recipeId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Recipe recipe = requireRecipe(restaurantId, recipeId);

        BigDecimal cost = accumulateCost(recipe, BigDecimal.ONE, new HashSet<>());

        recipe.setTheoreticalCost(cost.setScale(2, RoundingMode.HALF_UP));
        recipe.setUpdatedBy(restaurantScopeService.currentUserId(authentication));

        return recipeMapper.toResponse(saveRecipe(recipe));
    }

    // Read-only preview: "if I made quantityRequested of this menu item's active recipe, what raw
    // inventory items -- and how much of each -- would that use?" Walks the exact same component
    // tree as calculateTheoreticalCost (same recursion shape, same cycle guard, same yield-loss
    // math), just accumulating quantities per raw item instead of summing a cost. Never creates
    // an InventoryMovement and never touches InventoryLevel -- that's the future Order module's
    // job, not this one's.
    @Transactional(readOnly = true)
    public RecipeExpansionResponse expandToInventoryConsumption(
            Authentication authentication,
            UUID restaurantId,
            UUID menuItemId,
            BigDecimal quantityRequested
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        Recipe recipe = recipeRepository
                .findByRestaurant_IdAndMenuItem_IdAndStatus(restaurantId, menuItemId, RecipeStatus.ACTIVE)
                .orElseThrow(RecipeNotFoundException::new);

        // Two maps built together in one walk: quantities keyed by item id (so the same raw item
        // used in two different branches, e.g. flour in both the dough and a roux, sums into one
        // line instead of two), and the actual InventoryItem references, so names/units can be
        // resolved afterward without a second query per item.
        Map<UUID, BigDecimal> quantityByItemId = new LinkedHashMap<>();
        Map<UUID, InventoryItem> itemsById = new HashMap<>();

        accumulateConsumption(recipe, quantityRequested, new HashSet<>(), quantityByItemId, itemsById);

        List<RecipeExpansionLineResponse> lines = quantityByItemId.entrySet().stream()
                .map(entry -> {
                    InventoryItem item = itemsById.get(entry.getKey());
                    return RecipeExpansionLineResponse.builder()
                            .inventoryItemId(item.getId())
                            .inventoryItemName(item.getName())
                            .baseUnit(item.getBaseUnit())
                            .quantity(entry.getValue())
                            .build();
                })
                .toList();

        return RecipeExpansionResponse.builder()
                .recipeId(recipe.getId())
                .recipeName(recipe.getName())
                .menuItemId(recipe.getMenuItem() == null ? null : recipe.getMenuItem().getId())
                .menuItemName(recipe.getMenuItem() == null ? null : recipe.getMenuItem().getName())
                .quantityRequested(quantityRequested)
                .lines(lines)
                .build();
    }

    // This method takes each component, calls the method yieldAdjusted quantity and calculates what is the
    // exact quantity needed to have the recipe with this needed quantity(so it calculates loss and other stuff)
    // and puts the whole result in a variable and returns it as total
    private BigDecimal accumulateCost(Recipe recipe, BigDecimal multiplier, Set<UUID> visitedRecipeIds) {
        if (!visitedRecipeIds.add(recipe.getId())) {
            throw new RecipeComponentValidationException(
                    "Cycle detected in recipe components: \"" + recipe.getName() + "\" is reachable from one of its own sub-recipes"
            );
        }

        BigDecimal total = BigDecimal.ZERO;

        for (RecipeComponent component : recipe.getComponents()) {
            BigDecimal effectiveQuantity = yieldAdjustedQuantity(component.getQuantity(), component.getYieldLossPercent())
                    .multiply(multiplier);

            if (component.getComponentType() == RecipeComponentType.INVENTORY_ITEM) {
                InventoryItem item = component.getInventoryItem();
                BigDecimal unitCost = item.getCostPerUnit() == null ? BigDecimal.ZERO : item.getCostPerUnit();
                total = total.add(effectiveQuantity.multiply(unitCost));
            } else {
                total = total.add(accumulateCost(component.getChildRecipe(), effectiveQuantity, visitedRecipeIds));
            }
        }

        visitedRecipeIds.remove(recipe.getId());
        return total;
    }

    // Same tree-walking structure and cycle guard as accumulateCost above -- kept as a separate
    // method rather than refactored to share one implementation, since threading a shared
    // accumulation strategy through accumulateCost risked changing its already-working behavior
    // for no benefit to it. The one real difference beyond "sum a map instead of a total": a
    // recipe component's quantity is normalized into the raw item's own baseUnit (via
    // UnitConversionService) before being merged in, since two components referencing the same
    // item could be written in different units (e.g. "500 GRAM" here, "0.2 KILOGRAM" there) --
    // summing those as raw numbers without converting first would silently produce a wrong total.
    private void accumulateConsumption(
            Recipe recipe,
            BigDecimal multiplier,
            Set<UUID> visitedRecipeIds,
            Map<UUID, BigDecimal> quantityByItemId,
            Map<UUID, InventoryItem> itemsById
    ) {
        if (!visitedRecipeIds.add(recipe.getId())) {
            throw new RecipeComponentValidationException(
                    "Cycle detected in recipe components: \"" + recipe.getName() + "\" is reachable from one of its own sub-recipes"
            );
        }

        for (RecipeComponent component : recipe.getComponents()) {
            BigDecimal effectiveQuantity = yieldAdjustedQuantity(component.getQuantity(), component.getYieldLossPercent())
                    .multiply(multiplier);

            if (component.getComponentType() == RecipeComponentType.INVENTORY_ITEM) {
                InventoryItem item = component.getInventoryItem();
                BigDecimal normalizedQuantity = unitConversionService.convert(
                        item,
                        effectiveQuantity,
                        component.getUnit(),
                        item.getBaseUnit()
                );

                quantityByItemId.merge(item.getId(), normalizedQuantity, BigDecimal::add);
                itemsById.putIfAbsent(item.getId(), item);
            } else {
                accumulateConsumption(component.getChildRecipe(), effectiveQuantity, visitedRecipeIds, quantityByItemId, itemsById);
            }
        }

        visitedRecipeIds.remove(recipe.getId());
    }

    // The use of this method is:
    // it takes the yeldPercentage(which is the percentage ofte product that gets lost when using it for this recipe)
    // and calculates how much you need to take for this quantity so in the end you end up with having that amount exactly for
    // your product
    private BigDecimal yieldAdjustedQuantity(BigDecimal quantity, BigDecimal yieldLossPercent) {
        BigDecimal loss = yieldLossPercent == null ? BigDecimal.ZERO : yieldLossPercent;
        BigDecimal retainedFraction = BigDecimal.ONE.subtract(loss.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP));

        if (retainedFraction.signum() <= 0) {
            throw new RecipeComponentValidationException("yieldLossPercent cannot be 100% for a component with a nonzero quantity");
        }

        return quantity.divide(retainedFraction, 6, RoundingMode.HALF_UP);
    }


    // Only checks if the given type and component match each other(Item with INVENTORY_ITEM)
    // and chirld recipe with SUB_RECIPE
    private boolean matchesSameReference(
            RecipeComponent existing,
            RecipeComponentType componentType,
            UUID inventoryItemId,
            UUID childRecipeId
    ) {
        if (componentType == RecipeComponentType.INVENTORY_ITEM) {
            return existing.getInventoryItem() != null && Objects.equals(existing.getInventoryItem().getId(), inventoryItemId);
        }

        return existing.getChildRecipe() != null && Objects.equals(existing.getChildRecipe().getId(), childRecipeId);
    }

    //Returns the menu item if there is one for the specific recipe
    private MenuItem resolveMenuItem(UUID restaurantId, RecipeType recipeType, UUID menuItemId) {
        if (recipeType == RecipeType.FINISHED_DISH && menuItemId == null) {
            throw new AuthException("menuItemId is required when recipeType is FINISHED_DISH", HttpStatus.BAD_REQUEST);
        }

        if (menuItemId == null) {
            return null;
        }

        return requireMenuItem(restaurantId, menuItemId);
    }

    // Uses the repository query to find the menu Item
    private MenuItem requireMenuItem(UUID restaurantId, UUID menuItemId) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(MenuItemNotFoundException::new);

        // Checks the Menu Ownership in a chain way becouse this is how the structure of the Menu is done,
        // and checks if it follows the whole menu design
        if (menuItem.getSection() == null
                || menuItem.getSection().getMenu() == null
                || menuItem.getSection().getMenu().getRestaurant() == null
                || !Objects.equals(menuItem.getSection().getMenu().getRestaurant().getId(), restaurantId)) {
            throw new MenuItemNotFoundException();
        }

        return menuItem;
    }

    private InventoryItem requireItem(UUID restaurantId, UUID itemId) {
        return inventoryItemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(itemId, restaurantId)
                .orElseThrow(InventoryItemNotFoundException::new);
    }

    private Recipe requireChildRecipe(UUID restaurantId, UUID childRecipeId) {
        return recipeRepository.findByIdAndRestaurant_Id(childRecipeId, restaurantId)
                .orElseThrow(RecipeNotFoundException::new);
    }

    private Recipe requireRecipe(UUID restaurantId, UUID recipeId) {
        return recipeRepository.findByIdAndRestaurant_Id(recipeId, restaurantId)
                .orElseThrow(RecipeNotFoundException::new);
    }

    // Only checks if the code is available or not
    private void assertCodeAvailable(UUID restaurantId, String rawCode, String existingCode) {
        String normalizedCode = NormalizationUtils.normalizeCode(rawCode, 80);
        if (normalizedCode == null || normalizedCode.equals(existingCode)) {
            return;
        }

        recipeRepository.findByRestaurant_IdAndCode(restaurantId, normalizedCode)
                .ifPresent(existing -> {
                    throw new AuthException(
                            "This code is already used by " + existing.getName() + " in this restaurant",
                            HttpStatus.CONFLICT
                    );
                });
    }

    private Recipe saveRecipe(Recipe recipe) {
        try {
            return recipeRepository.saveAndFlush(recipe);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Recipe update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
