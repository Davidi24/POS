package com.saporini.mobile_desktop.kds.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.OutdoorGrill
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import com.saporini.mobile_desktop.admin.inventory.ui.OptionGroup
import com.saporini.mobile_desktop.admin.inventory.ui.OptionLine
import com.saporini.mobile_desktop.admin.inventory.ui.PhoneOptionsButton
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.saporini.mobile_desktop.core.components.BottomToast
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.ToastBackground
import com.saporini.mobile_desktop.core.components.ToastYellow
import com.saporini.mobile_desktop.core.components.ToolbarIconButton
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.PlatformVerticalScrollbar
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.kds.KdsScreenModel
import com.saporini.mobile_desktop.kds.model.KdsAction
import com.saporini.mobile_desktop.kds.model.KdsAllDayItem
import com.saporini.mobile_desktop.kds.model.KdsConnection
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Instant

// The cooks' board as a kanban (user, 2026-10-29, from their picture): one bar (where the food goes: All, Dine-in,
// Takeaway, Delivery with counts; the station, search; kitchen online, the clock; Prep list, stations, refresh), then the
// three columns New, Cooking and Ready to pick up. A column folds to a thin rail and back. Tickets move on with their
// button or by dragging them to the next column (mouse: drag; touch: hold, then drag), and they fly across when they do.
// "Prep list" (what was All day) is a sidebar that slides in from the window's right edge over the page, only from its
// button; the columns stay as they are under it.

// The waiter's columns are Sent to kitchen, Cooking and Ready to serve (user, 2026-10-31).
private fun Lane.heading(waiter: Boolean = false): String = when (this) {
    Lane.NEW -> if (waiter) "Sent to kitchen" else "New"
    Lane.COOKING -> "Cooking"
    Lane.READY -> if (waiter) "Ready to serve" else "Ready to pick up"
}

private fun Lane.caption(waiter: Boolean): String = when (this) {
    Lane.NEW -> if (waiter) "not started yet" else "fired, waiting to start"
    Lane.COOKING -> "on the stove"
    Lane.READY -> if (waiter) "waiting for you" else "waiting for the waiter"
}

/**
 * POS → Kitchen Status: this same board for the waiters (user, 2026-10-31: "almost identical to the kitchen, just without
 * its buttons"). One card per order ([tickets], from `orderTickets`), nothing to drag or step on, no prep list; a card is in
 * Ready to serve once one of its dishes is, with "Go to order". [me] is the signed-in person, for "My orders".
 */
internal class WaiterBoard(val tickets: List<KdsTicket>, val me: String?, val onGoToOrder: (String) -> Unit)

/**
 * The steps a ticket takes when it's dropped from [from] onto [to]: forward one column (start, ready) or back one or two
 * steps when it was moved on by mistake. Empty when it stays in its column (it's only moved to another place in the list).
 * Null when it can't go there: a new ticket goes to Cooking before Ready to pick up (user rule, 2026-10-31).
 */
private fun dropSteps(ticket: KdsTicket, from: Lane, to: Lane): List<KdsAction>? = when {
    to == from -> emptyList()
    to.ordinal == from.ordinal + 1 -> (if (to == Lane.COOKING) KdsAction.START else KdsAction.READY).takeIf { ticket.allows(it) }?.let { listOf(it) }
    to.ordinal > from.ordinal -> null
    ticket.allows(KdsAction.BACK) -> List(from.ordinal - to.ordinal) { KdsAction.BACK }
    else -> null
}

/** Where a step takes the ticket: the column it lands in, or null when it leaves the board (served). */
private fun landing(ticket: KdsTicket, action: KdsAction, itemId: String?): Lane? {
    val after = ticket.copy(items = ticket.items.map { item ->
        if (itemId != null && item.id != itemId) item
        else when (action) {
            KdsAction.START -> if (item.status in setOf("PENDING", "FIRED")) item.copy(status = "IN_PROGRESS") else item
            KdsAction.READY -> if (item.status in setOf("PENDING", "FIRED", "IN_PROGRESS")) item.copy(status = "READY") else item
            else -> item
        }
    }, status = if (itemId == null) when (action) { KdsAction.START -> "IN_PROGRESS"; KdsAction.READY -> "READY"; else -> ticket.status } else ticket.status)
    if (action == KdsAction.BACK) return when (ticket.lane()) { Lane.READY -> Lane.COOKING; else -> Lane.NEW }
    return if (action == KdsAction.COMPLETE && itemId == null) null else after.lane()
}

/** The board's Show filter (in the filter menu with the stations). */
private enum class BoardShow(val label: String) { ALL("Everything"), LATE("Late only"), PRIORITY("Rush and VIP") }

private data class Dragging(val ticket: KdsTicket, val from: Lane, val pointer: Offset, val grab: Offset, val size: Size)

/**
 * A card on its way: straight from where it was to its new place (no tilt, no arc, user rule). With [to] null it goes to
 * wherever its card now sits on the board (the empty slot at the top of the column it moved to); [leaving] fades it out.
 */
