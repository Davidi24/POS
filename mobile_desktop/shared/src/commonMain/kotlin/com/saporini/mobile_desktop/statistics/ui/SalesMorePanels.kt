package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.saporini.mobile_desktop.statistics.FloorFigures
import com.saporini.mobile_desktop.statistics.FloorPlace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.TrendBadge
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.statistics.BillBand
import com.saporini.mobile_desktop.statistics.HourPoint
import com.saporini.mobile_desktop.statistics.StatsView

// Statistics → Sales, under Sales by weekday (user, 2026-11-14: "add these new ones, make them look good"): lunch against
// dinner, where the money came from, the bill sizes and, with two floors or more, the sales by floor.

/**
 * A service of the day by its hours: from [from] until [until] (until past 24 runs into the night; the service day
 * starts at 06:00). MOCK: these hours become an Admin Hub setting with the production pass.
 */
private class Service(val name: String, val from: Int, val until: Int, val icon: ImageVector, val color: Color) {
    fun holds(hour: Int): Boolean = if (until > 24) hour >= from || hour < until - 24 else hour in from until until
    val hours: String get() = if (until > 24) "from ${hourLabel(from)}:00" else "${hourLabel(from)}:00 – ${hourLabel(until)}:00"
}

private val Services = listOf(
    Service("Breakfast", 6, 11, Icons.Outlined.FreeBreakfast, StatsSeries[2]),
    Service("Lunch", 11, 16, Icons.Outlined.WbSunny, StatsSeries[4]),
    Service("Dinner", 16, 30, Icons.Outlined.NightsStay, StatsSeries[3])
)

private class ServiceFigures(val service: Service, val hours: List<HourPoint>, val before: Long?) {
    val sales: Long = hours.sumOf { it.sales }
    val orders: Long = hours.sumOf { it.orders }
    val averageBill: Long get() = if (orders > 0) sales / orders else 0
    /**
     * Every hour of the service in the order they happen (user, 2026-11-14: a lone column looked bad), so quiet hours show
     * as low columns; hours after midnight only up to the last one with sales.
     */
    val span: List<HourPoint> = run {
        val byHour = hours.associateBy { it.hour }
        val day = (service.from until minOf(service.until, 24)).map { byHour[it] ?: HourPoint(it, 0, 0) }
        val night = if (service.until > 24) (0 until service.until - 24).map { byHour[it] ?: HourPoint(it, 0, 0) } else emptyList()
        day + night.take(night.indexOfLast { it.sales > 0 } + 1)
    }
}

/**
 * Lunch against dinner (and breakfast when there were sales then): a card each with its sales and change, its share of
 * the day's sales, the orders and the average bill. [stacked] puts the cards one under another (phones).
 */
