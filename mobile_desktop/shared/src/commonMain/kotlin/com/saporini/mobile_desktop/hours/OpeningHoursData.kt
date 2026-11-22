package com.saporini.mobile_desktop.hours

import com.saporini.mobile_desktop.core.format.instantOrNull
import com.saporini.mobile_desktop.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/** One opening: from [opens] to [closes] ("HH:mm"); a closing at or before the opening runs past midnight. */
@Serializable
data class HoursSpanDto(val opens: String, val closes: String)

/** A weekday (1 = Monday … 7 = Sunday) and its openings; none = closed that day. */
@Serializable
data class HoursDayDto(val day: Int, val spans: List<HoursSpanDto> = emptyList())

/** A date or a range of dates with hours of their own: closed, or its own openings, and a note ("Christmas Eve"). */
@Serializable
data class SpecialDayDto(
    val id: String? = null,
    val startsOn: String,
    val endsOn: String? = null,
    val closed: Boolean = false,
    val spans: List<HoursSpanDto> = emptyList(),
    val note: String? = null
)

/**
 * Whether the branch is open now. [set] false = its hours were never set, so it counts as open. [why]: HOURS, SPECIAL
 * (a special day), OPENED (opened outside the hours) or NOT_SET. [until] is when the opening it's in ends, [nextOpen] when
 * it opens next while closed.
 */
@Serializable
data class OpenNowDto(
    val open: Boolean = true,
    val set: Boolean = false,
    val why: String? = null,
    val until: String? = null,
    val nextOpen: String? = null,
    val openedUntil: String? = null,
    val note: String? = null
)

@Serializable
data class OpeningHoursDto(
    val set: Boolean = false,
    val timezone: String? = null,
    val week: List<HoursDayDto> = emptyList(),
    val specialDays: List<SpecialDayDto> = emptyList(),
    val now: OpenNowDto = OpenNowDto()
)

/** One date as it is: closed or its openings, and whether a special day made it so. */
@Serializable
data class CalendarDayDto(val date: String, val closed: Boolean = false, val spans: List<HoursSpanDto> = emptyList(), val special: Boolean = false,
                          val note: String? = null)

/**
 * A booking whose start falls when the branch is closed by its hours (user, 2026-11-21): listed after the hours change, to
 * move or cancel, and marked "Branch closed then" in Reservations.
 */
@Serializable
data class ClosedBookingDto(
    val id: String,
    val code: String? = null,
    val start: String,
    val end: String? = null,
    val guests: Int = 0,
    val name: String? = null,
    val phone: String? = null,
    val status: String = "",
    val tables: List<String> = emptyList()
)

@Serializable
data class WeekRequestDto(val days: List<HoursDayDto>)

@Serializable
data class OpenNowRequestDto(val until: String)

/** `/restaurants/{r}/branches/{b}/opening-hours` (user, 2026-11-20, step 4). */
class OpeningHoursApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) {
    private fun url(restaurantId: String, branchId: String, path: String = "") =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/opening-hours$path"

    suspend fun hours(restaurantId: String, branchId: String): OpeningHoursDto = client.get(url(restaurantId, branchId)).body()

    suspend fun now(restaurantId: String, branchId: String): OpenNowDto = client.get(url(restaurantId, branchId, "/now")).body()

    /** Whether it's open at [time] (an ISO moment) by its hours: a booking's time. */
    suspend fun openAt(restaurantId: String, branchId: String, time: String): Boolean =
        client.get(url(restaurantId, branchId, "/at")) { parameter("time", time) }.body()

    suspend fun calendar(restaurantId: String, branchId: String, from: LocalDate, to: LocalDate): List<CalendarDayDto> =
        client.get(url(restaurantId, branchId, "/calendar")) { parameter("from", from.toString()); parameter("to", to.toString()) }.body()

    suspend fun saveWeek(restaurantId: String, branchId: String, days: List<HoursDayDto>): OpeningHoursDto =
        client.put(url(restaurantId, branchId, "/week")) { contentType(ContentType.Application.Json); setBody(WeekRequestDto(days)) }.body()

