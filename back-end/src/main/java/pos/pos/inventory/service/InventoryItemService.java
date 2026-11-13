package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.inventory.InventoryItemNotFoundException;
import pos.pos.inventory.dto.InventoryItemRequest;
import pos.pos.inventory.dto.InventoryItemResponse;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.entity.InventoryLevel;
import pos.pos.inventory.enums.InventoryItemType;
import pos.pos.inventory.enums.InventoryKindScope;
import pos.pos.inventory.repository.InventoryCountRepository;
import pos.pos.inventory.repository.InventoryLevelRepository;
import pos.pos.inventory.mapper.InventoryItemMapper;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.inventory.repository.InventorySalesSourceRepository;
import pos.pos.recipe.entity.Recipe;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.recipe.repository.RecipeRepository;
import pos.pos.recipe.service.RecipeService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


// Manages the items that a restaurant keeps in stock.
// It can create, update, find, search, list, and deactivate inventory items.
// Before every operation, it checks that the user can access or manage the restaurant.
// It also prevents two active items in the same restaurant from using the same barcode.
@Service
@RequiredArgsConstructor
public class InventoryItemService {

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventorySalesSourceRepository inventorySalesSourceRepository;
    private final InventoryItemMapper inventoryItemMapper;
    private final SupplierService supplierService;
    private final InventoryChangeNotifier inventoryChangeNotifier;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final InventoryCountRepository inventoryCountRepository;
    private final InventoryMovementService inventoryMovementService;
    private final InventoryKindService inventoryKindService;
    private final InventoryItemEvents inventoryItemEvents;


    //Finds the restaurant and the logged in user who is doing the change and then saves these Info and other info through the mapper
    @Transactional
    public InventoryItemResponse createItem(Authentication authentication, UUID restaurantId, InventoryItemRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        assertBarcodeAvailable(restaurantId, request.getBarcode(), null);
        assertCodeAvailable(restaurantId, request.getCode(), null);

        InventoryItem item = new InventoryItem();
        item.setRestaurant(restaurant);
        item.setCreatedBy(actorId);
        item.setUpdatedBy(actorId);
        inventoryItemMapper.applyRequest(item, request);
        item.setKind(item.getItemType() == InventoryItemType.CUSTOM
                ? inventoryKindService.requireOwnKind(restaurantId, InventoryKindScope.ITEM, request.getKindId()) : null);
        supplierService.attachToItem(restaurantId, item, request.getSupplierId(), request.getSupplierName(), actorId);

        InventoryItem saved = saveItem(item);
        supplierService.linkSavedItem(saved);
        inventoryItemEvents.record(saved, "ITEM_CREATED", "Added", actorId);
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
        return inventoryItemMapper.toResponse(saved);
    }



