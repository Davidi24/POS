package pos.pos.recipe.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.recipe.RecipeNotFoundException;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.enums.InventoryItemType;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.inventory.repository.InventoryLevelRepository;
import pos.pos.inventory.service.InventoryChangeNotifier;
import pos.pos.inventory.service.InventoryUnitConversion;
import pos.pos.menu.recipe.DishRecipeDtos.IngredientChoiceResponse;
import pos.pos.menu.recipe.DishRecipeDtos.IngredientKind;
import pos.pos.menu.recipe.DishRecipeDtos.UnitCost;
import pos.pos.recipe.dto.SubRecipeDtos.SubRecipeIngredientRequest;
import pos.pos.recipe.dto.SubRecipeDtos.SubRecipeIngredientResponse;
import pos.pos.recipe.dto.SubRecipeDtos.SubRecipeRequest;
import pos.pos.recipe.dto.SubRecipeDtos.SubRecipeResponse;
import pos.pos.recipe.entity.Recipe;
import pos.pos.recipe.entity.RecipeComponent;
import pos.pos.recipe.enums.RecipeComponentType;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.recipe.enums.RecipeType;
import pos.pos.recipe.repository.RecipeRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Sub-recipes, edited in Inventory → Items: things the kitchen makes from what it has (a sauce, a dough). Each is an
 * item (its name, kind, unit and cost per unit, so it sits with the bought items) linked to its recipe (what one batch
 * makes and what goes in). Both are saved together, like a dish's ingredients in the dish editor. The older "prep
 * batch" and "sub-recipe" recipe types are the same thing here; new ones are SUB_RECIPE.
 */
@Service
@RequiredArgsConstructor
public class SubRecipeService {

    /** The recipe types that are sub-recipes (both behave the same everywhere). */
    public static final Set<RecipeType> SUB_RECIPE_TYPES = EnumSet.of(RecipeType.PREP_BATCH, RecipeType.SUB_RECIPE);
    private static final int MAX_CHOICES = 500;
    private static final int COST_SCALE = 6;

    private final RestaurantScopeService restaurantScopeService;
    private final RecipeRepository recipeRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final RecipeService recipeService;
    private final InventoryChangeNotifier inventoryChangeNotifier;

    @Transactional
    public SubRecipeResponse get(Authentication authentication, UUID restaurantId, UUID recipeId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return response(requireSubRecipe(restaurantId, recipeId));
    }

    /** What a sub-recipe can be made with; [forRecipeId] (the one being edited) and what already contains it are left out. */
    @Transactional(readOnly = true)
    public List<IngredientChoiceResponse> choices(Authentication authentication, UUID restaurantId, UUID forRecipeId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return choiceList(restaurantId, null, forRecipeId);
    }

    /**
     * What a dish or a sub-recipe can be made with: active stock items (not the ones made here, which their sub-recipe
     * stands for) and sub-recipes in use, by name, each with what one of each unit it can be measured in costs.
     */
    @Transactional(readOnly = true)
    public List<IngredientChoiceResponse> choiceList(UUID restaurantId, String search, UUID forRecipeId) {
        String query = NormalizationUtils.normalizeLower(search);
        Set<UUID> madeHere = madeHereItemIds(restaurantId);
        List<Recipe> subRecipes = recipeRepository.findAllByRestaurant_IdAndStatusAndRecipeTypeInOrderByNameAsc(
                restaurantId, RecipeStatus.ACTIVE, SUB_RECIPE_TYPES);
        Set<UUID> notAllowed = forRecipeId == null ? Set.of() : containing(forRecipeId, subRecipes);

        Stream<IngredientChoiceResponse> stock = inventoryItemRepository
                .findAllByRestaurant_IdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(restaurantId).stream()
                .filter(item -> !madeHere.contains(item.getId()) && matches(item.getName(), query))
                .map(item -> new IngredientChoiceResponse(
                        IngredientKind.STOCK,
                        item.getId(),
                        item.getName(),
                        item.getItemType().name(),
                        item.getBaseUnit(),
                        unitCosts(item.getBaseUnit(), item.getCostPerUnit() == null ? BigDecimal.ZERO : item.getCostPerUnit()),
                        item.getImageUrl()
                ));
        Stream<IngredientChoiceResponse> made = subRecipes.stream()
                .filter(recipe -> !notAllowed.contains(recipe.getId()) && matches(recipe.getName(), query))
                .map(recipe -> new IngredientChoiceResponse(
                        IngredientKind.PREPARATION,
                        recipe.getId(),
                        recipe.getName(),
                        "PREPARATION",
                        recipe.getYieldUnit(),
                        unitCosts(recipe.getYieldUnit(), liveCost(recipe).divide(recipe.getYieldQuantity(), COST_SCALE, RoundingMode.HALF_UP)),
                        pictureOf(recipe)
                ));
        return Stream.concat(made, stock).limit(MAX_CHOICES).toList();
    }

