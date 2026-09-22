package com.saporini.mobile_desktop.pos.tables.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class TableLayoutResponseDto(
    val restaurantId: String,
    val branchId: String,
    val floors: List<FloorSummaryDto> = emptyList(),
    val tables: List<TableLayoutItemResponseDto> = emptyList()
)

@Serializable
data class FloorSummaryDto(
    val name: String,
    val tableCount: Int,
    val positionedTableCount: Int
)

@Serializable
data class TableLayoutItemResponseDto(
    val tableId: String,
    val mergedIntoTableId: String? = null,
    val mergedTableIds: List<String> = emptyList(),
    val tableNumber: String,
    val name: String? = null,
    val capacity: Int,
    val effectiveCapacity: Int,
    val floor: String? = null,
    val positionX: Double? = null,
    val positionY: Double? = null,
    val rotationDegrees: Double = 0.0,
    val layoutScale: Double = 0.74,
    val shape: String,
    val status: String,
    val guestCount: Int? = null,
    val seatedAt: String? = null,
    val active: Boolean
)

@Serializable
data class UpdateTableLayoutRequestDto(
    val items: List<TableLayoutItemRequestDto>
)

@Serializable
data class TableLayoutItemRequestDto(
    val tableId: String,
    val floor: String? = null,
    val positionX: Double? = null,
    val positionY: Double? = null,
    val rotationDegrees: Double? = null,
    val layoutScale: Double? = null,
    val shape: String? = null
)
