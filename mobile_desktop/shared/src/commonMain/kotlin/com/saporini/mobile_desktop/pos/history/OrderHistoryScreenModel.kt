package com.saporini.mobile_desktop.pos.history

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.ui.OrdersScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HistoryState(val scope: OrdersScope? = null, val canRead: Boolean = false, val filter: HistoryFilter = HistoryFilter(),
    val items: List<OrderSummary> = emptyList(), val loadedPages: Int = 0, val totalElements: Long = 0, val hasNext: Boolean = false,
    val loading: Boolean = false, val loadingMore: Boolean = false, val stale: Boolean = true, val error: String? = null,
    val selectedId: String? = null, val detail: Order? = null, val detailLoading: Boolean = false, val detailError: String? = null)

/** Independent read-only history. A failed refresh retains all loaded pages; scope/filter changes clear them. */
class OrderHistoryScreenModel(private val repository: OrderHistoryRepository, session: SessionManager,
    private val pageSize: Int = 40, private val reconcileMillis: Long = 30_000) : ScreenModel {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(HistoryState())
    val state = mutable.asStateFlow()
    private val requests = Mutex()
    private var revision = 0L
    private var detailRevision = 0L
    private var active = false
    private var live: Job? = null
    private var detailJob: Job? = null
    init {
        require(pageSize in 1..100 && reconcileMillis > 0)
        work.launch { session.currentUser.collectLatest { user ->
            revision++; detailRevision++; detailJob?.cancel()
            val scope = user?.takeIf { it.isActive && !it.restaurantId.isNullOrBlank() && !it.defaultBranchId.isNullOrBlank() }
                ?.let { OrdersScope(it.id, it.restaurantId!!, it.defaultBranchId!!) }
            mutable.value = HistoryState(scope = scope, canRead = scope != null && "ORDER_READ" in user?.permissions.orEmpty())
            restart()
        } }
    }
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value; revision++; detailRevision++; detailJob?.cancel()
        mutable.update { it.copy(loading = false, loadingMore = false, detailLoading = false) }; restart()
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
    fun setFilter(filter: HistoryFilter) {
        filter.problem()?.let { problem -> mutable.update { it.copy(error = problem) }; return }
        if (filter == state.value.filter) return
        revision++; closeDetails()
        mutable.update { HistoryState(scope = it.scope, canRead = it.canRead, filter = filter) }
        refresh()
    }
    fun clearError() { mutable.update { it.copy(error = null, detailError = null) } }
    fun refresh() = work.launch { refreshNow() }
    suspend fun refreshNow(): Boolean = load(false)
    fun loadMore() = work.launch { load(true) }
    private suspend fun load(append: Boolean): Boolean = requests.withLock {
        val old = state.value; val s = old.scope ?: return@withLock false
        if (!active || !old.canRead || (append && (!old.hasNext || old.error != null))) return@withLock false
        val token = revision
        mutable.update { it.copy(loading = !append, loadingMore = append, error = null) }
        try {
            val result = if (append) old.items.toMutableList() else mutableListOf()
            val pages = if (append) listOf(old.loadedPages) else (0 until old.loadedPages.coerceAtLeast(1)).toList()
            var last: HistoryPage? = null
            for (page in pages) {
                val data = repository.page(s, old.filter, page, pageSize)
                if (token != revision || !active) return@withLock false
                require(data.page == page && data.size == pageSize && data.items.size <= pageSize && data.totalElements >= 0)
                require(!data.hasNext || data.items.isNotEmpty())
                data.items.forEach { require(it.restaurantId == s.restaurantId && it.branchId == s.branchId && it.status in setOf(OrderStatus.CLOSED, OrderStatus.CANCELLED, OrderStatus.VOIDED)) }
                result += data.items; last = data
                if (!data.hasNext) break
            }
            val data = requireNotNull(last)
            mutable.update { it.copy(items = result.distinctBy { order -> order.id }, loadedPages = data.page + 1,
                totalElements = data.totalElements, hasNext = data.hasNext, stale = false, error = null) }
            state.value.selectedId?.let { loadDetail(s, token, it, detailRevision) }
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (token == revision) fail(e); false }
        finally { if (token == revision) mutable.update { it.copy(loading = false, loadingMore = false) } }
    }
    fun selectOrder(id: String) {
        val s = state.value.scope ?: return
        if (!active || !state.value.canRead || id.isBlank()) return
        detailRevision++; detailJob?.cancel()
        val token = revision; val d = detailRevision
        mutable.update { it.copy(selectedId = id, detail = null, detailLoading = true, detailError = null) }
        detailJob = work.launch { requests.withLock { if (token == revision && d == detailRevision && active) loadDetail(s, token, id, d) } }
    }
    fun closeDetails() {
        detailRevision++; detailJob?.cancel()
        mutable.update { it.copy(selectedId = null, detail = null, detailLoading = false, detailError = null) }
    }
    private suspend fun loadDetail(s: OrdersScope, token: Long, id: String, d: Long) {
        fun current() = token == revision && d == detailRevision && state.value.selectedId == id && active
        try {
            val order = repository.detail(s, id)
            require(order.id == id && order.restaurantId == s.restaurantId && order.branchId == s.branchId)
            if (current()) mutable.update { it.copy(detail = order, detailError = null) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (current()) {
            if (e is ApiException && e.status in setOf(401,403)) fail(e)
            else mutable.update { it.copy(detail = if(e is ApiException && e.status == 404) null else it.detail, detailError = "Could not load this order. Refresh to try again.") }
        } } finally { if (current()) mutable.update { it.copy(detailLoading = false) } }
    }
    private fun fail(e: Exception) {
        if (e is ApiException && e.status in setOf(401,403)) {
            revision++; detailRevision++; detailJob?.cancel()
            mutable.update { HistoryState(scope = it.scope, filter = it.filter, error = "You do not have permission to view order history.") }
        } else mutable.update { it.copy(stale = true, error = if(e is ApiException) e.message else "Could not update history. Check your connection and try again.") }
    }
    override fun onDispose() { active = false; revision++; work.cancel() }
}
