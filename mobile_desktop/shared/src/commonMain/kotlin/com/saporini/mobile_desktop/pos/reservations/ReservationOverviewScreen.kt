package com.saporini.mobile_desktop.pos.reservations

import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationSource
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import com.saporini.mobile_desktop.core.components.SearchField
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.ReportProblem
import com.saporini.mobile_desktop.core.ui.PlatformVerticalScrollbar
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.outlined.Public
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha
import com.saporini.mobile_desktop.core.components.SkeletonLight
import com.saporini.mobile_desktop.core.components.SkeletonBox
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.core.ui.ScreenSize
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationCapacity
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationSummary
import com.saporini.mobile_desktop.pos.tables.ui.ReservationTimeMap
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.overview_next_hours
import mobile_desktop.shared.generated.resources.overview_no_more_reservations
import mobile_desktop.shared.generated.resources.overview_no_table
import mobile_desktop.shared.generated.resources.overview_reservations
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val OrderLikeBorder = Color(0xFFE3E8E1)
private val ActiveStatuses = setOf(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN, ReservationStatus.SEATED)
private val PresentStatuses = ActiveStatuses + ReservationStatus.COMPLETED
// Bookings made online (website, app or a partner service): the "Ordered online" row and the past-day card.
private val OnlineSources = setOf(ReservationSource.WEB, ReservationSource.MOBILE, ReservationSource.THIRD_PARTY)
// The online colour (same purple family as the Online menu cover).
private val OnlineColor = Color(0xFF6E5A9E)

// Fading of Previous cards: finished ones the most, those still going a little less.
private const val FinishedAlpha = 0.45f
private const val StillActiveAlpha = 0.72f
// Previous reservations that are over: they sit above the "Still active" line.
private val FinishedStatuses = setOf(ReservationStatus.COMPLETED, ReservationStatus.NO_SHOW)
// Done with arriving, whatever the time: these move up to the earlier rows.
private val PassedStatuses = setOf(ReservationStatus.CHECKED_IN, ReservationStatus.SEATED, ReservationStatus.COMPLETED, ReservationStatus.NO_SHOW)
// Reservation cards under a section header sit this far in from each side, so the header bars read a bit wider.
private val SectionRowInset = 10.dp

// Pause between folding the open section and opening the clicked one.
private const val FOLD_STEP_MILLIS = 240L

// The overview list's sections that open one at a time.
private enum class ListSection { ARRIVING, LATER, TOMORROW }

// From this hour today, tomorrow's bookings show as their own section under Later.
private const val TOMORROW_FROM_HOUR = 22

// Start loading the next page this many rows before the end of the list.
private const val LOAD_AHEAD_ITEMS = 6

// One list on the overview that loads page by page.
internal data class ListPaging(
    val hasMore: Boolean,
    val loading: Boolean,
    // The last page failed: no more automatic loading until "Try again".
    val failed: Boolean,
    val onLoadMore: () -> Unit,
    // False until the first page has arrived.
    val ready: Boolean = true
)

// Reservations landing page: today's numbers, who arrives next, status split and walk-in space.
@Composable
internal fun ReservationOverviewScreen(
    arrivals: List<Reservation>,
    arrivalsPaging: ListPaging,
    date: LocalDate,
    event: com.saporini.mobile_desktop.pos.reservations.domain.model.RestaurantEvent? = null,
    onDateChange: (LocalDate) -> Unit,
    floors: List<String>,
    floor: String?,
    onFloorChange: (String) -> Unit,
    selectedArea: String,
    areaChoices: List<String>,
    onAreaChange: (String) -> Unit,
    selectedStatus: String,
    onStatusChange: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    now: Instant,
    reservations: List<Reservation>,
    listed: List<Reservation>,
    summary: ReservationSummary?,
    capacity: ReservationCapacity?,
    loading: Boolean,
    onOpenReservation: (String) -> Unit,
    dayPaging: ListPaging,
    onOpenCalendar: () -> Unit,
    firstLoad: Boolean = false,
    modifier: Modifier = Modifier
) {
    val zone = RestaurantTime.zone
    val isToday = serviceDateOf(now, zone) == date
    val isPast = date < serviceDateOf(now, zone)
    var datePickerOpen by remember { mutableStateOf(false) }
    val filters: @Composable () -> Unit = {
            Box {
                HeaderButton(
                    text = date.formLabel(),
                    icon = Icons.Outlined.CalendarToday,
                    onClick = { datePickerOpen = true },
                    modifier = Modifier.width(190.dp)
                )
                DropdownMenu(
                    expanded = datePickerOpen,
                    onDismissRequest = { datePickerOpen = false },
                    offset = DpOffset(0.dp, 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    containerColor = Color.White,
                    shadowElevation = 8.dp
                ) {
                    CompactDatePicker(selectedDate = date, onDateSelected = {
                        onDateChange(it)
                        datePickerOpen = false
                    })
                }
            }
            if (floors.size > 1) {
                val floorChoices = if (isPast) listOf("All floors") + floors else floors
                val floorLabel = if (isPast && floor == null) "All floors" else floor ?: floors.first()
                HeaderDropdown(
                    label = floorLabel,
                    icon = Icons.Outlined.Layers,
                    modifier = Modifier.width(140.dp),
                    choices = floorChoices,
                    onSelect = onFloorChange
                )
            }
            HeaderDropdown(label = selectedArea, icon = Icons.Outlined.TableRestaurant, modifier = Modifier.width(146.dp), choices = areaChoices, onSelect = onAreaChange)
            // No status dropdown here: the By status box filters by status (and "No table").
    }
    val search: @Composable (Dp) -> Unit = { width ->
        SearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            modifier = Modifier.width(width),
            placeholder = "Search guest, code, table",
            height = ToolbarHeight
        )
    }
    val calendarButton: @Composable () -> Unit = {
        Surface(onClick = onOpenCalendar, modifier = Modifier.height(ToolbarHeight), shape = RoundedCornerShape(8.dp), color = FormGreen) {
            Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp), tint = Color.White)
                Text("Calendar", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
            }
        }
    }
    val title: @Composable () -> Unit = {
        Text("Overview", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = FormInk)
        if (loading) CircularProgressIndicator(Modifier.padding(start = 6.dp).size(16.dp), color = FormGreen, strokeWidth = 2.dp)
    }
    val listFilter = ListFilter(
        status = selectedStatus.takeIf { it != "All statuses" },
        area = selectedArea.takeIf { it != "All areas" },
        query = searchQuery.trim(),
        floor = floor.takeIf { floors.size > 1 }
    )
    BoxWithConstraints(modifier.background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        if (size.isDesktop) {
            Column(
                Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    title()
                    Spacer(Modifier.weight(1f))
                    filters()
                    search(240.dp)
                    calendarButton()
                }
                if (firstLoad) OverviewSkeleton(size, Modifier.weight(1f))
                else OverviewContent(date, now, isToday, reservations, listed, listFilter, onStatusChange, onAreaChange, arrivals, arrivalsPaging, dayPaging, summary, capacity, onOpenReservation, size, isPast && floor == null, Modifier.weight(1f), event)
            }
        } else {
            // Tablet and phone: one scrolling page; the filters scroll sideways under the title.
            val side = if (size.isPhone) 16.dp else 20.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = side, end = side, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    title()
                    Spacer(Modifier.weight(1f))
                    calendarButton()
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    search(if (size.isPhone) 200.dp else 240.dp)
                    filters()
                }
                if (firstLoad) OverviewSkeleton(size, Modifier.fillMaxWidth())
                else OverviewContent(date, now, isToday, reservations, listed, listFilter, onStatusChange, onAreaChange, arrivals, arrivalsPaging, dayPaging, summary, capacity, onOpenReservation, size, isPast && floor == null, Modifier.fillMaxWidth(), event)
            }
        }
    }
}

