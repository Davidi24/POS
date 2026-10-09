package com.saporini.mobile_desktop.admin.devices.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.devices.DEVICE_STATUSES
import com.saporini.mobile_desktop.admin.devices.DeviceAssignmentDto
import com.saporini.mobile_desktop.admin.devices.DeviceDto
import com.saporini.mobile_desktop.admin.devices.DevicesScreenModel
import com.saporini.mobile_desktop.admin.devices.DevicesState
import com.saporini.mobile_desktop.admin.devices.PairingCodeDto
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.InfoBox
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.components.LabeledValue
import com.saporini.mobile_desktop.core.components.ListPanel
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.SideColumn
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.ToolbarButton
import com.saporini.mobile_desktop.core.components.ToolbarPrimaryButton
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.notification_order
import mobile_desktop.shared.generated.resources.settings_notifications
import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import mobile_desktop.shared.generated.resources.workspace_pos
import org.koin.compose.koinInject
import kotlin.time.Clock

private enum class DeviceGroup(val title: String, val types: Set<String>) {
    TILLS("Tills and tablets", setOf("TERMINAL", "TABLET")),
    KITCHEN("Kitchen screens", setOf("KDS")),
    PAYMENT("Card terminals and displays", setOf("PAYMENT_TERMINAL", "CUSTOMER_DISPLAY"))
}

private enum class DeviceFilter(val label: String) { ALL("All devices"), CONNECTED("Connected"), OFFLINE("Not connected"), ATTENTION("Need a look") }

/** Admin Hub → Devices: tills, tablets, kitchen screens, card terminals and printers. */
@Composable
internal fun DevicesScreen(modifier: Modifier = Modifier) {
    val model = koinInject<DevicesScreenModel>()
    BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    DevicesContent(state, model, modifier)
}

