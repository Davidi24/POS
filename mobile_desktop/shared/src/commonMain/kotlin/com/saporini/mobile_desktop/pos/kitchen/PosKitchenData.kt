package com.saporini.mobile_desktop.pos.kitchen

import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.kds.model.KdsTicketItem
import com.saporini.mobile_desktop.kds.model.asInstant
import kotlin.time.Instant

// The waiter's view of the kitchen: one card per order (a table's food from every station), in the lane of what the
// waiter has to do next. Ready food comes first: it's what they go and pick up.
internal enum class KitchenLane { READY, COOKING, WAITING }

internal enum class DishState { WAITING, COOKING, READY }

internal data class KitchenDish(
    val ticketId: String,
    val quantity: Int,
    val name: String,
    val detail: String?,
    val state: DishState,
    val station: String
)

internal data class KitchenOrderCard(
    val orderId: String,
    val orderNumber: String,
    val table: String?,
    val guests: Int?,
    val waiterId: String?,
    val lane: KitchenLane,
    val rush: Boolean,
    val sentAt: Instant?,
    // When the first of its food became ready (for "ready for 4 min").
    val readySince: Instant?,
    val dishes: List<KitchenDish>,
    val stations: List<String>,
    val note: String?,
    val readyTicketIds: List<String>,
    // Tickets held back by the kitchen or scheduled for later (a course that isn't fired yet).
    val held: Boolean
) {
    val readyCount get() = dishes.filter { it.state == DishState.READY }.sumOf { it.quantity }
    val totalCount get() = dishes.sumOf { it.quantity }
}

private val ReadyStatuses = setOf("READY", "EXPO_READY")
private val CookingStatuses = setOf("IN_PROGRESS")
private val OpenItemStatuses = setOf("PENDING", "FIRED", "IN_PROGRESS", "READY", "EXPO_READY")

private fun KdsTicketItem.dishState(): DishState = when (status) {
    in ReadyStatuses -> DishState.READY
    in CookingStatuses -> DishState.COOKING
    else -> DishState.WAITING
}

private fun KdsTicketItem.detailText(): String? = buildList {
    variantNameSnapshot?.takeIf { it.isNotBlank() }?.let { add(it) }
    modifiers.forEach { add(if (it.quantity > 1) "${it.quantity}× ${it.name}" else it.name); it.notes?.takeIf(String::isNotBlank)?.let { note -> add(note) } }
    notes?.takeIf { it.isNotBlank() }?.let { add(it) }
}.joinToString(" · ").ifBlank { null }

internal fun KdsState.kitchenOrders(): List<KitchenOrderCard> {
    if (!canRead) return emptyList()
    val stationNames = boards.associate { it.stationId to it.stationName }
    val held = upcomingTickets.mapTo(HashSet()) { it.id }
    return tickets.asSequence()
        .filter { !it.terminal }
        .groupBy { it.orderId }
        .mapNotNull { (orderId, tickets) -> card(orderId, tickets, stationNames, held) }
        .sortedWith(compareBy<KitchenOrderCard> { if (it.rush) 0 else 1 }.thenBy { it.readySince ?: it.sentAt ?: Instant.DISTANT_FUTURE })
}

private fun card(orderId: String, tickets: List<KdsTicket>, stationNames: Map<String, String>, held: Set<String>): KitchenOrderCard? {
    val first = tickets.first()
    val dishes = tickets.flatMap { ticket ->
        val station = ticket.stationName ?: stationNames[ticket.stationId].orEmpty()
        ticket.items.filter { it.status in OpenItemStatuses }.map { item ->
            KitchenDish(ticket.id, item.quantity, item.itemNameSnapshot, item.detailText(), item.dishState(), station)
        }
    }
    if (dishes.isEmpty()) return null
    val lane = when {
        dishes.any { it.state == DishState.READY } -> KitchenLane.READY
        dishes.any { it.state == DishState.COOKING } -> KitchenLane.COOKING
        else -> KitchenLane.WAITING
    }
    val readyTickets = tickets.filter { ticket -> ticket.status in ReadyStatuses || ticket.items.any { it.status in ReadyStatuses } }
    val readySince = readyTickets.mapNotNull { it.readyAt.asInstant() ?: it.items.mapNotNull { item -> item.readyAt.asInstant() }.minOrNull() }.minOrNull()
    val sentAt = tickets.mapNotNull { it.firedAt.asInstant() ?: it.createdAt.asInstant() }.minOrNull()
    val note = tickets.flatMap { listOfNotNull(it.occasion, it.notes, it.courseName) }.filter { it.isNotBlank() }.distinct().joinToString(" · ").ifBlank { null }
    return KitchenOrderCard(
        orderId = orderId,
        orderNumber = first.orderNumber.ifBlank { first.ticketNumber },
        table = tickets.firstNotNullOfOrNull { it.tableNumber?.takeIf(String::isNotBlank) ?: it.tableName?.takeIf(String::isNotBlank) },
        guests = tickets.firstNotNullOfOrNull { it.guestCount },
        waiterId = tickets.firstNotNullOfOrNull { it.createdBy },
        lane = lane,
        rush = tickets.any { it.priority == "RUSH" || it.priority == "VIP" },
        sentAt = sentAt,
        readySince = readySince,
        dishes = dishes.sortedBy { when (it.state) { DishState.READY -> 0; DishState.COOKING -> 1; DishState.WAITING -> 2 } },
        stations = dishes.map { it.station }.filter { it.isNotBlank() }.distinct(),
        note = note,
        readyTicketIds = readyTickets.map { it.id },
        held = tickets.all { it.id in held }
    )
}

internal fun KitchenOrderCard.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return listOfNotNull(orderNumber, table, note).any { it.contains(q, true) } ||
        dishes.any { it.name.contains(q, true) || it.detail?.contains(q, true) == true }
}
