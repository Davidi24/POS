package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.INVENTORY_ITEM_TYPES
import com.saporini.mobile_desktop.admin.inventory.INVENTORY_LOCATION_TYPES
import com.saporini.mobile_desktop.admin.inventory.INVENTORY_UNITS
import com.saporini.mobile_desktop.admin.inventory.InventoryConfirm
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.ItemField
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_CODE
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_NAME
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_TEXT
import com.saporini.mobile_desktop.admin.inventory.MAX_SUPPLIER_NAME
import com.saporini.mobile_desktop.admin.inventory.MAX_SUPPLIER_SKU
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.StockLocationDto
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.SearchableSelect
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToggleRow
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.quantityText
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter

/** Every dialog the Inventory page can show, driven by the model's state. */
@Composable
internal fun InventoryDialogs(state: InventoryState, model: InventoryScreenModel) {
    state.itemDraft?.let { ItemEditorDialog(state, model) }
    state.locationDraft?.let { PlaceEditorDialog(state, model) }
    state.moveDraft?.let { MoveDialog(state, model) }
    state.openCount?.let { CountDialog(it, state, model) }
    when (val confirm = state.confirm) {
        is InventoryConfirm.DeactivateItem -> ConfirmDialog("Switch off ${state.item(confirm.itemId)?.name ?: "this item"}?",
            "It leaves the lists and can't be received or counted. Its history stays, and you can switch it on again from Items.",
            "Switch off", model::confirm, model::dismissConfirm, danger = true, busy = state.saving)
        is InventoryConfirm.DeactivateLocation -> ConfirmDialog("Switch off ${state.location(confirm.locationId)?.name ?: "this place"}?",
            "Stock can't be moved in or out of it any more. Move what's still there to another place first.",
            "Switch off", model::confirm, model::dismissConfirm, danger = true, busy = state.saving)
        is InventoryConfirm.CountStep -> ConfirmDialog(
            if (confirm.step == "approve") "Approve this count?" else "Cancel this count?",
            if (confirm.step == "approve") "The stock of every counted item becomes what was counted. The differences are recorded as count corrections."
            else "The count is kept as cancelled and changes nothing.",
            if (confirm.step == "approve") "Approve" else "Cancel count", model::confirm, model::dismissConfirm,
            danger = confirm.step == "cancel", busy = state.saving)
        null -> Unit
    }
}

