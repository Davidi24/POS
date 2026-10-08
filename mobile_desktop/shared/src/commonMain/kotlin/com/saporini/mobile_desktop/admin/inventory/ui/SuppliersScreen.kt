package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.InventoryTab
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.InitialsAvatar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.ToolbarPrimaryButton
import com.saporini.mobile_desktop.core.files.csvRow
import com.saporini.mobile_desktop.core.files.safeFileName
import com.saporini.mobile_desktop.core.files.saveTextFile
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import kotlinx.coroutines.launch
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.notification_order
import mobile_desktop.shared.generated.resources.settings_notifications
import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import mobile_desktop.shared.generated.resources.settings_payments
import org.koin.compose.koinInject

/** Key of the "no supplier yet" group in the list. */
private const val NO_SUPPLIER = "\u0000none"

/** One supplier on screen: its items and, for the ones running low, how much to order to fill up again. */
internal data class SupplierView(
    val key: String,
    val name: String,
    val items: List<StockItemDto>,
    val lines: List<OrderLine>
) {
    val lowCount: Int get() = lines.size
    val orderCents: Long get() = lines.sumOf { it.cents ?: 0 }
}

/** An item to order: [quantity] in the item's unit to bring it back to its full level (or reorder point). */
internal data class OrderLine(val item: StockItemDto, val onHand: Double, val quantity: Double?, val cents: Long?)

internal fun supplierViews(state: InventoryState): List<SupplierView> {
    val lowByItem = state.lowStock.groupBy { it.inventoryItemId }
    fun lines(items: List<StockItemDto>) = items.mapNotNull { item ->
        val low = lowByItem[item.id] ?: return@mapNotNull null
        val onHand = low.sumOf { it.onHandQuantity.asDouble() }
        // Fill each low place up to its full level (else the item's), or at least to the reorder point.
        val need = low.sumOf { level ->
            val target = (level.parQuantity ?: item.parLevel ?: level.reorderQuantity ?: item.reorderPoint).asDouble()
            (target - level.onHandQuantity.asDouble()).coerceAtLeast(0.0)
        }.takeIf { it > 0 }
        OrderLine(item, onHand, need, need?.let { kotlin.math.round(it * item.costPerUnit.asDouble() * 100).toLong() })
    }
    val named = state.suppliers.map { SupplierView(it.name.lowercase(), it.name, it.items, lines(it.items)) }
    val orphans = state.items.filter { it.active && it.trackInventory && it.supplierName.isNullOrBlank() }.sortedBy { it.name.lowercase() }
    return named + listOfNotNull(orphans.takeIf { it.isNotEmpty() }?.let { SupplierView(NO_SUPPLIER, "No supplier yet", it, lines(it)) })
}

/** Admin Hub → Suppliers: who the restaurant buys from (the supplier written on its stock items), and what to order. */
@Composable
internal fun SuppliersScreen(modifier: Modifier = Modifier) {
    val model = koinInject<InventoryScreenModel>()
    // Choose the tab before the model starts, so it loads once.
    remember(model) { model.tab(InventoryTab.SUPPLIERS); true }
    BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    SuppliersContent(state, model, modifier)
}

