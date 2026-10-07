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
import androidx.compose.runtime.produceState
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

    var reasonPrompt by remember { mutableStateOf<ReasonPrompt?>(null) }
    var arrivedPrompt by remember { mutableStateOf<ArrivedPrompt?>(null) }
    var holdPrompt by remember { mutableStateOf(false) }
    var assigningTable by remember(reservation?.id) { mutableStateOf(false) }
    // Seat right after picking a table (checked in without one).
    var seatAfterPick by remember(reservation?.id) { mutableStateOf(false) }
    val policy = state.policy
    // Ticks every 30 s so "late" and "hold ends" labels stay current.
    val now by produceState(Clock.System.now()) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            value = Clock.System.now()
        }
    }

    fun ask(prompt: ReasonPrompt) { reasonPrompt = prompt }

    fun start(offer: OfferedAction) {
        val booking = reservation ?: return
        if (!offer.enabled || state.isSaving) return
        val id = booking.id
        val name = booking.displayGuestName
        val correction = offer.needsReason
        fun reasonHint(optional: String = "Reason (optional)") = if (correction) "Reason for the correction" else optional
        when (offer.action) {
            BookingAction.ACCEPT -> if (correction) ask(ReasonPrompt(
                "Accept this request?", offer.note.orEmpty(), "Accept", reasonRequired = true, reasonHint = reasonHint()
            ) { model.confirmReservation(id, it) }) else model.confirmReservation(id)
            BookingAction.DECLINE -> ask(ReasonPrompt(
                "Decline this request?",
                "The request from $name is declined and its table becomes free. The guest is told by email, and anything they paid goes back in full.",
                "Decline request",
                danger = true, reasonRequired = correction, reasonHint = reasonHint(),
                suggestions = listOf("Fully booked", "Closed that day", "Group too big")
            ) { reason -> model.declineReservation(id, reason) })
            BookingAction.GUEST_ARRIVED -> if (booking.partySize > 1) {
                arrivedPrompt = ArrivedPrompt(
                    "How many have arrived?", booking.partySize, booking.partySize,
                    confirmLabel = { if (it == booking.partySize) "Everyone's here" else "$it arrived" },
                    reasonRequired = correction, note = offer.note
                ) { count, reason -> model.checkInReservation(id, reason, count) }
            } else if (correction) ask(ReasonPrompt(
                "Guest arrived?", offer.note.orEmpty(), "Guest arrived", reasonRequired = true, reasonHint = reasonHint()
            ) { model.checkInReservation(id, it) }) else model.checkInReservation(id)
            BookingAction.ARRIVED_COUNT -> arrivedPrompt = ArrivedPrompt(
                "How many have arrived?", booking.partySize, booking.arrivedGuests ?: booking.partySize,
                confirmLabel = { "Save: $it of ${booking.partySize}" }, reasonRequired = correction, note = offer.note
            ) { count, reason -> model.updateArrivedGuests(id, count, reason) }
            BookingAction.SEAT -> if (booking.tableAssignments.isEmpty()) {
                seatAfterPick = true
                assigningTable = true
            } else model.seatReservation(id)
            BookingAction.UNDO_SEAT -> ask(ReasonPrompt(
                "Undo seating?", "$name goes back to waiting for a table, and the table becomes free.", "Undo seating",
                reasonRequired = correction, reasonHint = reasonHint(), suggestions = listOf("Seated the wrong booking")
            ) { model.undoSeatReservation(id, it) })
            BookingAction.FINISH -> model.completeReservation(id)
            BookingAction.LEFT_WITHOUT_ORDERING -> ask(ReasonPrompt(
                "Left without ordering?", "The visit of $name ends here.", "Finish visit", reasonRequired = correction,
                reasonHint = reasonHint("Anything to add (optional)")
            ) { model.completeReservation(id, listOfNotNull("Left without ordering", it).joinToString(": ")) })
            BookingAction.HOLD_LONGER -> holdPrompt = true
            BookingAction.CONFIRM_ATTENDANCE -> ask(ReasonPrompt(
                "Attendance confirmed?", "$name said they're coming. The booking gets \"✓ Attendance confirmed\".", "Attendance confirmed",
                reasonRequired = correction, reasonHint = reasonHint("Note (optional), e.g. \"Called at 14:10\""),
                suggestions = listOf("Called the guest", "The guest called")
            ) { model.confirmAttendance(id, it) })
            BookingAction.NO_SHOW -> ask(ReasonPrompt(
                "Mark as no show?", "$name didn't come. The table becomes free, and it counts in the guest's history.", "Mark no show",
                danger = true, reasonRequired = correction, reasonHint = reasonHint()
            ) { model.markNoShow(id, it) })
            BookingAction.CANCEL -> ask(ReasonPrompt(
                "Cancel this booking?", "The booking for $name is cancelled and its table becomes free. You can reopen it later.", "Cancel booking",
                danger = true, reasonRequired = correction, reasonHint = reasonHint(),
                suggestions = listOf("Guest called to cancel", "Booked twice")
            ) { model.cancelReservation(id, it) })
            BookingAction.LEFT_BEFORE_SEATING -> ask(ReasonPrompt(
                "Left before being seated?", "The booking for $name is cancelled. Say what happened.", "Cancel booking",
                danger = true, reasonRequired = true, reasonHint = "What happened",
                suggestions = listOf("Left before being seated", "Waited too long")
            ) { model.cancelReservation(id, it) })
            BookingAction.REOPEN -> ask(ReasonPrompt(
                "Reopen this booking?", offer.note ?: "The booking for $name is back on. A table given to someone else meanwhile isn't taken back.",
                "Reopen", reasonRequired = correction, reasonHint = reasonHint()
            ) { model.reopenReservation(id, it) })
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
            onDismiss = { assigningTable = false; seatAfterPick = false },
            onPick = { picked ->
                assigningTable = false
                val seat = seatAfterPick
                seatAfterPick = false
                val current = reservation.tableAssignments.map { it.tableId }
                scope.launch {
                    if (picked.map { it.first } != current) {
                        model.patchReservation(
                            reservation.id,
                            UpdateReservationInput(tableIds = picked.map { it.first }, primaryTableId = picked.firstOrNull()?.first)
                        ).join()
                    }
                    if (seat && picked.isNotEmpty() && model.state.value.error == null) model.seatReservation(reservation.id)
                }
            }
        )
    }
    reasonPrompt?.let { prompt ->
        ReasonPromptDialog(prompt.copy(onConfirm = { reason -> reasonPrompt = null; prompt.onConfirm(reason) }), onDismiss = { reasonPrompt = null })
    }
    arrivedPrompt?.let { prompt ->
        ArrivedPromptDialog(prompt.copy(onConfirm = { count, reason -> arrivedPrompt = null; prompt.onConfirm(count, reason) }), onDismiss = { arrivedPrompt = null })
    }
    if (holdPrompt && reservation != null) {
        HoldLongerDialog(
            guestName = reservation.displayGuestName,
            endsAt = reservation.reservationEnd.localTime(),
            onConfirm = { minutes, reason -> holdPrompt = false; model.extendHold(reservation.id, minutes, reason) },
            onDismiss = { holdPrompt = false }
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
        val actions = bookingActions(reservation, policy, state::can, now)
        val menuActions = actions.menu

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
                            menuActions.forEach { offer ->
                                val tint = when {
                                    !offer.enabled -> FormMuted
                                    offer.action.danger -> FormDanger
                                    else -> FormInk
                                }
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(offer.action.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = tint)
                                            offer.note?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = FormMuted) }
                                        }
                                    },
                                    leadingIcon = { Icon(offer.action.icon(), null, Modifier.size(18.dp), tint = tint) },
                                    enabled = offer.enabled,
                                    onClick = {
                                        menuOpen = false
                                        start(offer)
                                    }
                                )
                            }
                        }
                    }
                }
                SquareIconButton(Icons.Outlined.Close, "Close", onClose)
            }
            StatusChip(reservation.status)
            BookingFlagChips(bookingFlags(reservation, policy, now))
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
                    occasions = state.occasions,
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
                PanelTab.INFO -> {
                reservation.reviewReason?.takeIf { reservation.needsReview }?.let { reason ->
                    NoticeCard(
                        "Needs review", ReviewColor,
                        "$reason. " + if (reservation.status == ReservationStatus.CHECKED_IN) "Seat them, or cancel with a reason if they left."
                        else "Finish the visit if they've left."
                    )
                }
                OccasionCard(reservation)
                reservation.guestNoShows?.takeIf { it >= policy.noShowWarningFrom }?.let {
                    GuestNoShowCard(model, reservation, canClear = state.can(ReservationsScreenModel.CORRECT_PERMISSION)) { prompt -> ask(prompt) }
                }
                LateSeatingCard(model, reservation, policy, now) { tableIds ->
                    model.patchReservation(reservation.id, UpdateReservationInput(tableIds = tableIds, primaryTableId = tableIds.firstOrNull()))
                }
                InfoCard {
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
                BookingMoneyCard(
                    model, reservation,
                    canManage = state.can(ReservationsScreenModel.WRITE_PERMISSION),
                    canGoodwill = state.can(ReservationsScreenModel.GOODWILL_PERMISSION),
                    now = now
                )
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
                    // Other changes kept with the status history: table held longer, arrived guests, tables released.
                    val otherChanges = state.timeline.filter { it.type !in setOf("CREATED", "STATUS_CHANGE", "TABLE_ASSIGNED", "NOTE_ADDED") }
                    if (state.statusHistory.isEmpty() && otherChanges.isEmpty()) {
                        Text("No status changes yet.", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
                    }
                    otherChanges.sortedByDescending { it.occurredAt }.forEach { event ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.padding(top = 5.dp).size(9.dp).background(WaitingColor, CircleShape))
                            Column {
                                Text(event.message ?: event.type.orEmpty(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
                                Text(
                                    listOfNotNull(event.occurredAt?.localDateLabel(), event.occurredAt?.localTime()).joinToString(" · "),
                                    fontFamily = Inter(), fontSize = 12.sp, color = FormMuted
                                )
                            }
                        }
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
            val primaryTable = reservation.primaryTable?.tableNumber
            if ((reservation.status == ReservationStatus.CHECKED_IN || reservation.status == ReservationStatus.SEATED) && primaryTable != null) {
                FooterButton("Go to table $primaryTable", Icons.Outlined.TableRestaurant, primary = false, modifier = Modifier.fillMaxWidth()) {
                    onGoToTable(primaryTable)
                }
            }
            val primary = actions.primary
            val secondary = actions.secondary
            if (primary != null || secondary != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    secondary?.let { offer ->
                        FooterButton(offer.action.label, offer.action.icon(), primary = false, modifier = Modifier.weight(1f)) { start(offer) }
                    }
                    primary?.let { offer ->
                        if (!offer.enabled) {
                            // e.g. "Check-in opens at 17:00" or "Only a manager can change this now".
                            Row(
                                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F6F2)).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                            ) {
                                Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = FormMuted)
                                Text(
                                    offer.note ?: offer.action.label,
                                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormMuted,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            val label = if (offer.action == BookingAction.SEAT && reservation.tableAssignments.isEmpty()) "Choose table and seat" else offer.action.label
                            FooterButton(label, offer.action.icon(), primary = true, modifier = Modifier.weight(1f)) { start(offer) }
                        }
                    }
                }
                primary?.takeIf { it.enabled && it.needsReason }?.note?.let {
                    Text(it, fontFamily = Inter(), fontSize = 12.sp, color = LateColor)
                }
            }
        }
    }
}

