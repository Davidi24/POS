package com.saporini.mobile_desktop.pos.kitchen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RoomService
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.CompactStat
import com.saporini.mobile_desktop.core.components.CountPill
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.SkeletonBox
import com.saporini.mobile_desktop.core.components.SkeletonLight
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.PlatformVerticalScrollbar
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import com.saporini.mobile_desktop.pos.reservations.ToolbarHeight
import kotlinx.coroutines.delay
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import kotlin.time.Clock
import kotlin.time.Instant

private const val ALL_STATIONS = "All stations"
private const val ALL_ORDERS = "All orders"
private const val MY_ORDERS = "My orders"

private fun KitchenLane.color(): Color = when (this) {
    KitchenLane.READY -> Kit.Green
    KitchenLane.COOKING -> Kit.Amber
    KitchenLane.WAITING -> Kit.Blue
}

private fun KitchenLane.title(): String = when (this) {
    KitchenLane.READY -> "Ready to serve"
    KitchenLane.COOKING -> "Cooking"
    KitchenLane.WAITING -> "Waiting"
}

private fun KitchenLane.icon(): ImageVector = when (this) {
    KitchenLane.READY -> Icons.Outlined.RoomService
    KitchenLane.COOKING -> Icons.Outlined.LocalFireDepartment
    KitchenLane.WAITING -> Icons.Outlined.HourglassEmpty
}

@Composable
fun KitchenStatusScreen(modifier: Modifier = Modifier) {
    val model = org.koin.compose.koinInject<com.saporini.mobile_desktop.kds.KdsScreenModel>()
    val state by model.state.collectAsState()
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(model, owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) model.setActive(true)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) model.setActive(false)
        }
        owner.lifecycle.addObserver(observer)
        model.setActive(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(observer); model.onDispose() }
    }
    LaunchedEffect(state.scope, state.canRead) { if (state.canRead) model.loadPosTiming() }
    KitchenStatusContent(
        state = state,
        onPickUp = { ticketIds -> ticketIds.forEach { model.pickUp(it) } },
        onRefresh = { model.refresh() },
        onClearMessages = model::clearMessages,
        modifier = modifier
    )
}

