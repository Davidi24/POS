package com.saporini.mobile_desktop.statistics.ui

import mobile_desktop.shared.generated.resources.workspace_statistics
import mobile_desktop.shared.generated.resources.settings_shifts
import mobile_desktop.shared.generated.resources.shifts_planned
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.role_finance
import mobile_desktop.shared.generated.resources.Res
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import com.saporini.mobile_desktop.core.components.ScrollColumn
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.TableBar
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.number
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.TrendBadge
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.StatsView
import androidx.compose.ui.Alignment
import com.saporini.mobile_desktop.statistics.DayPoint
import com.saporini.mobile_desktop.statistics.CompareMode
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.components.IconTile

// Statistics → Sales, view Sales: when and where the money comes in (day by day against the comparison, measure by
// measure, the hours and weekdays, the branches, the order types, sources and rooms). Its Orders view is OrdersTab.kt:
// how many, what happened to them, the busy hours and the items removed.


@Composable
internal fun SalesMoneyTab(v: StatsView, size: ScreenSize, today: LocalDate, onHoursDay: (LocalDate?) -> Unit = {}, onWeek: (LocalDate?) -> Unit = {},
                           onDay: (LocalDate) -> Unit = {}, onEveryDay: () -> Unit = {}, onCalendar: (Boolean) -> Unit = {}) {
    val t = v.totals
    // Sales details and its days only for a month or less (user, 2026-11-15: "if it's one year you can't, only for one month
    // and below").
    val details = v.dayCount <= com.saporini.mobile_desktop.statistics.DetailsMaxDays
    val c = v.compareTotals
    // No heading band here (user, 2026-11-13: "no need"); the switch above already says Sales. The money only (user, 2026-11-13): total, what one order brings in, the best and lowest day and, on the right, where a running
    // month is heading. Orders have their own view.
    val best = v.days.filter { it.sales > 0 }.maxByOrNull { it.sales }
    // The lowest day with sales; today doesn't count while it's still going.
    val lowest = v.days.filter { it.sales > 0 && it.date != today }.minByOrNull { it.sales }
    TileGrid(buildList<@Composable (Modifier) -> Unit> {
        // The app's 3D pictures (user, 2026-11-13): money, the bill, a calendar, a rising chart.
        // What the total is, said on the card (user, 2026-11-14): the bills after discounts, VAT in them.
        add { m -> StatTile("Total sales", v.money(t.sales), m, v.change(t.sales, c?.sales), detail = "after discounts · VAT included",
            picture = Res.drawable.role_finance) }
        add { m -> StatTile("Average bill", v.money(t.averageBill), m, v.change(t.averageBill, c?.averageBill), detail = "what one order brings in",
            tone = StatsSeries[2], picture = Res.drawable.settings_payments) }
        add { m -> StatTile("Best day", best?.let { v.money(it.sales) } ?: "–", m,
            detail = best?.let { com.saporini.mobile_desktop.core.format.weekdayDate(it.date) } ?: "no sales yet",
            tone = StatsSeries[4], picture = Res.drawable.shifts_planned, onClick = best?.takeIf { details }?.let { { onDay(it.date) } }) }
        add { m -> StatTile("Lowest day", lowest?.let { v.money(it.sales) } ?: "–", m,
            detail = lowest?.let { com.saporini.mobile_desktop.core.format.weekdayDate(it.date) } ?: "not enough days yet",
            tone = Kit.Grey, picture = Res.drawable.settings_shifts, onClick = lowest?.takeIf { details }?.let { { onDay(it.date) } }) }
    })
    // A day on the graph (or Details, or the best/lowest day) opens what made it (user, 2026-11-15).
    DayLinePanel(v, size, today, DayMeasure.SALES, onDay = onDay.takeIf { details }, onEveryDay = onEveryDay.takeIf { details },
        onCalendar = onCalendar.takeIf { details })
    if (v.state.branches.size <= 1) {
        // One branch: the page exactly as the user approved it (2026-11-14: "for a single branch it's perfect"; the moves
        // below were asked for restaurants with several branches only). Don't change this order.
        PanelLine(size,
            1.4f to { m -> HoursPanel(v, today, onHoursDay, m) },
            1f to { m -> SectionsPanel(v, m) }
        )
        PanelLine(size,
            1.25f to { m -> ServicesPanel(v, m, stacked = size.isPhone) },
            1f to { m -> WeekdaysPanel(v, today, onWeek, m) }
        )
        PanelLine(size,
            1f to { m -> BillSizesPanel(v, m) },
            1f to { m -> FloorsPanel(v, m) },
            1f to { m -> MoneySourcesPanel(v, m) }
        )
    } else {
        // Several branches (user, 2026-11-14): lunch vs dinner beside the bill sizes, then the weekdays, the floors and where
        // the money came from, and Sales by hour with the menu sections at the end. Menu sections and floors are a branch's
        // own, so on All branches they wait until a branch is picked at the top.
        PanelLine(size,
            1.25f to { m -> ServicesPanel(v, m, stacked = size.isPhone) },
            1f to { m -> BillSizesPanel(v, m) }
        )
        if (v.allBranches) PanelLine(size,
            1f to { m -> WeekdaysPanel(v, today, onWeek, m, note = false) },
            1f to { m -> MoneySourcesPanel(v, m) }
        ) else PanelLine(size,
            1f to { m -> WeekdaysPanel(v, today, onWeek, m, note = false) },
            1f to { m -> FloorsPanel(v, m) },
            1f to { m -> MoneySourcesPanel(v, m) }
        )
        if (v.allBranches) HoursPanel(v, today, onHoursDay, Modifier.fillMaxWidth())
        else PanelLine(size,
            1.4f to { m -> HoursPanel(v, today, onHoursDay, m) },
            1f to { m -> SectionsPanel(v, m) }
        )
    }
    // Nothing by branch here (user, 2026-11-14: the single-branch page is perfect; with several branches one is picked at the top).
}

