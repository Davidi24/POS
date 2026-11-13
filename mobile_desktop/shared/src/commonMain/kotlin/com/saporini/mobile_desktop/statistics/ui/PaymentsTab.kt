package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.material.icons.outlined.Receipt
import mobile_desktop.shared.generated.resources.inventory_stock_value
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.Res
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.format.decimalTextToCents
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.StatsView
import androidx.compose.foundation.layout.fillMaxHeight
import com.saporini.mobile_desktop.core.components.IconTile

// Statistics → Payments: how guests paid. The money and the number of payments per method, tips and refunds per
// method, every day's payments split by method (MOCK split, StatsView), each branch's methods, and a reconciliation:
// the app's total per method beside what the terminal or the bank says, with the difference.

@Composable
internal fun PaymentsFinanceTab(v: StatsView, size: ScreenSize) {
    SectionHeading("Payments", "How guests paid: methods, tips and refunds, by day and by branch", Res.drawable.settings_payments, StatsSeries[1])
    val t = v.totals
    val c = v.compareTotals
    val methods = v.methods
    val count = methods.sumOf { it.count }
    val cardShare = methods.filter { it.key != "CASH" }.sumOf { it.collected }.toFloat() / methods.sumOf { it.collected }.coerceAtLeast(1)
    TileGrid(listOf(
        { m -> StatTile("Money collected", v.money(t.collected), m, v.change(t.collected, c?.collected), icon = Icons.Outlined.AccountBalance, tone = StatsSeries[1]) },
        { m -> StatTile("Payments", countText(count), m, detail = "${methods.size} ${if (methods.size == 1) "method" else "methods"}", icon = Icons.Outlined.Payments) },
        { m -> StatTile("Average payment", v.money(if (count > 0) t.collected / count else 0), m, detail = "bill, tip and fees", icon = Icons.Outlined.Sell,
            tone = StatsSeries[2]) },
        { m -> StatTile("Tips", v.money(t.tips), m, v.change(t.tips, c?.tips), detail = if (t.sales > 0) "${percent1(t.tips.toFloat() / t.sales)} of sales" else null,
            icon = Icons.Outlined.VolunteerActivism) },
        { m -> StatTile("Refunds", v.money(t.refunds), m, v.change(t.refunds, c?.refunds), inverted = true, detail = "${t.refundedOrders} ${if (t.refundedOrders == 1L) "order" else "orders"}",
            icon = Icons.AutoMirrored.Outlined.Undo, tone = Kit.Grey) },
        { m -> StatTile("Paid by card", percent1(cardShare), m, detail = "everything but cash", icon = Icons.Outlined.CreditCard, tone = StatsSeries[1]) }
    ), desktop = 6)
    PanelLine(size,
        1f to { m -> MethodsPanel(v, m) },
        1.6f to { m -> PaymentsByDayPanel(v, m) }
    )
    PanelLine(size,
        1f to { m -> MethodCountsPanel(v, m) },
        1f to { m -> TipsByMethodPanel(v, m) },
        1f to { m -> RefundsByMethodPanel(v, m) }
    )
    if (v.branches.size > 1) BranchMethodsPanel(v, Modifier.fillMaxWidth())
    SectionHeading("Finance", "From the bills to the money kept, the tax in the sales, and a check against the terminal and the bank",
        Res.drawable.inventory_stock_value, StatsSeries[0])
    PanelLine(size,
        1.5f to { m -> BridgePanel(v, m) },
        1f to { m -> TaxPanel(v, m) }
    )
    ReconciliationPanel(v, Modifier.fillMaxWidth(), size)
}

/** From the bills (before discounts) to the money kept: discounts and refunds take away, tips add (a waterfall). */
@Composable
private fun BridgePanel(v: StatsView, modifier: Modifier) {
    val t = v.totals
    StatsPanel(Icons.Outlined.AccountBalance, "From the bills to the money kept", modifier, empty = t.sales == 0L) {
        HeroLine(v.money(v.moneyKept), "kept after refunds", v.change(v.moneyKept, v.compareTotals?.let { it.collected - it.refunds }))
        val steps = buildList {
            add(Step("Bills", (t.sales + t.discounts) / 100f, StepKind.TOTAL, "what the orders came to before discounts"))
            add(Step("Discounts", t.discounts / 100f, StepKind.DOWN, "taken off the bills"))
            add(Step("Tips", t.tips / 100f, StepKind.UP, "on top of the bills"))
            add(Step("Refunds", t.refunds / 100f, StepKind.DOWN, "given back"))
            if (kotlin.math.abs(v.otherMoney) > t.sales / 200) add(Step("Other", kotlin.math.abs(v.otherMoney) / 100f,
                if (v.otherMoney >= 0) StepKind.UP else StepKind.DOWN, "fees, rounding and bills not paid in full"))
            add(Step("Kept", v.moneyKept / 100f, StepKind.TOTAL, "the money that stayed"))
        }
        Waterfall(steps, height = 250.dp, format = { v.money((it * 100).toLong()) }, axisFormat = { v.axisMoney(it) })
    }
}

