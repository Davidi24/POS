package com.saporini.mobile_desktop.pos.reservations

import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlinx.datetime.daysUntil
import androidx.compose.material.icons.outlined.Info
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationRules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationAvailabilityOption
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationInput
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationsScreenModel
import com.saporini.mobile_desktop.pos.tables.ui.ReservationTablePicker
import com.saporini.mobile_desktop.pos.tables.ui.TableNotification
import com.saporini.mobile_desktop.pos.tables.ui.TableNotificationQueue
import com.saporini.mobile_desktop.pos.tables.ui.TableNotificationTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.minus

internal val FormGreen = Color(0xFF4F7942)
internal val FormGreenSoft = Color(0xFFEEF3EB)
internal val FormInk = Color(0xFF222426)
internal val FormMuted = Color(0xFF747572)
internal val FormPlaceholder = Color(0xFF9A9B97)
internal val FormBorder = Color(0xFFE3DED8)
internal val FormDanger = Color(0xFFB13A2F)
private val FormWidth = 640.dp
private val FormWidthWithMap = 460.dp
private val FormExpandedWidth = 1400.dp
private const val SUGGESTIONS_SHOWN = 5
private const val SUGGESTIONS_FETCHED = 30
internal const val NOTE_LIMIT = 500
internal const val NAME_LIMIT = 150
internal const val PHONE_LIMIT = 50
internal const val EMAIL_LIMIT = 150

// Same rules as the server, so a form never sends something it will refuse.
private val PhoneChars = Regex("^\\+?[0-9 ()./-]*$")
private val EmailShape = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

internal fun nameProblem(name: String): String? = when {
    name.isBlank() -> "Customer name is required."
    name.trim().length < 2 -> "Use at least 2 characters."
    else -> null
}

internal fun phoneProblem(phone: String): String? {
    val value = phone.trim()
    return when {
        value.isEmpty() -> null
        !value.matches(PhoneChars) -> "Use only digits, spaces and + ( ) - . /"
        value.count(Char::isDigit) < 5 -> "Enter a full phone number."
        else -> null
    }
}

internal fun emailProblem(email: String): String? {
    val value = email.trim()
    return when {
        value.isEmpty() -> null
        !value.matches(EmailShape) || value.substringBefore('@').length > 64 -> "Enter a valid email."
        else -> null
    }
}

// Drops characters a phone number can't contain, as the user types.
internal fun cleanPhoneInput(raw: String): String =
    raw.filterIndexed { index, char -> char.isDigit() || char in " ()./-" || (char == '+' && index == 0) }.take(PHONE_LIMIT)

private const val STEP_WHEN = 0
private const val STEP_TABLE = 1
private const val STEP_GUEST = 2
private const val STEP_REVIEW = 3
private val StepLabels = listOf("When", "Table", "Guest", "Review")

private enum class TableMode { SUGGEST, MANUAL }

// Quarter-hour slots inside the calendar's opening window.
// Time within the service day in minutes from midnight; 06:00 = 360, 01:30 the next night = 1530.
@kotlin.jvm.JvmInline
internal value class ServiceTime(val minutes: Int) : Comparable<ServiceTime> {
    override fun compareTo(other: ServiceTime): Int = minutes.compareTo(other.minutes)
    fun label(): String = "${((minutes / 60) % 24).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
    fun toMinutes(): Int = minutes
    fun at(date: LocalDate): LocalDateTime {
        val day = if (minutes >= 24 * 60) date.plus(DatePeriod(days = 1)) else date
        val inDay = minutes % (24 * 60)
        return LocalDateTime(day, LocalTime(inDay / 60, inDay % 60))
    }
}

// Quarter-hour slots from 06:00 until 02:00 the next night.
internal val TimeSlots: List<ServiceTime> = (6 * 60..26 * 60 step 15).map { ServiceTime(it) }
internal val StartSlots: List<ServiceTime> = TimeSlots.dropLast(1)

// Start slots on [date] that haven't passed yet.
internal fun openStartSlots(date: LocalDate, now: Instant, zone: TimeZone): List<ServiceTime> =
    StartSlots.filter { it.at(date).toInstant(zone) >= now }

