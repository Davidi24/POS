package com.saporini.mobile_desktop.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Place
import androidx.compose.foundation.layout.size
import mobile_desktop.shared.generated.resources.supplier_place
import androidx.compose.runtime.collectAsState
import mobile_desktop.shared.generated.resources.overview_reservations
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitSwitch
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.UnsavedChangesBar
import com.saporini.mobile_desktop.core.format.decimalTextToCents
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.money.RealMoney
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.finance.FinanceBook
import com.saporini.mobile_desktop.finance.TaxSetup
import com.saporini.mobile_desktop.platform.ui.CarefulBanner
import com.saporini.mobile_desktop.platform.ui.InfoSection
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_stock_value
import mobile_desktop.shared.generated.resources.role_finance
import mobile_desktop.shared.generated.resources.salary_managers
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.settings_shifts
import mobile_desktop.shared.generated.resources.workspace_restaurants

private val Countries = listOf("Albania", "Italy", "Spain", "United Kingdom", "United States", "Dominican Republic")

/**
 * The tax rules every amount on these pages comes from, set once: where the restaurant pays taxes, VAT, due days,
 * wages, profit tax, how much to keep aside. Saved with the floating bar (MOCK: kept on this device until the
 * production pass).
 */
@Composable
internal fun TaxSetupPage(fx: Fx, modifier: Modifier) {
    val saved = FinanceBook.setup
    var draft by remember(saved) { mutableStateOf(saved) }
    // Discard puts the typed fields back too.
    var round by remember { mutableStateOf(0) }
    val changed = draft != saved
    Box(modifier) {
        ScrollColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageHeader("Tax setup", "The rules every amount here comes from")
            CarefulBanner("These rules decide every amount in Finance",
                "The ones filled in are Albania's as the app knows them. Check them once with the tax office or an accountant; they rarely change.", fx.phone)
            androidx.compose.runtime.key(round) { RealMoney {
                val left: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        WhereSection(fx, draft) { draft = it }
                        TaxNumbersSection(fx)
                        VatSection(fx, draft) { draft = it }
                        DaysSection(fx, draft) { draft = it }
                    }
                }
                val right: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        WagesSection(fx, draft) { draft = it }
                        ProfitSection(fx, draft) { draft = it }
                        AsideSection(fx, draft) { draft = it }
                    }
                }
                if (fx.wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    left(Modifier.weight(1f))
                    right(Modifier.weight(1f))
                } else {
                    left(Modifier.fillMaxWidth())
                    right(Modifier.fillMaxWidth())
                }
            } }
            Spacer(Modifier.height(if (changed) 96.dp else 28.dp))
        }
        if (changed) UnsavedChangesBar(saving = false, problem = null, error = null, onDiscard = { draft = saved; round++ },
            onSave = { FinanceBook.setup = draft; fx.say("Tax setup saved") },
            message = if (fx.phone) "Unsaved changes" else "You changed the tax rules",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
    }
}

