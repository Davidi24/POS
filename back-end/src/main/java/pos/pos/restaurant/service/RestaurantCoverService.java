package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.menu.image.MenuImageReleaser;
import pos.pos.menu.image.MenuImageStorage;
import pos.pos.menu.image.MenuImageUploads;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;

import java.util.UUID;

/**
 * The cover pictures on the restaurant and branch cards of the super admins' lists. Kept in the same picture storage
 * as dish photos (in the restaurant's folder); a new picture replaces the old one, which is deleted once saved.
 */
@Service
@RequiredArgsConstructor
public class RestaurantCoverService {

    private static final String ONLY_SUPER_ADMIN = "Only the Super Admin can change the cover pictures";

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final MenuImageStorage imageStorage;
    private final MenuImageReleaser imageReleaser;

    public record CoverResponse(String coverUrl) {
    }

    @Transactional
    public CoverResponse uploadRestaurantCover(Authentication authentication, UUID restaurantId, MultipartFile file) {
        Restaurant restaurant = restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId, ONLY_SUPER_ADMIN);
        String previous = restaurant.getCoverUrl();
        restaurant.setCoverUrl(store(restaurantId, file));
        restaurantRepository.save(restaurant);
        release(previous);
        return new CoverResponse(restaurant.getCoverUrl());
    }

    @Transactional
    public CoverResponse removeRestaurantCover(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId, ONLY_SUPER_ADMIN);
        String previous = restaurant.getCoverUrl();
        restaurant.setCoverUrl(null);
        restaurantRepository.save(restaurant);
        release(previous);
        return new CoverResponse(null);
    }

    @Transactional
    public CoverResponse uploadBranchCover(Authentication authentication, UUID restaurantId, UUID branchId, MultipartFile file) {
        restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId, ONLY_SUPER_ADMIN);
        Branch branch = restaurantScopeService.requireExistingBranch(restaurantId, branchId);
        String previous = branch.getCoverUrl();
        branch.setCoverUrl(store(restaurantId, file));
        branchRepository.save(branch);
        release(previous);
        return new CoverResponse(branch.getCoverUrl());
    }

    @Transactional
    public CoverResponse removeBranchCover(Authentication authentication, UUID restaurantId, UUID branchId) {
        restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId, ONLY_SUPER_ADMIN);
        Branch branch = restaurantScopeService.requireExistingBranch(restaurantId, branchId);
        String previous = branch.getCoverUrl();
        branch.setCoverUrl(null);
        branchRepository.save(branch);
        release(previous);
        return new CoverResponse(null);
    }

    private String store(UUID restaurantId, MultipartFile file) {
        MenuImageUploads.CheckedPhoto picture = MenuImageUploads.check(file);
        String key = imageStorage.store(restaurantId, picture.content(), picture.contentType(), picture.extension());
        imageReleaser.discardOnRollback(key);
        return imageStorage.urlFor(key);
    }

    private void release(String previous) {
        if (previous != null) {
            imageReleaser.releaseAfterCommit(previous);
        }
    }
}
