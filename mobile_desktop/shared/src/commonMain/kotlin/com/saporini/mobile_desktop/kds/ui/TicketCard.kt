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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RoomService
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.RemoteImage
import com.saporini.mobile_desktop.kds.model.KdsAction
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.kds.model.KdsTicketItem
import com.saporini.mobile_desktop.kds.model.asInstant
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlin.time.Instant

/**
 * The next step for a whole ticket in [lane], when it's allowed. The kitchen's job ends at ready (user rule): serving is
 * the waiter's, so a ready ticket has no next step here.
 */
internal fun KdsTicket.nextAction(lane: Lane): KdsAction? = when (lane) {
    Lane.NEW -> KdsAction.START
    Lane.COOKING -> KdsAction.READY
    Lane.READY -> null
}?.takeIf { allows(it) }

/** The next step for one dish: start it, or mark it ready. */
internal fun KdsTicketItem.nextAction(): KdsAction? = when (status) {
    "PENDING", "FIRED" -> KdsAction.START
    "IN_PROGRESS" -> KdsAction.READY
    else -> null
}

internal fun KdsAction.buttonText(): String = when (this) {
    KdsAction.FIRE -> "Send to the kitchen"
    KdsAction.START -> "Start cooking"
    KdsAction.READY -> "Ready"
    KdsAction.COMPLETE -> "Served"
    KdsAction.BACK -> "Send back"
}

internal fun KdsAction.icon(): ImageVector = when (this) {
    KdsAction.FIRE -> Icons.Outlined.LocalFireDepartment
    KdsAction.START -> Icons.Outlined.PlayArrow
    KdsAction.READY -> Icons.Outlined.RoomService
    KdsAction.COMPLETE -> Icons.Outlined.DoneAll
    KdsAction.BACK -> Icons.AutoMirrored.Outlined.Undo
}

/** How a ticket's wait looks: grey while fine, amber getting slow (¾ of the limit), red once late. */
internal fun waitTone(minutes: Long?, limit: Int): Color = when {
    minutes == null -> Kit.Grey
    minutes >= limit -> Kit.Danger
    minutes * 4 >= limit * 3 -> Kit.Amber
    else -> Kit.Grey
}

/** "12 min", "1 h 05 min", or for food that waited days "2 d". */
internal fun waitText(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes < 24 * 60 -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min"
    else -> "${minutes / (24 * 60)} d"
}

/** Where the food goes: at a table, picked up, or delivered. Tickets don't say delivery yet, so that needs the server. */
internal enum class OrderKind(val label: String, val color: Color) {
    DINE_IN("Dine-in", Kit.Blue), TAKEAWAY("Takeaway", Kit.Purple), DELIVERY("Delivery", Kit.Green)
}

internal fun KdsTicket.kind(): OrderKind = when (orderType) {
    "DELIVERY" -> OrderKind.DELIVERY
    "TAKEAWAY" -> OrderKind.TAKEAWAY
    "DINE_IN" -> OrderKind.DINE_IN
    else -> if (tableId != null || tableName != null || tableNumber != null) OrderKind.DINE_IN else OrderKind.TAKEAWAY
}

/** Who the food is for: the table, else the guest's name, else (dine-in without a table yet) "No table yet". */
internal fun KdsTicket.forWhom(): String = when {
    tableName != null -> tableName
    tableNumber != null -> "Table $tableNumber"
    customerName != null -> customerName
    kind() == OrderKind.DINE_IN -> "No table yet"
    else -> kind().label
}

/** Everything written for the kitchen on a ticket: the occasion, the order's note and each dish's note. */
internal fun KdsTicket.kitchenNotes(): List<Pair<String?, String>> = buildList {
    occasion?.takeIf { it.isNotBlank() }?.let { add("Occasion" to it) }
    notes?.takeIf { it.isNotBlank() }?.let { add(null as String? to it) }
    items.forEach { item -> item.notes?.takeIf { it.isNotBlank() }?.let { add(item.itemNameSnapshot to it) } }
}

/** Notes the cook must read: the app's warm brown on a soft amber. */
internal val NoteInk = Color(0xFF8B5C18)
private val NoteFill = Color(0xFFFFF4E0)

/** What a card needs from the board: the dishes' photos, and what to do on a step, a note or a print. */
internal class CardHooks(
    val photos: Map<String, String?>,
    val onAction: (KdsTicket, KdsAction, String?) -> Unit,
    val onOpen: (KdsTicket) -> Unit,
    val onPrint: (KdsTicket) -> Unit,
    // Start only the dishes picked on the card (the rest stay "not started").
    val onStartDishes: (KdsTicket, List<String>) -> Unit = { _, _ -> },
    // POS → Kitchen Status (the waiter's board): nothing on a card moves food on; ready food has "Go to order" instead.
    val onGoToOrder: ((KdsTicket) -> Unit)? = null
) {
    val forWaiter get() = onGoToOrder != null
}

