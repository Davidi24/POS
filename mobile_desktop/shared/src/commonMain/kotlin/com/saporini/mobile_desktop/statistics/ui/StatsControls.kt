package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.LaunchedEffect
import com.saporini.mobile_desktop.core.components.dayCountText
import com.saporini.mobile_desktop.core.components.periodLabel
import com.saporini.mobile_desktop.statistics.periodRange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.DateButtonFace
import com.saporini.mobile_desktop.core.components.DayCalendar
import com.saporini.mobile_desktop.core.components.RangeCalendar
import com.saporini.mobile_desktop.core.components.WeekCalendar
import com.saporini.mobile_desktop.core.components.periodOrMonthLabel
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.statistics.MAX_STATS_DAYS
import com.saporini.mobile_desktop.statistics.StatsBranch
import com.saporini.mobile_desktop.statistics.StatsPeriod
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.number
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChevronLeft
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// The Statistics header's own controls: the Sales | Orders switch and the Filters panel (user, 2026-11-13: "make this look
// better").


/** One choice of a filter: its words, whether it's on, what picking it does and, for types, sources and methods, its colour. */
internal class FilterChoice(val label: String, val selected: Boolean, val onPick: () -> Unit, val color: Color? = null)

/** A group of choices with its heading. */
internal class FilterGroup(val title: String, val icon: ImageVector, val tone: Color, val choices: List<FilterChoice>)

/**
 * The Filters button and its panel: a title with how many are on and Clear all, then each group as a heading with its icon
 * and the choices as chips. A pick changes the figures at once; the panel stays open until Done or a click outside.
 */
@Composable
internal fun StatsFiltersButton(active: Int, groups: List<FilterGroup>, onClearAll: () -> Unit, size: Dp) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, modifier = Modifier.size(size), shape = RoundedCornerShape(8.dp),
            color = if (active > 0) Kit.GreenSoft else Color.White, border = BorderStroke(1.dp, if (active > 0) Kit.Green else Kit.Border)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Tune, "Filters", Modifier.size(19.dp), tint = if (active > 0) Kit.Green else Kit.Ink)
            }
        }
        if (active > 0) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp).size(17.dp).clip(CircleShape).background(Kit.Green), Alignment.Center) {
                Text("$active", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White, maxLines = 1)
            }
        }
        // Short enough to open right under its button (a taller one was pushed up over the top bar).
        DropdownMenu(open, { open = false }, Modifier.width(340.dp).heightIn(max = 480.dp), offset = DpOffset(0.dp, 6.dp),
            shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
            FiltersPanel(active, groups, onClearAll) { open = false }
        }
    }
}

