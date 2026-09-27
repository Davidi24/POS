@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import com.saporini.mobile_desktop.pos.reservations.*
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Instant

private enum class ShiftCalendarMode { WEEK, MONTH, TIME, LIST }
private val calendarLine = Color(0xFFE0E4DF)
private val calendarSoft = Color(0xFFF6F8F5)
private val dayOff = Color(0xFFF2F4F2)

@Composable
internal fun ShiftAdminCalendar(state: ShiftState, model: ShiftScreenModel, modifier: Modifier = Modifier) {
    var selectedDate by remember(state.userId) { mutableStateOf(state.date) }
    var mode by remember { mutableStateOf(ShiftCalendarMode.WEEK) }
    var staffQuery by remember { mutableStateOf("") }
    var staffFilter by remember { mutableStateOf<String?>(null) }
    var roleFilter by remember { mutableStateOf("All roles") }
    var statusFilter by remember { mutableStateOf("All statuses") }
    var dateMenu by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<ShiftItem?>(null) }
    var editorItem by remember { mutableStateOf<ShiftItem?>(null) }
    var editorDate by remember { mutableStateOf(selectedDate) }
    var editorUser by remember { mutableStateOf<String?>(null) }
    var editorStart by remember { mutableStateOf("09:00") }
    var editorOpen by remember { mutableStateOf(false) }
    var correcting by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, ShiftItem?>?>(null) }
    val board = state.board
    val zone = state.zone
    val now = board?.serverNow?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Clock.System.now()
    val weekdays = (0..6).map { selectedDate.minus(DatePeriod(days = selectedDate.dayOfWeek.isoDayNumber - 1)).plus(DatePeriod(days = it)) }
    val firstMonth = LocalDate(selectedDate.year, selectedDate.month, 1)
    val monthStart = firstMonth.minus(DatePeriod(days = firstMonth.dayOfWeek.isoDayNumber - 1))
    val monthDays = (0..41).map { monthStart.plus(DatePeriod(days = it)) }
    val today = now.toLocalDateTime(zone).date
    val all = board?.items.orEmpty()
    val staff = board?.staff.orEmpty()
    val knownStaff = (staff + all.map { ShiftStaff(it.userId, it.userName) }).distinctBy { it.id }
    val filteredStaff = knownStaff.filter { staffFilter == null || it.id == staffFilter }
        .filter { staffQuery.isBlank() || it.name.contains(staffQuery.trim(), ignoreCase = true) }
        .filter { roleFilter == "All roles" || roleFilter in it.roles }
    fun visible(items: List<ShiftItem>) = items.filter { item ->
        val person = knownStaff.firstOrNull { it.id == item.userId }
        (staffFilter == null || item.userId == staffFilter) &&
            (staffQuery.isBlank() || person?.name?.contains(staffQuery.trim(), ignoreCase = true) == true) &&
            (roleFilter == "All roles" || roleFilter in person?.roles.orEmpty()) &&
            (statusFilter == "All statuses" || calendarStatus(item.status) == statusFilter)
    }
    val dayItems = visible(all).filter { it.start?.calendarDate(zone) == selectedDate }.sortedBy { it.start }
    fun openEditor(item: ShiftItem? = null, date: LocalDate = selectedDate, userId: String? = null, correction: Boolean = false, time: String = "09:00") {
        model.clearError(); selectedItem = null; editorItem = item; editorDate = item?.start?.calendarDate(zone) ?: date
        editorUser = item?.userId ?: userId; editorStart = time; correcting = correction; editorOpen = true
    }
    fun openItem(item: ShiftItem) {
        item.start?.calendarDate(zone)?.let { selectedDate = it; model.date(it, mode == ShiftCalendarMode.MONTH) }
        selectedItem = item
    }
    fun moveDate(direction: Int) {
        if (state.busy) return
        selectedDate = if (mode == ShiftCalendarMode.MONTH) LocalDate(selectedDate.year, selectedDate.month, 1).plus(DatePeriod(months = direction))
            else selectedDate.plus(DatePeriod(days = direction * 7))
        model.date(selectedDate, mode == ShiftCalendarMode.MONTH)
    }
    val monthTitle = "${selectedDate.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${selectedDate.year}"
    val desktop = !isPhoneWindow()
    LaunchedEffect(board?.timezone) { RestaurantTime.use(board?.timezone) }

    Column(modifier.fillMaxSize().background(Color.White).padding(horizontal = if (desktop) 26.dp else 16.dp, vertical = if (desktop) 20.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Shifts", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.sp, color = FormInk)
                Text("Manage your team's schedule", fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = FormMuted)
            }
            if (state.canManage) CalendarPrimaryButton("Add shift", Icons.Outlined.Add) { openEditor() }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            CalendarSecondaryButton("Today", Icons.Outlined.Today, Modifier.widthIn(min = 80.dp)) { selectedDate = today; model.date(today, mode == ShiftCalendarMode.MONTH) }
            CalendarIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Previous") { moveDate(-1) }
            CalendarIconButton(Icons.AutoMirrored.Outlined.ArrowForward, "Next") { moveDate(1) }
            Box {
                HeaderButton(monthTitle, Icons.Outlined.CalendarMonth, { dateMenu = true }, Modifier.widthIn(min = 182.dp, max = 210.dp))
                DropdownMenu(dateMenu, { dateMenu = false }, shape = RoundedCornerShape(12.dp), containerColor = Color.White) {
                    CompactDatePicker(selectedDate) { selectedDate = it; model.date(it, mode == ShiftCalendarMode.MONTH); dateMenu = false }
                }
            }
            Spacer(Modifier.weight(1f, fill = false))
            CalendarSegment(mode, onSelect = { mode = it; model.date(selectedDate, it == ShiftCalendarMode.MONTH) })
            if (knownStaff.isNotEmpty()) {
                StaffSearchField(staffQuery, onQueryChange = { staffQuery = it })
                ShiftFilterDropdown("All roles", Icons.Outlined.Badge, knownStaff.flatMap { person -> person.roles.map { it to it } }.distinctBy { it.first }.sortedBy { it.second }, roleFilter.takeIf { it != "All roles" }) { roleFilter = it ?: "All roles" }
                ShiftFilterDropdown("All staff", Icons.Outlined.People, knownStaff.map { it.id to it.name }, staffFilter) { staffFilter = it }
            }
            ShiftStatusFilter(statusFilter) { statusFilter = it }
        }
        if (state.error != null && !editorOpen && pending == null) ShiftMessage(state.error, true) { model.clearError() }
        if (state.notice != null) ShiftMessage(state.notice, false) { model.clearError() }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = FormGreen, trackColor = FormGreenSoft)
        if (!state.ready || !state.canRead) {
            ShiftPanel("Shift access", Icons.Outlined.Lock) { Text("Your account needs a restaurant, branch, and shift permission.", fontFamily = Inter(), fontSize = 13.sp, color = FormMuted) }
        } else if (board == null && !state.loading) {
            ShiftPanel("Couldn't load shifts", Icons.Outlined.CloudOff) {
                Text("Your team's schedule could not be loaded. Check your connection and try again.", fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
                CalendarSecondaryButton("Try again", Icons.Outlined.Refresh) { model.clearError(); model.refresh() }
            }
        } else {
            Text(calendarRange(selectedDate, mode, zone), fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = FormMuted)
            if (desktop) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ShiftCalendarCard(Modifier.weight(0.69f).fillMaxHeight()) {
                        when (mode) {
                            ShiftCalendarMode.WEEK -> WeekStaffGrid(weekdays, filteredStaff, visible(all), zone, selectedDate, today, state.busy,
                                onSelectDay = { selectedDate = it }, onOpen = ::openItem,
                                onAdd = { person, date -> openEditor(date = date, userId = person) }, onAddShift = { openEditor() })
                            ShiftCalendarMode.MONTH -> MonthStaffGrid(monthDays, selectedDate, visible(all), zone, state.busy, onSelectDay = { selectedDate = it; model.date(it, true) }, onOpen = ::openItem)
                            ShiftCalendarMode.TIME -> WeekTimeGrid(weekdays, visible(all), zone, selectedDate, today, onSelectDay = { selectedDate = it }, onOpen = ::openItem, onAdd = { date, hour -> openEditor(date = date, time = "${hour.toString().padStart(2, '0')}:00") })
                            ShiftCalendarMode.LIST -> WeekAgenda(weekdays, visible(all), zone, ::openItem, onAdd = { openEditor(date = it) })
                        }
                    }
                    SelectedDayPanel(Modifier.weight(0.31f).fillMaxHeight(), selectedDate, dayItems, state, now, zone,
                        onPrevious = { selectedDate = selectedDate.minus(DatePeriod(days = 1)); model.date(selectedDate, mode == ShiftCalendarMode.MONTH) },
                        onNext = { selectedDate = selectedDate.plus(DatePeriod(days = 1)); model.date(selectedDate, mode == ShiftCalendarMode.MONTH) },
                        onOpen = ::openItem, onAdd = { openEditor(date = selectedDate, userId = staffFilter) })
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShiftCalendarCard(Modifier.fillMaxWidth()) {
                        when (mode) {
                            ShiftCalendarMode.WEEK -> WeekDayStrip(weekdays, visible(all), zone, selectedDate, today, state.busy, onSelectDay = { selectedDate = it }, onOpen = ::openItem)
                            ShiftCalendarMode.MONTH -> MonthStaffGrid(monthDays, selectedDate, visible(all), zone, state.busy, onSelectDay = { selectedDate = it; model.date(it, true) }, onOpen = ::openItem)
                            ShiftCalendarMode.TIME -> WeekTimeGrid(weekdays, visible(all), zone, selectedDate, today, onSelectDay = { selectedDate = it }, onOpen = ::openItem, onAdd = { date, hour -> openEditor(date = date, time = "${hour.toString().padStart(2, '0')}:00") })
                            ShiftCalendarMode.LIST -> WeekAgenda(weekdays, visible(all), zone, ::openItem, onAdd = { openEditor(date = it) })
                        }
                    }
                    SelectedDayPanel(Modifier.fillMaxWidth(), selectedDate, dayItems, state, now, zone,
                        onPrevious = { selectedDate = selectedDate.minus(DatePeriod(days = 1)); model.date(selectedDate, mode == ShiftCalendarMode.MONTH) },
                        onNext = { selectedDate = selectedDate.plus(DatePeriod(days = 1)); model.date(selectedDate, mode == ShiftCalendarMode.MONTH) },
                        onOpen = ::openItem, onAdd = { openEditor(date = selectedDate, userId = staffFilter) })
                }
            }
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

