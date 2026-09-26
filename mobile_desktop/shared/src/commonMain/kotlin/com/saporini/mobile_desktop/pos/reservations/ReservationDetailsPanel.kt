package com.saporini.mobile_desktop.pos.reservations

import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import androidx.compose.material.icons.outlined.Info
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationRules

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationNote
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import com.saporini.mobile_desktop.pos.reservations.domain.model.UpdateReservationInput
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationsScreenModel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import androidx.compose.material.icons.outlined.Schedule

// Right-hand panel for one reservation: details in tabs, notes, history, editing and status actions.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReservationDetailsPanel(
    model: ReservationsScreenModel,
    onClose: () -> Unit,
    groupOfTable: (String) -> String?,
    onGoToTable: (String) -> Unit,
    fullScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val state by model.state.collectAsState()
    val reservation = state.selectedReservation
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var editSection by remember(reservation?.id) { mutableStateOf<EditSection?>(null) }
    var tab by remember(reservation?.id) { mutableStateOf(PanelTab.INFO) }
    var menuOpen by remember { mutableStateOf(false) }

    var pendingConfirm by remember { mutableStateOf<StatusAction?>(null) }
    var assigningTable by remember(reservation?.id) { mutableStateOf(false) }

    fun runAction(action: StatusAction, reason: String? = null) {
        val id = reservation?.id ?: return
        if (action.danger && reason == null) {
            pendingConfirm = action
            return
        }
        val note = reason?.takeIf(String::isNotBlank)
        when (action) {
            StatusAction.CONFIRM -> model.confirmReservation(id)
            StatusAction.CHECK_IN -> model.checkInReservation(id)
            StatusAction.COMPLETE -> model.completeReservation(id)
            StatusAction.CANCEL -> model.cancelReservation(id, note)
            StatusAction.NO_SHOW -> model.markNoShow(id, note)
            StatusAction.REOPEN -> model.reopenReservation(id)
        }
    }

    if (assigningTable && reservation != null) {
        ChangeTableDialog(
            model = model,
            reservation = reservation,
            guests = reservation.partySize,
            startIso = reservation.reservationStart,
            endIso = reservation.reservationEnd,
            timeLabel = "${reservation.reservationStart.localTime()} – ${reservation.reservationEnd.localTime()}",
            onDismiss = { assigningTable = false },
            onPick = { picked ->
                assigningTable = false
                val current = reservation.tableAssignments.map { it.tableId }
                if (picked.map { it.first } != current) {
                    model.patchReservation(
                        reservation.id,
                        UpdateReservationInput(tableIds = picked.map { it.first }, primaryTableId = picked.firstOrNull()?.first)
                    )
                }
            }
        )
    }
    pendingConfirm?.let { action ->
        DangerConfirmDialog(
            action = action,
            guestName = reservation?.displayGuestName.orEmpty(),
            onConfirm = { reason ->
                pendingConfirm = null
                runAction(action, reason)
            },
            onDismiss = { pendingConfirm = null }
        )
    }

    Column(
        modifier.fillMaxHeight().then(if (fullScreen) Modifier.fillMaxWidth() else Modifier.width(420.dp)).background(Color.White)
            .border(BorderStroke(1.dp, FormBorder))
    ) {
        if (reservation == null) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                SquareIconButton(Icons.Outlined.Close, "Close", onClose)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val selectedId = state.selectedReservationId
                if (state.error != null && selectedId != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Could not load this reservation.", fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
                        Surface(onClick = { model.selectReservation(selectedId) }, shape = RoundedCornerShape(8.dp), color = FormGreen) {
                            Text(
                                "Try again", Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White
                            )
                        }
                    }
                } else {
                    CircularProgressIndicator(Modifier.size(28.dp), color = FormGreen, strokeWidth = 3.dp)
                }
            }
            return@Column
        }

        val tables = reservation.tableAssignments.mapNotNull { it.tableNumber }
        val tableLabel = if (tables.isEmpty()) "Unassigned" else tables.joinToString(" + ")
        val menuActions = reservation.status.menuActions()

        Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "#${reservation.reservationCode ?: reservation.id.take(8).uppercase()}",
                    Modifier.weight(1f, fill = false),
                    fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = FormInk,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F1EE))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.TableRestaurant, null, Modifier.size(18.dp), tint = FormInk)
                    Text(tableLabel, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (menuActions.isNotEmpty()) {
                    Box {
                        SquareIconButton(Icons.Outlined.MoreVert, "More actions") { menuOpen = true }
                        DropdownMenu(menuOpen, { menuOpen = false }, modifier = Modifier.background(Color.White)) {
                            menuActions.forEach { action ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            action.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                            color = if (action.danger) FormDanger else FormInk
                                        )
                                    },
                                    leadingIcon = { Icon(action.icon, null, Modifier.size(18.dp), tint = if (action.danger) FormDanger else FormInk) },
                                    onClick = {
                                        menuOpen = false
                                        runAction(action)
                                    }
                                )
                            }
                        }
                    }
                }
                SquareIconButton(Icons.Outlined.Close, "Close", onClose)
            }
            StatusChip(reservation.status)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.PersonOutline, null, Modifier.size(24.dp), tint = FormInk)
                Column(Modifier.weight(1f)) {
                    Text(reservation.displayGuestName, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = FormInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${reservation.partySize} ${if (reservation.partySize == 1) "guest" else "guests"}", fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
                }
                reservation.contactPhone?.takeIf(String::isNotBlank)?.let { phone ->
                    SquareIconButton(Icons.Outlined.Phone, "Call ${reservation.displayGuestName}") {
                        runCatching { uriHandler.openUri("tel:${phone.filter { it.isDigit() || it == '+' }}") }
                    }
                }
                reservation.contactEmail?.takeIf(String::isNotBlank)?.let { email ->
                    SquareIconButton(Icons.Outlined.Email, "Email ${reservation.displayGuestName}") {
                        runCatching { uriHandler.openUri("mailto:$email") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarToday, null, Modifier.size(18.dp), tint = FormInk)
                Spacer(Modifier.width(8.dp))
                Text(reservation.reservationStart.localDateLabel(), Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = FormInk)
                Icon(Icons.Outlined.AccessTime, null, Modifier.size(18.dp), tint = FormInk)
                Spacer(Modifier.width(8.dp))
                Text("${reservation.reservationStart.localTime()} - ${reservation.reservationEnd.localTime()}", fontFamily = Inter(), fontSize = 13.sp, color = FormInk)
                durationLabel(reservation.reservationStart, reservation.reservationEnd)?.let {
                    Text(" ($it)", fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        val section = editSection
        if (section != null) {
            HorizontalDivider(color = FormBorder)
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { editSection = null }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to details", Modifier.size(20.dp), tint = FormInk)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (section == EditSection.INFO) "Edit info" else "Edit notes",
                    fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = FormInk
                )
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)) {
                EditReservationForm(
                    reservation = reservation,
                    section = section,
                    notes = state.notes,
                    currentUserId = state.scope?.userId,
                    rules = state.rules,
                    saving = state.isSaving,
                    onCancel = { editSection = null },
                    onSave = { input, notesToAdd, noteIdsToDelete ->
                        scope.launch {
                            if (input != null) {
                                model.patchReservation(reservation.id, input).join()
                                if (model.state.value.error != null) return@launch
                            }
                            noteIdsToDelete.forEach { model.deleteNote(reservation.id, it).join() }
                            notesToAdd.forEach { model.addNote(reservation.id, it).join() }
                            if (model.state.value.error == null) editSection = null
                        }
                    }
                )
            }
            return@Column
        }

        PanelTabs(tab) { tab = it }
        val noTables = tab == PanelTab.TABLES && reservation.tableAssignments.isEmpty()
        val noNotes = tab == PanelTab.NOTES && state.notes.isEmpty() && !state.isLoadingDetails
        if (noTables || noNotes) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                if (noTables) EmptyWithAction(Icons.Outlined.TableRestaurant, "No table assigned yet.", "Add table") { assigningTable = true }
                else EmptyWithAction(Icons.Outlined.StickyNote2, "No notes yet.", "Add note") { editSection = EditSection.NOTES }
            }
        } else Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                PanelTab.INFO -> InfoCard {
                    InfoLine(Icons.Outlined.Phone, reservation.contactPhone?.takeIf(String::isNotBlank) ?: "No phone")
                    InfoLine(Icons.Outlined.Email, reservation.contactEmail?.takeIf(String::isNotBlank) ?: "No email")
                    InfoLine(Icons.Outlined.PersonOutline, "${reservation.partySize} ${if (reservation.partySize == 1) "guest" else "guests"}")
                    InfoLine(
                        Icons.Outlined.TableRestaurant,
                        if (tables.isEmpty()) "No table yet" else "Table $tableLabel",
                        muted = tables.firstOrNull()?.let(groupOfTable)?.let { "($it)" }
                    )
                    InfoBlock(Icons.Outlined.Notes, "Special request", reservation.specialRequests?.takeIf(String::isNotBlank) ?: "None")
                    InfoBlock(Icons.Outlined.StickyNote2, "Internal note", reservation.internalNotes?.takeIf(String::isNotBlank) ?: "None")
                }
                PanelTab.TABLES -> {
                    reservation.tableAssignments.forEach { assignment ->
                        InfoCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.TableRestaurant, null, Modifier.size(20.dp), tint = FormInk)
                                Column(Modifier.weight(1f)) {
                                    Text("Table ${assignment.tableNumber ?: "?"}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = FormInk)
                                    Text(
                                        listOfNotNull(
                                            assignment.capacity?.let { "$it seats" },
                                            assignment.floor,
                                            assignment.tableNumber?.let(groupOfTable)
                                        ).joinToString(" · "),
                                        fontFamily = Inter(), fontSize = 12.sp, color = FormMuted
                                    )
                                }
                                if (assignment.primary == true) {
                                    Text(
                                        "Primary",
                                        Modifier.clip(RoundedCornerShape(50)).background(FormGreenSoft).padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = FormGreen
                                    )
                                }
                            }
                        }
                    }
                }
                PanelTab.NOTES -> {
                    if (state.isLoadingDetails && state.notes.isEmpty()) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = FormGreen, strokeWidth = 2.dp)
                    } else {
                        Text("Existing notes (${state.notes.size})", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormInk)
                        state.notes.forEach { note ->
                            NoteCard(note, byCurrentUser = note.createdBy == state.scope?.userId, onDelete = null)
                        }
                    }
                }
                PanelTab.HISTORY -> {
                    if (state.statusHistory.isEmpty()) {
                        Text("No status changes yet.", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
                    }
                    state.statusHistory.sortedByDescending { it.changedAt }.forEach { entry ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.padding(top = 5.dp).size(9.dp)
                                    .background(StatusDotColors[entry.newStatus?.panelLabel()] ?: FormMuted, CircleShape)
                            )
                            Column {
                                Text(
                                    listOfNotNull(entry.oldStatus?.panelLabel(), entry.newStatus?.panelLabel()).joinToString(" → "),
                                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk
                                )
                                Text(
                                    listOfNotNull(entry.changedAt?.localDateLabel(), entry.changedAt?.localTime(), entry.reason).joinToString(" · "),
                                    fontFamily = Inter(), fontSize = 12.sp, color = FormMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = FormBorder)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                PanelTab.INFO -> FooterButton("Edit info", Icons.Outlined.Edit, primary = false, modifier = Modifier.fillMaxWidth()) { editSection = EditSection.INFO }
                // Without a table or notes, the centered Add button in the tab is the only action.
                PanelTab.TABLES -> if (reservation.tableAssignments.isNotEmpty()) {
                    FooterButton("Edit table", Icons.Outlined.TableRestaurant, primary = false, modifier = Modifier.fillMaxWidth()) { assigningTable = true }
                }
                PanelTab.NOTES -> if (state.notes.isNotEmpty()) {
                    FooterButton("Edit notes", Icons.Outlined.Edit, primary = false, modifier = Modifier.fillMaxWidth()) { editSection = EditSection.NOTES }
                }
                PanelTab.HISTORY -> Unit
            }
            val (secondary, primary) = reservation.status.footerActions()
            val primaryTable = reservation.primaryTable?.tableNumber
            if (reservation.status == ReservationStatus.CHECKED_IN || reservation.status == ReservationStatus.SEATED) {
                if (primaryTable != null) {
                    FooterButton("Go to table $primaryTable", Icons.Outlined.TableRestaurant, primary = true, modifier = Modifier.fillMaxWidth()) {
                        onGoToTable(primaryTable)
                    }
                } else {
                    Text(
                        "Checked in without a table. Add one in the Tables tab, then seat the guests from Tables.",
                        fontFamily = Inter(), fontSize = 12.sp, color = FormDanger
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                secondary?.let { action ->
                    FooterButton(action.label, action.icon, primary = false, modifier = Modifier.weight(1f)) { if (!state.isSaving) runAction(action) }
                }
                // Same rule as the server: check-in opens 2 hours before the start.
                val checkInOpensAt = runCatching { Instant.parse(reservation.reservationStart) - 2.hours }.getOrNull()
                val tooEarly = primary == StatusAction.CHECK_IN && checkInOpensAt != null && Clock.System.now() < checkInOpensAt
                if (tooEarly && checkInOpensAt != null) {
                    val opens = checkInOpensAt.toLocalDateTime(RestaurantTime.zone)
                    Row(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F6F2)).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = FormMuted)
                        Text(
                            "Check-in opens ${opens.date.formLabel().substringBeforeLast(",")} at ${opens.hour.toString().padStart(2, '0')}:${opens.minute.toString().padStart(2, '0')}",
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                } else primary?.let { action ->
                    FooterButton(action.label, action.icon, primary = true, modifier = Modifier.weight(1f)) { if (!state.isSaving) runAction(action) }
                }
            }
        }
    }
}