    /** A new sub-recipe: its item and its recipe with every ingredient, in one go. */
    @Transactional
    public SubRecipeResponse create(Authentication authentication, UUID restaurantId, SubRecipeRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        List<Wanted> wanted = resolve(restaurantId, null, request.ingredients());
        boolean active = request.active() == null || request.active();

        InventoryItem item = new InventoryItem();
        item.setRestaurant(restaurant);
        item.setCode(newItemCode(restaurantId, request.name()));
        item.setName(request.name());
        item.setDescription(request.description());
        item.setItemType(request.itemType());
        item.setBaseUnit(request.yieldUnit());
        // Not counted itself: serving something made with it takes its ingredients from stock.
        item.setTrackInventory(false);
        item.setActive(active);
        item.setCreatedBy(actorId);
        item.setUpdatedBy(actorId);
        item = saveItem(item);

        Recipe recipe = new Recipe();
        recipe.setRestaurant(restaurant);
        recipe.setRecipeType(RecipeType.SUB_RECIPE);
        recipe.setStatus(active ? RecipeStatus.ACTIVE : RecipeStatus.ARCHIVED);
        recipe.setVersion(1);
        recipe.setCode(newRecipeCode(restaurantId, item.getId()));
        recipe.setEffectiveFrom(OffsetDateTime.now(ZoneOffset.UTC));
        recipe.setOutputItem(item);
        recipe.setCreatedBy(actorId);
        apply(recipe, request, actorId);
        replaceComponents(recipe, wanted);
        Recipe saved = finish(recipe, actorId);
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
        return response(saved);
    }

    /** Changes a sub-recipe as a whole: its item's name and kind, the batch, the ingredients, and whether it's in use. */
    @Transactional
    public SubRecipeResponse update(Authentication authentication, UUID restaurantId, UUID recipeId, SubRecipeRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Recipe recipe = requireSubRecipe(restaurantId, recipeId);
        List<Wanted> wanted = resolve(restaurantId, recipe, request.ingredients());

        InventoryItem item = recipe.getOutputItem();
        if (!InventoryUnitConversion.canConvert(item.getBaseUnit(), request.yieldUnit())
                && inventoryLevelRepository.findAllByInventoryItem_IdOrderByLocation_NameAsc(item.getId()).stream()
                .anyMatch(level -> level.getOnHandQuantity() != null && level.getOnHandQuantity().signum() != 0)) {
            throw new AuthException(item.getName() + " has stock counted in " + item.getBaseUnit().name().toLowerCase(Locale.ROOT)
                    + ", so a batch can't be measured in " + request.yieldUnit().name().toLowerCase(Locale.ROOT) + ".", HttpStatus.CONFLICT);
        }
        item.setItemType(request.itemType());
        item.setDescription(request.description());
        if (request.active() != null) {
            boolean active = request.active();
            if (active && recipe.getStatus() != RecipeStatus.ACTIVE) {
                recipe.setStatus(RecipeStatus.ACTIVE);
                recipe.setRetiredAt(null);
            } else if (!active && recipe.getStatus() == RecipeStatus.ACTIVE) {
                recipe.setStatus(RecipeStatus.ARCHIVED);
                recipe.setRetiredAt(OffsetDateTime.now(ZoneOffset.UTC));
            }
        }
        apply(recipe, request, actorId);
        replaceComponents(recipe, wanted);
        Recipe saved = finish(recipe, actorId);
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
        return response(saved);
    }

