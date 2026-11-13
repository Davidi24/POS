package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.StockLevelDto
import com.saporini.mobile_desktop.admin.inventory.StockLocationDto
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.core.components.ScrollbarGap
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.InfoBox
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.ScrollList
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.money
import kotlin.math.roundToLong
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_places
import kotlin.time.Clock
import com.saporini.mobile_desktop.admin.inventory.SupplierBook
import androidx.compose.material.icons.outlined.LocalShipping

// Inventory → Stock: what is where. Every item in every place with how much there is (and how that compares to its
// levels), the places themselves, scanning, and recording each kind of change. The item catalogue itself is on Items.

/** Out of stock, a shade darker than running low (both red, as the user asked). */
internal val DeepRed = Color(0xFF7E2A23)

internal enum class StockHealth(val label: String, val color: Color) {
    EMPTY("Out of stock", DeepRed), LOW("Running low", Kit.Danger), OK("Fine", Kit.Green), NO_LEVEL("No levels set", Kit.Grey)
}

internal fun StockLevelDto.health(): StockHealth {
    val onHand = onHandQuantity.asDouble()
    return when {
        onHand <= 0.0 -> StockHealth.EMPTY
        lowStock -> StockHealth.LOW
        reorderQuantity == null && parQuantity == null -> StockHealth.NO_LEVEL
        else -> StockHealth.OK
    }
}

/** The kinds of stock change, in the order people reach for them, with the words on their buttons. */
internal val ChangeKinds = listOf(StockMoveKind.RECEIVE, StockMoveKind.TRANSFER, StockMoveKind.WASTE, StockMoveKind.RETURN, StockMoveKind.ADJUST)

internal fun changeTitle(kind: StockMoveKind): String = when (kind) {
    StockMoveKind.RECEIVE -> "Receive"
    StockMoveKind.WASTE -> "Waste"
    StockMoveKind.TRANSFER -> "Move"
    StockMoveKind.RETURN -> "Return"
    StockMoveKind.ADJUST -> "Correct"
}

internal fun changeDetail(kind: StockMoveKind): String = when (kind) {
    StockMoveKind.RECEIVE -> "a delivery"
    StockMoveKind.WASTE -> "with a reason"
    StockMoveKind.TRANSFER -> "between places"
    StockMoveKind.RETURN -> "to a supplier"
    StockMoveKind.ADJUST -> "a wrong number"
}

/** What the list shows, by how the stock stands. "Running low" is the model's filter, so the Overview can open it. */
private enum class StockStatus(val label: String, val icon: ImageVector, val color: Color) {
    ALL("Everything", Icons.Outlined.Inventory2, Kit.Green),
    LOW("Running low", Icons.AutoMirrored.Outlined.TrendingDown, Kit.Danger),
    EMPTY("Out of stock", Icons.Outlined.RemoveShoppingCart, DeepRed),
    OK("Fine", Icons.Outlined.CheckCircle, Kit.Green),
    NO_LEVEL("No levels set", Icons.Outlined.TrackChanges, Kit.Grey);

    fun matches(level: StockLevelDto): Boolean = when (this) {
        ALL -> true
        LOW -> level.lowStock
        EMPTY -> level.health() == StockHealth.EMPTY
        OK -> level.health() == StockHealth.OK
        NO_LEVEL -> level.health() == StockHealth.NO_LEVEL
    }
}

// By price = what one unit of the item costs (user, 2026-10-30); items without a cost go last either way.
private enum class StockSort(val label: String) {
    URGENT("Most urgent"), NAME("Name A–Z"), MOST("Most in stock"), PRICEY("Most expensive"), CHEAP("Least expensive"), RECENT("Recently changed")
}

