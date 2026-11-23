package com.saporini.mobile_desktop.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.KitSwitch
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PanelRow
import com.saporini.mobile_desktop.core.components.RightEdgeActionButton
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.ShareBars
import com.saporini.mobile_desktop.core.components.ShareDatum
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.edgeActionButtonHeight
import com.saporini.mobile_desktop.core.format.decimalTextToCents
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.finance.Expense
import com.saporini.mobile_desktop.finance.ExpenseKind
import com.saporini.mobile_desktop.finance.FinanceBook
import com.saporini.mobile_desktop.finance.monthTitle
import com.saporini.mobile_desktop.finance.pct
import kotlinx.datetime.LocalDate
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_stock_value
import mobile_desktop.shared.generated.resources.shifts_needs_review
import mobile_desktop.shared.generated.resources.supplier_deliveries
import mobile_desktop.shared.generated.resources.supplier_orders
import kotlin.math.roundToLong

internal fun kindIcon(kind: ExpenseKind): ImageVector = when (kind) {
    ExpenseKind.STOCK -> Icons.Outlined.Inventory2
    ExpenseKind.RENT -> Icons.Outlined.Home
    ExpenseKind.POWER -> Icons.Outlined.Bolt
    ExpenseKind.GAS -> Icons.Outlined.LocalFireDepartment
    ExpenseKind.PHONE -> Icons.Outlined.Wifi
    ExpenseKind.CLEANING -> Icons.Outlined.CleaningServices
    ExpenseKind.REPAIRS -> Icons.Outlined.Build
    ExpenseKind.MARKETING -> Icons.Outlined.Campaign
    ExpenseKind.FEES -> Icons.Outlined.CreditCard
    ExpenseKind.ADVICE -> Icons.Outlined.Gavel
    ExpenseKind.OTHER -> Icons.Outlined.MoreHoriz
}

internal fun kindColor(kind: ExpenseKind): Color = when (kind) {
    ExpenseKind.STOCK -> Kit.Green
    ExpenseKind.RENT -> Kit.Purple
    ExpenseKind.POWER -> Kit.Amber
    ExpenseKind.GAS -> Color(0xFFB0662E)
    ExpenseKind.PHONE -> Kit.Blue
    ExpenseKind.CLEANING -> Color(0xFF3E8E6A)
    ExpenseKind.REPAIRS -> Kit.Grey
    ExpenseKind.MARKETING -> Color(0xFFB0505E)
    ExpenseKind.FEES -> Color(0xFF24748A)
    ExpenseKind.ADVICE -> Color(0xFF6E5A9E)
    ExpenseKind.OTHER -> Kit.Grey
}

