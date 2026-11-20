package pos.pos.role.scope;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.service.EffectivePermissions;
import pos.pos.security.rbac.PermissionTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Permissions given for some things only (user, 2026-11-20): "See tables" for the floors chosen, "See others' shifts" for
 * the roles chosen. Stored per restaurant for a role, or for one person (which then wins), in `permission_scopes`. Nothing
 * stored = all of them. "See tables" is given for a whole branch ("branch:id"), a whole floor ("branchId|floor name"; floors
 * are the tables' floor names) or a place ("place:id"); a role is its id.
 */
@Service
public class PermissionScopeService {

    public static final int MAX_TARGETS = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionRepository permissions;
    private final String schema;

    public PermissionScopeService(NamedParameterJdbcTemplate jdbc, PermissionRepository permissions,
                                  @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid schema");
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.schema = "\"" + schema + "\".";
    }

    public record Branch(String target, UUID branchId, String name) {
    }

    public record Floor(String target, UUID branchId, String branchName, String name) {
    }

    /** A place (Tables → Edit → Create places), under the floor most of its tables are on. */
    public record Place(String target, UUID branchId, String floor, String name) {
    }

    public record RoleChoice(String target, String name) {
    }

    public record Choices(List<Branch> branches, List<Floor> floors, List<Place> places, List<RoleChoice> roles) {
    }

    /** What [userId] may use [code] for in [restaurantId]; null = everything. */
    @Transactional(readOnly = true)
    public Set<String> allowed(UUID userId, UUID restaurantId, String code) {
        if (userId == null || restaurantId == null || PermissionTree.scopeOf(code) == null) return null;
        List<String> own = targets("user_id", userId, restaurantId, code);
        if (!own.isEmpty()) return new HashSet<>(own);
        List<UUID> roleIds = jdbc.queryForList("select ur.role_id from " + schema + "user_roles ur where ur.user_id = :user",
                Map.of("user", userId), UUID.class);
        Set<String> union = new HashSet<>();
        for (UUID roleId : roleIds) {
            if (!EffectivePermissions.forRoles(permissions, restaurantId, List.of(roleId)).contains(code)) continue;
            List<String> given = targets("role_id", roleId, restaurantId, code);
            // A role giving it for everything gives everything.
            if (given.isEmpty()) return null;
            union.addAll(given);
        }
        return union.isEmpty() ? null : union;
    }

    /** Whether [userId] may use [code] for [target]. */
    @Transactional(readOnly = true)
    public boolean allows(UUID userId, UUID restaurantId, String code, String target) {
        Set<String> allowed = allowed(userId, restaurantId, code);
        return allowed == null || allowed.contains(target);
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> forRole(UUID restaurantId, UUID roleId) {
        return byCode("role_id", roleId, restaurantId);
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> forUser(UUID restaurantId, UUID userId) {
        return byCode("user_id", userId, restaurantId);
    }

    @Transactional
    public Map<String, List<String>> replaceForRole(UUID restaurantId, UUID roleId, Map<String, List<String>> scopes, UUID actorId) {
        replace("role_id", roleId, restaurantId, scopes, actorId);
        return forRole(restaurantId, roleId);
    }

    @Transactional
    public Map<String, List<String>> replaceForUser(UUID restaurantId, UUID userId, Map<String, List<String>> scopes, UUID actorId) {
        replace("user_id", userId, restaurantId, scopes, actorId);
        return forUser(restaurantId, userId);
    }

    /** A floor renamed in Tables → Edit floors keeps who may see it. */
    @Transactional
    public void renameFloor(UUID restaurantId, UUID branchId, String from, String to) {
        if (from == null || to == null || from.equals(to)) return;
        jdbc.update("update " + schema + "permission_scopes set target = :to where restaurant_id = :restaurant and permission_code = 'TABLES_READ'"
                        + " and target = :from",
                Map.of("restaurant", restaurantId, "from", floorTarget(branchId, from), "to", floorTarget(branchId, to)));
    }

    /** The floors of every branch and the roles a restaurant can choose from. */
    @Transactional(readOnly = true)
    public Choices choices(UUID restaurantId) {
        List<Floor> floors = jdbc.query("select distinct t.branch_id, b.name as branch_name, t.floor from " + schema + "tables t"
                        + " join " + schema + "branches b on b.id = t.branch_id"
                        + " where t.restaurant_id = :restaurant and t.floor is not null and t.floor <> '' and b.deleted_at is null"
                        + " order by b.name, t.floor",
                Map.of("restaurant", restaurantId), (row, i) -> {
                    UUID branch = row.getObject("branch_id", UUID.class);
                    String floor = row.getString("floor");
                    return new Floor(floorTarget(branch, floor), branch, row.getString("branch_name"), floor);
                });
        List<Branch> branches = jdbc.query("select b.id, b.name from " + schema + "branches b where b.restaurant_id = :restaurant and b.deleted_at is null"
                        + " order by b.name", Map.of("restaurant", restaurantId),
                (row, i) -> new Branch(branchTarget(row.getObject("id", UUID.class)), row.getObject("id", UUID.class), row.getString("name")));
        // Each place under the floor most of its tables are on.
        Map<UUID, String> placeFloor = new HashMap<>();
        Map<UUID, Integer> placeCount = new HashMap<>();
        jdbc.query("select t.category_id, t.floor, count(*) as n from " + schema + "tables t where t.restaurant_id = :restaurant"
                        + " and t.category_id is not null and t.floor is not null and t.floor <> '' group by t.category_id, t.floor",
                Map.of("restaurant", restaurantId), (java.sql.ResultSet row) -> {
                    UUID place = row.getObject("category_id", UUID.class);
                    int n = row.getInt("n");
                    if (n > placeCount.getOrDefault(place, 0)) { placeCount.put(place, n); placeFloor.put(place, row.getString("floor")); }
                });
        List<Place> places = new ArrayList<>();
        jdbc.query("select c.id, c.name, c.branch_id from " + schema + "\"table-categories\" c join " + schema + "branches b on b.id = c.branch_id"
                        + " where b.restaurant_id = :restaurant and b.deleted_at is null order by c.display_order, c.name", Map.of("restaurant", restaurantId), (java.sql.ResultSet row) -> {
                    UUID id = row.getObject("id", UUID.class);
                    if (placeFloor.containsKey(id)) places.add(new Place(placeTarget(id), row.getObject("branch_id", UUID.class), placeFloor.get(id), row.getString("name")));
                });
        List<RoleChoice> roles = jdbc.query("select r.id, r.name from " + schema + "roles r"
                        + " where (r.is_system and r.code not in ('SUPER_ADMIN', 'CO_SUPER_ADMIN')) or r.restaurant_id = :restaurant"
                        + " order by r.rank desc, r.name",
                Map.of("restaurant", restaurantId), (row, i) -> new RoleChoice(row.getObject("id", UUID.class).toString(), row.getString("name")));
        return new Choices(branches, floors, places, roles);
    }

    /** Each person's role ids, for "See others' shifts". */
    @Transactional(readOnly = true)
    public Map<UUID, Set<String>> roleTargetsOf(Collection<UUID> userIds) {
        Map<UUID, Set<String>> out = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) return out;
        jdbc.query("select user_id, role_id from " + schema + "user_roles where user_id in (:users)", Map.of("users", userIds),
                (java.sql.ResultSet row) -> {
                    out.computeIfAbsent(row.getObject("user_id", UUID.class), id -> new HashSet<>()).add(row.getObject("role_id", UUID.class).toString());
                });
        return out;
    }

    public static String branchTarget(UUID branchId) {
        return "branch:" + branchId;
    }

    public static String placeTarget(UUID placeId) {
        return "place:" + placeId;
    }

    /** Whether a table on [floor] of [branchId], in place [placeId] (or none), is among [allowed] (null = all). */
    public static boolean tableAllowed(Set<String> allowed, UUID branchId, String floor, UUID placeId) {
        if (allowed == null) return true;
        return allowed.contains(branchTarget(branchId)) || (floor != null && allowed.contains(floorTarget(branchId, floor)))
                || (placeId != null && allowed.contains(placeTarget(placeId)));
    }

    public static String floorTarget(UUID branchId, String floor) {
        return branchId + "|" + (floor == null ? "" : floor.trim());
    }

    private List<String> targets(String column, UUID owner, UUID restaurantId, String code) {
        return jdbc.queryForList("select target from " + schema + "permission_scopes where " + column + " = :owner and restaurant_id = :restaurant"
                + " and permission_code = :code", Map.of("owner", owner, "restaurant", restaurantId, "code", code), String.class);
    }

    private Map<String, List<String>> byCode(String column, UUID owner, UUID restaurantId) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        jdbc.query("select permission_code, target from " + schema + "permission_scopes where " + column + " = :owner and restaurant_id = :restaurant"
                        + " order by permission_code, target", Map.of("owner", owner, "restaurant", restaurantId),
                (java.sql.ResultSet row) -> {
                    out.computeIfAbsent(row.getString("permission_code"), c -> new ArrayList<>()).add(row.getString("target"));
                });
        return out;
    }

    private void replace(String column, UUID owner, UUID restaurantId, Map<String, List<String>> scopes, UUID actorId) {
        if (restaurantId == null) throw new AuthException("Open a restaurant first", HttpStatus.BAD_REQUEST);
        Map<String, List<String>> asked = scopes == null ? Map.of() : scopes;
        Choices choices = choices(restaurantId);
        Set<String> floors = new HashSet<>();
        choices.branches().forEach(b -> floors.add(b.target()));
        choices.floors().forEach(f -> floors.add(f.target()));
        choices.places().forEach(p -> floors.add(p.target()));
        Set<String> roles = new HashSet<>();
        choices.roles().forEach(r -> roles.add(r.target()));
        asked.forEach((code, targets) -> {
            String kind = PermissionTree.scopeOf(code);
            if (kind == null) throw new AuthException("This permission is given for everything", HttpStatus.BAD_REQUEST);
            if (targets != null && targets.size() > MAX_TARGETS) throw new AuthException("Too many choices", HttpStatus.BAD_REQUEST);
            Set<String> known = "FLOORS".equals(kind) ? floors : roles;
            if (targets != null) for (String target : targets) {
                if (!known.contains(target)) throw new AuthException("Choose from the restaurant's own floors and roles", HttpStatus.BAD_REQUEST);
            }
        });
        jdbc.update("delete from " + schema + "permission_scopes where " + column + " = :owner and restaurant_id = :restaurant",
                Map.of("owner", owner, "restaurant", restaurantId));
        asked.forEach((code, targets) -> {
            if (targets == null) return;
            for (String target : new java.util.LinkedHashSet<>(targets)) {
                Map<String, Object> p = new HashMap<>();
                p.put("id", UUID.randomUUID());
                p.put("restaurant", restaurantId);
                p.put("owner", owner);
                p.put("code", code);
                p.put("target", target);
                p.put("actor", actorId);
                jdbc.update("insert into " + schema + "permission_scopes (id, restaurant_id, " + column + ", permission_code, target, created_by)"
                        + " values (:id, :restaurant, :owner, :code, :target, :actor)", p);
            }
        });
    }
}
