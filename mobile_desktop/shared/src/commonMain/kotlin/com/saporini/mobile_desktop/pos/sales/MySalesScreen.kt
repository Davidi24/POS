package com.saporini.mobile_desktop.pos.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.ValueLine
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.reservations.CompactDatePicker
import com.saporini.mobile_desktop.pos.reservations.HeaderButton
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import com.saporini.mobile_desktop.pos.reservations.ToolbarHeight
import com.saporini.mobile_desktop.pos.shifts.ShiftRepository
import com.saporini.mobile_desktop.pos.shifts.StaffPay
import com.saporini.mobile_desktop.pos.shifts.centsText
import com.saporini.mobile_desktop.pos.shifts.hoursText
import com.saporini.mobile_desktop.pos.shifts.longDay
import com.saporini.mobile_desktop.pos.shifts.toCents
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.*
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.overview_next_hours
import mobile_desktop.shared.generated.resources.settings_payments
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

// What the person earned: worked hours × wage + tips, for the day (or the chosen shift), the week and the month so far.
internal data class PayBit(val minutes: Long = 0, val wages: Long = 0, val tips: Long = 0) {
    val total get() = wages + tips
}
internal data class SalesPay(val currency: String, val rateCents: Long?, val day: PayBit, val week: PayBit, val month: PayBit, val missingRate: Boolean)

internal fun StaffPay.salesPay(currency: String, date: LocalDate, shiftId: String?): SalesPay {
    val weekStart = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))
    val monthStart = LocalDate(date.year, date.month, 1)
    fun bit(days: ClosedRange<LocalDate>, onlyShift: String? = null): PayBit {
        val lines = shifts.filter { line ->
            val d = runCatching { LocalDate.parse(line.date) }.getOrNull() ?: return@filter false
            // A chosen shift is found by itself (an overnight shift can start the day before).
            if (onlyShift != null) line.shiftId == onlyShift else d in days
        }
        val tipCents = if (onlyShift != null) 0 else tipsByDay.filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> d in days } == true }
            .sumOf { it.tips.toCents() ?: 0 }
        return PayBit(lines.sumOf { it.workedMinutes }, lines.sumOf { it.wages.toCents() ?: 0 }, tipCents)
    }
    return SalesPay(currency, hourlyRate.toCents(), bit(date..date, shiftId), bit(weekStart..date), bit(monthStart..date), missingRate)
}

private fun MySalesState.cents(value: OrderDecimal?): Long = value.toCents() ?: 0
private fun MySalesState.amount(value: OrderDecimal?): String = value?.let { centsText(it.toCents() ?: 0, totals?.currency ?: "EUR") } ?: "–"
private fun SalesReport?.time(value: String?): String = value?.let {
    runCatching { Instant.parse(it).toLocalDateTime(TimeZone.of(this?.timezone ?: "UTC")).time.toString().take(5) }.getOrNull()
} ?: "now"

@Composable
fun MySalesScreen(modifier: Modifier = Modifier, onShiftRequested: () -> Unit = {}) {
    val model = koinInject<MySalesScreenModel>()
    val state by model.state.collectAsState()
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(model, owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) model.setActive(true)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) model.setActive(false)
        }
        owner.lifecycle.addObserver(observer)
        model.setActive(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(observer); model.onDispose() }
    }
    // My pay comes from the shift records (hours × wage + tips): the month so far up to the day shown.
    val shifts = koinInject<ShiftRepository>()
    val session = koinInject<SessionManager>()
    val user by session.currentUser.collectAsState()
    val date = state.shownDate()
    var pay by remember { mutableStateOf<SalesPay?>(null) }
    LaunchedEffect(user?.id, date, state.filter.shiftId, state.report?.generatedAt) {
        val r = user?.restaurantId; val b = user?.defaultBranchId
        if (r == null || b == null || user?.permissions?.contains("SHIFT_SELF") != true) { pay = null; return@LaunchedEffect }
        val weekStart = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))
        val from = minOf(weekStart, LocalDate(date.year, date.month, 1))
        pay = try {
            val report = shifts.pay(r, b, from.toString(), date.toString(), mine = true)
            report.staff.firstOrNull { it.userId == user?.id }?.salesPay(report.currency, date, state.filter.shiftId)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
    }
    MySalesContent(state, model::date, model::shift, model::currency, { model.refresh() }, onShiftRequested, modifier, pay)
}

