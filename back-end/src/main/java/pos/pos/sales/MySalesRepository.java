package pos.pos.sales;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.*;
import java.time.*;
import java.util.*;
import static pos.pos.sales.MySalesResponse.*;

/** Database aggregates: never load a day's order/payment entities or join payments into order totals. */
@Repository
public class MySalesRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final String schema;
    public MySalesRepository(NamedParameterJdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid reporting schema");
        this.jdbc = jdbc; this.schema = "\"" + schema + "\".";
    }
    public List<CurrencyTotals> totals(UUID restaurantId, UUID branchId, UUID staffId,
            OffsetDateTime from, OffsetDateTime to, UUID shiftId, String zone, String defaultCurrency) {
        var p = new HashMap<String,Object>();
        p.put("restaurant", restaurantId); p.put("branch", branchId); p.put("staff", staffId);
        p.put("from", from); p.put("to", to); p.put("zone", zone);
        if (shiftId != null) p.put("shift", shiftId);
        String scope = "o.restaurant_id=:restaurant and o.branch_id=:branch and o.created_by=:staff";
        String closed = scope + " and o.status='CLOSED' and o.closed_at>=:from and o.closed_at<:to";
        String opened = scope + " and o.status in ('OPEN','DRAFT') and o.opened_at>=:from and o.opened_at<:to";
        String payments = "p.restaurant_id=:restaurant and p.branch_id=:branch and " + scope
                + " and p.status in ('CAPTURED','PARTIALLY_REFUNDED','REFUNDED') and p.paid_at>=:from and p.paid_at<:to"
                + (shiftId == null ? "" : " and p.shift_id=:shift");
        String net = "(p.amount+p.tip_amount+p.surcharge_amount-p.refunded_amount)";
        String orderTable = schema + "orders o";
        String paymentTable = schema + "payments p join " + orderTable + " on o.id=p.order_id";
        List<String> currencies = jdbc.queryForList("select distinct currency from (select o.currency from " + orderTable + " where " + closed
                + " union select o.currency from " + orderTable + " where " + opened
                + " union select p.currency from " + paymentTable + " where " + payments + ") c order by currency", p, String.class);
        if (currencies.isEmpty()) currencies = List.of(defaultCurrency);
        var result = new ArrayList<CurrencyTotals>();
        for (String currency : currencies) {
            p.put("currency", currency);
            String c = closed + " and o.currency=:currency";
            String pay = payments + " and p.currency=:currency";
            Map<String,Object> a = jdbc.queryForMap("select count(*) orders, count(distinct o.table_id) tables, coalesce(sum(o.guest_count),0) guests,"
                + " coalesce(sum(o.total),0) sales, coalesce(sum(o.subtotal),0) subtotal, coalesce(sum(o.discount_total),0) discounts,"
                + " coalesce(sum(o.tax_total),0) tax, coalesce(sum(o.service_charge_total),0) service,"
                + " count(*) filter (where not exists (select 1 from " + schema + "payments paid where paid.order_id=o.id and paid.restaurant_id=:restaurant"
                + " and paid.branch_id=:branch and paid.status in ('CAPTURED','PARTIALLY_REFUNDED','REFUNDED'))) missing from " + orderTable + " where " + c, p);
            Map<String,Object> b = jdbc.queryForMap("select count(*) count, coalesce(sum(p.tip_amount),0) tips, coalesce(sum(p.refunded_amount),0) refunds,"
                + " coalesce(sum(" + net + "),0) collected from " + paymentTable + " where " + pay, p);
            long open = jdbc.queryForObject("select count(*) from " + orderTable + " where " + opened + " and o.currency=:currency", p, Long.class);
            var hourly = jdbc.query("select to_char(date_trunc('hour', o.closed_at at time zone :zone), 'YYYY-MM-DD HH24:00') as hour_label, sum(o.total) sales from " + orderTable
                + " where " + c + " group by 1 order by 1", p, (rs,n) -> new Hour(rs.getString("hour_label"), rs.getBigDecimal("sales")));
            var methods = jdbc.query("select p.method, sum(" + net + ") collected, count(*) count from " + paymentTable + " where " + pay
                + " group by p.method order by collected desc, p.method", p, (rs,n) -> new Method(rs.getString("method"), rs.getBigDecimal("collected"), rs.getLong("count")));
            var items = jdbc.query("select l.item_name_snapshot || coalesce(' · ' || l.variant_name_snapshot,'') name, sum(l.quantity) quantity, sum(l.line_total) sales from "
                + orderTable + " join " + schema + "order_line_items l on l.order_id=o.id where " + c
                + " and l.status not in ('CANCELLED','VOIDED') group by l.menu_item_id, l.item_name_snapshot, l.variant_id, l.variant_name_snapshot order by quantity desc, name, l.menu_item_id, l.variant_id limit 5",
                p, (rs,n) -> new Item(rs.getString("name"), rs.getLong("quantity"), rs.getBigDecimal("sales")));
            var areas = jdbc.query("select coalesce(t.floor,'Unassigned floor') name, count(distinct t.id) tables, sum(o.total) sales from " + orderTable
                + " join " + schema + "tables t on t.id=o.table_id where " + c + " group by 1 order by sales desc, name limit 5",
                p, (rs,n) -> new Area(rs.getString("name"), rs.getLong("tables"), rs.getBigDecimal("sales")));
            var recent = jdbc.query("select p.id, o.id order_id, o.order_number, t.table_number, o.guest_count, p.paid_at, p.method, p.status, " + net
                + " collected from " + paymentTable + " left join " + schema + "tables t on t.id=o.table_id where " + pay + " order by p.paid_at desc, p.id desc limit 5",
                p, (rs,n) -> new RecentPayment(rs.getObject("id",UUID.class), rs.getObject("order_id",UUID.class), rs.getString("order_number"), rs.getString("table_number"),
                    rs.getInt("guest_count"), rs.getObject("paid_at",OffsetDateTime.class), rs.getString("method"), rs.getString("status"), rs.getBigDecimal("collected")));
            long count = number(a,"orders");
            result.add(new CurrencyTotals(currency, count, number(a,"tables"), number(a,"guests"), open,
                money(a,"sales"), money(a,"subtotal"), money(a,"discounts"), money(a,"tax"), money(a,"service"),
                count == 0 ? BigDecimal.ZERO : money(a,"sales").divide(BigDecimal.valueOf(count),2,RoundingMode.HALF_UP),
                number(b,"count"), money(b,"tips"), money(b,"refunds"), money(b,"collected"), number(a,"missing"), hourly, methods, items, areas, recent));
        }
        return List.copyOf(result);
    }
    private static long number(Map<String,Object> row, String key) { return ((Number)row.get(key)).longValue(); }
    private static BigDecimal money(Map<String,Object> row, String key) { return (BigDecimal)row.get(key); }
}
