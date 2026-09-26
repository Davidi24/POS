@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop

import io.ktor.client.engine.mock.respond
import io.ktor.serialization.kotlinx.json.json
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import com.saporini.mobile_desktop.orders.order
import com.saporini.mobile_desktop.orders.summary
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuPage
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuRestaurant
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuSection
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import com.saporini.mobile_desktop.pos.menu.ui.MenuScreenModel
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderCatalogRepository
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderRepository
import com.saporini.mobile_desktop.pos.orders.ui.OrdersScreenModel
import com.saporini.mobile_desktop.pos.reservations.domain.model.*
import com.saporini.mobile_desktop.pos.reservations.domain.repository.ReservationRepository
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationsScreenModel
import com.saporini.mobile_desktop.pos.tables.domain.model.*
import com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository
import com.saporini.mobile_desktop.pos.tables.ui.TablesScreenModel
import com.saporini.mobile_desktop.pos.ui.PosScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Renders the whole app (navigation + screen) with sample data at phone, tablet and desktop sizes,
// clicking through Tables, Orders, Reservations and Menu like a user. Output: build/reports/app-ui/<size>/.
class AppScreenshotTest {
    @Test fun phone360() = run(360, 800)
    @Test fun phone390() = run(390, 844)
    @Test fun tablet768() = run(768, 1024)
    @Test fun tablet1024() = run(1024, 768)
    @Test fun desktop1280() = run(1280, 800)
    @Test fun desktop1920() = run(1920, 1080)

    private val LOAD_DELAY = 2_000L
    private val restaurantId = "10000000-0000-0000-0000-000000000001"
    private val branchId = "branch-1"