/** What a by-day graph shows: the money (Sales view, green) or the orders (Orders view, blue). */
internal enum class DayMeasure { SALES, ORDERS }

// The by-day graph: a little taller, with more amounts on the left (user, 2026-11-15).
private fun DayGraphHeight(size: ScreenSize) = if (size.isPhone) 220.dp else 300.dp
private const val DayGraphSteps = 5

/**
 * The by-day graph of the Sales and Orders views (user, 2026-11-14: one measure each, no switch; Orders in its own colour).
 * A month shows all its days: this month's line stops at the last day with any, last month's goes on to its end, so you
 * see how this month is doing against the last one before it's over. Closed days get a band and no point. The small
 * figures line ends with where the month is heading.
 */
@Composable
internal fun DayLinePanel(v: StatsView, size: ScreenSize, today: LocalDate, measure: DayMeasure, modifier: Modifier = Modifier.fillMaxWidth(),
                          onDay: ((LocalDate) -> Unit)? = null, onEveryDay: (() -> Unit)? = null, onCalendar: ((Boolean) -> Unit)? = null) {
    val days = v.days
    val money = measure == DayMeasure.SALES
    val calendar = onCalendar != null && v.state.salesCalendar
    fun value(d: DayPoint): Float = if (money) d.sales / 100f else d.orders.toFloat()
    val format: (Float) -> String = { if (money) v.money((it * 100).toLong()) else countText(it.toLong()) }
    // A branch picked at the top draws in its own colour, the one its dot has (user, 2026-11-14: "Blloku should be blue").
    val branchPicked = v.state.branchId?.takeIf { v.state.branches.size > 1 }
    val color = branchPicked?.let { v.branchColor(it) } ?: if (money) StatsSeries[0] else StatsSeries[1]
    StatsPanel(Icons.AutoMirrored.Outlined.ShowChart, if (money) "Day by day" else "Orders by day", modifier, empty = days.all { it.sales == 0L && it.orders == 0L },
        action = {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The graph or the month as a calendar (user, 2026-11-15: "make the graph a toggle that can show as calendar").
            onCalendar?.let { change -> GraphCalendarToggle(calendar, change, compact = size.isPhone) }
            // Sales details: every day with its orders (user, 2026-11-15).
            onEveryDay?.let { open -> DetailsButton(open) }
            CardDownload(v, if (money) "sales-by-day" else "orders-by-day") {
                CardReport(if (money) "sales-by-day" else "orders-by-day", if (money) "Day by day" else "Orders by day",
                    listOf(ReportColumn("Date"), ReportColumn("Day"), ReportColumn("Sales", ColumnKind.MONEY), ReportColumn("Orders", ColumnKind.COUNT),
                        ReportColumn("Guests", ColumnKind.COUNT)),
                    days.map { d -> listOf(com.saporini.mobile_desktop.core.format.shortDate(d.date, withYear = true), weekdayName(d.date.dayOfWeek), d.sales, d.orders, d.guests) },
                    listOf(ReportTotal("Total sales", days.sumOf { it.sales }), ReportTotal("Orders", days.sumOf { it.orders }, money = false),
                        ReportTotal("Guests", days.sumOf { it.guests }, money = false)))
            }
          }
        }) {
        val t = v.totals
        val c = v.compareTotals
        val axis = v.axisDays
        val wholeLastMonth = v.monthAxis && v.compareMode == CompareMode.LAST_MONTH
        // Where the period ends when it's a month or a year still running (for the estimate).
        val periodEnd = when {
            v.monthAxis && axis.isNotEmpty() -> axis.last()
            v.yearAxis -> LocalDate(v.from.year, 12, 31)
            else -> null
        }
        val running = periodEnd != null && v.to == today && today < periodEnd
        // Closed days (user, 2026-11-14): no drop to 0. A finished day without sales gets a light band and no point (the
        // line goes straight over it); a weekday that's always been closed is expected closed in the days to come too.
        // From the branches' opening hours and special days when they're set (user, 2026-11-20), else worked out from the sales.
        val closedWeekdays = remember(days, v.axisCompareDays, v.yearCompareDays) {
            val seen = (days.filter { it.date != today } + (if (v.yearAxis) v.yearCompareDays else v.axisCompareDays).orEmpty()).groupBy { it.date.dayOfWeek }
            seen.filter { (_, list) -> list.isNotEmpty() && list.all { it.sales == 0L } }.keys
        }
        val noSales = days.indices.filter { v.closedDay(days[it].date, days[it].sales, days[it].sales == 0L && days[it].date != today) }.toSet()
        val closedAhead = if (running && v.monthAxis) axis.indices.filter { axis[it] > today &&
            (v.closedDays?.contains(axis[it]) ?: (axis[it].dayOfWeek in closedWeekdays)) }.toSet() else emptySet()
        // The line ends at the last day with sales (today counts once it has some).
        val lastReal = if (days.lastOrNull()?.let { it.date == today && it.sales == 0L } == true) days.lastIndex - 1 else days.lastIndex
        val actual: List<Float?> = days.mapIndexed { i, d -> if (i > lastReal || i in noSales) null else value(d) }
        // Where the month is heading: what's done plus each open day still to come at its weekday's average this month
        // (the month's average for a weekday that hasn't come yet). Only the total, never a line (user, 2026-11-14).
        val open = days.filterIndexed { i, d -> i <= lastReal && i !in noSales && d.date != today }
        val onTrack: Long? = if (running && open.isNotEmpty() && lastReal >= 0) {
            val byWeekday = open.groupBy { it.date.dayOfWeek }.mapValues { (_, list) -> list.map { value(it) }.average() }
            val pace = open.map { value(it) }.average()
            // Every open day from the one after the last real day to the end of the month (or year).
            var date = days.getOrNull(lastReal)?.date?.plus(DatePeriod(days = 1)) ?: v.from
            var ahead = 0.0
            while (date <= periodEnd!!) {
                if (!(v.closedDays?.contains(date) ?: (date.dayOfWeek in closedWeekdays))) ahead += byWeekday[date.dayOfWeek] ?: pace
                date = date.plus(DatePeriod(days = 1))
            }
            if (money) (days.sumOf { it.sales } + (ahead * 100).toLong()) / 100 * 100 else days.sumOf { it.orders } + ahead.toLong()
        } else null
        val compare = v.axisCompareDays?.map { d -> if (d.sales == 0L) null else value(d) }
        val figure = if (money) v.money(t.sales) else countText(t.orders)
        val change = if (money) v.change(t.sales, c?.sales) else v.change(t.orders, c?.orders)
        val caption = "${if (money) "sales" else "orders"} in ${v.dayCount} days"
        // A small figures line (the big total is the card above): the figure, its change, and the estimate at its end.
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(figure, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink, maxLines = 1)
                TrendBadge(change, false)
                Text(caption, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (onTrack != null) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
                    Text("On track for ${if (money) v.money(onTrack) else "${countText(onTrack)} orders"}", fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = color, maxLines = 1)
                    Text("by ${com.saporini.mobile_desktop.core.format.shortDate(periodEnd ?: v.to)} at this pace", fontFamily = Inter(), fontSize = 11.sp,
                        color = Kit.Muted, maxLines = 1)
                }
            }
        }
        // The calendar instead of the graph: a day opens Sales details on it.
        if (calendar) {
            // About the graph's size, a little bigger (user, 2026-11-15: "so big, make it similar to the graph", then "a little bigger").
            MonthCalendar(v, today, size.isPhone, onDay, Modifier.padding(top = 4.dp), height = DayGraphHeight(size) + 80.dp)
            return@StatsPanel
        }
        // A year shows the twelve months: one point a month (its total), no days (user, 2026-11-14). This year's line
        // stops at this month (so far); last year's goes on to December.
        if (v.yearAxis) {
            fun monthly(list: List<DayPoint>, year: Int): List<Float?> = (1..12).map { m ->
                val inMonth = list.filter { it.date.year == year && it.date.month.number == m && it.sales > 0 }
                if (inMonth.isEmpty()) null else inMonth.sumOf { value(it).toDouble() }.toFloat()
            }
            val lastYear = v.compareMode == CompareMode.LAST_YEAR
            val thisYear = monthly(days.take(lastReal + 1), v.from.year)
            // The month still running is only part of a month: dotted from the last whole month, so it doesn't read as a fall.
            val partial = if (running) today.month.number - 1 else -1
            val split = partial > 0 && thisYear.getOrNull(partial) != null && thisYear.getOrNull(partial - 1) != null
            val whole = if (split) thisYear.mapIndexed { i, x -> if (i == partial) null else x } else thisYear
            val soFar = if (split) thisYear.mapIndexed { i, x -> if (i == partial || i == partial - 1) x else null } else null
            val compareYear = v.yearCompareDays?.let { monthly(it, v.from.year - 1) }
            // Several branches on All branches: a line each too, in its colour (user, 2026-11-14).
            val branchYears = if (v.allBranches) v.branches.map { b -> LineSeries(b.name, monthly(b.dayPoints.take(lastReal + 1), v.from.year), v.branchColor(b.id)) }
                else emptyList()
            val yearSeries = listOfNotNull(
                LineSeries(if (v.allBranches) "All branches" else if (lastYear) "${v.from.year}" else "This period", whole, color),
                soFar?.let { LineSeries("${monthName(today)} so far", it, color, dotted = true) }
            ) + branchYears + listOfNotNull(
                compareYear?.let { LineSeries(if (lastYear) "${v.from.year - 1}" else compareName(v), it, Kit.Faint, dashed = true) }
            )
            if (yearSeries.size > 1) ChartLegend(yearSeries.map { it.color to it.name }, Modifier.padding(horizontal = 4.dp), lines = true,
                dashedLast = compareYear != null, dotted = if (soFar != null) setOf(1) else emptySet())
            val months = kotlinx.datetime.Month.entries
            LineChart(yearSeries, months.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3) },
                height = DayGraphHeight(size), steps = DayGraphSteps, format = format, axisFormat = { if (money) v.axisMoney(it) else compactNumber(it) },
                titleOf = { i ->
                    "${months[i].name.lowercase().replaceFirstChar { it.uppercase() }} ${v.from.year}" +
                        if (running && i + 1 == today.month.number) " · so far" else ""
                },
            )
            return@StatsPanel
        }
        // Several branches on All branches (user, 2026-11-14): the whole restaurant in green, then a line per branch in its
        // colour, the comparison dashed last. One branch (or one picked at the top) looks as it always did.
        val branchLines = if (v.allBranches) v.branches.map { b ->
            LineSeries(b.name, b.dayPoints.mapIndexed { i, d -> if (i > lastReal || i in noSales || d.sales == 0L) null else value(d) }, v.branchColor(b.id))
        } else emptyList()
        val series = listOf(LineSeries(if (v.allBranches) "All branches" else if (wholeLastMonth) monthName(v.from) else "This period", actual, color)) +
            branchLines + listOfNotNull(compare?.let {
                LineSeries(if (wholeLastMonth) monthName(v.from.minus(DatePeriod(months = 1))) else compareName(v), it, Kit.Faint, dashed = true)
            })
        if (series.size > 1) ChartLegend(series.map { it.color to it.name }, Modifier.padding(horizontal = 4.dp), lines = true, dashedLast = compare != null)
        LineChart(series, axis.map { dayLabel(it, axis.size) }, height = DayGraphHeight(size), steps = DayGraphSteps, format = format,
            axisFormat = { if (money) v.axisMoney(it) else compactNumber(it) }, shaded = noSales + closedAhead,
            titleOf = { i ->
                val date = axis[i]
                weekdayDate(date) + when {
                    i in noSales || i in closedAhead -> if (v.closedDays != null || date.dayOfWeek in closedWeekdays) " · Closed" else " · No sales"
                    else -> ""
                }
            },
            // A day of the period, up to today, opens the Day window.
            onPick = onDay?.let { open -> { i: Int -> open(axis[i]) } },
            pickHint = { i -> "Click to open the day ›".takeIf { axis.getOrNull(i)?.let { it >= v.from && it <= v.to && it <= today } == true } })
    }
}

