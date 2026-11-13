package com.saporini.mobile_desktop.statistics.ui

import mobile_desktop.shared.generated.resources.inventory_stock_value
import mobile_desktop.shared.generated.resources.Res
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.PriceCheck
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.StatsView
import kotlin.math.roundToInt

// Statistics → Inventory & costs: what the stock is worth, what came in and what was used (the cost of what was sold),
// waste, returns and counts, the food cost against a 30% target, the margin per dish and what went to each kind of
// supplier. MOCK until the inventory statistics exist: built around the real sales (StatsView.stock).

private const val FOOD_COST_TARGET = 0.30f

@Composable
internal fun StockSection(v: StatsView, size: ScreenSize) {
    val s = v.stock
    val sales = v.totals.sales
    SectionHeading("Stock & costs", "What the stock is worth, what came in and went out, waste and the food cost", Res.drawable.inventory_stock_value,
        StatsSeries[1])
    TileGrid(listOf(
        { m -> StatTile("Stock value", v.money(s.stockValue), m, detail = "at cost, today", icon = Icons.Outlined.Warehouse, tone = StatsSeries[1]) },
        { m -> StatTile("Received", v.money(s.received), m, detail = "deliveries in", icon = Icons.Outlined.LocalShipping) },
        { m -> StatTile("Used", v.money(s.used), m, v.change(s.used, s.compareUsed), detail = "cost of what was sold", icon = Icons.Outlined.RestaurantMenu,
            tone = StatsSeries[2]) },
        { m -> StatTile("Waste", v.money(s.waste), m, inverted = true, detail = "${percent1(s.waste.toFloat() / s.used.coerceAtLeast(1))} of what was used",
            icon = Icons.Outlined.DeleteOutline, tone = Kit.Grey) },
        { m -> StatTile("Food cost", percent1(v.foodCost), m, inverted = true, detail = "target ${percent1(FOOD_COST_TARGET)}", icon = Icons.Outlined.Percent,
            tone = if (v.foodCost <= FOOD_COST_TARGET) StatsAccent else Kit.Amber) },
        { m -> StatTile("Gross margin", v.money(sales - s.used), m, detail = if (sales > 0) "${percent1((sales - s.used).toFloat() / sales)} of sales" else null,
            icon = Icons.Outlined.Savings) }
    ), desktop = 6)
    PanelLine(size,
        1.6f to { m -> StockByDayPanel(v, m, size) },
        1f to { m -> FoodCostPanel(v, m) }
    )
    PanelLine(size,
        1f to { m -> WastePanel(v, m) },
        1f to { m -> SuppliersPanel(v, m) },
        1f to { m -> MovementsPanel(v, m) }
    )
    DishMarginsPanel(v, Modifier.fillMaxWidth(), size)
}

/** What came in, what was used and what was thrown away, day by day, on one money axis. */
@Composable
private fun StockByDayPanel(v: StatsView, modifier: Modifier, size: ScreenSize) {
    val days = v.stock.byDay
    StatsPanel(Icons.Outlined.QueryStats, "Stock in and out", modifier, empty = days.all { it.used == 0L && it.received == 0L }) {
        val series = listOf(
            LineSeries("Received", days.map { it.received / 100f }, StatsSeries[1]),
            LineSeries("Used", days.map { it.used / 100f }, StatsSeries[2]),
            LineSeries("Wasted", days.map { it.waste / 100f }, StatsSeries[5])
        )
        ChartLegend(series.map { it.color to it.name }, Modifier.padding(horizontal = 4.dp), lines = true)
        LineChart(series, days.map { dayLabel(it.date, v.dayCount) }, roomLeft(), height = if (size.isPhone) 200.dp else 240.dp, format = { v.money((it * 100).toLong()) },
            axisFormat = { v.axisMoney(it) }, titleOf = { weekdayDate(days[it].date) })
        PanelNote("Deliveries come on Mondays, Wednesdays and Fridays, so what's received jumps on those days.")
    }
}