private enum class StatusAction(val label: String, val icon: ImageVector, val danger: Boolean = false) {
    CONFIRM("Confirm", Icons.Outlined.CheckCircle),
    CHECK_IN("Check in", Icons.Outlined.HowToReg),
    COMPLETE("Complete", Icons.Outlined.TaskAlt),
    REOPEN("Reopen", Icons.Outlined.Replay),
    NO_SHOW("Mark as no show", Icons.Outlined.PersonOff, danger = true),
    CANCEL("Cancel reservation", Icons.Outlined.Cancel, danger = true)
}

// (secondary, primary) buttons shown at the bottom of the panel.
private fun ReservationStatus.footerActions(): Pair<StatusAction?, StatusAction?> = when (this) {
    ReservationStatus.PENDING -> null to StatusAction.CONFIRM
    // Seating happens from the Tables screen, so there is no seat button here.
    ReservationStatus.CONFIRMED -> null to StatusAction.CHECK_IN
    ReservationStatus.CHECKED_IN -> null to null
    ReservationStatus.SEATED -> null to StatusAction.COMPLETE
    ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW -> null to StatusAction.REOPEN
    ReservationStatus.COMPLETED -> null to null
}

private fun ReservationStatus.menuActions(): List<StatusAction> = when (this) {
    ReservationStatus.PENDING -> listOf(StatusAction.CANCEL)
    ReservationStatus.CONFIRMED -> listOf(StatusAction.NO_SHOW, StatusAction.CANCEL)
    else -> emptyList()
}

