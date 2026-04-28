package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.RestaurantNotFoundException;
import pos.pos.restaurant.dto.CreateRestaurantRequest;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.UpdateRestaurantRequest;
import pos.pos.restaurant.dto.UpdateRestaurantStatusRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.mapper.RestaurantMapper;
import pos.pos.restaurant.policy.RestaurantPolicy;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.util.RestaurantFieldNormalizer;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;
import pos.pos.utils.PageableUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

//checked
@Service
@RequiredArgsConstructor
public class RestaurantAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final boolean DEFAULT_ACTIVE = true;
    private static final RestaurantStatus DEFAULT_STATUS = RestaurantStatus.ACTIVE;

    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final RestaurantMapper restaurantMapper;
    private final ActorScopeService actorScopeService;
    private final RestaurantPolicy restaurantPolicy;
    private final RestaurantValidationService restaurantValidationService;
    private final RestaurantOwnerProvisioningService restaurantOwnerProvisioningService;
    private final RestaurantScopeService restaurantScopeService;

    @Transactional(readOnly = true)
    public PageResponse<RestaurantResponse> getRestaurants(
            Authentication authentication,
            String search,
            Boolean active,
            String status,
            UUID ownerUserId,
            Integer page,
            Integer size,
            String sortBy,
            String direction
    ) {
        ActorScope scope = actorScopeService.resolve(authentication);
        Pageable pageable = PageableUtils.create(page, size, direction, resolveSortProperty(sortBy), DEFAULT_PAGE_SIZE);
        String searchLike = NormalizationUtils.normalizeLowerLike(search);
        RestaurantStatus normalizedStatus = resolveStatus(status);

        var restaurantsPage = restaurantRepository.searchVisibleRestaurants(
                active,
                normalizedStatus,
                ownerUserId,
                searchLike,
                scope.superAdmin(),
                scope.userId(),
                scope.restaurantId(),
                pageable
        );

        var items = restaurantsPage.getContent().stream()
                .map(restaurantMapper::toResponse)
                .toList();

        return PageResponse.from(new PageImpl<>(items, pageable, restaurantsPage.getTotalElements()));
    }

    //checked
    @Transactional
    public RestaurantResponse createRestaurant(Authentication authentication, CreateRestaurantRequest request) {
        restaurantScopeService.assertCanCreateRestaurant(authentication);

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        var normalizedFields = restaurantValidationService.normalizeAndValidateFields(
                request.getCode(),
                request.getSlug(),
                request.getName(),
                request.getTimezone(),
                null
        );

        Restaurant restaurant = restaurantMapper.toNewEntity(
                request,
                normalizedFields.code(),
                normalizedFields.slug(),
                DEFAULT_ACTIVE,
                DEFAULT_STATUS,
                actorId
        );

        restaurantRepository.save(restaurant);

        User owner = restaurantOwnerProvisioningService.createAndInviteOwner(
                request.getOwner(),
                restaurant.getId(),
                actorId,
                restaurant.getName()
        );
        restaurant.setOwnerId(owner.getId());
        restaurantRepository.save(restaurant);

        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurant(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurantBySlug(Authentication authentication, String slug) {
        String normalizedSlug = RestaurantFieldNormalizer.normalizeSlug(slug);
        Restaurant restaurant = restaurantRepository.findBySlugAndDeletedAtIsNull(normalizedSlug)
                .orElseThrow(RestaurantNotFoundException::new);
        restaurantPolicy.assertCanAccess(actorScopeService.resolve(authentication), restaurant);
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateRestaurant(
            Authentication authentication,
            UUID restaurantId,
            UpdateRestaurantRequest request
    ) {
        ActorScope scope = actorScopeService.resolve(authentication);
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(scope, restaurantId);

        restaurantValidationService.validateStatusTransition(restaurant.getStatus(), request.getStatus());
        restaurantPolicy.assertCanChangeOwner(scope, restaurant, request.getOwnerUserId());
        UUID ownerUserId = restaurantValidationService.validateOwnerUser(request.getOwnerUserId(), restaurant.getId());
        validateStatusFields(request.getIsActive(), request.getStatus());
        var normalizedFields = restaurantValidationService.normalizeAndValidateFields(
                request.getCode(),
                request.getSlug(),
                request.getName(),
                request.getTimezone(),
                restaurant.getId()
        );

        restaurantMapper.updateEntity(
                restaurant,
                request,
                normalizedFields.code(),
                normalizedFields.slug(),
                ownerUserId,
                restaurantScopeService.currentUserId(authentication)
        );

        restaurantRepository.save(restaurant);
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateRestaurantStatus(
            Authentication authentication,
            UUID restaurantId,
            UpdateRestaurantStatusRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        restaurantValidationService.validateStatusTransition(restaurant.getStatus(), request.getStatus());
        validateStatusFields(request.getIsActive(), request.getStatus());

        restaurantMapper.updateStatus(
                restaurant,
                request.getIsActive(),
                request.getStatus(),
                restaurantScopeService.currentUserId(authentication)
        );

        restaurantRepository.save(restaurant);
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    public void deleteRestaurant(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.assertCanDeleteRestaurant(authentication);
        Restaurant restaurant = restaurantScopeService.requireExistingRestaurant(restaurantId);

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        branchRepository.softDeleteAllByRestaurantId(restaurantId, now, actorId);

        restaurantMapper.markDeleted(restaurant, actorId, now);
        restaurantRepository.save(restaurant);
    }

    // rejects terminal statuses (e.g. ARCHIVED) and inconsistent isActive/status pairs
    private void validateStatusFields(Boolean isActive, RestaurantStatus status) {
        restaurantValidationService.validateManageableStatus(status);
        restaurantValidationService.validateStatusConsistency(isActive, status);
    }

    private String resolveSortProperty(String sortBy) {
        String normalized = NormalizationUtils.normalizeLower(sortBy);
        if (normalized == null || normalized.equals("createdat") || normalized.equals("created_at")) {
            return "createdAt";
        }

        return switch (normalized) {
            case "updatedat", "updated_at" -> "updatedAt";
            case "name" -> "name";
            case "legalname", "legal_name" -> "legalName";
            case "code" -> "code";
            case "slug" -> "slug";
            case "status" -> "status";
            default -> throw new AuthException("Invalid sortBy value", HttpStatus.BAD_REQUEST);
        };
    }

    private RestaurantStatus resolveStatus(String status) {
        String normalized = NormalizationUtils.normalizeUpper(status);
        if (normalized == null) {
            return null;
        }

        try {
            return RestaurantStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new AuthException("Invalid status value", HttpStatus.BAD_REQUEST);
        }
    }

}
