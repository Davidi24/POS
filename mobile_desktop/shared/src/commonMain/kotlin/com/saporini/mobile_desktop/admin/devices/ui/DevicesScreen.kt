@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.admin.devices.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.devices.DEVICE_STATUSES
import com.saporini.mobile_desktop.admin.devices.DeviceAssignmentDto
import com.saporini.mobile_desktop.admin.devices.DeviceDto
import com.saporini.mobile_desktop.admin.devices.DeviceExtras
import com.saporini.mobile_desktop.admin.devices.DevicesScreenModel
import com.saporini.mobile_desktop.admin.devices.DevicesState
import com.saporini.mobile_desktop.admin.devices.PairingCodeDto
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.InfoBox
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.components.LabeledValue
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.ToolbarPrimaryButton
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.device_card_terminal
import mobile_desktop.shared.generated.resources.device_cash_drawer
import mobile_desktop.shared.generated.resources.device_printer
import mobile_desktop.shared.generated.resources.device_scanner
import mobile_desktop.shared.generated.resources.device_tablet
import mobile_desktop.shared.generated.resources.device_till
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import kotlin.time.Clock

// Admin Hub → Devices: six kinds of device as picture cards (how many, how many in use, what waits to be confirmed),
// then what waits to be confirmed, then every device by kind. A device opens in a dialog with everything about it.
// Adding: pick the kind, fill in a name, then confirm it works (pairing code, test print, a test scan or opening the drawer).

/** The kinds of device the restaurant has, each with its picture. Scanners and cash drawers are MOCK ([DeviceExtras]). */
internal enum class DeviceKind(val title: String, val one: String, val hint: String, val image: DrawableResource, val types: List<String>) {
    TILL("Tills", "till", "Orders and payments at the counter", Res.drawable.device_till, listOf("POS_TERMINAL", "TERMINAL")),
    TABLET("Tablets & screens", "tablet", "Waiter tablets, kitchen screens, the guest display", Res.drawable.device_tablet, listOf("TABLET", "KDS", "CUSTOMER_DISPLAY")),
    PRINTER("Printers", "printer", "Receipts and kitchen tickets", Res.drawable.device_printer, listOf("PRINTER")),
    CARD("Card terminals", "card terminal", "Card and phone payments", Res.drawable.device_card_terminal, listOf("PAYMENT_TERMINAL")),
    SCANNER("Barcode scanners", "scanner", "Scan stock on deliveries and counts", Res.drawable.device_scanner, listOf(DeviceExtras.SCANNER)),
    DRAWER("Cash drawers", "cash drawer", "Opened by the receipt printer", Res.drawable.device_cash_drawer, listOf(DeviceExtras.CASH_DRAWER));

    /** Devices of this kind sign in with a pairing code (the others are plugged into one that does). */
    val pairs get() = this == TILL || this == TABLET || this == CARD
    val mock get() = this == SCANNER || this == DRAWER
}

internal fun kindOf(device: DeviceDto): DeviceKind = DeviceKind.entries.firstOrNull { device.deviceType in it.types } ?: DeviceKind.TABLET

private val PhotoBlack = Color(0xFF0B0C0B)

/** Admin Hub → Devices. */
@Composable
internal fun DevicesScreen(modifier: Modifier = Modifier) {
    val model = koinInject<DevicesScreenModel>()
    BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    DevicesContent(state, model, modifier)
}

