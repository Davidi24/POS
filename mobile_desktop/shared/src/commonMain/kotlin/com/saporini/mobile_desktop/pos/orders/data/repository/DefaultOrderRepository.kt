package com.saporini.mobile_desktop.pos.orders.data.repository

import com.saporini.mobile_desktop.pos.orders.domain.validation.validate

import com.saporini.mobile_desktop.pos.orders.data.api.OrderApi
import com.saporini.mobile_desktop.pos.orders.data.dto.*
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderRepository

/** Stateless: never caches another user's orders and never retries writes automatically. */
class DefaultOrderRepository(private val api: OrderApi) : OrderRepository {
    override fun observeOrderChanges(restaurantId: String, branchId: String) =
        api.observeOrderChanges(restaurantId, branchId)

    override suspend fun getOrders(
        restaurantId: String,
        branchId: String,
        from: String?,
        to: String?,
        status: OrderStatus?,
        customerId: String?
    ): List<OrderSummary> {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(branchId.isNotBlank()) { "branchId is required" }
        require((from == null) == (to == null)) { "Provide both from and to, or neither" }
        return api.getOrders(restaurantId = restaurantId, branchId = branchId, from = from, to = to, status = status, customerId = customerId).map { it.toDomain() }
    }

    override suspend fun getOrdersPage(
        restaurantId: String,
        branchId: String,
        from: String?,
        to: String?,
        status: OrderStatus?,
        customerId: String?,
        search: String?,
        historyOnly: Boolean,
        openOnly: Boolean,
        page: Int,
        size: Int
    ): OrderPage {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(branchId.isNotBlank()) { "branchId is required" }
        require((from == null) == (to == null)) { "Provide both from and to, or neither" }
        require(page >= 0 && size in 1..100) { "Page must be non-negative and size between 1 and 100" }
        require(!(historyOnly && openOnly)) { "historyOnly and openOnly cannot both be true" }
        val result = api.getOrdersPage(restaurantId, branchId, from, to, status, customerId, search, historyOnly, openOnly, page, size)
        require(result.page == page && result.size == size && result.items.size <= size && result.totalElements >= 0)
        require(!result.hasNext || result.items.isNotEmpty())
        return OrderPage(result.items.map { it.toDomain() }, result.page, result.size, result.totalElements, result.hasNext)
    }

    override suspend fun getOpenOrders(
        restaurantId: String,
        branchId: String
    ): List<OrderSummary> {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(branchId.isNotBlank()) { "branchId is required" }
        return api.getOpenOrders(restaurantId = restaurantId, branchId = branchId).map { it.toDomain() }
    }

    override suspend fun getOrderHistory(
        restaurantId: String,
        branchId: String,
        from: String?,
        to: String?
    ): List<OrderSummary> {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(branchId.isNotBlank()) { "branchId is required" }
        require((from == null) == (to == null)) { "Provide both from and to, or neither" }
        return api.getOrderHistory(restaurantId = restaurantId, branchId = branchId, from = from, to = to).map { it.toDomain() }
    }

    override suspend fun getCurrentTableOrder(
        restaurantId: String,
        branchId: String,
        tableId: String
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(branchId.isNotBlank()) { "branchId is required" }
        require(tableId.isNotBlank()) { "tableId is required" }
        return api.getCurrentTableOrder(restaurantId = restaurantId, branchId = branchId, tableId = tableId).toDomain()
    }

    override suspend fun createOrder(
        restaurantId: String,
        branchId: String,
        request: CreateOrderInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(branchId.isNotBlank()) { "branchId is required" }
        require(request.branchId == null || request.branchId == branchId) { "Order branch must match the selected branch" }
        request.validate()
        return api.createOrder(restaurantId = restaurantId, branchId = branchId, request = request.toDto()).toDomain()
    }

    override suspend fun createTableOrder(
        restaurantId: String,
        branchId: String,
        tableId: String,
        request: CreateOrderInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(branchId.isNotBlank()) { "branchId is required" }
        require(tableId.isNotBlank()) { "tableId is required" }
        require(request.branchId == null || request.branchId == branchId) { "Order branch must match the selected branch" }
        require(request.tableId == null || request.tableId == tableId) { "Order table must match the selected table" }
        request.validate()
        return api.createTableOrder(restaurantId = restaurantId, branchId = branchId, tableId = tableId, request = request.toDto()).toDomain()
    }

    override suspend fun getKitchenBoard(
        restaurantId: String,
        branchId: String
    ): List<OrderSummary> {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(branchId.isNotBlank()) { "branchId is required" }
        return api.getKitchenBoard(restaurantId = restaurantId, branchId = branchId).map { it.toDomain() }
    }

