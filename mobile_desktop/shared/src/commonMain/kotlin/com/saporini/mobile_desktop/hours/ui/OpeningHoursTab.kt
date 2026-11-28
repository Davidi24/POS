package com.saporini.mobile_desktop.hours.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.draw.alpha
import kotlinx.datetime.minus
import com.saporini.mobile_desktop.hours.covering
import com.saporini.mobile_desktop.hours.hoursText
import com.saporini.mobile_desktop.hours.specialSpans
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.DoneBurst
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.KitSwitch
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.components.RangeCalendar
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.UnsavedChangesBar
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.hours.DayDraft
import com.saporini.mobile_desktop.hours.MAX_HOURS_NOTE
import com.saporini.mobile_desktop.hours.MAX_OPENINGS_A_DAY
import com.saporini.mobile_desktop.hours.OpenNowDto
import com.saporini.mobile_desktop.hours.OpeningHoursModel
import com.saporini.mobile_desktop.hours.OpeningHoursState
import com.saporini.mobile_desktop.hours.SpanDraft
import com.saporini.mobile_desktop.hours.SpecialDayDto
import com.saporini.mobile_desktop.hours.SpecialDraft
import com.saporini.mobile_desktop.hours.clockAt
import com.saporini.mobile_desktop.hours.dateAt
import com.saporini.mobile_desktop.hours.dateOf
import com.saporini.mobile_desktop.hours.dayName
import com.saporini.mobile_desktop.hours.hm
import com.saporini.mobile_desktop.hours.shortDayName
import com.saporini.mobile_desktop.hours.spansProblem
import com.saporini.mobile_desktop.hours.specialProblem
import com.saporini.mobile_desktop.hours.text
import com.saporini.mobile_desktop.pos.shifts.SmallToggle
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The app's own red (user, 2026-11-23: "don't use colours never seen in the other pages").
private val ClosedRed = Kit.Danger
// The app's own green (user, 2026-11-23: "the green you use in open now, I don't like it").
private val OpenGreen = Kit.Green

/**
 * Admin Hub → Restaurant info → Opening hours (user, 2026-11-20): the branch (when there are several), whether it's open
 * now, its week and its special days. Changes to the week are saved with the floating bar; special days one by one.
 */
@Composable
internal fun OpeningHoursTab(state: OpeningHoursState, model: OpeningHoursModel, size: ScreenSize, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        ScrollColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OpeningHoursItems(state, model, size)
            Spacer(Modifier.height(if (state.changed) 96.dp else 12.dp))
        }
        OpeningHoursBar(state, model, size, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
    }
    OpeningHoursExtras(state, model)
}

/** The page's parts, for a column that scrolls (this tab's own, or Restaurant info's, user 2026-11-23). */
@Composable
internal fun OpeningHoursItems(state: OpeningHoursState, model: OpeningHoursModel, size: ScreenSize) {
    if (state.branches.size > 1) BranchChoice(state, model)
    when {
        state.branchId == null -> EmptyNote("No branches yet", "Opening hours belong to a branch. Add one first.")
        !state.loaded && state.loading -> LoadingCards()
        !state.loaded -> EmptyNote("Could not load the opening hours", state.error ?: "Check the connection and try again.") {
            KitButton("Try again", model::refresh, style = ButtonStyle.SECONDARY)
        }
        else -> {
            NowCard(state, model, size)
            if (state.closedCount > 0) ClosedBookingsLine(state.closedCount, model::showClosedBookings)
            CalendarPart(state, model, size)
        }
    }
}

private fun dialogOpen(state: OpeningHoursState) = state.special != null || state.removing != null || state.opening || state.closedBookings != null ||
    state.editingWeek

/** The floating bar that saves a changed week. */
@Composable
internal fun OpeningHoursBar(state: OpeningHoursState, model: OpeningHoursModel, size: ScreenSize, modifier: Modifier) {
    // The week is changed in its own window now; the bar stays for a change left outside it.
    if (state.changed && !state.editingWeek) UnsavedChangesBar(
        saving = state.saving, problem = model.weekProblem, error = state.error.takeIf { !dialogOpen(state) },
        onDiscard = model::discardWeek, onSave = model::saveWeek,
        message = if (size.isPhone) "Unsaved changes" else "You changed the opening hours",
        modifier = modifier
    )
}