@Composable
private fun OverviewContent(
    date: LocalDate,
    now: Instant,
    isToday: Boolean,
    reservations: List<Reservation>,
    listed: List<Reservation>,
    listFilter: ListFilter,
    onStatusChange: (String) -> Unit,
    onAreaChange: (String) -> Unit,
    arrivals: List<Reservation>,
    arrivalsPaging: ListPaging,
    dayPaging: ListPaging,
    summary: ReservationSummary?,
    capacity: ReservationCapacity?,
    onOpenReservation: (String) -> Unit,
    size: ScreenSize,
    showFloorOnPastRows: Boolean,
    modifier: Modifier,
    event: com.saporini.mobile_desktop.pos.reservations.domain.model.RestaurantEvent? = null
) {
    val active = reservations.filter { it.status in PresentStatuses }
    val isPast = date < serviceDateOf(now, RestaurantTime.zone)
    val upcoming = reservations
        .filter { it.status == ReservationStatus.PENDING || it.status == ReservationStatus.CONFIRMED }
        .filter { reservation ->
            val start = runCatching { Instant.parse(reservation.reservationStart) }.getOrNull() ?: return@filter false
            !isToday || (start >= now - 0.25.hours && start <= now + 2.hours)
        }
        .sortedBy { it.reservationStart }
    // The cards count the whole day on this floor from the server summary; the loaded list (which may be only its
    // first pages) is just the fallback until the summary arrives.
    val unassigned = summary?.unassignedCount ?: reservations.count { it.status in ActiveStatuses && it.tableAssignments.isEmpty() }
    val cancelled = summary?.cancelledCount ?: reservations.count { it.status == ReservationStatus.CANCELLED }
    val noShows = summary?.noShowCount ?: reservations.count { it.status == ReservationStatus.NO_SHOW }

    // Left = still to come or at the table now; to arrive = not checked in yet.
    val presentCount = summary?.let { it.pendingCount + it.confirmedCount + it.checkedInCount + it.seatedCount + it.completedCount } ?: active.size
    val done = summary?.completedCount ?: active.count { it.status == ReservationStatus.COMPLETED }
    val leftCount = presentCount - done
    val totalGuests = summary?.presentGuests ?: active.sumOf { it.partySize }
    val guestsToArrive = summary?.guestsToArrive
        ?: active.filter { it.status == ReservationStatus.PENDING || it.status == ReservationStatus.CONFIRMED }.sumOf { it.partySize }
    val soonCount = summary?.arrivingSoonCount ?: upcoming.size
    val soonGuests = summary?.arrivingSoonGuests ?: upcoming.sumOf { it.partySize }
    val totalBookings = summary?.totalReservations ?: reservations.size
    val bookedGuests = summary?.totalGuests ?: reservations.sumOf { it.partySize }
    val arrivedGuests = (totalGuests - guestsToArrive).coerceAtLeast(0)
    val openBookings = summary?.let { it.pendingCount + it.confirmedCount + it.checkedInCount + it.seatedCount }
        ?: reservations.count { it.status in setOf(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN, ReservationStatus.SEATED) }
    val dayDataComplete = dayPaging.ready && !dayPaging.hasMore && !dayPaging.loading && !dayPaging.failed
    val onlineBookings = reservations.count {
        it.source == ReservationSource.WEB || it.source == ReservationSource.MOBILE || it.source == ReservationSource.THIRD_PARTY
    }
    var selectedHour by remember(date) { mutableStateOf<Int?>(null) }
    // "Ordered online" in By status: only bookings made online (website, app or a partner); combines with the rest.
    var onlineOnly by remember(date) { mutableStateOf(false) }
    // Future day's "To do" box: big parties and bookings with special requests or notes.
    var bigOnly by remember(date) { mutableStateOf(false) }
    var requestsOnly by remember(date) { mutableStateOf(false) }
    // Confirmed bookings whose guests haven't confirmed attendance yet, and visits staff must look at.
    var notConfirmedOnly by remember(date) { mutableStateOf(false) }
    var reviewOnly by remember(date) { mutableStateOf(false) }
    val policy = LocalReservationPolicy.current
    val bigFrom = policy.approvalGroupSize
    val isNotConfirmed = { reservation: Reservation -> reservation.status == ReservationStatus.CONFIRMED && reservation.attendance != "CONFIRMED" }
    val reservationsByHour = reservations.mapNotNull { reservation ->
        val hour = runCatching { Instant.parse(reservation.reservationStart).toLocalDateTime(RestaurantTime.zone).hour }.getOrNull()
            ?: return@mapNotNull null
        hour to reservation
    }.groupBy({ it.first }, { it.second })
        .toSortedMap()
    val lastChartHour = (reservationsByHour.keys.maxOrNull() ?: 8).coerceAtLeast(8)
    val chartHours = (8..lastChartHour).associateWith { reservationsByHour[it].orEmpty() }
    val peakHour = reservations.asSequence().mapNotNull { reservation ->
        runCatching { Instant.parse(reservation.reservationStart).toLocalDateTime(RestaurantTime.zone).hour }.getOrNull()
    }.groupingBy { it }.eachCount().maxByOrNull { it.value }
    // A day still to come: what to get ready for. Its list shows pending and confirmed bookings, grouped by hour.
    val isFuture = !isToday && !isPast
    val bookedAhead = reservations.distinctBy { it.id }
        .filter { it.status == ReservationStatus.PENDING || it.status == ReservationStatus.CONFIRMED }
    val pendingAhead = bookedAhead.count { it.status == ReservationStatus.PENDING }
    val guestsAhead = bookedAhead.sumOf { it.partySize }
    val busiestAhead = bookedAhead.groupBy { reservation ->
        runCatching { Instant.parse(reservation.reservationStart).toLocalDateTime(RestaurantTime.zone).hour }.getOrNull()
    }
        .filterKeys { it != null }
        .maxByOrNull { (_, bookings) -> bookings.size }
    // Big groups need approval and a call the day before (from the Admin Hub approval size, 7 by default).
    val isBigParty = { reservation: Reservation -> reservation.partySize >= bigFrom }
    val hasRequests = { reservation: Reservation ->
        !reservation.specialRequests.isNullOrBlank() || !reservation.internalNotes.isNullOrBlank() || reservation.occasionName != null
    }
    val futureCard0: @Composable (Modifier) -> Unit = { cardModifier ->
        SummaryCard(
            Res.drawable.overview_reservations, "Bookings", if (dayDataComplete) "${bookedAhead.size}" else "–",
            if (!dayDataComplete) "Loading day bookings"
            else listOf("${bookedAhead.size - pendingAhead} confirmed", "$pendingAhead pending", "$cancelled cancelled").joinToString(" · "),
            FormGreen, cardModifier,
            suffix = if (dayDataComplete) "booked" else null
        )
    }
    val futureCard1: @Composable (Modifier) -> Unit = { cardModifier ->
        SummaryCard(
            Res.drawable.overview_guests, "Guests expected", if (dayDataComplete) "$guestsAhead" else "–",
            when {
                !dayDataComplete -> "Loading day bookings"
                bookedAhead.isEmpty() -> "No bookings yet"
                else -> "Biggest booking: ${bookedAhead.maxOf { it.partySize }} guests"
            },
            Color(0xFF24748A), cardModifier
        )
    }
    val futureCard2: @Composable (Modifier) -> Unit = { cardModifier ->
        SummaryCard(
            Res.drawable.overview_next_hours, "Busiest time",
            if (!dayDataComplete) "–" else busiestAhead?.key?.let { "${it.toString().padStart(2, '0')}:00" } ?: "–",
            when {
                !dayDataComplete -> "Loading day bookings"
                busiestAhead == null -> "No bookings yet"
                else -> "${busiestAhead.value.size} ${if (busiestAhead.value.size == 1) "booking" else "bookings"} · " +
                    "${busiestAhead.value.sumOf { it.partySize }} guests"
            },
            Color(0xFFC8790B), cardModifier,
            imageScale = 1.3f
        )
    }
    val card0: @Composable (Modifier) -> Unit = { cardModifier ->
        SummaryCard(
            Res.drawable.overview_reservations, "Reservations", if (isPast) "$totalBookings" else "$leftCount",
            if (isPast) listOf("$cancelled cancelled", "$noShows no-show", "$openBookings open").joinToString(" · ")
            else listOf("$done/$presentCount done", "$cancelled cancelled", "$noShows no show").joinToString(" · "),
            FormGreen, cardModifier,
            suffix = if (isPast) "$done completed" else null,
            progress = if (isPast) if (totalBookings == 0) null else done.toFloat() / totalBookings
            else if (presentCount == 0) null else done.toFloat() / presentCount
        )
    }
    val card1: @Composable (Modifier) -> Unit = { cardModifier ->
        SummaryCard(
            Res.drawable.overview_guests, "Guests", if (isPast) "$arrivedGuests" else "$guestsToArrive",
            if (isPast) "$arrivedGuests of $bookedGuests guests arrived" else "${totalGuests - guestsToArrive}/$totalGuests arrived",
            Color(0xFF24748A), cardModifier,
            progress = if (isPast) if (bookedGuests == 0) null else arrivedGuests.toFloat() / bookedGuests
            else if (totalGuests == 0) null else (totalGuests - guestsToArrive).toFloat() / totalGuests
        )
    }
    val card2: @Composable (Modifier) -> Unit = { cardModifier ->
        SummaryCard(
            if (isPast) Res.drawable.overview_reservations else Res.drawable.overview_next_hours,
            if (isPast) "Online bookings" else "Next 2 hours",
            if (isPast) if (dayDataComplete) "$onlineBookings" else "–" else if (isToday) "$soonCount" else "–",
            if (isPast) {
                if (!dayDataComplete) "Loading day bookings"
                else "$onlineBookings ordered online"
            } else if (isToday) "$soonGuests guests arriving" else "Shown for today only",
            Color(0xFFC8790B), cardModifier,
            suffix = if (isPast) null else if (isToday) (if (soonCount == 1) "reservation coming" else "reservations coming") else null,
            imageScale = if (isPast) 1f else 1.3f
        )
    }
    val card3: @Composable (Modifier) -> Unit = { cardModifier ->
        SummaryCard(
            if (isPast) Res.drawable.overview_next_hours else Res.drawable.overview_no_table,
            if (isPast) "Peak time" else "Without a table",
            if (isPast) {
                if (!dayDataComplete) "–" else peakHour?.key?.let { "${it.toString().padStart(2, '0')}:00" } ?: "–"
            } else "$unassigned",
            if (isPast) {
                if (!dayDataComplete) "Loading day bookings"
                else peakHour?.let { "${it.value} reservations" } ?: "No reservations"
            } else if (unassigned == 0) "Every booking has a table" else "Need a table",
            if (isPast) Color(0xFFC8790B) else if (unassigned > 0) FormDanger else Color(0xFF8A8D88), cardModifier,
            imageScale = if (isPast) 1.3f else 1f
        )
    }
    // Today without filters the list is the Arriving feed (the next 2 hours, then the rest of today), loaded from the
    // server a page at a time as you scroll. A status, area or search filter lists every match of the day instead;
    // another day lists its bookings.
    val filtering = listFilter.active || selectedHour != null || onlineOnly || bigOnly || requestsOnly || notConfirmedOnly || reviewOnly
    val feedView = isToday && !filtering
    val soonFrom = now - 0.25.hours
    val soonUntil = now + 2.hours
    val startOf = { reservation: Reservation -> runCatching { Instant.parse(reservation.reservationStart) }.getOrNull() }
    // Earlier = done with arriving: checked in or further along, a no-show, or more than 15 minutes past its time
    // (cancelled bookings only show under their filter). They sit above the rest, faded, and the list opens just
    // below them.
    val isPassed = { reservation: Reservation ->
        reservation.status in PassedStatuses || startOf(reservation)?.let { it < soonFrom } == true
    }
    // Each reservation shows once: every row needs a unique key or the list crashes, and a booking can briefly be
    // listed twice while the lists refresh one after the other (e.g. right after one is added).
    val dayRows = (when {
        listFilter.active -> listed
        selectedHour != null || onlineOnly || notConfirmedOnly || reviewOnly -> reservations
        // A day ahead lists what's still expected: pending and confirmed.
        isFuture -> reservations.filter { it.status == ReservationStatus.PENDING || it.status == ReservationStatus.CONFIRMED }
        else -> reservations.filter { it.status != ReservationStatus.CANCELLED }
    }).asSequence()
        .filter { reservation ->
            selectedHour == null || startOf(reservation)?.toLocalDateTime(RestaurantTime.zone)?.hour == selectedHour
        }
        .filter { reservation -> !onlineOnly || reservation.source in OnlineSources }
        .filter { reservation -> !bigOnly || isBigParty(reservation) }
        .filter { reservation -> !requestsOnly || hasRequests(reservation) }
        .filter { reservation -> !notConfirmedOnly || isNotConfirmed(reservation) }
        .filter { reservation -> !reviewOnly || reservation.needsReview }
        .distinctBy { it.id }
        .sortedBy { it.reservationStart }
        .toList()
    // Previous / Arriving / Later are today's sections. Another day is one plain list: all faded once it's over.
    val pastDay = date < serviceDateOf(now, RestaurantTime.zone)
    val earlier = if (isToday) dayRows.filter(isPassed) else emptyList()
    val earlierIds = earlier.mapTo(HashSet()) { it.id }
    // Inside Previous: finished ones first (completed, no-show), then a "Still active" line and the ones still
    // going (checked in, seated, or late but not closed yet).
    val finishedEarlier = earlier.filter { it.status in FinishedStatuses }
    val activeEarlier = earlier.filterNot { it.status in FinishedStatuses }
    val previousSplit = finishedEarlier.isNotEmpty() && activeEarlier.isNotEmpty()
    // Rows in the Previous section when open: its reservations, the "Still active" line, and the "above" line.
    val previousRowCount = earlier.size + (if (previousSplit) 1 else 0) + 1
    // The feed and the day list refresh one after the other, so a booking can briefly be in both: it shows once.
    val feed = arrivals.distinctBy { it.id }
    val rows = when {
        feedView -> feed.filter { reservation ->
            reservation.id !in earlierIds && startOf(reservation)?.let { it >= soonFrom && it <= soonUntil } == true
        }
        isToday -> dayRows.filterNot(isPassed)
        else -> dayRows
    }
    // Later = the rest of today only. Tomorrow gets its own section in the last two hours of the day (from 22:00 on);
    // nothing further ahead is listed here.
    val tomorrow = date.plus(DatePeriod(days = 1))
    val showTomorrow = feedView && now >= LocalDateTime(date, LocalTime(TOMORROW_FROM_HOUR, 0)).toInstant(RestaurantTime.zone)
    val laterFeed = if (!feedView) emptyList() else feed.filter { reservation ->
        reservation.id !in earlierIds && startOf(reservation)?.let { it > soonUntil } == true
    }
    val laterBookings = laterFeed.filter { it.reservationStart.localDate() == date }
    val tomorrowBookings = if (showTomorrow) laterFeed.filter { it.reservationStart.localDate() == tomorrow } else emptyList()
    // The feed runs on into the following days: stop paging once it has gone past the last day shown.
    val lastDayShown = if (showTomorrow) tomorrow else date
    val feedPastShown = arrivals.lastOrNull()?.reservationStart?.localDate()?.let { it > lastDayShown } == true
    val paging = if (feedView) arrivalsPaging.copy(hasMore = arrivalsPaging.hasMore && !feedPastShown) else dayPaging
    val stillToComeToday = laterBookings.isNotEmpty()
    val empty = when {
        rows.isNotEmpty() -> null
        // Every match (or every booking of another day) is an earlier one: they're the list, no message needed.
        (filtering || !isToday) && earlier.isNotEmpty() -> null
        feedView && arrivals.isEmpty() && arrivalsPaging.failed ->
            EmptyInfo(Icons.Outlined.Schedule, "Couldn't load the arrivals", "Check the connection, then try again below.")
        filtering -> EmptyInfo(
            Icons.Outlined.SearchOff,
            "Nothing found",
            if (selectedHour != null) "No reservations at ${selectedHour.toString().padStart(2, '0')}:00."
            else if (onlineOnly && !listFilter.active) "No reservations ordered online on ${date.fullDay()}."
            else if (bigOnly && !listFilter.active) "No groups of $bigFrom or more on ${date.fullDay()}."
            else if (notConfirmedOnly && !listFilter.active) "Every guest has confirmed attendance."
            else if (reviewOnly && !listFilter.active) "No visits need a review."
            else if (requestsOnly && !listFilter.active) "No special requests or notes on ${date.fullDay()}."
            else listFilter.emptyMessage(date)
        )
        stillToComeToday -> EmptyInfo(Icons.Outlined.Schedule, "No more reservations in the next 2 hours", "The next guests arrive later today.", Res.drawable.overview_no_more_reservations)
        isToday -> EmptyInfo(Icons.Outlined.EventBusy, "No more reservations today", "New bookings for today will show up here.", Res.drawable.overview_no_more_reservations)
        else -> EmptyInfo(Icons.Outlined.EventBusy, "No reservations this day", "Nothing booked on ${date.fullDay()}.")
    }
    // Waiting for the first page, or a filter still reading the rest of the day: a spinner, not "nothing found".
    val stillLoading = (feedView && !arrivalsPaging.ready) ||
        (filtering && rows.isEmpty() && dayPaging.hasMore && !dayPaging.failed)
    // The list is split into sections, each with its own foldable header: Previous (faded, on top), then Arriving
    // (next 2 hours) today or Upcoming on other views, then Later with one group per day.
    var previousOpen by remember(date) { mutableStateOf(true) }
    var arrivingOpen by remember(date) { mutableStateOf(true) }
    // Arriving, Later and Tomorrow open one at a time when clicked; scrolling down into a closed one opens it too.
    var laterOpen by remember(date) { mutableStateOf(false) }
    var tomorrowOpen by remember(date) { mutableStateOf(false) }
    // Views without a Previous section keep the plain list they had, no header over it.
    val restHeader = when {
        feedView -> "Arriving (next 2 hours)"
        earlier.isNotEmpty() -> "Upcoming"
        else -> null
    }
    val showRows = arrivingOpen || restHeader == null
    val rowInset = if (restHeader != null) SectionRowInset else 0.dp

    // The earlier rows and the filters need the whole day, so its remaining pages load straight away (one after
    // another; a day is a few hundred bookings at most, and only the rows on screen are drawn).
    LaunchedEffect(dayPaging.hasMore, dayPaging.loading, dayPaging.failed) {
        if (dayPaging.hasMore && !dayPaging.loading && !dayPaging.failed) dayPaging.onLoadMore()
    }
    // Each view keeps its own scroll position; a refresh swaps rows in place, so the position stays.
    val listState = remember(feedView, listFilter, selectedHour, onlineOnly, bigOnly, requestsOnly, notConfirmedOnly, reviewOnly, date) { LazyListState() }
    val listScope = rememberCoroutineScope()
    // Set once the user scrolls or clicks a section; until then the list keeps Arriving in view as data loads.
    val userMoved = remember(listState) { mutableStateOf(false) }
    // Where each section header sits: Previous's header, rows and "above" line come first, and so on down.
    val restHeaderIndex = if (earlier.isEmpty()) 0 else 1 + if (previousOpen) previousRowCount else 0
    fun laterIndexFor(arrivingIsOpen: Boolean) = restHeaderIndex + (if (restHeader != null) 1 else 0) +
        (if (arrivingIsOpen || restHeader == null) rows.size + (if (empty != null) 1 else 0) else 0)
    fun tomorrowIndexFor(arrivingIsOpen: Boolean, laterIsOpen: Boolean) = laterIndexFor(arrivingIsOpen) +
        if (laterBookings.isEmpty()) 0 else 1 + if (laterIsOpen) laterBookings.size else 0
    val laterHeaderIndex = laterIndexFor(arrivingOpen)
    val tomorrowHeaderIndex = tomorrowIndexFor(arrivingOpen, laterOpen)
    // Rows animate only just after the user folds or opens a section. Refreshes and the clock (a booking moving
    // into Previous) update the list in place, so nothing plays by itself or twice.
    var animateRows by remember { mutableStateOf(false) }
    var motion by remember { mutableIntStateOf(0) }
    LaunchedEffect(motion) {
        if (motion == 0) return@LaunchedEffect
        delay(700)
        animateRows = false
    }
    fun startMotion() {
        userMoved.value = true
        animateRows = true
        motion++
    }
    // Folding a pinned header keeps it at the top. Otherwise the list would hold on to a row that just disappeared
    // and land further down.
    fun fold(headerIndex: Int, toggle: () -> Unit) {
        val pinned = headerIndex < listState.firstVisibleItemIndex
        startMotion()
        toggle()
        if (pinned) listScope.launch { listState.scrollToItem(headerIndex) }
    }
    // Height of a pinned section header (they're all the same), so a section can be brought up to sit right
    // under the header pinned above it.
    var headerPx by remember { mutableIntStateOf(0) }
    // Open on the next guests with Previous pinned at the top: the "N previous reservations above" line and the
    // Arriving header sit right under it, and the previous rows come out when scrolling up. Once per view, and
    // never after the user has scrolled.
    // The day's bookings arrive page by page and each page adds rows to Previous, which would push Arriving down.
    // So the view is set again whenever Previous grows, until the user scrolls or clicks a section themselves.
    LaunchedEffect(listState, previousRowCount, earlier.isNotEmpty(), stillLoading, previousOpen) {
        if (userMoved.value || earlier.isEmpty() || stillLoading || !previousOpen) return@LaunchedEffect
        var previousHeader = headerPx
        if (previousHeader == 0) {
            listState.scrollToItem(0)
            previousHeader = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size ?: 0
        }
        if (userMoved.value) return@LaunchedEffect
        listState.scrollToItem(previousRowCount, -(previousHeader + listState.layoutInfo.mainAxisItemSpacing))
    }
    // Glides a section header to the top; with pinnedAbove it stops just under the section before it, whose
    // header stays pinned above (Previous over Arriving, Arriving over Later).
    fun bringToTop(headerIndex: Int, pinnedAbove: Boolean) {
        listScope.launch {
            val above = if (pinnedAbove) headerPx + listState.layoutInfo.mainAxisItemSpacing else 0
            listState.animateScrollToItem(headerIndex, -above)
        }
    }
    // Previous opens at its start, which is its bottom: it reads upward from the latest to the earliest. So the box
    // fills with its latest reservations, then the "N previous reservations above" line, with the next section's
    // header (Arriving) sitting at the bottom edge; the earlier ones are further up. Closing it keeps its header
    // where it is.
    fun toggleSection(headerIndex: Int, open: Boolean, pinnedAbove: Boolean, setOpen: (Boolean) -> Unit) {
        if (open) {
            fold(headerIndex) { setOpen(false) }
        } else {
            startMotion()
            setOpen(true)
            listScope.launch {
                // Wait until its rows are in the list.
                withFrameNanos { }
                withFrameNanos { }
                val info = listState.layoutInfo
                val viewport = info.viewportEndOffset - info.viewportStartOffset
                val hint = headerIndex + previousRowCount
                if (restHeader != null) {
                    // The next header (right after the "above" line) goes to the bottom edge of the box.
                    listState.scrollToItem(hint + 1, -(viewport - headerPx).coerceAtLeast(0))
                } else {
                    listState.scrollToItem(hint, -(headerPx + info.mainAxisItemSpacing))
                }
            }
        }
    }
    // Opens one of Arriving / Later / Tomorrow and closes the others, in two steps you can follow:
    // 1. the open section folds up (its rows fade out) while the header at the top stays put;
    // 2. the target's header settles in place, just under the header above it, and its rows fade in below it.
    fun focusSection(target: ListSection) {
        // Header positions at a given set of open sections (Previous doesn't change here).
        fun headerIndexOf(section: ListSection, arrivingIsOpen: Boolean, laterIsOpen: Boolean) = when (section) {
            ListSection.ARRIVING -> restHeaderIndex
            ListSection.LATER -> laterIndexFor(arrivingIsOpen)
            ListSection.TOMORROW -> tomorrowIndexFor(arrivingIsOpen, laterIsOpen)
        }
        val pinnedAbove = when (target) {
            ListSection.ARRIVING -> earlier.isNotEmpty()
            ListSection.LATER -> restHeader != null
            ListSection.TOMORROW -> restHeader != null || laterBookings.isNotEmpty()
        }
        // The section header at the top of the box right now, and whether it's pinned over its own rows.
        val first = listState.firstVisibleItemIndex
        val topHeader = listOfNotNull(
            0.takeIf { earlier.isNotEmpty() },
            restHeaderIndex.takeIf { restHeader != null },
            laterHeaderIndex.takeIf { laterBookings.isNotEmpty() },
            tomorrowHeaderIndex.takeIf { tomorrowBookings.isNotEmpty() }
        ).lastOrNull { it <= first }
        // Only when that header's own section is the one folding does it need holding in place (its rows vanish).
        val topFolds = topHeader != null && topHeader < first && (
            (topHeader == restHeaderIndex && restHeader != null && arrivingOpen && target != ListSection.ARRIVING) ||
                (topHeader == laterHeaderIndex && laterBookings.isNotEmpty() && laterOpen && target != ListSection.LATER) ||
                (topHeader == tomorrowHeaderIndex && tomorrowBookings.isNotEmpty() && tomorrowOpen && target != ListSection.TOMORROW)
            )
        // Step 1: fold everything else. The header at the top keeps its place while the rows go.
        startMotion()
        val arrivingStays = arrivingOpen && target == ListSection.ARRIVING
        val laterStays = laterOpen && target == ListSection.LATER
        arrivingOpen = arrivingStays
        laterOpen = laterStays
        tomorrowOpen = tomorrowOpen && target == ListSection.TOMORROW
        listScope.launch {
            if (topFolds && topHeader != null) {
                // Same position after the fold: headers above it don't move when the sections below it close.
                val topIndex = when (topHeader) {
                    laterHeaderIndex -> laterIndexFor(arrivingStays)
                    tomorrowHeaderIndex -> tomorrowIndexFor(arrivingStays, laterStays)
                    else -> topHeader
                }
                listState.scrollToItem(topIndex)
            }
            delay(FOLD_STEP_MILLIS)
            // Step 2: bring the target's header to its spot, then open it so its rows fade in underneath.
            val index = headerIndexOf(target, arrivingStays, laterStays)
            val above = if (pinnedAbove) headerPx + listState.layoutInfo.mainAxisItemSpacing else 0
            listState.animateScrollToItem(index, -above)
            startMotion()
            when (target) {
                ListSection.ARRIVING -> arrivingOpen = true
                ListSection.LATER -> laterOpen = true
                ListSection.TOMORROW -> tomorrowOpen = true
            }
        }
    }
    // Clicking a closed section opens it (closing the others) and brings it up; clicking an open one just closes
    // it, and its header stays right where it is. All of them can be closed.
    fun clickSection(section: ListSection, open: Boolean, headerIndex: Int, setOpen: (Boolean) -> Unit) {
        when {
            open -> fold(headerIndex) { setOpen(false) }
            feedView -> focusSection(section)
            else -> { setOpen(true); bringToTop(headerIndex, pinnedAbove = earlier.isNotEmpty()) }
        }
    }
    // With the last section closed there's nothing under it, and the list would pull everything down to fill the
    // box. Room at the end (one box height, less a header) lets any header stay at the top instead.
    var listHeightPx by remember { mutableIntStateOf(0) }
    val lastSectionOpen = when {
        tomorrowBookings.isNotEmpty() -> tomorrowOpen
        laterBookings.isNotEmpty() -> laterOpen
        restHeader != null -> arrivingOpen
        else -> true
    }
    val density = LocalDensity.current
    val endSpace = if (lastSectionOpen || listHeightPx == 0) 0.dp
    else with(density) { (listHeightPx - headerPx - 8.dp.roundToPx()).coerceAtLeast(0).toDp() }
    // Scrolling down (by the user) into a closed section's header opens it, so the list just carries on into it.
    // Only a header coming up from the lower half counts: a closed one sitting at the top (e.g. Arriving right
    // after Later was clicked open) stays closed.
    val openOnScroll = rememberUpdatedState {
        val info = listState.layoutInfo
        fun inView(index: Int) = info.visibleItemsInfo.any {
            it.index == index && it.offset >= info.viewportEndOffset / 2 && it.offset + it.size <= info.viewportEndOffset
        }
        when {
            restHeader != null && !arrivingOpen && inView(restHeaderIndex) -> { startMotion(); arrivingOpen = true }
            laterBookings.isNotEmpty() && !laterOpen && inView(laterHeaderIndex) -> { startMotion(); laterOpen = true }
            tomorrowBookings.isNotEmpty() && !tomorrowOpen && inView(tomorrowHeaderIndex) -> { startMotion(); tomorrowOpen = true }
        }
    }
    val scrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    userMoved.value = true
                    if (available.y < 0f) openOnScroll.value()
                }
                return Offset.Zero
            }
        }
    }
    // Later stays in sight while it's still below: a copy of its header sits at the bottom of the box until the
    // real one scrolls up to that spot (same place, so the hand-over doesn't show).
    // The section right after the one on screen keeps its header at the bottom of the box while its real header
    // is still below (e.g. Arriving while scrolled up into Previous, Later while in Arriving). Once the real header
    // is in view, it shows itself and the copy goes, so the copy never covers another header.
    val sectionBelow by remember(listState, restHeaderIndex, laterHeaderIndex, tomorrowHeaderIndex, restHeader, earlier.isNotEmpty(), laterBookings.isNotEmpty(), tomorrowBookings.isNotEmpty()) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf null
            val first = listState.firstVisibleItemIndex
            val headers = listOfNotNull(
                (null to 0).takeIf { earlier.isNotEmpty() },
                (ListSection.ARRIVING to restHeaderIndex).takeIf { restHeader != null },
                (ListSection.LATER to laterHeaderIndex).takeIf { laterBookings.isNotEmpty() },
                (ListSection.TOMORROW to tomorrowHeaderIndex).takeIf { tomorrowBookings.isNotEmpty() }
            )
            // The next section header after the first thing on screen.
            val (section, index) = headers.firstOrNull { (_, index) -> index > first } ?: return@derivedStateOf null
            val header = info.visibleItemsInfo.firstOrNull { it.index == index }
            val stillBelow = if (header == null) lastVisible < index else header.offset + header.size > info.viewportEndOffset
            section.takeIf { stillBelow }
        }
    }
    val laterCountText = if (paging.hasMore && tomorrowBookings.isEmpty()) "${laterBookings.size}+ reservations" else null
    // Next page as soon as the end of the list comes into view, and again while it stays in view.
    LaunchedEffect(listState, paging.hasMore, paging.loading, paging.failed, paging.ready) {
        if (!paging.hasMore || paging.loading || paging.failed || !paging.ready) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - LOAD_AHEAD_ITEMS
        }.first { it }
        paging.onLoadMore()
    }
    val arrivingList: @Composable (Modifier, Boolean) -> Unit = { listModifier, fill ->
        LazyListContainer(
            icon = if (filtering) Icons.Outlined.FilterList else Icons.Outlined.EventAvailable,
            title = when {
                filtering -> listOfNotNull(
                    listFilter.status,
                    listFilter.area?.let { if (it == "Unassigned") "No table" else it },
                    selectedHour?.let { "${it.toString().padStart(2, '0')}:00" },
                    "Ordered online".takeIf { onlineOnly },
                    "Big groups".takeIf { bigOnly },
                    "Special requests".takeIf { requestsOnly },
                    "Not confirmed".takeIf { notConfirmedOnly },
                    "Needs review".takeIf { reviewOnly }
                ).joinToString(" · ").ifEmpty { "Results" }
                feedView -> "Today"
                else -> "Reservations"
            },
            titleExtra = date.fullDay().takeIf { isPast && !listFilter.active },
            // Today's sections carry their own counts; a filter or another day counts everything listed.
            count = if (filtering || !isToday) earlier.size + rows.size else null,
            // Another day: which one, on the right.
            action = if (selectedHour != null) ({
                Text(
                    "Clear",
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable { selectedHour = null }.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen
                )
            }) else if (isToday || isPast) null else ({
                Text(
                    date.fullDay(), Modifier.padding(end = 8.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormMuted, maxLines = 1
                )
            }),
            modifier = listModifier,
            listState = listState,
            fill = fill,
            scrollConnection = scrollConnection,
            onListHeight = { listHeightPx = it },
            bottomOverlay = sectionBelow?.let { section ->
                {
                    PinnedHeader {
                        when (section) {
                            ListSection.ARRIVING -> DayGroupHeader(restHeader.orEmpty(), rows.size, arrivingOpen) {
                                focusSection(ListSection.ARRIVING)
                            }
                            ListSection.LATER -> DayGroupHeader("Later", laterBookings.size, laterOpen, countText = laterCountText) {
                                focusSection(ListSection.LATER)
                            }
                            ListSection.TOMORROW -> DayGroupHeader(
                                tomorrow.dayHeader(date), tomorrowBookings.size, tomorrowOpen,
                                countText = if (paging.hasMore) "${tomorrowBookings.size}+ reservations" else null
                            ) { focusSection(ListSection.TOMORROW) }
                        }
                    }
                }
            },
            emptyState = when {
                stillLoading -> ({ CircularProgressIndicator(Modifier.padding(24.dp).size(22.dp), color = FormGreen, strokeWidth = 2.dp) })
                // Nothing to list at all: the message sits in the middle of the box.
                empty != null && earlier.isEmpty() && laterBookings.isEmpty() && tomorrowBookings.isEmpty() && !paging.hasMore -> ({ CenteredEmpty(empty) })
                else -> null
            }
        ) {
            // Section headers stay pinned at the top while their section scrolls under them; the next section's
            // header pushes the previous one away.
            if (earlier.isNotEmpty()) {
                stickyHeader(key = "previous-header") {
                    PinnedHeader(onHeight = { headerPx = it }) {
                        DayGroupHeader("Previous", earlier.size, previousOpen, muted = true) {
                            toggleSection(0, previousOpen, pinnedAbove = false) { previousOpen = it }
                        }
                    }
                }
                if (previousOpen) {
                    items(finishedEarlier, key = { it.id }) { reservation ->
                        Box(smoothItem(animateRows).padding(horizontal = SectionRowInset)) {
                            ReservationCard(reservation, now = now.takeIf { isToday }, passed = true, fadedAlpha = FinishedAlpha) { onOpenReservation(reservation.id) }
                        }
                    }
                    if (previousSplit) item(key = "still-active-line") {
                        Box(smoothItem(animateRows).padding(horizontal = SectionRowInset)) { StillActiveLine() }
                    }
                    items(activeEarlier, key = { it.id }) { reservation ->
                        Box(smoothItem(animateRows).padding(horizontal = SectionRowInset)) {
                            ReservationCard(reservation, now = now.takeIf { isToday }, passed = true, fadedAlpha = StillActiveAlpha) { onOpenReservation(reservation.id) }
                        }
                    }
                    item(key = "previous-hint") {
                        Box(smoothItem(animateRows)) {
                            EarlierDivider(earlier.size)
                        }
                    }
                }
            }
            if (restHeader != null) stickyHeader(key = "rest-header") {
                PinnedHeader(onHeight = { headerPx = it }) {
                    DayGroupHeader(restHeader, rows.size, arrivingOpen) {
                        clickSection(ListSection.ARRIVING, arrivingOpen, restHeaderIndex) { arrivingOpen = it }
                    }
                }
            }
            if (showRows) {
                // Only later bookings to show: a compact version of the message under the header.
                if (empty != null) item(key = "empty") { Box(smoothItem(animateRows).padding(horizontal = rowInset)) { CompactEmpty(empty) } }
                if (isFuture && !filtering) {
                    // A day ahead: under a label per hour, e.g. "19:00 · 6 reservations · 14 guests".
                    rows.groupBy { startOf(it)?.toLocalDateTime(RestaurantTime.zone)?.hour ?: -1 }.forEach { (hour, bookings) ->
                        item(key = "hour-$hour") { HourGroupLabel(hour, bookings.size, bookings.sumOf { it.partySize }) }
                        items(bookings, key = { it.id }) { reservation ->
                            ReservationCard(reservation, now = null) { onOpenReservation(reservation.id) }
                        }
                    }
                } else items(rows, key = { it.id }) { reservation ->
                    Box(smoothItem(animateRows).padding(horizontal = rowInset)) {
                        ReservationCard(reservation, now = now.takeIf { isToday && !filtering }, passed = pastDay, showFloor = showFloorOnPastRows) { onOpenReservation(reservation.id) }
                    }
                }
            }
            if (laterBookings.isNotEmpty()) {
                // The rest of today. More of it may still load as the list scrolls, hence "N+" until then.
                stickyHeader(key = "later-header") {
                    PinnedHeader {
                        DayGroupHeader("Later", laterBookings.size, laterOpen, countText = laterCountText) {
                            clickSection(ListSection.LATER, laterOpen, laterHeaderIndex) { laterOpen = it }
                        }
                    }
                }
                if (laterOpen) items(laterBookings, key = { it.id }) { reservation ->
                    Box(smoothItem(animateRows).padding(horizontal = SectionRowInset)) {
                        ReservationCard(reservation, now = null) { onOpenReservation(reservation.id) }
                    }
                }
            }
            if (tomorrowBookings.isNotEmpty()) {
                stickyHeader(key = "tomorrow-header") {
                    PinnedHeader {
                        DayGroupHeader(
                            tomorrow.dayHeader(date), tomorrowBookings.size, tomorrowOpen,
                            countText = if (paging.hasMore) "${tomorrowBookings.size}+ reservations" else null
                        ) {
                            clickSection(ListSection.TOMORROW, tomorrowOpen, tomorrowHeaderIndex) { tomorrowOpen = it }
                        }
                    }
                }
                if (tomorrowOpen) items(tomorrowBookings, key = { it.id }) { reservation ->
                    Box(smoothItem(animateRows).padding(horizontal = SectionRowInset)) {
                        ReservationCard(reservation, now = null) { onOpenReservation(reservation.id) }
                    }
                }
            }
            if (paging.hasMore || paging.loading || paging.failed) item(key = "more") { Box(smoothItem(animateRows)) { LoadMoreFooter(paging) } }
            if (endSpace > 0.dp) item(key = "end-space") { Spacer(Modifier.height(endSpace)) }
        }
    }
    // Tapping a status filters the list by it (tap again to clear); "No table" can be added on top.
    val noTableOnly = listFilter.area == "Unassigned"
    val statusList: @Composable (Modifier, Boolean) -> Unit = { boxModifier, fill ->
        ListContainer(
            Icons.Outlined.DonutLarge, "By status", count = null, modifier = boxModifier, scrollable = fill,
            action = if (listFilter.status == null && !noTableOnly && !onlineOnly) null else ({
                Text(
                    "Clear",
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                        onStatusChange("All statuses")
                        if (noTableOnly) onAreaChange("All areas")
                        onlineOnly = false
                    }.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen
                )
            })
        ) {
            listOf(
                Triple("Pending", summary?.pendingCount ?: reservations.count { it.status == ReservationStatus.PENDING }, Icons.Outlined.HourglassEmpty),
                Triple("Confirmed", summary?.confirmedCount ?: reservations.count { it.status == ReservationStatus.CONFIRMED }, Icons.Outlined.EventAvailable),
                Triple("Checked in", summary?.checkedInCount ?: reservations.count { it.status == ReservationStatus.CHECKED_IN }, Icons.Outlined.HowToReg),
                Triple("Seated", summary?.seatedCount ?: reservations.count { it.status == ReservationStatus.SEATED }, Icons.Outlined.EventSeat),
                Triple("Completed", summary?.completedCount ?: reservations.count { it.status == ReservationStatus.COMPLETED }, Icons.Outlined.TaskAlt),
                Triple("Cancelled", cancelled, Icons.Outlined.Cancel),
                Triple("No show", noShows, Icons.Outlined.PersonOff)
            ).forEach { (label, count, icon) ->
                val selected = listFilter.status == label
                StatusCountRow(icon, label, count, StatusDotColors[label] ?: FormMuted, selected) {
                    onStatusChange(if (selected) "All statuses" else label)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp), color = OrderLikeBorder)
            StatusCountRow(Icons.Outlined.TableRestaurant, "No table", unassigned, FormDanger, noTableOnly) {
                onAreaChange(if (noTableOnly) "All areas" else "Unassigned")
            }
            StatusCountRow(Icons.Outlined.Public, "Ordered online", reservations.count { it.source in OnlineSources }, OnlineColor, onlineOnly) {
                onlineOnly = !onlineOnly
            }
            // Today: guests who haven't confirmed yet, and visits staff must decide on (never seated, or left open).
            if (isToday) {
                val notConfirmed = summary?.attendanceNotConfirmedCount ?: reservations.count(isNotConfirmed)
                val due = (summary?.notConfirmedDueCount ?: 0) > 0
                StatusCountRow(Icons.Outlined.PhoneInTalk, "Not confirmed", notConfirmed, if (due) ReviewColor else LateColor, notConfirmedOnly) {
                    notConfirmedOnly = !notConfirmedOnly
                }
                StatusCountRow(Icons.Outlined.ReportProblem, "Needs review", summary?.needsReviewCount ?: reservations.count { it.needsReview }, ReviewColor, reviewOnly) {
                    reviewOnly = !reviewOnly
                }
            }
        }
    }
    // A day ahead: what to sort out before it. Each row filters the list (tap again to clear) and combines with
    // the others, like By status.
    val pendingPicked = listFilter.status == "Pending"
    val todoActive = pendingPicked || noTableOnly || bigOnly || requestsOnly || notConfirmedOnly
    // The day before, from the reminder time (15:00), bookings not confirmed yet turn red.
    val reminderPassed = run {
        val local = now.toLocalDateTime(RestaurantTime.zone)
        val (hour, minute) = policy.confirmReminderTime.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 15) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        date == serviceDateOf(now, RestaurantTime.zone).plus(kotlinx.datetime.DatePeriod(days = 1)) && local.hour * 60 + local.minute >= hour * 60 + minute
    }
    val todoList: @Composable (Modifier) -> Unit = { boxModifier ->
        ListContainer(
            Icons.Outlined.TaskAlt, "To do before the day", count = null, modifier = boxModifier, scrollable = false,
            action = if (!todoActive) null else ({
                Text(
                    "Clear",
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                        if (pendingPicked) onStatusChange("All statuses")
                        if (noTableOnly) onAreaChange("All areas")
                        bigOnly = false
                        requestsOnly = false
                        notConfirmedOnly = false
                    }.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen
                )
            })
        ) {
            StatusCountRow(Icons.Outlined.HourglassEmpty, "Requests to answer", pendingAhead, StatusDotColors["Pending"] ?: FormMuted, pendingPicked) {
                onStatusChange(if (pendingPicked) "All statuses" else "Pending")
            }
            StatusCountRow(
                Icons.Outlined.PhoneInTalk, "Not confirmed yet", bookedAhead.count(isNotConfirmed),
                if (reminderPassed) ReviewColor else LateColor, notConfirmedOnly
            ) {
                notConfirmedOnly = !notConfirmedOnly
            }
            StatusCountRow(Icons.Outlined.TableRestaurant, "No table", bookedAhead.count { it.tableAssignments.isEmpty() }, FormDanger, noTableOnly) {
                onAreaChange(if (noTableOnly) "All areas" else "Unassigned")
            }
            StatusCountRow(Icons.Outlined.Groups, "Big groups ($bigFrom+) to call", bookedAhead.count(isBigParty), Color(0xFF24748A), bigOnly) {
                bigOnly = !bigOnly
            }
            StatusCountRow(Icons.Outlined.EventNote, "Special requests", bookedAhead.count(hasRequests), Color(0xFFC8790B), requestsOnly) {
                requestsOnly = !requestsOnly
            }
        }
    }
    val walkIns: @Composable (Modifier) -> Unit = { boxModifier ->
        ListContainer(Icons.Outlined.MeetingRoom, "Free for walk-ins", count = null, modifier = boxModifier, scrollable = false) {
            when {
                !isToday -> EmptyText("Shown for today only.")
                capacity == null -> EmptyText("Loading…")
                else -> {
                    Text("Next 2 hours", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
                    InfoPill(Icons.Outlined.TableRestaurant, "${capacity.availableRootTables} of ${capacity.totalRootTables} tables free")
                    InfoPill(Icons.Outlined.Groups, "${capacity.availableSeats} of ${capacity.totalSeats} seats free")
                    capacity.maxAvailableTableCapacity?.let { InfoPill(Icons.Outlined.EventSeat, "Largest free table: $it seats") }
                }
            }
            if (isToday) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = OrderLikeBorder)
                WaitlistSection(Modifier.fillMaxWidth())
            }
        }
    }
    val reservationsByTime: @Composable (Modifier, Boolean) -> Unit = { boxModifier, fill ->
        ListContainer(
            Icons.Outlined.BarChart,
            "Reservations by time",
            count = null,
            modifier = boxModifier,
            scrollable = true,
            fill = fill
        ) {
            when {
                !dayDataComplete -> EmptyText("Loading all reservations…")
                reservationsByHour.isEmpty() -> EmptyText("No reservations for this date.")
                else -> {
                    val maxCount = reservationsByHour.values.maxOf { it.size }.coerceAtLeast(1)
                    Text("Pick an hour to see its reservations", fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
                    chartHours.forEach { (hour, bookings) ->
                        val isSelected = selectedHour == hour
                        HourRow(
                            hour = hour,
                            bookings = bookings.size,
                            guests = bookings.sumOf { it.partySize },
                            share = bookings.size.toFloat() / maxCount,
                            selected = isSelected,
                            onClick = { selectedHour = if (isSelected) null else hour }
                        )
                    }
                }
            }
        }
    }

    // The restaurant's own night that day: bookings stay normal; the special menu is on.
    val eventBanner: @Composable () -> Unit = {
        event?.let { night ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7EEF3)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(night.icon, fontSize = 18.sp)
                Text(
                    listOfNotNull(
                        night.name,
                        night.menuName?.let { if (night.specialMenuOnly) "only the $it" else "$it and the usual menus" }
                    ).joinToString(" · "),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk
                )
            }
        }
    }
    if (size.isDesktop) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            eventBanner()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isFuture) {
                    futureCard0(Modifier.weight(1f)); futureCard1(Modifier.weight(1f)); futureCard2(Modifier.weight(1f)); card3(Modifier.weight(1f))
                } else {
                    card0(Modifier.weight(1f)); card1(Modifier.weight(1f)); card2(Modifier.weight(1f)); card3(Modifier.weight(1f))
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                arrivingList(Modifier.weight(1f).fillMaxHeight(), true)
                Column(Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (isFuture) {
                        todoList(Modifier.fillMaxWidth())
                        reservationsByTime(Modifier.fillMaxWidth().weight(1f), true)
                    } else if (isPast) {
                        reservationsByTime(Modifier.fillMaxWidth().weight(1f), true)
                    } else {
                        statusList(Modifier.fillMaxWidth().weight(1f), true)
                        walkIns(Modifier.fillMaxWidth())
                    }
                }
            }
        }
        return
    }
    // Tablet and phone: cards two by two, then the lists one under the other (the page scrolls).
    val gap = if (size.isPhone) 10.dp else 12.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        eventBanner()
        if (isFuture) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { futureCard0(Modifier.weight(1f)); futureCard1(Modifier.weight(1f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { futureCard2(Modifier.weight(1f)); card3(Modifier.weight(1f)) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { card0(Modifier.weight(1f)); card1(Modifier.weight(1f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { card2(Modifier.weight(1f)); card3(Modifier.weight(1f)) }
        }
        arrivingList(Modifier.fillMaxWidth().heightIn(max = if (size.isPhone) 560.dp else 640.dp), false)
        if (isFuture) {
            todoList(Modifier.fillMaxWidth())
            reservationsByTime(Modifier.fillMaxWidth().heightIn(max = if (size.isPhone) 560.dp else 640.dp), false)
        } else if (isPast) {
            reservationsByTime(Modifier.fillMaxWidth().heightIn(max = if (size.isPhone) 560.dp else 640.dp), false)
        } else if (size.isPhone) {
            statusList(Modifier.fillMaxWidth(), false)
            walkIns(Modifier.fillMaxWidth())
        } else {
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(gap)) {
                statusList(Modifier.weight(1f).fillMaxHeight(), false)
                walkIns(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

// One hour in "Reservations by time", made to look like a button: a bordered card with a chevron that tints on
// hover (hand cursor on desktop), turns green with a check when picked, and greys out when the hour is empty.
@Composable
private fun HourRow(hour: Int, bookings: Int, guests: Int, share: Float, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val empty = bookings == 0
    val muted = Color(0xFF7F847D)
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(
                when {
                    selected -> FormGreenSoft
                    empty -> Color(0xFFF3F4F2)
                    hovered -> Color(0xFFF6F9F4)
                    else -> Color.White
                }
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                when {
                    selected -> FormGreen
                    empty -> Color(0xFFE1E4DE)
                    hovered -> FormGreen.copy(alpha = 0.45f)
                    else -> Color(0xFFE3E6E1)
                },
                shape
            )
            .then(
                if (empty) Modifier
                else Modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            )
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${hour.toString().padStart(2, '0')}:00",
                fontFamily = Inter(), fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.sp, color = if (empty) muted else FormInk
            )
            Spacer(Modifier.weight(1f))
            Text(
                "$bookings ${if (bookings == 1) "booking" else "bookings"} · $guests guests",
                fontFamily = Inter(), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 11.sp, color = if (selected) FormInk else muted, maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
            when {
                selected -> Icon(Icons.Filled.CheckCircle, "Selected", Modifier.size(16.dp), tint = FormGreen)
                empty -> Spacer(Modifier.size(16.dp))
                else -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = if (hovered) FormGreen else muted)
            }
        }
        Box(Modifier.fillMaxWidth().padding(end = 4.dp).height(6.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE8ECE7))) {
            if (!empty) {
                Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(FormGreen))
            }
        }
    }
}

// Floor plan at one chosen time, with the guests booked then.
@Composable
internal fun ReservationMapContent(
    date: LocalDate,
    time: ServiceTime,
    floor: String?,
    floorOfTable: Map<String, String>,
    reservations: List<Reservation>,
    onOpenReservation: (String) -> Unit,
    modifier: Modifier
) {
    val zone = RestaurantTime.zone
    val moment = time.at(date).toInstant(zone)
    val atThatTime = reservations.filter { reservation ->
        if (reservation.status !in PresentStatuses) return@filter false
        val start = runCatching { Instant.parse(reservation.reservationStart) }.getOrNull() ?: return@filter false
        val end = runCatching { Instant.parse(reservation.reservationEnd) }.getOrNull() ?: return@filter false
        moment >= start && moment < end
    }.sortedBy { it.reservationStart }
    val onThisFloor = atThatTime.filter { reservation ->
        floor == null || reservation.tableAssignments.isEmpty() ||
            reservation.tableAssignments.any { floorOfTable[it.tableNumber] == floor }
    }
    // Coming = pending or confirmed bookings starting in the 2 hours after the chosen time.
    val comingNext = reservations.filter { reservation ->
        if (reservation.status != ReservationStatus.PENDING && reservation.status != ReservationStatus.CONFIRMED) return@filter false
        val start = runCatching { Instant.parse(reservation.reservationStart) }.getOrNull() ?: return@filter false
        start > moment && start <= moment + 2.hours &&
            (floor == null || reservation.tableAssignments.isEmpty() || reservation.tableAssignments.any { floorOfTable[it.tableNumber] == floor })
    }.sortedBy { it.reservationStart }
    var tab by remember { mutableStateOf(GuestTab.CURRENT) }
    val bookings = onThisFloor.flatMap { reservation ->
        reservation.tableAssignments.mapNotNull { assignment ->
            assignment.tableNumber?.let {
                it to (reservation.displayGuestName.substringBefore(' ').shortened(12) to "${reservation.reservationStart.clock()}–${reservation.reservationEnd.clock()}")
            }
        }
    }.toMap()

    val mapPanel: @Composable (Modifier) -> Unit = { panelModifier ->
        Panel(Icons.Outlined.Map, "Tables at ${time.label()}", panelModifier) {
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F7F5))) {
                ReservationTimeMap(
                    floorName = floor,
                    bookings = bookings,
                    onTableClick = { table ->
                        onThisFloor.firstOrNull { reservation -> reservation.tableAssignments.any { it.tableNumber == table } }
                            ?.let { onOpenReservation(it.id) }
                    },
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LegendDot(Color(0xFF147A25), "Free at ${time.label()}")
                LegendDot(Color(0xFFAA3F38), "Booked at ${time.label()}")
            }
        }
    }
    val guestPanel: @Composable (Modifier) -> Unit = { panelModifier ->
        Panel(Icons.Outlined.Groups, "Guests", panelModifier) {
            GuestTabs(tab, currentCount = onThisFloor.size, comingCount = comingNext.size) { tab = it }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    GuestTab.CURRENT -> {
                        if (onThisFloor.isEmpty()) EmptyText("No bookings at ${time.label()}.")
                        onThisFloor.forEach { reservation -> CompactReservationCard(reservation) { onOpenReservation(reservation.id) } }
                    }
                    GuestTab.COMING -> {
                        if (comingNext.isEmpty()) EmptyText("Nobody arrives in the 2 hours after ${time.label()}.")
                        comingNext.forEach { reservation ->
                            CompactReservationCard(reservation, relativeTo = moment) { onOpenReservation(reservation.id) }
                        }
                    }
                }
            }
            Text(
                when (tab) {
                    GuestTab.CURRENT -> "${onThisFloor.sumOf { it.partySize }} guests · ${bookings.size} tables in use"
                    GuestTab.COMING -> "${comingNext.sumOf { it.partySize }} guests arriving after ${time.label()}"
                },
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk
            )
        }
    }
    BoxWithConstraints(modifier) {
        if (maxWidth >= 900.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                mapPanel(Modifier.weight(1f).fillMaxHeight())
                guestPanel(Modifier.width(360.dp).fillMaxHeight())
            }
        } else {
            // Narrow: the map on top, the guest list under it, all in one scroll.
            val phone = maxWidth < 600.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                mapPanel(Modifier.fillMaxWidth().height(if (phone) 380.dp else 480.dp))
                guestPanel(Modifier.fillMaxWidth().height(420.dp))
            }
        }
    }
}