    private void apply(Recipe recipe, SubRecipeRequest request, UUID actorId) {
        recipe.setName(request.name());
        recipe.setDescription(request.description());
        recipe.setInstructions(request.instructions());
        recipe.setYieldQuantity(request.yieldQuantity());
        recipe.setYieldUnit(request.yieldUnit());
        recipe.setUpdatedBy(actorId);
    }

    // Prices the batch from today's stock costs, saves the recipe and brings its item in step (name, unit, cost, on/off).
    private Recipe finish(Recipe recipe, UUID actorId) {
        recipe.setTheoreticalCost(recipeService.costOf(recipe).setScale(2, RoundingMode.HALF_UP));
        Recipe saved = save(recipe);
        recipeService.syncOutputItem(saved, actorId);
        return saved;
    }

    private record Wanted(IngredientKind kind, InventoryItem stockItem, Recipe subRecipe, BigDecimal quantity, InventoryUnit unit) {
        String key() {
            return kind + ":" + (stockItem != null ? stockItem.getId() : subRecipe.getId());
        }

        String name() {
            return stockItem != null ? stockItem.getName() : subRecipe.getName();
        }
    }

    // Checks every ingredient: a stock item in use or a sub-recipe in use, measured in a unit that fits it, listed once,
    // and never [self] or something that already contains it. A stock item made here stands for its sub-recipe.
    private List<Wanted> resolve(UUID restaurantId, Recipe self, List<SubRecipeIngredientRequest> ingredients) {
        List<Wanted> wanted = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SubRecipeIngredientRequest ingredient : ingredients) {
            if ((ingredient.inventoryItemId() == null) == (ingredient.subRecipeId() == null)) {
                throw new AuthException("Choose a stock item or a sub-recipe for each ingredient", HttpStatus.BAD_REQUEST);
            }
            Recipe subRecipe = null;
            InventoryItem stockItem = null;
            if (ingredient.inventoryItemId() != null) {
                stockItem = inventoryItemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(ingredient.inventoryItemId(), restaurantId)
                        .orElseThrow(() -> new AuthException("One of the ingredients is no longer a stock item. Refresh and try again.",
                                HttpStatus.CONFLICT));
                subRecipe = recipeRepository.findByOutputItem_Id(stockItem.getId()).orElse(null);
                if (subRecipe != null) {
                    stockItem = null;
                } else if (!stockItem.isActive()) {
                    throw new AuthException(stockItem.getName() + " is switched off. Switch it on or choose another item.", HttpStatus.CONFLICT);
                }
            } else {
                subRecipe = recipeRepository.findByIdAndRestaurant_Id(ingredient.subRecipeId(), restaurantId)
                        .filter(found -> SUB_RECIPE_TYPES.contains(found.getRecipeType()))
                        .orElseThrow(() -> new AuthException("One of the sub-recipes no longer exists. Refresh and try again.",
                                HttpStatus.CONFLICT));
            }
            Wanted next;
            if (subRecipe != null) {
                if (self != null && Objects.equals(subRecipe.getId(), self.getId())) {
                    throw new AuthException(self.getName() + " can't be made from itself.", HttpStatus.BAD_REQUEST);
                }
                if (subRecipe.getStatus() != RecipeStatus.ACTIVE) {
                    throw new AuthException(subRecipe.getName() + " is switched off. Switch it on or choose another one.", HttpStatus.CONFLICT);
                }
                if (self != null) {
                    recipeService.assertNotInside(self, subRecipe);
                }
                requireUnit(subRecipe.getName(), ingredient.unit(), subRecipe.getYieldUnit());
                next = new Wanted(IngredientKind.PREPARATION, null, subRecipe, ingredient.quantity(), ingredient.unit());
            } else {
                requireUnit(stockItem.getName(), ingredient.unit(), stockItem.getBaseUnit());
                next = new Wanted(IngredientKind.STOCK, stockItem, null, ingredient.quantity(), ingredient.unit());
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

    // Updates the rows that stay (they may carry a waste % or a note), adds new ones and drops the ones no longer listed.
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
            component.setChildRecipe(next.subRecipe());
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

    // A sub-recipe of the restaurant; one made before items were linked gets its item now.
    private Recipe requireSubRecipe(UUID restaurantId, UUID recipeId) {
        Recipe recipe = recipeRepository.findByIdAndRestaurant_Id(recipeId, restaurantId)
                .filter(found -> SUB_RECIPE_TYPES.contains(found.getRecipeType()))
                .orElseThrow(RecipeNotFoundException::new);
        if (recipe.getOutputItem() == null) {
            InventoryItem item = new InventoryItem();
            item.setRestaurant(recipe.getRestaurant());
            item.setCode(newItemCode(restaurantId, recipe.getName()));
            item.setName(recipe.getName());
            item.setDescription(recipe.getDescription());
            item.setItemType(InventoryItemType.PREPARED_COMPONENT);
            item.setBaseUnit(recipe.getYieldUnit());
            item.setTrackInventory(false);
            item.setActive(recipe.getStatus() == RecipeStatus.ACTIVE);
            item.setCreatedBy(recipe.getCreatedBy());
            item.setUpdatedBy(recipe.getUpdatedBy());
            recipe.setOutputItem(saveItem(item));
            recipe = save(recipe);
            recipeService.syncOutputItem(recipe, recipe.getUpdatedBy());
        }
        return recipe;
    }

    // The item's code from its name (TOMATO_SAUCE), with a number added when that's taken.
    private String newItemCode(UUID restaurantId, String name) {
        String base = NormalizationUtils.normalizeCode(name, 72);
        if (base == null) {
            base = "MADE";
        }
        String code = base;
        for (int n = 2; inventoryItemRepository.findByRestaurant_IdAndCode(restaurantId, code).isPresent(); n++) {
            code = base + "_" + n;
        }
        return code;
    }

    private String newRecipeCode(UUID restaurantId, UUID itemId) {
        String code = "SUB_" + itemId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        while (recipeRepository.findByRestaurant_IdAndCode(restaurantId, code).isPresent()) {
            code = code + "_" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        }
        return code;
    }

    private Set<UUID> madeHereItemIds(UUID restaurantId) {
        Set<UUID> ids = new HashSet<>();
        for (Object[] link : recipeRepository.outputItemLinks(restaurantId)) {
            ids.add((UUID) link[0]);
        }
        return ids;
    }

    // [recipeId] and every sub-recipe that has it somewhere inside: none of them can go into [recipeId].
    private static Set<UUID> containing(UUID recipeId, List<Recipe> subRecipes) {
        Map<UUID, Set<UUID>> usedIn = new HashMap<>();
        for (Recipe recipe : subRecipes) {
            for (RecipeComponent component : recipe.getComponents()) {
                if (component.getChildRecipe() != null) {
                    usedIn.computeIfAbsent(component.getChildRecipe().getId(), id -> new HashSet<>()).add(recipe.getId());
                }
            }
        }
        Set<UUID> result = new HashSet<>();
        Deque<UUID> pending = new ArrayDeque<>();
        pending.push(recipeId);
        while (!pending.isEmpty()) {
            UUID current = pending.pop();
            if (result.add(current)) {
                usedIn.getOrDefault(current, Set.of()).forEach(pending::push);
            }
        }
        return result;
    }

    private SubRecipeResponse response(Recipe recipe) {
        InventoryItem item = recipe.getOutputItem();
        BigDecimal batchCost = liveCost(recipe);
        List<SubRecipeIngredientResponse> ingredients = recipe.getComponents().stream()
                .sorted(Comparator.comparingInt(RecipeComponent::getDisplayOrder).thenComparing(RecipeComponent::getComponentNameSnapshot))
                .map(component -> component.getInventoryItem() != null
                        ? new SubRecipeIngredientResponse(IngredientKind.STOCK, component.getInventoryItem().getId(),
                                component.getInventoryItem().getName(), component.getQuantity(), component.getUnit(),
                                lineCost(component), component.getInventoryItem().getImageUrl())
                        : new SubRecipeIngredientResponse(IngredientKind.PREPARATION, component.getChildRecipe().getId(),
                                component.getChildRecipe().getName(), component.getQuantity(), component.getUnit(),
                                lineCost(component), pictureOf(component.getChildRecipe())))
                .toList();
        return new SubRecipeResponse(
                recipe.getId(),
                item == null ? null : item.getId(),
                recipe.getName(),
                item == null ? InventoryItemType.PREPARED_COMPONENT : item.getItemType(),
                recipe.getYieldQuantity(),
                recipe.getYieldUnit(),
                ingredients,
                batchCost.setScale(2, RoundingMode.HALF_UP),
                batchCost.divide(recipe.getYieldQuantity(), 4, RoundingMode.HALF_UP),
                pictureOf(recipe),
                recipe.getDescription(),
                recipe.getInstructions(),
                recipe.getStatus() == RecipeStatus.ACTIVE
        );
    }

    // What one ingredient line costs from today's prices (its waste included); null when it can't be worked out.
    private BigDecimal lineCost(RecipeComponent component) {
        try {
            BigDecimal loss = component.getYieldLossPercent() == null ? BigDecimal.ZERO : component.getYieldLossPercent();
            BigDecimal kept = BigDecimal.ONE.subtract(loss.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
            BigDecimal used = component.getQuantity().divide(kept, 6, RoundingMode.HALF_UP);
            BigDecimal cost;
            if (component.getInventoryItem() != null) {
                InventoryItem item = component.getInventoryItem();
                BigDecimal unitCost = item.getCostPerUnit() == null ? BigDecimal.ZERO : item.getCostPerUnit();
                cost = InventoryUnitConversion.convert(used, component.getUnit(), item.getBaseUnit()).multiply(unitCost);
            } else {
                Recipe child = component.getChildRecipe();
                BigDecimal inYield = InventoryUnitConversion.convert(used, component.getUnit(), child.getYieldUnit());
                cost = inYield.divide(child.getYieldQuantity(), 9, RoundingMode.HALF_UP).multiply(liveCost(child));
            }
            return cost.setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // The picture people know it by: its item's photo, else the recipe's own.
    private static String pictureOf(Recipe recipe) {
        if (recipe.getOutputItem() != null && recipe.getOutputItem().getImageUrl() != null) {
            return recipe.getOutputItem().getImageUrl();
        }
        return recipe.getImageUrl();
    }

    // Today's cost from stock prices; the stored figure when a nested sub-recipe can't be costed.
    private BigDecimal liveCost(Recipe recipe) {
        try {
            return recipeService.costOf(recipe);
        } catch (RuntimeException ex) {
            return recipe.getTheoreticalCost() == null ? BigDecimal.ZERO : recipe.getTheoreticalCost();
        }
    }

    // What one of each unit [countedIn] can be measured in costs, given [costPerCountedUnit].
    private static List<UnitCost> unitCosts(InventoryUnit countedIn, BigDecimal costPerCountedUnit) {
        return Arrays.stream(InventoryUnit.values())
                .filter(unit -> InventoryUnitConversion.canConvert(unit, countedIn))
                .map(unit -> new UnitCost(unit, InventoryUnitConversion.convert(BigDecimal.ONE, unit, countedIn)
                        .multiply(costPerCountedUnit).setScale(COST_SCALE, RoundingMode.HALF_UP)))
                .toList();
    }

    private static boolean matches(String name, String query) {
        return query == null || (name != null && name.toLowerCase(Locale.ROOT).contains(query));
    }

    private InventoryItem saveItem(InventoryItem item) {
        try {
            return inventoryItemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("The sub-recipe's item couldn't be saved. Refresh and try again.", HttpStatus.CONFLICT);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private Recipe save(Recipe recipe) {
        try {
            return recipeRepository.saveAndFlush(recipe);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("The sub-recipe couldn't be saved. Refresh and try again.", HttpStatus.CONFLICT);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
