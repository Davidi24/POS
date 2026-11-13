package com.saporini.mobile_desktop.pos.shifts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

// Planning shifts: the restaurant's usual shift times, who may see whose shifts, and turning "these people, this
// shift, these days" into the single shifts the server saves.

/**
 * A usual shift ("Morning 09:00–17:00"), for the roles in [roles] (none = everyone; user, 2026-10-31), with the [icon]
 * picked for it (none = the part of the day it starts in). A shift that ends at or before it starts ends the next day.
 * At the branch worked in it runs on [weekdays] (0 = Monday) from [startsOn] for [lengthCount] of [lengthUnit] (no unit =
 * forever; without weekdays it isn't on any day yet), for [people] (by id, with the turn each starts on), taking turns every
 * [turnEvery] weeks with [turnShifts] (user, 2026-10-31: "one week the first shift, one week the second").
 */
internal data class ShiftTemplate(
    val id: String, val name: String, val start: LocalTime, val end: LocalTime, val roles: List<TemplateRoleDto> = emptyList(), val icon: String? = null,
    val weekdays: Set<Int> = emptySet(), val startsOn: LocalDate? = null, val lengthUnit: LengthUnit? = null, val lengthCount: Int = 1,
    val people: Map<String, Int> = emptyMap(), val turnEvery: Int = 1, val turnShifts: List<String> = emptyList()
) {
    /** Whether it's on the calendar that day. */
    fun runsOn(day: LocalDate): Boolean {
        val from = startsOn ?: return false
        if (day < from || day.dayOfWeek.ordinal !in weekdays) return false
        return lengthUnit == null || day <= lengthUnit.lastDay(from, lengthCount)
    }
    val overnight get() = end <= start
    val minutes: Long get() = ((end.hour * 60 + end.minute) - (start.hour * 60 + start.minute)).let { if (it <= 0) it + 24 * 60 else it }.toLong()
    val times get() = "${start.hhmm()}–${end.hhmm()}"
    /** Who it's for, in a few words: "Waiter + Kitchen", or "Everyone". */
    val forWhom: String get() = if (roles.isEmpty()) "Everyone" else roles.joinToString(" + ") { it.name }
    /** Someone can be put on it when it's for everyone or for one of their roles. */
    fun fits(person: ShiftStaff): Boolean = roles.isEmpty() || person.roles.any { role -> roles.any { it.name.equals(role, ignoreCase = true) } }
}

/**
 * The restaurant's usual shifts, kept on the server (`/restaurants/{r}/shift-templates`, since 2026-10-31): the app's copy,
 * filled when Shifts loads ([fill]) and changed through [save] / [delete]. Until it's connected (look checks) the five
 * starting shifts are used and changes stay in the app.
 */
/** The shifts on the calendar that day: those that run then, and those another one takes turns with. */
internal fun List<ShiftTemplate>.runningOn(day: LocalDate): List<ShiftTemplate> =
    filter { shift -> shift.runsOn(day) || any { other -> shift.id in other.turnShifts && other.runsOn(day) } }

/** What saving a plan did: a problem to show, or how many shifts were made and how many were left out (already working). */
internal data class PlanAnswer(val problem: String? = null, val added: Int = 0, val skipped: Int = 0)

/** How long a shift runs when it isn't forever: a number of days, weeks or months from its start. */
internal enum class LengthUnit(val label: String, val most: Int) {
    DAYS("Days", 31), WEEKS("Weeks", 52), MONTHS("Months", 12);

    fun lastDay(start: LocalDate, count: Int): LocalDate = when (this) {
        DAYS -> start.plus(DatePeriod(days = count - 1))
        WEEKS -> start.plus(DatePeriod(days = count * 7 - 1))
        MONTHS -> start.plus(DatePeriod(months = count)).plus(DatePeriod(days = -1))
    }
}

