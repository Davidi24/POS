package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.components.InitialsAvatar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.ValueLine
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.PlatformVerticalScrollbar
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.reservations.CompactDatePicker
import com.saporini.mobile_desktop.pos.reservations.HeaderButton
import com.saporini.mobile_desktop.pos.reservations.ToolbarHeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.overview_next_hours
import mobile_desktop.shared.generated.resources.settings_payments
import org.koin.compose.koinInject
import kotlin.time.Clock

private const val WEEK = "Week"
private const val MONTH = "Month"

// Admin Hub → Shifts → Hours & pay: everyone's hours for a week or month, their hourly wage (set here), wages, tips
// and the total. Pay = worked hours × the wage the shift was clocked in at + tips recorded on their orders.
@Composable
internal fun HoursAndPay(state: ShiftState, modifier: Modifier = Modifier, titleExtra: (@Composable () -> Unit)?) {
    val repository = koinInject<ShiftRepository>()
    val session = koinInject<SessionManager>()
    val user by session.currentUser.collectAsState()
    val restaurant = user?.restaurantId
    val branch = user?.defaultBranchId
    val zone = state.zone
    val today = remember(zone) { Clock.System.now().toLocalDateTime(zone).date }
    var period by remember { mutableStateOf(WEEK) }
    var anchor by remember { mutableStateOf(state.date) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<StaffPay?>(null) }
    var report by remember { mutableStateOf<PayReport?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val from = if (period == MONTH) LocalDate(anchor.year, anchor.month, 1) else anchor.minus(DatePeriod(days = anchor.dayOfWeek.isoDayNumber - 1))
    val to = if (period == MONTH) from.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)) else from.plus(DatePeriod(days = 6))

    LaunchedEffect(restaurant, branch, from, to, reload) {
        if (restaurant == null || branch == null) return@LaunchedEffect
        loading = true
        try {
            val loaded = repository.pay(restaurant, branch, from.toString(), to.toString(), mine = false)
            report = loaded
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = (e as? ApiException)?.message ?: "Couldn't load hours and pay. Check the connection and try again." }
        finally { loading = false }
    }
    // Open shifts keep counting: refresh every minute while the page is open.
    LaunchedEffect(Unit) { while (true) { delay(60_000); reload++ } }

    val currency = report?.currency ?: "EUR"
    val showing = report?.takeIf { it.from == from.toString() && it.to == to.toString() }
    val people = showing?.staff.orEmpty()
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<StaffPay> { it.workedMinutes > 0 }.thenBy { it.name.lowercase() })
    val selected = people.firstOrNull { it.userId == selectedId } ?: people.firstOrNull { it.workedMinutes > 0 } ?: people.firstOrNull()
    val all = showing?.staff.orEmpty()
    val hours = all.sumOf { it.workedMinutes }
    val wages = all.sumOf { it.wages.toCents() ?: 0 }
    val tips = all.sumOf { it.tips.toCents() ?: 0 }
    val withoutWage = all.count { it.hourlyRate == null }
    val workedPeople = all.count { it.workedMinutes > 0 }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        val desktop = size.isDesktop
        Column(
            Modifier.fillMaxSize().then(if (desktop) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = if (desktop) 24.dp else 14.dp, vertical = if (desktop) 16.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PayHeader(size, titleExtra, period, { period = it }, from, to, anchor, { anchor = it }, today, query, { query = it }, loading) { reload++ }
            error?.let { ShiftMessage(it, error = true) { error = null } }

            if (!state.canManage) {
                Box(Modifier.fillMaxWidth().heightIn(min = 320.dp), Alignment.Center) {
                    OverviewEmpty("Managers only", "Hours and pay need the “Manage Shifts” permission.", Icons.Outlined.Lock)
                }
                return@Column
            }
            if (showing == null) {
                Box(Modifier.fillMaxWidth().heightIn(min = 320.dp), Alignment.Center) {
                    if (loading || error == null) CircularProgressIndicator(Modifier.size(28.dp), color = Kit.Green, strokeWidth = 3.dp)
                    else OverviewEmpty("Couldn't load hours and pay", "Check the connection, then try again.", Icons.Outlined.CloudOff)
                }
                return@Column
            }

            val tiles = listOf<@Composable (Modifier) -> Unit>(
                { m -> com.saporini.mobile_desktop.core.components.CompactStat("Hours worked", hoursText(hours), Kit.Green, m,
                    detail = "${all.sumOf { it.shiftCount }} shifts · $workedPeople ${if (workedPeople == 1) "person" else "people"}", icon = Icons.Outlined.Schedule) },
                { m -> com.saporini.mobile_desktop.core.components.CompactStat("Wages", centsText(wages, currency), Kit.Amber, m,
                    detail = if (withoutWage > 0) "$withoutWage without a wage" else null, icon = Icons.Outlined.Payments) },
                { m -> com.saporini.mobile_desktop.core.components.CompactStat("Tips", centsText(tips, currency), Kit.Blue, m,
                    detail = "on their orders", icon = Icons.Outlined.VolunteerActivism) },
                { m -> com.saporini.mobile_desktop.core.components.CompactStat("Total pay", centsText(wages + tips, currency), Kit.Purple, m,
                    detail = periodLabel(period, from, to), icon = Icons.Outlined.AccountBalanceWallet) }
            )
            if (desktop) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { tiles.forEach { it(Modifier.weight(1f)) } }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tiles[0](Modifier.weight(1f)); tiles[1](Modifier.weight(1f)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tiles[2](Modifier.weight(1f)); tiles[3](Modifier.weight(1f)) }
            }
            if (withoutWage > 0) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Kit.Amber.copy(alpha = 0.08f))
                        .border(1.dp, Kit.Amber.copy(alpha = 0.25f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = Kit.Amber)
                    Text("${if (withoutWage == 1) "1 person has" else "$withoutWage people have"} no hourly wage yet, so their hours show without pay. Choose “Set wage” on their row.",
                        fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Color(0xFF8A5A0B))
                }
            }

            val table: @Composable (Modifier) -> Unit = { m -> TeamTable(m, people, selected?.userId, currency, size, { selectedId = it }) { editing = it } }
            val person: @Composable (Modifier) -> Unit = { m -> PersonPanel(m, selected, currency, from, to, period, zone) { editing = it } }
            if (desktop) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    table(Modifier.weight(0.64f).fillMaxHeight())
                    person(Modifier.weight(0.36f).fillMaxHeight())
                }
            } else {
                table(Modifier.fillMaxWidth().heightIn(max = 560.dp))
                person(Modifier.fillMaxWidth())
            }
        }
    }

    editing?.let { person ->
        WageDialog(person, currency, onDismiss = { editing = null }) { cents ->
            val r = restaurant ?: return@WageDialog "Sign in again to change wages."
            val b = branch ?: return@WageDialog "Sign in again to change wages."
            try {
                repository.setPayRate(r, b, person.userId, centsToDecimal(cents))
                editing = null
                reload++
                null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { (e as? ApiException)?.message ?: "Couldn't save the wage. Check the connection and try again." }
        }
    }
}

