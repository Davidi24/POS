package com.saporini.mobile_desktop.pos.tables.ui

import mobile_desktop.shared.generated.resources.reservation_desk
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ChevronRight
import kotlinx.datetime.toLocalDateTime
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.AppDialog
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.outlined.JoinInner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderFulfillmentStatus
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderStatus
import com.saporini.mobile_desktop.pos.orders.ui.label
import com.saporini.mobile_desktop.pos.orders.ui.orderStatusColor
import com.saporini.mobile_desktop.pos.orders.ui.progressColor
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog

private val TableGreen = Color(0xFF4F7942)
private val TableDanger = Color(0xFFB13A2F)
private val TableInk = Color(0xFF242424)
private val TableMuted = Color(0xFF71736E)
private val TableBorder = Color(0xFFE1DCD8)

/**
 * Everyday actions for a table during service (seat, order, move, join/separate,
 * availability). Table-plan editing (add/resize/rotate tables, the background
 * image) lives in TableLayoutToolbar's edit mode instead — this modal is about
 * running service on the floor plan as it already stands.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BoxScope.TableDetailsModal(
    tables: List<FloorPlanTable>,
    primaryTableId: String?,
    onDismiss: () -> Unit,
    // Guests, and "More will come".
    onSeatGuests: (Int, Boolean) -> Unit,
    onUpdateGuestCount: (Int, Boolean) -> Unit,
    onStartOrder: () -> Unit,
    onViewOrder: () -> Unit,
    onOpenEmptyOrder: () -> Unit,
    onMergeTables: () -> Unit,
    onSeparateTables: (String) -> Unit,
    onStartMoveGuests: () -> Unit,
    onSetAvailability: (Boolean) -> Unit,
    onGoToPayment: () -> Unit,
    onSetBillRequested: (String, Boolean) -> Unit,
    onUnmergeSome: () -> Unit = {},
    // With guests: every other table becomes free, this one keeps the guests and the order (user, 2026-11-01).
    onUnmergeKeeping: (String) -> Unit = {},
    isSaving: Boolean = false,
    // Settings → Tables → Special cases (user, 2026-11-01).
    canMoveBooking: Boolean = false,
    canMoveGuests: Boolean = true,
    // Opens the booking in Reservations, where it's also cancelled (user, 2026-11-01).
    onViewBooking: ((String) -> Unit)? = null,
    // Opens this table's past orders in POS → History.
    onShowHistory: (() -> Unit)? = null,
    // Moving the table's next booking opens the floor plan to pick its new table (user, 2026-11-02).
    onChooseBookingTable: () -> Unit = {},
    onGiveMoreTime: (Int) -> Unit = {},
    onBookingWaits: () -> Unit = {},
    onStillHere: () -> Unit = {}
) {
    val table = tables.toServiceSummaryTable()
    val tableTitle = tables.joinToString(separator = "-") { it.label }
    val covers = tables.mapNotNull { it.guestCount }.takeIf { it.isNotEmpty() }?.sum()
        ?: tables.sumOf { it.seatCount.coerceAtLeast(1) }
    val orderLabels = tables.mapNotNull { it.orderLabel }.distinct()
    val hasOrder = orderLabels.isNotEmpty()
    val isMergedGroup = tables.size > 1
    var mode by remember(tables) { mutableStateOf(DetailMode.MENU) }
    // The ways to handle a coming booking stay folded until Handle is clicked (user, 2026-11-01).
    var handling by remember(tables.map { it.id }) { mutableStateOf(false) }
    val head = tables.firstOrNull { it.id == primaryTableId } ?: tables.first()
    val seats = tables.sumOf { it.seatCount.coerceAtLeast(1) }
    val leastGuests = (head.guestsMin ?: 1).coerceAtLeast(1)
    var guestCount by remember(tables) {
        mutableStateOf((tables.mapNotNull { it.guestCount }.takeIf { it.isNotEmpty() }?.sum() ?: 1).coerceAtLeast(leastGuests))
    }
    // "More will come" (user, 2026-11-01): seated now, the rest later; the table's ring fills as they arrive.
    var moreComing by remember(tables) { mutableStateOf(head.moreComing) }
    var showAvailabilityConfirm by remember { mutableStateOf(false) }
    var showFreeConfirm by remember { mutableStateOf(false) }
    var showSeparateConfirm by remember { mutableStateOf(false) }
    val clash = tables.firstNotNullOfOrNull { it.clash }
    val autoFreeAt = tables.firstNotNullOfOrNull { it.autoFreeAt }
    // Seating a booking starts from its guests, and can be changed from there (user, 2026-11-01).
    // Seating a booking starts from its size, the number staff would enter; they change it if fewer came (user, 2026-11-01).
    val startFromBooking = {
        tables.firstNotNullOfOrNull { it.bookingParty ?: it.bookingGuests }?.let { guests ->
            guestCount = guests.coerceAtLeast(leastGuests).coerceAtMost((head.guestsMax ?: guests).coerceAtLeast(leastGuests))
        }
    }

    // Never taller than the window: with a special case there are more buttons to reach.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
    val roomHigh = maxHeight
    val roomWide = maxWidth
    Box(
        modifier = Modifier
            .width(if (roomWide < 446.dp) roomWide - 16.dp else 430.dp)
            .heightIn(max = (roomHigh - 24.dp).coerceAtLeast(240.dp))
            .shadow(18.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
    ) {
    // A thin bar at the panel's edge (user, 2026-11-01: "the scroll bar is so big").
    val panelScroll = androidx.compose.foundation.rememberScrollState()
    Column(Modifier.verticalScroll(panelScroll).padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Table $tableTitle",
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                // Merged tables have long names: on a phone they take two lines, all of it readable.
                fontSize = if (roomWide < 446.dp && tableTitle.length > 6) 19.sp else 24.sp,
                lineHeight = if (roomWide < 446.dp && tableTitle.length > 6) 23.sp else 30.sp,
                color = TableInk,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            // The table's past orders, in POS → History (user, 2026-11-01).
            if (onShowHistory != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).border(1.dp, TableBorder, RoundedCornerShape(50)).clickable(onClick = onShowHistory)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.History, null, Modifier.size(17.dp), tint = TableInk)
                    Text("History", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = TableInk)
                }
                Spacer(Modifier.width(6.dp))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Close, "Close", modifier = Modifier.size(26.dp), tint = TableInk)
            }
        }
        Spacer(Modifier.height(12.dp))
        // An order with dishes shows its order status and kitchen status, never the raw "Pending" (user, 2026-11-01), on one
        // line, each named right before it (user, 2026-11-02: "in one line, like this it's confusing").
        val orderStatus = table.orderStatus?.let { runCatching { OrderStatus.valueOf(it) }.getOrNull() }
        val foodStatus = table.foodStatus?.let { runCatching { OrderFulfillmentStatus.valueOf(it) }.getOrNull() }
        if (table.state == TableVisualState.Ordered && orderStatus != null && foodStatus != null) {
            // Order at the left, food at the right edge (user, 2026-11-02).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusCaption("Order")
                // Active and Served in a green a little darker than the nav bar's Online dot (user, 2026-11-01).
                StatusPill(
                    text = orderStatus.label(),
                    color = if (orderStatus == OrderStatus.OPEN) PanelStatusGreen else orderStatusColor(orderStatus),
                    small = true
                )
                Spacer(Modifier.weight(1f))
                StatusCaption("Food")
                StatusPill(
                    text = foodStatus.label(),
                    color = if (foodStatus == OrderFulfillmentStatus.FULFILLED) PanelStatusGreen else progressColor(foodStatus),
                    small = true
                )
            }
        } else {
            StatusPill(text = table.statusLabel(), color = table.statusColor())
        }
        // The bookings on this table, each opening it in Reservations (user, 2026-11-02: "I can go from the table to the
        // reservation"), with what it celebrates. The next one can be moved to another table from here, on the map.
        val seatedBooking = tables.firstNotNullOfOrNull { it.seatedBooking }
        val nextBooking = tables.firstNotNullOfOrNull { it.nextBooking() }?.takeIf { it.id != seatedBooking?.id }
        if (seatedBooking != null || nextBooking != null) {
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                seatedBooking?.let { booking ->
                    TableBookingCard(booking, "Their booking", narrow = roomWide < 446.dp, onOpen = onViewBooking?.let { open -> { open(booking.id) } })
                }
                nextBooking?.let { booking ->
                    TableBookingCard(
                        booking, "Next booking", narrow = roomWide < 446.dp,
                        onOpen = onViewBooking?.let { open -> { open(booking.id) } },
                        // Seated guests make the clash's Handle the place to move it; here it's for any other table.
                        onMove = if (canMoveBooking && clash == null) onChooseBookingTable else null
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        when (mode) {
            DetailMode.MENU -> {
                // Everything about handling sits in one box, apart from the table's own buttons below (user, 2026-11-01).
                // Its border is the warning text's colour.
                if (autoFreeAt != null || clash != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF8A5A0B), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        autoFreeAt?.let { at ->
                            // An orange table's order has no dishes, so there's nothing to close as unpaid.
                            val closesOrder = hasOrder && table.state != TableVisualState.Paid && table.state != TableVisualState.Occupied
                            MessageBar(
                                "Nobody has acted on this table. At ${at.clockTime()} it's set free" + (if (closesOrder) " and the order closes as unpaid." else "."),
                                MessageKind.WARNING
                            )
                            Spacer(Modifier.height(10.dp))
                            DialogActionButton(
                                text = "Still here", icon = Icons.Filled.Groups,
                                modifier = Modifier.fillMaxWidth().height(50.dp), dark = clash == null, isLoading = isSaving,
                                onClick = onStillHere
                            )
                            if (clash != null) Spacer(Modifier.height(16.dp))
                        }
                        clash?.let {
                            val actions = clashActions(table.state, it, canMoveBooking, canMoveGuests)
                            MessageBar(
                                listOfNotNull(it.panelText(), it.waitingText()).joinToString(" "), MessageKind.WARNING,
                                // A phone keeps the words on the whole width, with Handle on the line under them (user, 2026-11-02).
                                trailing = if (actions.isEmpty() || roomWide < 446.dp) null else { { HandleButton(handling) { handling = !handling } } }
                            )
                            // The booking that's coming, in Reservations (user, 2026-11-01).
                            val booking = it.reservationId
                            val bookingLink: @Composable (Modifier) -> Unit = { linkModifier ->
                                if (booking != null && onViewBooking != null) Text(
                                    "See the reservation ›",
                                    linkModifier.clip(RoundedCornerShape(6.dp)).clickable { onViewBooking(booking) }
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = TableNextBookingPurple
                                )
                            }
                            if (roomWide < 446.dp) {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    bookingLink(Modifier)
                                    Spacer(Modifier.weight(1f))
                                    if (actions.isNotEmpty()) HandleButton(handling) { handling = !handling }
                                }
                                if (handling) Spacer(Modifier.height(10.dp))
                            } else if (booking != null && onViewBooking != null) {
                                bookingLink(Modifier.align(Alignment.End))
                            } else if (handling) Spacer(Modifier.height(10.dp))
                            val button: @Composable (ClashAction, Modifier, Boolean) -> Unit = { action, buttonModifier, main ->
                                DialogActionButton(
                                    text = action.label, icon = action.icon,
                                    modifier = buttonModifier.height(50.dp), dark = main, isLoading = isSaving,
                                    onClick = {
                                        when (action) {
                                            ClashAction.SET_FREE -> onSetAvailability(true)
                                            ClashAction.PAY -> onGoToPayment()
                                            ClashAction.MOVE_BOOKING -> onChooseBookingTable()
                                            ClashAction.MOVE_GUESTS -> onStartMoveGuests()
                                            ClashAction.MORE_TIME -> mode = DetailMode.MORE_TIME
                                            ClashAction.BOOKING_WAITS -> onBookingWaits()
                                        }
                                    }
                                )
                            }
                            if (handling) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    actions.firstOrNull()?.let { main -> button(main, Modifier.fillMaxWidth(), true) }
                                    // One a line on a phone, where two beside each other would cut their names.
                                    actions.drop(1).chunked(if (roomWide < 446.dp) 1 else 2).forEach { pair ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            pair.forEach { action -> button(action, Modifier.weight(1f), false) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // A line between the cards (user, 2026-11-01).
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(TableBorder))
                    Spacer(Modifier.height(16.dp))
                }
                if (table.state == TableVisualState.Occupied || table.state == TableVisualState.Ordered || table.state == TableVisualState.BillPending ||
                    table.state == TableVisualState.Paid) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, TableBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DetailRow(icon = Icons.Filled.Groups, label = "Covers", value = "$covers Guests" + if (head.moreComing) " · more coming" else "")
                        DetailRow(icon = Icons.Filled.AddCircle, label = "Seated since", value = table.seatedSince())
                        // An orange table's order has no dishes yet, so nothing is ordered to show.
                        if (hasOrder && table.state != TableVisualState.Occupied) {
                            DetailRow(icon = Icons.Filled.Assignment, label = "Current order", value = orderLabels.joinToString(", "))
                        }
                        if (table.state == TableVisualState.Paid) {
                            DetailRow(icon = Icons.Filled.Payments, label = "Paid", value = "Waiting for the guests to leave")
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                ActionList(
                    table = table,
                    // Until Handle is clicked the panel's own buttons stay as they are; after it, the handling buttons replace their twins.
                    clashShown = clash != null && handling,
                    // A booking can be cancelled from its table: guests call (user, 2026-11-01).
                    onViewBooking = tables.firstNotNullOfOrNull { it.bookingId }?.let { id -> onViewBooking?.let { open -> { open(id) } } },
                    hasOrder = hasOrder,
                    isMergedGroup = isMergedGroup,
                    isSaving = isSaving,
                    onSeatCustomers = {
                        if (table.state == TableVisualState.HoldEnding) startFromBooking()
                        mode = DetailMode.GUEST_COUNT
                    },
                    onSeatReservation = {
                        startFromBooking()
                        mode = DetailMode.GUEST_COUNT
                    },
                    onChangeGuestCount = { mode = DetailMode.CHANGE_GUEST_COUNT },
                    onStartOrder = onStartOrder,
                    onViewOrder = onViewOrder,
                    onOpenEmptyOrder = onOpenEmptyOrder,
                    onMoveGuests = onStartMoveGuests,
                    onJoinTables = onMergeTables,
                    onSeparateTables = { showSeparateConfirm = true },
                    onGoToPayment = onGoToPayment,
                    onSetBillRequested = { requested -> table.orderId?.let { onSetBillRequested(it, requested) } },
                    onMarkUnavailable = { showAvailabilityConfirm = true },
                    onMarkAvailable = { onSetAvailability(true) },
                    // Guests who leave without ordering: asked first (user, 2026-11-01). Once they've paid, it's the usual end.
                    onSetFree = { if (table.state == TableVisualState.Paid) onSetAvailability(true) else showFreeConfirm = true }
                )
            }
            DetailMode.MORE_TIME -> {
                Text("How much more time?", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TableInk)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    clash?.moreTime.orEmpty().forEach { minutes ->
                        DialogActionButton(
                            text = "$minutes min", icon = null,
                            modifier = Modifier.weight(1f).height(50.dp), dark = true, isLoading = isSaving,
                            onClick = { onGiveMoreTime(minutes) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                DialogActionButton(text = "Back", icon = null, modifier = Modifier.fillMaxWidth().height(50.dp), onClick = { mode = DetailMode.MENU })
            }
            DetailMode.GUEST_COUNT -> {
                GuestCountStep(
                    label = "Number of guests",
                    guestCount = guestCount,
                    leastGuests = leastGuests,
                    mostGuests = head.guestsMax,
                    note = guestCountNote(guestCount, seats, leastGuests, head, isMergedGroup),
                    onGuestCountChanged = { guestCount = it },
                    confirmLabel = "Seat guests",
                    isSaving = isSaving,
                    onBack = { mode = DetailMode.MENU },
                    onConfirm = { onSeatGuests(guestCount, moreComing) },
                    moreComing = moreComing,
                    onMoreComingChanged = { moreComing = it }
                )
            }
            DetailMode.CHANGE_GUEST_COUNT -> {
                GuestCountStep(
                    label = "Update guest count",
                    guestCount = guestCount,
                    leastGuests = leastGuests,
                    mostGuests = head.guestsMax,
                    note = guestCountNote(guestCount, seats, leastGuests, head, isMergedGroup),
                    onGuestCountChanged = { guestCount = it },
                    confirmLabel = "Save",
                    isSaving = isSaving,
                    onBack = { mode = DetailMode.MENU },
                    onConfirm = { onUpdateGuestCount(guestCount, moreComing) },
                    moreComing = moreComing,
                    onMoreComingChanged = { moreComing = it }
                )
            }
        }
    }
    // matchParentSize: the bar follows the panel's height and never makes the panel taller.
    Box(Modifier.matchParentSize()) {
        com.saporini.mobile_desktop.core.ui.PlatformVerticalScrollbar(
            panelScroll,
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 22.dp, bottom = 22.dp, end = 6.dp).width(3.dp)
        )
    }
    }
    }

    if (showFreeConfirm) {
        AppDialog(
            title = "Set Table $tableTitle free?",
            onDismiss = { showFreeConfirm = false },
            maxWidth = 460.dp,
            buttons = {
                KitButton("Cancel", { showFreeConfirm = false }, style = ButtonStyle.SECONDARY)
                KitButton("Set free", { showFreeConfirm = false; onSetAvailability(true) })
            }
        ) {
            Text("The guests are leaving without ordering. The table will be free for others.", fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = TableMuted)
        }
    }

    if (showAvailabilityConfirm) {
        AppDialog(
            title = "Mark Table $tableTitle unavailable?",
            onDismiss = { showAvailabilityConfirm = false },
            maxWidth = 460.dp,
            buttons = {
                KitButton("Cancel", { showAvailabilityConfirm = false }, style = ButtonStyle.SECONDARY)
                KitButton("Mark unavailable", { showAvailabilityConfirm = false; onSetAvailability(false) }, style = ButtonStyle.DANGER)
            }
        ) {
            Text("It can't be used for seating or bookings until you make it available again.", fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = TableMuted)
        }
    }

    if (showSeparateConfirm) {
        // All or some; with two tables, some is the same as all. With guests, the tables that leave become free and one
        // table keeps the guests and the order (user, 2026-11-01).
        val canPickSome = tables.size > 2
        val seated = table.state == TableVisualState.Occupied || table.state == TableVisualState.Ordered || table.state == TableVisualState.BillPending ||
            table.state == TableVisualState.Paid
        var keeping by remember { mutableStateOf(primaryTableId ?: tables.first().id) }
        AppDialog(
            title = "Unmerge $tableTitle?",
            onDismiss = { showSeparateConfirm = false },
            maxWidth = 480.dp,
            buttons = {
                // A phone's window has room for short words only.
                val narrow = LocalNarrowDialog.current
                val unmergeAll = {
                    showSeparateConfirm = false
                    if (seated) keeping?.let(onUnmergeKeeping) else primaryTableId?.let(onSeparateTables)
                    Unit
                }
                KitButton("Cancel", { showSeparateConfirm = false }, style = ButtonStyle.SECONDARY)
                if (canPickSome) {
                    KitButton(if (narrow) "Some" else "Some of them", { showSeparateConfirm = false; onUnmergeSome() }, style = ButtonStyle.PRIMARY_OUTLINE)
                    KitButton(if (narrow) "All" else "All of them", unmergeAll)
                } else {
                    KitButton("Unmerge", unmergeAll)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when {
                        seated && canPickSome -> "The tables you unmerge become free. Unmerge all of them, or only some?"
                        seated -> "The other table becomes free."
                        canPickSome -> "Unmerge all of these tables, or only some?"
                        else -> "These tables go back to being separate."
                    },
                    fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = TableMuted
                )
                if (seated) {
                    Text("Which table keeps the guests and the order?", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TableInk)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tables.sortedWith(compareBy({ it.label.length }, { it.label })).forEach { one ->
                            com.saporini.mobile_desktop.core.components.ChoiceChip(one.label, keeping == one.id, { keeping = one.id })
                        }
                    }
                }
            }
        }
    }
}

private enum class DetailMode { MENU, GUEST_COUNT, CHANGE_GUEST_COUNT, MORE_TIME }

// One booking on the table: whose, when, how many and what it celebrates; a tap opens it in Reservations.
@Composable
private fun TableBookingCard(booking: TableBooking, caption: String, narrow: Boolean, onOpen: (() -> Unit)?, onMove: (() -> Unit)? = null) {
    val festive = booking.icon != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (festive) Color(0xFFFFF7E6) else Color(0xFFF6F7F5))
            .border(1.dp, if (festive) Color(0xFFF2DDB0) else TableBorder, RoundedCornerShape(10.dp))
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            if (booking.icon != null) Text(booking.icon, fontSize = 20.sp)
            else androidx.compose.foundation.Image(
                org.jetbrains.compose.resources.painterResource(mobile_desktop.shared.generated.resources.Res.drawable.reservation_desk),
                null, Modifier.size(32.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                listOfNotNull(caption, booking.start?.clockTime()).joinToString(" · "),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = TableMuted, maxLines = 1
            )
            Text(
                listOfNotNull(
                    booking.name?.takeIf { it.isNotBlank() } ?: "Guest",
                    booking.guests?.let { if (it == 1) "1 guest" else "$it guests" }
                ).joinToString(" · "),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.sp, color = TableInk,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            booking.occasion?.let {
                Text(it, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Color(0xFF8A5A0B), maxLines = 1)
            }
            // A phone keeps the name whole: Move goes under it.
            if (onMove != null && narrow) {
                Spacer(Modifier.height(4.dp))
                MoveBookingChip(onMove)
            }
        }
        if (onMove != null && !narrow) MoveBookingChip(onMove)
        if (onOpen != null) Icon(Icons.Filled.ChevronRight, "Open the booking", Modifier.size(22.dp), tint = TableMuted)
    }
}

@Composable
private fun MoveBookingChip(onMove: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White).border(1.dp, TableBorder, RoundedCornerShape(50))
            .clickable(onClick = onMove).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Filled.SyncAlt, null, Modifier.size(15.dp), tint = TableInk)
        Text("Move", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = TableInk)
    }
}

@Composable
private fun HandleButton(open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF8A5A0B)).clickable(onClick = onClick).padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Handle", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Color.White)
        Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, Modifier.size(16.dp), tint = Color.White)
    }
}

private enum class ClashAction(val label: String, val icon: ImageVector) {
    SET_FREE("Set free", Icons.Filled.CheckCircle),
    PAY("Pay", Icons.Filled.Payments),
    MOVE_BOOKING("Move the booking", Icons.Filled.SyncAlt),
    MOVE_GUESTS("Move these guests", Icons.Filled.SyncAlt),
    MORE_TIME("Give more time", Icons.Filled.Alarm),
    BOOKING_WAITS("Booking waits", Icons.Filled.Groups)
}

// What can be done about the booking, the best first (user, 2026-11-01). A move that can't happen drops out, so Give more
// time leads when no free table fits.
private fun clashActions(state: TableVisualState, clash: TableClash, canMoveBooking: Boolean, canMoveGuests: Boolean): List<ClashAction> {
    val wanted = when {
        state == TableVisualState.Paid -> listOf(ClashAction.SET_FREE, ClashAction.MOVE_BOOKING)
        state == TableVisualState.BillPending -> listOf(ClashAction.PAY, ClashAction.MOVE_BOOKING, ClashAction.MORE_TIME)
        state == TableVisualState.Occupied && !clash.waiting -> listOf(ClashAction.MOVE_GUESTS, ClashAction.MOVE_BOOKING, ClashAction.MORE_TIME)
        else -> listOf(ClashAction.MOVE_BOOKING, ClashAction.MOVE_GUESTS, ClashAction.MORE_TIME)
    }
    val all = if (clash.waiting || state == TableVisualState.Paid) wanted else wanted + ClashAction.BOOKING_WAITS
    return all.filter { action ->
        when (action) {
            ClashAction.MOVE_BOOKING -> canMoveBooking
            ClashAction.MOVE_GUESTS -> canMoveGuests
            ClashAction.MORE_TIME -> clash.moreTime.isNotEmpty()
            else -> true
        }
    }
}

// What the picked number means. Staff aren't told who hears about an unusual number (user, 2026-10-31).
private fun guestCountNote(guests: Int, seats: Int, least: Int, head: FloorPlanTable, merged: Boolean): Pair<String, Color>? = when {
    head.guestsUsual != null && guests > head.guestsUsual ->
        ("This is unusual for $seats seats." + if (guests == head.guestsMax) " ${head.guestsMax} is the most." else "") to Color(0xFFB7791F)
    merged -> "Merged tables seat at least $least. Unmerge them for fewer." to Color(0xFF71736E)
    else -> null
}

@Composable
private fun GuestCountStep(
    label: String,
    guestCount: Int,
    leastGuests: Int,
    mostGuests: Int?,
    note: Pair<String, Color>?,
    onGuestCountChanged: (Int) -> Unit,
    confirmLabel: String,
    isSaving: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    moreComing: Boolean = false,
    onMoreComingChanged: (Boolean) -> Unit = {}
) {
    Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF303033))
    Spacer(Modifier.height(10.dp))
    GuestCountPicker(guestCount = guestCount, leastGuests = leastGuests, mostGuests = mostGuests, onGuestCountChanged = onGuestCountChanged)
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onMoreComingChanged(!moreComing) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Checkbox(
            checked = moreComing,
            onCheckedChange = onMoreComingChanged,
            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = TableGreen)
        )
        Text("More will come", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xFF303033))
    }
    if (note != null) {
        Spacer(Modifier.height(10.dp))
        Text(note.first, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = note.second)
    }
    Spacer(Modifier.height(22.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DialogActionButton(
            text = "Back",
            icon = null,
            modifier = Modifier.weight(1f).height(54.dp),
            onClick = onBack
        )
        DialogActionButton(
            text = confirmLabel,
            icon = Icons.Filled.CheckCircle,
            modifier = Modifier.weight(1f).height(54.dp),
            dark = true,
            isLoading = isSaving,
            onClick = onConfirm
        )
    }
}

@Composable
private fun ActionList(
    table: FloorPlanTable,
    clashShown: Boolean,
    onViewBooking: (() -> Unit)?,
    hasOrder: Boolean,
    isMergedGroup: Boolean,
    isSaving: Boolean,
    onSeatCustomers: () -> Unit,
    onSeatReservation: () -> Unit,
    onChangeGuestCount: () -> Unit,
    onStartOrder: () -> Unit,
    onViewOrder: () -> Unit,
    onOpenEmptyOrder: () -> Unit,
    onMoveGuests: () -> Unit,
    onJoinTables: () -> Unit,
    onSeparateTables: () -> Unit,
    onGoToPayment: () -> Unit,
    onSetBillRequested: (Boolean) -> Unit,
    onMarkUnavailable: () -> Unit,
    onMarkAvailable: () -> Unit,
    onSetFree: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (table.state) {
            // A late booking's table is still free to seat (its guests, or others once the hold ends).
            TableVisualState.Free, TableVisualState.HoldEnding -> {
                DialogActionButton(
                    text = "Seat customers", icon = Icons.Filled.Groups,
                    modifier = Modifier.fillMaxWidth().height(50.dp), dark = true,
                    onClick = onSeatCustomers
                )
                if (isMergedGroup) {
                    MergedGroupButtons(isSaving, onSeparateTables, onJoinTables)
                } else {
                    DialogActionButton(
                        text = "Join tables", icon = Icons.Outlined.JoinInner,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        onClick = onJoinTables
                    )
                }
                if (table.state == TableVisualState.HoldEnding && onViewBooking != null) DialogActionButton(
                    text = "View reservation", icon = Icons.Filled.Event,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = onViewBooking
                )
                DialogActionButton(
                    text = "Mark unavailable", icon = Icons.Filled.Block,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = onMarkUnavailable
                )
            }
            TableVisualState.Reserved -> {
                DialogActionButton(
                    text = "Seat reservation", icon = Icons.Filled.Groups,
                    modifier = Modifier.fillMaxWidth().height(50.dp), dark = true,
                    onClick = onSeatReservation
                )
                if (onViewBooking != null) DialogActionButton(
                    text = "View reservation", icon = Icons.Filled.Event,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = onViewBooking
                )
                if (isMergedGroup) {
                    MergedGroupButtons(isSaving, onSeparateTables, onJoinTables)
                } else {
                    DialogActionButton(
                        text = "Join tables", icon = Icons.Outlined.JoinInner,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        onClick = onJoinTables
                    )
                }
            }
            TableVisualState.Occupied, TableVisualState.Ordered, TableVisualState.BillPending -> {
                // Nothing ordered yet: it stays Start order and opens that same order (user, 2026-11-01).
                val emptyOrder = hasOrder && table.state == TableVisualState.Occupied
                DialogActionButton(
                    text = if (hasOrder && !emptyOrder) "View order" else "Start order",
                    icon = Icons.Filled.List,
                    modifier = Modifier.fillMaxWidth().height(50.dp), dark = !clashShown,
                    onClick = when {
                        emptyOrder -> onOpenEmptyOrder
                        hasOrder -> onViewOrder
                        else -> onStartOrder
                    }
                )
                // Dishes are added in the order itself, not here (user, 2026-11-01).
                // One button for the bill (user, 2026-11-01): "Request bill", then "Pay" once the guests asked for it.
                // An orange table's order has no dishes, so there's no bill yet.
                if (hasOrder && table.state != TableVisualState.Occupied) {
                    if (table.state == TableVisualState.BillPending) {
                        if (!clashShown) DialogActionButton(
                            text = "Pay", icon = Icons.Filled.Payments,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            onClick = onGoToPayment
                        )
                    } else {
                        DialogActionButton(
                            text = "Request bill", icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            modifier = Modifier.fillMaxWidth().height(50.dp), isLoading = isSaving,
                            onClick = { onSetBillRequested(true) }
                        )
                    }
                }
                DialogActionButton(
                    text = "Change guest count", icon = Icons.Filled.Groups,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = onChangeGuestCount
                )
                if (!clashShown) DialogActionButton(
                    text = "Move guests", icon = Icons.Filled.SyncAlt,
                    modifier = Modifier.fillMaxWidth().height(50.dp), isLoading = isSaving,
                    onClick = onMoveGuests
                )
                // Seated but nothing ordered: they may decide not to stay. An order with no dishes ends with it (server).
                if (table.state == TableVisualState.Occupied) DialogActionButton(
                    text = "Set free", icon = Icons.Filled.CheckCircle,
                    modifier = Modifier.fillMaxWidth().height(50.dp), isLoading = isSaving,
                    onClick = onSetFree
                )
                // Close as unpaid is done in the order, not from the table (user, 2026-11-01).
                // A table whose bill was requested joins nothing.
                val canJoin = table.state != TableVisualState.BillPending
                if (isMergedGroup) {
                    MergedGroupButtons(isSaving, onSeparateTables, onJoinTables, canMergeNew = canJoin)
                } else if (canJoin) {
                    DialogActionButton(
                        text = "Join tables", icon = Icons.Outlined.JoinInner,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        onClick = onJoinTables
                    )
                }
            }
            TableVisualState.Paid -> {
                if (!clashShown) DialogActionButton(
                    text = "Set free", icon = Icons.Filled.CheckCircle,
                    modifier = Modifier.fillMaxWidth().height(50.dp), dark = true, isLoading = isSaving,
                    onClick = onSetFree
                )
                if (hasOrder) DialogActionButton(
                    text = "View order", icon = Icons.Filled.List,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = onViewOrder
                )
            }
            TableVisualState.Unavailable -> {
                DialogActionButton(
                    text = "Mark available", icon = Icons.Filled.CheckCircle,
                    modifier = Modifier.fillMaxWidth().height(50.dp), dark = true, isLoading = isSaving,
                    onClick = onMarkAvailable
                )
            }
        }
    }
}

@Composable
private fun GuestCountPicker(
    guestCount: Int,
    leastGuests: Int,
    mostGuests: Int?,
    onGuestCountChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GuestCountButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Remove guest",
            onClick = { onGuestCountChanged((guestCount - 1).coerceAtLeast(leastGuests)) }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = TableBorder, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$guestCount ${if (guestCount == 1) "guest" else "guests"}",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = TableInk
            )
        }

        GuestCountButton(
            icon = Icons.Filled.Add,
            contentDescription = "Add guest",
            enabled = mostGuests == null || guestCount < mostGuests,
            onClick = { onGuestCountChanged(guestCount + 1) }
        )
    }
}

@Composable
private fun GuestCountButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F3F0))
            .border(width = 1.dp, color = TableBorder, shape = RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp), tint = TableInk)
    }
}

@Composable
private fun StatusCaption(text: String) {
    Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = TableMuted)
}

// The nav bar's Online green (#18C637), a little darker.
private val PanelStatusGreen = Color(0xFF14AE30)

@Composable
private fun StatusPill(text: String, color: Color, small: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .padding(horizontal = if (small) 11.dp else 18.dp, vertical = if (small) 5.dp else 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = if (small) 12.sp else 13.sp, letterSpacing = 0.sp, maxLines = 1, color = Color.White)
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color(0xFF303033)
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF696969))
        Spacer(Modifier.width(12.dp))
        // The name keeps one line; a long value (an order number on a phone) takes the rest of the row, on two lines if it must.
        Text(
            text = label, maxLines = 1, softWrap = false,
            fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color(0xFF3E3E42)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f),
            fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 15.sp, color = valueColor
        )
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    isLoading: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit = {}
) {
    val foreground = if (dark) Color.White else if (danger) TableDanger else Color(0xFF2E2E31)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (dark) TableGreen else Color.White)
            .border(width = if (dark) 0.dp else 1.dp, color = TableBorder, shape = RoundedCornerShape(8.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = foreground, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = foreground)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp,
            maxLines = 1, softWrap = false, color = foreground
        )
    }
}

private fun FloorPlanTable.statusLabel(): String =
    statusText ?: when (state) {
        TableVisualState.Free -> "Free"
        TableVisualState.Occupied -> "Occupied"
        TableVisualState.Ordered -> "Ordered"
        TableVisualState.Reserved -> "Reserved"
        TableVisualState.HoldEnding -> "Hold ends soon"
        TableVisualState.BillPending -> "Bill Pending"
        TableVisualState.Paid -> "Paid"
        TableVisualState.Unavailable -> "Unavailable"
    }

private fun FloorPlanTable.statusColor(): Color = tableStatusColor(state)

private fun List<FloorPlanTable>.toServiceSummaryTable(): FloorPlanTable {
    val first = first()
    val activeOrders = mapNotNull { table ->
        table.orderLabel?.let { orderLabel -> table.orderId to orderLabel }
    }.distinctBy { it.second }
    val firstOrder = activeOrders.firstOrNull()
    val orderLabelSummary = activeOrders.joinToString(", ") { it.second }.takeIf { it.isNotBlank() }
    val totalGuests = mapNotNull { it.guestCount }.takeIf { it.isNotEmpty() }?.sum()
    val summaryState = when {
        any { it.state == TableVisualState.BillPending } -> TableVisualState.BillPending
        any { it.state == TableVisualState.Ordered } && none { it.state == TableVisualState.Paid } -> TableVisualState.Ordered
        any { it.state == TableVisualState.Paid } -> TableVisualState.Paid
        any { it.state == TableVisualState.Occupied } -> TableVisualState.Occupied
        any { it.state == TableVisualState.Reserved } -> TableVisualState.Reserved
        all { it.state == TableVisualState.Unavailable } -> TableVisualState.Unavailable
        else -> TableVisualState.Free
    }

    return first.copy(
        state = summaryState,
        orderId = firstOrder?.first,
        orderLabel = orderLabelSummary,
        orderStatus = firstNotNullOfOrNull { it.orderStatus },
        foodStatus = firstNotNullOfOrNull { it.foodStatus },
        statusText = when {
            summaryState == TableVisualState.Occupied -> "Seated"
            activeOrders.isNotEmpty() -> first.statusText ?: "In Progress"
            else -> first.statusText
        },
        guestCount = totalGuests,
        seatedAt = mapNotNull { it.seatedAt }.minOrNull()
    )
}

private fun FloorPlanTable.seatedSince(): String =
    if (state == TableVisualState.Free) "--" else seatedAt?.toSeatedTimeLabel() ?: "Just now"

// The restaurant's own clock, not UTC (user, 2026-11-01).
private fun String.toSeatedTimeLabel(): String = runCatching {
    val at = kotlin.time.Instant.parse(this).toLocalDateTime(com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime.zone)
    "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
}.getOrDefault(substringAfter('T', missingDelimiterValue = "").take(5))

// A merged table: take it apart, or join more tables to it (user, 2026-10-31).
@Composable
private fun MergedGroupButtons(isSaving: Boolean, onUnmerge: () -> Unit, onMergeNew: () -> Unit, canMergeNew: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DialogActionButton(
            text = "Unmerge", icon = Icons.Filled.CallSplit,
            modifier = Modifier.weight(1f).height(50.dp), isLoading = isSaving,
            onClick = onUnmerge
        )
        if (canMergeNew) {
            DialogActionButton(
                text = "Merge new", icon = Icons.Outlined.JoinInner,
                modifier = Modifier.weight(1f).height(50.dp),
                onClick = onMergeNew
            )
        }
    }
}