/** MOCK until the server reports tax per rate: the VAT included in the sales, worked out at 20%. */
@Composable
private fun TaxPanel(v: StatsView, modifier: Modifier) {
    val t = v.totals
    StatsPanel(Icons.Outlined.Receipt, "Tax summary", modifier, empty = t.sales == 0L, note = "for the accountant") {
        HeroLine(v.money(v.vat), "VAT ${(v.vatRate * 100).toInt()}% included in the sales", big = false)
        SplitBar(listOf(
            Part("NET", "Sales before tax", v.salesBeforeTax.toFloat(), StatsSeries[0], v.money(v.salesBeforeTax)),
            Part("VAT", "VAT ${(v.vatRate * 100).toInt()}%", v.vat.toFloat(), StatsSeries[1], v.money(v.vat))
        ), barHeight = 12.dp)
        FigureRow("Sales, tax included", v.money(t.sales), strong = true)
        FigureRow("Service charges", v.money(0))
        FigureRow("Discounts given", "-" + v.money(t.discounts), detail = "already off the sales")
        FigureRow("Tips", v.money(t.tips), detail = "not part of the sales")
        FigureRow("Refunds", "-" + v.money(t.refunds), valueColor = if (t.refunds > 0) Kit.Danger else Kit.Ink)
    }
}

/** MOCK until the server splits payments per day and method: every day's sales shared out by method. */
@Composable
private fun PaymentsByDayPanel(v: StatsView, modifier: Modifier) {
    val methods = v.methods
    StatsPanel(Icons.Outlined.CalendarMonth, "Payments by day", modifier, empty = methods.isEmpty()) {
        ChartLegend(methods.map { methodColor(it.key) to methodLabel(it.key) }, Modifier.padding(horizontal = 4.dp))
        StackedColumnChart(v.paymentsByDay.map { dayLabel(it.first, v.dayCount) }, methods.map { methodLabel(it.key) to methodColor(it.key) },
            v.paymentsByDay.map { (_, amounts) -> amounts.map { it / 100f } }, roomLeft(), height = 230.dp, format = { v.money((it * 100).toLong()) },
            axisFormat = { v.axisMoney(it) }, titleOf = { weekdayDate(v.paymentsByDay[it].first) })
    }
}

@Composable
private fun MethodCountsPanel(v: StatsView, modifier: Modifier) {
    val methods = v.methods.sortedByDescending { it.count }
    StatsPanel(Icons.Outlined.Payments, "Number of payments", modifier, empty = methods.isEmpty()) {
        HeroLine(countText(methods.sumOf { it.count }), "payments", big = false)
        RankBars(methods.map { Triple(methodLabel(it.key), it.count.toFloat(), "${it.count}") },
            details = methods.map { if (it.count > 0) "${v.money(it.collected / it.count)} each" else null }, colors = methods.map { methodColor(it.key) },
            leading = { IconTile(methodIcon(methods[it].key), methodColor(methods[it].key)) })
    }
}

@Composable
private fun TipsByMethodPanel(v: StatsView, modifier: Modifier) {
    val methods = v.methods.sortedByDescending { it.tips }
    StatsPanel(Icons.Outlined.VolunteerActivism, "Tips by method", modifier, empty = methods.all { it.tips == 0L }, emptyText = "Tips per method show here.") {
        HeroLine(v.money(v.totals.tips), "in tips", v.change(v.totals.tips, v.compareTotals?.tips), big = false)
        RankBars(methods.map { Triple(methodLabel(it.key), it.tips.toFloat(), v.money(it.tips)) },
            details = methods.map { if (it.collected > 0) "${percent1(it.tips.toFloat() / it.collected)} of it" else null }, colors = methods.map { methodColor(it.key) },
            leading = { IconTile(methodIcon(methods[it].key), methodColor(methods[it].key)) })
    }
}

@Composable
private fun RefundsByMethodPanel(v: StatsView, modifier: Modifier) {
    val methods = v.methods.filter { it.refunds > 0 }.sortedByDescending { it.refunds }
    StatsPanel(Icons.AutoMirrored.Outlined.Undo, "Refunds", modifier, empty = methods.isEmpty(), emptyText = "No refunds in these days.") {
        HeroLine("-" + v.money(v.totals.refunds), "${v.totals.refundedOrders} ${if (v.totals.refundedOrders == 1L) "order" else "orders"} refunded", v.change(v.totals.refunds, v.compareTotals?.refunds), inverted = true,
            big = false)
        RankBars(methods.map { Triple(methodLabel(it.key), it.refunds.toFloat(), "-" + v.money(it.refunds)) }, colors = methods.map { methodColor(it.key) },
            leading = { IconTile(methodIcon(methods[it].key), methodColor(methods[it].key)) })
    }
}

