package com.saporini.mobile_desktop.pos.orders.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderCustomerChoice
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderReservationChoice
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import kotlinx.coroutines.launch
import kotlinx.datetime.*

@Composable internal fun OrderStartForm(model: OrdersScreenModel, onDismiss: () -> Unit, onCreated: () -> Unit, existing: Order? = null) {
    val state by model.state.collectAsState(); val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(existing?.orderType ?: OrderType.DINE_IN) }
    var table by remember { mutableStateOf(existing?.tableId) }
    var guestCount by remember { mutableStateOf((existing?.guestCount ?: 1).toString()) }
    var note by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var customer by remember { mutableStateOf(existing?.customerId) }
    var reservation by remember { mutableStateOf(existing?.reservationId) }
    var tables by remember { mutableStateOf(emptyList<LayoutTable>()) }
    var customers by remember { mutableStateOf(emptyList<OrderCustomerChoice>()) }
    var reservations by remember { mutableStateOf(emptyList<OrderReservationChoice>()) }
    var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        loading = true; error = null
        model.catalog?.getTables()?.fold({ tables = it.tables.filter { t -> t.active && t.mergedIntoTableId == null } }, { error = it.message })
        if(state.can("SETTINGS_READ")) {
            model.catalog?.getCustomers()?.fold({ customers = it }, { error = it.message })
            model.catalog?.getReservations()?.fold({ reservations = it }, { error = it.message })
        }
        loading = false
    }
    LaunchedEffect(Unit) { load() }
    OrderModal(if(existing == null) "Start an order" else "Edit order information", onDismiss, state.isSaving) {
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(existing == null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(OrderType.DINE_IN,OrderType.TAKEAWAY).forEach { value -> OrderChip(type == value, { type = value; if(type == OrderType.TAKEAWAY) { table = null; reservation = null } }, label = { Text(value.label()) }) }
        }
        if(type == OrderType.DINE_IN && existing == null) OrderChoice("Table", tables.firstOrNull { it.id == table }?.let { it.name ?: "Table ${it.tableNumber}" } ?: "Choose a table", tables.map { it.id to "${it.name ?: "Table ${it.tableNumber}"} · ${it.capacity} seats · ${it.status.label()}" }, { table = it })
        OrderField("Guests", guestCount, { guestCount = it })
        if(state.can("SETTINGS_READ") && existing == null) {
            OrderChoice("Customer", customers.firstOrNull { it.id == customer }?.fullName ?: "Walk-in guest", listOf("" to "Walk-in guest") + customers.map { it.id to it.fullName }, { customer = it.takeIf(String::isNotBlank) })
            if(type == OrderType.DINE_IN) OrderChoice("Reservation", reservations.firstOrNull { it.id == reservation }?.reservationCode ?: "None", listOf("" to "None") + reservations.map { it.id to "${it.reservationCode} · ${it.customerName ?: it.contactName.orEmpty()}" }, { id -> reservation = id.takeIf(String::isNotBlank); reservations.firstOrNull { it.id == id }?.let { customer = it.customerId; guestCount = it.partySize.toString() } })
        }
        OrderField("Order note (optional)", note, { note = it }, singleLine = false)
        error?.let { OrderError(it); TextButton({ scope.launch { load() } }) { Text("Reload choices") } }
        OrderButton(if(state.isSaving) "Saving…" else if(existing == null) "Create draft & add items" else "Save information", !state.isSaving && !loading && !state.needsReconciliation, Modifier.fillMaxWidth()) {
            val guests = guestCount.toIntOrNull()
            if(guests == null || guests < 1) error = "Enter a guest count greater than zero"
            else if(type == OrderType.DINE_IN && table == null && existing == null) error = "Select a table for this dine-in order"
            else scope.launch {
                val result = if(existing == null) model.operations.createOrder(CreateOrderInput(tableId = table, reservationId = reservation, customerId = customer, orderType = type, guestCount = guests, notes = note.takeIf { it.isNotBlank() }))
                else model.operations.updateOrder(existing.id, UpdateOrderInput(guestCount = guests, notes = note))
                result.fold({ onCreated() }, { error = it.message })
            }
        }
    }
}

