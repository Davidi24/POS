package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import com.saporini.mobile_desktop.core.components.PeopleLinesSkeleton
import com.saporini.mobile_desktop.core.components.loadingSwap
import com.saporini.mobile_desktop.core.components.steadyLoading
import com.saporini.mobile_desktop.core.components.SkeletonBox
import com.saporini.mobile_desktop.core.components.SkeletonLight
import com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.ui.OptionGroup
import com.saporini.mobile_desktop.admin.inventory.ui.OptionLine
import com.saporini.mobile_desktop.admin.inventory.ui.PhoneControlHeight
import com.saporini.mobile_desktop.admin.inventory.ui.PhoneOptionsButton
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldBlock
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.ScrollList
import com.saporini.mobile_desktop.core.components.ScrollbarGap
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.ValueLine
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.core.ui.HoverTooltip
import com.saporini.mobile_desktop.pos.reservations.CompactDatePicker
import com.saporini.mobile_desktop.pos.reservations.HeaderButton
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_stock_value
import mobile_desktop.shared.generated.resources.salary_cleaning
import mobile_desktop.shared.generated.resources.salary_extra
import mobile_desktop.shared.generated.resources.salary_kitchen
import mobile_desktop.shared.generated.resources.salary_managers
import mobile_desktop.shared.generated.resources.salary_waiters
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.workspace_statistics
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import kotlin.time.Clock

private const val MONTH = "Month"

/** The list of everyone, opened from "See everyone" (the teams use their [PayTeam] name). */
private const val EVERYONE = "EVERYONE"

// Admin Hub → Salary, in the Inventory look: the page header with the period on the right, four picture cards (left to
// pay, total, tips, workers to pay), then a card per team (kitchen, waiters, managers, extra workers). A team opens its
// people as one list (search, one options button for the filters, the order). Someone is paid by the hour (worked
// hours × the wage their shift was clocked in at, from the server) or a monthly salary ([SalaryPlans], MOCK until the
// server stores it); tips on their orders come on top. Marking someone paid only records it (MOCK, [SalaryPlans]).

/**
 * The teams people are paid in: the five built-in ones (by the paid role's team, or the team someone was moved to), and
 * a restaurant's own paid role as a team of its own ("ROLE:<id>", with the role's name and picture; user, 2026-10-31).
 * The colour is the team's own; the icon marks it in menus.
 */
internal class PayTeam(val name: String, val label: String, val color: Color, val image: DrawableResource, val icon: ImageVector) {
    // A team is its key: two looks of the same own role are the same team.
    override fun equals(other: Any?) = other is PayTeam && other.name == name
    override fun hashCode() = name.hashCode()

    companion object {
        // In this order on the page (user rule): managers, kitchen, waiters, cleaning, extra workers; then the restaurant's own.
        val MANAGERS = PayTeam("MANAGERS", "Managers", Kit.Blue, Res.drawable.salary_managers, Icons.Outlined.ManageAccounts)
        val KITCHEN = PayTeam("KITCHEN", "Kitchen", Color(0xFF9C5B34), Res.drawable.salary_kitchen, Icons.Outlined.SoupKitchen)
        val WAITERS = PayTeam("WAITERS", "Waiters", Kit.Green, Res.drawable.salary_waiters, Icons.Outlined.RoomService)
        val CLEANING = PayTeam("CLEANING", "Cleaning", Color(0xFF3B8FC4), Res.drawable.salary_cleaning, Icons.Outlined.CleaningServices)
        // Everyone whose role fits no other team is an extra worker; there's no "Other staff" (user, 2026-10-31).
        val EXTRA = PayTeam("EXTRA", "Extra workers", Kit.Purple, Res.drawable.salary_extra, Icons.Outlined.PersonAddAlt)
        val builtIn = listOf(MANAGERS, KITCHEN, WAITERS, CLEANING, EXTRA)

        /** A restaurant's own paid role as a team: its name, and the picture picked for it (the general figure otherwise). */
        fun own(key: String, name: String?, picture: String?): PayTeam {
            val look = com.saporini.mobile_desktop.admin.people.ui.rolePicture(picture)
                ?: com.saporini.mobile_desktop.admin.people.ui.rolePicture(com.saporini.mobile_desktop.admin.people.ui.DefaultRolePicture)!!
            return PayTeam(key, name?.ifBlank { null } ?: "Team", look.color, look.image, Icons.Outlined.Badge)
        }

        /** The team someone is paid in: the one they were moved to, else the one their paid role names (Admin Hub → Users). */
        fun of(staff: StaffPay): PayTeam =
            SalaryPlans.teamOf(staff.userId)?.let { name -> builtIn.firstOrNull { it.name == name } } ?: ofRole(staff)

        private fun ofServer(staff: StaffPay): PayTeam? = when {
            staff.payTeam?.startsWith("ROLE:") == true -> own(staff.payTeam, staff.payTeamName, staff.payTeamPicture)
            else -> builtIn.firstOrNull { it.name == staff.payTeam }
        }

        // A manager who also waits tables is paid as a manager; without the server's team, roles go by their name.
        fun ofRole(staff: StaffPay): PayTeam {
            ofServer(staff)?.let { return it }
            val roles = staff.roles.map { it.uppercase() }
            fun any(vararg words: String) = roles.any { role -> words.any { it in role } }
            return when {
                any("OWNER", "ADMIN", "MANAGER", "SUPERVISOR") -> MANAGERS
                any("KITCHEN", "CHEF", "COOK", "PREP", "DISH") -> KITCHEN
                any("CLEAN", "HOUSEKEEP", "JANITOR", "PORTER") -> CLEANING
                any("WAITER", "SERVER", "HOST", "BAR", "RUNNER", "CASHIER") -> WAITERS
                else -> EXTRA
            }
        }
    }
}

/** The teams someone can be moved to by hand (the others follow their role). */
private val MovableTeams = listOf(PayTeam.CLEANING, PayTeam.EXTRA)

/** One person's pay for the period, worked out once for the list, the details and the totals. */
private class PersonPay(val staff: StaffPay, val monthly: Long?, from: LocalDate, to: LocalDate) {
    val hourly: Long? = staff.hourlyRate.toCents()
    val notSet = monthly == null && hourly == null
    val earned: Long = monthly?.let { salaryShare(it, from, to) } ?: (staff.wages.toCents() ?: 0)
    val tips: Long = staff.tips.toCents() ?: 0
    val total: Long = earned + tips
    val paid: Long = SalaryPlans.paidFor(staff.userId, from, to, total)
    val left: Long = total - paid
    /** Something left to pay and a pay set up: someone can only be paid once their pay is configured (user rule). */
    val payable: Boolean get() = !notSet && left > 0
    val team: PayTeam = PayTeam.of(staff)
    val id get() = staff.userId
    val name get() = staff.name.ifBlank { "Staff member" }
    val role: String = staff.roles.firstOrNull()?.let(::roleName) ?: "Staff"
}

/** What a person's line can do. */
private class PayActions(
    val open: (String) -> Unit,
    val edit: (String) -> Unit,
    /** Opens the pay dialog (pay some or all of what's left). */
    val pay: (PersonPay) -> Unit,
    val unpaid: (PersonPay) -> Unit,
    /** Moves someone to a team, or back to their role's team (null). */
    val move: (PersonPay, PayTeam?) -> Unit
)

/** Who's ticked to be paid together: [ids] ticked, [can] who may be ticked (pay set and something left to pay). */
private class PayPicks(val ids: Set<String>, val can: Set<String>, val toggle: (String) -> Unit, val setAll: (Collection<String>, Boolean) -> Unit)

private enum class PayShow(val label: String, val icon: ImageVector, val color: Color) {
    ALL("Everyone", Icons.Outlined.Groups, Kit.Green),
    TO_PAY("Still to pay", Icons.Outlined.Payments, Kit.Green),
    PAID("Paid", Icons.Outlined.CheckCircle, Kit.Green),
    HOURLY("By the hour", Icons.Outlined.Schedule, Kit.Green),
    MONTHLY("Monthly salary", Icons.Outlined.CalendarMonth, Kit.Blue),
    NOT_SET("No pay set", Icons.Outlined.ErrorOutline, Kit.Amber);

    fun matches(person: PersonPay) = when (this) {
        ALL -> true
        TO_PAY -> person.left > 0
        PAID -> person.total > 0 && person.left == 0L
        HOURLY -> person.monthly == null && person.hourly != null
        MONTHLY -> person.monthly != null
        NOT_SET -> person.notSet
    }
}

private enum class PaySort(val label: String) { NAME("Name A–Z"), PAY("Highest pay"), LEFT("Most left to pay"), HOURS("Most hours") }

