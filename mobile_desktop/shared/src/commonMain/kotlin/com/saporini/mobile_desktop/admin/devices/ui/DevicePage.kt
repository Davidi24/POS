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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.session.TillHeartbeat
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_stock_items
import mobile_desktop.shared.generated.resources.shifts_needs_review
import mobile_desktop.shared.generated.resources.supplier_place

// One device on a page of its own (redone 2026-11-25, user: "the design of this page is terrible; use icons, make it
// professional"). A header with the picture, the name, how it is and its key facts in one strip (last seen, connected,
// branch, who signs in), then cards that all look alike: an icon tile in the card's colour, a title and one line, and their
// content: connecting it (this device; a code), its state as one row of choices, who can sign in, how it's reached and its
// details as icon lines. Two columns on a computer, one on phones.

private val PhotoBand = Color(0xFF0B0C0B)

/** How each state looks: its word, what it means, its icon and colour. */
internal data class StateLook(val label: String, val line: String, val icon: ImageVector, val color: Color)

internal fun stateLook(status: String?): StateLook = when (status) {
    "ACTIVE" -> StateLook("In use", "Works normally", Icons.Outlined.CheckCircle, Kit.Blue)
    "MAINTENANCE" -> StateLook("In repair", "Kept, but not used for now", Icons.Outlined.Build, Kit.Purple)
    "INACTIVE" -> StateLook("Switched off", "Can't be used until switched on", Icons.Outlined.PowerSettingsNew, Kit.Grey)
    "BLOCKED" -> StateLook("Blocked", "Lost or stolen: locked out", Icons.Outlined.Block, Kit.Danger)
    "RETIRED" -> StateLook("Retired", "No longer used", Icons.Outlined.Inventory2, Kit.Grey)
    else -> StateLook("To confirm", "Added, not confirmed yet", Icons.Outlined.HourglassTop, Kit.Amber)
}

private val StateOrder = listOf("ACTIVE", "MAINTENANCE", "INACTIVE", "BLOCKED", "RETIRED")

@Composable
internal fun DevicePage(
    modifier: Modifier, device: DeviceDto, state: DevicesState, model: DevicesScreenModel, size: ScreenSize,
    onBack: () -> Unit, onConfirm: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit
) {
    val kind = kindOf(device)
    val wide = size.isDesktop
    var moving by remember { mutableStateOf(false) }
    ScrollColumn(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DeviceHeader(device, kind, state, size, onConfirm, onEdit, onRemove, onMove = { moving = true })
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { LeftCards(device, kind, state, model) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { RightCards(device, kind, state, model, onEdit) }
            }
        } else {
            LeftCards(device, kind, state, model)
            RightCards(device, kind, state, model, onEdit)
        }
        Spacer(Modifier.height(12.dp))
    }
    if (moving) MoveBranchDialog(device, state, model) { moving = false }
}

// ---------- The header ----------

/**
 * The device at the top: its picture whole on the black band; its name ("This device" on the one you're on), kind, code and
 * maker; how it is in one pill; Confirm / Edit / ⋮; and its key facts in one strip.
 */
