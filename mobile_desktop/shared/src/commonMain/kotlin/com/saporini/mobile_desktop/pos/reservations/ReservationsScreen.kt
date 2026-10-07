package com.saporini.mobile_desktop.pos.reservations

import androidx.compose.material3.HorizontalDivider

import androidx.compose.ui.draw.alpha

import com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha

import com.saporini.mobile_desktop.core.components.SkeletonLight

import com.saporini.mobile_desktop.core.components.SkeletonBox

import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.key
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import androidx.compose.material.icons.outlined.Layers
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorPlanFloorNames
import com.saporini.mobile_desktop.pos.tables.ui.TableNotification
import com.saporini.mobile_desktop.pos.tables.ui.TableNotificationQueue
import com.saporini.mobile_desktop.pos.tables.ui.TableNotificationTone
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.isoDayNumber
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.shadow
import kotlin.time.Duration.Companion.hours
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.saporini.mobile_desktop.core.components.RightEdgeActionButton
import com.saporini.mobile_desktop.core.components.RightEdgeActionButtonHeight
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import com.saporini.mobile_desktop.pos.reservations.ui.OTHER_TABLES_GROUP
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationListFilter
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationListMode
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationsScreenModel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import kotlin.math.max
import kotlin.time.Clock

private val Olive = Color(0xFF4F7942)
private const val ALL_FLOORS_PICKED = "__all_floors__"
private val Ink = Color(0xFF242522)
private val Muted = Color(0xFF747672)
private val LightMuted = Color(0xFF959792)
private val Border = Color(0xFFE3DED8)
private val Grid = Color(0xFFE9E4DE)
private val Canvas = Color(0xFFF8F8F6)
private val RowStripe = Color(0xFFFBFBFA)
private val Warning = Color(0xFFC8790B)
private val ReadyBlue = Color(0xFF24748A)
// The day runs from 06:00 to 02:00 the next night; hours past 24 belong to the night after the selected date.
private const val START_HOUR = 6f
private const val END_HOUR = 26f
private val Hours = (6 until 26).toList()
private val NowGreen = Color(0xFF2E9E4F)
// Stronger shades of each status's calendar block color.
internal val StatusDotColors = linkedMapOf(
    "Pending" to Color(0xFFC8790B),
    "Confirmed" to Color(0xFF8DB580),
    "Checked in" to Color(0xFF24748A),
    "Seated" to Color(0xFF4F7942),
    "Completed" to Color(0xFF8A8D88),
    "Cancelled" to Color(0xFFB13A2F),
    "No show" to Color(0xFF8E5A52),
    "Request expired" to Color(0xFF9A9C97)
)
private val MinHourWidth = 150.dp
private val TimelineGutter = 24.dp
private val BlockHeight = 62.dp
private val BlockGap = 6.dp
private val RowVerticalPadding = 8.dp

@Composable
fun ReservationsScreen(
    modifier: Modifier = Modifier,
    onGoToTable: (String) -> Unit = {},
    focusReservationId: String? = null,
    onFocusHandled: () -> Unit = {}
) {
    val model = koinInject<ReservationsScreenModel>()
    // Opened from a notification: show that reservation's details.
    LaunchedEffect(focusReservationId) {
        focusReservationId?.let {
            model.selectReservation(it)
            onFocusHandled()
        }
    }
    val owner = LocalLifecycleOwner.current

    DisposableEffect(model, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) model.refresh()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) model.refresh()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            model.onDispose()
        }
    }


    // Every saved change or failure pops up in the bottom-left corner, like on Tables.
    var noticeSequence by remember { mutableIntStateOf(0) }
    var notices by remember { mutableStateOf<List<TableNotification>>(emptyList()) }
    LaunchedEffect(model) {
        model.notices.collect { notice ->
            noticeSequence += 1
            val tone = if (notice.isError) TableNotificationTone.Error else TableNotificationTone.Success
            notices = (notices + TableNotification(noticeSequence, notice.message, tone)).takeLast(3)
        }
    }

    // Pick up bookings made elsewhere while this screen is open.
    LaunchedEffect(model) {
        while (true) {
            delay(60_000)
            if (!model.state.value.isSaving) model.refresh()
        }
    }

    val policy = model.state.collectAsState().value.policy
    Box(modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalReservationPolicy provides policy) {
            ReservationsCalendarContent(model = model, onGoToTable = onGoToTable, modifier = Modifier.fillMaxSize())
        }
        TableNotificationQueue(
            notifications = notices,
            onDismiss = { id -> notices = notices.filterNot { it.id == id } },
            modifier = Modifier.align(Alignment.BottomStart).padding(28.dp)
        )
    }
}

