package com.saporini.mobile_desktop.hours

import com.saporini.mobile_desktop.core.components.DoneMark
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.platform.platformMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** One opening while it's being changed. */
data class SpanDraft(val opens: LocalTime, val closes: LocalTime) {
    /** Closing at or before the opening: it closes the next day. */
    val pastMidnight: Boolean get() = closes <= opens
}

/** A weekday (1 = Monday … 7 = Sunday) and its openings; none = closed. */
data class DayDraft(val day: Int, val spans: List<SpanDraft>)

/** A special day being added ([id] null) or changed: a date or a range, closed or its own openings, and a note. */
data class SpecialDraft(
    val id: String? = null,
    val from: LocalDate,
    val to: LocalDate,
    val closed: Boolean = true,
    val spans: List<SpanDraft> = listOf(SpanDraft(LocalTime(12, 0), LocalTime(23, 0))),
    val note: String = "",
    val showProblems: Boolean = false
)

data class HoursBranch(val id: String, val name: String)

data class OpeningHoursState(
    val restaurantId: String? = null,
    val branches: List<HoursBranch> = emptyList(),
    val branchId: String? = null,
    val hours: OpeningHoursDto? = null,
    // The week as saved and as it's being changed (the same until someone changes it).
    val saved: List<DayDraft> = emptyList(),
    val week: List<DayDraft> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val special: SpecialDraft? = null,
    val savedSpecial: SpecialDraft? = null,
    val removing: SpecialDayDto? = null,
    val opening: Boolean = false,
    val canChange: Boolean = false,
    val canOpen: Boolean = false,
    val done: DoneMark? = null,
    val notice: String? = null,
    val error: String? = null,
    // After a change: the bookings that now fall when the branch is closed, to move or cancel (user, 2026-11-21). Null = the
    // window is shut.
    val closedBookings: List<ClosedBookingDto>? = null,
    // How many bookings fall in closed time now (the page's red line with "See them").
    val closedCount: Int = 0,
    val move: BookingMove? = null,
    val cancelling: ClosedBookingDto? = null,
    val bookingBusy: Boolean = false,
    val bookingError: String? = null,
    val canMoveBookings: Boolean = false,
    val canCancelBookings: Boolean = false
) {
    val changed: Boolean get() = week != saved
    val zone: TimeZone get() = zoneOf(hours?.timezone)
}

/** Moving one booking to an open time: the day picked, its open times, the time picked. */
data class BookingMove(
    val booking: ClosedBookingDto,
    val day: LocalDate,
    val times: List<LocalTime>? = null,
    val time: LocalTime? = null
)

const val MAX_OPENINGS_A_DAY = 6
const val MAX_HOURS_NOTE = 255

/** What's wrong with a day's openings (the server's own rules), or null. */
fun spansProblem(spans: List<SpanDraft>): String? {
    if (spans.size > MAX_OPENINGS_A_DAY) return "At most $MAX_OPENINGS_A_DAY openings a day"
    if (spans.any { it.opens == it.closes }) return "An opening can't close when it opens"
    val sorted = spans.sortedBy { it.opens }
    sorted.forEachIndexed { i, span ->
        if (span.pastMidnight && i < sorted.size - 1) return "Only the last opening of a day can run past midnight"
        if (i > 0 && span.opens < sorted[i - 1].closes) return "Openings in a day can't overlap"
    }
    return null
}

fun specialProblem(draft: SpecialDraft): String? = when {
    draft.to < draft.from -> "The last day comes after the first"
    !draft.closed && draft.spans.isEmpty() -> "Add when it opens and closes, or mark it closed"
    !draft.closed -> spansProblem(draft.spans)
    draft.note.length > MAX_HOURS_NOTE -> "Keep the note under $MAX_HOURS_NOTE characters"
    else -> null
}

private fun HoursSpanDto.draft(): SpanDraft? {
    val opens = timeOf(opens) ?: return null
    val closes = timeOf(closes) ?: return null
    return SpanDraft(opens, closes)
}

