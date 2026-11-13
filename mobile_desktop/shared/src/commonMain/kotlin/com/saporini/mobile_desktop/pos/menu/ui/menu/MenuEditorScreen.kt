package com.saporini.mobile_desktop.pos.menu.ui.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.isWidePhoneWindow
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEvent
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEventNight
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEventNightInput
import com.saporini.mobile_desktop.pos.menu.domain.model.SpecialMenuKind
import kotlinx.coroutines.delay

private val EditorOlive = Color(0xFF4F7942)
private val EditorInk = Color(0xFF242522)
private val EditorMuted = Color(0xFF71736E)
private val EditorBorder = Color(0xFFE2E3DE)
private val EditorSurface = Color(0xFFF7F7F5)
private val EditorErrorBg = Color(0xFFFFF1EF)
private val EditorErrorText = Color(0xFFD6453D)

private data class MenuColorSuggestion(
    val name: String,
    val hex: String
)

private val MenuColorSuggestions = listOf(
    MenuColorSuggestion("Warm gold", "#B88945"),
    MenuColorSuggestion("Sky blue", "#6F9DB8"),
    MenuColorSuggestion("Sage", "#789A5B"),
    MenuColorSuggestion("Sage", "#AEBE95"),
    MenuColorSuggestion("Navy", "#24445F"),
    MenuColorSuggestion("Terracotta", "#A85F3F"),
    MenuColorSuggestion("Burgundy", "#783F4B"),
    MenuColorSuggestion("Plum", "#65507D"),
    MenuColorSuggestion("Teal", "#35766D"),
    MenuColorSuggestion("Slate", "#607180")
)

