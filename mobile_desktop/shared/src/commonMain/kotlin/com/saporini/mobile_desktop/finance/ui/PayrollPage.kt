package com.saporini.mobile_desktop.finance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.finance.FilingState
import com.saporini.mobile_desktop.finance.FinanceBook
import com.saporini.mobile_desktop.finance.FinanceTab
import com.saporini.mobile_desktop.finance.PayLine
import com.saporini.mobile_desktop.finance.monthTitle
import com.saporini.mobile_desktop.finance.pct
import com.saporini.mobile_desktop.platform.ui.InitialsFace
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.role_finance
import mobile_desktop.shared.generated.resources.salary_kitchen
import mobile_desktop.shared.generated.resources.salary_managers
import mobile_desktop.shared.generated.resources.salary_waiters

/** The month's wages and what goes to the tax office from them: contributions both sides and income tax held back. */
@Composable
internal fun PayrollPage(fx: Fx) {
    val f = FinanceBook.figures()
    val setup = FinanceBook.setup
    val deadline = FinanceBook.deadlines().firstOrNull { it.key == "PAY-${f.month}" }
    val state = deadline?.let { FinanceBook.state(it) }
    val fromWages = f.payroll.sumOf { it.employeeContributions + it.incomeTax }
    PageHeader("Payroll", deadline?.let { "${monthTitle(f.month)} · list and payment by ${shortDate(it.due, withYear = true)}" } ?: monthTitle(f.month)) {
        MonthSwitcher(fx.phone)
    }
    BranchPartNote()
    SummaryCardRow(fx.size, listOf(
        { m -> OverviewStatCard("Wages", fx.money(f.wages), "${f.payroll.size} people, before anything is taken", Kit.Green, m, image = Res.drawable.salary_waiters) },
        { m -> OverviewStatCard("Held back from wages", fx.money(fromWages), "Their contributions and income tax", Kit.Blue, m, image = Res.drawable.salary_kitchen) },
        { m -> OverviewStatCard("Paid by the restaurant", fx.money(f.employerContributions), "Contributions on top of wages", Kit.Amber, m,
            image = Res.drawable.salary_managers) },
        { m -> OverviewStatCard("To the tax office", fx.money(f.payrollToPay), state?.label ?: "", state?.let { stateColor(it) } ?: Kit.Grey, m,
            image = Res.drawable.role_finance) }
    ))
    if (deadline != null && state != null && state != FilingState.NOT_YET && state != FilingState.PAID) {
        FileStrip(state, onFiled = { FinanceBook.markFiled(deadline.key); fx.say("Marked as filed. Pay it by ${shortDate(deadline.due)}.") },
            onPaid = { FinanceBook.markPaid(deadline.key); fx.say("Marked as paid") })
    }
    val missing = f.payroll.filter { it.taxNumberMissing }
    missing.forEach { p -> CheckLine("${p.name} has no personal number", "Add it in Admin Hub → Users; the payroll list can't be sent without it", true) {
        fx.say("Personal numbers are added in Admin Hub → Users (production pass).") } }
    Section("Each person", Icons.Outlined.Groups, Modifier.fillMaxWidth(), extra = "Wages come from Salary") {
        if (!fx.phone) PayHead()
        f.payroll.forEach { line -> PersonLine(fx, line); ThinLine() }
        if (!fx.phone) Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Together", Modifier.weight(1.6f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
            listOf(f.wages, f.payroll.sumOf { it.employeeContributions }, f.payroll.sumOf { it.incomeTax }, f.payroll.sumOf { it.net }, f.employerContributions)
                .forEach { Text(fx.money(it), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.End) }
        }
    }
    Section("How it's worked out", Icons.Outlined.Calculate, Modifier.fillMaxWidth(), action = { GoPill("Change in Tax setup", { fx.onTab(FinanceTab.SETUP) }) }) {
        SumLine("Their contributions", pct(setup.employeeSocial + setup.employeeHealth), detail = "Social ${pct(setup.employeeSocial)} and health ${pct(setup.employeeHealth)} of the wage")
        SumLine("The restaurant's contributions", pct(setup.employerSocial + setup.employerHealth), detail = "Social ${pct(setup.employerSocial)} and health ${pct(setup.employerHealth)} on top")
        SumLine("Income tax", "${pct(setup.incomeMiddleRate)} / ${pct(setup.incomeTopRate)}",
            detail = "Nothing up to ${fx.money(setup.incomeFreeUpTo)} a month, ${pct(setup.incomeMiddleRate)} up to ${fx.money(setup.incomeMiddleUpTo)}, ${pct(setup.incomeTopRate)} above")
    }
}

@Composable
private fun PayHead() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
        listOf("Person", "Wage", "Their contributions", "Income tax", "Paid to them", "Restaurant pays").forEachIndexed { i, name ->
            Text(name.uppercase(), Modifier.weight(if (i == 0) 1.6f else 1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.5.sp,
                color = Kit.Muted, textAlign = if (i == 0) TextAlign.Start else TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PersonLine(fx: Fx, line: PayLine) {
    val who: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InitialsFace(line.name, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(line.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                Text(line.role, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted)
                if (line.taxNumberMissing) Tag("No personal number", Kit.Danger)
            }
        }
    }
    if (fx.phone) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            who(Modifier.fillMaxWidth())
            SumLine("Wage", fx.money(line.gross))
            SumLine("Held back", fx.money(line.employeeContributions + line.incomeTax), "−")
            SumLine("Paid to them", fx.money(line.net), "=")
        }
    } else Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        who(Modifier.weight(1.6f))
        listOf(line.gross, line.employeeContributions, line.incomeTax, line.net, line.employerContributions).forEachIndexed { i, v ->
            Text(fx.money(v), Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (i == 3) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp,
                color = Kit.Ink, textAlign = TextAlign.End)
        }
    }
}
