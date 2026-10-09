package com.saporini.mobile_desktop.reservations

import com.saporini.mobile_desktop.pos.reservations.MoneyAction
import com.saporini.mobile_desktop.pos.reservations.addExtraOffer
import com.saporini.mobile_desktop.pos.reservations.data.dto.MoneyLineDto
import com.saporini.mobile_desktop.pos.reservations.data.dto.toDomain
import com.saporini.mobile_desktop.pos.reservations.domain.model.BookingExtraChoice
import com.saporini.mobile_desktop.pos.reservations.domain.model.MoneyLine
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import com.saporini.mobile_desktop.pos.reservations.domain.model.moneyCents
import com.saporini.mobile_desktop.pos.reservations.domain.model.moneyText
import com.saporini.mobile_desktop.pos.reservations.extraTooLate
import com.saporini.mobile_desktop.pos.reservations.goodwillProblem
import com.saporini.mobile_desktop.pos.reservations.moneyActions
import com.saporini.mobile_desktop.pos.reservations.moneyDueLabel
import com.saporini.mobile_desktop.pos.reservations.paymentLinkOffer
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// A booking's money on the panel: what staff can do with each paid part, the goodwill limit, payment links and extras.
class BookingMoneyTest {
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    private fun line(status: String, kind: String = "EXTRA", amount: Long = 2500, refunded: Long = 0) =
        MoneyLine(id = "p1", kind = kind, description = "Birthday cake", amountCents = amount, currency = "EUR", status = status, refundedCents = refunded)

    private fun booking(status: ReservationStatus = ReservationStatus.CONFIRMED, email: String? = "maria@example.com", startsIn: Int = 72) = Reservation(
        id = "r1", restaurantId = "rest", branchId = "b1", status = status, partySize = 8,
        reservationStart = (now + startsIn.hours).toString(), reservationEnd = (now + (startsIn + 2).hours).toString(),
        contactName = "Maria", contactEmail = email
    )

    @Test
    fun unpaidExtrasCanBePaidAtTheDeskOrRemovedButADepositStays() {
        assertEquals(listOf(MoneyAction.MARK_PAID, MoneyAction.REMOVE), moneyActions(line("PENDING"), canManage = true, canGoodwill = false))
        assertEquals(listOf(MoneyAction.MARK_PAID), moneyActions(line("PENDING", kind = "DEPOSIT"), canManage = true, canGoodwill = false))
        // The food pre-order is paid through its own flow.
        assertTrue(moneyActions(line("PENDING", kind = "PRE_ORDER"), canManage = true, canGoodwill = true).isEmpty())
        // Read-only staff see the money but can't change it.
        assertTrue(moneyActions(line("PENDING"), canManage = false, canGoodwill = false).isEmpty())
        assertTrue(moneyActions(line("PAID"), canManage = true, canGoodwill = true).isEmpty())
    }

    @Test
    fun goodwillOnlyForKeptMoneyAndOnlyWithThePermission() {
        assertEquals(listOf(MoneyAction.GOODWILL), moneyActions(line("KEPT"), canManage = false, canGoodwill = true))
        assertTrue(moneyActions(line("KEPT"), canManage = true, canGoodwill = false).isEmpty())
        assertTrue(moneyActions(line("REFUNDED"), canManage = true, canGoodwill = true).isEmpty())
        // 1 cent left can't be split into "part of it".
        assertTrue(moneyActions(line("KEPT", amount = 2500, refunded = 2499), canManage = true, canGoodwill = true).isEmpty())
    }

    @Test
    fun aGoodwillRefundIsNeverAllOfIt() {
        val kept = line("KEPT", amount = 5000, refunded = 1000)
        assertNull(goodwillProblem("39.99", kept))
        assertNull(goodwillProblem("10,5", kept))
        assertEquals("Less than €40.00: a goodwill refund is never all of it", goodwillProblem("40", kept))
        assertEquals("Enter an amount to give back", goodwillProblem("", kept))
        assertEquals("Enter an amount to give back", goodwillProblem("0", kept))
        assertEquals("Enter an amount like 10 or 12.50", goodwillProblem("12.345", kept))
        assertEquals("Enter an amount like 10 or 12.50", goodwillProblem("ten", kept))
    }

    @Test
    fun aPaymentLinkNeedsSomethingToPayAndTheGuestsEmail() {
        val due = listOf(line("PENDING"))
        assertEquals(true, paymentLinkOffer(booking(), due, canManage = true)?.enabled)
        val noEmail = paymentLinkOffer(booking(email = " "), due, canManage = true)
        assertEquals(false, noEmail?.enabled)
        assertEquals("Add the guest's email to send a payment link", noEmail?.note)
        assertNull(paymentLinkOffer(booking(), listOf(line("PAID")), canManage = true))
        assertNull(paymentLinkOffer(booking(ReservationStatus.SEATED), due, canManage = true))
        assertNull(paymentLinkOffer(booking(), due, canManage = false))
    }

    @Test
    fun extrasBeforeTheGuestsArriveAndBeforeTheirDeadline() {
        val cake = BookingExtraChoice("m1", "Birthday cake", priceCents = 2500, orderBeforeHours = 48)
        assertTrue(addExtraOffer(booking(), listOf(cake), canManage = true))
        assertFalse(addExtraOffer(booking(ReservationStatus.CHECKED_IN), listOf(cake), canManage = true))
        assertFalse(addExtraOffer(booking(), emptyList(), canManage = true))

        assertNull(extraTooLate(cake, booking(startsIn = 72).reservationStart, now))
        assertEquals("Too late: order 48 h ahead", extraTooLate(cake, booking(startsIn = 47).reservationStart, now))
    }

    @Test
    fun amountsAreExactCents() {
        assertEquals(4910, moneyCents("49.10"))
        assertEquals(4910, moneyCents("49.1"))
        assertEquals(1200, moneyCents("12"))
        assertEquals(1200, moneyCents("1.2E+1"))
        assertNull(moneyCents("abc"))
        assertEquals("€49.10", moneyText(4910, "EUR"))
        assertEquals("£0.05", moneyText(5, "GBP"))
        assertEquals("€75.00 to pay", moneyDueLabel(listOf(line("PENDING", amount = 5000), line("PENDING"), line("PAID"))))
        assertNull(moneyDueLabel(listOf(line("PAID"))))
    }

    @Test
    fun serverLinesKeepTheirExplanation() {
        val line = MoneyLineDto(
            id = "pre-order-1", kind = "PRE_ORDER", amount = OrderDecimal("50.00"), currency = "EUR", status = "PAID",
            refundedAmount = OrderDecimal("0"), refundIfCancelledNow = OrderDecimal("49.00"),
            explanation = "€49.00 back if cancelled now: food pre-order €50.00 minus the card fee (€1.00)"
        ).toDomain()
        assertEquals("Food pre-order", line.description)
        assertEquals(5000, line.amountCents)
        assertEquals(4900, line.refundIfCancelledNowCents)
        assertTrue(line.isPreOrder)
    }
}
