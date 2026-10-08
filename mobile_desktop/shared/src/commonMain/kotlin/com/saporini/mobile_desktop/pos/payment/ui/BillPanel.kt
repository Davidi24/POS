package com.saporini.mobile_desktop.pos.payment.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.payment.PaymentScreenModel
import com.saporini.mobile_desktop.pos.payment.PaymentUiState
import com.saporini.mobile_desktop.pos.payment.data.PaymentDto
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime

/** The bill as the guest sees it: dishes, extras, tax and total, then the payments already taken. */
@Composable
internal fun BillPanel(state: PaymentUiState, model: PaymentScreenModel, modifier: Modifier, scrollable: Boolean = true) {
    val summary = state.summary ?: return
    val receipt = state.receipt
    val currency = summary.currency
    val zone = RestaurantTime.zone
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column((if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Green), Alignment.Center) {
                    Text(receipt?.tableNumber?.take(4) ?: "–", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Order #${summary.orderNumber ?: "–"}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink)
                    Text(listOfNotNull(receipt?.guestCount?.let { if (it == 1) "1 guest" else "$it guests" }, receipt?.serverName?.let { "served by $it" },
                        receipt?.openedAt?.let { "opened ${timeText(it, zone)}" }).joinToString(" · "), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusPill(when (summary.orderStatus) { "CLOSED" -> "Closed"; "CANCELLED", "VOIDED" -> "Cancelled"; else -> "Open" },
                    when (summary.orderStatus) { "CLOSED" -> Kit.Green; "CANCELLED", "VOIDED" -> Kit.Danger; else -> Kit.Blue })
            }
            KitDivider()
            if (receipt == null) {
                Text(if (state.loadingReceipt) "Loading the dishes…" else "The dishes couldn't be loaded.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dishes", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
                    Text("${receipt.lines.filterNot { it.removed }.sumOf { it.quantity }} items", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                }
                receipt.lines.forEach { line ->
                    Row(Modifier.alpha(if (line.removed) 0.5f else 1f), verticalAlignment = Alignment.Top) {
                        Text("${line.quantity}", Modifier.width(26.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
                        Column(Modifier.weight(1f)) {
                            Text(line.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Kit.Ink,
                                textDecoration = if (line.removed) TextDecoration.LineThrough else null)
                            val extra = listOfNotNull(line.variant, line.options.takeIf { it.isNotEmpty() }?.joinToString(", "), line.notes?.let { "“$it”" })
                            if (extra.isNotEmpty()) Text(extra.joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                        }
                        Text(line.lineTotal.money(currency), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Kit.Ink)
                    }
                }
                KitDivider()
                SumLine("Subtotal", receipt.subtotal.money(currency))
                receipt.discounts.forEach { SumLine(it.name, "-${it.amount.money(currency)}", color = Kit.Green) }
                if (receipt.serviceChargeTotal.cents() > 0) SumLine("Service", receipt.serviceChargeTotal.money(currency))
                receipt.tax?.let { tax ->
                    SumLine("Tax ${plain(tax.rate.value)}%${if (tax.inclusive) " (included)" else ""}", tax.amount.money(currency))
                }
                if (summary.prepaidTotal.cents() > 0) SumLine("Paid ahead (booking)", "-${summary.prepaidTotal.money(currency)}", color = Kit.Green)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Total", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink)
                Text(summary.orderTotal.money(currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFFAD3F08))
            }
            if (summary.payments.isNotEmpty()) {
                Caption("Payments")
                summary.payments.forEach { payment -> PaymentRow(payment, currency, state, model) }
                if (summary.tipTotal.cents() > 0) SumLine("Tips", summary.tipTotal.money(currency), color = Kit.Green)
                if (summary.refundedTotal.cents() > 0) SumLine("Refunded", "-${summary.refundedTotal.money(currency)}", color = Kit.Danger)
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: PaymentDto, currency: String, state: PaymentUiState, model: PaymentScreenModel) {
    val actions = model.actionsFor(payment)
    val zone = RestaurantTime.zone
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8F9F7)).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(methodIcon(payment.method), null, Modifier.size(20.dp), tint = Kit.Ink)
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(methodName(payment.method), payment.cardLast4?.let { "•••• $it" }).joinToString(" "), fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
            Text(listOfNotNull(payment.paidAt?.let { timeText(it, zone) }, payment.takenByName, payment.tipAmount.cents().takeIf { it > 0 }?.let { "tip ${moneyText(it, currency)}" })
                .joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(payment.amount.money(currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
            Text(paymentStatusLabel(payment.status), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = paymentStatusColor(payment.status))
        }
        RowActionsMenu(buildList {
            if (actions.canRefund) add(RowAction("Refund", Icons.AutoMirrored.Outlined.Undo) { model.startRefund(payment.id) })
            if (actions.canCancel) add(RowAction("Cancel payment", Icons.Outlined.Block, danger = true) { model.startCancel(payment.id) })
        }, busy = state.submitting && state.correction?.paymentId == payment.id)
    }
}

private fun plain(text: String): String = if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
