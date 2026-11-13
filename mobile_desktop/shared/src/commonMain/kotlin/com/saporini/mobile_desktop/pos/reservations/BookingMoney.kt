package com.saporini.mobile_desktop.pos.reservations

import com.saporini.mobile_desktop.pos.reservations.domain.model.BookingExtraChoice
import com.saporini.mobile_desktop.pos.reservations.domain.model.MoneyLine
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import com.saporini.mobile_desktop.pos.reservations.domain.model.moneyCents
import com.saporini.mobile_desktop.pos.reservations.domain.model.moneyText
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// A booking's money as staff see it: each paid part (deposit, paid extras, food pre-order), what they can do with it,
// and the goodwill limit. Refund amounts and card fees come from the server; these rules only decide what's offered.

internal enum class MoneyAction(val label: String) {
    MARK_PAID("Mark paid"),
    REMOVE("Remove"),
    GOODWILL("Goodwill refund")
}

// Extras and payment links only make sense before the guests arrive (the server says the same).
private val OPEN_FOR_MONEY = setOf(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)

internal fun moneyStatusLabel(line: MoneyLine): String = when (line.status) {
    "PENDING" -> "To pay"
    "PAID" -> "Paid"
    "REFUNDED" -> "Refunded"
    "KEPT" -> "Kept"
    "CANCELLED" -> "Not charged"
    else -> line.status.lowercase().replaceFirstChar { it.uppercase() }
}

// Paid at the desk, or no longer wanted (only an unpaid extra; a required deposit stays). Goodwill needs its own
// permission and only applies to money the restaurant kept.
internal fun moneyActions(line: MoneyLine, canManage: Boolean, canGoodwill: Boolean): List<MoneyAction> = buildList {
    if (line.status == "PENDING" && !line.isPreOrder && canManage) {
        add(MoneyAction.MARK_PAID)
        if (line.kind == "EXTRA") add(MoneyAction.REMOVE)
    }
    // A goodwill refund is at least 1 cent and less than what's left, so there must be more than 1 cent left.
    if (line.status == "KEPT" && canGoodwill && goodwillLeftCents(line) > 1) add(MoneyAction.GOODWILL)
}

internal fun goodwillLeftCents(line: MoneyLine): Long = (line.amountCents - line.refundedCents).coerceAtLeast(0)

private val AMOUNT = Regex("\\d+([.,]\\d{1,2})?")

// Null when the amount can be given back.
internal fun goodwillProblem(text: String, line: MoneyLine): String? {
    val clean = text.trim()
    if (clean.isEmpty()) return "Enter an amount to give back"
    if (!AMOUNT.matches(clean)) return "Enter an amount like 10 or 12.50"
    val cents = moneyCents(clean) ?: return "Enter an amount like 10 or 12.50"
    if (cents <= 0) return "Enter an amount to give back"
    val left = goodwillLeftCents(line)
    if (cents >= left) return "Less than ${moneyText(left, line.currency)}: a goodwill refund is never all of it"
    return null
}

// Null hides the button; a note explains why it can't be used yet.
internal data class MoneyOffer(val enabled: Boolean, val note: String? = null)

internal fun paymentLinkOffer(reservation: Reservation, lines: List<MoneyLine>, canManage: Boolean): MoneyOffer? {
    if (!canManage || reservation.status !in OPEN_FOR_MONEY || lines.none { it.status == "PENDING" }) return null
    return if (reservation.contactEmail.isNullOrBlank()) MoneyOffer(false, "Add the guest's email to send a payment link")
    else MoneyOffer(true)
}

internal fun addExtraOffer(reservation: Reservation, choices: List<BookingExtraChoice>, canManage: Boolean): Boolean =
    canManage && reservation.status in OPEN_FOR_MONEY && choices.isNotEmpty()

// "Too late: order 48 h ahead" once the extra's order deadline has passed.
internal fun extraTooLate(choice: BookingExtraChoice, reservationStart: String, now: Instant): String? {
    val start = runCatching { Instant.parse(reservationStart) }.getOrNull() ?: return null
    val deadline = start - (choice.orderBeforeHours ?: 0).hours
    if (now <= deadline) return null
    return choice.orderBeforeHours?.let { "Too late: order $it h ahead" } ?: "Too late to order"
}

// What's still to pay, e.g. "€75.00 to pay".
internal fun moneyDueLabel(lines: List<MoneyLine>): String? {
    val due = lines.filter { it.status == "PENDING" }
    if (due.isEmpty()) return null
    return "${moneyText(due.sumOf { it.amountCents }, due.first().currency)} to pay"
}
