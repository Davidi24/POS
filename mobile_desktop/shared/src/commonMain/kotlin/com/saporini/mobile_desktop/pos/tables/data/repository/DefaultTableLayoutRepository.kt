package com.saporini.mobile_desktop.pos.tables.data.repository

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import com.saporini.mobile_desktop.pos.tables.data.dto.TableCategoryRequestDto
import com.saporini.mobile_desktop.pos.tables.domain.model.TableSection
import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.tables.data.api.TableLayoutApi
import com.saporini.mobile_desktop.pos.tables.data.dto.FloorLayoutRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.UpdateTableLayoutRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.UpdateTableStatusRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.toDomain
import com.saporini.mobile_desktop.pos.tables.data.dto.toRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.toCreateRequestDto
import com.saporini.mobile_desktop.pos.tables.domain.model.BranchTableLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableStatus
import com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository
import com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultTableLayoutRepository(
    private val api: TableLayoutApi,
    sessionManager: SessionManager? = null
) : TableLayoutRepository {

    private val monitorScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cacheMutex = Mutex()
    private val _layoutChanges =
        MutableSharedFlow<TableLayoutChange>(extraBufferCapacity = 8)
    override val layoutChanges: Flow<TableLayoutChange> =
        _layoutChanges.asSharedFlow()
    private val floorLayoutsByBranch =
        mutableMapOf<BranchKey, List<FloorLayout>>()
    private val tableLayoutsByBranch =
        mutableMapOf<BranchKey, BranchTableLayout>()
    private val planImagesByUrl =
        mutableMapOf<String, ByteArray>()

    init {
        if (sessionManager != null) {
            monitorScope.launch {
                sessionManager.currentUser
                    .map { user ->
                        val restaurantId = user?.restaurantId
                        val branchId = user?.defaultBranchId
                        if (restaurantId == null || branchId == null) {
                            null
                        } else {
                            BranchKey(restaurantId, branchId)
                        }
                    }
                    .distinctUntilChanged()
                    .collectLatest { key ->
                        if (key == null) {
                            return@collectLatest
                        }

                        api.observeLayoutChanges(
                            restaurantId = key.restaurantId,
                            branchId = key.branchId
                        ).collect {
                            invalidateFromServer(key)
                        }
                    }
            }
        }
    }

    override suspend fun downloadPlanImage(url: String): ByteArray {
        val cached = cacheMutex.withLock {
            planImagesByUrl[url]
        }
        if (cached != null) {
            return cached
        }

        val downloaded = api.downloadImage(url)
        return cacheMutex.withLock {
            planImagesByUrl.getOrPut(url) { downloaded }
        }
    }

    override suspend fun getFloorLayouts(
        restaurantId: String,
        branchId: String
    ): List<FloorLayout> {
        val key = BranchKey(restaurantId, branchId)
        val cached = cacheMutex.withLock {
            floorLayoutsByBranch[key]
        }
        if (cached != null) {
            return cached
        }

        val loaded = api.getFloorLayouts(
            restaurantId = restaurantId,
            branchId = branchId
        ).map { response ->
            response.toDomain(ApiConfig.BASE_URL)
        }

        return cacheMutex.withLock {
            floorLayoutsByBranch.getOrPut(key) { loaded }
        }
    }

    override suspend fun createFloorLayout(
        restaurantId: String,
        branchId: String,
        floorName: String
    ): FloorLayout {
        val saved = api.createFloorLayout(
            restaurantId = restaurantId,
            branchId = branchId,
            request = FloorLayoutRequestDto(
                floorName = floorName
            )
        ).toDomain(ApiConfig.BASE_URL)

        cacheMutex.withLock {
            replaceCachedFloor(
                BranchKey(restaurantId, branchId),
                saved
            )
        }
        return saved
    }

    override suspend fun updateFloorLayout(
        restaurantId: String,
        branchId: String,
        floorLayout: FloorLayout
    ): FloorLayout {
        val saved = api.updateFloorLayout(
            restaurantId = restaurantId,
            branchId = branchId,
            floorLayoutId = floorLayout.id,
            request = floorLayout.toRequestDto()
        ).toDomain(ApiConfig.BASE_URL)

        cacheMutex.withLock {
            replaceCachedFloor(
                BranchKey(restaurantId, branchId),
                saved
            )
        }
        return saved
    }

    override suspend fun uploadPlanImage(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String,
        imageBytes: ByteArray,
        fileName: String,
        contentType: String
    ): FloorLayout {
        val key = BranchKey(restaurantId, branchId)
        val previousImageUrl = cacheMutex.withLock {
            floorLayoutsByBranch[key]
                ?.firstOrNull { it.id == floorLayoutId }
                ?.planImageUrl
        }

        val saved = api.uploadPlanImage(
            restaurantId = restaurantId,
            branchId = branchId,
            floorLayoutId = floorLayoutId,
            imageBytes = imageBytes,
            fileName = fileName,
            contentType = contentType
        ).toDomain(ApiConfig.BASE_URL)

        cacheMutex.withLock {
            replaceCachedFloor(key, saved)
            previousImageUrl?.let(planImagesByUrl::remove)
            saved.planImageUrl?.let { url ->
                planImagesByUrl[url] = imageBytes
            }
        }
        return saved
    }

    override suspend fun removePlanImage(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String
    ): FloorLayout {
        val key = BranchKey(restaurantId, branchId)
        val previousImageUrl = cacheMutex.withLock {
            floorLayoutsByBranch[key]
                ?.firstOrNull { it.id == floorLayoutId }
                ?.planImageUrl
        }

        val saved = api.removePlanImage(
            restaurantId = restaurantId,
            branchId = branchId,
            floorLayoutId = floorLayoutId
        ).toDomain(ApiConfig.BASE_URL)

        cacheMutex.withLock {
            replaceCachedFloor(key, saved)
            previousImageUrl?.let(planImagesByUrl::remove)
        }
        return saved
    }

    override suspend fun deleteFloorLayout(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String
    ) {
        api.deleteFloorLayout(
            restaurantId = restaurantId,
            branchId = branchId,
            floorLayoutId = floorLayoutId
        )

        cacheMutex.withLock {
            val key = BranchKey(restaurantId, branchId)
            val cached = floorLayoutsByBranch[key]
            val removed = cached
                ?.firstOrNull { it.id == floorLayoutId }
            if (cached != null) {
                floorLayoutsByBranch[key] =
                    cached.filterNot { it.id == floorLayoutId }
            }
            removed?.planImageUrl?.let(planImagesByUrl::remove)
            tableLayoutsByBranch.remove(key)
        }
    }

    override suspend fun getTableLayout(
        restaurantId: String,
        branchId: String
    ): BranchTableLayout {
        val key = BranchKey(restaurantId, branchId)
        val cached = cacheMutex.withLock {
            tableLayoutsByBranch[key]
        }
        if (cached != null) {
            return cached
        }

        val loaded = api.getTableLayout(
            restaurantId = restaurantId,
            branchId = branchId
        ).toDomain()

        return cacheMutex.withLock {
            tableLayoutsByBranch.getOrPut(key) { loaded }
        }
    }

    override suspend fun saveTableLayout(
        restaurantId: String,
        branchId: String,
        tables: List<LayoutTable>
    ): BranchTableLayout {
        val saved = api.updateTableLayout(
            restaurantId = restaurantId,
            branchId = branchId,
            request = UpdateTableLayoutRequestDto(
                items = tables.map { it.toRequestDto() }
            )
        ).toDomain()

        cacheMutex.withLock {
            tableLayoutsByBranch[
                BranchKey(restaurantId, branchId)
            ] = saved
        }
        return saved
    }

    override suspend fun createTable(
        restaurantId: String,
        branchId: String,
        table: LayoutTable
    ): LayoutTable {
        val created = api.createTable(
            restaurantId = restaurantId,
            branchId = branchId,
            request = table.toCreateRequestDto()
        ).toDomain()

        cacheMutex.withLock {
            tableLayoutsByBranch.remove(
                BranchKey(restaurantId, branchId)
            )
        }
        return created
    }

    override suspend fun deleteTable(
        restaurantId: String,
        branchId: String,
        tableId: String
    ) {
        api.deleteTable(restaurantId, branchId, tableId)

        cacheMutex.withLock {
            tableLayoutsByBranch.remove(
                BranchKey(restaurantId, branchId)
            )
        }
    }

    override suspend fun updateTableStatus(
        restaurantId: String,
        branchId: String,
        tableId: String,
        status: LayoutTableStatus,
        guestCount: Int?,
        moreComing: Boolean?
    ): LayoutTable {
        val updated = api.updateTableStatus(
            restaurantId = restaurantId,
            branchId = branchId,
            tableId = tableId,
            request = UpdateTableStatusRequestDto(
                status = status.name,
                guestCount = guestCount,
                moreComing = moreComing
            )
        ).toDomain()

        cacheMutex.withLock {
            val key = BranchKey(restaurantId, branchId)
            val cached = tableLayoutsByBranch[key]
            if (cached != null) {
                tableLayoutsByBranch[key] = cached.copy(
                    tables = cached.tables.map { table ->
                        if (table.id == updated.id) updated else table
                    }
                )
            }
        }
        return updated
    }

    override suspend fun saveTableMerge(
        restaurantId: String,
        branchId: String,
        primaryTableId: String,
        childTableIds: List<String>,
        tablesToUnmerge: List<String>
    ): BranchTableLayout {
        tablesToUnmerge
            .distinct()
            .forEach { tableId ->
                api.unmergeTables(
                    restaurantId = restaurantId,
                    branchId = branchId,
                    primaryTableId = tableId
                )
            }

        if (childTableIds.isNotEmpty()) {
            api.mergeTables(
                restaurantId = restaurantId,
                branchId = branchId,
                primaryTableId = primaryTableId,
                childTableIds = childTableIds
            )
        }

        val saved = api.getTableLayout(
            restaurantId = restaurantId,
            branchId = branchId
        ).toDomain()
        cacheMutex.withLock {
            tableLayoutsByBranch[
                BranchKey(restaurantId, branchId)
            ] = saved
        }
        return saved
    }

    private suspend fun layoutAfter(restaurantId: String, branchId: String, write: suspend () -> Unit): BranchTableLayout {
        write()
        return api.getTableLayout(restaurantId = restaurantId, branchId = branchId).toDomain()
    }

    override suspend fun giveMoreTime(restaurantId: String, branchId: String, tableId: String, minutes: Int) =
        layoutAfter(restaurantId, branchId) { api.giveMoreTime(restaurantId, branchId, tableId, minutes) }

    override suspend fun stillHere(restaurantId: String, branchId: String, tableId: String) =
        layoutAfter(restaurantId, branchId) { api.stillHere(restaurantId, branchId, tableId) }

    override suspend fun bookingWaits(restaurantId: String, branchId: String, reservationId: String) =
        layoutAfter(restaurantId, branchId) { api.checkInBooking(restaurantId, reservationId) }

    override suspend fun bookingOptions(restaurantId: String, branchId: String, tableId: String) =
        api.bookingOptions(restaurantId, branchId, tableId).map {
            com.saporini.mobile_desktop.pos.tables.domain.model.BookingTableOption(it.tableIds, it.tableNumbers, it.primaryTableId, it.totalCapacity)
        }

    override suspend fun moveBooking(restaurantId: String, branchId: String, tableId: String, option: com.saporini.mobile_desktop.pos.tables.domain.model.BookingTableOption) =
        layoutAfter(restaurantId, branchId) { api.moveBooking(restaurantId, branchId, tableId, option.tableIds, option.primaryTableId) }

    override suspend fun specialCaseActions(restaurantId: String, branchId: String) =
        api.specialCaseActions(restaurantId, branchId).map {
            com.saporini.mobile_desktop.pos.tables.domain.model.SpecialCaseAction(it.id, it.kind, it.summary, it.detail, it.tableNumber, it.guests, it.orderId, it.happenedAt, it.tableFree)
        }

    override suspend fun returnSpecialCaseAction(restaurantId: String, branchId: String, actionId: String, tableId: String?) =
        layoutAfter(restaurantId, branchId) { api.returnSpecialCaseAction(restaurantId, branchId, actionId, tableId) }

    override suspend fun setBillRequested(
        restaurantId: String,
        branchId: String,
        orderId: String,
        requested: Boolean
    ): BranchTableLayout {
        api.setBillRequested(restaurantId, orderId, requested)

        val saved = api.getTableLayout(
            restaurantId = restaurantId,
            branchId = branchId
        ).toDomain()
        cacheMutex.withLock {
            tableLayoutsByBranch[
                BranchKey(restaurantId, branchId)
            ] = saved
        }
        return saved
    }

    override suspend fun unmergeSome(restaurantId: String, branchId: String, tableId: String, freeTableIds: List<String>, keepTableId: String?) =
        layoutAfter(restaurantId, branchId) { api.unmergeSome(restaurantId, branchId, tableId, freeTableIds, keepTableId) }

    override suspend fun separateTable(
        restaurantId: String,
        branchId: String,
        primaryTableId: String
    ): BranchTableLayout {
        api.unmergeTables(
            restaurantId = restaurantId,
            branchId = branchId,
            primaryTableId = primaryTableId
        )

        val saved = api.getTableLayout(
            restaurantId = restaurantId,
            branchId = branchId
        ).toDomain()
        cacheMutex.withLock {
            tableLayoutsByBranch[
                BranchKey(restaurantId, branchId)
            ] = saved
        }
        return saved
    }

    override suspend fun getTableSections(
        restaurantId: String,
        branchId: String
    ): List<TableSection> = coroutineScope {
        val categories = async { api.getTableCategories(restaurantId, branchId) }
        val tables = async { api.getTables(restaurantId, branchId) }
        val tableIdsByCategory = tables.await()
            .filter { it.categoryId != null }
            .groupBy({ it.categoryId!! }, { it.id })
        categories.await().map { category ->
            TableSection(
                id = category.id,
                code = category.code,
                name = category.name,
                displayOrder = category.displayOrder,
                tableIds = tableIdsByCategory[category.id].orEmpty().toSet()
            )
        }
    }

    override suspend fun saveTableSection(
        restaurantId: String,
        branchId: String,
        sectionId: String?,
        name: String,
        displayOrder: Int,
        tableIds: Set<String>
    ): TableSection {
        val request = TableCategoryRequestDto(
            code = name.toSectionCode(),
            name = name,
            defaultCapacity = 2,
            displayOrder = displayOrder,
            active = true
        )
        val saved = if (sectionId == null) {
            api.createTableCategory(restaurantId, branchId, request)
        } else {
            api.updateTableCategory(restaurantId, branchId, sectionId, request)
        }
        api.replaceTableCategoryTables(restaurantId, branchId, saved.id, tableIds.toList())
        return TableSection(
            id = saved.id,
            code = saved.code,
            name = saved.name,
            displayOrder = saved.displayOrder,
            tableIds = tableIds
        )
    }

    override suspend fun deleteTableSection(
        restaurantId: String,
        branchId: String,
        sectionId: String
    ) {
        // The backend refuses to delete a category that still has tables.
        api.replaceTableCategoryTables(restaurantId, branchId, sectionId, emptyList())
        api.deleteTableCategory(restaurantId, branchId, sectionId)
    }

    override suspend fun setTableSectionTables(
        restaurantId: String,
        branchId: String,
        sectionId: String,
        tableIds: Set<String>
    ) {
        api.replaceTableCategoryTables(restaurantId, branchId, sectionId, tableIds.toList())
    }

    override suspend fun getFreeTableIds(
        restaurantId: String,
        branchId: String,
        from: String,
        to: String
    ): Set<String> = api.getAvailableTables(restaurantId, branchId, from, to)
        .filter { it.availableForRequestedWindow != false }
        .map { it.tableId }
        .toSet()

    override suspend fun reorderTableSections(
        restaurantId: String,
        branchId: String,
        sectionIds: List<String>
    ) {
        api.reorderTableCategories(restaurantId, branchId, sectionIds)
    }

    private fun String.toSectionCode(): String =
        trim().uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_').take(50).ifEmpty { "SECTION" }

    private fun replaceCachedFloor(
        key: BranchKey,
        floorLayout: FloorLayout
    ) {
        val cached = floorLayoutsByBranch[key] ?: return
        floorLayoutsByBranch[key] =
            if (cached.any { it.id == floorLayout.id }) {
                cached.map {
                    if (it.id == floorLayout.id) floorLayout else it
                }
            } else {
                cached + floorLayout
            }
    }

    internal suspend fun invalidateFromServer(
        restaurantId: String,
        branchId: String
    ) {
        invalidateFromServer(BranchKey(restaurantId, branchId))
    }

    private suspend fun invalidateFromServer(key: BranchKey) {
        cacheMutex.withLock {
            floorLayoutsByBranch.remove(key)
                .orEmpty()
                .mapNotNull { it.planImageUrl }
                .forEach(planImagesByUrl::remove)
            tableLayoutsByBranch.remove(key)
        }

        _layoutChanges.emit(
            TableLayoutChange(
                restaurantId = key.restaurantId,
                branchId = key.branchId
            )
        )
    }

    private data class BranchKey(
        val restaurantId: String,
        val branchId: String
    )
}
