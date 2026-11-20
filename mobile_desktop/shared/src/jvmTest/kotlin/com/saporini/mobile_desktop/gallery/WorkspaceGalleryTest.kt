package com.saporini.mobile_desktop.gallery

import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.saporini.mobile_desktop.fraud.FraudScreenModel
import com.saporini.mobile_desktop.fraud.FraudTab
import com.saporini.mobile_desktop.fraud.data.FraudActivityDto
import com.saporini.mobile_desktop.fraud.data.FraudActivityPageDto
import com.saporini.mobile_desktop.fraud.data.FraudAlertDto
import com.saporini.mobile_desktop.fraud.data.FraudAlertFilter
import com.saporini.mobile_desktop.fraud.data.FraudAlertPageDto
import com.saporini.mobile_desktop.fraud.data.FraudChecksRequestDto
import com.saporini.mobile_desktop.fraud.data.FraudDayCountDto
import com.saporini.mobile_desktop.fraud.data.FraudOverviewDto
import com.saporini.mobile_desktop.fraud.data.FraudQuery
import com.saporini.mobile_desktop.fraud.data.FraudRepository
import com.saporini.mobile_desktop.fraud.data.FraudReviewRequestDto
import com.saporini.mobile_desktop.fraud.data.FraudRuleCountDto
import com.saporini.mobile_desktop.fraud.data.FraudRuleSettingDto
import com.saporini.mobile_desktop.fraud.data.FraudRulesDto
import com.saporini.mobile_desktop.fraud.data.FraudStaffRiskDto
import com.saporini.mobile_desktop.fraud.ui.FraudContent
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.statistics.StatisticsScreenModel
import com.saporini.mobile_desktop.statistics.StatsTab
import com.saporini.mobile_desktop.statistics.data.DaySalesDto
import com.saporini.mobile_desktop.statistics.data.HourSalesDto
import com.saporini.mobile_desktop.statistics.data.ItemSalesDto
import com.saporini.mobile_desktop.statistics.data.KpiDto
import com.saporini.mobile_desktop.statistics.data.KpisDto
import com.saporini.mobile_desktop.statistics.data.MethodTotalDto
import com.saporini.mobile_desktop.statistics.data.ReportInfoDto
import com.saporini.mobile_desktop.statistics.data.SectionSalesDto
import com.saporini.mobile_desktop.statistics.data.ShareDto
import com.saporini.mobile_desktop.statistics.data.StaffStatsDto
import com.saporini.mobile_desktop.statistics.data.StatisticsRepository
import com.saporini.mobile_desktop.statistics.data.StatsOverviewDto
import com.saporini.mobile_desktop.statistics.data.StatsPeriodDto
import com.saporini.mobile_desktop.statistics.data.StatsQuery
import com.saporini.mobile_desktop.statistics.data.StatsSalesDto
import com.saporini.mobile_desktop.statistics.data.StatsStaffDto
import com.saporini.mobile_desktop.statistics.data.WeekdaySalesDto
import com.saporini.mobile_desktop.statistics.ui.StatisticsContent
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private fun d(text: String) = OrderDecimal(text)
private fun kpi(now: String, before: String) = KpiDto(d(now), d(before))
private fun minutesAgo(minutes: Int) = (Clock.System.now() - minutes.minutes).toString()

