package com.saporini.mobile_desktop.fraud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.DateRangeButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.dayCountText
import com.saporini.mobile_desktop.core.components.periodLabel
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.fraud.FraudScreenModel
import com.saporini.mobile_desktop.fraud.FraudState
import com.saporini.mobile_desktop.fraud.FraudTab
import com.saporini.mobile_desktop.fraud.MAX_FRAUD_DAYS
import com.saporini.mobile_desktop.fraud.data.FraudAlertDto
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import kotlin.time.Clock

private val QuickPeriods = listOf(1 to "Today", 7 to "Last 7 days", 14 to "Last 14 days", 30 to "Last 30 days", MAX_FRAUD_DAYS to "Last 3 months")

/** The Fraud Detection workspace's content for the tab chosen in its navigation bar. */
@Composable
internal fun FraudWorkspace(tab: FraudTab, onTab: (FraudTab) -> Unit, modifier: Modifier = Modifier) {
    val model = koinInject<FraudScreenModel>()
    remember(model) { model.tab(tab); true }
    BindToLifecycle(model::setActive, model::onDispose)
    LaunchedEffect(tab) { model.tab(tab) }
    val state by model.state.collectAsState()
    FraudContent(state, model, onTab, modifier)
}

@Composable
internal fun FraudContent(state: FraudState, model: FraudScreenModel, onTab: (FraudTab) -> Unit, modifier: Modifier = Modifier) {
    // The alert open in the review dialog, by key: its latest copy comes from the lists.
    var openKey by remember { mutableStateOf<String?>(null) }
    val openAlert: FraudAlertDto? = openKey?.let { key ->
        state.alerts.firstOrNull { it.key == key } ?: state.overview?.latest?.firstOrNull { it.key == key }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val from = state.from
            val to = state.to
            PageHeader(
                when (state.tab) {
                    FraudTab.OVERVIEW -> "Fraud overview"
                    FraudTab.ALERTS -> "Alerts"
                    FraudTab.ACTIVITY -> "Sensitive actions"
                    FraudTab.RULES -> "Checks"
                },
                when (state.tab) {
                    FraudTab.RULES -> "What the system watches for, and when it flags it"
                    else -> if (from != null && to != null) "${periodLabel(from, to)} · ${dayCountText(from, to)}" else null
                }
            ) {
                if (state.tab != FraudTab.RULES && from != null && to != null) {
                    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
                    val days = from.daysUntil(to) + 1
                    val current = QuickPeriods.firstOrNull { it.first == days && to == today }?.second ?: "Chosen days"
                    HeaderDropdown(current, Icons.Outlined.DateRange, Modifier.width(if (size.isPhone) 150.dp else 160.dp), QuickPeriods.map { it.second },
                        { label -> QuickPeriods.firstOrNull { it.second == label }?.let { model.lastDays(it.first) } })
                    DateRangeButton(from, to, today, { a, b -> model.period(a, b) }, maxDays = MAX_FRAUD_DAYS)
                }
                RefreshButton(state.loading, model::refresh)
            }
            ScreenMessages(state.error.takeIf { hasData(state) }, null, model::clearError)
            if (state.truncated && state.tab != FraudTab.RULES) {
                MessageBar("So many actions in these days that only the latest were checked. Choose fewer days to see all of them.", MessageKind.WARNING)
            }
            val body = Modifier.fillMaxWidth().weight(1f)
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to fraud checks", "Your role needs “View fraud alerts”.")
                !hasData(state) && state.loading -> OverviewPageSkeleton(size)
                !hasData(state) && state.error != null -> PageState(PageStateKind.FAILED, hint = state.error, action = { RetryText(onClick = model::refresh) })
                else -> when (state.tab) {
                    FraudTab.OVERVIEW -> state.overview?.let { FraudOverviewTab(it, state, model, size, onTab, { openKey = it.key }, body) }
                    FraudTab.ALERTS -> AlertsTab(state, model, size, { openKey = it.key }, body)
                    FraudTab.ACTIVITY -> ActivityTab(state, model, size, body)
                    FraudTab.RULES -> state.rules?.let { RulesTab(it, state, model, size, body) }
                }
            }
        }
    }
    openAlert?.let { AlertReviewDialog(it, state, model) { openKey = null } }
}

private fun hasData(state: FraudState): Boolean = when (state.tab) {
    FraudTab.OVERVIEW -> state.overview != null
    // The lists keep their filters on screen and show loading and problems inside the list.
    FraudTab.ALERTS, FraudTab.ACTIVITY -> true
    FraudTab.RULES -> state.rules != null
}
