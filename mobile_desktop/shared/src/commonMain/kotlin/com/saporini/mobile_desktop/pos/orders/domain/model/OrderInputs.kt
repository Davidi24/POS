package com.saporini.mobile_desktop.pos.orders.domain.model

/*
 * This file defines the data the app sends when asking the backend to create or
 * change an order: add items, change quantities, apply discounts or move tables.
 * Inputs describe what we want to do. The backend performs the action and
 * returns the updated order.
 *
 * Order number, currency and opening time are assigned by the backend.
 */

/** Null patch fields mean unchanged; use dedicated link operations to clear associations. */
data class CreateOrderDiscountInput(
    val name: String,
    val discountType: OrderDiscountType,
    val discountValue: OrderDecimal,
    val reason: String? = null
)

data class CreateOrderItemOptionInput(
    val optionItemId: String,
    val quantity: Int? = null,
    val notes: String? = null
)

data class CreateOrderLineItemInput(
    val menuItemId: String,
    val variantId: String? = null,
    val quantity: Int,
    val notes: String? = null,
    val options: List<CreateOrderItemOptionInput>? = null
)

data class CreateOrderInput(
    val branchId: String? = null,
    val tableId: String? = null,
    val reservationId: String? = null,
    val customerId: String? = null,
    val orderType: OrderType? = null,
    val source: OrderSource = OrderSource.POS,
    val status: OrderStatus = OrderStatus.DRAFT,
    val guestCount: Int? = null,
    val notes: String? = null,
    val items: List<CreateOrderLineItemInput>? = null,
    val discounts: List<CreateOrderDiscountInput>? = null
)

data class OrderActionInput(
    val reason: String? = null,
    val note: String? = null
)

data class OrderCustomerInput(
    val customerId: String? = null
)

data class OrderLineItemNotesInput(
    val notes: String? = null
)

data class OrderLineItemQuantityInput(
    val quantity: Int
)

data class OrderMergeInput(
    val sourceOrderId: String,
    val note: String? = null
)

data class OrderReservationInput(
    val reservationId: String? = null
)

data class OrderSplitInput(
    val lineItemIds: List<String>,
    val targetTableId: String? = null,
    val guestCount: Int? = null,
    val notes: String? = null
)

data class OrderTableInput(
    val tableId: String? = null
)

data class OrderTransferBranchInput(
    val branchId: String,
    val tableId: String? = null,
    val reservationId: String? = null,
    val note: String? = null
)

data class OrderTransferTableInput(
    val tableId: String,
    val note: String? = null
)

data class UpdateOrderLineItemStatusInput(
    val status: OrderLineItemStatus
)

data class UpdateOrderInput(
    val branchId: String? = null,
    val tableId: String? = null,
    val reservationId: String? = null,
    val customerId: String? = null,
    val orderType: OrderType? = null,
    val source: OrderSource? = null,
    val status: OrderStatus? = null,
    val guestCount: Int? = null,
    val notes: String? = null,
    val items: List<CreateOrderLineItemInput>? = null,
    val discounts: List<CreateOrderDiscountInput>? = null
)