@Composable
private fun ReservationsCalendarContent(
    model: ReservationsScreenModel,
    onGoToTable: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by model.state.collectAsState()
    // Re-picks today once the restaurant's zone arrives from the server.
    val restaurantZone = RestaurantTime.zone
    var selectedDate by remember(restaurantZone) { mutableStateOf(serviceDateOf(Clock.System.now(), restaurantZone)) }
    // The restaurant's event night on the chosen day (e.g. ❤️ Valentine's), shown above the overview.
    var dayEvent by remember { mutableStateOf<com.saporini.mobile_desktop.pos.reservations.domain.model.RestaurantEvent?>(null) }
    LaunchedEffect(model, selectedDate) { dayEvent = model.eventOn(selectedDate).getOrNull() }
    LaunchedEffect(model, selectedDate, restaurantZone) {
        val zone = RestaurantTime.zone
        model.setFilter(
            ReservationListFilter(
                mode = ReservationListMode.CALENDAR,
                from = LocalDateTime(selectedDate, LocalTime(START_HOUR.toInt(), 0)).toInstant(zone).toString(),
                to = LocalDateTime(selectedDate.plus(DatePeriod(days = 1)), LocalTime(END_HOUR.toInt() - 24, 0)).toInstant(zone).toString()
            )
        )
    }
    var pickedFloor by remember { mutableStateOf<String?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    var calendarOpen by remember { mutableStateOf(false) }
    var calendarView by remember { mutableStateOf(CalendarView.TIMELINE) }
    val clockNow by produceState(Clock.System.now()) {
        while (true) {
            delay(30_000)
            value = Clock.System.now()
        }
    }
    var mapTime by remember { mutableStateOf(ServiceTime(19 * 60)) }
    LaunchedEffect(selectedDate, restaurantZone) {
        val zone = RestaurantTime.zone
        mapTime = if (serviceDateOf(clockNow, zone) == selectedDate) {
            val minutes = (clockNow.hoursFrom(selectedDate) * 60).toInt()
            ServiceTime((minutes - minutes % 30).coerceIn(StartSlots.first().minutes, StartSlots.last().minutes))
        } else {
            ServiceTime(19 * 60)
        }
    }
    var selectedArea by remember { mutableStateOf("All areas") }
    var selectedStatus by remember { mutableStateOf("All statuses") }
    var newButtonPosition by rememberSaveable { mutableStateOf(.84f) }

    val reservations = state.visibleReservations
    val tableGroups = state.tableGroups
    val areaByTable = remember(tableGroups) {
        tableGroups.flatMap { group -> group.tables.map { it.tableNumber to group.name } }.toMap()
    }
    val realEvents = remember(reservations, areaByTable, selectedDate) {
        reservations.mapNotNull { it.toCalendarEvent(areaByTable, selectedDate) }
    }
    val events = realEvents
    val areaChoices = listOf("All areas") + tableGroups.map { it.name } + "Unassigned"
    // Historical overview can include every floor; elsewhere retain the existing first-floor default.
    val allFloorsSelected = pickedFloor == ALL_FLOORS_PICKED && selectedDate < serviceDateOf(clockNow, restaurantZone)
    val selectedFloor = if (allFloorsSelected) null else pickedFloor?.takeIf { it in state.floors } ?: state.floors.firstOrNull()
    val floorChoices = if (state.floors.size > 1) state.floors else emptyList()
    val calendarFloorChoices = if (allFloorsSelected) listOf("All floors") + floorChoices else floorChoices
    val calendarRows = remember(tableGroups, selectedArea, selectedFloor, events) {
        val unassignedCount = events.count { it.rowId == "unassigned" }
        val unassignedSection = CalendarRow.Section("Unassigned", hiddenItemLabel = "reservation")
        val unassignedRow = CalendarRow.Resource(
            "unassigned", "Unassigned",
            "$unassignedCount ${if (unassignedCount == 1) "reservation" else "reservations"}",
            Icons.Outlined.Groups, 86.dp
        )
        val groupRows = run {
            tableGroups.filter { selectedArea == "All areas" || it.name == selectedArea }.flatMap { group ->
                val floorTables = group.tables.filter { selectedFloor == null || it.floor == selectedFloor }
                if (floorTables.isEmpty()) return@flatMap emptyList()
                listOf(CalendarRow.Section(group.name)) + floorTables.map { table ->
                    CalendarRow.Resource(
                        table.tableNumber, table.tableNumber,
                        "${table.capacity} ${if (table.capacity == 1) "seat" else "seats"}",
                        Icons.Outlined.TableRestaurant
                    )
                }
            }
        }
        val showUnassigned = selectedArea == "All areas" || selectedArea == "Unassigned"
        (if (showUnassigned) listOf(unassignedSection, unassignedRow) else emptyList()) +
            (if (selectedArea == "Unassigned") emptyList() else groupRows)
    }
    val query = state.searchQuery.trim()
    val visibleEvents = events.filter { event ->
        (selectedStatus == "All statuses" || event.statusLabel == selectedStatus) &&
            (selectedArea == "All areas" || event.area == selectedArea) &&
            (query.isBlank() || listOf(event.code, event.guestName, event.timeRange, event.rowId)
                .any { it.contains(query, ignoreCase = true) })
    }
    // A search keeps tables whose number matches or that have a matching booking.
    val shownRows = remember(calendarRows, visibleEvents, query) {
        if (query.isBlank()) calendarRows else searchRows(calendarRows, visibleEvents, query)
    }
    var onlyBooked by remember { mutableStateOf(false) }

    val floorOfTable = remember(tableGroups) {
        tableGroups.flatMap { group -> group.tables.mapNotNull { table -> table.floor?.let { table.tableNumber to it } } }.toMap()
    }

    // Walk-in space for the next 2 hours, counted on the floor that is on screen.
    LaunchedEffect(clockNow, selectedFloor, state.lastRefreshedAt) {
        model.loadCapacity(clockNow.toString(), (clockNow + 2.hours).toString(), floor = selectedFloor)
    }
    // The overview's numbers and its Arriving feed follow the floor on screen (all of them with a single floor), and
    // reload with every refresh: every minute and after each change. The feed keeps the pages already shown.
    val overviewFloor = selectedFloor.takeIf { state.floors.size > 1 }
    LaunchedEffect(selectedDate, restaurantZone, overviewFloor, state.lastRefreshedAt) {
        val zone = RestaurantTime.zone
        model.loadSummary(
            LocalDateTime(selectedDate, LocalTime(START_HOUR.toInt(), 0)).toInstant(zone).toString(),
            LocalDateTime(selectedDate.plus(DatePeriod(days = 1)), LocalTime(END_HOUR.toInt() - 24, 0)).toInstant(zone).toString(),
            overviewFloor
        )
    }
    LaunchedEffect(model, state.lastRefreshedAt, overviewFloor) {
        model.refreshArrivals(overviewFloor)
    }
    // The overview uses the same filters as the calendar.
    fun matchesFilters(reservation: Reservation): Boolean {
        val tables = reservation.tableAssignments.mapNotNull { it.tableNumber }
        return (selectedStatus == "All statuses" || reservation.status.statusLabel() == selectedStatus) &&
            (selectedArea == "All areas" ||
                (selectedArea == "Unassigned" && tables.isEmpty()) ||
                tables.any { areaByTable[it] == selectedArea }) &&
            (selectedFloor == null || tables.isEmpty() || tables.any { floorOfTable[it] == selectedFloor }) &&
            (query.isBlank() ||
                (listOfNotNull(reservation.reservationCode, reservation.displayGuestName, reservation.contactPhone, reservation.contactEmail) + tables)
                    .any { it.contains(query, ignoreCase = true) })
    }
    val overviewReservations = remember(state.reservations, selectedStatus, selectedArea, selectedFloor, query, areaByTable, floorOfTable) {
        state.reservations.filter(::matchesFilters)
    }
    // Cards and side lists follow only the date and floor; status, area and search narrow the big list.
    val floorReservations = remember(state.reservations, selectedFloor, floorOfTable) {
        state.reservations.filter { reservation ->
            val tables = reservation.tableAssignments.mapNotNull { it.tableNumber }
            selectedFloor == null || tables.isEmpty() || tables.any { floorOfTable[it] == selectedFloor }
        }
    }

    // Reservations opens on the overview; the calendar slides in, like adding items in Orders.
    val pages = rememberPagerState(pageCount = { 2 })
    LaunchedEffect(calendarOpen) {
        pages.animateScrollToPage(if (calendarOpen) 1 else 0, animationSpec = tween(300))
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        HorizontalPager(
            state = pages,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
            beyondViewportPageCount = 1
        ) { page ->
            if (page == 0) {
                ReservationOverviewScreen(
                    arrivals = state.arrivals,
                    arrivalsPaging = ListPaging(
                        hasMore = state.hasMoreArrivals,
                        loading = state.isLoadingMoreArrivals,
                        failed = state.loadMoreArrivalsFailed,
                        onLoadMore = { model.loadMoreArrivals() },
                        ready = state.arrivalsLoaded
                    ),
                    date = selectedDate,
                    event = dayEvent,
                    onDateChange = { selectedDate = it },
                    floors = state.floors,
                    floor = selectedFloor,
                    onFloorChange = { pickedFloor = if (it == "All floors") ALL_FLOORS_PICKED else it },
                    selectedArea = selectedArea,
                    areaChoices = areaChoices,
                    onAreaChange = { selectedArea = it },
                    selectedStatus = selectedStatus,
                    onStatusChange = { selectedStatus = it },
                    searchQuery = state.searchQuery,
                    onSearchChange = model::setSearchQuery,
                    now = clockNow,
                    reservations = floorReservations,
                    listed = overviewReservations,
                    // Counted by the server for the whole day on the floor on screen.
                    summary = state.summary,
                    capacity = state.capacity,
                    loading = state.isLoading,
                    // Until the first answer arrives the page shows its skeleton.
                    firstLoad = state.lastRefreshedAt == null && state.error == null,
                    onOpenReservation = model::selectReservation,
                    dayPaging = ListPaging(
                        hasMore = state.hasMoreReservations,
                        loading = state.isLoadingMoreReservations,
                        failed = state.loadMoreReservationsFailed,
                        onLoadMore = { model.loadMoreReservations() }
                    ),
                    onOpenCalendar = {
                        model.closeReservationDetails()
                        model.loadAllReservationPages()
                        calendarOpen = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
                return@HorizontalPager
            }
            val calendarSide = when {
                this@BoxWithConstraints.maxWidth < 600.dp -> 12.dp
                this@BoxWithConstraints.maxWidth < 1100.dp -> 20.dp
                else -> 28.dp
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(start = calendarSide, end = calendarSide, top = if (calendarSide == 28.dp) 22.dp else 14.dp, bottom = if (calendarSide == 28.dp) 18.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (calendarSide == 28.dp) 14.dp else 10.dp)
            ) {
                ReservationsCalendarToolbar(
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it },
                    selectedFloor = if (allFloorsSelected) "All floors" else selectedFloor.orEmpty(),
                    floorChoices = calendarFloorChoices,
                    onFloorChange = { pickedFloor = if (it == "All floors") ALL_FLOORS_PICKED else it },
                    selectedArea = selectedArea,
                    areaChoices = areaChoices,
                    onAreaChange = { selectedArea = it },
                    selectedStatus = selectedStatus,
                    onStatusChange = { selectedStatus = it },
                    searchQuery = state.searchQuery,
                    onSearchChange = model::setSearchQuery,
                    view = calendarView,
                    onViewChange = { calendarView = it },
                    mapTime = mapTime,
                    onMapTimeChange = { mapTime = it },
                    nowSlot = run {
                        val zone = RestaurantTime.zone
                        if (clockNow.toLocalDateTime(zone).date != selectedDate) null
                        else {
                            val minutes = (clockNow.hoursFrom(selectedDate) * 60).toInt()
                            ServiceTime((minutes - minutes % 30).coerceIn(StartSlots.first().minutes, StartSlots.last().minutes))
                        }
                    },
                    onBack = {
                        model.closeReservationDetails()
                        calendarOpen = false
                    }
                )
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (state.lastRefreshedAt == null && state.error == null) {
                        CalendarSkeleton(Modifier.fillMaxSize())
                    } else when (calendarView) {
                        CalendarView.TIMELINE -> ReservationCalendar(
                            rows = shownRows,
                            hideEmptyRows = selectedStatus != "All statuses" || onlyBooked,
                            onlyBooked = onlyBooked,
                            onOnlyBookedChange = { onlyBooked = it },
                            emptyMessage = when {
                                query.isNotBlank() && shownRows.none { it is CalendarRow.Resource } -> "Nothing matches \"$query\"."
                                else -> null
                            },
                            events = visibleEvents,
                            selectedEventId = state.selectedReservationId,
                            selectedDate = selectedDate,
                            onEventClick = model::selectReservation,
                            modifier = Modifier.fillMaxSize()
                        )
                        CalendarView.MAP -> ReservationMapContent(
                            date = selectedDate,
                            time = mapTime,
                            floor = selectedFloor,
                            floorOfTable = floorOfTable,
                            reservations = state.reservations,
                            onOpenReservation = model::selectReservation,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // The reservation panel sits below the toolbar on either page; on a phone it covers the screen.
        val phonePanel = maxWidth < 600.dp
        androidx.compose.animation.AnimatedVisibility(
            visible = state.selectedReservationId != null,
            modifier = if (phonePanel) Modifier.fillMaxSize()
            else Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 28.dp, bottom = 18.dp).fillMaxHeight(),
            enter = slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160))
        ) {
            ReservationDetailsPanel(
                model = model,
                onClose = model::closeReservationDetails,
                groupOfTable = { areaByTable[it] },
                onGoToTable = onGoToTable,
                fullScreen = phonePanel,
                modifier = if (phonePanel) Modifier else Modifier.shadow(16.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            )
        }

        val maxButtonOffset = with(LocalDensity.current) {
            (maxHeight - RightEdgeActionButtonHeight).coerceAtLeast(0.dp).toPx()
        }
        if (state.selectedReservationId == null) RightEdgeActionButton(
            text = "Reservation",
            onClick = { createOpen = true },
            enabled = true,
            width = 110.dp,
            modifier = Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(0, (newButtonPosition * maxButtonOffset).roundToInt()) }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (maxButtonOffset > 0f) {
                            newButtonPosition = (newButtonPosition + delta / maxButtonOffset).coerceIn(0f, 1f)
                        }
                    }
                )
        )
        if (createOpen) {
            CreateReservationDialog(
                model = model,
                initialDate = selectedDate,
                onDismiss = { createOpen = false },
                onCreated = { createdDate ->
                    createOpen = false
                    selectedDate = createdDate
                }
            )
        }
    }
}

internal enum class CalendarView(val label: String, val icon: ImageVector) {
    TIMELINE("Timeline", Icons.Outlined.ViewTimeline),
    MAP("Map", Icons.Outlined.Map)
}

@Composable
private fun ReservationsCalendarToolbar(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    selectedFloor: String,
    floorChoices: List<String>,
    onFloorChange: (String) -> Unit,
    selectedArea: String,
    areaChoices: List<String>,
    onAreaChange: (String) -> Unit,
    selectedStatus: String,
    onStatusChange: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    view: CalendarView,
    onViewChange: (CalendarView) -> Unit,
    mapTime: ServiceTime,
    onMapTimeChange: (ServiceTime) -> Unit,
    nowSlot: ServiceTime?,
    onBack: () -> Unit
) {
    var datePickerOpen by remember { mutableStateOf(false) }
    val backAndTitle: @Composable () -> Unit = {
        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to overview", Modifier.size(20.dp), tint = Ink)
        }
        Text(
            text = "Calendar",
            fontFamily = Inter(),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = 0.sp,
            color = Ink
        )
    }
    val filters: @Composable () -> Unit = {
        Box {
            HeaderButton(
                text = selectedDate.calendarLabel(),
                icon = Icons.Outlined.CalendarToday,
                onClick = { datePickerOpen = true },
                modifier = Modifier.width(176.dp)
            )
            DropdownMenu(
                expanded = datePickerOpen,
                onDismissRequest = { datePickerOpen = false },
                offset = DpOffset(0.dp, 6.dp),
                shape = RoundedCornerShape(12.dp),
                containerColor = Color.White,
                shadowElevation = 8.dp
            ) {
                CompactDatePicker(
                    selectedDate = selectedDate,
                    onDateSelected = {
                        onDateSelected(it)
                        datePickerOpen = false
                    }
                )
            }
        }
        if (floorChoices.isNotEmpty()) {
            HeaderDropdown(
                label = selectedFloor,
                icon = Icons.Outlined.Layers,
                modifier = Modifier.width(140.dp),
                choices = floorChoices,
                onSelect = onFloorChange
            )
        }
        if (view == CalendarView.MAP) {
            var timePickerOpen by remember { mutableStateOf(false) }
            Box {
                HeaderButton(
                    text = mapTime.label(),
                    icon = Icons.Outlined.AccessTime,
                    onClick = { timePickerOpen = true },
                    modifier = Modifier.width(118.dp)
                )
                DropdownMenu(
                    expanded = timePickerOpen,
                    onDismissRequest = { timePickerOpen = false },
                    offset = DpOffset(0.dp, 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    containerColor = Color.White,
                    shadowElevation = 8.dp
                ) {
                    CompactTimePicker(
                        selected = mapTime,
                        nowSlot = nowSlot,
                        onSelected = {
                            onMapTimeChange(it)
                            timePickerOpen = false
                        }
                    )
                }
            }
        } else {
        HeaderDropdown(
            label = selectedArea,
            icon = Icons.Outlined.TableRestaurant,
            modifier = Modifier.width(146.dp),
            choices = areaChoices,
            onSelect = onAreaChange
        )
        HeaderDropdown(
            label = selectedStatus,
            icon = Icons.Outlined.FilterList,
            modifier = Modifier.width(154.dp),
            choices = listOf("All statuses") + StatusDotColors.keys,
            onSelect = onStatusChange,
            dotColors = StatusDotColors
        )
        SearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            modifier = Modifier.width(240.dp),
            placeholder = "Search guest, code, table",
            height = ToolbarHeight
        )
        }
    }
    val viewSwitch: @Composable () -> Unit = {
        HeaderDropdown(
            label = view.label,
            icon = view.icon,
            modifier = Modifier.width(130.dp),
            choices = CalendarView.entries.filter { it != view }.map { it.label },
            onSelect = { picked -> CalendarView.entries.firstOrNull { it.label == picked }?.let(onViewChange) },
            filled = true
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 1100.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                backAndTitle()
                Spacer(Modifier.weight(1f))
                filters()
                viewSwitch()
            }
        } else {
            // Narrower windows: title and view switch on top, the filters scroll sideways below.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    backAndTitle()
                    Spacer(Modifier.weight(1f))
                    viewSwitch()
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) { filters() }
            }
        }
    }
}

