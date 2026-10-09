@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.ValueLine
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.reservations.CompactDatePicker
import com.saporini.mobile_desktop.pos.reservations.HeaderButton
import com.saporini.mobile_desktop.pos.reservations.ToolbarHeight
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_next_hours
import mobile_desktop.shared.generated.resources.overview_reservations
import mobile_desktop.shared.generated.resources.settings_payments
import kotlin.time.Clock
import kotlin.time.Instant

// POS → Shift: the staff member's own shift. The clock and the buttons for right now, this week's hours and pay, the
// days of the week, and what's coming up. Managers plan everyone's shifts in Admin Hub → Shifts.
@Composable
internal fun MyShiftContent(state: ShiftState, model: ShiftScreenModel, modifier: Modifier = Modifier) {
    var details by remember { mutableStateOf<ShiftItem?>(null) }
    var pending by remember { mutableStateOf<Pair<String, ShiftItem?>?>(null) }
    val board = state.board
    val zone = state.zone
    // The clock moves on between refreshes: minutes counted from when the board arrived.
    var tick by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) { while (true) { delay(10_000); tick = Clock.System.now() } }
    val loadedAt = remember(board) { Clock.System.now() }
    val serverNow = board?.serverNow?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: loadedAt
    val now = serverNow + (tick - loadedAt).coerceAtLeast(kotlin.time.Duration.ZERO)
    val today = now.toLocalDateTime(zone).date
    val days = (0..6).map { state.weekStart.plus(DatePeriod(days = it)) }
    val mine = board?.items.orEmpty().filter { it.userId == state.userId }
    val current = board?.current
    val early = board?.clockInEarlyMinutes ?: 120
    val upcoming = mine.filter { it.status == "SCHEDULED" && it.scheduledEnd?.let { end -> Instant.parse(end) >= now } == true }
        .sortedBy { it.scheduledStart }
    val next = upcoming.firstOrNull()
    val eligible = board?.clockInShift ?: next?.takeIf { canClockIn(it, now, early) }
    val weekItems = mine.filter { it.start.localDate(zone) in days }
    val pay = state.pay?.staff?.firstOrNull { it.userId == state.userId }
    val currency = state.pay?.currency ?: "EUR"
    // Live minutes for an open shift, and for a break that's still going.
    val liveExtra = if (current?.status == "OPEN") (tick - loadedAt).inWholeMinutes.coerceAtLeast(0) else 0
    val weekWorked = weekItems.sumOf { it.workedMinutes } + if (current != null && current.start.localDate(zone) in days) liveExtra else 0
    val weekPlanned = weekItems.filter { it.status != "CANCELLED" }.sumOf { item ->
        val s = item.scheduledStart?.let(Instant::parse); val e = item.scheduledEnd?.let(Instant::parse)
        if (s != null && e != null) (e - s).inWholeMinutes else 0
    }
    val weekBreaks = weekItems.sumOf { it.breakMinutes }
    val worked = weekItems.count { it.startedAt != null }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().then(if (size.isDesktop) Modifier else Modifier.verticalScroll(scroll))
                .padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MyShiftHeader(state, size, today, onDate = { model.date(it) }, onRefresh = { model.clearError(); model.refresh() })
            if (state.error != null && pending == null) ShiftMessage(state.error, error = true, onDismiss = model::clearError)
            if (state.notice != null) ShiftMessage(state.notice, error = false, onDismiss = model::clearError)
            LaunchedEffect(state.notice) { if (state.notice != null) { delay(3_000); model.clearError() } }

            when {
                !state.ready || !state.canSelf -> Box(Modifier.fillMaxWidth().heightIn(min = 320.dp), Alignment.Center) {
                    OverviewEmpty("No shift access", "Your account needs a restaurant, a branch and the “My Shifts” permission. Sign in again after it's given.", Icons.Outlined.Lock)
                }
                board == null && state.loading -> Box(Modifier.fillMaxWidth().heightIn(min = 320.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Kit.Green, strokeWidth = 3.dp)
                }
                board == null -> Box(Modifier.fillMaxWidth().heightIn(min = 320.dp), Alignment.Center) {
                    OverviewEmpty("Couldn't load your shifts", "Check the connection, then try again.", Icons.Outlined.CloudOff) {
                        SmallAction("Try again", Icons.Outlined.Refresh, primary = true) { model.clearError(); model.refresh() }
                    }
                }
                else -> {
                    val cards = listOf<@Composable (Modifier) -> Unit>(
                        { m ->
                            OverviewStatCard(
                                "Worked this week", hoursText(weekWorked),
                                "${if (worked == 1) "1 shift" else "$worked shifts"} · ${hoursText(weekBreaks)} breaks",
                                Kit.Green, m, image = Res.drawable.overview_next_hours, imageScale = 1.3f,
                                progress = if (weekPlanned > 0) weekWorked.toFloat() / weekPlanned else null
                            )
                        },
                        { m ->
                            val wages = pay?.wages.toCents()
                            val tips = pay?.tips.toCents() ?: 0
                            OverviewStatCard(
                                "My pay this week",
                                if (pay == null) "–" else centsText((wages ?: 0) + tips, currency),
                                when {
                                    pay == null -> "Pay couldn't be loaded"
                                    pay.hourlyRate == null && pay.missingRate -> "Your hourly wage isn't set yet"
                                    pay.hourlyRate == null -> "Ask your manager to set your wage"
                                    else -> "${centsText(wages ?: 0, currency)} wages · ${centsText(tips, currency)} tips"
                                },
                                Kit.Amber, m, image = Res.drawable.settings_payments
                            )
                        },
                        { m ->
                            val left = upcoming.count { it.start.localDate(zone) in days }
                            OverviewStatCard(
                                "Planned this week", hoursText(weekPlanned),
                                if (left == 0) "No more shifts this week" else "$left ${if (left == 1) "shift" else "shifts"} still to come",
                                Kit.Blue, m, image = Res.drawable.overview_reservations
                            )
                        },
                        { m ->
                            val onShift = current != null
                            val nextStart = next?.scheduledStart?.let(Instant::parse)
                            OverviewStatCard(
                                if (onShift) "On shift" else "Next shift",
                                when {
                                    onShift -> "Now"
                                    nextStart == null -> "–"
                                    else -> nextStart.toLocalDateTime(zone).let { local ->
                                        if (local.date == today) "Today ${local.time.hhmm()}" else "${local.date.weekdayShort()} ${local.time.hhmm()}"
                                    }
                                },
                                when {
                                    onShift -> current.scheduledEnd?.let { "Until ${it.clock(zone)}" } ?: "Started ${current.startedAt.clock(zone)}"
                                    next == null -> "Nothing planned for 2 weeks"
                                    else -> "Until ${next.scheduledEnd.clock(zone)} · ${hoursText(next.plannedMinutes())}"
                                },
                                Kit.Purple, m, icon = Icons.Outlined.EventAvailable
                            )
                        }
                    )
                    if (size.isDesktop) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { cards.forEach { it(Modifier.weight(1f)) } }
                    } else {
                        val gap = if (size.isPhone) 10.dp else 12.dp
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { cards[0](Modifier.weight(1f)); cards[1](Modifier.weight(1f)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { cards[2](Modifier.weight(1f)); cards[3](Modifier.weight(1f)) }
                    }

                    val nowCard: @Composable (Modifier) -> Unit = { m ->
                        NowCard(m, state, current, eligible, next, now, zone, early, liveExtra, size,
                            onClockIn = { model.clearError(); pending = "clock-in" to eligible },
                            onBreak = { current?.let { model.clearError(); pending = "break" to it } },
                            onEndBreak = { current?.let { model.action(it, "resume", null) {} } },
                            onClockOut = { current?.let { model.clearError(); pending = "close" to it } },
                            onDetails = { details = current })
                    }
                    val week: @Composable (Modifier) -> Unit = { m -> WeekPanel(m, days, mine, today, state.date, zone, weekWorked, weekPlanned, size, current, liveExtra) { details = it } }
                    val comingUp: @Composable (Modifier) -> Unit = { m -> ComingUpPanel(m, upcoming.filter { it.id != current?.id }.take(6), eligible, now, today, zone) { details = it } }
                    val paid: @Composable (Modifier) -> Unit = { m -> WorkedPanel(m, pay, currency, weekItems, zone) { id -> details = mine.firstOrNull { it.id == id } } }

                    if (size.isDesktop) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(0.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                nowCard(Modifier.fillMaxWidth())
                                week(Modifier.fillMaxWidth().weight(1f))
                            }
                            Column(Modifier.weight(0.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                comingUp(Modifier.fillMaxWidth().weight(0.9f))
                                paid(Modifier.fillMaxWidth().weight(1.1f))
                            }
                        }
                    } else {
                        nowCard(Modifier.fillMaxWidth())
                        week(Modifier.fillMaxWidth())
                        comingUp(Modifier.fillMaxWidth())
                        paid(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    details?.let { original ->
        val item = board?.items?.find { it.id == original.id } ?: board?.current?.takeIf { it.id == original.id } ?: original
        // Planning tools stay in Admin Hub: this screen is only about the person's own shift.
        ShiftDetails(item, zone, state.copy(permissions = state.permissions - "SHIFT_MANAGE"), now,
            onDismiss = { details = null }, onEdit = {}, onCorrect = {}, onAction = { action -> details = null; pending = action to item })
    }
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

@Composable
private fun MyShiftHeader(state: ShiftState, size: ScreenSize, today: LocalDate, onDate: (LocalDate) -> Unit, onRefresh: () -> Unit) {
    var dateMenu by remember { mutableStateOf(false) }
    val weekEnd = state.weekStart.plus(DatePeriod(days = 6))
    val title: @Composable () -> Unit = {
        Column {
            Text("My shift", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.sp, color = Kit.Ink)
            Text("${today.longDay()} · your hours, breaks and pay", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
    }
    val controls: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SquareButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous week") { onDate(state.date.minus(DatePeriod(days = 7))) }
            Box {
                HeaderButton("${state.weekStart.shortDay()} – ${weekEnd.shortDay()}", Icons.Outlined.CalendarToday, { dateMenu = true }, Modifier.width(if (size.isPhone) 170.dp else 200.dp))
                DropdownMenu(dateMenu, { dateMenu = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
                    CompactDatePicker(state.date) { onDate(it); dateMenu = false }
                }
            }
            SquareButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next week") { onDate(state.date.plus(DatePeriod(days = 7))) }
            if (today !in state.weekStart..weekEnd) SmallAction("This week", Icons.Outlined.Today) { onDate(today) }
            Surface(onClick = onRefresh, modifier = Modifier.size(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), color = Kit.Green, strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, "Refresh", Modifier.size(18.dp), tint = Kit.Ink)
                }
            }
        }
    }
    if (size.isPhone) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { title(); Box(Modifier.horizontalScroll(rememberScrollState())) { controls() } }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { title() }; controls() }
    }
}

// Right now: the running clock with the shift's bar, and the buttons for what the person can do next.
@Composable
private fun NowCard(
    modifier: Modifier, state: ShiftState, current: ShiftItem?, eligible: ShiftItem?, next: ShiftItem?, now: Instant, zone: TimeZone,
    early: Int, liveExtra: Long, size: ScreenSize,
    onClockIn: () -> Unit, onBreak: () -> Unit, onEndBreak: () -> Unit, onClockOut: () -> Unit, onDetails: () -> Unit
) {
    val status = current?.status ?: "OFF_DUTY"
    val accent = when (status) { "OPEN" -> Kit.Green; "ON_BREAK" -> Kit.Amber; else -> Kit.Grey }
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 1.dp) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            val body: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(
                            when (status) { "OPEN" -> "On duty"; "ON_BREAK" -> "On break"; else -> "Off duty" }, accent,
                            when (status) { "OPEN" -> Icons.Outlined.PlayCircle; "ON_BREAK" -> Icons.Outlined.Coffee; else -> Icons.Outlined.Bedtime }
                        )
                        if (current != null) Text("Clocked in at ${current.startedAt.clock(zone)}", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                    }
                    if (current != null) {
                        val workedNow = current.workedMinutes + liveExtra
                        BigDuration(workedNow)
                        Text(
                            if (status == "ON_BREAK") current.breaks.lastOrNull { it.endedAt == null }?.let { br ->
                                "On break since ${br.startedAt.clock(zone)} · ${hoursText(((now - Instant.parse(br.startedAt)).inWholeMinutes).coerceAtLeast(0))} so far"
                            } ?: "On break"
                            else "Worked so far · breaks ${hoursText(current.breakMinutes)}",
                            fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted
                        )
                        ShiftBar(current, now, zone)
                    } else {
                        Text("You're off duty", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Kit.Ink)
                        Text(
                            when {
                                eligible != null -> "Your ${eligible.scheduledStart.clock(zone)}–${eligible.scheduledEnd.clock(zone)} shift is ready to start."
                                next != null -> "Next: ${next.start.localDate(zone)?.longDay()} · ${next.scheduledStart.clock(zone)}–${next.scheduledEnd.clock(zone)}. " +
                                    (if (early == 0) "Clock-in opens when it starts." else "Clock-in opens ${early.earlyText()} before.")
                                else -> "No shift planned. You can still clock in if your manager asked you to work."
                            },
                            fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Muted
                        )
                    }
                }
            }
            val buttons: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (status) {
                        "OPEN" -> {
                            BigAction("Start break", Icons.Outlined.Coffee, Kit.Amber, !state.busy, onBreak)
                            BigAction("Clock out", Icons.Outlined.StopCircle, Kit.Danger, !state.busy, onClockOut, outlined = true)
                        }
                        "ON_BREAK" -> {
                            BigAction("End break", Icons.Outlined.PlayArrow, Kit.Green, !state.busy, onEndBreak)
                            BigAction("Clock out", Icons.Outlined.StopCircle, Kit.Danger, !state.busy, onClockOut, outlined = true)
                        }
                        else -> {
                            BigAction("Clock in", Icons.Outlined.Login, Kit.Green, !state.busy, onClockIn)
                            Text(if (eligible != null) "For your ${eligible.scheduledStart.clock(zone)} shift" else "Starts a shift that isn't planned",
                                Modifier.fillMaxWidth(), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, textAlign = TextAlign.Center)
                        }
                    }
                    if (current != null) {
                        Text("Shift details", Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onDetails).padding(vertical = 6.dp),
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, textAlign = TextAlign.Center)
                    }
                }
            }
            if (size.isPhone) {
                Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    body(Modifier.fillMaxWidth()); buttons(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.weight(1f).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    body(Modifier.weight(1f)); buttons(Modifier.width(200.dp))
                }
            }
        }
    }
}