// A new booking starts at the next free quarter-hour today (or 19:00 on a later day), never in the past.
private fun defaultWhen(initialDate: LocalDate, now: Instant, zone: TimeZone): Pair<LocalDate, ServiceTime> {
    var day = maxOf(initialDate, now.toLocalDateTime(zone).date.minus(DatePeriod(days = 1)))
    while (true) {
        val open = openStartSlots(day, now, zone)
        if (open.isNotEmpty()) {
            // A day that hasn't started yet opens at the usual 19:00 dinner slot.
            return day to if (open.size == StartSlots.size) ServiceTime(19 * 60) else open.first()
        }
        day = day.plus(DatePeriod(days = 1))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateReservationDialog(
    model: ReservationsScreenModel,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onCreated: (LocalDate) -> Unit
) {
    val zone = RestaurantTime.zone
    val modelState by model.state.collectAsState()
    // Only tables on the floor plan can be offered; the server also knows tables on floors the app doesn't show.
    val planTableIds = remember(modelState.tableGroups) {
        modelState.tableGroups.flatMap { group -> group.tables.map { it.id } }.toSet()
    }
    val scope = rememberCoroutineScope()
    var guests by remember { mutableIntStateOf(2) }
    val initialWhen = remember { defaultWhen(initialDate, Clock.System.now(), zone) }
    var date by remember { mutableStateOf(initialWhen.first) }
    var start by remember { mutableStateOf(initialWhen.second) }
    // The booking length comes from settings: 2 h, or 2 h 15 for groups of 5 or more.
    val rules = modelState.rules
    val policy = modelState.policy
    var end by remember { mutableStateOf(TimeSlots.firstOrNull { it.toMinutes() >= initialWhen.second.toMinutes() + policy.bookingMinutes(rules, 2) } ?: TimeSlots.last()) }
    // Once staff pick an end time themselves, the group size no longer changes it.
    var endTouched by remember { mutableStateOf(false) }
    LaunchedEffect(guests, start, rules, policy) {
        if (endTouched) return@LaunchedEffect
        end = TimeSlots.firstOrNull { it.toMinutes() >= start.toMinutes() + policy.bookingMinutes(rules, guests) } ?: TimeSlots.last()
    }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var noteTags by remember { mutableStateOf<List<String>>(emptyList()) }
    // Booked by phone for the next 24 hours: the guest just said they're coming (✓ Attendance confirmed).
    var confirmedOnPhone by remember { mutableStateOf(true) }
    var tableMode by remember { mutableStateOf(TableMode.SUGGEST) }
    var options by remember { mutableStateOf<List<ReservationAvailabilityOption>>(emptyList()) }
    var optionsLoading by remember { mutableStateOf(false) }
    var showAllSuggestions by remember { mutableStateOf(false) }
    var chosenSuggestion by remember { mutableIntStateOf(0) }
    var freeTableIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var manualTables by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var step by remember { mutableIntStateOf(0) }
    var nearestFreeStarts by remember { mutableStateOf<List<ServiceTime>>(emptyList()) }
    var searchingNearest by remember { mutableStateOf(false) }
    var confirmNoTable by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var notificationSequence by remember { mutableIntStateOf(0) }
    var notifications by remember { mutableStateOf<List<TableNotification>>(emptyList()) }

    fun notify(message: String, tone: TableNotificationTone) {
        notificationSequence += 1
        notifications = listOf(TableNotification(notificationSequence, message, tone))
    }

    val startIso = start.at(date).toInstant(zone).toString()
    val endIso = end.at(date).toInstant(zone).toString()
    val timesValid = end > start
    val nowInstant = Clock.System.now()
    val startsInPast = start.at(date).toInstant(zone) < nowInstant
    val soonBooking = start.at(date).toInstant(zone) - nowInstant < kotlin.time.Duration.parse("24h")

    LaunchedEffect(guests, date, start, end, planTableIds) {
        if (!timesValid) return@LaunchedEffect
        delay(250)
        optionsLoading = true
        model.tableSuggestions(startIso, endIso, guests, SUGGESTIONS_FETCHED)
            .onSuccess { found ->
                options = if (planTableIds.isEmpty()) found else found.filter { option -> option.tableIds.all { it in planTableIds } }
                chosenSuggestion = 0
            }
            .onFailure { options = emptyList() }
        model.freeTableIds(startIso, endIso).onSuccess { free ->
            freeTableIds = free
            manualTables = manualTables.filter { it.first in free }
        }
        optionsLoading = false
    }

    // When the chosen time is full, look for the closest start times (same length) where a table fits.
    LaunchedEffect(options, optionsLoading, start, end, guests, date) {
        nearestFreeStarts = emptyList()
        if (optionsLoading || options.isNotEmpty() || !timesValid) return@LaunchedEffect
        searchingNearest = true
        val length = end.toMinutes() - start.toMinutes()
        val candidates = listOf(30, -30, 60, -60, 90, -90, 120, -120)
            .map { start.toMinutes() + it }
            .filter { it >= TimeSlots.first().toMinutes() && it + length <= TimeSlots.last().toMinutes() }
        val found = mutableListOf<ServiceTime>()
        for (minutes in candidates) {
            if (found.size >= 3) break
            val candidateStart = ServiceTime(minutes)
            val candidateEnd = ServiceTime(minutes + length)
            val fits = model.tableSuggestions(
                candidateStart.at(date).toInstant(zone).toString(),
                candidateEnd.at(date).toInstant(zone).toString(),
                guests,
                SUGGESTIONS_FETCHED
            ).getOrNull().orEmpty().any { option -> planTableIds.isEmpty() || option.tableIds.all { it in planTableIds } }
            if (fits) found += candidateStart
        }
        nearestFreeStarts = found.sorted()
        searchingNearest = false
    }

    val nameError = nameProblem(name)
    val phoneError = phoneProblem(phone)
    val emailError = emailProblem(email)
    val timeError = when {
        !timesValid -> "End time must be after the start time."
        startsInPast -> "This time has already passed. Pick a later time or another day."
        else -> null
    }
    val suggestion = options.getOrNull(chosenSuggestion)
    val chosenTableIds: List<String> = when (tableMode) {
        TableMode.SUGGEST -> suggestion?.tableIds.orEmpty()
        TableMode.MANUAL -> manualTables.map { it.first }
    }
    val chosenPrimary = when (tableMode) {
        TableMode.SUGGEST -> suggestion?.primaryTableId
        TableMode.MANUAL -> manualTables.firstOrNull()?.first
    } ?: chosenTableIds.firstOrNull()
    fun stepValid(target: Int): Boolean = when (target) {
        STEP_WHEN -> timeError == null
        STEP_GUEST -> nameError == null && phoneError == null && emailError == null
        else -> true
    }
    val chosenOption = suggestion
    val tableSummary = when {
        tableMode == TableMode.MANUAL && manualTables.isNotEmpty() -> "Your pick · " + manualTables.joinToString(" + ") { it.second }
        tableMode == TableMode.SUGGEST && chosenOption != null ->
            (if (chosenSuggestion == 0) "Best fit" else "Suggestion") +
                " · ${chosenOption.tableNumbers.joinToString(" + ")} · ${chosenOption.capacityLabel()}"
        else -> "Unassigned (no table yet)"
    }

    fun goTo(target: Int) {
        val firstInvalid = (STEP_WHEN until target).firstOrNull { !stepValid(it) }
        if (firstInvalid != null) {
            attempted = true
            step = firstInvalid
            return
        }
        step = target.coerceIn(STEP_WHEN, STEP_REVIEW)
    }

    fun submit() {
        attempted = true
        if (nameError != null || phoneError != null || emailError != null || timeError != null || saving) return
        saving = true
        saveError = null
        scope.launch {
            model.submitReservation(
                ReservationInput(
                    partySize = guests,
                    reservationStart = startIso,
                    reservationEnd = endIso,
                    contactName = name.trim(),
                    contactPhone = phone.trim().ifBlank { null },
                    contactEmail = email.trim().ifBlank { null },
                    specialRequests = combinedNote(noteTags, note),
                    initialTableIds = chosenTableIds.ifEmpty { null },
                    primaryTableId = chosenPrimary,
                    attendanceConfirmed = confirmedOnPhone.takeIf { soonBooking }
                )
            ).onSuccess { onCreated(date) }
                .onFailure { saveError = it.message ?: "Could not create the reservation." }
            saving = false
        }
    }

    // The floor plan picker, shown beside the form on wide screens and inside it on narrow ones.
    val tablePicker: @Composable (Modifier) -> Unit = { outer ->
        Box(
            outer.clip(RoundedCornerShape(12.dp)).background(Color.White)
                .border(1.dp, FormBorder, RoundedCornerShape(12.dp)).padding(8.dp)
        ) {
            val shownSuggestions = if (showAllSuggestions) options else options.take(SUGGESTIONS_SHOWN)
            ReservationTablePicker(
                freeTableIds = freeTableIds,
                selectedTableIds = chosenTableIds.toSet(),
                otherSuggestions = if (tableMode == TableMode.SUGGEST) {
                    shownSuggestions.filterIndexed { index, _ -> index != chosenSuggestion }.map { it.tableIds.toSet() }
                } else {
                    emptyList()
                },
                onTableClick = { id, label, isFree ->
                    val suggestionIndex = shownSuggestions.indexOfFirst { id in it.tableIds }
                    when {
                        id in chosenTableIds && tableMode == TableMode.MANUAL -> {
                            manualTables = manualTables.filterNot { it.first == id }
                            notify("Table $label removed from your pick", TableNotificationTone.Info)
                        }
                        id in chosenTableIds -> notify("Table $label is already chosen", TableNotificationTone.Info)
                        !isFree -> notify("Table $label is already reserved at this time", TableNotificationTone.Error)
                        tableMode != TableMode.MANUAL && suggestionIndex >= 0 -> {
                            tableMode = TableMode.SUGGEST
                            chosenSuggestion = suggestionIndex
                            notify("Suggestion ${shownSuggestions[suggestionIndex].tableNumbers.joinToString(" + ")} chosen", TableNotificationTone.Success)
                        }
                        tableMode != TableMode.MANUAL -> {
                            manualTables = listOf(id to label)
                            tableMode = TableMode.MANUAL
                            notify("Table $label picked", TableNotificationTone.Success)
                        }
                        else -> {
                            manualTables = manualTables + (id to label)
                            notify("Table $label added to your pick", TableNotificationTone.Success)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )        }
    }
    val mapLegend: @Composable (Modifier) -> Unit = { outer ->
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(outer, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendItem(Color(0xFF147A25), "Free at this time")
            LegendItem(Color(0xFFAA3F38), "Already reserved at this time")
            LegendOutline(Color(0xFF242424), "Chosen")
            if (tableMode == TableMode.SUGGEST) LegendOutline(FormGreen, "Other suggestion")
        }
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)).padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            // Narrow windows (phones, small tablets) keep the map inside the form instead of beside it.
            val narrow = maxWidth < 760.dp
            val expanded = step == STEP_TABLE && !narrow
            val tall = expanded || (narrow && step == STEP_TABLE)
            val formWidth by animateDpAsState(
                targetValue = minOf(maxWidth, if (expanded) FormWidthWithMap else FormWidth),
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "reservation-form-panel-width"
            )
            val availableHeight = maxHeight
            val dialogWidth by animateDpAsState(
                targetValue = if (expanded) minOf(maxWidth, FormExpandedWidth) else minOf(maxWidth, FormWidth),
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "reservation-form-width"
            )
            Box(
                Modifier.width(dialogWidth)
                    .then(if (tall) Modifier.height(availableHeight * 0.94f) else Modifier.heightIn(max = availableHeight))
                    .shadow(22.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                    .background(Color.White).border(1.dp, FormBorder, RoundedCornerShape(16.dp))
            ) {
                Row(Modifier.heightIn(max = availableHeight)) {
                    Column(Modifier.width(formWidth).then(if (tall) Modifier.fillMaxHeight() else Modifier)) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Create reservation", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = FormInk)
                            if (!expanded) CloseButton { if (!saving) onDismiss() }
                        }
                        StepBar(
                            compact = narrow,
                            current = step,
                            canOpen = { target -> target <= step || (STEP_WHEN until target).all(::stepValid) },
                            onOpen = { target -> goTo(target) }
                        )
                        Column(
                            Modifier.weight(1f, fill = tall).verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            when (step) {
                                STEP_WHEN -> {
                                    val guestsField: @Composable (Modifier) -> Unit = { fieldModifier ->
                                FormField("Guests", required = true, modifier = fieldModifier) {
                                    DropdownBox(Icons.Outlined.PersonOutline, "$guests", guestChoices(rules)) { guests = it.toInt() }
                                }
                                    }
                                    val dateField: @Composable (Modifier) -> Unit = { fieldModifier ->
                                FormField("Date", required = true, modifier = fieldModifier) {
                                    DateBox(date) { picked ->
                                        date = picked
                                        // Moving to today can put the start in the past; jump to the next free time.
                                        val open = openStartSlots(picked, Clock.System.now(), zone)
                                        if (open.isNotEmpty() && start < open.first()) {
                                            val length = end.toMinutes() - start.toMinutes()
                                            start = open.first()
                                            end = TimeSlots.firstOrNull { it.toMinutes() >= start.toMinutes() + length.coerceAtLeast(15) } ?: TimeSlots.last()
                                        }
                                    }
                                }
                                    }
                                    val startField: @Composable (Modifier) -> Unit = { fieldModifier ->
                                FormField("Start time", required = true, modifier = fieldModifier) {
                                    val startChoices = openStartSlots(date, nowInstant, zone).ifEmpty { StartSlots }
                                    DropdownBox(Icons.Outlined.AccessTime, start.label(), startChoices.map { it.label() }) { picked ->
                                        val newStart = startChoices.first { it.label() == picked }
                                        val length = end.toMinutes() - start.toMinutes()
                                        start = newStart
                                        end = TimeSlots.firstOrNull { it.toMinutes() >= newStart.toMinutes() + length.coerceAtLeast(15) }
                                            ?: TimeSlots.last()
                                    }
                                }
                                    }
                                    val endField: @Composable (Modifier) -> Unit = { fieldModifier ->
                                FormField("End time", required = true, modifier = fieldModifier) {
                                    DropdownBox(
                                        Icons.Outlined.AccessTime, end.label(),
                                        TimeSlots.filter { it > start }.map { it.label() },
                                        isError = attempted && timeError != null
                                    ) { picked ->
                                        end = TimeSlots.first { it > start && it.label() == picked }
                                        endTouched = true
                                    }
                                }
                                    }
                                    if (narrow) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            guestsField(Modifier.width(96.dp)); dateField(Modifier.weight(1f))
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            startField(Modifier.weight(1f)); endField(Modifier.weight(1f))
                                        }
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            guestsField(Modifier.width(96.dp)); dateField(Modifier.weight(1f))
                                            startField(Modifier.width(120.dp)); endField(Modifier.width(120.dp))
                                        }
                                    }
                                    if (attempted && timeError != null) ErrorText(timeError)
                                    ruleWarnings(rules, guests, date, nowInstant.toLocalDateTime(zone).date).forEach { RuleWarning(it) }
                                }
                                STEP_GUEST -> {
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                        maxItemsInEachRow = if (narrow) 1 else 2
                                    ) {
                                        FormField("Customer name", required = true, modifier = Modifier.weight(1f)) {
                                            InputBox(Icons.Outlined.PersonOutline, name, "Full name (e.g. Isabella Conti)", isError = attempted && nameError != null) { name = it.take(NAME_LIMIT) }
                                            if (attempted && nameError != null) ErrorText(nameError)
                                        }
                                        FormField("Phone", modifier = Modifier.weight(1f)) {
                                            InputBox(Icons.Outlined.Phone, phone, "+1 234 567 8900", keyboardType = KeyboardType.Phone, isError = attempted && phoneError != null) { phone = cleanPhoneInput(it) }
                                            if (attempted && phoneError != null) ErrorText(phoneError)
                                        }
                                    }
                                    FormField("Email") {
                                        InputBox(Icons.Outlined.Email, email, "email@example.com", keyboardType = KeyboardType.Email, isError = attempted && emailError != null) { email = it.trim().take(EMAIL_LIMIT) }
                                        if (attempted && emailError != null) ErrorText(emailError)
                                    }
                                    FormField("Note", optional = true) {
                                        NoteSuggestionChips(selected = noteTags, onToggle = { tag ->
                                            noteTags = if (tag in noteTags) noteTags - tag else noteTags + tag
                                        })
                                        NoteBox(note, "Anything else? Write your own note…") { note = it.take(NOTE_LIMIT) }
                                    }
                                    if (soonBooking) {
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { confirmedOnPhone = !confirmedOnPhone }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            androidx.compose.material3.Checkbox(
                                                checked = confirmedOnPhone, onCheckedChange = { confirmedOnPhone = it },
                                                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = FormGreen)
                                            )
                                            Column {
                                                Text("The guest confirmed they're coming", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
                                                Text("Marks \"✓ Attendance confirmed\", so nobody has to call them again", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
                                            }
                                        }
                                    }
                                }
                                STEP_TABLE -> {
                                    Text(
                                        "$guests ${if (guests == 1) "guest" else "guests"} · ${date.formLabel()} · ${start.label()} – ${end.label()}",
                                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormMuted
                                    )
                                    FormField("Best fit") {
                                        BestFitCard(
                                            option = options.firstOrNull(),
                                            selected = tableMode == TableMode.SUGGEST && chosenSuggestion == 0,
                                            loading = optionsLoading,
                                            timesValid = timesValid,
                                            onClick = {
                                                tableMode = TableMode.SUGGEST
                                                chosenSuggestion = 0
                                            },
                                            timeLabel = "${start.label()} – ${end.label()}",
                                            nearestFreeStarts = nearestFreeStarts,
                                            searchingNearest = searchingNearest,
                                            onPickTime = { newStart ->
                                                val length = end.toMinutes() - start.toMinutes()
                                                val endMinutes = newStart.toMinutes() + length
                                                start = newStart
                                                end = ServiceTime(endMinutes)
                                                notify("Moved to ${start.label()} – ${end.label()}", TableNotificationTone.Success)
                                            }
                                        )
                                    }
                                    if (options.size > 1 || optionsLoading) {
                                        FormField("Other suggestions") {
                                            val others = options.drop(1)
                                            SuggestionList(
                                                options = if (showAllSuggestions) others else others.take(SUGGESTIONS_SHOWN - 1),
                                                chosen = if (tableMode == TableMode.SUGGEST) chosenSuggestion - 1 else -1,
                                                loading = optionsLoading,
                                                canShowMore = !showAllSuggestions && others.size > SUGGESTIONS_SHOWN - 1,
                                                onChoose = { index ->
                                                    tableMode = TableMode.SUGGEST
                                                    chosenSuggestion = index + 1
                                                },
                                                onShowMore = { showAllSuggestions = true }
                                            )
                                        }
                                    }
                                    if (tableMode == TableMode.MANUAL) {
                                        ManualTables(
                                            tables = manualTables,
                                            onRemove = { id -> manualTables = manualTables.filterNot { it.first == id } },
                                            onBackToSuggestions = {
                                                tableMode = TableMode.SUGGEST
                                                chosenSuggestion = 0
                                            }
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Outlined.Map, null, Modifier.size(16.dp), tint = FormMuted)
                                        Text(
                                            "Or choose the table you think is best on the map.",
                                            fontFamily = Inter(), fontSize = 12.sp, color = FormMuted
                                        )
                                    }
                                    if (narrow) {
                                        tablePicker(Modifier.fillMaxWidth().height(340.dp))
                                        mapLegend(Modifier.fillMaxWidth())
                                    }
                                }
                                else -> ReviewSummary(
                                    name = name.trim(),
                                    phone = phone.trim(),
                                    email = email.trim(),
                                    guests = guests,
                                    dateLabel = date.formLabel(),
                                    timeLabel = "${start.label()} – ${end.label()}",
                                    tableLabel = tableSummary,
                                    note = combinedNote(noteTags, note).orEmpty(),
                                    onEdit = { target -> goTo(target) }
                                )
                            }
                            saveError?.let { ErrorText(it) }
                            Spacer(Modifier.height(2.dp))
                        }
                        HorizontalDivider(color = FormBorder)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                onClick = {
                                    if (saving) return@Surface
                                    if (step == STEP_WHEN) onDismiss() else goTo(step - 1)
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, FormBorder)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(if (step == STEP_WHEN) "Cancel" else "Back", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = FormInk)
                                }
                            }
                            Surface(
                                onClick = {
                                    when {
                                        step == STEP_REVIEW -> submit()
                                        step == STEP_TABLE && chosenTableIds.isEmpty() && !optionsLoading -> confirmNoTable = true
                                        else -> goTo(step + 1)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = FormGreen
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (saving) {
                                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text(
                                            if (step == STEP_REVIEW) "Create reservation" else "Next",
                                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = expanded,
                        modifier = Modifier.weight(1f, fill = false),
                        enter = fadeIn(tween(260, delayMillis = 160)),
                        exit = fadeOut(tween(140))
                    ) {
                        Row(Modifier.fillMaxHeight()) {
                            VerticalDivider(color = FormBorder)
                            Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F5))) {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            when (tableMode) {
                                                TableMode.SUGGEST -> "Best fit and other suggestions"
                                                TableMode.MANUAL -> "Your pick"
                                            },
                                            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = FormInk
                                        )
                                        Text(
                                            "Dark outline is the chosen table. Tap another suggestion or any free table to change it.",
                                            fontFamily = Inter(), fontSize = 12.sp, color = FormMuted
                                        )
                                    }
                                    CloseButton { if (!saving) onDismiss() }
                                }
                                HorizontalDivider(color = FormBorder)
                                tablePicker(Modifier.weight(1f).fillMaxWidth().padding(10.dp))
                                mapLegend(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
                            }
                        }
                    }
                }

                // One notification at a time: a new one swaps in over the previous one.
                AnimatedContent(
                    targetState = notifications.lastOrNull(),
                    contentKey = { it?.id },
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 80)) + slideInVertically(tween(260)) { it / 3 }) togetherWith
                            (fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 3 })
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                    label = "reservation-notification"
                ) { current ->
                    if (current != null) {
                        TableNotificationQueue(
                            notifications = listOf(current),
                            onDismiss = { id -> notifications = notifications.filterNot { it.id == id } }
                        )
                    }
                }
            }
        }
    }
    if (confirmNoTable) {
        NoTableConfirmDialog(
            timeLabel = "${start.label()} – ${end.label()}",
            onContinue = {
                confirmNoTable = false
                goTo(STEP_GUEST)
            },
            onCancel = { confirmNoTable = false }
        )
    }
}

