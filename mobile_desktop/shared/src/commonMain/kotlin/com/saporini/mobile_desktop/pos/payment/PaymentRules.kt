package com.saporini.mobile_desktop.pos.payment

import com.saporini.mobile_desktop.pos.payment.data.OrderPaymentSummaryDto
import com.saporini.mobile_desktop.pos.payment.data.PaymentDto
import com.saporini.mobile_desktop.pos.reservations.domain.model.moneyCents
import com.saporini.mobile_desktop.pos.shifts.toCents

// The till's rules, the same as the server's, so mistakes are explained before anything is sent. The server still
// checks everything again; these only decide what is offered and what the screen says.

enum class PayMethod(val api: String, val label: String, val card: Boolean) {
    CASH("CASH", "Cash", false),
    CARD("CARD", "Card", true),
    CONTACTLESS("CONTACTLESS", "Contactless", true),
    DIGITAL_WALLET("DIGITAL_WALLET", "Phone wallet", true),
    GIFT_CARD("GIFT_CARD", "Gift card", false),
    BANK_TRANSFER("BANK_TRANSFER", "Bank transfer", false),
    OTHER("OTHER", "Other", false);

    companion object {
        fun fromApi(value: String?): PayMethod? = entries.firstOrNull { it.api == value }
    }
}

/** What the person at the till has typed so far. Empty texts mean "use the suggestion". */
data class PaymentDraft(
    val method: PayMethod = PayMethod.CARD,
    val amountText: String = "",
    val tipPercent: Int? = null,
    val tipText: String = "",
    val tenderedText: String = "",
    val cardLast4: String = "",
    val notes: String = "",
    val closeWhenPaid: Boolean = true
)

/** The draft worked out against the bill: every amount in cents, or null when it isn't a valid number. */
data class PaymentQuote(
    val dueCents: Long,
    val amountCents: Long?,
    val tipCents: Long?,
    val tenderedCents: Long?,
    val changeCents: Long?,
    val leftAfterCents: Long?,
    val problem: String?
) {
    val canSubmit: Boolean get() = problem == null
}

private val LAST4 = Regex("\\d{4}")
const val MAX_NOTES = 1000
const val MAX_REASON = 500

/** The amount the till suggests for this method: the whole bill, rounded for cash. */
fun suggestedAmountCents(summary: OrderPaymentSummaryDto, method: PayMethod): Long {
    val balance = summary.balanceDue.toCents() ?: 0
    val cash = summary.cashBalanceDue.toCents() ?: balance
    return if (method == PayMethod.CASH) cash else balance
}

fun tipFromPercent(amountCents: Long, percent: Int): Long = (amountCents * percent + 50) / 100

fun quote(summary: OrderPaymentSummaryDto, draft: PaymentDraft): PaymentQuote {
    val balance = summary.balanceDue.toCents() ?: 0
    val cashBalance = summary.cashBalanceDue.toCents() ?: balance
    val due = if (draft.method == PayMethod.CASH) cashBalance else balance
    val amount = if (draft.amountText.isBlank()) due else moneyCents(draft.amountText)
    val tip = when {
        draft.tipText.isNotBlank() -> moneyCents(draft.tipText)
        draft.tipPercent != null && amount != null -> tipFromPercent(amount, draft.tipPercent)
        else -> 0L
    }
    val tendered = if (draft.tenderedText.isBlank()) null else moneyCents(draft.tenderedText)
    val change = if (tendered != null && amount != null && tip != null) tendered - amount - tip else null
    val leftAfter = amount?.let { (balance - it).coerceAtLeast(0) }
        // A cash payment of the rounded bill settles it.
        ?.let { left -> if (draft.method == PayMethod.CASH && amount == cashBalance) 0 else left }
    return PaymentQuote(due, amount, tip, tendered, change, leftAfter, problem(summary, draft, amount, tip, tendered, balance, cashBalance))
}

