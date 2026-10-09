@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.saporini.mobile_desktop.poscompletion

import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.orders.*
import com.saporini.mobile_desktop.pos.history.*
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.ui.OrdersScope
import com.saporini.mobile_desktop.pos.orders.ui.OrderListFilter
import com.saporini.mobile_desktop.pos.orders.ui.OrderListMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Instant

private class HistoryFake : OrderHistoryRepository {
    val calls = mutableListOf<Pair<Int,HistoryFilter>>()
    var rows = (0..8).map { summary("o$it").copy(status = OrderStatus.CLOSED) }
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    var detailGate: CompletableDeferred<Unit>? = null
    val events = MutableSharedFlow<Unit>(extraBufferCapacity=10)
    override suspend fun page(scope: OrdersScope, filter: HistoryFilter, page: Int, size: Int): HistoryPage {
        calls += page to filter
        val snapshot = rows.map { it.copy(branchId = scope.branchId) }
        gate?.let { withContext(NonCancellable) { it.await() } }; failure?.let { throw it }
        return HistoryPage(snapshot.drop(page*size).take(size),page,size,snapshot.size.toLong(),(page+1)*size<snapshot.size)
    }
    override suspend fun detail(scope: OrdersScope, id: String): Order { detailGate?.let { withContext(NonCancellable) { it.await() } }; return order(id,scope.branchId).copy(status=OrderStatus.CLOSED) }
    override fun changes(scope: OrdersScope): Flow<Unit> = events
}
class PosHistoryStateTest {
    private val dispatcher=StandardTestDispatcher()
    @BeforeTest fun before() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun after() { Dispatchers.resetMain() }
    private suspend fun TestScope.check(repo: HistoryFake=HistoryFake(), permissions: List<String> = listOf("ORDER_READ"), block: suspend TestScope.(OrderHistoryScreenModel,SessionManager)->Unit) {
        val session=SessionManager().apply { signIn(user(permissions=permissions)) }; val model=OrderHistoryScreenModel(repo,session,pageSize=3)
        try { runCurrent(); block(model,session) } finally { model.onDispose(); runCurrent() }
    }
    @Test fun refreshRetainsEveryLoadedPageAndTotal() = runTest(dispatcher) {
        val repo=HistoryFake(); check(repo) { m,_ -> m.setActive(true);runCurrent();m.loadMore();runCurrent();m.loadMore();runCurrent()
            assertEquals(9,m.state.value.items.size);assertFalse(m.state.value.hasNext);repo.calls.clear();m.refresh();runCurrent()
            assertEquals(listOf(0,1,2),repo.calls.map { it.first });assertEquals(9L,m.state.value.totalElements);assertEquals(9,m.state.value.items.size)
        }
    }
    @Test fun failedRefreshPreservesLoadedRows() = runTest(dispatcher) {
        val repo=HistoryFake();check(repo) { m,_ -> m.setActive(true);runCurrent();m.loadMore();runCurrent();repo.failure=Exception("offline");m.refresh();runCurrent()
            assertEquals(6,m.state.value.items.size);assertTrue(m.state.value.stale);assertNotNull(m.state.value.error)
            repo.failure=null;m.refresh();runCurrent();assertNull(m.state.value.error);assertEquals(6,m.state.value.items.size)
        }
    }
    @Test fun filteredSearchIsServerSideAndResetsPaging() = runTest(dispatcher) {
        val repo=HistoryFake();check(repo) { m,_ -> m.setActive(true);runCurrent();m.loadMore();runCurrent();m.setFilter(HistoryFilter(search="Soup",staffId="waiter",status=OrderStatus.CANCELLED));runCurrent()
            assertEquals(1,m.state.value.loadedPages);assertEquals("Soup",repo.calls.last().second.search);assertEquals("waiter",repo.calls.last().second.staffId)
        }
    }
    @Test fun invalidRangeDoesNotMakeRequest() = runTest(dispatcher) {
        val repo=HistoryFake();check(repo) { m,_ -> m.setActive(true);runCurrent();val count=repo.calls.size
            val now=Instant.parse("2026-09-25T12:00:00Z");m.setFilter(HistoryFilter(now,now));runCurrent();assertEquals(count,repo.calls.size);assertNotNull(m.state.value.error)
        }
    }
    @Test fun permissionIsRequiredBeforeLoading() = runTest(dispatcher) { val repo=HistoryFake();check(repo,emptyList()) { m,_ -> m.setActive(true);runCurrent();assertTrue(repo.calls.isEmpty()) } }
    @Test fun forbiddenClearsRowsAndDetails() = runTest(dispatcher) {
        val repo=HistoryFake();check(repo) { m,_ -> m.setActive(true);runCurrent();m.selectOrder("o0");runCurrent();repo.failure=ApiException(403,"Denied");m.refresh();runCurrent()
            assertFalse(m.state.value.canRead);assertTrue(m.state.value.items.isEmpty());assertNull(m.state.value.detail)
        }
    }
    @Test fun lateResponseCannotRestoreSignedOutData() = runTest(dispatcher) {
        val repo=HistoryFake().apply { gate=CompletableDeferred() };check(repo) { m,s -> m.setActive(true);runCurrent();s.signOut();runCurrent();repo.gate!!.complete(Unit);runCurrent()
            assertNull(m.state.value.scope);assertTrue(m.state.value.items.isEmpty())
        }
    }
    @Test fun branchChangeClearsDetailAndLoadsNewBranch() = runTest(dispatcher) {
        check { m,s -> m.setActive(true);runCurrent();m.selectOrder("o0");runCurrent();s.signIn(user(branch="other"));runCurrent()
            assertNull(m.state.value.detail);assertEquals("other",m.state.value.items.first().branchId)
        }
    }
    @Test fun lateDetailCannotReopenDismissedOrder() = runTest(dispatcher) {
        val repo=HistoryFake().apply { detailGate=CompletableDeferred() };check(repo) { m,_ -> m.setActive(true);runCurrent();m.selectOrder("o0");runCurrent();m.closeDetails();repo.detailGate!!.complete(Unit);runCurrent();assertNull(m.state.value.detail) }
    }
    @Test fun liveChangeRefreshesAndBackgroundStopsReads() = runTest(dispatcher) {
        val repo=HistoryFake();check(repo) { m,_ -> m.setActive(true);runCurrent();repo.events.emit(Unit);runCurrent();assertEquals(2,repo.calls.size)
            m.setActive(false);runCurrent();advanceTimeBy(90_000);runCurrent();assertEquals(2,repo.calls.size)
        }
    }
    @Test fun existingOrdersLayoutReceivesReadOnlyHistoryAndDateFilters() = runTest(dispatcher) {
        val repo = HistoryFake()
        val session = SessionManager().apply { signIn(user(permissions = listOf("ORDER_READ", "ORDER_UPDATE", "ORDER_CREATE"))) }
        val model = com.saporini.mobile_desktop.pos.orders.ui.OrdersScreenModel(
            unsupportedRepository(), session, historyRepository = repo, historyOnly = true)
        try {
            runCurrent(); model.setActive(true); runCurrent()
            assertEquals(setOf("ORDER_READ"), model.state.value.permissions)
            assertEquals(9, model.state.value.orders.size)
            model.setFilter(OrderListFilter(mode = OrderListMode.HISTORY,
                from = "2026-09-25T00:00:00Z", to = "2026-09-26T00:00:00Z"))
            runCurrent()
            assertEquals("2026-09-25T00:00:00Z", model.state.value.filter.from)
            model.historyMine(true); runCurrent()
            assertEquals("user-1", repo.calls.last().second.staffId)
            model.selectOrder("o0"); runCurrent()
            assertEquals("o0", model.state.value.selectedOrder?.id)
            model.setActive(false)
        } finally { model.onDispose(); runCurrent() }
    }

}