@Composable
internal fun ServicesPanel(v: StatsView, modifier: Modifier, stacked: Boolean) {
    fun figures(hours: List<HourPoint>?, service: Service) = hours?.filter { service.holds(it.hour) }
    val shown = Services.mapNotNull { service ->
        val now = figures(v.hours, service).orEmpty()
        if (service.name == "Breakfast" && now.sumOf { it.sales } == 0L) return@mapNotNull null
        ServiceFigures(service, now, figures(v.compareHours, service)?.sumOf { it.sales })
    }
    val total = shown.sumOf { it.sales }
    StatsPanel(Icons.Outlined.Schedule, if (shown.size > 2) "By service" else "Lunch vs dinner", modifier, empty = total == 0L,
        action = {
            CardDownload(v, "sales-by-service") {
                CardReport("sales-by-service", if (shown.size > 2) "By service" else "Lunch vs dinner",
                    listOf(ReportColumn("Service"), ReportColumn("Hours"), ReportColumn("Sales", ColumnKind.MONEY), ReportColumn("Share", ColumnKind.SHARE),
                        ReportColumn("Orders", ColumnKind.COUNT), ReportColumn("Average bill", ColumnKind.MONEY)),
                    shown.map { f -> listOf(f.service.name, f.service.hours, f.sales, csvShare(f.sales, total), f.orders, f.averageBill) },
                    listOf(ReportTotal("Total sales", total), ReportTotal("Orders", shown.sumOf { it.orders }, money = false)))
            }
        }) {
        val best = shown.maxBy { it.sales }
        val other = shown.filter { it !== best }.maxByOrNull { it.sales }
        PanelNote(when {
            other == null || other.sales == 0L -> "All the sales came at ${best.service.name.lowercase()}"
            else -> "${best.service.name} brings in ${percentOf(best.sales, total)} of the sales" +
                if (best.averageBill > other.averageBill) ", with a bigger bill: ${v.money(best.averageBill)} against ${v.money(other.averageBill)}"
                else ", with a smaller bill: ${v.money(best.averageBill)} against ${v.money(other.averageBill)}"
        })
        // One money scale for every service (user, 2026-11-15: "breakfast's columns look as high as dinner's but the money is
        // so different"), with its amounts on the left of each card's columns.
        val scale = (niceAxis(shown.maxOf { f -> f.span.maxOfOrNull { it.sales } ?: 0L } / 100f, steps = 2).first * 100).toLong()
        val card: @Composable (ServiceFigures, Modifier) -> Unit = { f, m -> ServiceCard(v, f, total, scale, m) }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { shown.forEach { card(it, Modifier.fillMaxWidth()) } }
        else Row(Modifier.fillMaxWidth().then(roomLeft()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { card(it, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

/**
 * One service, kept simple (user, 2026-11-14: "too complex"): its name and hours, the sales, its share with the change,
 * its hours as small columns (the busiest full) and, at the foot, orders and the average bill.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceCard(v: StatsView, f: ServiceFigures, total: Long, scale: Long, modifier: Modifier) {
    val color = f.service.color
    // Room above, below and between the parts (user, 2026-11-14: "no padding in the vertical way").
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(horizontal = 14.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTile(f.service.icon, color, 28.dp)
                Column {
                    Text(f.service.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    Text(f.service.hours, fontFamily = Inter(), fontSize = 10.5.sp, color = Kit.Muted, maxLines = 1)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                // The whole amount, never cut: it gets a line of its own.
                Text(v.money(f.sales), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.sp,
                    color = if (f.sales > 0) Kit.Ink else Kit.Faint, maxLines = 1, softWrap = false)
                // Its share as a pill in the service's colour, the change beside it (under it when there's no room).
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.align(Alignment.CenterVertically).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("${percentOf(f.sales, total)} of sales", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                            letterSpacing = 0.sp, color = color, maxLines = 1)
                    }
                    TrendBadge(v.change(f.sales, f.before))
                }
            }
        }
        MiniHours(v, f.span, color, scale, Modifier.padding(top = 18.dp))
        // Orders and the average bill side by side in a white strip, split by a thin line.
        Row(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CardFact(countText(f.orders), "orders", Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(22.dp).background(Kit.Border))
            CardFact(if (f.orders > 0) v.money(f.averageBill) else "–", "a bill", Modifier.weight(1f))
        }
    }
}

/**
 * The service's hours as small columns on the money [scale] all the services share, its amounts (top, half, 0) on the
 * left with faint lines across; the service's busiest hour in full colour. Pointing at a column, or tapping it on a touch
 * screen, lifts it and shows its hour, sales and orders above it (user, 2026-11-14: "when I click or hover it shows its data").
 */
@Composable
private fun MiniHours(v: StatsView, hours: List<HourPoint>, color: Color, scale: Long, modifier: Modifier = Modifier) {
    val top = scale.coerceAtLeast(1)
    val peak = hours.maxOf { it.sales }
    val every = if (hours.size > 8) 2 else 1
    var hovered by remember { mutableStateOf<Int?>(null) }
    var tapped by remember { mutableStateOf<Int?>(null) }
    val shown = hovered ?: tapped
    val barsHeight = 64.dp
    Row(modifier.fillMaxWidth()) {
        // The amounts, each centred on its line: the scale's top, its half and 0.
        Box(Modifier.width(32.dp).height(barsHeight)) {
            listOf(Alignment.TopEnd to -5.5f, Alignment.CenterEnd to 0f, Alignment.BottomEnd to 5.5f).forEachIndexed { i, (at, shift) ->
                Text(v.axisMoney(top * (2 - i) / 2 / 100f), Modifier.align(at).offset(y = shift.dp), fontFamily = Inter(), fontSize = 9.sp,
                    letterSpacing = 0.sp, color = Kit.Faint, maxLines = 1, softWrap = false)
            }
        }
        Column(Modifier.weight(1f).padding(start = 5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth().height(barsHeight).drawBehind {
                val line = 1.dp.toPx()
                drawLine(Kit.Border.copy(alpha = 0.7f), Offset(0f, line / 2), Offset(size.width, line / 2), line)
                drawLine(Kit.Border.copy(alpha = 0.7f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), line)
                drawLine(color.copy(alpha = 0.3f), Offset(0f, size.height - line / 2), Offset(size.width, size.height - line / 2), line)
            }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                hours.forEachIndexed { i, h ->
                    val source = remember { MutableInteractionSource() }
                    val over by source.collectIsHoveredAsState()
                    LaunchedEffect(over) { if (over) hovered = i else if (hovered == i) hovered = null }
                    val active = shown == i
                    // The whole column's height answers the pointer, so a low column is as easy to reach as a tall one.
                    Box(Modifier.weight(1f).fillMaxHeight().hoverable(source)
                        .clickable(interactionSource = source, indication = null) { tapped = if (tapped == i) null else i },
                        contentAlignment = Alignment.BottomCenter) {
                        // A quiet hour keeps a sliver so it reads as "nothing"; one with a few sales stays visible on a big scale.
                        val share = if (h.sales == 0L) 0.03f else (h.sales.toFloat() / top).coerceIn(0.05f, 1f)
                        Box(Modifier.fillMaxWidth().fillMaxHeight(share).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(when {
                                active -> color
                                h.sales == 0L -> color.copy(alpha = 0.14f)
                                h.sales == peak && shown == null -> color
                                else -> color.copy(alpha = 0.35f)
                            }))
                        if (active) Popup(popupPositionProvider = AboveAnchor, onDismissRequest = { tapped = null }) { HourTip(v, h) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                hours.forEachIndexed { i, h ->
                    Text(if (i % every == 0 || shown == i) hourLabel(h.hour) else "", Modifier.weight(1f), fontFamily = Inter(), fontSize = 9.sp,
                        fontWeight = if (shown == i) FontWeight.Bold else FontWeight.Normal, color = if (shown == i) Kit.Ink else Kit.Faint,
                        textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

/** The dark tip over a column: "12:00 – 13:00", then "€12,340.00 · 182 orders" (the charts' tip look). */
@Composable
internal fun HourTip(v: StatsView, h: HourPoint) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF1F2421)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${hourLabel(h.hour)}:00 – ${hourLabel((h.hour + 1) % 24)}:00", fontFamily = Inter(), fontSize = 10.5.sp, letterSpacing = 0.sp,
            color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        Text(if (h.sales == 0L) "No sales" else "${v.money(h.sales)} · ${countText(h.orders)} ${if (h.orders == 1L) "order" else "orders"}",
            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.sp, color = Color.White, maxLines = 1)
    }
}

/** Puts a popup just above its anchor, centred on it, kept inside the window. */
private object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height - 8
        val y = if (above >= 0) above else anchorBounds.bottom + 8
        return IntOffset(x, y)
    }
}

@Composable
private fun CardFact(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1, softWrap = false)
        Text(label, fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, maxLines = 1)
    }
}


private fun moneySourceLabel(key: String): String = when (key) {
    "BOOKING" -> "Booked tables"
    "WALK_IN" -> "Walk-ins"
    "ONLINE" -> "Online orders"
    "TAKEAWAY" -> "Takeaway"
    "DELIVERY" -> "Delivery"
    else -> key.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
}

private fun moneySourceIcon(key: String): ImageVector = when (key) {
    "BOOKING" -> Icons.Outlined.EventAvailable
    "WALK_IN" -> Icons.AutoMirrored.Outlined.DirectionsWalk
    "ONLINE" -> Icons.Outlined.QrCode2
    "TAKEAWAY" -> Icons.Outlined.ShoppingBag
    "DELIVERY" -> Icons.Outlined.DeliveryDining
    else -> Icons.Outlined.Storefront
}

/** What each source is, said under its name on the two-card look. */
private fun moneySourceNote(key: String): String = when (key) {
    "WALK_IN" -> "Ordered at the table with staff"
    "BOOKING" -> "Guests who booked a table"
    "ONLINE" -> "Ordered by the guests: QR, website, app"
    "TAKEAWAY" -> "Picked up by the guests"
    "DELIVERY" -> "Taken to the guests"
    else -> ""
}

private fun moneySourceColor(key: String): Color = when (key) {
    "BOOKING" -> StatsSeries[4]
    "WALK_IN" -> StatsSeries[1]
    "ONLINE" -> StatsSeries[5]
    "TAKEAWAY" -> typeColor("TAKEAWAY")
    "DELIVERY" -> typeColor("DELIVERY")
    else -> StatsOther
}

/**
 * Where the money came from: walk-ins and online orders, plus takeaway and delivery once they're switched on (see
 * [StatsView.moneySources]). With two (or one) a card each side by side, so the panel isn't half empty; with more, one bar
 * split by them and a line each with its orders, average bill, money and share.
 */
@Composable
internal fun MoneySourcesPanel(v: StatsView, modifier: Modifier) {
    val parts = v.moneySources
    StatsPanel(Icons.Outlined.Storefront, "Where the money came from", modifier, empty = parts.isEmpty() || parts.all { it.sales == 0L },
        action = {
            CardDownload(v, "where-the-money-came-from") {
                val all = parts.sumOf { it.sales }
                CardReport("where-the-money-came-from", "Where the money came from",
                    listOf(ReportColumn("From"), ReportColumn("Orders", ColumnKind.COUNT), ReportColumn("Sales", ColumnKind.MONEY),
                        ReportColumn("Average bill", ColumnKind.MONEY), ReportColumn("Share", ColumnKind.SHARE)),
                    parts.map { p -> listOf(moneySourceLabel(p.key), p.orders, p.sales, if (p.orders > 0) p.sales / p.orders else 0L, csvShare(p.sales, all)) },
                    listOf(ReportTotal("Total sales", all), ReportTotal("Orders", parts.sumOf { it.orders }, money = false)))
            }
        }) {
        val total = parts.sumOf { it.sales }.coerceAtLeast(1)
        fun bill(sales: Long, orders: Long) = if (orders > 0) sales / orders else 0
        val walkIns = parts.firstOrNull { it.key == "WALK_IN" }
        val online = parts.firstOrNull { it.key == "ONLINE" }
        PanelNote(when {
            online != null && walkIns != null && online.orders > 0 && walkIns.orders > 0 ->
                "Online orders bring in ${percentOf(online.sales, total)} of the sales, ${v.money(bill(online.sales, online.orders))} a bill against " +
                    "${v.money(bill(walkIns.sales, walkIns.orders))} for walk-ins"
            online != null && online.sales == 0L -> "No online orders yet: every sale was taken by staff"
            else -> "${moneySourceLabel(parts.first().key)} bring in ${percentOf(parts.first().sales, total)} of the sales"
        })
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.filter { it.sales > 0 }.forEach { Box(Modifier.weight(it.sales.toFloat()).fillMaxHeight().background(moneySourceColor(it.key))) }
        }
        if (parts.size <= 2) {
            Row(Modifier.fillMaxWidth().then(roomLeft()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                parts.forEach { part -> MoneySourceCard(v, part, total, Modifier.weight(1f).fillMaxHeight()) }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEach { part ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(start = 8.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconTile(moneySourceIcon(part.key), moneySourceColor(part.key), 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(moneySourceLabel(part.key), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("${countText(part.orders)} orders · ${v.money(bill(part.sales, part.orders))} a bill", fontFamily = Inter(), fontSize = 11.sp,
                            color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(v.money(part.sales), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    Text(percentOf(part.sales, total), Modifier.width(36.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, textAlign = TextAlign.End,
                        maxLines = 1)
                }
            }
        }
    }
}

/** One source as a card: icon, name and what it is, the money, its share as a pill, then orders | a bill (like the service cards). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoneySourceCard(v: StatsView, part: com.saporini.mobile_desktop.statistics.MoneySource, total: Long, modifier: Modifier) {
    val color = moneySourceColor(part.key)
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(moneySourceIcon(part.key), color, 34.dp)
                Column {
                    Text(moneySourceLabel(part.key), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1)
                    Text(moneySourceNote(part.key), fontFamily = Inter(), fontSize = 10.5.sp, color = Kit.Muted, maxLines = 2, lineHeight = 13.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(v.money(part.sales), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = 0.sp,
                    color = if (part.sales > 0) Kit.Ink else Kit.Faint, maxLines = 1, softWrap = false)
                Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("${percentOf(part.sales, total)} of sales", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp,
                        letterSpacing = 0.sp, color = color, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CardFact(countText(part.orders), "orders", Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(22.dp).background(Kit.Border))
            CardFact(if (part.orders > 0) v.money(part.sales / part.orders) else "–", "a bill", Modifier.weight(1f))
        }
    }
}

/** "Under €20", "€20 – €50", "€100 and more". */
private fun bandLabel(v: StatsView, band: BillBand): String {
    fun whole(cents: Long) = v.money(cents).removeSuffix(".00").removeSuffix(",00")
    return when {
        band.from == null -> "Under ${whole(band.until ?: 0)}"
        band.until == null -> "${whole(band.from)} and more"
        else -> "${whole(band.from)} – ${whole(band.until)}"
    }
}

private val BillsColor = StatsSeries[1]
private val BillSalesColor = StatsSeries[0]

/**
 * The bills in four price bands: for each, its share of the bills against its share of the sales, so you see which
 * bills carry the restaurant.
 */
@Composable
internal fun BillSizesPanel(v: StatsView, modifier: Modifier) {
    val bands = v.billBands
    StatsPanel(Icons.Outlined.Receipt, "Bill sizes", modifier, empty = bands.isEmpty(),
        action = {
            CardDownload(v, "bill-sizes") {
                val allBills = bands.sumOf { it.bills }
                val allSales = bands.sumOf { it.sales }
                CardReport("bill-sizes", "Bill sizes",
                    listOf(ReportColumn("Bills"), ReportColumn("Number of bills", ColumnKind.COUNT), ReportColumn("Share of bills", ColumnKind.SHARE),
                        ReportColumn("Sales", ColumnKind.MONEY), ReportColumn("Share of sales", ColumnKind.SHARE)),
                    bands.map { b -> listOf(bandLabel(v, b), b.bills, csvShare(b.bills, allBills), b.sales, csvShare(b.sales, allSales)) },
                    listOf(ReportTotal("Total sales", allSales), ReportTotal("Bills", allBills, money = false)))
            }
        }) {
        val bills = bands.sumOf { it.bills }.coerceAtLeast(1)
        val sales = bands.sumOf { it.sales }.coerceAtLeast(1)
        val heavy = bands.maxBy { it.sales.toFloat() / sales - it.bills.toFloat() / bills }
        PanelNote("Bills of ${bandLabel(v, heavy).replaceFirstChar { it.lowercase() }} are ${percentOf(heavy.bills, bills)} of the bills but " +
            "${percentOf(heavy.sales, sales)} of the sales")
        // Each bar says what it is ("17% of bills", "5% of sales"), so no key is needed (user, 2026-11-14: "make it more understandable").
        val top = bands.maxOf { maxOf(it.bills.toFloat() / bills, it.sales.toFloat() / sales) }.coerceAtLeast(0.01f)
        // Beside other panels the four bands share the height the row has, so the panel is never half empty (user, 2026-11-14).
        val stretch = inPanelRow()
        Column(Modifier.fillMaxWidth().then(roomLeft()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bands.forEach { band ->
                Row(Modifier.fillMaxWidth().then(if (stretch) Modifier.weight(1f) else Modifier).clip(RoundedCornerShape(10.dp)).background(Kit.Canvas)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(134.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(bandLabel(v, band), Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BandInfo(v, band, bills, sales)
                        }
                        Text("${countText(band.bills)} bills", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BandBar(band.bills.toFloat() / bills / top, percentOf(band.bills, bills), "of bills", BillsColor)
                        BandBar(band.sales.toFloat() / sales / top, percentOf(band.sales, sales), "of sales", BillSalesColor)
                    }
                }
            }
        }
    }
}

/**
 * An info mark beside a band's name: pointing at it or tapping it says in words what the two bars mean (user,
 * 2026-11-14): "82 bills were under €20, 17% of all bills. Together they come to €312.40, which is 4% of all sales."
 */
@Composable
private fun BandInfo(v: StatsView, band: BillBand, bills: Long, sales: Long) {
    val source = remember { MutableInteractionSource() }
    val over by source.collectIsHoveredAsState()
    var tapped by remember { mutableStateOf(false) }
    // Left out of a printed card (see StatsPanel): only its room stays.
    Box(Modifier.size(18.dp).alpha(if (LocalPrinting.current) 0f else 1f).clip(CircleShape).hoverable(source)
        .clickable(interactionSource = source, indication = null) { tapped = !tapped },
        contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Info, "What this means", Modifier.size(14.dp), tint = if (over || tapped) Kit.Ink else Kit.Faint)
        if (over || tapped) Popup(popupPositionProvider = AboveAnchor, onDismissRequest = { tapped = false }) { BandTip(v, band, bills, sales) }
    }
}

/** What a band's info mark says: how many bills fell in it, and what they came to. */
@Composable
internal fun BandTip(v: StatsView, band: BillBand, bills: Long, sales: Long) {
    fun whole(cents: Long) = v.money(cents).removeSuffix(".00").removeSuffix(",00")
    val range = when {
        band.from == null -> "under ${whole(band.until ?: 0)}"
        band.until == null -> "${whole(band.from)} or more"
        else -> "between ${whole(band.from)} and ${whole(band.until)}"
    }
    val count = "${countText(band.bills)} ${if (band.bills == 1L) "bill was" else "bills were"}"
    Column(Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1F2421)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$count $range, ${percentOf(band.bills, bills)} of all bills.", fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp,
            color = Color.White, lineHeight = 16.sp)
        Text("Together they come to ${v.money(band.sales)}, which is ${percentOf(band.sales, sales)} of all sales.", fontFamily = Inter(),
            fontSize = 12.sp, letterSpacing = 0.sp, color = Color.White, lineHeight = 16.sp)
    }
}

@Composable
private fun BandBar(fill: Float, percent: String, words: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).height(8.dp)) {
            Box(Modifier.fillMaxWidth(fill.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
        }
        // "17% of bills": the share in the bar's colour, then what it is.
        Row(Modifier.width(84.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(percent, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 0.sp, color = color, maxLines = 1)
            Text(" $words", fontFamily = Inter(), fontSize = 11.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1)
        }
    }
}


/** The server's name for orders without a table. */
internal const val NoTableFloor = "No table"

/** The floors that had sales, without the orders that had no table. */
internal fun StatsView.salesFloors() = floors.filter { it.key != NoTableFloor && it.sales > 0 }.sortedByDescending { it.sales }

/** The floors' list height when it scrolls: about the height of the bill bands beside it. */
private val FloorListHeight = 300.dp

/** A colour per floor (never green, the sales charts' colour), in the order the floors are listed. */
private val FloorColors = listOf(StatsSeries[1], StatsSeries[3], StatsSeries[2], StatsSeries[5], StatsSeries[4])

/**
 * The table sales of each floor, always shown (user, 2026-11-14), each floor a card in its own colour with its orders,
 * average bill and share; a floor's places (Tables → Edit → Create places) sit inside its card as white lines in the
 * floor's colour, so they read as part of it. With no places anywhere, a short line says where to make them.
 */
@Composable
internal fun FloorsPanel(v: StatsView, modifier: Modifier) {
    val floors = v.floorFigures
    StatsPanel(Icons.Outlined.Layers, "Sales by floor", modifier, empty = floors.isEmpty(), emptyText = "Each floor's sales show here once tables have orders.",
        action = {
            CardDownload(v, "sales-by-floor") {
                val all = floors.sumOf { it.sales }
                CardReport("sales-by-floor", "Sales by floor",
                    listOf(ReportColumn("Floor"), ReportColumn("Place"), ReportColumn("Orders", ColumnKind.COUNT), ReportColumn("Sales", ColumnKind.MONEY),
                        ReportColumn("Average bill", ColumnKind.MONEY), ReportColumn("Share", ColumnKind.SHARE)),
                    floors.flatMap { f ->
                        listOf(listOf<Any>(f.name, "Whole floor", f.orders, f.sales, if (f.orders > 0) f.sales / f.orders else 0L, csvShare(f.sales, all))) +
                            f.places.map { p -> listOf<Any>(f.name, p.name ?: "Not in a place", p.orders, p.sales, if (p.orders > 0) p.sales / p.orders else 0L, csvShare(p.sales, all)) }
                    },
                    listOf(ReportTotal("Table sales", all), ReportTotal("Orders", floors.sumOf { it.orders }, money = false)))
            }
        }) {
        val total = floors.sumOf { it.sales }
        val best = floors.first()
        val bestPlace = best.places.firstOrNull { it.name != null }
        PanelNote(when {
            floors.size > 1 -> "${best.name} brings in ${percentOf(best.sales, total)} of the table sales"
            bestPlace != null -> "${bestPlace.name} brings in ${percentOf(bestPlace.sales, best.sales)} of ${best.name}'s sales"
            else -> "All the table sales were on ${best.name}"
        })
        val cards: @Composable () -> Unit = {
            floors.forEachIndexed { i, floor -> FloorCard(v, floor, total, FloorColors[i % FloorColors.size], single = floors.size == 1) }
        }
        // Many floors and places scroll inside the panel, at a fixed height, so the row isn't stretched by them (user, 2026-11-14).
        if (inPanelRow() && floors.size + floors.sumOf { it.places.size } > 4) {
            ScrollColumn(Modifier.fillMaxWidth().height(FloorListHeight), verticalArrangement = Arrangement.spacedBy(8.dp)) { cards() }
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { cards() }
        if (floors.all { it.places.isEmpty() }) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.Border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = Kit.Faint)
                Text("Group tables into places in Tables → Edit → Create places, and each place's sales show here under its floor.",
                    fontFamily = Inter(), fontSize = 11.sp, letterSpacing = 0.sp, color = Kit.Muted, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun FloorCard(v: StatsView, floor: FloorFigures, total: Long, color: Color, single: Boolean) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(Icons.Outlined.Layers, color, 34.dp)
            Column(Modifier.weight(1f)) {
                Text(floor.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${countText(floor.orders)} orders · ${v.money(if (floor.orders > 0) floor.sales / floor.orders else 0)} a bill", fontFamily = Inter(),
                    fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(v.money(floor.sales), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1)
                Text(if (single) "all table sales" else "${percentOf(floor.sales, total)} of table sales", fontFamily = Inter(), fontSize = 10.5.sp,
                    color = color, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        if (!single) Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth((floor.sales.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
        }
        if (floor.places.isNotEmpty()) {
            // Its places, set in under the floor with a line of its colour on their left.
            val line = color.copy(alpha = 0.35f)
            Column(Modifier.fillMaxWidth().padding(start = 16.dp).drawBehind {
                val x = -9.dp.toPx()
                drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                floor.places.forEach { place -> PlaceLine(v, place, floor, color) }
            }
        }
    }
}

@Composable
private fun PlaceLine(v: StatsView, place: FloorPlace, floor: FloorFigures, color: Color) {
    val named = place.name != null
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (named) color else Color.Transparent)
                .then(if (named) Modifier else Modifier.border(1.5.dp, color.copy(alpha = 0.5f), CircleShape)))
            Column(Modifier.weight(1f)) {
                Text(place.name ?: "Not in a place", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
                    color = if (named) Kit.Ink else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${countText(place.orders)} orders · ${v.money(if (place.orders > 0) place.sales / place.orders else 0)} a bill", fontFamily = Inter(),
                    fontSize = 10.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(v.money(place.sales), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1)
                Text("${percentOf(place.sales, floor.sales)} of the floor", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth((place.sales.toFloat() / floor.sales.coerceAtLeast(1)).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50))
                .background(if (named) color.copy(alpha = 0.7f) else color.copy(alpha = 0.35f)))
        }
    }
}

/** "34%" of [total] (0% without one). */
private fun percentOf(part: Long, total: Long): String = if (total <= 0) "0%" else "${(part * 100 + total / 2) / total}%"
