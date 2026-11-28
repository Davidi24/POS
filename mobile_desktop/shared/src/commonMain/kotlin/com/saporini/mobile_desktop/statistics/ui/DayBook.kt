package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.statistics.StatsDetails
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


// Statistics → Sales, from a total down to what made it (user, 2026-11-15: "every day as its own card with its orders inside,
// each order expands with its details; that's how the money came in through orders; each day has at the end its details:
// guests, orders, total money"): Sales details is one page (only for a month or less) listing the period's days as cards.
// A day's card has its sales against a week before, its orders (the first ones, then "Show all"), each opening its bill
// under its line, and its totals at the end. Download all saves the whole of it as a report. Read-only.

/** The "Details" button on the by-day card, beside its download (user, 2026-11-15: "not Every day, a better name"). */
@Composable
internal fun DetailsButton(onClick: () -> Unit) {
    Row(Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).background(Color.White)
        .clickable(onClick = onClick).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, Modifier.size(16.dp), tint = Kit.Green)
        Text("Details", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
    }
}

/** How many orders a day's card shows before "Show all". */
private const val FirstOrders = 8

/** The Sales details window: one page of day cards, Download all in its buttons. */
@Composable
internal fun DayBookDialog(v: StatsView, state: StatisticsState, model: StatisticsScreenModel, today: LocalDate, info: ReportInfo,
                           onSaved: (String) -> Unit) {
    if (!state.everyDay) return
    val details = state.details ?: return
    val zone = remember(state.overview) { zoneOf(state) }
    val branch = state.branchId?.let { id -> state.branches.firstOrNull { it.id == id }?.name }
    var exporting by remember { mutableStateOf(false) }
    AppDialog("Sales details", onDismiss = model::closeEveryDay, maxWidth = 980.dp, height = 860.dp,
        subtitle = listOfNotNull(periodLine(v), branch).joinToString(" · "),
        buttons = {
            Spacer(Modifier.weight(1f))
            // Download all (user, 2026-11-15): the whole page as a report; every page shows first, with how many there are.
            if (LocalStatsDownloads.current) KitButton(if (details.ordersLoading) "Loading the orders…" else "Download all", { exporting = true }, icon = Icons.Outlined.Download,
                loading = details.ordersLoading, enabled = v.days.any { it.sales > 0 })
        }) {
        DetailsContent(v, state, details, today, zone, model)
    }
    if (exporting) {
        val detailsInfo = remember(info) { ReportInfo(info.restaurant, info.branch, "Sales details", info.period, info.compare, info.madeOn, info.currency, info.fileDates) }
        val items = remember(details.orders, v) { detailsItems(v, state, details.orders, detailsInfo, today, zone) }
        PageExportDialog(items, detailsInfo, onClose = { exporting = false }, onResult = onSaved, onePdf = "Sales details ${v.from} to ${v.to}",
            pdfHint = "The summary, then every day with its orders and its totals")
    }
}

private fun zoneOf(state: StatisticsState): TimeZone =
    state.overview?.period?.timezone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()

