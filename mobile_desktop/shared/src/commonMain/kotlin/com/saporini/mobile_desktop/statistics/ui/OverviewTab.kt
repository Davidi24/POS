package com.saporini.mobile_desktop.statistics.ui

import mobile_desktop.shared.generated.resources.settings_tables
import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import mobile_desktop.shared.generated.resources.shifts_needs_review
import mobile_desktop.shared.generated.resources.inventory_stock_value
import mobile_desktop.shared.generated.resources.inventory_stock_changes
import mobile_desktop.shared.generated.resources.role_finance
import mobile_desktop.shared.generated.resources.salary_waiters
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.outlined.AccessTime
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.notification_order
import mobile_desktop.shared.generated.resources.workspace_statistics
import mobile_desktop.shared.generated.resources.Res
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.RoomService
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.WatchRow
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.CompareMode
import com.saporini.mobile_desktop.statistics.StatsTab
import com.saporini.mobile_desktop.statistics.StatsView
import kotlinx.datetime.LocalDate
import mobile_desktop.shared.generated.resources.notification_table
import com.saporini.mobile_desktop.core.components.IconTile

// Statistics → Overview: the period at a glance. Ten figures with how they moved and a sparkline, the sales day by
// day against the comparison, how people paid, the best sellers, eat in / takeaway / delivery, and the branches (or
// what's worth a look).

internal fun dayLabel(date: LocalDate, days: Int): String = if (days <= 7) weekdayShort(date) else shortDate(date)

internal fun compareName(v: StatsView): String = when (v.compareMode) {
    CompareMode.PREVIOUS -> "The days before"
    CompareMode.LAST_YEAR -> "Last year"
    CompareMode.NONE -> ""
}