    override suspend fun exportOrders(
        restaurantId: String,
        branchId: String,
        from: String?,
        to: String?
    ): OrderExport {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(branchId.isNotBlank()) { "branchId is required" }
        require((from == null) == (to == null)) { "Provide both from and to, or neither" }
        return api.exportOrders(restaurantId = restaurantId, branchId = branchId, from = from, to = to).toDomain()
    }

    override suspend fun getRestaurantOrders(
        restaurantId: String,
        from: String?,
        to: String?
    ): List<OrderSummary> {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require((from == null) == (to == null)) { "Provide both from and to, or neither" }
        return api.getRestaurantOrders(restaurantId = restaurantId, from = from, to = to).map { it.toDomain() }
    }

    override suspend fun getOrder(
        restaurantId: String,
        orderId: String
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        return api.getOrder(restaurantId = restaurantId, orderId = orderId).toDomain()
    }

    override suspend fun getOrderByNumber(
        restaurantId: String,
        orderNumber: String
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        return api.getOrderByNumber(restaurantId = restaurantId, orderNumber = orderNumber).toDomain()
    }

    override suspend fun getCustomerOrders(
        restaurantId: String,
        customerId: String
    ): List<OrderSummary> {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(customerId.isNotBlank()) { "customerId is required" }
        return api.getCustomerOrders(restaurantId = restaurantId, customerId = customerId).map { it.toDomain() }
    }

