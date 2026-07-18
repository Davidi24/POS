package com.saporini.mobile_desktop.pos.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.saporini.mobile_desktop.core.theme.Inter

private val ActiveOlive = Color(0xFF4B522A)
private val Ink = Color(0xFF202124)
private val Muted = Color(0xFF70736F)
private val Border = Color(0xFFE6E1DB)
private val Pending = Color(0xFFC77606)
private val Confirmed = Color(0xFF5E7F39)
private val CheckedIn = Color(0xFF5D8C8E)
private val Seated = Color(0xFF98580E)

@Composable
fun ReservationsScreen(
    modifier: Modifier = Modifier
) {
    var selectedFloor by remember { mutableStateOf("1st Floor") }
    var selectedStatus by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedReservationId by remember { mutableStateOf<String?>(null) }

    val visibleReservations = reservationSamples.filter { reservation ->
        (selectedStatus == "All" || reservation.status == selectedStatus) &&
            (searchQuery.isBlank() ||
                reservation.id.contains(searchQuery, ignoreCase = true) ||
                reservation.guest.contains(searchQuery, ignoreCase = true) ||
                reservation.phone.contains(searchQuery, ignoreCase = true))
    }
    val selectedReservation = selectedReservationId?.let { id ->
        reservationSamples.firstOrNull { it.id == id }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ReservationsToolbar(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            selectedFloor = selectedFloor,
            onFloorSelected = { selectedFloor = it },
            selectedStatus = selectedStatus,
            onStatusSelected = { selectedStatus = it }
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ReservationTimeline(
                reservations = visibleReservations,
                selectedReservationId = selectedReservation?.id,
                onReservationClick = { selectedReservationId = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            selectedReservation?.let { reservation ->
                ReservationDetailsPanel(
                    reservation = reservation,
                    onClose = { selectedReservationId = null },
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun ReservationsToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFloor: String,
    onFloorSelected: (String) -> Unit,
    selectedStatus: String,
    onStatusSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReservationSearchField(searchQuery, onSearchChange, Modifier.width(224.dp))
        FloorToggle(selectedFloor, onFloorSelected)
        DateNavigator()
        StatusButton("All", selectedStatus == "All", null) { onStatusSelected("All") }
        StatusButton("Pending", selectedStatus == "Pending", Pending) { onStatusSelected("Pending") }
        StatusButton("Confirmed", selectedStatus == "Confirmed", Confirmed) { onStatusSelected("Confirmed") }
        StatusButton("Checked In", selectedStatus == "Checked In", CheckedIn) { onStatusSelected("Checked In") }
        StatusButton("Seated", selectedStatus == "Seated", Seated) { onStatusSelected("Seated") }
        Spacer(Modifier.weight(1f))
        NewReservationButton()
    }
}

@Composable
private fun ReservationSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ink)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 0.sp,
                color = Ink
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Search name / phone",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            letterSpacing = 0.sp,
                            color = Color(0xFF949494)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun FloorToggle(
    selectedFloor: String,
    onFloorSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(3.dp)
    ) {
        listOf("1st Floor", "2nd Floor", "3rd Floor").forEach { floor ->
            val selected = floor == selectedFloor
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) ActiveOlive else Color.Transparent)
                    .clickable { onFloorSelected(floor) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = floor,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    color = if (selected) Color.White else Muted
                )
            }
        }
    }
}

@Composable
private fun DateNavigator() {
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {}, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ink)
        }
        Box(
            modifier = Modifier
                .width(76.dp)
                .fillMaxHeight()
                .border(1.dp, Border),
            contentAlignment = Alignment.Center
        ) {
            Text("Today", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
        }
        IconButton(onClick = {}, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ink)
        }
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .border(1.dp, Border),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ink)
        }
    }
}

@Composable
private fun StatusButton(
    text: String,
    selected: Boolean,
    dotColor: Color?,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(42.dp)
            .width(
                when (text) {
                    "Checked In" -> 112.dp
                    "Confirmed" -> 106.dp
                    "Pending" -> 92.dp
                    "Seated" -> 88.dp
                    else -> 60.dp
                }
            )
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ActiveOlive else Color.White)
            .border(1.dp, Border, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) Color.White else Ink)
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun NewReservationButton() {
    TextButton(
        onClick = {},
        modifier = Modifier
            .height(42.dp)
            .width(176.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ActiveOlive),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Text("New Reservation", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp)
    }
}

