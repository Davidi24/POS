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
import pos.pos.exception.role.RoleManagementNotAllowedException;
import pos.pos.role.dto.CloneRoleRequest;
import pos.pos.role.dto.CreateRoleRequest;
import pos.pos.role.dto.PermissionResponse;
import pos.pos.role.dto.ReplaceRolePermissionsRequest;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.dto.UpdateRoleRequest;
import pos.pos.role.dto.UpdateRoleStatusRequest;
import pos.pos.role.entity.Permission;
import pos.pos.role.entity.RestaurantRolePermission;
import pos.pos.role.entity.Role;
import pos.pos.role.entity.RolePermission;
import pos.pos.role.mapper.PermissionMapper;
import pos.pos.role.mapper.RoleMapper;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RolePermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.notification.service.NotificationService;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.HashSet;
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
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final pos.pos.role.repository.RestaurantRolePermissionRepository restaurantRolePermissionRepository;
    private final RolePayService rolePayService;

    @Transactional
    public RoleResponse createRole(Authentication authentication, CreateRoleRequest request) {
        String normalizedName = normalizeRoleName(request.getName());
        String generatedCode = deriveRoleCode(normalizedName);

        assertUniqueRoleName(normalizedName, null);
        assertUniqueRoleCode(generatedCode, null);

        UUID restaurantId = ownerRestaurant(authentication);
        Role role = roleRepository.save(Role.builder()
                .name(normalizedName)
                .code(generatedCode)
                .restaurantId(restaurantId)
                .description(request.getDescription())
                .picture(request.getPicture())
                .rank(defaultCustomRank(authentication))
                .isSystem(false)
                .isActive(true)
                .assignable(true)
                .protectedRole(false)
                .build());

        // Paid unless said otherwise; a paid role of the restaurant's own is a Salary team of its own.
        if (restaurantId != null) {
            rolePayService.set(restaurantId, role, request.getPaid() == null || request.getPaid(), null, roleHierarchyService.currentUserId(authentication));
        }

        publishRoleChange(authentication, role.getId(), "Role created");

        return rolePayService.response(role, restaurantId);
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
        if (request.getPicture() != null) role.setPicture(request.getPicture());
        UUID restaurantId = roleHierarchyService.actorRestaurantId(authentication);
        if (request.getPaid() != null && restaurantId != null) {
            rolePayService.set(restaurantId, role, request.getPaid(), request.getPayTeam(), roleHierarchyService.currentUserId(authentication));
        }

        publishRoleChange(authentication, role.getId(), "Role updated");

        return rolePayService.response(roleRepository.save(role), restaurantId);
    }

    /**
     * Whether a role's people get paid in the restaurant being worked in, and their Salary team (user, 2026-10-31).
     * Built-in roles follow the same rule as changing their permissions there; a restaurant's own roles need managing it.
     */
    @Transactional
    public RoleResponse setRolePay(Authentication authentication, UUID roleId, pos.pos.role.dto.RolePayRequest request) {
        Role role = findExistingRole(roleId);
        UUID restaurantId = roleHierarchyService.actorRestaurantId(authentication);
        if (restaurantId == null) {
            throw new AuthException("Open a restaurant first", HttpStatus.BAD_REQUEST);
        }
        if (role.isSystem()) {
            roleHierarchyService.assertCanChangeRoleInRestaurant(authentication, role);
        } else {
            roleHierarchyService.assertCanManageRole(authentication, role);
        }
        rolePayService.set(restaurantId, role, request.paid(), request.payTeam(), roleHierarchyService.currentUserId(authentication));
        publishRoleChange(authentication, role.getId(), "Role pay updated");
        return rolePayService.response(role, restaurantId);
    }

    @Transactional
    public List<PermissionResponse> replaceRolePermissions(
            Authentication authentication,
            UUID roleId,
            ReplaceRolePermissionsRequest request
    ) {
        Role role = findExistingRole(roleId);
        // A built-in role is shared by every restaurant: inside one restaurant its permissions change for that restaurant only.
        UUID restaurantId = roleHierarchyService.actorRestaurantId(authentication);
        if (role.isSystem() && restaurantId != null) {
            return replaceRolePermissionsHere(authentication, role, restaurantId, request);
        }
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

        publishRoleChange(authentication, roleId, "Role permissions changed");

        return requestedPermissions.stream()
                .map(PermissionMapper::toResponse)
                .toList();
    }

    /**
     * A built-in role's permissions in one restaurant: stored as that restaurant's changes to the defaults (added or taken
     * away). Only someone above the role changes it, and a permission they don't have themselves can't be added.
     */
    private List<PermissionResponse> replaceRolePermissionsHere(Authentication authentication, Role role, UUID restaurantId,
                                                                ReplaceRolePermissionsRequest request) {
        roleHierarchyService.assertCanChangeRoleInRestaurant(authentication, role);
        Set<UUID> requestedPermissionIds = request.getPermissionIds() == null ? Set.of() : new LinkedHashSet<>(request.getPermissionIds());
        List<Permission> requested = permissionRepository.findAllById(requestedPermissionIds).stream().sorted(PERMISSION_ORDER).toList();
        if (requested.size() != requestedPermissionIds.size()) {
            throw new PermissionNotFoundException();
        }
        Set<String> requestedCodes = requested.stream().map(Permission::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> current = EffectivePermissions.forRoles(permissionRepository, restaurantId, List.of(role.getId()));
        assertActorCanGrantPermissions(authentication, requested.stream().filter(p -> !current.contains(p.getCode())).toList());

        Set<String> defaults = new HashSet<>(permissionRepository.findCodesByRoleIds(List.of(role.getId())));
        UUID actorId = roleHierarchyService.currentUserId(authentication);
        restaurantRolePermissionRepository.deleteAllForRole(restaurantId, role.getId());
        restaurantRolePermissionRepository.flush();
        List<RestaurantRolePermission> changes = new ArrayList<>();
        requestedCodes.stream().filter(code -> !defaults.contains(code)).forEach(code -> changes.add(roleChange(restaurantId, role, code, true, actorId)));
        defaults.stream().filter(code -> !requestedCodes.contains(code)).forEach(code -> changes.add(roleChange(restaurantId, role, code, false, actorId)));
        restaurantRolePermissionRepository.saveAll(changes);

        publishRoleChange(authentication, role.getId(), "Role permissions changed");
        return requested.stream().map(PermissionMapper::toResponse).toList();
    }

    private static RestaurantRolePermission roleChange(UUID restaurantId, Role role, String code, boolean granted, UUID actorId) {
        RestaurantRolePermission change = new RestaurantRolePermission();
        change.setRestaurantId(restaurantId);
        change.setRoleId(role.getId());
        change.setPermissionCode(code);
        change.setGranted(granted);
        change.setUpdatedBy(actorId);
        return change;
    }

    @Transactional
    public RoleResponse updateRoleStatus(Authentication authentication, UUID roleId, UpdateRoleStatusRequest request) {
        Role role = findExistingRole(roleId);
        UUID restaurantId = roleHierarchyService.actorRestaurantId(authentication);
        // A built-in role is switched off in the restaurant being worked in only (user, 2026-10-31), by someone above it.
        if (role.isSystem()) {
            if (restaurantId == null) {
                throw new AuthException("Open a restaurant first", HttpStatus.BAD_REQUEST);
            }
            roleHierarchyService.assertCanChangeRoleInRestaurant(authentication, role);
            rolePayService.setActive(restaurantId, role, Boolean.TRUE.equals(request.getIsActive()), roleHierarchyService.currentUserId(authentication));
            publishRoleChange(authentication, role.getId(), "Role status changed");
            return rolePayService.response(role, restaurantId);
        }
        roleHierarchyService.assertCanManageRole(authentication, role);

        role.setActive(Boolean.TRUE.equals(request.getIsActive()));
        publishRoleChange(authentication, role.getId(), "Role status changed");
        return rolePayService.response(roleRepository.save(role), restaurantId);
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
        publishRoleChange(authentication, role.getId(), "Role deleted");
    }

    @Transactional
    public RoleResponse cloneRole(Authentication authentication, UUID roleId, CloneRoleRequest request) {
        Role sourceRole = findExistingRole(roleId);
        if (!roleHierarchyService.visibleTo(authentication, sourceRole)) {
            throw new RoleNotFoundException();
        }
        if (!roleHierarchyService.isSuperAdmin(authentication)) {
            roleHierarchyService.assertCanAssignRole(authentication, sourceRole);
        }
        UUID restaurantId = ownerRestaurant(authentication);

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
                .restaurantId(restaurantId)
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

        publishRoleChange(authentication, clonedRole.getId(), "Role cloned");

        return rolePayService.response(clonedRole, roleHierarchyService.actorRestaurantId(authentication));
    }

    // A custom role belongs to its maker's restaurant. The Super Admin's roles are shared by every restaurant unless
    // they made them inside one; a Co-Super-Admin always makes them inside a restaurant.
    private UUID ownerRestaurant(Authentication authentication) {
        UUID restaurantId = roleHierarchyService.actorRestaurantId(authentication);
        if (roleHierarchyService.isSuperAdmin(authentication)) {
            return restaurantId;
        }
        if (restaurantId == null) {
            throw new RoleManagementNotAllowedException();
        }
        return restaurantId;
    }

    private void publishRoleChange(Authentication authentication, UUID roleId, String message) {
        // Super admins announce it to the restaurant they're working in.
        if (roleHierarchyService.isPlatformAdmin(authentication)) {
            UUID restaurantId = roleHierarchyService.actorRestaurantId(authentication);
            if (restaurantId != null) {
                notificationService.publishRoleChange(restaurantId, roleId, roleHierarchyService.currentUserId(authentication), message);
            }
            return;
        }
        User actor = userRepository.findActiveById(roleHierarchyService.currentUserId(authentication)).orElse(null);
        if (actor == null || actor.getRestaurantId() == null) {
            return;
        }
        notificationService.publishRoleChange(actor.getRestaurantId(), roleId, actor.getId(), message);
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
