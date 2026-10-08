package com.saporini.mobile_desktop.admin.audit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.audit.AuditEntryDto
import com.saporini.mobile_desktop.admin.audit.AuditLogScreenModel
import com.saporini.mobile_desktop.admin.audit.AuditLogState
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.ChartPalette
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.InitialsAvatar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.LoadMoreWhenNearEnd
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.PagedFooter
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.SideColumn
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.TimeColumn
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.overview_next_hours
import mobile_desktop.shared.generated.resources.overview_reservations
import mobile_desktop.shared.generated.resources.workspace_admin
import org.koin.compose.koinInject
import kotlin.time.Clock

private const val ALL_AREAS = "All areas"

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
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PageHeader("Audit log", "Every change to the restaurant's settings: what, who and when") {
                SearchField(state.search, model::search, Modifier.width(240.dp), placeholder = "Search changes or people", height = PageControlHeight)
                if (!size.isDesktop) {
                    HeaderDropdown(state.area?.let(::areaLabel) ?: ALL_AREAS, Icons.Outlined.Category, Modifier.width(170.dp),
                        listOf(ALL_AREAS) + state.areas.map(::areaLabel), { label -> model.area(state.areas.firstOrNull { areaLabel(it) == label }) })
                }
                RefreshButton(state.loading, model::refresh)
            }
            ScreenMessages(state.error, null, model::clearError)
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to the audit log", "Your role needs “View audit log”.")
                state.entries.isEmpty() && state.loading -> OverviewPageSkeleton(size)
                state.entries.isEmpty() && state.stale -> PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                else -> {
                    SummaryCardRow(size, auditCards(state, zone))
                    if (size.isDesktop) {
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Timeline(state, model, zone, compact = false, Modifier.weight(1f).fillMaxHeight())
                            SideColumn(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                AreaPanel(state, model)
                                PeoplePanel(state, model)
                            }
                        }
                    } else {
                        Timeline(state, model, zone, compact = size.isPhone, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
}

private fun auditCards(state: AuditLogState, zone: TimeZone): List<@Composable (Modifier) -> Unit> {
    val latest = state.entries.firstOrNull()
    val now = Clock.System.now()
    val today = now.toLocalDateTime(zone).date
    val todayCount = state.entries.count { localOf(it.occurredAt, zone)?.date == today }
    val busiest = state.entries.groupBy { it.actorName ?: "System" }.maxByOrNull { it.value.size }
    return listOf(
        { m -> OverviewStatCard("Changes recorded", "${state.total}", "All time · newest first", Kit.Green, m, image = Res.drawable.overview_reservations) },
        { m -> OverviewStatCard("Today", "$todayCount", if (todayCount == 0) "Nothing changed today" else "changes since midnight", Kit.Blue, m,
            image = Res.drawable.overview_next_hours, imageScale = 1.3f) },
        { m -> OverviewStatCard("Last change", latest?.let { agoText(it.occurredAt, now, zone) } ?: "–",
            latest?.let { "${it.actorName ?: "System"} · ${areaLabel(it.entityType)}" } ?: "No changes yet", Kit.Amber, m, image = Res.drawable.workspace_admin) },
        { m -> OverviewStatCard("Most changes", busiest?.key ?: "–", busiest?.let { "${it.value.size} of the latest ${state.entries.size}" } ?: "No changes yet",
            Kit.Purple, m, image = Res.drawable.overview_guests) }
    )
}

@Composable
private fun Timeline(state: AuditLogState, model: AuditLogScreenModel, zone: TimeZone, compact: Boolean, modifier: Modifier) {
    val visible = state.visible
    OverviewPanel(Icons.Outlined.History, "Changes", modifier, count = visible.size) {
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                if (state.entries.isEmpty()) OverviewEmpty("No changes yet", "Every change to the settings will be listed here.", Icons.Outlined.History,
                    image = Res.drawable.overview_reservations)
                else OverviewEmpty("Nothing matches", "Try another word or area. Older changes load as you scroll.", Icons.AutoMirrored.Outlined.ManageSearch)
            }
            return@OverviewPanel
        }
        val listState = rememberLazyListState()
        LoadMoreWhenNearEnd(listState, state.hasNext, state.loading || state.loadingMore, onLoadMore = model::loadMore)
        val byDay = visible.groupBy { localOf(it.occurredAt, zone)?.date }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 4.dp)) {
            byDay.forEach { (day, entries) ->
                item(key = "day-$day") { GroupLabel(day?.let(::dayTitle) ?: "Unknown date", "${entries.size} ${if (entries.size == 1) "change" else "changes"}") }
                items(entries, key = { it.id }) { entry -> AuditCard(entry, zone, compact) { entry.actorName?.let(model::search) } }
            }
            item(key = "footer") { PagedFooter(state.entries.size, state.total, state.hasNext, state.loadingMore, state.error != null, model::loadMore) }
        }
    }
}