@Composable
internal fun MenuEditorDialog(
    menu: Menu?,
    existingColors: Set<String> = emptySet(),
    status: DialogActionStatus = DialogActionStatus.Idle,
    onRetry: () -> Unit = {},
    onSuccessSettled: () -> Unit = {},
    onDismiss: () -> Unit,
    onDeleteMenu: (deleteItems: Boolean) -> Unit = { },
    onSave: (
        name: String,
        description: String?,
        shownInPos: Boolean,
        availableFrom: String?,
        availableUntil: String?,
        availableFromDate: String?,
        availableUntilDate: String?,
        color: String
    ) -> Unit,
    // A special menu (booking extras, or an event night's menu and its event); reported beside onSave.
    onSpecialChange: (SpecialMenuChoice) -> Unit = {},
    // The event nights an event menu can belong to.
    eventNights: EventNightsState = EventNightsState.Loaded(emptyList()),
    onReloadEventNights: () -> Unit = {},
    // Adding and deleting events in the event picker (needs the settings permission).
    canManageEvents: Boolean = false,
    onCreateEventNight: suspend (MenuEventNightInput) -> Result<MenuEventNight> = {
        Result.failure(IllegalStateException("Adding events isn't available here"))
    },
    onDeleteEventNight: suspend (MenuEventNight) -> Result<Unit> = {
        Result.failure(IllegalStateException("Deleting events isn't available here"))
    },
    onUpdateEventNight: suspend (MenuEventNight, MenuEventNightInput) -> Result<MenuEventNight> = { _, _ ->
        Result.failure(IllegalStateException("Changing events isn't available here"))
    }
) {
    var name by remember(menu?.id) { mutableStateOf(menu?.name.orEmpty()) }
    var specialChoice by remember(menu?.id) {
        mutableStateOf(
            SpecialMenuChoice(
                special = menu?.special ?: false,
                kind = menu?.specialKind ?: SpecialMenuKind.BOOKING_EXTRAS,
                eventId = menu?.event?.id
            )
        )
    }
    var description by remember(menu?.id) { mutableStateOf(menu?.description.orEmpty()) }
    // "Show in POS" (to waiters and at the till). Menus have no Active switch any more; this is the one choice.
    var active by remember(menu?.id) { mutableStateOf(menu?.shownToWaiters ?: true) }
    var availableAllDay by remember(menu?.id) {
        mutableStateOf(menu?.availableFrom.isNullOrBlank() || menu?.availableUntil.isNullOrBlank())
    }
    var availableFrom by remember(menu?.id) {
        mutableStateOf(displayTime(menu?.availableFrom) ?: "17:00")
    }
    var availableUntil by remember(menu?.id) {
        mutableStateOf(displayTime(menu?.availableUntil) ?: "23:00")
    }
    var availableYearRound by remember(menu?.id) {
        mutableStateOf(menu?.availableFromDate.isNullOrBlank() || menu?.availableUntilDate.isNullOrBlank())
    }
    var availableFromDate by remember(menu?.id) {
        mutableStateOf(menu?.availableFromDate.orEmpty())
    }
    var availableUntilDate by remember(menu?.id) {
        mutableStateOf(menu?.availableUntilDate.orEmpty())
    }
    var selectedColor by remember(menu?.id) {
        mutableStateOf(
            normalizeHexInput(
                menu?.color ?: run {
                    val usedColors = existingColors.map { normalizeHexInput(it) }.toSet()
                    MenuColorSuggestions.firstOrNull { normalizeHexInput(it.hex) !in usedColors }?.hex ?: "#AEBE95"
                }
            )
        )
    }
    var showDeleteConfirm by remember(menu?.id) { mutableStateOf(false) }
    var eventPickerOpen by remember(menu?.id) { mutableStateOf(false) }
    // The menu's own dates from before it became an event menu, given back if it stops being one.
    var datesBeforeEvent by remember(menu?.id) { mutableStateOf<Triple<Boolean, String, String>?>(null) }
    val isUncategorized = menu?.code == "UNCATEGORIZED"
    var deleteItemsChecked by remember(menu?.id) { mutableStateOf(false) }
    val deleteContents = isUncategorized || deleteItemsChecked
    var attemptedSubmit by remember(menu?.id) { mutableStateOf(false) }
    // Bumped on every failed click so the toast's auto-dismiss timer restarts each time,
    // even if the user clicks again before the previous 3s window has finished.
    var invalidAttemptToken by remember(menu?.id) { mutableStateOf(0) }
    var toastVisible by remember(menu?.id) { mutableStateOf(false) }

    val isEditing = menu != null
    val isSaving = status is DialogActionStatus.Loading
    val colorIsValid = isValidHexColor(selectedColor)
    val fromIsValid = availableAllDay || isValidTime(availableFrom)
    val untilIsValid = availableAllDay || isValidTime(availableUntil)
    // An event menu's days are its event's, so its own date fields don't count.
    val isEventMenu = specialChoice.isEventMenu
    val datesOwnedByUs = !availableYearRound && !isEventMenu
    val fromDateIsValid = !datesOwnedByUs || isValidIsoDate(availableFromDate)
    val untilDateIsValid = !datesOwnedByUs || isValidIsoDate(availableUntilDate)
    val dateOrderIsValid = !datesOwnedByUs ||
        (fromDateIsValid && untilDateIsValid && availableFromDate <= availableUntilDate)
    val eventMissing = isEventMenu && specialChoice.eventId == null
    val chosenEvent = (eventNights as? EventNightsState.Loaded)?.events?.firstOrNull { it.id == specialChoice.eventId }
        ?: menu?.event?.takeIf { it.id == specialChoice.eventId }?.let {
            MenuEventNight(it.id, it.name, it.icon, it.startDate, it.endDate, menuId = menu.id, menuName = menu.name, active = it.active)
        }
    // The preview card shows the event's icon and days, like the real card.
    val previewEvent = chosenEvent?.takeIf { specialChoice.isEventMenu }?.let {
        MenuEvent(it.id, it.name, it.icon, it.startDate, it.endDate, it.active, it.specialMenuOnly)
    }
    val requirementsMet = name.isNotBlank() &&
        colorIsValid &&
        fromIsValid &&
        untilIsValid &&
        fromDateIsValid &&
        untilDateIsValid &&
        dateOrderIsValid &&
        !eventMissing
    val canSave = requirementsMet && !isSaving
    val nameError = attemptedSubmit && name.isBlank()
    val eventError = attemptedSubmit && eventMissing
    val showValidationMessage = toastVisible && attemptedSubmit && !requirementsMet
    val previewFrom = availableFrom.takeIf { !availableAllDay && isValidTime(it) }
    val previewUntil = availableUntil.takeIf { !availableAllDay && isValidTime(it) }

    // Auto-dismiss the toast ~3s after the most recent failed attempt; a new failed
    // click restarts the window instead of stacking timers.
    LaunchedEffect(invalidAttemptToken) {
        if (invalidAttemptToken > 0) {
            toastVisible = true
            delay(3000)
            toastVisible = false
        }
    }

    // An event was chosen (or just added) in the picker: the menu takes the event's days, locked.
    fun chooseEvent(event: MenuEventNight) {
        if (!specialChoice.isEventMenu) {
            datesBeforeEvent = Triple(availableYearRound, availableFromDate, availableUntilDate)
        }
        specialChoice = specialChoice.copy(special = true, kind = SpecialMenuKind.EVENT, eventId = event.id)
        availableYearRound = false
        availableFromDate = event.startDate
        availableUntilDate = event.endDate
        eventPickerOpen = false
    }

    // The chosen event's days changed (edited here): the locked dates follow.
    fun eventChanged(updated: MenuEventNight) {
        if (specialChoice.isEventMenu && specialChoice.eventId == updated.id) {
            availableFromDate = updated.startDate
            availableUntilDate = updated.endDate
        }
    }

    fun changeSpecial(next: SpecialMenuChoice) {
        val leavingEvent = specialChoice.isEventMenu && !next.isEventMenu
        // Ticking "Special menu" again while it still remembers its event: back on the event's days.
        val returningToEvent = !specialChoice.isEventMenu && next.isEventMenu
        if (returningToEvent) {
            val event = (eventNights as? EventNightsState.Loaded)?.events?.firstOrNull { it.id == next.eventId }
            if (event != null) {
                chooseEvent(event)
                return
            }
        }
        specialChoice = next
        if (leavingEvent) {
            // Back to the menu's own dates (all year when it never had its own).
            val (yearRound, from, until) = datesBeforeEvent ?: Triple(true, "", "")
            availableYearRound = yearRound
            availableFromDate = from
            availableUntilDate = until
            datesBeforeEvent = null
        }
    }

    fun trySave() {
        if (requirementsMet) {
            attemptedSubmit = false
            onSpecialChange(specialChoice)
            onSave(
                name.trim(),
                description.trim().takeIf { it.isNotEmpty() },
                active,
                availableFrom.takeIf { !availableAllDay },
                availableUntil.takeIf { !availableAllDay },
                availableFromDate.takeIf { datesOwnedByUs },
                availableUntilDate.takeIf { datesOwnedByUs },
                normalizeHexInput(selectedColor)
            )
        } else {
            attemptedSubmit = true
            invalidAttemptToken++
        }
    }

    val isPhone = isPhoneMenuWindow()
    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isSaving,
            dismissOnClickOutside = !isSaving,
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (status !is DialogActionStatus.Idle && isPhone) {
                // Desktop keeps its panel mounted and swaps the body in place (see the
                // `else` branch below) instead of collapsing to a small takeover card.
                MenuEditorStatusTakeover(
                    isPhone = true,
                    status = status,
                    onRetry = onRetry,
                    onCancel = onDismiss,
                    onSuccessSettled = onSuccessSettled
                )
            } else if (isPhone) {
                PhoneMenuEditorPage(
                    isEditing = isEditing,
                    isSaving = isSaving,
                    requirementsMet = requirementsMet,
                    showValidationMessage = showValidationMessage,
                    errorMessage = null,
                    onDismiss = onDismiss,
                    onDeleteMenu = { showDeleteConfirm = true },
                    // Same look-disabled-but-clickable pattern as desktop: goes through
                    // trySave() so an invalid click surfaces the toast instead of the
                    // disabled button silently doing nothing.
                    onSave = { trySave() },
                    fields = {
                        MenuEditorFields(
                            name = name,
                            onNameChange = { name = it },
                            description = description,
                            onDescriptionChange = { description = it },
                            active = active,
                            onActiveChange = { active = it },
                            specialChoice = specialChoice,
                            onSpecialChoiceChange = { changeSpecial(it) },
                            onPickEvent = { eventPickerOpen = true },
                            eventError = eventError,
                            chosenEvent = chosenEvent,
                            availableAllDay = availableAllDay,
                            onAvailableAllDayChange = { availableAllDay = it },
                            availableFrom = availableFrom,
                            onAvailableFromChange = { availableFrom = it.take(5) },
                            availableUntil = availableUntil,
                            onAvailableUntilChange = { availableUntil = it.take(5) },
                            fromIsValid = fromIsValid,
                            untilIsValid = untilIsValid,
                            availableYearRound = availableYearRound,
                            onAvailableYearRoundChange = { availableYearRound = it },
                            availableFromDate = availableFromDate,
                            onAvailableFromDateChange = { availableFromDate = it.take(10) },
                            availableUntilDate = availableUntilDate,
                            onAvailableUntilDateChange = { availableUntilDate = it.take(10) },
                            fromDateIsValid = fromDateIsValid,
                            untilDateIsValid = untilDateIsValid,
                            dateOrderIsValid = dateOrderIsValid,
                            isSaving = isSaving,
                            errorMessage = null,
                            nameError = nameError,
                            stackFields = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    visuals = {
                        PhoneMenuEditorVisuals(
                            previewEvent = previewEvent,
                            name = name,
                            description = description,
                            active = active,
                            availableFrom = previewFrom,
                            availableUntil = previewUntil,
                            selectedColor = selectedColor,
                            onColorChange = { selectedColor = normalizeHexInput(it) },
                            colorIsValid = colorIsValid,
                            isSaving = isSaving
                        )
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.34f))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 1180.dp)
                            .fillMaxWidth(0.94f)
                            .fillMaxHeight(0.96f)
                            .shadow(24.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .border(1.dp, EditorBorder, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 18.dp, top = 8.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEditing) "Edit Menu" else "Create Menu",
                                modifier = Modifier.weight(1f),
                                fontFamily = Inter(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color(0xFF232422)
                            )

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(EditorSurface)
                                    .border(1.dp, EditorBorder, CircleShape)
                                    .clickable(enabled = !isSaving, onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close menu editor",
                                    modifier = Modifier.size(18.dp),
                                    tint = EditorInk
                                )
                            }
                        }

                        HorizontalDivider(color = EditorBorder)

                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            val wideLayout = maxWidth >= 860.dp

                            if (status !is DialogActionStatus.Idle) {
                                // Same-size panel: only this body swaps to the
                                // loading/success/error content, the card never resizes.
                                DialogStatusBody(
                                    status = status,
                                    onRetry = onRetry,
                                    onCancel = onDismiss,
                                    onSuccessSettled = onSuccessSettled,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (wideLayout) {
                                Row(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    MenuEditorFields(
                                        name = name,
                                        onNameChange = { name = it },
                                        description = description,
                                        onDescriptionChange = { description = it },
                                        active = active,
                                        onActiveChange = { active = it },
                                        specialChoice = specialChoice,
                                        onSpecialChoiceChange = { changeSpecial(it) },
                                        onPickEvent = { eventPickerOpen = true },
                                        eventError = eventError,
                                        chosenEvent = chosenEvent,
                                        availableAllDay = availableAllDay,
                                        onAvailableAllDayChange = { availableAllDay = it },
                                        availableFrom = availableFrom,
                                        onAvailableFromChange = { availableFrom = it.take(5) },
                                        availableUntil = availableUntil,
                                        onAvailableUntilChange = { availableUntil = it.take(5) },
                                        fromIsValid = fromIsValid,
                                        untilIsValid = untilIsValid,
                                        availableYearRound = availableYearRound,
                                        onAvailableYearRoundChange = { availableYearRound = it },
                                        availableFromDate = availableFromDate,
                                        onAvailableFromDateChange = { availableFromDate = it.take(10) },
                                        availableUntilDate = availableUntilDate,
                                        onAvailableUntilDateChange = { availableUntilDate = it.take(10) },
                                        fromDateIsValid = fromDateIsValid,
                                        untilDateIsValid = untilDateIsValid,
                                        dateOrderIsValid = dateOrderIsValid,
                                        isSaving = isSaving,
                                        errorMessage = null,
                                        nameError = nameError,
                                        modifier = Modifier
                                            .weight(1.15f)
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState())
                                            .padding(
                                                start = 30.dp,
                                                end = 30.dp,
                                                top = 18.dp,
                                                bottom = 18.dp
                                            )
                                    )

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(EditorBorder)
                                    )

                                    MenuEditorVisuals(
                                        previewEvent = previewEvent,
                                        name = name,
                                        description = description,
                                        active = active,
                                        availableFrom = previewFrom,
                                        availableUntil = previewUntil,
                                        selectedColor = selectedColor,
                                        onColorChange = { selectedColor = normalizeHexInput(it) },
                                        colorIsValid = colorIsValid,
                                        isSaving = isSaving,
                                        modifier = Modifier
                                            .weight(0.85f)
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState())
                                            .padding(
                                                start = 30.dp,
                                                end = 30.dp,
                                                top = 18.dp,
                                                bottom = 18.dp
                                            )
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 24.dp, vertical = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(26.dp)
                                ) {
                                    MenuEditorFields(
                                        name = name,
                                        onNameChange = { name = it },
                                        description = description,
                                        onDescriptionChange = { description = it },
                                        active = active,
                                        onActiveChange = { active = it },
                                        specialChoice = specialChoice,
                                        onSpecialChoiceChange = { changeSpecial(it) },
                                        onPickEvent = { eventPickerOpen = true },
                                        eventError = eventError,
                                        chosenEvent = chosenEvent,
                                        availableAllDay = availableAllDay,
                                        onAvailableAllDayChange = { availableAllDay = it },
                                        availableFrom = availableFrom,
                                        onAvailableFromChange = { availableFrom = it.take(5) },
                                        availableUntil = availableUntil,
                                        onAvailableUntilChange = { availableUntil = it.take(5) },
                                        fromIsValid = fromIsValid,
                                        untilIsValid = untilIsValid,
                                        availableYearRound = availableYearRound,
                                        onAvailableYearRoundChange = { availableYearRound = it },
                                        availableFromDate = availableFromDate,
                                        onAvailableFromDateChange = { availableFromDate = it.take(10) },
                                        availableUntilDate = availableUntilDate,
                                        onAvailableUntilDateChange = { availableUntilDate = it.take(10) },
                                        fromDateIsValid = fromDateIsValid,
                                        untilDateIsValid = untilDateIsValid,
                                        dateOrderIsValid = dateOrderIsValid,
                                        isSaving = isSaving,
                                        errorMessage = null,
                                        nameError = nameError,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    HorizontalDivider(color = EditorBorder)

                                    MenuEditorVisuals(
                                        previewEvent = previewEvent,
                                        name = name,
                                        description = description,
                                        active = active,
                                        availableFrom = previewFrom,
                                        availableUntil = previewUntil,
                                        selectedColor = selectedColor,
                                        onColorChange = { selectedColor = normalizeHexInput(it) },
                                        colorIsValid = colorIsValid,
                                        isSaving = isSaving,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Footer (delete/cancel/save) only makes sense while the form is
                        // showing; the status body above already has its own actions.
                        if (status is DialogActionStatus.Idle) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                thickness = 1.dp,
                                color = EditorBorder
                            )

                            // Box so the validation toast can float above the button row
                            // without pushing it down or resizing the panel.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isEditing) {
                                        Button(
                                            onClick = { showDeleteConfirm = true },
                                            enabled = !isSaving,
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(percent = 50),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB13A2F))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "Delete Menu",
                                                fontFamily = Inter(),
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Spacer(Modifier.weight(1f))

                                    TextButton(
                                        onClick = onDismiss,
                                        enabled = !isSaving,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            fontFamily = Inter(),
                                            fontWeight = FontWeight.SemiBold,
                                            color = EditorMuted
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Button(
                                        onClick = { trySave() },
                                        // Stays clickable even when required fields are
                                        // empty (enabled=true), so the click can still
                                        // trigger trySave()'s validation toast below — it
                                        // only *looks* disabled (dimmed) until then.
                                        enabled = !isSaving,
                                        shape = RoundedCornerShape(percent = 50),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (requirementsMet) EditorOlive else EditorOlive.copy(alpha = 0.45f),
                                            disabledContainerColor = if (requirementsMet) EditorOlive else EditorOlive.copy(alpha = 0.45f)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                    ) {
                                        if (isSaving) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(9.dp))
                                        }
                                        Text(
                                            text = when {
                                                isSaving -> "Saving"
                                                isEditing -> "Save Changes"
                                                else -> "Create Menu"
                                            },
                                            fontFamily = Inter(),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                MenuValidationToast(
                                    visible = showValidationMessage,
                                    message = "Please fill in all required fields (*)",
                                    placement = ToastPlacement.Above,
                                    arrowAlignment = Alignment.End,
                                    anchorAlignment = Alignment.TopEnd,
                                    offsetY = (-46).dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (eventPickerOpen) {
        EventPickerDialog(
            eventNights = eventNights,
            selectedEventId = specialChoice.eventId,
            menuId = menu?.id,
            canManageEvents = canManageEvents,
            onReload = onReloadEventNights,
            onDismiss = { eventPickerOpen = false },
            onPick = { chooseEvent(it) },
            onCreateEvent = onCreateEventNight,
            onUpdateEvent = onUpdateEventNight,
            onEventUpdated = { eventChanged(it) },
            onDeleteEvent = onDeleteEventNight,
            // The menu's own event was deleted: it stays special but needs another event before saving.
            onEventDeleted = { deleted ->
                if (specialChoice.eventId == deleted.id) specialChoice = specialChoice.copy(eventId = null)
            }
        )
    }

    if (showDeleteConfirm) {
        // `menu` here is often just the list summary (opened via "Edit Menu" from the
        // cover list), whose `sections` is never populated -- only the detail fetch
        // (via openMenu) includes it. So this can't gate on a live section/item count;
        // always show the choice instead of hiding it based on stale/empty data.
        MenuNestedDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "Delete Menu",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = EditorInk
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val message = if (deleteContents) {
                        "Are you sure? This menu and everything inside it will be permanently deleted."
                    } else {
                        "Are you sure? The menu will be removed. If it has any sections or items and you " +
                            "don't check the box below, you can still find them in the \"Uncategorized\" menu."
                    }
                    Text(message, fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = EditorMuted)
                    if (!isUncategorized) Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { deleteItemsChecked = !deleteItemsChecked }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (deleteItemsChecked) Color(0xFFB13A2F) else Color.White)
                                .border(
                                    width = if (deleteItemsChecked) 1.dp else 1.5.dp,
                                    color = if (deleteItemsChecked) Color(0xFFB13A2F) else Color(0xFF8B8F87),
                                    shape = RoundedCornerShape(5.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (deleteItemsChecked) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Also delete everything inside this menu",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = EditorInk
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteMenu(deleteContents)
                    },
                    shape = RoundedCornerShape(percent = 50),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB13A2F))
                ) {
                    Text(
                        text = "Yes, Delete",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        text = "No",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = EditorMuted
                    )
                }
            }
        )
    }
}