@Composable
internal fun KitchenStatusContent(
    state: KdsState,
    onPickUp: (List<String>) -> Unit,
    onRefresh: () -> Unit,
    onClearMessages: () -> Unit = {},
    modifier: Modifier = Modifier,
    clock: () -> Instant = { Clock.System.now() }
) {
    var who by remember(state.scope) { mutableStateOf(ALL_ORDERS) }
    var station by remember(state.scope) { mutableStateOf(ALL_STATIONS) }
    var query by remember(state.scope) { mutableStateOf("") }
    var phoneLane by remember { mutableStateOf(KitchenLane.READY) }
    // Minutes on the cards move on by themselves.
    var now by remember { mutableStateOf(clock()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = clock() } }
    // "Marked as picked up." fades after a moment; errors stay until closed.
    LaunchedEffect(state.notice) { if (state.notice != null) { delay(3_000); onClearMessages() } }

    val timing = state.posTiming
    val me = state.scope?.userId
    val all = state.kitchenOrders()
    val stations = all.flatMap { it.stations }.distinct().sorted()
    val shown = all
        .filter { who == ALL_ORDERS || it.waiterId == me }
        .filter { station == ALL_STATIONS || station in it.stations }
        .filter { it.matches(query) }
    val ready = shown.filter { it.lane == KitchenLane.READY }
    val cooking = shown.filter { it.lane == KitchenLane.COOKING }
    val waiting = shown.filter { it.lane == KitchenLane.WAITING }.sortedBy { it.held }
    fun minutesSince(at: Instant?) = at?.let { (now - it).inWholeMinutes.coerceAtLeast(0) }
    val slow = shown.filter { it.lane != KitchenLane.READY && !it.held && (minutesSince(it.sentAt) ?: 0) >= timing.slowAfterMinutes }
    val waitingLongest = waiting.filterNot { it.held }.mapNotNull { minutesSince(it.sentAt) }.maxOrNull()
    val readyTooLong = ready.count { (minutesSince(it.readySince) ?: 0) >= timing.readyWaitingMinutes }
    val filtering = who != ALL_ORDERS || station != ALL_STATIONS || query.isNotBlank()

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        val lanesSideBySide = size.isDesktop || (size == ScreenSize.TABLET && maxWidth >= 860.dp)
        val pad = if (size.isPhone) 14.dp else 22.dp
        Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = if (size.isPhone) 12.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            KitchenHeader(state, size, who, { who = it }, station, listOf(ALL_STATIONS) + stations, { station = it }, query, { query = it }, onRefresh)

            state.actionError?.let { MessageBar(it.message, error = true, onClearMessages) }
            state.notice?.let { MessageBar(it, error = false, onClearMessages) }

            when {
                !state.canRead -> CenterBox { OverviewEmpty("No access to the kitchen", "Ask a manager to give your role “View Kitchen Display”. Then sign in again.", Icons.Outlined.Lock) }
                !state.loaded && state.error != null -> CenterBox {
                    OverviewEmpty("Couldn't load the kitchen", state.error.message, Icons.Outlined.CloudOff) { RetryButton(onRefresh) }
                }
                !state.loaded -> KitchenSkeleton(size)
                state.needsStationSetup -> CenterBox {
                    OverviewEmpty("The kitchen isn't set up yet", "Once kitchen stations are added, the food for your tables shows up here.", Icons.Outlined.Restaurant, Res.drawable.settings_orders_kitchen)
                }
                else -> {
                    // Slim tiles: a number and a few words, so the lanes below keep their room.
                    val cards: List<@Composable (Modifier) -> Unit> = listOf(
                        { m ->
                            CompactStat("Ready to serve", "${ready.size}", Kit.Green, m,
                                detail = if (readyTooLong > 0) "$readyTooLong waiting" else null, icon = Icons.Outlined.RoomService,
                                valueColor = if (readyTooLong > 0) Kit.Danger else Kit.Ink,
                                onClick = if (size.isDesktop) null else ({ phoneLane = KitchenLane.READY }))
                        },
                        { m ->
                            CompactStat("Cooking", "${cooking.size}", Kit.Amber, m, icon = Icons.Outlined.LocalFireDepartment,
                                onClick = if (size.isDesktop) null else ({ phoneLane = KitchenLane.COOKING }))
                        },
                        { m ->
                            CompactStat("Waiting", "${waiting.size}", Kit.Blue, m,
                                detail = waitingLongest?.let { "longest $it min" }, icon = Icons.Outlined.HourglassEmpty,
                                onClick = if (size.isDesktop) null else ({ phoneLane = KitchenLane.WAITING }))
                        },
                        { m ->
                            CompactStat("Taking long", "${slow.size}", if (slow.isEmpty()) Kit.Grey else Kit.Danger, m,
                                detail = "over ${timing.slowAfterMinutes} min", icon = Icons.Outlined.Timer,
                                valueColor = if (slow.isEmpty()) Kit.Ink else Kit.Danger)
                        }
                    )
                    if (size.isDesktop) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards.forEach { it(Modifier.weight(1f)) } }
                    } else {
                        val gap = if (size.isPhone) 10.dp else 12.dp
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { cards[0](Modifier.weight(1f)); cards[1](Modifier.weight(1f)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { cards[2](Modifier.weight(1f)); cards[3](Modifier.weight(1f)) }
                    }
                    if (state.stale && state.loaded) MessageBar("Reconnecting to the kitchen… The orders below may be a little behind.", error = false, null, warning = true)

                    val lanes = mapOf(KitchenLane.READY to ready, KitchenLane.COOKING to cooking, KitchenLane.WAITING to waiting)
                    val lane: @Composable (KitchenLane, Modifier) -> Unit = { which, m ->
                        LaneBox(which, lanes.getValue(which), m, now, state, filtering, who == MY_ORDERS, onPickUp)
                    }
                    if (lanesSideBySide) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            lane(KitchenLane.READY, Modifier.weight(1.12f).fillMaxHeight())
                            lane(KitchenLane.COOKING, Modifier.weight(1f).fillMaxHeight())
                            lane(KitchenLane.WAITING, Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        OverviewTabs(
                            KitchenLane.entries.map { "${it.title()} (${lanes.getValue(it).size})" },
                            "${phoneLane.title()} (${lanes.getValue(phoneLane).size})",
                            { label -> phoneLane = KitchenLane.entries.first { label.startsWith(it.title()) } },
                            Modifier.fillMaxWidth()
                        )
                        lane(phoneLane, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun KitchenHeader(
    state: KdsState, size: ScreenSize,
    who: String, onWho: (String) -> Unit,
    station: String, stations: List<String>, onStation: (String) -> Unit,
    query: String, onQuery: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val title: @Composable () -> Unit = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kitchen Status", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.sp, color = Kit.Ink)
                LiveBadge(state)
            }
            Text("What the kitchen is making for your tables", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
    }
    val controls: @Composable (Modifier) -> Unit = { m ->
        Row(m, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OverviewTabs(listOf(ALL_ORDERS, MY_ORDERS), who, onWho, Modifier.width(if (size.isPhone) 190.dp else 220.dp), height = ToolbarHeight)
            if (stations.size > 2) HeaderDropdown(station, Icons.Outlined.Restaurant, Modifier.width(if (size.isPhone) 140.dp else 160.dp), stations, onStation)
            if (!size.isPhone) SearchField(query, onQuery, Modifier.width(220.dp), placeholder = "Search table, dish…", height = ToolbarHeight)
            RefreshButton(state.loading, onRefresh)
        }
    }
    if (size.isDesktop) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { title() }
            controls(Modifier)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            title()
            controls(Modifier.fillMaxWidth())
            if (size.isPhone) SearchField(query, onQuery, Modifier.fillMaxWidth(), placeholder = "Search table, dish…", height = ToolbarHeight)
        }
    }
}

// "Live" with a green dot while connected; amber while the connection comes back.
@Composable
private fun LiveBadge(state: KdsState) {
    val live = state.loaded && !state.stale
    val color = if (live) Kit.Green else Kit.Amber
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(if (live) "Live" else if (state.loaded) "Reconnecting" else "Connecting", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = color)
    }
}

@Composable
private fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Kit.Green, strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Refresh, "Refresh", Modifier.size(18.dp), tint = Kit.Ink)
        }
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = Kit.Green) {
        Row(Modifier.height(40.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Outlined.Refresh, null, Modifier.size(17.dp), tint = Color.White)
            Text("Try again", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
        }
    }
}

