package com.saporini.mobile_desktop.statistics.ui

import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import mobile_desktop.shared.generated.resources.Res
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
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.RoomService
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Percent
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
import com.saporini.mobile_desktop.core.components.RankBadge
import com.saporini.mobile_desktop.core.components.TableColumn
import com.saporini.mobile_desktop.core.components.TableHeader
import com.saporini.mobile_desktop.core.components.TrendBadge
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.StatsView

// Statistics → Menu & dishes: what guests buy. The dishes as a table (sold, sales, share, change, margin), the menu
// sections, best and slow sellers, how dishes go out (eat in, takeaway, delivery), each branch's favourites and the
// dishes on popularity × margin. Costs and margins are estimates until recipe costs are reported (MOCK, StatsView).

@Composable
internal fun DishesStockTab(v: StatsView, size: ScreenSize) {
    val dishes = v.dishes
    SectionHeading("Dishes", "What guests order, what sells slowly and what each dish earns", Res.drawable.settings_orders_kitchen, StatsSeries[2])
    val sold = dishes.sumOf { it.quantity }
    val sales = dishes.sumOf { it.sales }
    val profit = dishes.sumOf { it.profit }
    val best = dishes.maxByOrNull { it.quantity }
    TileGrid(listOf(
        { m -> StatTile("Dishes sold", countText(sold), m, detail = "${dishes.count { it.quantity > 0 }} different dishes", icon = Icons.Outlined.RestaurantMenu) },
        { m -> StatTile("Sales from dishes", v.money(sales), m, v.change(sales, v.compareDishSales.values.sum().takeIf { v.compareDishSales.isNotEmpty() }),
            icon = Icons.Outlined.Sell, tone = StatsSeries[1]) },
        { m -> StatTile("Average dish price", v.money(if (sold > 0) sales / sold else 0), m, detail = "what a dish sold for", icon = Icons.Outlined.LocalDining,
            tone = StatsSeries[2]) },
        { m -> StatTile("Best seller", best?.name ?: "–", m, detail = best?.let { "${it.quantity} sold · ${v.money(it.sales)}" }, icon = Icons.Outlined.EmojiEvents,
            tone = StatsSeries[4]) },
        { m -> StatTile("Profit on dishes", v.money(profit), m, detail = "sales less ingredients (estimated)", icon = Icons.Outlined.Savings) },
        { m -> StatTile("Food cost", if (sales > 0) percent1((sales - profit).toFloat() / sales) else "–", m, detail = "of what dishes sold for (estimated)",
            icon = Icons.Outlined.Percent, tone = Kit.Grey) }
    ), desktop = 6)
    DishTablePanel(v, size)
    PanelLine(size,
        1.3f to { m -> EngineeringPanel(v, m, size) },
        1f to { m -> PanelStack(m, listOf({ SectionsPanel(v, it) }, { TopAndSlowPanel(v, it) })) }
    )
    if (v.branches.size > 1) PanelLine(size,
        1f to { m -> HowTheyGoOutPanel(v, m) },
        1f to { m -> BranchDishesPanel(v, m) }
    ) else HowTheyGoOutPanel(v, Modifier.fillMaxWidth())
    StockSection(v, size)
}

