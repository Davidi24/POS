@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.BorderStroke
import com.saporini.mobile_desktop.admin.inventory.removeProduct
import com.saporini.mobile_desktop.admin.inventory.catalogOf
import com.saporini.mobile_desktop.admin.inventory.catalogItemDraft
import com.saporini.mobile_desktop.admin.inventory.linkProduct
import com.saporini.mobile_desktop.admin.inventory.CatalogProduct
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha
import com.saporini.mobile_desktop.core.components.SkeletonLight
import com.saporini.mobile_desktop.core.components.SkeletonBox
import com.saporini.mobile_desktop.core.components.SkeletonBlock
import com.saporini.mobile_desktop.core.components.FilterPills
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import com.saporini.mobile_desktop.admin.inventory.mapsDirections
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import com.saporini.mobile_desktop.core.components.CenterChosen
import com.saporini.mobile_desktop.core.components.fadingEdges
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.InventoryTab
import com.saporini.mobile_desktop.admin.inventory.PurchaseOrder
import com.saporini.mobile_desktop.admin.inventory.PurchaseStatus
import com.saporini.mobile_desktop.admin.inventory.PurchaseSteps
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.Supplier
import com.saporini.mobile_desktop.admin.inventory.SupplierBook
import com.saporini.mobile_desktop.admin.inventory.SupplierItem
import com.saporini.mobile_desktop.admin.inventory.SupplierPlace
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.BottomToast
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.ScrollList
import com.saporini.mobile_desktop.core.components.ScrollbarGap
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.ToolbarPrimaryButton
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.PlatformBackHandler
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.supplier_orders
import mobile_desktop.shared.generated.resources.supplier_place
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import com.saporini.mobile_desktop.core.components.DoneBurst
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.saporini.mobile_desktop.core.components.LeftEdgeActionButton
import com.saporini.mobile_desktop.core.components.RightEdgeActionButton
import com.saporini.mobile_desktop.core.components.RightEdgeActionButtonHeight
import com.saporini.mobile_desktop.core.components.edgeActionButtonHeight

// Admin Hub → Suppliers. How buying works here, in the order people do it:
//   1. add a supplier, 2. add their places (warehouses or branches you order from), 3. link the stock items you buy
//   from them (existing items: one "Eggs" can come from several suppliers), 4. order from a supplier place into one of
//   your stock places, and receive the delivery there: that adds it to Stock (real stock receipts).
// Suppliers, places, links and orders are MOCK data in SupplierBook until the backend round.

internal enum class SupplierPage(val label: String) { SUPPLIERS("Suppliers"), ORDERS("Orders") }

private enum class SupplierShow(val label: String) { ACTIVE("Active"), OFF("Not active"), ALL("All suppliers") }

// Open: still to arrive · In progress: arrived, being counted or confirmed · Confirmed: in stock · Cancelled.
internal enum class OrderShow(val label: String) { OPEN("Open"), IN_PROGRESS("In progress"), RECEIVED("Confirmed"), CANCELLED("Cancelled") }

/** The supplier dialog that's open, if any. */
internal sealed interface SupplierDialog {
    data object Add : SupplierDialog
    data class Edit(val supplierId: String) : SupplierDialog
    data class Place(val supplierId: String, val placeId: String?) : SupplierDialog
    data class LinkItems(val supplierId: String) : SupplierDialog
    data class EditLink(val supplierId: String, val itemId: String) : SupplierDialog
    data class NewOrder(val supplierId: String?, val itemIds: Set<String> = emptySet()) : SupplierDialog
    data class Order(val orderId: String, val receiving: Boolean = false) : SupplierDialog
    data class ImportCatalog(val supplierId: String) : SupplierDialog
    data class LinkProduct(val productId: String) : SupplierDialog
}

/** Admin Hub → Suppliers: who you buy from, what they bring, and what's on its way. */
@Composable
internal fun SuppliersScreen(modifier: Modifier = Modifier, onBackToInventory: (() -> Unit)? = null) {
    val model = koinInject<InventoryScreenModel>()
    // Choose the tab before the model starts, so it loads once.
    remember(model) { model.tab(InventoryTab.SUPPLIERS); true }
    BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    SuppliersContent(state, model, modifier, onBackToInventory = onBackToInventory)
}

