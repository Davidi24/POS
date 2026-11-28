@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.admin.devices.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.collectAsState
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.i18n.tr
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.Restaurant
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.devices.DEVICE_STATUSES
import com.saporini.mobile_desktop.admin.devices.DEVICE_TYPES
import com.saporini.mobile_desktop.admin.devices.DeviceDraft
import com.saporini.mobile_desktop.admin.devices.DeviceDto
import com.saporini.mobile_desktop.admin.devices.DevicesScreenModel
import com.saporini.mobile_desktop.admin.devices.DevicesState
import com.saporini.mobile_desktop.admin.devices.MAX_DEVICE_CODE
import com.saporini.mobile_desktop.admin.devices.MAX_DEVICE_NAME
import com.saporini.mobile_desktop.admin.devices.MAX_DEVICE_NOTES
import com.saporini.mobile_desktop.admin.devices.PRINTER_CONNECTIONS
import com.saporini.mobile_desktop.admin.devices.PairingCodeDto
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToggleRow
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime

/** Adding or editing a device (or a printer, which has its connection and paper settings). */
@Composable
internal fun DeviceEditorDialog(draft: DeviceDraft, state: DevicesState, model: DevicesScreenModel) {
    val deviceKind = if (draft.printer) DeviceKind.PRINTER else DeviceKind.entries.firstOrNull { draft.deviceType in it.types } ?: DeviceKind.TABLET
    val kind = deviceKind.one
    AppDialog(
        if (draft.id == null) "Add a $kind" else "Edit ${draft.name.ifBlank { kind }}",
        model::cancelEdit,
        subtitle = if (draft.printer) "Printers belong to the whole restaurant. Next you choose what it prints."
        else if (draft.id == null) "Next you'll connect it and confirm it works." else null,
        busy = state.saving,
        buttons = {
            KitButton("Cancel", model::cancelEdit, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton(if (draft.id == null) "Add $kind" else "Save", model::save, icon = Icons.Outlined.Check, loading = state.saving)
        }
    ) {
        if (draft.id == null && !draft.printer) KindBanner(deviceKind, 120.dp)
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        FieldPair(
            { m -> TextInput(draft.name, { v -> model.change { it.copy(name = v) } }, m, label = "Name", required = true,
                icon = if (draft.printer) Icons.Outlined.Print else Icons.Outlined.Devices, placeholder = if (draft.printer) "Kitchen printer" else "Front till",
                maxLength = MAX_DEVICE_NAME) },
            { m -> TextInput(draft.code, { v -> model.change { it.copy(code = v.uppercase()) } }, m, label = "Short code", required = true,
                icon = Icons.Outlined.Badge, placeholder = if (draft.printer) "PRN-1" else "TILL-1", maxLength = MAX_DEVICE_CODE,
                hint = "Unique in the restaurant") }
        )
        if (!draft.printer) {
            // A new device keeps to its kind (a tablet can be a waiter tablet, a kitchen screen or the guest display); it starts as "To confirm".
            val kinds = if (draft.id == null) deviceKind.types.filter { it in DEVICE_TYPES } else DEVICE_TYPES
            if (kinds.size > 1 || draft.id != null) FieldPair(
                { m -> SelectInput(draft.deviceType, kinds, ::deviceTypeLabel, { v -> model.change { it.copy(deviceType = v) } }, m, label = "Kind", required = true) },
                { m -> if (draft.id != null) SelectInput(draft.status, DEVICE_STATUSES, ::deviceStatusLabel, { v -> model.change { it.copy(status = v) } }, m, label = "Status")
                    else Spacer(m) }
            )
            // How a card terminal or drawer is connected has its own window now (Connect / Change on its page; 2026-11-28).
        } else {
            // Ready-made kinds fill in paper, cut and drawer (user, 2026-11-27); an existing printer changes its kind in a list.
            if (draft.id == null) PresetTiles(draft, model)
            else SelectInput(draft.printerRole, com.saporini.mobile_desktop.admin.devices.PrinterPreset.entries.map { it.role },
                { role -> com.saporini.mobile_desktop.admin.devices.printerRoleLabel(role) }, { v -> model.change { it.copy(printerRole = v) } }, Modifier.fillMaxWidth(),
                label = "Kind of printer")
            val document = draft.printerRole == "DOCUMENT"
            // How it's connected has its own window (How it's reached → Change; 2026-11-28); here what it is.
            if (!document) TextInput(draft.paperWidthText, { v -> model.change { it.copy(paperWidthText = v.filter(Char::isDigit).take(3)) } },
                label = "Paper width", suffix = "mm", keyboardType = KeyboardType.Number, hint = "Usually 58 or 80")
            if (!document) {
                ToggleRow("Cut the paper after each print", draft.autoCut, { v -> model.change { it.copy(autoCut = v) } })
                ToggleRow("Open the cash drawer", draft.cashDrawerKick, { v -> model.change { it.copy(cashDrawerKick = v) } },
                    detail = "For receipt printers with a cash drawer plugged in")
            }
        }
        Caption("Hardware (optional)")
        FieldPair(
            { m -> TextInput(draft.manufacturer, { v -> model.change { it.copy(manufacturer = v) } }, m, label = "Maker", placeholder = "Epson", maxLength = 100) },
            { m -> TextInput(draft.model, { v -> model.change { it.copy(model = v) } }, m, label = "Model", placeholder = "TM-m30III", maxLength = 100) }
        )
        if (!draft.printer) {
            FieldPair(
                { m -> TextInput(draft.serialNumber, { v -> model.change { it.copy(serialNumber = v) } }, m, label = "Serial number", maxLength = 100) },
                { m -> TextInput(draft.ipAddress, { v -> model.change { it.copy(ipAddress = v.trim()) } }, m, label = "IP address", placeholder = "192.168.1.20", maxLength = 45) }
            )
        } else {
            TextInput(draft.serialNumber, { v -> model.change { it.copy(serialNumber = v) } }, label = "Serial number", maxLength = 100)
        }
        TextInput(draft.notes, { v -> model.change { it.copy(notes = v) } }, label = "Notes", optional = true, singleLine = false, minLines = 2,
            placeholder = "Where it is, who to call when it breaks…", maxLength = MAX_DEVICE_NOTES)
    }
}

/** A new pairing code, shown once in big letters. */
@Composable
internal fun FreshCodeDialog(code: PairingCodeDto, device: DeviceDto?, onDone: () -> Unit) {
    AppDialog("Pairing code", onDone, subtitle = device?.let { "For ${it.name}" }, maxWidth = 480.dp,
        buttons = { KitButton("Done", onDone, icon = Icons.Outlined.Check) }) {
        Text(if (device != null && kindOf(device) != DeviceKind.CARD) "On the device, sign in, open Devices and press “Enter a code”. You're asked to agree when it's typed. It works once and can't be shown again."
            else "Type it on the card terminal. It works once and can't be shown again.",
            fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Ink)
        SelectionContainer {
            Text(
                code.pairingToken ?: "—",
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.GreenSoft)
                    .border(1.dp, Kit.Green.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(vertical = 18.dp, horizontal = 12.dp),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.5.sp,
                color = Kit.Green, textAlign = TextAlign.Center
            )
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Works until ${dateTimeText(code.expiresAt, RestaurantTime.zone)}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
            Text("Select the code to copy it.", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint)
        }
    }
}

/** Confirming a device: connect it (pairing code, test print, a test scan, or opening the drawer), then say it works. */
@Composable
internal fun ConfirmDeviceDialog(device: DeviceDto, state: DevicesState, model: DevicesScreenModel, onDone: (confirmed: Boolean) -> Unit) {
    val kind = kindOf(device)
    var tested by remember(device.id) { mutableStateOf(false) }
    var scanned by remember(device.id) { mutableStateOf("") }
    val code = state.freshCode?.takeIf { it.deviceId == device.id }
    val ready = when {
        kind.pairs -> true
        kind == DeviceKind.SCANNER -> scanned.trim().length >= 4
        else -> tested
    }
    AppDialog("Confirm ${device.name}", { onDone(false) }, subtitle = "${deviceTypeLabel(device.deviceType)} · ${device.code}", maxWidth = 600.dp, busy = state.saving,
        buttons = {
            KitButton("Later", { onDone(false) }, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton("Confirm, it works", { onDone(true) }, icon = Icons.Outlined.Verified, enabled = ready && state.canEdit, loading = state.saving)
        }) {
        KindBanner(kind, 150.dp)
        ConfirmSteps(if (ready) 2 else 1)
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        when (kind) {
            // A till or tablet: someone signs in on it and types the code in Devices, so it's known who set it up (user,
            // 2026-11-25); a card terminal on its own pairing screen.
            DeviceKind.CARD -> {
                // Through its till or tablet only (user, 2026-11-27).
                val host = device.linkedDeviceName
                HowStep(1, if (host != null) "Connect it to $host by ${if (device.linkType == "USB") "cable" else "Bluetooth"}."
                    else "On its page, press Connect and choose the till or tablet.")
                HowStep(2, "Card payments on that till or tablet go to it.")
                KitButton(if (tested) "Try again" else "Try a test payment", { tested = true }, icon = Icons.Outlined.PointOfSale,
                    style = if (tested) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY, enabled = host != null)
                if (tested) Done("Test mode: no payment provider is connected yet, so card payments ask how the card went.")
            }
            DeviceKind.TILL, DeviceKind.TABLET -> {
                HowStep(1, "Open Saporini on the ${kind.one} and sign in with your own account.")
                HowStep(2, if (kind == DeviceKind.TILL) "Go to Admin Hub → Devices → Tills and press “Enter a code”."
                    else "Go to Admin Hub → Devices → Tablets & screens and press “Enter a code”.")
                HowStep(3, "Type this code. You're asked here to agree, then it's connected.")
                if (code?.pairingToken != null) {
                    SelectionContainer {
                        Text(code.pairingToken, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.GreenSoft)
                            .border(1.dp, Kit.Green.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(vertical = 16.dp, horizontal = 12.dp),
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.5.sp, color = Kit.Green, textAlign = TextAlign.Center)
                    }
                    Text("Works until ${dateTimeText(code.expiresAt, RestaurantTime.zone)}. When it shows as connected, confirm it.",
                        fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                } else KitButton("Make a pairing code", { model.newPairingCode(60) }, icon = Icons.Outlined.QrCode2, loading = state.saving, enabled = state.mayConnect(device))
            }
            DeviceKind.PRINTER -> {
                HowStep(1, "Switch it on and load the paper.")
                HowStep(2, if (device.printerIp != null) "Make sure it's on the restaurant's network (${device.printerIp})." else "Plug it in (${connectionLabel(device.printerConnectionType)}).")
                HowStep(3, "Print a test page.")
                KitButton(if (tested) "Print another test page" else "Print a test page", { tested = true; model.testPrinter(device) }, icon = Icons.Outlined.Print, style = if (tested) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY)
                if (tested) Done("A test page went to ${device.name}. Did it come out?")
            }
            DeviceKind.SCANNER -> {
                HowStep(1, "Plug it in (USB) or pair it with the till (Bluetooth).")
                HowStep(2, "Click the field below and scan any barcode, a bottle or a box.")
                TextInput(scanned, { scanned = it }, label = "Test scan", icon = Icons.Outlined.QrCodeScanner, placeholder = "Click here, then scan")
                if (scanned.trim().length >= 4) Done("Read “${scanned.trim()}”. The scanner works.")
            }
            DeviceKind.DRAWER -> {
                HowStep(1, device.linkedDeviceName?.let { "Plug the drawer's cable into $it." } ?: "Edit it and choose the printer it's plugged into.")
                HowStep(2, "Open it from here, like after a cash payment.")
                KitButton(if (tested) "Open it again" else "Open the drawer", { tested = true; model.openDrawer(device) }, icon = Icons.Outlined.PointOfSale,
                    style = if (tested) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY, enabled = device.linkedDeviceId != null)
                if (tested) Done("Sent to ${device.linkedDeviceName}. Did the drawer open?")
            }
        }
    }
}

/** Added → Connect → Confirm, the current one in green. */
@Composable
private fun ConfirmSteps(current: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        listOf("Added", "Connect", "Confirm").forEachIndexed { index, label ->
            val done = index < current
            val now = index == current
            if (index > 0) Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (done || now) Kit.Green else Kit.Border))
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(if (done) Kit.Green else Color.White)
                    .border(2.dp, if (done || now) Kit.Green else Kit.Border, CircleShape), Alignment.Center) {
                    if (done) Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = Color.White)
                    else Text("${index + 1}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (now) Kit.Green else Kit.Muted)
                }
                Text(label, fontFamily = Inter(), fontWeight = if (done || now) FontWeight.Bold else FontWeight.SemiBold, fontSize = 12.sp, color = if (done || now) Kit.Ink else Kit.Muted)
            }
        }
    }
}