private fun MySalesState.shownDate(): LocalDate =
    filter.date ?: report?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

@Composable
internal fun MySalesContent(
    state: MySalesState, onDate: (LocalDate?) -> Unit, onShift: (String?) -> Unit,
    onCurrency: (String) -> Unit, onRefresh: () -> Unit, onShiftRequested: () -> Unit, modifier: Modifier = Modifier,
    pay: SalesPay? = null
) {
    val totals = state.totals
    val currency = totals?.currency ?: "EUR"
    val date = state.shownDate()
    val today = Clock.System.now().toLocalDateTime(state.report?.timezone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()).date
    val forShift = state.filter.shiftId != null

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SalesHeader(state, size, date, today, onDate, onShift, onCurrency, onRefresh)
            val notice = when {
                state.error != null && state.canRead -> state.error to true
                state.stale && state.report != null -> "These numbers may be a little behind. They refresh by themselves." to false
                totals?.ordersWithoutPayments?.let { it > 0 } == true ->
                    "${totals.ordersWithoutPayments} closed ${if (totals.ordersWithoutPayments == 1L) "order has" else "orders have"} no payment recorded yet, so tips and collected money leave them out." to false
                else -> null
            }
            notice?.let { (text, error) -> InfoBar(text, error) }

            when {
                !state.canRead -> Box(Modifier.fillMaxWidth().heightIn(min = 360.dp), Alignment.Center) {
                    OverviewEmpty("No access to sales", state.error ?: "Your role needs “View Orders” to see sales.", Icons.Outlined.Lock)
                }
                state.report == null && state.loading -> Box(Modifier.fillMaxWidth().heightIn(min = 360.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Kit.Green, strokeWidth = 3.dp)
                }
                state.report == null -> Box(Modifier.fillMaxWidth().heightIn(min = 360.dp), Alignment.Center) {
                    OverviewEmpty("Couldn't load your sales", "Check the connection, then refresh.", Icons.Outlined.CloudOff)
                }
                else -> {
                    val cards = listOf<@Composable (Modifier) -> Unit>(
                        { m ->
                            OverviewStatCard("Sales", state.amount(totals?.sales),
                                if ((totals?.ordersServed ?: 0) == 0L) "No closed orders yet" else "${totals?.ordersServed} orders · avg ${state.amount(totals?.averageTicket)}",
                                Kit.Green, m, image = Res.drawable.settings_payments)
                        },
                        { m ->
                            OverviewStatCard("Tips", state.amount(totals?.recordedTips),
                                "${totals?.paymentCount ?: 0} ${if (totals?.paymentCount == 1L) "payment" else "payments"} recorded",
                                Kit.Blue, m, icon = Icons.Outlined.VolunteerActivism)
                        },
                        { m ->
                            OverviewStatCard(if (forShift) "Earned this shift" else if (date == today) "Earned today" else "Earned that day",
                                pay?.let { centsText(it.day.total, it.currency) } ?: "–",
                                when {
                                    pay == null -> "Pay isn't available"
                                    pay.rateCents == null -> "Your hourly wage isn't set yet"
                                    else -> "${hoursText(pay.day.minutes)} × ${centsText(pay.rateCents, pay.currency)}${if (!forShift) " + tips" else ""}"
                                },
                                Kit.Amber, m, image = Res.drawable.overview_next_hours, imageScale = 1.3f)
                        },
                        { m ->
                            OverviewStatCard("Tables served", "${totals?.tablesServed ?: 0}",
                                "${totals?.guestsServed ?: 0} guests · ${totals?.openOrders ?: 0} still open",
                                Kit.Purple, m, image = Res.drawable.overview_guests)
                        }
                    )
                    if (size.isDesktop) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { cards.forEach { it(Modifier.weight(1f)) } }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards[0](Modifier.weight(1f)); cards[1](Modifier.weight(1f)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards[2](Modifier.weight(1f)); cards[3](Modifier.weight(1f)) }
                    }

                    val hours: @Composable (Modifier) -> Unit = { m -> HourlyPanel(m, state, currency) }
                    val payPanel: @Composable (Modifier) -> Unit = { m -> PayPanel(m, pay, forShift, date, today, onShiftRequested) }
                    val methods: @Composable (Modifier) -> Unit = { m -> MethodsPanel(m, state, currency) }
                    val items: @Composable (Modifier) -> Unit = { m -> TopItemsPanel(m, state, currency) }
                    val floors: @Composable (Modifier) -> Unit = { m -> FloorsPanel(m, state, currency) }
                    val recent: @Composable (Modifier) -> Unit = { m -> RecentPanel(m, state, currency) }
                    when (size) {
                        ScreenSize.DESKTOP -> {
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                hours(Modifier.weight(1.35f).fillMaxHeight()); payPanel(Modifier.weight(1f).fillMaxHeight()); methods(Modifier.weight(1f).fillMaxHeight())
                            }
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(Modifier.weight(1.1f).fillMaxHeight()); floors(Modifier.weight(0.9f).fillMaxHeight()); recent(Modifier.weight(1.3f).fillMaxHeight())
                            }
                        }
                        ScreenSize.TABLET -> {
                            hours(Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                payPanel(Modifier.weight(1f).fillMaxHeight()); methods(Modifier.weight(1f).fillMaxHeight())
                            }
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(Modifier.weight(1f).fillMaxHeight()); floors(Modifier.weight(1f).fillMaxHeight())
                            }
                            recent(Modifier.fillMaxWidth())
                        }
                        ScreenSize.PHONE -> {
                            payPanel(Modifier.fillMaxWidth()); hours(Modifier.fillMaxWidth()); methods(Modifier.fillMaxWidth())
                            items(Modifier.fillMaxWidth()); floors(Modifier.fillMaxWidth()); recent(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesHeader(
    state: MySalesState, size: ScreenSize, date: LocalDate, today: LocalDate,
    onDate: (LocalDate?) -> Unit, onShift: (String?) -> Unit, onCurrency: (String) -> Unit, onRefresh: () -> Unit
) {
    var dateMenu by remember { mutableStateOf(false) }
    val report = state.report
    val shiftChoices = report?.shifts.orEmpty()
    val shiftLabel = { shift: SalesShift -> "${report.time(shift.start)} – ${report.time(shift.end)}" }
    val selectedShift = shiftChoices.firstOrNull { it.id == state.filter.shiftId }
    val title: @Composable () -> Unit = {
        Column {
            Text("My Sales", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.sp, color = Kit.Ink)
            Text("${date.longDay()} · your sales, tips and pay", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
    }
    val controls: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconSquare(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Day before") { onDate(date.minus(DatePeriod(days = 1))) }
            Box {
                HeaderButton(if (date == today) "Today" else "${date.day} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${date.year}",
                    Icons.Outlined.CalendarToday, { dateMenu = true }, Modifier.width(170.dp))
                DropdownMenu(dateMenu, { dateMenu = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
                    CompactDatePicker(date) { onDate(if (it == today) null else it); dateMenu = false }
                }
            }
            IconSquare(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Day after", enabled = date < today) { if (date < today) onDate(date.plus(DatePeriod(days = 1)).takeIf { it != today }) }
            if (shiftChoices.isNotEmpty()) {
                HeaderDropdown(
                    selectedShift?.let(shiftLabel) ?: "All day", Icons.Outlined.Schedule, Modifier.width(150.dp),
                    listOf("All day") + shiftChoices.map(shiftLabel),
                    { label -> onShift(shiftChoices.firstOrNull { shiftLabel(it) == label }?.id) }
                )
            }
            val currencies = report?.currencies.orEmpty().map { it.currency }
            if (currencies.size > 1) HeaderDropdown(state.totals?.currency ?: currencies.first(), Icons.Outlined.Payments, Modifier.width(110.dp), currencies, onCurrency)
            Surface(onClick = onRefresh, modifier = Modifier.size(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), color = Kit.Green, strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, "Refresh", Modifier.size(18.dp), tint = Kit.Ink)
                }
            }
        }
    }
    if (size.isDesktop) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { title() }; controls() }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { title(); Box(Modifier.horizontalScroll(rememberScrollState())) { controls() } }
    }
}

@Composable
private fun IconSquare(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(20.dp), tint = if (enabled) Kit.Ink else Kit.Faint) }
    }
}