@Composable
internal fun StockTab(state: InventoryState, model: InventoryScreenModel, size: ScreenSize, modifier: Modifier) {
    var picked by rememberSaveable { mutableStateOf(StockStatus.ALL) }
    val status = if (state.lowOnly) StockStatus.LOW else if (picked == StockStatus.LOW) StockStatus.ALL else picked
    val choose = { option: StockStatus -> picked = option; model.lowOnly(option == StockStatus.LOW) }
    var sort by rememberSaveable { mutableStateOf(StockSort.URGENT) }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf<String?>(null) }
    // Lines picked for one change together ("place|item"); the Stock action button only shows while some are picked.
    var selected by remember { mutableStateOf(setOf<String>()) }
    // Lines that vanish (another place picked, stock gone) drop out of the selection.
    val present = state.levels.map { lineKey(it) }.toSet()
    LaunchedEffect(present) { if (!present.containsAll(selected)) selected = selected.intersect(present) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!state.canEdit) InfoBox(Icons.Outlined.Storefront, "Your role can look at stock but not change it.")
        val list: @Composable (Modifier) -> Unit = { m ->
            StockPanel(state, model, size, status, choose, sort, { sort = it },
                selection = Selection(selected, onToggle = { key -> selected = if (key in selected) selected - key else selected + key },
                    onSet = { selected = it }), onScan = { scanning = true }, modifier = m)
        }
        if (size.isPhone) list(Modifier.fillMaxWidth().weight(1f))
        else Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ScrollColumn(Modifier.width(if (size.isDesktop) 300.dp else 260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PlacesPanel(state, model)
                // Counted over the place picked above (everything when none).
                val here = state.levels.filter { state.levelLocationId == null || it.locationId == state.levelLocationId }
                OverviewPanel(Icons.Outlined.TrackChanges, "Status", Modifier.fillMaxWidth()) {
                    StockStatus.entries.forEach { option ->
                        val lines = here.filter(option::matches)
                        // What those lines are worth now, at cost (user, 2026-10-30).
                        FilterRow(option.icon, option.label, lines.size, option.color, status == option,
                            detail = "worth ${moneyText(lines.sumOf { lineWorthCents(state, it) }, null)}") {
                            choose(if (status == option && option != StockStatus.ALL) StockStatus.ALL else option)
                        }
                    }
                }
            }
            list(Modifier.weight(1f).fillMaxHeight())
        }
    }

    if (scanning) ScanBarcodeDialog("Scan an item", "See its stock and record a change for it.", onCode = { code ->
        scanning = false
        scanned = code
    }, onDismiss = { scanning = false })
    scanned?.let { code ->
        ScanResultDialog(code, state, model, onScanAgain = { scanned = null; scanning = true }, onClose = { scanned = null })
    }
}

/** What is picked for a change together, and how to change it. */
private class Selection(val keys: Set<String>, val onToggle: (String) -> Unit, val onSet: (Set<String>) -> Unit)

private fun lineKey(level: StockLevelDto) = "${level.locationId}|${level.inventoryItemId}"

/**
 * The places: pick one to see what's kept there (pick it again for all); add, edit or count them from here. Slim lines in
 * their own scrolling box, so a branch with many places doesn't push the status filter far down.
 */
@Composable
private fun PlacesPanel(state: InventoryState, model: InventoryScreenModel) {
    val places = state.locations.filter { state.showInactive || it.active }.sortedWith(compareBy<StockLocationDto> { !it.active }.thenBy { it.name.lowercase() })
    val stocked = state.levels.filter { it.onHandQuantity.asDouble() > 0 }
    OverviewPanel(Icons.Outlined.Storefront, "Places", Modifier.fillMaxWidth(), count = places.count { it.active },
        // Only the places here (user, 2026-11-11): adding, changing and deleting them is in Manage places.
        action = if (state.canEdit) ({ SmallLink("Manage places", Icons.Outlined.Tune) { model.managePlaces(true) } }) else null) {
        // As wide as the lines in the scrolling box under it (which keep room for its scrollbar).
        Box(Modifier.padding(end = ScrollbarGap)) {
            PlaceLine(Icons.Outlined.Storefront, Kit.Green, "All places", stocked.map { it.inventoryItemId }.distinct().size, low = 0,
                selected = state.levelLocationId == null, actions = emptyList(), iconCode = "legacy_store") { model.levelsAt(null) }
        }
        if (places.isNotEmpty()) {
            // About six lines show; the rest scroll inside the box.
            ScrollColumn(Modifier.fillMaxWidth().heightIn(max = PlaceLineHeight * 6 + 2.dp * 5), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                places.forEach { place ->
                    val here = state.levels.filter { it.locationId == place.id }
                    PlaceLine(placeTypeIcon(place.locationType), if (place.active) placeTypeColor(place.locationType) else Kit.Grey,
                        if (place.active) place.name else "${place.name} · off", here.count { it.onHandQuantity.asDouble() > 0 }, low = here.count { it.lowStock },
                        selected = state.levelLocationId == place.id, actions = emptyList(),
                        onClick = if (place.active) ({ model.levelsAt(if (state.levelLocationId == place.id) null else place.id) }) else null,
                        locationType = place.locationType, iconCode = place.iconCode, imageUrl = place.imageUrl)
                }
            }
        } else {
            Text("Add where you keep stock: the kitchen, the bar, the walk-in fridge, the dry store.", fontFamily = Inter(), fontSize = 11.sp,
                lineHeight = 16.sp, color = Kit.Muted)
        }
    }
}

private val PlaceLineHeight = 36.dp