@Composable
internal fun SuppliersContent(state: InventoryState, model: InventoryScreenModel, modifier: Modifier = Modifier, initialSupplier: String? = null) {
    val all = supplierViews(state)
    var search by rememberSaveable { mutableStateOf("") }
    var selectedKey by rememberSaveable { mutableStateOf(initialSupplier) }
    val shown = all.filter { view ->
        val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val text = (view.name + " " + view.items.joinToString(" ") { it.name + " " + (it.supplierSku ?: "") }).lowercase()
        words.all { it in text }
    }
    val selected = all.firstOrNull { it.key == selectedKey }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PageHeader("Suppliers", "Who you buy from, what they bring and what to order") {
                RefreshButton(state.loading, model::refresh)
                if (state.canEdit) ToolbarPrimaryButton("Receive a delivery", Icons.Outlined.LocalShipping, !state.saving) { model.startMove(StockMoveKind.RECEIVE) }
            }
            ScreenMessages(state.error.takeIf { state.itemDraft == null && state.moveDraft == null }, state.notice, model::clearMessages)
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to suppliers", "Your role needs “View settings” to see suppliers.")
                state.items.isEmpty() && state.loading -> OverviewPageSkeleton(size)
                state.items.isEmpty() && state.stale && state.error != null -> PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                else -> {
                    SummaryCardRow(size, supplierCards(state, all) { selectedKey = it })
                    val body = Modifier.fillMaxWidth().weight(1f)
                    val list: @Composable (Modifier) -> Unit = { m ->
                        SupplierList(shown, all.isEmpty(), search, { search = it }, selected?.key, { selectedKey = it }, m)
                    }
                    val detail: @Composable (Modifier) -> Unit = { m ->
                        if (selected == null) {
                            Surface(m, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    OverviewEmpty("Choose a supplier", "See what they bring you, what's running low and an order list to send them.",
                                        Icons.Outlined.LocalShipping, image = Res.drawable.notification_order)
                                }
                            }
                        } else SupplierDetail(selected, state, model, size, onBack = { selectedKey = null }, modifier = m)
                    }
                    when {
                        size.isPhone -> if (selected == null) list(body) else detail(body)
                        else -> Row(body, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            list(Modifier.width(if (size.isDesktop) 400.dp else 300.dp).fillMaxHeight())
                            detail(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
    InventoryDialogs(state, model)
}

private fun supplierCards(state: InventoryState, all: List<SupplierView>, onSelect: (String) -> Unit): List<@Composable (Modifier) -> Unit> {
    val suppliers = all.filter { it.key != NO_SUPPLIER }
    val tracked = state.items.count { it.active && it.trackInventory }
    val covered = state.items.count { it.active && it.trackInventory && !it.supplierName.isNullOrBlank() }
    val low = all.sumOf { it.lowCount }
    val toOrder = all.sumOf { it.orderCents }
    val orphan = all.firstOrNull { it.key == NO_SUPPLIER }
    val busiest = suppliers.maxByOrNull { it.lowCount }?.takeIf { it.lowCount > 0 }
    return listOf(
        { m -> OverviewStatCard("Suppliers", "${suppliers.size}", suppliers.maxByOrNull { it.items.size }?.let { "Most items: ${it.name}" } ?: "Add a supplier on an item",
            Kit.Green, m, image = Res.drawable.notification_order) },
        { m -> OverviewStatCard("Items with a supplier", "$covered", "of $tracked counted items", Kit.Blue, m, image = Res.drawable.settings_orders_kitchen,
            suffix = "/ $tracked", progress = if (tracked > 0) covered.toFloat() / tracked else 0f,
            onClick = orphan?.let { { onSelect(NO_SUPPLIER) } }) },
        { m -> OverviewStatCard("To order", "$low", busiest?.let { "Most from ${it.name}" } ?: "Nothing is running low",
            if (low > 0) Kit.Danger else Kit.Grey, m, image = Res.drawable.settings_notifications, suffix = if (low == 1) "item" else "items",
            valueColor = if (low > 0) Kit.Danger else Kit.Ink, onClick = busiest?.let { { onSelect(it.key) } }) },
        { m -> OverviewStatCard("Order value", if (toOrder > 0) moneyText(toOrder, null) else "–", "at today's costs, to fill up", Kit.Amber, m,
            image = Res.drawable.settings_payments) }
    )
}

@Composable
private fun SupplierList(
    shown: List<SupplierView>,
    noneAtAll: Boolean,
    search: String,
    onSearch: (String) -> Unit,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier
) {
    OverviewPanel(Icons.Outlined.LocalShipping, "Suppliers", modifier, count = shown.count { it.key != NO_SUPPLIER }) {
        SearchField(search, onSearch, Modifier.fillMaxWidth(), placeholder = "Search supplier, item or code", height = PageControlHeight)
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty(if (noneAtAll) "No suppliers yet" else "Nothing matches",
                    if (noneAtAll) "Write who you buy from on each stock item (Inventory → Items) and they show here." else "Try another word.",
                    Icons.Outlined.LocalShipping, image = Res.drawable.notification_order)
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
                items(shown, key = { it.key }) { view -> SupplierCard(view, view.key == selectedKey) { onSelect(view.key) } }
            }
        }
    }
}

@Composable
private fun SupplierCard(view: SupplierView, selected: Boolean, onClick: () -> Unit) {
    val orphan = view.key == NO_SUPPLIER
    StripCard(when { orphan -> Kit.Amber; view.lowCount > 0 -> Kit.Danger; else -> Kit.Green }, selected = selected, minHeight = 64.dp, chevron = true,
        onClick = onClick) {
        if (orphan) IconTile(Icons.Outlined.WarningAmber, Kit.Amber, 36.dp) else InitialsAvatar(view.name, color = Kit.Green, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(view.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(view.items.take(3).joinToString(", ") { it.name } + if (view.items.size > 3) " +${view.items.size - 3}" else "",
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${view.items.size} ${if (view.items.size == 1) "item" else "items"}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink)
            if (view.lowCount > 0) Text("${view.lowCount} low", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Danger)
            else Text("all fine", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        }
    }
}

@Composable
private fun SupplierDetail(view: SupplierView, state: InventoryState, model: InventoryScreenModel, size: ScreenSize, onBack: () -> Unit, modifier: Modifier) {
    val orphan = view.key == NO_SUPPLIER
    val scope = rememberCoroutineScope()
    var savedTo by remember(view.key) { mutableStateOf<String?>(null) }
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (size.isPhone) 14.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (size.isPhone) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Kit.Tint).clickable(onClick = onBack), Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to suppliers", Modifier.size(20.dp), tint = Kit.Ink)
                    }
                }
                if (orphan) IconTile(Icons.Outlined.WarningAmber, Kit.Amber, 54.dp) else InitialsAvatar(view.name, color = Kit.Green, size = 54.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(view.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (orphan) "Counted items with no supplier written on them" else
                        "${view.items.size} ${if (view.items.size == 1) "item" else "items"} · ${view.items.map { itemTypeLabel(it.itemType) }.distinct().joinToString(", ")}",
                        fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (view.lowCount > 0) StatusPill("${view.lowCount} to order", Kit.Danger) else StatusPill("All stocked", Kit.Green)
            }

            if (orphan) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Amber.copy(alpha = 0.1f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = Kit.Amber)
                    Text("Write a supplier on these items so they show up in order lists. Tap an item to edit it.", fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
                }
            }

            // What to order
            if (view.lines.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, Modifier.size(18.dp), tint = Kit.Danger)
                    Spacer(Modifier.width(8.dp))
                    Text("To order", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                    Spacer(Modifier.width(8.dp))
                    Text(if (view.orderCents > 0) "about ${moneyText(view.orderCents, null)}" else "", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp, color = Kit.Muted)
                    Spacer(Modifier.weight(1f))
                    if (!orphan) KitButton("Download order list", {
                        scope.launch {
                            savedTo = runCatching {
                                saveTextFile("order-${safeFileName(view.name)}.csv", "text/csv", orderCsv(view))
                            }.getOrElse { "Couldn't save the file: ${it.message ?: "unknown problem"}" }
                        }
                    }, icon = Icons.Outlined.Download, style = ButtonStyle.SECONDARY)
                }
                savedTo?.let { Text(if (it.startsWith("Couldn't")) it else "Saved to $it", fontFamily = Inter(), fontSize = 11.sp,
                    color = if (it.startsWith("Couldn't")) Kit.Danger else Kit.Green) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    view.lines.forEach { line -> OrderLineCard(line, state.canEdit, compact = size.isPhone) { model.startMove(StockMoveKind.RECEIVE, line.item.id) } }
                }
            }

            Caption(if (orphan) "Items" else "Everything from ${view.name}")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                view.items.forEach { item -> SuppliedItemCard(item, view.lines.any { it.item.id == item.id }, state, model, compact = size.isPhone) }
            }
        }
    }
}

