package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.components.dayCountText
import com.saporini.mobile_desktop.core.components.periodLabel
import com.saporini.mobile_desktop.core.files.csvRow
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.StatisticsScreenModel
import com.saporini.mobile_desktop.statistics.StatisticsState
import com.saporini.mobile_desktop.statistics.StatsView
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_reservations
import mobile_desktop.shared.generated.resources.salary_waiters
import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.workspace_statistics
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

// Statistics → Reports: every spreadsheet there is, for the days chosen at the top, in the same four groups as the
// tabs (one card each, with the tab's picture and colour). The server makes the sales, dishes, staff and payment
// reports; this device makes the rest from the figures the tabs show (MOCK where those are: stock, bookings, wages and
// the tax summary). Order details come with the production pass.

private enum class ReportMaker { SERVER, DEVICE, LATER }

private class ReportKind(
    val code: String, val title: String, val description: String, val icon: ImageVector, val maker: ReportMaker,
    // What the file holds, for the line under the description ("7 days", "4 people"); null when it can't be told.
    val holds: (StatsView) -> String? = { null }
)

private class ReportGroup(val title: String, val picture: DrawableResource, val tone: Color, val reports: List<ReportKind>)

private fun counted(count: Int, one: String, many: String = one + "s") = "$count ${if (count == 1) one else many}"

private val ReportGroups = listOf(
    ReportGroup("Sales & orders", Res.drawable.workspace_statistics, StatsSeries[0], listOf(
        ReportKind("daily-sales", "Sales per day", "Sales, orders and guests for every day", Icons.Outlined.CalendarMonth, ReportMaker.SERVER,
            { counted(it.dayCount, "day") }),
        ReportKind("hours", "Sales by hour and weekday", "Sales and orders for each hour of the day and each weekday", Icons.Outlined.AccessTime,
            ReportMaker.DEVICE, { "${counted(it.hours.count { h -> h.orders > 0 }, "hour")} and 7 weekdays" }),
        ReportKind("orders", "Order details", "Every order with its time, table, person, items and payments", Icons.AutoMirrored.Outlined.ReceiptLong,
            ReportMaker.LATER)
    )),
    ReportGroup("Dishes & stock", Res.drawable.settings_orders_kitchen, StatsSeries[2], listOf(
        ReportKind("items", "Dishes sold", "Every dish sold, how many and for how much", Icons.Outlined.RestaurantMenu, ReportMaker.SERVER,
            { "every dish sold" }),
        ReportKind("sections", "Menu sections", "Dishes sold and sales for each section of the menu", Icons.Outlined.DonutLarge, ReportMaker.DEVICE,
            { counted(it.sections.size, "section") }),
        ReportKind("stock", "Stock movement and waste", "What came in, what was used and thrown away each day, and the waste per item",
            Icons.Outlined.Inventory2, ReportMaker.DEVICE, { "${counted(it.dayCount, "day")} and ${counted(it.stock.wasteItems.size, "item")}" })
    )),
    ReportGroup("Payments & finance", Res.drawable.settings_payments, StatsSeries[1], listOf(
        ReportKind("payments", "Payments by method", "Money taken per payment method, with the tips", Icons.Outlined.Payments, ReportMaker.SERVER,
            { counted(it.methods.size, "method") }),
        ReportKind("discounts-refunds", "Discounts, refunds and voids", "Discounts, removed items, refunds and voids, per person and in all",
            Icons.Outlined.RemoveShoppingCart, ReportMaker.DEVICE, { counted(it.staff.size, "person", "people") }),
        ReportKind("tax", "Tax and accounting summary", "Sales before and after tax, the VAT, tips, refunds and payments",
            Icons.Outlined.AccountBalance, ReportMaker.DEVICE, { "for the accountant" })
    )),
    ReportGroup("Team & bookings", Res.drawable.salary_waiters, StatsSeries[3], listOf(
        ReportKind("staff", "Staff performance", "Orders, sales, tips, discounts, removals and refunds per person", Icons.Outlined.Badge, ReportMaker.SERVER,
            { counted(it.staff.size, "person", "people") }),
        ReportKind("labour", "Hours and labour cost", "Hours worked, the labour cost (estimated wages) and sales per hour, per person", Icons.Outlined.Schedule,
            ReportMaker.DEVICE, { counted(it.staff.size, "person", "people") }),
        ReportKind("bookings", "Bookings", "Bookings and guests per day, where they came from, no-shows and deposits", Icons.Outlined.EventSeat,
            ReportMaker.DEVICE, { counted(it.dayCount, "day") })
    ))
)

