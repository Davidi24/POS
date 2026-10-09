package com.saporini.mobile_desktop.pos.reservations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import com.saporini.mobile_desktop.pos.reservations.domain.model.BookingExtraChoice
import com.saporini.mobile_desktop.pos.reservations.domain.model.MoneyLine
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import com.saporini.mobile_desktop.pos.reservations.domain.model.moneyCents
import com.saporini.mobile_desktop.pos.reservations.domain.model.moneyText
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationsScreenModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlin.time.Instant

// "Money" on the booking panel: the deposit, paid extras and food pre-order, each with what a cancel would do now,
// plus paying at the desk, a payment link by email, adding an extra, and (with permission) a goodwill refund.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookingMoneyCard(
    model: ReservationsScreenModel,
    reservation: Reservation,
    canManage: Boolean,
    canGoodwill: Boolean,
    now: Instant
) {
    val scope = rememberCoroutineScope()
    var lines by remember(reservation.id) { mutableStateOf<List<MoneyLine>?>(null) }
    var choices by remember(reservation.id) { mutableStateOf<List<BookingExtraChoice>>(emptyList()) }
    var failed by remember(reservation.id) { mutableStateOf(false) }
    var reload by remember(reservation.id) { mutableIntStateOf(0) }
    var working by remember(reservation.id) { mutableStateOf(false) }
    var addingExtra by remember(reservation.id) { mutableStateOf(false) }
    var goodwillLine by remember(reservation.id) { mutableStateOf<MoneyLine?>(null) }
    var confirm by remember(reservation.id) { mutableStateOf<MoneyConfirm?>(null) }
    val open = reservation.status == ReservationStatus.PENDING || reservation.status == ReservationStatus.CONFIRMED

    // A status, occasion or time change can change what's paid back or which extras fit.
    LaunchedEffect(reservation.id, reservation.status, reservation.occasionCode, reservation.reservationStart, reservation.partySize, reload) {
        failed = false
        model.money(reservation.id).onSuccess { lines = it }.onFailure { failed = true }
        choices = if (canManage && open) model.extraChoices(reservation.id).getOrDefault(emptyList()) else emptyList()
    }

    fun run(action: () -> Deferred<Result<List<MoneyLine>>>) {
        if (working) return
        working = true
        scope.launch {
            try {
                action().await().onSuccess { lines = it }
            } finally {
                working = false
            }
        }
    }

    confirm?.let { question ->
        MenuNestedDialog(
            onDismissRequest = { confirm = null },
            title = { Text(question.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text(question.message, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = FormMuted) },
            confirmButton = {
                Button(
                    onClick = { confirm = null; question.onConfirm() },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = if (question.danger) FormDanger else FormGreen)
                ) { Text(question.confirmLabel, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Back", color = FormMuted, fontFamily = Inter()) } }
        )
    }
    goodwillLine?.let { line ->
        GoodwillDialog(
            line = line,
            onConfirm = { cents, reason ->
                goodwillLine = null
                run { model.goodwillRefund(reservation.id, line, cents, reason) }
            },
            onDismiss = { goodwillLine = null }
        )
    }
    if (addingExtra) {
        AddExtraDialog(
            choices = choices,
            reservation = reservation,
            now = now,
            onConfirm = { choice, quantity ->
                addingExtra = false
                run { model.addExtra(reservation.id, choice, quantity) }
            },
            onDismiss = { addingExtra = false }
        )
    }

    if (failed && lines == null) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, FormBorder, RoundedCornerShape(10.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Couldn't load the booking's money.", Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
            Text(
                "Try again",
                Modifier.clip(RoundedCornerShape(6.dp)).clickable { reload++ }.padding(horizontal = 8.dp, vertical = 4.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen
            )
        }
        return
    }
    val loaded = lines ?: return
    val extraOffered = addExtraOffer(reservation, choices, canManage)
    if (loaded.isEmpty() && !extraOffered) return
    val linkOffer = paymentLinkOffer(reservation, loaded, canManage)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, FormBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Payments, null, Modifier.size(20.dp), tint = FormInk)
            Text("Money", Modifier.padding(start = 14.dp).weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormInk)
            moneyDueLabel(loaded)?.let { Text(it, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LateColor) }
        }
        if (loaded.isEmpty()) {
            Text("Nothing paid for this booking.", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
        }
        loaded.forEach { line ->
            val muted = line.status == "CANCELLED"
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        line.description, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = if (muted) FormMuted else FormInk
                    )
                    Text(moneyText(line.amountCents, line.currency), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (muted) FormMuted else FormInk)
                    MoneyStatusChip(line)
                }
                line.explanation?.takeIf(String::isNotBlank)?.let {
                    Text(it, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = FormMuted)
                }
                val actions = moneyActions(line, canManage, canGoodwill)
                if (actions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        actions.forEach { action ->
                            MoneyChipButton(action.label, enabled = !working, danger = action == MoneyAction.REMOVE) {
                                when (action) {
                                    MoneyAction.MARK_PAID -> confirm = MoneyConfirm(
                                        "Mark as paid?",
                                        "${line.description}: ${moneyText(line.amountCents, line.currency)} paid at the desk (card or cash).",
                                        "Mark paid"
                                    ) { run { model.markPaid(reservation.id, line) } }
                                    MoneyAction.REMOVE -> confirm = MoneyConfirm(
                                        "Remove this extra?",
                                        "${line.description} isn't paid yet. It's taken off the booking and the guest pays nothing for it.",
                                        "Remove extra", danger = true
                                    ) { run { model.removeUnpaid(reservation.id, line) } }
                                    MoneyAction.GOODWILL -> goodwillLine = line
                                }
                            }
                        }
                    }
                }
            }
        }
        if (linkOffer != null || extraOffered) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (linkOffer != null) {
                    MoneyChipButton("Send payment link", enabled = linkOffer.enabled && !working, icon = Icons.Outlined.Email) {
                        confirm = MoneyConfirm(
                            "Email a payment link?",
                            "${reservation.contactEmail} gets a link to pay ${moneyDueLabel(loaded)?.removeSuffix(" to pay") ?: "what's due"} online.",
                            "Send link"
                        ) {
                            if (!working) {
                                working = true
                                scope.launch { try { model.sendPaymentLink(reservation.id).await() } finally { working = false } }
                            }
                        }
                    }
                }
                if (extraOffered) {
                    MoneyChipButton("Add extra", enabled = !working, icon = Icons.Outlined.Add) { addingExtra = true }
                }
            }
            linkOffer?.note?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = FormMuted) }
        }
    }
}

