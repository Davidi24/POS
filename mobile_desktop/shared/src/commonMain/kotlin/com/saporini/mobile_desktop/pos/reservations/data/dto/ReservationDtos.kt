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
    val primaryTableId: String? = null,
    val attendanceConfirmed: Boolean? = null,
    val occasionCode: String? = null,
    val occasionOptions: List<String>? = null,
    val occasionNote: String? = null
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
    val primaryTableId: String? = null,
    val occasionCode: String? = null,
    val occasionOptions: List<String>? = null,
    val occasionNote: String? = null
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
    val expiredAt: String? = null,
    val holdUntil: String? = null,
    val arrivedGuests: Int? = null,
    val needsReview: Boolean? = null,
    val reviewReason: String? = null,
    val attendanceConfirmedAt: String? = null,
    val attendanceConfirmedVia: String? = null,
    val attendance: String? = null,
    val guestNoShows: Int? = null,
    val occasionCode: String? = null,
    val occasionName: String? = null,
    val occasionIcon: String? = null,
    val occasionOptions: List<String>? = null,
    val occasionNote: String? = null,
    val eventName: String? = null,
    val eventIcon: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val tableAssignments: List<ReservationTableAssignmentResponseDto>? = null
)

@Serializable
data class ReservationDetailsResponseDto(
    val reservation: ReservationResponseDto,
    val audit: ReservationAuditResponseDto? = null,
    val timeline: List<ReservationTimelineEventResponseDto> = emptyList(),
    val deposit: ReservationDepositResponseDto? = null
)

@Serializable
data class ReservationPageResponseDto(
    val items: List<ReservationResponseDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 100,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
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
    val reason: String? = null,
    val arrivedGuests: Int? = null
)

@Serializable
data class ExtendReservationHoldRequestDto(
    val minutes: Int,
    val reason: String? = null
)

@Serializable
data class ReservationSeatingCheckResponseDto(
    val minutesLeft: Int? = null,
    val nextBookingStart: String? = null,
    val nextBookingName: String? = null,
    val fitsAtTables: Boolean? = null,
    val alternatives: List<ReservationAvailabilityOptionResponseDto> = emptyList()
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
    val upcomingCount: Int? = null,
    val presentGuests: Int? = null,
    val guestsToArrive: Int? = null,
    val expiredCount: Int? = null,
    val needsReviewCount: Int? = null,
    val attendanceNotConfirmedCount: Int? = null,
    val notConfirmedDueCount: Int? = null,
    val bigGroupCount: Int? = null,
    val unassignedCount: Int? = null,
    val arrivingSoonCount: Int? = null,
    val arrivingSoonGuests: Int? = null
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
    val advanceBookingDays: Int? = null,
    val largeGroupFrom: Int? = null,
    val largeGroupExtraMinutes: Int? = null,
    val approvalGroupSize: Int? = null,
    val holdMinutes: Int? = null,
    val holdWarningMinutes: Int? = null,
    val lateAfterMinutes: Int? = null,
    val checkInOpensMinutes: Int? = null,
    val reopenWindowMinutes: Int? = null,
    val undoSeatMinutes: Int? = null,
    val runningLateMaxMinutes: Int? = null,
    val serviceDayStartHour: Int? = null,
    val sameDayConfirmMinutes: Int? = null,
    val noShowWarningFrom: Int? = null,
    val confirmReminderTime: String? = null
)

@Serializable
data class GuestHistoryResponseDto(
    val noShowCount: Int? = null,
    val warningFrom: Int? = null,
    val noShows: List<GuestNoShowDto> = emptyList(),
    val clears: List<GuestNoShowClearDto> = emptyList()
)

@Serializable
data class GuestNoShowDto(val reservationId: String, val reservationCode: String? = null, val reservationStart: String? = null, val partySize: Int? = null)

@Serializable
data class GuestNoShowClearDto(val clearedAt: String? = null, val reason: String? = null)

@Serializable
data class WaitlistEntryResponseDto(
    val id: String,
    val guestName: String,
    val contactPhone: String? = null,
    val partySize: Int,
    val note: String? = null,
    val createdAt: String? = null,
    val waitedMinutes: Int? = null,
    val tableFreeNow: Boolean? = null,
    val tableFreeAround: String? = null
)

@Serializable
data class WaitlistEntryRequestDto(val guestName: String, val contactPhone: String? = null, val partySize: Int, val note: String? = null)

@Serializable
data class SeatWaitlistEntryRequestDto(val tableId: String)

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

@Serializable
data class ReservationOccasionDto(
    val code: String? = null,
    val name: String,
    val icon: String,
    val options: List<String> = emptyList(),
    val active: Boolean? = null
)

@Serializable
data class SaveReservationOccasionsRequestDto(val occasions: List<ReservationOccasionDto>)

@Serializable
data class RestaurantEventDto(
    val id: String? = null,
    val name: String,
    val icon: String,
    val startDate: String,
    val endDate: String,
    val menuId: String? = null,
    val menuName: String? = null,
    val specialMenuOnly: Boolean? = null,
    val active: Boolean? = null
)

// One paid (or to-pay) part of a booking: a deposit, a paid extra or the food pre-order.
@Serializable
data class MoneyLineDto(
    val id: String,
    val kind: String,
    val description: String? = null,
    val amount: OrderDecimal? = null,
    val currency: String? = null,
    val status: String,
    val refundDeadline: String? = null,
    val refundedAmount: OrderDecimal? = null,
    val refundIfCancelledNow: OrderDecimal? = null,
    val explanation: String? = null
)

// A paid extra from a special menu that fits the booking's occasion, e.g. "Birthday cake · €25".
@Serializable
data class BookingExtraChoiceDto(
    val menuItemId: String,
    val name: String,
    val description: String? = null,
    val price: OrderDecimal? = null,
    val currency: String? = null,
    val orderBeforeHours: Int? = null,
    val occasionCodes: List<String> = emptyList()
)

@Serializable
data class AddBookingExtraRequestDto(val menuItemId: String, val quantity: Int)

@Serializable
data class GoodwillRefundRequestDto(val amount: OrderDecimal, val reason: String)