@Composable
private fun ReservationTimeline(
    reservations: List<ReservationSample>,
    selectedReservationId: String?,
    onReservationClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .background(Color.White)
    ) {
        TimelineHeader()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                timelineRows.forEach { row ->
                    when (row) {
                        is TimelineRow.Section -> SectionRow(row.title)
                        is TimelineRow.Table -> ReservationTableRow(
                            row = row,
                            reservations = reservations.filter { it.rowId == row.id },
                            selectedReservationId = selectedReservationId,
                            onReservationClick = onReservationClick
                        )
                    }
                }
            }
            NowLine()
        }
    }
}

@Composable
private fun TimelineHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color(0xFFFCFBFA))
            .border(1.dp, Border),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(154.dp)
                .fillMaxHeight()
                .padding(start = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("Table / Area", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Ink)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val slotWidth = maxWidth / 14f
            (10..24).forEachIndexed { index, hour ->
                Text(
                    text = "${hour}:00",
                    modifier = Modifier.offset(x = slotWidth * index).padding(top = 20.dp),
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.sp,
                    color = Ink
                )
            }
        }
    }
}

@Composable
private fun SectionRow(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color(0xFFF3F0EC))
            .border(1.dp, Border),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 14.dp),
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.sp,
            color = ActiveOlive
        )
    }
}

@Composable
private fun ReservationTableRow(
    row: TimelineRow.Table,
    reservations: List<ReservationSample>,
    selectedReservationId: String?,
    onReservationClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .border(1.dp, Border),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .width(154.dp)
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (row.id == "unassigned") Icons.Outlined.Groups else Icons.Outlined.TableRestaurant,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color(0xFF9C6A20)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(row.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
                Text(row.capacity, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Muted)
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFFFEFDFB))
        ) {
            val slotWidth = maxWidth / 28f
            repeat(28) { slot ->
                Box(
                    modifier = Modifier
                        .offset(x = slotWidth * slot)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(if (slot % 2 == 0) Color(0xFFF7F5F2) else Color(0xFFFCFBFA))
                )
            }
            reservations.forEach { reservation ->
                ReservationBlock(
                    reservation = reservation,
                    selected = reservation.id == selectedReservationId,
                    modifier = Modifier
                        .offset(x = slotWidth * reservation.startSlot)
                        .width((slotWidth * reservation.durationSlots).coerceAtLeast(112.dp))
                        .align(Alignment.CenterStart),
                    onClick = { onReservationClick(reservation.id) }
                )
            }
        }
    }
}

@Composable
private fun ReservationBlock(
    reservation: ReservationSample,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = reservationColor(reservation.status)
    val background = if (reservation.status == "Pending") Color(0xFFFFDA91) else color
    val contentColor = if (reservation.status == "Pending") Ink else Color.White
    Column(
        modifier = modifier
            .height(52.dp)
            .shadow(if (selected) 5.dp else 1.dp, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .border(if (selected) 2.dp else 1.dp, color, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = reservation.id,
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
            Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(13.dp), tint = contentColor)
            Spacer(Modifier.width(3.dp))
            Text(reservation.partySize.toString(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = contentColor)
        }
        Text(
            text = reservation.guest,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = contentColor,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = reservation.timeRange,
            fontFamily = Inter(),
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            color = contentColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun NowLine() {
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(3f)) {
        val left = 154.dp
        val timelineWidth = maxWidth - left
        val x = left + (timelineWidth * (16f / 28f))
        Box(
            modifier = Modifier
                .offset(x = x)
                .width(2.dp)
                .fillMaxHeight()
                .background(Confirmed)
        )
        Box(
            modifier = Modifier
                .offset(x = x - 18.dp, y = (-16).dp)
                .clip(RoundedCornerShape(50))
                .background(ActiveOlive)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Now", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White)
        }
    }
}

@Composable
private fun ReservationDetailsPanel(
    reservation: ReservationSample,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Reservation ${reservation.id}",
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                letterSpacing = 0.sp,
                color = Ink
            )
            IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(20.dp), tint = Ink)
            }
        }

        Chip(reservation.status, reservationColor(reservation.status), fill = reservation.status != "Pending")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Border, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color(0xFF9C6A20))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(reservation.guest, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
                Text(reservation.phone, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Ink)
            }
        }

        DetailRow(Icons.Outlined.Groups, "Party Size", "${reservation.partySize} guests")
        DetailRow(Icons.Outlined.CalendarToday, "Date", "May 24, 2025")
        DetailRow(Icons.Outlined.AccessTime, "Time", reservation.startTime)
        DetailRow(Icons.Outlined.AccessTime, "Duration", reservation.duration)
        DetailRow(Icons.Outlined.CalendarToday, "Status", reservation.status)
        DetailRow(Icons.Outlined.EventSeat, "Floor", reservation.floor)
        DetailRow(Icons.Outlined.TableRestaurant, "Table", reservation.tableLabel)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Note", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
            Text(reservation.note, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Muted)
        }

        if (reservation.tableLabel == "Unassigned") {
            AssignTableBox()
        }

        Text("Actions", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
        DetailActionButton("Confirm Reservation", Icons.Filled.Check, true)
        DetailActionButton("Edit Reservation", Icons.Outlined.Edit, false)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailActionButton("No-show", Icons.Outlined.Groups, false, Modifier.weight(1f))
            DetailActionButton("Cancel", Icons.Filled.Close, false, Modifier.weight(1f), danger = true)
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF9C6A20))
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
    }
}