/** One slim place line: its kind's icon, the name, how many are running low, how many items are there, and its ⋮ menu. */
@Composable
private fun PlaceLine(icon: ImageVector, color: Color, name: String, count: Int, low: Int, selected: Boolean, actions: List<RowAction>,
                      locationType: String? = null, iconCode: String? = null, imageUrl: String? = null, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(Modifier.fillMaxWidth().height(PlaceLineHeight).clip(RoundedCornerShape(8.dp))
        .background(when { selected -> color.copy(alpha = 0.1f); hovered -> Kit.Canvas; else -> Color.White })
        .border(if (selected) 1.5.dp else 1.dp, if (selected) color else Kit.RowBorder, RoundedCornerShape(8.dp))
        .then(if (onClick != null) Modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else Modifier)
        .padding(start = 8.dp, end = if (actions.isEmpty()) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)), Alignment.Center) {
            when {
                !imageUrl.isNullOrBlank() -> com.saporini.mobile_desktop.core.ui.RemoteImage(imageUrl, null, Modifier.matchParentSize()) {
                    PlaceIconImage(locationType, iconCode, Modifier.size(19.dp), color)
                }
                locationType != null || iconCode != null -> PlaceIconImage(locationType, iconCode, Modifier.size(19.dp), color)
                else -> Icon(icon, null, Modifier.size(14.dp), tint = color)
            }
        }
        Text(name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 12.5.sp,
            color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (low > 0) {
            Text("$low low", Modifier.clip(RoundedCornerShape(50)).background(Kit.Danger.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 1.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Danger, maxLines = 1)
        }
        Text("$count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = if (count > 0) Kit.Ink else Kit.Faint)
        if (actions.isNotEmpty()) RowActionsMenu(actions)
    }
}

@Composable
private fun StockPanel(
    state: InventoryState,
    model: InventoryScreenModel,
    size: ScreenSize,
    status: StockStatus,
    onStatus: (StockStatus) -> Unit,
    sort: StockSort,
    onSort: (StockSort) -> Unit,
    selection: Selection,
    onScan: () -> Unit,
    modifier: Modifier
) {
    val words = state.search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val levels = state.visibleLevels.filter { level -> status.matches(level) && words.all { it in level.inventoryItemName.orEmpty().lowercase() } }
        .let { list ->
            when (sort) {
                StockSort.URGENT -> list.sortedWith(compareBy<StockLevelDto> { it.health().ordinal }.thenBy { it.inventoryItemName?.lowercase() })
                StockSort.NAME -> list.sortedBy { it.inventoryItemName?.lowercase() }
                StockSort.MOST -> list.sortedByDescending { it.onHandQuantity.asDouble() }
                StockSort.PRICEY, StockSort.CHEAP -> {
                    val (priced, unpriced) = list.partition { unitCost(state, it) > 0.0 }
                    (if (sort == StockSort.PRICEY) priced.sortedByDescending { unitCost(state, it) } else priced.sortedBy { unitCost(state, it) }) +
                        unpriced.sortedBy { it.inventoryItemName?.lowercase() }
                }
                StockSort.RECENT -> list.sortedByDescending { it.lastMovementAt.orEmpty() }
            }
        }
    val place = state.location(state.levelLocationId)
    // Which place a branch's sales take an item from is set per item here, next to its stock.
    var salePlaceFor by remember { mutableStateOf<String?>(null) }
    val branch = state.myBranchId
    val actions = { level: StockLevelDto -> lineActions(level, state, model, branch) { salePlaceFor = it } }
    // What the lines shown are worth now, at cost (user, 2026-10-30: "I need to watch the current worth of that stock").
    val worth = levels.sumOf { lineWorthCents(state, it) }
    OverviewPanel(Icons.AutoMirrored.Outlined.ViewList, "Stock on hand", modifier, count = levels.size,
        titleExtra = listOfNotNull(place?.let { "at ${it.name}" }, "worth ${moneyText(worth, null)}").joinToString(" · "),
        bottomPadding = 0.dp, flat = size.isPhone) {
        if (size.isPhone) PhoneStockFilters(state, model, place, status, onStatus, sort, onSort, shown = levels.size, onScan = onScan)
        else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SearchField(state.search, model::search, Modifier.weight(1f, fill = false).widthIn(max = 380.dp).padding(end = 10.dp),
                placeholder = "Find an item", height = PageControlHeight)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KitButton("Scan an item", onScan, icon = Icons.Outlined.QrCodeScanner)
                HeaderDropdown(sort.label, Icons.AutoMirrored.Outlined.Sort, Modifier.width(184.dp), StockSort.entries.map { it.label },
                    { label -> StockSort.entries.firstOrNull { it.label == label }?.let(onSort) })
            }
        }
        if (selection.keys.isNotEmpty() && state.canEdit) {
            SelectionBar(selection.keys.size, compact = size.isPhone, allShown = levels.all { lineKey(it) in selection.keys },
                onAll = { selection.onSet(selection.keys + levels.map(::lineKey)) },
                onNone = { selection.onSet(selection.keys - levels.map(::lineKey).toSet()) }, onClear = { selection.onSet(emptySet()) }) { kind ->
                val lines = state.levels.filter { lineKey(it) in selection.keys }.map { it.inventoryItemId to it.locationId }
                model.startBulk(kind, lines)
            }
        }
        if (levels.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                when {
                    state.levels.isEmpty() -> OverviewEmpty("No stock yet", "Receive a delivery to start: every item then shows here with how much is in each place.",
                        Icons.Outlined.Inventory2, image = Res.drawable.inventory_places,
                        action = if (state.canEdit) ({
                            KitButton("Receive stock", { model.startMove(StockMoveKind.RECEIVE) }, icon = moveKindLook(StockMoveKind.RECEIVE).icon)
                        }) else null)
                    status == StockStatus.LOW && words.isEmpty() -> OverviewEmpty("Nothing is running low", "Every line here is above its reorder point.", Icons.Outlined.CheckCircle)
                    else -> OverviewEmpty("Nothing matches", "Try another word, place or status.", Icons.Outlined.Inventory2)
                }
            }
            return@OverviewPanel
        }
        // A table: the header names each column, every line reads left to right the same way.
        val showPlace = place == null && size.isDesktop
        if (!size.isPhone) StockListHeader(showPlace, picking = state.canEdit)
        ScrollList(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(levels, key = { _, it -> "${it.locationId}-${it.inventoryItemId}" }) { index, level ->
                // A calm re-arrange when the filter, place or sort changes (user, 2026-10-30): lines that stay glide to their
                // new place, lines that go fade out and new ones fade in, so you see the table change.
                Box(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = tween(320, easing = FastOutSlowInEasing), fadeOutSpec = tween(160))) {
                    StockLine(level, state, size, number = index + 1, showPlace = showPlace, placeInSubtitle = place == null && !size.isDesktop,
                        showPrice = sort == StockSort.PRICEY || sort == StockSort.CHEAP, actions = actions(level),
                        onSetLevels = { model.editItem(level.inventoryItemId) }.takeIf { state.canEdit },
                        picked = lineKey(level) in selection.keys,
                        onPick = { selection.onToggle(lineKey(level)) }.takeIf { state.canEdit })
                }
            }
        }
    }
    val saleItem = state.item(salePlaceFor)
    if (saleItem != null && branch != null) SaleSourceDialog(saleItem, state, model, branch) { salePlaceFor = null }
}