@Composable private fun CalendarPrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.height(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = FormGreen) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Color.White)
        }
    }
}

@Composable private fun CalendarSecondaryButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, FormBorder)) {
        Row(Modifier.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(17.dp), tint = FormInk)
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun CalendarIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(42.dp), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, FormBorder)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(18.dp), tint = FormInk) }
    }
}

@Composable private fun CalendarSegment(selected: ShiftCalendarMode, onSelect: (ShiftCalendarMode) -> Unit) {
    Row(Modifier.height(40.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, FormBorder, RoundedCornerShape(8.dp)).background(Color.White).padding(3.dp)) {
        listOf(ShiftCalendarMode.WEEK to "Week", ShiftCalendarMode.MONTH to "Month", ShiftCalendarMode.TIME to "Time", ShiftCalendarMode.LIST to "List").forEach { (mode, label) ->
            val active = selected == mode
            Surface(onClick = { onSelect(mode) }, shape = RoundedCornerShape(6.dp), color = if (active) FormGreenSoft else Color.Transparent) {
                Box(Modifier.widthIn(min = 58.dp).fillMaxHeight().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontFamily = Inter(), fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.sp, color = if (active) FormGreen else FormMuted)
                }
            }
        }
    }
}


@Composable private fun StaffSearchField(value: String, onQueryChange: (String) -> Unit) {
    Row(Modifier.widthIn(min = 145.dp, max = 185.dp).height(ToolbarHeight).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, FormBorder, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(Icons.Outlined.Search, null, Modifier.size(17.dp), tint = FormMuted)
        BasicTextField(value, onQueryChange, Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Inter(), fontSize = 12.sp, color = FormInk), decorationBox = { inner ->
            if (value.isEmpty()) Text("Search staff", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted, maxLines = 1)
            inner()
        })
        if (value.isNotEmpty()) IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(22.dp)) { Icon(Icons.Outlined.Close, "Clear search", Modifier.size(14.dp)) }
    }
}

