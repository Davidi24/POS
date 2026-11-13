@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.CenterChosen
import com.saporini.mobile_desktop.core.components.fadingEdges
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.datetime.*
import kotlin.time.Instant
import org.jetbrains.compose.resources.painterResource

// Admin Hub → Shifts → a day of the month, laid out by shift (user, 2026-10-31: "it should be the shift and a plus to add
// someone in it, simple"): each usual shift with who works it and a + to put someone on it, then "Other times" for
// shifts at their own hours (its + adds someone for any hours that day, no usual shift).

@Composable
internal fun ShiftDayDialog(
    day: LocalDate, firstDay: LocalDate, lastDay: LocalDate, today: LocalDate, now: Instant, zone: TimeZone, state: ShiftState,
    people: List<ShiftStaff>, items: List<ShiftItem>, message: String?,
    onDay: (LocalDate) -> Unit, onDismiss: () -> Unit,
    onOpen: (ShiftItem) -> Unit, onEdit: (ShiftItem) -> Unit, onRemove: (ShiftItem) -> Unit, onAttendance: (ShiftItem) -> Unit,
    onQuickAdd: (ShiftStaff, ShiftTemplate) -> Unit, onAddAt: (ShiftStaff, LocalTime, LocalTime) -> Unit, onPlan: () -> Unit,
    // POS → My shift → Team: no role bar, just one list of who works that day (user, 2026-10-31).
    listOnly: Boolean = false
) {
    val templates by ShiftTemplates.all.collectAsState()
    val roles by ShiftTemplates.roles.collectAsState()
    val dayItems = items.filter { it.start.localDate(zone) == day && it.status != "CANCELLED" }.sortedBy { it.start }
    val byId = people.associateBy { it.id }
    val canAdd = state.canManage && !state.busy && day >= today
    val minutes = dayItems.sumOf { shiftMinutes(it) }
    val working = dayItems.map { it.userId }.distinct().size
    // Who already works that day, and when (shown, muted, in the + lists).
    val busyAt = dayItems.groupBy { it.userId }.mapValues { (_, shifts) -> shifts.joinToString(", ") { "${it.start.clock(zone)}–${(it.scheduledEnd ?: it.endedAt).clock(zone)}" } }
    // Only the shifts that run this day (their days are set in Plan shifts, user 2026-10-31). Someone sits under the first of
    // them with their times that's for them; anyone else is at specific hours.
    val dayTemplates = templates.runningOn(day)
    val home = dayItems.associateWith { item -> dayTemplates.firstOrNull { t -> item.matches(t, zone) && byId[item.userId]?.let(t::fits) == true } }
    val usual = dayTemplates.associate { t -> t.id to dayItems.filter { home[it]?.id == t.id } }
    val other = dayItems.filter { home[it] == null }
    val inRole = { userId: String, role: TemplateRoleDto -> byId[userId]?.roles?.any { it.equals(role.name, ignoreCase = true) } == true }
    val outside = dayItems.filter { item -> roles.isNotEmpty() && roles.none { inRole(item.userId, it) } }
    // The bar on top: who works, role by role, as a table or on a timeline, picked in a dropdown with the table first
    // (user, 2026-10-31). Until a place is picked it's the first role with someone working that day, or everyone on the timeline.
    var timeline by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    val tab = picked?.takeIf { key -> (key == AllTab && timeline) || (key == OthersTab && outside.isNotEmpty()) || roles.any { it.id == key } }
        ?: if (timeline) AllTab else roles.firstOrNull { role -> dayItems.any { inRole(it.userId, role) } }?.id ?: roles.firstOrNull()?.id ?: AllTab
    var specificFor by remember(day) { mutableStateOf<List<ShiftStaff>?>(null) }
    AppDialog(
        "${day.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${day.day} ${day.month.name.lowercase().replaceFirstChar { it.uppercase() }}" +
            if (day == today) " · Today" else "",
        onDismiss, maxWidth = 1000.dp, height = 780.dp,
        subtitle = if (dayItems.isEmpty()) "Nobody works yet" else "$working working · ${hoursText(minutes)}",
        buttons = {
            if (state.canManage && day >= today) KitButton("Plan shifts", onPlan, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.EditCalendar)
            KitButton("Done", onDismiss)
        }
    ) {
        DayStrip(day, firstDay, lastDay, dayItems, now, onDay)
        // A problem (a shift that couldn't be added, say) shows here, inside the open dialog.
        message?.let {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Danger.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(17.dp), tint = Kit.Danger)
                Text(it, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Danger)
            }
        }
        if (listOnly) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ViewPicker(timeline) { timeline = it } }
        else DayBar(roles, roles.associate { role -> role.id to dayItems.filter { inRole(it.userId, role) }.map { it.userId }.distinct().size },
            outside.map { it.userId }.distinct().size, working, tab, timeline, onTab = { picked = it }, onView = { timeline = it })
        if (timeline) {
            val placed = mutableSetOf<String>()
            val groups = buildList {
                roles.forEach { role ->
                    val shifts = dayItems.filter { inRole(it.userId, role) && it.userId !in placed }
                    if (shifts.isNotEmpty()) add(Triple(role.id, role.name, shifts))
                    placed += shifts.map { it.userId }
                }
                dayItems.filter { it.userId !in placed }.takeIf { it.isNotEmpty() }?.let { add(Triple(OthersTab, if (roles.isEmpty()) "Team" else "Others", it)) }
            }
            DayTimeline(groups.filter { tab == AllTab || it.first == tab }.map { it.second to it.third }, day, today, now, zone, onOpen)
        } else if (listOnly) {
            if (dayItems.isEmpty()) NoShiftsHere("No shifts this day")
            else Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 3.dp) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    dayItems.forEach { item ->
                        ShiftPersonRow(item, byId[item.userId], zone, now, state, ownTimes = true, onOpen = { onOpen(item) }, onEdit = {}, onRemove = {}, onAttendance = {})
                    }
                }
            }
        } else {
            val tabRole = roles.firstOrNull { it.id == tab }
            val mine = { item: ShiftItem ->
                when {
                    tab == OthersTab -> item in outside
                    tabRole != null -> inRole(item.userId, tabRole)
                    else -> true
                }
            }
            val tabPeople = when {
                tab == OthersTab -> emptyList()
                tabRole != null -> people.filter { inRole(it.id, tabRole) }
                else -> people
            }
            val tabTemplates = when {
                tab == OthersTab -> emptyList()
                tabRole != null -> dayTemplates.filter { t -> t.roles.isEmpty() || t.roles.any { it.id == tabRole.id } }
                else -> dayTemplates
            }
            val tabOther = other.filter(mine)
            val row: @Composable (ShiftItem, Boolean) -> Unit = { item, ownTimes ->
                ShiftPersonRow(item, byId[item.userId], zone, now, state, ownTimes, onOpen = { onOpen(item) }, onEdit = { onEdit(item) },
                    onRemove = { onRemove(item) }, onAttendance = { onAttendance(item) })
            }
            // By shift, those with people on them first, then one "No shift" for everyone else (user, 2026-10-31). A day that's
            // gone shows only the shifts that were worked.
            val shiftCards = buildList<Pair<Int, @Composable () -> Unit>> {
                tabTemplates.forEach { template ->
                    val shifts = usual[template.id].orEmpty().filter(mine)
                    if (canAdd || shifts.isNotEmpty()) add(shifts.size to {
                        // Who it's for shows under its times, and its + offers only them (user, 2026-10-31).
                        ShiftSection(template.name, "${template.times} · ${hoursText(template.minutes)} · ${template.forWhom}", shifts.size,
                            add = if (canAdd) { { AddToShiftButton(template.name, template.times, tabPeople.filter(template::fits), shifts.map { it.userId }.toSet(), busyAt) { onQuickAdd(it, template) } } } else null
                        ) { shifts.forEach { row(it, false) } }
                    })
                }
            }.sortedBy { (count, _) -> count == 0 }.map { it.second }
            val sections = if ((canAdd && tab != OthersTab) || tabOther.isNotEmpty()) shiftCards + @Composable {
                ShiftSection("No shift", "Their own hours that day", tabOther.size,
                    add = if (canAdd && tabPeople.isNotEmpty()) { { KitButton("Add now", { specificFor = tabPeople }, icon = Icons.Outlined.Add) } } else null
                ) { tabOther.forEach { row(it, true) } }
            } else shiftCards
            // Each shift is its own white card, with a line between one card and the next (user, 2026-10-31: "the cards were
            // perfect: the Morning card, then a line, then the next card").
            sections.forEachIndexed { index, section ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border),
                    shadowElevation = 3.dp) { section() }
            }
            if (!canAdd && sections.isEmpty()) NoShiftsHere(if (dayItems.isEmpty()) "No shifts this day" else "Nobody from here that day")
        }
    }
    specificFor?.let { who ->
        SpecificHoursDialog(day, who, busyAt, onDismiss = { specificFor = null }) { person, start, end ->
            specificFor = null
            onAddAt(person, start, end)
        }
    }
}