/**
 * One ticket on the board, laid out like the kitchen picture the user chose: the order number with where it goes, the
 * table or name, when it was fired and how long ago, and how many dishes are done; every dish with a tick, its photo,
 * quantity and options, its own status and (cooking) a Ready button; then notes, print, more and the next step.
 * [dragHandle] is the head the cook grabs to move the ticket to another column.
 */
@Composable
internal fun TicketCard(
    ticket: KdsTicket,
    lane: Lane,
    state: KdsState,
    now: Instant,
    hooks: CardHooks,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    dragHandle: Modifier = Modifier
) {
    val timing = state.posTiming
    val zone = RestaurantTime.zone
    val waited = if (lane == Lane.READY) minutesBetween(ticket.readyAt.asInstant(), now) else minutesBetween(ticket.since(), now)
    val limit = if (lane == Lane.READY) timing.readyWaitingMinutes else timing.slowAfterMinutes
    val late = (waited ?: 0) >= limit
    val strip = if (late) Kit.Danger else lane.color
    val busy = "ticket:${ticket.id}" in state.busyKeys
    val kind = ticket.kind()
    val live = ticket.items.filter { it.status != "CANCELLED" }
    val done = live.count { it.status in setOf("READY", "EXPO_READY", "COMPLETED") }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Kit.RowBorder), shadowElevation = 1.dp) {
        Column(Modifier.drawBehind { drawRect(strip, size = Size(4.dp.toPx(), size.height)) }.padding(start = 4.dp)) {
            // Head: order number and where it goes; the table or name; fired at, how long, dishes done. The cook drags it by here.
            Row(dragHandle.fillMaxWidth().then(if (hooks.forWaiter) Modifier else Modifier.clickable { hooks.onOpen(ticket) }).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("#${ticket.ticketNumber.ifBlank { ticket.orderNumber }.takeLast(6)}", fontFamily = Inter(), fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 15.sp else 16.sp, color = Kit.Ink, maxLines = 1)
                        Text(kind.label, Modifier.clip(RoundedCornerShape(50)).background(kind.color.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = kind.color, maxLines = 1)
                    }
                    Text(listOfNotNull(ticket.forWhom(),
                        ticket.guestCount?.let { if (it == 1) "1 guest" else "$it guests" },
                        ticket.stationName?.takeIf { state.stationId == null && state.boards.size > 1 }).joinToString(" · "),
                        fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val at = if (lane == Lane.READY) ticket.readyAt else ticket.firedAt ?: ticket.createdAt
                    Text(timeText(at, zone), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    val waitColor = waitTone(waited, limit).let { if (it == Kit.Grey) (if (lane == Lane.READY) Kit.Green else Kit.Ink) else it }
                    Text(when {
                        waited == null || waited == 0L -> if (lane == Lane.READY) "Just ready" else "Just fired"
                        lane == Lane.READY -> "${waitText(waited)} ready"
                        lane == Lane.NEW -> "${waitText(waited)} ago"
                        else -> waitText(waited)
                    }, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = waitColor, maxLines = 1)
                    Text("$done / ${live.size}", Modifier.clip(RoundedCornerShape(50)).background(lane.color.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 1.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = lane.color, maxLines = 1)
                }
            }
            val flags = listOfNotNull(("Rush" to Kit.Danger).takeIf { ticket.priority == "RUSH" }, ("VIP" to Kit.Purple).takeIf { ticket.priority == "VIP" })
            if (flags.isNotEmpty()) Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                flags.forEach { (text, color) -> StatusChip(text, color, if (text == "Rush") Icons.Outlined.Bolt else Icons.Outlined.Star) }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Kit.RowBorder))
            // Ticks pick dishes; the step button starts only the picked ones (user rule: picking alone starts nothing).
            var picked by remember(ticket.id) { mutableStateOf(setOf<String>()) }
            val pickable = ticket.items.filter { !hooks.forWaiter && it.status in setOf("PENDING", "FIRED") && state.canUpdate && ticket.allows(KdsAction.START, it.id) }.map { it.id }.toSet()
            LaunchedEffect(pickable) { picked = picked intersect pickable }
            val onPick: (String) -> Unit = { id -> if (id in pickable) picked = if (id in picked) picked - id else picked + id }
            // A long ticket folds: the first three dishes, then "N more dishes" to open the rest.
            var unfolded by rememberSaveable(ticket.id) { mutableStateOf(false) }
            val folds = ticket.items.size > 4
            val shown = if (folds && !unfolded) ticket.items.take(3) else ticket.items
            Column(Modifier.padding(start = 9.dp, end = 8.dp, top = 4.dp, bottom = 2.dp)) {
                shown.forEach { item -> DishLine(item, ticket, lane, state, now, hooks, compact, busy, item.id in picked, onPick) }
                if (folds) {
                    val rest = ticket.items.drop(3)
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).clickable { unfolded = !unfolded }
                        .background(Kit.Canvas).padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (unfolded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Green)
                        Text(if (unfolded) "Show fewer dishes" else "${rest.size} more ${if (rest.size == 1) "dish" else "dishes"}",
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
                        if (!unfolded) Text(rest.joinToString(", ") { it.itemNameSnapshot }, Modifier.weight(1f), fontFamily = Inter(), fontSize = 11.sp,
                            color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // Foot: the notes, print, more; and the step that moves the whole ticket on.
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NotesButton(ticket, if (hooks.forWaiter) null else ({ hooks.onOpen(ticket) }))
                if (!hooks.forWaiter) {
                    FootButton(Icons.Outlined.Print, "Print the ticket") { hooks.onPrint(ticket) }
                    TicketMenu(ticket, lane, state, busy, hooks)
                }
                Spacer(Modifier.weight(1f))
                val action = ticket.nextAction(lane)
                // The waiter serves ready food from its order (user, 2026-10-31), even when only one dish is ready.
                if (hooks.forWaiter) { if (lane == Lane.READY) GoToOrderButton { hooks.onGoToOrder?.invoke(ticket) } }
                else if (picked.isNotEmpty()) PickedStartButton(picked.size, busy) { hooks.onStartDishes(ticket, ticket.items.map { it.id }.filter { it in picked }); picked = emptySet() }
                else if (state.canUpdate && action != null) StepButton(action, busy) { hooks.onAction(ticket, action, null) }
                // Ready food waits for the waiter, who takes it out (and marks it served) from the POS: the kitchen is done.
                else if (lane == Lane.READY) Row(Modifier.height(34.dp).clip(RoundedCornerShape(9.dp)).background(Kit.GreenSoft).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.RoomService, null, Modifier.size(16.dp), tint = Kit.Green)
                    Text("Ready to pick up", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Green, maxLines = 1)
                }
            }
        }
    }
}

