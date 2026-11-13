package com.saporini.mobile_desktop.statistics.ui

import mobile_desktop.shared.generated.resources.workspace_statistics
import mobile_desktop.shared.generated.resources.Res
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.TableBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.StatsView
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.components.IconTile

// Statistics → Sales & orders. Sales: when and where the money comes in (day by day against the comparison, measure by
// measure, the hours and weekdays, the branches, the order types, sources and rooms). Then Orders (OrdersTab.kt): how
// many, what happened to them, the busy hours and the items removed.

private enum class Measure(val label: String) { SALES("Sales"), ORDERS("Orders"), GUESTS("Guests"), AVERAGE("Average bill") }

@Composable
internal fun SalesOrdersTab(v: StatsView, size: ScreenSize) {
    val t = v.totals
    val c = v.compareTotals
    SectionHeading("Sales", "When and where the money comes in, against ${if (c == null) "nothing yet" else compareName(v).lowercase()}",
        Res.drawable.workspace_statistics, StatsSeries[0])
    TileGrid(listOf(
        { m -> StatTile("Sales", v.money(t.sales), m, v.change(t.sales, c?.sales), detail = "from ${countText(t.orders)} orders", icon = Icons.Outlined.Payments) },
        { m -> StatTile("A day on average", v.money(t.sales / v.dayCount), m, v.change(t.sales / v.dayCount, c?.let { it.sales / v.dayCount }),
            detail = "over ${v.dayCount} days", icon = Icons.Outlined.CalendarViewWeek) },
        { m -> StatTile("Per order", v.money(t.averageBill), m, v.change(t.averageBill, c?.averageBill), detail = "${countText(t.orders)} orders",
            icon = Icons.Outlined.Sell, tone = StatsSeries[2]) },
        { m -> StatTile("Per guest", v.money(t.perGuest), m, v.change(t.perGuest, c?.perGuest), detail = "${countText(t.guests)} guests",
            icon = Icons.Outlined.Groups, tone = StatsSeries[3]) }
    ))
    DayByDayPanel(v, size)
    PanelLine(size,
        1f to { m -> AgainstPanel(v, m) },
        1.4f to { m -> HoursPanel(v, m) }
    )
    PanelLine(size,
        1f to { m -> WeekdaysPanel(v, m) },
        1f to { m -> if (v.branches.size > 1) BranchSalesPanel(v, m) else RoomsPanel(v, m) }
    )
    PanelLine(size,
        1f to { m -> OrderTypesPanel(v, m, "By order type") },
        1f to { m -> SourcesPanel(v, m) },
        1f to { m -> if (v.branches.size > 1) RoomsPanel(v, m) else SectionsSharePanel(v, m) }
    )
    OrdersSection(v, size)
}

