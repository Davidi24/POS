package com.saporini.mobile_desktop.finance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock

// Finance & taxation (user, 2026-11-23: "one more app, finance and taxation; the app helps doing the restaurant's taxes,
// no need to hire someone"). First round = screens only: everything here is MOCK, worked out on the device from made-up
// figures that look like the demo restaurant's month, until the production pass takes sales VAT from the orders, purchases
// from Inventory deliveries, wages from Salary and keeps expenses, filings and the tax setup on the server.

/** The workspace's places, in bar order. */
enum class FinanceTab(val title: String) {
    OVERVIEW("Overview"),
    VAT("VAT"),
    EXPENSES("Expenses"),
    PAYROLL("Payroll"),
    DEADLINES("Deadlines"),
    FILING("Tax returns"),
    SETUP("Tax setup")
}

/**
 * The restaurant's tax rules. Percentages are in tenths (167 = 16.7 %). Defaults are Albania's as we know them (MOCK until
 * checked with an accountant): VAT 20 %, returns and payroll by the 14th and the 20th of the next month, profit tax 15 %.
 */
data class TaxSetup(
    val country: String = "Albania",
    val vatRegistered: Boolean = true,
    val vatEveryMonths: Int = 1,
    val standardRate: Int = 200,
    val reducedRate: Int = 60,
    val vatDueDay: Int = 14,
    val payrollDueDay: Int = 20,
    val profitTaxRate: Int = 150,
    val smallBusiness: Boolean = false,
    val employerSocial: Int = 150,
    val employerHealth: Int = 17,
    val employeeSocial: Int = 95,
    val employeeHealth: Int = 17,
    // Income tax on wages: nothing up to the first amount a month, the middle rate up to the second, the top rate above.
    val incomeFreeUpTo: Long = 50_000,
    val incomeMiddleUpTo: Long = 200_000,
    val incomeMiddleRate: Int = 130,
    val incomeTopRate: Int = 230,
    val setAsidePercent: Int = 12,
    val remindDaysBefore: Int = 5,
    val accountantEmail: String = ""
)

enum class ExpenseKind(val label: String) {
    STOCK("Food and drink"),
    RENT("Rent"),
    POWER("Electricity and water"),
    GAS("Gas"),
    PHONE("Internet and phone"),
    CLEANING("Cleaning"),
    REPAIRS("Repairs"),
    MARKETING("Marketing"),
    FEES("Bank and card fees"),
    ADVICE("Accountant and legal"),
    OTHER("Other")
}

/** One cost with its invoice: [net] without VAT, [vat] the VAT on it (0 when none). */
data class Expense(
    val id: String,
    val date: LocalDate,
    val payee: String,
    val kind: ExpenseKind,
    val net: Long,
    val vat: Long,
    val invoiceNo: String?,
    val hasReceipt: Boolean,
    val fromInventory: Boolean,
    val vatNumberMissing: Boolean = false,
    val deductible: Boolean = true,
    // The branch it was added at (null: the whole restaurant).
    val branchId: String? = null
) {
    val total: Long get() = net + vat
}

/** Sales of one VAT rate in a month: [net] without VAT. */
data class RateLine(val rate: Int, val name: String, val net: Long, val vat: Long)

/** One person's wages in a month and what goes to the tax office. */
data class PayLine(
    val name: String,
    val role: String,
    val gross: Long,
    val employeeContributions: Long,
    val incomeTax: Long,
    val employerContributions: Long,
    val taxNumberMissing: Boolean
) {
    val net: Long get() = gross - employeeContributions - incomeTax
    val toTaxOffice: Long get() = employeeContributions + incomeTax + employerContributions
}

/** One day of the sales book. */
data class SalesDay(val date: LocalDate, val bills: Int, val net: Long, val vat: Long)

/** A month put together: what was sold, bought and paid, and the taxes that come from it. */
data class MonthFigures(
    val month: LocalDate,
    val finished: Boolean,
    val sales: List<RateLine>,
    val salesDays: List<SalesDay>,
    val expenses: List<Expense>,
    val payroll: List<PayLine>
) {
    val salesNet: Long get() = sales.sumOf { it.net }
    val outputVat: Long get() = sales.sumOf { it.vat }
    val inputVat: Long get() = expenses.filter { it.deductible }.sumOf { it.vat }
    val vatToPay: Long get() = outputVat - inputVat
    val expensesNet: Long get() = expenses.sumOf { it.net }
    val wages: Long get() = payroll.sumOf { it.gross }
    val employerContributions: Long get() = payroll.sumOf { it.employerContributions }
    val payrollToPay: Long get() = payroll.sumOf { it.toTaxOffice }
    val profit: Long get() = salesNet - expensesNet - wages - employerContributions
}