@Composable
private fun NoShiftsHere(text: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(vertical = 26.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.EventBusy, null, Modifier.size(30.dp), tint = Kit.Faint)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
    }
}

private const val OthersTab = "others"
private const val AllTab = "all"

/** Who works, role by role with how many (on the timeline "Everyone" first), and the Table | Timeline dropdown. */
@Composable
private fun DayBar(roles: List<TemplateRoleDto>, counts: Map<String, Int>, others: Int, working: Int, tab: String, timeline: Boolean,
                   onTab: (String) -> Unit, onView: (Boolean) -> Unit) {
    val scroll = rememberScrollState()
    val spans = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    CenterChosen(scroll, tab, spans)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().fadingEdges(scroll).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (timeline) DayBarPlace("Everyone", working, tab == AllTab, Modifier.onGloballyPositioned { spans[AllTab] = it.positionInParent().x.toInt() to it.size.width },
                    { Icon(Icons.Outlined.Groups, null, Modifier.size(22.dp), tint = if (tab == AllTab) Kit.Green else Kit.Muted) }) { onTab(AllTab) }
                roles.forEach { role ->
                    DayBarPlace(role.name, counts[role.id] ?: 0, tab == role.id, Modifier.onGloballyPositioned { spans[role.id] = it.positionInParent().x.toInt() to it.size.width },
                        { Image(painterResource(categoryPicture(role).image), null, Modifier.size(26.dp), contentScale = ContentScale.Fit) }) { onTab(role.id) }
                }
                if (others > 0) DayBarPlace("Others", others, tab == OthersTab, Modifier.onGloballyPositioned { spans[OthersTab] = it.positionInParent().x.toInt() to it.size.width },
                    { Icon(Icons.Outlined.Groups, null, Modifier.size(22.dp), tint = Kit.Muted) }) { onTab(OthersTab) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
        }
        ViewPicker(timeline, onView)
    }
}

