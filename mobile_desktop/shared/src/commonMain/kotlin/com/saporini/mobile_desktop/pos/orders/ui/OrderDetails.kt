package com.saporini.mobile_desktop.pos.orders.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.pos.orders.domain.model.*

@Composable internal fun OrderDetails(order: Order, state: OrdersUiState, onEdit: () -> Unit, onAction: (String) -> Unit, modifier: Modifier = Modifier, onFull: (() -> Unit)? = null) {
    var activity by remember(order.id) { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Column(modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OrderText(order.orderNumber, 23, true, modifier = Modifier.weight(1f))
            Box {
                TextButton({ more = true }) { Text("•••") }
                DropdownMenu(more, { more = false }) {
                    val actions = buildList {
                        if(order.editable && state.can("ORDER_UPDATE")) { add("Edit information"); add("Order note"); add("Mark ready"); add("Mark fulfilled") }
                        if(order.editable && state.can("ORDER_DISCOUNT_APPLY")) add("Apply discount")
                        if(order.editable && state.can("ORDER_TRANSFER")) { add("Move table"); add("Split order"); add("Merge orders") }
                        if(order.editable && state.can("ORDER_CANCEL")) add("Cancel order")
                        if(order.editable && state.can("ORDER_VOID")) add("Void order")
                        if(order.editable && state.can("ORDER_CLOSE")) add("Close order")
                        if(order.status == OrderStatus.CLOSED && state.can("ORDER_REOPEN")) add("Reopen order")
                    }
                    actions.forEach { action -> DropdownMenuItem(text = { Text(action) }, enabled = !state.isSaving, onClick = { more = false; onAction(action) }) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OrderBadge(order.status.label()); if (order.status.showsFulfillmentProgress) OrderBadge(order.fulfillmentStatus.label()) }
        OrderText("${order.tableName ?: order.tableNumber?.let { "Table $it" } ?: order.orderType.label()} · ${order.guestCount ?: 1} guests")
        OrderText("Opened ${order.openedAt.orderTime()} · ${order.orderType.label()}", 12, color = OrderMuted)
        order.customerName?.let { Surface(color = OrderBackground, shape = RoundedCornerShape(10.dp)) { OrderText(it, bold = true, modifier = Modifier.fillMaxWidth().padding(14.dp)) } }
        order.reservationCode?.let { OrderText("Reservation $it", color = OrderMuted) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrderChip(!activity, { activity = false }, label = { Text("Items (${order.lineItems.orEmpty().filter { it.active }.sumOf { it.quantity }})") })
            OrderChip(activity, { activity = true }, label = { Text("Activity") })
        }
        if(activity) {
            if(order.events.isNullOrEmpty()) OrderText("No activity recorded", color = OrderMuted)
            order.events.orEmpty().sortedByDescending { it.createdAt }.forEach { event ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    OrderText(event.eventType.label(), bold = true)
                    event.note?.let { OrderText(it, 13) }; OrderText(event.createdAt.replace('T',' ').take(16), 12, color = OrderMuted)
                    HorizontalDivider(color = OrderBorder)
                }
            }
        } else {
            if(order.lineItems.isNullOrEmpty()) OrderText("This draft has no items yet. Add items to get started.", color = OrderMuted)
            order.lineItems.orEmpty().forEach { line ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        OrderText("${line.quantity} × ${line.itemNameSnapshot}", bold = true, modifier = Modifier.weight(1f)); OrderText(line.lineTotal.money(order.currency), bold = true)
                    }
                    line.variantNameSnapshot?.let { OrderText(it, 12, color = OrderMuted) }
                    line.options.orEmpty().forEach { OrderText("+ ${it.optionNameSnapshot} × ${it.quantity}", 12, color = OrderMuted) }
                    line.notes?.let { OrderText(it, 13, color = OrderMuted) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrderBadge(line.status.label())
                        if(order.editable && line.active && state.can("ORDER_VOID")) TextButton({ onAction("Void item:${line.id}") }, enabled = !state.isSaving) { Text("Void") }
                    }
                    HorizontalDivider(color = OrderBorder)
                }
            }
            order.notes?.takeIf { it.isNotBlank() }?.let { OrderText("Note: $it", color = OrderMuted) }
            OrderAmountRow("Subtotal", order.subtotal.money(order.currency))
            order.discounts.orEmpty().forEach { discount ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OrderText(discount.name, modifier = Modifier.weight(1f)); OrderText("− ${discount.amountApplied.money(order.currency)}")
                    if(order.editable && state.can("ORDER_DISCOUNT_APPLY")) TextButton({ onAction("Remove discount:${discount.id}") }, enabled = !state.isSaving) { Text("Remove") }
                }
            }
            OrderAmountRow(if(order.taxInclusive) "Tax (included)" else "Tax", order.taxTotal.money(order.currency))
            if(order.serviceChargeTotal.value.toDoubleOrNull() != 0.0) OrderAmountRow("Service charge", order.serviceChargeTotal.money(order.currency))
            HorizontalDivider(color = OrderBorder); OrderAmountRow("Total", order.total.money(order.currency), true)
        }
        if(order.editable && state.can("ORDER_UPDATE")) {
            OrderButton("+  Add or edit items", !state.isSaving && !state.needsReconciliation, Modifier.fillMaxWidth(), onEdit)
            if(order.lineItems.orEmpty().any { it.status == OrderLineItemStatus.PENDING }) OrderButton("Send pending items to kitchen", !state.isSaving && !state.needsReconciliation, Modifier.fillMaxWidth()) { onAction("Send to kitchen") }
        }
        if(onFull != null) OutlinedButton(onFull, Modifier.fillMaxWidth()) { Text("View full order  →") }
    }
}
@Composable internal fun OrderAmountRow(label: String, amount: String, total: Boolean = false) {
    Row(Modifier.fillMaxWidth()) { OrderText(label, if(total) 20 else 14, total, modifier = Modifier.weight(1f)); OrderText(amount, if(total) 20 else 14, total) }
}
