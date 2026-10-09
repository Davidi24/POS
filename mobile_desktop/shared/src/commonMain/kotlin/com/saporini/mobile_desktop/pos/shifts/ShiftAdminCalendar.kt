@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.PlatformVerticalScrollbar
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import com.saporini.mobile_desktop.pos.reservations.CompactDatePicker
import com.saporini.mobile_desktop.pos.reservations.HeaderButton
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import com.saporini.mobile_desktop.pos.reservations.ToolbarHeight
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.overview_next_hours
import mobile_desktop.shared.generated.resources.overview_reservations
import kotlin.time.Clock
import kotlin.time.Instant

// Admin Hub → Shifts → Schedule. Three ways to look at the team's shifts:
// Team = people down the left, the week's days across (a + in every cell adds that person on that day);
// Timeline = hours down the left, the weekdays across, shifts drawn at their real times;
// Month = the whole month, a few names per day.
private enum class CalendarView(val label: String) { TEAM("Team"), TIMELINE("Timeline"), MONTH("Month") }

private const val ALL_ROLES = "All roles"
private const val ALL_STATUSES = "All statuses"
private const val NEEDS_REVIEW = "Needs review"
private val StatusChoices = listOf(ALL_STATUSES, "Planned", "On duty", "On break", "Worked", NEEDS_REVIEW, "Missed", "Cancelled")

