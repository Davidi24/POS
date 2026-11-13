@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.EdgeScrollbar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import kotlinx.datetime.*
import kotlin.time.Instant

// The month calendar, in the Admin Hub and (read-only) in POS → My shift → Team.

private val Line = Color(0xFFEBEEE8)
private val TodayTint = Color(0xFFF3F8F0)
private val PastTint = Color(0xFFFBFBFA)

@Composable
internal fun MonthOverview(
    anchor: LocalDate, days: List<LocalDate>, items: List<ShiftItem>, zone: TimeZone, today: LocalDate, now: Instant,
    canAdd: Boolean, onDay: (LocalDate) -> Unit, onTodayRow: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = {}, onAdd: (LocalDate) -> Unit
) {
    // A day's cell gets narrow on tablets (about 80 dp at 600 wide), so it shows less there: see CellRoom.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val room = when {
        maxWidth / 7 >= 165.dp -> CellRoom.FULL
        maxWidth / 7 >= 118.dp -> CellRoom.MEDIUM
        else -> CellRoom.SMALL
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(36.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
            val todayColumn = if (today in days) today.dayOfWeek.ordinal else -1
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday").forEachIndexed { index, name ->
                Text(if (room == CellRoom.SMALL) name.take(3) else name, Modifier.weight(1f).padding(start = if (room == CellRoom.SMALL) 8.dp else 12.dp),
                    fontFamily = Inter(), fontWeight = if (index == todayColumn) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 12.sp, color = if (index == todayColumn) Kit.Green else Kit.Muted, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
        // The page scrolls, so every week gets a roomy row. Each day shows who works (faces), parts of the day and hours;
        // the names and times are one click away in the day panel (the user preferred this lighter look).
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().height(146.dp).then(if (today in week) Modifier.onGloballyPositioned(onTodayRow) else Modifier)) {
                week.forEach { day ->
                    val dayShifts = items.filter { it.start.localDate(zone) == day && it.status != "CANCELLED" }.sortedBy { it.start }
                    MonthCell(Modifier.weight(1f).fillMaxHeight(), day, anchor, today, dayShifts, zone, now, canAdd && day >= today, room, { onDay(day) }) { onAdd(day) }
                }
            }
        }
    }
    }
}

/**
 * How much a month cell can show. FULL: everything. MEDIUM (a 1024 tablet): no "Today" word (the green circle and frame
 * say it) and no hours. SMALL (600–800 wide): short weekday names, the count under the date, two faces and plain part counts.
 */
private enum class CellRoom { FULL, MEDIUM, SMALL }

@Composable
private fun MonthCell(
    modifier: Modifier, day: LocalDate, anchor: LocalDate, today: LocalDate, shifts: List<ShiftItem>, zone: TimeZone, now: Instant, canAdd: Boolean,
    room: CellRoom, onClick: () -> Unit, onAdd: () -> Unit
) {
    val small = room == CellRoom.SMALL
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val inMonth = day.month == anchor.month
    val isToday = day == today
    val people = shifts.map { it.userName }.distinct()
    val looks = shifts.map { lookOf(it, now) }
    val problems = looks.count { it == ShiftLook.MISSED || it == ShiftLook.NO_CLOCK_IN || it == ShiftLook.LATE }
    val onNow = looks.count { it == ShiftLook.ON_DUTY || it == ShiftLook.ON_BREAK }
    Column(
        modifier.hoverable(hover).clickable(enabled = inMonth, onClick = onClick)
            .background(when { !inMonth -> Color(0xFFFAFAF8); isToday -> TodayTint; hovered -> Color(0xFFF9FBF8); else -> Color.White })
            .drawBehind {
                drawLine(Line, Offset(0f, 0f), Offset(0f, size.height), 1f)
                drawLine(Line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
                // Today stands out with a green frame (and a green top edge); hovering a day shows a lighter one.
                if (isToday) {
                    val w = 2.dp.toPx()
                    drawRect(Kit.Green, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                    drawRect(Kit.Green, size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()))
                } else if (hovered && inMonth) drawRect(Kit.Green.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
            .padding(if (small) 7.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (small) 5.dp else 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (small) 26.dp else 28.dp).clip(CircleShape).background(if (isToday) Kit.Green else Color.Transparent), Alignment.Center) {
                Text("${day.day}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (small) 13.sp else 14.sp,
                    color = when { isToday -> Color.White; !inMonth -> Kit.Faint; day < today -> Kit.Muted; else -> Kit.Ink })
            }
            if (isToday && room == CellRoom.FULL) Text("Today", Modifier.padding(start = 6.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
            if (problems > 0 && inMonth) {
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Kit.Danger))
            }
            Spacer(Modifier.weight(1f))
            when {
                !inMonth -> {}
                canAdd && hovered -> Box(Modifier.size(if (small) 22.dp else 26.dp).clip(RoundedCornerShape(7.dp)).background(Kit.Green).clickable(onClick = onAdd), Alignment.Center) {
                    Icon(Icons.Outlined.Add, "Add shifts on ${day.shortDay()}", Modifier.size(16.dp), tint = Color.White)
                }
                !small -> CellCount(day, today, onNow, people.size)
            }
        }
        if (small && inMonth && (onNow > 0 || people.isNotEmpty())) CellCount(day, today, onNow, people.size)
        if (inMonth && shifts.isNotEmpty()) {
            // Days that are done fade back (user rule: "light"), so today and what's coming stand out.
            Column(Modifier.weight(1f).alpha(if (day < today) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(if (small) 5.dp else 7.dp)) {
                if (small) AvatarStack(people, 2, 24.dp) else AvatarStack(people, 4, 30.dp)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (small) 5.dp else 4.dp)) {
                    DayPart.entries.forEach { part ->
                        val count = shifts.count { item -> item.localStart(zone)?.let(::dayPartOf) == part }
                        if (count > 0) PartCount(part, count, plain = small)
                    }
                    if (room == CellRoom.FULL) {
                        Spacer(Modifier.weight(1f))
                        Text(hoursText(shifts.sumOf { shiftMinutes(it) }), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = Kit.Muted, maxLines = 1)
                    }
                }
            }
        } else if (inMonth) {
            // A day without shifts says so plainly (user, 2026-10-31: "where it has no data it should say so").
            Column(Modifier.weight(1f).fillMaxWidth().alpha(if (day < today) 0.6f else 1f), verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally) {
                // A shift runs that day and nobody's on it yet: that's something to do.
                val waiting = canAdd && ShiftTemplates.all.collectAsState().value.runningOn(day).isNotEmpty()
                Icon(Icons.Outlined.EventBusy, null, Modifier.size(if (small) 16.dp else 18.dp), tint = if (waiting) Kit.Amber else Kit.Faint)
                Text(if (waiting) "Nobody yet" else "No shifts", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = if (waiting) Kit.Amber else Kit.Faint, maxLines = 1)
            }
        }
    }
}

