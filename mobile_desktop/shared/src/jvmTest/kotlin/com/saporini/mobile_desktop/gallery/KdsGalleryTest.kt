package com.saporini.mobile_desktop.gallery

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.saporini.mobile_desktop.kds.KdsScreenModel
import com.saporini.mobile_desktop.kds.data.KdsMenuEntry
import com.saporini.mobile_desktop.kds.data.KdsMenuRepository
import com.saporini.mobile_desktop.kds.data.KdsRepository
import com.saporini.mobile_desktop.kds.model.KdsAction
import com.saporini.mobile_desktop.kds.model.KdsActionInput
import com.saporini.mobile_desktop.kds.model.KdsBoard
import com.saporini.mobile_desktop.kds.model.KdsDevice
import com.saporini.mobile_desktop.kds.model.KdsHistoryFilter
import com.saporini.mobile_desktop.kds.model.KdsLiveEvent
import com.saporini.mobile_desktop.kds.model.KdsModifier
import com.saporini.mobile_desktop.kds.model.KdsScope
import com.saporini.mobile_desktop.kds.model.KdsRouting
import com.saporini.mobile_desktop.kds.model.KdsStation
import com.saporini.mobile_desktop.kds.model.KdsStationInput
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.kds.model.KdsTicketItem
import com.saporini.mobile_desktop.kds.model.KdsTicketPage
import com.saporini.mobile_desktop.kds.ui.KdsSection
import com.saporini.mobile_desktop.kds.ui.KdsSectionContent
import com.saporini.mobile_desktop.kds.ui.StationsDialog
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderItemDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderRepository
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderRequestDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderScreenModel
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private val now = Clock.System.now()
private fun ago(minutes: Int) = (now - minutes.minutes).toString()

private fun item(id: String, name: String, qty: Int, status: String, variant: String? = null, notes: String? = null, modifiers: List<KdsModifier> = emptyList()) =
    KdsTicketItem(id, itemNameSnapshot = name, quantity = qty, status = status, variantNameSnapshot = variant, notes = notes, modifiers = modifiers)

private fun ticket(id: String, number: String, table: String?, status: String, firedMinutesAgo: Int, items: List<KdsTicketItem>, priority: String = "NORMAL",
                   guests: Int? = null, course: String? = null, notes: String? = null, occasion: String? = null, dueInMinutes: Int? = null, readyMinutesAgo: Int? = null) =
    KdsTicket(id, "restaurant-1", "branch-1", "st1", number, "o-$id", number, "GRL", "Grill", tableNumber = table, guestCount = guests, status = status,
        priority = priority, courseName = course, notes = notes, firedAt = if (status == "PENDING") null else ago(firedMinutesAgo), createdAt = ago(firedMinutesAgo),
        readyAt = readyMinutesAgo?.let { ago(it) }, dueAt = dueInMinutes?.let { (now + it.minutes).toString() }, items = items, occasion = occasion)

