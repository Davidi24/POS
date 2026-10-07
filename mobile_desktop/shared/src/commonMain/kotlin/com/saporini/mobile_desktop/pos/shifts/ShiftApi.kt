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
    val shifts: List<ShiftPayLine> = emptyList(), val tipsByDay: List<DayTips> = emptyList())
@Serializable data class PayReport(val timezone: String, val currency: String, val from: String, val to: String, val serverNow: String, val staff: List<StaffPay> = emptyList())
@Serializable data class PayRate(val hourlyRate: OrderDecimal)
@Serializable data class ShiftSchedule(val userId: String, val scheduledStart: String, val scheduledEnd: String, val notes: String?, val version: Long = 0)
@Serializable data class ShiftClockIn(val shiftId: String? = null)
@Serializable data class ShiftAction(val version: Long, val notes: String? = null)
@Serializable data class ShiftBreakRequest(val version: Long, val type: String)
@Serializable data class ShiftCorrection(val version: Long, val startedAt: String, val endedAt: String, val reason: String)

interface ShiftRepository {
    suspend fun board(restaurant: String, branch: String, from: String, to: String, mine: Boolean): ShiftBoard
    suspend fun schedule(restaurant: String, branch: String, id: String?, request: ShiftSchedule): ShiftItem
    suspend fun clockIn(restaurant: String, branch: String, id: String?): ShiftItem
    suspend fun action(restaurant: String, branch: String, id: String, action: String, request: ShiftAction): ShiftItem
    suspend fun startBreak(restaurant: String, branch: String, id: String, request: ShiftBreakRequest): ShiftItem
    suspend fun correct(restaurant: String, branch: String, id: String, request: ShiftCorrection): ShiftItem
    suspend fun pay(restaurant: String, branch: String, from: String, to: String, mine: Boolean): PayReport
    suspend fun setPayRate(restaurant: String, branch: String, userId: String, rate: OrderDecimal): PayRate
}
class ShiftApi(private val client: HttpClient, private val baseUrl: () -> String = { ApiConfig.BASE_URL }) : ShiftRepository {
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
}