@Composable
private fun DayByDayPanel(v: StatsView, size: ScreenSize) {
    var measure by remember { mutableStateOf(Measure.SALES) }
    val days = v.days
    fun value(sales: Long, orders: Long, guests: Long): Float = when (measure) {
        Measure.SALES -> sales / 100f
        Measure.ORDERS -> orders.toFloat()
        Measure.GUESTS -> guests.toFloat()
        Measure.AVERAGE -> if (orders > 0) sales / 100f / orders else 0f
    }
    val money = measure == Measure.SALES || measure == Measure.AVERAGE
    val format: (Float) -> String = { if (money) v.money((it * 100).toLong()) else countText(it.toLong()) }
    StatsPanel(Icons.AutoMirrored.Outlined.ShowChart, "Day by day", Modifier.fillMaxWidth(), empty = days.all { it.sales == 0L },
        action = { FilterPills(Measure.entries.map { it to it.label }, measure, { measure = it }) }) {
        val t = v.totals
        val c = v.compareTotals
        when (measure) {
            Measure.SALES -> HeroLine(v.money(t.sales), "sales in ${v.dayCount} days", v.change(t.sales, c?.sales))
            Measure.ORDERS -> HeroLine(countText(t.orders), "orders in ${v.dayCount} days", v.change(t.orders, c?.orders))
            Measure.GUESTS -> HeroLine(countText(t.guests), "guests in ${v.dayCount} days", v.change(t.guests, c?.guests))
            Measure.AVERAGE -> HeroLine(v.money(t.averageBill), "an order on average", v.change(t.averageBill, c?.averageBill))
        }
        val color = when (measure) { Measure.SALES -> StatsSeries[0]; Measure.ORDERS -> StatsSeries[1]; Measure.GUESTS -> StatsSeries[3]; Measure.AVERAGE -> StatsSeries[2] }
        val series = listOfNotNull(
            LineSeries("This period", days.map { value(it.sales, it.orders, it.guests) }, color),
            v.compareDays?.let { LineSeries(compareName(v), it.map { d -> value(d.sales, d.orders, d.guests) }, Kit.Faint, dashed = true) }
        )
        if (series.size > 1) ChartLegend(series.map { it.color to it.name }, Modifier.padding(horizontal = 4.dp), lines = true, dashedLast = true)
        LineChart(series, days.map { dayLabel(it.date, v.dayCount) }, height = if (size.isPhone) 200.dp else 260.dp, format = format,
            axisFormat = { if (money) v.axisMoney(it) else compactNumber(it) }, titleOf = { weekdayDate(days[it].date) })
        val best = days.maxBy { it.sales }
        val quiet = days.filter { it.sales > 0 }.minByOrNull { it.sales } ?: best
        FactLine(listOf(
            Triple("Best day", weekdayDate(best.date), v.money(best.sales)),
            Triple("Quietest day", weekdayDate(quiet.date), v.money(quiet.sales)),
            Triple("Per order", v.money(t.averageBill), "on average"),
            Triple("Per guest", v.money(t.perGuest), "${countText(t.guests)} guests")
        ), size.isPhone)
    }
}

/** Each measure now (dark dot) against the comparison (hollow), with the change. */
@Composable
private fun AgainstPanel(v: StatsView, modifier: Modifier) {
    val t = v.totals
    val c = v.compareTotals
    StatsPanel(Icons.AutoMirrored.Outlined.CompareArrows, if (c == null) "This period" else "Against ${compareName(v).lowercase()}", modifier) {
        Dumbbells(listOf(
            Triple("Sales", t.sales.toFloat() to c?.sales?.toFloat(), v.money(t.sales) to c?.let { v.money(it.sales) }),
            Triple("Orders", t.orders.toFloat() to c?.orders?.toFloat(), countText(t.orders) to c?.let { countText(it.orders) }),
            Triple("Guests", t.guests.toFloat() to c?.guests?.toFloat(), countText(t.guests) to c?.let { countText(it.guests) }),
            Triple("Average bill", t.averageBill.toFloat() to c?.averageBill?.toFloat(), v.money(t.averageBill) to c?.let { v.money(it.averageBill) }),
            Triple("Spend per guest", t.perGuest.toFloat() to c?.perGuest?.toFloat(), v.money(t.perGuest) to c?.let { v.money(it.perGuest) })
        ), beforeName = compareName(v).takeIf { c != null })
        if (c == null) PanelNote("Choose something to compare with in Filters.")
    }
}

@Composable
private fun HoursPanel(v: StatsView, modifier: Modifier) {
    val hours = v.hours
    val first = hours.indexOfFirst { it.sales > 0 }
    val last = hours.indexOfLast { it.sales > 0 }
    StatsPanel(Icons.Outlined.AccessTime, "Sales by hour", modifier, empty = first < 0) {
        val shown = hours.subList(first, last + 1)
        val compare = v.compareHours?.associateBy { it.hour }
        val peak = shown.maxBy { it.sales }
        PanelNote("Busiest at ${hourLabel(peak.hour)}:00 – ${hourLabel((peak.hour + 1) % 24)}:00: ${v.money(peak.sales)} from ${peak.orders} orders")
        ColumnChart(shown.map { h ->
            ColumnDatum(hourLabel(h.hour), h.sales / 100f, "${v.money(h.sales)} · ${h.orders} orders", compare?.get(h.hour)?.sales?.let { it / 100f })
        }, roomLeft(), height = 220.dp, axisFormat = { v.axisMoney(it) }, peakLabel = { v.axisMoney(it) }, compareName = compareName(v).takeIf { compare != null })
    }
}

