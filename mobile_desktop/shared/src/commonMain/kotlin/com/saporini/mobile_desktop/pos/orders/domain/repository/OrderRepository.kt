package com.saporini.mobile_desktop.pos.orders.domain.repository

import com.saporini.mobile_desktop.pos.orders.domain.model.*

interface OrderRepository {
    /** Connected/change invalidations; offline implementations may omit a stream. */
    fun observeOrderChanges(restaurantId: String, branchId: String): kotlinx.coroutines.flow.Flow<Unit> =
        kotlinx.coroutines.flow.emptyFlow()

    suspend fun getOrders(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null,
        status: OrderStatus? = null,
        customerId: String? = null
    ): List<OrderSummary>

    suspend fun getOrdersPage(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null,
        status: OrderStatus? = null,
        customerId: String? = null,
        search: String? = null,
        historyOnly: Boolean = false,
        openOnly: Boolean = false,
        page: Int = 0,
        size: Int = 50
    ): OrderPage {
        require(page >= 0 && size in 1..100)
        val all = if (historyOnly) getOrderHistory(restaurantId, branchId, from, to)
        else getOrders(restaurantId, branchId, from, to, status, customerId)
        val statusFiltered = if (openOnly) all.filter { it.status == OrderStatus.DRAFT || it.status == OrderStatus.OPEN } else all
        val filtered = if (search.isNullOrBlank()) statusFiltered else statusFiltered.filter { order ->
            listOfNotNull(order.orderNumber, order.tableNumber, order.tableName, order.customerName, order.notes)
                .any { it.contains(search.trim(), ignoreCase = true) }
        }
        val items = filtered.drop(page * size).take(size)
        return OrderPage(items, page, size, filtered.size.toLong(), (page + 1) * size < filtered.size)
    }

    suspend fun getOpenOrders(
        restaurantId: String,
        branchId: String
    ): List<OrderSummary>

    suspend fun getOrderHistory(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null
    ): List<OrderSummary>

    suspend fun getCurrentTableOrder(
        restaurantId: String,
        branchId: String,
        tableId: String
    ): Order

    suspend fun createOrder(
        restaurantId: String,
        branchId: String,
        request: CreateOrderInput
    ): Order

    suspend fun createTableOrder(
        restaurantId: String,
        branchId: String,
        tableId: String,
        request: CreateOrderInput
    ): Order

    suspend fun getKitchenBoard(
        restaurantId: String,
        branchId: String
    ): List<OrderSummary>

    suspend fun exportOrders(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null
    ): OrderExport

    suspend fun getRestaurantOrders(
        restaurantId: String,
        from: String? = null,
        to: String? = null
    ): List<OrderSummary>

    suspend fun getOrder(
        restaurantId: String,
        orderId: String
    ): Order

    suspend fun getOrderByNumber(
        restaurantId: String,
        orderNumber: String
    ): Order

    suspend fun getCustomerOrders(
        restaurantId: String,
        customerId: String
    ): List<OrderSummary>

    suspend fun updateOrder(
        restaurantId: String,
        orderId: String,
        request: UpdateOrderInput
    ): Order

    suspend fun updateOrderCustomer(
        restaurantId: String,
        orderId: String,
        request: OrderCustomerInput
    ): Order

    suspend fun updateOrderTable(
        restaurantId: String,
        orderId: String,
        request: OrderTableInput
    ): Order

    suspend fun updateOrderReservation(
        restaurantId: String,
        orderId: String,
        request: OrderReservationInput
    ): Order

    suspend fun validateOrder(
        restaurantId: String,
        request: CreateOrderInput
    ): OrderValidation

    suspend fun nextOrderNumber(
        restaurantId: String
    ): OrderNextNumber

    suspend fun openOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Order

    suspend fun sendToKitchen(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Order

    suspend fun markOrderReady(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Order

    suspend fun fulfillOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Order

    suspend fun closeOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Order

    suspend fun reopenOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Order

    suspend fun cancelOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Order

    suspend fun voidOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Order

    suspend fun mergeOrders(
        restaurantId: String,
        orderId: String,
        request: OrderMergeInput
    ): Order

    suspend fun transferOrderTable(
        restaurantId: String,
        orderId: String,
        request: OrderTransferTableInput
    ): Order

    suspend fun transferOrderBranch(
        restaurantId: String,
        orderId: String,
        request: OrderTransferBranchInput
    ): Order

    suspend fun getItems(
        restaurantId: String,
        orderId: String
    ): List<OrderLineItem>

    suspend fun addItem(
        restaurantId: String,
        orderId: String,
        request: CreateOrderLineItemInput
    ): OrderLineItem

    suspend fun getItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String
    ): OrderLineItem

    suspend fun updateItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: CreateOrderLineItemInput
    ): OrderLineItem

    suspend fun updateItemQuantity(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderLineItemQuantityInput
    ): OrderLineItem

    suspend fun updateItemNotes(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderLineItemNotesInput
    ): OrderLineItem

    suspend fun updateItemStatus(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: UpdateOrderLineItemStatusInput
    ): OrderLineItem

    suspend fun fireItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionInput = OrderActionInput()
    ): OrderLineItem

    suspend fun readyItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionInput = OrderActionInput()
    ): OrderLineItem

    suspend fun fulfillItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionInput = OrderActionInput()
    ): OrderLineItem

    suspend fun voidItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionInput = OrderActionInput()
    ): OrderLineItem

    suspend fun getItemOptions(
        restaurantId: String,
        orderId: String,
        lineItemId: String
    ): List<OrderItemOption>

    suspend fun addItemOption(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: CreateOrderItemOptionInput
    ): OrderItemOption

    suspend fun updateItemOption(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        optionId: String,
        request: CreateOrderItemOptionInput
    ): OrderItemOption

    suspend fun deleteItemOption(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        optionId: String
    ): Unit

    suspend fun getDiscounts(
        restaurantId: String,
        orderId: String
    ): List<OrderDiscount>

    suspend fun addDiscount(
        restaurantId: String,
        orderId: String,
        request: CreateOrderDiscountInput
    ): OrderDiscount

    suspend fun updateDiscount(
        restaurantId: String,
        orderId: String,
        discountId: String,
        request: CreateOrderDiscountInput
    ): OrderDiscount

    suspend fun deleteDiscount(
        restaurantId: String,
        orderId: String,
        discountId: String
    ): Unit

    suspend fun getEvents(
        restaurantId: String,
        orderId: String
    ): List<OrderEvent>

    suspend fun addNoteEvent(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): OrderEvent

    suspend fun getTimeline(
        restaurantId: String,
        orderId: String
    ): List<OrderEvent>

    suspend fun getAudit(
        restaurantId: String,
        orderId: String
    ): OrderAudit

    suspend fun getTotals(
        restaurantId: String,
        orderId: String
    ): OrderTotals

    suspend fun splitPreview(
        restaurantId: String,
        orderId: String,
        request: OrderSplitInput
    ): OrderSplitPreview

    suspend fun splitOrder(
        restaurantId: String,
        orderId: String,
        request: OrderSplitInput
    ): Order

    suspend fun getSummary(
        restaurantId: String,
        branchId: String? = null,
        from: String? = null,
        to: String? = null
    ): OrderMetrics

}
