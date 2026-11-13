package com.saporini.mobile_desktop.pos.orders

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.saporini.mobile_desktop.pos.menu.ui.item.ItemDetailDialog
import com.saporini.mobile_desktop.pos.menu.ui.item.MenuItem
import com.saporini.mobile_desktop.pos.menu.ui.item.DraftIngredient
import com.saporini.mobile_desktop.pos.menu.ui.item.DraftVariant
import com.saporini.mobile_desktop.pos.menu.ui.item.DraftOptionGroup
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.filled.Reply
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuValidationToast
import com.saporini.mobile_desktop.pos.menu.ui.menu.ToastPlacement
import com.saporini.mobile_desktop.core.components.AnimatedStatusIcon
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import com.saporini.mobile_desktop.core.ui.PlatformVerticalScrollbar
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.RoomService
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material.icons.outlined.ZoomOutMap
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.ui.*
import com.saporini.mobile_desktop.pos.menu.ui.MenuSearchBox
import com.saporini.mobile_desktop.pos.menu.ui.MenuCategoryIcon
import com.saporini.mobile_desktop.pos.menu.ui.MenuCategory
import com.saporini.mobile_desktop.pos.menu.ui.item.MenuItemCard
import org.koin.compose.koinInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mobile_desktop.shared.generated.resources.Res
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ResourceItem
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.math.roundToInt

@Composable
fun OrdersScreen(
    modifier: Modifier = Modifier, onPaymentRequested: (orderId: String) -> Unit = {}, historyOnly: Boolean = false,
    focusOrderId: String? = null, onFocusHandled: () -> Unit = {}, openItems: Boolean = false,
    startOrder: Pair<String, Int>? = null, onStartOrderHandled: () -> Unit = {},
    onTableFreed: (tableNumber: String) -> Unit = {}, onOrderReopened: (orderId: String) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    historySearch: String? = null, onHistorySearchHandled: () -> Unit = {}
) {
    val model = koinInject<OrdersScreenModel>(qualifier = if(historyOnly) org.koin.core.qualifier.named("pos-history") else null)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(model, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_START) model.setActive(true)
            if(event == Lifecycle.Event.ON_STOP) model.setActive(false)
        }
        owner.lifecycle.addObserver(observer)
        model.setActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(observer); model.setActive(false); model.onDispose() }
    }
    OrdersContent(model, modifier, onPaymentRequested, focusOrderId, onFocusHandled, openItems, startOrder, onStartOrderHandled, onTableFreed, onOrderReopened, onOpenHistory, historySearch, onHistorySearchHandled)
}

/** Separate from lifecycle ownership to support deterministic screenshots and previews. */
@Composable
fun OrdersContent(
    model: OrdersScreenModel, modifier: Modifier = Modifier, onPaymentRequested: (orderId: String) -> Unit = {},
    focusOrderId: String? = null, onFocusHandled: () -> Unit = {}, openItems: Boolean = false,
    startOrder: Pair<String, Int>? = null, onStartOrderHandled: () -> Unit = {},
    // Closed as unpaid: on to the table it freed (user, 2026-11-01).
    onTableFreed: (tableNumber: String) -> Unit = {},
    onOrderReopened: (orderId: String) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    // History opened from a table: that table's orders.
    historySearch: String? = null,
    onHistorySearchHandled: () -> Unit = {}
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var addingItems by remember { mutableStateOf(false) }
    var editChooser by remember { mutableStateOf(false) }
    var progressChooser by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<String?>(null) }
    var mineOnly by remember { mutableStateOf(false) }
    var kitchen by remember { mutableStateOf<OrderFulfillmentStatus?>(null) }
    var orderStatus by remember { mutableStateOf<OrderStatus?>(null) }
    var fullDetails by remember { mutableStateOf(false) }
    var orderButtonPosition by rememberSaveable { mutableFloatStateOf(.84f) }
    LaunchedEffect(state.scope) { creating = false; editing = false; addingItems = false; editChooser = false; progressChooser = false; action = null; fullDetails = false; mineOnly = false; kitchen = null; orderStatus = null }
    var openingItems by remember { mutableStateOf(false) }
    // Opened from Kitchen Status ("Go to order"): that order shows, so its ready food can be served.
    // An order opened from elsewhere (a booking, Kitchen Status) stands out in the list for a few seconds (user, 2026-11-02).
    var flashOrderId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flashOrderId) {
        if (flashOrderId != null) { kotlinx.coroutines.delay(4_000); flashOrderId = null }
    }
    LaunchedEffect(focusOrderId, state.scope) {
        val id = focusOrderId ?: return@LaunchedEffect
        if (state.scope == null) return@LaunchedEffect
        flashOrderId = id
        // History opens on an order only from No action taken: it's among the unpaid ones.
        if (model.historyOnly) model.setFilter(OrderListFilter(mode = OrderListMode.HISTORY, unpaidOnly = true))
        model.selectOrder(id)
        if (openItems) { editing = true; openingItems = true }
        onFocusHandled()
    }
    // The items window is open while the order loads; if it can't be loaded there's nothing to edit.
    LaunchedEffect(openingItems) {
        if (!openingItems) return@LaunchedEffect
        // From the model itself: the screen's copy of its state is a moment behind, so it can look as if nothing is loading yet.
        val opened = model.state.first { it.selectedOrder != null || !it.isLoadingDetails }
        if (opened.selectedOrder == null) editing = false
        openingItems = false
    }
    LaunchedEffect(historySearch, state.scope) {
        val table = historySearch ?: return@LaunchedEffect
        if (state.scope == null) return@LaunchedEffect
        model.setFilter(OrderListFilter(mode = OrderListMode.HISTORY))
        model.setSearchQuery(table)
        onHistorySearchHandled()
    }
    // Tables → Start order: the order exists at once, and its dishes are the next thing to add.
    var startOrderProblem by remember { mutableStateOf<String?>(null) }
    var unpaidDone by remember { mutableStateOf<String?>(null) }
    // Only History reopens orders (user, 2026-11-01). For payment: straight to its payment.
    val inHistory = model.historyOnly || state.filter.mode == OrderListMode.HISTORY
    val canReopenForPayment = inHistory && model.hasPermission("ORDER_CLOSE")
    val canReopenOrder = inHistory && model.hasPermission("ORDER_REOPEN")
    var reopenAsking by remember { mutableStateOf<Order?>(null) }
    val reopenForPayment: (Order) -> Unit = { reopened ->
        scope.launch {
            model.reopenForPaymentFromHistory(reopened.id).fold(
                { onPaymentRequested(reopened.id) },
                { startOrderProblem = it.message ?: "Could not reopen the order" }
            )
        }
    }
    reopenAsking?.let { reopened ->
        com.saporini.mobile_desktop.core.components.AppDialog(
            title = "Reopen ${reopened.orderNumber}?",
            onDismiss = { reopenAsking = null },
            maxWidth = 460.dp,
            buttons = {
                com.saporini.mobile_desktop.core.components.KitButton("Cancel", { reopenAsking = null }, style = com.saporini.mobile_desktop.core.components.ButtonStyle.SECONDARY)
                com.saporini.mobile_desktop.core.components.KitButton("Reopen", {
                    reopenAsking = null
                    scope.launch {
                        model.reopenFromHistory(reopened.id).fold(
                            { onOrderReopened(reopened.id) },
                            { startOrderProblem = it.message ?: "Could not reopen the order" }
                        )
                    }
                })
            }
        ) {
            Text("It goes back to Active orders.", fontFamily = Inter(), fontSize = 14.sp, color = OrderMuted)
        }
    }
    // The order window opens at once and fills in when the order is made.
    var starting by remember { mutableStateOf(false) }
    // New order opens on the menu; Start order and Edit order open on the order's items (user, 2026-11-01).
    var newOrder by remember { mutableStateOf(false) }
    LaunchedEffect(startOrder, state.scope) {
        val (tableId, guests) = startOrder ?: return@LaunchedEffect
        if (state.scope == null) return@LaunchedEffect
        starting = true
        try {
            model.operations.createOrder(CreateOrderInput(tableId = tableId, orderType = OrderType.DINE_IN, guestCount = guests))
                .fold({ editing = true }, { startOrderProblem = it.message ?: "Could not start the order" })
        } finally {
            starting = false
            onStartOrderHandled()
        }
    }
    com.saporini.mobile_desktop.core.components.BottomToast(startOrderProblem, isError = true, onDismiss = { startOrderProblem = null })
    com.saporini.mobile_desktop.core.components.BottomToast(unpaidDone, onDismiss = { unpaidDone = null })
    val visible = state.visibleOrders.filter { (!mineOnly || it.createdBy == state.scope?.userId) && (orderStatus == null || it.status == orderStatus) && (kitchen == null || (it.status.showsFulfillmentProgress && it.fulfillmentStatus == kitchen)) }
    val showEmptyCreate = visible.isEmpty() && !state.isLoading && state.can("ORDER_CREATE") &&
        state.searchQuery.isBlank() && !mineOnly && kitchen == null && orderStatus == null && state.filter.mode == OrderListMode.OPEN
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val isPhone = isPhoneWindow()
        val compact = maxWidth < 1000.dp
        val order = state.selectedOrder
        var detailsClosedByUser by remember { mutableStateOf(false) }
        LaunchedEffect(compact, visible.firstOrNull()?.id, state.selectedOrderId, state.isLoading, detailsClosedByUser) {
            if(!compact && !state.isLoading && state.selectedOrderId == null && !detailsClosedByUser && startOrder == null && !starting) visible.firstOrNull()?.id?.let(model::selectOrder)
        }
        Column(Modifier.fillMaxSize().padding(start = if(compact) 16.dp else 28.dp, top = if(compact) 16.dp else 28.dp, end = if(compact) 16.dp else 28.dp, bottom = if(compact) 10.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if(compact && editing && order != null) {
                OrderComposer(model, order, onBack = { editing = false })
            } else if((compact || fullDetails) && state.selectedOrderId != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ fullDetails = false; model.closeOrderDetails() }) { Text("‹  Orders") }
                    Spacer(Modifier.weight(1f))
                    TextButton({ model.refresh() }) { Text("Refresh") }
                }
                if(order == null) { if(state.isLoadingDetails) CircularProgressIndicator() else OrderText("Could not load order. Please refresh.") }
                else OrderDetails(order, state, { editChooser = true }, { action = it }, Modifier.weight(1f), reopenForPayment = canReopenForPayment, reopenOrder = canReopenOrder)
                state.error?.let { OrderError(it.message, model::clearError) }
            } else {
                OrdersToolbar(
                    query = state.searchQuery,
                    onQueryChange = model::setSearchQuery,
                    orderStatus = orderStatus,
                    onOrderStatusChange = { orderStatus = it },
                    foodStatus = kitchen,
                    onFoodStatusChange = { kitchen = it },
                    mineOnly = mineOnly,
                    onMineChange = { mineOnly = it; model.historyMine(it) },
                    onOpenHistory = onOpenHistory,
                    historyOnly = model.historyOnly,
                    historyStatus = state.filter.status,
                    onHistoryStatusChange = { model.setFilter(state.filter.copy(status = it, unpaidOnly = false)) },
                    historyUnpaid = state.filter.unpaidOnly,
                    onHistoryUnpaid = { model.setFilter(OrderListFilter(mode = OrderListMode.HISTORY, unpaidOnly = true)) },
                    // The server's total; All | Mine and the Status button narrow only what's loaded.
                    count = if (state.isLoading && state.orders.isEmpty()) null
                        else if (mineOnly || kitchen != null || orderStatus != null) visible.size.toLong() else state.historyTotal
                )
                state.error?.let { OrderError(it.message, model::clearError) }
                state.refreshWarning?.let { OrderError(it, model::clearError) }
                if(state.needsReconciliation) OutlinedButton({ scope.launch { if(model.refreshNow()) action = "Check last change" } }) { Text("Refresh and check last change") }
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if(state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.Center), color = OrderGreen)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if(visible.isEmpty() && !state.isLoading) {
                        OrdersEmptyListPanel(
                            title = if(state.searchQuery.isNotBlank() || mineOnly || kitchen != null || orderStatus != null) "No matching orders" else if(state.filter.mode == OrderListMode.OPEN) "No active orders" else if(state.filter.unpaidOnly) "No unpaid orders" else "No order history",
                            showCreate = showEmptyCreate,
                            modifier = Modifier.weight(if(compact) 1f else 1.65f).fillMaxHeight(),
                            onCreate = { creating = true }
                        )
                    } else if (visible.isEmpty()) {
                        // First load: the list's cards as pulsing grey shapes.
                        OrdersListSkeleton(Modifier.weight(if(compact) 1f else 1.65f).fillMaxHeight())
                    } else {
                        OrdersListPanel(
                            orders = visible,
                            hasNext = state.historyHasNext,
                            loadingMore = state.historyLoadingMore || state.isLoading,
                            pageError = state.error != null,
                            onLoadMore = model::loadMoreHistory,
                            selectedId = state.selectedOrderId,
                            flashId = flashOrderId,
                            compact = compact,
                            modifier = Modifier.weight(if(compact) 1f else 1.65f).fillMaxHeight(),
                            onSelect = {
                                detailsClosedByUser = false
                                model.selectOrder(it)
                            }
                        )
                    }
                    if(!compact) Surface(Modifier.weight(.9f).fillMaxHeight(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            when {
                                state.isLoadingDetails -> CircularProgressIndicator()
                                order != null -> DesktopOrderDetailsPanel(
                                    order,
                                    state,
                                    onEditItems = { editing = true },
                                    onEditInfo = { action = "Edit information" },
                                    onEditProgress = { progressChooser = true },
                                    onAction = { action = it },
                                    onPayment = { onPaymentRequested(order.id) },
                                    onRequestBill = { scope.launch { model.operations.requestBill(order.id) } },
                                    canReopenForPayment = canReopenForPayment,
                                    canReopenOrder = canReopenOrder,
                                    onReopenForPayment = { reopenForPayment(order) },
                                    onClose = {
                                    detailsClosedByUser = true
                                    model.closeOrderDetails()
                                })
                                else -> OrdersNoSelectionPanel()
                            }
                        }
                    }
                }
            }
        }
        if (state.can("ORDER_CREATE") && !showEmptyCreate && !creating && !editing && !addingItems &&
            !fullDetails && (!compact || state.selectedOrderId == null)) {
            val maxButtonOffset = with(LocalDensity.current) {
                (maxHeight - 84.dp).coerceAtLeast(0.dp).toPx()
            }
            NewOrderEdgeButton(
                onClick = { creating = true },
                enabled = !state.isSaving && !state.needsReconciliation,
                modifier = Modifier.align(Alignment.TopStart)
                    .offset { IntOffset(0, (orderButtonPosition * maxButtonOffset).roundToInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (maxButtonOffset > 0f) {
                                orderButtonPosition = (orderButtonPosition + delta / maxButtonOffset).coerceIn(0f, 1f)
                            }
                        }
                    )
            )
        }
        if(creating) OrderStartForm(model, { creating = false }, { creating = false; newOrder = true; editing = true })
        if((editing || starting) && !compact && (order != null || starting || openingItems)) OrderItemsEditModal(
            order = order.takeIf { !starting },
            state = state,
            model = model,
            startOnMenu = newOrder,
            onDismiss = { editing = false; newOrder = false },
            onSendAll = { action = "Send to kitchen" },
            onVoidItem = { id -> action = "Void item:$id" }
        )
        if(addingItems && order != null) key(order.id) {
            OrderAddItemMenuModal(order, model, onDismiss = { addingItems = false })
        }
        if(editChooser && order != null) OrderEditChoiceModal(
            order = order,
            onDismiss = { editChooser = false },
            onItems = { editChooser = false; editing = true },
            onInfo = { editChooser = false; action = "Edit information" },
            onProgress = { editChooser = false; progressChooser = true }
        )
        if (progressChooser && order != null) OrderProgressChoiceModal(order, state, model, onDismiss = { progressChooser = false })
        if (action == "Send to kitchen" && order != null) {
            OrderKitchenSendDialog(order, model, onDismiss = { action = null })
        } else if (action == "Edit information" && order != null) {
            OrderInfoEditDialog(order, model, onDismiss = { action = null })
        } else if (action == "Close as unpaid" && order != null) {
            CloseUnpaidDialog(order, model, onDismiss = { action = null }, onDone = { closed ->
                action = null
                // On to the table it freed; an order on no table just says where it went.
                closed.tableNumber?.let(onTableFreed) ?: run { unpaidDone = "${closed.orderNumber} closed as unpaid. It's in History." }
            })
        } else if (action == "Reopen order" && order != null) {
            LaunchedEffect(order.id) { action = null; reopenAsking = order }
        } else if (action == "Reopen for payment" && order != null) {
            LaunchedEffect(order.id) { action = null; reopenForPayment(order) }
        } else if (action == "Pay" && order != null) {
            LaunchedEffect(order.id) { action = null; onPaymentRequested(order.id) }
        } else if (action != null) OrderActionForm(action!!, model, order, { action = null })
    }
}