@Composable
private fun OrderLineCard(line: OrderLine, canEdit: Boolean, compact: Boolean, onReceive: () -> Unit) {
    val unit = unitShort(line.item.baseUnit)
    val empty = line.onHand <= 0
    StripCard(if (empty) Kit.Danger else Kit.Amber, minHeight = 60.dp) {
        IconTile(itemTypeIcon(line.item.itemType), itemTypeColor(line.item.itemType), 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(line.item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(if (empty) "Out of stock" else "${trimmed(line.onHand)} $unit left", line.item.supplierSku?.takeIf { it.isNotBlank() }?.let { "code $it" })
                .joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = if (empty) Kit.Danger else Kit.Muted, maxLines = 1)
        }
        if (!compact) CardDivider()
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.quantity?.let { "${trimmed(it)} $unit" } ?: "Set a full level", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = if (line.quantity == null) Kit.Muted else Kit.Ink)
            Text(line.cents?.let { moneyText(it, null) } ?: "to fill up", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        }
        if (canEdit) KitButton("Received", onReceive, icon = Icons.Outlined.LocalShipping, style = ButtonStyle.SECONDARY)
    }
}

@Composable
private fun SuppliedItemCard(item: StockItemDto, low: Boolean, state: InventoryState, model: InventoryScreenModel, compact: Boolean) {
    val actions = if (!state.canEdit) emptyList() else buildList {
        add(RowAction("Edit item", Icons.Outlined.Edit) { model.editItem(item.id) })
        if (item.trackInventory) add(RowAction("Receive stock", Icons.Outlined.LocalShipping) { model.startMove(StockMoveKind.RECEIVE, item.id) })
    }
    StripCard(if (low) Kit.Danger else itemTypeColor(item.itemType), minHeight = 56.dp, onClick = if (state.canEdit) ({ model.editItem(item.id) }) else null) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(itemTypeLabel(item.itemType), item.supplierSku?.takeIf { it.isNotBlank() }?.let { "code $it" },
                if (!item.trackInventory) "not counted" else null).joinToString(" · "),
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!compact) {
            Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Reorder at ${item.reorderPoint?.let(::plainNumber) ?: "–"}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
                Text("Full at ${item.parLevel?.let(::plainNumber) ?: "–"}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            }
        }
        Text("${money(item.costPerUnit, null)} / ${unitShort(item.baseUnit)}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
        RowActionsMenu(actions)
    }
}

/** The order list as CSV, for sending to the supplier. */
internal fun orderCsv(view: SupplierView): String = buildString {
    appendLine("Item,Supplier code,Quantity,Unit,Cost per unit,Estimated cost")
    view.lines.forEach { line ->
        val cells = listOf(line.item.name, line.item.supplierSku.orEmpty(), line.quantity?.let(::trimmed).orEmpty(), unitShort(line.item.baseUnit),
            plainNumber(line.item.costPerUnit), line.cents?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" }.orEmpty())
        appendLine(csvRow(cells))
    }
}