@Composable
internal fun ReportsTab(state: StatisticsState, model: StatisticsScreenModel, v: StatsView, size: ScreenSize) {
    val from = state.from
    val to = state.to
    SectionHeading(
        "Download a spreadsheet",
        (if (from != null && to != null) "Each covers ${periodLabel(from, to)} (${dayCountText(from, to)}); change the days at the top. "
        else "Each covers the days chosen at the top. ") + "They open in Excel, Numbers or Google Sheets.",
        Res.drawable.overview_reservations, StatsSeries[0]
    )
    if (size.isPhone) {
        ReportGroups.forEach { ReportGroupCard(it, state, model, v, size, Modifier.fillMaxWidth()) }
    } else {
        // Two groups a row, each row one height.
        ReportGroups.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { ReportGroupCard(it, state, model, v, size, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One group: its picture and name on a band of its colour, then a line per report. */
@Composable
private fun ReportGroupCard(group: ReportGroup, state: StatisticsState, model: StatisticsScreenModel, v: StatsView, size: ScreenSize, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(group.tone.copy(alpha = 0.13f), group.tone.copy(alpha = 0.04f), Color.Transparent)))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.9f)), Alignment.Center) {
                    Image(painterResource(group.picture), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(group.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink, maxLines = 1)
                    Text(counted(group.reports.size, "report"), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
                }
            }
            group.reports.forEachIndexed { index, kind ->
                if (index > 0) KitDivider(Modifier.padding(horizontal = 16.dp))
                ReportLine(kind, group.tone, state, model, v, size)
            }
        }
    }
}

@Composable
private fun ReportLine(kind: ReportKind, tone: Color, state: StatisticsState, model: StatisticsScreenModel, v: StatsView, size: ScreenSize) {
    // A server report the server doesn't offer (an older server) waits like the later ones.
    val maker = if (kind.maker == ReportMaker.SERVER && state.reports.none { it.code == kind.code }) ReportMaker.LATER else kind.maker
    val later = maker == ReportMaker.LATER
    val action: @Composable () -> Unit = {
        when (maker) {
            ReportMaker.SERVER -> DownloadButton(state.downloading == kind.code, state.downloading == null) { model.download(kind.code) }
            ReportMaker.DEVICE -> DownloadButton(false, state.overview != null) { model.offer(kind.code, deviceReport(kind.code, v)) }
            ReportMaker.LATER -> Text("Coming soon", Modifier.clip(RoundedCornerShape(50)).background(Kit.Canvas).padding(horizontal = 10.dp, vertical = 5.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = if (size.isPhone) Alignment.Top else Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background((if (later) Kit.Grey else tone).copy(alpha = 0.11f)), Alignment.Center) {
            Icon(kind.icon, null, Modifier.size(20.dp), tint = if (later) Kit.Faint else tone)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(kind.title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (later) Kit.Muted else Kit.Ink, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(kind.description, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!later) kind.holds(v)?.let { holds ->
                Text("CSV · $holds", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Faint, maxLines = 1)
            }
            if (size.isPhone) Box(Modifier.padding(top = 6.dp)) { action() }
        }
        if (!size.isPhone) action()
    }
}

/** The same small white button on every line: a download arrow, or a spinner while the server prepares the file. */
@Composable
private fun DownloadButton(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled && !busy, modifier = Modifier.height(36.dp), shape = RoundedCornerShape(8.dp),
        color = Color.White, border = BorderStroke(1.dp, Kit.Border)
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(14.dp), color = Kit.Green, strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Download, null, Modifier.size(17.dp), tint = if (enabled) Kit.Green else Kit.Faint)
            Text(if (busy) "Preparing…" else "Download", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp,
                color = if (enabled || busy) Kit.Ink else Kit.Faint, maxLines = 1)
        }
    }
}