@Composable
internal fun HoursAndPay(
    state: ShiftState,
    modifier: Modifier = Modifier,
    // For the look check: a person's shifts and pay, their pay dialog, a team (or EVERYONE), or "mark as paid" for a team, open from the start.
    startOpen: String? = null,
    startEditing: String? = null,
    startTeam: String? = null,
    startPaying: String? = null,
    startPayingPerson: String? = null
) {
    val repository = koinInject<ShiftRepository>()
    val session = koinInject<SessionManager>()
    val user by session.currentUser.collectAsState()
    val restaurant = user?.restaurantId
    val branch = user?.defaultBranchId
    val zone = state.zone
    val today = remember(zone) { Clock.System.now().toLocalDateTime(zone).date }
    // Salaries are monthly, so the page opens on the month.
    // Salaries are worked out per month; there's no week view (user rule).
    val period = MONTH
    var anchor by remember { mutableStateOf(state.date) }
    var teamKey by remember { mutableStateOf(startTeam) }
    var query by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(PayShow.ALL) }
    var role by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(PaySort.NAME) }
    var openedId by remember { mutableStateOf(startOpen) }
    var editingId by remember { mutableStateOf(startEditing) }
    // People ticked to pay together, instead of "Pay everyone" (user, 2026-10-31). Another team or month starts with none.
    var picked by remember(teamKey, anchor) { mutableStateOf(emptySet<String>()) }
    var payingPicked by remember { mutableStateOf(startPaying != null) }
    // The look check opens "Pay selected" with everyone in the team ticked.
    var pickAllFirst by remember { mutableStateOf(startPaying != null) }
    var payingId by remember { mutableStateOf(startPayingPerson) }
    var report by remember { mutableStateOf<PayReport?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val from = if (period == MONTH) LocalDate(anchor.year, anchor.month, 1) else anchor.minus(DatePeriod(days = anchor.dayOfWeek.isoDayNumber - 1))
    val to = if (period == MONTH) from.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)) else from.plus(DatePeriod(days = 6))

    LaunchedEffect(restaurant, branch, from, to, reload) {
        if (restaurant == null || branch == null) return@LaunchedEffect
        loading = true
        try {
            report = repository.pay(restaurant, branch, from.toString(), to.toString(), mine = false)
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = (e as? ApiException)?.message ?: "Couldn't load pay. Check the connection and try again." }
        finally { loading = false }
    }
    // Open shifts keep counting: refresh every minute while the page is open.
    LaunchedEffect(Unit) { while (true) { delay(60_000); reload++ } }

    val currency = report?.currency ?: "EUR"
    val showing = report?.takeIf { it.from == from.toString() && it.to == to.toString() }
    fun peopleOf(shown: PayReport): List<PersonPay> = shown.staff.map { PersonPay(it, it.monthlySalary.toCents(), from, to) }
    val everyone = showing?.let(::peopleOf).orEmpty()
    // The loading shapes stay a moment once shown, so a quick month change doesn't flash them (user, 2026-10-31).
    val loadingMonth = steadyLoading(showing == null)
    // The built-in teams, then the restaurant's own paid roles (also before anyone has them), then any other team someone's in.
    fun teamsOf(report: PayReport?, people: List<PersonPay>): List<PayTeam> =
        (PayTeam.builtIn + report?.teams.orEmpty().map { PayTeam.own(it.key, it.name, it.picture) } + people.map { it.team }).distinctBy { it.name }
    val allTeams = teamsOf(showing, everyone)
    // A team's page keeps its name while another month loads.
    val seenTeams = remember { mutableMapOf<String, PayTeam>() }
    allTeams.forEach { seenTeams[it.name] = it }
    fun membersOf(key: String?): List<PersonPay> =
        (allTeams.firstOrNull { it.name == key } ?: seenTeams[key])?.let { team -> everyone.filter { it.team == team } } ?: everyone
    val team = allTeams.firstOrNull { it.name == teamKey } ?: seenTeams[teamKey]
    val members = membersOf(teamKey)
    val words = query.trim()
    val people = members.filter { (words.isEmpty() || it.name.contains(words, ignoreCase = true)) && show.matches(it) && (role == null || role in it.staff.roles) }
        .let { list ->
            when (sort) {
                PaySort.NAME -> list.sortedBy { it.name.lowercase() }
                PaySort.PAY -> list.sortedWith(compareByDescending<PersonPay> { it.total }.thenBy { it.name.lowercase() })
                PaySort.LEFT -> list.sortedWith(compareByDescending<PersonPay> { it.left }.thenBy { it.name.lowercase() })
                PaySort.HOURS -> list.sortedWith(compareByDescending<PersonPay> { it.staff.workedMinutes }.thenBy { it.name.lowercase() })
            }
        }
    val pickable = members.filter { it.payable && it.left > 0 }
    val chosen = pickable.filter { it.id in picked }
    val picks = PayPicks(picked, pickable.map { it.id }.toSet(), toggle = { id -> picked = if (id in picked) picked - id else picked + id },
        setAll = { ids, on -> picked = if (on) picked + ids else picked - ids.toSet() })
    LaunchedEffect(showing != null) { if (pickAllFirst && showing != null) { picked = pickable.map { it.id }.toSet(); pickAllFirst = false } }
    val openTeam = { key: String?, filter: PayShow ->
        teamKey = key; query = ""; show = filter; role = null
    }
    val actions = PayActions(
        open = { openedId = it },
        edit = { editingId = it },
        pay = { person -> if (person.payable) payingId = person.id },
        unpaid = { person ->
            SalaryPlans.markUnpaid(person.id, from, to)
            notice = "${person.name} is no longer marked as paid for ${periodLabel(period, from, to)}."
        },
        move = { person, target ->
            SalaryPlans.setTeam(person.id, target?.name)
            notice = "${person.name} is ${if (target == null) "back" else "now"} in ${(target ?: PayTeam.ofRole(person.staff)).label}."
        }
    )

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        val side = if (size.isPhone) 12.dp else 22.dp
        // A team's lines: the phone's two rows below a bigger tablet's width, where the columns would squeeze the name.
        val compactLines = size.isPhone || maxWidth < 860.dp
        Column(
            Modifier.fillMaxSize().padding(start = side, end = side, top = 18.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(if (size.isPhone) 10.dp else 14.dp)
        ) {
            val periodControls: @Composable (Modifier) -> Unit = { m ->
                PeriodControls(size, period, from, to, anchor, { anchor = it }, today, loading, { reload++ }, m)
            }
            // Inside a team its own header holds the month, so the people get the room (user, 2026-10-31).
            if (teamKey == null || !state.canManage) SalaryHeader(size, periodControls, loading) { reload++ }
            else TeamHeader(team, members, people.size, showing != null, currency, size, onBack = { openTeam(null, PayShow.ALL) },
                chosen, canPick = pickable.isNotEmpty(), onPay = { payingPicked = true }, periodControls, loading) { reload++ }
            when {
                !state.canManage -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty("Managers only", "Salaries need the “Manage Shifts” permission.", Icons.Outlined.Lock)
                }
                showing == null && !loading && error != null -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty("Couldn't load pay", "Check the connection, then try again.", Icons.Outlined.CloudOff)
                }
                // While a month loads, shapes like the cards, then a fade into them (like Users; user, 2026-10-31). Keyed on
                // loaded or not, so a quiet refresh changes nothing; the old and the new never show on top of each other.
                teamKey == null -> AnimatedContent(showing.takeUnless { loadingMonth }, Modifier.fillMaxWidth().weight(1f),
                    transitionSpec = { loadingSwap(targetState == null) },
                    contentKey = { it == null }, label = "salary-teams") { shown ->
                    if (shown == null) SalarySkeleton(size, Modifier.fillMaxSize())
                    else peopleOf(shown).let { people ->
                        TeamsOverview(Modifier.fillMaxSize(), people, teamsOf(shown, people), currency, size, period, from, to, onOpen = { openTeam(it, PayShow.ALL) })
                    }
                }
                else -> {
                    // The four cards stay on the main page (user rule); a team is just its people.
                    TeamPay(
                        Modifier.fillMaxWidth().weight(1f), team, people, members, currency, size, compactLines,
                        query, { query = it }, show, { show = it }, role, { role = it }, sort, { sort = it }, actions, picks, waiting = loadingMonth
                    )
                }
            }
        }
    }
    ScreenMessages(error.takeIf { showing != null }, notice, { error = null; notice = null })

    openedId?.let { id ->
        everyone.firstOrNull { it.id == id }?.let { person ->
            AppDialog(person.name, onDismiss = { openedId = null }, subtitle = person.staff.roles.joinToString(" · ", transform = ::roleName).ifBlank { "Staff" },
                maxWidth = 560.dp) {
                PersonDetails(person, currency, period, from, to, zone, onEdit = { editingId = id },
                    onPay = { actions.pay(person) }.takeIf { person.payable }, onUnpaid = { actions.unpaid(person) }.takeIf { person.paid > 0 })
            }
        }
    }
    editingId?.let { id ->
        everyone.firstOrNull { it.id == id }?.let { person ->
            PayDialog(person, currency, onDismiss = { editingId = null }) { kind, cents ->
                val r = restaurant ?: return@PayDialog "Sign in again to change pay."
                val b = branch ?: return@PayDialog "Sign in again to change pay."
                try {
                    if (kind == PayKind.MONTHLY) repository.setMonthlySalary(r, b, id, centsToDecimal(cents))
                    else repository.setPayRate(r, b, id, centsToDecimal(cents))
                    reload++
                    editingId = null
                    notice = "Saved: ${person.name} gets " +
                        if (kind == PayKind.MONTHLY) "${wholeText(cents, currency)} a month." else "${centsText(cents, currency)} an hour."
                    null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { (e as? ApiException)?.message ?: "Couldn't save the pay. Check the connection and try again." }
            }
        }
    }
    // Over the person's dialog when it was opened from there.
    payingId?.let { id ->
        everyone.firstOrNull { it.id == id }?.takeIf { it.payable }?.let { person ->
            PayOutDialog(person, currency, periodLabel(period, from, to), today, SalaryPlans.paymentsFor(person.id, from, to),
                onDismiss = { payingId = null }) { cents, method, reference ->
                SalaryPlans.markPaid(person.id, from, to, cents, today, method, reference)
                payingId = null
                val left = person.left - cents
                notice = "Paid ${person.name} ${centsText(cents, currency)} (${method.label.lowercase()})." +
                    if (left > 0) " ${centsText(left, currency)} left to pay." else " All paid."
            }
        }
    }
    if (payingPicked && showing != null && chosen.isNotEmpty()) {
        val label = team?.label ?: "Everyone"
        PayAllDialog(label, chosen.sortedBy { it.name.lowercase() }, currency, periodLabel(period, from, to), onDismiss = { payingPicked = false }) {
            chosen.forEach { SalaryPlans.markPaid(it.id, from, to, it.left) }
            payingPicked = false
            picked = emptySet()
            notice = "$label: ${peopleText(chosen.size)} marked as paid, ${centsText(chosen.sumOf { it.left }, currency)}."
        }
    }
}

