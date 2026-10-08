package com.saporini.mobile_desktop.statistics.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.ChartPalette
import com.saporini.mobile_desktop.core.components.FactChip
import com.saporini.mobile_desktop.core.components.HeroFigure
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KpiCard
import com.saporini.mobile_desktop.core.components.LegendDot
import com.saporini.mobile_desktop.core.components.MetricBar
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PanelRow
import com.saporini.mobile_desktop.core.components.RankBadge
import com.saporini.mobile_desktop.core.components.RingChart
import com.saporini.mobile_desktop.core.components.ShareBars
import com.saporini.mobile_desktop.core.components.ShareDatum
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.TrendChart
import com.saporini.mobile_desktop.core.components.ValueLine
import com.saporini.mobile_desktop.core.components.WatchRow
import com.saporini.mobile_desktop.core.components.percentText
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.StatsTab
import com.saporini.mobile_desktop.statistics.changeText
import com.saporini.mobile_desktop.statistics.data.ItemSalesDto
import com.saporini.mobile_desktop.statistics.data.KpiDto
import com.saporini.mobile_desktop.statistics.data.StatsOverviewDto
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.notification_order
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.workspace_statistics

internal fun KpiDto.change(): String? = changeText(value, previous)

/** A count figure ("124") from a KPI whose value is a whole number. */
internal fun KpiDto.count(): String = value.value.substringBefore('.')

@Composable
internal fun OverviewTab(data: StatsOverviewDto, size: ScreenSize, onTab: (StatsTab) -> Unit) {
    val k = data.kpis
    val currency = data.period.currency
    SummaryCardRow(size, listOf(
        { m -> KpiCard("Sales", k.sales.value.money(currency), "${k.sales.previous.money(currency)} before", Kit.Green, m, k.sales.change(),
            image = Res.drawable.workspace_statistics, onClick = { onTab(StatsTab.SALES) }) },
        { m -> KpiCard("Orders", k.orders.count(), "${k.orders.previous.value.substringBefore('.')} before", Kit.Blue, m, k.orders.change(),
            image = Res.drawable.notification_order) },
        { m -> KpiCard("Guests", k.guests.count(), "${k.averagePerGuest.value.money(currency)} spent per guest", Kit.Purple, m, k.guests.change(),
            image = Res.drawable.overview_guests) },
        { m -> KpiCard("Average bill", k.averageTicket.value.money(currency), "${k.averageTicket.previous.money(currency)} before", Kit.Amber, m,
            k.averageTicket.change(), image = Res.drawable.settings_payments) }
    ))
    PanelRow(size,
        2f to { m -> SalesTrendPanel(data, m) },
        1f to { m -> MoneyInPanel(data, m) }
    )
    PanelRow(size,
        1.3f to { m -> TopDishesPanel(data.topItems, currency, m) { onTab(StatsTab.SALES) } },
        1f to { m -> OrderTypesPanel(data, m) },
        1f to { m -> WatchPanel(data, m) }
    )
    if (k.openOrders > 0) Footnote("${k.openOrders} ${if (k.openOrders == 1L) "order is" else "orders are"} still open and not counted yet.")
    if (k.otherCurrencyOrders > 0) Footnote("${k.otherCurrencyOrders} orders in another currency are left out of the money figures.")
}

@Composable
private fun SalesTrendPanel(data: StatsOverviewDto, modifier: Modifier) {
    val currency = data.period.currency
    val days = data.salesByDay.mapNotNull { day -> parseDate(day.date)?.let { it to day } }
    OverviewPanel(Icons.AutoMirrored.Outlined.ShowChart, "Sales by day", modifier) {
        HeroFigure(data.kpis.sales.value.money(currency), "sales, ${data.kpis.orders.count()} orders", data.kpis.sales.change())
        if (days.isEmpty() || days.all { it.second.sales.cents() == 0L }) {
            OverviewCompactEmpty("No sales in these days", "Sales show here once orders are paid.", Icons.AutoMirrored.Outlined.ShowChart)
            return@OverviewPanel
        }
        TrendChart(days.map { it.second.sales.cents() / 100f }, days.map { (date, _) -> if (days.size <= 7) weekdayShort(date) else shortDate(date) },
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp), height = 200.dp)
        val best = days.maxBy { it.second.sales.cents() }
        val average = days.sumOf { it.second.sales.cents() } / days.size
        val busiest = days.maxBy { it.second.guests }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FactChip("Best day", weekdayDate(best.first), Modifier.weight(1f), best.second.sales.money(currency))
            FactChip("Daily average", com.saporini.mobile_desktop.core.format.moneyText(average, currency), Modifier.weight(1f),
                "over ${days.size} ${if (days.size == 1) "day" else "days"}", tone = Kit.Blue)
            FactChip("Most guests", weekdayDate(busiest.first), Modifier.weight(1f), "${busiest.second.guests} guests", tone = Kit.Purple)
        }
    }
}

