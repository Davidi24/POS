package com.saporini.mobile_desktop.admin.devices.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.Navigator
import com.saporini.mobile_desktop.admin.adminMessage
import com.saporini.mobile_desktop.admin.devices.ConnectRequestDto
import com.saporini.mobile_desktop.admin.devices.ConnectWatch
import com.saporini.mobile_desktop.admin.devices.DeviceConnector
import com.saporini.mobile_desktop.auth.ui.login.LoginScreen
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.session.IdleLevel
import com.saporini.mobile_desktop.core.session.PIN_SIGN_IN
import com.saporini.mobile_desktop.core.session.IdleTime
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.TillHeartbeat
import com.saporini.mobile_desktop.core.session.TokenStore
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.notifications.NotificationCenter
import com.saporini.mobile_desktop.workspace.ui.resolveStartScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.workspace_pos
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.getKoin
import kotlin.time.Clock

/** The server's notification for a code typed on another device (`DeviceConnectRequestService.EVENT`). */
private const val ConnectEvent = "DEVICE_CONNECT_REQUEST"

/**
 * Which device is a till changes, wherever the app is (user, 2026-11-25: "it comes me the notification like the other device
 * is becoming the till and you will be removed; I agree, and in that moment I'm removed and it redirects me somewhere else"):
 * - Someone typed a code you made on another device: a window asks you to agree or refuse. The notification brings it, and
 *   while a code made here can still be typed, a look every few seconds too. Agreeing on the device that is that till makes
 *   it stop being it at once.
 * - The till moved to another device (or was switched off in Devices): this one says so.
 * Then the app leaves the till: a PIN sign-in ends (PINs only work on a till) and the sign-in screen shows; a password sign-in
 * stays and goes to the start; nobody signed in = the sign-in screen.
 */
@Composable
fun TillChanges(navigator: Navigator) {
    val koin = getKoin()
    val connector = remember { koin.getOrNull<DeviceConnector>() } ?: return
    val notifications = remember { koin.getOrNull<NotificationCenter>() }
    val session = remember { koin.get<SessionManager>() }
    val user by session.currentUser.collectAsState()
    val till by TokenStore.till.collectAsState()
    val heartbeat by TillHeartbeat.state.collectAsState()
    val watchUntil by ConnectWatch.until.collectAsState()
    var asking by remember { mutableStateOf<ConnectRequestDto?>(null) }
    var askingAt by remember { mutableStateOf(0L) }
    var answering by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var handled by remember { mutableStateOf(setOf<String>()) }
    var moved by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val restaurantId = user?.takeIf { it.isActive }?.restaurantId
    // Agreeing to a code needs the password: a PIN sign-in isn't asked (user, 2026-11-25).
    val mayConnect = user?.permissions.orEmpty().let { p -> ("DEVICES_CONNECT" in p || "DEVICES_MANAGE" in p) && PIN_SIGN_IN !in p }

    suspend fun look() {
        val restaurant = restaurantId ?: return
        if (!mayConnect || asking != null) return
        val waiting = runCatching { connector.waiting(restaurant) }.getOrNull() ?: return
        val next = waiting.firstOrNull { it.id !in handled } ?: return
        problem = null
        askingAt = nowMs()
        asking = next
    }

    fun leaveTill(name: String?) {
        val pin = PIN_SIGN_IN in session.currentUser.value?.permissions.orEmpty()
        TokenStore.forgetTill()
        TillHeartbeat.reset()
        val current = session.currentUser.value
        when {
            current == null -> navigator.replaceAll(LoginScreen)
            // The app's own watcher then shows the sign-in screen: with no till any more, the password one.
            pin -> session.signOut()
            else -> navigator.replaceAll(resolveStartScreen(current))
        }
        notice = name?.let { "This device isn't $it any more." }
    }

    LaunchedEffect(restaurantId, mayConnect) { look() }
    LaunchedEffect(notifications, restaurantId) {
        notifications?.arrivals?.collect { if (it.eventCode == ConnectEvent) look() }
    }
    LaunchedEffect(watchUntil, restaurantId, asking == null) {
        while (asking == null && nowMs() < watchUntil) {
            delay(4_000)
            look()
        }
    }
    LaunchedEffect(heartbeat, till) {
        val current = till
        if (heartbeat == TillHeartbeat.State.REFUSED && current != null && moved == null) moved = current.name
    }

    asking?.let { request ->
        val restaurant = restaurantId
        val hereIsIt = till?.deviceId == request.deviceId
        ConnectApprovalDialog(request, askingAt, hereIsIt, answering, problem,
            onAgree = {
                if (restaurant == null) return@ConnectApprovalDialog
                answering = true
                scope.launch {
                    try {
                        connector.agree(restaurant, request.id)
                        handled = handled + request.id
                        asking = null
                        if (TokenStore.till.value?.deviceId == request.deviceId) leaveTill(request.deviceName)
                        else notice = "${request.deviceName} moves to ${request.asking}."
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        problem = adminMessage(e, write = true)
                    } finally {
                        answering = false
                    }
                }
            },
            onRefuse = {
                if (restaurant == null) return@ConnectApprovalDialog
                answering = true
                scope.launch {
                    try {
                        connector.refuse(restaurant, request.id)
                        handled = handled + request.id
                        asking = null
                        notice = "Refused. That code doesn't work any more."
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        problem = adminMessage(e, write = true)
                    } finally {
                        answering = false
                    }
                }
            },
            onClose = {
                handled = handled + request.id
                asking = null
            })
    }
    moved?.let { name -> TillMovedDialog(name, onOk = { moved = null; leaveTill(name) }) }
    ScreenMessages(null, notice, { notice = null })
}

