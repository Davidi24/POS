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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.animation.core.animateFloat
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
    // Set up = "In use", and whether it's on now (user, 2026-11-25: "it should say in use or configured"); switched-off ones
    // read "Switched off", so "In use" never sits beside "Not in use".
    ONLINE("In use · Online", OnlineGreen, Icons.Outlined.Wifi),
    OFFLINE("In use · Offline", Kit.Grey, Icons.Outlined.WifiOff),
    NOT_CONNECTED("Not connected yet", Kit.Amber, Icons.Outlined.LinkOff),
    NOT_IN_USE("Switched off", Kit.Purple, Icons.Outlined.DoNotDisturbOn)
}

/**
 * Whether a printer answers (user, 2026-11-27): tills with the app open check every minute. Online = one reached it lately;
 * Offline = one checked and nobody reached it; Not checked = no till checked it lately (none open, or none connected).
 */
internal enum class Reach(val label: String, val color: Color, val icon: ImageVector) {
    ONLINE("Online", OnlineGreen, Icons.Outlined.Wifi),
    OFFLINE("Offline", Kit.Danger, Icons.Outlined.WifiOff),
    UNCHECKED("Not checked", Kit.Grey, Icons.Outlined.Schedule)
}

/** A printer in use, as it answers; null for one switched off or in repair (its state says it). */
internal fun reachOf(device: DeviceDto): Reach? =
    if (device.status != "ACTIVE" && device.status != "PROVISIONING") null
    else when (device.printerState) {
        "ONLINE" -> Reach.ONLINE
        "OFFLINE" -> Reach.OFFLINE
        else -> Reach.UNCHECKED
    }