@Composable private fun ShiftFilterDropdown(placeholder: String, icon: ImageVector, options: List<Pair<String, String>>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: placeholder
    Box {
        Surface(onClick = { expanded = true }, modifier = Modifier.widthIn(min = 130.dp, max = 190.dp).height(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, FormBorder)) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(icon, null, Modifier.size(17.dp), tint = FormInk)
                Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = FormInk)
            }
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 340.dp).background(Color.White)) {
            DropdownMenuItem(text = { Text("All staff", fontFamily = Inter(), fontSize = 13.sp) }, onClick = { onSelect(null); expanded = false })
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name, fontFamily = Inter(), fontSize = 13.sp) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

@Composable private fun ShiftStatusFilter(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { expanded = true }, modifier = Modifier.widthIn(min = 145.dp, max = 170.dp).height(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, FormBorder)) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.FilterList, null, Modifier.size(17.dp), tint = FormInk)
                Text(selected, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = FormInk)
            }
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.background(Color.White)) {
            listOf("All statuses", "Scheduled", "On duty", "On break", "Completed", "Missed", "Cancelled").forEach { label ->
                DropdownMenuItem(text = { Text(label, fontFamily = Inter(), fontSize = 13.sp) }, onClick = { onSelect(label); expanded = false })
            }
        }
    }
}