/** The window's content without the window, for the look checks (a dialog doesn't show in a page render). */
@Composable
internal fun DayBookPreview(v: StatsView, state: StatisticsState, model: StatisticsScreenModel, today: LocalDate, narrow: Boolean = false) {
    val details = state.details ?: return
    androidx.compose.runtime.CompositionLocalProvider(LocalNarrowDialog provides narrow) {
        Column(Modifier.fillMaxWidth().background(Color.White).padding(if (narrow) 16.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Sales details", fontFamily = Inter(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Kit.Ink)
                Text(periodLine(v), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            }
            DetailsContent(v, state, details, today, TimeZone.UTC, model)
        }
    }
}

/** The period's four figures, then a card per day from the first to today. */
@Composable
private fun ColumnScope.DetailsContent(v: StatsView, state: StatisticsState, details: StatsDetails, today: LocalDate, zone: TimeZone,
                                       model: StatisticsScreenModel) {
    val t = v.totals
    val c = v.compareTotals
    val against = compareDates(state.compare, v.from, v.to)
    FigureTiles(listOf(
        Triple("Sales", v.money(t.sales), v.change(t.sales, c?.sales)),
        Triple("Orders", countText(t.orders), v.change(t.orders, c?.orders)),
        Triple("Guests", countText(t.guests), v.change(t.guests, c?.guests)),
        Triple("Average bill", v.money(t.averageBill), v.change(t.averageBill, c?.averageBill))
    )) { change -> if (against.isEmpty()) null else if (change == null) "nothing on $against" else "against $against" }
    when {
        !state.canSeeOrders -> Quiet("Your role can't see orders, so each day shows its figures only.")
        details.ordersProblem != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(details.ordersProblem, Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Danger)
            KitButton("Try again", model::retryDetails, style = ButtonStyle.SECONDARY)
        }
    }
    val tags = remember(v) { tagsOf(v, today) }
    val earlier = remember(v, state.daysBefore) { earlierDays(v, state) }
    val names = remember(state.staff) { state.staff?.staff.orEmpty().associate { it.staffId to it.name } }
    val byDay = remember(details.orders, zone) { details.orders?.groupBy { localOf(it.openedAt, zone)?.date } }
    v.days.filter { it.date <= today }.forEach { d ->
        DayCard(v, d, tags[d.date], earlier[d.date.minus(DatePeriod(days = 7))], today, details, byDay?.get(d.date).orEmpty(), names, zone, model)
    }
}

/** What a day's orders add up to: the totals at the end of its card (and of its part of the report). */
internal class DayTotals(orders: List<StatsOrder>) {
    private val paidOrders = orders.filter { it.status == "CLOSED" }
    private val kept = orders.filter { it.status != "CANCELLED" }
    val count = orders.size.toLong()
    val guests = kept.sumOf { (it.guests ?: 0).toLong() }
    val dishes = kept.sumOf { (it.items ?: 0).toLong() }
    val paid = paidOrders.sumOf { it.total }
    val average = if (paidOrders.isNotEmpty()) paid / paidOrders.size else 0L
    val cancelled = orders.count { !it.unpaid && it.status != "CLOSED" }.toLong()
    val unpaid = orders.filter { it.unpaid }.sumOf { it.total }
}

/**
 * One day: a date badge, its name and tag, its sales with the change against the same weekday a week before; its orders
 * (the first [FirstOrders], then "Show all"); and its totals in a strip at the end. A closed day is one slim line.
 */
@Composable
private fun DayCard(v: StatsView, d: DayPoint, tag: DayTag?, before: DayPoint?, today: LocalDate, details: StatsDetails, orders: List<StatsOrder>,
                    names: Map<String, String>, zone: TimeZone, model: StatisticsScreenModel) {
    val narrow = LocalNarrowDialog.current
    val focused = details.focus == d.date
    val requester = remember { BringIntoViewRequester() }
    // A day clicked on the graph, the calendar or a day card of the page comes to the top, with a green frame for a moment.
    var flash by remember { mutableStateOf(false) }
    if (focused) androidx.compose.runtime.LaunchedEffect(details.ordersLoading) {
        if (!details.ordersLoading) {
            kotlinx.coroutines.delay(80)
            requester.bringIntoView(androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 100_000f))
            flash = true
            kotlinx.coroutines.delay(2200)
            flash = false
        }
    }
    val frame by androidx.compose.animation.animateColorAsState(if (flash) Kit.Green else Kit.Border, tween(400))
    val shape = RoundedCornerShape(14.dp)
    val closed = orders.isEmpty() && v.closedDay(d.date, d.sales, d.sales == 0L)
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(requester).clip(shape).background(Color.White)
        .border(if (flash) 2.dp else 1.dp, frame, shape)) {
        // The day: its badge, its name and how it went.
        Row(Modifier.fillMaxWidth().padding(horizontal = if (narrow) 12.dp else 16.dp, vertical = if (closed) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateBadge(d.date, closed, tag)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${weekdayName(d.date.dayOfWeek)} ${d.date.day} ${monthWord(d.date)}", Modifier.weight(1f, fill = false), fontFamily = Inter(),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (closed) Kit.Muted else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    tag?.takeIf { it != DayTag.CLOSED }?.let { TagPill(if (it == DayTag.BEST) "Best day" else if (it == DayTag.LOWEST) "Lowest day" else it.label, it.color) }
                }
                Text(if (closed) "Closed · no sales" else "${countText(d.orders)} ${if (d.orders == 1L) "order" else "orders"} · ${countText(d.guests)} guests",
                    fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
            }
            if (!closed) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(v.money(d.sales), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 15.sp else 18.sp, letterSpacing = 0.sp,
                    color = Kit.Ink, maxLines = 1, softWrap = false)
                val change = if (d.date == today || before == null) null else v.change(d.sales, before.sales)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrendBadge(change)
                    if (!narrow && change != null) Text("against ${weekdayDate(d.date.minus(DatePeriod(days = 7)))}", fontFamily = Inter(), fontSize = 10.5.sp,
                        color = Kit.Faint, maxLines = 1)
                }
            }
        }
        if (closed) return@Column
        // Its orders, from the first to the last.
        if (details.ordersLoading) Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Canvas)) }
        } else if (details.orders != null) {
            val all = d.date in details.allShown
            val shown = if (all) orders else orders.take(FirstOrders)
            Column(Modifier.fillMaxWidth().padding(horizontal = if (narrow) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (orders.isEmpty()) Quiet("No orders were opened this day.")
                shown.forEach { order -> OrderLine(v, order, details, names, zone, open = details.order?.id == order.id, model) }
                if (orders.size > FirstOrders) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { model.showAllOrders(d.date) }
                    .padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (all) "Show fewer" else "Show all ${countText(orders.size.toLong())} orders", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Green)
                    Icon(if (all) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp), tint = Kit.Green)
                }
            }
        }
        // What the day adds up to (user, 2026-11-15: "each day has at the end the details: guests, orders, total money").
        Spacer(Modifier.height(12.dp))
        DayFooter(v, d, orders.takeIf { details.orders != null && it.isNotEmpty() })
    }
}