@Composable
internal fun DevicesContent(
    state: DevicesState, model: DevicesScreenModel, modifier: Modifier = Modifier,
    startOpen: String? = null, startConfirming: String? = null, startAdding: Boolean = false, startKind: DeviceKind? = null
) {
    // Like Users: the kinds as cards, and a kind's own page to manage its devices (user rule).
    var openKind by remember { mutableStateOf(startKind) }
    var picking by remember { mutableStateOf(startAdding) }
    var addingExtra by remember { mutableStateOf<DeviceKind?>(null) }
    var openId by remember { mutableStateOf(startOpen) }
    var confirming by remember { mutableStateOf(startConfirming) }
    var removingExtra by remember { mutableStateOf<DeviceDto?>(null) }
    var localNotice by remember { mutableStateOf<String?>(null) }
    val all = state.devices + state.printers + DeviceExtras.items
    val byId = all.associateBy { it.id }

    fun open(device: DeviceDto) { openKind = kindOf(device); openId = device.id; if (!DeviceExtras.isExtra(device.id)) model.select(device.id) }
    fun closeDevice() { openId = null; model.select(null) }
    fun startConfirm(device: DeviceDto) { confirming = device.id; if (!DeviceExtras.isExtra(device.id)) model.select(device.id) }
    fun add(kind: DeviceKind) {
        picking = false
        when (kind) {
            DeviceKind.PRINTER -> model.newPrinter()
            DeviceKind.SCANNER, DeviceKind.DRAWER -> addingExtra = kind
            else -> model.newDevice(kind.types.first())
        }
    }
    // A device that was just added goes straight on to being confirmed.
    LaunchedEffect(state.justAdded) {
        state.justAdded?.let { id -> confirming = id; model.select(id); model.clearJustAdded() }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        val side = if (size.isPhone) 14.dp else 22.dp
        Column(Modifier.fillMaxSize().padding(start = side, end = side, top = if (size.isPhone) 12.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PageHeader("Devices", "Everything that runs the restaurant, and whether it works") {
                RefreshButton(state.loading, model::refresh)
                // Inside a kind, its own "Add a …" button takes over.
                if (state.canEdit && openKind == null) ToolbarPrimaryButton("Add device", Icons.Outlined.Add, !state.saving) { picking = true }
            }
            ScreenMessages(state.error.takeIf { state.draft == null && confirming == null }, state.notice ?: localNotice,
                onClear = { model.clearMessages(); localNotice = null })
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to devices", "Your role needs “View settings” to see devices.")
                state.devices.isEmpty() && state.printers.isEmpty() && state.loading -> OverviewPageSkeleton(size, sidePanel = false)
                state.devices.isEmpty() && state.printers.isEmpty() && state.stale -> PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                openId != null && byId[openId] != null -> byId.getValue(openId!!).let { device ->
                    DevicePage(Modifier.fillMaxWidth().weight(1f), device, state, model, size, onBack = ::closeDevice,
                        onConfirm = { startConfirm(device) }, onEdit = { model.edit(device.id) },
                        onRemove = { if (DeviceExtras.isExtra(device.id)) removingExtra = device else model.askDelete(device.id) })
                }
                openKind == null -> KindGrid(Modifier.fillMaxWidth().weight(1f), all, size) { openKind = it }
                else -> openKind?.let { kind ->
                    KindPage(Modifier.fillMaxWidth().weight(1f), kind, all.filter { kindOf(it) == kind }, state, size,
                        onBack = { openKind = null }, onAdd = { add(kind) }, onOpen = ::open, onConfirm = ::startConfirm,
                        onEdit = { device -> if (DeviceExtras.isExtra(device.id)) open(device) else model.edit(device.id) },
                        onRemove = { device -> if (DeviceExtras.isExtra(device.id)) removingExtra = device else model.askDelete(device.id) })
                }
            }
        }
    }

    if (picking) AddDevicePicker(onPick = ::add, onDismiss = { picking = false })
    addingExtra?.let { kind ->
        ExtraDeviceDialog(kind, state.printers, onDismiss = { addingExtra = null }) { added -> addingExtra = null; confirming = added.id }
    }
    state.draft?.let { DeviceEditorDialog(it, state, model) }
    confirming?.let { id ->
        byId[id]?.let { device ->
            ConfirmDeviceDialog(device, state, model, onDone = { confirmed ->
                confirming = null
                if (confirmed && DeviceExtras.isExtra(device.id)) { DeviceExtras.confirm(device.id); localNotice = "${device.name} is confirmed" }
                else if (confirmed) model.confirm(device.id)
                if (openId == null) model.select(null)
            })
        }
    }
    // A pairing code made from the device's own dialog (the confirm dialog shows its code itself).
    if (confirming == null) state.freshCode?.let { FreshCodeDialog(it, state.selected, model::dismissFreshCode) }
    state.confirmDelete?.let { id ->
        val device = byId[id]
        ConfirmDialog("Remove ${device?.name ?: "this device"}?",
            "It disappears from the list and can no longer be used. To stop it for a while, set it to “Switched off” or “Blocked” instead.",
            "Remove", onConfirm = { model.confirmDelete(); openId = null }, onDismiss = model::dismissDelete, danger = true, busy = state.saving)
    }
    removingExtra?.let { device ->
        ConfirmDialog("Remove ${device.name}?", "It disappears from the list.", "Remove",
            onConfirm = { DeviceExtras.remove(device.id); removingExtra = null; openId = null; localNotice = "Removed" }, onDismiss = { removingExtra = null }, danger = true)
    }
}

// ---------- The six kinds, as cards ----------

/** Each kind's colour: its dot, the hover border and its count. */
private val DeviceKind.color: Color
    get() = when (this) {
        DeviceKind.TILL -> Kit.Green
        DeviceKind.TABLET -> Kit.Blue
        DeviceKind.PRINTER -> Kit.Purple
        DeviceKind.CARD -> Kit.Amber
        DeviceKind.SCANNER -> Color(0xFF1F7A83)
        DeviceKind.DRAWER -> Color(0xFF8B5C18)
    }