@Composable
internal fun FiltersPanel(active: Int, groups: List<FilterGroup>, onClearAll: () -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Filters", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.sp, color = Kit.Ink)
            Text(if (active > 0) "$active on" else "Showing everything", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        }
        if (active > 0) {
            Text("Clear all", Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClearAll).padding(horizontal = 8.dp, vertical = 6.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Green)
        }
    }
    HorizontalDivider(color = Kit.Border)
    groups.forEachIndexed { index, group ->
        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Kit.Border.copy(alpha = 0.5f))
        FilterGroupBlock(group)
    }
    HorizontalDivider(color = Kit.Border)
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp), horizontalArrangement = Arrangement.End) {
        Box(Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(Kit.Green).clickable(onClick = onDone).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center) {
            Text("Done", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Color.White)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroupBlock(group: FilterGroup) {
    // The heading leads (bold, its icon), the choices sit under it smaller and lighter.
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(group.icon, null, Modifier.size(15.dp), tint = group.tone)
            Text(group.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink)
        }
        FlowRow(Modifier.padding(start = 22.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            group.choices.forEach { FilterChipView(it) }
        }
    }
}

@Composable
private fun FilterChipView(choice: FilterChoice) {
    val shape = RoundedCornerShape(7.dp)
    Row(
        Modifier.height(26.dp).clip(shape).background(if (choice.selected) Kit.GreenSoft else Color.White)
            .border(1.dp, if (choice.selected) Kit.Green else Kit.Border, shape)
            .clickable(onClick = choice.onPick).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (choice.selected) Icon(Icons.Outlined.Check, null, Modifier.size(12.dp), tint = Kit.Green)
        else choice.color?.let { Box(Modifier.size(7.dp).clip(CircleShape).background(it)) }
        Text(choice.label, fontFamily = Inter(), fontWeight = if (choice.selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 11.5.sp,
            letterSpacing = 0.sp, color = if (choice.selected) Kit.Green else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The period button (user, 2026-11-14: "this month by default, put the choices inside the calendar so I choose there"):
 * it shows the period ("November 2026", "8 – 14 Nov") and opens the quick picks beside the calendar (above it on a
 * phone). A pick applies at once and stays in view (user, 2026-11-14: "when you select an option, show it"): the
 * calendar marks its days, also those in the month before, until Done or a click outside.
 */
@Composable
internal fun StatsPeriodButton(period: StatsPeriod, from: LocalDate, to: LocalDate, today: LocalDate, stacked: Boolean,
    onPreset: (StatsPeriod) -> Unit, onDays: (LocalDate, LocalDate) -> Unit, firstYear: Int = today.year - 2, onMonth: (LocalDate) -> Unit = {},
    onYear: (Int) -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    Box {
        // "All time" says so, with the day it starts from.
        DateButtonFace(if (period == StatsPeriod.ALL_TIME) "All time · since ${com.saporini.mobile_desktop.core.format.shortDate(from, withYear = true)}"
            else periodOrMonthLabel(from, to, today)) { open = true }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White,
            shadowElevation = 8.dp) {
            StatsPeriodPicker(period, from, to, today, stacked, onPreset, onDays, firstYear, onMonth, onYear) { open = false }
        }
    }
}

/** What the right side of the period picker shows: the days, a year's months (By month) or the years (By year). */
private enum class PickerMode { DAYS, MONTHS, YEARS }

/**
 * What the period button opens: the quick picks beside the calendar, or above it ([stacked], phones). Pointing at a
 * quick pick shows its days in the calendar before it's chosen; under the calendar, the days chosen and Done. Under the
 * quick picks, By month shows this year's months to pick one and By year the years (user, 2026-11-16: "remove Last 30
 * days, add By month and By year").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StatsPeriodPicker(period: StatsPeriod, from: LocalDate, to: LocalDate, today: LocalDate, stacked: Boolean,
    onPreset: (StatsPeriod) -> Unit, onDays: (LocalDate, LocalDate) -> Unit, firstYear: Int, onMonth: (LocalDate) -> Unit, onYear: (Int) -> Unit,
    onDone: () -> Unit) {
    val presets = StatsPeriod.entries.filter { it != StatsPeriod.CUSTOM }
    var pointed by remember { mutableStateOf<StatsPeriod?>(null) }
    // A month or a year picked from its own list opens on that list again.
    var mode by remember {
        mutableStateOf(when {
            period == StatsPeriod.CUSTOM && com.saporini.mobile_desktop.core.components.isWholeYear(from, to) -> PickerMode.YEARS
            period == StatsPeriod.CUSTOM && com.saporini.mobile_desktop.core.components.isWholeMonth(from, to, today) -> PickerMode.MONTHS
            else -> PickerMode.DAYS
        })
    }
    val (shownFrom, shownTo) = pointed?.let { periodRange(it, today) } ?: (from to to)
    val right = @Composable {
        when (mode) {
            PickerMode.DAYS -> RangeCalendar(shownFrom, shownTo, today, MAX_STATS_DAYS, neighbourDays = true,
                footer = { first -> PeriodFooter(shownFrom, shownTo, first, onDone) }, onPick = onDays)
            PickerMode.MONTHS -> MonthGrid(from, to, today, onMonth, onDone)
            PickerMode.YEARS -> YearGrid(from, to, today, firstYear, onYear, onDone)
        }
    }
    val pickPreset: (StatsPeriod) -> Unit = { p -> mode = PickerMode.DAYS; onPreset(p) }
    if (stacked) {
        Column {
            FlowRow(Modifier.width(292.dp).padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { p -> FilterChipView(FilterChoice(p.label, p == period && mode == PickerMode.DAYS, { pickPreset(p) })) }
                FilterChipView(FilterChoice("By month", mode == PickerMode.MONTHS, { mode = PickerMode.MONTHS }))
                FilterChipView(FilterChoice("By year", mode == PickerMode.YEARS, { mode = PickerMode.YEARS }))
            }
            HorizontalDivider(Modifier.width(292.dp), color = Kit.Border)
            right()
        }
    } else {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Column(Modifier.width(156.dp).padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                presets.forEach { p ->
                    PresetLine(p.label, p == period && mode == PickerMode.DAYS, onHover = { on -> if (on) pointed = p else if (pointed == p) pointed = null }) {
                        pickPreset(p)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp, horizontal = 6.dp), color = Kit.Border)
                ModeLine("By month", mode == PickerMode.MONTHS) { mode = PickerMode.MONTHS }
                ModeLine("By year", mode == PickerMode.YEARS) { mode = PickerMode.YEARS }
            }
            VerticalDivider(color = Kit.Border)
            right()
        }
    }
}

/** By month or By year beside the calendar: opens its list on the right (green while it's open). */
@Composable
private fun ModeLine(label: String, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(if (open) Kit.GreenSoft else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (open) FontWeight.SemiBold else FontWeight.Medium, fontSize = 12.5.sp,
            letterSpacing = 0.sp, color = if (open) Kit.Green else Kit.Ink, maxLines = 1)
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = if (open) Kit.Green else Kit.Faint)
    }
}

/** A year's twelve months (this year first), three a row; months still to come can't be picked. */
@Composable
private fun MonthGrid(from: LocalDate, to: LocalDate, today: LocalDate, onMonth: (LocalDate) -> Unit, onDone: () -> Unit) {
    val picked = if (com.saporini.mobile_desktop.core.components.isWholeMonth(from, to, today)) from else null
    var year by remember { mutableStateOf(picked?.year ?: today.year) }
    GridFrame(year.toString(), onBack = { year-- }, onNext = if (year < today.year) ({ year++ }) else null,
        footer = { GridFooter(picked?.let { periodOrMonthLabel(from, to, today) + " · " + dayCountText(from, to) }, "Tap a month", onDone) }) {
        (1..12).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    val first = LocalDate(year, m, 1)
                    val name = first.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
                    GridTile(name, if (year == today.year && m == today.month.number) "so far" else null, selected = picked == first,
                        enabled = first <= today) { onMonth(first) }
                }
            }
        }
    }
}