/** Title on the left, the period on the right (like Inventory's header); on a phone the period goes under the title. */
@Composable
private fun SalaryHeader(size: ScreenSize, periodControls: @Composable (Modifier) -> Unit, loading: Boolean, onRefresh: () -> Unit) {
    if (size.isPhone) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Salary", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
                Text("What each team earns and what's left to pay", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            RefreshButton(loading, onRefresh, size = 38.dp)
        }
        periodControls(Modifier.fillMaxWidth())
    } else {
        PageHeader("Salary", "What each team earns and what's left to pay") { periodControls(Modifier) }
    }
}

/** The period's arrows and picker; off phones also "This month" (when another month is shown) and refresh. */
@Composable
private fun PeriodControls(
    size: ScreenSize, period: String, from: LocalDate, to: LocalDate, anchor: LocalDate,
    onAnchor: (LocalDate) -> Unit, today: LocalDate, loading: Boolean, onRefresh: () -> Unit, modifier: Modifier
) {
    val step = { forward: Boolean ->
        onAnchor(if (period == MONTH) anchor.plus(DatePeriod(months = if (forward) 1 else -1)) else anchor.plus(DatePeriod(days = if (forward) 7 else -7)))
    }
    val picker: @Composable (Modifier) -> Unit = { m ->
        var open by remember { mutableStateOf(false) }
        Box(m) {
            HeaderButton(periodLabel(period, from, to), Icons.Outlined.CalendarToday, { open = true }, Modifier.fillMaxWidth())
            DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp) {
                CompactDatePicker(anchor) { onAnchor(it); open = false }
            }
        }
    }
    if (size.isPhone) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallSquare(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Earlier") { step(false) }
            picker(Modifier.weight(1f))
            SmallSquare(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Later") { step(true) }
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Earlier") { step(false) }
            picker(Modifier.width(190.dp))
            SquareButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Later") { step(true) }
            RefreshButton(loading, onRefresh)
        }
    }
}

/** A phone's 40 dp arrow button, the height of the tabs next to it. */
@Composable
private fun SmallSquare(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(PhoneControlHeight), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(19.dp), tint = Kit.Ink) }
    }
}

/**
 * The month at a glance, in one light strip instead of four cards (user: "I don't want cards"): what's left to pay, large,
 * with a bar of what's paid; then the total, tips and who's still to pay as plain figures beside it.
 */
@Composable
private fun SalarySummary(people: List<PersonPay>, currency: String, period: String, from: LocalDate, to: LocalDate, stacked: Boolean, phone: Boolean,
                          modifier: Modifier) {
    val total = people.sumOf { it.total }
    val paid = people.sumOf { it.paid }
    val earning = people.filter { it.total > 0 }
    val waiting = earning.count { it.left > 0 }
    val tips = people.sumOf { it.tips }
    val left: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Left to pay", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
            Text(centsText(total - paid, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (phone) 20.sp else 22.sp,
                color = if (total - paid > 0) Kit.Green else Kit.Ink, maxLines = 1)
            PaidBar(paid, total)
            Text(when { total == 0L -> "Nothing to pay yet"; paid == 0L -> "Nobody paid yet for ${periodLabel(period, from, to)}"; paid >= total -> "Everyone is paid"
                else -> "${centsText(paid, currency)} paid of ${centsText(total, currency)}" },
                fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1)
        }
    }
    val figures = listOf(
        Triple("Total to pay", centsText(total, currency), "${centsText(people.sumOf { it.earned }, currency)} pay + tips"),
        Triple("Tips", centsText(tips, currency), if (tips == 0L) "none on orders yet" else "on top of pay"),
        Triple("Workers to pay", if (earning.size != waiting) "$waiting of ${earning.size}" else "$waiting", "${daysHoursText(people.sumOf { it.staff.workedMinutes })} worked")
    )
    val figure: @Composable (Triple<String, String, String>, Modifier) -> Unit = { (label, value, detail), m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
            Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink, maxLines = 1)
            // Two lines on a phone, where the three figures share a narrow row.
            Text(detail, fontFamily = Inter(), fontSize = 11.5.sp, lineHeight = 15.sp, color = Kit.Muted, maxLines = if (phone) 2 else 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).padding(horizontal = if (phone) 14.dp else 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Phones and small tablets: what's left on top, the three figures in a row under it.
        if (stacked) {
            left(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { figures.forEach { figure(it, Modifier.weight(1f)) } }
        } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            left(Modifier.weight(1.4f))
            figures.forEach { f ->
                // A thin line between the figures, not boxes.
                Box(Modifier.padding(horizontal = 18.dp).width(1.dp).fillMaxHeight().background(Kit.Border))
                figure(f, Modifier.weight(1f))
            }
        }
    }
}

/** How wide a team card gets on a big screen. */
private val TeamCardMaxWidth = 360.dp

/** Salary's main page while a month loads: the summary strip and the team cards as softly pulsing shapes, like Users. */
@Composable
private fun SalarySkeleton(size: ScreenSize, modifier: Modifier) {
    val alpha = rememberSkeletonAlpha("salary-teams")
    val gap = if (size.isPhone) 10.dp else 14.dp
    BoxWithConstraints(modifier.alpha(alpha).clipToBounds(), contentAlignment = Alignment.TopCenter) {
        val columns = when { size.isPhone -> 1; maxWidth < 840.dp -> 2; else -> 3 }
        val cardWidth = if (columns == 1) maxWidth else ((maxWidth - gap * (columns - 1)) / columns).coerceAtMost(TeamCardMaxWidth)
        Column(Modifier.width(cardWidth * columns + gap * (columns - 1)), verticalArrangement = Arrangement.spacedBy(gap)) {
            val left: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(Modifier.size(width = 80.dp, height = 11.dp), SkeletonLight)
                    SkeletonBox(Modifier.size(width = 150.dp, height = 22.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.85f).height(6.dp), SkeletonLight, RoundedCornerShape(50))
                }
            }
            val figure: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(Modifier.size(width = 70.dp, height = 11.dp), SkeletonLight)
                    SkeletonBox(Modifier.size(width = 96.dp, height = 15.dp))
                    SkeletonBox(Modifier.size(width = 84.dp, height = 10.dp), SkeletonLight)
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).padding(horizontal = if (size.isPhone) 14.dp else 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (columns < 3) {
                    left(Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { figure(Modifier.weight(1f)) } }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    left(Modifier.weight(1.4f))
                    repeat(3) { Spacer(Modifier.width(37.dp)); figure(Modifier.weight(1f)) }
                }
            }
            repeat(if (size.isPhone) 3 else 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    repeat(columns) { TeamCardSkeleton(Modifier.width(cardWidth)) }
                }
            }
        }
    }
}

@Composable
private fun TeamCardSkeleton(modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column {
            Box(Modifier.fillMaxWidth().height(112.dp).background(SkeletonLight.copy(alpha = 0.55f))) {
                SkeletonBox(Modifier.align(Alignment.TopStart).padding(12.dp).size(width = 92.dp, height = 24.dp), SkeletonLight, RoundedCornerShape(50))
                SkeletonBox(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(84.dp), SkeletonLight, RoundedCornerShape(42.dp))
            }
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBox(Modifier.size(width = 120.dp, height = 16.dp))
                SkeletonBox(Modifier.size(width = 110.dp, height = 18.dp))
                SkeletonBox(Modifier.fillMaxWidth().height(6.dp), SkeletonLight, RoundedCornerShape(50))
                Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(Modifier.size(width = 120.dp, height = 12.dp), SkeletonLight)
                    Spacer(Modifier.weight(1f))
                    repeat(3) { SkeletonBox(Modifier.padding(start = 2.dp).size(26.dp), SkeletonLight, CircleShape) }
                }
            }
        }
    }
}

