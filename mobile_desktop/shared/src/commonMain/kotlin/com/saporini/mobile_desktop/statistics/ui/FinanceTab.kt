package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Icon
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.FinanceView
import com.saporini.mobile_desktop.statistics.StatsView
import kotlinx.datetime.LocalDate
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_stock_changes
import mobile_desktop.shared.generated.resources.inventory_stock_value
import mobile_desktop.shared.generated.resources.role_finance
import mobile_desktop.shared.generated.resources.shifts_planned
import kotlin.math.roundToLong

// Statistics → Finance (user, 2026-11-15: "I need a balance: a graph of money in and out; a simple switch at the top right
// like Inventory's: Balance, Money in, Money out; show everything that moved in and out"). First version, to talk about.
// Money in = what guests paid, by method. Money out = refunds, tips handed to the staff, wages, stock bought from suppliers
// and card fees. Balance = in less out.

/** The money's own colours: in green, out red. */
internal val MoneyInColor = Color(0xFF16A34A)
internal val MoneyOutColor = Color(0xFFDC2626)

/** One kind of money, day by day: a payment method coming in, or something going out. */
internal class MoneyKind(val key: String, val name: String, val detail: String, val icon: ImageVector, val color: Color, val byDay: List<Long>,
                         val countByDay: List<Long>? = null) {
    val total: Long = byDay.sum()
}

/**
 * Every day's money in and out of the period. Money in is real per method (what each took); its split by day is the pages'
 * MOCK (each day's sales shared out by method). Money out: refunds and tips are real totals, spread over the days by sales;
 * MOCK until the server and Salary/Inventory send them: wages (the staff's hours at their estimated wage), stock bought
 * (the Stock page's estimated deliveries) and card fees (1.4% of what wasn't cash).
 */
internal class MoneyFlow(v: StatsView) {
    val days: List<LocalDate> = v.days.map { it.date }
    private val sales = v.days.map { it.sales }
    private val totalSales = sales.sum().coerceAtLeast(1)
    private fun spread(total: Long): List<Long> = sales.map { (total * it.toDouble() / totalSales).roundToLong() }

    val moneyIn: List<MoneyKind> = v.methods.mapIndexed { index, m ->
        val raw = v.paymentsByDay.map { it.second.getOrElse(index) { 0L } }
        val sum = raw.sum().toDouble().takeIf { it > 0 } ?: 1.0
        MoneyKind(m.key, methodLabel(m.key), "${countText(m.count)} ${if (m.count == 1L) "payment" else "payments"}", methodIcon(m.key), methodColor(m.key),
            raw.map { (it * m.collected / sum).roundToLong() }, raw.map { (it * m.count / sum).roundToLong() })
    }.filter { it.total > 0 }

    val moneyOut: List<MoneyKind> = run {
        val t = v.totals
        val wages = v.staff.sumOf { it.laborCost }.takeIf { it > 0 } ?: (t.sales * 0.24).roundToLong()
        val stock = v.stock.byDay.map { it.received }
        val notCash = days.indices.map { i -> moneyIn.filter { it.key != "CASH" }.sumOf { it.byDay[i] } }
        listOf(
            MoneyKind("WAGES", "Wages", "the staff's hours worked", Icons.Outlined.Badge, StatsSeries[2], spread(wages)),
            MoneyKind("STOCK", "Stock from suppliers", "deliveries received", Icons.Outlined.Inventory2, StatsSeries[4],
                if (stock.size == days.size) stock else spread(stock.sum())),
            MoneyKind("TIPS", "Tips to the staff", "handed over from the payments", Icons.Outlined.VolunteerActivism, StatsSeries[3], spread(t.tips)),
            MoneyKind("REFUNDS", "Refunds", "given back to guests", Icons.AutoMirrored.Outlined.Undo, StatsSeries[5], spread(t.refunds)),
            MoneyKind("FEES", "Card fees", "1.4% of card and phone payments", Icons.Outlined.CreditCard, StatsSeries[1], notCash.map { (it * 0.014).roundToLong() })
        ).filter { it.total > 0 }
    }