/** What a stock line is worth now: how much is there × what one unit cost, in cents. */
private fun lineWorthCents(state: InventoryState, level: StockLevelDto): Long =
    (level.onHandQuantity.asDouble().coerceAtLeast(0.0) * unitCost(state, level) * 100).roundToLong()

private fun unitCost(state: InventoryState, level: StockLevelDto): Double = state.item(level.inventoryItemId)?.costPerUnit?.asDouble() ?: 0.0

/** A stock line's menu: each kind of change for this item in this place, and where sales take it from. */
private fun lineActions(level: StockLevelDto, state: InventoryState, model: InventoryScreenModel, branch: String?, onSalePlace: (String) -> Unit): List<RowAction> {
    // A link to the item's supplier (user, 2026-10-30), for everyone who can see stock: it opens Suppliers on them.
    val supplier = SupplierBook.suppliersOf(level.inventoryItemId).firstOrNull()
    val toSupplier = supplier?.let { RowAction("Go to ${it.name}", Icons.Outlined.LocalShipping) { SupplierBook.openRequest = it.id } }
    if (!state.canEdit) return listOfNotNull(toSupplier)
    val item = state.item(level.inventoryItemId)
    return ChangeKinds.map { kind ->
        val look = moveKindLook(kind)
        RowAction(if (kind == StockMoveKind.TRANSFER) "Move to another place" else look.label, look.icon, danger = kind == StockMoveKind.WASTE) {
            model.startMove(kind, level.inventoryItemId, level.locationId)
        }
    } + listOfNotNull(
        if (branch != null && item != null && item.active && item.trackInventory) RowAction("Change sale place", Icons.Outlined.PointOfSale) { onSalePlace(item.id) } else null,
        toSupplier
    )
}

// Column widths shared by the header and the lines.
private const val ColItem = 2.6f
private const val ColPlace = 1.5f
private const val ColAmount = 1.1f
private const val ColWorth = 1f
// Wide enough for "999".
private val LineNumberWidth = 24.dp
private const val ColLevel = 2f
private val ColStatus = 128.dp

@Composable
private fun StockListHeader(showPlace: Boolean, picking: Boolean) {
    val label: @Composable (String, Modifier) -> Unit = { text, m ->
        Text(text, m, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp, color = Kit.Muted, maxLines = 1)
    }
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp + ScrollbarGap), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // Over the name: past the tick (when picking) and the picture.
        label("ITEM", Modifier.weight(ColItem).padding(start = LineNumberWidth + 10.dp + (if (picking) 32.dp else 0.dp) + 50.dp))
        if (showPlace) label("PLACE", Modifier.weight(ColPlace))
        label("IN STOCK", Modifier.weight(ColAmount))
        label("WORTH", Modifier.weight(ColWorth))
        Row(Modifier.weight(ColLevel), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            label("HOW FULL", Modifier)
            Box(Modifier.size(width = 2.dp, height = 10.dp).background(Kit.Ink.copy(alpha = 0.55f)))
            Text("= reorder point", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Faint, maxLines = 1)
        }
        label("STATUS", Modifier.width(ColStatus))
        Spacer(Modifier.width(32.dp))
    }
}

/**
 * One item in one place: picture and name, the place, how much there is, how full the place is for it (bar towards
 * "full", the mark at the reorder point, both written under it) and its status. Clicking picks it for a Stock action.
 */
