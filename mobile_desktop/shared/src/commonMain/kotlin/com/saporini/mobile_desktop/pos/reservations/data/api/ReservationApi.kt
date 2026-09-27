@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.saporini.mobile_desktop.pos.reservations.data.api

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.reservations.data.dto.*
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart

class ReservationApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) {
    private fun endpoint(path: String): String = "${baseUrlProvider().trimEnd('/')}$path"
    private fun restaurantPath(restaurantId: String): String =
        "/restaurants/${restaurantId.encodeURLPathPart()}/reservations"
    private fun branchPath(restaurantId: String, branchId: String): String =
        "/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/reservations"

    suspend fun getReservations(restaurantId: String): List<ReservationResponseDto> =
        client.get(endpoint(restaurantPath(restaurantId))).body()

    suspend fun getBranchReservations(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null,
        status: ReservationStatus? = null,
        customerId: String? = null
    ): List<ReservationResponseDto> = client.get(endpoint(branchPath(restaurantId, branchId))) {
        from?.let { parameter("from", it) }
        to?.let { parameter("to", it) }
        status?.let { parameter("status", it.name) }
        customerId?.let { parameter("customerId", it) }
    }.body()

    suspend fun getBranchReservationCalendar(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null,
        page: Int = 0,
        size: Int = 100
    ): ReservationPageResponseDto = client.get(endpoint("${branchPath(restaurantId, branchId)}/calendar")) {
        from?.let { parameter("from", it) }
        to?.let { parameter("to", it) }
        parameter("page", page)
        parameter("size", size)
    }.body()

    suspend fun getTodayReservations(restaurantId: String, branchId: String, page: Int = 0, size: Int = 100): ReservationPageResponseDto =
        client.get(endpoint("${branchPath(restaurantId, branchId)}/today")) {
            parameter("page", page)
            parameter("size", size)
        }.body()

    // Guests still to arrive (pending or confirmed) from `from` on, soonest first; one floor when given.
    suspend fun getArrivals(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        floor: String? = null,
        page: Int = 0,
        size: Int = 100
    ): ReservationPageResponseDto = client.get(endpoint("${branchPath(restaurantId, branchId)}/arrivals")) {
        from?.let { parameter("from", it) }
        floor?.let { parameter("floor", it) }
        parameter("page", page)
        parameter("size", size)
    }.body()

    suspend fun getUpcomingReservations(
        restaurantId: String,
        branchId: String,
        limit: Int? = null
    ): List<ReservationResponseDto> = client.get(endpoint("${branchPath(restaurantId, branchId)}/upcoming")) {
        limit?.let { parameter("limit", it) }
    }.body()

