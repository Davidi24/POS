package pos.pos.auth.bootstrap;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.config.properties.BootstrapSuperAdminProperties;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;
import pos.pos.utils.NormalizationUtils;

@Component
@RequiredArgsConstructor
public class SuperAdminBootstrapRunner implements CommandLineRunner {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final String SUPER_ADMIN_ROLE_NAME = "Super Admin";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordService passwordService;
    private final BootstrapSuperAdminProperties properties;

    @Override
    @Transactional
    public void run(String... args) {
        Role superAdminRole = getOrCreateSuperAdminRole();
        User superAdminUser = getOrCreateSuperAdminUser();
        assignRoleIfMissing(superAdminUser, superAdminRole);
    }

    private Role getOrCreateSuperAdminRole() {
        return roleRepository.findByCode(SUPER_ADMIN_ROLE_CODE)
                .orElseGet(() -> roleRepository.save(
                        Role.builder()
                                .code(SUPER_ADMIN_ROLE_CODE)
                                .name(SUPER_ADMIN_ROLE_NAME)
                                .description("System super administrator")
                                .isSystem(true)
                                .isActive(true)
                                .build()
                ));
    }

    private User getOrCreateSuperAdminUser() {
        String normalizedEmail = NormalizationUtils.normalizeLower(properties.getEmail());

        if (normalizedEmail == null) {
            throw new IllegalStateException("app.bootstrap.super-admin.email must not be blank");
        }

        return userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(normalizedEmail)
                                .passwordHash(passwordService.hash(properties.getPassword()))
                                .firstName(properties.getFirstName())
                                .lastName(properties.getLastName())
                                .status("ACTIVE")
                                .isActive(true)
                                .emailVerified(true)
                                .failedLoginAttempts(0)
                                .pinEnabled(false)
                                .pinAttempts(0)
                                .build()
                ));
    }

    private void assignRoleIfMissing(User user, Role role) {
        boolean alreadyAssigned = userRoleRepository.existsByUserIdAndRoleId(user.getId(), role.getId());

        if (alreadyAssigned) {
            return;
        }

        userRoleRepository.save(
                UserRole.builder()
                        .userId(user.getId())
                        .roleId(role.getId())
                        .build()
        );
    }
}
