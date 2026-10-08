package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
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
import com.saporini.mobile_desktop.core.components.PeriodStepper
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.dayCountText
import com.saporini.mobile_desktop.core.components.periodLabel
import com.saporini.mobile_desktop.core.files.saveTextFile
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import com.saporini.mobile_desktop.statistics.MAX_STATS_DAYS
import com.saporini.mobile_desktop.statistics.StatisticsScreenModel
import com.saporini.mobile_desktop.statistics.StatisticsState
import com.saporini.mobile_desktop.statistics.StatsPeriod
import com.saporini.mobile_desktop.statistics.StatsTab
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import kotlin.time.Clock

/** The Statistics workspace's content for the tab chosen in its navigation bar. */
@Composable
internal fun StatisticsWorkspace(tab: StatsTab, onTab: (StatsTab) -> Unit, modifier: Modifier = Modifier) {
    val model = koinInject<StatisticsScreenModel>()
    // The tab is chosen before the model starts, so it loads once.
    remember(model) { model.tab(tab); true }
    BindToLifecycle(model::setActive, model::onDispose)
    LaunchedEffect(tab) { model.tab(tab) }
    val state by model.state.collectAsState()
    StatisticsContent(state, model, onTab, modifier)
}

@Composable
internal fun StatisticsContent(state: StatisticsState, model: StatisticsScreenModel, onTab: (StatsTab) -> Unit, modifier: Modifier = Modifier) {
    var saved by remember { mutableStateOf<String?>(null) }
    // A finished report download is saved as a file, then handed back.
    LaunchedEffect(state.download) {
        val download = state.download ?: return@LaunchedEffect
        saved = runCatching { "Saved to ${saveTextFile(download.fileName, "text/csv", download.csv)}" }
            .getOrElse { "Couldn't save the report: ${it.message ?: "unknown problem"}" }
        model.downloadHandled()
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val from = state.from
            val to = state.to
            PageHeader(
                when (state.tab) {
                    StatsTab.OVERVIEW -> "Overview"
                    StatsTab.SALES -> "Sales"
                    StatsTab.STAFF -> "Staff"
                    StatsTab.REPORTS -> "Reports"
                },
                if (from != null && to != null) "${periodLabel(from, to)} · ${dayCountText(from, to)}, compared with the ${dayCountText(from, to)} before" else null
            ) {
                PeriodControls(state, model, size)
                RefreshButton(state.loading, model::refresh)
            }
            ScreenMessages(state.error.takeIf { !state.stale || hasData(state) }, null, model::clearError)
            saved?.let { MessageBar(it, if (it.startsWith("Couldn't")) MessageKind.ERROR else MessageKind.SUCCESS, onDismiss = { saved = null }) }
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to statistics", "Your role needs “View reports” to see statistics.")
                !hasData(state) && state.loading -> OverviewPageSkeleton(size)
                !hasData(state) && state.error != null -> PageState(PageStateKind.FAILED, hint = state.error, action = { RetryText(onClick = model::refresh) })
                else -> when (state.tab) {
                    StatsTab.OVERVIEW -> state.overview?.let { OverviewTab(it, size, onTab) }
                    StatsTab.SALES -> state.sales?.let { SalesTab(it, size) }
                    StatsTab.STAFF -> state.staff?.let { StaffTab(it, size) }
                    StatsTab.REPORTS -> ReportsTab(state, model, size)
                }
            }
        }
    }
}

private fun hasData(state: StatisticsState): Boolean = when (state.tab) {
    StatsTab.OVERVIEW -> state.overview != null
    StatsTab.SALES -> state.sales != null
    StatsTab.STAFF -> state.staff != null
    StatsTab.REPORTS -> state.reports.isNotEmpty()
}

@Composable
private fun PeriodControls(state: StatisticsState, model: StatisticsScreenModel, size: ScreenSize) {
    val from = state.from ?: return
    val to = state.to ?: return
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val presets = StatsPeriod.entries.filter { it != StatsPeriod.CUSTOM }
    HeaderDropdown(if (state.period == StatsPeriod.CUSTOM) "Chosen days" else state.period.label, Icons.Outlined.DateRange,
        Modifier.width(if (size.isPhone) 150.dp else 160.dp), presets.map { it.label }, { label -> presets.firstOrNull { it.label == label }?.let(model::period) })
    PeriodStepper({ model.shift(-1) }, { model.shift(1) }, forwardEnabled = to < today)
    DateRangeButton(from, to, today, { a, b -> model.custom(a, b) }, maxDays = MAX_STATS_DAYS)
}

/** A small grey line under a panel or above a list, with an info icon. */
@Composable
internal fun Footnote(text: String) {
    com.saporini.mobile_desktop.core.components.InfoBox(Icons.Outlined.Info, text, Modifier.fillMaxWidth(), tone = com.saporini.mobile_desktop.core.components.Kit.Muted)
}
