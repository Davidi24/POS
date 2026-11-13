package com.saporini.mobile_desktop.gallery

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.pos.shifts.localDate
import androidx.compose.runtime.getValue
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.shifts.PayRate
import com.saporini.mobile_desktop.pos.shifts.PayReport
import com.saporini.mobile_desktop.pos.shifts.PlanShiftsDialog
import com.saporini.mobile_desktop.pos.shifts.ShiftTemplateDialog
import com.saporini.mobile_desktop.pos.shifts.ShiftAction
import com.saporini.mobile_desktop.pos.shifts.ShiftBoard
import com.saporini.mobile_desktop.pos.shifts.ShiftBreakRequest
import com.saporini.mobile_desktop.pos.shifts.ShiftContent
import com.saporini.mobile_desktop.pos.shifts.ShiftCorrection
import com.saporini.mobile_desktop.pos.shifts.ShiftItem
import com.saporini.mobile_desktop.pos.shifts.ShiftRepository
import com.saporini.mobile_desktop.pos.shifts.ShiftSchedule
import com.saporini.mobile_desktop.pos.shifts.ShiftScreenModel
import com.saporini.mobile_desktop.pos.shifts.ShiftStaff
import com.saporini.mobile_desktop.pos.shifts.ShiftTimesDialog
import com.saporini.mobile_desktop.pos.shifts.StaffPay
import com.saporini.mobile_desktop.pos.shifts.weekStart
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.time.Clock

// Look check only (screens round): Admin Hub → Shifts (Team, Timeline, Month, Hours & pay), the planner and the shift
// times at phone, tablet and desktop sizes, and POS → My shift → Team for a waiter, into build/reports/gallery/shifts-*.png.

private val Zone = TimeZone.of("Europe/Tirane")

internal class GalleryShifts : ShiftRepository {
    private val now = Clock.System.now()
    val staff = listOf(
        ShiftStaff("ilir", "Ilir Marku", listOf("Manager")), ShiftStaff("dm", "Demo Manager", listOf("Manager")),
        ShiftStaff("arben", "Arben Hoxha", listOf("Kitchen")), ShiftStaff("mira", "Mira Leka", listOf("Kitchen")), ShiftStaff("dk", "Demo Kitchen", listOf("Kitchen")),
        ShiftStaff("sara", "Sara Basha", listOf("Waiter")), ShiftStaff("dritan", "Dritan Gjoka", listOf("Waiter")), ShiftStaff("klea", "Klea Dervishi", listOf("Waiter")),
        ShiftStaff("dw", "Demo Waiter", listOf("Waiter")), ShiftStaff("owner", "Bistro Owner", listOf("Owner"))
    )
    private fun t(h: Int, m: Int = 0) = LocalTime(h, m)
    private val week: Map<String, Map<Int, Pair<LocalTime, LocalTime>>> = mapOf(
        "arben" to mapOf(0 to (t(9) to t(17)), 1 to (t(9) to t(17)), 2 to (t(9) to t(17)), 4 to (t(15) to t(23)), 5 to (t(15) to t(23))),
        "dk" to mapOf(0 to (t(15) to t(23)), 1 to (t(15) to t(23)), 3 to (t(9) to t(17)), 4 to (t(9) to t(17)), 6 to (t(9) to t(17))),
        "mira" to mapOf(2 to (t(15) to t(23)), 3 to (t(15) to t(23)), 4 to (t(17) to t(0)), 5 to (t(9) to t(17)), 6 to (t(15) to t(23))),
        "dw" to mapOf(0 to (t(10) to t(16)), 1 to (t(10) to t(16)), 2 to (t(16) to t(23, 30)), 3 to (t(16) to t(23, 30)), 5 to (t(16) to t(23, 30))),
        "dritan" to mapOf(0 to (t(16) to t(23, 30)), 2 to (t(10) to t(16)), 3 to (t(10) to t(16)), 4 to (t(16) to t(23, 30)), 6 to (t(10) to t(16))),
        "sara" to mapOf(0 to (t(10) to t(16)), 1 to (t(16) to t(23, 30)), 4 to (t(10) to t(16)), 5 to (t(10) to t(16)), 6 to (t(16) to t(23, 30))),
        "klea" to mapOf(0 to (t(13) to t(21)), 2 to (t(16) to t(23, 30)), 3 to (t(17) to t(0, 30)), 4 to (t(17) to t(0, 30)), 5 to (t(17) to t(0, 30))),
        "dm" to mapOf(0 to (t(9, 30) to t(17, 30)), 1 to (t(9, 30) to t(17, 30)), 3 to (t(9, 30) to t(17, 30)), 4 to (t(15) to t(23, 30)), 5 to (t(15) to t(23, 30))),
        "ilir" to mapOf(0 to (t(15) to t(23, 30)), 2 to (t(9, 30) to t(17, 30)), 3 to (t(15) to t(23, 30)), 5 to (t(9, 30) to t(17, 30)), 6 to (t(9, 30) to t(17, 30)))
    )
    private val notes = mapOf("klea" to "Private dinner for 24 guests at 20:00", "arben" to "Fish delivery 08:30")

