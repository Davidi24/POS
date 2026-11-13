@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.saporini.mobile_desktop.admin.inventory.PurchaseSteps
import com.saporini.mobile_desktop.admin.inventory.PurchaseStatus
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.core.files.saveTextFile
import com.saporini.mobile_desktop.admin.inventory.priceToCents
import com.saporini.mobile_desktop.admin.inventory.parseTable
import com.saporini.mobile_desktop.admin.inventory.linkProduct
import com.saporini.mobile_desktop.admin.inventory.importCatalog
import com.saporini.mobile_desktop.admin.inventory.catalogOf
import com.saporini.mobile_desktop.admin.inventory.CatalogProduct
import com.saporini.mobile_desktop.admin.inventory.CatalogField
import com.saporini.mobile_desktop.admin.inventory.DeliveryLine
import com.saporini.mobile_desktop.admin.inventory.PurchaseLine
import com.saporini.mobile_desktop.admin.inventory.PurchaseOrder
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.Supplier
import com.saporini.mobile_desktop.admin.inventory.SupplierBook
import com.saporini.mobile_desktop.admin.inventory.SupplierItem
import com.saporini.mobile_desktop.admin.inventory.SupplierPlace
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.FieldBlock
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToggleRow
import com.saporini.mobile_desktop.core.format.centsOrNull
import com.saporini.mobile_desktop.core.format.decimalTextToCents
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.ui.item.PhotoChange
import com.saporini.mobile_desktop.pos.reservations.CompactDatePicker
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlinx.coroutines.launch
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.FileKitType
import com.saporini.mobile_desktop.pos.menu.ui.item.photoContentType
import com.saporini.mobile_desktop.pos.menu.ui.item.MaxPhotoBytes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.QrCodeScanner
import com.saporini.mobile_desktop.admin.inventory.StockItemDraft
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind

/** Whichever supplier dialog is open. */
@Composable
internal fun SupplierDialogs(
    dialog: SupplierDialog?, state: InventoryState, model: InventoryScreenModel, today: LocalDate,
    onDialog: (SupplierDialog?) -> Unit, onAdded: (String) -> Unit, onPlaced: (PurchaseOrder) -> Unit, onNote: (String) -> Unit = {}
) {
    val close = { onDialog(null) }
    when (dialog) {
        null -> Unit
        SupplierDialog.Add -> AddSupplierDialog(state, model, close) { id -> onDialog(null); onAdded(id) }
        is SupplierDialog.Edit -> SupplierBook.supplier(dialog.supplierId)?.let { EditSupplierDialog(it, close) }
        is SupplierDialog.Place -> SupplierBook.supplier(dialog.supplierId)?.let { PlaceDialog(it, SupplierBook.place(dialog.placeId), close) }
        is SupplierDialog.LinkItems -> SupplierBook.supplier(dialog.supplierId)?.let { LinkItemsDialog(it, state, close) }
        is SupplierDialog.EditLink -> {
            val link = SupplierBook.link(dialog.supplierId, dialog.itemId)
            val item = state.item(dialog.itemId)
            val supplier = SupplierBook.supplier(dialog.supplierId)
            if (link != null && item != null && supplier != null) EditLinkDialog(supplier, item, link, close)
        }
        is SupplierDialog.NewOrder -> NewOrderDialog(dialog, state, today, close, onLinkItems = { onDialog(SupplierDialog.LinkItems(it)) }, onPlaced = onPlaced)
        is SupplierDialog.ImportCatalog -> SupplierBook.supplier(dialog.supplierId)?.let { supplier ->
            ImportCatalogDialog(supplier, close) { note -> onDialog(null); onNote(note) }
        }
        is SupplierDialog.LinkProduct -> SupplierBook.catalog.firstOrNull { it.id == dialog.productId }?.let { product ->
            LinkProductDialog(product, state, close) { note -> onDialog(null); onNote(note) }
        }
        is SupplierDialog.Order -> SupplierBook.order(dialog.orderId)?.let { order ->
            OrderDialog(order, dialog.receiving, state, model, today, close, onReceiving = { onDialog(SupplierDialog.Order(order.id, it)) })
        }
    }
}

// ---- Adding a supplier: who, their places, what you buy ----

private class SupplierForm(supplier: Supplier?) {
    var name by mutableStateOf(supplier?.name.orEmpty())
    var contact by mutableStateOf(supplier?.contactName.orEmpty())
    var phone by mutableStateOf(supplier?.phone.orEmpty())
    var email by mutableStateOf(supplier?.email.orEmpty())
    var notes by mutableStateOf(supplier?.notes.orEmpty())
    var active by mutableStateOf(supplier?.active ?: true)
    var address by mutableStateOf(supplier?.address.orEmpty())
    var cover by mutableStateOf<PhotoChange>(supplier?.cover?.let { PhotoChange.Replace(it, "banner", "image/png") } ?: PhotoChange.Keep)
    // The picture as a photo change: a kept picture shows as "picked" so the field can show it (MOCK bytes, no URL yet).
    var photo by mutableStateOf<PhotoChange>(supplier?.photo?.let { PhotoChange.Replace(it, "photo", "image/png") } ?: PhotoChange.Keep)

    fun problem(ownId: String?): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Write the supplier's name."
            SupplierBook.suppliers.any { it.id != ownId && it.name.equals(trimmed, ignoreCase = true) } -> "There's already a supplier called $trimmed."
            email.isNotBlank() && !email.trim().matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) -> "That email doesn't look right."
            else -> null
        }
    }

    fun toSupplier(base: Supplier?) = (base ?: Supplier("", "")).copy(name = name.trim(), contactName = contact.trim(), phone = phone.trim(),
        email = email.trim(), notes = notes.trim(), active = active, photo = (photo as? PhotoChange.Replace)?.bytes,
        cover = (cover as? PhotoChange.Replace)?.bytes, address = address.trim())
}

@Composable
private fun SupplierFields(form: SupplierForm, problem: String?, withAddress: Boolean = true) {
    // Just the banner across the top of their card (user, 2026-10-30: "a banner is enough, why an image too"); their card
    // keeps the usual supplier picture.
    BannerField(form.cover) { form.cover = it }
    TextInput(form.name, { form.name = it }, label = "Supplier name", placeholder = "e.g. Green Farm", required = true, icon = Icons.Outlined.Storefront,
        problem = problem.takeIf { it != null && !it.startsWith("That email") }, maxLength = 150)
    FieldPair(
        { m -> TextInput(form.contact, { form.contact = it }, m, label = "Contact person", optional = true, icon = Icons.Outlined.Person, maxLength = 120) },
        { m -> TextInput(form.phone, { form.phone = it }, m, label = "Phone", optional = true, icon = Icons.Outlined.Call, keyboardType = KeyboardType.Phone, maxLength = 40) }
    )
    TextInput(form.email, { form.email = it }, label = "Email for orders", optional = true, icon = Icons.Outlined.Mail, keyboardType = KeyboardType.Email,
        problem = problem.takeIf { it != null && it.startsWith("That email") }, maxLength = 160)
    // Adding a supplier asks for addresses with their places only (user, 2026-10-30); editing keeps the card's address.
    if (withAddress) TextInput(form.address, { form.address = it }, label = "Address", optional = true, placeholder = "Street, town", icon = Icons.Outlined.Place,
        hint = "Shown on their card with a Directions link to Google Maps.", maxLength = 240)
    TextInput(form.notes, { form.notes = it }, label = "Notes", optional = true, placeholder = "Minimum order, payment terms, who to call…", singleLine = false,
        minLines = 2, maxLength = 2000)
}