private val NewOrderEdgeShape = GenericShape { size, _ ->
    val shoulder = size.height * .25f
    val end = size.width - shoulder
    moveTo(0f, 0f)
    cubicTo(0f, shoulder * .7f, size.width * .22f, shoulder, size.width * .42f, shoulder)
    lineTo(end, shoulder)
    cubicTo(end + shoulder * .55f, shoulder, size.width, shoulder * 1.45f, size.width, size.height * .5f)
    cubicTo(size.width, size.height * .5f + shoulder * .55f, end + shoulder * .55f, size.height - shoulder, end, size.height - shoulder)
    lineTo(size.width * .42f, size.height - shoulder)
    cubicTo(size.width * .22f, size.height - shoulder, 0f, size.height - shoulder * .7f, 0f, size.height)
    close()
}

@Composable
private fun NewOrderEdgeButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.width(100.dp).height(84.dp),
        shape = NewOrderEdgeShape,
        color = if (enabled) OrderGreen else OrderGreen.copy(alpha = .5f),
        contentColor = Color.White
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Now order", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp, letterSpacing = 0.sp, maxLines = 1)
        }
    }
}

@Composable private fun OrderItemsEditModal(
    order: Order?,
    state: OrdersUiState,
    model: OrdersScreenModel,
    startOnMenu: Boolean,
    onDismiss: () -> Unit,
    onSendAll: () -> Unit,
    onVoidItem: (String) -> Unit
) {
    // New order opens on the menu, so the first dish is one click away.
    var showAddItem by remember(order?.id) { mutableStateOf(startOnMenu && order != null && order.lineItems.orEmpty().none { it.active }) }
    Dialog(
        onDismissRequest = { if (!state.isSaving && order != null) { if (showAddItem) showAddItem = false else onDismiss() } },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.92f).fillMaxHeight(.88f),
            shape = RoundedCornerShape(16.dp), color = Color.White,
            border = BorderStroke(1.dp, OrderBorder), shadowElevation = 12.dp
        ) {
            // While Start order makes the order (user, 2026-11-01: "go smoother to the order"), the window is already open
            // with the menu's shapes, and the order fades in when it's there.
            androidx.compose.animation.AnimatedContent(order == null, transitionSpec = { com.saporini.mobile_desktop.core.components.loadingSwap(targetState) }) { starting ->
                if (starting || order == null) OrderComposerShapes()
                else OrderItemsEditPages(order, state, model, showAddItem, { showAddItem = it }, onDismiss, onSendAll, onVoidItem)
            }
        }
    }
}

@Composable private fun OrderItemsEditPages(
    order: Order,
    state: OrdersUiState,
    model: OrdersScreenModel,
    showAddItem: Boolean,
    onShowAddItem: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSendAll: () -> Unit,
    onVoidItem: (String) -> Unit
) {
    val activeItems = order.lineItems.orEmpty().filter { it.active }
    var selectedId by remember(order.id) { mutableStateOf(activeItems.firstOrNull()?.id) }
    val selected = activeItems.firstOrNull { it.id == selectedId }
    val scope = rememberCoroutineScope()
    var sendingId by remember(order.id) { mutableStateOf<String?>(null) }
    var sentIds by remember(order.id) { mutableStateOf(emptySet<String>()) }
    var sendError by remember(order.id) { mutableStateOf<String?>(null) }
    var editorDirty by remember(order.id, selectedId) { mutableStateOf(false) }
    // Ticked dishes go to the kitchen together; the top tick takes them all (user, 2026-11-01).
    var picked by remember(order.id) { mutableStateOf(emptySet<String>()) }
    val awaiting = activeItems.filter { it.awaitingKitchen && it.id !in sentIds }.map { it.id }
    val pickedNow = awaiting.filter { it in picked }
    val allPicked = awaiting.isNotEmpty() && pickedNow.size == awaiting.size
    val canWrite = order.editable && state.can("ORDER_UPDATE") && !state.isSaving && !state.needsReconciliation && sendingId == null
    LaunchedEffect(activeItems.map { it.id }) {
        if (selectedId != null && activeItems.none { it.id == selectedId }) selectedId = activeItems.firstOrNull()?.id
    }
    val pages = rememberPagerState(initialPage = if (showAddItem) 1 else 0, pageCount = { 2 })
    LaunchedEffect(showAddItem) {
        if (pages.currentPage != (if (showAddItem) 1 else 0)) pages.animateScrollToPage(if (showAddItem) 1 else 0, animationSpec = tween(300))
    }
    run {
        run {
            // Keep both pages composed so the editor's selection, scroll and draft survive navigation.
            HorizontalPager(
                state = pages, modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false, beyondViewportPageCount = 1
            ) { page ->
                if (page == 0) {
                        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Edit order items", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = OrderInk)
                                Spacer(Modifier.width(16.dp))
                                OrderEditSmallIconText(Icons.Outlined.ContentCopy, order.displayOrderNumber(), OrderInk)
                                Spacer(Modifier.width(16.dp))
                                OrderListPill(Icons.Outlined.TableRestaurant, order.tableName ?: order.tableNumber?.let { "Table $it" } ?: "Table -", modifier = Modifier.widthIn(min = 70.dp, max = 96.dp))
                                Spacer(Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        if (allPicked) { if (!state.isSaving) { picked = emptySet(); onSendAll() } }
                                        else if (pickedNow.isNotEmpty()) scope.launch {
                                            sendError = null
                                            try {
                                                for (id in pickedNow) {
                                                    sendingId = id
                                                    val sent = model.operations.fireItem(order.id, id).fold(
                                                        { sentIds = sentIds + id; true },
                                                        { sendError = it.message ?: "Could not send this item to the kitchen"; false }
                                                    )
                                                    if (!sent) break
                                                }
                                            } finally { sendingId = null }
                                        }
                                    },
                                    enabled = canWrite && !editorDirty && pickedNow.isNotEmpty(),
                                    modifier = Modifier.height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = OrderGreen),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Outlined.Send, null, Modifier.size(18.dp).rotate(-45f), tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text(when { allPicked -> "Send all to kitchen"; pickedNow.isNotEmpty() -> "Send selected to kitchen"; else -> "Send to kitchen" },
                                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                }
                                Spacer(Modifier.width(10.dp))
                                Surface(onClick = onDismiss, enabled = !state.isSaving && sendingId == null, modifier = Modifier.size(36.dp), shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder), shadowElevation = 2.dp) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, null, Modifier.size(20.dp), tint = OrderInk) }
                                }
                            }
                            HorizontalDivider(color = OrderBorder)
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Surface(Modifier.weight(1.65f).fillMaxHeight(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
                                    Column(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            // Over the dishes' ticks: all, or none.
                                            if (awaiting.isNotEmpty()) {
                                                TriStateCheckbox(
                                                    state = when { allPicked -> ToggleableState.On; pickedNow.isNotEmpty() -> ToggleableState.Indeterminate; else -> ToggleableState.Off },
                                                    onClick = { picked = if (allPicked) emptySet() else awaiting.toSet() },
                                                    enabled = canWrite,
                                                    modifier = Modifier.padding(start = 13.dp, end = 12.dp).size(24.dp),
                                                    colors = CheckboxDefaults.colors(checkedColor = OrderGreen)
                                                )
                                            }
                                            Text("Order items", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = OrderInk, modifier = Modifier.weight(1f))
                                            OutlinedButton(
                                                onClick = { onShowAddItem(true) },
                                                enabled = canWrite,
                                                modifier = Modifier.height(38.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, OrderGreen),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = OrderGreen)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Add item", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OrderGreen)
                                            }
                                        }
                                        val listState = rememberLazyListState()
                                        if (activeItems.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Box(Modifier.size(64.dp).clip(CircleShape).background(OrderGreen.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Outlined.Restaurant, null, Modifier.size(30.dp), tint = OrderGreen)
                                                }
                                                Text("No dishes yet", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OrderInk)
                                                Text("Pick them from the menu.", fontFamily = Inter(), fontSize = 13.sp, color = OrderMuted)
                                                Button(onClick = { onShowAddItem(true) }, enabled = canWrite, shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = OrderGreen)) {
                                                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = Color.White)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Open the menu", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                                }
                                            }
                                        } else Box(Modifier.weight(1f)) {
                                            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                                                items(activeItems, key = { it.id }) { line ->
                                                    OrderEditLineRow(
                                                        line = line,
                                                        currency = order.currency,
                                                        selected = line.id == selected?.id,
                                                        onClick = { if (!state.isSaving && sendingId == null) selectedId = line.id },
                                                        sending = sendingId == line.id,
                                                        pickable = line.id in awaiting,
                                                        picked = line.id in pickedNow,
                                                        canPick = canWrite,
                                                        onPick = { picked = if (line.id in picked) picked - line.id else picked + line.id },
                                                        sent = line.id in sentIds || line.status != OrderLineItemStatus.PENDING,
                                                        canSend = canWrite && !editorDirty,
                                                        onSend = {
                                                            if (canWrite && !editorDirty && line.awaitingKitchen && line.id !in sentIds) {
                                                                sendingId = line.id
                                                                sendError = null
                                                                scope.launch {
                                                                    try {
                                                                        model.operations.fireItem(order.id, line.id).fold(
                                                                            { sentIds = sentIds + line.id },
                                                                            { sendError = it.message ?: "Could not send this item to the kitchen" }
                                                                        )
                                                                    } finally { sendingId = null }
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                            // In the panel's side room, so the rows line up with Add item above them.
                                            PlatformVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd).offset(x = 10.dp).fillMaxHeight().width(3.dp))
                                        }
                                        sendError?.let { OrderError(it) { sendError = null; model.clearError() } }
                                        if (editorDirty) Text("Save your changes before sending items to the kitchen", fontFamily = Inter(), fontSize = 11.sp, color = OrderMuted)
                                        OrderItemsLegend()
                                    }
                                }
                                Surface(Modifier.weight(.82f).fillMaxHeight(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
                                    if (activeItems.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        OrderText("Dishes you add can be changed here", 15, true, color = OrderMuted)
                                    } else key(selected?.id) {
                                        OrderSelectedLineEditor(
                                            line = selected, order = order, model = model,
                                            onClose = { selectedId = null },
                                            onDirtyChange = { editorDirty = it },
                                            onVoid = { selected?.let { onVoidItem(it.id) } },
                                            modifier = Modifier.fillMaxSize().padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                } else {
                    OrderAddItemMenuContent(
                        order = order, model = model, backToEditor = true, active = showAddItem,
                        onDismiss = { onShowAddItem(false) }
                    )
                }
            }
        }
    }
}

