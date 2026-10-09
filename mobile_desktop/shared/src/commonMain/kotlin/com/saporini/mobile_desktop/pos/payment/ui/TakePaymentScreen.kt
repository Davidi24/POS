package com.saporini.mobile_desktop.pos.payment.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.ChoiceChip
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToggleRow
import com.saporini.mobile_desktop.core.components.ToolbarIconButton
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.payment.MAX_NOTES
import com.saporini.mobile_desktop.pos.payment.PayMethod
import com.saporini.mobile_desktop.pos.payment.PaymentFailure
import com.saporini.mobile_desktop.pos.payment.PaymentScreenModel
import com.saporini.mobile_desktop.pos.payment.PaymentUiState
import com.saporini.mobile_desktop.pos.shifts.centsToDecimal
import org.koin.compose.koinInject

/** Taking payment for one order: the bill on the left, how the guest pays on the right. */
@Composable
fun TakePaymentScreen(orderId: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val model = koinInject<PaymentScreenModel>()
    DisposableEffect(model, orderId) {
        model.open(orderId)
        model.loadReceipt()
        onDispose { }
    }
    DisposableEffect(model) { onDispose { model.onDispose() } }
    val state by model.state.collectAsState()
    TakePaymentContent(state, model, onBack, modifier)
}

@Composable
internal fun TakePaymentContent(state: PaymentUiState, model: PaymentScreenModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var showReceipt by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        val pad = if (size.isPhone) 14.dp else 24.dp
        Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = if (size.isPhone) 12.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Header(state, model, onBack)
            val summary = state.summary
            when {
                !state.canTake && !state.canRefund -> PageState(PageStateKind.NO_ACCESS, "No access to payments", "Your role needs “Close orders” to take payments.")
                summary == null && state.loading -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Kit.Green, strokeWidth = 3.dp)
                }
                summary == null -> PageState(PageStateKind.FAILED, hint = state.error?.message ?: "Couldn't load the bill.",
                    action = { RetryText(onClick = model::refresh) })
                size.isPhone -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PayColumn(state, model, size, onBack) { showReceipt = true }
                    BillPanel(state, model, Modifier.fillMaxWidth(), scrollable = false)
                }
                else -> Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    BillPanel(state, model, Modifier.width(if (size.isDesktop) 440.dp else 340.dp).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PayColumn(state, model, size, onBack) { showReceipt = true }
                    }
                }
            }
        }
    }
    if (state.correction != null) CorrectionDialog(state, model)
    if (showReceipt) ReceiptDialog(state, model) { showReceipt = false }
}

@Composable
private fun Header(state: PaymentUiState, model: PaymentScreenModel, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Kit.Tint).clickable(onClick = onBack), Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to the order", Modifier.size(20.dp), tint = Kit.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text("Collect payment", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Kit.Ink)
            Text(listOfNotNull(state.summary?.orderNumber?.let { "Order #$it" }, state.receipt?.tableNumber?.let { "Table $it" },
                state.receipt?.guestCount?.let { if (it == 1) "1 guest" else "$it guests" }).joinToString(" · ").ifEmpty { "Choose how the guest pays" },
                fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
        ToolbarIconButton(Icons.Outlined.Refresh, "Refresh the bill", enabled = !state.loading) { model.refresh(); model.loadReceipt() }
    }
}