@Composable internal fun OrderActionForm(action: String, model: OrdersScreenModel, order: Order?, onDismiss: () -> Unit) {
    if(action == "Edit information" && order != null) { OrderStartForm(model,onDismiss,onDismiss,order); return }
    val state by model.state.collectAsState(); val scope = rememberCoroutineScope()
    var note by remember(action) { mutableStateOf(if(action == "Order note") order?.notes.orEmpty() else "") }
    var error by remember(action) { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }; var value by remember { mutableStateOf("") }
    var discountType by remember { mutableStateOf(OrderDiscountType.PERCENTAGE) }
    var tableId by remember { mutableStateOf<String?>(null) }; var sourceId by remember { mutableStateOf<String?>(null) }
    var tables by remember { mutableStateOf(emptyList<LayoutTable>()) }
    var orders by remember { mutableStateOf(emptyList<OrderSummary>()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }; var preview by remember { mutableStateOf<OrderSplitPreview?>(null) }
    var from by remember { mutableStateOf(state.filter.from?.take(10).orEmpty()) }; var to by remember { mutableStateOf(state.filter.to?.take(10).orEmpty()) }
    var status by remember { mutableStateOf(state.filter.status) }; var loading by remember { mutableStateOf(false) }
    LaunchedEffect(action) {
        if(action == "Move table" || action == "Split order") { loading = true; model.catalog?.getTables()?.fold({ tables = it.tables.filter { t -> t.active && t.mergedIntoTableId == null } }, { error = it.message }); loading = false }
        if(action == "Merge orders") { loading = true; model.operations.getOpenOrders().fold({ orders = it.filter { o -> o.id != order?.id && o.currency == order?.currency } }, { error = it.message }); loading = false }
    }
    val title = action.substringBefore(':')
    OrderModal(title, onDismiss, state.isSaving) {
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when(title) {
            "Check last change" -> OrderText("Review the refreshed order and its activity before continuing. Do not repeat an action that already appears in the order.")
            "History filters" -> {
                OrderField("From date (YYYY-MM-DD)", from, { from = it }); OrderField("To date (YYYY-MM-DD)", to, { to = it })
                OrderChoice("Status", status?.label() ?: "All", listOf("" to "All") + OrderStatus.entries.map { it.name to it.label() }, { status = OrderStatus.entries.firstOrNull { s -> s.name == it } })
            }
            "Apply discount" -> {
                OrderField("Discount name", name, { name = it }); OrderChoice("Type", discountType.label(), listOf(OrderDiscountType.PERCENTAGE,OrderDiscountType.FIXED_AMOUNT).map { it.name to it.label() }, { discountType = OrderDiscountType.valueOf(it) }); OrderField(if(discountType == OrderDiscountType.PERCENTAGE) "Percentage" else "Amount (${order?.currency})", value, { value = it }); OrderField("Reason", note, { note = it }, singleLine = false)
            }
            "Move table" -> OrderChoice("Destination", tables.firstOrNull { it.id == tableId }?.let { it.name ?: "Table ${it.tableNumber}" } ?: "Choose a table", tables.filter { it.id != order?.tableId }.map { it.id to (it.name ?: "Table ${it.tableNumber}") }, { tableId = it })
            "Merge orders" -> {
                OrderText("Selected items will be moved into ${order?.orderNumber}. The source order will be voided. Orders already sent to the kitchen cannot be merged.")
                OrderChoice("Source order", orders.firstOrNull { it.id == sourceId }?.orderNumber ?: "Choose", orders.map { it.id to "${it.orderNumber} · ${it.total.money(it.currency)}" }, { sourceId = it })
            }
            "Split order" -> {
                OrderText("Choose complete lines to move to a new order. At least one line must remain. Only orders without kitchen history can be split.")
                order?.lineItems.orEmpty().filter { it.active }.forEach { line -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(line.id in selected, { checked -> selected = if(checked) selected + line.id else selected - line.id; preview = null }); OrderText("${line.quantity} × ${line.itemNameSnapshot}")
                } }
                OrderChoice("New table", tables.firstOrNull { it.id == tableId }?.tableNumber ?: "Same table", listOf("" to "Same table") + tables.map { it.id to (it.name ?: "Table ${it.tableNumber}") }, { tableId = it.takeIf(String::isNotBlank); preview = null })
                TextButton({ scope.launch { model.operations.splitPreview(order!!.id, OrderSplitInput(selected.toList(),targetTableId = tableId)).fold({ preview = it }, { error = it.message }) } }, enabled = selected.isNotEmpty()) { Text("Preview split") }
                preview?.let { OrderAmountRow("New order total", it.total.money(it.currency),true) }
            }
            "Order note" -> OrderField("Order note", note, { note = it }, singleLine = false)
            else -> {
                OrderText(when(title) { "Send to kitchen" -> "Send pending items from ${order?.orderNumber} to the kitchen?"; "Remove discount" -> "Remove this discount and recalculate the total?"; else -> "$title for ${order?.orderNumber}?" })
                if(title.contains("Void") || title.contains("Cancel")) OrderField("Reason (required)", note, { note = it }, singleLine = false)
            }
        }
        error?.let { OrderError(it) }
        OrderButton(if(state.isSaving) "Saving…" else if(title == "Check last change") "I checked the order" else "Confirm", !state.isSaving && !loading && (!state.needsReconciliation || title == "Check last change"), Modifier.fillMaxWidth()) {
            if(title == "History filters") {
                runCatching {
                    val start = from.takeIf(String::isNotBlank)?.let { LocalDate.parse(it).atStartOfDayIn(TimeZone.currentSystemDefault()).toString() }
                    val end = to.takeIf(String::isNotBlank)?.let { LocalDate.parse(it).plus(1,DateTimeUnit.DAY).atStartOfDayIn(TimeZone.currentSystemDefault()).minus(kotlin.time.Duration.parse("1ns")).toString() }
                    require(start == null || end == null || start <= end) { "End date must follow start date" }
                    model.setFilter(OrderListFilter(OrderListMode.HISTORY,start,end,status)); onDismiss()
                }.onFailure { error = it.message ?: "Enter valid dates" }
            } else if(title == "Check last change") { model.acknowledgeReconciliation(); onDismiss() }
            else if((title.contains("Void") || title.contains("Cancel")) && note.isBlank()) error = "Please enter a reason"
            else if(title == "Move table" && tableId == null) error = "Select a destination table"
            else if(title == "Merge orders" && sourceId == null) error = "Select a source order"
            else if(title == "Split order" && preview == null) error = "Preview the split before confirming"
            else scope.launch {
                try {
                    val id = requireNotNull(order).id; val op = model.operations; val request = OrderActionInput(reason = note.takeIf(String::isNotBlank))
                    val result: Result<*> = when(title) {
                        "Send to kitchen" -> op.sendToKitchen(id)
                        "Mark ready" -> op.markOrderReady(id)
                        "Mark fulfilled" -> op.fulfillOrder(id)
                        "Close order" -> op.closeOrder(id)
                        "Reopen order" -> op.reopenOrder(id)
                        "Cancel order" -> op.cancelOrder(id, request)
                        "Void order" -> op.voidOrder(id, request)
                        "Void item" -> op.voidItem(id,action.substringAfter(':'),request)
                        "Remove discount" -> op.deleteDiscount(id,action.substringAfter(':'))
                        "Order note" -> op.updateOrder(id,UpdateOrderInput(notes = note))
                        "Move table" -> op.transferOrderTable(id,OrderTransferTableInput(tableId!!))
                        "Merge orders" -> op.mergeOrders(id,OrderMergeInput(sourceId!!))
                        "Split order" -> op.splitOrder(id,OrderSplitInput(selected.toList(),targetTableId = tableId))
                        "Apply discount" -> {
                            require(name.isNotBlank()) { "Enter a discount name" }; require(Regex("[0-9]+(\\.[0-9]{1,2})?").matches(value)) { "Enter a valid discount value" }
                            require(discountType != OrderDiscountType.PERCENTAGE || value.toDouble() <= 100) { "Percentage cannot exceed 100" }
                            op.addDiscount(id,CreateOrderDiscountInput(name,discountType,OrderDecimal(value),note.takeIf(String::isNotBlank)))
                        }
                        else -> error("Unsupported order action")
                    }
                    result.fold({ onDismiss() }, { error = it.message })
                } catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(e: Exception) { error = e.message }
            }
        }
    }
}
