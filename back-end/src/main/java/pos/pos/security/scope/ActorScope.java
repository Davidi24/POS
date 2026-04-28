package pos.pos.security.scope;

import pos.pos.security.rbac.AppPermission;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// In-memory snapshot of the logged-in user built once per request — never persisted to the DB.
// Holds everything policies need to make access decisions without hitting the DB again.
public record ActorScope(
        User actor,          // the logged-in user entity
        boolean superAdmin,  // true if the user has the SuperAdmin role (bypasses most restrictions)
        Set<String> roleCodes,       // e.g. ["OWNER", "MANAGER"]
        Set<String> permissionCodes  // e.g. ["RESTAURANTS_READ", "USERS_DELETE"]
) {

    // Defensive constructor — ensures nothing is null and makes role/permission sets immutable
    public ActorScope {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(actor.getId(), "actor.id");
        Objects.requireNonNull(roleCodes, "roleCodes");
        Objects.requireNonNull(permissionCodes, "permissionCodes");
        roleCodes = Set.copyOf(new LinkedHashSet<>(roleCodes));
        permissionCodes = Set.copyOf(new LinkedHashSet<>(permissionCodes));
    }

    public UUID userId() {
        return actor.getId();
    }

    public UUID restaurantId() {
        return actor.getRestaurantId();
    }

    public UUID defaultBranchId() {
        return actor.getDefaultBranchId();
    }

    // Returns true if this user belongs to the given restaurant (used to scope access to their own restaurant only)
    public boolean belongsToRestaurant(UUID restaurantId) {
        return restaurantId != null && Objects.equals(restaurantId(), restaurantId);
    }

    // Returns true if this user's default branch matches the given branch
    public boolean belongsToDefaultBranch(UUID branchId) {
        return branchId != null && Objects.equals(defaultBranchId(), branchId);
    }

    // Case-insensitive role check
    public boolean hasRole(String roleCode) {
        String normalizedRoleCode = NormalizationUtils.normalizeUpper(roleCode);
        return normalizedRoleCode != null && roleCodes.contains(normalizedRoleCode);
    }

    // Case-insensitive permission check by string code
    public boolean hasPermission(String permissionCode) {
        String normalizedPermissionCode = NormalizationUtils.normalizeUpper(permissionCode);
        return normalizedPermissionCode != null && permissionCodes.contains(normalizedPermissionCode);
    }

    // Permission check using the enum — preferred over the string version to avoid typos
    public boolean hasPermission(AppPermission permission) {
        return permission != null && permissionCodes.contains(permission.name());
    }
}