/**
 * The period's sales hour by hour, or one day's: the panel has its own calendar (user, 2026-11-14). No comparison here (the
 * by-day graph has it). While a day loads, what was shown stays, dimmed.
 */
@Composable
private fun HoursPanel(v: StatsView, today: LocalDate, onDay: (LocalDate?) -> Unit, modifier: Modifier) {
    val day = v.hoursDay
    val hours = if (day == null) v.hours else v.dayHours ?: v.hours
    val first = hours.indexOfFirst { it.sales > 0 }
    val last = hours.indexOfLast { it.sales > 0 }
    val dim = if (v.hoursLoading) 0.45f else 1f
    StatsPanel(Icons.Outlined.AccessTime, "Sales by hour", modifier, empty = first < 0 && !v.hoursLoading,
        emptyText = day?.let { "No sales on ${weekdayDate(it)}." } ?: "Figures show here once there are sales in these days.",
        action = {
            // The top's dates by default; a picked day turns the button amber and brings a download of just that day (user, 2026-11-15).
            val periodText = com.saporini.mobile_desktop.core.components.periodLabel(v.from, v.to)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The card's Download saves what it shows: the picked day, or every hour of the top's dates.
                val dayHours = v.dayHours
                CardDownload(v, "sales-by-hour", 36.dp) {
                    val shown = if (day != null && dayHours != null) dayHours else v.hours
                    hoursReport(shown, day?.let { weekdayDate(it) }, day?.toString())
                }
                PanelDayButton(day, today, periodText, onDay)
            }
        }) {
        if (day != null) DifferentDatesNote("Only ${weekdayDate(day)}, not ${com.saporini.mobile_desktop.core.components.periodLabel(v.from, v.to)}")
        if (first < 0) return@StatsPanel
        val shown = hours.subList(first, last + 1)
        // No comparison ticks here (user, 2026-11-14: "the graph on top has the comparison, no need here").
        val peak = shown.maxBy { it.sales }
        val busiest = "Busiest at ${hourLabel(peak.hour)}:00 – ${hourLabel((peak.hour + 1) % 24)}:00: ${v.money(peak.sales)} from ${peak.orders} orders"
        Box(Modifier.alpha(dim)) { PanelNote(busiest) }
        ColumnChart(shown.map { h ->
            ColumnDatum(hourLabel(h.hour), h.sales / 100f, "${v.money(h.sales)} · ${h.orders} orders")
        }, roomLeft().alpha(dim), height = 220.dp, axisFormat = { v.axisMoney(it) }, peakLabel = { v.axisMoney(it) })
    }
}

