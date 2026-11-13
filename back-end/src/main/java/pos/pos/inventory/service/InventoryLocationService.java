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
import pos.pos.inventory.entity.InventoryLevel;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.inventory.entity.InventorySalesSource;
import pos.pos.inventory.enums.InventoryCountStatus;
import pos.pos.inventory.enums.InventoryKindScope;
import pos.pos.inventory.enums.InventoryLocationType;
import pos.pos.inventory.repository.InventoryCountRepository;
import pos.pos.inventory.repository.InventoryLevelRepository;
import pos.pos.inventory.mapper.InventoryLocationMapper;
import pos.pos.inventory.repository.InventoryLocationRepository;
import pos.pos.inventory.repository.InventorySalesSourceRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
import java.util.UUID;

// Manages the places where inventory is stored, such as a warehouse, kitchen, bar, or freezer.
// It can create, update, find, list, and deactivate inventory locations.
// A location may belong to a branch, but it must always belong to the correct restaurant.
// It also prevents two locations in the same restaurant from using the same code.
@Service
@RequiredArgsConstructor
public class InventoryLocationService {

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryLocationRepository inventoryLocationRepository;
    private final InventorySalesSourceRepository inventorySalesSourceRepository;
    private final InventoryLocationMapper inventoryLocationMapper;
    private final InventoryChangeNotifier inventoryChangeNotifier;
    private final InventoryKindService inventoryKindService;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final InventoryCountRepository inventoryCountRepository;
    private final InventoryMovementService inventoryMovementService;

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
        location.setKind(ownKind(restaurantId, location, request));

        InventoryLocation saved = saveLocation(location);
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
        return inventoryLocationMapper.toResponse(saved);
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

        boolean hasSalesSources = inventorySalesSourceRepository.existsByLocation_Id(locationId);
        Branch nextBranch = resolveBranch(restaurantId, request.getBranchId());
        boolean branchChanged = !java.util.Objects.equals(
                location.getBranch() == null ? null : location.getBranch().getId(),
                nextBranch == null ? null : nextBranch.getId()
        );
        if (hasSalesSources && (branchChanged || Boolean.FALSE.equals(request.getActive()))) {
            throw new AuthException("Remove this location's sale stock sources before changing its branch or deactivating it", HttpStatus.CONFLICT);
        }

        assertCodeAvailable(restaurantId, request.getCode(), location.getCode());

        location.setBranch(nextBranch);
        location.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        inventoryLocationMapper.applyRequest(location, request);
        location.setKind(ownKind(restaurantId, location, request));

        InventoryLocation saved = saveLocation(location);
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
        return inventoryLocationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InventoryLocationResponse getLocation(Authentication authentication, UUID restaurantId, UUID locationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return inventoryLocationMapper.toResponse(requireLocation(restaurantId, locationId));
    }