// Shown until the first load finishes: the page's cards and lists as pulsing grey shapes.
@Composable
private fun OverviewSkeleton(size: ScreenSize, modifier: Modifier) {
    val alpha = rememberSkeletonAlpha("reservations-overview")
    if (size.isDesktop) {
        Column(modifier.alpha(alpha), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(4) { SkeletonSummaryCard(Modifier.weight(1f)) } }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonList(Modifier.weight(1f).fillMaxHeight(), rows = 6, rowHeight = 72.dp)
                Column(Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SkeletonList(Modifier.fillMaxWidth().weight(1f), rows = 7, rowHeight = 42.dp)
                    SkeletonList(Modifier.fillMaxWidth(), rows = 3, rowHeight = 34.dp)
                }
            }
        }
        return
    }
    val gap = if (size.isPhone) 10.dp else 12.dp
    Column(modifier.alpha(alpha), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { SkeletonSummaryCard(Modifier.weight(1f)); SkeletonSummaryCard(Modifier.weight(1f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { SkeletonSummaryCard(Modifier.weight(1f)); SkeletonSummaryCard(Modifier.weight(1f)) }
        SkeletonList(Modifier.fillMaxWidth(), rows = 4, rowHeight = if (size.isPhone) 96.dp else 72.dp)
        SkeletonList(Modifier.fillMaxWidth(), rows = 4, rowHeight = 42.dp)
    }
}

@Composable
private fun SkeletonSummaryCard(modifier: Modifier) {
    Row(
        modifier.height(82.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
            .border(1.dp, OrderLikeBorder, RoundedCornerShape(10.dp))
    ) {
        SkeletonBox(Modifier.width(5.dp).fillMaxHeight(), shape = RoundedCornerShape(0.dp))
        Row(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(38.dp), SkeletonLight, RoundedCornerShape(10.dp))
            Spacer(Modifier.width(11.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBox(Modifier.width(70.dp).height(10.dp))
                SkeletonBox(Modifier.width(96.dp).height(18.dp))
                SkeletonBox(Modifier.width(118.dp).height(9.dp), SkeletonLight)
            }
        }
    }
}

@Composable
private fun SkeletonList(modifier: Modifier, rows: Int, rowHeight: Dp) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
            .border(1.dp, OrderLikeBorder, RoundedCornerShape(12.dp)).padding(12.dp).clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(18.dp), shape = RoundedCornerShape(5.dp))
            Spacer(Modifier.width(8.dp))
            SkeletonBox(Modifier.width(96.dp).height(14.dp))
        }
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth().height(rowHeight).clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFE8ECE6), RoundedCornerShape(10.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(Modifier.width(5.dp).fillMaxHeight(), SkeletonLight, RoundedCornerShape(0.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth(0.35f).height(12.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.55f).height(10.dp), SkeletonLight)
                }
                SkeletonBox(Modifier.padding(end = 14.dp).size(28.dp), SkeletonLight, RoundedCornerShape(14.dp))
            }
        }
    }
}