/** One dish: a tick (started or done), its photo, quantity, name and options, its status, and Ready while it cooks. */
@Composable
private fun DishLine(
    item: KdsTicketItem, ticket: KdsTicket, lane: Lane, state: KdsState, now: Instant, hooks: CardHooks, compact: Boolean, busy: Boolean,
    picked: Boolean, onPick: (String) -> Unit
) {
    val ready = item.status in setOf("READY", "EXPO_READY", "COMPLETED")
    val cancelled = item.status == "CANCELLED"
    val cooking = item.status == "IN_PROGRESS"
    val step = item.nextAction()?.takeIf { !hooks.forWaiter && state.canUpdate && ticket.allows(it, item.id) && !busy }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(if (cancelled) 0.45f else 1f), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // The tick: empty to do (tap to pick it), green outline picked, amber cooking, green ready. It's the kitchen's;
        // the waiter's board has no ticks (user, 2026-10-31), the status pill says it all.
        val tickColor = when { ready -> Kit.Green; cooking -> Kit.Amber; else -> Kit.Faint }
        if (!hooks.forWaiter) Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(when { ready || cooking -> tickColor; picked -> Kit.GreenSoft; else -> Color.White })
            .border(1.5.dp, when { ready || cooking -> tickColor; picked -> Kit.Green; else -> Kit.Border }, RoundedCornerShape(5.dp))
            .then(if (step == KdsAction.START) Modifier.clickable { onPick(item.id) } else Modifier), Alignment.Center) {
            if (ready || cooking || picked) Icon(Icons.Outlined.Check, when { ready -> "Ready"; cooking -> "Cooking"; else -> "Picked" }, Modifier.size(12.dp),
                tint = if (picked && !ready && !cooking) Kit.Green else Color.White)
        }
        DishPhoto(item.menuItemId?.let { hooks.photos[it] }, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${item.quantity} × ", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
                Text(item.itemNameSnapshot, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = if (ready) Kit.Muted else Kit.Ink, textDecoration = if (cancelled) TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!item.notes.isNullOrBlank()) Icon(Icons.AutoMirrored.Outlined.StickyNote2, "Has a note", Modifier.padding(start = 5.dp).size(13.dp), tint = NoteInk)
            }
            Text(item.detailLine() ?: "Regular", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // A narrow column keeps the dish name whole: the status goes under it.
            if (compact) DishStatus(item, ticket, lane, state, now, hooks.forWaiter, inline = true)
        }
        if (!compact) DishStatus(item, ticket, lane, state, now, hooks.forWaiter)
        if (cooking && step == KdsAction.READY) {
            Surface(onClick = { hooks.onAction(ticket, KdsAction.READY, item.id) }, shape = RoundedCornerShape(8.dp), color = Kit.Green) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = Color.White)
                    Text("Ready", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Color.White)
                }
            }
        }
    }
}

