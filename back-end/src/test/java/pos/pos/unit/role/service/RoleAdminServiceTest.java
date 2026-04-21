package pos.pos.unit.role.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.role.PermissionAssignmentNotAllowedException;
import pos.pos.exception.role.PermissionNotFoundException;
import pos.pos.exception.role.RoleCodeAlreadyExistsException;
import pos.pos.role.dto.CloneRoleRequest;
import pos.pos.role.dto.CreateRoleRequest;
import pos.pos.role.dto.ReplaceRolePermissionsRequest;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.dto.UpdateRoleRequest;
import pos.pos.role.dto.UpdateRoleStatusRequest;
import pos.pos.role.entity.Permission;
import pos.pos.role.entity.Role;
import pos.pos.role.entity.RolePermission;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RolePermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.role.service.RoleAdminService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.RoleHierarchyService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleAdminService")
class RoleAdminServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000221");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000222");
    private static final UUID CLONED_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000223");
    private static final UUID PERMISSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000224");

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private RoleHierarchyService roleHierarchyService;

    @InjectMocks
    private RoleAdminService roleAdminService;

    @Test
    @DisplayName("Should create a custom role with a derived code and actor-based rank")
    void shouldCreateCustomRoleWithDerivedCodeAndActorBasedRank() {
        Authentication authentication = authentication("ROLES_CREATE");
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("Floor Supervisor")
                .description("Manages floor operations")
                .build();

        when(roleHierarchyService.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(roleHierarchyService.highestActiveRank(ACTOR_ID)).thenReturn(30_000L);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role savedRole = invocation.getArgument(0);
            savedRole.setId(ROLE_ID);
            return savedRole;
        });

        RoleResponse response = roleAdminService.createRole(authentication, request);

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());
        Role savedRole = roleCaptor.getValue();

        assertThat(savedRole.getCode()).isEqualTo("FLOOR_SUPERVISOR");
        assertThat(savedRole.getName()).isEqualTo("Floor Supervisor");
        assertThat(savedRole.getRank()).isEqualTo(29_999L);
        assertThat(savedRole.isSystem()).isFalse();
        assertThat(savedRole.isAssignable()).isTrue();
        assertThat(response.getId()).isEqualTo(ROLE_ID);
    }

    @Test
    @DisplayName("Should reject create requests when the derived code already exists")
    void shouldRejectCreateRequestsWhenDerivedCodeAlreadyExists() {
        Authentication authentication = authentication("ROLES_CREATE");
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("Floor Supervisor")
                .description("Manages floor operations")
                .build();

        when(roleRepository.existsByCodeAndDeletedAtIsNull("FLOOR_SUPERVISOR")).thenReturn(true);

        assertThatThrownBy(() -> roleAdminService.createRole(authentication, request))
                .isInstanceOf(RoleCodeAlreadyExistsException.class);

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    @DisplayName("Should update a custom role when the actor can manage it")
    void shouldUpdateCustomRoleWhenActorCanManageIt() {
        Authentication authentication = authentication("ROLES_UPDATE");
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .name("Senior Manager")
                .description("Updated description")
                .build();
        Role role = customRole();

        when(roleRepository.findByIdAndDeletedAtIsNull(ROLE_ID)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);

        RoleResponse response = roleAdminService.updateRole(authentication, ROLE_ID, request);

        assertThat(role.getName()).isEqualTo("Senior Manager");
        assertThat(role.getDescription()).isEqualTo("Updated description");
        assertThat(response.getName()).isEqualTo("Senior Manager");
        verify(roleHierarchyService).assertCanManageRole(authentication, role);
    }

    @Test
    @DisplayName("Should reject updates to system roles")
    void shouldRejectUpdatesToSystemRoles() {
        Authentication authentication = authentication("ROLES_UPDATE");
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .name("Owner")
                .description("Updated")
                .build();
        Role role = customRole();
        role.setSystem(true);

        when(roleRepository.findByIdAndDeletedAtIsNull(ROLE_ID)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> roleAdminService.updateRole(authentication, ROLE_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("System roles cannot be modified");
    }

    @Test
    @DisplayName("Should reject permission replacement when one permission id is unknown")
    void shouldRejectPermissionReplacementWhenPermissionIsUnknown() {
        Authentication authentication = authentication("ROLES_ASSIGN_PERMISSIONS");
        ReplaceRolePermissionsRequest request = new ReplaceRolePermissionsRequest();
        request.setPermissionIds(Set.of(PERMISSION_ID));

        when(roleRepository.findByIdAndDeletedAtIsNull(ROLE_ID)).thenReturn(Optional.of(customRole()));
        when(permissionRepository.findAllById(Set.of(PERMISSION_ID))).thenReturn(List.of());

        assertThatThrownBy(() -> roleAdminService.replaceRolePermissions(authentication, ROLE_ID, request))
                .isInstanceOf(PermissionNotFoundException.class);
    }

    @Test
    @DisplayName("Should reject permission replacement when the actor lacks one requested permission")
    void shouldRejectPermissionReplacementWhenActorLacksPermission() {
        Authentication authentication = authentication("ROLES_ASSIGN_PERMISSIONS");
        ReplaceRolePermissionsRequest request = new ReplaceRolePermissionsRequest();
        request.setPermissionIds(Set.of(PERMISSION_ID));
        Permission permission = permission("USERS_DELETE");

        when(roleRepository.findByIdAndDeletedAtIsNull(ROLE_ID)).thenReturn(Optional.of(customRole()));
        when(permissionRepository.findAllById(Set.of(PERMISSION_ID))).thenReturn(List.of(permission));
        when(roleHierarchyService.isSuperAdmin(authentication)).thenReturn(false);

        assertThatThrownBy(() -> roleAdminService.replaceRolePermissions(authentication, ROLE_ID, request))
                .isInstanceOf(PermissionAssignmentNotAllowedException.class);

        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should replace role permissions with a diff instead of rewriting everything")
    void shouldReplaceRolePermissionsWithDiff() {
        Authentication authentication = authentication("ROLES_ASSIGN_PERMISSIONS", "USERS_READ", "USERS_UPDATE");
        ReplaceRolePermissionsRequest request = new ReplaceRolePermissionsRequest();
        request.setPermissionIds(Set.of(
                PERMISSION_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000225")
        ));

        Role role = customRole();
        Permission readUsers = permission(PERMISSION_ID, "USERS_READ");
        Permission updateUsers = permission(UUID.fromString("00000000-0000-0000-0000-000000000225"), "USERS_UPDATE");
        RolePermission oldAssignment = assignment(UUID.fromString("00000000-0000-0000-0000-000000000226"));
        RolePermission keepAssignment = assignment(PERMISSION_ID);

        when(roleRepository.findByIdAndDeletedAtIsNull(ROLE_ID)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(request.getPermissionIds())).thenReturn(List.of(readUsers, updateUsers));
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of(oldAssignment, keepAssignment));
        when(roleHierarchyService.isSuperAdmin(authentication)).thenReturn(false);

        List<pos.pos.role.dto.PermissionResponse> response =
                roleAdminService.replaceRolePermissions(authentication, ROLE_ID, request);

        verify(rolePermissionRepository).deleteAll(List.of(oldAssignment));
        verify(rolePermissionRepository).saveAll(List.of(RolePermission.builder()
                .roleId(ROLE_ID)
                .permissionId(updateUsers.getId())
                .build()));
        assertThat(response).extracting(pos.pos.role.dto.PermissionResponse::getCode)
                .containsExactly("USERS_READ", "USERS_UPDATE");
    }

    @Test
    @DisplayName("Should update role status")
    void shouldUpdateRoleStatus() {
        Authentication authentication = authentication("ROLES_UPDATE");
        UpdateRoleStatusRequest request = new UpdateRoleStatusRequest();
        request.setIsActive(false);
        Role role = customRole();

        when(roleRepository.findByIdAndDeletedAtIsNull(ROLE_ID)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);

        RoleResponse response = roleAdminService.updateRoleStatus(authentication, ROLE_ID, request);

        assertThat(role.isActive()).isFalse();
        assertThat(response.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Should soft delete custom roles")
    void shouldSoftDeleteCustomRoles() {
        Authentication authentication = authentication("ROLES_DELETE");
        Role role = customRole();

        when(roleRepository.findByIdAndDeletedAtIsNull(ROLE_ID)).thenReturn(Optional.of(role));

        roleAdminService.deleteRole(authentication, ROLE_ID);

        assertThat(role.isActive()).isFalse();
        assertThat(role.isAssignable()).isFalse();
        assertThat(role.getDeletedAt()).isNotNull();
        verify(roleRepository).save(role);
    }

    @Test
    @DisplayName("Should clone a role with the same rank and copied permissions")
    void shouldCloneRoleWithSameRankAndCopiedPermissions() {
        Authentication authentication = authentication("ROLES_CREATE", "USERS_READ");
        CloneRoleRequest request = new CloneRoleRequest();
        request.setName("Assistant Manager");

        Role sourceRole = customRole();
        Permission permission = permission("USERS_READ");

        when(roleRepository.findByIdAndDeletedAtIsNull(ROLE_ID)).thenReturn(Optional.of(sourceRole));
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of(assignment(PERMISSION_ID)));
        when(permissionRepository.findAllById(List.of(PERMISSION_ID))).thenReturn(List.of(permission));
        when(roleHierarchyService.isSuperAdmin(authentication)).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role savedRole = invocation.getArgument(0);
            savedRole.setId(CLONED_ROLE_ID);
            return savedRole;
        });

        RoleResponse response = roleAdminService.cloneRole(authentication, ROLE_ID, request);

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());
        Role savedRole = roleCaptor.getValue();
        assertThat(savedRole.getRank()).isEqualTo(sourceRole.getRank());
        assertThat(savedRole.getCode()).isEqualTo("ASSISTANT_MANAGER");
        verify(rolePermissionRepository).saveAll(List.of(RolePermission.builder()
                .roleId(CLONED_ROLE_ID)
                .permissionId(PERMISSION_ID)
                .build()));
        assertThat(response.getId()).isEqualTo(CLONED_ROLE_ID);
    }

    private Authentication authentication(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("admin@pos.local")
                        .username("admin")
                        .active(true)
                        .build(),
                null,
                List.of(authorities).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }

    private Role customRole() {
        return Role.builder()
                .id(ROLE_ID)
                .code("MANAGER")
                .name("Manager")
                .description("Manager role")
                .rank(20_000L)
                .isSystem(false)
                .isActive(true)
                .assignable(true)
                .protectedRole(false)
                .build();
    }

    private Permission permission(String code) {
        return permission(PERMISSION_ID, code);
    }

    private Permission permission(UUID id, String code) {
        return Permission.builder()
                .id(id)
                .code(code)
                .name(code.replace('_', ' '))
                .description("Permission " + code)
                .build();
    }

    private RolePermission assignment(UUID permissionId) {
        return RolePermission.builder()
                .id(UUID.randomUUID())
                .roleId(ROLE_ID)
                .permissionId(permissionId)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
