@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.reservations.*
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

private val ShiftBorder = Color(0xFFE3E8E1)
internal val ShiftBlue = Color(0xFF24748A)
internal val ShiftAmber = Color(0xFFC8790B)

@Composable
fun ShiftScreen(modifier: Modifier = Modifier, management: Boolean = false) {
    val model = koinInject<ShiftScreenModel>()
    val state by model.state.collectAsState()
    DisposableEffect(model, management) {
        model.start(management)
        onDispose { model.onDispose() }
    }
    LaunchedEffect(model) { while (true) { delay(30_000); if (!model.state.value.busy) model.refresh() } }
    ShiftContent(state, management, model, modifier)
}

/** Admin Hub → Salary: its own section next to Shifts. */
@Composable
fun HoursAndPayScreen(modifier: Modifier = Modifier) {
    val model = koinInject<ShiftScreenModel>()
    val state by model.state.collectAsState()
    DisposableEffect(model) {
        model.start(true)
        onDispose { model.onDispose() }
    }
    HoursAndPayContent(state, modifier)
}

@Composable
internal fun ShiftContent(
    state: ShiftState, management: Boolean, model: ShiftScreenModel, modifier: Modifier = Modifier,
    startOnTeam: Boolean = false, openDay: kotlinx.datetime.LocalDate? = null
) {
    if (!management) { MyShiftContent(state, model, modifier, startOnTeam, openDay); return }
    // Admin Hub → Shifts: the usual shifts and the month calendar, under its own title row with Plan shifts. Salary is its own
    // section in the Admin Hub bar.
    ShiftPage(null, null, modifier) { size, side ->
        ShiftSchedule(state, model, size, Modifier.weight(1f), side = side, openDay = openDay)
    }
}

@Composable
internal fun HoursAndPayContent(
    state: ShiftState, modifier: Modifier = Modifier, startOpen: String? = null, startEditing: String? = null, startTeam: String? = null,
    startPaying: String? = null, startPayingPerson: String? = null
) {
    // Salary draws its own Inventory-style header (title left, the period on the right).
    HoursAndPay(state, modifier, startOpen, startEditing, startTeam, startPaying, startPayingPerson)
}

/** A page with its title on top. The content pads its own sides, so an edge button can sit on the window's edge. */
@Composable
private fun ShiftPage(title: String?, subtitle: String?, modifier: Modifier,
                      content: @Composable ColumnScope.(com.saporini.mobile_desktop.core.ui.ScreenSize, androidx.compose.ui.unit.Dp) -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = com.saporini.mobile_desktop.core.ui.screenSizeFor(maxWidth)
        val side = if (size.isPhone) 12.dp else 22.dp
        Column(Modifier.fillMaxSize().padding(top = if (size.isPhone) 14.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(if (size.isPhone) 10.dp else 14.dp)) {
            if (title != null) Column(Modifier.padding(horizontal = side)) {
                Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (size.isPhone) 19.sp else 20.sp, letterSpacing = 0.sp, color = FormInk, maxLines = 1)
                subtitle?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = FormMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            content(size, side)
        }
    }
}