// "3h 42m" in big type with small units.
@Composable
private fun BigDuration(minutes: Long) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${minutes / 60}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, color = Kit.Ink)
        Text("h", Modifier.padding(start = 2.dp, bottom = 7.dp, end = 10.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Kit.Muted)
        Text("${minutes % 60}".padStart(2, '0'), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, color = Kit.Ink)
        Text("m", Modifier.padding(start = 2.dp, bottom = 7.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Kit.Muted)
    }
}

// The shift as a bar from its start to its planned end: worked time green, breaks amber, a line at now.
@Composable
private fun ShiftBar(item: ShiftItem, now: Instant, zone: TimeZone) {
    val start = (item.startedAt ?: item.scheduledStart)?.let(Instant::parse) ?: return
    val plannedEnd = item.scheduledEnd?.let(Instant::parse)
    val end = listOfNotNull(plannedEnd, now).max()
    val total = (end - start).inWholeSeconds.coerceAtLeast(1).toFloat()
    fun at(instant: Instant) = ((instant - start).inWholeSeconds / total).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // Drawn rather than laid out, so the card can size itself around it.
        val worked = at(now)
        val breaks = item.breaks.map { br -> at(Instant.parse(br.startedAt)) to at(br.endedAt?.let(Instant::parse) ?: now) }
        Box(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(Kit.Tint)
                .drawBehind {
                    drawRect(Kit.Green.copy(alpha = 0.85f), size = androidx.compose.ui.geometry.Size(size.width * worked, size.height))
                    breaks.forEach { (from, to) ->
                        val w = (size.width * (to - from)).coerceAtLeast(3.dp.toPx())
                        drawRect(Kit.Amber, topLeft = androidx.compose.ui.geometry.Offset(size.width * from, 0f), size = androidx.compose.ui.geometry.Size(w, size.height))
                    }
                }
        )
        Row(Modifier.fillMaxWidth()) {
            Text(item.startedAt.clock(zone), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
            Spacer(Modifier.weight(1f))
            if (plannedEnd != null) {
                val left = (plannedEnd - now).inWholeMinutes
                Text(if (left > 0) "${hoursText(left)} left" else "${hoursText(-left)} over", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                    color = if (left > 0) Kit.Green else Kit.Amber)
                Spacer(Modifier.weight(1f))
                Text(item.scheduledEnd.clock(zone), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
            }
        }
    }
}

@Composable
private fun WeekPanel(
    modifier: Modifier, days: List<LocalDate>, mine: List<ShiftItem>, today: LocalDate, selected: LocalDate, zone: TimeZone,
    worked: Long, planned: Long, size: ScreenSize, current: ShiftItem?, liveExtra: Long, onOpen: (ShiftItem) -> Unit
) {
    OverviewPanel(Icons.Outlined.CalendarViewWeek, "This week", modifier,
        titleExtra = "${hoursText(worked)} worked · ${hoursText(planned)} planned") {
        val tiles: @Composable (Modifier) -> Unit = { m ->
            days.forEach { day ->
                val shifts = mine.filter { it.start.localDate(zone) == day && it.status != "CANCELLED" }.sortedBy { it.start }
                DayTile(m, day, shifts, day == today, zone, current, liveExtra, onOpen)
            }
        }
        if (size.isPhone) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { tiles(Modifier.width(112.dp)) }
        } else {
            Row(Modifier.fillMaxWidth().then(if (size.isDesktop) Modifier.weight(1f) else Modifier), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles(Modifier.weight(1f).then(if (size.isDesktop) Modifier.fillMaxHeight() else Modifier.heightIn(min = 150.dp)))
            }
        }
    }
}