// Timeline shape while the first load runs: table column on the left, a few booking bars per row.
@Composable
private fun CalendarSkeleton(modifier: Modifier) {
    val alpha = rememberSkeletonAlpha("reservations-calendar")
    Surface(modifier = modifier.alpha(alpha), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Border)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val leftWidth = if (maxWidth < 560.dp) 108.dp else 150.dp
            Column(Modifier.fillMaxSize().clipToBounds()) {
                Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(Modifier.width(leftWidth - 40.dp).height(14.dp))
                    Spacer(Modifier.width(40.dp))
                    repeat(6) { SkeletonBox(Modifier.width(40.dp).height(10.dp), SkeletonLight); Spacer(Modifier.weight(1f)) }
                }
                HorizontalDivider(color = Border)
                repeat(8) { row ->
                    Row(Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.width(leftWidth), verticalAlignment = Alignment.CenterVertically) {
                            SkeletonBox(Modifier.size(22.dp), SkeletonLight, RoundedCornerShape(6.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SkeletonBox(Modifier.width(44.dp).height(12.dp))
                                SkeletonBox(Modifier.width(56.dp).height(9.dp), SkeletonLight)
                            }
                        }
                        // Bars at different places per row so it reads as a timeline.
                        val start = listOf(0.05f, 0.30f, 0.12f, 0.45f, 0.20f, 0.55f, 0.08f, 0.35f)[row]
                        Spacer(Modifier.weight(start.coerceAtLeast(0.01f)))
                        SkeletonBox(Modifier.weight(0.28f).height(52.dp), SkeletonLight, RoundedCornerShape(7.dp))
                        Spacer(Modifier.weight(1f - start))
                    }
                    HorizontalDivider(color = Border.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun ReservationCalendar(
    rows: List<CalendarRow>,
    hideEmptyRows: Boolean,
    onlyBooked: Boolean,
    onOnlyBookedChange: (Boolean) -> Unit,
    emptyMessage: String?,
    events: List<CalendarEvent>,
    selectedEventId: String?,
    selectedDate: LocalDate,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val now by produceState(Clock.System.now()) {
        while (true) {
            delay(30_000)
            value = Clock.System.now()
        }
    }
    val nowHour = now.hoursFrom(selectedDate).takeIf { it in START_HOUR..END_HOUR }
    // Time that has already passed is washed out; a day that is over is washed out entirely.
    val pastUntilHour = nowHour ?: END_HOUR.takeIf { now.hoursFrom(selectedDate) > END_HOUR }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Border)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Phones get a slimmer table column and hours so more of the day fits.
            val phoneTimeline = maxWidth < 560.dp
            val leftWidth = if (phoneTimeline) 108.dp else 150.dp
            val timelineWidth = maxWidth - leftWidth - TimelineGutter * 2
            // Hours never get narrower than MinHourWidth; the timeline scrolls sideways instead.
            val hourWidth = maxOf(timelineWidth / Hours.size, if (phoneTimeline) 120.dp else MinHourWidth)
            val timelineScroll = rememberScrollState()
            val hourWidthPx = with(LocalDensity.current) { hourWidth.toPx() }
            // Open around the current time on today, otherwise at the first booking of the day.
            LaunchedEffect(selectedDate, hourWidthPx) {
                val anchor = nowHour ?: events.minOfOrNull { it.startHour } ?: 11f
                val maxScroll = snapshotFlow { timelineScroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
                timelineScroll.animateScrollTo(((anchor - 1f - START_HOUR) * hourWidthPx).roundToInt().coerceIn(0, maxScroll))
            }
            var collapsedSections by remember { mutableStateOf(emptySet<String>()) }
            val allOrderedRows = remember(rows, events, hideEmptyRows) { orderRowsByReservations(rows, events, hideEmptyRows) }
            val hiddenTableCounts = remember(allOrderedRows) { tableCountBySection(allOrderedRows) }
            val orderedRows = remember(allOrderedRows, collapsedSections) { withoutCollapsedTables(allOrderedRows, collapsedSections) }
            val sectionCounts = remember(rows, events) { reservationCountBySection(rows, events) }

            Column(Modifier.fillMaxSize()) {
                CalendarHourHeader(
                    leftWidth = leftWidth,
                    hourWidth = hourWidth,
                    scroll = timelineScroll,
                    onlyBooked = onlyBooked,
                    onOnlyBookedChange = onOnlyBookedChange
                )
                if (emptyMessage != null) {
                    Text(
                        emptyMessage,
                        Modifier.fillMaxWidth().padding(24.dp),
                        fontFamily = Inter(), fontSize = 13.sp, color = Muted
                    )
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(
                        items = orderedRows,
                        key = { row ->
                            when (row) {
                                is CalendarRow.Resource -> "table-${row.id}"
                                is CalendarRow.Section -> "section-${row.sectionKey()}"
                            }
                        }
                    ) { row ->
                        Box(
                            Modifier.animateItem(
                                fadeInSpec = tween(260, delayMillis = 60),
                                placementSpec = tween(380, easing = FastOutSlowInEasing),
                                fadeOutSpec = tween(200)
                            )
                        ) {
                            when (row) {
                                is CalendarRow.Section -> CalendarSectionRow(
                                    title = row.title,
                                    collapsed = row.sectionKey() in collapsedSections,
                                    tableCount = hiddenTableCounts[row.sectionKey()] ?: 0,
                                    hiddenItemLabel = row.hiddenItemLabel,
                                    onToggle = {
                                        val key = row.sectionKey()
                                        collapsedSections = if (key in collapsedSections) collapsedSections - key else collapsedSections + key
                                    },
                                    detail = if (row.emptyTables) {
                                        "No reservations"
                                    } else {
                                        val count = sectionCounts[row.title] ?: 0
                                        "$count ${if (count == 1) "reservation" else "reservations"}"
                                    }
                                )
                                is CalendarRow.Resource -> CalendarResourceRow(
                                    row = row,
                                    events = events.filter { it.rowId == row.id },
                                    selectedEventId = selectedEventId,
                                    leftWidth = leftWidth,
                                    hourWidth = hourWidth,
                                    scroll = timelineScroll,
                                    pastUntilHour = pastUntilHour,
                                    onEventClick = onEventClick
                                )
                            }
                        }
                    }
                }
                TimelineScrollbar(leftWidth = leftWidth, scroll = timelineScroll)
            }
            if (pastUntilHour != null) {
                PastTimeVeil(
                    leftWidth = leftWidth,
                    width = TimelineGutter + hourWidth * (pastUntilHour - START_HOUR),
                    scroll = timelineScroll
                )
            }
            if (nowHour != null) {
                NowIndicator(
                    leftWidth = leftWidth,
                    x = TimelineGutter + hourWidth * (nowHour - START_HOUR),
                    label = now.localTimeLabel(),
                    scroll = timelineScroll
                )
            }
        }
    }
}

// Mouse drag pans the timeline; a plain wheel turn scrolls it sideways only over the hour header (see caller).
private fun Modifier.timelinePan(scroll: ScrollState): Modifier = pointerInput(scroll) {
    detectHorizontalDragGestures { change, dragAmount ->
        change.consume()
        scroll.dispatchRawDelta(-dragAmount)
    }
}

private fun Modifier.wheelToHorizontal(scroll: ScrollState): Modifier = pointerInput(scroll) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll) {
                val delta = event.changes.firstOrNull()?.scrollDelta ?: continue
                val amount = if (delta.x != 0f) delta.x else delta.y
                scroll.dispatchRawDelta(amount * 60f)
                event.changes.forEach { it.consume() }
            }
        }
    }
}

