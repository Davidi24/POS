package com.saporini.mobile_desktop.statistics.ui

import mobile_desktop.shared.generated.resources.salary_waiters
import mobile_desktop.shared.generated.resources.Res
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VolunteerActivism
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
import com.saporini.mobile_desktop.core.components.WatchRow
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.shifts.ShiftAvatar
import com.saporini.mobile_desktop.statistics.PersonFigures
import com.saporini.mobile_desktop.statistics.StatsView

// Statistics → Staff: who sold what. Hours, sales per hour worked and the labour cost on top, then everyone side by
// side, the leaders in sales, tips and sales per hour, and who gave discounts, removed items or refunded. Wages and
// pay live in Admin Hub → Salary; the labour cost here is an estimate until it reads them (MOCK, StatsView).

@Composable
internal fun TeamBookingsTab(v: StatsView, size: ScreenSize) {
    SectionHeading("Team", "Who sold what, hours on the clock, sales per hour and the labour cost", Res.drawable.salary_waiters, StatsSeries[3])
    val people = v.staff
    val hours = people.sumOf { it.hours }
    val labor = people.sumOf { it.laborCost }
    val sales = people.sumOf { it.sales }
    TileGrid(listOf(
        { m -> StatTile("People who worked", countText(people.count { it.hours > 0 || it.orders > 0 }.toLong()), m, detail = "took orders or clocked in",
            icon = Icons.Outlined.Groups, tone = StatsSeries[3]) },
        { m -> StatTile("Hours worked", hoursText(hours), m, detail = "breaks taken off", icon = Icons.Outlined.Schedule, tone = StatsSeries[1]) },
        { m -> StatTile("Sales per hour worked", v.money(if (hours > 0.1) (sales / hours).toLong() else 0), m, detail = "everyone together", icon = Icons.Outlined.Speed) },
        { m -> StatTile("Labour cost", v.money(labor), m, detail = "hours × wage (estimated)", icon = Icons.Outlined.Savings, tone = StatsSeries[2]) },
        { m -> StatTile("Labour cost of sales", if (sales > 0) percent1(labor.toFloat() / sales) else "–", m, detail = "the lower, the better", inverted = true,
            icon = Icons.Outlined.Percent, tone = Kit.Grey) },
        { m -> StatTile("Tips", v.money(people.sumOf { it.tips }), m, detail = "on their orders", icon = Icons.Outlined.VolunteerActivism) }
    ), desktop = 6)
    LeaderboardPanel(v, people, size)
    PanelLine(size,
        1f to { m -> RankPanel(Icons.Outlined.Leaderboard, "Sales", people.sortedByDescending { it.sales }, m, v, StatsAccent, { it.sales }) { "${it.orders} orders" } },
        1f to { m -> RankPanel(Icons.Outlined.Speed, "Sales per hour worked", people.filter { it.hours > 0.5 }.sortedByDescending { it.salesPerHour }, m, v,
            StatsSeries[1], { it.salesPerHour }) { "${hoursText(it.hours)} worked" } },
        1f to { m -> RankPanel(Icons.Outlined.VolunteerActivism, "Tips", people.sortedByDescending { it.tips }, m, v, StatsSeries[4], { it.tips }) {
            if (it.sales > 0) "${percent1(it.tips.toFloat() / it.sales)} of sales" else "" } }
    )
    StaffWatchPanel(v, people, Modifier.fillMaxWidth())
    Footnote("Wages and paying people are in Admin Hub → Salary. The labour cost here uses each person's hours and an estimated wage for now.")
    BookingsSection(v, size)
}

@Composable
private fun LeaderboardPanel(v: StatsView, people: List<PersonFigures>, size: ScreenSize) {
    StatsPanel(Icons.Outlined.Badge, "Everyone side by side", Modifier.fillMaxWidth(), empty = people.isEmpty(), emptyText = "Each person's orders and sales show here.") {
        val compact = !size.isDesktop
        val columns = if (compact) listOf("Orders" to 0.6f, "Sales" to 1f, "Per hour" to 0.9f)
        else listOf("Orders" to 0.6f, "Sales" to 1f, "Average bill" to 0.9f, "Guests" to 0.6f, "Tips" to 0.8f, "Discounts" to 0.8f, "Removed" to 0.7f,
            "Refunds" to 0.8f, "Hours" to 0.6f, "Per hour" to 0.8f)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PERSON", Modifier.weight(1.8f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Kit.Muted)
            columns.forEach { (title, weight) ->
                Text(title.uppercase(), Modifier.weight(weight), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Kit.Muted,
                    textAlign = TextAlign.End, maxLines = 1)
            }
        }
        val top = people.maxOfOrNull { it.sales } ?: 0
        people.forEachIndexed { index, p ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1.8f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShiftAvatar(p.name, 30.dp)
                    Column {
                        Text(p.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p.sales == top && top > 0) Text("Most sales", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Green)
                    }
                }
                val cells = if (compact) listOf("${p.orders}", v.money(p.sales), v.money(p.salesPerHour))
                else listOf("${p.orders}", v.money(p.sales), v.money(p.averageBill), "${p.guests}", v.money(p.tips), v.money(p.discounts), "${p.removedItems}",
                    v.money(p.refunds), hoursText(p.hours), v.money(p.salesPerHour))
                cells.forEachIndexed { i, text ->
                    Text(text, Modifier.weight(columns[i].second), fontFamily = Inter(), fontWeight = if (i == 1) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp, color = Kit.Ink, textAlign = TextAlign.End, maxLines = 1)
                }
            }
            if (index < people.lastIndex) KitDivider()
        }
    }
}

@Composable
private fun RankPanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, people: List<PersonFigures>, modifier: Modifier, v: StatsView,
    color: androidx.compose.ui.graphics.Color, value: (PersonFigures) -> Long, detail: (PersonFigures) -> String
) {
    StatsPanel(icon, title, modifier, empty = people.isEmpty() || people.all { value(it) == 0L }) {
        RankBars(people.map { Triple(it.name, value(it).toFloat(), v.money(value(it))) }, details = people.map(detail), color = color, max = 6,
            leading = { ShiftAvatar(people[it].name, 36.dp) })
    }
}

/** Discounts, removed items, refunds and voided orders per person, the most first. */
@Composable
private fun StaffWatchPanel(v: StatsView, people: List<PersonFigures>, modifier: Modifier) {
    val watched = people.filter { it.discounts > 0 || it.removedItems > 0 || it.refunds > 0 || it.voidedOrders > 0 }
        .sortedByDescending { it.discounts + it.removedValue + it.refunds }
    StatsPanel(Icons.Outlined.Visibility, "Discounts, removals and refunds", modifier, empty = watched.isEmpty(),
        emptyText = "Nobody gave discounts, removed items or refunded in these days.") {
        watched.take(8).forEach { p ->
            WatchRow(Icons.Outlined.Sell, p.name, v.money(p.discounts + p.removedValue + p.refunds), Kit.Amber, null,
                listOfNotNull(
                    p.discounts.takeIf { it > 0 }?.let { "${v.money(it)} discounts" },
                    p.removedItems.takeIf { it > 0 }?.let { "$it ${if (it == 1L) "item" else "items"} removed" },
                    p.refunds.takeIf { it > 0 }?.let { "${v.money(it)} refunded" },
                    p.voidedOrders.takeIf { it > 0 }?.let { "$it voided" }
                ).joinToString(" · "))
        }
    }
}
