package com.saporini.mobile_desktop.pos.reservations.domain.repository

import com.saporini.mobile_desktop.pos.reservations.domain.model.*

interface ReservationRepository {
    suspend fun getReservations(restaurantId: String): List<Reservation>

    suspend fun getBranchReservations(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null,
        status: ReservationStatus? = null,
        customerId: String? = null
    ): List<Reservation>

    suspend fun getBranchReservationCalendar(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null
    ): List<Reservation>

    suspend fun getTodayReservations(restaurantId: String, branchId: String): List<Reservation>

    suspend fun getUpcomingReservations(
        restaurantId: String,
        branchId: String,
        limit: Int? = null
    ): List<Reservation>

    suspend fun createReservation(restaurantId: String, request: ReservationInput): Reservation
    suspend fun getReservation(restaurantId: String, reservationId: String): Reservation
    suspend fun updateReservation(restaurantId: String, reservationId: String, request: ReservationInput): Reservation
    suspend fun patchReservation(restaurantId: String, reservationId: String, request: UpdateReservationInput): Reservation
    suspend fun deleteReservation(restaurantId: String, reservationId: String)

    suspend fun confirmReservation(restaurantId: String, reservationId: String, request: ReservationActionInput = ReservationActionInput()): Reservation
    suspend fun cancelReservation(restaurantId: String, reservationId: String, request: ReservationActionInput = ReservationActionInput()): Reservation
    suspend fun checkInReservation(restaurantId: String, reservationId: String, request: ReservationActionInput = ReservationActionInput()): Reservation
    suspend fun seatReservation(restaurantId: String, reservationId: String, request: ReservationActionInput = ReservationActionInput()): Reservation
    suspend fun completeReservation(restaurantId: String, reservationId: String, request: ReservationActionInput = ReservationActionInput()): Reservation
    suspend fun markNoShow(restaurantId: String, reservationId: String, request: ReservationActionInput = ReservationActionInput()): Reservation
    suspend fun reopenReservation(restaurantId: String, reservationId: String, request: ReservationActionInput = ReservationActionInput()): Reservation

    suspend fun getReservationTables(restaurantId: String, reservationId: String): List<ReservationTableAssignment>
    suspend fun updateReservationTables(restaurantId: String, reservationId: String, request: ReservationTablesInput): List<ReservationTableAssignment>
    suspend fun addReservationTable(restaurantId: String, reservationId: String, tableId: String): ReservationTableAssignment
    suspend fun deleteReservationTable(restaurantId: String, reservationId: String, tableId: String)
    suspend fun markPrimaryReservationTable(restaurantId: String, reservationId: String, tableId: String): List<ReservationTableAssignment>
    suspend fun autoAssignReservationTable(restaurantId: String, reservationId: String): List<ReservationTableAssignment>

    suspend fun getStatusHistory(restaurantId: String, reservationId: String): List<ReservationStatusHistory>
    suspend fun getTimeline(restaurantId: String, reservationId: String): List<ReservationTimelineEvent>
    suspend fun getAudit(restaurantId: String, reservationId: String): ReservationAudit

    suspend fun addNote(restaurantId: String, reservationId: String, note: String): ReservationNote
    suspend fun deleteNote(restaurantId: String, reservationId: String, noteId: String)

    suspend fun getDeposit(restaurantId: String, reservationId: String): ReservationDeposit
    suspend fun updateDeposit(restaurantId: String, reservationId: String, request: ReservationDepositInput): ReservationDeposit
    suspend fun payDeposit(restaurantId: String, reservationId: String): ReservationDeposit
    suspend fun refundDeposit(restaurantId: String, reservationId: String): ReservationDeposit
    suspend fun waiveDeposit(restaurantId: String, reservationId: String): ReservationDeposit
    suspend fun forfeitDeposit(restaurantId: String, reservationId: String): ReservationDeposit

    suspend fun searchAvailability(restaurantId: String, branchId: String, request: ReservationAvailabilitySearchInput): List<ReservationAvailabilityOption>
    suspend fun recommendAvailability(restaurantId: String, branchId: String, request: ReservationAvailabilitySearchInput): List<ReservationAvailabilityOption>
    suspend fun getReservationSummary(restaurantId: String, branchId: String, from: String? = null, to: String? = null): ReservationSummary
    // The restaurant's time zone and its active booking rule.
    suspend fun getReservationSettings(restaurantId: String, branchId: String): ReservationSettings
    suspend fun getReservationCapacity(restaurantId: String, branchId: String, from: String? = null, to: String? = null, partySize: Int? = null, floor: String? = null): ReservationCapacity
    suspend fun validateReservation(restaurantId: String, branchId: String, request: ReservationValidationInput): ReservationValidation
}