@Composable
private fun HowStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Kit.GreenSoft), Alignment.Center) {
            Text("$number", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Kit.Green)
        }
        Text(text, Modifier.padding(top = 2.dp), fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = Kit.Ink)
    }
}

@Composable
private fun Done(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.GreenSoft).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = Kit.Green)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Green)
    }
}

/**
 * A printer on this computer (USB): picked from the printers installed here, and kept as plugged into this till (2026-11-27).
 * On a phone there are none: it's set up on the computer it's plugged into.
 */
@Composable
internal fun SystemPrinterPicker(draft: com.saporini.mobile_desktop.admin.devices.DeviceDraft, model: DevicesScreenModel) {
    val printers = remember { com.saporini.mobile_desktop.core.printing.PrinterLink.systemPrinters() }
    val here = com.saporini.mobile_desktop.core.session.TokenStore.till.collectAsState().value
    if (printers.isEmpty()) {
        Text(if (draft.systemPrinterName.isBlank()) "Set it up on the computer it's plugged into: its printers show there."
            else "On ${draft.systemPrinterName}. Change it on the computer it's plugged into.",
            fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp, color = Kit.Muted)
        return
    }
    SelectInput(draft.systemPrinterName.takeIf { it in printers } ?: "", listOf("") + printers, { if (it.isEmpty()) tr("Choose a printer") else it },
        { v -> model.change { it.copy(systemPrinterName = v, hostDeviceId = here?.deviceId ?: it.hostDeviceId) } },
        Modifier.fillMaxWidth(), label = "Printer on this computer", required = true)
}