@Composable
private fun WhereSection(fx: Fx, d: TaxSetup, set: (TaxSetup) -> Unit) {
    InfoSection("Where you pay taxes", "The country decides the rules below.", picture = Res.drawable.workspace_restaurants, compact = fx.phone) {
        SelectInput(d.country, Countries, { it }, { set(d.copy(country = it)) }, Modifier.fillMaxWidth(), label = "Country")
        if (d.country != "Albania") Text("Ready-made rules for ${d.country} aren't in the app yet: check every rate below yourself.",
            Modifier.clip(RoundedCornerShape(10.dp)).background(Kit.Amber.copy(alpha = 0.08f)).padding(10.dp), fontFamily = Inter(), fontSize = 12.5.sp,
            lineHeight = 17.sp, color = Kit.Amber)
        SwitchLine("Registered for VAT", "Off for a small business under the VAT limit: no VAT on sales, none taken back.", d.vatRegistered) {
            set(d.copy(vatRegistered = it))
        }
        if (d.vatRegistered) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("VAT return every", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
            OverviewTabs(listOf("Month", "Quarter"), if (d.vatEveryMonths == 1) "Month" else "Quarter",
                { set(d.copy(vatEveryMonths = if (it == "Month") 1 else 3)) }, Modifier.width(200.dp))
        }
        Text("The tax number (NIPT) is under Tax numbers.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
    }
}

/**
 * The restaurant's legal name and tax numbers, from the server (moved here from Restaurant info's Legal & tax, user
 * 2026-11-23; only the Super Admin changes them), then the company's addresses (moved from Restaurant info → Details the
 * same day; the owner changes them).
 */
@Composable
private fun TaxNumbersSection(fx: Fx) {
    val model = org.koin.compose.koinInject<com.saporini.mobile_desktop.platform.RestaurantDetailsScreenModel>()
    com.saporini.mobile_desktop.core.components.BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    val restaurant = state.restaurant
    InfoSection("Tax numbers", "Printed on receipts and invoices.", picture = Res.drawable.overview_reservations, compact = fx.phone,
        trailing = if (state.canManage) ({ com.saporini.mobile_desktop.platform.ui.AddPill("Add tax details", model::newTax) })
            else ({ com.saporini.mobile_desktop.platform.ui.LockedNote("Set by the Super Admin") })) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Legal name", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Kit.Muted)
            Text(restaurant?.legalName?.ifBlank { null } ?: "–", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, translate = false)
        }
        if (state.loaded && state.taxProfiles.isEmpty()) Text("No tax numbers yet.", fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
        state.taxProfiles.forEach { tax ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(12.dp))
                .then(if (state.canManage) Modifier.clickable { model.editTax(tax) } else Modifier).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(listOfNotNull(tax.vatNumber?.let { "VAT $it" }, tax.taxNumber?.let { "Tax no. $it" }).joinToString(" · ").ifBlank { tax.country },
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink, translate = false)
                    Text(listOfNotNull(tax.country, tax.fiscalCode?.let { "Fiscal code $it" }, tax.taxOffice).joinToString(" · "), fontFamily = Inter(),
                        fontSize = 12.sp, color = Kit.Muted, translate = false)
                }
                if (tax.isDefault) com.saporini.mobile_desktop.platform.ui.DotPill("On receipts", Kit.Green)
                if (state.canManage) com.saporini.mobile_desktop.core.components.RowActionsMenu(listOf(
                    com.saporini.mobile_desktop.core.components.RowAction("Edit", androidx.compose.material.icons.Icons.Outlined.Edit) { model.editTax(tax) },
                    com.saporini.mobile_desktop.core.components.RowAction("Remove", androidx.compose.material.icons.Icons.Outlined.Delete, danger = true) { model.askRemoveTax(tax) }
                ), state.saving)
            }
        }
    }
    InfoSection("Company addresses", "Where the company is registered, billed and run from. Printed on invoices.", picture = Res.drawable.supplier_place,
        compact = fx.phone, trailing = if (state.canEdit) ({ com.saporini.mobile_desktop.platform.ui.AddPill("Add address", model::newAddress) }) else null) {
        if (state.loaded && state.addresses.isEmpty()) Text("No address yet. Receipts print the branch's address.", fontFamily = Inter(), fontSize = 12.5.sp,
            color = Kit.Muted)
        state.addresses.forEach { address ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(12.dp))
                .then(if (state.canEdit) Modifier.clickable { model.editAddress(address) } else Modifier).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Kit.GreenSoft), Alignment.Center) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Place, null, Modifier.size(18.dp), tint = Kit.Green)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(com.saporini.mobile_desktop.platform.addressTypeLabel(address.addressType), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp, color = Kit.Muted)
                    Text(address.oneLine.ifBlank { "Address" }, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink,
                        maxLines = 2, translate = false)
                }
                if (address.isPrimary) com.saporini.mobile_desktop.platform.ui.DotPill("Main", Kit.Green)
                if (state.canEdit) com.saporini.mobile_desktop.core.components.RowActionsMenu(listOf(
                    com.saporini.mobile_desktop.core.components.RowAction("Edit", androidx.compose.material.icons.Icons.Outlined.Edit) { model.editAddress(address) },
                    com.saporini.mobile_desktop.core.components.RowAction("Remove", androidx.compose.material.icons.Icons.Outlined.Delete, danger = true) { model.askRemoveAddress(address) }
                ), state.saving)
            }
        }
    }
    com.saporini.mobile_desktop.platform.ui.CompanyDialogs(state, model)
    com.saporini.mobile_desktop.core.components.ScreenMessages(state.error.takeIf { state.tax == null && state.address == null && state.confirm == null },
        state.notice, model::clearMessages)
}

@Composable
private fun VatSection(fx: Fx, d: TaxSetup, set: (TaxSetup) -> Unit) {
    InfoSection("VAT rates", "What's added to the price of what you sell.", picture = Res.drawable.settings_payments, compact = fx.phone) {
        FieldPair(
            { m -> PercentField("Standard rate", d.standardRate, m, d.vatRegistered) { set(d.copy(standardRate = it)) } },
            { m -> PercentField("Reduced rate", d.reducedRate, m, d.vatRegistered) { set(d.copy(reducedRate = it)) } },
            wide = !fx.phone
        )
        Text("Every dish counts at the standard rate unless its menu section says otherwise (Menu → a section → VAT).", fontFamily = Inter(), fontSize = 12.sp,
            lineHeight = 17.sp, color = Kit.Muted)
    }
}

