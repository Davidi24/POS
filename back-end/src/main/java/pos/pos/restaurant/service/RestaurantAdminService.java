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
import pos.pos.exception.restaurant.RestaurantAccessNotAllowedException;
import pos.pos.exception.restaurant.RestaurantCodeAlreadyExistsException;
import pos.pos.exception.restaurant.RestaurantCreationNotAllowedException;
import pos.pos.exception.restaurant.RestaurantDeletionNotAllowedException;
import pos.pos.exception.restaurant.RestaurantManagementNotAllowedException;
import pos.pos.exception.restaurant.RestaurantNotFoundException;
import pos.pos.exception.restaurant.RestaurantOwnerNotFoundException;
import pos.pos.exception.restaurant.RestaurantOwnershipChangeNotAllowedException;
import pos.pos.exception.restaurant.RestaurantSlugAlreadyExistsException;
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

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final RestaurantMapper restaurantMapper;
    private final RoleHierarchyService roleHierarchyService;

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
        User actor = superAdmin ? null : currentActor(authentication);

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

    @Transactional
    public RestaurantResponse createRestaurant(Authentication authentication, CreateRestaurantRequest request) {
        assertCanCreateRestaurant(authentication);

        UUID actorId = roleHierarchyService.currentUserId(authentication);
        UUID ownerUserId = validateOwnerUser(request.getOwnerUserId(), null);
        boolean isActive = request.getIsActive() == null || request.getIsActive();
        RestaurantStatus status = request.getStatus() == null ? RestaurantStatus.ACTIVE : request.getStatus();
        validateStatusConsistency(isActive, status);
        validateTimezone(request.getTimezone());

        String normalizedCode = normalizeCode(request.getCode(), request.getName());
        String normalizedSlug = normalizeSlug(request.getSlug(), request.getName());
        assertUniqueCode(normalizedCode, null);
        assertUniqueSlug(normalizedSlug, null);

        Restaurant restaurant = new Restaurant();
        restaurant.setName(request.getName());
        restaurant.setLegalName(request.getLegalName());
        restaurant.setCode(normalizedCode);
        restaurant.setSlug(normalizedSlug);
        restaurant.setDescription(request.getDescription());
        restaurant.setEmail(request.getEmail());
        restaurant.setPhone(request.getPhone());
        restaurant.setWebsite(request.getWebsite());
        restaurant.setCurrency(request.getCurrency());
        restaurant.setTimezone(request.getTimezone());
        restaurant.setOwnerId(ownerUserId);
        restaurant.setActive(isActive);
        restaurant.setStatus(status);
        restaurant.setCreatedBy(actorId);
        restaurant.setUpdatedBy(actorId);

        restaurantRepository.save(restaurant);
        return restaurantMapper.toResponse(restaurant);
    }

    public RestaurantResponse getRestaurant(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = findExistingRestaurant(restaurantId);
        assertCanAccessRestaurant(authentication, restaurant);
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateRestaurant(
            Authentication authentication,
            UUID restaurantId,
            UpdateRestaurantRequest request
    ) {
        Restaurant restaurant = findExistingRestaurant(restaurantId);
        assertCanManageRestaurant(authentication, restaurant);

        boolean superAdmin = roleHierarchyService.isSuperAdmin(authentication);
        if (!superAdmin && !Objects.equals(restaurant.getOwnerId(), request.getOwnerUserId())) {
            throw new RestaurantOwnershipChangeNotAllowedException();
        }

        UUID ownerUserId = validateOwnerUser(request.getOwnerUserId(), restaurant.getId());
        validateStatusConsistency(request.getIsActive(), request.getStatus());
        validateTimezone(request.getTimezone());

        String normalizedCode = normalizeCode(request.getCode(), request.getName());
        String normalizedSlug = normalizeSlug(request.getSlug(), request.getName());
        assertUniqueCode(normalizedCode, restaurant.getId());
        assertUniqueSlug(normalizedSlug, restaurant.getId());

        restaurant.setName(request.getName());
        restaurant.setLegalName(request.getLegalName());
        restaurant.setCode(normalizedCode);
        restaurant.setSlug(normalizedSlug);
        restaurant.setDescription(request.getDescription());
        restaurant.setEmail(request.getEmail());
        restaurant.setPhone(request.getPhone());
        restaurant.setWebsite(request.getWebsite());
        restaurant.setCurrency(request.getCurrency());
        restaurant.setTimezone(request.getTimezone());
        restaurant.setOwnerId(ownerUserId);
        restaurant.setActive(request.getIsActive());
        restaurant.setStatus(request.getStatus());
        restaurant.setUpdatedBy(roleHierarchyService.currentUserId(authentication));

        restaurantRepository.save(restaurant);
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateRestaurantStatus(
            Authentication authentication,
            UUID restaurantId,
            UpdateRestaurantStatusRequest request
    ) {
        Restaurant restaurant = findExistingRestaurant(restaurantId);
        assertCanManageRestaurant(authentication, restaurant);
        validateStatusConsistency(request.getIsActive(), request.getStatus());

        restaurant.setActive(request.getIsActive());
        restaurant.setStatus(request.getStatus());
        restaurant.setUpdatedBy(roleHierarchyService.currentUserId(authentication));

        restaurantRepository.save(restaurant);
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    public void deleteRestaurant(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = findExistingRestaurant(restaurantId);
        assertCanDeleteRestaurant(authentication, restaurant);

        restaurant.setActive(false);
        restaurant.setStatus(RestaurantStatus.ARCHIVED);
        restaurant.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        restaurant.setUpdatedBy(roleHierarchyService.currentUserId(authentication));
        restaurantRepository.save(restaurant);
    }

    private Restaurant findExistingRestaurant(UUID restaurantId) {
        return restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(RestaurantNotFoundException::new);
    }

    private User currentActor(Authentication authentication) {
        return userRepository.findByIdAndDeletedAtIsNull(roleHierarchyService.currentUserId(authentication))
                .orElseThrow(RestaurantManagementNotAllowedException::new);
    }

    private void assertCanCreateRestaurant(Authentication authentication) {
        if (!roleHierarchyService.isSuperAdmin(authentication)) {
            throw new RestaurantCreationNotAllowedException();
        }
    }

    private void assertCanDeleteRestaurant(Authentication authentication, Restaurant restaurant) {
        if (!roleHierarchyService.isSuperAdmin(authentication)) {
            throw new RestaurantDeletionNotAllowedException();
        }
        assertCanManageRestaurant(authentication, restaurant);
    }

    private void assertCanAccessRestaurant(Authentication authentication, Restaurant restaurant) {
        if (roleHierarchyService.isSuperAdmin(authentication)) {
            return;
        }

        User actor = currentActor(authentication);
        if (!Objects.equals(actor.getRestaurantId(), restaurant.getId())
                && !Objects.equals(actor.getId(), restaurant.getOwnerId())) {
            throw new RestaurantAccessNotAllowedException();
        }
    }

    private void assertCanManageRestaurant(Authentication authentication, Restaurant restaurant) {
        if (roleHierarchyService.isSuperAdmin(authentication)) {
            return;
        }

        User actor = currentActor(authentication);
        if (!Objects.equals(actor.getRestaurantId(), restaurant.getId())
                && !Objects.equals(actor.getId(), restaurant.getOwnerId())) {
            throw new RestaurantManagementNotAllowedException();
        }
    }

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

    private void validateStatusConsistency(Boolean isActive, RestaurantStatus status) {
        if (Boolean.TRUE.equals(isActive) && status != RestaurantStatus.ACTIVE) {
            throw new AuthException("Non-active restaurant statuses must have isActive=false", HttpStatus.BAD_REQUEST);
        }

        if (Boolean.FALSE.equals(isActive) && status == RestaurantStatus.ACTIVE) {
            throw new AuthException("ACTIVE restaurants must have isActive=true", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(NormalizationUtils.normalize(timezone));
        } catch (DateTimeException | NullPointerException ex) {
            throw new AuthException("timezone must be a valid IANA identifier", HttpStatus.BAD_REQUEST);
        }
    }

    private void assertUniqueCode(String code, UUID restaurantId) {
        boolean exists = restaurantId == null
                ? restaurantRepository.existsByCodeAndDeletedAtIsNull(code)
                : restaurantRepository.existsByCodeAndIdNotAndDeletedAtIsNull(code, restaurantId);
        if (exists) {
            throw new RestaurantCodeAlreadyExistsException();
        }
    }

    private void assertUniqueSlug(String slug, UUID restaurantId) {
        boolean exists = restaurantId == null
                ? restaurantRepository.existsBySlugAndDeletedAtIsNull(slug)
                : restaurantRepository.existsBySlugAndIdNotAndDeletedAtIsNull(slug, restaurantId);
        if (exists) {
            throw new RestaurantSlugAlreadyExistsException();
        }
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

    private String normalizeCode(String code, String fallbackName) {
        String source = NormalizationUtils.normalize(code) == null ? fallbackName : code;
        String normalized = NormalizationUtils.normalizeUpper(source);
        if (normalized == null) {
            throw new AuthException("code is required", HttpStatus.BAD_REQUEST);
        }

        String sanitized = normalized
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        if (sanitized.isEmpty()) {
            throw new AuthException("code is required", HttpStatus.BAD_REQUEST);
        }

        return sanitized;
    }

    private String normalizeSlug(String slug, String fallbackName) {
        String source = NormalizationUtils.normalize(slug) == null ? fallbackName : slug;
        String normalized = NormalizationUtils.normalizeLower(source);
        if (normalized == null) {
            throw new AuthException("slug is required", HttpStatus.BAD_REQUEST);
        }

        String sanitized = normalized
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (sanitized.isEmpty()) {
            throw new AuthException("slug is required", HttpStatus.BAD_REQUEST);
        }

        return sanitized;
    }
}