@Composable
private fun AssignTableBox() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Assign a Table", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
        Text("This reservation is not assigned to a table yet.", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Muted)
        DetailActionButton("Auto-Assign", Icons.Filled.Check, true)
        DetailActionButton("Assign Table", Icons.Outlined.TableRestaurant, false)
    }
}

@Composable
private fun DetailActionButton(
    text: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val border = if (danger) Color(0xFFE7B8B8) else if (active) ActiveOlive else Color(0xFFC9C4BE)
    val background = if (active) if (danger) Color(0xFFC93535) else ActiveOlive else Color.White
    val content = if (active) Color.White else if (danger) Color(0xFFC93535) else ActiveOlive
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable {},
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = content)
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = content)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, fill: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (fill) color else color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (fill) Color.White else color)
    }
}

private fun reservationColor(status: String): Color = when (status) {
    "Confirmed" -> Confirmed
    "Checked In" -> CheckedIn
    "Seated" -> Seated
    else -> Pending
}

private sealed class TimelineRow {
    data class Section(val title: String) : TimelineRow()
    data class Table(val id: String, val label: String, val capacity: String) : TimelineRow()
}

private data class ReservationSample(
    val id: String,
    val rowId: String,
    val guest: String,
    val phone: String,
    val partySize: Int,
    val startSlot: Int,
    val durationSlots: Int,
    val status: String,
    val startTime: String,
    val timeRange: String,
    val duration: String,
    val floor: String,
    val tableLabel: String,
    val note: String
)

private val timelineRows = listOf(
    TimelineRow.Table("unassigned", "Unassigned", "No table assigned"),
    TimelineRow.Section("Main Salon"),
    TimelineRow.Table("A1", "A1", "2-4 seats"),
    TimelineRow.Table("A2", "A2", "2-4 seats"),
    TimelineRow.Table("A3", "A3", "4-6 seats"),
    TimelineRow.Table("A4", "A4", "4-6 seats"),
    TimelineRow.Section("Terrace"),
    TimelineRow.Table("T1", "T1", "2-4 seats"),
    TimelineRow.Table("T2", "T2", "2-4 seats"),
    TimelineRow.Section("Bar"),
    TimelineRow.Table("B1", "B1", "1-2 seats"),
    TimelineRow.Table("B2", "B2", "1-2 seats"),
    TimelineRow.Section("Private Salon"),
    TimelineRow.Table("P1", "P1", "6-10 seats")
)

