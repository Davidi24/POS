package com.saporini.mobile_desktop.statistics

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.shifts.toCents
import com.saporini.mobile_desktop.statistics.data.ItemSalesDto
import com.saporini.mobile_desktop.statistics.data.KpisDto
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlin.math.roundToLong

// What the Statistics screens show, worked out once from what was loaded. Money is in cents.
//
// MOCK for the production pass: the server doesn't yet return everything these screens show. Until it does:
// - the narrower filters (order type, source, menu section, staff member, payment method) scale the loaded figures by
//   that slice's share instead of the server filtering (StatsFilters);
// - busy times (weekday × hour) are spread from the weekday and hour totals;
// - payments per day, refunds per method, refunded orders, dish costs, dish sales per order type and wages are
//   estimated from the real figures;
// - Bookings and Inventory & costs are made up around the real sales (StatsBookings, StatsStock).
// The production pass should return these from /statistics and delete the estimates here.

internal fun OrderDecimal?.centsOf(): Long = this?.toCents() ?: 0L

internal data class DayPoint(val date: LocalDate, val sales: Long, val orders: Long, val guests: Long)

internal data class HourPoint(val hour: Int, val sales: Long, val orders: Long)

internal data class WeekdayPoint(val day: DayOfWeek, val averageSales: Long, val sales: Long, val orders: Long)

/** One part of a whole: an order type, a source, a room, a menu section. */
internal data class Slice(val key: String, val sales: Long, val count: Long)

internal data class DishFigures(
    val id: String?, val name: String, val section: String?, val quantity: Long, val sales: Long,
    // MOCK: estimated until recipe costs are reported.
    val cost: Long,
    // MOCK: how many went out per order type (DINE_IN, TAKEAWAY, DELIVERY).
    val byType: Map<String, Long>
) {
    val profit: Long get() = sales - cost
    val margin: Float get() = if (sales > 0) profit.toFloat() / sales else 0f
    val price: Long get() = if (quantity > 0) sales / quantity else 0
}

internal data class MethodFigures(val key: String, val collected: Long, val count: Long, val tips: Long, val refunds: Long)

internal data class BranchFigures(
    val id: String, val name: String, val sales: Long, val orders: Long, val guests: Long, val collected: Long, val tips: Long,
    val methods: List<MethodFigures>, val dishes: List<ItemSalesDto>,
    // The sales of each day of the period, in the order of [StatsView.days].
    val days: List<Long>
) {
    val averageBill: Long get() = if (orders > 0) sales / orders else 0
}

internal data class PersonFigures(
    val id: String, val name: String, val orders: Long, val sales: Long, val guests: Long, val tips: Long, val discounts: Long,
    val removedItems: Long, val removedValue: Long, val refunds: Long, val voidedOrders: Long, val hours: Double,
    // MOCK: the hourly wage, until Statistics reads the wages from Salary.
    val wage: Long
) {
    val averageBill: Long get() = if (orders > 0) sales / orders else 0
    val laborCost: Long get() = (hours * wage).roundToLong()
    val salesPerHour: Long get() = if (hours > 0.1) (sales / hours).roundToLong() else 0
}

internal data class Totals(
    val sales: Long, val orders: Long, val guests: Long, val tips: Long, val collected: Long, val refunds: Long, val discounts: Long,
    val removedValue: Long, val cancelled: Long, val voided: Long, val open: Long, val removedItems: Long, val refundedOrders: Long,
    val unpaid: Long = 0, val unpaidValue: Long = 0
) {
    val averageBill: Long get() = if (orders > 0) sales / orders else 0
    val perGuest: Long get() = if (guests > 0) sales / guests else 0
}