@Composable
private fun DeviceHeader(
    device: DeviceDto, kind: DeviceKind, state: DevicesState, size: ScreenSize,
    onConfirm: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit, onMove: () -> Unit
) {
    val extra = DeviceExtras.isExtra(device.id)
    val here = hereId() == device.id
    val heartbeat by TillHeartbeat.state.collectAsState()
    val link = if (kind.pairs && !extra) linkOf(device, here, heartbeat) else null
    val phone = size.isPhone
    val facts = headerFacts(device, kind, state, link, here, onMove)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White,
        border = BorderStroke(if (here) 1.5.dp else 1.dp, if (here) Kit.Ink.copy(alpha = 0.45f) else Kit.Border), shadowElevation = 2.dp) {
        val picture: @Composable (Modifier) -> Unit = { m ->
            Box(m.background(PhotoBand)) {
                Image(painterResource(kind.image), kind.title, Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
                if (link != null) LinkDot(link, 18.dp, Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
        }
        val body: @Composable (Modifier) -> Unit = { m ->
            Column(m.padding(horizontal = if (phone) 16.dp else 24.dp, vertical = if (phone) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                            itemVerticalAlignment = Alignment.CenterVertically) {
                            Text(device.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (phone) 21.sp else 25.sp,
                                letterSpacing = (-0.3).sp, color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (here) HereBadge()
                        }
                        Text(listOfNotNull(deviceTypeLabel(device.deviceType), device.code,
                            listOfNotNull(device.manufacturer, device.model).joinToString(" ").ifBlank { null }).joinToString("  ·  "),
                            fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.canEdit) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (device.status == "PROVISIONING" && (!kind.pairs || extra) && !phone)
                            KitButton("Confirm it works", onConfirm, icon = Icons.Outlined.Verified, enabled = !state.saving)
                        if (!extra && !phone) KitButton("Edit", onEdit, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Edit, enabled = !state.saving)
                        RowActionsMenu(listOfNotNull(
                            RowAction("Confirm it works", Icons.Outlined.Verified, onClick = onConfirm)
                                .takeIf { device.status == "PROVISIONING" && (!kind.pairs || extra) && phone },
                            RowAction("Edit", Icons.Outlined.Edit, onClick = onEdit).takeIf { !extra && phone },
                            RowAction("Remove", Icons.Outlined.DeleteOutline, danger = true, onClick = onRemove)
                        ), busy = state.saving)
                    }
                }
                // How it is, in one line.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (link != null) {
                        LinkPill(link, big = true)
                        Text(linkDetail(device, link, here), fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    } else {
                        StatePillBig(stateLook(device.status))
                        if (kind == DeviceKind.PRINTER) Text(connectionLabel(device.printerConnectionType), fontFamily = Inter(), fontSize = 13.sp,
                            letterSpacing = 0.sp, color = Kit.Muted)
                    }
                }
                FactStrip(facts, phone)
            }
        }
        if (phone) Column {
            picture(Modifier.fillMaxWidth().height(150.dp))
            body(Modifier.fillMaxWidth())
        } else Row(Modifier.height(IntrinsicSize.Min)) {
            picture(Modifier.width(if (size.isDesktop) 270.dp else 220.dp).fillMaxHeight().heightIn(min = 210.dp))
            body(Modifier.weight(1f))
        }
    }
}

/** A device's state as a pill with its icon, for devices that don't sign in. */
@Composable
private fun StatePillBig(look: StateLook) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(look.color.copy(alpha = 0.12f)).padding(start = 9.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(look.icon, null, Modifier.size(15.dp), tint = look.color)
        Text(look.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = look.color)
    }
}

/** One fact in the header's strip: an icon, a small label, the value, and maybe a link ("Change"). */
private class Fact(val icon: ImageVector, val label: String, val value: String, val valueColor: Color = Kit.Ink,
                   val action: String? = null, val onAction: (() -> Unit)? = null)

