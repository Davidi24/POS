@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop.reservations

import com.saporini.mobile_desktop.core.network.ApiErrorResponse
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.orders.user
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.reservations.preorder.MAX_DISH_QUANTITY
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderApi
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderItemDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderLine
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderOptionRequestDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderRepository
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderRequestDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderScreenModel
import com.saporini.mobile_desktop.pos.reservations.preorder.preOrderProblem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val DAY = LocalDate(2026, 10, 6)

private fun preOrder(status: String = "SCHEDULED", items: List<PreOrderItemDto> = listOf(PreOrderItemDto("i1", "pizza", itemName = "Margherita", quantity = 2))) =
    PreOrderDto("po1", "branch-1", "res-1", status = status, total = OrderDecimal("18.00"), reservationStart = "2026-10-07T19:00:00Z", items = items)

private class FakePreOrders : PreOrderRepository {
    var current: PreOrderDto? = preOrder()
    val saved = mutableListOf<PreOrderRequestDto>()
    val calls = mutableListOf<String>()
    var failure: Exception? = null

    override suspend fun forBooking(restaurantId: String, reservationId: String): PreOrderDto? {
        calls += "get $reservationId"; return current?.takeIf { reservationId == "res-1" }
    }

    override suspend fun save(restaurantId: String, reservationId: String, request: PreOrderRequestDto): PreOrderDto {
        failure?.let { throw it }
        saved += request
        return preOrder(items = request.items.map { PreOrderItemDto(menuItemId = it.menuItemId, quantity = it.quantity) }).also { current = it }
    }

    override suspend fun cancel(restaurantId: String, reservationId: String, reason: String?): PreOrderDto {
        calls += "cancel $reason"
        return preOrder("CANCELLED").also { current = it }
    }

    override suspend fun sendNow(restaurantId: String, reservationId: String): PreOrderDto {
        failure?.let { throw it }
        calls += "send"
        return preOrder("SENT").also { current = it }
    }

    override suspend fun forBranch(restaurantId: String, branchId: String, from: String, to: String, status: String?): List<PreOrderDto> {
        calls += "list $from $to $status"
        return listOf(preOrder())
    }
}

class PreOrderRulesTest {
    private val line = PreOrderLine("pizza", "Margherita")

    @Test
    fun theServersLimitsAreCheckedBeforeSending() {
        assertEquals("Add at least one dish", preOrderProblem(emptyList(), ""))
        assertNull(preOrderProblem(List(50) { line }, "n".repeat(500)))
        assertEquals("At most 50 dishes", preOrderProblem(List(51) { line }, ""))
        assertEquals("Each dish can be ordered 1 to 50 times", preOrderProblem(listOf(line.copy(quantity = 51)), ""))
        assertEquals("Dish notes can be at most 200 characters", preOrderProblem(listOf(line.copy(notes = "x".repeat(201))), ""))
        assertEquals("At most 30 choices per dish", preOrderProblem(listOf(line.copy(options = List(31) { PreOrderOptionRequestDto("o$it", 1) })), ""))
        assertEquals("Each choice can be taken 1 to 20 times", preOrderProblem(listOf(line.copy(options = listOf(PreOrderOptionRequestDto("o", 21)))), ""))
        assertEquals("Notes can be at most 500 characters", preOrderProblem(listOf(line), "n".repeat(501)))
    }
}

class PreOrderScreenModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)
    @AfterTest fun after() = Dispatchers.resetMain()

    private suspend fun TestScope.check(
        repo: FakePreOrders = FakePreOrders(),
        permissions: List<String> = listOf("ORDER_READ", "ORDER_CREATE", "ORDER_CANCEL", "ORDER_UPDATE"),
        block: suspend TestScope.(PreOrderScreenModel, FakePreOrders) -> Unit
    ) {
        val session = SessionManager().apply { signIn(user(permissions = permissions)) }
        val model = PreOrderScreenModel(repo, session, today = { DAY }, zone = { TimeZone.of("Europe/Rome") })
        try {
            runCurrent(); block(model, repo)
        } finally {
            model.onDispose(); runCurrent()
        }
    }

    @Test
    fun opensABookingsPreOrderOrNone() = runTest(dispatcher) {
        check { model, _ ->
            model.open("res-1"); runCurrent()
            assertEquals("po1", model.state.value.preOrder?.id)
            model.open("res-2"); runCurrent()
            assertNull(model.state.value.preOrder)
            assertNull(model.state.value.error)
        }
    }

    @Test
    fun takingAPreOrderAddsCountsAndRemovesDishes() = runTest(dispatcher) {
        val repo = FakePreOrders().apply { current = null }
        check(repo) { model, _ ->
            model.open("res-1"); runCurrent()
            model.addDish("pizza", "Margherita")
            assertTrue(model.state.value.lines.isEmpty())
            model.startEdit()
            model.addDish("pizza", "Margherita")
            model.addDish("pizza", "Margherita")
            model.addDish("pizza", "Margherita", options = listOf(PreOrderOptionRequestDto("extra-cheese", 1)))
            model.addDish("tiramisu", "Tiramisù")
            assertEquals(listOf(2, 1, 1), model.state.value.lines.map { it.quantity })
            assertEquals(4, model.state.value.dishCount)
            model.quantity(7, 5)
            model.quantity(2, 5)
            model.quantity(1, 0)
            model.quantity(0, 999)
            assertEquals(listOf(MAX_DISH_QUANTITY, 5), model.state.value.lines.map { it.quantity })
            model.dishNotes(0, " no basil ")
            model.notes(" Birthday 🎂 ")
            model.save(); runCurrent()
            val sent = repo.saved.single()
            assertEquals(listOf("pizza", "tiramisu"), sent.items.map { it.menuItemId })
            assertEquals("no basil", sent.items.first().notes)
            assertEquals("Birthday 🎂", sent.notes)
            assertFalse(model.state.value.editing)
            assertEquals("Pre-order taken", model.state.value.notice)
        }
    }

    @Test
    fun changingKeepsWhatWasOrderedAndAnEmptyPreOrderIsRefused() = runTest(dispatcher) {
        check { model, repo ->
            model.open("res-1"); runCurrent()
            model.startEdit()
            assertEquals(listOf(PreOrderLine("pizza", "Margherita", quantity = 2)), model.state.value.lines)
            model.quantity(0, 0)
            model.save(); runCurrent()
            assertEquals("Add at least one dish", model.state.value.error)
            assertTrue(repo.saved.isEmpty())
            model.cancelEdit()
            assertFalse(model.state.value.editing)
        }
    }

    @Test
    fun aSentPreOrderCantChangeOrBeCancelled() = runTest(dispatcher) {
        val repo = FakePreOrders().apply { current = preOrder("SENT") }
        check(repo) { model, _ ->
            model.open("res-1"); runCurrent()
            model.startEdit()
            assertFalse(model.state.value.editing)
            assertEquals("This pre-order is already with the kitchen and can't change", model.state.value.error)
            model.askCancel()
            assertFalse(model.state.value.confirmCancel)
            model.sendNow(); runCurrent()
            assertTrue(repo.calls.none { it == "send" })
        }
    }

    @Test
    fun cancellingAsksFirstAndSendingGoesToTheKitchen() = runTest(dispatcher) {
        check { model, repo ->
            model.open("res-1"); runCurrent()
            model.askCancel()
            assertTrue(model.state.value.confirmCancel)
            model.cancelReason("  Guest is unwell  ")
            model.confirmCancel(); runCurrent()
            assertTrue("cancel Guest is unwell" in repo.calls)
            assertEquals("CANCELLED", model.state.value.preOrder?.status)
            // After a cancel a new pre-order can be taken.
            model.startEdit()
            assertTrue(model.state.value.editing)
            assertTrue(model.state.value.lines.isEmpty())
        }
    }

    @Test
    fun sendingEarlyAndARefusalRereadsThePreOrder() = runTest(dispatcher) {
        check { model, repo ->
            model.open("res-1"); runCurrent()
            repo.failure = ApiException(400, "This pre-order was already sent to the kitchen")
            repo.calls.clear()
            model.sendNow(); runCurrent()
            assertEquals("This pre-order was already sent to the kitchen", model.state.value.error)
            assertTrue("get res-1" in repo.calls)
            repo.failure = null
            model.sendNow(); runCurrent()
            assertEquals("SENT", model.state.value.preOrder?.status)
            assertEquals("Sent to the kitchen", model.state.value.notice)
        }
    }

    @Test
    fun withoutRightsNothingIsWritten() = runTest(dispatcher) {
        check(permissions = listOf("ORDER_READ")) { model, repo ->
            model.open("res-1"); runCurrent()
            model.startEdit()
            assertEquals("You can't take pre-orders", model.state.value.error)
            model.askCancel()
            assertEquals("Only a manager can cancel a pre-order", model.state.value.error)
            model.sendNow(); runCurrent()
            assertEquals("You can't send pre-orders to the kitchen", model.state.value.error)
            assertTrue(repo.saved.isEmpty() && repo.calls.none { it == "send" || it.startsWith("cancel") })
        }
    }

    @Test
    fun theKitchenListCoversWholeRestaurantDaysAndAtMostSixtyTwo() = runTest(dispatcher) {
        check { model, repo ->
            model.loadList(); runCurrent()
            // Rome is UTC+2 in October: the week starts at 22:00 UTC the evening before.
            assertEquals("list 2026-10-05T22:00:00Z 2026-10-12T22:00:00Z SCHEDULED", repo.calls.last())
            assertEquals(1, model.state.value.upcoming.size)
            assertFalse(model.period(DAY, LocalDate(2026, 12, 7)))
            assertEquals("Choose at most 62 days", model.state.value.error)
            assertFalse(model.period(DAY, LocalDate(2026, 10, 5)))
            assertTrue(model.period(DAY, DAY)); runCurrent()
            assertEquals("list 2026-10-05T22:00:00Z 2026-10-06T22:00:00Z SCHEDULED", repo.calls.last())
            model.statusFilter(null); runCurrent()
            assertEquals("list 2026-10-05T22:00:00Z 2026-10-06T22:00:00Z null", repo.calls.last())
        }
    }
}

