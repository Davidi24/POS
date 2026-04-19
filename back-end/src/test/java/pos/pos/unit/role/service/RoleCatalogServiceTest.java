package pos.pos.unit.role.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.role.RoleNotFoundException;
import pos.pos.role.dto.PermissionResponse;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.entity.Permission;
import pos.pos.role.entity.Role;
import pos.pos.role.entity.RolePermission;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RolePermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.role.service.RoleCatalogService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.RoleHierarchyService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleCatalogService")
class RoleCatalogServiceTest {

    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID PERMISSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private RoleHierarchyService roleHierarchyService;

    @InjectMocks
    private RoleCatalogService roleCatalogService;

    @Test
    @DisplayName("Should map active roles to API responses")
    void shouldMapActiveRolesToApiResponses() {
        Role manager = role("MANAGER", "Manager", 20_000L);
        Role waiter = role("WAITER", "Waiter", 10_000L);

        when(roleRepository.findByIsActiveTrueOrderByRankDescNameAsc()).thenReturn(List.of(manager, waiter));

        List<RoleResponse> responses = roleCatalogService.getRoles();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getCode()).isEqualTo("MANAGER");
        assertThat(responses.get(1).getCode()).isEqualTo("WAITER");
        verify(roleRepository).findByIsActiveTrueOrderByRankDescNameAsc();
    }

    @Test
    @DisplayName("Should return one role when it exists")
    void shouldReturnOneRoleWhenItExists() {
        Role role = role("ADMIN", "Admin", 30_000L);

        when(roleRepository.findByIdAndDeletedAtIsNull(ROLE_ID)).thenReturn(Optional.of(role));

        RoleResponse response = roleCatalogService.getRole(ROLE_ID);

        assertThat(response.getId()).isEqualTo(ROLE_ID);
        assertThat(response.getCode()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Should reject missing roles")
    void shouldRejectMissingRoles() {
        when(roleRepository.findByIdAndDeletedAtIsNull(ROLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleCatalogService.getRole(ROLE_ID))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    @DisplayName("Should map all permissions including permission code")
    void shouldMapAllPermissionsIncludingCode() {
        Permission readUsers = permission("USERS_READ", "View Users");
        Permission updateUsers = permission("USERS_UPDATE", "Update Users");

        when(permissionRepository.findAll(any(Sort.class))).thenReturn(List.of(readUsers, updateUsers));

        List<PermissionResponse> responses = roleCatalogService.getPermissions();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getCode()).isEqualTo("USERS_READ");
        assertThat(responses.get(1).getCode()).isEqualTo("USERS_UPDATE");
    }

    @Test
    @DisplayName("Should return sorted permissions for one role")
    void shouldReturnSortedPermissionsForOneRole() {
        Role role = role("ADMIN", "Admin", 30_000L);
        Permission deleteUsers = permission(UUID.fromString("00000000-0000-0000-0000-000000000203"), "USERS_DELETE", "Delete Users");
        Permission createUsers = permission(PERMISSION_ID, "USERS_CREATE", "Create Users");

        when(roleRepository.findByIdAndDeletedAtIsNull(ROLE_ID)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of(
                assignment(ROLE_ID, deleteUsers.getId()),
                assignment(ROLE_ID, createUsers.getId())
        ));
        when(permissionRepository.findAllById(List.of(deleteUsers.getId(), createUsers.getId())))
                .thenReturn(List.of(deleteUsers, createUsers));

        List<PermissionResponse> responses = roleCatalogService.getRolePermissions(ROLE_ID);

        assertThat(responses).extracting(PermissionResponse::getCode)
                .containsExactly("USERS_CREATE", "USERS_DELETE");
    }

    @Test
    @DisplayName("Should map assignable roles to API responses")
    void shouldMapAssignableRolesToResponses() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(UUID.randomUUID())
                        .email("manager@pos.local")
                        .username("manager")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
        Role manager = role("MANAGER", "Manager", 20_000L);
        Role waiter = role("WAITER", "Waiter", 10_000L);

        when(roleHierarchyService.getAssignableRoles(authentication)).thenReturn(List.of(manager, waiter));

        List<RoleResponse> responses = roleCatalogService.getAssignableRoles(authentication);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getCode()).isEqualTo("MANAGER");
        assertThat(responses.get(1).getCode()).isEqualTo("WAITER");
        verify(roleHierarchyService).getAssignableRoles(authentication);
    }

    @Test
    @DisplayName("Should map system roles separately")
    void shouldMapSystemRolesSeparately() {
        Role owner = role("OWNER", "Owner", 50_000L);
        owner.setSystem(true);

        when(roleRepository.findActiveSystemRoles()).thenReturn(List.of(owner));

        List<RoleResponse> responses = roleCatalogService.getSystemRoles();

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getCode()).isEqualTo("OWNER");
            assertThat(response.getIsSystem()).isTrue();
        });
    }

    private Role role(String code, String name, long rank) {
        return Role.builder()
                .id(ROLE_ID)
                .code(code)
                .name(name)
                .description(name + " role")
                .rank(rank)
                .isActive(true)
                .assignable(true)
                .protectedRole(false)
                .build();
    }

    private Permission permission(String code, String name) {
        return permission(PERMISSION_ID, code, name);
    }

    private Permission permission(UUID id, String code, String name) {
        return Permission.builder()
                .id(id)
                .code(code)
                .name(name)
                .description(name + " permission")
                .build();
    }

    private RolePermission assignment(UUID roleId, UUID permissionId) {
        return RolePermission.builder()
                .id(UUID.randomUUID())
                .roleId(roleId)
                .permissionId(permissionId)
                .build();
    }
}