/** A dish's status as a soft pill: to start, cooking (with how long and a bar towards the late limit), or ready. */
@Composable
private fun DishStatus(item: KdsTicketItem, ticket: KdsTicket, lane: Lane, state: KdsState, now: Instant, waiter: Boolean, inline: Boolean = false) {
    val (label, color, icon) = when (item.status) {
        "IN_PROGRESS" -> Triple("Cooking", Kit.Amber, Icons.Outlined.Schedule)
        "READY", "EXPO_READY", "COMPLETED" -> Triple("Ready", Kit.Green, Icons.Filled.CheckCircle)
        "CANCELLED" -> Triple("Cancelled", Kit.Grey, Icons.Outlined.Restaurant)
        // A dish left out when the others started cooking: red until it starts too. A waiter's order card holds every
        // station's dishes, so there it's only not started yet.
        else -> if (waiter) Triple("Not started", Kit.Blue, Icons.Outlined.Schedule)
            else if (lane != Lane.NEW) Triple("Not started", Kit.Danger, Icons.Outlined.Schedule)
            else Triple("To start", Kit.Blue, Icons.Outlined.LocalFireDepartment)
    }
    val pill: @Composable () -> Unit = {
        Row(Modifier.clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.11f)).padding(horizontal = 7.dp, vertical = if (inline) 2.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, null, Modifier.size(12.dp), tint = color)
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = color, maxLines = 1)
        }
    }
    val time: @Composable () -> Unit = {
        when (item.status) {
            "IN_PROGRESS" -> {
                val minutes = minutesBetween(ticket.startedAt.asInstant() ?: ticket.since(), now) ?: 0
                val limit = state.posTiming.slowAfterMinutes.coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    // In a tight row the bar gives way, so the time never breaks over two lines.
                    Box(Modifier.weight(1f, fill = false).width(36.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Kit.Amber.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((minutes.toFloat() / limit).coerceIn(0.06f, 1f)).clip(RoundedCornerShape(2.dp))
                            .background(if (minutes >= limit) Kit.Danger else Kit.Amber))
                    }
                    Text("${minutes}m", fontFamily = Inter(), fontSize = 10.5.sp, color = Kit.Muted, maxLines = 1, softWrap = false)
                }
            }
            "READY", "EXPO_READY" -> minutesBetween(item.readyAt.asInstant() ?: ticket.readyAt.asInstant(), now)?.let {
                Text("${it}m", fontFamily = Inter(), fontSize = 10.5.sp, color = Kit.Muted)
            }
        }
    }
    if (inline) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { pill(); time() }
    else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { pill(); time() }
}

/** The step that moves the whole ticket on: the app's green (served is white), a size smaller than a page's buttons. */
@Composable
private fun StepButton(action: KdsAction, busy: Boolean, onClick: () -> Unit) {
    val served = action == KdsAction.COMPLETE
    Surface(onClick = onClick, enabled = !busy, modifier = Modifier.height(34.dp), shape = RoundedCornerShape(9.dp),
        color = if (served) Color.White else Kit.Green, border = if (served) BorderStroke(1.dp, Kit.Border) else null) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val tint = if (served) Kit.Ink else Color.White
            if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), color = tint, strokeWidth = 2.dp)
            else Icon(action.icon(), null, Modifier.size(16.dp), tint = tint)
            Text(if (busy) "Saving…" else action.buttonText(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = tint, maxLines = 1)
        }
    }
}

/** On the waiter's board: opens the order in POS → Orders, where its ready food is served. */
@Composable
private fun GoToOrderButton(onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.height(34.dp), shape = RoundedCornerShape(9.dp), color = Kit.Green) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Go to order", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color.White, maxLines = 1)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = Color.White)
        }
    }
}