/** Where a [MenuValidationToast] sits relative to the button that triggered it. */
internal enum class ToastPlacement { Above, Below }

/**
 * Small floating bubble telling the user why an action can't go through yet (missing
 * required fields, an action needing more items to make sense, etc). Fades/scales in
 * near whichever button triggered it, with a small arrow pointing back at that button,
 * then auto-dismisses (driven by the caller's `visible` flag) without shifting any
 * surrounding layout. Shared across menu forms — internal (not private) so other files
 * in this package can reuse it instead of each rebuilding their own toast.
 *
 * Defined as its own composable (rather than inline where it's used) so the plain,
 * receiver-less [AnimatedVisibility] overload resolves unambiguously — called from
 * inside nested Column/Row scopes, the compiler otherwise prefers their scoped
 * AnimatedVisibility extensions over this one.
 */
@Composable
internal fun MenuValidationToast(
    visible: Boolean,
    message: String,
    anchorAlignment: Alignment,
    offsetY: Dp,
    placement: ToastPlacement = ToastPlacement.Above,
    arrowAlignment: Alignment.Horizontal = Alignment.Start
) {
    // Two-stage visibility (popupVisible outlives contentVisible by one exit-animation
    // beat) so the bubble can fade out instead of just vanishing when `visible` flips.
    var popupVisible by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            popupVisible = true
            contentVisible = true
        } else if (popupVisible) {
            contentVisible = false
            delay(200)
            popupVisible = false
        }
    }

    if (!popupVisible) return

    // A Popup renders in its own layer, positioned relative to this composable's
    // location, without ever contributing to the enclosing layout's measured size —
    // unlike a plain Box+align sibling, showing/hiding it can't nudge the button
    // row that anchors it.
    val density = LocalDensity.current
    Popup(
        alignment = anchorAlignment,
        offset = with(density) { IntOffset(0, offsetY.roundToPx()) }
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(180)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f, animationSpec = tween(200))
        ) {
            val tailNearStart = arrowAlignment == Alignment.Start
            Box(
                modifier = Modifier.padding(
                    top = if (placement == ToastPlacement.Below) 6.dp else 0.dp,
                    bottom = if (placement == ToastPlacement.Above) 6.dp else 0.dp
                )
            ) {
                // Tail: a 45°-rotated square, half tucked behind the bubble (below,
                // z-order-wise) and half poking past its edge — reads as a triangle
                // fused straight into the bubble, no gap or seam.
                Box(
                    modifier = Modifier
                        .align(
                            when {
                                placement == ToastPlacement.Below && tailNearStart -> Alignment.TopStart
                                placement == ToastPlacement.Below -> Alignment.TopEnd
                                tailNearStart -> Alignment.BottomStart
                                else -> Alignment.BottomEnd
                            }
                        )
                        .padding(
                            start = if (tailNearStart) 18.dp else 0.dp,
                            end = if (tailNearStart) 0.dp else 18.dp
                        )
                        .offset(y = if (placement == ToastPlacement.Below) (-6).dp else 6.dp)
                        .size(12.dp)
                        .graphicsLayer { rotationZ = 45f }
                        .background(EditorErrorBg, RoundedCornerShape(2.dp))
                )
                Row(
                    modifier = Modifier
                        .shadow(6.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(EditorErrorBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = EditorErrorText
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = message,
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = EditorErrorText
                    )
                }
            }
        }
    }
}