@Composable
internal fun ShiftAdminCalendar(state: ShiftState, model: ShiftScreenModel, modifier: Modifier = Modifier, titleExtra: (@Composable () -> Unit)? = null) {
    var view by remember { mutableStateOf(CalendarView.TEAM) }
    var query by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf(ALL_ROLES) }
    var statusFilter by remember { mutableStateOf(ALL_STATUSES) }
    var dateMenu by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<ShiftItem?>(null) }
    var editorItem by remember { mutableStateOf<ShiftItem?>(null) }
    var editorDate by remember { mutableStateOf(state.date) }
    var editorUser by remember { mutableStateOf<String?>(null) }
    var editorStart by remember { mutableStateOf("09:00") }
    var editorOpen by remember { mutableStateOf(false) }
    var correcting by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, ShiftItem?>?>(null) }
    // The "on duty" blocks and the now line move on by themselves.
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = Clock.System.now() } }

    val board = state.board
    val zone = state.zone
    val today = now.toLocalDateTime(zone).date
    val anchor = state.date
    val monthly = view == CalendarView.MONTH
    val weekStart = anchor.minus(DatePeriod(days = anchor.dayOfWeek.isoDayNumber - 1))
    val weekDays = (0..6).map { weekStart.plus(DatePeriod(days = it)) }
    val monthFirst = LocalDate(anchor.year, anchor.month, 1)
    val monthGridStart = monthFirst.minus(DatePeriod(days = monthFirst.dayOfWeek.isoDayNumber - 1))
    val monthDays = (0..41).map { monthGridStart.plus(DatePeriod(days = it)) }.let { days ->
        // Only as many weeks as the month needs.
        val lastWeek = days.indexOfLast { it.month == anchor.month } / 7
        days.take((lastWeek + 1) * 7)
    }
    val periodDays = if (monthly) monthDays.filter { it.month == anchor.month } else weekDays

    val all = board?.items.orEmpty()
    val knownStaff = (board?.staff.orEmpty() + all.map { ShiftStaff(it.userId, it.userName) }).distinctBy { it.id }
    val roles = listOf(ALL_ROLES) + knownStaff.flatMap { it.roles }.distinct().sorted()
    val people = knownStaff
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .filter { roleFilter == ALL_ROLES || roleFilter in it.roles }
        .sortedBy { it.name.lowercase() }
    val peopleIds = people.mapTo(HashSet()) { it.id }
    fun needsReview(item: ShiftItem) = item.status == "SCHEDULED" && item.scheduledEnd?.let { runCatching { Instant.parse(it) < now }.getOrDefault(false) } == true
    val shown = all.filter { item ->
        item.userId in peopleIds && when (statusFilter) {
            ALL_STATUSES -> item.status != "CANCELLED"
            "Planned" -> item.status == "SCHEDULED" && !needsReview(item)
            "On duty" -> item.status == "OPEN"
            "On break" -> item.status == "ON_BREAK"
            "Worked" -> item.status == "CLOSED"
            NEEDS_REVIEW -> needsReview(item)
            "Missed" -> item.status == "MISSED"
            else -> item.status == "CANCELLED"
        }
    }
    val inPeriod = all.filter { it.status != "CANCELLED" && it.start.localDate(zone) in periodDays }

    fun openEditor(item: ShiftItem? = null, date: LocalDate = anchor, userId: String? = null, correction: Boolean = false, time: String = "09:00") {
        model.clearError(); selectedItem = null; editorItem = item; editorDate = item?.start.localDate(zone) ?: date
        editorUser = item?.userId ?: userId; editorStart = time; correcting = correction; editorOpen = true
    }
    fun go(date: LocalDate, asMonth: Boolean = monthly) = model.date(date, asMonth)
    val desktop = !isPhoneWindow()
    LaunchedEffect(board?.timezone) { RestaurantTime.use(board?.timezone) }
    // The "Add shift" tab on the right edge (like "Add table"): it can be dragged up and down.
    var addButtonPosition by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(.84f) }
    val pad = if (desktop) 24.dp else 14.dp

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = if (desktop) 16.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // One toolbar: title, Schedule / Hours & pay, the view, the week, then search and filters.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 1200.dp
                Row(
                    Modifier.fillMaxWidth().then(if (wide) Modifier else Modifier.horizontalScroll(rememberScrollState())),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Shifts", Modifier.padding(end = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.sp, color = Kit.Ink)
                    titleExtra?.invoke()
                    HeaderDropdown(view.label, when (view) { CalendarView.TEAM -> Icons.Outlined.Groups; CalendarView.TIMELINE -> Icons.Outlined.ViewWeek; CalendarView.MONTH -> Icons.Outlined.CalendarMonth },
                        Modifier.width(118.dp), CalendarView.entries.map { it.label }, { label ->
                            val next = CalendarView.entries.first { it.label == label }
                            if (next != view) { view = next; go(anchor, next == CalendarView.MONTH) }
                        })
                    SquareButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, if (monthly) "Previous month" else "Previous week") {
                        go(if (monthly) anchor.minus(DatePeriod(months = 1)) else anchor.minus(DatePeriod(days = 7)))
                    }
                    Box {
                        HeaderButton(
                            if (monthly) "${anchor.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${anchor.year}" else "${weekDays.first().shortDay()} – ${weekDays.last().shortDay()}",
                            Icons.Outlined.CalendarToday, { dateMenu = true }, Modifier.width(170.dp)
                        )
                        DropdownMenu(dateMenu, { dateMenu = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
                            CompactDatePicker(anchor) { go(it); dateMenu = false }
                        }
                    }
                    SquareButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, if (monthly) "Next month" else "Next week") {
                        go(if (monthly) anchor.plus(DatePeriod(months = 1)) else anchor.plus(DatePeriod(days = 7)))
                    }
                    if (today !in periodDays) SquareButton(Icons.Outlined.Today, if (monthly) "This month" else "This week") { go(today) }
                    if (wide) Spacer(Modifier.weight(1f)) else Spacer(Modifier.width(8.dp))
                    SearchField(query, { query = it }, Modifier.width(160.dp), placeholder = "Search staff", height = ToolbarHeight)
                    HeaderDropdown(roleFilter, Icons.Outlined.Badge, Modifier.width(128.dp), roles, { roleFilter = it })
                    HeaderDropdown(statusFilter, Icons.Outlined.FilterList, Modifier.width(140.dp), StatusChoices, { statusFilter = it })
                }
            }

            if (state.error != null && !editorOpen && pending == null) ShiftMessage(state.error, true) { model.clearError() }
            if (state.notice != null) ShiftMessage(state.notice, false) { model.clearError() }
            LaunchedEffect(state.notice) { if (state.notice != null) { delay(3_000); model.clearError() } }

            when {
                !state.ready || !state.canRead -> EmptyCard("No access to shifts", "Your account needs a restaurant, a branch and “View Shifts”.", Icons.Outlined.Lock)
                board == null && state.loading -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), color = Kit.Green, strokeWidth = 3.dp) }
                board == null -> EmptyCard("Couldn't load the schedule", "Check the connection, then try again.", Icons.Outlined.CloudOff) {
                    SecondaryAction("Try again", Icons.Outlined.Refresh) { model.clearError(); model.refresh() }
                }
                else -> {
                    val planned = inPeriod.filter { it.status != "MISSED" }.sumOf { it.plannedMinutes() }
                    val worked = inPeriod.sumOf { it.workedMinutes }
                    val onDuty = all.count { it.status == "OPEN" }
                    val onBreak = all.count { it.status == "ON_BREAK" }
                    val review = all.count { needsReview(it) }
                    val periodName = if (monthly) "this month" else "this week"
                    val tiles = listOf<@Composable (Modifier) -> Unit>(
                        { m -> com.saporini.mobile_desktop.core.components.CompactStat("On duty now", "${onDuty + onBreak}", Kit.Green, m,
                            detail = if (onBreak > 0) "$onBreak on a break" else null, icon = Icons.Outlined.PlayCircle) },
                        { m -> com.saporini.mobile_desktop.core.components.CompactStat("Planned $periodName", hoursText(planned), Kit.Blue, m,
                            detail = "${inPeriod.size} shifts", icon = Icons.Outlined.EventNote) },
                        { m -> com.saporini.mobile_desktop.core.components.CompactStat("Worked $periodName", hoursText(worked), Kit.Amber, m,
                            detail = if (planned > 0) "${(worked * 100 / planned).coerceAtMost(999)}% of planned" else null, icon = Icons.Outlined.Schedule) },
                        { m -> com.saporini.mobile_desktop.core.components.CompactStat(NEEDS_REVIEW, "$review", if (review > 0) Kit.Danger else Kit.Grey, m,
                            detail = if (review == 0) "all accounted for" else "no clock-in", icon = Icons.Outlined.ReportProblem,
                            valueColor = if (review > 0) Kit.Danger else Kit.Ink, onClick = if (review > 0) ({ statusFilter = NEEDS_REVIEW }) else null) }
                    )
                    if (desktop) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { tiles.forEach { it(Modifier.weight(1f)) } }
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tiles[0](Modifier.weight(1f)); tiles[1](Modifier.weight(1f)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tiles[2](Modifier.weight(1f)); tiles[3](Modifier.weight(1f)) }
                    }
                    val canAdd = state.canManage && !state.busy
                    Surface(Modifier.fillMaxWidth().weight(1f).heightIn(min = 320.dp), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                        when (view) {
                            CalendarView.TEAM -> TeamWeek(weekDays, people, shown, zone, today, now, canAdd,
                                onOpen = { selectedItem = it },
                                onAdd = { person, date -> openEditor(date = date, userId = person) })
                            CalendarView.TIMELINE -> TimelineWeek(weekDays, shown, knownStaff, zone, today, now, canAdd,
                                onOpen = { selectedItem = it },
                                onAdd = { date, hour -> openEditor(date = date, time = "${hour.toString().padStart(2, '0')}:00") })
                            CalendarView.MONTH -> MonthGrid(anchor, monthDays, shown, zone, today, now, canAdd,
                                onOpen = { selectedItem = it },
                                onShowWeek = { date -> view = CalendarView.TEAM; go(date, false) },
                                onAdd = { date -> openEditor(date = date) })
                        }
                    }
                }
            }
        }

        if (state.canManage && board != null) {
            val maxOffset = with(LocalDensity.current) { (maxHeight - com.saporini.mobile_desktop.core.components.RightEdgeActionButtonHeight).coerceAtLeast(0.dp).toPx() }
            com.saporini.mobile_desktop.core.components.RightEdgeActionButton(
                text = "Add shift",
                onClick = { openEditor() },
                enabled = !state.busy,
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { androidx.compose.ui.unit.IntOffset(0, (addButtonPosition * maxOffset).toInt()) }
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            if (maxOffset > 0f) addButtonPosition = (addButtonPosition + delta / maxOffset).coerceIn(0f, 1f)
                        }
                    )
            )
        }
    }

    selectedItem?.let { original ->
        val item = board?.items?.find { it.id == original.id } ?: original
        ShiftDetails(item, zone, state, now, onDismiss = { selectedItem = null },
            onEdit = { selectedItem = null; openEditor(item) },
            onCorrect = { selectedItem = null; openEditor(item, correction = true) },
            onAction = { selectedItem = null; pending = it to item })
    }
    if (editorOpen) ShiftEditor(state, editorItem, correcting, editorDate, editorUser, editorStart,
        onDismiss = { if (!state.busy) editorOpen = false },
        onSave = { request -> model.schedule(editorItem?.id, request) { editorOpen = false } },
        onCorrect = { item, request -> model.correct(item, request) { editorOpen = false } })
    pending?.let { (action, original) ->
        val item = original?.let { source -> board?.items?.find { it.id == source.id } ?: board?.current?.takeIf { it.id == source.id } ?: source }
        ShiftActionDialog(action, item, state, onDismiss = { if (!state.busy) pending = null }) { reason, breakType ->
            when (action) {
                "clock-in" -> model.clockIn(item?.id) { pending = null }
                "break" -> item?.let { model.startBreak(it, breakType) { pending = null } }
                else -> item?.let { model.action(it, action, reason) { pending = null } }
            }
        }
    }
}