private fun BookingAction.icon(): ImageVector = when (this) {
    BookingAction.ACCEPT -> Icons.Outlined.CheckCircle
    BookingAction.DECLINE, BookingAction.CANCEL, BookingAction.LEFT_BEFORE_SEATING -> Icons.Outlined.Cancel
    BookingAction.GUEST_ARRIVED -> Icons.Outlined.HowToReg
    BookingAction.CONFIRM_ATTENDANCE -> Icons.Outlined.CheckCircle
    BookingAction.ARRIVED_COUNT -> Icons.Outlined.PersonOutline
    BookingAction.SEAT -> Icons.Outlined.TableRestaurant
    BookingAction.UNDO_SEAT, BookingAction.REOPEN -> Icons.Outlined.Replay
    BookingAction.FINISH, BookingAction.LEFT_WITHOUT_ORDERING -> Icons.Outlined.TaskAlt
    BookingAction.HOLD_LONGER -> Icons.Outlined.Schedule
    BookingAction.NO_SHOW -> Icons.Outlined.PersonOff
}

private fun ReservationStatus.panelLabel(): String = when (this) {
    ReservationStatus.PENDING -> "Pending"
    ReservationStatus.CONFIRMED -> "Confirmed"
    ReservationStatus.CHECKED_IN -> "Checked in"
    ReservationStatus.SEATED -> "Seated"
    ReservationStatus.COMPLETED -> "Completed"
    ReservationStatus.CANCELLED -> "Cancelled"
    ReservationStatus.NO_SHOW -> "No show"
    ReservationStatus.EXPIRED -> "Request expired"
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
    onSave: (UpdateReservationInput?, notesToAdd: List<String>, noteIdsToDelete: Set<String>) -> Unit,
    occasions: List<com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationOccasion> = emptyList()
) {
    var occasionCode by remember(reservation.id) { mutableStateOf(reservation.occasionCode) }
    var occasionOptions by remember(reservation.id) { mutableStateOf(reservation.occasionOptions) }
    var occasionNote by remember(reservation.id) { mutableStateOf(reservation.occasionNote.orEmpty()) }
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
        // Occasions no longer offered stay listed on the booking, so show the picker only with the current list.
        if (occasions.isNotEmpty()) {
            FormField("Occasion", optional = true) {
                OccasionPicker(occasions, occasionCode, occasionOptions, occasionNote) { code, options, text ->
                    occasionCode = code
                    occasionOptions = options
                    occasionNote = text
                }
            }
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
                                    contactEmail = email.trim(),
                                    // Only when it changed; an empty code removes it.
                                    occasionCode = (occasionCode ?: "").takeIf {
                                        occasionCode != reservation.occasionCode || occasionOptions != reservation.occasionOptions ||
                                            occasionNote.trim() != reservation.occasionNote.orEmpty()
                                    },
                                    occasionOptions = occasionOptions,
                                    occasionNote = occasionNote.trim()
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
        ReservationStatus.EXPIRED -> "Request expired"
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

private enum class EditSection { INFO, NOTES }

// "⚠ 2 no-shows before": when and which bookings, and (for managers) clearing the warning with a reason.
@Composable
private fun GuestNoShowCard(model: ReservationsScreenModel, reservation: Reservation, canClear: Boolean, ask: (ReasonPrompt) -> Unit) {
    var history by remember(reservation.id, reservation.guestNoShows) { mutableStateOf<com.saporini.mobile_desktop.pos.reservations.domain.model.GuestHistory?>(null) }
    LaunchedEffect(reservation.id, reservation.guestNoShows) { history = model.guestHistory(reservation.id).getOrNull() }
    val count = reservation.guestNoShows ?: 0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ReviewColor.copy(alpha = 0.08f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "⚠ $count ${if (count == 1) "no-show" else "no-shows"} before",
            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ReviewColor
        )
        history?.noShows?.take(5)?.forEach { noShow ->
            Text(
                listOfNotNull(noShow.reservationStart?.localDateLabel(), noShow.reservationStart?.localTime(), noShow.partySize?.let { "$it guests" })
                    .joinToString(" · "),
                fontFamily = Inter(), fontSize = 12.sp, color = FormInk
            )
        }
        if (canClear) {
            Text(
                "Clear warning",
                Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                    ask(ReasonPrompt(
                        "Clear the no-show warning?",
                        "The warning stops showing on this guest's bookings. The no-shows stay in the history.",
                        "Clear warning", reasonRequired = true, reasonHint = "Why, e.g. \"Explained, family emergency\""
                    ) { reason -> if (reason != null) model.clearNoShowWarning(reservation.id, reason) })
                }.padding(vertical = 4.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen
            )
        }
    }
}

@Composable
private fun NoticeCard(title: String, color: Color, message: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.10f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (color == HoldEndsColor) Color(0xFF8A6A00) else color)
        Text(message, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = FormInk)
    }
}