/** Messages, the done tick and the windows (special day, open now, bookings in closed time). */
@Composable
internal fun OpeningHoursExtras(state: OpeningHoursState, model: OpeningHoursModel) {
    ScreenMessages(state.error.takeIf { !dialogOpen(state) && !state.changed && state.loaded }, state.notice, model::clearMessages)
    DoneBurst(state.done)
    state.special?.let { SpecialDayDialog(it, state, model) }
    if (state.editingWeek) WeekDialog(state, model)
    state.removing?.let { day ->
        ConfirmDialog("Remove this special day?", "${specialDates(day)} goes back to the usual week.", "Remove",
            onConfirm = model::removeSpecial, onDismiss = { model.askRemove(null) }, danger = true, busy = state.saving)
    }
    if (state.opening) OpenNowDialog(state.zone, state.saving, state.error, onDismiss = { model.askOpen(false) }, onOpen = model::openUntil)
    ClosedBookingsDialog(state, model)
}

/** Bookings still in closed time (user, 2026-11-21): a red line on the page until they're moved or cancelled. */
@Composable
private fun ClosedBookingsLine(count: Int, onSee: () -> Unit) {
    Surface(onClick = onSee, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = ClosedThenRed.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, ClosedThenRed.copy(alpha = 0.3f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.EventBusy, null, Modifier.size(18.dp), tint = ClosedThenRed)
            Text(if (count == 1) "A booking falls when the branch is closed" else "$count bookings fall when the branch is closed",
                Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = ClosedThenRed)
            Text("See them", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ClosedThenRed)
        }
    }
}

// ---- Pieces of the page ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BranchChoice(state: OpeningHoursState, model: OpeningHoursModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Branch", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.branches.forEach { branch ->
                SmallToggle(branch.name, branch.id == state.branchId, icon = Icons.Outlined.Storefront) { model.pickBranch(branch.id) }
            }
        }
    }
}

/** Whether the branch is open right now, and the button to open it outside its hours (or close it again). */
@Composable
private fun NowCard(state: OpeningHoursState, model: OpeningHoursModel, size: ScreenSize) {
    val hours = state.hours ?: return
    val now = hours.now
    val look = nowLook(now, hours.set, state.zone)
    val action: (@Composable () -> Unit)? = when {
        !hours.set && !state.changed && state.canChange -> ({ KitButton("Set opening hours", model::openWeek, icon = Icons.Outlined.Schedule) })
        now.why == "OPENED" && state.canOpen -> ({ KitButton("Close again", model::closeAgain, style = ButtonStyle.DANGER_OUTLINE, loading = state.saving) })
        hours.set && !now.open && state.canOpen -> ({ KitButton("Open now", { model.askOpen(true) }, icon = Icons.Outlined.LockOpen) })
        else -> null
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = look.tint, border = BorderStroke(1.dp, look.color.copy(alpha = 0.28f))) {
        val body: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), Alignment.Center) {
                    Icon(look.icon, null, Modifier.size(21.dp), tint = look.color)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (hours.set) Box(Modifier.size(8.dp).clip(CircleShape).background(look.color))
                        Text(look.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                    }
                    Text(look.line, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Muted)
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (size.isPhone) {
                body()
                action?.let { Box(Modifier.padding(start = 54.dp)) { it() } }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { body() }
                    action?.invoke()
                }
            }
        }
    }
}

private class NowLook(val title: String, val line: String, val color: Color, val tint: Color, val icon: ImageVector)

private fun nowLook(now: OpenNowDto, set: Boolean, zone: TimeZone): NowLook {
    val note = now.note?.takeIf { it.isNotBlank() }
    return when {
        !set -> NowLook("Opening hours aren't set yet", "Until they are, this branch counts as always open: nothing is blocked.",
            Kit.Amber, Color(0xFFFFF7E8), Icons.Outlined.Schedule)
        now.why == "OPENED" -> NowLook("Open outside the hours", listOfNotNull("Until ${clockAt(now.openedUntil ?: now.until, zone)?.hm().orEmpty()}", note)
            .joinToString(" · "), Kit.Amber, Color(0xFFFFF7E8), Icons.Outlined.LockOpen)
        now.open -> NowLook("Open now", listOfNotNull("Until ${clockAt(now.until, zone)?.hm().orEmpty()}", note).joinToString(" · "),
            OpenGreen, Kit.GreenSoft, Icons.Outlined.Storefront)
        else -> NowLook("Closed now", listOfNotNull(nextOpenText(now.nextOpen, zone) ?: "No opening in the next 30 days", note).joinToString(" · "),
            ClosedRed, Kit.Danger.copy(alpha = 0.07f), Icons.Outlined.EventBusy)
    }
}