private class Flight(val ticket: KdsTicket, val lane: Lane, val from: Rect, val to: Offset?, val leaving: Boolean, val fallback: Offset) {
    val progress = Animatable(0f)
}

/** Where the board's pieces are on screen (root coordinates), what's being dragged, and the cards in the air. */
private class BoardMotion {
    var origin by mutableStateOf(Offset.Zero)
    val lanes = mutableStateMapOf<Lane, Rect>()
    val cards = HashMap<String, Rect>()
    var drag by mutableStateOf<Dragging?>(null)
    val flights = mutableStateListOf<Flight>()
    val hidden = mutableStateMapOf<String, Boolean>()
    // A ticket moved to another column goes to its top (newest move first) and pushes the others down.
    val toTop = mutableStateMapOf<String, Long>()
    // A ticket that just came in from the waiters shakes once at the end of its column.
    val arrived = mutableStateMapOf<String, Long>()
    // Where a moved ticket shows until the change is saved, so its new column makes room at once (the others shift down
    // by its height) while the card travels into that room.
    val pending = mutableStateMapOf<String, Lane>()
    // The order the cook put a column in (by dragging, or a moved ticket at the top); tickets not in it follow it.
    val order = mutableStateMapOf<Lane, List<String>>()
    // Each column's list, so a ticket put at the top of a column scrolled down can bring that top into view first.
    val lists = HashMap<Lane, androidx.compose.foundation.lazy.LazyListState>()
    var stamp = 0L

    /** The top of [lane]'s list: shown at once (a column scrolled down jumps to its top), and where a card heads first. */
    fun revealTop(lane: Lane, density: androidx.compose.ui.unit.Density): Offset {
        lists[lane]?.requestScrollToItem(0)
        val rect = lanes[lane] ?: return Offset.Zero
        return with(density) { Offset(rect.left + 12.dp.toPx(), rect.top + 86.dp.toPx()) }
    }

    /** Puts [id] at [index] of [lane]'s list as shown now ([shown], without [id]). */
    fun place(id: String, lane: Lane, index: Int, shown: List<String>) {
        val list = shown.filter { it != id }.toMutableList()
        list.add(index.coerceIn(0, list.size), id)
        order[lane] = list
        Lane.entries.filter { it != lane }.forEach { other -> order[other]?.let { order[other] = it - id } }
    }

    /**
     * Moves [ticket]'s card from [start] to [to] (null: to its own slot, wherever that now is); it shows again in its
     * place once it has arrived and the change is saved ([job]).
     */
    fun fly(scope: CoroutineScope, ticket: KdsTicket, lane: Lane, start: Rect, to: Offset?, leaving: Boolean, job: Job?, fallback: Offset = start.topLeft) {
        val flight = Flight(ticket, lane, start, to, leaving, fallback)
        // Its slot reports where it is once the new column has laid it out; until then the flight heads for [fallback].
        if (to == null) cards.remove(ticket.id)
        hidden[ticket.id] = true
        flights += flight
        scope.launch {
            flight.progress.animateTo(1f, tween(if (leaving) 260 else 420, easing = FastOutSlowInEasing))
            withTimeoutOrNull(6_000) { job?.join() }
            flights.remove(flight)
            hidden.remove(ticket.id)
            pending.remove(ticket.id)
        }
    }
}

/** Where in a column ([ids], as shown) a card let go at [y] lands: before the first card whose middle is below it. */
private fun dropIndex(motion: BoardMotion, ids: List<String>, dragged: String, y: Float): Int =
    ids.filter { it != dragged }.count { id -> motion.cards[id]?.let { it.center.y < y } ?: false }

/** The lanes reach the bottom edge, so only their top corners are round. */
private val LaneShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val RailWidth = 64.dp
private val PrepWidth = 360.dp

