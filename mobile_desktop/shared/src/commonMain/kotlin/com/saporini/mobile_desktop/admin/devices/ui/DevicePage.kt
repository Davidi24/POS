@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.admin.devices.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.devices.DeviceDto
import com.saporini.mobile_desktop.admin.devices.DeviceExtras
import com.saporini.mobile_desktop.admin.devices.DevicesScreenModel
import com.saporini.mobile_desktop.admin.devices.DevicesState
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.LabeledValue
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.SettingsCard
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock

// One device on a page of its own (user, 2026-10-31: "when I go inside one device make it look better"): a hero with its
// picture, name, status and buttons, then white cards in the Settings look: its state, how it's reached, its pairing codes
// and who uses it (devices that sign in), and its details. Two columns when there's room, one on phones.

private val PhotoBand = Color(0xFF0B0C0B)

/** The states a device can be put in, with what each means. */
private val StatusChoices = listOf(
    "ACTIVE" to "Works normally",
    "MAINTENANCE" to "Kept, but not used for now",
    "INACTIVE" to "Can't be used until switched on",
    "BLOCKED" to "Lost or stolen: locked out",
    "RETIRED" to "No longer used"
)

@Composable
internal fun DevicePage(
    modifier: Modifier, device: DeviceDto, state: DevicesState, model: DevicesScreenModel, size: ScreenSize,
    onBack: () -> Unit, onConfirm: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit
) {
    val kind = kindOf(device)
    val wide = size.isDesktop
    ScrollColumn(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Breadcrumb(kind, device, onBack)
        DeviceHero(device, kind, state, size, onConfirm, onEdit, onRemove)
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { LeftCards(device, kind, state, model) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { RightCards(device, kind, state, model, onEdit) }
            }
        } else {
            LeftCards(device, kind, state, model)
            RightCards(device, kind, state, model, onEdit)
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** Back to the kind's page, then where you are: "Tablets & screens / Tablet 2". */
@Composable
private fun Breadcrumb(kind: DeviceKind, device: DeviceDto, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // One step back inside the page: a plain arrow (user rule).
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to ${kind.title}", Modifier.size(22.dp), tint = Kit.Ink)
        }
        Text(kind.title, Modifier.clickable(onClick = onBack).pointerHoverIcon(PointerIcon.Hand), fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Muted, maxLines = 1)
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = Kit.Faint)
        Text(device.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

/** The device's picture on a black band, its name, kind and code, status and connection, and its buttons. */
@Composable
private fun DeviceHero(
    device: DeviceDto, kind: DeviceKind, state: DevicesState, size: ScreenSize,
    onConfirm: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit
) {
    val extra = DeviceExtras.isExtra(device.id)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border),
        shadowElevation = 1.dp) {
        if (size.isPhone) Column {
            HeroPicture(kind, Modifier.fillMaxWidth().height(170.dp))
            HeroText(device, kind, state, extra, compact = true, onConfirm, onEdit, onRemove)
        } else Row(Modifier.height(IntrinsicSize.Min)) {
            HeroPicture(kind, Modifier.width(if (size.isDesktop) 300.dp else 230.dp).fillMaxHeight().heightIn(min = 190.dp))
            Box(Modifier.weight(1f)) { HeroText(device, kind, state, extra, compact = false, onConfirm, onEdit, onRemove) }
        }
    }
}

