@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.saporini.mobile_desktop.orders

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.orders.OrdersContent
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.domain.repository.*
import com.saporini.mobile_desktop.pos.orders.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.*

class OrdersUiScreenshotTest {
    @Test fun desktop() = capture(1440,1000)
    @Test fun phone() = capture(390,844)
    @Test fun narrowPhone() = capture(360,800)
    @Test fun tablet() = capture(768,900)
    private fun capture(width: Int, height: Int) {
        val scheduler = TestCoroutineScheduler(); val dispatcher = StandardTestDispatcher(scheduler); Dispatchers.setMain(dispatcher)
        val base = order().copy(orderNumber = "#1042",tableNumber = "7",customerName = "Emma Wilson",guestCount = 3, subtotal = OrderDecimal("42.50"),total = OrderDecimal("42.50"), fulfillmentStatus = OrderFulfillmentStatus.IN_PREPARATION,
            lineItems = listOf(OrderLineItem("line-1","item-1",itemNameSnapshot = "Margherita pizza",quantity = 2,unitPriceSnapshot = OrderDecimal("12.50"),priceDeltaTotal = OrderDecimal.ZERO,discountTotal = OrderDecimal.ZERO,taxTotal = OrderDecimal.ZERO,lineTotal = OrderDecimal("25.00"),status = OrderLineItemStatus.PENDING,createdAt = "2026-09-22T12:00:00Z",updatedAt = "2026-09-22T12:00:00Z")),
            events = listOf(OrderEvent("event-1",OrderEventType.CREATED,"Order created",createdAt = "2026-09-22T12:00:00Z")))
        val repository = object: OrderRepository by unsupportedRepository() {
            override suspend fun getOpenOrders(restaurantId: String, branchId: String) = (0..5).map { i -> summary("order-$i").copy(orderNumber = "#${1042-i}",tableNumber = "${7-i}",guestCount = 3,customerName = listOf("Emma Wilson","James Carter","Sophie Miller")[i%3],total = OrderDecimal("42.50"),fulfillmentStatus = if(i%2==0) OrderFulfillmentStatus.IN_PREPARATION else OrderFulfillmentStatus.READY) }
            override suspend fun getOrdersPage(restaurantId: String, branchId: String, from: String?, to: String?, status: OrderStatus?,
                customerId: String?, search: String?, historyOnly: Boolean, openOnly: Boolean, page: Int, size: Int): OrderPage {
                val items = (0..5).map { i -> summary("order-$i").copy(orderNumber = "#${1042-i}", tableNumber = "${7-i}", guestCount = 3,
                    customerName = listOf("Emma Wilson", "James Carter", "Sophie Miller")[i%3], total = OrderDecimal("42.50"),
                    fulfillmentStatus = if (i%2==0) OrderFulfillmentStatus.IN_PREPARATION else OrderFulfillmentStatus.READY) }
                return OrderPage(items, page, size, items.size.toLong(), false)
            }
            override suspend fun getOrder(restaurantId: String, orderId: String) = base.copy(id = orderId)
        }
        val session = SessionManager(); session.signIn(user(permissions = listOf("ORDER_READ","ORDER_CREATE","ORDER_UPDATE","ORDER_TRANSFER","ORDER_CANCEL","ORDER_CLOSE","ORDER_REOPEN","ORDER_VOID","ORDER_DISCOUNT_APPLY","MENUS_READ")))
        val catalogItem = OrderCatalogItem("item-1","Margherita pizza",OrderDecimal("12.50"),true,description = "Tomato, mozzarella and fresh basil")
        val catalogMenu = OrderCatalogMenu("menu-1","All Day",true,OrderCatalogRestaurant("restaurant-1"),sections = listOf(OrderCatalogSection("section-1","Mains",true,items = listOf(catalogItem))))
        val catalog = object: OrderCatalogRepository {
            override suspend fun getMenus(restaurantId: String,page: Int,size: Int) = OrderCatalogPage(listOf(catalogMenu),0,50,1,1,false,false)
            override suspend fun getMenu(restaurantId: String,menuId: String) = catalogMenu
            override suspend fun getItemChoices(restaurantId: String,menuId: String,itemId: String) = OrderItemChoices(catalogItem,emptyList())
        }
        val tables = java.lang.reflect.Proxy.newProxyInstance(com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository::class.java.classLoader,arrayOf(com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository::class.java)) { _,method,_ ->
            when(method.name) { "getTableLayout" -> com.saporini.mobile_desktop.pos.tables.domain.model.BranchTableLayout("restaurant-1","branch-1",emptyList(),emptyList()); else -> error(method.name) }
        } as com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository
        val model = OrdersScreenModel(repository,session,catalogRepository = catalog,tableRepository = tables)
        val scene = ImageComposeScene(width,height,coroutineContext = dispatcher) { SaporiniTheme { OrdersContent(model) } }
        val output = File("/tmp/orders-release/screenshots/$width").apply { mkdirs() }
        fun snap(name: String) {
            repeat(6) { scheduler.advanceTimeBy(100); scheduler.runCurrent(); scene.render(scheduler.currentTime * 1_000_000L).close(); Thread.sleep(25) }
            val image = scene.render(scheduler.currentTime * 1_000_000L)
            try { val png = image.encodeToData(EncodedImageFormat.PNG)!!; try { File(output,"$name.png").writeBytes(png.bytes) } finally { png.close() } } finally { image.close() }
        }
        fun all(): List<SemanticsNode> {
            fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
        }
        fun label(n: SemanticsNode): String = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }+n.children.joinToString { label(it) }
        fun click(text: String) {
            val node = all().lastOrNull { label(it).contains(text) && it.config.getOrNull(SemanticsActions.OnClick)?.action != null } ?: error("Missing action $text")
            assertTrue(node.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()); scheduler.runCurrent()
        }
        try {
            scheduler.runCurrent(); model.refresh(); snap("01-list"); assertTrue(all().any { label(it).contains("#1042") })
            model.selectOrder("order-0"); snap("02-details"); assertTrue(all().any { label(it).contains("Margherita pizza") })
            if (width >= 1000) {
                // Desktop: the order sits beside the list with Items / Order info / Notes / History tabs.
                click("History"); snap("03-activity"); assertTrue(all().any { label(it).contains("Order created") })
                click("Items"); click("Add item"); snap("07-composer")
                // "New order" is the draggable edge button here; the list is what matters after closing.
                model.closeOrderDetails(); snap("scratch"); assertTrue(all().any { label(it).contains("#1041") })
            } else {
                click("Activity"); snap("03-activity"); assertTrue(all().any { label(it).contains("Order created") })
                click("Items"); click("•••"); snap("04-actions"); click("Apply discount"); snap("05-discount"); click("Close")
                // "Add or edit items" first asks what to change.
                click("Add or edit items"); snap("07-edit-choice"); click("Order items"); snap("07-composer"); click("+ Add item"); snap("08-item-options"); click("Close"); click("Orders")
                // "New order" is the draggable edge button, which has no click action to press here.
                model.closeOrderDetails(); snap("scratch"); assertTrue(all().any { label(it).contains("#1041") })
            }
            assertTrue(all().none { it.config.getOrNull(SemanticsProperties.EditableText)?.text?.contains("orderNumber") == true })
        } finally { scene.close(); model.onDispose(); Dispatchers.resetMain() }
    }
}
