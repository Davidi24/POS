package pos.pos.role.scope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.exception.auth.AuthException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which floors "See tables" and which roles "See others' shifts" cover, for a role in the restaurant worked in or for one
 * person (user, 2026-11-20). Saved with the same rights as the permissions themselves.
 */
@RestController
@RequiredArgsConstructor
public class PermissionScopeController {

    private final PermissionScopeService scopes;
    private final RoleHierarchyService hierarchy;
    private final RoleRepository roles;
    private final UserRepository users;

    public record ScopesRequest(@NotNull @Size(max = 10) Map<String, @Size(max = PermissionScopeService.MAX_TARGETS) List<@Size(max = 200) String>> scopes) {
    }

    public record ScopesResponse(Map<String, List<String>> scopes) {
    }

    @GetMapping("/restaurants/{restaurantId}/permission-scopes/choices")
    @PreAuthorize("hasAnyAuthority('ROLES_READ', 'USERS_UPDATE')")
    public PermissionScopeService.Choices choices(Authentication auth, @PathVariable UUID restaurantId) {
        UUID mine = hierarchy.actorRestaurantId(auth);
        if (mine == null || !mine.equals(restaurantId)) throw new AuthException("Open this restaurant first", HttpStatus.FORBIDDEN);
        return scopes.choices(restaurantId);
    }

    @GetMapping("/roles/{roleId}/scopes")
    @PreAuthorize("hasAuthority('ROLES_READ')")
    public ScopesResponse roleScopes(Authentication auth, @PathVariable UUID roleId) {
        return new ScopesResponse(scopes.forRole(restaurant(auth), role(roleId).getId()));
    }

    @PutMapping("/roles/{roleId}/scopes")
    @PreAuthorize("hasAuthority('ROLES_ASSIGN_PERMISSIONS')")
    @Transactional
    public ScopesResponse saveRoleScopes(Authentication auth, @PathVariable UUID roleId, @Valid @RequestBody ScopesRequest request) {
        Role role = role(roleId);
        if (role.isSystem()) hierarchy.assertCanChangeRoleInRestaurant(auth, role);
        else hierarchy.assertCanManageRole(auth, role);
        return new ScopesResponse(scopes.replaceForRole(restaurant(auth), role.getId(), request.scopes(), hierarchy.currentUserId(auth)));
    }

    @GetMapping("/users/{userId}/scopes")
    @PreAuthorize("hasAuthority('USERS_UPDATE')")
    public ScopesResponse userScopes(Authentication auth, @PathVariable UUID userId) {
        hierarchy.assertCanManageUser(auth, userId);
        return new ScopesResponse(scopes.forUser(restaurantOf(userId), userId));
    }

    @PutMapping("/users/{userId}/scopes")
    @PreAuthorize("hasAuthority('USERS_UPDATE')")
    @Transactional
    public ScopesResponse saveUserScopes(Authentication auth, @PathVariable UUID userId, @Valid @RequestBody ScopesRequest request) {
        hierarchy.assertCanManageUser(auth, userId);
        return new ScopesResponse(scopes.replaceForUser(restaurantOf(userId), userId, request.scopes(), hierarchy.currentUserId(auth)));
    }

    private Role role(UUID roleId) {
        return roles.findByIdAndDeletedAtIsNull(roleId).orElseThrow(() -> new AuthException("Role not found", HttpStatus.NOT_FOUND));
    }

    private UUID restaurant(Authentication auth) {
        UUID restaurantId = hierarchy.actorRestaurantId(auth);
        if (restaurantId == null) throw new AuthException("Open a restaurant first", HttpStatus.BAD_REQUEST);
        return restaurantId;
    }

    private UUID restaurantOf(UUID userId) {
        UUID restaurantId = users.findActiveById(userId).map(u -> u.getRestaurantId())
                .orElseThrow(() -> new AuthException("Person not found", HttpStatus.NOT_FOUND));
        if (restaurantId == null) throw new AuthException("This person works in no restaurant", HttpStatus.BAD_REQUEST);
        return restaurantId;
    }
}
