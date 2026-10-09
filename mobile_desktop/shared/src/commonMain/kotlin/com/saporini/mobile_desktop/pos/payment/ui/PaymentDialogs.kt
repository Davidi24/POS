package com.saporini.mobile_desktop.pos.payment.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.payment.MAX_REASON
import com.saporini.mobile_desktop.pos.payment.PaymentScreenModel
import com.saporini.mobile_desktop.pos.payment.PaymentUiState
import com.saporini.mobile_desktop.pos.reservations.domain.model.moneyCents
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime

/** A manager giving money back, or cancelling a payment taken today by mistake. Both need a reason. */
@Composable
internal fun CorrectionDialog(state: PaymentUiState, model: PaymentScreenModel) {
    val correction = state.correction ?: return
    val payment = state.summary?.payments?.firstOrNull { it.id == correction.paymentId } ?: return
    val currency = state.summary.currency
    val problem = model.correctionProblem()
    AppDialog(if (correction.cancel) "Cancel this payment?" else "Refund", model::dismissCorrection, busy = state.submitting, maxWidth = 520.dp,
        subtitle = "${methodName(payment.method)} · ${payment.amount.money(currency)} · ${dateTimeText(payment.paidAt, RestaurantTime.zone)}",
        buttons = {
            KitButton("Back", model::dismissCorrection, style = ButtonStyle.SECONDARY, enabled = !state.submitting)
            KitButton(if (correction.cancel) "Cancel payment" else "Refund ${moneyText(moneyCents(correction.amountText) ?: 0, currency)}",
                model::confirmCorrection, icon = if (correction.cancel) Icons.Outlined.Block else Icons.AutoMirrored.Outlined.Undo,
                style = ButtonStyle.DANGER, enabled = problem == null, loading = state.submitting)
        }) {
        state.error?.let { MessageBar(it.message, if (it.mayHaveWorked) MessageKind.WARNING else MessageKind.ERROR) }
        if (correction.cancel) {
            Text("The whole payment is taken off the bill, as if it never happened. Use it for mistakes made today; otherwise give a refund.",
                fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Ink)
        } else {
            TextInput(correction.amountText, model::correctionAmount, label = "Amount to give back", required = true, suffix = currency,
                keyboardType = KeyboardType.Decimal, hint = "Up to ${model.actionsFor(payment).refundableCents.let { moneyText(it, currency) }}")
        }
        TextInput(correction.reason, model::correctionReason, label = "Reason", required = true, singleLine = false, minLines = 2, maxLength = MAX_REASON,
            placeholder = if (correction.cancel) "Charged the wrong table" else "Dish sent back")
        problem?.takeIf { correction.reason.isNotBlank() || correction.amountText.isNotBlank() }?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger) }
    }
}

/** The receipt as it prints, to show the guest or check before printing. */
@Composable
internal fun ReceiptDialog(state: PaymentUiState, model: PaymentScreenModel, onClose: () -> Unit) {
    val receipt = state.receipt
    AppDialog("Receipt", onClose, maxWidth = 460.dp, buttons = {
        KitButton("Refresh", model::loadReceipt, style = ButtonStyle.SECONDARY, loading = state.loadingReceipt)
        KitButton("Done", onClose)
    }) {
        if (receipt == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Kit.Green, strokeWidth = 2.dp) }
            return@AppDialog
        }
        val currency = receipt.currency
        val zone = RestaurantTime.zone
        // Paper look: narrow, monospaced, dashed lines between parts.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFFFCFBF8)).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            receipt.restaurantName?.let { Mono(it, bold = true, size = 16, center = true) }
            receipt.legalName?.takeIf { it != receipt.restaurantName }?.let { Mono(it, center = true) }
            receipt.addressLines.forEach { Mono(it, center = true) }
            receipt.phone?.let { Mono(it, center = true) }
            receipt.vatNumber?.let { Mono("VAT $it", center = true) }
            Dashes()
            receipt.orderNumber?.let { MonoLine("Order", "#$it") }
            receipt.tableNumber?.let { MonoLine("Table", it) }
            receipt.guestCount?.let { MonoLine("Guests", "$it") }
            receipt.serverName?.let { MonoLine("Served by", it) }
            MonoLine("Date", dateTimeText(receipt.closedAt ?: receipt.printedAt ?: receipt.openedAt, zone))
            Dashes()
            receipt.lines.forEach { line ->
                MonoLine("${line.quantity} × ${line.name}", line.lineTotal.money(currency), struck = line.removed)
                val extras = listOfNotNull(line.variant) + line.options
                extras.forEach { Mono("   $it", muted = true) }
            }
            Dashes()
            MonoLine("Subtotal", receipt.subtotal.money(currency))
            receipt.discounts.forEach { MonoLine(it.name, "-${it.amount.money(currency)}") }
            if (receipt.serviceChargeTotal.cents() > 0) MonoLine("Service", receipt.serviceChargeTotal.money(currency))
            receipt.tax?.let { MonoLine("Tax ${it.rate.value.trimEnd('0').trimEnd('.')}%${if (it.inclusive) " incl." else ""}", it.amount.money(currency)) }
            MonoLine("TOTAL", receipt.total.money(currency), bold = true)
            if (receipt.payments.isNotEmpty()) {
                Dashes()
                receipt.payments.forEach { payment ->
                    MonoLine(methodName(payment.method) + (payment.cardLast4?.let { " •$it" } ?: ""), payment.amount.money(currency))
                    if (payment.tipAmount.cents() > 0) MonoLine("  Tip", payment.tipAmount.money(currency))
                    payment.changeAmount?.takeIf { it.cents() > 0 }?.let { MonoLine("  Change", it.money(currency)) }
                }
                MonoLine("Left to pay", receipt.balanceDue.money(currency), bold = true)
            }
            receipt.footerNote?.let { Dashes(); Mono(it, center = true) }
        }
    }
}

@Composable
private fun Mono(text: String, bold: Boolean = false, size: Int = 12, center: Boolean = false, muted: Boolean = false) {
    Text(text, Modifier.fillMaxWidth(), fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = size.sp,
        color = if (muted) Kit.Muted else Kit.Ink, textAlign = if (center) TextAlign.Center else TextAlign.Start)
}

@Composable
private fun MonoLine(label: String, value: String, bold: Boolean = false, struck: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp,
            color = Kit.Ink, textDecoration = if (struck) TextDecoration.LineThrough else null)
        Text(value, Modifier.width(110.dp), fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp,
            color = Kit.Ink, textAlign = TextAlign.End, textDecoration = if (struck) TextDecoration.LineThrough else null)
    }
}

@Composable
private fun Dashes() {
    Text("- ".repeat(40), Modifier.fillMaxWidth().padding(vertical = 2.dp), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Kit.Faint, maxLines = 1)
}
