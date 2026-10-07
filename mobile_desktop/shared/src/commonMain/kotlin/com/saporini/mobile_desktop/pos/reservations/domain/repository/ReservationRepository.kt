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
        customerId: String? = null,
        page: Int = 0,
        size: Int = 100
    ): ReservationPage

    suspend fun getBranchReservationCalendar(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null
    ): List<Reservation>

    suspend fun getBranchReservationCalendarPage(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null,
        page: Int = 0,
        size: Int = 100
    ): ReservationPage {
        val items = if (page == 0) getBranchReservationCalendar(restaurantId, branchId, from, to) else emptyList()
        return ReservationPage(items, page, size, items.size.toLong(), if (items.isEmpty()) 0 else 1, false, page > 0)
    }

    suspend fun getTodayReservations(restaurantId: String, branchId: String): List<Reservation>

    suspend fun getTodayReservationsPage(restaurantId: String, branchId: String, page: Int = 0, size: Int = 100): ReservationPage {
        val items = if (page == 0) getTodayReservations(restaurantId, branchId) else emptyList()
        return ReservationPage(items, page, size, items.size.toLong(), if (items.isEmpty()) 0 else 1, false, page > 0)
    }

    suspend fun getUpcomingReservations(
        restaurantId: String,
        branchId: String,
        limit: Int? = null
    ): List<Reservation>

    // Guests still to arrive from `from` on, soonest first, one page at a time; one floor when given.
    suspend fun getArrivalsPage(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        floor: String? = null,
        page: Int = 0,
        size: Int = 100
    ): ReservationPage {
        val items = if (page == 0) getUpcomingReservations(restaurantId, branchId, size) else emptyList()
        return ReservationPage(items, page, size, items.size.toLong(), if (items.isEmpty()) 0 else 1, false, page > 0)
    }

    suspend fun createReservation(restaurantId: String, request: ReservationInput): Reservation
    suspend fun getReservation(restaurantId: String, reservationId: String): Reservation
    suspend fun getReservationDetails(restaurantId: String, reservationId: String): ReservationDetails
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
    // Seated the wrong booking: back to checked in.
    suspend fun undoSeatReservation(restaurantId: String, reservationId: String, request: ReservationActionInput = ReservationActionInput()): Reservation
    // "3 of 6 arrived" → "6 of 6".
    suspend fun updateArrivedGuests(restaurantId: String, reservationId: String, request: ReservationActionInput): Reservation
    // Keep the table longer for late guests; the booking still ends on time.
    suspend fun extendHold(restaurantId: String, reservationId: String, minutes: Int, reason: String? = null): Reservation
    suspend fun getSeatingCheck(restaurantId: String, reservationId: String): ReservationSeatingCheck
    suspend fun getOccasions(restaurantId: String): List<ReservationOccasion>
    // The restaurant's event on a day ("yyyy-MM-dd"), if any.
    suspend fun getEventOn(restaurantId: String, date: String): RestaurantEvent?
    // "✓ Attendance confirmed" after a call.
    suspend fun confirmAttendance(restaurantId: String, reservationId: String, reason: String? = null): Reservation
    suspend fun getGuestHistory(restaurantId: String, reservationId: String): GuestHistory
    // Managers clear a guest's no-show warning with a reason; the history stays.
    suspend fun clearNoShowWarning(restaurantId: String, reservationId: String, reason: String): Reservation

    // Say no to a request: the guest is told and gets all their money back.
    suspend fun declineReservation(restaurantId: String, reservationId: String, reason: String? = null): Reservation
    // The booking's money: deposit, paid extras, food pre-order, and what a cancel would do.
    suspend fun getMoney(restaurantId: String, reservationId: String): List<MoneyLine>
    suspend fun getExtraChoices(restaurantId: String, reservationId: String): List<BookingExtraChoice>
    suspend fun addExtra(restaurantId: String, reservationId: String, menuItemId: String, quantity: Int): List<MoneyLine>
    suspend fun sendPaymentLink(restaurantId: String, reservationId: String)
    // Paid at the desk.
    suspend fun markPaid(restaurantId: String, reservationId: String, paymentId: String): List<MoneyLine>
    suspend fun removeUnpaid(restaurantId: String, reservationId: String, paymentId: String): List<MoneyLine>
    // Part of kept money back, with a reason (never all of it).
    suspend fun goodwillRefund(restaurantId: String, reservationId: String, lineId: String, amountCents: Long, reason: String): List<MoneyLine>

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
    // With a floor, only that floor's bookings (plus those without a table) are counted.
    suspend fun getReservationSummary(restaurantId: String, branchId: String, from: String? = null, to: String? = null, floor: String? = null): ReservationSummary
    // The restaurant's time zone and its active booking rule.
    suspend fun getReservationSettings(restaurantId: String, branchId: String): ReservationSettings
    suspend fun getReservationCapacity(restaurantId: String, branchId: String, from: String? = null, to: String? = null, partySize: Int? = null, floor: String? = null): ReservationCapacity
    suspend fun validateReservation(restaurantId: String, branchId: String, request: ReservationValidationInput): ReservationValidation
}
