package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.ui.OptionGroup
import com.saporini.mobile_desktop.admin.inventory.ui.OptionLine
import com.saporini.mobile_desktop.admin.inventory.ui.PhoneControlHeight
import com.saporini.mobile_desktop.admin.inventory.ui.PhoneOptionsButton
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.BottomToast
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RightEdgeActionButton
import com.saporini.mobile_desktop.core.components.RightEdgeActionButtonHeight
import com.saporini.mobile_desktop.core.components.EdgeScrollbar
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.reservations.CompactDatePicker
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Instant

// Admin Hub → Shifts: the month calendar on a page that scrolls (the four summary cards were removed at the user's
// request). The calendar's bar has search on the left and the month, filters, shift times
// and refresh on the right. Clicking a day opens it (ShiftDayDialog): who works, edit / remove / fix hours, and adding
// people with the usual shift times. "Plan shifts" puts several people on several days. Everyone only sees people at
// their level or below (ShiftVisibility).

/** A day's width in a phone's month, which slides sideways: wide enough for faces and the part-of-day counts. */
private val PhoneMonthCell = 124.dp

/** Where today's week row and the page are on screen (top, height in px), to scroll to today when a month opens. */
private class TodayGlide {
    var row by mutableStateOf<Pair<Float, Int>?>(null)
    var view by mutableStateOf<Pair<Float, Int>?>(null)
    var done by mutableStateOf(false)
}