/** The order window while Start order makes the order: its header and the menu's dishes as pulsing shapes. */
@Composable private fun OrderComposerShapes() {
    val alpha = com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha("order-start")
    Column(Modifier.fillMaxSize().padding(16.dp).graphicsLayer { this.alpha = alpha }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Starting the order…", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = OrderInk)
            com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.width(110.dp).height(26.dp), shape = RoundedCornerShape(8.dp))
        }
        HorizontalDivider(color = OrderBorder)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.width(96.dp).height(34.dp), shape = RoundedCornerShape(10.dp)) }
        }
        repeat(3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.weight(1f).height(150.dp), com.saporini.mobile_desktop.core.components.SkeletonLight, RoundedCornerShape(14.dp)) }
            }
        }
    }
}



@Composable
private fun OrderInfoEditDialog(order: Order, model: OrdersScreenModel, onDismiss: () -> Unit) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val initialGuests = remember(order.id) { order.guestCount ?: 1 }
    val initialNote = remember(order.id) { order.notes.orEmpty() }
    var guests by remember(order.id) { mutableStateOf(initialGuests.toString()) }
    var note by remember(order.id) { mutableStateOf(initialNote) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy = submitting || state.isSaving
    val enabled = order.editable && state.can("ORDER_UPDATE") && !busy && !state.needsReconciliation
    val guestCount = guests.toIntOrNull()
    val dirty = guestCount != initialGuests || note != initialNote
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = false
    )) {
        Surface(Modifier.padding(20.dp).widthIn(max = 440.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp), color = Color.White,
            border = BorderStroke(1.dp, OrderBorder), shadowElevation = 12.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Edit order information", fontFamily = Inter(), fontWeight = FontWeight.Bold,
                        fontSize = 19.sp, color = OrderInk, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Surface(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(18.dp), color = Color.White,
                        border = BorderStroke(1.dp, OrderBorder), shadowElevation = 2.dp) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, "Close", Modifier.size(20.dp), tint = OrderInk)
                        }
                    }
                }
                HorizontalDivider(color = OrderBorder)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrderEditorLabel("Guests")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            OrderQtyButton(Icons.Outlined.Remove, enabled = enabled && (guestCount ?: 0) > 1) { guests = (guestCount!! - 1).toString(); error = null }
                            Surface(shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder), modifier = Modifier.widthIn(min = 46.dp).height(34.dp)) {
                                Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                    BasicTextField(value = guests, onValueChange = { value ->
                                        if (value.length <= 10 && value.all { it.isDigit() }) { guests = value; error = null }
                                    }, enabled = enabled, singleLine = true, modifier = Modifier.width(46.dp),
                                        textStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OrderInk, textAlign = TextAlign.Center))
                                }
                            }
                            OrderQtyButton(Icons.Outlined.Add, enabled = enabled && (guestCount ?: 0) < Int.MAX_VALUE) { guests = ((guestCount ?: 0) + 1).coerceAtLeast(1).toString(); error = null }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrderEditorLabel("Order note (optional)")
                        Surface(shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
                            BasicTextField(value = note, onValueChange = { note = it; error = null }, enabled = enabled,
                                modifier = Modifier.fillMaxWidth().height(110.dp).padding(12.dp),
                                textStyle = TextStyle(fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = OrderInk),
                                decorationBox = { field ->
                                    if (note.isEmpty()) Text("Add a note for this order...", fontFamily = Inter(), fontSize = 13.sp, color = OrderMuted)
                                    field()
                                })
                        }
                    }
                    error?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Color(0xFFB13A2F)) }
                    if (state.needsReconciliation) Text("Check the last change in the order before trying again.", fontFamily = Inter(), fontSize = 12.sp, color = Color(0xFFB13A2F))
                }
                HorizontalDivider(color = OrderBorder)
                Button(onClick = {
                    if (enabled && dirty && !submitting) {
                        if (guestCount == null || guestCount < 1) error = "Enter a guest count greater than zero"
                        else if ((order.guestCount ?: 1) != initialGuests || order.notes.orEmpty() != initialNote) {
                            error = "Order information changed. Close and reopen it to review the latest details."
                        } else {
                            submitting = true; error = null
                            scope.launch {
                                try {
                                    model.operations.updateOrder(order.id, UpdateOrderInput(guestCount = guestCount, notes = note)).fold(
                                        { onDismiss() }, { error = it.message ?: "Could not save order information" })
                                } finally { submitting = false }
                            }
                        }
                    }
                }, enabled = enabled && dirty, modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = OrderGreen,
                        disabledContainerColor = if (busy) OrderGreen else OrderGreen.copy(alpha = .5f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Save changes", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }
}


@Composable
private fun OrderKitchenSendDialog(order: Order, model: OrdersScreenModel, onDismiss: () -> Unit) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy = submitting || state.isSaving
    val canSend = order.editable && state.can("ORDER_UPDATE") && !busy && !state.needsReconciliation &&
        order.lineItems.orEmpty().any { it.awaitingKitchen }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = false
    )) {
        Surface(Modifier.padding(20.dp).widthIn(max = 440.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp), color = Color.White,
            border = BorderStroke(1.dp, OrderBorder), shadowElevation = 12.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Send all to kitchen", fontFamily = Inter(), fontSize = 19.sp,
                        fontWeight = FontWeight.Bold, color = OrderInk, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Surface(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(18.dp), color = Color.White,
                        border = BorderStroke(1.dp, OrderBorder), shadowElevation = 2.dp) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, "Close", Modifier.size(20.dp), tint = OrderInk)
                        }
                    }
                }
                HorizontalDivider(color = OrderBorder)
                Text("Send all pending items in ${order.displayOrderNumber()} to the kitchen?",
                    fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = OrderMuted)
                error?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Color(0xFFB13A2F)) }
                if (state.needsReconciliation) Text("Check the last change in the order before trying again.",
                    fontFamily = Inter(), fontSize = 12.sp, color = Color(0xFFB13A2F))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, OrderBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                        Icon(Icons.Outlined.Close, null, Modifier.size(17.dp), tint = OrderInk)
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            letterSpacing = 0.sp, color = OrderInk)
                    }
                    Button(onClick = {
                        if (canSend && !submitting) {
                            submitting = true; error = null
                            scope.launch {
                                try {
                                    model.operations.sendToKitchen(order.id).fold(
                                        { onDismiss() }, { error = it.message ?: "Could not send items to the kitchen" })
                                } finally { submitting = false }
                            }
                        }
                    }, enabled = canSend, modifier = Modifier.weight(1.8f).height(42.dp),
                        shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(
                            containerColor = OrderGreen, disabledContainerColor = if (busy) OrderGreen else OrderGreen.copy(alpha = .5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Outlined.Send, null, Modifier.size(18.dp).rotate(-45f), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Send all to kitchen", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp, color = Color.White, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun OrderAddItemMenuModal(order: Order, model: OrdersScreenModel, onDismiss: () -> Unit) {
    val state by model.state.collectAsState()
    Dialog(onDismissRequest = { if (!state.isSaving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.90f).fillMaxHeight(.86f),
            shape = RoundedCornerShape(16.dp), color = Color.White,
            border = BorderStroke(1.dp, OrderBorder), shadowElevation = 14.dp
        ) {
            OrderAddItemMenuContent(order, model, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun OrderAddItemMenuContent(
    order: Order, model: OrdersScreenModel, backToEditor: Boolean = false, active: Boolean = true, onDismiss: () -> Unit
) {
    val state by model.state.collectAsState()
    var menus by remember { mutableStateOf(emptyList<OrderCatalogMenu>()) }
    var activeMenu by remember { mutableStateOf<OrderCatalogMenu?>(null) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var selectedSectionId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<OrderCatalogItem?>(null) }
    var previewItem by remember { mutableStateOf<MenuItem?>(null) }
    var lastSelectedItem by remember { mutableStateOf<OrderCatalogItem?>(null) }
    LaunchedEffect(selectedItem) { if (selectedItem != null) lastSelectedItem = selectedItem }
    var choices by remember { mutableStateOf<OrderItemChoices?>(null) }
    var variantId by remember { mutableStateOf<String?>(null) }
    var optionIds by remember { mutableStateOf(emptySet<String>()) }
    var choicesLoading by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var addedMessage by remember { mutableStateOf<String?>(null) }
    var addedNoticeId by remember { mutableIntStateOf(0) }
    var addError by remember { mutableStateOf<String?>(null) }
    // Option groups framed in red for 3 seconds after Add to Order finds them unchosen (user, 2026-11-01).
    var missingGroups by remember { mutableStateOf(emptySet<String>()) }
    var missingTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(missingTick) {
        if (missingTick == 0) return@LaunchedEffect
        delay(3_000L)
        missingGroups = emptySet()
    }
    LaunchedEffect(addedNoticeId) {
        if (addedMessage != null) {
            delay(2_000L)
            addedMessage = null
        }
    }
    val scope = rememberCoroutineScope()
    var quantity by remember { mutableStateOf(1) }
    var note by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadMenus() {
        loading = true
        error = null
        try {
            val catalog = requireNotNull(model.catalog) { "Menu catalog is not available." }
            val collected = mutableListOf<OrderCatalogMenu>()
            var page = 0
            do {
                val result = catalog.getMenus(page, 100).getOrThrow()
                collected.addAll(result.items.filter { it.active })
                page++
            } while (result.hasNext)
            menus = collected
            menuId = collected.firstOrNull()?.id
        } catch(e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch(e: Exception) {
            error = e.message ?: "Could not load menu items."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(state.scope) { loadMenus() }
    LaunchedEffect(menuId) {
        activeMenu = null
        selectedSectionId = null
        selectedItem = null
        val id = menuId ?: return@LaunchedEffect
        loading = true
        error = null
        try {
            activeMenu = model.catalog?.getMenu(id)?.getOrThrow()
        } catch(e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch(e: Exception) {
            error = e.message ?: "Could not load this menu."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(menuId, selectedItem?.id) {
        addError = null
        val item = selectedItem ?: return@LaunchedEffect
        choices = null
        optionIds = emptySet()
        variantId = null
        quantity = 1
        note = ""
        val menu = menuId ?: return@LaunchedEffect
        choicesLoading = true
        error = null
        try {
            choices = requireNotNull(model.catalog).getItemChoices(menu, item.id).getOrThrow()
            val variants = choices!!.item.variants.orEmpty().filter { it.active }
            variantId = (variants.firstOrNull { it.isDefault } ?: variants.firstOrNull())?.id
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Could not load item options." }
        finally { choicesLoading = false }
    }

    val sections = activeMenu?.sections.orEmpty().filter { it.active }.sortedBy { it.displayOrder }
    val trimmedQuery = query.trim()
    val catalogItems = sections
        .filter { selectedSectionId == null || trimmedQuery.isNotBlank() || it.id == selectedSectionId }
        .flatMap { section -> section.items.orEmpty().filter { it.available }.map { section.name to it } }
        .filter { (_, item) -> trimmedQuery.isBlank() || item.name.contains(trimmedQuery, ignoreCase = true) || item.description.orEmpty().contains(trimmedQuery, ignoreCase = true) }
        .sortedBy { it.second.displayOrder }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (backToEditor) {
                    IconButton(onClick = onDismiss, enabled = !submitting, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Reply, "Back to order items", Modifier.size(20.dp), tint = OrderInk)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text("Add item", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = OrderInk, modifier = Modifier.weight(1f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    OrderAddMenuSelect(
                        label = menus.firstOrNull { it.id == menuId }?.label ?: "Change menu",
                        menus = menus,
                        onMenuSelected = { if (!submitting) menuId = it }
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Surface(onClick = onDismiss, enabled = !submitting, modifier = Modifier.size(38.dp), shape = RoundedCornerShape(19.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder), shadowElevation = 2.dp) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, null, Modifier.size(21.dp), tint = OrderInk) }
                    }
                }
            }
            HorizontalDivider(color = OrderBorder)
            Row(Modifier.weight(1f)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val categoryItems = remember(sections) { listOf(MenuCategory("All", MenuCategoryIcon.RESTAURANT_MENU)) + sections.map { MenuCategory(it.name, MenuCategoryIcon.RESTAURANT_MENU, it.id) } }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            OrderAddCategoryRow(
                                items = categoryItems,
                                selected = sections.firstOrNull { it.id == selectedSectionId }?.name ?: "All",
                                onSelected = { selectedName -> selectedSectionId = sections.firstOrNull { it.name == selectedName }?.id }
                            )
                        }
                        MenuSearchBox(
                            query = query,
                            onQueryChange = { query = it },
                            items = sections.flatMap { section -> section.items.orEmpty().map { section.name to it } },
                            itemName = { it.second.name },
                            itemCategory = { it.first },
                            itemPrice = { it.second.basePrice.money(order.currency) },
                            itemAvailable = { it.second.available },
                            onSuggestionClick = { pair -> if (!submitting) { selectedItem = pair.second; selectedSectionId = sections.firstOrNull { it.name == pair.first }?.id; query = pair.second.name } },
                            modifier = Modifier.width(260.dp)
                        )
                    }
                    (error ?: state.error?.message?.takeIf { selectedItem == null })?.let { OrderError(it) { error = null; model.clearError() } }
                    state.refreshWarning?.let { OrderError(it, model::clearError) }
                    if (choicesLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = OrderGreen)
                    if(loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = OrderGreen)
                    if(!loading && catalogItems.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No items found", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = OrderMuted)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(205.dp),
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(catalogItems, key = { it.second.id }) { (sectionName, item) ->
                                OrderAddMenuItemCard(
                                    item = item,
                                    category = sectionName,
                                    currency = order.currency,
                                    selected = selectedItem?.id == item.id,
                                    onSelect = { if (!submitting) { selectedItem = item } },
                                    onExpand = { previewItem = item.toMenuPreview(sectionName, order.currency) }
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = selectedItem != null,
                    enter = expandHorizontally(tween(250), expandFrom = Alignment.End) + fadeIn(tween(180)),
                    exit = shrinkHorizontally(tween(250), shrinkTowards = Alignment.End) + fadeOut(tween(150))
                ) {
                    Box(Modifier.padding(start = 18.dp)) {
                        Surface(Modifier.width(360.dp).fillMaxHeight(), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
                            OrderAddItemDetails(
                                item = choices?.item?.takeIf { it.id == selectedItem?.id } ?: selectedItem ?: lastSelectedItem,
                                onClose = { if (!submitting) { selectedItem = null; error = null } },
                                canClose = !submitting,
                                isAdding = submitting,
                                errorMessage = (addError ?: state.error?.message).takeIf { active },
                                onCloseError = { addError = null; model.clearError() },
                                missingGroups = missingGroups,
                                choices = choices,
                                variantId = variantId,
                                optionIds = optionIds,
                                onVariantChange = { if (!submitting) variantId = it },
                                onOptionChange = { id -> if (!submitting) { optionIds = if (id in optionIds) optionIds - id else optionIds + id; addError = null } },
                                enabled = !submitting && !state.isSaving && !state.needsReconciliation && order.editable && state.can("ORDER_UPDATE"),
                                currency = order.currency,
                                quantity = quantity,
                                note = note,
                                onQuantityChange = { if (!submitting) quantity = it.coerceAtLeast(1) },
                                onNoteChange = { if (!submitting) note = it.take(200) },
                                onAdd = {
                                    val current = choices
                                    if (current != null && current.item.id == selectedItem?.id && !submitting && !state.isSaving && !state.needsReconciliation) {
                                        val unchosen = current.groups.filter { group -> group.availableChoices.count { it.id in optionIds } < group.minimum }
                                        if (unchosen.isNotEmpty()) { missingGroups = unchosen.mapTo(HashSet()) { it.group.id }; missingTick++ }
                                        val input = runCatching {
                                            current.toLineItemInput(quantity, variantId,
                                                optionIds.map { CreateOrderItemOptionInput(it, 1) }, note)
                                        }
                                        input.fold(onSuccess = { request ->
                                            submitting = true
                                            addError = null
                                            scope.launch {
                                                try {
                                                    model.operations.addItem(order.id, request).fold(
                                                        onSuccess = {
                                                            addedMessage = "${request.quantity}× ${current.item.name} added to order"
                                                            addedNoticeId++
                                                            selectedItem = null
                                                            choices = null
                                                        },
                                                        onFailure = { addError = it.message ?: "Could not add item." }
                                                    )
                                                } finally { submitting = false }
                                            }
                                        }, onFailure = { addError = it.message ?: "Check the item options." })
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        addedMessage?.let { message ->
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp).widthIn(max = 460.dp),
                shape = RoundedCornerShape(10.dp), color = Color(0xFF232422), shadowElevation = 10.dp
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    key(addedNoticeId) {
                        AnimatedStatusIcon(Icons.Outlined.Check, Color(0xFF75BE64), size = 26.dp, strokeWidth = 1.5.dp)
                    }
                    Text(message, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                }
            }
        }
    }
    previewItem?.let { item ->
        ItemDetailDialog(item = item, onDismiss = { previewItem = null })
    }
}

private fun OrderCatalogItem.toMenuPreview(category: String, currency: String) = MenuItem(
    id = id, name = name, description = description.orEmpty(), price = basePrice.money(currency),
    category = category, available = available, sku = sku, imageUrl = imageUrl,
    ingredients = ingredients.orEmpty().map { DraftIngredient(it, "", "") },
    variants = variants.orEmpty().map {
        DraftVariant(it.name, (if (it.priceDelta.isNegative) "" else "+") + it.priceDelta.money(currency), it.id)
    },
    optionGroups = optionGroups.orEmpty().map {
        DraftOptionGroup(it.name, it.requiredOverride ?: it.required, emptyList(), it.linkId, it.optionGroupId)
    }
)

@Composable
private fun OrderAddMenuSelect(label: String, menus: List<OrderCatalogMenu>, onMenuSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.width(260.dp).height(42.dp),
            shape = RoundedCornerShape(9.dp),
            color = OrderBackground,
            border = BorderStroke(1.dp, OrderBorder)
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = OrderMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp).rotate(90f), tint = OrderInk)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White), shape = RoundedCornerShape(10.dp), containerColor = Color.White, tonalElevation = 0.dp, shadowElevation = 6.dp) {
            menus.forEach { menu ->
                DropdownMenuItem(
                    text = { Text(menu.label, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = OrderInk) },
                    onClick = { expanded = false; onMenuSelected(menu.id) }
                )
            }
        }
    }
}

@Composable
private fun OrderAddCategoryRow(items: List<MenuCategory>, selected: String, onSelected: (String) -> Unit) {
    var overflowExpanded by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 7.dp
        val moreWidth = 36.dp
        fun chipWidth(item: MenuCategory) = if (item.name == "All") 58.dp else 96.dp
        val fullWidth = items.fold(0.dp) { total, item -> total + chipWidth(item) } + gap * (items.size - 1).coerceAtLeast(0)
        val fixedItems = if (fullWidth <= maxWidth) items else {
            var usedWidth = 0.dp
            items.takeWhile { item ->
                val required = usedWidth + chipWidth(item) + gap + moreWidth
                if (required <= maxWidth) {
                    usedWidth += chipWidth(item) + gap
                    true
                } else false
            }
        }
        val selectedOverflow = items.firstOrNull { it.name == selected && it !in fixedItems }
        // Keep a selected section visible when replacing a same-width section fits.
        val visibleItems = if (selectedOverflow != null && fixedItems.size >= 2)
            fixedItems.dropLast(1) + selectedOverflow else fixedItems
        val overflowItems = items.filterNot { it in visibleItems }
        LaunchedEffect(overflowItems) { if (overflowItems.isEmpty()) overflowExpanded = false }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            visibleItems.forEach { item ->
                OrderAddCategoryChip(item = item, selected = item.name == selected) { onSelected(item.name) }
            }
            if(overflowItems.isNotEmpty()) {
                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, OrderBorder, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Outlined.MoreHoriz, "More sections", Modifier.size(20.dp), tint = OrderInk)
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                        offset = DpOffset(x = 0.dp, y = 6.dp),
                        modifier = Modifier.background(Color.White),
                        shape = RoundedCornerShape(10.dp),
                        containerColor = Color.White,
                        tonalElevation = 0.dp,
                        shadowElevation = 6.dp
                    ) {
                        overflowItems.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OrderInk) },
                                leadingIcon = { Icon(item.icon.icon, null, Modifier.size(16.dp), tint = OrderInk) },
                                onClick = { overflowExpanded = false; onSelected(item.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderAddCategoryChip(item: MenuCategory, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(36.dp)
            .width(if(item.name == "All") 58.dp else 96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if(selected) OrderGreen else Color.White)
            .border(1.dp, OrderBorder, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 7.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if(selected) Color.White else OrderInk)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(item.icon.icon, null, Modifier.size(14.dp), tint = if(selected) Color.White else OrderInk)
            Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
    }
}

@Composable
private fun OrderAddMenuItemCard(item: OrderCatalogItem, category: String, currency: String, selected: Boolean, onSelect: () -> Unit, onExpand: () -> Unit) {
    MenuItemCard(
        name = item.name,
        description = item.description.orEmpty(),
        price = item.basePrice.money(currency),
        category = category,
        available = item.available,
        selected = selected,
        isPhone = false,
        canEdit = false,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        ingredients = item.ingredients.orEmpty().ifEmpty { listOfNotNull(item.description?.takeIf { it.isNotBlank() }) },
        imageUrl = item.imageUrl,
        onExpand = onExpand
    )
}

@Composable
private fun OrderAddItemDetails(item: OrderCatalogItem?, onClose: () -> Unit, canClose: Boolean, isAdding: Boolean, errorMessage: String?, onCloseError: () -> Unit,
    // The option groups still missing a choice, framed in red for a moment after Add to Order.
    missingGroups: Set<String>, choices: OrderItemChoices?, variantId: String?,
    optionIds: Set<String>, onVariantChange: (String) -> Unit, onOptionChange: (String) -> Unit,
    enabled: Boolean, currency: String, quantity: Int, note: String,
    onQuantityChange: (Int) -> Unit, onNoteChange: (String) -> Unit, onAdd: () -> Unit) {
    if(item == null) {
        Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
            Text("Select an item to add it", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = OrderMuted, textAlign = TextAlign.Center)
        }
        return
    }
    val variants = item.variants.orEmpty().filter { it.active }.sortedBy { it.displayOrder }
    val selectedVariant = variants.firstOrNull { it.id == variantId }
    val optionsTotal = choices?.groups.orEmpty().flatMap { it.availableChoices }
        .filter { it.id in optionIds }.sumOf { it.priceDelta.value.toDoubleOrNull() ?: 0.0 }
    // Preview only; the server calculates and persists authoritative prices.
    val lineTotal = ((item.basePrice.value.toDoubleOrNull() ?: 0.0) +
        (selectedVariant?.priceDelta?.value?.toDoubleOrNull() ?: 0.0) + optionsTotal) * quantity
    val detailsScrollState = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(detailsScrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    com.saporini.mobile_desktop.pos.menu.ui.item.MenuPhoto(item.imageUrl, item.name, Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = OrderInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.description.orEmpty(), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, color = OrderMuted, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(onClick = onClose, enabled = canClose, modifier = Modifier.size(30.dp),
                        shape = RoundedCornerShape(15.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, "Close item panel", Modifier.size(18.dp), tint = OrderInk)
                        }
                    }
                }
                HorizontalDivider(color = OrderBorder)
                Text("Variants", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OrderInk)
                if(variants.isEmpty()) {
                    OrderAddOptionRow("Regular", item.basePrice.money(currency), selected = true, radio = true, enabled = false) {}
                } else {
                    variants.forEach { variant ->
                        val price = (item.basePrice.value.toDoubleOrNull() ?: 0.0) + (variant.priceDelta.value.toDoubleOrNull() ?: 0.0)
                        OrderAddOptionRow(variant.name, price.toOrderMoney(currency), selected = variant.id == variantId,
                            radio = true, enabled = enabled && choices != null) { onVariantChange(variant.id) }
                    }
                }
                if (!choices?.groups.isNullOrEmpty()) {
                    HorizontalDivider(color = OrderBorder)
                    Text("Options", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OrderInk)
                    choices?.groups.orEmpty().forEach { group ->
                        val selectedCount = group.availableChoices.count { it.id in optionIds }
                        val flagged = group.group.id in missingGroups && selectedCount < group.minimum
                        val frame by androidx.compose.animation.animateColorAsState(if (flagged) OrderDanger else Color.Transparent, tween(180), label = "missing-option")
                        // Brought into view when it's flagged: the list may be scrolled past it.
                        val into = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                        LaunchedEffect(flagged) { if (flagged) into.bringIntoView() }
                        Column(
                            Modifier.fillMaxWidth().bringIntoViewRequester(into).drawBehind {
                                // Around the group, without moving its rows.
                                val outX = 6.dp.toPx(); val outY = 5.dp.toPx()
                                drawRoundRect(frame, topLeft = Offset(-outX, -outY), size = Size(size.width + outX * 2, size.height + outY * 2),
                                    cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                            },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(group.group.name + if (group.minimum > 0) " · Choose at least ${group.minimum}" else " · Optional",
                                fontFamily = Inter(), fontSize = 11.sp, fontWeight = if (flagged) FontWeight.SemiBold else FontWeight.Normal, color = if (flagged) OrderDanger else OrderMuted)
                            group.availableChoices.forEach { option ->
                                val canSelect = option.id in optionIds || group.maximum == null || selectedCount < group.maximum!!
                                OrderAddOptionRow(option.name, option.priceDelta.money(currency), selected = option.id in optionIds,
                                    radio = false, enabled = enabled && canSelect) { onOptionChange(option.id) }
                            }
                        }
                    }
                }
                HorizontalDivider(color = OrderBorder)
                OrderEditorLabel("Item note")
                Surface(shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
                    Box(Modifier.fillMaxWidth().height(96.dp).padding(12.dp)) {
                        BasicTextField(
                            value = note, onValueChange = onNoteChange, enabled = enabled,
                            modifier = Modifier.fillMaxSize().padding(bottom = 18.dp),
                            textStyle = TextStyle(fontFamily = Inter(), fontSize = 13.sp, color = OrderInk),
                            decorationBox = { field ->
                                if (note.isEmpty()) Text("Add a note for this item...", fontFamily = Inter(), fontSize = 13.sp, color = OrderMuted)
                                field()
                            }
                        )
                        Text("${note.length}/200", fontFamily = Inter(), fontSize = 12.sp, color = OrderMuted, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
            PlatformVerticalScrollbar(detailsScrollState, Modifier.align(Alignment.CenterEnd).offset(x = 11.dp).fillMaxHeight().width(3.dp))
        }
        HorizontalDivider(color = OrderBorder)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OrderEditorLabel("Quantity")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OrderQtyButton(Icons.Outlined.Remove) { onQuantityChange(quantity - 1) }
                    Surface(shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder), modifier = Modifier.size(width = 38.dp, height = 34.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("$quantity", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OrderInk) }
                    }
                    OrderQtyButton(Icons.Outlined.Add) { onQuantityChange(quantity + 1) }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Line Total", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = OrderMuted)
                Text(lineTotal.toOrderMoney(currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = OrderInk)
            }
        }
        // Sits above the button, covering nothing, and closes with its X (user, 2026-11-01).
        errorMessage?.let { problem ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(OrderDanger.copy(alpha = .08f)).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = OrderDanger)
                Text(problem, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = OrderDanger)
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).clickable(onClick = onCloseError), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "Close", Modifier.size(16.dp), tint = OrderDanger)
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Button(onClick = onAdd, enabled = enabled && choices != null, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = OrderGreen,
                disabledContainerColor = if (isAdding) OrderGreen else OrderGreen.copy(alpha = .45f)), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)) {
                if (isAdding) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("Add to Order", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                }
            }
        }
    }
}

private val OrderDanger = Color(0xFFB13A2F)

@Composable
private fun OrderAddOptionRow(label: String, price: String, selected: Boolean, radio: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(28.dp).clickable(enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            if(radio) RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.size(22.dp)) else Checkbox(checked = selected, onCheckedChange = null, enabled = enabled, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = OrderInk, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if(price.isNotBlank()) Text(price, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = OrderInk)
    }
}

private fun Double.toOrderMoney(currency: String): String {
    val symbol = when(currency) { "EUR" -> "€"; "USD" -> "$"; "GBP" -> "£"; else -> "$currency " }
    val cents = (this * 100).roundToInt()
    val units = cents / 100
    val rem = kotlin.math.abs(cents % 100).toString().padStart(2, '0')
    return "$symbol$units.$rem"
}

@Composable private fun OrderEditSmallIconText(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        Text(text, fontFamily = Inter(), fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun OrderEditLineRow(
    line: OrderLineItem,
    currency: String,
    selected: Boolean,
    onClick: () -> Unit,
    sending: Boolean,
    pickable: Boolean,
    picked: Boolean,
    canPick: Boolean,
    onPick: () -> Unit,
    sent: Boolean,
    canSend: Boolean,
    onSend: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(if(selected) 1.4.dp else 1.dp, if(selected) OrderGreen else OrderBorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Only what hasn't gone to the kitchen can be ticked; the others keep the room, so the rows line up.
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (pickable) Checkbox(checked = picked, onCheckedChange = { onPick() }, enabled = canPick, modifier = Modifier.size(24.dp),
                    colors = CheckboxDefaults.colors(checkedColor = OrderGreen))
            }
            Text("${line.quantity}×", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = OrderInk, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
            com.saporini.mobile_desktop.pos.menu.ui.item.MenuPhoto(line.imageUrl, null, Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(line.itemNameSnapshot, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = OrderInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line.orderItemSubtitle(), fontFamily = Inter(), fontSize = 13.sp, color = OrderMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(.95f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OrderItemProgressBadge(line.status)
                OrderEditSmallIconText(Icons.Outlined.ChatBubbleOutline, line.notes?.takeIf { it.isNotBlank() } ?: "No notes", OrderMuted)
            }
            Text(line.lineTotal.money(currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = OrderInk, modifier = Modifier.width(76.dp).padding(end = 18.dp), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Clip)
            if (!line.sendToKitchen) {
                // Counter items are served directly; the waiter marks them served from the item editor.
                Row(Modifier.width(150.dp).height(38.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.RoomService, null, Modifier.size(17.dp), tint = OrderMuted)
                    Spacer(Modifier.width(6.dp))
                    Text("Serve directly", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = OrderMuted, maxLines = 1)
                }
            } else Surface(onClick = onSend, enabled = canSend && !sent && line.status == OrderLineItemStatus.PENDING, shape = RoundedCornerShape(10.dp), color = OrderBackground, modifier = Modifier.width(150.dp).height(38.dp)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), color = OrderGreen, strokeWidth = 2.dp)
                    else {
                        Icon(if (sent) Icons.Outlined.Check else Icons.Outlined.Send, null,
                            Modifier.size(17.dp).rotate(if (sent) 0f else -45f), tint = if (sent) OrderGreen else OrderInk)
                        Spacer(Modifier.width(6.dp))
                        Text(if (sent) "Sent to kitchen" else "Send to kitchen", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (sent) OrderGreen else OrderInk, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable private fun OrderSelectedLineEditor(
    line: OrderLineItem?, order: Order, model: OrdersScreenModel, onClose: () -> Unit,
    onDirtyChange: (Boolean) -> Unit, onVoid: () -> Unit, modifier: Modifier = Modifier
) {
    if (line == null) {
        Box(modifier, contentAlignment = Alignment.Center) { OrderText("Select an item to edit", 15, true, color = OrderMuted) }
        return
    }
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val currency = order.currency
    var baseline by remember { mutableStateOf(line) }
    var quantity by remember { mutableIntStateOf(line.quantity) }
    var variantId by remember { mutableStateOf(line.variantId) }
    var options by remember { mutableStateOf(line.options.orEmpty().map { CreateOrderItemOptionInput(it.optionItemId, it.quantity, it.notes) }) }
    var note by remember { mutableStateOf(line.notes.orEmpty()) }
    var choices by remember { mutableStateOf<OrderItemChoices?>(null) }
    var loading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<String?>(null) }
    val originalOptions = baseline.options.orEmpty().map { CreateOrderItemOptionInput(it.optionItemId, it.quantity, it.notes) }
    val structuralChange = quantity != baseline.quantity || variantId != baseline.variantId || options.toSet() != originalOptions.toSet()
    val dirty = structuralChange || note != baseline.notes.orEmpty()
    val enabled = order.editable && line.active && state.can("ORDER_UPDATE") && !state.isSaving && !state.needsReconciliation && busy == null
    val canConfigure = enabled && line.status == OrderLineItemStatus.PENDING && baseline.status == OrderLineItemStatus.PENDING
    val variants = choices?.item?.variants.orEmpty().filter { it.active }.sortedBy { it.displayOrder }
    val variantChoices = if (baseline.variantId == null) listOf(null to "Regular") + variants.map { it.id to it.name }
        else variants.map { it.id to it.name }
    val availableOptions = choices?.groups.orEmpty().flatMap { it.availableChoices }
    LaunchedEffect(dirty) { onDirtyChange(dirty) }
    LaunchedEffect(saved) { if (saved) { delay(2000); saved = false } }
    // Refresh clean fields from SSE, but keep the user's unsaved draft intact.
    LaunchedEffect(line) {
        if (!dirty && busy == null) {
            baseline = line; quantity = line.quantity; variantId = line.variantId; note = line.notes.orEmpty()
            options = line.options.orEmpty().map { CreateOrderItemOptionInput(it.optionItemId, it.quantity, it.notes) }
        }
    }
    LaunchedEffect(line.menuItemId, state.scope) {
        loading = true
        try {
            val catalog = requireNotNull(model.catalog) { "Menu is unavailable" }
            var page = 0
            var found: OrderItemChoices? = null
            do {
                val result = catalog.getMenus(page++, 100).getOrThrow()
                for (menu in result.items.filter { it.active }) {
                    val full = catalog.getMenu(menu.id).getOrThrow()
                    if (full.sections.orEmpty().filter { it.active }.flatMap { it.items.orEmpty() }.any { it.id == line.menuItemId }) {
                        found = catalog.getItemChoices(menu.id, line.menuItemId).getOrThrow()
                        break
                    }
                }
            } while (found == null && result.hasNext)
            choices = found
            if (found == null) catalogError = "This item is no longer in an available menu. Existing choices are kept."
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { catalogError = e.message ?: "Could not load item choices" }
        finally { loading = false }
    }
    fun accept(updated: OrderLineItem) {
        baseline = updated; quantity = updated.quantity; variantId = updated.variantId; note = updated.notes.orEmpty()
        options = updated.options.orEmpty().map { CreateOrderItemOptionInput(it.optionItemId, it.quantity, it.notes) }
    }
    fun changeStatus(status: OrderLineItemStatus) {
        if (!enabled || dirty) return
        picker = null; busy = "status"; error = null
        scope.launch {
            try {
                val result = when (status) {
                    OrderLineItemStatus.FIRED -> model.operations.fireItem(order.id, line.id)
                    OrderLineItemStatus.READY -> model.operations.readyItem(order.id, line.id)
                    OrderLineItemStatus.FULFILLED -> model.operations.fulfillItem(order.id, line.id)
                    else -> model.operations.updateItemStatus(order.id, line.id, UpdateOrderLineItemStatusInput(status))
                }
                result.fold({ accept(it) }, { error = it.message ?: "Could not change item progress" })
            } finally { busy = null }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val scroll = rememberScrollState()
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    com.saporini.mobile_desktop.pos.menu.ui.item.MenuPhoto(line.imageUrl, null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(line.itemNameSnapshot, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = OrderInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(baseline.lineTotal.money(currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = OrderInk)
                    }
                    Spacer(Modifier.width(10.dp))
                    Surface(onClick = onClose, enabled = !state.isSaving && busy == null, modifier = Modifier.size(34.dp), shape = RoundedCornerShape(17.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, "Close item editor", Modifier.size(19.dp), tint = OrderInk) }
                    }
                }
                HorizontalDivider(color = OrderBorder)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        OrderEditorLabel("Quantity")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OrderQtyButton(Icons.Outlined.Remove, enabled = canConfigure && quantity > 1) { quantity--; error = null; saved = false }
                            Surface(shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder), modifier = Modifier.size(width = 38.dp, height = 34.dp)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("$quantity", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OrderInk) }
                            }
                            OrderQtyButton(Icons.Outlined.Add, enabled = canConfigure && quantity < Int.MAX_VALUE) { quantity++; error = null; saved = false }
                        }
                    }
                    Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        OrderEditorLabel("Variant")
                        Box {
                            OrderEditField(variants.firstOrNull { it.id == variantId }?.name ?: baseline.variantNameSnapshot ?: "Regular", trailing = canConfigure && variantChoices.size > 1) { picker = "variant" }
                            DropdownMenu(expanded = picker == "variant", onDismissRequest = { picker = null }) {
                                variantChoices.forEach { (id, name) ->
                                    DropdownMenuItem(text = { Text(name, fontFamily = Inter(), fontSize = 13.sp) },
                                        leadingIcon = { RadioButton(selected = variantId == id, onClick = null) },
                                        onClick = { variantId = id; picker = null; error = null; saved = false })
                                }
                            }
                        }
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = OrderGreen)
                catalogError?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = OrderMuted) }
                if (line.status != OrderLineItemStatus.PENDING) Text("Sent items keep their quantity and choices. You can still edit the note.", fontFamily = Inter(), fontSize = 11.sp, color = OrderMuted)
                OrderEditorLabel("Options")
                val optionNames = options.map { selected -> availableOptions.firstOrNull { it.id == selected.optionItemId }?.name
                    ?: baseline.options.orEmpty().firstOrNull { it.optionItemId == selected.optionItemId }?.optionNameSnapshot ?: "Option" }
                OrderEditField(optionNames.joinToString().ifEmpty {
                    if (!loading && choices != null && availableOptions.isEmpty()) "No options to select" else "No options selected"
                }, trailing = canConfigure && availableOptions.isNotEmpty()) { picker = "options" }
                OrderEditorLabel("Progress status")
                Box {
                    OrderStatusSelectField(baseline.status, enabled = enabled && !dirty) { picker = "status" }
                    DropdownMenu(expanded = picker == "status", onDismissRequest = { picker = null }) {
                        (if (baseline.sendToKitchen) listOf(OrderLineItemStatus.FIRED, OrderLineItemStatus.PREPARING, OrderLineItemStatus.READY, OrderLineItemStatus.FULFILLED)
                            else listOf(OrderLineItemStatus.READY, OrderLineItemStatus.FULFILLED)).forEach { status ->
                            DropdownMenuItem(text = { OrderItemProgressBadge(status) }, enabled = status != baseline.status, onClick = { changeStatus(status) })
                        }
                    }
                }
                OrderEditorLabel("Item note")
                Surface(shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
                    Box(Modifier.fillMaxWidth().height(96.dp).padding(12.dp)) {
                        BasicTextField(value = note, onValueChange = { if (it.length <= 200) { note = it; error = null; saved = false } }, enabled = enabled,
                            modifier = Modifier.fillMaxSize().padding(bottom = 18.dp), textStyle = TextStyle(fontFamily = Inter(), fontSize = 13.sp, color = OrderInk),
                            decorationBox = { field ->
                                if (note.isEmpty()) Text("Add a note for this item...", fontFamily = Inter(), fontSize = 13.sp, color = OrderMuted)
                                field()
                            })
                        Text("${note.length}/200", fontFamily = Inter(), fontSize = 12.sp, color = OrderMuted, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
            PlatformVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd).offset(x = 10.dp).fillMaxHeight().width(3.dp))
        }
        HorizontalDivider(color = OrderBorder)
        if (state.needsReconciliation) Text("Check the last change in the order before trying again.", fontFamily = Inter(), fontSize = 11.sp, color = Color(0xFFB13A2F))
        if (saved) Text("Changes saved", fontFamily = Inter(), fontSize = 12.sp, color = OrderGreen)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { changeStatus(OrderLineItemStatus.FULFILLED) }, enabled = enabled && !dirty && baseline.status != OrderLineItemStatus.FULFILLED,
                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, OrderBorder), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                if (busy == "status") CircularProgressIndicator(Modifier.size(18.dp), color = OrderGreen, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.CheckCircleOutline, null, Modifier.size(18.dp), tint = OrderInk)
                    Spacer(Modifier.width(7.dp))
                    Text("Mark as served", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.sp, color = OrderInk, maxLines = 1)
                }
            }
            Button(onClick = onVoid, enabled = order.editable && state.can("ORDER_VOID") && !state.isSaving && !state.needsReconciliation && busy == null,
                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB13A2F), contentColor = Color.White), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Delete", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.sp, color = Color.White)
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Button(onClick = {
                if (enabled && dirty) {
                    error = null; saved = false
                    if (line.updatedAt != baseline.updatedAt) error = "This item changed. Close and reopen it to review the latest changes."
                    else {
                        val request = runCatching {
                            if (structuralChange) {
                                require(line.status == OrderLineItemStatus.PENDING) { "This item has already been sent to the kitchen" }
                                if (variantId != baseline.variantId || options.toSet() != originalOptions.toSet()) {
                                    requireNotNull(choices) { "Item choices are not available" }.toLineItemInput(quantity, variantId, options, note)
                                } else CreateOrderLineItemInput(line.menuItemId, variantId, quantity, note.trim().takeIf { it.isNotEmpty() }, options)
                            } else null
                        }
                        request.fold({ input ->
                            busy = "save"
                            scope.launch {
                                try {
                                    val result = if (input != null) model.operations.updateItem(order.id, line.id, input)
                                        else model.operations.updateItemNotes(order.id, line.id, OrderLineItemNotesInput(note.trim().takeIf { it.isNotEmpty() }))
                                    result.fold({ accept(it); saved = true }, { error = it.message ?: "Could not save changes" })
                                } finally { busy = null }
                            }
                        }, { error = it.message ?: "Check the selected item choices" })
                    }
                }
            }, enabled = enabled && dirty, modifier = Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrderGreen, disabledContainerColor = if (busy == "save") OrderGreen else OrderGreen.copy(alpha = .5f)), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)) {
                if (busy == "save") CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Save changes", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                }
            }
            MenuValidationToast(visible = error != null, message = error.orEmpty(), placement = ToastPlacement.Above,
                arrowAlignment = Alignment.End, anchorAlignment = Alignment.TopEnd, offsetY = (-46).dp)
        }
    }
    if (picker == "options") OrderModal("Options", { picker = null }, state.isSaving) {
        choices?.groups.orEmpty().forEach { group ->
            Text(group.group.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(if (group.minimum > 0) "Choose at least ${group.minimum}" else "Optional", fontFamily = Inter(), fontSize = 12.sp, color = OrderMuted)
            group.availableChoices.forEach { option ->
                val checked = options.any { it.optionItemId == option.id }
                val count = group.availableChoices.count { choice -> options.any { it.optionItemId == choice.id } }
                OrderAddOptionRow(option.name, option.priceDelta.money(currency), selected = checked, radio = false,
                    enabled = canConfigure && (checked || group.maximum == null || count < group.maximum!!)) {
                    options = if (checked) options.filterNot { it.optionItemId == option.id } else options + CreateOrderItemOptionInput(option.id, 1)
                    error = null; saved = false
                }
            }
        }
        OrderButton("Done", modifier = Modifier.fillMaxWidth()) { picker = null }
    }
}


@Composable private fun OrderStatusSelectField(status: OrderLineItemStatus, enabled: Boolean = false, onClick: () -> Unit = {}) {
    val color = orderLineStatusColor(status)
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp), color = OrderBackground, border = BorderStroke(1.dp, OrderBorder)) {
        Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = .14f)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Restaurant, null, Modifier.size(16.dp), tint = color)
                    Text(status.label(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = color, maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = OrderInk)
        }
    }
}

