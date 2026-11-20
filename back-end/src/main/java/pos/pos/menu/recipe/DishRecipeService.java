package pos.pos.menu.recipe;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.inventory.service.InventoryUnitConversion;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.recipe.DishRecipeDtos.DishIngredientRequest;
import pos.pos.menu.recipe.DishRecipeDtos.DishIngredientResponse;
import pos.pos.menu.recipe.DishRecipeDtos.DishRecipeRequest;
import pos.pos.menu.recipe.DishRecipeDtos.DishRecipeResponse;
import pos.pos.menu.recipe.DishRecipeDtos.IngredientChoiceResponse;
import pos.pos.menu.recipe.DishRecipeDtos.IngredientKind;
import pos.pos.menu.service.MenuItemService;
import pos.pos.recipe.entity.Recipe;
import pos.pos.recipe.entity.RecipeComponent;
import pos.pos.recipe.enums.RecipeComponentType;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.recipe.enums.RecipeType;
import pos.pos.recipe.repository.RecipeRepository;
import pos.pos.recipe.service.RecipeService;
import pos.pos.recipe.service.SubRecipeService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.security.scope.ActorScopeService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A dish's ingredients from stock, edited in the dish editor. Underneath they are the dish's ACTIVE recipe (type
 * FINISHED_DISH), the same one the Recipes page shows and that takes stock when the dish is served, so nothing is
 * typed twice. Shared preparations (sauce, dough) stay their own recipes and are picked here like a stock item.
 */
@Service
@RequiredArgsConstructor
public class DishRecipeService {

    private static final Set<RecipeType> PREPARATIONS = EnumSet.of(RecipeType.PREP_BATCH, RecipeType.SUB_RECIPE);

    private final MenuItemService menuItemService;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final SubRecipeService subRecipeService;
    private final InventoryItemRepository inventoryItemRepository;
    private final ActorScopeService actorScopeService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public DishRecipeResponse getRecipe(Authentication authentication, UUID menuId, UUID sectionId, UUID itemId) {
        MenuItem item = menuItemService.requireReadableItem(authentication, menuId, sectionId, itemId);
        Recipe recipe = recipeRepository.findFirstByRestaurant_IdAndMenuItem_IdAndStatusOrderByVersionDesc(
                restaurantOf(item).getId(), item.getId(), RecipeStatus.ACTIVE).orElse(null);
        return response(item, recipe, recipe == null ? BigDecimal.ZERO : liveCost(recipe));
    }

    /**
     * Saves the dish's ingredients, in the order given. The first ingredients create the dish's recipe; an empty list
     * retires it, so the dish stops taking anything from stock. Amounts already given a waste % on the Recipes page
     * keep it.
     */
    @Transactional
    public DishRecipeResponse saveRecipe(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId,
            DishRecipeRequest request
    ) {
        MenuItem item = menuItemService.requireWritableItem(authentication, menuId, sectionId, itemId);
        // One save at a time per dish, so two editors can't both start a recipe for it.
        entityManager.lock(item, LockModeType.PESSIMISTIC_WRITE);
        Restaurant restaurant = restaurantOf(item);
        UUID actorId = actorScopeService.currentUserId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Wanted> wanted = resolve(restaurant.getId(), request.ingredients());

        List<Recipe> active = recipeRepository.findAllByRestaurant_IdAndMenuItem_IdAndStatusOrderByVersionDesc(
                restaurant.getId(), item.getId(), RecipeStatus.ACTIVE);
        Recipe recipe = active.isEmpty() ? null : active.get(0);
        // Older data could hold more than one ACTIVE recipe for a dish; only the newest stays.
        active.stream().skip(1).forEach(older -> retire(older, actorId, now));
        if (wanted.isEmpty()) {
            if (recipe != null) {
                retire(recipe, actorId, now);
                save(recipe);
            }
            return response(item, null, BigDecimal.ZERO);
        }

        if (recipe == null) {
            recipe = newRecipe(item, restaurant, actorId, now);
        }
        recipe.setName(dishName(item));
        recipe.setUpdatedBy(actorId);
        replaceComponents(recipe, wanted);
        BigDecimal cost = recipeService.costOf(recipe);
        recipe.setTheoreticalCost(cost.setScale(2, RoundingMode.HALF_UP));
        return response(item, save(recipe), cost);
    }

