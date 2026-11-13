package pos.pos.role.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.role.PermissionAssignmentNotAllowedException;
import pos.pos.exception.role.PermissionNotFoundException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.role.dto.PersonPermissionsResponse;
import pos.pos.role.dto.ReplacePersonPermissionsRequest;
import pos.pos.role.entity.Permission;
import pos.pos.role.entity.Role;
import pos.pos.role.entity.UserPermissionDenial;
import pos.pos.role.entity.UserPermissionGrant;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.role.repository.UserPermissionDenialRepository;
import pos.pos.role.repository.UserPermissionGrantRepository;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin Hub → Users → a person's Permissions (user, 2026-10-30): start from what their roles give them in their
 * restaurant and switch single permissions on or off for them. What's switched off is stored as a denial, what's added as
 * a grant; both count on every request. Someone who can manage the person changes it, and only adds what they have.
 */
@Service
@RequiredArgsConstructor
public class PersonPermissionService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionDenialRepository denialRepository;
    private final UserPermissionGrantRepository grantRepository;
    private final RoleHierarchyService roleHierarchyService;

    @Transactional(readOnly = true)
    public PersonPermissionsResponse getPermissions(Authentication authentication, UUID userId) {
        User person = manageable(authentication, userId);
        List<UUID> roleIds = roleRepository.findActiveRolesByUserId(userId).stream().map(Role::getId).toList();
        Set<String> fromRoles = EffectivePermissions.forRoles(permissionRepository, person.getRestaurantId(), roleIds);
        List<String> permissions = EffectivePermissions.forUser(permissionRepository, userId, person.getRestaurantId(), roleIds);
        return new PersonPermissionsResponse(List.copyOf(fromRoles), permissions);
    }

    @Transactional
    public PersonPermissionsResponse replacePermissions(Authentication authentication, UUID userId, ReplacePersonPermissionsRequest request) {
        User person = manageable(authentication, userId);
        Set<String> requested = new LinkedHashSet<>(request.getPermissionCodes());
        Set<String> known = permissionRepository.findAll().stream().map(Permission::getCode).collect(Collectors.toSet());
        if (!known.containsAll(requested)) {
            throw new PermissionNotFoundException();
        }
        List<UUID> roleIds = roleRepository.findActiveRolesByUserId(userId).stream().map(Role::getId).toList();
        Set<String> fromRoles = EffectivePermissions.forRoles(permissionRepository, person.getRestaurantId(), roleIds);
        Set<String> now = Set.copyOf(EffectivePermissions.forUser(permissionRepository, userId, person.getRestaurantId(), roleIds));

        // Nobody hands out something they can't do themselves.
        if (!roleHierarchyService.isSuperAdmin(authentication)) {
            Set<String> mine = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
            if (requested.stream().anyMatch(code -> !now.contains(code) && !mine.contains(code))) {
                throw new PermissionAssignmentNotAllowedException();
            }
        }

        UUID actorId = roleHierarchyService.currentUserId(authentication);
        denialRepository.deleteAllByUserId(userId);
        grantRepository.deleteAllByUserId(userId);
        denialRepository.flush();
        grantRepository.flush();
        fromRoles.stream().filter(code -> !requested.contains(code)).forEach(code -> {
            UserPermissionDenial denial = new UserPermissionDenial();
            denial.setUserId(userId);
            denial.setPermissionCode(code);
            denial.setCreatedBy(actorId);
            denialRepository.save(denial);
        });
        requested.stream().filter(code -> !fromRoles.contains(code)).forEach(code -> {
            UserPermissionGrant grant = new UserPermissionGrant();
            grant.setUserId(userId);
            grant.setPermissionCode(code);
            grant.setCreatedBy(actorId);
            grantRepository.save(grant);
        });
        return new PersonPermissionsResponse(List.copyOf(fromRoles), List.copyOf(requested));
    }

    private User manageable(Authentication authentication, UUID userId) {
        User person = userRepository.findActiveById(userId).orElseThrow(UserNotFoundException::new);
        roleHierarchyService.assertCanManageUser(authentication, userId);
        return person;
    }
}
