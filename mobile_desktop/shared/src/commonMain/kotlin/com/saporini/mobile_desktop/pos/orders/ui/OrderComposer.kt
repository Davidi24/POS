package com.saporini.mobile_desktop.pos.orders.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import kotlinx.coroutines.launch

@Composable internal fun OrderComposer(model: OrdersScreenModel, order: Order, onBack: () -> Unit) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var menus by remember { mutableStateOf(emptyList<OrderCatalogMenu>()) }
    var menu by remember { mutableStateOf<OrderCatalogMenu?>(null) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var sectionId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var choices by remember { mutableStateOf<OrderItemChoices?>(null) }
    var editingLine by remember { mutableStateOf<OrderLineItem?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var showCart by remember { mutableStateOf(false) }
    var confirmSend by remember { mutableStateOf(false) }
    suspend fun loadMenus() {
        loading = true; error = null
        try {
            val catalog = requireNotNull(model.catalog)
            var page = 0; val all = mutableListOf<OrderCatalogMenu>()
            do { val result = catalog.getMenus(page, 100).getOrThrow(); all.addAll(result.items); page++ } while(result.hasNext)
            menus = all; menuId = all.firstOrNull()?.id
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { error = e.message } finally { loading = false }
    }
    LaunchedEffect(state.scope) { loadMenus() }
    LaunchedEffect(menuId) {
        menu = null; sectionId = null
        if(menuId != null) { loading = true; model.catalog!!.getMenu(menuId!!).fold({ menu = it; sectionId = it.sections.orEmpty().firstOrNull { s -> s.active }?.id }, { error = it.message }); loading = false }
    }
    fun customize(itemId: String, line: OrderLineItem? = null) {
        if(choosing || state.isSaving || state.needsReconciliation) return
        choosing = true
        scope.launch { try {
            val candidates = if(line == null) listOfNotNull(menuId) else (listOfNotNull(menuId) + menus.map { it.id }).distinct()
            var found: OrderItemChoices? = null
            for(candidate in candidates) {
                val result = model.catalog!!.getItemChoices(candidate, itemId)
                if(result.isSuccess) { found = result.getOrThrow(); break }
            }
            if(found == null) error = "This item is no longer in an available menu. You can void it or add another item."
            else { editingLine = line; choices = found }
        } finally { choosing = false } }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 900.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text("‹  Orders") }; OrderText(order.orderNumber, 22, true, modifier = Modifier.weight(1f)); OrderBadge(order.status.label())
            }
            OrderText("${order.tableName ?: order.tableNumber?.let { "Table $it" } ?: order.orderType.label()} · ${order.guestCount ?: 1} guests", color = OrderMuted)
            (error ?: state.error?.message)?.let { OrderError(it) { error = null; model.clearError() } }
            state.refreshWarning?.let { OrderError(it, model::clearError) }
            if(loading || choosing || state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth(), color = OrderGreen)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrderChoice("Menu", menus.firstOrNull { it.id == menuId }?.name ?: "Choose", menus.map { it.id to it.name }, { menuId = it })
                    OrderField("Search menu items", search, { search = it })
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        menu?.sections.orEmpty().filter { it.active }.sortedBy { it.displayOrder }.forEach { section -> OrderChip(sectionId == section.id, { sectionId = section.id }, label = { Text(section.name) }) }
                    }
                    val catalogItems = menu?.sections.orEmpty().filter { it.active && (it.id == sectionId || search.isNotBlank()) }.flatMap { it.items.orEmpty() }.filter { it.name.contains(search, true) }
                    if(!loading && catalogItems.isEmpty()) { OrderText("No items found", color = OrderMuted); TextButton({ scope.launch { loadMenus() } }) { Text("Reload menus") } }
                    LazyVerticalGrid(GridCells.Adaptive(if(phone) 155.dp else 200.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(catalogItems, key = { it.id }) { item -> Surface(onClick = { customize(item.id) }, enabled = item.available && !choosing && !state.isSaving && !state.needsReconciliation, shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, OrderBorder)) {
                            Column(Modifier.padding(16.dp).heightIn(min = 125.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OrderText(item.name, 16, true); item.description?.let { OrderText(it.take(85), 12, color = OrderMuted) }
                                Spacer(Modifier.weight(1f)); OrderText(item.basePrice.money(order.currency), bold = true, color = OrderGreen)
                                OrderText(if(item.available) "+ Add item" else "Unavailable", 12, color = OrderMuted)
                            }
                        } }
                    }
                }
                if(!phone) Surface(Modifier.width(340.dp).fillMaxHeight(), shape = RoundedCornerShape(14.dp), color = Color.White) {
                    OrderCart(order, model, { line -> customize(line.menuItemId, line) })
                }
            }
            if(phone) OrderButton("View order · ${order.lineItems.orEmpty().filter { it.active }.sumOf { it.quantity }} items · ${order.total.money(order.currency)}", modifier = Modifier.fillMaxWidth()) { showCart = true }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onBack, Modifier.weight(1f), enabled = !state.isSaving) { Text("Done · saved") }
                OrderButton("Send to kitchen", !state.isSaving && !state.needsReconciliation && order.lineItems.orEmpty().any { it.awaitingKitchen }, Modifier.weight(1f)) { confirmSend = true }
            }
        }
    }
    if(showCart) OrderModal("Current order", { showCart = false }, state.isSaving) { OrderCart(order, model, { line -> showCart = false; customize(line.menuItemId,line) }, Modifier.heightIn(max = 600.dp)) }
    if(choices != null) OrderItemForm(choices!!, editingLine, order, model) { choices = null; editingLine = null }
    if(confirmSend) OrderActionForm("Send to kitchen", model, order) { confirmSend = false }
}

