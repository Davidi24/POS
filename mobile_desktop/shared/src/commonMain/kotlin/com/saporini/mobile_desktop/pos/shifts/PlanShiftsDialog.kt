@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Image
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.hoverable
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.DoneBurst
import com.saporini.mobile_desktop.core.components.DoneMark
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldBlock
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.FormColors
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.LocalDialogRoom
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.math.roundToInt
import kotlin.time.Instant

// Usual shifts: the planner (when a shift runs), the quick menu of a day's empty cell (one click for a usual shift), and the
// list of usual shift times.

/**
 * Plan shifts: whose shifts, then the shift, then only when it runs: which days, from when and for how long. That's saved on
 * the shift, so it can be changed again; who works is ticked day by day in the day window (user, 2026-10-31: "here only apply
 * the shift, then in here check who works").
 */
@Composable
internal fun PlanShiftsDialog(
    state: ShiftState,
    staff: List<ShiftStaff>,
    now: Instant,
    initialDate: LocalDate,
    initialTemplate: String? = null,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit = {}
) {
    val zone = state.zone
    val today = now.toLocalDateTime(zone).date
    val templates by ShiftTemplates.all.collectAsState()
    var templateId by remember { mutableStateOf(initialTemplate) }
    val template = templates.firstOrNull { it.id == templateId }
    // A usual shift being added (null id) or changed, from the shift cards (user, 2026-10-31).
    var editing by remember { mutableStateOf<ShiftTemplate?>(null) }
    var adding by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<DoneMark?>(null) }
    // First the shift is picked, on its own (user, 2026-10-31: "the shift cards open only in the dialog, after Plan shifts,
    // first thing"); then when it runs.
    var pickingShift by remember { mutableStateOf(initialTemplate == null) }
    // Before the shift: whose shifts (user, 2026-10-31: "it opens Plan waiters' shifts, Plan kitchen shifts…; each kind
    // has its own shifts"). [category] is a paid role's id, or null for anyone.
    val roleChoices by ShiftTemplates.roles.collectAsState()
    var choosingCategory by remember { mutableStateOf(pickingShift && roleChoices.isNotEmpty()) }
    var category by remember { mutableStateOf<String?>(null) }
    val categoryRole = roleChoices.firstOrNull { it.id == category }
    val categoryTemplates = if (categoryRole == null) templates else templates.filter { t -> t.roles.isEmpty() || t.roles.any { it.id == categoryRole.id } }

    // What's being changed: starts as what the shift has saved at this branch.
    var weekdays by remember(templateId) { mutableStateOf(template?.weekdays.orEmpty()) }
    var startDate by remember(templateId) { mutableStateOf(template?.startsOn ?: maxOf(initialDate, today)) }
    var forever by remember(templateId) { mutableStateOf(template?.lengthUnit == null) }
    var unit by remember(templateId) { mutableStateOf(template?.lengthUnit ?: LengthUnit.WEEKS) }
    var length by remember(templateId) { mutableIntStateOf(template?.lengthCount ?: 1) }
    // Who it applies to, with the turn each starts on, and the shifts it takes turns with (user, 2026-10-31: "who it applies
    // to, and one week the first shift, one week the second").
    var people by remember(templateId) { mutableStateOf(template?.people.orEmpty()) }
    var turnShifts by remember(templateId) { mutableStateOf(template?.turnShifts.orEmpty()) }
    var every by remember(templateId) { mutableIntStateOf(template?.turnEvery ?: 1) }
    val eligible = template?.let { shift ->
        staff.filter { person -> shift.fits(person) && (categoryRole == null || person.roles.any { it.equals(categoryRole.name, ignoreCase = true) }) }
    }.orEmpty().sortedBy { it.name.lowercase() }
    val chosen = staff.filter { it.id in people }.sortedBy { it.name.lowercase() }
    val turns = listOfNotNull(template) + turnShifts.mapNotNull { id -> templates.firstOrNull { it.id == id } }
    val changed = template != null && (weekdays != template.weekdays || (weekdays.isNotEmpty() && (startDate != template.startsOn ||
        (if (forever) null else unit) != template.lengthUnit || (!forever && length != template.lengthCount) || people != template.people ||
        turnShifts != template.turnShifts || (turnShifts.isNotEmpty() && every != template.turnEvery))))
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val save = {
        val shift = template
        if (shift != null) {
            saving = true; problem = null
            scope.launch {
                val answer = ShiftTemplates.savePlan(shift.id, weekdays, startDate, if (forever) null else unit, length, people, every, turnShifts)
                saving = false
                if (answer.problem != null) problem = answer.problem
                else {
                    onSaved(when {
                        weekdays.isEmpty() -> "${shift.name} is off the calendar."
                        else -> "${shift.name} saved." + (if (answer.added > 0) " ${shiftsText(answer.added)} added." else "") +
                            (if (answer.skipped > 0) " ${answer.skipped} left out: already working." else "")
                    })
                    onDismiss()
                }
            }
        }
        Unit
    }
    // Taking turns: the turns left when one is taken out, and everyone back on a turn that's still there.
    val setTurns = { next: List<String> ->
        turnShifts = next
        people = people.mapValues { (_, turn) -> if (turn > next.size) 0 else turn }
    }

    val planTitle = categoryRole?.let { "Plan ${it.name} shifts" } ?: "Plan shifts"
    // One bigger window for every step (user, 2026-10-31: "make it bigger; when I click one of these, only the content
    // changes, not another form"): whose shifts, then the shift, then when it runs.
    val step = when { choosingCategory -> PlanStep.WHOSE; pickingShift || template == null -> PlanStep.SHIFT; else -> PlanStep.FORM }
    AppDialog(
        if (step == PlanStep.WHOSE) "Plan shifts" else planTitle, { if (!saving) onDismiss() }, busy = saving, maxWidth = 1000.dp, height = 780.dp,
        onBack = when (step) {
            PlanStep.WHOSE -> null
            PlanStep.SHIFT -> if (roleChoices.isNotEmpty()) { { choosingCategory = true } } else null
            PlanStep.FORM -> { { pickingShift = true } }
        },
        subtitle = when (step) {
            PlanStep.WHOSE -> "Whose shifts?"
            PlanStep.SHIFT -> "Pick the shift"
            PlanStep.FORM -> "Who, which days and how long"
        },
        buttons = {
            KitButton(if (step == PlanStep.FORM) "Cancel" else "Close", onDismiss, style = ButtonStyle.SECONDARY, enabled = !saving)
            // Not "add": it can be changed again (user, 2026-10-31).
            if (step == PlanStep.FORM) KitButton("Save changes", save, icon = Icons.Outlined.Check, enabled = changed, loading = saving)
        }
    ) {
        when (step) {
        PlanStep.WHOSE -> CategoryCards(roleChoices, staff) { id -> category = id; choosingCategory = false }
        PlanStep.SHIFT -> ShiftCards(categoryTemplates, templateId, onPick = { id -> templateId = id; problem = null; pickingShift = false },
            onEdit = { editing = it }, onAdd = { adding = true },
            // Another role's shifts can be copied in (user, 2026-10-31: "make the kitchen the same as the manager, just import").
            onImport = categoryRole?.takeIf { role -> importSources(role, roleChoices, templates).isNotEmpty() }?.let { { importing = true } })
        PlanStep.FORM -> template?.let { shift ->
            val narrow = LocalNarrowDialog.current
            val room = LocalDialogRoom.current
            val lastDay = unit.lastDay(startDate, length)
            val last = daysFrom(startDate, weekdays, lastDay).lastOrNull()
            val quick: @Composable () -> Unit = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickPick("Mon – Fri", weekdays == (0..4).toSet()) { weekdays = (0..4).toSet() }
                    QuickPick("Weekend", weekdays == setOf(5, 6)) { weekdays = setOf(5, 6) }
                    QuickPick("Every day", weekdays.size == 7) { weekdays = (0..6).toSet() }
                    if (weekdays.isNotEmpty()) QuickPick("Clear", false) { weekdays = emptySet() }
                }
            }
            val starts: @Composable () -> Unit = { StartsPicker(startDate, today) { startDate = it } }
            val cards: @Composable () -> Unit = {
                PlanCard("Shift", action = { TextLink("Edit", Icons.Outlined.Edit) { editing = shift } }) { ChosenShift(shift) }
                // Who it applies to (user, 2026-10-31: "why did you remove who it applies to").
                val allChosen = eligible.isNotEmpty() && eligible.all { it.id in people }
                PlanCard("Who", if (eligible.isEmpty()) null else "${eligible.count { it.id in people }} of ${eligible.size}", action = {
                    if (eligible.isNotEmpty()) TextLink(if (allChosen) "Clear" else "Select all", if (allChosen) Icons.Outlined.Close else Icons.Outlined.DoneAll) {
                        people = if (allChosen) people - eligible.map { it.id }.toSet() else people + eligible.filter { it.id !in people }.associate { it.id to 0 }
                    }
                }) {
                    if (eligible.isEmpty()) Text("Nobody here has a role this shift is for.", fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Amber)
                    else PeopleGrid(eligible, people.keys) { person -> people = if (person.id in people) people - person.id else people + (person.id to 0) }
                }
                // Taking turns week by week (user, 2026-10-31: "one week the first shift, one week the second; I choose who
                // alternates how").
                val turnChoices = categoryTemplates.filter { it.id != shift.id }
                PlanCard("Take turns", if (turns.size > 1) "${turns.joinToString(" → ") { it.name }} · every ${weeksText(every)}" else null, action = {
                    com.saporini.mobile_desktop.core.components.KitSwitch(turns.size > 1, { on -> setTurns(if (on) listOfNotNull(turnChoices.firstOrNull()?.id) else emptyList()) },
                        enabled = turnChoices.isNotEmpty(), compact = true)
                }) {
                    if (turns.size < 2) Text(
                        if (turnChoices.isEmpty()) "Add another shift to take turns with ${shift.name}." else "Everyone works ${shift.name} every week. Switch on to swap shifts week by week.",
                        fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Muted
                    ) else TurnsEditor(turns, turnChoices, every, chosen, people, startDate, narrow,
                        onTurns = { list -> setTurns(list.drop(1).map { it.id }) }, onEvery = { every = it }, onStart = { who, turn -> people = people + (who to turn) },
                        onSplit = { people = people + chosen.mapIndexed { index, person -> person.id to index % turns.size } })
                }
                PlanCard("Days", if (weekdays.isEmpty()) "Pick the days it runs" else daysAWeek(weekdays.size), action = if (narrow) null else quick) {
                    if (narrow) quick()
                    WeekdayToggles(weekdays) { index -> weekdays = if (index in weekdays) weekdays - index else weekdays + index }
                }
                PlanCard("How long", action = if (narrow) null else starts) {
                    if (narrow) starts()
                    val foreverOption: @Composable (Modifier) -> Unit = { m ->
                        DurationOption("Forever", forever, m, { forever = true }) {
                            Text("This keeps going until you stop it", fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
                        }
                    }
                    val timeOption: @Composable (Modifier) -> Unit = { m ->
                        DurationOption("For a time", !forever, m, { forever = false }) {
                            val stepper: @Composable () -> Unit = { CountStepper(length, unit.most) { forever = false; length = it } }
                            val units: @Composable () -> Unit = {
                                UnitTabs(unit, !forever) { forever = false; unit = it; length = length.coerceAtMost(it.most) }
                            }
                            if (narrow) { stepper(); units() }
                            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { stepper(); units() }
                            if (!forever) Text(
                                when {
                                    weekdays.isEmpty() || last != null -> "This will last till ${longDay(last ?: lastDay, today)}"
                                    else -> "None of these days falls in that time"
                                },
                                fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Ink
                            )
                        }
                    }
                    if (narrow) { foreverOption(Modifier.fillMaxWidth()); timeOption(Modifier.fillMaxWidth()) }
                    else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        foreverOption(Modifier.weight(1f).fillMaxHeight())
                        timeOption(Modifier.weight(1.7f).fillMaxHeight())
                    }
                }
            }
            // Beside the cards when there's room, so how it will run stays in view.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = room != null && maxWidth >= 780.dp
                if (wide) Row(Modifier.fillMaxWidth().height(room ?: 0.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ScrollColumn(Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.fillMaxWidth().padding(3.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { cards() }
                    }
                    PlanSummary(shift, weekdays, startDate, today, forever, last, chosen, turns, every, problem, Modifier.width(272.dp).fillMaxHeight(), scrolls = true)
                } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    cards()
                    PlanSummary(shift, weekdays, startDate, today, forever, last, chosen, turns, every, problem, Modifier.fillMaxWidth(), scrolls = false)
                }
            }
        }
        }
        DoneBurst(copied)
    }

    importing.takeIf { it }?.let {
        categoryRole?.let { role ->
            ImportShiftsDialog(role, roleChoices, templates, onDismiss = { importing = false }) { count ->
                importing = false
                copied = DoneMark((copied?.tick ?: 0) + 1, if (count == 1) "1 shift copied to ${role.name}" else "$count shifts copied to ${role.name}")
            }
        }
    }
    // A shift added inside a role is for that role (user, 2026-10-31: "inside Manager, Add shift shouldn't have For"). A new
    // shift goes straight on to when it runs.
    // Inside a role a shift's For isn't shown either when it's changed (user, 2026-10-31).
    if (adding || editing != null) ShiftTemplateDialog(editing, forRole = categoryRole,
        onDismiss = { adding = false; editing = null }) { id ->
        val added = adding
        adding = false; editing = null
        if (added && id != null) { templateId = id; pickingShift = false }
    }
}