    private fun items(from: LocalDate, to: LocalDate): List<ShiftItem> {
        val out = mutableListOf<ShiftItem>()
        var day = from
        var breakGiven = false
        var lateGiven = false
        while (day <= to) {
            for ((id, pattern) in week) {
                val times = pattern[day.dayOfWeek.ordinal] ?: continue
                val person = staff.first { it.id == id }
                val start = LocalDateTime(day, times.first).toInstant(Zone)
                val end = LocalDateTime(if (times.second <= times.first) day.plus(DatePeriod(days = 1)) else day, times.second).toInstant(Zone)
                var status = when { end <= now -> "CLOSED"; start <= now -> "OPEN"; else -> "SCHEDULED" }
                if (status == "OPEN" && !lateGiven && id == "klea") { status = "SCHEDULED"; lateGiven = true }
                if (status == "OPEN" && !breakGiven && id == "sara") { status = "ON_BREAK"; breakGiven = true }
                if (status == "CLOSED" && id == "dritan" && day.dayOfWeek.ordinal == 2) status = "MISSED"
                val worked = when (status) { "CLOSED" -> (end - start).inWholeMinutes - 30; "OPEN", "ON_BREAK" -> (now - start).inWholeMinutes; else -> 0 }
                out += ShiftItem("$id-$day", 0, id, person.name, status, start.toString(), end.toString(),
                    startedAt = if (status in setOf("CLOSED", "OPEN", "ON_BREAK")) start.toString() else null,
                    endedAt = if (status == "CLOSED") end.toString() else null, workedMinutes = worked, breakMinutes = if (status == "CLOSED") 30 else 0,
                    notes = notes[id]?.takeIf { day.dayOfWeek.ordinal == 2 })
            }
            day = day.plus(DatePeriod(days = 1))
        }
        return out
    }