/** Opening the calendar glides down to today's week when it's below the fold, once per visit. */
@Composable
private fun GlideToToday(glide: TodayGlide, scroll: ScrollState) {
    val density = LocalDensity.current
    LaunchedEffect(glide.row != null, glide.view != null) {
        if (glide.done || glide.row == null || glide.view == null) return@LaunchedEffect
        delay(350)
        val (rowTop, rowHeight) = glide.row ?: return@LaunchedEffect
        val (viewTop, viewHeight) = glide.view ?: return@LaunchedEffect
        glide.done = true
        val bottom = rowTop - viewTop + rowHeight
        if (bottom > viewHeight) scroll.animateScrollTo((scroll.value + bottom - viewHeight + 24 * density.density).toInt(),
            androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
}

/**
 * The month. A phone gets the same month as a computer, sliding sideways (user, 2026-10-31: "same as on the PC, but I can
 * scroll"); it opens on today's column. A day opens the day window.
 */
@Composable
private fun MonthGrid(
    phone: Boolean, anchor: LocalDate, monthDays: List<LocalDate>, shown: List<ShiftItem>, zone: TimeZone, today: LocalDate, now: Instant, canAdd: Boolean,
    glide: TodayGlide, onDay: (LocalDate) -> Unit, onAdd: (LocalDate) -> Unit
) {
    val onTodayRow: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = { if (!glide.done) glide.row = it.positionInRoot().y to it.size.height }
    if (phone) {
        val sideways = rememberScrollState()
        val cell = with(LocalDensity.current) { PhoneMonthCell.toPx() }
        val todayHere = today.year == anchor.year && today.month == anchor.month
        LaunchedEffect(anchor.month, todayHere) {
            if (todayHere) sideways.scrollTo(((today.dayOfWeek.ordinal - 1).coerceAtLeast(0) * cell).toInt())
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(sideways)) {
            Box(Modifier.width(PhoneMonthCell * 7)) { MonthOverview(anchor, monthDays, shown, zone, today, now, canAdd, onDay = onDay, onTodayRow = onTodayRow, onAdd = onAdd) }
        }
    } else MonthOverview(anchor, monthDays, shown, zone, today, now, canAdd, onDay = onDay, onTodayRow = onTodayRow, onAdd = onAdd)
}

/** What the planner opens with (a usual shift picked from its card, too). */
private data class PlanRequest(val date: LocalDate, val template: String? = null)

@Composable
internal fun ShiftSchedule(
    state: ShiftState, model: ShiftScreenModel, size: ScreenSize, modifier: Modifier = Modifier, side: Dp = 22.dp, openDay: LocalDate? = null
) {
    LaunchedEffect(Unit) { if (!state.monthView) model.date(state.date, true) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var show by remember { mutableStateOf(ShiftShow.ALL) }
    var selectedItem by remember { mutableStateOf<ShiftItem?>(null) }
    var editorItem by remember { mutableStateOf<ShiftItem?>(null) }
    var correcting by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<PlanRequest?>(null) }
    var editTimes by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, ShiftItem?>?>(null) }
    var dayOpen by remember { mutableStateOf(openDay) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val glide = remember { TodayGlide() }
    // On-duty marks and "late" move on by themselves.
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = Clock.System.now() } }

    val board = state.board
    val zone = state.zone
    val today = now.toLocalDateTime(zone).date
    val anchor = state.date
    val monthFirst = LocalDate(anchor.year, anchor.month, 1)
    val monthDays = (0..41).map { monthFirst.weekStart().plus(DatePeriod(days = it)) }.let { days ->
        days.take((days.indexOfLast { it.month == anchor.month } / 7 + 1) * 7)
    }
    val periodDays = monthDays.filter { it.month == anchor.month }
    var selectedDay by remember { mutableStateOf(today) }
    LaunchedEffect(periodDays.first(), periodDays.last()) {
        if (selectedDay !in periodDays) selectedDay = if (today in periodDays) today else periodDays.first()
    }

    // Everyone this person may see: themselves and people at their level or below.
    val rawItems = board?.items.orEmpty()
    val knownStaff = (board?.staff.orEmpty() + rawItems.map { ShiftStaff(it.userId, it.userName) }).distinctBy { it.id }
        .filter { it.id == state.userId || ShiftVisibility.canSee(state.viewerRank, it.roles) }
    val visibleIds = knownStaff.mapTo(HashSet()) { it.id }
    val all = rawItems.filter { it.userId in visibleIds }
    val periodItems = all.filter { it.start.localDate(zone) in periodDays }
    val groups = knownStaff.groupBy { staffGroupOf(it.roles) }.toList().sortedWith(compareBy({ it.first.order }, { it.first.label }))
    val people = knownStaff
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .filter { group == null || staffGroupOf(it.roles).key == group }
        .sortedWith(compareBy({ staffGroupOf(it.roles).order }, { it.name.lowercase() }))
    val peopleIds = people.mapTo(HashSet()) { it.id }
    val shown = all.filter { it.userId in peopleIds && show.matches(lookOf(it, now)) }
    val canAdd = state.canManage && !state.busy
    // Who can be put on a shift: the branch's roster the person may see.
    val roster = board?.staff.orEmpty().map { it.id }.toSet()
    val addable = knownStaff.filter { it.id in roster }

    fun go(date: LocalDate) = model.date(date, true)
    fun openPlan(date: LocalDate = if (today in periodDays) today else periodDays.first(), template: String? = null) {
        model.clearError(); selectedItem = null; plan = PlanRequest(date, template)
    }
    // One person on one day at [start]–[end] (a usual shift's times, or their own).
    fun addAt(person: ShiftStaff, date: LocalDate, start: LocalTime, end: LocalTime) {
        localMessage = null; model.clearError()
        val preview = planShifts(listOf(person), listOf(date), start, end, null, zone, all, now)
        when {
            preview.shifts.isNotEmpty() -> model.planMany(preview.shifts) {}
            preview.alreadyWorking.isNotEmpty() -> localMessage = "${person.name} already works then. Open that shift to change it."
            else -> localMessage = "That shift would already be over. Pick a later day."
        }
    }
    fun quickAdd(person: ShiftStaff, date: LocalDate, template: ShiftTemplate) = addAt(person, date, template.start, template.end)
    LaunchedEffect(board?.timezone) { RestaurantTime.use(board?.timezone) }
    val phone = size.isPhone

    val filterCount = (if (group != null) 1 else 0) + (if (show != ShiftShow.ALL) 1 else 0)
    val options: @Composable ColumnScope.(() -> Unit) -> Unit = { close ->
        OptionGroup("Team")
        OptionLine("Everyone", group == null, { group = null; close() }, icon = Icons.Outlined.Groups, count = knownStaff.size)
        groups.forEach { (g, members) -> OptionLine(g.label, group == g.key, { group = g.key; close() }, icon = groupIcon(g), color = groupColor(g), count = members.size) }
        OptionGroup("Show")
        ShiftShow.entries.forEach { option ->
            OptionLine(option.label, show == option, { show = option; close() }, icon = option.icon, color = option.color,
                count = periodItems.count { option.matches(lookOf(it, now)) })
        }
        if (state.canManage) {
            OptionGroup("Planning")
            OptionLine("Edit shift times", false, { editTimes = true; close() }, icon = Icons.Outlined.Tune)
        }
    }

    val calendar: @Composable (Modifier) -> Unit = { m ->
        Surface(m, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
            Column {
                CalendarBar(
                    size, label = monthLabel(anchor),
                    anchor = anchor, showToday = today !in periodDays,
                    onPrevious = { go(anchor.minus(DatePeriod(months = 1))) }, onNext = { go(anchor.plus(DatePeriod(months = 1))) },
                    onToday = { go(today); selectedDay = today }, onPick = { go(it); selectedDay = it },
                    query = query, onQuery = { query = it }, loading = state.loading, onRefresh = { model.clearError(); model.refresh() },
                    filters = filterCount, canPlanTimes = state.canManage, onTimes = { editTimes = true }, options = options,
                    onPlan = null
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
                MonthGrid(phone, anchor, monthDays, shown, zone, today, now, canAdd, glide, onDay = { dayOpen = it }) { openPlan(it) }
            }
        }
    }

    Column(modifier.fillMaxSize()) {
    // The page's title with Plan shifts on top (user, 2026-10-31: "Plan shifts on top, not on the edge").
    Row(Modifier.fillMaxWidth().padding(start = side, end = side, bottom = if (phone) 10.dp else 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Shifts", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (phone) 19.sp else 20.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
            Text("Who works when, and who's on now", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (canAdd && board != null) KitButton(if (phone) "Plan" else "Plan shifts", { openPlan() }, if (phone) Modifier.height(40.dp) else Modifier,
            icon = Icons.Outlined.EditCalendar, enabled = !state.busy)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        when {
            !state.ready || !state.canRead -> StatePage(Modifier.padding(horizontal = side), "No access to shifts", "Your account needs a restaurant, a branch and “View Shifts”.", Icons.Outlined.Lock)
            board == null && state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), color = Kit.Green, strokeWidth = 3.dp) }
            board == null -> StatePage(Modifier.padding(horizontal = side), "Couldn't load the schedule", "Check the connection, then try again.", Icons.Outlined.CloudOff) {
                KitButton("Try again", { model.clearError(); model.refresh() }, icon = Icons.Outlined.Refresh)
            }
            // The whole page scrolls: the calendar is as tall as it needs to be.
            else -> Box(Modifier.fillMaxSize().onGloballyPositioned { if (!glide.done) glide.view = it.positionInRoot().y to it.size.height }) {
                val scroll = rememberScrollState()
                GlideToToday(glide, scroll)
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = side, end = side, bottom = if (phone) 96.dp else 28.dp),
                    verticalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 16.dp)) {
                    calendar(Modifier.fillMaxWidth())
                }
                EdgeScrollbar(scroll)
            }
        }

    }
    }


    // Problems and "saved" pop up in the bottom-left corner; a dialog that is open shows its own problem.
    val dialogOpen = editorItem != null || pending != null || plan != null || dayOpen != null
    val toastMessage = localMessage?.takeIf { dayOpen == null }
    BottomToast(toastMessage ?: state.error?.takeIf { !dialogOpen } ?: state.notice, isError = toastMessage != null || (state.error != null && !dialogOpen)) {
        localMessage = null; model.clearError()
    }

    // A day of the month: everything about it in one place. The other dialogs open on top of it, so closing them comes back here.
    dayOpen?.takeIf { board != null }?.let { day ->
        ShiftDayDialog(
            day, periodDays.first(), periodDays.last(), today, now, zone, state, people = addable, items = all,
            message = (localMessage ?: state.error).takeIf { editorItem == null && pending == null && plan == null },
            onDay = { dayOpen = it }, onDismiss = { dayOpen = null; localMessage = null; model.clearError() },
            onOpen = { selectedItem = it },
            onEdit = { item -> correcting = false; editorItem = item },
            onRemove = { item -> pending = "cancel" to item },
            onAttendance = { item -> correcting = true; editorItem = item },
            onQuickAdd = { person, template -> quickAdd(person, day, template) },
            onAddAt = { person, start, end -> addAt(person, day, start, end) },
            onPlan = { openPlan(day) }
        )
    }
    selectedItem?.let { original ->
        val item = board?.items?.find { it.id == original.id } ?: original
        ShiftDetails(item, zone, state, now, onDismiss = { selectedItem = null },
            onEdit = { selectedItem = null; correcting = false; editorItem = item },
            onCorrect = { selectedItem = null; correcting = true; editorItem = item },
            onAction = { selectedItem = null; pending = it to item })
    }
    editorItem?.let { item ->
        ShiftEditor(state, item, correcting, item.start.localDate(zone) ?: anchor, item.userId,
            onDismiss = { if (!state.busy) editorItem = null },
            onSave = { request -> model.schedule(item.id, request) { editorItem = null } },
            onCorrect = { shift, request -> model.correct(shift, request) { editorItem = null } })
    }
    plan?.let { request ->
        PlanShiftsDialog(state, addable, now, request.date, initialTemplate = request.template,
            onDismiss = { plan = null }, onSaved = { model.planSaved(it) })
    }
    if (editTimes) ShiftTimesDialog { editTimes = false }
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

