package com.saporini.mobile_desktop.gallery

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.shifts.HoursAndPayContent
import com.saporini.mobile_desktop.pos.shifts.PayRate
import com.saporini.mobile_desktop.pos.shifts.PayReport
import com.saporini.mobile_desktop.pos.shifts.SalaryPlans
import com.saporini.mobile_desktop.pos.shifts.ShiftAction
import com.saporini.mobile_desktop.pos.shifts.ShiftBoard
import com.saporini.mobile_desktop.pos.shifts.ShiftBreakRequest
import com.saporini.mobile_desktop.pos.shifts.ShiftCorrection
import com.saporini.mobile_desktop.pos.shifts.ShiftPayLine
import com.saporini.mobile_desktop.pos.shifts.ShiftRepository
import com.saporini.mobile_desktop.pos.shifts.ShiftSchedule
import com.saporini.mobile_desktop.pos.shifts.ShiftScreenModel
import com.saporini.mobile_desktop.pos.shifts.ShiftStaff
import com.saporini.mobile_desktop.pos.shifts.StaffPay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

// Look check only (screens round): Admin Hub → Salary at desktop, tablet and phone, a person's details on a phone and
// the pay dialog, into build/reports/gallery/salary-*.png.

private val SalaryZone = TimeZone.of("Europe/Tirane")

private class SalaryShifts : ShiftRepository {
    // name, role, hourly wage (null = none), shifts in the period, minutes per shift, tips per shift
    private val team = listOf(
        Triple("anisa", "Anisa Çela", "WAITER") to Triple("11.50", 9, 7 * 60 + 30),
        Triple("besa", "Besa Muça", "KITCHEN") to Triple("14.00", 12, 8 * 60),
        Triple("ema", "Ema Shkurti", "WAITER") to Triple("11.00", 6, 6 * 60),
        Triple("erion", "Erion Kapllani", "MANAGER") to Triple("15.00", 14, 9 * 60),
        Triple("gentian", "Gentian Prifti", "KITCHEN") to Triple("12.00", 0, 0),
        Triple("klea", "Klea Dervishi", "WAITER") to Triple(null, 4, 5 * 60),
        Triple("luan", "Luan Berisha", "WAITER") to Triple("11.50", 10, 7 * 60)
    )

    override suspend fun board(restaurant: String, branch: String, from: String, to: String, mine: Boolean) =
        ShiftBoard("Europe/Tirane", Clock.System.now().toString(), emptyList(), staff = team.map { (who, _) -> ShiftStaff(who.first, who.second, listOf(who.third)) })

    override suspend fun pay(restaurant: String, branch: String, from: String, to: String, mine: Boolean): PayReport {
        val start = LocalDate.parse(from)
        val days = generateSequence(start) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= LocalDate.parse(to) }.toList()
        return PayReport("Europe/Tirane", "EUR", from, to, Clock.System.now().toString(), team.map { (who, pay) ->
            val (rate, count, minutes) = pay
            val worked = days.filterIndexed { index, _ -> index % 3 != 2 }.take(count * days.size / 31)
            val lines = worked.map { day ->
                val begin = day.atTime(LocalTime(if (who.third == "KITCHEN") 10 else 16, 0)).toInstant(SalaryZone)
                ShiftPayLine("${who.first}-$day", day.toString(), "CLOSED", begin.toString(), (begin + (minutes + 30).minutes).toString(),
                    minutes.toLong(), 30, rate?.let(::OrderDecimal), rate?.let { OrderDecimal(String.format("%.2f", it.toDouble() * minutes / 60.0)) })
            }
            val totalMinutes = lines.sumOf { it.workedMinutes }
            val wages = rate?.let { String.format("%.2f", it.toDouble() * totalMinutes / 60.0) } ?: "0.00"
            val tips = if (who.third == "WAITER") String.format("%.2f", lines.size * 6.5) else "0.00"
            StaffPay(who.first, who.second, listOf(who.third), rate?.let(::OrderDecimal), lines.size, totalMinutes, lines.size * 30L,
                OrderDecimal(wages), OrderDecimal(tips), OrderDecimal(String.format("%.2f", wages.toDouble() + tips.toDouble())),
                missingRate = rate == null && totalMinutes > 0, shifts = lines)
        // Two people on a monthly salary.
        }.map { person ->
            mapOf("erion" to "1800.00", "besa" to "1400.00")[person.userId]?.let { person.copy(hourlyRate = null, wages = OrderDecimal.ZERO, missingRate = false, monthlySalary = OrderDecimal(it)) } ?: person
        })
    }

    override suspend fun setPayRate(restaurant: String, branch: String, userId: String, rate: OrderDecimal) = PayRate(rate)
    override suspend fun schedule(restaurant: String, branch: String, id: String?, request: ShiftSchedule) = error("unused")
    override suspend fun clockIn(restaurant: String, branch: String, id: String?) = error("unused")
    override suspend fun action(restaurant: String, branch: String, id: String, action: String, request: ShiftAction) = error("unused")
    override suspend fun startBreak(restaurant: String, branch: String, id: String, request: ShiftBreakRequest) = error("unused")
    override suspend fun correct(restaurant: String, branch: String, id: String, request: ShiftCorrection) = error("unused")
}