@Composable
internal fun SuppliersContent(
    state: InventoryState,
    model: InventoryScreenModel,
    modifier: Modifier = Modifier,
    // For the look check: a supplier (by name) chosen, a page, a part of the supplier's page, or a dialog open from the start.
    initialSupplier: String? = null,
    initialPage: SupplierPage = SupplierPage.SUPPLIERS,
    initialPart: SupplierPart = SupplierPart.ITEMS,
    initialDialog: SupplierDialog? = null,
    // A supplier opened from Stock ("Go to …"): the phone's back button returns there.
    onBackToInventory: (() -> Unit)? = null
) {
    val today = remember { Clock.System.now().toLocalDateTime(RestaurantTime.zone).date }
    LaunchedEffect(state.restaurantId, state.items, state.levels.isNotEmpty()) {
        SupplierBook.open(state.restaurantId, state.items, state.locations, state.levels, today)
    }
    // MOCK: a sample banner photo of their place (files/mock-suppliers/<name>.jpg) for suppliers that have none, once.
    LaunchedEffect(SupplierBook.suppliers.size) {
        SupplierBook.suppliers.toList().filter { it.cover == null && SupplierBook.sampleCoverTried.add(it.id) }.forEach { supplier ->
            sampleBanner(supplier.name)?.let { bytes -> SupplierBook.supplier(supplier.id)?.let { SupplierBook.update(it.copy(cover = bytes)) } }
        }
    }
    var page by rememberSaveable { mutableStateOf(initialPage) }
    // Where the Add supplier tab sits on the left edge and the New order tab on the right (0 = top, 1 = bottom); both can be dragged.
    var addButtonPosition by rememberSaveable { mutableStateOf(.84f) }
    var orderButtonPosition by rememberSaveable { mutableStateOf(.84f) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var phoneDetail by remember { mutableStateOf(false) }
    var orderShow by remember { mutableStateOf(OrderShow.OPEN) }
    // The Orders page's supplier filter (set when coming from a supplier's page).
    var orderSupplierId by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf(initialDialog) }
    // Opened from Stock's "Go to …": back goes straight back to Stock.
    var fromStock by remember { mutableStateOf(false) }
    // Asked for from Stock: open that supplier's page.
    LaunchedEffect(SupplierBook.openRequest, SupplierBook.suppliers.size) {
        val wanted = SupplierBook.openRequest ?: return@LaunchedEffect
        if (SupplierBook.supplier(wanted) != null) {
            page = SupplierPage.SUPPLIERS
            selectedId = wanted
            phoneDetail = true
            fromStock = true
            SupplierBook.openRequest = null
        }
    }
    // The order just placed: the Orders page opens on it and marks it, and a note says it went through.
    var placedId by remember { mutableStateOf<String?>(null) }
    var placedNote by remember { mutableStateOf<String?>(null) }
    // The look check names its supplier; the app starts on the first one.
    LaunchedEffect(SupplierBook.suppliers.size) {
        if (selectedId == null || SupplierBook.supplier(selectedId) == null) {
            selectedId = (initialSupplier?.let { name -> SupplierBook.suppliers.firstOrNull { it.name.equals(name, ignoreCase = true) } }
                ?: SupplierBook.suppliers.firstOrNull { it.active })?.id
            if (initialSupplier != null && selectedId != null) phoneDetail = true
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        // The phone's back button steps back inside Suppliers (user, 2026-10-30: "it should go back, not leave"): a supplier's
        // page back to the list (or to Stock when it was opened from there), Orders back to Suppliers.
        PlatformBackHandler(enabled = size.isPhone && page == SupplierPage.ORDERS) { page = SupplierPage.SUPPLIERS }
        PlatformBackHandler(enabled = size.isPhone && page == SupplierPage.SUPPLIERS && phoneDetail && SupplierBook.supplier(selectedId) != null) {
            phoneDetail = false
            if (fromStock) { fromStock = false; onBackToInventory?.invoke() }
        }
        Column(
            Modifier.fillMaxSize().padding(start = if (size.isPhone) 16.dp else 22.dp, end = if (size.isPhone) 16.dp else 22.dp,
                top = if (size.isPhone) 16.dp else 18.dp, bottom = if (size.isPhone) 0.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(if (size.isPhone) 12.dp else 14.dp)
        ) {
            SuppliersHeader(size, page, { page = it; phoneDetail = false; fromStock = false }, state, model, onAdd = { dialog = SupplierDialog.Add })
            ScreenMessages(state.error.takeIf { state.itemDraft == null && state.moveDraft == null && state.bulkMove == null }, state.notice, model::clearMessages)
            // A change the server refused (the list was loaded again).
            ScreenMessages(SupplierBook.problem, null, onClear = { SupplierBook.problem = null })
            // Until the suppliers are in, loading shapes laid out like the page, then a soft fade into it (as Inventory does).
            val waiting = state.canRead && !(state.items.isEmpty() && state.stale && state.error != null) &&
                ((state.items.isEmpty() && (state.loading || state.stale)) || (state.items.isNotEmpty() && !SupplierBook.ready))
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to suppliers", "Your role needs “View stock” to see suppliers.")
                state.items.isEmpty() && state.stale && state.error != null -> PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                else -> Crossfade(waiting, Modifier.fillMaxWidth().weight(1f), animationSpec = tween(220), label = "suppliers-loading") { skeleton ->
                  if (skeleton) SuppliersSkeleton(page, size, Modifier.fillMaxSize()) else Column(Modifier.fillMaxSize()) {
                    // No summary cards (the user took them away): the lists start right under the header.
                    val body = Modifier.fillMaxWidth().weight(1f)
                    val select: (String) -> Unit = { selectedId = it; phoneDetail = true; fromStock = false }
                    when (page) {
                        SupplierPage.ORDERS -> OrdersPanel(state, size, today, orderShow, { orderShow = it }, orderSupplierId, { orderSupplierId = it }, null,
                            onOpen = { dialog = SupplierDialog.Order(it) }, onReceive = { dialog = SupplierDialog.Order(it, receiving = true) },
                            onNew = { dialog = SupplierDialog.NewOrder(null) }, highlightId = placedId, modifier = body)
                        SupplierPage.SUPPLIERS -> {
                            val detail: @Composable (Modifier) -> Unit = { m ->
                                val supplier = SupplierBook.supplier(selectedId)
                                if (supplier == null) {
                                    Surface(m, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                                            OverviewEmpty("Choose a supplier", "See their places, the items you buy from them and their orders.",
                                                Icons.Outlined.LocalShipping, image = Res.drawable.supplier_place)
                                        }
                                    }
                                } else SupplierDetail(supplier, state, model, size, today, initialPart, onBack = { phoneDetail = false; fromStock = false },
                                    onDialog = { dialog = it },
                                    onGoToOrders = { orderId ->
                                        // Their orders on the Orders page: that order marked, else the first kind they have.
                                        val theirs = SupplierBook.ordersOf(supplier.id)
                                        orderShow = theirs.firstOrNull { it.id == orderId }?.let(::orderShowOf)
                                            ?: OrderShow.entries.firstOrNull { show -> theirs.any { orderShowOf(it) == show } } ?: OrderShow.OPEN
                                        orderSupplierId = supplier.id
                                        placedId = orderId
                                        page = SupplierPage.ORDERS
                                    }, modifier = m)
                            }
                            when {
                                size.isPhone && phoneDetail && SupplierBook.supplier(selectedId) != null -> detail(body)
                                size.isPhone -> SupplierListPanel(state, size, selectedId = null, onSelect = select, onAdd = { dialog = SupplierDialog.Add },
                                    onEdit = { dialog = SupplierDialog.Edit(it) }, cards = null, modifier = body)
                                else -> Row(body, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    SupplierListPanel(state, size, selectedId, select, onAdd = { dialog = SupplierDialog.Add }, onEdit = { dialog = SupplierDialog.Edit(it) },
                                        cards = null,
                                        modifier = Modifier.weight(1f).fillMaxHeight())
                                    detail(Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                    }
                  }
                }
            }
        }
        // "+ Add supplier" as a green tab on the left edge; drag it up or down. Phones get the smaller tab on the right edge, like
        // every other tab there, on the list only (user, 2026-10-30).
        if (state.canEdit && page == SupplierPage.SUPPLIERS && !(size.isPhone && phoneDetail && SupplierBook.supplier(selectedId) != null)) {
            val maxOffset = with(LocalDensity.current) { (maxHeight - edgeActionButtonHeight(size.isPhone)).coerceAtLeast(0.dp).toPx() }
            val placed = Modifier.align(if (size.isPhone) Alignment.TopEnd else Alignment.TopStart)
                .offset { IntOffset(0, (addButtonPosition * maxOffset).roundToInt()) }
                .draggable(orientation = Orientation.Vertical, state = rememberDraggableState { delta ->
                    if (maxOffset > 0f) addButtonPosition = (addButtonPosition + delta / maxOffset).coerceIn(0f, 1f)
                })
            if (size.isPhone) RightEdgeActionButton("Add supplier", { dialog = SupplierDialog.Add }, enabled = !state.saving, width = 100.dp,
                compact = true, modifier = placed)
            else LeftEdgeActionButton("Add supplier", { dialog = SupplierDialog.Add }, enabled = !state.saving, width = 122.dp, modifier = placed)
        }
        // "+ New order" the same way on the Orders page, on the right edge.
        if (state.canEdit && !size.isPhone && page == SupplierPage.ORDERS) {
            val maxOffset = with(LocalDensity.current) { (maxHeight - RightEdgeActionButtonHeight).coerceAtLeast(0.dp).toPx() }
            RightEdgeActionButton("New order", { dialog = SupplierDialog.NewOrder(null) }, enabled = !state.saving, width = 112.dp,
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(0, (orderButtonPosition * maxOffset).roundToInt()) }
                    .draggable(orientation = Orientation.Vertical, state = rememberDraggableState { delta ->
                        if (maxOffset > 0f) orderButtonPosition = (orderButtonPosition + delta / maxOffset).coerceIn(0f, 1f)
                    }))
        }
    }
    SupplierDialogs(dialog, state, model, today, onDialog = { dialog = it }, onAdded = { id ->
            selectedId = id; page = SupplierPage.SUPPLIERS; phoneDetail = true
            model.celebrate("${SupplierBook.supplier(id)?.name ?: "The supplier"} was added")
        },
        onPlaced = { order ->
            // A new order goes to the Orders page, on the open ones, marked.
            dialog = null
            orderShow = OrderShow.OPEN
            orderSupplierId = null
            page = SupplierPage.ORDERS
            placedId = order.id
            placedNote = "Order ${order.number} placed with ${SupplierBook.supplier(order.supplierId)?.name ?: "the supplier"}"
        }, onNote = { placedNote = it })
    BottomToast(placedNote, onDismiss = { placedNote = null })
    DoneBurst(state.done)
    // Leaving the Orders page ends the mark and the supplier filter.
    LaunchedEffect(page) { if (page != SupplierPage.ORDERS) { placedId = null; orderSupplierId = null } }
    InventoryDialogs(state, model)
}

@Composable
private fun SuppliersHeader(
    size: ScreenSize, page: SupplierPage, onPage: (SupplierPage) -> Unit, state: InventoryState, model: InventoryScreenModel, onAdd: () -> Unit
) {
    val tabs: @Composable (Modifier, Dp) -> Unit = { m, height ->
        OverviewTabs(SupplierPage.entries.map { it.label }, page.label, { label -> onPage(SupplierPage.entries.first { it.label == label }) }, m, height = height)
    }
    if (size.isPhone) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Suppliers", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
                Text("Who you buy from and what's on its way", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RefreshButton(state.loading, model::refresh, size = 38.dp)
        }
        // A phone: the two views as underlined tabs, like Inventory's.
        UnderlineTabs(SupplierPage.entries.map { it to it.label }, page, onPage,
            icon = { if (it == SupplierPage.SUPPLIERS) Icons.Outlined.Storefront else Icons.AutoMirrored.Outlined.ReceiptLong })
    } else {
        PageHeader("Suppliers", "Who you buy from, what they bring and what's on its way") {
            tabs(Modifier.width(240.dp), PageControlHeight)
            RefreshButton(state.loading, model::refresh)
        }
    }
}

@Composable
private fun GreenSquare(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(PhoneControlHeight), shape = RoundedCornerShape(8.dp), color = Kit.Green) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(19.dp), tint = Color.White) }
    }
}

