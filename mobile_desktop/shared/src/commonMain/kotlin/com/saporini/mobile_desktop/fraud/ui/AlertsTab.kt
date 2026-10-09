package com.saporini.mobile_desktop.fraud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.LabeledValue
import com.saporini.mobile_desktop.core.components.LoadMoreWhenNearEnd
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PagedFooter
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.SideColumn
import com.saporini.mobile_desktop.core.components.SkeletonListPanel
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.TimeColumn
import com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.fraud.FraudScreenModel
import com.saporini.mobile_desktop.fraud.FraudState
import com.saporini.mobile_desktop.fraud.REVIEW_STATUSES
import com.saporini.mobile_desktop.fraud.data.FraudAlertDto
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.workspace_fraud

private const val MAX_NOTE = 1000

@Composable
internal fun AlertsTab(state: FraudState, model: FraudScreenModel, size: ScreenSize, onOpen: (FraudAlertDto) -> Unit, modifier: Modifier) {
    val filter = state.filter
    val list: @Composable (Modifier) -> Unit = { m ->
        OverviewPanel(Icons.Outlined.NotificationsActive, "Alerts", m, count = state.alertsTotal.toInt()) {
            if (!size.isDesktop) {
                FilterPills(listOf<Pair<String?, String>>(null to "All") + REVIEW_STATUSES.map { it to reviewLabel(it) }, filter.status,
                    { model.filter(filter.copy(status = it)) })
                FilterPills(listOf<Pair<String?, String>>(null to "Any risk", "HIGH" to "High", "MEDIUM" to "Medium", "LOW" to "Low"), filter.severity,
                    { model.filter(filter.copy(severity = it)) })
            }
            ActiveFilters(state, model)
            when {
                state.alerts.isEmpty() && state.loading -> SkeletonListPanel(Modifier.fillMaxWidth().weight(1f).alpha(rememberSkeletonAlpha("alerts")), 6, 72.dp)
                state.alerts.isEmpty() && state.error != null -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty("Couldn't load the alerts", state.error, Icons.Outlined.WarningAmber, action = { RetryText(onClick = model::refresh) })
                }
                state.alerts.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty(if (filter.status == "OPEN") "Nothing to check" else "No alerts", if (filter.status == "OPEN")
                        "Every alert in these days has been looked at." else "No alert matches these filters in these days.",
                        Icons.Outlined.VerifiedUser, image = Res.drawable.workspace_fraud)
                }
                else -> {
                    val listState = rememberLazyListState()
                    LoadMoreWhenNearEnd(listState, state.alertsHasNext, state.loading || state.loadingMore, onLoadMore = model::loadMore)
                    val zone = RestaurantTime.zone
                    val byDay = state.alerts.groupBy { localOf(it.occurredAt, zone)?.date }
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 4.dp)) {
                        byDay.forEach { (day, alerts) ->
                            item(key = "day-$day") { GroupLabel(day?.let(::weekdayDate) ?: "Unknown day", "${alerts.size} ${if (alerts.size == 1) "alert" else "alerts"}") }
                            items(alerts, key = { it.key }) { alert -> AlertCard(alert, compact = size.isPhone, busy = state.reviewing == alert.key) { onOpen(alert) } }
                        }
                        item(key = "footer") {
                            PagedFooter(state.alerts.size, state.alertsTotal, state.alertsHasNext, state.loadingMore, state.error != null, model::loadMore)
                        }
                    }
                }
            }
        }
    }
    if (size.isDesktop) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            list(Modifier.weight(1f).fillMaxHeight())
            SideColumn(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                OverviewPanel(Icons.Outlined.FilterAlt, "Status", Modifier.fillMaxWidth()) {
                    FilterRow(Icons.Outlined.Shield, "Every alert", null, Kit.Green, filter.status == null) { model.filter(filter.copy(status = null)) }
                    REVIEW_STATUSES.forEach { status ->
                        FilterRow(reviewIcon(status), reviewLabel(status), null, reviewColor(status), filter.status == status) { model.filter(filter.copy(status = status)) }
                    }
                }
                OverviewPanel(Icons.Outlined.WarningAmber, "Risk", Modifier.fillMaxWidth()) {
                    FilterRow(Icons.Outlined.Shield, "Any risk", null, Kit.Green, filter.severity == null) { model.filter(filter.copy(severity = null)) }
                    listOf("HIGH", "MEDIUM", "LOW").forEach { severity ->
                        FilterRow(Icons.Outlined.WarningAmber, severityLabel(severity), null, severityColor(severity), filter.severity == severity) {
                            model.filter(filter.copy(severity = severity))
                        }
                    }
                }
                state.overview?.rules?.filter { it.total > 0 }?.takeIf { it.isNotEmpty() }?.let { rules ->
                    OverviewPanel(Icons.AutoMirrored.Outlined.Rule, "Check", Modifier.fillMaxWidth()) {
                        FilterRow(Icons.AutoMirrored.Outlined.Rule, "Every check", null, Kit.Green, filter.rule == null) { model.filter(filter.copy(rule = null)) }
                        rules.forEach { rule ->
                            FilterRow(ruleIcon(rule.rule), rule.title, rule.total.toInt(), severityColor(rule.severity), filter.rule == rule.rule) {
                                model.filter(filter.copy(rule = if (filter.rule == rule.rule) null else rule.rule))
                            }
                        }
                    }
                }
            }
        }
    } else list(modifier)
}

