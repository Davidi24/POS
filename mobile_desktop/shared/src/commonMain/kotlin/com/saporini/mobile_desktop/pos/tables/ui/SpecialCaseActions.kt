package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.tables.domain.model.SpecialCaseAction
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val Ink = Color(0xFF242424)
private val Muted = Color(0xFF71736E)
private val Line = Color(0xFFE8E5E1)

// How each kind looks and what putting it back is called.
private data class ActionLook(val icon: ImageVector, val color: Color, val button: String)

private fun SpecialCaseAction.look(): ActionLook = when (kind) {
    "ORDER_CLOSED" -> ActionLook(Icons.Filled.MoneyOff, Color(0xFFDC2626), "Go to History")
    "BOOKING_MOVED" -> ActionLook(Icons.Filled.SyncAlt, TableNextBookingPurple, "Move it back")
    "BOOKING_NO_ACTION" -> ActionLook(Icons.Filled.EventBusy, TableNextBookingPurple, "Put it back")
    else -> ActionLook(Icons.Filled.TableRestaurant, Color(0xFF4F7942), "Put guests back")
}

// The time; with the day when it isn't today.
internal fun String.whenItHappened(): String = runCatching {
    val zone = TimeZone.currentSystemDefault()
    val day = Instant.parse(this).toLocalDateTime(zone).date
    val today = Clock.System.now().toLocalDateTime(zone).date
    if (day == today) clockTime() else "Yesterday ${clockTime()}".takeIf { today.toEpochDays() - day.toEpochDays() == 1L } ?: "$day ${clockTime()}"
}.getOrDefault(clockTime())

// What the system did by itself because nobody acted (Settings → Tables → Special cases), each one with its way back.
@Composable
internal fun SpecialCaseActionsDialog(
    actions: List<SpecialCaseAction>,
    freeTables: List<FloorPlanTable>,
    isSaving: Boolean,
    onReturn: (SpecialCaseAction, String?) -> Unit,
    onOrderInHistory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = "No action taken",
        subtitle = "What the system did because nobody acted.",
        onDismiss = onDismiss,
        maxWidth = 640.dp
    ) {
        if (actions.isEmpty()) {
            Text("Nothing left to put back.", fontFamily = Inter(), fontSize = 14.sp, color = Muted, modifier = Modifier.padding(vertical = 12.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.forEach { action ->
                // An order is reopened only from History (user, 2026-11-01).
                ActionRow(action, freeTables, isSaving) { row, tableId ->
                    if (row.kind == "ORDER_CLOSED") row.orderId?.let(onOrderInHistory) else onReturn(row, tableId)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    action: SpecialCaseAction,
    freeTables: List<FloorPlanTable>,
    isSaving: Boolean,
    onReturn: (SpecialCaseAction, String?) -> Unit
) {
    val look = action.look()
    val narrow = LocalNarrowDialog.current
    val pickTable = !action.tableFree
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(look.color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(look.icon, null, Modifier.size(20.dp), tint = look.color)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(action.summary, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Ink)
                    Text(
                        listOfNotNull(action.detail, action.happenedAt.whenItHappened()).joinToString(" · "),
                        fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Muted
                    )
                }
                if (!pickTable && !narrow) KitButton(look.button, { onReturn(action, null) }, style = ButtonStyle.PRIMARY_OUTLINE, enabled = !isSaving)
            }
            if (!pickTable && narrow) KitButton(look.button, { onReturn(action, null) }, Modifier.fillMaxWidth(), style = ButtonStyle.PRIMARY_OUTLINE, enabled = !isSaving)
            if (pickTable) TablePicker(action, freeTables, isSaving, onReturn)
        }
    }
}

// Its table has been taken: which free one instead (user, 2026-11-01). A booking needs one it fits.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TablePicker(
    action: SpecialCaseAction,
    freeTables: List<FloorPlanTable>,
    isSaving: Boolean,
    onReturn: (SpecialCaseAction, String?) -> Unit
) {
    val fitting = if (action.kind == "BOOKING_NO_ACTION") freeTables.filter { it.seatCount >= (action.guests ?: 1) } else freeTables
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF8EB)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            (action.tableNumber ?: "Its table") + (if (fitting.isEmpty()) " isn't free, and no free table fits right now." else " isn't free now. Choose a table:"),
            fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.sp, color = Color(0xFF92580A)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fitting.forEach { table ->
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White)
                        .border(1.dp, Color(0xFF4F7942), RoundedCornerShape(8.dp))
                        .clickable(enabled = !isSaving) { table.id?.let { onReturn(action, it) } }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(table.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Color(0xFF4F7942))
                    Text("${table.seatCount} seats", fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Muted)
                }
            }
        }
    }
}