private val KindCardMaxWidth = 280.dp
private val KindCardMinWidth = 220.dp

/**
 * The kinds as cards, three a row and never wider than [KindCardMaxWidth] (smaller than Users' cards, so both rows fit on
 * a desktop screen; user, 2026-10-31: "a little smaller"). A small tablet, where three would squeeze the names, gets two
 * a row, and so does a phone, with smaller cards (user, 2026-10-31: "make it so much better").
 */
@Composable
private fun KindGrid(modifier: Modifier, all: List<DeviceDto>, size: ScreenSize, onOpen: (DeviceKind) -> Unit) {
    val gap = if (size.isPhone) 10.dp else 14.dp
    ScrollColumn(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val columns = when {
                size.isPhone -> 2
                (maxWidth - gap * 2) / 3 >= KindCardMinWidth -> 3
                else -> 2
            }
            val cardWidth = if (columns == 1) maxWidth else ((maxWidth - gap * (columns - 1)) / columns).coerceAtMost(KindCardMaxWidth)
            Column(Modifier.width(cardWidth * columns + gap * (columns - 1)), verticalArrangement = Arrangement.spacedBy(gap)) {
                DeviceKind.entries.chunked(columns).forEach { row ->
                    // Cards in a row keep the same height when one's chips take two lines.
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                        row.forEach { kind ->
                            val devices = all.filter { kindOf(it) == kind }
                            KindCard(kind, devices, Modifier.width(cardWidth).fillMaxHeight(), compact = size.isPhone) { onOpen(kind) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

/** What a kind's devices are doing, as short chips: in use, in repair, offline. */
@Composable
private fun KindChips(kind: DeviceKind, devices: List<DeviceDto>) {
    val inUse = devices.count { it.status == "ACTIVE" }
    val repair = devices.count { it.status == "MAINTENANCE" || it.status == "BLOCKED" }
    val offline = devices.count { kind.pairs && it.status == "ACTIVE" && !it.online }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (devices.isEmpty()) Text("None yet", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint)
        else {
            StatusChip("$inUse in use", Kit.Green)
            if (repair > 0) StatusChip("$repair in repair", Kit.Purple)
            if (offline > 0) StatusChip("$offline offline", Kit.Grey)
        }
        if (kind.mock) Text("sample", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint)
    }
}

/** Devices waiting to be confirmed, as a small notification on the card. */
@Composable
private fun WaitingBadge(count: Int, modifier: Modifier, short: Boolean = false) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Kit.Amber).border(2.dp, Color.White, RoundedCornerShape(50)).padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.HourglassTop, null, Modifier.size(13.dp), tint = Color.White)
        Text(if (short) "$count" else "$count to confirm", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White, maxLines = 1)
    }
}

/**
 * A kind as a card: its picture, how many, what it's for, and how its devices are doing. Opens the kind's page.
 * [compact]: a phone's two-a-row card, with tighter text and the waiting badge as just a number.
 */
@Composable
private fun KindCard(kind: DeviceKind, devices: List<DeviceDto>, modifier: Modifier, compact: Boolean = false, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val waiting = devices.count { it.status == "PROVISIONING" }
    Surface(
        onClick = onOpen, modifier = modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand), shape = RoundedCornerShape(16.dp), color = Color.White,
        border = BorderStroke(if (hovered) 1.5.dp else 1.dp, if (hovered) kind.color.copy(alpha = 0.55f) else Kit.Border),
        shadowElevation = if (hovered) 6.dp else 1.dp, interactionSource = interaction
    ) {
        Column {
            // The pictures are 3:2, so the band keeps that shape and shows all of it (user: "make the image bigger").
            Box(Modifier.fillMaxWidth().aspectRatio(1.5f).background(PhotoBlack)) {
                Image(painterResource(kind.image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Row(Modifier.align(Alignment.TopStart).padding(if (compact) 8.dp else 12.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.94f))
                    .padding(start = if (compact) 6.dp else 8.dp, end = if (compact) 8.dp else 10.dp, top = if (compact) 3.dp else 4.dp, bottom = if (compact) 3.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Devices, null, Modifier.size(if (compact) 12.dp else 14.dp), tint = kind.color)
                    Text(if (compact) "${devices.size}" else if (devices.size == 1) "1 device" else "${devices.size} devices", fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold, fontSize = if (compact) 11.sp else 12.sp, color = Kit.Ink, maxLines = 1)
                }
                if (waiting > 0) WaitingBadge(waiting, Modifier.align(Alignment.TopEnd).padding(if (compact) 6.dp else 10.dp), short = compact)
            }
            Column(Modifier.padding(start = if (compact) 12.dp else 16.dp, end = if (compact) 8.dp else 12.dp, top = if (compact) 10.dp else 12.dp,
                bottom = if (compact) 12.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(if (compact) 8.dp else 9.dp).clip(CircleShape).background(kind.color))
                    Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                    Text(kind.title, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (compact) 14.sp else 16.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(if (compact) 18.dp else 20.dp), tint = if (hovered) kind.color else Kit.Faint)
                }
                Text(kind.hint, fontFamily = Inter(), fontSize = if (compact) 11.5.sp else 12.sp, lineHeight = if (compact) 15.sp else 16.sp, color = Kit.Muted,
                    maxLines = if (compact) 2 else 1, minLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis)
                KindChips(kind, devices)
            }
        }
    }
}

