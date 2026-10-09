package com.saporini.mobile_desktop.pos.reservations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationAvailabilityOption
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationsScreenModel
import com.saporini.mobile_desktop.pos.tables.ui.ReservationTablePicker

// Picks new tables for an existing reservation; its own current tables count as free.
@Composable
internal fun ChangeTableDialog(
    model: ReservationsScreenModel,
    reservation: Reservation,
    guests: Int,
    startIso: String,
    endIso: String,
    timeLabel: String,
    onDismiss: () -> Unit,
    onPick: (List<Pair<String, String>>) -> Unit
) {
    val state by model.state.collectAsState()
    val planTableIds = remember(state.tableGroups) { state.tableGroups.flatMap { group -> group.tables.map { it.id } }.toSet() }
    val ownTables = remember(reservation.id) {
        reservation.tableAssignments.mapNotNull { assignment -> assignment.tableNumber?.let { assignment.tableId to it } }
    }
    var options by remember { mutableStateOf<List<ReservationAvailabilityOption>>(emptyList()) }
    var freeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf(ownTables) }

    LaunchedEffect(startIso, endIso, guests) {
        loading = true
        model.tableSuggestions(startIso, endIso, guests, 15).onSuccess { found ->
            options = found.filter { option -> planTableIds.isEmpty() || option.tableIds.all { it in planTableIds } }.take(6)
        }
        model.freeTableIds(startIso, endIso).onSuccess { freeIds = it + ownTables.map { table -> table.first } }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)).padding(28.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.fillMaxWidth().fillMaxHeight(0.9f).shadow(22.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                    .background(Color.White).border(1.dp, FormBorder, RoundedCornerShape(16.dp))
            ) {
                Column(Modifier.width(380.dp).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Change table", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = FormInk)
                            Text("$guests guests · $timeLabel", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
                        }
                        CloseButton(onDismiss)
                    }
                    HorizontalDivider(color = FormBorder)
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (ownTables.isNotEmpty()) {
                            PickRow(
                                icon = Icons.Outlined.TableRestaurant,
                                title = "Keep current: ${ownTables.joinToString(" + ") { it.second }}",
                                detail = "The table it has now",
                                selected = picked == ownTables
                            ) { picked = ownTables }
                        }
                        Text("Suggestions", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormInk)
                        when {
                            loading -> CircularProgressIndicator(Modifier.size(18.dp), color = FormGreen, strokeWidth = 2.dp)
                            options.isEmpty() -> Text("No other free table fits this time.", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
                        }
                        options.forEachIndexed { index, option ->
                            val tables = option.tableIds.zip(option.tableNumbers)
                            PickRow(
                                icon = if (index == 0) Icons.Outlined.AutoAwesome else Icons.Outlined.TableRestaurant,
                                title = (if (index == 0) "Best fit: " else "") + option.tableNumbers.joinToString(" + "),
                                detail = "${option.totalCapacity ?: 0} seats",
                                selected = picked == tables
                            ) { picked = tables }
                        }
                        PickRow(
                            icon = Icons.Outlined.TableRestaurant,
                            title = "No table (Unassigned)",
                            detail = "Assign one later",
                            selected = picked.isEmpty()
                        ) { picked = emptyList() }
                        Text("Or tap free tables on the map.", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
                    }
                    HorizontalDivider(color = FormBorder)
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, FormBorder)) {
                            Box(contentAlignment = Alignment.Center) { Text("Cancel", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk) }
                        }
                        Surface(onClick = { onPick(picked) }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), color = FormGreen) {
                            Box(contentAlignment = Alignment.Center) { Text("Use this table", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White) }
                        }
                    }
                }
                VerticalDivider(color = FormBorder)
                Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFF7F7F5)).padding(12.dp)) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
                            .border(1.dp, FormBorder, RoundedCornerShape(12.dp)).padding(8.dp)
                    ) {
                        ReservationTablePicker(
                            freeTableIds = freeIds,
                            selectedTableIds = picked.map { it.first }.toSet(),
                            onTableClick = { id, label, isFree ->
                                when {
                                    picked.any { it.first == id } -> picked = picked.filterNot { it.first == id }
                                    isFree -> picked = picked + (id to label)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MapLegend(Color(0xFF147A25), "Free at this time")
                        MapLegend(Color(0xFFAA3F38), "Already reserved at this time")
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(icon: ImageVector, title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) FormGreenSoft else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) FormGreen else FormBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (selected) FormGreen else FormInk)
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk)
            Text(detail, fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
        }
    }
}

@Composable
private fun MapLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontFamily = Inter(), fontSize = 11.sp, color = FormMuted)
    }
}
