package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.inventory.InventoryItemNotFoundException;
import pos.pos.exception.inventory.InventoryLevelNotFoundException;
import pos.pos.exception.inventory.InventoryLocationNotFoundException;
import pos.pos.inventory.dto.InventoryLevelResponse;
import pos.pos.inventory.dto.InventoryLevelTotalResponse;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.entity.InventoryLevel;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.inventory.mapper.InventoryLevelMapper;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.inventory.repository.InventoryLevelRepository;
import pos.pos.inventory.repository.InventoryLocationRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// Manages the current stock quantity of each item at each inventory location.
// It allows users to view stock levels and find items that are low in stock.
// Stock quantities are not changed directly by an endpoint.
// They are updated internally when an inventory movement or approved count changes the stock.
@Service
@RequiredArgsConstructor
public class InventoryLevelService {

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final InventoryLevelMapper inventoryLevelMapper;
    private final InventoryLocationRepository inventoryLocationRepository;
    private final InventoryItemRepository inventoryItemRepository;


    //Checks the Access of the user to the restaurant and
    @Transactional(readOnly = true)
    public InventoryLevelResponse getLevel(Authentication authentication, UUID restaurantId, UUID locationId, UUID itemId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        //Checks the existence of the Location and Item
        requireLocation(restaurantId, locationId);
        requireItem(restaurantId, itemId);

        return inventoryLevelMapper.toResponse(
                inventoryLevelRepository.findByLocation_IdAndInventoryItem_Id(locationId, itemId)
                        .orElseThrow(InventoryLevelNotFoundException::new)
        );
    }

    //It lists the items on the Location searched.
    //It can List from given Item, from given Location and also from both given item and location
    //The variables ItemID and LoacationID can also be null and list the level of everyhing in this restaurant
    //Same logic as getLevel but uses no Exception if it does not exist
    @Transactional(readOnly = true)
    public List<InventoryLevelResponse> listLevels(
            Authentication authentication,
            UUID restaurantId,
            UUID locationId,
            UUID itemId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        if (locationId != null && itemId != null) {
            requireLocation(restaurantId, locationId);
            requireItem(restaurantId, itemId);

            return inventoryLevelRepository.findByLocation_IdAndInventoryItem_Id(locationId, itemId)
                    .map(inventoryLevelMapper::toResponse)
                    .map(List::of)
                    .orElseGet(List::of);
        }

        if (locationId != null) {
            requireLocation(restaurantId, locationId);

            return inventoryLevelRepository.findAllByLocation_IdOrderByInventoryItem_NameAsc(locationId).stream()
                    .map(inventoryLevelMapper::toResponse)
                    .toList();
        }

        if (itemId != null) {
            requireItem(restaurantId, itemId);

            return inventoryLevelRepository.findAllByInventoryItem_IdOrderByLocation_NameAsc(itemId).stream()
                    .map(inventoryLevelMapper::toResponse)
                    .toList();
        }

        return inventoryLevelRepository.findAllByRestaurantId(restaurantId).stream()
                .map(inventoryLevelMapper::toResponse)
                .toList();
    }


    // Same access + ownership pattern as the other reads: confirm the restaurant is accessible,
    // then confirm the item actually belongs to it (404 otherwise), before summing.
    @Transactional(readOnly = true)
    public InventoryLevelTotalResponse getTotalOnHand(Authentication authentication, UUID restaurantId, UUID itemId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        InventoryItem item = requireItem(restaurantId, itemId);

        BigDecimal total = inventoryLevelRepository.sumOnHandQuantityByRestaurantAndItem(restaurantId, itemId);

        return InventoryLevelTotalResponse.builder()
                .itemId(item.getId())
                .itemName(item.getName())
                .totalOnHandQuantity(total == null ? BigDecimal.ZERO : total)
                .unit(item.getBaseUnit())
                .build();
    }

    // Returns the Repository Method for listing the stock that is lower than reorder variable
    @Transactional(readOnly = true)
    public List<InventoryLevelResponse> listLowStock(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        return inventoryLevelRepository.findLowStockByRestaurantId(restaurantId).stream()
                .map(inventoryLevelMapper::toResponse)
                .toList();
    }

    // Internal use only. Not exposed through InventoryLevelController.
    // Meant to be called by the InventoryMovement service once it exists: every delivery,
    // sale, waste log, count correction, etc. calls this to apply its effect on stock,
    // instead of any endpoint being able to set onHandQuantity directly.
    @Transactional
    InventoryLevel upsertLevel(InventoryLocation location, InventoryItem item, BigDecimal quantityDelta) {
        //Get the Inventor Level Created, if it does not exist a level create a new one and return it
        InventoryLevel level = inventoryLevelRepository
                .findByLocation_IdAndInventoryItem_Id(location.getId(), item.getId())
                .orElseGet(() -> {
                    InventoryLevel created = new InventoryLevel();
                    created.setLocation(location);
                    created.setInventoryItem(item);
                    created.setOnHandQuantity(BigDecimal.ZERO);
                    return created;
                });

        // updates the onhand quantity and sets the last Movement
        BigDecimal currentOnHand = level.getOnHandQuantity() == null ? BigDecimal.ZERO : level.getOnHandQuantity();
        level.setOnHandQuantity(currentOnHand.add(quantityDelta));
        level.setLastMovementAt(OffsetDateTime.now(ZoneOffset.UTC));

        return inventoryLevelRepository.saveAndFlush(level);
    }

    // Internal use only. Meant to be called by InventoryCountService when a count is approved:
    // records that an item was physically verified at a location, independent of whether its
    // quantity actually changed (a line can have zero variance and still count as "checked").
    @Transactional
    InventoryLevel markCounted(InventoryLocation location, InventoryItem item, OffsetDateTime countedAt) {
        InventoryLevel level = inventoryLevelRepository
                .findByLocation_IdAndInventoryItem_Id(location.getId(), item.getId())
                .orElseGet(() -> {
                    InventoryLevel created = new InventoryLevel();
                    created.setLocation(location);
                    created.setInventoryItem(item);
                    created.setOnHandQuantity(BigDecimal.ZERO);
                    return created;
                });

        level.setLastCountedAt(countedAt);
        return inventoryLevelRepository.saveAndFlush(level);
    }

    // Internal use only. Meant to be called by InventoryCountService to pre-fill a new count
    // line's expectedQuantity from the live on-hand balance, defaulting to zero if no level
    // row exists yet for that (location, item) pair.
    @Transactional(readOnly = true)
    BigDecimal currentOnHandQuantity(UUID locationId, UUID itemId) {
        return inventoryLevelRepository.findByLocation_IdAndInventoryItem_Id(locationId, itemId)
                .map(InventoryLevel::getOnHandQuantity)
                .orElse(BigDecimal.ZERO);
    }

    //Calls the reporitory Method of the Location with ID
    private InventoryLocation requireLocation(UUID restaurantId, UUID locationId) {
        return inventoryLocationRepository.findByIdAndRestaurant_Id(locationId, restaurantId)
                .orElseThrow(InventoryLocationNotFoundException::new);
    }

    //Calls the repository Method of Item from its ID
    private InventoryItem requireItem(UUID restaurantId, UUID itemId) {
        return inventoryItemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(itemId, restaurantId)
                .orElseThrow(InventoryItemNotFoundException::new);
    }
}