/** The planner's steps, all in one window. */
private enum class PlanStep { WHOSE, SHIFT, FORM }

private fun daysAWeek(count: Int) = when (count) {
    0 -> "None yet"
    1 -> "1 day a week"
    7 -> "Every day"
    else -> "$count days a week"
}

private fun weekdaysText(weekdays: Set<Int>) = when {
    weekdays.isEmpty() -> "None yet"
    weekdays.size == 7 -> "Every day"
    weekdays == (0..4).toSet() -> "Monday to Friday"
    weekdays == setOf(5, 6) -> "Weekend"
    weekdays.size == 1 -> WeekdayNames[weekdays.first()]
    else -> weekdays.sorted().joinToString(", ") { WeekdayNames[it].take(3) }
}

private fun longDay(date: LocalDate, today: LocalDate) =
    "${WeekdayNames[date.dayOfWeek.ordinal]} ${date.day} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }}" + if (date.year != today.year) " ${date.year}" else ""

/** When a saved shift runs, in a few words; null when it isn't on any day yet. */
private fun planText(shift: ShiftTemplate): String? {
    val from = shift.startsOn ?: return null
    if (shift.weekdays.isEmpty()) return null
    val until = shift.lengthUnit?.let { unit -> daysFrom(from, shift.weekdays, unit.lastDay(from, shift.lengthCount)).lastOrNull() ?: unit.lastDay(from, shift.lengthCount) }
    return weekdaysText(shift.weekdays) + " · " + (until?.let { "till ${it.weekdayShort()} ${it.shortDay()}" } ?: "Forever") +
        (if (shift.people.isNotEmpty()) " · ${if (shift.people.size == 1) "1 person" else "${shift.people.size} people"}" else "")
}