/** MOCK: bookings over the period, made up around the real guests until the reservation statistics exist. */
internal data class StatsBookings(
    val days: List<Pair<LocalDate, Long>>, val guestsByDay: List<Pair<LocalDate, Long>>, val byHour: List<Pair<Int, Long>>,
    val sources: List<Slice>, val booked: Long, val guests: Long, val arrived: Long, val cancelled: Long, val noShows: Long, val upcoming: Long,
    // Seats taken at lunch and dinner per weekday, 0..1.
    val occupancy: List<Pair<DayOfWeek, Pair<Float, Float>>>, val depositsTaken: Long, val depositsRefunded: Long, val depositsKept: Long,
    val bigGroups: Long, val compareBooked: Long?, val compareGuests: Long?
) {
    val arrivalRate: Float get() = (booked - cancelled - upcoming).takeIf { it > 0 }?.let { arrived.toFloat() / it } ?: 0f
    val averageGroup: Float get() = if (booked > 0) guests.toFloat() / booked else 0f
}

/** MOCK: stock and costs over the period, made up around the real sales until the inventory statistics exist. */
internal data class StatsStock(
    val stockValue: Long, val received: Long, val used: Long, val waste: Long, val returns: Long, val adjustments: Long,
    val byDay: List<StockDay>, val wasteItems: List<Slice>, val suppliers: List<Slice>, val compareUsed: Long?
) {
    data class StockDay(val date: LocalDate, val received: Long, val used: Long, val waste: Long)
}

internal class StatsView(val state: StatisticsState) {
    private val overview = state.overview
    private val sales = state.sales
    val currency: String = overview?.period?.currency ?: sales?.period?.currency ?: state.currency ?: "EUR"
    val from: LocalDate = state.from ?: LocalDate(2026, 1, 1)
    val to: LocalDate = state.to ?: from
    val dayCount: Int = (from.daysUntil(to) + 1).coerceAtLeast(1)
    val filters: StatsFilters = state.filters
    val compareMode: CompareMode = state.compare
    val hasData: Boolean = overview != null

    // ---- The narrower filters (MOCK: shares of what's loaded) ----

    private val typeSlices = sales?.orderTypes.orEmpty().map { Slice(it.key, it.sales.centsOf(), it.orders) }
        .ifEmpty { overview?.orderTypes.orEmpty().map { Slice(it.key, it.sales.centsOf(), it.orders) } }
    private val sourceSlices = sales?.sources.orEmpty().map { Slice(it.key, it.sales.centsOf(), it.orders) }
    private val sectionSlices = sales?.sections.orEmpty().map { Slice(it.section, it.sales.centsOf(), it.quantity) }
    private val rawMethods = overview?.paymentMethods.orEmpty()
    private val rawStaff = state.staff?.staff.orEmpty()

    private fun share(slices: List<Slice>, key: String?): Double {
        if (key == null) return 1.0
        val total = slices.sumOf { it.sales }.takeIf { it > 0 } ?: return 0.0
        return (slices.firstOrNull { it.key == key }?.sales ?: 0L).toDouble() / total
    }

    private val methodShare: Double = filters.method?.let { key ->
        val total = rawMethods.sumOf { it.collected.centsOf() }.takeIf { it > 0 } ?: return@let 0.0
        (rawMethods.firstOrNull { it.method == key }?.collected.centsOf()).toDouble() / total
    } ?: 1.0
    private val staffShare: Double = filters.staffId?.let { id ->
        val total = rawStaff.sumOf { it.sales.centsOf() }.takeIf { it > 0 } ?: return@let 0.0
        (rawStaff.firstOrNull { it.staffId == id }?.sales.centsOf()).toDouble() / total
    } ?: 1.0

    /** How much of everything the narrower filters keep (1 without filters). */
    val factor: Double = share(typeSlices, filters.orderType) * share(sourceSlices, filters.source) * share(sectionSlices, filters.section) *
        methodShare * staffShare

    private fun Long.scaled(by: Double = factor): Long = (this * by).roundToLong()

    // ---- Totals ----

