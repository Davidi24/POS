package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.EmailAlreadyExistsException;
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
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    @Transactional
    public UserResponse register(CreateUserRequest request, Authentication authentication) {
        UUID createdByUserId = currentUser(authentication).getId();

        String normalizedEmail = NormalizationUtils.normalizeLower(request.getEmail());

        if (userRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new EmailAlreadyExistsException();
        }

        Role role = roleRepository.findById(request.getRoleId())
                .filter(Role::isActive)
                .orElseThrow(RoleNotFoundException::new);

        roleHierarchyService.assertCanAssignRole(authentication, role);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordService.hash(request.getTemporaryPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .emailVerifiedAt(now)
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

        return userMapper.toUserResponse(user, List.of(role.getCode()));
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
