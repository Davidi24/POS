package com.saporini.mobile_desktop.fraud.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.InfoBox
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitSwitch
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.fraud.FraudScreenModel
import com.saporini.mobile_desktop.fraud.FraudState
import com.saporini.mobile_desktop.fraud.data.FraudRuleSettingDto
import com.saporini.mobile_desktop.fraud.data.FraudRulesDto

private val SeverityOrder = listOf("HIGH", "MEDIUM", "LOW")

@Composable
internal fun RulesTab(rules: FraudRulesDto, state: FraudState, model: FraudScreenModel, size: ScreenSize, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OverviewPanel(Icons.Outlined.Tune, "Limits", Modifier.fillMaxWidth(), titleExtra = "set in Admin Hub → Settings → Fraud checks") {
            val limits = listOf(
                "Big discount" to "${rules.discountPercent}% of the bill",
                "Big refund" to "from ${money(rules.refundAmount, null)}",
                "Removals a day" to "more than ${rules.voidsPerDay}",
                "High tip" to "${rules.tipPercent}% of the payment",
                "Cash refunds a day" to "more than ${rules.cashRefundsPerDay}"
            )
            (if (size.isPhone) limits.chunked(2) else listOf(limits)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                            Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (!state.canEditRules) {
            InfoBox(Icons.Outlined.Info, "Only someone who can change settings can switch checks on or off.", Modifier.fillMaxWidth())
        }
        SeverityOrder.forEach { severity ->
            val group = rules.rules.filter { it.severity == severity }
            if (group.isEmpty()) return@forEach
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(severityColor(severity)))
                Text("${severityLabel(severity)} risk", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
                Text("${group.count { it.enabled }} of ${group.size} on", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            }
            val columns = when (size) { ScreenSize.PHONE -> 1; ScreenSize.TABLET -> 2; ScreenSize.DESKTOP -> 3 }
            group.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { rule -> RuleCard(rule, state, model, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: FraudRuleSettingDto, state: FraudState, model: FraudScreenModel, modifier: Modifier) {
    val color = severityColor(rule.severity)
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, if (rule.enabled) Kit.Border else Kit.RowBorder),
        shadowElevation = if (rule.enabled) 1.dp else 0.dp) {
        Row(Modifier.alpha(if (rule.enabled) 1f else 0.6f)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(if (rule.enabled) color else Kit.Grey))
            Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.12f)), Alignment.Center) {
                        Icon(ruleIcon(rule.rule), null, Modifier.size(19.dp), tint = color)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(rule.title, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 2)
                    KitSwitch(rule.enabled, { model.setRuleEnabled(rule.rule, it) }, enabled = state.canEditRules && !state.savingRules, compact = true)
                }
                Text(rule.description, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted)
                rule.threshold?.takeIf { it.isNotBlank() }?.let {
                    Text(it, Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f)).padding(horizontal = 9.dp, vertical = 3.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = color, maxLines = 1)
                }
            }
        }
    }
}
