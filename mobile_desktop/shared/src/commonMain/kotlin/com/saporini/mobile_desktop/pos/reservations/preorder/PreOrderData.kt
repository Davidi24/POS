package com.saporini.mobile_desktop.pos.reservations.preorder

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

// Food ordered ahead for a booking. The server prices it; it goes to the kitchen a set time before the guests come.

val PRE_ORDER_STATUSES = listOf("SCHEDULED", "SENT", "CANCELLED", "FORFEITED")

@Serializable
data class PreOrderOptionDto(val optionItemId: String, val name: String? = null, val priceDelta: OrderDecimal? = null, val quantity: Int = 1, val notes: String? = null)

@Serializable
data class PreOrderItemDto(
    val id: String? = null,
    val menuItemId: String,
    val variantId: String? = null,
    val itemName: String? = null,
    val variantName: String? = null,
    val quantity: Int = 1,
    val unitPrice: OrderDecimal? = null,
    val lineTotal: OrderDecimal? = null,
    val notes: String? = null,
    val options: List<PreOrderOptionDto> = emptyList()
)

@Serializable
data class PreOrderDto(
    val id: String,
    val branchId: String? = null,
    val reservationId: String,
    val reservationCode: String? = null,
    val reservationStart: String? = null,
    val partySize: Int? = null,
    val contactName: String? = null,
    val status: String,
    val paymentStatus: String? = null,
    val source: String? = null,
    val currency: String = "EUR",
    val subtotal: OrderDecimal? = null,
    val taxTotal: OrderDecimal? = null,
    val serviceChargeTotal: OrderDecimal? = null,
    val total: OrderDecimal? = null,
    val paidAmount: OrderDecimal? = null,
    val leadMinutes: Int? = null,
    val sendAt: String? = null,
    val orderId: String? = null,
    val orderNumber: String? = null,
    val notes: String? = null,
    val cancellationReason: String? = null,
    val sentAt: String? = null,
    val cancelledAt: String? = null,
    val updatedAt: String? = null,
    val items: List<PreOrderItemDto> = emptyList()
) {
    /** Only a scheduled pre-order can still change, be cancelled or be sent early. */
    val open: Boolean get() = status == "SCHEDULED"
}

@Serializable
data class PreOrderOptionRequestDto(val optionItemId: String, val quantity: Int, val notes: String? = null)

@Serializable
data class PreOrderItemRequestDto(
    val menuItemId: String,
    val variantId: String? = null,
    val quantity: Int,
    val notes: String? = null,
    val options: List<PreOrderOptionRequestDto> = emptyList()
)

@Serializable
data class PreOrderRequestDto(val items: List<PreOrderItemRequestDto>, val notes: String? = null)

@Serializable
data class PreOrderActionRequestDto(val reason: String? = null)

interface PreOrderRepository {
    /** The booking's pre-order, or null when it has none. */
    suspend fun forBooking(restaurantId: String, reservationId: String): PreOrderDto?
    suspend fun save(restaurantId: String, reservationId: String, request: PreOrderRequestDto): PreOrderDto
    suspend fun cancel(restaurantId: String, reservationId: String, reason: String?): PreOrderDto
    suspend fun sendNow(restaurantId: String, reservationId: String): PreOrderDto
    suspend fun forBranch(restaurantId: String, branchId: String, from: String, to: String, status: String?): List<PreOrderDto>
}

class PreOrderApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : PreOrderRepository {

    private fun restaurant(restaurantId: String) = "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}"
    private fun booking(restaurantId: String, reservationId: String) =
        "${restaurant(restaurantId)}/reservations/${reservationId.encodeURLPathPart()}/pre-order"

    override suspend fun forBooking(restaurantId: String, reservationId: String): PreOrderDto? = try {
        client.get(booking(restaurantId, reservationId)).body()
    } catch (e: com.saporini.mobile_desktop.core.network.ApiException) {
        // No pre-order yet is a normal answer, not an error (a missing booking still is).
        if (e.status == 404 && e.message.contains("no pre-order", ignoreCase = true)) null else throw e
    }

    override suspend fun save(restaurantId: String, reservationId: String, request: PreOrderRequestDto): PreOrderDto =
        client.put(booking(restaurantId, reservationId)) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun cancel(restaurantId: String, reservationId: String, reason: String?): PreOrderDto =
        client.post("${booking(restaurantId, reservationId)}/cancel") { contentType(ContentType.Application.Json); setBody(PreOrderActionRequestDto(reason)) }.body()

    override suspend fun sendNow(restaurantId: String, reservationId: String): PreOrderDto =
        client.post("${booking(restaurantId, reservationId)}/send").body()

    override suspend fun forBranch(restaurantId: String, branchId: String, from: String, to: String, status: String?): List<PreOrderDto> =
        client.get("${restaurant(restaurantId)}/branches/${branchId.encodeURLPathPart()}/pre-orders") {
            parameter("from", from)
            parameter("to", to)
            status?.let { parameter("status", it) }
        }.body()
}

// The server's limits.
const val MAX_PRE_ORDER_DISHES = 50
const val MAX_DISH_QUANTITY = 50
const val MAX_DISH_OPTIONS = 30
const val MAX_OPTION_QUANTITY = 20
const val MAX_DISH_NOTES = 200
const val MAX_PRE_ORDER_NOTES = 500
const val MAX_PRE_ORDER_LIST_DAYS = 62

/** A dish in the pre-order being written; [name] is only for showing it. */
data class PreOrderLine(
    val menuItemId: String,
    val name: String,
    val variantId: String? = null,
    val quantity: Int = 1,
    val notes: String = "",
    val options: List<PreOrderOptionRequestDto> = emptyList()
)

fun linesOf(preOrder: PreOrderDto): List<PreOrderLine> = preOrder.items.map { item ->
    PreOrderLine(item.menuItemId, listOfNotNull(item.itemName, item.variantName).joinToString(" · ").ifEmpty { "Dish" }, item.variantId,
        item.quantity, item.notes.orEmpty(), item.options.map { PreOrderOptionRequestDto(it.optionItemId, it.quantity, it.notes) })
}

fun preOrderProblem(lines: List<PreOrderLine>, notes: String): String? = when {
    lines.isEmpty() -> "Add at least one dish"
    lines.size > MAX_PRE_ORDER_DISHES -> "At most $MAX_PRE_ORDER_DISHES dishes"
    lines.any { it.quantity !in 1..MAX_DISH_QUANTITY } -> "Each dish can be ordered 1 to $MAX_DISH_QUANTITY times"
    lines.any { it.notes.trim().length > MAX_DISH_NOTES } -> "Dish notes can be at most $MAX_DISH_NOTES characters"
    lines.any { it.options.size > MAX_DISH_OPTIONS } -> "At most $MAX_DISH_OPTIONS choices per dish"
    lines.any { line -> line.options.any { it.quantity !in 1..MAX_OPTION_QUANTITY } } -> "Each choice can be taken 1 to $MAX_OPTION_QUANTITY times"
    notes.trim().length > MAX_PRE_ORDER_NOTES -> "Notes can be at most $MAX_PRE_ORDER_NOTES characters"
    else -> null
}

fun preOrderRequest(lines: List<PreOrderLine>, notes: String) = PreOrderRequestDto(
    items = lines.map { PreOrderItemRequestDto(it.menuItemId, it.variantId, it.quantity, it.notes.trim().ifEmpty { null }, it.options) },
    notes = notes.trim().ifEmpty { null }
)