/**
 * The period's average day by weekday, or one week's days: the panel has its own week calendar (user, 2026-11-14:
 * "select one week like Outlook"), and a week is compared with the week before. While a week loads, what was shown
 * stays, dimmed.
 */
@Composable
private fun WeekdaysPanel(v: StatsView, today: LocalDate, onWeek: (LocalDate?) -> Unit, modifier: Modifier, note: Boolean = true) {
    val week = v.week
    val weekDays = v.weekDays
    val dim = if (v.weekLoading) 0.45f else 1f
    val periodText = com.saporini.mobile_desktop.core.components.periodLabel(v.from, v.to)
    // The top's dates by default ("1 – 14 Nov · average"); a picked week turns the button amber and brings its download (user, 2026-11-15).
    val action: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardDownload(v, "sales-by-weekday", 36.dp) {
                if (week != null && weekDays != null) weekReport(weekDays, com.saporini.mobile_desktop.core.components.periodLabel(week, week.plus(DatePeriod(days = 6))),
                    "$week-to-${week.plus(DatePeriod(days = 6))}")
                else weekdayAverages(v, today).let { list ->
                    CardReport("sales-by-weekday", "Sales by weekday",
                        listOf(ReportColumn("Weekday"), ReportColumn("Average open day", ColumnKind.MONEY), ReportColumn("Open days", ColumnKind.COUNT),
                            ReportColumn("Closed days left out", ColumnKind.COUNT), ReportColumn("Sales in all", ColumnKind.MONEY), ReportColumn("Orders in all", ColumnKind.COUNT)),
                        list.map { d -> listOf(weekdayName(d.day), d.average, d.open.toLong(), d.closed.toLong(), d.sales, d.orders) },
                        listOf(ReportTotal("Total sales", list.sumOf { it.sales }), ReportTotal("Orders", list.sumOf { it.orders }, money = false)),
                        note = "Each weekday's average is its sales on the days the restaurant was open, divided by those days; days without sales count as closed and are left out.")
                }
            }
            PanelWeekButton(week, today, "$periodText · average", onWeek)
        }
    }
    if (week != null && weekDays != null) {
        val range = com.saporini.mobile_desktop.core.components.periodLabel(week, week.plus(DatePeriod(days = 6)))
        StatsPanel(Icons.Outlined.CalendarViewWeek, "Sales by weekday", modifier, empty = weekDays.all { it.sales == 0L } && !v.weekLoading,
            emptyText = "No sales in $range.", action = action) {
            DifferentDatesNote("Only $range, not $periodText")
            val compare = v.weekCompareDays
            val best = weekDays.filter { it.sales > 0 }.maxByOrNull { it.sales }
            Box(Modifier.alpha(dim)) {
                PanelNote(best?.let { "${weekdayName(it.date.dayOfWeek)} was the best day: ${v.money(it.sales)} from ${it.orders} orders" } ?: "No sales yet this week")
            }
            ColumnChart(weekDays.mapIndexed { i, d ->
                ColumnDatum("${weekdayName(d.date.dayOfWeek).take(3)} ${d.date.day}", d.sales / 100f,
                    if (d.date > today) "${weekdayDate(d.date)} · still to come" else "${weekdayDate(d.date)} · ${v.money(d.sales)} · ${d.orders} orders",
                    compare?.getOrNull(i)?.sales?.let { it / 100f })
            }, roomLeft().alpha(dim), height = 220.dp, color = StatsSeries[0], axisFormat = { v.axisMoney(it) }, peakLabel = { v.axisMoney(it) },
                compareName = v.weekCompareStart?.let { com.saporini.mobile_desktop.core.components.periodLabel(it, it.plus(DatePeriod(days = 6))) }
                    .takeIf { compare != null })
        }
        return
    }
    val days = weekdayAverages(v, today)
    StatsPanel(Icons.Outlined.CalendarViewWeek, "Sales by weekday", modifier, empty = days.all { it.average == 0L } && !v.weekLoading,
        // "average per day" beside the title, left out where the panel is a third of the row (several branches) and it would be cut.
        note = if (note) "average per day" else null, action = action) {
        if (days.all { it.average == 0L }) return@StatsPanel
        val best = days.maxBy { it.average }
        val closedAny = days.any { it.closed > 0 }
        Box(Modifier.alpha(dim)) {
            // Whole sentences, so each one has its translation.
            PanelNote(if (closedAny) "${weekdayPlural(best.day)} are the strongest: ${v.money(best.average)} on an average open day (closed days left out)"
                else "${weekdayPlural(best.day)} are the strongest: ${v.money(best.average)} on an average day")
        }
        ColumnChart(days.map { d -> ColumnDatum(weekdayName(d.day).take(3), d.average / 100f, weekdayTip(v, d)) }, roomLeft().alpha(dim), height = 220.dp,
            color = StatsSeries[0], axisFormat = { v.axisMoney(it) }, peakLabel = { v.axisMoney(it) })
    }
}

