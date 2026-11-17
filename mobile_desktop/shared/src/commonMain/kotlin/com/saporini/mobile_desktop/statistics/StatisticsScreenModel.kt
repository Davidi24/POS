package com.saporini.mobile_desktop.statistics

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.shifts.toCents
import com.saporini.mobile_desktop.statistics.data.ReportInfoDto
import com.saporini.mobile_desktop.statistics.data.StatisticsRepository
import com.saporini.mobile_desktop.statistics.data.StatsOverviewDto
import com.saporini.mobile_desktop.statistics.data.StatsQuery
import com.saporini.mobile_desktop.statistics.data.StatsSalesDto
import com.saporini.mobile_desktop.statistics.data.StatsStaffDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** The Statistics workspace tabs, in the order of its navigation bar (user's order: Overview, Reports, then the rest). */
// The places in the Statistics bar, in the user's order (2026-11-13). Reports have no place of their own: each page will
// get its own downloads.
// Orders is a place of its own since 2026-11-14 (user: "put Orders separate, not with Sales"; it was a Sales | Orders switch),
// and Finance comes right after Sales (user, 2026-11-14). The nav bar follows this order.
enum class StatsTab(val title: String) {
    // Stock is its own place after Dishes (user, 2026-11-16: "make stock separate, not with dishes").
    OVERVIEW("Overview"), SALES("Sales"), FINANCE("Finance"), ORDERS("Orders"), DISHES("Dishes"), STOCK("Stock"), TABLES("Tables"), TEAM("Team")
}

enum class StatsPeriod(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 days"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    // From 1 January to today (user, 2026-11-14).
    THIS_YEAR("This year"),
    // Since the restaurant opened (user, 2026-11-15): its first day, at most a year back (the server's limit).
    ALL_TIME("All time"),
    CUSTOM("Custom")
}

/**
 * What the figures are compared with; the screens always name the dates (user, 2026-11-14: "the days before" was confusing).
 * [word] follows the dates in the Filters choices: "1 – 14 Oct · last month".
 */
enum class CompareMode(val word: String) {
    LAST_MONTH("last month"),
    PREVIOUS("just before"),
    LAST_WEEK("a week earlier"),
    LAST_YEAR("last year"),
    NONE("")
}

/** True for a period inside one month from its 1st (a month, or this month so far): its graph spans the whole month. */
fun monthLike(from: LocalDate, to: LocalDate): Boolean = from.day == 1 && from.month == to.month && from.year == to.year

/** True for a year from 1 January (this year so far, or a whole year): its graph shows months, not days (user, 2026-11-14). */
fun yearLike(from: LocalDate, to: LocalDate): Boolean = from.day == 1 && from.month == kotlinx.datetime.Month.JANUARY &&
    to.year == from.year && !monthLike(from, to)

/** The comparison that fits a period: a month → the same days last month, one day → that weekday last week, a year so far → the same days last year, else the days just before. */
fun defaultCompare(from: LocalDate?, to: LocalDate?): CompareMode = when {
    from == null || to == null -> CompareMode.LAST_MONTH
    monthLike(from, to) && from != to -> CompareMode.LAST_MONTH
    from == to -> CompareMode.LAST_WEEK
    // A year so far (from 1 January): the same days last year.
    from.day == 1 && from.month == kotlinx.datetime.Month.JANUARY && from.year == to.year -> CompareMode.LAST_YEAR
    else -> CompareMode.PREVIOUS
}

/** A branch the figures can be narrowed to. */
data class StatsBranch(val id: String, val name: String)

/** A place on a floor (the tables grouped in Tables → Edit → Create places) and how many tables it has; null name = tables in no place. */
data class StatsPlace(val name: String?, val tables: Int)

/**
 * The narrower filters (one options button). The server doesn't filter by them yet: until the production pass the
 * screens apply them to what was loaded (MOCK, see [StatsView]).
 */
data class StatsFilters(
    val orderType: String? = null,
    val source: String? = null,
    val section: String? = null,
    val staffId: String? = null,
    val method: String? = null
) {
    val count: Int get() = listOfNotNull(orderType, source, section, staffId, method).size

    /**
     * Only the filters a place offers: Sales has the menu section, Orders the staff member, neither the payment method
     * (Finance's; user, 2026-11-14: filters made to fit the page). The other places keep every filter.
     */
    fun keptFor(tab: StatsTab): StatsFilters = when (tab) {
        StatsTab.SALES -> copy(staffId = null, method = null)
        StatsTab.ORDERS -> copy(section = null, method = null)
        else -> this
    }
}

const val MAX_STATS_DAYS = 366

/**
 * TEMPORARY (user, 2026-11-14: "make it with one branch so I see how it looks"): the workspace acts as if the restaurant had
 * one branch (no branch picker, no branch lines, the whole restaurant's figures). Set back to false to see the branches.
 */
const val OneBranchPreview = true

/** A downloaded report, ready for the screen to save or share. */
data class StatsDownload(val code: String, val fileName: String, val csv: String)

data class StatisticsState(
    val restaurantId: String? = null,
    val canRead: Boolean = false,
    val branchId: String? = null,
    val branches: List<StatsBranch> = emptyList(),
    // The dishes' photos from the menu, by dish id and by name, for the dish lists (empty when the menu can't be read).
    val dishPhotos: Map<String, String> = emptyMap(),
    // The restaurant's name, for the top of downloaded reports (null when it can't be read).
    val restaurantName: String? = null,
    // The restaurant's first day (its first sale in the last year or the day it was added), for "All time".
    val openedOn: LocalDate? = null,
    // Each branch's floors and their places, by branch id then floor name (empty when the tables can't be read), for Sales by floor.
    val floorPlaces: Map<String, Map<String, List<StatsPlace>>> = emptyMap(),
    // The order types the restaurant takes, from its settings (null = unknown: the figures decide).
    val orderTypesOn: Set<String>? = null,
    // Shown in another currency than the restaurant's own (sales from before a currency change); null = its own.
    val currency: String? = null,
    val currencies: List<String> = emptyList(),
    // This month from its 1st by default (user, 2026-11-03), shown as "November 2026".
    val period: StatsPeriod = StatsPeriod.THIS_MONTH,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val compare: CompareMode = CompareMode.LAST_MONTH,
    val filters: StatsFilters = StatsFilters(),
    val tab: StatsTab = StatsTab.OVERVIEW,
    // One load fills every tab (switching tabs doesn't wait): the period's figures, the comparison's and each branch's.
    val overview: StatsOverviewDto? = null,
    val sales: StatsSalesDto? = null,
    val staff: StatsStaffDto? = null,
    val compareOverview: StatsOverviewDto? = null,
    val compareSales: StatsSalesDto? = null,
    // The whole previous month (or year), for the graph of a month (or year) still running: its line goes on to the end.
    val compareWholeSales: StatsSalesDto? = null,
    // Sales by hour on one day of its own calendar (user, 2026-11-14: "a calendar here, separate, that chooses only the
    // date"); null = the period's hours. The day's sales and the same weekday a week earlier, to compare with.
    val hoursDay: LocalDate? = null,
    val hoursSales: StatsSalesDto? = null,
    val hoursCompareSales: StatsSalesDto? = null,
    val hoursLoading: Boolean = false,
    // Sales by weekday for one week of its own calendar (user, 2026-11-14: "select one week like Outlook"): its Monday,
    // that week's sales and the week before; null = the period's averages.
    val week: LocalDate? = null,
    val weekSales: StatsSalesDto? = null,
    val weekCompareSales: StatsSalesDto? = null,
    val weekLoading: Boolean = false,
    val branchOverviews: Map<String, StatsOverviewDto> = emptyMap(),
    // Sales details (user, 2026-11-15; only for a month or less): open or not, and its days with their orders; the week before
    // the period, so its first days have something to compare with; whether the person may see orders (ORDER_READ).
    val everyDay: Boolean = false,
    val details: StatsDetails? = null,
    val daysBefore: StatsSalesDto? = null,
    val canSeeOrders: Boolean = false,
    // Sales' Day by day as a calendar instead of the graph (user, 2026-11-15: "make the graph a toggle that can show as calendar").
    val salesCalendar: Boolean = false,
    // Finance's switch at the top right (user, 2026-11-15): Balance, Money in or Money out.
    val financeView: FinanceView = FinanceView.BALANCE,
    val reports: List<ReportInfoDto> = emptyList(),
    val loading: Boolean = false,
    val stale: Boolean = true,
    val error: String? = null,
    val downloading: String? = null,
    val download: StatsDownload? = null
)

/** "+12%", "-3%", "no data before" (nothing to compare with) or null, comparing a figure with the period before. */
fun changeText(value: OrderDecimal, previous: OrderDecimal): String? {
    val now = value.toCents() ?: return null
    val before = previous.toCents() ?: return null
    return changeText(now, before)
}

/** The same for plain numbers (counts, cents). */
fun changeText(now: Long, before: Long): String? {
    if (before == 0L) return if (now == 0L) null else com.saporini.mobile_desktop.core.components.NoDataBefore
    val percent = ((now - before) * 1000 / kotlin.math.abs(before)).let { (it + if (it >= 0) 5 else -5) / 10 }
    return when {
        percent > 0 -> "+$percent%"
        percent < 0 -> "$percent%"
        else -> "±0%"
    }
}

/** The first and last day of a preset period, counted in the restaurant's days. */
fun periodRange(period: StatsPeriod, today: LocalDate): Pair<LocalDate, LocalDate>? = when (period) {
    StatsPeriod.TODAY -> today to today
    StatsPeriod.YESTERDAY -> today.minus(DatePeriod(days = 1)).let { it to it }
    StatsPeriod.LAST_7_DAYS -> today.minus(DatePeriod(days = 6)) to today
    StatsPeriod.THIS_MONTH -> LocalDate(today.year, today.month, 1) to today
    StatsPeriod.LAST_MONTH -> LocalDate(today.year, today.month, 1).minus(DatePeriod(months = 1)).let { first ->
        first to LocalDate(today.year, today.month, 1).minus(DatePeriod(days = 1))
    }
    StatsPeriod.THIS_YEAR -> LocalDate(today.year, 1, 1) to today
    // Needs the restaurant's first day; the model works it out (see StatisticsScreenModel.period).
    StatsPeriod.ALL_TIME -> null
    StatsPeriod.CUSTOM -> null
}

/** The days the figures are compared with: as many days just before, or the same dates a year earlier. */
fun compareRange(mode: CompareMode, from: LocalDate, to: LocalDate): Pair<LocalDate, LocalDate>? = when (mode) {
    CompareMode.PREVIOUS -> {
        val days = from.daysUntil(to) + 1
        from.minus(DatePeriod(days = days)) to from.minus(DatePeriod(days = 1))
    }
    CompareMode.LAST_YEAR -> from.minus(DatePeriod(years = 1)) to to.minus(DatePeriod(years = 1))
    // The same days a month earlier (31 Mar → 28 Feb), and a week earlier.
    CompareMode.LAST_MONTH -> from.minus(DatePeriod(months = 1)) to to.minus(DatePeriod(months = 1))
    CompareMode.LAST_WEEK -> from.minus(DatePeriod(days = 7)) to to.minus(DatePeriod(days = 7))
    CompareMode.NONE -> null
}

/**
 * The Statistics workspace's state: the period, branch, comparison and filters chosen, and the figures. One load
 * fetches what every tab needs (the period, the days it's compared with and each branch), so switching tabs is
 * instant; a late answer for an earlier choice is dropped.
 */
class StatisticsScreenModel(
    private val repository: StatisticsRepository,
    session: SessionManager,
    private val today: () -> LocalDate = { kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()) },
    // The restaurant's branches, for the branch filter and the branch comparison (empty when they can't be read).
    private val branchesOf: suspend (String) -> List<StatsBranch> = { emptyList() },
    // The dishes' photos, by dish id and by name.
    private val photosOf: suspend (String) -> Map<String, String> = { emptyMap() },
    // Each branch's floors with their places and tables, by branch id then floor name.
    private val placesOf: suspend (String) -> Map<String, Map<String, List<StatsPlace>>> = { emptyMap() },
    // The restaurant's name, for downloaded reports.
    private val restaurantNameOf: suspend (String) -> String? = { null },
    // The day the restaurant was added, for "All time".
    private val addedOn: suspend (String) -> LocalDate? = { null },
    // A day's orders (restaurant, branch or null = every branch, from, to) and one order's receipt, for the Day window.
    private val dayOrdersOf: suspend (String, String?, kotlin.time.Instant, kotlin.time.Instant) -> List<StatsOrder> = { _, _, _, _ -> emptyList() },
    private val receiptOf: suspend (String, String) -> com.saporini.mobile_desktop.pos.payment.data.ReceiptDto? = { _, _ -> null },
    // The temporary one-branch look (see [OneBranchPreview]); tests pass false so they keep seeing the branches.
    private val oneBranchPreview: Boolean = OneBranchPreview
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(StatisticsState())
    val state: StateFlow<StatisticsState> = mutable.asStateFlow()
    private var revision = 0L
    private var active = false
    private var loadJob: Job? = null
    private var downloadJob: Job? = null
    private var branchesFor: String? = null
    private var hoursJob: Job? = null
    private var hoursRevision = 0L
    private var weekJob: Job? = null
    private var weekRevision = 0L
    private var ordersJob: Job? = null
    private var receiptJob: Job? = null
    private var dayRevision = 0L
    private var beforeJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                loadJob?.cancel()
                downloadJob?.cancel()
                downloadJob = null
                hoursJob?.cancel()
                hoursRevision++
                weekJob?.cancel()
                weekRevision++
                stopDetails()
                beforeJob?.cancel()
                branchesFor = null
                val (from, to) = periodRange(StatsPeriod.THIS_MONTH, today())!!
                mutable.value = StatisticsState(
                    restaurantId = user?.takeIf { it.isActive }?.restaurantId,
                    // The whole restaurant for everyone while the branch filter is off (user, 2026-11-14); platform admins used to
                    // start on the branch they entered: user?.defaultBranchId.takeIf { isPlatformAdmin(user) }.
                    branchId = null,
                    canRead = user != null && "REPORTS_READ" in user.permissions,
                    canSeeOrders = user != null && "ORDER_READ" in user.permissions,
                    from = from,
                    to = to
                )
                if (active) load()
            }
        }
    }

    /** The restaurant's today, as the periods count it. */
    fun todayDate(): LocalDate = today()

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load() else {
            revision++
            loadJob?.cancel()
            downloadJob?.cancel()
            downloadJob = null
            hoursJob?.cancel()
            hoursRevision++
            weekJob?.cancel()
            weekRevision++
            stopDetails()
            beforeJob?.cancel()
            mutable.update { it.copy(loading = false, downloading = null, download = null, hoursLoading = false, weekLoading = false,
                everyDay = false, details = null) }
        }
    }

    /** Opens a tab; the figures are already there (Reports loads its list the first time). */
    fun tab(tab: StatsTab) {
        if (state.value.tab == tab) return
        mutable.update { it.copy(tab = tab, error = null, filters = it.filters.keptFor(tab)) }
    }


    fun period(period: StatsPeriod) {
        if (period == StatsPeriod.CUSTOM) {
            mutable.update { it.copy(period = period) }
            return
        }
        if (period == StatsPeriod.ALL_TIME) {
            // From the restaurant's first day (its first sale or the day it was added, whichever is earlier), at most 366 days
            // back, which is what the server answers for; nothing to compare with before it opened.
            val today = today()
            val earliest = today.minus(DatePeriod(days = MAX_STATS_DAYS - 1))
            val from = (state.value.openedOn ?: earliest).let { if (it < earliest) earliest else if (it > today) today else it }
            change { it.copy(period = period, from = from, to = today, compare = CompareMode.NONE).withoutPicks() }
            return
        }
        val (from, to) = periodRange(period, today()) ?: return
        change { it.copy(period = period, from = from, to = to, compare = defaultCompare(from, to)).withoutPicks() }
    }

    /**
     * By month (user, 2026-11-16): the month that starts on [first], from its 1st to its last day (to today while it runs);
     * this month and last month are their quick picks.
     */
    fun month(first: LocalDate) {
        val today = today()
        val start = LocalDate(first.year, first.month, 1)
        if (start > today) return
        val thisMonth = LocalDate(today.year, today.month, 1)
        when (start) {
            thisMonth -> period(StatsPeriod.THIS_MONTH)
            thisMonth.minus(DatePeriod(months = 1)) -> period(StatsPeriod.LAST_MONTH)
            else -> custom(start, start.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)))
        }
    }

    /** By year (user, 2026-11-16): 1 January to 31 December, or This year (to today) for the year running. */
    fun year(year: Int) {
        val today = today()
        when {
            year > today.year -> return
            year == today.year -> period(StatsPeriod.THIS_YEAR)
            else -> custom(LocalDate(year, 1, 1), LocalDate(year, 12, 31))
        }
    }

    /** A chosen range; refused (with a message) when reversed or longer than a year. */
    fun custom(from: LocalDate, to: LocalDate): Boolean {
        val problem = when {
            to < from -> "The end can't be before the start"
            from.daysUntil(to) + 1 > MAX_STATS_DAYS -> "Choose at most $MAX_STATS_DAYS days"
            to > today() -> "The end can't be in the future"
            else -> null
        }
        if (problem != null) {
            mutable.update { it.copy(error = problem) }
            return false
        }
        change { it.copy(period = StatsPeriod.CUSTOM, from = from, to = to, compare = defaultCompare(from, to)).withoutPicks() }
        return true
    }

    /** Moves the whole range one step back (negative) or forward, keeping its length; a month steps a whole month. */
    fun shift(steps: Int) {
        val current = state.value
        val from = current.from ?: return
        val to = current.to ?: return
        if (com.saporini.mobile_desktop.core.components.isWholeMonth(from, to, today())) {
            val first = from.plus(DatePeriod(months = steps))
            if (first > today()) return
            val last = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
            change { it.copy(period = StatsPeriod.CUSTOM, from = first, to = if (last > today()) today() else last).withoutPicks() }
            return
        }
        val days = from.daysUntil(to) + 1
        if (days !in 1..MAX_STATS_DAYS) return
        val shift = days.toLong() * steps.toLong()
        if (shift !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return
        val newFrom = from.plus(DatePeriod(days = shift.toInt()))
        val newTo = to.plus(DatePeriod(days = shift.toInt()))
        if (newFrom > today() || newTo > today()) return
        change { it.copy(period = StatsPeriod.CUSTOM, from = newFrom, to = newTo).withoutPicks() }
    }

    fun branch(branchId: String?) = change { it.copy(branchId = branchId) }

    fun compare(mode: CompareMode) {
        if (state.value.compare == mode) return
        change { it.copy(compare = mode) }
    }

    /** The narrower filters work on what's loaded (see [StatsFilters]), so changing them doesn't reload. */
    fun filters(update: (StatsFilters) -> StatsFilters) = mutable.update { it.copy(filters = update(it.filters)) }

    fun clearFilters() = mutable.update { it.copy(filters = StatsFilters()) }

    /** Shows the figures in [code] (e.g. the currency used before a change); the restaurant's own when it's the first. */
    fun currency(code: String) = change { it.copy(currency = code.takeIf { c -> c != it.currencies.firstOrNull() }) }

    private fun change(update: (StatisticsState) -> StatisticsState) {
        revision++
        // The figures shown stay (dimmed) until the new ones arrive, so nothing jumps.
        mutable.update { update(it).copy(stale = true, error = null, download = null, daysBefore = null) }
        load()
        // The calendar on Day by day compares each day with a week before, so it needs the days before the period too.
        if (state.value.salesCalendar) loadDaysBefore()
    }

    fun refresh() = load()

    /**
     * Shows Sales by hour for [day] alone (null = back to the period). Days after today can't be picked. The day stays
     * when the branch or currency changes (it's loaded again) and goes when the period changes.
     */
    fun hoursDay(day: LocalDate?) {
        if (day != null && day > today()) return
        if (state.value.hoursDay == day) return
        hoursJob?.cancel()
        hoursRevision++
        mutable.update { if (day == null) it.withoutHoursDay() else it.copy(hoursDay = day) }
        if (day != null) loadHoursDay()
    }

    private fun StatisticsState.withoutHoursDay() = copy(hoursDay = null, hoursSales = null, hoursCompareSales = null, hoursLoading = false)

    private fun StatisticsState.withoutWeek() = copy(week = null, weekSales = null, weekCompareSales = null, weekLoading = false)

    /** A new period lets go of the day and the week picked in the panels' own calendars. */
    private fun StatisticsState.withoutPicks() = withoutHoursDay().withoutWeek()

    /**
     * Shows Sales by weekday for the week that starts on [monday] (null = back to the period's averages). Weeks that
     * haven't started can't be picked. Like the hours' day, it stays with a branch or currency change.
     */
    fun week(monday: LocalDate?) {
        val start = monday?.let { it.minus(DatePeriod(days = it.dayOfWeek.isoDayNumber - 1)) }
        if (start != null && start > today()) return
        if (state.value.week == start) return
        weekJob?.cancel()
        weekRevision++
        mutable.update { if (start == null) it.withoutWeek() else it.copy(week = start) }
        if (start != null) loadWeek()
    }

    // ---- Sales details: every day with its orders (user, 2026-11-15) ----

    /** Finance shows its balance, the money in or the money out. */
    fun financeView(view: FinanceView) = mutable.update { it.copy(financeView = view) }

    /** Day by day as a calendar (or back to the graph). */
    fun showSalesCalendar(on: Boolean) {
        mutable.update { it.copy(salesCalendar = on) }
        if (on) loadDaysBefore()
    }

    /**
     * Opens Sales details (only for a month or less, user 2026-11-15): every day of the period as a card with its orders;
     * [focus] = the day to bring into view (a day clicked on the graph, the calendar or the best/lowest day card).
     */
    fun openEveryDay(focus: LocalDate? = null) {
        val current = state.value
        val from = current.from ?: return
        val to = current.to ?: return
        if (from.daysUntil(to) + 1 > DetailsMaxDays) return
        stopDetails()
        mutable.update { it.copy(everyDay = true, details = StatsDetails(focus = focus, ordersLoading = it.canSeeOrders)) }
        loadDaysBefore()
        loadDetailsOrders()
    }

    fun closeEveryDay() {
        stopDetails()
        mutable.update { it.copy(everyDay = false, details = null) }
    }

    /** Loads the orders again (after a failure). */
    fun retryDetails() {
        stopDetails()
        mutable.update { it.copy(details = it.details?.copy(orders = null, ordersLoading = it.canSeeOrders, ordersProblem = null, order = null, receipt = null)) }
        loadDetailsOrders()
    }

    /** A day's card shows all its orders (or only the first ones again). */
    fun showAllOrders(date: LocalDate) = mutable.update { s ->
        s.copy(details = s.details?.let { d -> d.copy(allShown = if (date in d.allShown) d.allShown - date else d.allShown + date) })
    }

    /** Opens an order under its line (its receipt), closing the one open before; a second click closes it. */
    fun openOrder(order: StatsOrder) {
        val restaurantId = state.value.restaurantId ?: return
        val details = state.value.details ?: return
        if (details.order?.id == order.id) {
            closeOrder()
            return
        }
        receiptJob?.cancel()
        val token = dayRevision
        mutable.update { it.copy(details = it.details?.copy(order = order, receipt = null, receiptLoading = true, receiptProblem = null)) }
        receiptJob = work.launch {
            try {
                val receipt = receiptOf(restaurantId, order.id)
                if (token == dayRevision) mutable.update { s ->
                    s.copy(details = s.details?.takeIf { it.order?.id == order.id }?.copy(receipt = receipt, receiptLoading = false,
                        receiptProblem = if (receipt == null) "This order's receipt can't be shown." else null) ?: s.details)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == dayRevision) mutable.update { s ->
                    s.copy(details = s.details?.takeIf { it.order?.id == order.id }?.copy(receiptLoading = false, receiptProblem = orderMessage(e)) ?: s.details)
                }
            }
        }
    }

    fun closeOrder() {
        receiptJob?.cancel()
        mutable.update { it.copy(details = it.details?.copy(order = null, receipt = null, receiptLoading = false, receiptProblem = null)) }
    }

    private fun stopDetails() {
        dayRevision++
        ordersJob?.cancel()
        receiptJob?.cancel()
    }

    /** The restaurant's time zone (the figures' days), else this device's. */
    private fun zone(): TimeZone = state.value.overview?.period?.timezone?.let { runCatching { TimeZone.of(it) }.getOrNull() }
        ?: TimeZone.currentSystemDefault()

    /**
     * The period's orders in one go (people who may see orders), from its first midnight to the one after its last, in the
     * restaurant's time zone. MOCK-ish: they're listed by when they opened (the history's rule), the figures count when they
     * were paid, so a late order can sit on the day before. Orders that never had a dish are left out (user, 2026-11-15:
     * "no need for empty orders").
     */
    private fun loadDetailsOrders() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val from = current.from ?: return
        val to = current.to ?: return
        if (!current.canSeeOrders) return
        val token = dayRevision
        ordersJob = work.launch {
            try {
                val zone = zone()
                val orders = dayOrdersOf(restaurantId, current.branchId, from.atStartOfDayIn(zone), to.plus(DatePeriod(days = 1)).atStartOfDayIn(zone))
                    .filter { (it.items ?: 0) > 0 || it.total > 0 }
                    .sortedBy { it.openedAt }
                if (token == dayRevision) mutable.update { s -> s.copy(details = s.details?.copy(orders = orders, ordersLoading = false, ordersProblem = null)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == dayRevision) mutable.update { s -> s.copy(details = s.details?.copy(ordersLoading = false, ordersProblem = orderMessage(e))) }
            }
        }
    }

    /** The seven days before the period, for the first days' "against a week before" (Sales details and the calendar). */
    private fun loadDaysBefore() {
        val current = state.value
        if (current.daysBefore != null) return
        val restaurantId = current.restaurantId ?: return
        val from = current.from ?: return
        if (!current.canRead) return
        val token = revision
        beforeJob?.cancel()
        beforeJob = work.launch {
            try {
                val sales = repository.sales(StatsQuery(restaurantId, current.branchId, from.minus(DatePeriod(days = 7)), from.minus(DatePeriod(days = 1)), current.currency))
                if (token == revision) mutable.update { it.copy(daysBefore = sales) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Without them the first week simply shows no change.
            }
        }
    }

    private fun orderMessage(e: Exception): String = when {
        e is ApiException && (e.status == 401 || e.status == 403) -> "Your role can't see orders."
        e is ApiException && e.status in 400..499 -> e.message
        e is ApiException -> "The server couldn't list the orders. Try again."
        else -> "Could not load the orders. Check the connection and try again."
    }

    /** The week's sales and the week before's; the last week's figures stay (dimmed) until they come. */
    private fun loadWeek() {
        val current = state.value
        val monday = current.week ?: return
        val restaurantId = current.restaurantId ?: return
        if (!active || !current.canRead) return
        val token = ++weekRevision
        weekJob?.cancel()
        weekJob = work.launch {
            mutable.update { it.copy(weekLoading = true) }
            try {
                val sunday = monday.plus(DatePeriod(days = 6))
                val query = StatsQuery(restaurantId, current.branchId, monday, if (sunday > today()) today() else sunday, current.currency)
                val before = monday.minus(DatePeriod(days = 7))
                val (weekSales, earlier) = coroutineScope {
                    val sales = async { repository.sales(query) }
                    val previous = async {
                        try { repository.sales(query.copy(from = before, to = before.plus(DatePeriod(days = 6)))) }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    }
                    sales.await() to previous.await()
                }
                if (token == weekRevision) mutable.update { it.copy(weekSales = weekSales, weekCompareSales = earlier) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == weekRevision) mutable.update { it.withoutWeek().copy(error = message(e)) }
            } finally {
                if (token == weekRevision) mutable.update { it.copy(weekLoading = false) }
            }
        }
    }

    /** The picked day's sales and the same weekday a week earlier; the last day's figures stay (dimmed) until they come. */
    private fun loadHoursDay() {
        val current = state.value
        val day = current.hoursDay ?: return
        val restaurantId = current.restaurantId ?: return
        if (!active || !current.canRead) return
        val token = ++hoursRevision
        hoursJob?.cancel()
        hoursJob = work.launch {
            mutable.update { it.copy(hoursLoading = true) }
            try {
                val query = StatsQuery(restaurantId, current.branchId, day, day, current.currency)
                val weekBefore = day.minus(DatePeriod(days = 7))
                val (daySales, before) = coroutineScope {
                    val sales = async { repository.sales(query) }
                    val earlier = async {
                        try { repository.sales(query.copy(from = weekBefore, to = weekBefore)) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    }
                    sales.await() to earlier.await()
                }
                if (token == hoursRevision) mutable.update { it.copy(hoursSales = daySales, hoursCompareSales = before) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Back to the period's hours, with the message.
                if (token == hoursRevision) mutable.update { it.withoutHoursDay().copy(error = message(e)) }
            } finally {
                if (token == hoursRevision) mutable.update { it.copy(hoursLoading = false) }
            }
        }
    }

    private fun load() {
        downloadJob?.cancel()
        downloadJob = null
        mutable.update { it.copy(downloading = null, download = null) }
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val from = current.from ?: return
        val to = current.to ?: return
        if (!active || !current.canRead) return
        val token = ++revision
        // A day picked for Sales by hour is loaded again with the new branch or currency.
        if (current.hoursDay != null) loadHoursDay()
        if (current.week != null) loadWeek()
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                // The branches and the dish photos once per restaurant; a person who can't read them simply has no branch filter
                // and plates instead of photos.
                if (branchesFor != restaurantId) {
                    // The reports the server makes, also once per restaurant (for the pages' downloads).
                    val (once, extra) = coroutineScope {
                        // The branches are back for the by-branch panels at the end of the pages (user, 2026-11-14: "add the branch ones
                        // at the end"); the branch filter itself stays off (commented out in StatisticsScreen.kt), so the figures are the
                        // whole restaurant's and each branch is compared at the end.
                        val branches = async {
                            if (oneBranchPreview) emptyList()
                            else try { branchesOf(restaurantId) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
                        }
                        val photos = async { try { photosOf(restaurantId) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() } }
                        val places = async { try { placesOf(restaurantId) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() } }
                        val name = async { try { restaurantNameOf(restaurantId) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } }
                        // The first day with a sale in the last year, and the day the restaurant was added: "All time" starts at the earlier.
                        val opened = async {
                            val added = try { addedOn(restaurantId) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                            val firstSale = try {
                                val today = today()
                                repository.overview(StatsQuery(restaurantId, null, today.minus(DatePeriod(days = MAX_STATS_DAYS - 1)), today, null))
                                    .salesByDay.filter { (it.sales.toCents() ?: 0L) > 0 }.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.minOrNull()
                            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                            listOfNotNull(added, firstSale).minOrNull()
                        }
                        val types = async { try { repository.orderTypesOn(restaurantId) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } }
                        val reports = async { try { repository.reports(restaurantId) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() } }
                        Triple(branches.await(), photos.await(), types.await()) to (Triple(reports.await(), places.await(), name.await()) to opened.await())
                    }
                    val (branches, photos, types) = once
                    val (more, openedOn) = extra
                    val (reportList, floorPlaces, restaurantName) = more
                    branchesFor = restaurantId
                    if (token == revision) mutable.update { it.copy(branches = branches, dishPhotos = photos, orderTypesOn = types, reports = reportList, floorPlaces = floorPlaces, restaurantName = restaurantName, openedOn = openedOn) }
                }
                val shown = state.value
                val query = StatsQuery(restaurantId, shown.branchId, from, to, shown.currency)
                val compared = compareRange(shown.compare, from, to)?.let { (a, b) -> query.copy(from = a, to = b) }
                // A month (or year) still running, compared with last month (or year): its graph also draws the rest of it.
                val monthEnd = from.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
                val wholeLastMonth = when {
                    shown.compare == CompareMode.LAST_MONTH && monthLike(from, to) && to < monthEnd ->
                        query.copy(from = from.minus(DatePeriod(months = 1)), to = from.minus(DatePeriod(days = 1)))
                    shown.compare == CompareMode.LAST_YEAR && yearLike(from, to) && to < LocalDate(from.year, 12, 31) ->
                        query.copy(from = LocalDate(from.year - 1, 1, 1), to = LocalDate(from.year - 1, 12, 31))
                    else -> null
                }
                val perBranch = if (shown.branchId == null && shown.branches.size > 1) shown.branches else emptyList()
                coroutineScope {
                    val overview = async { repository.overview(query) }
                    val sales = async { repository.sales(query) }
                    val staff = async { repository.staff(query) }
                    val compareOverview = compared?.let { async { repository.overview(it) } }
                    val compareSales = compared?.let { async { repository.sales(it) } }
                    val compareMonth = wholeLastMonth?.let { async { try { repository.sales(it) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } } }
                    // Each branch on its own, for the branch comparisons; one that fails is left out.
                    val branches = perBranch.map { branch ->
                        branch.id to async {
                            try { repository.overview(query.copy(branchId = branch.id)) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                        }
                    }
                    val result = overview.await()
                    val salesResult = sales.await()
                    val staffResult = staff.await()
                    val compareOverviewResult = compareOverview?.await()
                    val compareSalesResult = compareSales?.await()
                    val compareMonthResult = compareMonth?.await()
                    val branchResults = branches.mapNotNull { (id, answer) -> answer.await()?.let { id to it } }.toMap()
                    if (token == revision) mutable.update {
                        it.copy(
                            overview = result, sales = salesResult, staff = staffResult,
                            compareOverview = compareOverviewResult, compareSales = compareSalesResult, compareWholeSales = compareMonthResult,
                            branchOverviews = branchResults,
                            currencies = result.period.currencies, stale = false, error = null
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) {
                    val accessDenied = denied(e)
                    mutable.update {
                        if (accessDenied) {
                            it.copy(
                                overview = null, sales = null, staff = null, compareOverview = null, compareSales = null, compareWholeSales = null,
                                branchOverviews = emptyMap(), reports = emptyList(), download = null, downloading = null,
                                stale = true, error = message(e), canRead = false
                            )
                        } else {
                            it.copy(stale = true, error = message(e))
                        }
                    }
                }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    /** Fetches one report as CSV for the period shown; the screen saves [StatisticsState.download]. */
    fun download(code: String) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val from = current.from ?: return
        val to = current.to ?: return
        if (!current.canRead || current.downloading != null) return
        val token = revision
        mutable.update { it.copy(downloading = code, download = null) }
        downloadJob = work.launch {
            try {
                val csv = repository.report(StatsQuery(restaurantId, current.branchId, from, to, current.currency), code)
                if (token == revision) mutable.update { it.copy(download = StatsDownload(code, "$code-$from-to-$to.csv", csv)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = message(e)) }
            } finally {
                if (token == revision && state.value.downloading == code) {
                    mutable.update { it.copy(downloading = null) }
                }
            }
        }
    }

    /** A report made on the device from the figures shown (the ones the server doesn't make yet). */
    fun offer(code: String, csv: String, fileName: String? = null) {
        val current = state.value
        val from = current.from ?: return
        val to = current.to ?: return
        if (!current.canRead) return
        mutable.update { it.copy(download = StatsDownload(code, fileName ?: "$code-$from-to-$to.csv", csv)) }
    }

    fun downloadHandled() = mutable.update { it.copy(download = null) }

    fun clearError() = mutable.update { it.copy(error = null) }

    override fun onDispose() {
        revision++
        active = false
        work.cancel()
    }

    private fun denied(e: Exception) = e is ApiException && (e.status == 401 || e.status == 403)

    private fun message(e: Exception): String = when {
        e is ApiException && (e.status == 401 || e.status == 403) -> "You don't have permission to see statistics."
        e is ApiException && e.status in 400..499 -> e.message
        e is ApiException -> "The server couldn't work out the statistics. Try again."
        else -> "Could not load the statistics. Check the connection and try again."
    }
}
