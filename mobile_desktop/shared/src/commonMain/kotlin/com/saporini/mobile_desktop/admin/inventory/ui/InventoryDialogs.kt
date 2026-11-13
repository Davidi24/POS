package com.saporini.mobile_desktop.admin.inventory.ui

import com.saporini.mobile_desktop.pos.menu.ui.item.PhotoChange
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
import androidx.compose.material.icons.outlined.QrCodeScanner
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
import com.saporini.mobile_desktop.admin.inventory.itemDraftOf
import com.saporini.mobile_desktop.admin.inventory.locationDraftOf
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.ItemField
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_CODE
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_NAME
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_TEXT
import com.saporini.mobile_desktop.admin.inventory.MAX_SUPPLIER_NAME
import com.saporini.mobile_desktop.admin.inventory.StockItemDraft
import com.saporini.mobile_desktop.admin.inventory.Supplier
import com.saporini.mobile_desktop.admin.inventory.SupplierBook
import androidx.compose.material.icons.outlined.Block
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.saporini.mobile_desktop.admin.inventory.CUSTOM_KIND
import com.saporini.mobile_desktop.admin.inventory.MAX_KIND_NAME
import com.saporini.mobile_desktop.admin.inventory.PLACE_KINDS
import com.saporini.mobile_desktop.admin.inventory.StockKindDto
import com.saporini.mobile_desktop.admin.inventory.UNCATEGORIZED_KIND
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import com.saporini.mobile_desktop.core.components.CheckRow
import com.saporini.mobile_desktop.core.components.CheckState