@Composable
internal fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    optional: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
            if (required) Text(" *", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormDanger)
            if (optional) Text(" (optional)", fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
        }
        content()
    }
}

@Composable
private fun FieldFrame(
    isError: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
            .border(1.dp, if (isError) FormDanger else FormBorder, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
internal fun DropdownBox(
    icon: ImageVector,
    value: String,
    choices: List<String>,
    isError: Boolean = false,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FieldFrame(isError = isError, onClick = { expanded = true }) {
            Icon(icon, null, Modifier.size(18.dp), tint = FormInk)
            Text(value, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk, maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = FormInk)
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.background(Color.White).heightIn(max = 280.dp)) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            choice, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = if (choice == value) FormGreen else FormInk
                        )
                    },
                    onClick = {
                        onSelect(choice)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun DateBox(date: LocalDate, onSelect: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FieldFrame(onClick = { open = true }) {
            Icon(Icons.Outlined.CalendarToday, null, Modifier.size(18.dp), tint = FormInk)
            Text(date.formLabel(), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk, maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = FormInk)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 6.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = Color.White,
            shadowElevation = 8.dp
        ) {
            CompactDatePicker(selectedDate = date, onDateSelected = {
                onSelect(it)
                open = false
            })
        }
    }
}

@Composable
internal fun InputBox(
    icon: ImageVector,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    onChange: (String) -> Unit
) {
    FieldFrame(isError = isError) {
        Icon(icon, null, Modifier.size(18.dp), tint = FormInk)
        BasicTextField(
            value = value,
            // Pasted line breaks or tabs become spaces so one-line fields stay one line.
            onValueChange = { onChange(it.replace(Regex("[\\r\\n\\t]+"), " ")) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = FormInk),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, fontFamily = Inter(), fontSize = 13.sp, color = FormPlaceholder, maxLines = 1)
                inner()
            }
        )
    }
}

