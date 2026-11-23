package com.saporini.mobile_desktop.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PanelRow
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.finance.DeadlineKind
import com.saporini.mobile_desktop.finance.FilingState
import com.saporini.mobile_desktop.finance.FinanceBook
import com.saporini.mobile_desktop.finance.FinanceTab
import com.saporini.mobile_desktop.finance.TaxDeadline
import com.saporini.mobile_desktop.finance.firstOf
import com.saporini.mobile_desktop.finance.monthTitle
import com.saporini.mobile_desktop.finance.pct
import com.saporini.mobile_desktop.finance.share

// ---- Deadlines ----

/** Everything to file and pay, month by month, with the marks for filed and paid; done ones fold away. */
@Composable
internal fun DeadlinesPage(fx: Fx) {
    val setup = FinanceBook.setup
    val all = FinanceBook.deadlines()
    val open = all.filter { FinanceBook.state(it) != FilingState.PAID }
    val done = all.filter { FinanceBook.state(it) == FilingState.PAID }.reversed()
    var showDone by remember { mutableStateOf(false) }
    PageHeader("Deadlines", "What to file and pay, and by when · you're reminded ${setup.remindDaysBefore} days before")
    open.groupBy { firstOf(it.due) }.forEach { (month, list) ->
        Section(monthTitle(month), Icons.Outlined.EventAvailable, Modifier.fillMaxWidth(),
            extra = list.mapNotNull { it.amount }.sum().takeIf { it > 0 }?.let { "${fx.money(it)} to pay" }) {
            list.forEachIndexed { i, d ->
                DeadlineLine(fx, d, compact = false) { DeadlineActions(fx, d) }
                if (i < list.lastIndex) ThinLine()
            }
        }
    }
    Section("Done", Icons.Outlined.History, Modifier.fillMaxWidth(), extra = "${done.size} filed and paid",
        action = { GoPill(if (showDone) "Hide" else "Show", { showDone = !showDone }) }) {
        if (showDone) done.forEachIndexed { i, d -> DeadlineLine(fx, d, compact = false); if (i < done.lastIndex) ThinLine() }
        else SmallMuted("The last three months, all on time.")
    }
}

@Composable
private fun DeadlineActions(fx: Fx, d: TaxDeadline) {
    when (FinanceBook.state(d)) {
        FilingState.READY -> KitButton(if (fx.phone) "Filed" else "Mark as filed", { FinanceBook.markFiled(d.key); fx.say("Marked as filed") }, style = ButtonStyle.SECONDARY)
        FilingState.FILED -> KitButton(if (fx.phone) "Paid" else "Mark as paid", { FinanceBook.markPaid(d.key); fx.say("Marked as paid") })
        else -> {}
    }
}

// ---- Tax returns ----

