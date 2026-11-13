package com.saporini.mobile_desktop.pos.shifts

import com.saporini.mobile_desktop.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal

@Serializable data class ShiftStaff(val id: String, val name: String, val roles: List<String> = emptyList())
@Serializable data class ShiftBreak(val id: String, val type: String, val paid: Boolean, val startedAt: String, val endedAt: String? = null)
@Serializable data class ShiftItem(
    val id: String, val version: Long, val userId: String, val userName: String, val status: String,
    val scheduledStart: String? = null, val scheduledEnd: String? = null,
    val startedAt: String? = null, val endedAt: String? = null,
    val workedMinutes: Long = 0, val breakMinutes: Long = 0, val notes: String? = null,
    val breaks: List<ShiftBreak> = emptyList()
) {
    val active get() = status == "OPEN" || status == "ON_BREAK"
    val start get() = scheduledStart ?: startedAt
}
@Serializable data class ShiftBoard(val timezone: String, val serverNow: String, val items: List<ShiftItem>, val current: ShiftItem? = null, val staff: List<ShiftStaff> = emptyList(), val clockInShift: ShiftItem? = null,
    // Admin Hub → Settings → Shifts: staff can clock in this long before a scheduled shift.
    val clockInEarlyMinutes: Int = 120)

// Pay = hours worked × hourly wage + tips on the person's orders. Money comes as decimal text (never floating point).
@Serializable data class ShiftPayLine(val shiftId: String, val date: String, val status: String, val startedAt: String? = null, val endedAt: String? = null,
    val workedMinutes: Long = 0, val breakMinutes: Long = 0, val hourlyRate: OrderDecimal? = null, val wages: OrderDecimal? = null)
@Serializable data class DayTips(val date: String, val tips: OrderDecimal)
@Serializable data class StaffPay(val userId: String, val name: String, val roles: List<String> = emptyList(), val hourlyRate: OrderDecimal? = null,
    val shiftCount: Int = 0, val workedMinutes: Long = 0, val breakMinutes: Long = 0, val wages: OrderDecimal = OrderDecimal.ZERO,
    val tips: OrderDecimal = OrderDecimal.ZERO, val total: OrderDecimal = OrderDecimal.ZERO, val missingRate: Boolean = false,
    val shifts: List<ShiftPayLine> = emptyList(), val tipsByDay: List<DayTips> = emptyList(),
    // Paid by the month: the same salary whatever the hours (no hourly wage or wages then); null = paid by the hour.
    val monthlySalary: OrderDecimal? = null,
    // The Salary team the server pays them in (their highest paid role's): MANAGERS, KITCHEN, WAITERS, CLEANING, EXTRA, or
    // "ROLE:<id>" for a restaurant's own role, with its name and picture.
    val payTeam: String? = null, val payTeamName: String? = null, val payTeamPicture: String? = null)
/** A restaurant's own paid role as a Salary team ("ROLE:<id>"), shown even before anyone has it. */
@Serializable data class PayTeamInfo(val key: String, val name: String? = null, val picture: String? = null)
@Serializable data class PayReport(val timezone: String, val currency: String, val from: String, val to: String, val serverNow: String, val staff: List<StaffPay> = emptyList(),
    val teams: List<PayTeamInfo> = emptyList())
/** How someone is paid: one of the two. */
@Serializable data class PayRate(val hourlyRate: OrderDecimal? = null, val monthlySalary: OrderDecimal? = null)
@Serializable data class ShiftSchedule(val userId: String, val scheduledStart: String, val scheduledEnd: String, val notes: String?, val version: Long = 0)
@Serializable data class ShiftClockIn(val shiftId: String? = null)
@Serializable data class ShiftAction(val version: Long, val notes: String? = null)
@Serializable data class ShiftBreakRequest(val version: Long, val type: String)
@Serializable data class ShiftCorrection(val version: Long, val startedAt: String, val endedAt: String, val reason: String)
/** A role a usual shift can be for (the restaurant's paid roles). */
@Serializable data class TemplateRoleDto(val id: String, val name: String, val code: String? = null, val picture: String? = null)
/** A usual shift from the server: times as "09:00:00", and the roles it's for (none = everyone). */
@Serializable data class ShiftTemplateDto(val id: String, val name: String, val start: String, val end: String, val roles: List<TemplateRoleDto> = emptyList(), val icon: String? = null,
    val weekdays: List<Int> = emptyList(), val startsOn: String? = null, val lengthUnit: String? = null, val lengthCount: Int? = null,
    val people: List<PlanPersonDto> = emptyList(), val turnEvery: Int? = null, val turnShifts: List<String> = emptyList())
