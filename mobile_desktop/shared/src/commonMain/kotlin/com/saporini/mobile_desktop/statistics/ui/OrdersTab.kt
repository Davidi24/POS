package com.saporini.mobile_desktop.statistics.ui

import mobile_desktop.shared.generated.resources.notification_order
import mobile_desktop.shared.generated.resources.Res
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.RoomService
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.StatsView
import androidx.compose.foundation.layout.Box
import com.saporini.mobile_desktop.pos.shifts.ShiftAvatar

// Statistics → Orders: how many, how big, what happened to them (paid, cancelled, voided, refunded, still open), when
// the house is busiest (weekday × hour), how and where they were ordered, and the items taken off them.

@Composable
internal fun OrdersSection(v: StatsView, size: ScreenSize) {
    val t = v.totals
    val c = v.compareTotals
    SectionHeading("Orders", "How many, what happened to them, when the house is busiest and what was taken off",
        Res.drawable.notification_order, StatsSeries[1])
    TileGrid(listOf(
        { m -> StatTile("Orders", countText(t.orders), m, v.change(t.orders, c?.orders),
            detail = if (t.open > 0) "${countText(t.open)} still open" else "${v.money(t.averageBill)} each on average", icon = Icons.AutoMirrored.Outlined.ReceiptLong, tone = StatsSeries[1]) },
        { m -> StatTile("Cancelled", countText(t.cancelled), m, v.change(t.cancelled, c?.cancelled), inverted = true, detail = "before anything was made",
            icon = Icons.Outlined.HighlightOff, tone = Kit.Grey) },
        { m -> StatTile("Voided", countText(t.voided), m, v.change(t.voided, c?.voided), inverted = true, detail = "taken back after sending",
            icon = Icons.Outlined.Block, tone = Kit.Grey) },
        { m -> StatTile("Items removed", countText(t.removedItems), m, inverted = true, detail = v.money(t.removedValue) + " in value",
            icon = Icons.Outlined.RemoveShoppingCart, tone = Kit.Grey) },
        // Guests who left without paying (user, 2026-11-01); voided orders don't count these.
        { m -> StatTile("Unpaid", countText(t.unpaid), m, v.change(t.unpaid, c?.unpaid), inverted = true,
            detail = if (t.unpaid > 0) v.money(t.unpaidValue) + " not paid" else "everyone paid",
            icon = Icons.Outlined.MoneyOff, tone = Kit.Danger) }
    ))
    PanelLine(size,
        1.5f to { m -> OrdersByDayPanel(v, m) },
        1f to { m -> OutcomesPanel(v, m) }
    )
    BusyTimesPanel(v, Modifier.fillMaxWidth())
    PanelLine(size,
        1f to { m -> TypesDonutPanel(v, m) },
        1f to { m -> SourcesOrdersPanel(v, m) },
        1f to { m -> AverageByTypePanel(v, m) }
    )
    PanelLine(size,
        1f to { m -> RemovedByPersonPanel(v, m) },
        1f to { m -> VoidedByPersonPanel(v, m) }
    )
}

@Composable
private fun OrdersByDayPanel(v: StatsView, modifier: Modifier) {
    val days = v.days
    val compare = v.compareDays
    StatsPanel(Icons.Outlined.BarChart, "Orders by day", modifier, empty = days.all { it.orders == 0L }) {
        HeroLine(countText(v.totals.orders), "orders", v.change(v.totals.orders, v.compareTotals?.orders))
        ColumnChart(days.mapIndexed { i, d ->
            ColumnDatum(dayLabel(d.date, v.dayCount), d.orders.toFloat(), "${d.orders} orders · ${v.money(d.sales)}", compare?.getOrNull(i)?.orders?.toFloat())
        }, roomLeft(), height = 220.dp, color = StatsSeries[1], axisFormat = { compactNumber(it) }, peakLabel = { compactNumber(it) },
            compareName = compareName(v).takeIf { compare != null })
    }
}

/** What happened to the orders: paid and closed, refunded, cancelled, voided, still open. */
@Composable
private fun OutcomesPanel(v: StatsView, modifier: Modifier) {
    val t = v.totals
    val paid = (t.orders - t.refundedOrders).coerceAtLeast(0)
    val all = paid + t.refundedOrders + t.cancelled + t.voided + t.unpaid + t.open
    StatsPanel(Icons.Outlined.DonutLarge, "What happened to orders", modifier, empty = all == 0L) {
        val parts = listOf(
            Part("PAID", "Paid", paid.toFloat(), Kit.Green, countText(paid)),
            Part("REFUNDED", "Paid, then refunded", t.refundedOrders.toFloat(), Kit.Amber, countText(t.refundedOrders)),
            Part("CANCELLED", "Cancelled", t.cancelled.toFloat(), Kit.Grey, countText(t.cancelled)),
            Part("VOIDED", "Voided", t.voided.toFloat(), Kit.Danger, countText(t.voided)),
            Part("UNPAID", "Closed as unpaid", t.unpaid.toFloat(), StatsSeries[5], countText(t.unpaid)),
            Part("OPEN", "Still open", t.open.toFloat(), StatsSeries[1], countText(t.open))
        )
        // In a row kept one height, the ring and its lines sit in the middle of the room the panel has.
        Box(roomLeft().fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Donut(parts, countText(all), "orders", size = 132.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    parts.forEach { KeyRow(it.color, it.label, it.display, "${percent1(it.value / all.coerceAtLeast(1))} of orders") }
                }
            }
        }
    }
}