@Composable
private fun DayTile(modifier: Modifier, day: LocalDate, shifts: List<ShiftItem>, isToday: Boolean, zone: TimeZone, current: ShiftItem?, liveExtra: Long, onOpen: (ShiftItem) -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (isToday) Kit.GreenSoft else Kit.Canvas)
            .border(if (isToday) 1.5.dp else 1.dp, if (isToday) Kit.Green else Kit.Border, RoundedCornerShape(10.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(day.weekdayShort().uppercase(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.6.sp, color = if (isToday) Kit.Green else Kit.Muted)
            Spacer(Modifier.weight(1f))
            if (isToday) Text("Today", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Kit.Green)
        }
        Text("${day.day}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 24.sp, color = if (isToday) Kit.Green else Kit.Ink)
        if (shifts.isEmpty()) {
            Text("Day off", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Kit.Faint)
        }
        shifts.forEach { item ->
            val color = shiftTone(item.status)
            val minutes = item.workedMinutes + if (item.id == current?.id) liveExtra else 0
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Color.White)
                    .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(7.dp)).clickable { onOpen(item) }
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(5.dp))
                    Text("${item.start.clock(zone)}–${(item.scheduledEnd ?: item.endedAt).clock(zone)}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = (-0.2).sp, color = Kit.Ink, maxLines = 1, softWrap = false)
                }
                Text(
                    when (item.status) {
                        "OPEN" -> "On duty · ${hoursText(minutes)}"
                        "ON_BREAK" -> "On break"
                        "CLOSED" -> "${hoursText(minutes)} worked"
                        "MISSED" -> "Missed"
                        else -> "${hoursText(item.plannedMinutes())} planned"
                    },
                    fontFamily = Inter(), fontSize = 10.sp, color = if (item.status == "MISSED") Kit.Danger else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ComingUpPanel(modifier: Modifier, upcoming: List<ShiftItem>, eligible: ShiftItem?, now: Instant, today: LocalDate, zone: TimeZone, onOpen: (ShiftItem) -> Unit) {
    OverviewPanel(Icons.Outlined.EventNote, "Coming up", modifier, count = upcoming.size) {
        if (upcoming.isEmpty()) {
            OverviewCompactEmpty("Nothing planned", "New shifts show up here when your manager plans them.", Icons.Outlined.EventAvailable)
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                upcoming.forEach { item ->
                    val day = item.start.localDate(zone) ?: today
                    val daysAway = today.daysUntil(day)
                    ShiftLine(
                        day, "${item.scheduledStart.clock(zone)} – ${item.scheduledEnd.clock(zone)}",
                        listOfNotNull(hoursText(item.plannedMinutes()), item.notes?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        onClick = { onOpen(item) }
                    ) {
                        when {
                            item.id == eligible?.id -> StatusChip("Clock in now", Kit.Green, strong = true)
                            daysAway == 0 -> StatusChip("Today", Kit.Green)
                            daysAway == 1 -> StatusChip("Tomorrow", Kit.Blue)
                            else -> StatusChip("In $daysAway days", Kit.Grey)
                        }
                    }
                }
            }
        }
    }
}

// This week's worked shifts with what each one paid, then the totals.
@Composable
private fun WorkedPanel(modifier: Modifier, pay: StaffPay?, currency: String, weekItems: List<ShiftItem>, zone: TimeZone, onOpen: (String) -> Unit) {
    val lines = pay?.shifts.orEmpty()
    OverviewPanel(Icons.Outlined.Payments, "Worked and paid", modifier, titleExtra = "this week") {
        if (lines.isEmpty() && weekItems.none { it.startedAt != null }) {
            OverviewCompactEmpty("No shifts worked yet", "Your hours and pay add up here as you work.", Icons.Outlined.Schedule)
            return@OverviewPanel
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pay == null) {
                weekItems.filter { it.startedAt != null }.sortedByDescending { it.startedAt }.forEach { item ->
                    ShiftLine(item.start.localDate(zone) ?: return@forEach, "${item.startedAt.clock(zone)} – ${item.endedAt?.clock(zone) ?: "now"}",
                        "${hoursText(item.workedMinutes)} · ${hoursText(item.breakMinutes)} breaks", onClick = { onOpen(item.id) }) {}
                }
            } else {
                lines.sortedByDescending { it.startedAt }.forEach { line ->
                    val date = runCatching { LocalDate.parse(line.date) }.getOrNull() ?: return@forEach
                    ShiftLine(date, "${line.startedAt.clock(zone)} – ${line.endedAt?.clock(zone) ?: "now"}",
                        "${hoursText(line.workedMinutes)}${if (line.breakMinutes > 0) " · ${hoursText(line.breakMinutes)} break" else ""}",
                        onClick = { onOpen(line.shiftId) }) {
                        Column(horizontalAlignment = Alignment.End) {
                            val wages = line.wages.toCents()
                            if (wages == null) StatusChip("No wage", Kit.Amber)
                            else {
                                Text(centsText(wages, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink)
                                line.hourlyRate.toCents()?.let { Text("${centsText(it, currency)}/h", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted) }
                            }
                        }
                    }
                }
            }
        }
        if (pay != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
            ValueLine("Wages · ${hoursText(pay.workedMinutes)}", centsText(pay.wages.toCents() ?: 0, currency))
            ValueLine("Tips on your orders", centsText(pay.tips.toCents() ?: 0, currency))
            ValueLine("Total", centsText(pay.total.toCents() ?: 0, currency), strong = true, valueColor = Kit.Green)
            if (pay.missingRate) Text("Some hours have no wage yet: ask your manager to set it in Admin Hub.", Modifier.padding(horizontal = 4.dp),
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Amber)
        }
    }
}

@Composable
private fun ShiftLine(date: LocalDate, title: String, detail: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Column(
            Modifier.width(36.dp).clip(RoundedCornerShape(7.dp)).background(Kit.Tint).padding(vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(date.weekdayShort().uppercase(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 8.sp, letterSpacing = 0.4.sp, color = Kit.Muted)
            Text("${date.day}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp, color = Kit.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun BigAction(text: String, icon: ImageVector, color: Color, enabled: Boolean, onClick: () -> Unit, outlined: Boolean = false) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp),
        color = if (outlined) Color.White else color, border = if (outlined) BorderStroke(1.dp, color.copy(alpha = 0.5f)) else null
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (outlined) color else Color.White)
            Spacer(Modifier.width(8.dp))
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (outlined) color else Color.White)
        }
    }
}

@Composable
private fun SmallAction(text: String, icon: ImageVector, primary: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.height(ToolbarHeight), shape = RoundedCornerShape(8.dp),
        color = if (primary) Kit.Green else Color.White, border = if (primary) null else BorderStroke(1.dp, Kit.Border)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(17.dp), tint = if (primary) Color.White else Kit.Ink)
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (primary) Color.White else Kit.Ink)
        }
    }
}

