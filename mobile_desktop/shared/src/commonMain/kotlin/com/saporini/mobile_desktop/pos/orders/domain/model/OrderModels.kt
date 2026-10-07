package com.saporini.mobile_desktop.pos.orders.domain.model

data class OrderPage(
    val items: List<OrderSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean
)

data class OrderSummary(
    val id: String,
    val restaurantId: String,
    val branchId: String,
    val orderNumber: String,
    val currency: String,
    val orderType: OrderType,
    val source: OrderSource,
    val status: OrderStatus,
    val fulfillmentStatus: OrderFulfillmentStatus,
    val total: OrderDecimal,
    val itemCount: Int? = null,
    val openedAt: String,
    val tableId: String? = null,
    val tableNumber: String? = null,
    val tableName: String? = null,
    val customerId: String? = null,
    val customerName: String? = null,
    val guestCount: Int? = null,
    val notes: String? = null,
    val closedAt: String? = null,
    val createdBy: String? = null
)

data class OrderAudit(
    val orderId: String,
    val status: OrderStatus,
    val fulfillmentStatus: OrderFulfillmentStatus,
    val paymentStatus: OrderPaymentStatus,
    val createdAt: String,
    val createdBy: String? = null,
    val updatedAt: String,
    val updatedBy: String? = null,
    val lineItems: List<OrderLineItem>? = null,
    val discounts: List<OrderDiscount>? = null,
    val events: List<OrderEvent>? = null
)

data class OrderDiscount(
    val id: String,
    val name: String,
    val discountType: OrderDiscountType,
    val discountValue: OrderDecimal,
    val amountApplied: OrderDecimal,
    val reason: String? = null,
    val appliedBy: String? = null,
    val createdAt: String,
    val updatedAt: String
)

data class OrderEvent(
    val id: String,
    val eventType: OrderEventType,
    val note: String? = null,
    val createdBy: String? = null,
    val createdAt: String
)

data class OrderExport(
    val restaurantId: String,
    val branchId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val exportedAt: String,
    val orderCount: Int,
    val orders: List<Order>? = null
)

data class OrderItemOption(
    val id: String,
    val optionItemId: String,
    val optionNameSnapshot: String,
    val priceDeltaSnapshot: OrderDecimal,
    val quantity: Int,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String
)

data class OrderLineItem(
    val id: String,
    val menuItemId: String,
    val variantId: String? = null,
    val itemNameSnapshot: String,
    val variantNameSnapshot: String? = null,
    val skuSnapshot: String? = null,
    val quantity: Int,
    val unitPriceSnapshot: OrderDecimal,
    val priceDeltaTotal: OrderDecimal,
    val discountTotal: OrderDecimal,
    val taxTotal: OrderDecimal,
    val lineTotal: OrderDecimal,
    val status: OrderLineItemStatus,
    // False for counter items like a cola: served directly, never sent to the kitchen.
    val sendToKitchen: Boolean = true,
    val notes: String? = null,
    val options: List<OrderItemOption>? = null,
    val createdAt: String,
    val updatedAt: String
)

data class OrderNextNumber(
    val restaurantId: String,
    val branchId: String? = null,
    val orderNumber: String
)

data class Order(
    val id: String,
    val restaurantId: String,
    val branchId: String,
    val tableId: String? = null,
    val tableNumber: String? = null,
    val tableName: String? = null,
    val reservationId: String? = null,
    val reservationCode: String? = null,
    val customerId: String? = null,
    val customerCode: String? = null,
    val customerName: String? = null,
    val orderNumber: String,
    val currency: String,
    val taxInclusive: Boolean = false,
    val orderType: OrderType,
    val source: OrderSource,
    val status: OrderStatus,
    val fulfillmentStatus: OrderFulfillmentStatus,
    val paymentStatus: OrderPaymentStatus,
    val guestCount: Int? = null,
    val notes: String? = null,
    val subtotal: OrderDecimal,
    val discountTotal: OrderDecimal,
    val taxTotal: OrderDecimal,
    val serviceChargeTotal: OrderDecimal,
    val total: OrderDecimal,
    val openedAt: String,
    val closedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val lineItems: List<OrderLineItem>? = null,
    val discounts: List<OrderDiscount>? = null,
    val events: List<OrderEvent>? = null
)

data class OrderSplitPreview(
    val sourceOrderId: String,
    val currency: String,
    val lineItemIds: List<String>? = null,
    val lineCount: Int,
    val subtotal: OrderDecimal,
    val discountTotal: OrderDecimal,
    val taxTotal: OrderDecimal,
    val serviceChargeTotal: OrderDecimal,
    val total: OrderDecimal
)

data class OrderMetrics(
    val branchId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val totalOrders: Int,
    val openCount: Int,
    val closedCount: Int,
    val cancelledCount: Int,
    val voidedCount: Int,
    val paidCount: Int,
    val unpaidCount: Int,
    val totalRevenue: OrderDecimal,
    val averageTicket: OrderDecimal,
    val openTicketTotal: OrderDecimal
)

data class OrderTotals(
    val orderId: String,
    val currency: String,
    val lineCount: Int,
    val quantityTotal: Int,
    val subtotal: OrderDecimal,
    val discountTotal: OrderDecimal,
    val taxTotal: OrderDecimal,
    val serviceChargeTotal: OrderDecimal,
    val total: OrderDecimal
)

data class OrderValidation(
    val valid: Boolean,
    val suggestedOrderNumber: String? = null,
    val errors: List<String>? = null,
    val warnings: List<String>? = null,
    val totals: OrderTotals? = null
)