/** Full takeover shown in place of the form while status is Loading/Success/Failed. */
@Composable
private fun MenuEditorStatusTakeover(
    isPhone: Boolean,
    status: DialogActionStatus,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onSuccessSettled: () -> Unit
) {
    Box(
        modifier = if (isPhone) {
            Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()
        } else {
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)).padding(24.dp)
        },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = if (isPhone) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.9f)
                    .shadow(24.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.dp, EditorBorder, RoundedCornerShape(20.dp))
            },
            verticalArrangement = Arrangement.Center
        ) {
            DialogStatusBody(
                status = status,
                onRetry = onRetry,
                onCancel = onCancel,
                onSuccessSettled = onSuccessSettled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PhoneMenuEditorPage(
    isEditing: Boolean,
    isSaving: Boolean,
    requirementsMet: Boolean,
    showValidationMessage: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onDeleteMenu: () -> Unit,
    onSave: () -> Unit,
    fields: @Composable () -> Unit,
    visuals: @Composable () -> Unit
) {
    val isWidePhone = isWidePhoneWindow()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (isWidePhone) 48.dp else 64.dp)
                .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Back to menus",
                    modifier = Modifier.size(24.dp),
                    tint = EditorInk
                )
            }
            Text(
                text = if (isEditing) "Edit Menu" else "Create Menu",
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = EditorInk
            )
        }
        HorizontalDivider(color = EditorBorder)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = if (isWidePhone) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            fields()
            HorizontalDivider(color = EditorBorder)
            visuals()
            if (isEditing) {
                Button(
                    onClick = onDeleteMenu,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(percent = 50),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB13A2F))
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Delete Menu",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        HorizontalDivider(color = EditorBorder)
        // Box so the validation toast can float above the button without nudging
        // this footer's own layout (same pattern as the desktop path).
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (isWidePhone) 6.dp else 12.dp)
        ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            errorMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF1EF), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    fontFamily = Inter(),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFFB13A2F)
                )
            }
            Button(
                // Same look-disabled-but-clickable pattern as desktop: always
                // clickable while idle, so an invalid tap surfaces the toast
                // instead of the button silently doing nothing.
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSave()
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = if (isWidePhone) 44.dp else 50.dp),
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (requirementsMet) EditorOlive else EditorOlive.copy(alpha = 0.45f).compositeOver(Color.White)
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = if (isWidePhone) 8.dp else 14.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = when {
                        isSaving -> "Saving"
                        isEditing -> "Save Changes"
                        else -> "Create Menu"
                    },
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

            MenuValidationToast(
                visible = showValidationMessage,
                message = "Please fill in all required fields (*)",
                placement = ToastPlacement.Above,
                arrowAlignment = Alignment.CenterHorizontally,
                anchorAlignment = Alignment.TopCenter,
                offsetY = (-46).dp
            )
        }
    }
}

