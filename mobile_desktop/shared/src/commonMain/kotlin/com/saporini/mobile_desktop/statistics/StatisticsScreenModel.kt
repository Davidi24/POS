package com.saporini.mobile_desktop.statistics

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.shifts.toCents
import com.saporini.mobile_desktop.statistics.data.ReportInfoDto
import com.saporini.mobile_desktop.statistics.data.StatisticsRepository
import com.saporini.mobile_desktop.statistics.data.StatsOverviewDto
import com.saporini.mobile_desktop.statistics.data.StatsQuery
import com.saporini.mobile_desktop.statistics.data.StatsSalesDto
import com.saporini.mobile_desktop.statistics.data.StatsStaffDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** The Statistics workspace tabs, in the order of its navigation bar (user's order: Overview, Reports, then the rest). */
enum class StatsTab(val title: String) {
    OVERVIEW("Overview"), REPORTS("Reports"), SALES("Sales & orders"), DISHES("Dishes & stock"), PAYMENTS("Payments & finance"),
    TEAM("Team & bookings")
}

enum class StatsPeriod(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 days"),
    THIS_MONTH("This month"),
    LAST_30_DAYS("Last 30 days"),
    LAST_MONTH("Last month"),
    CUSTOM("Custom")
}

/** What the figures are compared with. */
enum class CompareMode(val label: String, val phrase: String) {
    PREVIOUS("The days before", "the days before"),
    LAST_YEAR("The same days last year", "the same days last year"),
    NONE("No comparison", "")
}

/** A branch the figures can be narrowed to. */
data class StatsBranch(val id: String, val name: String)

/**
 * The narrower filters (one options button). The server doesn't filter by them yet: until the production pass the
 * screens apply them to what was loaded (MOCK, see [StatsView]).
 */
data class StatsFilters(
    val orderType: String? = null,
    val source: String? = null,
    val section: String? = null,
    val staffId: String? = null,
    val method: String? = null
) {
    val count: Int get() = listOfNotNull(orderType, source, section, staffId, method).size
}

const val MAX_STATS_DAYS = 366

/** A downloaded report, ready for the screen to save or share. */
data class StatsDownload(val code: String, val fileName: String, val csv: String)

data class StatisticsState(
    val restaurantId: String? = null,
    val canRead: Boolean = false,
    val branchId: String? = null,
    val branches: List<StatsBranch> = emptyList(),
    // The dishes' photos from the menu, by dish id and by name, for the dish lists (empty when the menu can't be read).
    val dishPhotos: Map<String, String> = emptyMap(),
    // The order types the restaurant takes, from its settings (null = unknown: the figures decide).
    val orderTypesOn: Set<String>? = null,
    // Shown in another currency than the restaurant's own (sales from before a currency change); null = its own.
    val currency: String? = null,
    val currencies: List<String> = emptyList(),
    // This month from its 1st by default (user, 2026-11-03), shown as "November 2026".
    val period: StatsPeriod = StatsPeriod.THIS_MONTH,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val compare: CompareMode = CompareMode.PREVIOUS,
    val filters: StatsFilters = StatsFilters(),
    val tab: StatsTab = StatsTab.OVERVIEW,
    // One load fills every tab (switching tabs doesn't wait): the period's figures, the comparison's and each branch's.
    val overview: StatsOverviewDto? = null,
    val sales: StatsSalesDto? = null,
    val staff: StatsStaffDto? = null,
    val compareOverview: StatsOverviewDto? = null,
    val compareSales: StatsSalesDto? = null,
    val branchOverviews: Map<String, StatsOverviewDto> = emptyMap(),
    val reports: List<ReportInfoDto> = emptyList(),
    val loading: Boolean = false,
    val stale: Boolean = true,
    val error: String? = null,
    val downloading: String? = null,
    val download: StatsDownload? = null
)

/** "+12%", "-3%", "new" or null, comparing a figure with the period before. */
fun changeText(value: OrderDecimal, previous: OrderDecimal): String? {
    val now = value.toCents() ?: return null
    val before = previous.toCents() ?: return null
    return changeText(now, before)
}

/** The same for plain numbers (counts, cents). */
fun changeText(now: Long, before: Long): String? {
    if (before == 0L) return if (now == 0L) null else "new"
    val percent = ((now - before) * 1000 / kotlin.math.abs(before)).let { (it + if (it >= 0) 5 else -5) / 10 }
    return when {
        percent > 0 -> "+$percent%"
        percent < 0 -> "$percent%"
        else -> "±0%"
    }
}

/** The first and last day of a preset period, counted in the restaurant's days. */
fun periodRange(period: StatsPeriod, today: LocalDate): Pair<LocalDate, LocalDate>? = when (period) {
    StatsPeriod.TODAY -> today to today
    StatsPeriod.YESTERDAY -> today.minus(DatePeriod(days = 1)).let { it to it }
    StatsPeriod.LAST_7_DAYS -> today.minus(DatePeriod(days = 6)) to today
    StatsPeriod.LAST_30_DAYS -> today.minus(DatePeriod(days = 29)) to today
    StatsPeriod.THIS_MONTH -> LocalDate(today.year, today.month, 1) to today
    StatsPeriod.LAST_MONTH -> LocalDate(today.year, today.month, 1).minus(DatePeriod(months = 1)).let { first ->
        first to LocalDate(today.year, today.month, 1).minus(DatePeriod(days = 1))
    }
    StatsPeriod.CUSTOM -> null
}

/** The days the figures are compared with: as many days just before, or the same dates a year earlier. */
fun compareRange(mode: CompareMode, from: LocalDate, to: LocalDate): Pair<LocalDate, LocalDate>? = when (mode) {
    CompareMode.PREVIOUS -> {
        val days = from.daysUntil(to) + 1
        from.minus(DatePeriod(days = days)) to from.minus(DatePeriod(days = 1))
    }
    CompareMode.LAST_YEAR -> from.minus(DatePeriod(years = 1)) to to.minus(DatePeriod(years = 1))
    CompareMode.NONE -> null
}

/**
 * The Statistics workspace's state: the period, branch, comparison and filters chosen, and the figures. One load
 * fetches what every tab needs (the period, the days it's compared with and each branch), so switching tabs is
 * instant; a late answer for an earlier choice is dropped.
 */
class StatisticsScreenModel(
    private val repository: StatisticsRepository,
    session: SessionManager,
    private val today: () -> LocalDate = { kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()) },
    // The restaurant's branches, for the branch filter and the branch comparison (empty when they can't be read).
    private val branchesOf: suspend (String) -> List<StatsBranch> = { emptyList() },
    // The dishes' photos, by dish id and by name.
    private val photosOf: suspend (String) -> Map<String, String> = { emptyMap() }
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(StatisticsState())
    val state: StateFlow<StatisticsState> = mutable.asStateFlow()
    private var revision = 0L
    private var active = false
    private var loadJob: Job? = null
    private var downloadJob: Job? = null
    private var branchesFor: String? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                loadJob?.cancel()
                downloadJob?.cancel()
                downloadJob = null
                branchesFor = null
                val (from, to) = periodRange(StatsPeriod.THIS_MONTH, today())!!
                mutable.value = StatisticsState(
                    restaurantId = user?.takeIf { it.isActive }?.restaurantId,
                    branchId = user?.defaultBranchId.takeIf { com.saporini.mobile_desktop.core.session.isPlatformAdmin(user) },
                    canRead = user != null && "REPORTS_READ" in user.permissions,
                    from = from,
                    to = to
                )
                if (active) load()
            }
        }
    }

    /** The restaurant's today, as the periods count it. */
    fun todayDate(): LocalDate = today()

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load() else {
            revision++
            loadJob?.cancel()
            downloadJob?.cancel()
            downloadJob = null
            mutable.update { it.copy(loading = false, downloading = null, download = null) }
        }
    }

    /** Opens a tab; the figures are already there (Reports loads its list the first time). */
    fun tab(tab: StatsTab) {
        if (state.value.tab == tab) return
        mutable.update { it.copy(tab = tab, error = null) }
        if (tab == StatsTab.REPORTS && state.value.reports.isEmpty()) load()
    }

    fun period(period: StatsPeriod) {
        if (period == StatsPeriod.CUSTOM) {
            mutable.update { it.copy(period = period) }
            return
        }
        val (from, to) = periodRange(period, today()) ?: return
        change { it.copy(period = period, from = from, to = to) }
    }

    /** A chosen range; refused (with a message) when reversed or longer than a year. */
    fun custom(from: LocalDate, to: LocalDate): Boolean {
        val problem = when {
            to < from -> "The end can't be before the start"
            from.daysUntil(to) + 1 > MAX_STATS_DAYS -> "Choose at most $MAX_STATS_DAYS days"
            to > today() -> "The end can't be in the future"
            else -> null
        }
        if (problem != null) {
            mutable.update { it.copy(error = problem) }
            return false
        }
        change { it.copy(period = StatsPeriod.CUSTOM, from = from, to = to) }
        return true
    }

    /** Moves the whole range one step back (negative) or forward, keeping its length; a month steps a whole month. */
    fun shift(steps: Int) {
        val current = state.value
        val from = current.from ?: return
        val to = current.to ?: return
        if (com.saporini.mobile_desktop.core.components.isWholeMonth(from, to, today())) {
            val first = from.plus(DatePeriod(months = steps))
            if (first > today()) return
            val last = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
            change { it.copy(period = StatsPeriod.CUSTOM, from = first, to = if (last > today()) today() else last) }
            return
        }
        val days = from.daysUntil(to) + 1
        if (days !in 1..MAX_STATS_DAYS) return
        val shift = days.toLong() * steps.toLong()
        if (shift !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return
        val newFrom = from.plus(DatePeriod(days = shift.toInt()))
        val newTo = to.plus(DatePeriod(days = shift.toInt()))
        if (newFrom > today() || newTo > today()) return
        change { it.copy(period = StatsPeriod.CUSTOM, from = newFrom, to = newTo) }
    }

    fun branch(branchId: String?) = change { it.copy(branchId = branchId) }

    fun compare(mode: CompareMode) {
        if (state.value.compare == mode) return
        change { it.copy(compare = mode) }
    }

    /** The narrower filters work on what's loaded (see [StatsFilters]), so changing them doesn't reload. */
    fun filters(update: (StatsFilters) -> StatsFilters) = mutable.update { it.copy(filters = update(it.filters)) }

    fun clearFilters() = mutable.update { it.copy(filters = StatsFilters()) }

    /** Shows the figures in [code] (e.g. the currency used before a change); the restaurant's own when it's the first. */
    fun currency(code: String) = change { it.copy(currency = code.takeIf { c -> c != it.currencies.firstOrNull() }) }

    private fun change(update: (StatisticsState) -> StatisticsState) {
        revision++
        // The figures shown stay (dimmed) until the new ones arrive, so nothing jumps.
        mutable.update { update(it).copy(stale = true, error = null, download = null) }
        load()
    }

    fun refresh() = load()

    private fun load() {
        downloadJob?.cancel()
        downloadJob = null
        mutable.update { it.copy(downloading = null, download = null) }
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val from = current.from ?: return
        val to = current.to ?: return
        if (!active || !current.canRead) return
        val token = ++revision
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                // The branches and the dish photos once per restaurant; a person who can't read them simply has no branch filter
                // and plates instead of photos.
                if (branchesFor != restaurantId) {
                    val (branches, photos, types) = coroutineScope {
                        val branches = async { try { branchesOf(restaurantId) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() } }
                        val photos = async { try { photosOf(restaurantId) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() } }
                        val types = async { try { repository.orderTypesOn(restaurantId) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } }
                        Triple(branches.await(), photos.await(), types.await())
                    }
                    branchesFor = restaurantId
                    if (token == revision) mutable.update { it.copy(branches = branches, dishPhotos = photos, orderTypesOn = types) }
                }
                val shown = state.value
                val query = StatsQuery(restaurantId, shown.branchId, from, to, shown.currency)
                val compared = compareRange(shown.compare, from, to)?.let { (a, b) -> query.copy(from = a, to = b) }
                val perBranch = if (shown.branchId == null && shown.branches.size > 1) shown.branches else emptyList()
                coroutineScope {
                    // Reports also need the figures (some are made on the device from them).
                    val reports = if (shown.tab == StatsTab.REPORTS) async { repository.reports(restaurantId) } else null
                    val overview = async { repository.overview(query) }
                    val sales = async { repository.sales(query) }
                    val staff = async { repository.staff(query) }
                    val compareOverview = compared?.let { async { repository.overview(it) } }
                    val compareSales = compared?.let { async { repository.sales(it) } }
                    // Each branch on its own, for the branch comparisons; one that fails is left out.
                    val branches = perBranch.map { branch ->
                        branch.id to async {
                            try { repository.overview(query.copy(branchId = branch.id)) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                        }
                    }
                    val result = overview.await()
                    val salesResult = sales.await()
                    val staffResult = staff.await()
                    val compareOverviewResult = compareOverview?.await()
                    val compareSalesResult = compareSales?.await()
                    val branchResults = branches.mapNotNull { (id, answer) -> answer.await()?.let { id to it } }.toMap()
                    val reportList = reports?.await()
                    if (token == revision) mutable.update {
                        it.copy(
                            overview = result, sales = salesResult, staff = staffResult,
                            compareOverview = compareOverviewResult, compareSales = compareSalesResult, branchOverviews = branchResults,
                            reports = reportList ?: it.reports, currencies = result.period.currencies, stale = false, error = null
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) {
                    val accessDenied = denied(e)
                    mutable.update {
                        if (accessDenied) {
                            it.copy(
                                overview = null, sales = null, staff = null, compareOverview = null, compareSales = null,
                                branchOverviews = emptyMap(), reports = emptyList(), download = null, downloading = null,
                                stale = true, error = message(e), canRead = false
                            )
                        } else {
                            it.copy(stale = true, error = message(e))
                        }
                    }
                }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    /** Fetches one report as CSV for the period shown; the screen saves [StatisticsState.download]. */
    fun download(code: String) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val from = current.from ?: return
        val to = current.to ?: return
        if (!current.canRead || current.downloading != null) return
        val token = revision
        mutable.update { it.copy(downloading = code, download = null) }
        downloadJob = work.launch {
            try {
                val csv = repository.report(StatsQuery(restaurantId, current.branchId, from, to, current.currency), code)
                if (token == revision) mutable.update { it.copy(download = StatsDownload(code, "$code-$from-to-$to.csv", csv)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = message(e)) }
            } finally {
                if (token == revision && state.value.downloading == code) {
                    mutable.update { it.copy(downloading = null) }
                }
            }
        }
    }

    /** A report made on the device from the figures shown (the ones the server doesn't make yet). */
    fun offer(code: String, csv: String) {
        val current = state.value
        val from = current.from ?: return
        val to = current.to ?: return
        if (!current.canRead) return
        mutable.update { it.copy(download = StatsDownload(code, "$code-$from-to-$to.csv", csv)) }
    }

    fun downloadHandled() = mutable.update { it.copy(download = null) }

    fun clearError() = mutable.update { it.copy(error = null) }

    override fun onDispose() {
        revision++
        active = false
        work.cancel()
    }

    private fun denied(e: Exception) = e is ApiException && (e.status == 401 || e.status == 403)

    private fun message(e: Exception): String = when {
        e is ApiException && (e.status == 401 || e.status == 403) -> "You don't have permission to see statistics."
        e is ApiException && e.status in 400..499 -> e.message
        e is ApiException -> "The server couldn't work out the statistics. Try again."
        else -> "Could not load the statistics. Check the connection and try again."
    }
}