    private fun totalsOf(k: KpisDto?, previous: Boolean, removedItems: Long): Totals? {
        k ?: return null
        fun pick(value: com.saporini.mobile_desktop.statistics.data.KpiDto) = (if (previous) value.previous else value.value).centsOf()
        // Counts come as whole numbers in the same decimal form; cents ÷ 100 gives the count back.
        fun count(value: com.saporini.mobile_desktop.statistics.data.KpiDto) = pick(value) / 100
        val refunds = pick(k.refunds)
        val average = count(k.orders).takeIf { it > 0 }?.let { pick(k.sales) / it } ?: 0
        return Totals(
            sales = pick(k.sales).scaled(), orders = count(k.orders).scaled(), guests = count(k.guests).scaled(), tips = pick(k.tips).scaled(),
            collected = pick(k.collected).scaled(), refunds = refunds.scaled(), discounts = pick(k.discounts).scaled(),
            removedValue = pick(k.removedItemsValue).scaled(), cancelled = count(k.cancelledOrders).scaled(), voided = count(k.voidedOrders).scaled(),
            open = if (previous) 0 else k.openOrders, removedItems = removedItems.scaled(),
            // MOCK: refunded orders, from the refunds and the average bill.
            refundedOrders = if (refunds <= 0) 0 else (refunds / average.coerceAtLeast(1)).coerceAtLeast(1).scaled(),
            unpaid = count(k.unpaidOrders).scaled(), unpaidValue = pick(k.unpaidValue).scaled()
        )
    }

    val totals: Totals = totalsOf(overview?.kpis, previous = false, removedItems = rawStaff.sumOf { it.removedItems })
        ?: Totals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    /** The comparison's totals: the loaded comparison, or (the days before) the server's own previous figures meanwhile. */
    val compareTotals: Totals? = when {
        compareMode == CompareMode.NONE -> null
        state.compareOverview != null -> totalsOf(state.compareOverview.kpis, previous = false, removedItems = 0)
        compareMode == CompareMode.PREVIOUS -> totalsOf(overview?.kpis, previous = true, removedItems = 0)
        else -> null
    }

    fun change(now: Long, before: Long?): String? = before?.let { changeText(now, it) }

    // ---- Over time ----

    private fun daysOf(from: LocalDate, count: Int, list: List<com.saporini.mobile_desktop.statistics.data.DaySalesDto>): List<DayPoint> {
        val byDate = list.associateBy { it.date }
        return (0 until count).map { offset ->
            val date = from.plus(DatePeriod(days = offset))
            val day = byDate[date.toString()]
            DayPoint(date, day?.sales.centsOf().scaled(), (day?.orders ?: 0L).scaled(), (day?.guests ?: 0L).scaled())
        }
    }

    val days: List<DayPoint> = daysOf(from, dayCount, sales?.salesByDay?.ifEmpty { null } ?: overview?.salesByDay.orEmpty())

    /** The comparison's days, matched to [days] by position (day 1 with day 1). */
    val compareDays: List<DayPoint>? = run {
        if (compareMode == CompareMode.NONE) return@run null
        val range = compareRange(compareMode, from, to) ?: return@run null
        val list = state.compareSales?.salesByDay?.ifEmpty { null } ?: state.compareOverview?.salesByDay ?: return@run null
        daysOf(range.first, dayCount, list)
    }

    val hours: List<HourPoint> = run {
        val byHour = sales?.salesByHour.orEmpty().associateBy { it.hour }
        (0..23).map { h -> byHour[h].let { HourPoint(h, it?.sales.centsOf().scaled(), (it?.orders ?: 0L).scaled()) } }
    }

    val compareHours: List<HourPoint>? = state.compareSales?.let { c ->
        val byHour = c.salesByHour.associateBy { it.hour }
        (0..23).map { h -> byHour[h].let { HourPoint(h, it?.sales.centsOf().scaled(), (it?.orders ?: 0L).scaled()) } }
    }

    val weekdays: List<WeekdayPoint> = run {
        val byDay = sales?.salesByWeekday.orEmpty().associateBy { it.isoDayOfWeek }
        DayOfWeek.entries.map { d ->
            val w = byDay[d.isoDayNumber]
            WeekdayPoint(d, w?.averageSales.centsOf().scaled(), w?.sales.centsOf().scaled(), (w?.orders ?: 0L).scaled())
        }
    }