/** Every dialog the Inventory page can show, driven by the model's state. */
@Composable
internal fun InventoryDialogs(state: InventoryState, model: InventoryScreenModel) {
    state.itemDraft?.let { ItemEditorDialog(state, model) }
    // Manage places stays open under a place's editor and the delete question.
    if (state.managingPlaces) ManagePlacesDialog(state, model)
    state.locationDraft?.let { PlaceEditorDialog(state, model) }
    state.moveDraft?.let { MoveDialog(state, model) }
    state.bulkMove?.takeIf { !it.quiet }?.let { BulkMoveDialog(it, state, model) }
    state.openCount?.let { CountDialog(it, state, model) }
    when (val confirm = state.confirm) {
        is InventoryConfirm.DeactivateItem -> ConfirmDialog("Switch off ${state.item(confirm.itemId)?.name ?: "this item"}?",
            "It leaves the lists and can't be received or counted. Its history stays, and you can switch it on again from Items.",
            "Switch off", model::confirm, model::dismissConfirm, danger = true, busy = state.saving)
        is InventoryConfirm.DeleteItem -> {
            val item = state.item(confirm.itemId)
            val held = state.levelsByItem[confirm.itemId].orEmpty().filter { it.onHandQuantity.asDouble() > 0 }
            val stock = if (held.isEmpty()) "" else " Its stock (${trimmed(held.sumOf { it.onHandQuantity.asDouble() })} ${unitShort(item?.baseUnit)} in " +
                "${if (held.size == 1) "1 place" else "${held.size} places"}) is taken out and noted in History."
            ConfirmDialog("Delete ${item?.name ?: "this item"}?",
                "Are you sure? It's removed from Inventory for good and can't be brought back.$stock Its past changes stay in History.",
                "Delete", model::confirm, model::dismissConfirm, danger = true, busy = state.saving)
        }
        is InventoryConfirm.DeleteKind -> RemoveKindDialog(confirm, state, model)
        is InventoryConfirm.DeleteLocation -> DeletePlaceDialog(confirm.locationId, state, model)
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
            // Editing: Save waits until something changed.
            val changed = draft.id == null || state.item(draft.id)?.let(::itemDraftOf) != draft
            KitButton(if (draft.id == null) "Add item" else "Save", model::saveItem, icon = Icons.Outlined.Check, loading = state.saving, enabled = changed)
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        draft.filledFrom?.let { MessageBar("Filled in from $it. Check it, add a photo and save: the product is then linked to this item.", MessageKind.INFO) }
        ItemPhotoField(draft.imageUrl, draft.photo, { photo -> model.changeItem { it.copy(photo = photo) } }, enabled = !state.saving) {
            TextInput(draft.name, { v -> model.changeItem { it.copy(name = v) } }, label = "Name", required = true, icon = Icons.Outlined.Inventory2,
                placeholder = "San Marzano tomatoes", problem = problems[ItemField.NAME], maxLength = MAX_STOCK_NAME)
        }
        FieldPair(
            { m ->
                // The restaurant's kinds (built-in ones it kept, its own, Uncategorized); an item keeps a kind that was removed.
                val current = if (draft.itemType == CUSTOM_KIND) "$CUSTOM_KIND:${draft.kindId}" else draft.itemType
                val keys = state.itemKinds.map { it.key }.let { if (current in it) it else it + current }
                val labels = state.itemKinds.associate { it.key to it.itemLabel() }
                SelectInput(current, keys, { key -> labels[key] ?: itemTypeLabel(key) }, { key ->
                    model.changeItem {
                        if (key.startsWith("$CUSTOM_KIND:")) it.copy(itemType = CUSTOM_KIND, kindId = key.substringAfter(':'))
                        else it.copy(itemType = key, kindId = null)
                    }
                }, m, label = "Kind", icon = Icons.Outlined.Category)
            },
            { m -> SelectInput(draft.baseUnit, INVENTORY_UNITS, { "${unitLabel(it)} (${unitShort(it)})" }, { v -> model.changeItem { it.copy(baseUnit = v) } }, m,
                label = "Counted in", icon = Icons.Outlined.Scale, hint = "Stock, costs and recipes use this unit") }
        )
        Caption("Cost and levels")
        FieldPair(
            { m -> TextInput(draft.costText, { v -> model.changeItem { it.copy(costText = v) } }, m, label = "Cost per ${unitShort(draft.baseUnit)}", required = true,
                icon = Icons.Outlined.Euro, placeholder = "2.50", problem = problems[ItemField.COST], keyboardType = KeyboardType.Decimal) },
            { m -> TextInput(draft.code, { v -> model.changeItem { it.copy(code = v) } }, m, label = "Your code", optional = true, icon = Icons.Outlined.Badge,
                problem = problems[ItemField.CODE], maxLength = MAX_STOCK_CODE) },
            // Short fields: they stay side by side even on a phone.
            wide = true
        )
        FieldPair(
            { m -> TextInput(draft.reorderText, { v -> model.changeItem { it.copy(reorderText = v) } }, m, label = "Reorder at", optional = true,
                suffix = unitShort(draft.baseUnit), problem = problems[ItemField.REORDER], keyboardType = KeyboardType.Decimal,
                hint = "Shows as running low under this") },
            { m -> TextInput(draft.parText, { v -> model.changeItem { it.copy(parText = v) } }, m, label = "Full at", optional = true,
                suffix = unitShort(draft.baseUnit), problem = problems[ItemField.PAR], keyboardType = KeyboardType.Decimal,
                hint = "How much a full shelf holds") },
            wide = true
        )
        Caption("Supplier")
        // One of the suppliers you created in Suppliers (user, 2026-10-30), "No supplier", or a new one added right here.
        var newSupplier by remember { mutableStateOf(false) }
        FieldPair(
            { m -> SupplierSelect(draft, m, onPick = { supplier ->
                    model.changeItem { d ->
                        if (supplier == null) d.copy(supplierId = null, supplierName = "")
                        else d.copy(supplierId = supplier.id, supplierName = supplier.name,
                            supplierSku = d.supplierSku.ifBlank { d.id?.let { SupplierBook.link(supplier.id, it)?.code }.orEmpty() })
                    }
                }, onNew = { newSupplier = true }) },
            { m -> TextInput(draft.supplierSku, { v -> model.changeItem { it.copy(supplierSku = v) } }, m, label = "Supplier's code", optional = true,
                problem = problems[ItemField.SUPPLIER_SKU], maxLength = MAX_SUPPLIER_SKU) }
        )
        if (newSupplier) NewSupplierDialog(onDismiss = { newSupplier = false }, onAdd = { name -> newSupplier = false; model.addSupplierForItem(name) })
        // Typed in, or read with a barcode scanner.
        var scanning by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextInput(draft.barcode, { v -> model.changeItem { it.copy(barcode = v.trim()) } }, Modifier.weight(1f), label = "Barcode", optional = true,
                icon = Icons.Outlined.QrCode, problem = problems[ItemField.BARCODE], maxLength = MAX_STOCK_CODE)
            KitButton("Scan", { scanning = true }, icon = Icons.Outlined.QrCodeScanner, style = ButtonStyle.SECONDARY, enabled = !state.saving)
        }
        if (scanning) ScanBarcodeDialog("Scan the item's barcode", "It goes into the Barcode field.", onCode = { code ->
            model.changeItem { it.copy(barcode = code) }
            scanning = false
        }, onDismiss = { scanning = false })
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

internal fun unitLabel(unit: String): String = when (unit) {
    "EACH" -> "Pieces"; "GRAM" -> "Grams"; "KILOGRAM" -> "Kilograms"; "MILLILITER" -> "Millilitres"; "LITER" -> "Litres"; "OUNCE" -> "Ounces"
    "POUND" -> "Pounds"; "CUP" -> "Cups"; "TABLESPOON" -> "Tablespoons"; "TEASPOON" -> "Teaspoons"; "PORTION" -> "Portions"; "CASE" -> "Cases"
    "BOTTLE" -> "Bottles"; "PACK" -> "Packs"; "TRAY" -> "Trays"; else -> unit
}

/**
 * "Remove <kind>?" (user, 2026-10-30): its items move to Uncategorized, unless "Delete its items too" is ticked; then they
 * are deleted one by one under a progress bar ("Deleting 3 of 7…") before the kind goes.
 */
@Composable
private fun RemoveKindDialog(confirm: InventoryConfirm.DeleteKind, state: InventoryState, model: InventoryScreenModel) {
    val items = confirm.scope != PLACE_KINDS
    val things = if (items) (if (confirm.count == 1L) "item" else "items") else (if (confirm.count == 1L) "place" else "places")
    var deleteThem by remember(confirm) { mutableStateOf(false) }
    val progress = state.kindRemoval
    AppDialog("Remove ${confirm.label}?", model::dismissConfirm, busy = state.saving, maxWidth = 500.dp, buttons = {
        KitButton("Cancel", model::dismissConfirm, style = ButtonStyle.SECONDARY, enabled = !state.saving)
        KitButton(if (deleteThem) "Remove and delete" else "Remove", { model.removeKind(deleteThem) }, style = ButtonStyle.DANGER, loading = state.saving)
    }) {
        Text(
            when {
                confirm.count == 0L -> "Nothing has this kind."
                deleteThem -> "Its ${confirm.count} $things will be deleted."
                else -> "Its ${confirm.count} $things go to Uncategorized."
            },
            fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Ink
        )
        if (items && confirm.count > 0) {
            CheckRow("Delete its ${confirm.count} $things too", if (deleteThem) CheckState.ON else CheckState.OFF, { deleteThem = !deleteThem },
                detail = "Items used in a recipe are kept.", enabled = !state.saving)
        }
        if (progress != null && progress.total > 0) {
            // Moves smoothly from one item to the next.
            val shown by animateFloatAsState(progress.done.toFloat() / progress.total, tween(350), label = "kind-removal")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = Kit.Danger, trackColor = Kit.Danger.copy(alpha = 0.12f))
                Text(if (progress.done < progress.total) "Deleting ${progress.done + 1} of ${progress.total}…" else "Removing the kind…",
                    fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            }
        }
    }
}

