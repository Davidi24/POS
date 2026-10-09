package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.INVENTORY_ITEM_TYPES
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.InfoBox
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitSwitch
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.SideColumn
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.settings_orders_kitchen

@Composable
internal fun ItemsTab(state: InventoryState, model: InventoryScreenModel, size: ScreenSize, modifier: Modifier) {
    var type by remember { mutableStateOf<String?>(null) }
    var missingSaleOnly by remember { mutableStateOf(false) }
    val branch = state.myBranchId
    val missingSale = { item: StockItemDto -> branch != null && item.active && item.trackInventory && state.saleSource(branch, item.id) == null }
    val shown = state.visibleItems.filter { (type == null || it.itemType == type) && (!missingSaleOnly || missingSale(it)) }
    val list: @Composable (Modifier) -> Unit = { m ->
        OverviewPanel(Icons.AutoMirrored.Outlined.ViewList, "Stock items", m, count = shown.size) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(state.search, model::search, Modifier.weight(1f), placeholder = "Search name, code, barcode or supplier", height = PageControlHeight)
                Box(Modifier.width(14.dp))
                Text("Show switched off", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                KitSwitch(state.showInactive, model::showInactive, compact = true)
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty(if (state.items.isEmpty()) "No stock items yet" else "Nothing matches",
                        if (state.items.isEmpty()) "Add the ingredients, drinks and supplies you want to count." else "Try another word or type.",
                        Icons.Outlined.Category, image = Res.drawable.settings_orders_kitchen)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
                    items(shown, key = { it.id }) { item -> ItemCard(item, state, model, compact = !size.isDesktop) }
                }
            }
        }
    }
    if (size.isDesktop) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            list(Modifier.weight(1f).fillMaxHeight())
            SideColumn(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                OverviewPanel(Icons.Outlined.Category, "By type", Modifier.fillMaxWidth()) {
                    val active = state.items.filter { state.showInactive || it.active }
                    FilterRow(Icons.Outlined.Category, "All types", active.size, Kit.Green, type == null) { type = null }
                    INVENTORY_ITEM_TYPES.forEach { code ->
                        FilterRow(itemTypeIcon(code), itemTypeLabel(code), active.count { it.itemType == code }, itemTypeColor(code), type == code) {
                            type = if (type == code) null else code
                        }
                    }
                }
                val missing = state.items.count(missingSale)
                OverviewPanel(Icons.Outlined.PointOfSale, "Sold from", Modifier.fillMaxWidth()) {
                    Text("When a dish is served, its recipe's stock is taken from the place set here. Items without one can stop the kitchen from finishing those dishes.",
                        Modifier.fillMaxWidth(), fontFamily = Inter(), fontSize = 11.sp, lineHeight = 16.sp, color = Kit.Muted)
                    FilterRow(Icons.Outlined.WarningAmber, "No place set", missing, if (missing > 0) Kit.Amber else Kit.Grey, missingSaleOnly) {
                        missingSaleOnly = !missingSaleOnly
                    }
                }
            }
        }
    } else list(modifier)
}

@Composable
private fun ItemCard(item: StockItemDto, state: InventoryState, model: InventoryScreenModel, compact: Boolean) {
    val branch = state.myBranchId
    val source = branch?.let { state.saleSource(it, item.id) }
    var choosingSource by remember { mutableStateOf(false) }
    val actions = if (!state.canEdit) emptyList() else buildList {
        add(RowAction("Edit", Icons.Outlined.Edit) { model.editItem(item.id) })
        if (item.active && item.trackInventory) {
            add(RowAction("Receive stock", Icons.Outlined.LocalShipping) { model.startMove(StockMoveKind.RECEIVE, item.id) })
            if (branch != null) add(RowAction("Choose where sales take it from", Icons.Outlined.PointOfSale) { choosingSource = true })
        }
        if (item.active) add(RowAction("Switch off", Icons.Outlined.ToggleOff, danger = true) { model.askDeactivateItem(item.id) })
    }
    StripCard(if (!item.active) Kit.Grey else itemTypeColor(item.itemType), minHeight = 68.dp, faded = !item.active,
        onClick = if (state.canEdit) ({ model.editItem(item.id) }) else null) {
        IconTile(itemTypeIcon(item.itemType), itemTypeColor(item.itemType), 38.dp)
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(itemTypeLabel(item.itemType), item.code?.takeIf { it.isNotBlank() }, item.barcode?.takeIf { it.isNotBlank() }?.let { "barcode $it" })
                .joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!compact) {
            CardDivider()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${money(item.costPerUnit, null)} / ${unitShort(item.baseUnit)}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                Text(item.supplierName?.takeIf { it.isNotBlank() } ?: "No supplier", fontFamily = Inter(), fontSize = 11.sp,
                    color = if (item.supplierName.isNullOrBlank()) Kit.Faint else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CardDivider()
            Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Reorder at ${item.reorderPoint?.let(::plainNumber) ?: "–"}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
                Text("Full at ${item.parLevel?.let(::plainNumber) ?: "–"}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            }
            Box(Modifier.weight(1f)) {
                when {
                    !item.trackInventory -> InfoBox(Icons.Outlined.Sell, "Not counted")
                    source != null -> InfoBox(Icons.Outlined.PointOfSale, "Sold from ${source.locationName ?: "a place"}", tone = Kit.Green, highlighted = true)
                    item.active -> InfoBox(Icons.Outlined.WarningAmber, "No sale place", tone = Kit.Amber, highlighted = true)
                }
            }
        }
        if (!item.active) StatusPill("Off", Kit.Grey)
        RowActionsMenu(actions)
    }
    if (choosingSource && branch != null) SaleSourceDialog(item, state, model, branch) { choosingSource = false }
}
