package pos.pos.security.scope;

import pos.pos.security.rbac.AppPermission;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ActorScope(
        UUID userId,
        User actor,
        boolean superAdmin,
        Set<String> roleCodes,
        Set<String> permissionCodes
) {

    public ActorScope {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(roleCodes, "roleCodes");
        Objects.requireNonNull(permissionCodes, "permissionCodes");
        roleCodes = Set.copyOf(new LinkedHashSet<>(roleCodes));
        permissionCodes = Set.copyOf(new LinkedHashSet<>(permissionCodes));
    }

    public UUID restaurantId() {
        return actor.getRestaurantId();
    }

    public UUID defaultBranchId() {
        return actor.getDefaultBranchId();
    }

    public boolean belongsToRestaurant(UUID restaurantId) {
        return restaurantId != null && Objects.equals(restaurantId(), restaurantId);
    }

    public boolean belongsToDefaultBranch(UUID branchId) {
        return branchId != null && Objects.equals(defaultBranchId(), branchId);
    }

    public boolean hasRole(String roleCode) {
        String normalizedRoleCode = NormalizationUtils.normalizeUpper(roleCode);
        return normalizedRoleCode != null && roleCodes.contains(normalizedRoleCode);
    }

    public boolean hasPermission(String permissionCode) {
        String normalizedPermissionCode = NormalizationUtils.normalizeUpper(permissionCode);
        return normalizedPermissionCode != null && permissionCodes.contains(normalizedPermissionCode);
    }

    public boolean hasPermission(AppPermission permission) {
        return permission != null && permissionCodes.contains(permission.name());
    }
}
