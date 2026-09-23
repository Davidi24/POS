package com.saporini.mobile_desktop.pos.orders.data.dto

import com.saporini.mobile_desktop.pos.orders.domain.model.*
import kotlinx.serialization.Serializable

// Decimal values preserve the backend amount exactly; totals are calculated server-side.

@Serializable
data class OrderAuditResponseDto(
    val orderId: String,
    val status: OrderStatus,
    val fulfillmentStatus: OrderFulfillmentStatus,
    val paymentStatus: OrderPaymentStatus,
    val createdAt: String,
    val createdBy: String? = null,
    val updatedAt: String,
    val updatedBy: String? = null,
    val lineItems: List<OrderLineItemResponseDto>? = null,
    val discounts: List<OrderDiscountResponseDto>? = null,
    val events: List<OrderEventResponseDto>? = null
)

@Serializable
data class OrderDiscountResponseDto(
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

@Serializable
data class OrderEventResponseDto(
    val id: String,
    val eventType: OrderEventType,
    val note: String? = null,
    val createdBy: String? = null,
    val createdAt: String
)

@Serializable
data class OrderExportResponseDto(
    val restaurantId: String,
    val branchId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val exportedAt: String,
    val orderCount: Int,
    val orders: List<OrderResponseDto>? = null
)

@Serializable
data class OrderItemOptionResponseDto(
    val id: String,
    val optionItemId: String,
    val optionNameSnapshot: String,
    val priceDeltaSnapshot: OrderDecimal,
    val quantity: Int,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class OrderLineItemResponseDto(
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
    val notes: String? = null,
    val options: List<OrderItemOptionResponseDto>? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class OrderNextNumberResponseDto(
    val restaurantId: String,
    val branchId: String? = null,
    val orderNumber: String
)

@Serializable
data class OrderResponseDto(
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
    val lineItems: List<OrderLineItemResponseDto>? = null,
    val discounts: List<OrderDiscountResponseDto>? = null,
    val events: List<OrderEventResponseDto>? = null
)

@Serializable
data class OrderSplitPreviewResponseDto(
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

@Serializable
data class OrderMetricsResponseDto(
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

@Serializable
data class OrderTotalsResponseDto(
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

@Serializable
data class OrderValidationResponseDto(
    val valid: Boolean,
    val suggestedOrderNumber: String? = null,
    val errors: List<String>? = null,
    val warnings: List<String>? = null,
    val totals: OrderTotalsResponseDto? = null
)
