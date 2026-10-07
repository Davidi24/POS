package pos.pos.report.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.payment.mapper.PaymentMapper;
import pos.pos.report.dto.StatisticsDtos.DaySales;
import pos.pos.report.dto.StatisticsDtos.HourSales;
import pos.pos.report.dto.StatisticsDtos.ItemSales;
import pos.pos.report.dto.StatisticsDtos.Kpi;
import pos.pos.report.dto.StatisticsDtos.Kpis;
import pos.pos.report.dto.StatisticsDtos.Overview;
import pos.pos.report.dto.StatisticsDtos.Period;
import pos.pos.report.dto.StatisticsDtos.ReportInfo;
import pos.pos.report.dto.StatisticsDtos.Sales;
import pos.pos.report.dto.StatisticsDtos.Staff;
import pos.pos.report.dto.StatisticsDtos.StaffStats;
import pos.pos.report.dto.StatisticsDtos.WeekdaySales;
import pos.pos.report.service.StatisticsRepository.Scope;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    public static final int MAX_DAYS = 366;
    private static final int TOP_ITEMS = 10;
    private static final int SLOW_ITEMS = 5;

    public static final List<ReportInfo> REPORTS = List.of(
            new ReportInfo("daily-sales", "Sales per day", "Sales, orders and guests for every day of the period"),
            new ReportInfo("items", "Dishes sold", "Every dish sold, how many and for how much"),
            new ReportInfo("staff", "Staff performance", "Orders, sales, tips, discounts, removals and refunds per person"),
            new ReportInfo("payments", "Payments by method", "Money taken per payment method, with tips")
    );

    private final RestaurantScopeService restaurantScopeService;
    private final StatisticsRepository repository;
    private final PaymentMapper paymentMapper;

    /** The resolved window: restaurant-local days [from, to] and the same number of days just before. */
    public record Window(Restaurant restaurant, UUID branchId, LocalDate from, LocalDate to, Scope current, Scope previous, Period period) {
    }

    @Transactional(readOnly = true)
    public List<ReportInfo> reports(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return REPORTS;
    }

    @Transactional(readOnly = true)
    public Overview overview(Authentication authentication, UUID restaurantId, UUID branchId, LocalDate from, LocalDate to) {
        Window window = window(authentication, restaurantId, branchId, from, to);
        return new Overview(
                window.period(),
                kpis(window),
                fillDays(repository.salesByDay(window.current()), window.from(), window.to()),
                repository.paymentMethods(window.current()),
                repository.orderTypes(window.current()),
                repository.topItems(window.current(), 5)
        );
    }

    @Transactional(readOnly = true)
    public Sales sales(Authentication authentication, UUID restaurantId, UUID branchId, LocalDate from, LocalDate to) {
        Window window = window(authentication, restaurantId, branchId, from, to);
        List<DaySales> days = fillDays(repository.salesByDay(window.current()), window.from(), window.to());
        return new Sales(
                window.period(),
                days,
                fillHours(repository.salesByHour(window.current())),
                weekdays(days),
                repository.orderTypes(window.current()),
                repository.sources(window.current()),
                repository.floors(window.current()),
                repository.sections(window.current()),
                repository.topItems(window.current(), TOP_ITEMS),
                repository.slowItems(window.current(), SLOW_ITEMS)
        );
    }

    @Transactional(readOnly = true)
    public Staff staff(Authentication authentication, UUID restaurantId, UUID branchId, LocalDate from, LocalDate to) {
        Window window = window(authentication, restaurantId, branchId, from, to);
        return new Staff(window.period(), staffRows(window));
    }

    public Window window(Authentication authentication, UUID restaurantId, UUID branchId, LocalDate from, LocalDate to) {
        Restaurant restaurant = restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        if (branchId != null) {
            restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        }
        if (from == null || to == null) {
            throw bad("from and to are required");
        }
        if (to.isBefore(from)) {
            throw bad("to must not be before from");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw bad("The period can be at most " + MAX_DAYS + " days");
        }
        ZoneId zone = zone(restaurant);
        String currency = restaurant.getCurrency() == null ? "EUR" : restaurant.getCurrency().trim().toUpperCase(Locale.ROOT);
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);
        Scope current = new Scope(restaurant.getId(), branchId, currency,
                from.atStartOfDay(zone).toOffsetDateTime(), to.plusDays(1).atStartOfDay(zone).toOffsetDateTime(), zone.getId());
        Scope previous = new Scope(restaurant.getId(), branchId, currency,
                previousFrom.atStartOfDay(zone).toOffsetDateTime(), from.atStartOfDay(zone).toOffsetDateTime(), zone.getId());
        Period period = new Period(from, to, previousFrom, previousTo, zone.getId(), currency, branchId);
        return new Window(restaurant, branchId, from, to, current, previous, period);
    }

    public Kpis kpis(Window window) {
        var sales = repository.sales(window.current());
        var previousSales = repository.sales(window.previous());
        var payments = repository.payments(window.current());
        var previousPayments = repository.payments(window.previous());
        return new Kpis(
                new Kpi(sales.sales(), previousSales.sales()),
                new Kpi(BigDecimal.valueOf(sales.orders()), BigDecimal.valueOf(previousSales.orders())),
                new Kpi(BigDecimal.valueOf(sales.guests()), BigDecimal.valueOf(previousSales.guests())),
                new Kpi(divide(sales.sales(), sales.orders()), divide(previousSales.sales(), previousSales.orders())),
                new Kpi(divide(sales.sales(), sales.guests()), divide(previousSales.sales(), previousSales.guests())),
                new Kpi(payments.tips(), previousPayments.tips()),
                new Kpi(payments.taken().subtract(payments.refunds()), previousPayments.taken().subtract(previousPayments.refunds())),
                new Kpi(payments.refunds(), previousPayments.refunds()),
                new Kpi(sales.discounts(), previousSales.discounts()),
                new Kpi(repository.removedItemsValue(window.current()), repository.removedItemsValue(window.previous())),
                new Kpi(BigDecimal.valueOf(repository.ordersEndedAs(window.current(), "CANCELLED")),
                        BigDecimal.valueOf(repository.ordersEndedAs(window.previous(), "CANCELLED"))),
                new Kpi(BigDecimal.valueOf(repository.ordersEndedAs(window.current(), "VOIDED")),
                        BigDecimal.valueOf(repository.ordersEndedAs(window.previous(), "VOIDED"))),
                repository.openOrders(window.current()),
                repository.otherCurrencyOrders(window.current())
        );
    }

    public List<StaffStats> staffRows(Window window) {
        Scope scope = window.current();
        var sales = repository.staffSales(scope);
        Map<UUID, BigDecimal> tips = repository.staffTips(scope);
        Map<UUID, BigDecimal> discounts = repository.staffDiscounts(scope);
        Map<UUID, BigDecimal> removedValue = repository.staffRemovedValue(scope);
        Map<UUID, Long> removedCount = repository.staffRemovedCount(scope);
        Map<UUID, BigDecimal> refunds = repository.staffRefunds(scope);
        Map<UUID, Long> voidedOrders = repository.staffVoidedOrders(scope);
        Map<UUID, BigDecimal> hours = repository.staffHours(scope);

        Set<UUID> people = new HashSet<>();
        sales.forEach(row -> people.add(row.staffId()));
        people.addAll(tips.keySet());
        people.addAll(discounts.keySet());
        people.addAll(removedValue.keySet());
        people.addAll(refunds.keySet());
        people.addAll(voidedOrders.keySet());
        people.addAll(hours.keySet());
        people.remove(null);
        Map<UUID, String> names = paymentMapper.names(people);
        Map<UUID, StatisticsRepository.StaffRow> salesByStaff = new HashMap<>();
        sales.forEach(row -> salesByStaff.put(row.staffId(), row));

        List<StaffStats> rows = new ArrayList<>();
        for (UUID person : people) {
            var row = salesByStaff.get(person);
            long orders = row == null ? 0 : row.orders();
            BigDecimal total = row == null ? BigDecimal.ZERO : row.sales();
            rows.add(new StaffStats(
                    person,
                    names.getOrDefault(person, "Former staff"),
                    orders,
                    scale(total),
                    divide(total, orders),
                    row == null ? 0 : row.guests(),
                    scale(tips.getOrDefault(person, BigDecimal.ZERO)),
                    scale(discounts.getOrDefault(person, BigDecimal.ZERO)),
                    removedCount.getOrDefault(person, 0L),
                    scale(removedValue.getOrDefault(person, BigDecimal.ZERO)),
                    scale(refunds.getOrDefault(person, BigDecimal.ZERO)),
                    voidedOrders.getOrDefault(person, 0L),
                    hours.getOrDefault(person, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
            ));
        }
        rows.sort(Comparator.comparing(StaffStats::sales).reversed().thenComparing(StaffStats::name));
        return rows;
    }

    // ---- CSV reports ----

    @Transactional(readOnly = true)
    public String csvReport(Authentication authentication, UUID restaurantId, UUID branchId, LocalDate from, LocalDate to, String code) {
        Window window = window(authentication, restaurantId, branchId, from, to);
        StringBuilder csv = new StringBuilder();
        switch (code) {
            case "daily-sales" -> {
                csv.append("date,sales,orders,guests,currency\n");
                for (DaySales day : fillDays(repository.salesByDay(window.current()), window.from(), window.to())) {
                    row(csv, day.date().toString(), day.sales().toPlainString(), String.valueOf(day.orders()),
                            String.valueOf(day.guests()), window.period().currency());
                }
            }
            case "items" -> {
                csv.append("dish,section,quantity,sales,currency\n");
                for (ItemSales item : repository.topItems(window.current(), 10_000)) {
                    row(csv, item.name(), item.section(), String.valueOf(item.quantity()), item.sales().toPlainString(),
                            window.period().currency());
                }
            }
            case "staff" -> {
                csv.append("name,orders,sales,average_ticket,guests,tips,discounts,removed_items,removed_value,refunds,voided_orders,hours,currency\n");
                for (StaffStats person : staffRows(window)) {
                    row(csv, person.name(), String.valueOf(person.orders()), person.sales().toPlainString(),
                            person.averageTicket().toPlainString(), String.valueOf(person.guests()), person.tips().toPlainString(),
                            person.discounts().toPlainString(), String.valueOf(person.removedItems()), person.removedValue().toPlainString(),
                            person.refunds().toPlainString(), String.valueOf(person.voidedOrders()), person.hoursWorked().toPlainString(),
                            window.period().currency());
                }
            }
            case "payments" -> {
                csv.append("method,collected,payments,tips,currency\n");
                for (var method : repository.paymentMethods(window.current())) {
                    row(csv, method.method(), method.collected().toPlainString(), String.valueOf(method.count()),
                            method.tips().toPlainString(), window.period().currency());
                }
            }
            default -> throw new AuthException("Unknown report: " + code, HttpStatus.NOT_FOUND);
        }
        return csv.toString();
    }

    public static void row(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvCell(values[i]));
        }
        csv.append('\n');
    }

    /** Quotes when needed and defuses spreadsheet formulas (a dish named "=SUM(...)" stays text). */
    public static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String text = value;
        if (startsWithSpreadsheetFormula(text)) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static boolean startsWithSpreadsheetFormula(String value) {
        int offset = 0;
        while (offset < value.length()) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)
                    && !Character.isISOControl(codePoint) && codePoint != 0xFEFF) {
                break;
            }
            offset += Character.charCount(codePoint);
        }
        if (offset >= value.length()) {
            return false;
        }

        int first = value.codePointAt(offset);
        if (first == '-' && value.strip().matches("-?\\d+(\\.\\d+)?")) {
            return false;
        }
        return first == '=' || first == '+' || first == '-' || first == '@';
    }

    // ---- Helpers ----

    public static List<DaySales> fillDays(List<DaySales> rows, LocalDate from, LocalDate to) {
        Map<LocalDate, DaySales> byDay = new LinkedHashMap<>();
        rows.forEach(row -> byDay.put(row.date(), row));
        List<DaySales> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            DaySales row = byDay.get(day);
            days.add(row == null ? new DaySales(day, BigDecimal.ZERO.setScale(2), 0, 0) : new DaySales(day, scale(row.sales()), row.orders(), row.guests()));
        }
        return days;
    }

    public static List<HourSales> fillHours(List<HourSales> rows) {
        Map<Integer, HourSales> byHour = new HashMap<>();
        rows.forEach(row -> byHour.put(row.hour(), row));
        List<HourSales> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            HourSales row = byHour.get(hour);
            hours.add(row == null ? new HourSales(hour, BigDecimal.ZERO.setScale(2), 0) : new HourSales(hour, scale(row.sales()), row.orders()));
        }
        return hours;
    }

    public static List<WeekdaySales> weekdays(List<DaySales> days) {
        Map<DayOfWeek, BigDecimal> sales = new LinkedHashMap<>();
        Map<DayOfWeek, Long> orders = new HashMap<>();
        Map<DayOfWeek, Integer> count = new HashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            sales.put(day, BigDecimal.ZERO);
            orders.put(day, 0L);
            count.put(day, 0);
        }
        for (DaySales day : days) {
            DayOfWeek weekday = day.date().getDayOfWeek();
            sales.merge(weekday, day.sales(), BigDecimal::add);
            orders.merge(weekday, day.orders(), Long::sum);
            count.merge(weekday, 1, Integer::sum);
        }
        List<WeekdaySales> result = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            int occurrences = count.get(day);
            result.add(new WeekdaySales(day.getValue(), day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    scale(sales.get(day)), orders.get(day),
                    occurrences == 0 ? BigDecimal.ZERO.setScale(2) : sales.get(day).divide(BigDecimal.valueOf(occurrences), 2, RoundingMode.HALF_UP)));
        }
        return result;
    }

    private static BigDecimal divide(BigDecimal amount, long count) {
        if (count <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static ZoneId zone(Restaurant restaurant) {
        try {
            return restaurant.getTimezone() == null ? ZoneId.of("UTC") : ZoneId.of(restaurant.getTimezone());
        } catch (Exception ignored) {
            return ZoneId.of("UTC");
        }
    }

    private static AuthException bad(String message) {
        return new AuthException(message, HttpStatus.BAD_REQUEST);
    }
}
