package com.saporini.mobile_desktop.pos.reservations

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationPolicy
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationsScreenModel
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// The booking rules as the app shows them (the server enforces the same ones, agreed with the owner 2026-09-27):
// which actions a booking offers, when only a manager with a reason may act, and the short labels on cards
// ("12 min late", "Hold ends in 8 min", "3 of 6 arrived", "Waiting for table", "Needs review").

// From a booking to its table on Tables and to its order in Orders (user, 2026-11-02), for every card and the panel.
internal class BookingLinks(
    // The seated booking's open order on its table: id and number.
    val orderOf: (Reservation) -> Pair<String, String>?,
    val goToTable: (Reservation) -> Unit,
    val goToOrder: (String) -> Unit,
    // The colour its table has on Tables right now, for a booking whose guests are there (checked in or seated).
    val tableColor: (Reservation) -> Color? = { null }
)

internal val LocalBookingLinks = staticCompositionLocalOf<BookingLinks?> { null }

// Admin Hub reservation timers for every card and panel below the reservations screen.
internal val LocalReservationPolicy = staticCompositionLocalOf { ReservationPolicy() }

internal val HoldEndsColor = Color(0xFFEAB308)
internal val LateColor = Color(0xFFC8790B)
internal val ReviewColor = Color(0xFFB13A2F)
internal val WaitingColor = Color(0xFF24748A)
internal val AttendanceColor = Color(0xFF4F7942)

internal enum class BookingAction(val label: String, val danger: Boolean = false) {
    ACCEPT("Accept request"),
    DECLINE("Decline request", danger = true),
    GUEST_ARRIVED("Guest arrived"),
    // A button only while the guest hasn't confirmed; after that the booking just says "Attendance confirmed" (user, 2026-11-02).
    CONFIRM_ATTENDANCE("Confirm attendance"),
    // Called and nobody answered: a grey mark, never a cancel (user, 2026-11-02).
    COULD_NOT_REACH("Couldn't reach"),
    ARRIVED_COUNT("Change arrived guests"),
    SEAT("Seat guests"),
    UNDO_SEAT("Undo seating"),
    FINISH("Finish visit"),
    LEFT_WITHOUT_ORDERING("Left without ordering"),
    HOLD_LONGER("Hold table longer"),
    NO_SHOW("Mark as no show", danger = true),
    CANCEL("Cancel booking", danger = true),
    LEFT_BEFORE_SEATING("Left before being seated", danger = true),
    REOPEN("Reopen")
}

// One action as offered right now: maybe greyed out with why, maybe only with a reason (a manager correcting).
internal data class OfferedAction(
    val action: BookingAction,
    val enabled: Boolean = true,
    val needsReason: Boolean = false,
    val note: String? = null
)

internal data class BookingActions(
    val primary: OfferedAction? = null,
    val secondary: OfferedAction? = null,
    val menu: List<OfferedAction> = emptyList()
)