@Composable
internal fun TicketsSection(
    state: KdsState, model: KdsScreenModel, size: ScreenSize, now: Instant, onStations: (() -> Unit)?, startPrepList: Boolean = false,
    // The page's padding around the board, which the prep list runs past to reach the window's edge.
    pageSide: Dp = 0.dp, pageTop: Dp = 0.dp,
    waiter: WaiterBoard? = null
) {
    var prepOpen by rememberSaveable { mutableStateOf(startPrepList) }
    var kindFilter by rememberSaveable { mutableStateOf<OrderKind?>(null) }
    var show by rememberSaveable { mutableStateOf(BoardShow.ALL) }
    // A waiter looks for the food to take out first.
    var phoneLane by rememberSaveable { mutableStateOf(if (waiter != null) Lane.READY else Lane.NEW) }
    var mine by rememberSaveable { mutableStateOf(false) }
    var folded by remember { mutableStateOf(setOf<Lane>()) }
    var localNotice by remember { mutableStateOf<String?>(null) }
    var localWarning by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val motion = remember { BoardMotion() }
    // Stations seen on the board (name and open tickets), so the menu still lists them all while one is chosen.
    val known = remember(state.scope) { mutableStateMapOf<String, Pair<String, Int>>() }
    LaunchedEffect(state.boards, state.stationId) {
        state.boards.forEach { board ->
            val name = board.stationName.ifBlank { board.stationCode ?: "Station" }
            known[board.stationId] = name to board.tickets.count { !it.terminal }
        }
    }
    // The order types the restaurant takes (Admin Hub → Settings → Order types). Dine-in only: no tabs at all.
    val kinds = buildList {
        add(OrderKind.DINE_IN)
        if (state.posTiming.takeawayEnabled) add(OrderKind.TAKEAWAY)
        if (state.posTiming.deliveryEnabled) add(OrderKind.DELIVERY)
    }
    val chosenKind = kindFilter?.takeIf { kinds.size > 1 && it in kinds }
    val all = waiter?.let { w -> w.tickets.filter { !mine || it.createdBy == w.me } } ?: state.visibleTickets
    val tickets = all.filter { ticket ->
        (chosenKind == null || ticket.kind() == chosenKind) && when (show) {
            BoardShow.ALL -> true
            BoardShow.PRIORITY -> ticket.priority in setOf("RUSH", "VIP")
            BoardShow.LATE -> (minutesBetween(ticket.since(), now) ?: 0) >= state.posTiming.slowAfterMinutes
        }
    }
    val byLane = Lane.entries.associateWith { lane ->
        val here = tickets.filter { (motion.pending[it.id] ?: it.lane()) == lane }
        val placed = motion.order[lane].orEmpty()
        // The cook's order first (dragged, or moved here at the top); new tickets after it, oldest first.
        here.sortedWith(compareBy<KdsTicket> { placed.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } })
    }
    // New tickets from the waiters (not the first load, not a station switch) shake once when they come in.
    val seen = remember(state.scope, state.stationId) { mutableSetOf<String>() }
    var primed by remember(state.scope, state.stationId) { mutableStateOf(false) }
    val openIds = state.tickets.filter { !it.terminal }.map { if (waiter != null) it.orderId else it.id }.toSet()
    LaunchedEffect(openIds, state.loaded) {
        if (!state.loaded) return@LaunchedEffect
        if (primed) openIds.filter { it !in seen && it !in motion.toTop }.forEach { motion.arrived[it] = ++motion.stamp }
        seen += openIds
        primed = true
    }
    val photos = remember(state.menu.items) { state.menu.items.associate { it.item.id to it.item.imageUrl } }
    val phone = size.isPhone

    // A step from a button: the card flies to the column it lands in (or off the board when served).
    fun step(ticket: KdsTicket, action: KdsAction, itemId: String?) {
        val from = ticket.lane()
        val to = landing(ticket, action, itemId)
        val job = model.perform(ticket.id, action, itemId)
        if (job != null && to != null && to != from) motion.place(ticket.id, to, 0, byLane.getValue(to).map { it.id })
        if (job == null || phone) return
        val start = motion.cards[ticket.id] ?: return
        when {
            // Served: it fades where it is, and the cards under it move up.
            to == null -> motion.fly(scope, ticket, from, start, start.topLeft, leaving = true, job)
            to != from -> {
                motion.pending[ticket.id] = to
                motion.fly(scope, ticket, from, start, null, leaving = false, job, motion.revealTop(to, density))
            }
        }
    }
    fun startDishes(ticket: KdsTicket, ids: List<String>) {
        if (ids.isEmpty()) return
        val from = ticket.lane()
        val job = scope.launch { for (id in ids) model.perform(ticket.id, KdsAction.START, id)?.join() }
        if (from != Lane.NEW) return
        motion.place(ticket.id, Lane.COOKING, 0, byLane.getValue(Lane.COOKING).map { it.id })
        if (phone) return
        val start = motion.cards[ticket.id] ?: return
        motion.pending[ticket.id] = Lane.COOKING
        motion.fly(scope, ticket, from, start, null, leaving = false, job, motion.revealTop(Lane.COOKING, density))
    }
    fun drop(d: Dragging) {
        val target = motion.lanes.entries.firstOrNull { it.value.contains(d.pointer) }?.key
        val steps = target?.let { dropSteps(d.ticket, d.from, it) }
        val ghost = Rect(d.pointer - d.grab, d.size)
        if (target != null && steps != null) {
            // It lands where it was let go, not at the top (user rule).
            motion.place(d.ticket.id, target, dropIndex(motion, byLane.getValue(target).map { it.id }, d.ticket.id, d.pointer.y), byLane.getValue(target).map { it.id })
            val job = if (steps.isEmpty()) null else scope.launch { for (step in steps) model.perform(d.ticket.id, step)?.join() }
            if (target != d.from) motion.pending[d.ticket.id] = target
            motion.fly(scope, d.ticket, d.from, ghost, null, leaving = false, job, ghost.topLeft)
        } else {
            // Not a step it can take: back to where it was.
            motion.cards[d.ticket.id]?.let { home -> motion.fly(scope, d.ticket, d.from, ghost, home.topLeft, leaving = false, null) }
            if (target != null) localWarning = if (d.from == Lane.NEW && target == Lane.READY) "You can't put it here. Start cooking it first." else "You can't put it here."
        }
    }
    val hooks = if (waiter != null) CardHooks(photos, { _, _, _ -> }, {}, {}, onGoToOrder = { waiter.onGoToOrder(it.orderId) })
        else CardHooks(photos, onAction = { t, a, i -> step(t, a, i) }, onOpen = { model.selectTicket(it.id) },
            onPrint = { localNotice = "Kitchen printing comes once a printer is connected (Admin Hub → Devices)." },
            onStartDishes = { t, ids -> startDishes(t, ids) })
    val idle = remember(photos) { CardHooks(photos, { _, _, _ -> }, {}, {}) }

    Box(Modifier.fillMaxSize().onGloballyPositioned { motion.origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 14.dp)) {
            BoardBar(state, model, size, now, known, all, kinds, chosenKind, { kindFilter = it }, show, { show = it }, prepOpen, { prepOpen = !prepOpen }, onStations,
                waiter = waiter != null, mine = mine, onMine = { mine = it })
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val sideBySide = size.isDesktop || maxWidth >= 860.dp
                val narrowCards = maxWidth < 420.dp
                if (!sideBySide) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Short names on the tabs; the column's head under them says it in full.
                        val tab: (Lane) -> String = { if (waiter != null && it == Lane.NEW) "Sent" else it.title }
                        OverviewTabs(Lane.entries.map { "${tab(it)} ${byLane.getValue(it).size}" }, "${tab(phoneLane)} ${byLane.getValue(phoneLane).size}",
                            { label -> phoneLane = Lane.entries.first { label.startsWith(tab(it)) } }, Modifier.fillMaxWidth())
                        LaneColumn(phoneLane, byLane.getValue(phoneLane), state, now, motion, hooks, Modifier.weight(1f).fillMaxWidth(), folded = false,
                            canFold = false, onFold = {}, compact = narrowCards, drag = null, waiter = waiter != null) { _, _ -> Modifier }
                    }
                } else {
                    val gap = 12.dp
                    val open = Lane.entries.filter { it !in folded }
                    val room = maxWidth - gap * 2 - RailWidth * folded.size
                    Row(Modifier.fillMaxSize()) {
                        Lane.entries.forEachIndexed { index, lane ->
                            val width by animateDpAsState(if (lane in folded) RailWidth else room / open.size.coerceAtLeast(1), tween(320))
                            if (index > 0) Spacer(Modifier.width(gap))
                            LaneColumn(lane, byLane.getValue(lane), state, now, motion, hooks, Modifier.width(width).fillMaxHeight(), folded = lane in folded,
                                canFold = lane in folded || open.size > 1, onFold = { folded = if (lane in folded) folded - lane else folded + lane },
                                compact = width < 400.dp, drag = motion.drag, waiter = waiter != null) { ticket, from ->
                                // The waiter's board moves nothing: cards stay where the kitchen puts them.
                                if (waiter != null) Modifier else Modifier.pointerInput(ticket.id, from) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        // A mouse drags at once; a finger holds first, so the list still scrolls.
                                        val start = if (down.type == PointerType.Touch) awaitLongPressOrCancellation(down.id)
                                            else awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                                        if (start != null && state.canUpdate) {
                                            val card = motion.cards[ticket.id] ?: return@awaitEachGesture
                                            motion.drag = Dragging(ticket, from, card.topLeft + start.position, start.position, card.size)
                                            drag(start.id) { change ->
                                                motion.drag = motion.drag?.let { it.copy(pointer = it.pointer + change.positionChange()) }
                                                change.consume()
                                            }
                                            motion.drag?.let { drop(it) }
                                            motion.drag = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // A real sidebar (user, 2026-10-31): flush with the window's right edge, from under the nav bar to the bottom, no corners.
        if (waiter == null) BoxWithConstraints(Modifier.fillMaxSize()) {
            PrepSidebar(prepOpen, state.allDay, photos, Modifier.align(Alignment.TopEnd).offset(x = pageSide, y = -pageTop)
                .wrapContentHeight(Alignment.Top, unbounded = true).height(maxHeight + pageTop).width(PrepWidth.coerceAtMost(maxWidth + pageSide))) { prepOpen = false }
        }

        // The card being dragged follows the pointer; cards in the air go straight to where they belong.
        val cardWidth: (Rect) -> androidx.compose.ui.unit.Dp = { with(density) { it.width.toDp() } }
        motion.drag?.let { d ->
            val at = d.pointer - d.grab - motion.origin
            // Over a column it can't go to, the card itself says so: it's under the cook's eyes wherever they drag it.
            val over = motion.lanes.entries.firstOrNull { it.value.contains(d.pointer) }?.key
            val refused = over != null && dropSteps(d.ticket, d.from, over) == null
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.width(cardWidth(Rect(Offset.Zero, d.size)))) {
                Box(Modifier.graphicsLayer { shadowElevation = 16f; alpha = if (refused) 0.9f else 0.97f }) {
                    TicketCard(d.ticket, d.from, state, now, idle, compact = cardWidth(Rect(Offset.Zero, d.size)) < 380.dp)
                }
                if (refused) {
                    Box(Modifier.matchParentSize().border(2.5.dp, ToastYellow, RoundedCornerShape(14.dp)))
                    CantGoBadge(Modifier.align(Alignment.TopCenter).offset(y = (-16).dp))
                }
            }
        }
        motion.drag?.let { d ->
            val lane = motion.lanes.entries.firstOrNull { it.value.contains(d.pointer) } ?: return@let
            if (dropSteps(d.ticket, d.from, lane.key) == null) return@let
            val ids = byLane.getValue(lane.key).map { it.id }.filter { it != d.ticket.id }
            val index = dropIndex(motion, ids, d.ticket.id, d.pointer.y)
            val gap = with(density) { 6.dp.toPx() }
            val y = when {
                ids.isEmpty() -> lane.value.top + with(density) { 76.dp.toPx() }
                index < ids.size -> (motion.cards[ids[index]]?.top ?: lane.value.top) - gap
                else -> (motion.cards[ids.last()]?.bottom ?: lane.value.top) + gap
            } - motion.origin.y
            val left = lane.value.left + with(density) { 12.dp.toPx() } - motion.origin.x
            Box(Modifier.offset { IntOffset(left.roundToInt(), (y - with(density) { 1.5.dp.toPx() }).roundToInt()) }
                .width(with(density) { (lane.value.width - 26.dp.toPx()).toDp() }).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Kit.Green))
        }
        motion.flights.forEach { flight ->
            val p = flight.progress.value
            val start = flight.from.topLeft - motion.origin
            // Its slot is read every frame, so it lands exactly where the column made room for it.
            val end = (flight.to ?: motion.cards[flight.ticket.id]?.topLeft ?: flight.fallback) - motion.origin
            val x = start.x + (end.x - start.x) * p
            val y = start.y + (end.y - start.y) * p
            Box(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(cardWidth(flight.from))
                .graphicsLayer { shadowElevation = if (flight.leaving) 0f else 10f; alpha = if (flight.leaving) 1f - p else 1f }) {
                TicketCard(flight.ticket, flight.lane, state, now, idle, compact = cardWidth(flight.from) < 380.dp)
            }
        }
    }
    BottomToast(localWarning ?: localNotice, isWarning = localWarning != null, onDismiss = { localWarning = null; localNotice = null })
}

/** On a dragged card over a column it can't go to: the notification's look, small. */
@Composable
private fun CantGoBadge(modifier: Modifier) {
    Row(modifier.shadow(8.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(ToastBackground).padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Block, null, Modifier.size(16.dp), tint = ToastYellow)
        Text("You can't put it here", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color.White, maxLines = 1)
    }
}

/** The prep list on top of the page: it comes in from the right edge and takes no room from the columns. */
@Composable
private fun PrepSidebar(visible: Boolean, items: List<KdsAllDayItem>, photos: Map<String, String?>, modifier: Modifier, onClose: () -> Unit) {
    AnimatedVisibility(visible, modifier, enter = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(160)),
        exit = slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))) {
        PrepList(items, photos, Modifier.fillMaxSize(), onClose)
    }
}

