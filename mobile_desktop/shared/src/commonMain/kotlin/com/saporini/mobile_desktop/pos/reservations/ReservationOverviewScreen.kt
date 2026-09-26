package com.saporini.mobile_desktop.pos.reservations

import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import com.saporini.mobile_desktop.core.components.SearchField
import androidx.compose.material.icons.outlined.FilterList
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val OrderLikeBorder = Color(0xFFE3E8E1)
private val ActiveStatuses = setOf(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN, ReservationStatus.SEATED)
private val PresentStatuses = ActiveStatuses + ReservationStatus.COMPLETED
private const val LATER_SHOWN = 20

// Reservations landing page: today's numbers, who arrives next, status split and walk-in space.
@Composable
internal fun ReservationOverviewScreen(
    later: List<Reservation>,
    date: LocalDate,
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
    onOpenCalendar: () -> Unit,
    firstLoad: Boolean = false,
    modifier: Modifier = Modifier
) {
    val zone = RestaurantTime.zone
    val isToday = serviceDateOf(now, zone) == date
    var datePickerOpen by remember { mutableStateOf(false) }
    val filters: @Composable () -> Unit = {
            Box {
                HeaderButton(
                    text = date.formLabel(),
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
                    CompactDatePicker(selectedDate = date, onDateSelected = {
                        onDateChange(it)
                        datePickerOpen = false
                    })
                }
            }
            if (floors.size > 1 && floor != null) {
                HeaderDropdown(label = floor, icon = Icons.Outlined.Layers, modifier = Modifier.width(140.dp), choices = floors, onSelect = onFloorChange)
            }
            HeaderDropdown(label = selectedArea, icon = Icons.Outlined.TableRestaurant, modifier = Modifier.width(146.dp), choices = areaChoices, onSelect = onAreaChange)
            HeaderDropdown(
                label = selectedStatus,
                icon = Icons.Outlined.FilterList,
                modifier = Modifier.width(154.dp),
                choices = listOf("All statuses") + StatusDotColors.keys,
                onSelect = onStatusChange,
                dotColors = StatusDotColors
            )
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
                else OverviewContent(date, now, isToday, reservations, listed, listFilter, later, summary, capacity, onOpenReservation, size, Modifier.weight(1f))
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
                else OverviewContent(date, now, isToday, reservations, listed, listFilter, later, summary, capacity, onOpenReservation, size, Modifier.fillMaxWidth())
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
    later: List<Reservation>,
    summary: ReservationSummary?,
    capacity: ReservationCapacity?,
    onOpenReservation: (String) -> Unit,
    size: ScreenSize,
    modifier: Modifier
) {
    val active = reservations.filter { it.status in PresentStatuses }
    val upcoming = reservations
        .filter { it.status == ReservationStatus.PENDING || it.status == ReservationStatus.CONFIRMED }
        .filter { reservation ->
            val start = runCatching { Instant.parse(reservation.reservationStart) }.getOrNull() ?: return@filter false
            !isToday || (start >= now - 0.25.hours && start <= now + 2.hours)
        }
        .sortedBy { it.reservationStart }
    val unassigned = reservations.count { it.status in ActiveStatuses && it.tableAssignments.isEmpty() }
    val cancelled = summary?.cancelledCount ?: reservations.count { it.status == ReservationStatus.CANCELLED }
    val noShows = summary?.noShowCount ?: reservations.count { it.status == ReservationStatus.NO_SHOW }

    // Left = still to come or at the table now; to arrive = not checked in yet.
    val left = active.filter { it.status != ReservationStatus.COMPLETED }
    val done = active.size - left.size
    val toArrive = active.filter { it.status == ReservationStatus.PENDING || it.status == ReservationStatus.CONFIRMED }
    val totalGuests = active.sumOf { it.partySize }
    val guestsToArrive = toArrive.sumOf { it.partySize }
    val card0: @Composable (Modifier) -> Unit = { cardModifier ->
        SummaryCard(
            Res.drawable.overview_reservations, "Reservations", "${left.size}",
            listOf("$done done", "$cancelled cancelled", "$noShows no show").joinToString(" · "),
            FormGreen, cardModifier,
            suffix = "of ${active.size} left",
            progress = if (active.isEmpty()) null else done.toFloat() / active.size
        )
    }
    val card1: @Composable (Modifier) -> Unit = { cardModifier ->
        SummaryCard(
            Res.drawable.overview_guests, "Guests", "$guestsToArrive",
            "${totalGuests - guestsToArrive} already arrived",
            Color(0xFF24748A), cardModifier,
            suffix = "of $totalGuests to arrive",
            progress = if (totalGuests == 0) null else (totalGuests - guestsToArrive).toFloat() / totalGuests
        )
    }
    val card2: @Composable (Modifier) -> Unit = { cardModifier ->
        SummaryCard(
            Res.drawable.overview_next_hours, "Next 2 hours",
            if (isToday) "${upcoming.size}" else "–",
            if (isToday) "${upcoming.sumOf { it.partySize }} guests arriving" else "Shown for today only",
            Color(0xFFC8790B), cardModifier,
            suffix = if (isToday) (if (upcoming.size == 1) "reservation coming" else "reservations coming") else null
        )
    }
    val card3: @Composable (Modifier) -> Unit = { cardModifier ->
        SummaryCard(
            Res.drawable.overview_no_table, "Without a table", "$unassigned",
            if (unassigned == 0) "Every booking has a table" else "Need a table",
            if (unassigned > 0) FormDanger else Color(0xFF8A8D88), cardModifier
        )
    }
    // With a status, area or search filter the list shows every match of the day instead of who arrives next.
    val filtering = listFilter.active
    val rows = if (filtering) listed.sortedBy { it.reservationStart } else upcoming
    // After the next 2 hours: the following bookings, today or on later days, with their date.
    val shownIds = rows.map { it.id }.toSet()
    var collapsedDays by remember { mutableStateOf(emptySet<String>()) }
    val laterBookings = if (!isToday) emptyList() else later
        .filter { it.id !in shownIds && (it.status == ReservationStatus.PENDING || it.status == ReservationStatus.CONFIRMED) }
        .filter { runCatching { Instant.parse(it.reservationStart) > now + 2.hours }.getOrDefault(false) }
        .take(LATER_SHOWN)
    val stillToComeToday = isToday && reservations.any { reservation ->
        (reservation.status == ReservationStatus.PENDING || reservation.status == ReservationStatus.CONFIRMED) &&
            runCatching { Instant.parse(reservation.reservationStart) > now }.getOrDefault(false)
    }
    val empty = when {
        rows.isNotEmpty() -> null
        filtering -> EmptyInfo(Icons.Outlined.SearchOff, "Nothing found", listFilter.emptyMessage(date))
        stillToComeToday -> EmptyInfo(Icons.Outlined.Schedule, "No more reservations in the next 2 hours", "The next guests arrive later today.", Res.drawable.overview_no_more_reservations)
        isToday -> EmptyInfo(Icons.Outlined.EventBusy, "No more reservations today", "New bookings for today will show up here.", Res.drawable.overview_no_more_reservations)
        else -> EmptyInfo(Icons.Outlined.EventBusy, "No bookings this day", "No pending or confirmed bookings on ${date.fullDay()}.")
    }
    val arrivingList: @Composable (Modifier, Boolean) -> Unit = { listModifier, fill ->
        ListContainer(
            icon = if (filtering) Icons.Outlined.FilterList else Icons.Outlined.EventAvailable,
            title = when {
                filtering -> listFilter.status ?: "Results"
                isToday -> "Arriving"
                else -> "Bookings"
            },
            count = rows.size,
            modifier = listModifier,
            fill = fill,
            // Nothing to list at all: the message sits in the middle of the box.
            emptyState = if (empty != null && laterBookings.isEmpty()) ({ CenteredEmpty(empty) }) else null
        ) {
            // Only later bookings to show: a compact version of the message above them.
            if (empty != null) CompactEmpty(empty)
            rows.forEach { reservation ->
                ReservationCard(reservation, now = now.takeIf { isToday && !filtering }) { onOpenReservation(reservation.id) }
            }
            if (laterBookings.isNotEmpty()) {
                Text(
                    "Later",
                    Modifier.padding(start = 4.dp, top = if (rows.isEmpty()) 4.dp else 10.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormMuted
                )
                // One collapsible group per day, e.g. "Monday, 28 September".
                laterBookings.groupBy { it.reservationStart.localDate() }.forEach { (day, bookings) ->
                    val key = day?.toString().orEmpty()
                    val open = key !in collapsedDays
                    DayGroupHeader(
                        label = day?.let { it.dayHeader(date) } ?: "Unknown date",
                        count = bookings.size,
                        open = open,
                        onToggle = { collapsedDays = if (open) collapsedDays + key else collapsedDays - key }
                    )
                    if (open) bookings.forEach { reservation ->
                        ReservationCard(reservation, now = null) { onOpenReservation(reservation.id) }
                    }
                }
            }
        }
    }
    val statusList: @Composable (Modifier, Boolean) -> Unit = { boxModifier, fill ->
        ListContainer(Icons.Outlined.DonutLarge, "By status", count = null, modifier = boxModifier, scrollable = fill) {
            listOf(
                Triple("Pending", summary?.pendingCount ?: reservations.count { it.status == ReservationStatus.PENDING }, Icons.Outlined.HourglassEmpty),
                Triple("Confirmed", summary?.confirmedCount ?: reservations.count { it.status == ReservationStatus.CONFIRMED }, Icons.Outlined.EventAvailable),
                Triple("Checked in", summary?.checkedInCount ?: reservations.count { it.status == ReservationStatus.CHECKED_IN }, Icons.Outlined.HowToReg),
                Triple("Seated", summary?.seatedCount ?: reservations.count { it.status == ReservationStatus.SEATED }, Icons.Outlined.EventSeat),
                Triple("Completed", summary?.completedCount ?: reservations.count { it.status == ReservationStatus.COMPLETED }, Icons.Outlined.TaskAlt),
                Triple("Cancelled", cancelled, Icons.Outlined.Cancel),
                Triple("No show", noShows, Icons.Outlined.PersonOff)
            ).forEach { (label, count, icon) -> StatusCountRow(icon, label, count, StatusDotColors[label] ?: FormMuted) }
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
        }
    }

    if (size.isDesktop) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                card0(Modifier.weight(1f)); card1(Modifier.weight(1f)); card2(Modifier.weight(1f)); card3(Modifier.weight(1f))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                arrivingList(Modifier.weight(1f).fillMaxHeight(), true)
                Column(Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    statusList(Modifier.fillMaxWidth().weight(1f), true)
                    walkIns(Modifier.fillMaxWidth())
                }
            }
        }
        return
    }
    // Tablet and phone: cards two by two, then the lists one under the other (the page scrolls).
    val gap = if (size.isPhone) 10.dp else 12.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { card0(Modifier.weight(1f)); card1(Modifier.weight(1f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { card2(Modifier.weight(1f)); card3(Modifier.weight(1f)) }
        arrivingList(Modifier.fillMaxWidth(), false)
        if (size.isPhone) {
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
    progress: Float? = null
) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, OrderLikeBorder), shadowElevation = 1.dp) {
        BoxWithConstraints {
        // Half-width phone cards skip the icon so the numbers keep their room.
        val showIcon = maxWidth >= 200.dp
        Row(Modifier.height(82.dp)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Row(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showIcon) {
                    Image(painterResource(image), null, Modifier.size(52.dp), contentScale = ContentScale.Fit)
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
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, OrderLikeBorder)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = FormInk)
                Spacer(Modifier.width(8.dp))
                Text(title, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                count?.let {
                    Text(
                        "$it", Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFF2F6F2)).padding(horizontal = 9.dp, vertical = 2.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormInk
                    )
                }
            }
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
private fun StatusCountRow(icon: ImageVector, label: String, count: Int, color: Color) {
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp))
            .background(if (count > 0) Color.White else Color(0xFFF3F4F2))
            .border(1.dp, if (count > 0) Color(0xFFE8E8E4) else Color(0xFFD8DBD5), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(if (count > 0) color.copy(alpha = 0.14f) else Color(0xFFE1E4DE)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (count > 0) color else Color(0xFF7F847D))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (count > 0) FormInk else Color(0xFF7F847D))
        Text("$count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (count > 0) FormInk else Color(0xFF7F847D))
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
private fun ReservationCard(reservation: Reservation, now: Instant?, onClick: () -> Unit) {
    val label = when (reservation.status) {
        ReservationStatus.PENDING -> "Pending"
        ReservationStatus.CONFIRMED -> "Confirmed"
        ReservationStatus.CHECKED_IN -> "Checked in"
        ReservationStatus.SEATED -> "Seated"
        ReservationStatus.COMPLETED -> "Completed"
        ReservationStatus.CANCELLED -> "Cancelled"
        ReservationStatus.NO_SHOW -> "No show"
    }
    val statusColor = StatusDotColors[label] ?: FormMuted
    val tables = reservation.tableAssignments.mapNotNull { it.tableNumber }
    val minutesAway = now?.let { runCatching { (Instant.parse(reservation.reservationStart) - it).inWholeMinutes }.getOrNull() }
    val whenText = when {
        minutesAway == null -> "until ${reservation.reservationEnd.clock()}"
        minutesAway <= 0 -> "now"
        minutesAway < 60 -> "in $minutesAway min"
        else -> "in ${minutesAway / 60} h ${minutesAway % 60} min"
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
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
                        when {
                            minutesAway == null -> "until ${reservation.reservationEnd.clock()}"
                            minutesAway <= 0 -> "now"
                            minutesAway < 60 -> "in $minutesAway min"
                            else -> "in ${minutesAway / 60} h ${minutesAway % 60} min"
                        },
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen, maxLines = 1
                    )
                }
                Box(Modifier.width(1.dp).height(40.dp).background(OrderLikeBorder))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(reservation.displayGuestName, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Groups, null, Modifier.size(15.dp), tint = FormMuted)
                        Spacer(Modifier.width(5.dp))
                        Text("${reservation.partySize} guests", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
                    }
                }
                Row(
                    Modifier.widthIn(min = 92.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (tables.isEmpty()) FormDanger.copy(alpha = 0.10f) else Color(0xFFF2F6F2))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.TableRestaurant, null, Modifier.size(17.dp), tint = if (tables.isEmpty()) FormDanger else FormInk)
                    Text(
                        if (tables.isEmpty()) "No table" else tables.joinToString(" + "),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = if (tables.isEmpty()) FormDanger else FormInk, maxLines = 1
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(22.dp)).background(statusColor.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
                    Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = statusColor)
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

@Composable
private fun DayGroupHeader(label: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F6F2))
            .clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            if (open) "Collapse" else "Expand", Modifier.size(18.dp), tint = FormGreen
        )
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (count == 1) "1 reservation" else "$count reservations",
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