@Composable
private fun StockLine(level: StockLevelDto, state: InventoryState, size: ScreenSize, number: Int, showPlace: Boolean, placeInSubtitle: Boolean,
                      actions: List<RowAction>, picked: Boolean, onPick: (() -> Unit)?, showPrice: Boolean = false,
                      onSetLevels: (() -> Unit)? = null) {
    val item = state.item(level.inventoryItemId)
    val health = level.health()
    val unit = unitShort(item?.baseUnit)
    val place = state.location(level.locationId)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val amount: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.Bottom) {
            Text(plainNumber(level.onHandQuantity), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                color = if (health == StockHealth.EMPTY) health.color else Kit.Ink, maxLines = 1)
            Text(" $unit", Modifier.padding(bottom = 2.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
        }
    }
    Surface(
        onClick = { onPick?.invoke() }, enabled = onPick != null,
        modifier = Modifier.fillMaxWidth().hoverable(interaction).pointerHoverIcon(PointerIcon.Hand), shape = RoundedCornerShape(12.dp),
        color = when { picked -> Kit.GreenSoft; hovered -> Kit.Canvas; else -> Color.White },
        border = BorderStroke(if (picked) 1.5.dp else 1.dp, if (picked) Kit.Green else Kit.RowBorder), interactionSource = interaction
    ) {
        if (size.isPhone) PhoneStockCard(level, state, health, unit, placeInSubtitle, actions, picked, onPick, showPrice, onSetLevels)
        else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(health.color))
            Row(Modifier.weight(1f).heightIn(min = 62.dp).padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Item
                Row(Modifier.weight(ColItem), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // The tick shows every line can be picked (a tap on the line picks it) for a Stock action.
                    // The line's place in the list, so you know how far down you are ("28" of the 127 in the title; user, 2026-10-30).
                    if (!size.isPhone) Text("$number", Modifier.width(LineNumberWidth), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp, color = Kit.Faint, textAlign = TextAlign.End, maxLines = 1)
                    if (onPick != null) PickTick(picked, small = size.isPhone)
                    val thumb = if (size.isPhone) 36.dp else 40.dp
                    if (item != null) StockItemThumb(item, thumb) else ItemThumb(null, null, thumb)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(level.inventoryItemName ?: item?.name ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (size.isPhone) {
                            // Where it is in grey, how it stands in its own colour.
                            Text(buildAnnotatedString {
                                level.locationName.takeIf { placeInSubtitle }?.let { append(it); append(" · ") }
                                withStyle(SpanStyle(color = if (health == StockHealth.OK) Kit.Muted else health.color, fontWeight = FontWeight.SemiBold)) {
                                    append(health.label)
                                }
                            }, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            // Sorted by price, the line says what one unit costs instead of when it last changed, so the order reads.
                            val last = if (showPrice) item?.let { if (it.costPerUnit.asDouble() > 0.0) "${money(it.costPerUnit, null)} per $unit" else "no cost set" }
                                else level.lastMovementAt?.let { "changed ${agoText(it, Clock.System.now(), RestaurantTime.zone)}" }
                            Text(listOfNotNull(level.locationName.takeIf { placeInSubtitle }, item?.let { it.kindLabel() }, last).joinToString(" · "),
                                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (size.isPhone) {
                    // The amount over its unit, right-aligned, so long units never squeeze the name.
                    Column(Modifier.widthIn(min = 40.dp), horizontalAlignment = Alignment.End) {
                        Text(plainNumber(level.onHandQuantity), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                            color = if (health == StockHealth.EMPTY) health.color else Kit.Ink, maxLines = 1)
                        Text(unit, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                        Text(moneyText(lineWorthCents(state, level), null), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                            color = Kit.Ink, maxLines = 1)
                    }
                } else {
                    // Place
                    if (showPlace) Row(Modifier.weight(ColPlace), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (place != null) PlaceIconImage(place, Modifier.size(20.dp))
                        Text(level.locationName ?: "Place", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    amount(Modifier.weight(ColAmount))
                    // Worth: what this line's stock cost, now (nothing when the item has no cost yet).
                    val lineWorth = lineWorthCents(state, level)
                    Text(if (lineWorth > 0 || (item?.costPerUnit?.asDouble() ?: 0.0) > 0.0) moneyText(lineWorth, null) else "No cost",
                        Modifier.weight(ColWorth), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = if (lineWorth > 0) Kit.Ink else Kit.Faint, maxLines = 1)
                    // How full
                    Column(Modifier.weight(ColLevel), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val levels = listOfNotNull(level.reorderQuantity?.let { "reorder at ${plainNumber(it)}" }, level.parQuantity?.let { "full at ${plainNumber(it)}" })
                        if (levels.isEmpty() && onSetLevels != null) {
                            // People who may change items set the levels right here (the item editor's "Reorder at" / "Full at").
                            SetLevelsButton(onSetLevels)
                        } else if (levels.isEmpty()) {
                            // Without levels there is nothing to measure "full" against, so no bar.
                            Text("No levels yet", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Faint, maxLines = 1)
                            Text("set a reorder point on the item", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            StockGauge(level.onHandQuantity.asDouble(), level.reorderQuantity?.asDouble(), level.parQuantity?.asDouble(), health.color)
                            Text(levels.joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(Modifier.width(ColStatus)) { StatusPill(health.label, health.color) }
                }
                RowActionsMenu(actions)
            }
        }
    }
}

/**
 * A stock line on a phone, as a card (user, 2026-10-30: "a lot of the PC's info is missing" / "not organized as a card"):
 * the item with its place and kind and the menu on top, then a soft box with how much there is, what it's worth and how it
 * stands, the bar towards "full" with its levels (or Set levels), and when it last changed.
 */
@Composable
private fun PhoneStockCard(level: StockLevelDto, state: InventoryState, health: StockHealth, unit: String, placeInSubtitle: Boolean,
                           actions: List<RowAction>, picked: Boolean, onPick: (() -> Unit)?, showPrice: Boolean, onSetLevels: (() -> Unit)?) {
    val item = state.item(level.inventoryItemId)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(health.color))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onPick != null) PickTick(picked, small = true)
                if (item != null) StockItemThumb(item, 40.dp) else ItemThumb(null, null, 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(level.inventoryItemName ?: item?.name ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(level.locationName.takeIf { placeInSubtitle }, item?.kindLabel()).joinToString(" · "), fontFamily = Inter(),
                        fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RowActionsMenu(actions)
            }
            Column(
                Modifier.fillMaxWidth().padding(end = 8.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Canvas)
                    .border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhoneFigure("In stock", Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(plainNumber(level.onHandQuantity), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                color = if (health == StockHealth.EMPTY) health.color else Kit.Ink, maxLines = 1)
                            Text(" $unit", Modifier.padding(bottom = 2.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                        }
                    }
                    PhoneFigure("Worth", Modifier.weight(1f)) {
                        val worth = lineWorthCents(state, level)
                        Text(if (worth > 0 || (item?.costPerUnit?.asDouble() ?: 0.0) > 0.0) moneyText(worth, null) else "No cost", fontFamily = Inter(),
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (worth > 0) Kit.Ink else Kit.Faint, maxLines = 1)
                    }
                    PhoneFigure("Status", Modifier.weight(1.2f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(health.color))
                            Text(health.label, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = health.color, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                val levels = listOfNotNull(level.reorderQuantity?.let { "reorder at ${plainNumber(it)}" }, level.parQuantity?.let { "full at ${plainNumber(it)}" })
                if (levels.isNotEmpty()) StockGauge(level.onHandQuantity.asDouble(), level.reorderQuantity?.asDouble(), level.parQuantity?.asDouble(), health.color)
                // Sorted by price, the price per unit; otherwise when it last changed.
                val last = if (showPrice) item?.let { if (it.costPerUnit.asDouble() > 0.0) "${money(it.costPerUnit, null)} per $unit" else "no cost set" }
                    else level.lastMovementAt?.let { "changed ${agoText(it, Clock.System.now(), RestaurantTime.zone)}" }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        levels.isNotEmpty() -> Text(levels.joinToString(" · "), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        onSetLevels != null -> Box(Modifier.weight(1f)) { SetLevelsButton(onSetLevels) }
                        else -> Text("No levels yet", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                            color = Kit.Faint, maxLines = 1)
                    }
                    last?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1) }
                }
            }
        }
    }
}

/** One figure in a phone stock card: a small grey label over its value. */
@Composable
private fun PhoneFigure(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.5.sp, color = Kit.Muted, maxLines = 1)
        value()
    }
}

/** A small green button in a line's "How full" place when the item has no levels yet: opens the item to set them. */
@Composable
private fun SetLevelsButton(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = Kit.GreenSoft, border = BorderStroke(1.dp, Kit.Green.copy(alpha = 0.35f)),
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Tune, null, Modifier.size(15.dp), tint = Kit.Green)
            Text("Set levels", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
        }
    }
}

/** "Select all" as a check box (user, 2026-10-30): ticked picks every line shown, unticking lets them go again. */
@Composable
private fun SelectAllBox(all: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(if (all) Kit.Green else Color.White)
            .border(1.5.dp, if (all) Kit.Green else Kit.Faint, RoundedCornerShape(4.dp)), Alignment.Center) {
            if (all) Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = Color.White)
        }
        Text("Select all", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
    }
}

/** The round tick that shows a line is picked (an empty ring on hover when it isn't). */
@Composable
private fun PickTick(picked: Boolean, small: Boolean = false) {
    Box(Modifier.size(if (small) 20.dp else 22.dp).clip(CircleShape).background(if (picked) Kit.Green else Color.White)
        .border(1.5.dp, if (picked) Kit.Green else Kit.Faint, CircleShape), Alignment.Center) {
        if (picked) Icon(Icons.Outlined.Check, "Picked", Modifier.size(14.dp), tint = Color.White)
    }
}

/** Shown only while lines are picked: how many, pick all shown, clear, and the Stock action menu for all of them. */
@Composable
private fun SelectionBar(count: Int, compact: Boolean, allShown: Boolean, onAll: () -> Unit, onNone: () -> Unit, onClear: () -> Unit,
                         onAction: (StockMoveKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val actions: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            KitButton(if (compact) "Stock action for ${if (count == 1) "this line" else "these $count"}" else "Stock action", { open = true },
                if (compact) Modifier.fillMaxWidth() else Modifier, icon = Icons.Outlined.Bolt)
            DropdownMenu(open, { open = false }, Modifier.background(Color.White)) {
                ChangeKinds.forEach { kind ->
                    val look = moveKindLook(kind)
                    DropdownMenuItem(text = {
                        Column {
                            Text(changeTitle(kind), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink)
                            Text(changeDetail(kind), fontFamily = Inter(), fontSize = 11.sp, letterSpacing = 0.sp, color = Kit.Muted)
                        }
                    }, leadingIcon = { Icon(look.icon, null, Modifier.size(18.dp), tint = look.color) }, onClick = { open = false; onAction(kind) })
                }
            }
        }
    }
    if (compact) {
        // A phone: what's picked and how to change the pick on one line, the action as a wide button under it.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.GreenSoft).border(1.dp, Kit.Green.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(17.dp), tint = Kit.Green)
                Text("$count picked", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                SelectAllBox(allShown, if (allShown) onNone else onAll)
                SmallLink("Clear", Icons.Outlined.Close, onClear)
            }
            Box(Modifier.padding(end = 6.dp)) { actions(Modifier.fillMaxWidth()) }
        }
        return
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.GreenSoft).border(1.dp, Kit.Green.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = Kit.Green)
        Text("$count picked", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
        SelectAllBox(allShown, if (allShown) onNone else onAll)
        SmallLink("Clear", Icons.Outlined.Close, onClear)
        Spacer(Modifier.weight(1f))
        actions(Modifier)
    }
}