@Composable
private fun PayHeader(
    size: ScreenSize, titleExtra: (@Composable () -> Unit)?,
    period: String, onPeriod: (String) -> Unit, from: LocalDate, to: LocalDate, anchor: LocalDate, onAnchor: (LocalDate) -> Unit, today: LocalDate,
    query: String, onQuery: (String) -> Unit, loading: Boolean, onRefresh: () -> Unit
) {
    var dateMenu by remember { mutableStateOf(false) }
    val step = { forward: Boolean ->
        onAnchor(if (period == MONTH) anchor.plus(DatePeriod(months = if (forward) 1 else -1)) else anchor.plus(DatePeriod(days = if (forward) 7 else -7)))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 1080.dp
        Row(
            Modifier.fillMaxWidth().then(if (wide) Modifier else Modifier.horizontalScroll(rememberScrollState())),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Shifts", Modifier.padding(end = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.sp, color = Kit.Ink)
            titleExtra?.invoke()
            com.saporini.mobile_desktop.pos.reservations.HeaderDropdown(period, Icons.Outlined.DateRange, Modifier.width(108.dp), listOf(WEEK, MONTH), onPeriod)
            SquareButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Earlier") { step(false) }
            Box {
                HeaderButton(periodLabel(period, from, to), Icons.Outlined.CalendarToday, { dateMenu = true }, Modifier.width(170.dp))
                DropdownMenu(dateMenu, { dateMenu = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
                    CompactDatePicker(anchor) { onAnchor(it); dateMenu = false }
                }
            }
            SquareButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Later") { step(true) }
            if (today !in from..to) SquareButton(Icons.Outlined.Today, if (period == MONTH) "This month" else "This week") { onAnchor(today) }
            if (wide) Spacer(Modifier.weight(1f)) else Spacer(Modifier.width(8.dp))
            SearchField(query, onQuery, Modifier.width(160.dp), placeholder = "Search staff", height = ToolbarHeight)
            Surface(onClick = onRefresh, modifier = Modifier.size(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                Box(contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Kit.Green, strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, "Refresh", Modifier.size(18.dp), tint = Kit.Ink)
                }
            }
        }
    }
}

// Column widths of the team table, so the header and the rows line up.
private object Cols {
    val shifts = 0.6f; val hours = 0.8f; val wage = 1.05f; val wages = 0.95f; val tips = 0.85f; val total = 0.95f; val person = 2.1f
}

@Composable
private fun TeamTable(modifier: Modifier, people: List<StaffPay>, selectedId: String?, currency: String, size: ScreenSize, onSelect: (String) -> Unit, onEditWage: (StaffPay) -> Unit) {
    val wide = !size.isPhone
    OverviewPanel(Icons.Outlined.Groups, "Team", modifier, count = people.size) {
        if (people.isEmpty()) {
            OverviewCompactEmpty("No staff found", "Nobody matches the search, or no one works at this branch yet.", Icons.Outlined.PersonSearch)
            return@OverviewPanel
        }
        if (wide) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderCell("Staff", Cols.person, TextAlign.Start)
                HeaderCell("Shifts", Cols.shifts); HeaderCell("Hours", Cols.hours); HeaderCell("Wage", Cols.wage)
                HeaderCell("Wages", Cols.wages); HeaderCell("Tips", Cols.tips); HeaderCell("Total", Cols.total)
            }
        }
        val listState = rememberLazyListState()
        Box(Modifier.weight(1f, fill = false)) {
            LazyColumn(state = listState, modifier = Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(people, key = { it.userId }) { person ->
                    PersonRow(person, person.userId == selectedId, currency, wide, { onSelect(person.userId) }) { onEditWage(person) }
                }
            }
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                PlatformVerticalScrollbar(state = listState, modifier = Modifier.fillMaxHeight().width(3.dp))
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign = TextAlign.End) {
    Text(text, Modifier.weight(weight), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, textAlign = align)
}

@Composable
private fun PersonRow(person: StaffPay, selected: Boolean, currency: String, wide: Boolean, onClick: () -> Unit, onEditWage: () -> Unit) {
    val rate = person.hourlyRate.toCents()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) Kit.GreenSoft else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.RowBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(if (wide) Cols.person else 1f), verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(person.name, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(person.name.ifBlank { "Staff member" }, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(person.roles.firstOrNull()?.lowercase()?.replaceFirstChar { it.uppercase() }?.replace('_', ' ') ?: "Staff",
                    fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
            }
        }
        if (wide) {
            Cell("${person.shiftCount}", Cols.shifts, muted = person.shiftCount == 0)
            Cell(if (person.workedMinutes == 0L) "–" else hoursText(person.workedMinutes), Cols.hours, muted = person.workedMinutes == 0L)
            Box(Modifier.weight(Cols.wage), contentAlignment = Alignment.CenterEnd) {
                if (rate == null) {
                    Surface(onClick = onEditWage, shape = RoundedCornerShape(50), color = Kit.Amber.copy(alpha = 0.12f)) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(13.dp), tint = Kit.Amber)
                            Text("Set wage", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Amber)
                        }
                    }
                } else {
                    Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onEditWage).padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${centsText(rate, currency)}/h", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                        Icon(Icons.Outlined.Edit, "Change wage", Modifier.size(14.dp), tint = Kit.Muted)
                    }
                }
            }
            Cell(if (person.workedMinutes == 0L) "–" else centsText(person.wages.toCents() ?: 0, currency), Cols.wages, muted = person.workedMinutes == 0L)
            Cell(centsText(person.tips.toCents() ?: 0, currency), Cols.tips, muted = (person.tips.toCents() ?: 0) == 0L)
            Cell(centsText(person.total.toCents() ?: 0, currency), Cols.total, strong = true, muted = (person.total.toCents() ?: 0) == 0L)
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(centsText(person.total.toCents() ?: 0, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                Text(if (person.workedMinutes == 0L) "No hours" else hoursText(person.workedMinutes), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            }
        }
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, strong: Boolean = false, muted: Boolean = false) {
    Text(text, Modifier.weight(weight), fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = 13.sp, color = if (muted) Kit.Faint else Kit.Ink, textAlign = TextAlign.End, maxLines = 1)
}