@Composable private fun OrderEditField(text: String, trailing: Boolean = false, onClick: () -> Unit = {}) {
    Surface(onClick = onClick, enabled = trailing, shape = RoundedCornerShape(10.dp), color = OrderBackground, border = BorderStroke(1.dp, OrderBorder)) {
        Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontFamily = Inter(), fontSize = 13.sp, color = OrderMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if(trailing) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = OrderInk)
        }
    }
}

@Composable private fun OrderQtyButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit = {}) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(8.dp), color = OrderBackground, border = BorderStroke(1.dp, OrderBorder), modifier = Modifier.size(width = 34.dp, height = 34.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(16.dp), tint = OrderInk) }
    }
}

@Composable private fun OrderEditorLabel(text: String) {
    Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = OrderInk)
}

@Composable private fun OrderProgressChoice(status: OrderLineItemStatus, selected: Boolean, modifier: Modifier = Modifier) {
    val color = orderLineStatusColor(status)
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = if(selected) .16f else .10f), border = BorderStroke(1.dp, if(selected) color else Color.Transparent), modifier = modifier.height(34.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
            Text(status.label(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if(selected) color else OrderInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun OrderItemProgressBadge(status: OrderLineItemStatus) {
    val color = orderLineStatusColor(status)
    OrderListBadge(status.label(), color, icon = Icons.Outlined.Restaurant)
}

@Composable private fun OrderItemsLegend() {
    Row(Modifier.fillMaxWidth().height(13.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
        listOf(OrderLineItemStatus.PENDING, OrderLineItemStatus.PREPARING, OrderLineItemStatus.READY, OrderLineItemStatus.FULFILLED, OrderLineItemStatus.VOIDED).forEach { status ->
            val color = orderLineStatusColor(status)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
                Text(status.label(), fontFamily = Inter(), fontSize = 10.sp, lineHeight = 10.sp, color = OrderMuted)
            }
        }
    }
}

private fun orderLineStatusColor(status: OrderLineItemStatus): Color = when(status) {
    OrderLineItemStatus.PENDING -> Color(0xFF6A6F75)
    OrderLineItemStatus.FIRED, OrderLineItemStatus.PREPARING -> Color(0xFFE56A14)
    OrderLineItemStatus.READY -> Color(0xFF0B83B5)
    OrderLineItemStatus.FULFILLED -> OrderGreen
    OrderLineItemStatus.CANCELLED, OrderLineItemStatus.VOIDED -> Color(0xFFB13A2F)
}

private fun OrderLineItem.orderItemSubtitle(): String {
    val parts = buildList {
        add(variantNameSnapshot ?: "Regular")
        options.orEmpty().forEach { add(it.optionNameSnapshot) }
    }
    return parts.joinToString("  •  ")
}

@Composable private fun OrderEditChoiceModal(
    order: Order,
    onDismiss: () -> Unit,
    onItems: () -> Unit,
    onInfo: () -> Unit,
    onProgress: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().padding(top = 300.dp, end = 96.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Surface(
                modifier = Modifier.width(210.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, OrderBorder)
            ) {
                Column(Modifier.padding(vertical = 18.dp)) {
                    OrderEditMenuItem("Order items", onItems)
                    OrderEditMenuItem("Order info", onInfo)
                    OrderEditMenuItem("Order progress", onProgress)
                }
            }
        }
    }
}

@Composable private fun OrderProgressChoiceModal(
    order: Order,
    state: OrdersUiState,
    model: OrdersScreenModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selected by remember(order.id) { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy = submitting || state.isSaving
    val hasPendingItems = order.lineItems.orEmpty().any { it.awaitingKitchen }
    val hasItems = order.lineItems.orEmpty().any { it.active }
    val steps = listOf("Send to kitchen", "Mark ready", "Mark as served", "Close order")
    fun available(action: String): Boolean = order.editable && !busy && !state.needsReconciliation && when (action) {
        "Send to kitchen" -> state.can("ORDER_UPDATE") && hasPendingItems
        "Mark ready" -> state.can("ORDER_UPDATE") && hasItems && order.fulfillmentStatus !in setOf(OrderFulfillmentStatus.READY, OrderFulfillmentStatus.FULFILLED)
        "Mark as served" -> state.can("ORDER_UPDATE") && hasItems && order.fulfillmentStatus != OrderFulfillmentStatus.FULFILLED
        "Close order" -> state.can("ORDER_CLOSE")
        else -> false
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = false
    )) {
        Surface(Modifier.padding(20.dp).widthIn(max = 440.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp), color = Color.White,
            border = BorderStroke(1.dp, OrderBorder), shadowElevation = 12.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Order progress", fontFamily = Inter(), fontWeight = FontWeight.Bold,
                        fontSize = 19.sp, color = OrderInk, modifier = Modifier.weight(1f))
                    Surface(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(18.dp), color = Color.White,
                        border = BorderStroke(1.dp, OrderBorder), shadowElevation = 2.dp) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, "Close", Modifier.size(20.dp), tint = OrderInk)
                        }
                    }
                }
                HorizontalDivider(color = OrderBorder)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current", fontFamily = Inter(), fontSize = 12.sp, color = OrderMuted)
                        OrderListBadge(statusLabel(order.status, order.unpaidAt), statusColor(order.status, order.unpaidAt), dot = true)
                        if (order.status.showsFulfillmentProgress) OrderListBadge(order.progressLabel(), orderProgressColor(order.fulfillmentStatus), Icons.Outlined.Restaurant)
                    }
                    Text("Choose the next step, then save your changes.", fontFamily = Inter(), fontSize = 13.sp, color = OrderMuted)
                    Column {
                        steps.forEachIndexed { index, action ->
                            val chosen = selected == action
                            val enabled = available(action)
                            val icon = when (action) {
                                "Send to kitchen" -> Icons.Outlined.Send
                                "Mark ready" -> Icons.Outlined.Restaurant
                                "Mark as served" -> Icons.Outlined.CheckCircleOutline
                                else -> Icons.Outlined.ReceiptLong
                            }
                            val description = when (action) {
                                "Send to kitchen" -> if (hasPendingItems) "Send all pending items to the kitchen" else "No pending items to send"
                                "Mark ready" -> "Ready for service"
                                "Mark as served" -> "All items have been served"
                                else -> "Finish and close this order"
                            }
                            Surface(onClick = { selected = action; error = null }, enabled = enabled,
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                                color = if (chosen) OrderGreen.copy(alpha = .07f) else Color.White,
                                border = BorderStroke(if (chosen) 1.4.dp else 1.dp, if (chosen) OrderGreen else OrderBorder)) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(shape = RoundedCornerShape(18.dp), color = if (chosen) OrderGreen else OrderBackground, modifier = Modifier.size(36.dp)) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(icon, null, Modifier.size(18.dp).rotate(if (index == 0) -45f else 0f), tint = if (chosen) Color.White else if (enabled) OrderInk else OrderMuted)
                                        }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(if (action == "Send to kitchen") "Send all to kitchen" else action,
                                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                            color = if (enabled || chosen) OrderInk else OrderMuted)
                                        Text(description, fontFamily = Inter(), fontSize = 11.sp, color = OrderMuted)
                                    }
                                    RadioButton(selected = chosen, onClick = null, enabled = enabled,
                                        colors = RadioButtonDefaults.colors(selectedColor = OrderGreen), modifier = Modifier.size(20.dp))
                                }
                            }
                            if (index < steps.lastIndex) Box(Modifier.padding(start = 28.dp).width(2.dp).height(12.dp).background(OrderBorder))
                        }
                    }
                    error?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Color(0xFFB13A2F)) }
                    if (state.needsReconciliation) Text("Check the last change in the order before trying again.", fontFamily = Inter(), fontSize = 12.sp, color = Color(0xFFB13A2F))
                }
                HorizontalDivider(color = OrderBorder)
                Button(onClick = {
                    val action = selected
                    if (action != null && available(action) && !submitting) {
                        submitting = true; error = null
                        scope.launch {
                            try {
                                val result: Result<*> = when (action) {
                                    "Send to kitchen" -> model.operations.sendToKitchen(order.id)
                                    "Mark ready" -> model.operations.markOrderReady(order.id)
                                    "Mark as served" -> model.operations.fulfillOrder(order.id)
                                    "Close order" -> model.operations.closeOrder(order.id)
                                    else -> Result.failure<Unit>(IllegalArgumentException("Choose a progress step"))
                                }
                                result.fold({ onDismiss() }, { error = it.message ?: "Could not save order progress" })
                            } finally { submitting = false }
                        }
                    }
                }, enabled = selected?.let { available(it) } == true, modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = OrderGreen,
                        disabledContainerColor = if (busy) OrderGreen else OrderGreen.copy(alpha = .5f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Save changes", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }
}


@Composable private fun OrderEditMenuItem(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        OrderText(
            text,
            20,
            true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}

@Composable private fun OrdersEmptyListPanel(title: String, showCreate: Boolean, modifier: Modifier = Modifier, onCreate: () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(orderEmptyDiningResource),
                contentDescription = null,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(.70f).height(300.dp).offset(y = (-18).dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(2.dp))
            Text(
                title,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = Color(0xFF20242A),
                maxLines = 1
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if(title == "No active orders") "Create a new order to start taking items." else "Try a different search or filter.",
                fontFamily = Inter(),
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = OrderMuted,
                maxLines = 1
            )
            if(showCreate) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onCreate,
                    modifier = Modifier.width(190.dp).height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrderGreen)
                ) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("New order", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable private fun OrdersNoSelectionPanel() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(110.dp).background(Color(0xFFF6F6F4), RoundedCornerShape(55.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ReceiptLong, null, Modifier.size(52.dp), tint = Color(0xFF868B8E))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Select an order to see its details",
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            color = Color(0xFF20242A),
            maxLines = 1
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Choose an order from the list to view items,\nstatus and more.",
            modifier = Modifier.fillMaxWidth(),
            fontFamily = Inter(),
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = OrderMuted,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@OptIn(InternalResourceApi::class)
private val orderEmptyDiningResource: DrawableResource by lazy {
    DrawableResource(
        "drawable:order_empty_dining",
        setOf(ResourceItem(setOf(), "composeResources/mobile_desktop.shared.generated.resources/drawable/order_empty_dining.png", -1, -1))
    )
}

@Composable
private fun OrdersListPanel(
    orders: List<OrderSummary>,
    hasNext: Boolean = false,
    loadingMore: Boolean = false,
    pageError: Boolean = false,
    onLoadMore: () -> Unit = {},
    selectedId: String?,
    compact: Boolean,
    modifier: Modifier = Modifier,
    flashId: String? = null,
    onSelect: (String) -> Unit
) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
        val listState = rememberLazyListState()
        LaunchedEffect(flashId, orders.size) {
            val index = orders.indexOfFirst { it.id == flashId }
            if (flashId != null && index >= 0) listState.animateScrollToItem(index)
        }
        val flash by rememberInfiniteTransition(label = "order-flash").animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
            label = "order-flash-alpha"
        )
        val nearEnd by remember(listState, orders.size) { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= orders.size - 5 } }
        LaunchedEffect(nearEnd, hasNext, loadingMore, pageError, orders.size) {
            if (nearEnd && hasNext && !loadingMore && !pageError) onLoadMore()
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 4.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(orders, key = { it.id }) { row ->
                    Box {
                        OrderListCard(row, selectedId == row.id, compact) { onSelect(row.id) }
                        if (row.id == flashId) Box(Modifier.matchParentSize().border(3.dp, OrderGreen.copy(alpha = flash), RoundedCornerShape(12.dp)))
                    }
                }
                if (hasNext) item(key = "history-load-more") {
                    TextButton(onClick = onLoadMore, enabled = !loadingMore) {
                        Text(if (loadingMore) "Loading…" else if (pageError) "Try again" else "Load more")
                    }
                }
            }
            PlatformVerticalScrollbar(state = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(3.dp).padding(vertical = 10.dp)
            )
        }
    }
}

