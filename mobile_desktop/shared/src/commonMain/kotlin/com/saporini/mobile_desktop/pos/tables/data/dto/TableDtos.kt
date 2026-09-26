package com.saporini.mobile_desktop.pos.tables.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class TableRequestDto(
    val tableNumber: String,
    val name: String? = null,
    val capacity: Int,
    val floor: String,
    val positionX: Double,
    val positionY: Double,
    val rotationDegrees: Double,
    val layoutScale: Double,
    val shape: String,
    val status: String = "AVAILABLE",
    val active: Boolean = true
)

@Serializable
data class TableResponseDto(
    val id: String,
    val categoryId: String? = null,
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
data class UpdateTableStatusRequestDto(
    val status: String,
    val guestCount: Int? = null
)

@Serializable
data class TableMergeRequestDto(
    val tableIds: List<String>
)

@Serializable
data class TableCategoryResponseDto(
    val id: String,
    val code: String,
    val name: String,
    val displayOrder: Int = 0,
    val active: Boolean = true
)

@Serializable
data class TableCategoryRequestDto(
    val code: String,
    val name: String,
    val defaultCapacity: Int,
    val displayOrder: Int,
    val active: Boolean
)

@Serializable
data class UpdateTableCategoryTablesRequestDto(
    val tableIds: List<String>
)

@Serializable
data class TableAvailabilityDto(
    val tableId: String,
    val tableNumber: String,
    val floor: String? = null,
    val availableForRequestedWindow: Boolean? = null
)

@Serializable
data class ReorderTableCategoriesRequestDto(
    val categoryIds: List<String>
)
