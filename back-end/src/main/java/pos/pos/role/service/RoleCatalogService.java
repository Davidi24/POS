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

    public List<RoleResponse> getRoles() {
        return roleRepository.findByIsActiveTrueOrderByRankDescNameAsc().stream()
                .map(RoleMapper::toResponse)
                .toList();
    }

    public RoleResponse getRole(UUID roleId) {
        return RoleMapper.toResponse(findExistingRole(roleId));
    }

    public List<PermissionResponse> getPermissions() {
        return permissionRepository.findAll(PERMISSION_SORT).stream()
                .map(PermissionMapper::toResponse)
                .toList();
    }

    public List<PermissionResponse> getRolePermissions(UUID roleId) {
        findExistingRole(roleId);

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
        return roleHierarchyService.getAssignableRoles(authentication)
                .stream()
                .map(RoleMapper::toResponse)
                .toList();
    }

    public List<RoleResponse> getSystemRoles() {
        return roleRepository.findActiveSystemRoles().stream()
                .map(RoleMapper::toResponse)
                .toList();
    }

    private Role findExistingRole(UUID roleId) {
        return roleRepository.findByIdAndDeletedAtIsNull(roleId)
                .orElseThrow(RoleNotFoundException::new);
    }
}
