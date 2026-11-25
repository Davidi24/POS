@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.admin.devices.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import com.saporini.mobile_desktop.core.components.fadingEdges
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.devices.DeviceDto
import com.saporini.mobile_desktop.admin.devices.DevicesScreenModel
import com.saporini.mobile_desktop.admin.devices.DevicesState
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.i18n.tr
import com.saporini.mobile_desktop.core.session.TillHeartbeat
import com.saporini.mobile_desktop.core.session.TokenStore
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlin.time.Clock
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_pin_create
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.workspace_pos

// How the devices that sign in are doing, said plainly (user, 2026-11-25: "it should be clear: the current device, the other
// devices connected, online, what is not"). One state per device, one colour each, the same on the cards, the lists and the
// device's page; "This device" marks the one the app runs on.

/** A bright green for "online", apart from the app's sage green so it reads at a glance. */
internal val OnlineGreen = Color(0xFF16A34A)

/** Where a till, tablet or card terminal stands. */
internal enum class Link(val label: String, val color: Color, val icon: ImageVector) {
    ONLINE("Online", OnlineGreen, Icons.Outlined.Wifi),
    OFFLINE("Offline", Kit.Grey, Icons.Outlined.WifiOff),
    NOT_CONNECTED("Not connected yet", Kit.Amber, Icons.Outlined.LinkOff),
    NOT_IN_USE("Not in use", Kit.Purple, Icons.Outlined.DoNotDisturbOn)
}

/** The device the app runs on now, when it's a till or tablet ([TokenStore.till]). */
@Composable
internal fun hereId(): String? = TokenStore.till.collectAsState().value?.deviceId

internal fun linkOf(device: DeviceDto, here: Boolean, heartbeat: TillHeartbeat.State): Link = when {
    device.status != "ACTIVE" && device.status != "PROVISIONING" -> Link.NOT_IN_USE
    here && heartbeat == TillHeartbeat.State.OK -> Link.ONLINE
    !device.connected -> Link.NOT_CONNECTED
    device.online -> Link.ONLINE
    else -> Link.OFFLINE
}

/** The line under a state: "Seen 2 h ago", "In repair", "Make a code or use this device". */
internal fun linkDetail(device: DeviceDto, link: Link, here: Boolean): String {
    val now = Clock.System.now()
    val zone = RestaurantTime.zone
    return when (link) {
        Link.ONLINE -> if (here) "You're on it now" else "Now"
        Link.OFFLINE -> device.lastSeenAt?.let { "Last seen ${tr(agoText(it, now, zone))}" } ?: "Never seen since it was connected"
        Link.NOT_CONNECTED -> "Nobody has connected it yet"
        Link.NOT_IN_USE -> deviceStatusLabel(device.status)
    }
}

/** A state as a small dot on a device's picture (white ring), so a list reads at a glance. */
@Composable
internal fun LinkDot(link: Link, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Color.White).padding(2.dp).clip(CircleShape).background(link.color))
}

/** A state as a pill: dot and word ("● Online"). */
@Composable
internal fun LinkPill(link: Link, modifier: Modifier = Modifier, big: Boolean = false) {
    Row(modifier.clip(RoundedCornerShape(50)).background(link.color.copy(alpha = 0.12f))
        .padding(horizontal = if (big) 12.dp else 9.dp, vertical = if (big) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(if (big) 9.dp else 8.dp).clip(CircleShape).background(link.color))
        Text(link.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = if (big) 13.sp else 11.5.sp,
            letterSpacing = 0.sp, color = link.color.copy(alpha = 0.95f).let { if (link == Link.OFFLINE) Kit.Muted else it }, maxLines = 1)
    }
}

/** "This device", green, beside the name of the till or tablet the app runs on. */
@Composable
internal fun HereBadge(modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Kit.Ink).padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.Computer, null, Modifier.size(12.dp), tint = Color.White)
        Text("This device", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.sp, color = Color.White, maxLines = 1)
    }
}