/** Tabs across the page with a green line under the chosen one (a phone's view switch, as in Inventory). */
@Composable
private fun <T> UnderlineTabs(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, icon: (T) -> ImageVector) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Kit.Border))
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (value, label) ->
                val chosen = value == selected
                Column(Modifier.weight(1f).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).clickable { onSelect(value) },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.padding(top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(icon(value), null, Modifier.size(17.dp), tint = if (chosen) Kit.Green else Kit.Muted)
                        Text(label, fontFamily = Inter(), fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp,
                            color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth(0.7f).height(3.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (chosen) Kit.Green else Color.Transparent))
                }
            }
        }
    }
}

// ---- Loading shapes, laid out like the page so nothing jumps when the data arrives ----

@Composable
private fun SuppliersSkeleton(page: SupplierPage, size: ScreenSize, modifier: Modifier) {
    val alpha = rememberSkeletonAlpha("suppliers-${page.name}")
    Box(modifier.alpha(alpha).clipToBounds()) {
        if (page == SupplierPage.ORDERS) {
            SkeletonBoxPanel(Modifier.fillMaxSize()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(80.dp, 110.dp, 96.dp, 100.dp).forEach { SkeletonBox(Modifier.width(it).height(if (size.isPhone) 30.dp else 40.dp), SkeletonLight, RoundedCornerShape(8.dp)) }
                }
                SkeletonBox(Modifier.fillMaxWidth(if (size.isPhone) 1f else 0.4f).height(if (size.isPhone) 40.dp else 44.dp), SkeletonLight, RoundedCornerShape(10.dp))
                repeat(7) { SkeletonLine(if (size.isPhone) 64.dp else 70.dp) }
            }
            return@Box
        }
        val list: @Composable (Modifier) -> Unit = { m ->
            SkeletonBoxPanel(m) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(Modifier.weight(1f).height(if (size.isPhone) 40.dp else 44.dp), SkeletonLight, RoundedCornerShape(10.dp))
                    SkeletonBox(Modifier.width(if (size.isPhone) 40.dp else 130.dp).height(if (size.isPhone) 40.dp else 44.dp), SkeletonBlock, RoundedCornerShape(10.dp))
                }
                val perRow = if (size.isPhone) 1 else 2
                repeat(3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(perRow) { SkeletonSupplierCard(Modifier.weight(1f)) } }
                }
            }
        }
        if (size.isPhone) list(Modifier.fillMaxSize())
        else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            list(Modifier.weight(1f).fillMaxHeight())
            SkeletonBoxPanel(Modifier.weight(1f).fillMaxHeight()) {
                SkeletonBox(Modifier.fillMaxWidth().height(110.dp), SkeletonLight, RoundedCornerShape(12.dp))
                SkeletonBox(Modifier.width(200.dp).height(20.dp))
                SkeletonBox(Modifier.width(320.dp).height(12.dp), SkeletonLight)
                repeat(2) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonBox(Modifier.width(150.dp).height(14.dp))
                        repeat(2) { SkeletonLine(60.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBoxPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(12.dp).clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(18.dp), shape = RoundedCornerShape(5.dp))
            Spacer(Modifier.width(8.dp))
            SkeletonBox(Modifier.width(110.dp).height(14.dp))
        }
        content()
    }
}

/** A supplier card while loading: banner, picture on it, two lines. */
@Composable
private fun SkeletonSupplierCard(modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(14.dp))) {
        Box(Modifier.fillMaxWidth().height(110.dp)) {
            SkeletonBox(Modifier.fillMaxWidth().height(78.dp), SkeletonLight, RoundedCornerShape(0.dp))
            SkeletonBox(Modifier.align(Alignment.BottomStart).padding(start = 14.dp).size(62.dp), SkeletonBlock, RoundedCornerShape(16.dp))
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SkeletonBox(Modifier.fillMaxWidth(0.5f).height(14.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.35f).height(10.dp), SkeletonLight)
            SkeletonBox(Modifier.fillMaxWidth(0.8f).height(10.dp), SkeletonLight)
        }
    }
}

/** A list line while loading: colour strip, picture, two lines, a figure. */
@Composable
private fun SkeletonLine(height: Dp) {
    Row(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        SkeletonBox(Modifier.width(4.dp).fillMaxHeight(), SkeletonLight, RoundedCornerShape(0.dp))
        Spacer(Modifier.width(12.dp))
        SkeletonBox(Modifier.size(38.dp), SkeletonLight, RoundedCornerShape(9.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SkeletonBox(Modifier.fillMaxWidth(0.4f).height(12.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.6f).height(9.dp), SkeletonLight)
        }
        SkeletonBox(Modifier.padding(end = 14.dp).width(64.dp).height(14.dp))
    }
}

// ---- Suppliers list ----

@Composable
private fun SupplierListPanel(
    state: InventoryState, size: ScreenSize, selectedId: String?, onSelect: (String) -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit,
    cards: List<@Composable (Modifier) -> Unit>?, modifier: Modifier
) {
    var search by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(SupplierShow.ACTIVE) }
    val lowIds = state.lowStock.map { it.inventoryItemId }.toSet()
    val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val all = SupplierBook.suppliers.toList()
    val shown = all.filter { supplier ->
        val text = (supplier.name + " " + supplier.contactName + " " + SupplierBook.linksOf(supplier.id).joinToString(" ") { state.item(it.itemId)?.name.orEmpty() }).lowercase()
        words.all { it in text } && when (show) {
            SupplierShow.ACTIVE -> supplier.active
            SupplierShow.OFF -> !supplier.active
            SupplierShow.ALL -> true
        }
    }.sortedBy { it.name.lowercase() }
    // Adding a supplier is the green tab on the window's left edge (user, 2026-10-30), like "New item" on the right.
    OverviewPanel(Icons.Outlined.Storefront, "Suppliers", modifier, count = shown.size, bottomPadding = 0.dp, flat = size.isPhone) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val height = if (size.isPhone) PhoneControlHeight else PageControlHeight
            SearchField(search, { search = it }, Modifier.weight(1f), placeholder = "Search supplier or item", height = height)
            // A phone has no panel head, so the green square stays there.
            PhoneOptionsButton(Icons.Outlined.Tune, "Show", if (show != SupplierShow.ACTIVE) 1 else 0, height) { close ->
                OptionGroup("Show")
                SupplierShow.entries.forEach { choice ->
                    OptionLine(choice.label, show == choice, { show = choice; close() },
                        icon = when (choice) { SupplierShow.ACTIVE -> Icons.Outlined.CheckCircle; SupplierShow.OFF -> Icons.Outlined.ToggleOff; SupplierShow.ALL -> Icons.Outlined.Storefront },
                        color = if (choice == SupplierShow.OFF) Kit.Grey else Kit.Green,
                        count = all.count { when (choice) { SupplierShow.ACTIVE -> it.active; SupplierShow.OFF -> !it.active; SupplierShow.ALL -> true } })
                }
            }
        }
        ScrollList(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cards?.let { item(key = "cards") { Box(Modifier.padding(bottom = 4.dp)) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SummaryCardRow(size, it) } } } }
            if (shown.isEmpty()) item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), Alignment.Center) {
                    OverviewEmpty(
                        if (all.isEmpty()) "No suppliers yet" else "Nothing matches",
                        if (all.isEmpty()) "Add who you buy from: their places and the items they bring take a minute." else "Try another word, or show all suppliers.",
                        Icons.Outlined.Storefront, image = Res.drawable.supplier_place,
                        action = if (all.isEmpty() && state.canEdit) ({ KitButton("Add supplier", onAdd, icon = Icons.Outlined.AddBusiness) }) else null
                    )
                }
            }
            // Cards two to a row (one on a phone), like profile cards.
            val perRow = if (size.isPhone) 1 else 2
            items(shown.chunked(perRow), key = { row -> row.first().id }) { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { supplier ->
                        val links = SupplierBook.linksOf(supplier.id)
                        SupplierCard(supplier, links.size, SupplierBook.placesOf(supplier.id).size, links.count { it.itemId in lowIds },
                            SupplierBook.ordersOf(supplier.id).count { it.status.open }, supplier.id == selectedId, state.canEdit,
                            Modifier.weight(1f).fillMaxHeight(), onClick = { onSelect(supplier.id) }, onEdit = { onEdit(supplier.id) }, phone = size.isPhone)
                    }
                    if (row.size < perRow) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** A supplier as a profile card: their banner, their picture on it, name, what they bring, and where they are (with directions). */
@Composable
private fun SupplierCard(supplier: Supplier, items: Int, places: Int, low: Int, openOrders: Int, selected: Boolean, canEdit: Boolean,
                         modifier: Modifier, onClick: () -> Unit, onEdit: () -> Unit, phone: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Surface(
        onClick = onClick, modifier = modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand), shape = RoundedCornerShape(14.dp), color = Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, when { selected -> Kit.Green; hovered -> Kit.Green.copy(alpha = 0.45f); else -> Kit.RowBorder }),
        shadowElevation = if (hovered) 3.dp else 1.dp, interactionSource = interaction
    ) {
        Column(Modifier.alpha(if (supplier.active) 1f else 0.6f)) {
            // The banner a little taller (user, 2026-10-30), taller still on a phone; the box grows with it, so the picture
            // still sits on its edge.
            Box(Modifier.fillMaxWidth().height(if (phone) 156.dp else 128.dp)) {
                SupplierBanner(supplier.cover, Modifier.fillMaxWidth().height(if (phone) 124.dp else 96.dp))
                if (canEdit) {
                    Surface(onClick = onEdit, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp), shape = CircleShape,
                        color = Color.White.copy(alpha = 0.94f), shadowElevation = 1.dp) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Edit, "Edit ${supplier.name}", Modifier.size(16.dp), tint = Kit.Ink) }
                    }
                }
                Box(Modifier.align(Alignment.BottomStart).padding(start = 14.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(3.dp)) {
                    SupplierPicture(56.dp, supplier.photo)
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!supplier.active) SmallTag("Off", Kit.Grey)
                    if (openOrders > 0) SmallTag(count(openOrders, "open order"), Kit.Blue)
                    if (low > 0) SmallTag("$low low", Kit.Danger)
                }
            }
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(supplier.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${count(items, "item")} · ${count(places, "place")}", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
                if (supplier.address.isNotBlank()) AddressLine(supplier.address, Modifier.padding(top = 4.dp), directionChoices(supplier))
            }
        }
    }
}

