package com.saporini.mobile_desktop.fraud

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.fraud.data.FraudActivityDto
import com.saporini.mobile_desktop.fraud.data.FraudAlertDto
import com.saporini.mobile_desktop.fraud.data.FraudAlertFilter
import com.saporini.mobile_desktop.fraud.data.FraudOverviewDto
import com.saporini.mobile_desktop.fraud.data.FraudQuery
import com.saporini.mobile_desktop.fraud.data.FraudRepository
import com.saporini.mobile_desktop.fraud.data.FraudReviewRequestDto
import com.saporini.mobile_desktop.fraud.data.FraudRulesDto
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/** The Fraud Detection tabs, in the order of its navigation bar. */
enum class FraudTab { OVERVIEW, ALERTS, ACTIVITY, RULES }

const val MAX_FRAUD_DAYS = 92
const val FRAUD_PAGE_SIZE = 40
val REVIEW_STATUSES = listOf("OPEN", "REVIEWED", "DISMISSED", "CONFIRMED")
val ACTIVITY_TYPES = listOf("DISCOUNT", "ITEM_REMOVED", "ORDER_VOIDED", "ORDER_CANCELLED", "REFUND", "PAYMENT_CANCELLED", "ORDER_REOPENED")

data class FraudState(
    val restaurantId: String? = null,
    val canRead: Boolean = false,
    val canReview: Boolean = false,
    val canEditRules: Boolean = false,
    val savingRules: Boolean = false,
    val branchId: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val tab: FraudTab = FraudTab.OVERVIEW,
    val overview: FraudOverviewDto? = null,
    val filter: FraudAlertFilter = FraudAlertFilter(status = "OPEN"),
    val alerts: List<FraudAlertDto> = emptyList(),
    val alertPages: Int = 0,
    val alertsTotal: Long = 0,
    val alertsHasNext: Boolean = false,
    val truncated: Boolean = false,
    val activityType: String? = null,
    val activityStaffId: String? = null,
    val activity: List<FraudActivityDto> = emptyList(),
    val activityPages: Int = 0,
    val activityTotal: Long = 0,
    val activityHasNext: Boolean = false,
    val rules: FraudRulesDto? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val reviewing: String? = null,
    val stale: Boolean = true,
    val error: String? = null
)

/**
 * The Fraud Detection workspace's state. Lists are paged; a refresh reloads every page already shown, so the list
 * keeps its place. A review updates the alert everywhere it is shown.
 */