// ---------- A kind's own page ----------

/** One kind: back to the kinds, its picture and name, "Add a …"; what waits to be confirmed; then its devices to open, edit or remove. */
@Composable
private fun KindPage(
    modifier: Modifier, kind: DeviceKind, devices: List<DeviceDto>, state: DevicesState, size: ScreenSize,
    onBack: () -> Unit, onAdd: () -> Unit, onOpen: (DeviceDto) -> Unit, onConfirm: (DeviceDto) -> Unit, onEdit: (DeviceDto) -> Unit, onRemove: (DeviceDto) -> Unit
) {
    val canEdit = state.canEdit && !state.saving
    var query by remember(kind) { mutableStateOf("") }
    val shown = devices.filter { d -> query.isBlank() || listOfNotNull(d.name, d.code, d.model, d.manufacturer).any { it.contains(query.trim(), ignoreCase = true) } }
        .sortedWith(compareBy({ it.status != "PROVISIONING" }, { it.status == "RETIRED" }, { it.name.lowercase() }))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // One step back inside the page: a plain arrow (the curved one in the top bar leaves the workspace; user rule).
            Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the devices", Modifier.size(22.dp), tint = Kit.Ink)
            }
            KindThumb(kind, if (size.isPhone) 42.dp else 50.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(kind.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (size.isPhone) 17.sp else 19.sp, color = Kit.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(listOf(if (devices.size == 1) "1 device" else "${devices.size} devices", kind.hint).joinToString(" · "), fontFamily = Inter(), fontSize = 12.sp,
                    color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state.canEdit && !size.isPhone) KitButton("Add a ${kind.one}", onAdd, icon = Icons.Outlined.Add, enabled = canEdit)
        }
        if (state.canEdit && size.isPhone) KitButton("Add a ${kind.one}", onAdd, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add, enabled = canEdit)
        // Devices waiting to be confirmed come first in the list, each with its own Confirm button (no second box above).
        Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(12.dp), color = Color.White,
            border = BorderStroke(1.dp, if (size.isPhone) Color.Transparent else Kit.Border)) {
            Column(Modifier.padding(if (size.isPhone) 0.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.padding(start = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Devices, null, Modifier.size(18.dp), tint = Kit.Ink)
                    Text(kind.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                    Spacer(Modifier.weight(1f))
                    Text("${shown.size}", Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(horizontal = 8.dp, vertical = 1.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                }
                if (devices.size > 4) SearchField(query, { query = it }, if (size.isPhone) Modifier.fillMaxWidth() else Modifier.width(380.dp),
                    placeholder = "Find a ${kind.one}", height = 44.dp)
                if (shown.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                        OverviewEmpty(if (devices.isEmpty()) "No ${kind.title.lowercase()} yet" else "Nothing matches",
                            if (devices.isEmpty()) "Add one, then confirm it works." else "Try another name or code.", Icons.Outlined.Devices)
                    }
                } else ScrollColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { device ->
                        DeviceRow(device, kind, size, state.canEdit, canEdit, onOpen = { onOpen(device) }, onEdit = { onEdit(device) },
                            onConfirm = { onConfirm(device) }, onRemove = { onRemove(device) })
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

/** A device on its kind's page: a strip in its status colour, its picture, name and details, how it's reached, its status, Edit and ⋮. */
@Composable
private fun DeviceRow(
    device: DeviceDto, kind: DeviceKind, size: ScreenSize, canEdit: Boolean, enabled: Boolean,
    onOpen: () -> Unit, onEdit: () -> Unit, onConfirm: () -> Unit, onRemove: () -> Unit
) {
    val tone = deviceStatusColor(device.status)
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand), shape = RoundedCornerShape(12.dp), color = Color.White,
        border = BorderStroke(1.dp, Kit.RowBorder)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(tone))
            Row(Modifier.weight(1f).padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KindThumb(kind, 44.dp)
                Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(device.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(deviceTypeLabel(device.deviceType).takeIf { kind == DeviceKind.TABLET }, device.code,
                        listOfNotNull(device.manufacturer, device.model).joinToString(" ").ifBlank { null }).joinToString(" · "),
                        fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (size.isPhone) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(tone))
                        Text(deviceStatusLabel(device.status), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = tone, maxLines = 1)
                    }
                }
                if (!size.isPhone) Box(Modifier.weight(1f)) { ConnectionText(device, kind) }
                if (!size.isPhone) StatusPill(deviceStatusLabel(device.status), tone)
                if (canEdit && !size.isPhone) {
                    if (device.status == "PROVISIONING") KitButton("Confirm", onConfirm, icon = Icons.Outlined.Verified, enabled = enabled)
                    else Surface(onClick = onEdit, enabled = enabled, shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(15.dp), tint = Kit.Ink)
                            Text("Edit", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
                        }
                    }
                }
                if (canEdit) RowActionsMenu(listOfNotNull(
                    RowAction("Open", Icons.Outlined.Info, onClick = onOpen),
                    RowAction("Confirm it works", Icons.Outlined.Verified, onClick = onConfirm).takeIf { device.status == "PROVISIONING" },
                    RowAction("Edit", Icons.Outlined.Edit, onClick = onEdit).takeIf { !DeviceExtras.isExtra(device.id) },
                    RowAction("Remove", Icons.Outlined.DeleteOutline, danger = true, onClick = onRemove)
                ), busy = !enabled)
            }
        }
    }
}