    /** MOCK: orders per weekday and hour, spread from the weekday and hour totals (rows Monday…Sunday, 24 hours). */
    val heat: List<List<Long>> = run {
        val hourOrders = hours.map { it.orders.toDouble() }
        val hourTotal = hourOrders.sum()
        // Each weekday's orders; without them, the period's orders shared by each weekday's average sales.
        val byOrders = weekdays.map { it.orders.toDouble() }
        val dayOrders = if (byOrders.sum() > 0) byOrders else {
            val sales = weekdays.map { it.averageSales.toDouble() }
            val total = sales.sum().takeIf { it > 0 } ?: 1.0
            sales.map { it / total * totals.orders }
        }
        weekdays.indices.map { w ->
            (0..23).map { h ->
                if (hourTotal <= 0.0) 0L
                else (dayOrders[w] * hourOrders[h] / hourTotal * noise("heat-${weekdays[w].day}-$h", 0.18)).roundToLong()
            }
        }
    }

    // ---- Parts of the whole ----

    private fun only(slices: List<Slice>, key: String?, ownShare: Double): List<Slice> {
        // Everything else is scaled by the other filters; the filtered dimension keeps just its own slice.
        val others = if (ownShare > 0) factor / ownShare else 0.0
        val kept = if (key == null) slices else slices.filter { it.key == key }
        return kept.map { it.copy(sales = it.sales.scaled(others), count = it.count.scaled(others)) }.sortedByDescending { it.sales }
    }

    val orderTypes: List<Slice> = only(typeSlices, filters.orderType, share(typeSlices, filters.orderType))
    val sources: List<Slice> = only(sourceSlices, filters.source, share(sourceSlices, filters.source))
    val floors: List<Slice> = sales?.floors.orEmpty().map { Slice(it.key, it.sales.centsOf().scaled(), it.orders.scaled()) }
    val sections: List<Slice> = only(sectionSlices, filters.section, share(sectionSlices, filters.section))
    val compareSections: List<Slice> = state.compareSales?.sections.orEmpty().map { Slice(it.section, it.sales.centsOf(), it.quantity) }

    // ---- Dishes ----

    private fun dishOf(item: ItemSalesDto, scale: Double): DishFigures {
        val sold = item.sales.centsOf().scaled(scale)
        val quantity = item.quantity.scaled(scale)
        val rate = (costRate(item.section, item.name) + (noise("cost-${item.name}", 0.12) - 1.0) * 0.25).coerceIn(0.12, 0.6)
        val types = typeSlices.ifEmpty { listOf(Slice("DINE_IN", 1, 1)) }
        val typeTotal = types.sumOf { it.count }.coerceAtLeast(1)
        val byType = types.associate { t ->
            val drinkPenalty = if (t.key == "DELIVERY" && isDrink(item.section, item.name)) 0.35 else 1.0
            t.key to (quantity * t.count.toDouble() / typeTotal * drinkPenalty * noise("type-${item.name}-${t.key}", 0.3)).roundToLong()
        }
        return DishFigures(item.menuItemId, item.name, item.section, quantity, sold, (sold * rate).roundToLong(), byType)
    }

    private val dishScale: Double = run {
        val sectionShare = share(sectionSlices, filters.section)
        if (sectionShare > 0) factor / sectionShare else 0.0
    }

    val dishes: List<DishFigures> = sales?.topItems.orEmpty().ifEmpty { overview?.topItems.orEmpty() }
        .filter { filters.section == null || it.section == filters.section }
        .map { dishOf(it, dishScale) }
    val slowDishes: List<DishFigures> = sales?.slowItems.orEmpty()
        .filter { filters.section == null || it.section == filters.section }
        .map { dishOf(it, dishScale) }

