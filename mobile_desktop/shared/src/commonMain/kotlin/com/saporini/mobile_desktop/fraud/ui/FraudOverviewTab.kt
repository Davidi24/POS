package com.saporini.mobile_desktop.fraud.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.BarChart
import com.saporini.mobile_desktop.core.components.BarDatum
import com.saporini.mobile_desktop.core.components.InitialsAvatar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KpiCard
import com.saporini.mobile_desktop.core.components.LegendDot
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PanelRow
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.fraud.FraudScreenModel
import com.saporini.mobile_desktop.fraud.FraudState
import com.saporini.mobile_desktop.fraud.FraudTab
import com.saporini.mobile_desktop.fraud.data.FraudAlertDto
import com.saporini.mobile_desktop.fraud.data.FraudAlertFilter
import com.saporini.mobile_desktop.fraud.data.FraudOverviewDto
import com.saporini.mobile_desktop.statistics.ui.parseDate
import com.saporini.mobile_desktop.statistics.ui.weekdayShort
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.settings_notifications
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.workspace_fraud
import mobile_desktop.shared.generated.resources.workspace_statistics

@Composable
internal fun FraudOverviewTab(
    data: FraudOverviewDto,
    state: FraudState,
    model: FraudScreenModel,
    size: ScreenSize,
    onTab: (FraudTab) -> Unit,
    onOpen: (FraudAlertDto) -> Unit,
    modifier: Modifier
) {
    // Opens the Alerts tab with a filter already chosen.
    val showAlerts: (FraudAlertFilter) -> Unit = { filter ->
        model.tab(FraudTab.ALERTS)
        model.filter(filter)
        onTab(FraudTab.ALERTS)
    }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SummaryCardRow(size, listOf(
            { m -> KpiCard("To check", "${data.openAlerts}", if (data.openAlerts == 0L) "Nothing waiting for you" else "alerts nobody has looked at",
                if (data.openAlerts > 0) Kit.Danger else Kit.Green, m, image = Res.drawable.workspace_fraud,
                valueColor = if (data.openAlerts > 0) Kit.Danger else Kit.Ink, onClick = { showAlerts(FraudAlertFilter(status = "OPEN")) }) },
            { m -> KpiCard("High risk", "${data.highOpenAlerts}", "open alerts marked high", if (data.highOpenAlerts > 0) Kit.Danger else Kit.Grey, m,
                image = Res.drawable.settings_notifications, onClick = { showAlerts(FraudAlertFilter(status = "OPEN", severity = "HIGH")) }) },
            { m -> KpiCard("All alerts", "${data.totalAlerts}", "in these days, checked or not", Kit.Blue, m, image = Res.drawable.workspace_statistics,
                onClick = { showAlerts(FraudAlertFilter()) }) },
            { m -> KpiCard("Money involved", money(data.amountInvolved, data.currency), "in the alerts of these days", Kit.Amber, m,
                image = Res.drawable.settings_payments) }
        ))
        PanelRow(size,
            1.6f to { m -> AlertsPerDayPanel(data, m) },
            1f to { m -> ByCheckPanel(data, m) { rule -> showAlerts(FraudAlertFilter(rule = rule, status = "OPEN")) } }
        )
        PanelRow(size,
            1f to { m -> PeoplePanel(data, m) { staffId -> showAlerts(FraudAlertFilter(staffId = staffId)) } },
            1.6f to { m -> LatestPanel(data, state, size, m, onOpen) { showAlerts(FraudAlertFilter(status = "OPEN")) } }
        )
    }
}

@Composable
private fun AlertsPerDayPanel(data: FraudOverviewDto, modifier: Modifier) {
    val days = data.days.mapNotNull { day -> parseDate(day.date)?.let { it to day } }
    OverviewPanel(Icons.Outlined.BarChart, "Alerts per day", modifier) {
        if (days.isEmpty() || days.all { it.second.alerts == 0L }) {
            OverviewCompactEmpty("A quiet stretch", "No alerts in these days.", Icons.Outlined.VerifiedUser)
            return@OverviewPanel
        }
        BarChart(days.map { (date, day) ->
            BarDatum(if (days.size <= 7) weekdayShort(date) else shortDate(date), day.alerts.toFloat(), "${day.alerts}", highlight = day.high > 0)
        }, Modifier.padding(top = 6.dp), height = 220.dp, color = Kit.Danger)
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(Kit.Danger, "with a high-risk alert")
            LegendDot(Kit.Danger.copy(alpha = 0.5f), "medium and low only")
        }
    }
}