/** Where someone is, with a "Directions" link that opens Google Maps. With two or more [choices] (label to address), the link
 *  first asks which place to go to (user rule: a supplier with two places lets you pick). */
@Composable
internal fun AddressLine(address: String, modifier: Modifier = Modifier, choices: List<Pair<String, String>> = emptyList()) {
    val uri = LocalUriHandler.current
    var picking by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Place, null, Modifier.size(15.dp), tint = Kit.Muted)
        Text(address, Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable { if (choices.size > 1) picking = true else runCatching { uri.openUri(mapsDirections(address)) } }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Outlined.Directions, null, Modifier.size(15.dp), tint = Kit.Green)
                Text("Directions", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
                if (choices.size > 1) Icon(Icons.Outlined.ExpandMore, null, Modifier.size(15.dp), tint = Kit.Green)
            }
            androidx.compose.material3.DropdownMenu(picking, { picking = false }, offset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp),
                shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp, modifier = Modifier.widthIn(min = 260.dp, max = 360.dp)) {
                Text("Which place?", Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, letterSpacing = 0.sp, color = Kit.Muted)
                choices.forEach { (label, place) ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { picking = false; runCatching { uri.openUri(mapsDirections(place)) } }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Kit.GreenSoft), Alignment.Center) {
                            Icon(Icons.Outlined.Place, null, Modifier.size(17.dp), tint = Kit.Green)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(place, fontFamily = Inter(), fontSize = 11.5.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Outlined.Directions, null, Modifier.size(17.dp), tint = Kit.Green)
                    }
                }
            }
        }
    }
}

/** Every place of a supplier you could drive to: their places with an address, and their own address when no place has it. */
internal fun directionChoices(supplier: Supplier): List<Pair<String, String>> {
    val places = SupplierBook.placesOf(supplier.id).filter { it.address.isNotBlank() }.map { it.name to it.address.trim() }
    val own = supplier.address.trim().takeIf { it.isNotBlank() && places.none { (_, address) -> address.equals(it, ignoreCase = true) } }
    return (listOfNotNull(own?.let { supplier.name to it }) + places).distinctBy { it.second.lowercase() }
}

/** MOCK: the sample banner shipped for a demo supplier's name, if there is one. */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
private suspend fun sampleBanner(name: String): ByteArray? =
    runCatching { Res.readBytes("files/mock-suppliers/${name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}.jpg") }.getOrNull()

/** A supplier's banner: their own picture, or a soft green band with the warehouse picture faded in. */
@Composable
internal fun SupplierBanner(cover: ByteArray?, modifier: Modifier) {
    val bitmap = remember(cover) { cover?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFFE6EFE1), Color(0xFFCFE0C6), Color(0xFFB9D3AC))))) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Image(painterResource(Res.drawable.supplier_place), null, Modifier.align(Alignment.CenterEnd).padding(end = 52.dp).size(96.dp).alpha(0.16f),
            contentScale = ContentScale.Fit)
    }
}

/** A supplier's own picture, or the warehouse picture when they have none (never initials). */
@Composable
internal fun SupplierPicture(size: Dp, photo: ByteArray? = null) {
    val bitmap = remember(photo) { photo?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(Kit.Tint), Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Image(painterResource(Res.drawable.supplier_place), null, Modifier.size(size * 0.86f), contentScale = ContentScale.Fit)
    }
}

@Composable
internal fun SmallTag(text: String, color: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = color, maxLines = 1)
}

// ---- One supplier ----

/**
 * A supplier to look at: who they are (Edit), their orders (open ones first), the items you buy from them and their
 * places. Their orders only show here; "Go to orders" (or an order) opens the Orders page, where orders are made and
 * received (user rule: not in two places).
 */
