package com.saporini.mobile_desktop.kds.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.InfoBox
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.components.TimeColumn
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.kds.KdsScreenModel
import com.saporini.mobile_desktop.kds.model.KdsAction
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.kds.model.asInstant
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderScreenModel
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderState
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_next_hours
import kotlin.time.Instant

/** Food that isn't cooking yet: held and timed tickets, and the food ordered ahead with bookings. */
@Composable
internal fun UpcomingSection(state: KdsState, model: KdsScreenModel, size: ScreenSize, now: Instant, preOrders: PreOrderScreenModel) {
    DisposableEffect(preOrders) {
        preOrders.loadList()
        onDispose { preOrders.onDispose() }
    }
    val pre by preOrders.state.collectAsState()
    // Pre-orders turn into tickets on their own; keep the list in step with the board.
    LaunchedEffect(state.lastRefreshedAt) { preOrders.loadList() }
    var openPreOrder by remember { mutableStateOf<PreOrderDto?>(null) }
    val tickets = state.upcomingTickets
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader("Upcoming", "Held and timed tickets, and food ordered ahead with bookings") {
            RefreshButton(state.loading || pre.loadingList, { model.refresh(); preOrders.loadList() })
        }
        val ticketList: @Composable (Modifier) -> Unit = { m -> UpcomingTickets(tickets, state, model, now, size, m) }
        if (pre.canRead) {
            if (size.isDesktop) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ticketList(Modifier.weight(1.3f).fillMaxHeight())
                    PreOrdersPanel(pre, Modifier.weight(1f).fillMaxHeight()) { openPreOrder = it }
                }
            } else {
                ticketList(Modifier.weight(1f).fillMaxWidth())
                PreOrdersPanel(pre, Modifier.weight(1f).fillMaxWidth()) { openPreOrder = it }
            }
        } else ticketList(Modifier.weight(1f).fillMaxWidth())
    }
    openPreOrder?.let { shown -> PreOrderDialog(shown, pre, preOrders) { openPreOrder = null; preOrders.open(null) } }
}

@Composable
private fun UpcomingTickets(tickets: List<KdsTicket>, state: KdsState, model: KdsScreenModel, now: Instant, size: ScreenSize, modifier: Modifier) {
    OverviewPanel(Icons.Outlined.Schedule, "Waiting to start", modifier, count = tickets.size) {
        if (tickets.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty("Nothing held back", "Tickets held by the waiters or timed for later wait here until they're sent.", Icons.Outlined.Schedule,
                    image = Res.drawable.overview_next_hours)
            }
            return@OverviewPanel
        }
        val held = tickets.filter { it.dueAt.asInstant() == null || it.priority == "HOLD_FIRE" }
        val timed = tickets - held.toSet()
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
            if (timed.isNotEmpty()) {
                item(key = "timed") { GroupLabel("Timed", "sent by themselves at their time") }
                items(timed, key = { it.id }) { UpcomingCard(it, state, model, now, size) }
            }
            if (held.isNotEmpty()) {
                item(key = "held") { GroupLabel("Held", "waiting for the waiter to send them") }
                items(held, key = { it.id }) { UpcomingCard(it, state, model, now, size) }
            }
        }
    }
}