/** The line under a printer's state: "It answers · Bar till checks it", "No answer at 192.168.1.52 · checked by Bar till". */
internal fun reachDetail(device: DeviceDto, reach: Reach): String {
    val by = device.printerCheckedBy
    return when (reach) {
        Reach.ONLINE -> if (by != null) "It answers · $by checks it" else "It answers"
        Reach.OFFLINE -> (device.printerProblem ?: "It doesn't answer") + (by?.let { " · checked by $it" } ?: "")
        Reach.UNCHECKED -> device.printerCheckedAt?.let { "Not checked since ${tr(agoText(it, Clock.System.now(), RestaurantTime.zone))}" }
            ?: "No till has checked it yet"
    }
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
 * the sign-in"): the code made on a till or tablet's page, typed in eight boxes with the dash already there (user: "eight
 * squares and the line in the middle, I don't have to write it"). Then it waits for the person who made the code to agree
 * (user: "it goes to the person who generated the code"); the till doesn't lock meanwhile (3 minutes, like other important work).
 */
@Composable
internal fun EnterCodeDialog(state: DevicesState, model: DevicesScreenModel) {
    com.saporini.mobile_desktop.core.session.IdleTime(com.saporini.mobile_desktop.core.session.IdleLevel.IMPORTANT)
    val till by TokenStore.till.collectAsState()
    var code by remember { mutableStateOf("") }
    val wait = state.connectWait
    val title = when {
        wait == null -> if (state.codeKind == "TABLET") "Enter a tablet's or screen's code" else "Enter a till's code"
        wait.ended != null -> "It didn't connect"
        else -> "Waiting for an answer"
    }
    AppDialog(title = title, onDismiss = model::closeEnterCode, maxWidth = 480.dp, busy = state.saving,
        buttons = {
            when {
                wait == null -> {
                    KitButton("Cancel", model::closeEnterCode, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                    KitButton("Connect", { model.enterCode(code) }, icon = Icons.Outlined.Login, enabled = code.length == CodeLength, loading = state.saving)
                }
                wait.ended != null -> {
                    KitButton("Close", model::closeEnterCode, style = ButtonStyle.SECONDARY)
                    KitButton("Try again", { code = ""; model.cancelWait() }, icon = Icons.Outlined.Refresh)
                }
                else -> KitButton("Cancel", model::closeEnterCode, style = ButtonStyle.SECONDARY)
            }
        }) {
        if (wait == null) {
            Text(if (state.codeKind == "TABLET") "Type the code from the tablet's or screen's page. This device becomes that one."
                else "Type the code from the till's page. This device becomes that till.", fontFamily = Inter(), fontSize = 14.sp,
                lineHeight = 20.sp, letterSpacing = 0.sp, color = Kit.Ink)
            till?.let { Note("This device is ${it.name} now. With a code it becomes another one.", Kit.Amber) }
            Spacer(Modifier.height(4.dp))
            CodeBoxes(code, enabled = !state.saving, error = state.codeError != null,
                onChange = { typed ->
                    val full = typed.length == CodeLength && code.length < CodeLength
                    code = typed
                    if (state.codeError != null) model.clearCodeError()
                    if (full) model.enterCode(typed)
                },
                onDone = { if (code.length == CodeLength) model.enterCode(code) })
            state.codeError?.let { com.saporini.mobile_desktop.core.components.MessageBar(it, com.saporini.mobile_desktop.core.components.MessageKind.ERROR) }
        } else {
            WaitingForMaker(wait)
        }
    }
}

/** A pairing code's length: "K7P4-9QXM" without its dash. */
private const val CodeLength = 8

/** Letters and digits only, in capitals, at most eight: a pasted "k7p4-9qxm" comes in whole. */
private fun cleanCode(text: String): String = text.filter { it.isLetterOrDigit() && it.code < 128 }.uppercase().take(CodeLength)

/**
 * The code in eight boxes, four, a short line, four (user, 2026-11-25). One hidden field takes the typing, pasting and
 * the keyboard's Done; the box being typed in has the green frame and a blinking caret.
 */
@Composable
private fun CodeBoxes(value: String, enabled: Boolean, error: Boolean, onChange: (String) -> Unit, onDone: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val blink = androidx.compose.animation.core.rememberInfiniteTransition()
    val caret by blink.animateFloat(1f, 0f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(530, easing = androidx.compose.animation.core.LinearEasing),
        androidx.compose.animation.core.RepeatMode.Reverse))
    androidx.compose.foundation.text.BasicTextField(
        value = androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length)),
        onValueChange = { onChange(cleanCode(it.text)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEnter(onDone),
        decorationBox = { _ ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                for (i in 0 until CodeLength) {
                    if (i == CodeLength / 2) {
                        Box(Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (value.length >= i) Kit.Ink else Kit.Faint))
                    }
                    val char = value.getOrNull(i)
                    val current = focused && enabled && i == value.length.coerceAtMost(CodeLength - 1) && value.length < CodeLength
                    val frame = when {
                        error -> Kit.Danger
                        current -> Kit.Green
                        char != null -> Kit.Ink.copy(alpha = 0.35f)
                        else -> Kit.Border
                    }
                    Box(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (current) Kit.GreenSoft else if (char != null) Color.White else Kit.Canvas)
                        .border(BorderStroke(if (current || error) 2.dp else 1.5.dp, frame), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center) {
                        if (char != null) {
                            Text(char.toString(), translate = false, fontFamily = Inter(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.sp, color = Kit.Ink)
                        } else if (current) {
                            Box(Modifier.width(2.dp).height(24.dp).background(Kit.Green.copy(alpha = caret)))
                        }
                    }
                }
            }
        }
    )
}

/** Enter on a computer's keyboard does what the phone keyboard's Done does. */
private fun Modifier.onKeyEnter(action: () -> Unit): Modifier = this.then(Modifier.onPreviewKeyEvent { event ->
    val enter = event.key == androidx.compose.ui.input.key.Key.Enter || event.key == androidx.compose.ui.input.key.Key.NumPadEnter
    if (enter && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) { action(); true } else false
})