@Composable
private fun SupplierDetail(
    supplier: Supplier, state: InventoryState, model: InventoryScreenModel, size: ScreenSize, today: LocalDate, initialPart: SupplierPart,
    onBack: () -> Unit, onDialog: (SupplierDialog) -> Unit, onGoToOrders: (orderId: String?) -> Unit, modifier: Modifier
) {
    val links = SupplierBook.linksOf(supplier.id).mapNotNull { link -> state.item(link.itemId)?.let { link to it } }
    val places = SupplierBook.placesOf(supplier.id)
    val orders = SupplierBook.ordersOf(supplier.id)
    val lowIds = state.lowStock.map { it.inventoryItemId }.toSet()
    val edit = state.canEdit
    val pad = if (size.isPhone) 14.dp else 20.dp
    var showPast by remember(supplier.id) { mutableStateOf(false) }
    var part by remember(supplier.id) { mutableStateOf(initialPart) }
    val open = orders.filter { it.status.open }.sortedWith(compareBy<PurchaseOrder> { it.expectedOn ?: LocalDate(9999, 1, 1) })
    val past = orders.filter { !it.status.open }.sortedByDescending { it.receivedOn ?: it.orderedOn }
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, if (size.isPhone) Color.Transparent else Kit.Border)) {
        // No padding under the scrolling part: the list runs to the panel's bottom edge (a little room after the last box instead).
        ScrollColumn(Modifier.fillMaxSize().padding(start = if (size.isPhone) 0.dp else pad, top = if (size.isPhone) 4.dp else pad, end = if (size.isPhone) 0.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Who: their banner with their picture on it (like a profile), then name, contact and where they are.
            Box(Modifier.fillMaxWidth().height(if (size.isPhone) 148.dp else 176.dp)) {
                SupplierBanner(supplier.cover, Modifier.fillMaxWidth().height(if (size.isPhone) 112.dp else 136.dp).clip(RoundedCornerShape(12.dp)))
                if (size.isPhone) {
                    Box(Modifier.padding(8.dp).size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.94f)).clickable(onClick = onBack), Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Reply, "Back to suppliers", Modifier.size(20.dp), tint = Kit.Ink)
                    }
                }
                Box(Modifier.align(Alignment.BottomStart).padding(start = 18.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(4.dp)) {
                    SupplierPicture(if (size.isPhone) 64.dp else 80.dp, supplier.photo)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(supplier.name, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (size.isPhone) 19.sp else 22.sp,
                        color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusPill(if (supplier.active) "Active" else "Not active", if (supplier.active) Kit.Green else Kit.Grey)
                }
                ContactLine(supplier)
                if (supplier.address.isNotBlank()) AddressLine(supplier.address, choices = directionChoices(supplier))
            }

            supplier.notes.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.EditNote, null, Modifier.size(15.dp), tint = Kit.Muted)
                    Text(it, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted)
                }
            }

            // Orders: what's on its way from them, then their past orders.
            SectionBox {
                SectionTitle(Icons.AutoMirrored.Outlined.ReceiptLong, "Orders", open.size.takeIf { it > 0 }?.let { "$it open" },
                    action = { PrimarySmall("Go to orders", Icons.AutoMirrored.Outlined.ArrowForward) { onGoToOrders(null) } })
                if (open.isEmpty()) {
                    EmptyHint(if (orders.isEmpty()) "No orders yet." else "Nothing on its way from ${supplier.name}.")
                } else {
                    Paged(open, ORDERS_PER_PAGE, key = supplier.id + "open") { order ->
                        OrderLine(order, state, ScreenSize.PHONE, today, showSupplier = false, onOpen = { onGoToOrders(order.id) }, onReceive = null)
                    }
                }
                if (past.isNotEmpty()) {
                    Text(if (showPast) "Hide past orders" else "Past orders (${past.size})", Modifier.clip(RoundedCornerShape(6.dp)).clickable { showPast = !showPast }
                        .padding(horizontal = 4.dp, vertical = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
                    if (showPast) Paged(past, ORDERS_PER_PAGE, key = supplier.id + "past") { order ->
                        OrderLine(order, state, ScreenSize.PHONE, today, showSupplier = false, onOpen = { onGoToOrders(order.id) }, onReceive = null)
                    }
                }
            }

            // What you buy from them and their whole catalog share one box, picked from a small tab bar (user rule).
            SectionBox {
                SupplierTabs(part, mapOf(SupplierPart.ITEMS to links.size, SupplierPart.CATALOG to SupplierBook.catalogOf(supplier.id).size), { part = it },
                    compact = size.isPhone, trailing = if (!edit) null else when (part) {
                        SupplierPart.ITEMS -> ({ SectionButton("Link items", Icons.Outlined.AddLink, compact = size.isPhone) { onDialog(SupplierDialog.LinkItems(supplier.id)) } })
                        SupplierPart.CATALOG -> ({ SectionButton("Load price list", Icons.Outlined.UploadFile, compact = size.isPhone) { onDialog(SupplierDialog.ImportCatalog(supplier.id)) } })
                    })
                when (part) {
                    // A page at a time when there are many, running-low ones first.
                    SupplierPart.ITEMS -> if (links.isEmpty()) {
                        EmptyHint("No items linked yet. Link the stock items you buy from ${supplier.name}, or put products from their catalog in your stock.")
                    } else {
                        val sorted = links.sortedWith(compareByDescending<Pair<SupplierItem, StockItemDto>> { it.second.id in lowIds }.thenBy { it.second.name.lowercase() })
                        Paged(sorted, ITEMS_PER_PAGE, key = supplier.id + "items") { (link, item) ->
                            SuppliedItemLine(item, link, state, size, item.id in lowIds,
                                others = SupplierBook.suppliersOf(item.id).filter { it.id != supplier.id }.map { it.name },
                                onEdit = { onDialog(SupplierDialog.EditLink(supplier.id, item.id)) }.takeIf { edit },
                                onUnlink = { SupplierBook.unlink(supplier.id, item.id) }.takeIf { edit })
                        }
                    }
                    // Everything they sell, from their price list, in your stock or not yet.
                    SupplierPart.CATALOG -> CatalogSection(supplier, state, size, edit, onStock = { product ->
                        // Their product as a new stock item: the item form opens filled in, and saving it links the product.
                        model.newItemPrefilled(catalogItemDraft(product, supplier.name)) { saved -> SupplierBook.linkProduct(product.id, saved.id) }
                    }, onDialog = onDialog)
                }
            }

            SectionBox {
                SectionTitle(Icons.Outlined.Warehouse, "Their places", "${places.size}",
                    action = if (edit) ({ SectionButton("Add place", Icons.Outlined.AddLocationAlt) { onDialog(SupplierDialog.Place(supplier.id, null)) } }) else null)
                if (places.isEmpty()) {
                    EmptyHint("No places yet. Add the warehouse or branch you order from.")
                } else {
                    // Two at a time, side by side and the same size (one under the other on a phone), with pages when there are more.
                    PagedPairs(places, key = supplier.id + "places", stacked = size.isPhone) { place, m ->
                        PlaceCard(place, m, onClick = { onDialog(SupplierDialog.Place(supplier.id, place.id)) }.takeIf { edit })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** The two halves of the "what you buy" box, picked from its tab bar. */
internal enum class SupplierPart(val label: String, val icon: ImageVector) {
    ITEMS("Items you buy", Icons.Outlined.Inventory2),
    CATALOG("Their catalog", Icons.AutoMirrored.Outlined.MenuBook)
}

/**
 * A simple tab bar (icon, name and count; the chosen one green and underlined) that slides sideways when it doesn't fit,
 * with the chosen tab's button at its end.
 */
@Composable
private fun SupplierTabs(
    part: SupplierPart, counts: Map<SupplierPart, Int>, onPart: (SupplierPart) -> Unit, compact: Boolean = false, trailing: (@Composable () -> Unit)? = null
) {
    val scroll = rememberScrollState()
    val spans = remember { mutableStateMapOf<SupplierPart, Pair<Int, Int>>() }
    CenterChosen(scroll, part, spans)
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Kit.Border))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).fadingEdges(scroll).horizontalScroll(scroll)) {
            SupplierPart.entries.forEach { option ->
                val chosen = option == part
                Column(Modifier.width(IntrinsicSize.Max).onGloballyPositioned { spans[option] = it.positionInParent().x.roundToInt() to it.size.width }
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).clickable { onPart(option) }.padding(horizontal = if (compact) 8.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.padding(top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Phones leave the icons out so both tabs fit.
                        if (!compact) Icon(option.icon, null, Modifier.size(17.dp), tint = if (chosen) Kit.Green else Kit.Muted)
                        Text(option.label, fontFamily = Inter(), fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.5.sp,
                            letterSpacing = 0.sp, color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1, softWrap = false)
                        counts[option]?.let { count ->
                            Text("$count", Modifier.clip(RoundedCornerShape(50)).background(if (chosen) Kit.GreenSoft else Kit.Tint).padding(horizontal = 7.dp, vertical = 1.dp),
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (chosen) Kit.Green else Kit.Muted)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (chosen) Kit.Green else Color.Transparent))
                }
            }
        }
        trailing?.let { Box(Modifier.padding(start = 8.dp, bottom = 4.dp)) { it() } }
        }
    }
}

private const val ITEMS_PER_PAGE = 6
private const val PRODUCTS_PER_PAGE = 6

private enum class CatalogShow(val label: String) { ALL("All"), NOT_LINKED("Not in your stock"), LINKED("In your stock") }

/** A supplier's catalog: search, All / Not in your stock / In your stock, and their products a page at a time. */
@Composable
private fun ColumnScope.CatalogSection(
    supplier: Supplier, state: InventoryState, size: ScreenSize, edit: Boolean, onStock: (CatalogProduct) -> Unit, onDialog: (SupplierDialog) -> Unit
) {
    val all = SupplierBook.catalogOf(supplier.id)
    var search by remember(supplier.id) { mutableStateOf("") }
    var show by remember(supplier.id) { mutableStateOf(CatalogShow.ALL) }
    val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val shown = all.filter { p ->
        words.all { it in (p.name + " " + p.code + " " + p.category).lowercase() } && when (show) {
            CatalogShow.ALL -> true
            CatalogShow.NOT_LINKED -> p.linkedItemId == null
            CatalogShow.LINKED -> p.linkedItemId != null
        }
    }.sortedWith(compareBy<CatalogProduct> { it.linkedItemId != null }.thenBy { it.name.lowercase() })
    if (all.isEmpty()) {
        EmptyHint("No price list yet. Load theirs (paste it from a spreadsheet or pick a CSV file) to see everything you could buy from them.")
        return
    }
    if (all.size > PRODUCTS_PER_PAGE) {
        SearchField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = "Find a product or code", height = if (size.isPhone) PhoneControlHeight else 40.dp)
    }
    FilterPills(CatalogShow.entries.map { choice ->
        choice to "${choice.label} ${all.count { when (choice) { CatalogShow.ALL -> true; CatalogShow.NOT_LINKED -> it.linkedItemId == null; CatalogShow.LINKED -> it.linkedItemId != null } }}"
    }, show, { show = it }, Modifier.fillMaxWidth())
    if (shown.isEmpty()) EmptyHint("Nothing matches.")
    else Paged(shown, PRODUCTS_PER_PAGE, key = supplier.id + "catalog" + show + search) { product -> CatalogLine(product, state, size, edit, onDialog, onStock) }
}

