package com.saporini.mobile_desktop.pos.tables.domain.repository

import com.saporini.mobile_desktop.pos.tables.domain.model.BranchTableLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableStatus
import com.saporini.mobile_desktop.pos.tables.domain.model.TableSection
import kotlinx.coroutines.flow.Flow

data class TableLayoutChange(
    val restaurantId: String,
    val branchId: String
)

interface TableLayoutRepository {

    val layoutChanges: Flow<TableLayoutChange>

    suspend fun downloadPlanImage(url: String): ByteArray

    suspend fun getFloorLayouts(
        restaurantId: String,
        branchId: String
    ): List<FloorLayout>

    suspend fun createFloorLayout(
        restaurantId: String,
        branchId: String,
        floorName: String
    ): FloorLayout

    suspend fun updateFloorLayout(
        restaurantId: String,
        branchId: String,
        floorLayout: FloorLayout
    ): FloorLayout

    suspend fun uploadPlanImage(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String,
        imageBytes: ByteArray,
        fileName: String,
        contentType: String
    ): FloorLayout

    suspend fun removePlanImage(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String
    ): FloorLayout

    suspend fun deleteFloorLayout(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String
    )

    suspend fun getTableLayout(
        restaurantId: String,
        branchId: String
    ): BranchTableLayout

    suspend fun saveTableLayout(
        restaurantId: String,
        branchId: String,
        tables: List<LayoutTable>
    ): BranchTableLayout

    suspend fun createTable(
        restaurantId: String,
        branchId: String,
        table: LayoutTable
    ): LayoutTable

    suspend fun updateTableStatus(
        restaurantId: String,
        branchId: String,
        tableId: String,
        status: LayoutTableStatus,
        guestCount: Int? = null
    ): LayoutTable

    suspend fun saveTableMerge(
        restaurantId: String,
        branchId: String,
        primaryTableId: String,
        childTableIds: List<String>,
        previousPrimaryTableIds: List<String> = emptyList()
    ): BranchTableLayout

    suspend fun separateTable(
        restaurantId: String,
        branchId: String,
        primaryTableId: String
    ): BranchTableLayout

    suspend fun deleteTable(
        restaurantId: String,
        branchId: String,
        tableId: String
    )

    suspend fun getTableSections(
        restaurantId: String,
        branchId: String
    ): List<TableSection>

    suspend fun saveTableSection(
        restaurantId: String,
        branchId: String,
        sectionId: String?,
        name: String,
        displayOrder: Int,
        tableIds: Set<String>
    ): TableSection

    suspend fun deleteTableSection(
        restaurantId: String,
        branchId: String,
        sectionId: String
    )

    suspend fun setTableSectionTables(
        restaurantId: String,
        branchId: String,
        sectionId: String,
        tableIds: Set<String>
    )

    suspend fun getFreeTableIds(
        restaurantId: String,
        branchId: String,
        from: String,
        to: String
    ): Set<String>

    suspend fun reorderTableSections(
        restaurantId: String,
        branchId: String,
        sectionIds: List<String>
    )
}