private class GalleryStats : StatisticsRepository {
    // Eat in with QR ordering on, takeaway and delivery off (as Bistroo): Where the money came from shows walk-ins and online orders.
    override suspend fun orderTypesOn(restaurantId: String) = setOf("DINE_IN", com.saporini.mobile_desktop.statistics.data.QR_ORDERING)
    // Each branch has its share; the days before (the comparison) a little less.
    private fun factor(query: StatsQuery): Double = (when (query.branchId) { "b1" -> 0.52; "b2" -> 0.3; "b3" -> 0.18; else -> 1.0 }) *
        (if (query.to < LocalDate(2026, 10, 2)) 0.88 else 1.0)
    private fun m(text: String, query: StatsQuery) = d(String.format(java.util.Locale.ROOT, "%.2f", text.toDouble() * factor(query)))
    private fun n(value: Long, query: StatsQuery) = (value * factor(query)).toLong()
    private fun k(now: String, before: String, query: StatsQuery) = KpiDto(m(now, query), m(before, query))
    private fun kc(now: Long, before: Long, query: StatsQuery) = KpiDto(d("${n(now, query)}"), d("${n(before, query)}"))
    private fun period(query: StatsQuery) = StatsPeriodDto(query.from.toString(), query.to.toString())
    private fun days(query: StatsQuery): List<DaySalesDto> {
        val sales = listOf("1840.50", "2210.00", "1625.40", "2480.90", "3120.00", "3560.75", "2050.10")
        // Every day of the range, the week's pattern repeating (a whole last month draws a whole line).
        val count = (query.to.toEpochDays() - query.from.toEpochDays()).toInt().coerceIn(0, 400)
        return (0..count).map { offset ->
            val i = (count - offset).let { back -> (6 - back % 7) }
            val date = query.to.minus(DatePeriod(days = count - offset))
            // Closed on Mondays, so the graph's closed days can be seen.
            if (date.dayOfWeek == kotlinx.datetime.DayOfWeek.MONDAY) DaySalesDto(date.toString(), d("0.00"), 0, 0)
            else DaySalesDto(date.toString(), m(sales[i], query), n(60L + i * 7, query), n(110L + i * 12, query))
        }
    }
    private val items = listOf(
        ItemSalesDto("m1", "Margherita", "Pizza", 212, d("2120.00")), ItemSalesDto("m2", "Diavola", "Pizza", 140, d("1680.00")),
        ItemSalesDto("m3", "Tagliatelle al ragù", "Pasta", 96, d("1344.00")), ItemSalesDto("m4", "Tiramisù", "Desserts", 120, d("780.00")),
        ItemSalesDto("m5", "Peroni 33 cl", "Drinks", 260, d("1040.00")), ItemSalesDto("m6", "Caprese", "Starters", 54, d("540.00")),
        ItemSalesDto("m7", "Spritz", "Drinks", 88, d("704.00")),
        // Every dish sold comes now (user, 2026-11-16), so Every dish sold has pages.
        ItemSalesDto("m11", "Bruschetta", "Starters", 48, d("336.00")), ItemSalesDto("m12", "Quattro formaggi", "Pizza", 44, d("572.00")),
        ItemSalesDto("m13", "Lasagne", "Pasta", 40, d("520.00")), ItemSalesDto("m14", "Espresso", "Drinks", 36, d("72.00")),
        ItemSalesDto("m15", "Panna cotta", "Desserts", 30, d("195.00")), ItemSalesDto("m16", "Limoncello", "Drinks", 22, d("110.00"))
    )
    override suspend fun overview(query: StatsQuery) = StatsOverviewDto(
        period(query),
        KpisDto(sales = k("16887.65", "15020.10", query), orders = kc(483, 455, query), guests = kc(958, 901, query), averageTicket = kpi("34.96", "33.01"),
            averagePerGuest = kpi("17.63", "16.67"), tips = k("812.40", "760.00", query), collected = k("17700.05", "15780.10", query),
            refunds = k("64.00", "120.00", query), discounts = k("410.20", "300.00", query), removedItemsValue = k("96.50", "140.00", query),
            cancelledOrders = kc(6, 9, query), voidedOrders = kc(2, 1, query), openOrders = 3),
        days(query),
        listOf(MethodTotalDto("CARD", m("11240.30", query), n(302, query), m("640.00", query)), MethodTotalDto("CASH", m("4120.75", query), n(141, query), m("150.40", query)),
            MethodTotalDto("CONTACTLESS", m("2339.00", query), n(61, query), m("22.00", query))),
        listOf(ShareDto("DINE_IN", m("13110.00", query), n(371, query)), ShareDto("TAKEAWAY", m("2780.65", query), n(82, query)),
            ShareDto("DELIVERY", m("997.00", query), n(30, query))),
        items.map { it.copy(quantity = n(it.quantity, query), sales = m(it.sales.value, query)) }
    )
    override suspend fun sales(query: StatsQuery) = StatsSalesDto(
        period(query), days(query),
        // A little at breakfast (10:00 only), so By service shows its breakfast card with every breakfast hour.
        (10..23).map { h -> HourSalesDto(h, d(listOf(60, 120, 980, 1840, 1210, 420, 210, 180, 650, 1960, 3120, 2880, 1740, 640)[h - 10].toString()), 10L + h) },
        listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY").mapIndexed { i, day ->
            WeekdaySalesDto(i + 1, day, d("0"), 0, d(listOf("1840", "1620", "1900", "2210", "3120", "3560", "2480")[i])) },
        listOf(ShareDto("DINE_IN", d("13110.00"), 371), ShareDto("TAKEAWAY", d("2780.65"), 82), ShareDto("DELIVERY", d("997.00"), 30)),
        listOf(ShareDto("POS", d("14200"), 410), ShareDto("QR_TABLE", d("1900"), 52), ShareDto("PHONE", d("787.65"), 21)),
        listOf(ShareDto("Main room", d("10240"), 290), ShareDto("Terrace", d("4890"), 140)),
        // Eight sections, so Sales by menu section shows its list scrolling past five.
        listOf(SectionSalesDto("Pizza", 352, d("3800")), SectionSalesDto("Pasta", 96, d("1344")), SectionSalesDto("Drinks", 348, d("1744")),
            SectionSalesDto("Desserts", 120, d("780")), SectionSalesDto("Starters", 140, d("1120")), SectionSalesDto("Salads", 64, d("704")),
            SectionSalesDto("Wine", 90, d("1260")), SectionSalesDto("Coffee", 210, d("420"))),
        items.map { it.copy(quantity = n(it.quantity, query), sales = m(it.sales.value, query)) },
        listOf(ItemSalesDto("m9", "Insalata di farro", "Starters", 3, d("33.00")), ItemSalesDto("m10", "Calzone vegano", "Pizza", 5, d("65.00")))
    )
    override suspend fun staff(query: StatsQuery) = StatsStaffDto(period(query), listOf(
        StaffStatsDto("s1", "Giulia Rossi", 162, d("6120.40"), d("37.78"), 330, d("310.20"), d("80.00"), 2, d("18.00"), d("0"), 0, d("38.5")),
        StaffStatsDto("s2", "Marco Bianchi", 141, d("4980.10"), d("35.32"), 270, d("240.00"), d("210.20"), 9, d("62.50"), d("64.00"), 1, d("36")),
        StaffStatsDto("s3", "Sara Conti", 120, d("3990.15"), d("33.25"), 240, d("182.20"), d("40.00"), 1, d("6.00"), d("0"), 0, d("30.25")),
        StaffStatsDto("s4", "Luca Ferri", 60, d("1797.00"), d("29.95"), 118, d("80.00"), d("80.00"), 3, d("10.00"), d("0"), 1, d("22"))
    ))
    override suspend fun reports(restaurantId: String) = listOf(
        ReportInfoDto("daily-sales", "Sales per day", "Sales, orders and guests for every day of the period"),
        ReportInfoDto("items", "Dishes sold", "Every dish sold, how many and for how much"),
        ReportInfoDto("staff", "Staff performance", "Orders, sales, tips, discounts, removals and refunds per person"),
        ReportInfoDto("payments", "Payments by method", "Money taken per payment method, with tips"))
    override suspend fun report(query: StatsQuery, code: String) = "a,b\n1,2\n"
}