/** One product from their list: name, their code, pack and price; linked to your item, or a button to put it in your stock. */
@Composable
private fun CatalogLine(product: CatalogProduct, state: InventoryState, size: ScreenSize, edit: Boolean, onDialog: (SupplierDialog) -> Unit, onStock: (CatalogProduct) -> Unit) {
    val item = state.item(product.linkedItemId)
    StripCard(if (item != null) Kit.Green else Kit.Border, minHeight = 58.dp) {
        if (item != null) StockItemThumb(item, 38.dp)
        else Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(Kit.Tint), Alignment.Center) {
            Icon(Icons.Outlined.Sell, null, Modifier.size(18.dp), tint = Kit.Muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(product.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(product.code.ifBlank { null }, product.pack.ifBlank { null } ?: product.unit.ifBlank { null }, product.category.ifBlank { null })
                .joinToString(" · ").ifBlank { "No code" }, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item != null) Text("In your stock as ${item.name}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Green,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(product.priceCents?.let { moneyText(it, null) } ?: "–", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
            product.unit.takeIf { it.isNotBlank() }?.let { Text("per $it", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1) }
        }
        if (edit && item == null) {
            if (size.isPhone) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Kit.Green).clickable { onStock(product) }, Alignment.Center) {
                    Icon(Icons.Outlined.MoveToInbox, "Put in your stock", Modifier.size(18.dp), tint = Color.White)
                }
            } else PrimarySmall("Put in your stock", Icons.Outlined.MoveToInbox) { onStock(product) }
        }
        if (edit) RowActionsMenu(listOfNotNull(
            RowAction("Put in your stock", Icons.Outlined.MoveToInbox) { onStock(product) }.takeIf { item == null },
            RowAction(if (item == null) "Link to one of your items" else "Link to another item", Icons.Outlined.AddLink) { onDialog(SupplierDialog.LinkProduct(product.id)) },
            RowAction("Remove from their list", Icons.Outlined.Delete, danger = true) { SupplierBook.removeProduct(product.id) }
        ))
    }
}
private const val ORDERS_PER_PAGE = 4

/** One part of a supplier's page in its own soft box, set apart from the next. */
@Composable
private fun SectionBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).border(1.dp, Kit.RowBorder, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content
    )
}

/** A list shown [perPage] at a time, with "1–6 of 23" and previous / next under it when there's more than one page. */
@Composable
private fun <T> Paged(list: List<T>, perPage: Int, key: String, row: @Composable (T) -> Unit) {
    val pages = (list.size + perPage - 1) / perPage
    var page by remember(key) { mutableIntStateOf(0) }
    val shown = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.drop(shown * perPage).take(perPage).forEach { row(it) }
        PagerRow(shown, perPage, list.size, pages) { page = it }
    }
}

/** Two at a time as equal cards side by side ([stacked]: one under the other), with pages when there are more than two. */
@Composable
private fun <T> PagedPairs(list: List<T>, key: String, stacked: Boolean, card: @Composable (T, Modifier) -> Unit) {
    val perPage = 2
    val pages = (list.size + perPage - 1) / perPage
    var page by remember(key) { mutableIntStateOf(0) }
    val shown = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
    val pair = list.drop(shown * perPage).take(perPage)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stacked) {
            pair.forEach { card(it, Modifier.fillMaxWidth()) }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { card(it, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size < perPage) Spacer(Modifier.weight(1f))
            }
        }
        PagerRow(shown, perPage, list.size, pages) { page = it }
    }
}

/** "1–6 of 23" with ‹ Page 1 of 4 ›, shown only when there's more than one page. */
@Composable
private fun PagerRow(shown: Int, perPage: Int, total: Int, pages: Int, onPage: (Int) -> Unit) {
    if (pages <= 1) return
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${shown * perPage + 1}–${minOf((shown + 1) * perPage, total)} of $total", Modifier.weight(1f), fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
        PageArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous page", shown > 0) { onPage(shown - 1) }
        Text("Page ${shown + 1} of $pages", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
        PageArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next page", shown < pages - 1) { onPage(shown + 1) }
    }
}

@Composable
private fun PageArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp), shape = RoundedCornerShape(8.dp), color = Color.White,
        border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(18.dp), tint = if (enabled) Kit.Ink else Kit.Faint) }
    }
}

@Composable
private fun ContactLine(supplier: Supplier) {
    val parts = listOfNotNull(
        supplier.contactName.takeIf { it.isNotBlank() }?.let { Icons.Outlined.Person to it },
        supplier.phone.takeIf { it.isNotBlank() }?.let { Icons.Outlined.Call to it },
        supplier.email.takeIf { it.isNotBlank() }?.let { Icons.Outlined.Mail to it }
    )
    if (parts.isEmpty()) {
        Text("No contact yet", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, Modifier.size(14.dp), tint = Kit.Muted)
                Text(text, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String, count: String?, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = Kit.Ink)
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
        count?.let { Text(it, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Muted) }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun SectionButton(text: String, icon: ImageVector, compact: Boolean = false, onClick: () -> Unit) {
    // Compact (phones): just the icon, where the text wouldn't fit beside the tabs.
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).background(Color.White)
            .clickable(onClick = onClick).padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, text.takeIf { compact }, Modifier.size(16.dp), tint = Kit.Ink)
        if (!compact) Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
    }
}

/** The supplier page's one green button: starting an order. */
@Composable
private fun PrimarySmall(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Kit.Green).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = Color.White)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp)).padding(14.dp),
        fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted)
}