// ---------- The bar ----------

@Composable
private fun BoardBar(
    state: KdsState, model: KdsScreenModel, size: ScreenSize, now: Instant, known: Map<String, Pair<String, Int>>, all: List<KdsTicket>,
    kinds: List<OrderKind>, chosenKind: OrderKind?, onKind: (OrderKind?) -> Unit, show: BoardShow, onShow: (BoardShow) -> Unit,
    prepOpen: Boolean, onPrep: () -> Unit, onStations: (() -> Unit)?,
    waiter: Boolean = false, mine: Boolean = false, onMine: (Boolean) -> Unit = {}
) {
    var searching by remember { mutableStateOf(false) }
    val lead: @Composable () -> Unit = {
        if (kinds.size > 1) KindSwitch(kinds, chosenKind, all, onKind)
        else Column {
            Text(if (waiter) "Kitchen Status" else "Kitchen", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 22.sp, color = Kit.Ink)
            val what = if (waiter) (if (all.size == 1) "order" else "orders") else (if (all.size == 1) "ticket" else "tickets")
            Text("${all.size} open $what", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
        }
    }
    val search: @Composable (Modifier) -> Unit = { m ->
        if (searching || state.filter.query.isNotBlank()) SearchField(state.filter.query, { model.setFilter(state.filter.copy(query = it)) }, m,
            placeholder = "Table, dish or order", height = 40.dp)
        else ToolbarIconButton(Icons.Outlined.Search, if (waiter) "Search orders" else "Search tickets") { searching = true }
    }
    val filters: @Composable () -> Unit = {
        val active = (if (state.stationId != null) 1 else 0) + (if (show != BoardShow.ALL) 1 else 0) + (if (mine) 1 else 0)
        PhoneOptionsButton(Icons.Outlined.FilterList, "Filters", active, 40.dp) { close ->
            if (waiter) {
                OptionGroup("Whose orders")
                OptionLine("All orders", !mine, { onMine(false); close() }, icon = Icons.Outlined.Receipt)
                OptionLine("My orders", mine, { onMine(true); close() }, icon = Icons.Outlined.Person)
            }
            if (known.size > 1 || state.stationId != null) {
                OptionGroup("Station")
                OptionLine("All stations", state.stationId == null, { model.selectStation(null); close() }, icon = Icons.Outlined.Kitchen,
                    count = known.values.sumOf { it.second })
                known.entries.sortedBy { it.value.first }.forEach { (id, info) ->
                    OptionLine(info.first, state.stationId == id, { model.selectStation(id); close() }, icon = Icons.Outlined.OutdoorGrill, count = info.second)
                }
            }
            OptionGroup("Show")
            BoardShow.entries.forEach { option ->
                OptionLine(option.label, show == option, { onShow(option); close() },
                    icon = when (option) { BoardShow.ALL -> Icons.Outlined.Receipt; BoardShow.LATE -> Icons.Outlined.Timer; BoardShow.PRIORITY -> Icons.Outlined.Bolt },
                    color = if (option == BoardShow.LATE) Kit.Danger else Kit.Green)
            }
        }
    }
    val tools: @Composable () -> Unit = {
        if (!waiter) BadgeIconButton(Icons.AutoMirrored.Outlined.ListAlt, "Prep list", state.allDay.sumOf { it.pending + it.preparing }, prepOpen, onPrep)
        onStations?.let { ToolbarIconButton(Icons.Outlined.Settings, "Kitchen stations", onClick = it) }
        RefreshButton(state.loading, { model.refresh() })
    }
    if (size.isPhone) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) { lead() }
            Spacer(Modifier.width(10.dp))
            Clock(now, state.connection)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchField(state.filter.query, { model.setFilter(state.filter.copy(query = it)) }, Modifier.weight(1f), placeholder = "Table, dish or order", height = 40.dp)
            filters(); tools()
        }
    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        lead()
        Spacer(Modifier.width(2.dp))
        search(Modifier.width(230.dp))
        filters()
        Spacer(Modifier.weight(1f))
        Clock(now, state.connection)
        Spacer(Modifier.width(6.dp))
        tools()
    }
}