private val reservationSamples = listOf(
    ReservationSample("RSV-201", "unassigned", "Emma Wilson", "+1 (555) 123-1001", 2, 3, 4, "Pending", "11:30", "11:30 - 12:30", "1h", "Not Assigned", "Unassigned", "No table assigned."),
    ReservationSample("RSV-204", "unassigned", "Michael Brown", "+1 (555) 123-4567", 4, 17, 5, "Pending", "18:30", "18:30 - 20:00", "1h 30m", "Not Assigned", "Unassigned", "Anniversary celebration."),
    ReservationSample("RSV-207", "unassigned", "Olivia Martinez", "+1 (555) 123-1007", 6, 23, 4, "Pending", "21:30", "21:30 - 22:30", "1h", "Not Assigned", "Unassigned", "Needs high chair."),
    ReservationSample("RSV-101", "A1", "James Carter", "+1 (555) 123-1101", 2, 1, 4, "Confirmed", "11:00", "11:00 - 12:30", "1h 30m", "1st Floor", "A1", "Window seat preferred."),
    ReservationSample("RSV-112", "A1", "Sofia Rossi", "+1 (555) 123-1112", 3, 14, 4, "Checked In", "17:00", "17:00 - 18:30", "1h 30m", "1st Floor", "A1", "Arrived early."),
    ReservationSample("RSV-128", "A1", "Daniel Kim", "+1 (555) 123-1128", 4, 21, 3, "Seated", "20:30", "20:30 - 22:00", "1h 30m", "1st Floor", "A1", "Birthday dinner."),
    ReservationSample("RSV-102", "A2", "Lucas Moretti", "+1 (555) 123-1102", 2, 4, 4, "Confirmed", "12:00", "12:00 - 13:30", "1h 30m", "1st Floor", "A2", "Quiet area."),
    ReservationSample("RSV-113", "A2", "Isabella Conti", "+1 (555) 123-1113", 4, 17, 4, "Confirmed", "18:30", "18:30 - 20:00", "1h 30m", "1st Floor", "A2", "No special notes."),
    ReservationSample("RSV-103", "A3", "Grace Lee", "+1 (555) 123-1103", 4, 6, 4, "Checked In", "13:00", "13:00 - 14:30", "1h 30m", "1st Floor", "A3", "Guest checked in."),
    ReservationSample("RSV-129", "A3", "Andrew Smith", "+1 (555) 123-1129", 6, 20, 3, "Seated", "20:00", "20:00 - 21:30", "1h 30m", "1st Floor", "A3", "Wine pairing."),
    ReservationSample("RSV-104", "A4", "Hiroshi Tanaka", "+1 (555) 123-1104", 3, 8, 4, "Checked In", "14:00", "14:00 - 15:30", "1h 30m", "1st Floor", "A4", "Arrived."),
    ReservationSample("RSV-114", "A4", "Mia Johnson", "+1 (555) 123-1114", 2, 19, 4, "Confirmed", "19:00", "19:00 - 20:30", "1h 30m", "1st Floor", "A4", "No special notes."),
    ReservationSample("RSV-105", "T1", "Ben Thompson", "+1 (555) 123-1105", 2, 3, 4, "Confirmed", "11:30", "11:30 - 13:00", "1h 30m", "1st Floor", "T1", "Terrace requested."),
    ReservationSample("RSV-116", "T1", "Chloe Martin", "+1 (555) 123-1116", 3, 14, 4, "Checked In", "16:30", "16:30 - 18:00", "1h 30m", "1st Floor", "T1", "Patio table."),
    ReservationSample("RSV-130", "T1", "Noah Davis", "+1 (555) 123-1130", 4, 21, 4, "Seated", "20:30", "20:30 - 22:00", "1h 30m", "1st Floor", "T1", "No special notes."),
    ReservationSample("RSV-106", "T2", "Ava Garcia", "+1 (555) 123-1106", 2, 5, 4, "Confirmed", "12:30", "12:30 - 14:00", "1h 30m", "1st Floor", "T2", "Terrace."),
    ReservationSample("RSV-117", "T2", "Ethan White", "+1 (555) 123-1117", 2, 17, 4, "Confirmed", "18:00", "18:00 - 19:30", "1h 30m", "1st Floor", "T2", "No special notes."),
    ReservationSample("RSV-131", "T2", "Zoe Walker", "+1 (555) 123-1131", 3, 23, 3, "Seated", "21:00", "21:00 - 22:30", "1h 30m", "1st Floor", "T2", "Dessert candle."),
    ReservationSample("RSV-107", "B1", "Chris Hall", "+1 (555) 123-1107", 2, 0, 3, "Checked In", "11:00", "11:00 - 12:00", "1h", "1st Floor", "B1", "Bar seats."),
    ReservationSample("RSV-118", "B1", "Mason Clark", "+1 (555) 123-1118", 2, 15, 3, "Confirmed", "17:30", "17:30 - 18:30", "1h", "1st Floor", "B1", "Bar seats."),
    ReservationSample("RSV-132", "B1", "Ryan Lewis", "+1 (555) 123-1132", 2, 22, 3, "Seated", "21:00", "21:00 - 22:00", "1h", "1st Floor", "B1", "Late seating."),
    ReservationSample("RSV-108", "B2", "Lily Allen", "+1 (555) 123-1108", 2, 3, 4, "Confirmed", "12:00", "12:00 - 13:00", "1h", "1st Floor", "B2", "Bar."),
    ReservationSample("RSV-119", "B2", "Natalie Young", "+1 (555) 123-1119", 2, 17, 4, "Checked In", "18:30", "18:30 - 19:30", "1h", "1st Floor", "B2", "Checked in."),
    ReservationSample("RSV-133", "B2", "Tom Harris", "+1 (555) 123-1133", 2, 24, 3, "Seated", "22:00", "22:00 - 23:00", "1h", "1st Floor", "B2", "Late drinks."),
    ReservationSample("RSV-109", "P1", "Corporate Dinner", "+1 (555) 123-1109", 8, 10, 9, "Seated", "15:00", "15:00 - 18:00", "3h", "1st Floor", "P1", "Private room setup."),
    ReservationSample("RSV-134", "P1", "Birthday Celebration", "+1 (555) 123-1134", 10, 20, 8, "Seated", "19:30", "19:30 - 23:00", "3h 30m", "1st Floor", "P1", "Birthday cake storage.")
)