/** The CSV of a report made on this device from what the tabs show. */
private fun deviceReport(code: String, v: StatsView): String {
    val money = { cents: Long -> "${if (cents < 0) "-" else ""}${abs(cents) / 100}.${(abs(cents) % 100).toString().padStart(2, '0')}" }
    val lines = mutableListOf<String>()
    when (code) {
        "hours" -> {
            lines += csvRow(listOf("Hour", "Sales", "Orders"))
            v.hours.filter { it.sales > 0 || it.orders > 0 }.forEach { lines += csvRow(listOf("${hourLabel(it.hour)}:00", money(it.sales), "${it.orders}")) }
            lines += ""
            lines += csvRow(listOf("Weekday", "Sales on an average day", "Sales", "Orders"))
            v.weekdays.forEach { lines += csvRow(listOf(weekdayName(it.day), money(it.averageSales), money(it.sales), "${it.orders}")) }
        }
        "sections" -> {
            val total = v.sections.sumOf { it.sales }.coerceAtLeast(1)
            lines += csvRow(listOf("Menu section", "Dishes sold", "Sales", "Share of sales"))
            v.sections.forEach { lines += csvRow(listOf(it.key, "${it.count}", money(it.sales), percent1(it.sales.toFloat() / total))) }
        }
        "discounts-refunds" -> {
            lines += csvRow(listOf("Person", "Discounts", "Items removed", "Removed value", "Refunds", "Voided orders"))
            v.staff.forEach { p -> lines += csvRow(listOf(p.name, money(p.discounts), "${p.removedItems}", money(p.removedValue), money(p.refunds), "${p.voidedOrders}")) }
            val t = v.totals
            lines += csvRow(listOf("In all", money(t.discounts), "${t.removedItems}", money(t.removedValue), money(t.refunds), "${t.voided}"))
            lines += csvRow(listOf("Cancelled orders", "${t.cancelled}"))
            lines += csvRow(listOf("Refunded orders", "${t.refundedOrders}"))
        }
        "labour" -> {
            lines += csvRow(listOf("Person", "Hours worked", "Hourly wage (estimated)", "Labour cost (estimated)", "Sales", "Sales per hour", "Labour cost of sales"))
            v.staff.forEach { p ->
                lines += csvRow(listOf(p.name, "${(p.hours * 10).roundToInt() / 10.0}", money(p.wage), money(p.laborCost), money(p.sales), money(p.salesPerHour),
                    if (p.sales > 0) percent1(p.laborCost.toFloat() / p.sales) else ""))
            }
        }
        "bookings" -> {
            val b = v.bookings
            lines += csvRow(listOf("Date", "Bookings", "Guests"))
            b.days.forEachIndexed { i, (date, count) -> lines += csvRow(listOf(date.toString(), "$count", "${b.guestsByDay.getOrNull(i)?.second ?: 0}")) }
            lines += ""
            lines += csvRow(listOf("Source", "Bookings"))
            b.sources.forEach { lines += csvRow(listOf(it.key.lowercase().replace('_', ' '), "${it.count}")) }
            lines += ""
            lines += csvRow(listOf("Arrived", "${b.arrived}"))
            lines += csvRow(listOf("Cancelled", "${b.cancelled}"))
            lines += csvRow(listOf("No-shows", "${b.noShows}"))
            lines += csvRow(listOf("Deposits taken", money(b.depositsTaken)))
            lines += csvRow(listOf("Deposits refunded", money(b.depositsRefunded)))
        }
        "tax" -> {
            val t = v.totals
            lines += csvRow(listOf("Line", "Amount"))
            lines += csvRow(listOf("Sales, tax included", money(t.sales)))
            lines += csvRow(listOf("VAT ${(v.vatRate * 100).toInt()}% in the sales", money(v.vat)))
            lines += csvRow(listOf("Sales before tax", money(v.salesBeforeTax)))
            lines += csvRow(listOf("Discounts given", money(t.discounts)))
            lines += csvRow(listOf("Tips", money(t.tips)))
            lines += csvRow(listOf("Refunds", money(t.refunds)))
            lines += csvRow(listOf("Money collected", money(t.collected)))
            lines += ""
            lines += csvRow(listOf("Payment method", "Collected", "Payments", "Tips"))
            v.methods.forEach { lines += csvRow(listOf(methodLabel(it.key), money(it.collected), "${it.count}", money(it.tips))) }
        }
        "stock" -> {
            val s = v.stock
            lines += csvRow(listOf("Date", "Received", "Used", "Wasted"))
            s.byDay.forEach { lines += csvRow(listOf(it.date.toString(), money(it.received), money(it.used), money(it.waste))) }
            lines += ""
            lines += csvRow(listOf("Wasted item", "Value"))
            s.wasteItems.forEach { lines += csvRow(listOf(it.key, money(it.sales))) }
            lines += ""
            lines += csvRow(listOf("Stock value", money(s.stockValue)))
        }
    }
    return "﻿" + lines.joinToString("\n") + "\n"
}