/**
 * Up in Devices' header, only when the device you're on is a till or tablet (user, 2026-11-25: "it gives the idea a device
 * should be a till or a tablet; at most something up, 'this is a till', simple"): "This is Till 2", its dot, a click opens it.
 * When it stopped being one (connected elsewhere, switched off), "No longer Till 2" with × to forget it. Nothing otherwise.
 */
@Composable
internal fun HereChip(state: DevicesState, onOpen: (DeviceDto) -> Unit) {
    val till by TokenStore.till.collectAsState()
    val heartbeat by TillHeartbeat.state.collectAsState()
    val t = till ?: return
    val device = state.devices.firstOrNull { it.id == t.deviceId }
    val refused = heartbeat == TillHeartbeat.State.REFUSED
    val color = when {
        refused -> Kit.Amber
        heartbeat == TillHeartbeat.State.UNREACHABLE -> Kit.Muted
        else -> OnlineGreen
    }
    Row(Modifier.height(com.saporini.mobile_desktop.core.components.PageControlHeight).clip(RoundedCornerShape(50)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(50))
        .then(if (!refused && device != null) Modifier.clickable { onOpen(device) } else Modifier)
        .padding(start = 12.dp, end = if (refused) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Icon(Icons.Outlined.Computer, null, Modifier.size(15.dp), tint = Kit.Ink)
        Text(if (refused) "No longer ${t.name}" else "This is ${t.name}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
        if (refused) Box(Modifier.size(28.dp).clip(CircleShape).clickable { TokenStore.forgetTill() }, Alignment.Center) {
            Icon(Icons.Outlined.Close, "Forget it", Modifier.size(15.dp), tint = Kit.Muted)
        }
    }
}

/**
 * Devices → Enter a code (user, 2026-11-25: "the person who puts the code logs in and puts it in the Devices section, not in
 * the sign-in"): the code made on a till or tablet's page, typed on the device you're signed in on, which becomes that one.
 */
@Composable
internal fun EnterCodeDialog(state: DevicesState, model: DevicesScreenModel) {
    val till by TokenStore.till.collectAsState()
    var code by remember { mutableStateOf("") }
    val typed = code.filter { it.isLetterOrDigit() }
    AppDialog(title = "Enter a code", onDismiss = model::closeEnterCode, maxWidth = 460.dp, busy = state.saving,
        buttons = {
            KitButton("Cancel", model::closeEnterCode, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton("Connect", { model.enterCode(code) }, icon = Icons.Outlined.Login, enabled = typed.length >= 8, loading = state.saving)
        }) {
        Text("Type the code from the till or tablet's page. This device becomes that one.", fontFamily = Inter(), fontSize = 14.sp,
            lineHeight = 20.sp, letterSpacing = 0.sp, color = Kit.Ink)
        till?.let { Note("This device is ${it.name} now. With a code it becomes another one.", Kit.Amber) }
        com.saporini.mobile_desktop.core.components.TextInput(code, { code = it.uppercase().take(40) }, label = "Code",
            icon = Icons.Outlined.VpnKey, placeholder = "K7P4-9QXM")
        state.codeError?.let { com.saporini.mobile_desktop.core.components.MessageBar(it, com.saporini.mobile_desktop.core.components.MessageKind.ERROR) }
    }
}

/**
 * "This device" on a till or tablet's page (user, 2026-11-25: the two ways as separate cards; green only for online): make
 * the device you're on this till, or, when you're on it, disconnect it (asks first).
 */
@Composable
internal fun ThisDeviceCard(device: DeviceDto, state: DevicesState, model: DevicesScreenModel) {
    val till by TokenStore.till.collectAsState()
    val here = till?.deviceId == device.id
    val usable = device.status == "ACTIVE" || device.status == "PROVISIONING"
    var asking by remember { mutableStateOf(false) }
    var disconnecting by remember { mutableStateOf(false) }
    Panel("This device", if (here) "You're on ${device.name}." else "Make the device you're using now ${device.name}.",
        Icons.Outlined.Computer, Kit.Ink, picture = Res.drawable.workspace_pos) {
        when {
            here -> ActionRow(Icons.Outlined.Computer, Kit.Ink, "${device.name} is this computer",
                "Staff sign in here with their PIN.") {
                KitButton("Disconnect", { disconnecting = true }, style = ButtonStyle.DANGER_OUTLINE, icon = Icons.Outlined.LinkOff)
            }
            !usable -> Note("Put ${device.name} back in use to connect it.", Kit.Muted)
            !state.mayConnect(device) -> Note("Ask someone who connects devices to do it.", Kit.Muted)
            else -> {
                if (device.connected) { Note("It's connected on another device now. Using it here moves it, and the other one stops working.", Kit.Amber); Spacer(Modifier.height(10.dp)) }
                ActionRow(Icons.Outlined.PointOfSale, Kit.Ink, "Use it here", "Staff then sign in on this computer with their PIN.") {
                    KitButton("Use this device", { asking = true }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Login, loading = state.saving)
                }
            }
        }
    }
    if (asking) {
        AppDialog(title = "Use this device as ${device.name}?", onDismiss = { asking = false }, maxWidth = 460.dp,
            buttons = {
                KitButton("Cancel", { asking = false }, style = ButtonStyle.SECONDARY)
                KitButton("Connect", { asking = false; model.connectThisDevice() })
            }) {
            Text(if (device.connected) "Staff will sign in here with their PIN. The device that is ${device.name} now stops being it."
                else "Staff will sign in here with their PIN.", fontFamily = Inter(), fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink)
        }
    }
    if (disconnecting) {
        AppDialog(title = "Disconnect this device?", onDismiss = { disconnecting = false }, maxWidth = 460.dp,
            buttons = {
                KitButton("Cancel", { disconnecting = false }, style = ButtonStyle.SECONDARY)
                KitButton("Disconnect", { disconnecting = false; model.disconnectThisDevice() }, style = ButtonStyle.DANGER)
            }) {
            Text("It stops being ${device.name}. Staff can't sign in here with their PIN until it's connected again.",
                fontFamily = Inter(), fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink)
        }
    }
}

/**
 * "Connect with a code": make a code for this device. A till or tablet takes it from someone signed in on it, in Devices →
 * Enter a code, so it's known who set it up (user, 2026-11-25); a card terminal on its own pairing screen. One code works at
 * a time: a new one ends the one before, so there's no list of old codes (user, 2026-11-25: "should that delete the other?").
 */
@Composable
internal fun CodeCard(device: DeviceDto, state: DevicesState, model: DevicesScreenModel) {
    val usable = device.status == "ACTIVE" || device.status == "PROVISIONING"
    val code = state.freshCode?.takeIf { it.deviceId == device.id && it.pairingToken != null }
    val card = kindOf(device) == DeviceKind.CARD
    Panel("Connect with a code",
        if (card) "Type the code on the card terminal to connect it."
        else "For another device: sign in on it, open Devices and press “Enter a code”.",
        Icons.Outlined.VpnKey, Kit.Blue, picture = Res.drawable.auth_pin_create) {
        when {
            !usable -> Note("Put ${device.name} back in use to connect it.", Kit.Muted)
            !state.mayConnect(device) -> Note("Ask someone who connects devices to do it.", Kit.Muted)
            else -> {
                if (code != null) { FreshCode(code.pairingToken!!, code.expiresAt); Spacer(Modifier.height(12.dp)) }
                CodeButton(state, model, again = code != null)
            }
        }
    }
}

/** A short line in a soft tile, in [color]. */
@Composable
private fun Note(text: String, color: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = color)
        Text(text, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp, color = if (color == Kit.Muted) Kit.Muted else Kit.Ink)
    }
}

/**
 * Who can sign in on a till or tablet (user, 2026-11-25): the card only says it (everyone at the branch, or the roles and
 * people chosen); Edit, like the Details card's, opens its own form (user: "it opens another form, not expand").
 */
@Composable
internal fun SignInCard(device: DeviceDto, state: DevicesState, model: DevicesScreenModel) {
    val view = state.signIn
    var editing by remember { mutableStateOf(false) }
    Panel("Who can sign in here", view?.alwaysAllowed?.takeIf { it.isNotEmpty() }
        ?.let { "${it.joinToString(", ") { name -> tr(name) }} always can." } ?: "Who can use ${device.name} with their PIN.",
        Icons.Outlined.Groups, Kit.Purple, picture = Res.drawable.overview_guests,
        trailing = if (state.canEdit && view != null) ({
            KitButton("Edit", { editing = true }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Edit, enabled = !state.saving)
        }) else null) {
        when {
            view == null -> Text("Loading…", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
            view.everyone -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Public, null, Modifier.size(18.dp), tint = Kit.Muted)
                Column(Modifier.weight(1f)) {
                    Text("Everyone at the branch", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp, color = Kit.Ink)
                    Text("Anyone who works at the branch signs in here with their PIN.", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp,
                        color = Kit.Muted)
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (view.roles.isNotEmpty()) {
                    Text("ROLES", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = Kit.Faint)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        view.roles.forEach { role -> RoleChip(role.name, on = true, onClick = null) }
                    }
                }
                if (view.people.isNotEmpty()) {
                    Text("PEOPLE", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = Kit.Faint)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        view.people.forEach { person -> PersonChip(person, onRemove = null) }
                    }
                }
            }
        }
    }
    if (editing && view != null) SignInDialog(device, view, state, model) { editing = false }
}