/** "What kind of printer?": the ready-made kinds as tiles; one click fills in its paper, cut and drawer (user, 2026-11-27). */
@Composable
internal fun PresetTiles(draft: DeviceDraft, model: DevicesScreenModel) {
    val narrow = com.saporini.mobile_desktop.core.components.LocalNarrowDialog.current
    Caption("What kind of printer?")
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = if (narrow) 2 else 3) {
        com.saporini.mobile_desktop.admin.devices.PrinterPreset.entries.forEach { preset ->
            val chosen = draft.printerRole == preset.role
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (chosen) Kit.GreenSoft else Color.White)
                .border(if (chosen) 1.5.dp else 1.dp, if (chosen) Kit.Green else Kit.Border, RoundedCornerShape(12.dp))
                .clickable { model.usePreset(preset) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(presetIcon(preset), null, Modifier.size(17.dp), tint = if (chosen) Kit.Green else Kit.Ink)
                    Text(preset.title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp,
                        color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1)
                }
                Text(preset.line, fontFamily = Inter(), fontSize = 11.5.sp, lineHeight = 15.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 2)
            }
        }
    }
}

private fun presetIcon(preset: com.saporini.mobile_desktop.admin.devices.PrinterPreset) = when (preset) {
    com.saporini.mobile_desktop.admin.devices.PrinterPreset.RECEIPT -> Icons.Outlined.PointOfSale
    com.saporini.mobile_desktop.admin.devices.PrinterPreset.KITCHEN -> Icons.Outlined.Restaurant
    com.saporini.mobile_desktop.admin.devices.PrinterPreset.BAR -> Icons.Outlined.LocalBar
    com.saporini.mobile_desktop.admin.devices.PrinterPreset.SMALL -> Icons.Outlined.Receipt
    com.saporini.mobile_desktop.admin.devices.PrinterPreset.DOCUMENT -> Icons.Outlined.Description
    com.saporini.mobile_desktop.admin.devices.PrinterPreset.OTHER -> Icons.Outlined.Print
}

