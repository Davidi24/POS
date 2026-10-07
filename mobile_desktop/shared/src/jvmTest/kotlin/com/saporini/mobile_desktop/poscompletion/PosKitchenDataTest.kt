package com.saporini.mobile_desktop.poscompletion
import com.saporini.mobile_desktop.kds.model.*
import com.saporini.mobile_desktop.pos.kitchen.*
import kotlin.test.*
import kotlin.time.Instant

// The waiter's Kitchen Status: one card per order across stations, in the lane of what to do next.
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
        assertEquals(1, state(ticket()).kitchenOrders().size)
        assertTrue(state(ticket(), permissions = emptySet()).kitchenOrders().isEmpty())
    }
    @Test fun dishKeepsQuantityVariantModifiersAndNotes() {
        val dish = state(ticket()).kitchenOrders().single().dishes.single()
        assertEquals(2, dish.quantity); assertEquals("Pizza", dish.name); assertEquals("Large · 2× Cheese · No olives", dish.detail)
    }
    @Test fun ticketsOfOneOrderFromTwoStationsAreOneCard() {
        val card = state(ticket("a", "IN_PROGRESS", station = "grill"), ticket("b", "READY", station = "bar")).kitchenOrders().single()
        assertEquals(KitchenLane.READY, card.lane)
        assertEquals(listOf("b"), card.readyTicketIds)
        assertEquals(2, card.readyCount); assertEquals(4, card.totalCount)
        assertEquals(setOf("GRILL", "BAR"), card.stations.toSet())
    }
    @Test fun lanesFollowTheFoodState() {
        val cards = state(ticket("a", "FIRED", order = "1"), ticket("b", "IN_PROGRESS", order = "2")).kitchenOrders().associateBy { it.orderId }
        assertEquals(KitchenLane.WAITING, cards.getValue("1").lane)
        assertEquals(KitchenLane.COOKING, cards.getValue("2").lane)
    }
    @Test fun finishedTicketsAreLeftOut() {
        assertTrue(state(ticket(status = "COMPLETED")).kitchenOrders().isEmpty())
    }
    @Test fun heldTicketsAreMarked() {
        val card = state(ticket("hold", "PENDING").copy(priority = "HOLD_FIRE")).kitchenOrders().single()
        assertTrue(card.held)
    }
    @Test fun searchFindsTableAndDish() {
        val card = state(ticket()).kitchenOrders().single()
        assertTrue(card.matches("t5")); assertTrue(card.matches("pizza")); assertFalse(card.matches("pasta"))
    }
}
