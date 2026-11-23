package com.saporini.mobile_desktop.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PanelRow
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.finance.FilingState
import com.saporini.mobile_desktop.finance.FinanceBook
import com.saporini.mobile_desktop.finance.MonthFigures
import com.saporini.mobile_desktop.finance.monthTitle
import com.saporini.mobile_desktop.finance.pct
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_reservations
import mobile_desktop.shared.generated.resources.role_finance
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.supplier_deliveries

/** VAT for the month: on sales by rate, on purchases by supplier, what's left to pay, and the two books. */
@Composable
internal fun VatPage(fx: Fx) {
    val f = FinanceBook.figures()
    val deadline = FinanceBook.deadlines().firstOrNull { it.key == "VAT-${f.month}" }
    val state = deadline?.let { FinanceBook.state(it) }
    PageHeader("VAT", deadline?.let { "${monthTitle(f.month)} · return and payment by ${shortDate(it.due, withYear = true)}" } ?: monthTitle(f.month)) {
        MonthSwitcher(fx.phone)
        if (!fx.phone) KitButton("Download", { fx.say("The VAT books are ready to download once the app is connected (production pass).") },
            icon = Icons.Outlined.Download, style = ButtonStyle.SECONDARY)
    }
    BranchPartNote()
    SummaryCardRow(fx.size, listOf(
        { m -> OverviewStatCard("VAT on sales", fx.money(f.outputVat), "On ${fx.money(f.salesNet)} of sales", Kit.Green, m, image = Res.drawable.settings_payments) },
        { m -> OverviewStatCard("VAT on purchases", fx.money(f.inputVat), "${f.expenses.count { it.vat > 0 }} invoices with VAT", Kit.Blue, m,
            image = Res.drawable.supplier_deliveries) },
        { m -> OverviewStatCard(if (f.vatToPay >= 0) "VAT to pay" else "VAT to get back", fx.money(kotlin.math.abs(f.vatToPay)),
            if (f.finished) "For ${monthTitle(f.month)}" else "So far this month", Kit.Amber, m, image = Res.drawable.role_finance) },
        { m -> OverviewStatCard("Return", state?.label ?: "–", deadline?.let { "Due ${shortDate(it.due)} · ${whenText(it.due)}" } ?: "", state?.let { stateColor(it) } ?: Kit.Grey,
            m, image = Res.drawable.overview_reservations) }
    ))
    if (deadline != null && state != null && state != FilingState.NOT_YET && state != FilingState.PAID) {
        FileStrip(state, onFiled = { FinanceBook.markFiled(deadline.key); fx.say("Marked as filed. Pay it by ${shortDate(deadline.due)}.") },
            onPaid = { FinanceBook.markPaid(deadline.key); fx.say("Marked as paid") })
    }
    PanelRow(fx.size,
        1f to { m -> SalesByRate(fx, f, m) },
        1f to { m -> PurchasesBySupplier(fx, f, m) })
    Books(fx, f)
}

