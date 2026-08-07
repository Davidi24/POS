package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.inventory.InventoryLocationNotFoundException;
import pos.pos.inventory.dto.InventoryLocationRequest;
import pos.pos.inventory.dto.InventoryLocationResponse;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.inventory.mapper.InventoryLocationMapper;
import pos.pos.inventory.repository.InventoryLocationRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryLocationService {

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryLocationRepository inventoryLocationRepository;
    private final InventoryLocationMapper inventoryLocationMapper;

    @Transactional
    public InventoryLocationResponse createLocation(
            Authentication authentication,
            UUID restaurantId,
            InventoryLocationRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        assertCodeAvailable(restaurantId, request.getCode(), null);

        InventoryLocation location = new InventoryLocation();
        location.setRestaurant(restaurant);
        location.setBranch(resolveBranch(restaurantId, request.getBranchId()));
        location.setCreatedBy(actorId);
        location.setUpdatedBy(actorId);
        inventoryLocationMapper.applyRequest(location, request);

        return inventoryLocationMapper.toResponse(saveLocation(location));
    }

    @Transactional
    public InventoryLocationResponse updateLocation(
            Authentication authentication,
            UUID restaurantId,
            UUID locationId,
            InventoryLocationRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryLocation location = requireLocation(restaurantId, locationId);

        assertCodeAvailable(restaurantId, request.getCode(), location.getCode());

        location.setBranch(resolveBranch(restaurantId, request.getBranchId()));
        location.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        inventoryLocationMapper.applyRequest(location, request);

        return inventoryLocationMapper.toResponse(saveLocation(location));
    }

    @Transactional(readOnly = true)
    public InventoryLocationResponse getLocation(Authentication authentication, UUID restaurantId, UUID locationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return inventoryLocationMapper.toResponse(requireLocation(restaurantId, locationId));
    }

    @Transactional(readOnly = true)
    public List<InventoryLocationResponse> listActiveLocations(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return inventoryLocationRepository.findAllByRestaurant_IdAndActiveTrueOrderByNameAsc(restaurantId).stream()
                .map(inventoryLocationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryLocationResponse> listAllLocations(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return inventoryLocationRepository.findAllByRestaurant_IdOrderByNameAsc(restaurantId).stream()
                .map(inventoryLocationMapper::toResponse)
                .toList();
    }

    @Transactional
    public void deactivateLocation(Authentication authentication, UUID restaurantId, UUID locationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryLocation location = requireLocation(restaurantId, locationId);

        location.setActive(false);
        location.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        saveLocation(location);
    }

    private InventoryLocation requireLocation(UUID restaurantId, UUID locationId) {
        return inventoryLocationRepository.findByIdAndRestaurant_Id(locationId, restaurantId)
                .orElseThrow(InventoryLocationNotFoundException::new);
    }

    private Branch resolveBranch(UUID restaurantId, UUID branchId) {
        if (branchId == null) {
            return null;
        }

        return restaurantScopeService.requireExistingBranch(restaurantId, branchId);
    }

    private void assertCodeAvailable(UUID restaurantId, String rawCode, String existingCode) {
        String normalizedCode = NormalizationUtils.normalizeCode(rawCode, 80);
        if (normalizedCode == null || normalizedCode.equals(existingCode)) {
            return;
        }

        inventoryLocationRepository.findByRestaurant_IdAndCode(restaurantId, normalizedCode)
                .ifPresent(existing -> {
                    throw new AuthException(
                            "This code is already used by another location in this restaurant",
                            HttpStatus.CONFLICT
                    );
                });
    }

    private InventoryLocation saveLocation(InventoryLocation location) {
        try {
            return inventoryLocationRepository.saveAndFlush(location);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Inventory location update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