/** Where the food goes, with how many: one segmented switch, the chosen part green like the app's switches. */
@Composable
private fun KindSwitch(kinds: List<OrderKind>, chosenKind: OrderKind?, all: List<KdsTicket>, onKind: (OrderKind?) -> Unit) {
    Row(Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(10.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val part: @Composable (String, Int, Boolean, () -> Unit) -> Unit = { label, count, chosen, onClick ->
            Row(Modifier.fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(if (chosen) Kit.Green else Color.Transparent).clickable(onClick = onClick)
                .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (chosen) Color.White else Kit.Ink, maxLines = 1)
                Text("$count", Modifier.clip(RoundedCornerShape(50)).background(if (chosen) Color.White.copy(alpha = 0.22f) else Kit.Tint)
                    .padding(horizontal = 6.dp, vertical = 1.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    color = if (chosen) Color.White else Kit.Muted, maxLines = 1)
            }
        }
        part("All", all.size, chosenKind == null) { onKind(null) }
        kinds.forEach { option -> part(option.label, all.count { it.kind() == option }, chosenKind == option) { onKind(option) } }
    }
}

/** The kitchen's clock: the time big; under it whether the screen is live, and the day. */
@Composable
private fun Clock(now: Instant, connection: KdsConnection) {
    val local = now.toLocalDateTime(RestaurantTime.zone)
    val live = connection == KdsConnection.LIVE
    Column(horizontalAlignment = Alignment.End) {
        Text("${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}", fontFamily = Inter(), fontWeight = FontWeight.Bold,
            fontSize = 20.sp, lineHeight = 22.sp, color = Kit.Ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (live) Kit.Green else Kit.Amber))
            Text(listOf(when (connection) { KdsConnection.LIVE -> "Online"; KdsConnection.CONNECTING -> "Connecting…"; else -> "Reconnecting…" },
                weekdayDate(local.date)).joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = if (live) Kit.Muted else Kit.Amber, maxLines = 1)
        }
    }
}