    //Just changes the updated by and does the same things as create item.
    @Transactional
    public InventoryItemResponse updateItem(
            Authentication authentication,
            UUID restaurantId,
            UUID itemId,
            InventoryItemRequest request
    ) {
        //Identifies the current user from and authenticate and check if the user is allowed to manage it
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryItem item = requireItem(restaurantId, itemId);

        if ((Boolean.FALSE.equals(request.getActive()) || Boolean.FALSE.equals(request.getTrackInventory()))
                && inventorySalesSourceRepository.existsByInventoryItem_Id(itemId)) {
            throw new AuthException("Remove this item's sale stock sources before deactivating inventory tracking", HttpStatus.CONFLICT);
        }

        assertBarcodeAvailable(restaurantId, request.getBarcode(), item.getBarcode());
        assertCodeAvailable(restaurantId, request.getCode(), item.getCode());

        // An item made here follows its sub-recipe: its unit is the batch's and its cost comes from the ingredients.
        Recipe madeBy = recipeRepository.findByOutputItem_Id(itemId).orElse(null);
        if (madeBy != null && request.getBaseUnit() != null && request.getBaseUnit() != madeBy.getYieldUnit()) {
            throw new AuthException(item.getName() + " is made here: change how much a batch makes in its sub-recipe", HttpStatus.CONFLICT);
        }

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        InventoryItemEvents.Snapshot before = InventoryItemEvents.Snapshot.of(item);
        item.setUpdatedBy(actorId);
        inventoryItemMapper.applyRequest(item, request);
        item.setKind(item.getItemType() == InventoryItemType.CUSTOM
                ? inventoryKindService.requireOwnKind(restaurantId, InventoryKindScope.ITEM, request.getKindId()) : null);
        supplierService.attachToItem(restaurantId, item, request.getSupplierId(), request.getSupplierName(), actorId);

        InventoryItem saved = saveItem(item);
        supplierService.linkSavedItem(saved);
        String changed = InventoryItemEvents.changes(before, saved);
        if (!changed.isEmpty()) inventoryItemEvents.record(saved, "ITEM_UPDATED", changed, actorId);
        if (madeBy != null) {
            madeBy.setName(saved.getName());
            if (saved.isActive() && madeBy.getStatus() != RecipeStatus.ACTIVE) {
                madeBy.setStatus(RecipeStatus.ACTIVE);
                madeBy.setRetiredAt(null);
            } else if (!saved.isActive() && madeBy.getStatus() == RecipeStatus.ACTIVE) {
                madeBy.setStatus(RecipeStatus.ARCHIVED);
                madeBy.setRetiredAt(OffsetDateTime.now(ZoneOffset.UTC));
            }
            madeBy.setUpdatedBy(actorId);
            recipeService.syncOutputItem(recipeRepository.saveAndFlush(madeBy), actorId);
        }
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
        return withRecipe(saved);
    }


    //returns the item after checking if current use allowed to manage
    @Transactional(readOnly = true)
    public InventoryItemResponse getItem(Authentication authentication, UUID restaurantId, UUID itemId) {
        //Check if allowed to manage
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return withRecipe(requireItem(restaurantId, itemId));
    }