/** The years, from the restaurant's first (at least the last three) to this one, three a row. */
@Composable
private fun YearGrid(from: LocalDate, to: LocalDate, today: LocalDate, firstYear: Int, onYear: (Int) -> Unit, onDone: () -> Unit) {
    val whole = com.saporini.mobile_desktop.core.components.isWholeYear(from, to)
    val running = from == LocalDate(today.year, 1, 1) && to == today
    val picked = if (whole || running) from.year else null
    GridFrame("Years", onBack = null, onNext = null,
        footer = { GridFooter(picked?.let { "$it${if (running) " so far" else ""} · ${dayCountText(from, to)}" }, "Tap a year", onDone) }) {
        (firstYear..today.year).toList().chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { y -> GridTile(y.toString(), if (y == today.year) "so far" else null, selected = picked == y, enabled = true) { onYear(y) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The month and year lists' frame: a title with arrows, the tiles, and the footer. */
@Composable
private fun GridFrame(title: String, onBack: (() -> Unit)?, onNext: (() -> Unit)?, footer: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.width(292.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp)) {
                if (onBack != null) Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack), Alignment.Center) {
                    Icon(Icons.Outlined.ChevronLeft, "Year before", Modifier.size(20.dp), tint = Kit.Ink)
                }
            }
            Text(title, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.Center)
            Box(Modifier.size(32.dp)) {
                if (onNext != null) Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onNext), Alignment.Center) {
                    Icon(Icons.Outlined.ChevronRight, "Year after", Modifier.size(20.dp), tint = Kit.Ink)
                }
            }
        }
        content()
        Spacer(Modifier.height(4.dp))
        footer()
    }
}

/** A month or a year to pick: green when it's the one shown, faded when it can't be picked yet. */
@Composable
private fun RowScope.GridTile(label: String, note: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.weight(1f).height(52.dp).clip(shape).background(if (selected) Kit.Green else Kit.Canvas)
            .then(if (note != null && !selected) Modifier.border(1.dp, Kit.Green.copy(alpha = 0.5f), shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.sp,
            color = when { selected -> Color.White; !enabled -> Kit.Faint; else -> Kit.Ink }, maxLines = 1)
        note?.let { Text(it, fontFamily = Inter(), fontSize = 10.sp, letterSpacing = 0.sp, color = if (selected) Color.White.copy(alpha = 0.85f) else Kit.Green, maxLines = 1) }
    }
}

