package com.saporini.mobile_desktop.kds.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.OutdoorGrill
import androidx.compose.material.icons.outlined.RoomService
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.kds.model.KdsTicketItem
import com.saporini.mobile_desktop.kds.model.asInstant
import kotlin.time.Instant

/** The three columns of the board: new food, food on the stove, food on the pass. */
internal enum class Lane(val title: String, val color: Color, val icon: ImageVector) {
    NEW("New", Kit.Blue, Icons.Outlined.HourglassEmpty),
    COOKING("Cooking", Kit.Amber, Icons.Outlined.LocalFireDepartment),
    READY("Ready", Kit.Green, Icons.Outlined.RoomService)
}

internal fun KdsTicket.lane(): Lane = when {
    status in setOf("READY", "EXPO_READY") -> Lane.READY
    status == "IN_PROGRESS" || items.any { it.status == "IN_PROGRESS" } -> Lane.COOKING
    items.isNotEmpty() && items.all { it.status in setOf("READY", "EXPO_READY", "COMPLETED", "CANCELLED") } -> Lane.READY
    else -> Lane.NEW
}

internal fun itemStatusLabel(status: String): String = when (status) {
    "PENDING" -> "Waiting"
    "FIRED" -> "To start"
    "IN_PROGRESS" -> "Cooking"
    "READY", "EXPO_READY" -> "Ready"
    "COMPLETED" -> "Served"
    "CANCELLED" -> "Cancelled"
    else -> humanize(status)
}

internal fun itemStatusColor(status: String): Color = when (status) {
    "PENDING", "FIRED" -> Kit.Blue
    "IN_PROGRESS" -> Kit.Amber
    "READY", "EXPO_READY", "COMPLETED" -> Kit.Green
    "CANCELLED" -> Kit.Grey
    else -> Kit.Grey
}

internal fun ticketStatusLabel(status: String): String = when (status) {
    "PENDING" -> "Held"
    "FIRED" -> "New"
    "IN_PROGRESS" -> "Cooking"
    "READY", "EXPO_READY" -> "Ready"
    "COMPLETED" -> "Served"
    "CANCELLED" -> "Cancelled"
    else -> humanize(status)
}

internal fun priorityLabel(priority: String): String? = when (priority) {
    "RUSH" -> "Rush"
    "VIP" -> "VIP"
    "HOLD_FIRE" -> "Hold"
    else -> null
}

internal fun priorityColor(priority: String): Color = when (priority) {
    "RUSH" -> Kit.Danger
    "VIP" -> Kit.Purple
    else -> Kit.Grey
}

internal fun stationTypeLabel(type: String): String = when (type) {
    "PREP" -> "Prep"
    "GRILL" -> "Grill"
    "FRY" -> "Fryer"
    "GARDE_MANGER" -> "Cold kitchen"
    "BAR" -> "Bar"
    "DESSERT" -> "Desserts"
    "EXPO" -> "Pass"
    "PACKING" -> "Packing"
    else -> humanize(type)
}

internal fun stationTypeIcon(type: String): ImageVector = when (type) {
    "GRILL" -> Icons.Outlined.OutdoorGrill
    "FRY" -> Icons.Outlined.LocalFireDepartment
    "GARDE_MANGER" -> Icons.Outlined.AcUnit
    "BAR" -> Icons.Outlined.LocalBar
    "DESSERT" -> Icons.Outlined.Cake
    "EXPO" -> Icons.Outlined.CheckCircle
    "PACKING" -> Icons.Outlined.Inventory2
    "PREP" -> Icons.Outlined.SoupKitchen
    else -> Icons.Outlined.Kitchen
}

internal val StationTypes = listOf("PREP", "GRILL", "FRY", "GARDE_MANGER", "BAR", "DESSERT", "EXPO", "PACKING")

/** Since when the kitchen has this ticket: fired, else created. */
internal fun KdsTicket.since(): Instant? = firedAt.asInstant() ?: createdAt.asInstant()

internal fun minutesBetween(from: Instant?, now: Instant): Long? = from?.let { (now - it).inWholeMinutes.coerceAtLeast(0) }

/** "Table 4", "Takeaway · Anna" or "#A7K2": who the food is for. */
internal fun KdsTicket.title(): String = when {
    tableName != null -> tableName
    tableNumber != null -> "Table $tableNumber"
    customerName != null -> customerName
    else -> "#$orderNumber"
}

/** The variant, options and notes of a dish, in one line for the cook. */
internal fun KdsTicketItem.detailLine(): String? = listOfNotNull(
    variantNameSnapshot?.takeIf { it.isNotBlank() },
    modifiers.takeIf { it.isNotEmpty() }?.joinToString(", ") { m -> (if (m.quantity > 1) "${m.quantity}× " else "") + m.name + (m.notes?.let { " ($it)" } ?: "") },
    seatLabel?.let { "seat $it" }
).joinToString(" · ").ifBlank { null }
