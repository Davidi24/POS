package com.saporini.mobile_desktop.kds

import com.saporini.mobile_desktop.kds.model.*
import kotlin.test.*

class KdsStateTest {
    @Test fun variantsAndSpecialInstructionsStaySeparateInAllDayCounts() {
        val soup = kdsTicket().items.single()
        val ticket = kdsTicket().copy(items = listOf(soup, soup.copy(id = "large", quantity = 3, variantNameSnapshot = "Large"),
            soup.copy(id = "allergy", quantity = 1, notes = "No dairy"), soup.copy(id = "cancelled", quantity = 20, status = "CANCELLED")))
        val state = KdsState(now = testNow, boards = listOf(KdsBoard("s", tickets = listOf(ticket))))
        assertEquals(3, state.allDay.size); assertEquals(6, state.allDay.sumOf { it.pending }); assertEquals(1, state.allDay.first { it.notes == "No dairy" }.pending)
    }
    @Test fun futureDueTimeDoesNotHideAlreadyFiredFood() {
        val pending = kdsTicket("future", "PENDING").copy(dueAt = "2026-09-27T12:00:00Z")
        val fired = pending.copy(id = "fired", status = "FIRED", items = pending.items.map { it.copy(status = "FIRED") })
        val overdue = pending.copy(id = "overdue", dueAt = "2026-09-27T09:00:00Z")
        val state = KdsState(now = testNow, boards = listOf(KdsBoard("s", tickets = listOf(pending, fired, overdue))))
        assertEquals(setOf("fired", "overdue"), state.visibleTickets.map { it.id }.toSet())
        assertEquals(setOf("future", "fired"), state.upcomingTickets.map { it.id }.toSet())
    }
    @Test fun mixedTicketCanOnlyCompleteWhenEveryRemainingItemIsReady() {
        val ready = kdsTicket(status = "READY"); val mixed = ready.copy(status = "IN_PROGRESS", items = ready.items + ready.items.single().copy(id = "preparing", status = "IN_PROGRESS"))
        assertFalse(mixed.allows(KdsAction.COMPLETE)); assertTrue(mixed.allows(KdsAction.COMPLETE, "i")); assertTrue(mixed.allows(KdsAction.READY, "preparing"))
        assertFalse(ready.copy(status = "CANCELLED").allows(KdsAction.COMPLETE))
        assertFalse(ready.copy(items = listOf(ready.items.single().copy(status = "NEW_UNKNOWN_STATUS"))).allows(KdsAction.READY))
    }
    @Test fun filteringFindsModifiersAndPrioritizesRushTickets() {
        val normal = kdsTicket(); val rush = kdsTicket("rush").copy(priority = "RUSH", items = listOf(normal.items.single().copy(modifiers = listOf(KdsModifier("Extra basil")))))
        val state = KdsState(boards = listOf(KdsBoard("s", tickets = listOf(normal, rush))))
        assertEquals("rush", state.visibleTickets.first().id)
        assertEquals(listOf("rush"), state.copy(filter = KdsTicketFilter(query = "basil")).visibleTickets.map { it.id })
    }
    @Test fun editingAStationPreservesItsRoutingAndDeviceSettings() {
        val station = KdsStation("s", "r", "b", name = "Grill", deviceId = "device", active = false, acceptsScheduledOrders = false,
            routings = listOf(KdsRouting("dish", id = "route", menuItemName = "Soup", priority = "VIP", courseLabel = "Starter", active = false)))
        val input = station.toInput(); assertEquals("device", input.deviceId); assertFalse(input.active); assertFalse(input.acceptsScheduledOrders)
        assertEquals("VIP", input.routings.single().priority); assertFalse(input.routings.single().active); assertNull(input.problem())
    }
}