/** The day's date as a small calendar leaf: the weekday on top, the day's number under it. */
@Composable
private fun DateBadge(date: LocalDate, closed: Boolean, tag: DayTag?) {
    val tone = when {
        closed -> Kit.Grey
        tag == DayTag.TODAY -> Kit.Blue
        else -> Kit.Green
    }
    Column(Modifier.size(width = 48.dp, height = 52.dp).clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.1f)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(weekdayShort(date).uppercase(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 0.6.sp, color = tone, maxLines = 1)
        Text("${date.day}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = if (closed) Kit.Muted else Kit.Ink, maxLines = 1)
    }
}

/**
 * The day's totals in a strip across the card's foot: orders, guests, dishes, average bill, cancelled and unpaid when there
 * are any, and what was paid, in green at the end. Without orders (a role that can't see them) the day's own figures.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayFooter(v: StatsView, d: DayPoint, orders: List<StatsOrder>?) {
    val narrow = LocalNarrowDialog.current
    val figures: List<Pair<String, String>>
    val total: Pair<String, String>
    if (orders != null) {
        val s = DayTotals(orders)
        figures = listOfNotNull("Orders" to countText(s.count), "Guests" to countText(s.guests), "Dishes" to countText(s.dishes),
            "Average bill" to v.money(s.average), ("Cancelled" to countText(s.cancelled)).takeIf { s.cancelled > 0 },
            ("Unpaid" to v.money(s.unpaid)).takeIf { s.unpaid > 0 })
        total = "Total paid" to v.money(s.paid)
    } else {
        figures = listOf("Orders" to countText(d.orders), "Guests" to countText(d.guests), "Average bill" to v.money(if (d.orders > 0) d.sales / d.orders else 0))
        total = "Total sales" to v.money(d.sales)
    }
    FlowRow(Modifier.fillMaxWidth().background(Kit.Canvas).padding(horizontal = if (narrow) 12.dp else 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(if (narrow) 16.dp else 26.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        figures.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(label.uppercase(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, letterSpacing = 0.5.sp,
                    color = if (label == "Unpaid") Kit.Danger else Kit.Faint, maxLines = 1)
                Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, letterSpacing = 0.sp,
                    color = if (label == "Unpaid") Kit.Danger else Kit.Ink, maxLines = 1, softWrap = false)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = if (narrow) Alignment.Start else Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(total.first.uppercase(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, letterSpacing = 0.5.sp, color = Kit.Green, maxLines = 1)
            Text(total.second, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.sp, color = Kit.Green, maxLines = 1, softWrap = false)
        }
    }
}

/** "Friday 13 November 2026". */
internal fun longDate(date: LocalDate): String = "${weekdayName(date.dayOfWeek)} ${date.day} ${monthWord(date)} ${date.year}"

internal fun monthWord(date: LocalDate): String = date.month.name.lowercase().replaceFirstChar { it.uppercase() }

/** "1 – 15 Nov · 15 days · €71,614.17 in sales" for the whole period. */
private fun periodLine(v: StatsView): String =
    "${com.saporini.mobile_desktop.core.components.periodLabel(v.from, v.to)} · ${v.dayCount} days · ${v.money(v.days.sumOf { it.sales })} in sales"

private fun orderTimes(order: StatsOrder, zone: TimeZone): String =
    timeText(order.openedAt, zone) + (order.closedAt?.let { " – ${timeText(it, zone)}" } ?: "")

@Composable
private fun Quiet(text: String) {
    Text(text, Modifier.padding(vertical = 6.dp), fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
}

internal fun statusOf(order: StatsOrder): Pair<String, Color>? = when {
    order.unpaid -> "Unpaid" to Kit.Danger
    order.status == "CANCELLED" -> "Cancelled" to Kit.Grey
    order.status == "VOIDED" -> "Voided" to Kit.Grey
    else -> null
}

/** An order's line; a click opens what they had and the bill under it (and closes it again). */
@Composable
private fun OrderLine(v: StatsView, order: StatsOrder, details: StatsDetails, names: Map<String, String>, zone: TimeZone, open: Boolean,
                      model: StatisticsScreenModel) {
    val status = statusOf(order)
    val counted = order.status == "CLOSED"
    val where = order.table?.let { "Table $it" } ?: orderTypeLabel(order.type)
    val detail = listOfNotNull(where, order.staffId?.let(names::get), order.guests?.takeIf { it > 0 }?.let { "$it ${if (it == 1) "guest" else "guests"}" },
        order.items?.takeIf { it > 0 }?.let { "$it ${if (it == 1) "dish" else "dishes"}" }).joinToString(" · ")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas)
        .then(if (open) Modifier.border(1.dp, Kit.Green.copy(alpha = 0.45f), RoundedCornerShape(10.dp)) else Modifier)) {
        Row(Modifier.fillMaxWidth().clickable { model.openOrder(order) }
            .padding(horizontal = 14.dp, vertical = 9.dp).alpha(if (counted || order.unpaid || open) 1f else 0.62f),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(timeText(order.openedAt, zone), Modifier.width(44.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
                color = Kit.Muted, maxLines = 1)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("#${order.number}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    status?.let { (label, color) -> TagPill(label, color) }
                }
                if (detail.isNotEmpty()) Text(detail, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(v.money(order.total), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (counted) Kit.Ink else Kit.Muted,
                textDecoration = if (order.status == "CANCELLED") TextDecoration.LineThrough else null, maxLines = 1, softWrap = false)
            Icon(if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, if (open) "Close the order" else "Open the order",
                Modifier.size(20.dp), tint = if (open) Kit.Green else Kit.Faint)
        }
        AnimatedVisibility(open, enter = expandVertically(tween(240)) + fadeIn(tween(200)), exit = shrinkVertically(tween(200)) + fadeOut(tween(150))) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OrderDetails(v, details, order, zone, model)
            }
        }
    }
}

