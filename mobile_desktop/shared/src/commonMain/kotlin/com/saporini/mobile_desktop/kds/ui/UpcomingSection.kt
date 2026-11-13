@file:OptIn(ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.kds.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.drawBehind
import com.saporini.mobile_desktop.core.components.ChevronCircle
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.Icon
import com.saporini.mobile_desktop.core.ui.PlatformVerticalScrollbar
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
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

/**
 * Food that isn't cooking yet, as cards like the board's tickets (user, 2026-10-29: "the upcoming make them as cards"):
 * tickets that wait (timed ones first, then held ones) and the food ordered ahead with bookings, by day. Each card shows
 * when, for whom, the dishes with their photos, and what happens next.
 */
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
    val zone = RestaurantTime.zone
    val photos = remember(state.menu.items) { state.menu.items.associate { it.item.id to it.item.imageUrl } }
    val nextDue = tickets.mapNotNull { it.dueAt.asInstant() }.filter { it > now }.minOrNull()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val subtitle = listOfNotNull(
            "${tickets.size} waiting",
            nextDue?.let { "next at ${timeText(it.toString(), zone)}" },
            "${pre.upcoming.size} ${if (pre.upcoming.size == 1) "booking" else "bookings"} with food".takeIf { pre.canRead }
        ).joinToString(" · ")
        PageHeader("Upcoming", subtitle) {
            RefreshButton(state.loading || pre.loadingList, { model.refresh(); preOrders.loadList() })
        }
        val ticketList: @Composable (Modifier) -> Unit = { m -> UpcomingTickets(tickets, state, model, now, size, photos, m) }
        if (pre.canRead) {
            if (size.isDesktop) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ticketList(Modifier.weight(1f).fillMaxHeight())
                    PreOrdersPanel(pre, now, size, photos, Modifier.weight(1f).fillMaxHeight()) { openPreOrder = it }
                }
            } else {
                ticketList(Modifier.weight(1f).fillMaxWidth())
                PreOrdersPanel(pre, now, size, photos, Modifier.weight(1f).fillMaxWidth()) { openPreOrder = it }
            }
        } else ticketList(Modifier.weight(1f).fillMaxWidth())
    }
    openPreOrder?.let { shown -> PreOrderDialog(shown, pre, preOrders) { openPreOrder = null; preOrders.open(null) } }
}

/** How wide a card is at least; a panel fits as many as it can. */
private val UpcomingCardWidth = 290.dp

@Composable
private fun UpcomingTickets(
    tickets: List<KdsTicket>, state: KdsState, model: KdsScreenModel, now: Instant, size: ScreenSize, photos: Map<String, String?>, modifier: Modifier
) {
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
        CardRows(listOfNotNull(
            timed.takeIf { it.isNotEmpty() }?.let { Triple("Timed", "${it.size} · sent by themselves at their time", it) },
            held.takeIf { it.isNotEmpty() }?.let { Triple("Held", "${it.size} · waiting for the waiter to send them", it) }
        ), key = { it.id }) { UpcomingCard(it, state, model, now, photos) }
    }
}

/**
 * Groups of cards (a label, then the cards as many to a row as fit, each at least [UpcomingCardWidth]), in one list that
 * scrolls with its scrollbar.
 */
@Composable
private fun <T> ColumnScope.CardRows(groups: List<Triple<String, String, List<T>>>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val gap = 10.dp
        val columns = ((maxWidth - 8.dp + gap) / (UpcomingCardWidth + gap)).toInt().coerceAtLeast(1)
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(end = 8.dp, bottom = 6.dp)) {
            groups.forEach { (label, detail, list) ->
                item(key = "g-$label") { GroupLabel(label, detail) }
                list.chunked(columns).forEach { row ->
                    item(key = "r-" + row.joinToString("|") { key(it).toString() }) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { Box(Modifier.weight(1f).fillMaxHeight()) { card(it) } }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) { PlatformVerticalScrollbar(listState, Modifier.fillMaxHeight().width(3.dp)) }
    }
}

