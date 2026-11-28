package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.saporini.mobile_desktop.core.components.Kit
import kotlin.time.Instant

// How a shift shows on the calendars: what it is right now (planned, on duty, worked...), its colours, label and icon.

internal enum class ShiftLook(val label: String) {
    PLANNED("Planned"), LATE("Not in yet"), ON_DUTY("On duty"), ON_BREAK("On break"), WORKED("Worked"),
    NO_CLOCK_IN("No clock-in"), MISSED("Missed"), CANCELLED("Cancelled")
}

internal fun lookOf(item: ShiftItem, now: Instant): ShiftLook = when (item.status) {
    "OPEN" -> ShiftLook.ON_DUTY
    "ON_BREAK" -> ShiftLook.ON_BREAK
    "CLOSED" -> ShiftLook.WORKED
    "MISSED" -> ShiftLook.MISSED
    "CANCELLED" -> ShiftLook.CANCELLED
    else -> {
        val start = item.scheduledStart?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val end = item.scheduledEnd?.let { runCatching { Instant.parse(it) }.getOrNull() }
        when {
            end != null && end < now -> ShiftLook.NO_CLOCK_IN
            start != null && start < now -> ShiftLook.LATE
            else -> ShiftLook.PLANNED
        }
    }
}

/** Fill, border, the main text, the second line and the accent (bar, icon) of a shift. */
internal data class ShiftPalette(val fill: Color, val border: Color, val title: Color, val detail: Color, val accent: Color)

internal fun paletteOf(look: ShiftLook): ShiftPalette = when (look) {
    ShiftLook.PLANNED -> ShiftPalette(Color(0xFFF0F6F8), Color(0xFFD3E5EB), Kit.Ink, Kit.Blue, Kit.Blue)
    ShiftLook.LATE -> ShiftPalette(Color(0xFFFFF6E6), Color(0xFFF0D29E), Kit.Ink, Kit.Amber, Kit.Amber)
    ShiftLook.ON_DUTY -> ShiftPalette(Kit.Green, Kit.Green, Color.White, Color.White.copy(alpha = 0.92f), Color.White)
    ShiftLook.ON_BREAK -> ShiftPalette(Color(0xFFFFF2DC), Color(0xFFEBC88A), Kit.Ink, Kit.Amber, Kit.Amber)
    ShiftLook.WORKED -> ShiftPalette(Color(0xFFF4F6F2), Color(0xFFE2E6DF), Color(0xFF494C47), Kit.Muted, Kit.Green)
    ShiftLook.NO_CLOCK_IN -> ShiftPalette(Color(0xFFFFF0EB), Color(0xFFF0C6B8), Kit.Ink, Kit.Danger, Kit.Danger)
    ShiftLook.MISSED -> ShiftPalette(Color(0xFFFCEEED), Color(0xFFEDC4C0), Kit.Ink, Kit.Danger, Kit.Danger)
    ShiftLook.CANCELLED -> ShiftPalette(Color.White, Kit.Border, Kit.Faint, Kit.Faint, Kit.Faint)
}

internal fun iconOf(look: ShiftLook): ImageVector = when (look) {
    ShiftLook.PLANNED -> Icons.Outlined.Schedule
    ShiftLook.LATE -> Icons.Outlined.HourglassTop
    ShiftLook.ON_DUTY -> Icons.Outlined.PlayCircle
    ShiftLook.ON_BREAK -> Icons.Outlined.Coffee
    ShiftLook.WORKED -> Icons.Outlined.TaskAlt
    ShiftLook.NO_CLOCK_IN -> Icons.Outlined.ReportProblem
    ShiftLook.MISSED -> Icons.Outlined.PersonOff
    ShiftLook.CANCELLED -> Icons.Outlined.EventBusy
}

/** The calendar's "Show" choices. */
internal enum class ShiftShow(val label: String, val icon: ImageVector, val color: Color) {
    ALL("All shifts", Icons.Outlined.CalendarMonth, Kit.Green),
    PLANNED("Planned", Icons.Outlined.Schedule, Kit.Blue),
    WORKING("Working now", Icons.Outlined.PlayCircle, Kit.Green),
    WORKED("Worked", Icons.Outlined.TaskAlt, Kit.Green),
    REVIEW("Needs a look", Icons.Outlined.ReportProblem, Kit.Amber),
    MISSED("Missed", Icons.Outlined.PersonOff, Kit.Danger),
    CANCELLED("Cancelled", Icons.Outlined.EventBusy, Kit.Grey);

    fun matches(look: ShiftLook) = when (this) {
        ALL -> look != ShiftLook.CANCELLED
        PLANNED -> look == ShiftLook.PLANNED
        WORKING -> look == ShiftLook.ON_DUTY || look == ShiftLook.ON_BREAK || look == ShiftLook.LATE
        WORKED -> look == ShiftLook.WORKED
        REVIEW -> look == ShiftLook.NO_CLOCK_IN || look == ShiftLook.LATE
        MISSED -> look == ShiftLook.MISSED
        CANCELLED -> look == ShiftLook.CANCELLED
    }
}

/** A role group's colour and icon in the side panel and the group headings. */
internal fun groupColor(group: StaffGroup): Color = when (group.key) {
    "MANAGER" -> Kit.Purple
    "KITCHEN" -> Kit.Amber
    "WAITER" -> Kit.Blue
    "ADMIN", "OWNER", "CO_OWNER" -> Kit.Green
    else -> Kit.Grey
}

internal fun groupIcon(group: StaffGroup): ImageVector = when (group.key) {
    "MANAGER" -> Icons.Outlined.ManageAccounts
    "KITCHEN" -> Icons.Outlined.SoupKitchen
    "WAITER" -> Icons.Outlined.RoomService
    "ADMIN", "OWNER", "CO_OWNER", "PLATFORM" -> Icons.Outlined.AdminPanelSettings
    "VIEWER" -> Icons.Outlined.Visibility
    else -> Icons.Outlined.Badge
}