/**
 * The form for who can sign in (its own window, with the people picture on top): everyone at the branch or only some; the
 * roles to tick and the people added by name. Save once something changed.
 */
@Composable
private fun SignInDialog(device: DeviceDto, view: com.saporini.mobile_desktop.admin.devices.DeviceSignInView, state: DevicesState,
                         model: DevicesScreenModel, onClose: () -> Unit) {
    var some by remember { mutableStateOf(!view.everyone) }
    var roles by remember { mutableStateOf(view.roles.map { it.id }.toSet()) }
    var people by remember { mutableStateOf(view.people) }
    val changed = some != !view.everyone || (some && (roles != view.roles.map { it.id }.toSet() || people.map { it.id }.toSet() != view.people.map { it.id }.toSet()))
    val empty = some && roles.isEmpty() && people.isEmpty()
    AppDialog("Who can sign in on ${device.name}", onClose, maxWidth = 600.dp, busy = state.saving,
        buttons = {
            KitButton("Cancel", onClose, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton("Save", {
                if (some) model.saveSignIn(roles.toList(), people.map { it.id }) else model.saveSignIn(emptyList(), emptyList())
                onClose()
            }, icon = Icons.Outlined.Check, enabled = changed && !empty, loading = state.saving)
        }) {
        // The people picture and who can always sign in.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).border(1.dp, Kit.RowBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.foundation.Image(org.jetbrains.compose.resources.painterResource(Res.drawable.overview_guests), null, Modifier.size(64.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Who uses ${device.name} with their PIN", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp,
                    color = Kit.Ink)
                Text(view.alwaysAllowed.takeIf { it.isNotEmpty() }?.let { "${it.joinToString(", ") { name -> tr(name) }} always can." }
                    ?: "Owners and managers always can.", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        Segmented(listOf(Icons.Outlined.Public to "Everyone at the branch", Icons.Outlined.Tune to "Only some"), if (some) 1 else 0, true) { some = it == 1 }
        Spacer(Modifier.height(10.dp))
        Text(if (some) "Only the roles and people below sign in here with their PIN." else "Anyone who works at the branch signs in here with their PIN.",
            fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
        if (some) {
            Spacer(Modifier.height(16.dp))
            RolePeoplePicker(view, roles, people, onRoles = { roles = it }, onPeople = { people = it })
            if (empty) { Spacer(Modifier.height(12.dp)); Note("Choose at least one role or person, or pick Everyone.", Kit.Amber) }
        }
    }
}

/**
 * "Only some" (user, 2026-11-25: "an icon for each role, a nav bar; by the role I choose, list all its people ready to pick;
 * admins or down, you know the rule"): the roles the person editing sees as a bar of pictures, then the chosen role's people
 * with a tick each, and "Everyone who is a …" on top to take the whole role. Owners, admins and managers show as "Always".
 */
@Composable
private fun RolePeoplePicker(view: com.saporini.mobile_desktop.admin.devices.DeviceSignInView, roles: Set<String>,
                             people: List<com.saporini.mobile_desktop.admin.devices.SignInPerson>,
                             onRoles: (Set<String>) -> Unit, onPeople: (List<com.saporini.mobile_desktop.admin.devices.SignInPerson>) -> Unit) {
    // A role that always signs in, with nobody in it you see (yourself, say), would only say "nobody".
    val tabs = view.tabs.filter { !it.always || it.people.isNotEmpty() }
    // Opens on the first role that can be chosen (the always ones lead the bar, as they rank highest).
    var open by remember { mutableStateOf(tabs.firstOrNull { !it.always }?.id ?: tabs.firstOrNull()?.id) }
    val picked = people.map { it.id }.toSet()
    val scroll = androidx.compose.foundation.rememberScrollState()
    Row(Modifier.fillMaxWidth().fadingEdges(scroll).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEach { tab ->
            val look = com.saporini.mobile_desktop.admin.people.ui.roleLookOf(tab.code, tab.name, tab.picture)
            val chosen = tab.id == open
            val count = when {
                tab.always -> null
                tab.id in roles -> "All"
                else -> tab.people.count { it.id in picked }.takeIf { it > 0 }?.toString()
            }
            Column(Modifier.width(104.dp).clip(RoundedCornerShape(14.dp)).background(if (chosen) look.color.copy(alpha = 0.10f) else Color.White)
                .border(if (chosen) 1.5.dp else 1.dp, if (chosen) look.color.copy(alpha = 0.6f) else Kit.RowBorder, RoundedCornerShape(14.dp))
                .clickable { open = tab.id }.padding(top = 10.dp, bottom = 8.dp, start = 6.dp, end = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    androidx.compose.foundation.Image(org.jetbrains.compose.resources.painterResource(look.image), null, Modifier.size(44.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    if (count != null) Box(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp).height(18.dp).widthIn(min = 18.dp)
                        .clip(RoundedCornerShape(50)).background(look.color).padding(horizontal = 5.dp), Alignment.Center) {
                        Text(count, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.sp, color = Color.White, maxLines = 1)
                    }
                }
                Text(tab.name, fontFamily = Inter(), fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.sp,
                    color = if (chosen) Kit.Ink else Kit.Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(if (tab.always) "Always" else if (tab.people.size == 1) "1 person" else "${tab.people.size} people", fontFamily = Inter(),
                    fontSize = 11.sp, letterSpacing = 0.sp, color = Kit.Faint, maxLines = 1)
            }
        }
    }
    val tab = tabs.firstOrNull { it.id == open } ?: return
    val look = com.saporini.mobile_desktop.admin.people.ui.roleLookOf(tab.code, tab.name, tab.picture)
    val whole = tab.id in roles
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(14.dp))) {
        // The whole role: everyone who has it, also people given it later.
        if (tab.always) Row(Modifier.fillMaxWidth().background(Kit.Canvas).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(18.dp), tint = look.color)
            Text("Every ${tab.name} signs in here anyway.", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp,
                color = Kit.Ink)
        } else Row(Modifier.fillMaxWidth().background(if (whole) look.color.copy(alpha = 0.08f) else Kit.Canvas)
            .clickable { onRoles(if (whole) roles - tab.id else roles + tab.id) }.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Everyone who is a ${tab.name}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp, color = Kit.Ink)
                Text("Also whoever gets this role later.", fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted)
            }
            Tick(whole, look.color)
        }
        if (tab.people.isEmpty()) Text("Nobody has this role yet.", Modifier.padding(horizontal = 14.dp, vertical = 14.dp), fontFamily = Inter(),
            fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
        tab.people.forEach { person ->
            val on = tab.always || whole || person.id in picked
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))
            Row(Modifier.fillMaxWidth()
                .then(if (!tab.always && !whole) Modifier.clickable { onPeople(if (person.id in picked) people.filterNot { it.id == person.id } else people + person) } else Modifier)
                .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.saporini.mobile_desktop.pos.shifts.ShiftAvatar(person.name, 30.dp)
                Text(person.name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp,
                    color = Kit.Ink, maxLines = 1, translate = false)
                when {
                    tab.always -> Text("Always", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted)
                    whole -> Text("With the role", fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted)
                }
                if (!tab.always) Tick(on, look.color, faded = whole)
            }
        }
    }
    // People chosen before who aren't in a role shown here (given by someone higher up, say): they stay, with × to take off.
    val listed = tabs.flatMap { t -> t.people.map { it.id } }.toSet()
    val others = people.filterNot { it.id in listed }
    if (others.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("ALSO CHOSEN", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = Kit.Faint)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            others.forEach { person -> PersonChip(person) { onPeople(people.filterNot { it.id == person.id }) } }
        }
    }
}