private fun ReservationStatus.panelLabel(): String = when (this) {
    ReservationStatus.PENDING -> "Pending"
    ReservationStatus.CONFIRMED -> "Confirmed"
    ReservationStatus.CHECKED_IN -> "Checked in"
    ReservationStatus.SEATED -> "Seated"
    ReservationStatus.COMPLETED -> "Completed"
    ReservationStatus.CANCELLED -> "Cancelled"
    ReservationStatus.NO_SHOW -> "No show"
}

private fun durationLabel(start: String, end: String): String? {
    val from = runCatching { Instant.parse(start) }.getOrNull() ?: return null
    val to = runCatching { Instant.parse(end) }.getOrNull() ?: return null
    val minutes = (to - from).inWholeMinutes
    if (minutes <= 0) return null
    val hours = minutes / 60
    val rest = minutes % 60
    return listOfNotNull(hours.takeIf { it > 0 }?.let { "${it}h" }, rest.takeIf { it > 0 }?.let { "${it}m" }).joinToString(" ")
}

private enum class PanelTab(val label: String) { INFO("Info"), TABLES("Tables"), NOTES("Notes"), HISTORY("History") }

@Composable
private fun PanelTabs(current: PanelTab, onSelect: (PanelTab) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            PanelTab.entries.forEach { tab ->
                val selected = tab == current
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        tab.label, Modifier.padding(vertical = 10.dp),
                        fontFamily = Inter(), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp, color = if (selected) FormGreen else FormMuted
                    )
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(if (selected) FormGreen else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = FormBorder)
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, FormBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun InfoBlock(icon: ImageVector, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = FormInk)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = FormInk)
            Text(text, fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
        }
    }
}