@Composable
private fun PhoneMenuEditorVisuals(
    previewEvent: MenuEvent? = null,
    name: String,
    description: String,
    active: Boolean,
    availableFrom: String?,
    availableUntil: String?,
    selectedColor: String,
    onColorChange: (String) -> Unit,
    colorIsValid: Boolean,
    isSaving: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorFieldLabel(text = "Menu color")
        MenuEditorInput(
            value = selectedColor,
            onValueChange = onColorChange,
            placeholder = "#AEBE95",
            enabled = !isSaving,
            isError = !colorIsValid
        )
        if (!colorIsValid) {
            Text(
                text = "Enter a 6-digit hex color.",
                fontFamily = Inter(),
                fontSize = 12.sp,
                color = Color(0xFFB13A2F)
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = (maxWidth.value / 48f).toInt().coerceIn(1, 7)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MenuColorSuggestions.chunked(columns).forEach { suggestions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            Box(
                                Modifier.weight(1f).height(44.dp).clickable(enabled = !isSaving) {
                                    onColorChange(suggestion.hex)
                                },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                MenuColorSwatch(
                                    suggestion = suggestion,
                                    selected = selectedColor.equals(suggestion.hex, ignoreCase = true),
                                    enabled = !isSaving,
                                    onClick = { onColorChange(suggestion.hex) },
                                    diameter = 28.dp
                                )
                            }
                        }
                        repeat(columns - suggestions.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        EditorFieldLabel(text = "Cover preview")
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(EditorSurface)
                .border(1.dp, EditorBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val previewWidth = minOf(maxWidth, 260.dp)
            MenuCoverPreview(
                name = name,
                description = description,
                shownInPos = active,
                availableFrom = availableFrom,
                availableUntil = availableUntil,
                color = selectedColor.takeIf { colorIsValid } ?: "#AEBE95",
                modifier = Modifier.width(previewWidth).height(previewWidth * 1.3125f),
                event = previewEvent
            )
        }
    }
}

@Composable
private fun MenuEditorFieldPair(
    stackFields: Boolean,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    if (stackFields) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MenuEditorFields(
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    specialChoice: SpecialMenuChoice = SpecialMenuChoice(),
    onSpecialChoiceChange: ((SpecialMenuChoice) -> Unit)? = null,
    onPickEvent: () -> Unit = {},
    eventError: Boolean = false,
    chosenEvent: MenuEventNight? = null,
    availableAllDay: Boolean,
    onAvailableAllDayChange: (Boolean) -> Unit,
    availableFrom: String,
    onAvailableFromChange: (String) -> Unit,
    availableUntil: String,
    onAvailableUntilChange: (String) -> Unit,
    fromIsValid: Boolean,
    untilIsValid: Boolean,
    availableYearRound: Boolean,
    onAvailableYearRoundChange: (Boolean) -> Unit,
    availableFromDate: String,
    onAvailableFromDateChange: (String) -> Unit,
    availableUntilDate: String,
    onAvailableUntilDateChange: (String) -> Unit,
    fromDateIsValid: Boolean,
    untilDateIsValid: Boolean,
    dateOrderIsValid: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    nameError: Boolean = false,
    stackFields: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorFieldLabel(text = "Menu name", required = true, error = nameError)
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (active) "Shown in POS" else "Hidden in POS",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (active) EditorOlive else EditorMuted
                )
                Spacer(Modifier.width(9.dp))
                Switch(
                    checked = active,
                    onCheckedChange = onActiveChange,
                    enabled = !isSaving,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = EditorOlive
                    )
                )
            }

            MenuEditorInput(
                value = name,
                onValueChange = onNameChange,
                placeholder = "For example: Dinner Menu",
                enabled = !isSaving,
                isError = nameError
            )
            if (nameError) {
                Text(
                    text = "Menu name is required.",
                    fontFamily = Inter(),
                    fontSize = 12.sp,
                    color = Color(0xFFD6453D)
                )
            }
        }

        if (onSpecialChoiceChange != null) {
            SpecialMenuFields(
                choice = specialChoice,
                onChoiceChange = onSpecialChoiceChange,
                chosenEvent = chosenEvent,
                onPickEvent = onPickEvent,
                enabled = !isSaving,
                eventError = eventError,
                compact = stackFields
            )
        }

        // An event menu's days are the event's, shown read-only on its card above: no date fields here.
        if (!specialChoice.isEventMenu) {
            AvailabilityCheckbox(
                label = "Available all year",
                checked = availableYearRound,
                enabled = !isSaving,
                onCheckedChange = onAvailableYearRoundChange
            )
        }

        if (!specialChoice.isEventMenu && (!stackFields || !availableYearRound)) {
            MenuEditorFieldPair(
                stackFields = stackFields,
                first = { fieldModifier ->
                    Column(modifier = fieldModifier) {
                        EditorFieldLabel(text = "Start date")
                        Spacer(Modifier.height(8.dp))
                        MenuDateInput(
                            title = "Start date",
                            value = availableFromDate,
                            onValueChange = onAvailableFromDateChange,
                            enabled = !availableYearRound && !isSaving,
                            isError = !fromDateIsValid || !dateOrderIsValid,
                            calendarOnly = stackFields
                        )
                    }
                },
                second = { fieldModifier ->
                    Column(modifier = fieldModifier) {
                        EditorFieldLabel(text = "End date")
                        Spacer(Modifier.height(8.dp))
                        MenuDateInput(
                            title = "End date",
                            value = availableUntilDate,
                            onValueChange = onAvailableUntilDateChange,
                            enabled = !availableYearRound && !isSaving,
                            isError = !untilDateIsValid || !dateOrderIsValid,
                            calendarOnly = stackFields,
                            minimumDate = availableFromDate
                        )
                    }
                }
            )
        }

        if (!specialChoice.isEventMenu && !availableYearRound && (!fromDateIsValid || !untilDateIsValid || !dateOrderIsValid)) {
            Text(
                text = "Use YYYY-MM-DD and make sure the end date is not before the start date.",
                fontFamily = Inter(),
                fontSize = 12.sp,
                color = Color(0xFFB13A2F)
            )
        }

        AvailabilityCheckbox(
            label = "Available all day",
            checked = availableAllDay,
            enabled = !isSaving,
            onCheckedChange = onAvailableAllDayChange
        )

        if (!stackFields || !availableAllDay) {
            MenuEditorFieldPair(
                stackFields = stackFields,
                first = { fieldModifier ->
                    Column(modifier = fieldModifier) {
                        EditorFieldLabel(text = "Available from")
                        Spacer(Modifier.height(8.dp))
                        MenuEditorInput(
                            value = availableFrom,
                            onValueChange = onAvailableFromChange,
                            placeholder = "17:00",
                            enabled = !availableAllDay && !isSaving,
                            isError = !fromIsValid,
                            trailingIcon = Icons.Outlined.AccessTime
                        )
                    }
                },
                second = { fieldModifier ->
                    Column(modifier = fieldModifier) {
                        EditorFieldLabel(text = "Available until")
                        Spacer(Modifier.height(8.dp))
                        MenuEditorInput(
                            value = availableUntil,
                            onValueChange = onAvailableUntilChange,
                            placeholder = "23:00",
                            enabled = !availableAllDay && !isSaving,
                            isError = !untilIsValid,
                            trailingIcon = Icons.Outlined.AccessTime
                        )
                    }
                }
            )
        }

        if (!availableAllDay && (!fromIsValid || !untilIsValid)) {
            Text(
                text = "Use 24-hour time in HH:mm format, for example 17:00.",
                fontFamily = Inter(),
                fontSize = 12.sp,
                color = Color(0xFFB13A2F)
            )
        }

        EditorFieldLabel(text = "Notes")
        MenuEditorInput(
            value = description,
            onValueChange = onDescriptionChange,
            placeholder = "Add any notes about when or how this menu is used",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            enabled = !isSaving
        )

        errorMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF1EF), RoundedCornerShape(9.dp))
                    .border(1.dp, Color(0xFFF2C4BE), RoundedCornerShape(9.dp))
                    .padding(12.dp),
                fontFamily = Inter(),
                fontSize = 13.sp,
                color = Color(0xFFB13A2F)
            )
        }
    }
}

