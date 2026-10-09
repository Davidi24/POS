@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.saporini.mobile_desktop.kds

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.kds.data.*
import com.saporini.mobile_desktop.kds.model.*
import com.saporini.mobile_desktop.kds.ui.KdsSection
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

internal val testNow = Instant.parse("2026-09-27T10:00:00Z")
internal fun kdsTicket(id: String = "t", status: String = "FIRED", branch: String = "b") = KdsTicket(id, "r", branch, "s", ticketNumber = "K-1", orderId = "o", orderNumber = "O-1", status = status,
    createdAt = "2026-09-27T09:00:00Z", items = listOf(KdsTicketItem("i", "line", "dish", "Soup", 2, status)))
internal fun kdsEntry() = KdsMenuEntry("menu", "Lunch", "section", "Starters", MenuItem("dish", null, "Soup", null, 9.0, null, true, 0, emptyList(), emptyList(), emptyList()))
internal fun kdsUser(branch: String = "b", permissions: List<String> = listOf("KDS_READ", "KDS_UPDATE", "MENUS_READ", "MENUS_UPDATE", "SETTINGS_READ", "SETTINGS_UPDATE")) = CurrentUserResponse("u", "r", branch, "kitchen@example.invalid", "kitchen", "Test", "Kitchen", isActive = true, emailVerified = true, phoneVerified = false, roles = listOf("KITCHEN"), permissions = permissions)
internal class FakeKds : KdsRepository {
    var reads = 0; var writes = 0; var subscriptions = 0; var cancellations = 0
    var tickets = listOf(kdsTicket()); var historyItems = emptyList<KdsTicket>(); val historyPages = mutableListOf<Int>()
    var boardFailure: Exception? = null; var actionFailure: Exception? = null
    var boardGate: CompletableDeferred<Unit>? = null; var actionGate: CompletableDeferred<Unit>? = null; var detailGate: CompletableDeferred<Unit>? = null
    var stationsResult = listOf(KdsStation("s", "r", "b", name = "Hot line"))
    val changes = MutableSharedFlow<KdsLiveEvent>(extraBufferCapacity = 16)
    override suspend fun board(scope: KdsScope, stationId: String?, deviceId: String?): List<KdsBoard> {
        reads++; val snapshot = tickets.map { it.copy(branchId = scope.branchId) }
        boardGate?.let { withContext(NonCancellable) { it.await() } }; boardFailure?.let { throw it }
        return listOf(KdsBoard(stationId ?: "s", deviceId = deviceId, tickets = snapshot.filter { !it.terminal }))
    }
    override suspend fun display(scope: KdsScope, deviceId: String) = board(scope, "s", deviceId).single()
    override suspend fun ticket(scope: KdsScope, ticketId: String): KdsTicket { detailGate?.let { withContext(NonCancellable) { it.await() } }; return (tickets + historyItems).first { it.id == ticketId } }
    override suspend fun orderTickets(scope: KdsScope, orderId: String) = tickets.filter { it.orderId == orderId }
    override suspend fun syncOrder(scope: KdsScope, orderId: String) = tickets.filter { it.orderId == orderId }
    override suspend fun history(scope: KdsScope, filter: KdsHistoryFilter, stationId: String?, deviceId: String?, page: Int, size: Int): KdsTicketPage {
        historyPages += page
        return KdsTicketPage(historyItems.drop(page * size).take(size), page, size, historyItems.size.toLong(), (historyItems.size + size - 1) / size, (page + 1) * size < historyItems.size)
    }
    override suspend fun action(scope: KdsScope, ticketId: String, itemId: String?, action: KdsAction, input: KdsActionInput): KdsTicket {
        writes++; actionGate?.let { withContext(NonCancellable) { it.await() } }
        val next = when(action) { KdsAction.FIRE -> "FIRED"; KdsAction.START -> "IN_PROGRESS"; KdsAction.READY -> "READY"; KdsAction.COMPLETE -> "COMPLETED" }
        val updated = tickets.first { it.id == ticketId }.copy(status = next, items = tickets.first { it.id == ticketId }.items.map { if(itemId == null || it.id == itemId) it.copy(status = next) else it })
        tickets = tickets.map { if(it.id == ticketId) updated else it }
        if(updated.terminal) historyItems = historyItems + updated
        actionFailure?.let { throw it }; return updated
    }
    override suspend fun stations(scope: KdsScope) = stationsResult
    override suspend fun devices(scope: KdsScope) = emptyList<KdsDevice>()
    override suspend fun saveStation(scope: KdsScope, id: String?, input: KdsStationInput) = KdsStation(id ?: "new", scope.restaurantId, scope.branchId, name = input.name)
    override fun changes(scope: KdsScope) = flow { subscriptions++; try { emitAll(changes) } finally { cancellations++ } }
}
internal class FakeKdsMenu : KdsMenuRepository {
    var entries = listOf(kdsEntry()); var reads = 0; var writes = 0; var failure: Exception? = null
    override suspend fun load(restaurantId: String): List<KdsMenuEntry> { reads++; return entries }
    override suspend fun availability(entry: KdsMenuEntry, available: Boolean): MenuItem {
        writes++; val item = entry.item.copy(available = available); entries = entries.map { if(it.item.id == item.id) it.copy(item = item) else it }
        failure?.let { throw it }; return item
    }
}
class KdsScreenModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun before() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun after() { Dispatchers.resetMain() }
    private suspend fun TestScope.checkModel(repo: FakeKds = FakeKds(), menu: FakeKdsMenu = FakeKdsMenu(), user: CurrentUserResponse = kdsUser(), block: suspend TestScope.(KdsScreenModel, SessionManager) -> Unit) {
        val session = SessionManager().apply { signIn(user) }
        val model = KdsScreenModel(repo, menu, session, object : Clock { override fun now() = testNow })
        try { runCurrent(); block(model, session) } finally { model.onDispose(); runCurrent() }
    }
    @Test fun networkingStartsAndStopsWithVisibility() = runTest(dispatcher) {
        val repo = FakeKds(); checkModel(repo) { model, _ ->
            assertEquals(0, repo.reads); model.setActive(true); runCurrent(); assertEquals(1, repo.reads); assertEquals(1, repo.subscriptions)
            model.setActive(false); runCurrent(); advanceTimeBy(90_000); runCurrent(); assertEquals(1, repo.reads); assertEquals(1, repo.cancellations)
            model.setActive(true); runCurrent(); assertEquals(2, repo.reads)
        }
    }
    @Test fun readPermissionIsRequiredBeforeAnyNetworkCall() = runTest(dispatcher) {
        val repo = FakeKds(); checkModel(repo, user = kdsUser(permissions = emptyList())) { model, _ -> model.setActive(true); runCurrent(); assertEquals(0, repo.reads); assertFalse(model.state.value.canRead) }
    }
    @Test fun readOnlyUsersCannotChangeTickets() = runTest(dispatcher) {
        val repo = FakeKds(); checkModel(repo, user = kdsUser(permissions = listOf("KDS_READ"))) { model, _ ->
            model.setActive(true); runCurrent(); model.perform("t", KdsAction.READY); runCurrent(); assertEquals(0, repo.writes); assertEquals(KdsErrorKind.PERMISSION, model.state.value.actionError?.kind)
        }
    }
    @Test fun duplicateTapsSendOneTicketMutation() = runTest(dispatcher) {
        val repo = FakeKds().apply { actionGate = CompletableDeferred() }; checkModel(repo) { model, _ ->
            model.setActive(true); runCurrent(); model.perform("t", KdsAction.READY); model.perform("t", KdsAction.READY); runCurrent()
            assertEquals(1, repo.writes); assertTrue(model.state.value.busyKeys.isNotEmpty())
            repo.actionGate!!.complete(Unit); runCurrent(); assertEquals("READY", model.state.value.tickets.single().status); assertTrue(model.state.value.busyKeys.isEmpty())
        }
    }
    @Test fun failedRefreshKeepsDataAndMarksItStale() = runTest(dispatcher) {
        val repo = FakeKds(); checkModel(repo) { model, _ ->
            model.setActive(true); runCurrent(); repo.boardFailure = Exception("offline"); model.refresh(); runCurrent()
            assertEquals(1, model.state.value.tickets.size); assertTrue(model.state.value.stale); assertNotNull(model.state.value.error)
            repo.boardFailure = null; model.refresh(); runCurrent(); assertFalse(model.state.value.stale); assertNull(model.state.value.error)
        }
    }
    @Test fun reconnectAndChangesReloadAuthoritativeData() = runTest(dispatcher) {
        val repo = FakeKds(); checkModel(repo) { model, _ ->
            model.setActive(true); runCurrent(); repo.changes.emit(KdsLiveEvent.DISCONNECTED); runCurrent(); assertEquals(KdsConnection.RECONNECTING, model.state.value.connection)
            repo.tickets = listOf(kdsTicket(status = "READY")); repo.changes.emit(KdsLiveEvent.CONNECTED); runCurrent()
            assertEquals("READY", model.state.value.tickets.single().status); assertEquals(KdsConnection.LIVE, model.state.value.connection)
        }
    }
    @Test fun lateReadCannotRestoreSignedOutData() = runTest(dispatcher) {
        val repo = FakeKds().apply { boardGate = CompletableDeferred() }; checkModel(repo) { model, session ->
            model.setActive(true); runCurrent(); session.signOut(); runCurrent(); repo.boardGate!!.complete(Unit); runCurrent()
            assertNull(model.state.value.scope); assertTrue(model.state.value.boards.isEmpty()); assertFalse(model.state.value.loading)
        }
    }
    @Test fun lateWriteCannotRestoreSignedOutData() = runTest(dispatcher) {
        val repo = FakeKds().apply { actionGate = CompletableDeferred() }; checkModel(repo) { model, session ->
            model.setActive(true); runCurrent(); model.perform("t", KdsAction.READY); runCurrent(); session.signOut(); runCurrent()
            repo.actionGate!!.complete(Unit); runCurrent(); assertNull(model.state.value.scope); assertTrue(model.state.value.boards.isEmpty()); assertNull(model.state.value.notice)
        }
    }
    @Test fun branchChangeClearsSelectionAndLoadsNewBranch() = runTest(dispatcher) {
        checkModel { model, session ->
            model.setActive(true); runCurrent(); model.selectTicket("t"); runCurrent(); session.signIn(kdsUser(branch = "new-branch")); runCurrent()
            assertEquals("new-branch", model.state.value.tickets.single().branchId); assertNull(model.state.value.selectedTicketId)
        }
    }
    @Test fun lateDetailsCannotReopenClosedTicket() = runTest(dispatcher) {
        val repo = FakeKds().apply { detailGate = CompletableDeferred() }; checkModel(repo) { model, _ ->
            model.setActive(true); runCurrent(); model.selectTicket("t"); runCurrent(); model.closeTicket(); repo.detailGate!!.complete(Unit); runCurrent()
            assertNull(model.state.value.selectedTicket); assertNull(model.state.value.selectedTicketId); assertFalse(model.state.value.detailLoading)
        }
    }
    @Test fun unknownWriteOutcomeRequiresReviewNotAutomaticRetry() = runTest(dispatcher) {
        val repo = FakeKds().apply { actionFailure = Exception("connection lost after commit") }; checkModel(repo) { model, _ ->
            model.setActive(true); runCurrent(); model.perform("t", KdsAction.READY); runCurrent()
            assertEquals("READY", model.state.value.tickets.single().status); assertTrue(model.state.value.needsReconciliation)
            model.perform("t", KdsAction.COMPLETE); runCurrent(); assertEquals(1, repo.writes)
            model.acknowledgeReconciliation(); assertFalse(model.state.value.needsReconciliation)
            repo.actionFailure = null; model.perform("t", KdsAction.COMPLETE); runCurrent(); assertTrue(model.state.value.tickets.isEmpty())
        }
    }
    @Test fun completionMovesToHistoryAndRefreshPreservesLoadedPages() = runTest(dispatcher) {
        val repo = FakeKds().apply { historyItems = (0..60).map { kdsTicket("h$it", "COMPLETED") } }; checkModel(repo) { model, _ ->
            model.setActive(true); runCurrent(); model.selectSection(KdsSection.HISTORY); runCurrent(); assertEquals(30, model.state.value.history.items.size)
            model.loadMoreHistory(); runCurrent(); assertEquals(60, model.state.value.history.items.size)
            repo.historyPages.clear(); model.refresh(); runCurrent(); assertEquals(listOf(0, 1), repo.historyPages); assertEquals(60, model.state.value.history.items.size)
        }
    }
    @Test fun invalidHistoryRangeDoesNotCallBackend() = runTest(dispatcher) {
        val repo = FakeKds(); checkModel(repo) { model, _ ->
            model.setActive(true); runCurrent(); model.setHistoryFilter(KdsHistoryFilter(testNow, testNow)); runCurrent()
            assertTrue(repo.historyPages.isEmpty()); assertEquals(KdsErrorKind.VALIDATION, model.state.value.history.error?.kind)
        }
    }
    @Test fun menuAvailabilityIsWrittenAndReflectedInFilter() = runTest(dispatcher) {
        val menu = FakeKdsMenu(); checkModel(menu = menu) { model, _ ->
            model.setActive(true); runCurrent(); model.selectSection(KdsSection.MENU); runCurrent(); model.setAvailability("dish", false); runCurrent()
            assertEquals(1, menu.writes); assertFalse(model.state.value.menu.items.single().item.available)
            model.setMenuFilter(available = true); assertTrue(model.state.value.menu.visibleItems.isEmpty())
        }
    }
    @Test fun uncertainAvailabilityCannotBeAcknowledgedBeforeMenuRefresh() = runTest(dispatcher) {
        val menu = FakeKdsMenu().apply { failure = Exception("response lost") }; checkModel(menu = menu) { model, _ ->
            model.setActive(true); runCurrent(); model.selectSection(KdsSection.MENU); runCurrent(); model.setAvailability("dish", false); runCurrent()
            model.acknowledgeReconciliation(); assertTrue(model.state.value.needsReconciliation)
            model.refresh(); runCurrent(); model.acknowledgeReconciliation(); assertFalse(model.state.value.needsReconciliation); assertFalse(model.state.value.menu.items.single().item.available)
        }
    }
    @Test fun forbiddenResponseRemovesPreviouslyVisibleData() = runTest(dispatcher) {
        val repo = FakeKds(); checkModel(repo) { model, _ ->
            model.setActive(true); runCurrent(); repo.boardFailure = ApiException(403, "Access denied"); model.refresh(); runCurrent()
            assertTrue(model.state.value.boards.isEmpty()); assertFalse(model.state.value.canRead)
        }
    }
    @Test fun deviceBindingUsesDeviceBoardAndResetsHistory() = runTest(dispatcher) {
        checkModel { model, _ ->
            model.setActive(true); runCurrent(); model.selectDevice("device"); runCurrent()
            assertEquals("device", model.state.value.boards.single().deviceId); assertNull(model.state.value.stationId)
            model.selectStation("s"); runCurrent(); assertNull(model.state.value.deviceId)
        }
    }
    @Test fun invalidStationRoutingCannotBeSubmitted() = runTest(dispatcher) {
        checkModel { model, _ ->
            model.setActive(true); runCurrent(); assertNull(model.saveStation(null, KdsStationInput("", "GRILL")))
            assertEquals(KdsErrorKind.VALIDATION, model.state.value.actionError?.kind)
        }
    }
}
