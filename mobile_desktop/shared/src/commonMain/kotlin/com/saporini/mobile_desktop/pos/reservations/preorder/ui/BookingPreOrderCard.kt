package com.saporini.mobile_desktop.pos.reservations.preorder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderCatalogRepository
import com.saporini.mobile_desktop.pos.reservations.preorder.MAX_PRE_ORDER_NOTES
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderScreenModel
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderState
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import org.koin.compose.koinInject

internal fun preOrderStatusLabel(status: String): String = when (status) {
    "SCHEDULED" -> "Waiting for the kitchen"
    "SENT" -> "In the kitchen"
    "CANCELLED" -> "Cancelled"
    "FORFEITED" -> "Not used"
    else -> status.lowercase().replaceFirstChar { it.uppercase() }
}

internal fun preOrderStatusColor(status: String): Color = when (status) {
    "SCHEDULED" -> Kit.Purple
    "SENT" -> Kit.Green
    else -> Kit.Grey
}

/**
 * The booking's food ordered ahead, as a card in the booking panel: what was ordered and when it goes to the kitchen,
 * with buttons to take one, change it, send it early or cancel it. Owns its model while it shows.
 */
@Composable
fun BookingPreOrderCard(reservationId: String, guests: Int, bookingOpen: Boolean, modifier: Modifier = Modifier) {
    val model = koinInject<PreOrderScreenModel>()
    val menus = koinInject<MenuRepository>()
    val catalog = koinInject<OrderCatalogRepository>()
    val dishes = remember(menus, catalog) { CatalogPreOrderDishes(menus, catalog) }
    DisposableEffect(model) { onDispose { model.onDispose() } }
    LaunchedEffect(reservationId) { model.open(reservationId) }
    val state by model.state.collectAsState()
    PreOrderCardContent(state, model, dishes, guests, bookingOpen, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PreOrderCardContent(
    state: PreOrderState,
    model: PreOrderScreenModel,
    dishes: PreOrderDishes,
    guests: Int,
    bookingOpen: Boolean,
    modifier: Modifier = Modifier
) {
    if (!state.canRead) return
    val preOrder = state.preOrder
    // Food can only be ordered ahead for pending or confirmed bookings; past ones only show what was ordered.
    if (preOrder == null && !bookingOpen && !state.loading) return
    val zone = RestaurantTime.zone
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Kit.Purple.copy(alpha = 0.12f)), Alignment.Center) {
                    Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(18.dp), tint = Kit.Purple)
                }
                Text("Food ordered ahead", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
                if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), color = Kit.Green, strokeWidth = 2.dp)
                else preOrder?.let { StatusPill(preOrderStatusLabel(it.status), preOrderStatusColor(it.status)) }
            }
            state.error?.takeIf { !state.editing && !state.confirmCancel }?.let { MessageBar(it, MessageKind.ERROR, onDismiss = model::clearMessages) }
            state.notice?.let { MessageBar(it, MessageKind.SUCCESS, onDismiss = model::clearMessages) }
            if (preOrder == null || preOrder.items.isEmpty()) {
                if (!state.loading) Text(
                    "Nothing ordered ahead. Take the guests' food now and the kitchen gets it a set time before they come.",
                    fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted
                )
                if (state.canPlace && bookingOpen && !state.loading) KitButton("Take a pre-order", model::startEdit, icon = Icons.Outlined.Add, style = ButtonStyle.SECONDARY)
            } else {
                PreOrderSummary(preOrder)
                preOrder.sendAt?.takeIf { preOrder.open }?.let {
                    Text("Goes to the kitchen ${dateTimeText(it, zone)}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Purple)
                }
                if (preOrder.open) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.canPlace && bookingOpen) KitButton("Change", model::startEdit, icon = Icons.Outlined.Edit, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                        if (state.canSend) KitButton("Send now", model::sendNow, icon = Icons.Outlined.LocalFireDepartment, style = ButtonStyle.SECONDARY,
                            loading = state.saving)
                        if (state.canCancel) KitButton("Cancel", model::askCancel, icon = Icons.Outlined.Block, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                    }
                } else if (preOrder.status == "CANCELLED" && state.canPlace && bookingOpen) {
                    KitButton("Take a new pre-order", model::startEdit, icon = Icons.Outlined.Add, style = ButtonStyle.SECONDARY)
                }
            }
        }
    }
    if (state.editing) PreOrderEditorDialog(state, model, dishes, guests)
    if (state.confirmCancel) {
        ConfirmDialog("Cancel the pre-order?", "The kitchen won't get it. If the guest paid for it online, the money goes back to them.",
            "Cancel pre-order", model::confirmCancel, model::dismissCancel, danger = true, busy = state.saving) {
            state.error?.let { MessageBar(it, MessageKind.ERROR) }
            TextInput(state.cancelReason, model::cancelReason, label = "Reason", optional = true, maxLength = MAX_PRE_ORDER_NOTES,
                placeholder = "The guests changed their plans")
        }
    }
}

@Composable
private fun PreOrderSummary(preOrder: PreOrderDto) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8F9F7)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        preOrder.items.take(6).forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text("${item.quantity}×", Modifier.width(28.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(item.itemName, item.variantName).joinToString(" · "), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val extras = item.options.map { (if (it.quantity > 1) "${it.quantity}× " else "") + (it.name ?: "choice") } +
                        listOfNotNull(item.notes?.takeIf { it.isNotBlank() }?.let { "“$it”" })
                    if (extras.isNotEmpty()) Text(extras.joinToString(", "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                item.lineTotal?.let { Text(money(it, preOrder.currency), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Ink) }
            }
        }
        if (preOrder.items.size > 6) Text("and ${preOrder.items.size - 6} more", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        Row {
            Text(listOfNotNull(preOrder.paymentStatus?.let { if (it == "PAID") "Paid online" else null }, preOrder.notes?.takeIf { it.isNotBlank() }?.let { "“$it”" })
                .joinToString(" · "), Modifier.weight(1f), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            preOrder.total?.let { Text(money(it, preOrder.currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink) }
        }
    }
}