/** "Opens at 19:00" today, "Opens tomorrow at 12:00", else "Opens Tue 24 Nov at 12:00". */
internal fun nextOpenText(nextOpen: String?, zone: TimeZone): String? {
    val time = clockAt(nextOpen, zone) ?: return null
    val date = dateAt(nextOpen, zone) ?: return null
    val today = Clock.System.todayIn(zone)
    return when (today.daysUntil(date)) {
        0 -> "Opens at ${time.hm()}"
        1 -> "Opens tomorrow at ${time.hm()}"
        else -> "Opens ${shortDate(date)} at ${time.hm()}"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayLine(day: DayDraft, state: OpeningHoursState, model: OpeningHoursModel, phone: Boolean) {
    val open = day.spans.isNotEmpty()
    val editable = state.canChange && !state.saving
    val problem = spansProblem(day.spans)
    val name: @Composable () -> Unit = {
        Text(dayName(day.day), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (open) Kit.Ink else Kit.Muted)
    }
    // On = open that day (its openings beside it), off = closed all day.
    val switch: @Composable () -> Unit = { KitSwitch(open, if (editable) ({ on -> model.setOpen(day.day, on) }) else null, compact = true) }
    val spans: @Composable () -> Unit = {
        if (!open) Text("Closed all day", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Faint)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            day.spans.forEachIndexed { i, span ->
                SpanEditor(span, editable, onChange = { model.changeSpan(day.day, i, it) },
                    onRemove = if (day.spans.size > 1) ({ model.removeSpan(day.day, i) }) else null)
            }
            if (editable && day.spans.size < MAX_OPENINGS_A_DAY) TextLink("Add opening", Icons.Outlined.Add) { model.addSpan(day.day) }
        }
    }
    val copy: @Composable () -> Unit = { if (editable && open) CopyMenu(day.day, model) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (phone) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { name() }
                switch()
                copy()
            }
            spans()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(104.dp)) { name() }
                switch()
                Box(Modifier.weight(1f)) { spans() }
                copy()
            }
        }
        problem?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger, modifier = Modifier.padding(start = if (phone) 0.dp else 168.dp)) }
    }
}

/** One opening: when it opens – when it closes ("next day" when it runs past midnight), and × to take it off. */
@Composable
private fun SpanEditor(span: SpanDraft, editable: Boolean, onChange: (SpanDraft) -> Unit, onRemove: (() -> Unit)?) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Kit.Tint).padding(start = 4.dp, end = if (onRemove != null) 2.dp else 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TimeChip(span.opens, editable) { onChange(span.copy(opens = it)) }
        Text("–", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Muted)
        TimeChip(span.closes, editable) { onChange(span.copy(closes = it)) }
        if (span.pastMidnight) Text("next day", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = Kit.Purple,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Kit.Purple.copy(alpha = 0.1f)).padding(horizontal = 7.dp, vertical = 2.dp))
        if (editable && onRemove != null) {
            Box(Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onRemove), Alignment.Center) {
                Icon(Icons.Outlined.Close, "Remove this opening", Modifier.size(15.dp), tint = Kit.Muted)
            }
        }
    }
}

/** A time in 15-minute steps, chosen from a grid that opens at the time shown. */
@Composable
internal fun TimeChip(value: LocalTime, enabled: Boolean = true, onChange: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                .border(1.dp, if (open) Kit.Green else Kit.Border, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { open = true }.padding(start = 10.dp, end = if (enabled) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(value.hm(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1, softWrap = false,
                translate = false)
            if (enabled) Icon(Icons.Outlined.ExpandMore, null, Modifier.size(15.dp), tint = Kit.Muted)
        }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 4.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
            val slots = (0 until 24 * 4).map { LocalTime(it / 4, (it % 4) * 15) }
            val rows = slots.chunked(4)
            val scroll = rememberScrollState()
            val step = with(LocalDensity.current) { 36.dp.toPx() }
            LaunchedEffect(Unit) {
                val row = rows.indexOfFirst { slot -> slot.any { it.hour == value.hour && it.minute / 15 == value.minute / 15 } }.coerceAtLeast(0)
                scroll.scrollTo(((row - 2).coerceAtLeast(0) * step).roundToInt())
            }
            Column(Modifier.width(250.dp).height(240.dp).verticalScroll(scroll).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { slot ->
                            val selected = slot == value
                            Box(
                                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) Kit.Green else Color.Transparent)
                                    .clickable { onChange(slot); open = false },
                                Alignment.Center
                            ) {
                                Text(slot.hm(), fontFamily = Inter(), fontWeight = if (selected) FontWeight.Bold else if (slot.minute == 0) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 12.sp, color = when { selected -> Color.White; slot.minute == 0 -> Kit.Ink; else -> Kit.Muted }, translate = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** ⋮-like copy button: the day's openings onto other days. */
@Composable
private fun CopyMenu(day: Int, model: OpeningHoursModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(32.dp).clip(CircleShape).clickable { open = true }, Alignment.Center) {
            Icon(Icons.Outlined.ContentCopy, "Copy ${dayName(day)}'s hours", Modifier.size(16.dp), tint = Kit.Muted)
        }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
            val choices = listOf(
                "Copy to every day" to (1..7).toList(),
                "Copy to Monday – Friday" to (1..5).toList(),
                "Copy to Saturday and Sunday" to listOf(6, 7)
            )
            choices.forEach { (label, days) ->
                DropdownMenuItem(text = { Text(label, fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink) },
                    onClick = { model.copyDay(day, days - day); open = false })
            }
        }
    }
}

@Composable
private fun SpecialLine(day: SpecialDayDto, editable: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    val from = dateOf(day.startsOn) ?: return
    val color = if (day.closed) ClosedRed else Kit.Blue
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = editable, onClick = onEdit).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(48.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().background(color).padding(vertical = 2.dp), Alignment.Center) {
                Text(monthShort(from).uppercase(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = Color.White)
            }
            Text("${from.day}", Modifier.padding(vertical = 3.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink,
                translate = false)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(specialDates(day), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (day.closed) "Closed" else day.spans.text(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = color)
                day.note?.takeIf { it.isNotBlank() }?.let {
                    Text("· $it", fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                }
            }
        }
        if (editable) {
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onEdit), Alignment.Center) {
                Icon(Icons.Outlined.Edit, "Change", Modifier.size(16.dp), tint = Kit.Muted)
            }
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onRemove), Alignment.Center) {
                Icon(Icons.Outlined.Delete, "Remove", Modifier.size(16.dp), tint = Kit.Danger)
            }
        }
    }
}