/** Something to pay or send by a day. */
data class TaxDeadline(
    val key: String,
    val kind: DeadlineKind,
    val title: String,
    val period: String,
    val due: LocalDate,
    val amount: Long?,
    val month: LocalDate?,
    // From when it can be filed: the day after the period it's about.
    val readyFrom: LocalDate
)

enum class DeadlineKind { VAT, PAYROLL, PROFIT_ADVANCE, PROFIT_YEAR }

enum class FilingState(val label: String) { NOT_YET("Month still running"), READY("Ready to file"), FILED("Filed, to pay"), PAID("Filed and paid") }

/** Things the app noticed that an accountant would ask about, each with the place to fix it. */
data class CheckItem(val title: String, val detail: String, val tab: FinanceTab, val serious: Boolean)

/** MOCK: the finance workspace's state, in memory until the production pass. */
object FinanceBook {
    val today: LocalDate get() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** The month the pages look at (its 1st). */
    var month: LocalDate by mutableStateOf(firstOf(Clock.System.todayIn(TimeZone.currentSystemDefault())))

    var setup: TaxSetup by mutableStateOf(TaxSetup())

    /**
     * The branch looked at (step 26: "Finance & taxation: branch financial records, consolidated reporting"), or null for every
     * branch together. MOCK: a branch's records are its share of the made-up restaurant's, its people a share of them.
     */
    var branchId: String? by mutableStateOf(null)

    /** Expenses added by hand (bills, receipts) on top of the made-up ones. */
    val added = mutableStateListOf<Expense>()

    /** Expenses whose receipt was added afterwards (by id). */
    var receipts: Set<String> by mutableStateOf(emptySet())

    /** Deadlines marked filed or paid by hand (by key). */
    var filed: Set<String> by mutableStateOf(emptySet())
    var paid: Set<String> by mutableStateOf(emptySet())

    fun shiftMonth(by: Int) {
        val next = if (by > 0) month.plus(DatePeriod(months = by)) else month.minus(DatePeriod(months = -by))
        if (next <= firstOf(today)) month = next
    }

    fun addExpense(expense: Expense) = added.add(expense.copy(branchId = branchId))

    fun addReceipt(id: String) { receipts = receipts + id }

    fun markFiled(key: String) { filed = filed + key }

    fun markPaid(key: String) { filed = filed + key; paid = paid + key }

    fun figures(month: LocalDate = this.month, branchId: String? = this.branchId): MonthFigures =
        monthFigures(firstOf(month), setup, today, added.toList(), receipts, partOf(branchId))

    /** MOCK: each branch's share of the made-up figures, the first branches a bit bigger (3 branches: 1/2, 1/3, 1/6). */
    private fun partOf(branchId: String?): BranchPart? {
        val branches = com.saporini.mobile_desktop.core.session.BranchScope.branches
        val index = branchId?.let { id -> branches.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: return null
        val n = branches.size
        return BranchPart(branchId, index, n, (n - index).toDouble() / (n * (n + 1) / 2.0))
    }

    fun deadlines(from: LocalDate = firstOf(today).minus(DatePeriod(months = 3)), months: Int = 9): List<TaxDeadline> =
        taxDeadlines(from, months, setup) { figures(it) }

    fun state(deadline: TaxDeadline): FilingState = when {
        deadline.key in paid -> FilingState.PAID
        deadline.key in filed -> FilingState.FILED
        // MOCK: everything due before today was filed and paid on time.
        deadline.due < today -> FilingState.PAID
        deadline.readyFrom > today -> FilingState.NOT_YET
        else -> FilingState.READY
    }

    fun checks(figures: MonthFigures): List<CheckItem> = buildList {
        val noVatNumber = figures.expenses.filter { it.vatNumberMissing }
        if (noVatNumber.isNotEmpty()) add(CheckItem(
            if (noVatNumber.size == 1) "1 invoice without the supplier's VAT number" else "${noVatNumber.size} invoices without the supplier's VAT number",
            "Without it the VAT on them can't be taken back: ${noVatNumber.joinToString(", ") { it.payee }.take(80)}", FinanceTab.EXPENSES, true))
        val noReceipt = figures.expenses.filter { !it.hasReceipt }
        if (noReceipt.isNotEmpty()) add(CheckItem(
            if (noReceipt.size == 1) "1 expense without a receipt" else "${noReceipt.size} expenses without a receipt",
            "Add a photo of each, so they count as costs", FinanceTab.EXPENSES, false))
        val noTaxNumber = figures.payroll.filter { it.taxNumberMissing }
        if (noTaxNumber.isNotEmpty()) add(CheckItem(
            if (noTaxNumber.size == 1) "${noTaxNumber.first().name} has no personal number" else "${noTaxNumber.size} people have no personal number",
            "The payroll list can't be sent without it", FinanceTab.PAYROLL, true))
        add(CheckItem("4 dishes have no VAT rate", "They're counted at the standard rate until you choose", FinanceTab.SETUP, false))
    }
}

fun firstOf(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

fun lastOf(month: LocalDate): LocalDate = firstOf(month).plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))

