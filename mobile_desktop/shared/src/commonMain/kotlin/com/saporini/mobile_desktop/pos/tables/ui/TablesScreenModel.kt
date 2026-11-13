package com.saporini.mobile_desktop.pos.tables.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderTransferTableInput
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderRepository as PosOrderRepository
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableStatus
import com.saporini.mobile_desktop.pos.tables.domain.model.TableSection
import com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TablesScreenModel(
    private val repository: TableLayoutRepository,
    private val orderRepository: PosOrderRepository,
    private val sessionManager: SessionManager
) : ScreenModel {

    private val _state = MutableStateFlow(TablesUiState())
    val state: StateFlow<TablesUiState> = _state.asStateFlow()
    private val saveMutex = Mutex()
    private var loadJob: Job? = null

    init {
        // Back on Tables: the tables seen last show at once and refresh quietly behind (opening Tables showed the
        // loading shapes every time; user, 2026-10-31).
        val cached = LastTables.state?.takeIf { LastTables.key != null && LastTables.key == cacheKey() }
        if (cached != null) {
            _state.value = cached
            load(showLoading = false)
        } else {
            load()
        }
        screenModelScope.launch {
            _state.collect { latest ->
                val key = cacheKey()
                if (key != null && latest.loaded && latest.errorMessage == null && latest.tableLayout != null) {
                    LastTables.key = key
                    LastTables.state = latest.copy(isLoading = false, isSaving = false)
                }
            }
        }
        screenModelScope.launch {
            repository.layoutChanges.collectLatest { change ->
                val scope = currentBranchScope()
                    ?: return@collectLatest
                if (
                    change.restaurantId != scope.restaurantId ||
                    change.branchId != scope.branchId
                ) {
                    return@collectLatest
                }

                delay(250)
                while (_state.value.isSaving) {
                    delay(100)
                }
                load(showLoading = false)
            }
        }
    }

    fun load() {
        load(showLoading = true)
    }

    private fun cacheKey(): String? {
        val user = sessionManager.currentUser.value ?: return null
        val restaurantId = user.restaurantId ?: return null
        val branchId = user.defaultBranchId ?: return null
        return "${user.id}/$restaurantId/$branchId"
    }

    private fun load(showLoading: Boolean) {
        val scope = currentBranchScope() ?: return

        loadJob?.cancel()
        loadJob = screenModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = showLoading,
                errorMessage = null
            )

            try {
                val result = coroutineScope {
                    val floors = async {
                        repository.getFloorLayouts(
                            restaurantId = scope.restaurantId,
                            branchId = scope.branchId
                        )
                    }

                    val tables = async {
                        repository.getTableLayout(
                            restaurantId = scope.restaurantId,
                            branchId = scope.branchId
                        )
                    }

                    floors.await() to tables.await()
                }
                val images = coroutineScope {
                    result.first.mapNotNull { floor ->
                        floor.planImageUrl?.let { url ->
                            async {
                                runCatching {
                                    floor.id to repository.downloadPlanImage(url)
                                }.getOrNull()
                            }
                        }
                    }.mapNotNull { it.await() }.toMap()
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    loaded = true,
                    floorLayouts = result.first,
                    planImageBytes = images,
                    tableLayout = result.second,
                    errorMessage = null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    loaded = true,
                    errorMessage = error.message
                        ?: "Could not load the table layout"
                )
            }
        }
    }

    fun createFloor(floorName: String) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val floor = repository.createFloorLayout(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                floorName = floorName
            )

            replaceFloor(floor)
        }
    }

    fun saveFloor(floorLayout: FloorLayout, onSuccess: () -> Unit = {}) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val saved = repository.updateFloorLayout(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                floorLayout = floorLayout
            )

            replaceFloor(saved)
            onSuccess()
        }
    }

    fun uploadPlanImage(
        floorName: String,
        imageBytes: ByteArray,
        fileName: String,
        contentType: String,
        onSuccess: () -> Unit = {}
    ) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val floorLayout = _state.value.floorLayouts
                .firstOrNull { it.floorName == floorName }
                ?: repository.createFloorLayout(
                    restaurantId = scope.restaurantId,
                    branchId = scope.branchId,
                    floorName = floorName
                ).also(::replaceFloor)

            val saved = repository.uploadPlanImage(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                floorLayoutId = floorLayout.id,
                imageBytes = imageBytes,
                fileName = fileName,
                contentType = contentType
            )

            replaceFloor(saved)
            _state.value = _state.value.copy(
                planImageBytes = _state.value.planImageBytes + (saved.id to imageBytes)
            )
            onSuccess()
        }
    }

    fun removePlanImage(floorLayoutId: String, onSuccess: () -> Unit = {}) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val saved = repository.removePlanImage(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                floorLayoutId = floorLayoutId
            )

            replaceFloor(saved)
            _state.value = _state.value.copy(
                planImageBytes = _state.value.planImageBytes - saved.id
            )
            onSuccess()
        }
    }

    fun deleteFloor(floorLayoutId: String) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            repository.deleteFloorLayout(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                floorLayoutId = floorLayoutId
            )

            _state.value = _state.value.copy(
                floorLayouts = _state.value.floorLayouts.filterNot {
                    it.id == floorLayoutId
                },
                planImageBytes = _state.value.planImageBytes - floorLayoutId
            )
        }
    }

    fun saveTables(tables: List<LayoutTable>, onSuccess: () -> Unit = {}) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            tables.mapNotNull { it.floor }.distinct().forEach { floorName ->
                if (_state.value.floorLayouts.none { it.floorName == floorName }) {
                    replaceFloor(
                        repository.createFloorLayout(
                            restaurantId = scope.restaurantId,
                            branchId = scope.branchId,
                            floorName = floorName
                        )
                    )
                }
            }

            val previousIds = _state.value.tableLayout
                ?.tables
                .orEmpty()
                .map { it.id }
                .toSet()
            val retainedIds = tables.map { it.id }.filter { it.isNotBlank() }.toSet()

            (previousIds - retainedIds).forEach { tableId ->
                repository.deleteTable(scope.restaurantId, scope.branchId, tableId)
            }

            val persistedTables = tables.map { table ->
                if (table.id.isBlank()) {
                    repository.createTable(
                        restaurantId = scope.restaurantId,
                        branchId = scope.branchId,
                        table = table
                    )
                } else {
                    table
                }
            }

            val saved = repository.saveTableLayout(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                tables = persistedTables
            )

            _state.value = _state.value.copy(
                tableLayout = saved
            )
            onSuccess()
        }
    }

    fun createTables(tables: List<LayoutTable>, onSuccess: (List<LayoutTable>) -> Unit = {}) {
        val scope = currentBranchScope() ?: return
        val tablesToCreate = tables.filter { it.id.isBlank() }
        if (tablesToCreate.isEmpty()) {
            onSuccess(emptyList())
            return
        }

        launchSaving {
            tablesToCreate.mapNotNull { it.floor }.distinct().forEach { floorName ->
                if (_state.value.floorLayouts.none { it.floorName == floorName }) {
                    replaceFloor(
                        repository.createFloorLayout(
                            restaurantId = scope.restaurantId,
                            branchId = scope.branchId,
                            floorName = floorName
                        )
                    )
                }
            }

            val created = tablesToCreate.map { table ->
                repository.createTable(
                    restaurantId = scope.restaurantId,
                    branchId = scope.branchId,
                    table = table
                )
            }

            _state.value.tableLayout?.let { currentLayout ->
                _state.value = _state.value.copy(
                    tableLayout = currentLayout.copy(
                        tables = currentLayout.tables + created
                    )
                )
            }
            onSuccess(created)
        }
    }

    fun deleteTables(
        tableIds: List<String>,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        val scope = currentBranchScope() ?: return
        val idsToDelete = tableIds.filter { it.isNotBlank() }.distinct()
        if (idsToDelete.isEmpty()) {
            onFailure("Could not delete this table")
            return
        }

        launchSaving(showError = false, onFailure = onFailure) {
            idsToDelete.forEach { tableId ->
                repository.deleteTable(
                    restaurantId = scope.restaurantId,
                    branchId = scope.branchId,
                    tableId = tableId
                )
            }

            _state.value.tableLayout?.let { currentLayout ->
                _state.value = _state.value.copy(
                    tableLayout = currentLayout.copy(
                        tables = currentLayout.tables.filterNot { it.id in idsToDelete }
                    )
                )
            }
            onSuccess()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun replaceFloor(floorLayout: FloorLayout) {
        val existing = _state.value.floorLayouts

        val updated = if (existing.any { it.id == floorLayout.id }) {
            existing.map {
                if (it.id == floorLayout.id) floorLayout else it
            }
        } else {
            existing + floorLayout
        }

        _state.value = _state.value.copy(
            floorLayouts = updated.sortedBy { it.floorName }
        )
    }

    private fun launchSaving(
        showError: Boolean = true,
        onFailure: (String) -> Unit = {},
        block: suspend () -> Unit
    ) {
        screenModelScope.launch {
            saveMutex.withLock {
                _state.value = _state.value.copy(
                    isSaving = true,
                    errorMessage = null
                )

                try {
                    block()

                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = null
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    val message = error.message ?: "Could not save the table layout"
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = if (showError) message else null
                    )
                    onFailure(message)
                }
            }
        }
    }

    fun seatGuests(tableId: String, guestCount: Int, moreComing: Boolean? = null, onSuccess: () -> Unit = {}) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val updated = repository.updateTableStatus(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                tableId = tableId,
                status = LayoutTableStatus.OCCUPIED,
                guestCount = guestCount.coerceAtLeast(1),
                moreComing = moreComing
            )
            replaceCachedTable(updated)
            // Merged tables get their share of the guests too.
            load(showLoading = false)
            onSuccess()
        }
    }

    fun saveTableMerge(
        primaryTableId: String,
        childTableIds: List<String>,
        tablesToUnmerge: List<String>,
        onSuccess: () -> Unit = {}
    ) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val saved = repository.saveTableMerge(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                primaryTableId = primaryTableId,
                childTableIds = childTableIds,
                tablesToUnmerge = tablesToUnmerge
            )
            _state.value = _state.value.copy(tableLayout = saved)
            onSuccess()
        }
    }

    // ---- Settings → Tables → Special cases ----

    fun can(permission: String): Boolean = permission in sessionManager.currentUser.value?.permissions.orEmpty()

    fun loadSpecialActions() {
        val scope = currentBranchScope() ?: return
        screenModelScope.launch {
            runCatching { repository.specialCaseActions(scope.restaurantId, scope.branchId) }
                .onSuccess { actions -> _state.value = _state.value.copy(specialActions = actions) }
        }
    }

    fun giveMoreTime(tableId: String, minutes: Int, onSuccess: () -> Unit = {}) = specialCaseWrite(onSuccess) { scope ->
        repository.giveMoreTime(scope.restaurantId, scope.branchId, tableId, minutes)
    }

    fun stillHere(tableId: String, onSuccess: () -> Unit = {}) = specialCaseWrite(onSuccess) { scope ->
        repository.stillHere(scope.restaurantId, scope.branchId, tableId)
    }

    fun bookingWaits(reservationId: String, onSuccess: () -> Unit = {}) = specialCaseWrite(onSuccess) { scope ->
        repository.bookingWaits(scope.restaurantId, scope.branchId, reservationId)
    }

    fun moveBooking(tableId: String, option: com.saporini.mobile_desktop.pos.tables.domain.model.BookingTableOption, onSuccess: () -> Unit = {}) =
        specialCaseWrite(onSuccess) { scope -> repository.moveBooking(scope.restaurantId, scope.branchId, tableId, option) }

    fun returnSpecialAction(actionId: String, tableId: String?, onSuccess: () -> Unit = {}) = specialCaseWrite(onSuccess) { scope ->
        repository.returnSpecialCaseAction(scope.restaurantId, scope.branchId, actionId, tableId)
    }

    suspend fun bookingOptions(tableId: String): List<com.saporini.mobile_desktop.pos.tables.domain.model.BookingTableOption> {
        val scope = currentBranchScope() ?: return emptyList()
        return runCatching { repository.bookingOptions(scope.restaurantId, scope.branchId, tableId) }.getOrDefault(emptyList())
    }

    private fun specialCaseWrite(onSuccess: () -> Unit, write: suspend (BranchScope) -> com.saporini.mobile_desktop.pos.tables.domain.model.BranchTableLayout) {
        val scope = currentBranchScope() ?: return
        launchSaving {
            val saved = write(scope)
            _state.value = _state.value.copy(tableLayout = saved)
            loadSpecialActions()
            onSuccess()
        }
    }

    fun setBillRequested(orderId: String, requested: Boolean, onSuccess: () -> Unit = {}) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val saved = repository.setBillRequested(scope.restaurantId, scope.branchId, orderId, requested)
            _state.value = _state.value.copy(tableLayout = saved)
            onSuccess()
        }
    }

    fun unmergeSome(tableId: String, freeTableIds: List<String>, keepTableId: String?, onSuccess: () -> Unit = {}) = specialCaseWrite(onSuccess) { scope ->
        repository.unmergeSome(scope.restaurantId, scope.branchId, tableId, freeTableIds, keepTableId)
    }

    fun separateTable(primaryTableId: String, onSuccess: () -> Unit = {}) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val saved = repository.separateTable(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                primaryTableId = primaryTableId
            )
            _state.value = _state.value.copy(tableLayout = saved)
            onSuccess()
        }
    }

    fun updateGuestCount(tableId: String, guestCount: Int, moreComing: Boolean? = null, onSuccess: () -> Unit = {}) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val updated = repository.updateTableStatus(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                tableId = tableId,
                status = LayoutTableStatus.OCCUPIED,
                guestCount = guestCount.coerceAtLeast(1),
                moreComing = moreComing
            )
            replaceCachedTable(updated)
            // Merged tables get their share of the guests too.
            load(showLoading = false)
            onSuccess()
        }
    }

    fun setTableAvailability(tableId: String, available: Boolean, onSuccess: () -> Unit = {}) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val updated = repository.updateTableStatus(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                tableId = tableId,
                status = if (available) LayoutTableStatus.AVAILABLE else LayoutTableStatus.OUT_OF_SERVICE
            )
            replaceCachedTable(updated)
            onSuccess()
        }
    }

    fun moveGuests(sourceTableIds: List<String>, destinationTableId: String, guestCount: Int?, onSuccess: () -> Unit = {}) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val sourceIds = sourceTableIds
                .filter { it.isNotBlank() && it != destinationTableId }
                .distinct()
            val ordersToMove = sourceIds.mapNotNull { sourceTableId ->
                try {
                    orderRepository.getCurrentTableOrder(
                        restaurantId = scope.restaurantId,
                        branchId = scope.branchId,
                        tableId = sourceTableId
                    )
                } catch (error: ApiException) {
                    if (error.status == 404) null else throw error
                }
            }.distinctBy { it.id }

            ordersToMove.forEach { order ->
                orderRepository.transferOrderTable(
                    restaurantId = scope.restaurantId,
                    orderId = order.id,
                    request = OrderTransferTableInput(
                        tableId = destinationTableId,
                        note = "Guests moved to another table"
                    )
                )
            }

            val movedTo = repository.updateTableStatus(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                tableId = destinationTableId,
                status = LayoutTableStatus.OCCUPIED,
                guestCount = (guestCount ?: 1).coerceAtLeast(1)
            )
            val freedSources = sourceIds.map { sourceTableId ->
                repository.updateTableStatus(
                    restaurantId = scope.restaurantId,
                    branchId = scope.branchId,
                    tableId = sourceTableId,
                    status = LayoutTableStatus.AVAILABLE
                )
            }
            replaceCachedTable(movedTo)
            freedSources.forEach(::replaceCachedTable)
            load(showLoading = false)
            onSuccess()
        }
    }

    private fun replaceCachedTable(updated: LayoutTable) {
        val currentLayout = _state.value.tableLayout
        if (currentLayout != null) {
            _state.value = _state.value.copy(
                tableLayout = currentLayout.copy(
                    tables = currentLayout.tables.map { table ->
                        if (table.id == updated.id) updated else table
                    }
                )
            )
        }
    }

    suspend fun loadTableSections(): Result<List<TableSection>> {
        val scope = currentBranchScope() ?: return Result.failure(IllegalStateException("No branch selected"))
        return runSectionCall { repository.getTableSections(scope.restaurantId, scope.branchId) }
    }

    suspend fun saveTableSection(
        sectionId: String?,
        name: String,
        displayOrder: Int,
        tableIds: Set<String>
    ): Result<TableSection> {
        val scope = currentBranchScope() ?: return Result.failure(IllegalStateException("No branch selected"))
        return runSectionCall {
            repository.saveTableSection(scope.restaurantId, scope.branchId, sectionId, name, displayOrder, tableIds)
        }
    }

    suspend fun deleteTableSection(sectionId: String): Result<Unit> {
        val scope = currentBranchScope() ?: return Result.failure(IllegalStateException("No branch selected"))
        return runSectionCall { repository.deleteTableSection(scope.restaurantId, scope.branchId, sectionId) }
    }

    suspend fun setTableSectionTables(sectionId: String, tableIds: Set<String>): Result<Unit> {
        val scope = currentBranchScope() ?: return Result.failure(IllegalStateException("No branch selected"))
        return runSectionCall { repository.setTableSectionTables(scope.restaurantId, scope.branchId, sectionId, tableIds) }
    }

    suspend fun reorderTableSections(sectionIds: List<String>): Result<Unit> {
        val scope = currentBranchScope() ?: return Result.failure(IllegalStateException("No branch selected"))
        return runSectionCall { repository.reorderTableSections(scope.restaurantId, scope.branchId, sectionIds) }
    }

    private suspend fun <T> runSectionCall(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }

    private fun currentBranchScope(): BranchScope? {
        val user = sessionManager.currentUser.value
        val restaurantId = user?.restaurantId
        val branchId = user?.defaultBranchId

        if (restaurantId == null || branchId == null) {
            _state.value = _state.value.copy(
                isLoading = false,
                loaded = true,
                isSaving = false,
                errorMessage = "No restaurant branch is assigned to this user"
            )

            return null
        }

        return BranchScope(
            restaurantId = restaurantId,
            branchId = branchId
        )
    }

    private data class BranchScope(
        val restaurantId: String,
        val branchId: String
    )
}

// The tables last seen (one branch), kept while the app runs so Tables opens without waiting.
private object LastTables {
    var key: String? = null
    var state: TablesUiState? = null
}
