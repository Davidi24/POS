package pos.pos.auth.bootstrap;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.config.properties.BootstrapSuperAdminProperties;
import pos.pos.role.entity.Permission;
import pos.pos.role.entity.Role;
import pos.pos.role.entity.RolePermission;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RolePermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.rbac.AppPermission;
import pos.pos.security.rbac.AppRole;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;
import pos.pos.utils.NormalizationUtils;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class SuperAdminBootstrapRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SuperAdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordService passwordService;
    private final BootstrapSuperAdminProperties properties;

    @Override
    @Transactional
    public void run(String... args) {
        seedPermissions();
        seedRoles();
        seedSuperAdminUser();
    }

    private void seedPermissions() {
        for (AppPermission perm : AppPermission.values()) {
            permissionRepository.findByCode(perm.name())
                    .orElseGet(() -> {
                        logger.info("Seeding permission: {}", perm.name());
                        return permissionRepository.save(
                                Permission.builder()
                                        .code(perm.name())
                                        .name(perm.displayName())
                                        .description(perm.description())
                                        .build()
                        );
                    });
        }
    }

    private void seedRoles() {
        for (AppRole appRole : AppRole.values()) {
            Role role = roleRepository.findByCode(appRole.name())
                    .orElseGet(() -> {
                        logger.info("Seeding role: {}", appRole.name());
                        return roleRepository.save(
                                Role.builder()
                                        .code(appRole.name())
                                        .name(appRole.displayName())
                                        .description(appRole.description())
                                        .build()
                        );
                    });

            role.setName(appRole.displayName());
            role.setDescription(appRole.description());
            role.setRank(appRole.rank());
            role.setSystem(true);
            role.setActive(true);
            role.setAssignable(appRole.assignable());
            role.setProtectedRole(appRole.protectedRole());
            roleRepository.save(role);

            assignPermissionsToRole(role, appRole.permissions());
        }
    }

    private void assignPermissionsToRole(Role role, Set<AppPermission> permissions) {
        for (AppPermission perm : permissions) {
            Permission permission = permissionRepository.findByCode(perm.name())
                    .orElseThrow(() -> new IllegalStateException(
                            "Permission not found during role seeding: " + perm.name()
                    ));

            if (!rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), permission.getId())) {
                rolePermissionRepository.save(
                        RolePermission.builder()
                                .roleId(role.getId())
                                .permissionId(permission.getId())
                                .build()
                );
            }
        }
    }

    private void seedSuperAdminUser() {
        String normalizedEmail = NormalizationUtils.normalizeLower(properties.getEmail());

        if (normalizedEmail == null) {
            throw new IllegalStateException("app.bootstrap.super-admin.email must not be blank");
        }

        Role superAdminRole = roleRepository.findByCode(AppRole.SUPER_ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role not found after seeding"));

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseGet(() -> {
                    logger.info("Creating super admin user: {}", normalizedEmail);
                    return userRepository.save(
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
                    );
                });

        if (!userRoleRepository.existsByUserIdAndRoleId(user.getId(), superAdminRole.getId())) {
            userRoleRepository.save(
                    UserRole.builder()
                            .userId(user.getId())
                            .roleId(superAdminRole.getId())
                            .build()
            );
        }
    }
}