// Order-card look: white card, thin border, colored strip on the left.
@Composable
private fun SummaryCard(
    image: DrawableResource,
    title: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier,
    suffix: String? = null,
    progress: Float? = null,
    // Draws the picture larger inside the same 52dp slot, for artwork with more empty space around it.
    imageScale: Float = 1f
) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, OrderLikeBorder), shadowElevation = 1.dp) {
        BoxWithConstraints {
        // Half-width phone cards skip the icon so the numbers keep their room.
        val showIcon = maxWidth >= 200.dp
        Row(Modifier.height(82.dp)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Row(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showIcon) {
                    Image(painterResource(image), null, Modifier.size(52.dp).scale(imageScale), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(11.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormMuted, maxLines = 1)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = FormInk, maxLines = 1)
                        suffix?.let {
                            Text(" $it", Modifier.padding(bottom = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = FormMuted, maxLines = 1)
                        }
                    }
                    Text(detail, fontFamily = Inter(), fontSize = 11.sp, color = FormMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    progress?.let { fraction ->
                        Box(Modifier.padding(top = 2.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.14f))) {
                            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).clip(RoundedCornerShape(50)).background(accent))
                        }
                    }
                }
            }
        }
        }
    }
}

// Orders-list container: rounded white surface with a header and scrolling content.
@Composable
private fun ListContainer(
    icon: ImageVector,
    title: String,
    count: Int?,
    modifier: Modifier,
    scrollable: Boolean = true,
    emptyState: (@Composable () -> Unit)? = null,
    fill: Boolean = true,
    // With onToggle the header folds the list: clickable, with an arrow like the day groups.
    open: Boolean = true,
    onToggle: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, OrderLikeBorder)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListHeader(icon, title, null, count, open, onToggle, action)
            if (emptyState != null) {
                Box(if (fill) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { emptyState() }
                return@Column
            }
            if (!scrollable || !fill) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                return@Column
            }
            val scroll = rememberScrollState()
            Box(Modifier.weight(1f, fill = false)) {
                Column(Modifier.verticalScroll(scroll).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                PlatformVerticalScrollbar(state = scroll,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(3.dp)
                )
            }
        }
    }
}

