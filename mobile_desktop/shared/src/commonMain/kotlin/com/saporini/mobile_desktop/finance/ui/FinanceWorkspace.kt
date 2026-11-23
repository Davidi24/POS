package com.saporini.mobile_desktop.finance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.BarChart
import com.saporini.mobile_desktop.core.components.BarDatum
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PanelRow
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.format.shortMoney
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.money.ShownMoney
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.finance.DeadlineKind
import com.saporini.mobile_desktop.finance.FilingState
import com.saporini.mobile_desktop.finance.FinanceBook
import com.saporini.mobile_desktop.finance.FinanceTab
import com.saporini.mobile_desktop.finance.TaxDeadline
import com.saporini.mobile_desktop.finance.firstOf
import com.saporini.mobile_desktop.finance.monthTitle
import com.saporini.mobile_desktop.finance.pct
import com.saporini.mobile_desktop.finance.share
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_stock_value
import mobile_desktop.shared.generated.resources.role_finance
import mobile_desktop.shared.generated.resources.salary_managers
import mobile_desktop.shared.generated.resources.settings_payments

/** What every page needs: the currency, the window's size and a way to another place. */
internal class Fx(val currency: String?, val size: ScreenSize, val wide: Boolean, val onTab: (FinanceTab) -> Unit, val say: (String) -> Unit) {
    fun money(cents: Long) = moneyText(cents, currency)
    fun short(cents: Long) = shortMoney(cents, currency)
    val phone: Boolean get() = size.isPhone
}

/**
 * The Finance & taxation workspace (user, 2026-11-23: "the app helps doing the restaurant's taxes, no need to hire
 * someone"): Overview, VAT, Expenses, Payroll, Deadlines, Tax returns and Tax setup. First round: screens on MOCK figures
 * ([FinanceBook]).
 */
