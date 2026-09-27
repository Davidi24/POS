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
    NO_SHOW,
    // A request nobody answered before the booking time; never counted as a no-show.
    EXPIRED
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
    val expiredAt: String? = null,
    // When the table stops waiting for late guests (booking time + hold, or later if staff held it longer).
    val holdUntil: String? = null,
    // "3 of 6 arrived"; null until check-in.
    val arrivedGuests: Int? = null,
    // Never seated before the booking ended, or still open from an earlier day: staff decide what happened.
    val needsReview: Boolean = false,
    val reviewReason: String? = null,
    // "✓ Attendance confirmed" (by STAFF after a call or by the GUEST), and for confirmed bookings whether it's due:
    // CONFIRMED, WAITING, CONFIRM_NOW (booked less than 2 h ahead) or NOT_CONFIRMED (deadline passed).
    val attendanceConfirmedAt: String? = null,
    val attendanceConfirmedVia: String? = null,
    val attendance: String? = null,
    // The guest's no-shows since a manager last cleared the warning.
    val guestNoShows: Int? = null,
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

data class ReservationDetails(
    val reservation: Reservation,
    val audit: ReservationAudit,
    val timeline: List<ReservationTimelineEvent>,
    val deposit: ReservationDeposit
)

data class ReservationPage(
    val items: List<Reservation>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
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
    val upcomingCount: Int = 0,
    // Null when the server doesn't send them yet; the screen then counts from its own list.
    // Guests of every booking still on (pending to completed), and of those not arrived yet.
    val presentGuests: Int? = null,
    val guestsToArrive: Int? = null,
    // Pending to seated bookings without a table.
    val unassignedCount: Int? = null,
    // Pending or confirmed bookings from 15 minutes ago to 2 hours ahead.
    val arrivingSoonCount: Int? = null,
    val arrivingSoonGuests: Int? = null,
    // Requests nobody answered in time, and visits staff must look at.
    val expiredCount: Int? = null,
    val needsReviewCount: Int? = null,
    val attendanceNotConfirmedCount: Int? = null,
    val notConfirmedDueCount: Int? = null,
    val bigGroupCount: Int? = null
)

// A guest's no-shows (the warning on their bookings) and the times a manager cleared it.
data class GuestHistory(
    val noShowCount: Int = 0,
    val warningFrom: Int = 1,
    val noShows: List<GuestNoShow> = emptyList(),
    val clears: List<GuestNoShowClear> = emptyList()
)

data class GuestNoShow(val reservationId: String, val reservationCode: String?, val reservationStart: String?, val partySize: Int?)

data class GuestNoShowClear(val clearedAt: String?, val reason: String?)

// A walk-in group waiting at the door for a table.
data class WaitlistEntry(
    val id: String,
    val guestName: String,
    val contactPhone: String? = null,
    val partySize: Int,
    val note: String? = null,
    val createdAt: String? = null,
    val waitedMinutes: Int = 0,
    val tableFreeNow: Boolean = false,
    val tableFreeAround: String? = null
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
    val primaryTableId: String? = null,
    // Same-day phone bookings: the guest already said they're coming ("✓ Attendance confirmed").
    val attendanceConfirmed: Boolean? = null
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
    val reason: String? = null,
    // Check-in and "N of M arrived": how many of the group are here; null means everyone.
    val arrivedGuests: Int? = null
)

// Late guests keep their end time: do they still fit at their tables, and where else they could sit.
data class ReservationSeatingCheck(
    val minutesLeft: Int = 0,
    val nextBookingStart: String? = null,
    val nextBookingName: String? = null,
    val fitsAtTables: Boolean = false,
    val alternatives: List<ReservationAvailabilityOption> = emptyList()
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

data class ReservationSettings(
    val timezone: String?,
    val rules: ReservationRules,
    val policy: ReservationPolicy = ReservationPolicy()
)

// Admin Hub → Settings → Reservations: the timers and limits the server enforces, with the agreed defaults.
data class ReservationPolicy(
    val largeGroupFrom: Int = 5,
    val largeGroupExtraMinutes: Int = 15,
    val approvalGroupSize: Int = 7,
    val holdMinutes: Int = 30,
    val holdWarningMinutes: Int = 20,
    val lateAfterMinutes: Int = 15,
    val checkInOpensMinutes: Int = 120,
    val reopenWindowMinutes: Int = 60,
    val undoSeatMinutes: Int = 15,
    val runningLateMaxMinutes: Int = 30,
    val serviceDayStartHour: Int = 6,
    val sameDayConfirmMinutes: Int = 120,
    val noShowWarningFrom: Int = 1,
    val confirmReminderTime: String = "15:00"
) {
    // 2 h, or 2 h 15 for groups of 5 or more.
    fun bookingMinutes(rules: ReservationRules, guests: Int): Int =
        rules.defaultDurationMinutes + if (guests >= largeGroupFrom) largeGroupExtraMinutes else 0
}

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
        // Used while no rule is set: two-hour bookings with 5 minutes' cleaning, 1–20 guests, no booking window.
        val NONE = ReservationRules(null, 120, 5, 1, 20, null)
    }
}