@Composable
private fun AddSupplierDialog(state: InventoryState, model: InventoryScreenModel, onDismiss: () -> Unit, onAdded: (String) -> Unit) {
    val form = remember { SupplierForm(null) }
    var step by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    val places = remember { mutableStateListOf<SupplierPlace>() }
    val placeForm = remember { PlaceForm(null) }
    var placeProblem by remember { mutableStateOf<String?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    var scanning by remember { mutableStateOf(false) }
    var scanNote by remember { mutableStateOf<String?>(null) }
    val supplierName = form.name.trim()
    // The name a new place gets when left empty: "<Supplier> main" until there is one (user, 2026-10-30).
    val suggestion = "$supplierName main".takeIf { supplierName.isNotEmpty() && places.none { p -> p.name.equals(it, ignoreCase = true) } }
    fun addPlace() {
        if (placeForm.name.isBlank() && suggestion != null) placeForm.name = suggestion
        placeProblem = placeForm.problem()
        if (placeProblem == null) { places += placeForm.toPlace("", ""); placeForm.clear() }
    }
    // A place typed but not added yet still counts when moving on.
    fun keepTypedPlace() {
        if (placeForm.name.isNotBlank()) { places += placeForm.toPlace("", ""); placeForm.clear() }
    }
    // The "give it a name" note goes as soon as something is typed.
    LaunchedEffect(placeForm.name) { if (placeForm.name.isNotBlank()) placeProblem = null }
    AppDialog(
        "Add supplier", onDismiss, subtitle = listOf("Who you buy from", "Where you order from", "What you buy from them")[step], maxWidth = 660.dp,
        buttons = {
            // Back carries the arrow; the ways forward have none (user, 2026-10-30).
            if (step == 0) KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
            else KitButton("Back", { step-- }, icon = Icons.AutoMirrored.Outlined.ArrowBack, style = ButtonStyle.SECONDARY)
            when (step) {
                0 -> KitButton("Next: their places", {
                    problem = form.problem(null)
                    if (problem == null) step = 1
                })
                1 -> {
                    val skip = places.isEmpty() && placeForm.name.isBlank()
                    KitButton(if (skip) "Skip for now" else "Next: items", { keepTypedPlace(); step = 2 },
                        style = if (skip) ButtonStyle.PRIMARY_OUTLINE else ButtonStyle.PRIMARY)
                }
                else -> KitButton("Add supplier", {
                    val prices = picked.associateWith { id -> state.item(id)?.costPerUnit.centsOrNull() }
                    // Their card's address is the first place's, since adding asks for addresses with the places only.
                    val supplier = form.toSupplier(null).let { found ->
                        if (found.address.isNotBlank()) found else found.copy(address = places.firstOrNull { it.address.isNotBlank() }?.address.orEmpty())
                    }
                    onAdded(SupplierBook.add(supplier, places.toList(), prices))
                }, icon = Icons.Outlined.Check)
            }
        }
    ) {
        StepBar(step, listOf("Supplier", "Their places", "Items"))
        when (step) {
            0 -> SupplierFields(form, problem, withAddress = false)
            1 -> {
                Tip("Add each warehouse or branch you order from. You choose one of them on every order.")
                places.forEachIndexed { index, place -> key(place) { PlaceCard(place) { places.removeAt(index) } } }
                // The new place in a card of its own, so it's clear one more is being added.
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Kit.Border, RoundedCornerShape(14.dp)).background(Kit.Canvas)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.AddLocationAlt, null, Modifier.size(18.dp), tint = Kit.Green)
                        Text(if (places.isEmpty()) "Their first place" else "One more place", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = Kit.Ink)
                    }
                    PlaceFields(placeForm, placeProblem, suggestion)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        KitButton("Add this place", ::addPlace, icon = Icons.Outlined.Add, style = ButtonStyle.PRIMARY_OUTLINE)
                    }
                }
            }
            else -> {
                Tip("Pick the stock items you already have that ${supplierName.ifEmpty { "they" }} bring. One item can come from several suppliers, so don't make a second “Eggs”.")
                // Something they bring that isn't in your stock yet: make it here (it's picked once saved), or scan it.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitButton("New item", { scanNote = null; model.newItemPrefilled(StockItemDraft(supplierName = supplierName)) { saved -> picked += saved.id } },
                        icon = Icons.Outlined.Add, style = ButtonStyle.PRIMARY_OUTLINE)
                    KitButton("Scan", { scanNote = null; scanning = true }, icon = Icons.Outlined.QrCodeScanner, style = ButtonStyle.SECONDARY)
                }
                scanNote?.let { MessageBar(it, MessageKind.INFO) }
                ItemPicker(state, picked.toSet(), emptySet(), { id -> if (id in picked) picked.remove(id) else picked += id })
            }
        }
    }
    if (scanning) ScanBarcodeDialog("Scan an item", "It's picked for ${supplierName.ifEmpty { "this supplier" }}. A barcode you don't have yet starts a new item.",
        onCode = { code ->
            scanning = false
            val found = state.itemByBarcode(code)
            if (found != null) {
                if (found.id !in picked) picked += found.id
                scanNote = "${found.name} is picked."
            } else {
                model.newItemPrefilled(StockItemDraft(barcode = code, supplierName = supplierName)) { saved -> picked += saved.id }
            }
        }, onDismiss = { scanning = false })
}

/** A place added while adding a supplier: a card that slides in, with where it is and the days it delivers. */
@Composable
private fun PlaceCard(place: SupplierPlace, onRemove: () -> Unit) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(260)) + expandVertically(tween(260)) + scaleIn(tween(260), initialScale = 0.96f)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Kit.Green.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(Icons.Outlined.Warehouse, Kit.Green, 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(place.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(place.address.ifBlank { null }, place.phone.ifBlank { null }).joinToString(" · ").ifBlank { "No address yet" },
                    fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (place.deliveryDays.isNotEmpty()) {
                    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    Text(if (place.deliveryDays.size == 7) "Delivers every day" else "Delivers " + place.deliveryDays.sorted().joinToString(" · ") { names[it - 1] },
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Green, maxLines = 1)
                }
            }
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onRemove), Alignment.Center) {
                Icon(Icons.Outlined.Close, "Remove place", Modifier.size(17.dp), tint = Kit.Muted)
            }
        }
    }
}