// ---- One order ----

/** What they had and the bill, inside the order's line: two white boxes side by side (one under the other on a phone). */
@Composable
private fun ColumnScope.OrderDetails(v: StatsView, details: StatsDetails, order: StatsOrder, zone: TimeZone, model: StatisticsScreenModel) {
    val r = details.receipt?.takeIf { details.order?.id == order.id }
    if (r == null) {
        if (details.receiptProblem != null) {
            Text(details.receiptProblem, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Danger)
            KitButton("Try again", { model.closeOrder(); model.openOrder(order) }, style = ButtonStyle.SECONDARY)
        } else repeat(3) { Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)) }
        return
    }
    fun money(value: com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal) = v.money(value.centsOf())
    val narrow = LocalNarrowDialog.current
    val linesBox: @Composable (Modifier) -> Unit = { m ->
        InnerBox("What they had", m, extra = "${r.lines.filter { !it.removed }.sumOf { it.quantity }} dishes" +
            (order.closedAt?.let { " · ${orderTimes(order, zone)}" } ?: "")) {
            if (r.lines.isEmpty()) Quiet("Nothing on this order.")
            r.lines.forEach { line -> ReceiptLine(line, ::money) }
        }
    }
    val totalsBox: @Composable (Modifier) -> Unit = { m ->
        InnerBox("The bill", m) {
            TotalLine("Dishes", money(r.subtotal))
            r.discounts.forEach { TotalLine(it.name, "−${money(it.amount)}") }
            if (r.discounts.isEmpty() && r.discountTotal.centsOf() > 0) TotalLine("Discounts", "−${money(r.discountTotal)}")
            if (r.serviceChargeTotal.centsOf() > 0) TotalLine("Service charge", money(r.serviceChargeTotal))
            r.tax?.let { tax -> TotalLine("VAT ${tax.rate.value.trimEnd('0').trimEnd('.')}%${if (tax.inclusive) " (included)" else ""}", money(tax.amount), faint = true) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
            TotalLine("Total", money(r.total), strong = true)
            if (r.payments.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                r.payments.forEach { p -> PaymentLine(p, ::money, zone) }
            }
            if (r.tipTotal.centsOf() > 0) TotalLine("Tips", money(r.tipTotal), faint = true)
            if (r.balanceDue.centsOf() > 0) TotalLine("Still owed", money(r.balanceDue), color = Kit.Danger)
        }
    }
    if (narrow) { linesBox(Modifier.fillMaxWidth()); totalsBox(Modifier.fillMaxWidth()) }
    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        linesBox(Modifier.weight(1.4f))
        totalsBox(Modifier.weight(1f))
    }
}

