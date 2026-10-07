package com.saporini.mobile_desktop.payment

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.payment.data.PaymentApi
import com.saporini.mobile_desktop.pos.payment.data.PaymentListFilter
import com.saporini.mobile_desktop.pos.payment.data.RefundPaymentRequestDto
import com.saporini.mobile_desktop.pos.payment.data.TakePaymentRequestDto
import com.saporini.mobile_desktop.pos.payment.data.VoidPaymentRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentApiTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val summaryJson = """
        {"orderId":"o","orderNumber":"ORD-1","currency":"EUR","orderStatus":"OPEN","paymentStatus":"PARTIALLY_PAID",
         "orderTotal":9007199254740993.25,"balanceDue":9007199254740983.25,"cashBalanceDue":9007199254740983.25,
         "tipsEnabled":true,"tipSuggestions":[5,10,15],"maxTipPercent":50,"splitBillsAllowed":true,"payments":[]}
    """.trimIndent()
    private val paymentJson = """{"id":"p","referenceNumber":"PAY-1","method":"CARD","status":"CAPTURED","amount":10.00,"currency":"EUR"}"""

    private fun client(handler: suspend (io.ktor.client.request.HttpRequestData) -> String) = HttpClient(MockEngine { request ->
        respond(handler(request), headers = jsonHeaders)
    }) { install(ContentNegotiation) { json(json) } }

    @Test
    fun takingAPaymentSendsExactDecimalsAndTheRequestKey() = runTest {
        val client = client { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/restaurants/r/orders/o/payments", request.url.encodedPath)
            assertEquals("key-1234567890abcdef", request.headers["Idempotency-Key"])
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"amount\":10.00"), body)
            assertTrue(body.contains("\"tipAmount\":1.50"), body)
            assertTrue(body.contains("\"method\":\"CARD\""), body)
            """{"payment":$paymentJson,"summary":$summaryJson,"orderClosed":false}"""
        }
        try {
            val response = PaymentApi(client) { "http://localhost/" }.take("r", "o",
                TakePaymentRequestDto("CARD", OrderDecimal("10.00"), tipAmount = OrderDecimal("1.50")), "key-1234567890abcdef")
            assertEquals("PAY-1", response.payment.referenceNumber)
            // Large amounts survive without floating point.
            assertEquals("9007199254740983.25", response.summary.balanceDue.value)
        } finally {
            client.close()
        }
    }

    @Test
    fun refundsCancelsAndReadsUseTheirPaths() = runTest {
        val seen = mutableListOf<String>()
        val client = client { request ->
            seen += "${request.method.value} ${request.url.encodedPath}"
            when {
                request.url.encodedPath.endsWith("/refund") -> {
                    assertTrue((request.body as TextContent).text.contains("\"reason\":\"Cold\""))
                    summaryJson
                }
                request.url.encodedPath.endsWith("/void") -> summaryJson
                request.url.encodedPath.endsWith("/receipt") -> """{"currency":"EUR","orderNumber":"ORD-1","lines":[]}"""
                request.url.encodedPath.contains("/branches/") -> {
                    assertEquals("CASH", request.url.parameters["method"])
                    assertEquals("pizza", request.url.parameters["search"])
                    assertNull(request.url.parameters["staffId"])
                    assertEquals("2", request.url.parameters["page"])
                    """{"items":[$paymentJson],"page":2,"size":20,"totalElements":41,"totalPages":3,"hasNext":false}"""
                }
                request.url.encodedPath.startsWith("/restaurants/r/payments/") -> paymentJson
                else -> summaryJson
            }
        }
        try {
            val api = PaymentApi(client) { "http://localhost" }
            api.summary("r", "o")
            api.refund("r", "o", "p", RefundPaymentRequestDto(OrderDecimal("5.00"), "Cold"), "k-0123456789abcdef")
            api.cancel("r", "o", "p", VoidPaymentRequestDto("Wrong"), "k-0123456789abcdef")
            api.receipt("r", "o")
            api.payment("r", "p")
            val page = api.branchPayments("r", "b", PaymentListFilter(method = "CASH", search = "  pizza "), 2, 20)
            assertEquals(41, page.totalElements)
            assertEquals(listOf(
                "GET /restaurants/r/orders/o/payments",
                "POST /restaurants/r/orders/o/payments/p/refund",
                "POST /restaurants/r/orders/o/payments/p/void",
                "GET /restaurants/r/orders/o/receipt",
                "GET /restaurants/r/payments/p",
                "GET /restaurants/r/branches/b/payments"
            ), seen)
        } finally {
            client.close()
        }
    }

    @Test
    fun idsAreEscapedInPaths() = runTest {
        val client = client { request ->
            assertEquals("/restaurants/r%2Fx/orders/o%20y/payments", request.url.encodedPath)
            summaryJson
        }
        try {
            PaymentApi(client) { "http://localhost" }.summary("r/x", "o y")
        } finally {
            client.close()
        }
    }
}