@Composable
private fun DishTablePanel(v: StatsView, size: ScreenSize) {
    val dishes = v.dishes
    val total = dishes.sumOf { it.sales }.coerceAtLeast(1)
    StatsPanel(Icons.AutoMirrored.Outlined.MenuOpen, "Sales by dish", Modifier.fillMaxWidth(), empty = dishes.isEmpty(),
        emptyText = "Every dish sold, how many and for how much.") {
        val compact = !size.isDesktop
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas)) {
            TableHeader(if (compact) listOf(TableColumn("Dish", 2f), TableColumn("Sold", 0.7f, true), TableColumn("Sales", 1f, true))
            else listOf(TableColumn("#", 0.3f), TableColumn("Dish", 2f), TableColumn("Section", 1.1f), TableColumn("Sold", 0.6f, true),
                TableColumn("Sales", 0.9f, true), TableColumn("Share", 1.2f), TableColumn("Change", 0.8f), TableColumn("Margin", 0.7f, true)), trailingSpace = false)
        }
        dishes.take(15).forEachIndexed { index, dish ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!compact) Row(Modifier.weight(0.3f)) { RankBadge(index + 1) }
                Row(Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DishThumb(v, dish, 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(dish.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (compact) Text(dish.section ?: "", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    }
                }
                if (!compact) Text(dish.section ?: "–", Modifier.weight(1.1f), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text("${dish.quantity}", Modifier.weight(if (compact) 0.7f else 0.6f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = Kit.Ink, textAlign = TextAlign.End)
                Text(v.money(dish.sales), Modifier.weight(if (compact) 1f else 0.9f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = Kit.Ink, textAlign = TextAlign.End)
                if (!compact) {
                    val share = dish.sales.toFloat() / total
                    Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(StatsAccent.copy(alpha = 0.12f))) {
                            Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(StatsAccent))
                        }
                        Text(com.saporini.mobile_desktop.core.components.percentText(share), Modifier.width(34.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                    }
                    Row(Modifier.weight(0.8f)) { TrendBadge(v.change(dish.sales, v.compareDishSales[dish.name])) }
                    Text(percent1(dish.margin), Modifier.weight(0.7f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        color = if (dish.margin >= 0.7f) Kit.Green else Kit.Ink, textAlign = TextAlign.End)
                }
            }
            if (index < dishes.take(15).lastIndex) KitDivider()
        }
        if (dishes.size > 15) Footnote("The full list is in Reports → Dishes sold.")
    }
}

@Composable
private fun SectionsPanel(v: StatsView, modifier: Modifier) {
    val sections = v.sections
    StatsPanel(Icons.Outlined.DonutLarge, "Sales by menu section", modifier, empty = sections.isEmpty()) {
        // At most five colours; the rest fold into "Other".
        val top = sections.take(5)
        val rest = sections.drop(5)
        val parts = top.mapIndexed { i, s -> Part(s.key, s.key, s.sales.toFloat(), seriesColor(i), v.money(s.sales), "${s.count} sold") } +
            listOfNotNull(rest.takeIf { it.isNotEmpty() }?.let { r ->
                Part("OTHER", "Other", r.sumOf { it.sales }.toFloat(), StatsOther, v.money(r.sumOf { it.sales }), "${r.sumOf { it.count }} sold")
            })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Donut(parts, v.short(sections.sumOf { it.sales }), "from dishes", size = 128.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { parts.forEach { KeyRow(it.color, it.label, it.display, it.detail) } }
        }
    }
}

@Composable
private fun TopAndSlowPanel(v: StatsView, modifier: Modifier) {
    val top = v.dishes.sortedByDescending { it.quantity }.take(5)
    val slow = v.slowDishes.take(5)
    StatsPanel(Icons.Outlined.EmojiEvents, "Best and slow sellers", modifier, empty = top.isEmpty()) {
        RankBars(top.map { Triple(it.name, it.quantity.toFloat(), "${it.quantity} sold") }, details = top.map { v.money(it.sales) }, max = 5,
            leading = { DishThumb(v, top[it]) })
        if (slow.isNotEmpty()) {
            Row(Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.Icon(Icons.AutoMirrored.Outlined.TrendingDown, null, Modifier.width(15.dp), tint = Kit.Amber)
                Text("Selling slowly", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink)
            }
            slow.forEach { dish ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Amber.copy(alpha = 0.07f))
                    .padding(start = 8.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DishThumb(v, dish)
                    Column(Modifier.weight(1f)) {
                        Text(dish.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dish.section ?: "", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    }
                    Text(if (dish.quantity == 0L) "not sold" else "${dish.quantity} sold", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = Kit.Ink)
                }
            }
        }
    }
}

/** Popularity × margin: stars (sold a lot, earn well), workhorses, puzzles and dogs. Margins are estimates (MOCK). */
@Composable
private fun EngineeringPanel(v: StatsView, modifier: Modifier, size: ScreenSize) {
    val dishes = v.dishes.filter { it.quantity > 0 }.take(20)
    StatsPanel(Icons.Outlined.Insights, "Popularity and margin", modifier, empty = dishes.size < 3, note = "each dot a dish") {
        // The dots wear their menu section's colour, as in the sections ring.
        ChartLegend(v.sections.take(5).mapIndexed { i, s -> seriesColor(i) to s.key } + listOfNotNull((StatsOther to "Other").takeIf { v.sections.size > 5 }),
            Modifier.padding(horizontal = 4.dp))
        QuadrantChart(
            dishes.map { d -> Dot(d.name, d.quantity.toFloat(), d.margin, sectionColor(v, d.section),
                "${d.quantity} sold · ${percent1(d.margin)} margin · ${v.money(d.profit)} profit") },
            xName = "sold", yName = "margin", corners = listOf("Puzzles", "Stars", "Dogs", "Workhorses"),
            modifier = roomLeft(), height = if (size.isPhone) 260.dp else 300.dp, xFormat = { compactNumber(it) }, yFormat = { percent1(it) }
        )
        PanelNote("Stars sell a lot and earn well; workhorses sell but earn less; puzzles earn well but sell little. Margins use estimated ingredient costs.")
    }
}

/** A section's colour, the same as in the sections ring (the first five; the rest grey). */
internal fun sectionColor(v: StatsView, section: String?): androidx.compose.ui.graphics.Color {
    val index = v.sections.indexOfFirst { it.key == section }
    return if (index in 0..4) seriesColor(index) else StatsOther
}

/** The best sellers split by how they went out (MOCK until the server splits dish sales by order type). */
@Composable
private fun HowTheyGoOutPanel(v: StatsView, modifier: Modifier) {
    val dishes = v.dishes.sortedByDescending { it.quantity }.take(8)
    val types = v.orderTypes.map { it.key }.ifEmpty { listOf("DINE_IN", "TAKEAWAY", "DELIVERY") }
    StatsPanel(Icons.Outlined.RoomService, "How dishes go out", modifier, empty = dishes.isEmpty()) {
        ChartLegend(types.map { typeColor(it) to orderTypeLabel(it) }, Modifier.padding(horizontal = 4.dp))
        StackedColumnChart(dishes.map { it.name.take(10) }, types.map { orderTypeLabel(it) to typeColor(it) },
            dishes.map { d -> types.map { (d.byType[it] ?: 0L).toFloat() } }, roomLeft(), height = 230.dp, format = { "${it.toLong()} sold" },
            axisFormat = { compactNumber(it) }, titleOf = { dishes[it].name })
    }
}

/** Each branch's share of the best sellers. */
@Composable
private fun BranchDishesPanel(v: StatsView, modifier: Modifier) {
    val dishes = v.dishes.sortedByDescending { it.quantity }.take(10)
    val branches = v.branches
    StatsPanel(Icons.Outlined.Business, "Dishes by branch", modifier, empty = dishes.isEmpty()) {
        ChartLegend(branches.map { v.branchColor(it.id) to it.name }, Modifier.padding(horizontal = 4.dp))
        StackedColumnChart(dishes.map { it.name.take(10) }, branches.map { b -> b.name to v.branchColor(b.id) },
            dishes.map { d -> branches.map { b -> (b.dishes.firstOrNull { it.name == d.name }?.quantity ?: 0L).toFloat() } },
            roomLeft(), height = 230.dp, format = { "${it.toLong()} sold" }, axisFormat = { compactNumber(it) }, titleOf = { dishes[it].name })
    }
}
