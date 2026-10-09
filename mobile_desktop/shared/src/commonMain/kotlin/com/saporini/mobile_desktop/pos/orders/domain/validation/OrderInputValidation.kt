package com.saporini.mobile_desktop.pos.orders.domain.validation

import com.saporini.mobile_desktop.pos.orders.domain.model.*

/** Early feedback only; authorization, menu rules and totals remain backend responsibilities. */
internal fun Any.validate() {
    when (this) {
        is CreateOrderInput -> {
            require(source == OrderSource.POS) { "POS orders must use source POS" }
            require(status == OrderStatus.DRAFT || status == OrderStatus.OPEN) { "New orders must be draft or open" }
            validateHeader(guestCount)
            items?.forEach { it.validate() }
            discounts?.forEach { it.validate() }
        }
        is UpdateOrderInput -> {
            validateHeader(guestCount)
            items?.forEach { it.validate() }
            discounts?.forEach { it.validate() }
        }
        is CreateOrderLineItemInput -> {
            require(menuItemId.isNotBlank()) { "Choose a menu item" }
            require(quantity > 0) { "Quantity must be greater than zero" }
            options?.let { selected ->
                require(selected.map { it.optionItemId }.distinct().size == selected.size) { "Duplicate item options" }
                selected.forEach { it.validate() }
            }
        }
        is CreateOrderItemOptionInput -> {
            require(optionItemId.isNotBlank()) { "Choose an option" }
            require(quantity == null || quantity > 0) { "Option quantity must be greater than zero" }
        }
        is CreateOrderDiscountInput -> {
            require(name.isNotBlank()) { "Discount name is required" }
            require(!discountValue.isNegative) { "Discount cannot be negative" }
        }
        is OrderLineItemQuantityInput -> require(quantity > 0) { "Quantity must be greater than zero" }
        is OrderMergeInput -> require(sourceOrderId.isNotBlank()) { "Choose the source order" }
        is OrderTransferTableInput -> require(tableId.isNotBlank()) { "Choose the target table" }
        is OrderTransferBranchInput -> require(branchId.isNotBlank()) { "Choose the target branch" }
        is OrderSplitInput -> {
            require(lineItemIds.isNotEmpty() && lineItemIds.all { it.isNotBlank() }) { "Choose items to split" }
            require(lineItemIds.distinct().size == lineItemIds.size) { "Split items must be unique" }
            validateHeader(guestCount)
        }
    }
}

private fun validateHeader(guestCount: Int?) {
    require(guestCount == null || guestCount > 0) { "Guest count must be greater than zero" }
}