@Composable
private fun TimelineScrollbar(leftWidth: Dp, scroll: ScrollState) {
    if (scroll.maxValue <= 0 || scroll.maxValue == Int.MAX_VALUE) return
    Row(
        Modifier.fillMaxWidth().height(18.dp).background(Color.White).border(BorderStroke(0.5.dp, Grid)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(leftWidth))
        BoxWithConstraints(Modifier.weight(1f).padding(horizontal = 10.dp).height(8.dp)) {
            val trackPx = constraints.maxWidth.toFloat()
            val viewportPx = scroll.viewportSize.toFloat()
            val contentPx = viewportPx + scroll.maxValue
            val thumbPx = (trackPx * viewportPx / contentPx).coerceAtLeast(40f)
            val thumbOffsetPx = (trackPx - thumbPx) * scroll.value / scroll.maxValue
            val density = LocalDensity.current
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(50)).background(Color(0xFFF0EEEB)))
            Box(
                Modifier
                    .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                    .width(with(density) { thumbPx.toDp() })
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFBDB8B1))
                    .pointerInput(scroll, trackPx, thumbPx) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            scroll.dispatchRawDelta(dragAmount * scroll.maxValue / (trackPx - thumbPx).coerceAtLeast(1f))
                        }
                    }
            )
        }
    }
}