@Composable
internal fun SquareButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(20.dp), tint = Kit.Ink) }
    }
}

internal fun shiftTone(status: String): Color = when (status) {
    "OPEN", "CLOSED" -> Kit.Green; "ON_BREAK" -> Kit.Amber; "SCHEDULED" -> Kit.Blue; "MISSED", "CANCELLED" -> Kit.Danger; else -> Kit.Grey
}
internal fun ShiftItem.plannedMinutes(): Long {
    val s = scheduledStart?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0
    val e = scheduledEnd?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0
    return (e - s).inWholeMinutes.coerceAtLeast(0)
}
internal fun String?.clock(zone: TimeZone): String = this?.let { runCatching { Instant.parse(it).toLocalDateTime(zone).time.hhmm() }.getOrNull() } ?: "—"
internal fun String?.localDate(zone: TimeZone): LocalDate? = this?.let { runCatching { Instant.parse(it).toLocalDateTime(zone).date }.getOrNull() }
internal fun LocalTime.hhmm(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
internal fun LocalDate.weekdayShort(): String = dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
internal fun LocalDate.monthShort(): String = month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
internal fun LocalDate.shortDay(): String = "$day ${monthShort()}"
internal fun LocalDate.longDay(): String = "${dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, $day ${month.name.lowercase().replaceFirstChar { it.uppercase() }}"
private fun Int.earlyText(): String = when {
    this % 60 == 0 -> if (this == 60) "1 hour" else "${this / 60} hours"
    else -> "$this minutes"
}