    var waiter = false
    var monthly = false
    override suspend fun board(restaurant: String, branch: String, from: String, to: String, mine: Boolean): ShiftBoard {
        val all = items(LocalDate.parse(from), LocalDate.parse(to))
        val me = if (waiter) "sara" else "ilir"
        // A waiter gets the team too, without anyone above them (the server filters by level).
        val team = all.filter { !waiter || it.userName != "Arjan Dibra" }
        return ShiftBoard("Europe/Tirane", now.toString(), if (mine) all.filter { it.userId == me } else team,
            current = all.firstOrNull { it.userId == me && (it.status == "OPEN" || it.status == "ON_BREAK") },
            staff = if (mine) emptyList() else staff.filter { !waiter || it.name != "Arjan Dibra" })
    }
    override suspend fun schedule(restaurant: String, branch: String, id: String?, request: ShiftSchedule) = error("unused")
    override suspend fun clockIn(restaurant: String, branch: String, id: String?) = error("unused")
    override suspend fun action(restaurant: String, branch: String, id: String, action: String, request: ShiftAction) = error("unused")
    override suspend fun startBreak(restaurant: String, branch: String, id: String, request: ShiftBreakRequest) = error("unused")
    override suspend fun correct(restaurant: String, branch: String, id: String, request: ShiftCorrection) = error("unused")
    override suspend fun pay(restaurant: String, branch: String, from: String, to: String, mine: Boolean): PayReport {
        val all = items(LocalDate.parse(from), LocalDate.parse(to))
        val rates = mapOf("arben" to "17.50", "mira" to "12.00", "dritan" to "11.50", "sara" to "11.50", "ilir" to "15.00", "dm" to "16.00", "dk" to "13.00", "dw" to "12.50")
        return PayReport("Europe/Tirane", "EUR", from, to, now.toString(), staff.map { person ->
            val worked = all.filter { it.userId == person.id }.sumOf { it.workedMinutes }
            val rate = rates[person.id]
            val wages = rate?.let { OrderDecimal(String.format("%.2f", it.toDouble() * worked / 60.0)) } ?: OrderDecimal.ZERO
            val lines = all.filter { it.userId == person.id && it.startedAt != null }.map { item ->
                com.saporini.mobile_desktop.pos.shifts.ShiftPayLine(item.id, item.start!!.substring(0, 10), item.status, item.startedAt, item.endedAt, item.workedMinutes,
                    item.breakMinutes, rate?.let(::OrderDecimal), rate?.let { OrderDecimal(String.format("%.2f", it.toDouble() * item.workedMinutes / 60.0)) })
            }
            val tipsByDay = lines.take(3).map { com.saporini.mobile_desktop.pos.shifts.DayTips(it.date, OrderDecimal("14.50")) }
            if (monthly) StaffPay(person.id, person.name, person.roles, null, lines.size, worked, tips = OrderDecimal(String.format("%.2f", tipsByDay.size * 14.5)),
                monthlySalary = OrderDecimal("1100.00"), tipsByDay = tipsByDay,
                shifts = lines.map { it.copy(hourlyRate = null, wages = null) })
            else StaffPay(person.id, person.name, person.roles, rate?.let(::OrderDecimal), lines.size, worked,
                wages = wages, total = wages, missingRate = rate == null && worked > 0, shifts = lines)
        }.filter { !mine || it.userId == "sara" })
    }
    override suspend fun setPayRate(restaurant: String, branch: String, userId: String, rate: OrderDecimal) = PayRate(rate)
}

internal fun shiftSession(waiter: Boolean) = SessionManager().apply {
    signIn(CurrentUserResponse(
        id = if (waiter) "sara" else "ilir", restaurantId = "r1", defaultBranchId = "b1", email = "x@pos.local", username = "x",
        firstName = if (waiter) "Sara" else "Ilir", lastName = if (waiter) "Basha" else "Marku", isActive = true, emailVerified = true, phoneVerified = true,
        roles = listOf(if (waiter) "WAITER" else "MANAGER"),
        permissions = if (waiter) listOf("SHIFT_SELF", "POS_ACCESS") else listOf("SHIFT_SELF", "SHIFT_READ", "SHIFT_MANAGE", "ADMIN_ACCESS")
    ))
}

/** The usual shifts the look checks show; a restaurant starts with none. */
internal fun fillUsualShifts(withRoles: Boolean = false) = com.saporini.mobile_desktop.pos.shifts.ShiftTemplates.fill(com.saporini.mobile_desktop.pos.shifts.ShiftTemplatesDto(
    roles = if (!withRoles) emptyList() else listOf("r-m" to "Manager" to "MANAGER", "r-w" to "Waiter" to "WAITER", "r-k" to "Kitchen" to "KITCHEN")
        .map { (idName, code) -> com.saporini.mobile_desktop.pos.shifts.TemplateRoleDto(idName.first, idName.second, code) },
    templates = listOf("morning" to "Morning" to ("09:00:00" to "17:00:00"), "lunch" to "Lunch" to ("10:00:00" to "16:00:00"),
        "afternoon" to "Afternoon" to ("15:00:00" to "23:00:00"), "evening" to "Evening" to ("16:00:00" to "23:30:00"),
        "closing" to "Closing" to ("17:00:00" to "00:30:00"))
        .map { (idName, times) -> com.saporini.mobile_desktop.pos.shifts.ShiftTemplateDto(idName.first, idName.second, times.first, times.second, weekdays = (0..6).toList(), startsOn = "2026-01-01") }))