// Same box for long lists: only the rows on screen are drawn.
@Composable
private fun LazyListContainer(
    icon: ImageVector,
    title: String,
    titleExtra: String? = null,
    count: Int?,
    modifier: Modifier,
    listState: LazyListState,
    emptyState: (@Composable () -> Unit)? = null,
    fill: Boolean = true,
    open: Boolean = true,
    onToggle: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    // Drawn over the bottom edge of the list, e.g. the next section's header while it's still below.
    bottomOverlay: (@Composable () -> Unit)? = null,
    // Sees the user's scrolling on the list (e.g. to open a section when scrolled into).
    scrollConnection: NestedScrollConnection? = null,
    onListHeight: ((Int) -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, OrderLikeBorder)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListHeader(icon, title, titleExtra, count, open, onToggle, action)
            if (emptyState != null) {
                Box(if (fill) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { emptyState() }
                return@Column
            }
            Box(Modifier.weight(1f, fill = false).then(if (scrollConnection != null) Modifier.nestedScroll(scrollConnection) else Modifier)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(end = 10.dp).then(if (onListHeight != null) Modifier.onSizeChanged { onListHeight(it.height) } else Modifier),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                if (bottomOverlay != null) Box(Modifier.align(Alignment.BottomStart).padding(end = 10.dp)) { bottomOverlay() }
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                    PlatformVerticalScrollbar(state = listState, modifier = Modifier.fillMaxHeight().width(3.dp))
                }
            }
        }
    }
}

