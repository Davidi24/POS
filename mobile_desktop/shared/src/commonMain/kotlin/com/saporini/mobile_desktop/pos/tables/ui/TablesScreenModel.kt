package com.saporini.mobile_desktop.pos.tables.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableStatus
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
    private val sessionManager: SessionManager
) : ScreenModel {

    private val _state = MutableStateFlow(TablesUiState())
    val state: StateFlow<TablesUiState> = _state.asStateFlow()
    private val saveMutex = Mutex()
    private var loadJob: Job? = null

    init {
        load()
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

    fun saveFloor(floorLayout: FloorLayout) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val saved = repository.updateFloorLayout(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                floorLayout = floorLayout
            )

            replaceFloor(saved)
        }
    }

    fun uploadPlanImage(
        floorName: String,
        imageBytes: ByteArray,
        fileName: String,
        contentType: String
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
        }
    }

    fun removePlanImage(floorLayoutId: String) {
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

    fun saveTables(tables: List<LayoutTable>) {
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

    private fun launchSaving(block: suspend () -> Unit) {
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
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = error.message
                            ?: "Could not save the table layout"
                    )
                }
            }
        }
    }

    fun seatGuests(tableId: String, guestCount: Int) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val updated = repository.updateTableStatus(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                tableId = tableId,
                status = LayoutTableStatus.OCCUPIED,
                guestCount = guestCount.coerceAtLeast(1)
            )
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
    }

    fun saveTableMerge(
        primaryTableId: String,
        childTableIds: List<String>,
        previousPrimaryTableIds: List<String>
    ) {
        val scope = currentBranchScope() ?: return

        launchSaving {
            val saved = repository.saveTableMerge(
                restaurantId = scope.restaurantId,
                branchId = scope.branchId,
                primaryTableId = primaryTableId,
                childTableIds = childTableIds,
                previousPrimaryTableIds = previousPrimaryTableIds
            )
            _state.value = _state.value.copy(tableLayout = saved)
        }
    }

    private fun currentBranchScope(): BranchScope? {
        val user = sessionManager.currentUser.value
        val restaurantId = user?.restaurantId
        val branchId = user?.defaultBranchId

        if (restaurantId == null || branchId == null) {
            _state.value = _state.value.copy(
                isLoading = false,
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
