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
import pos.pos.inventory.mapper.InventoryItemMapper;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
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
    private final InventoryItemMapper inventoryItemMapper;


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

        return inventoryItemMapper.toResponse(saveItem(item));
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

        assertBarcodeAvailable(restaurantId, request.getBarcode(), item.getBarcode());
        assertCodeAvailable(restaurantId, request.getCode(), item.getCode());

        item.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        inventoryItemMapper.applyRequest(item, request);

        return inventoryItemMapper.toResponse(saveItem(item));
    }


    //returns the item after checking if current use allowed to manage
    @Transactional(readOnly = true)
    public InventoryItemResponse getItem(Authentication authentication, UUID restaurantId, UUID itemId) {
        //Check if allowed to manage
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return inventoryItemMapper.toResponse(requireItem(restaurantId, itemId));
    }



    //returns the scanned item after authorising the user,
    @Transactional(readOnly = true)
    public InventoryItemResponse getItemByBarcode(Authentication authentication, UUID restaurantId, String barcode) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        String normalizedBarcode = NormalizationUtils.normalizeUpper(barcode);
        return inventoryItemMapper.toResponse(
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
        return inventoryItemRepository
                .findAllByRestaurant_IdAndNameContainingIgnoreCaseAndDeletedAtIsNullOrderByNameAsc(restaurantId, safeKeyword)
                .stream()
                .map(inventoryItemMapper::toResponse)
                .toList();
    }


    //Returns the function from Repostiory
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listActiveItems(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        return inventoryItemRepository.findAllByRestaurant_IdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(restaurantId)
                .stream()
                .map(inventoryItemMapper::toResponse)
                .toList();
    }


    //Same as listActiveItems, but for items that have been deactivated (active = false), not soft-deleted
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listInactiveItems(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        return inventoryItemRepository.findAllByRestaurant_IdAndActiveFalseAndDeletedAtIsNullOrderByNameAsc(restaurantId)
                .stream()
                .map(inventoryItemMapper::toResponse)
                .toList();
    }


    //Same as listActiveItems, but returns everything not soft-deleted regardless of the active flag
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listAllItems(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        return inventoryItemRepository.findAllByRestaurant_IdAndDeletedAtIsNullOrderByNameAsc(restaurantId)
                .stream()
                .map(inventoryItemMapper::toResponse)
                .toList();
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

        item.setActive(false);
        item.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        saveItem(item);
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

        inventoryItemRepository.findByRestaurant_IdAndCodeAndDeletedAtIsNull(restaurantId, normalizedCode)
                .ifPresent(existing -> {
                    throw new AuthException(
                            "This code is already used by " + existing.getName() + " in this restaurant",
                            HttpStatus.CONFLICT
                    );
                });
    }



    //
    private InventoryItem saveItem(InventoryItem item) {
        try {
            //Used save and flush becouse it executes the SQL task directly in this method
            //If it was just save it could be delayed, other transactions could happen and there would come to mistakes
            return inventoryItemRepository.saveAndFlush(item);
            //Item violated Spring Database rules
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Inventory item update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