// Title row of a list box: icon, title, optional action and count; with onToggle it folds the list (arrow at the end).
@Composable
private fun ListHeader(
    icon: ImageVector,
    title: String,
    titleExtra: String?,
    count: Int?,
    open: Boolean,
    onToggle: (() -> Unit)?,
    action: (@Composable () -> Unit)?
) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = FormInk)
        Spacer(Modifier.width(8.dp))
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        titleExtra?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
        count?.let {
            Text(
                "$it", Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFF2F6F2)).padding(horizontal = 9.dp, vertical = 2.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormInk
            )
        }
        if (onToggle != null) {
            Spacer(Modifier.width(6.dp))
            FoldArrow(open, Modifier.size(20.dp), FormGreen)
        }
    }
}

// A day ahead's list: the hour its bookings start, with how many and how many guests.
@Composable
private fun HourGroupLabel(hour: Int, bookings: Int, guests: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (hour < 0) "No time" else "${hour.toString().padStart(2, '0')}:00",
            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormInk
        )
        Text(
            "$bookings ${if (bookings == 1) "reservation" else "reservations"} · $guests guests",
            fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = FormMuted
        )
        HorizontalDivider(Modifier.weight(1f), color = OrderLikeBorder)
    }
}

// Inside Previous, between the finished reservations and the ones still going.
@Composable
private fun StillActiveLine() {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Still active", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = FormMuted)
        HorizontalDivider(Modifier.weight(1f), color = OrderLikeBorder)
    }
}