/** Waiting for the person who made the code: who, what this device becomes, and the time left (counted down here). */
@Composable
private fun WaitingForMaker(wait: com.saporini.mobile_desktop.admin.devices.ConnectWait) {
    val request = wait.request
    val maker = request.madeBy ?: "The person who made the code"
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    androidx.compose.runtime.LaunchedEffect(wait.shownAt, wait.ended) {
        while (wait.ended == null) { kotlinx.coroutines.delay(1_000); now = Clock.System.now().toEpochMilliseconds() }
    }
    val left = wait.secondsLeft(now)
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition()
    val grow by pulse.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)))
    val tint = if (wait.ended != null) Kit.Amber else Kit.Green
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            if (wait.ended == null) {
                Box(Modifier.size(56.dp + 40.dp * grow).clip(CircleShape).background(tint.copy(alpha = 0.18f * (1f - grow))))
            }
            Box(Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(if (wait.ended != null) Icons.Outlined.ErrorOutline else Icons.Outlined.HowToReg, null, Modifier.size(30.dp), tint = tint)
            }
        }
        if (wait.ended == null) {
            Text("Waiting for $maker", fontFamily = Inter(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp,
                color = Kit.Ink, textAlign = TextAlign.Center)
            Text("$maker made this code and got a message to agree. Then this device becomes ${request.deviceName}.",
                fontFamily = Inter(), fontSize = 13.5.sp, lineHeight = 19.sp, letterSpacing = 0.sp, color = Kit.Muted, textAlign = TextAlign.Center)
            Row(Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Timer, null, Modifier.size(15.dp), tint = Kit.Muted)
                Text("${left / 60}:${(left % 60).toString().padStart(2, '0')} left", fontFamily = Inter(), fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp, color = Kit.Ink)
            }
            if (request.deviceInUse) Note("${request.deviceName} is in use on another device. That one stops being it when $maker agrees.", Kit.Muted)
        } else {
            Text(wait.ended, fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp, color = Kit.Ink,
                textAlign = TextAlign.Center)
        }
    }
}

/**
 * Connecting it, in one card (user, 2026-11-27: "these two are so connected, merge them in one card"; before: This device and
 * Connect with a code). In use: who has it and Disconnect (asks first), nothing else (user, 2026-11-25: "you disconnect it
 * first"). Not in use: "Use this device" (tills and tablets) and a code for another device; a card terminal only the code.
 */
@Composable
internal fun ConnectCard(device: DeviceDto, state: DevicesState, model: DevicesScreenModel) {
    val till by TokenStore.till.collectAsState()
    val here = till?.deviceId == device.id
    val usable = device.status == "ACTIVE" || device.status == "PROVISIONING"
    val card = kindOf(device) == DeviceKind.CARD
    val code = state.freshCode?.takeIf { it.deviceId == device.id && it.pairingToken != null }
    var asking by remember { mutableStateOf(false) }
    Panel("Connection", null, Icons.Outlined.Link, Kit.Ink, picture = if (card) Res.drawable.auth_pin_create else Res.drawable.workspace_pos) {
        when {
            here -> ActionRow(Icons.Outlined.Computer, Kit.Ink, "${device.name} is this computer", "Staff sign in here with their PIN.") {
                DisconnectButton(device, here = true, state, model)
            }
            !usable -> Note("Put ${device.name} back in use to connect it.", Kit.Muted)
            !state.mayConnect(device) -> Note(state.connectRefusal, Kit.Muted)
            // Which device has it (user, 2026-11-27: "make it say who has it"), when that device said.
            device.connected -> ActionRow(Icons.Outlined.Link, Kit.Ink,
                device.connectedDeviceName?.let { "In use on $it" } ?: if (card) "${device.name} is in use" else "In use on another device",
                listOfNotNull(device.connectedDeviceSystem, device.connectedAt?.let { "since ${dateTimeText(it, RestaurantTime.zone)}" })
                    .joinToString(" · ").ifBlank { "Disconnect it to connect it again." }) {
                DisconnectButton(device, here = false, state, model)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!card) ActionRow(Icons.Outlined.PointOfSale, Kit.Ink, "Use it here", "Staff then sign in on this computer with their PIN.") {
                    KitButton("Use this device", { asking = true }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Login, loading = state.saving)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!card) Text("OR WITH A CODE, ON ANOTHER DEVICE", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
                        letterSpacing = 0.8.sp, color = Kit.Faint)
                    if (code != null) FreshCode(code.pairingToken!!, code.expiresAt)
                    CodeButton(state, model, again = code != null)
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
            Text("Staff will sign in here with their PIN.", fontFamily = Inter(), fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink)
        }
    }
}