internal fun bookingActions(
    reservation: Reservation,
    policy: ReservationPolicy,
    can: (String) -> Boolean,
    now: Instant
): BookingActions {
    val start = reservation.reservationStart.instant() ?: return BookingActions()
    val end = reservation.reservationEnd.instant() ?: start + 2.hours
    val earlierDay = serviceDate(start, policy) < serviceDate(now, policy)
    val canCorrect = can(ReservationsScreenModel.CORRECT_PERMISSION)

    // Within the staff time limit anyone may; after it, or on an earlier day's booking, a manager with a reason.
    fun offer(action: BookingAction, since: Instant? = null, window: Int? = null, what: String = ""): OfferedAction {
        val late = since != null && window != null && now > since + window.minutes
        return when {
            !late && !earlierDay -> OfferedAction(action)
            canCorrect -> OfferedAction(
                action, needsReason = true,
                note = if (earlierDay) "From an earlier day: give a reason" else "More than ${duration(window ?: 0)} since it was $what: give a reason"
            )
            else -> OfferedAction(action, enabled = false, note = "Only a manager can change this now")
        }
    }

    return when (reservation.status) {
        ReservationStatus.PENDING -> {
            val big = reservation.partySize >= policy.approvalGroupSize
            val accept = if (big && !can(ReservationsScreenModel.APPROVE_PERMISSION)) {
                OfferedAction(BookingAction.ACCEPT, enabled = false, note = "Groups of ${policy.approvalGroupSize} or more need someone who can approve bookings")
            } else offer(BookingAction.ACCEPT)
            BookingActions(
                primary = accept,
                // The guest came although nobody answered the request yet.
                secondary = offer(BookingAction.GUEST_ARRIVED).takeIf { now >= start - policy.checkInOpensMinutes.minutes },
                menu = listOf(offer(BookingAction.DECLINE))
            )
        }
        ReservationStatus.CONFIRMED -> {
            val opens = start - policy.checkInOpensMinutes.minutes
            val arrived = if (now < opens) OfferedAction(BookingAction.GUEST_ARRIVED, enabled = false, note = "Check-in opens at ${opens.clock()}")
            else offer(BookingAction.GUEST_ARRIVED)
            BookingActions(
                primary = arrived,
                // After calling the guest (or they called): the booking gets "✓ Attendance confirmed".
                secondary = offer(BookingAction.CONFIRM_ATTENDANCE).takeIf { reservation.attendance != "CONFIRMED" && now < end },
                menu = listOfNotNull(
                    offer(BookingAction.COULD_NOT_REACH).takeIf {
                        reservation.attendance != "CONFIRMED" && reservation.attendanceUnreachableAt == null && now < end
                    },
                    offer(BookingAction.HOLD_LONGER).takeIf { now < end && now >= start - policy.checkInOpensMinutes.minutes },
                    offer(BookingAction.NO_SHOW).takeIf { now >= start },
                    offer(BookingAction.CANCEL)
                )
            )
        }
        ReservationStatus.CHECKED_IN -> BookingActions(
            primary = offer(BookingAction.SEAT),
            secondary = offer(BookingAction.ARRIVED_COUNT).takeIf { (reservation.arrivedGuests ?: reservation.partySize) < reservation.partySize },
            menu = listOf(offer(BookingAction.ARRIVED_COUNT), offer(BookingAction.LEFT_BEFORE_SEATING))
        )
        ReservationStatus.SEATED -> BookingActions(
            primary = offer(BookingAction.FINISH),
            menu = listOf(
                offer(BookingAction.ARRIVED_COUNT),
                offer(BookingAction.LEFT_WITHOUT_ORDERING),
                // No time limit while there's no order on it (user, 2026-11-02); the server refuses once there is one.
                offer(BookingAction.UNDO_SEAT)
            )
        )
        ReservationStatus.NO_SHOW -> {
            val since = reservation.noShowAt?.instant()
            BookingActions(
                primary = offer(BookingAction.GUEST_ARRIVED, since, policy.reopenWindowMinutes, "marked no-show"),
                secondary = offer(BookingAction.REOPEN, since, policy.reopenWindowMinutes, "marked no-show")
            )
        }
        ReservationStatus.CANCELLED -> BookingActions(
            primary = offer(BookingAction.REOPEN, reservation.cancelledAt?.instant(), policy.reopenWindowMinutes, "cancelled")
        )
        ReservationStatus.EXPIRED -> BookingActions(
            primary = offer(BookingAction.REOPEN, reservation.expiredAt?.instant(), policy.reopenWindowMinutes, "expired")
        )
        ReservationStatus.NO_ACTION -> BookingActions(
            primary = offer(BookingAction.REOPEN, reservation.noActionAt?.instant(), policy.reopenWindowMinutes, "closed as no action taken")
        )
        ReservationStatus.COMPLETED -> BookingActions()
    }
}

internal data class BookingFlag(val text: String, val color: Color)

