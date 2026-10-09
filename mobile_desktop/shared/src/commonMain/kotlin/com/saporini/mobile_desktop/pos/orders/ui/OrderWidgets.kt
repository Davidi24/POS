package com.saporini.mobile_desktop.pos.orders.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import kotlinx.datetime.*
import kotlin.time.Instant

internal val OrderGreen = Color(0xFF4F7942)
internal val OrderInk = Color(0xFF242925)
internal val OrderMuted = Color(0xFF757B76)
internal val OrderBorder = Color(0xFFE3E8E1)
internal val OrderBackground = Color(0xFFF7F9F6)
internal fun Enum<*>.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
internal fun OrderDecimal.money(currency: String) = when(currency) { "EUR" -> "€"; "USD" -> "$"; "GBP" -> "£"; else -> "$currency " } + value
internal fun String.orderTime(): String = runCatching { Instant.parse(this).toLocalDateTime(TimeZone.currentSystemDefault()).let { "${it.hour.toString().padStart(2,'0')}:${it.minute.toString().padStart(2,'0')}" } }.getOrDefault(take(16).replace('T',' '))
internal val OrderStatus.showsFulfillmentProgress get() = this != OrderStatus.CANCELLED && this != OrderStatus.VOIDED
internal val Order.editable get() = status == OrderStatus.OPEN || status == OrderStatus.DRAFT
internal val OrderLineItem.active get() = status != OrderLineItemStatus.CANCELLED && status != OrderLineItemStatus.VOIDED
// Not sent yet and meant for the kitchen; counter items (e.g. a cola) never count as waiting for the kitchen.
internal val OrderLineItem.awaitingKitchen get() = status == OrderLineItemStatus.PENDING && sendToKitchen

@Composable internal fun OrderText(text: String, size: Int = 14, bold: Boolean = false, color: Color = OrderInk, modifier: Modifier = Modifier) {
    Text(text, modifier, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontFamily = Inter(), fontSize = size.sp, fontWeight = if(bold) FontWeight.SemiBold else FontWeight.Normal, color = color)
}
@Composable internal fun OrderButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 46.dp), enabled = enabled, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = OrderGreen)) { OrderText(text, bold = true, color = Color.White) }
}
@Composable internal fun OrderBadge(text: String) {
    val color = when {
        text.contains("Ready", true) -> Color(0xFF24748A)
        text.contains("prepar", true) || text.contains("Fired", true) -> Color(0xFFAA5E10)
        text.contains("cancel",true) || text.contains("void",true) -> Color(0xFFAA3F38)
        text == "Open" || text == "Fulfilled" -> OrderGreen
        else -> OrderMuted
    }
    Surface(color = color.copy(alpha = .09f), shape = RoundedCornerShape(20.dp)) { OrderText(text, 12, true, color, Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) }
}
@Composable internal fun OrderField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, shape = RoundedCornerShape(10.dp))
}
@Composable internal fun OrderChoice(label: String, value: String, choices: List<Pair<String,String>>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(10.dp)) { OrderText("$label: $value  ▾", 13) }
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 350.dp)) {
            choices.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(id) }) }
        }
    }
}
@Composable internal fun OrderModal(title: String, onDismiss: () -> Unit, busy: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if(!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = false)) {
        Surface(Modifier.padding(12.dp).widthIn(max = 680.dp).fillMaxWidth().heightIn(max = 850.dp), shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OrderText(title, 22, true, modifier = Modifier.weight(1f))
                    TextButton(onDismiss, enabled = !busy) { Text("Close") }
                }
                HorizontalDivider(color = OrderBorder)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            }
        }
    }
}
@Composable internal fun OrderError(message: String, onDismiss: (() -> Unit)? = null) {
    Surface(color = Color(0xFFFFF1EB), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OrderText(message, color = Color(0xFF9C3F26), modifier = Modifier.weight(1f))
            if(onDismiss != null) TextButton(onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable internal fun OrderChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(selected, onClick, label, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OrderGreen, selectedLabelColor = Color.White))
}
