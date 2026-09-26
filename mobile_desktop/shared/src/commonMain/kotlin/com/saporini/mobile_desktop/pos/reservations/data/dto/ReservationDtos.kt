package com.saporini.mobile_desktop.pos.reservations.data.dto

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationDepositStatus
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationSource
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import kotlinx.serialization.Serializable

@Serializable
data class ReservationRequestDto(
    val branchId: String? = null,
    val customerId: String? = null,
    val source: ReservationSource? = null,
    val partySize: Int,
    val reservationStart: String,
    val reservationEnd: String,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val seatingPreference: String? = null,
    val specialRequests: String? = null,
    val internalNotes: String? = null,
    val depositRequired: Boolean? = null,
    val depositAmount: OrderDecimal? = null,
    val initialTableIds: List<String>? = null,
    val primaryTableId: String? = null
)

@Serializable
data class UpdateReservationRequestDto(
    val branchId: String? = null,
    val customerId: String? = null,
    val source: ReservationSource? = null,
    val partySize: Int? = null,
    val reservationStart: String? = null,
    val reservationEnd: String? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val seatingPreference: String? = null,
    val specialRequests: String? = null,
    val internalNotes: String? = null,
    val depositRequired: Boolean? = null,
    val depositAmount: OrderDecimal? = null,
    val tableIds: List<String>? = null,
    val primaryTableId: String? = null
)

@Serializable
data class ReservationResponseDto(
    val id: String,
    val restaurantId: String,
    val branchId: String,
    val customerId: String? = null,
    val customerCode: String? = null,
    val customerName: String? = null,
    val reservationCode: String? = null,
    val source: ReservationSource? = null,
    val status: ReservationStatus,
    val partySize: Int,
    val reservationStart: String,
    val reservationEnd: String,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val seatingPreference: String? = null,
    val specialRequests: String? = null,
    val internalNotes: String? = null,
    val depositRequired: Boolean? = null,
    val depositAmount: OrderDecimal? = null,
    val depositStatus: ReservationDepositStatus? = null,
    val confirmedAt: String? = null,
    val cancelledAt: String? = null,
    val cancellationReason: String? = null,
    val checkedInAt: String? = null,
    val seatedAt: String? = null,
    val completedAt: String? = null,
    val noShowAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val tableAssignments: List<ReservationTableAssignmentResponseDto>? = null
)

@Serializable
data class ReservationTableAssignmentResponseDto(
    val assignmentId: String,
    val tableId: String,
    val tableNumber: String? = null,
    val tableName: String? = null,
    val floor: String? = null,
    val capacity: Int? = null,
    val primary: Boolean? = null,
    val assignedAt: String? = null,
    val assignedBy: String? = null
)

@Serializable
data class ReservationActionRequestDto(
    val reason: String? = null
)

@Serializable
data class ReservationNoteRequestDto(
    val note: String
)

@Serializable
data class ReservationNoteResponseDto(
    val id: String,
    val note: String,
    val createdBy: String? = null,
    val createdByName: String? = null,
    val updatedBy: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class UpdateReservationTablesRequestDto(
    val tableIds: List<String>,
    val primaryTableId: String? = null
)

@Serializable
data class UpdateReservationDepositRequestDto(
    val depositRequired: Boolean? = null,
    val depositAmount: OrderDecimal? = null
)

@Serializable
data class ReservationDepositResponseDto(
    val reservationId: String,
    val depositRequired: Boolean? = null,
    val depositAmount: OrderDecimal? = null,
    val depositStatus: ReservationDepositStatus? = null
)

@Serializable
data class ReservationAvailabilitySearchRequestDto(
    val reservationStart: String,
    val reservationEnd: String,
    val partySize: Int,
    val maxOptions: Int? = null
)

@Serializable
data class ReservationAvailabilityOptionResponseDto(
    val tableIds: List<String>? = null,
    val tableNumbers: List<String>? = null,
    val primaryTableId: String? = null,
    val tableCount: Int? = null,
    val totalCapacity: Int? = null,
    val exactFit: Boolean? = null
)

@Serializable
data class ReservationValidationRequestDto(
    val reservationStart: String,
    val reservationEnd: String,
    val partySize: Int,
    val tableIds: List<String>? = null,
    val primaryTableId: String? = null
)

@Serializable
data class ReservationValidationResponseDto(
    val valid: Boolean? = null,
    val errors: List<String>? = null,
    val warnings: List<String>? = null,
    val suggestions: List<ReservationAvailabilityOptionResponseDto>? = null
)

@Serializable
data class ReservationSummaryResponseDto(
    val branchId: String,
    val from: String? = null,
    val to: String? = null,
    val totalReservations: Int? = null,
    val totalGuests: Int? = null,
    val pendingCount: Int? = null,
    val confirmedCount: Int? = null,
    val checkedInCount: Int? = null,
    val seatedCount: Int? = null,
    val completedCount: Int? = null,
    val cancelledCount: Int? = null,
    val noShowCount: Int? = null,
    val upcomingCount: Int? = null
)

@Serializable
data class ReservationSettingsResponseDto(
    val branchId: String? = null,
    val timezone: String? = null,
    val ruleName: String? = null,
    val defaultDurationMinutes: Int? = null,
    val bufferMinutes: Int? = null,
    val minPartySize: Int? = null,
    val maxPartySize: Int? = null,
    val advanceBookingDays: Int? = null
)

@Serializable
data class ReservationCapacityResponseDto(
    val branchId: String,
    val from: String? = null,
    val to: String? = null,
    val totalRootTables: Int? = null,
    val availableRootTables: Int? = null,
    val totalSeats: Int? = null,
    val availableSeats: Int? = null,
    val maxSingleTableCapacity: Int? = null,
    val maxAvailableTableCapacity: Int? = null,
    val requestedPartySize: Int? = null,
    val canAccommodateRequestedPartySize: Boolean? = null
)

@Serializable
data class ReservationStatusHistoryResponseDto(
    val id: String,
    val oldStatus: ReservationStatus? = null,
    val newStatus: ReservationStatus? = null,
    val reason: String? = null,
    val changedBy: String? = null,
    val changedAt: String? = null
)

@Serializable
data class ReservationTimelineEventResponseDto(
    val id: String,
    val type: String? = null,
    val occurredAt: String? = null,
    val actorId: String? = null,
    val message: String? = null,
    val oldStatus: ReservationStatus? = null,
    val newStatus: ReservationStatus? = null,
    val relatedTableId: String? = null,
    val relatedNoteId: String? = null
)

@Serializable
data class ReservationAuditResponseDto(
    val reservationId: String,
    val createdAt: String? = null,
    val createdBy: String? = null,
    val updatedAt: String? = null,
    val updatedBy: String? = null,
    val statusHistory: List<ReservationStatusHistoryResponseDto>? = null,
    val notes: List<ReservationNoteResponseDto>? = null,
    val tableAssignments: List<ReservationTableAssignmentResponseDto>? = null
)
