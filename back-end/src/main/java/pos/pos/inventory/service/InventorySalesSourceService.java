package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.inventory.InventoryItemNotFoundException;
import pos.pos.exception.inventory.InventoryLocationNotFoundException;
import pos.pos.inventory.dto.InventorySalesSourceRequest;
import pos.pos.inventory.dto.InventorySalesSourceResponse;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.inventory.entity.InventorySalesSource;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.inventory.repository.InventoryLocationRepository;
import pos.pos.inventory.repository.InventorySalesSourceRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventorySalesSourceService {

    private final RestaurantScopeService restaurantScopeService;
    private final InventorySalesSourceRepository sourceRepository;
    private final BranchRepository branchRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;

    @Transactional(readOnly = true)
    public List<InventorySalesSourceResponse> list(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return sourceRepository.findAllByRestaurant_IdOrderByBranch_NameAscInventoryItem_NameAsc(restaurantId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public InventorySalesSourceResponse set(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID itemId,
            InventorySalesSourceRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Branch branch = branchRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(branchId, restaurantId)
                .orElseThrow(() -> new AuthException("Branch not found", HttpStatus.NOT_FOUND));
        InventoryItem item = itemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(itemId, restaurantId)
                .orElseThrow(InventoryItemNotFoundException::new);
        InventoryLocation location = locationRepository.findByIdAndRestaurant_Id(request.getLocationId(), restaurantId)
                .orElseThrow(InventoryLocationNotFoundException::new);

        if (!branch.isActive()) {
            throw new AuthException("Inactive branches cannot be configured for sale stock", HttpStatus.CONFLICT);
        }
        if (!item.isActive() || !item.isTrackInventory()) {
            throw new AuthException("Sale stock items must be active and track inventory", HttpStatus.CONFLICT);
        }
        if (!location.isActive()) {
            throw new AuthException("An inactive location cannot supply sale stock", HttpStatus.CONFLICT);
        }
        if (location.getBranch() != null && !branchId.equals(location.getBranch().getId())) {
            throw new AuthException("The location must belong to this branch or be restaurant-wide", HttpStatus.BAD_REQUEST);
        }

        InventorySalesSource source = sourceRepository.findByBranch_IdAndInventoryItem_Id(branchId, itemId)
                .orElseGet(InventorySalesSource::new);
        source.setRestaurant(restaurant);
        source.setBranch(branch);
        source.setInventoryItem(item);
        source.setLocation(location);
        try {
            return toResponse(sourceRepository.saveAndFlush(source));
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException("A sale stock source for this branch and item already exists", HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public void delete(Authentication authentication, UUID restaurantId, UUID branchId, UUID itemId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        if (!branchRepository.existsByIdAndRestaurantIdAndDeletedAtIsNull(branchId, restaurantId)) {
            throw new AuthException("Branch not found", HttpStatus.NOT_FOUND);
        }
        sourceRepository.deleteByBranch_IdAndInventoryItem_Id(branchId, itemId);
    }

    private InventorySalesSourceResponse toResponse(InventorySalesSource source) {
        return InventorySalesSourceResponse.builder()
                .id(source.getId())
                .restaurantId(source.getRestaurant().getId())
                .branchId(source.getBranch().getId())
                .branchName(source.getBranch().getName())
                .inventoryItemId(source.getInventoryItem().getId())
                .inventoryItemName(source.getInventoryItem().getName())
                .locationId(source.getLocation().getId())
                .locationName(source.getLocation().getName())
                .build();
    }
}