@Composable internal fun ShiftPanel(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, ShiftBorder)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(icon, null, Modifier.size(20.dp), tint = FormInk)
                Text(title, fontFamily = Inter(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FormInk)
            }
            content()
        }
    }
}
@Composable private fun ShiftButton(text: String, icon: ImageVector, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) FormGreen else Color.White, contentColor = if (primary) Color.White else FormInk),
        border = if (primary) null else BorderStroke(1.dp, FormBorder), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp)
    }
}
@Composable private fun ShiftBadge(status: String) {
    val color = shiftColor(status)
    Text(shiftStatus(status), Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 5.dp), fontFamily = Inter(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
}
@Composable private fun Muted(text: String) { Text(text, fontFamily = Inter(), fontSize = 12.sp, color = FormMuted) }
@Composable internal fun ShiftMessage(text: String, error: Boolean, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (error) FormDanger.copy(alpha=.08f) else FormGreenSoft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = if (error) FormDanger else FormGreen)
        IconButton(onDismiss, Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, "Dismiss", Modifier.size(17.dp)) }
    }
}
@Composable private fun ShiftDialog(title: String, busy: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 620.dp).fillMaxWidth().heightIn(max = 760.dp), shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), fontFamily = Inter(), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = FormInk)
                    IconButton(onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "Close") }
                }
                content()
            }
        }
    }
}
@Composable private fun StaffPicker(staff: List<ShiftStaff>, selected: String?, placeholder: String, allowAll: Boolean = false, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, FormBorder)) {
            Text(staff.find { it.id == selected }?.name ?: placeholder, Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = FormInk)
        }
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max=300.dp).background(Color.White)) {
            if (allowAll) DropdownMenuItem(text = { Text("All staff", fontFamily = Inter()) }, onClick = { onSelect(null); expanded = false })
            staff.forEach { user -> DropdownMenuItem(text = { Text(user.name, fontFamily = Inter(), fontSize = 13.sp) }, onClick = { onSelect(user.id); expanded = false }) }
        }
    }
}
@Composable internal fun ShiftDetails(item: ShiftItem, zone: TimeZone, state: ShiftState, now: Instant, onDismiss: () -> Unit, onEdit: () -> Unit, onCorrect: () -> Unit, onAction: (String) -> Unit) {
    val day = item.start.shiftLocal(zone)?.date
    val role = state.board?.staff?.firstOrNull { it.id == item.userId }?.roles?.firstOrNull()
    com.saporini.mobile_desktop.core.components.AppDialog(
        "Shift", onDismiss, busy = state.busy, maxWidth = 580.dp,
        subtitle = day?.let { "${it.dayOfWeek.name.lowercase().replaceFirstChar { c -> c.uppercase() }}, ${it.day} ${it.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }} · times in ${zone.id}" }
    ) {
        // Who, and where the shift stands.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FormGreenSoft.copy(alpha = 0.6f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShiftAvatar(item.userName, 46.dp)
            Column(Modifier.weight(1f)) {
                Text(item.userName, fontFamily = Inter(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(role ?: "Team member", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
            }
            ShiftBadge(item.status)
        }
        // The times, two by two.
        val planned = item.plannedMinutes()
        val lines = listOfNotNull(
            item.scheduledStart?.let { "Planned" to "${it.shiftTime(zone)} – ${item.scheduledEnd.shiftTime(zone)}${if (planned > 0) "  ·  ${shiftDuration(planned)}" else ""}" },
            "Worked" to if (item.startedAt == null) "—" else shiftDuration(item.workedMinutes),
            "Clocked in" to item.startedAt.shiftTime(zone),
            "Clocked out" to (item.endedAt?.shiftTime(zone) ?: if (item.active) "Still working" else "—"),
            "Breaks" to if (item.breakMinutes > 0) shiftDuration(item.breakMinutes) else "None"
        )
        lines.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (label, value) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, ShiftBorder, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Muted(label)
                        Text(value, fontFamily = Inter(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (item.breaks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Breaks", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormInk)
                item.breaks.forEach { br ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Coffee, null, Modifier.size(16.dp), tint = ShiftAmber)
                        Text(if (br.paid) "Paid break" else "${br.type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }} (unpaid)", Modifier.weight(1f),
                            fontFamily = Inter(), fontSize = 13.sp, color = FormInk)
                        Text("${br.startedAt.shiftTime(zone)} – ${br.endedAt?.shiftTime(zone) ?: "now"}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
                    }
                }
            }
        }
        if (!item.notes.isNullOrBlank()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F9F7)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.StickyNote2, null, Modifier.size(17.dp), tint = FormMuted)
                Text(item.notes, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = FormInk)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.userId == state.userId && state.canSelf && item.status == "SCHEDULED" && state.board?.current == null && canClockIn(item, now, state.board?.clockInEarlyMinutes ?: 120)) ShiftButton("Clock in", Icons.Outlined.PlayArrow, enabled = !state.busy) { onAction("clock-in") }
            if (state.canManage && item.status == "SCHEDULED") {
                ShiftButton("Edit shift", Icons.Outlined.Edit, enabled = !state.busy, onClick = onEdit)
                ShiftButton("Cancel shift", Icons.Outlined.Cancel, primary = false, enabled = !state.busy) { onAction("cancel") }
                if (item.scheduledEnd?.let { Instant.parse(it) < now } == true) ShiftButton("Mark missed", Icons.Outlined.PersonOff, primary = false, enabled = !state.busy) { onAction("missed") }
            }
            if (state.canManage && (item.status == "CLOSED" || item.status == "MISSED" || (item.status == "SCHEDULED" && item.scheduledEnd?.let { Instant.parse(it) < now } == true))) ShiftButton(if (item.status == "CLOSED") "Correct attendance" else "Record attendance", Icons.Outlined.Edit, enabled = !state.busy, onClick = onCorrect)
            if (item.active && (state.canManage || (state.canSelf && state.userId == item.userId))) ShiftButton("End shift", Icons.Outlined.StopCircle, primary = false, enabled = !state.busy) { onAction("close") }
        }
    }
}
@Composable private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Muted(label); Text(value, fontFamily = Inter(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FormInk) }
}

