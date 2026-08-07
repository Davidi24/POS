package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.inventory.InventoryItemNotFoundException;
import pos.pos.exception.inventory.InventoryLevelNotFoundException;
import pos.pos.exception.inventory.InventoryLocationNotFoundException;
import pos.pos.inventory.dto.InventoryLevelResponse;
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

// This service is read-only from the outside. InventoryLevel represents the current stock
// balance for one (item, location) pair, and it should never be edited directly through a
// public endpoint — that would let a number get typed in without any record of why it changed.
// Every real change to onHandQuantity is supposed to come from an InventoryMovement (delivery,
// sale, waste, count correction, etc). upsertLevel() below is the one place that's allowed to
// touch onHandQuantity, and it's package-private on purpose: only another service in this same
// package (the future InventoryMovement service) is meant to call it.
@Service
@RequiredArgsConstructor
public class InventoryLevelService {

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final InventoryLevelMapper inventoryLevelMapper;
    private final InventoryLocationRepository inventoryLocationRepository;
    private final InventoryItemRepository inventoryItemRepository;

    @Transactional(readOnly = true)
    public InventoryLevelResponse getLevel(Authentication authentication, UUID restaurantId, UUID locationId, UUID itemId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        requireLocation(restaurantId, locationId);
        requireItem(restaurantId, itemId);

        return inventoryLevelMapper.toResponse(
                inventoryLevelRepository.findByLocation_IdAndInventoryItem_Id(locationId, itemId)
                        .orElseThrow(InventoryLevelNotFoundException::new)
        );
    }

    @Transactional(readOnly = true)
    public List<InventoryLevelResponse> listByLocation(Authentication authentication, UUID restaurantId, UUID locationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        requireLocation(restaurantId, locationId);

        return inventoryLevelRepository.findAllByLocation_IdOrderByInventoryItem_NameAsc(locationId).stream()
                .map(inventoryLevelMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryLevelResponse> listByItem(Authentication authentication, UUID restaurantId, UUID itemId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        requireItem(restaurantId, itemId);

        return inventoryLevelRepository.findAllByInventoryItem_IdOrderByLocation_NameAsc(itemId).stream()
                .map(inventoryLevelMapper::toResponse)
                .toList();
    }

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
        InventoryLevel level = inventoryLevelRepository
                .findByLocation_IdAndInventoryItem_Id(location.getId(), item.getId())
                .orElseGet(() -> {
                    InventoryLevel created = new InventoryLevel();
                    created.setLocation(location);
                    created.setInventoryItem(item);
                    created.setOnHandQuantity(BigDecimal.ZERO);
                    return created;
                });

        BigDecimal currentOnHand = level.getOnHandQuantity() == null ? BigDecimal.ZERO : level.getOnHandQuantity();
        level.setOnHandQuantity(currentOnHand.add(quantityDelta));
        level.setLastMovementAt(OffsetDateTime.now(ZoneOffset.UTC));

        return inventoryLevelRepository.saveAndFlush(level);
    }

    private InventoryLocation requireLocation(UUID restaurantId, UUID locationId) {
        return inventoryLocationRepository.findByIdAndRestaurant_Id(locationId, restaurantId)
                .orElseThrow(InventoryLocationNotFoundException::new);
    }

    private InventoryItem requireItem(UUID restaurantId, UUID itemId) {
        return inventoryItemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(itemId, restaurantId)
                .orElseThrow(InventoryItemNotFoundException::new);
    }
}