// ---------- The calendar's own bar ----------

/** Search on the left; the month (with Today when another month is shown), Filters, shift times and refresh on the right. */
@Composable
private fun CalendarBar(
    size: ScreenSize, label: String, anchor: LocalDate, showToday: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, onPick: (LocalDate) -> Unit,
    query: String, onQuery: (String) -> Unit, loading: Boolean, onRefresh: () -> Unit, filters: Int, canPlanTimes: Boolean, onTimes: () -> Unit,
    options: @Composable ColumnScope.(close: () -> Unit) -> Unit, onPlan: (() -> Unit)? = null,
    // POS → My shift → Team: only these months can be shown; the date button lists them instead of a date picker.
    months: List<LocalDate>? = null
) {
    val first = months == null || LocalDate(anchor.year, anchor.month, 1) > months.first()
    val last = months == null || LocalDate(anchor.year, anchor.month, 1) < months.last()
    val month: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // No Today button: the date picker has Today (user, 2026-10-31).
            BarSquare(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month", onPrevious, enabled = first)
            DateButton(label, anchor, when { size.isPhone -> Modifier.weight(1f); size.isDesktop -> Modifier.width(186.dp); else -> Modifier.width(178.dp) }, onPick, months)
            BarSquare(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month", onNext, enabled = last)
        }
    }
    val search: @Composable (Modifier) -> Unit = { m -> SearchField(query, onQuery, m, placeholder = "Search staff", height = PhoneControlHeight) }
    val filterButton: @Composable () -> Unit = { PhoneOptionsButton(Icons.Outlined.FilterList, "Filters", filters, options) }
    val refresh: @Composable () -> Unit = { RefreshButton(loading, onRefresh, size = PhoneControlHeight) }
    Box(Modifier.fillMaxWidth().padding(horizontal = if (size.isPhone) 10.dp else 14.dp, vertical = 12.dp)) {
        if (size.isPhone) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                search(Modifier.weight(1f)); filterButton()
                onPlan?.let { PlanButton(it) }
            }
            month(Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The search takes the room on the left (up to 320 dp); everything else stays against the right edge.
            Box(Modifier.weight(1f)) { search(Modifier.widthIn(max = 320.dp).fillMaxWidth()) }
            Spacer(Modifier.width(8.dp))
            month(Modifier)
            Spacer(Modifier.width(4.dp))
            filterButton()
            if (canPlanTimes && size.isDesktop) BarSquare(Icons.Outlined.Tune, "Edit shift times", onTimes)
            refresh()
        }
    }
}