    /** What dishes can be made with: active stock items and sub-recipes in use, by name (made-here items through their sub-recipe). */
    @Transactional(readOnly = true)
    public List<IngredientChoiceResponse> choices(Authentication authentication, UUID menuId, String search) {
        Menu menu = menuItemService.requireWritableMenu(authentication, menuId);
        return subRecipeService.choiceList(menu.getRestaurant().getId(), search, null);
    }

    private record Wanted(IngredientKind kind, InventoryItem stockItem, Recipe preparation, BigDecimal quantity, InventoryUnit unit) {
        String key() {
            return kind + ":" + (stockItem != null ? stockItem.getId() : preparation.getId());
        }

        String name() {
            return stockItem != null ? stockItem.getName() : preparation.getName();
        }
    }

    private List<Wanted> resolve(UUID restaurantId, List<DishIngredientRequest> ingredients) {
        List<Wanted> wanted = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DishIngredientRequest ingredient : ingredients) {
            if ((ingredient.inventoryItemId() == null) == (ingredient.preparationId() == null)) {
                throw new AuthException("Choose a stock item or a preparation for each ingredient", HttpStatus.BAD_REQUEST);
            }
            Wanted next;
            if (ingredient.inventoryItemId() != null) {
                InventoryItem stockItem = inventoryItemRepository
                        .findByIdAndRestaurant_IdAndDeletedAtIsNull(ingredient.inventoryItemId(), restaurantId)
                        .orElseThrow(() -> new AuthException("One of the ingredients is no longer a stock item. Refresh and try again.",
                                HttpStatus.CONFLICT));
                // An item made here stands for its sub-recipe, so serving the dish takes the sub-recipe's ingredients.
                Recipe madeBy = recipeRepository.findByOutputItem_Id(stockItem.getId()).orElse(null);
                if (madeBy != null) {
                    if (madeBy.getStatus() != RecipeStatus.ACTIVE) {
                        throw new AuthException(madeBy.getName() + " is switched off. Switch it on or choose another item.", HttpStatus.CONFLICT);
                    }
                    requireUnit(madeBy.getName(), ingredient.unit(), madeBy.getYieldUnit());
                    next = new Wanted(IngredientKind.PREPARATION, null, madeBy, ingredient.quantity(), ingredient.unit());
                } else {
                    if (!stockItem.isActive()) {
                        throw new AuthException(stockItem.getName() + " is switched off in stock. Switch it on or choose another item.",
                                HttpStatus.CONFLICT);
                    }
                    requireUnit(stockItem.getName(), ingredient.unit(), stockItem.getBaseUnit());
                    next = new Wanted(IngredientKind.STOCK, stockItem, null, ingredient.quantity(), ingredient.unit());
                }
            } else {
                Recipe preparation = recipeRepository.findByIdAndRestaurant_Id(ingredient.preparationId(), restaurantId)
                        .filter(found -> PREPARATIONS.contains(found.getRecipeType()) && found.getStatus() == RecipeStatus.ACTIVE)
                        .orElseThrow(() -> new AuthException("One of the preparations is no longer active. Refresh and try again.",
                                HttpStatus.CONFLICT));
                requireUnit(preparation.getName(), ingredient.unit(), preparation.getYieldUnit());
                next = new Wanted(IngredientKind.PREPARATION, null, preparation, ingredient.quantity(), ingredient.unit());
            }
            if (!seen.add(next.key())) {
                throw new AuthException(next.name() + " is listed twice. Give it once, with the whole amount.", HttpStatus.BAD_REQUEST);
            }
            wanted.add(next);
        }
        return wanted;
    }

    private static void requireUnit(String name, InventoryUnit unit, InventoryUnit countedIn) {
        if (!InventoryUnitConversion.canConvert(unit, countedIn)) {
            String allowed = String.join(", ", Arrays.stream(InventoryUnit.values())
                    .filter(candidate -> InventoryUnitConversion.canConvert(candidate, countedIn))
                    .map(candidate -> candidate.name().toLowerCase(Locale.ROOT))
                    .toList());
            throw new AuthException(name + " is counted in " + countedIn.name().toLowerCase(Locale.ROOT)
                    + ", so measure it in " + allowed + ".", HttpStatus.BAD_REQUEST);
        }
    }

    // Updates the rows that stay in place (they may carry a waste % from the Recipes page), adds new ones and drops
    // the ones no longer listed.
    private void replaceComponents(Recipe recipe, List<Wanted> wanted) {
        Map<String, RecipeComponent> existing = new LinkedHashMap<>();
        for (RecipeComponent component : recipe.getComponents()) {
            existing.putIfAbsent(keyOf(component), component);
        }
        List<RecipeComponent> leftover = new ArrayList<>(recipe.getComponents());
        for (int index = 0; index < wanted.size(); index++) {
            Wanted next = wanted.get(index);
            RecipeComponent component = existing.remove(next.key());
            if (component == null) {
                component = new RecipeComponent();
                recipe.addComponent(component);
            } else {
                leftover.remove(component);
            }
            component.setComponentType(next.kind() == IngredientKind.STOCK ? RecipeComponentType.INVENTORY_ITEM : RecipeComponentType.SUB_RECIPE);
            component.setInventoryItem(next.stockItem());
            component.setChildRecipe(next.preparation());
            component.setComponentNameSnapshot(next.name());
            component.setQuantity(next.quantity());
            component.setUnit(next.unit());
            component.setDisplayOrder(index);
        }
        leftover.forEach(recipe::removeComponent);
    }

    private static String keyOf(RecipeComponent component) {
        return component.getInventoryItem() != null
                ? IngredientKind.STOCK + ":" + component.getInventoryItem().getId()
                : IngredientKind.PREPARATION + ":" + component.getChildRecipe().getId();
    }

    private Recipe newRecipe(MenuItem item, Restaurant restaurant, UUID actorId, OffsetDateTime now) {
        int version = recipeRepository.maxVersionForMenuItem(item.getId()) + 1;
        Recipe recipe = new Recipe();
        recipe.setRestaurant(restaurant);
        recipe.setMenuItem(item);
        recipe.setRecipeType(RecipeType.FINISHED_DISH);
        recipe.setStatus(RecipeStatus.ACTIVE);
        recipe.setVersion(version);
        recipe.setCode(newCode(restaurant.getId(), item, version));
        recipe.setYieldQuantity(BigDecimal.ONE);
        recipe.setYieldUnit(InventoryUnit.PORTION);
        recipe.setEffectiveFrom(now);
        recipe.setCreatedBy(actorId);
        recipe.setUpdatedBy(actorId);
        return recipe;
    }

    private String newCode(UUID restaurantId, MenuItem item, int version) {
        String code = "DISH_" + item.getId().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT) + "_V" + version;
        while (recipeRepository.findByRestaurant_IdAndCode(restaurantId, code).isPresent()) {
            code = code + "_" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        }
        return code;
    }

    private static void retire(Recipe recipe, UUID actorId, OffsetDateTime now) {
        recipe.setStatus(RecipeStatus.ARCHIVED);
        OffsetDateTime from = recipe.getEffectiveFrom();
        recipe.setRetiredAt(from != null && from.isAfter(now) ? from : now);
        recipe.setUpdatedBy(actorId);
    }

    private Recipe save(Recipe recipe) {
        try {
            return recipeRepository.saveAndFlush(recipe);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("The dish's ingredients couldn't be saved. Refresh and try again.", HttpStatus.CONFLICT);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    // Today's cost from stock prices; the stored figure when a nested preparation can't be costed (a cycle made on
    // the Recipes page).
    private BigDecimal liveCost(Recipe recipe) {
        try {
            return recipeService.costOf(recipe);
        } catch (RuntimeException ex) {
            return recipe.getTheoreticalCost() == null ? BigDecimal.ZERO : recipe.getTheoreticalCost();
        }
    }

    private static DishRecipeResponse response(MenuItem item, Recipe recipe, BigDecimal cost) {
        List<DishIngredientResponse> ingredients = recipe == null ? List.of() : recipe.getComponents().stream()
                .sorted(Comparator.comparingInt(RecipeComponent::getDisplayOrder).thenComparing(RecipeComponent::getComponentNameSnapshot))
                .map(component -> component.getInventoryItem() != null
                        ? new DishIngredientResponse(IngredientKind.STOCK, component.getInventoryItem().getId(),
                                component.getInventoryItem().getName(), component.getQuantity(), component.getUnit())
                        : new DishIngredientResponse(IngredientKind.PREPARATION, component.getChildRecipe().getId(),
                                component.getChildRecipe().getName(), component.getQuantity(), component.getUnit()))
                .toList();
        return new DishRecipeResponse(recipe == null ? null : recipe.getId(), ingredients,
                cost.setScale(2, RoundingMode.HALF_UP), item.getBasePrice());
    }

    private static String dishName(MenuItem item) {
        String name = item.getName();
        return name.length() <= 150 ? name : name.substring(0, 150);
    }

    private static Restaurant restaurantOf(MenuItem item) {
        return item.getSection().getMenu().getRestaurant();
    }
}
