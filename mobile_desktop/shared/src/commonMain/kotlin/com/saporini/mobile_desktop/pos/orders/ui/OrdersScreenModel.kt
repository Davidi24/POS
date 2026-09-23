package com.saporini.mobile_desktop.pos.orders.ui

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.orders.domain.model.Order
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex

/** Screen-independent state. Call setActive(false) on background/navigation and dispose on exit. */
class OrdersScreenModel(
    private val repository: OrderRepository,
    private val sessionManager: SessionManager,
    catalogRepository: com.saporini.mobile_desktop.pos.orders.domain.repository.OrderCatalogRepository? = null,
    tableRepository: com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository? = null
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
    private var detailJob: Job? = null

    init {
        work.launch {
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
            refreshNow()
            repository.observeOrderChanges(scope.restaurantId, scope.branchId)
                .conflate().collect {
                    // Preserve an invalidation received during a write; never interrupt that write.
                    while (isActive) {
                        _state.first { !it.isSaving }
                        if (refreshNow()) break
                        delay(3_000L)
                    }
                }
        }
    }

    fun setFilter(filter: OrderListFilter) {
        listRevision++
        _state.update { it.copy(filter = filter) }
        refresh()
    }

    fun setSearchQuery(query: String) { _state.update { it.copy(searchQuery = query) } }
    fun clearError() { _state.update { it.copy(error = null, refreshWarning = null) } }

    /** The UI must require the user to check current server data before explicitly acknowledging. */
    fun acknowledgeReconciliation() {
        _state.update { it.copy(needsReconciliation = false, error = null) }
    }

    fun refresh(): Job = work.launch { refreshNow() }

    suspend fun refreshNow(allowWhileSaving: Boolean = false): Boolean {
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
            _state.update { it.copy(isLoading = true) }
            try {
                val orders = when (filter.mode) {
                    OrderListMode.OPEN -> repository.getOpenOrders(scope.restaurantId, scope.branchId)
                    OrderListMode.HISTORY -> repository.getOrderHistory(scope.restaurantId, scope.branchId, filter.from, filter.to)
                    OrderListMode.ALL -> repository.getOrders(scope.restaurantId, scope.branchId, filter.from, filter.to, filter.status, filter.customerId)
                }
                if (!isCurrent(token, scope) || requestRevision != listRevision) return false
                _state.update { it.copy(orders = orders, lastRefreshedAt = kotlin.time.Clock.System.now().toString(), error = if (it.needsReconciliation) it.error else null, refreshWarning = null) }
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

    override fun onDispose() { work.cancel() }
}
