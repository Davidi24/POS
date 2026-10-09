package com.saporini.mobile_desktop.pos.orders.data.dto

import com.saporini.mobile_desktop.pos.orders.domain.model.*
import kotlinx.serialization.Serializable

// Decimal values preserve the backend amount exactly; totals are calculated server-side.

@Serializable
data class CreateOrderDiscountRequestDto(
    val name: String,
    val discountType: OrderDiscountType,
    val discountValue: OrderDecimal,
    val reason: String? = null
)

@Serializable
data class CreateOrderItemOptionRequestDto(
    val optionItemId: String,
    val quantity: Int? = null,
    val notes: String? = null
)

@Serializable
data class CreateOrderLineItemRequestDto(
    val menuItemId: String,
    val variantId: String? = null,
    val quantity: Int,
    val notes: String? = null,
    val options: List<CreateOrderItemOptionRequestDto>? = null
)

@Serializable
data class CreateOrderRequestDto(
    val branchId: String? = null,
    val tableId: String? = null,
    val reservationId: String? = null,
    val customerId: String? = null,
    val orderType: OrderType? = null,
    val source: OrderSource? = null,
    val status: OrderStatus? = null,
    val guestCount: Int? = null,
    val notes: String? = null,
    val items: List<CreateOrderLineItemRequestDto>? = null,
    val discounts: List<CreateOrderDiscountRequestDto>? = null
)

@Serializable
data class OrderActionRequestDto(
    val reason: String? = null,
    val note: String? = null
)

@Serializable
data class OrderCustomerRequestDto(
    val customerId: String? = null
)

@Serializable
data class OrderLineItemNotesRequestDto(
    val notes: String? = null
)

@Serializable
data class OrderLineItemQuantityRequestDto(
    val quantity: Int
)

@Serializable
data class OrderMergeRequestDto(
    val sourceOrderId: String,
    val note: String? = null
)

@Serializable
data class OrderReservationRequestDto(
    val reservationId: String? = null
)

@Serializable
data class OrderSplitRequestDto(
    val lineItemIds: List<String>,
    val targetTableId: String? = null,
    val guestCount: Int? = null,
    val notes: String? = null
)

@Serializable
data class OrderTableRequestDto(
    val tableId: String? = null
)

@Serializable
data class OrderTransferBranchRequestDto(
    val branchId: String,
    val tableId: String? = null,
    val reservationId: String? = null,
    val note: String? = null
)

@Serializable
data class OrderTransferTableRequestDto(
    val tableId: String,
    val note: String? = null
)

@Serializable
data class UpdateOrderLineItemStatusRequestDto(
    val status: OrderLineItemStatus
)

@Serializable
data class UpdateOrderRequestDto(
    val branchId: String? = null,
    val tableId: String? = null,
    val reservationId: String? = null,
    val customerId: String? = null,
    val orderType: OrderType? = null,
    val source: OrderSource? = null,
    val status: OrderStatus? = null,
    val guestCount: Int? = null,
    val notes: String? = null,
    val items: List<CreateOrderLineItemRequestDto>? = null,
    val discounts: List<CreateOrderDiscountRequestDto>? = null
)