/** Every cost of the month with its invoice: from Inventory's deliveries and the bills added here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpensesPage(fx: Fx) {
    val f = FinanceBook.figures()
    var kind by remember(f.month) { mutableStateOf<ExpenseKind?>(null) }
    var all by remember(f.month, kind) { mutableStateOf(false) }
    val noReceipt = f.expenses.count { !it.hasReceipt }
    val biggest = f.expenses.groupBy { it.kind }.maxByOrNull { (_, list) -> list.sumOf { it.net } }
    PageHeader("Expenses", "${monthTitle(f.month)} · ${f.expenses.size} expenses") { MonthSwitcher(fx.phone) }
    SummaryCardRow(fx.size, listOf(
        { m -> OverviewStatCard("Expenses", fx.money(f.expensesNet), "Without VAT, this month", Kit.Green, m, image = Res.drawable.supplier_orders) },
        { m -> OverviewStatCard("VAT taken back", fx.money(f.inputVat), "Lowers the VAT you pay", Kit.Blue, m, image = Res.drawable.inventory_stock_value) },
        { m -> OverviewStatCard("Without a receipt", "$noReceipt", if (noReceipt == 0) "All have one" else "Add a photo of each", if (noReceipt == 0) Kit.Green else Kit.Amber, m,
            image = Res.drawable.shifts_needs_review) },
        { m -> OverviewStatCard("Biggest cost", biggest?.key?.label ?: "–", biggest?.let { fx.money(it.value.sumOf { e -> e.net }) } ?: "", Kit.Purple, m,
            image = Res.drawable.supplier_deliveries) }
    ))
    PanelRow(fx.size,
        1f to { m ->
            Section("Where the money went", Icons.Outlined.PieChart, m) {
                ShareBars(f.expenses.groupBy { it.kind }.map { (k, list) -> ShareDatum(k.label, list.sumOf { it.net }.toFloat(), fx.money(list.sumOf { it.net })) }, max = 11)
            }
        },
        1f to { m ->
            Section("Before you file", Icons.Outlined.ReceiptLong, m) {
                val checks = FinanceBook.checks(f).filter { it.tab == com.saporini.mobile_desktop.finance.FinanceTab.EXPENSES }
                if (checks.isEmpty()) SmallMuted("Every expense has its receipt and VAT number.")
                checks.forEach { CheckLine(it.title, it.detail, it.serious) { kind = null } }
                SmallMuted("Food and drink come by themselves from Inventory → deliveries. Add bills like rent and electricity with Add expense.")
            }
        })
    val shown = f.expenses.filter { kind == null || it.kind == kind }.sortedByDescending { it.date }
    Section("Every expense", Icons.Outlined.ReceiptLong, Modifier.fillMaxWidth(), extra = "${shown.size} · ${fx.money(shown.sumOf { it.total })} with VAT") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KindChip("All", null, kind == null) { kind = null }
            f.expenses.map { it.kind }.distinct().sortedBy { it.ordinal }.forEach { k -> KindChip(k.label, k, kind == k) { kind = if (kind == k) null else k } }
        }
        (if (all) shown else shown.take(12)).forEach { e -> ExpenseLine(fx, e) }
        if (shown.size > 12) ShowAll(all, shown.size) { all = !all }
    }
}

@Composable
private fun KindChip(label: String, kind: ExpenseKind?, on: Boolean, onClick: () -> Unit) {
    val color = kind?.let { kindColor(it) } ?: Kit.Ink
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) color.copy(alpha = 0.12f) else Color.White)
        .border(1.dp, if (on) color.copy(alpha = 0.5f) else Kit.Border, RoundedCornerShape(50)).clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        kind?.let { Icon(kindIcon(it), null, Modifier.size(14.dp), tint = color) }
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (on) color else Kit.Ink, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseLine(fx: Fx, e: Expense) {
    val color = kindColor(e.kind)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)), Alignment.Center) {
            Icon(kindIcon(e.kind), null, Modifier.size(20.dp), tint = color)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(e.payee, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                translate = false)
            Text(listOfNotNull(shortDate(e.date), e.kind.label, e.invoiceNo?.let { "invoice $it" }, if (e.fromInventory) "from Inventory" else null).joinToString(" · "),
                fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!e.hasReceipt || e.vatNumberMissing) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (e.vatNumberMissing) Tag("No VAT number", Kit.Danger)
                if (!e.hasReceipt) Tag("No receipt", Kit.Amber)
            }
        }
        if (!e.hasReceipt) KitButton(if (fx.phone) "Receipt" else "Add receipt", {
            FinanceBook.addReceipt(e.id); fx.say("Receipt added for ${e.payee}")
        }, icon = Icons.Outlined.AddAPhoto, style = ButtonStyle.SECONDARY)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(fx.money(e.total), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
            Text(if (e.vat > 0) "VAT ${fx.money(e.vat)}" else "No VAT", fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted)
        }
    }
}

@Composable
internal fun Tag(text: String, color: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp),
        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = color, maxLines = 1)
}

/** Add expense: the green tab on the right edge, like the other "add" buttons (user rule). */
@Composable
internal fun BoxWithConstraintsScope.AddExpenseTab(fx: Fx, height: Dp) {
    var open by remember { mutableStateOf(false) }
    var position by rememberSaveable { mutableStateOf(0.3f) }
    val maxOffset = with(LocalDensity.current) { (height - edgeActionButtonHeight(fx.phone)).coerceAtLeast(0.dp).toPx() }
    RightEdgeActionButton("Add expense", { open = true }, enabled = true, width = if (fx.phone) 108.dp else 132.dp, compact = fx.phone,
        modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, kotlin.math.round(position * maxOffset).toInt()) }
            .draggable(orientation = Orientation.Vertical, state = rememberDraggableState { delta ->
                if (maxOffset > 0f) position = (position + delta / maxOffset).coerceIn(0f, 1f)
            }))
    if (open) AddExpenseDialog(fx, onClose = { open = false })
}