/** The banner picture: a wide tile to click (add or change; × removes it). Checked like any photo (type, size, really a picture). */
@Composable
private fun BannerField(photo: PhotoChange, onChange: (PhotoChange) -> Unit) {
    var problem by remember { mutableStateOf<String?>(null) }
    val picked = photo as? PhotoChange.Replace
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file == null) return@rememberFilePickerLauncher
        scope.launch {
            val contentType = photoContentType(file.name)
            val bytes = if (contentType == null) null else runCatching { file.readBytes() }.getOrNull()
            problem = when {
                contentType == null -> "Use a PNG, JPEG or WebP picture."
                bytes == null -> "This picture couldn't be read. Try another one."
                bytes.size > MaxPhotoBytes -> "This picture is too big. Use one under 5 MB."
                runCatching { bytes.decodeToImageBitmap() }.isFailure -> "This file can't be opened as a picture."
                else -> null
            }
            if (problem == null && bytes != null && contentType != null) onChange(PhotoChange.Replace(bytes, file.name, contentType))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).clickable { picker.launch() }) {
            SupplierBanner(picked?.bytes, Modifier.fillMaxSize())
            if (picked == null) {
                Row(Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(17.dp), tint = Kit.Green)
                    Text("Add a banner picture", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
                }
            } else {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Color.White).clickable { problem = null; onChange(PhotoChange.Keep) },
                    Alignment.Center) {
                    Icon(Icons.Outlined.Close, "Remove banner", Modifier.size(15.dp), tint = Kit.Danger)
                }
                Row(Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(13.dp), tint = Color.White)
                    Text("Change banner", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White)
                }
            }
        }
        problem?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Danger) }
    }
}

/** 1 Supplier — 2 Their places — 3 Items: where you are in adding a supplier. */
@Composable
private fun StepBar(step: Int, labels: List<String>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            val done = index < step
            val current = index == step
            Box(Modifier.size(26.dp).clip(CircleShape).background(if (done || current) Kit.Green else Kit.Tint), Alignment.Center) {
                if (done) Icon(Icons.Outlined.Check, null, Modifier.size(15.dp), tint = Color.White)
                else Text("${index + 1}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (current) Color.White else Kit.Muted)
            }
            Spacer(Modifier.width(8.dp))
            Text(label, fontFamily = Inter(), fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold, fontSize = 12.sp,
                color = if (current || done) Kit.Ink else Kit.Muted, maxLines = 1)
            if (index < labels.lastIndex) {
                Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(2.dp).clip(RoundedCornerShape(50)).background(if (done) Kit.Green else Kit.RowBorder))
            }
        }
    }
}

@Composable
private fun Tip(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.GreenSoft).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.Lightbulb, null, Modifier.size(17.dp), tint = Kit.Green)
        Text(text, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Ink)
    }
}

@Composable
private fun EditSupplierDialog(supplier: Supplier, onDismiss: () -> Unit) {
    val form = remember(supplier.id) { SupplierForm(supplier) }
    var problem by remember { mutableStateOf<String?>(null) }
    AppDialog("Edit supplier", onDismiss, subtitle = supplier.name, maxWidth = 620.dp, buttons = {
        KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
        KitButton("Save", {
            problem = form.problem(supplier.id)
            if (problem == null) { SupplierBook.update(form.toSupplier(supplier)); onDismiss() }
        }, icon = Icons.Outlined.Check, enabled = form.toSupplier(supplier) != supplier)
    }) {
        SupplierFields(form, problem)
        // Switching a supplier off keeps it and its orders, but it isn't offered for new orders.
        ToggleRow("Active", form.active, { form.active = it }, detail = "Not active: not offered for new orders.")
    }
}

// ---- Places ----

private class PlaceForm(place: SupplierPlace?) {
    var name by mutableStateOf(place?.name.orEmpty())
    var address by mutableStateOf(place?.address.orEmpty())
    var phone by mutableStateOf(place?.phone.orEmpty())
    var days by mutableStateOf(place?.deliveryDays ?: emptySet())

    fun problem() = if (name.isBlank()) "Give the place a name, like “Main warehouse”." else null
    fun toPlace(id: String, supplierId: String) = SupplierPlace(id, supplierId, name.trim(), address.trim(), phone.trim(), days)
    fun clear() { name = ""; address = ""; phone = ""; days = emptySet() }
}

@Composable
private fun PlaceFields(form: PlaceForm, problem: String?, suggestion: String? = null) {
    TextInput(form.name, { form.name = it }, label = "Place name", placeholder = suggestion ?: "e.g. Main warehouse, Durrës branch", icon = Icons.Outlined.Warehouse,
        problem = problem, hint = suggestion?.let { "Left empty, it's called “$it”." }, maxLength = 120)
    FieldPair(
        { m -> TextInput(form.address, { form.address = it }, m, label = "Address", optional = true, icon = Icons.Outlined.Place, maxLength = 240) },
        { m -> TextInput(form.phone, { form.phone = it }, m, label = "Phone", optional = true, icon = Icons.Outlined.Call, keyboardType = KeyboardType.Phone, maxLength = 40) }
    )
    FieldBlock("Delivers on", optional = true, hint = "The days they bring orders from this place.") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // All week, then the days one by one (user, 2026-10-30).
            val all = form.days.size == 7
            Box(
                Modifier.weight(1.7f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(if (all) Kit.Green else Color.White)
                    .border(1.dp, if (all) Kit.Green else Kit.Border, RoundedCornerShape(8.dp)).clickable { form.days = if (all) emptySet() else (1..7).toSet() },
                Alignment.Center
            ) {
                Text(if (LocalNarrowDialog.current) "All" else "All week", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                    color = if (all) Color.White else Kit.Ink, maxLines = 1)
            }
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { index, label ->
                val day = index + 1
                val on = day in form.days
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Kit.Green else Color.White)
                        .border(1.dp, if (on) Kit.Green else Kit.Border, RoundedCornerShape(8.dp)).clickable { form.days = if (on) form.days - day else form.days + day },
                    Alignment.Center
                ) {
                    Text(if (LocalNarrowDialog.current) label.take(1) else label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        color = if (on) Color.White else Kit.Ink)
                }
            }
        }
    }
}

@Composable
private fun PlaceDialog(supplier: Supplier, place: SupplierPlace?, onDismiss: () -> Unit) {
    val form = remember(place?.id) { PlaceForm(place) }
    var problem by remember { mutableStateOf<String?>(null) }
    AppDialog(if (place == null) "Add a place" else "Edit place", onDismiss, subtitle = supplier.name, maxWidth = 560.dp, buttons = {
        if (place != null) KitButton("Remove", { SupplierBook.removePlace(place.id); onDismiss() }, icon = Icons.Outlined.Delete, style = ButtonStyle.SECONDARY)
        KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
        KitButton("Save", {
            problem = form.problem()
            if (problem == null) { SupplierBook.savePlace(form.toPlace(place?.id.orEmpty(), supplier.id)); onDismiss() }
        }, icon = Icons.Outlined.Check, enabled = place == null || form.toPlace(place.id, supplier.id) != place)
    }) {
        Tip("A warehouse or branch of ${supplier.name} that you order from.")
        PlaceFields(form, problem)
    }
}

