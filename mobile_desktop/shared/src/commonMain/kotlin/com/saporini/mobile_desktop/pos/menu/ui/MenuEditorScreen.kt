package com.saporini.mobile_desktop.pos.menu.ui

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.ui.isWidePhoneWindow
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu

private val EditorOlive = Color(0xFF94A27F)
private val EditorInk = Color(0xFF242522)
private val EditorMuted = Color(0xFF71736E)
private val EditorBorder = Color(0xFFE2E3DE)
private val EditorSurface = Color(0xFFF7F7F5)

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
fun MenuEditorDialog(
    menu: Menu?,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onDeleteMenu: () -> Unit = { },
    onSave: (
        name: String,
        description: String?,
        active: Boolean,
        availableFrom: String?,
        availableUntil: String?,
        availableFromDate: String?,
        availableUntilDate: String?,
        color: String
    ) -> Unit
) {
    var name by remember(menu?.id) { mutableStateOf(menu?.name.orEmpty()) }
    var description by remember(menu?.id) { mutableStateOf(menu?.description.orEmpty()) }
    var active by remember(menu?.id) { mutableStateOf(menu?.active ?: true) }
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
        mutableStateOf(normalizeHexInput(menu?.color ?: "#AEBE95"))
    }
    var showDeleteConfirm by remember(menu?.id) { mutableStateOf(false) }

    val isEditing = menu != null
    val colorIsValid = isValidHexColor(selectedColor)
    val fromIsValid = availableAllDay || isValidTime(availableFrom)
    val untilIsValid = availableAllDay || isValidTime(availableUntil)
    val fromDateIsValid = availableYearRound || isValidIsoDate(availableFromDate)
    val untilDateIsValid = availableYearRound || isValidIsoDate(availableUntilDate)
    val dateOrderIsValid = availableYearRound ||
        (fromDateIsValid && untilDateIsValid && availableFromDate <= availableUntilDate)
    val canSave = name.isNotBlank() &&
        colorIsValid &&
        fromIsValid &&
        untilIsValid &&
        fromDateIsValid &&
        untilDateIsValid &&
        dateOrderIsValid &&
        !isSaving
    val previewFrom = availableFrom.takeIf { !availableAllDay && isValidTime(it) }
    val previewUntil = availableUntil.takeIf { !availableAllDay && isValidTime(it) }

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
            if (isPhone) {
                PhoneMenuEditorPage(
                    isEditing = isEditing,
                    isSaving = isSaving,
                    canSave = canSave,
                    errorMessage = errorMessage,
                    onDismiss = onDismiss,
                    onDeleteMenu = { showDeleteConfirm = true },
                    onSave = {
                        onSave(
                            name.trim(),
                            description.trim().takeIf { it.isNotEmpty() },
                            active,
                            availableFrom.takeIf { !availableAllDay },
                            availableUntil.takeIf { !availableAllDay },
                            availableFromDate.takeIf { !availableYearRound },
                            availableUntilDate.takeIf { !availableYearRound },
                            normalizeHexInput(selectedColor)
                        )
                    },
                    fields = {
                        MenuEditorFields(
                            name = name,
                            onNameChange = { name = it },
                            description = description,
                            onDescriptionChange = { description = it },
                            active = active,
                            onActiveChange = { active = it },
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
                            stackFields = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    visuals = {
                        PhoneMenuEditorVisuals(
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
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .fillMaxHeight(0.9f)
                            .widthIn(max = 1180.dp)
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

                            if (wideLayout) {
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
                                        errorMessage = errorMessage,
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
                                        errorMessage = errorMessage,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    HorizontalDivider(color = EditorBorder)

                                    MenuEditorVisuals(
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

                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 1.dp,
                            color = EditorBorder
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isEditing) {
                                TextButton(
                                    onClick = { showDeleteConfirm = true },
                                    enabled = !isSaving,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = Color(0xFFB13A2F)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Delete Menu",
                                        fontFamily = Inter(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFB13A2F)
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
                                onClick = {
                                    onSave(
                                        name.trim(),
                                        description.trim().takeIf { it.isNotEmpty() },
                                        active,
                                        availableFrom.takeIf { !availableAllDay },
                                        availableUntil.takeIf { !availableAllDay },
                                        availableFromDate.takeIf { !availableYearRound },
                                        availableUntilDate.takeIf { !availableYearRound },
                                        normalizeHexInput(selectedColor)
                                    )
                                },
                                enabled = canSave,
                                colors = ButtonDefaults.buttonColors(containerColor = EditorOlive),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
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
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
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
                Text(
                    text = "Are you sure you want to delete this menu? This cannot be undone.",
                    fontFamily = Inter(),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = EditorMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteMenu()
                    }
                ) {
                    Text(
                        text = "Yes, Delete",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB13A2F)
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

@Composable
private fun PhoneMenuEditorPage(
    isEditing: Boolean,
    isSaving: Boolean,
    canSave: Boolean,
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
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
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
                TextButton(
                    onClick = onDeleteMenu,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFFB13A2F)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Delete Menu",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB13A2F)
                    )
                }
            }
        }

        HorizontalDivider(color = EditorBorder)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (isWidePhone) 6.dp else 12.dp),
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
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSave()
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = if (isWidePhone) 44.dp else 50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EditorOlive),
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
    }
}

@Composable
private fun PhoneMenuEditorVisuals(
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
        OutlinedTextField(
            value = selectedColor,
            onValueChange = onColorChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("#AEBE95") },
            singleLine = true,
            enabled = !isSaving,
            isError = !colorIsValid,
            shape = RoundedCornerShape(9.dp),
            colors = editorOutlinedTextFieldColors()
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
                active = active,
                availableFrom = availableFrom,
                availableUntil = availableUntil,
                color = selectedColor.takeIf { colorIsValid } ?: "#AEBE95",
                modifier = Modifier.width(previewWidth).height(previewWidth * 1.3125f)
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
                EditorFieldLabel(text = "Menu name", required = true)
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (active) "Active" else "Inactive",
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

            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("For example: Dinner Menu") },
                singleLine = true,
                enabled = !isSaving,
                shape = RoundedCornerShape(9.dp),
                colors = editorOutlinedTextFieldColors()
            )
        }

        AvailabilityCheckbox(
            label = "Available year-round",
            checked = availableYearRound,
            enabled = !isSaving,
            onCheckedChange = onAvailableYearRoundChange
        )

        if (!stackFields || !availableYearRound) {
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
                            calendarOnly = stackFields,
                            colors = editorOutlinedTextFieldColors()
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
                            minimumDate = availableFromDate,
                            colors = editorOutlinedTextFieldColors()
                        )
                    }
                }
            )
        }

        if (!availableYearRound && (!fromDateIsValid || !untilDateIsValid || !dateOrderIsValid)) {
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
                        OutlinedTextField(
                            value = availableFrom,
                            onValueChange = onAvailableFromChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("17:00") },
                            singleLine = true,
                            enabled = !availableAllDay && !isSaving,
                            isError = !fromIsValid,
                            trailingIcon = {
                                Icon(Icons.Outlined.AccessTime, contentDescription = null)
                            },
                            shape = RoundedCornerShape(9.dp),
                            colors = editorOutlinedTextFieldColors()
                        )
                    }
                },
                second = { fieldModifier ->
                    Column(modifier = fieldModifier) {
                        EditorFieldLabel(text = "Available until")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = availableUntil,
                            onValueChange = onAvailableUntilChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("23:00") },
                            singleLine = true,
                            enabled = !availableAllDay && !isSaving,
                            isError = !untilIsValid,
                            trailingIcon = {
                                Icon(Icons.Outlined.AccessTime, contentDescription = null)
                            },
                            shape = RoundedCornerShape(9.dp),
                            colors = editorOutlinedTextFieldColors()
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

        EditorFieldLabel(text = "Description")
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Describe when or how this menu is used") },
            minLines = 4,
            maxLines = 6,
            enabled = !isSaving,
            shape = RoundedCornerShape(9.dp),
            colors = editorOutlinedTextFieldColors()
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
                active = active,
                availableFrom = availableFrom,
                availableUntil = availableUntil,
                color = selectedColor.takeIf { colorIsValid } ?: "#AEBE95",
                modifier = Modifier
                    .width(240.dp)
                    .height(315.dp)
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
            OutlinedTextField(
                value = selectedColor,
                onValueChange = onColorChange,
                modifier = Modifier.width(132.dp),
                placeholder = { Text("#AEBE95") },
                singleLine = true,
                enabled = !isSaving,
                isError = !colorIsValid,
                shape = RoundedCornerShape(9.dp),
                colors = editorOutlinedTextFieldColors()
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
    required: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            fontFamily = Inter(),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = EditorInk
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

@Composable
private fun editorOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Black,
    unfocusedBorderColor = Color.Black,
    disabledBorderColor = Color.Black.copy(alpha = 0.45f),
    errorBorderColor = Color(0xFFB13A2F)
)

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