/**
 * A phone's Stock controls: search with a Scan button and sort, then the place and the status as two compact dropdown
 * buttons (the place menu can also add or edit a place).
 */
@Composable
private fun PhoneStockFilters(
    state: InventoryState,
    model: InventoryScreenModel,
    place: StockLocationDto?,
    status: StockStatus,
    onStatus: (StockStatus) -> Unit,
    sort: StockSort,
    onSort: (StockSort) -> Unit,
    shown: Int,
    onScan: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(state.search, model::search, Modifier.weight(1f), placeholder = "Find an item", height = PhoneControlHeight)
        Surface(onClick = onScan, modifier = Modifier.size(PhoneControlHeight), shape = RoundedCornerShape(8.dp), color = Kit.Green) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.QrCodeScanner, "Scan an item", Modifier.size(19.dp), tint = Color.White) }
        }
        PhoneOptionsButton(Icons.AutoMirrored.Outlined.Sort, "Sort", active = 0) { close ->
            OptionGroup("Sort by")
            StockSort.entries.forEach { option -> OptionLine(option.label, sort == option, { onSort(option); close() }) }
        }
    }
    val stocked = state.levels.filter { it.onHandQuantity.asDouble() > 0 }
    // Counted over the place picked (everything when none), like the side panel on bigger screens.
    val here = state.levels.filter { state.levelLocationId == null || it.locationId == state.levelLocationId }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhoneFilterButton(place?.let { placeTypeIcon(it.locationType) } ?: Icons.Outlined.Storefront, place?.name ?: "All places", Modifier.weight(1f),
            active = place != null) { close ->
            OptionGroup("Place")
            OptionLine("All places", place == null, { model.levelsAt(null); close() }, icon = Icons.Outlined.Storefront,
                count = stocked.map { it.inventoryItemId }.distinct().size)
            state.activeLocations.sortedBy { it.name.lowercase() }.forEach { option ->
                OptionLine(option.name, place?.id == option.id, { model.levelsAt(option.id); close() }, icon = placeTypeIcon(option.locationType),
                    color = placeTypeColor(option.locationType), count = stocked.count { it.locationId == option.id })
            }
            if (state.canEdit) {
                OptionGroup("Places")
                OptionLine("Manage places", false, { model.managePlaces(true); close() }, icon = Icons.Outlined.Tune, color = Kit.Ink)
            }
        }
        PhoneFilterButton(status.icon, status.label, Modifier.weight(1f), active = status != StockStatus.ALL, count = shown) { close ->
            OptionGroup("Show")
            StockStatus.entries.forEach { option ->
                OptionLine(option.label, status == option, { onStatus(option); close() }, icon = option.icon, color = option.color, count = here.count(option::matches))
            }
        }
    }
}