/** "Filed it? Paid it?": the two marks, so the deadline list knows. */
@Composable
internal fun FileStrip(state: FilingState, onFiled: () -> Unit, onPaid: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(stateColor(state).copy(alpha = 0.07f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (state == FilingState.READY) "The month is over: this return can be filed" else "Filed. Now it's to pay",
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
            Text(if (state == FilingState.READY) "Type the numbers from Tax returns into e-Filing, then mark it here." else "Mark it once the money left the bank.",
                fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
        }
        if (state == FilingState.READY) KitButton("Mark as filed", onFiled, style = ButtonStyle.SECONDARY)
        KitButton("Mark as paid", onPaid)
    }
}

@Composable
private fun SalesByRate(fx: Fx, f: MonthFigures, modifier: Modifier) {
    Section("VAT on sales by rate", Icons.Outlined.PointOfSale, modifier) {
        TableHead(listOf("Rate", "Sales without VAT", "VAT"), fx.phone)
        f.sales.forEach { line ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pct(line.rate), Modifier.width(52.dp).clip(RoundedCornerShape(6.dp)).background(Kit.GreenSoft).padding(vertical = 4.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Green, textAlign = TextAlign.Center, translate = false)
                    Text(line.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = if (line.net == 0L) Kit.Muted else Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(fx.money(line.net), Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.End)
                Text(fx.money(line.vat), Modifier.weight(0.8f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.End)
            }
            ThinLine()
        }
        TotalRow("Together", fx.money(f.salesNet), fx.money(f.outputVat))
    }
}

@Composable
private fun PurchasesBySupplier(fx: Fx, f: MonthFigures, modifier: Modifier) {
    val bySupplier = f.expenses.filter { it.vat > 0 }.groupBy { it.payee }.entries.sortedByDescending { e -> e.value.sumOf { it.vat } }
    Section("VAT on purchases", Icons.Outlined.LocalShipping, modifier, extra = "${bySupplier.size} suppliers") {
        TableHead(listOf("Supplier", "Without VAT", "VAT"), fx.phone)
        bySupplier.take(8).forEach { (name, list) ->
            val missing = list.any { it.vatNumberMissing }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.3f)) {
                    Text(name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                    if (missing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.WarningAmber, null, Modifier.size(12.dp), tint = Kit.Danger)
                        Text("No VAT number on one invoice", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Danger)
                    } else Text(if (list.size == 1) "1 invoice" else "${list.size} invoices", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                }
                Text(fx.money(list.sumOf { it.net }), Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.End)
                Text(fx.money(list.sumOf { it.vat }), Modifier.weight(0.8f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.End)
            }
            ThinLine()
        }
        if (bySupplier.size > 8) Text("And ${bySupplier.size - 8} more in the purchase book below", Modifier.padding(4.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        TotalRow("Together", fx.money(f.expenses.filter { it.vat > 0 }.sumOf { it.net }), fx.money(f.inputVat))
    }
}

@Composable
internal fun TableHead(names: List<String>, phone: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Kit.Canvas).padding(horizontal = 4.dp, vertical = 7.dp)) {
        names.forEachIndexed { i, name ->
            Text(name.uppercase(), Modifier.weight(if (i == 0) 1.3f else if (i == names.lastIndex) 0.8f else 1f), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                fontSize = 10.sp, letterSpacing = 0.5.sp, color = Kit.Muted, textAlign = if (i == 0) TextAlign.Start else TextAlign.End, maxLines = 1)
        }
    }
}

@Composable
internal fun TotalRow(label: String, middle: String, last: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.GreenSoft).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.3f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
        Text(middle, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.End)
        Text(last, Modifier.weight(0.8f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Green, textAlign = TextAlign.End)
    }
}

/** The sales book (a line a day) and the purchase book (a line an invoice), as the tax office asks for them. */
@Composable
private fun Books(fx: Fx, f: MonthFigures) {
    var book by remember { mutableStateOf("Sales book") }
    var all by remember(book, f.month) { mutableStateOf(false) }
    Section("The books", Icons.Outlined.MenuBook, Modifier.fillMaxWidth(), action = {
        OverviewTabs(listOf("Sales book", "Purchase book"), book, { book = it }, Modifier.width(if (fx.phone) 210.dp else 260.dp))
    }) {
        if (book == "Sales book") {
            TableHead(listOf("Day", "Bills", "Without VAT", "VAT"), fx.phone)
            val days = f.salesDays.reversed()
            (if (all) days else days.take(10)).forEach { day ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.saporini.mobile_desktop.core.format.weekdayDate(day.date), Modifier.weight(1.3f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp, color = Kit.Ink)
                    Text("${day.bills}", Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted, textAlign = TextAlign.End, translate = false)
                    Text(fx.money(day.net), Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.End)
                    Text(fx.money(day.vat), Modifier.weight(0.8f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.End)
                }
                ThinLine()
            }
            if (days.size > 10) ShowAll(all, days.size) { all = !all }
        } else {
            TableHead(listOf("Invoice", "Supplier", "Without VAT", "VAT"), fx.phone)
            val invoices = f.expenses.filter { it.vat > 0 }.reversed()
            (if (all) invoices else invoices.take(10)).forEach { e ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.3f)) {
                        Text(e.invoiceNo ?: "–", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink, translate = false)
                        Text(shortDate(e.date), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                    }
                    Text(e.payee, Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Ink, textAlign = TextAlign.End, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, translate = false)
                    Text(fx.money(e.net), Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.End)
                    Text(fx.money(e.vat), Modifier.weight(0.8f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = if (e.vatNumberMissing) Kit.Danger else Kit.Ink, textAlign = TextAlign.End)
                }
                ThinLine()
            }
            if (invoices.size > 10) ShowAll(all, invoices.size) { all = !all }
        }
    }
}

@Composable
internal fun ShowAll(all: Boolean, count: Int, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), Alignment.Center) {
        GoPill(if (all) "Show fewer" else "Show all $count", onClick)
    }
}

@Composable
internal fun SmallMuted(text: String, color: Color = Kit.Muted) = Text(text, fontFamily = Inter(), fontSize = 12.sp, color = color)