class FraudScreenModel(
    private val repository: FraudRepository,
    session: SessionManager,
    private val today: () -> LocalDate = { kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()) }
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(FraudState())
    val state: StateFlow<FraudState> = mutable.asStateFlow()
    private val writes = Mutex()
    private var revision = 0L
    private var active = false
    private var loadJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                val to = today()
                mutable.value = FraudState(
                    restaurantId = user?.takeIf { it.isActive }?.restaurantId,
                    canRead = user != null && "FRAUD_READ" in user.permissions,
                    canReview = user != null && "FRAUD_REVIEW" in user.permissions,
                    canEditRules = user != null && "SETTINGS_UPDATE" in user.permissions,
                    from = to.minus(DatePeriod(days = 6)),
                    to = to
                )
                if (active) load(keepPages = false)
            }
        }
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load(keepPages = true) else { revision++; loadJob?.cancel(); mutable.update { it.copy(loading = false, loadingMore = false) } }
    }

    fun tab(tab: FraudTab) {
        if (state.value.tab == tab) return
        mutable.update { it.copy(tab = tab, error = null) }
        load(keepPages = true)
    }

    fun period(from: LocalDate, to: LocalDate): Boolean {
        val problem = when {
            to < from -> "The end can't be before the start"
            from.daysUntil(to) + 1 > MAX_FRAUD_DAYS -> "Choose at most $MAX_FRAUD_DAYS days"
            from > today() -> "The start can't be in the future"
            else -> null
        }
        if (problem != null) {
            mutable.update { it.copy(error = problem) }
            return false
        }
        restart { it.copy(from = from, to = to) }
        return true
    }

    fun lastDays(days: Int) {
        val to = today()
        period(to.minus(DatePeriod(days = (days.coerceIn(1, MAX_FRAUD_DAYS)) - 1)), to)
    }

    fun branch(branchId: String?) = restart { it.copy(branchId = branchId) }

    fun filter(filter: FraudAlertFilter) {
        if (filter.status != null && filter.status !in REVIEW_STATUSES) return
        restart { it.copy(filter = filter) }
    }

    fun activityFilter(type: String?, staffId: String?) {
        if (type != null && type !in ACTIVITY_TYPES) return
        restart { it.copy(activityType = type, activityStaffId = staffId) }
    }

    private fun restart(change: (FraudState) -> FraudState) {
        revision++
        mutable.update {
            change(it).copy(overview = null, alerts = emptyList(), alertPages = 0, alertsTotal = 0, alertsHasNext = false,
                activity = emptyList(), activityPages = 0, activityTotal = 0, activityHasNext = false, stale = true, error = null)
        }
        load(keepPages = false)
    }

    fun refresh() = load(keepPages = true)

    private fun query(current: FraudState): FraudQuery? {
        val restaurantId = current.restaurantId ?: return null
        return FraudQuery(restaurantId, current.branchId, current.from ?: return null, current.to ?: return null)
    }

    private fun load(keepPages: Boolean) {
        val current = state.value
        val query = query(current) ?: return
        if (!active || !current.canRead) return
        val token = ++revision
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                when (current.tab) {
                    FraudTab.OVERVIEW -> repository.overview(query).let { result ->
                        if (token == revision) mutable.update { it.copy(overview = result, truncated = result.truncated) }
                    }
                    FraudTab.ALERTS -> {
                        // Reload every page already shown, so the list stays where it was.
                        val pages = if (keepPages) current.alertPages.coerceAtLeast(1) else 1
                        val items = mutableListOf<FraudAlertDto>()
                        var total = 0L
                        var hasNext = false
                        var truncated = false
                        for (page in 0 until pages) {
                            val result = repository.alerts(query, current.filter, page, FRAUD_PAGE_SIZE)
                            items += result.items
                            total = result.totalElements
                            hasNext = result.hasNext
                            truncated = result.truncated
                            if (!result.hasNext) break
                        }
                        if (token == revision) mutable.update {
                            it.copy(alerts = items.distinctBy { alert -> alert.key }, alertPages = pages, alertsTotal = total,
                                alertsHasNext = hasNext, truncated = truncated)
                        }
                    }
                    FraudTab.ACTIVITY -> {
                        val pages = if (keepPages) current.activityPages.coerceAtLeast(1) else 1
                        val items = mutableListOf<FraudActivityDto>()
                        var total = 0L
                        var hasNext = false
                        for (page in 0 until pages) {
                            val result = repository.activity(query, current.activityType, current.activityStaffId, page, FRAUD_PAGE_SIZE)
                            items += result.items
                            total = result.totalElements
                            hasNext = result.hasNext
                            if (!result.hasNext) break
                        }
                        if (token == revision) mutable.update {
                            it.copy(activity = items, activityPages = pages, activityTotal = total, activityHasNext = hasNext)
                        }
                    }
                    FraudTab.RULES -> repository.rules(query.restaurantId).let { result ->
                        if (token == revision) mutable.update { it.copy(rules = result) }
                    }
                }
                if (token == revision) mutable.update { it.copy(stale = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed refresh keeps what is already shown.
                if (token == revision) mutable.update { it.copy(stale = true, error = message(e), canRead = it.canRead && !denied(e)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    /** The next page of the open list, near its end. */
    fun loadMore() {
        val current = state.value
        val query = query(current) ?: return
        if (!active || current.loading || current.loadingMore) return
        val token = revision
        when (current.tab) {
            FraudTab.ALERTS -> if (!current.alertsHasNext) return
            FraudTab.ACTIVITY -> if (!current.activityHasNext) return
            else -> return
        }
        mutable.update { it.copy(loadingMore = true) }
        work.launch {
            try {
                if (current.tab == FraudTab.ALERTS) {
                    val result = repository.alerts(query, current.filter, current.alertPages, FRAUD_PAGE_SIZE)
                    if (token == revision) mutable.update {
                        it.copy(alerts = (it.alerts + result.items).distinctBy { alert -> alert.key }, alertPages = it.alertPages + 1,
                            alertsTotal = result.totalElements, alertsHasNext = result.hasNext, truncated = result.truncated, error = null)
                    }
                } else {
                    val result = repository.activity(query, current.activityType, current.activityStaffId, current.activityPages, FRAUD_PAGE_SIZE)
                    if (token == revision) mutable.update {
                        it.copy(activity = it.activity + result.items, activityPages = it.activityPages + 1,
                            activityTotal = result.totalElements, activityHasNext = result.hasNext, error = null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The list stays; the screen offers "Try again".
                if (token == revision) mutable.update { it.copy(error = message(e)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** Marks an alert reviewed, dismissed, confirmed or open again, with an optional note. */
    fun review(alertKey: String, status: String, note: String?) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (!current.canReview) {
            mutable.update { it.copy(error = "You can't review alerts") }
            return
        }
        if (status !in REVIEW_STATUSES) return
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanNote != null && cleanNote.length > 1000) {
            mutable.update { it.copy(error = "The note can be at most 1000 characters") }
            return
        }
        if (current.reviewing != null) return
        mutable.update { it.copy(reviewing = alertKey) }
        work.launch {
            writes.withLock {
                try {
                    val saved = repository.review(restaurantId, alertKey, FraudReviewRequestDto(status, cleanNote))
                    mutable.update { state ->
                        fun FraudAlertDto.apply() = if (key == alertKey) copy(status = saved.status, reviewNote = saved.reviewNote,
                            reviewedBy = saved.reviewedBy, reviewedByName = saved.reviewedByName, reviewedAt = saved.reviewedAt) else this
                        // An alert that no longer matches the status filter leaves the list.
                        val alerts = state.alerts.map { it.apply() }
                            .filter { state.filter.status == null || it.status == state.filter.status }
                        state.copy(
                            alerts = alerts,
                            alertsTotal = state.alertsTotal - (state.alerts.size - alerts.size),
                            overview = state.overview?.let { overview -> overview.copy(latest = overview.latest.map { it.apply() }) },
                            error = null
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutable.update { it.copy(error = message(e)) }
                } finally {
                    mutable.update { it.copy(reviewing = null) }
                }
            }
        }
    }

    /** Switches one check on or off for the restaurant (saved in its settings, thresholds unchanged). */
    fun setRuleEnabled(rule: String, enabled: Boolean) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val rules = current.rules ?: return
        if (!current.canEditRules) {
            mutable.update { it.copy(error = "Only someone who can change settings can switch checks") }
            return
        }
        if (current.savingRules || rules.rules.none { it.rule == rule }) return
        val disabled = rules.rules.filter { if (it.rule == rule) !enabled else !it.enabled }.map { it.rule }
        val request = com.saporini.mobile_desktop.fraud.data.FraudChecksRequestDto(
            rules.discountPercent, rules.refundAmount, rules.voidsPerDay, rules.tipPercent, rules.cashRefundsPerDay, disabled
        )
        mutable.update { it.copy(savingRules = true) }
        work.launch {
            try {
                repository.updateChecks(restaurantId, request)
                val fresh = repository.rules(restaurantId)
                // Alerts of a check switched off (or on) change too.
                mutable.update { it.copy(rules = fresh, overview = null, alerts = emptyList(), alertPages = 0, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = message(e)) }
            } finally {
                mutable.update { it.copy(savingRules = false) }
            }
        }
    }

    fun clearError() = mutable.update { it.copy(error = null) }

    override fun onDispose() {
        revision++
        active = false
        work.cancel()
    }

    private fun denied(e: Exception) = e is ApiException && (e.status == 401 || e.status == 403)

    private fun message(e: Exception): String = when {
        e is ApiException && (e.status == 401 || e.status == 403) -> "You don't have permission to see fraud alerts."
        e is ApiException && e.status in 400..499 -> e.message
        e is ApiException -> "The server couldn't check for risky actions. Try again."
        else -> "Could not load. Check the connection and try again."
    }
}
