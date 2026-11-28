package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.menu.image.MenuImageReleaser;
import pos.pos.menu.image.MenuImageStorage;
import pos.pos.menu.image.MenuImageUploads;
import pos.pos.restaurant.dto.RestaurantBrandingResponse;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantBranding;
import pos.pos.restaurant.mapper.RestaurantBrandingMapper;
import pos.pos.restaurant.repository.RestaurantBrandingRepository;

import java.util.UUID;

/**
 * The restaurant's logo, kept in the same picture storage as dish photos (R2 in production). A new logo replaces the
 * old one, which is deleted once the change is saved.
 */
@Service
@RequiredArgsConstructor
public class RestaurantLogoService {

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantBrandingRepository restaurantBrandingRepository;
    private final RestaurantBrandingMapper restaurantBrandingMapper;
    private final MenuImageStorage imageStorage;
    private final MenuImageReleaser imageReleaser;

    @Transactional
    public RestaurantBrandingResponse uploadLogo(Authentication authentication, UUID restaurantId, MultipartFile file) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        MenuImageUploads.CheckedPhoto picture = MenuImageUploads.check(file);
        RestaurantBranding branding = brandingOf(restaurant, restaurantScopeService.currentUserId(authentication));

        String key = imageStorage.store(restaurantId, picture.content(), picture.contentType(), picture.extension());
        imageReleaser.discardOnRollback(key);
        String previous = branding.getLogoUrl();
        branding.setLogoUrl(imageStorage.urlFor(key));
        restaurantBrandingRepository.save(branding);
        if (previous != null) {
            imageReleaser.releaseAfterCommit(previous);
        }
        return restaurantBrandingMapper.toResponse(branding);
    }

    @Transactional
    public RestaurantBrandingResponse removeLogo(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        RestaurantBranding branding = brandingOf(restaurant, restaurantScopeService.currentUserId(authentication));
        String previous = branding.getLogoUrl();
        branding.setLogoUrl(null);
        restaurantBrandingRepository.save(branding);
        if (previous != null) {
            imageReleaser.releaseAfterCommit(previous);
        }
        return restaurantBrandingMapper.toResponse(branding);
    }

    private RestaurantBranding brandingOf(Restaurant restaurant, UUID actorId) {
        RestaurantBranding branding = restaurantBrandingRepository.findByRestaurantIdAndDeletedAtIsNull(restaurant.getId())
                .orElseGet(() -> {
                    RestaurantBranding created = new RestaurantBranding();
                    created.setRestaurant(restaurant);
                    created.setCreatedBy(actorId);
                    return created;
                });
        branding.setUpdatedBy(actorId);
        return branding;
    }
}