/** How the shift will run, in one panel: its name and times, who it's for, the days, the start and how long. */
@Composable
private fun PlanSummary(
    shift: ShiftTemplate, weekdays: Set<Int>, startDate: LocalDate, today: LocalDate, forever: Boolean, last: LocalDate?, people: List<ShiftStaff>,
    turns: List<ShiftTemplate>, every: Int, problem: String?, modifier: Modifier, scrolls: Boolean
) {
    val scroll = rememberScrollState()
    Column(
        // Beside the cards it has a fixed height and scrolls itself; under them it's part of what scrolls.
        modifier.clip(RoundedCornerShape(16.dp)).background(Kit.Canvas).border(1.dp, Kit.RowBorder, RoundedCornerShape(16.dp))
            .then(if (scrolls) Modifier.verticalScroll(scroll) else Modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        problem?.let { Text(it, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Danger) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(shift.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${shift.times} · ${hoursText(shift.minutes)}", fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
        }
        SummaryLine("For", shift.forWhom)
        SummaryLine("Who", when (people.size) { 0 -> "Nobody yet"; 1 -> people.first().name; else -> "${people.size} people" }) {
            if (people.isNotEmpty()) AvatarStack(people.map { it.name }, 4, 26.dp)
        }
        if (turns.size > 1) SummaryLine("Takes turns", "${turns.joinToString(" → ") { it.name }}, every ${weeksText(every)}")
        SummaryLine("Days", weekdaysText(weekdays))
        SummaryLine("Starts", if (startDate == today) "Today" else "${startDate.weekdayShort()} ${startDate.shortDay()}")
        SummaryLine("Lasts", when {
            forever -> "Forever"
            last != null -> "Till ${last.weekdayShort()} ${last.shortDay()}"
            else -> "No days yet"
        })
        Text(if (people.isEmpty()) "You can also put people on it day by day on the calendar." else "Days someone already works are left out.",
            fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
    }
}

@Composable
private fun SummaryLine(label: String, value: String, leading: @Composable () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading()
            Text(value, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Kit.Ink)
        }
    }
}

private fun weeksText(weeks: Int) = if (weeks == 1) "week" else "$weeks weeks"

private fun shiftsText(count: Int) = if (count == 1) "1 shift" else "$count shifts"

/** People to tick, in even columns: face, name, role, and a tick circle. */
@Composable
private fun PeopleGrid(people: List<ShiftStaff>, chosen: Set<String>, onToggle: (ShiftStaff) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 540.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            people.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { person ->
                        val selected = person.id in chosen
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (selected) Kit.GreenSoft else Color.White)
                                .border(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.Border, RoundedCornerShape(10.dp))
                                .pointerHoverIcon(PointerIcon.Hand).clickable { onToggle(person) }.padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            ShiftAvatar(person.name, 32.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(person.name.ifBlank { "Staff member" }, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(roleName(person.roles), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                            }
                            Box(Modifier.size(20.dp).clip(CircleShape).background(if (selected) Kit.Green else Color.White)
                                .border(1.5.dp, if (selected) Kit.Green else Kit.Faint, CircleShape), Alignment.Center) {
                                if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = Color.White)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Taking turns: the shifts in order (the first is the one being planned), how often they swap, who starts on which, and the
 * next weeks as a small table so it's clear who works what.
 */
@Composable
private fun TurnsEditor(
    turns: List<ShiftTemplate>, choices: List<ShiftTemplate>, every: Int, people: List<ShiftStaff>, starts: Map<String, Int>, startDate: LocalDate, narrow: Boolean,
    onTurns: (List<ShiftTemplate>) -> Unit, onEvery: (Int) -> Unit, onStart: (String, Int) -> Unit, onSplit: () -> Unit
) {
    val label: @Composable (String) -> Unit = { Text(it, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink) }
    label("The shifts, in turn")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        turns.forEachIndexed { index, turn ->
            if (index > 0) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = Kit.Faint)
            TurnChip(index + 1, turn, onRemove = if (index == 0) null else { { onTurns(turns.filterIndexed { i, _ -> i != index }) } })
        }
        val left = choices.filter { option -> turns.none { it.id == option.id } }
        if (turns.size < 4 && left.isNotEmpty()) AddTurnButton(left) { onTurns(turns + it) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        label("Swap every")
        com.saporini.mobile_desktop.core.components.OverviewTabs(listOf("1 week", "2 weeks", "3 weeks", "4 weeks"), if (every == 1) "1 week" else "$every weeks",
            { onEvery(it.substringBefore(' ').toInt()) }, if (narrow) Modifier.weight(1f) else Modifier.width(380.dp), height = 36.dp)
    }
    if (people.isEmpty()) {
        Text("Pick who works it above, then choose who starts on which.", fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { label("Who starts on which") }
        if (people.size > 1) TextLink("Split them evenly", Icons.Outlined.CallSplit, onSplit)
    }
    people.forEach { person ->
        val start = (starts[person.id] ?: 0).coerceIn(0, turns.size - 1)
        val pills: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                turns.forEachIndexed { index, turn ->
                    val on = index == start
                    val tone = dayPartColor(dayPartOf(turn.start))
                    Row(
                        Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(if (on) tone.copy(alpha = 0.14f) else Color.White)
                            .border(if (on) 1.5.dp else 1.dp, if (on) tone else Kit.Border, RoundedCornerShape(50)).pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onStart(person.id, index) }.padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(turn.picture, null, Modifier.size(14.dp), tint = if (on) tone else Kit.Muted)
                        Text(turn.name, fontFamily = Inter(), fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp, color = if (on) Kit.Ink else Kit.Muted, maxLines = 1)
                    }
                }
            }
        }
        if (narrow) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShiftAvatar(person.name, 26.dp)
                Text(person.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.horizontalScroll(rememberScrollState())) { pills() }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShiftAvatar(person.name, 28.dp)
            Text(person.name, Modifier.width(150.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) { pills() }
        }
    }
    TurnPreview(turns, every, people, starts, startDate)
}

/** One shift in the turns: its number, picture, name and times; the ones after the first can be taken out. */
@Composable
private fun TurnChip(number: Int, turn: ShiftTemplate, onRemove: (() -> Unit)?) {
    val tone = dayPartColor(dayPartOf(turn.start))
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))
            .padding(start = 8.dp, end = if (onRemove != null) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Kit.Green), Alignment.Center) {
            Text("$number", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
        }
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(tone.copy(alpha = 0.13f)), Alignment.Center) {
            Icon(turn.picture, null, Modifier.size(15.dp), tint = tone)
        }
        Column {
            Text(turn.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1)
            Text(turn.times, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        }
        onRemove?.let { remove ->
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).clickable(onClick = remove), Alignment.Center) {
                Icon(Icons.Outlined.Close, "Take ${turn.name} out", Modifier.size(15.dp), tint = Kit.Muted)
            }
        }
    }
}