    /** The same dishes in the comparison, by name, for the change per dish. */
    val compareDishSales: Map<String, Long> = state.compareSales?.topItems.orEmpty().associate { it.name to it.sales.centsOf() }

    // ---- Payments ----

    val methods: List<MethodFigures> = run {
        val totalCollected = rawMethods.sumOf { it.collected.centsOf() }.coerceAtLeast(1)
        val others = if (methodShare > 0) factor / methodShare else 0.0
        rawMethods.filter { filters.method == null || it.method == filters.method }.map { m ->
            val collected = m.collected.centsOf()
            MethodFigures(m.method, collected.scaled(others), m.count.scaled(others), m.tips.centsOf().scaled(others),
                // MOCK: the refunds shared out by how much each method took.
                (totals.refunds * collected.toDouble() / totalCollected).roundToLong())
        }.sortedByDescending { it.collected }
    }

    val compareMethods: Map<String, Long> = state.compareOverview?.paymentMethods.orEmpty().associate { it.method to it.collected.centsOf() }

    /** MOCK: each day's sales shared out by how much each method took over the period. */
    val paymentsByDay: List<Pair<LocalDate, List<Long>>> = run {
        val total = methods.sumOf { it.collected }.toDouble().takeIf { it > 0 } ?: 1.0
        days.map { day ->
            val raw = methods.map { m -> day.sales * (m.collected / total) * noise("pay-${day.date}-${m.key}", 0.22) }
            val sum = raw.sum().takeIf { it > 0 } ?: 1.0
            day.date to raw.map { (it / sum * day.sales).roundToLong() }
        }
    }

    // ---- Branches ----

    val branches: List<BranchFigures> = state.branches.mapNotNull { branch ->
        val o = state.branchOverviews[branch.id] ?: return@mapNotNull null
        val k = o.kpis
        BranchFigures(
            branch.id, branch.name, k.sales.value.centsOf().scaled(), (k.orders.value.centsOf() / 100).scaled(), (k.guests.value.centsOf() / 100).scaled(),
            k.collected.value.centsOf().scaled(), k.tips.value.centsOf().scaled(),
            o.paymentMethods.map { MethodFigures(it.method, it.collected.centsOf().scaled(), it.count.scaled(), it.tips.centsOf().scaled(), 0) },
            o.topItems,
            daysOf(from, dayCount, o.salesByDay).map { it.sales }
        )
    }.sortedByDescending { it.sales }

    // ---- Staff ----

    val staff: List<PersonFigures> = rawStaff.filter { filters.staffId == null || it.staffId == filters.staffId }.map { p ->
        val others = if (staffShare > 0) factor / staffShare else 0.0
        PersonFigures(
            p.staffId, p.name, p.orders.scaled(others), p.sales.centsOf().scaled(others), p.guests.scaled(others), p.tips.centsOf().scaled(others),
            p.discounts.centsOf().scaled(others), p.removedItems.scaled(others), p.removedValue.centsOf().scaled(others), p.refunds.centsOf().scaled(others),
            p.voidedOrders.scaled(others), p.hoursWorked.value.toDoubleOrNull() ?: 0.0,
            // MOCK: 11–16 an hour by person until the wages come from Salary.
            wage = (1100 + (hash01("wage-${p.staffId}") * 500).roundToLong() / 50 * 50)
        )
    }.sortedByDescending { it.sales }

    val staffOptions: List<Pair<String, String>> = rawStaff.sortedBy { it.name.lowercase() }.map { it.staffId to it.name }

    // ---- Bookings (MOCK) ----

