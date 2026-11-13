package com.saporini.mobile_desktop.pos.kitchen

import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.kds.model.asInstant
import kotlin.time.Instant

// The waiter's view of the kitchen is the kitchen's own board with one card per order: a table's food from every station
// put together. An order is ready to serve as soon as one of its dishes is.

private val ReadyStatuses = setOf("READY", "EXPO_READY")
private val OpenItemStatuses = setOf("PENDING", "FIRED", "IN_PROGRESS", "READY", "EXPO_READY")

/** The open orders as tickets the board can show: one per order, its status saying the column it goes in. */
internal fun KdsState.orderTickets(): List<KdsTicket> {
    if (!canRead) return emptyList()
    return visibleTickets.groupBy { it.orderId }.mapNotNull { (_, tickets) -> orderTicket(tickets) }
}

private fun orderTicket(tickets: List<KdsTicket>): KdsTicket? {
    val items = tickets.flatMap { ticket -> ticket.items.filter { it.status in OpenItemStatuses } }
    if (items.isEmpty()) return null
    val first = tickets.first()
    val earliest: (List<String?>) -> String? = { times -> times.filterNotNull().minByOrNull { it.asInstant() ?: Instant.DISTANT_FUTURE } }
    val readyAt = earliest(tickets.flatMap { ticket -> ticket.items.filter { it.status in ReadyStatuses }.map { it.readyAt ?: ticket.readyAt } })
    return first.copy(
        id = first.orderId,
        ticketNumber = first.orderNumber.ifBlank { first.ticketNumber },
        stationName = tickets.mapNotNull { it.stationName?.takeIf(String::isNotBlank) }.distinct().joinToString(", ").ifBlank { null },
        tableId = tickets.firstNotNullOfOrNull { it.tableId },
        tableNumber = tickets.firstNotNullOfOrNull { it.tableNumber?.takeIf(String::isNotBlank) },
        tableName = tickets.firstNotNullOfOrNull { it.tableName?.takeIf(String::isNotBlank) },
        customerName = tickets.firstNotNullOfOrNull { it.customerName?.takeIf(String::isNotBlank) },
        guestCount = tickets.firstNotNullOfOrNull { it.guestCount },
        orderType = tickets.firstNotNullOfOrNull { it.orderType },
        createdBy = tickets.firstNotNullOfOrNull { it.createdBy },
        status = when {
            items.any { it.status in ReadyStatuses } -> "READY"
            items.any { it.status == "IN_PROGRESS" } -> "IN_PROGRESS"
            else -> "FIRED"
        },
        priority = when {
            tickets.any { it.priority == "RUSH" } -> "RUSH"
            tickets.any { it.priority == "VIP" } -> "VIP"
            else -> "NORMAL"
        },
        notes = tickets.mapNotNull { it.notes?.takeIf(String::isNotBlank) }.distinct().joinToString(" · ").ifBlank { null },
        occasion = tickets.firstNotNullOfOrNull { it.occasion?.takeIf(String::isNotBlank) },
        firedAt = earliest(tickets.map { it.firedAt ?: it.createdAt }),
        startedAt = earliest(tickets.map { it.startedAt }),
        readyAt = readyAt,
        createdAt = earliest(tickets.map { it.createdAt }),
        // What's ready first: it's what the waiter takes out.
        items = items.sortedBy { when (it.status) { in ReadyStatuses -> 0; "IN_PROGRESS" -> 1; else -> 2 } }
    )
}