/** Under the month and year lists: what's shown ("November 2026 · 16 days"), what a tap does, and Done. */
@Composable
private fun GridFooter(shown: String?, hint: String, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(shown ?: "Nothing picked here yet", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp,
                color = if (shown != null) Kit.Ink else Kit.Muted, maxLines = 1)
            Text(hint, fontFamily = Inter(), fontSize = 11.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1)
        }
        Box(Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(Kit.Green).clickable(onClick = onDone).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center) {
            Text("Done", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Color.White)
        }
    }
}

/** Under the calendar: the days chosen ("16 Oct – 14 Nov · 30 days"), what a tap does, and Done. */
@Composable
private fun PeriodFooter(from: LocalDate, to: LocalDate, first: LocalDate?, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(if (first == null) "${periodLabel(from, to)} · ${dayCountText(from, to)}" else "From ${shortDate(first)}", fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
            Text(if (first == null) "Tap a day to choose others" else "Now tap the last day", fontFamily = Inter(), fontSize = 11.sp,
                letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1)
        }
        Box(Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(Kit.Green).clickable(onClick = onDone).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center) {
            Text("Done", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Color.White)
        }
    }
}

/** A quick pick beside the calendar; the one in use is green with a tick. Pointing at it previews its days. */
@Composable
private fun PresetLine(label: String, selected: Boolean, onHover: (Boolean) -> Unit, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { onHover(hovered) }
    Row(
        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp))
            .background(when { selected -> Kit.GreenSoft; hovered -> Kit.Canvas; else -> Color.Transparent })
            .hoverable(source).clickable(interactionSource = source, indication = null, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 12.5.sp,
            letterSpacing = 0.sp, color = if (selected) Kit.Green else Kit.Ink, maxLines = 1)
        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(15.dp), tint = Kit.Green)
    }
}

/**
 * A panel's own day (Sales by hour; user, 2026-11-14). Normally it shows the dates chosen at the top ([periodText], "1 – 14 Nov")
 * in the header button's look; once a day is picked it turns amber with that day and an × back to the top's dates (user,
 * 2026-11-15: "show the exact dates; only when I pick one it should look different from the top filter").
 */
@Composable
internal fun PanelDayButton(day: LocalDate?, maxDate: LocalDate, periodText: String, onPick: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        if (day == null) DateButtonFace(periodText, height = 36.dp) { open = true }
        else PickedDateFace(weekdayLabel(day), onOpen = { open = true }, onClear = { onPick(null) })
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White,
            shadowElevation = 8.dp) {
            PanelDayCalendar(day, maxDate, periodText) { onPick(it); open = false }
        }
    }
}

private fun weekdayLabel(day: LocalDate) = com.saporini.mobile_desktop.core.format.weekdayDate(day)

/** A panel's date button while it shows something else than the top: amber, the date, and an × back to the top's dates. */
@Composable
private fun PickedDateFace(label: String, onOpen: () -> Unit, onClear: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.height(36.dp).clip(shape).background(Kit.Amber.copy(alpha = 0.1f)).border(1.dp, Kit.Amber, shape),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.fillMaxHeight().clickable(onClick = onOpen).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp), tint = Kit.Amber)
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
        }
        Box(Modifier.fillMaxHeight().clickable(onClick = onClear).padding(start = 2.dp, end = 10.dp), Alignment.Center) {
            Icon(Icons.Outlined.Close, "Back to the dates at the top", Modifier.size(15.dp), tint = Kit.Amber)
        }
    }
}

/**
 * A card's Download (user, 2026-11-15: every card has one at its top right); it saves what the card shows.
 */
@Composable
internal fun PanelDownloadButton(size: Dp = 32.dp, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(size), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Download, "Download what this shows", Modifier.size(17.dp), tint = Kit.Green) }
    }
}

/** The amber line a panel shows under its title while it differs from the top: "Only Tue 6 Nov, not 1 – 14 Nov". */
@Composable
internal fun DifferentDatesNote(text: String) {
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Kit.Amber))
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.sp, color = Kit.Amber, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

