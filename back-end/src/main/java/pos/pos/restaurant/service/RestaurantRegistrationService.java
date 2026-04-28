package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.restaurant.RestaurantNotFoundException;
import pos.pos.exception.restaurant.RestaurantRegistrationNotPendingException;
import pos.pos.exception.restaurant.RestaurantReviewNotAllowedException;
import pos.pos.restaurant.dto.RestaurantRegistrationStatusResponse;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.RestaurantRegistrationRequest;
import pos.pos.restaurant.dto.ReviewRestaurantRegistrationRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantRegistrationDecision;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.mapper.RestaurantMapper;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.service.UserIdentityService;

import java.util.UUID;

//checked
@Service
@RequiredArgsConstructor
public class RestaurantRegistrationService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final RestaurantValidationService restaurantValidationService;
    private final RestaurantOwnerProvisioningService restaurantOwnerProvisioningService;
    private final UserIdentityService userIdentityService;
    private final RoleHierarchyService roleHierarchyService;

    @Transactional(readOnly = true)
    public RestaurantRegistrationStatusResponse getRegistrationStatus(UUID restaurantId) {
        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(RestaurantNotFoundException::new);
        return new RestaurantRegistrationStatusResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getStatus(),
                restaurant.getRejectionReason(),
                restaurant.getCreatedAt()
        );
    }

    // Called when a restaurant owner submits a registration form (no auth required).
    // Validates that the owner's email/username/phone are not already taken, auto-generates a unique code+slug
    // from the restaurant name, saves the restaurant with status=PENDING, and returns it without creating the owner user yet.
    // The owner account is only created after an admin approves the registration.
    @Transactional
    public RestaurantResponse registerRestaurant(RestaurantRegistrationRequest request) {
        userIdentityService.normalizeAndAssertUnique(
                request.getOwner().getEmail(),
                request.getOwner().getUsername(),
                request.getOwner().getPhone()
        );

        RestaurantValidationService.NormalizedRestaurantFields normalizedFields =
                restaurantValidationService.normalizeAndGenerateUniqueRegistrationFields(
                        request.getName(),
                        request.getTimezone()
                );

        Restaurant restaurant = restaurantMapper.toPendingRegistrationEntity(
                request,
                normalizedFields.code(),
                normalizedFields.slug()
        );

        restaurantRepository.save(restaurant);
        return restaurantMapper.toResponse(restaurant);
    }

    // Called by a SuperAdmin to approve or reject a PENDING restaurant registration.
    // On REJECT: marks the restaurant as rejected — no user account is created.
    // On APPROVE: creates the owner user account, sends them an invitation email with a temp password,
    // and marks the restaurant as active and linked to the new owner.
    @Transactional
    public RestaurantResponse reviewRegistration(
            Authentication authentication,
            UUID restaurantId,
            ReviewRestaurantRegistrationRequest request
    ) {
        if (!roleHierarchyService.isSuperAdmin(authentication)) {
            throw new RestaurantReviewNotAllowedException();
        }

        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(RestaurantNotFoundException::new);

        if (restaurant.getStatus() != RestaurantStatus.PENDING) {
            throw new RestaurantRegistrationNotPendingException();
        }

        UUID actorId = roleHierarchyService.currentUserId(authentication);
        // if the superadmin rejects then it will set restaurant status rejected
        if (request.getDecision() == RestaurantRegistrationDecision.REJECT) {
            restaurantMapper.markRegistrationRejected(restaurant, request.getRejectionReason(), actorId);
            restaurantRepository.save(restaurant);
            return restaurantMapper.toResponse(restaurant);
        }

        // create the owner if true
        var owner = restaurantOwnerProvisioningService.createAndInvitePendingOwner(
                restaurant,
                actorId,
                restaurant.getName()
        );
        restaurantMapper.markRegistrationApproved(restaurant, owner.getId(), actorId);
        restaurantRepository.save(restaurant);
        return restaurantMapper.toResponse(restaurant);
    }
}
