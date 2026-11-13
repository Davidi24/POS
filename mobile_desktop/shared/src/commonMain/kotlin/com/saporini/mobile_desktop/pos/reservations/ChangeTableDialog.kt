package com.saporini.mobile_desktop.pos.reservations

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationAvailabilityOption
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationsScreenModel
import com.saporini.mobile_desktop.pos.tables.ui.MapChoiceRow
import com.saporini.mobile_desktop.pos.tables.ui.MapChoices
import com.saporini.mobile_desktop.pos.tables.ui.MapChoicesHeading
import com.saporini.mobile_desktop.pos.tables.ui.MapChoicesNote
import com.saporini.mobile_desktop.pos.tables.ui.TableMapDialog

// Picks new tables for an existing reservation on the floor plan; its own current tables count as free.
// Wide windows put the choices beside the map, narrow ones under it (user, 2026-11-02: "make it responsive").
@Composable
internal fun ChangeTableDialog(
    model: ReservationsScreenModel,
    reservation: Reservation,
    guests: Int,
    startIso: String,
    endIso: String,
    timeLabel: String,
    onDismiss: () -> Unit,
    onPick: (List<Pair<String, String>>) -> Unit,
    title: String = "Change table",
    confirmLabel: String = "Use this table",
    // Moving late guests: their current tables stay out (they don't fit there) and "no table" isn't a choice.
    keepCurrent: Boolean = true
) {
    val state by model.state.collectAsState()
    val planTableIds = remember(state.tableGroups) { state.tableGroups.flatMap { group -> group.tables.map { it.id } }.toSet() }
    val ownTables = remember(reservation.id) {
        reservation.tableAssignments.mapNotNull { assignment -> assignment.tableNumber?.let { assignment.tableId to it } }
    }
    var options by remember { mutableStateOf<List<ReservationAvailabilityOption>>(emptyList()) }
    var freeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf(if (keepCurrent) ownTables else emptyList()) }

    LaunchedEffect(startIso, endIso, guests) {
        loading = true
        model.tableSuggestions(startIso, endIso, guests, 15).onSuccess { found ->
            options = found.filter { option -> planTableIds.isEmpty() || option.tableIds.all { it in planTableIds } }
                .filter { option -> keepCurrent || option.tableIds.none { id -> ownTables.any { it.first == id } } }
                .take(6)
            if (!keepCurrent && picked.isEmpty()) options.firstOrNull()?.let { picked = it.tableIds.zip(it.tableNumbers) }
        }
        model.freeTableIds(startIso, endIso).onSuccess { free ->
            freeIds = if (keepCurrent) free + ownTables.map { table -> table.first } else free - ownTables.map { it.first }.toSet()
        }
        loading = false
    }

    TableMapDialog(
        title = title,
        detail = "$guests guests · $timeLabel",
        freeTableIds = freeIds,
        selectedTableIds = picked.map { it.first }.toSet(),
        onTableClick = { id, label, isFree ->
            when {
                picked.any { it.first == id } -> picked = picked.filterNot { it.first == id }
                isFree -> picked = picked + (id to label)
            }
        },
        confirmLabel = confirmLabel,
        confirmEnabled = keepCurrent || picked.isNotEmpty(),
        onConfirm = { onPick(picked) },
        onDismiss = onDismiss,
        takenLegend = "Already reserved at this time"
    ) { compact ->
        if (keepCurrent && ownTables.isNotEmpty()) {
            MapChoiceRow(
                icon = Icons.Outlined.TableRestaurant,
                title = (if (compact) "Keep " else "Keep current: ") + ownTables.joinToString(" + ") { it.second },
                detail = if (compact) null else "The table it has now",
                selected = picked == ownTables,
                compact = false
            ) { picked = ownTables }
        }
        MapChoicesHeading("Suggestions")
        when {
            loading -> CircularProgressIndicator(Modifier.size(18.dp), color = FormGreen, strokeWidth = 2.dp)
            options.isEmpty() -> MapChoicesNote("No other free table fits this time.")
        }
        MapChoices(compact) {
            options.forEachIndexed { index, option ->
                val tables = option.tableIds.zip(option.tableNumbers)
                MapChoiceRow(
                    icon = if (index == 0) Icons.Outlined.AutoAwesome else Icons.Outlined.TableRestaurant,
                    title = (if (index == 0 && !compact) "Best fit: " else "") + option.tableNumbers.joinToString(" + "),
                    detail = "${option.totalCapacity ?: 0} seats",
                    selected = picked == tables,
                    compact = compact
                ) { picked = tables }
            }
            if (keepCurrent) {
                MapChoiceRow(
                    icon = Icons.Outlined.TableRestaurant,
                    title = if (compact) "No table" else "No table (Unassigned)",
                    detail = if (compact) null else "Assign one later",
                    selected = picked.isEmpty(),
                    compact = compact
                ) { picked = emptyList() }
            }
        }
        if (!compact) MapChoicesNote("Or tap free tables on the map.")
    }
}