/**
 * A Bluetooth printer (user, 2026-11-27): on a phone or tablet, picked from the devices paired with it (Android asks for
 * "Nearby devices" first), and kept as paired with this till, which prints for the others. On a computer a paired printer is
 * in its printer list, so it's added as a printer on this computer.
 */
@Composable
internal fun BluetoothPrinterPicker(draft: DeviceDraft, model: DevicesScreenModel) {
    val access = com.saporini.mobile_desktop.core.printing.rememberBluetoothAccess()
    val here = com.saporini.mobile_desktop.core.session.TokenStore.till.collectAsState().value
    val computer = com.saporini.mobile_desktop.core.printing.PrinterLink.systemPrinters().isNotEmpty() ||
        com.saporini.mobile_desktop.core.session.deviceSystem().let { !it.startsWith("Android") }
    if (computer) {
        Text("On a computer, pair the printer in the computer's Bluetooth settings, then choose “On a computer (USB)”: paired printers show in its printer list.",
            fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp, color = Kit.Muted)
        return
    }
    if (!access.allowed) {
        BluetoothAsk("Let this device use Bluetooth to see its printers.", "Allow Bluetooth", access.ask)
        return
    }
    val paired = remember(access.allowed) { com.saporini.mobile_desktop.core.printing.PrinterLink.bluetoothPrinters() }
    if (paired.isEmpty()) {
        Text(if (draft.systemPrinterName.isBlank()) "Nothing is paired yet. Pair the printer in this device's Bluetooth settings, then come back."
            else "Paired as ${draft.systemPrinterName}.",
            fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp, color = Kit.Muted)
        return
    }
    val addresses = paired.map { it.address }
    SelectInput(draft.systemPrinterName.takeIf { it in addresses } ?: "", listOf("") + addresses,
        { address -> if (address.isEmpty()) tr("Choose a printer") else paired.first { it.address == address }.let { "${it.name} · ${it.address}" } },
        { v -> model.change { it.copy(systemPrinterName = v, hostDeviceId = here?.deviceId ?: it.hostDeviceId) } },
        Modifier.fillMaxWidth(), label = "Paired printer", required = true,
        hint = if (here != null) "The other tills print on it through this one" else "Prints from this device")
}

