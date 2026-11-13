package com.saporini.mobile_desktop.pos.menu.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.isWidePhoneWindow
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEventNight
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEventNightInput
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private val PickerGreen = Color(0xFF4F7942)
private val PickerGreenSoft = Color(0xFFEEF3EB)
private val PickerInk = Color(0xFF222426)
private val PickerMuted = Color(0xFF747572)
private val PickerBorder = Color(0xFFE4E5E1)
private val PickerSurface = Color(0xFFF7F7F5)
private val PickerDanger = Color(0xFFB13A2F)

private val EventIcons = listOf("🎉", "❤️", "🎄", "🎆", "🍷", "🎭", "🌸", "🎃")

/**
 * "Special menu" → which event night the menu belongs to. Same look as the section editor: a list of rows, an add
 * action and a footer. An event that already uses another menu is shown locked. Adding an event opens the event
 * form right here; once saved, the new event is chosen and the picker closes back to the menu editor. Events can be
 * deleted from their row.
 */
@Composable
internal fun EventPickerDialog(
    eventNights: EventNightsState,
    selectedEventId: String?,
    menuId: String?,
    canManageEvents: Boolean,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    onPick: (MenuEventNight) -> Unit,
    onCreateEvent: suspend (MenuEventNightInput) -> Result<MenuEventNight>,
    onUpdateEvent: suspend (MenuEventNight, MenuEventNightInput) -> Result<MenuEventNight>,
    onEventUpdated: (MenuEventNight) -> Unit,
    onDeleteEvent: suspend (MenuEventNight) -> Result<Unit>,
    onEventDeleted: (MenuEventNight) -> Unit
) {
    val isPhone = isPhoneMenuWindow()
    val isWidePhone = isWidePhoneWindow()
    var picked by remember(selectedEventId) { mutableStateOf(selectedEventId) }
    var addEventOpen by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<MenuEventNight?>(null) }
    var eventToEdit by remember { mutableStateOf<MenuEventNight?>(null) }
    val events = (eventNights as? EventNightsState.Loaded)?.events.orEmpty()
    val pickedEvent = events.firstOrNull { it.id == picked }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f))
                .then(if (isPhone) Modifier.safeDrawingPadding().imePadding() else Modifier)
                .padding(if (isWidePhone) 12.dp else 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.width(minOf(maxWidth, 560.dp)).widthIn(max = 560.dp)
                    .heightIn(max = maxHeight * if (isWidePhone) 0.94f else 0.82f)
                    .shadow(22.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                    .background(Color.White).border(1.dp, PickerBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Choose the event", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = PickerInk)
                        Text(
                            "The menu shows only on the event's days, and takes its dates.",
                            fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = PickerMuted
                        )
                    }
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(PickerSurface)
                            .border(1.dp, PickerBorder, CircleShape).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Close, "Close event picker", Modifier.size(18.dp), tint = PickerInk)
                    }
                }
                HorizontalDivider(color = PickerBorder)

                Column(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = if (isWidePhone) 10.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (eventNights) {
                        EventNightsState.Loading -> PickerMessage("Loading event nights…")
                        is EventNightsState.Failed -> Column(
                            Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(eventNights.message, fontFamily = Inter(), fontSize = 13.sp, color = PickerDanger, textAlign = TextAlign.Center)
                            OutlinedPickerButton("Try again", onClick = onReload)
                        }
                        is EventNightsState.Loaded -> if (events.isEmpty()) {
                            NoEventsYet(canAddEvents = canManageEvents, onAdd = { addEventOpen = true })
                        } else {
                            events.forEach { event ->
                                // One menu per event: an event that already has another menu can't be chosen.
                                val takenBy = event.menuName?.takeIf { event.menuId != null && event.menuId != menuId }
                                EventRow(
                                    event = event,
                                    takenBy = takenBy,
                                    selected = event.id == picked,
                                    canManage = canManageEvents,
                                    onClick = { picked = event.id },
                                    onEdit = { eventToEdit = event },
                                    onDelete = { eventToDelete = event }
                                )
                            }
                            if (canManageEvents) {
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, PickerInk, RoundedCornerShape(10.dp))
                                        .clickable { addEventOpen = true },
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Add, null, Modifier.size(19.dp), tint = PickerInk)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add event…", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PickerInk)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = PickerBorder)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isWidePhone) 6.dp else 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Inter(), color = PickerMuted) }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { pickedEvent?.let(onPick) },
                        enabled = pickedEvent != null,
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PickerGreen,
                            disabledContainerColor = PickerGreen.copy(alpha = 0.45f),
                            disabledContentColor = Color.White
                        )
                    ) {
                        Text("Use this event", fontFamily = Inter(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    eventToDelete?.let { event ->
        DeleteEventDialog(
            event = event,
            menuId = menuId,
            onDismiss = { eventToDelete = null },
            onDeleteEvent = onDeleteEvent,
            onDeleted = {
                eventToDelete = null
                if (picked == event.id) picked = null
                onEventDeleted(event)
            }
        )
    }

    eventToEdit?.let { event ->
        EventFormDialog(
            existing = event,
            onDismiss = { eventToEdit = null },
            onSave = { input -> onUpdateEvent(event, input) },
            onSaved = { updated ->
                eventToEdit = null
                onEventUpdated(updated)
            }
        )
    }

    if (addEventOpen) {
        EventFormDialog(
            existing = null,
            onDismiss = { addEventOpen = false },
            onSave = onCreateEvent,
            onSaved = { event ->
                addEventOpen = false
                onPick(event)
            }
        )
    }
}

@Composable
private fun EventRow(
    event: MenuEventNight,
    takenBy: String?,
    selected: Boolean,
    canManage: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val locked = takenBy != null
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) PickerGreenSoft else PickerSurface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) PickerGreen else PickerBorder, RoundedCornerShape(10.dp))
            .clickable(enabled = !locked, onClick = onClick)
            .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Only the event's own part fades when it's locked; delete stays usable.
        Row(Modifier.weight(1f).alpha(if (locked) 0.55f else 1f), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Color.White, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(event.icon.ifBlank { "🎉" }, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                event.name + if (!event.active) " (off)" else "",
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PickerInk,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(eventDaysText(event.startDate, event.endDate), takenBy?.let { "Uses $it" }).joinToString(" · "),
                fontFamily = Inter(), fontSize = 11.sp, color = PickerMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            locked -> Icon(Icons.Outlined.Lock, "Already has a menu", Modifier.size(18.dp), tint = PickerMuted)
            selected -> Box(Modifier.size(22.dp).clip(CircleShape).background(PickerGreen), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, "Chosen", Modifier.size(15.dp), tint = Color.White)
            }
            else -> Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White).border(1.5.dp, Color(0xFF8B8F87), CircleShape))
        }
        }
        if (canManage) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                    .border(1.dp, PickerBorder, RoundedCornerShape(8.dp)).clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Edit, "Edit ${event.name}", Modifier.size(18.dp), tint = PickerInk)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(PickerDanger).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.DeleteOutline, "Delete ${event.name}", Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}

