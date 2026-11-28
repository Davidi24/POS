@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.admin.devices.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.devices.DeviceDraft
import com.saporini.mobile_desktop.admin.devices.DevicesScreenModel
import com.saporini.mobile_desktop.admin.devices.DevicesState
import com.saporini.mobile_desktop.admin.devices.MAX_DEVICE_CODE
import com.saporini.mobile_desktop.admin.devices.MAX_DEVICE_NAME
import com.saporini.mobile_desktop.admin.devices.MAX_DEVICE_NOTES
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToggleRow
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.i18n.tr
import com.saporini.mobile_desktop.core.printing.PrinterLink
import com.saporini.mobile_desktop.core.printing.rememberBluetoothAccess
import com.saporini.mobile_desktop.core.session.TokenStore
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

// Adding a printer or a card terminal in steps, each its own page (user, 2026-11-28: "search for the device, choose by cable or
// Bluetooth, if it reads any it lists them, then the things to fill open, like connecting a keyboard to a laptop; or connect
// it by hand; separate, not all in one form"). A cash drawer starts on its printer. Changing how an existing printer or drawer
// is connected uses the same pages without the last one.

private enum class AddStep { HOW, FIND, HOST, MANUAL, DETAILS }

/** Where the search looks. */
internal enum class FindWay(val label: String, val icon: ImageVector) {
    NETWORK("Network", Icons.Outlined.Lan),
    CABLE("Cable", Icons.Outlined.Cable),
    BLUETOOTH("Bluetooth", Icons.Outlined.Bluetooth)
}

/** Something the search found: its name and where it is (an address, a printer's name here, a port). */
internal data class Found(val name: String, val address: String, val way: FindWay)

/** Whether the add steps (not the plain editor) handle [draft]: a new printer, card terminal or drawer, or a connection change. */
internal fun usesAddSteps(draft: DeviceDraft, state: DevicesState): Boolean =
    state.draftPart == "CONNECTION" || (draft.id == null && (draft.printer || draft.deviceType == "PAYMENT_TERMINAL" || draft.deviceType == "CASH_DRAWER"))

