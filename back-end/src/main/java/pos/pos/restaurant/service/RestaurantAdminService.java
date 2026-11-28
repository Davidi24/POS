package pos.pos.restaurant.service;

import com.github.f4b6a3.uuid.UuidCreator;
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
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.BranchStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final RestaurantClosureGuard closureGuard;
    private final pos.pos.restaurant.repository.RestaurantCardRepository restaurantCardRepository;
    private final RestaurantDeviceRetirement deviceRetirement;
    private final pos.pos.settings.service.SettingsAuditService auditService;

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
        requireKnownCurrency(request.getCurrency());

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

        if (restaurant.getId() == null) {
            restaurant.setId(UuidCreator.getTimeOrdered());
        }

        if (request.getOwner() != null) {
            User owner = restaurantOwnerProvisioningService.createAndInviteOwner(
                    request.getOwner(),
                    restaurant.getId(),
                    actorId,
                    restaurant.getName()
            );
            restaurant.setOwnerId(owner.getId());
        }
        // A new restaurant goes to the end of the restaurant list. The lock makes restaurants created at the same
        // moment wait for each other, so each gets its own position.
        restaurantCardRepository.lockDisplayOrder();
        restaurant.setDisplayOrder(restaurantCardRepository.nextDisplayOrder());
        // The id is set up front (the owner needs it), so save() merges and hands back the managed copy.
        Restaurant saved = restaurantRepository.save(restaurant);
        Restaurant managed = saved != null ? saved : restaurant;
        branchRepository.save(firstBranch(managed, request.getFirstBranchName(), actorId));

        return restaurantMapper.toResponse(managed);
    }

    private Branch firstBranch(Restaurant restaurant, String requestedName, UUID actorId) {
        String name = NormalizationUtils.normalize(requestedName);
        Branch branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setName(name == null ? "Main" : name);
        branch.setCode("MAIN");
        branch.setActive(true);
        branch.setStatus(BranchStatus.ACTIVE);
        branch.setCreatedBy(actorId);
        branch.setUpdatedBy(actorId);
        return branch;
    }

    // ISO 4217 codes only (EUR, ALL, USD...), so prices and receipts always show a real currency.
    private void requireKnownCurrency(String currency) {
        String code = NormalizationUtils.normalizeUpper(currency);
        try {
            pos.pos.restaurant.util.RestaurantCurrencyPrecision.scale(code);
        } catch (RuntimeException ex) {
            throw new AuthException("currency must be a 3-letter ISO code such as EUR, ALL or USD", HttpStatus.BAD_REQUEST);
        }
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
        assertAllowedChanges(scope, restaurant, request);

        restaurantValidationService.validateStatusTransition(restaurant.getStatus(), request.getStatus());
        restaurantPolicy.assertCanChangeOwner(scope, restaurant, request.getOwnerUserId());
        if (!Objects.equals(restaurant.getOwnerId(), request.getOwnerUserId())) {
            throw new AuthException("Use Transfer ownership to give the restaurant to someone else, or Add owner when it has none.", HttpStatus.CONFLICT);
        }
        requireContactDetails(request.getEmail(), request.getPhone());
        validateStatusFields(request.getIsActive(), request.getStatus());
        closureGuard.restaurant(restaurantId,request.getStatus());
        UUID ownerUserId = restaurantValidationService.validateOwnerUser(
                request.getOwnerUserId(),
                restaurant.getId(),
                request.getStatus()
        );
        var normalizedFields = restaurantValidationService.normalizeAndValidateFields(
                request.getCode(),
                request.getSlug(),
                request.getName(),
                request.getTimezone(),
                restaurant.getId()
        );

        if (request.getExpectedVersion() == null || request.getExpectedVersion() != restaurant.getVersion()) {
            throw new AuthException("These details have changed, or the edit version is missing. Refresh and review your changes before saving.", HttpStatus.CONFLICT);
        }
        restaurantMapper.updateEntity(
                restaurant,
                request,
                normalizedFields.code(),
                normalizedFields.slug(),
                ownerUserId,
                restaurantScopeService.currentUserId(authentication)
        );

        restaurantRepository.saveAndFlush(restaurant);
        retireDevicesIfArchived(restaurant, request.getStatus(), restaurantScopeService.currentUserId(authentication));
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateRestaurantStatus(
            Authentication authentication,
            UUID restaurantId,
            UpdateRestaurantStatusRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId,
                "Only the Super Admin can open, suspend or close a restaurant");
        restaurantValidationService.validateStatusTransition(restaurant.getStatus(), request.getStatus());
        validateStatusFields(request.getIsActive(), request.getStatus());
        closureGuard.restaurant(restaurantId,request.getStatus());

        restaurantMapper.updateStatus(
                restaurant,
                request.getIsActive(),
                request.getStatus(),
                restaurantScopeService.currentUserId(authentication)
        );

        restaurantRepository.save(restaurant);
        retireDevicesIfArchived(restaurant, request.getStatus(), restaurantScopeService.currentUserId(authentication));
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    public void deleteRestaurant(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.assertCanDeleteRestaurant(authentication);
        Restaurant restaurant = restaurantScopeService.requireExistingRestaurant(restaurantId);
        closureGuard.restaurant(restaurantId,RestaurantStatus.ARCHIVED);

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Devices go first: their assignments point at the branches archived next.
        int devices = deviceRetirement.retireRestaurant(restaurantId, actorId, now);
        branchRepository.softDeleteAllByRestaurantId(restaurantId, now, actorId);

        restaurantMapper.markDeleted(restaurant, actorId, now);
        restaurantRepository.save(restaurant);
        auditService.log(restaurant, null, "RESTAURANT", restaurantId, "ARCHIVE",
                "Archived the restaurant; devices retired=" + devices + ". Sales, payments and other history retained.", actorId);
    }

    // A restaurant closed for good (ARCHIVED) keeps no device that can still sign in.
    private void retireDevicesIfArchived(Restaurant restaurant, RestaurantStatus status, UUID actorId) {
        if (status != RestaurantStatus.ARCHIVED) {
            return;
        }
        int devices = deviceRetirement.retireRestaurant(restaurant.getId(), actorId, OffsetDateTime.now(ZoneOffset.UTC));
        auditService.log(restaurant, null, "RESTAURANT", restaurant.getId(), "ARCHIVE",
                "Closed the restaurant for good; devices retired=" + devices + ".", actorId);
    }

    // Guests see both on the booking pages and in their emails, so an update may never leave either empty.
    private void requireContactDetails(String email, String phone) {
        if (NormalizationUtils.normalize(email) == null || NormalizationUtils.normalize(phone) == null) {
            throw new AuthException("The restaurant needs an email and a phone number", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Owners keep their restaurant's details for guests up to date (description, email, phone, website). Who it is
     * (name, code, legal name), whether it's open and who owns it are for super admins. Currency and time zone change
     * only through their own steps (Change currency, Change time zone), which take care of what depends on them.
     */
    private void assertAllowedChanges(ActorScope scope, Restaurant restaurant, UpdateRestaurantRequest request) {
        if (!Objects.equals(NormalizationUtils.normalizeUpper(request.getCurrency()), NormalizationUtils.normalizeUpper(restaurant.getCurrency()))) {
            throw new AuthException("Use “Change currency” to change the currency, so prices are converted with it", HttpStatus.BAD_REQUEST);
        }
        if (!Objects.equals(RestaurantFieldNormalizer.normalizeTimezone(request.getTimezone()), restaurant.getTimezone())) {
            throw new AuthException("Use “Change time zone” to change the time zone, so you see what moves first", HttpStatus.BAD_REQUEST);
        }
        if (scope.superAdmin()) {
            return;
        }
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(NormalizationUtils.normalize(request.getName()), restaurant.getName())) changed.add("name");
        if (!Objects.equals(NormalizationUtils.normalize(request.getLegalName()), restaurant.getLegalName())) changed.add("legal name");
        if (!Objects.equals(RestaurantFieldNormalizer.normalizeCodeOrFallback(request.getCode(), request.getName()), restaurant.getCode())) changed.add("code");
        if (!Objects.equals(RestaurantFieldNormalizer.normalizeSlugOrFallback(request.getSlug(), request.getName()), restaurant.getSlug())) changed.add("web address");
        if (request.getStatus() != restaurant.getStatus() || !Objects.equals(request.getIsActive(), restaurant.isActive())) changed.add("status");
        if (!Objects.equals(request.getOwnerUserId(), restaurant.getOwnerId())) changed.add("owner");
        if (!changed.isEmpty()) {
            throw new AuthException("Only the Super Admin can change the restaurant's " + String.join(", ", changed), HttpStatus.FORBIDDEN);
        }
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