// ---- The calendar (user, 2026-11-23: "make it more like a calendar; you choose a day to make it a special day, or a closed day") ----

private enum class DayKind { OPEN, CLOSED, SPECIAL_CLOSED, SPECIAL_OPEN, NOT_SET }

private class DayInfo(val kind: DayKind, val spans: List<SpanDraft>, val note: String?)

/** What a date is: a special day's closed or other hours, else the usual week's day (never set = always open). */
private fun dayInfo(date: LocalDate, state: OpeningHoursState): DayInfo {
    state.hours?.specialDays.orEmpty().covering(date)?.let { special ->
        val note = special.note?.takeIf { it.isNotBlank() }
        return if (special.closed) DayInfo(DayKind.SPECIAL_CLOSED, emptyList(), note) else DayInfo(DayKind.SPECIAL_OPEN, specialSpans(special), note)
    }
    if (state.hours?.set != true) return DayInfo(DayKind.NOT_SET, emptyList(), null)
    val spans = state.saved.firstOrNull { it.day == date.dayOfWeek.isoDayNumber }?.spans.orEmpty()
    return if (spans.isEmpty()) DayInfo(DayKind.CLOSED, emptyList(), null) else DayInfo(DayKind.OPEN, spans, null)
}

private fun kindColor(kind: DayKind): Color = when (kind) {
    DayKind.OPEN -> OpenGreen
    DayKind.CLOSED -> Kit.Grey
    DayKind.SPECIAL_CLOSED -> ClosedRed
    DayKind.SPECIAL_OPEN -> Kit.Blue
    DayKind.NOT_SET -> Kit.Faint
}

/** The page: the month's calendar, and beside it (under it on narrow windows) the usual week and the special days coming up. */
@Composable
private fun CalendarPart(state: OpeningHoursState, model: OpeningHoursModel, size: ScreenSize) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 980.dp
        if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            HoursCalendar(state, model, size, Modifier.weight(1f))
            Column(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                UsualWeekCard(state, model, compact = true)
                ComingUpCard(state, model, compact = true)
            }
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HoursCalendar(state, model, size, Modifier.fillMaxWidth())
            UsualWeekCard(state, model, compact = size.isPhone)
            ComingUpCard(state, model, compact = size.isPhone)
        }
    }
}