@Composable
internal fun AddDeviceDialog(draft: DeviceDraft, state: DevicesState, model: DevicesScreenModel) {
    val kind = when {
        draft.printer -> DeviceKind.PRINTER
        draft.deviceType == "PAYMENT_TERMINAL" -> DeviceKind.CARD
        else -> DeviceKind.DRAWER
    }
    val connectionOnly = state.draftPart == "CONNECTION"
    val first = if (kind == DeviceKind.DRAWER) AddStep.MANUAL else AddStep.HOW
    var step by remember(draft.id, connectionOnly) { mutableStateOf(first) }
    var cameFrom by remember(draft.id, connectionOnly) { mutableStateOf(first) }
    val here by TokenStore.till.collectAsState()
    val hereDevice = state.devices.firstOrNull { it.id == here?.deviceId && it.deviceType in setOf("POS_TERMINAL", "TERMINAL", "TABLET") }
    fun picked(found: Found) {
        model.change { d ->
            when (kind) {
                DeviceKind.PRINTER -> when (found.way) {
                    FindWay.NETWORK -> d.copy(connection = "NETWORK", printerIp = found.address, printerPortText = "9100", systemPrinterName = "", hostDeviceId = null)
                    FindWay.CABLE -> d.copy(connection = "USB", systemPrinterName = found.address, printerIp = "", hostDeviceId = here?.deviceId)
                    FindWay.BLUETOOTH -> d.copy(connection = "BLUETOOTH", systemPrinterName = found.address, printerIp = "", hostDeviceId = here?.deviceId)
                }
                else -> d.copy(linkedDeviceId = hereDevice?.id ?: d.linkedDeviceId, linkType = if (found.way == FindWay.CABLE) "USB" else "BLUETOOTH",
                    linkAddress = found.address, name = if (d.id == null && d.name.startsWith("Card terminal")) found.name.take(MAX_DEVICE_NAME) else d.name)
            }
        }
        when {
            // Found on a device that isn't a till: which till or tablet takes its card payments (user, 2026-11-28: "search, find it, connect it").
            kind == DeviceKind.CARD && hereDevice == null -> step = AddStep.HOST
            connectionOnly -> model.save()
            else -> { cameFrom = AddStep.FIND; step = AddStep.DETAILS }
        }
    }
    val title = when (step) {
        AddStep.HOW -> if (connectionOnly) "How is ${draft.name} connected?" else "Add a ${kind.one}"
        AddStep.FIND -> "Find the ${kind.one}"
        AddStep.HOST -> "Which till or tablet uses it?"
        AddStep.MANUAL -> when (kind) {
            DeviceKind.PRINTER -> "Where is the printer?"
            DeviceKind.CARD -> "Connect the card terminal"
            else -> "Which printer is it plugged into?"
        }
        AddStep.DETAILS -> "Name it"
    }
    val manualReady = when (kind) {
        DeviceKind.PRINTER -> when (draft.connection) {
            "NETWORK" -> draft.printerIp.isNotBlank()
            else -> draft.systemPrinterName.isNotBlank()
        }
        DeviceKind.CARD -> draft.linkedDeviceId != null && (draft.linkType == "USB" || draft.linkAddress.isNotBlank())
        else -> draft.linkedDeviceId != null
    }
    val back: (() -> Unit)? = when (step) {
        AddStep.FIND, AddStep.MANUAL -> if (first == AddStep.HOW) ({ step = AddStep.HOW }) else null
        AddStep.HOST -> ({ step = AddStep.FIND })
        AddStep.DETAILS -> ({ step = cameFrom })
        AddStep.HOW -> null
    }
    AppDialog(title, model::cancelEdit, maxWidth = 620.dp, busy = state.saving, onBack = back, scrollKey = step,
        buttons = {
            KitButton("Cancel", model::cancelEdit, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            when (step) {
                AddStep.MANUAL -> KitButton(if (connectionOnly) "Save" else "Next", {
                    if (connectionOnly) model.save() else { cameFrom = AddStep.MANUAL; step = AddStep.DETAILS }
                }, icon = if (connectionOnly) Icons.Outlined.Check else Icons.Outlined.ChevronRight, enabled = manualReady, loading = state.saving)
                AddStep.HOST -> KitButton("Next", { cameFrom = AddStep.HOST; step = AddStep.DETAILS }, icon = Icons.Outlined.ChevronRight,
                    enabled = draft.linkedDeviceId != null)
                AddStep.DETAILS -> KitButton("Add ${kind.one}", model::save, icon = Icons.Outlined.Check, loading = state.saving)
                else -> {}
            }
        }) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        when (step) {
            AddStep.HOW -> {
                ChoiceTile(Icons.Outlined.Search, "Search for it", when (kind) {
                    DeviceKind.PRINTER -> "Finds the printers on the network, on this computer, or paired by Bluetooth."
                    else -> "Finds what's paired by Bluetooth or plugged in by cable on this device."
                }) { step = AddStep.FIND }
                ChoiceTile(Icons.Outlined.EditNote, "Connect it by hand", when (kind) {
                    DeviceKind.PRINTER -> "Type its address or pick it yourself."
                    else -> "Choose the till or tablet and how it's connected."
                }) { step = AddStep.MANUAL }
            }
            AddStep.FIND -> FindPanel(kind, onPick = ::picked)
            AddStep.HOST -> {
                Text("Card payments on the till or tablet you choose go to it.", fontFamily = Inter(), fontSize = 13.5.sp, lineHeight = 19.sp,
                    letterSpacing = 0.sp, color = Kit.Muted)
                val hosts = state.devices.filter { it.deviceType in setOf("POS_TERMINAL", "TERMINAL", "TABLET") && it.status != "RETIRED" }
                    .sortedWith(compareBy({ it.deviceType == "TABLET" }, { it.name.lowercase() }))
                if (hosts.isEmpty()) Text("Add a till or tablet first: a card terminal works only through one.", fontFamily = Inter(), fontSize = 13.sp,
                    letterSpacing = 0.sp, color = Kit.Amber)
                else HostPicks(hosts, draft.linkedDeviceId) { id -> model.change { it.copy(linkedDeviceId = id) } }
            }
            AddStep.MANUAL -> when (kind) {
                DeviceKind.PRINTER -> ManualPrinter(draft, model)
                DeviceKind.CARD -> TerminalLinkFields(draft, state, model)
                else -> DrawerLinkFields(draft, state, model)
            }
            AddStep.DETAILS -> Details(kind, draft, state, model) { step = first }
        }
    }
}