// End of the Previous section, where the list opens: a label saying those are above (not a button).
@Composable
private fun EarlierDivider(count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(Modifier.weight(1f), color = OrderLikeBorder)
        Surface(shape = RoundedCornerShape(50), color = Color(0xFFF3F5F2), border = BorderStroke(1.dp, OrderLikeBorder)) {
            Row(
                Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.KeyboardArrowUp, null, Modifier.size(18.dp), tint = FormMuted)
                Text(
                    if (count == 1) "1 previous reservation above" else "$count previous reservations above",
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormMuted
                )
            }
        }
        HorizontalDivider(Modifier.weight(1f), color = OrderLikeBorder)
    }
}

// End of a paged list: a pill between two hairlines. It loads by itself as it scrolls into view; the pill is for
// clicking when that didn't happen, and says so when the last page failed.
@Composable
private fun LoadMoreFooter(paging: ListPaging) {
    val tint = if (paging.failed) FormDanger else FormGreen
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(Modifier.weight(1f), color = OrderLikeBorder)
        Surface(
            onClick = paging.onLoadMore,
            enabled = !paging.loading,
            shape = RoundedCornerShape(50),
            color = Color.White,
            border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                when {
                    paging.loading -> CircularProgressIndicator(Modifier.padding(2.dp).size(14.dp), color = FormGreen, strokeWidth = 2.dp)
                    paging.failed -> Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp), tint = tint)
                    else -> Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp), tint = tint)
                }
                Text(
                    when {
                        paging.loading -> "Loading more…"
                        paging.failed -> "Couldn't load more. Try again"
                        else -> "Show more reservations"
                    },
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = tint
                )
            }
        }
        HorizontalDivider(Modifier.weight(1f), color = OrderLikeBorder)
    }
}

@Composable
private fun Panel(icon: ImageVector, title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, OrderLikeBorder)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = FormInk)
                Spacer(Modifier.width(8.dp))
                Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = FormInk)
            }
            content()
        }
    }
}

// Tables status-list look: icon square, label, count.
@Composable
private fun StatusCountRow(
    icon: ImageVector,
    label: String,
    count: Int,
    color: Color,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    // Selected: tinted in its colour with a check. Empty rows stay grey unless selected.
    val lit = count > 0 || selected
    val muted = Color(0xFF7F847D)
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> color.copy(alpha = 0.1f)
                    count > 0 -> Color.White
                    else -> Color(0xFFF3F4F2)
                }
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                when {
                    selected -> color
                    count > 0 -> Color(0xFFE8E8E4)
                    else -> Color(0xFFD8DBD5)
                },
                RoundedCornerShape(8.dp)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                .background(if (lit) color.copy(alpha = if (selected) 0.2f else 0.14f) else Color(0xFFE1E4DE)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (lit) color else muted)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label, Modifier.weight(1f), fontFamily = Inter(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp, color = if (lit) FormInk else muted
        )
        Text("$count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (lit) FormInk else muted)
        if (selected) {
            Spacer(Modifier.width(7.dp))
            Icon(Icons.Filled.CheckCircle, "Selected", Modifier.size(17.dp), tint = color)
        }
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F6F2)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = FormInk)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
    }
}

private data class EmptyInfo(val icon: ImageVector, val title: String, val hint: String, val image: DrawableResource? = null)

