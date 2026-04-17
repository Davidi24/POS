package pos.pos.unit.auth.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.auth.bootstrap.SuperAdminBootstrapRunner;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminBootstrapRunner")
class SuperAdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private PasswordService passwordService;

    private BootstrapSuperAdminProperties properties;
    private SuperAdminBootstrapRunner superAdminBootstrapRunner;

    private final Map<String, Permission> permissionsByCode = new HashMap<>();
    private final Map<String, Role> rolesByCode = new HashMap<>();
    private final Map<String, User> usersByEmail = new HashMap<>();
    private final Map<String, User> usersByUsername = new HashMap<>();
    private final Map<String, RolePermission> rolePermissionsByKey = new HashMap<>();
    private final Map<String, UserRole> userRolesByKey = new HashMap<>();

    @BeforeEach
    void setUp() {
        properties = new BootstrapSuperAdminProperties();
        superAdminBootstrapRunner = new SuperAdminBootstrapRunner(
                userRepository,
                roleRepository,
                userRoleRepository,
                permissionRepository,
                rolePermissionRepository,
                passwordService,
                properties
        );

        lenient().when(permissionRepository.findByCode(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(permissionsByCode.get(invocation.getArgument(0))));
        lenient().when(permissionRepository.save(any(Permission.class)))
                .thenAnswer(invocation -> {
                    Permission permission = invocation.getArgument(0);
                    if (permission.getId() == null) {
                        permission.setId(UUID.randomUUID());
                    }
                    permissionsByCode.put(permission.getCode(), permission);
                    return permission;
                });

        lenient().when(roleRepository.findByCode(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(rolesByCode.get(invocation.getArgument(0))));
        lenient().when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> {
                    Role role = invocation.getArgument(0);
                    if (role.getId() == null) {
                        role.setId(UUID.randomUUID());
                    }
                    rolesByCode.put(role.getCode(), role);
                    return role;
                });

        lenient().when(rolePermissionRepository.existsByRoleIdAndPermissionId(any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> rolePermissionsByKey.containsKey(
                        invocation.getArgument(0).toString() + ":" + invocation.getArgument(1).toString()
                ));
        lenient().when(rolePermissionRepository.save(any(RolePermission.class)))
                .thenAnswer(invocation -> {
                    RolePermission rolePermission = invocation.getArgument(0);
                    if (rolePermission.getId() == null) {
                        rolePermission.setId(UUID.randomUUID());
                    }
                    rolePermissionsByKey.put(rolePermission.getRoleId() + ":" + rolePermission.getPermissionId(), rolePermission);
                    return rolePermission;
                });

        lenient().when(userRepository.findByEmailAndDeletedAtIsNull(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(usersByEmail.get(invocation.getArgument(0))));
        lenient().when(userRepository.findByUsernameAndDeletedAtIsNull(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(usersByUsername.get(invocation.getArgument(0))));
        lenient().when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    if (user.getId() == null) {
                        user.setId(UUID.randomUUID());
                    }
                    usersByEmail.put(user.getEmail(), user);
                    if (user.getUsername() != null) {
                        usersByUsername.put(user.getUsername(), user);
                    }
                    return user;
                });

        lenient().when(userRoleRepository.existsByUserIdAndRoleId(any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> userRolesByKey.containsKey(
                        invocation.getArgument(0).toString() + ":" + invocation.getArgument(1).toString()
                ));
        lenient().when(userRoleRepository.save(any(UserRole.class)))
                .thenAnswer(invocation -> {
                    UserRole userRole = invocation.getArgument(0);
                    if (userRole.getId() == null) {
                        userRole.setId(UUID.randomUUID());
                    }
                    userRolesByKey.put(userRole.getUserId() + ":" + userRole.getRoleId(), userRole);
                    return userRole;
                });
    }

    @Test
    @DisplayName("Should seed roles and permissions but skip user creation when bootstrap is disabled")
    void shouldSeedButSkipUserCreationWhenDisabled() {
        properties.setEnabled(false);

        superAdminBootstrapRunner.run();

        assertThat(permissionsByCode).hasSize(AppPermission.values().length);
        assertThat(rolesByCode).hasSize(AppRole.values().length);
        assertThat(usersByEmail).isEmpty();
        assertThat(userRolesByKey).isEmpty();

        Role superAdminRole = rolesByCode.get(AppRole.SUPER_ADMIN.name());
        assertThat(superAdminRole).isNotNull();
        assertThat(superAdminRole.isSystem()).isTrue();
        assertThat(superAdminRole.isActive()).isTrue();
        assertThat(superAdminRole.isProtectedRole()).isTrue();
        assertThat(superAdminRole.isAssignable()).isFalse();
        assertThat(superAdminRole.getRank()).isEqualTo(AppRole.SUPER_ADMIN.rank());

        verifyNoInteractions(passwordService);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should create and assign a normalized super admin when bootstrap is enabled")
    void shouldCreateSuperAdminWhenEnabled() {
        properties.setEnabled(true);
        properties.setEmail("  Admin@POS.local  ");
        properties.setUsername("  Admin  ");
        properties.setPassword("StrongPass123!");
        properties.setFirstName("Super");
        properties.setLastName("Admin");

        when(passwordService.hash("StrongPass123!")).thenReturn("hashed-password");

        superAdminBootstrapRunner.run();

        User createdUser = usersByEmail.get("admin@pos.local");
        assertThat(createdUser).isNotNull();
        assertThat(createdUser.getUsername()).isEqualTo("admin");
        assertThat(createdUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(createdUser.getFirstName()).isEqualTo("Super");
        assertThat(createdUser.getLastName()).isEqualTo("Admin");
        assertThat(createdUser.isActive()).isTrue();
        assertThat(createdUser.isEmailVerified()).isTrue();

        Role superAdminRole = rolesByCode.get(AppRole.SUPER_ADMIN.name());
        assertThat(userRolesByKey).containsKey(createdUser.getId() + ":" + superAdminRole.getId());
        verify(passwordService).hash("StrongPass123!");
    }

    @Test
    @DisplayName("Should reject bootstrap when configured username belongs to another account")
    void shouldRejectWhenUsernameBelongsToDifferentUser() {
        properties.setEnabled(true);
        properties.setEmail("admin@pos.local");
        properties.setUsername("admin");
        properties.setPassword("StrongPass123!");

        when(passwordService.hash("StrongPass123!")).thenReturn("hashed-password");
        when(userRepository.findByUsernameAndDeletedAtIsNull("admin"))
                .thenReturn(Optional.of(User.builder()
                        .id(UUID.randomUUID())
                        .email("other@pos.local")
                        .username("admin")
                        .build()));

        assertThatThrownBy(() -> superAdminBootstrapRunner.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Super admin username is already used by another account");
    }

    @Test
    @DisplayName("Should reuse an existing super admin by email and backfill username without rehashing password")
    void shouldReuseExistingUserByEmail() {
        UUID existingUserId = UUID.randomUUID();
        properties.setEnabled(true);
        properties.setEmail("admin@pos.local");
        properties.setUsername("admin");
        properties.setPassword("StrongPass123!");

        User existingUser = User.builder()
                .id(existingUserId)
                .email("admin@pos.local")
                .username(null)
                .isActive(true)
                .emailVerified(true)
                .build();
        usersByEmail.put("admin@pos.local", existingUser);

        superAdminBootstrapRunner.run();

        assertThat(existingUser.getUsername()).isEqualTo("admin");
        Role superAdminRole = rolesByCode.get(AppRole.SUPER_ADMIN.name());
        assertThat(userRolesByKey).containsKey(existingUserId + ":" + superAdminRole.getId());
        verify(passwordService, never()).hash(anyString());
    }
}