/**
 * "Delete <place>?" (user, 2026-10-30): when it holds stock, asks what happens to it: kept (it moves to the branch's
 * Uncategorized place) or deleted too (taken out of stock). History keeps a line either way.
 */
@Composable
private fun DeletePlaceDialog(locationId: String, state: InventoryState, model: InventoryScreenModel) {
    val place = state.location(locationId)
    val held = state.levels.count { it.locationId == locationId && it.onHandQuantity.asDouble() > 0 }
    val canKeep = place?.locationType != UNCATEGORIZED_KIND
    var keep by remember(locationId) { mutableStateOf(canKeep) }
    AppDialog("Delete ${place?.name ?: "this place"}?", model::dismissConfirm, busy = state.saving, maxWidth = 520.dp, buttons = {
        KitButton("Cancel", model::dismissConfirm, style = ButtonStyle.SECONDARY, enabled = !state.saving)
        KitButton("Delete place", { model.deleteLocation(locationId, keep && held > 0) }, style = ButtonStyle.DANGER, loading = state.saving)
    }) {
        if (held == 0) {
            Text("It's empty.", fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Ink)
            return@AppDialog
        }
        Text("It has ${if (held == 1) "1 item" else "$held items"} in stock.", fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Ink)
        if (canKeep) DeleteChoice(Icons.Outlined.Inbox, "Save the items in Uncategorized", null, keep, Kit.Green) { keep = true }
        DeleteChoice(Icons.Outlined.DeleteSweep, "Delete the items too", null, !keep, Kit.Danger) { keep = false }
    }
}