/** How the device is reached: connected or when it was last seen (devices that sign in), the printer's address, or plugged in. */
@Composable
private fun ConnectionText(device: DeviceDto, kind: DeviceKind) {
    val now = Clock.System.now()
    val (text, color) = when {
        kind == DeviceKind.PRINTER -> (device.printerIp ?: connectionLabel(device.printerConnectionType)) to Kit.Muted
        kind.mock -> (if (device.status == "ACTIVE") "Plugged in" else "Not checked") to Kit.Muted
        device.online -> "Connected" to Kit.Green
        device.lastSeenAt != null -> agoText(device.lastSeenAt, now, RestaurantTime.zone) to Kit.Muted
        device.status == "PROVISIONING" -> "Not paired yet" to Kit.Muted
        else -> "Offline" to Kit.Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (kind.pairs) Box(Modifier.size(7.dp).clip(CircleShape).background(if (device.online) Kit.Green else Color(0xFFD5D8D3)))
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The kind's picture, small and rounded. */
@Composable
internal fun KindThumb(kind: DeviceKind, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(PhotoBlack)) {
        Image(painterResource(kind.image), kind.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** The kind's picture as a wide banner, with [overlay] on its lower edge. */
@Composable
internal fun KindBanner(kind: DeviceKind, height: Dp, overlay: @Composable BoxScope.() -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(14.dp)).background(PhotoBlack)) {
        // The pictures have black backgrounds, so the whole device fits on the black band.
        Image(painterResource(kind.image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        overlay()
    }
}

// ---------- Adding ----------

@Composable
private fun AddDevicePicker(onPick: (DeviceKind) -> Unit, onDismiss: () -> Unit) {
    AppDialog("Add a device", onDismiss, subtitle = "What are you adding?", maxWidth = 820.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = if (maxWidth < 520.dp) 2 else 3
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DeviceKind.entries.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { kind ->
                            Surface(onClick = { onPick(kind) }, modifier = Modifier.weight(1f).pointerHoverIcon(PointerIcon.Hand), shape = RoundedCornerShape(14.dp),
                                color = Color.White, border = BorderStroke(1.dp, Kit.RowBorder), shadowElevation = 1.dp) {
                                Column {
                                    Box(Modifier.fillMaxWidth().aspectRatio(1.7f).background(PhotoBlack)) {
                                        Image(painterResource(kind.image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("A ${kind.one}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1)
                                        Text(kind.hint, fontFamily = Inter(), fontSize = 11.5.sp, lineHeight = 15.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ---------- One device (its page is DevicePage.kt) ----------

@Composable
internal fun PairingSection(state: DevicesState, model: DevicesScreenModel) {
    var minutes by remember { mutableStateOf(15) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

internal fun pairingDuration(minutes: Int): String = when {
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
internal fun AssignmentSection(state: DevicesState, model: DevicesScreenModel) {
    val current = state.assignments.firstOrNull { it.active }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
