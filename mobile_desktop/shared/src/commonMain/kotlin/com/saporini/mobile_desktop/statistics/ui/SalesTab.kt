package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.TableBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.BarChart
import com.saporini.mobile_desktop.core.components.BarDatum
import com.saporini.mobile_desktop.core.components.FactChip
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.HeroFigure
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PanelRow
import com.saporini.mobile_desktop.core.components.RankBadge
import com.saporini.mobile_desktop.core.components.ShareBars
import com.saporini.mobile_desktop.core.components.ShareDatum
import com.saporini.mobile_desktop.core.components.TableColumn
import com.saporini.mobile_desktop.core.components.TableHeader
import com.saporini.mobile_desktop.core.components.TrendChart
import com.saporini.mobile_desktop.core.components.percentText
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.data.ItemSalesDto
import com.saporini.mobile_desktop.statistics.data.ShareDto
import com.saporini.mobile_desktop.statistics.data.StatsSalesDto

private enum class TrendMeasure(val label: String) { SALES("Sales"), ORDERS("Orders"), GUESTS("Guests") }

@Composable
internal fun SalesTab(data: StatsSalesDto, size: ScreenSize) {
    val currency = data.period.currency
    DayTrendPanel(data, size)
    PanelRow(size,
        1.4f to { m -> HoursPanel(data, m) },
        1f to { m -> WeekdaysPanel(data, m) }
    )
    PanelRow(size,
        1f to { m -> SharePanel(Icons.AutoMirrored.Outlined.MenuOpen, "Menu sections", data.sections.map { ShareDto(it.section, it.sales, it.quantity) }, currency, "sold",
            m, "Sales per menu section show here.") },
        1f to { m -> SharePanel(Icons.Outlined.Devices, "Where orders came from", data.sources.map { it.copy(key = sourceLabel(it.key)) }, currency, "orders",
            m, "Till, QR, phone and app orders show here.") },
        1f to { m -> SharePanel(Icons.Outlined.TableBar, "Rooms", data.floors, currency, "orders", m, "Sales per room show here once tables are used.") }
    )
    PanelRow(size,
        1.6f to { m -> DishTablePanel(data.topItems, currency, size, m) },
        1f to { m -> SlowDishesPanel(data.slowItems, currency, m) }
    )
}

@Composable
private fun DayTrendPanel(data: StatsSalesDto, size: ScreenSize) {
    val currency = data.period.currency
    var measure by remember { mutableStateOf(TrendMeasure.SALES) }
    val days = data.salesByDay.mapNotNull { day -> parseDate(day.date)?.let { it to day } }
    OverviewPanel(Icons.AutoMirrored.Outlined.ShowChart, "Day by day", Modifier.fillMaxWidth(),
        action = { FilterPills(TrendMeasure.entries.map { it to it.label }, measure, { measure = it }) }) {
        val totalSales = days.sumOf { it.second.sales.cents() }
        val totalOrders = days.sumOf { it.second.orders }
        val totalGuests = days.sumOf { it.second.guests }
        HeroFigure(
            when (measure) {
                TrendMeasure.SALES -> com.saporini.mobile_desktop.core.format.moneyText(totalSales, currency)
                TrendMeasure.ORDERS -> "$totalOrders"
                TrendMeasure.GUESTS -> "$totalGuests"
            },
            when (measure) { TrendMeasure.SALES -> "sales"; TrendMeasure.ORDERS -> "orders"; TrendMeasure.GUESTS -> "guests" } +
                " in ${days.size} ${if (days.size == 1) "day" else "days"}"
        )
        if (days.isEmpty()) {
            OverviewCompactEmpty("No sales in these days", "Each day's figures show here.", Icons.AutoMirrored.Outlined.ShowChart)
            return@OverviewPanel
        }
        val values = days.map { (_, day) ->
            when (measure) {
                TrendMeasure.SALES -> day.sales.cents() / 100f
                TrendMeasure.ORDERS -> day.orders.toFloat()
                TrendMeasure.GUESTS -> day.guests.toFloat()
            }
        }
        TrendChart(values, days.map { (date, _) -> if (days.size <= 7) weekdayShort(date) else shortDate(date) },
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp), height = if (size.isPhone) 180.dp else 230.dp,
            color = when (measure) { TrendMeasure.SALES -> Kit.Green; TrendMeasure.ORDERS -> Kit.Blue; TrendMeasure.GUESTS -> Kit.Purple })
        if (!size.isPhone && days.size > 1) {
            val best = days.maxBy { it.second.sales.cents() }
            val quiet = days.minBy { it.second.sales.cents() }
            val perOrder = if (totalOrders > 0) totalSales / totalOrders else 0
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FactChip("Best day", weekdayDate(best.first), Modifier.weight(1f), best.second.sales.money(currency))
                FactChip("Quietest day", weekdayDate(quiet.first), Modifier.weight(1f), quiet.second.sales.money(currency), tone = Kit.Grey)
                FactChip("Per order", com.saporini.mobile_desktop.core.format.moneyText(perOrder, currency), Modifier.weight(1f), "on average", tone = Kit.Amber)
                FactChip("Per guest", if (totalGuests > 0) com.saporini.mobile_desktop.core.format.moneyText(totalSales / totalGuests, currency) else "–",
                    Modifier.weight(1f), "$totalGuests guests", tone = Kit.Purple)
            }
        }
    }
}