@Composable
internal fun NoteBox(value: String, placeholder: String, onChange: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White)
            .border(1.dp, FormBorder, RoundedCornerShape(8.dp)).padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Notes, null, Modifier.size(18.dp), tint = FormInk)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                textStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = FormInk),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, fontFamily = Inter(), fontSize = 13.sp, color = FormPlaceholder)
                    inner()
                }
            )
        }
        Text("${value.length}/$NOTE_LIMIT", Modifier.align(Alignment.End), fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
    }
}

@Composable
private fun BestFitCard(
    option: ReservationAvailabilityOption?,
    selected: Boolean,
    loading: Boolean,
    timesValid: Boolean,
    onClick: () -> Unit,
    timeLabel: String,
    nearestFreeStarts: List<ServiceTime>,
    searchingNearest: Boolean,
    onPickTime: (ServiceTime) -> Unit
) {
    if (timesValid && !loading && option == null) {
        NoFreeTableCard(timeLabel, nearestFreeStarts, searchingNearest, onPickTime)
        return
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) FormGreenSoft else Color(0xFFF7F7F5))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) FormGreen else FormBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = option != null, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp), tint = FormGreen)
        when {
            !timesValid -> Text("Pick a valid time to find a table.", fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
            loading -> LoadingText("Finding the best table…")
            option == null -> Text(
                "No free table fits this time. Pick one on the map, or it will be saved as Unassigned.",
                fontFamily = Inter(), fontSize = 13.sp, color = FormMuted
            )
            else -> Column(Modifier.weight(1f)) {
                Text("Best fit: ${option.tableNumbers.joinToString(" + ")}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
                Text(option.capacityLabel(), fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
            }
        }
        if (selected && option != null) {
            Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = FormGreen)
        }
    }
}