@Composable
private fun UpcomingCard(ticket: KdsTicket, state: KdsState, model: KdsScreenModel, now: Instant, size: ScreenSize) {
    val zone = RestaurantTime.zone
    val due = ticket.dueAt.asInstant()
    val inMinutes = due?.let { (it - now).inWholeMinutes }
    val busy = "ticket:${ticket.id}" in state.busyKeys
    StripCard(if (due == null) Kit.Grey else Kit.Blue, minHeight = 72.dp, onClick = { model.selectTicket(ticket.id) }) {
        if (!size.isPhone) {
            TimeColumn(if (due != null) timeText(ticket.dueAt, zone) else "Held", when {
                inMinutes == null -> "until sent"
                inMinutes <= 0 -> "due now"
                inMinutes < 60 -> "in $inMinutes min"
                else -> "in ${inMinutes / 60} h ${inMinutes % 60} min"
            }, if (due == null) Kit.Grey else Kit.Blue, Modifier.width(92.dp), icon = if (due == null) Icons.Outlined.PauseCircle else Icons.Outlined.Schedule)
            CardDivider()
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(listOfNotNull(ticket.title(), ticket.courseName).joinToString(" · "), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ticket.items.joinToString(", ") { "${it.quantity}× ${it.itemNameSnapshot}" }, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            ticket.occasion?.takeIf { it.isNotBlank() }?.let { Text(it, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Purple, maxLines = 1) }
        }
        if (state.canUpdate && ticket.allows(KdsAction.FIRE)) {
            KitButton(if (busy) "Sending…" else "Send now", { model.perform(ticket.id, KdsAction.FIRE) }, icon = Icons.Outlined.LocalFireDepartment,
                style = ButtonStyle.SECONDARY, loading = busy)
        }
    }
}

@Composable
private fun PreOrdersPanel(pre: PreOrderState, modifier: Modifier, onOpen: (PreOrderDto) -> Unit) {
    val zone = RestaurantTime.zone
    OverviewPanel(Icons.AutoMirrored.Outlined.EventNote, "Ordered with bookings", modifier, count = pre.upcoming.size) {
        pre.error?.let { MessageBar(it, MessageKind.ERROR) }
        if (pre.upcoming.isEmpty()) {
            OverviewCompactEmpty("No food ordered ahead", "When guests order their food with the booking, it shows here for the next 7 days.",
                Icons.AutoMirrored.Outlined.EventNote)
            return@OverviewPanel
        }
        val byDay = pre.upcoming.groupBy { localOf(it.reservationStart, zone)?.date }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            byDay.forEach { (day, list) ->
                item(key = "d-$day") { GroupLabel(day?.let(::weekdayDate) ?: "Date to confirm", "${list.size} ${if (list.size == 1) "booking" else "bookings"}") }
                items(list, key = { it.id }) { preOrder ->
                    StripCard(Kit.Purple, minHeight = 64.dp, chevron = true, onClick = { onOpen(preOrder) }) {
                        TimeColumn(timeText(preOrder.reservationStart, zone), preOrder.partySize?.let { "$it guests" } ?: "", Kit.Purple, Modifier.width(66.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(preOrder.contactName ?: preOrder.reservationCode ?: "Booking", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${preOrder.items.sumOf { it.quantity }} dishes" + (preOrder.sendAt?.let { " · to the kitchen at ${timeText(it, zone)}" } ?: ""),
                                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreOrderDialog(shown: PreOrderDto, pre: PreOrderState, model: PreOrderScreenModel, onClose: () -> Unit) {
    val zone = RestaurantTime.zone
    LaunchedEffect(shown.reservationId) { model.open(shown.reservationId) }
    // The fresh copy once loaded (it may have been sent or changed since the list was read).
    val current = pre.preOrder?.takeIf { it.id == shown.id } ?: pre.upcoming.firstOrNull { it.id == shown.id } ?: shown
    AppDialog(current.contactName ?: "Booking ${current.reservationCode ?: ""}", onClose, maxWidth = 560.dp, busy = pre.saving,
        subtitle = listOfNotNull(dateTimeText(current.reservationStart, zone), current.partySize?.let { "$it guests" }, current.reservationCode).joinToString(" · "),
        buttons = {
            KitButton("Close", onClose, style = ButtonStyle.SECONDARY)
            if (pre.canSend && current.open) KitButton("Send to the kitchen now", model::sendNow, icon = Icons.Outlined.LocalFireDepartment,
                loading = pre.saving, enabled = pre.preOrder?.id == current.id)
        }) {
        pre.error?.let { MessageBar(it, MessageKind.ERROR) }
        pre.notice?.let { MessageBar(it, MessageKind.SUCCESS) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusPill(when (current.status) { "SCHEDULED" -> "Waiting"; "SENT" -> "In the kitchen"; "CANCELLED" -> "Cancelled"; else -> "Not used" },
                when (current.status) { "SCHEDULED" -> Kit.Purple; "SENT" -> Kit.Green; else -> Kit.Grey })
            current.sendAt?.let { Text("Goes to the kitchen at ${timeText(it, zone)}", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted) }
        }
        current.items.forEach { item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8F9F7)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${item.quantity}×", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(item.itemName, item.variantName).joinToString(" · "), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Kit.Ink)
                    if (item.options.isNotEmpty()) Text(item.options.joinToString(", ") { o -> (if (o.quantity > 1) "${o.quantity}× " else "") + (o.name ?: "option") },
                        fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                    item.notes?.takeIf { it.isNotBlank() }?.let { Text("“$it”", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF8B5C18)) }
                }
            }
        }
        current.notes?.takeIf { it.isNotBlank() }?.let { InfoBox(Icons.Outlined.Groups, it, Modifier.fillMaxWidth(), tone = Kit.Purple, highlighted = true) }
    }
}