@Composable
internal fun FinanceWorkspace(tab: FinanceTab, onTab: (FinanceTab) -> Unit, modifier: Modifier = Modifier) {
    var notice by remember { mutableStateOf<String?>(null) }
    // A branch's own records, or every branch together (step 25).
    com.saporini.mobile_desktop.workspace.ui.FollowBranchScope(com.saporini.mobile_desktop.core.session.Workspace.FINANCE) { FinanceBook.branchId = it }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        val fx = Fx(ShownMoney.restaurantCurrency, size, maxWidth >= 900.dp, onTab) { notice = it }
        val side = if (size.isPhone) 14.dp else 22.dp
        if (tab == FinanceTab.SETUP) {
            TaxSetupPage(fx, Modifier.fillMaxSize().padding(horizontal = side, vertical = if (size.isPhone) 12.dp else 18.dp))
        } else {
            ScrollColumn(Modifier.fillMaxSize().padding(horizontal = side, vertical = if (size.isPhone) 12.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (tab) {
                    FinanceTab.OVERVIEW -> OverviewPage(fx)
                    FinanceTab.VAT -> VatPage(fx)
                    FinanceTab.EXPENSES -> ExpensesPage(fx)
                    FinanceTab.PAYROLL -> PayrollPage(fx)
                    FinanceTab.DEADLINES -> DeadlinesPage(fx)
                    FinanceTab.FILING -> FilingPage(fx)
                    FinanceTab.SETUP -> {}
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        if (tab == FinanceTab.EXPENSES) AddExpenseTab(fx, maxHeight)
    }
    ScreenMessages(null, notice, { notice = null })
}

// ---- Pieces every page uses ----

/** ‹ November 2026 ›: the month the pages show (not past this month). */
@Composable
internal fun MonthSwitcher(compact: Boolean = false) {
    val month = FinanceBook.month
    val atNow = month >= firstOf(FinanceBook.today)
    Row(Modifier.height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clickable { FinanceBook.shiftMonth(-1) }, Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Month before", Modifier.size(20.dp), tint = Kit.Ink)
        }
        Text(monthTitle(month), Modifier.widthIn(min = if (compact) 110.dp else 140.dp).padding(horizontal = 4.dp), fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.size(40.dp).then(if (atNow) Modifier else Modifier.clickable { FinanceBook.shiftMonth(1) }), Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Month after", Modifier.size(20.dp), tint = if (atNow) Kit.Border else Kit.Ink)
        }
    }
}

/** The colour and look of a filing state. */
internal fun stateColor(state: FilingState): Color = when (state) {
    FilingState.NOT_YET -> Kit.Grey
    FilingState.READY -> Kit.Amber
    FilingState.FILED -> Kit.Blue
    FilingState.PAID -> Color(0xFF16A34A)
}

@Composable
internal fun StatePill(state: FilingState) {
    val color = stateColor(state)
    Row(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(state.label, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = color, maxLines = 1)
    }
}

/** A calendar leaf: the day big, the month small. */
@Composable
internal fun DateLeaf(date: kotlinx.datetime.LocalDate, tone: Color = Kit.Green, size: Dp = 46.dp) {
    Column(Modifier.size(size).clip(RoundedCornerShape(11.dp)).border(1.dp, Kit.Border, RoundedCornerShape(11.dp)).background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(size * 0.3f).background(tone), Alignment.Center) {
            Text(date.month.name.take(3), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.5.sp, color = Color.White)
        }
        Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
            Text("${date.day}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp, color = Kit.Ink, translate = false)
        }
    }
}

/** "in 21 days", "tomorrow", "today", "3 days ago". */
internal fun whenText(date: kotlinx.datetime.LocalDate): String {
    val days = FinanceBook.today.daysUntil(date)
    return when {
        days == 0 -> "today"
        days == 1 -> "tomorrow"
        days > 1 -> "in $days days"
        days == -1 -> "yesterday"
        else -> "${-days} days ago"
    }
}

/** A line of a sum: what it is, its sign and the amount; [strong] for the result. */
@Composable
internal fun SumLine(label: String, amount: String, sign: String = "", strong: Boolean = false, color: Color = Kit.Ink, detail: String? = null) {
    Row(Modifier.fillMaxWidth().then(if (strong) Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.07f)) else Modifier)
        .padding(horizontal = 10.dp, vertical = if (strong) 10.dp else 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(sign, Modifier.width(16.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Muted, translate = false)
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp, color = if (strong) color else Kit.Ink)
            detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted) }
        }
        Text(amount, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (strong) 16.sp else 13.5.sp, color = if (strong) color else Kit.Ink)
    }
}

@Composable
internal fun ThinLine() = Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))

/** A small round button with a word and an arrow ("See the return ›"). */
@Composable
internal fun GoPill(text: String, onClick: () -> Unit, onDark: Boolean = false) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (onDark) Color.White.copy(alpha = 0.18f) else Kit.GreenSoft).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = if (onDark) Color.White else Kit.Green, maxLines = 1)
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(14.dp), tint = if (onDark) Color.White else Kit.Green)
    }
}