@Composable
private fun InfoBar(text: String, error: Boolean) {
    val color = if (error) Kit.Danger else Kit.Amber
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null, Modifier.size(18.dp), tint = color)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = if (error) color else Color(0xFF8A5A0B))
    }
}

// Closed sales per hour of the day, as bars with the amount on top; the busiest hour is darker.
@Composable
private fun HourlyPanel(modifier: Modifier, state: MySalesState, currency: String) {
    val hourly = state.totals?.hourly.orEmpty().mapNotNull { h ->
        val hour = h.hour.drop(11).take(2).toIntOrNull() ?: return@mapNotNull null
        hour to (h.sales.toCents() ?: 0)
    }.toMap()
    OverviewPanel(Icons.Outlined.BarChart, "Sales by hour", modifier, titleExtra = "closed orders") {
        if (hourly.isEmpty()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), Alignment.Center) {
                OverviewCompactEmpty("No sales yet", "Each hour's sales show here as your orders close.", Icons.Outlined.BarChart)
            }
            return@OverviewPanel
        }
        val first = (hourly.keys.min() - 1).coerceAtLeast(0)
        val last = (hourly.keys.max() + 1).coerceAtMost(23).coerceAtLeast(first + 5).coerceAtMost(23)
        val hours = (first..last).toList()
        val most = hourly.values.max().coerceAtLeast(1)
        val peak = hourly.maxBy { it.value }.key
        Row(Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            hours.forEach { hour ->
                val cents = hourly[hour] ?: 0
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (cents > 0) Text(shortMoney(cents, currency), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = if (hour == peak) Kit.Green else Kit.Muted, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.fillMaxWidth(0.72f).height(if (cents == 0L) 3.dp else (150.dp * (cents.toFloat() / most)).coerceAtLeast(5.dp))
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(when { cents == 0L -> Kit.Border; hour == peak -> Kit.Green; else -> Kit.Green.copy(alpha = 0.45f) })
                    )
                    Spacer(Modifier.height(5.dp))
                    Text("${hour.toString().padStart(2, '0')}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = if (hour == peak) Kit.Ink else Kit.Muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Kit.Green))
            Text("Busiest: ${peak.toString().padStart(2, '0')}:00 · ${centsText(hourly.getValue(peak), currency)}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        }
    }
}

private fun shortMoney(cents: Long, currency: String): String {
    val symbol = com.saporini.mobile_desktop.pos.shifts.currencySymbol(currency).trim()
    val whole = cents / 100
    return if (whole >= 1000) "$symbol${whole / 1000}.${(whole % 1000) / 100}k" else "$symbol$whole"
}

// Hours × wage + tips: for the day or shift shown, then the week and the month so far.
@Composable
private fun PayPanel(modifier: Modifier, pay: SalesPay?, forShift: Boolean, date: LocalDate, today: LocalDate, onShiftRequested: () -> Unit) {
    OverviewPanel(Icons.Outlined.AccountBalanceWallet, "My pay", modifier, action = {
        Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onShiftRequested).padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("My shift", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(14.dp), tint = Kit.Green)
        }
    }) {
        if (pay == null) {
            OverviewCompactEmpty("Pay isn't available", "It shows here once you work shifts with the “My Shifts” permission.", Icons.Outlined.AccountBalanceWallet)
            return@OverviewPanel
        }
        val c = pay.currency
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ValueLine("Hours worked", hoursText(pay.day.minutes))
            ValueLine("Hourly wage", pay.rateCents?.let { "${centsText(it, c)}/h" } ?: "Not set yet", valueColor = if (pay.rateCents == null) Kit.Amber else Kit.Ink)
            ValueLine("Wages", centsText(pay.day.wages, c))
            if (!forShift) ValueLine("Tips", centsText(pay.day.tips, c))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
            ValueLine(if (forShift) "Earned this shift" else if (date == today) "Earned today" else "Earned that day", centsText(pay.day.total, c), strong = true, valueColor = Kit.Green)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PayTile("Week so far", pay.week, c, Modifier.weight(1f))
            PayTile("Month so far", pay.month, c, Modifier.weight(1f))
        }
        if (pay.missingRate || pay.rateCents == null) {
            Text("Hours without a wage aren't paid here yet. Your manager sets wages in Admin Hub.", Modifier.padding(horizontal = 4.dp),
                fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Amber)
        }
    }
}

