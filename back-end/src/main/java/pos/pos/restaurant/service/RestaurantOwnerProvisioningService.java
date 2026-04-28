package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.service.PasswordResetService;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.role.RoleNotFoundException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.restaurant.dto.CreateRestaurantOwnerRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.security.rbac.AppRole;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;
import pos.pos.user.service.UserIdentityService;
import pos.pos.utils.NormalizationUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantOwnerProvisioningService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;
    private final PasswordResetService passwordResetService;
    private final UserMapper userMapper;
    private final UserIdentityService userIdentityService;

    public User createAndInviteOwner(
            CreateRestaurantOwnerRequest request,
            UUID restaurantId,
            UUID actorId,
            String restaurantName
    ) {
        UserIdentityService.NormalizedUserIdentity ownerIdentity = userIdentityService.normalizeAndAssertUnique(
                request.getEmail(),
                request.getUsername(),
                request.getPhone()
        );

        User owner = userMapper.toRestaurantOwner(
                request,
                ownerIdentity,
                passwordService.hash(generateUnusableInitialPassword()),
                restaurantId,
                actorId
        );

        persistOwner(owner, actorId);
        passwordResetService.issueRestaurantOwnerInvite(
                owner,
                request.getClientTarget(),
                NormalizationUtils.normalize(restaurantName)
        );
        return owner;
    }

    public User createAndInvitePendingOwner(Restaurant restaurant, UUID actorId, String restaurantName) {
        if (restaurant.getPendingOwnerEmail() == null
                || restaurant.getPendingOwnerUsername() == null
                || restaurant.getPendingOwnerFirstName() == null
                || restaurant.getPendingOwnerLastName() == null) {
            throw new AuthException(
                    "Pending owner details are missing for this restaurant registration",
                    HttpStatus.BAD_REQUEST
            );
        }

        UserIdentityService.NormalizedUserIdentity ownerIdentity = userIdentityService.normalizeAndAssertUnique(
                restaurant.getPendingOwnerEmail(),
                restaurant.getPendingOwnerUsername(),
                restaurant.getPendingOwnerPhone()
        );

        User owner = userMapper.toRestaurantOwner(
                restaurant,
                ownerIdentity,
                passwordService.hash(generateUnusableInitialPassword()),
                actorId
        );

        persistOwner(owner, actorId);
        passwordResetService.issueRestaurantOwnerInvite(
                owner,
                restaurant.getPendingOwnerClientTarget() == null
                        ? ClientLinkTarget.UNIVERSAL
                        : restaurant.getPendingOwnerClientTarget(),
                NormalizationUtils.normalize(restaurantName)
        );
        return owner;
    }

    private void persistOwner(User owner, UUID actorId) {
        Role ownerRole = roleRepository.findByCode(AppRole.OWNER.name())
                .filter(Role::isActive)
                .orElseThrow(RoleNotFoundException::new);

        userRepository.save(owner);
        userRoleRepository.save(UserRole.builder()
                .userId(owner.getId())
                .roleId(ownerRole.getId())
                .assignedBy(actorId)
                .build());
    }

    private String generateUnusableInitialPassword() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }
}
