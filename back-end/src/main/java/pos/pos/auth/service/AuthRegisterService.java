package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.role.RoleNotFoundException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.security.service.PasswordService;
import pos.pos.user.dto.CreateUserRequest;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;
import pos.pos.user.service.UserIdentityService;

import java.util.List;
import java.util.UUID;

// checked
// tested
@Service
@RequiredArgsConstructor
public class AuthRegisterService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordService passwordService;
    private final UserMapper userMapper;
    private final RoleHierarchyService roleHierarchyService;
    private final EmailVerificationService emailVerificationService;
    private final UserIdentityService userIdentityService;
    private final pos.pos.restaurant.repository.RestaurantRepository restaurantRepository;
    private final pos.pos.restaurant.repository.BranchRepository branchRepository;

    @Transactional
    public UserResponse register(CreateUserRequest request, Authentication authentication) {
        UUID createdByUserId = currentUser(authentication).getId();

        UserIdentityService.NormalizedUserIdentity identity = userIdentityService.normalizeAndAssertUnique(
                request.getEmail(),
                request.getUsername(),
                request.getPhone()
        );

        Role role = roleRepository.findById(request.getRoleId())
                .filter(Role::isActive)
                .orElseThrow(RoleNotFoundException::new);

        roleHierarchyService.assertCanAssignRole(authentication, role);
        UUID restaurantId = resolveRestaurant(request, authentication);
        UUID defaultBranchId = resolveBranch(request.getDefaultBranchId(), restaurantId);

        User user = User.builder()
                .email(identity.email())
                .username(identity.username())
                .passwordHash(passwordService.hash(request.getTemporaryPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .restaurantId(restaurantId)
                .defaultBranchId(defaultBranchId)
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(false)
                .phoneVerified(false)
                .createdBy(createdByUserId)
                .updatedBy(createdByUserId)
                .build();

        userRepository.save(user);

        UserRole userRole = UserRole.builder()
                .userId(user.getId())
                .roleId(role.getId())
                .assignedBy(createdByUserId)
                .build();

        userRoleRepository.save(userRole);

        emailVerificationService.issueVerificationForUser(user, request.getClientTarget());

        return userMapper.toUserResponse(user, List.of(role.getCode()));
    }

    // Staff join the restaurant of whoever adds them; only a super admin picks one (or none, for system accounts).
    private UUID resolveRestaurant(CreateUserRequest request, Authentication authentication) {
        if (roleHierarchyService.isSuperAdmin(authentication)) {
            if (request.getRestaurantId() != null && restaurantRepository.findByIdAndDeletedAtIsNull(request.getRestaurantId()).isEmpty()) {
                throw new pos.pos.exception.auth.AuthException("Restaurant not found", org.springframework.http.HttpStatus.BAD_REQUEST);
            }
            return request.getRestaurantId();
        }
        UUID actorRestaurantId = roleHierarchyService.actorRestaurantId(authentication);
        if (actorRestaurantId == null) {
            throw new pos.pos.exception.auth.AuthException("Only staff of a restaurant can add people to it",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (request.getRestaurantId() != null && !request.getRestaurantId().equals(actorRestaurantId)) {
            throw new pos.pos.exception.auth.AuthException("You can only add people to your own restaurant",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return actorRestaurantId;
    }

    private UUID resolveBranch(UUID branchId, UUID restaurantId) {
        if (branchId == null) {
            return null;
        }
        if (restaurantId == null || branchRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(branchId, restaurantId).isEmpty()) {
            throw new pos.pos.exception.auth.AuthException("defaultBranchId must be a branch of the restaurant",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        return branchId;
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