private fun headerFacts(device: DeviceDto, kind: DeviceKind, state: DevicesState, link: Link?, here: Boolean, onMove: () -> Unit): List<Fact> {
    val now = Clock.System.now()
    val zone = RestaurantTime.zone
    val branch = state.branchChoices.firstOrNull { it.id == state.branchId }
    // The branch only when the restaurant has several (user, 2026-11-25: "with one branch no need").
    val branchFact = if (state.branchChoices.size > 1 && kind != DeviceKind.PRINTER && !DeviceExtras.isExtra(device.id))
        Fact(Icons.Outlined.Storefront, "Branch", branch?.name ?: "This branch",
            action = if (state.canEdit) "Change" else null, onAction = if (state.canEdit) onMove else null) else null
    return when {
        link != null -> listOfNotNull(
            Fact(Icons.Outlined.Schedule, "Last seen", if (link == Link.ONLINE) "Now" else device.lastSeenAt?.let { agoText(it, now, zone) } ?: "Never",
                if (link == Link.ONLINE) OnlineGreen else Kit.Ink),
            Fact(Icons.Outlined.Link, "Connected", device.connectedAt?.let { agoText(it, now, zone) } ?: "Not yet",
                if (device.connected || here) Kit.Ink else Kit.Amber),
            branchFact,
            if (kind == DeviceKind.TILL || kind == DeviceKind.TABLET) Fact(Icons.Outlined.Groups, "Who signs in", state.signIn?.let { view ->
                when {
                    view.everyone -> "Everyone"
                    view.roles.isNotEmpty() && view.people.isEmpty() -> view.roles.joinToString(", ") { it.name }
                    else -> listOfNotNull(view.roles.size.takeIf { it > 0 }?.let { if (it == 1) "1 role" else "$it roles" },
                        view.people.size.takeIf { it > 0 }?.let { if (it == 1) "1 person" else "$it people" }).joinToString(" · ")
                }
            } ?: "–") else null
        )
        kind == DeviceKind.PRINTER -> listOf(
            Fact(Icons.Outlined.Cable, "Connection", connectionLabel(device.printerConnectionType)),
            Fact(Icons.Outlined.Lan, "Address", listOfNotNull(device.printerIp, device.printerPort?.toString()).joinToString(":").ifBlank { "–" }),
            Fact(Icons.Outlined.Receipt, "Paper", "${device.paperWidthMm ?: "–"} mm")
        )
        else -> listOfNotNull(Fact(Icons.Outlined.Cable, "Connection", if (device.status == "ACTIVE") "Plugged in" else "Not checked yet"), branchFact)
    }
}

/** The facts side by side on a soft strip, divided by thin lines; two a row on phones. */
@Composable
private fun FactStrip(facts: List<Fact>, phone: Boolean) {
    if (facts.isEmpty()) return
    val perRow = if (phone) 2 else facts.size
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).border(1.dp, Kit.RowBorder, RoundedCornerShape(14.dp))) {
        facts.chunked(perRow).forEachIndexed { r, row ->
            if (r > 0) HorizontalDivider(color = Kit.RowBorder)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { i, fact ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Kit.RowBorder))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(fact.icon, null, Modifier.size(14.dp), tint = Kit.Faint)
                            Text(fact.label.uppercase(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 0.6.sp,
                                color = Kit.Faint, maxLines = 1)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(fact.value, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.5.sp,
                                letterSpacing = 0.sp, color = fact.valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val act = fact.onAction
                            if (fact.action != null && act != null) Text(fact.action, Modifier.clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = act).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 4.dp, vertical = 1.dp),
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Blue)
                        }
                    }
                }
                repeat(perRow - row.size) { Box(Modifier.width(1.dp).fillMaxHeight().background(Kit.RowBorder)); Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------- The cards ----------

@Composable
private fun LeftCards(device: DeviceDto, kind: DeviceKind, state: DevicesState, model: DevicesScreenModel) {
    // The two ways to connect it, each its own card (user, 2026-11-25: "separate, not together"); a card terminal isn't the app.
    val extra = DeviceExtras.isExtra(device.id)
    if ((kind == DeviceKind.TILL || kind == DeviceKind.TABLET) && !extra) ThisDeviceCard(device, state, model)
    // A code for it: a till or tablet takes it from someone signed in on it, in Devices → Enter a code (user, 2026-11-25:
    // "the person who puts the code logs in and puts it in the Devices section"); a card terminal on its own pairing screen.
    if ((kind == DeviceKind.TILL || kind == DeviceKind.TABLET || kind == DeviceKind.CARD) && !extra) CodeCard(device, state, model)
    StateCard(device, kind, state, model)
}

@Composable
private fun RightCards(device: DeviceDto, kind: DeviceKind, state: DevicesState, model: DevicesScreenModel, onEdit: () -> Unit) {
    val extra = DeviceExtras.isExtra(device.id)
    // Who can sign in with their PIN (tills, tablets and screens).
    if ((kind == DeviceKind.TILL || kind == DeviceKind.TABLET) && !extra) SignInCard(device, state, model)
    ReachCard(device, kind)
    DetailsCard(device, state, onEdit)
}

/**
 * A card on a device's page: an icon tile in [tint], the title and one line, maybe something at the right ([trailing]),
 * then its content.
 */
@Composable
internal fun Panel(
    title: String, subtitle: String?, icon: ImageVector, tint: Color, modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    // One of the app's 3D pictures instead of the icon (user, 2026-11-25: "use the 3D icons I generated").
    picture: org.jetbrains.compose.resources.DrawableResource? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (picture != null) Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(Kit.Canvas)
                    .border(1.dp, Kit.RowBorder, RoundedCornerShape(13.dp)), Alignment.Center) {
                    Image(painterResource(picture), null, Modifier.fillMaxSize().padding(5.dp), contentScale = ContentScale.Fit)
                } else Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)), Alignment.Center) {
                    Icon(icon, null, Modifier.size(21.dp), tint = tint)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.5.sp, letterSpacing = (-0.1).sp, color = Kit.Ink)
                    subtitle?.let { Text(it, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp, color = Kit.Muted) }
                }
                trailing?.let { Spacer(Modifier.width(10.dp)); it() }
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** One line of facts in a card: an icon, the label, and the value at the right. */
internal class InfoLine(val icon: ImageVector, val label: String, val value: String, val valueColor: Color = Kit.Ink)