@Composable
private fun MoneyInPanel(data: StatsOverviewDto, modifier: Modifier) {
    val currency = data.period.currency
    val k = data.kpis
    val methods = data.paymentMethods.sortedByDescending { it.collected.cents() }
    OverviewPanel(Icons.Outlined.Payments, "Money in", modifier) {
        if (methods.isEmpty()) {
            OverviewCompactEmpty("No payments yet", "Payments per method show here.", Icons.Outlined.Payments)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RingChart(methods.map { it.collected.cents().toFloat() }, k.collected.value.shortMoney(currency), "collected", size = 128.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val total = methods.sumOf { it.collected.cents() }.coerceAtLeast(1)
                    methods.take(5).forEachIndexed { index, method ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(methodIcon(method.method), null, Modifier.size(16.dp), tint = ChartPalette[index % ChartPalette.size])
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(methodLabel(method.method), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${method.count} · ${percentText(method.collected.cents().toFloat() / total)}", fontFamily = Inter(), fontSize = 10.sp,
                                    color = Kit.Muted)
                            }
                            Text(method.collected.money(currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(2.dp))
        ValueLine("Collected", k.collected.value.money(currency), strong = true)
        ValueLine("Tips", k.tips.value.money(currency), valueColor = Kit.Green)
        ValueLine("Refunded", "-${k.refunds.value.money(currency)}", valueColor = if (k.refunds.value.cents() > 0) Kit.Danger else Kit.Ink)
    }
}

@Composable
internal fun TopDishesPanel(items: List<ItemSalesDto>, currency: String, modifier: Modifier, title: String = "Best sellers", onMore: (() -> Unit)? = null) {
    OverviewPanel(Icons.Outlined.RestaurantMenu, title, modifier,
        action = onMore?.let { more -> {
            Text("See all", Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = more).padding(horizontal = 6.dp, vertical = 2.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
        } }) {
        if (items.isEmpty()) {
            OverviewCompactEmpty("Nothing sold yet", "Dishes show here once they are ordered and paid.", Icons.Outlined.RestaurantMenu)
            return@OverviewPanel
        }
        val top = items.maxOf { it.sales.cents() }.coerceAtLeast(1)
        items.take(6).forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RankBadge(index + 1)
                MetricBar(item.name, item.sales.money(currency), item.sales.cents().toFloat() / top, ChartPalette[index % ChartPalette.size],
                    Modifier.weight(1f), detail = "${item.quantity} sold")
            }
        }
    }
}

@Composable
private fun OrderTypesPanel(data: StatsOverviewDto, modifier: Modifier) {
    val currency = data.period.currency
    OverviewPanel(Icons.Outlined.Category, "How people ordered", modifier) {
        if (data.orderTypes.isEmpty()) {
            OverviewCompactEmpty("No orders yet", "Eat in, takeaway and delivery show here.", Icons.Outlined.Category)
            return@OverviewPanel
        }
        val types = data.orderTypes.sortedByDescending { it.sales.cents() }
        Box(Modifier.fillMaxWidth(), Alignment.Center) {
            RingChart(types.map { it.sales.cents().toFloat() }, "${types.sumOf { it.orders }}", "orders", size = 132.dp)
        }
        ShareBars(types.map { ShareDatum(orderTypeLabel(it.key), it.sales.cents().toFloat(), it.sales.money(currency), "${it.orders} orders") })
    }
}

@Composable
private fun WatchPanel(data: StatsOverviewDto, modifier: Modifier) {
    val k = data.kpis
    val currency = data.period.currency
    OverviewPanel(Icons.Outlined.Visibility, "Worth a look", modifier) {
        WatchRow(Icons.Outlined.LocalOffer, "Discounts", k.discounts.value.money(currency), Kit.Amber, k.discounts.change(),
            detail = percentOfSales(k.discounts, k.sales))
        WatchRow(Icons.AutoMirrored.Outlined.Undo, "Refunds", k.refunds.value.money(currency), Kit.Danger, k.refunds.change())
        WatchRow(Icons.Outlined.RemoveShoppingCart, "Removed items", k.removedItemsValue.value.money(currency), Kit.Purple, k.removedItemsValue.change(),
            detail = "value taken off orders")
        WatchRow(Icons.Outlined.Cancel, "Cancelled orders", k.cancelledOrders.count(), Kit.Grey, k.cancelledOrders.change())
        WatchRow(Icons.Outlined.Block, "Voided orders", k.voidedOrders.count(), Kit.Danger, k.voidedOrders.change())
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(Kit.Green, "lower than before")
            LegendDot(Kit.Danger, "higher than before")
        }
    }
}

private fun percentOfSales(part: KpiDto, sales: KpiDto): String? {
    val total = sales.value.cents().takeIf { it > 0 } ?: return null
    return "${percentText(part.value.cents().toFloat() / total)} of sales"
}
