package com.saporini.mobile_desktop.pos.orders.ui

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.orders.domain.model.Order
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderStatus
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex

/** Screen-independent state. Call setActive(false) on background/navigation and dispose on exit. */
// How often open orders are checked against the server besides the live stream.
private const val RECONCILE_MILLIS = 15_000L
private const val ORDER_LIST_PAGE_SIZE = 50

class OrdersScreenModel(
    private val repository: OrderRepository,
    private val sessionManager: SessionManager,
    catalogRepository: com.saporini.mobile_desktop.pos.orders.domain.repository.OrderCatalogRepository? = null,
    tableRepository: com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository? = null,
    private val historyRepository: com.saporini.mobile_desktop.pos.history.OrderHistoryRepository? = null,
    val historyOnly: Boolean = false
) : ScreenModel {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()
    val operations = OrderOperations(this, repository)
    val catalog: OrderCatalogQueries? = if (catalogRepository != null && tableRepository != null)
        OrderCatalogQueries(this, catalogRepository, tableRepository) else null
    private val mutationMutex = Mutex()
    private val refreshMutex = Mutex()
    private var revision = 0L
    private var listRevision = 0L
    private var detailRevision = 0L
    private var active = false
    private var streamJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private val history = historyRepository?.let { com.saporini.mobile_desktop.pos.history.OrderHistoryScreenModel(it, sessionManager) }

    init {
        if (historyOnly) requireNotNull(history) { "History repository is required" }
        if (historyOnly) work.launch {
            history!!.state.collect { h ->
                _state.value = OrdersUiState(scope = h.scope, permissions = if(h.canRead) setOf("ORDER_READ") else emptySet(),
                    orders = h.items, selectedOrderId = h.selectedId, selectedOrder = h.detail,
                    filter = OrderListFilter(mode = OrderListMode.HISTORY, from = h.filter.from?.toString(), to = h.filter.to?.toString(),
                        status = h.filter.status, customerId = h.filter.customerId),
                    searchQuery = h.filter.search, isLoading = h.loading, isLoadingDetails = h.detailLoading,
                    error = (h.error ?: h.detailError)?.let { OrderFailure(OrderErrorKind.CONNECTION, it) },
                    historyTotal = h.totalElements, historyLoadedPages = h.loadedPages,
                    historyHasNext = h.hasNext, historyLoadingMore = h.loadingMore)
            }
        }
        if (!historyOnly) work.launch {
            sessionManager.currentUser.collectLatest { user ->
                revision++
                detailRevision++
                detailJob?.cancel()
                val scope = user?.let {
                    val restaurant = it.restaurantId?.takeIf(String::isNotBlank)
                    val branch = it.defaultBranchId?.takeIf(String::isNotBlank)
                    if (restaurant == null || branch == null) null else OrdersScope(it.id, restaurant, branch)
                }
                _state.value = OrdersUiState(scope = scope, permissions = user?.permissions.orEmpty().toSet())
                restartLiveUpdates()
            }
        }
    }

    /** Hold an SSE subscription only for the active user/branch while visible. */
    fun setActive(value: Boolean) {
        if (historyOnly) { history!!.setActive(value); return }
        if (active == value) return
        active = value
        restartLiveUpdates()
    }

    private fun restartLiveUpdates() {
        streamJob?.cancel()
        streamJob = null
        val scope = _state.value.scope ?: return
        if (!active || !_state.value.can("ORDER_READ")) return
        streamJob = work.launch {
            kotlinx.coroutines.supervisorScope {
                launch {
                    refreshNow()
                    try {
                        repository.observeOrderChanges(scope.restaurantId, scope.branchId)
                            .conflate().collect {
                                // Preserve an invalidation received during a write; never interrupt that write.
                                while (isActive) {
                                    _state.first { !it.isSaving }
                                    if (refreshNow()) break
                                    delay(3_000L)
                                }
                            }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Without the live stream the regular check below keeps the list current.
                    }
                }
                // A live stream can drop events unnoticed (proxies, sleep); a slow regular check catches up.
                launch {
                    while (isActive) {
                        delay(RECONCILE_MILLIS)
                        _state.first { !it.isSaving }
                        refreshNow()
                    }
                }
            }
        }
    }

    fun setFilter(filter: OrderListFilter) {
        if (historyOnly) {
            history!!.setFilter(history.state.value.filter.copy(from = filter.from?.let { kotlin.time.Instant.parse(it) },
                to = filter.to?.let { kotlin.time.Instant.parse(it) }, status = filter.status, customerId = filter.customerId)); return
        }
        searchJob?.cancel()
        listRevision++
        _state.update { it.copy(filter = filter, orders = emptyList(), historyTotal = 0,
            historyLoadedPages = 0, historyHasNext = false, historyLoadingMore = false) }
        refresh()
    }

    fun setSearchQuery(query: String) {
        if (historyOnly) { history!!.setFilter(history.state.value.filter.copy(search = query)); return }
        if (query == _state.value.searchQuery) return
        searchJob?.cancel()
        listRevision++
        _state.update { it.copy(searchQuery = query, orders = emptyList(), historyTotal = 0,
            historyLoadedPages = 0, historyHasNext = false, historyLoadingMore = false) }
        searchJob = work.launch { delay(250); refreshNow() }
    }
    fun clearError() { if (historyOnly) { history!!.clearError(); return }; _state.update { it.copy(error = null, refreshWarning = null) } }

    /** The UI must require the user to check current server data before explicitly acknowledging. */
    fun acknowledgeReconciliation() {
        _state.update { it.copy(needsReconciliation = false, error = null) }
    }

    fun refresh(): Job = work.launch { refreshNow() }

    suspend fun refreshNow(allowWhileSaving: Boolean = false): Boolean {
        if (historyOnly) return history!!.refreshNow()
        if (_state.value.isSaving && !allowWhileSaving) return false
        refreshMutex.lock()
        try {
            if (_state.value.isSaving && !allowWhileSaving) return false
            val token = revision
            val requestRevision = listRevision
            val scope = try { requireScope("ORDER_READ") } catch (e: Exception) {
                _state.update { it.copy(error = failure(e, false)) }
                return false
            }
            val filter = _state.value.filter
            val search = _state.value.searchQuery
            val pagesToRefresh = _state.value.historyLoadedPages.coerceAtLeast(1)
            _state.update { it.copy(isLoading = true) }
            try {
                val orders: List<com.saporini.mobile_desktop.pos.orders.domain.model.OrderSummary>
                var total = 0L
                var loadedPages = 0
                var hasNext = false
                val collected = mutableListOf<com.saporini.mobile_desktop.pos.orders.domain.model.OrderSummary>()
                var last: com.saporini.mobile_desktop.pos.orders.domain.model.OrderPage? = null
                for (page in 0 until pagesToRefresh) {
                    val result = repository.getOrdersPage(
                        restaurantId = scope.restaurantId, branchId = scope.branchId,
                        from = filter.from, to = filter.to,
                        status = filter.status, customerId = filter.customerId,
                        search = search, historyOnly = filter.mode == OrderListMode.HISTORY,
                        openOnly = filter.mode == OrderListMode.OPEN,
                        page = page, size = ORDER_LIST_PAGE_SIZE
                    )
                    if (!isCurrent(token, scope) || requestRevision != listRevision) return false
                    require(result.page == page && result.size == ORDER_LIST_PAGE_SIZE && result.items.size <= ORDER_LIST_PAGE_SIZE)
                    require(result.totalElements >= 0 && (!result.hasNext || result.items.isNotEmpty()))
                    result.items.forEach { order ->
                        require(order.restaurantId == scope.restaurantId && order.branchId == scope.branchId)
                        if (filter.mode == OrderListMode.HISTORY) require(order.status in setOf(OrderStatus.CLOSED, OrderStatus.CANCELLED, OrderStatus.VOIDED))
                        if (filter.mode == OrderListMode.OPEN) require(order.status in setOf(OrderStatus.DRAFT, OrderStatus.OPEN))
                    }
                    collected += result.items
                    last = result
                    loadedPages = page + 1
                    if (!result.hasNext) break
                }
                val lastPage = requireNotNull(last)
                orders = collected.distinctBy { it.id }
                total = lastPage.totalElements
                hasNext = lastPage.hasNext
                if (!isCurrent(token, scope) || requestRevision != listRevision) return false
                _state.update { it.copy(orders = orders, historyTotal = total, historyLoadedPages = loadedPages,
                    historyHasNext = hasNext, historyLoadingMore = false,
                    lastRefreshedAt = kotlin.time.Clock.System.now().toString(), error = if (it.needsReconciliation) it.error else null, refreshWarning = null) }
                val id = _state.value.selectedOrderId
                if (id != null) fetchDetails(scope, token, id, detailRevision)
                return true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (isCurrent(token, scope)) _state.update { it.copy(error = failure(e, false)) }
                return false
            } finally {
                if (isCurrent(token, scope)) _state.update { it.copy(isLoading = false) }
            }
        } finally { refreshMutex.unlock() }
    }

    fun selectOrder(orderId: String) {
        if (historyOnly) { history!!.selectOrder(orderId); return }
        require(orderId.isNotBlank())
        detailRevision++
        detailJob?.cancel()
        _state.update { it.copy(selectedOrderId = orderId, selectedOrder = null, isLoadingDetails = true, error = null) }
        val detailToken = detailRevision
        val token = revision
        detailJob = work.launch {
            try { fetchDetails(requireScope("ORDER_READ"), token, orderId, detailToken) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == revision && detailToken == detailRevision) _state.update { it.copy(error = failure(e, false)) }
            } finally {
                if (token == revision && detailToken == detailRevision) _state.update { it.copy(isLoadingDetails = false) }
            }
        }
    }

    fun closeOrderDetails() {
        if (historyOnly) { history!!.closeDetails(); return }
        detailRevision++
        detailJob?.cancel()
        _state.update { it.copy(selectedOrderId = null, selectedOrder = null, isLoadingDetails = false) }
    }

    private suspend fun fetchDetails(scope: OrdersScope, token: Long, id: String, detailToken: Long) {
        try {
            val order = repository.getOrder(scope.restaurantId, id)
            require(order.restaurantId == scope.restaurantId && order.branchId == scope.branchId) { "Order belongs to another branch" }
            if (isCurrent(token, scope) && detailToken == detailRevision && _state.value.selectedOrderId == id) {
                _state.update { it.copy(selectedOrder = order) }
            }
        } catch (e: ApiException) {
            if (e.status == 404 && isCurrent(token, scope) && detailToken == detailRevision) closeOrderDetails()
            throw e
        }
    }

    internal suspend fun <T> query(permission: String, action: suspend (OrdersScope) -> T): Result<T> {
        val token = revision
        return try {
            val scope = requireScope(permission)
            val result = action(scope)
            if (!isCurrent(token, scope)) throw CancellationException("Order session changed")
            Result.success(result)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(OrderOperationException(failure(e, false))) }
    }

    internal suspend fun <T> mutate(
        permission: String,
        operation: String,
        selectResult: Boolean = false,
        action: suspend (OrdersScope) -> T
    ): Result<T> {
        if (!mutationMutex.tryLock()) return Result.failure(OrderOperationException(OrderFailure(OrderErrorKind.CONFLICT, "Another order change is still saving")))
        val token = revision
        var started = false
        try {
            val scope = requireScope(permission)
            check(!_state.value.needsReconciliation) { "Refresh and check the last change before trying another update" }
            listRevision++
            detailRevision++
            detailJob?.cancel()
            val selection = _state.value.selectedOrderId
            val detailToken = detailRevision
            _state.update { it.copy(isSaving = true, isLoadingDetails = false, error = null, lastSuccessfulOperation = null) }
            started = true
            val result = action(scope)
            if (!isCurrent(token, scope)) throw CancellationException("Order session changed")
            if (result is Order && detailToken == detailRevision && (selectResult || result.id == selection)) {
                if (result.branchId == scope.branchId) {
                    _state.update { it.copy(selectedOrderId = result.id, selectedOrder = result) }
                } else closeOrderDetails()
            }
            _state.update { it.copy(lastSuccessfulOperation = operation) }
            // The write is already confirmed. A refresh failure must not encourage repeating it.
            val refreshed = refreshNow(allowWhileSaving = true)
            if (!isCurrent(token, scope)) throw CancellationException("Order session changed")
            if (!refreshed) _state.update { it.copy(error = null, refreshWarning = "Change saved. Refresh to load the latest orders.") }
            return Result.success(result)
        } catch (e: CancellationException) {
            if (started && token == revision && _state.value.lastSuccessfulOperation == null) {
                _state.update { it.copy(needsReconciliation = true, error = OrderFailure(OrderErrorKind.CONNECTION, "The change may have reached the server. Refresh and check before trying again.", true)) }
            }
            throw e
        } catch (e: Exception) {
            val problem = failure(e, started)
            if (token == revision) _state.update { it.copy(error = problem, needsReconciliation = it.needsReconciliation || problem.operationMayHaveSucceeded) }
            return Result.failure(OrderOperationException(problem))
        } finally {
            if (token == revision) _state.update { it.copy(isSaving = false) }
            mutationMutex.unlock()
        }
    }

    private fun requireScope(permission: String): OrdersScope {
        if (historyOnly && permission != "ORDER_READ") throw OrderOperationException(OrderFailure(OrderErrorKind.PERMISSION, "History is read-only"))
        val user = sessionManager.currentUser.value
        val scope = _state.value.scope
        if (user == null || scope == null || user.id != scope.userId || user.restaurantId != scope.restaurantId || user.defaultBranchId != scope.branchId) {
            throw OrderOperationException(OrderFailure(OrderErrorKind.SESSION, "Sign in with an assigned restaurant and branch"))
        }
        if (permission !in user.permissions) throw OrderOperationException(OrderFailure(OrderErrorKind.PERMISSION, "You do not have permission for this order action"))
        return scope
    }

    private fun isCurrent(token: Long, scope: OrdersScope): Boolean {
        val user = sessionManager.currentUser.value
        return token == revision && _state.value.scope == scope && user?.id == scope.userId && user.restaurantId == scope.restaurantId && user.defaultBranchId == scope.branchId
    }

    private fun failure(error: Exception, writeStarted: Boolean): OrderFailure = when (error) {
        is OrderOperationException -> error.failure
        is kotlinx.serialization.SerializationException -> OrderFailure(OrderErrorKind.SERVER, "Could not read the server response. Refresh to check the latest order.", writeStarted)
        is IllegalArgumentException, is IllegalStateException -> OrderFailure(OrderErrorKind.VALIDATION, error.message ?: "Check the order details")
        is ApiException -> OrderFailure(when (error.status) {
            401 -> OrderErrorKind.SESSION
            403 -> OrderErrorKind.PERMISSION
            404 -> OrderErrorKind.NOT_FOUND
            409 -> OrderErrorKind.CONFLICT
            in 400..499 -> OrderErrorKind.VALIDATION
            else -> OrderErrorKind.SERVER
        }, error.message, writeStarted && (error.status >= 500 || error.status == 408))
        else -> OrderFailure(OrderErrorKind.CONNECTION,
            if (writeStarted) "The result is uncertain. Refresh and check before trying again." else "Could not load orders. Check your connection and refresh.", writeStarted)
    }

    fun historyMine(mine: Boolean) { if (historyOnly) history!!.setFilter(history.state.value.filter.copy(staffId = if (mine) state.value.scope?.userId else null)) }
    fun loadMoreHistory() {
        if (historyOnly) { history?.clearError(); history?.loadMore(); return }
        val current = _state.value
        if (!current.historyHasNext || current.historyLoadingMore || current.isLoading) return
        work.launch {
            refreshMutex.lock()
            try {
                val old = _state.value
                if (!old.historyHasNext || old.historyLoadingMore) return@launch
                val scope = try { requireScope("ORDER_READ") } catch (e: Exception) {
                    _state.update { it.copy(error = failure(e, false)) }; return@launch
                }
                val token = revision
                val requestRevision = listRevision
                val page = old.historyLoadedPages
                _state.update { it.copy(historyLoadingMore = true, error = null) }
                try {
                    val result = repository.getOrdersPage(
                        restaurantId = scope.restaurantId, branchId = scope.branchId,
                        from = old.filter.from, to = old.filter.to, status = old.filter.status,
                        customerId = old.filter.customerId, search = old.searchQuery,
                        historyOnly = old.filter.mode == OrderListMode.HISTORY,
                        openOnly = old.filter.mode == OrderListMode.OPEN,
                        page = page, size = ORDER_LIST_PAGE_SIZE
                    )
                    if (!isCurrent(token, scope) || requestRevision != listRevision) return@launch
                    require(result.page == page && result.size == ORDER_LIST_PAGE_SIZE && result.items.size <= ORDER_LIST_PAGE_SIZE)
                    require(result.totalElements >= 0 && (!result.hasNext || result.items.isNotEmpty()))
                    result.items.forEach { order ->
                        require(order.restaurantId == scope.restaurantId && order.branchId == scope.branchId)
                        if (old.filter.mode == OrderListMode.HISTORY) require(order.status in setOf(OrderStatus.CLOSED, OrderStatus.CANCELLED, OrderStatus.VOIDED))
                        if (old.filter.mode == OrderListMode.OPEN) require(order.status in setOf(OrderStatus.DRAFT, OrderStatus.OPEN))
                    }
                    _state.update { it.copy(orders = (it.orders + result.items).distinctBy { order -> order.id },
                        historyTotal = result.totalElements, historyLoadedPages = page + 1,
                        historyHasNext = result.hasNext, error = null) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (isCurrent(token, scope)) _state.update { it.copy(error = failure(e, false)) } }
                finally { if (isCurrent(token, scope)) _state.update { it.copy(historyLoadingMore = false) } }
            } finally { refreshMutex.unlock() }
        }
    }
    override fun onDispose() { history?.onDispose(); work.cancel() }
}
