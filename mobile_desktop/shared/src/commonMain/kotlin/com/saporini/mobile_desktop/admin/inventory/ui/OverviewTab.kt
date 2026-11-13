package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import mobile_desktop.shared.generated.resources.inventory_stock_value
import mobile_desktop.shared.generated.resources.inventory_stock_changes
import mobile_desktop.shared.generated.resources.inventory_stock_items
import mobile_desktop.shared.generated.resources.inventory_places
import mobile_desktop.shared.generated.resources.Res
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.Image
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import kotlin.time.Clock
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.admin.inventory.RECENT_MOVEMENTS
import androidx.compose.material.icons.outlined.History
import com.saporini.mobile_desktop.admin.inventory.CUSTOM_KIND
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.saporini.mobile_desktop.core.ui.HoverTooltip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.ACTIVITY_PERIODS
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.admin.inventory.StockActivityDayDto
import com.saporini.mobile_desktop.core.components.BarChart
import com.saporini.mobile_desktop.core.components.BarDatum
import com.saporini.mobile_desktop.core.components.HeroFigure
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.PanelRow
import com.saporini.mobile_desktop.core.components.RingChart
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.percentText
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.quantityText
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

/** Which kind of change the overview's chart shows. */
private enum class ActivitySeries(val label: String, val caption: String) {
    USED("Used", "taken by sales"),
    RECEIVED("Received", "delivered"),
    WASTED("Wasted", "thrown away")
}

/**
 * Inventory → Overview: the key numbers, what went in and out lately (counted by the server), how healthy the stock is,
 * where its value sits, and what's running low. The actions themselves live on their own tabs (Stock, Items, Counts).
 */
@Composable
internal fun OverviewTab(
    state: InventoryState,
    model: InventoryScreenModel,
    size: ScreenSize,
    cards: List<@Composable (Modifier) -> Unit>,
    onView: (InventoryView) -> Unit,
    modifier: Modifier
) {
    // Nothing to show yet (no items, or items but never any stock): a "get started" page instead of empty charts.
    val noStockYet = state.levels.none { it.onHandQuantity.asDouble() > 0 } && (state.activity?.changes ?: 0L) == 0L
    if (!state.loading && (state.items.isEmpty() || noStockYet)) {
        GettingStarted(state, model, size, modifier)
        return
    }
    ScrollColumn(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SummaryCardRow(size, cards)
        PanelRow(size,
            1.7f to { m -> ActivityPanel(state, model, m, compact = size.isPhone) },
            1f to { m -> HealthPanel(state, m) })
        val value: @Composable (Modifier) -> Unit = { m -> ValueByTypePanel(state, m, besideOthers = !size.isPhone) }
        val top: @Composable (Modifier) -> Unit = { m -> TopValuePanel(state, m) }
        val low: @Composable (Modifier) -> Unit = { m ->
            // "See all" opens Stock with only the running-low lines, where a delivery can be received for each.
            LowStockPanel(state, m) { model.levelsAt(null); model.lowOnly(true); onView(InventoryView.STOCK) }
        }
        val recent: @Composable (Modifier) -> Unit = { m -> RecentChangesPanel(state, m) { onView(InventoryView.HISTORY) } }
        // Four across on a desktop; two by two on a tablet (one under the other on a phone).
        if (size.isDesktop) PanelRow(size, 1f to value, 1f to top, 1f to low, 1f to recent)
        else {
            PanelRow(size, 1f to value, 1f to top)
            PanelRow(size, 1f to low, 1f to recent)
        }
        // Room under the last panels, so they scroll up clear of the window's edge (user, 2026-10-30).
        Spacer(Modifier.height(18.dp))
    }
}

// Most valuable stock, Running low and Recent stock changes show six lines each (user, 2026-10-30).
private const val PanelLines = 6