@Composable
private fun PlaceChip(place: StockLocationDto, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PlaceIconImage(place, Modifier.size(18.dp))
        Text(place.name, Modifier.widthIn(max = 130.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HealthChip(health: StockHealth, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(health.color))
        Text(health.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Ink, maxLines = 1)
    }
}

/**
 * How full a place is: the bar fills towards the full (par) level, a small mark shows the reorder point. Without a
 * full level the reorder point counts as half way.
 */
@Composable
internal fun StockGauge(onHand: Double, reorder: Double?, par: Double?, color: Color) {
    val top = par?.takeIf { it > 0 } ?: reorder?.takeIf { it > 0 }?.let { it * 2 } ?: maxOf(onHand, 1.0)
    val fill = (onHand / top).coerceIn(0.0, 1.0).toFloat()
    val mark = reorder?.let { (it / top).coerceIn(0.0, 1.0).toFloat() }
    val markColor = Kit.Ink.copy(alpha = 0.55f)
    // Drawn on a canvas: it sits in cards measured by intrinsic height, where BoxWithConstraints can't be used.
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val bar = 6.dp.toPx()
        val top = (size.height - bar) / 2
        val radius = androidx.compose.ui.geometry.CornerRadius(bar / 2)
        drawRoundRect(color.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, bar), radius)
        if (fill > 0f) drawRoundRect(color, androidx.compose.ui.geometry.Offset(0f, top), androidx.compose.ui.geometry.Size(size.width * fill, bar), radius)
        mark?.let { drawRect(markColor, androidx.compose.ui.geometry.Offset(size.width * it - 1.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) }
    }
}