/** One item you buy from the supplier: its picture, their code and price, and what's in stock. */
@Composable
private fun SuppliedItemLine(
    item: StockItemDto, link: SupplierItem, state: InventoryState, size: ScreenSize, low: Boolean, others: List<String>,
    onEdit: (() -> Unit)?, onUnlink: (() -> Unit)?
) {
    val unit = unitShort(item.baseUnit)
    val onHand = state.levels.filter { it.inventoryItemId == item.id }.sumOf { it.onHandQuantity.asDouble() }
    StripCard(if (low) Kit.Danger else itemTypeColor(item.itemType), minHeight = 60.dp, onClick = onEdit) {
        StockItemThumb(item, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(
                // A phone has no stock column, so the amount in stock leads the line.
                if (size.isPhone) "${trimmed(onHand)} $unit in stock${if (low) " · low" else ""}" else null,
                link.code.takeIf { it.isNotBlank() }?.let { "their code $it" } ?: item.kindLabel(long = true).takeIf { !size.isPhone },
                others.takeIf { it.isNotEmpty() }?.let { "also from ${it.joinToString(", ")}" }).joinToString(" · "),
                fontFamily = Inter(), fontSize = 11.sp, color = if (size.isPhone && low) Kit.Danger else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!size.isPhone) {
            Column(Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${trimmed(onHand)} $unit", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (low) Kit.Danger else Kit.Ink, maxLines = 1)
                Text(if (low) "in stock · low" else "in stock", fontFamily = Inter(), fontSize = 11.sp, color = if (low) Kit.Danger else Kit.Muted, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(link.priceCents?.let { moneyText(it, null) } ?: "–", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
            Text("per $unit", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        }
        RowActionsMenu(listOfNotNull(
            onEdit?.let { RowAction("Change code or price", Icons.Outlined.Edit, onClick = it) },
            onUnlink?.let { RowAction("Unlink from this supplier", Icons.Outlined.LinkOff, danger = true, onClick = it) }
        ))
    }
}

@Composable
private fun PlaceCard(place: SupplierPlace, modifier: Modifier, onClick: (() -> Unit)?) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.RowBorder), shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Kit.GreenSoft), Alignment.Center) {
                Icon(Icons.Outlined.Warehouse, null, Modifier.size(20.dp), tint = Kit.Green)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(place.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // The same lines on every card (a dash when something is missing), so cards side by side look alike.
                if (place.address.isNotBlank()) AddressLine(place.address)
                else Text("No address", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint, maxLines = 1)
                Text(place.phone.ifBlank { "No phone" }, fontFamily = Inter(), fontSize = 11.sp, color = if (place.phone.isBlank()) Kit.Faint else Kit.Muted, maxLines = 1)
                run {
                    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        (1..7).forEach { day ->
                            val on = day in place.deliveryDays
                            Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) Kit.Green else Kit.Tint), Alignment.Center) {
                                Text(WeekLetters[day - 1], fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 9.sp, color = if (on) Color.White else Kit.Faint)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val WeekLetters = listOf("M", "T", "W", "T", "F", "S", "S")

// ---- Orders ----

@Composable
private fun OrdersPanel(
    state: InventoryState, size: ScreenSize, today: LocalDate, show: OrderShow, onShow: (OrderShow) -> Unit,
    supplierId: String?, onSupplier: (String?) -> Unit, cards: List<@Composable (Modifier) -> Unit>?, onOpen: (String) -> Unit, onReceive: (String) -> Unit, onNew: () -> Unit,
    highlightId: String? = null, modifier: Modifier
) {
    var search by rememberSaveable { mutableStateOf("") }
    val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val all = SupplierBook.orders.toList()
    fun matches(order: PurchaseOrder, choice: OrderShow) = orderShowOf(order) == choice
    val shown = all.filter { order ->
        val text = (order.number + " " + SupplierBook.supplier(order.supplierId)?.name.orEmpty() + " " +
            order.lines.joinToString(" ") { state.item(it.itemId)?.name.orEmpty() }).lowercase()
        matches(order, show) && (supplierId == null || order.supplierId == supplierId) && words.all { it in text }
    }.sortedWith(compareBy<PurchaseOrder> { !it.status.open }.thenBy { it.expectedOn ?: LocalDate(9999, 1, 1) }.thenByDescending { it.orderedOn })
    val title = when (show) {
        OrderShow.OPEN -> "Open orders"; OrderShow.IN_PROGRESS -> "Orders in progress"; OrderShow.RECEIVED -> "Confirmed orders"
        OrderShow.CANCELLED -> "Cancelled orders"
    }
    // Open / In progress / Confirmed / Cancelled is a visible switch (with how many); only the supplier filter sits in the button.
    val showTabs: @Composable (Modifier, Dp) -> Unit = { m, height ->
        OverviewTabs(OrderShow.entries.map { "${it.label} ${all.count { order -> matches(order, it) }}" }, "${show.label} ${all.count { matches(it, show) }}",
            { label -> OrderShow.entries.firstOrNull { label.startsWith(it.label + " ") }?.let(onShow) }, m, height = height)
    }
    val suppliers = SupplierBook.suppliers.filter { s -> all.any { it.supplierId == s.id } }
    val supplierFilter: @Composable (Dp) -> Unit = { height ->
        if (suppliers.size > 1) PhoneOptionsButton(Icons.Outlined.Tune, "Filter by supplier", if (supplierId != null) 1 else 0, height) { close ->
            OptionGroup("Supplier")
            OptionLine("All suppliers", supplierId == null, { onSupplier(null); close() }, icon = Icons.Outlined.Storefront)
            suppliers.forEach { s -> OptionLine(s.name, supplierId == s.id, { onSupplier(s.id); close() }, icon = Icons.Outlined.Storefront, count = all.count { it.supplierId == s.id }) }
        }
    }
    // A phone gets more room between the kinds, the search and the list (user, 2026-10-30).
    OverviewPanel(Icons.AutoMirrored.Outlined.ReceiptLong, title, modifier, count = shown.size, bottomPadding = 0.dp, flat = size.isPhone,
        gap = if (size.isPhone) 14.dp else 8.dp) {
        if (size.isPhone) {
            // A phone: the four kinds as sliding pills (the chosen one slides to the middle).
            FilterPills(OrderShow.entries.map { it to "${it.label} ${all.count { order -> matches(order, it) }}" }, show, onShow, Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(search, { search = it }, Modifier.weight(1f), placeholder = "Find an order, supplier or item", height = PhoneControlHeight)
                supplierFilter(PhoneControlHeight)
                // Phones have no edge tab: New order is the green square, like Add supplier.
                if (state.canEdit) GreenSquare(Icons.Outlined.AddShoppingCart, "New order", onNew)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Only the Open / In progress / Confirmed / Cancelled switch on the left; search and the filter sit against the
                // right edge (user rule). New order is the green tab on the window's right edge.
                showTabs(Modifier.width(if (size.isDesktop) 440.dp else 380.dp), PageControlHeight)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    SearchField(search, { search = it }, Modifier.widthIn(max = 320.dp).fillMaxWidth(), placeholder = "Find an order, supplier or item",
                        height = PageControlHeight)
                }
                supplierFilter(PageControlHeight)
            }
        }
        // Came from a supplier's page: say whose orders these are, with the way back to all of them.
        SupplierBook.supplier(supplierId)?.let { chosen -> SupplierFilterNote(chosen, compact = size.isPhone, onClear = { onSupplier(null) }) }
        if (size.isDesktop && shown.isNotEmpty()) OrderListHeader()
        val listState = rememberLazyListState()
        // Scroll to the order just placed.
        LaunchedEffect(highlightId, shown.size) {
            val index = shown.indexOfFirst { it.id == highlightId }
            if (index >= 0) listState.animateScrollToItem(index + if (cards != null) 1 else 0)
        }
        ScrollList(Modifier.fillMaxWidth().weight(1f), listState, contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (size.isPhone) 12.dp else 8.dp)) {
            cards?.let { item(key = "cards") { Box(Modifier.padding(bottom = 4.dp)) { SummaryCardRowColumn(size, it) } } }
            if (shown.isEmpty()) item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), Alignment.Center) {
                    OverviewEmpty(
                        if (all.isEmpty()) "No orders yet" else "Nothing here",
                        if (all.isEmpty()) "Order from a supplier's place into one of your stock places. When it arrives, add it to stock."
                        else "Try another filter or word.",
                        Icons.AutoMirrored.Outlined.ReceiptLong, image = Res.drawable.supplier_orders,
                        action = if (all.isEmpty() && state.canEdit) ({ KitButton("New order", onNew, icon = Icons.Outlined.AddShoppingCart) }) else null
                    )
                }
            }
            items(shown, key = { it.id }) { order ->
                OrderLine(order, state, size, today, showSupplier = true, highlight = order.id == highlightId, onOpen = { onOpen(order.id) },
                    onReceive = { onReceive(order.id) }.takeIf { state.canEdit && order.status.open })
            }
        }
    }
}

/** "You're seeing the orders from <supplier>" over the Orders list while it shows one supplier, with Show all orders. */
@Composable
private fun SupplierFilterNote(supplier: Supplier, compact: Boolean, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.GreenSoft)
            .border(1.dp, Kit.Green.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(2.dp)) { SupplierPicture(if (compact) 26.dp else 30.dp, supplier.photo) }
        Text(buildAnnotatedString {
            append("You're seeing the orders from ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(supplier.name) }
        }, Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).clickable(onClick = onClear)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Outlined.Close, null, Modifier.size(14.dp), tint = Kit.Ink)
            Text(if (compact) "Show all" else "Show all orders", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp,
                color = Kit.Ink, maxLines = 1)
        }
    }
}

/** Which of the Orders page's four kinds an order is under. */
private fun orderShowOf(order: PurchaseOrder): OrderShow = when {
    order.status == PurchaseStatus.ORDERED -> OrderShow.OPEN
    order.status.inProgress -> OrderShow.IN_PROGRESS
    order.status == PurchaseStatus.RECEIVED -> OrderShow.RECEIVED
    else -> OrderShow.CANCELLED
}

@Composable
private fun SummaryCardRowColumn(size: ScreenSize, cards: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SummaryCardRow(size, cards) }
}

/** An order's colour: blue on its way, amber being received and counted, green in stock, grey cancelled, red when late. */
internal fun orderColor(order: PurchaseOrder, today: LocalDate): Color = when {
    order.late(today) -> Kit.Danger
    order.status == PurchaseStatus.ORDERED -> Kit.Blue
    order.status.inProgress -> Kit.Amber
    order.status == PurchaseStatus.RECEIVED -> Kit.Green
    else -> Kit.Grey
}

