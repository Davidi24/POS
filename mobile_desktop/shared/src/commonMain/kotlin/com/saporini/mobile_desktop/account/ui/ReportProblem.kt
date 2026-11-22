package com.saporini.mobile_desktop.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import com.saporini.mobile_desktop.admin.people.CreatePlatformRequestDto
import com.saporini.mobile_desktop.admin.people.PeopleRepository
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.platform.platformMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val MaxProblem = 2000

/**
 * "Report a problem" from the profile menu (user, 2026-11-21: "if a user has a problem they make a request and write a
 * message"): anyone working in a restaurant writes what's wrong and it goes to the Super Admin's Requests as a ticket, which
 * they assign to someone and follow through Open → Assigned → In progress → Solved. Once sent the window says so.
 */
@Composable
internal fun ReportProblemDialog(restaurantId: String, onClose: () -> Unit) {
    val people = koinInject<PeopleRepository>()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showProblem by remember { mutableStateOf(false) }
    val send: () -> Unit = {
        if (message.isBlank()) showProblem = true
        else {
            sending = true
            error = null
            scope.launch {
                try {
                    people.askSuperAdmin(restaurantId, CreatePlatformRequestDto(kind = "PROBLEM", note = message.trim()))
                    sent = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = platformMessage(e, write = true)
                } finally {
                    sending = false
                }
            }
            Unit
        }
    }
    if (sent) {
        AppDialog("Report a problem", onClose, maxWidth = 460.dp, buttons = { Box(Modifier.weight(1f)); KitButton("Done", onClose) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Kit.Green), Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(30.dp), tint = Color.White)
                }
                Text("Sent", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink)
                Text("The Super Admin has your message and will get back to you.", fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp,
                    color = Kit.Muted, textAlign = TextAlign.Center)
            }
        }
        return
    }
    AppDialog(
        "Report a problem", onClose,
        subtitle = "Tell the Super Admin what's wrong. They will look into it.",
        busy = sending, maxWidth = 560.dp,
        buttons = {
            KitButton("Cancel", onClose, style = ButtonStyle.SECONDARY, enabled = !sending)
            KitButton("Send", send, icon = Icons.AutoMirrored.Outlined.Send, loading = sending)
        }
    ) {
        error?.let { MessageBar(it, MessageKind.ERROR) }
        TextInput(message, { message = it.take(MaxProblem); showProblem = false }, Modifier.fillMaxWidth(), label = "What's wrong?", required = true,
            singleLine = false, minLines = 6, maxLength = MaxProblem,
            placeholder = "What happened, where in the app, and since when",
            problem = if (showProblem && message.isBlank()) "Write what the problem is." else null)
    }
}
