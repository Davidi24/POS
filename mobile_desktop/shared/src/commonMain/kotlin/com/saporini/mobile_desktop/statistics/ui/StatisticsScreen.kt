package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.DateRangeButton
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
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
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import com.saporini.mobile_desktop.statistics.FILTER_ONLINE
import com.saporini.mobile_desktop.statistics.FILTER_WALK_IN
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
    // The branch switcher in the bar decides: the branch looked at, or every branch combined (step 26).
    com.saporini.mobile_desktop.workspace.ui.FollowBranchScope(com.saporini.mobile_desktop.core.session.Workspace.STATISTICS) { model.branch(it) }
    val state by model.state.collectAsState()
    StatisticsContent(state, model, onTab, modifier)
}

@Composable
internal fun StatisticsContent(state: StatisticsState, model: StatisticsScreenModel, onTab: (StatsTab) -> Unit, modifier: Modifier = Modifier) {
    var saved by remember { mutableStateOf<String?>(null) }
    // A server report still shows its table first (nothing on the pages asks for one now).
    state.download?.let { download -> DownloadPreviewDialog(download, null, onClose = model::downloadHandled, onResult = { saved = it }) }
    // Everything the tabs show, worked out once per change.
    val view = remember(state) { StatsView(state) }
    // A card's Download opens its export window (user, 2026-11-15: invoice-like PDF pages or a CSV); the page's Download saves
    // every card on the page (the registry) into a folder.
    var export by remember { mutableStateOf<CardExport?>(null) }
    val registry = remember(state.tab) { CardRegistry() }
    val info = reportInfo(state, view)
    val downloads = !com.saporini.mobile_desktop.core.session.signedInWithPin(com.saporini.mobile_desktop.account.ui.LocalAccount.current.user)
    androidx.compose.runtime.CompositionLocalProvider(LocalStatsSave provides { report, picture -> export = CardExport(report, picture) },
        LocalStatsDownloads provides downloads) {
        // Sales details → a day → an order (user, 2026-11-15), under the export window so a day's Download opens over it.
        DayBookDialog(view, state, model, model.todayDate(), info) { saved = it }
    }
    export?.let { ReportExportDialog(it, info, onClose = { export = null }, onResult = { saved = it }) }
    androidx.compose.runtime.CompositionLocalProvider(LocalStatsSave provides { report, picture -> export = CardExport(report, picture) },
        LocalCardRegistry provides registry, LocalStatsDownloads provides downloads) {
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
                        // The comparison by its dates: "compared with 1 – 14 Oct" (user, 2026-11-14).
                        val compared = compareDates(state.compare, from, to).takeIf { it.isNotEmpty() }
                            ?.let { ", compared with $it" }.orEmpty()
                        // A month reads "November 2026", also on its 1st (user, 2026-11-03).
                        val today = model.todayDate()
                        (if (com.saporini.mobile_desktop.core.components.isWholeMonth(from, to, today) ||
                            com.saporini.mobile_desktop.core.components.isWholeYear(from, to))
                            com.saporini.mobile_desktop.core.components.periodOrMonthLabel(from, to, today) + compared
                        else "${periodLabel(from, to)} · ${dayCountText(from, to)}$compared") +
                            // The branch picked at the top ends the line.
                            (state.branchId?.let { id -> state.branches.firstOrNull { it.id == id }?.name?.let { " · $it" } }.orEmpty())
                    } else null
                ) {
                    // With more than one branch, which one (or all) is picked first, at the top (user, 2026-11-14).
                    // Whoever has the bar's branch switcher picks there (step 26); the page's own picker stays for the others.
                    if (state.branches.size > 1 && !com.saporini.mobile_desktop.workspace.ui.usesBranchScope())
                        StatsBranchButton(state.branchId, state.branches, { view.branchColor(it) }, model::branch)
                    PeriodControls(state, model, size)
                    FiltersButton(state, model, view)
                    // The page's own reports (agreed 2026-11-13: a Download button per page instead of a Reports place).
                    if (registry.cards.isNotEmpty()) PageExportButton(info, PageControlHeight) { saved = it }
                    RefreshButton(state.loading, model::refresh)
                    // Finance's switch at the far right on a computer (user, 2026-11-15: "top right, like Inventory's").
                    if (state.tab == StatsTab.FINANCE && size.isDesktop) FinanceSwitch(state.financeView, model::financeView)
                }
                // On tablets the switch gets its own row at the right; on phones the whole width.
                if (state.tab == StatsTab.FINANCE && !size.isDesktop) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FinanceSwitch(state.financeView, model::financeView, if (size.isPhone) Modifier.fillMaxWidth() else Modifier)
                }
                FilterChips(activeFilters(state, model, view), model::clearFilters)
                when {
                    !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to statistics", "Your role needs “View reports” to see statistics.")
                    !hasData(state) && state.loading -> OverviewPageSkeleton(size)
                    !hasData(state) && state.error != null -> PageState(PageStateKind.FAILED, hint = state.error, action = { RetryText(onClick = model::refresh) })
                    // While new figures load, the old ones stay, dimmed, so nothing jumps.
                    else -> Column(Modifier.fillMaxWidth().alpha(if (state.loading && state.stale) 0.55f else 1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (state.tab) {
                            // The pages as they were for now (user, 2026-11-13: the bar first, the pages one by one later).
                            // Overview's money figures open Sales on Sales, its order figures on Orders.
                            StatsTab.OVERVIEW -> OverviewTab(view, size, onTab, onOrders = { onTab(StatsTab.ORDERS) })
                            StatsTab.SALES -> SalesMoneyTab(view, size, model.todayDate(), model::hoursDay, model::week,
                                onDay = { model.openEveryDay(it) }, onEveryDay = { model.openEveryDay() },
                                onCalendar = model::showSalesCalendar)
                            StatsTab.ORDERS -> OrdersSection(view, size, model.todayDate())
                            StatsTab.FINANCE -> FinanceTab(view, size, state.financeView, model::financeView)
                            StatsTab.DISHES -> DishesTab(view, size)
                            StatsTab.STOCK -> StockTab(view, size)
                            StatsTab.TABLES -> BookingsSection(view, size)
                            StatsTab.TEAM -> TeamTab(view, size)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        // The toasts sit outside the page's column: inside it they took a slot (and its gap), so the page moved down when one
        // showed (user, 2026-11-14).
        ScreenMessages(state.error.takeIf { !state.stale || hasData(state) }, null, model::clearError)
        com.saporini.mobile_desktop.core.components.BottomToast(saved, isError = saved?.startsWith("Couldn't") == true, onDismiss = { saved = null })
    }
    }
}

private fun hasData(state: StatisticsState): Boolean = state.overview != null


@Composable
private fun PeriodControls(state: StatisticsState, model: StatisticsScreenModel, size: ScreenSize) {
    val from = state.from ?: return
    val to = state.to ?: return
    val today = remember(model) { model.todayDate() }
    // One button: the quick picks (This month first chosen) sit inside the calendar (user, 2026-11-14), no separate list.
    // By year lists the years from the restaurant's first (at least the last three).
    val firstYear = minOf(state.openedOn?.year ?: today.year, today.year - 2)
    StatsPeriodButton(state.period, from, to, today, stacked = size.isPhone, onPreset = model::period, onDays = { a, b -> model.custom(a, b) },
        firstYear = firstYear, onMonth = model::month, onYear = model::year)
    // The branch is chosen in Filters (user, 2026-11-13: "put the branch selection inside it"; the ‹ › arrows went too).
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
    // The Sales place's two views each offer their own: Sales the menu section, Orders who took them (staff member). Payment
    // method is never on Sales (how people paid isn't part of the sales; Finance has it). The other places offer every filter.
    val showSection = state.tab != StatsTab.ORDERS
    val showStaff = state.tab != StatsTab.SALES
    val showMethod = state.tab != StatsTab.SALES && state.tab != StatsTab.ORDERS
    val groups = buildList<FilterGroup> {
        // Branches are off for now (user, 2026-11-14: "do like it has only one branch"; they come back later).
        // A restaurant with several branches: all of them together, or one.
        // if (state.branches.size > 1) add(FilterGroup("Branch", Icons.Outlined.Business, StatsSeries[0],
        //     listOf(FilterChoice("All branches", state.branchId == null, { model.branch(null) })) +
        //         state.branches.map { branch -> FilterChoice(branch.name, state.branchId == branch.id, { model.branch(branch.id) }, view.branchColor(branch.id)) }))
        // No "Compare with" here (user, 2026-11-14: "remove this"): each period compares with the days that fit it
        // (`defaultCompare`: a month with last month, a day with a week earlier, a year with last year, else just before).
        // Order types only when more than eat in is switched on in Admin Hub → Settings → Order types (user, 2026-11-14:
        // filters that fit what the pages show); with the settings unknown, the types that had orders.
        val on = state.orderTypesOn
        val types = if (on != null) listOf("DINE_IN", "TAKEAWAY", "DELIVERY").filter { it in on }
            else view.state.sales?.orderTypes.orEmpty().filter { it.orders > 0 }.map { it.key }
        if (types.size > 1) add(FilterGroup("Order type", Icons.Outlined.RoomService, StatsSeries[2],
            listOf(FilterChoice("All types", f.orderType == null, { model.filters { it.copy(orderType = null) } })) +
                types.map { key -> FilterChoice(orderTypeLabel(key), f.orderType == key, { model.filters { it.copy(orderType = key) } }, typeColor(key)) }))
        // Walk-ins or online orders, as Where the money came from splits them, only when guests can order themselves.
        val onlineOn = on?.contains(com.saporini.mobile_desktop.statistics.data.QR_ORDERING)
            ?: view.state.sales?.sources.orEmpty().any { it.key in com.saporini.mobile_desktop.statistics.OnlineSources && it.orders > 0 }
        if (onlineOn) add(FilterGroup("How it was ordered", Icons.Outlined.Devices, StatsSeries[3], listOf(
            FilterChoice("All orders", f.source == null, { model.filters { it.copy(source = null) } }),
            FilterChoice("Walk-ins", f.source == FILTER_WALK_IN, { model.filters { it.copy(source = FILTER_WALK_IN) } }, StatsSeries[1]),
            FilterChoice("Online orders", f.source == FILTER_ONLINE, { model.filters { it.copy(source = FILTER_ONLINE) } }, StatsSeries[5])
        )))
        val sections = view.state.sales?.sections.orEmpty().map { it.section }
        if (showSection && sections.isNotEmpty()) add(FilterGroup("Menu section", Icons.AutoMirrored.Outlined.MenuBook, StatsSeries[4],
            listOf(FilterChoice("Whole menu", f.section == null, { model.filters { it.copy(section = null) } })) +
                sections.map { name -> FilterChoice(name, f.section == name, { model.filters { it.copy(section = name) } }) }))
        if (showStaff && view.staffOptions.isNotEmpty()) add(FilterGroup("Staff member", Icons.Outlined.Person, StatsSeries[5],
            listOf(FilterChoice("Everyone", f.staffId == null, { model.filters { it.copy(staffId = null) } })) +
                view.staffOptions.map { (id, name) -> FilterChoice(name, f.staffId == id, { model.filters { it.copy(staffId = id) } }) }))
        val methods = view.state.overview?.paymentMethods.orEmpty().map { it.method }
        if (showMethod && methods.isNotEmpty()) add(FilterGroup("Payment method", Icons.Outlined.Payments, StatsSeries[0],
            listOf(FilterChoice("All methods", f.method == null, { model.filters { it.copy(method = null) } })) +
                methods.map { key -> FilterChoice(methodLabel(key), f.method == key, { model.filters { it.copy(method = key) } }, methodColor(key)) }))
    }
    // The comparison always fits the period; Clear all still puts it back, in case it was changed.
    val usual = com.saporini.mobile_desktop.statistics.defaultCompare(state.from, state.to)
    // Branches off for now (see above): a branch neither counts nor is cleared. With branches back:
    // val active = f.count + (if (state.compare != usual) 1 else 0) + (if (state.branchId != null) 1 else 0)
    // StatsFiltersButton(active, groups, onClearAll = { model.clearFilters(); model.compare(usual); model.branch(null) }, PageControlHeight)
    StatsFiltersButton(f.count, groups, onClearAll = { model.clearFilters(); model.compare(usual) }, PageControlHeight)
}

/** The filters that are on, as chips that take themselves off. */
private fun activeFilters(state: StatisticsState, model: StatisticsScreenModel, view: StatsView): List<Pair<String, () -> Unit>> {
    val f = state.filters
    return listOfNotNull(
        // Branches off for now (user, 2026-11-14), so no branch chip:
        // state.branchId?.let { id -> (state.branches.firstOrNull { it.id == id }?.name ?: "One branch") to { model.branch(null) } },
        f.orderType?.let { orderTypeLabel(it) to { model.filters { s -> s.copy(orderType = null) } } },
        f.source?.let { key -> (when (key) { FILTER_WALK_IN -> "Walk-ins"; FILTER_ONLINE -> "Online orders"; else -> sourceLabel(key) }) to
            { model.filters { s -> s.copy(source = null) } } },
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

/** Who and what a report is for: the restaurant, the branch (with several), the page, the dates and when it was made. */
private fun reportInfo(state: StatisticsState, view: StatsView): ReportInfo {
    val from = view.from
    val to = view.to
    val period = com.saporini.mobile_desktop.core.components.periodLabel(from, to).let { if (from.year == to.year) "$it ${to.year}" else it }
    val now = kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val madeOn = "${com.saporini.mobile_desktop.core.format.shortDate(now.date, withYear = true)}, " +
        "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
    val branch = if (state.branches.size > 1) state.branches.firstOrNull { it.id == state.branchId }?.name ?: "All branches" else null
    return ReportInfo(state.restaurantName ?: "Restaurant", branch, state.tab.title, period,
        compareDates(state.compare, from, to).takeIf { it.isNotEmpty() }, madeOn, view.currency, "$from-to-$to")
}

/** Finance's Balance | Money in | Money out, in Inventory's look. */
@Composable
private fun FinanceSwitch(view: com.saporini.mobile_desktop.statistics.FinanceView, onView: (com.saporini.mobile_desktop.statistics.FinanceView) -> Unit,
                          modifier: Modifier = Modifier.width(330.dp)) {
    val views = com.saporini.mobile_desktop.statistics.FinanceView.entries
    com.saporini.mobile_desktop.core.components.OverviewTabs(views.map { it.title }, view.title, { title -> views.firstOrNull { it.title == title }?.let(onView) },
        modifier, height = PageControlHeight)
}