/** The month at a glance, then a card per team (the page scrolls when they don't fit); a team's card shows who has no pay set. */
@Composable
private fun TeamsOverview(
    modifier: Modifier, everyone: List<PersonPay>, teams: List<PayTeam>, currency: String, size: ScreenSize, period: String, from: LocalDate, to: LocalDate,
    onOpen: (String) -> Unit
) {
    // Every team always shows (an empty one says how to fill it). Only people with a paid role are here (the server's list).
    val gap = if (size.isPhone) 10.dp else 14.dp
    ScrollColumn(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        // Three a row (two on a small tablet), all the same size and never wider than TeamCardMaxWidth (big screens); a
        // shorter last row sits in the middle, like the workspace picker (user rule).
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val columns = when { size.isPhone -> 1; maxWidth < 840.dp -> 2; else -> 3 }
            val cardWidth = if (columns == 1) maxWidth else ((maxWidth - gap * (columns - 1)) / columns).coerceAtMost(TeamCardMaxWidth)
            Column(Modifier.width(cardWidth * columns + gap * (columns - 1)), verticalArrangement = Arrangement.spacedBy(gap)) {
                // The month at a glance, as wide as the team cards.
                SalarySummary(everyone, currency, period, from, to, stacked = columns < 3, phone = size.isPhone, Modifier.fillMaxWidth())
                teams.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                        row.forEach { team ->
                            val members = everyone.filter { it.team == team }
                            // People without pay show as a small notification on their team's card (user rule: no banner).
                            val missing = members.count { it.notSet }
                            // Cards on a phone too, picture band and all (user, 2026-10-31: "cards, not a list").
                            TeamCard(team, members, currency, Modifier.width(cardWidth), missing) { onOpen(team.name) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * A team as a card: its picture on a band of its colour, how many people, the total to pay, a bar of what's paid
 * (green) and what's left, and their faces. Every card has the same lines, so a row of them lines up.
 */
@Composable
private fun TeamCard(team: PayTeam, people: List<PersonPay>, currency: String, modifier: Modifier, notSet: Int, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val total = people.sumOf { it.total }
    val paid = people.sumOf { it.paid }
    Surface(
        onClick = onOpen, modifier = modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand), shape = RoundedCornerShape(16.dp), color = Color.White,
        border = BorderStroke(if (hovered) 1.5.dp else 1.dp, if (hovered) team.color.copy(alpha = 0.55f) else Kit.Border),
        shadowElevation = if (hovered) 6.dp else 1.dp, interactionSource = interaction
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(112.dp).background(Brush.horizontalGradient(listOf(team.color.copy(alpha = 0.05f), team.color.copy(alpha = 0.17f))))) {
                Image(painterResource(team.image), null, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp, top = 10.dp, bottom = 8.dp).height(92.dp),
                    contentScale = ContentScale.Fit)
                Row(
                    Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.92f))
                        .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Outlined.Groups, null, Modifier.size(15.dp), tint = team.color)
                    Text(peopleText(people.size), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
                }
                if (notSet > 0) NotSetBadge(notSet, Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
            // As compact as a Users role card (user, 2026-10-31: "Salary has some big things"): the total on one line.
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(team.color))
                    Spacer(Modifier.width(8.dp))
                    Text(team.label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = if (hovered) team.color else Kit.Faint)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(centsText(total, currency), Modifier.alignByBaseline(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = if (total == 0L) Kit.Faint else Kit.Ink, maxLines = 1)
                    Text("total", Modifier.alignByBaseline(), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
                }
                PaidBar(paid, total)
                Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { LeftText(team, people, total, paid, currency) }
                    AvatarStack(people.map { it.name }, max = 4, size = 28.dp)
                }
            }
        }
    }
}

/** A team's picture filling a small tile. */
@Composable
private fun TeamTilePicture(team: PayTeam, padding: Dp) {
    Image(painterResource(team.image), null, Modifier.padding(padding).fillMaxSize(), contentScale = ContentScale.Fit)
}

/** What's paid (green) out of the total. */
@Composable
private fun PaidBar(paid: Long, total: Long) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Kit.Tint)) {
        if (total > 0 && paid > 0) Box(Modifier.fillMaxWidth((paid.toFloat() / total).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Kit.Green))
    }
}