@Composable
private fun AvailabilityCheckbox(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) EditorOlive else Color.White)
                .border(
                    width = if (checked) 1.dp else 1.5.dp,
                    color = if (checked) EditorOlive else Color(0xFF8B8F87),
                    shape = RoundedCornerShape(5.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = Color.White
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = EditorInk
        )
    }
}

@Composable
private fun MenuEditorVisuals(
    previewEvent: MenuEvent? = null,
    name: String,
    description: String,
    active: Boolean,
    availableFrom: String?,
    availableUntil: String?,
    selectedColor: String,
    onColorChange: (String) -> Unit,
    colorIsValid: Boolean,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EditorSurface)
                .border(1.dp, EditorBorder, RoundedCornerShape(14.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            MenuCoverPreview(
                name = name,
                description = description,
                shownInPos = active,
                availableFrom = availableFrom,
                availableUntil = availableUntil,
                color = selectedColor.takeIf { colorIsValid } ?: "#AEBE95",
                modifier = Modifier
                    .width(240.dp)
                    .height(315.dp),
                event = previewEvent
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Menu color",
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = EditorInk
            )
            MenuEditorInput(
                value = selectedColor,
                onValueChange = onColorChange,
                placeholder = "#AEBE95",
                modifier = Modifier.width(120.dp),
                enabled = !isSaving,
                isError = !colorIsValid
            )
        }

        if (!colorIsValid) {
            Text(
                text = "Enter a 6-digit hex color.",
                modifier = Modifier.fillMaxWidth(),
                fontFamily = Inter(),
                fontSize = 11.sp,
                color = Color(0xFFB13A2F)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MenuColorSuggestions.forEach { suggestion ->
                MenuColorSwatch(
                    suggestion = suggestion,
                    selected = selectedColor.equals(suggestion.hex, ignoreCase = true),
                    enabled = !isSaving,
                    onClick = { onColorChange(suggestion.hex) }
                )
            }
        }
    }
}