@Composable private fun OrderListCard(order: OrderSummary, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(order.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if(copied) {
            delay(1200)
            copied = false
        }
    }
    val accent = orderAccentColor(order)
    Surface(
        onClick,
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if(selected) OrderGreen else Color(0xFFE8ECE6)),
        shadowElevation = if(selected) 0.dp else 1.dp
    ) {
        if(!compact) {
            Row(Modifier.fillMaxWidth().height(80.dp)) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
                Row(
                    Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp, top = 9.dp, end = 28.dp, bottom = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.widthIn(min = 150.dp, max = 188.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                order.displayOrderNumber(),
                                modifier = Modifier.weight(1f, fill = false),
                                fontFamily = Inter(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = OrderInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(order.orderNumber))
                                    copied = true
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(15.dp), tint = OrderInk)
                            }
                        }
                        Text(
                            "${order.guestCount ?: 1} ${if((order.guestCount ?: 1) == 1) "guest" else "guests"} · ${order.customerName ?: "No name provided"}",
                            fontFamily = Inter(),
                            fontSize = 12.sp,
                            color = OrderMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if(copied) Text("Copied", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = OrderGreen, maxLines = 1)
                    }
                    Column(Modifier.widthIn(min = 118.dp, max = 138.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrderListPill(
                            Icons.Outlined.TableRestaurant,
                            order.tableName ?: order.tableNumber?.let { "Table $it" } ?: "Table -",
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Some dishes still wait to be sent: the reminder takes this line, in amber.
                        val notSent = notSentNote(order.fulfillmentStatus, order.notSentItems)
                        OrderListIconText(
                            Icons.Outlined.FormatListBulleted,
                            if (notSent != null) "${order.notSentItems} of ${order.itemCount ?: 0} not sent"
                            else "${order.itemCount ?: 0} ${if((order.itemCount ?: 0) == 1) "item" else "items"}",
                            color = if (notSent != null) OrderNotSentColor else OrderMuted,
                            bold = notSent != null
                        )
                    }
                    OrderColumnDivider()
                    Column(Modifier.widthIn(min = 150.dp, max = 176.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrderListBadge(statusLabel(order.status, order.unpaidAt), statusColor(order.status, order.unpaidAt), dot = true)
                        if (order.status.showsFulfillmentProgress) OrderListBadge(order.progressLabel(), orderProgressColor(order.fulfillmentStatus), Icons.Outlined.Restaurant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(102.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (order.closedUnpaid) OrderListIconText(Icons.Outlined.Schedule, order.unpaidAt.orEmpty().unpaidDay(), color = UnpaidRed, bold = true)
                            else OrderListIconText(Icons.Outlined.Schedule, order.openedAt.elapsedLabel(), color = OrderGreen, bold = true)
                            Text("Opened at ${order.openedAt.orderTime()}", fontFamily = Inter(), fontSize = 12.sp, color = OrderMuted, maxLines = 1)
                        }
                        Text(
                            (order.unpaidAmount.takeIf { order.closedUnpaid } ?: order.total).money(order.currency),
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (order.closedUnpaid) UnpaidRed else OrderInk,
                            modifier = Modifier.width(58.dp),
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.requiredSize(32.dp).background(Color(0xFFF0F4F0), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(19.dp), tint = OrderInk)
                    }
                }
            }
        } else Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OrderText(order.displayOrderNumber(), 15, true, modifier = Modifier.weight(1f)); OrderText(order.openedAt.orderTime(), 12, color = OrderMuted)
            }
            OrderListPill(Icons.Outlined.TableRestaurant, order.tableName ?: order.tableNumber?.let { "Table $it" } ?: "Table -")
            if(order.customerName != null || order.guestCount != null) OrderText(listOfNotNull(order.guestCount?.let { "$it guests" }, order.customerName).joinToString(" · "), 13, color = OrderMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OrderBadge(statusLabel(order.status, order.unpaidAt)); if (order.status.showsFulfillmentProgress) OrderBadge(order.progressLabel()); notSentNote(order.fulfillmentStatus, order.notSentItems)?.let { OrderText(it, 12, true, OrderNotSentColor) }; Spacer(Modifier.weight(1f)); OrderText((order.unpaidAmount.takeIf { order.closedUnpaid } ?: order.total).money(order.currency), 14, true)
            }
        }
    }
}

