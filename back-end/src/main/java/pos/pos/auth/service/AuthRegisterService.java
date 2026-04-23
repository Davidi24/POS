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

        User user = User.builder()
                .email(identity.email())
                .username(identity.username())
                .passwordHash(passwordService.hash(request.getTemporaryPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
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

    private AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