@Composable
private fun ByCheckPanel(data: FraudOverviewDto, modifier: Modifier, onRule: (String) -> Unit) {
    val rules = data.rules.filter { it.total > 0 }.sortedWith(compareByDescending<com.saporini.mobile_desktop.fraud.data.FraudRuleCountDto> { it.open }
        .thenByDescending { it.total })
    OverviewPanel(Icons.AutoMirrored.Outlined.Rule, "By check", modifier, count = rules.size) {
        if (rules.isEmpty()) {
            OverviewCompactEmpty("No check was set off", "Each check that flags something shows here with its count.", Icons.AutoMirrored.Outlined.Rule)
            return@OverviewPanel
        }
        rules.take(7).forEach { rule ->
            val color = severityColor(rule.severity)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8F9F7))
                    .clickable(indication = LocalIndication.current, interactionSource = null) { onRule(rule.rule) }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)), Alignment.Center) {
                    Icon(ruleIcon(rule.rule), null, Modifier.size(17.dp), tint = color)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(rule.title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${severityLabel(rule.severity)} · ${rule.total} in all", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, maxLines = 1)
                }
                if (rule.open > 0) Text("${rule.open} to check", Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = color)
                else Text("all checked", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            }
        }
    }
}

@Composable
private fun PeoplePanel(data: FraudOverviewDto, modifier: Modifier, onPerson: (String) -> Unit) {
    val people = data.staff.filter { it.totalAlerts > 0 }.sortedWith(compareByDescending<com.saporini.mobile_desktop.fraud.data.FraudStaffRiskDto> { it.highAlerts }
        .thenByDescending { it.openAlerts }.thenByDescending { it.totalAlerts })
    OverviewPanel(Icons.Outlined.Groups, "People to look at", modifier) {
        if (people.isEmpty()) {
            OverviewCompactEmpty("No one stands out", "People with alerts show here, the riskiest first.", Icons.Outlined.Groups)
            return@OverviewPanel
        }
        people.take(6).forEach { person ->
            val tone = if (person.highAlerts > 0) Kit.Danger else Kit.Amber
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8F9F7))
                    .clickable(indication = LocalIndication.current, interactionSource = null) { onPerson(person.staffId) }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialsAvatar(person.staffName ?: "?", color = tone, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.staffName ?: "Someone", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text("${person.totalAlerts} alerts · ${money(person.amount, data.currency)}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                }
                if (person.highAlerts > 0) RiskCount("${person.highAlerts} high", Kit.Danger)
                if (person.openAlerts > 0) { Spacer(Modifier.width(6.dp)); RiskCount("${person.openAlerts} open", Kit.Amber) }
            }
        }
    }
}

@Composable
private fun RiskCount(text: String, color: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = color, maxLines = 1)
}

@Composable
private fun LatestPanel(data: FraudOverviewDto, state: FraudState, size: ScreenSize, modifier: Modifier, onOpen: (FraudAlertDto) -> Unit, onAll: () -> Unit) {
    OverviewPanel(Icons.Outlined.NotificationsActive, "Latest alerts", modifier, action = {
        Text("See all", Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAll).padding(horizontal = 6.dp, vertical = 2.dp),
            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
    }) {
        if (data.latest.isEmpty()) {
            OverviewCompactEmpty("All quiet", "New alerts show here first.", Icons.Outlined.VerifiedUser)
            return@OverviewPanel
        }
        data.latest.take(6).forEach { alert -> AlertCard(alert, compact = !size.isDesktop, busy = state.reviewing == alert.key) { onOpen(alert) } }
    }
}
