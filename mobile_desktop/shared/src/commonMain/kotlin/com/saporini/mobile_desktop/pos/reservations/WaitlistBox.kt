package com.saporini.mobile_desktop.pos.reservations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import com.saporini.mobile_desktop.pos.reservations.data.api.ReservationApi
import com.saporini.mobile_desktop.pos.reservations.data.dto.SeatWaitlistEntryRequestDto
import com.saporini.mobile_desktop.pos.reservations.data.dto.WaitlistEntryRequestDto
import com.saporini.mobile_desktop.pos.reservations.data.dto.toDomain
import com.saporini.mobile_desktop.pos.reservations.domain.model.WaitlistEntry
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableStatus
import com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.Instant

// Walk-ins waiting at the door when every table is taken: how long each group has waited and roughly when a table
// big enough frees up. Staff seat them at a free table, or remove them if they leave (never a no-show).
// Used on today's reservations overview and on the Tables screen; it loads its own data every 30 seconds.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WaitlistSection(modifier: Modifier = Modifier, onSeated: () -> Unit = {}) {
    val api = koinInject<ReservationApi>()
    val tablesRepository = koinInject<TableLayoutRepository>()
    val session = koinInject<SessionManager>()
    val user by session.currentUser.collectAsState()
    val restaurantId = user?.restaurantId
    val branchId = user?.defaultBranchId
    val canManage = "RESERVATION_MANAGE" in user?.permissions.orEmpty()
    val coroutines = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<WaitlistEntry>>(emptyList()) }
    var tables by remember { mutableStateOf<List<LayoutTable>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var seating by remember { mutableStateOf<WaitlistEntry?>(null) }
    var removing by remember { mutableStateOf<WaitlistEntry?>(null) }

    suspend fun load() {
        if (restaurantId == null || branchId == null) return
        try {
            entries = api.getWaitlist(restaurantId, branchId).map { it.toDomain() }
            error = null
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            error = failure.message ?: "Couldn't load the waitlist"
        }
    }

    fun act(block: suspend () -> Unit) {
        coroutines.launch {
            try {
                block()
                load()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                error = failure.message ?: "Couldn't save the change"
            }
        }
    }

    LaunchedEffect(restaurantId, branchId, reload) {
        while (true) {
            load()
            delay(30_000)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (entries.isEmpty()) "Waitlist" else "Waitlist · ${entries.size}",
                Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FormInk
            )
            if (canManage) {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable { adding = true }.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(15.dp), tint = FormGreen)
                    Text("Add", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormGreen)
                }
            }
        }
        error?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = FormDanger) }
        if (entries.isEmpty() && error == null) {
            Text("Nobody is waiting.", fontFamily = Inter(), fontSize = 12.sp, color = FormMuted)
        }
        entries.forEach { entry ->
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, FormBorder)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${entry.guestName} · ${entry.partySize} ${if (entry.partySize == 1) "guest" else "guests"}",
                            Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FormInk,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "waiting ${duration(entry.waitedMinutes)}",
                            fontFamily = Inter(), fontSize = 11.sp, color = if (entry.waitedMinutes >= 20) LateColor else FormMuted
                        )
                    }
                    Text(
                        when {
                            entry.tableFreeNow -> "A table is free now"
                            entry.tableFreeAround != null -> "Table free around ${entry.tableFreeAround.clockTime()}"
                            else -> "No table big enough on the floor plan"
                        },
                        fontFamily = Inter(), fontSize = 12.sp, color = if (entry.tableFreeNow) FormGreen else FormMuted
                    )
                    entry.contactPhone?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = FormMuted) }
                    entry.note?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = FormMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    if (canManage) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Seat",
                                Modifier.clip(RoundedCornerShape(6.dp)).background(FormGreen).clickable {
                                    coroutines.launch {
                                        if (restaurantId != null && branchId != null) {
                                            tables = runCatching { tablesRepository.getTableLayout(restaurantId, branchId).tables }.getOrDefault(emptyList())
                                        }
                                        seating = entry
                                    }
                                }.padding(horizontal = 12.dp, vertical = 5.dp),
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White
                            )
                            Text(
                                "They left",
                                Modifier.clip(RoundedCornerShape(6.dp)).clickable { removing = entry }.padding(horizontal = 8.dp, vertical = 5.dp),
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = FormMuted
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding && restaurantId != null && branchId != null) {
        AddToWaitlistDialog(
            onDismiss = { adding = false },
            onAdd = { request ->
                adding = false
                act { api.addToWaitlist(restaurantId, branchId, request) }
            }
        )
    }
    seating?.let { entry ->
        // Free tables that fit first, then the other free ones.
        val free = tables.filter { it.active && it.mergedIntoTableId == null && (it.status == LayoutTableStatus.AVAILABLE || it.status == LayoutTableStatus.RESERVED) }
            .sortedWith(compareBy({ it.effectiveCapacity < entry.partySize }, { it.effectiveCapacity }, { it.tableNumber }))
        MenuNestedDialog(
            onDismissRequest = { seating = null },
            title = { Text("Seat ${entry.guestName}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (free.isEmpty()) "No table is free right now." else "Pick a free table for ${entry.partySize} ${if (entry.partySize == 1) "guest" else "guests"}.",
                        fontFamily = Inter(), fontSize = 13.sp, color = FormMuted
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        free.forEach { table ->
                            val fits = table.effectiveCapacity >= entry.partySize
                            Surface(
                                onClick = {
                                    seating = null
                                    if (restaurantId != null && branchId != null) {
                                        act {
                                            api.seatFromWaitlist(restaurantId, branchId, entry.id, SeatWaitlistEntryRequestDto(table.id))
                                            onSeated()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (fits) FormGreenSoft else Color.White,
                                border = BorderStroke(1.dp, if (fits) FormGreen else FormBorder)
                            ) {
                                Text(
                                    "${table.tableNumber} · ${table.effectiveCapacity} seats",
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (fits) FormGreen else FormInk
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { seating = null }) { Text("Back", color = FormMuted, fontFamily = Inter()) } }
        )
    }
    removing?.let { entry ->
        ReasonPromptDialog(
            ReasonPrompt(
                "Remove ${entry.guestName}?", "They left before a table was free. This is never counted as a no-show.", "Remove",
                danger = true, reasonHint = "Note (optional)"
            ) {
                removing = null
                if (restaurantId != null && branchId != null) act { api.removeFromWaitlist(restaurantId, branchId, entry.id) }
            },
            onDismiss = { removing = null }
        )
    }
}

@Composable
private fun AddToWaitlistDialog(onDismiss: () -> Unit, onAdd: (WaitlistEntryRequestDto) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var guests by remember { mutableStateOf("2") }
    var note by remember { mutableStateOf("") }
    val count = guests.toIntOrNull()
    val ready = name.isNotBlank() && count != null && count in 1..200
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to the waitlist", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InputBox(Icons.Outlined.Person, name, "Name") { name = it.take(150) }
                InputBox(Icons.Outlined.Groups, guests, "Guests", keyboardType = KeyboardType.Number, isError = count == null || count !in 1..200) {
                    guests = it.filter(Char::isDigit).take(3)
                }
                InputBox(Icons.Outlined.Phone, phone, "Phone (to call when a table is ready)", keyboardType = KeyboardType.Phone) {
                    phone = it.take(50)
                }
                InputBox(Icons.Outlined.Notes, note, "Note (optional)") { note = it.take(500) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ready) onAdd(WaitlistEntryRequestDto(name.trim(), phone.trim().ifBlank { null }, count!!, note.trim().ifBlank { null }))
                },
                enabled = ready,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = FormGreen)
            ) { Text("Add", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back", color = FormMuted, fontFamily = Inter()) } }
    )
}

private fun String.clockTime(): String = runCatching {
    Instant.parse(this).toLocalDateTime(RestaurantTime.zone).let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }
}.getOrDefault("--:--")
