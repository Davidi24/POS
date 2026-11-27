package com.saporini.mobile_desktop.pos.payment.data

import com.saporini.mobile_desktop.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart

interface PaymentRepository {
    suspend fun summary(restaurantId: String, orderId: String): OrderPaymentSummaryDto

    /** [requestKey] makes a retry of the same attempt safe: the server answers it once and replays it after. */
    suspend fun take(restaurantId: String, orderId: String, request: TakePaymentRequestDto, requestKey: String): TakePaymentResponseDto

    suspend fun refund(
        restaurantId: String, orderId: String, paymentId: String, request: RefundPaymentRequestDto, requestKey: String
    ): OrderPaymentSummaryDto

    suspend fun cancel(
        restaurantId: String, orderId: String, paymentId: String, request: VoidPaymentRequestDto, requestKey: String
    ): OrderPaymentSummaryDto

    suspend fun receipt(restaurantId: String, orderId: String): ReceiptDto

    suspend fun payment(restaurantId: String, paymentId: String): PaymentDto

    suspend fun branchPayments(restaurantId: String, branchId: String, filter: PaymentListFilter, page: Int, size: Int): PaymentPageDto

    /** The card terminal connected to the till or tablet [deviceId], or null (user, 2026-11-27: terminals work only through one). */
    suspend fun cardTerminal(restaurantId: String, deviceId: String): CardTerminalDto? = null
}

/** A till's card terminal: its name and how it's connected (BLUETOOTH or USB). */
@kotlinx.serialization.Serializable
data class CardTerminalDto(val id: String, val name: String = "", val linkType: String? = null, val linkAddress: String? = null)

class PaymentApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : PaymentRepository {

    private fun orderPath(restaurantId: String, orderId: String) =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/orders/${orderId.encodeURLPathPart()}"

    override suspend fun cardTerminal(restaurantId: String, deviceId: String): CardTerminalDto? {
        val answer = client.get("${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/card-terminal") {
            parameter("deviceId", deviceId)
        }
        return if (answer.status == io.ktor.http.HttpStatusCode.NoContent) null else answer.body()
    }

    override suspend fun summary(restaurantId: String, orderId: String): OrderPaymentSummaryDto =
        client.get("${orderPath(restaurantId, orderId)}/payments").body()

    override suspend fun take(
        restaurantId: String, orderId: String, request: TakePaymentRequestDto, requestKey: String
    ): TakePaymentResponseDto = client.post("${orderPath(restaurantId, orderId)}/payments") {
        headers.append("Idempotency-Key", requestKey)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    override suspend fun refund(
        restaurantId: String, orderId: String, paymentId: String, request: RefundPaymentRequestDto, requestKey: String
    ): OrderPaymentSummaryDto = client.post("${orderPath(restaurantId, orderId)}/payments/${paymentId.encodeURLPathPart()}/refund") {
        headers.append("Idempotency-Key", requestKey)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    override suspend fun cancel(
        restaurantId: String, orderId: String, paymentId: String, request: VoidPaymentRequestDto, requestKey: String
    ): OrderPaymentSummaryDto = client.post("${orderPath(restaurantId, orderId)}/payments/${paymentId.encodeURLPathPart()}/void") {
        headers.append("Idempotency-Key", requestKey)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    override suspend fun receipt(restaurantId: String, orderId: String): ReceiptDto =
        client.get("${orderPath(restaurantId, orderId)}/receipt").body()

    override suspend fun payment(restaurantId: String, paymentId: String): PaymentDto =
        client.get("${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/payments/${paymentId.encodeURLPathPart()}").body()

    override suspend fun branchPayments(
        restaurantId: String, branchId: String, filter: PaymentListFilter, page: Int, size: Int
    ): PaymentPageDto = client.get(
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/payments"
    ) {
        filter.from?.let { parameter("from", it) }
        filter.to?.let { parameter("to", it) }
        filter.method?.let { parameter("method", it) }
        filter.status?.let { parameter("status", it) }
        filter.staffId?.let { parameter("staffId", it) }
        filter.search?.takeIf { it.isNotBlank() }?.let { parameter("search", it.trim()) }
        parameter("page", page)
        parameter("size", size)
    }.body()
}