@Composable internal fun ShiftEditor(state: ShiftState, original: ShiftItem?, correction: Boolean, initialDate: LocalDate, initialStaffId: String?, initialStartTime: String = "09:00", onDismiss: () -> Unit, onSave: (ShiftSchedule) -> Unit, onCorrect: (ShiftItem, ShiftCorrection) -> Unit) {
    // Keep the version that the editor opened with; never overwrite a newer edit silently.
    val item = original
    val zone = state.zone
    val start = (if (correction) item?.startedAt ?: item?.scheduledStart else item?.scheduledStart).shiftLocal(zone)
    val end = (if (correction) item?.endedAt ?: item?.scheduledEnd else item?.scheduledEnd).shiftLocal(zone)
    var staff by remember(item?.id) { mutableStateOf(item?.userId ?: initialStaffId) }
    var startDate by remember(item?.id) { mutableStateOf(start?.date ?: initialDate) }
    var endDate by remember(item?.id, initialStartTime) { mutableStateOf(end?.date ?: initialDate.plus(DatePeriod(days = if ((initialStartTime.take(2).toIntOrNull() ?: 9) + 8 >= 24) 1 else 0))) }
    var startTime by remember(item?.id, initialStartTime) { mutableStateOf(start?.time?.toString()?.take(5) ?: initialStartTime) }
    val defaultEndMinutes = ((initialStartTime.take(2).toIntOrNull() ?: 9) * 60 + (initialStartTime.drop(3).take(2).toIntOrNull() ?: 0) + 8 * 60) % (24 * 60)
    val defaultEndTime = "${(defaultEndMinutes / 60).toString().padStart(2, '0')}:${(defaultEndMinutes % 60).toString().padStart(2, '0')}"
    var endTime by remember(item?.id, initialStartTime) { mutableStateOf(end?.time?.toString()?.take(5) ?: defaultEndTime) }
    var notes by remember { mutableStateOf(if (correction) "" else item?.notes.orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }
    ShiftDialog(if (correction) "Correct attendance" else if (item == null) "Schedule shift" else "Edit shift", state.busy, onDismiss) {
        Muted("Times shown in ${zone.id}. Use the next date for an overnight shift.")
        if (!correction) FormField("Staff member", required = true) {
            if (item == null) StaffPicker(state.board?.staff.orEmpty(), staff, "Select staff") { staff = it }
            else Text(item.userName, fontFamily = Inter(), fontSize = 14.sp, color = FormInk)
        }
        if (!correction && state.board?.staff.isNullOrEmpty() && item == null) Muted("No active staff in this branch. Assign staff to the branch before scheduling.")
        if (!correction) {
            // A usual shift sets both times (and the next day for one that ends after midnight).
            val templates by ShiftTemplates.all.collectAsState()
            if (templates.isNotEmpty()) FormField("Usual shifts", optional = true) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    templates.forEach { t ->
                        SmallToggle("${t.name} · ${t.times}", startTime == t.start.hhmm() && endTime == t.end.hhmm()) {
                            startTime = t.start.hhmm(); endTime = t.end.hhmm(); endDate = if (t.overnight) startDate.plus(DatePeriod(days = 1)) else startDate
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField("Start date", Modifier.weight(1f).widthIn(min = 190.dp), required = true) { DateBox(startDate) { startDate = it } }
            FormField("Start time", Modifier.weight(1f).widthIn(min = 190.dp), required = true) { InputBox(Icons.Outlined.Schedule, startTime, "HH:mm") { startTime = it.take(5) } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField("End date", Modifier.weight(1f).widthIn(min = 190.dp), required = true) { DateBox(endDate) { endDate = it } }
            FormField("End time", Modifier.weight(1f).widthIn(min = 190.dp), required = true) { InputBox(Icons.Outlined.Schedule, endTime, "HH:mm") { endTime = it.take(5) } }
        }
        FormField(if (correction) "Reason for correction" else "Shift notes", required = correction, optional = !correction) {
            NoteBox(notes, if (correction) "Explain why the attendance needs correcting" else "Section, handover, or a note for this shift") { notes = it.take(500) }
        }
        if (correction) Muted("Recorded breaks must remain inside the corrected times. Your change and reason will be saved in the audit trail.")
        (problem ?: state.error)?.let { ErrorText(it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ShiftButton(if (state.busy) "Saving…" else "Save shift", Icons.Outlined.Check, enabled = !state.busy) {
                val from = parseShiftTime(startDate, startTime, zone)
                val to = parseShiftTime(endDate, endTime, zone)
                problem = when {
                    !correction && staff == null -> "Select a staff member."
                    from == null || to == null -> "Enter valid times as HH:mm. This local time must exist in the restaurant’s timezone."
                    to <= from -> "End time must be after start time. For overnight shifts, choose the next date."
                    (to - from).inWholeMinutes > 24 * 60 -> "A shift can be at most 24 hours."
                    correction && notes.isBlank() -> "Enter a reason for the correction."
                    else -> null
                }
                if (problem == null && from != null && to != null) {
                    if (correction && item != null) onCorrect(item, ShiftCorrection(item.version, from.toString(), to.toString(), notes.trim()))
                    else onSave(ShiftSchedule(staff!!, from.toString(), to.toString(), notes.trim().ifBlank { null }, item?.version ?: 0))
                }
            }
        }
    }
}
@Composable internal fun ShiftActionDialog(action: String, item: ShiftItem?, state: ShiftState, onDismiss: () -> Unit, onConfirm: (String?, String) -> Unit) {
    var notes by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Unpaid break") }
    val needsReason = action in setOf("cancel", "missed") || (action == "close" && item?.userId != state.userId)
    val title = when (action) { "clock-in" -> "Clock in"; "break" -> "Start break"; "cancel" -> "Cancel shift"; "missed" -> "Mark shift missed"; else -> "End shift" }
    ShiftDialog(title, state.busy, onDismiss) {
        Text(when (action) {
            "clock-in" -> if (item == null) "Start an unscheduled shift now? Your clock-in time will be recorded." else "Clock in for your ${item.scheduledStart.shiftLabel(state.zone)} shift?"
            "break" -> "Choose your break type. Unpaid breaks are excluded from worked time."
            "cancel" -> "Cancel this scheduled shift for ${item?.userName}? The record will remain in the schedule history."
            "missed" -> "Mark this shift as missed? Use this only after confirming that ${item?.userName} did not work it."
            else -> "End ${if (item?.userId == state.userId) "your" else item?.userName + "’s"} shift now? Any active break will also end."
        }, fontFamily = Inter(), fontSize = 14.sp, color = FormInk)
        if (action == "break") DropdownBox(Icons.Outlined.Coffee, type, listOf("Unpaid break", "Meal (unpaid)", "Rest (unpaid)") + if (state.canManage) listOf("Paid break") else emptyList()) { type = it }
        if (needsReason) FormField("Reason", required = true) { NoteBox(notes, "Explain this change") { notes = it.take(500) } }
        state.error?.let { ErrorText(it) }
        ShiftButton(if (state.busy) "Saving…" else title, Icons.Outlined.Check, enabled = !state.busy && (!needsReason || notes.isNotBlank())) {
            onConfirm(notes.trim().ifBlank { null }, when (type) { "Paid break" -> "PAID_BREAK"; "Meal (unpaid)" -> "MEAL"; "Rest (unpaid)" -> "REST"; else -> "UNPAID_BREAK" })
        }
    }
}

internal fun parseShiftTime(date: LocalDate, value: String, zone: TimeZone): Instant? = runCatching {
    require(Regex("\\d{2}:\\d{2}").matches(value))
    val local = LocalDateTime(date, LocalTime.parse(value))
    local.toInstant(zone).also { require(it.toLocalDateTime(zone) == local) }
}.getOrNull()
internal fun shiftDuration(minutes: Long): String = "${minutes / 60}h ${minutes % 60}m"
// Clocking in opens this long before a scheduled shift (Admin Hub → Settings → Shifts, 2 hours by default).
internal fun canClockIn(item: ShiftItem, now: Instant, earlyMinutes: Int = 120): Boolean = item.status == "SCHEDULED" && item.scheduledStart != null && item.scheduledEnd != null && (Instant.parse(item.scheduledStart) - now).inWholeSeconds <= earlyMinutes * 60L && now <= Instant.parse(item.scheduledEnd)
private fun String?.shiftLocal(zone: TimeZone): LocalDateTime? = this?.let { runCatching { Instant.parse(it).toLocalDateTime(zone) }.getOrNull() }
private fun String?.shiftTime(zone: TimeZone): String = shiftLocal(zone)?.time?.toString()?.take(5) ?: "—"
private fun String?.shiftLabel(zone: TimeZone): String = shiftLocal(zone)?.let { "${it.date.shiftDateLabel()} · ${it.time.toString().take(5)}" } ?: "—"
private fun LocalDate.shiftDateLabel(): String = "${dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}, $day ${month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
private fun shiftStatus(status: String) = when (status) { "OPEN" -> "On duty"; "ON_BREAK" -> "On break"; "CLOSED" -> "Completed"; "SCHEDULED" -> "Scheduled"; "MISSED" -> "Missed"; "CANCELLED" -> "Cancelled"; else -> "Off duty" }
private fun shiftColor(status: String) = when (status) { "OPEN", "CLOSED" -> FormGreen; "SCHEDULED" -> ShiftBlue; "ON_BREAK" -> ShiftAmber; "MISSED", "CANCELLED" -> FormDanger; else -> FormMuted }
