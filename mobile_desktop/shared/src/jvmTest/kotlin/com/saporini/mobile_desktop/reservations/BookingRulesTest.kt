package com.saporini.mobile_desktop.reservations

import com.saporini.mobile_desktop.pos.reservations.BookingAction
import com.saporini.mobile_desktop.pos.reservations.bookingActions
import com.saporini.mobile_desktop.pos.reservations.bookingFlags
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationPolicy
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationTableAssignment
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationsScreenModel
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// What the booking panel offers under the agreed rules, and the labels on cards (default Admin Hub values).
class BookingRulesTest {
    // 20:00 in Berlin.
    private val now = Instant.parse("2026-09-27T18:00:00Z")
    private val policy = ReservationPolicy()
    private val staff: (String) -> Boolean = { it == ReservationsScreenModel.WRITE_PERMISSION }
    private val manager: (String) -> Boolean = { true }

    @BeforeTest
    fun zone() = RestaurantTime.use("Europe/Berlin")

    private fun booking(
        status: ReservationStatus,
        startsIn: Duration,
        guests: Int = 2,
        table: Boolean = true,
        change: Reservation.() -> Reservation = { this }
    ) = Reservation(
        id = "r1", restaurantId = "rest", branchId = "b1", status = status, partySize = guests,
        reservationStart = (now + startsIn).toString(), reservationEnd = (now + startsIn + 2.hours).toString(),
        contactName = "Maria",
        tableAssignments = if (table) listOf(ReservationTableAssignment("a1", "t1", tableNumber = "T4", primary = true)) else emptyList()
    ).change()

    @Test
    fun checkInOpensTwoHoursBefore() {
        val early = bookingActions(booking(ReservationStatus.CONFIRMED, 3.hours), policy, staff, now)
        assertFalse(early.primary!!.enabled)
        assertEquals("Check-in opens at 21:00", early.primary!!.note)

        val soon = bookingActions(booking(ReservationStatus.CONFIRMED, 30.minutes), policy, staff, now)
        assertEquals(BookingAction.GUEST_ARRIVED, soon.primary!!.action)
        assertTrue(soon.primary!!.enabled)
        assertTrue(soon.menu.none { it.action == BookingAction.NO_SHOW }, "no no-show before the booking starts")
    }

    @Test
    fun lateGuestsCanBeHeldOrMarkedNoShow() {
        val late = bookingActions(booking(ReservationStatus.CONFIRMED, (-25).minutes), policy, staff, now)
        assertTrue(late.menu.any { it.action == BookingAction.HOLD_LONGER })
        assertTrue(late.menu.any { it.action == BookingAction.NO_SHOW })
    }

    @Test
    fun bigGroupsNeedApproval() {
        val request = booking(ReservationStatus.PENDING, 1.days, guests = 8)
        assertFalse(bookingActions(request, policy, staff, now).primary!!.enabled)
        assertTrue(bookingActions(request, policy, manager, now).primary!!.enabled)
        assertTrue(bookingFlags(request, policy, now).any { it.text == "Needs approval" })
    }

    @Test
    fun correctingANoShowAfterAnHourNeedsAManager() {
        val recent = booking(ReservationStatus.NO_SHOW, (-40).minutes) { copy(noShowAt = (now - 10.minutes).toString()) }
        val arrived = bookingActions(recent, policy, staff, now).primary!!
        assertEquals(BookingAction.GUEST_ARRIVED, arrived.action)
        assertTrue(arrived.enabled && !arrived.needsReason)

        val old = booking(ReservationStatus.NO_SHOW, (-100).minutes) { copy(noShowAt = (now - 70.minutes).toString()) }
        assertFalse(bookingActions(old, policy, staff, now).primary!!.enabled)
        val forManager = bookingActions(old, policy, manager, now).primary!!
        assertTrue(forManager.enabled && forManager.needsReason)
    }

