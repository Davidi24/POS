package pos.pos.auth.bootstrap;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pos.pos.config.properties.BootstrapSuperAdminProperties;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

@Component
@RequiredArgsConstructor
public class SuperAdminBootstrapRunner implements CommandLineRunner {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordService passwordService;
    private final BootstrapSuperAdminProperties properties;

    @Override
    public void run(String... args) {
        Role superAdminRole = getOrCreateSuperAdminRole();
        User superAdminUser = getOrCreateSuperAdminUser();
        assignRoleIfMissing(superAdminUser, superAdminRole);
    }

    private Role getOrCreateSuperAdminRole() {
        return roleRepository.findByName(SUPER_ADMIN_ROLE)
                .orElseGet(() -> roleRepository.save(
                        Role.builder()
                                .name(SUPER_ADMIN_ROLE)
                                .description("System super administrator")
                                .build()
                ));
    }

    private User getOrCreateSuperAdminUser() {
        return userRepository.findByEmail(properties.getEmail())
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(properties.getEmail())
                                .passwordHash(passwordService.hash(properties.getPassword()))
                                .firstName(properties.getFirstName())
                                .lastName(properties.getLastName())
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