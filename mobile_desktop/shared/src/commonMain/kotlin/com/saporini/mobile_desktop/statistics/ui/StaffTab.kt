package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.ChartPalette
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.InitialsAvatar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KpiCard
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PanelRow
import com.saporini.mobile_desktop.core.components.RankBadge
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.percentText
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.data.StaffStatsDto
import com.saporini.mobile_desktop.statistics.data.StatsStaffDto
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.overview_next_hours
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.workspace_statistics

private enum class StaffMeasure(val label: String) { SALES("Sales"), ORDERS("Orders"), TIPS("Tips"), PER_HOUR("Per hour") }

private fun StaffStatsDto.hours(): Double = hoursWorked.value.toDoubleOrNull() ?: 0.0
private fun StaffStatsDto.perHourCents(): Long = hours().takeIf { it > 0.1 }?.let { (sales.cents() / it).toLong() } ?: 0L

@Composable
internal fun StaffTab(data: StatsStaffDto, size: ScreenSize) {
    val currency = data.period.currency
    val people = data.staff
    if (people.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), Alignment.Center) {
            OverviewEmpty("No one worked in these days", "Each person's orders, sales and tips show here.", Icons.Outlined.Groups, image = Res.drawable.overview_guests)
        }
        return
    }
    val top = people.maxBy { it.sales.cents() }
    val tips = people.sumOf { it.tips.cents() }
    val hours = people.sumOf { it.hours() }
    val sales = people.sumOf { it.sales.cents() }
    SummaryCardRow(size, listOf(
        { m -> KpiCard("Team", "${people.size}", "${people.count { it.orders > 0 }} took orders", Kit.Green, m, image = Res.drawable.overview_guests) },
        { m -> KpiCard("Top seller", top.name, "${top.sales.money(currency)} from ${top.orders} orders", Kit.Amber, m, image = Res.drawable.workspace_statistics) },
        { m -> KpiCard("Tips", moneyText(tips, currency), if (sales > 0) "${percentText(tips.toFloat() / sales)} of sales" else "no sales",
            Kit.Blue, m, image = Res.drawable.settings_payments) },
        { m -> KpiCard("Hours worked", hoursText(hours),
            if (hours > 0.1) "${moneyText((sales / hours).toLong(), currency)} sales per hour" else "no clock-ins", Kit.Purple, m,
            image = Res.drawable.overview_next_hours) }
    ))
    PanelRow(size,
        2f to { m -> LeaderboardPanel(people, currency, size, m) },
        1f to { m -> StaffWatchPanel(people, currency, m) }
    )
}

@Composable
private fun LeaderboardPanel(people: List<StaffStatsDto>, currency: String, size: ScreenSize, modifier: Modifier) {
    var measure by remember { mutableStateOf(StaffMeasure.SALES) }
    val value: (StaffStatsDto) -> Long = {
        when (measure) {
            StaffMeasure.SALES -> it.sales.cents()
            StaffMeasure.ORDERS -> it.orders
            StaffMeasure.TIPS -> it.tips.cents()
            StaffMeasure.PER_HOUR -> it.perHourCents()
        }
    }
    val shown: (StaffStatsDto) -> String = {
        when (measure) {
            StaffMeasure.SALES -> it.sales.money(currency)
            StaffMeasure.ORDERS -> "${it.orders}"
            StaffMeasure.TIPS -> it.tips.money(currency)
            StaffMeasure.PER_HOUR -> if (it.hours() > 0.1) "${moneyText(it.perHourCents(), currency)}/h" else "–"
        }
    }
    val ranked = people.sortedByDescending(value)
    val best = ranked.maxOfOrNull(value)?.coerceAtLeast(1) ?: 1
    OverviewPanel(Icons.Outlined.EmojiEvents, "Leaderboard", modifier, action = {
        FilterPills(StaffMeasure.entries.map { it to it.label }, measure, { measure = it })
    }) {
        ranked.forEachIndexed { index, person ->
            val color = ChartPalette[index % ChartPalette.size]
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (index == 0) Kit.GreenSoft.copy(alpha = 0.6f) else Color(0xFFF8F9F7))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RankBadge(index + 1)
                InitialsAvatar(person.name, color = color, size = 36.dp)
                Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(person.name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(shown(person), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))) {
                        Box(Modifier.fillMaxWidth((value(person).toFloat() / best).coerceIn(0f, 1f)).fillMaxHeight().background(color))
                    }
                    Text(listOf("${person.orders} orders", "${person.guests} guests", hoursText(person.hoursWorked)).joinToString(" · "),
                        fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                }
                if (!size.isPhone) {
                    StaffFigure("Average bill", person.averageTicket.money(currency), Modifier.width(92.dp))
                    StaffFigure("Tips", person.tips.money(currency), Modifier.width(80.dp))
                }
            }
        }
    }
}

@Composable
private fun StaffFigure(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, textAlign = TextAlign.End)
        Text(label, fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, maxLines = 1)
    }
}

/** People whose discounts, removals or refunds stand out: worth a word, not proof of anything. */
@Composable
private fun StaffWatchPanel(people: List<StaffStatsDto>, currency: String, modifier: Modifier) {
    val flagged = people.map { it to (it.discounts.cents() + it.removedValue.cents() + it.refunds.cents()) }
        .filter { (person, total) -> total > 0 || person.voidedOrders > 0 }.sortedByDescending { it.second }
    OverviewPanel(Icons.Outlined.Visibility, "Worth a look", modifier, titleExtra = "money taken off") {
        if (flagged.isEmpty()) {
            OverviewCompactEmpty("All clear", "No discounts, removals or refunds in these days.", Icons.Outlined.Visibility)
            return@OverviewPanel
        }
        flagged.take(6).forEach { (person, total) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8F9F7)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(person.name, color = Kit.Amber, size = 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(person.name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(moneyText(total, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Danger)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (person.discounts.cents() > 0) WatchChip(Icons.Outlined.LocalOffer, person.discounts.money(currency), Kit.Amber)
                    if (person.removedItems > 0) WatchChip(Icons.Outlined.RemoveShoppingCart, "${person.removedItems} · ${person.removedValue.money(currency)}", Kit.Purple)
                    if (person.refunds.cents() > 0) WatchChip(Icons.AutoMirrored.Outlined.Undo, person.refunds.money(currency), Kit.Danger)
                    if (person.voidedOrders > 0) WatchChip(Icons.Outlined.RemoveShoppingCart, "${person.voidedOrders} voided", Kit.Grey)
                }
            }
        }
    }
}

@Composable
private fun WatchChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(12.dp), tint = color)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = color, maxLines = 1)
    }
}