/** A card's dishes: photo, "2 × Name", at most [max], then how many more. */
@Composable
private fun CardDishes(dishes: List<Triple<Int, String, String?>>, photos: Map<String, String?>, max: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        dishes.take(max).forEach { (qty, name, menuItemId) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DishPhoto(menuItemId?.let { photos[it] }, 30.dp)
                Text("$qty × ", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
                Text(name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        if (dishes.size > max) Text("+ ${dishes.size - max} more ${if (dishes.size - max == 1) "dish" else "dishes"}", Modifier.padding(start = 38.dp),
            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Kit.Muted)
    }
}

/** The look all Upcoming cards share: white, a strip in their colour, a head, dishes and a foot. */
@Composable
private fun UpcomingCardFrame(strip: Color, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().fillMaxHeight(), shape = RoundedCornerShape(14.dp), color = Color.White,
        border = BorderStroke(1.dp, Kit.RowBorder), shadowElevation = 1.dp) {
        Column(Modifier.drawBehind { drawRect(strip, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** A ticket that waits: when it goes (or that it's held), for whom, its dishes, and "Send now". */
@Composable
private fun UpcomingCard(ticket: KdsTicket, state: KdsState, model: KdsScreenModel, now: Instant, photos: Map<String, String?>) {
    val zone = RestaurantTime.zone
    val due = ticket.dueAt.asInstant().takeIf { ticket.priority != "HOLD_FIRE" }
    val inMinutes = due?.let { (it - now).inWholeMinutes }
    val busy = "ticket:${ticket.id}" in state.busyKeys
    val orderKind = ticket.kind()
    val tone = when {
        due == null -> Kit.Grey
        (inMinutes ?: 0) <= 15 -> Kit.Amber
        else -> Kit.Blue
    }
    UpcomingCardFrame(tone, onClick = { model.selectTicket(ticket.id) }) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(if (due == null) Icons.Outlined.PauseCircle else Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = tone)
                    Text(if (due != null) timeText(ticket.dueAt, zone) else "Held", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink)
                }
                Text(listOfNotNull("#${ticket.ticketNumber.ifBlank { ticket.orderNumber }.takeLast(6)}",
                    if (orderKind == OrderKind.DINE_IN) ticket.title() else ticket.customerName, ticket.stationName).joinToString(" · "),
                    fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusPill(when {
                    inMinutes == null -> "Held"
                    inMinutes <= 0 -> "Due now"
                    inMinutes < 60 -> "In $inMinutes min"
                    else -> "In ${inMinutes / 60} h ${(inMinutes % 60).toString().padStart(2, '0')}"
                }, tone)
                Text(orderKind.label, Modifier.clip(RoundedCornerShape(50)).background(orderKind.color.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = orderKind.color, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))
        CardDishes(ticket.items.map { Triple(it.quantity, it.itemNameSnapshot, it.menuItemId) }, photos)
        ticket.occasion?.takeIf { it.isNotBlank() }?.let { Banner(Icons.Outlined.Celebration, it, Kit.Purple) }
        if (state.canUpdate && ticket.allows(KdsAction.FIRE)) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (due == null) "The waiter sends it" else "Goes by itself at ${timeText(ticket.dueAt, zone)}", Modifier.weight(1f), fontFamily = Inter(),
                fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Surface(onClick = { model.perform(ticket.id, KdsAction.FIRE) }, enabled = !busy, modifier = Modifier.height(34.dp), shape = RoundedCornerShape(9.dp),
                color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(16.dp), tint = Kit.Danger)
                    Text(if (busy) "Sending…" else "Send now", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PreOrdersPanel(pre: PreOrderState, now: Instant, size: ScreenSize, photos: Map<String, String?>, modifier: Modifier, onOpen: (PreOrderDto) -> Unit) {
    val zone = RestaurantTime.zone
    val today = now.toLocalDateTime(zone).date
    OverviewPanel(Icons.AutoMirrored.Outlined.EventNote, "Ordered with bookings", modifier, count = pre.upcoming.size) {
        pre.error?.let { MessageBar(it, MessageKind.ERROR) }
        if (pre.upcoming.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty("No food ordered ahead", "When guests order their food with the booking, it shows here for the next 7 days.",
                    Icons.AutoMirrored.Outlined.EventNote)
            }
            return@OverviewPanel
        }
        val byDay = pre.upcoming.sortedBy { it.reservationStart }.groupBy { localOf(it.reservationStart, zone)?.date }
        CardRows(byDay.map { (day, list) ->
            val label = when {
                day == null -> "Date to confirm"
                day == today -> "Today · ${weekdayDate(day)}"
                day == today.plus(DatePeriod(days = 1)) -> "Tomorrow · ${weekdayDate(day)}"
                else -> weekdayDate(day)
            }
            Triple(label, "${list.size} ${if (list.size == 1) "booking" else "bookings"} · ${list.sumOf { p -> p.items.sumOf { it.quantity } }} dishes", list)
        }, key = { it.id }) { preOrder -> PreOrderCard(preOrder, photos) { onOpen(preOrder) } }
    }
}

/** A booking's food: when they come and how many, who, the dishes, and when it goes to the kitchen. */
@Composable
private fun PreOrderCard(preOrder: PreOrderDto, photos: Map<String, String?>, onOpen: () -> Unit) {
    val zone = RestaurantTime.zone
    val sent = preOrder.status == "SENT"
    val tone = if (sent) Kit.Green else Kit.Purple
    UpcomingCardFrame(tone, onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.EventNote, null, Modifier.size(18.dp), tint = tone)
                    Text(timeText(preOrder.reservationStart, zone), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink)
                    preOrder.partySize?.let {
                        Row(Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Outlined.Groups, null, Modifier.size(12.dp), tint = Kit.Muted)
                            Text("$it", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Ink)
                        }
                    }
                }
                Text(preOrder.contactName ?: preOrder.reservationCode ?: "Booking", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusPill(if (sent) "In the kitchen" else "Waiting", tone)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))
        CardDishes(preOrder.items.map { Triple(it.quantity, it.itemName ?: "Dish", it.menuItemId) }, photos)
        preOrder.notes?.takeIf { it.isNotBlank() }?.let { Banner(Icons.AutoMirrored.Outlined.StickyNote2, it, NoteInk) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${preOrder.items.sumOf { it.quantity }} dishes" + (preOrder.sendAt?.let { if (sent) " · in the kitchen" else " · to the kitchen at ${timeText(it, zone)}" } ?: ""),
                Modifier.weight(1f), fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ChevronCircle()
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