/**
 * One weekday over the period: its average open day (user, 2026-11-15: closed days left out, said on the card), how many open
 * days that's from, how many closed ones were left out, and everything it sold.
 */
private class WeekdayAverage(val day: kotlinx.datetime.DayOfWeek, val average: Long, val open: Int, val closed: Int, val sales: Long, val orders: Long)

/**
 * Each weekday's average open day, worked out here from the day figures (the server's average counts every day). Closed days
 * come from the branches' opening hours; while none are set, a day with no sales stands in for a closed one. Today doesn't
 * count while it's still going.
 */
private fun weekdayAverages(v: StatsView, today: LocalDate): List<WeekdayAverage> = kotlinx.datetime.DayOfWeek.entries.map { weekday ->
    val all = v.days.filter { it.date.dayOfWeek == weekday && it.date != today }
    val open = all.filterNot { v.closedDay(it.date, it.sales, it.sales == 0L) }
    WeekdayAverage(weekday, if (open.isEmpty()) 0L else open.sumOf { it.sales } / open.size, open.size, all.size - open.size,
        v.days.filter { it.date.dayOfWeek == weekday }.sumOf { it.sales }, v.days.filter { it.date.dayOfWeek == weekday }.sumOf { it.orders })
}

/** "€8,252.04 on an average Friday · from 2 Fridays (only 2 in these dates) · 1 closed day left out". */
private fun weekdayTip(v: StatsView, d: WeekdayAverage): String {
    val name = weekdayName(d.day)
    val plural = weekdayPlural(d.day)
    if (d.open == 0) return if (d.closed > 0) "Closed every $name in these dates" else "No $plural in these dates"
    return buildList {
        add("${v.money(d.average)} on an average $name")
        add(when (d.open) { 1 -> "from 1 $name (few, read with care)"; 2 -> "from 2 $plural (few, read with care)"; else -> "from ${d.open} $plural" })
        if (d.closed > 0) add(if (d.closed == 1) "1 closed day left out" else "${d.closed} closed days left out")
    }.joinToString(" · ")
}