/** A bar per branch, as long as what it took and split by how it was paid. */
@Composable
private fun BranchMethodsPanel(v: StatsView, modifier: Modifier) {
    val methods = v.methods.map { it.key }
    val most = v.branches.maxOfOrNull { b -> b.methods.sumOf { it.collected } }?.takeIf { it > 0 } ?: 1L
    StatsPanel(Icons.Outlined.Business, "Payments by branch", modifier, note = "how each branch was paid") {
        ChartLegend(methods.map { methodColor(it) to methodLabel(it) }, Modifier.padding(horizontal = 4.dp))
        v.branches.forEach { b ->
            val total = b.methods.sumOf { it.collected }
            Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    Text("${b.methods.sumOf { it.count }} payments", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    Spacer(Modifier.width(10.dp))
                    Text(v.money(total), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                }
                Row(Modifier.fillMaxWidth(total.toFloat() / most).height(14.dp).clip(RoundedCornerShape(50)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    methods.forEach { key ->
                        val part = b.methods.firstOrNull { it.key == key }?.collected ?: 0L
                        if (part > 0) Box(Modifier.weight(part.toFloat()).fillMaxHeight().background(methodColor(key)))
                    }
                }
                if (total > 0) Text(methods.mapNotNull { key ->
                    (b.methods.firstOrNull { it.key == key }?.collected ?: 0L).takeIf { it > 0 }?.let { "${methodLabel(key)} ${percent1(it.toFloat() / total)}" }
                }.joinToString("  ·  "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
            }
        }
    }
}

/**
 * The app's total per method beside what the card terminal's day-end report or the bank says (typed in here, kept
 * only on this screen for now), and the difference: green when they match, amber when they don't.
 */
@Composable
private fun ReconciliationPanel(v: StatsView, modifier: Modifier, size: ScreenSize) {
    val typed = remember(v.from, v.to, v.state.branchId) { mutableStateMapOf<String, String>() }
    val methods = v.methods
    StatsPanel(Icons.Outlined.Balance, "Check against the terminal and the bank", modifier, empty = methods.isEmpty()) {
        PanelNote("Type the totals from the card terminal's day-end report or the bank statement for these days. Differences show at once.")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Method" to 1.3f, "In the app" to 1f, "Terminal or bank" to 1.2f, "Difference" to 1.1f).forEach { (title, weight) ->
                Text(title.uppercase(), Modifier.weight(weight), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Kit.Muted,
                    textAlign = if (title == "Method") TextAlign.Start else TextAlign.End, maxLines = 1)
            }
        }
        methods.forEachIndexed { index, m ->
            val counted = typed[m.key]?.let(::decimalTextToCents)
            val difference = counted?.let { it - m.collected }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(methodColor(m.key)))
                    Text(methodLabel(m.key), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                }
                Text(v.money(m.collected), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink,
                    textAlign = TextAlign.End, maxLines = 1)
                Box(Modifier.weight(1.2f), contentAlignment = Alignment.CenterEnd) {
                    BasicTextField(
                        typed[m.key].orEmpty(), { raw -> typed[m.key] = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.').take(12) },
                        Modifier.width(if (size.isPhone) 90.dp else 130.dp).clip(RoundedCornerShape(8.dp)).background(Kit.Canvas)
                            .border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.End),
                        cursorBrush = SolidColor(Kit.Green),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterEnd) {
                                if (typed[m.key].isNullOrEmpty()) Text("0.00", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Faint)
                                inner()
                            }
                        }
                    )
                }
                Row(Modifier.weight(1.1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    when {
                        difference == null -> Text("–", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Faint)
                        difference == 0L -> {
                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(15.dp), tint = Kit.Green)
                            Text("  Matches", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
                        }
                        else -> {
                            Icon(Icons.Outlined.ReportProblem, null, Modifier.size(15.dp), tint = Kit.Amber)
                            Text("  ${if (difference > 0) "+" else ""}${v.money(difference)}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                color = Kit.Amber)
                        }
                    }
                }
            }
            if (index < methods.lastIndex) KitDivider()
        }
        val counted = methods.mapNotNull { m -> typed[m.key]?.let(::decimalTextToCents)?.let { it - m.collected } }
        if (counted.isNotEmpty()) FigureRow("Difference in all", (if (counted.sum() > 0) "+" else "") + v.money(counted.sum()),
            valueColor = if (counted.sum() == 0L) Kit.Green else Kit.Amber, strong = true, detail = "${counted.size} of ${methods.size} methods checked")
    }
}