/** Table or Timeline, in a small dropdown; the table is the default. */
@Composable
private fun ViewPicker(timeline: Boolean, onPick: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val iconOf = { onTimeline: Boolean -> if (onTimeline) Icons.Outlined.ViewTimeline else Icons.Outlined.TableChart }
    Box {
        Row(
            Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(10.dp))
                .clickable { open = true }.padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(iconOf(timeline), null, Modifier.size(18.dp), tint = Kit.Ink)
            Text(if (timeline) "Timeline" else "Table", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = Kit.Muted)
        }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp,
            modifier = Modifier.width(200.dp)) {
            listOf(false to "Table", true to "Timeline").forEach { (value, label) ->
                val chosen = value == timeline
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { open = false; onPick(value) }.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(iconOf(value), null, Modifier.size(18.dp), tint = if (chosen) Kit.Green else Kit.Ink)
                    Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.sp,
                        color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1)
                    if (chosen) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Kit.Green)
                }
            }
        }
    }
}

@Composable
private fun DayBarPlace(name: String, count: Int, chosen: Boolean, modifier: Modifier, picture: @Composable () -> Unit, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            picture()
            Text(name, fontFamily = Inter(), fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1)
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (chosen) Kit.Green else if (count > 0) Kit.Tint else Color.Transparent).padding(horizontal = 7.dp, vertical = 1.dp), Alignment.Center) {
                Text("$count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (chosen) Color.White else if (count > 0) Kit.Ink else Kit.Faint)
            }
        }
        Box(Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().height(2.5.dp).background(if (chosen) Kit.Green else Color.Transparent))
    }
}

private class TimelineBar(val item: ShiftItem, val from: Int, val to: Int)

/**
 * Everyone working a day on the clock (user, 2026-10-31: "a Timeline button which shows all users inside"): a line per person
 * under their role, a bar per shift in the colour of what it is now, the hours on top and a line at the moment now. A bar opens
 * that shift. On a phone it slides sideways.
 */
