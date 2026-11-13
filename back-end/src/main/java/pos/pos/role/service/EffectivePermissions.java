package pos.pos.role.service;

import pos.pos.role.repository.PermissionRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a person may do: what their roles allow in their restaurant (a built-in role with that restaurant's changes), less
 * the permissions switched off for them, plus the ones given to them alone. Sign-in, refresh and every request use it.
 */
public final class EffectivePermissions {

    private EffectivePermissions() {
    }

    public static List<String> forUser(PermissionRepository permissions, UUID userId, UUID restaurantId, List<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Set<String> codes = forRoles(permissions, restaurantId, roleIds);
        codes.removeAll(Set.copyOf(permissions.findDeniedCodesByUserId(userId)));
        codes.addAll(permissions.findGrantedCodesByUserId(userId));
        return List.copyOf(codes);
    }

    /**
     * What these roles allow in [restaurantId]: each role's own permissions with the restaurant's changes, together. A
     * built-in role switched off there gives nothing.
     */
    public static Set<String> forRoles(PermissionRepository permissions, UUID restaurantId, List<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        if (restaurantId != null) {
            List<UUID> off = permissions.findRoleIdsSwitchedOff(restaurantId, roleIds);
            if (!off.isEmpty()) {
                roleIds = roleIds.stream().filter(id -> !off.contains(id)).toList();
                if (roleIds.isEmpty()) return new LinkedHashSet<>();
            }
        }
        List<Object[]> changes = restaurantId == null ? List.of() : permissions.findRestaurantRoleChanges(restaurantId, roleIds);
        if (changes.isEmpty()) {
            return new LinkedHashSet<>(permissions.findCodesByRoleIds(roleIds));
        }
        // Role by role, so taking a permission from one role never takes it from another that also gives it.
        Map<UUID, Set<String>> byRole = new HashMap<>();
        for (Object[] row : permissions.findRoleCodePairs(roleIds)) {
            byRole.computeIfAbsent((UUID) row[0], id -> new HashSet<>()).add((String) row[1]);
        }
        for (Object[] row : changes) {
            Set<String> role = byRole.computeIfAbsent((UUID) row[0], id -> new HashSet<>());
            if (Boolean.TRUE.equals(row[2])) role.add((String) row[1]);
            else role.remove((String) row[1]);
        }
        Set<String> codes = new LinkedHashSet<>();
        byRole.values().forEach(codes::addAll);
        return codes;
    }
}