    val inByDay: List<Long> = days.indices.map { i -> moneyIn.sumOf { it.byDay[i] } }
    val outByDay: List<Long> = days.indices.map { i -> moneyOut.sumOf { it.byDay[i] } }
    val totalIn: Long = inByDay.sum()
    val totalOut: Long = outByDay.sum()
    val balance: Long get() = totalIn - totalOut

    // The comparison: money in is real; money out takes refunds and tips as they were, the rest at the same share of sales.
    private val estimatedShare = moneyOut.filter { it.key in setOf("WAGES", "STOCK", "FEES") }.sumOf { it.total }.toDouble() / totalSales
    val compareIn: Long? = v.compareTotals?.collected
    val compareOut: Long? = v.compareTotals?.let { c -> c.refunds + c.tips + (c.sales * estimatedShare).roundToLong() }

    /** Days that kept more than went out, of the days anything moved. */
    val daysInPlus: Int = days.indices.count { inByDay[it] - outByDay[it] > 0 }
    val daysMoved: Int = days.indices.count { inByDay[it] > 0 || outByDay[it] > 0 }
}

/** The Finance page: Balance, Money in or Money out, as picked in the header's switch. */
@Composable
internal fun FinanceTab(v: StatsView, size: ScreenSize, view: FinanceView, onView: (FinanceView) -> Unit) {
    val flow = remember(v) { MoneyFlow(v) }
    when (view) {
        FinanceView.BALANCE -> BalanceView(v, flow, size, onView)
        FinanceView.MONEY_IN -> MoneyView(v, flow, size, incoming = true)
        FinanceView.MONEY_OUT -> MoneyView(v, flow, size, incoming = false)
    }
}

// ---- Balance ----

@Composable
private fun BalanceView(v: StatsView, flow: MoneyFlow, size: ScreenSize, onView: (FinanceView) -> Unit) {
    val compareBalance = if (flow.compareIn != null && flow.compareOut != null) flow.compareIn - flow.compareOut else null
    TileGrid(listOf(
        { m -> StatTile("Money in", v.money(flow.totalIn), m, v.change(flow.totalIn, flow.compareIn), detail = "everything guests paid",
            tone = MoneyInColor, picture = Res.drawable.role_finance, onClick = { onView(FinanceView.MONEY_IN) }) },
        { m -> StatTile("Money out", v.money(flow.totalOut), m, v.change(flow.totalOut, flow.compareOut), inverted = true,
            detail = "wages, stock, tips, refunds, fees", tone = MoneyOutColor, picture = Res.drawable.inventory_stock_changes,
            onClick = { onView(FinanceView.MONEY_OUT) }) },
        { m -> StatTile("Balance", (if (flow.balance < 0) "-" else "") + v.money(kotlin.math.abs(flow.balance)), m, v.change(flow.balance, compareBalance),
            detail = "money in less money out", tone = if (flow.balance >= 0) StatsSeries[0] else MoneyOutColor, picture = Res.drawable.inventory_stock_value) },
        { m -> StatTile("Days in the plus", "${flow.daysInPlus} of ${flow.daysMoved}", m, detail = "more came in than went out",
            tone = StatsSeries[1], picture = Res.drawable.shifts_planned) }
    ), desktop = 4)
    InOutGraphPanel(v, flow, size, Modifier.fillMaxWidth())
    PanelLine(size,
        1f to { m -> KindsPanel(v, flow.moneyIn, flow.totalIn, incoming = true, m) { onView(FinanceView.MONEY_IN) } },
        1f to { m -> KindsPanel(v, flow.moneyOut, flow.totalOut, incoming = false, m) { onView(FinanceView.MONEY_OUT) } }
    )
    // What was here before, for the accountant: from the bills to the money kept, the tax, and the check with the terminal.
    PanelLine(size,
        1.5f to { m -> BridgePanel(v, m) },
        1f to { m -> TaxPanel(v, m) }
    )
    ReconciliationPanel(v, Modifier.fillMaxWidth(), size)
}