@Composable
internal fun DayTimeline(groups: List<Pair<String, List<ShiftItem>>>, day: LocalDate, today: LocalDate, now: Instant, zone: TimeZone, onOpen: (ShiftItem) -> Unit) {
    val dayStart = remember(day, zone) { day.atStartOfDayIn(zone) }
    val minuteOf = { iso: String? -> iso?.let { runCatching { (Instant.parse(it) - dayStart).inWholeMinutes.toInt() }.getOrNull() } }
    val nowMinute = (now - dayStart).inWholeMinutes.toInt()
    val barOf = { item: ShiftItem ->
        minuteOf(item.start)?.let { from ->
            TimelineBar(item, from, (minuteOf(item.scheduledEnd ?: item.endedAt) ?: if (item.active) nowMinute else from + 60).coerceAtLeast(from + 15))
        }
    }
    val bars = groups.flatMap { it.second }.mapNotNull(barOf)
    if (bars.isEmpty()) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(vertical = 26.dp, horizontal = 16.dp), Alignment.Center) {
            Text("Nobody works this day", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
        }
        return
    }
    val axisFrom = bars.minOf { it.from } / 60 * 60
    val axisTo = maxOf((bars.maxOf { it.to } + 59) / 60 * 60, axisFrom + 8 * 60)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val phone = maxWidth < 560.dp
        val label = if (phone) 122.dp else 190.dp
        val total = max(maxWidth, 700.dp)
        // Some room on the right, so the last hour's label fits.
        val chart = total - label - 28.dp
        val perMinute = chart / (axisTo - axisFrom).toFloat()
        val perHour = perMinute * 60f
        val hours = if (perHour >= 46.dp) 1 else if (perHour >= 26.dp) 2 else 3
        val ticks = (axisFrom..axisTo step hours * 60).toList()
        val xOf = { minute: Int -> perMinute * (minute - axisFrom).toFloat() }
        val showNow = day == today && nowMinute in axisFrom..axisTo
        val lineColor = Kit.RowBorder
        val nowColor = Kit.Danger
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 3.dp) {
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Column(
                        Modifier.width(total).drawWithContent {
                            ticks.forEach { tick ->
                                val x = (label + xOf(tick)).toPx()
                                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                            }
                            drawContent()
                            if (showNow) {
                                val x = (label + xOf(nowMinute)).toPx()
                                drawLine(nowColor, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                                drawCircle(nowColor, 3.5.dp.toPx(), Offset(x, 3.5.dp.toPx()))
                            }
                        }
                    ) {
                        Box(Modifier.width(total).height(34.dp)) {
                            ticks.forEach { tick ->
                                val hour = tick / 60 % 24
                                Text("${if (hour < 10) "0" else ""}$hour:00", Modifier.offset(x = label + xOf(tick) - 20.dp).width(40.dp).align(Alignment.CenterStart), fontFamily = Inter(),
                                    fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, textAlign = TextAlign.Center, maxLines = 1)
                            }
                        }
                        groups.forEach { (group, shifts) ->
                            val rows = shifts.groupBy { it.userId }.values.sortedBy { it.minOfOrNull { item -> item.start.orEmpty() } }
                            Row(Modifier.width(total).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(group, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
                                Text("${rows.size}", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.5.sp, color = Kit.Muted)
                            }
                            rows.forEach { own ->
                                Row(Modifier.width(total).height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Row(Modifier.width(label).clickable { onOpen(own.first()) }.padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ShiftAvatar(own.first().userName, 28.dp)
                                        Text(own.first().userName, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Box(Modifier.width(chart).fillMaxHeight()) {
                                        own.mapNotNull(barOf).forEach { bar ->
                                            val palette = paletteOf(lookOf(bar.item, now))
                                            val width = perMinute * (bar.to - bar.from).toFloat()
                                            Box(
                                                Modifier.offset(x = xOf(bar.from)).width(width).fillMaxHeight().padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(palette.fill)
                                                    .border(1.dp, palette.border, RoundedCornerShape(8.dp)).clickable { onOpen(bar.item) }.padding(horizontal = 8.dp),
                                                Alignment.CenterStart
                                            ) {
                                                if (width >= 84.dp) Text("${bar.item.start.clock(zone)}–${(bar.item.scheduledEnd ?: bar.item.endedAt).clock(zone)}", fontFamily = Inter(),
                                                    fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = palette.title, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // What the colours mean, only for what's on this day.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bars.map { lookOf(it.item, now) }.distinct().sortedBy { it.ordinal }.forEach { look ->
                    val palette = paletteOf(look)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(width = 16.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(palette.fill).border(1.dp, palette.border, RoundedCornerShape(3.dp)))
                        Text(look.label, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A shift starts and ends at the usual shift's times. */
private fun ShiftItem.matches(template: ShiftTemplate, zone: TimeZone): Boolean =
    localStart(zone) == template.start && scheduledEnd?.let { runCatching { Instant.parse(it).toLocalDateTime(zone).time }.getOrNull() } == template.end

/** The day's arrows and what the day looks like right now: who's on and what needs a look. */
@Composable
private fun DayStrip(day: LocalDate, firstDay: LocalDate, lastDay: LocalDate, shifts: List<ShiftItem>, now: Instant, onDay: (LocalDate) -> Unit) {
    val looks = shifts.map { lookOf(it, now) }
    val onNow = looks.count { it == ShiftLook.ON_DUTY || it == ShiftLook.ON_BREAK }
    val review = looks.count { it == ShiftLook.LATE || it == ShiftLook.NO_CLOCK_IN || it == ShiftLook.MISSED }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DayArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous day", day > firstDay) { onDay(day.minus(DatePeriod(days = 1))) }
        DayArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next day", day < lastDay) { onDay(day.plus(DatePeriod(days = 1))) }
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onNow > 0) StripChip(Icons.Outlined.PlayCircle, "$onNow on now", Kit.Green)
            if (review > 0) StripChip(Icons.Outlined.ReportProblem, "$review need a look", Kit.Danger)
        }
    }
}

@Composable
private fun DayArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp).alpha(if (enabled) 1f else 0.4f), shape = RoundedCornerShape(8.dp),
        color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(19.dp), tint = Kit.Ink) }
    }
}

@Composable
private fun StripChip(icon: ImageVector, text: String, color: Color) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f)).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = color, maxLines = 1)
    }
}

// ---------- A shift and who works it ----------

/**
 * One shift of the day, inside its own card: its name, times and how many, with [add] on the right, a line, then its people.
 * An empty shift says so in one quiet line. No colours and no icons (user, 2026-10-31: "more modern", "no icons for
 * Morning, Lunch…").
 */
@Composable
private fun ShiftSection(title: String, detail: String, count: Int, add: (@Composable () -> Unit)?, people: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1)
                    if (count > 0) SoftCount(count)
                }
                Text(detail, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            add?.invoke()
        }
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(Kit.RowBorder))
        if (count == 0) Text("Nobody yet", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint)
        else Column(Modifier.padding(vertical = 4.dp), content = people)
    }
}