/** A square toolbar button with a small count on its corner; green while what it opens is showing. */
@Composable
private fun BadgeIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Box {
        Surface(onClick = onClick, modifier = Modifier.size(40.dp), shape = RoundedCornerShape(8.dp), color = if (active) Kit.GreenSoft else Color.White,
            border = BorderStroke(1.dp, if (active) Kit.Green else Kit.Border)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(19.dp), tint = if (active) Kit.Green else Kit.Ink) }
        }
        if (count > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).height(18.dp).widthIn(min = 18.dp).clip(RoundedCornerShape(50))
            .background(Kit.Purple).border(1.5.dp, Color.White, RoundedCornerShape(50)).padding(horizontal = 4.dp), Alignment.Center) {
            Text("$count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White, maxLines = 1)
        }
    }
}

// ---------- A column ----------

@Composable
private fun LaneColumn(
    lane: Lane, tickets: List<KdsTicket>, state: KdsState, now: Instant, motion: BoardMotion, hooks: CardHooks, modifier: Modifier,
    folded: Boolean, canFold: Boolean, onFold: () -> Unit, compact: Boolean, drag: Dragging?, waiter: Boolean, handle: (KdsTicket, Lane) -> Modifier
) {
    // While a ticket is dragged, a column it can go to gets a green border, stronger under the pointer; a column it can't
    // go to (New straight to Ready) looks switched off from the start, more so under the pointer (user, 2026-10-31: no "Drop here").
    val other = drag?.takeIf { it.from != lane }
    val canTake = other != null && dropSteps(other.ticket, other.from, lane) != null
    val closed = other != null && !canTake
    val hovering = drag != null && motion.lanes[lane]?.contains(drag.pointer) == true
    val refused = hovering && closed
    val fade by animateFloatAsState(when { refused -> 0.3f; closed -> 0.5f; else -> 1f }, tween(160))
    Surface(
        modifier.onGloballyPositioned { motion.lanes[lane] = Rect(it.positionInRoot(), it.size.toSize()) }.graphicsLayer { alpha = fade },
        shape = LaneShape, color = lane.color.copy(alpha = 0.045f),
        border = BorderStroke(when { hovering && canTake -> 3.dp; canTake -> 2.dp; else -> 1.dp }, when {
            hovering && canTake -> Kit.Green
            canTake -> Kit.Green.copy(alpha = 0.5f)
            closed -> Kit.Border
            else -> lane.color.copy(alpha = 0.22f)
        })
    ) {
        if (folded) FoldedRail(lane, tickets.size, waiter, onFold)
        else Column {
            Row(Modifier.fillMaxWidth().background(lane.color.copy(alpha = 0.13f)).padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(lane.icon, null, Modifier.size(26.dp), tint = lane.color)
                Column(Modifier.weight(1f)) {
                    Text(lane.heading(waiter), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink, maxLines = 1)
                    Text(lane.caption(waiter), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(34.dp).clip(CircleShape).background(lane.color), Alignment.Center) {
                    Text("${tickets.size}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
                if (canFold) Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onFold), Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Fold ${lane.heading(waiter)}", Modifier.size(20.dp), tint = Kit.Muted)
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (tickets.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { LaneEmpty(lane, state.filter.query.isNotBlank(), waiter) }
                } else {
                    val listState = rememberLazyListState()
                    SideEffect { motion.lists[lane] = listState }
                    // A list keeps its old first card where it was when a card is put above it, which hides the new one and looks
                    // like the column scrolled by itself. So while a column is at its top, it stays at the top: the ticket that
                    // moved in shows first and the others slide down, and a column whose top card left doesn't jump either.
                    val top = tickets.firstOrNull()?.id
                    val lastTop = remember { arrayOfNulls<String>(1) }
                    if (lastTop[0] != top) {
                        if (lastTop[0] != null && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40) listState.requestScrollToItem(0)
                        lastTop[0] = top
                    }
                    LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 16.dp)) {
                        items(tickets, key = { it.id }) { ticket ->
                            val away = motion.hidden[ticket.id] == true
                            val dragged = drag?.ticket?.id == ticket.id
                            val arrival = motion.arrived[ticket.id]
                            val shake = remember(ticket.id) { Animatable(0f) }
                            LaunchedEffect(arrival) {
                                if (arrival == null) return@LaunchedEffect
                                for (to in listOf(1f, -1f, 0.75f, -0.75f, 0.45f, -0.45f, 0f)) shake.animateTo(to, tween(70))
                                motion.arrived.remove(ticket.id)
                            }
                            TicketCard(ticket, lane, state, now, hooks, compact = compact, dragHandle = handle(ticket, lane),
                                modifier = Modifier.animateItem(tween(260), tween(380, easing = FastOutSlowInEasing), tween(200))
                                    .graphicsLayer { translationX = shake.value * 9.dp.toPx(); rotationZ = shake.value * 1.2f }
                                    .onGloballyPositioned { if ((motion.pending[ticket.id] ?: ticket.lane()) == lane) motion.cards[ticket.id] = Rect(it.positionInRoot(), it.size.toSize()) }
                                    .alpha(when { away -> 0f; dragged -> 0.3f; else -> 1f }))
                        }
                    }
                    Box(Modifier.matchParentSize().padding(vertical = 8.dp), contentAlignment = Alignment.CenterEnd) {
                        PlatformVerticalScrollbar(listState, Modifier.fillMaxHeight().width(3.dp))
                    }
                }
            }
        }
    }
}

