package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.exception.inventory.InventoryLocationNotFoundException;
import pos.pos.inventory.dto.InventoryLocationResponse;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.inventory.mapper.InventoryLocationMapper;
import pos.pos.inventory.repository.InventoryLocationRepository;
import pos.pos.menu.image.MenuImageReleaser;
import pos.pos.menu.image.MenuImageStorage;
import pos.pos.menu.image.MenuImageUploads;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.UUID;

/**
 * A place's own picture, instead of one of the app's icons (user, 2026-11-11: "when creating a place I can upload my own
 * image if I want"). Same picture storage and limits as stock item photos; a new picture replaces the old one, which is
 * deleted once the change is saved and nothing else shows it.
 */
@Service
@RequiredArgsConstructor
public class InventoryLocationPhotoService {

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryLocationRepository inventoryLocationRepository;
    private final InventoryLocationMapper inventoryLocationMapper;
    private final MenuImageStorage imageStorage;
    private final MenuImageReleaser imageReleaser;
    private final InventoryChangeNotifier inventoryChangeNotifier;

    @Transactional
    public InventoryLocationResponse uploadPhoto(Authentication authentication, UUID restaurantId, UUID locationId, MultipartFile file) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryLocation location = requireLocation(restaurantId, locationId);
        MenuImageUploads.CheckedPhoto photo = MenuImageUploads.check(file);
        String key = imageStorage.store(restaurantId, photo.content(), photo.contentType(), photo.extension());
        imageReleaser.discardOnRollback(key);
        return replace(authentication, location, imageStorage.urlFor(key));
    }

    @Transactional
    public InventoryLocationResponse removePhoto(Authentication authentication, UUID restaurantId, UUID locationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        return replace(authentication, requireLocation(restaurantId, locationId), null);
    }

    private InventoryLocationResponse replace(Authentication authentication, InventoryLocation location, String imageUrl) {
        String previous = location.getImageUrl();
        location.setImageUrl(imageUrl);
        location.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        InventoryLocation saved = inventoryLocationRepository.saveAndFlush(location);
        if (previous != null && !previous.equals(imageUrl)) {
            imageReleaser.releaseAfterCommit(previous);
        }
        inventoryChangeNotifier.changedAfterCommit(location.getRestaurant().getId());
        return inventoryLocationMapper.toResponse(saved);
    }

    private InventoryLocation requireLocation(UUID restaurantId, UUID locationId) {
        return inventoryLocationRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(locationId, restaurantId)
                .orElseThrow(InventoryLocationNotFoundException::new);
    }
}
