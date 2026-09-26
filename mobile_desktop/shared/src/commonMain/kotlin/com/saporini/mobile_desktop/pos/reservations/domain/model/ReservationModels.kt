package com.saporini.mobile_desktop.pos.reservations.domain.model

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import kotlinx.serialization.Serializable

@Serializable
enum class ReservationStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    SEATED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}

@Serializable
enum class ReservationSource {
    INTERNAL,
    WEB,
    MOBILE,
    PHONE,
    WALK_IN,
    THIRD_PARTY
}

@Serializable
enum class ReservationDepositStatus {
    NOT_REQUIRED,
    PENDING,
    PAID,
    PARTIALLY_PAID,
    REFUNDED,
    FORFEITED,
    WAIVED
}

data class Reservation(
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
    val tableAssignments: List<ReservationTableAssignment> = emptyList()
) {
    val displayGuestName: String
        get() = contactName?.takeIf(String::isNotBlank)
            ?: customerName?.takeIf(String::isNotBlank)
            ?: "No name provided"

    val primaryTable: ReservationTableAssignment?
        get() = tableAssignments.firstOrNull { it.primary == true }
            ?: tableAssignments.firstOrNull()
}

data class ReservationTableAssignment(
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

data class ReservationNote(
    val id: String,
    val note: String,
    val createdBy: String? = null,
    val createdByName: String? = null,
    val updatedBy: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ReservationStatusHistory(
    val id: String,
    val oldStatus: ReservationStatus? = null,
    val newStatus: ReservationStatus? = null,
    val reason: String? = null,
    val changedBy: String? = null,
    val changedAt: String? = null
)

data class ReservationTimelineEvent(
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

data class ReservationAudit(
    val reservationId: String,
    val createdAt: String? = null,
    val createdBy: String? = null,
    val updatedAt: String? = null,
    val updatedBy: String? = null,
    val statusHistory: List<ReservationStatusHistory> = emptyList(),
    val notes: List<ReservationNote> = emptyList(),
    val tableAssignments: List<ReservationTableAssignment> = emptyList()
)

data class ReservationDeposit(
    val reservationId: String,
    val depositRequired: Boolean? = null,
    val depositAmount: OrderDecimal? = null,
    val depositStatus: ReservationDepositStatus? = null
)

data class ReservationAvailabilityOption(
    val tableIds: List<String> = emptyList(),
    val tableNumbers: List<String> = emptyList(),
    val primaryTableId: String? = null,
    val tableCount: Int? = null,
    val totalCapacity: Int? = null,
    val exactFit: Boolean? = null
)

data class ReservationValidation(
    val valid: Boolean = false,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val suggestions: List<ReservationAvailabilityOption> = emptyList()
)

data class ReservationSummary(
    val branchId: String,
    val from: String? = null,
    val to: String? = null,
    val totalReservations: Int = 0,
    val totalGuests: Int = 0,
    val pendingCount: Int = 0,
    val confirmedCount: Int = 0,
    val checkedInCount: Int = 0,
    val seatedCount: Int = 0,
    val completedCount: Int = 0,
    val cancelledCount: Int = 0,
    val noShowCount: Int = 0,
    val upcomingCount: Int = 0
)

data class ReservationCapacity(
    val branchId: String,
    val from: String? = null,
    val to: String? = null,
    val totalRootTables: Int = 0,
    val availableRootTables: Int = 0,
    val totalSeats: Int = 0,
    val availableSeats: Int = 0,
    val maxSingleTableCapacity: Int? = null,
    val maxAvailableTableCapacity: Int? = null,
    val requestedPartySize: Int? = null,
    val canAccommodateRequestedPartySize: Boolean? = null
)

data class ReservationInput(
    val branchId: String? = null,
    val customerId: String? = null,
    val source: ReservationSource? = ReservationSource.INTERNAL,
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

data class UpdateReservationInput(
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

data class ReservationActionInput(
    val reason: String? = null
)

data class ReservationTablesInput(
    val tableIds: List<String>,
    val primaryTableId: String? = null
)

data class ReservationDepositInput(
    val depositRequired: Boolean? = null,
    val depositAmount: OrderDecimal? = null
)

data class ReservationAvailabilitySearchInput(
    val reservationStart: String,
    val reservationEnd: String,
    val partySize: Int,
    val maxOptions: Int? = null
)

data class ReservationValidationInput(
    val reservationStart: String,
    val reservationEnd: String,
    val partySize: Int,
    val tableIds: List<String>? = null,
    val primaryTableId: String? = null
)

data class ReservationSettings(val timezone: String?, val rules: ReservationRules)

// The restaurant's booking rule. For staff these are defaults and limits to warn about, not hard stops.
data class ReservationRules(
    val name: String?,
    val defaultDurationMinutes: Int,
    val bufferMinutes: Int,
    val minPartySize: Int,
    val maxPartySize: Int,
    val advanceBookingDays: Int?
) {
    companion object {
        // Used while no rule is set: two-hour bookings, 1–20 guests, no booking window.
        val NONE = ReservationRules(null, 120, 0, 1, 20, null)
    }
}
