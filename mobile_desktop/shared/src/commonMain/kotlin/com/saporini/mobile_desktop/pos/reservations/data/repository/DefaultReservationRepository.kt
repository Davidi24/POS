package com.saporini.mobile_desktop.pos.reservations.data.repository

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.reservations.data.api.ReservationApi
import com.saporini.mobile_desktop.pos.reservations.data.dto.AddBookingExtraRequestDto
import com.saporini.mobile_desktop.pos.reservations.data.dto.GoodwillRefundRequestDto
import com.saporini.mobile_desktop.pos.reservations.data.dto.ExtendReservationHoldRequestDto
import com.saporini.mobile_desktop.pos.reservations.data.dto.ReservationActionRequestDto
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
        customerId: String?,
        page: Int,
        size: Int
    ): ReservationPage = api.getBranchReservations(restaurantId, branchId, from, to, status, customerId, page, size).toDomain()

    override suspend fun getBranchReservationCalendar(
        restaurantId: String,
        branchId: String,
        from: String?,
        to: String?
    ): List<Reservation> = api.getBranchReservationCalendar(restaurantId, branchId, from, to, 0, 100).items.map { it.toDomain() }

    override suspend fun getBranchReservationCalendarPage(
        restaurantId: String, branchId: String, from: String?, to: String?, page: Int, size: Int
    ): ReservationPage = api.getBranchReservationCalendar(restaurantId, branchId, from, to, page, size).toDomain()

    override suspend fun getTodayReservations(restaurantId: String, branchId: String): List<Reservation> =
        api.getTodayReservations(restaurantId, branchId).items.map { it.toDomain() }

    override suspend fun getTodayReservationsPage(restaurantId: String, branchId: String, page: Int, size: Int): ReservationPage =
        api.getTodayReservations(restaurantId, branchId, page, size).toDomain()

    override suspend fun getUpcomingReservations(restaurantId: String, branchId: String, limit: Int?): List<Reservation> =
        api.getUpcomingReservations(restaurantId, branchId, limit).map { it.toDomain() }

    override suspend fun getArrivalsPage(
        restaurantId: String, branchId: String, from: String?, floor: String?, page: Int, size: Int
    ): ReservationPage = api.getArrivals(restaurantId, branchId, from, floor, page, size).toDomain()

    override suspend fun createReservation(restaurantId: String, request: ReservationInput): Reservation =
        api.createReservation(restaurantId, request.toDto()).toDomain()

    override suspend fun getReservation(restaurantId: String, reservationId: String): Reservation =
        api.getReservation(restaurantId, reservationId).toDomain()

    override suspend fun getReservationDetails(restaurantId: String, reservationId: String): ReservationDetails =
        api.getReservationDetails(restaurantId, reservationId).toDomain()

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

    override suspend fun undoSeatReservation(restaurantId: String, reservationId: String, request: ReservationActionInput): Reservation =
        api.undoSeatReservation(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun updateArrivedGuests(restaurantId: String, reservationId: String, request: ReservationActionInput): Reservation =
        api.updateArrivedGuests(restaurantId, reservationId, request.toDto()).toDomain()

    override suspend fun extendHold(restaurantId: String, reservationId: String, minutes: Int, reason: String?): Reservation =
        api.extendHold(restaurantId, reservationId, ExtendReservationHoldRequestDto(minutes, reason)).toDomain()

    override suspend fun getSeatingCheck(restaurantId: String, reservationId: String): ReservationSeatingCheck =
        api.getSeatingCheck(restaurantId, reservationId).toDomain()

    override suspend fun getOccasions(restaurantId: String): List<ReservationOccasion> =
        api.getOccasions(restaurantId).map { it.toDomain() }

    override suspend fun getEventOn(restaurantId: String, date: String): RestaurantEvent? =
        api.getEventOn(restaurantId, date)?.toDomain()

    override suspend fun confirmAttendance(restaurantId: String, reservationId: String, reason: String?): Reservation =
        api.confirmAttendance(restaurantId, reservationId, ReservationActionRequestDto(reason = reason)).toDomain()

    override suspend fun getGuestHistory(restaurantId: String, reservationId: String): GuestHistory =
        api.getGuestHistory(restaurantId, reservationId).toDomain()

    override suspend fun clearNoShowWarning(restaurantId: String, reservationId: String, reason: String): Reservation =
        api.clearNoShowWarning(restaurantId, reservationId, ReservationActionRequestDto(reason = reason)).toDomain()

    override suspend fun declineReservation(restaurantId: String, reservationId: String, reason: String?): Reservation =
        api.declineReservation(restaurantId, reservationId, ReservationActionRequestDto(reason = reason)).toDomain()

    override suspend fun getMoney(restaurantId: String, reservationId: String): List<MoneyLine> =
        api.getMoney(restaurantId, reservationId).map { it.toDomain() }

    override suspend fun getExtraChoices(restaurantId: String, reservationId: String): List<BookingExtraChoice> =
        api.getExtraChoices(restaurantId, reservationId).map { it.toDomain() }

    override suspend fun addExtra(restaurantId: String, reservationId: String, menuItemId: String, quantity: Int): List<MoneyLine> =
        api.addExtra(restaurantId, reservationId, AddBookingExtraRequestDto(menuItemId, quantity)).map { it.toDomain() }

    override suspend fun sendPaymentLink(restaurantId: String, reservationId: String) =
        api.sendPaymentLink(restaurantId, reservationId)

    override suspend fun markPaid(restaurantId: String, reservationId: String, paymentId: String): List<MoneyLine> =
        api.markPaid(restaurantId, reservationId, paymentId).map { it.toDomain() }

    override suspend fun removeUnpaid(restaurantId: String, reservationId: String, paymentId: String): List<MoneyLine> =
        api.removeUnpaid(restaurantId, reservationId, paymentId).map { it.toDomain() }

    override suspend fun goodwillRefund(restaurantId: String, reservationId: String, lineId: String, amountCents: Long, reason: String): List<MoneyLine> =
        api.goodwillRefund(restaurantId, reservationId, lineId, GoodwillRefundRequestDto(OrderDecimal(centsText(amountCents)), reason)).map { it.toDomain() }

    private fun centsText(cents: Long): String = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

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

    override suspend fun getReservationSummary(restaurantId: String, branchId: String, from: String?, to: String?, floor: String?): ReservationSummary =
        api.getReservationSummary(restaurantId, branchId, from, to, floor).toDomain()

    override suspend fun getReservationSettings(restaurantId: String, branchId: String): ReservationSettings =
        api.getReservationSettings(restaurantId, branchId).let { dto ->
            val defaults = ReservationPolicy()
            ReservationSettings(
                timezone = dto.timezone,
                rules = ReservationRules(
                    name = dto.ruleName,
                    defaultDurationMinutes = dto.defaultDurationMinutes ?: ReservationRules.NONE.defaultDurationMinutes,
                    bufferMinutes = dto.bufferMinutes ?: ReservationRules.NONE.bufferMinutes,
                    minPartySize = dto.minPartySize ?: 1,
                    maxPartySize = dto.maxPartySize ?: ReservationRules.NONE.maxPartySize,
                    advanceBookingDays = dto.advanceBookingDays
                ),
                policy = ReservationPolicy(
                    largeGroupFrom = dto.largeGroupFrom ?: defaults.largeGroupFrom,
                    largeGroupExtraMinutes = dto.largeGroupExtraMinutes ?: defaults.largeGroupExtraMinutes,
                    approvalGroupSize = dto.approvalGroupSize ?: defaults.approvalGroupSize,
                    holdMinutes = dto.holdMinutes ?: defaults.holdMinutes,
                    holdWarningMinutes = dto.holdWarningMinutes ?: defaults.holdWarningMinutes,
                    lateAfterMinutes = dto.lateAfterMinutes ?: defaults.lateAfterMinutes,
                    checkInOpensMinutes = dto.checkInOpensMinutes ?: defaults.checkInOpensMinutes,
                    reopenWindowMinutes = dto.reopenWindowMinutes ?: defaults.reopenWindowMinutes,
                    undoSeatMinutes = dto.undoSeatMinutes ?: defaults.undoSeatMinutes,
                    runningLateMaxMinutes = dto.runningLateMaxMinutes ?: defaults.runningLateMaxMinutes,
                    serviceDayStartHour = dto.serviceDayStartHour ?: defaults.serviceDayStartHour,
                    sameDayConfirmMinutes = dto.sameDayConfirmMinutes ?: defaults.sameDayConfirmMinutes,
                    noShowWarningFrom = dto.noShowWarningFrom ?: defaults.noShowWarningFrom,
                    confirmReminderTime = dto.confirmReminderTime?.take(5) ?: defaults.confirmReminderTime
                )
            )
        }

    override suspend fun getReservationCapacity(restaurantId: String, branchId: String, from: String?, to: String?, partySize: Int?, floor: String?): ReservationCapacity =
        api.getReservationCapacity(restaurantId, branchId, from, to, partySize, floor).toDomain()

    override suspend fun validateReservation(restaurantId: String, branchId: String, request: ReservationValidationInput): ReservationValidation =
        api.validateReservation(restaurantId, branchId, request.toDto()).toDomain()
}