private fun reservationCountBySection(rows: List<CalendarRow>, events: List<CalendarEvent>): Map<String, Int> {
    val countByRow = events.groupingBy { it.rowId }.eachCount()
    val counts = mutableMapOf<String, Int>()
    var currentSection: String? = null
    rows.forEach { row ->
        when (row) {
            is CalendarRow.Section -> currentSection = row.title
            is CalendarRow.Resource -> currentSection?.let { title ->
                counts[title] = (counts[title] ?: 0) + (countByRow[row.id] ?: 0)
            }
        }
    }
    return counts
}

// Busiest groups first, busiest tables first inside each group; Unassigned stays on top; ungrouped tables sort like any group, last only among ties.
private fun orderRowsByReservations(
    rows: List<CalendarRow>,
    events: List<CalendarEvent>,
    hideEmptyRows: Boolean
): List<CalendarRow> {
    val countByRow = events.groupingBy { it.rowId }.eachCount()
    val leading = mutableListOf<CalendarRow.Resource>()
    val groups = mutableListOf<Pair<CalendarRow.Section, MutableList<CalendarRow.Resource>>>()
    rows.forEach { row ->
        when (row) {
            is CalendarRow.Section -> groups += row to mutableListOf()
            is CalendarRow.Resource -> groups.lastOrNull()?.second?.add(row) ?: leading.add(row)
        }
    }
    fun count(row: CalendarRow.Resource) = countByRow[row.id] ?: 0
    fun keep(row: CalendarRow.Resource) = !hideEmptyRows || count(row) > 0
    val emptyGroups = if (hideEmptyRows) {
        emptyList()
    } else {
        groups.map { (section, tables) -> section.copy(emptyTables = true) to tables.filter { count(it) == 0 } }
            .filter { (_, tables) -> tables.isNotEmpty() }
    }
    return buildList {
        addAll(leading.filter(::keep))
        groups
            .map { (section, tables) -> Triple(section, tables.filter { count(it) > 0 }.sortedByDescending(::count), tables.sumOf(::count)) }
            .filter { (_, tables, _) -> tables.isNotEmpty() }
            .sortedWith(
                compareByDescending<Triple<CalendarRow.Section, List<CalendarRow.Resource>, Int>> { it.third }
                    .thenBy { it.first.title != "Unassigned" }
                    .thenBy { it.first.title == OTHER_TABLES_GROUP }
            )
            .forEach { (section, tables, _) ->
                add(section)
                addAll(tables)
            }
        emptyGroups.forEach { (section, tables) ->
            add(section)
            addAll(tables)
        }
    }
}

