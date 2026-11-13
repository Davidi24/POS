package com.saporini.mobile_desktop.admin.audit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.audit.AuditEntryDto
import com.saporini.mobile_desktop.admin.audit.AuditLogScreenModel
import com.saporini.mobile_desktop.admin.audit.AuditLogState
import com.saporini.mobile_desktop.admin.audit.actionKind
import com.saporini.mobile_desktop.admin.audit.actorLabel
import com.saporini.mobile_desktop.admin.inventory.ui.ListLinesSkeleton
import com.saporini.mobile_desktop.admin.inventory.ui.OptionGroup
import com.saporini.mobile_desktop.admin.inventory.ui.OptionLine
import com.saporini.mobile_desktop.admin.inventory.ui.PhoneControlHeight
import com.saporini.mobile_desktop.admin.inventory.ui.PhoneOptionsButton
import com.saporini.mobile_desktop.admin.inventory.ui.SmallLink
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.LoadMoreWhenNearEnd
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.PagedFooter
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.ScrollList
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import com.saporini.mobile_desktop.pos.shifts.ShiftAvatar
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_reservations
import org.koin.compose.koinInject
import kotlin.time.Clock

// Admin Hub → Audit log, in the look of Inventory → History (user rule): on the left the kinds of change and who made
// them, as filters with their counts; on the right the changes, newest first, a day at a time, each day foldable, with
// the area filter and search on top. Each change: time, a tile in its kind's colour, what changed (area · who), and
// its kind as a chip. Phones and tablets have no room for the side column: there the search sits on top and the kinds,
// the people and the areas fold into one options button (user rule).

private val Kinds = listOf(null, "ADDED", "CHANGED", "REMOVED", "RESET")

/** Admin Hub → Audit Logs: who changed which setting, and when. */
@Composable
internal fun AuditLogScreen(modifier: Modifier = Modifier) {
    val model = koinInject<AuditLogScreenModel>()
    BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    AuditLogContent(state, model, modifier)
}