@Composable private fun OrderColumnDivider() {
    Box(Modifier.width(1.dp).height(50.dp).background(OrderBorder))
}


@Composable private fun DesktopOrderDetailsPanel(
    order: Order,
    state: OrdersUiState,
    onEditItems: () -> Unit,
    onEditInfo: () -> Unit,
    onEditProgress: () -> Unit,
    onAction: (String) -> Unit,
    onPayment: () -> Unit,
    onClose: () -> Unit,
    onRequestBill: () -> Unit = {},
    canReopenForPayment: Boolean = false,
    canReopenOrder: Boolean = false,
    onReopenForPayment: () -> Unit = {}
) {
    var tab by remember(order.id) { mutableStateOf("Items") }
    val canCloseUnpaid = order.canCloseUnpaid && state.can("ORDER_VOID")
    val canEdit = order.editable && state.can("ORDER_UPDATE")
    var editMenu by remember { mutableStateOf(false) }
    var headerMenu by remember(order.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(order.displayOrderNumber(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = OrderInk, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            OrderListPill(Icons.Outlined.TableRestaurant, order.tableName ?: order.tableNumber?.let { "Table $it" } ?: "Table -", modifier = Modifier.widthIn(min = 92.dp, max = 120.dp))
            Spacer(Modifier.width(8.dp))
            Box {
                Surface(onClick = { headerMenu = true },
                    enabled = (canEdit || canCloseUnpaid || (canReopenOrder && order.status == OrderStatus.CLOSED)) && !state.isSaving && !state.needsReconciliation,
                    modifier = Modifier.size(34.dp), shape = RoundedCornerShape(8.dp), color = Color.White,
                    border = BorderStroke(1.dp, OrderBorder)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.MoreVert, "Order actions", Modifier.size(20.dp), tint = OrderInk)
                    }
                }
                DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false },
                    shape = RoundedCornerShape(10.dp), containerColor = Color.White,
                    tonalElevation = 0.dp, shadowElevation = 6.dp) {
                    if (canEdit) {
                        OrderEditDropdownItem("Order items", Icons.Outlined.FormatListBulleted) { headerMenu = false; onEditItems() }
                        OrderEditDropdownItem("Order info", Icons.Outlined.ReceiptLong) { headerMenu = false; onEditInfo() }
                        OrderEditDropdownItem("Order progress", Icons.Outlined.Restaurant) { headerMenu = false; onEditProgress() }
                    }
                    // Guests left without paying (user, 2026-11-01): voided as unpaid, reopened later for payment.
                    if (canCloseUnpaid) {
                        if (canEdit) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = OrderBorder)
                        OrderEditDropdownItem("Close as unpaid", Icons.Outlined.MoneyOff, UnpaidRed) { headerMenu = false; onAction("Close as unpaid") }
                    }
                    if (canReopenOrder && order.status == OrderStatus.CLOSED) {
                        OrderEditDropdownItem("Reopen order", Icons.Outlined.ReceiptLong) { headerMenu = false; onAction("Reopen order") }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(onClick = onClose, modifier = Modifier.size(34.dp), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(19.dp), tint = OrderInk)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.widthIn(min = 124.dp, max = 158.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OrderListBadge(statusLabel(order.status, order.unpaidAt), statusColor(order.status, order.unpaidAt), dot = true)
                if (order.status.showsFulfillmentProgress) OrderListBadge(order.progressLabel(), orderProgressColor(order.fulfillmentStatus), Icons.Outlined.Restaurant)
                notSentNote(order.fulfillmentStatus, order.notSentItems)?.let {
                    Text(it, Modifier.padding(start = 10.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = OrderNotSentColor, maxLines = 1)
                }
            }
            OrderDetailMiniInfo(Icons.Outlined.PersonOutline, order.customerName ?: "No name provided", "${order.guestCount ?: 1} ${if((order.guestCount ?: 1) == 1) "guest" else "guests"}", Modifier.weight(.72f))
            // Opens the order's dishes, where they're added and changed (user, 2026-11-01).
            if (order.unpaidAt == null) OutlinedButton(
                onClick = onEditItems,
                enabled = order.editable && state.can("ORDER_UPDATE") && !state.isSaving && !state.needsReconciliation,
                modifier = Modifier.height(38.dp).width(118.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, OrderGreen),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(17.dp), tint = OrderGreen)
                Spacer(Modifier.width(6.dp))
                Text("Edit order", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = OrderGreen, maxLines = 1)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Items", "Order info", "Notes", "History").forEach { label ->
                val selected = tab == label
                Box(Modifier.weight(1f).height(42.dp).clickable { tab = label }, contentAlignment = Alignment.Center) {
                    Text(
                        if(label == "Items") "Items (${order.lineItems.orEmpty().filter { it.active }.sumOf { it.quantity }})" else label,
                        fontFamily = Inter(),
                        fontWeight = if(selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp,
                        color = if(selected) OrderGreen else OrderMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Box(Modifier.align(Alignment.BottomCenter).height(3.dp).fillMaxWidth().background(if(selected) OrderGreen else Color.Transparent, RoundedCornerShape(3.dp)))
                }
            }
        }
        HorizontalDivider(color = OrderBorder)
        when(tab) {
            "Items" -> DesktopOrderItemsList(order, Modifier.weight(1f).fillMaxWidth())
            "Order info" -> DesktopOrderFixedText(listOf(
                "Order type" to order.orderType.label(),
                "Source" to order.source.label(),
                "Payment" to order.paymentStatus.label(),
                "Created" to order.createdAt.orderTime(),
            ), Modifier.weight(1f).fillMaxWidth())
            "Notes" -> Box(Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp)) { Text(order.notes?.takeIf { it.isNotBlank() } ?: "No notes", fontFamily = Inter(), fontSize = 13.sp, color = OrderMuted) }
            else -> DesktopOrderHistory(order, Modifier.weight(1f).fillMaxWidth())
        }
        HorizontalDivider(color = OrderBorder)
        Spacer(Modifier.height(12.dp))
        if (order.unpaidAt != null) {
            UnpaidStrip(order)
            Spacer(Modifier.height(12.dp))
        }
        OrderAmountRowCompact("Subtotal", order.subtotal.money(order.currency))
        if(order.discountTotal.value.toDoubleOrNull() != 0.0) OrderAmountRowCompact("Discount", "− ${order.discountTotal.money(order.currency)}")
        OrderAmountRowCompact("Service charge (0%)", order.serviceChargeTotal.money(order.currency))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OrderInk, modifier = Modifier.weight(1f))
            Text(order.total.money(order.currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = OrderInk)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopOrderActionButton("Print", Icons.Outlined.Print, Modifier.weight(1f), container = OrderBackground, content = OrderInk) { }
            if (order.unpaidAt == null) Box(Modifier.weight(1.25f)) {
                DesktopOrderActionButton("Edit order", Icons.Outlined.Edit, Modifier.fillMaxWidth(), outlined = true) {
                    if(order.editable && state.can("ORDER_UPDATE")) editMenu = true
                }
                DropdownMenu(
                    expanded = editMenu,
                    onDismissRequest = { editMenu = false },
                    offset = DpOffset(x = 0.dp, y = (-8).dp),
                    modifier = Modifier.background(Color.White),
                    shape = RoundedCornerShape(10.dp),
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 6.dp
                ) {
                    OrderEditDropdownItem("Order items", Icons.Outlined.FormatListBulleted) { editMenu = false; onEditItems() }
                    OrderEditDropdownItem("Order info", Icons.Outlined.ReceiptLong) { editMenu = false; onEditInfo() }
                    OrderEditDropdownItem("Order progress", Icons.Outlined.Restaurant) { editMenu = false; onEditProgress() }
                }
            }
            // One button for the bill (user, 2026-11-01): "Request bill" while the guests eat, then "Pay" once they asked for it.
            val canRequest = order.billRequestedAt == null && order.editable && order.paymentStatus != OrderPaymentStatus.PAID && state.can("ORDER_UPDATE")
            if (order.closedUnpaid) {
                if (canReopenForPayment) DesktopOrderActionButton("Reopen for payment", Icons.Outlined.CreditCard, Modifier.weight(1.6f),
                    container = OrderGreen, content = Color.White) { if (!state.isSaving && !state.needsReconciliation) onReopenForPayment() }
            } else if (canRequest) DesktopOrderActionButton("Request bill", Icons.Outlined.ReceiptLong, Modifier.weight(1.25f), container = OrderGreen, content = Color.White) {
                if (!state.isSaving && !state.needsReconciliation) onRequestBill()
            } else DesktopOrderActionButton(if (order.paymentStatus == OrderPaymentStatus.PAID) "Payment" else "Pay", Icons.Outlined.CreditCard, Modifier.weight(1.25f),
                container = OrderGreen, content = Color.White) { onPayment() }
        }
    }
}


