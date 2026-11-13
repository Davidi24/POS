package com.saporini.mobile_desktop.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderCatalogItem
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderCatalogMenu
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderCatalogOptionLink
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderCatalogSection
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderCatalogVariant
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderChoice
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderChoiceGroup
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderConfiguredChoiceGroup
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderItemChoices
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderItemDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderOptionDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderRepository
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderRequestDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderScreenModel
import com.saporini.mobile_desktop.pos.reservations.preorder.ui.PreOrderCardContent
import com.saporini.mobile_desktop.pos.reservations.preorder.ui.PreOrderDishes
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private fun d(text: String) = OrderDecimal(text)

private class GalleryBookingPreOrders : PreOrderRepository {
    val preOrder = PreOrderDto("p1", "branch-1", "r1", "BK-7Q2", (Clock.System.now() + 5.hours).toString(), 6, "Rossi family", "SCHEDULED",
        paymentStatus = "PAID", total = d("78.50"), sendAt = (Clock.System.now() + 4.hours).toString(), notes = "Starters as soon as they sit down",
        items = listOf(
            PreOrderItemDto("i1", "m1", null, "Margherita", null, 3, d("9.00"), d("27.00")),
            PreOrderItemDto("i2", "m3", "v2", "Bistecca", "Large", 1, d("32.00"), d("34.50"),
                options = listOf(PreOrderOptionDto("o2", "Rosemary potatoes", d("2.50")))),
            PreOrderItemDto("i3", "m4", null, "Tiramisù", null, 2, d("7.00"), d("14.00"), notes = "One without cocoa")
        ))
    override suspend fun forBooking(restaurantId: String, reservationId: String) = preOrder
    override suspend fun save(restaurantId: String, reservationId: String, request: PreOrderRequestDto) = preOrder
    override suspend fun cancel(restaurantId: String, reservationId: String, reason: String?) = preOrder.copy(status = "CANCELLED")
    override suspend fun sendNow(restaurantId: String, reservationId: String) = preOrder.copy(status = "SENT")
    override suspend fun forBranch(restaurantId: String, branchId: String, from: String, to: String, status: String?) = listOf(preOrder)
}

private class GalleryDishes : PreOrderDishes {
    private fun dish(id: String, name: String, price: String, variants: List<OrderCatalogVariant>? = null, options: List<OrderCatalogOptionLink>? = null) =
        OrderCatalogItem(id, name, d(price), true, variants = variants, optionGroups = options)
    override suspend fun menus(restaurantId: String) = listOf(OrderCatalogMenu("menu-1", "Dinner", true, sections = listOf(
        OrderCatalogSection("s1", "Pizza", true, 0, listOf(dish("m1", "Margherita", "9.00"), dish("m2", "Diavola", "11.50"))),
        OrderCatalogSection("s2", "Mains", true, 1, listOf(dish("m3", "Bistecca", "28.00",
            variants = listOf(OrderCatalogVariant("v1", "Regular", d("0"), true, true), OrderCatalogVariant("v2", "Large", d("4.00"), true)),
            options = listOf(OrderCatalogOptionLink("l1", "g1", "Side", true, required = true))), dish("m5", "Risotto ai funghi", "15.00"))),
        OrderCatalogSection("s3", "Desserts", true, 2, listOf(dish("m4", "Tiramisù", "7.00"), dish("m6", "Panna cotta", "6.50")))
    )))
    override suspend fun choices(restaurantId: String, menuId: String, itemId: String) = OrderItemChoices(
        dish("m3", "Bistecca", "28.00"),
        listOf(OrderConfiguredChoiceGroup(OrderCatalogOptionLink("l1", "g1", "Side", true, required = true), OrderChoiceGroup("g1", "restaurant-1", "Side", true,
            1, 1, true, listOf(OrderChoice("o1", "g1", "Salad", d("0"), true), OrderChoice("o2", "g1", "Rosemary potatoes", d("2.50"), true))))))
}

class PreOrderGalleryTest {
    @Test
    fun bookingPreOrder() = gallery {
        val model = PreOrderScreenModel(GalleryBookingPreOrders(), gallerySession())
        settle(); model.open("r1"); settle()
        render("preorder-card", 520, 640, required = listOf("Food ordered ahead", "Margherita", "Send now")) {
            val state by model.state.collectAsState()
            Box(Modifier.padding(16.dp).width(440.dp)) { PreOrderCardContent(state, model, GalleryDishes(), 6, bookingOpen = true) }
        }
        model.startEdit(); settle()
        render("preorder-editor", 1300, 900, required = listOf("Change the pre-order", "Bistecca", "THEIR FOOD")) {
            val state by model.state.collectAsState()
            Box(Modifier.padding(16.dp).width(440.dp)) { PreOrderCardContent(state, model, GalleryDishes(), 6, bookingOpen = true) }
        }
        model.onDispose()
    }
}