@Composable
private fun CenteredEmpty(info: EmptyInfo) {
    Column(
        Modifier.widthIn(max = 420.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (info.image != null) {
            Image(painterResource(info.image), null, Modifier.size(76.dp), contentScale = ContentScale.Fit)
        } else {
            Box(Modifier.size(64.dp).clip(CircleShape).background(FormGreenSoft), contentAlignment = Alignment.Center) {
                Icon(info.icon, null, Modifier.size(30.dp), tint = FormGreen)
            }
        }
        Text(info.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = FormInk, textAlign = TextAlign.Center)
        Text(info.hint, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = FormMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CompactEmpty(info: EmptyInfo) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F9F7))
            .border(1.dp, OrderLikeBorder, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(FormGreenSoft), contentAlignment = Alignment.Center) {
            Icon(info.icon, null, Modifier.size(18.dp), tint = FormGreen)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(info.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FormInk)
            Text(info.hint, fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, Modifier.padding(horizontal = 4.dp, vertical = 6.dp), fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
    }
}

// Narrow version of the reservation card for side lists: guest on top, table and status below.
@Composable
private fun CompactReservationCard(reservation: Reservation, relativeTo: Instant? = null, onClick: () -> Unit) {
    val label = when (reservation.status) {
        ReservationStatus.PENDING -> "Pending"
        ReservationStatus.CONFIRMED -> "Confirmed"
        ReservationStatus.CHECKED_IN -> "Checked in"
        ReservationStatus.SEATED -> "Seated"
        ReservationStatus.COMPLETED -> "Completed"
        ReservationStatus.CANCELLED -> "Cancelled"
        ReservationStatus.NO_SHOW -> "No show"
        ReservationStatus.EXPIRED -> "Request expired"
    }
    val statusColor = StatusDotColors[label] ?: FormMuted
    val tables = reservation.tableAssignments.mapNotNull { it.tableNumber }
    Surface(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE8ECE6)), shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().height(72.dp)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(statusColor))
            Row(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.width(62.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(reservation.reservationStart.clock(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FormInk)
                    val minutesAway = relativeTo?.let { runCatching { (Instant.parse(reservation.reservationStart) - it).inWholeMinutes }.getOrNull() }
                    Text(
                        when {
                            minutesAway == null -> "to ${reservation.reservationEnd.clock()}"
                            minutesAway < 60 -> "in $minutesAway min"
                            else -> "in ${minutesAway / 60} h ${minutesAway % 60} min"
                        },
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = FormGreen, maxLines = 1
                    )
                }
                Box(Modifier.width(1.dp).height(40.dp).background(OrderLikeBorder))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${reservation.displayGuestName} · ${reservation.partySize}",
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormInk,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (tables.isEmpty()) FormDanger.copy(alpha = 0.10f) else Color(0xFFF2F6F2))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Outlined.TableRestaurant, null, Modifier.size(13.dp), tint = if (tables.isEmpty()) FormDanger else FormInk)
                            Text(
                                if (tables.isEmpty()) "No table" else tables.joinToString(" + "),
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                                color = if (tables.isEmpty()) FormDanger else FormInk, maxLines = 1
                            )
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(22.dp)).background(statusColor.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(Modifier.size(6.dp).background(statusColor, RoundedCornerShape(3.dp)))
                            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = statusColor, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

// Order-card look for one reservation: status strip, time, guest, table pill, status badge, chevron.
@Composable
private fun ReservationCard(
    reservation: Reservation,
    now: Instant?,
    passed: Boolean = false,
    showFloor: Boolean = false,
    // How faded a passed card is: finished ones the most, still-active ones a little less.
    fadedAlpha: Float = FinishedAlpha,
    onClick: () -> Unit
) {
    val label = when (reservation.status) {
        ReservationStatus.PENDING -> "Pending"
        ReservationStatus.CONFIRMED -> "Confirmed"
        ReservationStatus.CHECKED_IN -> "Checked in"
        ReservationStatus.SEATED -> "Seated"
        ReservationStatus.COMPLETED -> "Completed"
        ReservationStatus.CANCELLED -> "Cancelled"
        ReservationStatus.NO_SHOW -> "No show"
        ReservationStatus.EXPIRED -> "Request expired"
    }
    val statusColor = StatusDotColors[label] ?: FormMuted
    val tables = reservation.tableAssignments.mapNotNull { assignment ->
        assignment.tableNumber?.let { table ->
            if (showFloor) assignment.floor?.takeIf(String::isNotBlank)?.let { "$table · $it" } ?: table else table
        }
    }
    val minutesAway = now?.let { runCatching { (Instant.parse(reservation.reservationStart) - it).inWholeMinutes }.getOrNull() }
    val stillExpected = reservation.status == ReservationStatus.PENDING || reservation.status == ReservationStatus.CONFIRMED
    // "Hold ends in 8 min", "3 of 6 arrived", "Waiting for table", "Needs review" from the booking rules.
    val flags = now?.let { bookingFlags(reservation, LocalReservationPolicy.current, it) }.orEmpty()
    val holdFlag = flags.firstOrNull { it.color == HoldEndsColor }
    val arrivedFlag = flags.firstOrNull { it.text.endsWith(" arrived") }
    val waitingForTable = flags.any { it.text == "Waiting for table" }
    val statusText = if (reservation.needsReview) "Needs review" else label
    val statusTone = if (reservation.needsReview) ReviewColor else statusColor
    val whenText = when {
        holdFlag != null -> holdFlag.text
        // Earlier and still not here: how late they are.
        passed && stillExpected && minutesAway != null && minutesAway < 0 ->
            if (-minutesAway < 60) "${-minutesAway} min late" else "${-minutesAway / 60} h ${-minutesAway % 60} min late"
        minutesAway == null || passed -> "until ${reservation.reservationEnd.clock()}"
        minutesAway <= 0 -> "now"
        minutesAway < 60 -> "in $minutesAway min"
        else -> "in ${minutesAway / 60} h ${minutesAway % 60} min"
    }
    // Earlier bookings look faded: still readable and clickable, clearly behind us.
    BoxWithConstraints(Modifier.fillMaxWidth().alpha(if (passed) fadedAlpha else 1f)) {
    if (maxWidth < 560.dp) {
        NarrowReservationCard(reservation, label, statusColor, tables, whenText, onClick)
        return@BoxWithConstraints
    }
    Surface(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE8ECE6)), shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().height(72.dp)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(statusColor))
            Row(
                Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(Modifier.width(84.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(15.dp), tint = FormInk)
                        Spacer(Modifier.width(6.dp))
                        Text(reservation.reservationStart.clock(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FormInk)
                    }
                    Text(
                        whenText,
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1,
                        color = when {
                            holdFlag != null -> Color(0xFF8A6A00)
                            passed -> FormMuted
                            else -> FormGreen
                        }
                    )
                }
                Box(Modifier.width(1.dp).height(40.dp).background(OrderLikeBorder))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        listOfNotNull(reservation.occasionIcon, reservation.displayGuestName).joinToString(" "),
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Groups, null, Modifier.size(15.dp), tint = FormMuted)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            arrivedFlag?.text ?: "${reservation.partySize} guests", fontFamily = Inter(), fontSize = 12.sp,
                            color = if (arrivedFlag != null) WaitingColor else FormMuted
                        )
                    }
                }
                val tableTone = when {
                    waitingForTable -> WaitingColor
                    tables.isEmpty() -> FormDanger
                    else -> FormInk
                }
                Row(
                    Modifier.widthIn(min = 92.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (tables.isEmpty()) tableTone.copy(alpha = 0.10f) else Color(0xFFF2F6F2))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.TableRestaurant, null, Modifier.size(17.dp), tint = tableTone)
                    Text(
                        when {
                            waitingForTable -> "Waiting for table"
                            tables.isEmpty() -> "No table"
                            else -> tables.joinToString(" + ")
                        },
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = tableTone, maxLines = 1
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(22.dp)).background(statusTone.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(8.dp).background(statusTone, RoundedCornerShape(4.dp)))
                    Text(statusText, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = statusTone)
                }
                Box(Modifier.size(32.dp).background(Color(0xFFF0F4F0), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(19.dp), tint = FormInk)
                }
            }
        }
    }
    }
}

// Same card for narrow lists: time and status on top, guest below, then guests and table.
@Composable
private fun NarrowReservationCard(
    reservation: Reservation,
    label: String,
    statusColor: Color,
    tables: List<String>,
    whenText: String,
    onClick: () -> Unit
) {
    Surface(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE8ECE6)), shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(statusColor))
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(15.dp), tint = FormInk)
                    Spacer(Modifier.width(5.dp))
                    Text(reservation.reservationStart.clock(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FormInk)
                    Spacer(Modifier.width(8.dp))
                    Text(whenText, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        Modifier.clip(RoundedCornerShape(22.dp)).background(statusColor.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(7.dp).background(statusColor, RoundedCornerShape(4.dp)))
                        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = statusColor, maxLines = 1)
                    }
                }
                Text(reservation.displayGuestName, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Groups, null, Modifier.size(15.dp), tint = FormMuted)
                        Spacer(Modifier.width(5.dp))
                        Text("${reservation.partySize} guests", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted, maxLines = 1)
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (tables.isEmpty()) FormDanger.copy(alpha = 0.10f) else Color(0xFFF2F6F2))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Outlined.TableRestaurant, null, Modifier.size(14.dp), tint = if (tables.isEmpty()) FormDanger else FormInk)
                        Text(
                            if (tables.isEmpty()) "No table" else tables.joinToString(" + "),
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                            color = if (tables.isEmpty()) FormDanger else FormInk, maxLines = 1
                        )
                    }
                }
            }
            Box(Modifier.fillMaxHeight().padding(end = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(28.dp).background(Color(0xFFF0F4F0), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = FormInk)
                }
            }
        }
    }
}


internal data class ListFilter(val status: String?, val area: String?, val query: String, val floor: String?) {
    val active: Boolean get() = status != null || area != null || query.isNotEmpty()

    // e.g. "No cancelled reservations on 1st Floor on Friday, 25 September."
    fun emptyMessage(date: LocalDate): String = buildString {
        append("No ")
        status?.let { append(if (it == "No show") "no-show " else "${it.lowercase()} ") }
        append("reservations")
        area?.let { append(if (it == "Unassigned") " without a table" else " in $it") }
        if (query.isNotEmpty()) append(" matching \"$query\"")
        floor?.let { append(" on $it") }
        append(" on ${date.fullDay()}.")
    }
}

private fun LocalDate.fullDay(): String {
    val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    return "${weekdays[dayOfWeek.ordinal]}, $day ${months[monthNumber - 1]}"
}

private fun String.localDate(): LocalDate? =
    runCatching { serviceDateOf(Instant.parse(this), RestaurantTime.zone) }.getOrNull()

// "Later today", "Tomorrow · Saturday, 26 September" or "Monday, 28 September" (year added when it differs).
private fun LocalDate.dayHeader(today: LocalDate): String {
    val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val full = "${weekdays[dayOfWeek.ordinal]}, $day ${months[monthNumber - 1]}" + if (year != today.year) " $year" else ""
    return when (today.daysUntil(this)) {
        0 -> "Later today"
        1 -> "Tomorrow · $full"
        else -> full
    }
}

// Down when open, right when folded, turning smoothly so the fold reads as a motion.
@Composable
private fun FoldArrow(open: Boolean, modifier: Modifier, tint: Color) {
    val angle by animateFloatAsState(if (open) 0f else -90f, tween(260, easing = FastOutSlowInEasing), label = "fold-arrow")
    Icon(Icons.Outlined.KeyboardArrowDown, if (open) "Collapse" else "Expand", modifier.rotate(angle), tint = tint)
}

// Rows fade in and out and glide into place when a section folds or the list refreshes (same motion as the
// calendar's rows).
private fun LazyItemScope.smoothItem(animate: Boolean): Modifier = Modifier.animateItem(
    fadeInSpec = if (animate) tween(260, delayMillis = 60) else null,
    placementSpec = if (animate) tween(380, easing = FastOutSlowInEasing) else null,
    fadeOutSpec = if (animate) tween(200) else null
)

// A pinned section header: an opaque strip, so rows scrolling underneath don't show through its rounded corners.
@Composable
private fun PinnedHeader(onHeight: ((Int) -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .then(if (onHeight != null) Modifier.onSizeChanged { onHeight(it.height) } else Modifier)
            .background(Color.White).padding(bottom = 2.dp)
    ) { content() }
}

@Composable
private fun DayGroupHeader(
    label: String,
    count: Int,
    open: Boolean,
    muted: Boolean = false,
    // Replaces "N reservations", e.g. "40+ reservations" while more are still loading.
    countText: String? = null,
    onToggle: () -> Unit
) {
    // Muted (grey) for the Previous section, green for what's still to come.
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (muted) Color(0xFFF1F2F0) else Color(0xFFF2F6F2))
            .clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FoldArrow(open, Modifier.size(18.dp), if (muted) FormMuted else FormGreen)
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (muted) FormMuted else FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            countText ?: if (count == 1) "1 reservation" else "$count reservations",
            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormMuted
        )
    }
}

private fun String.clock(): String =
    runCatching { Instant.parse(this).toLocalDateTime(RestaurantTime.zone) }.getOrNull()
        ?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" } ?: "--:--"

private fun Instant.hoursSince(day: LocalDate): Float {
    val local = toLocalDateTime(RestaurantTime.zone)
    return day.daysUntil(local.date) * 24 + local.hour + local.minute / 60f
}

private enum class GuestTab(val label: String) { CURRENT("Current"), COMING("Coming") }

@Composable
private fun GuestTabs(current: GuestTab, currentCount: Int, comingCount: Int, onSelect: (GuestTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
            .border(1.dp, OrderLikeBorder, RoundedCornerShape(8.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        GuestTab.entries.forEach { tab ->
            val selected = tab == current
            val count = if (tab == GuestTab.CURRENT) currentCount else comingCount
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                    .background(if (selected) FormGreen else Color.Transparent)
                    .clickable { onSelect(tab) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${tab.label} ($count)", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (selected) Color.White else FormInk)
            }
        }
    }
}

// Keeps a label to a fixed length so one very long name can't break a tight layout.
internal fun String.shortened(max: Int): String = if (length <= max) this else take(max - 1).trimEnd() + "…"
