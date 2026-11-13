package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RoomService
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.admin.inventory.ui.OptionGroup
import com.saporini.mobile_desktop.admin.inventory.ui.OptionLine
import com.saporini.mobile_desktop.admin.inventory.ui.PhoneOptionsButton
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.DateRangeButton
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.PeriodStepper
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.dayCountText
import com.saporini.mobile_desktop.core.components.periodLabel
import com.saporini.mobile_desktop.core.files.saveTextFile
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import com.saporini.mobile_desktop.statistics.CompareMode
import com.saporini.mobile_desktop.statistics.MAX_STATS_DAYS
import com.saporini.mobile_desktop.statistics.StatisticsScreenModel
import com.saporini.mobile_desktop.statistics.StatisticsState
import com.saporini.mobile_desktop.statistics.StatsPeriod
import com.saporini.mobile_desktop.statistics.StatsTab
import com.saporini.mobile_desktop.statistics.StatsView
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
    // Everything the tabs show, worked out once per change.
    val view = remember(state) { StatsView(state) }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        ScrollColumn(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().padding(start = if (size.isPhone) 14.dp else 22.dp, end = if (size.isPhone) 4.dp else 12.dp,
                    top = if (size.isPhone) 12.dp else 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val from = state.from
                val to = state.to
                PageHeader(
                    state.tab.title,
                    if (from != null && to != null) {
                        // Reports are files for the days chosen; there's nothing to compare there.
                        val compared = state.compare.takeIf { it != CompareMode.NONE && state.tab != StatsTab.REPORTS }
                            ?.let { ", compared with ${it.phrase}" }.orEmpty()
                        // A month reads "November 2026", also on its 1st (user, 2026-11-03).
                        val today = model.todayDate()
                        (if (com.saporini.mobile_desktop.core.components.isWholeMonth(from, to, today))
                            com.saporini.mobile_desktop.core.components.periodOrMonthLabel(from, to, today) + compared
                        else "${periodLabel(from, to)} · ${dayCountText(from, to)}$compared") +
                            (state.branchId?.let { id -> state.branches.firstOrNull { it.id == id }?.name?.let { " · $it" } }.orEmpty())
                    } else null
                ) {
                    PeriodControls(state, model, size)
                    if (state.tab != StatsTab.REPORTS) FiltersButton(state, model, view)
                    RefreshButton(state.loading, model::refresh)
                }
                FilterChips(activeFilters(state, model, view), model::clearFilters)
                ScreenMessages(state.error.takeIf { !state.stale || hasData(state) }, null, model::clearError)
                com.saporini.mobile_desktop.core.components.BottomToast(saved, isError = saved?.startsWith("Couldn't") == true, onDismiss = { saved = null })
                when {
                    !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to statistics", "Your role needs “View reports” to see statistics.")
                    !hasData(state) && state.loading -> OverviewPageSkeleton(size)
                    !hasData(state) && state.error != null -> PageState(PageStateKind.FAILED, hint = state.error, action = { RetryText(onClick = model::refresh) })
                    // While new figures load, the old ones stay, dimmed, so nothing jumps.
                    else -> Column(Modifier.fillMaxWidth().alpha(if (state.loading && state.stale) 0.55f else 1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (state.tab) {
                            StatsTab.OVERVIEW -> OverviewTab(view, size, onTab)
                            StatsTab.REPORTS -> ReportsTab(state, model, view, size)
                            StatsTab.SALES -> SalesOrdersTab(view, size)
                            StatsTab.DISHES -> DishesStockTab(view, size)
                            StatsTab.PAYMENTS -> PaymentsFinanceTab(view, size)
                            StatsTab.TEAM -> TeamBookingsTab(view, size)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private fun hasData(state: StatisticsState): Boolean = when (state.tab) {
    StatsTab.REPORTS -> state.reports.isNotEmpty()
    else -> state.overview != null
}

@Composable
private fun PeriodControls(state: StatisticsState, model: StatisticsScreenModel, size: ScreenSize) {
    val from = state.from ?: return
    val to = state.to ?: return
    val today = remember(model) { model.todayDate() }
    val presets = StatsPeriod.entries.filter { it != StatsPeriod.CUSTOM }
    HeaderDropdown(if (state.period == StatsPeriod.CUSTOM) "Chosen days" else state.period.label, Icons.Outlined.DateRange,
        Modifier.width(if (size.isPhone) 150.dp else 160.dp), presets.map { it.label }, { label -> presets.firstOrNull { it.label == label }?.let(model::period) })
    PeriodStepper({ model.shift(-1) }, { model.shift(1) }, forwardEnabled = to < today)
    DateRangeButton(from, to, today, { a, b -> model.custom(a, b) }, maxDays = MAX_STATS_DAYS,
        label = com.saporini.mobile_desktop.core.components.periodOrMonthLabel(from, to, today))
    // A restaurant with several branches: all of them together, or one.
    if (state.branches.size > 1) {
        val shown = state.branches.firstOrNull { it.id == state.branchId }?.name ?: "All branches"
        HeaderDropdown(shown, Icons.Outlined.Business, Modifier.width(170.dp), listOf("All branches") + state.branches.map { it.name },
            { label -> model.branch(state.branches.firstOrNull { it.name == label }?.id) })
    }
    // After a currency change the restaurant has sold in two currencies; each is shown on its own.
    if (state.currencies.size > 1) {
        val shown = state.currency ?: state.currencies.first()
        HeaderDropdown("In $shown", Icons.Outlined.CurrencyExchange, Modifier.width(120.dp), state.currencies.map { "In $it" },
            { label -> model.currency(label.removePrefix("In ")) })
    }
}

/** Every narrower filter, and what to compare with, in one options button (user rule), with how many are on. */
@Composable
private fun FiltersButton(state: StatisticsState, model: StatisticsScreenModel, view: StatsView) {
    val f = state.filters
    PhoneOptionsButton(Icons.Outlined.Tune, "Filters", f.count + if (state.compare != CompareMode.PREVIOUS) 1 else 0, PageControlHeight) { close ->
        OptionGroup("Compare with")
        CompareMode.entries.forEach { mode ->
            OptionLine(mode.label, state.compare == mode, { model.compare(mode); close() }, icon = Icons.AutoMirrored.Outlined.CompareArrows)
        }
        val types = view.state.sales?.orderTypes.orEmpty().map { it.key }.ifEmpty { listOf("DINE_IN", "TAKEAWAY", "DELIVERY") }
        OptionGroup("Order type")
        OptionLine("Every order type", f.orderType == null, { model.filters { it.copy(orderType = null) }; close() }, icon = Icons.Outlined.RoomService)
        types.forEach { key ->
            OptionLine(orderTypeLabel(key), f.orderType == key, { model.filters { it.copy(orderType = key) }; close() }, icon = Icons.Outlined.RoomService,
                color = typeColor(key))
        }
        val sources = view.state.sales?.sources.orEmpty().map { it.key }
        if (sources.isNotEmpty()) {
            OptionGroup("Where orders came from")
            OptionLine("Everywhere", f.source == null, { model.filters { it.copy(source = null) }; close() }, icon = Icons.Outlined.Devices)
            sources.forEach { key ->
                OptionLine(sourceLabel(key), f.source == key, { model.filters { it.copy(source = key) }; close() }, icon = Icons.Outlined.Devices, color = sourceColor(key))
            }
        }
        val sections = view.state.sales?.sections.orEmpty().map { it.section }
        if (sections.isNotEmpty()) {
            OptionGroup("Menu section")
            OptionLine("The whole menu", f.section == null, { model.filters { it.copy(section = null) }; close() }, icon = Icons.AutoMirrored.Outlined.MenuBook)
            sections.forEach { name ->
                OptionLine(name, f.section == name, { model.filters { it.copy(section = name) }; close() }, icon = Icons.AutoMirrored.Outlined.MenuBook,
                    color = com.saporini.mobile_desktop.core.components.Kit.Amber)
            }
        }
        if (view.staffOptions.isNotEmpty()) {
            OptionGroup("Staff member")
            OptionLine("Everyone", f.staffId == null, { model.filters { it.copy(staffId = null) }; close() }, icon = Icons.Outlined.Person)
            view.staffOptions.forEach { (id, name) ->
                OptionLine(name, f.staffId == id, { model.filters { it.copy(staffId = id) }; close() }, icon = Icons.Outlined.Person,
                    color = com.saporini.mobile_desktop.core.components.Kit.Blue)
            }
        }
        val methods = view.state.overview?.paymentMethods.orEmpty().map { it.method }
        if (methods.isNotEmpty()) {
            OptionGroup("Payment method")
            OptionLine("Every method", f.method == null, { model.filters { it.copy(method = null) }; close() }, icon = Icons.Outlined.Payments)
            methods.forEach { key ->
                OptionLine(methodLabel(key), f.method == key, { model.filters { it.copy(method = key) }; close() }, icon = methodIcon(key), color = methodColor(key))
            }
        }
    }
}

/** The filters that are on, as chips that take themselves off. */
private fun activeFilters(state: StatisticsState, model: StatisticsScreenModel, view: StatsView): List<Pair<String, () -> Unit>> {
    if (state.tab == StatsTab.REPORTS) return emptyList()
    val f = state.filters
    return listOfNotNull(
        f.orderType?.let { orderTypeLabel(it) to { model.filters { s -> s.copy(orderType = null) } } },
        f.source?.let { sourceLabel(it) to { model.filters { s -> s.copy(source = null) } } },
        f.section?.let { it to { model.filters { s -> s.copy(section = null) } } },
        f.staffId?.let { id -> (view.staffOptions.firstOrNull { it.first == id }?.second ?: "One person") to { model.filters { s -> s.copy(staffId = null) } } },
        f.method?.let { methodLabel(it) to { model.filters { s -> s.copy(method = null) } } }
    )
}

/** A small grey line under a panel or above a list, with an info icon. */
@Composable
internal fun Footnote(text: String) {
    com.saporini.mobile_desktop.core.components.InfoBox(Icons.Outlined.Info, text, Modifier.fillMaxWidth(), tone = com.saporini.mobile_desktop.core.components.Kit.Muted)
}
