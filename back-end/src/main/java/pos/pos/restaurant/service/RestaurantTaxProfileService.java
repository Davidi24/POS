package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.RestaurantTaxProfileNotFoundException;
import pos.pos.restaurant.dto.RestaurantTaxProfileResponse;
import pos.pos.restaurant.dto.UpsertRestaurantTaxProfileRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantTaxProfile;
import pos.pos.restaurant.mapper.RestaurantTaxProfileMapper;
import pos.pos.restaurant.repository.RestaurantTaxProfileRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantTaxProfileService {

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantTaxProfileRepository restaurantTaxProfileRepository;
    private final RestaurantTaxProfileMapper restaurantTaxProfileMapper;

    public List<RestaurantTaxProfileResponse> getTaxProfiles(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return restaurantTaxProfileRepository.findAllByRestaurantIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtAsc(restaurantId)
                .stream()
                .map(restaurantTaxProfileMapper::toResponse)
                .toList();
    }

    @Transactional
    public RestaurantTaxProfileResponse createTaxProfile(
            Authentication authentication,
            UUID restaurantId,
            UpsertRestaurantTaxProfileRequest request
    ) {
        validateEffectiveRange(request);
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearExistingDefault(restaurantId, null, actorId);
        }

        RestaurantTaxProfile taxProfile = restaurantTaxProfileMapper.toNewEntity(restaurant, request, actorId);
        restaurantTaxProfileRepository.save(taxProfile);
        return restaurantTaxProfileMapper.toResponse(taxProfile);
    }

    @Transactional
    public RestaurantTaxProfileResponse updateTaxProfile(
            Authentication authentication,
            UUID restaurantId,
            UUID taxProfileId,
            UpsertRestaurantTaxProfileRequest request
    ) {
        validateEffectiveRange(request);
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        RestaurantTaxProfile taxProfile = findExistingTaxProfile(restaurantId, taxProfileId);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearExistingDefault(restaurantId, taxProfileId, actorId);
        }

        restaurantTaxProfileMapper.updateEntity(taxProfile, request, actorId);
        restaurantTaxProfileRepository.save(taxProfile);
        return restaurantTaxProfileMapper.toResponse(taxProfile);
    }

    @Transactional
    public RestaurantTaxProfileResponse makeDefault(
            Authentication authentication,
            UUID restaurantId,
            UUID taxProfileId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        RestaurantTaxProfile taxProfile = findExistingTaxProfile(restaurantId, taxProfileId);
        clearExistingDefault(restaurantId, taxProfileId, actorId);
        restaurantTaxProfileMapper.updateDefault(taxProfile, true, actorId);
        restaurantTaxProfileRepository.save(taxProfile);
        return restaurantTaxProfileMapper.toResponse(taxProfile);
    }

    @Transactional
    public void deleteTaxProfile(Authentication authentication, UUID restaurantId, UUID taxProfileId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        RestaurantTaxProfile taxProfile = findExistingTaxProfile(restaurantId, taxProfileId);
        restaurantTaxProfileMapper.markDeleted(
                taxProfile,
                restaurantScopeService.currentUserId(authentication),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        restaurantTaxProfileRepository.save(taxProfile);
    }

    private RestaurantTaxProfile findExistingTaxProfile(UUID restaurantId, UUID taxProfileId) {
        return restaurantTaxProfileRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(taxProfileId, restaurantId)
                .orElseThrow(RestaurantTaxProfileNotFoundException::new);
    }

    private void clearExistingDefault(UUID restaurantId, UUID keepTaxProfileId, UUID actorId) {
        restaurantTaxProfileRepository.findByRestaurantIdAndIsDefaultTrueAndDeletedAtIsNull(restaurantId)
                .filter(existing -> !existing.getId().equals(keepTaxProfileId))
                .ifPresent(existing -> {
                    restaurantTaxProfileMapper.updateDefault(existing, false, actorId);
                    restaurantTaxProfileRepository.save(existing);
                });
    }

    private void validateEffectiveRange(UpsertRestaurantTaxProfileRequest request) {
        if (request.getEffectiveFrom() != null
                && request.getEffectiveTo() != null
                && !request.getEffectiveTo().isAfter(request.getEffectiveFrom())) {
            throw new AuthException("effectiveTo must be after effectiveFrom", HttpStatus.BAD_REQUEST);
        }
    }
}