/** The next step's button on an order line: what to do with it now. */
private fun nextStep(order: PurchaseOrder): Pair<String, ImageVector>? = when (order.status) {
    PurchaseStatus.ORDERED -> "Receive order" to Icons.Outlined.MoveToInbox
    PurchaseStatus.PARTLY_RECEIVED -> "Receive delivery" to Icons.Outlined.MoveToInbox
    PurchaseStatus.ARRIVED -> "Count it" to Icons.Outlined.Checklist
    PurchaseStatus.COUNTED -> "Confirm" to Icons.Outlined.Inventory2
    else -> null
}

/** Four small bars that fill as the order goes through its steps. */
@Composable
private fun StepBars(order: PurchaseOrder, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(PurchaseSteps.size) { index ->
            Box(Modifier.size(width = 18.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(if (index < order.status.step) color else Kit.Border))
        }
    }
}

internal fun orderStatusText(order: PurchaseOrder, today: LocalDate): String = if (order.late(today)) "Late" else order.status.label

// Column widths shared by the orders header and lines (desktop).
private const val ColOrder = 1.3f
private const val ColSupplier = 1.9f
private const val ColInto = 1.4f
private const val ColExpected = 1.1f
private const val ColTotal = 1f
private val ColStatus = 150.dp

@Composable
private fun OrderListHeader() {
    val label: @Composable (String, Modifier) -> Unit = { text, m ->
        Text(text, m, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp, color = Kit.Muted, maxLines = 1)
    }
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp + ScrollbarGap), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        label("ORDER", Modifier.weight(ColOrder))
        label("SUPPLIER", Modifier.weight(ColSupplier))
        label("INTO YOUR STOCK", Modifier.weight(ColInto))
        label("EXPECTED", Modifier.weight(ColExpected))
        label("TOTAL", Modifier.weight(ColTotal))
        label("STATUS", Modifier.width(ColStatus))
        Spacer(Modifier.width(32.dp + 160.dp))
    }
}

/** One order: number and date, supplier (and their place), the stock place it goes into, when it's due, the total and status. */
@Composable
internal fun OrderLine(order: PurchaseOrder, state: InventoryState, size: ScreenSize, today: LocalDate, showSupplier: Boolean, onOpen: () -> Unit,
                       onReceive: (() -> Unit)?, highlight: Boolean = false, stackAction: Boolean = false) {
    val supplier = SupplierBook.supplier(order.supplierId)
    val from = SupplierBook.place(order.placeId)
    // Each item has its own stock place: one name when they all go to the same place, else how many places.
    val intoPlaces = order.placeIds.mapNotNull { state.location(it) }
    val into = intoPlaces.singleOrNull()
    val intoText = when {
        intoPlaces.size > 1 -> "${intoPlaces.size} stock places"
        into != null -> into.name
        else -> null
    }
    val color = orderColor(order, today)
    // The order just placed stands out (green frame and a "New" tag).
    StripCard(color, selected = highlight, minHeight = 64.dp, onClick = onOpen) {
        if (!size.isDesktop && stackAction) {
            // A phone's supplier page: the order on one row, its next step's button under it, so nothing is cut off.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconSquare(Icons.AutoMirrored.Outlined.ReceiptLong, color)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(order.number, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                            SmallTag(orderStatusText(order, today), color)
                        }
                        Text(listOfNotNull(count(order.lines.size, "item"), intoText?.let { "into $it" },
                            order.expectedOn?.takeIf { order.status == PurchaseStatus.ORDERED }?.let { "due ${dayLabel(it, today)}" }).joinToString(" · "),
                            fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(moneyText(order.totalCents, null), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1)
                }
                val next = nextStep(order)
                if (onReceive != null && next != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).clickable(onClick = onReceive)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        Icon(next.second, null, Modifier.size(15.dp), tint = Kit.Ink)
                        Text(next.first, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
                    }
                }
            }
            return@StripCard
        }
        if (!size.isDesktop) {
            if (showSupplier) SupplierPicture(40.dp) else IconSquare(Icons.AutoMirrored.Outlined.ReceiptLong, color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (showSupplier) supplier?.name ?: "Supplier" else order.number, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SmallTag(orderStatusText(order, today), color)
                    if (highlight) SmallTag("New", Kit.Green)
                }
                Text(listOfNotNull(if (showSupplier) order.number else null, count(order.lines.size, "item"),
                    intoText?.let { "into $it" }, order.expectedOn?.takeIf { order.status.open }?.let { "due ${dayLabel(it, today)}" }).joinToString(" · "),
                    fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(moneyText(order.totalCents, null), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1)
            // On a supplier's page an open order shows its next step.
            val next = nextStep(order)
            if (onReceive != null && !showSupplier && next != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).clickable(onClick = onReceive)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(next.second, null, Modifier.size(15.dp), tint = Kit.Ink)
                    Text(next.first, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
                }
            }
            return@StripCard
        }
        Column(Modifier.weight(ColOrder), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(order.number, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1)
                if (highlight) SmallTag("New", Kit.Green)
            }
            Text("ordered ${dayLabel(order.orderedOn, today)}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        }
        Row(Modifier.weight(ColSupplier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SupplierPicture(36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(supplier?.name ?: "Supplier", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(from?.let { "from ${it.name}" } ?: count(order.lines.size, "item"), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.weight(ColInto), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (into != null) PlaceIconImage(into, Modifier.size(20.dp))
            else Icon(Icons.Outlined.MoveToInbox, null, Modifier.size(16.dp), tint = Kit.Faint)
            Text(intoText ?: "Not chosen", fontFamily = Inter(), fontSize = 13.sp, color = if (intoText == null) Kit.Faint else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(ColExpected), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(when {
                order.status == PurchaseStatus.RECEIVED -> order.receivedOn?.let { dayLabel(it, today) } ?: "–"
                else -> order.expectedOn?.let { dayLabel(it, today) } ?: "No date"
            }.replaceFirstChar { it.uppercase() }, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (order.late(today)) Kit.Danger else Kit.Ink, maxLines = 1)
            Text(when {
                order.status == PurchaseStatus.RECEIVED -> "arrived"
                order.status == PurchaseStatus.PARTLY_RECEIVED -> "part in stock, rest to come"
                order.status == PurchaseStatus.ARRIVED -> "arrived, to count"
                order.status == PurchaseStatus.COUNTED -> "counted, to confirm"
                order.status == PurchaseStatus.CANCELLED -> "cancelled"
                else -> count(order.lines.size, "item")
            }, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        }
        Text(moneyText(order.totalCents, null), Modifier.weight(ColTotal), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink, maxLines = 1)
        Column(Modifier.width(ColStatus), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill(orderStatusText(order, today), color)
            if (order.status != PurchaseStatus.CANCELLED) Box(Modifier.padding(start = 4.dp)) { StepBars(order, color) }
        }
        Box(Modifier.width(160.dp), Alignment.CenterEnd) {
            val next = nextStep(order)
            if (onReceive != null && next != null) KitButton(next.first, onReceive, icon = next.second, style = ButtonStyle.SECONDARY)
        }
        RowActionsMenu(listOfNotNull(
            RowAction("Open order", Icons.AutoMirrored.Outlined.ReceiptLong, onClick = onOpen),
            onReceive?.let { action -> nextStep(order)?.let { RowAction(it.first, it.second, onClick = action) } },
            if (order.status.open && state.canEdit) RowAction("Cancel order", Icons.Outlined.Cancel, danger = true) { SupplierBook.cancel(order.id) } else null
        ))
    }
}

@Composable
private fun IconSquare(icon: ImageVector, color: Color) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)), Alignment.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = color)
    }
}

internal fun count(n: Int, word: String) = "$n ${if (n == 1) word else word + "s"}"

private val MonthShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val DayShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "Today", "Tomorrow", "Yesterday", or "Thu 30 Oct". */
internal fun dayLabel(date: LocalDate, today: LocalDate): String {
    val days = today.daysUntilDate(date)
    return when (days) {
        0 -> "today"
        1 -> "tomorrow"
        -1 -> "yesterday"
        else -> "${DayShort[date.dayOfWeek.ordinal]} ${date.day} ${MonthShort[date.month.ordinal]}"
    }
}

private fun LocalDate.daysUntilDate(other: LocalDate): Int = (other.toEpochDays() - toEpochDays()).toInt()