/** A folded column: a thin rail with its icon, count and name, that opens again on a click. */
@Composable
private fun FoldedRail(lane: Lane, count: Int, waiter: Boolean, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().clickable(onClick = onOpen).background(lane.color.copy(alpha = 0.10f)).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Open ${lane.heading(waiter)}", Modifier.size(20.dp), tint = Kit.Muted)
        Icon(lane.icon, null, Modifier.size(24.dp), tint = lane.color)
        Box(Modifier.size(32.dp).clip(CircleShape).background(lane.color), Alignment.Center) {
            Text("$count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
        }
        Text(lane.heading(waiter), Modifier.sideways(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink, maxLines = 1)
    }
}

/** Lays text out turned a quarter to the left, reading bottom to top. */
private fun Modifier.sideways(): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(placeable.height, placeable.width) {
        placeable.place(-(placeable.width / 2 - placeable.height / 2), -(placeable.height / 2 - placeable.width / 2))
    }
}.rotate(-90f)

@Composable
private fun LaneEmpty(lane: Lane, filtering: Boolean, waiter: Boolean) {
    val (title, hint) = when {
        filtering -> "Nothing found" to "Try another table, dish or order number."
        waiter && lane == Lane.NEW -> "Nothing waiting" to "Orders show here once they're sent to the kitchen."
        waiter && lane == Lane.COOKING -> "Nothing on the stove" to "Orders move here when the kitchen starts them."
        waiter -> "Nothing to serve" to "Food shows here as soon as the kitchen marks it ready."
        lane == Lane.NEW -> "No new tickets" to "New orders appear here as soon as they're sent."
        lane == Lane.COOKING -> "Nothing on the stove" to "Drag a ticket here, or tap Start cooking."
        else -> "The pass is clear" to "Ready food waits here until it's served."
    }
    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(lane.color.copy(alpha = 0.10f)), Alignment.Center) {
            Icon(if (filtering) Icons.Outlined.SearchOff else lane.icon, null, Modifier.size(26.dp), tint = lane.color)
        }
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
        Text(hint, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted, textAlign = TextAlign.Center)
    }
}

