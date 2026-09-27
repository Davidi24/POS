@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.saporini.mobile_desktop.pos.shifts

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.network.ApiException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.*
import kotlin.test.*
import kotlin.time.Instant

class ShiftScreenModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun finish() { Dispatchers.resetMain() }
    private fun session(permission: List<String> = listOf("SHIFT_SELF")) = SessionManager().apply {
        signIn(CurrentUserResponse("u", "r", "b", "staff@example.invalid", "staff", "Test", "Staff", isActive = true, emailVerified = true, phoneVerified = false, roles = listOf("WAITER"), permissions = permission))
    }
    @Test fun selectingDayWithinWeekDoesNotReloadOrClearCalendar() = runTest(dispatcher) {
        val repo = FakeShifts(); val model = ShiftScreenModel(repo, session()); model.start(false); advanceUntilIdle()
        assertEquals(1,repo.reads); val week = model.state.value.weekStart
        model.date(week); advanceUntilIdle(); assertEquals(1,repo.reads); assertNotNull(model.state.value.board)
        model.date(week.plus(DatePeriod(days=7))); advanceUntilIdle(); assertEquals(2,repo.reads); model.onDispose()
    }
    @Test fun waiterCannotLoadAdminRoster() = runTest(dispatcher) {
        val repo = FakeShifts(); val model = ShiftScreenModel(repo,session()); model.start(true); advanceUntilIdle()
        assertEquals(0,repo.reads); assertNotNull(model.state.value.error); model.onDispose()
    }
    @Test fun repeatedClockInClickSendsOnlyOneMutation() = runTest(dispatcher) {
        val repo = FakeShifts(); val model = ShiftScreenModel(repo,session()); model.start(false); advanceUntilIdle()
        model.clockIn(null) {}; model.clockIn(null) {}; advanceUntilIdle()
        assertEquals(1,repo.clocks); assertFalse(model.state.value.busy); assertNotNull(model.state.value.board?.current); model.onDispose()
    }
    @Test fun failedSaveStaysVisibleAndCanRetry() = runTest(dispatcher) {
        val repo = FakeShifts(); repo.failure = ApiException(409,"Already clocked in")
        val model = ShiftScreenModel(repo,session()); model.start(false); advanceUntilIdle()
        var dismissed=false; model.clockIn(null) { dismissed=true }; advanceUntilIdle()
        assertFalse(dismissed); assertFalse(model.state.value.busy); assertEquals("Already clocked in",model.state.value.error)
        assertEquals(2,repo.reads); model.onDispose()
    }
    @Test fun signOutRemovesShiftData() = runTest(dispatcher) {
        val repo=FakeShifts(); val session=session(); val model=ShiftScreenModel(repo,session); model.start(false); advanceUntilIdle()
        session.signOut(); advanceUntilIdle(); assertNull(model.state.value.board); assertNull(model.state.value.userId); assertFalse(model.state.value.ready); model.onDispose()
    }
    @Test fun dstGapAndInvalidTimesAreRejected() {
        val zone=TimeZone.of("Europe/Berlin")
        assertNull(parseShiftTime(LocalDate(2026,3,29),"02:30",zone))
        assertNull(parseShiftTime(LocalDate(2026,3,29),"25:00",zone))
        assertNotNull(parseShiftTime(LocalDate(2026,3,29),"03:30",zone))
    }
    @Test fun clockInWindowUsesFullTimestampsAcrossMidnight() {
        val item=sample().copy(status="SCHEDULED",scheduledStart="2026-09-27T22:00:00Z",scheduledEnd="2026-09-28T06:00:00Z")
        assertFalse(canClockIn(item,Instant.parse("2026-09-27T19:59:59Z")))
        assertTrue(canClockIn(item,Instant.parse("2026-09-28T00:00:00Z")))
        assertFalse(canClockIn(item,Instant.parse("2026-09-28T06:00:01Z")))
    }
}
internal fun sample()=ShiftItem("s",0,"u","Test Staff","OPEN",startedAt="2026-09-27T08:00:00Z")
internal class FakeShifts : ShiftRepository {
    var reads=0; var clocks=0; var failure: Exception?=null; var current: ShiftItem?=null
    override suspend fun board(restaurant:String,branch:String,from:String,to:String,mine:Boolean): ShiftBoard { reads++; return ShiftBoard("Europe/Berlin","2026-09-27T10:00:00Z",listOfNotNull(current),current) }
    override suspend fun clockIn(restaurant:String,branch:String,id:String?):ShiftItem { clocks++; delay(100); failure?.let { throw it }; return sample().also { current=it } }
    override suspend fun schedule(restaurant:String,branch:String,id:String?,request:ShiftSchedule)=sample()
    override suspend fun action(restaurant:String,branch:String,id:String,action:String,request:ShiftAction)=sample()
    override suspend fun startBreak(restaurant:String,branch:String,id:String,request:ShiftBreakRequest)=sample()
    override suspend fun correct(restaurant:String,branch:String,id:String,request:ShiftCorrection)=sample()
}