/** The green + of a shift: who can be put on it (people already working that day come last, with their times). */
@Composable
private fun AddToShiftButton(shift: String, times: String, people: List<ShiftStaff>, onShift: Set<String>, busyAt: Map<String, String>, onPick: (ShiftStaff) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Box {
        AddButton("Add someone to $shift") { open = true; query = "" }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp,
            modifier = Modifier.width(290.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add to $shift · $times", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                if (people.size > 6) SearchField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Find someone", height = 38.dp)
            }
            val choices = people.filter { it.id !in onShift && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
                .sortedWith(compareBy({ it.id in busyAt }, { staffGroupOf(it.roles).order }, { it.name.lowercase() }))
            if (choices.isEmpty()) Text(if (query.isBlank()) "Everyone is on this shift." else "Nobody matches.", Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            choices.forEach { person ->
                val busy = busyAt[person.id]
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { open = false; onPick(person) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    ShiftAvatar(person.name, 30.dp)
                    Column(Modifier.weight(1f)) {
                        Text(person.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(if (busy != null) "Works $busy" else roleName(person.roles), fontFamily = Inter(), fontSize = 11.sp, letterSpacing = 0.sp,
                            color = if (busy != null) Kit.Amber else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Outlined.Add, null, Modifier.size(17.dp), tint = Kit.Green)
                }
            }
        }
    }
}

/** A round green +. */
@Composable
private fun AddButton(description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(34.dp), shape = CircleShape, color = Kit.Green) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, description, Modifier.size(20.dp), tint = Color.White) }
    }
}

/**
 * Someone on a shift: face, name, role and hours (or their own times, under "Other times"), what it is now when it isn't
 * just planned, and change / remove (planned) or fix the hours (worked).
 */
