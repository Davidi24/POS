package pos.pos.report.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pos.pos.report.dto.StatisticsDtos.DaySales;
import pos.pos.report.dto.StatisticsDtos.HourSales;
import pos.pos.report.dto.StatisticsDtos.ItemSales;
import pos.pos.report.dto.StatisticsDtos.MethodTotal;
import pos.pos.report.dto.StatisticsDtos.SectionSales;
import pos.pos.report.dto.StatisticsDtos.Share;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates straight from the database, so a busy year never loads its orders into memory. Every query is scoped
 * to one restaurant, optionally one branch, one currency and a [from, to) time window.
 */
@Repository
public class StatisticsRepository {

    private static final String MONEY_STATUSES = "('CAPTURED','PARTIALLY_REFUNDED','REFUNDED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final String schema;

    public StatisticsRepository(
            NamedParameterJdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema
    ) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid reporting schema");
        }
        this.jdbc = jdbc;
        this.schema = "\"" + schema + "\".";
    }

    /** The window and scope of one query. {@code branchId} null means every branch of the restaurant. */
    public record Scope(UUID restaurantId, UUID branchId, String currency, OffsetDateTime from, OffsetDateTime to, String zone) {
        Map<String, Object> params() {
            Map<String, Object> params = new HashMap<>();
            params.put("restaurant", restaurantId);
            params.put("currency", currency);
            params.put("from", from);
            params.put("to", to);
            params.put("zone", zone);
            if (branchId != null) {
                params.put("branch", branchId);
            }
            return params;
        }

        String branch(String alias) {
            return branchId == null ? "" : " and " + alias + ".branch_id = :branch";
        }
    }

    public record SalesTotals(long orders, BigDecimal sales, long guests, BigDecimal discounts) {
    }

    public record PaymentTotals(BigDecimal taken, BigDecimal tips, BigDecimal refunds) {
    }

    public record StaffRow(UUID staffId, long orders, BigDecimal sales, long guests) {
    }

    private String closedOrders(Scope scope) {
        return "o.restaurant_id = :restaurant" + scope.branch("o")
                + " and o.status = 'CLOSED' and o.closed_at >= :from and o.closed_at < :to and o.currency = :currency";
    }

    public SalesTotals sales(Scope scope) {
        Map<String, Object> row = jdbc.queryForMap(
                "select count(*) orders, coalesce(sum(o.total), 0) sales, coalesce(sum(o.guest_count), 0) guests,"
                        + " coalesce(sum(o.discount_total), 0) discounts from " + schema + "orders o where " + closedOrders(scope),
                scope.params());
        return new SalesTotals(number(row, "orders"), money(row, "sales"), number(row, "guests"), money(row, "discounts"));
    }

    /** Money taken in the window (bill + tip) and refunds given in the window, by when each happened. */
    public PaymentTotals payments(Scope scope) {
        Map<String, Object> taken = jdbc.queryForMap(
                "select coalesce(sum(p.amount + p.tip_amount + p.surcharge_amount), 0) taken,"
                        + " coalesce(sum(greatest(p.tip_amount - greatest(p.refunded_amount - p.amount, 0), 0)), 0) tips"
                        + " from " + schema + "payments p where p.restaurant_id = :restaurant" + scope.branch("p")
                        + " and p.status in " + MONEY_STATUSES + " and p.paid_at >= :from and p.paid_at < :to and p.currency = :currency",
                scope.params());
        BigDecimal refunds = jdbc.queryForObject(
                "select coalesce(sum(t.amount), 0) from " + schema + "payment_transactions t join " + schema + "payments p on p.id = t.payment_id"
                        + " where p.restaurant_id = :restaurant" + scope.branch("p")
                        + " and t.transaction_type = 'REFUND' and t.status = 'APPROVED' and t.processed_at >= :from and t.processed_at < :to"
                        + " and p.currency = :currency",
                scope.params(), BigDecimal.class);
        return new PaymentTotals(money(taken, "taken"), money(taken, "tips"), refunds == null ? BigDecimal.ZERO : refunds);
    }

    /** What removed items were worth, by when they were removed. */
    public BigDecimal removedItemsValue(Scope scope) {
        BigDecimal value = jdbc.queryForObject(
                "select coalesce(sum(l.unit_price_snapshot * l.quantity + l.price_delta_total), 0) from " + schema + "order_line_items l"
                        + " join " + schema + "orders o on o.id = l.order_id where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and l.status = 'VOIDED' and l.voided_at >= :from and l.voided_at < :to and o.currency = :currency",
                scope.params(), BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    public long ordersEndedAs(Scope scope, String status) {
        Map<String, Object> params = scope.params();
        params.put("status", status);
        Long count = jdbc.queryForObject(
                "select count(*) from " + schema + "orders o where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and o.status = :status and o.updated_at >= :from and o.updated_at < :to",
                params, Long.class);
        return count == null ? 0 : count;
    }

    public long openOrders(Scope scope) {
        Long count = jdbc.queryForObject(
                "select count(*) from " + schema + "orders o where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and o.status in ('OPEN', 'DRAFT')",
                scope.params(), Long.class);
        return count == null ? 0 : count;
    }

    public long otherCurrencyOrders(Scope scope) {
        Long count = jdbc.queryForObject(
                "select count(*) from " + schema + "orders o where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and o.status = 'CLOSED' and o.closed_at >= :from and o.closed_at < :to and o.currency <> :currency",
                scope.params(), Long.class);
        return count == null ? 0 : count;
    }

    public List<DaySales> salesByDay(Scope scope) {
        return jdbc.query(
                "select cast(o.closed_at at time zone :zone as date) as day, coalesce(sum(o.total), 0) sales, count(*) orders,"
                        + " coalesce(sum(o.guest_count), 0) guests from " + schema + "orders o where " + closedOrders(scope)
                        + " group by 1 order by 1",
                scope.params(),
                (rs, n) -> new DaySales(rs.getObject("day", LocalDate.class), rs.getBigDecimal("sales"), rs.getLong("orders"), rs.getLong("guests")));
    }

    public List<HourSales> salesByHour(Scope scope) {
        return jdbc.query(
                "select cast(extract(hour from o.closed_at at time zone :zone) as integer) as hour, coalesce(sum(o.total), 0) sales,"
                        + " count(*) orders from " + schema + "orders o where " + closedOrders(scope) + " group by 1 order by 1",
                scope.params(),
                (rs, n) -> new HourSales(rs.getInt("hour"), rs.getBigDecimal("sales"), rs.getLong("orders")));
    }

    public List<Share> orderTypes(Scope scope) {
        return shares(scope, "o.order_type");
    }

    public List<Share> sources(Scope scope) {
        return shares(scope, "o.source");
    }

    public List<Share> floors(Scope scope) {
        return jdbc.query(
                "select coalesce(t.floor, 'No table') as share_key, coalesce(sum(o.total), 0) sales, count(*) orders from " + schema + "orders o"
                        + " left join " + schema + "tables t on t.id = o.table_id where " + closedOrders(scope)
                        + " group by 1 order by sales desc, share_key",
                scope.params(),
                (rs, n) -> new Share(rs.getString("share_key"), rs.getBigDecimal("sales"), rs.getLong("orders")));
    }

    private List<Share> shares(Scope scope, String column) {
        return jdbc.query(
                "select " + column + " as share_key, coalesce(sum(o.total), 0) sales, count(*) orders from " + schema + "orders o where "
                        + closedOrders(scope) + " group by 1 order by sales desc, share_key",
                scope.params(),
                (rs, n) -> new Share(rs.getString("share_key"), rs.getBigDecimal("sales"), rs.getLong("orders")));
    }

    public List<MethodTotal> paymentMethods(Scope scope) {
        return jdbc.query(
                "select p.method, coalesce(sum(p.amount + p.tip_amount + p.surcharge_amount - p.refunded_amount), 0) collected,"
                        + " count(*) count, coalesce(sum(greatest(p.tip_amount - greatest(p.refunded_amount - p.amount, 0), 0)), 0) tips from " + schema + "payments p"
                        + " where p.restaurant_id = :restaurant" + scope.branch("p") + " and p.status in " + MONEY_STATUSES
                        + " and p.paid_at >= :from and p.paid_at < :to and p.currency = :currency"
                        + " group by p.method order by collected desc, p.method",
                scope.params(),
                (rs, n) -> new MethodTotal(rs.getString("method"), rs.getBigDecimal("collected"), rs.getLong("count"), rs.getBigDecimal("tips")));
    }

    public List<ItemSales> topItems(Scope scope, int limit) {
        Map<String, Object> params = scope.params();
        params.put("limit", limit);
        return jdbc.query(
                "select l.menu_item_id, l.item_name_snapshot as name, max(ms.name) as section, sum(l.quantity) quantity,"
                        + " coalesce(sum(l.line_total), 0) sales from " + schema + "orders o join " + schema + "order_line_items l on l.order_id = o.id"
                        + " left join " + schema + "\"menu-items\" mi on mi.id = l.menu_item_id"
                        + " left join " + schema + "\"menu-sections\" ms on ms.id = mi.section_id"
                        + " where " + closedOrders(scope) + " and l.status not in ('CANCELLED', 'VOIDED')"
                        + " group by l.menu_item_id, l.item_name_snapshot order by quantity desc, sales desc, name limit :limit",
                params,
                (rs, n) -> new ItemSales(rs.getObject("menu_item_id", UUID.class), rs.getString("name"), rs.getString("section"),
                        rs.getLong("quantity"), rs.getBigDecimal("sales")));
    }

    /** Dishes on the restaurant's active menus that sold least (including not at all) in the window. */
    public List<ItemSales> slowItems(Scope scope, int limit) {
        Map<String, Object> params = scope.params();
        params.put("limit", limit);
        return jdbc.query(
                "select mi.id as menu_item_id, mi.name, ms.name as section, coalesce(sold.quantity, 0) quantity, coalesce(sold.sales, 0) sales"
                        + " from " + schema + "\"menu-items\" mi join " + schema + "\"menu-sections\" ms on ms.id = mi.section_id"
                        + " join " + schema + "menus m on m.id = ms.menu_id"
                        + " left join (select l.menu_item_id, sum(l.quantity) quantity, sum(l.line_total) sales from " + schema + "orders o"
                        + " join " + schema + "order_line_items l on l.order_id = o.id where " + closedOrders(scope)
                        + " and l.status not in ('CANCELLED', 'VOIDED') group by l.menu_item_id) sold on sold.menu_item_id = mi.id"
                        + " where m.restaurant_id = :restaurant and m.is_active = true and ms.is_active = true and mi.is_available = true"
                        + " order by quantity asc, sales asc, mi.name limit :limit",
                params,
                (rs, n) -> new ItemSales(rs.getObject("menu_item_id", UUID.class), rs.getString("name"), rs.getString("section"),
                        rs.getLong("quantity"), rs.getBigDecimal("sales")));
    }

    public List<SectionSales> sections(Scope scope) {
        return jdbc.query(
                "select coalesce(ms.name, 'Other') as section, sum(l.quantity) quantity, coalesce(sum(l.line_total), 0) sales from " + schema + "orders o"
                        + " join " + schema + "order_line_items l on l.order_id = o.id"
                        + " left join " + schema + "\"menu-items\" mi on mi.id = l.menu_item_id"
                        + " left join " + schema + "\"menu-sections\" ms on ms.id = mi.section_id"
                        + " where " + closedOrders(scope) + " and l.status not in ('CANCELLED', 'VOIDED')"
                        + " group by 1 order by sales desc, section",
                scope.params(),
                (rs, n) -> new SectionSales(rs.getString("section"), rs.getLong("quantity"), rs.getBigDecimal("sales")));
    }

    // ---- Staff ----

    public List<StaffRow> staffSales(Scope scope) {
        return jdbc.query(
                "select o.created_by as staff, count(*) orders, coalesce(sum(o.total), 0) sales, coalesce(sum(o.guest_count), 0) guests"
                        + " from " + schema + "orders o where " + closedOrders(scope) + " and o.created_by is not null group by o.created_by",
                scope.params(),
                (rs, n) -> new StaffRow(rs.getObject("staff", UUID.class), rs.getLong("orders"), rs.getBigDecimal("sales"), rs.getLong("guests")));
    }

    /** Tips on orders each person created, by payment time (the same rule as staff pay). */
    public Map<UUID, BigDecimal> staffTips(Scope scope) {
        return moneyByStaff(scope,
                "select o.created_by as staff, coalesce(sum(greatest(p.tip_amount - greatest(p.refunded_amount - p.amount, 0), 0)), 0) value from " + schema + "payments p join " + schema + "orders o on o.id = p.order_id"
                        + " where p.restaurant_id = :restaurant" + scope.branch("p") + " and p.status in " + MONEY_STATUSES
                        + " and p.paid_at >= :from and p.paid_at < :to and p.currency = :currency and o.created_by is not null group by o.created_by");
    }

    public Map<UUID, BigDecimal> staffDiscounts(Scope scope) {
        return moneyByStaff(scope,
                "select d.applied_by as staff, coalesce(sum(d.amount_applied), 0) value from " + schema + "order_discounts d join " + schema + "orders o on o.id = d.order_id"
                        + " where " + closedOrders(scope) + " and d.applied_by is not null group by d.applied_by");
    }

    public Map<UUID, BigDecimal> staffRemovedValue(Scope scope) {
        return moneyByStaff(scope,
                "select l.voided_by as staff, coalesce(sum(l.unit_price_snapshot * l.quantity + l.price_delta_total), 0) value from " + schema + "order_line_items l"
                        + " join " + schema + "orders o on o.id = l.order_id where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and l.status = 'VOIDED' and l.voided_by is not null and l.voided_at >= :from and l.voided_at < :to and o.currency = :currency"
                        + " group by l.voided_by");
    }

    public Map<UUID, Long> staffRemovedCount(Scope scope) {
        Map<UUID, Long> result = new HashMap<>();
        jdbc.query("select l.voided_by as staff, count(*) value from " + schema + "order_line_items l join " + schema + "orders o on o.id = l.order_id"
                        + " where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and l.status = 'VOIDED' and l.voided_by is not null and l.voided_at >= :from and l.voided_at < :to group by l.voided_by",
                scope.params(), rs -> {
                    result.put(rs.getObject("staff", UUID.class), rs.getLong("value"));
                });
        return result;
    }

    public Map<UUID, BigDecimal> staffRefunds(Scope scope) {
        return moneyByStaff(scope,
                "select t.created_by as staff, coalesce(sum(t.amount), 0) value from " + schema + "payment_transactions t join " + schema + "payments p on p.id = t.payment_id"
                        + " where p.restaurant_id = :restaurant" + scope.branch("p") + " and t.transaction_type = 'REFUND' and t.status = 'APPROVED'"
                        + " and t.processed_at >= :from and t.processed_at < :to and p.currency = :currency and t.created_by is not null group by t.created_by");
    }

    public Map<UUID, Long> staffVoidedOrders(Scope scope) {
        Map<UUID, Long> result = new HashMap<>();
        jdbc.query("select o.updated_by as staff, count(*) value from " + schema + "orders o where o.restaurant_id = :restaurant" + scope.branch("o")
                        + " and o.status = 'VOIDED' and o.updated_by is not null and o.updated_at >= :from and o.updated_at < :to group by o.updated_by",
                scope.params(), rs -> {
                    result.put(rs.getObject("staff", UUID.class), rs.getLong("value"));
                });
        return result;
    }

    /** Hours on the clock (unpaid breaks taken off) for shifts that started in the window. */
    public Map<UUID, BigDecimal> staffHours(Scope scope) {
        return moneyByStaff(scope,
                "select s.user_id as staff, coalesce(sum(extract(epoch from (coalesce(s.ended_at, now()) - s.started_at))"
                        + " - coalesce((select sum(extract(epoch from (coalesce(b.ended_at, coalesce(s.ended_at, now())) - b.started_at)))"
                        + " from " + schema + "shift_breaks b where b.shift_id = s.id and b.is_paid = false), 0)) / 3600.0, 0) value"
                        + " from " + schema + "shifts s where s.restaurant_id = :restaurant" + scope.branch("s")
                        + " and s.started_at is not null and s.started_at >= :from and s.started_at < :to group by s.user_id");
    }

    private Map<UUID, BigDecimal> moneyByStaff(Scope scope, String sql) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        jdbc.query(sql, scope.params(), rs -> {
            result.put(rs.getObject("staff", UUID.class), rs.getBigDecimal("value"));
        });
        return result;
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static BigDecimal money(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }
}