/** How many work in a day: "N on", or green with a pulse while some are on now. */
@Composable
private fun CellCount(day: LocalDate, today: LocalDate, onNow: Int, people: Int) {
    if (onNow > 0) Row(Modifier.clip(RoundedCornerShape(50)).background(Kit.Green).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PulseDot(Color.White)
        Text("$onNow on", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color.White, maxLines = 1, softWrap = false)
    } else if (people > 0) Text("$people on", Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(horizontal = 7.dp, vertical = 2.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = if (day < today) Kit.Muted else Kit.Ink, maxLines = 1, softWrap = false)
}

/** A part of the day and how many start then; [plain] (narrow cells) drops the tinted box. */
@Composable
private fun PartCount(part: DayPart, count: Int, plain: Boolean = false) {
    val color = dayPartColor(part)
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(if (plain) Color.Transparent else color.copy(alpha = 0.12f))
        .padding(horizontal = if (plain) 0.dp else 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(dayPartIcon(part), part.label, Modifier.size(12.dp), tint = color)
        Text("$count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = color)
    }
}

/** Overlapping avatars of up to [max] people, then "+N". */
@Composable
internal fun AvatarStack(names: List<String>, max: Int, size: Dp) {
    val shown = names.take(max)
    val extra = names.size - shown.size
    val step = size * 0.7f
    val count = shown.size + if (extra > 0) 1 else 0
    Box(Modifier.width(if (count == 0) 0.dp else size + step * (count - 1)).height(size)) {
        shown.forEachIndexed { i, name ->
            Box(Modifier.offset(x = step * i).size(size).clip(CircleShape).background(Color.White).padding(1.5.dp)) { ShiftAvatar(name, size - 3.dp) }
        }
        if (extra > 0) {
            Box(Modifier.offset(x = step * shown.size).size(size).clip(CircleShape).background(Color.White).padding(1.5.dp)) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Kit.Tint), Alignment.Center) {
                    Text("+$extra", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp, color = Kit.Muted)
                }
            }
        }
    }
}

/** What a shift is right now, as a small pill (a pulsing dot while on duty). */
@Composable
internal fun LookPill(look: ShiftLook) {
    val color = if (look == ShiftLook.ON_DUTY) Kit.Green else paletteOf(look).detail
    Row(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (look == ShiftLook.ON_DUTY) PulseDot(color) else Icon(iconOf(look), null, Modifier.size(11.dp), tint = color)
        Text(look.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = color, maxLines = 1)
    }
}

// ---------- Small pieces ----------

@Composable
internal fun SoftCount(count: Int) {
    Text("$count", Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(horizontal = 7.dp, vertical = 1.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
}

@Composable
internal fun PulseDot(color: Color) {
    val pulse = rememberInfiniteTransition(label = "on-duty")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "on-duty-alpha")
    Box(Modifier.size(6.dp).alpha(alpha).clip(CircleShape).background(color))
}

/** Worked minutes for a shift that started, otherwise its planned length. */
internal fun shiftMinutes(item: ShiftItem) = if (item.status == "CLOSED" || item.status == "OPEN" || item.status == "ON_BREAK") item.workedMinutes else item.plannedMinutes()