private fun problem(
    summary: OrderPaymentSummaryDto,
    draft: PaymentDraft,
    amount: Long?,
    tip: Long?,
    tendered: Long?,
    balance: Long,
    cashBalance: Long
): String? {
    val cash = draft.method == PayMethod.CASH
    if (summary.orderStatus == "CANCELLED" || summary.orderStatus == "VOIDED") return "This order was ${summary.orderStatus.lowercase()}"
    if (balance <= 0) return "Nothing is left to pay"
    if (amount == null) return "Enter an amount like 10 or 12.50"
    if (amount <= 0) return "Enter an amount to pay"
    val allowed = if (cash) maxOf(balance, cashBalance) else balance
    if (amount > allowed) return "That's more than is left to pay"
    if (!summary.splitBillsAllowed && amount < balance && !(cash && amount == cashBalance)) return "This restaurant takes the whole bill at once"
    if (tip == null) return "Enter a tip like 2 or 2.50"
    if (tip < 0) return "A tip can't be negative"
    if (tip > 0 && !summary.tipsEnabled) return "Tips are switched off"
    if (tip > tipFromPercent(amount, summary.maxTipPercent)) return "The tip is more than ${summary.maxTipPercent}% of the payment"
    if (draft.tenderedText.isNotBlank()) {
        if (!cash) return "Only cash has an amount handed over"
        if (tendered == null) return "Enter the cash handed over like 50"
        if (tendered < amount + tip) return "The cash handed over is less than the amount and tip"
    }
    if (draft.cardLast4.isNotBlank()) {
        if (!draft.method.card) return "Card digits only go with card payments"
        if (!LAST4.matches(draft.cardLast4.trim())) return "Card digits are the last 4 numbers"
    }
    if (draft.notes.length > MAX_NOTES) return "Notes can be at most $MAX_NOTES characters"
    return null
}

/** Round sums a guest is likely to hand over for [dueCents], smallest first (always includes the exact amount). */
fun quickCashTenders(dueCents: Long): List<Long> {
    if (dueCents <= 0) return emptyList()
    val steps = listOf(100L, 500L, 1_000L, 2_000L, 5_000L, 10_000L)
    val options = mutableListOf(dueCents)
    for (step in steps) {
        val rounded = ((dueCents + step - 1) / step) * step
        if (rounded > dueCents && rounded !in options) options.add(rounded)
        if (options.size >= 5) break
    }
    return options.sorted()
}

// ---- Refunds and cancelling ----

/** What a manager may do with one payment: refund what's left, cancel only the day it was taken with nothing refunded. */
data class PaymentActions(val canRefund: Boolean, val refundableCents: Long, val canCancel: Boolean)

fun paymentActions(payment: PaymentDto, permissions: Set<String>, takenToday: Boolean): PaymentActions {
    val holdsMoney = payment.status == "CAPTURED" || payment.status == "PARTIALLY_REFUNDED"
    val refundable = payment.refundableAmount.toCents() ?: 0
    return PaymentActions(
        canRefund = holdsMoney && refundable > 0 && "PAYMENT_REFUND" in permissions,
        refundableCents = refundable,
        canCancel = payment.status == "CAPTURED" && (payment.refundedAmount.toCents() ?: 0) == 0L
                && takenToday && "ORDER_VOID" in permissions
    )
}

fun refundProblem(payment: PaymentDto, amountText: String, reason: String): String? {
    val cents = moneyCents(amountText) ?: return "Enter an amount like 10 or 12.50"
    if (cents <= 0) return "Enter an amount to give back"
    val left = payment.refundableAmount.toCents() ?: 0
    if (cents > left) return "At most ${left / 100}.${(left % 100).toString().padStart(2, '0')} can still be given back"
    return reasonProblem(reason)
}

fun reasonProblem(reason: String): String? {
    val clean = reason.trim()
    if (clean.length < 3) return "Write a reason (at least 3 characters)"
    if (clean.length > MAX_REASON) return "The reason can be at most $MAX_REASON characters"
    return null
}