/** "€1,200.00 left to pay", "All paid" or, for an empty team, how to fill it. */
@Composable
private fun LeftText(team: PayTeam, people: List<PersonPay>, total: Long, paid: Long, currency: String) {
    when {
        people.isEmpty() -> Text(when (team) { PayTeam.EXTRA -> "Temporary workers go here"; PayTeam.CLEANING -> "Cleaning staff go here"; else -> "Nobody yet" }, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        total == 0L -> Text("Nothing to pay yet", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint, maxLines = 1)
        paid >= total -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(15.dp), tint = Kit.Green)
            Text("All paid", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
        }
        else -> Text("${centsText(total - paid, currency)} left to pay", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A team's page title, in place of the Salary header: back to the teams, its picture and name, the month, and "Pay
 * selected" for the people ticked in the list (user, 2026-10-31: no "Pay everyone"), when someone is still to pay. One
 * row on a big screen; the month goes under it on tablets and phones.
 */
@Composable
private fun TeamHeader(
    team: PayTeam?, people: List<PersonPay>, shown: Int, loaded: Boolean, currency: String, size: ScreenSize, onBack: () -> Unit,
    chosen: List<PersonPay>, canPick: Boolean, onPay: () -> Unit, periodControls: @Composable (Modifier) -> Unit, loading: Boolean, onRefresh: () -> Unit
) {
    val color = team?.color ?: Kit.Green
    val payAll: @Composable (Modifier) -> Unit = { m ->
        if (canPick) {
            if (chosen.isEmpty()) HoverTooltip("Tick the people you want to pay", m) {
                KitButton("Pay selected", {}, if (size.isPhone) Modifier.fillMaxWidth() else Modifier, icon = Icons.Outlined.Payments, enabled = false)
            }
            else KitButton("Pay ${chosen.size} selected · ${centsText(chosen.sumOf { it.left }, currency)}", onPay, m, icon = Icons.Outlined.Payments)
        }
    }
    val count = when { !loaded -> "Loading…"; shown < people.size -> "$shown of ${peopleText(people.size)} shown"; else -> peopleText(people.size) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val oneRow = !size.isPhone && maxWidth >= 1000.dp
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // One step back inside the page: a plain arrow. The curved one in the top bar leaves the whole workspace (user rule).
                HoverTooltip("Back to the teams") {
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the teams", Modifier.size(22.dp), tint = Kit.Ink)
                    }
                }
                Box(Modifier.size(if (size.isPhone) 42.dp else 50.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(color.copy(alpha = 0.06f), color.copy(alpha = 0.2f)))), Alignment.Center) {
                    TeamTilePicture(team ?: PayTeam.EXTRA, 5.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(team?.label ?: "Everyone", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (size.isPhone) 17.sp else 19.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(count, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when {
                    size.isPhone -> RefreshButton(loading, onRefresh, size = 38.dp)
                    oneRow -> { periodControls(Modifier); Spacer(Modifier.width(4.dp)); payAll(Modifier) }
                    else -> payAll(Modifier)
                }
            }
            if (size.isPhone) {
                periodControls(Modifier.fillMaxWidth())
                payAll(Modifier.fillMaxWidth())
            } else if (!oneRow) periodControls(Modifier)
        }
    }
}

/** A team's people as one list, like Inventory's "Stock on hand": search, filters in one button, the order, then the lines. */
@Composable
private fun TeamPay(
    modifier: Modifier, team: PayTeam?, people: List<PersonPay>, everyone: List<PersonPay>, currency: String, size: ScreenSize, compactLines: Boolean,
    query: String, onQuery: (String) -> Unit, show: PayShow, onShow: (PayShow) -> Unit, role: String?, onRole: (String?) -> Unit,
    sort: PaySort, onSort: (PaySort) -> Unit, actions: PayActions, picks: PayPicks, waiting: Boolean
) {
    val notSet = everyone.filter { it.notSet }
    // Ticks show when someone here can be paid; "select all" takes the people shown.
    val picking = picks.can.isNotEmpty()
    val shownPickable = people.filter { it.id in picks.can }.map { it.id }
    val allPicked = shownPickable.isNotEmpty() && shownPickable.all { it in picks.ids }
    val onAll = { picks.setAll(shownPickable, !allPicked) }
    val filters = (if (show != PayShow.ALL) 1 else 0) + (if (role != null) 1 else 0) + (if (size.isPhone && sort != PaySort.NAME) 1 else 0)
    val roles = everyone.flatMap { it.staff.roles }.distinct().sortedBy(::roleName)
    // Every filter (and on a phone the order too) in one button, as in Inventory → Items.
    val options: @Composable (Dp) -> Unit = { height ->
        PhoneOptionsButton(Icons.Outlined.Tune, "Filter people", filters, height) { close ->
            OptionGroup("Show")
            PayShow.entries.forEach { choice ->
                OptionLine(choice.label, show == choice, { onShow(choice); close() }, icon = choice.icon, color = choice.color, count = everyone.count(choice::matches))
            }
            if (roles.size > 1) {
                OptionGroup("Role")
                OptionLine("All roles", role == null, { onRole(null); close() }, icon = Icons.Outlined.Badge)
                roles.forEach { code ->
                    OptionLine(roleName(code), role == code, { onRole(code); close() }, icon = Icons.Outlined.AdminPanelSettings, color = Kit.Blue,
                        count = everyone.count { code in it.staff.roles })
                }
            }
            if (size.isPhone) {
                OptionGroup("Order")
                PaySort.entries.forEach { choice -> OptionLine(choice.label, sort == choice, { onSort(choice); close() }, icon = Icons.AutoMirrored.Outlined.Sort) }
            }
        }
    }
    // No title row: the team's header already says how many are shown (user, 2026-10-31: "give it table space").
    OverviewPanel(Icons.Outlined.Groups, "People", modifier, count = people.size, bottomPadding = 0.dp, flat = size.isPhone, showTitle = false) {
        if (size.isPhone) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query, onQuery, Modifier.weight(1f), placeholder = "Find a person", height = PhoneControlHeight)
                options(PhoneControlHeight)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SearchField(query, onQuery, Modifier.weight(1f, fill = false).widthIn(max = 380.dp).padding(end = 10.dp), placeholder = "Find a person",
                    height = PageControlHeight)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    options(PageControlHeight)
                    HeaderDropdown(sort.label, Icons.AutoMirrored.Outlined.Sort, Modifier.width(196.dp), PaySort.entries.map { it.label },
                        { label -> PaySort.entries.firstOrNull { it.label == label }?.let(onSort) })
                }
            }
        }
        val empty: @Composable () -> Unit = {
            if (everyone.isEmpty()) OverviewEmpty(
                when (team) { PayTeam.EXTRA -> "No extra workers yet"; PayTeam.CLEANING -> "No cleaning staff yet"; else -> "Nobody here yet" },
                if (team in MovableTeams) "${if (team == PayTeam.EXTRA) "Temporary workers" else "Cleaning staff"} show here. Move someone here from the ⋮ menu on their line in another team."
                else "People in this team show here once they're on the branch's staff.",
                Icons.Outlined.PersonSearch
            ) else OverviewEmpty("Nobody matches", "Try another name, or show everyone.", Icons.Outlined.PersonSearch)
        }
        // While the month loads, lines shaped like the people, then a fade into them (like Users, user 2026-10-31). Keyed on
        // loading or not, so searching or sorting changes the list in place.
        AnimatedContent(if (waiting) null else people, Modifier.fillMaxWidth().weight(1f), transitionSpec = { loadingSwap(targetState == null) },
            contentKey = { it == null },
            label = "salary-people") { list ->
            when {
                list == null -> PeopleLinesSkeleton(10, Modifier.fillMaxSize(), figures = if (compactLines) 0 else if (size.isDesktop) 5 else 3,
                    buttons = 2, phone = compactLines, lineHeight = 62.dp)
                list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { empty() }
                else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Columns with a header from a bigger tablet up; below that each line has the phone's two rows.
                    if (!compactLines) PayListHeader(size, picking, allPicked.takeIf { shownPickable.isNotEmpty() }, onAll)
                    else if (shownPickable.isNotEmpty()) Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAll).padding(start = 8.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PayTick(allPicked, null)
                        Text("Select all to pay", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
                    }
                    ScrollList(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(list, key = { it.id }) { person ->
                            PayLine(person, currency, if (compactLines) ScreenSize.PHONE else size, actions, picking,
                                (person.id in picks.ids).takeIf { person.id in picks.can }) { picks.toggle(person.id) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * People on a team with no pay set, as a small notification on the team's card: an amber bubble with a bell and how
 * many (user rule: no banner across the page). It only tells, never opens the team (user, 2026-10-31): hovering or
 * tapping it brings out an amber speech bubble right under it, its arrow pointing at the bell. The card opens the team.
 * [opensLeft]: the bubble grows to the left (the bell at a card's right edge) or to the right (the phone's picture tile).
 */
@Composable
private fun NotSetBadge(count: Int, modifier: Modifier, small: Boolean = false, opensLeft: Boolean = !small) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var badgeWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val gap = with(density) { 3.dp.roundToPx() }
    val position = remember(opensLeft, gap) { BelowAnchor(opensLeft, gap) }
    // A tap keeps it out for a moment, then it goes by itself.
    LaunchedEffect(open) { if (open) { delay(3_000); open = false } }
    val message = "${peopleText(count)} ${if (count == 1) "has" else "have"} no pay set up"
    Box(modifier) {
        Box {
            Row(
                Modifier.onSizeChanged { badgeWidth = it.width }.clip(RoundedCornerShape(50)).background(Kit.Amber).border(2.dp, Color.White, RoundedCornerShape(50))
                    .hoverable(interaction).clickable { open = !open }
                    .padding(start = if (small) 5.dp else 7.dp, end = if (small) 7.dp else 9.dp, top = if (small) 2.dp else 4.dp, bottom = if (small) 2.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(Icons.Outlined.NotificationImportant, message, Modifier.size(if (small) 13.dp else 15.dp), tint = Color.White)
                Text("$count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (small) 11.sp else 12.sp, color = Color.White)
            }
            if (open || hovered) Popup(popupPositionProvider = position, onDismissRequest = { open = false }) {
                // The arrow sits under the middle of the bell, so the bubble reads as coming out of it.
                val arrowInset = with(density) { (badgeWidth / 2).toDp() } - 6.dp
                Column(horizontalAlignment = if (opensLeft) Alignment.End else Alignment.Start) {
                    Canvas(Modifier.padding(start = if (opensLeft) 0.dp else arrowInset.coerceAtLeast(0.dp), end = if (opensLeft) arrowInset.coerceAtLeast(0.dp) else 0.dp)
                        .size(12.dp, 6.dp)) {
                        drawPath(Path().apply { moveTo(0f, size.height); lineTo(size.width / 2, 0f); lineTo(size.width, size.height); close() }, Kit.Amber)
                    }
                    Row(
                        Modifier.shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Kit.Amber)
                            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.NotificationImportant, null, Modifier.size(16.dp), tint = Color.White)
                        Text(message, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Puts a popup just under what it belongs to, lined up with its right edge ([alignEnd]) or its left edge, kept inside the window. */
private class BelowAnchor(private val alignEnd: Boolean, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), anchorBounds.bottom + gap)
    }
}

// Column widths shared by the header and the lines.
private const val ColPerson = 2.4f
private const val ColPay = 1.5f
private const val ColHours = 1f
private const val ColEarned = 1.1f
private const val ColTips = 0.8f
private const val ColTotal = 1.1f
private val ColButtons = 160.dp

@Composable
private fun PayListHeader(size: ScreenSize, picking: Boolean, allPicked: Boolean?, onAll: () -> Unit) {
    val label: @Composable (String, Modifier) -> Unit = { text, m ->
        Text(text, m, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp, color = Kit.Muted, maxLines = 1)
    }
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp + ScrollbarGap), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // Over the name, past the tick and the picture; the tick here ticks everyone shown.
        Row(Modifier.weight(ColPerson), verticalAlignment = Alignment.CenterVertically) {
            if (picking) {
                PayTick(allPicked, onAll.takeIf { allPicked != null }, "Select all")
                Spacer(Modifier.width(10.dp))
            }
            label("PERSON", Modifier.padding(start = 50.dp))
        }
        label("PAY", Modifier.weight(ColPay))
        label("HOURS", Modifier.weight(ColHours))
        if (size.isDesktop) {
            label("EARNED", Modifier.weight(ColEarned))
            label("TIPS", Modifier.weight(ColTips))
        }
        label("TOTAL", Modifier.weight(ColTotal))
        Spacer(Modifier.width(ColButtons))
        Spacer(Modifier.width(32.dp))
    }
}

/** One person: who, how they're paid, the hours, what they earned, tips, the total and whether it's paid; the strip is their team's colour. */
@Composable
private fun PayLine(person: PersonPay, currency: String, size: ScreenSize, actions: PayActions, picking: Boolean, picked: Boolean?, onPick: () -> Unit) {
    val minutes = person.staff.workedMinutes
    // The line itself does nothing when clicked; only its buttons and its tick do (user rule). The ⋮ menu opens the shifts and pay.
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = if (picked == true) Kit.GreenSoft else Color.White,
        border = BorderStroke(1.dp, if (picked == true) Kit.Green.copy(alpha = 0.45f) else Kit.RowBorder)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(person.team.color))
            Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.weight(ColPerson), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // An empty place when there's nothing to pay them, so the lines stay lined up.
                    if (picking) PayTick(picked, onPick.takeIf { picked != null }, "Pay ${person.name}")
                    ShiftAvatar(person.name, if (size.isPhone) 36.dp else 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(person.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val shifts = person.staff.shiftCount
                        Text(
                            if (size.isPhone) "${person.role} · ${rateText(person, currency) ?: "no pay set"}"
                            else "${person.role} · ${if (shifts == 0) "no shifts" else "$shifts ${if (shifts == 1) "shift" else "shifts"}"}",
                            fontFamily = Inter(), fontSize = 11.sp, color = if (size.isPhone && person.notSet) Kit.Amber else Kit.Muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (size.isPhone) {
                    Column(Modifier.widthIn(min = 60.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(centsText(person.total, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = if (person.total == 0L) Kit.Faint else Kit.Ink, maxLines = 1)
                        when {
                            person.total == 0L -> Text(if (minutes == 0L) "no hours" else daysHoursText(minutes), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                            person.left == 0L -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(12.dp), tint = Kit.Green)
                                Text("Paid", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Green, maxLines = 1)
                            }
                            person.notSet -> Text("Set pay first", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Amber, maxLines = 1)
                            else -> Text("${centsText(person.left, currency)} to pay", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                        }
                    }
                } else {
                    Box(Modifier.weight(ColPay)) { PayAmount(person, currency) }
                    Figure(if (minutes == 0L) "–" else daysHoursText(minutes), Modifier.weight(ColHours), muted = minutes == 0L)
                    if (size.isDesktop) {
                        Figure(if (person.earned == 0L) "–" else centsText(person.earned, currency), Modifier.weight(ColEarned), muted = person.earned == 0L)
                        Figure(if (person.tips == 0L) "–" else centsText(person.tips, currency), Modifier.weight(ColTips), muted = person.tips == 0L)
                    }
                    Text(centsText(person.total, currency), Modifier.weight(ColTotal), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = if (person.total == 0L) Kit.Faint else Kit.Ink, maxLines = 1)
                    LineButtons(person, actions, Modifier.width(ColButtons))
                }
                RowActionsMenu(listOfNotNull(
                    RowAction("Mark as not paid", Icons.AutoMirrored.Outlined.Undo) { actions.unpaid(person) }.takeIf { person.paid > 0 },
                    RowAction("Shifts and pay", Icons.AutoMirrored.Outlined.ReceiptLong) { actions.open(person.id) },
                ) + MovableTeams.filter { it != person.team }.map { team -> RowAction("Move to ${team.label}", team.icon) { actions.move(person, team) } } +
                    listOfNotNull(PayTeam.ofRole(person.staff).takeIf { it != person.team }?.let { home ->
                        RowAction("Back to ${home.label}", Icons.AutoMirrored.Outlined.Undo) { actions.move(person, null) }
                    }))
            }
            // A phone has no room beside the name, so the two buttons go under it.
            if (size.isPhone) LineButtons(person, actions, Modifier.padding(start = if (picking) 92.dp else 56.dp, bottom = 10.dp))
            }
        }
    }
}

/**
 * The line's two buttons, apart (user rule): Pay (some or all of what's left; "Paid" once it's all paid) and Edit (how
 * they're paid). Before the pay is set there's only "Set up pay", since nobody is paid before that.
 */
@Composable
private fun LineButtons(person: PersonPay, actions: PayActions, modifier: Modifier) {
    // Pay sits right beside Edit, and both keep to the slot's right edge so the Edit buttons line up down the list
    // (user, 2026-10-31: "why is Edit so far from Pay").
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        if (person.notSet) {
            LineButton("Set up pay", Icons.Outlined.Add, Kit.Amber, filled = false) { actions.edit(person.id) }
            return@Row
        }
        when {
            person.left > 0 -> LineButton("Pay", Icons.Outlined.Payments, Kit.Green, filled = true) { actions.pay(person) }
            person.total > 0 -> StatusPill("Paid", Kit.Green)
            else -> Text("Nothing yet", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint, maxLines = 1)
        }
        LineButton("Edit", Icons.Outlined.Edit, Kit.Ink, filled = false) { actions.edit(person.id) }
    }
}

/** A small pill button; a plain clickable row (not a Surface) so the line keeps the height of the others. */
@Composable
private fun LineButton(text: String, icon: ImageVector, color: Color, filled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp)))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = if (filled) Color.White else color)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (filled) Color.White else color, maxLines = 1)
    }
}