@Composable
internal fun AuditLogContent(state: AuditLogState, model: AuditLogScreenModel, modifier: Modifier = Modifier) {
    val zone = RestaurantTime.zone
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        // A small tablet keeps each line's time and face but drops the kind chip (the kind leads the second line).
        val chips = maxWidth >= 900.dp
        Column(
            Modifier.fillMaxSize().padding(start = if (size.isPhone) 12.dp else 22.dp, end = if (size.isPhone) 12.dp else 22.dp, top = 18.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(if (size.isPhone) 10.dp else 14.dp)
        ) {
            if (!size.isDesktop) {
                // Phones and tablets: refresh stays beside the title (the page header would give it a row of its own).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Audit log", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (size.isPhone) 19.sp else 20.sp, color = Kit.Ink)
                        Text(if (size.isPhone) "Every change to the settings: what, who and when" else "Every change to the restaurant's settings: what, who and when",
                            fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    RefreshButton(state.loading, model::refresh, size = if (size.isPhone) 38.dp else PageControlHeight)
                }
            } else {
                PageHeader("Audit log", "Every change to the restaurant's settings: what, who and when") { RefreshButton(state.loading, model::refresh) }
            }
            ScreenMessages(state.error, null, model::clearError)
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to the audit log", "Your role needs “View audit log”.")
                state.entries.isEmpty() && state.stale && !state.loading -> PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                size.isDesktop -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ScrollColumn(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        KindPanel(state, model)
                        PeoplePanel(state, model)
                    }
                    ChangesPanel(state, model, zone, size, chips = true, Modifier.weight(1f).fillMaxHeight())
                }
                else -> ChangesPanel(state, model, zone, size, chips, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** The kinds of change with how many of each were loaded; click one to see only those (again for all). */
@Composable
private fun KindPanel(state: AuditLogState, model: AuditLogScreenModel) {
    val counts = state.entries.groupingBy { actionKind(it.action) }.eachCount()
    OverviewPanel(Icons.Outlined.Timeline, "Kind of change", Modifier.fillMaxWidth()) {
        Kinds.forEach { kind ->
            val look = kind?.let(::kindLook)
            FilterRow(look?.icon ?: Icons.Outlined.History, look?.label ?: "Every change", if (kind == null) state.entries.size else counts[kind] ?: 0,
                look?.color ?: Kit.Green, state.kind == kind) { model.kind(if (state.kind == kind || kind == null) null else kind) }
        }
    }
}

/** Who made the changes loaded, most first; click a name to see only their changes. */
@Composable
private fun PeoplePanel(state: AuditLogState, model: AuditLogScreenModel) {
    OverviewPanel(Icons.Outlined.People, "Who changed things", Modifier.fillMaxWidth()) {
        FilterRow(Icons.Outlined.People, "Everyone", state.entries.size, Kit.Green, state.actor == null) { model.actor(null) }
        state.actors.take(8).forEach { (name, count) ->
            FilterRow(Icons.Outlined.Person, name, count, Kit.Blue, state.actor == name) { model.actor(if (state.actor == name) null else name) }
        }
        Text("Counted over the changes loaded.", Modifier.padding(start = 4.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint)
    }
}

/**
 * The changes a day at a time, each day foldable. On a desktop the area filter and the search sit on top (the kinds and
 * people are in the side column); on phones and tablets the search and one options button. [chips]: room for the kind chip.
 */
@Composable
private fun ChangesPanel(state: AuditLogState, model: AuditLogScreenModel, zone: TimeZone, size: ScreenSize, chips: Boolean, modifier: Modifier) {
    var folded by remember { mutableStateOf(setOf<String>()) }
    val visible = state.visible
    val days = visible.map { localOf(it.occurredAt, zone)?.date.toString() }.distinct()
    OverviewPanel(Icons.Outlined.History, "Changes", modifier, count = visible.size, bottomPadding = 0.dp, flat = size.isPhone) {
        val allFolded = days.all { it in folded }
        val foldLink: @Composable () -> Unit = {
            SmallLink(if (allFolded) "Open all days" else "Fold all days", if (allFolded) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess) {
                folded = if (allFolded) emptySet() else days.toSet()
            }
        }
        when {
            size.isDesktop -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectInput(state.area, listOf<String?>(null) + state.areas, { it?.let(::areaLabel) ?: "Every area" }, model::area,
                    Modifier.width(240.dp), icon = Icons.Outlined.Category, placeholder = "Every area")
                SearchField(state.search, model::search, Modifier.weight(1f, fill = false).widthIn(max = 320.dp), placeholder = "Search changes",
                    height = PageControlHeight)
                if (days.size > 1) {
                    Spacer(Modifier.weight(1f))
                    foldLink()
                }
            }
            size.isPhone -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(state.search, model::search, Modifier.weight(1f), placeholder = "Search changes", height = PhoneControlHeight)
                FilterOptions(state, model, PhoneControlHeight)
            }
            else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SearchField(state.search, model::search, Modifier.weight(1f, fill = false).widthIn(max = 380.dp).padding(end = 10.dp),
                    placeholder = "Search changes", height = PageControlHeight)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (days.size > 1) foldLink()
                    FilterOptions(state, model, PageControlHeight)
                }
            }
        }
        if (state.entries.isEmpty() && state.loading) {
            ListLinesSkeleton(8, 60.dp, Modifier.fillMaxWidth().weight(1f))
            return@OverviewPanel
        }
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                if (state.entries.isEmpty()) OverviewEmpty("No changes yet", "Every change to the settings will be listed here, newest first.",
                    Icons.Outlined.History, image = Res.drawable.overview_reservations)
                else OverviewEmpty("Nothing like that", "Try another area, kind, person or word. Older changes load as you scroll.",
                    Icons.AutoMirrored.Outlined.ManageSearch)
            }
            return@OverviewPanel
        }
        val listState = rememberLazyListState()
        LoadMoreWhenNearEnd(listState, state.hasNext, state.loading || state.loadingMore, onLoadMore = model::loadMore)
        val today = Clock.System.now().toLocalDateTime(zone).date
        val byDay = visible.groupBy { localOf(it.occurredAt, zone)?.date }
        ScrollList(Modifier.fillMaxWidth().weight(1f), state = listState, contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            byDay.forEach { (day, entries) ->
                val key = day.toString()
                item(key = "day-$day") { DayHeader(day, today, entries.size, folded = key in folded) { folded = if (key in folded) folded - key else folded + key } }
                if (key !in folded) items(entries, key = { it.id }) { entry ->
                    ChangeLine(entry, zone, compact = size.isPhone, chip = chips, chosen = state.actor == actorLabel(entry)) {
                        model.actor(if (state.actor == actorLabel(entry)) null else actorLabel(entry))
                    }
                }
            }
            item(key = "footer") { PagedFooter(state.entries.size, state.total, state.hasNext, state.loadingMore, state.error != null, model::loadMore) }
        }
    }
}

/** Phones and tablets: the kind of change, who made it and the area in one options button, with how many are on. */
@Composable
private fun FilterOptions(state: AuditLogState, model: AuditLogScreenModel, height: Dp) {
    val counts = state.entries.groupingBy { actionKind(it.action) }.eachCount()
    PhoneOptionsButton(Icons.Outlined.Tune, "Filter changes", listOfNotNull(state.kind, state.actor, state.area).size, height) { close ->
        OptionGroup("Kind of change")
        Kinds.forEach { kind ->
            val look = kind?.let(::kindLook)
            OptionLine(look?.label ?: "Every change", state.kind == kind, { model.kind(kind); close() }, icon = look?.icon ?: Icons.Outlined.History,
                color = look?.color ?: Kit.Green, count = if (kind == null) state.entries.size else counts[kind] ?: 0)
        }
        OptionGroup("Who changed things")
        OptionLine("Everyone", state.actor == null, { model.actor(null); close() }, icon = Icons.Outlined.People, count = state.entries.size)
        state.actors.take(8).forEach { (name, count) ->
            OptionLine(name, state.actor == name, { model.actor(name); close() }, icon = Icons.Outlined.Person, color = Kit.Blue, count = count)
        }
        if (state.areas.isNotEmpty()) {
            OptionGroup("Area")
            OptionLine("Every area", state.area == null, { model.area(null); close() }, icon = Icons.Outlined.Category)
            state.areas.forEach { area ->
                OptionLine(areaLabel(area), state.area == area, { model.area(area); close() }, icon = Icons.Outlined.Category, color = Kit.Grey)
            }
        }
    }
}