/** What went in and out per day: one kind at a time, today in full colour, the totals as switches under the chart. */
@Composable
private fun ActivityPanel(state: InventoryState, model: InventoryScreenModel, modifier: Modifier, compact: Boolean) {
    // Until someone picks one, the chart opens on what sales used, or on deliveries when nothing was used yet.
    var picked by rememberSaveable { mutableStateOf<ActivitySeries?>(null) }
    val activity = state.activity
    val series = picked ?: if (activity != null && activity.used.asDouble() <= 0 && activity.received.asDouble() > 0) ActivitySeries.RECEIVED else ActivitySeries.USED
    val currency = activity?.currency ?: "EUR"
    OverviewPanel(Icons.Outlined.Insights, "Stock in and out", modifier, action = {
        // On a phone: small pills as wide as their text ("7 d"), so none gets cut; "7 days" in a wider switch elsewhere.
        if (compact) PeriodPills(state.activityDays, model::activityDays)
        else OverviewTabs(ACTIVITY_PERIODS.map { "$it days" }, "${state.activityDays} days", { label -> model.activityDays(label.substringBefore(' ').toInt()) },
            Modifier.width(270.dp), height = 32.dp)
    }) {
        if (activity == null) {
            if (state.loading) ChartSkeleton()
            else OverviewCompactEmpty("The chart couldn't load", "Press refresh at the top to try again.", Icons.Outlined.Insights)
            return@OverviewPanel
        }
        if (activity.changes == 0L) {
            PanelEmpty(Icons.Outlined.Insights, "No stock changes in the last ${state.activityDays} days",
                "Deliveries, sales and waste show up here day by day as soon as they're recorded.")
            return@OverviewPanel
        }
        val pick = { day: StockActivityDayDto -> when (series) { ActivitySeries.USED -> day.used; ActivitySeries.RECEIVED -> day.received; ActivitySeries.WASTED -> day.wasted } }
        val total = when (series) { ActivitySeries.USED -> activity.used; ActivitySeries.RECEIVED -> activity.received; ActivitySeries.WASTED -> activity.wasted }
        val caption = "${series.caption} in the last ${state.activityDays} days"
        if (compact) {
            // On a phone the caption goes under the figure instead of being cut off beside it.
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(moneyText(cents(total), currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Kit.Ink, maxLines = 1)
                Text(caption, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
            }
        } else HeroFigure(moneyText(cents(total), currency), caption)
        val days = activity.days
        BarChart(
            days.map { day ->
                val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
                BarDatum(
                    label = date?.let { if (days.size <= 7) weekdayDate(it).substringBefore(' ') else "${it.day}" } ?: "",
                    value = pick(day).asDouble().toFloat(),
                    display = moneyText(cents(pick(day)), currency),
                    highlight = day.date == activity.to
                )
            },
            height = 170.dp, color = Kit.Green, showValues = false,
            // Hover (or tap on a phone) shows what came in and went out that day (user, 2026-10-30).
            tip = { index -> days.getOrNull(index)?.let { DayTip(it, currency, series) } }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
            ActivitySeries.entries.forEach { kind ->
                val value = when (kind) { ActivitySeries.USED -> activity.used; ActivitySeries.RECEIVED -> activity.received; ActivitySeries.WASTED -> activity.wasted }
                SeriesSwitch(kind.label, moneyText(cents(value), currency), selected = series == kind, Modifier.weight(1f), compact) { picked = kind }
            }
        }
        if (activity.used.asDouble() > 0 && activity.wasted.asDouble() > 0) {
            Text("Waste was ${percentText((activity.wasted.asDouble() / activity.used.asDouble()).toFloat())} of what sales used.",
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        }
    }
}

/** A day's tip over the chart: the date, then what came in (received) and went out (used by sales, wasted). */
@Composable
private fun DayTip(day: StockActivityDayDto, currency: String, series: ActivitySeries) {
    val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
    Text(date?.let(::weekdayDate) ?: day.date, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, maxLines = 1)
    // A sign only when something moved: "+€120.00" in, "−€35.20" out, plain "€0.00" otherwise.
    val amount = { value: OrderDecimal, sign: String -> cents(value).let { if (it > 0) sign + moneyText(it, currency) else moneyText(0, currency) } }
    TipLine("In · received", amount(day.received, "+"), movementLook("RECEIPT").color, series == ActivitySeries.RECEIVED)
    TipLine("Out · used by sales", amount(day.used, "−"), movementLook("SALE_CONSUMPTION").color, series == ActivitySeries.USED)
    TipLine("Out · wasted", amount(day.wasted, "−"), movementLook("WASTE").color, series == ActivitySeries.WASTED)
    Text(if (day.changes == 1L) "1 stock change" else "${day.changes} stock changes", fontFamily = Inter(), fontSize = 10.sp,
        color = Color.White.copy(alpha = 0.6f), maxLines = 1)
}

/** One line of [DayTip]: a dot in the kind's colour, its name and the amount (the kind the chart shows in bold). */
@Composable
private fun TipLine(label: String, amount: String, color: Color, shown: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, fontFamily = Inter(), fontWeight = if (shown) FontWeight.SemiBold else FontWeight.Normal, fontSize = 11.sp,
            color = Color.White.copy(alpha = if (shown) 1f else 0.75f), maxLines = 1)
        Spacer(Modifier.width(14.dp))
        Text(amount, fontFamily = Inter(), fontWeight = if (shown) FontWeight.Bold else FontWeight.SemiBold, fontSize = 11.sp, color = Color.White, maxLines = 1)
    }
}