@Composable private fun ShiftCalendarCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, calendarLine)) {
        Column(content = content)
    }
}

@Composable private fun WeekStaffGrid(
    dates: List<LocalDate>, staff: List<ShiftStaff>, items: List<ShiftItem>, zone: TimeZone,
    selected: LocalDate, today: LocalDate, busy: Boolean, onSelectDay: (LocalDate) -> Unit,
    onOpen: (ShiftItem) -> Unit, onAdd: (String, LocalDate) -> Unit, onAddShift: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(calendarSoft).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Staff", Modifier.weight(1.35f).padding(start = 12.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = FormInk)
            dates.forEach { date ->
                Surface(onClick = { onSelectDay(date) }, modifier = Modifier.weight(1f).padding(horizontal = 2.dp), shape = RoundedCornerShape(6.dp), color = if (date == selected) FormGreenSoft else Color.Transparent) {
                    Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(calendarWeekday(date), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.sp, color = FormMuted)
                        Text("${date.day} ${calendarMonth(date)}", fontFamily = Inter(), fontWeight = if (date == today) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.sp, color = if (date == today) FormGreen else FormInk)
                    }
                }
            }
        }
        HorizontalDivider(color = calendarLine)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            staff.forEach { person ->
                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1.35f).padding(start = 9.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StaffAvatar(person.name, Modifier.size(30.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = FormInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(person.roles.firstOrNull() ?: "Team member", fontFamily = Inter(), fontSize = 10.sp, letterSpacing = 0.sp, color = FormMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    dates.forEach { date ->
                        val shifts = items.filter { it.userId == person.id && it.start?.calendarDate(zone) == date }.sortedBy { it.start }
                        Column(Modifier.weight(1f).heightIn(min = 68.dp).padding(horizontal = 2.dp, vertical = 3.dp).border(1.dp, calendarLine, RoundedCornerShape(6.dp)).background(if (date == selected) FormGreenSoft.copy(alpha = .56f) else Color.White, RoundedCornerShape(6.dp)).padding(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            shifts.take(2).forEach { item -> ShiftGridChip(item, zone, onClick = { onOpen(item) }) }
                            if (shifts.size > 2) Text("+${shifts.size - 2} more", fontFamily = Inter(), fontSize = 10.sp, color = FormMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
                            if (shifts.isEmpty() && !busy) Box(Modifier.fillMaxWidth().height(58.dp).clickable { onAdd(person.id, date) }, contentAlignment = Alignment.Center) {
                                Text("+", fontFamily = Inter(), fontSize = 18.sp, color = FormMuted)
                            }
                        }
                    }
                }
                HorizontalDivider(color = calendarLine)
            }
            if (staff.isEmpty()) Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.People, null, Modifier.size(30.dp), tint = FormMuted)
                Text("No staff members are assigned to this branch yet.", fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
                Text("Add staff from Users, then return here to plan their shifts.", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
            }
        }
        HorizontalDivider(color = calendarLine)
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.Start) {
            CalendarSecondaryButton("Schedule shift", Icons.Outlined.Add, onClick = onAddShift)
        }
    }
}

