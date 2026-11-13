package pos.pos.restaurant.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pos.pos.restaurant.dto.RestaurantCardResponse;
import pos.pos.restaurant.enums.RestaurantStatus;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The super admins' restaurant list: each restaurant with its city, branches, staff and owner in one query. */
@Repository
public class RestaurantCardRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final String schema;

    public RestaurantCardRepository(NamedParameterJdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid schema");
        this.jdbc = jdbc;
        this.schema = "\"" + schema + "\".";
    }

    /** [searchLike] is a lower-case LIKE pattern or null; [open] true = open only, false = suspended/closed only. */
    public List<RestaurantCardResponse> cards(String searchLike, Boolean open, int limit, int offset) {
        Map<String, Object> p = new HashMap<>();
        p.put("search", searchLike);
        p.put("limit", limit);
        p.put("offset", offset);
        String sql = "select r.id, r.name, r.code, r.status, r.is_active, r.currency, r.timezone, a.city, a.country,"
                + " (select count(*) from " + schema + "branches b where b.restaurant_id = r.id and b.deleted_at is null) branches,"
                + " (select count(*) from " + schema + "branches b where b.restaurant_id = r.id and b.deleted_at is null and b.is_active and b.status = 'ACTIVE') open_branches,"
                + " (select count(*) from " + schema + "users u where u.restaurant_id = r.id and u.deleted_at is null) staff,"
                + " o.id owner_id, trim(coalesce(o.first_name, '') || ' ' || coalesce(o.last_name, '')) owner_name, o.email owner_email,"
                + " o.email_verified owner_verified, inv.status invite_delivery,"
                + " (lk.used_at is null and lk.superseded_at is null and lk.expires_at <= now()) invite_expired,"
                + " br.logo_url, r.cover_url, r.created_at"
                + " from " + schema + "restaurants r"
                + " left join lateral (select ad.city, ad.country from " + schema + "\"restaurant-addresses\" ad where ad.restaurant_id = r.id"
                + "   and ad.deleted_at is null order by ad.is_primary desc, ad.created_at limit 1) a on true"
                + " left join " + schema + "users o on o.id = r.owner_id"
                + " left join lateral (select eo.status from " + schema + "email_outbox eo where eo.user_id = o.id and eo.kind = 'OWNER_INVITE'"
                + "   order by eo.created_at desc limit 1) inv on not o.email_verified"
                + " left join lateral (select t.expires_at, t.used_at, t.superseded_at from " + schema + "auth_password_reset_tokens t"
                + "   where t.user_id = o.id and t.purpose = 'INVITE' order by t.created_at desc limit 1) lk on not o.email_verified"
                + " left join " + schema + "\"restaurant-branding\" br on br.restaurant_id = r.id and br.deleted_at is null"
                + " where r.deleted_at is null and r.status not in ('PENDING','REJECTED')"
                + " and (cast(:search as text) is null or lower(r.name) like :search or lower(r.code) like :search or lower(coalesce(a.city, '')) like :search)"
                + (open == null ? "" : open ? " and r.is_active and r.status = 'ACTIVE'" : " and not (r.is_active and r.status = 'ACTIVE')")
                + " order by r.display_order, lower(r.name), r.id limit :limit offset :offset";
        return jdbc.query(sql, p, (row, i) -> new RestaurantCardResponse(
                row.getObject("id", UUID.class),
                row.getString("name"),
                row.getString("code"),
                RestaurantStatus.valueOf(row.getString("status")),
                row.getBoolean("is_active"),
                row.getString("currency"),
                row.getString("timezone"),
                row.getString("city"),
                row.getString("country"),
                row.getLong("branches"),
                row.getLong("open_branches"),
                row.getLong("staff"),
                row.getObject("owner_id", UUID.class),
                blankToNull(row.getString("owner_name")),
                row.getString("owner_email"),
                row.getObject("owner_id") != null && !row.getBoolean("owner_verified"),
                row.getString("invite_delivery"),
                row.getBoolean("invite_expired"),
                row.getString("logo_url"),
                row.getString("cover_url"),
                row.getObject("created_at", OffsetDateTime.class)
        ));
    }

    public long count(String searchLike, Boolean open) {
        Map<String, Object> p = new HashMap<>();
        p.put("search", searchLike);
        String sql = "select count(*) from " + schema + "restaurants r"
                + " left join lateral (select ad.city from " + schema + "\"restaurant-addresses\" ad where ad.restaurant_id = r.id"
                + "   and ad.deleted_at is null order by ad.is_primary desc, ad.created_at limit 1) a on true"
                + " where r.deleted_at is null and r.status not in ('PENDING','REJECTED')"
                + " and (cast(:search as text) is null or lower(r.name) like :search or lower(r.code) like :search or lower(coalesce(a.city, '')) like :search)"
                + (open == null ? "" : open ? " and r.is_active and r.status = 'ACTIVE'" : " and not (r.is_active and r.status = 'ACTIVE')");
        Long value = jdbc.queryForObject(sql, p, Long.class);
        return value == null ? 0 : value;
    }

    // Any fixed number: the transaction-scoped PostgreSQL lock that serializes giving out list positions.
    private static final long DISPLAY_ORDER_LOCK = 7_302_150_301L;

    /**
     * Waits for any other transaction that is placing or reordering restaurants, until this one commits. Called before
     * {@link #nextDisplayOrder()} and {@link #reorder}, so two restaurants created at once never get the same position.
     */
    public void lockDisplayOrder() {
        jdbc.queryForList("select 1 from (select pg_advisory_xact_lock(:key)) l", Map.of("key", DISPLAY_ORDER_LOCK), Integer.class);
    }

    /** The position after the last restaurant on the list (call {@link #lockDisplayOrder()} first in the same transaction). */
    public int nextDisplayOrder() {
        Integer last = jdbc.queryForObject("select max(display_order) from " + schema + "restaurants where deleted_at is null",
                Map.of(), Integer.class);
        return last == null ? 0 : last + 1;
    }

    /**
     * Puts the listed restaurants first, in the given order; the others keep their order after them.
     */
    public void reorder(List<UUID> restaurantIds) {
        lockDisplayOrder();
        List<UUID> current = jdbc.queryForList("select id from " + schema + "restaurants where deleted_at is null"
                + " order by display_order, lower(name), id", Map.of(), UUID.class);
        List<UUID> order = new java.util.ArrayList<>(restaurantIds.stream().filter(current::contains).distinct().toList());
        current.stream().filter(id -> !order.contains(id)).forEach(order::add);
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            rows.add(Map.of("id", order.get(i), "position", i));
        }
        jdbc.batchUpdate("update " + schema + "restaurants set display_order = :position where id = :id", rows.toArray(Map[]::new));
    }

    private static final String BRANCH_SEARCH = " and (cast(:search as text) is null or lower(b.name) like :search or lower(b.code) like :search"
            + " or lower(coalesce(a.city, '')) like :search or lower(coalesce(a.street_line_1, '')) like :search)";

    /**
     * One page of the restaurant's branches, open ones first, each with its main address and its staff and device
     * counts. [searchLike] is a lower-case LIKE pattern on the name, code, city or street, or null for all.
     */
    public List<pos.pos.restaurant.dto.BranchCardResponse> branchCards(UUID restaurantId, String searchLike, int limit, int offset) {
        Map<String, Object> p = new HashMap<>();
        p.put("restaurant", restaurantId);
        p.put("search", searchLike);
        p.put("limit", limit);
        p.put("offset", offset);
        String sql = "select b.id, b.name, b.code, b.status, b.is_active, b.cover_url, b.phone, b.email, a.street_line_1, a.city, a.country,"
                + " (select count(*) from " + schema + "users u where u.default_branch_id = b.id and u.deleted_at is null) staff,"
                + " (select count(*) from " + schema + "devices d where d.branch_id = b.id and d.status <> 'RETIRED') devices"
                + " from " + schema + "branches b"
                + branchAddress()
                + " where b.restaurant_id = :restaurant and b.deleted_at is null"
                + BRANCH_SEARCH
                + " order by (b.is_active and b.status = 'ACTIVE') desc, lower(b.name), b.id limit :limit offset :offset";
        return jdbc.query(sql, p, (row, i) -> new pos.pos.restaurant.dto.BranchCardResponse(
                row.getObject("id", UUID.class),
                row.getString("name"),
                row.getString("code"),
                pos.pos.restaurant.enums.BranchStatus.valueOf(row.getString("status")),
                row.getBoolean("is_active"),
                row.getString("cover_url"),
                row.getString("phone"),
                row.getString("email"),
                row.getString("street_line_1"),
                row.getString("city"),
                row.getString("country"),
                row.getLong("staff"),
                row.getLong("devices")
        ));
    }

    /** How many branches match [searchLike] (null: all of the restaurant's branches). */
    public long countBranchCards(UUID restaurantId, String searchLike) {
        Map<String, Object> p = new HashMap<>();
        p.put("restaurant", restaurantId);
        p.put("search", searchLike);
        Long value = jdbc.queryForObject("select count(*) from " + schema + "branches b" + branchAddress()
                + " where b.restaurant_id = :restaurant and b.deleted_at is null" + BRANCH_SEARCH, p, Long.class);
        return value == null ? 0 : value;
    }

    /** How many of the restaurant's branches are open, whatever is searched. */
    public long countOpenBranches(UUID restaurantId) {
        Long value = jdbc.queryForObject("select count(*) from " + schema + "branches b where b.restaurant_id = :restaurant"
                + " and b.deleted_at is null and b.is_active and b.status = 'ACTIVE'", Map.of("restaurant", restaurantId), Long.class);
        return value == null ? 0 : value;
    }

    private String branchAddress() {
        return " left join lateral (select ad.street_line_1, ad.city, ad.country from " + schema + "\"branch-addresses\" ad where ad.branch_id = b.id"
                + "   and ad.deleted_at is null order by ad.is_primary desc, ad.created_at limit 1) a on true";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