@Composable
private fun SuggestionList(
    options: List<ReservationAvailabilityOption>,
    chosen: Int,
    loading: Boolean,
    canShowMore: Boolean,
    onChoose: (Int) -> Unit,
    onShowMore: () -> Unit
) {
    when {
        loading -> InfoRow(Icons.Outlined.AutoAwesome) { LoadingText("Finding suggestions…") }
        options.isEmpty() -> InfoRow(Icons.Outlined.AutoAwesome) {
            Text("No free tables fit this time. Try another time or pick tables manually.", fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { index, option ->
                val selected = index == chosen
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (selected) FormGreenSoft else Color.White)
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) FormGreen else FormBorder, RoundedCornerShape(8.dp))
                        .clickable { onChoose(index) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(16.dp).clip(CircleShape)
                            .border(if (selected) 5.dp else 1.5.dp, if (selected) FormGreen else FormPlaceholder, CircleShape)
                    )
                    Icon(Icons.Outlined.TableRestaurant, null, Modifier.size(18.dp), tint = FormInk)
                    Text(option.tableNumbers.joinToString(" + "), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
                    Text(option.capacityLabel(), fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
                }
            }
            if (canShowMore) {
                Text(
                    "Show more suggestions",
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onShowMore).padding(horizontal = 6.dp, vertical = 4.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualTables(
    tables: List<Pair<String, String>>,
    onRemove: (String) -> Unit,
    onBackToSuggestions: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your pick", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
            Text(
                "Back to suggestions",
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onBackToSuggestions).padding(horizontal = 6.dp, vertical = 2.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen
            )
        }
        if (tables.isEmpty()) {
            Text("No tables picked. Tap free tables on the map, or go back to suggestions.", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tables.forEach { (id, label) ->
                    Row(
                        Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(Color.White)
                            .border(1.5.dp, FormInk, RoundedCornerShape(50)).clickable { onRemove(id) }
                            .padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormInk)
                        Icon(Icons.Outlined.Close, "Remove $label", Modifier.size(14.dp), tint = FormMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF7F7F5))
            .border(1.dp, FormBorder, RoundedCornerShape(8.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = FormGreen)
        content()
    }
}

@Composable
private fun LoadingText(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(14.dp), color = FormGreen, strokeWidth = 2.dp)
        Text(text, fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
    }
}

@Composable
internal fun ErrorText(text: String) {
    Text(text, fontFamily = Inter(), fontSize = 12.sp, color = FormDanger)
}

// Staff may go past the restaurant's rules; these say so without blocking the booking.
internal fun ruleWarnings(rules: ReservationRules, guests: Int, date: LocalDate, today: LocalDate): List<String> = buildList {
    if (guests > rules.maxPartySize) add("More than the usual ${rules.maxPartySize} guests. You can still book it.")
    if (guests < rules.minPartySize) add("Fewer than the usual ${rules.minPartySize} guests. You can still book it.")
    rules.advanceBookingDays?.let { days ->
        if (today.daysUntil(date) > days) add("More than $days days ahead, past the usual booking window. You can still book it.")
    }
}

// Choices for the Guests field: at least 1–20, more if the rule allows bigger parties.
internal fun guestChoices(rules: ReservationRules): List<String> = (1..maxOf(20, rules.maxPartySize)).map { "$it" }

@Composable
internal fun RuleWarning(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFF4E0)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = Color(0xFFB36B00))
        Text(text, fontFamily = Inter(), fontSize = 12.sp, color = Color(0xFF7A4A00))
    }
}

@Composable
internal fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Close, "Close", Modifier.size(20.dp), tint = FormInk)
    }
}