    private fun run(width: Int, height: Int) {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        val missing = mutableSetOf<String>()
        val session = SessionManager().apply {
            signIn(CurrentUserResponse(
                id = "user-1", restaurantId = restaurantId, defaultBranchId = branchId,
                email = "staff@example.invalid", username = "staff", firstName = "David", lastName = "Keci",
                isActive = true, emailVerified = true, phoneVerified = true, roles = listOf("MANAGER"),
                permissions = listOf(
                    "MENUS_CREATE", "MENUS_READ", "MENUS_UPDATE", "ORDER_CREATE", "ORDER_READ", "ORDER_UPDATE",
                    "SETTINGS_READ", "SETTINGS_UPDATE", "ORDER_TRANSFER", "ORDER_CANCEL", "ORDER_CLOSE"
                )
            ))
        }
        // The main list calls answer after 2 s (virtual time) so the loading skeletons can be captured.
        val baseTables = tableRepository(missing)
        val tables = object : TableLayoutRepository by baseTables {
            override suspend fun getTableLayout(restaurantId: String, branchId: String): BranchTableLayout {
                kotlinx.coroutines.delay(LOAD_DELAY); return baseTables.getTableLayout(restaurantId, branchId)
            }
        }
        val baseOrders = orderRepository(missing)
        val orders = object : OrderRepository by baseOrders {
            override suspend fun getOpenOrders(restaurantId: String, branchId: String): List<OrderSummary> {
                kotlinx.coroutines.delay(LOAD_DELAY); return baseOrders.getOpenOrders(restaurantId, branchId)
            }
        }
        val baseReservations = reservationRepository(missing)
        val reservations = object : ReservationRepository by baseReservations {
            override suspend fun getBranchReservationCalendar(restaurantId: String, branchId: String, from: String?, to: String?): List<Reservation> {
                kotlinx.coroutines.delay(LOAD_DELAY); return baseReservations.getBranchReservationCalendar(restaurantId, branchId, from, to)
            }
            override suspend fun getTodayReservations(restaurantId: String, branchId: String): List<Reservation> {
                kotlinx.coroutines.delay(LOAD_DELAY); return baseReservations.getTodayReservations(restaurantId, branchId)
            }
        }
        val menus = menuRepository(missing)
        val catalog = catalogRepository()
        startKoin {
            modules(module {
                single { session }
                single<TableLayoutRepository> { tables }
                single<OrderRepository> { orders }
                single<OrderCatalogRepository> { catalog }
                single<ReservationRepository> { reservations }
                single<MenuRepository> { menus }
                factory { TablesScreenModel(repository = get(), orderRepository = get(), sessionManager = get()) }
                factory { OrdersScreenModel(repository = get(), sessionManager = get(), catalogRepository = get(), tableRepository = get()) }
                factory { ReservationsScreenModel(repository = get(), sessionManager = get(), tableLayoutRepository = get()) }
                factory { MenuScreenModel(repository = get(), sessionManager = get()) }
                single { com.saporini.mobile_desktop.notifications.NotificationApi(notificationClient()) }
                single { com.saporini.mobile_desktop.notifications.NotificationCenter(get(), get()) }
            })
        }
        val scene = ImageComposeScene(width, height, density = Density(1f), coroutineContext = dispatcher) {
            SaporiniTheme { PosScreen.Content() }
        }
        val output = File("build/reports/app-ui/${width}x$height").apply { deleteRecursively(); mkdirs() }

        fun settle(frames: Int = 8) = repeat(frames) {
            scheduler.advanceTimeBy(120); scheduler.runCurrent()
            scene.render(scheduler.currentTime * 1_000_000L).close(); Thread.sleep(30)
        }
        fun snap(name: String) {
            settle()
            val image = scene.render(scheduler.currentTime * 1_000_000L)
            try {
                val png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
                try { File(output, "$name.png").writeBytes(png.bytes) } finally { png.close() }
            } finally { image.close() }
        }
        fun nodes(): List<SemanticsNode> {
            fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
            return scene.semanticsOwners.flatMap { all(it.unmergedRootSemanticsNode) }
        }
        fun label(n: SemanticsNode): String =
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } +
                n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString() +
                n.children.joinToString { label(it) }
        fun visibleText(n: SemanticsNode): String =
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } + n.children.joinToString("") { visibleText(it) }
        // Clicks the innermost clickable whose text matches; logs instead of failing so every page still gets captured.
        // inContent = skip the app's own navigation bars (top bar on desktop, bottom bar on phone).
        fun click(text: String, exact: Boolean = false, inContent: Boolean = false): Boolean {
            val node = nodes().lastOrNull { node ->
                val matches = if (exact) visibleText(node).trim() == text || label(node) == text else label(node).contains(text)
                val box = node.boundsInRoot
                val inBars = inContent && (box.bottom <= 72f || box.top >= height - 90f)
                matches && !inBars && node.config.getOrNull(SemanticsActions.OnClick)?.action != null
            }
            if (node == null) { println("[$width x $height] nothing clickable with '$text'"); return false }
            node.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()
            scheduler.runCurrent(); settle(4)
            return true
        }

        try {
            settle(3); snap("00-tables-loading")
            settle(25); snap("01-tables")
            click("Orders", exact = true); snap("02a-orders-loading"); settle(25); snap("02-orders")
            click("#1042"); snap("03-order-details")
            click("Reservations", exact = true); snap("04a-reservations-loading"); settle(45); snap("04-reservations-overview")
            click("Aria Shah"); snap("05-reservation-panel")
            click("Tables", exact = true, inContent = true); snap("06-reservation-panel-tables")
            click("Notes", exact = true, inContent = true); snap("06b-reservation-panel-notes")
            click("Info", exact = true, inContent = true)
            click("Edit info"); snap("06c-reservation-edit")
            click("Back to details"); click("Close", exact = true)
            click("Calendar", exact = true); snap("07-calendar-timeline")
            click("Back to overview"); click("Tables", exact = true); click("Reservations", exact = true)
            click("Calendar", exact = true); snap("07a-calendar-loading"); settle(25); snap("07-calendar-timeline")
            click("Timeline"); click("Map", exact = true); snap("08-calendar-map")
            click("Map"); click("Timeline", exact = true)
            click("Back to overview") || click("Overview"); snap("09-back-to-overview")
            click("Reservation", exact = true); snap("10-create-when")
            click("Next", exact = true); snap("11-create-table")
            click("Next", exact = true); snap("11b-create-guest")
            click("Cancel", exact = true)
            click("Menu", exact = true); snap("12-menu")
            click("OPEN MENU"); snap("13-menu-items")
            click("Orders", exact = true); click("New order"); snap("14-new-order")
            click("Notifications"); snap("15-notifications")
            println("[$width x $height] repository calls without sample data: $missing")
        } finally {
            scene.close()
            stopKoin()
            Dispatchers.resetMain()
        }
    }

    // ---------------- sample data ----------------

    private val now: Instant = Clock.System.now()
    private fun at(offset: Duration) = (now + offset).toString()

    private val layoutTables: List<LayoutTable> = listOf(
        table("t01", "T01", 4, 0.65f, 0.33f, LayoutTableShape.ROUND, LayoutTableStatus.OCCUPIED, 0.45f, guests = 3, order = "#1042"),
        table("t02", "T02", 4, 0.73f, 0.43f, LayoutTableShape.ROUND, LayoutTableStatus.AVAILABLE, 0.43f),
        table("t03", "T03", 4, 0.73f, 0.34f, LayoutTableShape.ROUND, LayoutTableStatus.AVAILABLE, 0.42f),
        table("t04", "T04", 4, 0.66f, 0.43f, LayoutTableShape.ROUND, LayoutTableStatus.AVAILABLE, 0.42f),
        table("t05", "T05", 8, 0.73f, 0.14f, LayoutTableShape.RECTANGLE, LayoutTableStatus.AVAILABLE, 0.38f, rotation = 90f),
        table("t06", "T06", 4, 0.46f, 0.50f, LayoutTableShape.ROUND, LayoutTableStatus.AVAILABLE, 0.41f),
        table("t07", "T07", 4, 0.53f, 0.47f, LayoutTableShape.ROUND, LayoutTableStatus.OCCUPIED, 0.43f, guests = 5, order = "#1041")
    )

    private fun table(
        id: String, number: String, seats: Int, x: Float, y: Float, shape: LayoutTableShape,
        status: LayoutTableStatus, scale: Float, rotation: Float = 0f, guests: Int? = null, order: String? = null
    ) = LayoutTable(
        id = id, mergedIntoTableId = null, mergedTableIds = emptyList(), tableNumber = number, name = number,
        capacity = seats, effectiveCapacity = seats, floor = "1st Floor", positionX = x, positionY = y,
        rotationDegrees = rotation, scale = scale, shape = shape, status = status, guestCount = guests,
        seatedAt = if (guests != null) at((-40).minutes) else null,
        currentOrderId = order?.let { "order-${it.drop(1)}" }, currentOrderNumber = order,
        currentOrderStatus = order?.let { "OPEN" }, currentOrderFulfillmentStatus = order?.let { "IN_PREPARATION" },
        active = true
    )

    private val planImage: ByteArray by lazy {
        File("../../back-end/uploads/floor-plans").walkTopDown().firstOrNull { it.name.startsWith("5528e029") }?.readBytes()
            ?: File("../uploads/floor-plans").walkTopDown().firstOrNull { it.isFile }?.readBytes()
            ?: ByteArray(0)
    }

    private fun tableRepository(missing: MutableSet<String>): TableLayoutRepository = proxy(missing) { name, args ->
        when (name) {
            "getLayoutChanges" -> emptyFlow<com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutChange>()
            "getFloorLayouts" -> listOf(FloorLayout("floor-1", restaurantId, branchId, "1st Floor", "plan-1", "plan://1st-floor", 0f, 72f, 1.02f))
            "downloadPlanImage" -> planImage
            "getTableLayout" -> BranchTableLayout(restaurantId, branchId, listOf(FloorSummary("1st Floor", layoutTables.size, layoutTables.size)), layoutTables)
            "getTableSections" -> listOf(
                TableSection("s1", "WINDOW", "Window", 0, setOf("t01", "t03")),
                TableSection("s2", "TERRACE", "Terrace", 1, setOf("t02", "t04", "t06", "t07"))
            )
            "getFreeTableIds" -> setOf("t02", "t03", "t04", "t05", "t06")
            else -> NOT_HANDLED
        }
    }

    private val guests = listOf(
        "Sofia Rossi", "James Carter", "Emma Wilson", "Lucas Moretti", "Aria Shah", "Noah Becker",
        "Mia Schneider", "Ethan Clark", "Chloe Martin", "Hugo Laurent", "Ivy Chen", "Ben Thompson"
    )

    private val reservationList: List<Reservation> by lazy {
        val plan = listOf(
            Triple(-5.hours, ReservationStatus.COMPLETED, "t05"),
            Triple(-3.hours, ReservationStatus.COMPLETED, "t02"),
            Triple(-1.hours, ReservationStatus.SEATED, "t07"),
            Triple((-20).minutes, ReservationStatus.CHECKED_IN, "t01"),
            Triple(30.minutes, ReservationStatus.CONFIRMED, "t03"),
            Triple(50.minutes, ReservationStatus.CONFIRMED, "t04"),
            Triple(75.minutes, ReservationStatus.PENDING, null),
            Triple(100.minutes, ReservationStatus.CONFIRMED, "t06"),
            Triple(2.hours, ReservationStatus.CANCELLED, "t02"),
            Triple(26.hours, ReservationStatus.CONFIRMED, "t05"),
            Triple(27.hours, ReservationStatus.PENDING, "t03"),
            Triple(50.hours, ReservationStatus.CONFIRMED, "t01")
        )
        plan.mapIndexed { index, (offset, status, tableId) ->
            val table = layoutTables.firstOrNull { it.id == tableId }
            Reservation(
                id = "res-$index", restaurantId = restaurantId, branchId = branchId,
                reservationCode = "R-${1200 + index}", status = status, partySize = 2 + index % 4,
                reservationStart = at(offset), reservationEnd = at(offset + 2.hours),
                contactName = guests[index % guests.size], contactPhone = "+355 69 123 45${index}0",
                contactEmail = "${guests[index % guests.size].substringBefore(' ').lowercase()}@example.com",
                specialRequests = if (index % 3 == 0) "Window seat · Birthday" else null,
                tableAssignments = listOfNotNull(table?.let {
                    ReservationTableAssignment("a-$index", it.id, it.tableNumber, it.name, it.floor, it.capacity, primary = true)
                })
            )
        }
    }

    private fun reservationRepository(missing: MutableSet<String>): ReservationRepository = proxy(missing) { name, args ->
        fun inRange(from: Any?, to: Any?) = reservationList.filter { r ->
            val start = Instant.parse(r.reservationStart)
            (from == null || start >= Instant.parse(from as String)) && (to == null || start < Instant.parse(to as String))
        }
        when (name) {
            "getReservationSettings" -> ReservationSettings("Europe/Tirane", ReservationRules.NONE)
            "getBranchReservationCalendar" -> inRange(args[2], args[3])
            "getBranchReservations" -> inRange(args[2], args[3])
            "getTodayReservations" -> reservationList
            "getUpcomingReservations" -> reservationList.filter { Instant.parse(it.reservationStart) > now }
            "getReservation" -> reservationList.first { it.id == args[1] }
            "getAudit" -> ReservationAudit(args[1] as String, notes = listOf(ReservationNote("n1", "Prefers the quiet corner", createdByName = "David Keci", createdAt = at((-2).hours))))
            "getStatusHistory" -> listOf(ReservationStatusHistory("h1", null, ReservationStatus.PENDING, changedAt = at((-30).hours)), ReservationStatusHistory("h2", ReservationStatus.PENDING, ReservationStatus.CONFIRMED, changedAt = at((-20).hours)))
            "getTimeline" -> emptyList<ReservationTimelineEvent>()
            "getDeposit" -> ReservationDeposit(args[1] as String, false)
            "getReservationSummary" -> ReservationSummary(branchId)
            "getReservationCapacity" -> ReservationCapacity(branchId, totalRootTables = 7, availableRootTables = 4, totalSeats = 32, availableSeats = 20, maxSingleTableCapacity = 8, maxAvailableTableCapacity = 8)
            "recommendAvailability", "searchAvailability" -> listOf(
                ReservationAvailabilityOption(listOf("t02"), listOf("T02"), "t02", 1, 4, true),
                ReservationAvailabilityOption(listOf("t06"), listOf("T06"), "t06", 1, 4, true),
                ReservationAvailabilityOption(listOf("t05"), listOf("T05"), "t05", 1, 8, false)
            )
            else -> NOT_HANDLED
        }
    }

    private fun orderRepository(missing: MutableSet<String>): OrderRepository = proxy(missing) { name, args ->
        val names = listOf("Emma Wilson", "James Carter", "Sophie Miller")
        when (name) {
            "getOpenOrders" -> (0..5).map { i ->
                summary("order-${1042 - i}").copy(restaurantId = restaurantId,
                    orderNumber = "#${1042 - i}", tableNumber = "T0${7 - i}", guestCount = 3, customerName = names[i % 3],
                    total = OrderDecimal("42.50"),
                    fulfillmentStatus = if (i % 2 == 0) OrderFulfillmentStatus.IN_PREPARATION else OrderFulfillmentStatus.READY
                )
            }
            "getOrder" -> order(args[1] as String).copy(restaurantId = restaurantId,
                orderNumber = "#" + (args[1] as String).removePrefix("order-"), tableNumber = "T01", customerName = "Emma Wilson",
                guestCount = 3, subtotal = OrderDecimal("42.50"), total = OrderDecimal("42.50"),
                fulfillmentStatus = OrderFulfillmentStatus.IN_PREPARATION,
                lineItems = listOf(OrderLineItem("line-1", "item-1", itemNameSnapshot = "Margherita pizza", quantity = 2,
                    unitPriceSnapshot = OrderDecimal("12.50"), priceDeltaTotal = OrderDecimal.ZERO, discountTotal = OrderDecimal.ZERO,
                    taxTotal = OrderDecimal.ZERO, lineTotal = OrderDecimal("25.00"), status = OrderLineItemStatus.PENDING,
                    createdAt = at((-30).minutes), updatedAt = at((-30).minutes)))
            )
            else -> NOT_HANDLED
        }
    }

    private fun catalogRepository(): OrderCatalogRepository {
        val item = OrderCatalogItem("item-1", "Margherita pizza", OrderDecimal("12.50"), true, description = "Tomato, mozzarella and fresh basil")
        val menu = OrderCatalogMenu("menu-1", "All Day", true, OrderCatalogRestaurant(restaurantId), sections = listOf(OrderCatalogSection("section-1", "Mains", true, items = listOf(item))))
        return object : OrderCatalogRepository {
            override suspend fun getMenus(restaurantId: String, page: Int, size: Int) = OrderCatalogPage(listOf(menu), 0, 50, 1, 1, false, false)
            override suspend fun getMenu(restaurantId: String, menuId: String) = menu
            override suspend fun getItemChoices(restaurantId: String, menuId: String, itemId: String) = OrderItemChoices(item, emptyList())
        }
    }

    private val menuList: List<Menu> by lazy {
        val sections = listOf("Starters", "Main dishes", "Pasta", "Desserts", "Drinks").mapIndexed { index, name ->
            MenuSection("section-$index", name, null, true, index, (0..2).map { itemIndex ->
                MenuItem("item-$index-$itemIndex", "SKU-$index-$itemIndex",
                    if (itemIndex == 0) "Roasted vegetables with fresh herbs" else "$name favourite ${itemIndex + 1}",
                    "Freshly prepared with seasonal ingredients.", 12.5 + itemIndex, null, itemIndex != 2, itemIndex,
                    listOf("Tomato", "Basil", "Olive oil"), emptyList(), emptyList())
            })
        }
        listOf("Breakfast", "Lunch", "Dinner", "Drinks").mapIndexed { index, title ->
            Menu("menu-$index", MenuRestaurant(restaurantId, "REVIEW", "Review Restaurant"), "MENU_$index", title,
                "Seasonal dishes", true, index, null, null, null, null, listOf("#D6C394", "#A5B58C", "#9DBCCC")[index % 3], 15,
                null, null, null, null, sections)
        }
    }

    private fun menuRepository(missing: MutableSet<String>): MenuRepository = proxy(missing) { name, args ->
        when (name) {
            "getMenus" -> MenuPage(menuList, 0, 20, menuList.size.toLong(), 1, false, false)
            "getMenu" -> menuList.first { it.id == args[0] }
            else -> NOT_HANDLED
        }
    }

    // Sample notifications: two unread, one read; the live stream is simply unavailable here.
    private fun notificationClient(): io.ktor.client.HttpClient {
        val list = """{"items":[
          {"id":"n1","eventCode":"RESERVATION_CREATED","subject":"New online reservation","body":"Sofia Rossi · 4 guests · Sat 26 Sep, 20:30 · T05","referenceType":"RESERVATION","referenceId":"res-0","createdAt":"${at((-4).minutes)}"},
          {"id":"n2","eventCode":"RESERVATION_CANCELLED","subject":"Reservation cancelled","body":"James Carter · 2 guests · Sat 26 Sep, 21:00 · T02","referenceType":"RESERVATION","referenceId":"res-1","createdAt":"${at((-38).minutes)}"},
          {"id":"n3","eventCode":"RESERVATION_CREATED","subject":"New reservation","body":"Mia Schneider · 6 guests · Sun 27 Sep, 19:00 · T01 + T03","referenceType":"RESERVATION","referenceId":"res-2","readAt":"${at((-1).hours)}","createdAt":"${at((-3).hours)}"}
        ],"totalElements":3}"""
        return io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { request ->
            val url = request.url.toString()
            val headers = io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json")
            when {
                "/stream" in url -> respond("", io.ktor.http.HttpStatusCode.NotFound)
                "unreadOnly=true" in url -> respond("""{"items":[],"totalElements":2}""", io.ktor.http.HttpStatusCode.OK, headers)
                else -> respond(list, io.ktor.http.HttpStatusCode.OK, headers)
            }
        }) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
            }
            install(io.ktor.client.plugins.sse.SSE)
            install(io.ktor.client.plugins.HttpTimeout)
        }
    }

    private object NotHandled
    private val NOT_HANDLED: Any = NotHandled

    // Answers repository calls from sample data. Suspend functions get the value back directly.
    private inline fun <reified T : Any> proxy(missing: MutableSet<String>, crossinline answer: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, rawArgs ->
            val args = rawArgs ?: emptyArray()
            when (method.name) {
                "toString" -> "Sample${T::class.simpleName}"
                "hashCode" -> 0
                "equals" -> false
                else -> {
                    val result = answer(method.name, args)
                    if (result === NOT_HANDLED) {
                        missing += "${T::class.simpleName}.${method.name}"
                        throw IllegalStateException("No sample data for ${method.name}")
                    }
                    result ?: Unit
                }
            }
        } as T
}