/** A month at a time; each day says how it opens, special days in their colour; a click on a day to come changes it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HoursCalendar(state: OpeningHoursState, model: OpeningHoursModel, size: ScreenSize, modifier: Modifier) {
    val zone = state.zone
    val today = Clock.System.todayIn(zone)
    val thisMonth = LocalDate(today.year, today.month, 1)
    var month by remember(state.branchId) { mutableStateOf(thisMonth) }
    val phone = size.isPhone
    val editable = state.canChange && !state.saving
    val lead = month.dayOfWeek.isoDayNumber - 1
    val gridStart = month.minus(DatePeriod(days = lead))
    val last = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
    val weeks = (lead + last.day + 6) / 7
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 1.dp) {
        Column(Modifier.padding(if (phone) 12.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MonthArrow(Icons.Outlined.ChevronLeft, "Month before", month > thisMonth.minus(DatePeriod(months = 12))) { month = month.minus(DatePeriod(months = 1)) }
                Text("${monthName(month)} ${month.year}", Modifier.widthIn(min = if (phone) 0.dp else 170.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                    fontSize = if (phone) 17.sp else 19.sp, color = Kit.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                MonthArrow(Icons.Outlined.ChevronRight, "Next month", month < thisMonth.plus(DatePeriod(months = 24))) { month = month.plus(DatePeriod(months = 1)) }
                if (month != thisMonth) TextLink("Today", Icons.Outlined.Today) { month = thisMonth }
                Spacer(Modifier.weight(1f))
                if (!phone) Text("Times in ${state.hours?.timezone.orEmpty()}", fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Faint, maxLines = 1)
            }
            if (state.canChange) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.TouchApp, null, Modifier.size(16.dp), tint = Kit.Muted)
                Text("Click a day to close it or give it other hours. In its window you can pick several days.", fontFamily = Inter(), fontSize = 12.5.sp,
                    color = Kit.Muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(if (phone) 4.dp else 6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(if (phone) 4.dp else 6.dp)) {
                    (1..7).forEach { d ->
                        Text(if (phone) shortDayName(d).take(2) else shortDayName(d), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, letterSpacing = 0.4.sp, color = if (d == today.dayOfWeek.isoDayNumber && month == thisMonth) OpenGreen else Kit.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                repeat(weeks) { w ->
                    Row(horizontalArrangement = Arrangement.spacedBy(if (phone) 4.dp else 6.dp)) {
                        (0 until 7).forEach { d ->
                            val date = gridStart.plus(DatePeriod(days = w * 7 + d))
                            DayCell(date, dayInfo(date, state), inMonth = date.month == month.month, today = today, phone = phone,
                                clickable = editable && date >= today, modifier = Modifier.weight(1f)) { model.pickDay(date) }
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.hours?.set == true) {
                    LegendDot(OpenGreen, "Open")
                    LegendDot(Kit.Grey, "Closed")
                }
                LegendDot(ClosedRed, "Special day: closed")
                LegendDot(Kit.Blue, "Special day: other hours")
            }
        }
    }
}

@Composable
private fun MonthArrow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).border(1.dp, Kit.Border, CircleShape).clickable(enabled = enabled, onClick = onClick), Alignment.Center) {
        Icon(icon, label, Modifier.size(20.dp), tint = if (enabled) Kit.Ink else Kit.Faint)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
    }
}

/** One day: its number (today in a green circle) and how it opens; special days tinted; days gone by faded. */
@Composable
private fun DayCell(date: LocalDate, info: DayInfo, inMonth: Boolean, today: LocalDate, phone: Boolean, clickable: Boolean, modifier: Modifier,
                    onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = kindColor(info.kind)
    val special = info.kind == DayKind.SPECIAL_CLOSED || info.kind == DayKind.SPECIAL_OPEN
    val background = when (info.kind) {
        DayKind.SPECIAL_CLOSED, DayKind.SPECIAL_OPEN -> color.copy(alpha = 0.08f)
        DayKind.CLOSED -> Kit.Canvas
        else -> Color.White
    }
    val border = when {
        hovered && clickable -> Kit.Green
        special -> color.copy(alpha = 0.35f)
        else -> Kit.RowBorder
    }
    Column(
        modifier.height(if (phone) 54.dp else 96.dp).alpha(if (date < today) 0.45f else if (!inMonth) 0.55f else 1f)
            .clip(RoundedCornerShape(10.dp)).background(background).border(if (hovered && clickable) 1.5.dp else 1.dp, border, RoundedCornerShape(10.dp))
            .clickable(enabled = clickable, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(if (phone) 4.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (phone) 3.dp else 4.dp),
        horizontalAlignment = if (phone) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val isToday = date == today
            Box(Modifier.size(if (phone) 22.dp else 24.dp).clip(CircleShape).background(if (isToday) OpenGreen else Color.Transparent), Alignment.Center) {
                Text("${date.day}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (phone) 11.5.sp else 12.5.sp,
                    color = if (isToday) Color.White else Kit.Ink, translate = false)
            }
            if (!phone) {
                Spacer(Modifier.weight(1f))
                if (special) Icon(if (info.kind == DayKind.SPECIAL_CLOSED) Icons.Outlined.EventBusy else Icons.Outlined.Schedule, null,
                    Modifier.size(14.dp), tint = color)
            }
        }
        if (phone) {
            if (info.kind != DayKind.NOT_SET) Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        } else when (info.kind) {
            DayKind.OPEN, DayKind.SPECIAL_OPEN -> {
                info.spans.take(2).forEach { span ->
                    Text("${span.opens.hm()}–${span.closes.hm()}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
                        color = if (special) color else Kit.Ink, maxLines = 1, softWrap = false, translate = false)
                }
                if (info.spans.size > 2) Text("+${info.spans.size - 2} more", fontFamily = Inter(), fontSize = 10.5.sp, color = Kit.Muted)
            }
            DayKind.CLOSED -> Text("Closed", fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted)
            DayKind.SPECIAL_CLOSED -> Text("Closed", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = color)
            DayKind.NOT_SET -> Text("Always open", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint, maxLines = 1)
        }
        if (!phone && info.note != null && info.spans.size < 2) Text(info.note, fontFamily = Inter(), fontSize = 10.5.sp, lineHeight = 13.sp,
            color = color.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
    }
}

/** The usual week as text, with Change (its own window). */
@Composable
private fun UsualWeekCard(state: OpeningHoursState, model: OpeningHoursModel, compact: Boolean) {
    val set = state.hours?.set == true
    val today = Clock.System.todayIn(state.zone).dayOfWeek.isoDayNumber
    HoursCard(Icons.Outlined.Schedule, "Usual week",
        if (set) "Every week, unless a special day says otherwise." else "Not set yet: until it is, the branch counts as always open.",
        compact = compact,
        trailing = if (state.canChange) ({ KitButton(if (set) "Change" else "Set the week", model::openWeek, icon = Icons.Outlined.Edit,
            style = if (set) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY, enabled = !state.saving) }) else null) {
        if (set) (1..7).forEach { d ->
            val spans = state.saved.firstOrNull { it.day == d }?.spans.orEmpty()
            val on = d == today
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (on) Kit.GreenSoft else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayName(d), Modifier.width(98.dp), fontFamily = Inter(), fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp,
                    color = if (on) OpenGreen else Kit.Ink)
                Text(if (spans.isEmpty()) "Closed" else spans.hoursText(), Modifier.weight(1f), fontFamily = Inter(),
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, color = if (spans.isEmpty()) Kit.Muted else Kit.Ink,
                    translate = spans.isEmpty())
            }
        }
    }
}