@Composable
private fun Figure(text: String, modifier: Modifier, muted: Boolean) {
    Text(text, modifier, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (muted) Kit.Faint else Kit.Ink, maxLines = 1)
}

/** "€11.50 / hour" or "€1,400 / month", or "Not set" (the Edit / Set up pay button changes it). */
@Composable
private fun PayAmount(person: PersonPay, currency: String) {
    if (person.notSet) {
        Text("Not set", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Amber, maxLines = 1)
        return
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(person.monthly?.let { wholeText(it, currency) } ?: centsText(person.hourly ?: 0, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold,
            fontSize = 15.sp, color = Kit.Ink, maxLines = 1)
        Text(if (person.monthly != null) " / month" else " / hour", Modifier.padding(bottom = 1.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
    }
}

/**
 * Paying one person: what the period comes to, what's paid and what's left; the amount (what's left, filled in, can be
 * changed), how it's paid (cash, bank transfer…) and a reference, then the payments made before. Returns them through
 * [onPay]; it only records the payment (MOCK, nothing is sent).
 */
@Composable
private fun PayOutDialog(
    person: PersonPay, currency: String, periodText: String, today: LocalDate, history: List<PayoutRecord>,
    onDismiss: () -> Unit, onPay: (Long, PayMethod, String?) -> Unit
) {
    var text by remember(person.id) { mutableStateOf(amountInput(person.left)) }
    var method by remember(person.id) { mutableStateOf(SalaryPlans.lastMethodOf(person.id) ?: PayMethod.CASH) }
    var reference by remember(person.id) { mutableStateOf("") }
    val typed = text.replace(',', '.').toCentsOrNull()
    val problem = when {
        text.isBlank() -> "Enter how much you pay."
        typed == null -> "Enter an amount, like 200."
        typed <= 0 -> "Enter an amount above zero."
        typed > person.left -> "That's more than the ${centsText(person.left, currency)} left."
        else -> null
    }
    val amount = typed?.takeIf { problem == null }
    AppDialog(
        "Pay ${person.name}", onDismiss = onDismiss, subtitle = periodText, maxWidth = 520.dp,
        buttons = {
            KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
            KitButton(amount?.let { "Pay ${centsText(it, currency)}" } ?: "Pay", { amount?.let { onPay(it, method, reference) } },
                icon = Icons.Outlined.Payments, enabled = amount != null)
        }
    ) {
        // What's owed, what's paid, what's left.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))
                .padding(vertical = 12.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ValueLine("To pay for the period", centsText(person.total, currency))
            ValueLine("Paid already", if (person.paid == 0L) "–" else centsText(person.paid, currency), valueColor = if (person.paid > 0) Kit.Green else Kit.Ink)
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(1.dp).background(Kit.RowBorder))
            ValueLine("Left to pay", centsText(person.left, currency), strong = true)
        }

        // How much: what's left is filled in; it can be changed.
        FieldBlock("Amount", problem = problem.takeIf { text.isNotBlank() || typed != null }) {
            Row(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, if (problem != null && text.isNotBlank()) Kit.Danger else Kit.Green, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(currencySymbol(currency).trim(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Muted)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    text, { value -> text = value.filter { it.isDigit() || it == '.' || it == ',' }.take(11) },
                    Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Kit.Ink),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text("0.00", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Kit.Faint)
                            inner()
                        }
                    }
                )
                if (typed != person.left) {
                    Text("All ${centsText(person.left, currency)}", Modifier.clip(RoundedCornerShape(8.dp)).background(Kit.GreenSoft)
                        .clickable { text = amountInput(person.left) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
                } else Text("all that's left", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
            }
        }

        // How it's paid, for this payment.
        FieldBlock("How", problem = null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayMethod.entries.forEach { option ->
                    MethodChoice(option, method == option, Modifier.weight(1f)) { method = option }
                }
            }
        }
        TextInput(reference, { reference = it.take(80) }, label = "Reference", optional = true, icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            placeholder = when (method) { PayMethod.BANK -> "Transfer number"; PayMethod.CASH -> "Receipt number or a note"; PayMethod.OTHER -> "How, and a note" },
            maxLength = 80)

        // Every payment so far, so what's left is clear.
        if (history.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Paid so far", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                history.forEach { record -> PaymentLine(record, currency, today) }
            }
        }
        Text("This records the payment. No money is sent from the app.", fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted)
    }
}

private fun PayMethod.icon(): ImageVector = when (this) {
    PayMethod.CASH -> Icons.Outlined.Payments
    PayMethod.BANK -> Icons.Outlined.AccountBalance
    PayMethod.OTHER -> Icons.Outlined.MoreHoriz
}

@Composable
private fun MethodChoice(method: PayMethod, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(10.dp), color = if (selected) Kit.GreenSoft else Color.White,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.Border)) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
            Icon(method.icon(), null, Modifier.size(18.dp), tint = if (selected) Kit.Green else Kit.Muted)
            Text(method.label, fontFamily = Inter(), fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp,
                color = if (selected) Kit.Green else Kit.Ink, maxLines = 1)
        }
    }
}