// One person: wage, the period's totals, hours per day and each worked shift with its pay.
@Composable
private fun PersonPanel(modifier: Modifier, person: StaffPay?, currency: String, from: LocalDate, to: LocalDate, period: String, zone: TimeZone, onEditWage: (StaffPay) -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        if (person == null) {
            Box(Modifier.fillMaxWidth().heightIn(min = 280.dp), Alignment.Center) {
                OverviewEmpty("Choose someone", "Their hours, wage and pay show here.", Icons.Outlined.Person)
            }
            return@Surface
        }
        val rate = person.hourlyRate.toCents()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(person.name, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name.ifBlank { "Staff member" }, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(person.roles.joinToString(" · ") { it.lowercase().replaceFirstChar { c -> c.uppercase() }.replace('_', ' ') }.ifBlank { "Staff" },
                        fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (rate == null) Kit.Amber.copy(alpha = 0.08f) else Kit.Canvas)
                    .border(1.dp, if (rate == null) Kit.Amber.copy(alpha = 0.3f) else Kit.Border, RoundedCornerShape(10.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hourly wage", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
                    Text(rate?.let { "${centsText(it, currency)} / hour" } ?: "Not set yet", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = if (rate == null) Kit.Amber else Kit.Ink)
                }
                Surface(onClick = { onEditWage(person) }, shape = RoundedCornerShape(8.dp), color = if (rate == null) Kit.Green else Color.White,
                    border = if (rate == null) null else BorderStroke(1.dp, Kit.Border)) {
                    Row(Modifier.height(36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (rate == null) Icons.Outlined.Add else Icons.Outlined.Edit, null, Modifier.size(16.dp), tint = if (rate == null) Color.White else Kit.Ink)
                        Text(if (rate == null) "Set wage" else "Change", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (rate == null) Color.White else Kit.Ink)
                    }
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ValueLine("Shifts worked", "${person.shiftCount}")
                    ValueLine("Hours worked", hoursText(person.workedMinutes))
                    ValueLine("Breaks", hoursText(person.breakMinutes))
                    ValueLine("Wages", centsText(person.wages.toCents() ?: 0, currency))
                    ValueLine("Tips", centsText(person.tips.toCents() ?: 0, currency))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
                    ValueLine("Total pay", centsText(person.total.toCents() ?: 0, currency), strong = true, valueColor = Kit.Green)
                    if (person.missingRate) Text("Some hours have no wage, so they aren't paid here yet.", Modifier.padding(horizontal = 4.dp),
                        fontFamily = Inter(), fontSize = 11.sp, color = Kit.Amber)
                }
                HoursChart(person, from, to, period)
                Text("Shifts", Modifier.padding(start = 4.dp, top = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                if (person.shifts.isEmpty()) {
                    OverviewCompactEmpty("No shifts worked", "Nothing clocked in ${if (period == MONTH) "this month" else "this week"}.", Icons.Outlined.Schedule)
                } else person.shifts.sortedByDescending { it.startedAt }.forEach { line ->
                    val date = runCatching { LocalDate.parse(line.date) }.getOrNull() ?: return@forEach
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.width(40.dp).clip(RoundedCornerShape(8.dp)).background(Kit.Tint).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(date.weekdayShort().uppercase(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = Kit.Muted)
                            Text("${date.day}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${line.startedAt.clock(zone)} – ${line.endedAt?.clock(zone) ?: "now"}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                            Text("${hoursText(line.workedMinutes)}${if (line.breakMinutes > 0) " · ${hoursText(line.breakMinutes)} break" else ""}${if (line.endedAt == null) " · still on" else ""}",
                                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                        }
                        val w = line.wages.toCents()
                        if (w == null) StatusChip("No wage", Kit.Amber)
                        else Column(horizontalAlignment = Alignment.End) {
                            Text(centsText(w, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                            line.hourlyRate.toCents()?.let { Text("${centsText(it, currency)}/h", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted) }
                        }
                    }
                }
            }
        }
    }
}

// Hours per day across the period as small bars (a week: 7 labelled bars; a month: one thin bar per day).
@Composable
private fun HoursChart(person: StaffPay, from: LocalDate, to: LocalDate, period: String) {
    val days = generateSequence(from) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= to }.toList()
    val perDay = person.shifts.groupBy { it.date }.mapValues { (_, lines) -> lines.sumOf { it.workedMinutes } }
    val most = (perDay.values.maxOrNull() ?: 0L).coerceAtLeast(8 * 60L)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Hours per day", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
        Row(Modifier.fillMaxWidth().height(86.dp), horizontalArrangement = Arrangement.spacedBy(if (period == MONTH) 2.dp else 8.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { day ->
                val minutes = perDay[day.toString()] ?: 0L
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (period == WEEK && minutes > 0) Text(hoursText(minutes), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = Kit.Muted, maxLines = 1)
                    Box(
                        Modifier.fillMaxWidth().height(if (minutes == 0L) 3.dp else (62.dp * (minutes.toFloat() / most)).coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (minutes == 0L) Kit.Border else Kit.Green)
                    )
                }
            }
        }
        if (period == WEEK) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { Text(it.weekdayShort().take(2), Modifier.weight(1f), fontFamily = Inter(), fontSize = 9.sp, color = Kit.Muted, textAlign = TextAlign.Center) }
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                Text(from.shortDay(), fontFamily = Inter(), fontSize = 9.sp, color = Kit.Muted)
                Spacer(Modifier.weight(1f))
                Text(to.shortDay(), fontFamily = Inter(), fontSize = 9.sp, color = Kit.Muted)
            }
        }
    }
}

