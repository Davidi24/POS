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
    val moreComing: Boolean = false,
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
    val seatedReservationId: String? = null,
    val seatedReservationName: String? = null,
    val seatedReservationStart: String? = null,
    val seatedReservationPartySize: Int? = null,
    val seatedReservationOccasionIcon: String? = null,
    val seatedReservationOccasionName: String? = null,
    val currentOrderId: String? = null,
    val currentOrderNumber: String? = null,
    val currentOrderStatus: String? = null,
    val currentOrderFulfillmentStatus: String? = null,
    val currentOrderBillRequested: Boolean = false,
    // The guests have paid and are still at the table.
    val paid: Boolean = false,
    val currentOrderServedItems: Int = 0,
    val currentOrderTotalItems: Int = 0,
    val guestsMin: Int? = null,
    val guestsUsual: Int? = null,
    val guestsMax: Int? = null,
    val nextBookingClashAt: String? = null,
    val nextBookingSoon: Boolean = false,
    val bookingComing: Boolean = false,
    val moreTimeOptions: String? = null,
    val nextReservationCheckedInAt: String? = null,
    val nextReservationMovedFrom: String? = null,
    val autoFreeAt: String? = null,
    val autoFreeWarningAt: String? = null,
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

@Serializable
data class BookingTableOptionDto(
    val tableIds: List<String>,
    val tableNumbers: List<String>,
    val primaryTableId: String? = null,
    val totalCapacity: Int? = null
)

@Serializable
data class SpecialCaseActionDto(
    val id: String,
    val kind: String,
    val summary: String,
    val detail: String? = null,
    val tableNumber: String? = null,
    val guests: Int? = null,
    val orderId: String? = null,
    val happenedAt: String,
    val tableFree: Boolean = true
)

@Serializable
data class MoreTimeRequestDto(val minutes: Int)

@Serializable
data class MoveBookingRequestDto(val tableIds: List<String>, val primaryTableId: String? = null)

@Serializable
data class ReturnActionRequestDto(val tableId: String? = null)

@Serializable
data class UnmergeTablesRequestDto(val freeTableIds: List<String>, val keepTableId: String? = null)