/** "SETTINGS_PAYMENTS" → "Payments settings", "SETTINGS" → "General settings", "PRINTER" → "Printer". */
private fun areaLabel(code: String?): String = when {
    code.isNullOrBlank() -> ""
    code == "SETTINGS" -> "General settings"
    code.startsWith("SETTINGS_") -> humanize(code.removePrefix("SETTINGS_")) + " settings"
    else -> humanize(code)
}

private fun dayTitle(date: LocalDate): String = weekdayDate(date) + " ${date.year}"

private data class ActionLook(val label: String, val icon: ImageVector, val color: Color)

private fun actionLook(action: String?): ActionLook {
    val code = action.orEmpty().uppercase()
    return when {
        "CREATE" in code || "ADD" in code -> ActionLook("Added", Icons.Outlined.AddCircleOutline, Kit.Green)
        "DELETE" in code || "REMOVE" in code -> ActionLook("Removed", Icons.Outlined.DeleteOutline, Kit.Danger)
        "RESET" in code || "RESTORE" in code || "IMPORT" in code -> ActionLook(humanize(action), Icons.Outlined.Restore, Kit.Purple)
        "UPDATE" in code || "CHANGE" in code || "SET" in code -> ActionLook("Changed", Icons.Outlined.EditNote, Kit.Blue)
        else -> ActionLook(humanize(action).ifBlank { "Changed" }, Icons.Outlined.Tune, Kit.Grey)
    }
}

@Composable
private fun AuditCard(entry: AuditEntryDto, zone: TimeZone, compact: Boolean, onPerson: () -> Unit) {
    val look = actionLook(entry.action)
    StripCard(look.color, minHeight = 66.dp) {
        if (!compact) {
            TimeColumn(timeText(entry.occurredAt, zone), look.label, look.color, Modifier.width(76.dp))
            CardDivider()
        }
        Box(Modifier.size(34.dp).background(look.color.copy(alpha = 0.12f), CircleShape), Alignment.Center) {
            Icon(look.icon, null, Modifier.size(18.dp), tint = look.color)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.message?.takeIf { it.isNotBlank() } ?: "${look.label} ${areaLabel(entry.entityType).lowercase()}",
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, color = Kit.Ink,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.entityType?.let { StatusChip(areaLabel(it), look.color) }
                if (compact) Text(timeText(entry.occurredAt, zone), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            }
        }
        val who = entry.actorName ?: if (entry.actorUserId == null) "System" else "Someone"
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable(enabled = entry.actorName != null, onClick = onPerson).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!compact) Text(who, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
            InitialsAvatar(who, size = 30.dp, color = if (entry.actorUserId == null) Kit.Grey else Kit.Green)
        }
    }
}

@Composable
private fun AreaPanel(state: AuditLogState, model: AuditLogScreenModel) {
    val counts = state.entries.groupingBy { it.entityType }.eachCount()
    OverviewPanel(Icons.Outlined.Category, "By area", Modifier.fillMaxWidth(), titleExtra = "latest ${state.entries.size}") {
        FilterRow(Icons.Outlined.Category, ALL_AREAS, state.entries.size, Kit.Green, state.area == null) { model.area(null) }
        state.areas.forEachIndexed { index, area ->
            FilterRow(actionLook("UPDATE").icon, areaLabel(area), counts[area] ?: 0, ChartPalette[index % ChartPalette.size], state.area == area) {
                model.area(if (state.area == area) null else area)
            }
        }
    }
}

@Composable
private fun PeoplePanel(state: AuditLogState, model: AuditLogScreenModel) {
    val people = state.entries.groupingBy { it.actorName ?: "System" }.eachCount().entries.sortedByDescending { it.value }.take(8)
    OverviewPanel(Icons.Outlined.Person, "Who changed things", Modifier.fillMaxWidth()) {
        people.forEachIndexed { index, (name, count) ->
            val chosen = state.search.equals(name, ignoreCase = true)
            FilterRow(Icons.Outlined.Person, name, count, ChartPalette[index % ChartPalette.size], chosen) {
                model.search(if (chosen) "" else name)
            }
        }
    }
}
