@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.saporini.mobile_desktop.poscompletion

import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.orders.user
import com.saporini.mobile_desktop.pos.orders.ui.OrdersScope
import com.saporini.mobile_desktop.pos.sales.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

internal fun salesReport(scope: OrdersScope=OrdersScope("user-1","restaurant-1","branch-1"),filter: SalesFilter=SalesFilter()) = SalesReport(scope.restaurantId,scope.branchId,filter.staffId ?: scope.userId,"Test Waiter","Europe/Berlin",filter.date?.toString() ?: "2026-09-25","2026-09-24T22:00:00Z","2026-09-25T22:00:00Z",filter.shiftId,"2026-09-25T12:00:00Z",currencies=listOf(SalesTotals("EUR"),SalesTotals("USD")))
private class SalesFake : MySalesRepository {
    var calls=0;var failure:Exception?=null;var gate:CompletableDeferred<Unit>?=null;var wrongBranch=false
    val events=MutableSharedFlow<Unit>(extraBufferCapacity=10)
    override suspend fun report(scope: OrdersScope,filter: SalesFilter):SalesReport {
        calls++;gate?.let { withContext(NonCancellable) { it.await() } };failure?.let { throw it }
        return salesReport(if(wrongBranch) scope.copy(branchId="wrong") else scope,filter)
    }
    override fun changes(scope:OrdersScope):Flow<Unit> = events
}
class MySalesStateTest {
    private val dispatcher=StandardTestDispatcher()
    @BeforeTest fun before(){Dispatchers.setMain(dispatcher)}
    @AfterTest fun after(){Dispatchers.resetMain()}
    private suspend fun TestScope.check(repo:SalesFake=SalesFake(),permissions:List<String> = listOf("ORDER_READ"),block:suspend TestScope.(MySalesScreenModel,SessionManager)->Unit) {
        val session=SessionManager().apply { signIn(user(permissions=permissions)) };val m=MySalesScreenModel(repo,session)
        try{runCurrent();block(m,session)}finally{m.onDispose();runCurrent()}
    }
    @Test fun noPermissionMeansNoNetwork()=runTest(dispatcher){val repo=SalesFake();check(repo,emptyList()){m,_->m.setActive(true);runCurrent();assertEquals(0,repo.calls)}}
    @Test fun ownStaffOnlyUnlessAuditAndShiftReadGranted()=runTest(dispatcher){val repo=SalesFake();check(repo){m,_->m.setActive(true);runCurrent();m.staff("other");runCurrent();assertEquals(1,repo.calls);assertNotNull(m.state.value.error)}}
    @Test fun managerCanSelectStaffAndDateClearsShift()=runTest(dispatcher){check(permissions=listOf("ORDER_READ","ORDER_AUDIT","SHIFT_READ")){m,_->m.setActive(true);runCurrent();m.staff("other");m.shift("shift");runCurrent();assertEquals("other",m.state.value.report?.staffId);m.date(LocalDate(2026,9,24));runCurrent();assertNull(m.state.value.report?.shiftId);assertEquals("2026-09-24",m.state.value.report?.date)}}
    @Test fun currenciesNeverAddedTogether()=runTest(dispatcher){check{m,_->m.setActive(true);runCurrent();assertEquals("EUR",m.state.value.totals?.currency);m.currency("USD");assertEquals("USD",m.state.value.totals?.currency);m.currency("INVALID");assertEquals("USD",m.state.value.totals?.currency)}}
    @Test fun networkFailureRetainsStaleReportButForbiddenClearsIt()=runTest(dispatcher){val repo=SalesFake();check(repo){m,_->m.setActive(true);runCurrent();repo.failure=Exception("offline");m.refresh();runCurrent();assertNotNull(m.state.value.report);assertTrue(m.state.value.stale);repo.failure=ApiException(403,"Denied");m.refresh();runCurrent();assertNull(m.state.value.report);assertFalse(m.state.value.canRead)}}
    @Test fun lateResponseCannotRestoreSignedOutReport()=runTest(dispatcher){val repo=SalesFake().apply{gate=CompletableDeferred()};check(repo){m,s->m.setActive(true);runCurrent();s.signOut();runCurrent();repo.gate!!.complete(Unit);runCurrent();assertNull(m.state.value.report);assertNull(m.state.value.scope)}}
    @Test fun unexpectedBranchResponseRejected()=runTest(dispatcher){val repo=SalesFake().apply{wrongBranch=true};check(repo){m,_->m.setActive(true);runCurrent();assertNull(m.state.value.report);assertNotNull(m.state.value.error)}}
    @Test fun liveChangesAndPeriodicReconciliationStopInBackground()=runTest(dispatcher){val repo=SalesFake();check(repo){m,_->m.setActive(true);runCurrent();repo.events.emit(Unit);runCurrent();advanceTimeBy(30_000);runCurrent();assertEquals(3,repo.calls);m.setActive(false);runCurrent();advanceTimeBy(90_000);runCurrent();assertEquals(3,repo.calls)}}
}