@Composable
private fun MessageBar(text: String, error: Boolean, onDismiss: (() -> Unit)?, warning: Boolean = false) {
    val color = when { error -> Kit.Danger; warning -> Kit.Amber; else -> Kit.Green }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = color)
        if (onDismiss != null) {
            Icon(Icons.Outlined.Close, "Dismiss", Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(4.dp), tint = color)
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 320.dp), contentAlignment = Alignment.Center) { content() }
}

// One lane: its header with the count, then the order cards (only the ones on screen are drawn).
@Composable
private fun LaneBox(
    lane: KitchenLane,
    orders: List<KitchenOrderCard>,
    modifier: Modifier,
    now: Instant,
    state: KdsState,
    filtering: Boolean,
    mineOnly: Boolean,
    onPickUp: (List<String>) -> Unit
) {
    val color = lane.color()
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.035f), border = BorderStroke(1.dp, color.copy(alpha = 0.22f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(lane.icon(), null, Modifier.size(17.dp), tint = color)
                }
                Spacer(Modifier.width(10.dp))
                Text(lane.title(), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                CountPill(orders.size, color = color, background = Color.White)
            }
            if (orders.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        filtering -> LaneEmpty(Icons.Outlined.SearchOff, "Nothing found", if (mineOnly) "None of your orders are here." else "Try another station or search.", color)
                        lane == KitchenLane.READY -> LaneEmpty(Icons.Filled.CheckCircle, "Nothing to pick up", "Food shows up here as soon as the kitchen marks it ready.", color)
                        lane == KitchenLane.COOKING -> LaneEmpty(Icons.Outlined.LocalFireDepartment, "Nothing cooking", "Orders move here when the kitchen starts them.", color)
                        else -> LaneEmpty(Icons.Outlined.HourglassEmpty, "No orders waiting", "New orders wait here until the kitchen starts them.", color)
                    }
                }
            } else {
                val listState = rememberLazyListState()
                Box(Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        var heldShown = false
                        orders.forEach { order ->
                            if (order.held && !heldShown) {
                                heldShown = true
                                item(key = "held-label") { GroupLabel("Held for later", "Not started by the kitchen yet") }
                            }
                            item(key = order.orderId) {
                                OrderCard(order, now, state, onPickUp)
                            }
                        }
                    }
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                        PlatformVerticalScrollbar(state = listState, modifier = Modifier.fillMaxHeight().width(3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LaneEmpty(icon: ImageVector, title: String, hint: String, color: Color) {
    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(26.dp), tint = color)
        }
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
        Text(hint, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun OrderCard(order: KitchenOrderCard, now: Instant, state: KdsState, onPickUp: (List<String>) -> Unit) {
    val timing = state.posTiming
    val laneColor = order.lane.color()
    val sentMinutes = order.sentAt?.let { (now - it).inWholeMinutes.coerceAtLeast(0) }
    val readyMinutes = order.readySince?.let { (now - it).inWholeMinutes.coerceAtLeast(0) }
    val late = when (order.lane) {
        KitchenLane.READY -> (readyMinutes ?: 0) >= timing.readyWaitingMinutes
        else -> !order.held && (sentMinutes ?: 0) >= timing.slowAfterMinutes
    }
    val strip = if (late) Kit.Danger else laneColor
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color.White,
        border = BorderStroke(1.dp, if (late) Kit.Danger.copy(alpha = 0.35f) else Kit.RowBorder), shadowElevation = if (order.lane == KitchenLane.READY) 1.dp else 0.dp
    ) {
        Row(Modifier.height(IntrinsicSize.Min).alpha(if (order.held) 0.72f else 1f)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(strip))
            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TableBadge(order.table, laneColor)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(order.table?.let { "Table $it" } ?: "No table", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull("#${order.orderNumber}", order.guests?.let { if (it == 1) "1 guest" else "$it guests" }).joinToString(" · "),
                            fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    when {
                        order.held -> StatusChip("Held", Kit.Grey, Icons.Outlined.PauseCircle)
                        order.lane == KitchenLane.READY -> StatusChip(
                            if (late) "Waiting ${readyMinutes} min" else "Ready ${readyMinutes?.let { "$it min" } ?: "now"}",
                            if (late) Kit.Danger else Kit.Green, Icons.Outlined.Timer, strong = late
                        )
                        else -> StatusChip("${sentMinutes ?: 0} min", if (late) Kit.Danger else laneColor, Icons.Outlined.Timer, strong = late)
                    }
                }
                if (order.rush || order.stations.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (order.rush) StatusChip("Rush", Kit.Danger, Icons.Outlined.Bolt)
                        order.stations.take(3).forEach { StationTag(it) }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))
                val readyDishes = order.dishes.filter { it.state == DishState.READY }
                val otherDishes = order.dishes.filter { it.state != DishState.READY }
                if (order.lane == KitchenLane.READY) {
                    readyDishes.forEach { DishLine(it) }
                    if (otherDishes.isNotEmpty()) {
                        Text("Still in the kitchen", Modifier.padding(top = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
                        otherDishes.forEach { DishLine(it, faded = true) }
                    }
                } else {
                    order.dishes.forEach { DishLine(it) }
                }
                order.note?.let { NoteBox(it) }

                if (order.lane == KitchenLane.READY && state.canPickUp) {
                    val busy = order.readyTicketIds.any { "ticket:$it" in state.busyKeys }
                    PickUpButton(
                        if (otherDishes.isEmpty()) "Picked up" else "Picked up · ${order.readyCount} of ${order.totalCount}",
                        busy
                    ) { onPickUp(order.readyTicketIds) }
                }
            }
        }
    }
}

@Composable
private fun TableBadge(table: String?, color: Color) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (table == null) Icon(Icons.Outlined.ShoppingBag, null, Modifier.size(20.dp), tint = color)
        else Text(table.take(4), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (table.length > 3) 12.sp else 15.sp, color = color, maxLines = 1)
    }
}

