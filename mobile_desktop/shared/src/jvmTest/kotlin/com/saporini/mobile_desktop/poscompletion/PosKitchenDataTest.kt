package com.saporini.mobile_desktop.poscompletion
import com.saporini.mobile_desktop.kds.model.*
import com.saporini.mobile_desktop.pos.kitchen.*
import kotlin.test.*
import kotlin.time.Instant

// The waiter's Kitchen Status: the kitchen's board with one card per order across stations.
class PosKitchenDataTest {
    private val now = Instant.parse("2026-09-25T12:00:00Z")
    private fun ticket(id: String = "t", status: String = "FIRED", order: String = "o", station: String = "s", itemStatus: String = status) =
        KdsTicket(id, "r", "b", station, ticketNumber = "K-$id", orderId = order, orderNumber = "O-$order", status = status, stationName = station.uppercase(),
            tableNumber = "T5", guestCount = 4, createdBy = "waiter", firedAt = "2026-09-25T11:55:00Z", readyAt = if (status == "READY") "2026-09-25T11:58:00Z" else null,
            items = listOf(KdsTicketItem("i-$id", itemNameSnapshot = "Pizza", quantity = 2, status = itemStatus, variantNameSnapshot = "Large",
                modifiers = listOf(KdsModifier("Cheese", 2)), notes = "No olives")))
    private fun state(vararg tickets: KdsTicket, permissions: Set<String> = setOf("KDS_READ")) =
        KdsState(now = now, scope = KdsScope("u", "r", "b"), permissions = permissions,
            boards = tickets.groupBy { it.stationId }.map { (station, list) -> KdsBoard(station, stationName = station.uppercase(), tickets = list) })

    @Test fun waiterNeedsOnlyReadPermission() {
        assertEquals(1, state(ticket(), permissions = setOf("ORDER_READ")).orderTickets().size)
        assertTrue(state(ticket(), permissions = emptySet()).orderTickets().isEmpty())
    }
    @Test fun ticketsOfOneOrderFromTwoStationsAreOneCard() {
        val card = state(ticket("a", "IN_PROGRESS", station = "grill"), ticket("b", "READY", station = "bar")).orderTickets().single()
        // One ready dish is enough for Ready to serve.
        assertEquals("READY", card.status)
        assertEquals("o", card.id)
        assertEquals(listOf("READY", "IN_PROGRESS"), card.items.map { it.status })
        assertEquals(setOf("GRILL", "BAR"), card.stationName.orEmpty().split(", ").toSet())
        assertEquals("2026-09-25T11:58:00Z", card.readyAt)
    }
    @Test fun statusFollowsTheFood() {
        val cards = state(ticket("a", "FIRED", order = "1"), ticket("b", "IN_PROGRESS", order = "2")).orderTickets().associateBy { it.id }
        assertEquals("FIRED", cards.getValue("1").status)
        assertEquals("IN_PROGRESS", cards.getValue("2").status)
    }
    @Test fun finishedTicketsAreLeftOut() {
        assertTrue(state(ticket(status = "COMPLETED")).orderTickets().isEmpty())
    }
    @Test fun heldTicketsWaitInUpcoming() {
        assertTrue(state(ticket("hold", "PENDING").copy(priority = "HOLD_FIRE")).orderTickets().isEmpty())
    }
}
