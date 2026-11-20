package pos.pos.admins;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Support admins' restaurants, the Admins page and restaurants' requests to the Super Admin, in plain SQL. */
@Repository
public class AdminRepository {

    private static final String NAME = "trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, ''))";

    private final NamedParameterJdbcTemplate jdbc;
    private final String schema;

    public AdminRepository(NamedParameterJdbcTemplate jdbc, @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid schema");
        this.jdbc = jdbc;
        this.schema = "\"" + schema + "\".";
    }

    public List<AdminDtos.RestaurantRef> restaurantsOf(UUID adminId) {
        return jdbc.query("select r.id, r.name from " + schema + "admin_restaurant_access a join " + schema + "restaurants r on r.id = a.restaurant_id"
                        + " where a.user_id = :admin and r.deleted_at is null order by lower(r.name)",
                Map.of("admin", adminId), (row, i) -> new AdminDtos.RestaurantRef(row.getObject("id", UUID.class), row.getString("name")));
    }

    public void replaceRestaurants(UUID adminId, List<UUID> restaurantIds, UUID actorId) {
        jdbc.update("delete from " + schema + "admin_restaurant_access where user_id = :admin", Map.of("admin", adminId));
        for (UUID restaurantId : restaurantIds) {
            Map<String, Object> p = new HashMap<>();
            p.put("admin", adminId);
            p.put("restaurant", restaurantId);
            p.put("actor", actorId);
            jdbc.update("insert into " + schema + "admin_restaurant_access (user_id, restaurant_id, created_by)"
                    + " select :admin, r.id, :actor from " + schema + "restaurants r where r.id = :restaurant and r.deleted_at is null", p);
        }
    }

    /** Support admins (CO_SUPER_ADMIN) and every restaurant's admins (ADMIN), not removed. */
    public List<AdminDtos.AdminCard> adminCards() {
        String sql = "select u.id, " + NAME + " as name, u.username, u.email, u.phone, u.avatar_url, u.is_active, r.code,"
                + " u.admin_all_restaurants, u.admin_may_remove_owners, u.restaurant_id, rest.name as restaurant_name"
                + " from " + schema + "users u join " + schema + "user_roles ur on ur.user_id = u.id join " + schema + "roles r on r.id = ur.role_id"
                + " left join " + schema + "restaurants rest on rest.id = u.restaurant_id"
                + " where u.deleted_at is null and r.code in ('CO_SUPER_ADMIN', 'ADMIN') and r.is_system"
                + " order by case when r.code = 'CO_SUPER_ADMIN' then 0 else 1 end, lower(" + NAME + ")";
        List<AdminDtos.AdminCard> cards = new ArrayList<>();
        jdbc.query(sql, Map.of(), (ResultSet row) -> {
            boolean support = "CO_SUPER_ADMIN".equals(row.getString("code"));
            UUID id = row.getObject("id", UUID.class);
            UUID restaurantId = row.getObject("restaurant_id", UUID.class);
            List<AdminDtos.RestaurantRef> restaurants = support ? restaurantsOf(id)
                    : restaurantId == null ? List.of() : List.of(new AdminDtos.RestaurantRef(restaurantId, row.getString("restaurant_name")));
            cards.add(new AdminDtos.AdminCard(id, nameOr(row), row.getString("email"), row.getString("phone"), row.getString("avatar_url"),
                    row.getBoolean("is_active"), support ? "SUPPORT" : "RESTAURANT", support && row.getBoolean("admin_all_restaurants"),
                    support && row.getBoolean("admin_may_remove_owners"), restaurants));
        });
        // Someone with both roles shows once, as a support admin.
        Map<UUID, AdminDtos.AdminCard> once = new java.util.LinkedHashMap<>();
        cards.forEach(card -> once.putIfAbsent(card.id(), card));
        return List.copyOf(once.values());
    }

    /** The support admins who take care of [restaurantId] (given it, or every restaurant). */
    public List<AdminDtos.SupportContact> supportAdminsOf(UUID restaurantId) {
        String sql = "select distinct u.id, " + NAME + " as name, u.username, u.email, u.phone, u.avatar_url from " + schema + "users u"
                + " join " + schema + "user_roles ur on ur.user_id = u.id join " + schema + "roles r on r.id = ur.role_id and r.code = 'CO_SUPER_ADMIN'"
                + " where u.deleted_at is null and u.is_active and (u.admin_all_restaurants or exists (select 1 from " + schema
                + "admin_restaurant_access a where a.user_id = u.id and a.restaurant_id = :restaurant))";
        return jdbc.query(sql, Map.of("restaurant", restaurantId), (row, i) -> new AdminDtos.SupportContact(row.getObject("id", UUID.class),
                nameOr(row), row.getString("email"), row.getString("phone"), row.getString("avatar_url")));
    }