    suspend fun createReservation(
        restaurantId: String,
        request: ReservationRequestDto
    ): ReservationResponseDto = client.post(endpoint(restaurantPath(restaurantId))) {
        headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    suspend fun getReservation(restaurantId: String, reservationId: String): ReservationResponseDto =
        client.get(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}")).body()

    suspend fun getReservationDetails(restaurantId: String, reservationId: String): ReservationDetailsResponseDto =
        client.get(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/details")).body()

    suspend fun updateReservation(
        restaurantId: String,
        reservationId: String,
        request: ReservationRequestDto
    ): ReservationResponseDto = client.put(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}")) {
        headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    suspend fun patchReservation(
        restaurantId: String,
        reservationId: String,
        request: UpdateReservationRequestDto
    ): ReservationResponseDto = client.patch(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}")) {
        headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    suspend fun deleteReservation(restaurantId: String, reservationId: String) {
        client.delete(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        }
    }

    suspend fun confirmReservation(restaurantId: String, reservationId: String, request: ReservationActionRequestDto = ReservationActionRequestDto()): ReservationResponseDto =
        reservationAction(restaurantId, reservationId, "confirm", request)

    suspend fun cancelReservation(restaurantId: String, reservationId: String, request: ReservationActionRequestDto = ReservationActionRequestDto()): ReservationResponseDto =
        reservationAction(restaurantId, reservationId, "cancel", request)

    suspend fun checkInReservation(restaurantId: String, reservationId: String, request: ReservationActionRequestDto = ReservationActionRequestDto()): ReservationResponseDto =
        reservationAction(restaurantId, reservationId, "check-in", request)

    suspend fun seatReservation(restaurantId: String, reservationId: String, request: ReservationActionRequestDto = ReservationActionRequestDto()): ReservationResponseDto =
        reservationAction(restaurantId, reservationId, "seat", request)

    suspend fun completeReservation(restaurantId: String, reservationId: String, request: ReservationActionRequestDto = ReservationActionRequestDto()): ReservationResponseDto =
        reservationAction(restaurantId, reservationId, "complete", request)

    suspend fun markNoShow(restaurantId: String, reservationId: String, request: ReservationActionRequestDto = ReservationActionRequestDto()): ReservationResponseDto =
        reservationAction(restaurantId, reservationId, "mark-no-show", request)

    suspend fun reopenReservation(restaurantId: String, reservationId: String, request: ReservationActionRequestDto = ReservationActionRequestDto()): ReservationResponseDto =
        reservationAction(restaurantId, reservationId, "reopen", request)

    suspend fun undoSeatReservation(restaurantId: String, reservationId: String, request: ReservationActionRequestDto = ReservationActionRequestDto()): ReservationResponseDto =
        reservationAction(restaurantId, reservationId, "undo-seat", request)

    suspend fun updateArrivedGuests(restaurantId: String, reservationId: String, request: ReservationActionRequestDto): ReservationResponseDto =
        reservationAction(restaurantId, reservationId, "arrived-guests", request)

    suspend fun extendHold(restaurantId: String, reservationId: String, request: ExtendReservationHoldRequestDto): ReservationResponseDto =
        client.post(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/extend-hold")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun confirmAttendance(restaurantId: String, reservationId: String, request: ReservationActionRequestDto = ReservationActionRequestDto()): ReservationResponseDto =
        reservationAction(restaurantId, reservationId, "confirm-attendance", request)

    suspend fun clearNoShowWarning(restaurantId: String, reservationId: String, request: ReservationActionRequestDto): ReservationResponseDto =
        reservationAction(restaurantId, reservationId, "clear-no-show-warning", request)

    suspend fun getGuestHistory(restaurantId: String, reservationId: String): GuestHistoryResponseDto =
        client.get(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/guest-history")).body()

    // Walk-ins waiting at the door for a table.
    suspend fun getWaitlist(restaurantId: String, branchId: String): List<WaitlistEntryResponseDto> =
        client.get(endpoint("${waitlistPath(restaurantId, branchId)}")).body()

    suspend fun addToWaitlist(restaurantId: String, branchId: String, request: WaitlistEntryRequestDto): WaitlistEntryResponseDto =
        client.post(endpoint(waitlistPath(restaurantId, branchId))) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun seatFromWaitlist(restaurantId: String, branchId: String, entryId: String, request: SeatWaitlistEntryRequestDto): WaitlistEntryResponseDto =
        client.post(endpoint("${waitlistPath(restaurantId, branchId)}/${entryId.encodeURLPathPart()}/seat")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun removeFromWaitlist(restaurantId: String, branchId: String, entryId: String) {
        client.post(endpoint("${waitlistPath(restaurantId, branchId)}/${entryId.encodeURLPathPart()}/remove"))
    }

    private fun waitlistPath(restaurantId: String, branchId: String) =
        "/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/waitlist"

    suspend fun getSeatingCheck(restaurantId: String, reservationId: String): ReservationSeatingCheckResponseDto =
        client.get(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/seating-check")).body()

    private suspend fun reservationAction(
        restaurantId: String,
        reservationId: String,
        action: String,
        request: ReservationActionRequestDto
    ): ReservationResponseDto = client.post(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/$action")) {
        headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    suspend fun getReservationTables(restaurantId: String, reservationId: String): List<ReservationTableAssignmentResponseDto> =
        client.get(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/tables")).body()

    suspend fun updateReservationTables(
        restaurantId: String,
        reservationId: String,
        request: UpdateReservationTablesRequestDto
    ): List<ReservationTableAssignmentResponseDto> = client.put(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/tables")) {
        headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    suspend fun addReservationTable(
        restaurantId: String,
        reservationId: String,
        tableId: String
    ): ReservationTableAssignmentResponseDto = client.post(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/tables/${tableId.encodeURLPathPart()}")) {
        headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
    }.body()

    suspend fun deleteReservationTable(restaurantId: String, reservationId: String, tableId: String) {
        client.delete(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/tables/${tableId.encodeURLPathPart()}")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        }
    }

    suspend fun markPrimaryReservationTable(
        restaurantId: String,
        reservationId: String,
        tableId: String
    ): List<ReservationTableAssignmentResponseDto> = client.patch(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/tables/${tableId.encodeURLPathPart()}/primary")) {
        headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
    }.body()

    suspend fun autoAssignReservationTable(restaurantId: String, reservationId: String): List<ReservationTableAssignmentResponseDto> =
        client.post(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/auto-assign-table")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        }.body()

    suspend fun getStatusHistory(restaurantId: String, reservationId: String): List<ReservationStatusHistoryResponseDto> =
        client.get(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/status-history")).body()

    suspend fun getTimeline(restaurantId: String, reservationId: String): List<ReservationTimelineEventResponseDto> =
        client.get(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/timeline")).body()

    suspend fun getAudit(restaurantId: String, reservationId: String): ReservationAuditResponseDto =
        client.get(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/audit")).body()

    suspend fun addNote(
        restaurantId: String,
        reservationId: String,
        request: ReservationNoteRequestDto
    ): ReservationNoteResponseDto = client.post(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/notes")) {
        headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    suspend fun deleteNote(restaurantId: String, reservationId: String, noteId: String) {
        client.delete(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/notes/${noteId.encodeURLPathPart()}")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        }
    }

    suspend fun getDeposit(restaurantId: String, reservationId: String): ReservationDepositResponseDto =
        client.get(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/deposit")).body()

    suspend fun updateDeposit(
        restaurantId: String,
        reservationId: String,
        request: UpdateReservationDepositRequestDto
    ): ReservationDepositResponseDto = client.put(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/deposit")) {
        headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    suspend fun payDeposit(restaurantId: String, reservationId: String): ReservationDepositResponseDto =
        depositAction(restaurantId, reservationId, "pay")

    suspend fun refundDeposit(restaurantId: String, reservationId: String): ReservationDepositResponseDto =
        depositAction(restaurantId, reservationId, "refund")

    suspend fun waiveDeposit(restaurantId: String, reservationId: String): ReservationDepositResponseDto =
        depositAction(restaurantId, reservationId, "waive")

    suspend fun forfeitDeposit(restaurantId: String, reservationId: String): ReservationDepositResponseDto =
        depositAction(restaurantId, reservationId, "forfeit")

    private suspend fun depositAction(restaurantId: String, reservationId: String, action: String): ReservationDepositResponseDto =
        client.post(endpoint("${restaurantPath(restaurantId)}/${reservationId.encodeURLPathPart()}/deposit/$action")) {
            headers.append("Idempotency-Key", kotlin.uuid.Uuid.random().toString())
        }.body()

    suspend fun searchAvailability(
        restaurantId: String,
        branchId: String,
        request: ReservationAvailabilitySearchRequestDto
    ): List<ReservationAvailabilityOptionResponseDto> = client.post(endpoint("${branchPath(restaurantId, branchId)}/availability/search")) {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    suspend fun recommendAvailability(
        restaurantId: String,
        branchId: String,
        request: ReservationAvailabilitySearchRequestDto
    ): List<ReservationAvailabilityOptionResponseDto> = client.post(endpoint("${branchPath(restaurantId, branchId)}/availability/recommend")) {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    suspend fun getReservationSummary(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null,
        floor: String? = null
    ): ReservationSummaryResponseDto = client.get(endpoint("${branchPath(restaurantId, branchId)}/summary")) {
        from?.let { parameter("from", it) }
        to?.let { parameter("to", it) }
        floor?.let { parameter("floor", it) }
    }.body()

    suspend fun getReservationSettings(restaurantId: String, branchId: String): ReservationSettingsResponseDto =
        client.get(endpoint("${branchPath(restaurantId, branchId)}/settings")).body()

    suspend fun getReservationCapacity(
        restaurantId: String,
        branchId: String,
        from: String? = null,
        to: String? = null,
        partySize: Int? = null,
        floor: String? = null
    ): ReservationCapacityResponseDto = client.get(endpoint("${branchPath(restaurantId, branchId)}/capacity")) {
        from?.let { parameter("from", it) }
        to?.let { parameter("to", it) }
        partySize?.let { parameter("partySize", it) }
        floor?.let { parameter("floor", it) }
    }.body()

    suspend fun validateReservation(
        restaurantId: String,
        branchId: String,
        request: ReservationValidationRequestDto
    ): ReservationValidationResponseDto = client.post(endpoint("${branchPath(restaurantId, branchId)}/validate")) {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()
}