@Composable
private fun HeroPicture(kind: DeviceKind, modifier: Modifier) {
    // The pictures have black backgrounds, so the whole device fits on the black band.
    Box(modifier.background(PhotoBand)) {
        Image(painterResource(kind.image), kind.title, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun HeroText(
    device: DeviceDto, kind: DeviceKind, state: DevicesState, extra: Boolean, compact: Boolean,
    onConfirm: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit
) {
    val tone = deviceStatusColor(device.status)
    val now = Clock.System.now()
    Column(Modifier.padding(if (compact) 16.dp else 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(device.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (compact) 20.sp else 24.sp, color = Kit.Ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(deviceTypeLabel(device.deviceType), device.code,
            listOfNotNull(device.manufacturer, device.model).joinToString(" ").ifBlank { null }).joinToString(" · "),
            fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            StatusPill(deviceStatusLabel(device.status), tone)
            when {
                kind.pairs && device.online -> StatusPill("Connected", Kit.Green)
                kind.pairs -> StatusPill(device.lastSeenAt?.let { "Last seen ${agoText(it, now, RestaurantTime.zone)}" } ?: "Never connected", Kit.Grey)
                kind == DeviceKind.PRINTER -> StatusPill(connectionLabel(device.printerConnectionType), Kit.Blue)
                else -> Unit
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        if (state.canEdit) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (device.status == "PROVISIONING") KitButton("Confirm it works", onConfirm, icon = Icons.Outlined.Verified, enabled = !state.saving)
            if (!extra) KitButton("Edit", onEdit, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Edit, enabled = !state.saving)
            KitButton("Remove", onRemove, style = ButtonStyle.DANGER_OUTLINE, icon = Icons.Outlined.DeleteOutline, enabled = !state.saving)
        }
    }
}

@Composable
private fun LeftCards(device: DeviceDto, kind: DeviceKind, state: DevicesState, model: DevicesScreenModel) {
    StatusCard(device, kind, state, model)
    if (kind.pairs && !DeviceExtras.isExtra(device.id)) {
        SettingsCard("Pairing codes", note = "Type a code on the device to connect it. Each code works once.", icon = Icons.Outlined.QrCode2) {
            Spacer(Modifier.height(12.dp))
            PairingSection(state, model)
        }
    }
}

@Composable
private fun RightCards(device: DeviceDto, kind: DeviceKind, state: DevicesState, model: DevicesScreenModel, onEdit: () -> Unit) {
    ReachCard(device, kind)
    if (kind.pairs && !DeviceExtras.isExtra(device.id)) {
        SettingsCard("Who uses it", note = "The branch or the person the device is given to.", icon = Icons.Outlined.Groups) {
            Spacer(Modifier.height(12.dp))
            AssignmentSection(state, model)
        }
    }
    DetailsCard(device, state, onEdit)
}

/** The device's state, as choices to click: in use, in repair, switched off, blocked, retired. */
@Composable
private fun StatusCard(device: DeviceDto, kind: DeviceKind, state: DevicesState, model: DevicesScreenModel) {
    val extra = DeviceExtras.isExtra(device.id)
    SettingsCard("State", note = "Only devices in use can be used.", icon = Icons.Outlined.ToggleOn) {
        Spacer(Modifier.height(12.dp))
        if (device.status == "PROVISIONING") {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF8EC))
                .border(1.dp, Color(0xFFF1DDB5), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.HourglassTop, null, Modifier.size(18.dp), tint = Kit.Amber)
                Text(if (kind.pairs) "Not confirmed yet. Pair it with a code, then confirm it works." else "Not confirmed yet. Check it works, then confirm it.",
                    fontFamily = Inter(), fontSize = 12.sp, color = Kit.Ink)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChoices.forEach { (status, line) ->
                StatusChoice(status, line, chosen = device.status == status, enabled = state.canEdit && !state.saving) {
                    when {
                        extra -> DeviceExtras.setStatus(device.id, status)
                        kind == DeviceKind.PRINTER -> model.setPrinterStatus(device.id, status)
                        else -> model.setStatus(device.id, status)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChoice(status: String, line: String, chosen: Boolean, enabled: Boolean, onPick: () -> Unit) {
    val tone = deviceStatusColor(status)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(if (chosen) tone.copy(alpha = 0.08f) else Color.White)
        .border(1.dp, if (chosen) tone.copy(alpha = 0.55f) else Kit.RowBorder, RoundedCornerShape(10.dp))
        .then(if (enabled && !chosen) Modifier.clickable(onClick = onPick).pointerHoverIcon(PointerIcon.Hand) else Modifier)
        .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(18.dp).clip(CircleShape).border(2.dp, if (chosen) tone else Kit.Border, CircleShape), Alignment.Center) {
            if (chosen) Box(Modifier.size(9.dp).clip(CircleShape).background(tone))
        }
        Column(Modifier.weight(1f)) {
            Text(deviceStatusLabel(status), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
            Text(line, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** How the device is reached: connected or last seen (devices that sign in), the printer's connection, or plugged in. */
@Composable
private fun ReachCard(device: DeviceDto, kind: DeviceKind) {
    val now = Clock.System.now()
    val zone = RestaurantTime.zone
    SettingsCard("How it's reached", icon = Icons.Outlined.Wifi, iconTint = Kit.Blue) {
        Spacer(Modifier.height(12.dp))
        val facts: List<Pair<String, String>> = when {
            kind == DeviceKind.PRINTER -> listOf(
                "Connection" to connectionLabel(device.printerConnectionType),
                "Address" to listOfNotNull(device.printerIp, device.printerPort?.toString()).joinToString(":").ifBlank { "–" },
                "Paper" to "${device.paperWidthMm ?: "–"} mm",
                "Cuts the paper" to if (device.autoCut == true) "Yes" else "No",
                "Opens a cash drawer" to if (device.cashDrawerKickEnabled == true) "Yes" else "No")
            DeviceExtras.isExtra(device.id) -> listOf("Connection" to if (device.status == "ACTIVE") "Plugged in" else "Not checked yet")
            else -> listOf(
                "Last seen" to if (device.online) "Now" else device.lastSeenAt?.let { agoText(it, now, zone) } ?: "Never",
                "IP address" to (device.ipAddress ?: "–"),
                "App version" to (device.appVersion ?: "–"),
                "System" to listOfNotNull(device.platform, device.osVersion).joinToString(" ").ifBlank { "–" })
        }
        FactGrid(facts)
    }
}

@Composable
private fun DetailsCard(device: DeviceDto, state: DevicesState, onEdit: () -> Unit) {
    val extra = DeviceExtras.isExtra(device.id)
    SettingsCard("Details", icon = Icons.Outlined.Info, iconTint = Kit.Grey,
        trailing = if (state.canEdit && !extra) ({ KitButton("Edit", onEdit, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Edit, enabled = !state.saving) }) else null) {
        Spacer(Modifier.height(12.dp))
        FactGrid(listOfNotNull(
            "Code" to device.code,
            "Maker and model" to listOfNotNull(device.manufacturer, device.model).joinToString(" ").ifBlank { "–" },
            ("Serial" to (device.serialNumber ?: "–")).takeIf { !extra }))
        device.notes?.takeIf { it.isNotBlank() }?.let { LabeledValue("Notes", it, Modifier.padding(top = 12.dp)) }
    }
}

/** Facts two a row, each a soft tile. */
@Composable
private fun FactGrid(facts: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    LabeledValue(label, value, Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 9.dp))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