@Composable private fun WeekDayStrip(dates: List<LocalDate>, items: List<ShiftItem>, zone: TimeZone, selected: LocalDate, today: LocalDate, busy: Boolean, onSelectDay: (LocalDate) -> Unit, onOpen: (ShiftItem) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        dates.forEach { date ->
            val dayItems = items.filter { it.start?.calendarDate(zone) == date }.sortedBy { it.start }
            Surface(onClick = { onSelectDay(date) }, modifier = Modifier.widthIn(min = 108.dp, max = 142.dp).heightIn(min = 105.dp), shape = RoundedCornerShape(8.dp), color = if (selected == date) FormGreenSoft else Color.White, border = BorderStroke(1.dp, if (selected == date) FormGreen.copy(alpha = .55f) else calendarLine)) {
                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${calendarWeekday(date)}, ${date.day} ${calendarMonth(date)}", fontFamily = Inter(), fontWeight = if (date == today) FontWeight.Bold else FontWeight.SemiBold, fontSize = 11.sp, color = if (date == today) FormGreen else FormInk)
                    if (dayItems.isEmpty()) Text("No shifts", fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
                    dayItems.take(2).forEach { item -> TextButton(onClick = { onSelectDay(date); onOpen(item) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("${item.start.calendarTime(zone)}  ${item.userName}", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    if (dayItems.size > 2) Text("+${dayItems.size - 2} more", fontFamily = Inter(), fontSize = 10.sp, color = FormMuted)
                }
            }
        }
    }
}


@Composable private fun WeekTimeGrid(dates: List<LocalDate>, items: List<ShiftItem>, zone: TimeZone, selected: LocalDate, today: LocalDate,
    onSelectDay: (LocalDate) -> Unit, onOpen: (ShiftItem) -> Unit, onAdd: (LocalDate, Int) -> Unit) {
    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState()).padding(8.dp)) {
        Column(Modifier.width(48.dp)) {
            Box(Modifier.height(54.dp), contentAlignment = Alignment.Center) { Text("Time", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = FormMuted) }
            (6..23).forEach { hour ->
                Box(Modifier.height(46.dp), contentAlignment = Alignment.TopCenter) {
                    Text("${hour.toString().padStart(2, '0')}:00", fontFamily = Inter(), fontSize = 10.sp, letterSpacing = 0.sp, color = FormMuted)
                }
            }
        }
        dates.forEach { date ->
            Column(Modifier.widthIn(min = 116.dp).weight(1f)) {
                Surface(onClick = { onSelectDay(date) }, modifier = Modifier.fillMaxWidth().height(54.dp), color = if (date == selected) FormGreenSoft else calendarSoft, shape = RoundedCornerShape(6.dp)) {
                    Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(calendarWeekday(date), fontFamily = Inter(), fontSize = 10.sp, color = FormMuted)
                        Text("${date.day} ${calendarMonth(date)}", fontFamily = Inter(), fontWeight = if (date == today) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp, color = if (date == today) FormGreen else FormInk)
                    }
                }
                (6..23).forEach { hour ->
                    val shifts = items.filter { it.start?.calendarDate(zone) == date && it.start.calendarHour(zone) == hour }.sortedBy { it.start }
                    Column(Modifier.fillMaxWidth().height(46.dp).border(.5.dp, calendarLine).clickable(enabled = shifts.isEmpty()) { onAdd(date, hour) }.padding(2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        shifts.take(2).forEach { item ->
                            Surface(onClick = { onOpen(item) }, modifier = Modifier.fillMaxWidth().weight(1f, fill = false), shape = RoundedCornerShape(3.dp), color = calendarStatusColor(item.status).copy(alpha = .13f)) {
                                Text("${item.start.calendarTime(zone)} ${item.userName}", Modifier.padding(horizontal = 3.dp, vertical = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 0.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MonthStaffGrid(dates: List<LocalDate>, selected: LocalDate, items: List<ShiftItem>, zone: TimeZone, busy: Boolean, onSelectDay: (LocalDate) -> Unit, onOpen: (ShiftItem) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(7.dp)) {
        Row(Modifier.fillMaxWidth().background(calendarSoft)) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label -> Text(label, Modifier.weight(1f).padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = FormMuted) }
        }
        dates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val dayItems = items.filter { it.start?.calendarDate(zone) == date }.sortedBy { it.start }
                    Surface(onClick = { onSelectDay(date) }, modifier = Modifier.weight(1f).heightIn(min = 82.dp).padding(2.dp), shape = RoundedCornerShape(6.dp), color = if (date == selected) FormGreenSoft else Color.White, border = BorderStroke(1.dp, if (date == selected) FormGreen.copy(alpha = .45f) else calendarLine)) {
                        Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${date.day}", fontFamily = Inter(), fontWeight = if (date == selected) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp, color = if (date == selected) FormGreen else FormInk)
                            dayItems.take(2).forEach { item ->
                                TextButton(onClick = { onSelectDay(date); onOpen(item) }, modifier = Modifier.fillMaxWidth().heightIn(min = 19.dp), contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                                    Text("${item.start.calendarTime(zone)} ${item.userName}", fontFamily = Inter(), fontSize = 9.sp, letterSpacing = 0.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = FormGreen)
                                }
                            }
                            if (dayItems.size > 2) Text("+${dayItems.size - 2} more", fontFamily = Inter(), fontSize = 9.sp, color = FormMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun WeekAgenda(dates: List<LocalDate>, items: List<ShiftItem>, zone: TimeZone, onOpen: (ShiftItem) -> Unit, onAdd: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        dates.forEach { date ->
            val dayItems = items.filter { it.start?.calendarDate(zone) == date }.sortedBy { it.start }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${calendarWeekday(date)}, ${date.day} ${calendarMonth(date)}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = FormInk)
                    Text("${dayItems.size} ${if (dayItems.size == 1) "shift" else "shifts"}", fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
                }
                CalendarIconButton(Icons.Outlined.Add, "Schedule shift") { onAdd(date) }
            }
            if (dayItems.isEmpty()) Text("No shifts scheduled", Modifier.padding(start = 4.dp, bottom = 5.dp), fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
            dayItems.forEach { item -> ShiftAgendaRow(item, zone, onClick = { onOpen(item) }) }
            HorizontalDivider(color = calendarLine)
        }
    }
}

@Composable private fun ShiftGridChip(item: ShiftItem, zone: TimeZone, onClick: () -> Unit) {
    val tint = calendarStatusColor(item.status)
    Surface(onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp), color = tint.copy(alpha = if (item.status == "SCHEDULED") .12f else .08f)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("${item.start.calendarTime(zone)}–${(item.scheduledEnd ?: item.endedAt).calendarTime(zone)}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Clip)
            Text(calendarStatus(item.status), fontFamily = Inter(), fontSize = 9.sp, letterSpacing = 0.sp, color = tint, maxLines = 1)
        }
    }
}

@Composable private fun ShiftAgendaRow(item: ShiftItem, zone: TimeZone, onClick: () -> Unit) {
    Surface(onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, calendarLine)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.start.calendarTime(zone), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormInk, modifier = Modifier.width(54.dp))
            StaffAvatar(item.userName, Modifier.size(29.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.userName, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.start.calendarTime(zone)} – ${(item.scheduledEnd ?: item.endedAt).calendarTime(zone)}${item.notes?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", fontFamily = Inter(), fontSize = 11.sp, color = FormMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CalendarStatusBadge(item.status)
            Icon(Icons.Outlined.ChevronRight, "Shift details", Modifier.size(18.dp), tint = FormMuted)
        }
    }
}

@Composable private fun SelectedDayPanel(modifier: Modifier, date: LocalDate, items: List<ShiftItem>, state: ShiftState, now: Instant, zone: TimeZone,
    onPrevious: () -> Unit, onNext: () -> Unit, onOpen: (ShiftItem) -> Unit, onAdd: () -> Unit) {
    val review = items.count { it.status == "MISSED" || (it.status == "SCHEDULED" && it.scheduledEnd?.let { end -> runCatching { Instant.parse(end) < now }.getOrDefault(false) } == true) }
    val onBreak = items.count { it.status == "ON_BREAK" }
    ShiftCalendarCard(modifier) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${calendarWeekday(date)}, ${date.day} ${calendarMonth(date)} ${date.year}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.sp, color = FormInk)
                    if (date == state.date) Text("Selected day", fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
                }
                IconButton(onPrevious, enabled = !state.busy, modifier = Modifier.size(36.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous day", Modifier.size(18.dp)) }
                IconButton(onNext, enabled = !state.busy, modifier = Modifier.size(36.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Next day", Modifier.size(18.dp)) }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(calendarSoft).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                DaySummary("${items.size}", "Shifts", Icons.Outlined.People, FormGreen)
                VerticalDivider(Modifier.height(38.dp), color = calendarLine)
                DaySummary("$onBreak", "On break", Icons.Outlined.Coffee, ShiftAmber)
                VerticalDivider(Modifier.height(38.dp), color = calendarLine)
                DaySummary("$review", "Needs review", Icons.Outlined.Info, if (review > 0) ShiftAmber else FormMuted)
            }
            HorizontalDivider(color = calendarLine)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Scheduled (${items.size})", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = FormInk)
                if (state.canManage) CalendarSecondaryButton("Add to day", Icons.Outlined.Add, onClick = onAdd)
            }
            if (items.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.EventAvailable, null, Modifier.size(34.dp), tint = FormMuted)
                Spacer(Modifier.height(8.dp))
                Text("No shifts on this day", fontFamily = Inter(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FormInk)
                Text("Add a shift to plan your team's day.", fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
            } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items.forEach { item -> ShiftDayRow(item, zone, onClick = { onOpen(item) }) }
            }
            if (items.isEmpty() && state.canManage) CalendarPrimaryButton("Add shift", Icons.Outlined.Add, onAdd)
        }
    }
}

@Composable private fun DaySummary(value: String, label: String, icon: ImageVector, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp), tint = tint)
            Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.sp, color = FormInk)
        }
        Text(label, fontFamily = Inter(), fontSize = 10.sp, letterSpacing = 0.sp, color = FormMuted, maxLines = 1)
    }
}

@Composable private fun ShiftDayRow(item: ShiftItem, zone: TimeZone, onClick: () -> Unit) {
    var menu by remember(item.id) { mutableStateOf(false) }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, calendarLine)) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StaffAvatar(item.userName, Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.userName, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.start.calendarTime(zone)}–${(item.scheduledEnd ?: item.endedAt).calendarTime(zone)}", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.sp, color = FormInk, maxLines = 1)
                Text(if (item.notes.isNullOrBlank()) calendarStatus(item.status) else "${calendarStatus(item.status)} · ${item.notes}", fontFamily = Inter(), fontSize = 10.sp, letterSpacing = 0.sp, color = FormMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.status == "ON_BREAK") Icon(Icons.Outlined.Coffee, "On break", Modifier.size(17.dp), tint = ShiftAmber)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.MoreVert, "Shift actions", Modifier.size(18.dp), tint = FormMuted) }
                DropdownMenu(menu, { menu = false }, modifier = Modifier.background(Color.White)) {
                    DropdownMenuItem(text = { Text("View details", fontFamily = Inter(), fontSize = 13.sp) }, onClick = { menu = false; onClick() })
                }
            }
        }
    }
}