@Composable
private fun PayColumn(state: PaymentUiState, model: PaymentScreenModel, size: ScreenSize, onBack: () -> Unit, onReceipt: () -> Unit) {
    val summary = state.summary ?: return
    val currency = summary.currency
    val balance = summary.balanceDue.cents()
    val total = summary.orderTotal.cents()
    val paid = summary.paidTotal.cents() + summary.prepaidTotal.cents()
    // Hero: what is left to pay.
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.horizontalGradient(listOf(Kit.Green.copy(alpha = 0.10f), Kit.Green.copy(alpha = 0.03f))))
        .padding(horizontal = 22.dp, vertical = 18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (balance > 0) "Left to pay" else "Nothing left to pay", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Kit.Muted)
                    Text(moneyText(balance.coerceAtLeast(0), currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 36.sp,
                        color = if (balance > 0) Color(0xFFAD3F08) else Kit.Green)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Bill ${moneyText(total, currency)}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                    if (paid > 0) Text("${moneyText(paid, currency)} paid", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Green)
                    val cash = summary.cashBalanceDue.cents()
                    if (cash != balance && balance > 0) Text("${moneyText(cash, currency)} in cash", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                }
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White)) {
                Box(Modifier.fillMaxWidth(if (total > 0) (paid.toFloat() / total).coerceIn(0f, 1f) else 0f).fillMaxHeight().background(Kit.Green))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(13.dp), tint = Kit.Muted)
                Text("Each payment is sent once, even if you tap twice or the connection drops.", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            }
        }
    }
    state.error?.let { failure ->
        MessageBar(failure.message + if (failure.mayHaveWorked) " Refresh the bill to see if it went through." else "",
            if (failure.mayHaveWorked || failure.kind == PaymentFailure.Kind.CONFLICT) MessageKind.WARNING else MessageKind.ERROR, onDismiss = model::clearError)
    }
    if (state.completed || (balance <= 0 && state.lastPayment != null)) {
        DonePanel(state, onBack, onReceipt)
        return
    }
    if (!state.canTake) {
        MessageBar("You can see the bill but not take payments. Ask a manager for “Close orders”.", MessageKind.INFO)
        return
    }
    if (balance <= 0) {
        MessageBar("Nothing is left to pay on this order.", MessageKind.SUCCESS)
        KitButton("See the receipt", onReceipt, icon = Icons.AutoMirrored.Outlined.ReceiptLong, style = ButtonStyle.SECONDARY)
        return
    }
    MethodPicker(state, model, size)
    EntryCard(state, model)
}