@Composable private fun OrderEditDropdownItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = OrderInk,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        },
        text = {
            Text(
                text = label,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = tint,
                letterSpacing = 0.sp
            )
        },
        onClick = onClick
    )
}

@Composable private fun OrderDetailMiniInfo(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = OrderInk)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OrderInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontFamily = Inter(), fontSize = 12.sp, color = OrderMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun DesktopOrderItemsList(order: Order, modifier: Modifier = Modifier) {
    val activeItems = order.lineItems.orEmpty().filter { it.active }
    if(activeItems.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            // It only says so (user, 2026-11-01); dishes are added through Edit order.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Restaurant, null, Modifier.size(28.dp), tint = OrderMuted.copy(alpha = .6f))
                Text("No items yet", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OrderMuted)
            }
        }
        return
    }
    val listState = rememberLazyListState()
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp, end = 8.dp, bottom = 10.dp), state = listState) {
            items(activeItems, key = { it.id }) { line ->
                DesktopOrderItemRow(line, order.currency)
                HorizontalDivider(color = OrderBorder)
            }
        }
        PlatformVerticalScrollbar(state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(3.dp).padding(vertical = 10.dp)
        )
    }
}

@Composable private fun DesktopOrderItemRow(line: OrderLineItem, currency: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEFF3EF)), contentAlignment = Alignment.Center) {
            com.saporini.mobile_desktop.pos.menu.ui.item.MenuPhoto(line.imageUrl, line.itemNameSnapshot, Modifier.fillMaxSize(), iconSize = 18.dp)
        }
        Text("${line.quantity}×", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OrderInk, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(line.itemNameSnapshot, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OrderInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = buildList {
                line.variantNameSnapshot?.let { add(it) }
                line.options.orEmpty().forEach { add("${it.optionNameSnapshot}${if(it.quantity > 1) " × ${it.quantity}" else ""}") }
                line.notes?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(", ")
            Text(detail.ifBlank { line.status.label() }, fontFamily = Inter(), fontSize = 12.sp, color = OrderMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(line.lineTotal.money(currency), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OrderInk, maxLines = 1)
    }
}

@Composable private fun DesktopOrderFixedText(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (label, value) -> OrderAmountRowCompact(label, value) }
    }
}