@Composable
private fun ItemEditorDialog(state: InventoryState, model: InventoryScreenModel) {
    val draft = state.itemDraft ?: return
    val problems = state.itemProblems
    AppDialog(
        if (draft.id == null) "New stock item" else "Edit ${draft.name.ifBlank { "item" }}", model::cancelItem,
        subtitle = "What you buy and count: ingredients, drinks, packaging and supplies.", busy = state.saving, maxWidth = 680.dp,
        buttons = {
            KitButton("Cancel", model::cancelItem, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton(if (draft.id == null) "Add item" else "Save", model::saveItem, icon = Icons.Outlined.Check, loading = state.saving)
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        TextInput(draft.name, { v -> model.changeItem { it.copy(name = v) } }, label = "Name", required = true, icon = Icons.Outlined.Inventory2,
            placeholder = "San Marzano tomatoes", problem = problems[ItemField.NAME], maxLength = MAX_STOCK_NAME)
        FieldPair(
            { m -> SelectInput(draft.itemType, INVENTORY_ITEM_TYPES, ::itemTypeLabel, { v -> model.changeItem { it.copy(itemType = v) } }, m,
                label = "Kind", icon = Icons.Outlined.Category) },
            { m -> SelectInput(draft.baseUnit, INVENTORY_UNITS, { "${unitLabel(it)} (${unitShort(it)})" }, { v -> model.changeItem { it.copy(baseUnit = v) } }, m,
                label = "Counted in", icon = Icons.Outlined.Scale, hint = "Stock, costs and recipes use this unit") }
        )
        Caption("Cost and levels")
        FieldPair(
            { m -> TextInput(draft.costText, { v -> model.changeItem { it.copy(costText = v) } }, m, label = "Cost per ${unitShort(draft.baseUnit)}", required = true,
                icon = Icons.Outlined.Euro, placeholder = "2.50", problem = problems[ItemField.COST], keyboardType = KeyboardType.Decimal) },
            { m -> TextInput(draft.code, { v -> model.changeItem { it.copy(code = v) } }, m, label = "Your code", optional = true, icon = Icons.Outlined.Badge,
                problem = problems[ItemField.CODE], maxLength = MAX_STOCK_CODE) }
        )
        FieldPair(
            { m -> TextInput(draft.reorderText, { v -> model.changeItem { it.copy(reorderText = v) } }, m, label = "Reorder at", optional = true,
                suffix = unitShort(draft.baseUnit), problem = problems[ItemField.REORDER], keyboardType = KeyboardType.Decimal,
                hint = "Shows as running low under this") },
            { m -> TextInput(draft.parText, { v -> model.changeItem { it.copy(parText = v) } }, m, label = "Full at", optional = true,
                suffix = unitShort(draft.baseUnit), problem = problems[ItemField.PAR], keyboardType = KeyboardType.Decimal,
                hint = "How much a full shelf holds") }
        )
        Caption("Supplier")
        FieldPair(
            { m -> TextInput(draft.supplierName, { v -> model.changeItem { it.copy(supplierName = v) } }, m, label = "Supplier", optional = true,
                icon = Icons.Outlined.LocalShipping, placeholder = "Green Farm", problem = problems[ItemField.SUPPLIER], maxLength = MAX_SUPPLIER_NAME) },
            { m -> TextInput(draft.supplierSku, { v -> model.changeItem { it.copy(supplierSku = v) } }, m, label = "Supplier's code", optional = true,
                problem = problems[ItemField.SUPPLIER_SKU], maxLength = MAX_SUPPLIER_SKU) }
        )
        TextInput(draft.barcode, { v -> model.changeItem { it.copy(barcode = v.trim()) } }, label = "Barcode", optional = true, icon = Icons.Outlined.QrCode,
            problem = problems[ItemField.BARCODE], maxLength = MAX_STOCK_CODE)
        TextInput(draft.description, { v -> model.changeItem { it.copy(description = v) } }, label = "Description", optional = true, singleLine = false, minLines = 2,
            maxLength = MAX_STOCK_TEXT)
        TextInput(draft.storageNotes, { v -> model.changeItem { it.copy(storageNotes = v) } }, label = "Storage notes", optional = true, singleLine = false,
            minLines = 2, icon = Icons.AutoMirrored.Outlined.Notes, placeholder = "Keep below 4 °C, first in first out…", maxLength = MAX_STOCK_TEXT,
            problem = problems[ItemField.TEXT])
        ToggleRow("Count its stock", draft.trackInventory, { v -> model.changeItem { it.copy(trackInventory = v) } },
            detail = "Switch off for things you don't count, like salt or napkins.")
        if (draft.id != null) ToggleRow("In use", draft.active, { v -> model.changeItem { it.copy(active = v) } },
            detail = "Switched-off items leave the lists but keep their history.")
    }
}

private fun unitLabel(unit: String): String = when (unit) {
    "EACH" -> "Pieces"; "GRAM" -> "Grams"; "KILOGRAM" -> "Kilograms"; "MILLILITER" -> "Millilitres"; "LITER" -> "Litres"; "OUNCE" -> "Ounces"
    "POUND" -> "Pounds"; "CUP" -> "Cups"; "TABLESPOON" -> "Tablespoons"; "TEASPOON" -> "Teaspoons"; "PORTION" -> "Portions"; "CASE" -> "Cases"
    "BOTTLE" -> "Bottles"; "PACK" -> "Packs"; "TRAY" -> "Trays"; else -> unit
}

@Composable
private fun PlaceEditorDialog(state: InventoryState, model: InventoryScreenModel) {
    val draft = state.locationDraft ?: return
    AppDialog(
        if (draft.id == null) "New place" else "Edit ${draft.name.ifBlank { "place" }}", model::cancelLocation,
        subtitle = "Where you keep stock in this branch.", busy = state.saving, maxWidth = 560.dp,
        buttons = {
            KitButton("Cancel", model::cancelLocation, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton(if (draft.id == null) "Add place" else "Save", model::saveLocation, icon = Icons.Outlined.Check, loading = state.saving)
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        TextInput(draft.name, { v -> model.changeLocation { it.copy(name = v) } }, label = "Name", required = true, icon = Icons.Outlined.Storefront,
            placeholder = "Walk-in fridge", maxLength = MAX_STOCK_NAME)
        Text("Kind", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        // Kinds as picture tiles: quicker to pick than a list.
        INVENTORY_LOCATION_TYPES.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { type ->
                    val chosen = draft.locationType == type
                    Surface(
                        onClick = { model.changeLocation { it.copy(locationType = type) } }, modifier = Modifier.weight(1f).height(74.dp),
                        shape = RoundedCornerShape(10.dp), color = if (chosen) Kit.GreenSoft else Color.White,
                        border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) Kit.Green else Kit.Border)
                    ) {
                        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(placeTypeIcon(type), null, Modifier.size(22.dp), tint = if (chosen) Kit.Green else placeTypeColor(type))
                            Text(placeTypeLabel(type), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                                color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1)
                        }
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        TextInput(draft.code, { v -> model.changeLocation { it.copy(code = v) } }, label = "Short code", optional = true, icon = Icons.Outlined.Badge,
            placeholder = "WALKIN", maxLength = MAX_STOCK_CODE)
        TextInput(draft.notes, { v -> model.changeLocation { it.copy(notes = v) } }, label = "Notes", optional = true, singleLine = false, minLines = 2,
            maxLength = MAX_STOCK_TEXT)
        if (draft.id != null) ToggleRow("In use", draft.active, { v -> model.changeLocation { it.copy(active = v) } })
    }
}

/** Receive, waste, move, return or correct stock. */
@Composable
private fun MoveDialog(state: InventoryState, model: InventoryScreenModel) {
    val draft = state.moveDraft ?: return
    val look = moveKindLook(draft.kind)
    val item = state.item(draft.itemId)
    val unit = unitShort(item?.baseUnit)
    val onHereLevel = state.levels.firstOrNull { it.inventoryItemId == draft.itemId && it.locationId == draft.locationId }
    val frozen = state.moveOutcomeUnknown
    AppDialog(
        when (draft.kind) {
            StockMoveKind.RECEIVE -> "Receive stock"
            StockMoveKind.WASTE -> "Record waste"
            StockMoveKind.TRANSFER -> "Move stock"
            StockMoveKind.RETURN -> "Return to supplier"
            StockMoveKind.ADJUST -> "Correct stock"
        },
        model::cancelMove, busy = state.saving, maxWidth = 600.dp,
        buttons = {
            KitButton("Cancel", model::cancelMove, style = ButtonStyle.SECONDARY, enabled = !state.saving && !frozen)
            KitButton(if (frozen) "Retry the same change" else look.label, model::saveMove, icon = look.icon, loading = state.saving,
                style = if (draft.kind == StockMoveKind.WASTE) ButtonStyle.DANGER else ButtonStyle.PRIMARY)
        }
    ) {
        if (frozen) MessageBar("This change may already be saved. Retry it exactly as it is; it won't be counted twice.", MessageKind.WARNING)
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        // The kind of change as big buttons.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StockMoveKind.entries.forEach { kind ->
                val l = moveKindLook(kind)
                val chosen = kind == draft.kind
                Surface(
                    onClick = { if (!frozen) model.changeMove { it.copy(kind = kind, toLocationId = if (kind == StockMoveKind.TRANSFER) it.toLocationId else null) } },
                    enabled = !frozen, modifier = Modifier.weight(1f).height(66.dp), shape = RoundedCornerShape(10.dp),
                    color = if (chosen) l.color.copy(alpha = 0.1f) else Color.White, border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) l.color else Kit.Border)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(l.icon, null, Modifier.size(20.dp), tint = l.color)
                        Text(l.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (chosen) l.color else Kit.Ink)
                    }
                }
            }
        }
        SearchableSelect(item, state.items.filter { it.active && it.trackInventory }, { it.name }, { v -> model.changeMove { it.copy(itemId = v.id) } },
            label = "Item", required = true, icon = Icons.Outlined.Inventory2, detailOf = { "${itemTypeLabel(it.itemType)} · ${unitShort(it.baseUnit)}" },
            placeholder = "Choose an item", enabled = !frozen)
        FieldPair(
            { m -> SelectInput(state.location(draft.locationId), state.activeLocations, { it.name }, { v -> model.changeMove { it.copy(locationId = v.id) } }, m,
                label = if (draft.kind == StockMoveKind.TRANSFER) "From" else "Place", required = true, icon = Icons.Outlined.Storefront,
                placeholder = "Choose a place", enabled = !frozen,
                hint = onHereLevel?.let { "${quantityText(it.onHandQuantity, item?.baseUnit)} there now" }) },
            { m ->
                if (draft.kind == StockMoveKind.TRANSFER) {
                    SelectInput<StockLocationDto?>(state.location(draft.toLocationId), state.activeLocations.filter { it.id != draft.locationId },
                        { it?.name ?: "" }, { v -> model.changeMove { it.copy(toLocationId = v?.id) } }, m, label = "To", required = true,
                        icon = Icons.Outlined.Storefront, placeholder = "Choose a place", enabled = !frozen)
                } else {
                    TextInput(draft.quantityText, { v -> model.changeMove { it.copy(quantityText = v) } }, m,
                        label = if (draft.kind == StockMoveKind.ADJUST) "Change by (+ or -)" else "Quantity", required = true, suffix = unit,
                        keyboardType = KeyboardType.Decimal, placeholder = if (draft.kind == StockMoveKind.ADJUST) "-0.5" else "10", enabled = !frozen)
                }
            }
        )
        if (draft.kind == StockMoveKind.TRANSFER) {
            TextInput(draft.quantityText, { v -> model.changeMove { it.copy(quantityText = v) } }, label = "Quantity", required = true, suffix = unit,
                keyboardType = KeyboardType.Decimal, placeholder = "10", enabled = !frozen)
        }
        if (draft.kind == StockMoveKind.RECEIVE) {
            TextInput(draft.unitCostText, { v -> model.changeMove { it.copy(unitCostText = v) } }, label = "Cost per $unit", optional = true,
                icon = Icons.Outlined.Euro, keyboardType = KeyboardType.Decimal, placeholder = item?.costPerUnit?.let(::plainNumber) ?: "",
                hint = "Leave empty to keep the item's cost", enabled = !frozen)
        }
        TextInput(draft.reason, { v -> model.changeMove { it.copy(reason = v) } }, label = "Reason",
            required = draft.kind == StockMoveKind.WASTE || draft.kind == StockMoveKind.ADJUST,
            optional = draft.kind != StockMoveKind.WASTE && draft.kind != StockMoveKind.ADJUST, singleLine = false, minLines = 2,
            icon = Icons.AutoMirrored.Outlined.Notes, maxLength = MAX_STOCK_TEXT, enabled = !frozen,
            placeholder = when (draft.kind) {
                StockMoveKind.RECEIVE -> "Delivery note 81"
                StockMoveKind.WASTE -> "Dropped, out of date, burnt…"
                StockMoveKind.TRANSFER -> "Prep for dinner"
                StockMoveKind.RETURN -> "Damaged on arrival"
                StockMoveKind.ADJUST -> "Scale was off"
            })
    }
}

