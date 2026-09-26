package com.saporini.mobile_desktop.orders

import com.saporini.mobile_desktop.pos.orders.data.api.OrderApi
import com.saporini.mobile_desktop.pos.orders.data.dto.*
import com.saporini.mobile_desktop.pos.orders.data.repository.DefaultOrderRepository
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.ui.awaitingKitchen
import com.saporini.mobile_desktop.pos.orders.ui.showsFulfillmentProgress
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class OrderDataContractTest {

    @Test fun counterItemsNeverCountAsWaitingForTheKitchen() {
        fun line(extra: String) = orderJson.decodeFromString<OrderLineItemResponseDto>("""
            {"id":"line-1","menuItemId":"item-1","itemNameSnapshot":"Cola","quantity":1,"unitPriceSnapshot":2.50,
             "priceDeltaTotal":0,"discountTotal":0,"taxTotal":0,"lineTotal":2.50,"status":"PENDING",
             "createdAt":"2026-09-26T10:00:00Z","updatedAt":"2026-09-26T10:00:00Z"$extra}
        """).toDomain()
        // Servers from before counter items existed don't send the flag: every item still goes to the kitchen.
        val legacy = line("")
        assertTrue(legacy.sendToKitchen)
        assertTrue(legacy.awaitingKitchen)
        val cola = line(""","sendToKitchen":false""")
        assertFalse(cola.sendToKitchen)
        assertFalse(cola.awaitingKitchen)
    }

    @Test fun serviceProgressDecodesForEveryOrderTypeAndLifecycle() {
        for (type in OrderType.entries) {
            for (lifecycle in OrderStatus.entries) {
                for (progress in OrderFulfillmentStatus.entries) {
                    val fixture = orderFixture()
                        .replace("\"orderType\":\"DINE_IN\"", "\"orderType\":\"${type.name}\"")
                        .replace("\"status\":\"OPEN\"", "\"status\":\"${lifecycle.name}\"")
                        .replace("\"fulfillmentStatus\":\"PENDING\"", "\"fulfillmentStatus\":\"${progress.name}\"")
                    val order = orderJson.decodeFromString<OrderResponseDto>(fixture).toDomain()
                    assertEquals(type, order.orderType)
                    assertEquals(lifecycle, order.status)
                    assertEquals(progress, order.fulfillmentStatus)
                }
            }
        }
    }

    @Test fun legacyRetryResponsesDecodeIntoTheSimplifiedProgress() {
        for ((legacy, current) in listOf("DELIVERED" to OrderFulfillmentStatus.FULFILLED, "CANCELLED" to OrderFulfillmentStatus.PENDING)) {
            val fixture = orderFixture().replace("\"fulfillmentStatus\":\"PENDING\"", "\"fulfillmentStatus\":\"$legacy\"")
            val dto = orderJson.decodeFromString<OrderResponseDto>(fixture)
            assertEquals(current, dto.fulfillmentStatus)
            assertEquals(current.name, orderJson.parseToJsonElement(orderJson.encodeToString(dto)).jsonObject["fulfillmentStatus"]!!.jsonPrimitive.content)
        }
        assertEquals(listOf("PENDING", "IN_PREPARATION", "READY", "PARTIALLY_FULFILLED", "FULFILLED"), OrderFulfillmentStatus.entries.map { it.name })
    }

    @Test fun cancelledAndVoidedOrdersHaveNoVisibleServiceProgress() {
        assertFalse(OrderStatus.CANCELLED.showsFulfillmentProgress)
        assertFalse(OrderStatus.VOIDED.showsFulfillmentProgress)
        assertTrue(OrderStatus.OPEN.showsFulfillmentProgress)
        assertTrue(OrderStatus.DRAFT.showsFulfillmentProgress)
        assertTrue(OrderStatus.CLOSED.showsFulfillmentProgress)
    }
    @Test fun decimalAmountsAndNullableSummaryDetailsSurviveMapping() {
        val dto = orderJson.decodeFromString<OrderResponseDto>(orderFixture())
        assertEquals("9007199254740993.25", dto.toDomain().total.value)
        assertNull(dto.lineItems)
        assertNull(dto.tableId)
        assertEquals("9007199254740993.25", orderJson.encodeToString(dto.total))
        assertFailsWith<IllegalArgumentException> { OrderDecimal("NaN") }
        assertFailsWith<IllegalArgumentException> { OrderDecimal("1,23") }
        assertEquals("0.10", orderJson.decodeFromString<OrderDecimal>("0.10").value)
    }

    @Test fun patchDoesNotReplaceItemsAndLinkRequestCanClearCustomer() {
        val patch = orderJson.parseToJsonElement(orderJson.encodeToString(UpdateOrderRequestDto(notes = "No salt"))).jsonObject
        assertFalse("items" in patch)
        assertFalse("discounts" in patch)
        val link = orderJson.decodeFromString<OrderCustomerRequestDto>(orderJson.encodeToString(OrderCustomerRequestDto()))
        assertNull(link.customerId)
    }

    @Test fun listAndWorkflowMatchBackendContract() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = client { request ->
            requests += request
            if (request.method == HttpMethod.Get) "[${orderFixture()}]" else orderFixture()
        }
        try {
            val api = OrderApi(client) { "http://localhost:8080/" }
            api.getOrders("restaurant-1", "branch-1", status = OrderStatus.OPEN)
            api.sendToKitchen("restaurant-1", "order-1", OrderActionRequestDto(note = "Start now"))
            assertEquals("/restaurants/restaurant-1/branches/branch-1/orders", requests[0].url.encodedPath)
            assertEquals("OPEN", requests[0].url.parameters["status"])
            assertEquals(HttpMethod.Post, requests[1].method)
            assertEquals("/restaurants/restaurant-1/orders/order-1/send-to-kitchen", requests[1].url.encodedPath)
            assertEquals("Start now", orderJson.parseToJsonElement((requests[1].body as TextContent).text).jsonObject["note"]?.jsonPrimitive?.content)
        } finally { client.close() }
    }

    @Test fun createIncludesPosSourceAndRejectsMismatchedTableBeforeSending() = runTest {
        var calls = 0
        var body = ""
        val client = client { request -> calls++; body = (request.body as TextContent).text; orderFixture() }
        try {
            val repository = DefaultOrderRepository(OrderApi(client) { "http://localhost" })
            repository.createTableOrder("restaurant-1", "branch-1", "table-1", CreateOrderInput(orderType = OrderType.DINE_IN))
            assertEquals("POS", orderJson.parseToJsonElement(body).jsonObject["source"]?.jsonPrimitive?.content)
            assertEquals("DRAFT", orderJson.parseToJsonElement(body).jsonObject["status"]?.jsonPrimitive?.content)
            assertFailsWith<IllegalArgumentException> {
                repository.createTableOrder("restaurant-1", "branch-1", "table-1", CreateOrderInput(tableId = "other-table"))
            }
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test fun unsafeInputsAreRejectedWithoutNetworkCalls() = runTest {
        var calls = 0
        val client = client { calls++; orderFixture() }
        try {
            val repository = DefaultOrderRepository(OrderApi(client) { "http://localhost" })
            assertFailsWith<IllegalArgumentException> {
                repository.addItem("restaurant-1", "order-1", CreateOrderLineItemInput(menuItemId = "item-1", quantity = 0))
            }
            assertFailsWith<IllegalArgumentException> {
                repository.splitOrder("restaurant-1", "order-1", OrderSplitInput(listOf("item-1", "item-1")))
            }
            assertFailsWith<IllegalArgumentException> {
                repository.getOrderHistory("restaurant-1", "branch-1", from = "2026-01-01T00:00:00Z")
            }
            assertEquals(0, calls)
        } finally { client.close() }
    }

    @Test fun failedWriteIsNotAutomaticallyRetried() = runTest {
        var calls = 0
        val engine = MockEngine { calls++; respond("{}", HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine) { expectSuccess = true; install(ContentNegotiation) { json(orderJson) } }
        try {
            assertFailsWith<ClientRequestException> { OrderApi(client) { "http://localhost" }.sendToKitchen("restaurant-1", "order-1") }
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test fun deleteHandlesNoContent() = runTest {
        val engine = MockEngine {
            assertEquals(HttpMethod.Delete, it.method)
            respond("", HttpStatusCode.NoContent)
        }
        val client = HttpClient(engine)
        try { OrderApi(client) { "http://localhost" }.deleteItemOption("restaurant-1", "order-1", "line-1", "option-1") }
        finally { client.close() }
    }

    private fun client(response: (HttpRequestData) -> String): HttpClient = HttpClient(MockEngine {
        respond(response(it), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }) { expectSuccess = true; install(ContentNegotiation) { json(orderJson) } }
}