    @Test
    fun undoSeatWithinFifteenMinutes() {
        val justSeated = booking(ReservationStatus.SEATED, (-30).minutes) { copy(seatedAt = (now - 10.minutes).toString()) }
        assertTrue(bookingActions(justSeated, policy, staff, now).menu.first { it.action == BookingAction.UNDO_SEAT }.enabled)

        val longAgo = booking(ReservationStatus.SEATED, (-40).minutes) { copy(seatedAt = (now - 20.minutes).toString()) }
        assertFalse(bookingActions(longAgo, policy, staff, now).menu.first { it.action == BookingAction.UNDO_SEAT }.enabled)
    }

    @Test
    fun earlierDayNeedsAManager() {
        val yesterday = booking(ReservationStatus.SEATED, (-1).days)
        assertFalse(bookingActions(yesterday, policy, staff, now).primary!!.enabled)
        assertTrue(bookingActions(yesterday, policy, manager, now).primary!!.needsReason)
    }

    @Test
    fun cardLabels() {
        assertEquals("16 min late", bookingFlags(booking(ReservationStatus.CONFIRMED, (-16).minutes), policy, now).single().text)
        assertEquals("Hold ends in 8 min", bookingFlags(booking(ReservationStatus.CONFIRMED, (-22).minutes), policy, now).single().text)
        assertTrue(bookingFlags(booking(ReservationStatus.CONFIRMED, (-5).minutes), policy, now).isEmpty())

        val waiting = booking(ReservationStatus.CHECKED_IN, (-5).minutes, guests = 6, table = false) { copy(arrivedGuests = 3) }
        assertEquals(listOf("3 of 6 arrived", "Waiting for table"), bookingFlags(waiting, policy, now).map { it.text })

        val review = booking(ReservationStatus.CHECKED_IN, (-3).hours) { copy(needsReview = true) }
        assertEquals("Needs review", bookingFlags(review, policy, now).first().text)
    }

    @Test
    fun heldLongerShowsTheNewTime() {
        val held = booking(ReservationStatus.CONFIRMED, (-25).minutes) { copy(holdUntil = (now + 20.minutes).toString()) }
        val flags = bookingFlags(held, policy, now).map { it.text }
        assertTrue("Held until 20:20" in flags, flags.toString())
        assertNull(flags.firstOrNull { it.startsWith("Hold ends") })
    }

    @Test
    fun bookingLengthByGroupSize() {
        val rules = com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationRules.NONE
        assertEquals(120, policy.bookingMinutes(rules, 4))
        assertEquals(135, policy.bookingMinutes(rules, 5))
    }

    @Test
    fun attendanceLabelsAndAction() {
        val waiting = booking(ReservationStatus.CONFIRMED, 5.hours) { copy(attendance = "WAITING") }
        assertTrue(bookingActions(waiting, policy, staff, now).secondary?.action == BookingAction.CONFIRM_ATTENDANCE)
        assertTrue(bookingFlags(waiting, policy, now).isEmpty())

        val confirmed = booking(ReservationStatus.CONFIRMED, 5.hours) { copy(attendance = "CONFIRMED") }
        assertNull(bookingActions(confirmed, policy, staff, now).secondary)
        assertEquals("✓ Attendance confirmed", bookingFlags(confirmed, policy, now).single().text)

        assertEquals("Not confirmed", bookingFlags(booking(ReservationStatus.CONFIRMED, 1.hours) { copy(attendance = "NOT_CONFIRMED") }, policy, now).single().text)
        assertEquals("Confirm now", bookingFlags(booking(ReservationStatus.CONFIRMED, 1.hours) { copy(attendance = "CONFIRM_NOW") }, policy, now).single().text)
    }

    @Test
    fun noShowWarningFromTheSetting() {
        val once = booking(ReservationStatus.CONFIRMED, 5.hours) { copy(guestNoShows = 1) }
        assertEquals("⚠ 1 no-show before", bookingFlags(once, policy, now).single().text)
        assertTrue(bookingFlags(once, policy.copy(noShowWarningFrom = 2), now).isEmpty())
        assertEquals("⚠ 3 no-shows before", bookingFlags(booking(ReservationStatus.CONFIRMED, 5.hours) { copy(guestNoShows = 3) }, policy, now).single().text)
    }
}