@Composable
internal fun OverviewTab(v: StatsView, size: ScreenSize, onTab: (StatsTab) -> Unit) {
    val t = v.totals
    val c = v.compareTotals
    val days = v.days
    // The key figures beside the graph (user, 2026-11-03).
    val figures = listOf(
        FigureCell("Total sales", v.money(t.sales), Res.drawable.workspace_statistics, v.change(t.sales, c?.sales),
            detail = "from ${countText(t.orders)} orders", onClick = { onTab(StatsTab.SALES) }),
        FigureCell("Orders", countText(t.orders), Res.drawable.settings_orders_kitchen, v.change(t.orders, c?.orders),
            detail = "${v.money(t.averageBill)} each on average", onClick = { onTab(StatsTab.SALES) }),
        FigureCell("Guests", countText(t.guests), Res.drawable.overview_guests, v.change(t.guests, c?.guests),
            detail = if (t.orders > 0) "${(t.guests * 10 / t.orders) / 10.0} an order" else null, onClick = { onTab(StatsTab.TEAM) }),
        FigureCell("Average bill", v.money(t.averageBill), Res.drawable.settings_payments, v.change(t.averageBill, c?.averageBill),
            detail = c?.let { "was ${v.money(it.averageBill)}" } ?: "an order"),
        FigureCell("Spend per guest", v.money(t.perGuest), Res.drawable.settings_tables, v.change(t.perGuest, c?.perGuest),
            detail = c?.let { "was ${v.money(it.perGuest)}" } ?: "a guest"),
        FigureCell("Tips", v.money(t.tips), Res.drawable.salary_waiters, v.change(t.tips, c?.tips), detail = "on top of the bills"),
        FigureCell("Total money in", v.money(t.collected), Res.drawable.role_finance, v.change(t.collected, c?.collected),
            detail = "bills, tips and fees", onClick = { onTab(StatsTab.PAYMENTS) }),
        FigureCell("Refunds", v.money(t.refunds), Res.drawable.inventory_stock_changes, v.change(t.refunds, c?.refunds), inverted = true,
            detail = "${countText(t.refundedOrders)} ${if (t.refundedOrders == 1L) "order" else "orders"}"),
        FigureCell("Discounts", v.money(t.discounts), Res.drawable.inventory_stock_value, v.change(t.discounts, c?.discounts), inverted = true,
            detail = if (t.sales > 0) "${percent1(t.discounts.toFloat() / (t.sales + t.discounts))} off the bills" else null),
        FigureCell("Cancelled & voided", countText(t.cancelled + t.voided), Res.drawable.shifts_needs_review,
            v.change(t.cancelled + t.voided, c?.let { it.cancelled + it.voided }), inverted = true,
            detail = "${t.cancelled} cancelled · ${t.voided} voided", onClick = { onTab(StatsTab.SALES) })
    )
    // The sales graph top left with the figures as a card on its right (user, 2026-11-03: "it will look perfect"); on a
    // phone the graph first, then the figures. "Worth a look" is gone: the grid already shows those figures.
    val vs = compareName(v).takeIf { it.isNotEmpty() }?.let { "vs. ${it.replaceFirstChar { c -> c.lowercase() }}" }
    // "To keep an eye on" sits down with the branches, so this panel isn't taller than the graph (user, 2026-11-03).
    // Tips with the sales; the money that came in and went out under them (user, 2026-11-03).
    val moneyOut = FigureCell("Total money out", v.money(t.refunds), Res.drawable.inventory_stock_changes, v.change(t.refunds, c?.refunds), inverted = true,
        detail = "given back to guests · ${countText(t.refundedOrders)} ${if (t.refundedOrders == 1L) "order" else "orders"}", onClick = { onTab(StatsTab.PAYMENTS) })
    val groups = listOf("Sales" to figures.subList(0, 6), "Money" to listOf(figures[6], moneyOut))
    val watch: @Composable (Modifier) -> Unit = { m ->
        KeyFigures(listOf("" to figures.subList(7, 10)), m, vs, title = "To keep an eye on", icon = Icons.Outlined.Visibility)
    }
    if (size.isPhone) {
        SalesTrendPanel(v, Modifier.fillMaxWidth(), size)
        KeyFigures(groups, Modifier.fillMaxWidth(), vs)
    } else PanelLine(size,
        1.45f to { m -> SalesTrendPanel(v, m, size) },
        1f to { m -> KeyFigures(groups, m, vs) }
    )
    // How the money came in, the best sellers and the busiest hours.
    PanelLine(size,
        1f to { m -> MethodsPanel(v, m) { onTab(StatsTab.PAYMENTS) } },
        1.15f to { m -> BestSellersPanel(v, m) { onTab(StatsTab.DISHES) } },
        1f to { m -> HoursMiniPanel(v, m) }
    )
    // Eat in / takeaway / delivery only when the restaurant takes more than eat in (or such orders came anyway), the
    // branches only when there are several (user, 2026-11-03).
    val typesShown = (v.state.orderTypesOn?.let { it.size > 1 } ?: false) || v.orderTypes.any { it.key != "DINE_IN" && it.count > 0 }
    val branchesShown = v.branches.size > 1
    // With them, refunds, discounts and cancelled orders (alone it takes half the width).
    when {
        typesShown && branchesShown -> PanelLine(size, 1f to { m -> OrderTypesPanel(v, m) }, 1f to { m -> BranchesPanel(v, m) }, 1f to watch)
        typesShown -> PanelLine(size, 1f to { m -> OrderTypesPanel(v, m) }, 1f to watch)
        branchesShown -> PanelLine(size, 1f to { m -> BranchesPanel(v, m) }, 1f to watch)
        else -> PanelLine(size, 1f to watch, 1f to { m -> Spacer(m) })
    }
    if (t.open > 0) Footnote("${t.open} ${if (t.open == 1L) "order is" else "orders are"} still open and not counted yet.")
    v.state.overview?.kpis?.otherCurrencyOrders?.takeIf { it > 0 }?.let { Footnote("$it orders in another currency are left out of the money figures.") }
}

/** Sales day by day, the comparison dashed, with the best day, the daily average and the busiest day. */
@Composable
internal fun SalesTrendPanel(v: StatsView, modifier: Modifier, size: ScreenSize, title: String = "Sales by day") {
    val days = v.days
    StatsPanel(Icons.AutoMirrored.Outlined.ShowChart, title, modifier, empty = days.all { it.sales == 0L }) {
        HeroLine(v.money(v.totals.sales), "sales from ${countText(v.totals.orders)} orders", v.change(v.totals.sales, v.compareTotals?.sales))
        val series = listOfNotNull(
            LineSeries("This period", days.map { it.sales / 100f }, StatsAccent),
            v.compareDays?.let { LineSeries(compareName(v), it.map { d -> d.sales / 100f }, Kit.Faint, dashed = true) }
        )
        if (series.size > 1) ChartLegend(series.map { it.color to it.name }, Modifier.padding(horizontal = 4.dp), lines = true, dashedLast = true)
        LineChart(series, days.map { dayLabel(it.date, v.dayCount) }, roomLeft(), height = if (size.isPhone) 190.dp else 240.dp,
            format = { v.money((it * 100).toLong()) }, axisFormat = { v.axisMoney(it) }, titleOf = { weekdayDate(days[it].date) })
        val best = days.maxBy { it.sales }
        val busiest = days.maxBy { it.guests }
        FactLine(listOf(
            Triple("Best day", weekdayDate(best.date), v.money(best.sales)),
            Triple("Daily average", v.money(v.totals.sales / v.dayCount), "over ${v.dayCount} ${if (v.dayCount == 1) "day" else "days"}"),
            Triple("Most guests", weekdayDate(busiest.date), "${busiest.guests} guests")
        ), size.isPhone)
    }
}