// ---- Linking items ----

/** Your stock items with their pictures, to tick the ones a supplier brings. Items already linked to this supplier are left out. */
@Composable
private fun ItemPicker(state: InventoryState, picked: Set<String>, exclude: Set<String>, onToggle: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val items = state.items.filter { it.active && it.id !in exclude && words.all { w -> w in (it.name + " " + it.code.orEmpty()).lowercase() } }
        .sortedWith(compareByDescending<StockItemDto> { it.id in picked }.thenBy { it.name.lowercase() })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchField(search, { search = it }, Modifier.weight(1f), placeholder = "Find an item", height = 42.dp)
        Text(if (picked.isEmpty()) "None picked" else "${picked.size} picked", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = if (picked.isEmpty()) Kit.Muted else Kit.Green)
    }
    if (items.isEmpty()) {
        Text(if (state.items.isEmpty()) "No stock items yet. Add them in Inventory → Items first." else "Nothing matches “$search”.",
            fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val on = item.id in picked
            val others = SupplierBook.suppliersOf(item.id).map { it.name }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) Kit.GreenSoft else Color.White)
                    .border(if (on) 1.5.dp else 1.dp, if (on) Kit.Green else Kit.RowBorder, RoundedCornerShape(10.dp)).clickable { onToggle(item.id) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) Kit.Green else Color.White).border(1.5.dp, if (on) Kit.Green else Kit.Faint, CircleShape),
                    Alignment.Center) {
                    if (on) Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = Color.White)
                }
                StockItemThumb(item, 38.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(item.kindLabel(long = true), others.takeIf { it.isNotEmpty() }?.let { "also from ${it.joinToString(", ")}" })
                        .joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${moneyText(item.costPerUnit.centsOrNull() ?: 0, null)} / ${unitShort(item.baseUnit)}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LinkItemsDialog(supplier: Supplier, state: InventoryState, onDismiss: () -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    val linked = SupplierBook.linksOf(supplier.id).map { it.itemId }.toSet()
    AppDialog("Link items", onDismiss, subtitle = "What you buy from ${supplier.name}", maxWidth = 620.dp, buttons = {
        KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
        KitButton(if (picked.isEmpty()) "Link items" else "Link ${count(picked.size, "item")}", {
            SupplierBook.linkItems(supplier.id, picked.associateWith { id -> state.item(id)?.costPerUnit.centsOrNull() })
            onDismiss()
        }, icon = Icons.Outlined.AddLink, enabled = picked.isNotEmpty())
    }) {
        Tip("Pick items you already have. Their price starts at your item's cost; change it afterwards on the item's line.")
        ItemPicker(state, picked.toSet(), linked) { id -> if (id in picked) picked.remove(id) else picked += id }
    }
}

@Composable
private fun EditLinkDialog(supplier: Supplier, item: StockItemDto, link: SupplierItem, onDismiss: () -> Unit) {
    val unit = unitShort(item.baseUnit)
    var code by remember { mutableStateOf(link.code) }
    var price by remember { mutableStateOf(link.priceCents?.let(::amountText).orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }
    AppDialog(item.name, onDismiss, subtitle = "As bought from ${supplier.name}", maxWidth = 520.dp, buttons = {
        KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
        KitButton("Save", {
            val cents = price.trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.let(::decimalTextToCents)
            problem = if (price.isNotBlank() && (cents == null || cents < 0)) "Write the price like 3.20." else null
            if (problem == null) { SupplierBook.saveLink(link.copy(code = code.trim(), priceCents = cents)); onDismiss() }
        }, icon = Icons.Outlined.Check, enabled = code != link.code || price != link.priceCents?.let(::amountText).orEmpty())
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StockItemThumb(item, 46.dp)
            Column {
                Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
                Text("Your cost now: ${moneyText(item.costPerUnit.centsOrNull() ?: 0, null)} / $unit", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            }
        }
        FieldPair(
            { m -> TextInput(code, { code = it }, m, label = "Their code", optional = true, placeholder = "e.g. GF-120", icon = Icons.Outlined.QrCode2, maxLength = 100) },
            { m -> TextInput(price, { price = it; problem = null }, m, label = "Their price", optional = true, placeholder = "0.00", icon = Icons.Outlined.Euro,
                keyboardType = KeyboardType.Decimal, suffix = "/ $unit", problem = problem) }
        )
    }
}

// ---- Ordering ----

@Composable
private fun NewOrderDialog(
    start: SupplierDialog.NewOrder, state: InventoryState, today: LocalDate, onDismiss: () -> Unit,
    onLinkItems: (String) -> Unit, onPlaced: (PurchaseOrder) -> Unit
) {
    val suppliers = SupplierBook.suppliers.filter { it.active }.sortedBy { it.name.lowercase() }
    var supplierId by remember { mutableStateOf(start.supplierId ?: suppliers.firstOrNull()?.id) }
    val places = supplierId?.let(SupplierBook::placesOf).orEmpty()
    var placeId by remember(supplierId) { mutableStateOf(places.firstOrNull()?.id) }
    val shelves = state.locations.filter { it.active }
    val links = supplierId?.let(SupplierBook::linksOf).orEmpty().mapNotNull { link -> state.item(link.itemId)?.let { link to it } }
    val lowIds = state.lowStock.map { it.inventoryItemId }.toSet()
    // Each item goes where it's kept (its first stock place), else the first stock place; it can be changed when counting.
    fun homeOf(itemId: String) = state.levels.firstOrNull { it.inventoryItemId == itemId }?.locationId ?: shelves.firstOrNull()?.id
    var expected by remember { mutableStateOf(today.plus(DatePeriod(days = 1))) }
    // Ordered amounts as typed; "Order these" fills the running-low items with what brings them back to full.
    val amounts = remember(supplierId) {
        mutableStateMapOf<String, String>().apply {
            if (supplierId == start.supplierId) start.itemIds.forEach { id -> state.item(id)?.let { put(id, suggested(it, state, picked = true)) } }
        }
    }
    var problem by remember { mutableStateOf<String?>(null) }
    val lines = links.mapNotNull { (link, item) ->
        val quantity = amounts[item.id]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        PurchaseLine(item.id, quantity, link.priceCents, placeId = homeOf(item.id))
    }
    val total = lines.sumOf { it.cents ?: 0 }
    AppDialog("New order", onDismiss, subtitle = "Each item goes to its own stock place", maxWidth = 760.dp, buttons = {
        Text(if (lines.isEmpty()) "Nothing to order yet" else "${count(lines.size, "item")} · ${moneyText(total, null)}", Modifier.weight(1f),
            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (lines.isEmpty()) Kit.Muted else Kit.Ink)
        KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
        KitButton("Place order", {
            val supplier = supplierId
            problem = when {
                supplier == null -> "Choose a supplier."
                lines.isEmpty() -> "Write how much you want of at least one item."
                else -> null
            }
            if (problem == null && supplier != null) {
                onPlaced(SupplierBook.placeOrder(PurchaseOrder("", "", supplier, placeId, lines.firstOrNull()?.placeId, today, expected, lines)))
            }
        }, icon = Icons.AutoMirrored.Outlined.Send, enabled = lines.isNotEmpty())
    }) {
        // From → into, as a little route.
        val narrow = LocalNarrowDialog.current
        FieldPair(
            { m -> SelectInput(suppliers.firstOrNull { it.id == supplierId }, suppliers, { it.name }, { supplierId = it.id; problem = null }, m,
                label = "Supplier", icon = Icons.Outlined.Storefront, required = true) },
            { m -> SelectInput(places.firstOrNull { it.id == placeId }, places, { it.name }, { placeId = it.id }, m, label = "From their place",
                icon = Icons.Outlined.Warehouse, enabled = places.isNotEmpty(), placeholder = if (places.isEmpty()) "No places added" else "Choose",
                hint = if (places.isEmpty()) "Add their places on the supplier." else null) }
        )
        FieldPair(
            { m -> DateField("Expected", expected, today, m) { expected = it } },
            { m -> Spacer(m) }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("What to order", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
            if (links.any { it.second.id in lowIds }) Text("Fill running low", Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                links.filter { it.second.id in lowIds }.forEach { (_, item) -> amounts[item.id] = suggested(item, state) }
            }.padding(horizontal = 8.dp, vertical = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
        }
        if (links.isEmpty()) {
            Tip("Nothing is linked to this supplier yet. Link the items you buy from them first.")
            supplierId?.let { id -> KitButton("Link items", { onLinkItems(id) }, icon = Icons.Outlined.AddLink, style = ButtonStyle.SECONDARY) }
        }
        // What was picked on the supplier comes first, then what's running low.
        links.sortedWith(compareByDescending<Pair<SupplierItem, StockItemDto>> { it.second.id in start.itemIds && supplierId == start.supplierId }
            .thenByDescending { it.second.id in lowIds }.thenBy { it.second.name.lowercase() }).forEach { (link, item) ->
            OrderItemRow(item, link, state, item.id in lowIds, amounts[item.id].orEmpty(), narrow, state.location(homeOf(item.id))?.name) {
                amounts[item.id] = it; problem = null
            }
        }
        problem?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger) }
    }
}

/**
 * Enough to bring an item back to its full level (or its reorder point) in every place where it's low. An item [picked]
 * that isn't low gets what brings its stock up to its full level, when it has one.
 */
private fun suggested(item: StockItemDto, state: InventoryState, picked: Boolean = false): String {
    val low = state.lowStock.filter { it.inventoryItemId == item.id }
    val need = low.sumOf { level ->
        val target = (level.parQuantity ?: item.parLevel ?: level.reorderQuantity ?: item.reorderPoint).asDouble()
        (target - level.onHandQuantity.asDouble()).coerceAtLeast(0.0)
    }.takeIf { it > 0 } ?: if (picked) {
        val onHand = state.levels.filter { it.inventoryItemId == item.id }.sumOf { it.onHandQuantity.asDouble() }
        ((item.parLevel ?: item.reorderPoint).asDouble() - onHand).takeIf { it > 0 } ?: 0.0
    } else 0.0
    return if (need > 0) trimmed(kotlin.math.ceil(need)) else ""
}

@Composable
private fun OrderItemRow(item: StockItemDto, link: SupplierItem, state: InventoryState, low: Boolean, amount: String, narrow: Boolean, home: String?,
                         onAmount: (String) -> Unit) {
    val unit = unitShort(item.baseUnit)
    val onHand = state.levels.filter { it.inventoryItemId == item.id }.sumOf { it.onHandQuantity.asDouble() }
    val quantity = amount.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (quantity != null) Kit.GreenSoft.copy(alpha = 0.6f) else Color.White)
            .border(1.dp, if (quantity != null) Kit.Green.copy(alpha = 0.5f) else Kit.RowBorder, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StockItemThumb(item, 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(buildString {
                append("${trimmed(onHand)} $unit in stock")
                if (low) append(" · low")
                if (!narrow) link.priceCents?.let { append(" · ${moneyText(it, null)} / $unit") }
                home?.let { append(" · goes to $it") }
            }, fontFamily = Inter(), fontSize = 11.sp, color = if (low) Kit.Danger else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AmountBox(amount, unit, onChange = onAmount)
        if (!narrow) {
            Text(quantity?.let { q -> link.priceCents?.let { moneyText((it * q).roundToLong(), null) } } ?: "", Modifier.width(82.dp), fontFamily = Inter(),
                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
        }
    }
}

/** A small amount field with the unit after it. */
@Composable
private fun AmountBox(value: String, unit: String, field: Modifier = Modifier, highlight: Boolean = false, onChange: (String) -> Unit) {
    Row(
        Modifier.width(118.dp).height(38.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
            .border(if (highlight) 1.5.dp else 1.dp, if (highlight) Kit.Green else Kit.Border, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value, { text -> onChange(text.filter { it.isDigit() || it == '.' || it == ',' }.take(9)) }, field.weight(1f), singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text("0", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Faint); inner() } }
        )
        Text(unit, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
    }
}

/** A date field that opens the small calendar. */
@Composable
private fun DateField(label: String, date: LocalDate, today: LocalDate, modifier: Modifier, onDate: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FieldBlock(label, modifier, hint = "When the delivery should arrive.") {
        Box {
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).clickable { open = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp), tint = Kit.Ink)
                Text(dayLabel(date, today).replaceFirstChar { it.uppercase() }, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = Kit.Ink)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Ink)
            }
            DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
                CompactDatePicker(date) { onDate(it); open = false }
            }
        }
    }
}