/** A square tick in the role's colour. */
@Composable
private fun Tick(on: Boolean, color: Color, faded: Boolean = false) {
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (on) color.copy(alpha = if (faded) 0.45f else 1f) else Color.White)
        .border(1.5.dp, if (on) Color.Transparent else Kit.Border, RoundedCornerShape(6.dp)), Alignment.Center) {
        if (on) Icon(Icons.Outlined.Check, null, Modifier.size(15.dp), tint = Color.White)
    }
}

/** A role as a chip: ticked or not ([onClick] null = only shown). */
@Composable
private fun RoleChip(name: String, on: Boolean, onClick: (() -> Unit)?) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) Kit.Tint else Color.White)
        .border(1.dp, if (on) Kit.Ink.copy(alpha = 0.45f) else Kit.Border, RoundedCornerShape(50))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (on) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(16.dp), tint = if (on) Kit.Ink else Kit.Faint)
        Text(name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.sp, color = if (on) Kit.Ink else Kit.Muted,
            maxLines = 1)
    }
}

/** A person as a chip with their face; × takes them off ([onRemove] null = only shown). */
@Composable
private fun PersonChip(person: com.saporini.mobile_desktop.admin.devices.SignInPerson, onRemove: (() -> Unit)?) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Kit.Canvas).border(1.dp, Kit.RowBorder, RoundedCornerShape(50))
        .padding(start = 4.dp, end = if (onRemove != null) 4.dp else 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        com.saporini.mobile_desktop.pos.shifts.ShiftAvatar(person.name, 24.dp)
        Text(person.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Ink,
            maxLines = 1, translate = false)
        if (onRemove != null) Box(Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onRemove), Alignment.Center) {
            Icon(Icons.Outlined.Close, "Remove", Modifier.size(14.dp), tint = Kit.Muted)
        }
    }
}

