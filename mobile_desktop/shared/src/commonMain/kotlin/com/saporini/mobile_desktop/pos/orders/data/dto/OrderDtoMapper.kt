package com.saporini.mobile_desktop.pos.orders.data.dto

import com.saporini.mobile_desktop.pos.orders.domain.model.*

internal fun OrderSummaryResponseDto.toDomain(): OrderSummary = OrderSummary(
    id = id,
    restaurantId = restaurantId,
    branchId = branchId,
    orderNumber = orderNumber,
    currency = currency,
    orderType = orderType,
    source = source,
    status = status,
    fulfillmentStatus = fulfillmentStatus,
    total = total,
    itemCount = itemCount,
    openedAt = openedAt,
    tableId = tableId,
    tableNumber = tableNumber,
    tableName = tableName,
    customerId = customerId,
    customerName = customerName,
    guestCount = guestCount,
    notes = notes,
    closedAt = closedAt,
    createdBy = createdBy
)

internal fun CreateOrderDiscountInput.toDto(): CreateOrderDiscountRequestDto = CreateOrderDiscountRequestDto(
    name = name,
    discountType = discountType,
    discountValue = discountValue,
    reason = reason
)

internal fun CreateOrderItemOptionInput.toDto(): CreateOrderItemOptionRequestDto = CreateOrderItemOptionRequestDto(
    optionItemId = optionItemId,
    quantity = quantity,
    notes = notes
)

internal fun CreateOrderLineItemInput.toDto(): CreateOrderLineItemRequestDto = CreateOrderLineItemRequestDto(
    menuItemId = menuItemId,
    variantId = variantId,
    quantity = quantity,
    notes = notes,
    options = options?.map { it.toDto() }
)

internal fun CreateOrderInput.toDto(): CreateOrderRequestDto = CreateOrderRequestDto(
    branchId = branchId,
    tableId = tableId,
    reservationId = reservationId,
    customerId = customerId,
    orderType = orderType,
    source = source,
    status = status,
    guestCount = guestCount,
    notes = notes,
    items = items?.map { it.toDto() },
    discounts = discounts?.map { it.toDto() }
)

internal fun OrderActionInput.toDto(): OrderActionRequestDto = OrderActionRequestDto(
    reason = reason,
    note = note
)

internal fun OrderCustomerInput.toDto(): OrderCustomerRequestDto = OrderCustomerRequestDto(
    customerId = customerId
)

internal fun OrderLineItemNotesInput.toDto(): OrderLineItemNotesRequestDto = OrderLineItemNotesRequestDto(
    notes = notes
)

internal fun OrderLineItemQuantityInput.toDto(): OrderLineItemQuantityRequestDto = OrderLineItemQuantityRequestDto(
    quantity = quantity
)

internal fun OrderMergeInput.toDto(): OrderMergeRequestDto = OrderMergeRequestDto(
    sourceOrderId = sourceOrderId,
    note = note
)

internal fun OrderReservationInput.toDto(): OrderReservationRequestDto = OrderReservationRequestDto(
    reservationId = reservationId
)

internal fun OrderSplitInput.toDto(): OrderSplitRequestDto = OrderSplitRequestDto(
    lineItemIds = lineItemIds,
    targetTableId = targetTableId,
    guestCount = guestCount,
    notes = notes
)

internal fun OrderTableInput.toDto(): OrderTableRequestDto = OrderTableRequestDto(
    tableId = tableId
)

internal fun OrderTransferBranchInput.toDto(): OrderTransferBranchRequestDto = OrderTransferBranchRequestDto(
    branchId = branchId,
    tableId = tableId,
    reservationId = reservationId,
    note = note
)

internal fun OrderTransferTableInput.toDto(): OrderTransferTableRequestDto = OrderTransferTableRequestDto(
    tableId = tableId,
    note = note
)

internal fun UpdateOrderLineItemStatusInput.toDto(): UpdateOrderLineItemStatusRequestDto = UpdateOrderLineItemStatusRequestDto(
    status = status
)

internal fun UpdateOrderInput.toDto(): UpdateOrderRequestDto = UpdateOrderRequestDto(
    branchId = branchId,
    tableId = tableId,
    reservationId = reservationId,
    customerId = customerId,
    orderType = orderType,
    source = source,
    status = status,
    guestCount = guestCount,
    notes = notes,
    items = items?.map { it.toDto() },
    discounts = discounts?.map { it.toDto() }
)

internal fun OrderAuditResponseDto.toDomain(): OrderAudit = OrderAudit(
    orderId = orderId,
    status = status,
    fulfillmentStatus = fulfillmentStatus,
    paymentStatus = paymentStatus,
    createdAt = createdAt,
    createdBy = createdBy,
    updatedAt = updatedAt,
    updatedBy = updatedBy,
    lineItems = lineItems?.map { it.toDomain() },
    discounts = discounts?.map { it.toDomain() },
    events = events?.map { it.toDomain() }
)