// ---- One order, step by step: ordered → received → counted & put in place → confirmed in stock ----

@Composable
private fun OrderDialog(
    order: PurchaseOrder, receiving: Boolean, state: InventoryState, model: InventoryScreenModel, today: LocalDate,
    onDismiss: () -> Unit, onReceiving: (Boolean) -> Unit
) {
    val supplier = SupplierBook.supplier(order.supplierId)
    val from = SupplierBook.place(order.placeId)
    val shelves = state.locations.filter { it.active }
    val status = order.status
    val edit = state.canEdit
    // Each item goes to its own stock place: the one on the line, else where the item is kept, else the order's.
    fun placeOf(line: PurchaseLine) = line.placeId ?: state.levels.firstOrNull { it.inventoryItemId == line.itemId }?.locationId ?: order.intoLocationId
        ?: shelves.firstOrNull()?.id
    val places = remember(order.id, status) { mutableStateMapOf<String, String>().apply { order.lines.forEach { line -> placeOf(line)?.let { put(line.itemId, it) } } } }
    // Step 2: how much of each item arrived (starts at what's still to come). Step 3: what was counted (starts at what arrived).
    val amounts = remember(order.id, status) {
        mutableStateMapOf<String, String>().apply {
            order.lines.forEach { line ->
                put(line.itemId, trimmed(when (status) {
                    PurchaseStatus.ARRIVED -> line.counted ?: line.arrived ?: line.left
                    else -> line.left
                }))
            }
        }
    }
    var scanning by remember { mutableStateOf(false) }
    var scannedId by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    // What arrived, waiting for "Receive" in the are-you-sure (it can't be undone).
    var toReceive by remember(order.id, status) { mutableStateOf<Map<String, Double>?>(null) }
    val focusFor = remember(order.id) { order.lines.associate { it.itemId to FocusRequester() } }
    LaunchedEffect(scannedId) { scannedId?.let { id -> runCatching { focusFor[id]?.requestFocus() } } }
    // Which step takes amounts now: how many arrived (waiting for a delivery) or how many were counted.
    val askArrived = edit && (status == PurchaseStatus.ORDERED || status == PurchaseStatus.PARTLY_RECEIVED)
    val counting = edit && status == PurchaseStatus.ARRIVED
    fun typed(): Map<String, Double?> = order.lines.associate { it.itemId to amounts[it.itemId]?.replace(',', '.')?.toDoubleOrNull() }
    AppDialog("Order ${order.number}", onDismiss, subtitle = listOfNotNull(supplier?.name, from?.let { "from ${it.name}" }).joinToString(" · "), maxWidth = 780.dp, buttons = {
        when {
            !edit || !status.open -> KitButton("Close", onDismiss, style = ButtonStyle.SECONDARY)
            askArrived -> {
                if (status == PurchaseStatus.ORDERED) KitButton("Cancel order", { SupplierBook.cancel(order.id); onDismiss() }, style = ButtonStyle.DANGER_OUTLINE,
                    icon = Icons.Outlined.Cancel)
                else KitButton("Finish with what came", { SupplierBook.finish(order.id, today); onDismiss() }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.DoneAll)
                KitButton(if (status == PurchaseStatus.ORDERED) "Receive order" else "Receive delivery", {
                    val arrived = typed()
                    problem = when {
                        arrived.values.any { it == null } -> "Write how much arrived of every item (0 if it didn't come)."
                        arrived.values.all { (it ?: 0.0) <= 0.0 } -> "Nothing arrived? Write how much came of at least one item."
                        else -> null
                    }
                    if (problem == null) toReceive = arrived.mapValues { it.value ?: 0.0 }
                }, icon = Icons.Outlined.MoveToInbox)
            }
            counting -> {
                KitButton("Cancel order", { SupplierBook.cancel(order.id); onDismiss() }, style = ButtonStyle.DANGER_OUTLINE, icon = Icons.Outlined.Cancel)
                KitButton("Save count", {
                    val counted = typed()
                    problem = when {
                        counted.values.any { it == null } -> "Write what you counted for every item (0 if it didn't come this time)."
                        order.lines.any { (counted[it.itemId] ?: 0.0) > 0 && places[it.itemId] == null } -> "Choose where each item is put."
                        else -> null
                    }
                    if (problem == null) SupplierBook.saveCount(order.id, counted.mapValues { it.value ?: 0.0 }, places.toMap())
                }, icon = Icons.Outlined.Checklist)
            }
            status == PurchaseStatus.COUNTED -> {
                KitButton("Count again", { SupplierBook.recount(order.id) }, style = ButtonStyle.SECONDARY)
                // What came goes to stock now, each item into its own place; anything still missing can come later.
                val short = order.lines.any { (it.counted ?: 0.0) + 0.0001 < it.left }
                KitButton(if (short) "Add what came to stock" else "Confirm and add to stock", {
                    val lines = order.lines.filter { (it.counted ?: 0.0) > 0 }.mapNotNull { line ->
                        val place = placeOf(line) ?: return@mapNotNull null
                        DeliveryLine(line.itemId, place, trimmed(line.counted ?: 0.0), line.priceCents?.let(::amountText).orEmpty())
                    }
                    // Real stock receipts at the order's prices; the order itself is kept in SupplierBook (MOCK).
                    if (lines.isNotEmpty()) model.receiveDelivery(lines, "Order ${order.number}${supplier?.let { " from ${it.name}" } ?: ""}")
                    SupplierBook.confirmInStock(order.id, today)
                    onDismiss()
                }, icon = Icons.Outlined.Inventory2)
            }
        }
    }) {
        OrderSteps(order, today)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(when {
                askArrived -> "How much arrived?"
                counting -> "Count it and say where each item goes"
                else -> "Items"
            }, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
            if (askArrived) Text("All arrived", Modifier.clip(RoundedCornerShape(6.dp)).clickable { order.lines.forEach { amounts[it.itemId] = trimmed(it.left) } }
                .padding(horizontal = 8.dp, vertical = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
            if (counting) KitButton("Scan an item", { scanning = true }, icon = Icons.Outlined.QrCodeScanner, style = ButtonStyle.SECONDARY)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            order.lines.forEach { line ->
                val item = state.item(line.itemId)
                val unit = unitShort(item?.baseUnit)
                val scanned = line.itemId == scannedId
                val value = amounts[line.itemId]?.replace(',', '.')?.toDoubleOrNull()
                // Short or over: what arrived against what was still to come; what was counted against what arrived.
                val difference = when {
                    askArrived -> value?.let { it - line.left }
                    counting -> value?.let { it - (line.arrived ?: line.left) }
                    status == PurchaseStatus.COUNTED -> line.counted?.let { it - (line.arrived ?: line.left) }
                    else -> null
                }?.takeIf { kotlin.math.abs(it) > 0.0001 }
                val place = state.location(places[line.itemId] ?: placeOf(line))
                // On a phone the place and the amount go on their own row under the item, so nothing is squeezed.
                val narrow = LocalNarrowDialog.current
                val controls = askArrived || counting
                val amountBox: @Composable () -> Unit = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AmountBox(amounts[line.itemId].orEmpty(), unit, Modifier.focusRequester(focusFor.getValue(line.itemId)), highlight = scanned) {
                            amounts[line.itemId] = it; problem = null
                        }
                        Text(if (askArrived) "arrived" else "counted", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted)
                    }
                }
                val placeSelect: @Composable (Modifier) -> Unit = { m ->
                    Box(m) { SelectInput(place, shelves, { it.name }, { places[line.itemId] = it.id; problem = null }, icon = Icons.Outlined.MoveToInbox, compact = true) }
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (scanned) Kit.GreenSoft else Color.White)
                        .border(if (scanned) 1.5.dp else 1.dp, if (scanned) Kit.Green else Kit.RowBorder, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item != null) StockItemThumb(item, 40.dp) else ItemThumb(null, null, 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item?.name ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(buildString {
                            append("ordered ${trimmed(line.quantity)} $unit")
                            if (line.received > 0) append(" · ${trimmed(line.received)} in stock")
                            if (status == PurchaseStatus.ARRIVED || status == PurchaseStatus.COUNTED) line.arrived?.let { append(" · ${trimmed(it)} arrived") }
                            line.priceCents?.let { append(" · ${moneyText(it, null)} / $unit") }
                        }, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // Where this item goes in your stock (chosen while counting).
                        if (counting && !narrow) {
                            placeSelect(Modifier.width(220.dp))
                        } else if (!counting && place != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                PlaceIconImage(place, Modifier.size(18.dp))
                                Text(place.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Ink, maxLines = 1)
                            }
                        }
                    }
                    difference?.let {
                        Text("${if (it > 0) "+" else ""}${trimmed(it)} $unit", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            color = if (it < 0) Kit.Danger else Kit.Amber)
                    }
                    when {
                        controls && narrow -> Unit
                        controls -> amountBox()
                        status == PurchaseStatus.PARTLY_RECEIVED || status == PurchaseStatus.RECEIVED -> Column(horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${trimmed(line.received)} / ${trimmed(line.quantity)} $unit", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                color = if (line.left <= 0.0001) Kit.Green else Kit.Amber)
                            Text(if (line.left <= 0.0001) "all in stock" else "in stock so far", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted)
                        }
                        status == PurchaseStatus.COUNTED -> Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${trimmed(line.counted ?: 0.0)} $unit", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                            Text("counted", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted)
                        }
                        else -> Text(line.cents?.let { moneyText(it, null) } ?: "–", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                    }
                }
                if (controls && narrow) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (counting) placeSelect(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                        amountBox()
                    }
                }
                }
            }
        }
        HorizontalDivider(color = Kit.RowBorder)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
            Text(moneyText(order.totalCents, null), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink)
        }
        problem?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger) }
    }
    if (scanning) ScanBarcodeDialog("Scan an item", "Its line on this order is picked, ready for the amount you counted.", onCode = { code ->
        val line = order.lines.firstOrNull { line ->
            val item = state.item(line.itemId)
            val link = SupplierBook.link(order.supplierId, line.itemId)
            listOfNotNull(item?.barcode, item?.code, link?.code).any { normalizeBarcode(it).equals(code, ignoreCase = true) }
        }
        scanning = false
        if (line == null) problem = "Nothing on this order has the code $code." else { problem = null; scannedId = line.itemId }
    }, onDismiss = { scanning = false })
    toReceive?.let { arrived ->
        ConfirmDialog(if (status == PurchaseStatus.ORDERED) "Receive order ${order.number}?" else "Receive this delivery?", "You can't undo this.", "Receive",
            onConfirm = { SupplierBook.markArrived(order.id, arrived, today); toReceive = null }, onDismiss = { toReceive = null })
    }
}