/** Money in and money out, day by day, as two lines (the gap between them is the balance). */
@Composable
private fun InOutGraphPanel(v: StatsView, flow: MoneyFlow, size: ScreenSize, modifier: Modifier) {
    StatsPanel(Icons.AutoMirrored.Outlined.ShowChart, "Money in and out", modifier, empty = flow.totalIn == 0L && flow.totalOut == 0L, action = {
        CardDownload(v, "money-in-and-out") {
            CardReport("money-in-and-out", "Money in and out",
                listOf(ReportColumn("Date"), ReportColumn("Day"), ReportColumn("Money in", ColumnKind.MONEY), ReportColumn("Money out", ColumnKind.MONEY),
                    ReportColumn("Balance", ColumnKind.MONEY)),
                flow.days.mapIndexed { i, d -> listOf(shortDate(d, withYear = true), weekdayName(d.dayOfWeek), flow.inByDay[i], flow.outByDay[i], flow.inByDay[i] - flow.outByDay[i]) },
                listOf(ReportTotal("Money in", flow.totalIn), ReportTotal("Money out", flow.totalOut), ReportTotal("Balance", flow.balance)))
        }
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text((if (flow.balance < 0) "-" else "") + v.money(kotlin.math.abs(flow.balance)), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                color = if (flow.balance >= 0) Kit.Ink else MoneyOutColor, maxLines = 1)
            Text("balance in ${v.dayCount} ${if (v.dayCount == 1) "day" else "days"}", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
        }
        ChartLegend(listOf(MoneyInColor to "Money in", MoneyOutColor to "Money out"), Modifier.padding(horizontal = 4.dp), lines = true)
        LineChart(listOf(LineSeries("Money in", flow.inByDay.map { it / 100f }, MoneyInColor), LineSeries("Money out", flow.outByDay.map { it / 100f }, MoneyOutColor)),
            flow.days.map { dayLabel(it, flow.days.size) }, height = if (size.isPhone) 220.dp else 300.dp, steps = 5,
            format = { v.money((it * 100).toLong()) }, axisFormat = { v.axisMoney(it) },
            titleOf = { i ->
                val left = flow.inByDay[i] - flow.outByDay[i]
                "${weekdayDate(flow.days[i])} · balance ${if (left < 0) "-" else ""}${v.money(kotlin.math.abs(left))}"
            })
    }
}

/** Where the money came from (or went): each kind with its share and a bar; "See all" opens its page. */
@Composable
private fun KindsPanel(v: StatsView, kinds: List<MoneyKind>, total: Long, incoming: Boolean, modifier: Modifier, onSeeAll: () -> Unit) {
    val sorted = kinds.sortedByDescending { it.total }
    StatsPanel(if (incoming) Icons.AutoMirrored.Outlined.CallReceived else Icons.AutoMirrored.Outlined.CallMade,
        if (incoming) "Where the money came from" else "Where the money went", modifier, empty = sorted.isEmpty(),
        action = { SeeAllButton(onSeeAll) }) {
        HeroLine(v.money(total), if (incoming) "came in" else "went out", big = false)
        RankBars(sorted.map { Triple(it.name, it.total.toFloat(), (if (incoming) "" else "-") + v.money(it.total)) },
            details = sorted.map { k -> "${if (total > 0) (k.total * 100 + total / 2) / total else 0}% · ${k.detail}" }, colors = sorted.map { it.color },
            leading = { IconTile(sorted[it].icon, sorted[it].color) })
    }
}

@Composable
private fun SeeAllButton(onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("See all", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Green)
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(14.dp), tint = Kit.Green)
    }
}

// ---- Money in / Money out ----