private class GalleryKds : KdsRepository {
    val tickets = listOf(
        ticket("t1", "K41", "4", "FIRED", 3, listOf(item("i1", "Margherita", 2, "FIRED"), item("i2", "Diavola", 1, "FIRED", notes = "Extra spicy")), guests = 3),
        ticket("t2", "K42", "12", "FIRED", 24, listOf(item("i3", "Tagliatelle al ragù", 2, "FIRED", variant = "Large"),
            item("i4", "Risotto ai funghi", 1, "FIRED", modifiers = listOf(KdsModifier("No parmesan")))), priority = "RUSH", guests = 4, course = "Mains"),
        ticket("t3", "K39", "7", "IN_PROGRESS", 12, listOf(item("i5", "Bistecca", 1, "IN_PROGRESS", variant = "Medium rare"), item("i6", "Patate al forno", 2, "READY")),
            guests = 2, occasion = "🎂 Birthday · Candles at dessert"),
        ticket("t4", "K38", null, "IN_PROGRESS", 9, listOf(item("i7", "Margherita", 1, "IN_PROGRESS"), item("i8", "Calzone", 1, "IN_PROGRESS")), priority = "VIP",
            notes = "Takeaway, customer waiting at the bar").copy(customerName = "Anna"),
        ticket("t5", "K36", "2", "READY", 20, listOf(item("i9", "Caprese", 2, "READY"), item("i10", "Bruschetta", 1, "READY")), guests = 2, readyMinutesAgo = 7),
        ticket("t6", "K43", "9", "PENDING", 0, listOf(item("i11", "Tiramisù", 4, "PENDING")), priority = "HOLD_FIRE", course = "Dessert"),
        ticket("t7", "K44", "15", "PENDING", 0, listOf(item("i12", "Lasagna", 6, "PENDING")), course = "Mains", dueInMinutes = 35)
    )
    val history = listOf(
        ticket("h1", "K30", "3", "COMPLETED", 60, listOf(item("h1i", "Margherita", 2, "COMPLETED"))).copy(readyAt = ago(48), completedAt = ago(45)),
        ticket("h2", "K31", "8", "CANCELLED", 70, listOf(item("h2i", "Diavola", 1, "CANCELLED"))).copy(completedAt = ago(66), voidReason = "Guest left"),
        ticket("h3", "K29", "5", "COMPLETED", 90, listOf(item("h3i", "Bistecca", 1, "COMPLETED"))).copy(readyAt = ago(62), completedAt = ago(60))
    )
    override suspend fun board(scope: KdsScope, stationId: String?, deviceId: String?) = listOf(
        KdsBoard("st1", "GRL", "Grill", tickets = tickets.filter { it.stationId == "st1" }),
        KdsBoard("st2", "PAS", "Pastry", tickets = emptyList()))
    override suspend fun display(scope: KdsScope, deviceId: String) = board(scope).first()
    override suspend fun ticket(scope: KdsScope, ticketId: String) = (tickets + history).first { it.id == ticketId }
    override suspend fun orderTickets(scope: KdsScope, orderId: String) =
        listOf(tickets.first().copy(id = "t1b", orderId = orderId, stationId = "st2", stationName = "Pastry", status = "IN_PROGRESS"))
    override suspend fun syncOrder(scope: KdsScope, orderId: String) = tickets
    override suspend fun history(scope: KdsScope, filter: KdsHistoryFilter, stationId: String?, deviceId: String?, page: Int, size: Int) =
        KdsTicketPage(history, page, size, history.size.toLong(), 1, false)
    override suspend fun action(scope: KdsScope, ticketId: String, itemId: String?, action: KdsAction, input: KdsActionInput) = tickets.first { it.id == ticketId }
    override suspend fun stations(scope: KdsScope) = listOf(
        KdsStation("st1", "restaurant-1", "branch-1", "GRL", "Grill", "GRILL", deviceId = "d1", deviceName = "Grill screen", routings = listOf(
            KdsRouting("d4", menuItemName = "Bistecca", priority = "RUSH", courseLabel = "Mains"),
            KdsRouting("d1", menuItemName = "Margherita", displayOrder = 1),
            KdsRouting("d3", menuItemName = "Calzone", displayOrder = 2, active = false))),
        KdsStation("st2", "restaurant-1", "branch-1", "PAS", "Pastry", "DESSERT", displayOrder = 1, active = false))
    override suspend fun devices(scope: KdsScope) = listOf(KdsDevice("d1", "KDS-1", "Grill screen", active = true, online = true, assignedStationId = "st1"))
    override suspend fun saveStation(scope: KdsScope, id: String?, input: KdsStationInput) = stations(scope).first()
    override fun changes(scope: KdsScope) = flowOf(KdsLiveEvent.CONNECTED)
}