@Composable
private fun BarSquare(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(PhoneControlHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(19.dp), tint = if (enabled) Kit.Ink else Kit.Faint) }
    }
}

/** "Plan" on a phone's calendar bar: the edge button's job where there's no room for it. */
@Composable
private fun PlanButton(onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.height(PhoneControlHeight), shape = RoundedCornerShape(8.dp), color = Kit.Green) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Outlined.Add, null, Modifier.size(17.dp), tint = Color.White)
            Text("Plan", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun DateButton(label: String, anchor: LocalDate, modifier: Modifier, onPick: (LocalDate) -> Unit, months: List<LocalDate>? = null) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(PhoneControlHeight), shape = RoundedCornerShape(8.dp), color = Color.White,
            border = BorderStroke(1.dp, if (open) Kit.Green else Kit.Border)) {
            Row(Modifier.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp), tint = Kit.Green)
                Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, textAlign = TextAlign.Center)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Muted)
            }
        }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
            if (months == null) CompactDatePicker(anchor) { onPick(it); open = false }
            else months.forEach { month ->
                val chosen = month.month == anchor.month && month.year == anchor.year
                Text(monthLabel(month), Modifier.widthIn(min = 186.dp).clickable { onPick(month); open = false }.padding(horizontal = 16.dp, vertical = 10.dp), fontFamily = Inter(),
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.sp, color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatePage(modifier: Modifier, title: String, hint: String, icon: ImageVector, action: (@Composable () -> Unit)? = null) {
    Surface(modifier.fillMaxWidth().heightIn(min = 360.dp), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 360.dp), contentAlignment = Alignment.Center) { OverviewEmpty(title, hint, icon, action = action) }
    }
}