@Composable
private fun WeekdaysPanel(v: StatsView, modifier: Modifier) {
    val days = v.weekdays
    StatsPanel(Icons.Outlined.CalendarViewWeek, "Sales by weekday", modifier, empty = days.all { it.averageSales == 0L }, note = "average per day") {
        val best = days.maxBy { it.averageSales }
        PanelNote("${weekdayName(best.day)}s are the strongest: ${v.money(best.averageSales)} on an average day")
        ColumnChart(days.map { d -> ColumnDatum(weekdayName(d.day).take(3), d.averageSales / 100f,
            "${v.money(d.averageSales)} on average · ${d.orders} orders in all") }, roomLeft(), height = 220.dp, color = StatsSeries[0],
            axisFormat = { v.axisMoney(it) }, peakLabel = { v.axisMoney(it) })
    }
}

/** Each branch's share of the sales, then each branch's sales day by day (a line each, in the branch's colour). */
@Composable
private fun BranchSalesPanel(v: StatsView, modifier: Modifier) {
    StatsPanel(Icons.Outlined.Business, "Sales by branch", modifier) {
        SplitBar(v.branches.map { b -> Part(b.id, b.name, b.sales.toFloat(), v.branchColor(b.id), v.money(b.sales), "${b.orders} orders · ${v.money(b.averageBill)} a bill") })
        val days = v.days
        if (days.size > 1) {
            Text("Day by day", Modifier.padding(start = 4.dp, top = 6.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
            LineChart(v.branches.map { b -> LineSeries(b.name, b.days.map { it / 100f }, v.branchColor(b.id)) }, days.map { dayLabel(it.date, v.dayCount) },
                roomLeft(), height = 160.dp, format = { v.money((it * 100).toLong()) }, axisFormat = { v.axisMoney(it) },
                titleOf = { weekdayDate(days[it].date) }, wash = false)
        }
    }
}

@Composable
private fun SourcesPanel(v: StatsView, modifier: Modifier) {
    StatsPanel(Icons.Outlined.Devices, "Where orders came from", modifier, empty = v.sources.isEmpty(), emptyText = "Till, QR, website and app orders show here.") {
        SplitBar(v.sources.map { Part(it.key, sourceLabel(it.key), it.sales.toFloat(), sourceColor(it.key), v.money(it.sales), "${it.count} orders") })
    }
}

@Composable
private fun RoomsPanel(v: StatsView, modifier: Modifier) {
    StatsPanel(Icons.Outlined.TableBar, "Sales by room", modifier, empty = v.floors.isEmpty(), emptyText = "Sales per room show here once tables are used.") {
        RankBars(v.floors.map { Triple(it.key, it.sales.toFloat(), v.money(it.sales)) }, details = v.floors.map { "${it.count} orders" }, color = StatsSeries[1],
            leading = { IconTile(Icons.Outlined.TableBar, StatsSeries[1]) })
    }
}

@Composable
private fun SectionsSharePanel(v: StatsView, modifier: Modifier) {
    StatsPanel(Icons.Outlined.TableBar, "Sales by menu section", modifier, empty = v.sections.isEmpty()) {
        RankBars(v.sections.map { Triple(it.key, it.sales.toFloat(), v.money(it.sales)) }, details = v.sections.map { "${it.count} sold" })
    }
}

internal fun weekdayName(day: kotlinx.datetime.DayOfWeek): String = day.name.lowercase().replaceFirstChar { it.uppercase() }