@Composable
private fun MethodPicker(state: PaymentUiState, model: PaymentScreenModel, size: ScreenSize) {
    SectionTitle("How the guest pays")
    val columns = if (size.isPhone) 2 else 4
    PayMethod.entries.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { method ->
                val chosen = state.draft.method == method
                Surface(onClick = { model.method(method) }, modifier = Modifier.weight(1f).height(92.dp), shape = RoundedCornerShape(12.dp),
                    color = if (chosen) Kit.GreenSoft else Color.White, border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) Kit.Green else Kit.Border),
                    shadowElevation = if (chosen) 2.dp else 0.dp) {
                    Box {
                        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(method.icon(), null, Modifier.size(26.dp), tint = if (chosen) Kit.Green else Kit.Ink)
                            Spacer(Modifier.height(5.dp))
                            Text(method.label, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.Center)
                            Text(method.hint(), fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, textAlign = TextAlign.Center, maxLines = 1)
                        }
                        if (chosen) Icon(Icons.Filled.CheckCircle, "Chosen", Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp), tint = Kit.Green)
                    }
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(state: PaymentUiState, model: PaymentScreenModel) {
    val summary = state.summary ?: return
    val currency = summary.currency
    val draft = state.draft
    val quote = state.quote ?: return
    var splitInto by remember { mutableStateOf<Int?>(null) }
    var noteOpen by remember { mutableStateOf(draft.notes.isNotBlank()) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Amount
            TextInput(draft.amountText, model::amount, label = "Amount", placeholder = centsToDecimal(quote.dueCents).value, suffix = currency,
                keyboardType = KeyboardType.Decimal, hint = if (draft.amountText.isBlank()) "Empty means the whole bill" else null)
            if (summary.splitBillsAllowed) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text("Split", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                    ChoiceChip("Whole bill", draft.amountText.isBlank(), { splitInto = null; model.amount("") })
                    listOf(2, 3, 4, 5).forEach { people ->
                        ChoiceChip("÷ $people", splitInto == people && draft.amountText.isNotBlank(), { splitInto = people; model.splitEvenly(people) })
                    }
                }
            }
            // Tip
            if (summary.tipsEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text("Tip", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                    ChoiceChip("None", draft.tipPercent == null && draft.tipText.isBlank(), { model.tipPercent(null) })
                    state.tipChoices.forEach { percent -> ChoiceChip("$percent%", draft.tipPercent == percent, { model.tipPercent(percent) }) }
                    TextInput(draft.tipText, model::tipAmount, Modifier.width(120.dp), placeholder = "Other", suffix = currency, keyboardType = KeyboardType.Decimal)
                }
            }
            // Cash
            if (draft.method == PayMethod.CASH) {
                TextInput(draft.tenderedText, model::tendered, label = "Cash handed over", optional = true, suffix = currency, keyboardType = KeyboardType.Decimal,
                    icon = Icons.Outlined.Payments, placeholder = "50")
                if (state.quickCash.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.quickCash.forEach { cents -> ChoiceChip(moneyText(cents, currency), quote.tenderedCents == cents, { model.tenderedCents(cents) }) }
                    }
                }
                quote.changeCents?.takeIf { it >= 0 }?.let { change ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Amber.copy(alpha = 0.1f)).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Give back", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Kit.Ink)
                        Text(moneyText(change, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF8B5C18))
                    }
                }
            }
            // Card
            if (draft.method.card) {
                TextInput(draft.cardLast4, model::cardLast4, label = "Last 4 digits of the card", optional = true, keyboardType = KeyboardType.Number,
                    placeholder = "4242", hint = "Helps find the payment later. Never type the whole card number.")
            }
            if (noteOpen) TextInput(draft.notes, model::notes, label = "Note", optional = true, singleLine = false, minLines = 2, maxLength = MAX_NOTES)
            else Text("+ Add a note", Modifier.clip(RoundedCornerShape(6.dp)).clickable { noteOpen = true }.padding(4.dp), fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Green)
            ToggleRow("Close the order when it's paid", draft.closeWhenPaid, model::closeWhenPaid, detail = "Frees the table once nothing is left to pay.")
            // Summary and the button
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SumLine("Payment", quote.amountCents?.let { moneyText(it, currency) } ?: "–")
                if ((quote.tipCents ?: 0) > 0) SumLine("Tip", moneyText(quote.tipCents ?: 0, currency))
                SumLine("Charged", moneyText((quote.amountCents ?: 0) + (quote.tipCents ?: 0), currency), strong = true)
                quote.leftAfterCents?.takeIf { it > 0 }?.let { SumLine("Still to pay after this", moneyText(it, currency)) }
            }
            quote.problem?.takeIf { draft.amountText.isNotBlank() || draft.tipText.isNotBlank() || draft.tenderedText.isNotBlank() || draft.cardLast4.isNotBlank() }
                ?.let { MessageBar(it, MessageKind.WARNING) }
            val charge = (quote.amountCents ?: 0) + (quote.tipCents ?: 0)
            Surface(onClick = model::take, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(12.dp),
                color = if (state.canSubmit) Kit.Green else Kit.Green.copy(alpha = 0.4f)) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(draft.method.icon(), null, Modifier.size(22.dp), tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(when {
                        state.submitting -> "Taking payment…"
                        state.pendingKey != null -> "Try again safely · ${moneyText(charge, currency)}"
                        else -> "Take ${moneyText(charge, currency)} by ${draft.method.label.lowercase()}"
                    }, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DonePanel(state: PaymentUiState, onBack: () -> Unit, onReceipt: () -> Unit) {
    val payment = state.lastPayment
    val currency = state.summary?.currency
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Green.copy(alpha = 0.35f))) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(Kit.GreenSoft), Alignment.Center) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(46.dp), tint = Kit.Green)
            }
            Text("Paid in full", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Kit.Ink)
            payment?.let {
                Text("${methodName(it.method)} · ${it.amount.money(currency)}" + (it.tipAmount.cents().takeIf { t -> t > 0 }?.let { t -> " + ${moneyText(t, currency)} tip" } ?: ""),
                    fontFamily = Inter(), fontSize = 14.sp, color = Kit.Muted)
                it.changeAmount?.cents()?.takeIf { c -> c > 0 }?.let { change ->
                    Text("Give back ${moneyText(change, currency)}", Modifier.clip(RoundedCornerShape(50)).background(Kit.Amber.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF8B5C18))
                }
            }
            if (state.summary?.orderStatus == "CLOSED") Text("The order is closed and the table is free.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                KitButton("Receipt", onReceipt, icon = Icons.AutoMirrored.Outlined.ReceiptLong, style = ButtonStyle.SECONDARY)
                KitButton("Back to orders", onBack)
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink)
}

@Composable
internal fun SumLine(label: String, value: String, strong: Boolean = false, color: Color = Kit.Ink) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (strong) 15.sp else 13.sp, color = if (strong) Kit.Ink else Kit.Muted)
        Text(value, fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold, fontSize = if (strong) 17.sp else 13.sp, color = color)
    }
}