@Composable
private fun FoodCostPanel(v: StatsView, modifier: Modifier) {
    val s = v.stock
    StatsPanel(Icons.Outlined.Percent, "Food cost", modifier, empty = v.totals.sales == 0L) {
        HeroLine(percent1(v.foodCost), "of sales went on ingredients", v.change(s.used, s.compareUsed), inverted = true)
        Meter(v.foodCost / 0.5f, if (v.foodCost <= FOOD_COST_TARGET) StatsAccent else Kit.Amber, height = 10.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("0%", Modifier.weight(1f), fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted)
            Text("Target ${percent1(FOOD_COST_TARGET)}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Muted)
            Text("50%", Modifier.weight(1f), fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, textAlign = TextAlign.End)
        }
        FigureRow("Sales", v.money(v.totals.sales))
        FigureRow("Cost of what was sold", "-" + v.money(s.used), valueColor = Kit.Danger)
        FigureRow("Gross margin", v.money(v.totals.sales - s.used), strong = true, valueColor = Kit.Green)
        // Each day's food cost: green on target, amber above it.
        val perDay = s.byDay.zip(v.days)
        if (perDay.size > 1) {
            Text("Food cost by day", Modifier.padding(start = 4.dp, top = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = Kit.Muted)
            ColumnChart(perDay.map { (stock, day) ->
                val share = if (day.sales > 0) stock.used.toFloat() / day.sales else 0f
                ColumnDatum(dayLabel(day.date, v.dayCount), share, "${percent1(share)} · ${v.money(stock.used)} of ${v.money(day.sales)}",
                    color = if (share <= FOOD_COST_TARGET) StatsAccent else Kit.Amber)
            }, roomLeft(), height = 110.dp, axisFormat = { "${(it * 100).roundToInt()}%" })
        }
        PanelNote(if (v.foodCost <= FOOD_COST_TARGET) "On target: ingredients cost less than ${percent1(FOOD_COST_TARGET)} of sales."
            else "Above target: check portions, waste and the prices of the dishes that cost most.")
    }
}

@Composable
private fun WastePanel(v: StatsView, modifier: Modifier) {
    val items = v.stock.wasteItems
    StatsPanel(Icons.Outlined.DeleteOutline, "Waste", modifier, empty = items.all { it.sales == 0L }, note = "most thrown away") {
        HeroLine("-" + v.money(v.stock.waste), "thrown away", big = false)
        RankBars(items.map { Triple(it.key, it.sales.toFloat(), v.money(it.sales)) }, color = StatsSeries[5], max = 6)
    }
}

@Composable
private fun SuppliersPanel(v: StatsView, modifier: Modifier) {
    val suppliers = v.stock.suppliers
    StatsPanel(Icons.Outlined.LocalShipping, "Spent with suppliers", modifier, empty = suppliers.all { it.sales == 0L }, note = "by kind") {
        HeroLine(v.money(suppliers.sumOf { it.sales }), "on deliveries", big = false)
        RankBars(suppliers.map { Triple(it.key, it.sales.toFloat(), v.money(it.sales)) }, color = StatsSeries[1], max = 7)
    }
}

@Composable
private fun MovementsPanel(v: StatsView, modifier: Modifier) {
    val s = v.stock
    StatsPanel(Icons.Outlined.SwapVert, "Stock movements", modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FigureRow("Received", v.money(s.received), detail = "deliveries booked in")
            FigureRow("Used for dishes", "-" + v.money(s.used), detail = "taken off by sales")
            FigureRow("Wasted", "-" + v.money(s.waste), detail = "thrown away", valueColor = Kit.Danger)
            FigureRow("Returned to suppliers", "-" + v.money(s.returns), detail = "sent back")
            FigureRow("Count corrections", (if (s.adjustments >= 0) "+" else "") + v.money(s.adjustments), detail = "after stock counts")
            KitDivider()
            FigureRow("Stock value now", v.money(s.stockValue), strong = true)
        }
    }
}

/** What each dish sells for, what its ingredients cost and what's left (estimated costs, MOCK). */
@Composable
private fun DishMarginsPanel(v: StatsView, modifier: Modifier, size: ScreenSize) {
    val dishes = v.dishes.filter { it.quantity > 0 }.sortedByDescending { it.profit }.take(12)
    StatsPanel(Icons.Outlined.PriceCheck, "Cost and margin by dish", modifier, empty = dishes.isEmpty(), note = "estimated costs") {
        val compact = !size.isDesktop
        val columns = if (compact) listOf("Price" to 0.8f, "Margin" to 0.8f, "Profit" to 1f)
        else listOf("Price" to 0.8f, "Cost" to 0.8f, "Margin" to 0.8f, "Sold" to 0.6f, "Profit" to 1f)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("DISH", Modifier.weight(1.8f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Kit.Muted)
            columns.forEach { (title, weight) ->
                Text(title.uppercase(), Modifier.weight(weight), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Kit.Muted,
                    textAlign = TextAlign.End)
            }
        }
        dishes.forEachIndexed { index, d ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1.8f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DishThumb(v, d, 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d.section ?: "", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    }
                }
                val cost = if (d.quantity > 0) d.cost / d.quantity else 0
                val cells = if (compact) listOf(v.money(d.price), percent1(d.margin), v.money(d.profit))
                else listOf(v.money(d.price), v.money(cost), percent1(d.margin), "${d.quantity}", v.money(d.profit))
                cells.forEachIndexed { i, text ->
                    val isMargin = columns[i].first == "Margin"
                    Text(text, Modifier.weight(columns[i].second), fontFamily = Inter(), fontWeight = if (i == cells.lastIndex) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp, color = if (isMargin && d.margin >= 0.7f) Kit.Green else Kit.Ink, textAlign = TextAlign.End, maxLines = 1)
                }
            }
            if (index < dishes.lastIndex) KitDivider()
        }
    }
}