private fun ReservationAvailabilityOption.capacityLabel(): String {
    val seats = totalCapacity ?: 0
    val count = tableCount ?: tableNumbers.size
    return "$seats seats" + if (count > 1) " · $count tables" else ""
}


internal fun LocalDate.formLabel(): String {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${days[dayOfWeek.ordinal]}, ${months[monthNumber - 1]} $day, $year"
}

@Composable
private fun StepBar(current: Int, canOpen: (Int) -> Boolean, onOpen: (Int) -> Unit, compact: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepLabels.forEachIndexed { index, label ->
            val done = index < current
            val active = index == current
            val enabled = canOpen(index)
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(enabled = enabled) { onOpen(index) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(if (done || active) FormGreen else Color.White)
                        .border(1.dp, if (done || active) FormGreen else FormBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) {
                        Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = Color.White)
                    } else {
                        Text("${index + 1}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (active) Color.White else FormMuted)
                    }
                }
                // On narrow forms only the current step keeps its name.
                if (!compact || active) Text(
                    label, fontFamily = Inter(), fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp, color = if (active || done) FormInk else FormMuted, maxLines = 1
                )
            }
            if (index < StepLabels.lastIndex) {
                Box(
                    Modifier.weight(1f).padding(horizontal = if (compact) 6.dp else 10.dp).height(2.dp).clip(RoundedCornerShape(50))
                        .background(if (index < current) FormGreen else FormBorder)
                )
            }
        }
    }
}