    val bookings: StatsBookings = run {
        val dineShare = typeSlices.sumOf { it.count }.takeIf { it > 0 }?.let { total ->
            (typeSlices.firstOrNull { it.key == "DINE_IN" }?.count ?: 0L).toDouble() / total
        } ?: 0.75
        val perDay = days.map { day ->
            // About four in ten eat-in guests booked ahead; the rest walk in. Without sales, a quiet house's numbers.
            val guests = if (totals.guests > 0) day.guests * dineShare * 0.42 else 26.0 * weekdayLoad(day.date.dayOfWeek)
            day.date to (guests * noise("book-${day.date}", 0.25)).roundToLong()
        }
        val bookingsByDay = perDay.map { (date, guests) -> date to (guests / 3.0).roundToLong() }
        val booked = bookingsByDay.sumOf { it.second }
        val guests = perDay.sumOf { it.second }
        val cancelled = (booked * 0.082).roundToLong()
        val noShows = (booked * 0.043).roundToLong()
        val upcoming = 0L
        val arrived = (booked - cancelled - noShows - upcoming).coerceAtLeast(0)
        val hourShape = listOf(12 to 0.07, 13 to 0.1, 14 to 0.05, 18 to 0.06, 19 to 0.17, 20 to 0.24, 21 to 0.19, 22 to 0.09, 23 to 0.03)
        val byHour = (11..23).map { h -> h to (booked * (hourShape.firstOrNull { it.first == h }?.second ?: 0.0) * noise("bh-$h", 0.15)).roundToLong() }
        val sources = listOf("PHONE" to 0.34, "WEBSITE" to 0.31, "IN_PERSON" to 0.15, "APPS" to 0.12, "SOCIAL" to 0.08).map { (key, part) ->
            Slice(key, 0, (booked * part * noise("src-$key", 0.2)).roundToLong())
        }.sortedByDescending { it.count }
        val bigGroups = (booked * 0.09).roundToLong()
        val deposits = bigGroups * 7 * 1000
        val refunded = (deposits * 0.14).roundToLong()
        val kept = (noShows * 0.5 * 7000).roundToLong().coerceAtMost(deposits - refunded)
        val occupancy = DayOfWeek.entries.map { d ->
            val load = weekdayLoad(d).toFloat()
            val lunch = ((0.32f + 0.28f * load) * noise("occ-l-$d", 0.12).toFloat()).coerceIn(0f, 0.98f)
            val dinner = ((0.48f + 0.42f * load) * noise("occ-d-$d", 0.1).toFloat()).coerceIn(0f, 0.99f)
            d to (lunch to dinner)
        }
        val compareGuests = compareDays?.sumOf { it.guests }?.let { (it * dineShare * 0.42).roundToLong() }
        StatsBookings(
            bookingsByDay, perDay, byHour, sources, booked, guests, arrived, cancelled, noShows, upcoming, occupancy, deposits, refunded, kept, bigGroups,
            compareGuests?.let { (it / 3.0).roundToLong() }, compareGuests
        )
    }

    // ---- Stock and costs (MOCK) ----

    val stock: StatsStock = run {
        val totalCost = dishes.sumOf { it.cost }.takeIf { it > 0 }
        val costRate = totalCost?.let { cost -> cost.toDouble() / dishes.sumOf { it.sales }.coerceAtLeast(1) } ?: 0.29
        val byDay = days.map { day ->
            val used = (day.sales * costRate * noise("used-${day.date}", 0.12)).roundToLong()
            // Deliveries come on Mondays, Wednesdays and Fridays; little in between.
            val delivery = when (day.date.dayOfWeek) { DayOfWeek.MONDAY -> 2.1; DayOfWeek.WEDNESDAY -> 1.6; DayOfWeek.FRIDAY -> 2.3; else -> 0.15 }
            val received = (day.sales * costRate * delivery * noise("recv-${day.date}", 0.2)).roundToLong()
            StatsStock.StockDay(day.date, received, used, (used * 0.028 * noise("waste-${day.date}", 0.5)).roundToLong())
        }
        val used = byDay.sumOf { it.used }
        val received = byDay.sumOf { it.received }
        val waste = byDay.sumOf { it.waste }
        val wasteItems = listOf("Fresh basil", "Mozzarella", "Bread", "Salad leaves", "Cream", "Tomatoes", "Fish of the day").mapIndexed { i, name ->
            Slice(name, (waste * listOf(0.22, 0.19, 0.16, 0.14, 0.11, 0.1, 0.08)[i] * noise("wi-$name", 0.3)).roundToLong(), 0)
        }.sortedByDescending { it.sales }
        val suppliers = listOf("Meat & fish" to 0.31, "Fresh produce" to 0.24, "Drinks" to 0.18, "Dairy" to 0.12, "Dry goods" to 0.09,
            "Bakery" to 0.04, "Cleaning & packaging" to 0.02).map { (name, part) -> Slice(name, (received * part * noise("sup-$name", 0.18)).roundToLong(), 0) }
            .sortedByDescending { it.sales }
        StatsStock(
            stockValue = ((used.toDouble() / dayCount) * 9 + 380_000).roundToLong(), received = received, used = used, waste = waste,
            returns = (received * 0.006).roundToLong(), adjustments = (used * 0.004 * (if (hash01("adj-$from") > 0.5) 1 else -1)).roundToLong(),
            byDay = byDay, wasteItems = wasteItems, suppliers = suppliers,
            compareUsed = compareTotals?.sales?.let { (it * costRate).roundToLong() }
        )
    }

