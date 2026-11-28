package com.saporini.mobile_desktop.statistics

import com.saporini.mobile_desktop.pos.payment.data.ReceiptDto
import kotlinx.datetime.LocalDate

// Statistics → Sales, from a total down to what made it (user, 2026-11-15: "a button that shows how we got this sale"; then
// "every day its own card with its orders inside, each order expands with its details; that's how the money came in"):
// Sales details lists the period's days, each with its orders and its totals at the end. All read-only.

/** Sales details is only for a month or less (user, 2026-11-15: "if it's one year you can't, only for one month and below"). */
const val DetailsMaxDays = 31

/**
 * One order of a day, for the Day window's list: when it opened and closed (the server's text, shown in the restaurant's
 * time), its table (null = no table), who took it, guests, dishes, what it came to (cents) and how it ended (CLOSED,
 * CANCELLED or VOIDED; [unpaid] = closed as unpaid).
 */
data class StatsOrder(
    val id: String,
    val branchId: String,
    val number: String,
    val openedAt: String,
    val closedAt: String?,
    val table: String?,
    val type: String,
    val staffId: String?,
    val guests: Int?,
    val items: Int?,
    val total: Long,
    val status: String,
    val unpaid: Boolean = false
)

/**
 * Sales details: the day to bring into view, the period's orders (null until loaded; only for people who may see orders),
 * the days whose cards show every order, and the order opened under its line with its receipt.
 */
data class StatsDetails(
    val focus: LocalDate? = null,
    val orders: List<StatsOrder>? = null,
    val ordersLoading: Boolean = false,
    val ordersProblem: String? = null,
    val allShown: Set<LocalDate> = emptySet(),
    val order: StatsOrder? = null,
    val receipt: ReceiptDto? = null,
    val receiptLoading: Boolean = false,
    val receiptProblem: String? = null
)

/** What Finance shows (user, 2026-11-15: "a switch at the top right like Inventory's: Balance, Money in, Money out"). */
enum class FinanceView(val title: String) { BALANCE("Balance"), MONEY_IN("Money in"), MONEY_OUT("Money out") }
