package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.saporini.mobile_desktop.core.components.isoWeekNumber
import com.saporini.mobile_desktop.core.files.DocAlign
import com.saporini.mobile_desktop.core.files.DocItem
import com.saporini.mobile_desktop.core.files.DocLine
import com.saporini.mobile_desktop.core.files.DocMuted
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.statistics.DayPoint
import com.saporini.mobile_desktop.statistics.StatisticsState
import com.saporini.mobile_desktop.statistics.StatsOrder
import com.saporini.mobile_desktop.statistics.StatsView
import com.saporini.mobile_desktop.statistics.centsOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// Sales details → Download all (user, 2026-11-15: "downloading all means even the content inside, and it shows how many pages
// there are; make the pages look good, more like reports"): one report made of parts, like the window. First a summary (the
// four figures, the month as a calendar, every day in a table), then a part per day with sales: its orders (order numbers
// whole) and, at the end, what the day adds up to. Each part is an item of the Download all window, so its pages can be
// left out one by one.

private const val DetailsNote = "Sales are the bills after discounts, VAT included. Orders are listed by when they were opened, " +
    "so one paid after midnight can sit on the day before. Made by Saporini POS."

/** Every part of the report, ready for the Download all window ([orders] null = the person can't see orders: the summary only). */
internal fun detailsItems(v: StatsView, state: StatisticsState, orders: List<StatsOrder>?, info: ReportInfo, today: LocalDate,
                          zone: TimeZone): List<PageExportItem> {
    val tags = tagsOf(v, today)
    val earlier = earlierDays(v, state)
    val names = state.staff?.staff.orEmpty().associate { it.staffId to it.name }
    val byDay = orders?.groupBy { localOf(it.openedAt, zone)?.date } ?: return listOf(summaryItem(v, info, tags, earlier, today))
    val days = v.days.filter { it.date <= today && (it.sales > 0 || byDay[it.date].orEmpty().isNotEmpty()) }
    return listOf(summaryItem(v, info, tags, earlier, today)) + days.mapIndexed { i, d ->
        dayItem(v, info, d, i, days.size, tags[d.date], byDay[d.date].orEmpty(), names, zone)
    }
}

private fun changeTone(v: StatsView, now: Long, before: Long?): Pair<String?, Int> {
    val text = v.change(now, before)
    return text to when {
        text == null -> 0
        text.startsWith("+") -> 1
        text.startsWith("-") -> -1
        else -> 0
    }
}

private fun figure(v: StatsView, label: String, value: String, now: Long, before: Long?, against: String?): DocFigure {
    if (against == null) return DocFigure(label, value)
    val (text, tone) = changeTone(v, now, before)
    return DocFigure(label, value, if (text == null) "nothing on $against" else "$text against $against", tone)
}

private fun weekBeforeText(v: StatsView, d: DayPoint, before: DayPoint?, today: LocalDate): String =
    if (d.date == today || d.sales == 0L || before == null) "–" else v.change(d.sales, before.sales) ?: "–"

// ---- The summary ----

private fun summaryItem(v: StatsView, info: ReportInfo, tags: Map<LocalDate, DayTag>, earlier: Map<LocalDate, DayPoint>, today: LocalDate): PageExportItem {
    val t = v.totals
    val c = v.compareTotals
    val flow = DocFlow(info, "Sales details", null, info.compare, "SUMMARY", DetailsNote)
    flow.section("In short")
    flow.figures(listOf(
        figure(v, "Total sales", v.money(t.sales), t.sales, c?.sales, info.compare),
        figure(v, "Orders", countText(t.orders), t.orders, c?.orders, info.compare),
        figure(v, "Guests", countText(t.guests), t.guests, c?.guests, info.compare),
        figure(v, "Average bill", v.money(t.averageBill), t.averageBill, c?.averageBill, info.compare)
    ))
    flow.gap(18f)
    val weeks = calendarWeeks(v, today)
    flow.section("The days at a glance", "darker = more sales", keep = 16f + weeks.size * 50f)
    flow.drawing(16f + weeks.size * 50f) { top -> drawCalendar(v, weeks, tags, earlier, today, info, top) }
    // The best and lowest days and the closed ones, in words.
    val best = tags.entries.firstOrNull { it.value == DayTag.BEST }?.key
    val lowest = tags.entries.firstOrNull { it.value == DayTag.LOWEST }?.key
    val closed = v.days.count { it.sales == 0L && it.date < today }
    val byDate = v.days.associateBy { it.date }
    val words = listOfNotNull(
        best?.let { "Best day: ${weekdayDate(it)}, ${v.money(byDate[it]?.sales ?: 0)}" },
        lowest?.let { "Lowest day: ${weekdayDate(it)}, ${v.money(byDate[it]?.sales ?: 0)}" },
        if (closed > 0) "Closed: $closed ${if (closed == 1) "day" else "days"}" else null
    )
    if (words.isNotEmpty()) {
        flow.gap(8f)
        flow.text(words.joinToString("   ·   "), 9f, DocMuted)
    }
    flow.gap(18f)
    val shown = v.days.filter { it.date <= today }
    val columns = listOf(ReportColumn("Day"), ReportColumn("Sales", ColumnKind.MONEY), ReportColumn("Orders", ColumnKind.COUNT),
        ReportColumn("Guests", ColumnKind.COUNT), ReportColumn("Average bill", ColumnKind.MONEY), ReportColumn("A week before", ColumnKind.SHARE),
        ReportColumn("Note"))
    val rows = shown.map { d ->
        listOf<Any>(weekdayDate(d.date), d.sales, d.orders, d.guests, if (d.orders > 0) d.sales / d.orders else 0L,
            weekBeforeText(v, d, earlier[d.date.minus(DatePeriod(days = 7))], today), tags[d.date]?.let { noteOf(it) } ?: "")
    }
    val totals = listOf(ReportTotal("Total sales", t.sales), ReportTotal("Orders", t.orders, money = false),
        ReportTotal("Guests", t.guests, money = false), ReportTotal("Average bill", t.averageBill))
    val weights = listOf(1.05f, 1.15f, 0.7f, 0.7f, 1.6f, 1.4f, 0.95f)
    flow.table("Day by day", columns, rows, totals, weights)
    val report = CardReport("00 Summary", "Summary · every day", columns, rows, totals)
    return PageExportItem(report.stem, report, null, flow.pages())
}

