@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.saporini.mobile_desktop.pos.orders.data.api

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.data.dto.*
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderStatus
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.timeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart

/** Uses the shared authenticated client; errors propagate to the repository/screen model. */
class OrderApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) {
    private fun endpoint(path: String): String =
        "${baseUrlProvider().trimEnd('/')}$path"

    fun observeOrderChanges(restaurantId: String, branchId: String) = flow {
        require(restaurantId.isNotBlank() && branchId.isNotBlank())
        val url = endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/orders/events")
        while (currentCoroutineContext().isActive) {
            try {
                client.sse(urlString = url, request = {
                    timeout {
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        socketTimeoutMillis = 60_000L
                    }
                }, reconnectionTime = 3.seconds) {
                    incoming.collect { event ->
                        // Always resync on connection, including after missed events/restarts.
                        if (event.event == "connected" || event.event == "orders-changed") emit(Unit)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The authenticated client refreshes expired credentials during reconnect.
            }
            delay(3.seconds)
        }
    }

    suspend fun getOrders(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null,
        status: OrderStatus? = null,
        customerId: String? = null
    ): List<OrderSummaryResponseDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/orders")) {
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
            status?.let { parameter("status", it.name) }
            customerId?.let { parameter("customerId", it) }
        }.body()
    }

    suspend fun getOpenOrders(
        restaurantId: String,
        branchId: String
    ): List<OrderSummaryResponseDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/orders/open")).body()
    }

    suspend fun getOrderHistory(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null
    ): List<OrderSummaryResponseDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/orders/history")) {
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
        }.body()
    }

    suspend fun getCurrentTableOrder(
        restaurantId: String,
        branchId: String,
        tableId: String
    ): OrderResponseDto {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/tables/${tableId.encodeURLPathPart()}/orders/current")).body()
    }

    suspend fun createOrder(
        restaurantId: String,
        branchId: String,
        request: CreateOrderRequestDto
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/orders")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun createTableOrder(
        restaurantId: String,
        branchId: String,
        tableId: String,
        request: CreateOrderRequestDto
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/tables/${tableId.encodeURLPathPart()}/orders")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getKitchenBoard(
        restaurantId: String,
        branchId: String
    ): List<OrderSummaryResponseDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/orders/kitchen-board")).body()
    }

    suspend fun exportOrders(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null
    ): OrderExportResponseDto {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/orders/export")) {
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
        }.body()
    }

    suspend fun getRestaurantOrders(
        restaurantId: String,
        from: String? = null,
        to: String? = null
    ): List<OrderSummaryResponseDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders")) {
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
        }.body()
    }

    suspend fun getOrder(
        restaurantId: String,
        orderId: String
    ): OrderResponseDto {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}")).body()
    }

    suspend fun getOrderByNumber(
        restaurantId: String,
        orderNumber: String
    ): OrderResponseDto {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/number/${orderNumber.encodeURLPathPart()}")).body()
    }

    suspend fun getCustomerOrders(
        restaurantId: String,
        customerId: String
    ): List<OrderSummaryResponseDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/customers/${customerId.encodeURLPathPart()}/orders")).body()
    }

    suspend fun updateOrder(
        restaurantId: String,
        orderId: String,
        request: UpdateOrderRequestDto
    ): OrderResponseDto {
        return client.patch(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateOrderCustomer(
        restaurantId: String,
        orderId: String,
        request: OrderCustomerRequestDto
    ): OrderResponseDto {
        return client.patch(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/customer")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateOrderTable(
        restaurantId: String,
        orderId: String,
        request: OrderTableRequestDto
    ): OrderResponseDto {
        return client.patch(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/table")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateOrderReservation(
        restaurantId: String,
        orderId: String,
        request: OrderReservationRequestDto
    ): OrderResponseDto {
        return client.patch(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/reservation")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun validateOrder(
        restaurantId: String,
        request: CreateOrderRequestDto
    ): OrderValidationResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/validate")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun nextOrderNumber(
        restaurantId: String
    ): OrderNextNumberResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/next-number")).body()
    }

    suspend fun openOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/open")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun sendToKitchen(
        restaurantId: String,
        orderId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/send-to-kitchen")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun markOrderReady(
        restaurantId: String,
        orderId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/ready")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun fulfillOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/fulfill")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun closeOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/close")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun reopenOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/reopen")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun cancelOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/cancel")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun voidOrder(
        restaurantId: String,
        orderId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/void")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun mergeOrders(
        restaurantId: String,
        orderId: String,
        request: OrderMergeRequestDto
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/merge")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun transferOrderTable(
        restaurantId: String,
        orderId: String,
        request: OrderTransferTableRequestDto
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/transfer/table")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun transferOrderBranch(
        restaurantId: String,
        orderId: String,
        request: OrderTransferBranchRequestDto
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/transfer/branch")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getItems(
        restaurantId: String,
        orderId: String
    ): List<OrderLineItemResponseDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items")).body()
    }

    suspend fun addItem(
        restaurantId: String,
        orderId: String,
        request: CreateOrderLineItemRequestDto
    ): OrderLineItemResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String
    ): OrderLineItemResponseDto {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}")).body()
    }

    suspend fun updateItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: CreateOrderLineItemRequestDto
    ): OrderLineItemResponseDto {
        return client.put(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateItemQuantity(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderLineItemQuantityRequestDto
    ): OrderLineItemResponseDto {
        return client.patch(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}/quantity")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateItemNotes(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderLineItemNotesRequestDto
    ): OrderLineItemResponseDto {
        return client.patch(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}/notes")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateItemStatus(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: UpdateOrderLineItemStatusRequestDto
    ): OrderLineItemResponseDto {
        return client.patch(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}/status")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun fireItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderLineItemResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}/fire")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun readyItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderLineItemResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}/ready")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun fulfillItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderLineItemResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}/fulfill")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun voidItem(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderLineItemResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}/void")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getItemOptions(
        restaurantId: String,
        orderId: String,
        lineItemId: String
    ): List<OrderItemOptionResponseDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}/options")).body()
    }

    suspend fun addItemOption(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        request: CreateOrderItemOptionRequestDto
    ): OrderItemOptionResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}/options")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateItemOption(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        optionId: String,
        request: CreateOrderItemOptionRequestDto
    ): OrderItemOptionResponseDto {
        return client.put(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}/options/${optionId.encodeURLPathPart()}")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteItemOption(
        restaurantId: String,
        orderId: String,
        lineItemId: String,
        optionId: String
    ): Unit {
        client.delete(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/items/${lineItemId.encodeURLPathPart()}/options/${optionId.encodeURLPathPart()}"))
    }

    suspend fun getDiscounts(
        restaurantId: String,
        orderId: String
    ): List<OrderDiscountResponseDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/discounts")).body()
    }

    suspend fun addDiscount(
        restaurantId: String,
        orderId: String,
        request: CreateOrderDiscountRequestDto
    ): OrderDiscountResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/discounts")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateDiscount(
        restaurantId: String,
        orderId: String,
        discountId: String,
        request: CreateOrderDiscountRequestDto
    ): OrderDiscountResponseDto {
        return client.put(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/discounts/${discountId.encodeURLPathPart()}")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteDiscount(
        restaurantId: String,
        orderId: String,
        discountId: String
    ): Unit {
        client.delete(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/discounts/${discountId.encodeURLPathPart()}"))
    }

    suspend fun getEvents(
        restaurantId: String,
        orderId: String
    ): List<OrderEventResponseDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/events")).body()
    }

    suspend fun addNoteEvent(
        restaurantId: String,
        orderId: String,
        request: OrderActionRequestDto = OrderActionRequestDto()
    ): OrderEventResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/events/notes")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getTimeline(
        restaurantId: String,
        orderId: String
    ): List<OrderEventResponseDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/timeline")).body()
    }

    suspend fun getAudit(
        restaurantId: String,
        orderId: String
    ): OrderAuditResponseDto {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/audit")).body()
    }

    suspend fun getTotals(
        restaurantId: String,
        orderId: String
    ): OrderTotalsResponseDto {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/totals")).body()
    }

    suspend fun splitPreview(
        restaurantId: String,
        orderId: String,
        request: OrderSplitRequestDto
    ): OrderSplitPreviewResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/split-preview")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun splitOrder(
        restaurantId: String,
        orderId: String,
        request: OrderSplitRequestDto
    ): OrderResponseDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}/split")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getSummary(
        restaurantId: String,
        branchId: String? = null,
        from: String? = null,
        to: String? = null
    ): OrderMetricsResponseDto {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/orders/summary")) {
            branchId?.let { parameter("branchId", it) }
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
        }.body()
    }

}
