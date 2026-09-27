package com.saporini.mobile_desktop.pos.reservations.ui

import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.reservations.domain.model.*

enum class ReservationListMode {
    TODAY,
    UPCOMING,
    RANGE,
    CALENDAR,
    ALL
}

data class ReservationListFilter(
    val mode: ReservationListMode = ReservationListMode.TODAY,
    val from: String? = null,
    val to: String? = null,
    val status: ReservationStatus? = null,
    val customerId: String? = null,
    val upcomingLimit: Int? = 25
) {
    init { require((from == null) == (to == null)) { "Provide both date boundaries" } }
}

data class ReservationsScope(
    val userId: String,
    val restaurantId: String,
    val branchId: String
)

enum class ReservationErrorKind {
    VALIDATION,
    PERMISSION,
    NOT_FOUND,
    CONFLICT,
    CONNECTION,
    SERVER,
    SESSION
}

data class ReservationFailure(
    val kind: ReservationErrorKind,
    val message: String,
    val operationMayHaveSucceeded: Boolean = false
)

class ReservationOperationException(val failure: ReservationFailure) : Exception(failure.message)

const val OTHER_TABLES_GROUP = "Ungrouped tables"

data class ReservationTableGroup(
    val name: String,
    val tables: List<LayoutTable>
)

data class ReservationsUiState(
    val scope: ReservationsScope? = null,
    val permissions: Set<String> = emptySet(),
    val reservations: List<Reservation> = emptyList(),
    val selectedReservationId: String? = null,
    val selectedReservation: Reservation? = null,
    val filter: ReservationListFilter = ReservationListFilter(),
    val searchQuery: String = "",
    val summary: ReservationSummary? = null,
    val capacity: ReservationCapacity? = null,
    val availabilityOptions: List<ReservationAvailabilityOption> = emptyList(),
    val validation: ReservationValidation? = null,
    val notes: List<ReservationNote> = emptyList(),
    val statusHistory: List<ReservationStatusHistory> = emptyList(),
    val timeline: List<ReservationTimelineEvent> = emptyList(),
    val audit: ReservationAudit? = null,
    val deposit: ReservationDeposit? = null,
    val isLoading: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val isSaving: Boolean = false,
    val isLoadingAvailability: Boolean = false,
    val error: ReservationFailure? = null,
    val refreshWarning: String? = null,
    val needsReconciliation: Boolean = false,
    val lastRefreshedAt: String? = null,
    val lastSuccessfulOperation: String? = null,
    val tableGroups: List<ReservationTableGroup> = emptyList(),
    val floors: List<String> = emptyList(),
    val hasMoreReservations: Boolean = false,
    val isLoadingMoreReservations: Boolean = false,
    val rules: ReservationRules = ReservationRules.NONE,
    // Admin Hub reservation timers (hold, late, check-in, correction windows).
    val policy: ReservationPolicy = ReservationPolicy(),
    // The last "load more" failed: lists stop loading on scroll and offer "Try again" instead.
    val loadMoreReservationsFailed: Boolean = false,
    // Overview "Arriving" feed: guests still to come from 15 minutes ago on, soonest first, loaded page by page.
    val arrivals: List<Reservation> = emptyList(),
    val arrivalsLoaded: Boolean = false,
    val hasMoreArrivals: Boolean = false,
    val isLoadingMoreArrivals: Boolean = false,
    val loadMoreArrivalsFailed: Boolean = false
) {
    val visibleReservations: List<Reservation>
        get() {
            val query = searchQuery.trim()
            return reservations.filter { reservation ->
                (filter.status == null || reservation.status == filter.status) &&
                    (filter.customerId == null || reservation.customerId == filter.customerId) &&
                    (query.isEmpty() || listOfNotNull(
                        reservation.reservationCode,
                        reservation.customerName,
                        reservation.contactName,
                        reservation.contactPhone,
                        reservation.contactEmail,
                        reservation.primaryTable?.tableNumber,
                        reservation.primaryTable?.tableName
                    ).any { it.contains(query, ignoreCase = true) })
            }
        }

    fun can(permission: String): Boolean = permission in permissions
}