/** "November 2026". */
fun monthTitle(month: LocalDate): String = "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}"

/** 167 → "16.7%", 200 → "20%". */
fun pct(tenths: Int): String = if (tenths % 10 == 0) "${tenths / 10}%" else "${tenths / 10}.${abs(tenths % 10)}%"

fun share(amount: Long, tenths: Int): Long = (amount * tenths / 1000.0).roundToLong()

// ---- Made-up months (MOCK) ----

private val Suppliers = listOf(
    Triple("Agro Fresh Tirana", ExpenseKind.STOCK, 0.27),
    Triple("Meat House Durrës", ExpenseKind.STOCK, 0.24),
    Triple("Olive Grove Berat", ExpenseKind.STOCK, 0.09),
    Triple("Dairy Korça", ExpenseKind.STOCK, 0.12),
    Triple("Mokka Roasters Tirana", ExpenseKind.STOCK, 0.07),
    Triple("Drinks Wholesale Shqipëria", ExpenseKind.STOCK, 0.21)
)

private val People = listOf(
    Triple("Ilir Marku", "Manager", 140_000L), Triple("Arben Hoxha", "Kitchen", 95_000L), Triple("Mira Leka", "Kitchen", 88_000L),
    Triple("Dritan Gjoka", "Waiter", 62_000L), Triple("Sara Basha", "Waiter", 60_000L), Triple("Klea Dervishi", "Waiter", 58_000L),
    Triple("Erion Kapllani", "Manager", 130_000L), Triple("Besa Muça", "Kitchen", 82_000L), Triple("Anisa Çela", "Waiter", 55_000L),
    Triple("Mimoza Daci", "Cleaner", 45_000L)
)

/** A steady wobble per month, so each month looks a little different but always the same. */
private fun wobble(month: LocalDate, salt: Int): Double {
    val seed = (month.year * 13 + month.month.ordinal * 7 + salt * 31) % 17
    return 0.9 + seed / 80.0
}

/** One branch's part of the restaurant's figures (MOCK). */
internal data class BranchPart(val id: String, val index: Int, val count: Int, val factor: Double)