/** One of the two ways to add it, as a big tile. */
@Composable
private fun ChoiceTile(icon: ImageVector, title: String, line: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(14.dp))
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Kit.GreenSoft), Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = Kit.Green)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.sp, color = Kit.Ink)
            Text(line, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp, color = Kit.Muted)
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = Kit.Faint)
    }
}

/**
 * The search: a bar of where to look (a printer: Network | Cable | Bluetooth; a card terminal: Bluetooth | Cable), what it
 * reads there as a list, a click picks one. Nothing found says why and how to make it show.
 */
@Composable
private fun FindPanel(kind: DeviceKind, onPick: (Found) -> Unit) {
    val phone = com.saporini.mobile_desktop.core.session.deviceSystem().startsWith("Android")
    // A printer: the network, and cable on a computer or Bluetooth on a phone or tablet (a computer's Bluetooth printers print
    // through its own printer list). A card terminal: Bluetooth or cable.
    val ways = when {
        kind != DeviceKind.PRINTER -> listOf(FindWay.BLUETOOTH, FindWay.CABLE)
        phone -> listOf(FindWay.NETWORK, FindWay.BLUETOOTH)
        else -> listOf(FindWay.NETWORK, FindWay.CABLE)
    }
    var way by remember(kind) { mutableStateOf(ways.first()) }
    var round by remember { mutableIntStateOf(0) }
    val access = rememberBluetoothAccess()
    var found by remember(way, round, access.allowed) { mutableStateOf<List<Found>?>(null) }
    LaunchedEffect(way, round, access.allowed) {
        found = null
        if (way == FindWay.BLUETOOTH) return@LaunchedEffect
        found = runCatching {
            withContext(Dispatchers.Default) {
                when {
                    way == FindWay.NETWORK -> PrinterLink.findOnNetwork().map { Found(it, it, way) }
                    kind == DeviceKind.PRINTER && way == FindWay.CABLE -> PrinterLink.systemPrinters().map { Found(it, it, way) }
                    kind == DeviceKind.PRINTER -> if (access.allowed) PrinterLink.bluetoothPrinters().map { Found(it.name, it.address, way) } else emptyList()
                    way == FindWay.BLUETOOTH -> if (access.allowed) PrinterLink.pairedDevices().map { Found(it.name, it.address, way) } else emptyList()
                    else -> PrinterLink.cablePorts().map { Found(it.name, it.address, way) }
                }
            }
        }.getOrDefault(emptyList())
    }
    OverviewTabs(ways.map { tr(it.label) }, tr(way.label), { label -> way = ways.first { tr(it.label) == label } }, Modifier.fillMaxWidth())
    val list = found
    when {
        way == FindWay.BLUETOOTH -> BluetoothFinder(kind, access, onPick)
        list == null -> Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Kit.Green, strokeWidth = 2.dp)
            Text(when (way) {
                FindWay.NETWORK -> "Looking for printers on this network…"
                FindWay.CABLE -> "Looking for what's plugged in…"
                FindWay.BLUETOOTH -> "Looking for paired devices…"
            }, fontFamily = Inter(), fontSize = 13.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
        }
        list.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(nothingFound(kind, way, phone), fontFamily = Inter(), fontSize = 13.5.sp, lineHeight = 19.sp, letterSpacing = 0.sp, color = Kit.Ink)
            KitButton("Search again", { round++ }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Refresh)
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (list.size == 1) "1 found. Pick it." else "${list.size} found. Pick yours.", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
            list.forEach { one ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.RowBorder, RoundedCornerShape(12.dp))
                    .clickable { onPick(one) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Tint), Alignment.Center) {
                        Icon(if (kind == DeviceKind.PRINTER) Icons.Outlined.Print else one.way.icon, null, Modifier.size(18.dp), tint = Kit.Ink)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(one.name, translate = false, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink)
                        if (one.address != one.name) Text(one.address, translate = false, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = Kit.Faint)
                }
            }
            Text("Search again", Modifier.clip(RoundedCornerShape(6.dp)).clickable { round++ }.padding(4.dp), fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Green)
        }
    }
}