/** Adds another shift to the turns, from a short menu. */
@Composable
private fun AddTurnButton(choices: List<ShiftTemplate>, onPick: (ShiftTemplate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Kit.GreenSoft.copy(alpha = 0.5f))
                .border(1.dp, Kit.Green.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.Add, null, Modifier.size(17.dp), tint = Kit.Green)
            Text("Add a shift", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Kit.Green)
        }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp,
            modifier = Modifier.width(260.dp)) {
            choices.forEach { option ->
                val tone = dayPartColor(dayPartOf(option.start))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { open = false; onPick(option) }.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(option.picture, null, Modifier.size(17.dp), tint = tone)
                    Text(option.name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
                    Text(option.times, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted)
                }
            }
        }
    }
}

/** The next weeks, a line per person: the shift each works that week, in the colour of its part of the day. */
@Composable
private fun TurnPreview(turns: List<ShiftTemplate>, every: Int, people: List<ShiftStaff>, starts: Map<String, Int>, startDate: LocalDate) {
    val firstWeek = startDate.weekStart()
    val weeks = (turns.size * every * 2).coerceIn(4, 6)
    Text("The next weeks", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).horizontalScroll(rememberScrollState())) {
        Column {
            Row(Modifier.background(Kit.Canvas).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(150.dp))
                (0 until weeks).forEach { week ->
                    Text("Week of ${firstWeek.plus(DatePeriod(days = week * 7)).shortDay()}", Modifier.width(118.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp, color = Kit.Muted, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
            people.forEach { person ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.width(150.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShiftAvatar(person.name, 24.dp)
                        Text(person.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    (0 until weeks).forEach { week ->
                        val turn = turns[(week / every + (starts[person.id] ?: 0)).mod(turns.size)]
                        val tone = dayPartColor(dayPartOf(turn.start))
                        Box(Modifier.width(118.dp).padding(horizontal = 5.dp), Alignment.Center) {
                            Text(turn.name, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(tone.copy(alpha = 0.13f)).padding(vertical = 6.dp),
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Kit.Ink, textAlign = TextAlign.Center, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** The shift being planned: its part of the day, name, times, length and who it's for. */
@Composable
private fun ChosenShift(template: ShiftTemplate) {
    val part = dayPartOf(template.start)
    val tone = dayPartColor(part)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(tone.copy(alpha = 0.13f)), Alignment.Center) {
            Icon(template.picture, null, Modifier.size(21.dp), tint = tone)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(template.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${template.times} · ${hoursText(template.minutes)}${if (template.overnight) " · ends next day" else ""} · ${template.forWhom}", fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
        }
    }
}

/** A quick choice: a small button with a frame, so it reads as something to click; a tick and green when it's what's picked. */
@Composable
private fun QuickPick(text: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(if (active) Kit.GreenSoft else Color.White)
            .border(if (active) 1.5.dp else 1.dp, if (active) Kit.Green else Kit.Border, RoundedCornerShape(50))
            .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (active) Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = Kit.Green)
        Text(text, fontFamily = Inter(), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 12.5.sp, color = if (active) Kit.Green else Kit.Ink, maxLines = 1)
    }
}

/** One way to say how long: a tick on the left like the shift cards, soft green when it's the one picked. */
@Composable
private fun DurationOption(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Kit.GreenSoft else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.Border, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (selected) Kit.Green else Color.White).border(1.5.dp, if (selected) Kit.Green else Kit.Border, CircleShape), Alignment.Center) {
            if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = Color.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink)
            content()
        }
    }
}

/** Days, weeks or months. */
@Composable
private fun UnitTabs(unit: LengthUnit, active: Boolean, onPick: (LengthUnit) -> Unit) {
    Row(Modifier.height(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        LengthUnit.entries.forEach { option ->
            val chosen = option == unit && active
            Box(Modifier.fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(if (chosen) Kit.Green.copy(alpha = 0.14f) else Color.Transparent)
                .clickable { onPick(option) }.padding(horizontal = 10.dp), Alignment.Center) {
                Text(option.label, fontFamily = Inter(), fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium, fontSize = 12.5.sp, color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1)
            }
        }
    }
}

/** A small number with minus and plus, from 1 to [most]. */
@Composable
private fun CountStepper(value: Int, most: Int, onChange: (Int) -> Unit) {
    val step: @Composable (androidx.compose.ui.graphics.vector.ImageVector, String, Boolean, () -> Unit) -> Unit = { icon, label, enabled, click ->
        Box(Modifier.size(34.dp).clickable(enabled = enabled, onClick = click), Alignment.Center) {
            Icon(icon, label, Modifier.size(17.dp), tint = if (enabled) Kit.Ink else Kit.Faint)
        }
    }
    Row(Modifier.height(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        step(Icons.Outlined.Remove, "Less", value > 1) { onChange(value - 1) }
        Text("$value", Modifier.widthIn(min = 26.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink, textAlign = TextAlign.Center, maxLines = 1)
        step(Icons.Outlined.Add, "More", value < most) { onChange(value + 1) }
    }
}

/**
 * The menu of an empty day: the usual shift times (one click adds that shift for [person] on [date]) and "more days",
 * which opens the planner with them ticked.
 */
@Composable
internal fun QuickAddMenu(expanded: Boolean, onDismiss: () -> Unit, person: ShiftStaff?, date: LocalDate, onPick: (ShiftTemplate) -> Unit, onMore: () -> Unit) {
    val templates by ShiftTemplates.all.collectAsState()
    DropdownMenu(expanded, onDismiss, offset = DpOffset(0.dp, 4.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp,
        modifier = Modifier.width(250.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (person != null) ShiftAvatar(person.name, 30.dp)
            Column {
                Text(person?.name ?: "Add a shift", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${date.weekdayShort()} ${date.shortDay()}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
        if (person != null && templates.isNotEmpty()) {
            Text("PUT ON", Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp,
                letterSpacing = 0.6.sp, color = Kit.Muted)
            templates.forEach { template ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { onPick(template) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dayPartColor(dayPartOf(template.start))))
                    Text(template.name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    Text(template.times, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Kit.Muted)
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Kit.Border))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(bottom = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onMore)
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(Icons.Outlined.EditCalendar, null, Modifier.size(17.dp), tint = Kit.Green)
            Text(if (person == null) "Choose who and when…" else "More days…", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Green)
        }
    }
}

// ---------- The usual shift times ----------

/** The restaurant's usual shifts, each with who it's for; a click changes one, and Add shift adds one. */
@Composable
internal fun ShiftTimesDialog(onDismiss: () -> Unit) {
    val templates by ShiftTemplates.all.collectAsState()
    var editing by remember { mutableStateOf<ShiftTemplate?>(null) }
    var adding by remember { mutableStateOf(false) }
    AppDialog(
        "Usual shifts", onDismiss, maxWidth = 620.dp,
        subtitle = "Pick one when you plan, instead of typing times.",
        buttons = {
            KitButton("Add shift", { adding = true }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Add)
            KitButton("Done", onDismiss, icon = Icons.Outlined.Check)
        }
    ) {
        if (templates.isEmpty()) Text("No usual shifts yet.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
        templates.forEach { template ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))
                    .clickable { editing = template }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val tone = dayPartColor(dayPartOf(template.start))
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.13f)), Alignment.Center) {
                    Icon(template.picture, null, Modifier.size(18.dp), tint = tone)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(template.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${template.times} · ${hoursText(template.minutes)} · ${template.forWhom}", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.Edit, "Change ${template.name}", Modifier.size(17.dp), tint = Kit.Muted)
            }
        }
        Text("A shift that ends at or before its start ends the next day (for example 17:00–00:30).", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
    }
    if (adding || editing != null) ShiftTemplateDialog(editing, onDismiss = { adding = false; editing = null }) { adding = false; editing = null }
}

// ---------- Pieces ----------

/** A time in 15-minute steps, picked from a grid that opens at the chosen time. */
@Composable
internal fun ShiftTimeField(label: String, value: LocalTime, modifier: Modifier = Modifier, hint: String? = null, onChange: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // On a phone two of these share a line with a remove button, so the clock icon makes way for the time.
    val narrow = LocalNarrowDialog.current
    FieldBlock(label, modifier, hint = hint) {
        Box {
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                    .border(1.dp, if (open) Kit.Green else FormColors.Border, RoundedCornerShape(8.dp)).clickable { open = true }
                    .padding(horizontal = if (narrow) 10.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (narrow) 6.dp else 9.dp)
            ) {
                if (!narrow) Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = Kit.Ink)
                Text(value.hhmm(), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Kit.Ink,
                    maxLines = 1, softWrap = false)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Ink)
            }
            DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 4.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
                val slots = (0 until 24 * 4).map { LocalTime(it / 4, (it % 4) * 15) }
                val rows = slots.chunked(4)
                val scroll = rememberScrollState()
                val step = with(LocalDensity.current) { 36.dp.toPx() }
                LaunchedEffect(Unit) {
                    val row = rows.indexOfFirst { value in it }.coerceAtLeast(0)
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
                                    Text(slot.hhmm(), fontFamily = Inter(), fontWeight = if (selected) FontWeight.Bold else if (slot.minute == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 12.sp, color = when { selected -> Color.White; slot.minute == 0 -> Kit.Ink; else -> Kit.Muted })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A part of the plan as a white card: its heading (a short detail, an action on the right), a line, then what's in it. */
@Composable
private fun PlanCard(title: String, detail: String? = null, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 3.dp) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                Text(detail.orEmpty(), Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                action?.invoke()
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun TextLink(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, Modifier.size(15.dp), tint = Kit.Green)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
    }
}

/** Three choices a row when each gets about 170 dp, else two. */
private fun planColumns(width: Dp): Int = if (width >= 170.dp * 3 + 16.dp) 3 else 2

/** One usual shift to choose: name, times, length, who it's for; coloured by the part of the day; a pencil to change it. */
@Composable
private fun TemplateCard(
    name: String, times: String, detail: String?, selected: Boolean, part: DayPart, modifier: Modifier, compact: Boolean = false,
    forWhom: String? = null, picture: androidx.compose.ui.graphics.vector.ImageVector, plan: String? = null, onEdit: (() -> Unit)? = null, onClick: () -> Unit
) {
    val tone = dayPartColor(part)
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Kit.GreenSoft else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // The part of the day as a soft square with its picture (a phone keeps just the small icon by the name).
        if (!compact) Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.13f)), Alignment.Center) {
            Icon(picture, null, Modifier.size(19.dp), tint = tone)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (compact) Icon(picture, null, Modifier.size(14.dp), tint = tone)
                Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(times, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1)
            detail?.let { Text(it, fontFamily = Inter(), fontSize = 10.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            // When it runs, or that it isn't on any day yet.
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.CalendarToday, null, Modifier.size(12.dp), tint = if (plan == null) Kit.Amber else Kit.Green)
                Text(plan ?: "No days yet", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = if (plan == null) Kit.Amber else Kit.Green, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Who it's for (user, 2026-10-31: "a waiters' shift, or waiters + kitchen").
            forWhom?.let {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Groups, null, Modifier.size(12.dp), tint = Kit.Blue)
                    Text(it, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = Kit.Blue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!compact || selected) Box(Modifier.size(20.dp).clip(CircleShape).background(if (selected) Kit.Green else Color.White)
                .border(1.5.dp, if (selected) Kit.Green else Kit.Border, CircleShape), Alignment.Center) {
                if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = Color.White)
            }
            onEdit?.let { edit ->
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).clickable(onClick = edit), Alignment.Center) {
                    Icon(Icons.Outlined.Edit, "Change $name", Modifier.size(15.dp), tint = Kit.Muted)
                }
            }
        }
    }
}

/** The shift step: that role's usual shifts as cards (with a pencil to change one), and Add shift. One-off hours are the day window's Specific hours. */
@Composable
private fun ShiftCards(templates: List<ShiftTemplate>, chosen: String?, onPick: (String) -> Unit, onEdit: (ShiftTemplate) -> Unit, onAdd: () -> Unit, onImport: (() -> Unit)? = null) {
    val narrow = LocalNarrowDialog.current
    val cards: List<@Composable (Modifier) -> Unit> = templates.map { option ->
        val card: @Composable (Modifier) -> Unit = { m ->
            TemplateCard(option.name, option.times, hoursText(option.minutes) + if (option.overnight) " · ends next day" else "",
                chosen == option.id, dayPartOf(option.start), m, compact = narrow, forWhom = option.forWhom, picture = option.picture, plan = planText(option), onEdit = { onEdit(option) }) { onPick(option.id) }
        }
        card
    } + listOfNotNull<@Composable (Modifier) -> Unit>({ m: Modifier -> AddShiftCard(m, onAdd) }, onImport?.let { click -> { m: Modifier -> ImportShiftsCard(m, click) } })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (templates.isEmpty()) Text("No shifts for them yet. Add one.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        EvenGrid(cards)
    }
}

/** Cards in even columns (three when each gets about 170 dp), each row one height. */
@Composable
private fun EvenGrid(cards: List<@Composable (Modifier) -> Unit>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = planColumns(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.chunked(columns).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The first step: whose shifts to plan, a card per paid role and one for anyone; they sit in the middle of the window. */
@Composable
private fun CategoryCards(roles: List<TemplateRoleDto>, staff: List<ShiftStaff>, onPick: (String?) -> Unit) {
    val cards: List<@Composable (Modifier) -> Unit> = roles.map { role ->
        val people = staff.count { person -> person.roles.any { it.equals(role.name, ignoreCase = true) } }
        val look = categoryPicture(role)
        val card: @Composable (Modifier) -> Unit = { m ->
            CategoryCard("${role.name} shift", people, m,
                picture = { Image(painterResource(look.image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }, color = look.color) {
                onPick(role.id)
            }
        }
        card
    } + listOf<@Composable (Modifier) -> Unit>({ m ->
        CategoryCard("Shift for anyone", staff.size, m, picture = { Icon(Icons.Outlined.Groups, null, Modifier.fillMaxSize(0.7f), tint = Kit.Blue) }, color = Kit.Blue) { onPick(null) }
    })
    Column(Modifier.fillMaxWidth().heightIn(min = LocalDialogRoom.current ?: 0.dp), verticalArrangement = Arrangement.Center) { EvenGrid(cards) }
}

/** A built-in role's Salary figure, or the picture a restaurant picked for its own role (the general one otherwise). */
internal fun categoryPicture(role: TemplateRoleDto): com.saporini.mobile_desktop.admin.people.ui.RolePicture {
    val key = when (role.code) {
        "MANAGER" -> "salary_managers"; "WAITER" -> "salary_waiters"; "KITCHEN" -> "salary_kitchen"; "CLEANER" -> "salary_cleaning"
        "EXTRA_WORKER" -> "salary_extra"; else -> role.picture ?: com.saporini.mobile_desktop.admin.people.ui.DefaultRolePicture
    }
    return com.saporini.mobile_desktop.admin.people.ui.rolePicture(key) ?: com.saporini.mobile_desktop.admin.people.ui.RolePictures.first()
}

/** Whose shifts: how many people on top, the picture in the middle of a band of its colour, "<role> shift" under it. */
@Composable
private fun CategoryCard(title: String, people: Int, modifier: Modifier, picture: @Composable () -> Unit, color: Color, onClick: () -> Unit) {
    val narrow = LocalNarrowDialog.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), color = Color.White,
        border = BorderStroke(if (hovered) 1.5.dp else 1.dp, if (hovered) color.copy(alpha = 0.55f) else Kit.Border),
        shadowElevation = if (hovered) 6.dp else 1.dp, interactionSource = interaction
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(if (narrow) 136.dp else 196.dp).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.06f), color.copy(alpha = 0.18f))))) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Outlined.Groups, null, Modifier.size(14.dp), tint = color)
                    Text(if (people == 1) "1 person" else "$people people", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
                }
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(if (narrow) 88.dp else 136.dp), Alignment.Center) { picture() }
            }
            Text(
                title, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                fontSize = if (narrow) 14.sp else 16.sp, color = Kit.Ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Copying another role's shifts in, next to Add shift. */
@Composable
private fun ImportShiftsCard(modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.Green.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Kit.GreenSoft), Alignment.Center) {
            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp), tint = Kit.Green)
        }
        Text("Import shifts", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Green)
    }
}