/** MOCK until the server sends weekday × hour: when orders come in, darker for more. */
@Composable
private fun BusyTimesPanel(v: StatsView, modifier: Modifier) {
    val heat = v.heat
    StatsPanel(Icons.Outlined.GridView, "Busy hours and days", modifier, empty = heat.all { row -> row.all { it == 0L } }, note = "orders by weekday and hour") {
        val busiest = heat.indices.flatMap { r -> heat[r].indices.map { c -> Triple(r, c, heat[r][c]) } }.maxBy { it.third }
        PanelNote("Busiest: ${weekdayName(v.weekdays[busiest.first].day)}s at ${hourLabel(busiest.second)}:00, about ${busiest.third} orders in that hour over the period")
        HeatMap(v.weekdays.map { weekdayName(it.day).take(3) }, (0..23).map { hourLabel(it) }, heat.map { row -> row.map { it.toFloat() } },
            cellHeight = 26.dp, format = { "${it.toLong()} orders" }, titleOf = { r, c -> "${weekdayName(v.weekdays[r].day)} ${hourLabel(c)}:00" })
    }
}

@Composable
private fun TypesDonutPanel(v: StatsView, modifier: Modifier) {
    val types = v.orderTypes
    StatsPanel(Icons.Outlined.RoomService, "Orders by type", modifier, empty = types.isEmpty()) {
        val total = types.sumOf { it.count }
        val parts = types.map { Part(it.key, orderTypeLabel(it.key), it.count.toFloat(), typeColor(it.key), "${it.count}", v.money(it.sales)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Donut(parts, countText(total), "orders", size = 112.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { parts.forEach { KeyRow(it.color, it.label, it.display, it.detail) } }
        }
    }
}

@Composable
private fun SourcesOrdersPanel(v: StatsView, modifier: Modifier) {
    val sources = v.sources.sortedByDescending { it.count }
    StatsPanel(Icons.Outlined.Devices, "Orders by source", modifier, empty = sources.isEmpty()) {
        RankBars(sources.map { Triple(sourceLabel(it.key), it.count.toFloat(), "${it.count}") }, details = sources.map { v.money(it.sales) }, color = StatsSeries[1])
    }
}

@Composable
private fun AverageByTypePanel(v: StatsView, modifier: Modifier) {
    val types = v.orderTypes.filter { it.count > 0 }
    StatsPanel(Icons.Outlined.Sell, "Average order by type", modifier, empty = types.isEmpty()) {
        ColumnChart(types.map { ColumnDatum(orderTypeLabel(it.key), it.sales / 100f / it.count, "${v.money(it.sales / it.count)} on average · ${it.count} orders",
            color = typeColor(it.key)) }, roomLeft(), height = 180.dp, axisFormat = { v.axisMoney(it) }, peakLabel = { v.axisMoney(it) })
    }
}

@Composable
private fun RemovedByPersonPanel(v: StatsView, modifier: Modifier) {
    val people = v.staff.filter { it.removedItems > 0 }.sortedByDescending { it.removedValue }
    StatsPanel(Icons.Outlined.RemoveShoppingCart, "Items removed, by person", modifier, empty = people.isEmpty(),
        emptyText = "Nobody took items off orders in these days.") {
        RankBars(people.map { Triple(it.name, it.removedValue.toFloat(), v.money(it.removedValue)) }, details = people.map { "${it.removedItems} ${if (it.removedItems == 1L) "item" else "items"}" },
            color = Kit.Purple, leading = { ShiftAvatar(people[it].name, 36.dp) })
    }
}

@Composable
private fun VoidedByPersonPanel(v: StatsView, modifier: Modifier) {
    val people = v.staff.filter { it.voidedOrders > 0 || it.refunds > 0 }.sortedByDescending { it.voidedOrders }
    StatsPanel(Icons.Outlined.Block, "Voided orders and refunds, by person", modifier, empty = people.isEmpty(),
        emptyText = "Nobody voided an order or gave a refund in these days.") {
        RankBars(people.map { Triple(it.name, it.voidedOrders.toFloat(), "${it.voidedOrders} voided") }, details = people.map { "${v.money(it.refunds)} refunded" },
            color = Kit.Grey, leading = { ShiftAvatar(people[it].name, 36.dp) })
    }
}
