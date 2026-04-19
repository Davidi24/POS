package pos.pos.role.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.role.PermissionAssignmentNotAllowedException;
import pos.pos.exception.role.PermissionNotFoundException;
import pos.pos.exception.role.RoleCodeAlreadyExistsException;
import pos.pos.exception.role.RoleNameAlreadyExistsException;
import pos.pos.exception.role.RoleNotFoundException;
import pos.pos.role.dto.CloneRoleRequest;
import pos.pos.role.dto.CreateRoleRequest;
import pos.pos.role.dto.PermissionResponse;
import pos.pos.role.dto.ReplaceRolePermissionsRequest;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.dto.UpdateRoleRequest;
import pos.pos.role.dto.UpdateRoleStatusRequest;
import pos.pos.role.entity.Permission;
import pos.pos.role.entity.Role;
import pos.pos.role.entity.RolePermission;
import pos.pos.role.mapper.PermissionMapper;
import pos.pos.role.mapper.RoleMapper;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RolePermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleAdminService {

    private static final Comparator<Permission> PERMISSION_ORDER =
            Comparator.comparing(Permission::getName).thenComparing(Permission::getCode);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleHierarchyService roleHierarchyService;

    @Transactional
    public RoleResponse createRole(Authentication authentication, CreateRoleRequest request) {
        String normalizedName = normalizeRoleName(request.getName());
        String generatedCode = deriveRoleCode(normalizedName);

        assertUniqueRoleName(normalizedName, null);
        assertUniqueRoleCode(generatedCode, null);

        Role role = roleRepository.save(Role.builder()
                .name(normalizedName)
                .code(generatedCode)
                .description(request.getDescription())
                .rank(defaultCustomRank(authentication))
                .isSystem(false)
                .isActive(true)
                .assignable(true)
                .protectedRole(false)
                .build());

        return RoleMapper.toResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(Authentication authentication, UUID roleId, UpdateRoleRequest request) {
        Role role = findExistingRole(roleId);
        assertCustomRole(role);
        roleHierarchyService.assertCanManageRole(authentication, role);

        String normalizedName = normalizeRoleName(request.getName());
        if (!normalizedName.equals(role.getName())) {
            assertUniqueRoleName(normalizedName, role.getId());
        }

        role.setName(normalizedName);
        role.setDescription(request.getDescription());

        return RoleMapper.toResponse(roleRepository.save(role));
    }

    @Transactional
    public List<PermissionResponse> replaceRolePermissions(
            Authentication authentication,
            UUID roleId,
            ReplaceRolePermissionsRequest request
    ) {
        Role role = findExistingRole(roleId);
        assertCustomRole(role);
        roleHierarchyService.assertCanManageRole(authentication, role);

        Set<UUID> requestedPermissionIds = request.getPermissionIds() == null
                ? Set.of()
                : new LinkedHashSet<>(request.getPermissionIds());
        List<Permission> requestedPermissions = permissionRepository.findAllById(requestedPermissionIds).stream()
                .sorted(PERMISSION_ORDER)
                .toList();

        if (requestedPermissions.size() != requestedPermissionIds.size()) {
            throw new PermissionNotFoundException();
        }

        assertActorCanGrantPermissions(authentication, requestedPermissions);

        List<RolePermission> existingAssignments = rolePermissionRepository.findByRoleId(roleId);
        Map<UUID, RolePermission> existingByPermissionId = existingAssignments.stream()
                .collect(Collectors.toMap(RolePermission::getPermissionId, Function.identity(), (left, right) -> left));

        List<RolePermission> assignmentsToRemove = existingAssignments.stream()
                .filter(assignment -> !requestedPermissionIds.contains(assignment.getPermissionId()))
                .toList();
        if (!assignmentsToRemove.isEmpty()) {
            rolePermissionRepository.deleteAll(assignmentsToRemove);
        }

        List<RolePermission> assignmentsToAdd = requestedPermissions.stream()
                .filter(permission -> !existingByPermissionId.containsKey(permission.getId()))
                .map(permission -> RolePermission.builder()
                        .roleId(roleId)
                        .permissionId(permission.getId())
                        .build())
                .toList();
        if (!assignmentsToAdd.isEmpty()) {
            rolePermissionRepository.saveAll(assignmentsToAdd);
        }

        return requestedPermissions.stream()
                .map(PermissionMapper::toResponse)
                .toList();
    }

    @Transactional
    public RoleResponse updateRoleStatus(Authentication authentication, UUID roleId, UpdateRoleStatusRequest request) {
        Role role = findExistingRole(roleId);
        assertCustomRole(role);
        roleHierarchyService.assertCanManageRole(authentication, role);

        role.setActive(Boolean.TRUE.equals(request.getIsActive()));
        return RoleMapper.toResponse(roleRepository.save(role));
    }

    @Transactional
    public void deleteRole(Authentication authentication, UUID roleId) {
        Role role = findExistingRole(roleId);
        assertCustomRole(role);
        roleHierarchyService.assertCanManageRole(authentication, role);

        role.setActive(false);
        role.setAssignable(false);
        role.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        roleRepository.save(role);
    }

    @Transactional
    public RoleResponse cloneRole(Authentication authentication, UUID roleId, CloneRoleRequest request) {
        Role sourceRole = findExistingRole(roleId);
        if (!roleHierarchyService.isSuperAdmin(authentication)) {
            roleHierarchyService.assertCanAssignRole(authentication, sourceRole);
        }

        List<Permission> sourcePermissions = loadPermissionsForRole(sourceRole.getId());
        assertActorCanGrantPermissions(authentication, sourcePermissions);

        String normalizedName = normalizeRoleName(request.getName());
        String generatedCode = deriveRoleCode(normalizedName);
        assertUniqueRoleName(normalizedName, null);
        assertUniqueRoleCode(generatedCode, null);

        String requestedDescription = NormalizationUtils.normalize(request.getDescription());
        Role clonedRole = roleRepository.save(Role.builder()
                .name(normalizedName)
                .code(generatedCode)
                .description(requestedDescription != null ? request.getDescription() : sourceRole.getDescription())
                .rank(sourceRole.getRank())
                .isSystem(false)
                .isActive(true)
                .assignable(true)
                .protectedRole(false)
                .build());

        if (!sourcePermissions.isEmpty()) {
            rolePermissionRepository.saveAll(sourcePermissions.stream()
                    .map(permission -> RolePermission.builder()
                            .roleId(clonedRole.getId())
                            .permissionId(permission.getId())
                            .build())
                    .toList());
        }

        return RoleMapper.toResponse(clonedRole);
    }

    private Role findExistingRole(UUID roleId) {
        return roleRepository.findByIdAndDeletedAtIsNull(roleId)
                .orElseThrow(RoleNotFoundException::new);
    }

    private void assertCustomRole(Role role) {
        if (role.isSystem()) {
            throw new AuthException("System roles cannot be modified", HttpStatus.BAD_REQUEST);
        }
    }

    private void assertUniqueRoleName(String normalizedName, UUID roleIdToExclude) {
        boolean exists = roleIdToExclude == null
                ? roleRepository.existsByNameAndDeletedAtIsNull(normalizedName)
                : roleRepository.existsByNameAndIdNotAndDeletedAtIsNull(normalizedName, roleIdToExclude);
        if (exists) {
            throw new RoleNameAlreadyExistsException();
        }
    }

    private void assertUniqueRoleCode(String code, UUID roleIdToExclude) {
        boolean exists = roleIdToExclude == null
                ? roleRepository.existsByCodeAndDeletedAtIsNull(code)
                : roleRepository.existsByCodeAndIdNotAndDeletedAtIsNull(code, roleIdToExclude);
        if (exists) {
            throw new RoleCodeAlreadyExistsException();
        }
    }

    private String normalizeRoleName(String rawName) {
        String normalizedName = NormalizationUtils.normalize(rawName);
        if (normalizedName == null) {
            throw new AuthException("Role name is required", HttpStatus.BAD_REQUEST);
        }
        return normalizedName;
    }

    private String deriveRoleCode(String normalizedName) {
        String code = normalizedName.replaceAll("[^A-Za-z0-9]+", "_");
        code = code.replaceAll("^_+|_+$", "");
        code = NormalizationUtils.normalizeUpper(code);
        if (code == null) {
            throw new AuthException("Role name is required", HttpStatus.BAD_REQUEST);
        }
        return code;
    }

    private long defaultCustomRank(Authentication authentication) {
        long actorHighestRank = roleHierarchyService.highestActiveRank(roleHierarchyService.currentUserId(authentication));
        return Math.max(actorHighestRank - 1, 1);
    }

    private List<Permission> loadPermissionsForRole(UUID roleId) {
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
                .sorted(PERMISSION_ORDER)
                .toList();
    }

    private void assertActorCanGrantPermissions(Authentication authentication, List<Permission> permissions) {
        if (permissions.isEmpty() || roleHierarchyService.isSuperAdmin(authentication)) {
            return;
        }

        Set<String> actorAuthorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean hasForbiddenPermission = permissions.stream()
                .map(Permission::getCode)
                .anyMatch(code -> !actorAuthorities.contains(code));

        if (hasForbiddenPermission) {
            throw new PermissionAssignmentNotAllowedException();
        }
    }
}