// ---- Overview ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewPage(fx: Fx) {
    val setup = FinanceBook.setup
    val f = FinanceBook.figures()
    val deadlines = FinanceBook.deadlines()
    val open = deadlines.filter { FinanceBook.state(it) != FilingState.PAID }
    PageHeader("Finance and taxes", "${monthTitle(f.month)} · ${setup.country} · " +
        if (setup.vatEveryMonths == 1) "VAT every month" else "VAT every quarter") { MonthSwitcher(fx.phone) }
    BranchPartNote()
    HowItWorks(fx)
    NextToPay(fx, open)
    val yearTax = (1..FinanceBook.today.month.ordinal + 1).sumOf { m ->
        FinanceBook.figures(kotlinx.datetime.LocalDate(FinanceBook.today.year, m, 1)).profit
    }.let { if (setup.smallBusiness) 0 else share(it, setup.profitTaxRate).coerceAtLeast(0) }
    SummaryCardRow(fx.size, listOf(
        { m -> OverviewStatCard(if (f.vatToPay >= 0) "VAT to pay" else "VAT to get back", fx.money(kotlin.math.abs(f.vatToPay)),
            "On sales less on purchases", Kit.Green, m, image = Res.drawable.settings_payments) { fx.onTab(FinanceTab.VAT) } },
        { m -> OverviewStatCard("Payroll taxes", fx.money(f.payrollToPay), "${f.payroll.size} people · by the ${setup.payrollDueDay}th",
            Kit.Blue, m, image = Res.drawable.salary_managers) { fx.onTab(FinanceTab.PAYROLL) } },
        { m -> OverviewStatCard("Profit tax this year", fx.money(yearTax), if (setup.smallBusiness) "Small business: 0%" else "So far, at ${pct(setup.profitTaxRate)}",
            Kit.Amber, m, image = Res.drawable.role_finance) },
        { m -> OverviewStatCard("Keep aside", fx.money(share(f.salesNet + f.outputVat, setup.setAsidePercent * 10)),
            "${setup.setAsidePercent}% of this month's sales", Kit.Purple, m, image = Res.drawable.inventory_stock_value) }
    ))
    if (com.saporini.mobile_desktop.workspace.ui.usesBranchScope() &&
        com.saporini.mobile_desktop.core.session.BranchScope.isAll(com.saporini.mobile_desktop.core.session.Workspace.FINANCE)) EveryBranch(fx)
    PanelRow(fx.size,
        1.25f to { m -> ComingUp(fx, open.take(6), m) },
        1f to { m -> Checks(fx, m) })
    PanelRow(fx.size,
        1f to { m -> HowItAddsUp(fx, m) },
        1f to { m -> TaxesByMonth(fx, m) })
}

/** Every month in four steps, so nobody has to be an accountant to follow it. */
@Composable
private fun HowItWorks(fx: Fx) {
    val steps = listOf(
        Triple("1", "The app works it out", "From your sales, purchases and wages"),
        Triple("2", "You check the list", "What it found missing or odd"),
        Triple("3", "You file the return", "The numbers to type in, ready"),
        Triple("4", "You pay and mark it", "Reminded before every deadline")
    )
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Kit.Canvas, border = BorderStroke(1.dp, Kit.Border)) {
        val step: @Composable (Triple<String, String, String>, Modifier) -> Unit = { (n, title, detail), m ->
            Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Kit.Green), Alignment.Center) {
                    Text(n, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White, translate = false)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    Text(detail, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 2)
                }
            }
        }
        if (fx.wide) Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            steps.forEach { step(it, Modifier.weight(1f)) }
        } else Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            steps.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { pair.forEach { step(it, Modifier.weight(1f)) } } }
        }
    }
}