internal fun monthFigures(month: LocalDate, setup: TaxSetup, today: LocalDate, added: List<Expense>, receipts: Set<String>,
                          part: BranchPart? = null): MonthFigures {
    val factor = part?.factor ?: 1.0
    val running = month == firstOf(today)
    val lastDay = lastOf(month)
    val days = if (running) today.day else lastDay.day
    // About 2,600 a day before VAT (in cents of the restaurant's currency), more at the weekend.
    val salesDays = (1..days).map { d ->
        val date = LocalDate(month.year, month.month, d)
        val weekend = date.dayOfWeek.ordinal >= 4
        val net = (260_000 * wobble(month, d) * (if (weekend) 1.35 else 1.0) * factor).roundToLong()
        SalesDay(date, (net / 2_300).toInt(), net, share(net, setup.standardRate))
    }
    val standardNet = salesDays.sumOf { it.net }
    val zeroNet = (standardNet * 0.012).roundToLong()
    val sales = listOf(
        RateLine(setup.standardRate, "Standard", standardNet, share(standardNet, setup.standardRate)),
        RateLine(setup.reducedRate, "Reduced", 0, 0),
        RateLine(0, "No VAT (gift cards, deposits)", zeroNet, 0)
    )
    val monthPart = days / lastDay.day.toDouble()
    val stockNet = (standardNet * 0.31).roundToLong()
    val expenses = buildList {
        Suppliers.forEachIndexed { i, (name, kind, cut) ->
            // Two or three deliveries a month from each.
            val deliveries = if (cut > 0.15) 3 else 2
            repeat(deliveries) { n ->
                val day = (4 + i * 3 + n * 10).coerceAtMost(days).coerceAtLeast(1)
                val net = (stockNet * cut / deliveries * wobble(month, i + n)).roundToLong()
                add(Expense("inv-${month}-$i-$n", LocalDate(month.year, month.month, day), name, kind, net, share(net, setup.standardRate),
                    "F-${(month.month.ordinal + 1) * 100 + i * 10 + n}", hasReceipt = true, fromInventory = true,
                    vatNumberMissing = i == 3 && n == 1 && running))
            }
        }
        fun bill(id: String, day: Int, payee: String, kind: ExpenseKind, net: Long, vatRate: Int, receipt: Boolean = true) {
            if (day > days) return
            val amount = (net * wobble(month, day) * factor).roundToLong()
            val key = "$id-$month"
            add(Expense(key, LocalDate(month.year, month.month, day), payee, kind, amount, share(amount, vatRate), "B-$day${kind.ordinal}",
                hasReceipt = receipt || key in receipts, fromInventory = false))
        }
        bill("rent", 1, "Rruga e Kavajës Properties", ExpenseKind.RENT, 650_000, 0)
        bill("power", 9, "City Power", ExpenseKind.POWER, 182_000, 200)
        bill("water", 11, "Tirana Water", ExpenseKind.POWER, 38_000, 200)
        bill("gas", 6, "Gas Albania", ExpenseKind.GAS, 74_000, 200)
        bill("phone", 15, "Net Albania", ExpenseKind.PHONE, 9_900, 200)
        bill("clean", 18, "Clean Pro", ExpenseKind.CLEANING, 32_000, 200, receipt = !running)
        bill("repair", 21, "Fix It Tirana", ExpenseKind.REPAIRS, 26_000, 200, receipt = !running)
        bill("ads", 3, "Social media ads", ExpenseKind.MARKETING, 21_000, 0)
        bill("fees", lastDay.day.coerceAtMost(days), "Card terminal fees", ExpenseKind.FEES, (standardNet * 0.011 * monthPart / factor).roundToLong(), 0)
        bill("advice", 25, "Accountant", ExpenseKind.ADVICE, 25_000, 200)
        addAll(added.filter { firstOf(it.date) == month && (part == null || it.branchId == part.id) }
            .map { it.copy(hasReceipt = it.hasReceipt || it.id in receipts) })
    }.sortedBy { it.date }
    val payroll = People.withIndex().filter { (i, _) -> part == null || i % part.count == part.index }.map { (i, person) ->
        val (name, role, monthly) = person
        // The restaurant's own currency: wages scaled to look like euros (MOCK).
        val gross = (monthly * 1.0 * wobble(month, i) / 1.0).roundToLong().let { it - it % 100 }
        val employee = share(gross, setup.employeeSocial + setup.employeeHealth)
        PayLine(name, role, gross, employee, incomeTax(gross, setup), share(gross, setup.employerSocial + setup.employerHealth),
            taxNumberMissing = i == 9)
    }
    return MonthFigures(month, !running, sales, salesDays, expenses, payroll)
}

/** Income tax on a month's wage: nothing up to the first band, the middle rate on the part up to the second, the top rate above. */
internal fun incomeTax(gross: Long, setup: TaxSetup): Long {
    val middle = (gross.coerceAtMost(setup.incomeMiddleUpTo) - setup.incomeFreeUpTo).coerceAtLeast(0)
    val top = (gross - setup.incomeMiddleUpTo).coerceAtLeast(0)
    return share(middle, setup.incomeMiddleRate) + share(top, setup.incomeTopRate)
}

internal fun taxDeadlines(from: LocalDate, months: Int, setup: TaxSetup, figures: (LocalDate) -> MonthFigures): List<TaxDeadline> = buildList {
    (0 until months).forEach { i ->
        val month = firstOf(from).plus(DatePeriod(months = i))
        val next = month.plus(DatePeriod(months = 1))
        val f = figures(month)
        if (setup.vatRegistered && (month.month.ordinal + 1) % setup.vatEveryMonths == 0) {
            add(TaxDeadline("VAT-$month", DeadlineKind.VAT, "VAT return and payment", monthTitle(month),
                LocalDate(next.year, next.month, setup.vatDueDay), f.vatToPay.coerceAtLeast(0), month, next))
        }
        add(TaxDeadline("PAY-$month", DeadlineKind.PAYROLL, "Payroll list, contributions and income tax", monthTitle(month),
            LocalDate(next.year, next.month, setup.payrollDueDay), f.payrollToPay, month, next))
        // Profit tax advances after each quarter (MOCK: a quarter of last year's tax).
        if ((month.month.ordinal + 1) % 3 == 0) {
            add(TaxDeadline("ADV-$month", DeadlineKind.PROFIT_ADVANCE, "Profit tax advance", "Quarter ${(month.month.ordinal + 1) / 3} ${month.year}",
                LocalDate(next.year, next.month, 15), if (setup.smallBusiness) 0 else (share(f.profit * 3, setup.profitTaxRate) / 2).coerceAtLeast(0), null, next))
        }
        if (month.month.ordinal == 2) {
            add(TaxDeadline("YEAR-${month.year - 1}", DeadlineKind.PROFIT_YEAR, "Yearly profit tax return and accounts", "${month.year - 1}",
                LocalDate(month.year, month.month, 31), null, null, LocalDate(month.year, 1, 1)))
        }
    }
}.sortedBy { it.due }