/** The filters that aren't visible as pills (a check or a person), as removable chips. */
@Composable
private fun ActiveFilters(state: FraudState, model: FraudScreenModel) {
    val filter = state.filter
    val ruleTitle = filter.rule?.let { rule -> state.overview?.rules?.firstOrNull { it.rule == rule }?.title ?: state.alerts.firstOrNull { it.rule == rule }?.title ?: rule }
    val person = filter.staffId?.let { id -> state.alerts.firstOrNull { it.staffId == id }?.staffName ?: state.overview?.staff?.firstOrNull { it.staffId == id }?.staffName ?: "One person" }
    if (ruleTitle == null && person == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ruleTitle?.let { FilterChip(Icons.AutoMirrored.Outlined.Rule, it) { model.filter(filter.copy(rule = null)) } }
        person?.let { FilterChip(Icons.Outlined.Person, it) { model.filter(filter.copy(staffId = null)) } }
    }
}

@Composable
private fun FilterChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onRemove: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Kit.GreenSoft).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = Kit.Green)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(50)).clickable(onClick = onRemove), Alignment.Center) {
            Icon(Icons.Outlined.Close, "Remove filter", Modifier.size(14.dp), tint = Kit.Green)
        }
    }
}

/** One alert as a card: when, which check, who, how much and whether someone looked at it. */
@Composable
internal fun AlertCard(alert: FraudAlertDto, compact: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    val zone = RestaurantTime.zone
    val color = severityColor(alert.severity)
    StripCard(color, minHeight = 70.dp, chevron = true, onClick = onClick, faded = alert.status == "DISMISSED") {
        if (!compact) {
            TimeColumn(timeText(alert.occurredAt, zone), severityLabel(alert.severity), color, Modifier.width(70.dp))
            CardDivider()
        }
        IconTile(ruleIcon(alert.rule), color, 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(alert.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(alert.staffName, alert.orderNumber?.let { "order #$it" }, alert.detail?.takeIf { it.isNotBlank() },
                if (compact) timeText(alert.occurredAt, zone) else null).joinToString(" · "),
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        alert.amount?.let { Text(money(it, alert.currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink) }
        if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = Kit.Green, strokeWidth = 2.dp)
        else if (!compact) StatusPill(reviewLabel(alert.status), reviewColor(alert.status))
    }
}

/** An alert's details, with buttons to say what it was. */
@Composable
internal fun AlertReviewDialog(alert: FraudAlertDto, state: FraudState, model: FraudScreenModel, onClose: () -> Unit) {
    val zone = RestaurantTime.zone
    var note by remember(alert.key) { mutableStateOf(alert.reviewNote.orEmpty()) }
    val busy = state.reviewing == alert.key
    val review: (String) -> Unit = { status -> model.review(alert.key, status, note); onClose() }
    AppDialog(alert.title, onClose, subtitle = "${severityLabel(alert.severity)} risk · ${dateTimeText(alert.occurredAt, zone)}", busy = busy, maxWidth = 620.dp,
        buttons = if (!state.canReview) ({ KitButton("Close", onClose, style = ButtonStyle.SECONDARY) }) else ({
            if (alert.status != "OPEN") KitButton("Open again", { review("OPEN") }, style = ButtonStyle.SECONDARY, enabled = !busy)
            KitButton("Not a problem", { review("DISMISSED") }, style = ButtonStyle.SECONDARY, enabled = !busy)
            KitButton("Confirm", { review("CONFIRMED") }, style = ButtonStyle.DANGER, enabled = !busy)
            KitButton("Checked", { review("REVIEWED") }, icon = Icons.Outlined.VerifiedUser, loading = busy)
        })
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(ruleIcon(alert.rule), severityColor(alert.severity), 46.dp)
            Column(Modifier.weight(1f)) {
                alert.detail?.takeIf { it.isNotBlank() }?.let { Text(it, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Ink) }
            }
            StatusPill(reviewLabel(alert.status), reviewColor(alert.status))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledValue("Who", alert.staffName ?: "Unknown", Modifier.weight(1f))
            LabeledValue("Order", alert.orderNumber?.let { "#$it" } ?: "–", Modifier.weight(1f))
            LabeledValue("Amount", alert.amount?.let { money(it, alert.currency) } ?: "–", Modifier.weight(1f))
            LabeledValue("When", localOf(alert.occurredAt, zone)?.let { "${shortDate(it.date)}, ${timeText(alert.occurredAt, zone)}" } ?: "–", Modifier.weight(1f))
        }
        if (alert.status != "OPEN" && alert.reviewedAt != null) {
            Text("${reviewLabel(alert.status)} by ${alert.reviewedByName ?: "someone"} · ${dateTimeText(alert.reviewedAt, zone)}", fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = reviewColor(alert.status))
        }
        if (state.canReview) {
            TextInput(note, { note = it }, label = "Note", optional = true, singleLine = false, minLines = 3, maxLength = MAX_NOTE,
                placeholder = "What happened? e.g. “Manager approved the discount for a regular”",
                problem = if (note.trim().length > MAX_NOTE) "At most $MAX_NOTE characters" else null)
        } else alert.reviewNote?.takeIf { it.isNotBlank() }?.let { LabeledValue("Note", it) }
    }
}
