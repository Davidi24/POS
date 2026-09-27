package com.saporini.mobile_desktop.pos.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog

// A question before a booking action: what happens, and a reason (required when a manager corrects something, or
// when the rule asks for one, e.g. guests leaving before being seated).
internal data class ReasonPrompt(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val danger: Boolean = false,
    val reasonRequired: Boolean = false,
    val reasonHint: String = "Reason (optional)",
    val suggestions: List<String> = emptyList(),
    val onConfirm: (String?) -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReasonPromptDialog(prompt: ReasonPrompt, onDismiss: () -> Unit) {
    var reason by remember(prompt) { mutableStateOf("") }
    val ready = !prompt.reasonRequired || reason.isNotBlank()
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(prompt.message, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = FormMuted)
                if (prompt.suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        prompt.suggestions.forEach { suggestion ->
                            val picked = reason == suggestion
                            Text(
                                suggestion,
                                Modifier.clip(RoundedCornerShape(50))
                                    .background(if (picked) FormGreenSoft else Color.White)
                                    .border(1.dp, if (picked) FormGreen else FormBorder, RoundedCornerShape(50))
                                    .clickable { reason = suggestion }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                color = if (picked) FormGreen else FormInk
                            )
                        }
                    }
                }
                InputBox(Icons.Outlined.Notes, reason, prompt.reasonHint, isError = prompt.reasonRequired && reason.isBlank()) { reason = it.take(300) }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (ready) prompt.onConfirm(reason.trim().ifBlank { null }) },
                enabled = ready,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = if (prompt.danger) FormDanger else FormGreen)
            ) {
                Text(prompt.confirmLabel, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back", color = FormMuted, fontFamily = Inter()) } }
    )
}

// "Guest arrived" and "N of M arrived": how many of the group are here.
internal data class ArrivedPrompt(
    val title: String,
    val partySize: Int,
    val initial: Int,
    val confirmLabel: (Int) -> String,
    val reasonRequired: Boolean = false,
    val note: String? = null,
    val onConfirm: (count: Int, reason: String?) -> Unit
)

@Composable
internal fun ArrivedPromptDialog(prompt: ArrivedPrompt, onDismiss: () -> Unit) {
    var count by remember(prompt) { mutableIntStateOf(prompt.initial.coerceIn(1, prompt.partySize)) }
    var reason by remember(prompt) { mutableStateOf("") }
    val ready = !prompt.reasonRequired || reason.isNotBlank()
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Booked for ${prompt.partySize}. The rest can be added when they arrive.",
                    fontFamily = Inter(), fontSize = 13.sp, color = FormMuted
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountButton(Icons.Outlined.Remove, "Fewer", count > 1) { count-- }
                    Text(
                        "$count of ${prompt.partySize}", Modifier.width(96.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = FormInk, textAlign = TextAlign.Center
                    )
                    CountButton(Icons.Outlined.Add, "More", count < prompt.partySize) { count++ }
                    if (count < prompt.partySize) {
                        TextButton(onClick = { count = prompt.partySize }) { Text("Everyone", fontFamily = Inter(), color = FormGreen) }
                    }
                }
                prompt.note?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = LateColor) }
                if (prompt.reasonRequired) {
                    InputBox(Icons.Outlined.Notes, reason, "Reason for the correction", isError = reason.isBlank()) { reason = it.take(300) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (ready) prompt.onConfirm(count, reason.trim().ifBlank { null }) },
                enabled = ready,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = FormGreen)
            ) {
                Text(prompt.confirmLabel(count), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back", color = FormMuted, fontFamily = Inter()) } }
    )
}

// The guest called: keep the table longer. Only the hold moves; the booking still ends on time.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HoldLongerDialog(guestName: String, endsAt: String, onConfirm: (minutes: Int, reason: String?) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableIntStateOf(15) }
    var reason by remember { mutableStateOf("") }
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hold the table longer?", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The table waits longer for $guestName. The booking still ends at $endsAt.",
                    fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = FormMuted
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 20, 30).forEach { option ->
                        val picked = minutes == option
                        Text(
                            "+$option min",
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (picked) FormGreenSoft else Color.White)
                                .border(if (picked) 1.5.dp else 1.dp, if (picked) FormGreen else FormBorder, RoundedCornerShape(8.dp))
                                .clickable { minutes = option }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            fontFamily = Inter(), fontWeight = if (picked) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp,
                            color = if (picked) FormGreen else FormInk
                        )
                    }
                }
                InputBox(Icons.Outlined.Notes, reason, "Note, e.g. \"Called: stuck in traffic\"") { reason = it.take(300) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(minutes, reason.trim().ifBlank { null }) },
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = FormGreen)
            ) { Text("Hold $minutes min longer", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back", color = FormMuted, fontFamily = Inter()) } }
    )
}

@Composable
private fun CountButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Color.White else Color(0xFFF3F4F2))
            .border(1.dp, FormBorder, RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, Modifier.size(18.dp), tint = if (enabled) FormInk else Color(0xFFB9BCB5))
    }
}

// The state labels of a booking, e.g. "12 min late", "3 of 6 arrived", "Waiting for table".
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookingFlagChips(flags: List<BookingFlag>, modifier: Modifier = Modifier) {
    if (flags.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        flags.forEach { flag ->
            Text(
                flag.text,
                Modifier.clip(RoundedCornerShape(50)).background(flag.color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 4.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                color = if (flag.color == HoldEndsColor) Color(0xFF8A6A00) else flag.color
            )
        }
    }
}