    public void insertRequest(UUID id, UUID restaurantId, String kind, UUID targetUserId, String note, UUID createdBy, OffsetDateTime at) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        p.put("restaurant", restaurantId);
        p.put("kind", kind);
        p.put("target", targetUserId);
        p.put("note", note);
        p.put("by", createdBy);
        p.put("at", at);
        jdbc.update("insert into " + schema + "platform_requests (id, restaurant_id, kind, target_user_id, note, status, created_by, created_at)"
                + " values (:id, :restaurant, :kind, :target, :note, 'PENDING', :by, :at)", p);
    }

    public boolean pendingExists(UUID restaurantId, String kind, UUID targetUserId) {
        Map<String, Object> p = new HashMap<>();
        p.put("restaurant", restaurantId);
        p.put("kind", kind);
        p.put("target", targetUserId);
        Boolean any = jdbc.queryForObject("select exists (select 1 from " + schema + "platform_requests where restaurant_id = :restaurant"
                + " and kind = :kind and status = 'PENDING' and (cast(:target as uuid) is null or target_user_id = :target))", p, Boolean.class);
        return Boolean.TRUE.equals(any);
    }

    /** Requests: one restaurant's when [restaurantId] is set, else everyone's; only waiting ones with [pendingOnly]. */
    public List<AdminDtos.PlatformRequest> requests(UUID restaurantId, boolean pendingOnly) {
        Map<String, Object> p = new HashMap<>();
        p.put("restaurant", restaurantId);
        String sql = "select q.*, r.name as restaurant_name,"
                + " (select " + NAME + " from " + schema + "users u where u.id = q.target_user_id) as target_name,"
                + " (select " + NAME + " from " + schema + "users u where u.id = q.created_by) as created_by_name,"
                + " (select " + NAME + " from " + schema + "users u where u.id = q.decided_by) as decided_by_name"
                + " from " + schema + "platform_requests q join " + schema + "restaurants r on r.id = q.restaurant_id"
                + " where (cast(:restaurant as uuid) is null or q.restaurant_id = :restaurant)"
                + (pendingOnly ? " and q.status = 'PENDING'" : "")
                + " order by case when q.status = 'PENDING' then 0 else 1 end, q.created_at desc limit 200";
        return jdbc.query(sql, p, (row, i) -> request(row));
    }

    public AdminDtos.PlatformRequest request(UUID id) {
        List<AdminDtos.PlatformRequest> found = jdbc.query("select q.*, r.name as restaurant_name,"
                + " (select " + NAME + " from " + schema + "users u where u.id = q.target_user_id) as target_name,"
                + " (select " + NAME + " from " + schema + "users u where u.id = q.created_by) as created_by_name,"
                + " (select " + NAME + " from " + schema + "users u where u.id = q.decided_by) as decided_by_name"
                + " from " + schema + "platform_requests q join " + schema + "restaurants r on r.id = q.restaurant_id where q.id = :id",
                Map.of("id", id), (row, i) -> request(row));
        return found.isEmpty() ? null : found.get(0);
    }

    public int decide(UUID id, boolean approve, UUID actorId, String note, OffsetDateTime at) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        p.put("status", approve ? "APPROVED" : "DECLINED");
        p.put("by", actorId);
        p.put("note", note);
        p.put("at", at);
        return jdbc.update("update " + schema + "platform_requests set status = :status, decided_by = :by, decided_at = :at, decision_note = :note"
                + " where id = :id and status = 'PENDING'", p);
    }

    private static AdminDtos.PlatformRequest request(ResultSet row) throws SQLException {
        return new AdminDtos.PlatformRequest(row.getObject("id", UUID.class), row.getObject("restaurant_id", UUID.class),
                row.getString("restaurant_name"), row.getString("kind"), row.getObject("target_user_id", UUID.class), blankToNull(row.getString("target_name")),
                row.getString("note"), row.getString("status"), row.getObject("created_by", UUID.class), blankToNull(row.getString("created_by_name")),
                row.getObject("created_at", OffsetDateTime.class), blankToNull(row.getString("decided_by_name")),
                row.getObject("decided_at", OffsetDateTime.class), row.getString("decision_note"));
    }

    private static String nameOr(ResultSet row) throws SQLException {
        String name = row.getString("name");
        return name == null || name.isBlank() ? row.getString("username") : name;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