/** The chart's period on a phone: three small pills in the overview's tab style, each as wide as its text. */
@Composable
private fun PeriodPills(days: Int, onPick: (Int) -> Unit) {
    Row(Modifier.height(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ACTIVITY_PERIODS.forEach { period ->
            val chosen = period == days
            Box(Modifier.fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(if (chosen) Kit.Green else Color.Transparent)
                .clickable { onPick(period) }.padding(horizontal = 9.dp), Alignment.Center) {
                Text("$period d", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.sp,
                    color = if (chosen) Color.White else Kit.Ink, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** A total under the chart; picking it shows that kind in the chart. */
@Composable
private fun SeriesSwitch(label: String, value: String, selected: Boolean, modifier: Modifier, compact: Boolean, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Kit.GreenSoft else Kit.Canvas)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = if (compact) 10.sp else 11.sp, color = if (selected) Kit.Green else Kit.Muted, maxLines = 1)
        // Smaller on a phone so a full amount like €12,345.00 still fits a third of the width.
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (compact) 12.5.sp else 15.sp, color = Kit.Ink, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

/** How the counted items stand: fine, running low or no stock. The ring and its three figures sit in the middle of the panel. */
@Composable
private fun HealthPanel(state: InventoryState, modifier: Modifier) {
    val counted = state.items.filter { it.active && it.trackInventory }
    val byItem = state.levelsByItem
    var fine = 0
    var low = 0
    var empty = 0
    counted.forEach { item ->
        val levels = byItem[item.id].orEmpty()
        when {
            levels.sumOf { it.onHandQuantity.asDouble() } <= 0.0 -> empty++
            levels.any { it.lowStock } -> low++
            else -> fine++
        }
    }
    val parts = listOf(Triple("Fine", fine, Kit.Green), Triple("Running low", low, Kit.Danger), Triple("No stock", empty, DeepRed))
    OverviewPanel(Icons.Outlined.DonutLarge, "Stock health", modifier) {
        if (counted.isEmpty()) {
            PanelEmpty(Icons.Outlined.DonutLarge, "No counted items yet", "Items you count show here as fine, running low or with no stock.")
            return@OverviewPanel
        }
        // fillMaxHeight takes what the panel has left (the panel is as tall as the chart beside it), so this centres.
        Column(Modifier.fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            RingChart(parts.map { it.second.toFloat() }, "${counted.size}", "items counted", size = 160.dp, colors = parts.map { it.third })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEach { (label, count, color) ->
                    HealthFigure(label, count, if (counted.isEmpty()) null else count.toFloat() / counted.size, color, Modifier.weight(1f))
                }
            }
            val notCounted = state.items.count { it.active && !it.trackInventory }
            if (notCounted > 0) Text("$notCounted more ${if (notCounted == 1) "item isn't" else "items aren't"} counted, like salt or napkins.",
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HealthFigure(label: String, count: Int, share: Float?, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("$count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Kit.Ink)
        Text(share?.let(::percentText) ?: "–", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
    }
}

/** Each kind keeps its own colour in this chart, whatever its rank. */
private fun kindColor(type: String): Color = when (type) {
    "PACKAGING" -> Color(0xFFB0662E)
    else -> kindKeyColor(type)
}

/** Where the stock's value sits: the total, one bar split by kind of item, and each kind's value and share. */
@Composable
private fun ValueByTypePanel(state: InventoryState, modifier: Modifier, besideOthers: Boolean) {
    // By kind key: the built-in code, or "CUSTOM:<id>" for the restaurant's own kinds.
    val types = state.items.associate { it.id to it.kindKey() }
    val byType = state.levels.groupBy { types[it.inventoryItemId] }
        .mapNotNull { (type, levels) -> type?.let { it to stockValueCents(state, levels) } }
        .filter { it.second > 0 }
    val total = byType.sumOf { it.second }
    // Every kind is listed (user, 2026-10-30), the ones with nothing in stock last at 0.
    val values = byType.toMap()
    OverviewPanel(Icons.Outlined.Category, "Value by kind", modifier) {
        if (byType.isEmpty()) {
            OverviewCompactEmpty("No stock value yet", "Receive deliveries with their cost to see where your money sits.", Icons.Outlined.Category)
            return@OverviewPanel
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(moneyText(total, "EUR"), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Kit.Ink)
            Text("  at cost", Modifier.padding(bottom = 3.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
        // One bar, split in the kinds' fixed order so a kind never changes place or colour.
        // The restaurant's kinds in their order, plus any a removed kind still holds.
        val kindKeys = state.itemKinds.map { it.key }.let { keys -> keys + byType.map { it.first }.filter { it !in keys } }
        val labels = state.itemKinds.associate { it.key to (if (it.code == CUSTOM_KIND) it.itemLabel() else itemTypeLabel(it.code)) }
        val ordered = kindKeys.mapNotNull { type -> byType.firstOrNull { it.first == type } }
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ordered.forEach { (type, value) -> Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(kindColor(type))) }
        }
        Spacer(Modifier.height(2.dp))
        val rows = kindKeys.map { type -> type to (values[type] ?: 0L) }.sortedByDescending { it.second }
        // The kinds scroll inside the panel (user, 2026-10-30), so it's never taller than the panels beside it: there it takes
        // the room their height leaves (asking for about four and a half rows itself); on a phone it shows about five.
        val list = if (besideOthers) Modifier.fillMaxWidth().weight(1f).intrinsicHeight(KindRowsHeight)
            else Modifier.fillMaxWidth().heightIn(max = KindRowsHeight + 44.dp)
        ScrollColumn(list, verticalArrangement = Arrangement.spacedBy(8.dp)) { rows.forEach { (type, value) ->
            // A name cut short ("Prepared (sauce, d…") shows in full on hover, and a click writes it out on the line (user, 2026-10-30).
            var cut by remember(type) { mutableStateOf(false) }
            var full by remember(type) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas)
                .then(if (cut || full) Modifier.clickable { full = !full } else Modifier).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(kindColor(type)))
                Spacer(Modifier.width(10.dp))
                // The width sits on this box: HoverTooltip leaves out its modifier while it's switched off.
                Box(Modifier.weight(1f)) {
                    HoverTooltip(labels[type] ?: itemTypeLabel(type), enabled = cut && !full) {
                        Text(labels[type] ?: itemTypeLabel(type), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                            maxLines = if (full) 3 else 1, overflow = TextOverflow.Ellipsis, onTextLayout = { if (!full) cut = it.hasVisualOverflow })
                    }
                }
                Text(moneyText(value, "EUR"), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (value > 0) Kit.Ink else Kit.Faint, maxLines = 1)
                Text(percentText(value.toFloat() / total), Modifier.width(44.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted,
                    textAlign = TextAlign.End, maxLines = 1)
            }
        } }
    }
}

// About four and a half kind rows (36 dp each, 8 dp apart): the half row shows there is more to scroll to.
private val KindRowsHeight = 194.dp

/**
 * Tells an `IntrinsicSize` row this list wants [height] (whatever its content), while it still fills the room it's given.
 * So the panels beside it set the row's height, and the list scrolls inside that.
 */
private fun Modifier.intrinsicHeight(height: Dp) = this then object : LayoutModifier {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = height.roundToPx()
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = height.roundToPx()
}

/**
 * The items worth the most right now, over every place: only those worth Admin Hub → Settings → Inventory's
 * "Valuable stock from" or more (100 by default). The top six show here; "See all" lists every one of them.
 */
@Composable
private fun TopValuePanel(state: InventoryState, modifier: Modifier) {
    val byItem = state.levelsByItem
    val currency = state.activity?.currency ?: "EUR"
    val fromCents = (state.valuableStockFrom * 100).roundToLong()
    val valuable = state.items.filter { it.active }.map { item -> item to stockValueCents(state, byItem[item.id].orEmpty()) }
        .filter { it.second > 0 && it.second >= fromCents }.sortedByDescending { it.second }
    var showAll by rememberSaveable { mutableStateOf(false) }
    OverviewPanel(Icons.Outlined.Paid, "Most valuable stock", modifier, count = valuable.size.takeIf { it > 0 }) {
        if (valuable.isEmpty()) {
            OverviewCompactEmpty("Nothing worth that much", "Items show here once their stock is worth ${moneyText(fromCents, currency)} or more. " +
                "Change the amount in Admin Hub → Settings → Inventory.", Icons.Outlined.Paid)
            return@OverviewPanel
        }
        valuable.take(PanelLines).forEachIndexed { index, (item, value) -> ValuableRow(state, index, item, value, currency) }
        SmallLink(if (valuable.size > PanelLines) "See all ${valuable.size}" else "See all", Icons.Outlined.Paid) { showAll = true }
    }
    if (showAll) {
        val total = stockValueCents(state, state.levels)
        AppDialog(
            "Most valuable stock",
            onDismiss = { showAll = false },
            subtitle = "${valuable.size} ${if (valuable.size == 1) "item is" else "items are"} worth ${moneyText(fromCents, currency)} or more, at cost",
            buttons = { KitButton("Close", { showAll = false }, style = ButtonStyle.SECONDARY) }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                valuable.forEachIndexed { index, (item, value) ->
                    ValuableRow(state, index, item, value, currency, share = if (total > 0) value.toFloat() / total else null)
                }
            }
            Text("Together ${moneyText(valuable.sumOf { it.second }, currency)}${if (total > 0) ", ${percentText(valuable.sumOf { it.second }.toFloat() / total)} of all your stock" else ""}. " +
                "Set from what value an item counts in Admin Hub → Settings → Inventory.",
                Modifier.padding(top = 4.dp), fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted)
        }
    }
}

/** One valuable item: rank, picture, name, how much and where, its value (and its share of all stock in the full list). */
@Composable
private fun ValuableRow(state: InventoryState, index: Int, item: StockItemDto, value: Long, currency: String, share: Float? = null) {
    val levels = state.levelsByItem[item.id].orEmpty()
    val total = levels.sumOf { it.onHandQuantity.asDouble() }
    val places = levels.count { it.onHandQuantity.asDouble() > 0 }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${index + 1}", Modifier.width(if (index >= 9) 22.dp else 16.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Faint,
            textAlign = TextAlign.Center)
        StockItemThumb(item, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${trimmed(total)} ${unitShort(item.baseUnit)} · $places ${if (places == 1) "place" else "places"}",
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(moneyText(value, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
            if (share != null) Text("${percentText(share)} of stock", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun LowStockPanel(state: InventoryState, modifier: Modifier, onSeeAll: () -> Unit) {
    val units = state.items.associate { it.id to it.baseUnit }
    // While something is low the panel's red border breathes, so it catches the eye.
    val pulse = if (state.lowStock.isEmpty()) Modifier else {
        val glow by rememberInfiniteTransition(label = "low-pulse").animateFloat(0.2f, 0.85f,
            infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "low-pulse-alpha")
        Modifier.border((1.5f + glow).dp, Kit.Danger.copy(alpha = glow), RoundedCornerShape(12.dp))
    }
    OverviewPanel(Icons.Outlined.NotificationImportant, "Running low", modifier.then(pulse), count = state.lowStock.size,
        titleColor = if (state.lowStock.isEmpty()) Kit.Ink else Kit.Danger) {
        if (state.lowStock.isEmpty()) {
            OverviewCompactEmpty("All stocked up", "Nothing is under its reorder point right now.", Icons.Outlined.CheckCircle)
        }
        state.lowStock.take(PanelLines).forEach { level ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ItemPicture(state, level.inventoryItemId)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(level.inventoryItemName ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${quantityText(level.onHandQuantity, units[level.inventoryItemId])} left · ${level.locationName ?: ""}",
                        fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Outlined.TrendingDown, "Running low", Modifier.size(20.dp), tint = Kit.Danger)
            }
        }
        if (state.lowStock.isNotEmpty()) SmallLink(if (state.lowStock.size > PanelLines) "See all ${state.lowStock.size} on Stock" else "Deal with them on Stock",
            Icons.Outlined.NotificationImportant, onSeeAll)
    }
}

/** The newest stock changes: what, where, how much and when. */
@Composable
private fun RecentChangesPanel(state: InventoryState, modifier: Modifier, onSeeAll: () -> Unit) {
    val units = state.items.associate { it.id to it.baseUnit }
    val now = Clock.System.now()
    OverviewPanel(Icons.Outlined.History, "Recent stock changes", modifier) {
        if (state.recentMovements.isEmpty()) {
            if (state.loading) RowsSkeleton(RECENT_MOVEMENTS)
            else OverviewCompactEmpty("No changes yet", "Deliveries, waste, moves and counts show here as they happen.", Icons.Outlined.History)
            return@OverviewPanel
        }
        state.recentMovements.take(RECENT_MOVEMENTS).forEach { move ->
            val look = movementLook(move.movementType)
            val delta = move.quantityDelta.asDouble()
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ItemPicture(state, move.inventoryItemId)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(move.inventoryItemName ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(move.locationName, agoText(move.occurredAt, now, RestaurantTime.zone)).joinToString(" · "),
                        fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${if (delta > 0) "+" else ""}${plainNumber(move.quantityDelta)} ${unitShort(move.unit ?: units[move.inventoryItemId])}",
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(look.icon, null, Modifier.size(13.dp), tint = look.color)
                        Text(look.label, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    }
                }
            }
        }
        SmallLink("See all in History", Icons.Outlined.History, onSeeAll)
    }
}

/** A calm empty state in the middle of a panel: icon in a soft circle, a title and one line. */
@Composable
private fun PanelEmpty(icon: ImageVector, title: String, hint: String) {
    Column(Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Kit.GreenSoft), Alignment.Center) {
            Icon(icon, null, Modifier.size(30.dp), tint = Kit.Green)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink, textAlign = TextAlign.Center)
        Text(hint, Modifier.widthIn(max = 340.dp), fontFamily = Inter(), fontSize = 12.sp, lineHeight = 18.sp, color = Kit.Muted, textAlign = TextAlign.Center)
    }
}

private class SetupStep(
    val title: String,
    val text: String,
    val picture: DrawableResource,
    val done: Boolean,
    val action: String,
    val ready: Boolean,
    val waitingFor: String?,
    val onClick: () -> Unit
)

/**
 * The overview before there's anything to show: a welcome, the three steps that fill it (each ticked off when done,
 * the next one highlighted) and what the page will show afterwards.
 */
@Composable
private fun GettingStarted(state: InventoryState, model: InventoryScreenModel, size: ScreenSize, modifier: Modifier) {
    val hasPlaces = state.activeLocations.isNotEmpty()
    val hasItems = state.items.any { it.active }
    val hasStock = state.levels.any { it.onHandQuantity.asDouble() > 0 }
    val steps = listOf(
        SetupStep("Add your places", "The kitchen, the bar, the walk-in fridge: wherever you keep stock.", Res.drawable.inventory_places,
            hasPlaces, "Add a place", ready = true, waitingFor = null, onClick = model::newLocation),
        SetupStep("Add your stock items", "Each ingredient, drink or supply once, with the unit you count it in and its cost.",
            Res.drawable.inventory_stock_items, hasItems, "Add an item", ready = true, waitingFor = null, onClick = model::newItem),
        SetupStep("Receive your first delivery", "Amounts per place, the stock's value and low-stock alerts all start from here.",
            Res.drawable.inventory_stock_changes, hasStock, "Receive stock", ready = hasPlaces && hasItems,
            waitingFor = "Add a place and an item first", onClick = { model.startMove(StockMoveKind.RECEIVE) })
    )
    val done = steps.count { it.done }
    val next = steps.indexOfFirst { !it.done }
    ScrollColumn(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Welcome
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(Kit.GreenSoft, Color.White)))
                .border(1.dp, Kit.Border, RoundedCornerShape(16.dp)).padding(horizontal = if (size.isPhone) 18.dp else 28.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (!size.isPhone) Image(painterResource(Res.drawable.inventory_stock_value), null, Modifier.size(128.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Set up your inventory", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Kit.Ink)
                Text("Three steps, and this page fills itself in: what you have, where it is, what it's worth and what's running low.",
                    fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.width(180.dp).height(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        steps.forEach { step -> Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(if (step.done) Kit.Green else Kit.Border)) }
                    }
                    Text("$done of ${steps.size} done", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                }
            }
        }
        // The steps
        val card: @Composable (Int, Modifier) -> Unit = { index, m -> SetupStepCard(index + 1, steps[index], highlighted = index == next, canEdit = state.canEdit, modifier = m) }
        if (size.isPhone) steps.indices.forEach { card(it, Modifier.fillMaxWidth()) }
        else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            steps.indices.forEach { card(it, Modifier.weight(1f).fillMaxHeight()) }
        }
        // What comes next
        Text("WHAT THIS PAGE WILL SHOW", Modifier.padding(top = 6.dp, start = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp,
            letterSpacing = 0.6.sp, color = Kit.Muted)
        val previews = listOf(
            Triple(Icons.Outlined.Insights, "Stock in and out", "What came in, was used and was wasted, day by day."),
            Triple(Icons.Outlined.DonutLarge, "Stock health", "What's fine, running low or empty, at a glance."),
            Triple(Icons.Outlined.Paid, "What it's worth", "Your stock's value at cost, by kind and by item."),
            Triple(Icons.Outlined.History, "Every change", "Deliveries, moves, waste and counts as they happen.")
        )
        val tile: @Composable (Triple<ImageVector, String, String>, Modifier) -> Unit = { (icon, title, text), m ->
            Row(m.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Tint), Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp), tint = Kit.Green)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    Text(text, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Muted, maxLines = 2)
                }
            }
        }
        val perRow = when (size) { ScreenSize.DESKTOP -> 4; ScreenSize.TABLET -> 2; ScreenSize.PHONE -> 1 }
        previews.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun SetupStepCard(number: Int, step: SetupStep, highlighted: Boolean, canEdit: Boolean, modifier: Modifier) {
    val border = when {
        step.done -> Kit.Green.copy(alpha = 0.45f)
        highlighted -> Kit.Green
        else -> Kit.Border
    }
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (step.done) Kit.GreenSoft else Color.White)
            .border(if (highlighted) 1.5.dp else 1.dp, border, RoundedCornerShape(14.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (step.done || highlighted) Kit.Green else Kit.Tint), Alignment.Center) {
                if (step.done) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Color.White)
                else Text("$number", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (highlighted) Color.White else Kit.Muted)
            }
            Spacer(Modifier.weight(1f))
            Image(painterResource(step.picture), null, Modifier.size(84.dp))
        }
        Text(step.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink)
        Text(step.text, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 18.sp, color = Kit.Muted)
        Spacer(Modifier.weight(1f))
        when {
            step.done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = Kit.Green)
                Text("Done", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Green)
            }
            !canEdit -> Text("Someone who can change stock does this.", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint)
            !step.ready -> Text(step.waitingFor ?: "", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint)
            else -> KitButton(step.action, step.onClick, Modifier.fillMaxWidth(), style = if (highlighted) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY)
        }
    }
}

/** The item's picture in a list row (its photo, a sample, or its kind's icon). */
@Composable
private fun ItemPicture(state: InventoryState, itemId: String?) {
    val item = state.item(itemId)
    if (item != null) StockItemThumb(item, 36.dp) else ItemThumb(null, null, 36.dp)
}

private fun cents(value: OrderDecimal): Long = (value.asDouble() * 100).roundToLong()