internal object ShiftTemplates {
    // None to start with: a restaurant makes its own (user, 2026-10-31: "no default shifts, I choose them from the beginning").
    private val saved = MutableStateFlow<List<ShiftTemplate>>(emptyList())
    val all: StateFlow<List<ShiftTemplate>> = saved.asStateFlow()
    private val choices = MutableStateFlow<List<TemplateRoleDto>>(emptyList())
    /** The roles a shift can be for: the restaurant's paid roles. */
    val roles: StateFlow<List<TemplateRoleDto>> = choices.asStateFlow()
    private var repository: ShiftRepository? = null
    private var restaurant: String? = null
    private var branch: String? = null

    fun connect(repository: ShiftRepository, restaurant: String, branch: String? = null) {
        this.repository = repository
        this.restaurant = restaurant
        this.branch = branch
    }

    fun fill(dto: ShiftTemplatesDto) {
        saved.value = dto.templates.mapNotNull { it.toTemplate() }
        choices.value = dto.roles
    }

    /** Adds ([id] null) or changes a usual shift; answers a problem to show, or null when saved. */
    suspend fun save(id: String?, name: String, start: LocalTime, end: LocalTime, roleIds: List<String>, icon: String? = null): String? {
        // When it runs stays as it is.
        val plan = saved.value.firstOrNull { it.id == id }
        val repo = repository
        val r = restaurant
        if (repo == null || r == null) {
            val chosen = choices.value.filter { it.id in roleIds }
            val template = ShiftTemplate(id ?: "new-${saved.value.size}-${name.hashCode()}", name, start, end, chosen, icon).withPlanOf(plan)
            saved.update { list -> if (list.any { it.id == template.id }) list.map { if (it.id == template.id) template else it } else list + template }
            return null
        }
        return try {
            val savedOne = repo.saveTemplate(r, id, ShiftTemplateRequest(name, start.hhmm(), end.hhmm(), roleIds, icon)).toTemplate()?.withPlanOf(plan)
                ?: return "Couldn't read the saved shift."
            saved.update { list -> if (list.any { it.id == savedOne.id }) list.map { if (it.id == savedOne.id) savedOne else it } else list + savedOne }
            null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { (e as? com.saporini.mobile_desktop.core.network.ApiException)?.message ?: "Couldn't save the shift. Check the connection and try again." }
    }

    /**
     * Saves how a shift runs at the branch ([unit] null = forever), who it applies to and the shifts it takes turns with; no
     * weekdays takes it off the calendar. The server makes the shifts.
     */
    suspend fun savePlan(id: String, weekdays: Set<Int>, startsOn: LocalDate, unit: LengthUnit?, count: Int, people: Map<String, Int>, every: Int, turns: List<String>): PlanAnswer {
        val shift = saved.value.firstOrNull { it.id == id } ?: return PlanAnswer("This shift was removed. Refresh and try again.")
        val planned = weekdays.isNotEmpty()
        val changed = if (planned) shift.copy(weekdays = weekdays, startsOn = startsOn, lengthUnit = unit, lengthCount = count, people = people, turnEvery = every, turnShifts = turns)
            else shift.copy(weekdays = emptySet(), startsOn = null, lengthUnit = null, lengthCount = 1, people = emptyMap(), turnEvery = 1, turnShifts = emptyList())
        val repo = repository
        val r = restaurant
        val b = branch
        if (repo == null || r == null || b == null) {
            saved.update { list -> list.map { if (it.id == id) changed else it } }
            return PlanAnswer()
        }
        return try {
            val answer = repo.savePlan(r, b, id, ShiftPlanRequest(weekdays.sorted(), startsOn.toString().takeIf { planned }, unit?.name?.takeIf { planned },
                count.takeIf { planned && unit != null }, if (planned) people.map { (who, turn) -> PlanPersonDto(who, turn) } else emptyList(), every, if (planned) turns else emptyList()))
            val savedOne = answer.template.toTemplate() ?: return PlanAnswer("Couldn't read the saved shift.")
            saved.update { list -> list.map { if (it.id == id) savedOne else it } }
            PlanAnswer(null, answer.added, answer.skipped)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { PlanAnswer((e as? com.saporini.mobile_desktop.core.network.ApiException)?.message ?: "Couldn't save the shift. Check the connection and try again.") }
    }

    /** A copy of [shift] for [role], with the same days and length (not its people or turns). Answers a problem to show, or null. */
    suspend fun copy(shift: ShiftTemplate, role: TemplateRoleDto): String? {
        val repo = repository
        val r = restaurant
        if (repo == null || r == null) {
            saved.update { list -> list + shift.copy(id = "copy-${list.size}-${role.id}", roles = listOf(role), people = emptyMap(), turnShifts = emptyList(), turnEvery = 1) }
            return null
        }
        val made = try {
            repo.saveTemplate(r, null, ShiftTemplateRequest(shift.name, shift.start.hhmm(), shift.end.hhmm(), listOf(role.id), shift.icon)).toTemplate() ?: return "Couldn't read the saved shift."
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { return (e as? com.saporini.mobile_desktop.core.network.ApiException)?.message ?: "Couldn't copy the shift. Check the connection and try again." }
        saved.update { list -> list + made }
        val from = shift.startsOn
        if (shift.weekdays.isEmpty() || from == null) return null
        return savePlan(made.id, shift.weekdays, from, shift.lengthUnit, shift.lengthCount, emptyMap(), 1, emptyList()).problem
    }

    /** The same shift, keeping how [other] runs here (a plain save of the shift doesn't send it). */
    private fun ShiftTemplate.withPlanOf(other: ShiftTemplate?) = if (other == null) this else copy(weekdays = other.weekdays, startsOn = other.startsOn,
        lengthUnit = other.lengthUnit, lengthCount = other.lengthCount, people = other.people, turnEvery = other.turnEvery, turnShifts = other.turnShifts)

    suspend fun delete(id: String): String? {
        val repo = repository
        val r = restaurant
        if (repo != null && r != null) {
            try { repo.deleteTemplate(r, id) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { return (e as? com.saporini.mobile_desktop.core.network.ApiException)?.message ?: "Couldn't remove the shift. Check the connection and try again." }
        }
        saved.update { list -> list.filterNot { it.id == id } }
        return null
    }

    private fun ShiftTemplateDto.toTemplate(): ShiftTemplate? {
        val from = runCatching { LocalTime.parse(start) }.getOrNull() ?: return null
        val until = runCatching { LocalTime.parse(end) }.getOrNull() ?: return null
        return ShiftTemplate(id, name, from, until, roles, icon, weekdays.toSet(), startsOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            LengthUnit.entries.firstOrNull { it.name == lengthUnit }, lengthCount ?: 1, people.associate { it.userId to it.startTurn }, turnEvery ?: 1, turnShifts)
    }
}

/** Morning, afternoon or evening, by when a shift starts. */
internal enum class DayPart(val label: String) { MORNING("Morning"), AFTERNOON("Afternoon"), EVENING("Evening") }

internal fun dayPartOf(start: LocalTime): DayPart = when {
    start.hour < 12 -> DayPart.MORNING
    start.hour < 16 -> DayPart.AFTERNOON
    else -> DayPart.EVENING
}

/**
 * Who sees whose shifts: everyone sees their own, and the shifts of people at their level or below (a waiter sees the
 * other waiters and the kitchen, not the managers; a manager sees managers and everyone under them). The server
 * enforces the same rule in the production pass; for now the screens apply it.
 */
internal object ShiftVisibility {
    // The built-in roles' ranks (as on the server). A custom role counts as a waiter's level.
    private val ranks = mapOf(
        "SUPER_ADMIN" to 60_000, "CO_SUPER_ADMIN" to 55_000, "OWNER" to 50_000, "CO_OWNER" to 40_000, "ADMIN" to 30_000,
        "MANAGER" to 20_000, "WAITER" to 10_000, "VIEWER" to 7_500, "KITCHEN" to 5_000, "CLEANER" to 4_000, "EXTRA_WORKER" to 3_000
    )
    private const val CUSTOM = 10_000

    /** "Co-Owner", "co_owner" and "CO_OWNER" are the same role. */
    fun code(role: String) = role.trim().uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')
    fun rankOf(role: String): Int = ranks[code(role)] ?: CUSTOM
    fun rankOf(roles: List<String>): Int = roles.maxOfOrNull { rankOf(it) } ?: 0
    fun canSee(viewerRank: Int, personRoles: List<String>) = rankOf(personRoles.ifEmpty { listOf("WAITER") }) <= viewerRank
}

/** A group of people in the schedule ("Kitchen", "Waiters"...), in the order a rota reads: the people who work shifts first. */
internal data class StaffGroup(val key: String, val label: String, val order: Int)

internal fun staffGroupOf(roles: List<String>): StaffGroup {
    val role = roles.maxByOrNull { ShiftVisibility.rankOf(it) }
    return when (role?.let(ShiftVisibility::code)) {
        null -> StaffGroup("TEAM", "Team", 50)
        "MANAGER" -> StaffGroup("MANAGER", "Managers", 0)
        "KITCHEN" -> StaffGroup("KITCHEN", "Kitchen", 1)
        "WAITER" -> StaffGroup("WAITER", "Waiters", 2)
        "ADMIN" -> StaffGroup("ADMIN", "Admins", 10)
        "CO_OWNER" -> StaffGroup("CO_OWNER", "Co-owners", 11)
        "OWNER" -> StaffGroup("OWNER", "Owners", 12)
        "VIEWER" -> StaffGroup("VIEWER", "Viewers", 13)
        "SUPER_ADMIN", "CO_SUPER_ADMIN" -> StaffGroup("PLATFORM", "Super admins", 14)
        else -> StaffGroup(ShiftVisibility.code(role), role, 3)
    }
}

/** Groups people usually not on the rota start folded away when nobody in them works that week. */
internal fun StaffGroup.usuallyOnRota() = order < 10

/** One shift the planner will add. */
internal data class PlannedShift(val userId: String, val name: String, val date: LocalDate, val start: Instant, val end: Instant, val notes: String?)

/** What a plan would do: the shifts to add, and the ones skipped (in the past, or the person already works then). */
internal data class PlanPreview(val shifts: List<PlannedShift>, val alreadyWorking: List<PlannedShift>, val past: Int)

/**
 * Every [people] × [dates] shift from [start] to [end] (the next day when it ends earlier), minus the ones that end in
 * the past or overlap a shift the person already has in [existing].
 */
internal fun planShifts(
    people: List<ShiftStaff>, dates: List<LocalDate>, start: LocalTime, end: LocalTime, notes: String?, zone: TimeZone,
    existing: List<ShiftItem>, now: Instant = Clock.System.now()
): PlanPreview {
    val busy = existing.filter { it.status != "CANCELLED" && it.status != "MISSED" }.groupBy { it.userId }
    val planned = mutableListOf<PlannedShift>()
    val clashing = mutableListOf<PlannedShift>()
    var past = 0
    for (date in dates.distinct().sorted()) {
        val from = runCatching { LocalDateTime(date, start).toInstant(zone) }.getOrNull() ?: continue
        val to = runCatching { LocalDateTime(if (end <= start) date.plus(DatePeriod(days = 1)) else date, end).toInstant(zone) }.getOrNull() ?: continue
        for (person in people) {
            if (to <= now) { past++; continue }
            val shift = PlannedShift(person.id, person.name, date, from, to, notes?.trim()?.ifBlank { null })
            val overlaps = busy[person.id].orEmpty().any { item ->
                val s = item.start?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return@any false
                val e = (item.scheduledEnd ?: item.endedAt)?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: now
                s < to && e > from
            }
            if (overlaps) clashing += shift else planned += shift
        }
    }
    return PlanPreview(planned, clashing, past)
}

/** The days from [start] to [last] that fall on the ticked [weekdays] (0 = Monday). */
internal fun daysFrom(start: LocalDate, weekdays: Set<Int>, last: LocalDate): List<LocalDate> =
    generateSequence(start) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= last }.filter { it.dayOfWeek.ordinal in weekdays }.toList()

internal fun LocalDate.weekStart(): LocalDate = plus(DatePeriod(days = -dayOfWeek.ordinal))

/** When an existing shift started, as a local time (for "same times as..."). */
internal fun ShiftItem.localStart(zone: TimeZone): LocalTime? = start?.let { runCatching { Instant.parse(it).toLocalDateTime(zone).time }.getOrNull() }