/** A card per kind, the kinds day by day (stacked), then everything that moved, day by day, the newest first. */
@Composable
private fun MoneyView(v: StatsView, flow: MoneyFlow, size: ScreenSize, incoming: Boolean) {
    val kinds = (if (incoming) flow.moneyIn else flow.moneyOut).sortedByDescending { it.total }
    val total = if (incoming) flow.totalIn else flow.totalOut
    val before = if (incoming) flow.compareIn else flow.compareOut
    TileGrid(kinds.map { k ->
        { m: Modifier ->
            StatTile(k.name, (if (incoming) "" else "-") + v.money(k.total), m,
                if (incoming) v.change(k.total, v.compareMethods[k.key]) else null, inverted = !incoming,
                detail = "${if (total > 0) (k.total * 100 + total / 2) / total else 0}% · ${k.detail}", icon = k.icon, tone = k.color)
        }
    }, desktop = kinds.size.coerceIn(1, 5))
    val title = if (incoming) "Money in by day" else "Money out by day"
    StatsPanel(Icons.AutoMirrored.Outlined.ShowChart, title, Modifier.fillMaxWidth(), empty = total == 0L, action = {
        CardDownload(v, if (incoming) "money-in-by-day" else "money-out-by-day") {
            CardReport(if (incoming) "money-in-by-day" else "money-out-by-day", title,
                listOf(ReportColumn("Date")) + kinds.map { ReportColumn(it.name, ColumnKind.MONEY) } + ReportColumn("Total", ColumnKind.MONEY),
                flow.days.mapIndexed { i, d -> listOf<Any>(shortDate(d, withYear = true)) + kinds.map { it.byDay[i] } + kinds.sumOf { it.byDay[i] } },
                kinds.map { ReportTotal(it.name, it.total) } + ReportTotal("Total", total))
        }
    }) {
        HeroLine((if (incoming) "" else "-") + v.money(total), if (incoming) "came in" else "went out", v.change(total, before), inverted = !incoming, big = false)
        ChartLegend(kinds.map { it.color to it.name }, Modifier.padding(horizontal = 4.dp))
        StackedColumnChart(flow.days.map { dayLabel(it, flow.days.size) }, kinds.map { it.name to it.color },
            flow.days.indices.map { i -> kinds.map { it.byDay[i] / 100f } }, height = if (size.isPhone) 220.dp else 280.dp,
            format = { v.money((it * 100).toLong()) }, axisFormat = { v.axisMoney(it) }, titleOf = { weekdayDate(flow.days[it]) })
    }
    LedgerPanel(v, flow, kinds, incoming, Modifier.fillMaxWidth())
}

/**
 * Everything that moved (user, 2026-11-15: "show everything that moved in and out"), like a bank statement: a day at a
 * time, the newest first, its total, then a line per kind with how many and how much (+ in green, - in red).
 */
@Composable
private fun LedgerPanel(v: StatsView, flow: MoneyFlow, kinds: List<MoneyKind>, incoming: Boolean, modifier: Modifier) {
    val title = if (incoming) "Everything that came in" else "Everything that went out"
    val stem = if (incoming) "money-in-list" else "money-out-list"
    val days = flow.days.indices.filter { i -> kinds.any { it.byDay[i] > 0 } }.reversed()
    StatsPanel(Icons.AutoMirrored.Outlined.ReceiptLong, title, modifier, empty = days.isEmpty(), action = {
        CardDownload(v, stem) {
            val rows = days.reversed().flatMap { i ->
                kinds.filter { it.byDay[i] > 0 }.map { k ->
                    listOf<Any>(shortDate(flow.days[i], withYear = true), k.name, k.countByDay?.get(i)?.toString() ?: "", if (incoming) k.byDay[i] else -k.byDay[i])
                }
            }
            CardReport(stem, title, listOf(ReportColumn("Date"), ReportColumn("What"), ReportColumn("Payments"), ReportColumn("Amount", ColumnKind.MONEY)), rows,
                listOf(ReportTotal(if (incoming) "Money in" else "Money out", kinds.sumOf { it.total })))
        }
    }) {
        days.forEach { i ->
            val dayTotal = kinds.sumOf { it.byDay[i] }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(weekdayDate(flow.days[i]), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    Text((if (incoming) "+" else "-") + v.money(dayTotal), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = if (incoming) MoneyInColor else MoneyOutColor, maxLines = 1)
                }
                kinds.filter { it.byDay[i] > 0 }.forEach { k ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconTile(k.icon, k.color)
                        Column(Modifier.weight(1f)) {
                            Text(k.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1)
                            Text(k.countByDay?.get(i)?.let { n -> "${countText(n)} ${if (n == 1L) "payment" else "payments"}" } ?: k.detail,
                                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                        }
                        Text((if (incoming) "+" else "-") + v.money(k.byDay[i]), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = if (incoming) MoneyInColor else MoneyOutColor, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