@Composable private fun OrderCart(order: Order, model: OrdersScreenModel, onEdit: (OrderLineItem) -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState(); val scope = rememberCoroutineScope()
    var voidId by remember { mutableStateOf<String?>(null) }
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OrderText("Current order", 20, true)
        if(order.lineItems.isNullOrEmpty()) OrderText("Choose items from the menu to start.", color = OrderMuted)
        order.lineItems.orEmpty().filter { it.active }.forEach { line ->
            OrderText(line.itemNameSnapshot, bold = true)
            line.variantNameSnapshot?.let { OrderText(it, 12, color = OrderMuted) }
            line.options.orEmpty().forEach { OrderText("+ ${it.optionNameSnapshot} × ${it.quantity}", 12, color = OrderMuted) }
            line.notes?.let { OrderText(it, 12, color = OrderMuted) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if(line.status == OrderLineItemStatus.PENDING) {
                    TextButton({ scope.launch { model.operations.updateItemQuantity(order.id,line.id,OrderLineItemQuantityInput(line.quantity - 1)) } }, enabled = line.quantity > 1 && !state.isSaving && !state.needsReconciliation) { Text("−") }
                    OrderText("${line.quantity}", bold = true)
                    TextButton({ scope.launch { model.operations.updateItemQuantity(order.id,line.id,OrderLineItemQuantityInput(line.quantity + 1)) } }, enabled = !state.isSaving && !state.needsReconciliation) { Text("+") }
                } else OrderBadge(line.status.label())
                Spacer(Modifier.weight(1f)); OrderText(line.lineTotal.money(order.currency), bold = true)
            }
            Row {
                if(line.status == OrderLineItemStatus.PENDING) TextButton({ onEdit(line) }, enabled = !state.isSaving) { Text("Customize") }
                if(state.can("ORDER_VOID")) TextButton({ voidId = line.id }, enabled = !state.isSaving) { Text("Remove / void") }
            }
            HorizontalDivider(color = OrderBorder)
        }
        OrderAmountRow("Total", order.total.money(order.currency), true)
        OrderText("Changes are saved to this order as you make them.", 12, color = OrderMuted)
    }
    if(voidId != null) OrderActionForm("Void item:$voidId", model, order) { voidId = null }
}

@Composable private fun OrderItemForm(choices: OrderItemChoices, line: OrderLineItem?, order: Order, model: OrdersScreenModel, onDismiss: () -> Unit) {
    val state by model.state.collectAsState(); val scope = rememberCoroutineScope()
    var variant by remember { mutableStateOf(line?.variantId ?: choices.item.variants.orEmpty().firstOrNull { it.active && it.isDefault }?.id) }
    var quantity by remember { mutableStateOf((line?.quantity ?: 1).toString()) }
    var notes by remember { mutableStateOf(line?.notes.orEmpty()) }
    var selected by remember { mutableStateOf(line?.options.orEmpty().associate { it.optionItemId to it.quantity }) }
    var error by remember { mutableStateOf<String?>(null) }
    OrderModal(if(line == null) "Add ${choices.item.name}" else "Edit ${choices.item.name}", onDismiss, state.isSaving) {
        if(choices.item.variants.orEmpty().any { it.active }) OrderChoice("Variant", choices.item.variants.orEmpty().firstOrNull { it.id == variant }?.name ?: "Standard", listOf("" to "Standard") + choices.item.variants.orEmpty().filter { it.active }.map { it.id to "${it.name} (${it.priceDelta.money(order.currency)})" }, { variant = it.takeIf(String::isNotBlank) })
        choices.groups.forEach { group ->
            OrderText(group.group.name, 17, true)
            OrderText("Choose ${group.minimum}–${group.maximum ?: "any"}", 12, color = OrderMuted)
            group.availableChoices.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable { selected = if(option.id in selected) selected - option.id else selected + (option.id to 1) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(option.id in selected, { checked -> selected = if(checked) selected + (option.id to 1) else selected - option.id })
                    OrderText(option.name, modifier = Modifier.weight(1f)); OrderText(option.priceDelta.money(order.currency), 12)
                }
            }
        }
        OrderField("Quantity", quantity, { quantity = it }); OrderField("Item notes", notes, { notes = it }, singleLine = false)
        error?.let { OrderError(it) }
        OrderButton(if(state.isSaving) "Saving…" else if(line == null) "Add item" else "Save changes", !state.isSaving && !state.needsReconciliation, Modifier.fillMaxWidth()) {
            val input = runCatching { choices.toLineItemInput(quantity.toIntOrNull() ?: 0, variant, selected.map { CreateOrderItemOptionInput(it.key,it.value) }, notes) }
            input.fold({ value -> scope.launch {
                val result = if(line == null) model.operations.addItem(order.id,value) else model.operations.updateItem(order.id,line.id,value)
                result.fold({ onDismiss() }, { error = it.message })
            } }, { error = it.message })
        }
    }
}