@Composable
private fun SquareIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, FormBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(18.dp), tint = FormInk)
        }
    }
}

@Composable
private fun FooterButton(label: String, icon: ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (primary) FormGreen else Color.White,
        border = if (primary) null else BorderStroke(1.dp, FormBorder)
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (primary) Color.White else FormGreen)
            Spacer(Modifier.width(8.dp))
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (primary) Color.White else FormGreen)
        }
    }
}

@Composable
private fun EditReservationForm(
    reservation: Reservation,
    section: EditSection,
    notes: List<ReservationNote>,
    currentUserId: String?,
    rules: ReservationRules,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (UpdateReservationInput?, notesToAdd: List<String>, noteIdsToDelete: Set<String>) -> Unit
) {
    // Note changes wait for "Save changes", like the rest of the form.
    var notesToAdd by remember(reservation.id) { mutableStateOf<List<String>>(emptyList()) }
    var noteIdsToDelete by remember(reservation.id) { mutableStateOf<Set<String>>(emptySet()) }
    var noteTags by remember(reservation.id) { mutableStateOf<List<String>>(emptyList()) }
    var noteText by remember(reservation.id) { mutableStateOf("") }
    val zone = RestaurantTime.zone
    val startLocal = remember(reservation.id) { reservation.reservationStart.toLocalOrNull(zone) }
    val endLocal = remember(reservation.id) { reservation.reservationEnd.toLocalOrNull(zone) }
    var guests by remember(reservation.id) { mutableIntStateOf(reservation.partySize) }
    // Bookings before 06:00 belong to the previous service day (e.g. 01:00 is late night of that evening).
    val serviceDate = startLocal?.let { if (it.hour < 6) it.date.minus(DatePeriod(days = 1)) else it.date }
    var date by remember(reservation.id) { mutableStateOf(serviceDate ?: LocalDate(2026, 1, 1)) }
    var start by remember(reservation.id) {
        mutableStateOf(startLocal?.let { ServiceTime(serviceDate!!.daysUntil(it.date) * 1440 + it.hour * 60 + it.minute) } ?: ServiceTime(19 * 60))
    }
    var end by remember(reservation.id) {
        mutableStateOf(endLocal?.let { ServiceTime(serviceDate!!.daysUntil(it.date) * 1440 + it.hour * 60 + it.minute) } ?: ServiceTime(21 * 60))
    }
    var name by remember(reservation.id) { mutableStateOf(reservation.contactName.orEmpty()) }
    var phone by remember(reservation.id) { mutableStateOf(reservation.contactPhone.orEmpty()) }
    var email by remember(reservation.id) { mutableStateOf(reservation.contactEmail.orEmpty()) }
    var attempted by remember(reservation.id) { mutableStateOf(false) }
    val nameError = nameProblem(name)
    val phoneError = phoneProblem(phone)
    val emailError = emailProblem(email)
    val timeError = if (end <= start) "End time must be after the start time." else null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (section == EditSection.INFO) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormField("Guests", required = true, modifier = Modifier.width(90.dp)) {
                DropdownBox(Icons.Outlined.PersonOutline, "$guests", guestChoices(rules)) { guests = it.toInt() }
            }
            FormField("Date", required = true, modifier = Modifier.weight(1f)) {
                DateBox(date) { date = it }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormField("Start", required = true, modifier = Modifier.weight(1f)) {
                DropdownBox(Icons.Outlined.AccessTime, start.label(), StartSlots.map { it.label() }) { picked ->
                    val newStart = StartSlots.first { it.label() == picked }
                    val length = end.toMinutes() - start.toMinutes()
                    start = newStart
                    end = TimeSlots.firstOrNull { it.toMinutes() >= newStart.toMinutes() + length.coerceAtLeast(15) } ?: TimeSlots.last()
                }
            }
            FormField("End", required = true, modifier = Modifier.weight(1f)) {
                DropdownBox(Icons.Outlined.AccessTime, end.label(), TimeSlots.filter { it > start }.map { it.label() }) { picked ->
                    end = TimeSlots.first { it > start && it.label() == picked }
                }
            }
        }
        if (attempted && timeError != null) ErrorText(timeError)
        ruleWarnings(rules, guests, date, kotlin.time.Clock.System.now().toLocalDateTime(zone).date).forEach { RuleWarning(it) }
        FormField("Customer name", required = true) {
            InputBox(Icons.Outlined.PersonOutline, name, "Full name", isError = attempted && nameError != null) { name = it.take(NAME_LIMIT) }
            if (attempted && nameError != null) ErrorText(nameError)
        }
        FormField("Phone") {
            InputBox(Icons.Outlined.Phone, phone, "+1 234 567 8900", keyboardType = KeyboardType.Phone, isError = attempted && phoneError != null) { phone = cleanPhoneInput(it) }
            if (attempted && phoneError != null) ErrorText(phoneError)
        }
        FormField("Email") {
            InputBox(Icons.Outlined.Email, email, "email@example.com", keyboardType = KeyboardType.Email, isError = attempted && emailError != null) { email = it.trim().take(EMAIL_LIMIT) }
            if (attempted && emailError != null) ErrorText(emailError)
        }
        }
        if (section == EditSection.NOTES) {
        FormField("Notes") {
            val keptNotes = notes.filter { it.id !in noteIdsToDelete }
            if (keptNotes.isEmpty() && notesToAdd.isEmpty()) {
                Text("No notes yet.", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
            }
            keptNotes.forEach { note ->
                NoteCard(note, byCurrentUser = note.createdBy == currentUserId, onDelete = { noteIdsToDelete = noteIdsToDelete + note.id })
            }
            notesToAdd.forEachIndexed { index, text ->
                NoteCard(
                    ReservationNote(id = "new-$index", note = text),
                    byCurrentUser = true,
                    pending = true,
                    onDelete = { notesToAdd = notesToAdd.filterIndexed { i, _ -> i != index } }
                )
            }
            NoteSuggestionChips(selected = noteTags, onToggle = { tag ->
                noteTags = if (tag in noteTags) noteTags - tag else noteTags + tag
            })
            NoteBox(noteText, "Or write your own note…") { noteText = it.take(NOTE_LIMIT) }
            val combined = combinedNote(noteTags, noteText)
            Surface(
                onClick = {
                    if (combined != null) {
                        notesToAdd = notesToAdd + combined
                        noteTags = emptyList()
                        noteText = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = BorderStroke(1.dp, if (combined != null) FormGreen else FormBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Add note", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (combined != null) FormGreen else FormMuted)
                }
            }
        }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = BorderStroke(1.dp, FormBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Cancel", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
                }
            }
            Surface(
                onClick = {
                    attempted = true
                    val infoValid = section != EditSection.INFO || (nameError == null && phoneError == null && emailError == null && timeError == null)
                    if (infoValid && !saving) {
                        onSave(
                            if (section == EditSection.INFO) {
                                UpdateReservationInput(
                                    partySize = guests,
                                    reservationStart = start.at(date).toInstant(zone).toString(),
                                    reservationEnd = end.at(date).toInstant(zone).toString(),
                                    contactName = name.trim(),
                                    contactPhone = phone.trim(),
                                    contactEmail = email.trim()
                                )
                            } else {
                                null
                            },
                            notesToAdd,
                            noteIdsToDelete
                        )
                    }
                },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(8.dp),
                color = FormGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Save changes", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormInk)
        content()
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String, muted: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = FormInk)
        Text(
            text, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = FormInk,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        muted?.let { Text(it, fontFamily = Inter(), fontSize = 13.sp, color = FormMuted, maxLines = 1, softWrap = false) }
    }
}

@Composable
private fun StatusChip(status: ReservationStatus) {
    val label = when (status) {
        ReservationStatus.PENDING -> "Pending"
        ReservationStatus.CONFIRMED -> "Confirmed"
        ReservationStatus.CHECKED_IN -> "Checked in"
        ReservationStatus.SEATED -> "Seated"
        ReservationStatus.COMPLETED -> "Completed"
        ReservationStatus.CANCELLED -> "Cancelled"
        ReservationStatus.NO_SHOW -> "No show"
    }
    val color = StatusDotColors[label] ?: FormMuted
    Row(
        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = color)
    }
}

@Composable
private fun NoteCard(note: ReservationNote, byCurrentUser: Boolean, onDelete: (() -> Unit)?, pending: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F7F5))
            .border(1.dp, FormBorder, RoundedCornerShape(10.dp)).padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(note.note, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk, overflow = TextOverflow.Ellipsis)
            Text(
                if (pending) "New · saved when you press Save changes"
                else listOfNotNull(
                    note.createdAt?.localDateLabel(),
                    when {
                        byCurrentUser -> "by you"
                        note.createdByName != null -> "by ${note.createdByName}"
                        else -> "by staff"
                    }
                ).joinToString("  •  "),
                fontFamily = Inter(), fontSize = 11.sp, color = FormMuted
            )
        }
        if (onDelete != null) {
            Surface(onClick = onDelete, shape = RoundedCornerShape(8.dp), color = Color.Transparent) {
                Icon(Icons.Outlined.DeleteOutline, "Delete note", Modifier.padding(7.dp).size(18.dp), tint = FormInk)
            }
        }
    }
}

