package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Icon
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.components.TrendBadge
import com.saporini.mobile_desktop.core.components.isoWeekNumber
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.payment.data.ReceiptDto
import com.saporini.mobile_desktop.statistics.CompareMode
import com.saporini.mobile_desktop.statistics.DayPoint
import com.saporini.mobile_desktop.statistics.StatisticsScreenModel
import com.saporini.mobile_desktop.statistics.StatisticsState
import com.saporini.mobile_desktop.statistics.StatsFilters
import com.saporini.mobile_desktop.statistics.StatsOrder
import com.saporini.mobile_desktop.statistics.StatsPeriod
import com.saporini.mobile_desktop.statistics.StatsView
import com.saporini.mobile_desktop.statistics.centsOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus


// Sales' days as a month calendar: on the Day by day card (its Calendar view) and in the downloaded report's summary.

// ---- Sales details: the calendar ----

/** A day's place in the period: its best, its lowest (with sales, not today), closed (no sales), or today so far. */
internal enum class DayTag(val label: String, val color: Color) {
    BEST("Best", Color(0xFF15803D)), LOWEST("Lowest", Kit.Grey), CLOSED("Closed", Kit.Faint), TODAY("So far", Kit.Blue)
}

internal fun tagsOf(v: StatsView, today: LocalDate): Map<LocalDate, DayTag> {
    val withSales = v.days.filter { it.sales > 0 }
    val best = withSales.maxByOrNull { it.sales }?.date
    val lowest = withSales.filter { it.date != today }.takeIf { it.size > 1 }?.minByOrNull { it.sales }?.date
    return v.days.mapNotNull { d ->
        when {
            d.date == today -> d.date to DayTag.TODAY
            d.date == best -> d.date to DayTag.BEST
            d.date == lowest -> d.date to DayTag.LOWEST
            d.sales == 0L -> d.date to DayTag.CLOSED
            else -> null
        }
    }.toMap()
}

/** Each day's figures a week before: the period's own days, and the week before it for the first ones. */
internal fun earlierDays(v: StatsView, state: StatisticsState): Map<LocalDate, DayPoint> =
    state.daysBefore?.salesByDay.orEmpty().mapNotNull { d ->
        runCatching { LocalDate.parse(d.date) }.getOrNull()?.let { it to DayPoint(it, d.sales.centsOf(), d.orders, d.guests) }
    }.toMap() + v.days.associateBy { it.date }

/** The calendar's last day: the period's, or the month's end while this month runs (its days to come show empty). */
internal fun calendarEnd(v: StatsView, today: LocalDate): LocalDate {
    val monthRunning = v.from.day == 1 && v.to == today && v.from.month == v.to.month && v.from.year == v.to.year
    return if (monthRunning) LocalDate(v.to.year, v.to.month, 1).plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)) else v.to
}

/** The calendar's weeks, from the Monday of the period's first day to the Sunday of its last (this month: to its end). */
internal fun calendarWeeks(v: StatsView, today: LocalDate): List<List<LocalDate>> {
    val last = calendarEnd(v, today)
    val start = v.from.minus(DatePeriod(days = v.from.dayOfWeek.isoDayNumber - 1))
    val end = last.plus(DatePeriod(days = 7 - last.dayOfWeek.isoDayNumber))
    return generateSequence(start) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= end }.toList().chunked(7)
}

private val WeekdayHeads = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * The period's days as a month calendar (user, 2026-11-15: "I like the calendar, make the graph a toggle that can show it";
 * then "it's so big, make it about the graph's size"): a key, then each day tinted by how much it sold (darker = more), with
 * its sales and the change against the same weekday a week before (or its Best / Lowest / So far mark); best ringed green,
 * today blue, closed grey; a column of week totals on the right. The rows share about the graph's height ([height]); on a
 * phone the days are small (number and "€3.1k"). A click on a day opens it in Sales details.
 */