private fun noteOf(tag: DayTag): String = when (tag) {
    DayTag.BEST -> "Best day"
    DayTag.LOWEST -> "Lowest day"
    DayTag.CLOSED -> "Closed"
    DayTag.TODAY -> "So far"
}

private val CalGreen = Color(0xFF3F8F3A)
private val ClosedGrey = Color(0xFFF1F2F0)

/** The month as on screen, drawn for the page: a box a day tinted by its sales, and the week's total on the right. */
private fun MutableList<DocItem>.drawCalendar(v: StatsView, weeks: List<List<LocalDate>>, tags: Map<LocalDate, DayTag>, earlier: Map<LocalDate, DayPoint>,
                                          today: LocalDate, info: ReportInfo, top: Float) {
    val weekW = 70f
    val gap = 4f
    val dayW = (Right - Left - weekW - gap * 7) / 7
    val cellH = 46f
    val byDate = v.days.associateBy { it.date }
    val end = calendarEnd(v, today)
    val most = v.days.maxOfOrNull { it.sales }?.coerceAtLeast(1) ?: 1
    listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEachIndexed { i, name ->
        add(DocItem.Text(Left + i * (dayW + gap) + 3f, top + 8f, name, 6.5f, bold = true, color = DocMuted, spacing = 0.6f))
    }
    add(DocItem.Text(Left + 7 * (dayW + gap) + 3f, top + 8f, "WEEK", 6.5f, bold = true, color = DocMuted, spacing = 0.6f))
    weeks.forEachIndexed { r, week ->
        val y = top + 16f + r * (cellH + gap)
        week.forEachIndexed { i, date ->
            val x = Left + i * (dayW + gap)
            val inPeriod = date in v.from..end
            val point = byDate[date]
            val first = inPeriod && (date == v.from || date.day == 1)
            val dayText = if (first) "${date.day} ${shortDate(date).substringAfter(' ')}" else "${date.day}"
            when {
                !inPeriod -> add(DocItem.Text(x + 5f, y + 11f, "${date.day}", 7f, color = DocLine))
                date > today || point == null -> {
                    add(DocItem.Box(x, y, dayW, cellH, stroke = DocLine, radius = 5f))
                    add(DocItem.Text(x + 5f, y + 11f, dayText, 7f, color = DocMuted))
                }
                point.sales == 0L -> {
                    add(DocItem.Box(x, y, dayW, cellH, fill = ClosedGrey, radius = 5f))
                    add(DocItem.Text(x + 5f, y + 11f, dayText, 7f, bold = true, color = DocMuted))
                    add(DocItem.Text(x + 5f, y + cellH - 7f, "Closed", 6.5f, color = DocMuted))
                }
                else -> {
                    val heat = point.sales.toFloat() / most
                    val tag = tags[date]
                    val ring = when (tag) { DayTag.BEST -> CalGreen; DayTag.TODAY -> Color(0xFF2563EB); else -> null }
                    add(DocItem.Box(x, y, dayW, cellH, fill = lerp(Color.White, CalGreen, 0.07f + 0.38f * heat), stroke = ring,
                        strokeWidth = 1.3f, radius = 5f))
                    add(DocItem.Text(x + 5f, y + 11f, dayText, 7f, bold = true))
                    tag?.takeIf { it != DayTag.CLOSED }?.let {
                        add(DocItem.Text(x + dayW - 4f, y + 10.5f, when (it) { DayTag.BEST -> "BEST"; DayTag.LOWEST -> "LOWEST"; else -> "TODAY" }, 5.2f, bold = true,
                            color = if (it == DayTag.BEST) CalGreen else DocMuted, align = DocAlign.RIGHT, spacing = 0.4f))
                    }
                    add(DocItem.Text(x + 5f, y + 29f, fit(moneyText(point.sales, info.currency), 8.2f, true, dayW - 8f), 8.2f, bold = true))
                    add(DocItem.Text(x + 5f, y + 40f, fit("${countText(point.orders)} orders", 6.5f, false, dayW - 8f), 6.5f, color = DocMuted))
                }
            }
        }
        // The week's own days of the period.
        val shown = week.filter { it in v.from..v.to && it <= today }
        val x = Left + 7 * (dayW + gap)
        add(DocItem.Box(x, y, weekW, cellH, fill = PageCanvas, stroke = DocLine, radius = 5f))
        add(DocItem.Text(x + 6f, y + 11f, "Week ${isoWeekNumber(week.first())}", 6.8f, bold = true, color = PageGreen))
        if (shown.isEmpty()) add(DocItem.Text(x + 6f, y + 29f, "To come", 7f, color = DocMuted))
        else {
            add(DocItem.Text(x + 6f, y + 29f, fit(moneyText(shown.sumOf { byDate[it]?.sales ?: 0L }, info.currency), 8.2f, true, weekW - 10f), 8.2f, bold = true))
            add(DocItem.Text(x + 6f, y + 40f, "${countText(shown.sumOf { byDate[it]?.orders ?: 0L })} orders", 6.5f, color = DocMuted))
        }
    }
}

