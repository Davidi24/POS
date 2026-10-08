package com.saporini.mobile_desktop.kds.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RoomService
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.kds.model.KdsAction
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.kds.model.KdsTicketItem
import com.saporini.mobile_desktop.kds.model.asInstant
import kotlin.time.Instant

/** The next step for a whole ticket in [lane], when it's allowed. */
internal fun KdsTicket.nextAction(lane: Lane): KdsAction? = when (lane) {
    Lane.NEW -> KdsAction.START
    Lane.COOKING -> KdsAction.READY
    Lane.READY -> KdsAction.COMPLETE
}.takeIf { allows(it) }

/** The next step for one dish: start it, or mark it ready. */
internal fun KdsTicketItem.nextAction(): KdsAction? = when (status) {
    "PENDING", "FIRED" -> KdsAction.START
    "IN_PROGRESS" -> KdsAction.READY
    else -> null
}

private fun KdsAction.buttonText(): String = when (this) {
    KdsAction.FIRE -> "Send to the kitchen"
    KdsAction.START -> "Start cooking"
    KdsAction.READY -> "Ready"
    KdsAction.COMPLETE -> "Served"
}

private fun KdsAction.icon(): ImageVector = when (this) {
    KdsAction.FIRE -> Icons.Outlined.LocalFireDepartment
    KdsAction.START -> Icons.Outlined.PlayArrow
    KdsAction.READY -> Icons.Outlined.RoomService
    KdsAction.COMPLETE -> Icons.Outlined.DoneAll
}

/**
 * One ticket as the cook sees it: who it's for, how long it has waited (red past the late limit from Admin Hub
 * settings), every dish with its options and notes, and one big button for the next step. Tapping a dish moves just
 * that dish on.
 */
@Composable
internal fun TicketCard(
    ticket: KdsTicket,
    lane: Lane,
    state: KdsState,
    now: Instant,
    onAction: (KdsAction, String?) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timing = state.posTiming
    val waited = if (lane == Lane.READY) minutesBetween(ticket.readyAt.asInstant(), now) else minutesBetween(ticket.since(), now)
    val late = (waited ?: 0) >= if (lane == Lane.READY) timing.readyWaitingMinutes else timing.slowAfterMinutes
    val tone = if (late) Kit.Danger else lane.color
    val busy = "ticket:${ticket.id}" in state.busyKeys
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White,
        border = BorderStroke(if (late) 1.5.dp else 1.dp, if (late) Kit.Danger.copy(alpha = 0.5f) else Kit.RowBorder), shadowElevation = 2.dp) {
        Column {
            // Head: who it's for and how long it has waited.
            Row(Modifier.fillMaxWidth().background(tone.copy(alpha = 0.09f)).clickable(onClick = onOpen).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ticket.title(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull("#${ticket.ticketNumber.ifBlank { ticket.orderNumber }}", ticket.guestCount?.let { if (it == 1) "1 guest" else "$it guests" },
                        ticket.courseName, ticket.stationName.takeIf { state.stationId == null && state.boards.size > 1 }).joinToString(" · "),
                        fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusChip(waited?.let { "$it min" } ?: "now", tone, Icons.Outlined.Timer, strong = late)
                Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onOpen), Alignment.Center) {
                    Icon(Icons.Outlined.MoreHoriz, "Ticket details", Modifier.size(18.dp), tint = Kit.Ink)
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ticket.priority in setOf("RUSH", "VIP")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (ticket.priority == "RUSH") StatusChip("Rush", Kit.Danger, Icons.Outlined.Bolt, strong = true)
                        if (ticket.priority == "VIP") StatusChip("VIP", Kit.Purple, Icons.Outlined.Star, strong = true)
                    }
                }
                ticket.occasion?.takeIf { it.isNotBlank() }?.let { Banner(Icons.Outlined.Celebration, it, Kit.Purple) }
                ticket.items.forEach { item ->
                    val step = item.nextAction()?.takeIf { state.canUpdate && ticket.allows(it, item.id) }
                    DishRow(item, if (step != null && !busy) ({ onAction(step, item.id) }) else null)
                }
                ticket.notes?.takeIf { it.isNotBlank() }?.let { Banner(Icons.AutoMirrored.Outlined.StickyNote2, it, Color(0xFF8B5C18)) }
                val action = ticket.nextAction(lane)
                if (state.canUpdate && action != null) {
                    Spacer(Modifier.height(2.dp))
                    BigActionButton(action.buttonText(), action.icon(), if (lane == Lane.NEW) Kit.Ink else lane.color, busy) { onAction(action, null) }
                }
            }
        }
    }
}

@Composable
private fun DishRow(item: KdsTicketItem, onStep: (() -> Unit)?) {
    val done = item.status in setOf("READY", "EXPO_READY", "COMPLETED")
    val cancelled = item.status == "CANCELLED"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).then(if (onStep != null) Modifier.clickable(onClick = onStep) else Modifier)
            .padding(vertical = 3.dp).alpha(if (cancelled) 0.45f else 1f),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.padding(top = 2.dp).size(18.dp), Alignment.Center) {
            when {
                done -> Icon(Icons.Filled.CheckCircle, "Ready", Modifier.size(18.dp), tint = Kit.Green)
                item.status == "IN_PROGRESS" -> Box(Modifier.size(12.dp).clip(CircleShape).background(Kit.Amber))
                else -> Box(Modifier.size(12.dp).clip(CircleShape).border(1.5.dp, Kit.Faint, CircleShape))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("${item.quantity}×", Modifier.width(30.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(item.itemNameSnapshot, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (done) Kit.Muted else Kit.Ink,
                textDecoration = if (cancelled) TextDecoration.LineThrough else null)
            item.detailLine()?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted) }
            item.notes?.takeIf { it.isNotBlank() }?.let {
                Text("“$it”", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF8B5C18))
            }
        }
        if (item.priority == "RUSH") Icon(Icons.Outlined.Bolt, "Rush", Modifier.size(16.dp), tint = Kit.Danger)
    }
}

@Composable
internal fun Banner(icon: ImageVector, text: String, color: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, Modifier.size(15.dp), tint = color)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, color = color)
    }
}

@Composable
internal fun BigActionButton(text: String, icon: ImageVector, color: Color, busy: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(9.dp), color = color) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(icon, null, Modifier.size(19.dp), tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "Saving…" else text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
        }
    }
}
