package com.saporini.mobile_desktop.admin.devices.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.saporini.mobile_desktop.admin.devices.DeviceDto
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.format.humanize

// Names, icons and colours for device kinds and states, shared by the list, the details and the editor.

internal fun deviceTypeLabel(type: String?): String = when (type) {
    "TERMINAL" -> "Till"
    "TABLET" -> "Tablet"
    "KDS" -> "Kitchen screen"
    "CUSTOMER_DISPLAY" -> "Customer display"
    "PAYMENT_TERMINAL" -> "Card terminal"
    "PRINTER" -> "Printer"
    else -> humanize(type)
}

internal fun deviceTypeIcon(type: String?): ImageVector = when (type) {
    "TERMINAL" -> Icons.Outlined.PointOfSale
    "TABLET" -> Icons.Outlined.TabletAndroid
    "KDS" -> Icons.Outlined.Restaurant
    "CUSTOMER_DISPLAY" -> Icons.Outlined.DisplaySettings
    "PAYMENT_TERMINAL" -> Icons.Outlined.Contactless
    "PRINTER" -> Icons.Outlined.Print
    else -> Icons.Outlined.Devices
}

internal fun deviceStatusLabel(status: String?): String = when (status) {
    "PROVISIONING" -> "Setting up"
    "ACTIVE" -> "In use"
    "INACTIVE" -> "Switched off"
    "BLOCKED" -> "Blocked"
    "MAINTENANCE" -> "In repair"
    "RETIRED" -> "Retired"
    else -> humanize(status)
}

internal fun deviceStatusColor(status: String?): Color = when (status) {
    "ACTIVE" -> Kit.Green
    "PROVISIONING" -> Kit.Blue
    "MAINTENANCE" -> Kit.Amber
    "BLOCKED" -> Kit.Danger
    else -> Kit.Grey
}

/** Something that needs a look: blocked, in repair, or a device in use that isn't connected. */
internal fun DeviceDto.needsAttention(printer: Boolean): Boolean =
    status == "BLOCKED" || status == "MAINTENANCE" || (!printer && status == "ACTIVE" && !online)

internal fun connectionLabel(connection: String?): String = when (connection) {
    "NETWORK" -> "Network (IP)"
    "USB" -> "USB cable"
    "BLUETOOTH" -> "Bluetooth"
    "SERIAL" -> "Serial cable"
    else -> humanize(connection)
}

internal fun pairingStateLabel(state: String?, active: Boolean): Pair<String, Color> = when {
    active -> "Waiting to be used" to Kit.Green
    state == "USED" || state == "REDEEMED" -> "Used" to Kit.Blue
    state == "REVOKED" -> "Cancelled" to Kit.Grey
    state == "EXPIRED" -> "Expired" to Kit.Grey
    else -> humanize(state).ifBlank { "Not usable" } to Kit.Grey
}
