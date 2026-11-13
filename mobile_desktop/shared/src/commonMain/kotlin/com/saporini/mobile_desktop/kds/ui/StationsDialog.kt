package com.saporini.mobile_desktop.kds.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.produceState
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.SearchableSelect
import com.saporini.mobile_desktop.kds.data.KdsMenuEntry
import com.saporini.mobile_desktop.kds.data.KdsMenuRepository
import com.saporini.mobile_desktop.kds.model.KdsRoutingInput
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.KitSwitch
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToggleRow
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.kds.KdsScreenModel
import com.saporini.mobile_desktop.kds.model.KdsDevice
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.kds.model.KdsStation
import com.saporini.mobile_desktop.kds.model.KdsStationInput
import com.saporini.mobile_desktop.kds.model.toInput

/** The kitchen's stations (grill, fryer, pass…): add one, rename it, give it a screen, switch it on or off. */
@Composable
internal fun StationsDialog(
    state: KdsState,
    model: KdsScreenModel,
    menuSource: KdsMenuRepository = koinInject(),
    // A station to open straight in the editor.
    openStation: String? = null,
    onClose: () -> Unit
) {
    LaunchedEffect(Unit) { model.loadStationSettings() }
    // The kitchen's dishes, for choosing which ones each station makes.
    val restaurantId = state.scope?.restaurantId
    val dishes by produceState<List<KdsMenuEntry>?>(null, restaurantId, state.canReadMenu) {
        value = if (restaurantId == null || !state.canReadMenu) emptyList() else try {
            menuSource.load(restaurantId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
    // The station being edited (null id for a new one), or null while the list shows.
    var editing by remember { mutableStateOf<Pair<String?, KdsStationInput>?>(null) }
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(openStation, state.stations) {
        if (!opened) state.stations.firstOrNull { it.id == openStation }?.let { editing = it.id to it.toInput(); opened = true }
    }
    val busy = state.busyKeys.any { it.startsWith("station:") }
    val draft = editing
    if (draft != null) {
        val names = state.stations.flatMap { it.routings }.associate { it.menuItemId to (it.menuItemName ?: "Dish") } +
            dishes.orEmpty().associate { it.item.id to it.item.name }
        StationEditor(draft.first, draft.second, state, busy, dishes, names, { editing = draft.first to it }, onBack = { editing = null }) {
            // The list is read again afterwards, so screens and dish counts are current.
            model.saveStation(draft.first, draft.second)?.invokeOnCompletion { model.loadStationSettings() }
            editing = null
        }
        return
    }
    AppDialog("Kitchen stations", onClose, subtitle = "Each station has its own board of tickets.", maxWidth = 620.dp,
        buttons = {
            KitButton("Close", onClose, style = ButtonStyle.SECONDARY)
            KitButton("New station", { editing = null to KdsStationInput("", "PREP", displayOrder = state.stations.size) }, icon = Icons.Outlined.Add)
        }) {
        (state.stationError ?: state.actionError)?.let { MessageBar(it.message, MessageKind.ERROR) }
        if (state.stations.isEmpty()) {
            OverviewCompactEmpty(if (state.stationLoading) "Loading stations…" else "No stations yet",
                "Add the places food is made, like the grill or the pastry corner.", Icons.Outlined.Kitchen)
        }
        state.stations.sortedBy { it.displayOrder }.forEach { station ->
            StationRow(station, busy || "station:${station.id}" in state.busyKeys,
                onEdit = { editing = station.id to station.toInput() },
                onActive = { active -> model.saveStation(station.id, station.toInput().copy(active = active)) })
        }
    }
}

@Composable
private fun StationRow(station: KdsStation, busy: Boolean, onEdit: () -> Unit, onActive: (Boolean) -> Unit) {
    Surface(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Kit.RowBorder)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).alpha(if (station.active) 1f else 0.6f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Green.copy(alpha = 0.12f)), Alignment.Center) {
                Icon(stationTypeIcon(station.stationType), null, Modifier.size(20.dp), tint = Kit.Green)
            }
            Column(Modifier.weight(1f)) {
                Text(station.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(stationTypeLabel(station.stationType), station.deviceName?.let { "on $it" } ?: "no screen",
                    "${station.routings.size} dishes").joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
            }
            KitSwitch(station.active, onActive, enabled = !busy, compact = true)
        }
    }
}