/** The roles with shifts of their own that [target] doesn't have yet. */
private fun importSources(target: TemplateRoleDto, roles: List<TemplateRoleDto>, templates: List<ShiftTemplate>) =
    roles.filter { role -> role.id != target.id && templates.any { shift -> shift.roles.any { it.id == role.id } && shift.roles.none { it.id == target.id } } }

/**
 * Copies another role's shifts to [target], with their days and length (user, 2026-10-31: "make the kitchen the same as the
 * manager, just import"): pick the role, untick what you don't want. They're copies, so changing one leaves the other. A shift
 * the role already has (same name and times) is left out.
 */
@Composable
internal fun ImportShiftsDialog(target: TemplateRoleDto, roles: List<TemplateRoleDto>, templates: List<ShiftTemplate>, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val sources = importSources(target, roles, templates)
    var source by remember { mutableStateOf(sources.firstOrNull()?.id) }
    var left by remember(source) { mutableStateOf(emptySet<String>()) }
    val shifts = templates.filter { shift -> shift.roles.any { it.id == source } && shift.roles.none { it.id == target.id } }
    val already = shifts.filter { shift -> templates.any { other -> other.roles.any { it.id == target.id } && other.name == shift.name && other.start == shift.start && other.end == shift.end } }.map { it.id }.toSet()
    val picked = shifts.filter { it.id !in left && it.id !in already }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AppDialog(
        "Import shifts to ${target.name}", onDismiss, busy = busy, maxWidth = 680.dp, subtitle = "They're copies, so you can change them after",
        buttons = {
            KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY, enabled = !busy)
            KitButton(if (picked.size == 1) "Import 1 shift" else "Import ${picked.size} shifts", {
                busy = true; problem = null
                scope.launch {
                    var done = 0
                    for (shift in picked) {
                        val answer = ShiftTemplates.copy(shift, target)
                        if (answer != null) { problem = answer; break }
                        done++
                    }
                    busy = false
                    if (problem == null) onDone(done)
                }
            }, icon = Icons.Outlined.ContentCopy, enabled = picked.isNotEmpty(), loading = busy)
        }
    ) {
        problem?.let { Text(it, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Danger) }
        Text("From", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.forEach { role ->
                val chosen = role.id == source
                val look = categoryPicture(role)
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (chosen) Kit.GreenSoft else Color.White)
                        .border(if (chosen) 1.5.dp else 1.dp, if (chosen) Kit.Green else Kit.Border, RoundedCornerShape(12.dp))
                        .pointerHoverIcon(PointerIcon.Hand).clickable { source = role.id }.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(painterResource(look.image), null, Modifier.size(34.dp), contentScale = ContentScale.Fit)
                    Text(role.name, fontFamily = Inter(), fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp, color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1)
                }
            }
        }
        if (shifts.isNotEmpty()) Text("Shifts", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        shifts.forEach { shift ->
            val there = shift.id in already
            val ticked = !there && shift.id !in left
            val part = dayPartOf(shift.start)
            val tone = dayPartColor(part)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (ticked) Kit.GreenSoft else Color.White)
                    .border(if (ticked) 1.5.dp else 1.dp, if (ticked) Kit.Green else Kit.Border, RoundedCornerShape(12.dp))
                    .clickable(enabled = !there) { left = if (shift.id in left) left - shift.id else left + shift.id }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.13f)), Alignment.Center) {
                    Icon(shift.picture, null, Modifier.size(19.dp), tint = tone)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(shift.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${shift.times} · ${hoursText(shift.minutes)} · ${planText(shift) ?: "No days yet"}", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (there) Text("Already there", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                else Box(Modifier.size(20.dp).clip(CircleShape).background(if (ticked) Kit.Green else Color.White)
                    .border(1.5.dp, if (ticked) Kit.Green else Kit.Faint, CircleShape), Alignment.Center) {
                    if (ticked) Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = Color.White)
                }
            }
        }
    }
}