/**
 * "Linux computer (fedora) wants to become Bar till": who typed your code, on what, what happens to this device, and the
 * time left to answer (counted down here from the server's seconds). The till doesn't lock while it's open.
 */
@Composable
private fun ConnectApprovalDialog(
    request: ConnectRequestDto, shownAt: Long, hereIsIt: Boolean, busy: Boolean, problem: String?,
    onAgree: () -> Unit, onRefuse: () -> Unit, onClose: () -> Unit
) {
    IdleTime(IdleLevel.IMPORTANT)
    var now by remember { mutableStateOf(nowMs()) }
    LaunchedEffect(shownAt) { while (true) { delay(1_000); now = nowMs() } }
    val left = (request.secondsLeft - (now - shownAt) / 1000).coerceAtLeast(0)
    val over = left == 0L
    AppDialog(title = "${request.asking} wants to become ${request.deviceName}", onDismiss = onClose, maxWidth = 480.dp, busy = busy,
        buttons = {
            if (over) {
                KitButton("Close", onClose, style = ButtonStyle.SECONDARY)
            } else {
                KitButton("Refuse", onRefuse, style = ButtonStyle.DANGER_OUTLINE, enabled = !busy)
                KitButton("Agree", onAgree, icon = Icons.Outlined.Check, loading = busy)
            }
        }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(Res.drawable.workspace_pos), null, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${request.askedBy ?: "Someone"} typed your code for ${request.deviceName} on it.", fontFamily = Inter(),
                    fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp, color = Kit.Ink)
                request.askingSystem?.takeIf { it.isNotBlank() }?.let {
                    Text("${request.asking} · $it", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
                }
            }
        }
        when {
            hereIsIt -> Line("This device is ${request.deviceName} now. If you agree, it stops being ${request.deviceName} and you leave it.", Kit.Amber)
            request.deviceInUse -> Line("The device that is ${request.deviceName} now stops being it.", Kit.Amber)
        }
        Row(Modifier.clip(RoundedCornerShape(50)).background(if (over) Kit.Danger.copy(alpha = 0.08f) else Kit.Tint)
            .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Timer, null, Modifier.size(15.dp), tint = if (over) Kit.Danger else Kit.Muted)
            if (over) Text("Time's up. Nothing changed.", fontFamily = Inter(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp, color = Kit.Danger)
            else Text("Answer within ${left / 60}:${(left % 60).toString().padStart(2, '0')}", fontFamily = Inter(), fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp, color = Kit.Ink)
        }
        problem?.let { MessageBar(it, MessageKind.ERROR) }
    }
}

/** The till moved to another device, or was switched off: said once, then the app leaves the till. */
@Composable
private fun TillMovedDialog(name: String, onOk: () -> Unit) {
    AppDialog(title = "This device isn't $name any more", onDismiss = onOk, maxWidth = 460.dp,
        buttons = { KitButton("OK", onOk) }) {
        Text("$name was connected on another device, or switched off in Devices. Staff can't sign in here with their PIN now.",
            fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp, color = Kit.Ink, textAlign = TextAlign.Start)
    }
}

@Composable
private fun Line(text: String, color: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = color)
        Text(text, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp, color = Kit.Ink)
    }
}

private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