// ---------- My shift → Team (read-only) ----------

private fun monthLabel(month: LocalDate) = "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}"

/**
 * Who works (user, 2026-10-31: "same as in the Admin Hub, one month before and one month ahead"): the Admin Hub's month
 * calendar for everyone they may see, read-only, from last month to next month. A day opens the day window as one list of
 * who works (no role bar, user 2026-10-31) or a timeline, with nothing to change there; a person opens their shift.
 */
@Composable
internal fun TeamMonth(state: ShiftState, model: ShiftScreenModel, size: ScreenSize, now: Instant, modifier: Modifier, openDay: LocalDate? = null, onOpen: (ShiftItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var show by remember { mutableStateOf(ShiftShow.ALL) }
    var dayOpen by remember { mutableStateOf(openDay) }
    val glide = remember { TodayGlide() }
    val zone = state.zone
    val today = now.toLocalDateTime(zone).date
    val thisMonth = LocalDate(today.year, today.month, 1)
    val months = listOf(thisMonth.minus(DatePeriod(months = 1)), thisMonth, thisMonth.plus(DatePeriod(months = 1)))
    val anchor = state.teamMonth ?: thisMonth
    val monthDays = (0..41).map { anchor.weekStart().plus(DatePeriod(days = it)) }.let { days ->
        days.take((days.indexOfLast { it.month == anchor.month } / 7 + 1) * 7)
    }
    val periodDays = monthDays.filter { it.month == anchor.month }
    val board = state.team
    val rawItems = board?.items.orEmpty()
    // The server decides who shows here (Admin Hub → Settings → Shifts).
    val knownStaff = (board?.staff.orEmpty() + rawItems.map { ShiftStaff(it.userId, it.userName) }).distinctBy { it.id }
    val all = rawItems
    val groups = knownStaff.groupBy { staffGroupOf(it.roles) }.toList().sortedWith(compareBy({ it.first.order }, { it.first.label }))
    val peopleIds = knownStaff.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .filter { group == null || staffGroupOf(it.roles).key == group }.mapTo(HashSet()) { it.id }
    val shown = all.filter { it.userId in peopleIds && show.matches(lookOf(it, now)) }
    val options: @Composable ColumnScope.(() -> Unit) -> Unit = { close ->
        OptionGroup("Team")
        OptionLine("Everyone", group == null, { group = null; close() }, icon = Icons.Outlined.Groups, count = knownStaff.size)
        groups.forEach { (g, members) -> OptionLine(g.label, group == g.key, { group = g.key; close() }, icon = groupIcon(g), color = groupColor(g), count = members.size) }
        OptionGroup("Show")
        ShiftShow.entries.forEach { option ->
            OptionLine(option.label, show == option, { show = option; close() }, icon = option.icon, color = option.color,
                count = all.count { it.start.localDate(zone) in periodDays && option.matches(lookOf(it, now)) })
        }
    }
    val calendar: @Composable () -> Unit = {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
            Column {
                CalendarBar(
                    size, label = monthLabel(anchor), anchor = anchor, showToday = false,
                    onPrevious = { model.teamMonth(anchor.minus(DatePeriod(months = 1))) }, onNext = { model.teamMonth(anchor.plus(DatePeriod(months = 1))) },
                    onToday = { model.teamMonth(today) }, onPick = { model.teamMonth(it) },
                    query = query, onQuery = { query = it }, loading = state.loading || state.teamLoading, onRefresh = { model.clearError(); model.refresh() },
                    filters = (if (group != null) 1 else 0) + (if (show != ShiftShow.ALL) 1 else 0), canPlanTimes = false, onTimes = {}, options = options, months = months
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
                if (board == null) TeamMonthShapes(monthDays.size / 7, size.isPhone)
                else MonthGrid(size.isPhone, anchor, monthDays, shown, zone, today, now, canAdd = false, glide, onDay = { dayOpen = it }) {}
            }
        }
    }
    // The desktop's page doesn't scroll, so the calendar scrolls here; on phones and tablets the page does.
    if (size.isDesktop) Box(modifier.onGloballyPositioned { if (!glide.done) glide.view = it.positionInRoot().y to it.size.height }) {
        val scroll = rememberScrollState()
        GlideToToday(glide, scroll)
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 12.dp)) { calendar() }
        EdgeScrollbar(scroll)
    } else Box(modifier) { calendar() }

    dayOpen?.takeIf { board != null }?.let { day ->
        // Nothing can be changed here, also for someone who can plan (that's in the Admin Hub).
        ShiftDayDialog(
            day, periodDays.first(), periodDays.last(), today, now, zone, state.copy(permissions = state.permissions - "SHIFT_MANAGE"), people = knownStaff, items = all,
            message = null, onDay = { dayOpen = it }, onDismiss = { dayOpen = null }, onOpen = onOpen, onEdit = {}, onRemove = {}, onAttendance = {},
            onQuickAdd = { _, _ -> }, onAddAt = { _, _, _ -> }, onPlan = {}, listOnly = true
        )
    }
}

/** While a month loads: its week rows as pulsing shapes. */
@Composable
private fun TeamMonthShapes(weeks: Int, phone: Boolean) {
    val alpha = com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha("team-month")
    Column(Modifier.fillMaxWidth().padding(12.dp).graphicsLayer { this.alpha = alpha }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(weeks.coerceAtLeast(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(if (phone) 3 else 7) { com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.weight(1f).height(126.dp), shape = RoundedCornerShape(10.dp)) }
            }
        }
    }
}