/** The last card of the shifts: add a usual shift. */
@Composable
private fun AddShiftCard(modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Kit.GreenSoft.copy(alpha = 0.5f))
            .border(1.dp, Kit.Green.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Kit.Green), Alignment.Center) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = Color.White)
        }
        Text("Add shift", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Green)
    }
}

/**
 * Adds a usual shift or changes one: its name, start and end, and the roles it's for (Everyone, or some of the paid
 * roles). Saved on the server; [onDone] gets the saved shift's id (null when removed).
 */
@Composable
internal fun ShiftTemplateDialog(template: ShiftTemplate?, forRole: TemplateRoleDto? = null, onDismiss: () -> Unit, onDone: (String?) -> Unit) {
    val roleChoices by ShiftTemplates.roles.collectAsState()
    var name by remember { mutableStateOf(template?.name.orEmpty()) }
    var start by remember { mutableStateOf(template?.start ?: LocalTime(9, 0)) }
    var end by remember { mutableStateOf(template?.end ?: LocalTime(17, 0)) }
    var roleIds by remember { mutableStateOf(template?.roles?.map { it.id }?.toSet() ?: setOfNotNull(forRole?.id)) }
    // Until one is picked it follows the start time.
    var icon by remember { mutableStateOf(template?.icon) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val save = {
        busy = true; problem = null
        scope.launch {
            val answer = ShiftTemplates.save(template?.id, name.trim(), start, end, roleIds.toList(), icon)
            busy = false
            if (answer == null) onDone(ShiftTemplates.all.value.lastOrNull { it.name == name.trim() && it.start == start && it.end == end }?.id ?: template?.id)
            else problem = answer
        }
        Unit
    }
    val remove = {
        val id = template?.id
        if (id != null) {
            busy = true; problem = null
            scope.launch {
                val answer = ShiftTemplates.delete(id)
                busy = false
                if (answer == null) onDone(null) else problem = answer
            }
        }
        Unit
    }
    AppDialog(
        when {
            template != null -> "Change shift"
            forRole != null -> "Add ${forRole.name} shift"
            else -> "Add shift"
        }, onDismiss, busy = busy, maxWidth = if (forRole != null) 480.dp else 560.dp,
        subtitle = if (forRole != null) null else "A usual shift and who it's for",
        buttons = {
            if (template != null) KitButton("Remove", remove, style = ButtonStyle.DANGER_OUTLINE, icon = Icons.Outlined.DeleteOutline, enabled = !busy)
            Spacer(Modifier.weight(1f))
            KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY, enabled = !busy)
            KitButton(if (template == null) "Add shift" else "Save", save, icon = Icons.Outlined.Check, loading = busy,
                enabled = name.isNotBlank() && start != end)
        }
    ) {
        problem?.let { Text(it, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Danger) }
        TextInput(name, { name = it.take(60) }, label = "Name", required = true, placeholder = if (forRole != null) "Morning" else "Waiters' morning", icon = Icons.Outlined.Badge, maxLength = 60)
        FieldPair(
            { m -> ShiftTimeField("Starts", start, m) { start = it } },
            { m -> ShiftTimeField("Ends", end, m, hint = if (end <= start) "The next day" else null) { end = it } }
        )
        val tone = dayPartColor(dayPartOf(start))
        Text("Icon", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        ShiftIconPicker(icon ?: startIconKey(start), tone) { icon = it }
        // How it will look: the icon, the name, the times and how long it is.
        if (start == end) Text("Start and end can't be the same.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger)
        else {
            val shift = ShiftTemplate("", name.trim().ifBlank { "New shift" }, start, end, icon = icon)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(tone.copy(alpha = 0.13f)), Alignment.Center) {
                    Icon(shift.picture, null, Modifier.size(19.dp), tint = tone)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(shift.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${shift.times} · ${hoursText(shift.minutes)}", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
                }
            }
        }
        if (forRole == null) {
            Text("For", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallToggle("Everyone", roleIds.isEmpty(), icon = Icons.Outlined.Groups) { roleIds = emptySet() }
                roleChoices.forEach { role ->
                    SmallToggle(role.name, role.id in roleIds) { roleIds = if (role.id in roleIds) roleIds - role.id else roleIds + role.id }
                }
            }
            Text(if (roleIds.isEmpty()) "Anyone who gets paid can be put on it." else "Only people with these roles can be put on it.",
                fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted)
        }
    }
}

/** The icons a usual shift can have, by name (kept on the server). The first three are what one without its own shows. */
private val ShiftIcons = listOf(
    "sunrise" to Icons.Outlined.WbTwilight, "sun" to Icons.Outlined.WbSunny, "moon" to Icons.Outlined.NightsStay, "coffee" to Icons.Outlined.LocalCafe,
    "lunch" to Icons.Outlined.LunchDining, "dinner" to Icons.Outlined.DinnerDining, "restaurant" to Icons.Outlined.Restaurant, "kitchen" to Icons.Outlined.SoupKitchen,
    "bar" to Icons.Outlined.LocalBar, "cleaning" to Icons.Outlined.CleaningServices, "delivery" to Icons.Outlined.DeliveryDining, "event" to Icons.Outlined.Celebration,
    "open" to Icons.Outlined.Storefront, "close" to Icons.Outlined.Lock, "clock" to Icons.Outlined.Schedule, "star" to Icons.Outlined.Star
)

/** The icon a shift without its own shows: the part of the day it starts in. */
private fun startIconKey(start: LocalTime) = when (dayPartOf(start)) {
    DayPart.MORNING -> "sunrise"
    DayPart.AFTERNOON -> "sun"
    DayPart.EVENING -> "moon"
}

/** The icon this shift shows. */
private val ShiftTemplate.picture: androidx.compose.ui.graphics.vector.ImageVector
    get() = ShiftIcons.firstOrNull { it.first == icon }?.second ?: dayPartIcon(dayPartOf(start))

/** The icons as a grid of tiles; the chosen one has a green frame, in the colour of its part of the day. */
@Composable
private fun ShiftIconPicker(chosen: String, tone: Color, onPick: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 360.dp) 8 else 6
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShiftIcons.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (key, picture) ->
                        val selected = key == chosen
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(if (selected) tone.copy(alpha = 0.13f) else Color.White)
                                .border(if (selected) 2.dp else 1.dp, if (selected) Kit.Green else Kit.Border, RoundedCornerShape(10.dp)).clickable { onPick(key) },
                            Alignment.Center
                        ) { Icon(picture, key, Modifier.size(22.dp), tint = if (selected) tone else Kit.Muted) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val WeekdayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** The weekdays to tick (0 = Monday), in full when each gets the room. */
@Composable
private fun WeekdayToggles(chosen: Set<Int>, onToggle: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val whole = maxWidth >= 560.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (maxWidth < 420.dp) 4.dp else 8.dp)) {
            WeekdayNames.forEachIndexed { index, name ->
                val selected = index in chosen
                val hover = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val hovered by hover.collectIsHoveredAsState()
                // A tick circle like the people's and the options', so a day reads as something to tick (user, 2026-10-31).
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (selected) Kit.GreenSoft else if (hovered) Kit.Canvas else Color.White)
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else if (hovered) Kit.Green.copy(alpha = 0.5f) else Kit.Border, RoundedCornerShape(10.dp))
                        .pointerHoverIcon(PointerIcon.Hand).hoverable(hover).clickable { onToggle(index) }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(if (selected) Kit.Green else Color.White)
                        .border(1.5.dp, if (selected) Kit.Green else if (hovered) Kit.Green.copy(alpha = 0.6f) else Kit.Faint, CircleShape), Alignment.Center) {
                        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(12.dp), tint = Color.White)
                    }
                    Text(if (whole) name else name.take(3), fontFamily = Inter(), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = if (whole) 12.5.sp else 12.sp,
                        color = if (selected) Kit.Green else Kit.Ink, maxLines = 1)
                }
            }
        }
    }
}

