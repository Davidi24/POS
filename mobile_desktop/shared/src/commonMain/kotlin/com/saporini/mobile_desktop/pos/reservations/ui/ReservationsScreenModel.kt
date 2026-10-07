package com.saporini.mobile_desktop.pos.reservations.ui

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.reservations.domain.model.*
import com.saporini.mobile_desktop.pos.reservations.domain.repository.ReservationRepository
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorPlanFloorNames
import com.saporini.mobile_desktop.pos.tables.domain.model.activeFloorNames
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.model.TableSection
import com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ReservationsScreenModel(
    private val repository: ReservationRepository,
    private val sessionManager: SessionManager,
    private val tableLayoutRepository: TableLayoutRepository? = null
) : ScreenModel {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ReservationsUiState())
    val state: StateFlow<ReservationsUiState> = _state.asStateFlow()

    // One event per finished change, shown as a notification by the screen.
    private val _notices = MutableSharedFlow<ReservationNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<ReservationNotice> = _notices.asSharedFlow()

    private val mutationMutex = Mutex()
    private val refreshMutex = Mutex()
    private var revision = 0L
    private var listRevision = 0L
    private var loadedReservationsPage = 0
    // Arrivals feed paging. `arrivalsFrom` stays fixed between refreshes so "load more" continues the same list.
    private val arrivalsMutex = Mutex()
    private var arrivalPagesLoaded = 0
    private var arrivalsFrom: String? = null
    private var arrivalsFloor: String? = null
    private var requestedArrivalsFloor: String? = null
    private var detailRevision = 0L
    private var detailJob: Job? = null

    init {
        work.launch {
            sessionManager.currentUser.collectLatest { user ->
                revision++
                detailRevision++
                detailJob?.cancel()
                val scope = user?.let {
                    val restaurant = it.restaurantId?.takeIf(String::isNotBlank)
                    val branch = it.defaultBranchId?.takeIf(String::isNotBlank)
                    if (restaurant == null || branch == null) null else ReservationsScope(it.id, restaurant, branch)
                }
                _state.value = ReservationsUiState(
                    scope = scope,
                    permissions = user?.permissions.orEmpty().toSet()
                )
                if (scope != null && _state.value.can(READ_PERMISSION)) {
                    // Times are shown in the restaurant's zone; fall back to this computer's if it can't be loaded.
                    val settings = runCatching { repository.getReservationSettings(scope.restaurantId, scope.branchId) }.getOrNull()
                    RestaurantTime.use(settings?.timezone)
                    _state.update { it.copy(rules = settings?.rules ?: ReservationRules.NONE, policy = settings?.policy ?: ReservationPolicy()) }
                    val occasions = runCatching { repository.getOccasions(scope.restaurantId) }.getOrDefault(emptyList())
                    _state.update { it.copy(occasions = occasions.filter { occasion -> occasion.active }) }
                    refresh()
                }
            }
        }
    }

    fun setFilter(filter: ReservationListFilter) {
        listRevision++
        loadedReservationsPage = 0
        _state.update { it.copy(filter = filter, availabilityOptions = emptyList(), validation = null, hasMoreReservations = false, isLoadingMoreReservations = false) }
        refresh()
    }

    fun loadMoreReservations(): Job = work.launch {
        val state = _state.value
        if (!state.hasMoreReservations || state.isLoadingMoreReservations) return@launch
        val filter = state.filter
        if (filter.mode !in setOf(ReservationListMode.TODAY, ReservationListMode.RANGE, ReservationListMode.CALENDAR, ReservationListMode.ALL)) return@launch
        val scope = try { requireScope(READ_PERMISSION) } catch (error: Exception) {
            _state.update { it.copy(error = failure(error, false)) }
            return@launch
        }
        val token = revision
        val requestRevision = listRevision
        _state.update { it.copy(isLoadingMoreReservations = true, loadMoreReservationsFailed = false) }
        // Waits for a refresh in progress, so the next page follows the pages it reloaded.
        refreshMutex.lock()
        try {
            if (!_state.value.hasMoreReservations || requestRevision != listRevision) return@launch
            val requestedPage = loadedReservationsPage + 1
            val result = when (filter.mode) {
                ReservationListMode.TODAY -> repository.getTodayReservationsPage(scope.restaurantId, scope.branchId, requestedPage, RESERVATION_PAGE_SIZE)
                ReservationListMode.RANGE, ReservationListMode.ALL -> repository.getBranchReservations(
                    restaurantId = scope.restaurantId,
                    branchId = scope.branchId,
                    from = filter.from,
                    to = filter.to,
                    status = filter.status,
                    customerId = filter.customerId,
                    page = requestedPage,
                    size = RESERVATION_PAGE_SIZE
                )
                ReservationListMode.CALENDAR -> repository.getBranchReservationCalendarPage(scope.restaurantId, scope.branchId, filter.from, filter.to, requestedPage, RESERVATION_PAGE_SIZE)
                ReservationListMode.UPCOMING -> return@launch
            }
            if (!isCurrent(token, scope) || requestRevision != listRevision || _state.value.filter != filter) return@launch
            loadedReservationsPage = result.page
            _state.update { current ->
                val existingIds = current.reservations.mapTo(hashSetOf()) { it.id }
                current.copy(
                    reservations = current.reservations + result.items.filterNot { it.id in existingIds },
                    hasMoreReservations = result.hasNext,
                    isLoadingMoreReservations = false,
                    error = null
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent(token, scope) && requestRevision == listRevision) {
                _state.update { it.copy(error = failure(error, false), loadMoreReservationsFailed = true) }
            }
        } finally {
            refreshMutex.unlock()
            if (requestRevision == listRevision) _state.update { it.copy(isLoadingMoreReservations = false) }
        }
    }

    fun loadAllReservationPages(): Job = work.launch {
        while (_state.value.hasMoreReservations && !_state.value.isLoadingMoreReservations && !_state.value.loadMoreReservationsFailed) {
            val before = _state.value.reservations.size
            loadMoreReservations().join()
            if (_state.value.error != null || _state.value.reservations.size == before) break
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun clearError() {
        _state.update { it.copy(error = null, refreshWarning = null) }
    }

    fun clearSuccess() {
        _state.update { it.copy(lastSuccessfulOperation = null) }
    }

    fun acknowledgeReconciliation() {
        _state.update { it.copy(needsReconciliation = false, error = null) }
    }

    fun refresh(): Job = work.launch {
        refreshNow()
        loadTableGroups()
    }

    private suspend fun loadTableGroups() {
        val tablesRepository = tableLayoutRepository ?: return
        val scope = _state.value.scope ?: return
        try {
            val (tables, sections, floorLayouts) = coroutineScope {
                val layout = async { tablesRepository.getTableLayout(scope.restaurantId, scope.branchId) }
                val groups = async { tablesRepository.getTableSections(scope.restaurantId, scope.branchId) }
                val floors = async { tablesRepository.getFloorLayouts(scope.restaurantId, scope.branchId) }
                Triple(layout.await().tables, groups.await(), floors.await())
            }
            _state.update {
                it.copy(
                    tableGroups = buildTableGroups(tables, sections),
                    floors = activeFloorNames(floorLayouts, tables)
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // The calendar keeps its previous rows when tables can't be loaded.
        }
    }

    private fun buildTableGroups(tables: List<LayoutTable>, sections: List<TableSection>): List<ReservationTableGroup> {
        val activeTables = tables.filter { it.active && it.floor in FloorPlanFloorNames }
            .sortedWith(compareBy<LayoutTable>({ it.tableNumber.length }, { it.tableNumber }))
        val grouped = sections.sortedBy { it.displayOrder }.mapNotNull { section ->
            activeTables.filter { it.id in section.tableIds }
                .takeIf { it.isNotEmpty() }
                ?.let { ReservationTableGroup(section.name, it) }
        }
        val groupedIds = sections.flatMap { it.tableIds }.toSet()
        val others = activeTables.filter { it.id !in groupedIds }
        return if (others.isEmpty()) grouped else grouped + ReservationTableGroup(OTHER_TABLES_GROUP, others)
    }

    suspend fun refreshNow(allowWhileSaving: Boolean = false): Boolean {
        if (_state.value.isSaving && !allowWhileSaving) return false
        refreshMutex.lock()
        try {
            if (_state.value.isSaving && !allowWhileSaving) return false
            val token = revision
            val requestRevision = listRevision
            val scope = try { requireScope(READ_PERMISSION) } catch (error: Exception) {
                _state.update { it.copy(error = failure(error, false)) }
                return false
            }
            val filter = _state.value.filter
            // Reload every page already loaded (not just the first), so rows the user scrolled to stay put.
            val lastPageShown = loadedReservationsPage
            var lastPageLoaded = 0
            _state.update { it.copy(isLoading = true) }
            try {
                var hasMoreReservations = false
                val reservations = when (filter.mode) {
                    ReservationListMode.TODAY -> loadPages(lastPageShown) { page ->
                        repository.getTodayReservationsPage(scope.restaurantId, scope.branchId, page, RESERVATION_PAGE_SIZE)
                    }.also {
                        hasMoreReservations = it.hasNext
                        lastPageLoaded = it.lastPage
                    }.items
                    ReservationListMode.UPCOMING -> repository.getUpcomingReservations(scope.restaurantId, scope.branchId, filter.upcomingLimit)
                    ReservationListMode.RANGE -> loadPages(lastPageShown) { page ->
                        repository.getBranchReservations(
                            restaurantId = scope.restaurantId,
                            branchId = scope.branchId,
                            from = filter.from,
                            to = filter.to,
                            status = filter.status,
                            customerId = filter.customerId,
                            page = page,
                            size = RESERVATION_PAGE_SIZE
                        )
                    }.also {
                        hasMoreReservations = it.hasNext
                        lastPageLoaded = it.lastPage
                    }.items
                    ReservationListMode.CALENDAR -> loadPages(lastPageShown) { page ->
                        repository.getBranchReservationCalendarPage(scope.restaurantId, scope.branchId, filter.from, filter.to, page, RESERVATION_PAGE_SIZE)
                    }.also {
                        hasMoreReservations = it.hasNext
                        lastPageLoaded = it.lastPage
                    }.items
                    ReservationListMode.ALL -> loadPages(lastPageShown) { page ->
                        repository.getBranchReservations(
                            restaurantId = scope.restaurantId,
                            branchId = scope.branchId,
                            from = filter.from,
                            to = filter.to,
                            status = filter.status,
                            customerId = filter.customerId,
                            page = page,
                            size = RESERVATION_PAGE_SIZE
                        )
                    }.also {
                        hasMoreReservations = it.hasNext
                        lastPageLoaded = it.lastPage
                    }.items
                }
                if (!isCurrent(token, scope) || requestRevision != listRevision) return false
                loadedReservationsPage = lastPageLoaded
                _state.update {
                    it.copy(
                        reservations = reservations,
                        hasMoreReservations = hasMoreReservations,
                        loadMoreReservationsFailed = false,
                        lastRefreshedAt = Clock.System.now().toString(),
                        error = if (it.needsReconciliation) it.error else null,
                        refreshWarning = null
                    )
                }
                val selectedId = _state.value.selectedReservationId
                if (selectedId != null) fetchDetails(scope, token, selectedId, detailRevision)
                return true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(token, scope)) _state.update { it.copy(error = failure(error, false)) }
                return false
            } finally {
                if (isCurrent(token, scope)) _state.update { it.copy(isLoading = false) }
            }
        } finally {
            refreshMutex.unlock()
        }
    }

    fun selectReservation(reservationId: String) {
        require(reservationId.isNotBlank())
        detailRevision++
        detailJob?.cancel()
        _state.update {
            it.copy(
                selectedReservationId = reservationId,
                selectedReservation = null,
                notes = emptyList(),
                statusHistory = emptyList(),
                timeline = emptyList(),
                audit = null,
                deposit = null,
                isLoadingDetails = true,
                error = null
            )
        }
        val token = revision
        val detailToken = detailRevision
        detailJob = work.launch {
            try {
                fetchDetails(requireScope(READ_PERMISSION), token, reservationId, detailToken)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token == revision && detailToken == detailRevision) {
                    _state.update { it.copy(error = failure(error, false)) }
                }
            } finally {
                if (token == revision && detailToken == detailRevision) {
                    _state.update { it.copy(isLoadingDetails = false) }
                }
            }
        }
    }

    fun closeReservationDetails() {
        detailRevision++
        detailJob?.cancel()
        _state.update {
            it.copy(
                selectedReservationId = null,
                selectedReservation = null,
                notes = emptyList(),
                statusHistory = emptyList(),
                timeline = emptyList(),
                audit = null,
                deposit = null,
                isLoadingDetails = false
            )
        }
    }

    // With a floor, the numbers cover that floor's bookings plus those without a table.
    fun loadSummary(from: String? = _state.value.filter.from, to: String? = _state.value.filter.to, floor: String? = null): Job = work.launch {
        query(READ_PERMISSION) { scope -> repository.getReservationSummary(scope.restaurantId, scope.branchId, from, to, floor) }
            .onSuccess { summary -> _state.update { it.copy(summary = summary, error = null) } }
            .onFailure { error -> _state.update { it.copy(error = operationFailure(error)) } }
    }

    fun loadCapacity(
        from: String? = _state.value.filter.from,
        to: String? = _state.value.filter.to,
        partySize: Int? = null,
        floor: String? = null
    ): Job = work.launch {
        query(READ_PERMISSION) { scope -> repository.getReservationCapacity(scope.restaurantId, scope.branchId, from, to, partySize, floor) }
            .onSuccess { capacity -> _state.update { it.copy(capacity = capacity, error = null) } }
            .onFailure { error -> _state.update { it.copy(error = operationFailure(error)) } }
    }

    fun searchAvailability(request: ReservationAvailabilitySearchInput): Job = availabilityQuery {
        repository.searchAvailability(it.restaurantId, it.branchId, request)
    }

    fun recommendAvailability(request: ReservationAvailabilitySearchInput): Job = availabilityQuery {
        repository.recommendAvailability(it.restaurantId, it.branchId, request)
    }

    fun validateReservation(request: ReservationValidationInput): Job = work.launch {
        _state.update { it.copy(isLoadingAvailability = true, error = null) }
        query(READ_PERMISSION) { scope -> repository.validateReservation(scope.restaurantId, scope.branchId, request) }
            .onSuccess { validation -> _state.update { it.copy(validation = validation, isLoadingAvailability = false) } }
            .onFailure { error -> _state.update { it.copy(error = operationFailure(error), isLoadingAvailability = false) } }
    }

    suspend fun tableSuggestions(start: String, end: String, partySize: Int, maxOptions: Int): Result<List<ReservationAvailabilityOption>> =
        query(READ_PERMISSION) { scope ->
            repository.recommendAvailability(
                scope.restaurantId, scope.branchId,
                ReservationAvailabilitySearchInput(start, end, partySize, maxOptions)
            )
        }

    suspend fun freeTableIds(start: String, end: String): Result<Set<String>> =
        query(READ_PERMISSION) { scope ->
            tableLayoutRepository?.getFreeTableIds(scope.restaurantId, scope.branchId, start, end).orEmpty()
        }

    // Next bookings from now on, across days (for the Overview "Later" list).
    // Reloads the Arriving feed from 15 minutes ago, as many pages as are shown now (one after a floor change),
    // and swaps the list in one go so the screen keeps its scroll position.
    fun refreshArrivals(floor: String?): Job = work.launch {
        requestedArrivalsFloor = floor
        arrivalsMutex.withLock {
            val fresh = floor != arrivalsFloor || !_state.value.arrivalsLoaded
            val lastPage = if (fresh) 0 else (arrivalPagesLoaded - 1).coerceAtLeast(0)
            val from = (Clock.System.now() - ARRIVAL_GRACE).toString()
            query(READ_PERMISSION) { scope ->
                loadPages(lastPage) { page -> repository.getArrivalsPage(scope.restaurantId, scope.branchId, from, floor, page, RESERVATION_PAGE_SIZE) }
            }.onSuccess { loaded ->
                arrivalsFrom = from
                arrivalsFloor = floor
                arrivalPagesLoaded = loaded.lastPage + 1
                _state.update {
                    it.copy(arrivals = loaded.items, arrivalsLoaded = true, hasMoreArrivals = loaded.hasNext, loadMoreArrivalsFailed = false)
                }
            }.onFailure {
                // A feed already shown stays as it is until the next refresh. Without a first page, the list
                // stops waiting and offers "Try again".
                if (arrivalsFrom == null) _state.update { it.copy(arrivalsLoaded = true, hasMoreArrivals = true, loadMoreArrivalsFailed = true) }
            }
        }
    }

    // Next page of the Arriving feed, continuing the list the last refresh started.
    fun loadMoreArrivals(): Job = work.launch {
        val state = _state.value
        if (!state.hasMoreArrivals || state.isLoadingMoreArrivals) return@launch
        _state.update { it.copy(isLoadingMoreArrivals = true, loadMoreArrivalsFailed = false) }
        try {
            // The first page never arrived: start the feed again.
            if (arrivalsFrom == null) {
                refreshArrivals(requestedArrivalsFloor).join()
                return@launch
            }
            arrivalsMutex.withLock {
                val from = arrivalsFrom ?: return@withLock
                if (!_state.value.hasMoreArrivals) return@withLock
                val floor = arrivalsFloor
                val page = arrivalPagesLoaded
                query(READ_PERMISSION) { scope -> repository.getArrivalsPage(scope.restaurantId, scope.branchId, from, floor, page, RESERVATION_PAGE_SIZE) }
                    .onSuccess { result ->
                        arrivalPagesLoaded = page + 1
                        _state.update { current ->
                            val shown = current.arrivals.mapTo(hashSetOf()) { it.id }
                            current.copy(arrivals = current.arrivals + result.items.filterNot { it.id in shown }, hasMoreArrivals = result.hasNext)
                        }
                    }
                    .onFailure { _state.update { it.copy(loadMoreArrivalsFailed = true) } }
            }
        } finally {
            _state.update { it.copy(isLoadingMoreArrivals = false) }
        }
    }

    // Pages 0..lastPage in order, stopping early when the list runs out; a row that moved between pages shows once.
    private suspend fun loadPages(lastPage: Int, fetch: suspend (Int) -> ReservationPage): LoadedPages {
        val items = ArrayList<Reservation>()
        val seen = HashSet<String>()
        var page = 0
        while (true) {
            val result = fetch(page)
            result.items.forEach { if (seen.add(it.id)) items += it }
            if (!result.hasNext || page >= lastPage) return LoadedPages(items, page, result.hasNext)
            page++
        }
    }

    private class LoadedPages(val items: List<Reservation>, val lastPage: Int, val hasNext: Boolean)

    suspend fun upcomingReservations(limit: Int): Result<List<Reservation>> =
        query(READ_PERMISSION) { scope -> repository.getUpcomingReservations(scope.restaurantId, scope.branchId, limit) }

    // Waits for the create to finish so the form can close on success or show the error.
    suspend fun submitReservation(request: ReservationInput): Result<Unit> {
        _state.update { it.copy(error = null) }
        createReservation(request).join()
        val error = _state.value.error
        return if (error == null) Result.success(Unit) else Result.failure(Exception(error.message))
    }

    // Staff bookings are confirmed when a table fits; otherwise the server keeps them as a request.
    fun createReservation(request: ReservationInput): Job = mutateReservation(
        request.contactName?.takeIf(String::isNotBlank)?.let { "Reservation created for $it" } ?: "Reservation created",
        selectResult = true,
        describe = { saved ->
            val done = saved.contactName?.takeIf(String::isNotBlank)?.let { "Reservation created for $it" } ?: "Reservation created"
            when {
                saved.status != ReservationStatus.PENDING -> done
                saved.partySize >= _state.value.policy.approvalGroupSize ->
                    "Saved as a request: groups of ${_state.value.policy.approvalGroupSize} or more need approval"
                else -> "Saved as a request: no table is free for the whole booking"
            }
        }
    ) { scope ->
        repository.createReservation(scope.restaurantId, request.copy(branchId = request.branchId ?: scope.branchId))
    }

    fun updateReservation(reservationId: String, request: ReservationInput): Job = mutateReservation("Reservation updated") { scope ->
        repository.updateReservation(scope.restaurantId, reservationId, request.copy(branchId = request.branchId ?: scope.branchId))
    }

    fun patchReservation(reservationId: String, request: UpdateReservationInput): Job = mutateReservation("Reservation updated") { scope ->
        repository.patchReservation(scope.restaurantId, reservationId, request)
    }

    fun deleteReservation(reservationId: String): Job = work.launch {
        if (!mutationMutex.tryLock()) {
            _state.update { it.copy(error = ReservationFailure(ReservationErrorKind.CONFLICT, "Another reservation change is still saving")) }
            return@launch
        }
        val token = revision
        var started = false
        try {
            val scope = requireScope(WRITE_PERMISSION)
            check(!_state.value.needsReconciliation) { "Refresh and check the last change before trying another update" }
            _state.update { it.copy(isSaving = true, error = null, lastSuccessfulOperation = null) }
            started = true
            repository.deleteReservation(scope.restaurantId, reservationId)
            if (!isCurrent(token, scope)) throw CancellationException("Reservation session changed")
            _state.update {
                it.copy(
                    reservations = it.reservations.filterNot { reservation -> reservation.id == reservationId },
                    selectedReservationId = if (it.selectedReservationId == reservationId) null else it.selectedReservationId,
                    selectedReservation = if (it.selectedReservationId == reservationId) null else it.selectedReservation,
                    lastSuccessfulOperation = "Reservation deleted"
                )
            }
            val refreshed = refreshNow(allowWhileSaving = true)
            if (!refreshed) _state.update { it.copy(error = null, refreshWarning = "Change saved. Refresh to load the latest reservations.") }
        } catch (error: CancellationException) {
            if (started && token == revision && _state.value.lastSuccessfulOperation == null) {
                _state.update { it.copy(needsReconciliation = true, error = failure(error, true)) }
            }
            throw error
        } catch (error: Exception) {
            val problem = failure(error, started)
            if (token == revision) _state.update { it.copy(error = problem, needsReconciliation = it.needsReconciliation || problem.operationMayHaveSucceeded) }
        } finally {
            if (token == revision) _state.update { it.copy(isSaving = false) }
            mutationMutex.unlock()
        }
    }

    fun confirmReservation(reservationId: String, reason: String? = null): Job = lifecycle("Reservation confirmed", reservationId, reason) { scope, id, input ->
        repository.confirmReservation(scope.restaurantId, id, input)
    }

    fun cancelReservation(reservationId: String, reason: String? = null): Job = lifecycle("Reservation cancelled", reservationId, reason) { scope, id, input ->
        repository.cancelReservation(scope.restaurantId, id, input)
    }

    // "Guest arrived": everyone, or how many of the group are here so far.
    fun checkInReservation(reservationId: String, reason: String? = null, arrivedGuests: Int? = null): Job =
        mutateReservation(
            withGuest("Guest arrived", reservationId),
            // A reminder for the host: "Guest arrived · Maria · 🎂 Birthday: Cake from us".
            describe = { saved ->
                listOfNotNull(withGuest("Guest arrived", reservationId), saved.occasionName?.let { name ->
                    listOfNotNull(saved.occasionIcon, name).joinToString(" ") +
                        (saved.occasionOptions.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = ": ") ?: "")
                }).joinToString(" · ")
            }
        ) { scope ->
            repository.checkInReservation(scope.restaurantId, reservationId, ReservationActionInput(reason = reason, arrivedGuests = arrivedGuests))
        }

    fun updateArrivedGuests(reservationId: String, arrivedGuests: Int, reason: String? = null): Job =
        mutateReservation(withGuest("$arrivedGuests arrived", reservationId)) { scope ->
            repository.updateArrivedGuests(scope.restaurantId, reservationId, ReservationActionInput(reason = reason, arrivedGuests = arrivedGuests))
        }

    fun undoSeatReservation(reservationId: String, reason: String? = null): Job = lifecycle("Seating undone", reservationId, reason) { scope, id, input ->
        repository.undoSeatReservation(scope.restaurantId, id, input)
    }

    fun extendHold(reservationId: String, minutes: Int, reason: String? = null): Job =
        mutateReservation(withGuest("Table held $minutes min longer", reservationId)) { scope ->
            repository.extendHold(scope.restaurantId, reservationId, minutes, reason)
        }

    fun confirmAttendance(reservationId: String, reason: String? = null): Job =
        mutateReservation(withGuest("Attendance confirmed", reservationId)) { scope ->
            repository.confirmAttendance(scope.restaurantId, reservationId, reason)
        }

    fun clearNoShowWarning(reservationId: String, reason: String): Job =
        mutateReservation(withGuest("No-show warning cleared", reservationId)) { scope ->
            repository.clearNoShowWarning(scope.restaurantId, reservationId, reason)
        }

    // Say no to a request: the guest is told and gets all their money back.
    fun declineReservation(reservationId: String, reason: String? = null): Job =
        mutateReservation(withGuest("Request declined", reservationId)) { scope ->
            repository.declineReservation(scope.restaurantId, reservationId, reason)
        }

    // The booking's money (deposit, paid extras, food pre-order) and what a cancel would do right now.
    suspend fun money(reservationId: String): Result<List<MoneyLine>> =
        query(READ_PERMISSION) { scope -> repository.getMoney(scope.restaurantId, reservationId) }

    // Paid extras from the special menu that fit the booking's occasion.
    suspend fun extraChoices(reservationId: String): Result<List<BookingExtraChoice>> =
        query(READ_PERMISSION) { scope -> repository.getExtraChoices(scope.restaurantId, reservationId) }

    // Money changes run in the model's scope, so closing the panel doesn't cut a save short.
    fun addExtra(reservationId: String, choice: BookingExtraChoice, quantity: Int): Deferred<Result<List<MoneyLine>>> = work.async {
        mutate(WRITE_PERMISSION, withGuest("${if (quantity > 1) "$quantity × " else ""}${choice.name} added", reservationId)) { scope ->
            repository.addExtra(scope.restaurantId, reservationId, choice.menuItemId, quantity)
        }
    }

    fun sendPaymentLink(reservationId: String): Deferred<Result<Unit>> = work.async {
        mutate(WRITE_PERMISSION, withGuest("Payment link emailed", reservationId)) { scope ->
            repository.sendPaymentLink(scope.restaurantId, reservationId)
        }
    }

    fun markPaid(reservationId: String, line: MoneyLine): Deferred<Result<List<MoneyLine>>> = work.async {
        mutate(WRITE_PERMISSION, "${line.description} marked paid") { scope ->
            repository.markPaid(scope.restaurantId, reservationId, line.id)
        }
    }

    fun removeUnpaid(reservationId: String, line: MoneyLine): Deferred<Result<List<MoneyLine>>> = work.async {
        mutate(WRITE_PERMISSION, "${line.description} removed") { scope ->
            repository.removeUnpaid(scope.restaurantId, reservationId, line.id)
        }
    }

    fun goodwillRefund(reservationId: String, line: MoneyLine, amountCents: Long, reason: String): Deferred<Result<List<MoneyLine>>> = work.async {
        mutate(GOODWILL_PERMISSION, "Goodwill refund: ${moneyText(amountCents, line.currency)} back") { scope ->
            repository.goodwillRefund(scope.restaurantId, reservationId, line.id, amountCents, reason)
        }
    }

    // The restaurant's event on a day, for the overview's header.
    suspend fun eventOn(date: kotlinx.datetime.LocalDate): Result<RestaurantEvent?> =
        query(READ_PERMISSION) { scope -> repository.getEventOn(scope.restaurantId, date.toString()) }

    suspend fun guestHistory(reservationId: String): Result<GuestHistory> =
        query(READ_PERMISSION) { scope -> repository.getGuestHistory(scope.restaurantId, reservationId) }

    // Late guests: do they still fit at their table until the booking ends, or where else they could sit.
    suspend fun seatingCheck(reservationId: String): Result<ReservationSeatingCheck> =
        query(READ_PERMISSION) { scope -> repository.getSeatingCheck(scope.restaurantId, reservationId) }

    fun seatReservation(reservationId: String, reason: String? = null): Job = lifecycle("Reservation seated", reservationId, reason) { scope, id, input ->
        repository.seatReservation(scope.restaurantId, id, input)
    }

    fun completeReservation(reservationId: String, reason: String? = null): Job = lifecycle("Reservation completed", reservationId, reason) { scope, id, input ->
        repository.completeReservation(scope.restaurantId, id, input)
    }

    fun markNoShow(reservationId: String, reason: String? = null): Job = lifecycle("Reservation marked no-show", reservationId, reason) { scope, id, input ->
        repository.markNoShow(scope.restaurantId, id, input)
    }

    fun reopenReservation(reservationId: String, reason: String? = null): Job = lifecycle("Reservation reopened", reservationId, reason) { scope, id, input ->
        repository.reopenReservation(scope.restaurantId, id, input)
    }

    fun replaceReservationTables(reservationId: String, request: ReservationTablesInput): Job = tableMutation("Tables updated", reservationId) { scope ->
        repository.updateReservationTables(scope.restaurantId, reservationId, request)
    }

    fun addReservationTable(reservationId: String, tableId: String): Job = tableMutation("Table added", reservationId) { scope ->
        repository.addReservationTable(scope.restaurantId, reservationId, tableId)
    }

    fun removeReservationTable(reservationId: String, tableId: String): Job = tableMutation("Table removed", reservationId) { scope ->
        repository.deleteReservationTable(scope.restaurantId, reservationId, tableId)
    }

    fun markPrimaryReservationTable(reservationId: String, tableId: String): Job = tableMutation("Primary table updated", reservationId) { scope ->
        repository.markPrimaryReservationTable(scope.restaurantId, reservationId, tableId)
    }

    fun autoAssignReservationTable(reservationId: String): Job = tableMutation("Table auto-assigned", reservationId) { scope ->
        repository.autoAssignReservationTable(scope.restaurantId, reservationId)
    }

    fun addNote(reservationId: String, note: String): Job = work.launch {
        mutate(WRITE_PERMISSION, "Note added") { scope -> repository.addNote(scope.restaurantId, reservationId, note) }
            .onSuccess { loadDetails(reservationId) }
    }

    fun deleteNote(reservationId: String, noteId: String): Job = work.launch {
        mutate(WRITE_PERMISSION, "Note deleted") { scope -> repository.deleteNote(scope.restaurantId, reservationId, noteId) }
            .onSuccess { loadDetails(reservationId) }
    }

    fun loadDetails(reservationId: String? = _state.value.selectedReservationId): Job = work.launch {
        val id = reservationId ?: return@launch
        selectReservation(id)
    }

    fun loadDeposit(reservationId: String? = _state.value.selectedReservationId): Job = work.launch {
        val id = reservationId ?: return@launch
        query(READ_PERMISSION) { scope -> repository.getDeposit(scope.restaurantId, id) }
            .onSuccess { deposit -> _state.update { it.copy(deposit = deposit, error = null) } }
            .onFailure { error -> _state.update { it.copy(error = operationFailure(error)) } }
    }

    fun updateDeposit(reservationId: String, request: ReservationDepositInput): Job = depositMutation("Deposit updated", reservationId) { scope ->
        repository.updateDeposit(scope.restaurantId, reservationId, request)
    }

    fun payDeposit(reservationId: String): Job = depositMutation("Deposit marked paid", reservationId) { scope ->
        repository.payDeposit(scope.restaurantId, reservationId)
    }

    fun refundDeposit(reservationId: String): Job = depositMutation("Deposit refunded", reservationId) { scope ->
        repository.refundDeposit(scope.restaurantId, reservationId)
    }

    fun waiveDeposit(reservationId: String): Job = depositMutation("Deposit waived", reservationId) { scope ->
        repository.waiveDeposit(scope.restaurantId, reservationId)
    }

    fun forfeitDeposit(reservationId: String): Job = depositMutation("Deposit forfeited", reservationId) { scope ->
        repository.forfeitDeposit(scope.restaurantId, reservationId)
    }

    private fun lifecycle(
        operation: String,
        reservationId: String,
        reason: String?,
        action: suspend (ReservationsScope, String, ReservationActionInput) -> Reservation
    ): Job = mutateReservation(withGuest(operation, reservationId)) { scope ->
        action(scope, reservationId, ReservationActionInput(reason = reason))
    }

    private fun withGuest(operation: String, reservationId: String): String =
        (_state.value.reservations.firstOrNull { it.id == reservationId } ?: _state.value.selectedReservation?.takeIf { it.id == reservationId })
            ?.let { "$operation · ${it.displayGuestName.let { name -> if (name.length > 40) name.take(39).trimEnd() + "…" else name }}" } ?: operation

    private fun tableMutation(
        operation: String,
        reservationId: String,
        action: suspend (ReservationsScope) -> Any
    ): Job = work.launch {
        mutate(WRITE_PERMISSION, operation) { scope -> action(scope) }
            .onSuccess { refreshSelectedReservation(reservationId) }
    }

    private fun depositMutation(
        operation: String,
        reservationId: String,
        action: suspend (ReservationsScope) -> ReservationDeposit
    ): Job = work.launch {
        mutate(WRITE_PERMISSION, operation) { scope -> action(scope) }
            .onSuccess { deposit ->
                _state.update { it.copy(deposit = deposit) }
                refreshSelectedReservation(reservationId)
            }
    }

    private fun mutateReservation(
        operation: String,
        selectResult: Boolean = false,
        describe: (Reservation) -> String = { operation },
        action: suspend (ReservationsScope) -> Reservation
    ): Job = work.launch {
        mutate(WRITE_PERMISSION, operation, describe) { scope -> action(scope) }
            .onSuccess { reservation -> applyReservationMutation(reservation, selectResult) }
    }

    private fun availabilityQuery(action: suspend (ReservationsScope) -> List<ReservationAvailabilityOption>): Job = work.launch {
        _state.update { it.copy(isLoadingAvailability = true, error = null) }
        query(READ_PERMISSION, action)
            .onSuccess { options -> _state.update { it.copy(availabilityOptions = options, isLoadingAvailability = false) } }
            .onFailure { error -> _state.update { it.copy(error = operationFailure(error), isLoadingAvailability = false) } }
    }

    private suspend fun fetchDetails(scope: ReservationsScope, token: Long, id: String, detailToken: Long) {
        try {
            val details = repository.getReservationDetails(scope.restaurantId, id)
            require(details.reservation.restaurantId == scope.restaurantId && details.reservation.branchId == scope.branchId) {
                "Reservation belongs to another branch"
            }
            if (isCurrent(token, scope) && detailToken == detailRevision && _state.value.selectedReservationId == id) {
                _state.update {
                    it.copy(
                        selectedReservation = details.reservation,
                        notes = details.audit.notes,
                        statusHistory = details.audit.statusHistory,
                        timeline = details.timeline,
                        audit = details.audit,
                        deposit = details.deposit,
                        error = null
                    )
                }
            }
        } catch (error: ApiException) {
            if (error.status == 404 && isCurrent(token, scope) && detailToken == detailRevision) closeReservationDetails()
            throw error
        }
    }

    private suspend fun refreshSelectedReservation(reservationId: String) {
        val scope = requireScope(READ_PERMISSION)
        val reservation = repository.getReservation(scope.restaurantId, reservationId)
        applyReservationMutation(reservation, selectResult = false)
        if (_state.value.selectedReservationId == reservationId) selectReservation(reservationId)
    }

    internal suspend fun <T> query(permission: String, action: suspend (ReservationsScope) -> T): Result<T> {
        val token = revision
        return try {
            val scope = requireScope(permission)
            val result = action(scope)
            if (!isCurrent(token, scope)) throw CancellationException("Reservation session changed")
            Result.success(result)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(ReservationOperationException(failure(error, false)))
        }
    }

    internal suspend fun <T> mutate(
        permission: String,
        operation: String,
        // The message once it's saved, when it depends on the result (e.g. a booking saved as a request).
        describe: (T) -> String = { operation },
        action: suspend (ReservationsScope) -> T
    ): Result<T> {
        if (!mutationMutex.tryLock()) {
            _notices.tryEmit(ReservationNotice("Another reservation change is still saving", isError = true))
            return Result.failure(ReservationOperationException(ReservationFailure(ReservationErrorKind.CONFLICT, "Another reservation change is still saving")))
        }
        val token = revision
        var started = false
        try {
            val scope = requireScope(permission)
            check(!_state.value.needsReconciliation) { "Refresh and check the last change before trying another update" }
            listRevision++
            detailRevision++
            detailJob?.cancel()
            _state.update { it.copy(isSaving = true, isLoadingDetails = false, error = null, lastSuccessfulOperation = null) }
            started = true
            val result = action(scope)
            if (!isCurrent(token, scope)) throw CancellationException("Reservation session changed")
            val message = describe(result)
            _state.update { it.copy(lastSuccessfulOperation = message) }
            _notices.tryEmit(ReservationNotice(message, isError = false))
            val refreshed = refreshNow(allowWhileSaving = true)
            if (!isCurrent(token, scope)) throw CancellationException("Reservation session changed")
            if (!refreshed) _state.update { it.copy(error = null, refreshWarning = "Change saved. Refresh to load the latest reservations.") }
            return Result.success(result)
        } catch (error: CancellationException) {
            if (started && token == revision && _state.value.lastSuccessfulOperation == null) {
                _state.update { it.copy(needsReconciliation = true, error = failure(error, true)) }
            }
            throw error
        } catch (error: Exception) {
            val problem = failure(error, started)
            _notices.tryEmit(ReservationNotice(problem.message, isError = true))
            if (token == revision) {
                _state.update { it.copy(error = problem, needsReconciliation = it.needsReconciliation || problem.operationMayHaveSucceeded) }
            }
            return Result.failure(ReservationOperationException(problem))
        } finally {
            if (token == revision) _state.update { it.copy(isSaving = false) }
            mutationMutex.unlock()
        }
    }

    private fun applyReservationMutation(reservation: Reservation, selectResult: Boolean) {
        if (reservation.branchId != _state.value.scope?.branchId) return
        _state.update { current ->
            val next = if (current.reservations.any { it.id == reservation.id }) {
                current.reservations.map { if (it.id == reservation.id) reservation else it }
            } else if (!current.filter.covers(reservation)) {
                // Saved for another day than the one on screen: keep the list as it is.
                current.reservations
            } else {
                listOf(reservation) + current.reservations
            }
            current.copy(
                reservations = next,
                selectedReservationId = if (selectResult || current.selectedReservationId == reservation.id) reservation.id else current.selectedReservationId,
                selectedReservation = if (selectResult || current.selectedReservationId == reservation.id) reservation else current.selectedReservation
            )
        }
    }

    private fun requireScope(permission: String): ReservationsScope {
        val user = sessionManager.currentUser.value
        val scope = _state.value.scope
        if (user == null || scope == null || user.id != scope.userId || user.restaurantId != scope.restaurantId || user.defaultBranchId != scope.branchId) {
            throw ReservationOperationException(ReservationFailure(ReservationErrorKind.SESSION, "Sign in with an assigned restaurant and branch"))
        }
        if (permission !in user.permissions) {
            throw ReservationOperationException(ReservationFailure(ReservationErrorKind.PERMISSION, "You do not have permission for this reservation action"))
        }
        return scope
    }

    private fun isCurrent(token: Long, scope: ReservationsScope): Boolean {
        val user = sessionManager.currentUser.value
        return token == revision && _state.value.scope == scope && user?.id == scope.userId && user.restaurantId == scope.restaurantId && user.defaultBranchId == scope.branchId
    }

    private fun operationFailure(error: Throwable): ReservationFailure = when (error) {
        is ReservationOperationException -> error.failure
        else -> failure(error, false)
    }

    private fun failure(error: Throwable, writeStarted: Boolean): ReservationFailure = when (error) {
        is ReservationOperationException -> error.failure
        is kotlinx.serialization.SerializationException -> ReservationFailure(
            ReservationErrorKind.SERVER,
            "Could not read the server response. Refresh to check the latest reservation.",
            writeStarted
        )
        is IllegalArgumentException, is IllegalStateException -> ReservationFailure(
            ReservationErrorKind.VALIDATION,
            error.message ?: "Check the reservation details"
        )
        is ApiException -> ReservationFailure(
            kind = when (error.status) {
                401 -> ReservationErrorKind.SESSION
                403 -> ReservationErrorKind.PERMISSION
                404 -> ReservationErrorKind.NOT_FOUND
                409 -> ReservationErrorKind.CONFLICT
                in 400..499 -> ReservationErrorKind.VALIDATION
                else -> ReservationErrorKind.SERVER
            },
            message = error.message,
            operationMayHaveSucceeded = writeStarted && (error.status >= 500 || error.status == 408)
        )
        else -> ReservationFailure(
            ReservationErrorKind.CONNECTION,
            if (writeStarted) "The result is uncertain. Refresh and check before trying again." else "Could not load reservations. Check your connection and refresh.",
            writeStarted
        )
    }

    override fun onDispose() {
        work.cancel()
    }

    companion object {
        const val READ_PERMISSION = "RESERVATION_READ"
        const val WRITE_PERMISSION = "RESERVATION_MANAGE"
        // Fix bookings after the staff time limits or from an earlier day (Manager and above by default).
        const val CORRECT_PERMISSION = "RESERVATION_CORRECT"
        // Accept requests from big groups.
        const val APPROVE_PERMISSION = "RESERVATION_APPROVE"
        // Give part of kept money back as goodwill (Owner, or whoever the Owner allows).
        const val GOODWILL_PERMISSION = "PAYMENT_GOODWILL_REFUND"
        const val RESERVATION_PAGE_SIZE = 100
        // A guest up to 15 minutes late still shows as arriving.
        private val ARRIVAL_GRACE = 15.minutes
    }
}

data class ReservationNotice(val message: String, val isError: Boolean)

// True when the booking falls inside the loaded date range (or no range is set).
private fun ReservationListFilter.covers(reservation: Reservation): Boolean {
    val start = runCatching { kotlin.time.Instant.parse(reservation.reservationStart) }.getOrNull() ?: return true
    val from = from?.let { runCatching { kotlin.time.Instant.parse(it) }.getOrNull() } ?: return true
    val to = to?.let { runCatching { kotlin.time.Instant.parse(it) }.getOrNull() } ?: return true
    return start >= from && start < to
}