/** Someone a shift applies to at a branch, and the turn they start on (0 = that shift). */
@Serializable data class PlanPersonDto(val userId: String, val startTurn: Int)
/** How a shift runs at a branch (no weekdays = off the calendar; no lengthUnit = forever). Every field is always sent. */
@Serializable data class ShiftPlanRequest(val weekdays: List<Int>, val startsOn: String?, val lengthUnit: String?, val lengthCount: Int?,
    val people: List<PlanPersonDto>, val turnEvery: Int, val turnShifts: List<String>)
@Serializable data class ShiftPlanSavedDto(val template: ShiftTemplateDto, val added: Int = 0, val skipped: Int = 0)
@Serializable data class ShiftTemplatesDto(val templates: List<ShiftTemplateDto> = emptyList(), val roles: List<TemplateRoleDto> = emptyList())
@Serializable data class ShiftTemplateRequest(val name: String, val start: String, val end: String, val roleIds: List<String>, val icon: String? = null)

interface ShiftRepository {
    suspend fun board(restaurant: String, branch: String, from: String, to: String, mine: Boolean): ShiftBoard
    suspend fun schedule(restaurant: String, branch: String, id: String?, request: ShiftSchedule): ShiftItem
    suspend fun clockIn(restaurant: String, branch: String, id: String?): ShiftItem
    suspend fun action(restaurant: String, branch: String, id: String, action: String, request: ShiftAction): ShiftItem
    suspend fun startBreak(restaurant: String, branch: String, id: String, request: ShiftBreakRequest): ShiftItem
    suspend fun correct(restaurant: String, branch: String, id: String, request: ShiftCorrection): ShiftItem
    suspend fun pay(restaurant: String, branch: String, from: String, to: String, mine: Boolean): PayReport
    suspend fun setPayRate(restaurant: String, branch: String, userId: String, rate: OrderDecimal): PayRate
    suspend fun setMonthlySalary(restaurant: String, branch: String, userId: String, salary: OrderDecimal): PayRate = throw UnsupportedOperationException("Monthly salaries are unavailable")
    /** The restaurant's usual shifts (user, 2026-10-31: kept on the server, each for the roles it's for). */
    /** With a branch: how each runs there. */
    suspend fun templates(restaurant: String, branch: String? = null): ShiftTemplatesDto = throw UnsupportedOperationException("Usual shifts are unavailable")
    suspend fun savePlan(restaurant: String, branch: String, id: String, request: ShiftPlanRequest): ShiftPlanSavedDto =
        throw UnsupportedOperationException("Usual shifts are unavailable")
    suspend fun saveTemplate(restaurant: String, id: String?, request: ShiftTemplateRequest): ShiftTemplateDto =
        throw UnsupportedOperationException("Usual shifts are unavailable")
    suspend fun deleteTemplate(restaurant: String, id: String): Unit = throw UnsupportedOperationException("Usual shifts are unavailable")
}
class ShiftApi(private val client: HttpClient, private val baseUrl: () -> String = { ApiConfig.BASE_URL }) : ShiftRepository {
    private fun templatesPath(r: String) = "${baseUrl().trimEnd('/')}/restaurants/${r.encodeURLPathPart()}/shift-templates"
    override suspend fun templates(restaurant: String, branch: String?): ShiftTemplatesDto = client.get(templatesPath(restaurant)) {
        branch?.let { parameter("branchId", it) }
    }.body()
    override suspend fun savePlan(restaurant: String, branch: String, id: String, request: ShiftPlanRequest): ShiftPlanSavedDto =
        client.put("${baseUrl().trimEnd('/')}/restaurants/${restaurant.encodeURLPathPart()}/branches/${branch.encodeURLPathPart()}/shift-templates/${id.encodeURLPathPart()}/plan") {
            contentType(ContentType.Application.Json); setBody(request)
        }.body()
    override suspend fun saveTemplate(restaurant: String, id: String?, request: ShiftTemplateRequest): ShiftTemplateDto =
        client.request(templatesPath(restaurant) + (id?.let { "/${it.encodeURLPathPart()}" } ?: "")) {
            method = if (id == null) HttpMethod.Post else HttpMethod.Put
            contentType(ContentType.Application.Json); setBody(request)
        }.body()
    override suspend fun deleteTemplate(restaurant: String, id: String) {
        client.delete("${templatesPath(restaurant)}/${id.encodeURLPathPart()}")
    }
    private fun path(r: String, b: String) = "${baseUrl().trimEnd('/')}/restaurants/${r.encodeURLPathPart()}/branches/${b.encodeURLPathPart()}/shifts"
    override suspend fun board(restaurant: String, branch: String, from: String, to: String, mine: Boolean): ShiftBoard = client.get(path(restaurant, branch)) {
        parameter("from", from); parameter("to", to); parameter("mine", mine)
    }.body()
    override suspend fun schedule(restaurant: String, branch: String, id: String?, request: ShiftSchedule): ShiftItem = client.request(path(restaurant, branch) + (id?.let { "/${it.encodeURLPathPart()}" } ?: "")) {
        method = if (id == null) HttpMethod.Post else HttpMethod.Put
        contentType(ContentType.Application.Json); setBody(request)
    }.body()
    override suspend fun clockIn(restaurant: String, branch: String, id: String?): ShiftItem = client.post("${path(restaurant, branch)}/clock-in") {
        contentType(ContentType.Application.Json); setBody(ShiftClockIn(id))
    }.body()
    override suspend fun action(restaurant: String, branch: String, id: String, action: String, request: ShiftAction): ShiftItem {
        require(action in setOf("resume", "close", "cancel", "missed"))
        return client.post("${path(restaurant, branch)}/${id.encodeURLPathPart()}/$action") { contentType(ContentType.Application.Json); setBody(request) }.body()
    }
    override suspend fun startBreak(restaurant: String, branch: String, id: String, request: ShiftBreakRequest): ShiftItem = client.post("${path(restaurant, branch)}/${id.encodeURLPathPart()}/break") {
        contentType(ContentType.Application.Json); setBody(request)
    }.body()
    override suspend fun correct(restaurant: String, branch: String, id: String, request: ShiftCorrection): ShiftItem = client.put("${path(restaurant, branch)}/${id.encodeURLPathPart()}/attendance") {
        contentType(ContentType.Application.Json); setBody(request)
    }.body()
    override suspend fun pay(restaurant: String, branch: String, from: String, to: String, mine: Boolean): PayReport = client.get("${path(restaurant, branch)}/pay") {
        parameter("from", from); parameter("to", to); parameter("mine", mine)
    }.body()
    override suspend fun setPayRate(restaurant: String, branch: String, userId: String, rate: OrderDecimal): PayRate = client.put("${path(restaurant, branch)}/pay-rates/${userId.encodeURLPathPart()}") {
        contentType(ContentType.Application.Json); setBody(PayRate(rate))
    }.body()
    override suspend fun setMonthlySalary(restaurant: String, branch: String, userId: String, salary: OrderDecimal): PayRate = client.put("${path(restaurant, branch)}/pay-rates/${userId.encodeURLPathPart()}") {
        contentType(ContentType.Application.Json); setBody(PayRate(monthlySalary = salary))
    }.body()
}