@Composable
private fun StationEditor(
    id: String?,
    input: KdsStationInput,
    state: KdsState,
    busy: Boolean,
    dishes: List<KdsMenuEntry>?,
    names: Map<String, String>,
    onChange: (KdsStationInput) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val problem = input.problem()
    AppDialog(if (id == null) "New station" else "Edit ${input.name.ifBlank { "station" }}", onBack, maxWidth = 620.dp, busy = busy,
        buttons = {
            KitButton("Back", onBack, style = ButtonStyle.SECONDARY)
            KitButton(if (id == null) "Add station" else "Save", onSave, icon = Icons.Outlined.Check, enabled = problem == null, loading = busy)
        }) {
        state.actionError?.let { MessageBar(it.message, MessageKind.ERROR) }
        TextInput(input.name, { onChange(input.copy(name = it)) }, label = "Name", required = true, icon = Icons.Outlined.Kitchen, placeholder = "Grill",
            maxLength = 150)
        Text("Kind", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        StationTypes.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { type ->
                    val chosen = input.stationType == type
                    Surface(onClick = { onChange(input.copy(stationType = type)) }, modifier = Modifier.weight(1f).height(68.dp), shape = RoundedCornerShape(10.dp),
                        color = if (chosen) Kit.GreenSoft else Color.White, border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) Kit.Green else Kit.Border)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(stationTypeIcon(type), null, Modifier.size(20.dp), tint = if (chosen) Kit.Green else Kit.Ink)
                            Text(stationTypeLabel(type), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (chosen) Kit.Green else Kit.Ink)
                        }
                    }
                }
            }
        }
        FieldPair(
            { m -> TextInput(input.code.orEmpty(), { onChange(input.copy(code = it.ifBlank { null })) }, m, label = "Short code", optional = true,
                icon = Icons.Outlined.Badge, placeholder = "GRL", maxLength = 80) },
            { m -> TextInput(input.screenLabel.orEmpty(), { onChange(input.copy(screenLabel = it.ifBlank { null })) }, m, label = "Name on the screen",
                optional = true, maxLength = 80) }
        )
        val screens = listOf<KdsDevice?>(null) + state.devices.filter { it.active && (it.assignedStationId == null || it.assignedStationId == id) }
        SelectInput(state.devices.firstOrNull { it.deviceId == input.deviceId }, screens, { it?.deviceName?.ifBlank { it.deviceCode } ?: "No screen" },
            { onChange(input.copy(deviceId = it?.deviceId)) }, label = "Screen", icon = Icons.Outlined.Tv,
            hint = "The kitchen screen that shows this station's tickets. Screens are added in Admin Hub → Devices.")
        ToggleRow("Takes timed orders", input.acceptsScheduledOrders, { onChange(input.copy(acceptsScheduledOrders = it)) },
            detail = "Food ordered for later (and with bookings) can come to this station.")
        ToggleRow("In use", input.active, { onChange(input.copy(active = it)) })
        RoutingEditor(input, dishes, names, onChange)
        problem?.takeIf { input.name.isNotBlank() }?.let { MessageBar(it, MessageKind.WARNING) }
    }
}

private val RoutingPriorities = listOf("NORMAL", "RUSH", "VIP", "HOLD_FIRE")

private fun routingPriorityLabel(priority: String): String = when (priority) {
    "RUSH" -> "Rush"
    "VIP" -> "VIP"
    "HOLD_FIRE" -> "Hold until sent"
    else -> "Normal"
}

/** Which dishes this station makes, each with its priority and course name on the ticket. */
@Composable
private fun RoutingEditor(input: KdsStationInput, dishes: List<KdsMenuEntry>?, names: Map<String, String>, onChange: (KdsStationInput) -> Unit) {
    val routings = input.routings
    Row(verticalAlignment = Alignment.CenterVertically) {
        Caption("Dishes made here", Modifier.weight(1f))
        Text("${routings.count { it.active }} of ${routings.size} on", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
    }
    if (routings.isEmpty()) {
        Text("No dishes yet. Orders of the dishes added here show up on this station's board.", fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp,
            color = Kit.Muted)
    }
    routings.forEachIndexed { index, routing ->
        fun update(change: (KdsRoutingInput) -> KdsRoutingInput) = onChange(input.copy(routings = routings.mapIndexed { i, r -> if (i == index) change(r) else r }))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8F9F7)).padding(horizontal = 10.dp, vertical = 6.dp)
            .alpha(if (routing.active) 1f else 0.6f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(names[routing.menuItemId] ?: "Dish", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            SelectInput(routing.priority, RoutingPriorities, ::routingPriorityLabel, { p -> update { it.copy(priority = p) } }, Modifier.width(130.dp))
            TextInput(routing.courseLabel.orEmpty(), { c -> update { it.copy(courseLabel = c.ifBlank { null }) } }, Modifier.width(120.dp), placeholder = "Course",
                maxLength = 50)
            KitSwitch(routing.active, { a -> update { it.copy(active = a) } }, compact = true)
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(50)).clickable {
                onChange(input.copy(routings = routings.filterIndexed { i, _ -> i != index }.mapIndexed { i, r -> r.copy(displayOrder = i) }))
            }, Alignment.Center) {
                Icon(Icons.Outlined.DeleteOutline, "Take ${names[routing.menuItemId] ?: "the dish"} off this station", Modifier.size(17.dp), tint = Kit.Danger)
            }
        }
    }
    val routed = routings.map { it.menuItemId }.toSet()
    val addable = dishes.orEmpty().filter { it.item.id !in routed }
    when {
        dishes == null -> Text("Loading the kitchen's dishes…", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        addable.isNotEmpty() -> SearchableSelect<KdsMenuEntry>(null, addable, { it.item.name }, { entry ->
            onChange(input.copy(routings = routings + KdsRoutingInput(entry.item.id, displayOrder = routings.size)))
        }, label = "Add a dish", icon = Icons.Outlined.Add, detailOf = { "${it.sectionName} · ${it.menuName}" }, placeholder = "Choose a dish")
    }
}