@Composable
private fun HoursPanel(data: StatsSalesDto, modifier: Modifier) {
    val currency = data.period.currency
    val hours = data.salesByHour.sortedBy { it.hour }
    OverviewPanel(Icons.Outlined.AccessTime, "Busiest hours", modifier) {
        if (hours.isEmpty() || hours.all { it.sales.cents() == 0L }) {
            OverviewCompactEmpty("No sales yet", "Sales per hour of the day show here.", Icons.Outlined.AccessTime)
            return@OverviewPanel
        }
        // Only the hours the restaurant is busy, from the first to the last hour with sales.
        val first = hours.indexOfFirst { it.sales.cents() > 0 }
        val last = hours.indexOfLast { it.sales.cents() > 0 }
        val shown = hours.subList(first, last + 1)
        val peak = shown.maxBy { it.sales.cents() }
        Text("Most sales at ${hourLabel(peak.hour)}:00 – ${hourLabel((peak.hour + 1) % 24)}:00, ${peak.sales.money(currency)} from ${peak.orders} orders",
            Modifier.padding(horizontal = 4.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        BarChart(shown.map { BarDatum(hourLabel(it.hour), it.sales.cents() / 100f, it.sales.shortMoney(currency), it.hour == peak.hour) },
            Modifier.padding(top = 4.dp), height = 210.dp)
    }
}

@Composable
private fun WeekdaysPanel(data: StatsSalesDto, modifier: Modifier) {
    val currency = data.period.currency
    val days = data.salesByWeekday.sortedBy { it.isoDayOfWeek }
    OverviewPanel(Icons.Outlined.CalendarViewWeek, "Weekdays", modifier, titleExtra = "average per day") {
        if (days.isEmpty() || days.all { it.averageSales.cents() == 0L }) {
            OverviewCompactEmpty("Not enough days yet", "The average for each weekday shows here.", Icons.Outlined.CalendarViewWeek)
            return@OverviewPanel
        }
        val best = days.maxBy { it.averageSales.cents() }
        Text("${best.day.lowercase().replaceFirstChar { it.uppercase() }}s are the strongest, ${best.averageSales.money(currency)} on average",
            Modifier.padding(horizontal = 4.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        BarChart(days.map { BarDatum(it.day.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }, it.averageSales.cents() / 100f,
            it.averageSales.shortMoney(currency), it == best) }, Modifier.padding(top = 4.dp), height = 210.dp, color = Kit.Blue)
    }
}

@Composable
private fun SharePanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    shares: List<ShareDto>,
    currency: String,
    countWord: String,
    modifier: Modifier,
    empty: String
) {
    OverviewPanel(icon, title, modifier) {
        if (shares.isEmpty()) {
            OverviewCompactEmpty("Nothing yet", empty, icon)
            return@OverviewPanel
        }
        ShareBars(shares.map { ShareDatum(it.key, it.sales.cents().toFloat(), it.sales.money(currency), "${it.orders} $countWord") }, max = 6)
    }
}

@Composable
private fun DishTablePanel(items: List<ItemSalesDto>, currency: String, size: ScreenSize, modifier: Modifier) {
    val total = items.sumOf { it.sales.cents() }.coerceAtLeast(1)
    OverviewPanel(Icons.AutoMirrored.Outlined.MenuOpen, "Dishes sold", modifier, count = items.size) {
        if (items.isEmpty()) {
            OverviewCompactEmpty("Nothing sold yet", "Every dish sold, how many and for how much.", Icons.AutoMirrored.Outlined.MenuOpen)
            return@OverviewPanel
        }
        val compact = size.isPhone
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas)) {
            TableHeader(if (compact) listOf(TableColumn("Dish", 2f), TableColumn("Sales", 1f, true))
            else listOf(TableColumn("#", 0.3f), TableColumn("Dish", 2.2f), TableColumn("Section", 1.2f), TableColumn("Sold", 0.7f, true),
                TableColumn("Sales", 1f, true), TableColumn("Share", 1.2f)), trailingSpace = false)
        }
        items.take(15).forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!compact) Row(Modifier.weight(0.3f)) { RankBadge(index + 1) }
                Column(Modifier.weight(if (compact) 2f else 2.2f)) {
                    Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (compact) Text("${item.quantity} sold · ${item.section ?: ""}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                }
                if (!compact) {
                    Text(item.section ?: "–", Modifier.weight(1.2f), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.quantity}", Modifier.weight(0.7f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                        textAlign = TextAlign.End)
                }
                Text(item.sales.money(currency), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink,
                    textAlign = TextAlign.End)
                if (!compact) {
                    val share = item.sales.cents().toFloat() / total
                    Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(Kit.Green.copy(alpha = 0.12f))) {
                            Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).fillMaxHeight().background(Kit.Green))
                        }
                        Text(percentText(share), Modifier.width(34.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                    }
                }
            }
            if (index < items.take(15).lastIndex) KitDivider()
        }
        if (items.size > 15) Footnote("The full list is in Reports → Dishes sold.")
    }
}

@Composable
private fun SlowDishesPanel(items: List<ItemSalesDto>, currency: String, modifier: Modifier) {
    OverviewPanel(Icons.AutoMirrored.Outlined.TrendingDown, "Selling slowly", modifier, titleColor = Kit.Ink, iconTint = Kit.Amber) {
        if (items.isEmpty()) {
            OverviewCompactEmpty("Nothing stands out", "Dishes that sell least show here, to help with the menu.", Icons.AutoMirrored.Outlined.TrendingDown)
            return@OverviewPanel
        }
        items.take(8).forEach { item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Amber.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.section ?: "", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${item.quantity} sold", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink)
                    Text(item.sales.money(currency), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                }
            }
        }
    }
}
