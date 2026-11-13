package pos.pos.restaurant.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What is still going on at a restaurant or branch: open orders, people clocked in, bookings still to come, money
 * waiting. Used before a branch is removed, the currency changes or the time zone moves.
 */
@Repository
public class RestaurantUsageRepository {

    // Bookings that still matter: not finished, cancelled or missed.
    private static final String LIVE_BOOKING = "r.status in ('PENDING','CONFIRMED','CHECKED_IN','SEATED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final String schema;

    public RestaurantUsageRepository(NamedParameterJdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid schema");
        this.jdbc = jdbc;
        this.schema = "\"" + schema + "\".";
    }

    public record BranchUsage(long openOrders, long upcomingBookings, long openShifts, long staff, long devices) {
        public boolean inUse() {
            return openOrders + upcomingBookings + openShifts + staff + devices > 0;
        }
    }

    public BranchUsage branchUsage(UUID restaurantId, UUID branchId, OffsetDateTime now) {
        Map<String, Object> p = new HashMap<>();
        p.put("restaurant", restaurantId);
        p.put("branch", branchId);
        p.put("now", now);
        return new BranchUsage(
                count("select count(*) from " + schema + "orders o where o.restaurant_id = :restaurant and o.branch_id = :branch"
                        + " and o.status in ('DRAFT','OPEN')", p),
                count("select count(*) from " + schema + "reservations r where r.restaurant_id = :restaurant and r.branch_id = :branch"
                        + " and " + LIVE_BOOKING + " and r.reservation_end > :now", p),
                count("select count(*) from " + schema + "shifts s where s.restaurant_id = :restaurant and s.branch_id = :branch"
                        + " and s.status in ('OPEN','ON_BREAK')", p),
                count("select count(*) from " + schema + "users u where u.restaurant_id = :restaurant and u.default_branch_id = :branch"
                        + " and u.deleted_at is null", p),
                count("select count(*) from " + schema + "devices d where d.restaurant_id = :restaurant and d.branch_id = :branch"
                        + " and d.status <> 'RETIRED'", p)
        );
    }

    /** Things that must be settled before the restaurant's currency changes (they were priced in the old one). */
    public record MoneyInFlight(long openOrders, long openShifts, long pendingBookingPayments, long depositsOnUpcomingBookings,
                                long waitingPreOrders) {
    }

    public MoneyInFlight moneyInFlight(UUID restaurantId, OffsetDateTime now) {
        Map<String, Object> p = Map.of("restaurant", restaurantId, "now", now);
        return new MoneyInFlight(
                count("select count(*) from " + schema + "orders o where o.restaurant_id = :restaurant and o.status in ('DRAFT','OPEN')", p),
                count("select count(*) from " + schema + "shifts s where s.restaurant_id = :restaurant and s.status in ('OPEN','ON_BREAK')", p),
                count("select count(*) from " + schema + "reservation_payments rp join " + schema + "reservations r on r.id = rp.reservation_id"
                        + " where r.restaurant_id = :restaurant and rp.status = 'PENDING'", p),
                count("select count(*) from " + schema + "reservations r where r.restaurant_id = :restaurant and " + LIVE_BOOKING
                        + " and r.reservation_end > :now and r.deposit_status in ('PENDING','PAID','PARTIALLY_PAID')", p),
                count("select count(*) from " + schema + "pre_orders po where po.restaurant_id = :restaurant and po.status = 'SCHEDULED'", p)
        );
    }

    /** Bookings still to come, for the time zone warning. */
    public long upcomingBookings(UUID restaurantId, OffsetDateTime now) {
        return count("select count(*) from " + schema + "reservations r where r.restaurant_id = :restaurant and " + LIVE_BOOKING
                + " and r.reservation_end > :now", Map.of("restaurant", restaurantId, "now", now));
    }

    public record UpcomingBooking(String code, String name, OffsetDateTime start) {
    }

    /** The next few bookings, to show how their times read after a time zone change. */
    public List<UpcomingBooking> nextBookings(UUID restaurantId, OffsetDateTime now, int limit) {
        return jdbc.query("select r.reservation_code, r.contact_name, r.reservation_start from " + schema + "reservations r"
                        + " where r.restaurant_id = :restaurant and " + LIVE_BOOKING + " and r.reservation_end > :now"
                        + " order by r.reservation_start limit :limit",
                Map.of("restaurant", restaurantId, "now", now, "limit", limit),
                (row, i) -> new UpcomingBooking(row.getString(1), row.getString(2), row.getObject(3, OffsetDateTime.class)));
    }

    public long openOrders(UUID restaurantId) {
        return count("select count(*) from " + schema + "orders o where o.restaurant_id = :restaurant and o.status in ('DRAFT','OPEN')",
                Map.of("restaurant", restaurantId));
    }

    public long openShifts(UUID restaurantId) {
        return count("select count(*) from " + schema + "shifts s where s.restaurant_id = :restaurant and s.status in ('OPEN','ON_BREAK')",
                Map.of("restaurant", restaurantId));
    }

    private long count(String sql, Map<String, ?> params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }
}