private data class MoneyConfirm(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val danger: Boolean = false,
    val onConfirm: () -> Unit
)

@Composable
private fun MoneyStatusChip(line: MoneyLine) {
    val color = when (line.status) {
        "PENDING" -> LateColor
        "PAID" -> FormGreen
        "REFUNDED" -> WaitingColor
        "KEPT" -> ReviewColor
        else -> FormMuted
    }
    Text(
        moneyStatusLabel(line),
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = color
    )
}

@Composable
private fun MoneyChipButton(
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> FormMuted
        danger -> FormDanger
        else -> FormGreen
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (enabled) FormBorder else FormBorder.copy(alpha = 0.5f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            icon?.let { Icon(it, null, Modifier.size(16.dp), tint = tint) }
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = tint)
        }
    }
}

// Part of kept money back, with a reason. Never all of it: the server refuses that too.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoodwillDialog(line: MoneyLine, onConfirm: (cents: Long, reason: String) -> Unit, onDismiss: () -> Unit) {
    var amount by remember(line.id) { mutableStateOf("") }
    var reason by remember(line.id) { mutableStateOf("") }
    val problem = goodwillProblem(amount, line)
    val ready = problem == null && reason.isNotBlank()
    val left = goodwillLeftCents(line)
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = { Text("Goodwill refund", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${line.description}: ${moneyText(line.amountCents, line.currency)} kept by the restaurant" +
                        (if (line.refundedCents > 0) ", ${moneyText(line.refundedCents, line.currency)} already given back" else "") +
                        ". Give part of it back; up to ${moneyText(left - 1, line.currency)}. The reason is kept in the booking's history.",
                    fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = FormMuted
                )
                InputBox(Icons.Outlined.Payments, amount, "Amount, e.g. 10.00", keyboardType = KeyboardType.Decimal, isError = amount.isNotEmpty() && problem != null) {
                    amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(10)
                }
                if (amount.isNotEmpty() && problem != null) ErrorText(problem)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Family emergency", "Regular guest", "Our mistake").forEach { suggestion ->
                        val picked = reason == suggestion
                        Text(
                            suggestion,
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (picked) FormGreenSoft else Color.White)
                                .border(1.dp, if (picked) FormGreen else FormBorder, RoundedCornerShape(50))
                                .clickable { reason = suggestion }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                            color = if (picked) FormGreen else FormInk
                        )
                    }
                }
                InputBox(Icons.Outlined.Notes, reason, "Reason (required)", isError = reason.isBlank()) { reason = it.take(300) }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (ready) onConfirm(moneyCents(amount.trim()) ?: 0, reason.trim()) },
                enabled = ready,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = FormGreen)
            ) {
                Text(
                    if (problem == null) "Give ${moneyText(moneyCents(amount.trim()) ?: 0, line.currency)} back" else "Give back",
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back", color = FormMuted, fontFamily = Inter()) } }
    )
}

