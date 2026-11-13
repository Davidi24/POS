package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.tables.domain.model.BookingTableOption
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable

// Joining a table whose booking is near (user, 2026-11-01): move the booking now, or join and handle it when it's due.
// The new table is picked on the floor plan (user, 2026-11-02: "it opens the map all the time").
@Composable
internal fun JoinBookingDialog(
    tables: List<LayoutTable>,
    joiningIds: Set<String>,
    canMove: Boolean,
    options: suspend (String) -> List<BookingTableOption>,
    onDismiss: () -> Unit,
    onJoin: (List<Pair<String, BookingTableOption>>) -> Unit
) {
    var offered by remember { mutableStateOf(emptyMap<String, List<BookingTableOption>>()) }
    var picks by remember { mutableStateOf(emptyMap<String, BookingTableOption>()) }
    var mapFor by remember { mutableStateOf<LayoutTable?>(null) }
    LaunchedEffect(tables) {
        if (canMove) tables.filter { it.nextReservationId != null }.forEach { table ->
            offered = offered + (table.id to options(table.id).filter { option -> option.tableIds.none { it in joiningIds } })
        }
    }
    fun LayoutTable.bookingLine() = listOfNotNull(
        nextReservationStart?.clockTime(),
        nextReservationName?.takeIf { it.isNotBlank() },
        nextReservationPartySize?.let { if (it == 1) "1 guest" else "$it guests" }
    ).joinToString(" · ")
    // Two bookings can't move onto the same free table.
    fun choicesFor(table: LayoutTable): List<BookingTableOption> {
        val taken = picks.filterKeys { it != table.id }.values.flatMap { it.tableIds }
        return offered[table.id].orEmpty().filter { option -> option.tableIds.none { it in taken } }
    }
    AppDialog(
        title = if (tables.size == 1) "${tables.single().tableNumber} has a booking" else "These tables have bookings",
        onDismiss = onDismiss,
        maxWidth = 480.dp,
        buttons = {
            KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
            KitButton(if (picks.isEmpty()) "Join anyway" else "Move and join", { onJoin(picks.toList()) })
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            tables.forEach { table ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.Border, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        listOf(table.tableNumber, table.bookingLine()).filter { it.isNotBlank() }.joinToString(" · "),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink
                    )
                    val pick = picks[table.id]
                    when {
                        pick != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Moves to " + pick.tableNumbers.joinToString(" + ") + (pick.seats?.let { " · $it seats" } ?: ""),
                                Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Green
                            )
                            KitButton("Change", { mapFor = table }, style = ButtonStyle.SECONDARY)
                            KitButton("Keep it", { picks = picks - table.id }, style = ButtonStyle.SECONDARY)
                        }
                        canMove && choicesFor(table).isNotEmpty() -> KitButton("Move it: choose on the map", { mapFor = table }, style = ButtonStyle.SECONDARY)
                        canMove && offered.containsKey(table.id) -> Text("No free table fits it.", fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted)
                    }
                }
            }
            Text("A booking you don't move stays on its table.", fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Muted)
        }
    }
    mapFor?.let { table ->
        BookingMoveDialog(
            title = "Move ${table.tableNumber}'s booking",
            detail = table.bookingLine(),
            options = choicesFor(table),
            onDismiss = { mapFor = null },
            onPick = { option ->
                picks = picks + (table.id to option)
                mapFor = null
            },
            confirmLabel = "Choose"
        )
    }
}
