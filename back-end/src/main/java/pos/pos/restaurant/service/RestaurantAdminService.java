package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.RestaurantOwnerNotFoundException;
import pos.pos.exception.restaurant.RestaurantOwnershipChangeNotAllowedException;
import pos.pos.restaurant.dto.CreateRestaurantRequest;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.UpdateRestaurantRequest;
import pos.pos.restaurant.dto.UpdateRestaurantStatusRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.mapper.RestaurantMapper;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final boolean DEFAULT_ACTIVE = true;
    private static final RestaurantStatus DEFAULT_STATUS = RestaurantStatus.ACTIVE;

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final RestaurantMapper restaurantMapper;
    private final RoleHierarchyService roleHierarchyService;
    private final RestaurantValidationService restaurantValidationService;
    private final RestaurantOwnerProvisioningService restaurantOwnerProvisioningService;
    private final RestaurantScopeService restaurantScopeService;

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
        Pageable pageable = PageRequest.of(
                page == null ? 0 : page,
                size == null ? DEFAULT_PAGE_SIZE : size,
                Sort.by(resolveDirection(direction), resolveSortProperty(sortBy))
        );

        String normalizedSearch = NormalizationUtils.normalizeLower(search);
        String searchLike = normalizedSearch == null ? null : "%" + normalizedSearch + "%";
        RestaurantStatus normalizedStatus = resolveStatus(status);
        boolean superAdmin = roleHierarchyService.isSuperAdmin(authentication);
        User actor = superAdmin ? null : restaurantScopeService.currentActor(authentication);

        var restaurantsPage = restaurantRepository.searchVisibleRestaurants(
                active,
                normalizedStatus,
                ownerUserId,
                searchLike,
                superAdmin,
                roleHierarchyService.currentUserId(authentication),
                actor == null ? null : actor.getRestaurantId(),
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
        RestaurantValidationService.NormalizedRestaurantFields normalizedFields =
                restaurantValidationService.normalizeAndValidateFields(
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

    //checked
    public RestaurantResponse getRestaurant(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateRestaurant(
            Authentication authentication,
            UUID restaurantId,
            UpdateRestaurantRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);

        boolean superAdmin = roleHierarchyService.isSuperAdmin(authentication);
        if (!superAdmin && !Objects.equals(restaurant.getOwnerId(), request.getOwnerUserId())) {
            throw new RestaurantOwnershipChangeNotAllowedException();
        }

        UUID ownerUserId = validateOwnerUser(request.getOwnerUserId(), restaurant.getId());
        restaurantValidationService.validateManageableStatus(request.getStatus());
        restaurantValidationService.validateStatusConsistency(request.getIsActive(), request.getStatus());
        RestaurantValidationService.NormalizedRestaurantFields normalizedFields =
                restaurantValidationService.normalizeAndValidateFields(
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
        restaurantValidationService.validateManageableStatus(request.getStatus());
        restaurantValidationService.validateStatusConsistency(request.getIsActive(), request.getStatus());

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

        restaurantMapper.markDeleted(
                restaurant,
                restaurantScopeService.currentUserId(authentication),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        restaurantRepository.save(restaurant);
    }

    // validates
    private UUID validateOwnerUser(UUID ownerUserId, UUID restaurantId) {
        if (ownerUserId == null) {
            return null;
        }

        User owner = userRepository.findByIdAndDeletedAtIsNull(ownerUserId)
                .orElseThrow(RestaurantOwnerNotFoundException::new);

        if (!owner.isActive()) {
            throw new AuthException("Owner user must be active", HttpStatus.BAD_REQUEST);
        }

        if (owner.getRestaurantId() != null && !Objects.equals(owner.getRestaurantId(), restaurantId)) {
            throw new AuthException("Owner user is already assigned to another restaurant", HttpStatus.BAD_REQUEST);
        }

        return owner.getId();
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

    private Sort.Direction resolveDirection(String direction) {
        try {
            return Sort.Direction.fromString(
                    NormalizationUtils.normalize(direction) == null ? "desc" : direction
            );
        } catch (IllegalArgumentException ex) {
            throw new AuthException("Invalid sort direction", HttpStatus.BAD_REQUEST);
        }
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