    suspend fun saveSpecialDay(restaurantId: String, branchId: String, day: SpecialDayDto): SpecialDayDto {
        val body = day.copy(id = null)
        return if (day.id == null) client.post(url(restaurantId, branchId, "/special-days")) { contentType(ContentType.Application.Json); setBody(body) }.body()
        else client.put(url(restaurantId, branchId, "/special-days/${day.id.encodeURLPathPart()}")) { contentType(ContentType.Application.Json); setBody(body) }.body()
    }

    suspend fun removeSpecialDay(restaurantId: String, branchId: String, dayId: String) {
        client.delete(url(restaurantId, branchId, "/special-days/${dayId.encodeURLPathPart()}"))
    }

    suspend fun openNow(restaurantId: String, branchId: String, until: String): OpenNowDto =
        client.post(url(restaurantId, branchId, "/open-now")) { contentType(ContentType.Application.Json); setBody(OpenNowRequestDto(until)) }.body()

    suspend fun closeNow(restaurantId: String, branchId: String): OpenNowDto = client.delete(url(restaurantId, branchId, "/open-now")).body()

    /** Bookings still to come whose start falls when the branch is closed by its hours. */
    suspend fun closedBookings(restaurantId: String, branchId: String): List<ClosedBookingDto> =
        client.get(url(restaurantId, branchId, "/closed-bookings")).body()
}

// ---- Reading times and dates ----

/** "12:00:00" or "12:00" as a time (the server writes seconds). */
internal fun timeOf(text: String): LocalTime? = runCatching {
    val parts = text.trim().split(':')
    LocalTime(parts[0].toInt(), parts.getOrNull(1)?.toInt() ?: 0)
}.getOrNull()

internal fun LocalTime.hm(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

internal fun dateOf(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

/** "12:00–15:00" (a closing at or before the opening is the next day's). */
internal fun HoursSpanDto.text(): String = "${timeOf(opens)?.hm() ?: opens}–${timeOf(closes)?.hm() ?: closes}"

internal fun List<HoursSpanDto>.text(): String = if (isEmpty()) "Closed" else joinToString(", ") { it.text() }

/** A moment the server sent, as the branch's clock time ("23:00"). */
internal fun clockAt(text: String?, zone: TimeZone): LocalTime? = instantOrNull(text)?.toLocalDateTime(zone)?.time

internal fun dateAt(text: String?, zone: TimeZone): LocalDate? = instantOrNull(text)?.toLocalDateTime(zone)?.date

/**
 * The times a booking of [lengthMinutes] can start on [date], every 15 minutes while open, the last leaving its length before
 * closing (user, 2026-11-21, as the guest booking page). [days] are the calendar's [date] and the day before (a past-midnight
 * opening of the day before counts). None when closed; times before [soonest] are left out.
 */
internal fun bookableTimes(days: List<CalendarDayDto>, date: LocalDate, lengthMinutes: Int, soonest: LocalDateTime? = null): List<LocalTime> {
    val out = mutableSetOf<LocalTime>()
    days.forEach { day ->
        val on = dateOf(day.date) ?: return@forEach
        if (day.closed) return@forEach
        day.spans.forEach { span ->
            val opens = timeOf(span.opens) ?: return@forEach
            val closes = timeOf(span.closes) ?: return@forEach
            val start = LocalDateTime(on, opens)
            val end = LocalDateTime(if (closes <= opens) on.plus(DatePeriod(days = 1)) else on, closes)
            val zone = TimeZone.UTC
            val endAt = end.toInstant(zone)
            var lastAt = endAt - lengthMinutes.minutes
            val startAt = start.toInstant(zone)
            if (lastAt < startAt) lastAt = startAt
            var at = startAt
            while (at <= lastAt) {
                val local = at.toLocalDateTime(zone)
                if (local.date == date && (soonest == null || local > soonest)) out.add(local.time)
                at += 15.minutes
            }
        }
    }
    return out.sorted()
}

internal fun zoneOf(id: String?): TimeZone = id?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()