    @Transactional(readOnly = true)
    public List<InventoryLocationResponse> listActiveLocations(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return inventoryLocationRepository.findAllByRestaurant_IdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(restaurantId).stream()
                .map(inventoryLocationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryLocationResponse> listAllLocations(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return inventoryLocationRepository.findAllByRestaurant_IdAndDeletedAtIsNullOrderByNameAsc(restaurantId).stream()
                .map(inventoryLocationMapper::toResponse)
                .toList();
    }

    @Transactional
    public void deactivateLocation(Authentication authentication, UUID restaurantId, UUID locationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryLocation location = requireLocation(restaurantId, locationId);

        if (inventorySalesSourceRepository.existsByLocation_Id(locationId)) {
            throw new AuthException("Remove this location's sale stock sources before deactivating it", HttpStatus.CONFLICT);
        }

        location.setActive(false);
        location.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        saveLocation(location);
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
    }

    private InventoryLocation requireLocation(UUID restaurantId, UUID locationId) {
        return inventoryLocationRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(locationId, restaurantId)
                .orElseThrow(InventoryLocationNotFoundException::new);
    }

    /**
     * Deletes a place (Stock → Places → Delete; user, 2026-10-30). Asked first in the app what happens to its stock:
     * [keepStock] moves every line to the branch's Uncategorized place (made when missing) and points its sale places
     * there too; otherwise the stock is taken out of stock as a correction and its sale places go. Either way History keeps
     * a "Place deleted: …" line per item. Refused while a count of it isn't approved or cancelled.
     */
    @Transactional
    public void deleteLocation(Authentication authentication, UUID restaurantId, UUID locationId, boolean keepStock) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryLocation location = requireLocation(restaurantId, locationId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        if (inventoryCountRepository.existsByLocation_IdAndStatusIn(locationId, java.util.List.of(InventoryCountStatus.DRAFT,
                InventoryCountStatus.IN_PROGRESS, InventoryCountStatus.COMPLETED))) {
            throw new AuthException(location.getName() + " has a count that isn't approved yet. Approve or cancel it first.", HttpStatus.CONFLICT);
        }
        List<InventoryLevel> levels = inventoryLevelRepository.findAllByLocation_IdOrderByInventoryItem_NameAsc(locationId).stream()
                .filter(level -> level.getOnHandQuantity() != null && level.getOnHandQuantity().signum() > 0)
                .toList();
        if (keepStock && location.getLocationType() == InventoryLocationType.UNCATEGORIZED && !levels.isEmpty()) {
            throw new AuthException("Uncategorized can't keep its own stock. Move it to another place first, or delete it with the place.",
                    HttpStatus.CONFLICT);
        }

        // Uncategorized is only made when there is stock to keep.
        InventoryLocation target = keepStock && !levels.isEmpty() ? uncategorizedFor(location, actorId) : null;
        for (InventoryLevel level : levels) {
            inventoryMovementService.clearForPlaceDeletion(restaurantId, location, target, level.getInventoryItem(), level.getOnHandQuantity(), actorId);
        }
        inventoryLevelRepository.deleteAllForLocation(locationId);
        if (target != null) {
            for (InventorySalesSource source : inventorySalesSourceRepository.findAllByLocation_Id(locationId)) {
                source.setLocation(target);
                inventorySalesSourceRepository.save(source);
            }
        } else {
            inventorySalesSourceRepository.deleteAllByLocation_Id(locationId);
        }

        location.setActive(false);
        location.setDeletedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        // Codes stay unique per restaurant even for deleted places, so this one's code is freed for a new place.
        String tail = "-DEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        String code = location.getCode();
        location.setCode((code.length() + tail.length() > 80 ? code.substring(0, 80 - tail.length()) : code) + tail);
        location.setUpdatedBy(actorId);
        saveLocation(location);
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
    }

    // The Uncategorized place of the deleted place's branch (or of the whole restaurant), made the first time it's needed.
    private InventoryLocation uncategorizedFor(InventoryLocation from, UUID actorId) {
        UUID restaurantId = from.getRestaurant().getId();
        Branch branch = from.getBranch();
        InventoryLocation found = (branch == null
                ? inventoryLocationRepository.findFirstByRestaurant_IdAndLocationTypeAndBranchIsNullAndDeletedAtIsNullOrderByCreatedAtAsc(
                        restaurantId, InventoryLocationType.UNCATEGORIZED)
                : inventoryLocationRepository.findFirstByRestaurant_IdAndLocationTypeAndBranch_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        restaurantId, InventoryLocationType.UNCATEGORIZED, branch.getId()))
                .filter(place -> !place.getId().equals(from.getId()))
                .orElse(null);
        if (found != null) {
            if (!found.isActive()) {
                found.setActive(true);
                found.setUpdatedBy(actorId);
                saveLocation(found);
            }
            return found;
        }
        String base = NormalizationUtils.normalizeCode("UNCATEGORIZED" + (branch == null ? "" : "-" + branch.getCode()), 70);
        String code = base;
        for (int n = 2; inventoryLocationRepository.findByRestaurant_IdAndCode(restaurantId, code).isPresent(); n++) {
            code = base + "-" + n;
        }
        InventoryLocation place = new InventoryLocation();
        place.setRestaurant(from.getRestaurant());
        place.setBranch(branch);
        place.setCode(code);
        place.setName("Uncategorized");
        place.setLocationType(InventoryLocationType.UNCATEGORIZED);
        place.setNotes("Stock from deleted places. Move it where it belongs.");
        place.setActive(true);
        place.setCreatedBy(actorId);
        place.setUpdatedBy(actorId);
        return saveLocation(place);
    }

    // One of the restaurant's own kinds for a CUSTOM place, none for the built-in kinds.
    private pos.pos.inventory.entity.InventoryKind ownKind(UUID restaurantId, InventoryLocation location, InventoryLocationRequest request) {
        return location.getLocationType() == InventoryLocationType.CUSTOM
                ? inventoryKindService.requireOwnKind(restaurantId, InventoryKindScope.PLACE, request.getKindId())
                : null;
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