/** Special days still to come, a line each (a click changes it). */
@Composable
private fun ComingUpCard(state: OpeningHoursState, model: OpeningHoursModel, compact: Boolean) {
    val today = Clock.System.todayIn(state.zone)
    val days = state.hours?.specialDays.orEmpty().filter { (dateOf(it.endsOn) ?: dateOf(it.startsOn) ?: today) >= today }.sortedBy { it.startsOn }
    HoursCard(Icons.Outlined.DateRange, "Special days coming up", "Holidays, a private event, a day you open late. They win over the week.",
        compact = compact) {
        if (days.isEmpty()) Text(if (state.canChange) "None yet. Click a day on the calendar to add one." else "None coming up.", fontFamily = Inter(),
            fontSize = 13.sp, color = Kit.Faint)
        days.forEachIndexed { index, day ->
            SpecialLine(day, state.canChange && !state.saving, onEdit = { model.editSpecial(day) }, onRemove = { model.askRemove(day) })
            if (index < days.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))
        }
    }
}

/** The usual week's window: a line per day, saved together. */
@Composable
private fun WeekDialog(state: OpeningHoursState, model: OpeningHoursModel) {
    val problem = model.weekProblem
    AppDialog("Usual week", model::closeWeek, busy = state.saving, maxWidth = 860.dp,
        subtitle = "When guests can book and the POS seats guests and starts orders. A closing at or before the opening is the next day (19:00–01:00).",
        buttons = {
            KitButton("Cancel", model::closeWeek, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton("Save", model::saveWeek, loading = state.saving, enabled = (state.changed || state.hours?.set != true) && problem == null)
        }) {
        val narrow = LocalNarrowDialog.current
        state.week.forEachIndexed { index, day ->
            DayLine(day, state, model, narrow)
            if (index < state.week.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))
        }
        state.error?.let { Text(it, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Danger) }
    }
}

/** A choice in the day's window: an icon in its colour, a name and a line; the chosen one framed. */
@Composable
private fun ChoiceTile(icon: ImageVector, title: String, line: String, color: Color, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) color.copy(alpha = 0.07f) else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) color.copy(alpha = 0.7f) else Kit.Border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)), Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink)
            Text(line, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
        }
        Box(Modifier.size(18.dp).clip(CircleShape).border(if (selected) 5.dp else 1.5.dp, if (selected) color else Kit.Border, CircleShape))
    }
}

