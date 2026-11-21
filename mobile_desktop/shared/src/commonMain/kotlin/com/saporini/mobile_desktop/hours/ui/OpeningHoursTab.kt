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

private val ClosedRed = Color(0xFFDC2626)
private val OpenGreen = Color(0xFF16A34A)

/**
 * Admin Hub → Restaurant info → Opening hours (user, 2026-11-20): the branch (when there are several), whether it's open
 * now, its week and its special days. Changes to the week are saved with the floating bar; special days one by one.
 */
@Composable
internal fun OpeningHoursTab(state: OpeningHoursState, model: OpeningHoursModel, size: ScreenSize, modifier: Modifier = Modifier) {
    val dialogOpen = state.special != null || state.removing != null || state.opening
    Box(modifier.fillMaxWidth()) {
        ScrollColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.branches.size > 1) BranchChoice(state, model)
            when {
                state.branchId == null -> EmptyNote("No branches yet", "Opening hours belong to a branch. Add one first.")
                !state.loaded && state.loading -> LoadingCards()
                !state.loaded -> EmptyNote("Could not load the opening hours", state.error ?: "Check the connection and try again.") {
                    KitButton("Try again", model::refresh, style = ButtonStyle.SECONDARY)
                }
                else -> {
                    NowCard(state, model, size)
                    if (state.hours?.set == true || state.changed) WeekCard(state, model, size)
                    SpecialDaysCard(state, model, size)
                }
            }
            Spacer(Modifier.height(if (state.changed) 96.dp else 12.dp))
        }
        if (state.changed) UnsavedChangesBar(
            saving = state.saving, problem = model.weekProblem, error = state.error.takeIf { !dialogOpen },
            onDiscard = model::discardWeek, onSave = model::saveWeek,
            message = if (size.isPhone) "Unsaved changes" else "You changed the opening hours",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
        )
    }
    ScreenMessages(state.error.takeIf { !dialogOpen && !state.changed && state.loaded }, state.notice, model::clearMessages)
    DoneBurst(state.done)
    state.special?.let { SpecialDayDialog(it, state, model) }
    state.removing?.let { day ->
        ConfirmDialog("Remove this special day?", "${specialDates(day)} goes back to the usual week.", "Remove",
            onConfirm = model::removeSpecial, onDismiss = { model.askRemove(null) }, danger = true, busy = state.saving)
    }
    if (state.opening) OpenNowDialog(state.zone, state.saving, state.error, onDismiss = { model.askOpen(false) }, onOpen = model::openUntil)
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
        !hours.set && !state.changed && state.canChange -> ({ KitButton("Set opening hours", model::startWeek, icon = Icons.Outlined.Schedule) })
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
            OpenGreen, Color(0xFFF0FAF3), Icons.Outlined.Storefront)
        else -> NowLook("Closed now", listOfNotNull(nextOpenText(now.nextOpen, zone) ?: "No opening in the next 30 days", note).joinToString(" · "),
            ClosedRed, Color(0xFFFEF2F2), Icons.Outlined.EventBusy)
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

/** The week: a line per day, open or closed and its openings. */
@Composable
private fun WeekCard(state: OpeningHoursState, model: OpeningHoursModel, size: ScreenSize) {
    HoursCard(Icons.Outlined.Schedule, "The week",
        "When guests can book and the POS seats guests and starts orders. A closing at or before the opening is the next day (19:00–01:00).",
        compact = size.isPhone,
        trailing = { Text("Times in ${state.hours?.timezone.orEmpty()}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint, maxLines = 1) }) {
        state.week.forEachIndexed { index, day ->
            DayLine(day, state, model, size.isPhone)
            if (index < state.week.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))
        }
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

/** Special days: upcoming ones as lines with a date leaf, closed in red, other hours in blue. */
@Composable
private fun SpecialDaysCard(state: OpeningHoursState, model: OpeningHoursModel, size: ScreenSize) {
    val today = Clock.System.todayIn(state.zone)
    val days = state.hours?.specialDays.orEmpty().filter { (dateOf(it.endsOn) ?: dateOf(it.startsOn) ?: today) >= today }
        .sortedBy { it.startsOn }
    HoursCard(Icons.Outlined.DateRange, "Special days", "Holidays, a private event, a day you open late. They win over the week.",
        compact = size.isPhone,
        trailing = if (state.canChange) ({ KitButton("Add special day", model::newSpecial, icon = Icons.Outlined.Add, enabled = !state.saving) }) else null) {
        if (days.isEmpty()) {
            Text("No special days coming up.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Faint)
        }
        days.forEachIndexed { index, day ->
            SpecialLine(day, state.canChange && !state.saving, onEdit = { model.editSpecial(day) }, onRemove = { model.askRemove(day) })
            if (index < days.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))
        }
        if (state.hours?.set != true && days.isNotEmpty()) {
            Text("Special days count once the week's hours are set.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Amber)
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

// ---- Dialogs ----

@Composable
private fun SpecialDayDialog(draft: SpecialDraft, state: OpeningHoursState, model: OpeningHoursModel) {
    val today = Clock.System.todayIn(state.zone)
    val problem = if (draft.showProblems) specialProblem(draft) else null
    val changed = state.savedSpecial == null || draft.copy(showProblems = false) != state.savedSpecial
    AppDialog(if (draft.id == null) "Add special day" else "Change special day", model::cancelSpecial, busy = state.saving, maxWidth = 700.dp,
        subtitle = "A date or a few days in a row: closed, or other hours.",
        buttons = {
            KitButton("Cancel", model::cancelSpecial, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton("Save", model::saveSpecial, loading = state.saving, enabled = changed)
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
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Which days", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                Text(rangeText(draft.from, draft.to), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Those days", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallToggle("Closed", draft.closed, icon = Icons.Outlined.EventBusy) { model.changeSpecial { it.copy(closed = true) } }
                    SmallToggle("Other hours", !draft.closed, icon = Icons.Outlined.Schedule) { model.changeSpecial { it.copy(closed = false) } }
                }
            }
            if (!draft.closed) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.spans.forEachIndexed { i, span ->
                        SpanEditor(span, !state.saving, onChange = { changed -> model.changeSpecial { d -> d.copy(spans = d.spans.mapIndexed { j, s -> if (j == i) changed else s }) } },
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
            TextInput(draft.note, { v -> model.changeSpecial { it.copy(note = v) } }, Modifier.fillMaxWidth(), label = "Note", optional = true,
                placeholder = "Christmas Eve, a private event…", maxLength = MAX_HOURS_NOTE, enabled = !state.saving,
                hint = "Guests see it on the booking page.")
            (problem ?: state.error)?.let { Text(it, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Danger) }
        }
        if (narrow) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) { calendar() }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = form)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                calendar()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = form)
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

private fun monthShort(date: LocalDate): String = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

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