@Composable
private fun CalendarHourHeader(
    leftWidth: Dp,
    hourWidth: Dp,
    scroll: ScrollState,
    onlyBooked: Boolean,
    onOnlyBookedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color.White)
            .border(BorderStroke(0.5.dp, Grid)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(leftWidth)
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Table / Area",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    color = Ink
                )
                Row(
                    Modifier.clip(RoundedCornerShape(4.dp)).clickable { onOnlyBookedChange(!onlyBooked) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        Modifier.size(12.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (onlyBooked) Olive else Color.White)
                            .border(1.dp, if (onlyBooked) Olive else Muted, RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (onlyBooked) Icon(Icons.Outlined.Check, null, Modifier.size(9.dp), tint = Color.White)
                    }
                    Text("Only booked", fontFamily = Inter(), fontSize = 10.sp, color = Muted)
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scroll)
                .timelinePan(scroll)
                .wheelToHorizontal(scroll)
        ) {
          Box(
              Modifier.padding(horizontal = TimelineGutter).width(hourWidth * Hours.size).fillMaxHeight()
          ) {
            Hours.forEachIndexed { index, hour ->
                Box(
                    modifier = Modifier
                        .offset(x = hourWidth * index - hourWidth / 2)
                        .width(hourWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${(hour % 24).toString().padStart(2, '0')}:00",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 0.sp,
                        color = Color(0xFF444642)
                    )
                }
            }
          }
        }
    }
}

@Composable
private fun CalendarSectionRow(
    title: String,
    detail: String,
    collapsed: Boolean,
    tableCount: Int,
    hiddenItemLabel: String,
    onToggle: () -> Unit
) {
    val arrowRotation by animateFloatAsState(if (collapsed) -90f else 0f, tween(220), label = "section-arrow")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Color(0xFFF3F5F1))
            .border(BorderStroke(0.5.dp, Grid))
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = if (collapsed) "Show $title" else "Hide $title",
            modifier = Modifier.padding(start = 10.dp).size(18.dp).graphicsLayer { rotationZ = arrowRotation },
            tint = Olive
        )
        Text(
            text = title,
            modifier = Modifier.padding(start = 6.dp),
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.sp,
            color = Olive
        )
        Text(
            text = " · $detail" + if (collapsed) " · $tableCount ${if (tableCount == 1) hiddenItemLabel else "${hiddenItemLabel}s"} hidden" else "",
            fontFamily = Inter(),
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 0.sp,
            color = Muted
        )
    }
}

private fun CalendarRow.Section.sectionKey(): String = "$title-$emptyTables-$hiddenItemLabel"

private fun tableCountBySection(rows: List<CalendarRow>): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    var current: String? = null
    rows.forEach { row ->
        when (row) {
            is CalendarRow.Section -> current = row.sectionKey()
            is CalendarRow.Resource -> current?.let { counts[it] = (counts[it] ?: 0) + 1 }
        }
    }
    return counts
}

private fun withoutCollapsedTables(rows: List<CalendarRow>, collapsed: Set<String>): List<CalendarRow> {
    if (collapsed.isEmpty()) return rows
    var hiding = false
    return rows.filter { row ->
        when (row) {
            is CalendarRow.Section -> {
                hiding = row.sectionKey() in collapsed
                true
            }
            is CalendarRow.Resource -> !hiding
        }
    }
}

@Composable
private fun CalendarResourceRow(
    row: CalendarRow.Resource,
    events: List<CalendarEvent>,
    selectedEventId: String?,
    leftWidth: Dp,
    hourWidth: Dp,
    scroll: ScrollState,
    pastUntilHour: Float?,
    onEventClick: (String) -> Unit
) {
    val (laneByEvent, laneCount) = remember(events) { assignLanes(events) }
    val blocksHeight = BlockHeight * laneCount + BlockGap * (laneCount - 1)
    val rowHeight = maxOf(row.height, blocksHeight + RowVerticalPadding * 2)
    val blocksTop = (rowHeight - blocksHeight) / 2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (row.id == "unassigned") Color(0xFFFFFCF7) else RowStripe)
            .border(BorderStroke(0.5.dp, Grid))
    ) {
        Row(
            modifier = Modifier
                .width(leftWidth)
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Icon(
                imageVector = row.icon,
                contentDescription = null,
                modifier = Modifier.size(if (row.id == "unassigned") 19.dp else 20.dp),
                tint = if (row.id == "unassigned") Warning else Color(0xFF9D680E)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    color = Ink
                )
                Text(
                    text = row.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.sp,
                    color = Muted
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scroll)
                .timelinePan(scroll)
        ) {
          Box(Modifier.padding(horizontal = TimelineGutter).width(hourWidth * Hours.size).fillMaxHeight()) {
            repeat(Hours.size + 1) { index ->
                Box(
                    modifier = Modifier
                        .offset(x = hourWidth * index)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Grid.copy(alpha = 0.75f))
                )
            }
            events.forEach { event ->
                ReservationBlock(
                    event = event,
                    selected = event.id == selectedEventId,
                    past = pastUntilHour != null && event.endHour <= pastUntilHour,
                    hourWidth = hourWidth,
                    onClick = { onEventClick(event.id) },
                    modifier = Modifier.offset(y = blocksTop + (BlockHeight + BlockGap) * (laneByEvent[event.id] ?: 0))
                )
            }
          }
        }
    }
}