// Late guests keep their end time. Once they're late, show whether they still fit at their table until then, or
// which tables are free for the rest of their visit.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LateSeatingCard(
    model: ReservationsScreenModel,
    reservation: Reservation,
    policy: com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationPolicy,
    now: Instant,
    onMove: (List<String>) -> Unit
) {
    val start = runCatching { Instant.parse(reservation.reservationStart) }.getOrNull() ?: return
    val waiting = reservation.status == ReservationStatus.CONFIRMED || reservation.status == ReservationStatus.CHECKED_IN
    val late = (now - start).inWholeMinutes >= policy.lateAfterMinutes
    if (!waiting || !late) return
    var check by remember(reservation.id, reservation.tableAssignments) { mutableStateOf<com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationSeatingCheck?>(null) }
    LaunchedEffect(reservation.id, reservation.tableAssignments, reservation.status) {
        check = model.seatingCheck(reservation.id).getOrNull()
    }
    val result = check ?: return
    val left = "${duration(result.minutesLeft)} left"
    when {
        reservation.tableAssignments.isEmpty() && result.alternatives.isEmpty() -> NoticeCard(
            "No table free for the rest of the visit", ReviewColor, "$left of the booking. Decide with the guests: a shorter stay, or wait."
        )
        result.fitsAtTables -> NoticeCard(
            "Still fits at the table", WaitingColor,
            listOfNotNull(
                left,
                result.nextBookingStart?.let { "next booking at this table at ${it.localTime()}" + (result.nextBookingName?.let { name -> " ($name)" } ?: "") }
            ).joinToString(" · ").replaceFirstChar { it.uppercase() }
        )
        else -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(LateColor.copy(alpha = 0.10f)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (reservation.tableAssignments.isEmpty()) "Pick a table for the rest of the visit" else "Doesn't fit at the table until the end",
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LateColor
            )
            Text(
                listOfNotNull(left, result.nextBookingStart?.let { "next booking there at ${it.localTime()}" }).joinToString(" · ")
                    .replaceFirstChar { it.uppercase() } + if (result.alternatives.isEmpty()) ". No other table is free: decide with the guests." else ". Free until then:",
                fontFamily = Inter(), fontSize = 12.sp, color = FormInk
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                result.alternatives.forEach { option ->
                    Surface(onClick = { onMove(option.tableIds) }, shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, FormBorder)) {
                        Text(
                            "Move to ${option.tableNumbers.joinToString(" + ")}" + (option.totalCapacity?.let { " · $it seats" } ?: ""),
                            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormInk
                        )
                    }
                }
            }
        }
    }
}

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