@Composable
internal fun DevicesContent(state: DevicesState, model: DevicesScreenModel, modifier: Modifier = Modifier) {
    var filter by remember { mutableStateOf(DeviceFilter.ALL) }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PageHeader("Devices", "Tills, tablets, kitchen screens, card terminals and printers") {
                RefreshButton(state.loading, model::refresh)
                if (state.canEdit) {
                    ToolbarButton("Add printer", Icons.Outlined.Print, !state.saving, model::newPrinter)
                    ToolbarPrimaryButton("Add device", Icons.Outlined.Add, !state.saving && state.branchId != null, model::newDevice)
                }
            }
            ScreenMessages(state.error.takeIf { state.draft == null }, state.notice, model::clearMessages)
            val selected = state.selected
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to devices", "Your role needs “View settings” to see devices.")
                state.devices.isEmpty() && state.printers.isEmpty() && state.loading -> OverviewPageSkeleton(size)
                state.devices.isEmpty() && state.printers.isEmpty() && state.stale -> PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                !size.isDesktop && selected != null -> DeviceDetails(selected, state, model, Modifier.fillMaxWidth().weight(1f), onBack = { model.select(null) })
                else -> {
                    SummaryCardRow(size, deviceCards(state))
                    if (size.isDesktop) {
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            DeviceBoard(state, model, filter, size, Modifier.weight(1f).fillMaxHeight())
                            if (selected != null) DeviceDetails(selected, state, model, Modifier.width(420.dp).fillMaxHeight(), onBack = null)
                            else SideColumn(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                FilterPanel(state, filter) { filter = it }
                                HowToPanel()
                            }
                        }
                    } else {
                        DeviceBoard(state, model, filter, size, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }

    state.draft?.let { DeviceEditorDialog(it, state, model) }
    state.freshCode?.let { FreshCodeDialog(it, state.selected, model::dismissFreshCode) }
    state.confirmDelete?.let { id ->
        val device = (state.devices + state.printers).firstOrNull { it.id == id }
        ConfirmDialog("Remove ${device?.name ?: "this device"}?",
            "It disappears from the list and can no longer be used. To stop it for a while, set it to “Switched off” or “Blocked” instead.",
            "Remove", onConfirm = model::confirmDelete, onDismiss = model::dismissDelete, danger = true, busy = state.saving)
    }
}

private fun deviceCards(state: DevicesState): List<@Composable (Modifier) -> Unit> {
    val attention = state.devices.count { it.needsAttention(false) } + state.printers.count { it.needsAttention(true) }
    val inUse = state.devices.count { it.status == "ACTIVE" }
    return listOf(
        { m -> OverviewStatCard("Devices", "${state.devices.size}", "$inUse in use · ${state.devices.size - inUse} other", Kit.Green, m,
            image = Res.drawable.workspace_pos, suffix = "in this branch") },
        { m -> OverviewStatCard("Connected now", "${state.onlineCount}", if (state.devices.isEmpty()) "No devices yet" else "of ${state.devices.size} devices",
            Kit.Blue, m, image = Res.drawable.settings_orders_kitchen,
            progress = if (state.devices.isEmpty()) null else state.onlineCount.toFloat() / state.devices.size) },
        { m -> OverviewStatCard("Printers", "${state.printers.size}", "Receipts and kitchen tickets", Kit.Purple, m, image = Res.drawable.notification_order) },
        { m -> OverviewStatCard("Need a look", "$attention", if (attention == 0) "Everything is fine" else "Blocked, in repair or offline",
            if (attention > 0) Kit.Danger else Kit.Grey, m, image = Res.drawable.settings_notifications, valueColor = if (attention > 0) Kit.Danger else Kit.Ink) }
    )
}

private fun DeviceDto.matches(filter: DeviceFilter, printer: Boolean): Boolean = when (filter) {
    DeviceFilter.ALL -> true
    DeviceFilter.CONNECTED -> !printer && online
    DeviceFilter.OFFLINE -> !printer && !online
    DeviceFilter.ATTENTION -> needsAttention(printer)
}

@Composable
private fun DeviceBoard(state: DevicesState, model: DevicesScreenModel, filter: DeviceFilter, size: ScreenSize, modifier: Modifier) {
    val groups = DeviceGroup.entries.map { group -> group to state.devices.filter { it.deviceType in group.types && it.matches(filter, false) } }
    val other = state.devices.filter { device -> DeviceGroup.entries.none { device.deviceType in it.types } && device.matches(filter, false) }
    val printers = state.printers.filter { it.matches(filter, true) }
    val empty = groups.all { it.second.isEmpty() } && other.isEmpty() && printers.isEmpty()
    OverviewPanel(Icons.Outlined.Devices, if (filter == DeviceFilter.ALL) "All devices" else filter.label, modifier,
        count = groups.sumOf { it.second.size } + other.size + printers.size) {
        if (empty) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty(
                    if (filter == DeviceFilter.ALL) "No devices yet" else "Nothing here",
                    if (filter == DeviceFilter.ALL) "Add the tills, tablets and kitchen screens you use, then pair each one with a code."
                    else "No device matches “${filter.label}”.",
                    Icons.Outlined.Devices, image = Res.drawable.workspace_pos
                )
            }
            return@OverviewPanel
        }
        val columns = when (size) { ScreenSize.PHONE -> 1; ScreenSize.TABLET -> 2; ScreenSize.DESKTOP -> if (state.selected != null) 2 else 3 }
        LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
            (groups + listOfNotNull(other.takeIf { it.isNotEmpty() }?.let { null to it })).forEach { (group, devices) ->
                if (devices.isNotEmpty()) {
                    item(key = "group-${group?.name ?: "other"}", span = { GridItemSpan(maxLineSpan) }) {
                        GroupLabel(group?.title ?: "Other devices", "${devices.size}")
                    }
                    items(devices, key = { it.id }) { DeviceTile(it, false, it.id == state.selectedId) { model.select(if (it.id == state.selectedId) null else it.id) } }
                }
            }
            if (printers.isNotEmpty()) {
                item(key = "group-printers", span = { GridItemSpan(maxLineSpan) }) { GroupLabel("Printers", "whole restaurant · ${printers.size}") }
                items(printers, key = { it.id }) { DeviceTile(it, true, it.id == state.selectedId) { model.select(if (it.id == state.selectedId) null else it.id) } }
            }
        }
    }
}