/** The next payment, big, on green: how much, by when, and what else falls in that month. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextToPay(fx: Fx, open: List<TaxDeadline>) {
    val next = open.firstOrNull { it.amount != null && it.amount > 0 && FinanceBook.state(it) != FilingState.NOT_YET }
        ?: open.firstOrNull { it.amount != null && it.amount > 0 } ?: return
    val sameMonth = open.filter { it.due.year == next.due.year && it.due.month == next.due.month && it.amount != null }
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(Color(0xFF2F5D2A), Kit.Green, Color(0xFF6E9A5E))))) {
        val body: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("NEXT TO PAY", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.8f))
                Text(fx.money(next.amount ?: 0), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (fx.phone) 30.sp else 38.sp,
                    letterSpacing = (-0.5).sp, color = Color.White)
                Text("${next.title} · ${next.period}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("By ${shortDate(next.due, withYear = true)}", Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Green)
                    Text(whenText(next.due), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
        val side: @Composable (Modifier) -> Unit = { m ->
            Column(m.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.13f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Everything due in ${next.due.month.name.lowercase().replaceFirstChar { it.uppercase() }}", fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                sameMonth.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${d.due.day}", Modifier.width(22.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color.White, translate = false)
                        Text(d.title, Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.5.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fx.money(d.amount ?: 0), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color.White)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.3f)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Together", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Text(fx.money(sameMonth.sumOf { it.amount ?: 0 }), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoPill("See the return", { fx.onTab(FinanceTab.FILING) }, onDark = true)
                    GoPill("All deadlines", { fx.onTab(FinanceTab.DEADLINES) }, onDark = true)
                }
            }
        }
        if (fx.wide) Row(Modifier.padding(22.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            body(Modifier.weight(1.2f))
            side(Modifier.weight(1f))
        } else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            body(Modifier.fillMaxWidth())
            side(Modifier.fillMaxWidth())
        }
    }
}

/** What's to file and pay next, a line each. */
@Composable
private fun ComingUp(fx: Fx, deadlines: List<TaxDeadline>, modifier: Modifier) {
    OverviewPanel(Icons.Outlined.EventNote, "Coming up", modifier, action = { GoPill("All deadlines", { fx.onTab(FinanceTab.DEADLINES) }) }) {
        if (deadlines.isEmpty()) Text("Nothing to file or pay right now.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
        deadlines.forEachIndexed { i, d -> DeadlineLine(fx, d, compact = true); if (i < deadlines.lastIndex) ThinLine() }
    }
}

/** One deadline: its date leaf, what it is and for which period, the amount, when, and its state. */
@Composable
internal fun DeadlineLine(fx: Fx, d: TaxDeadline, compact: Boolean, actions: (@Composable () -> Unit)? = null) {
    val state = FinanceBook.state(d)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateLeaf(d.due, tone = when (d.kind) {
            DeadlineKind.VAT -> Kit.Green; DeadlineKind.PAYROLL -> Kit.Blue; DeadlineKind.PROFIT_ADVANCE -> Kit.Amber; DeadlineKind.PROFIT_YEAR -> Kit.Purple
        })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(d.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${d.period} · ${whenText(d.due)}", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
            if (fx.phone || compact) Box(Modifier.padding(top = 3.dp)) { StatePill(state) }
        }
        if (!fx.phone && !compact) StatePill(state)
        Text(d.amount?.let { fx.money(it) } ?: "Return only", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp,
            color = if (d.amount == null) Kit.Muted else Kit.Ink)
        actions?.invoke()
    }
}

/** "Before you file": what the app noticed, each with where to fix it. */
@Composable
private fun Checks(fx: Fx, modifier: Modifier) {
    val checks = FinanceBook.checks(FinanceBook.figures())
    OverviewPanel(Icons.Outlined.FactCheck, "Before you file", modifier, count = checks.size) {
        if (checks.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp), tint = Color(0xFF16A34A))
            Text("All good: nothing missing this month.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink)
        }
        checks.forEach { item -> CheckLine(item.title, item.detail, item.serious) { fx.onTab(item.tab) } }
    }
}

@Composable
internal fun CheckLine(title: String, detail: String, serious: Boolean, onFix: () -> Unit) {
    val tone = if (serious) Kit.Danger else Kit.Amber
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tone.copy(alpha = 0.05f)).border(1.dp, tone.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
        .clickable(onClick = onFix).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (serious) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber, null, Modifier.size(20.dp), tint = tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
            Text(detail, fontFamily = Inter(), fontSize = 11.5.sp, lineHeight = 15.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("Fix", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = tone)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(16.dp), tint = tone)
    }
}

/** How this month's taxes come out of the month's numbers, line by line. */
@Composable
private fun HowItAddsUp(fx: Fx, modifier: Modifier) {
    val f = FinanceBook.figures()
    val setup = FinanceBook.setup
    OverviewPanel(Icons.Outlined.Functions, "How ${monthTitle(f.month)} adds up", modifier) {
        SumLine("Sales without VAT", fx.money(f.salesNet))
        SumLine("VAT on sales", fx.money(f.outputVat), "+", detail = "Collected for the state, at ${pct(setup.standardRate)}")
        SumLine("VAT on purchases", fx.money(f.inputVat), "−", detail = "Paid to suppliers, taken back")
        SumLine(if (f.vatToPay >= 0) "VAT to pay" else "VAT to get back", fx.money(kotlin.math.abs(f.vatToPay)), "=", strong = true, color = Kit.Green)
        Spacer(Modifier.height(4.dp))
        SumLine("Wages", fx.money(f.wages), detail = "${f.payroll.size} people")
        SumLine("Contributions and income tax", fx.money(f.payrollToPay), "=", strong = true, color = Kit.Blue, detail = "To the tax office")
        Spacer(Modifier.height(4.dp))
        SumLine("Profit before tax", fx.money(f.profit), detail = "Sales less costs, wages and contributions")
        if (!f.finished) Text("The month is still running: these grow until its last day.", Modifier.padding(start = 10.dp), fontFamily = Inter(),
            fontSize = 11.5.sp, color = Kit.Muted)
    }
}

