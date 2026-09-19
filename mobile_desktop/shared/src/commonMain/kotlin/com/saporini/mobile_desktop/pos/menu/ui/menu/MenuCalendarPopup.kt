package com.saporini.mobile_desktop.pos.menu.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

private val CalendarOlive = Color(0xFF94A27F)
private val CalendarInk = Color(0xFF242522)

@Composable
internal fun MenuDateInput(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isError: Boolean,
    calendarOnly: Boolean,
    colors: TextFieldColors,
    minimumDate: String? = null
) {
    var calendarOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val openCalendar = {
        focusManager.clearFocus()
        keyboard?.hide()
        calendarOpen = true
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            enabled = enabled,
            readOnly = calendarOnly,
            isError = isError,
            trailingIcon = {
                IconButton(onClick = openCalendar, enabled = enabled) {
                    Icon(Icons.Outlined.CalendarToday, "Choose $title")
                }
            },
            shape = RoundedCornerShape(9.dp),
            colors = colors
        )
        if (calendarOnly) {
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(9.dp))
                    .clickable(enabled = enabled, onClickLabel = "Choose $title", onClick = openCalendar)
                    .semantics { contentDescription = "$title: ${value.ifBlank { "Choose date" }}" }
            )
        }
    }
    if (calendarOpen) {
        MenuCalendarPopup(
            title = title,
            initialDate = runCatching { LocalDate.parse(value) }.getOrNull(),
            minimumDate = minimumDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onDismiss = { calendarOpen = false },
            onDateSelected = {
                onValueChange(it.toString())
                calendarOpen = false
            }
        )
    }
}

@Composable
private fun MenuCalendarPopup(
    title: String,
    initialDate: LocalDate?,
    minimumDate: LocalDate?,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var selectedDate by remember {
        mutableStateOf((initialDate ?: today).let { date ->
            if (minimumDate != null && date < minimumDate) minimumDate else date
        })
    }
    var month by remember { mutableStateOf(LocalDate(selectedDate.year, selectedDate.month, 1)) }
    val nextMonth = month + DatePeriod(months = 1)
    val dayCount = (nextMonth - DatePeriod(days = 1)).dayOfMonth
    val firstDayOffset = month.dayOfWeek.ordinal
    val rowCount = (firstDayOffset + dayCount + 6) / 7

    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { month -= DatePeriod(months = 1) },
                        enabled = minimumDate == null || month > LocalDate(minimumDate.year, minimumDate.month, 1),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month")
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}",
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = CalendarInk
                        )
                    }
                    IconButton(onClick = { month = nextMonth }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month")
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                        Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                            Text(label, fontFamily = Inter(), fontSize = 12.sp)
                        }
                    }
                }
                repeat(rowCount) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { weekday ->
                            val day = week * 7 + weekday - firstDayOffset + 1
                            val date = if (day in 1..dayCount) LocalDate(month.year, month.month, day) else null
                            val selectable = date != null && (minimumDate == null || date >= minimumDate)
                            val isSelected = date == selectedDate
                            Box(
                                Modifier.weight(1f).height(40.dp).clip(CircleShape)
                                    .clickable(enabled = selectable) { date?.let { selectedDate = it } }
                                    .semantics {
                                        if (date != null) contentDescription = date.toString()
                                        selected = isSelected
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (date != null) {
                                    Box(
                                        Modifier.size(32.dp).background(
                                            if (isSelected) CalendarOlive else Color.Transparent, CircleShape
                                        ).then(
                                            if (date == today && !isSelected) Modifier.border(1.dp, CalendarOlive, CircleShape)
                                            else Modifier
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            day.toString(), fontFamily = Inter(), fontSize = 13.sp,
                                            color = when {
                                                isSelected -> Color.White
                                                selectable -> CalendarInk
                                                else -> CalendarInk.copy(alpha = 0.3f)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Selected: $selectedDate", fontFamily = Inter(), fontSize = 12.sp, color = CalendarInk)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDateSelected(selectedDate) }) {
                Text("Use date", color = CalendarOlive, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = CalendarInk) } }
    )
}
