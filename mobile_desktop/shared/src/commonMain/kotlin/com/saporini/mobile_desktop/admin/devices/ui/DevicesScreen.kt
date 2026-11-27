@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.admin.devices.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.gestures.draggable
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
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.i18n.tr
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
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
import com.saporini.mobile_desktop.core.components.ToolbarButton
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
    // A printer just added: after confirming it, what it prints (user, 2026-11-27).
    var offerUse by remember { mutableStateOf<String?>(null) }
    var useItFor by remember { mutableStateOf<String?>(null) }
    // A device that was just added goes straight on to being confirmed.
    LaunchedEffect(state.justAdded) {
        state.justAdded?.let { id ->
            if (state.isPrinter(id)) offerUse = id
            confirming = id; model.select(id); model.clearJustAdded()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        // More room at the sides (user, 2026-11-27: "more space in left and right"), so the cards clear the right-edge tab.
        val side = when {
            size.isPhone -> 14.dp
            size.isDesktop -> 44.dp
            else -> 30.dp
        }
        Column(Modifier.fillMaxSize().padding(start = side, end = side, top = if (size.isPhone) 12.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Inside a kind or a device, the way back sits in the header, beside "Devices", with where you are under it
            // (user, 2026-11-25: "the back arrow on the device, up, make it better").
            val openDevice = openId?.let { byId[it] }
            val back: (() -> Unit)? = when {
                openDevice != null -> ::closeDevice
                openKind != null -> ({ openKind = null })
                else -> null
            }
            PageHeader("Devices",
                when {
                    openDevice != null -> "${kindOf(openDevice).title} › ${openDevice.name}"
                    openKind != null -> openKind?.title
                    else -> "Everything that runs the restaurant, and whether it works"
                },
                leading = back?.let { goBack -> {
                    // One step back inside the page: a plain arrow (the curved one in the top bar leaves the workspace; user rule).
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = goBack).pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            if (openDevice != null) "Back to ${kindOf(openDevice).title}" else "Back to the devices", Modifier.size(24.dp), tint = Kit.Ink)
                    }
                } }) {
                if (openId == null) HereChip(state, ::open)
                if (openId == null && (state.canConnect || state.canEdit) && !state.pinSignIn) ToolbarButton("Enter a code", Icons.Outlined.VpnKey, !state.saving) { model.openEnterCode() }
                RefreshButton(state.loading, model::refresh)
                // Inside a kind, its own "Add a …" button takes over.
                if (state.canEdit && openKind == null) ToolbarPrimaryButton("Add device", Icons.Outlined.Add, !state.saving) { picking = true }
            }
            ScreenMessages(state.error.takeIf { state.draft == null && confirming == null }, state.notice ?: localNotice,
                onClear = { model.clearMessages(); localNotice = null })
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to devices", "Your role needs “Connect devices” to see devices.")
                state.devices.isEmpty() && state.printers.isEmpty() && state.loading -> OverviewPageSkeleton(size, sidePanel = false)
                state.devices.isEmpty() && state.printers.isEmpty() && state.stale -> PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                openId != null && byId[openId] != null -> byId.getValue(openId!!).let { device ->
                    DevicePage(Modifier.fillMaxWidth().weight(1f), device, state, model, size, onBack = ::closeDevice,
                        onConfirm = { startConfirm(device) }, onEdit = { model.edit(device.id) },
                        onRemove = { if (DeviceExtras.isExtra(device.id)) removingExtra = device else model.askDelete(device.id) })
                }
                // Someone who only connects devices sees the tills and the tablets & screens (user, 2026-11-25).
                openKind == null -> KindGrid(Modifier.fillMaxWidth().weight(1f), all, size,
                    if (state.canEdit) DeviceKind.entries else listOf(DeviceKind.TILL, DeviceKind.TABLET), state, ::open) { openKind = it }
                else -> openKind?.let { kind ->
                    KindPage(Modifier.fillMaxWidth().weight(1f), kind, all.filter { kindOf(it) == kind }, state, size,
                        onBack = { openKind = null }, onAdd = { add(kind) }, onOpen = ::open, onConfirm = ::startConfirm,
                        onEdit = { device -> if (DeviceExtras.isExtra(device.id)) open(device) else model.edit(device.id) },
                        onRemove = { device -> if (DeviceExtras.isExtra(device.id)) removingExtra = device else model.askDelete(device.id) })
                }
            }
        }
        // Inside a kind, "Add a till" is the green tab on the right edge, as on the other pages (user, 2026-11-25); it can be
        // dragged up and down.
        val addKind = openKind
        if (addKind != null && openId == null && state.canEdit && state.canRead) {
            var position by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0.3f) }
            val compact = size.isPhone
            val maxOffset = with(androidx.compose.ui.platform.LocalDensity.current) {
                (maxHeight - com.saporini.mobile_desktop.core.components.edgeActionButtonHeight(compact)).coerceAtLeast(0.dp).toPx()
            }
            val label = "Add a ${addKind.one}"
            com.saporini.mobile_desktop.core.components.RightEdgeActionButton(
                label, { add(addKind) }, enabled = !state.saving,
                width = (if (compact) 50 + label.length * 6 else 62 + label.length * 7).coerceAtLeast(if (compact) 100 else 124).dp, compact = compact,
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { androidx.compose.ui.unit.IntOffset(0, kotlin.math.round(position * maxOffset).toInt()) }
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            if (maxOffset > 0f) position = (position + delta / maxOffset).coerceIn(0f, 1f)
                        }
                    )
            )
        }
    }

    if (picking) AddDevicePicker(onPick = ::add, onDismiss = { picking = false })
    addingExtra?.let { kind ->
        ExtraDeviceDialog(kind, state.printers, onDismiss = { addingExtra = null }) { added -> addingExtra = null; confirming = added.id }
    }
    state.draft?.let { DeviceEditorDialog(it, state, model) }
    if (state.enteringCode) EnterCodeDialog(state, model)
    confirming?.let { id ->
        byId[id]?.let { device ->
            ConfirmDeviceDialog(device, state, model, onDone = { confirmed ->
                confirming = null
                if (confirmed && DeviceExtras.isExtra(device.id)) { DeviceExtras.confirm(device.id); localNotice = "${device.name} is confirmed" }
                else if (confirmed) model.confirm(device.id)
                if (offerUse == device.id) { useItFor = device.id; offerUse = null }
                if (openId == null) model.select(null)
            })
        }
    }
    // A new printer, once confirmed: what it prints (a document printer has nothing to choose).
    useItFor?.let { id ->
        val printer = state.printers.firstOrNull { it.id == id }
        if (printer == null || printer.printerRole == "DOCUMENT") useItFor = null
        else {
            LaunchedEffect(id) { if (state.selectedId != id) model.select(id) }
            PrinterJobsDialog(printer, state, model) {
                useItFor = null
                if (openId == null) model.select(null)
            }
        }
    }
    // A pairing code made from the device's own dialog (the confirm dialog shows its code itself).
    if (confirming == null && openId == null) state.freshCode?.let { FreshCodeDialog(it, state.selected, model::dismissFreshCode) }
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
        DeviceKind.TILL -> Color(0xFF475569)
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
private fun KindGrid(
    modifier: Modifier, all: List<DeviceDto>, size: ScreenSize, kinds: List<DeviceKind>, state: DevicesState,
    onOpenDevice: (DeviceDto) -> Unit, onOpen: (DeviceKind) -> Unit
) {
    val gap = if (size.isPhone) 10.dp else 14.dp
    val here = hereId()
    val heartbeat by com.saporini.mobile_desktop.core.session.TillHeartbeat.state.collectAsState()
    ScrollColumn(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val columns = when {
                size.isPhone -> 2
                (maxWidth - gap * 2) / 3 >= KindCardMinWidth -> 3
                else -> 2
            }
            val cardWidth = if (columns == 1) maxWidth else ((maxWidth - gap * (columns - 1)) / columns).coerceAtMost(KindCardMaxWidth)
            Column(Modifier.width(cardWidth * columns + gap * (columns - 1)), verticalArrangement = Arrangement.spacedBy(gap)) {
                kinds.chunked(columns).forEach { row ->
                    // Cards in a row keep the same height when one's chips take two lines.
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                        row.forEach { kind ->
                            val devices = all.filter { kindOf(it) == kind }
                            KindCard(kind, devices, Modifier.width(cardWidth).fillMaxHeight(), compact = size.isPhone, here = here, heartbeat = heartbeat) { onOpen(kind) }
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
private fun KindChips(kind: DeviceKind, devices: List<DeviceDto>, here: String?, heartbeat: com.saporini.mobile_desktop.core.session.TillHeartbeat.State) {
    if (kind.pairs && devices.isNotEmpty()) {
        // Devices that sign in say how many are online, offline and not connected (user, 2026-11-25).
        val links = devices.map { linkOf(it, it.id == here, heartbeat) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Link.entries.forEach { link ->
                val count = links.count { it == link }
                if (count > 0) StatusChip(when (link) {
                    Link.ONLINE -> "$count in use · online"
                    Link.OFFLINE -> "$count in use · offline"
                    Link.NOT_CONNECTED -> "$count not connected"
                    Link.NOT_IN_USE -> "$count switched off"
                }, link.color)
            }
        }
        return
    }
    val inUse = devices.count { it.status == "ACTIVE" }
    val repair = devices.count { it.status == "MAINTENANCE" || it.status == "BLOCKED" }
    // Printers that don't answer (tills check every minute) show in red.
    val offline = devices.count { if (kind == DeviceKind.PRINTER) reachOf(it) == Reach.OFFLINE else kind.pairs && it.status == "ACTIVE" && !it.online }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (devices.isEmpty()) Text("None yet", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint)
        else {
            StatusChip("$inUse in use", Kit.Blue)
            if (repair > 0) StatusChip("$repair in repair", Kit.Purple)
            if (offline > 0) StatusChip("$offline offline", if (kind == DeviceKind.PRINTER) Kit.Danger else Kit.Grey)
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
private fun KindCard(
    kind: DeviceKind, devices: List<DeviceDto>, modifier: Modifier, compact: Boolean = false, here: String? = null,
    heartbeat: com.saporini.mobile_desktop.core.session.TillHeartbeat.State = com.saporini.mobile_desktop.core.session.TillHeartbeat.State.UNKNOWN,
    onOpen: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Devices that sign in show "not connected" instead (their chips); the rest wait to be confirmed.
    val waiting = if (kind.pairs) 0 else devices.count { it.status == "PROVISIONING" }
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
                KindChips(kind, devices, here, heartbeat)
            }
        }
    }
}

// ---------- A kind's own page ----------

/**
 * One kind: back to the kinds, its picture and name, "Add a …"; how many are online, offline and not connected; then every
 * device as a card (user, 2026-11-25: "make it look better": the Manage places cards' look), this device first, then online,
 * offline, not connected yet and not in use.
 */
@Composable
private fun KindPage(
    modifier: Modifier, kind: DeviceKind, devices: List<DeviceDto>, state: DevicesState, size: ScreenSize,
    onBack: () -> Unit, onAdd: () -> Unit, onOpen: (DeviceDto) -> Unit, onConfirm: (DeviceDto) -> Unit, onEdit: (DeviceDto) -> Unit, onRemove: (DeviceDto) -> Unit
) {
    val canEdit = state.canEdit && !state.saving
    val here = hereId()
    val heartbeat by com.saporini.mobile_desktop.core.session.TillHeartbeat.state.collectAsState()
    var query by remember(kind) { mutableStateOf("") }
    val linkFor = { d: DeviceDto -> if (kind.pairs && !DeviceExtras.isExtra(d.id)) linkOf(d, d.id == here, heartbeat) else null }
    val shown = devices.filter { d -> query.isBlank() || listOfNotNull(d.name, d.code, d.model, d.manufacturer).any { it.contains(query.trim(), ignoreCase = true) } }
        .sortedWith(compareBy<DeviceDto>({ it.id != here }, { linkFor(it)?.ordinal ?: if (it.status == "RETIRED") 9 else 0 },
            { it.status != "PROVISIONING" }, { it.name.lowercase() }))
    Column(modifier.padding(top = if (size.isPhone) 6.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if (size.isPhone) 16.dp else 22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The way back is in the page's header now.
            KindThumb(kind, if (size.isPhone) 44.dp else 54.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(kind.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (size.isPhone) 18.sp else 21.sp, letterSpacing = (-0.2).sp,
                    color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // How they're doing, at a glance.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(if (devices.size == 1) "1 device" else "${devices.size} devices", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp, color = Kit.Muted)
                    if (kind.pairs) Link.entries.forEach { link ->
                        val count = devices.count { linkFor(it) == link }
                        if (count > 0) LinkCount(link, count)
                    }
                }
            }
        }
        if (devices.size > 6) SearchField(query, { query = it }, if (size.isPhone) Modifier.fillMaxWidth() else Modifier.width(380.dp),
            placeholder = "Find a ${kind.one}", height = 44.dp)
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty(if (devices.isEmpty()) "No ${kind.title.lowercase()} yet" else "Nothing matches",
                    if (devices.isEmpty()) "Add one, then connect it." else "Try another name or code.", Icons.Outlined.Devices)
            }
        } else ScrollColumn(Modifier.fillMaxWidth().weight(1f)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Small cards (user, 2026-11-25: "much smaller"): as many as fit at about 220 dp, five at most; two on a phone.
                val columns = if (size.isPhone) 2 else ((maxWidth + 12.dp) / (220.dp + 12.dp)).toInt().coerceIn(2, 5)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    shown.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { device ->
                                DeviceCard(device, kind, linkFor(device), device.id == here, state, canEdit, Modifier.weight(1f).fillMaxHeight(),
                                    onOpen = { onOpen(device) }, onEdit = { onEdit(device) }, onConfirm = { onConfirm(device) }, onRemove = { onRemove(device) })
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** "● 3 online": a state and how many, in its colour. */
@Composable
private fun LinkCount(link: Link, count: Int) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(link.color.copy(alpha = 0.10f)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(link.color))
        Text(when (link) {
            Link.ONLINE -> "$count in use · online"
            Link.OFFLINE -> "$count in use · offline"
            Link.NOT_CONNECTED -> "$count not connected"
            Link.NOT_IN_USE -> "$count switched off"
        }, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp,
            color = if (link == Link.OFFLINE) Kit.Muted else link.color, maxLines = 1)
    }
}

/**
 * A device as a small card (user, 2026-11-25: "make the cards much smaller"): its picture on a short black band with its
 * state as a small white pill and "This device" on the one you're on; its name, code, and one line saying how it is (seen
 * now, last seen, not connected yet with Connect, in repair). The whole card opens it; ⋮ for whoever manages devices.
 */
@Composable
private fun DeviceCard(
    device: DeviceDto, kind: DeviceKind, link: Link?, here: Boolean, state: DevicesState, enabled: Boolean, modifier: Modifier,
    onOpen: () -> Unit, onEdit: () -> Unit, onConfirm: () -> Unit, onRemove: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val zoom by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1.06f else 1f, androidx.compose.animation.core.tween(280))
    val off = link == Link.NOT_IN_USE || (link == null && (device.status == "RETIRED" || device.status == "INACTIVE"))
    // A printer says whether it answers (tills check every minute).
    val reach = if (kind == DeviceKind.PRINTER) reachOf(device) else null
    val accent = when {
        here -> Kit.Ink
        link != null -> link.color
        reach != null -> reach.color
        else -> deviceStatusColor(device.status)
    }
    val now = Clock.System.now()
    val zone = RestaurantTime.zone
    Surface(onClick = onOpen, modifier = modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand).alpha(if (off) 0.6f else 1f),
        shape = RoundedCornerShape(14.dp), color = Color.White,
        border = BorderStroke(if (here) 1.5.dp else 1.dp, if (here) Kit.Ink.copy(alpha = 0.55f) else if (hovered) accent.copy(alpha = 0.5f) else Kit.RowBorder),
        shadowElevation = if (hovered) 6.dp else 1.dp, interactionSource = interaction) {
        Column {
            Box(Modifier.fillMaxWidth().height(92.dp).clipToBounds().background(PhotoBlack)) {
                Image(painterResource(kind.image), kind.title, Modifier.fillMaxSize().padding(vertical = 4.dp).graphicsLayer { scaleX = zoom; scaleY = zoom },
                    contentScale = ContentScale.Fit,
                    colorFilter = if (off) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null)
                if (reach != null) StatePill(reach.icon, reach.label, reach.color, dot = reach == Reach.ONLINE, modifier = Modifier.align(Alignment.TopStart))
                else StatePill(link?.icon ?: Icons.Outlined.Circle,
                    when {
                        link == null || link == Link.NOT_IN_USE -> deviceStatusLabel(device.status)
                        link == Link.NOT_CONNECTED -> "Not connected"
                        else -> link.label
                    },
                    if (link == Link.OFFLINE) Kit.Muted else link?.color ?: accent, dot = link == Link.ONLINE,
                    modifier = Modifier.align(Alignment.TopStart))
                if (here) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Kit.Ink),
                    Alignment.Center) {
                    Icon(Icons.Outlined.Computer, "This device", Modifier.size(14.dp), tint = Color.White)
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 4.dp, top = 9.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.canEdit) Box(Modifier.size(28.dp), Alignment.Center) {
                        RowActionsMenu(listOfNotNull(
                            RowAction("Open", Icons.Outlined.Info, onClick = onOpen),
                            RowAction("Confirm it works", Icons.Outlined.Verified, onClick = onConfirm).takeIf { link == null && device.status == "PROVISIONING" },
                            RowAction("Edit", Icons.Outlined.Edit, onClick = onEdit).takeIf { !DeviceExtras.isExtra(device.id) },
                            RowAction("Remove", Icons.Outlined.DeleteOutline, danger = true, onClick = onRemove)
                        ), busy = !enabled)
                    }
                }
                Text(listOfNotNull(device.code, listOfNotNull(device.manufacturer, device.model).joinToString(" ").ifBlank { null }).joinToString(" · "),
                    Modifier.padding(end = 8.dp), fontFamily = Inter(), fontSize = 11.5.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                // One line on how it is.
                Row(Modifier.padding(top = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (line, tone) = when (link) {
                        Link.ONLINE -> (if (here) "You're on it" else "Seen now") to OnlineGreen
                        Link.OFFLINE -> (device.lastSeenAt?.let { "Seen ${tr(agoText(it, now, zone))}" } ?: "Not seen yet") to Kit.Muted
                        Link.NOT_CONNECTED -> "Not set up yet" to Kit.Amber
                        Link.NOT_IN_USE -> deviceStatusLabel(device.status) to Kit.Purple
                        null -> when (kind) {
                            DeviceKind.PRINTER -> when (reach) {
                                Reach.OFFLINE -> (device.printerProblem ?: "It doesn't answer") to Kit.Danger
                                Reach.ONLINE -> (device.printerIp ?: connectionLabel(device.printerConnectionType)) to OnlineGreen
                                else -> (device.printerIp ?: connectionLabel(device.printerConnectionType)) to Kit.Muted
                            }
                            else -> (if (device.status == "ACTIVE") "Plugged in" else "Not checked yet") to Kit.Muted
                        }
                    }
                    Icon(link?.icon ?: reach?.icon ?: if (kind == DeviceKind.PRINTER) Icons.Outlined.Lan else Icons.Outlined.Cable, null, Modifier.size(14.dp), tint = tone)
                    Text(line, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = tone,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (link == Link.NOT_CONNECTED && state.mayConnect(device)) {
                        Text("Connect", Modifier.clip(RoundedCornerShape(50)).background(Kit.Ink).clickable(onClick = onOpen)
                            .padding(horizontal = 10.dp, vertical = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.5.sp,
                            letterSpacing = 0.sp, color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A white pill on a device's picture: its state's icon (a live dot when online) and word, in its colour. */
@Composable
private fun StatePill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, dot: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier.padding(7.dp).shadow(1.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Color.White)
        .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (dot) Box(Modifier.size(7.dp).clip(CircleShape).background(color)) else Icon(icon, null, Modifier.size(12.dp), tint = color)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.sp, color = color, maxLines = 1)
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

/** The codes made for the open device, newest first. */
@Composable
internal fun PairingCodeList(state: DevicesState, model: DevicesScreenModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.pairingCodes.take(5).forEach { code -> PairingCodeRow(code, state, model) }
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
            .background(if (code.active) Kit.Canvas else Color.White).padding(horizontal = 12.dp, vertical = 9.dp),
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