/** A choice with a round mark, a title and a line. */
@Composable
private fun WhoChoice(title: String, line: String, chosen: Boolean, enabled: Boolean, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (chosen) Kit.Tint else Color.White)
        .border(1.dp, if (chosen) Kit.Ink.copy(alpha = 0.45f) else Kit.RowBorder, RoundedCornerShape(12.dp))
        .then(if (enabled && !chosen) Modifier.clickable(onClick = onPick) else Modifier)
        .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(18.dp).clip(CircleShape).border(2.dp, if (chosen) Kit.Ink else Kit.Border, CircleShape), Alignment.Center) {
            if (chosen) Box(Modifier.size(9.dp).clip(CircleShape).background(Kit.Ink))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp, color = Kit.Ink)
            Text(line, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted)
        }
    }
}

/** Moves a device to another branch (the header's Branch → Change; only with several branches). */
@Composable
internal fun MoveBranchDialog(device: DeviceDto, state: DevicesState, model: DevicesScreenModel, onClose: () -> Unit) {
    var pick by remember { mutableStateOf(state.branchId) }
    AppDialog("Which branch does ${device.name} work in?", onClose, maxWidth = 480.dp,
        buttons = {
            KitButton("Cancel", onClose, style = ButtonStyle.SECONDARY)
            KitButton("Move it", { onClose(); pick?.let(model::moveTo) }, icon = Icons.Outlined.SwapHoriz, enabled = pick != state.branchId)
        }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.branchChoices.forEach { branch ->
                WhoChoice(branch.name, if (branch.id == state.branchId) "Where it works now" else branch.code, pick == branch.id, true) { pick = branch.id }
            }
        }
    }
}