    override suspend fun updateOrder(
        restaurantId: String,
        orderId: String,
        request: UpdateOrderInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.updateOrder(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun updateOrderCustomer(
        restaurantId: String,
        orderId: String,
        request: OrderCustomerInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.updateOrderCustomer(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun updateOrderTable(
        restaurantId: String,
        orderId: String,
        request: OrderTableInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.updateOrderTable(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun updateOrderReservation(
        restaurantId: String,
        orderId: String,
        request: OrderReservationInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.updateOrderReservation(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun validateOrder(
        restaurantId: String,
        request: CreateOrderInput
    ): OrderValidation {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        request.validate()
        return api.validateOrder(restaurantId = restaurantId, request = request.toDto()).toDomain()
    }

    override suspend fun nextOrderNumber(
        restaurantId: String
    ): OrderNextNumber {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        return api.nextOrderNumber(restaurantId = restaurantId).toDomain()
    }

    override suspend fun openOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.openOrder(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun sendToKitchen(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.sendToKitchen(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun markOrderReady(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.markOrderReady(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun fulfillOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.fulfillOrder(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun closeOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.closeOrder(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun reopenOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.reopenOrder(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun cancelOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.cancelOrder(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun voidOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.voidOrder(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun mergeOrders(
        restaurantId: String,
        orderId: String,
        request: OrderMergeInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.mergeOrders(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun transferOrderTable(
        restaurantId: String,
        orderId: String,
        request: OrderTransferTableInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.transferOrderTable(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun transferOrderBranch(
        restaurantId: String,
        orderId: String,
        request: OrderTransferBranchInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.transferOrderBranch(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun getItems(
        restaurantId: String,
        orderId: String
    ): List<OrderLineItem> {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        return api.getItems(restaurantId = restaurantId, orderId = orderId).map { it.toDomain() }
    }

    override suspend fun addItem(
        restaurantId: String,
        orderId: String,
        request: CreateOrderLineItemInput
    ): OrderLineItem {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.addItem(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun getItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String
    ): OrderLineItem {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        return api.getItem(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId).toDomain()
    }

    override suspend fun updateItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: CreateOrderLineItemInput
    ): OrderLineItem {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        request.validate()
        return api.updateItem(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId, request = request.toDto()).toDomain()
    }

    override suspend fun updateItemQuantity(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderLineItemQuantityInput
    ): OrderLineItem {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        request.validate()
        return api.updateItemQuantity(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId, request = request.toDto()).toDomain()
    }

    override suspend fun updateItemNotes(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderLineItemNotesInput
    ): OrderLineItem {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        request.validate()
        return api.updateItemNotes(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId, request = request.toDto()).toDomain()
    }

    override suspend fun updateItemStatus(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: UpdateOrderLineItemStatusInput
    ): OrderLineItem {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        request.validate()
        return api.updateItemStatus(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId, request = request.toDto()).toDomain()
    }

    override suspend fun fireItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionInput
    ): OrderLineItem {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        request.validate()
        return api.fireItem(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId, request = request.toDto()).toDomain()
    }

    override suspend fun readyItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionInput
    ): OrderLineItem {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        request.validate()
        return api.readyItem(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId, request = request.toDto()).toDomain()
    }

    override suspend fun fulfillItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionInput
    ): OrderLineItem {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        request.validate()
        return api.fulfillItem(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId, request = request.toDto()).toDomain()
    }

    override suspend fun voidItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionInput
    ): OrderLineItem {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        request.validate()
        return api.voidItem(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId, request = request.toDto()).toDomain()
    }

    override suspend fun getItemOptions(
        restaurantId: String,
        orderId: String,
        lineItemId: String
    ): List<OrderItemOption> {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        return api.getItemOptions(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId).map { it.toDomain() }
    }

    override suspend fun addItemOption(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: CreateOrderItemOptionInput
    ): OrderItemOption {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        request.validate()
        return api.addItemOption(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId, request = request.toDto()).toDomain()
    }

    override suspend fun updateItemOption(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        optionId: String,
        request: CreateOrderItemOptionInput
    ): OrderItemOption {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        require(optionId.isNotBlank()) { "optionId is required" }
        request.validate()
        return api.updateItemOption(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId, optionId = optionId, request = request.toDto()).toDomain()
    }

    override suspend fun deleteItemOption(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        optionId: String
    ): Unit {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(lineItemId.isNotBlank()) { "lineItemId is required" }
        require(optionId.isNotBlank()) { "optionId is required" }
        api.deleteItemOption(restaurantId = restaurantId, orderId = orderId, lineItemId = lineItemId, optionId = optionId)
    }

    override suspend fun getDiscounts(
        restaurantId: String,
        orderId: String
    ): List<OrderDiscount> {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        return api.getDiscounts(restaurantId = restaurantId, orderId = orderId).map { it.toDomain() }
    }

    override suspend fun addDiscount(
        restaurantId: String,
        orderId: String,
        request: CreateOrderDiscountInput
    ): OrderDiscount {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.addDiscount(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun updateDiscount(
        restaurantId: String,
        orderId: String,
        discountId: String,
        request: CreateOrderDiscountInput
    ): OrderDiscount {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(discountId.isNotBlank()) { "discountId is required" }
        request.validate()
        return api.updateDiscount(restaurantId = restaurantId, orderId = orderId, discountId = discountId, request = request.toDto()).toDomain()
    }

    override suspend fun deleteDiscount(
        restaurantId: String,
        orderId: String,
        discountId: String
    ): Unit {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        require(discountId.isNotBlank()) { "discountId is required" }
        api.deleteDiscount(restaurantId = restaurantId, orderId = orderId, discountId = discountId)
    }

    override suspend fun getEvents(
        restaurantId: String,
        orderId: String
    ): List<OrderEvent> {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        return api.getEvents(restaurantId = restaurantId, orderId = orderId).map { it.toDomain() }
    }

    override suspend fun addNoteEvent(
        restaurantId: String,
        orderId: String,
        request: OrderActionInput
    ): OrderEvent {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.addNoteEvent(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun getTimeline(
        restaurantId: String,
        orderId: String
    ): List<OrderEvent> {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        return api.getTimeline(restaurantId = restaurantId, orderId = orderId).map { it.toDomain() }
    }

    override suspend fun getAudit(
        restaurantId: String,
        orderId: String
    ): OrderAudit {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        return api.getAudit(restaurantId = restaurantId, orderId = orderId).toDomain()
    }

    override suspend fun getTotals(
        restaurantId: String,
        orderId: String
    ): OrderTotals {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        return api.getTotals(restaurantId = restaurantId, orderId = orderId).toDomain()
    }

    override suspend fun splitPreview(
        restaurantId: String,
        orderId: String,
        request: OrderSplitInput
    ): OrderSplitPreview {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.splitPreview(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun splitOrder(
        restaurantId: String,
        orderId: String,
        request: OrderSplitInput
    ): Order {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        request.validate()
        return api.splitOrder(restaurantId = restaurantId, orderId = orderId, request = request.toDto()).toDomain()
    }

    override suspend fun getSummary(
        restaurantId: String,
        branchId: String?,
        from: String?,
        to: String?
    ): OrderMetrics {
        require(restaurantId.isNotBlank()) { "restaurantId is required" }
        require((from == null) == (to == null)) { "Provide both from and to, or neither" }
        return api.getSummary(restaurantId = restaurantId, branchId = branchId, from = from, to = to).toDomain()
    }

}
