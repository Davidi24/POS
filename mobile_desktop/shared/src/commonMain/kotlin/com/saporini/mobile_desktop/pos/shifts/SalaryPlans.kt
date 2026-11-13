package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** How someone is paid: for the hours they clock, or the same salary every month. */
internal enum class PayKind { HOURLY, MONTHLY }

/** How one payment was handed over. */
internal enum class PayMethod(val label: String) { CASH("Cash"), BANK("Bank transfer"), OTHER("Other") }

/** One payment made to someone: how much, when, how, and a reference (a transfer number, a receipt…). */
internal data class PayoutRecord(val cents: Long, val paidOn: LocalDate?, val method: PayMethod?, val reference: String?)

/**
 * MOCK for the production pass, in memory only: who was moved to another team (cleaning, extra workers), and what was paid
 * out. The server should keep both (a team per person and a payout record per person and period) and return them with
 * `/shifts/pay`. How someone is paid (an hourly wage or a monthly salary) is on the server: `StaffPay.monthlySalary`.
 */
internal object SalaryPlans {
    private val teams = mutableStateMapOf<String, String>()
    private class Payout(
        val userId: String, val from: LocalDate, val to: LocalDate, val cents: Long,
        val paidOn: LocalDate? = null, val method: PayMethod? = null, val reference: String? = null
    )
    private val payouts = mutableStateListOf<Payout>()

    /** The team someone was moved to (a `PayTeam` name), or null when their role decides it. */
    fun teamOf(userId: String): String? = teams[userId]

    fun setTeam(userId: String, team: String?) { if (team != null) teams[userId] = team else teams.remove(userId) }

    /**
     * What was paid to someone for [from]..[to] (at most [total]): everything, when a payout covered a longer stretch
     * around it (a month paid shows each of its weeks paid), otherwise the payouts made for parts of it.
     */
    fun paidFor(userId: String, from: LocalDate, to: LocalDate, total: Long): Long {
        val theirs = payouts.filter { it.userId == userId }
        if (theirs.any { it.from <= from && it.to >= to && !(it.from == from && it.to == to) }) return total
        return theirs.filter { it.from >= from && it.to <= to }.sumOf { it.cents }.coerceAtMost(total)
    }

    /** Records that [cents] were paid to someone for [from]..[to], on [paidOn], by [method] (nothing is sent anywhere). */
    fun markPaid(userId: String, from: LocalDate, to: LocalDate, cents: Long, paidOn: LocalDate? = null, method: PayMethod? = null, reference: String? = null) {
        if (cents > 0) payouts += Payout(userId, from, to, cents, paidOn, method, reference?.trim()?.ifBlank { null })
    }

    /** The payments made to someone for [from]..[to] (or a longer stretch around it), oldest first. */
    fun paymentsFor(userId: String, from: LocalDate, to: LocalDate): List<PayoutRecord> =
        payouts.filter { it.userId == userId && ((it.from >= from && it.to <= to) || (it.from <= from && it.to >= to)) }
            .map { PayoutRecord(it.cents, it.paidOn, it.method, it.reference) }

    /** The way someone was paid last time, to start the next payment with. */
    fun lastMethodOf(userId: String): PayMethod? = payouts.lastOrNull { it.userId == userId && it.method != null }?.method

    /** Takes back what was recorded as paid for [from]..[to], including a payout for a longer stretch around it. */
    fun markUnpaid(userId: String, from: LocalDate, to: LocalDate) {
        payouts.removeAll { it.userId == userId && ((it.from >= from && it.to <= to) || (it.from <= from && it.to >= to)) }
    }
}

/** A monthly salary's share of [from]..[to]: each day earns its month's salary ÷ the days in that month. */
internal fun salaryShare(monthlyCents: Long, from: LocalDate, to: LocalDate): Long {
    var total = 0L
    var start = from
    while (start <= to) {
        val monthEnd = LocalDate(start.year, start.month, 1).plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        val end = minOf(monthEnd, to)
        val days = start.daysUntil(end) + 1L
        val length = monthEnd.day.toLong()
        // Half up, in whole cents.
        total += (monthlyCents * days * 2 + length) / (2 * length)
        start = end.plus(DatePeriod(days = 1))
    }
    return total
}
