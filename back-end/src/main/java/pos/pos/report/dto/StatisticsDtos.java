package pos.pos.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Statistics for one restaurant (optionally one branch) over restaurant-local calendar days. Money is in the
 * restaurant's currency; orders in other currencies are left out and counted in {@code otherCurrencyOrders}.
 * "Sales" are the totals of closed orders on the day they closed.
 */
public final class StatisticsDtos {

    private StatisticsDtos() {
    }

    public record Period(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo,
                         String timezone, String currency, UUID branchId) {
    }

    /** One figure with the same figure for the period just before, for "up 12%" comparisons. */
    public record Kpi(BigDecimal value, BigDecimal previous) {
    }

    public record Kpis(Kpi sales, Kpi orders, Kpi guests, Kpi averageTicket, Kpi averagePerGuest, Kpi tips,
                       Kpi collected, Kpi refunds, Kpi discounts, Kpi removedItemsValue, Kpi cancelledOrders,
                       Kpi voidedOrders, long openOrders, long otherCurrencyOrders) {
    }

    public record DaySales(LocalDate date, BigDecimal sales, long orders, long guests) {
    }

    public record HourSales(int hour, BigDecimal sales, long orders) {
    }

    /** {@code averageSales} is the average per such weekday in the period (days without sales count as zero). */
    public record WeekdaySales(int isoDayOfWeek, String day, BigDecimal sales, long orders, BigDecimal averageSales) {
    }

    public record Share(String key, BigDecimal sales, long orders) {
    }

    public record MethodTotal(String method, BigDecimal collected, long count, BigDecimal tips) {
    }

    public record ItemSales(UUID menuItemId, String name, String section, long quantity, BigDecimal sales) {
    }

    public record SectionSales(String section, long quantity, BigDecimal sales) {
    }

    public record StaffStats(UUID staffId, String name, long orders, BigDecimal sales, BigDecimal averageTicket,
                             long guests, BigDecimal tips, BigDecimal discounts, long removedItems,
                             BigDecimal removedValue, BigDecimal refunds, long voidedOrders,
                             BigDecimal hoursWorked) {
    }

    public record Overview(Period period, Kpis kpis, List<DaySales> salesByDay, List<MethodTotal> paymentMethods,
                           List<Share> orderTypes, List<ItemSales> topItems) {
    }

    public record Sales(Period period, List<DaySales> salesByDay, List<HourSales> salesByHour,
                        List<WeekdaySales> salesByWeekday, List<Share> orderTypes, List<Share> sources,
                        List<Share> floors, List<SectionSales> sections, List<ItemSales> topItems,
                        List<ItemSales> slowItems) {
    }

    public record Staff(Period period, List<StaffStats> staff) {
    }

    public record ReportInfo(String code, String title, String description) {
    }
}