/** A white box inside an open order: a small bold title (and a note at its right), then what's in it. */
@Composable
private fun InnerBox(title: String, modifier: Modifier, extra: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1)
            extra?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1) }
        }
        content()
    }
}

@Composable
private fun ReceiptLine(line: ReceiptDto.Line, money: (com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal) -> String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 8.dp)
        .alpha(if (line.removed) 0.55f else 1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${line.quantity}×", Modifier.width(30.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Kit.Muted, maxLines = 1)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.name + (line.variant?.let { " · $it" } ?: ""), Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (line.removed) TextDecoration.LineThrough else null)
                if (line.removed) TagPill("Removed", Kit.Grey)
            }
            val more = (line.options + listOfNotNull(line.notes?.takeIf { it.isNotBlank() }?.let { "“$it”" })).joinToString(" · ")
            if (more.isNotEmpty()) Text(more, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(money(line.lineTotal), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, softWrap = false,
            textDecoration = if (line.removed) TextDecoration.LineThrough else null)
    }
}

@Composable
private fun TotalLine(label: String, value: String, strong: Boolean = false, faint: Boolean = false, color: Color = Kit.Ink) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (strong) 15.sp else 12.5.sp, color = if (faint) Kit.Muted else color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold, fontSize = if (strong) 16.sp else 12.5.sp,
            color = if (faint) Kit.Muted else color, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun PaymentLine(p: ReceiptDto.Payment, money: (com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal) -> String, zone: TimeZone) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(methodColor(p.method).copy(alpha = 0.14f)), Alignment.Center) {
            Icon(methodIcon(p.method), null, Modifier.size(16.dp), tint = methodColor(p.method))
        }
        Column(Modifier.weight(1f)) {
            Text(methodLabel(p.method) + (p.cardLast4?.let { " ••$it" } ?: ""), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
                color = Kit.Ink, maxLines = 1)
            val more = listOfNotNull(p.paidAt?.let { "paid ${timeText(it, zone)}" }, p.tipAmount.centsOf().takeIf { it > 0 }?.let { "${money(p.tipAmount)} tip" },
                p.refundedAmount.centsOf().takeIf { it > 0 }?.let { "${money(p.refundedAmount)} given back" }).joinToString(" · ")
            if (more.isNotEmpty()) Text(more, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        }
        Text(money(p.amount), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1, softWrap = false)
    }
}