private fun nothingFound(kind: DeviceKind, way: FindWay, phone: Boolean): String = when {
    way == FindWay.NETWORK -> "No printer answered on this network. Check it's switched on and on the same Wi-Fi or cable."
    kind == DeviceKind.PRINTER && way == FindWay.CABLE && phone -> "On a phone or tablet, printers connect by Bluetooth or the network."
    kind == DeviceKind.PRINTER && way == FindWay.CABLE -> "No printer is installed on this computer. Plug it in and install it, then search again."
    kind == DeviceKind.PRINTER && !phone -> "On a computer, pair the printer in its Bluetooth settings: it then shows under Cable."
    way == FindWay.BLUETOOTH -> "Nothing is paired yet. Pair it in this device's Bluetooth settings, then search again."
    else -> "Nothing is plugged in by cable. Plug it in, then search again."
}

/** A printer connected by hand: the network address, a printer installed here, or one paired by Bluetooth. */
@Composable
private fun ManualPrinter(draft: DeviceDraft, model: DevicesScreenModel) {
    val ways = listOf("NETWORK" to "Network", "USB" to "On this computer", "BLUETOOTH" to "Bluetooth")
    OverviewTabs(ways.map { tr(it.second) }, tr(ways.firstOrNull { it.first == draft.connection }?.second ?: "Network"),
        { label -> ways.firstOrNull { tr(it.second) == label }?.let { (code, _) -> model.change { it.copy(connection = code) } } }, Modifier.fillMaxWidth())
    when (draft.connection) {
        "USB" -> SystemPrinterPicker(draft, model)
        "BLUETOOTH" -> BluetoothPrinterPicker(draft, model)
        else -> FieldPair(
            { m -> TextInput(draft.printerIp, { v -> model.change { it.copy(printerIp = v.trim()) } }, m, label = "IP address", required = true,
                icon = Icons.Outlined.Lan, placeholder = "192.168.1.50", maxLength = 45) },
            { m -> TextInput(draft.printerPortText, { v -> model.change { it.copy(printerPortText = v.filter(Char::isDigit).take(5)) } }, m,
                label = "Port", optional = true, icon = Icons.Outlined.Numbers, placeholder = "9100", keyboardType = KeyboardType.Number) }
        )
    }
}