@Composable
private fun DeviceTile(device: DeviceDto, printer: Boolean, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tone = deviceStatusColor(device.status)
    val now = Clock.System.now()
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = if (selected) Kit.GreenSoft else Color.White,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else if (hovered) Kit.Green.copy(alpha = 0.45f) else Kit.RowBorder),
        shadowElevation = if (hovered) 3.dp else 1.dp
    ) {
        Column(
            Modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(tone.copy(alpha = 0.12f)), Alignment.Center) {
                    Icon(deviceTypeIcon(device.deviceType), null, Modifier.size(22.dp), tint = tone)
                }
                Spacer(Modifier.weight(1f))
                if (!printer) {
                    // A live dot: green when connected.
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (device.online) Kit.Green else Color(0xFFD5D8D3)))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(device.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${deviceTypeLabel(device.deviceType)} · ${device.code}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(deviceStatusLabel(device.status), tone)
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        printer -> device.printerIp ?: connectionLabel(device.printerConnectionType)
                        device.online -> "Connected"
                        device.lastSeenAt != null -> agoText(device.lastSeenAt, now, RestaurantTime.zone)
                        else -> "Never connected"
                    },
                    fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = if (!printer && device.online) Kit.Green else Kit.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FilterPanel(state: DevicesState, filter: DeviceFilter, onFilter: (DeviceFilter) -> Unit) {
    val all = state.devices.size + state.printers.size
    val attention = state.devices.count { it.needsAttention(false) } + state.printers.count { it.needsAttention(true) }
    OverviewPanel(Icons.Outlined.Wifi, "Show", Modifier.fillMaxWidth()) {
        FilterRow(Icons.Outlined.Devices, DeviceFilter.ALL.label, all, Kit.Green, filter == DeviceFilter.ALL) { onFilter(DeviceFilter.ALL) }
        FilterRow(Icons.Outlined.Wifi, DeviceFilter.CONNECTED.label, state.onlineCount, Kit.Blue, filter == DeviceFilter.CONNECTED) { onFilter(DeviceFilter.CONNECTED) }
        FilterRow(Icons.Outlined.WifiOff, DeviceFilter.OFFLINE.label, state.devices.size - state.onlineCount, Kit.Grey, filter == DeviceFilter.OFFLINE) { onFilter(DeviceFilter.OFFLINE) }
        FilterRow(Icons.Outlined.Info, DeviceFilter.ATTENTION.label, attention, Kit.Danger, filter == DeviceFilter.ATTENTION) { onFilter(DeviceFilter.ATTENTION) }
    }
}

@Composable
private fun HowToPanel() {
    OverviewPanel(Icons.Outlined.QrCode2, "Connect a new device", Modifier.fillMaxWidth()) {
        listOf(
            "Add it here with a name and a short code.",
            "Open it and make a pairing code.",
            "Type the code on the device once. It connects and shows as Connected."
        ).forEachIndexed { index, step ->
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Kit.GreenSoft), Alignment.Center) {
                    Text("${index + 1}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Kit.Green)
                }
                Text(step, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Ink)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceDetails(device: DeviceDto, state: DevicesState, model: DevicesScreenModel, modifier: Modifier, onBack: (() -> Unit)?) {
    val printer = state.isPrinter(device.id)
    val zone = RestaurantTime.zone
    val tone = deviceStatusColor(device.status)
    ListPanel(modifier) {
        Box(Modifier.fillMaxWidth().background(tone.copy(alpha = 0.07f))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton({ onBack?.invoke() ?: model.select(null) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close", tint = Kit.Ink)
                }
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)).background(Color.White).border(1.dp, tone.copy(alpha = 0.3f), RoundedCornerShape(13.dp)),
                    Alignment.Center) {
                    Icon(deviceTypeIcon(device.deviceType), null, Modifier.size(26.dp), tint = tone)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(device.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(deviceStatusLabel(device.status), tone)
                        if (!printer) StatusPill(if (device.online) "Connected" else "Offline", if (device.online) Kit.Green else Kit.Grey)
                    }
                }
                if (state.loadingDetail) CircularProgressIndicator(Modifier.size(18.dp), color = Kit.Green, strokeWidth = 2.dp)
            }
        }
        KitDivider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoBox(deviceTypeIcon(device.deviceType), deviceTypeLabel(device.deviceType))
                InfoBox(Icons.Outlined.QrCode2, device.code)
                if (printer) InfoBox(Icons.Outlined.Print, "${device.paperWidthMm ?: "–"} mm paper")
            }
            if (state.canEdit) {
                if (!printer) {
                    SelectInput(device.status, DEVICE_STATUSES, ::deviceStatusLabel, { model.setStatus(device.id, it) }, Modifier.fillMaxWidth(),
                        label = "Status", enabled = !state.saving,
                        hint = "Blocked, switched off, in-repair and retired devices can't sign in; they need a new pairing code to come back.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitButton("Edit", { model.edit(device.id) }, icon = Icons.Outlined.Edit, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                    KitButton("Remove", { model.askDelete(device.id) }, icon = Icons.Outlined.Delete, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                }
            }
            Caption("Details")
            val now = Clock.System.now()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
                val cell = Modifier.width(170.dp)
                if (printer) {
                    LabeledValue("Connection", connectionLabel(device.printerConnectionType), cell)
                    LabeledValue("Address", listOfNotNull(device.printerIp, device.printerPort?.toString()).joinToString(":").ifBlank { "–" }, cell)
                    LabeledValue("Cuts paper", if (device.autoCut == true) "Yes" else "No", cell)
                    LabeledValue("Opens cash drawer", if (device.cashDrawerKickEnabled == true) "Yes" else "No", cell)
                } else {
                    LabeledValue("Last seen", if (device.online) "Now" else device.lastSeenAt?.let { agoText(it, now, zone) } ?: "Never", cell)
                    LabeledValue("IP address", device.ipAddress ?: "–", cell)
                    LabeledValue("App version", device.appVersion ?: "–", cell)
                    LabeledValue("System", listOfNotNull(device.platform, device.osVersion).joinToString(" ").ifBlank { "–" }, cell)
                }
                LabeledValue("Maker and model", listOfNotNull(device.manufacturer, device.model).joinToString(" ").ifBlank { "–" }, cell)
                LabeledValue("Serial", device.serialNumber ?: "–", cell)
            }
            device.notes?.takeIf { it.isNotBlank() }?.let { LabeledValue("Notes", it) }
            if (!printer) {
                KitDivider()
                PairingSection(state, model)
                KitDivider()
                AssignmentSection(state, model)
            }
        }
    }
}