class ShiftsGalleryTest {
    @Test
    fun scheduleAtEverySize() = gallery {
        fillUsualShifts(withRoles = true)
        val model = ShiftScreenModel(GalleryShifts(), shiftSession(waiter = false))
        model.start(true); settle()
        model.date(model.state.value.date, true); settle()
        listOf("desktop" to (1440 to 1900), "big" to (1920 to 1700), "tablet" to (1024 to 1800), "phone" to (390 to 1900), "phone-screen" to (390 to 844)).forEach { (name, size) ->
            render("shifts-month-$name", size.first, size.second, required = listOf("Shifts", if (name.startsWith("phone")) "Plan" else "Plan shifts", "Search staff")) {
                val state by model.state.collectAsState()
                ShiftContent(state, true, model)
            }
            settle()
        }
        // A day opened from the month: who works, and who can still be added.
        val today = kotlin.time.Clock.System.now().toLocalDateTime(model.state.value.zone).date
        listOf("desktop" to (1440 to 1100), "phone" to (390 to 1200)).forEach { (name, size) ->
            render("shifts-day-$name", size.first, size.second, required = listOf("No shift", "Table", "Manager", "Kitchen"), absent = listOf("In a shift")) {
                val state by model.state.collectAsState()
                ShiftContent(state, true, model, openDay = today)
            }
            // Everyone on the clock (user, 2026-10-31).
            render("shifts-timeline-$name", size.first, 700, required = listOf("Team", ":00")) {
                val state by model.state.collectAsState()
                val zone = state.zone
                val items = state.board?.items.orEmpty().filter { it.start.localDate(zone) == today }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) {
                    com.saporini.mobile_desktop.pos.shifts.DayTimeline(listOf("Team" to items), today, today, kotlin.time.Clock.System.now(), zone) {}
                }
            }
        }
        // No usual shifts yet (user, 2026-10-31): a day with shifts opens on Specific hours, an empty one offers Add shift.
        com.saporini.mobile_desktop.pos.shifts.ShiftTemplates.fill(com.saporini.mobile_desktop.pos.shifts.ShiftTemplatesDto(templates = emptyList()))
        render("shifts-day-none-desktop", 1440, 1100, required = listOf("No shift")) {
            val state by model.state.collectAsState()
            ShiftContent(state, true, model, openDay = today)
        }
        render("shifts-day-none-empty-desktop", 1440, 1100, required = listOf("No shift")) {
            val state by model.state.collectAsState()
            ShiftContent(state, true, model, openDay = today.plus(DatePeriod(days = 5)))
        }
        fillUsualShifts()
        // Hours & pay takes its repository and the session from Koin.
        val repository = GalleryShifts()
        org.koin.core.context.startKoin { modules(org.koin.dsl.module { single<ShiftRepository> { repository }; single { shiftSession(waiter = false) } }) }
        try {
            listOf("desktop" to (1440 to 960), "phone" to (390 to 1500)).forEach { (name, size) ->
                render("shifts-pay-$name", size.first, size.second) {
                    val state by model.state.collectAsState()
                    com.saporini.mobile_desktop.pos.shifts.HoursAndPayContent(state)
                }
            }
        } finally { org.koin.core.context.stopKoin() }
        model.onDispose()
    }

    @Test
    fun plannerAndShiftTimes() = gallery {
        fillUsualShifts()
        val repository = GalleryShifts()
        val model = ShiftScreenModel(repository, shiftSession(waiter = false))
        model.start(true); settle()
        val today = Clock.System.now().toLocalDateTime(Zone).date
        listOf("desktop" to (1440 to 1100), "phone" to (390 to 1400)).forEach { (name, size) ->
            render("shifts-planner-$name", size.first, size.second, required = listOf("Plan shifts", "Shift", "Who", "Take turns", "Days", "How long", "Save changes"), absent = listOf("Repeat", "Add shifts")) {
                val state by model.state.collectAsState()
                PlanShiftsDialog(state, repository.staff.filter { it.id != "owner" }, Clock.System.now(), today, initialTemplate = "morning", onDismiss = {})
            }
            // A restaurant starts with no usual shifts (user, 2026-10-31).
            com.saporini.mobile_desktop.pos.shifts.ShiftTemplates.fill(com.saporini.mobile_desktop.pos.shifts.ShiftTemplatesDto(templates = emptyList()))
            render("shifts-planner-none-$name", size.first, size.second, required = listOf("No shifts for them yet", "Add shift"), absent = listOf("Other times")) {
                val state by model.state.collectAsState()
                PlanShiftsDialog(state, repository.staff.filter { it.id != "owner" }, Clock.System.now(), today, onDismiss = {})
            }
            fillUsualShifts()
            // The planner opens on the shift cards first (user, 2026-10-31).
            render("shifts-planner-pick-$name", size.first, size.second, required = listOf("Pick the shift", "Add shift"), absent = listOf("Other times")) {
                val state by model.state.collectAsState()
                PlanShiftsDialog(state, repository.staff.filter { it.id != "owner" }, Clock.System.now(), today, onDismiss = {})
            }
            render("shifts-times-$name", size.first, if (name == "phone") 1000 else 900) { ShiftTimesDialog {} }
            // Whose shifts first, when the restaurant's paid roles are known (user, 2026-10-31).
            com.saporini.mobile_desktop.pos.shifts.ShiftTemplates.fill(com.saporini.mobile_desktop.pos.shifts.ShiftTemplatesDto(
                templates = listOf(
                    com.saporini.mobile_desktop.pos.shifts.ShiftTemplateDto("t1", "Waiters' morning", "09:00:00", "17:00:00",
                        listOf(com.saporini.mobile_desktop.pos.shifts.TemplateRoleDto("r-w", "Waiter", "WAITER")), "coffee"),
                    com.saporini.mobile_desktop.pos.shifts.ShiftTemplateDto("t2", "Kitchen evening", "16:00:00", "23:30:00",
                        listOf(com.saporini.mobile_desktop.pos.shifts.TemplateRoleDto("r-k", "Kitchen", "KITCHEN"))),
                    com.saporini.mobile_desktop.pos.shifts.ShiftTemplateDto("t3", "Closing", "17:00:00", "00:30:00")),
                roles = listOf("r-m" to "Manager" to "MANAGER", "r-w" to "Waiter" to "WAITER", "r-k" to "Kitchen" to "KITCHEN",
                    "r-c" to "Cleaner" to "CLEANER", "r-e" to "Extra worker" to "EXTRA_WORKER")
                    .map { (idName, code) -> com.saporini.mobile_desktop.pos.shifts.TemplateRoleDto(idName.first, idName.second, code) }))
            render("shifts-planner-whose-$name", size.first, size.second, required = listOf("Whose shifts?", "Waiter shift", "Shift for anyone", "people")) {
                val state by model.state.collectAsState()
                PlanShiftsDialog(state, repository.staff.filter { it.id != "owner" }, Clock.System.now(), today, onDismiss = {})
            }
            // Copying another role's shifts (user, 2026-10-31).
            render("shifts-import-$name", size.first, 800, required = listOf("Import shifts to Kitchen", "Waiters' morning", "Import 1 shift")) {
                val roles by com.saporini.mobile_desktop.pos.shifts.ShiftTemplates.roles.collectAsState()
                val templates by com.saporini.mobile_desktop.pos.shifts.ShiftTemplates.all.collectAsState()
                com.saporini.mobile_desktop.pos.shifts.ImportShiftsDialog(roles.first { it.id == "r-k" }, roles, templates, onDismiss = {}) {}
            }
            // Back to the usual shifts and no roles, for the next size's renders.
            fillUsualShifts()
            // Taking turns, and who starts on which (user, 2026-10-31).
            com.saporini.mobile_desktop.pos.shifts.ShiftTemplates.fill(com.saporini.mobile_desktop.pos.shifts.ShiftTemplatesDto(templates = listOf(
                com.saporini.mobile_desktop.pos.shifts.ShiftTemplateDto("morning", "Morning", "09:00:00", "17:00:00", weekdays = listOf(0, 1, 2, 3, 4), startsOn = today.toString(),
                    people = listOf(com.saporini.mobile_desktop.pos.shifts.PlanPersonDto("sara", 0), com.saporini.mobile_desktop.pos.shifts.PlanPersonDto("dritan", 1)),
                    turnEvery = 1, turnShifts = listOf("evening")),
                com.saporini.mobile_desktop.pos.shifts.ShiftTemplateDto("evening", "Evening", "16:00:00", "23:30:00"))))
            render("shifts-planner-turns-$name", size.first, if (name == "phone") 2400 else 1700, required = listOf("Take turns", "Who starts on which", "Week of", "Split them evenly")) {
                val state by model.state.collectAsState()
                PlanShiftsDialog(state, repository.staff.filter { it.id != "owner" }, Clock.System.now(), today, initialTemplate = "morning", onDismiss = {})
            }
            // A shift that runs for a time (user, 2026-10-31).
            com.saporini.mobile_desktop.pos.shifts.ShiftTemplates.fill(com.saporini.mobile_desktop.pos.shifts.ShiftTemplatesDto(templates = listOf(
                com.saporini.mobile_desktop.pos.shifts.ShiftTemplateDto("timed", "Afternoon", "15:00:00", "23:00:00", weekdays = listOf(0, 1, 2, 3, 4), startsOn = today.toString(), lengthUnit = "WEEKS", lengthCount = 3))))
            render("shifts-planner-time-$name", size.first, size.second, required = listOf("For a time", "Weeks", "This will last till", "Save changes")) {
                val state by model.state.collectAsState()
                PlanShiftsDialog(state, repository.staff.filter { it.id != "owner" }, Clock.System.now(), today, initialTemplate = "timed", onDismiss = {})
            }
            fillUsualShifts()
            // Adding a shift inside a role: no For (user, 2026-10-31).
            render("shifts-template-role-$name", size.first, 900, required = listOf("Add Manager shift", "Starts", "Ends", "Icon", "New shift"), absent = listOf("Everyone", "Only people")) {
                ShiftTemplateDialog(null, forRole = com.saporini.mobile_desktop.pos.shifts.TemplateRoleDto("r-m", "Manager", "MANAGER"), onDismiss = {}) {}
            }
            // A usual shift and who it's for (user, 2026-10-31).
            render("shifts-template-$name", size.first, 900, required = listOf("Add shift", "For", "Everyone")) { ShiftTemplateDialog(null, onDismiss = {}) {} }
        }
        model.onDispose()
    }

    @Test
    fun myShiftOnAMonthlySalary() = gallery {
        val model = ShiftScreenModel(GalleryShifts().apply { waiter = true; monthly = true }, shiftSession(waiter = true))
        model.start(false); settle()
        listOf("desktop" to (1440 to 960), "phone" to (390 to 1500)).forEach { (name, size) ->
            render("shifts-my-shift-monthly-$name", size.first, size.second, required = listOf("My salary", "€1,100.00", "Salary a month", "Hours worked"), absent = listOf("Wages", "/h", "My pay this week")) {
                val state by model.state.collectAsState()
                ShiftContent(state, false, model)
            }
        }
        model.onDispose()
    }

    @Test
    fun myShiftTeamForAWaiter() = gallery {
        val repository = GalleryShifts().apply { waiter = true }
        val model = ShiftScreenModel(repository, shiftSession(waiter = true))
        model.start(false); settle()
        model.showTeam(true); settle()
        listOf("desktop" to (1440 to 960), "phone" to (390 to 1500)).forEach { (name, size) ->
            // A waiter sees the other waiters and the kitchen, not the managers (the sample manager is Arjan Dibra).
            render("shifts-my-team-$name", size.first, size.second, required = listOf("Team"), absent = listOf("Arjan Dibra")) {
                val state by model.state.collectAsState()
                ShiftContent(state, false, model, startOnTeam = true)
            }
            // A day opens the same window as in the Admin Hub, with nothing to change.
            render("shifts-my-team-day-$name", size.first, size.second, required = listOf("Table"), absent = listOf("Arjan Dibra", "Plan shifts", "Add now")) {
                val state by model.state.collectAsState()
                ShiftContent(state, false, model, startOnTeam = true, openDay = Clock.System.now().toLocalDateTime(Zone).date)
            }
            settle()
            render("shifts-my-shift-$name", size.first, size.second) {
                val state by model.state.collectAsState()
                ShiftContent(state, false, model)
            }
        }
        model.onDispose()
    }
}
