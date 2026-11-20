package com.saporini.mobile_desktop.platform.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.saporini.mobile_desktop.admin.people.PlatformRequestDto
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.platform.PlatformRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.koin.compose.koinInject

/**
 * Super Admin: what restaurants ask (user, 2026-11-19): to remove one of their owners (owners never remove each other; you
 * check with that owner first, then approve or decline) or for a support admin (give them one on the Admins page).
 */
object RestaurantRequestsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val platform = koinInject<PlatformRepository>()
        val scope = rememberCoroutineScope()
        var list by remember { mutableStateOf<List<PlatformRequestDto>?>(null) }
        var waitingOnly by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var notice by remember { mutableStateOf<String?>(null) }
        var deciding by remember { mutableStateOf<Pair<PlatformRequestDto, Boolean>?>(null) }
        var busy by remember { mutableStateOf(false) }
        var reload by remember { mutableStateOf(0) }
        LaunchedEffect(waitingOnly, reload) {
            try {
                list = platform.platformRequests(waitingOnly)
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load the requests."
            }
        }
        ScreenMessages(error, notice, onClear = { error = null; notice = null })
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFCFCFB)), contentAlignment = Alignment.TopCenter) {
            val narrow = maxWidth < 760.dp
            ScrollColumn(Modifier.widthIn(max = 920.dp).fillMaxSize()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = if (narrow) 16.dp else 36.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BackArrow("Back to the restaurants") { navigator?.pop() }
                        Column(Modifier.weight(1f)) {
                            Text("Requests", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 18.sp else 21.sp, color = Kit.Ink)
                            Text("What restaurants ask you", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                        }
                    }
                    val waiting = "Waiting"
                    val everything = "All"
                    OverviewTabs(listOf(waiting, everything), if (waitingOnly) waiting else everything, { waitingOnly = it == waiting })
                    val items = list
                    when {
                        items == null -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
                        items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 24.dp), Alignment.Center) {
                            OverviewEmpty(if (waitingOnly) "Nothing waiting" else "No requests yet",
                                "Owners ask here to remove another owner, or for a support admin.", Icons.Outlined.MarkEmailUnread)
                        }
                        else -> items.forEach { request -> RequestCard(request, narrow) { approve -> deciding = request to approve } }
                    }
                }
            }
        }
        deciding?.let { (request, approve) ->
            var note by remember(request.id) { mutableStateOf("") }
            ConfirmDialog(
                when {
                    !approve -> "Decline this request?"
                    request.kind == "REMOVE_OWNER" -> "Remove ${request.targetName ?: "this owner"}?"
                    else -> "Mark as done?"
                },
                when {
                    !approve -> "The restaurant sees that you declined it."
                    request.kind == "REMOVE_OWNER" -> "Check with ${request.targetName ?: "them"} first. They're removed from ${request.restaurantName ?: "the restaurant"} and signed out everywhere."
                    else -> "Give them a support admin on the Admins page (Restaurants)."
                },
                if (!approve) "Decline" else if (request.kind == "REMOVE_OWNER") "Remove" else "Done",
                onConfirm = {
                    busy = true
                    scope.launch {
                        try {
                            platform.decideRequest(request.id, approve, note.trim().ifEmpty { null })
                            notice = if (approve) "Done" else "Declined"
                            deciding = null
                            reload++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "Couldn't save the answer."
                        } finally {
                            busy = false
                        }
                    }
                },
                onDismiss = { if (!busy) deciding = null },
                danger = approve && request.kind == "REMOVE_OWNER",
                busy = busy,
                extra = { TextInput(note, { note = it.take(2000) }, label = "Note for the restaurant", optional = true, maxLength = 2000) }
            )
        }
    }
}

@Composable
private fun RequestCard(request: PlatformRequestDto, narrow: Boolean, onDecide: (Boolean) -> Unit) {
    val removal = request.kind == "REMOVE_OWNER"
    val zone = TimeZone.currentSystemDefault()
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background((if (removal) Kit.Danger else Kit.Blue).copy(alpha = 0.10f)), Alignment.Center) {
                    Icon(if (removal) Icons.Outlined.PersonRemove else Icons.Outlined.SupportAgent, null, Modifier.size(20.dp), tint = if (removal) Kit.Danger else Kit.Blue)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (removal) "Remove an owner: ${request.targetName ?: "someone"}" else "Needs a support admin",
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                    Text(listOfNotNull(request.restaurantName, request.createdByName?.let { "asked by $it" }, dateTimeText(request.createdAt, zone))
                        .joinToString(" · "), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                }
                StatusChip(when (request.status) { "APPROVED" -> "Done"; "DECLINED" -> "Declined"; else -> "Waiting" },
                    when (request.status) { "APPROVED" -> Kit.Green; "DECLINED" -> Kit.Grey; else -> Kit.Amber })
            }
            request.note?.let { Text("“$it”", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink) }
            request.decisionNote?.let { Text("Your answer: $it", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted) }
            if (request.status == "PENDING") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitButton("Decline", { onDecide(false) }, style = ButtonStyle.DANGER_OUTLINE, icon = Icons.Outlined.Close)
                    KitButton(if (removal) "Remove the owner" else "Mark as done", { onDecide(true) }, icon = Icons.Outlined.Check)
                }
            }
        }
    }
}