/** The last step: what it's connected to (with Change), its kind (printers), name and code; the rest folded. */
@Composable
private fun Details(kind: DeviceKind, draft: DeviceDraft, state: DevicesState, model: DevicesScreenModel, onChangeConnection: () -> Unit) {
    var more by remember { mutableStateOf(false) }
    val connection = when (kind) {
        DeviceKind.PRINTER -> when (draft.connection) {
            "NETWORK" -> "Network · ${draft.printerIp}"
            "BLUETOOTH" -> "Bluetooth · ${draft.systemPrinterName}"
            else -> "On this computer · ${draft.systemPrinterName}"
        }
        DeviceKind.CARD -> "On ${state.devices.firstOrNull { it.id == draft.linkedDeviceId }?.name ?: "–"} · ${if (draft.linkType == "USB") "Cable" else "Bluetooth"}"
        else -> "In ${state.printers.firstOrNull { it.id == draft.linkedDeviceId }?.name ?: "–"}"
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.GreenSoft).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Kit.Green)
        Text(connection, Modifier.weight(1f), translate = false, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
            letterSpacing = 0.sp, color = Kit.Green)
        Text("Change", Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onChangeConnection).padding(4.dp), fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Green)
    }
    if (kind == DeviceKind.PRINTER) PresetTiles(draft, model)
    FieldPair(
        { m -> TextInput(draft.name, { v -> model.change { it.copy(name = v) } }, m, label = "Name", required = true,
            icon = if (kind == DeviceKind.PRINTER) Icons.Outlined.Print else Icons.Outlined.Devices, maxLength = MAX_DEVICE_NAME) },
        { m -> TextInput(draft.code, { v -> model.change { it.copy(code = v.uppercase()) } }, m, label = "Short code", required = true,
            icon = Icons.Outlined.Badge, maxLength = MAX_DEVICE_CODE, hint = "Unique in the restaurant") }
    )
    if (kind == DeviceKind.PRINTER && draft.printerRole != "DOCUMENT") {
        TextInput(draft.paperWidthText, { v -> model.change { it.copy(paperWidthText = v.filter(Char::isDigit).take(3)) } }, label = "Paper width",
            suffix = "mm", keyboardType = KeyboardType.Number, hint = "Usually 58 or 80")
        ToggleRow("Cut the paper after each print", draft.autoCut, { v -> model.change { it.copy(autoCut = v) } })
        ToggleRow("Open the cash drawer", draft.cashDrawerKick, { v -> model.change { it.copy(cashDrawerKick = v) } },
            detail = "For receipt printers with a cash drawer plugged in")
    }
    if (!more) Text("+ More details (optional)", Modifier.clip(RoundedCornerShape(6.dp)).clickable { more = true }.padding(4.dp), fontFamily = Inter(),
        fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Green)
    else {
        FieldPair(
            { m -> TextInput(draft.manufacturer, { v -> model.change { it.copy(manufacturer = v) } }, m, label = "Maker", maxLength = 100) },
            { m -> TextInput(draft.model, { v -> model.change { it.copy(model = v) } }, m, label = "Model", maxLength = 100) }
        )
        TextInput(draft.serialNumber, { v -> model.change { it.copy(serialNumber = v) } }, label = "Serial number", maxLength = 100)
        TextInput(draft.notes, { v -> model.change { it.copy(notes = v) } }, label = "Notes", optional = true, singleLine = false, minLines = 2,
            placeholder = "Where it is, who to call when it breaks…", maxLength = MAX_DEVICE_NOTES)
    }
}

/**
 * Bluetooth like pairing a keyboard (user, 2026-11-28: "allow this device to use Bluetooth"): let this device use it, switch it
 * on, look for what's nearby (paired ones first), and a click on a new one pairs it, then goes on.
 */