/** How the money came in: a ring per method and a line each, then tips and refunds. */
@Composable
internal fun MethodsPanel(v: StatsView, modifier: Modifier, onMore: (() -> Unit)? = null) {
    // Card, cash and contactless always show, each in its colour, also at €0 (user, 2026-11-03); any other method once used.
    val used = v.methods.associateBy { it.key }
    val keys = (listOf("CARD", "CASH", "CONTACTLESS") + v.methods.map { it.key }).distinct()
    StatsPanel(Icons.Outlined.Payments, "Payment methods", modifier, action = onMore?.let { more -> { SeeAll(more) } }) {
        val parts = keys.map { key ->
            val m = used[key]
            Part(key, methodLabel(key), (m?.collected ?: 0L).toFloat(), methodColor(key), v.money(m?.collected ?: 0L),
                "${m?.count ?: 0} ${if ((m?.count ?: 0L) == 1L) "payment" else "payments"}")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Donut(parts, v.short(v.totals.collected), "collected", size = 124.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                parts.take(5).forEach { KeyRow(it.color, it.label, it.display, it.detail) }
            }
        }
        Spacer(Modifier.height(2.dp))
        FigureRow("Collected", v.money(v.totals.collected), strong = true)
        FigureRow("Tips", v.money(v.totals.tips), valueColor = Kit.Green)
        FigureRow("Refunded", "-" + v.money(v.totals.refunds), valueColor = if (v.totals.refunds > 0) Kit.Danger else Kit.Ink)
    }
}

@Composable
internal fun SeeAll(onClick: () -> Unit) {
    Text("See all", Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
}

@Composable
private fun BestSellersPanel(v: StatsView, modifier: Modifier, onMore: () -> Unit) {
    val dishes = v.dishes.sortedByDescending { it.quantity }
    StatsPanel(Icons.Outlined.RestaurantMenu, "Best-selling dishes", modifier, empty = dishes.isEmpty(),
        emptyText = "Dishes show here once they are ordered and paid.", action = { SeeAll(onMore) }) {
        RankBars(dishes.take(6).map { Triple(it.name, it.quantity.toFloat(), "${it.quantity} sold") }, details = dishes.take(6).map { v.money(it.sales) }, max = 6,
            leading = { DishThumb(v, dishes[it]) })
    }
}

/** Eat in, takeaway and delivery as one bar split three ways. */
@Composable
internal fun OrderTypesPanel(v: StatsView, modifier: Modifier, title: String = "Eat in, takeaway, delivery") {
    val types = v.orderTypes
    StatsPanel(Icons.Outlined.RoomService, title, modifier, empty = types.isEmpty(), emptyText = "How people ordered shows here.") {
        HeroLine(countText(types.sumOf { it.count }), "orders", big = false)
        SplitBar(types.map { Part(it.key, orderTypeLabel(it.key), it.sales.toFloat(), typeColor(it.key), v.money(it.sales), "${it.count} orders") })
    }
}

@Composable
private fun BranchesPanel(v: StatsView, modifier: Modifier) {
    val branches = v.branches
    StatsPanel(Icons.Outlined.Business, "Branches", modifier) {
        RankBars(branches.map { Triple(it.name, it.sales.toFloat(), v.money(it.sales)) }, details = branches.map { "${it.orders} orders" },
            colors = branches.map { v.branchColor(it.id) }, leading = { IconTile(Icons.Outlined.Business, v.branchColor(branches[it].id)) })
    }
}

/** The hours of the day as columns, the busiest one in full colour. */
@Composable
private fun HoursMiniPanel(v: StatsView, modifier: Modifier) {
    val hours = v.hours.filter { it.sales > 0 }
    StatsPanel(Icons.Outlined.AccessTime, "Busiest hours", modifier, empty = hours.isEmpty()) {
        ColumnChart(hours.map { ColumnDatum(hourLabel(it.hour), it.sales / 100f, "${v.money(it.sales)} · ${it.orders} orders") }, roomLeft(), height = 190.dp,
            axisFormat = { v.axisMoney(it) }, peakLabel = { v.axisMoney(it) })
    }
}

