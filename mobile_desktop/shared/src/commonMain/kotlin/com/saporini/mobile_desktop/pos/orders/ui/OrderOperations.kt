package com.saporini.mobile_desktop.pos.orders.ui

import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderRepository

/** Typed operations for future screens; scope, permission, write serialization and refresh are centralized. */
class OrderOperations internal constructor(
    private val model: OrdersScreenModel,
    private val repository: OrderRepository
) {
    suspend fun getOrders(
        from: String? = null,
        to: String? = null,
        status: OrderStatus? = null,
        customerId: String? = null
    ): Result<List<OrderSummary>> = model.query("ORDER_READ") { scope ->
        repository.getOrders(restaurantId = scope.restaurantId, branchId = scope.branchId, from = from, to = to, status = status, customerId = customerId)
    }

    suspend fun getOpenOrders(

    ): Result<List<OrderSummary>> = model.query("ORDER_READ") { scope ->
        repository.getOpenOrders(restaurantId = scope.restaurantId, branchId = scope.branchId)
    }

    suspend fun getOrderHistory(
        from: String? = null,
        to: String? = null
    ): Result<List<OrderSummary>> = model.query("ORDER_READ") { scope ->
        repository.getOrderHistory(restaurantId = scope.restaurantId, branchId = scope.branchId, from = from, to = to)
    }

    suspend fun getCurrentTableOrder(
        tableId: String
    ): Result<Order> = model.query("ORDER_READ") { scope ->
        repository.getCurrentTableOrder(restaurantId = scope.restaurantId, branchId = scope.branchId, tableId = tableId)
    }

    suspend fun createOrder(
        request: CreateOrderInput
    ): Result<Order> = model.mutate("ORDER_CREATE", "createOrder", selectResult = true) { scope ->
        repository.createOrder(restaurantId = scope.restaurantId, branchId = scope.branchId, request = request)
    }

    suspend fun createTableOrder(
        tableId: String,
        request: CreateOrderInput
    ): Result<Order> = model.mutate("ORDER_CREATE", "createTableOrder", selectResult = true) { scope ->
        repository.createTableOrder(restaurantId = scope.restaurantId, branchId = scope.branchId, tableId = tableId, request = request)
    }

    suspend fun getKitchenBoard(

    ): Result<List<OrderSummary>> = model.query("ORDER_READ") { scope ->
        repository.getKitchenBoard(restaurantId = scope.restaurantId, branchId = scope.branchId)
    }

    suspend fun exportOrders(
        from: String? = null,
        to: String? = null
    ): Result<OrderExport> = model.query("ORDER_READ") { scope ->
        repository.exportOrders(restaurantId = scope.restaurantId, branchId = scope.branchId, from = from, to = to)
    }

    suspend fun getRestaurantOrders(
        from: String? = null,
        to: String? = null
    ): Result<List<OrderSummary>> = model.query("ORDER_READ") { scope ->
        repository.getRestaurantOrders(restaurantId = scope.restaurantId, from = from, to = to)
    }

    suspend fun getOrder(
        orderId: String
    ): Result<Order> = model.query("ORDER_READ") { scope ->
        repository.getOrder(restaurantId = scope.restaurantId, orderId = orderId)
    }

    suspend fun getOrderByNumber(
        orderNumber: String
    ): Result<Order> = model.query("ORDER_READ") { scope ->
        repository.getOrderByNumber(restaurantId = scope.restaurantId, orderNumber = orderNumber)
    }

    suspend fun getCustomerOrders(
        customerId: String
    ): Result<List<OrderSummary>> = model.query("ORDER_READ") { scope ->
        repository.getCustomerOrders(restaurantId = scope.restaurantId, customerId = customerId)
    }

    suspend fun updateOrder(
        orderId: String,
        request: UpdateOrderInput
    ): Result<Order> = model.mutate("ORDER_UPDATE", "updateOrder", selectResult = false) { scope ->
        repository.updateOrder(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun updateOrderCustomer(
        orderId: String,
        request: OrderCustomerInput
    ): Result<Order> = model.mutate("ORDER_UPDATE", "updateOrderCustomer", selectResult = false) { scope ->
        repository.updateOrderCustomer(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun updateOrderTable(
        orderId: String,
        request: OrderTableInput
    ): Result<Order> = model.mutate("ORDER_TRANSFER", "updateOrderTable", selectResult = false) { scope ->
        repository.updateOrderTable(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun updateOrderReservation(
        orderId: String,
        request: OrderReservationInput
    ): Result<Order> = model.mutate("ORDER_UPDATE", "updateOrderReservation", selectResult = false) { scope ->
        repository.updateOrderReservation(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun validateOrder(
        request: CreateOrderInput
    ): Result<OrderValidation> = model.query("ORDER_CREATE") { scope ->
        repository.validateOrder(restaurantId = scope.restaurantId, request = request)
    }

    suspend fun nextOrderNumber(

    ): Result<OrderNextNumber> = model.mutate("ORDER_CREATE", "nextOrderNumber", selectResult = false) { scope ->
        repository.nextOrderNumber(restaurantId = scope.restaurantId)
    }

    suspend fun openOrder(
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<Order> = model.mutate("ORDER_UPDATE", "openOrder", selectResult = false) { scope ->
        repository.openOrder(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun sendToKitchen(
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<Order> = model.mutate("ORDER_UPDATE", "sendToKitchen", selectResult = false) { scope ->
        repository.sendToKitchen(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun markOrderReady(
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<Order> = model.mutate("ORDER_UPDATE", "markOrderReady", selectResult = false) { scope ->
        repository.markOrderReady(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun fulfillOrder(
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<Order> = model.mutate("ORDER_UPDATE", "fulfillOrder", selectResult = false) { scope ->
        repository.fulfillOrder(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun closeOrder(
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<Order> = model.mutate("ORDER_CLOSE", "closeOrder", selectResult = false) { scope ->
        repository.closeOrder(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun reopenOrder(
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<Order> = model.mutate("ORDER_REOPEN", "reopenOrder", selectResult = false) { scope ->
        repository.reopenOrder(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun cancelOrder(
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<Order> = model.mutate("ORDER_CANCEL", "cancelOrder", selectResult = false) { scope ->
        repository.cancelOrder(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun voidOrder(
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<Order> = model.mutate("ORDER_VOID", "voidOrder", selectResult = false) { scope ->
        repository.voidOrder(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun mergeOrders(
        orderId: String,
        request: OrderMergeInput
    ): Result<Order> = model.mutate("ORDER_TRANSFER", "mergeOrders", selectResult = false) { scope ->
        repository.mergeOrders(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun transferOrderTable(
        orderId: String,
        request: OrderTransferTableInput
    ): Result<Order> = model.mutate("ORDER_TRANSFER", "transferOrderTable", selectResult = false) { scope ->
        repository.transferOrderTable(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun transferOrderBranch(
        orderId: String,
        request: OrderTransferBranchInput
    ): Result<Order> = model.mutate("ORDER_TRANSFER", "transferOrderBranch", selectResult = false) { scope ->
        repository.transferOrderBranch(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun getItems(
        orderId: String
    ): Result<List<OrderLineItem>> = model.query("ORDER_READ") { scope ->
        repository.getItems(restaurantId = scope.restaurantId, orderId = orderId)
    }

    suspend fun addItem(
        orderId: String,
        request: CreateOrderLineItemInput
    ): Result<OrderLineItem> = model.mutate("ORDER_UPDATE", "addItem", selectResult = false) { scope ->
        repository.addItem(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun getItem(
        orderId: String,
        lineItemId: String
    ): Result<OrderLineItem> = model.query("ORDER_READ") { scope ->
        repository.getItem(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId)
    }

    suspend fun updateItem(
        orderId: String,
        lineItemId: String,
        request: CreateOrderLineItemInput
    ): Result<OrderLineItem> = model.mutate("ORDER_UPDATE", "updateItem", selectResult = false) { scope ->
        repository.updateItem(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId, request = request)
    }

    suspend fun updateItemQuantity(
        orderId: String,
        lineItemId: String,
        request: OrderLineItemQuantityInput
    ): Result<OrderLineItem> = model.mutate("ORDER_UPDATE", "updateItemQuantity", selectResult = false) { scope ->
        repository.updateItemQuantity(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId, request = request)
    }

    suspend fun updateItemNotes(
        orderId: String,
        lineItemId: String,
        request: OrderLineItemNotesInput
    ): Result<OrderLineItem> = model.mutate("ORDER_UPDATE", "updateItemNotes", selectResult = false) { scope ->
        repository.updateItemNotes(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId, request = request)
    }

    suspend fun updateItemStatus(
        orderId: String,
        lineItemId: String,
        request: UpdateOrderLineItemStatusInput
    ): Result<OrderLineItem> = model.mutate("ORDER_UPDATE", "updateItemStatus", selectResult = false) { scope ->
        repository.updateItemStatus(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId, request = request)
    }

    suspend fun fireItem(
        orderId: String,
        lineItemId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<OrderLineItem> = model.mutate("ORDER_UPDATE", "fireItem", selectResult = false) { scope ->
        repository.fireItem(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId, request = request)
    }

    suspend fun readyItem(
        orderId: String,
        lineItemId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<OrderLineItem> = model.mutate("ORDER_UPDATE", "readyItem", selectResult = false) { scope ->
        repository.readyItem(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId, request = request)
    }

    suspend fun fulfillItem(
        orderId: String,
        lineItemId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<OrderLineItem> = model.mutate("ORDER_UPDATE", "fulfillItem", selectResult = false) { scope ->
        repository.fulfillItem(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId, request = request)
    }

    suspend fun voidItem(
        orderId: String,
        lineItemId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<OrderLineItem> = model.mutate("ORDER_VOID", "voidItem", selectResult = false) { scope ->
        repository.voidItem(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId, request = request)
    }

    suspend fun getItemOptions(
        orderId: String,
        lineItemId: String
    ): Result<List<OrderItemOption>> = model.query("ORDER_READ") { scope ->
        repository.getItemOptions(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId)
    }

    suspend fun addItemOption(
        orderId: String,
        lineItemId: String,
        request: CreateOrderItemOptionInput
    ): Result<OrderItemOption> = model.mutate("ORDER_UPDATE", "addItemOption", selectResult = false) { scope ->
        repository.addItemOption(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId, request = request)
    }

    suspend fun updateItemOption(
        orderId: String,
        lineItemId: String,
        optionId: String,
        request: CreateOrderItemOptionInput
    ): Result<OrderItemOption> = model.mutate("ORDER_UPDATE", "updateItemOption", selectResult = false) { scope ->
        repository.updateItemOption(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId, optionId = optionId, request = request)
    }

    suspend fun deleteItemOption(
        orderId: String,
        lineItemId: String,
        optionId: String
    ): Result<Unit> = model.mutate("ORDER_UPDATE", "deleteItemOption", selectResult = false) { scope ->
        repository.deleteItemOption(restaurantId = scope.restaurantId, orderId = orderId, lineItemId = lineItemId, optionId = optionId)
    }

    suspend fun getDiscounts(
        orderId: String
    ): Result<List<OrderDiscount>> = model.query("ORDER_READ") { scope ->
        repository.getDiscounts(restaurantId = scope.restaurantId, orderId = orderId)
    }

    suspend fun addDiscount(
        orderId: String,
        request: CreateOrderDiscountInput
    ): Result<OrderDiscount> = model.mutate("ORDER_DISCOUNT_APPLY", "addDiscount", selectResult = false) { scope ->
        repository.addDiscount(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun updateDiscount(
        orderId: String,
        discountId: String,
        request: CreateOrderDiscountInput
    ): Result<OrderDiscount> = model.mutate("ORDER_DISCOUNT_APPLY", "updateDiscount", selectResult = false) { scope ->
        repository.updateDiscount(restaurantId = scope.restaurantId, orderId = orderId, discountId = discountId, request = request)
    }

    suspend fun deleteDiscount(
        orderId: String,
        discountId: String
    ): Result<Unit> = model.mutate("ORDER_DISCOUNT_APPLY", "deleteDiscount", selectResult = false) { scope ->
        repository.deleteDiscount(restaurantId = scope.restaurantId, orderId = orderId, discountId = discountId)
    }

    suspend fun getEvents(
        orderId: String
    ): Result<List<OrderEvent>> = model.query("ORDER_READ") { scope ->
        repository.getEvents(restaurantId = scope.restaurantId, orderId = orderId)
    }

    suspend fun addNoteEvent(
        orderId: String,
        request: OrderActionInput = OrderActionInput()
    ): Result<OrderEvent> = model.mutate("ORDER_UPDATE", "addNoteEvent", selectResult = false) { scope ->
        repository.addNoteEvent(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun getTimeline(
        orderId: String
    ): Result<List<OrderEvent>> = model.query("ORDER_READ") { scope ->
        repository.getTimeline(restaurantId = scope.restaurantId, orderId = orderId)
    }

    suspend fun getAudit(
        orderId: String
    ): Result<OrderAudit> = model.query("ORDER_AUDIT") { scope ->
        repository.getAudit(restaurantId = scope.restaurantId, orderId = orderId)
    }

    suspend fun getTotals(
        orderId: String
    ): Result<OrderTotals> = model.query("ORDER_READ") { scope ->
        repository.getTotals(restaurantId = scope.restaurantId, orderId = orderId)
    }

    suspend fun splitPreview(
        orderId: String,
        request: OrderSplitInput
    ): Result<OrderSplitPreview> = model.query("ORDER_READ") { scope ->
        repository.splitPreview(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun splitOrder(
        orderId: String,
        request: OrderSplitInput
    ): Result<Order> = model.mutate("ORDER_TRANSFER", "splitOrder", selectResult = true) { scope ->
        repository.splitOrder(restaurantId = scope.restaurantId, orderId = orderId, request = request)
    }

    suspend fun getSummary(
        from: String? = null,
        to: String? = null
    ): Result<OrderMetrics> = model.query("ORDER_READ") { scope ->
        repository.getSummary(restaurantId = scope.restaurantId, branchId = scope.branchId, from = from, to = to)
    }

}