/** The one-day calendar the button opens, with All days and Today under it. */
@Composable
internal fun PanelDayCalendar(day: LocalDate?, maxDate: LocalDate, periodText: String, onPick: (LocalDate?) -> Unit) {
    DayCalendar(day, maxDate, { onPick(it) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CalendarLink(periodText, Kit.Green, enabled = day != null) { onPick(null) }
            Box(Modifier.weight(1f))
            CalendarLink("Today", Kit.Green, enabled = day != maxDate) { onPick(maxDate) }
        }
    }
}

@Composable
private fun CalendarLink(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = if (enabled) color else Kit.Faint)
}

/**
 * A panel's own week (Sales by weekday; user, 2026-11-14: "select one week like Outlook"). Normally it shows the dates at the
 * top ([periodText]); a picked week turns it amber with its dates and an × back (user, 2026-11-15).
 */
@Composable
internal fun PanelWeekButton(week: LocalDate?, maxDate: LocalDate, periodText: String, onPick: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        if (week == null) DateButtonFace(periodText, height = 36.dp) { open = true }
        else PickedDateFace(periodLabel(week, week.plus(DatePeriod(days = 6))), onOpen = { open = true }, onClear = { onPick(null) })
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White,
            shadowElevation = 8.dp) {
            PanelWeekCalendar(week, maxDate, periodText) { onPick(it); open = false }
        }
    }
}

/** The week calendar the button opens, with All weeks and This week under it. */
@Composable
internal fun PanelWeekCalendar(week: LocalDate?, maxDate: LocalDate, periodText: String, onPick: (LocalDate?) -> Unit) {
    val thisWeek = maxDate.minus(DatePeriod(days = maxDate.dayOfWeek.isoDayNumber - 1))
    WeekCalendar(week, maxDate, { onPick(it) }) {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            CalendarLink(periodText, Kit.Green, enabled = week != null) { onPick(null) }
            Box(Modifier.weight(1f))
            CalendarLink("This week", Kit.Green, enabled = week != thisWeek) { onPick(thisWeek) }
        }
    }
}

/**
 * The branch picker at the top, only for a restaurant with more than one branch (user, 2026-11-14: "for one branch it's
 * perfect; with several I choose a branch at the top"): All branches (the whole restaurant) or one branch, and every page
 * follows it. A branch shows by its colour, the one it has on every chart.
 */
@Composable
internal fun StatsBranchButton(selected: String?, branches: List<StatsBranch>, colorOf: (String) -> Color, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val chosen = branches.firstOrNull { it.id == selected }
    Box {
        Surface(onClick = { open = true }, modifier = Modifier.height(PageControlHeight), shape = RoundedCornerShape(8.dp),
            color = if (chosen != null) colorOf(chosen.id).copy(alpha = 0.08f) else Color.White,
            border = BorderStroke(1.dp, if (chosen != null) colorOf(chosen.id).copy(alpha = 0.55f) else Kit.Border)) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (chosen == null) Icon(Icons.Outlined.Business, null, Modifier.size(18.dp), tint = Kit.Ink)
                else Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf(chosen.id)))
                Text(chosen?.name ?: "All branches", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp,
                    color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Ink)
            }
        }
        DropdownMenu(open, { open = false }, Modifier.width(250.dp), offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp),
            containerColor = Color.White, shadowElevation = 8.dp) {
            Text("Show", Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp, letterSpacing = 0.sp, color = Kit.Muted)
            BranchChoice("All branches", "the whole restaurant", null, selected == null) { onPick(null); open = false }
            HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Kit.Border.copy(alpha = 0.6f))
            branches.forEach { b -> BranchChoice(b.name, null, colorOf(b.id), b.id == selected) { onPick(b.id); open = false } }
        }
    }
}

@Composable
private fun BranchChoice(name: String, detail: String?, color: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) Kit.GreenSoft else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (color == null) Icon(Icons.Outlined.Business, null, Modifier.size(16.dp), tint = if (selected) Kit.Green else Kit.Muted)
        else Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = Inter(), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.sp,
                color = if (selected) Kit.Green else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, fontFamily = Inter(), fontSize = 10.5.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1) }
        }
        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(15.dp), tint = Kit.Green)
    }
}