/**
 * Where a card terminal is connected (user, 2026-11-27: "only with a till or a tablet; Bluetooth and cable, both"): the till or
 * tablet, how, and on a phone or tablet the paired terminal (Bluetooth) or, by cable, the port's name if it has one.
 */
@Composable
internal fun TerminalLinkFields(draft: DeviceDraft, state: DevicesState, model: DevicesScreenModel) {
    val hosts = state.devices.filter { it.deviceType in setOf("POS_TERMINAL", "TERMINAL", "TABLET") && it.status != "RETIRED" }
    Caption("Connected to")
    if (hosts.isEmpty()) {
        Text("Add a till or tablet first: a card terminal works only through one.", fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp,
            letterSpacing = 0.sp, color = Kit.Amber)
        return
    }
    // Each till or tablet with its picture, like the Connect window (user, 2026-11-28: "put the icons of the till or tablet").
    HostPicks(hosts, draft.linkedDeviceId) { id -> model.change { it.copy(linkedDeviceId = id) } }
    if (draft.linkedDeviceId == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("BLUETOOTH" to "Bluetooth", "USB" to "Cable").forEach { (code, label) ->
            val chosen = draft.linkType == code
            Row(Modifier.clip(RoundedCornerShape(50)).background(if (chosen) Kit.GreenSoft else Color.White)
                .border(1.dp, if (chosen) Kit.Green else Kit.Border, RoundedCornerShape(50))
                .clickable { model.change { it.copy(linkType = code, linkAddress = if (it.linkType == code) it.linkAddress else "") } }
                .padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (code == "BLUETOOTH") Icons.Outlined.Bluetooth else Icons.Outlined.Cable, null, Modifier.size(16.dp), tint = if (chosen) Kit.Green else Kit.Muted)
                Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = if (chosen) Kit.Green else Kit.Ink)
            }
        }
    }
    if (draft.linkType == "BLUETOOTH") PairedTerminalPicker(draft.linkAddress) { v -> model.change { it.copy(linkAddress = v) } }
    else TextInput(draft.linkAddress, { v -> model.change { it.copy(linkAddress = v.take(200)) } }, label = "Port", optional = true,
        placeholder = "COM3 or /dev/ttyUSB0", hint = "Plug the cable into the till or tablet above")
}