/** A bill or a receipt typed in: who, what kind, how much with VAT, the VAT rate, the invoice number, the receipt. */
@Composable
private fun AddExpenseDialog(fx: Fx, onClose: () -> Unit) {
    val setup = FinanceBook.setup
    val today = FinanceBook.today
    var payee by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ExpenseKind.OTHER) }
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(setup.standardRate) }
    var invoice by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(today.day.toString()) }
    var receipt by remember { mutableStateOf(true) }
    var tried by remember { mutableStateOf(false) }
    val cents = decimalTextToCents(amount.trim().replace(',', '.'))
    val dayNumber = day.trim().toIntOrNull()?.takeIf { it in 1..31 }
    val month = FinanceBook.month
    val date = dayNumber?.let { runCatching { LocalDate(month.year, month.month, it) }.getOrNull() }
    val problems = buildMap {
        if (payee.isBlank()) put("payee", "Who was it paid to?")
        if (cents == null || cents <= 0) put("amount", "The amount with VAT, e.g. 120.00")
        if (date == null || date > today) put("day", "A day of ${monthTitle(month)}, not after today")
    }
    val net = cents?.let { (it * 1000.0 / (1000 + rate)).roundToLong() }
    AppDialog("Add an expense", onClose, maxWidth = 620.dp, subtitle = "${monthTitle(month)} · it counts in this month's VAT and costs",
        buttons = {
            KitButton("Cancel", onClose, style = ButtonStyle.SECONDARY)
            KitButton("Add expense", {
                tried = true
                if (problems.isEmpty() && cents != null && net != null && date != null) {
                    FinanceBook.addExpense(Expense("own-${kotlin.random.Random.nextLong()}", date, payee.trim(), kind, net, cents - net,
                        invoice.trim().ifBlank { null }, hasReceipt = receipt, fromInventory = false))
                    fx.say("${payee.trim()} was added")
                    onClose()
                }
            })
        }) {
        val shown = if (tried) problems else emptyMap()
        FieldPair(
            { m -> TextInput(payee, { payee = it }, m, label = "Paid to", required = true, problem = shown["payee"], placeholder = "City Power", maxLength = 120) },
            { m -> SelectInput(kind, ExpenseKind.entries.filter { it != ExpenseKind.STOCK }, { it.label }, { kind = it }, m, label = "What it was") }
        )
        com.saporini.mobile_desktop.core.money.RealMoney {
            FieldPair(
                { m -> TextInput(amount, { amount = it }, m, label = "Amount with VAT", required = true, problem = shown["amount"], placeholder = "120.00",
                    suffix = fx.currency ?: "EUR") },
                { m -> SelectInput(rate, listOf(setup.standardRate, setup.reducedRate, 0), { if (it == 0) "No VAT" else "VAT ${pct(it)}" }, { rate = it }, m,
                    label = "VAT on it", hint = net?.let { n -> "${fx.money(n)} + VAT ${fx.money((cents ?: 0) - n)}" }) }
            )
        }
        FieldPair(
            { m -> TextInput(invoice, { invoice = it }, m, label = "Invoice number", optional = true, placeholder = "F-1024", maxLength = 40) },
            { m -> TextInput(day, { day = it }, m, label = "Day of the month", required = true, problem = shown["day"], placeholder = "${today.day}") }
        )
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.AddAPhoto, null, Modifier.size(20.dp), tint = Kit.Green)
            Column(Modifier.weight(1f)) {
                Text("I have the receipt", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                Text("Taking its photo comes with the production pass.", fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted)
            }
            KitSwitch(receipt, { receipt = it })
        }
        Text("Food and drink from suppliers come by themselves from Inventory; add them there.", fontFamily = Inter(), fontSize = 12.sp,
            color = Kit.Muted, textAlign = TextAlign.Start)
    }
}