/** When the plan starts: a small button that opens the date picker; never before today. */
@Composable
private fun StartsPicker(date: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.CalendarToday, null, Modifier.size(15.dp), tint = Kit.Muted)
            Text(if (date == today) "Starts today" else "Starts ${date.weekdayShort()} ${date.shortDay()}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(15.dp), tint = Kit.Muted)
        }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
            com.saporini.mobile_desktop.pos.reservations.CompactDatePicker(selectedDate = date, onDateSelected = {
                onPick(maxOf(it, today))
                open = false
            })
        }
    }
}

@Composable
internal fun SmallToggle(text: String, selected: Boolean, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.height(32.dp).clip(RoundedCornerShape(50)).background(if (selected) Kit.Green else Color.White)
            .border(1.dp, if (selected) Kit.Green else Kit.Border, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
    ) {
        icon?.let { Icon(it, null, Modifier.size(14.dp), tint = if (selected) Color.White else Kit.Muted) }
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (selected) Color.White else Kit.Ink, maxLines = 1)
    }
}

// ---------- Shared looks ----------

private val AvatarBackgrounds = listOf(Color(0xFFE4F1E8), Color(0xFFE6EEFB), Color(0xFFFFEBD3), Color(0xFFFBE4EA), Color(0xFFEEE7FB), Color(0xFFE0F2F4))
private val AvatarInks = listOf(Color(0xFF287247), Color(0xFF3C61B5), Color(0xFFB36412), Color(0xFFB34C65), Color(0xFF7350B6), Color(0xFF1F7A83))

/** A person's initials in a soft circle whose colour stays the same for that name. */
@Composable
internal fun ShiftAvatar(name: String, size: Dp = 36.dp) {
    val index = (name.hashCode().toLong().and(0x7fffffff) % AvatarBackgrounds.size).toInt()
    Box(Modifier.size(size).clip(CircleShape).background(AvatarBackgrounds[index]), contentAlignment = Alignment.Center) {
        Text(name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifBlank { "?" },
            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp, color = AvatarInks[index])
    }
}

internal fun dayPartColor(part: DayPart): Color = when (part) {
    DayPart.MORNING -> Color(0xFFD99A1E)
    DayPart.AFTERNOON -> Kit.Blue
    DayPart.EVENING -> Kit.Purple
}

internal fun dayPartIcon(part: DayPart) = when (part) {
    DayPart.MORNING -> Icons.Outlined.WbTwilight
    DayPart.AFTERNOON -> Icons.Outlined.WbSunny
    DayPart.EVENING -> Icons.Outlined.NightsStay
}