/** The month's returns, ready: where it stands, the numbers to type in, and the documents to download or send. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilingPage(fx: Fx) {
    val f = FinanceBook.figures()
    val setup = FinanceBook.setup
    val deadlines = FinanceBook.deadlines()
    val vat = deadlines.firstOrNull { it.key == "VAT-${f.month}" }
    val pay = deadlines.firstOrNull { it.key == "PAY-${f.month}" }
    PageHeader("Tax returns", "${monthTitle(f.month)} · everything to file, ready") { MonthSwitcher(fx.phone) }
    BranchPartNote()
    vat?.let { Steps(fx, FinanceBook.state(it)) }
    PanelRow(fx.size,
        1f to { m ->
            Section("Numbers for the VAT return", Icons.Outlined.PointOfSale, m, extra = vat?.let { "by ${shortDate(it.due)}" }) {
                val standard = f.sales.firstOrNull { it.rate == setup.standardRate }
                val reduced = f.sales.firstOrNull { it.rate == setup.reducedRate }
                val zero = f.sales.firstOrNull { it.rate == 0 }
                FormBox(1, "Sales at ${pct(setup.standardRate)}, without VAT", fx.money(standard?.net ?: 0))
                FormBox(2, "VAT on them", fx.money(standard?.vat ?: 0))
                FormBox(3, "Sales at ${pct(setup.reducedRate)}, without VAT", fx.money(reduced?.net ?: 0))
                FormBox(4, "VAT on them", fx.money(reduced?.vat ?: 0))
                FormBox(5, "Sales without VAT", fx.money(zero?.net ?: 0))
                FormBox(6, "Purchases with VAT, without it", fx.money(f.expenses.filter { it.vat > 0 }.sumOf { it.net }))
                FormBox(7, "VAT on purchases", fx.money(f.inputVat))
                FormBox(8, if (f.vatToPay >= 0) "VAT to pay" else "VAT to get back", fx.money(kotlin.math.abs(f.vatToPay)), strong = true)
            }
        },
        1f to { m ->
            Section("Numbers for the payroll list", Icons.Outlined.Groups, m, extra = pay?.let { "by ${shortDate(it.due)}" }) {
                FormBox(1, "People paid", "${f.payroll.size}")
                FormBox(2, "Wages before anything is taken", fx.money(f.wages))
                FormBox(3, "Their contributions (${pct(setup.employeeSocial + setup.employeeHealth)})", fx.money(f.payroll.sumOf { it.employeeContributions }))
                FormBox(4, "The restaurant's contributions (${pct(setup.employerSocial + setup.employerHealth)})", fx.money(f.employerContributions))
                FormBox(5, "Income tax held back", fx.money(f.payroll.sumOf { it.incomeTax }))
                FormBox(6, "To pay together", fx.money(f.payrollToPay), strong = true)
            }
        })
    Section("Documents", Icons.Outlined.Description, Modifier.fillMaxWidth(), extra = "Download them or send them to your accountant",
        action = if (!fx.phone) ({
            KitButton("Send to accountant", {
                fx.say(if (setup.accountantEmail.isBlank()) "Add your accountant's email in Tax setup first." else "Sending comes with the production pass.")
            }, icon = Icons.AutoMirrored.Outlined.Send, style = ButtonStyle.SECONDARY)
        }) else null) {
        val docs = listOf(
            Triple(Icons.Outlined.PointOfSale, "VAT return", "The numbers above, as one page"),
            Triple(Icons.Outlined.MenuBook, "Sales book", "A line a day, as the tax office asks"),
            Triple(Icons.Outlined.TableChart, "Purchase book", "Every invoice with VAT"),
            Triple(Icons.Outlined.Groups, "Payroll list", "Each person's wage and what's held back"),
            Triple(Icons.Outlined.AccountBalance, "Profit and loss", "The month's sales, costs and profit")
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = if (fx.phone) 1 else if (fx.wide) 3 else 2) {
            docs.forEach { (icon, name, what) -> DocTile(fx, icon, name, what, Modifier.weight(1f)) }
        }
    }
    YearCard(fx)
}

/** Month ends → Check → File → Pay, the current step lit. */
@Composable
private fun Steps(fx: Fx, state: FilingState) {
    val reached = when (state) { FilingState.NOT_YET -> 0; FilingState.READY -> 1; FilingState.FILED -> 3; FilingState.PAID -> 4 }
    val names = listOf("Month ends", "Check the list", "File the return", "Pay")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        names.forEachIndexed { i, name ->
            val done = i < reached
            val now = i == reached
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(3.dp).background(if (i == 0) Color.Transparent else if (done || now) Kit.Green else Kit.Border))
                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (done) Kit.Green else if (now) Color.White else Kit.Tint)
                        .border(2.dp, if (done || now) Kit.Green else Kit.Border, CircleShape), Alignment.Center) {
                        if (done) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = Color.White)
                        else Text("${i + 1}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (now) Kit.Green else Kit.Muted, translate = false)
                    }
                    Box(Modifier.weight(1f).height(3.dp).background(if (i == names.lastIndex) Color.Transparent else if (done) Kit.Green else Kit.Border))
                }
                Text(name, fontFamily = Inter(), fontWeight = if (now) FontWeight.Bold else FontWeight.SemiBold, fontSize = if (fx.phone) 11.sp else 12.5.sp,
                    color = if (done || now) Kit.Ink else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** One box of a form: its number, what goes in it, the amount. */
@Composable
private fun FormBox(number: Int, label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (strong) Kit.GreenSoft else Color.White)
        .border(1.dp, if (strong) Kit.Green.copy(alpha = 0.35f) else Kit.RowBorder, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$number", Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (strong) Kit.Green else Kit.Tint).padding(top = 3.dp),
            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (strong) Color.White else Kit.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, translate = false)
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (strong) 15.sp else 13.5.sp, color = if (strong) Kit.Green else Kit.Ink)
    }
}

@Composable
private fun DocTile(fx: Fx, icon: ImageVector, name: String, what: String, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, Kit.Border, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Kit.GreenSoft), Alignment.Center) {
            Icon(icon, null, Modifier.size(21.dp), tint = Kit.Green)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink)
            Text(what, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 2)
        }
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.Border, RoundedCornerShape(10.dp))
            .then(Modifier.background(Color.White)), Alignment.Center) {
            androidx.compose.material3.IconButton({ fx.say("$name: downloads come with the production pass.") }, Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Download, "Download $name", Modifier.size(18.dp), tint = Kit.Ink)
            }
        }
    }
}

/** The year: the profit tax return after it ends, and the estimate so far. */
@Composable
private fun YearCard(fx: Fx) {
    val setup = FinanceBook.setup
    val year = FinanceBook.month.year
    val months = (1..12).map { kotlinx.datetime.LocalDate(year, it, 1) }.filter { it <= firstOf(FinanceBook.today) }
    val profit = months.sumOf { FinanceBook.figures(it).profit }
    val tax = if (setup.smallBusiness) 0 else share(profit, setup.profitTaxRate).coerceAtLeast(0)
    val due = FinanceBook.deadlines(kotlinx.datetime.LocalDate(year + 1, 1, 1), 4).firstOrNull { it.kind == DeadlineKind.PROFIT_YEAR }
    Section("The year $year", Icons.Outlined.AccountBalance, Modifier.fillMaxWidth(), extra = due?.let { "Profit tax return by ${shortDate(it.due, withYear = true)}" }) {
        SumLine("Profit so far", fx.money(profit), detail = "${months.size} months: sales less costs, wages and contributions")
        SumLine("Profit tax at ${pct(setup.profitTaxRate)}", fx.money(tax), "=", strong = true, color = Kit.Amber,
            detail = if (setup.smallBusiness) "A small business pays none" else "Less the advances already paid")
        Spacer(Modifier.height(2.dp))
        GoPill("Change the rules in Tax setup", { fx.onTab(FinanceTab.SETUP) })
    }
}