@Composable
private fun PayTile(label: String, bit: PayBit, currency: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
        Text(centsText(bit.total, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink, maxLines = 1)
        Text("${hoursText(bit.minutes)} · ${centsText(bit.tips, currency)} tips", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MethodsPanel(modifier: Modifier, state: MySalesState, currency: String) {
    val totals = state.totals
    val methods = totals?.paymentMethods.orEmpty()
    val sum = methods.sumOf { it.collected.toCents() ?: 0 }.coerceAtLeast(1)
    OverviewPanel(Icons.Outlined.CreditCard, "Payments", modifier, count = methods.sumOf { it.count }.toInt()) {
        if (methods.isEmpty()) {
            OverviewCompactEmpty("No payments yet", "Card and cash payments on your orders add up here.", Icons.Outlined.CreditCard)
        } else {
            methods.sortedByDescending { it.collected.toCents() ?: 0 }.forEachIndexed { index, method ->
                val cents = method.collected.toCents() ?: 0
                val color = listOf(Kit.Green, Kit.Blue, Kit.Amber, Kit.Purple, Kit.Grey)[index % 5]
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.12f)), Alignment.Center) {
                            Icon(if (method.name.contains("cash", true)) Icons.Outlined.Payments else Icons.Outlined.CreditCard, null, Modifier.size(15.dp), tint = color)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(method.name.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' '), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                        Text("${method.count}×", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                        Spacer(Modifier.width(10.dp))
                        Text(centsText(cents, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth(cents.toFloat() / sum).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
        if ((totals?.refunds.toCents() ?: 0) > 0) ValueLine("Refunded", "−${state.amount(totals?.refunds)}", valueColor = Kit.Danger)
        ValueLine("Collected", state.amount(totals?.collected), strong = true, valueColor = Kit.Green)
    }
}

@Composable
private fun TopItemsPanel(modifier: Modifier, state: MySalesState, currency: String) {
    val items = state.totals?.topItems.orEmpty()
    val most = items.maxOfOrNull { it.quantity }?.coerceAtLeast(1) ?: 1
    OverviewPanel(Icons.Outlined.Restaurant, "Top dishes", modifier, titleExtra = "by quantity") {
        if (items.isEmpty()) OverviewCompactEmpty("No dishes sold yet", "Your best sellers show here.", Icons.Outlined.Restaurant)
        items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(if (index == 0) Kit.Green else Kit.Tint), Alignment.Center) {
                    Text("${index + 1}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (index == 0) Color.White else Kit.Ink)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${item.quantity}×", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                        Spacer(Modifier.width(10.dp))
                        Text(centsText(item.sales.toCents() ?: 0, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                    }
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Kit.Tint)) {
                        Box(Modifier.fillMaxWidth(item.quantity.toFloat() / most).fillMaxHeight().clip(RoundedCornerShape(50)).background(Kit.Green.copy(alpha = if (index == 0) 1f else 0.5f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun FloorsPanel(modifier: Modifier, state: MySalesState, currency: String) {
    val areas = state.totals?.areas.orEmpty()
    OverviewPanel(Icons.Outlined.TableRestaurant, "Where you served", modifier) {
        if (areas.isEmpty()) OverviewCompactEmpty("No tables yet", "Floors you served show here.", Icons.Outlined.TableRestaurant)
        areas.forEach { area ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Kit.GreenSoft), Alignment.Center) {
                    Icon(Icons.Outlined.Layers, null, Modifier.size(16.dp), tint = Kit.Green)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(area.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${area.tables} ${if (area.tables == 1L) "table" else "tables"}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                }
                Text(centsText(area.sales.toCents() ?: 0, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
            }
        }
    }
}

@Composable
private fun RecentPanel(modifier: Modifier, state: MySalesState, currency: String) {
    val payments = state.totals?.recentPayments.orEmpty()
    OverviewPanel(Icons.AutoMirrored.Outlined.ReceiptLong, "Latest payments", modifier, count = payments.size) {
        if (payments.isEmpty()) OverviewCompactEmpty("No payments yet", "The latest payments on your orders show here.", Icons.AutoMirrored.Outlined.ReceiptLong)
        payments.forEach { payment ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Kit.GreenSoft), Alignment.Center) {
                    Text(payment.tableNumber?.take(4) ?: "—", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(payment.tableNumber?.let { "Table $it" } ?: "#${payment.orderNumber}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                    Text(
                        listOf(state.report.time(payment.paidAt), payment.method.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' '),
                            if (payment.guests == 1) "1 guest" else "${payment.guests} guests").joinToString(" · "),
                        fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(centsText(payment.collected.toCents() ?: 0, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                    when (payment.status) {
                        "REFUNDED" -> StatusChip("Refunded", Kit.Danger)
                        "PARTIALLY_REFUNDED" -> StatusChip("Part refunded", Kit.Amber)
                        else -> {}
                    }
                }
            }
        }
    }
}