/** The menu's colours: none of them green, so the sections don't look like the sales charts (user, 2026-11-14). */
private val SectionColors = listOf(
    StatsSeries[1], StatsSeries[2], StatsSeries[3], StatsSeries[4], StatsSeries[5],
    Color(0xFFC2412D), Color(0xFF3B4A8C), Color(0xFF8C5A3C), Color(0xFF4A9FD8), Color(0xFFE08AB0), Color(0xFF6B7C93), Color(0xFF8E3B76)
)

// Five and a half section lines (45 dp each, 8 dp apart): with more than five the list scrolls and the half line shows
// there's more (user, 2026-11-14).
private val SectionRowsHeight = 288.dp

/**
 * What each menu section sold: a ring split by section (its own colour, never green) with the total inside, and a line
 * per section with how many were sold and its share. Every section shows; past five the lines scroll.
 */
@Composable
private fun SectionsPanel(v: StatsView, modifier: Modifier) {
    val sections = v.sections.filter { it.sales > 0 }.sortedByDescending { it.sales }
    StatsPanel(Icons.AutoMirrored.Outlined.MenuBook, "Sales by menu section", modifier, empty = sections.isEmpty(),
        action = {
            CardDownload(v, "sales-by-menu-section") {
                val total = sections.sumOf { it.sales }
                CardReport("sales-by-menu-section", "Sales by menu section",
                    listOf(ReportColumn("Menu section"), ReportColumn("Sold", ColumnKind.COUNT), ReportColumn("Sales", ColumnKind.MONEY), ReportColumn("Share", ColumnKind.SHARE)),
                    sections.map { listOf(it.key, it.count, it.sales, csvShare(it.sales, total)) },
                    listOf(ReportTotal("Total sales", total), ReportTotal("Dishes sold", sections.sumOf { it.count }, money = false)))
            }
        },
        emptyText = "Each menu section's sales show here once dishes are sold.") {
        val total = sections.sumOf { it.sales }.coerceAtLeast(1)
        fun share(sales: Long) = "${(sales * 100 + total / 2) / total}%"
        val parts = sections.mapIndexed { i, s ->
            Part(s.key, s.key, s.sales.toFloat(), SectionColors[i % SectionColors.size], v.money(s.sales), "${countText(s.count)} sold · ${share(s.sales)}")
        }
        val top = sections.first()
        PanelNote("${top.key} brings in the most: ${share(top.sales)} of the menu's sales")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Donut(parts, v.short(sections.sumOf { it.sales }), "sold", size = 124.dp)
            if (parts.size > 5) {
                // A fixed height, so the row beside it never asks the list how tall it is.
                ScrollColumn(Modifier.weight(1f).height(SectionRowsHeight), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    parts.forEach { KeyRow(it.color, it.label, it.display, it.detail) }
                }
            } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEach { KeyRow(it.color, it.label, it.display, it.detail) }
            }
        }
    }
}