@Composable
private fun ReviewSummary(
    name: String,
    phone: String,
    email: String,
    guests: Int,
    dateLabel: String,
    timeLabel: String,
    tableLabel: String,
    note: String,
    onEdit: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Check the reservation before creating it.", fontFamily = Inter(), fontSize = 13.sp, color = FormMuted)
        ReviewCard("When", onEdit = { onEdit(STEP_WHEN) }) {
            ReviewLine(Icons.Outlined.CalendarToday, "Date", dateLabel)
            ReviewLine(Icons.Outlined.AccessTime, "Time", timeLabel)
            ReviewLine(Icons.Outlined.PersonOutline, "Guests", "$guests ${if (guests == 1) "guest" else "guests"}")
        }
        ReviewCard("Table", onEdit = { onEdit(STEP_TABLE) }) {
            ReviewLine(Icons.Outlined.TableRestaurant, "Table", tableLabel)
        }
        ReviewCard("Guest", onEdit = { onEdit(STEP_GUEST) }) {
            ReviewLine(Icons.Outlined.PersonOutline, "Name", name)
            ReviewLine(Icons.Outlined.Phone, "Phone", phone.ifBlank { "Not given" })
            ReviewLine(Icons.Outlined.Email, "Email", email.ifBlank { "Not given" })
        }
        ReviewCard("Note", onEdit = { onEdit(STEP_GUEST) }) {
            ReviewLine(Icons.Outlined.Notes, "Note", note.ifBlank { "No note" })
        }
    }
}