class PreOrderApiTest {
    private fun client(status: HttpStatusCode, body: String) = HttpClient(MockEngine {
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        // The same error handling as the app's client.
        HttpResponseValidator {
            validateResponse { response ->
                if (!response.status.isSuccess()) throw ApiException(response.status.value, response.body<ApiErrorResponse>().message)
            }
        }
    }

    @Test
    fun noPreOrderIsNullButAMissingBookingIsAnError() = runTest {
        client(HttpStatusCode.NotFound, """{"status":404,"message":"This reservation has no pre-order"}""").use { client ->
            assertNull(PreOrderApi(client) { "http://localhost" }.forBooking("r", "res"))
        }
        client(HttpStatusCode.NotFound, """{"status":404,"message":"Reservation not found"}""").use { client ->
            val error = assertFailsWith<ApiException> { PreOrderApi(client) { "http://localhost" }.forBooking("r", "res") }
            assertEquals(404, error.status)
        }
        client(HttpStatusCode.OK, """{"id":"p","reservationId":"res","status":"SCHEDULED","total":18.50,"items":[{"menuItemId":"m","quantity":2,"lineTotal":18.50}]}""").use { client ->
            val found = PreOrderApi(client) { "http://localhost" }.forBooking("r", "res")
            assertEquals("18.50", found?.total?.value)
            assertTrue(found!!.open)
        }
    }
}