internal fun weekdayName(day: kotlinx.datetime.DayOfWeek): String = day.name.lowercase().replaceFirstChar { it.uppercase() }

/** "Mondays"…, written out so they have their translations. */
internal fun weekdayPlural(day: kotlinx.datetime.DayOfWeek): String = when (day) {
    kotlinx.datetime.DayOfWeek.MONDAY -> "Mondays"
    kotlinx.datetime.DayOfWeek.TUESDAY -> "Tuesdays"
    kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Wednesdays"
    kotlinx.datetime.DayOfWeek.THURSDAY -> "Thursdays"
    kotlinx.datetime.DayOfWeek.FRIDAY -> "Fridays"
    kotlinx.datetime.DayOfWeek.SATURDAY -> "Saturdays"
    else -> "Sundays"
}

/** "November". */
private fun monthName(date: LocalDate): String = date.month.name.lowercase().replaceFirstChar { it.uppercase() }


/** Sales hour by hour (the top's dates, or one day: [dates], [fileDates]), for the card's download. */
private fun hoursReport(hours: List<com.saporini.mobile_desktop.statistics.HourPoint>, dates: String?, fileDates: String?): CardReport {
    val shown = hours.filter { it.sales > 0 || it.orders > 0 }
    return CardReport("sales-by-hour", "Sales by hour",
        listOf(ReportColumn("Hour"), ReportColumn("Sales", ColumnKind.MONEY), ReportColumn("Orders", ColumnKind.COUNT)),
        shown.map { h -> listOf("${hourLabel(h.hour)}:00 – ${hourLabel((h.hour + 1) % 24)}:00", h.sales, h.orders) },
        listOf(ReportTotal("Total sales", shown.sumOf { it.sales }), ReportTotal("Orders", shown.sumOf { it.orders }, money = false)),
        dates = dates, fileDates = fileDates)
}

