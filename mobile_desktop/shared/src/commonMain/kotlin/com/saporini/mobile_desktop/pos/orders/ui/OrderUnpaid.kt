package com.saporini.mobile_desktop.pos.orders.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// Guests who leave without paying (user, 2026-11-01): the order is voided as unpaid, its table freed, and it can be
// reopened later to take the payment. Nothing stays open waiting.
internal val Order.closedUnpaid get() = status == OrderStatus.VOIDED && unpaidAt != null
internal val OrderSummary.closedUnpaid get() = status == OrderStatus.VOIDED && unpaidAt != null
// Still to be paid, so it can be closed as unpaid: a live order, or one reopened for its payment that wasn't paid after all.
internal val Order.canCloseUnpaid get() = (status == OrderStatus.OPEN || status == OrderStatus.DRAFT) && paymentStatus != OrderPaymentStatus.PAID &&
    (total.value.toDoubleOrNull() ?: 0.0) > 0.0

internal val UnpaidRed = Color(0xFFDC2626)

internal fun String.unpaidDay(): String = runCatching {
    val at = Instant.parse(this).toLocalDateTime(TimeZone.currentSystemDefault())
    val month = at.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    "${at.day} $month ${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
}.getOrDefault(take(16).replace('T', ' '))

@Composable
internal fun CloseUnpaidDialog(order: Order, model: OrdersScreenModel, onDismiss: () -> Unit, onDone: (Order) -> Unit) {
    CloseUnpaidDialog(order, { reason -> model.operations.closeUnpaid(order.id, reason) }, onDismiss, onDone)
}

// Staff write the reason: no ready-made ones, a "will pay later" choice made waiters think guests may pay later (user, 2026-11-01).
@Composable
internal fun CloseUnpaidDialog(order: Order, close: suspend (String) -> Result<Order>, onDismiss: () -> Unit, onDone: (Order) -> Unit) {
    val scope = rememberCoroutineScope()
    var reason by remember(order.id) { mutableStateOf("") }
    var problem by remember(order.id) { mutableStateOf<String?>(null) }
    var saving by remember(order.id) { mutableStateOf(false) }
    AppDialog(
        title = "Close ${order.orderNumber} as unpaid?",
        onDismiss = onDismiss,
        maxWidth = 480.dp,
        busy = saving,
        buttons = {
            KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY, enabled = !saving)
            KitButton(if (saving) "Closing…" else "Close as unpaid", {
                saving = true
                problem = null
                scope.launch {
                    close(reason.trim()).fold(
                        { closed -> onDone(closed) },
                        { problem = it.message ?: "Could not close the order" }
                    )
                    saving = false
                }
            }, style = ButtonStyle.DANGER, enabled = reason.isNotBlank() && !saving)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("The order is voided and its table is freed.", fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = OrderMuted)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (order.paymentStatus == OrderPaymentStatus.PARTIALLY_PAID) "Total (part paid)" else "Total",
                    fontFamily = Inter(), fontSize = 13.sp, color = OrderMuted, modifier = Modifier.weight(1f))
                Text(order.total.money(order.currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OrderInk)
            }
            TextInput(reason, { reason = it }, label = "Reason", required = true,
                placeholder = "What happened", maxLength = 2000, singleLine = false, minLines = 3, enabled = !saving)
            problem?.let { Text(it, fontFamily = Inter(), fontSize = 13.sp, color = UnpaidRed) }
        }
    }
}

// On an order closed as unpaid, or reopened for its payment: why, what's still to pay and when.
@Composable
internal fun UnpaidStrip(order: Order, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color(0xFFFEF2F2), border = BorderStroke(1.dp, Color(0xFFFECACA))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.MoneyOff, null, Modifier.size(20.dp), tint = UnpaidRed)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (order.closedUnpaid) "Closed as unpaid" else "Open for payment", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = UnpaidRed)
                val why = if (order.unpaidReason == "NO_ACTION") "No action taken" else order.unpaidNote?.takeIf { it.isNotBlank() }
                val detail = listOfNotNull(order.unpaidAt?.unpaidDay(), why).joinToString(" · ")
                if (detail.isNotEmpty()) Text(detail, fontFamily = Inter(), fontSize = 12.sp, color = OrderMuted, maxLines = 2)
            }
            order.unpaidAmount?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(it.money(order.currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = UnpaidRed)
                    Text("to pay", fontFamily = Inter(), fontSize = 11.sp, color = OrderMuted)
                }
            }
        }
    }
}
