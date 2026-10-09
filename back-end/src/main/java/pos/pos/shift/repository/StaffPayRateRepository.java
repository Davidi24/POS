package pos.pos.shift.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Hourly wages per staff member, and the recorded tips on their orders. */
@Repository
public class StaffPayRateRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final String schema;

    public StaffPayRateRepository(NamedParameterJdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid schema");
        this.jdbc = jdbc; this.schema = "\"" + schema + "\".";
    }

    public Optional<BigDecimal> rate(UUID userId) {
        return jdbc.queryForList("select hourly_rate from " + schema + "staff_pay_rates where user_id=:user",
                Map.of("user", userId), BigDecimal.class).stream().findFirst();
    }

    public Map<UUID, BigDecimal> rates(UUID restaurantId) {
        var result = new HashMap<UUID, BigDecimal>();
        jdbc.query("select user_id, hourly_rate from " + schema + "staff_pay_rates where restaurant_id=:restaurant",
                Map.of("restaurant", restaurantId), row -> { result.put(row.getObject("user_id", UUID.class), row.getBigDecimal("hourly_rate")); });
        return result;
    }

    public void save(UUID restaurantId, UUID userId, BigDecimal hourlyRate, UUID actor) {
        var p = new HashMap<String, Object>();
        p.put("user", userId); p.put("restaurant", restaurantId); p.put("rate", hourlyRate);
        p.put("now", OffsetDateTime.now(ZoneOffset.UTC)); p.put("actor", actor);
        jdbc.update("insert into " + schema + "staff_pay_rates (user_id, restaurant_id, hourly_rate, updated_at, updated_by)"
                + " values (:user, :restaurant, :rate, :now, :actor) on conflict (user_id) do update"
                + " set hourly_rate=excluded.hourly_rate, restaurant_id=excluded.restaurant_id, updated_at=excluded.updated_at, updated_by=excluded.updated_by", p);
    }

    /** Worked shifts recorded before this person had a wage take the first wage set, so later raises leave them alone. */
    public int fillMissingShiftRates(UUID userId, BigDecimal hourlyRate) {
        return jdbc.update("update " + schema + "shifts set hourly_rate=:rate where user_id=:user and hourly_rate is null and started_at is not null",
                Map.of("user", userId, "rate", hourlyRate));
    }

    /** Tips retained after refunds on each staff member's orders per restaurant-local day, in one currency. */
    public List<TipRow> tips(UUID restaurantId, UUID branchId, UUID staffId, OffsetDateTime from, OffsetDateTime to, String zone, String currency) {
        var p = new HashMap<String, Object>();
        p.put("restaurant", restaurantId); p.put("branch", branchId); p.put("from", from); p.put("to", to);
        p.put("zone", zone); p.put("currency", currency);
        if (staffId != null) p.put("staff", staffId);
        // Refunds are allocated to the bill first and then the tip. Do not report refunded tips as staff earnings.
        String sql = "select o.created_by as staff, to_char(p.paid_at at time zone :zone, 'YYYY-MM-DD') as tip_day, coalesce(sum(greatest(p.tip_amount - greatest(p.refunded_amount - p.amount, 0), 0)),0) as tips"
                + " from " + schema + "payments p join " + schema + "orders o on o.id=p.order_id"
                + " where p.restaurant_id=:restaurant and p.branch_id=:branch and o.restaurant_id=:restaurant and o.branch_id=:branch"
                + " and p.status in ('CAPTURED','PARTIALLY_REFUNDED','REFUNDED') and p.paid_at>=:from and p.paid_at<:to and p.currency=:currency"
                + " and o.created_by is not null" + (staffId == null ? "" : " and o.created_by=:staff")
                + " group by o.created_by, tip_day";
        return jdbc.query(sql, p, (row, i) -> new TipRow(row.getObject("staff", UUID.class), row.getString("tip_day"), row.getBigDecimal("tips")));
    }

    public record TipRow(UUID staffId, String day, BigDecimal tips) {}
}
