@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.saporini.mobile_desktop.orders

import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderRepository
import com.saporini.mobile_desktop.pos.orders.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.io.IOException
import kotlin.test.*

class OrdersScreenModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val models = mutableListOf<OrdersScreenModel>()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { models.forEach { it.onDispose() }; Dispatchers.resetMain() }

    private fun model(repo: OrderRepository, session: SessionManager = SessionManager().also { it.signIn(user()) }): OrdersScreenModel =
        OrdersScreenModel(repo, session).also { models += it; scheduler.runCurrent() }

    @Test fun activationPollsOnlyWhileVisible() = runTest(dispatcher) {
        var reads = 0
        val repo = object : OrderRepository by unsupportedRepository() {
            override suspend fun getOrdersPage(restaurantId: String, branchId: String, from: String?, to: String?, status: OrderStatus?,
                customerId: String?, search: String?, historyOnly: Boolean, openOnly: Boolean, page: Int, size: Int): OrderPage {
                reads++
                assertTrue(openOnly)
                return OrderPage(listOf(summary()), page, size, 1, false)
            }
        }
        val model = model(repo)
        assertEquals(0, reads)
        model.setActive(true); runCurrent()
        assertEquals(1, reads)
        assertEquals(1, model.state.value.orders.size)
        advanceTimeBy(15_000); runCurrent()
        assertEquals(2, reads)
        model.setActive(false)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(2, reads)
    }

    @Test fun allOrderPagesLoadAppendRefreshAndSearchWithoutLosingLoadedPages() = runTest(dispatcher) {
        val all = (1..55).map { summary("order-$it") }
        val requests = mutableListOf<Triple<Int, Int, String?>>()
        val repo = object : OrderRepository by unsupportedRepository() {
            override suspend fun getOpenOrders(restaurantId: String, branchId: String) = emptyList<OrderSummary>()
            override suspend fun getOrdersPage(
                restaurantId: String, branchId: String, from: String?, to: String?, status: OrderStatus?,
                customerId: String?, search: String?, historyOnly: Boolean, openOnly: Boolean, page: Int, size: Int
            ): OrderPage {
                requests += Triple(page, size, search)
                val items = all.drop(page * size).take(size)
                return OrderPage(items, page, size, all.size.toLong(), (page + 1) * size < all.size)
            }
        }
        val model = model(repo)
        model.setFilter(OrderListFilter(mode = OrderListMode.ALL)); runCurrent()
        assertEquals(50, model.state.value.orders.size)
        assertEquals(1, model.state.value.historyLoadedPages)
        assertTrue(model.state.value.historyHasNext)

        model.loadMoreHistory(); runCurrent()
        assertEquals(55, model.state.value.orders.size)
        assertEquals(2, model.state.value.historyLoadedPages)
        assertFalse(model.state.value.historyHasNext)

        model.refreshNow()
        assertEquals(55, model.state.value.orders.size)
        assertEquals(listOf(0, 1), requests.takeLast(2).map { it.first })

        model.setSearchQuery("pasta")
        advanceTimeBy(250); runCurrent()
        assertEquals("pasta", requests.last().third)
        assertEquals(1, model.state.value.historyLoadedPages)
    }

    @Test fun openOrdersArePagedSearchedAndRefreshEveryLoadedPage() = runTest(dispatcher) {
        val all = (1..55).map { summary("open-$it") }
        val requests = mutableListOf<Triple<Int, Boolean, String?>>()
        val repo = object : OrderRepository by unsupportedRepository() {
            override suspend fun getOrdersPage(restaurantId: String, branchId: String, from: String?, to: String?, status: OrderStatus?,
                customerId: String?, search: String?, historyOnly: Boolean, openOnly: Boolean, page: Int, size: Int): OrderPage {
                requests += Triple(page, openOnly, search)
                val items = all.drop(page * size).take(size)
                return OrderPage(items, page, size, all.size.toLong(), (page + 1) * size < all.size)
            }
        }
        val model = model(repo)
        model.setActive(true)
        runCurrent()
        assertEquals(50, model.state.value.orders.size)
        assertTrue(requests.last().second)
        assertTrue(model.state.value.historyHasNext)
        model.loadMoreHistory(); runCurrent()
        assertEquals(55, model.state.value.orders.size)
        model.refreshNow()
        assertEquals(listOf(0, 1), requests.takeLast(2).map { it.first })
        model.setSearchQuery("open-4"); advanceTimeBy(250); runCurrent()
        assertEquals("open-4", requests.last().third)
        assertTrue(requests.last().second)
        assertEquals(1, model.state.value.historyLoadedPages)
        model.setActive(false)
    }

    @Test fun confirmedWriteRemainsSuccessfulIfRefreshFails() = runTest(dispatcher) {
        var writes = 0
        val repo = object : OrderRepository by unsupportedRepository() {
            override suspend fun createOrder(restaurantId: String, branchId: String, request: CreateOrderInput): Order { writes++; return order() }
            override suspend fun getOpenOrders(restaurantId: String, branchId: String): List<OrderSummary> = throw IOException("offline")
        }
        val model = model(repo)
        val result = model.operations.createOrder(CreateOrderInput())
        assertTrue(result.isSuccess)
        assertEquals(1, writes)
        assertEquals("order-1", model.state.value.selectedOrder?.id)
        assertNotNull(model.state.value.refreshWarning)
        assertFalse(model.state.value.needsReconciliation)
        assertFalse(model.state.value.isSaving)
    }

    @Test fun uncertainWriteBlocksRepeatsUntilExplicitlyReconciled() = runTest(dispatcher) {
        var writes = 0
        val repo = object : OrderRepository by unsupportedRepository() {
            override suspend fun createOrder(restaurantId: String, branchId: String, request: CreateOrderInput): Order { writes++; throw IOException("lost response") }
            override suspend fun getOrdersPage(restaurantId: String, branchId: String, from: String?, to: String?, status: OrderStatus?,
                customerId: String?, search: String?, historyOnly: Boolean, openOnly: Boolean, page: Int, size: Int) =
                OrderPage(listOf(summary()), page, size, 1, false)
        }
        val model = model(repo)
        assertTrue(model.operations.createOrder(CreateOrderInput()).isFailure)
        assertTrue(model.state.value.needsReconciliation)
        assertTrue(model.operations.createOrder(CreateOrderInput()).isFailure)
        assertEquals(1, writes)
        assertTrue(model.refreshNow())
        assertTrue(model.state.value.needsReconciliation)
        model.acknowledgeReconciliation()
        assertFalse(model.state.value.needsReconciliation)
    }

    @Test fun concurrentSubmissionsDoNotSendTwoWrites() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val repo = object : OrderRepository by unsupportedRepository() {
            override suspend fun createOrder(restaurantId: String, branchId: String, request: CreateOrderInput): Order { writes++; gate.await(); return order() }
            override suspend fun getOrdersPage(restaurantId: String, branchId: String, from: String?, to: String?, status: OrderStatus?,
                customerId: String?, search: String?, historyOnly: Boolean, openOnly: Boolean, page: Int, size: Int) =
                OrderPage(listOf(summary()), page, size, 1, false)
            override suspend fun getOrder(restaurantId: String, orderId: String) = order()
        }
        val model = model(repo)
        val first = async { model.operations.createOrder(CreateOrderInput()) }
        runCurrent()
        assertTrue(model.operations.createOrder(CreateOrderInput()).isFailure)
        assertEquals(1, writes)
        gate.complete(Unit)
        assertTrue(first.await().isSuccess)
    }

    @Test fun cancelledWritePropagatesCancellationAndRequiresReconciliation() = runTest(dispatcher) {
        val repo = object : OrderRepository by unsupportedRepository() {
            override suspend fun createOrder(restaurantId: String, branchId: String, request: CreateOrderInput): Order = awaitCancellation()
        }
        val model = model(repo)
        val job = launch { model.operations.createOrder(CreateOrderInput()) }
        runCurrent(); job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(model.state.value.needsReconciliation)
        assertFalse(model.state.value.isSaving)
    }

    @Test fun switchingUsersDiscardsInFlightResultsAndCachedOrders() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val session = SessionManager().also { it.signIn(user()) }
        val repo = object : OrderRepository by unsupportedRepository() {
            override suspend fun getOrdersPage(restaurantId: String, branchId: String, from: String?, to: String?, status: OrderStatus?,
                customerId: String?, search: String?, historyOnly: Boolean, openOnly: Boolean, page: Int, size: Int): OrderPage {
                gate.await()
                return OrderPage(listOf(summary()), page, size, 1, false)
            }
        }
        val model = model(repo, session)
        val loading = async { model.refreshNow() }
        runCurrent()
        session.signIn(user(id = "other-user", branch = "other-branch")); runCurrent()
        gate.complete(Unit); loading.await()
        assertTrue(model.state.value.orders.isEmpty())
        assertEquals("other-branch", model.state.value.scope?.branchId)
    }

    @Test fun staleDetailCannotReplaceNewSelection() = runTest(dispatcher) {
        val first = CompletableDeferred<Unit>()
        val repo = object : OrderRepository by unsupportedRepository() {
            override suspend fun getOrder(restaurantId: String, orderId: String): Order {
                if (orderId == "first") withContext(NonCancellable) { first.await() }
                return order(orderId)
            }
        }
        val model = model(repo)
        model.selectOrder("first"); runCurrent()
        model.selectOrder("second"); runCurrent()
        assertEquals("second", model.state.value.selectedOrder?.id)
        first.complete(Unit); runCurrent()
        assertEquals("second", model.state.value.selectedOrder?.id)
    }

    @Test fun missingPermissionPreventsWrite() = runTest(dispatcher) {
        val session = SessionManager().also { it.signIn(user(permissions = listOf("ORDER_READ"))) }
        val model = model(unsupportedRepository(), session)
        val result = model.operations.createOrder(CreateOrderInput())
        assertTrue(result.isFailure)
        assertEquals(OrderErrorKind.PERMISSION, model.state.value.error?.kind)
        assertFalse(model.state.value.needsReconciliation)
    }
}