@Composable
private fun ShiftPersonRow(
    item: ShiftItem, person: ShiftStaff?, zone: TimeZone, now: Instant, state: ShiftState, ownTimes: Boolean,
    onOpen: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit, onAttendance: () -> Unit
) {
    val look = lookOf(item, now)
    val group = staffGroupOf(person?.roles.orEmpty())
    val ended = item.scheduledEnd?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { it < now } == true
    val canChange = state.canManage && !state.busy && item.status == "SCHEDULED" && look == ShiftLook.PLANNED
    val canFix = state.canManage && !state.busy && (item.status == "CLOSED" || item.status == "MISSED" || (item.status == "SCHEDULED" && ended))
    val narrow = LocalNarrowDialog.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ShiftAvatar(item.userName, 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.userName, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(groupColor(group)))
                Text(listOfNotNull(roleName(person?.roles.orEmpty()), "${item.start.clock(zone)}–${(item.scheduledEnd ?: item.endedAt).clock(zone)}".takeIf { ownTimes },
                    hoursText(shiftMinutes(item))).joinToString(" · "), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // A phone has no room beside the name for what it is now.
            if (narrow && look != ShiftLook.PLANNED) Box(Modifier.padding(top = 2.dp)) { LookPill(look) }
        }
        if (!narrow && look != ShiftLook.PLANNED) LookPill(look)
        if (canChange) {
            RowAction(Icons.Outlined.Edit, "Change ${item.userName}'s shift", Kit.Ink, onEdit)
            RowAction(Icons.Outlined.DeleteOutline, "Remove ${item.userName} from this day", Kit.Danger, onRemove)
        } else if (canFix) {
            RowAction(Icons.Outlined.FactCheck, if (item.status == "CLOSED") "Correct ${item.userName}'s hours" else "Record ${item.userName}'s hours", Kit.Ink, onAttendance)
        }
    }
}

@Composable
private fun RowAction(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).clickable(onClick = onClick), Alignment.Center) {
        Icon(icon, description, Modifier.size(17.dp), tint = tint)
    }
}

// ---------- Someone at other times ----------

/** Puts one person on [day] for specific hours, without a usual shift: from, until, who, then Add. */
@Composable
private fun SpecificHoursDialog(day: LocalDate, people: List<ShiftStaff>, busyAt: Map<String, String>, onDismiss: () -> Unit,
                                onAdd: (ShiftStaff, LocalTime, LocalTime) -> Unit) {
    var chosen by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalTime(12, 0)) }
    var end by remember { mutableStateOf(LocalTime(16, 0)) }
    val person = people.firstOrNull { it.id == chosen }
    AppDialog(
        "Add for specific hours", onDismiss, maxWidth = 520.dp,
        subtitle = "${day.weekdayShort()} ${day.shortDay()} · no usual shift",
        buttons = {
            KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
            KitButton(person?.let { "Add ${it.name.substringBefore(' ')} · ${start.hhmm()}–${end.hhmm()}" } ?: "Pick someone", { person?.let { onAdd(it, start, end) } },
                icon = Icons.Outlined.Add, enabled = person != null && start != end)
        }
    ) {
        FieldPair(
            { m -> ShiftTimeField("From", start, m) { start = it } },
            { m -> ShiftTimeField("Until", end, m, hint = if (end <= start) "The next day" else null) { end = it } }
        )
        if (people.size > 6) SearchField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Find someone", height = 40.dp)
        val shown = people.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy({ it.id in busyAt }, { staffGroupOf(it.roles).order }, { it.name.lowercase() }))
        ScrollColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEach { option ->
                val selected = option.id == chosen
                val busy = busyAt[option.id]
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) Kit.GreenSoft else Color.White)
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.Border, RoundedCornerShape(10.dp))
                        .clickable { chosen = if (selected) null else option.id }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShiftAvatar(option.name, 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(option.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (busy != null) "Works $busy" else roleName(option.roles), fontFamily = Inter(), fontSize = 11.sp,
                            color = if (busy != null) Kit.Amber else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(20.dp).clip(CircleShape).background(if (selected) Kit.Green else Color.White)
                        .border(1.5.dp, if (selected) Kit.Green else Kit.Border, CircleShape), Alignment.Center) {
                        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = Color.White)
                    }
                }
            }
            if (shown.isEmpty()) Text("Nobody matches.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
    }
}

/** "Waiter", "Head chef": the person's first role, readable. */
internal fun roleName(roles: List<String>): String =
    roles.maxByOrNull { ShiftVisibility.rankOf(it) }?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Team"