/** Disconnect, asking first: on this device, or on the device where it's in use now (which then stops working). */
@Composable
private fun DisconnectButton(device: DeviceDto, here: Boolean, state: DevicesState, model: DevicesScreenModel) {
    var asking by remember { mutableStateOf(false) }
    KitButton("Disconnect", { asking = true }, style = ButtonStyle.DANGER_OUTLINE, icon = Icons.Outlined.LinkOff, loading = state.saving)
    if (asking) {
        AppDialog(title = if (here) "Disconnect this device?" else "Disconnect ${device.name}?", onDismiss = { asking = false }, maxWidth = 460.dp,
            buttons = {
                KitButton("Cancel", { asking = false }, style = ButtonStyle.SECONDARY)
                KitButton("Disconnect", { asking = false; model.disconnect(device.id) }, style = ButtonStyle.DANGER)
            }) {
            Text(if (here) "It stops being ${device.name}. Staff can't sign in here with their PIN until it's connected again."
                else "The device that is ${device.name} now stops working. Then you can connect it again, here or on another device.",
                fontFamily = Inter(), fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink)
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
    Panel("Who can sign in here", null,
        Icons.Outlined.Groups, Kit.Purple, picture = Res.drawable.overview_guests,
        trailing = if (state.canEdit && view != null) ({
            KitButton("Edit", { editing = true }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Edit, enabled = !state.saving)
        }) else null) {
        when {
            view == null -> Text("Loading…", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
            view.everyone -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Public, null, Modifier.size(18.dp), tint = Kit.Muted)
                Column(Modifier.weight(1f)) {
                    Text("Everyone at the branch", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp, color = Kit.Ink)
                    Text("Anyone who works at the branch signs in here with their PIN.", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp,
                        color = Kit.Muted)
                }
            }
            // By role, with how many (user, 2026-11-27: "make it 12 Admin, all of them, or 8 Manager"), not every name.
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                signInGroups(view).forEach { group -> RoleCount(group) }
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
                    ?: "Owners always can.", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
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
 * with a tick each, and "Everyone who is a …" on top to take the whole role. Owners show as "Always"; admins and managers are
 * chosen like anyone else (user, 2026-11-27: "I can remove access to someone; it doesn't mean all admins can use it").
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

/** One role on the Who can sign in card: the whole role ("All 12 people") or how many of its people ("8 of 10 people"). */
private data class SignInGroup(val name: String, val code: String, val picture: String?, val count: String)

/**
 * The card's summary: each role the person sees, whole or how many of its people; chosen roles they don't see; chosen people
 * outside every role they see as "Other people".
 */
private fun signInGroups(view: com.saporini.mobile_desktop.admin.devices.DeviceSignInView): List<SignInGroup> {
    val wholeRoles = view.roles.map { it.id }.toSet()
    val chosen = view.people.map { it.id }.toSet()
    val groups = view.tabs.mapNotNull { tab ->
        when {
            tab.id in wholeRoles -> SignInGroup(tab.name, tab.code, tab.picture, "All ${tab.people.size} people")
            else -> tab.people.count { it.id in chosen }.takeIf { it > 0 }
                ?.let { SignInGroup(tab.name, tab.code, tab.picture, "$it of ${tab.people.size} people") }
        }
    }
    val unseenRoles = view.roles.filter { role -> view.tabs.none { it.id == role.id } }
        .map { SignInGroup(it.name, it.code, it.picture, "All") }
    val others = view.people.count { person -> view.tabs.none { tab -> tab.people.any { it.id == person.id } } }
    return groups + unseenRoles + listOfNotNull(others.takeIf { it > 0 }?.let { SignInGroup("Other people", "", null, "$it") })
}

/** "[picture] Admin  All 12 people" as a chip in the role's colour. */
@Composable
private fun RoleCount(group: SignInGroup) {
    val look = com.saporini.mobile_desktop.admin.people.ui.roleLookOf(group.code, group.name, group.picture)
    Row(Modifier.clip(RoundedCornerShape(50)).background(look.color.copy(alpha = 0.08f))
        .border(1.dp, look.color.copy(alpha = 0.35f), RoundedCornerShape(50))
        .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        androidx.compose.foundation.Image(org.jetbrains.compose.resources.painterResource(look.image), null, Modifier.size(26.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        Text(group.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
        Text(group.count, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, letterSpacing = 0.sp, color = look.color, maxLines = 1)
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