/** One payment made before: when, how (and its reference), how much. */
@Composable
private fun PaymentLine(record: PayoutRecord, currency: String, today: LocalDate) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Kit.GreenSoft), Alignment.Center) {
            Icon(record.method?.icon() ?: Icons.Outlined.DoneAll, null, Modifier.size(17.dp), tint = Kit.Green)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(listOfNotNull(record.paidOn?.let { if (it == today) "Today" else "${it.weekdayShort()} ${it.shortDay()}" }, record.method?.label ?: "With the team")
                .joinToString(" · "), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
            record.reference?.let { Text(it, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text(centsText(record.cents, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Green, maxLines = 1)
    }
}

/** Confirms marking the ticked people as paid: who, how much each, and the total. */
@Composable
private fun PayAllDialog(
    team: String, people: List<PersonPay>, currency: String, periodText: String, onDismiss: () -> Unit, onConfirm: () -> Unit
) {
    val total = people.sumOf { it.left }
    AppDialog(
        "Mark as paid", onDismiss = onDismiss, subtitle = "$team · $periodText", maxWidth = 480.dp,
        buttons = {
            KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
            KitButton("Mark ${centsText(total, currency)} as paid", onConfirm, icon = Icons.Outlined.Check, enabled = people.isNotEmpty())
        }
    ) {
        Text("This records that you paid them. No money is sent from the app.", fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted)
        if (people.isEmpty()) Text("Everyone here is already paid.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(vertical = 4.dp)
        ) {
            people.forEachIndexed { index, person ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = Kit.RowBorder)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShiftAvatar(person.name, 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(person.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(person.role, person.paid.takeIf { it > 0 }?.let { "${centsText(it, currency)} paid before" }).joinToString(" · "),
                            fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    }
                    Text(centsText(person.left, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1)
                }
            }
        }
        ValueLine("Total", centsText(total, currency), strong = true, valueColor = Kit.Green)
    }
}

/** The square tick that picks a person to pay (green when ticked); [checked] null keeps an empty place of the same size. */
@Composable
private fun PayTick(checked: Boolean?, onClick: (() -> Unit)?, description: String? = null) {
    Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), Alignment.Center) {
        if (checked != null) Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(if (checked) Kit.Green else Color.White)
            .border(1.5.dp, if (checked) Kit.Green else Kit.Faint, RoundedCornerShape(4.dp)), Alignment.Center) {
            if (checked) Icon(Icons.Outlined.Check, description, Modifier.size(13.dp), tint = Color.White)
        }
    }
}

/**
 * A person's pay, shaped by how they're paid (the dialog a line opens): how they're paid on top (or, before that's set,
 * a card to set it up, since nobody can be paid before), what the period comes to, what's left to pay, then their shifts.
 * Hourly pay is worked out from the shifts, so those show an amount each; a monthly salary isn't, so its shifts are
 * only a record.
 */
@Composable
private fun ColumnScope.PersonDetails(
    person: PersonPay, currency: String, period: String, from: LocalDate, to: LocalDate, zone: TimeZone,
    onEdit: () -> Unit, onPay: (() -> Unit)?, onUnpaid: (() -> Unit)?
) {
    PayPlanCard(person, currency, onEdit)
    val minutes = person.staff.workedMinutes
    val shifts = person.staff.shiftCount
    val hours = if (minutes == 0L) "–" else daysHoursText(minutes)
    val shiftCount = "$shifts ${if (shifts == 1) "shift" else "shifts"}"
    val tips = if (person.tips == 0L) "–" else centsText(person.tips, currency)
    // Three tiles share a phone's narrow dialog, where "Hours worked" doesn't fit.
    val hoursLabel = if (com.saporini.mobile_desktop.core.components.LocalNarrowDialog.current) "Hours" else "Hours worked"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(periodLabel(period, from, to), Modifier.padding(start = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tile = Modifier.weight(1f).fillMaxHeight()
            when {
                person.monthly != null -> {
                    PayTile("Salary", centsText(person.earned, currency),
                        if (period == MONTH) "The whole month" else "${from.daysUntil(to) + 1} days of the month", tile)
                    PayTile("Tips", tips, "On top of the salary", tile)
                    PayTile(hoursLabel, hours, "$shiftCount · not paid by the hour", tile, muted = true)
                }
                person.hourly != null -> {
                    PayTile(hoursLabel, hours, shiftCount, tile)
                    PayTile("Wages", if (person.earned == 0L) "–" else centsText(person.earned, currency), "${centsText(person.hourly, currency)} × hours", tile)
                    PayTile("Tips", tips, "On top of wages", tile)
                }
                else -> {
                    PayTile(hoursLabel, hours, shiftCount, tile)
                    PayTile("Tips", tips, "Paid with their pay", tile)
                }
            }
        }
    }
    if (!person.notSet) ToPayCard(person, currency, onPay, onUnpaid)
    ShiftList(person, currency, period, zone)
}

/** How they're paid, with Change; before it's set, an amber card that asks to set it up first. */
@Composable
private fun PayPlanCard(person: PersonPay, currency: String, onEdit: () -> Unit) {
    if (person.notSet) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Amber.copy(alpha = 0.07f))
                .border(1.dp, Kit.Amber.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(Icons.Outlined.ErrorOutline, Kit.Amber)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Set up their pay first", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
                Text("By the hour or a monthly salary. They can't be paid until it's set.", fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp,
                    color = Color(0xFF8A5A0B))
            }
            SmallButton("Set up pay", Icons.Outlined.Add, primary = true, onClick = onEdit)
        }
        return
    }
    val monthly = person.monthly != null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(if (monthly) Icons.Outlined.CalendarMonth else Icons.Outlined.Schedule, if (monthly) Kit.Blue else Kit.Green)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(if (monthly) "Monthly salary" else "Paid by the hour", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(person.monthly?.let { wholeText(it, currency) } ?: centsText(person.hourly ?: 0, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, color = Kit.Ink, maxLines = 1)
                Text(if (monthly) " a month" else " an hour", Modifier.padding(bottom = 2.dp), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted, maxLines = 1)
            }
        }
        SmallButton("Change", Icons.Outlined.Edit, primary = false, onClick = onEdit)
    }
}

@Composable
private fun IconTile(icon: ImageVector, color: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)), Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = color)
    }
}

/** One figure of the period: name, amount and a short line under it. */
@Composable
private fun PayTile(label: String, value: String, detail: String, modifier: Modifier, muted: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (muted) Kit.Muted else Kit.Ink, maxLines = 1)
        Text(detail, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 14.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** What's left to pay for the period (or that it's all paid), the bar of what's paid, and Mark as paid / Undo. */
@Composable
private fun ToPayCard(person: PersonPay, currency: String, onPay: (() -> Unit)?, onUnpaid: (() -> Unit)?) {
    val done = person.total > 0 && person.left == 0L
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (done) Kit.GreenSoft else Color.White)
            .border(1.dp, if (done) Kit.Green.copy(alpha = 0.35f) else Kit.Border, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(if (done) "Paid" else "Left to pay", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                Text(centsText(if (done) person.paid else person.left, currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    color = if (done) Kit.Green else Kit.Ink, maxLines = 1)
            }
            when {
                onPay != null -> SmallButton("Pay", Icons.Outlined.Payments, primary = true, onClick = onPay)
                done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = Kit.Green)
                    Text("All paid", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Green)
                }
            }
        }
        if (person.total == 0L) {
            Text("Nothing to pay for this period yet.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        } else {
            PaidBar(person.paid, person.total)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${centsText(person.paid, currency)} paid of ${centsText(person.total, currency)}", Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.sp,
                    color = Kit.Muted, maxLines = 1)
                onUnpaid?.let {
                    Text("Mark as not paid", Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = it).padding(horizontal = 6.dp, vertical = 3.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                }
            }
        }
    }
}