/** "Delete Valentine's?" with what happens to its menu, then the usual removing animation. */
@Composable
private fun DeleteEventDialog(
    event: MenuEventNight,
    menuId: String?,
    onDismiss: () -> Unit,
    onDeleteEvent: suspend (MenuEventNight) -> Result<Unit>,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    val isIdle = status is DialogActionStatus.Idle
    val menuNote = when {
        event.menuId == null -> null
        event.menuId == menuId -> "This menu loses its event and you'll need to choose another one."
        else -> "Its menu \"${event.menuName ?: "menu"}\" will be hidden in POS and stop being a special menu."
    }

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        title = {
            Text(
                "Delete ${event.name}?", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            if (isIdle) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Bookings on ${eventDaysText(event.startDate, event.endDate)} stay as they are.",
                        fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = PickerMuted
                    )
                    menuNote?.let { Text(it, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = PickerDanger) }
                }
            } else {
                DialogStatusBody(
                    status = status,
                    onRetry = { status = DialogActionStatus.Idle },
                    onCancel = onDismiss,
                    onSuccessSettled = onDeleted,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (isIdle) {
                Button(
                    onClick = {
                        status = DialogActionStatus.Loading("Deleting")
                        scope.launch {
                            onDeleteEvent(event).fold(
                                onSuccess = { status = DialogActionStatus.Removed("${event.name} deleted") },
                                onFailure = { error ->
                                    status = DialogActionStatus.Failed(message = error.message ?: "Could not delete this event.")
                                }
                            )
                        }
                    },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = PickerDanger)
                ) {
                    Text("Delete", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        },
        dismissButton = {
            if (isIdle) {
                TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Inter(), color = PickerMuted) }
            }
        }
    )
}

@Composable
private fun NoEventsYet(canAddEvents: Boolean, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(PickerGreenSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.EventAvailable, null, Modifier.size(26.dp), tint = PickerGreen)
        }
        Text("No event nights yet", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PickerInk)
        Text(
            if (canAddEvents) "Add your first one, for example Valentine's or New Year's Eve."
            else "Ask a manager to add one in Admin Hub → Settings → Occasions & events.",
            fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = PickerMuted, textAlign = TextAlign.Center
        )
        if (canAddEvents) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = PickerGreen)
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add an event", fontFamily = Inter(), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * The event form: adds an event ([existing] null) or changes one (name, icon, days…). Opened from the picker and
 * from the menu editor's locked dates. Saving shows the usual loading/success animation.
 */