@Composable
private fun BluetoothFinder(kind: DeviceKind, access: com.saporini.mobile_desktop.core.printing.BluetoothAccess, onPick: (Found) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var round by remember { mutableIntStateOf(0) }
    var power by remember(round, access.allowed) { mutableStateOf<com.saporini.mobile_desktop.core.printing.BluetoothPower?>(null) }
    var nearby by remember(round, access.allowed) { mutableStateOf<List<com.saporini.mobile_desktop.core.printing.NearbyDevice>?>(null) }
    var pairing by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(round, access.allowed) {
        if (!access.allowed || !PrinterLink.canScanBluetooth) return@LaunchedEffect
        power = runCatching { PrinterLink.bluetoothPower() }.getOrDefault(com.saporini.mobile_desktop.core.printing.BluetoothPower.NONE)
        if (power == com.saporini.mobile_desktop.core.printing.BluetoothPower.ON) {
            nearby = runCatching { PrinterLink.scanBluetooth(10) }.getOrDefault(emptyList())
        }
    }
    when {
        !access.allowed -> BluetoothAsk("Let this device use Bluetooth to find the ${kind.one}.", "Allow Bluetooth", access.ask)
        !PrinterLink.canScanBluetooth -> {
            BluetoothAsk("On this computer, pair the ${kind.one} in the Bluetooth settings, then search again.", "Open Bluetooth settings") {
                val os = com.saporini.mobile_desktop.core.session.deviceSystem()
                runCatching { uri.openUri(if (os.startsWith("Windows")) "ms-settings:bluetooth" else "x-apple.systempreferences:com.apple.BluetoothSettings") }
            }
        }
        power == null -> Searching("Checking Bluetooth…")
        power == com.saporini.mobile_desktop.core.printing.BluetoothPower.NONE -> Line("This device has no Bluetooth.")
        power == com.saporini.mobile_desktop.core.printing.BluetoothPower.OFF -> BluetoothAsk("Bluetooth is off on this device.", "Turn on Bluetooth") {
            scope.launch {
                if (!PrinterLink.turnOnBluetooth()) access.turnOn()
                kotlinx.coroutines.delay(1500)
                round++
            }
        }
        nearby == null -> Searching("Looking for Bluetooth devices nearby… Switch it on and keep it close.")
        pairing != null -> Searching("Pairing with $pairing…")
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            problem?.let { MessageBar(it, MessageKind.ERROR) }
            val list = nearby.orEmpty()
            if (list.isEmpty()) Line("Nothing found nearby. Switch it on, put it in pairing mode, then search again.")
            list.forEach { one ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.RowBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        problem = null
                        if (one.paired) onPick(Found(one.name, one.address, FindWay.BLUETOOTH))
                        else {
                            pairing = one.name
                            scope.launch {
                                val failed = PrinterLink.pairBluetooth(one.address)
                                pairing = null
                                if (failed == null) onPick(Found(one.name, one.address, FindWay.BLUETOOTH)) else problem = failed
                            }
                        }
                    }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Tint), Alignment.Center) {
                        Icon(Icons.Outlined.Bluetooth, null, Modifier.size(18.dp), tint = Kit.Ink)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(one.name, translate = false, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink)
                        Text(one.address, translate = false, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted)
                    }
                    // Paired: a quiet green tick; new: a real small button (user, 2026-11-28: "the pair button looks ugly").
                    if (one.paired) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = Kit.Green)
                        Text("Paired", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Green)
                    } else Row(Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(Kit.Green).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Link, null, Modifier.size(16.dp), tint = Color.White)
                        Text("Pair", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Color.White)
                    }
                }
            }
            KitButton("Search again", { round++ }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Refresh)
        }
    }
}

/** A Bluetooth step that needs the person: a line and its button. */
@Composable
internal fun BluetoothAsk(text: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Tint).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Bluetooth, null, Modifier.size(22.dp), tint = Kit.Blue)
            Text(text, Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.5.sp, lineHeight = 19.sp, letterSpacing = 0.sp, color = Kit.Ink)
        }
        KitButton(button, onClick, icon = Icons.Outlined.Bluetooth)
    }
}

@Composable
private fun Searching(text: String) {
    Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), color = Kit.Green, strokeWidth = 2.dp)
        Text(text, fontFamily = Inter(), fontSize = 13.5.sp, lineHeight = 19.sp, letterSpacing = 0.sp, color = Kit.Muted)
    }
}

@Composable
private fun Line(text: String) {
    Text(text, fontFamily = Inter(), fontSize = 13.5.sp, lineHeight = 19.sp, letterSpacing = 0.sp, color = Kit.Ink)
}