// ---------- Team: people × days ----------

private val NameColumn = 190.dp
private val DayMinWidth = 116.dp

@Composable
private fun TeamWeek(
    days: List<LocalDate>, people: List<ShiftStaff>, items: List<ShiftItem>, zone: TimeZone, today: LocalDate, now: Instant, canAdd: Boolean,
    onOpen: (ShiftItem) -> Unit, onAdd: (String?, LocalDate) -> Unit
) {
    val byPerson = items.groupBy { it.userId }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxOf(maxWidth, NameColumn + DayMinWidth * 7)
        val horizontal = rememberScrollState()
        Box(Modifier.fillMaxSize().horizontalScroll(horizontal)) {
            Column(Modifier.width(width).fillMaxHeight()) {
                // Day headers: weekday, date (today in a green circle) and how many people and hours that day.
                Row(Modifier.fillMaxWidth().height(50.dp).background(Kit.Canvas)) {
                    Row(Modifier.width(NameColumn).fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Groups, null, Modifier.size(16.dp), tint = Kit.Ink)
                        Spacer(Modifier.width(7.dp))
                        Text("Team", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                        Spacer(Modifier.width(8.dp))
                        Text("${people.size}", Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 8.dp, vertical = 1.dp),
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
                    }
                    days.forEach { day ->
                        val dayItems = items.filter { it.start.localDate(zone) == day }
                        DayHeader(Modifier.weight(1f).fillMaxHeight(), day, today, dayItems.map { it.userId }.distinct().size, dayItems.sumOf { it.plannedMinutes().coerceAtLeast(it.workedMinutes) })
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
                val list = rememberLazyListState()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                        items(people, key = { it.id }) { person ->
                            PersonWeekRow(person, days, byPerson[person.id].orEmpty(), zone, today, now, canAdd, onOpen, onAdd)
                        }
                        if (canAdd) item(key = "add-someone") { AddSomeoneRow(days) { onAdd(null, it) } }
                        if (people.isEmpty()) item(key = "nobody") {
                            Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.PersonSearch, null, Modifier.size(32.dp), tint = Kit.Muted)
                                Text("Nobody to show", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
                                Text("Try another search or role.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                            }
                        }
                    }
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                        PlatformVerticalScrollbar(state = list, modifier = Modifier.fillMaxHeight().width(3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(modifier: Modifier, day: LocalDate, today: LocalDate, people: Int, minutes: Long) {
    val isToday = day == today
    val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
    Row(
        modifier.background(if (isToday) Kit.GreenSoft else if (weekend) Color(0xFFF3F5F2) else Color.Transparent)
            .drawBehind { drawLine(Kit.Border, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), 1f) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (isToday) Kit.Green else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text("${day.day}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isToday) Color.White else Kit.Ink)
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(day.weekdayShort().uppercase(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.5.sp, color = if (isToday) Kit.Green else Kit.Muted)
            Text(if (people == 0) "Nobody yet" else "$people · ${hoursText(minutes)}",
                fontFamily = Inter(), fontSize = 9.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val AvatarBackgrounds = listOf(Color(0xFFE4F1E8), Color(0xFFE6EEFB), Color(0xFFFFEBD3), Color(0xFFFBE4EA), Color(0xFFEEE7FB), Color(0xFFE0F2F4))
private val AvatarInks = listOf(Color(0xFF287247), Color(0xFF3C61B5), Color(0xFFB36412), Color(0xFFB34C65), Color(0xFF7350B6), Color(0xFF1F7A83))

@Composable
private fun PersonAvatar(name: String, size: Dp = 36.dp) {
    val index = (name.hashCode().toLong().and(0x7fffffff) % AvatarBackgrounds.size).toInt()
    Box(Modifier.size(size).clip(CircleShape).background(AvatarBackgrounds[index]), contentAlignment = Alignment.Center) {
        Text(name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifBlank { "?" },
            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp, color = AvatarInks[index])
    }
}

@Composable
private fun PersonWeekRow(
    person: ShiftStaff, days: List<LocalDate>, shifts: List<ShiftItem>, zone: TimeZone, today: LocalDate, now: Instant, canAdd: Boolean,
    onOpen: (ShiftItem) -> Unit, onAdd: (String?, LocalDate) -> Unit
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val weekShifts = shifts.filter { it.start.localDate(zone) in days }
    val weekMinutes = weekShifts.sumOf { if (it.status == "CLOSED") it.workedMinutes else it.plannedMinutes() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).height(IntrinsicSize.Min).hoverable(hover)
            .background(if (hovered) Color(0xFFFAFBF9) else Color.White)
            .drawBehind { drawLine(Kit.Border, androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f), androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f), 1f) }
    ) {
        Row(Modifier.width(NameColumn).fillMaxHeight().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(person.name, 28.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(person.name.ifBlank { "Staff member" }, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(person.roles.firstOrNull() ?: "Team member", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (weekMinutes > 0) {
                Text(hoursText(weekMinutes), Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(horizontal = 6.dp, vertical = 2.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Ink)
            }
        }
        days.forEach { day ->
            val dayShifts = shifts.filter { it.start.localDate(zone) == day }.sortedBy { it.start }
            DayCell(Modifier.weight(1f).fillMaxHeight(), day, today, dayShifts, zone, now, canAdd, onOpen) { onAdd(person.id, day) }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier, day: LocalDate, today: LocalDate, shifts: List<ShiftItem>, zone: TimeZone, now: Instant, canAdd: Boolean,
    onOpen: (ShiftItem) -> Unit, onAdd: () -> Unit
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val touch = isPhoneWindow()
    val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
    Column(
        modifier.hoverable(hover)
            .background(if (day == today) Kit.GreenSoft.copy(alpha = 0.55f) else if (weekend) Color(0xFFF9FAF8) else Color.Transparent)
            .drawBehind { drawLine(Kit.Border, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), 1f) }
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        shifts.forEach { item -> ShiftBlock(item, zone, now) { onOpen(item) } }
        if (canAdd) {
            val show = hovered || touch || shifts.isEmpty()
            AddSlot(
                if (shifts.isEmpty()) Modifier.fillMaxWidth().weight(1f).heightIn(min = 34.dp) else Modifier.fillMaxWidth().height(18.dp),
                visible = show, strong = hovered, compact = shifts.isNotEmpty(), onClick = onAdd
            )
        }
    }
}

// A dashed box with a + : shown faintly in empty cells and clearly on hover.
@Composable
private fun AddSlot(modifier: Modifier, visible: Boolean, strong: Boolean, compact: Boolean, onClick: () -> Unit) {
    val alpha = when { !visible -> 0f; strong -> 1f; else -> 0.35f }
    Box(
        modifier.alpha(alpha).clip(RoundedCornerShape(8.dp)).clickable(enabled = visible, onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = Kit.Green.copy(alpha = if (strong) 0.7f else 0.45f), cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                )
            }
            .background(if (strong) Kit.GreenSoft.copy(alpha = 0.6f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(Icons.Outlined.Add, "Add shift", Modifier.size(if (compact) 12.dp else 15.dp), tint = Kit.Green)
            if (strong && !compact) Text("Add", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Green)
        }
    }
}

@Composable
private fun AddSomeoneRow(days: List<LocalDate>, onAdd: (LocalDate) -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(44.dp).hoverable(hover).clickable { onAdd(days.first()) }
            .background(if (hovered) Kit.GreenSoft.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).drawBehind {
                drawCircle(Kit.Green.copy(alpha = 0.6f), style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
            },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.PersonAdd, null, Modifier.size(15.dp), tint = Kit.Green) }
        Text("Put someone on a shift", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
        Text("or use the + in any day", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
    }
}

private data class BlockLook(val background: Color, val border: Color, val bar: Color, val text: Color, val sub: Color, val label: String)

private fun blockLook(item: ShiftItem, now: Instant): BlockLook {
    val ended = item.scheduledEnd?.let { runCatching { Instant.parse(it) < now }.getOrDefault(false) } == true
    return when {
        item.status == "OPEN" -> BlockLook(Kit.Green, Kit.Green, Color.White.copy(alpha = 0.55f), Color.White, Color.White.copy(alpha = 0.88f), "On duty")
        item.status == "ON_BREAK" -> BlockLook(Color(0xFFFFF3DF), Kit.Amber.copy(alpha = 0.45f), Kit.Amber, Kit.Ink, Kit.Amber, "On break")
        item.status == "CLOSED" -> BlockLook(Color(0xFFF2F3F0), Color(0xFFE2E4DF), Kit.Grey.copy(alpha = 0.7f), Kit.Ink, Kit.Muted, "Worked")
        item.status == "MISSED" -> BlockLook(Kit.Danger.copy(alpha = 0.07f), Kit.Danger.copy(alpha = 0.3f), Kit.Danger, Kit.Ink, Kit.Danger, "Missed")
        item.status == "CANCELLED" -> BlockLook(Color.White, Kit.Border, Kit.Border, Kit.Faint, Kit.Faint, "Cancelled")
        item.status == "SCHEDULED" && ended -> BlockLook(Color(0xFFFFFAF1), Kit.Amber.copy(alpha = 0.55f), Kit.Amber, Kit.Ink, Kit.Amber, "No clock-in")
        else -> BlockLook(Color(0xFFEAF3F5), Kit.Blue.copy(alpha = 0.22f), Kit.Blue, Kit.Ink, Kit.Blue, "Planned")
    }
}

@Composable
private fun ShiftBlock(item: ShiftItem, zone: TimeZone, now: Instant, onClick: () -> Unit) {
    val look = blockLook(item, now)
    val minutes = if (item.status == "CLOSED" || item.status == "OPEN" || item.status == "ON_BREAK") item.workedMinutes else item.plannedMinutes()
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = look.background, border = BorderStroke(1.dp, look.border),
        shadowElevation = if (item.status == "OPEN") 2.dp else 0.dp) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(look.bar))
            Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${item.start.clock(zone)} – ${(item.scheduledEnd ?: item.endedAt).clock(zone)}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    color = look.text, maxLines = 1, textDecoration = if (item.status == "CANCELLED") TextDecoration.LineThrough else null)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.status == "OPEN") PulseDot(Color.White)
                    Text("${look.label} · ${hoursText(minutes)}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = look.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                item.notes?.takeIf { it.isNotBlank() && !it.startsWith("Demo shift preview") }?.let {
                    Text(it, fontFamily = Inter(), fontSize = 9.sp, color = if (item.status == "OPEN") Color.White.copy(alpha = 0.8f) else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PulseDot(color: Color) {
    val pulse = rememberInfiniteTransition(label = "on-duty")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "on-duty-alpha")
    Box(Modifier.size(6.dp).alpha(alpha).clip(CircleShape).background(color))
}

// ---------- Timeline: hours × weekdays ----------

private val HourHeight = 42.dp
private val TimeGutter = 48.dp

@Composable
private fun TimelineWeek(
    days: List<LocalDate>, items: List<ShiftItem>, staff: List<ShiftStaff>, zone: TimeZone, today: LocalDate, now: Instant, canAdd: Boolean,
    onOpen: (ShiftItem) -> Unit, onAdd: (LocalDate, Int) -> Unit
) {
    // The hours shown: 06:00 to midnight, stretched to fit any earlier start or a shift that runs past midnight.
    fun startMinute(item: ShiftItem) = item.start?.let { runCatching { Instant.parse(it).toLocalDateTime(zone) }.getOrNull() }?.let { it.hour * 60 + it.minute }
    fun endMinute(item: ShiftItem): Int? {
        val s = item.start?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val e = (item.scheduledEnd ?: item.endedAt)?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: now
        return (startMinute(item) ?: return null) + (e - s).inWholeMinutes.toInt().coerceAtLeast(15)
    }
    val weekItems = items.filter { it.start.localDate(zone) in days }
    val firstHour = minOf(6, weekItems.mapNotNull { startMinute(it) }.minOrNull()?.div(60) ?: 6)
    val lastHour = maxOf(24, weekItems.mapNotNull { endMinute(it) }.maxOrNull()?.let { (it + 59) / 60 } ?: 24).coerceAtMost(30)
    val hours = (firstHour until lastHour).toList()
    val roleOf = staff.associate { it.id to (it.roles.firstOrNull() ?: "") }
    val vertical = rememberScrollState()
    val density = LocalDensity.current
    // Open near the first shift of the week (or 08:00).
    LaunchedEffect(days.first()) {
        val target = ((weekItems.mapNotNull { startMinute(it) }.minOrNull() ?: 8 * 60) / 60 - firstHour - 1).coerceAtLeast(0)
        vertical.scrollTo(with(density) { (HourHeight * target).roundToPx() })
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxOf(maxWidth, TimeGutter + 110.dp * 7)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(width).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().height(50.dp).background(Kit.Canvas)) {
                    Box(Modifier.width(TimeGutter).fillMaxHeight(), Alignment.Center) { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = Kit.Muted) }
                    days.forEach { day ->
                        val dayItems = weekItems.filter { it.start.localDate(zone) == day }
                        DayHeader(Modifier.weight(1f).fillMaxHeight(), day, today, dayItems.map { it.userId }.distinct().size, dayItems.sumOf { it.plannedMinutes().coerceAtLeast(it.workedMinutes) })
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().verticalScroll(vertical)) {
                        Column(Modifier.width(TimeGutter)) {
                            hours.forEach { hour ->
                                Box(Modifier.height(HourHeight).fillMaxWidth().padding(end = 8.dp), contentAlignment = Alignment.TopEnd) {
                                    Text("${(hour % 24).toString().padStart(2, '0')}:00", Modifier.offset(y = (-6).dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp, color = if (hour >= 24) Kit.Faint else Kit.Muted)
                                }
                            }
                        }
                        days.forEach { day ->
                            val dayItems = weekItems.filter { it.start.localDate(zone) == day }.sortedBy { it.start }
                            TimelineDay(Modifier.weight(1f), day, today, hours, dayItems, roleOf, zone, now, canAdd, ::startMinute, ::endMinute, onOpen) { hour -> onAdd(day, hour % 24) }
                        }
                    }
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                        PlatformVerticalScrollbar(state = vertical, modifier = Modifier.fillMaxHeight().width(3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineDay(
    modifier: Modifier, day: LocalDate, today: LocalDate, hours: List<Int>, items: List<ShiftItem>, roleOf: Map<String, String>, zone: TimeZone, now: Instant,
    canAdd: Boolean, startMinute: (ShiftItem) -> Int?, endMinute: (ShiftItem) -> Int?, onOpen: (ShiftItem) -> Unit, onAdd: (Int) -> Unit
) {
    val firstHour = hours.first()
    val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
    // Shifts that overlap sit side by side.
    val lanes = mutableListOf<Int>()
    val laneOf = items.associate { item ->
        val s = startMinute(item) ?: 0; val e = endMinute(item) ?: s
        val lane = lanes.indexOfFirst { it <= s }.let { if (it == -1) { lanes.add(e); lanes.size - 1 } else { lanes[it] = e; it } }
        item.id to lane
    }
    val laneCount = lanes.size.coerceAtLeast(1)
    BoxWithConstraints(
        modifier.height(HourHeight * hours.size)
            .background(if (day == today) Kit.GreenSoft.copy(alpha = 0.45f) else if (weekend) Color(0xFFF9FAF8) else Color.Transparent)
            .drawBehind {
                drawLine(Kit.Border, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), 1f)
                val step = HourHeight.toPx()
                hours.indices.forEach { i -> drawLine(Kit.Border.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(0f, i * step), androidx.compose.ui.geometry.Offset(size.width, i * step), 1f) }
            }
    ) {
        val columnWidth = maxWidth
        // Empty hours: click to add a shift starting then.
        Column(Modifier.fillMaxSize()) {
            hours.forEach { hour -> HourSlot(Modifier.fillMaxWidth().height(HourHeight), hour, canAdd) { onAdd(hour) } }
        }
        items.forEach { item ->
            val s = startMinute(item) ?: return@forEach
            val e = endMinute(item) ?: return@forEach
            val lane = laneOf[item.id] ?: 0
            val top = HourHeight * ((s - firstHour * 60) / 60f)
            val height = (HourHeight * ((e - s) / 60f)).coerceAtLeast(26.dp)
            val laneWidth = columnWidth / laneCount
            TimelineBlock(
                Modifier.offset(x = laneWidth * lane + 3.dp, y = top + 2.dp).width(laneWidth - 6.dp).height(height - 4.dp),
                item, roleOf[item.userId].orEmpty(), zone, now
            ) { onOpen(item) }
        }
        if (day == today) {
            val local = now.toLocalDateTime(zone)
            val minute = local.hour * 60 + local.minute
            if (minute >= firstHour * 60) {
                val y = HourHeight * ((minute - firstHour * 60) / 60f)
                Box(Modifier.offset(y = y - 4.dp).fillMaxWidth().height(8.dp)) {
                    Box(Modifier.align(Alignment.CenterStart).size(8.dp).clip(CircleShape).background(Kit.Danger))
                    Box(Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(Kit.Danger))
                }
            }
        }
    }
}

@Composable
private fun HourSlot(modifier: Modifier, hour: Int, canAdd: Boolean, onAdd: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        modifier.hoverable(hover).then(if (canAdd) Modifier.clickable(onClick = onAdd) else Modifier)
            .background(if (hovered && canAdd) Kit.GreenSoft.copy(alpha = 0.7f) else Color.Transparent),
        contentAlignment = Alignment.CenterStart
    ) {
        if (hovered && canAdd) {
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(14.dp), tint = Kit.Green)
                Text("${(hour % 24).toString().padStart(2, '0')}:00", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Green)
            }
        }
    }
}

@Composable
private fun TimelineBlock(modifier: Modifier, item: ShiftItem, role: String, zone: TimeZone, now: Instant, onClick: () -> Unit) {
    val look = blockLook(item, now)
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(8.dp), color = look.background, border = BorderStroke(1.dp, look.border),
        shadowElevation = if (item.status == "OPEN") 2.dp else 0.dp) {
        Row {
            Box(Modifier.width(3.dp).fillMaxHeight().background(look.bar))
            Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.status == "OPEN") PulseDot(Color.White)
                    Text(item.userName, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = look.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${item.start.clock(zone)} – ${(item.scheduledEnd ?: item.endedAt).clock(zone)}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 9.sp,
                    color = look.sub, maxLines = 1)
                if (role.isNotBlank()) Text(role, fontFamily = Inter(), fontSize = 9.sp, color = if (item.status == "OPEN") Color.White.copy(alpha = 0.8f) else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------- Month ----------

@Composable
private fun MonthGrid(
    anchor: LocalDate, days: List<LocalDate>, items: List<ShiftItem>, zone: TimeZone, today: LocalDate, now: Instant, canAdd: Boolean,
    onOpen: (ShiftItem) -> Unit, onShowWeek: (LocalDate) -> Unit, onAdd: (LocalDate) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(32.dp).background(Kit.Canvas), verticalAlignment = Alignment.CenterVertically) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                Text(it.uppercase(), Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.6.sp, color = Kit.Muted)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val weeks = days.chunked(7)
            val rowHeight = maxOf(maxHeight / weeks.size, 92.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth().height(rowHeight)) {
                        week.forEach { day ->
                            MonthDay(Modifier.weight(1f).fillMaxHeight(), day, anchor, today, items.filter { it.start.localDate(zone) == day }.sortedBy { it.start },
                                zone, now, canAdd, onOpen, { onShowWeek(day) }) { onAdd(day) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDay(
    modifier: Modifier, day: LocalDate, anchor: LocalDate, today: LocalDate, shifts: List<ShiftItem>, zone: TimeZone, now: Instant, canAdd: Boolean,
    onOpen: (ShiftItem) -> Unit, onShowWeek: () -> Unit, onAdd: () -> Unit
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val inMonth = day.month == anchor.month
    Column(
        modifier.hoverable(hover)
            .background(when { day == today -> Kit.GreenSoft.copy(alpha = 0.55f); !inMonth -> Color(0xFFFAFAF9); else -> Color.White })
            .drawBehind {
                drawLine(Kit.Border, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), 1f)
                drawLine(Kit.Border, androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f), androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f), 1f)
            }
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (day == today) Kit.Green else Color.Transparent), Alignment.Center) {
                Text("${day.day}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    color = when { day == today -> Color.White; inMonth -> Kit.Ink; else -> Kit.Faint })
            }
            Spacer(Modifier.weight(1f))
            if (canAdd && (hovered || isPhoneWindow())) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Kit.GreenSoft).clickable(onClick = onAdd), Alignment.Center) {
                    Icon(Icons.Outlined.Add, "Add shift", Modifier.size(15.dp), tint = Kit.Green)
                }
            } else if (shifts.isNotEmpty()) {
                Text("${shifts.size}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Muted)
            }
        }
        shifts.take(3).forEach { item ->
            val look = blockLook(item, now)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(look.background).clickable { onOpen(item) }.padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(look.bar))
                Text("${item.start.clock(zone)} ${item.userName.split(' ').first()}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                    color = look.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (shifts.size > 3) {
            Text("+${shifts.size - 3} more", Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onShowWeek).padding(horizontal = 4.dp, vertical = 1.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Green)
        }
    }
}

// ---------- Small pieces ----------

@Composable
private fun SecondaryAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.height(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(17.dp), tint = Kit.Ink)
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
        }
    }
}

@Composable
private fun ColumnScope.EmptyCard(title: String, hint: String, icon: ImageVector, action: (@Composable () -> Unit)? = null) {
    Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) {
            com.saporini.mobile_desktop.core.components.OverviewEmpty(title, hint, icon, action = action)
        }
    }
}