/** One week's days, for the card's download. */
private fun weekReport(days: List<DayPoint>, dates: String, fileDates: String): CardReport = CardReport("sales-by-weekday", "Sales by weekday",
    listOf(ReportColumn("Date"), ReportColumn("Day"), ReportColumn("Sales", ColumnKind.MONEY), ReportColumn("Orders", ColumnKind.COUNT)),
    days.map { d -> listOf(com.saporini.mobile_desktop.core.format.shortDate(d.date, withYear = true), weekdayName(d.date.dayOfWeek), d.sales, d.orders) },
    listOf(ReportTotal("Total sales", days.sumOf { it.sales }), ReportTotal("Orders", days.sumOf { it.orders }, money = false)),
    dates = dates, fileDates = fileDates)

/** Graph | Calendar, two small segments with an icon each (the picked one green). */
@Composable
private fun GraphCalendarToggle(calendar: Boolean, onChange: (Boolean) -> Unit, compact: Boolean) {
    Row(Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).background(Color.White).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(false to "Graph", true to "Calendar").forEach { (isCalendar, label) ->
            val on = calendar == isCalendar
            Row(Modifier.fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(if (on) Kit.Green.copy(alpha = 0.12f) else Color.Transparent)
                .clickable { onChange(isCalendar) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(if (isCalendar) Icons.Outlined.CalendarMonth else Icons.AutoMirrored.Outlined.ShowChart, label, Modifier.size(15.dp),
                    tint = if (on) Kit.Green else Kit.Muted)
                if (!compact) Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.sp,
                    color = if (on) Kit.Green else Kit.Muted, maxLines = 1)
            }
        }
    }
}