private fun SpanDraft.dto() = HoursSpanDto(opens.hm(), closes.hm())

internal fun weekOf(hours: OpeningHoursDto?): List<DayDraft> = (1..7).map { day ->
    DayDraft(day, hours?.week?.firstOrNull { it.day == day }?.spans.orEmpty().mapNotNull { it.draft() })
}

internal fun specialDraftOf(day: SpecialDayDto): SpecialDraft? {
    val from = dateOf(day.startsOn) ?: return null
    return SpecialDraft(day.id, from, dateOf(day.endsOn) ?: from, day.closed,
        day.spans.mapNotNull { it.draft() }.ifEmpty { listOf(SpanDraft(LocalTime(12, 0), LocalTime(23, 0))) }, day.note.orEmpty())
}

/**
 * Admin Hub → Restaurant info → Opening hours (user, 2026-11-20): each branch's week (any number of openings a day, or
 * closed) and its special days. Changing them needs "Change opening hours"; opening for now needs "Open outside the hours".
 */
class OpeningHoursModel(
    private val api: OpeningHoursApi,
    private val session: SessionManager,
    private val reservations: com.saporini.mobile_desktop.pos.reservations.data.api.ReservationApi? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(OpeningHoursState())
    val state: StateFlow<OpeningHoursState> = mutable.asStateFlow()
    private var loadJob: Job? = null
    private var revision = 0L
    private var marks = 0

    fun close() = scope.cancel()

    /** The restaurant and its branches, from the page. Keeps the chosen branch while it's still there. */
    fun setBranches(restaurantId: String?, branches: List<HoursBranch>) {
        val permissions = session.currentUser.value?.permissions.orEmpty()
        val current = state.value
        val keep = current.branchId?.takeIf { id -> branches.any { it.id == id } && current.restaurantId == restaurantId }
        val chosen = keep ?: session.currentUser.value?.defaultBranchId?.takeIf { id -> branches.any { it.id == id } } ?: branches.firstOrNull()?.id
        mutable.update {
            it.copy(restaurantId = restaurantId, branches = branches, branchId = chosen,
                canChange = "HOURS_UPDATE" in permissions, canOpen = "OPEN_OUTSIDE_HOURS" in permissions,
                canMoveBookings = reservations != null && "RESERVATION_MANAGE" in permissions,
                canCancelBookings = reservations != null && "RESERVATION_CANCEL" in permissions)
        }
        if (chosen != current.branchId || restaurantId != current.restaurantId || !current.loaded) load()
    }

    fun pickBranch(branchId: String) {
        if (branchId == state.value.branchId) return
        mutable.update { it.copy(branchId = branchId, hours = null, saved = emptyList(), week = emptyList(), loaded = false) }
        load()
    }

    fun refresh() = load()

    private fun load() {
        val restaurantId = state.value.restaurantId ?: return
        val branchId = state.value.branchId ?: return
        val token = ++revision
        loadJob?.cancel()
        loadJob = scope.launch {
            mutable.update { it.copy(loading = true) }
            try {
                val hours = api.hours(restaurantId, branchId)
                if (token != revision) return@launch
                val week = weekOf(hours)
                val closed = if (hours.set) runCatching { api.closedBookings(restaurantId, branchId).size }.getOrDefault(state.value.closedCount) else 0
                mutable.update { s ->
                    // A refresh keeps what's being changed.
                    s.copy(hours = hours, saved = week, week = if (s.changed && s.loaded) s.week else week, loaded = true, error = null, closedCount = closed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = platformMessage(e, write = false)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    // ---- The week ----

    private fun changeDay(day: Int, change: (List<SpanDraft>) -> List<SpanDraft>) {
        if (!state.value.canChange) return
        mutable.update { s ->
            // Hours never set: the week starts from what's shown (every day closed) once someone changes it.
            val week = s.week.ifEmpty { weekOf(null) }
            s.copy(week = week.map { if (it.day == day) it.copy(spans = change(it.spans)) else it })
        }
    }

    /** Open or closed that day; opening a closed day gives it the openings of the nearest open day before it (or 12:00–23:00). */
    fun setOpen(day: Int, open: Boolean) {
        val week = state.value.week
        val like = (day - 1 downTo 1).map { d -> week.firstOrNull { it.day == d } }.firstOrNull { it != null && it.spans.isNotEmpty() }?.spans
            ?: week.firstOrNull { it.spans.isNotEmpty() }?.spans
        changeDay(day) { if (open) like ?: listOf(SpanDraft(LocalTime(12, 0), LocalTime(23, 0))) else emptyList() }
    }

    /** Another opening after the last one (e.g. dinner after lunch). */
    fun addSpan(day: Int) = changeDay(day) { spans ->
        if (spans.size >= MAX_OPENINGS_A_DAY) return@changeDay spans
        val last = spans.maxByOrNull { it.opens }
        val start = last?.closes?.let { c -> if (last.pastMidnight) null else LocalTime(minOf(c.hour + 1, 23), c.minute) } ?: LocalTime(19, 0)
        spans + SpanDraft(start, LocalTime(minOf(start.hour + 4, 23), start.minute))
    }

    fun removeSpan(day: Int, index: Int) = changeDay(day) { spans -> spans.filterIndexed { i, _ -> i != index } }

    fun changeSpan(day: Int, index: Int, span: SpanDraft) = changeDay(day) { spans -> spans.mapIndexed { i, s -> if (i == index) span else s } }

    /** The day's openings onto [days] too. */
    fun copyDay(day: Int, days: List<Int>) {
        if (!state.value.canChange) return
        val spans = state.value.week.firstOrNull { it.day == day }?.spans ?: return
        mutable.update { s -> s.copy(week = s.week.map { if (it.day in days) it.copy(spans = spans) else it }) }
    }

    /** Hours never set: start from every day 12:00–23:00, ready to change and save. */
    fun startWeek() {
        if (!state.value.canChange) return
        mutable.update { s -> s.copy(week = (1..7).map { DayDraft(it, listOf(SpanDraft(LocalTime(12, 0), LocalTime(23, 0)))) }) }
    }

    fun discardWeek() = mutable.update { it.copy(week = it.saved) }

    val weekProblem: String? get() = state.value.week.firstNotNullOfOrNull { day -> spansProblem(day.spans)?.let { "${dayName(day.day)}: $it" } }

    fun saveWeek() {
        val s = state.value
        val restaurantId = s.restaurantId ?: return
        val branchId = s.branchId ?: return
        if (!s.canChange || s.saving || weekProblem != null) return
        scope.launch {
            mutable.update { it.copy(saving = true, error = null) }
            try {
                val hours = api.saveWeek(restaurantId, branchId, s.week.map { day -> HoursDayDto(day.day, day.spans.sortedBy { it.opens }.map { it.dto() }) })
                val week = weekOf(hours)
                mutable.update { it.copy(hours = hours, saved = week, week = week, done = DoneMark(++marks, "Opening hours saved")) }
                checkClosedBookings()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }

    // ---- Special days ----

    fun newSpecial() {
        if (!state.value.canChange) return
        val today = Clock.System.todayIn(state.value.zone)
        val draft = SpecialDraft(from = today, to = today)
        mutable.update { it.copy(special = draft, savedSpecial = null, error = null) }
    }

    fun editSpecial(day: SpecialDayDto) {
        if (!state.value.canChange) return
        val draft = specialDraftOf(day) ?: return
        mutable.update { it.copy(special = draft, savedSpecial = draft, error = null) }
    }

    fun changeSpecial(change: (SpecialDraft) -> SpecialDraft) = mutable.update { s -> s.copy(special = s.special?.let(change)) }

    fun cancelSpecial() = mutable.update { it.copy(special = null, savedSpecial = null, error = null) }

    fun saveSpecial() {
        val s = state.value
        val draft = s.special ?: return
        val restaurantId = s.restaurantId ?: return
        val branchId = s.branchId ?: return
        if (specialProblem(draft) != null) {
            mutable.update { it.copy(special = draft.copy(showProblems = true)) }
            return
        }
        scope.launch {
            mutable.update { it.copy(saving = true, error = null) }
            try {
                api.saveSpecialDay(restaurantId, branchId, SpecialDayDto(
                    id = draft.id, startsOn = draft.from.toString(), endsOn = draft.to.toString(), closed = draft.closed,
                    spans = if (draft.closed) emptyList() else draft.spans.sortedBy { it.opens }.map { it.dto() }, note = draft.note.trim().ifBlank { null }))
                mutable.update { it.copy(special = null, savedSpecial = null, done = DoneMark(++marks, "Special day saved")) }
                load()
                checkClosedBookings()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }

    fun askRemove(day: SpecialDayDto?) = mutable.update { it.copy(removing = day) }

    fun removeSpecial() {
        val s = state.value
        val day = s.removing ?: return
        val id = day.id ?: return
        val restaurantId = s.restaurantId ?: return
        val branchId = s.branchId ?: return
        scope.launch {
            mutable.update { it.copy(saving = true, error = null) }
            try {
                api.removeSpecialDay(restaurantId, branchId, id)
                mutable.update { it.copy(removing = null, notice = "Special day removed") }
                load()
                checkClosedBookings()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }

    // ---- Bookings left in closed time (user, 2026-11-21) ----

    /** After the hours change: the bookings that now fall when the branch is closed open in a list, if there are any. */
    private suspend fun checkClosedBookings() {
        val restaurantId = state.value.restaurantId ?: return
        val branchId = state.value.branchId ?: return
        val list = runCatching { api.closedBookings(restaurantId, branchId) }.getOrNull() ?: return
        mutable.update { it.copy(closedCount = list.size) }
        if (list.isNotEmpty()) mutable.update { it.copy(closedBookings = list, bookingError = null) }
    }

    /** "See them" from the page: the list as it is now. */
    fun showClosedBookings() {
        scope.launch {
            val restaurantId = state.value.restaurantId ?: return@launch
            val branchId = state.value.branchId ?: return@launch
            try {
                val list = api.closedBookings(restaurantId, branchId)
                mutable.update { it.copy(closedBookings = list, closedCount = list.size, bookingError = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = false)) }
            }
        }
    }

    fun closeClosedBookings() = mutable.update { it.copy(closedBookings = null, move = null, cancelling = null, bookingError = null) }

    /** Change time: starts on the booking's own day (closed now), so the next step is picking another day or time. */
    fun startMove(booking: ClosedBookingDto) {
        if (!state.value.canMoveBookings) return
        val day = dateAt(booking.start, state.value.zone) ?: return
        mutable.update { it.copy(move = BookingMove(booking, day), bookingError = null) }
        moveDay(day)
    }

    fun moveDay(day: LocalDate) {
        val s = state.value
        val move = s.move ?: return
        val restaurantId = s.restaurantId ?: return
        val branchId = s.branchId ?: return
        mutable.update { it.copy(move = move.copy(day = day, times = null, time = null)) }
        scope.launch {
            try {
                val days = api.calendar(restaurantId, branchId, day.minus(kotlinx.datetime.DatePeriod(days = 1)), day)
                val length = lengthOf(move.booking)
                val now = (Clock.System.now() + 15.minutes).toLocalDateTime(s.zone)
                // Hours never set: the calendar is empty and any time is open.
                val times = if (days.isEmpty()) (0 until 96).map { LocalTime(it / 4, (it % 4) * 15) }.filter { time -> day > now.date || time > now.time }
                else bookableTimes(days, day, length, now)
                mutable.update { st -> st.copy(move = st.move?.takeIf { it.day == day && it.booking.id == move.booking.id }?.copy(times = times) ?: st.move) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(bookingError = platformMessage(e, write = false)) }
            }
        }
    }

    fun pickMoveTime(time: LocalTime) = mutable.update { s -> s.copy(move = s.move?.copy(time = time)) }

    fun cancelMove() = mutable.update { it.copy(move = null, bookingError = null) }

    fun saveMove() {
        val s = state.value
        val move = s.move ?: return
        val time = move.time ?: return
        val restaurantId = s.restaurantId ?: return
        val api = reservations ?: return
        if (s.bookingBusy) return
        val start = kotlinx.datetime.LocalDateTime(move.day, time).toInstant(s.zone)
        val end = start + lengthOf(move.booking).minutes
        scope.launch {
            mutable.update { it.copy(bookingBusy = true, bookingError = null) }
            try {
                api.patchReservation(restaurantId, move.booking.id,
                    com.saporini.mobile_desktop.pos.reservations.data.dto.UpdateReservationRequestDto(reservationStart = start.toString(), reservationEnd = end.toString()))
                mutable.update { st ->
                    st.copy(move = null, closedBookings = st.closedBookings?.filter { it.id != move.booking.id }, closedCount = (st.closedCount - 1).coerceAtLeast(0),
                        done = DoneMark(++marks, "${move.booking.name ?: "The booking"} moved to ${shortDayName(move.day.dayOfWeek.ordinal + 1)} ${move.day.day} at ${time.hm()}"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(bookingError = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(bookingBusy = false) }
            }
        }
    }

    fun startCancel(booking: ClosedBookingDto) {
        if (state.value.canCancelBookings) mutable.update { it.copy(cancelling = booking, bookingError = null) }
    }

    fun closeCancel() = mutable.update { it.copy(cancelling = null, bookingError = null) }

    fun confirmCancel(reason: String) {
        val s = state.value
        val booking = s.cancelling ?: return
        val restaurantId = s.restaurantId ?: return
        val api = reservations ?: return
        if (s.bookingBusy) return
        scope.launch {
            mutable.update { it.copy(bookingBusy = true, bookingError = null) }
            try {
                api.cancelReservation(restaurantId, booking.id,
                    com.saporini.mobile_desktop.pos.reservations.data.dto.ReservationActionRequestDto(reason = reason.trim().ifBlank { null }))
                mutable.update { st ->
                    st.copy(cancelling = null, closedBookings = st.closedBookings?.filter { it.id != booking.id }, closedCount = (st.closedCount - 1).coerceAtLeast(0),
                        done = DoneMark(++marks, "${booking.name ?: "The booking"} cancelled"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(bookingError = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(bookingBusy = false) }
            }
        }
    }

    // ---- Open outside the hours ----

    fun askOpen(open: Boolean) = mutable.update { it.copy(opening = open, error = null) }

    fun openUntil(until: Instant) {
        val s = state.value
        val restaurantId = s.restaurantId ?: return
        val branchId = s.branchId ?: return
        scope.launch {
            mutable.update { it.copy(saving = true, error = null) }
            try {
                api.openNow(restaurantId, branchId, until.toString())
                mutable.update { it.copy(opening = false, notice = "Open until ${clockAt(until.toString(), s.zone)?.hm()}") }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }

    fun closeAgain() {
        val s = state.value
        val restaurantId = s.restaurantId ?: return
        val branchId = s.branchId ?: return
        scope.launch {
            mutable.update { it.copy(saving = true, error = null) }
            try {
                api.closeNow(restaurantId, branchId)
                mutable.update { it.copy(notice = "Closed again") }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }

    fun clearMessages() = mutable.update { it.copy(notice = null, error = null) }
}

internal fun dayName(day: Int): String = when (day) {
    1 -> "Monday"; 2 -> "Tuesday"; 3 -> "Wednesday"; 4 -> "Thursday"; 5 -> "Friday"; 6 -> "Saturday"; else -> "Sunday"
}

internal fun shortDayName(day: Int): String = dayName(day).take(3)

/** A booking's length in minutes (2 hours when it can't be read). */
internal fun lengthOf(booking: ClosedBookingDto): Int {
    val start = com.saporini.mobile_desktop.core.format.instantOrNull(booking.start)
    val end = com.saporini.mobile_desktop.core.format.instantOrNull(booking.end)
    return if (start != null && end != null && end > start) (end - start).inWholeMinutes.toInt() else 120
}
