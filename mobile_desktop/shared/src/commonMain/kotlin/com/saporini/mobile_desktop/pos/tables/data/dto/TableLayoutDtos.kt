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
    val nextReservationStart: String? = null,
    val nextReservationEnd: String? = null,
    val nextReservationCode: String? = null,
    val nextReservationName: String? = null,
    val nextReservationId: String? = null,
    val nextReservationStatus: String? = null,
    val nextReservationHoldUntil: String? = null,
    val nextReservationHoldWarningAt: String? = null,
    val nextReservationPartySize: Int? = null,
    val nextReservationArrivedGuests: Int? = null,
    val nextReservationOccasionIcon: String? = null,
    val nextReservationOccasionName: String? = null,
    val currentOrderId: String? = null,
    val currentOrderNumber: String? = null,
    val currentOrderStatus: String? = null,
    val currentOrderFulfillmentStatus: String? = null,
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
