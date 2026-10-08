package com.saporini.mobile_desktop.admin.devices.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
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
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToggleRow
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime

/** Adding or editing a device (or a printer, which has its connection and paper settings). */
@Composable
internal fun DeviceEditorDialog(draft: DeviceDraft, state: DevicesState, model: DevicesScreenModel) {
    val kind = if (draft.printer) "printer" else "device"
    AppDialog(
        if (draft.id == null) "Add a $kind" else "Edit ${draft.name.ifBlank { kind }}",
        model::cancelEdit,
        subtitle = if (draft.printer) "Printers belong to the whole restaurant; routes choose what each one prints."
        else "After saving, open the device and make a pairing code to connect it.",
        busy = state.saving,
        buttons = {
            KitButton("Cancel", model::cancelEdit, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton(if (draft.id == null) "Add $kind" else "Save", model::save, icon = Icons.Outlined.Check, loading = state.saving)
        }
    ) {
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
            FieldPair(
                { m -> SelectInput(draft.deviceType, DEVICE_TYPES, ::deviceTypeLabel, { v -> model.change { it.copy(deviceType = v) } }, m, label = "Kind", required = true) },
                { m -> SelectInput(draft.status, DEVICE_STATUSES, ::deviceStatusLabel, { v -> model.change { it.copy(status = v) } }, m, label = "Status") }
            )
        } else {
            Caption("Connection")
            FieldPair(
                { m -> SelectInput(draft.connection, PRINTER_CONNECTIONS, ::connectionLabel, { v -> model.change { it.copy(connection = v) } }, m, label = "Connected by") },
                { m -> TextInput(draft.paperWidthText, { v -> model.change { it.copy(paperWidthText = v.filter(Char::isDigit).take(3)) } }, m,
                    label = "Paper width", suffix = "mm", keyboardType = KeyboardType.Number, hint = "Usually 58 or 80") }
            )
            if (draft.connection == "NETWORK") {
                FieldPair(
                    { m -> TextInput(draft.printerIp, { v -> model.change { it.copy(printerIp = v.trim()) } }, m, label = "IP address", required = true,
                        icon = Icons.Outlined.Lan, placeholder = "192.168.1.50", maxLength = 45) },
                    { m -> TextInput(draft.printerPortText, { v -> model.change { it.copy(printerPortText = v.filter(Char::isDigit).take(5)) } }, m,
                        label = "Port", optional = true, icon = Icons.Outlined.Numbers, placeholder = "9100", keyboardType = KeyboardType.Number) }
                )
            }
            ToggleRow("Cut the paper after each print", draft.autoCut, { v -> model.change { it.copy(autoCut = v) } })
            ToggleRow("Open the cash drawer", draft.cashDrawerKick, { v -> model.change { it.copy(cashDrawerKick = v) } },
                detail = "For receipt printers with a cash drawer plugged in")
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
        Text("Type this on the device when it asks for a pairing code. It works once and can't be shown again.",
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