    /** MOCK: VAT included in the prices, until the server reports the tax per rate (orders.tax_total). */
    val vatRate: Double = 0.20
    val salesBeforeTax: Long get() = (totals.sales / (1 + vatRate)).roundToLong()
    val vat: Long get() = totals.sales - salesBeforeTax
    /** What's left of the money taken once refunds went back. */
    val moneyKept: Long get() = totals.collected - totals.refunds
    /** Money taken that isn't sales, tips or refunds (rounding, unpaid parts of bills): what the bridge shows as "other". */
    val otherMoney: Long get() = moneyKept - (totals.sales + totals.tips - totals.refunds)

    /** Food cost: what the dishes used out of the sales. */
    val foodCost: Float get() = if (totals.sales > 0) stock.used.toFloat() / totals.sales else 0f
}

// ---- Small helpers ----

/** 0..1 from a text, the same every time (the MOCK figures don't jump between renders). */
internal fun hash01(key: String): Double {
    var h = 1125899906842597L
    key.forEach { h = 31 * h + it.code }
    h = h xor (h ushr 29)
    return ((h and 0xFFFFFF) / 16777215.0)
}

/** 1 ± [spread], the same every time for [key]. */
internal fun noise(key: String, spread: Double): Double = 1.0 + (hash01(key) - 0.5) * 2 * spread

/** How busy a weekday usually is, 0.5 (Monday) … 1.4 (Saturday). */
internal fun weekdayLoad(day: DayOfWeek): Double = when (day) {
    DayOfWeek.MONDAY -> 0.55; DayOfWeek.TUESDAY -> 0.62; DayOfWeek.WEDNESDAY -> 0.72; DayOfWeek.THURSDAY -> 0.86
    DayOfWeek.FRIDAY -> 1.25; DayOfWeek.SATURDAY -> 1.4; DayOfWeek.SUNDAY -> 1.0
    else -> 1.0
}

private fun isDrink(section: String?, name: String): Boolean {
    val words = "${section.orEmpty()} $name".lowercase()
    return listOf("drink", "bever", "wine", "beer", "cocktail", "coffee", "bar", "juice", "water", "soda").any { it in words }
}

/** MOCK: what a dish's ingredients cost, as a part of its price, by kind of dish. */
private fun costRate(section: String?, name: String): Double {
    val words = "${section.orEmpty()} $name".lowercase()
    return when {
        isDrink(section, name) -> 0.21
        listOf("dessert", "sweet", "cake", "tirami", "gelato").any { it in words } -> 0.24
        "pizza" in words -> 0.23
        listOf("pasta", "risotto", "spaghetti", "tagliatelle").any { it in words } -> 0.27
        listOf("salad", "starter", "antipast", "soup").any { it in words } -> 0.26
        listOf("steak", "ribeye", "grill", "meat", "fish", "burger", "main").any { it in words } -> 0.35
        else -> 0.29
    }
}