internal fun OrderDiscountResponseDto.toDomain(): OrderDiscount = OrderDiscount(
    id = id,
    name = name,
    discountType = discountType,
    discountValue = discountValue,
    amountApplied = amountApplied,
    reason = reason,
    appliedBy = appliedBy,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun OrderEventResponseDto.toDomain(): OrderEvent = OrderEvent(
    id = id,
    eventType = eventType,
    note = note,
    createdBy = createdBy,
    createdAt = createdAt
)

internal fun OrderExportResponseDto.toDomain(): OrderExport = OrderExport(
    restaurantId = restaurantId,
    branchId = branchId,
    from = from,
    to = to,
    exportedAt = exportedAt,
    orderCount = orderCount,
    orders = orders?.map { it.toDomain() }
)

internal fun OrderItemOptionResponseDto.toDomain(): OrderItemOption = OrderItemOption(
    id = id,
    optionItemId = optionItemId,
    optionNameSnapshot = optionNameSnapshot,
    priceDeltaSnapshot = priceDeltaSnapshot,
    quantity = quantity,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun OrderLineItemResponseDto.toDomain(): OrderLineItem = OrderLineItem(
    id = id,
    menuItemId = menuItemId,
    variantId = variantId,
    itemNameSnapshot = itemNameSnapshot,
    variantNameSnapshot = variantNameSnapshot,
    skuSnapshot = skuSnapshot,
    quantity = quantity,
    unitPriceSnapshot = unitPriceSnapshot,
    priceDeltaTotal = priceDeltaTotal,
    discountTotal = discountTotal,
    taxTotal = taxTotal,
    lineTotal = lineTotal,
    status = status,
    notes = notes,
    options = options?.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun OrderNextNumberResponseDto.toDomain(): OrderNextNumber = OrderNextNumber(
    restaurantId = restaurantId,
    branchId = branchId,
    orderNumber = orderNumber
)

internal fun OrderResponseDto.toDomain(): Order = Order(
    id = id,
    restaurantId = restaurantId,
    branchId = branchId,
    tableId = tableId,
    tableNumber = tableNumber,
    tableName = tableName,
    reservationId = reservationId,
    reservationCode = reservationCode,
    customerId = customerId,
    customerCode = customerCode,
    customerName = customerName,
    orderNumber = orderNumber,
    currency = currency,
    taxInclusive = taxInclusive,
    orderType = orderType,
    source = source,
    status = status,
    fulfillmentStatus = fulfillmentStatus,
    paymentStatus = paymentStatus,
    guestCount = guestCount,
    notes = notes,
    subtotal = subtotal,
    discountTotal = discountTotal,
    taxTotal = taxTotal,
    serviceChargeTotal = serviceChargeTotal,
    total = total,
    openedAt = openedAt,
    closedAt = closedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    updatedBy = updatedBy,
    lineItems = lineItems?.map { it.toDomain() },
    discounts = discounts?.map { it.toDomain() },
    events = events?.map { it.toDomain() }
)

internal fun OrderSplitPreviewResponseDto.toDomain(): OrderSplitPreview = OrderSplitPreview(
    sourceOrderId = sourceOrderId,
    currency = currency,
    lineItemIds = lineItemIds,
    lineCount = lineCount,
    subtotal = subtotal,
    discountTotal = discountTotal,
    taxTotal = taxTotal,
    serviceChargeTotal = serviceChargeTotal,
    total = total
)

internal fun OrderMetricsResponseDto.toDomain(): OrderMetrics = OrderMetrics(
    branchId = branchId,
    from = from,
    to = to,
    totalOrders = totalOrders,
    openCount = openCount,
    closedCount = closedCount,
    cancelledCount = cancelledCount,
    voidedCount = voidedCount,
    paidCount = paidCount,
    unpaidCount = unpaidCount,
    totalRevenue = totalRevenue,
    averageTicket = averageTicket,
    openTicketTotal = openTicketTotal
)

internal fun OrderTotalsResponseDto.toDomain(): OrderTotals = OrderTotals(
    orderId = orderId,
    currency = currency,
    lineCount = lineCount,
    quantityTotal = quantityTotal,
    subtotal = subtotal,
    discountTotal = discountTotal,
    taxTotal = taxTotal,
    serviceChargeTotal = serviceChargeTotal,
    total = total
)

internal fun OrderValidationResponseDto.toDomain(): OrderValidation = OrderValidation(
    valid = valid,
    suggestedOrderNumber = suggestedOrderNumber,
    errors = errors,
    warnings = warnings,
    totals = totals?.toDomain()
)