@Composable private fun CalendarStatusBadge(status: String) {
    val tint = calendarStatusColor(status)
    Text(calendarStatus(status), Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = .10f)).padding(horizontal = 8.dp, vertical = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.sp, color = tint)
}

@Composable private fun StaffAvatar(name: String, modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFFE4F1E8), Color(0xFFE9EEFD), Color(0xFFFFEACF), Color(0xFFFCE4E9), Color(0xFFEFE7FD))
    val tints = listOf(Color(0xFF287247), Color(0xFF3C61B5), Color(0xFFB36412), Color(0xFFB34C65), Color(0xFF7350B6))
    val index = (name.hashCode().toLong().and(0x7fffffff) % colors.size).toInt()
    Box(modifier.clip(CircleShape).background(colors[index]), contentAlignment = Alignment.Center) {
        Text(name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = tints[index])
    }
}

private fun calendarStatus(status: String): String = when (status) {
    "OPEN" -> "On duty"
    "ON_BREAK" -> "On break"
    "SCHEDULED" -> "Scheduled"
    "CLOSED" -> "Completed"
    "MISSED" -> "Missed"
    "CANCELLED" -> "Cancelled"
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
private fun calendarStatusColor(status: String): Color = when (status) {
    "OPEN", "CLOSED" -> FormGreen
    "ON_BREAK" -> ShiftAmber
    "SCHEDULED" -> Color(0xFF34794C)
    "MISSED", "CANCELLED" -> Color(0xFFB83B34)
    else -> FormMuted
}
private fun calendarDate(date: LocalDate): String = "${calendarWeekday(date)}, ${date.day} ${calendarMonth(date)}"
private fun calendarWeekday(date: LocalDate): String = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
private fun calendarMonth(date: LocalDate): String = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
private fun calendarRange(date: LocalDate, mode: ShiftCalendarMode, zone: TimeZone): String = when (mode) {
    ShiftCalendarMode.WEEK -> {
        val start = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1)); val end = start.plus(DatePeriod(days = 6))
        "${calendarDate(start)} – ${calendarDate(end)} · ${zone.id}"
    }
    ShiftCalendarMode.MONTH -> "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
    ShiftCalendarMode.TIME -> calendarDate(date)
    ShiftCalendarMode.LIST -> "Week of ${calendarDate(date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1)))}"
}
private fun String?.calendarDate(zone: TimeZone): LocalDate? = this?.let { runCatching { Instant.parse(it).toLocalDateTime(zone).date }.getOrNull() }
private fun String?.calendarHour(zone: TimeZone): Int? = this?.let { runCatching { Instant.parse(it).toLocalDateTime(zone).hour }.getOrNull() }
private fun String?.calendarTime(zone: TimeZone): String = this?.let { runCatching { Instant.parse(it).toLocalDateTime(zone).time.toString().take(5) }.getOrNull() } ?: "—"