@Composable
internal fun InfoLines(lines: List<InfoLine>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(12.dp))) {
        lines.forEachIndexed { i, line ->
            if (i > 0) HorizontalDivider(color = Kit.RowBorder)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(line.icon, null, Modifier.size(17.dp), tint = Kit.Muted)
                Text(line.label, Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1)
                Text(line.value, Modifier.weight(1.4f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp,
                    color = line.valueColor, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The device's state as one row of choices (in use, in repair, switched off, blocked, retired), what the chosen one means under it. */
@Composable
private fun StateCard(device: DeviceDto, kind: DeviceKind, state: DevicesState, model: DevicesScreenModel) {
    val extra = DeviceExtras.isExtra(device.id)
    val current = stateLook(device.status)
    Panel("State", "Only devices in use can be used.", Icons.Outlined.ToggleOn, current.color,
        picture = Res.drawable.shifts_needs_review) {
        if (device.status == "PROVISIONING") {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF8EC))
                .border(1.dp, Color(0xFFF1DDB5), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.HourglassTop, null, Modifier.size(18.dp), tint = Kit.Amber)
                Text(if (kind.pairs) "Not confirmed yet. Connecting it puts it in use." else "Not confirmed yet. Check it works, then confirm it.",
                    fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Ink)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StateOrder.forEach { status ->
                val look = stateLook(status)
                val chosen = device.status == status
                val enabled = state.canEdit && !state.saving && !chosen
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(if (chosen) look.color.copy(alpha = 0.10f) else Color.White)
                    .border(if (chosen) 1.5.dp else 1.dp, if (chosen) look.color.copy(alpha = 0.6f) else Kit.Border, RoundedCornerShape(10.dp))
                    .then(if (enabled) Modifier.clickable {
                        when {
                            extra -> DeviceExtras.setStatus(device.id, status)
                            kind == DeviceKind.PRINTER -> model.setPrinterStatus(device.id, status)
                            else -> model.setStatus(device.id, status)
                        }
                    }.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(look.icon, null, Modifier.size(16.dp), tint = if (chosen) look.color else Kit.Faint)
                    Text(look.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp,
                        color = if (chosen) Kit.Ink else Kit.Muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(15.dp), tint = Kit.Faint)
            Text(current.line, fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
        }
    }
}

/** How the device is reached: when it was connected, last seen, its address and place, what it runs; a printer's connection. */
@Composable
private fun ReachCard(device: DeviceDto, kind: DeviceKind) {
    val now = Clock.System.now()
    val zone = RestaurantTime.zone
    val lines = when {
        kind == DeviceKind.PRINTER -> listOf(
            InfoLine(Icons.Outlined.Cable, "Connection", connectionLabel(device.printerConnectionType)),
            InfoLine(Icons.Outlined.Lan, "Address", listOfNotNull(device.printerIp, device.printerPort?.toString()).joinToString(":").ifBlank { "–" }),
            InfoLine(Icons.Outlined.Receipt, "Paper", "${device.paperWidthMm ?: "–"} mm"),
            InfoLine(Icons.Outlined.ContentCut, "Cuts the paper", if (device.autoCut == true) "Yes" else "No"),
            InfoLine(Icons.Outlined.PointOfSale, "Opens a cash drawer", if (device.cashDrawerKickEnabled == true) "Yes" else "No"))
        DeviceExtras.isExtra(device.id) -> listOf(InfoLine(Icons.Outlined.Cable, "Connection", if (device.status == "ACTIVE") "Plugged in" else "Not checked yet"))
        else -> listOfNotNull(
            InfoLine(Icons.Outlined.Link, "Connected", device.connectedAt?.let { dateTimeText(it, zone) } ?: "Not yet"),
            InfoLine(Icons.Outlined.Schedule, "Last seen", if (device.online) "Now" else device.lastSeenAt?.let { agoText(it, now, zone) } ?: "Never",
                if (device.online) OnlineGreen else Kit.Ink),
            InfoLine(Icons.Outlined.Lan, "Address", addressText(device.ipAddress, device.place)),
            InfoLine(Icons.Outlined.Computer, "System", systemText(device.platform, device.osVersion)),
            device.appVersion?.let { InfoLine(Icons.Outlined.Apps, "App version", it) })
    }
    Panel("How it's reached", if (kind == DeviceKind.PRINTER) "Where the tills send what it prints." else "What the server knows about it.",
        Icons.Outlined.Wifi, Kit.Blue, picture = Res.drawable.supplier_place) {
        InfoLines(lines)
    }
}

/** "This computer", "Local network · 192.168.1.40", "Tirana, Albania · 85.12.34.56". */
internal fun addressText(ip: String?, place: String?): String {
    val address = ip?.trim()?.takeIf { it.isNotBlank() } ?: return "–"
    val local = address == "::1" || address == "0:0:0:0:0:0:0:1" || address.startsWith("127.")
    val lan = address.startsWith("10.") || address.startsWith("192.168.") ||
        Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(address) || address.lowercase().startsWith("fe80")
    return when {
        local -> "This computer"
        lan -> "Local network · $address"
        place != null -> "$place · $address"
        else -> address
    }
}

/** "Linux 7.2", "Windows 11", "Android 14": the system without the build's long tail. */
internal fun systemText(platform: String?, version: String?): String {
    val name = platform?.trim().orEmpty()
    if (name.isBlank()) return "–"
    if (name.any { it.isDigit() }) return name
    val short = version?.trim()?.split('.', '-')?.filter { it.isNotBlank() }?.take(2)?.joinToString(".").orEmpty()
    return if (short.isBlank()) name else "$name $short"
}

/** Its code, kind, maker and model, serial and notes; Edit at the right for whoever manages devices. */
@Composable
private fun DetailsCard(device: DeviceDto, state: DevicesState, onEdit: () -> Unit) {
    val extra = DeviceExtras.isExtra(device.id)
    Panel("Details", "How it's written down here.", Icons.Outlined.Info, Kit.Grey,
        picture = Res.drawable.inventory_stock_items,
        trailing = if (state.canEdit && !extra) ({ KitButton("Edit", onEdit, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Edit, enabled = !state.saving) }) else null) {
        InfoLines(listOfNotNull(
            InfoLine(Icons.Outlined.Tag, "Code", device.code),
            InfoLine(Icons.Outlined.Category, "Kind", deviceTypeLabel(device.deviceType)),
            InfoLine(Icons.Outlined.Factory, "Maker and model", listOfNotNull(device.manufacturer, device.model).joinToString(" ").ifBlank { "–" }),
            InfoLine(Icons.Outlined.QrCode2, "Serial", device.serialNumber ?: "–").takeIf { !extra }
        ))
        device.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.StickyNote2, null, Modifier.size(17.dp), tint = Kit.Muted)
                Text(notes, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp, color = Kit.Ink, translate = false)
            }
        }
    }
}
