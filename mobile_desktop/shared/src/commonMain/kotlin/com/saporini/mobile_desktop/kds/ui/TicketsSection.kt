package com.saporini.mobile_desktop.kds.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.CompactStat
import com.saporini.mobile_desktop.core.components.CountPill
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.LiveBadge
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.ToolbarButton
import com.saporini.mobile_desktop.core.components.ToolbarIconButton
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.PlatformVerticalScrollbar
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.kds.KdsScreenModel
import com.saporini.mobile_desktop.kds.model.KdsAllDayItem
import com.saporini.mobile_desktop.kds.model.KdsConnection
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import kotlin.time.Instant

private const val ALL_STATIONS = "All stations"

/** The cooks' board: new, cooking and ready tickets side by side, with an "all day" count of what's still to make. */
@Composable
internal fun TicketsSection(state: KdsState, model: KdsScreenModel, size: ScreenSize, now: Instant, onStations: (() -> Unit)?) {
    var showAllDay by rememberSaveable { mutableStateOf(true) }
    var phoneLane by rememberSaveable { mutableStateOf(Lane.NEW) }
    // Stations seen on the board, so the picker still lists them all while one is chosen.
    val known = remember(state.scope) { mutableStateMapOf<String, String>() }
    LaunchedEffect(state.boards) { state.boards.forEach { known[it.stationId] = it.stationName.ifBlank { it.stationCode ?: "Station" } } }
    val tickets = state.visibleTickets
    val byLane = Lane.entries.associateWith { lane -> tickets.filter { it.lane() == lane } }
    val timing = state.posTiming
    val late = tickets.count { it.lane() != Lane.READY && (minutesBetween(it.since(), now) ?: 0) >= timing.slowAfterMinutes }
    val oldest = byLane.getValue(Lane.NEW).mapNotNull { minutesBetween(it.since(), now) }.maxOrNull()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader("Kitchen", state.stationId?.let { known[it] } ?: if (state.boards.size == 1) state.boards.first().stationName else "Every station") {
            LiveBadge(state.connection == KdsConnection.LIVE, offlineText = if (state.connection == KdsConnection.CONNECTING) "Connecting" else "Reconnecting")
            SearchField(state.filter.query, { model.setFilter(state.filter.copy(query = it)) }, Modifier.width(if (size.isPhone) 180.dp else 230.dp),
                placeholder = "Table, dish or order", height = PageControlHeight)
            if (known.size > 1 || state.stationId != null) {
                HeaderDropdown(state.stationId?.let { known[it] } ?: ALL_STATIONS, Icons.Outlined.Kitchen, Modifier.width(170.dp),
                    listOf(ALL_STATIONS) + known.values.sorted(), { name -> model.selectStation(known.entries.firstOrNull { it.value == name }?.key) })
            }
            if (size.isDesktop) ToolbarButton(if (showAllDay) "Hide all day" else "All day", Icons.AutoMirrored.Outlined.FormatListBulleted) { showAllDay = !showAllDay }
            onStations?.let { ToolbarIconButton(Icons.Outlined.Settings, "Kitchen stations", onClick = it) }
            RefreshButton(state.loading, { model.refresh() })
        }
        val cards: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> CompactStat("New", "${byLane.getValue(Lane.NEW).size}", Lane.NEW.color, m, detail = oldest?.let { "oldest $it min" }, icon = Lane.NEW.icon,
                onClick = if (size.isDesktop) null else ({ phoneLane = Lane.NEW })) },
            { m -> CompactStat("Cooking", "${byLane.getValue(Lane.COOKING).size}", Lane.COOKING.color, m, icon = Lane.COOKING.icon,
                onClick = if (size.isDesktop) null else ({ phoneLane = Lane.COOKING })) },
            { m -> CompactStat("On the pass", "${byLane.getValue(Lane.READY).size}", Lane.READY.color, m, detail = "waiting to be served", icon = Lane.READY.icon,
                onClick = if (size.isDesktop) null else ({ phoneLane = Lane.READY })) },
            { m -> CompactStat("Late", "$late", if (late > 0) Kit.Danger else Kit.Grey, m, detail = "over ${timing.slowAfterMinutes} min", icon = Icons.Outlined.Timer,
                valueColor = if (late > 0) Kit.Danger else Kit.Ink) }
        )
        if (size.isDesktop) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards.forEach { it(Modifier.weight(1f)) } }
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards[0](Modifier.weight(1f)); cards[1](Modifier.weight(1f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards[2](Modifier.weight(1f)); cards[3](Modifier.weight(1f)) }
        }
        val filtering = state.filter.query.isNotBlank()
        val lane: @Composable (Lane, Modifier) -> Unit = { which, m ->
            LaneColumn(which, byLane.getValue(which), state, now, filtering, m,
                onAction = { ticket, action, itemId -> model.perform(ticket.id, action, itemId) }, onOpen = { model.selectTicket(it.id) })
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val sideBySide = size.isDesktop || maxWidth >= 860.dp
            if (sideBySide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Lane.entries.forEach { lane(it, Modifier.weight(1f).fillMaxHeight()) }
                    if (showAllDay && size.isDesktop) AllDayPanel(state.allDay, Modifier.width(290.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewTabs(Lane.entries.map { "${it.title} (${byLane.getValue(it).size})" }, "${phoneLane.title} (${byLane.getValue(phoneLane).size})",
                        { label -> phoneLane = Lane.entries.first { label.startsWith(it.title) } }, Modifier.fillMaxWidth())
                    lane(phoneLane, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun LaneColumn(
    lane: Lane,
    tickets: List<KdsTicket>,
    state: KdsState,
    now: Instant,
    filtering: Boolean,
    modifier: Modifier,
    onAction: (KdsTicket, com.saporini.mobile_desktop.kds.model.KdsAction, String?) -> Unit,
    onOpen: (KdsTicket) -> Unit
) {
    val color = lane.color
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.035f), border = BorderStroke(1.dp, color.copy(alpha = 0.22f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)), Alignment.Center) {
                    Icon(lane.icon, null, Modifier.size(18.dp), tint = color)
                }
                Spacer(Modifier.width(10.dp))
                Text(lane.title, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink)
                CountPill(tickets.size, color = color, background = Color.White)
            }
            if (tickets.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    LaneEmpty(lane, filtering)
                }
            } else {
                val listState = rememberLazyListState()
                Box(Modifier.weight(1f)) {
                    LazyColumn(Modifier.padding(end = 6.dp), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 6.dp)) {
                        items(tickets, key = { it.id }) { ticket ->
                            TicketCard(ticket, lane, state, now, { action, itemId -> onAction(ticket, action, itemId) }, { onOpen(ticket) })
                        }
                    }
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                        PlatformVerticalScrollbar(listState, Modifier.fillMaxHeight().width(3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LaneEmpty(lane: Lane, filtering: Boolean) {
    val (title, hint) = when {
        filtering -> "Nothing found" to "Try another table, dish or order number."
        lane == Lane.NEW -> "No new tickets" to "New orders appear here as soon as they're sent."
        lane == Lane.COOKING -> "Nothing on the stove" to "Tickets move here when you start them."
        else -> "The pass is clear" to "Ready food waits here until it's served."
    }
    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(lane.color.copy(alpha = 0.1f)), Alignment.Center) {
            Icon(if (filtering) Icons.Outlined.SearchOff else lane.icon, null, Modifier.size(26.dp), tint = lane.color)
        }
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
        Text(hint, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted, textAlign = TextAlign.Center)
    }
}

/** Every dish still to make across the open tickets, added up: what the cook preps in one go. */
@Composable
private fun AllDayPanel(items: List<KdsAllDayItem>, modifier: Modifier) {
    OverviewPanel(Icons.AutoMirrored.Outlined.FormatListBulleted, "All day", modifier, count = items.sumOf { it.pending + it.preparing }) {
        if (items.isEmpty()) {
            OverviewCompactEmpty("Nothing to make", "Dishes on open tickets add up here.", Icons.Outlined.Kitchen)
            return@OverviewPanel
        }
        val listState = rememberLazyListState()
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items.sortedByDescending { it.pending + it.preparing }, key = { "${it.menuItemId}-${it.name}-${it.variant}-${it.notes}-${it.modifiers.hashCode()}" }) { item ->
                val open = item.pending + item.preparing
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8F9F7)).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("$open", Modifier.width(36.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        color = if (open > 0) Kit.Ink else Kit.Faint)
                    Column(Modifier.weight(1f)) {
                        Text(listOfNotNull(item.name, item.variant).joinToString(" · "), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val extras = listOfNotNull(item.modifiers.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }, item.notes?.let { "“$it”" })
                        if (extras.isNotEmpty()) Text(extras.joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(item.pending.takeIf { it > 0 }?.let { "$it to start" }, item.preparing.takeIf { it > 0 }?.let { "$it cooking" },
                            item.ready.takeIf { it > 0 }?.let { "$it ready" }).joinToString(" · "), fontFamily = Inter(), fontSize = 10.sp, color = Kit.Faint)
                    }
                }
            }
        }
    }
}
