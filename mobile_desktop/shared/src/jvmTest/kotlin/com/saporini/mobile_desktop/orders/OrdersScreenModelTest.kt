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
            override suspend fun getOpenOrders(restaurantId: String, branchId: String): List<OrderSummary> { reads++; return listOf(summary()) }
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
            override suspend fun getOpenOrders(restaurantId: String, branchId: String) = listOf(summary())
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
            override suspend fun getOpenOrders(restaurantId: String, branchId: String) = listOf(summary())
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
            override suspend fun getOpenOrders(restaurantId: String, branchId: String): List<OrderSummary> { gate.await(); return listOf(summary()) }
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
