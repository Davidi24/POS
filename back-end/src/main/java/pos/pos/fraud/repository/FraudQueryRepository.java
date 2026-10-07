package pos.pos.fraud.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pos.pos.fraud.FraudRule;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Finds the actions each fraud rule looks for, straight from orders, items, discounts and payments. Each rule returns
 * at most {@link #LIMIT} rows (newest first) so a long period can't exhaust memory.
 */
@Repository
public class FraudQueryRepository {

    public static final int LIMIT = 2000;
    private static final String MONEY_STATUSES = "('CAPTURED','PARTIALLY_REFUNDED','REFUNDED')";
    private static final String GROSS = "(l.unit_price_snapshot * l.quantity + l.price_delta_total)";

    private final NamedParameterJdbcTemplate jdbc;
    private final String schema;

    public FraudQueryRepository(
            NamedParameterJdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema
    ) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid schema");
        }
        this.jdbc = jdbc;
        this.schema = "\"" + schema + "\".";
    }

    public record Scope(UUID restaurantId, UUID branchId, OffsetDateTime from, OffsetDateTime to, String zone) {
        Map<String, Object> params() {
            Map<String, Object> params = new HashMap<>();
            params.put("restaurant", restaurantId);
            params.put("from", from);
            params.put("to", to);
            params.put("zone", zone);
            params.put("limit", LIMIT);
            if (branchId != null) {
                params.put("branch", branchId);
            }
            return params;
        }

        String branch(String alias) {
            return branchId == null ? "" : " and " + alias + ".branch_id = :branch";
        }
    }

    /**
     * A raw finding. {@code refId} identifies the action (discount, item, payment, event or "person:day"). {@code
     * extra} carries rule-specific text (item name, discount name, reason) and {@code count} a number (items, refunds).
     */
    public record Finding(FraudRule rule, String refId, OffsetDateTime at, UUID staffId, UUID orderId, String orderNumber,
                          UUID paymentId, BigDecimal amount, String currency, String extra, BigDecimal ratio, long count) {
    }

    public List<Finding> largeDiscounts(Scope scope, int percent) {
        Map<String, Object> params = scope.params();
        params.put("percent", percent);
        return jdbc.query(
                "select d.id::text ref, d.created_at at, d.applied_by staff, o.id order_id, o.order_number, d.amount_applied amount,"
                        + " o.currency, coalesce(d.name, '') || coalesce(' · ' || d.reason, '') extra,"
                        + " round(d.amount_applied * 100 / nullif(o.subtotal, 0), 1) ratio"
                        + " from " + schema + "order_discounts d join " + schema + "orders o on o.id = d.order_id"
                        + " where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and d.created_at >= :from and d.created_at < :to and d.amount_applied > 0 and o.subtotal > 0"
                        + " and d.amount_applied * 100 >= o.subtotal * :percent and o.status <> 'VOIDED'"
                        + " order by d.created_at desc limit :limit",
                params, (rs, n) -> finding(FraudRule.LARGE_DISCOUNT, rs, false));
    }

    public List<Finding> itemsRemovedAfterKitchen(Scope scope) {
        return jdbc.query(
                "select l.id::text ref, l.voided_at at, l.voided_by staff, o.id order_id, o.order_number, " + GROSS + " amount,"
                        + " o.currency, l.quantity || ' × ' || l.item_name_snapshot extra, null::numeric ratio"
                        + " from " + schema + "order_line_items l join " + schema + "orders o on o.id = l.order_id"
                        + " where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and l.status = 'VOIDED' and l.fired_at is not null and l.voided_at >= :from and l.voided_at < :to"
                        + " and o.status <> 'VOIDED' order by l.voided_at desc limit :limit",
                scope.params(), (rs, n) -> finding(FraudRule.ITEM_VOID_AFTER_KITCHEN, rs, false));
    }

    /** People who removed at least {@code limit} items or orders on one restaurant-local day. */
    public List<Finding> manyVoids(Scope scope, int limit) {
        Map<String, Object> params = scope.params();
        params.put("voids", limit);
        return jdbc.query(
                "select x.staff::text || ':' || x.day ref, max(x.last_at) at, x.staff, null::uuid order_id, null order_number,"
                        + " sum(x.value) amount, max(x.currency) currency, x.day::text extra, null::numeric ratio, sum(x.cnt) count"
                        + " from ("
                        + "  select l.voided_by staff, cast(l.voided_at at time zone :zone as date) as day, count(*) cnt, sum(" + GROSS + ") value,"
                        + "   max(l.voided_at) last_at, max(o.currency) currency"
                        + "   from " + schema + "order_line_items l join " + schema + "orders o on o.id = l.order_id"
                        + "   where o.restaurant_id = :restaurant" + scope.branch("o") + " and l.status = 'VOIDED' and l.voided_by is not null"
                        + "   and l.voided_at >= :from and l.voided_at < :to and o.status <> 'VOIDED' group by 1, 2"
                        + "  union all"
                        + "  select o.updated_by staff, cast(o.updated_at at time zone :zone as date) as day, count(*) cnt, sum(o.subtotal) value,"
                        + "   max(o.updated_at) last_at, max(o.currency) currency"
                        + "   from " + schema + "orders o where o.restaurant_id = :restaurant" + scope.branch("o")
                        + "   and o.status = 'VOIDED' and o.updated_by is not null and o.updated_at >= :from and o.updated_at < :to group by 1, 2"
                        + " ) x group by x.staff, x.day having sum(x.cnt) >= :voids order by at desc limit :limit",
                params, (rs, n) -> finding(FraudRule.MANY_VOIDS, rs, true));
    }

    public List<Finding> ordersEndedAfterKitchen(Scope scope, String status, FraudRule rule) {
        Map<String, Object> params = scope.params();
        params.put("status", status);
        return jdbc.query(
                "select o.id::text ref, o.updated_at at, o.updated_by staff, o.id order_id, o.order_number,"
                        + " (select coalesce(sum(" + GROSS + "), 0) from " + schema + "order_line_items l where l.order_id = o.id) amount,"
                        + " o.currency, null extra, null::numeric ratio"
                        + " from " + schema + "orders o where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and o.status = :status and o.updated_at >= :from and o.updated_at < :to"
                        + " and exists (select 1 from " + schema + "order_line_items l where l.order_id = o.id and l.fired_at is not null)"
                        + " order by o.updated_at desc limit :limit",
                params, (rs, n) -> finding(rule, rs, false));
    }

    public List<Finding> largeRefunds(Scope scope, BigDecimal amount) {
        Map<String, Object> params = scope.params();
        params.put("amount", amount);
        return jdbc.query(
                "select t.id::text ref, t.processed_at at, t.created_by staff, o.id order_id, o.order_number, t.amount, p.currency,"
                        + " p.method || coalesce(' · ' || t.reason, '') extra, null::numeric ratio, p.id payment_id"
                        + " from " + schema + "payment_transactions t join " + schema + "payments p on p.id = t.payment_id"
                        + " join " + schema + "orders o on o.id = p.order_id"
                        + " where p.restaurant_id = :restaurant" + scope.branch("p")
                        + " and t.transaction_type = 'REFUND' and t.status = 'APPROVED' and t.processed_at >= :from and t.processed_at < :to"
                        + " and t.amount >= :amount order by t.processed_at desc limit :limit",
                params, (rs, n) -> finding(FraudRule.LARGE_REFUND, rs, false));
    }

    public List<Finding> manyCashRefunds(Scope scope, int limit) {
        Map<String, Object> params = scope.params();
        params.put("refunds", limit);
        return jdbc.query(
                "select x.staff::text || ':' || x.day ref, max(x.at) at, x.staff, null::uuid order_id, null order_number,"
                        + " sum(x.amount) amount, max(x.currency) currency, x.day::text extra, null::numeric ratio, count(*) count from ("
                        + "  select t.created_by staff, cast(t.processed_at at time zone :zone as date) as day, t.processed_at at, t.amount, p.currency"
                        + "  from " + schema + "payment_transactions t join " + schema + "payments p on p.id = t.payment_id"
                        + "  where p.restaurant_id = :restaurant" + scope.branch("p") + " and p.method = 'CASH'"
                        + "  and t.transaction_type = 'REFUND' and t.status = 'APPROVED' and t.created_by is not null"
                        + "  and t.processed_at >= :from and t.processed_at < :to"
                        + " ) x group by x.staff, x.day having count(*) >= :refunds order by at desc limit :limit",
                params, (rs, n) -> finding(FraudRule.CASH_REFUNDS, rs, true));
    }

    public List<Finding> voidedPayments(Scope scope) {
        return jdbc.query(
                "select p.id::text ref, p.voided_at at, p.voided_by staff, o.id order_id, o.order_number,"
                        + " p.amount + p.tip_amount amount, p.currency, p.method || coalesce(' · ' || p.void_reason, '') extra,"
                        + " null::numeric ratio, p.id payment_id"
                        + " from " + schema + "payments p join " + schema + "orders o on o.id = p.order_id"
                        + " where p.restaurant_id = :restaurant" + scope.branch("p")
                        + " and p.status = 'VOIDED' and p.voided_at >= :from and p.voided_at < :to order by p.voided_at desc limit :limit",
                scope.params(), (rs, n) -> finding(FraudRule.PAYMENT_VOIDED, rs, false));
    }

    public List<Finding> reopenedPaidOrders(Scope scope) {
        return jdbc.query(
                "select e.id::text ref, e.created_at at, e.created_by staff, o.id order_id, o.order_number, o.total amount, o.currency,"
                        + " e.note extra, null::numeric ratio"
                        + " from " + schema + "order_events e join " + schema + "orders o on o.id = e.order_id"
                        + " where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and e.event_type = 'REOPENED' and e.created_at >= :from and e.created_at < :to"
                        + " and exists (select 1 from " + schema + "payments p where p.order_id = o.id and p.status in " + MONEY_STATUSES + ")"
                        + " order by e.created_at desc limit :limit",
                scope.params(), (rs, n) -> finding(FraudRule.REOPENED_PAID_ORDER, rs, false));
    }

    public List<Finding> closedWithoutPayment(Scope scope) {
        return jdbc.query(
                "select o.id::text ref, o.closed_at at, o.updated_by staff, o.id order_id, o.order_number,"
                        + " o.total - o.prepaid_total amount, o.currency, o.payment_status extra, null::numeric ratio"
                        + " from " + schema + "orders o where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and o.status = 'CLOSED' and o.closed_at >= :from and o.closed_at < :to and o.total - o.prepaid_total > 0"
                        + " and o.payment_status not in ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')"
                        + " and not exists (select 1 from " + schema + "payments p where p.order_id = o.id and p.status in " + MONEY_STATUSES + ")"
                        + " order by o.closed_at desc limit :limit",
                scope.params(), (rs, n) -> finding(FraudRule.CLOSED_WITHOUT_PAYMENT, rs, false));
    }

    /** Orders set to PAID while the money recorded (payments kept + prepaid) is short of the total. */
    public List<Finding> markedPaidManually(Scope scope) {
        return jdbc.query(
                "select o.id::text ref, o.updated_at at, o.updated_by staff, o.id order_id, o.order_number,"
                        + " o.total - o.prepaid_total - coalesce(paid.kept, 0) amount, o.currency, null extra, null::numeric ratio"
                        + " from " + schema + "orders o left join (select p.order_id,"
                        + "  sum(p.amount - least(p.refunded_amount, p.amount)) kept from " + schema + "payments p"
                        + "  where p.status in " + MONEY_STATUSES + " group by p.order_id) paid on paid.order_id = o.id"
                        + " where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and o.payment_status = 'PAID' and o.updated_at >= :from and o.updated_at < :to"
                        + " and o.total - o.prepaid_total - coalesce(paid.kept, 0) >= 0.01"
                        + " order by o.updated_at desc limit :limit",
                scope.params(), (rs, n) -> finding(FraudRule.MARKED_PAID_MANUALLY, rs, false));
    }

    public List<Finding> highTips(Scope scope, int percent) {
        Map<String, Object> params = scope.params();
        params.put("percent", percent);
        return jdbc.query(
                "select p.id::text ref, p.paid_at at, p.created_by staff, o.id order_id, o.order_number, p.tip_amount amount, p.currency,"
                        + " p.method extra, round(p.tip_amount * 100 / nullif(p.amount, 0), 1) ratio, p.id payment_id"
                        + " from " + schema + "payments p join " + schema + "orders o on o.id = p.order_id"
                        + " where p.restaurant_id = :restaurant" + scope.branch("p")
                        + " and p.status in " + MONEY_STATUSES + " and p.paid_at >= :from and p.paid_at < :to"
                        + " and p.tip_amount > 0 and p.tip_amount * 100 >= p.amount * :percent"
                        + " order by p.paid_at desc limit :limit",
                params, (rs, n) -> finding(FraudRule.HIGH_TIP, rs, false));
    }

    /**
     * Refunds, removals, discounts and cancelled payments by people who use the clock (they have clocked in at this
     * restaurant before) but weren't clocked in at that moment.
     */
    public List<Finding> offShiftActions(Scope scope) {
        return jdbc.query(
                "select a.kind || ':' || a.ref ref, a.at, a.staff, a.order_id, a.order_number, a.amount, a.currency, a.kind extra,"
                        + " null::numeric ratio from (" + sensitiveActions(scope) + ") a"
                        + " where a.staff is not null"
                        + " and exists (select 1 from " + schema + "shifts s0 where s0.user_id = a.staff and s0.restaurant_id = :restaurant"
                        + "   and s0.started_at is not null)"
                        + " and not exists (select 1 from " + schema + "shifts s where s.user_id = a.staff and s.started_at is not null"
                        + "   and s.started_at <= a.at and coalesce(s.ended_at, now()) >= a.at)"
                        + " order by a.at desc limit :limit",
                scope.params(), (rs, n) -> finding(FraudRule.OFF_SHIFT_ACTION, rs, false));
    }

    // ---- Activity feed ----

    public record ActivityRow(String type, String ref, OffsetDateTime at, UUID staffId, UUID orderId, String orderNumber,
                              BigDecimal amount, String currency, String detail, boolean onShift) {
    }

    /** Sensitive actions in the window, newest first, as a page. {@code type} and {@code staffId} narrow it. */
    public List<ActivityRow> activity(Scope scope, String type, UUID staffId, int offset, int size) {
        Map<String, Object> params = scope.params();
        params.put("offset", offset);
        params.put("size", size);
        String filter = filter(params, type, staffId);
        return jdbc.query(
                "select a.*, exists (select 1 from " + schema + "shifts s where s.user_id = a.staff and s.started_at is not null"
                        + " and s.started_at <= a.at and coalesce(s.ended_at, now()) >= a.at) on_shift"
                        + " from (" + sensitiveActions(scope) + ") a where true" + filter
                        + " order by a.at desc, a.ref desc limit :size offset :offset",
                params,
                (rs, n) -> new ActivityRow(rs.getString("kind"), rs.getString("ref"), rs.getObject("at", OffsetDateTime.class),
                        rs.getObject("staff", UUID.class), rs.getObject("order_id", UUID.class), rs.getString("order_number"),
                        rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("detail"), rs.getBoolean("on_shift")));
    }

    public long activityCount(Scope scope, String type, UUID staffId) {
        Map<String, Object> params = scope.params();
        String filter = filter(params, type, staffId);
        Long count = jdbc.queryForObject("select count(*) from (" + sensitiveActions(scope) + ") a where true" + filter, params, Long.class);
        return count == null ? 0 : count;
    }

    private String filter(Map<String, Object> params, String type, UUID staffId) {
        StringBuilder filter = new StringBuilder();
        if (type != null) {
            params.put("kind", type);
            filter.append(" and a.kind = :kind");
        }
        if (staffId != null) {
            params.put("staffFilter", staffId);
            filter.append(" and a.staff = :staffFilter");
        }
        return filter.toString();
    }

    private String sensitiveActions(Scope scope) {
        return "select 'DISCOUNT' kind, d.id::text ref, d.created_at at, d.applied_by staff, o.id order_id, o.order_number,"
                + " d.amount_applied amount, o.currency, coalesce(d.name, '') || coalesce(' · ' || d.reason, '') detail"
                + " from " + schema + "order_discounts d join " + schema + "orders o on o.id = d.order_id"
                + " where o.restaurant_id = :restaurant" + scope.branch("o") + " and d.created_at >= :from and d.created_at < :to and d.amount_applied > 0"
                + " union all select 'ITEM_REMOVED', l.id::text, l.voided_at, l.voided_by, o.id, o.order_number, " + GROSS + ", o.currency,"
                + " l.quantity || ' × ' || l.item_name_snapshot || case when l.fired_at is not null then ' (after cooking)' else '' end"
                + " from " + schema + "order_line_items l join " + schema + "orders o on o.id = l.order_id"
                + " where o.restaurant_id = :restaurant" + scope.branch("o") + " and l.status = 'VOIDED' and l.voided_at >= :from and l.voided_at < :to"
                + " and o.status <> 'VOIDED'"
                + " union all select 'ORDER_' || o.status, o.id::text, o.updated_at, o.updated_by, o.id, o.order_number, o.subtotal, o.currency, null"
                + " from " + schema + "orders o where o.restaurant_id = :restaurant" + scope.branch("o")
                + " and o.status in ('VOIDED', 'CANCELLED') and o.updated_at >= :from and o.updated_at < :to"
                + " union all select 'REFUND', t.id::text, t.processed_at, t.created_by, o.id, o.order_number, t.amount, p.currency,"
                + " p.method || coalesce(' · ' || t.reason, '')"
                + " from " + schema + "payment_transactions t join " + schema + "payments p on p.id = t.payment_id"
                + " join " + schema + "orders o on o.id = p.order_id where p.restaurant_id = :restaurant" + scope.branch("p")
                + " and t.transaction_type = 'REFUND' and t.status = 'APPROVED' and t.processed_at >= :from and t.processed_at < :to"
                + " union all select 'PAYMENT_CANCELLED', p.id::text, p.voided_at, p.voided_by, o.id, o.order_number, p.amount + p.tip_amount,"
                + " p.currency, p.method || coalesce(' · ' || p.void_reason, '')"
                + " from " + schema + "payments p join " + schema + "orders o on o.id = p.order_id where p.restaurant_id = :restaurant" + scope.branch("p")
                + " and p.status = 'VOIDED' and p.voided_at >= :from and p.voided_at < :to"
                + " union all select 'ORDER_REOPENED', e.id::text, e.created_at, e.created_by, o.id, o.order_number, o.total, o.currency, e.note"
                + " from " + schema + "order_events e join " + schema + "orders o on o.id = e.order_id where o.restaurant_id = :restaurant" + scope.branch("o")
                + " and e.event_type = 'REOPENED' and e.created_at >= :from and e.created_at < :to";
    }

    // ---- Mapping ----

    private static Finding finding(FraudRule rule, ResultSet rs, boolean counted) throws SQLException {
        UUID paymentId = null;
        try {
            paymentId = rs.getObject("payment_id", UUID.class);
        } catch (SQLException ignored) {
            // Only payment rules select it.
        }
        return new Finding(
                rule,
                rs.getString("ref"),
                rs.getObject("at", OffsetDateTime.class),
                rs.getObject("staff", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("order_number"),
                paymentId,
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("extra"),
                rs.getBigDecimal("ratio"),
                counted ? rs.getLong("count") : 0
        );
    }

    static LocalDate day(String text) {
        return text == null ? null : LocalDate.parse(text);
    }
}