/** The four steps as a bar that fills: ordered → order received → counted & put in place → confirmed in stock. */
@Composable
internal fun OrderSteps(order: PurchaseOrder, today: LocalDate) {
    val step = order.status.step
    val cancelled = order.status == PurchaseStatus.CANCELLED
    // "today" / "yesterday" read on their own; other days get "on": "on Thu 29 Oct".
    fun on(date: LocalDate) = dayLabel(date, today).let { if (it in setOf("today", "yesterday", "tomorrow")) it else "on $it" }
    val dates = listOf(
        on(order.orderedOn),
        order.arrivedOn?.let { on(it) } ?: order.expectedOn?.let { if (order.late(today)) "was due ${dayLabel(it, today)}" else "due ${dayLabel(it, today)}" },
        if (step >= 3) "done" else null,
        order.receivedOn?.let { on(it) }
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).border(1.dp, Kit.RowBorder, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cancelled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Cancel, null, Modifier.size(18.dp), tint = Kit.Grey)
                Text("This order was cancelled.", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Muted)
            }
            return@Column
        }
        if (order.status == PurchaseStatus.PARTLY_RECEIVED) {
            val complete = order.lines.count { it.left <= 0.0001 }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Inventory2, null, Modifier.size(18.dp), tint = Kit.Amber)
                Text("Part of this order is in stock ($complete of ${order.lines.size} items complete). The rest comes with the next delivery.",
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            PurchaseSteps.forEachIndexed { index, name ->
                val done = index < step
                val current = index == step
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // The bar fills from the left as the steps are done: a done step fills up to it and on to halfway to the next
                        // step, so the green always ends in the middle before the step that comes next (user rule).
                        Box(Modifier.weight(1f).height(4.dp).background(if (index == 0) Color.Transparent else if (done) Kit.Green else Kit.Border))
                        Box(Modifier.size(28.dp).clip(CircleShape).background(when { done -> Kit.Green; current -> Color.White; else -> Kit.Tint })
                            .border(2.dp, if (done || current) Kit.Green else Kit.Border, CircleShape), Alignment.Center) {
                            if (done) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Color.White)
                            else Text("${index + 1}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (current) Kit.Green else Kit.Muted)
                        }
                        Box(Modifier.weight(1f).height(4.dp).background(if (index == PurchaseSteps.lastIndex) Color.Transparent else if (done) Kit.Green else Kit.Border))
                    }
                    Text(name, fontFamily = Inter(), fontWeight = if (done || current) FontWeight.Bold else FontWeight.SemiBold, fontSize = 12.sp,
                        color = if (done || current) Kit.Ink else Kit.Muted, maxLines = 2, textAlign = TextAlign.Center)
                    dates[index]?.takeIf { done || (index == 1 && current) }?.let {
                        Text(it, fontFamily = Inter(), fontSize = 10.5.sp, color = if (order.late(today) && index == 1) Kit.Danger else Kit.Muted, maxLines = 1,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteTile(icon: ImageVector, label: String, value: String, modifier: Modifier, strong: Boolean = false) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(if (strong) Kit.GreenSoft else Kit.Canvas)
            .border(1.dp, if (strong) Kit.Green.copy(alpha = 0.35f) else Kit.RowBorder, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (strong) Kit.Green else Kit.Muted)
        Column {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
            Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Cents as an amount to type: 320 → "3.20". */
private fun amountText(cents: Long): String = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

// ---- Their price list ----

/**
 * Loads a supplier's price list into their catalog, the way purchasing systems do it: paste the rows from a
 * spreadsheet or pick a CSV file (a template can be saved), check which column is which (guessed from the header),
 * look at the first rows and how many are new or updated, then load.
 */
@Composable
private fun ImportCatalogDialog(supplier: Supplier, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val table = remember(text) { parseTable(text) }
    // A first row of header words gives the columns; otherwise the usual order is assumed.
    val header = table.firstOrNull()?.takeIf { row -> row.count { CatalogField.guess(it) != null } >= 2 }
    val columnCount = table.maxOfOrNull { it.size } ?: 0
    val fields = remember(text) {
        mutableStateListOf<CatalogField>().apply {
            val defaults = listOf(CatalogField.NAME, CatalogField.CODE, CatalogField.UNIT, CatalogField.PACK, CatalogField.PRICE, CatalogField.CATEGORY)
            repeat(columnCount) { i -> add(header?.getOrNull(i)?.let(CatalogField::guess) ?: if (header == null) defaults.getOrElse(i) { CatalogField.SKIP } else CatalogField.SKIP) }
        }
    }
    val dataRows = if (header != null) table.drop(1) else table
    val rows = dataRows.map { cells -> fields.withIndex().filter { it.value != CatalogField.SKIP }.associate { (i, f) -> f to cells.getOrElse(i) { "" } } }
    val existing = SupplierBook.catalogOf(supplier.id)
    val named = rows.filter { !it[CatalogField.NAME].isNullOrBlank() }
    val updates = named.count { row ->
        val code = row[CatalogField.CODE].orEmpty().trim()
        existing.any { (code.isNotEmpty() && it.code.equals(code, true)) || (code.isEmpty() && it.name.equals(row[CatalogField.NAME]!!.trim(), true)) }
    }
    val picker = rememberFilePickerLauncher(type = FileKitType.File(setOf("csv", "tsv", "txt"))) { file ->
        if (file == null) return@rememberFilePickerLauncher
        scope.launch {
            val bytes = runCatching { file.readBytes() }.getOrNull()
            problem = when {
                bytes == null -> "This file couldn't be read. Try another one."
                bytes.size > 2_000_000 -> "This file is too big for a price list (over 2 MB)."
                else -> null
            }
            if (problem == null && bytes != null) text = bytes.decodeToString().removePrefix("﻿")
        }
    }
    AppDialog("Load their price list", onDismiss, subtitle = supplier.name, maxWidth = 820.dp, buttons = {
        KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
        KitButton(if (named.isEmpty()) "Load products" else "Load ${count(named.size, "product")}", {
            if (fields.none { it == CatalogField.NAME }) { problem = "Choose which column is the product name."; return@KitButton }
            val result = SupplierBook.importCatalog(supplier.id, rows)
            onDone("Price list loaded: ${result.added} new, ${result.updated} updated" + if (result.skipped > 0) ", ${result.skipped} without a name left out" else "")
        }, icon = Icons.Outlined.UploadFile, enabled = named.isNotEmpty())
    }) {
        Tip("Copy the rows from their spreadsheet (with the header row) and paste them here, or pick a CSV file. Loading it again updates the prices by their code.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            KitButton("Pick a CSV file", { picker.launch() }, icon = Icons.Outlined.UploadFile, style = ButtonStyle.SECONDARY)
            KitButton("Save a template", {
                scope.launch {
                    saved = runCatching {
                        "Saved to " + saveTextFile("price-list-template.csv", "text/csv",
                            "Name,Code,Unit,Pack size,Price,Category\nMussels live,ADR-2001,kg,5 kg net,6.40,Fish\nSquid cleaned,ADR-2014,kg,2 kg box,11.90,Fish\n")
                    }.getOrElse { "Couldn't save the template: ${it.message ?: "unknown problem"}" }
                }
            }, icon = Icons.Outlined.FileDownload, style = ButtonStyle.SECONDARY)
        }
        saved?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = if (it.startsWith("Couldn't")) Kit.Danger else Kit.Green) }
        TextInput(text, { text = it; problem = null }, label = "Paste their list here", placeholder = "Name\tCode\tUnit\tPack size\tPrice", singleLine = false, minLines = 5)
        if (columnCount > 0) {
            Text("Which column is which", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
            // Each column with its header (or first value) and what it is.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(columnCount) { i ->
                    Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text((header?.getOrNull(i) ?: dataRows.firstOrNull()?.getOrNull(i)).orEmpty().ifBlank { "Column ${i + 1}" }, fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        SelectInput(fields.getOrNull(i), CatalogField.entries, { it.label }, { fields[i] = it; problem = null }, compact = true)
                    }
                }
            }
            Text("${count(named.size, "product")} · ${named.size - updates} new · $updates updated" +
                (dataRows.size - named.size).let { if (it > 0) " · $it without a name left out" else "" },
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
            // The first rows as they'll be loaded.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp))) {
                val shownFields = listOf(CatalogField.NAME, CatalogField.CODE, CatalogField.PACK, CatalogField.UNIT, CatalogField.PRICE).filter { it in fields }
                Row(Modifier.fillMaxWidth().background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    shownFields.forEach { f ->
                        Text(f.label.uppercase(), Modifier.weight(if (f == CatalogField.NAME) 2f else 1f), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, letterSpacing = 0.6.sp, color = Kit.Muted, maxLines = 1)
                    }
                }
                named.take(6).forEach { row ->
                    HorizontalDivider(color = Kit.RowBorder)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        shownFields.forEach { f ->
                            val value = row[f].orEmpty()
                            Text(if (f == CatalogField.PRICE) priceToCents(value)?.let { moneyText(it, null) } ?: value.ifBlank { "–" } else value.ifBlank { "–" },
                                Modifier.weight(if (f == CatalogField.NAME) 2f else 1f), fontFamily = Inter(),
                                fontWeight = if (f == CatalogField.NAME) FontWeight.SemiBold else FontWeight.Normal, fontSize = 12.sp, color = Kit.Ink, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (named.size > 6) Text("and ${named.size - 6} more", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontFamily = Inter(), fontSize = 11.sp,
                    color = Kit.Muted)
            }
        }
        problem?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger) }
    }
}

/** Links a product from their list to one of your stock items (you start buying it from them). */
@Composable
private fun LinkProductDialog(product: CatalogProduct, state: InventoryState, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var picked by remember { mutableStateOf(product.linkedItemId) }
    AppDialog("Link to one of your items", onDismiss, subtitle = product.name, maxWidth = 620.dp, buttons = {
        KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
        KitButton("Link", {
            val itemId = picked ?: return@KitButton
            SupplierBook.linkProduct(product.id, itemId)
            onDone("${product.name} is now linked to ${state.item(itemId)?.name ?: "your item"}")
        }, icon = Icons.Outlined.AddLink, enabled = picked != null)
    }) {
        Tip("Pick the stock item this product is. Its code and price go on the item for this supplier, and you can order it from them.")
        ItemPicker(state, setOfNotNull(picked), emptySet()) { id -> picked = if (picked == id) null else id }
    }
}