// ---- Dialogs ----

/**
 * A day (or a few in a row) picked on the calendar: the usual hours, closed, or other hours, with a note. The calendar on
 * the left picks more days; an existing special day can go back to the usual hours.
 */
@Composable
private fun SpecialDayDialog(draft: SpecialDraft, state: OpeningHoursState, model: OpeningHoursModel) {
    val today = Clock.System.todayIn(state.zone)
    val problem = if (draft.showProblems) specialProblem(draft) else null
    val changed = state.savedSpecial == null || draft.copy(showProblems = false) != state.savedSpecial
    val weekday = draft.from.dayOfWeek.isoDayNumber
    val usualSpans = state.saved.firstOrNull { it.day == weekday }?.spans.orEmpty()
    val usualLine = when {
        state.hours?.set != true -> "Always open: the usual week isn't set yet"
        draft.from != draft.to -> "As the usual week says"
        usualSpans.isEmpty() -> "${dayName(weekday)}: closed"
        else -> "${dayName(weekday)}: ${usualSpans.hoursText()}"
    }
    val editable = !state.saving
    AppDialog(rangeText(draft.from, draft.to), model::cancelSpecial, busy = state.saving, maxWidth = 780.dp,
        subtitle = if (draft.id == null) "How these days open. Pick more days on the calendar for a few in a row." else "A special day: change it, or put it back to the usual hours.",
        buttons = {
            KitButton("Cancel", model::cancelSpecial, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton(if (draft.usual && draft.id != null) "Back to usual hours" else "Save", model::saveSpecial, loading = state.saving,
                enabled = changed && !(draft.usual && draft.id == null))
        }) {
        val narrow = LocalNarrowDialog.current
        val calendar: @Composable () -> Unit = {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))) {
                RangeCalendar(draft.from, draft.to, maxDate = today.plus(DatePeriod(years = 2)), maxDays = 366) { from, to ->
                    model.changeSpecial { it.copy(from = from, to = to) }
                }
            }
        }
        val form: @Composable ColumnScope.() -> Unit = {
            Text("These days are", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceTile(Icons.Outlined.Storefront, "Usual hours", usualLine, OpenGreen, draft.usual, editable) {
                    model.changeSpecial { it.copy(usual = true) }
                }
                ChoiceTile(Icons.Outlined.EventBusy, "Closed", "No bookings, and the POS doesn't seat guests or start orders.", ClosedRed,
                    !draft.usual && draft.closed, editable) { model.changeSpecial { it.copy(usual = false, closed = true) } }
                ChoiceTile(Icons.Outlined.Schedule, "Other hours", "Open, but at other times.", Kit.Blue, !draft.usual && !draft.closed, editable) {
                    model.changeSpecial { it.copy(usual = false, closed = false) }
                }
            }
            if (!draft.usual && !draft.closed) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.spans.forEachIndexed { i, span ->
                        SpanEditor(span, editable, onChange = { changed -> model.changeSpecial { d -> d.copy(spans = d.spans.mapIndexed { j, s -> if (j == i) changed else s }) } },
                            onRemove = if (draft.spans.size > 1) ({ model.changeSpecial { d -> d.copy(spans = d.spans.filterIndexed { j, _ -> j != i }) } }) else null)
                    }
                    if (draft.spans.size < MAX_OPENINGS_A_DAY) TextLink("Add opening", Icons.Outlined.Add) {
                        model.changeSpecial { d ->
                            val last = d.spans.maxByOrNull { it.opens }
                            val start = last?.closes?.takeIf { !last.pastMidnight }?.let { LocalTime(minOf(it.hour + 1, 23), it.minute) } ?: LocalTime(19, 0)
                            d.copy(spans = d.spans + SpanDraft(start, LocalTime(minOf(start.hour + 4, 23), start.minute)))
                        }
                    }
                }
            }
            if (!draft.usual) TextInput(draft.note, { v -> model.changeSpecial { it.copy(note = v) } }, Modifier.fillMaxWidth(), label = "Note", optional = true,
                placeholder = "Christmas Eve, a private event…", maxLength = MAX_HOURS_NOTE, enabled = editable,
                hint = "Guests see it on the booking page.")
            (problem ?: state.error)?.let { Text(it, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Danger) }
        }
        if (narrow) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) { calendar() }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = form)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                calendar()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = form)
            }
        }
    }
}

