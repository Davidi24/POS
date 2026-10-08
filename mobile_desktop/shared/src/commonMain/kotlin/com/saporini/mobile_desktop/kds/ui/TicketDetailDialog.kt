package com.saporini.mobile_desktop.kds.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.kds.KdsScreenModel
import com.saporini.mobile_desktop.kds.model.KdsAction
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime

/** Everything about one ticket: its timeline, each dish with its own buttons, and the same order at other stations. */
@Composable
internal fun TicketDetailDialog(state: KdsState, model: KdsScreenModel) {
    val id = state.selectedTicketId ?: return
    val ticket = state.selectedTicket ?: state.tickets.firstOrNull { it.id == id } ?: state.history.items.firstOrNull { it.id == id }
    val busy = "ticket:$id" in state.busyKeys
    AppDialog(ticket?.title() ?: "Ticket", model::closeTicket, maxWidth = 640.dp,
        subtitle = ticket?.let { t -> listOfNotNull("#${t.ticketNumber.ifBlank { t.orderNumber }}", t.stationName, t.courseName).joinToString(" · ") },
        buttons = {
            KitButton("Close", model::closeTicket, style = ButtonStyle.SECONDARY)
            if (ticket != null && state.canUpdate) {
                if (ticket.allows(KdsAction.FIRE)) KitButton("Send to the kitchen", { model.perform(id, KdsAction.FIRE) },
                    icon = Icons.Outlined.LocalFireDepartment, style = ButtonStyle.SECONDARY, enabled = !busy)
                ticket.nextAction(ticket.lane())?.let { action ->
                    KitButton(when (action) { KdsAction.START -> "Start cooking"; KdsAction.READY -> "All ready"; KdsAction.COMPLETE -> "Served"; else -> "Send" },
                        { model.perform(id, action) }, loading = busy)
                }
            }
        }) {
        state.detailError?.let { MessageBar(it.message, MessageKind.ERROR) }
        if (ticket == null) {
            Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Kit.Green, strokeWidth = 2.dp) }
            return@AppDialog
        }
        Timeline(ticket)
        ticket.occasion?.takeIf { it.isNotBlank() }?.let { Banner(Icons.Outlined.Celebration, it, Kit.Purple) }
        Caption("Dishes")
        ticket.items.forEach { item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8F9F7)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${item.quantity}×", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                Column(Modifier.weight(1f)) {
                    Text(item.itemNameSnapshot, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Kit.Ink)
                    item.detailLine()?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted) }
                    item.notes?.takeIf { it.isNotBlank() }?.let { Text("“$it”", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF8B5C18)) }
                }
                StatusPill(itemStatusLabel(item.status), itemStatusColor(item.status))
                if (state.canUpdate) {
                    val step = item.nextAction() ?: KdsAction.COMPLETE.takeIf { ticket.allows(it, item.id) }
                    if (step != null && ticket.allows(step, item.id)) {
                        KitButton(when (step) { KdsAction.START -> "Start"; KdsAction.READY -> "Ready"; KdsAction.COMPLETE -> "Served"; else -> "Send" },
                            { model.perform(id, step, item.id) }, style = ButtonStyle.SECONDARY, enabled = !busy)
                    }
                }
            }
        }
        ticket.notes?.takeIf { it.isNotBlank() }?.let { Banner(Icons.AutoMirrored.Outlined.StickyNote2, it, Color(0xFF8B5C18)) }
        if (state.relatedTickets.isNotEmpty()) {
            Caption("Same order at other stations")
            state.relatedTickets.forEach { other ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Kitchen, null, Modifier.size(16.dp), tint = Kit.Muted)
                    Text(other.stationName ?: "Station", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${other.items.sumOf { it.quantity }} dishes", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                    StatusPill(ticketStatusLabel(other.status), itemStatusColor(other.status))
                }
            }
        } else if (state.detailLoading) {
            Text("Looking for the rest of the order…", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
    }
}

/** Sent → started → ready → served, with the times that happened. */
@Composable
private fun Timeline(ticket: KdsTicket) {
    val zone = RestaurantTime.zone
    val steps = listOf("Sent" to (ticket.firedAt ?: ticket.createdAt), "Started" to ticket.startedAt, "Ready" to ticket.readyAt, "Served" to ticket.completedAt)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { index, (label, at) ->
            val done = at != null
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(if (done) Kit.Green else Kit.Tint), Alignment.Center) {
                    Text("${index + 1}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (done) Color.White else Kit.Muted)
                }
                Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (done) Kit.Ink else Kit.Muted)
                Text(if (done) timeText(at, zone) else "–", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            }
            if (index < steps.lastIndex) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).height(2.dp).background(if (steps[index + 1].second != null) Kit.Green else Kit.Border))
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}