@Composable
internal fun EventFormDialog(
    existing: MenuEventNight?,
    onDismiss: () -> Unit,
    onSave: suspend (MenuEventNightInput) -> Result<MenuEventNight>,
    onSaved: (MenuEventNight) -> Unit
) {
    val scope = rememberCoroutineScope()
    val isNew = existing == null
    var icon by remember { mutableStateOf(existing?.icon?.ifBlank { null } ?: EventIcons.first()) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var firstDay by remember { mutableStateOf(existing?.startDate.orEmpty()) }
    var lastDay by remember { mutableStateOf(existing?.endDate?.takeIf { it != existing.startDate }.orEmpty()) }
    // A deliberate choice: nothing picked for a new event.
    var onlyThisMenu by remember { mutableStateOf(existing?.specialMenuOnly) }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    var created by remember { mutableStateOf<MenuEventNight?>(null) }
    val isIdle = status is DialogActionStatus.Idle

    val start = runCatching { LocalDate.parse(firstDay) }.getOrNull()
    val end = runCatching { LocalDate.parse(lastDay.ifBlank { firstDay }) }.getOrNull()
    val problem = when {
        name.isBlank() -> "Give the event a name."
        start == null -> "Choose the first day."
        end == null -> "Choose the last day, or leave it empty for one day."
        end < start -> "The last day can't be before the first."
        onlyThisMenu == null -> "Choose what the till shows on those days."
        else -> null
    }

    fun submit() {
        if (problem != null) {
            attemptedSubmit = true
            return
        }
        status = DialogActionStatus.Loading(if (isNew) "Adding" else "Saving")
        scope.launch {
            onSave(
                MenuEventNightInput(
                    name = name.trim(),
                    icon = icon,
                    startDate = firstDay,
                    endDate = lastDay.ifBlank { firstDay },
                    specialMenuOnly = onlyThisMenu == true
                )
            ).fold(
                onSuccess = { event ->
                    created = event
                    status = DialogActionStatus.Success("${event.name} ${if (isNew) "added" else "saved"}")
                },
                onFailure = { error ->
                    status = DialogActionStatus.Failed(message = error.message ?: "Could not save this event.")
                }
            )
        }
    }

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        title = { Text(if (isNew) "Add event" else "Edit event", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            if (isIdle) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormLabel("Icon")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EventIcons.forEach { option ->
                                val chosen = option == icon
                                Box(
                                    Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))
                                        .background(if (chosen) PickerGreenSoft else PickerSurface)
                                        .border(if (chosen) 1.5.dp else 1.dp, if (chosen) PickerGreen else PickerBorder, RoundedCornerShape(9.dp))
                                        .clickable { icon = option },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(option, fontSize = 17.sp)
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormLabel("Event name", required = true)
                        MenuEditorInput(
                            value = name,
                            onValueChange = { name = it.take(120) },
                            placeholder = "For example: Valentine's",
                            isError = attemptedSubmit && name.isBlank()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormLabel("First day", required = true)
                            MenuDateInput(
                                title = "First day",
                                value = firstDay,
                                onValueChange = { firstDay = it },
                                enabled = true,
                                isError = attemptedSubmit && start == null,
                                calendarOnly = true
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormLabel("Last day")
                            MenuDateInput(
                                title = "Last day",
                                value = lastDay,
                                onValueChange = { lastDay = it },
                                enabled = true,
                                isError = attemptedSubmit && (end == null || (start != null && end < start)),
                                calendarOnly = true,
                                minimumDate = firstDay.takeIf { start != null }
                            )
                        }
                    }
                    Text("Leave the last day empty for a one-day event.", fontFamily = Inter(), fontSize = 11.sp, color = PickerMuted)
                    TillMenuChoice(
                        onlyEventMenu = onlyThisMenu,
                        onChange = { onlyThisMenu = it },
                        isError = attemptedSubmit && onlyThisMenu == null
                    )
                    // The till choice shows its own message.
                    if (attemptedSubmit && problem != null && onlyThisMenu != null) {
                        Text(problem, fontFamily = Inter(), fontSize = 12.sp, color = PickerDanger)
                    }
                }
            } else {
                DialogStatusBody(
                    status = status,
                    onRetry = { status = DialogActionStatus.Idle },
                    onCancel = onDismiss,
                    onSuccessSettled = { created?.let(onSaved) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (isIdle) {
                Button(
                    // Looks dimmed until the form is complete, but a click still shows what's missing.
                    onClick = { submit() },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (problem == null) PickerGreen else PickerGreen.copy(alpha = 0.45f)
                    )
                ) {
                    Text(if (isNew) "Add event" else "Save", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        },
        dismissButton = {
            if (isIdle) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = PickerMuted) }
            }
        }
    )
}

@Composable
private fun FormLabel(text: String, required: Boolean = false) {
    Row {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PickerInk)
        if (required) Text(" *", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PickerDanger)
    }
}

@Composable
private fun PickerMessage(text: String) {
    Text(
        text, Modifier.fillMaxWidth().padding(vertical = 22.dp),
        fontFamily = Inter(), fontSize = 13.sp, color = PickerMuted, textAlign = TextAlign.Center
    )
}

@Composable
internal fun OutlinedPickerButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, PickerInk),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = PickerInk)
    ) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold)
    }
}
