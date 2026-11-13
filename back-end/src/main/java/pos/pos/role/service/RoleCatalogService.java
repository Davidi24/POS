package pos.pos.role.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pos.pos.exception.role.RoleNotFoundException;
import pos.pos.role.dto.PermissionResponse;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.entity.Permission;
import pos.pos.role.entity.Role;
import pos.pos.role.entity.RolePermission;
import pos.pos.role.mapper.PermissionMapper;
import pos.pos.role.mapper.RoleMapper;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RolePermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.rbac.RoleHierarchyService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleCatalogService {

    private static final Sort PERMISSION_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("code"));

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleHierarchyService roleHierarchyService;
    private final RolePayService rolePayService;

    // Roles carry whether their people get paid in the restaurant being worked in.
    public List<RoleResponse> getRoles(Authentication authentication) {
        // Switched-off roles are listed too (dimmed in the app), so they can be switched back on.
        return rolePayService.responses(roleRepository.findByDeletedAtIsNullOrderByRankDescNameAsc().stream()
                .filter(role -> roleHierarchyService.visibleTo(authentication, role))
                .toList(), roleHierarchyService.actorRestaurantId(authentication));
    }

    public RoleResponse getRole(Authentication authentication, UUID roleId) {
        return rolePayService.response(findVisibleRole(authentication, roleId), roleHierarchyService.actorRestaurantId(authentication));
    }

    public List<PermissionResponse> getPermissions() {
        return permissionRepository.findAll(PERMISSION_SORT).stream()
                .map(PermissionMapper::toResponse)
                .toList();
    }

    public List<PermissionResponse> getRolePermissions(Authentication authentication, UUID roleId) {
        Role role = findVisibleRole(authentication, roleId);
        // A built-in role inside a restaurant: as that restaurant has it (its own changes on top of the defaults).
        UUID restaurantId = roleHierarchyService.actorRestaurantId(authentication);
        if (role.isSystem() && restaurantId != null) {
            Set<String> codes = EffectivePermissions.forRoles(permissionRepository, restaurantId, List.of(roleId));
            return permissionRepository.findAll(PERMISSION_SORT).stream()
                    .filter(permission -> codes.contains(permission.getCode()))
                    .map(PermissionMapper::toResponse)
                    .toList();
        }

        List<RolePermission> assignments = rolePermissionRepository.findByRoleId(roleId);
        if (assignments.isEmpty()) {
            return List.of();
        }

        Map<UUID, Permission> permissionsById = permissionRepository.findAllById(
                        assignments.stream().map(RolePermission::getPermissionId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));

        return assignments.stream()
                .map(assignment -> permissionsById.get(assignment.getPermissionId()))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(Permission::getName).thenComparing(Permission::getCode))
                .map(PermissionMapper::toResponse)
                .toList();
    }

    public List<RoleResponse> getAssignableRoles(Authentication authentication) {
        // A role switched off here can't be handed out.
        UUID restaurantId = roleHierarchyService.actorRestaurantId(authentication);
        java.util.Set<UUID> off = rolePayService.switchedOff(restaurantId);
        return rolePayService.responses(roleHierarchyService.getAssignableRoles(authentication).stream()
                .filter(role -> role.isActive() && !off.contains(role.getId())).toList(), restaurantId);
    }

    public List<RoleResponse> getSystemRoles() {
        return roleRepository.findActiveSystemRoles().stream()
                .map(RoleMapper::toResponse)
                .toList();
    }

    // Another restaurant's custom role is reported as not found, not as forbidden.
    private Role findVisibleRole(Authentication authentication, UUID roleId) {
        return roleRepository.findByIdAndDeletedAtIsNull(roleId)
                .filter(role -> roleHierarchyService.visibleTo(authentication, role))
                .orElseThrow(RoleNotFoundException::new);
    }
}