/** "Start 2 dishes": starts only the dishes ticked on the card. */
@Composable
private fun PickedStartButton(count: Int, busy: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !busy, modifier = Modifier.height(34.dp), shape = RoundedCornerShape(9.dp), color = Kit.Green) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(16.dp), tint = Color.White)
            Text("Start $count ${if (count == 1) "dish" else "dishes"}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
                color = Color.White, maxLines = 1)
        }
    }
}

/** A dish's photo from the menu, or a soft tile with a plate when there's none. */
@Composable
internal fun DishPhoto(url: String?, size: Dp) {
    RemoteImage(url, null, Modifier.size(size).clip(RoundedCornerShape(10.dp))) {
        Box(Modifier.fillMaxSize().background(Kit.Tint), Alignment.Center) { Icon(Icons.Outlined.Restaurant, null, Modifier.size(size * 0.42f), tint = Kit.Faint) }
    }
}

/** The notes button (with how many) and its popup: the occasion, the order's note and each dish's note. */
@Composable
private fun NotesButton(ticket: KdsTicket, onOpen: (() -> Unit)?) {
    val notes = ticket.kitchenNotes()
    var open by remember { mutableStateOf(false) }
    Box {
        FootButton(Icons.Outlined.ChatBubbleOutline, "Notes", notes.size) { open = true }
        DropdownMenu(open, { open = false }, Modifier.widthIn(min = 240.dp, max = 340.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White,
            shadowElevation = 8.dp) {
            Text("Notes", Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                fontSize = 13.sp, color = Kit.Ink)
            if (notes.isEmpty()) Text("Nothing written for the kitchen.", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontFamily = Inter(),
                fontSize = 12.sp, color = Kit.Muted)
            notes.forEach { (about, text) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (about == "Occasion") Kit.Purple.copy(alpha = 0.08f) else NoteFill).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (about == "Occasion") Icons.Outlined.Celebration else Icons.AutoMirrored.Outlined.StickyNote2, null, Modifier.size(15.dp),
                        tint = if (about == "Occasion") Kit.Purple else NoteInk)
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(about ?: "The order", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (about == "Occasion") Kit.Purple else NoteInk)
                        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Ink)
                    }
                }
            }
            if (onOpen != null) Text("Open the ticket", Modifier.padding(horizontal = 14.dp, vertical = 8.dp).clip(RoundedCornerShape(6.dp)).clickable { open = false; onOpen() },
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Green)
        }
    }
}

@Composable
private fun FootButton(icon: ImageVector, description: String, badge: Int = 0, onClick: () -> Unit) {
    Box {
        Surface(onClick = onClick, modifier = Modifier.size(34.dp), shape = RoundedCornerShape(9.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(17.dp), tint = Kit.Ink) }
        }
        if (badge > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp).size(16.dp).clip(CircleShape).background(Kit.Grey)
            .border(1.5.dp, Color.White, CircleShape), Alignment.Center) {
            Text("$badge", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.White)
        }
    }
}

/** ⋯ on a ticket: open it, and every step the whole ticket can take from here. */
@Composable
private fun TicketMenu(ticket: KdsTicket, lane: Lane, state: KdsState, busy: Boolean, hooks: CardHooks) {
    var open by remember { mutableStateOf(false) }
    Box {
        FootButton(Icons.Outlined.MoreHoriz, "More") { open = true }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
            val entries = buildList {
                add(Triple("Open the ticket", Icons.AutoMirrored.Outlined.StickyNote2) { hooks.onOpen(ticket) })
                if (state.canUpdate && !busy) {
                    if (lane == Lane.NEW && ticket.allows(KdsAction.START)) add(Triple("Start cooking", KdsAction.START.icon()) { hooks.onAction(ticket, KdsAction.START, null) })
                    if (lane != Lane.READY && ticket.allows(KdsAction.READY)) add(Triple("Everything ready", KdsAction.READY.icon()) { hooks.onAction(ticket, KdsAction.READY, null) })
                    if (lane != Lane.NEW && ticket.allows(KdsAction.BACK)) add(Triple(if (lane == Lane.READY) "Back to cooking" else "Back to new",
                        KdsAction.BACK.icon()) { hooks.onAction(ticket, KdsAction.BACK, null) })
                }
                add(Triple("Print the ticket", Icons.Outlined.Print) { hooks.onPrint(ticket) })
            }
            entries.forEach { (text, icon, run) ->
                DropdownMenuItem(text = { Text(text, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink) },
                    leadingIcon = { Icon(icon, null, Modifier.size(18.dp), tint = Kit.Ink) }, onClick = { open = false; run() })
            }
        }
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
