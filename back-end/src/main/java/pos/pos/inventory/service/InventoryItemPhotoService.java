package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.exception.inventory.InventoryItemNotFoundException;
import pos.pos.inventory.dto.InventoryItemResponse;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.mapper.InventoryItemMapper;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.menu.image.MenuImageReleaser;
import pos.pos.menu.image.MenuImageStorage;
import pos.pos.menu.image.MenuImageUploads;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.UUID;

/**
 * A stock item's photo. Kept in the same picture storage as dish photos (in the restaurant's folder); a new photo
 * replaces the old one, which is deleted once the change is saved and nothing else shows it.
 */
@Service
@RequiredArgsConstructor
public class InventoryItemPhotoService {

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryItemMapper inventoryItemMapper;
    private final MenuImageStorage imageStorage;
    private final MenuImageReleaser imageReleaser;
    private final InventoryChangeNotifier inventoryChangeNotifier;
    private final InventoryItemEvents inventoryItemEvents;

    @Transactional
    public InventoryItemResponse uploadPhoto(Authentication authentication, UUID restaurantId, UUID itemId, MultipartFile file) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryItem item = requireItem(restaurantId, itemId);
        MenuImageUploads.CheckedPhoto photo = MenuImageUploads.check(file);
        String key = imageStorage.store(restaurantId, photo.content(), photo.contentType(), photo.extension());
        imageReleaser.discardOnRollback(key);
        return replace(authentication, item, imageStorage.urlFor(key));
    }

    @Transactional
    public InventoryItemResponse removePhoto(Authentication authentication, UUID restaurantId, UUID itemId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        return replace(authentication, requireItem(restaurantId, itemId), null);
    }

    private InventoryItemResponse replace(Authentication authentication, InventoryItem item, String imageUrl) {
        String previous = item.getImageUrl();
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        item.setImageUrl(imageUrl);
        item.setUpdatedBy(actorId);
        InventoryItem saved = inventoryItemRepository.saveAndFlush(item);
        inventoryItemEvents.record(saved, "ITEM_UPDATED", imageUrl == null ? "Photo removed" : previous == null ? "Photo added" : "New photo", actorId);
        if (previous != null && !previous.equals(imageUrl)) {
            imageReleaser.releaseAfterCommit(previous);
        }
        inventoryChangeNotifier.changedAfterCommit(item.getRestaurant().getId());
        return inventoryItemMapper.toResponse(saved);
    }

    private InventoryItem requireItem(UUID restaurantId, UUID itemId) {
        return inventoryItemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(itemId, restaurantId)
                .orElseThrow(InventoryItemNotFoundException::new);
    }
}