/** The last six months' VAT and payroll taxes, a column each. */
@Composable
private fun TaxesByMonth(fx: Fx, modifier: Modifier) {
    val months = (5 downTo 0).map { firstOf(FinanceBook.month).minus(DatePeriod(months = it)) }
    val bars = months.map { m ->
        val f = FinanceBook.figures(m)
        val total = f.vatToPay.coerceAtLeast(0) + f.payrollToPay
        BarDatum(m.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, total.toFloat(), fx.short(total),
            highlight = m == FinanceBook.month, tooltip = "${monthTitle(m)} · ${fx.money(total)}")
    }
    OverviewPanel(Icons.Outlined.BarChart, "Taxes month by month", modifier, titleExtra = "VAT and payroll") {
        BarChart(bars, Modifier.fillMaxWidth(), height = 210.dp, color = Kit.Green)
    }
}

/** A white section with a small heading inside a page (used by the pages' lists). */
@Composable
internal fun Section(title: String, icon: ImageVector, modifier: Modifier = Modifier, extra: String? = null, action: (@Composable () -> Unit)? = null,
                     content: @Composable ColumnScope.() -> Unit) {
    OverviewPanel(icon, title, modifier, titleExtra = extra, action = action, content = content)
}

/**
 * At a branch (step 25): these are its records, but the returns are filed once, for the whole restaurant (one company, one
 * tax number).
 */
@Composable
internal fun BranchPartNote() {
    val branch = com.saporini.mobile_desktop.core.session.BranchScope.branch(FinanceBook.branchId) ?: return
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(androidx.compose.material.icons.Icons.Outlined.Place, null, Modifier.size(18.dp), tint = Kit.Green)
        Text("${branch.name}'s own records. Taxes are filed and paid once, for the whole restaurant: pick All branches to see them together.",
            Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Ink)
    }
}

/** Every branch side by side (consolidated reporting, step 25): what each adds to the month's taxes; a click opens its records. */
@Composable
private fun EveryBranch(fx: Fx) {
    val session = org.koin.compose.koinInject<com.saporini.mobile_desktop.core.session.SessionManager>()
    val branches = com.saporini.mobile_desktop.core.session.BranchScope.branches
    val month = FinanceBook.month
    Section("Every branch in ${monthTitle(month)}", Icons.Outlined.BarChart, Modifier.fillMaxWidth(), extra = "Together they make the figures below") {
        val perRow = if (fx.phone) 1 else if (fx.wide) branches.size.coerceAtMost(4) else 2
        branches.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { branch ->
                    val f = FinanceBook.figures(month, branch.id)
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))
                        .clickable {
                            com.saporini.mobile_desktop.core.session.BranchScope.showAll(com.saporini.mobile_desktop.core.session.Workspace.FINANCE, false)
                            session.workIn(branch.id, branch.name)
                        }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(branch.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, translate = false)
                        Line("Sales without VAT", fx.money(f.salesNet))
                        Line(if (f.vatToPay >= 0) "VAT to pay" else "VAT to get back", fx.money(kotlin.math.abs(f.vatToPay)))
                        Line("Payroll taxes", fx.money(f.payrollToPay))
                        Line("Profit before tax", fx.money(f.profit), strong = true)
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, strong: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
        Text(value, fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold, fontSize = 12.5.sp,
            color = if (strong) Kit.Green else Kit.Ink)
    }
}