/**
 * Opening a closed branch for now (a late group, an event): for an hour, two, three, or until a time; at most 18 hours.
 * Shared by Restaurant info and the POS's closed strip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OpenNowDialog(zone: TimeZone, busy: Boolean, error: String?, onDismiss: () -> Unit, onOpen: (Instant) -> Unit) {
    var hoursChoice by remember { mutableStateOf<Int?>(2) }
    var untilTime by remember {
        val now = Clock.System.now().toLocalDateTime(zone).time
        mutableStateOf(LocalTime((now.hour + 2) % 24, 0))
    }
    val now = Clock.System.now()
    val until: Instant = hoursChoice?.let { now + it.hours } ?: run {
        val today = Clock.System.todayIn(zone)
        val at = LocalDateTime(today, untilTime).toInstant(zone)
        if (at <= now) LocalDateTime(today.plus(DatePeriod(days = 1)), untilTime).toInstant(zone) else at
    }
    val tooLong = until - now > 18.hours
    AppDialog("Open outside the hours", onDismiss, busy = busy, maxWidth = 480.dp,
        subtitle = "Guests can be seated and orders started until then.",
        buttons = {
            KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY, enabled = !busy)
            KitButton("Open now", { onOpen(until) }, icon = Icons.Outlined.LockOpen, loading = busy, enabled = !tooLong)
        }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 2, 3).forEach { h ->
                SmallToggle(if (h == 1) "1 hour" else "$h hours", hoursChoice == h) { hoursChoice = h }
            }
            SmallToggle("Until a time", hoursChoice == null, icon = Icons.Outlined.Schedule) { hoursChoice = null }
        }
        if (hoursChoice == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Until", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                TimeChip(untilTime) { untilTime = it }
            }
        }
        val untilLocal = until.toLocalDateTime(zone)
        val tomorrow = untilLocal.date > Clock.System.todayIn(zone)
        Text(if (tomorrow) "Open until ${untilLocal.time.hm()} tomorrow" else "Open until ${untilLocal.time.hm()}",
            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
        if (tooLong) Text("Open it for at most 18 hours.", fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Danger)
        error?.let { Text(it, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Danger) }
    }
}

// ---- Shared looks ----

@Composable
private fun HoursCard(icon: ImageVector, title: String, note: String, compact: Boolean, trailing: (@Composable () -> Unit)? = null,
                      content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(if (compact) 14.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(if (compact) 36.dp else 40.dp).clip(RoundedCornerShape(11.dp)).background(Kit.GreenSoft), Alignment.Center) {
                    Icon(icon, null, Modifier.size(if (compact) 18.dp else 20.dp), tint = Kit.Green)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                    Text(note, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
                    if (compact) trailing?.let { Box(Modifier.padding(top = 6.dp)) { it() } }
                }
                if (!compact) trailing?.invoke()
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))
            content()
        }
    }
}

@Composable
private fun TextLink(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, Modifier.size(15.dp), tint = Kit.Green)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Green, maxLines = 1)
    }
}

@Composable
private fun EmptyNote(title: String, line: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Schedule, null, Modifier.size(34.dp), tint = Kit.Faint)
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
        Text(line, fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
        action?.invoke()
    }
}

/** Pulsing shapes of the two cards while the hours load. */
@Composable
private fun LoadingCards() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(14.dp)).background(Kit.Canvas))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Kit.Border, RoundedCornerShape(14.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(7) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(90.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Kit.Canvas))
                    Box(Modifier.width(150.dp).height(28.dp).clip(RoundedCornerShape(8.dp)).background(Kit.Canvas))
                }
            }
        }
    }
}

// ---- Dates in words ----

internal fun monthName(date: LocalDate): String = date.month.name.lowercase().replaceFirstChar { it.uppercase() }

internal fun monthShort(date: LocalDate): String = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

/** "Tue 24 Nov", with the year when it isn't this year. */
internal fun shortDate(date: LocalDate, thisYear: Int = Clock.System.todayIn(TimeZone.currentSystemDefault()).year): String =
    "${shortDayName(date.dayOfWeek.isoDayNumber)} ${date.day} ${monthShort(date)}" + if (date.year != thisYear) " ${date.year}" else ""

/** "Thu 24 Dec" or "24 Dec – 2 Jan · 10 days". */
internal fun rangeText(from: LocalDate, to: LocalDate): String {
    if (from == to) return shortDate(from)
    val days = from.daysUntil(to) + 1
    return "${from.day} ${monthShort(from)} – ${to.day} ${monthShort(to)}" + (if (to.year != from.year || from.year != Clock.System.todayIn(TimeZone.currentSystemDefault()).year) " ${to.year}" else "") +
        " · $days days"
}

private fun specialDates(day: SpecialDayDto): String {
    val from = dateOf(day.startsOn) ?: return day.startsOn
    return rangeText(from, dateOf(day.endsOn) ?: from)
}