/** The paired card terminal, on the phone or tablet it's paired with (a computer: its name as paired there). */
@Composable
private fun PairedTerminalPicker(address: String, onAddress: (String) -> Unit) {
    val access = com.saporini.mobile_desktop.core.printing.rememberBluetoothAccess()
    val phone = com.saporini.mobile_desktop.core.session.deviceSystem().startsWith("Android")
    if (!phone) {
        TextInput(address, { v -> onAddress(v.take(200)) }, label = "Paired as", required = true,
            placeholder = "Card terminal 1", hint = "Its name in the computer's Bluetooth settings")
        return
    }
    if (!access.allowed) {
        BluetoothAsk("Let this device use Bluetooth to see the card terminal.", "Allow Bluetooth", access.ask)
        return
    }
    val paired = remember(access.allowed) { com.saporini.mobile_desktop.core.printing.PrinterLink.bluetoothPrinters() }
    if (paired.isEmpty()) {
        Text("Nothing is paired yet. Pair the terminal in this device's Bluetooth settings, then come back.", fontFamily = Inter(), fontSize = 12.5.sp,
            lineHeight = 17.sp, letterSpacing = 0.sp, color = Kit.Muted)
        return
    }
    val addresses = paired.map { it.address }
    SelectInput(address.takeIf { it in addresses } ?: "", listOf("") + addresses,
        { address -> if (address.isEmpty()) tr("Choose the terminal") else paired.first { it.address == address }.let { "${it.name} · ${it.address}" } },
        { v -> onAddress(v) }, Modifier.fillMaxWidth(), label = "Paired terminal", required = true)
}

/** The printer a cash drawer is plugged into: that printer opens it. */
@Composable
internal fun DrawerLinkFields(draft: DeviceDraft, state: DevicesState, model: DevicesScreenModel) {
    val printers = state.printers.filter { it.printerRole != "DOCUMENT" && it.status != "RETIRED" }
    Caption("Plugged into")
    if (printers.isEmpty()) {
        Text("Add a receipt printer first: the drawer plugs into it.", fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp,
            color = Kit.Amber)
        return
    }
    SelectInput(draft.linkedDeviceId.orEmpty(), listOf("") + printers.map { it.id },
        { id -> if (id.isEmpty()) tr("Choose a printer") else printers.first { it.id == id }.name },
        { v -> model.change { it.copy(linkedDeviceId = v.ifEmpty { null }) } }, Modifier.fillMaxWidth(), label = "Printer", required = true,
        hint = "The drawer's cable goes into the printer")
}