private class GalleryFraud : FraudRepository {
    val alerts = listOf(
        FraudAlertDto("k1", "CLOSED_WITHOUT_PAYMENT", "HIGH", "Closed without payment", "Order closed with €48.00 still owed", minutesAgo(25), "s2", "Marco Bianchi",
            "o1", "A7K2", amount = d("48.00"), currency = "EUR"),
        FraudAlertDto("k2", "LARGE_DISCOUNT", "MEDIUM", "Big discount", "40% off a €86.00 bill", minutesAgo(70), "s2", "Marco Bianchi", "o2", "B3Q9",
            amount = d("34.40"), currency = "EUR"),
        FraudAlertDto("k3", "ITEM_VOID_AFTER_KITCHEN", "MEDIUM", "Item removed after cooking", "2 × Diavola removed after the kitchen got them",
            minutesAgo(190), "s4", "Luca Ferri", "o3", "C1M4", amount = d("24.00"), currency = "EUR", status = "REVIEWED", reviewedByName = "Davide Keci",
            reviewedAt = minutesAgo(100), reviewNote = "Guest changed their mind, dish went to staff"),
        FraudAlertDto("k4", "HIGH_TIP", "LOW", "High tip", "Tip of 45% on a card payment", minutesAgo(60 * 26), "s1", "Giulia Rossi", "o4", "D8P1",
            amount = d("18.00"), currency = "EUR"),
        FraudAlertDto("k5", "OFF_SHIFT_ACTION", "LOW", "Action while clocked out", "Refund while not clocked in", minutesAgo(60 * 30), "s3", "Sara Conti",
            amount = d("12.00"), currency = "EUR", status = "DISMISSED")
    )
    override suspend fun overview(query: FraudQuery) = FraudOverviewDto(
        query.from.toString(), query.to.toString(), currency = "EUR", openAlerts = 3, highOpenAlerts = 1, totalAlerts = 9, amountInvolved = d("214.40"),
        rules = listOf(FraudRuleCountDto("CLOSED_WITHOUT_PAYMENT", "HIGH", "Closed without payment", 1, 1, d("48")),
            FraudRuleCountDto("LARGE_DISCOUNT", "MEDIUM", "Big discount", 1, 3, d("90")),
            FraudRuleCountDto("ITEM_VOID_AFTER_KITCHEN", "MEDIUM", "Item removed after cooking", 0, 2, d("36")),
            FraudRuleCountDto("HIGH_TIP", "LOW", "High tip", 1, 2, d("28.4")),
            FraudRuleCountDto("OFF_SHIFT_ACTION", "LOW", "Action while clocked out", 0, 1, d("12"))),
        staff = listOf(FraudStaffRiskDto("s2", "Marco Bianchi", 2, 1, 4, d("120.40")), FraudStaffRiskDto("s4", "Luca Ferri", 0, 0, 2, d("36")),
            FraudStaffRiskDto("s1", "Giulia Rossi", 1, 0, 2, d("46"))),
        days = (0..6).map { i -> FraudDayCountDto(query.to.minus(DatePeriod(days = 6 - i)).toString(), listOf(1L, 0, 2, 1, 3, 0, 2)[i], if (i == 4 || i == 6) 1 else 0) },
        latest = alerts
    )
    override suspend fun alerts(query: FraudQuery, filter: FraudAlertFilter, page: Int, size: Int): FraudAlertPageDto {
        val items = alerts.filter { (filter.status == null || it.status == filter.status) && (filter.severity == null || it.severity == filter.severity) }
        return FraudAlertPageDto(items, 0, size, items.size.toLong(), 1, false)
    }
    override suspend fun activity(query: FraudQuery, type: String?, staffId: String?, page: Int, size: Int) = FraudActivityPageDto(listOf(
        FraudActivityDto("DISCOUNT", minutesAgo(70), "s2", "Marco Bianchi", "o2", "B3Q9", d("34.40"), "EUR", "40% off, “regular customer”"),
        FraudActivityDto("ITEM_REMOVED", minutesAgo(190), "s4", "Luca Ferri", "o3", "C1M4", d("24.00"), "EUR", "2 × Diavola, after the kitchen"),
        FraudActivityDto("REFUND", minutesAgo(60 * 30), "s3", "Sara Conti", "o5", "E2T7", d("12.00"), "EUR", "Cash refund", onShift = false),
        FraudActivityDto("ORDER_REOPENED", minutesAgo(60 * 31), "s1", "Giulia Rossi", "o6", "F9W3", null, null, "Paid order opened again")
    ), 0, size, 4, 1, false)
    override suspend fun rules(restaurantId: String) = FraudRulesDto(30, d("100.00"), 5, 30, 2, listOf(
        FraudRuleSettingDto("CLOSED_WITHOUT_PAYMENT", "HIGH", "Closed without payment", "An order closed with money still owed and no payment recorded"),
        FraudRuleSettingDto("LARGE_REFUND", "HIGH", "Big refund", "A refund at or above the set amount", threshold = "€100.00 or more"),
        FraudRuleSettingDto("MANY_VOIDS", "HIGH", "Many removals", "One person removed more items or orders in a day than the set limit", threshold = "More than 5 a day"),
        FraudRuleSettingDto("LARGE_DISCOUNT", "MEDIUM", "Big discount", "A discount at or above the set share of the bill", threshold = "30% or more"),
        FraudRuleSettingDto("PAYMENT_VOIDED", "MEDIUM", "Payment cancelled", "A payment taken and then cancelled", enabled = false),
        FraudRuleSettingDto("HIGH_TIP", "LOW", "High tip", "A tip at or above the set share of the payment", threshold = "30% or more")
    ))
    override suspend fun review(restaurantId: String, alertKey: String, request: FraudReviewRequestDto) = alerts.first { it.key == alertKey }.copy(status = request.status)
    override suspend fun updateChecks(restaurantId: String, request: FraudChecksRequestDto) {}
}