// ---- A day ----

/** A day's part: its orders from the first to the last, then what the day adds up to (like the window's day card). */
private fun dayItem(v: StatsView, info: ReportInfo, d: DayPoint, index: Int, count: Int, tag: DayTag?, orders: List<StatsOrder>,
                    names: Map<String, String>, zone: TimeZone): PageExportItem {
    val title = "${weekdayName(d.date.dayOfWeek)} ${d.date.day} ${monthWord(d.date)}"
    val flow = DocFlow(info, title, shortDate(d.date, withYear = true), null, "DAY ${index + 1} OF $count", DetailsNote)
    val columns = listOf(ReportColumn("Opened"), ReportColumn("Order"), ReportColumn("Table"), ReportColumn("Taken by"),
        ReportColumn("Guests", ColumnKind.COUNT), ReportColumn("Dishes", ColumnKind.COUNT), ReportColumn("Total", ColumnKind.MONEY), ReportColumn("How it ended"))
    val rows = orders.map { o ->
        listOf<Any>(timeText(o.openedAt, zone), o.number, o.table ?: orderTypeLabel(o.type), o.staffId?.let(names::get) ?: "–", (o.guests ?: 0).toLong(),
            (o.items ?: 0).toLong(), o.total, statusOf(o)?.first ?: "Paid")
    }
    val s = DayTotals(orders)
    val ended = orders.count { it.status != "CLOSED" }
    // The order number gets the most room, so it always shows whole.
    flow.table("Orders", columns, rows, weights = listOf(44f, 108f, 56f, 78f, 42f, 42f, 62f, 67f),
        extra = listOfNotNull("${countText(s.count)} ${if (s.count == 1L) "order" else "orders"}", if (ended > 0) "$ended cancelled or voided" else null,
            tag?.takeIf { it == DayTag.BEST || it == DayTag.LOWEST || it == DayTag.TODAY }?.let { if (it == DayTag.TODAY) "so far" else if (it == DayTag.BEST) "best day" else "lowest day" })
            .joinToString(" · "),
        empty = "No orders were opened this day.")
    flow.totalsBox("The day in total", listOfNotNull(
        "Orders" to countText(s.count),
        "Guests" to countText(s.guests),
        "Dishes" to countText(s.dishes),
        "Average bill" to moneyText(s.average, info.currency),
        ("Cancelled or voided" to countText(s.cancelled)).takeIf { s.cancelled > 0 },
        ("Unpaid" to moneyText(s.unpaid, info.currency)).takeIf { s.unpaid > 0 }
    ), "Total paid" to moneyText(s.paid, info.currency))
    // The day's CSV: its orders.
    val report = CardReport("${d.date} ${weekdayName(d.date.dayOfWeek)}", title, columns, rows,
        listOf(ReportTotal("Total paid", s.paid), ReportTotal("Orders", s.count, money = false), ReportTotal("Guests", s.guests, money = false)),
        dates = shortDate(d.date, withYear = true), fileDates = "${d.date}")
    return PageExportItem(report.stem, report, null, flow.pages())
}