private class GalleryKdsMenu : KdsMenuRepository {
    private fun dish(id: String, name: String, price: Double, available: Boolean = true) =
        MenuItem(id, null, name, null, price, null, available, 0, emptyList(), emptyList(), emptyList())
    override suspend fun load(restaurantId: String) = listOf(
        KdsMenuEntry("m", "Dinner", "s1", "Pizza", dish("d1", "Margherita", 9.0)),
        KdsMenuEntry("m", "Dinner", "s1", "Pizza", dish("d2", "Diavola", 11.5, available = false)),
        KdsMenuEntry("m", "Dinner", "s1", "Pizza", dish("d3", "Calzone", 12.0)),
        KdsMenuEntry("m", "Dinner", "s2", "Mains", dish("d4", "Bistecca", 28.0)),
        KdsMenuEntry("m", "Dinner", "s2", "Mains", dish("d5", "Risotto ai funghi", 15.0, available = false)),
        KdsMenuEntry("m", "Dinner", "s3", "Desserts", dish("d6", "Tiramisù", 7.0)))
    override suspend fun availability(entry: KdsMenuEntry, available: Boolean) = entry.item.copy(available = available)
}

private class GalleryPreOrders : PreOrderRepository {
    val list = listOf(
        PreOrderDto("p1", "branch-1", "r1", "BK-7Q2", (now + 180.minutes).toString(), 8, "Rossi family", "SCHEDULED", sendAt = (now + 150.minutes).toString(),
            items = listOf(PreOrderItemDto(menuItemId = "d1", itemName = "Margherita", quantity = 4), PreOrderItemDto(menuItemId = "d4", itemName = "Bistecca", quantity = 2))),
        PreOrderDto("p2", "branch-1", "r2", "BK-9M1", (now + (26 * 60).minutes).toString(), 12, "Studio Bianchi", "SCHEDULED",
            items = listOf(PreOrderItemDto(menuItemId = "d6", itemName = "Tiramisù", quantity = 12)))
    )
    override suspend fun forBooking(restaurantId: String, reservationId: String) = list.firstOrNull { it.reservationId == reservationId }
    override suspend fun save(restaurantId: String, reservationId: String, request: PreOrderRequestDto) = list.first()
    override suspend fun cancel(restaurantId: String, reservationId: String, reason: String?) = list.first()
    override suspend fun sendNow(restaurantId: String, reservationId: String) = list.first().copy(status = "SENT")
    override suspend fun forBranch(restaurantId: String, branchId: String, from: String, to: String, status: String?) = list
}

class KdsGalleryTest {
    @Test
    fun kitchen() = gallery {
        val session = gallerySession()
        val model = KdsScreenModel(GalleryKds(), GalleryKdsMenu(), session)
        val preOrders = PreOrderScreenModel(GalleryPreOrders(), session)
        model.setActive(true); settle()
        fun shot(name: String, section: KdsSection, width: Int = 1440, height: Int = 960, required: List<String> = emptyList()) =
            render(name, width, height, required) {
                val state by model.state.collectAsState()
                KdsSectionContent(state, model, section, clock = { now }, preOrderModel = { preOrders })
            }
        shot("kds-tickets", KdsSection.TICKETS, required = listOf("Kitchen", "Margherita", "Start cooking", "All day"))
        shot("kds-tickets-phone", KdsSection.TICKETS, 420, 900)
        model.selectTicket("t3"); settle()
        shot("kds-ticket-detail", KdsSection.TICKETS, required = listOf("SAME ORDER AT OTHER STATIONS"))
        model.closeTicket()
        model.selectSection(KdsSection.UPCOMING); settle()
        shot("kds-upcoming", KdsSection.UPCOMING, required = listOf("Waiting to start", "Ordered with bookings"))
        model.selectSection(KdsSection.MENU); settle()
        shot("kds-menu", KdsSection.MENU, required = listOf("Sold out", "Diavola"))
        model.selectSection(KdsSection.HISTORY); settle()
        shot("kds-history", KdsSection.HISTORY, required = listOf("History", "Guest left"))
        render("kds-station-editor", 1440, 1100, required = listOf("DISHES MADE HERE", "Bistecca", "Add a dish")) {
            val state by model.state.collectAsState()
            StationsDialog(state, model, GalleryKdsMenu(), openStation = "st1") {}
        }
        model.onDispose()
    }
}