/** Their shifts, newest first, five until "Show all": with the amount each when paid by the hour, a record otherwise. */
@Composable
private fun ShiftList(person: PersonPay, currency: String, period: String, zone: TimeZone) {
    val lines = person.staff.shifts.sortedByDescending { it.startedAt }
    val hourly = person.monthly == null && person.hourly != null
    var all by remember(person.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Shifts", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
            if (lines.isNotEmpty()) Text("  ${lines.size}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
            Spacer(Modifier.weight(1f))
            if (!hourly && lines.isNotEmpty()) {
                Text(if (person.monthly != null) "Not paid per shift" else "No amounts until pay is set", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            }
        }
        if (lines.isEmpty()) {
            Text("No shifts worked ${if (period == MONTH) "this month" else "this week"}.", Modifier.padding(start = 2.dp), fontFamily = Inter(), fontSize = 12.sp,
                color = Kit.Muted)
            return@Column
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))) {
            (if (all) lines else lines.take(5)).forEachIndexed { index, line ->
                val date = runCatching { LocalDate.parse(line.date) }.getOrNull() ?: return@forEachIndexed
                if (index > 0) HorizontalDivider(color = Kit.RowBorder)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Tint), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text(date.weekdayShort().uppercase(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = Kit.Muted)
                        Text("${date.day}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 16.sp, color = Kit.Ink)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${line.startedAt.clock(zone)} – ${line.endedAt?.clock(zone) ?: "now"}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = Kit.Ink)
                        Text(hoursText(line.workedMinutes) + (if (line.breakMinutes > 0) " · ${hoursText(line.breakMinutes)} break" else "") +
                            (if (line.endedAt == null) " · still on" else ""), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                    }
                    if (hourly) {
                        val wages = line.wages.toCents()
                        Text(wages?.let { centsText(it, currency) } ?: "No wage", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = if (wages == null) Kit.Amber else Kit.Ink)
                    }
                }
            }
            if (lines.size > 5) {
                HorizontalDivider(color = Kit.RowBorder)
                Text(if (all) "Show fewer" else "Show all ${lines.size} shifts", Modifier.fillMaxWidth().clickable { all = !all }.padding(vertical = 10.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, textAlign = TextAlign.Center)
            }
        }
        if (hourly && person.staff.missingRate) {
            Text("Some shifts were worked before a wage was set, so they aren't paid here yet.", Modifier.padding(start = 2.dp), fontFamily = Inter(),
                fontSize = 11.sp, color = Kit.Amber)
        }
    }
}

@Composable
private fun SmallButton(text: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = if (primary) Kit.Green else Color.White, border = if (primary) null else BorderStroke(1.dp, Kit.Border)) {
        Row(Modifier.height(36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (primary) Color.White else Kit.Ink)
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (primary) Color.White else Kit.Ink)
        }
    }
}

// Set how someone is paid: by the hour or a monthly salary, and how much. Returns an error from the save, or null when it worked.
@Composable
private fun PayDialog(person: PersonPay, currency: String, onDismiss: () -> Unit, onSave: suspend (PayKind, Long) -> String?) {
    fun startText(kind: PayKind) = (if (kind == PayKind.MONTHLY) person.monthly else person.hourly)?.let(::amountInput) ?: ""
    var kind by remember(person.id) { mutableStateOf(if (person.monthly != null) PayKind.MONTHLY else PayKind.HOURLY) }
    var text by remember(person.id) { mutableStateOf(startText(kind)) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // How they're paid now (null when nothing is set); switching it asks first (user rule).
    val current = when { person.monthly != null -> PayKind.MONTHLY; person.hourly != null -> PayKind.HOURLY; else -> null }
    var confirmSwitch by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    fun choose(next: PayKind) {
        if (next == kind) return
        kind = next
        text = startText(next)
        problem = null
    }
    fun save() {
        val cents = text.replace(',', '.').toCentsOrNull()
        problem = when {
            cents == null -> if (kind == PayKind.MONTHLY) "Enter the salary, like 1400." else "Enter the wage, like 12.50."
            cents <= 0 -> "Enter an amount above zero."
            // The server's limit (10,000,000) leaves room for currencies with large numbers.
            cents > 1_000_000_000 -> "That's too high. Check the amount."
            else -> null
        }
        if (problem != null || cents == null) return
        if (current != null && current != kind && confirmSwitch == null) { confirmSwitch = cents; return }
        confirmSwitch = null
        saving = true
        scope.launch { problem = onSave(kind, cents); saving = false }
    }
    confirmSwitch?.let { cents ->
        ConfirmPayKindDialog(person, currency, current ?: kind, kind, cents, onCancel = { confirmSwitch = null }) {
            confirmSwitch = null
            saving = true
            scope.launch { problem = onSave(kind, cents); saving = false }
        }
    }
    AppDialog(
        "Pay", onDismiss = onDismiss, subtitle = person.name, busy = saving, maxWidth = 480.dp,
        buttons = {
            KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY, enabled = !saving)
            KitButton(if (saving) "Saving…" else "Save", { save() }, icon = Icons.Outlined.Check, loading = saving)
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KindChoice("By the hour", "Paid for the hours they work", Icons.Outlined.Schedule, kind == PayKind.HOURLY, Modifier.weight(1f)) { choose(PayKind.HOURLY) }
            KindChoice("Monthly salary", "The same amount each month", Icons.Outlined.CalendarMonth, kind == PayKind.MONTHLY, Modifier.weight(1f)) { choose(PayKind.MONTHLY) }
        }
        FieldBlock(if (kind == PayKind.MONTHLY) "Salary" else "Wage", problem = problem) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, if (problem != null) Kit.Danger else Kit.Green, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(currencySymbol(currency).trim(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Muted)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    text, { value -> text = value.filter { it.isDigit() || it == '.' || it == ',' }.take(11); problem = null },
                    Modifier.weight(1f), singleLine = true, enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Kit.Ink),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text(if (kind == PayKind.MONTHLY) "0" else "0.00", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Kit.Faint)
                            inner()
                        }
                    }
                )
                Text(if (kind == PayKind.MONTHLY) "a month" else "an hour", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Muted)
            }
        }
        Text(
            if (kind == PayKind.MONTHLY) "The same every month, however many hours they work. Their hours are still counted."
            else "Shifts from now on are paid at this wage. Shifts already worked keep the wage they had.",
            fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted
        )
    }
}

/** "Change how Anisa is paid?": how they're paid now, how they'll be paid, what it means; Cancel or Yes, change it. */
@Composable
private fun ConfirmPayKindDialog(person: PersonPay, currency: String, from: PayKind, to: PayKind, cents: Long, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val first = person.name.substringBefore(' ')
    val now = if (from == PayKind.MONTHLY) person.monthly?.let { "${wholeText(it, currency)} a month" } else person.hourly?.let { "${centsText(it, currency)} an hour" }
    val next = if (to == PayKind.MONTHLY) "${wholeText(cents, currency)} a month" else "${centsText(cents, currency)} an hour"
    AppDialog("Change how $first is paid?", onCancel, subtitle = "From ${kindLabel(from).lowercase()} to ${kindLabel(to).lowercase()}", maxWidth = 500.dp,
        buttons = {
            KitButton("Cancel", onCancel, style = ButtonStyle.SECONDARY)
            KitButton("Yes, change it", onConfirm, icon = Icons.Outlined.Check)
        }) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KindSummary("Now", from, now ?: "–", false, Modifier.weight(1f).fillMaxHeight())
            Box(Modifier.size(34.dp).clip(CircleShape).background(Kit.GreenSoft), Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = Kit.Green)
            }
            KindSummary("From now on", to, next, true, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Amber.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(17.dp).padding(top = 1.dp), tint = Kit.Amber)
            Text(
                if (to == PayKind.MONTHLY) "$first gets the same $next, however many hours they work. Their hours are still counted, and hours already worked stay paid as before."
                else "$first is paid $next for shifts from now on, and the monthly salary stops. Shifts already worked keep what they had.",
                fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 18.sp, color = Color(0xFF8A5A0B)
            )
        }
    }
}

private fun kindLabel(kind: PayKind): String = if (kind == PayKind.MONTHLY) "Monthly salary" else "By the hour"

@Composable
private fun KindSummary(caption: String, kind: PayKind, amount: String, highlight: Boolean, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (highlight) Kit.GreenSoft else Kit.Canvas)
            .border(if (highlight) 1.5.dp else 1.dp, if (highlight) Kit.Green else Kit.Border, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(caption.uppercase(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.5.sp, color = if (highlight) Kit.Green else Kit.Muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (kind == PayKind.MONTHLY) Icons.Outlined.CalendarMonth else Icons.Outlined.Schedule, null, Modifier.size(16.dp),
                tint = if (highlight) Kit.Green else Kit.Muted)
            Text(kindLabel(kind), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
        }
        Text(amount, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (highlight) Kit.Green else Kit.Ink, maxLines = 1)
    }
}

@Composable
private fun KindChoice(title: String, detail: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp), color = if (selected) Kit.GreenSoft else Color.White,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.Border)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = if (selected) Kit.Green else Kit.Muted)
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = Kit.Green)
            }
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
            Text(detail, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Muted)
        }
    }
}

private fun peopleText(count: Int): String = if (count == 1) "1 person" else "$count people"

// A salary without cents when it's a whole amount ("€1,400 / month"); a wage always with them ("€11.00 / hour").
private fun rateText(person: PersonPay, currency: String): String? =
    person.monthly?.let { "${wholeText(it, currency)} / month" } ?: person.hourly?.let { "${centsText(it, currency)} / hour" }

/** Money without ".00" when it's a whole amount: "€1,400", "€11.50". */
private fun wholeText(cents: Long, currency: String): String = centsText(cents, currency).let { if (cents % 100 == 0L) it.removeSuffix(".00") else it }

/** What goes in the amount field: "1400", "11.50". */
private fun amountInput(cents: Long): String = if (cents % 100 == 0L) "${cents / 100}" else "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

private fun roleName(code: String): String = code.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun periodLabel(period: String, from: LocalDate, to: LocalDate): String =
    if (period == MONTH) "${from.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${from.year}"
    else "${from.shortDay()} – ${to.shortDay()}"
