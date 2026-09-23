package com.saporini.mobile_desktop.pos.orders.ui

import com.saporini.mobile_desktop.pos.orders.domain.model.*

enum class OrderListMode { OPEN, HISTORY, ALL }

data class OrderListFilter(
    val mode: OrderListMode = OrderListMode.OPEN,
    val from: String? = null,
    val to: String? = null,
    val status: OrderStatus? = null,
    val customerId: String? = null
) {
    init { require((from == null) == (to == null)) { "Provide both date boundaries" } }
}

data class OrdersScope(val userId: String, val restaurantId: String, val branchId: String)

enum class OrderErrorKind { VALIDATION, PERMISSION, NOT_FOUND, CONFLICT, CONNECTION, SERVER, SESSION }

data class OrderFailure(
    val kind: OrderErrorKind,
    val message: String,
    val operationMayHaveSucceeded: Boolean = false
)

class OrderOperationException(val failure: OrderFailure) : Exception(failure.message)

data class OrdersUiState(
    val scope: OrdersScope? = null,
    val permissions: Set<String> = emptySet(),
    val orders: List<OrderSummary> = emptyList(),
    val selectedOrderId: String? = null,
    val selectedOrder: Order? = null,
    val filter: OrderListFilter = OrderListFilter(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val isSaving: Boolean = false,
    val error: OrderFailure? = null,
    val refreshWarning: String? = null,
    val needsReconciliation: Boolean = false,
    val lastRefreshedAt: String? = null,
    val lastSuccessfulOperation: String? = null
) {
    val visibleOrders: List<OrderSummary>
        get() {
            val query = searchQuery.trim()
            return orders.filter { order ->
                (filter.status == null || order.status == filter.status) &&
                    (filter.customerId == null || order.customerId == filter.customerId) &&
                    (query.isEmpty() || listOfNotNull(order.orderNumber, order.tableNumber,
                        order.tableName, order.customerName).any { it.contains(query, ignoreCase = true) })
            }
        }

    fun can(permission: String): Boolean = permission in permissions
}