@Composable
private fun StationTag(name: String) {
    Text(
        name, Modifier.clip(RoundedCornerShape(6.dp)).background(Kit.Tint).padding(horizontal = 7.dp, vertical = 3.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Muted, maxLines = 1
    )
}

@Composable
private fun DishLine(dish: KitchenDish, faded: Boolean = false) {
    Row(Modifier.fillMaxWidth().alpha(if (faded) 0.6f else 1f), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp).size(16.dp), contentAlignment = Alignment.Center) {
            when (dish.state) {
                DishState.READY -> Icon(Icons.Filled.CheckCircle, "Ready", Modifier.size(16.dp), tint = Kit.Green)
                DishState.COOKING -> Box(Modifier.size(10.dp).clip(CircleShape).background(Kit.Amber))
                DishState.WAITING -> Box(Modifier.size(10.dp).clip(CircleShape).border(1.5.dp, Kit.Faint, CircleShape))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("${dish.quantity}×", Modifier.width(26.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
        Column(Modifier.weight(1f)) {
            Text(dish.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
            dish.detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Muted) }
        }
    }
}

@Composable
private fun NoteBox(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFBF3E6)).padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(Icons.Outlined.StickyNote2, null, Modifier.size(15.dp), tint = Color(0xFF8B5C18))
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = Color(0xFF8B5C18))
    }
}

@Composable
private fun PickUpButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(8.dp), color = Kit.Green) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Outlined.DoneAll, null, Modifier.size(18.dp), tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "Saving…" else text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
        }
    }
}

@Composable
private fun KitchenSkeleton(size: ScreenSize) {
    val alpha = rememberSkeletonAlpha("kitchen-status")
    Column(Modifier.fillMaxWidth().alpha(alpha), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(if (size.isDesktop) 4 else 2) {
                Box(Modifier.weight(1f).height(82.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.Border, RoundedCornerShape(10.dp))) {
                    SkeletonBox(Modifier.padding(14.dp).size(52.dp), SkeletonLight, RoundedCornerShape(10.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(if (size.isDesktop) 3 else 1) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBox(Modifier.width(120.dp).height(16.dp))
                    repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(118.dp), SkeletonLight, RoundedCornerShape(10.dp)) }
                }
            }
        }
    }
}
