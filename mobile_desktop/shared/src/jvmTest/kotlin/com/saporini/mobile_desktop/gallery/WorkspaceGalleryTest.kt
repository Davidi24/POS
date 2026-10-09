package com.saporini.mobile_desktop.gallery

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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private fun d(text: String) = OrderDecimal(text)
private fun kpi(now: String, before: String) = KpiDto(d(now), d(before))
private fun minutesAgo(minutes: Int) = (Clock.System.now() - minutes.minutes).toString()

private class GalleryStats : StatisticsRepository {
    private fun period(query: StatsQuery) = StatsPeriodDto(query.from.toString(), query.to.toString())
    private fun days(query: StatsQuery): List<DaySalesDto> {
        val sales = listOf("1840.50", "2210.00", "1625.40", "2480.90", "3120.00", "3560.75", "2050.10")
        return (0..6).map { i -> DaySalesDto(query.to.minus(DatePeriod(days = 6 - i)).toString(), d(sales[i]), 60L + i * 7, 110L + i * 12) }
    }
    private val items = listOf(
        ItemSalesDto("m1", "Margherita", "Pizza", 212, d("2120.00")), ItemSalesDto("m2", "Diavola", "Pizza", 140, d("1680.00")),
        ItemSalesDto("m3", "Tagliatelle al ragù", "Pasta", 96, d("1344.00")), ItemSalesDto("m4", "Tiramisù", "Desserts", 120, d("780.00")),
        ItemSalesDto("m5", "Peroni 33 cl", "Drinks", 260, d("1040.00")), ItemSalesDto("m6", "Caprese", "Starters", 54, d("540.00")),
        ItemSalesDto("m7", "Spritz", "Drinks", 88, d("704.00"))
    )
    override suspend fun overview(query: StatsQuery) = StatsOverviewDto(
        period(query),
        KpisDto(sales = kpi("16887.65", "15020.10"), orders = kpi("483", "455"), guests = kpi("958", "901"), averageTicket = kpi("34.96", "33.01"),
            averagePerGuest = kpi("17.63", "16.67"), tips = kpi("812.40", "760.00"), collected = kpi("17700.05", "15780.10"), refunds = kpi("64.00", "120.00"),
            discounts = kpi("410.20", "300.00"), removedItemsValue = kpi("96.50", "140.00"), cancelledOrders = kpi("6", "9"), voidedOrders = kpi("2", "1"),
            openOrders = 3),
        days(query),
        listOf(MethodTotalDto("CARD", d("11240.30"), 302, d("640.00")), MethodTotalDto("CASH", d("4120.75"), 141, d("150.40")),
            MethodTotalDto("CONTACTLESS", d("2339.00"), 61, d("22.00"))),
        listOf(ShareDto("DINE_IN", d("13110.00"), 371), ShareDto("TAKEAWAY", d("2780.65"), 82), ShareDto("DELIVERY", d("997.00"), 30)),
        items
    )
    override suspend fun sales(query: StatsQuery) = StatsSalesDto(
        period(query), days(query),
        (11..23).map { h -> HourSalesDto(h, d(listOf(120, 980, 1840, 1210, 420, 210, 180, 650, 1960, 3120, 2880, 1740, 640)[h - 11].toString()), 10L + h) },
        listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY").mapIndexed { i, day ->
            WeekdaySalesDto(i + 1, day, d("0"), 0, d(listOf("1840", "1620", "1900", "2210", "3120", "3560", "2480")[i])) },
        listOf(ShareDto("DINE_IN", d("13110.00"), 371), ShareDto("TAKEAWAY", d("2780.65"), 82)),
        listOf(ShareDto("POS", d("14200"), 410), ShareDto("QR_TABLE", d("1900"), 52), ShareDto("PHONE", d("787.65"), 21)),
        listOf(ShareDto("Main room", d("10240"), 290), ShareDto("Terrace", d("4890"), 140)),
        listOf(SectionSalesDto("Pizza", 352, d("3800")), SectionSalesDto("Pasta", 96, d("1344")), SectionSalesDto("Drinks", 348, d("1744")),
            SectionSalesDto("Desserts", 120, d("780"))),
        items,
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
        val model = StatisticsScreenModel(GalleryStats(), gallerySession()) { today }
        model.setActive(true); settle()
        fun shot(name: String, width: Int = 1440, height: Int = 1500, required: List<String> = emptyList()) = render(name, width, height, required) {
            val state by model.state.collectAsState()
            StatisticsContent(state, model, {})
        }
        shot("stats-overview", required = listOf("Overview", "Sales by day", "Money in", "Best sellers", "Worth a look"))
        shot("stats-overview-phone", 420, 2200)
        model.tab(StatsTab.SALES); settle()
        shot("stats-sales", height = 1900, required = listOf("Busiest hours", "Weekdays", "Dishes sold"))
        model.tab(StatsTab.STAFF); settle()
        shot("stats-staff", height = 1000, required = listOf("Leaderboard", "Giulia Rossi"))
        model.tab(StatsTab.REPORTS); settle()
        shot("stats-reports", height = 800, required = listOf("Sales per day", "Download"))
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
