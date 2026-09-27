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

@Composable
internal fun ShiftContent(state: ShiftState, management: Boolean, model: ShiftScreenModel, modifier: Modifier = Modifier) {
    if (management) { ShiftAdminCalendar(state, model, modifier); return }
    var details by remember { mutableStateOf<ShiftItem?>(null) }
    var editor by remember { mutableStateOf<ShiftItem?>(null) }
    var editing by remember { mutableStateOf(false) }
    var correction by remember { mutableStateOf(false) }
    var editorDate by remember { mutableStateOf(state.date) }
    var editorStaff by remember { mutableStateOf<String?>(null) }
    var editorStartTime by remember { mutableStateOf("09:00") }
    var pending by remember { mutableStateOf<Pair<String, ShiftItem?>?>(null) }
    var staffId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("All statuses") }
    val board = state.board
    val zone = state.zone
    val now = board?.serverNow?.let { Instant.parse(it) } ?: Clock.System.now()
    val today = now.toLocalDateTime(zone).date
    val all = board?.items.orEmpty()
    val visible = all.filter { (staffId == null || it.userId == staffId) && (status == "All statuses" || shiftStatus(it.status) == status) }
    val current = board?.current
    val days = (0..6).map { state.weekStart.plus(DatePeriod(days = it)) }
    val weekItems = all.filter { it.start?.shiftLocal(zone)?.date in days }
    val dayItems = visible.filter { it.start?.shiftLocal(zone)?.date == state.date }.sortedBy { it.start }
    fun openEditor(item: ShiftItem? = null, correct: Boolean = false) { model.clearError(); editor = item; correction = correct; editorDate = item?.start?.shiftLocal(zone)?.date ?: state.date; editorStaff = item?.userId; editorStartTime = "09:00"; editing = true; details = null }
    fun ask(action: String, item: ShiftItem? = null) { model.clearError(); pending = action to item; details = null }
    val next = all.filter { it.status == "SCHEDULED" && it.scheduledEnd?.let { end -> Instant.parse(end) >= now } == true }.minByOrNull { it.scheduledStart.orEmpty() }
    val eligible = board?.clockInShift ?: next?.takeIf { canClockIn(it, now) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val compact = maxWidth < 900.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).widthIn(min = 190.dp)) {
                    Text(if (management) "Shifts" else "My shift", fontFamily = Inter(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FormInk)
                    Text(if (management) "Plan your team’s week and review attendance." else "Your time, breaks, and upcoming schedule.", fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
                }
                ShiftButton("Refresh", Icons.Outlined.Refresh, primary = false, enabled = !state.loading && !state.busy) { model.clearError(); model.refresh() }
                if (management && state.canManage) ShiftButton("Schedule shift", Icons.Outlined.Add, enabled = !state.busy && board != null) { openEditor() }
            }
            if (state.error != null && !editing && pending == null) ShiftMessage(state.error, error = true, onDismiss = model::clearError)
            if (state.notice != null) ShiftMessage(state.notice, error = false, onDismiss = model::clearError)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = FormGreen, trackColor = FormGreenSoft)
            val permitted = if (management) state.canRead else state.canSelf
            if (!state.ready || !permitted) {
                ShiftPanel("Shift access", Icons.Outlined.Lock) { Muted("Your account needs a restaurant, branch, and shift permission. Sign in again after permissions are updated.") }
            } else if (board == null && !state.loading) {
                ShiftPanel("Couldn't load shifts", Icons.Outlined.CloudOff) { Muted("Your shifts could not be loaded. Check your connection and retry."); ShiftButton("Try again", Icons.Outlined.Refresh) { model.clearError(); model.refresh() } }
            } else {
                if (management) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShiftMetric("Scheduled", "${all.count { it.status == "SCHEDULED" }}", "This week", Icons.Outlined.CalendarToday, FormGreen, Modifier.weight(1f).widthIn(min = 155.dp))
                        ShiftMetric("On duty", "${all.count { it.status == "OPEN" }}", "Clocked in", Icons.Outlined.Badge, ShiftBlue, Modifier.weight(1f).widthIn(min = 155.dp))
                        ShiftMetric("On break", "${all.count { it.status == "ON_BREAK" }}", "Currently away", Icons.Outlined.Coffee, ShiftAmber, Modifier.weight(1f).widthIn(min = 155.dp))
                        ShiftMetric("Worked time", shiftDuration(weekItems.sumOf { it.workedMinutes }), "Shifts in this week", Icons.Outlined.Schedule, FormGreen, Modifier.weight(1f).widthIn(min = 155.dp))
                    }
                } else {
                    ShiftPanel(if (current == null) "Ready for your next shift" else "Current shift", Icons.Outlined.Schedule) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f).widthIn(min = 200.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ShiftBadge(current?.status ?: "OFF_DUTY")
                                Text(if (current == null) "You're off duty" else shiftDuration(current.workedMinutes), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 34.sp, color = FormInk)
                                Muted(if (current == null) next?.let { "Next: ${it.scheduledStart.shiftLabel(zone)} – ${it.scheduledEnd.shiftTime(zone)}" } ?: "No upcoming shift in the selected week." else "Worked time · started ${current.startedAt.shiftLabel(zone)}")
                                if (current != null) Muted("Breaks: ${shiftDuration(current.breakMinutes)} · ${current.breaks.size} recorded")
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (current == null) {
                                    ShiftButton("Clock in", Icons.Outlined.PlayArrow, enabled = !state.busy && board != null) { ask("clock-in", eligible) }
                                    Muted(if (eligible == null) "Start an unscheduled shift now." else "For your ${eligible.scheduledStart.shiftTime(zone)} shift.")
                                } else {
                                    if (current.status == "OPEN") ShiftButton("Start break", Icons.Outlined.Coffee, enabled = !state.busy) { ask("break", current) }
                                    else ShiftButton("End break", Icons.Outlined.PlayArrow, enabled = !state.busy) { model.action(current, "resume", null) {} }
                                    ShiftButton("Clock out", Icons.Outlined.StopCircle, primary = false, enabled = !state.busy) { ask("close", current) }
                                    ShiftButton("Shift details", Icons.Outlined.Info, primary = false) { details = current }
                                }
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShiftButton("Previous", Icons.AutoMirrored.Outlined.ArrowBack, primary = false, enabled = !state.busy) { model.date(state.date.minus(DatePeriod(days = 7))) }
                    Box(Modifier.width(210.dp)) { DateBox(state.date) { if (!state.busy) model.date(it) } }
                    ShiftButton("Next", Icons.AutoMirrored.Outlined.ArrowForward, primary = false, enabled = !state.busy) { model.date(state.date.plus(DatePeriod(days = 7))) }
                    ShiftButton("Today", Icons.Outlined.Today, primary = false, enabled = !state.busy) { model.date(today) }
                    if (management) {
                        Box(Modifier.width(200.dp)) {
                            StaffPicker(board?.staff.orEmpty(), staffId, "All staff", allowAll = true) { staffId = it }
                        }
                        Box(Modifier.width(170.dp)) { DropdownBox(Icons.Outlined.FilterList, status, listOf("All statuses", "Scheduled", "On duty", "On break", "Completed", "Missed", "Cancelled")) { status = it } }
                    }
                }
                Text("${state.weekStart.shiftDateLabel()} – ${days.last().shiftDateLabel()} · ${zone.id}", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
                if (management && !compact) {
                    ShiftPanel("Weekly schedule", Icons.Outlined.CalendarMonth) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            days.forEach { date ->
                                val shifts = visible.filter { it.start?.shiftLocal(zone)?.date == date }.sortedBy { it.start }
                                Column(Modifier.weight(1f).heightIn(min = 265.dp).clip(RoundedCornerShape(8.dp)).background(if (date == state.date) FormGreenSoft else Color(0xFFF8F9F7)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { model.date(date) }.padding(vertical = 4.dp)) {
                                        Text(date.dayOfWeek.name.take(3), fontFamily = Inter(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FormMuted)
                                        Text("${date.day}", fontFamily = Inter(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (date == today) FormGreen else FormInk)
                                    }
                                    shifts.take(4).forEach { item ->
                                        Surface(onClick = { details = item }, shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, ShiftBorder), modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                Text(item.userName, fontFamily = Inter(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text("${item.start.shiftTime(zone)}–${(item.scheduledEnd ?: item.endedAt).shiftTime(zone)}", fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
                                                Text(shiftStatus(item.status), fontFamily = Inter(), fontSize = 10.sp, color = shiftColor(item.status))
                                            }
                                        }
                                    }
                                    if (shifts.isEmpty()) Text("No shifts", fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
                                    if (shifts.size > 4) TextButton(onClick = { model.date(date) }) { Text("+${shifts.size - 4} more", color = FormGreen, fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        days.forEach { date ->
                            Surface(onClick = { if (!state.busy) model.date(date) }, shape = RoundedCornerShape(8.dp), color = if (date == state.date) FormGreen else Color.White, border = BorderStroke(1.dp, ShiftBorder)) {
                                Column(Modifier.width(72.dp).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(date.dayOfWeek.name.take(3), fontFamily = Inter(), fontSize = 11.sp, color = if (date == state.date) Color.White else FormMuted)
                                    Text("${date.day}", fontFamily = Inter(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (date == state.date) Color.White else FormInk)
                                    Text("${visible.count { it.start?.shiftLocal(zone)?.date == date }} shifts", fontFamily = Inter(), fontSize = 10.sp, color = if (date == state.date) Color.White else FormMuted)
                                }
                            }
                        }
                    }
                }
                ShiftPanel("${if (management) "Team schedule" else "My schedule"} · ${state.date.shiftDateLabel()}", Icons.Outlined.EventAvailable) {
                    if (dayItems.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.EventAvailable, null, Modifier.size(36.dp), tint = FormGreen)
                            Text(if (state.loading) "Loading shifts…" else "No shifts on this day", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = FormInk)
                            Muted(if (management) "Choose another day or schedule a shift for your team." else "Use the calendar to view your upcoming shifts and history.")
                        }
                    } else dayItems.forEach { item -> ShiftRow(item, zone, compact) { details = item } }
                }
                if (management) {
                    val earlierActive = visible.filter { it.active && it.start?.shiftLocal(zone)?.date !in days }
                    if (earlierActive.isNotEmpty()) ShiftPanel("Active shifts outside this week", Icons.Outlined.Schedule) {
                        Muted("These shifts are still clocked in. Open one to review its times or end it with a reason.")
                        earlierActive.forEach { item -> ShiftRow(item, zone, compact) { details = item } }
                    }
                    val unresolved = all.filter { it.status == "SCHEDULED" && it.scheduledEnd?.let { end -> Instant.parse(end) < now } == true }
                    if (unresolved.isNotEmpty()) ShiftPanel("Needs review · ${unresolved.size}", Icons.Outlined.Info) {
                        Muted("These scheduled shifts ended without a clock-in. Review them before marking them missed.")
                        unresolved.forEach { item -> ShiftRow(item, zone, compact) { details = item } }
                    }
                }
            }
        }
    }
    details?.let { original ->
        val item = board?.items?.find { it.id == original.id } ?: board?.current?.takeIf { it.id == original.id } ?: original
        ShiftDetails(item, zone, state, now, onDismiss = { details = null }, onEdit = { openEditor(item) }, onCorrect = { openEditor(item, true) }, onAction = { ask(it, item) })
    }
    if (editing) ShiftEditor(state, editor, correction, editorDate, editorStaff, editorStartTime, onDismiss = { if (!state.busy) editing = false }, onSave = { request ->
        model.schedule(editor?.id, request) { editing = false }
    }, onCorrect = { item, request -> model.correct(item, request) { editing = false } })
    pending?.let { (action, original) ->
        val item = original?.let { board?.items?.find { s -> s.id == it.id } ?: board?.current?.takeIf { s -> s.id == it.id } ?: it }
        ShiftActionDialog(action, item, state, onDismiss = { if (!state.busy) pending = null }) { notes, breakType ->
            when (action) {
                "clock-in" -> model.clockIn(item?.id) { pending = null }
                "break" -> item?.let { model.startBreak(it, breakType) { pending = null } }
                else -> item?.let { model.action(it, action, notes) { pending = null } }
            }
        }
    }
}

@Composable private fun ShiftMetric(label: String, value: String, detail: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, ShiftBorder)) {
        Row(Modifier.heightIn(min = 90.dp)) {
            Box(Modifier.width(4.dp).height(96.dp).background(color))
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, Modifier.size(30.dp), tint = color)
                Column { Muted(label); Text(value, fontFamily = Inter(), fontSize = 23.sp, fontWeight = FontWeight.Bold, color = FormInk); Text(detail, fontFamily = Inter(), fontSize = 11.sp, color = FormMuted) }
            }
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
@Composable private fun ShiftRow(item: ShiftItem, zone: TimeZone, compact: Boolean, onClick: () -> Unit) {
    Surface(onClick, shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, ShiftBorder), modifier = Modifier.fillMaxWidth()) {
        FlowRow(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(if (compact) 110.dp else 130.dp)) {
                Text(item.start.shiftTime(zone), fontFamily = Inter(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FormInk)
                Text("until ${(item.scheduledEnd ?: item.endedAt).shiftTime(zone)}", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
            }
            Column(Modifier.weight(1f).widthIn(min = 120.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.userName, fontFamily = Inter(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FormInk)
                Muted(if (item.startedAt != null) "${shiftDuration(item.workedMinutes)} worked · ${shiftDuration(item.breakMinutes)} breaks" else item.notes?.takeIf { it.isNotBlank() } ?: "Scheduled shift")
            }
            ShiftBadge(item.status)
            Icon(Icons.Outlined.ChevronRight, "View shift", Modifier.size(20.dp), tint = FormMuted)
        }
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
    ShiftDialog("Shift details", state.busy, onDismiss) {
        Text(item.userName, fontFamily = Inter(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = FormInk)
        ShiftBadge(item.status)
        Muted("Times shown in ${zone.id}")
        if (item.scheduledStart != null) DetailLine("Scheduled", "${item.scheduledStart.shiftLabel(zone)} – ${item.scheduledEnd.shiftLabel(zone)}")
        DetailLine("Clocked in", item.startedAt.shiftLabel(zone))
        DetailLine("Clocked out", item.endedAt.shiftLabel(zone))
        DetailLine("Worked time", shiftDuration(item.workedMinutes))
        DetailLine("Break time", shiftDuration(item.breakMinutes))
        if (!item.notes.isNullOrBlank()) { HorizontalDivider(color=ShiftBorder); Text(item.notes, fontFamily = Inter(), fontSize = 13.sp, color = FormInk) }
        if (item.breaks.isNotEmpty()) {
            Text("Breaks", fontFamily = Inter(), fontWeight = FontWeight.Bold, color = FormInk)
            item.breaks.forEach { br -> DetailLine(if (br.paid) "Paid break" else "${br.type.lowercase().replace('_', ' ')} (unpaid)", "${br.startedAt.shiftTime(zone)} – ${br.endedAt?.shiftTime(zone) ?: "Now"}") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.userId == state.userId && state.canSelf && item.status == "SCHEDULED" && state.board?.current == null && canClockIn(item, now)) ShiftButton("Clock in", Icons.Outlined.PlayArrow, enabled = !state.busy) { onAction("clock-in") }
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
internal fun canClockIn(item: ShiftItem, now: Instant): Boolean = item.status == "SCHEDULED" && item.scheduledStart != null && item.scheduledEnd != null && (Instant.parse(item.scheduledStart) - now).inWholeSeconds <= 7200 && now <= Instant.parse(item.scheduledEnd)
private fun String?.shiftLocal(zone: TimeZone): LocalDateTime? = this?.let { runCatching { Instant.parse(it).toLocalDateTime(zone) }.getOrNull() }
private fun String?.shiftTime(zone: TimeZone): String = shiftLocal(zone)?.time?.toString()?.take(5) ?: "—"
private fun String?.shiftLabel(zone: TimeZone): String = shiftLocal(zone)?.let { "${it.date.shiftDateLabel()} · ${it.time.toString().take(5)}" } ?: "—"
private fun LocalDate.shiftDateLabel(): String = "${dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}, $day ${month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
private fun shiftStatus(status: String) = when (status) { "OPEN" -> "On duty"; "ON_BREAK" -> "On break"; "CLOSED" -> "Completed"; "SCHEDULED" -> "Scheduled"; "MISSED" -> "Missed"; "CANCELLED" -> "Cancelled"; else -> "Off duty" }
private fun shiftColor(status: String) = when (status) { "OPEN", "CLOSED" -> FormGreen; "SCHEDULED" -> ShiftBlue; "ON_BREAK" -> ShiftAmber; "MISSED", "CANCELLED" -> FormDanger; else -> FormMuted }