// Overlapping reservations in one row go to separate lanes instead of being drawn on top of each other.
private fun assignLanes(events: List<CalendarEvent>): Pair<Map<String, Int>, Int> {
    val laneEnds = mutableListOf<Float>()
    val laneByEvent = mutableMapOf<String, Int>()
    events.sortedBy { it.startHour }.forEach { event ->
        val free = laneEnds.indexOfFirst { it <= event.startHour }
        val lane = if (free >= 0) free else laneEnds.size.also { laneEnds += 0f }
        laneEnds[lane] = event.endHour
        laneByEvent[event.id] = lane
    }
    return laneByEvent to laneEnds.size.coerceAtLeast(1)
}

@Composable
private fun ReservationBlock(
    event: CalendarEvent,
    selected: Boolean,
    past: Boolean,
    hourWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val x = (hourWidth.value * (event.startHour - START_HOUR)).dp
    val width = (hourWidth.value * max(0.75f, event.endHour - event.startHour)).dp
    val shape = RoundedCornerShape(7.dp)
    // A booking whose time is over turns gray, whatever its status.
    val background = when {
        selected -> Olive
        past -> PastBlockBackground
        else -> event.background
    }
    val border = when {
        selected -> Color.White
        past -> PastBlockBorder
        else -> event.border
    }
    val textColor = when {
        selected -> Color.White
        past -> Muted
        else -> Ink
    }

    Box(
        modifier = modifier
            .offset(x = x)
            .width(width)
            .height(BlockHeight)
            .clip(shape)
            .clickable(onClick = onClick)
            .background(background, shape)
            .border(1.dp, border, shape)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.code,
                    modifier = Modifier.weight(1f),
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor
                )
                Icon(Icons.Outlined.PersonOutline, null, Modifier.size(13.dp), tint = textColor)
                Text(
                    text = event.guests.toString(),
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.sp,
                    color = textColor
                )
            }
            Text(
                text = event.guestName,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor
            )
            Text(
                text = event.timeRange,
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.sp,
                maxLines = 1,
                color = textColor.copy(alpha = if (selected) 0.95f else 0.9f)
            )
        }
    }
}

internal val ToolbarHeight = 44.dp

@Composable
internal fun HeaderButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(ToolbarHeight),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ink)
            Text(
                text = text,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.sp,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ink)
        }
    }
}