@Composable
private fun MenuColorSwatch(
    suggestion: MenuColorSuggestion,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    diameter: Dp = 30.dp
) {
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = suggestion.name }
            .background(Color.White)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) EditorOlive else EditorBorder,
                shape = CircleShape
            )
            .padding(if (selected) 3.dp else 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(parseEditorColor(suggestion.hex))
        )
    }
}

@Composable
private fun EditorFieldLabel(
    text: String,
    required: Boolean = false,
    error: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            fontFamily = Inter(),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (error) Color(0xFFD6453D) else EditorInk
        )
        if (required) {
            Text(
                text = " *",
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFD6453D)
            )
        }
    }
}

/** Compact field in the Reservations form style: 46 dp tall, 13 sp text, light border, green when focused. */
@Composable
internal fun MenuEditorInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    trailingIcon: ImageVector? = null,
    trailingIconLabel: String? = null,
    onTrailingIconClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        isError -> Color(0xFFB13A2F)
        focused && !readOnly -> EditorOlive
        else -> EditorBorder
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (singleLine) Modifier.height(46.dp) else Modifier.heightIn(min = (20 * minLines + 24).dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Color.White else EditorSurface)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(start = 12.dp, end = if (trailingIcon != null) 6.dp else 12.dp, top = if (singleLine) 0.dp else 12.dp, bottom = if (singleLine) 0.dp else 12.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
    ) {
        BasicTextField(
            value = value,
            onValueChange = { raw -> onValueChange(if (singleLine) raw.replace(Regex("[\\r\\n\\t]+"), " ") else raw) },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            maxLines = maxLines,
            interactionSource = interaction,
            cursorBrush = SolidColor(EditorOlive),
            textStyle = TextStyle(
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = if (enabled) EditorInk else EditorMuted
            ),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFF9A9B97), maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                    }
                    inner()
                }
            }
        )
        trailingIcon?.let { icon ->
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (onTrailingIconClick != null) {
                            Modifier.clickable(enabled = enabled, onClickLabel = trailingIconLabel, onClick = onTrailingIconClick)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, trailingIconLabel, Modifier.size(18.dp), tint = if (enabled) EditorInk else EditorMuted)
            }
        }
    }
}

private fun displayTime(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return value.take(5).takeIf { isValidTime(it) }
}

private fun isValidTime(value: String): Boolean {
    return Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(value)
}

private fun isValidIsoDate(value: String): Boolean {
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(value) ?: return false
    val year = match.groupValues[1].toIntOrNull() ?: return false
    val month = match.groupValues[2].toIntOrNull() ?: return false
    val day = match.groupValues[3].toIntOrNull() ?: return false
    if (year !in 1..9999 || month !in 1..12) return false

    val leapYear = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
    val daysInMonth = when (month) {
        2 -> if (leapYear) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return day in 1..daysInMonth
}

private fun normalizeHexInput(value: String): String {
    val digits = value.trim()
        .removePrefix("#")
        .filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        .take(6)
        .uppercase()
    return "#$digits"
}

private fun isValidHexColor(value: String): Boolean {
    return Regex("^#[0-9A-Fa-f]{6}$").matches(value)
}

private fun parseEditorColor(value: String): Color {
    return try {
        val rgb = value.removePrefix("#").toLong(16)
        Color((0xFF000000L or rgb).toInt())
    } catch (_: NumberFormatException) {
        EditorOlive
    }
}