@Composable
private fun DeleteChoice(icon: ImageVector, title: String, text: String?, chosen: Boolean, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        color = if (chosen) color.copy(alpha = 0.07f) else Color.White, border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) color else Kit.Border)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)), Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = color)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                text?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted) }
            }
            Box(Modifier.size(20.dp).clip(CircleShape).background(if (chosen) color else Color.White)
                .border(1.5.dp, if (chosen) color else Kit.Faint, CircleShape), Alignment.Center) {
                if (chosen) Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun PlaceEditorDialog(state: InventoryState, model: InventoryScreenModel) {
    val draft = state.locationDraft ?: return
    AppDialog(
        if (draft.id == null) "New place" else "Edit ${draft.name.ifBlank { "place" }}", model::cancelLocation,
        subtitle = "Where you keep stock in this branch.", busy = state.saving, maxWidth = 560.dp,
        buttons = {
            KitButton("Cancel", model::cancelLocation, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            val changed = draft.id == null || state.location(draft.id)?.let(::locationDraftOf) != draft
            KitButton(if (draft.id == null) "Add place" else "Save", model::saveLocation, icon = Icons.Outlined.Check, loading = state.saving, enabled = changed)
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        TextInput(draft.name, { v -> model.changeLocation { it.copy(name = v) } }, label = "Name", required = true, icon = Icons.Outlined.Storefront,
            placeholder = "Walk-in fridge", maxLength = MAX_STOCK_NAME)
        Text("Kind", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        // Kinds as picture tiles: quicker to pick than a list. The restaurant's own kinds and Uncategorized come after the
        // built-in ones, and the last tile adds a new kind (user, 2026-10-30).
        val chosenKey = if (draft.locationType == CUSTOM_KIND) "$CUSTOM_KIND:${draft.kindId}" else draft.locationType
        var naming by remember { mutableStateOf(false) }
        var newName by remember { mutableStateOf("") }
        (state.placeKinds.map { it as StockKindDto? } + null).chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { kind ->
                    val key = kind?.key
                    val chosen = key != null && key == chosenKey
                    val code = kind?.code
                    val custom = code == CUSTOM_KIND
                    Surface(
                        onClick = {
                            when {
                                code == null -> naming = true
                                custom -> model.changeLocation { it.copy(locationType = CUSTOM_KIND, kindId = kind?.id) }
                                else -> model.changeLocation { it.copy(locationType = code, kindId = null) }
                            }
                        },
                        modifier = Modifier.weight(1f).height(74.dp),
                        shape = RoundedCornerShape(10.dp), color = if (chosen) Kit.GreenSoft else Color.White,
                        border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) Kit.Green else Kit.Border)
                    ) {
                        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            val tint = when {
                                chosen || code == null -> Kit.Green
                                custom -> kindKeyColor(key)
                                else -> placeTypeColor(code)
                            }
                            if (code == null) Icon(Icons.Outlined.Add, null, Modifier.size(22.dp), tint = tint)
                            else PlaceIconImage(code, defaultPlaceIconCode(code), Modifier.size(30.dp), tint)
                            Text(kind?.placeLabel() ?: "New kind", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                                color = if (chosen || kind == null) Kit.Green else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        if (naming) {
            // The new kind's name; adding it also picks it for this place.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(newName, { newName = it }, Modifier.weight(1f), label = "New kind", icon = Icons.Outlined.Category,
                    placeholder = "Wine cellar", maxLength = MAX_KIND_NAME)
                KitButton("Add", { model.addKind(PLACE_KINDS, newName, pick = true); naming = false; newName = "" },
                    icon = Icons.Outlined.Add, enabled = newName.isNotBlank() && !state.saving)
            }
        }
        Text("Picture", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        var pictureProblem by remember { mutableStateOf<String?>(null) }
        Text(pictureProblem ?: "Pick one of the icons, or upload your own picture (PNG, JPEG or WebP, up to 5 MB).", fontFamily = Inter(), fontSize = 11.sp,
            color = if (pictureProblem != null) Kit.Danger else Kit.Muted)
        // An own picture wins over the icon; picking an icon again lets go of it.
        val selectedIcon = (draft.iconCode ?: defaultPlaceIconCode(draft.locationType)).takeUnless { draft.usesOwnPicture }
        (listOf<PlaceIconChoice?>(null) + placeIconChoices).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { choice ->
                    if (choice == null) {
                        OwnPlacePictureTile(draft.imageUrl, draft.photo, !state.saving, Modifier.weight(1f).height(78.dp),
                            onChange = { change -> model.changeLocation { it.copy(photo = change) } }, onProblem = { pictureProblem = it })
                        return@forEach
                    }
                    val selected = choice.code == selectedIcon
                    Surface(
                        onClick = {
                            pictureProblem = null
                            model.changeLocation { it.copy(iconCode = choice.code, photo = if (it.imageUrl != null) PhotoChange.Remove else PhotoChange.Keep) }
                        },
                        modifier = Modifier.weight(1f).height(78.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Kit.GreenSoft else Color.White,
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.Border)
                    ) {
                        Column(Modifier.padding(horizontal = 3.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            PlaceIconImage(draft.locationType, choice.code, Modifier.size(38.dp), tint = Kit.Green)
                            Text(choice.label, fontFamily = Inter(), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 9.5.sp, color = if (selected) Kit.Green else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                repeat(5 - row.size) { Box(Modifier.weight(1f)) }
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
        // The item: picked from the list or scanned. A barcode no item has offers to create that item and come back here.
        var scanning by remember { mutableStateOf(false) }
        var missed by remember { mutableStateOf<String?>(null) }
        var notCounted by remember { mutableStateOf<String?>(null) }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchableSelect(item, state.items.filter { it.active && it.trackInventory }, { it.name }, { v -> missed = null; model.changeMove { it.copy(itemId = v.id) } },
                Modifier.weight(1f), label = "Item", required = true, icon = Icons.Outlined.Inventory2,
                detailOf = { "${it.kindLabel(long = true)} · ${unitShort(it.baseUnit)}" }, placeholder = "Choose an item", enabled = !frozen,
                leadingOf = { choice, size -> StockItemThumb(choice, size) })
            if (!draft.itemFixed) KitButton("Scan", { scanning = true }, icon = Icons.Outlined.QrCodeScanner, style = ButtonStyle.SECONDARY,
                enabled = !frozen && !state.saving)
        }
        missed?.let { code -> ScanMissBox(code, state.canEdit, onCreate = { missed = null; model.createItemFromScan(code) }, onScanAgain = { scanning = true }) }
        notCounted?.let { MessageBar("$it isn't counted (or is switched off), so it has no stock to change.", MessageKind.WARNING) }
        if (scanning) ScanBarcodeDialog("Scan an item", "Its stock change is filled in here.", onCode = { code ->
            scanning = false
            val found = state.itemByBarcode(code)
            missed = if (found == null) code else null
            notCounted = found?.takeIf { !it.active || !it.trackInventory }?.name
            if (found != null && found.active && found.trackInventory) model.changeMove { it.copy(itemId = found.id) }
        }, onDismiss = { scanning = false })
        FieldPair(
            { m -> SelectInput(state.location(draft.locationId), state.activeLocations, { it.name }, { v -> model.changeMove { it.copy(locationId = v.id) } }, m,
                label = if (draft.kind == StockMoveKind.TRANSFER) "From" else "Place", required = true, icon = Icons.Outlined.Storefront,
                placeholder = "Choose a place", enabled = !frozen,
                hint = onHereLevel?.let { "${quantityText(it.onHandQuantity, item?.baseUnit)} there now" },
                // Each place with its kind's icon (user, 2026-10-30).
                iconOf = { placeTypeIcon(it.locationType) }, iconColorOf = { placeTypeColor(it.locationType) }) },
            { m ->
                if (draft.kind == StockMoveKind.TRANSFER) {
                    SelectInput<StockLocationDto?>(state.location(draft.toLocationId), state.activeLocations.filter { it.id != draft.locationId },
                        { it?.name ?: "" }, { v -> model.changeMove { it.copy(toLocationId = v?.id) } }, m, label = "To", required = true,
                        icon = Icons.Outlined.Storefront, placeholder = "Choose a place", enabled = !frozen,
                        iconOf = { placeTypeIcon(it?.locationType) }, iconColorOf = { placeTypeColor(it?.locationType) })
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
            KitButton("Save", { chosen?.let { model.setSaleSource(branchId, item.id, it.id); onClose() } }, icon = Icons.Outlined.PointOfSale,
                enabled = chosen != null && chosen?.id != current?.locationId)
        }) {
        SelectInput(chosen, places, { it.name }, { chosen = it }, label = "Place", icon = Icons.Outlined.Storefront, required = true,
            placeholder = "Choose a place", hint = "Usually the kitchen for food and the bar for drinks.")
    }
}

private const val NO_SUPPLIER = "-"
private const val NEW_SUPPLIER = "+"

/**
 * The item's supplier: one of the suppliers you created in Suppliers (the ones in use, plus the item's own even when it's
 * not active), "No supplier", or "New supplier" at the end.
 */
@Composable
private fun SupplierSelect(draft: StockItemDraft, modifier: Modifier, onPick: (Supplier?) -> Unit, onNew: () -> Unit) {
    val suppliers = SupplierBook.suppliers.filter { it.active || it.id == draft.supplierId }.sortedBy { it.name.lowercase() }
    val choices = listOf(NO_SUPPLIER) + suppliers.map { it.id } + NEW_SUPPLIER
    SelectInput(draft.supplierId ?: NO_SUPPLIER, choices,
        { key -> when (key) { NO_SUPPLIER -> "No supplier"; NEW_SUPPLIER -> "New supplier"; else -> SupplierBook.supplier(key)?.name ?: draft.supplierName } },
        { key -> when (key) { NO_SUPPLIER -> onPick(null); NEW_SUPPLIER -> onNew(); else -> SupplierBook.supplier(key)?.let(onPick) } },
        modifier, label = "Supplier", icon = Icons.Outlined.LocalShipping,
        iconOf = { key -> when (key) {
            NO_SUPPLIER -> Icons.Outlined.Block
            NEW_SUPPLIER -> Icons.Outlined.Add
            else -> Icons.Outlined.LocalShipping
        } },
        iconColorOf = { key -> when (key) { NO_SUPPLIER -> Kit.Muted; NEW_SUPPLIER -> Kit.Green; else -> Kit.Blue } })
}

/** "New supplier" from the item form: just the name; the rest is filled in later in Suppliers. */
@Composable
private fun NewSupplierDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AppDialog("New supplier", onDismiss, subtitle = "Add their details later in Suppliers.", maxWidth = 460.dp, buttons = {
        KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
        KitButton("Add", { onAdd(name) }, icon = Icons.Outlined.Check, enabled = name.isNotBlank())
    }) {
        TextInput(name, { name = it }, label = "Supplier name", icon = Icons.Outlined.LocalShipping, placeholder = "Green Farm", maxLength = 150)
    }
}