private fun salarySession() = SessionManager().apply {
    signIn(CurrentUserResponse(
        id = "erion", restaurantId = "r1", defaultBranchId = "b1", email = "x@pos.local", username = "x", firstName = "Erion", lastName = "Kapllani",
        isActive = true, emailVerified = true, phoneVerified = true, roles = listOf("MANAGER"),
        permissions = listOf("SHIFT_SELF", "SHIFT_READ", "SHIFT_MANAGE", "ADMIN_ACCESS")
    ))
}

class SalaryGalleryTest {
    @Test
    fun salaryAtEverySize() = gallery {
        val repository = SalaryShifts()
        val session = salarySession()
        val model = ShiftScreenModel(repository, session)
        model.start(true); settle()
        // Ema helps out for now (an extra worker) and Gentian cleans, and two people are already paid this month (kept in memory for now).
        SalaryPlans.setTeam("ema", "EXTRA"); SalaryPlans.setTeam("gentian", "CLEANING")
        val today = Clock.System.now().toLocalDateTime(SalaryZone).date
        val monthStart = LocalDate(today.year, today.month, 1)
        val monthEnd = monthStart.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        SalaryPlans.markPaid("luan", monthStart, monthEnd, 10_000_000)
        SalaryPlans.markPaid("besa", monthStart, monthEnd, 50_000)
        org.koin.core.context.startKoin { modules(org.koin.dsl.module { single<ShiftRepository> { repository }; single { session } }) }
        try {
            listOf("desktop" to (1440 to 960), "tablet" to (1024 to 1200), "phone" to (390 to 1500), "phone-screen" to (390 to 844)).forEach { (name, size) ->
                render("salary-$name", size.first, size.second, required = listOf("Salary", "Left to pay", "Workers to pay", "Managers", "Kitchen", "Cleaning", "Extra workers")) {
                    val state by model.state.collectAsState()
                    HoursAndPayContent(state)
                }
            }
            listOf("desktop" to (1440 to 960), "tablet" to (1024 to 1100), "phone" to (390 to 1200)).forEach { (name, size) ->
                render("salary-team-$name", size.first, size.second, required = listOf("Waiters", "Anisa Çela", "Pay selected")) {
                    val state by model.state.collectAsState()
                    HoursAndPayContent(state, startTeam = "WAITERS")
                }
            }
            // The responsive pass: phones (360, 412), small tablets (600, 768), a tablet (1024) and a laptop (1280).
            listOf(360 to 1500, 412 to 1500, 600 to 1300, 768 to 1200, 1024 to 900, 1280 to 900).forEach { (width, height) ->
                render("size-salary-$width", width, height, required = listOf("Left to pay", "Managers", "Extra workers")) {
                    val state by model.state.collectAsState()
                    HoursAndPayContent(state)
                }
            }
            listOf(360 to 900, 600 to 900, 768 to 900, 1024 to 900).forEach { (width, height) ->
                render("size-salary-team-$width", width, height, required = listOf("Anisa Çela", "Pay selected")) {
                    val state by model.state.collectAsState()
                    HoursAndPayContent(state, startTeam = "WAITERS")
                }
            }
            render("size-salary-pay-360", 360, 900, required = listOf("Pay Anisa Çela", "Cash")) {
                val state by model.state.collectAsState()
                HoursAndPayContent(state, startTeam = "WAITERS", startPayingPerson = "anisa")
            }
            render("size-salary-person-360", 360, 800, required = listOf("Anisa Çela", "Left to pay")) {
                val state by model.state.collectAsState()
                HoursAndPayContent(state, startOpen = "anisa")
            }
            render("salary-pay-all", required = listOf("Mark as paid", "No money is sent")) {
                val state by model.state.collectAsState()
                HoursAndPayContent(state, startTeam = "WAITERS", startPaying = "WAITERS")
            }
            render("salary-person-phone", 390, 844, required = listOf("Anisa Çela", "Paid by the hour", "Left to pay", "Pay")) {
                val state by model.state.collectAsState()
                HoursAndPayContent(state, startOpen = "anisa")
            }
            render("salary-pay-person", 1440, 1000, required = listOf("Pay Anisa Çela", "Amount", "Cash", "Bank transfer", "Left to pay")) {
                val state by model.state.collectAsState()
                HoursAndPayContent(state, startTeam = "WAITERS", startPayingPerson = "anisa")
            }
            render("salary-person-hourly", 1440, 1000, required = listOf("Paid by the hour", "Wages", "Show all")) {
                val state by model.state.collectAsState()
                HoursAndPayContent(state, startTeam = "MANAGERS", startOpen = "luan")
            }
            render("salary-person-monthly", 1440, 1000, required = listOf("Monthly salary", "Not paid per shift", "Left to pay")) {
                val state by model.state.collectAsState()
                HoursAndPayContent(state, startTeam = "MANAGERS", startOpen = "erion")
            }
            render("salary-person-not-set", 1440, 1000, required = listOf("Set up their pay first", "Set up pay")) {
                val state by model.state.collectAsState()
                HoursAndPayContent(state, startTeam = "WAITERS", startOpen = "klea")
            }
            render("salary-edit", required = listOf("By the hour", "Monthly salary")) {
                val state by model.state.collectAsState()
                HoursAndPayContent(state, startEditing = "erion")
            }
            render("salary-edit-phone", 390, 844, required = listOf("By the hour", "Monthly salary")) {
                val state by model.state.collectAsState()
                HoursAndPayContent(state, startEditing = "klea")
            }
            // A part already paid by bank transfer: the dialog lists it under "Paid so far" and fills in what's left.
            SalaryPlans.markPaid("anisa", monthStart, monthEnd, 20_000, today, com.saporini.mobile_desktop.pos.shifts.PayMethod.BANK, "TRF-2291")
            render("salary-pay-person-history", 1440, 1100, required = listOf("Pay Anisa Çela", "Paid so far", "TRF-2291")) {
                val state by model.state.collectAsState()
                HoursAndPayContent(state, startTeam = "WAITERS", startPayingPerson = "anisa")
            }
        } finally {
            org.koin.core.context.stopKoin()
            SalaryPlans.setTeam("ema", null); SalaryPlans.setTeam("gentian", null)
            SalaryPlans.markUnpaid("luan", monthStart, monthEnd); SalaryPlans.markUnpaid("besa", monthStart, monthEnd)
            SalaryPlans.markUnpaid("anisa", monthStart, monthEnd)
        }
        model.onDispose()
    }
}
