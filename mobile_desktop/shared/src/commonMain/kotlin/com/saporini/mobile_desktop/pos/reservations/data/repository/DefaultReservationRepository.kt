package com.saporini.mobile_desktop.pos.reservations.data.repository

import com.saporini.mobile_desktop.pos.reservations.data.api.ReservationApi
import com.saporini.mobile_desktop.pos.reservations.data.dto.ReservationNoteRequestDto
import com.saporini.mobile_desktop.pos.reservations.data.dto.toDomain
import com.saporini.mobile_desktop.pos.reservations.data.dto.toDto
import com.saporini.mobile_desktop.pos.reservations.domain.model.*
import com.saporini.mobile_desktop.pos.reservations.domain.repository.ReservationRepository

class DefaultReservationRepository(
    private val api: ReservationApi
) : ReservationRepository {
    override suspend fun getReservations(restaurantId: String): List<Reservation> =
        api.getReservations(restaurantId).map { it.toDomain() }

    override suspend fun getBranchReservations(
        restaurantId: String,
        branchId: String,
        from: String?,
        to: String?,
        status: ReservationStatus?,
        customerId: String?
    ): List<Reservation> = api.getBranchReservations(restaurantId, branchId, from, to, status, customerId).map { it.toDomain() }

    override suspend fun getBranchReservationCalendar(
        restaurantId: String,
        branchId: String,
        from: String?,
        to: String?
    ): List<Reservation> = api.getBranchReservationCalendar(restaurantId, branchId, from, to).map { it.toDomain() }

    override suspend fun getTodayReservations(restaurantId: String, branchId: String): List<Reservation> =
        api.getTodayReservations(restaurantId, branchId).map { it.toDomain() }

    override suspend fun getUpcomingReservations(restaurantId: String, branchId: String, limit: Int?): List<Reservation> =
        api.getUpcomingReservations(restaurantId, branchId, limit).map { it.toDomain() }

    override suspend fun createReservation(restaurantId: String, request: ReservationInput): Reservation =
        api.createReservation(restaurantId, request.toDto()).toDomain()

    override suspend fun getReservation(restaurantId: String, reservationId: String): Reservation =
        api.getReservation(restaurantId, reservationId).toDomain()

    override suspend fun updateReservation(restaurantId: String, reservationId: String, request: ReservationInput): Reservation =
        api.updateReservation(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun patchReservation(restaurantId: String, reservationId: String, request: UpdateReservationInput): Reservation =
        api.patchReservation(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun deleteReservation(restaurantId: String, reservationId: String) =
        api.deleteReservation(restaurantId, reservationId)

    override suspend fun confirmReservation(restaurantId: String, reservationId: String, request: ReservationActionInput): Reservation =
        api.confirmReservation(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun cancelReservation(restaurantId: String, reservationId: String, request: ReservationActionInput): Reservation =
        api.cancelReservation(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun checkInReservation(restaurantId: String, reservationId: String, request: ReservationActionInput): Reservation =
        api.checkInReservation(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun seatReservation(restaurantId: String, reservationId: String, request: ReservationActionInput): Reservation =
        api.seatReservation(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun completeReservation(restaurantId: String, reservationId: String, request: ReservationActionInput): Reservation =
        api.completeReservation(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun markNoShow(restaurantId: String, reservationId: String, request: ReservationActionInput): Reservation =
        api.markNoShow(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun reopenReservation(restaurantId: String, reservationId: String, request: ReservationActionInput): Reservation =
        api.reopenReservation(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun getReservationTables(restaurantId: String, reservationId: String): List<ReservationTableAssignment> =
        api.getReservationTables(restaurantId, reservationId).map { it.toDomain() }

    override suspend fun updateReservationTables(restaurantId: String, reservationId: String, request: ReservationTablesInput): List<ReservationTableAssignment> =
        api.updateReservationTables(restaurantId, reservationId, request.toDto()).map { it.toDomain() }

    override suspend fun addReservationTable(restaurantId: String, reservationId: String, tableId: String): ReservationTableAssignment =
        api.addReservationTable(restaurantId, reservationId, tableId).toDomain()

    override suspend fun deleteReservationTable(restaurantId: String, reservationId: String, tableId: String) =
        api.deleteReservationTable(restaurantId, reservationId, tableId)

    override suspend fun markPrimaryReservationTable(restaurantId: String, reservationId: String, tableId: String): List<ReservationTableAssignment> =
        api.markPrimaryReservationTable(restaurantId, reservationId, tableId).map { it.toDomain() }

    override suspend fun autoAssignReservationTable(restaurantId: String, reservationId: String): List<ReservationTableAssignment> =
        api.autoAssignReservationTable(restaurantId, reservationId).map { it.toDomain() }

    override suspend fun getStatusHistory(restaurantId: String, reservationId: String): List<ReservationStatusHistory> =
        api.getStatusHistory(restaurantId, reservationId).map { it.toDomain() }

    override suspend fun getTimeline(restaurantId: String, reservationId: String): List<ReservationTimelineEvent> =
        api.getTimeline(restaurantId, reservationId).map { it.toDomain() }

    override suspend fun getAudit(restaurantId: String, reservationId: String): ReservationAudit =
        api.getAudit(restaurantId, reservationId).toDomain()

    override suspend fun addNote(restaurantId: String, reservationId: String, note: String): ReservationNote =
        api.addNote(restaurantId, reservationId, ReservationNoteRequestDto(note = note)).toDomain()

    override suspend fun deleteNote(restaurantId: String, reservationId: String, noteId: String) =
        api.deleteNote(restaurantId, reservationId, noteId)

    override suspend fun getDeposit(restaurantId: String, reservationId: String): ReservationDeposit =
        api.getDeposit(restaurantId, reservationId).toDomain()

    override suspend fun updateDeposit(restaurantId: String, reservationId: String, request: ReservationDepositInput): ReservationDeposit =
        api.updateDeposit(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun payDeposit(restaurantId: String, reservationId: String): ReservationDeposit =
        api.payDeposit(restaurantId, reservationId).toDomain()

    override suspend fun refundDeposit(restaurantId: String, reservationId: String): ReservationDeposit =
        api.refundDeposit(restaurantId, reservationId).toDomain()

    override suspend fun waiveDeposit(restaurantId: String, reservationId: String): ReservationDeposit =
        api.waiveDeposit(restaurantId, reservationId).toDomain()

    override suspend fun forfeitDeposit(restaurantId: String, reservationId: String): ReservationDeposit =
        api.forfeitDeposit(restaurantId, reservationId).toDomain()

    override suspend fun searchAvailability(restaurantId: String, branchId: String, request: ReservationAvailabilitySearchInput): List<ReservationAvailabilityOption> =
        api.searchAvailability(restaurantId, branchId, request.toDto()).map { it.toDomain() }

    override suspend fun recommendAvailability(restaurantId: String, branchId: String, request: ReservationAvailabilitySearchInput): List<ReservationAvailabilityOption> =
        api.recommendAvailability(restaurantId, branchId, request.toDto()).map { it.toDomain() }

    override suspend fun getReservationSummary(restaurantId: String, branchId: String, from: String?, to: String?): ReservationSummary =
        api.getReservationSummary(restaurantId, branchId, from, to).toDomain()

    override suspend fun getReservationSettings(restaurantId: String, branchId: String): ReservationSettings =
        api.getReservationSettings(restaurantId, branchId).let { dto ->
            ReservationSettings(
                timezone = dto.timezone,
                rules = dto.ruleName?.let {
                    ReservationRules(
                        name = it,
                        defaultDurationMinutes = dto.defaultDurationMinutes ?: ReservationRules.NONE.defaultDurationMinutes,
                        bufferMinutes = dto.bufferMinutes ?: 0,
                        minPartySize = dto.minPartySize ?: 1,
                        maxPartySize = dto.maxPartySize ?: ReservationRules.NONE.maxPartySize,
                        advanceBookingDays = dto.advanceBookingDays
                    )
                } ?: ReservationRules.NONE
            )
        }

    override suspend fun getReservationCapacity(restaurantId: String, branchId: String, from: String?, to: String?, partySize: Int?, floor: String?): ReservationCapacity =
        api.getReservationCapacity(restaurantId, branchId, from, to, partySize, floor).toDomain()

    override suspend fun validateReservation(restaurantId: String, branchId: String, request: ReservationValidationInput): ReservationValidation =
        api.validateReservation(restaurantId, branchId, request.toDto()).toDomain()
}