    //returns the scanned item after authorising the user,
    @Transactional(readOnly = true)
    public InventoryItemResponse getItemByBarcode(Authentication authentication, UUID restaurantId, String barcode) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        String normalizedBarcode = NormalizationUtils.normalizeUpper(barcode);
        return withRecipe(
                //If the Optional contains an item, return that InventoryItem.
                //If it is empty, throw InventoryItemNotFoundException.
                inventoryItemRepository.findByRestaurant_IdAndBarcodeAndDeletedAtIsNull(restaurantId, normalizedBarcode)
                        .orElseThrow(InventoryItemNotFoundException::new)
        );
    }


    //Returns the found result after taking it from the database from repository method
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> searchItems(Authentication authentication, UUID restaurantId, String keyword) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        String safeKeyword = keyword == null ? "" : keyword;
        return withRecipes(restaurantId, inventoryItemRepository
                .findAllByRestaurant_IdAndNameContainingIgnoreCaseAndDeletedAtIsNullOrderByNameAsc(restaurantId, safeKeyword));
    }


    //Returns the function from Repostiory
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listActiveItems(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        return withRecipes(restaurantId, inventoryItemRepository.findAllByRestaurant_IdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(restaurantId));
    }


    //Same as listActiveItems, but for items that have been deactivated (active = false), not soft-deleted
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listInactiveItems(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        return withRecipes(restaurantId, inventoryItemRepository.findAllByRestaurant_IdAndActiveFalseAndDeletedAtIsNullOrderByNameAsc(restaurantId));
    }


    //Same as listActiveItems, but returns everything not soft-deleted regardless of the active flag
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listAllItems(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        return withRecipes(restaurantId, inventoryItemRepository.findAllByRestaurant_IdAndDeletedAtIsNullOrderByNameAsc(restaurantId));
    }


    //Gets the item from repository method and changes the setter and updated by, after that calls the saveItem method
    @Transactional
    public void deactivateItem(Authentication authentication, UUID restaurantId, UUID itemId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryItem item = requireItem(restaurantId, itemId);

        // Already inactive -- nothing to change, skip the redundant write instead of
        // re-saving identical state every time this gets called on the same item.
        if (!item.isActive()) {
            return;
        }

        if (inventorySalesSourceRepository.existsByInventoryItem_Id(itemId)) {
            throw new AuthException("Remove this item's sale stock sources before deactivating it", HttpStatus.CONFLICT);
        }

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        item.setActive(false);
        item.setUpdatedBy(actorId);
        saveItem(item);
        inventoryItemEvents.record(item, "ITEM_UPDATED", "Not in use", actorId);
        // Switching off an item made here switches off its sub-recipe too, so dishes can't pick it any more.
        recipeRepository.findByOutputItem_Id(itemId).filter(recipe -> recipe.getStatus() == RecipeStatus.ACTIVE).ifPresent(recipe -> {
            recipe.setStatus(RecipeStatus.ARCHIVED);
            recipe.setRetiredAt(OffsetDateTime.now(ZoneOffset.UTC));
            recipe.setUpdatedBy(actorId);
            recipeRepository.saveAndFlush(recipe);
        });
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
    }


    /**
     * Deletes an item for good (Inventory → Items → Delete). Refused while a recipe still uses it (or its sub-recipe) or a
     * count that isn't approved or cancelled holds it. What's left in stock is taken out with an "Item deleted" correction,
     * so History and the stock value add up; its stock lines and sale places go, its own sub-recipe is archived, and its
     * code is freed for a new item. The row stays (soft delete), so past movements, counts and orders keep their name.
     */
    @Transactional
    public void deleteItem(Authentication authentication, UUID restaurantId, UUID itemId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryItem item = requireItem(restaurantId, itemId);

        List<String> recipes = recipeRepository.namesOfLiveRecipesUsingItem(itemId);
        if (!recipes.isEmpty()) {
            throw new AuthException(item.getName() + " is used in " + nameList(recipes) + ". Take it out of "
                    + (recipes.size() == 1 ? "that recipe" : "those recipes") + " first.", HttpStatus.CONFLICT);
        }
        Recipe own = recipeRepository.findByOutputItem_Id(itemId).filter(recipe -> recipe.getStatus() != RecipeStatus.ARCHIVED).orElse(null);
        if (own != null) {
            List<String> users = recipeRepository.namesOfLiveRecipesUsingRecipe(own.getId());
            if (!users.isEmpty()) {
                throw new AuthException(item.getName() + " is used in " + nameList(users) + ". Take it out of "
                        + (users.size() == 1 ? "that recipe" : "those recipes") + " first.", HttpStatus.CONFLICT);
            }
        }
        if (inventoryCountRepository.itemInOpenCount(itemId)) {
            throw new AuthException(item.getName() + " is in a count that isn't approved yet. Approve or cancel that count first.",
                    HttpStatus.CONFLICT);
        }

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        inventoryItemEvents.record(item, "ITEM_DELETED", "Deleted", actorId);
        for (InventoryLevel level : inventoryLevelRepository.findAllByInventoryItem_IdOrderByLocation_NameAsc(itemId)) {
            if (level.getOnHandQuantity() != null && level.getOnHandQuantity().signum() > 0) {
                inventoryMovementService.writeOffForDeletion(restaurantId, level.getLocation(), item, level.getOnHandQuantity(), actorId);
            }
        }
        inventoryLevelRepository.deleteAllForItem(itemId);
        inventorySalesSourceRepository.deleteAllByInventoryItem_Id(itemId);
        if (own != null) {
            own.setStatus(RecipeStatus.ARCHIVED);
            own.setRetiredAt(OffsetDateTime.now(ZoneOffset.UTC));
            own.setUpdatedBy(actorId);
            recipeRepository.saveAndFlush(own);
        }

        item.setActive(false);
        item.setCode(freedCode(item.getCode()));
        item.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        item.setUpdatedBy(actorId);
        saveItem(item);
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
    }

    // "Margherita", "Margherita and Tomato sauce", "Margherita, Tomato sauce and 3 more".
    private static String nameList(List<String> names) {
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " and " + names.get(1);
        if (names.size() == 3) return names.get(0) + ", " + names.get(1) + " and " + names.get(2);
        return names.get(0) + ", " + names.get(1) + " and " + (names.size() - 2) + " more";
    }

    // Codes stay unique per restaurant even for deleted rows, so the deleted item's code gets a tail and a new item can take it.
    private static String freedCode(String code) {
        String tail = "~DEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        return (code.length() + tail.length() > 80 ? code.substring(0, 80 - tail.length()) : code) + tail;
    }

    // The item as the app gets it, with the sub-recipe that makes it when it's made here.
    private InventoryItemResponse withRecipe(InventoryItem item) {
        InventoryItemResponse response = inventoryItemMapper.toResponse(item);
        recipeRepository.findByOutputItem_Id(item.getId()).ifPresent(recipe -> response.setRecipeId(recipe.getId()));
        return response;
    }

    private List<InventoryItemResponse> withRecipes(UUID restaurantId, List<InventoryItem> items) {
        Map<UUID, UUID> recipeByItem = new HashMap<>();
        for (Object[] link : recipeRepository.outputItemLinks(restaurantId)) {
            recipeByItem.put((UUID) link[0], (UUID) link[1]);
        }
        return items.stream().map(item -> {
            InventoryItemResponse response = inventoryItemMapper.toResponse(item);
            response.setRecipeId(recipeByItem.get(item.getId()));
            return response;
        }).toList();
    }

    //Uses the repository Method to get the Item by Id and Restaurant, method optional datatype
    private InventoryItem requireItem(UUID restaurantId, UUID itemId) {
        return inventoryItemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(itemId, restaurantId)
                .orElseThrow(InventoryItemNotFoundException::new);
    }

    // If barcode is already present, throw exception. Brings as argument the existing one(if there is one),
    // and the one existing if it is already being modified. if it is new it only checks on database
    private void assertBarcodeAvailable(UUID restaurantId, String rawBarcode, String existingBarcode) {
        String normalizedBarcode = NormalizationUtils.normalizeUpper(rawBarcode);
        if (normalizedBarcode == null || normalizedBarcode.equals(existingBarcode)) {
            return;
        }

        //findByRestaurant_IdAndBarcodeAndDeletedAtIsNull(...) returns only if found or if not found, thats why the method isPresent
        //The repository method returns an Optional type: Optional.of(foundInventoryItem) or Optional.empty()
        inventoryItemRepository.findByRestaurant_IdAndBarcodeAndDeletedAtIsNull(restaurantId, normalizedBarcode)
                .ifPresent(existing -> {
                    throw new AuthException(
                            "This barcode is already registered to " + existing.getName(),
                            HttpStatus.CONFLICT
                    );
                });
    }

    // Same idea as assertBarcodeAvailable, but for `code`. Without this, a duplicate code only
    // ever got caught by the database's uk_inventory_items_restaurant_code constraint, surfacing
    // as a generic "violates a data constraint" error instead of a clear message naming the
    // conflicting item. Mirrors InventoryLocationService.assertCodeAvailable.
    private void assertCodeAvailable(UUID restaurantId, String rawCode, String existingCode) {
        String normalizedCode = NormalizationUtils.normalizeCode(rawCode, 80);
        if (normalizedCode == null || normalizedCode.equals(existingCode)) {
            return;
        }

        inventoryItemRepository.findByRestaurant_IdAndCode(restaurantId, normalizedCode)
                .ifPresent(existing -> {
                    throw new AuthException(
                            "This code is already used by " + existing.getName() + " in this restaurant",
                            HttpStatus.CONFLICT
                    );
                });
    }



    private String integrityConstraintName(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }

    //
    private InventoryItem saveItem(InventoryItem item) {
        try {
            //Used save and flush becouse it executes the SQL task directly in this method
            //If it was just save it could be delayed, other transactions could happen and there would come to mistakes
            return inventoryItemRepository.saveAndFlush(item);
            //Item violated Spring Database rules
        } catch (DataIntegrityViolationException ex) {
            String constraint = integrityConstraintName(ex);
            if ("uk_inventory_items_restaurant_code".equals(constraint)) {
                throw new AuthException("This code is already used in this restaurant", HttpStatus.CONFLICT);
            }
            if ("uk_inventory_items_restaurant_barcode_active".equals(constraint)) {
                throw new AuthException("This barcode is already registered in this restaurant", HttpStatus.CONFLICT);
            }
            throw new AuthException("Inventory item update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