class WorkspaceGalleryTest {
    private val today = LocalDate(2026, 10, 8)

    @Test
    fun statistics() = gallery {
        val branches = listOf(com.saporini.mobile_desktop.statistics.StatsBranch("b1", "Main Branch"),
            com.saporini.mobile_desktop.statistics.StatsBranch("b2", "Blloku"), com.saporini.mobile_desktop.statistics.StatsBranch("b3", "Durrës"))
        // The Main room has places (Window, Bar side and tables in no place), so Sales by floor shows them under it.
        // A day's orders and one receipt, for the Day window (user, 2026-11-15).
        val dayOrders = listOf(
            com.saporini.mobile_desktop.statistics.StatsOrder("o1", "b1", "D-1041", "2026-10-06T10:12:00Z", "2026-10-06T10:48:00Z", "T04", "DINE_IN", "s1", 2, 3, 2350, "CLOSED"),
            com.saporini.mobile_desktop.statistics.StatsOrder("o2", "b1", "D-1042", "2026-10-06T12:05:00Z", "2026-10-06T13:10:00Z", "T07", "DINE_IN", "s2", 4, 7, 8640, "CLOSED"),
            com.saporini.mobile_desktop.statistics.StatsOrder("o3", "b1", "D-1043", "2026-10-06T12:40:00Z", null, null, "TAKEAWAY", "s1", null, 2, 1800, "CANCELLED"),
            com.saporini.mobile_desktop.statistics.StatsOrder("o4", "b1", "ORD-6DAE2D6AD218", "2026-10-06T19:22:00Z", "2026-10-06T21:05:00Z", "T12", "DINE_IN", "s2", 6, 11, 21480, "VOIDED", unpaid = true),
            com.saporini.mobile_desktop.statistics.StatsOrder("o5", "b1", "D-1045", "2026-10-06T20:02:00Z", "2026-10-06T21:30:00Z", "T03", "DINE_IN", "s1", 3, 6, 9720, "CLOSED"),
            // A table started and let go with nothing on it: left out of the day's list.
            com.saporini.mobile_desktop.statistics.StatsOrder("o6", "b1", "D-1046", "2026-10-06T20:40:00Z", "2026-10-06T20:55:00Z", "T09", "DINE_IN", "s2", 2, 0, 0, "CANCELLED"))
        fun dd(text: String) = com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal(text)
        val receipt = com.saporini.mobile_desktop.pos.payment.data.ReceiptDto(orderNumber = "D-1042", tableNumber = "T07", serverName = "Sara Basha", guestCount = 4,
            currency = "EUR", lines = listOf(
                com.saporini.mobile_desktop.pos.payment.data.ReceiptDto.Line("Margherita", null, 2, dd("10.00"), lineTotal = dd("20.00")),
                com.saporini.mobile_desktop.pos.payment.data.ReceiptDto.Line("Tagliatelle al ragù", null, 1, dd("14.00"), listOf("Extra parmesan"), dd("15.50")),
                com.saporini.mobile_desktop.pos.payment.data.ReceiptDto.Line("Ribeye", "300 g", 1, dd("32.00"), listOf("Medium rare"), dd("32.00"), notes = "Sauce on the side"),
                com.saporini.mobile_desktop.pos.payment.data.ReceiptDto.Line("Peroni 33 cl", null, 3, dd("4.00"), lineTotal = dd("12.00")),
                com.saporini.mobile_desktop.pos.payment.data.ReceiptDto.Line("Tiramisù", null, 1, dd("6.50"), lineTotal = dd("6.50"), removed = true)),
            subtotal = dd("79.50"), discounts = listOf(com.saporini.mobile_desktop.pos.payment.data.ReceiptDto.Discount("Regulars 10%", dd("7.95"))),
            discountTotal = dd("7.95"), serviceChargeTotal = dd("0.00"),
            tax = com.saporini.mobile_desktop.pos.payment.data.ReceiptDto.Tax(dd("20.00"), true, dd("11.93")), total = dd("71.55"),
            payments = listOf(com.saporini.mobile_desktop.pos.payment.data.ReceiptDto.Payment("CARD", amount = dd("50.00"), tipAmount = dd("5.00"), cardLast4 = "4821",
                paidAt = "2026-10-06T13:08:00Z", status = "COMPLETED"),
                com.saporini.mobile_desktop.pos.payment.data.ReceiptDto.Payment("CASH", amount = dd("21.55"), paidAt = "2026-10-06T13:09:00Z", status = "COMPLETED")),
            paidTotal = dd("71.55"), tipTotal = dd("5.00"))
        val model = StatisticsScreenModel(GalleryStats(), gallerySession(), today = { today }, branchesOf = { branches }, oneBranchPreview = false,
            dayOrdersOf = { _, _, _, _ -> dayOrders }, receiptOf = { _, _ -> receipt }, placesOf = {
            mapOf("b1" to mapOf("Main room" to listOf(com.saporini.mobile_desktop.statistics.StatsPlace("Window", 4),
                com.saporini.mobile_desktop.statistics.StatsPlace("Bar side", 3), com.saporini.mobile_desktop.statistics.StatsPlace(null, 5))))
        })
        model.setActive(true); settle()
        fun shot(name: String, width: Int = 1440, height: Int = 1500, required: List<String> = emptyList()) = render(name, width, height, required) {
            val state by model.state.collectAsState()
            StatisticsContent(state, model, {})
        }
        shot("stats-overview", height = 1450, required = listOf("Overview", "Total sales", "Sales by day", "Payment methods", "Contactless", "Best-selling dishes", "Against 1 – 8 Sep", "To keep an eye on"))
        shot("stats-overview-phone", 400, 2900)
        shot("stats-overview-tablet", 1024, 2000)
        // The places in the user's order (2026-11-13): Overview, Sales, Finance, Dishes, Tables, Team; the pages as they were.
        // Sales has two views, Sales (money) and Orders, switched like Inventory's (user, 2026-11-13).
        model.tab(StatsTab.SALES); settle()
        shot("stats-sales", height = 2700, required = listOf("Sales", "Total sales", "Average bill", "Best day", "Lowest day", "On track for",
            "after discounts · VAT included", "Day by day", "Sales by hour", "By service", "Breakfast", "Where the money came from", "Walk-ins", "Online orders", "Bill sizes",
            "All branches", "Main Branch", "Blloku"), )
        shot("stats-sales-1280", 1280, 900)
        // One branch picked at the top: the page is that branch's, exactly as for a restaurant with one branch (menu sections
        // and floors with their places come back).
        model.branch("b1"); settle()
        shot("stats-sales-branch", 1280, 2600, required = listOf("Main Branch", "Total sales", "Sales by menu section", "brings in the most",
            "Sales by floor", "Window", "Not in a place"))
        model.branch(null); settle()
        shot("stats-sales-tablet", 1024, 1000)
        shot("stats-sales-phone", 400, 1400)
        // Sales by hour on one day, from its own calendar (user, 2026-11-14).
        model.hoursDay(LocalDate(2026, 10, 6)); settle()
        shot("stats-sales-hours-day", 1280, 1500, required = listOf("Sales by hour", "Tue 6 Oct", "Only Tue 6 Oct, not 1 – 8 Oct"))
        model.hoursDay(null); settle()
        // Every day → a day → an order (user, 2026-11-15); the window is a dialog, so its content is rendered on its own.
        fun book(name: String, width: Int, height: Int, required: List<String>, narrow: Boolean = false) = render(name, width, height, required) {
            val state by model.state.collectAsState()
            com.saporini.mobile_desktop.statistics.ui.DayBookPreview(com.saporini.mobile_desktop.statistics.StatsView(state), state, model, today, narrow)
        }
        // Day by day as a calendar (user, 2026-11-15: "make the graph a toggle that can show as calendar").
        model.showSalesCalendar(true); settle()
        shot("stats-sales-calendar", 1280, 1100, required = listOf("Graph", "Calendar", "Day by day", "WEEK", "More sales", "Click a day to see its orders"))
        shot("stats-sales-calendar-phone", 400, 1100)
        model.showSalesCalendar(false); settle()
        // Sales details: every day as a card with its orders and its totals at the end (user, 2026-11-15).
        model.openEveryDay(); settle()
        book("stats-details", 980, 2400, listOf("Sales details", "Thursday 1 October", "Tuesday 6 October", "#D-1042", "Table T07", "Cancelled",
            "Unpaid", "TOTAL PAID", "AVERAGE BILL", "Best day", "Closed · no sales"))
        book("stats-details-phone", 400, 2600, listOf("Sales details", "Tuesday 6 October", "#D-1042"), narrow = true)
        // An order opens under its line, inside its day (user, 2026-11-15).
        model.openEveryDay(LocalDate(2026, 10, 6)); settle()
        model.openOrder(dayOrders[1]); settle()
        book("stats-details-order", 980, 2800, listOf("#D-1042", "What they had", "Ribeye", "Medium rare", "Removed", "The bill", "Regulars 10%",
            "VAT 20% (included)", "Card ••4821", "€5.00 tip", "#D-1045"))
        // Download all: the summary and a part per day, order numbers whole (user, 2026-11-15).
        val detailsInfo = com.saporini.mobile_desktop.statistics.ui.ReportInfo("Local Demo Bistroo", null, "Sales details", "1 – 8 Oct 2026",
            "1 – 8 Sep", "8 Oct 2026, 21:40", "EUR", "2026-10-01-to-2026-10-08")
        val detailsItems = com.saporini.mobile_desktop.statistics.ui.detailsItems(com.saporini.mobile_desktop.statistics.StatsView(model.state.value),
            model.state.value, assertNotNull(model.state.value.details?.orders), detailsInfo, today, kotlinx.datetime.TimeZone.UTC)
        assertTrue(detailsItems.size > 1, "Download all should hold the summary and the days")
        val detailsPages = listOf("summary" to detailsItems[0]) + detailsItems.drop(1).filter { it.report.rows.any { r -> r.contains("D-1042") } }.map { "day" to it }
        detailsPages.forEach { (name, item) ->
            item.pages.forEachIndexed { i, page ->
                render("stats-details-$name-page-${i + 1}", 620, 880) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFFF2F4F1)).padding(12.dp)) {
                        com.saporini.mobile_desktop.statistics.ui.DocPageView(page, androidx.compose.ui.Modifier.width(596.dp))
                    }
                }
            }
        }
        java.io.File("build/reports/gallery/stats-details-sample.pdf").writeBytes(com.saporini.mobile_desktop.core.files.pdfDocument(detailsItems.flatMap { it.pages }))
        model.closeEveryDay(); settle()
        // The tip a service card's hour column shows on hover or tap (a popup, so rendered on its own).
        render("stats-hour-tip", 240, 70, required = listOf("12:00 – 13:00", "orders")) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White).padding(10.dp)) {
                com.saporini.mobile_desktop.statistics.ui.HourTip(com.saporini.mobile_desktop.statistics.StatsView(model.state.value),
                    com.saporini.mobile_desktop.statistics.HourPoint(12, 1234000, 182))
            }
        }
        // The table a download's preview shows (the window itself is a dialog, so the table is rendered on its own).
        render("stats-download-preview", 760, 300, required = listOf("Floor", "Window", "Not in a place")) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White).padding(16.dp)) {
                val rows = com.saporini.mobile_desktop.statistics.ui.parseCsv("Floor,Place,Orders,Sales (EUR),Average bill (EUR),Share\n" +
                    "Main room,,290,10240.00,35.31,68%\nMain room,Window,96,3389.49,35.30,23%\nMain room,Bar side,68,2416.20,35.53,16%\n" +
                    "Main room,Not in a place,126,4434.31,35.19,29%\nTerrace,,140,4890.00,34.92,32%")
                com.saporini.mobile_desktop.statistics.ui.PreviewTable(rows.first(), rows.drop(1))
            }
        }
        // A card's report pages (chart, then the table with totals), as the export window shows them (the window is a dialog).
        val sampleChart = androidx.compose.ui.graphics.ImageBitmap(1100, 620).also {
            androidx.compose.ui.graphics.Canvas(it).apply {
                drawRect(0f, 0f, 1100f, 620f, androidx.compose.ui.graphics.Paint().apply { color = androidx.compose.ui.graphics.Color.White })
                drawRect(60f, 120f, 1040f, 560f, androidx.compose.ui.graphics.Paint().apply { color = androidx.compose.ui.graphics.Color(0xFFDCE8F8) })
            }
        }
        val sampleReport = com.saporini.mobile_desktop.statistics.ui.CardReport("sales-by-floor", "Where the money came from by floor",
            listOf(com.saporini.mobile_desktop.statistics.ui.ReportColumn("Floor"), com.saporini.mobile_desktop.statistics.ui.ReportColumn("Place"),
                com.saporini.mobile_desktop.statistics.ui.ReportColumn("Orders", com.saporini.mobile_desktop.statistics.ui.ColumnKind.COUNT),
                com.saporini.mobile_desktop.statistics.ui.ReportColumn("Sales", com.saporini.mobile_desktop.statistics.ui.ColumnKind.MONEY),
                com.saporini.mobile_desktop.statistics.ui.ReportColumn("Share", com.saporini.mobile_desktop.statistics.ui.ColumnKind.SHARE)),
            listOf(listOf<Any>("Main room", "Whole floor", 290L, 1024000L, "68%"), listOf<Any>("Main room", "Window", 96L, 338949L, "23%"),
                listOf<Any>("Main room", "Not in a place", 126L, 443431L, "29%"), listOf<Any>("Terrace", "Whole floor", 140L, 489000L, "32%")),
            listOf(com.saporini.mobile_desktop.statistics.ui.ReportTotal("Table sales", 1513000L),
                com.saporini.mobile_desktop.statistics.ui.ReportTotal("Orders", 430L, money = false)))
        val sampleInfo = com.saporini.mobile_desktop.statistics.ui.ReportInfo("Local Demo Bistroo", null, "Sales", "1 – 14 Nov 2026", "1 – 14 Oct",
            "15 Nov 2026, 12:40", "EUR", "2026-11-01-to-2026-11-14")
        val samplePages = com.saporini.mobile_desktop.statistics.ui.reportPages(sampleReport, sampleInfo, sampleChart, withChart = true, withTable = true)
        samplePages.forEachIndexed { i, page ->
            render("stats-report-page-${i + 1}", 620, 880) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFFF2F4F1)).padding(12.dp)) {
                    com.saporini.mobile_desktop.statistics.ui.DocPageView(page, androidx.compose.ui.Modifier.width(596.dp))
                }
            }
        }
        java.io.File("build/reports/gallery/stats-report-sample.pdf").writeBytes(com.saporini.mobile_desktop.core.files.pdfDocument(samplePages))
        // What a bill band's info mark says (a popup, so rendered on its own).
        render("stats-bill-tip", 320, 90, required = listOf("82 bills were under €20", "of all sales")) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White).padding(10.dp)) {
                com.saporini.mobile_desktop.statistics.ui.BandTip(com.saporini.mobile_desktop.statistics.StatsView(model.state.value),
                    com.saporini.mobile_desktop.statistics.BillBand(null, 2000, 82, 31240), 483, 1688765)
            }
        }
        // Sales by weekday for one week, from its own week calendar (user, 2026-11-14): this week, still running.
        model.week(LocalDate(2026, 10, 5)); settle()
        shot("stats-sales-week", 1280, 1500, required = listOf("Sales by weekday", "5 – 11 Oct", "was the best day", "Only 5 – 11 Oct, not 1 – 8 Oct"))
        model.week(null); settle()
        render("stats-week-calendar", 330, 380, required = listOf("September 2026", "Wk", "1 – 8 Oct", "This week")) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White)) {
                com.saporini.mobile_desktop.statistics.ui.PanelWeekCalendar(LocalDate(2026, 9, 28), today, "1 – 8 Oct") {}
            }
        }
        // The period button's popup: the quick picks beside the calendar (phones: above it), then By month and By year.
        render("stats-period-picker", 480, 520, required = listOf("This month", "Last 7 days", "This year", "All time", "By month", "By year", "October 2026",
            "9 Sep – 8 Oct · 30 days", "Done")) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White)) {
                // A range that starts in September: the end of September shows tinted before the 1st.
                com.saporini.mobile_desktop.statistics.ui.StatsPeriodPicker(com.saporini.mobile_desktop.statistics.StatsPeriod.CUSTOM,
                    LocalDate(2026, 9, 9), today, today, false, {}, { _, _ -> }, 2024, {}, {}, {})
            }
        }
        // By month (a whole month picked opens there): this year's months, those to come faded.
        render("stats-period-months", 480, 520, required = listOf("By month", "2026", "Sep", "Oct", "Dec", "so far", "September 2026 · 30 days", "Tap a month")) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White)) {
                com.saporini.mobile_desktop.statistics.ui.StatsPeriodPicker(com.saporini.mobile_desktop.statistics.StatsPeriod.CUSTOM,
                    LocalDate(2026, 9, 1), LocalDate(2026, 9, 30), today, false, {}, { _, _ -> }, 2024, {}, {}, {})
            }
        }
        // By year: the years from the restaurant's first.
        render("stats-period-years", 480, 520, required = listOf("By year", "Years", "2024", "2025", "2026", "2025 · 365 days", "Tap a year")) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White)) {
                com.saporini.mobile_desktop.statistics.ui.StatsPeriodPicker(com.saporini.mobile_desktop.statistics.StatsPeriod.CUSTOM,
                    LocalDate(2025, 1, 1), LocalDate(2025, 12, 31), today, false, {}, { _, _ -> }, 2024, {}, {}, {})
            }
        }
        render("stats-period-picker-phone", 320, 640, required = listOf("This month", "By month", "October 2026")) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White)) {
                com.saporini.mobile_desktop.statistics.ui.StatsPeriodPicker(com.saporini.mobile_desktop.statistics.StatsPeriod.THIS_MONTH,
                    LocalDate(2026, 10, 1), today, today, true, {}, { _, _ -> }, 2024, {}, {}, {})
            }
        }
        render("stats-hours-calendar", 300, 360, required = listOf("October 2026", "1 – 8 Oct", "Today")) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White)) {
                com.saporini.mobile_desktop.statistics.ui.PanelDayCalendar(LocalDate(2026, 10, 6), today, "1 – 8 Oct") {}
            }
        }
        // Orders is its own place since 2026-11-14 (was a Sales | Orders switch).
        model.tab(StatsTab.ORDERS); settle()
        shot("stats-orders", height = 2300, required = listOf("Orders by day", "What happened to orders", "Busy hours and days"))
        shot("stats-orders-phone", 400, 1400)
        model.tab(StatsTab.SALES)
        // This year: the graph shows the twelve months, compared with last year.
        model.period(com.saporini.mobile_desktop.statistics.StatsPeriod.THIS_YEAR); settle()
        shot("stats-sales-year", 1280, 640, required = listOf("Day by day"))
        // All time: from the restaurant's first day (here the fake's first sale, a year back), nothing to compare with.
        model.period(com.saporini.mobile_desktop.statistics.StatsPeriod.ALL_TIME); settle()
        shot("stats-sales-alltime", 1280, 700, required = listOf("All time · since", "Day by day"))
        model.period(com.saporini.mobile_desktop.statistics.StatsPeriod.THIS_MONTH); settle()
        // The Filters panel as it opens (its own render: a popup doesn't show in a page render).
        render("stats-filters-panel", 380, 520, required = listOf("Filters", "How it was ordered", "Menu section", "Done")) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.width(380.dp).background(androidx.compose.ui.graphics.Color.White)
                .padding(vertical = 8.dp)) {
                com.saporini.mobile_desktop.statistics.ui.FiltersPanel(1, listOf(
                    com.saporini.mobile_desktop.statistics.ui.FilterGroup("How it was ordered", androidx.compose.material.icons.Icons.Outlined.Info,
                        com.saporini.mobile_desktop.statistics.ui.StatsSeries[3], listOf(
                            com.saporini.mobile_desktop.statistics.ui.FilterChoice("All orders", false, {}),
                            com.saporini.mobile_desktop.statistics.ui.FilterChoice("Walk-ins", true, {}, com.saporini.mobile_desktop.statistics.ui.StatsSeries[1]),
                            com.saporini.mobile_desktop.statistics.ui.FilterChoice("Online orders", false, {}, com.saporini.mobile_desktop.statistics.ui.StatsSeries[5]))),
                    com.saporini.mobile_desktop.statistics.ui.FilterGroup("Menu section", androidx.compose.material.icons.Icons.Outlined.Info,
                        com.saporini.mobile_desktop.statistics.ui.StatsSeries[4], listOf("Whole menu", "Mains", "Starters", "Desserts", "Drinks", "Pizza")
                            .mapIndexed { i, n -> com.saporini.mobile_desktop.statistics.ui.FilterChoice(n, i == 0, {}) })
                ), {}, {})
            }
        }
        model.tab(StatsTab.FINANCE); settle()
        // Finance (user, 2026-11-15): Balance | Money in | Money out.
        shot("stats-finance", height = 2600, required = listOf("Finance", "Balance", "Money in", "Money out", "Days in the plus", "Money in and out",
            "Where the money came from", "Where the money went", "From the bills to the money kept", "Tax summary", "Check against the terminal and the bank"))
        shot("stats-finance-phone", 400, 2400)
        model.financeView(com.saporini.mobile_desktop.statistics.FinanceView.MONEY_IN); settle()
        shot("stats-finance-in", height = 2200, required = listOf("Money in by day", "Everything that came in", "Card"))
        model.financeView(com.saporini.mobile_desktop.statistics.FinanceView.MONEY_OUT); settle()
        shot("stats-finance-out", height = 2200, required = listOf("Money out by day", "Everything that went out", "Wages", "Stock from suppliers", "Refunds"))
        model.financeView(com.saporini.mobile_desktop.statistics.FinanceView.BALANCE); settle()
        model.tab(StatsTab.DISHES); settle()
        shot("stats-dishes", height = 2500, required = listOf("Dishes sold", "Gross profit", "Best seller", "Every dish sold", "COST EACH", "PROFIT",
            "1–10 of 13 dishes", "Page 1 of 2", "Popularity and margin"))
        shot("stats-dishes-phone", 400, 3000)
        // Stock on its own (user, 2026-11-16).
        model.tab(StatsTab.STOCK); settle()
        shot("stats-stock", height = 2000, required = listOf("Stock value", "Received", "Waste", "Food cost", "Stock in and out", "Stock movements"))
        model.tab(StatsTab.TABLES); settle()
        shot("stats-tables", height = 1700, required = listOf("Tables", "Bookings by day", "How full the tables were"))
        model.tab(StatsTab.TEAM); settle()
        shot("stats-team", height = 1900, required = listOf("Team", "Everyone side by side", "Giulia Rossi"))
        shot("stats-team-tablet", 1024, 2600)
        // One filter on: only takeaway (the figures follow its share for now).
        model.tab(StatsTab.OVERVIEW); model.filters { it.copy(orderType = "TAKEAWAY") }; settle()
        shot("stats-overview-filtered", height = 1400, required = listOf("Showing only", "Takeaway"))
        model.onDispose()
    }

    @Test
    fun fraud() = gallery {
        val model = FraudScreenModel(GalleryFraud(), gallerySession()) { today }
        model.setActive(true); settle()
        fun shot(name: String, width: Int = 1440, height: Int = 1100, required: List<String> = emptyList()) = render(name, width, height, required) {
            val state by model.state.collectAsState()
            FraudContent(state, model, {})
        }
        shot("fraud-overview", height = 1250, required = listOf("Fraud overview", "Alerts per day", "By check", "People to look at"))
        model.tab(FraudTab.ALERTS); model.filter(FraudAlertFilter()); settle()
        shot("fraud-alerts", required = listOf("Closed without payment"))
        model.tab(FraudTab.ACTIVITY); settle()
        shot("fraud-activity", required = listOf("Sensitive actions", "Cash refund"))
        model.tab(FraudTab.RULES); settle()
        shot("fraud-rules", required = listOf("Limits", "Big refund"))
        model.onDispose()
    }
}