/** Where this branch's sales take this item's stock from. */
@Composable
internal fun SaleSourceDialog(item: StockItemDto, state: InventoryState, model: InventoryScreenModel, branchId: String, onClose: () -> Unit) {
    val current = state.saleSource(branchId, item.id)
    val places = state.activeLocations.filter { it.branchId == null || it.branchId == branchId }
    var chosen by remember { mutableStateOf(places.firstOrNull { it.id == current?.locationId }) }
    AppDialog("Sold from · ${item.name}", onClose, subtitle = "When a dish with this item is served, its stock is taken from this place.",
        busy = state.saving, maxWidth = 520.dp,
        buttons = {
            if (current != null) KitButton("Remove", { model.removeSaleSource(branchId, item.id); onClose() }, style = ButtonStyle.SECONDARY)
            KitButton("Cancel", onClose, style = ButtonStyle.SECONDARY)
            KitButton("Save", { chosen?.let { model.setSaleSource(branchId, item.id, it.id); onClose() } }, icon = Icons.Outlined.PointOfSale, enabled = chosen != null)
        }) {
        SelectInput(chosen, places, { it.name }, { chosen = it }, label = "Place", icon = Icons.Outlined.Storefront, required = true,
            placeholder = "Choose a place", hint = "Usually the kitchen for food and the bar for drinks.")
    }
}
