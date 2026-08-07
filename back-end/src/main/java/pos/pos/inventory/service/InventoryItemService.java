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

// This service holds all the business logic for InventoryItem: creating, updating,
// reading, searching, and deactivating catalog items (the "what do we stock" table).
// It never talks to the database directly — it always goes through the repository.
// It never trusts the request blindly — every write checks restaurant access first
// (via RestaurantScopeService) and checks barcode uniqueness before saving.
// The controller only handles HTTP concerns; this class is where the actual decisions happen.

@Service
@RequiredArgsConstructor
public class InventoryItemService {

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryItemMapper inventoryItemMapper;


    //Take the item from Request and mapps the givven arguments for it
    @Transactional
    public InventoryItemResponse createItem(Authentication authentication, UUID restaurantId, InventoryItemRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        //checks the bar code
        assertBarcodeAvailable(restaurantId, request.getBarcode(), null);

        InventoryItem item = new InventoryItem();
        item.setRestaurant(restaurant);
        item.setCreatedBy(actorId);
        item.setUpdatedBy(actorId);
        inventoryItemMapper.applyRequest(item, request);

        return inventoryItemMapper.toResponse(saveItem(item));
    }



    //Updates an existing inventory Item and rechecks some information
    @Transactional
    public InventoryItemResponse updateItem(
            Authentication authentication,
            UUID restaurantId,
            UUID itemId,
            InventoryItemRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryItem item = requireItem(restaurantId, itemId);

        assertBarcodeAvailable(restaurantId, request.getBarcode(), item.getBarcode());

        item.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        inventoryItemMapper.applyRequest(item, request);

        return inventoryItemMapper.toResponse(saveItem(item));
    }


    //returns the item
    @Transactional(readOnly = true)
    public InventoryItemResponse getItem(Authentication authentication, UUID restaurantId, UUID itemId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return inventoryItemMapper.toResponse(requireItem(restaurantId, itemId));
    }



    //returns the scanned item
    @Transactional(readOnly = true)
    public InventoryItemResponse getItemByBarcode(Authentication authentication, UUID restaurantId, String barcode) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        String normalizedBarcode = NormalizationUtils.normalizeUpper(barcode);
        return inventoryItemMapper.toResponse(
                inventoryItemRepository.findByRestaurant_IdAndBarcodeAndDeletedAtIsNull(restaurantId, normalizedBarcode)
                        .orElseThrow(InventoryItemNotFoundException::new)
        );
    }

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



    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listActiveItems(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        return inventoryItemRepository.findAllByRestaurant_IdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(restaurantId)
                .stream()
                .map(inventoryItemMapper::toResponse)
                .toList();
    }


    @Transactional
    public void deactivateItem(Authentication authentication, UUID restaurantId, UUID itemId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryItem item = requireItem(restaurantId, itemId);

        item.setActive(false);
        item.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        saveItem(item);
    }



    private InventoryItem requireItem(UUID restaurantId, UUID itemId) {
        return inventoryItemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(itemId, restaurantId)
                .orElseThrow(InventoryItemNotFoundException::new);
    }

    private void assertBarcodeAvailable(UUID restaurantId, String rawBarcode, String existingBarcode) {
        String normalizedBarcode = NormalizationUtils.normalizeUpper(rawBarcode);
        if (normalizedBarcode == null || normalizedBarcode.equals(existingBarcode)) {
            return;
        }

        inventoryItemRepository.findByRestaurant_IdAndBarcodeAndDeletedAtIsNull(restaurantId, normalizedBarcode)
                .ifPresent(existing -> {
                    throw new AuthException(
                            "This barcode is already registered to " + existing.getName(),
                            HttpStatus.CONFLICT
                    );
                });
    }

    //Item saved
    private InventoryItem saveItem(InventoryItem item) {
        try {
            return inventoryItemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Inventory item update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
