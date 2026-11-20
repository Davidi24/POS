package pos.pos.sales;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record MySalesResponse(UUID restaurantId, UUID branchId, UUID staffId, String staffName,
        String timezone, LocalDate date, OffsetDateTime from, OffsetDateTime to, UUID shiftId,
        OffsetDateTime generatedAt, List<ShiftOption> shifts, List<CurrencyTotals> currencies, String attribution) {
    public record ShiftOption(UUID id, String status, OffsetDateTime start, OffsetDateTime end, long workedMinutes) {}
    public record CurrencyTotals(String currency, long ordersServed, long tablesServed, long guestsServed, long openOrders,
        BigDecimal sales, BigDecimal subtotal, BigDecimal discounts, BigDecimal tax, BigDecimal serviceCharge,
        BigDecimal averageTicket, long paymentCount, BigDecimal recordedTips, BigDecimal refunds, BigDecimal collected,
        long ordersWithoutPayments, List<Hour> hourly, List<Method> paymentMethods, List<Item> topItems,
        List<Area> areas, List<RecentPayment> recentPayments) {}
    public record Hour(String hour, BigDecimal sales) {}
    public record Method(String name, BigDecimal collected, long count) {}
    public record Item(String name, long quantity, BigDecimal sales) {}
    public record Area(String name, long tables, BigDecimal sales) {}
    public record RecentPayment(UUID id, UUID orderId, String orderNumber, String tableNumber, int guests,
        OffsetDateTime paidAt, String method, String status, BigDecimal collected) {}
}
