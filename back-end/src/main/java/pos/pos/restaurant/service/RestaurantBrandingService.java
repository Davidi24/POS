package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.restaurant.RestaurantBrandingNotFoundException;
import pos.pos.restaurant.dto.RestaurantBrandingResponse;
import pos.pos.restaurant.dto.UpsertRestaurantBrandingRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantBranding;
import pos.pos.restaurant.mapper.RestaurantBrandingMapper;
import pos.pos.restaurant.repository.RestaurantBrandingRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantBrandingService {

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantBrandingRepository restaurantBrandingRepository;
    private final RestaurantBrandingMapper restaurantBrandingMapper;

    public RestaurantBrandingResponse getBranding(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        RestaurantBranding branding = restaurantBrandingRepository.findByRestaurantIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(RestaurantBrandingNotFoundException::new);
        return restaurantBrandingMapper.toResponse(branding);
    }

    @Transactional
    public RestaurantBrandingResponse upsertBranding(
            Authentication authentication,
            UUID restaurantId,
            UpsertRestaurantBrandingRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        RestaurantBranding branding = restaurantBrandingRepository.findByRestaurantIdAndDeletedAtIsNull(restaurantId)
                .map(existing -> {
                    restaurantBrandingMapper.updateEntity(existing, request, actorId);
                    return existing;
                })
                .orElseGet(() -> restaurantBrandingMapper.toNewEntity(restaurant, request, actorId));

        restaurantBrandingRepository.save(branding);
        return restaurantBrandingMapper.toResponse(branding);
    }
}