// A paid extra from the special menu, e.g. a cake ordered on the phone. The guest pays by link or at the desk.
@Composable
private fun AddExtraDialog(
    choices: List<BookingExtraChoice>,
    reservation: Reservation,
    now: Instant,
    onConfirm: (BookingExtraChoice, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var picked by remember { mutableStateOf(choices.firstOrNull { extraTooLate(it, reservation.reservationStart, now) == null }) }
    var quantity by remember { mutableIntStateOf(1) }
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add an extra", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The guest pays with a payment link or at the desk. Cancelled in time, they get it back minus the card fee.",
                    fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = FormMuted
                )
                Column(Modifier.height((choices.size.coerceAtMost(4) * 62).dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { choice ->
                        val late = extraTooLate(choice, reservation.reservationStart, now)
                        val selected = picked?.menuItemId == choice.menuItemId
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (selected) FormGreenSoft else Color.White)
                                .border(if (selected) 1.5.dp else 1.dp, if (selected) FormGreen else FormBorder, RoundedCornerShape(8.dp))
                                .clickable(enabled = late == null) { picked = choice }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(choice.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (late == null) FormInk else FormMuted)
                                Text(
                                    late ?: listOfNotNull(choice.description?.takeIf(String::isNotBlank), choice.orderBeforeHours?.let { "order $it h ahead" }).joinToString(" · ").ifEmpty { " " },
                                    fontFamily = Inter(), fontSize = 11.sp, color = if (late != null) LateColor else FormMuted, maxLines = 1
                                )
                            }
                            Text(moneyText(choice.priceCents, choice.currency), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountButton(Icons.Outlined.Remove, "Fewer", quantity > 1) { quantity-- }
                    Text("$quantity", Modifier.width(40.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = FormInk, textAlign = TextAlign.Center)
                    CountButton(Icons.Outlined.Add, "More", quantity < 50) { quantity++ }
                }
            }
        },
        confirmButton = {
            val choice = picked
            Button(
                onClick = { if (choice != null) onConfirm(choice, quantity) },
                enabled = choice != null,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = FormGreen)
            ) {
                Text(
                    choice?.let { "Add · ${moneyText(it.priceCents * quantity, it.currency)}" } ?: "Add",
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back", color = FormMuted, fontFamily = Inter()) } }
    )
}
