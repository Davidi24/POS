package com.saporini.mobile_desktop.pos.sales

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.orders.ui.OrdersScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

data class MySalesState(val scope: OrdersScope? = null, val canRead: Boolean = false, val canReadOthers: Boolean = false,
    val filter: SalesFilter = SalesFilter(), val report: SalesReport? = null, val currency: String? = null,
    val loading: Boolean = false, val stale: Boolean = true, val error: String? = null) {
    val totals get() = report?.currencies?.firstOrNull { it.currency == currency } ?: report?.currencies?.firstOrNull()
}
class MySalesScreenModel(private val repository: MySalesRepository, session: SessionManager,
    private val reconcileMillis: Long = 30_000) : ScreenModel {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(MySalesState())
    val state = mutable.asStateFlow()
    private val requests = Mutex()
    private var revision = 0L
    private var active = false
    private var live: Job? = null
    init {
        require(reconcileMillis > 0)
        work.launch { session.currentUser.collectLatest { user ->
            revision++
            val scope = user?.takeIf { it.isActive && !it.restaurantId.isNullOrBlank() && !it.defaultBranchId.isNullOrBlank() }
                ?.let { OrdersScope(it.id,it.restaurantId!!,it.defaultBranchId!!) }
            mutable.value = MySalesState(scope = scope, canRead = scope != null && "ORDER_READ" in user?.permissions.orEmpty(),
                canReadOthers = "ORDER_AUDIT" in user?.permissions.orEmpty() && "SHIFT_READ" in user?.permissions.orEmpty())
            restart()
        } }
    }
    fun setActive(value: Boolean) {
        if (value == active) return
        active = value; revision++; mutable.update { it.copy(loading = false) }; restart()
    }
    private fun restart() {
        live?.cancel(); live = null
        val scope = state.value.scope ?: return
        if (!active || !state.value.canRead) return
        live = work.launch { supervisorScope {
            launch { refreshNow() }
            launch { while (isActive) { delay(reconcileMillis); refreshNow() } }
            launch {
                try { repository.changes(scope).conflate().collect { refreshNow() } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { mutable.update { it.copy(stale = true, error = "Live updates disconnected. Retrying automatically.") } }
            }
        } }
    }
    fun date(date: LocalDate?) = filter(state.value.filter.copy(date = date, shiftId = null))
    fun shift(id: String?) = filter(state.value.filter.copy(shiftId = id))
    fun staff(id: String?) = filter(state.value.filter.copy(staffId = id, shiftId = null))
    fun filter(filter: SalesFilter) {
        if (filter.staffId != null && filter.staffId != state.value.scope?.userId && !state.value.canReadOthers) {
            mutable.update { it.copy(error = "You can view only your own sales.") }; return
        }
        if (state.value.filter == filter) { refresh(); return }
        revision++
        mutable.update { it.copy(filter = filter, report = null, loading = false, stale = true, error = null) }
        refresh()
    }
    fun currency(code: String) { if (state.value.report?.currencies?.any { it.currency == code } == true) mutable.update { it.copy(currency = code) } }
    fun refresh() = work.launch { refreshNow() }
    suspend fun refreshNow(): Boolean = requests.withLock {
        val before = state.value; val s = before.scope ?: return@withLock false
        if (!active || !before.canRead) return@withLock false
        val token = revision
        mutable.update { it.copy(loading = true) }
        try {
            val report = repository.report(s,before.filter)
            if (revision != token || !active) return@withLock false
            require(report.restaurantId == s.restaurantId && report.branchId == s.branchId && report.staffId == (before.filter.staffId ?: s.userId))
            require(report.shiftId == before.filter.shiftId && (before.filter.date == null || report.date == before.filter.date.toString()))
            require(report.currencies.map { it.currency }.distinct().size == report.currencies.size)
            mutable.update { it.copy(report = report, stale = false, error = null,
                currency = it.currency?.takeIf { code -> report.currencies.any { c -> c.currency == code } } ?: report.currencies.firstOrNull()?.currency) }
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (token == revision) {
                if (e is ApiException && e.status in setOf(401,403)) mutable.update { it.copy(report = null, canRead = false, stale = true, error = "You do not have permission to view these sales.") }
                else mutable.update { it.copy(stale = true, error = if(e is ApiException) e.message else "Could not load sales. Check your connection and refresh.") }
            }
            false
        } finally { if (revision == token) mutable.update { it.copy(loading = false) } }
    }
    override fun onDispose() { active = false; revision++; work.cancel() }
}