/** What a scan found: the item with its stock per place and a button per kind of change; or the offer to create it. */
@Composable
private fun ScanResultDialog(code: String, state: InventoryState, model: InventoryScreenModel, onScanAgain: () -> Unit, onClose: () -> Unit) {
    val item = state.itemByBarcode(code)
    AppDialog(item?.name ?: "Not found", onClose, subtitle = "Barcode $code", maxWidth = 520.dp, buttons = {
        KitButton("Scan another", onScanAgain, icon = Icons.Outlined.QrCodeScanner, style = ButtonStyle.SECONDARY)
        KitButton("Close", onClose, style = ButtonStyle.SECONDARY)
    }) {
        if (item == null) {
            ScanMissBox(code, state.canEdit, onCreate = { onClose(); model.newItemWithBarcode(code) }, onScanAgain = onScanAgain)
            return@AppDialog
        }
        ScannedItem(item, state)
        if (!item.active || !item.trackInventory) {
            InfoBox(Icons.Outlined.Inventory2, if (!item.active) "This item is switched off." else "This item isn't counted, so it has no stock.")
        } else if (state.canEdit) {
            QuickAdd(code, item, state, model)
            Text("OTHER CHANGES", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.6.sp, color = Kit.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChangeKinds.forEach { kind ->
                    val look = moveKindLook(kind)
                    Surface(onClick = { onClose(); model.startMove(kind, item.id, state.levelLocationId) }, modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(look.icon, null, Modifier.size(20.dp), tint = look.color)
                            Text(changeTitle(kind), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Ink)
                        }
                    }
                }
            }
        }
    }
}

/** The fast path after a scan: how many came in and where, and one button. The dialog stays open for the next scan. */
@Composable
private fun QuickAdd(code: String, item: StockItemDto, state: InventoryState, model: InventoryScreenModel) {
    val levels = state.levelsByItem[item.id].orEmpty()
    var quantity by remember(code) { mutableStateOf("1") }
    // The place picked on the left, else where most of it already is, else the first place.
    var placeId by remember(code) {
        mutableStateOf(state.levelLocationId ?: levels.maxByOrNull { it.onHandQuantity.asDouble() }?.locationId ?: state.activeLocations.firstOrNull()?.id)
    }
    val unit = unitShort(item.baseUnit)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.GreenSoft).border(1.dp, Kit.Green.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Add to stock", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectInput(state.location(placeId), state.activeLocations, { it.name }, { placeId = it.id }, Modifier.weight(1f), label = "Into",
                icon = Icons.Outlined.Storefront, placeholder = "Choose a place")
            AmountField(quantity, unit, signed = false, enabled = !state.saving) { quantity = it }
        }
        KitButton("Add ${quantity.ifBlank { "0" }} $unit", {
            model.quickReceive(item.id, placeId, quantity)
        }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add, loading = state.saving, enabled = placeId != null && quantity.isNotBlank())
    }
}

@Composable
private fun ScannedItem(item: StockItemDto, state: InventoryState) {
    val levels = state.levelsByItem[item.id].orEmpty().sortedByDescending { it.onHandQuantity.asDouble() }
    val unit = unitShort(item.baseUnit)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(item.kindColor().copy(alpha = 0.07f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ItemPictureLarge(item, Modifier.size(72.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.kindLabel(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
            Text("${trimmed(levels.sumOf { it.onHandQuantity.asDouble() })} $unit", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Kit.Ink)
            Text(when (val n = levels.count { it.onHandQuantity.asDouble() > 0 }) { 0 -> "none in stock"; 1 -> "in 1 place"; else -> "in $n places" },
                fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
    }
    levels.forEach { level ->
        val health = level.health()
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(health.color))
            Text(level.locationName ?: "Place", Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
            Text("${plainNumber(level.onHandQuantity)} $unit", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
        }
    }
}
