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
import kotlinx.coroutines.cancel
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

/** The Statistics workspace tabs, in the order of its navigation bar. */
enum class StatsTab { OVERVIEW, SALES, STAFF, REPORTS }

enum class StatsPeriod(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 days"),
    THIS_MONTH("This month"),
    LAST_30_DAYS("Last 30 days"),
    LAST_MONTH("Last month"),
    CUSTOM("Custom")
}

const val MAX_STATS_DAYS = 366

/** A downloaded report, ready for the screen to save or share. */
data class StatsDownload(val code: String, val fileName: String, val csv: String)

data class StatisticsState(
    val restaurantId: String? = null,
    val canRead: Boolean = false,
    val branchId: String? = null,
    val period: StatsPeriod = StatsPeriod.LAST_7_DAYS,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val tab: StatsTab = StatsTab.OVERVIEW,
    val overview: StatsOverviewDto? = null,
    val sales: StatsSalesDto? = null,
    val staff: StatsStaffDto? = null,
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

/**
 * The Statistics workspace's state: the period and branch chosen, and the figures of the open tab. Only the open tab
 * is loaded; a late answer for an earlier choice is dropped.
 */
class StatisticsScreenModel(
    private val repository: StatisticsRepository,
    session: SessionManager,
    private val today: () -> LocalDate = { kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()) }
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(StatisticsState())
    val state: StateFlow<StatisticsState> = mutable.asStateFlow()
    private var revision = 0L
    private var active = false
    private var loadJob: Job? = null
    private var downloadJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                loadJob?.cancel()
                downloadJob?.cancel()
                downloadJob = null
                val (from, to) = periodRange(StatsPeriod.LAST_7_DAYS, today())!!
                mutable.value = StatisticsState(
                    restaurantId = user?.takeIf { it.isActive }?.restaurantId,
                    canRead = user != null && "REPORTS_READ" in user.permissions,
                    from = from,
                    to = to
                )
                if (active) load()
            }
        }
    }

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

    fun tab(tab: StatsTab) {
        if (state.value.tab == tab) return
        mutable.update { it.copy(tab = tab, error = null) }
        load()
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

    /** Moves the whole range one step back (negative) or forward, keeping its length. */
    fun shift(steps: Int) {
        val current = state.value
        val from = current.from ?: return
        val to = current.to ?: return
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

    private fun change(update: (StatisticsState) -> StatisticsState) {
        revision++
        mutable.update { update(it).copy(overview = null, sales = null, staff = null, stale = true, error = null, download = null) }
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
        val query = StatsQuery(restaurantId, current.branchId, from, to)
        val token = ++revision
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                when (current.tab) {
                    StatsTab.OVERVIEW -> repository.overview(query).let { result ->
                        if (token == revision) mutable.update { it.copy(overview = result) }
                    }
                    StatsTab.SALES -> repository.sales(query).let { result ->
                        if (token == revision) mutable.update { it.copy(sales = result) }
                    }
                    StatsTab.STAFF -> repository.staff(query).let { result ->
                        if (token == revision) mutable.update { it.copy(staff = result) }
                    }
                    StatsTab.REPORTS -> repository.reports(restaurantId).let { result ->
                        if (token == revision) mutable.update { it.copy(reports = result) }
                    }
                }
                if (token == revision) mutable.update { it.copy(stale = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) {
                    val accessDenied = denied(e)
                    mutable.update {
                        if (accessDenied) {
                            it.copy(
                                overview = null,
                                sales = null,
                                staff = null,
                                reports = emptyList(),
                                download = null,
                                downloading = null,
                                stale = true,
                                error = message(e),
                                canRead = false
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
                val csv = repository.report(StatsQuery(restaurantId, current.branchId, from, to), code)
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