@Composable
private fun PairingSection(state: DevicesState, model: DevicesScreenModel) {
    var minutes by remember { mutableStateOf(15) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pairing codes", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
        Text("Type a code on the new device to connect it to this record. Each code works once.",
            fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Muted)
        if (state.canEdit) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                SelectInput(minutes, listOf(15, 60, 240, 1440), { pairingDuration(it) }, { minutes = it }, Modifier.width(160.dp), label = "Code lasts")
                KitButton("New code", { model.newPairingCode(minutes) }, icon = Icons.Outlined.QrCode2, loading = state.saving)
            }
        }
        if (state.pairingCodes.isEmpty()) Text("No codes yet.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        state.pairingCodes.take(6).forEach { code -> PairingCodeRow(code, state, model) }
    }
}

private fun pairingDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes == 60 -> "1 hour"
    minutes < 1440 -> "${minutes / 60} hours"
    else -> "1 day"
}

@Composable
private fun PairingCodeRow(code: PairingCodeDto, state: DevicesState, model: DevicesScreenModel) {
    val (label, color) = pairingStateLabel(code.state, code.active)
    val zone = RestaurantTime.zone
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp))
            .background(if (code.active) Kit.GreenSoft.copy(alpha = 0.5f) else Color.White).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Made ${dateTimeText(code.createdAt, zone)}" + (code.createdByDisplayName?.let { " by $it" } ?: ""),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when {
                code.usedAt != null -> "Used ${dateTimeText(code.usedAt, zone)}"
                code.revokedAt != null -> "Cancelled ${dateTimeText(code.revokedAt, zone)}"
                else -> "Works until ${dateTimeText(code.expiresAt, zone)}"
            }, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        }
        StatusPill(label, color)
        if (code.active && state.canEdit) {
            androidx.compose.material3.IconButton({ model.revokePairingCode(code.id) }, enabled = !state.saving) {
                Icon(Icons.Outlined.LinkOff, "Cancel this code", Modifier.size(18.dp), tint = Kit.Danger)
            }
        }
    }
}

@Composable
private fun AssignmentSection(state: DevicesState, model: DevicesScreenModel) {
    val current = state.assignments.firstOrNull { it.active }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Who uses it", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
        if (current == null) Text("Not given to anyone. Give it to the branch so everyone there can use it.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        else AssignmentRow(current, state, model)
        if (state.canEdit && state.branchId != null && current?.branchId != state.branchId) {
            KitButton("Give to this branch", { model.assign(null, state.branchId) }, icon = Icons.Outlined.Storefront, style = ButtonStyle.SECONDARY, loading = state.saving)
        }
        val earlier = state.assignments.filterNot { it.active }.take(4)
        if (earlier.isNotEmpty()) {
            Caption("Before")
            earlier.forEach { AssignmentRow(it, state, model) }
        }
    }
}

@Composable
private fun AssignmentRow(assignment: DeviceAssignmentDto, state: DevicesState, model: DevicesScreenModel) {
    val zone = RestaurantTime.zone
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (assignment.active) Kit.GreenSoft else Color.White)
            .border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (assignment.assignmentType == "USER") assignment.userDisplayName ?: assignment.userEmail ?: "A person"
                else assignment.branchName ?: "This branch", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
            Text(listOfNotNull("From ${dateTimeText(assignment.assignedAt, zone)}", assignment.unassignedAt?.let { "until ${dateTimeText(it, zone)}" },
                assignment.assignedByDisplayName?.let { "by $it" }).joinToString(" "),
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (assignment.active && state.canEdit) KitButton("End", { model.unassign(assignment.id) }, style = ButtonStyle.SECONDARY, enabled = !state.saving)
    }
}