@Composable
private fun ReviewCard(title: String, onEdit: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F7F5))
            .border(1.dp, FormBorder, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormInk)
            Text(
                "Edit",
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onEdit).padding(horizontal = 6.dp, vertical = 2.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen
            )
        }
        content()
    }
}

@Composable
private fun ReviewLine(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = FormMuted)
        Text(label, Modifier.width(64.dp), fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
        Text(value, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
    }
}

@Composable
private fun LegendOutline(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(11.dp).border(2.dp, color, RoundedCornerShape(3.dp)))
        Text(label, fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoFreeTableCard(
    timeLabel: String,
    nearestFreeStarts: List<ServiceTime>,
    searching: Boolean,
    onPickTime: (ServiceTime) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFBF1EF))
            .border(1.dp, Color(0xFFE7C4BE), RoundedCornerShape(8.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.EventBusy, null, Modifier.size(18.dp), tint = FormDanger)
            Column {
                Text("No free table at $timeLabel", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
                Text("Every table that fits is already reserved.", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
            }
        }
        when {
            searching -> LoadingText("Looking for the nearest free times…")
            nearestFreeStarts.isEmpty() -> Text(
                "No free time found within 2 hours. Try another day.",
                fontFamily = Inter(), fontSize = 12.sp, color = FormMuted
            )
            else -> {
                Text("Nearest free times", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormInk)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    nearestFreeStarts.forEach { time ->
                        Surface(
                            onClick = { onPickTime(time) },
                            shape = RoundedCornerShape(50),
                            color = Color.White,
                            border = BorderStroke(1.dp, FormGreen)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Outlined.AccessTime, null, Modifier.size(14.dp), tint = FormGreen)
                                Text(time.label(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoTableConfirmDialog(
    timeLabel: String,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    MenuNestedDialog(
        onDismissRequest = onCancel,
        title = { Text("No table for this reservation", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Text(
                "There is no free table at $timeLabel. Are you sure you want to go ahead with this reservation? " +
                    "It will be saved without a table (Unassigned) and you can assign one later.",
                fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = FormMuted
            )
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = FormGreen)
            ) {
                Text("Continue", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = FormMuted, fontFamily = Inter()) }
        }
    )
}

internal val NoteSuggestions = listOf(
    "Birthday", "Anniversary", "Window seat", "Quiet table", "High chair",
    "Wheelchair access", "Allergies", "VIP", "Business dinner", "Outdoor seating"
)

internal fun combinedNote(tags: List<String>, custom: String): String? =
    (tags + custom.trim()).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteSuggestionChips(selected: List<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NoteSuggestions.forEach { tag ->
            val isSelected = tag in selected
            Surface(
                onClick = { onToggle(tag) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) FormGreen else Color.White,
                border = BorderStroke(1.dp, if (isSelected) FormGreen else FormBorder)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isSelected) Icon(Icons.Outlined.Check, null, Modifier.size(12.dp), tint = Color.White)
                    Text(tag, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (isSelected) Color.White else FormInk)
                }
            }
        }
    }
}
