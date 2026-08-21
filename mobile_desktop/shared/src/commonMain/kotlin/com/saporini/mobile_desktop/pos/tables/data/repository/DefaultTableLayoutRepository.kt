package com.saporini.mobile_desktop.pos.tables.data.repository

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.tables.data.api.TableLayoutApi
import com.saporini.mobile_desktop.pos.tables.data.dto.FloorLayoutRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.UpdateTableLayoutRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.toDomain
import com.saporini.mobile_desktop.pos.tables.data.dto.toRequestDto
import com.saporini.mobile_desktop.pos.tables.domain.model.BranchTableLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository

class DefaultTableLayoutRepository(
    private val api: TableLayoutApi
) : TableLayoutRepository {

    override suspend fun getFloorLayouts(
        restaurantId: String,
        branchId: String
    ): List<FloorLayout> {
        return api.getFloorLayouts(
            restaurantId = restaurantId,
            branchId = branchId
        ).map { response ->
            response.toDomain(ApiConfig.BASE_URL)
        }
    }

    override suspend fun createFloorLayout(
        restaurantId: String,
        branchId: String,
        floorName: String
    ): FloorLayout {
        return api.createFloorLayout(
            restaurantId = restaurantId,
            branchId = branchId,
            request = FloorLayoutRequestDto(
                floorName = floorName
            )
        ).toDomain(ApiConfig.BASE_URL)
    }

    override suspend fun updateFloorLayout(
        restaurantId: String,
        branchId: String,
        floorLayout: FloorLayout
    ): FloorLayout {
        return api.updateFloorLayout(
            restaurantId = restaurantId,
            branchId = branchId,
            floorLayoutId = floorLayout.id,
            request = floorLayout.toRequestDto()
        ).toDomain(ApiConfig.BASE_URL)
    }

    override suspend fun uploadPlanImage(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String,
        imageBytes: ByteArray,
        fileName: String,
        contentType: String
    ): FloorLayout {
        return api.uploadPlanImage(
            restaurantId = restaurantId,
            branchId = branchId,
            floorLayoutId = floorLayoutId,
            imageBytes = imageBytes,
            fileName = fileName,
            contentType = contentType
        ).toDomain(ApiConfig.BASE_URL)
    }

    override suspend fun removePlanImage(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String
    ): FloorLayout {
        return api.removePlanImage(
            restaurantId = restaurantId,
            branchId = branchId,
            floorLayoutId = floorLayoutId
        ).toDomain(ApiConfig.BASE_URL)
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
    }

    override suspend fun getTableLayout(
        restaurantId: String,
        branchId: String
    ): BranchTableLayout {
        return api.getTableLayout(
            restaurantId = restaurantId,
            branchId = branchId
        ).toDomain()
    }

    override suspend fun saveTableLayout(
        restaurantId: String,
        branchId: String,
        tables: List<LayoutTable>
    ): BranchTableLayout {
        return api.updateTableLayout(
            restaurantId = restaurantId,
            branchId = branchId,
            request = UpdateTableLayoutRequestDto(
                items = tables.map { it.toRequestDto() }
            )
        ).toDomain()
    }
}