// ---------- Prep list ----------

/**
 * Prep list (was "All day"): every dish still to make across the open tickets, added up, so the cook preps in one go.
 * A sidebar on the right that opens only from its button.
 */
@Composable
private fun PrepList(items: List<KdsAllDayItem>, photos: Map<String, String?>, modifier: Modifier, onClose: () -> Unit) {
    // Only what's still to make; a dish that's all ready has left the list.
    val toMake = items.filter { it.pending + it.preparing > 0 }
    Surface(modifier, shape = RectangleShape, color = Color.White, shadowElevation = 16.dp) {
        Column {
            Row(Modifier.fillMaxWidth().background(Kit.Purple.copy(alpha = 0.10f)).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ListAlt, null, Modifier.size(24.dp), tint = Kit.Purple)
                Column(Modifier.weight(1f)) {
                    Text("Prep list", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink)
                    Text("still to make, added up", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
                }
                Box(Modifier.size(32.dp).clip(CircleShape).background(Kit.Purple), Alignment.Center) {
                    Text("${toMake.sumOf { it.pending + it.preparing }}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
                Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose), Alignment.Center) {
                    Icon(Icons.Outlined.Close, "Close the prep list", Modifier.size(19.dp), tint = Kit.Muted)
                }
            }
            if (toMake.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Kitchen, null, Modifier.size(26.dp), tint = Kit.Faint)
                        Text("Nothing to make", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                        Text("Dishes on open tickets add up here.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, textAlign = TextAlign.Center)
                    }
                }
                return@Column
            }
            val listState = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 16.dp)) {
                    items(toMake.sortedByDescending { it.pending + it.preparing }, key = { "${it.menuItemId}-${it.name}-${it.variant}-${it.notes}-${it.modifiers.hashCode()}" }) { item ->
                        val open = item.pending + item.preparing
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DishPhoto(item.menuItemId?.let { photos[it] }, 44.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                // "10 Pizza", then "2 to start, 8 cooking, in 3 orders" (user, 2026-10-31).
                                Text("$open ${listOfNotNull(item.name, item.variant).joinToString(" · ")}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val extras = listOfNotNull(item.modifiers.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }, item.notes?.let { "“$it”" })
                                if (extras.isNotEmpty()) Text(extras.joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(Triple(item.pending, "to start", Lane.NEW), Triple(item.preparing, "cooking", Lane.COOKING)).filter { it.first > 0 }.forEach { (n, what, lane) ->
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(Modifier.size(6.dp).clip(CircleShape).background(lane.color))
                                            Text("$n $what", fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1)
                                        }
                                    }
                                    Text(if (item.orders == 1) "in 1 order" else "in ${item.orders} orders", fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.matchParentSize().padding(vertical = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    PlatformVerticalScrollbar(listState, Modifier.fillMaxHeight().width(3.dp))
                }
            }
        }
    }
}