/**
 * Connecting a card terminal (user, 2026-11-28: "when you say connect, only choose one of the tills or tablets, not edit the
 * info"): the restaurant's tills and tablets to pick from, then Bluetooth or cable. Nothing else of the terminal changes.
 */
@Composable
internal fun TerminalConnectDialog(device: DeviceDto, state: DevicesState, model: DevicesScreenModel, onDone: () -> Unit) {
    val hosts = state.devices.filter { it.deviceType in setOf("POS_TERMINAL", "TERMINAL", "TABLET") && it.status != "RETIRED" }
        .sortedWith(compareBy({ it.deviceType == "TABLET" }, { it.name.lowercase() }))
    var host by remember(device.id) { mutableStateOf(device.linkedDeviceId) }
    var way by remember(device.id) { mutableStateOf(device.linkType?.takeIf { it == "USB" || it == "BLUETOOTH" } ?: "BLUETOOTH") }
    var address by remember(device.id) { mutableStateOf(device.linkAddress.orEmpty()) }
    val ready = host != null && (way == "USB" || address.isNotBlank())
    AppDialog("Connect ${device.name}", onDone, subtitle = "Card payments on the till or tablet you choose go to it.", maxWidth = 560.dp,
        busy = state.saving,
        buttons = {
            if (device.linkedDeviceId != null) KitButton("Disconnect", {
                model.connectTerminal(device, null, null, null, onDone)
            }, style = ButtonStyle.DANGER_OUTLINE, enabled = !state.saving)
            KitButton("Cancel", onDone, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton("Connect", { model.connectTerminal(device, host, way, address, onDone) }, icon = Icons.Outlined.Check,
                enabled = ready, loading = state.saving)
        }) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        if (hosts.isEmpty()) {
            Text("Add a till or tablet first: a card terminal works only through one.", fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp,
                letterSpacing = 0.sp, color = Kit.Amber)
            return@AppDialog
        }
        HostPicks(hosts, host) { host = it }
        if (host == null) return@AppDialog
        Caption("How")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("BLUETOOTH" to "Bluetooth", "USB" to "Cable").forEach { (code, label) ->
                val chosen = way == code
                Row(Modifier.clip(RoundedCornerShape(50)).background(if (chosen) Kit.GreenSoft else Color.White)
                    .border(1.dp, if (chosen) Kit.Green else Kit.Border, RoundedCornerShape(50))
                    .clickable { if (way != code) { way = code; address = "" } }
                    .padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (code == "BLUETOOTH") Icons.Outlined.Bluetooth else Icons.Outlined.Cable, null, Modifier.size(16.dp), tint = if (chosen) Kit.Green else Kit.Muted)
                    Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = if (chosen) Kit.Green else Kit.Ink)
                }
            }
        }
        if (way == "BLUETOOTH") PairedTerminalPicker(address) { address = it }
        else TextInput(address, { address = it.take(200) }, label = "Port", optional = true, placeholder = "COM3 or /dev/ttyUSB0",
            hint = "Plug the cable into the till or tablet above")
    }
}

/** The tills and tablets a card terminal can be connected to, each with its picture; a click picks one. */
@Composable
internal fun HostPicks(hosts: List<DeviceDto>, chosenId: String?, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        hosts.forEach { one ->
            val chosen = chosenId == one.id
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (chosen) Kit.GreenSoft else Color.White)
                .border(if (chosen) 1.5.dp else 1.dp, if (chosen) Kit.Green else Kit.RowBorder, RoundedCornerShape(12.dp))
                .clickable { onPick(one.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KindThumb(if (one.deviceType == "TABLET") DeviceKind.TABLET else DeviceKind.TILL, 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(one.name, translate = false, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink)
                    Text(deviceTypeLabel(one.deviceType), fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted)
                }
                Icon(if (chosen) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(20.dp),
                    tint = if (chosen) Kit.Green else Kit.Faint)
            }
        }
    }
}