private fun String.toLocalOrNull(zone: TimeZone): LocalDateTime? =
    runCatching { Instant.parse(this).toLocalDateTime(zone) }.getOrNull()

private fun String.localTime(): String =
    toLocalOrNull(RestaurantTime.zone)?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" } ?: "--:--"

private fun String.localDateLabel(): String =
    toLocalOrNull(RestaurantTime.zone)?.date?.formLabel() ?: "Unknown date"

@Composable
private fun DangerConfirmDialog(
    action: StatusAction,
    guestName: String,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val cancelling = action == StatusAction.CANCEL
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (cancelling) "Cancel this reservation?" else "Mark as no show?",
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (cancelling) "The reservation for $guestName will be cancelled and its table becomes free. You can reopen it later."
                    else "$guestName will be marked as not showing up and the table becomes free. You can reopen it later.",
                    fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = FormMuted
                )
                InputBox(Icons.Outlined.Notes, reason, "Reason (optional)") { reason = it.take(200) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim()) },
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = FormDanger)
            ) {
                Text(if (cancelling) "Cancel reservation" else "Mark no show", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep it", color = FormMuted, fontFamily = Inter()) }
        }
    )
}

private enum class EditSection { INFO, NOTES }

@Composable
private fun EmptyWithAction(icon: ImageVector, message: String, action: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = FormMuted)
        Text(message, fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
        Surface(onClick = onClick, modifier = Modifier.height(42.dp), shape = RoundedCornerShape(8.dp), color = FormGreen) {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = Color.White)
                Text(action, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
            }
        }
    }
}