/** "Today · Wed 28 Oct", "Yesterday · …" or the date, with how many changes that day; click to fold or open the day. */
@Composable
private fun DayHeader(day: LocalDate?, today: LocalDate, count: Int, folded: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (folded) -90f else 0f, tween(220), label = "day-fold")
    val name = when (day) {
        null -> "Unknown date"
        today -> "Today · ${weekdayDate(day)}"
        today.minus(DatePeriod(days = 1)) -> "Yesterday · ${weekdayDate(day)}"
        else -> weekdayDate(day) + " ${day.year}"
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle)
        .background(if (folded) Kit.Canvas else Color.Transparent).padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.ExpandMore, if (folded) "Open $name" else "Fold $name", Modifier.size(18.dp).graphicsLayer { rotationZ = turn }, tint = Kit.Green)
        Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(Kit.Border))
        Text("$count ${if (count == 1) "change" else "changes"}${if (folded) " · folded" else ""}", fontFamily = Inter(), fontSize = 11.sp,
            color = Kit.Muted, maxLines = 1)
    }
}

/**
 * One change: the time, a tile in its kind's colour, what changed with its area and who did it, the kind as a chip
 * ([chip]; otherwise it leads the second line) and the face of who did it. [compact] (a phone): no time column or face,
 * the time ends the second line.
 */
@Composable
private fun ChangeLine(entry: AuditEntryDto, zone: TimeZone, compact: Boolean, chip: Boolean, chosen: Boolean, onPerson: () -> Unit) {
    val look = kindLook(actionKind(entry.action))
    val who = actorLabel(entry)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.RowBorder)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!compact) Text(timeText(entry.occurredAt, zone), Modifier.width(42.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = Kit.Muted, maxLines = 1)
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(look.color.copy(alpha = 0.12f)), Alignment.Center) {
                Icon(look.icon, null, Modifier.size(19.dp), tint = look.color)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val message = entry.message?.takeIf { it.isNotBlank() } ?: "${look.label} ${areaLabel(entry.entityType).lowercase()}"
                if (compact) {
                    // A phone has no time column: the time sits at the end of the first line, where a long second line can't hide it.
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(timeText(entry.occurredAt, zone), Modifier.padding(top = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                            color = Kit.Muted, maxLines = 1)
                    }
                } else {
                    Text(message, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val area = areaLabel(entry.entityType).ifBlank { null }
                if (compact || !chip) {
                    // The kind leads in its own colour (no room for the chip).
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = look.color, fontWeight = FontWeight.SemiBold)) { append(look.label) }
                        listOfNotNull(area, "by $who").forEach { append(" · "); append(it) }
                    }, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(listOfNotNull(area, "by $who").joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            if (!compact) {
                if (chip) KindChip(look)
                // Who did it: click to see only their changes.
                Box(Modifier.clip(CircleShape).background(if (chosen) Kit.GreenSoft else Color.Transparent).clickable(onClick = onPerson).padding(3.dp)) {
                    ShiftAvatar(who, 30.dp)
                }
            }
        }
    }
}

@Composable
private fun KindChip(look: KindLook) {
    Row(Modifier.width(118.dp), horizontalArrangement = Arrangement.Start) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(look.color.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(look.icon, null, Modifier.size(14.dp), tint = look.color)
            Text(look.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Ink, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
    }
}

/** "SETTINGS_PAYMENTS" → "Payments settings", "SETTINGS" → "General settings", "PRINTER" → "Printer". */
private fun areaLabel(code: String?): String = when {
    code.isNullOrBlank() -> ""
    code == "SETTINGS" -> "General settings"
    code.startsWith("SETTINGS_") -> humanize(code.removePrefix("SETTINGS_")) + " settings"
    else -> humanize(code)
}

private data class KindLook(val label: String, val icon: ImageVector, val color: Color)

private fun kindLook(kind: String): KindLook = when (kind) {
    "ADDED" -> KindLook("Added", Icons.Outlined.AddCircleOutline, Kit.Green)
    "REMOVED" -> KindLook("Removed", Icons.Outlined.DeleteOutline, Kit.Danger)
    "RESET" -> KindLook("Reset", Icons.Outlined.Restore, Kit.Purple)
    else -> KindLook("Changed", Icons.Outlined.EditNote, Kit.Blue)
}