/** A line with an icon tile, two lines of words and a button at the right (one under the other on a narrow card). */
@Composable
private fun ActionRow(icon: ImageVector, tint: Color, title: String, line: String, action: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 420.dp
        val words: @Composable (Modifier) -> Unit = { m ->
            Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), Alignment.Center) {
                    Icon(icon, null, Modifier.size(19.dp), tint = tint)
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink)
                    Text(line, fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
                }
            }
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.05f))
            .border(1.dp, tint.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(12.dp)) {
            if (narrow) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { words(Modifier); action() }
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                words(Modifier.weight(1f))
                action()
            }
        }
    }
}

/** A switch of two or more parts, each an icon and a word; the chosen one white and raised. */
@Composable
private fun Segmented(parts: List<Pair<ImageVector, String>>, chosen: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Tint).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEachIndexed { i, (icon, label) ->
            val on = i == chosen
            Row(Modifier.weight(1f).then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(9.dp)) else Modifier).clip(RoundedCornerShape(9.dp))
                .background(if (on) Color.White else Color.Transparent)
                .then(if (enabled && !on) Modifier.clickable { onPick(i) } else Modifier).padding(vertical = 9.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp), tint = if (on) Kit.Ink else Kit.Muted)
                Text(label, fontFamily = Inter(), fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp,
                    color = if (on) Kit.Ink else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A code just made, big, to read out or copy. */
@Composable
private fun FreshCode(code: String, expiresAt: String?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectionContainer {
            Text(code, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas)
                .border(1.5.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(vertical = 14.dp, horizontal = 10.dp),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 3.sp, color = Kit.Ink,
                textAlign = TextAlign.Center, translate = false)
        }
        Text("Works once, until ${dateTimeText(expiresAt, RestaurantTime.zone)}. A new code ends this one.", fontFamily = Inter(), fontSize = 11.5.sp,
            letterSpacing = 0.sp, color = Kit.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** Make a code, and beside it how long it lasts, as a small menu. */
@Composable
private fun CodeButton(state: DevicesState, model: DevicesScreenModel, again: Boolean) {
    var minutes by remember { mutableStateOf(15) }
    var open by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        KitButton(if (again) "Make a new code" else "Make a code", { model.newPairingCode(minutes) }, icon = Icons.Outlined.VpnKey,
            style = ButtonStyle.SECONDARY, loading = state.saving)
        Box {
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(15.dp), tint = Kit.Muted)
                Text("Lasts ${tr(pairingDuration(minutes))}", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Muted)
            }
            androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = Color.White) {
                listOf(15, 60, 240, 1440).forEach { choice ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(pairingDuration(choice), fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp,
                            fontWeight = if (choice == minutes) FontWeight.Bold else FontWeight.Normal, color = Kit.Ink) },
                        onClick = { minutes = choice; open = false })
                }
            }
        }
    }
}