// Set or change someone's hourly wage. Returns an error message from the save, or null when it worked.
@Composable
private fun WageDialog(person: StaffPay, currency: String, onDismiss: () -> Unit, onSave: suspend (Long) -> String?) {
    val start = person.hourlyRate.toCents()
    var text by remember(person.userId) { mutableStateOf(start?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" } ?: "") }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save() {
        val cents = text.replace(',', '.').toCentsOrNull()
        problem = when {
            cents == null -> "Enter an amount like 12.50."
            cents < 0 -> "The wage can't be negative."
            cents > 1_000_000 -> "That wage is too high. Check the amount."
            else -> null
        }
        if (problem != null || cents == null) return
        saving = true
        scope.launch { problem = onSave(cents); saving = false }
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 440.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(person.name, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Hourly wage", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink)
                        Text(person.name, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                    }
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = !saving, onClick = onDismiss), Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Close", Modifier.size(20.dp), tint = Kit.Ink)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp))
                        .border(1.5.dp, if (problem != null) Kit.Danger else Kit.Green, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currencySymbol(currency).trim(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Muted)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        text, { value -> text = value.filter { it.isDigit() || it == '.' || it == ',' }.take(9); problem = null },
                        Modifier.weight(1f), singleLine = true, enabled = !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Kit.Ink),
                        decorationBox = { inner -> Box { if (text.isEmpty()) Text("0.00", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Kit.Faint); inner() } }
                    )
                    Text("per hour", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                }
                problem?.let { Text(it, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Kit.Danger) }
                Text(
                    "New shifts are paid at this wage from the moment they clock in. Shifts already worked keep their wage; " +
                        "shifts worked before a wage was set get this one.",
                    fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    Surface(onClick = onDismiss, enabled = !saving, shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                        Box(Modifier.height(42.dp).padding(horizontal = 18.dp), Alignment.Center) {
                            Text("Cancel", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                        }
                    }
                    Surface(onClick = { save() }, enabled = !saving, shape = RoundedCornerShape(8.dp), color = Kit.Green) {
                        Row(Modifier.height(42.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (saving) CircularProgressIndicator(Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Check, null, Modifier.size(17.dp), tint = Color.White)
                            Text(if (saving) "Saving…" else "Save wage", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun periodLabel(period: String, from: LocalDate, to: LocalDate): String =
    if (period == MONTH) "${from.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${from.year}"
    else "${from.shortDay()} – ${to.shortDay()}"