// The short state labels for a booking right now, most urgent first.
internal fun bookingFlags(reservation: Reservation, policy: ReservationPolicy, now: Instant): List<BookingFlag> {
    val flags = ArrayList<BookingFlag>()
    if (reservation.needsReview) flags += BookingFlag("Needs review", ReviewColor)
    reservation.guestNoShows?.takeIf { it >= policy.noShowWarningFrom }?.let { count ->
        flags += BookingFlag("⚠ $count ${if (count == 1) "no-show" else "no-shows"} before", ReviewColor)
    }
    val start = reservation.reservationStart.instant()
    val waiting = reservation.status == ReservationStatus.CONFIRMED || reservation.status == ReservationStatus.PENDING
    if (waiting && start != null) {
        val minutesLate = (now - start).inWholeMinutes
        val hold = reservation.holdUntil?.instant() ?: (start + policy.holdMinutes.minutes)
        val holdLeft = (hold - now).inWholeMinutes
        // The warning comes the same time before the hold ends, also when staff held the table longer.
        val warningAt = hold - (policy.holdMinutes - policy.holdWarningMinutes).coerceAtLeast(0).minutes
        if (reservation.status == ReservationStatus.CONFIRMED && now >= warningAt && now < hold) {
            flags += BookingFlag("Hold ends in ${holdLeft.coerceAtLeast(1)} min", HoldEndsColor)
        } else if (minutesLate >= policy.lateAfterMinutes) {
            flags += BookingFlag("${duration(minutesLate.toInt())} late", LateColor)
        }
        if (hold > start + policy.holdMinutes.minutes && holdLeft >= 0) flags += BookingFlag("Held until ${hold.clock()}", LateColor)
    }
    if (reservation.status == ReservationStatus.PENDING && reservation.partySize >= policy.approvalGroupSize) {
        flags += BookingFlag("Needs approval", LateColor)
    }
    val arrived = reservation.arrivedGuests
    if ((reservation.status == ReservationStatus.CHECKED_IN || reservation.status == ReservationStatus.SEATED) &&
        arrived != null && arrived < reservation.partySize
    ) {
        flags += BookingFlag("$arrived of ${reservation.partySize} arrived", WaitingColor)
    }
    if (reservation.status == ReservationStatus.CHECKED_IN && reservation.tableAssignments.isEmpty()) {
        flags += BookingFlag("Waiting for table", WaitingColor)
    }
    return flags
}

internal val UnreachableColor = Color(0xFF8A8D88)
// A plain green and red for the reservation cards (user, 2026-11-03: "use a normal red and green").
internal val PlainGreen = Color(0xFF16A34A)
internal val PlainRed = Color(0xFFDC2626)
internal val GuestConfirmedColor = Color(0xFF15803D)
private val ConfirmLateColor = Color(0xFFC2410C)

internal fun ReservationStatus.pillLabel(): String = when (this) {
    ReservationStatus.PENDING -> "Pending"
    ReservationStatus.CONFIRMED -> "Confirmed"
    ReservationStatus.CHECKED_IN -> "Checked in"
    ReservationStatus.SEATED -> "Seated"
    ReservationStatus.COMPLETED -> "Completed"
    ReservationStatus.CANCELLED -> "Cancelled"
    ReservationStatus.NO_SHOW -> "No show"
    ReservationStatus.EXPIRED -> "Request expired"
    ReservationStatus.NO_ACTION -> "No action taken"
}

// The one state a booking shows, never "Confirmed" beside "hasn't confirmed" (user, 2026-11-02: "not both, make a state
// like confirmed by app"). A confirmed booking says who confirmed it: the app (the guest hasn't yet; orange-red once the
// deadline to confirm passed or it was booked too late to ask), the guest, or nobody could be reached.
internal fun bookingState(reservation: Reservation): BookingFlag {
    val label = reservation.status.pillLabel()
    if (reservation.status != ReservationStatus.CONFIRMED) return BookingFlag(label, StatusDotColors[label] ?: UnreachableColor)
    return when {
        reservation.attendance == "CONFIRMED" -> BookingFlag("Guest confirmed", GuestConfirmedColor)
        reservation.attendanceUnreachableAt != null -> BookingFlag("Couldn't reach", UnreachableColor)
        reservation.attendance == "NOT_CONFIRMED" || reservation.attendance == "CONFIRM_NOW" -> BookingFlag("Confirmed by app", ConfirmLateColor)
        else -> BookingFlag("Confirmed by app", StatusDotColors[label] ?: GuestConfirmedColor)
    }
}

// The restaurant's day runs from the service start (06:00) to the same time next morning.
internal fun serviceDate(moment: Instant, policy: ReservationPolicy): LocalDate =
    (moment - policy.serviceDayStartHour.hours).toLocalDateTime(RestaurantTime.zone).date

internal fun duration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private fun String.instant(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

private fun Instant.clock(): String = toLocalDateTime(RestaurantTime.zone).let {
    "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
}
