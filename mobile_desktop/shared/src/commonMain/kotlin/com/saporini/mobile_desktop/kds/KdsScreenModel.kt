package com.saporini.mobile_desktop.kds

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.kds.data.*
import com.saporini.mobile_desktop.kds.model.*
import com.saporini.mobile_desktop.kds.ui.KdsSection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** Owns data only. The screen activates it while visible and disposes it when leaving KDS. */
class KdsScreenModel(
    private val repository: KdsRepository,
    private val menuRepository: KdsMenuRepository,
    private val session: SessionManager,
    private val clock: Clock = Clock.System,
    private val reconciliationIntervalMillis: Long = 30_000L
) : ScreenModel {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(KdsState(now = clock.now()))
    val state: StateFlow<KdsState> = mutable.asStateFlow()
    // Serializes reads and writes so an older board cannot overwrite a successful action.
    private val requests = Mutex()
    private var generation = 0L
    private var viewRevision = 0L
    private var detailRevision = 0L
    private var historyRevision = 0L
    private var active = false
    private var liveJob: Job? = null
    private var detailJob: Job? = null

    init {
        require(reconciliationIntervalMillis > 0)
        work.launch {
            session.currentUser.collectLatest { user ->
                generation++; viewRevision++; detailRevision++; historyRevision++
                detailJob?.cancel()
                val scope = user?.takeIf { it.isActive }?.let {
                    val r = it.restaurantId?.takeIf(String::isNotBlank)
                    val b = it.defaultBranchId?.takeIf(String::isNotBlank)
                    if (r == null || b == null) null else KdsScope(it.id, r, b)
                }
                mutable.value = KdsState(now = clock.now(), scope = scope, permissions = user?.permissions.orEmpty().toSet())
                restartLiveUpdates()
            }
        }
    }
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) { detailRevision++; detailJob?.cancel(); mutable.update { it.copy(detailLoading = false) } }
        restartLiveUpdates()
    }
    private fun restartLiveUpdates() {
        liveJob?.cancel()
        liveJob = null
        mutable.update { it.copy(connection = if (active && it.canRead) KdsConnection.CONNECTING else KdsConnection.INACTIVE, loading = false) }
        val s = mutable.value.scope ?: return
        if (!active || !mutable.value.canRead) return
        val token = generation
        liveJob = work.launch {
            supervisorScope {
                launch { refreshNow() }
                launch {
                    // Reconcile station/menu edits too: those do not emit order events.
                    while (isActive) { delay(reconciliationIntervalMillis); refreshNow() }
                }
                launch {
                    try {
                        repository.changes(s).conflate().collect { event ->
                            if (!current(token, s)) return@collect
                            mutable.update { it.copy(connection = if (event == KdsLiveEvent.DISCONNECTED) KdsConnection.RECONNECTING else KdsConnection.LIVE,
                                stale = it.stale || event == KdsLiveEvent.DISCONNECTED) }
                            if (event != KdsLiveEvent.DISCONNECTED) refreshNow()
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (current(token, s)) {
                            readFailure(e)
                            mutable.update { it.copy(connection = KdsConnection.RECONNECTING) }
                        }
                    }
                }
            }
        }
    }
    fun selectSection(section: KdsSection) {
        mutable.update { it.copy(section = section) }
        if (active) refresh()
    }
    fun selectStation(id: String?) = changeSelection(id?.takeIf(String::isNotBlank), null)
    fun selectDevice(id: String?) = changeSelection(null, id?.takeIf(String::isNotBlank))
    private fun changeSelection(station: String?, device: String?) {
        if (mutable.value.stationId == station && mutable.value.deviceId == device) return
        viewRevision++; historyRevision++; closeTicket()
        mutable.update { it.copy(stationId = station, deviceId = device, boards = emptyList(), loaded = false, stale = true,
            history = KdsHistoryState(filter = it.history.filter), error = null) }
        if (active) refresh()
    }
    fun setFilter(filter: KdsTicketFilter) { mutable.update { it.copy(filter = filter) } }
    fun setMenuFilter(query: String = state.value.menu.query, available: Boolean? = state.value.menu.available, menuId: String? = state.value.menu.menuId) {
        mutable.update { it.copy(menu = it.menu.copy(query = query, available = available, menuId = menuId)) }
    }
    fun setHistoryFilter(filter: KdsHistoryFilter) {
        filter.problem()?.let { problem -> mutable.update { it.copy(history = it.history.copy(error = KdsFailure(KdsErrorKind.VALIDATION, problem))) }; return }
        historyRevision++
        mutable.update { it.copy(history = KdsHistoryState(filter = filter)) }
        if (active) refresh()
    }
    fun clearMessages() { mutable.update { it.copy(error = null, actionError = null, notice = null, detailError = null) } }
    /** Call only after staff reviewed freshly fetched data following an uncertain write. */
    fun acknowledgeReconciliation() {
        val s = state.value
        val key = s.reconciliationKey.orEmpty()
        val resourceReady = when {
            key.startsWith("menu:") -> s.menu.loaded && !s.menu.loading && s.menu.error == null
            key.startsWith("station:") -> !s.stationLoading && s.stationError == null
            else -> true
        }
        if (!s.stale && s.loaded && resourceReady) mutable.update { it.copy(needsReconciliation = false, reconciliationKey = null, actionError = null) }
    }
    fun refresh(): Job = work.launch { refreshNow() }
    suspend fun refreshNow(): Boolean = requests.withLock {
        val s = state.value.scope ?: return@withLock false
        if (!active || !state.value.canRead) return@withLock false
        val token = generation
        val success = loadBoard(s, token)
        if (!current(token, s) || !active || !state.value.canRead) return@withLock false
        when (state.value.section) {
            KdsSection.HISTORY -> loadHistory(s, token, append = false)
            KdsSection.MENU -> loadMenu(s, token)
            else -> Unit
        }
        if (state.value.reconciliationKey?.startsWith("menu:") == true && state.value.section != KdsSection.MENU) loadMenu(s, token)
        if (state.value.reconciliationKey?.startsWith("station:") == true) loadStationSettings(s, token)
        state.value.selectedTicketId?.let { loadDetail(s, token, it, detailRevision) }
        success
    }
    private suspend fun loadBoard(s: KdsScope, token: Long): Boolean {
        val revision = viewRevision; val station = state.value.stationId; val device = state.value.deviceId
        mutable.update { it.copy(loading = true) }
        try {
            val boards = if (device != null) listOf(repository.display(s, device)) else repository.board(s, station)
            boards.forEach { board ->
                require(station == null || board.stationId == station) { "Unexpected station in response" }
                require(device == null || board.deviceId == device) { "Unexpected device in response" }
                board.tickets.forEach { ticket -> valid(ticket, s); require(ticket.stationId == board.stationId) { "Unexpected ticket station" } }
            }
            if (!current(token, s) || revision != viewRevision) return false
            mutable.update { it.copy(boards = boards.sortedBy { board -> board.displayOrder }, loaded = true, stale = false,
                error = null, lastRefreshedAt = clock.now(), now = clock.now()) }
            return true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (current(token, s) && revision == viewRevision) readFailure(e); return false }
        finally { if (current(token, s) && revision == viewRevision) mutable.update { it.copy(loading = false) } }
    }
    fun loadMoreHistory(): Job = work.launch {
        requests.withLock {
            val s = state.value.scope ?: return@withLock
            if (active && state.value.canRead && state.value.history.hasNext) loadHistory(s, generation, append = true)
        }
    }
    private suspend fun loadHistory(s: KdsScope, token: Long, append: Boolean) {
        val revision = historyRevision; val view = viewRevision; val old = state.value.history
        if (old.loading) return
        mutable.update { it.copy(history = it.history.copy(loading = true, error = null)) }
        try {
            val pages = if (append) listOf(old.loadedPages) else (0 until old.loadedPages.coerceAtLeast(1)).toList()
            val result = if (append) old.items.toMutableList() else mutableListOf()
            var last: KdsTicketPage? = null
            for (page in pages) {
                val response = repository.history(s, old.filter, state.value.stationId, state.value.deviceId, page)
                if (!current(token, s) || revision != historyRevision || view != viewRevision) return
                require(response.page == page) { "Invalid history page" }
                response.items.forEach { valid(it, s); require(it.terminal) { "Active ticket returned in history" } }
                result += response.items; last = response
                if (!response.hasNext) break
            }
            if (current(token, s) && revision == historyRevision && view == viewRevision) {
                val response = requireNotNull(last)
                mutable.update { it.copy(history = it.history.copy(items = result.distinctBy { ticket -> ticket.id }, loadedPages = response.page + 1,
                    hasNext = response.hasNext, totalElements = response.totalElements)) }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (current(token, s) && revision == historyRevision && view == viewRevision) mutable.update { it.copy(history = it.history.copy(error = failure(e))) } }
        finally { if (current(token, s) && revision == historyRevision && view == viewRevision) mutable.update { it.copy(history = it.history.copy(loading = false)) } }
    }
    private suspend fun loadMenu(s: KdsScope, token: Long) {
        if (!state.value.canReadMenu) { mutable.update { it.copy(menu = it.menu.copy(error = KdsFailure(KdsErrorKind.PERMISSION, "You do not have permission to view the menu."))) }; return }
        mutable.update { it.copy(menu = it.menu.copy(loading = true, error = null)) }
        try {
            val entries = menuRepository.load(s.restaurantId)
            if (current(token, s)) mutable.update { it.copy(menu = it.menu.copy(items = entries, loaded = true)) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (current(token, s)) mutable.update { it.copy(menu = it.menu.copy(error = failure(e))) } }
        finally { if (current(token, s)) mutable.update { it.copy(menu = it.menu.copy(loading = false)) } }
    }
    fun selectTicket(id: String) {
        if (id.isBlank() || !active || !state.value.canRead) return
        detailRevision++; detailJob?.cancel()
        mutable.update { it.copy(selectedTicketId = id, selectedTicket = null, relatedTickets = emptyList(), detailLoading = true, detailError = null) }
        val token = generation; val revision = detailRevision
        detailJob = work.launch { requests.withLock {
            val s = state.value.scope ?: return@withLock
            if (active && state.value.canRead && revision == detailRevision) loadDetail(s, token, id, revision)
        } }
    }
    fun closeTicket() {
        detailRevision++; detailJob?.cancel()
        mutable.update { it.copy(selectedTicketId = null, selectedTicket = null, relatedTickets = emptyList(), detailLoading = false, detailError = null) }
    }
    private suspend fun loadDetail(s: KdsScope, token: Long, id: String, revision: Long) {
        fun stillSelected() = current(token, s) && revision == detailRevision && state.value.selectedTicketId == id
        if (stillSelected()) mutable.update { it.copy(detailLoading = true) }
        try {
            val ticket = repository.ticket(s, id); valid(ticket, s); require(ticket.id == id)
            if (!stillSelected()) return
            mutable.update { it.copy(selectedTicket = ticket) }
            val related = repository.orderTickets(s, ticket.orderId)
            related.forEach { valid(it, s); require(it.orderId == ticket.orderId) }
            if (stillSelected()) mutable.update { it.copy(selectedTicket = ticket, relatedTickets = related.filter { it.id != id }, detailError = null) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (stillSelected()) {
                if (e is ApiException && e.status == 404) closeTicket()
                mutable.update { it.copy(detailError = failure(e)) }
            }
        } finally { if (stillSelected()) mutable.update { it.copy(detailLoading = false) } }
    }
    fun perform(ticketId: String, action: KdsAction, itemId: String? = null, input: KdsActionInput = KdsActionInput()): Job? {
        val ticket = state.value.tickets.find { it.id == ticketId } ?: state.value.selectedTicket?.takeIf { it.id == ticketId }
        if (ticket == null || !ticket.allows(action, itemId)) { actionProblem("This action is not available for the current ticket."); return null }
        return mutate("ticket:$ticketId", "KDS_UPDATE", "Ticket updated.") { s, token ->
            val updated = repository.action(s, ticketId, itemId, action, input); valid(updated, s); require(updated.id == ticketId)
            if (current(token, s)) applyTicket(updated)
        }
    }
    // POS Kitchen Status: the waiter took this ticket's ready food out.
    fun pickUp(ticketId: String): Job? {
        val ticket = state.value.tickets.find { it.id == ticketId }
        if (ticket == null || (ticket.status !in setOf("READY", "EXPO_READY") && ticket.items.none { it.status in setOf("READY", "EXPO_READY") })) {
            actionProblem("Nothing on this ticket is ready yet."); return null
        }
        val permission = if ("KDS_UPDATE" in state.value.permissions) "KDS_UPDATE" else "ORDER_UPDATE"
        return mutate("ticket:$ticketId", permission, "Marked as picked up.") { s, token ->
            val updated = repository.pickUp(s, ticketId); valid(updated, s); require(updated.id == ticketId)
            if (current(token, s)) applyTicket(updated)
        }
    }
    // Timings for the POS Kitchen Status; the defaults stay when they can't be read.
    fun loadPosTiming(): Job = work.launch {
        val s = state.value.scope ?: return@launch
        if (!state.value.canRead) return@launch
        runCatching { repository.posTiming(s) }.getOrNull()?.let { timing ->
            if (state.value.scope == s) mutable.update { it.copy(posTiming = timing) }
        }
    }
    fun syncOrder(orderId: String): Job? {
        if (orderId.isBlank()) { actionProblem("Choose an order."); return null }
        return mutate("order:$orderId", "KDS_UPDATE", "Order synced to the kitchen.") { s, token ->
            val tickets = repository.syncOrder(s, orderId)
            tickets.forEach { valid(it, s); require(it.orderId == orderId) }
            if (current(token, s)) tickets.forEach(::applyTicket)
        }
    }
    fun setAvailability(itemId: String, available: Boolean): Job? {
        val entry = state.value.menu.items.find { it.item.id == itemId }
        if (entry == null) { actionProblem("Refresh the menu and choose a dish."); return null }
        if (entry.item.available == available) return null
        return mutate("menu:$itemId", "MENUS_UPDATE", "Dish availability updated.") { s, token ->
            val saved = menuRepository.availability(entry, available); require(saved.id == itemId)
            if (current(token, s)) mutable.update { it.copy(menu = it.menu.copy(items = it.menu.items.map { row -> if (row.item.id == itemId) row.copy(item = saved) else row })) }
        }
    }
    fun loadStationSettings(): Job = work.launch { requests.withLock {
        val s = state.value.scope ?: return@withLock
        if (active) loadStationSettings(s, generation)
    } }
    private suspend fun loadStationSettings(s: KdsScope, token: Long) {
        if ("SETTINGS_READ" !in state.value.permissions) {
            mutable.update { it.copy(stationError = KdsFailure(KdsErrorKind.PERMISSION, "You do not have permission to view station settings.")) }; return
        }
        mutable.update { it.copy(stationLoading = true, stationError = null) }
        try {
            val stations = repository.stations(s)
            if (!current(token, s)) return
            val devices = repository.devices(s)
            stations.forEach { require(it.restaurantId == s.restaurantId && it.branchId == s.branchId) }
            if (current(token, s)) mutable.update { it.copy(stations = stations, devices = devices) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (current(token, s)) mutable.update { it.copy(stationError = failure(e)) } }
        finally { if (current(token, s)) mutable.update { it.copy(stationLoading = false) } }
    }
    fun saveStation(id: String?, input: KdsStationInput): Job? {
        input.problem()?.let { actionProblem(it); return null }
        return mutate("station:${id ?: "new"}", "SETTINGS_UPDATE", "Station saved.") { s, token ->
            val saved = repository.saveStation(s, id, input)
            require(saved.restaurantId == s.restaurantId && saved.branchId == s.branchId && (id == null || saved.id == id))
            if (current(token, s)) mutable.update { it.copy(stations = (it.stations.filterNot { station -> station.id == saved.id } + saved).sortedBy { station -> station.displayOrder }, devices = emptyList()) }
        }
    }
    private fun mutate(key: String, permission: String, message: String, action: suspend (KdsScope, Long) -> Unit): Job? {
        val initial = state.value; val s = initial.scope
        if (!active || s == null || !initial.canRead || permission !in initial.permissions) {
            mutable.update { it.copy(actionError = KdsFailure(KdsErrorKind.PERMISSION, "You do not have permission to make this change.")) }; return null
        }
        if (initial.needsReconciliation) { actionProblem("Refresh and check the previous change before saving again."); return null }
        if (key in initial.busyKeys) return null
        val token = generation
        mutable.update { it.copy(busyKeys = it.busyKeys + key, actionError = null, notice = null) }
        return work.launch {
            try {
                requests.withLock {
                    if (!current(token, s)) return@withLock
                    if (state.value.needsReconciliation) { actionProblem("Check the previous change before saving again."); return@withLock }
                    // Reads and writes can suspend across account/branch changes. Never apply their old result.
                    action(s, token)
                    if (current(token, s)) {
                        mutable.update { it.copy(notice = message) }
                        if (active) {
                            loadBoard(s, token)
                            if (state.value.section == KdsSection.HISTORY) loadHistory(s, token, false)
                            state.value.selectedTicketId?.let { loadDetail(s, token, it, detailRevision) }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current(token, s)) {
                    val problem = failure(e, writing = true)
                    mutable.update { it.copy(actionError = problem, needsReconciliation = it.needsReconciliation || problem.uncertain,
                        reconciliationKey = if (problem.uncertain) key else it.reconciliationKey, stale = true,
                        menu = if (key.startsWith("menu:")) it.menu.copy(error = problem) else it.menu,
                        stationError = if (key.startsWith("station:")) problem else it.stationError) }
                    if (active) requests.withLock { if (current(token, s)) loadBoard(s, token) }
                }
            } finally { if (current(token, s)) mutable.update { it.copy(busyKeys = it.busyKeys - key) } }
        }
    }
    private fun applyTicket(ticket: KdsTicket) {
        mutable.update { old -> old.copy(boards = old.boards.map { board ->
            if (board.stationId != ticket.stationId) board else {
                val items = board.tickets.filterNot { it.id == ticket.id } + if (ticket.terminal) emptyList() else listOf(ticket)
                board.copy(tickets = items, activeTicketCount = items.count { !it.terminal }, readyTicketCount = items.count { it.status in setOf("READY", "EXPO_READY") })
            }
        }, selectedTicket = if (old.selectedTicketId == ticket.id) ticket else old.selectedTicket) }
    }
    private fun current(token: Long, scope: KdsScope) = token == generation && state.value.scope == scope
    private fun valid(ticket: KdsTicket, scope: KdsScope) { require(ticket.restaurantId == scope.restaurantId && ticket.branchId == scope.branchId) { "Ticket belongs to another branch" } }
    private fun actionProblem(message: String) { mutable.update { it.copy(actionError = KdsFailure(KdsErrorKind.VALIDATION, message)) } }
    private fun readFailure(error: Exception) {
        val problem = failure(error)
        mutable.update { old -> if (problem.kind == KdsErrorKind.PERMISSION || problem.kind == KdsErrorKind.AUTHENTICATION)
            KdsState(now = clock.now(), scope = old.scope, permissions = old.permissions, section = old.section, accessDenied = true, error = problem)
            else old.copy(stale = true, error = problem) }
    }
    private fun failure(error: Exception, writing: Boolean = false): KdsFailure {
        val status = (error as? ApiException)?.status
        val kind = when (status) { 401 -> KdsErrorKind.AUTHENTICATION; 403 -> KdsErrorKind.PERMISSION; 404 -> KdsErrorKind.NOT_FOUND; 409 -> KdsErrorKind.CONFLICT; 400, 422 -> KdsErrorKind.VALIDATION; null -> KdsErrorKind.CONNECTION; else -> KdsErrorKind.SERVER }
        val uncertain = writing && (status == null || status >= 500)
        val message = when {
            uncertain -> "The connection was interrupted while saving. Refresh and check the result before trying again."
            error is ApiException -> error.message
            else -> "Could not connect to the kitchen. Try again."
        }
        return KdsFailure(kind, message, uncertain)
    }
    override fun onDispose() { active = false; generation++; work.cancel() }
}