@Composable
private fun DaysSection(fx: Fx, d: TaxSetup, set: (TaxSetup) -> Unit) {
    InfoSection("Due days and reminders", "By which day of the next month each is filed and paid.", picture = Res.drawable.settings_shifts, compact = fx.phone) {
        FieldPair(
            { m -> NumberField("VAT by the", d.vatDueDay, m, "th", 1..28) { set(d.copy(vatDueDay = it)) } },
            { m -> NumberField("Payroll by the", d.payrollDueDay, m, "th", 1..28) { set(d.copy(payrollDueDay = it)) } },
            wide = !fx.phone
        )
        NumberField("Remind me", d.remindDaysBefore, Modifier.fillMaxWidth(), "days before", 0..30) { set(d.copy(remindDaysBefore = it)) }
    }
}

@Composable
private fun WagesSection(fx: Fx, d: TaxSetup, set: (TaxSetup) -> Unit) {
    InfoSection("Wages", "Contributions and income tax on what people earn.", picture = Res.drawable.salary_managers, compact = fx.phone) {
        Label("Held back from the wage")
        FieldPair(
            { m -> PercentField("Social", d.employeeSocial, m) { set(d.copy(employeeSocial = it)) } },
            { m -> PercentField("Health", d.employeeHealth, m) { set(d.copy(employeeHealth = it)) } },
            wide = !fx.phone
        )
        Label("Paid by the restaurant on top")
        FieldPair(
            { m -> PercentField("Social", d.employerSocial, m) { set(d.copy(employerSocial = it)) } },
            { m -> PercentField("Health", d.employerHealth, m) { set(d.copy(employerHealth = it)) } },
            wide = !fx.phone
        )
        Label("Income tax a month")
        FieldPair(
            { m -> MoneyField("Nothing up to", d.incomeFreeUpTo, m, fx.currency) { set(d.copy(incomeFreeUpTo = it)) } },
            { m -> MoneyField("Middle rate up to", d.incomeMiddleUpTo, m, fx.currency) { set(d.copy(incomeMiddleUpTo = it)) } },
            wide = !fx.phone
        )
        FieldPair(
            { m -> PercentField("Middle rate", d.incomeMiddleRate, m) { set(d.copy(incomeMiddleRate = it)) } },
            { m -> PercentField("Top rate", d.incomeTopRate, m) { set(d.copy(incomeTopRate = it)) } },
            wide = !fx.phone
        )
    }
}

@Composable
private fun ProfitSection(fx: Fx, d: TaxSetup, set: (TaxSetup) -> Unit) {
    InfoSection("Profit tax", "On what's left after costs, once a year, with advances.", picture = Res.drawable.role_finance, compact = fx.phone) {
        PercentField("Rate", d.profitTaxRate, Modifier.fillMaxWidth(), !d.smallBusiness) { set(d.copy(profitTaxRate = it)) }
        SwitchLine("Small business", "Under the small business limit no profit tax is paid.", d.smallBusiness) { set(d.copy(smallBusiness = it)) }
    }
}

@Composable
private fun AsideSection(fx: Fx, d: TaxSetup, set: (TaxSetup) -> Unit) {
    InfoSection("Keep aside and your accountant", "So the money is there when the taxes are due.", picture = Res.drawable.inventory_stock_value, compact = fx.phone) {
        NumberField("Keep aside", d.setAsidePercent, Modifier.fillMaxWidth(), "% of sales", 0..60) { set(d.copy(setAsidePercent = it)) }
        TextInput(d.accountantEmail, { set(d.copy(accountantEmail = it.trim())) }, Modifier.fillMaxWidth(), label = "Accountant's email", optional = true,
            placeholder = "accountant@studio.al", maxLength = 200, hint = "If you have one: the returns and books can be sent to them.")
    }
}

@Composable
private fun Label(text: String) = Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Kit.Muted)

@Composable
private fun SwitchLine(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
            Text(detail, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
        }
        KitSwitch(on, onChange)
    }
}

/** A percentage typed as "16.7"; kept in tenths. */
@Composable
private fun PercentField(label: String, tenths: Int, modifier: Modifier, enabled: Boolean = true, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(com.saporini.mobile_desktop.finance.pct(tenths).removeSuffix("%")) }
    val parsed = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
    TextInput(text, { v -> text = v; v.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }?.let { onChange((it * 10).toInt()) } },
        modifier, label = label, suffix = "%", enabled = enabled, problem = if (text.isNotBlank() && parsed == null) "0 to 100" else null)
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, suffix: String, range: IntRange, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    val parsed = text.trim().toIntOrNull()?.takeIf { it in range }
    TextInput(text, { v -> text = v; v.trim().toIntOrNull()?.takeIf { it in range }?.let(onChange) }, modifier, label = label, suffix = suffix,
        problem = if (text.isNotBlank() && parsed == null) "${range.first} to ${range.last}" else null)
}

@Composable
private fun MoneyField(label: String, cents: Long, modifier: Modifier, currency: String?, onChange: (Long) -> Unit) {
    var text by remember { mutableStateOf("${cents / 100}.${(cents % 100).toString().padStart(2, '0')}") }
    TextInput(text, { v -> text = v; decimalTextToCents(v.trim().replace(',', '.'))?.let(onChange) }, modifier, label = label, suffix = currency ?: "EUR")
}