@Composable
internal fun MonthCalendar(v: StatsView, today: LocalDate, narrow: Boolean, onDay: ((LocalDate) -> Unit)?, modifier: Modifier = Modifier,
                           height: Dp = 300.dp) {
    val tags = remember(v) { tagsOf(v, today) }
    val earlier = remember(v, v.state.daysBefore) { earlierDays(v, v.state) }
    val byDate = remember(v) { v.days.associateBy { it.date } }
    val weeks = remember(v, today) { calendarWeeks(v, today) }
    val end = calendarEnd(v, today)
    val top = v.days.maxOfOrNull { it.sales }?.coerceAtLeast(1) ?: 1
    fun changeOf(date: LocalDate, sales: Long): String? {
        val before = earlier[date.minus(DatePeriod(days = 7))]?.sales
        return if (date == today || sales == 0L || before == null) null else v.change(sales, before)
    }
    val gap = if (narrow) 4.dp else 6.dp
    // The weeks share what's left of the graph's height under the key and the weekday names.
    val rows = weeks.size.coerceAtLeast(1)
    val cellHeight = ((height - 40.dp - gap * (rows - 1)) / rows).coerceIn(if (narrow) 38.dp else 46.dp, if (narrow) 52.dp else 72.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        CalendarKey(narrow, onDay != null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            WeekdayHeads.forEach { name ->
                Text((if (narrow) name.take(1) else name).uppercase(), Modifier.weight(1f).padding(start = if (narrow) 0.dp else 4.dp), fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.5.sp, color = Kit.Faint, maxLines = 1,
                    textAlign = if (narrow) TextAlign.Center else TextAlign.Start)
            }
            if (!narrow) Text("WEEK", Modifier.width(WeekColumn).padding(start = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                letterSpacing = 0.5.sp, color = Kit.Faint, maxLines = 1)
        }
        weeks.forEach { week ->
            // The week's own days of the period, and the same days a week before (when all of them are known).
            val shown = week.filter { it in v.from..v.to && it <= today }
            val weekSales = shown.sumOf { byDate[it]?.sales ?: 0L }
            val beforeDays = shown.filter { it != today }.map { earlier[it.minus(DatePeriod(days = 7))] }
            val weekChange = if (shown.isEmpty() || beforeDays.isEmpty() || beforeDays.any { it == null }) null
                else v.change(shown.filter { it != today }.sumOf { byDate[it]?.sales ?: 0L }, beforeDays.sumOf { it!!.sales })
            val number = isoWeekNumber(week.first())
            Row(Modifier.fillMaxWidth().height(cellHeight), horizontalArrangement = Arrangement.spacedBy(gap)) {
                week.forEach { date ->
                    val point = byDate[date]
                    val inPeriod = date in v.from..end
                    DayCell(v, date, point, inPeriod = inPeriod, future = date > today, tag = tags[date],
                        heat = ((point?.sales ?: 0L).toFloat() / top).coerceIn(0f, 1f), change = point?.let { changeOf(date, it.sales) },
                        first = inPeriod && (date == v.from || date.day == 1), narrow = narrow, onClick = onDay?.let { open -> { open(date) } })
                }
                if (!narrow) Column(Modifier.width(WeekColumn).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas)
                    .padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Week $number", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Kit.Green, maxLines = 1)
                        if (shown.isNotEmpty()) TrendBadge(weekChange)
                    }
                    if (shown.isEmpty()) Text("To come", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint, maxLines = 1)
                    else Text(v.money(weekSales), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink,
                        maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

private val WeekColumn = 132.dp

/** How to read the calendar: the tint from less to more, and the marks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarKey(narrow: Boolean, clickable: Boolean) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Less", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { h ->
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(dayTint(h)))
            }
            Text(if (narrow) "More" else "More sales", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        }
        KeyMark("Best day", DayTag.BEST.color)
        KeyMark("Today", Kit.Blue)
        if (!narrow) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(ClosedFill))
            Text("Closed", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        }
        if (!narrow && clickable) Text("Click a day to see its orders", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint)
    }
}

@Composable
private fun KeyMark(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).border(1.5.dp, color, RoundedCornerShape(3.dp)))
        Text(text, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
    }
}

private val ClosedFill = Color(0xFFF1F2F0)

/** A day's tint by how much it sold: a pale green for little, a stronger one for the most. */
private fun dayTint(heat: Float): Color = StatsAccent.copy(alpha = 0.06f + 0.40f * heat)

/** One day of the calendar, as high as its row: its number and mark (or change) on top, its sales under them. */
@Composable
private fun RowScope.DayCell(v: StatsView, date: LocalDate, point: DayPoint?, inPeriod: Boolean, future: Boolean, tag: DayTag?, heat: Float,
                             change: String?, first: Boolean, narrow: Boolean, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(if (narrow) 6.dp else 10.dp)
    val dayText = if (first && !narrow) "${date.day} ${monthWord(date).take(3)}" else "${date.day}"
    val padding = if (narrow) Modifier.padding(horizontal = 5.dp, vertical = 5.dp) else Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
    val numberSize = if (narrow) 10.5.sp else 12.sp
    // Days around the period: only their number, faint.
    if (!inPeriod) {
        Box(Modifier.weight(1f).fillMaxHeight().then(padding)) {
            Text(dayText, fontFamily = Inter(), fontSize = numberSize, color = Kit.Faint.copy(alpha = 0.6f))
        }
        return
    }
    if (future || point == null) {
        Box(Modifier.weight(1f).fillMaxHeight().clip(shape).border(1.dp, Kit.Border.copy(alpha = 0.7f), shape).then(padding)) {
            Text(dayText, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = numberSize, color = Kit.Faint)
        }
        return
    }
    val closed = point.sales == 0L
    val ring = when (tag) {
        DayTag.BEST -> DayTag.BEST.color
        DayTag.TODAY -> Kit.Blue
        DayTag.LOWEST -> if (narrow) Kit.Grey else null
        else -> null
    }
    Column(Modifier.weight(1f).fillMaxHeight().clip(shape).background(if (closed) ClosedFill else dayTint(heat))
        .then(if (ring != null) Modifier.border(1.5.dp, ring, shape) else Modifier)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else Modifier).then(padding),
        verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dayText, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = numberSize,
                color = if (closed) Kit.Muted else Kit.Ink, maxLines = 1, softWrap = false)
            // Its mark (best, lowest, so far), else how it did against a week before.
            if (!narrow && !closed) {
                if (tag != null && tag != DayTag.CLOSED) TagPill(tag.label, tag.color) else TrendBadge(change)
            }
        }
        when {
            closed -> Text(if (narrow) "–" else "Closed", fontFamily = Inter(), fontSize = if (narrow) 9.5.sp else 11.sp, color = Kit.Faint, maxLines = 1)
            narrow -> Text(v.axisMoney(point.sales / 100f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.sp,
                color = Kit.Ink, maxLines = 1, softWrap = false)
            else -> Text(v.money(point.sales), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink,
                maxLines = 1, softWrap = false)
        }
    }
}

/** Four figures in soft boxes, each with its change and [caption] (what it's compared with). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FigureTiles(figures: List<Triple<String, String, String?>>, caption: (String?) -> String?) {
    val narrow = LocalNarrowDialog.current
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = if (narrow) 2 else 4) {
        figures.forEach { (label, value, change) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1)
                Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1, softWrap = false)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrendBadge(change)
                    // On a phone the window's subtitle already says what it's compared with.
                    if (!narrow) caption(change)?.let {
                        Text(it, fontFamily = Inter(), fontSize = 10.5.sp, color = Kit.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TagPill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.13f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.sp, color = color, maxLines = 1)
    }
}