// Small time grid in the date-picker style; opens scrolled to the chosen time.
@Composable
internal fun CompactTimePicker(
    selected: ServiceTime,
    nowSlot: ServiceTime?,
    onSelected: (ServiceTime) -> Unit
) {
    val slots = StartSlots.filter { it.minutes % 30 == 0 }
    val rows = slots.chunked(4)
    val scroll = rememberScrollState()
    val rowStepPx = with(LocalDensity.current) { (32.dp + 6.dp).toPx() }
    LaunchedEffect(Unit) {
        val row = rows.indexOfFirst { selected in it }.coerceAtLeast(0)
        scroll.scrollTo(((row - 1).coerceAtLeast(0) * rowStepPx).roundToInt())
    }
    Column(Modifier.width(264.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pick a time", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Ink)
            if (nowSlot != null) {
                Text(
                    "Now",
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable { onSelected(nowSlot) }.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Olive
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.height(190.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { slot ->
                        val isSelected = slot == selected
                        val isNow = slot == nowSlot
                        Box(
                            Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Olive else Color.Transparent)
                                .then(if (isNow && !isSelected) Modifier.border(1.dp, Olive, RoundedCornerShape(16.dp)) else Modifier)
                                .clickable { onSelected(slot) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                slot.label(), fontFamily = Inter(),
                                fontWeight = if (isSelected || isNow) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = when {
                                    isSelected -> Color.White
                                    isNow -> Olive
                                    else -> Ink
                                }
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun CompactDatePicker(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val today = remember(RestaurantTime.zone) { serviceDateOf(Clock.System.now(), RestaurantTime.zone) }
    var shownMonth by remember { mutableStateOf(LocalDate(selectedDate.year, selectedDate.monthNumber, 1)) }
    val daysInMonth = shownMonth.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
    val leadingBlanks = shownMonth.dayOfWeek.isoDayNumber - 1

    Column(Modifier.width(264.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(Icons.Outlined.ChevronLeft) { shownMonth = shownMonth.minus(DatePeriod(months = 1)) }
            Text(
                "${shownMonth.monthName()} ${shownMonth.year}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Ink
            )
            MonthArrow(Icons.Outlined.ChevronRight) { shownMonth = shownMonth.plus(DatePeriod(months = 1)) }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { label ->
                Text(
                    label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = LightMuted
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val cells = List(leadingBlanks) { null } + (1..daysInMonth).map { day -> shownMonth.plus(DatePeriod(days = day - 1)) }
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { date ->
                    Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val selected = date == selectedDate
                            val isToday = date == today
                            Box(
                                Modifier.size(30.dp).clip(CircleShape)
                                    .background(if (selected) Olive else Color.Transparent)
                                    .then(if (isToday && !selected) Modifier.border(1.dp, Olive, CircleShape) else Modifier)
                                    .clickable { onDateSelected(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${date.day}", fontFamily = Inter(),
                                    fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = when {
                                        selected -> Color.White
                                        isToday -> Olive
                                        else -> Ink
                                    }
                                )
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Today",
            modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(6.dp))
                .clickable { onDateSelected(today) }.padding(horizontal = 8.dp, vertical = 4.dp),
            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Olive
        )
    }
}

@Composable
private fun MonthArrow(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ink)
    }
}

@Composable
internal fun HeaderDropdown(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    choices: List<String>,
    onSelect: (String) -> Unit,
    dotColors: Map<String, Color> = emptyMap(),
    filled: Boolean = false
) {
    val content = if (filled) Color.White else Ink
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(ToolbarHeight),
            shape = RoundedCornerShape(8.dp),
            color = if (filled) Olive else Color.White,
            border = if (filled) null else BorderStroke(1.dp, Border)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val selectedDot = dotColors[label]
                if (selectedDot != null) StatusDot(selectedDot) else Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = content)
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.sp,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = choice,
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Ink
                        )
                    },
                    leadingIcon = dotColors[choice]?.let { color -> { StatusDot(color) } },
                    onClick = {
                        onSelect(choice)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun Reservation.toCalendarEvent(areaByTable: Map<String, String>, day: LocalDate): CalendarEvent? {
    val start = reservationStart.hoursFrom(day) ?: return null
    val end = reservationEnd.hoursFrom(day) ?: (start + 1.5f)
    // Only bookings that overlap this service day belong on its timeline.
    if (start >= END_HOUR || end <= START_HOUR) return null
    val table = primaryTable?.tableNumber?.takeIf(String::isNotBlank)
    val rowId = table ?: "unassigned"
    val style = status.style()
    return CalendarEvent(
        id = id,
        rowId = rowId,
        code = reservationCode ?: id.take(8).uppercase(),
        guestName = displayGuestName,
        guests = partySize,
        startHour = start.coerceIn(START_HOUR, END_HOUR - 0.5f),
        endHour = end.coerceIn(start.coerceIn(START_HOUR, END_HOUR - 0.5f) + 0.5f, END_HOUR),
        timeRange = "${reservationStart.localTimeText()} - ${reservationEnd.localTimeText()}",
        area = if (rowId == "unassigned") "Unassigned" else areaByTable[rowId] ?: OTHER_TABLES_GROUP,
        statusLabel = status.statusLabel(),
        background = style.background,
        border = style.border,
        selected = false
    )
}

private fun String.toLocal(): LocalDateTime? =
    runCatching { Instant.parse(this).toLocalDateTime(RestaurantTime.zone) }.getOrNull()

// Hours since midnight of [day] in local time, so 00:30 the next night becomes 24.5.
private fun String.hoursFrom(day: LocalDate): Float? {
    val local = toLocal() ?: return null
    return day.daysUntil(local.date) * 24 + local.hour + local.minute / 60f
}

private fun Instant.hoursFrom(day: LocalDate): Float {
    val local = toLocalDateTime(RestaurantTime.zone)
    return day.daysUntil(local.date) * 24 + local.hour + local.minute / 60f + local.second / 3600f
}

private fun String.localTimeText(): String =
    toLocal()?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" } ?: "--:--"

private fun ReservationStatus.statusLabel(): String = when (this) {
    ReservationStatus.PENDING -> "Pending"
    ReservationStatus.CONFIRMED -> "Confirmed"
    ReservationStatus.CHECKED_IN -> "Checked in"
    ReservationStatus.SEATED -> "Seated"
    ReservationStatus.COMPLETED -> "Completed"
    ReservationStatus.CANCELLED -> "Cancelled"
    ReservationStatus.NO_SHOW -> "No show"
    ReservationStatus.EXPIRED -> "Request expired"
}

private fun ReservationStatus.style(): EventStyle = when (this) {
    ReservationStatus.PENDING -> EventStyle(Color(0xFFFFEDCC), Color(0xFFE4AC45))
    ReservationStatus.CONFIRMED -> EventStyle(Color(0xFFDCEAD7), Color(0xFFBFD3B9))
    ReservationStatus.CHECKED_IN -> EventStyle(Color(0xFFD9EDF0), Color(0xFFB8D2D8))
    ReservationStatus.SEATED -> EventStyle(Color(0xFF4F7942), Color(0xFF4F7942))
    ReservationStatus.COMPLETED -> EventStyle(Color(0xFFE5E7E3), Color(0xFFD4D7D1))
    ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW -> EventStyle(Color(0xFFF0E2DE), Color(0xFFD0A39A))
    ReservationStatus.EXPIRED -> EventStyle(Color(0xFFEDEDEB), Color(0xFFCFCFCB))
}

private fun LocalDate.calendarLabel(): String = "${dayOfWeek.shortName()}, ${monthName()} $day, $year"

private fun kotlinx.datetime.DayOfWeek.shortName(): String = when (this) {
    kotlinx.datetime.DayOfWeek.MONDAY -> "Mon"
    kotlinx.datetime.DayOfWeek.TUESDAY -> "Tue"
    kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Wed"
    kotlinx.datetime.DayOfWeek.THURSDAY -> "Thu"
    kotlinx.datetime.DayOfWeek.FRIDAY -> "Fri"
    kotlinx.datetime.DayOfWeek.SATURDAY -> "Sat"
    kotlinx.datetime.DayOfWeek.SUNDAY -> "Sun"
}

private fun LocalDate.monthName(): String = when (monthNumber) {
    1 -> "Jan"
    2 -> "Feb"
    3 -> "Mar"
    4 -> "Apr"
    5 -> "May"
    6 -> "Jun"
    7 -> "Jul"
    8 -> "Aug"
    9 -> "Sep"
    10 -> "Oct"
    11 -> "Nov"
    else -> "Dec"
}


private sealed class CalendarRow {
    data class Section(
        val title: String,
        val emptyTables: Boolean = false,
        val hiddenItemLabel: String = "table"
    ) : CalendarRow()
    data class Resource(
        val id: String,
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
        val height: Dp = 74.dp
    ) : CalendarRow()
}

private data class CalendarEvent(
    val id: String,
    val rowId: String,
    val code: String,
    val guestName: String,
    val guests: Int,
    val startHour: Float,
    val endHour: Float,
    val timeRange: String,
    val area: String,
    val statusLabel: String,
    val background: Color,
    val border: Color,
    val selected: Boolean = false
)

private data class EventStyle(val background: Color, val border: Color)

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(9.dp).background(color, CircleShape))
}

// Green "current time" line across the timeline, with the time shown in the header.
@Composable
private fun NowIndicator(leftWidth: Dp, x: Dp, label: String, scroll: ScrollState) {
    Box(Modifier.fillMaxSize().padding(start = leftWidth).clipToBounds()) {
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx() - scroll.value - 1.dp.roundToPx(), 0) }
                .padding(top = 30.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(NowGreen)
        )
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx() - scroll.value - 22.dp.roundToPx(), 30.dp.roundToPx() - 9.dp.roundToPx()) }
                .width(44.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(50))
                .background(NowGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White)
        }
    }
}

private fun Instant.localTimeLabel(): String {
    val local = toLocalDateTime(RestaurantTime.zone)
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

@Composable
private fun PastTimeVeil(leftWidth: Dp, width: Dp, scroll: ScrollState) {
    Box(Modifier.fillMaxSize().padding(start = leftWidth, top = 54.dp, bottom = 18.dp).clipToBounds()) {
        Box(
            Modifier
                .offset { IntOffset(-scroll.value, 0) }
                .width(width)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.55f))
        )
    }
}

private fun searchRows(rows: List<CalendarRow>, events: List<CalendarEvent>, query: String): List<CalendarRow> {
    val rowsWithMatches = events.map { it.rowId }.toSet()
    val kept = rows.filter { row ->
        row !is CalendarRow.Resource || row.id.contains(query, ignoreCase = true) || row.id in rowsWithMatches
    }
    // Drop group headers that no longer have any table under them.
    return kept.filterIndexed { index, row ->
        row !is CalendarRow.Section || kept.drop(index + 1).takeWhile { it is CalendarRow.Resource }.isNotEmpty()
    }
}


private val PastBlockBackground = Color(0xFFEDEEEB)
private val PastBlockBorder = Color(0xFFD9DBD6)

// The service day runs 06:00–02:00: from midnight until 06:00 "today" is still the evening that started the day before.
internal fun serviceDateOf(instant: Instant, zone: TimeZone): LocalDate {
    val local = instant.toLocalDateTime(zone)
    return if (local.hour < START_HOUR.toInt()) local.date.minus(DatePeriod(days = 1)) else local.date
}