@Composable private fun DesktopOrderHistory(order: Order, modifier: Modifier = Modifier) {
    LazyColumn(modifier.padding(top = 14.dp)) {
        items(order.events.orEmpty().sortedByDescending { it.createdAt }, key = { it.id }) { event ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(event.eventType.label(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OrderInk)
                Text(event.note ?: event.createdAt.replace('T', ' ').take(16), fontFamily = Inter(), fontSize = 12.sp, color = OrderMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = OrderBorder)
        }
    }
}

@Composable private fun OrderAmountRowCompact(label: String, amount: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Inter(), fontSize = 13.sp, color = OrderMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(amount, fontFamily = Inter(), fontSize = 13.sp, color = OrderInk, maxLines = 1)
    }
}

@Composable private fun DesktopOrderActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    container: Color = Color.White,
    content: Color = OrderGreen,
    outlined: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = shape,
        color = container,
        border = if(outlined) BorderStroke(1.dp, OrderGreen) else null
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = content)
            Spacer(Modifier.width(7.dp))
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Order.displayOrderNumber(): String {
    val raw = orderNumber.trim()
    return when {
        raw.startsWith("#") -> raw
        raw.startsWith("ORD-", ignoreCase = true) -> "#${raw.drop(4)}"
        else -> "#$raw"
    }
}

private fun Order.progressLabel(): String = fulfillmentStatus.label()

@Composable private fun OrderListPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = Color(0xFFF2F6F2)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(19.dp), tint = OrderInk)
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OrderInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun OrderListIconText(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OrderInk,
    muted: Boolean = false,
    bold: Boolean = false
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = if(muted) OrderMuted else OrderInk)
        Text(text, fontFamily = Inter(), fontWeight = if(bold) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp, color = if(muted) OrderMuted else color, maxLines = 1)
    }
}

@Composable private fun OrderListBadge(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, dot: Boolean = false) {
    Surface(color = color.copy(alpha = .14f), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if(dot) Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
            if(icon != null) Icon(icon, null, Modifier.size(15.dp), tint = color)
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun OrderSummary.displayOrderNumber(): String {
    val raw = orderNumber.trim()
    return when {
        raw.startsWith("#") -> raw
        raw.startsWith("ORD-", ignoreCase = true) -> "#${raw.drop(4)}"
        else -> "#$raw"
    }
}

private fun orderAccentColor(order: OrderSummary): Color = when(order.status) {
    OrderStatus.CLOSED -> Color(0xFF5F6870)
    OrderStatus.CANCELLED, OrderStatus.VOIDED -> Color(0xFFAA3F38)
    OrderStatus.DRAFT -> Color(0xFF5F6870)
    OrderStatus.OPEN -> when(order.fulfillmentStatus) {
        OrderFulfillmentStatus.READY -> Color(0xFF1294E8)
        OrderFulfillmentStatus.IN_PREPARATION, OrderFulfillmentStatus.PARTIALLY_FULFILLED, OrderFulfillmentStatus.PENDING -> Color(0xFFFF6B00)
        OrderFulfillmentStatus.FULFILLED -> Color(0xFF139928)
    }
}

// An order closed as unpaid reads "Unpaid" in red, not "Voided".
private fun statusLabel(status: OrderStatus, unpaidAt: String?): String =
    if (status == OrderStatus.VOIDED && unpaidAt != null) "Unpaid" else status.label()

private fun statusColor(status: OrderStatus, unpaidAt: String?): Color =
    if (status == OrderStatus.VOIDED && unpaidAt != null) UnpaidRed else orderStatusColor(status)

private fun orderStatusColor(status: OrderStatus): Color = when(status) {
    OrderStatus.OPEN -> Color(0xFF147A25)
    OrderStatus.CLOSED -> Color(0xFF24748A)
    OrderStatus.CANCELLED, OrderStatus.VOIDED -> Color(0xFFAA3F38)
    OrderStatus.DRAFT -> Color(0xFF4F5350)
}

private fun orderProgressColor(status: OrderFulfillmentStatus): Color = when(status) {
    OrderFulfillmentStatus.READY -> Color(0xFF087594)
    OrderFulfillmentStatus.IN_PREPARATION, OrderFulfillmentStatus.PARTIALLY_FULFILLED -> Color(0xFFD15F00)
    OrderFulfillmentStatus.FULFILLED -> OrderGreen
    OrderFulfillmentStatus.PENDING -> Color(0xFF4F5350)
}

private fun OrderSummary.progressLabel(): String = fulfillmentStatus.label()

@OptIn(ExperimentalTime::class)
private fun String.elapsedLabel(): String = runCatching {
    val minutes = (Clock.System.now() - Instant.parse(this)).inWholeMinutes.coerceAtLeast(0)
    if(minutes < 60) "${minutes} min" else {
        val hours = minutes / 60
        val rest = minutes % 60
        if(rest == 0L) "${hours}h" else "${hours}h ${rest}m"
    }
}.getOrDefault("")

@Composable
private fun OrdersListSkeleton(modifier: Modifier) {
    val alpha = com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha("orders-list")
    Surface(modifier.alpha(alpha), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
        Column(Modifier.fillMaxSize().padding(8.dp).clipToBounds(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(6) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, OrderBorder, RoundedCornerShape(10.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.width(64.dp).height(16.dp))
                        Spacer(Modifier.weight(1f))
                        com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.width(40.dp).height(11.dp), com.saporini.mobile_desktop.core.components.SkeletonLight)
                    }
                    com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.width(96.dp).height(30.dp), com.saporini.mobile_desktop.core.components.SkeletonLight, RoundedCornerShape(8.dp))
                    com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.width(150.dp).height(11.dp), com.saporini.mobile_desktop.core.components.SkeletonLight)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.width(52.dp).height(22.dp), com.saporini.mobile_desktop.core.components.SkeletonLight, RoundedCornerShape(50))
                        com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.width(96.dp).height(22.dp), com.saporini.mobile_desktop.core.components.SkeletonLight, RoundedCornerShape(50))
                        Spacer(Modifier.weight(1f))
                        com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.width(60.dp).height(16.dp))
                    }
                }
            }
        }
    }
}
