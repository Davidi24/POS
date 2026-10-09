package com.saporini.mobile_desktop.admin.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import com.saporini.mobile_desktop.pos.reservations.data.api.ReservationApi
import com.saporini.mobile_desktop.pos.reservations.data.dto.toDomain
import com.saporini.mobile_desktop.pos.reservations.data.dto.toDto
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationOccasion
import com.saporini.mobile_desktop.pos.reservations.domain.model.RestaurantEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject

private val Green = Color(0xFF4F7942)
private val Ink = Color(0xFF222426)
private val Muted = Color(0xFF747572)
private val Border = Color(0xFFE3E6E1)
private val Danger = Color(0xFFB13A2F)

// Settings → Reservations: the occasions a booking can have (each with its icon and options) and the restaurant's
// event nights with their special menu. Each list saves on its own.
@Composable
internal fun OccasionsAndEventsSettings(restaurantId: String, canEdit: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OccasionsEditor(restaurantId, canEdit)
        EventsEditor(restaurantId, canEdit)
    }
}

@Composable
private fun Card(title: String, note: String, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
            Text(note, fontFamily = Inter(), fontSize = 12.sp, color = Muted)
            content()
        }
    }
}

// ---- Occasions ----

private data class OccasionDraft(val code: String, val icon: String, val name: String, val options: String, val active: Boolean)

@Composable
private fun OccasionsEditor(restaurantId: String, canEdit: Boolean) {
    val api = koinInject<ReservationApi>()
    val coroutines = rememberCoroutineScope()
    var saved by remember { mutableStateOf<List<OccasionDraft>?>(null) }
    var drafts by remember { mutableStateOf<List<OccasionDraft>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun fromServer(list: List<ReservationOccasion>) = list.map { OccasionDraft(it.code, it.icon, it.name, it.options.joinToString(", "), it.active) }

    LaunchedEffect(restaurantId) {
        runCatching { api.getOccasions(restaurantId).map { it.toDomain() } }
            .onSuccess { saved = fromServer(it); drafts = saved.orEmpty() }
            .onFailure { message = it.message ?: "Couldn't load the occasions" }
    }

    Card("Occasions", "Staff pick one when booking (🎂 Birthday…) with its options; it shows on the booking, the table and the kitchen ticket.") {
        drafts.forEachIndexed { index, draft ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallField(draft.icon, "🎂", Modifier.width(52.dp), canEdit) { value -> drafts = drafts.toMutableList().also { it[index] = draft.copy(icon = value.take(8)) } }
                SmallField(draft.name, "Name", Modifier.width(150.dp), canEdit) { value -> drafts = drafts.toMutableList().also { it[index] = draft.copy(name = value.take(80)) } }
                SmallField(draft.options, "Options, separated by commas", Modifier.weight(1f), canEdit) { value ->
                    drafts = drafts.toMutableList().also { it[index] = draft.copy(options = value.take(800)) }
                }
                Switch(
                    checked = draft.active, enabled = canEdit,
                    onCheckedChange = { value -> drafts = drafts.toMutableList().also { it[index] = draft.copy(active = value) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Green, checkedThumbColor = Color.White)
                )
                if (canEdit) {
                    Icon(
                        Icons.Outlined.DeleteOutline, "Remove ${draft.name}",
                        Modifier.size(20.dp).clickable { drafts = drafts.toMutableList().also { it.removeAt(index) } }, tint = Muted
                    )
                }
            }
        }
        message?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = if (it == "Saved.") Green else Danger) }
        if (canEdit) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LinkButton("Add occasion", Icons.Outlined.Add) { drafts = drafts + OccasionDraft("", "✨", "", "", true) }
                Box(Modifier.weight(1f))
                if (drafts != saved) {
                    TextButton(onClick = { drafts = saved.orEmpty(); message = null }) { Text("Discard", fontFamily = Inter(), color = Muted) }
                    SaveButton(busy) {
                        val problem = when {
                            drafts.any { it.name.isBlank() } -> "Every occasion needs a name."
                            drafts.any { it.icon.isBlank() } -> "Every occasion needs an icon."
                            drafts.map { it.name.trim().lowercase() }.distinct().size != drafts.size -> "Two occasions have the same name."
                            else -> null
                        }
                        if (problem != null) {
                            message = problem
                            return@SaveButton
                        }
                        busy = true
                        coroutines.launch {
                            try {
                                val result = api.saveOccasions(restaurantId, drafts.map { draft ->
                                    ReservationOccasion(
                                        code = draft.code,
                                        name = draft.name.trim(),
                                        icon = draft.icon.trim(),
                                        options = draft.options.split(",").map(String::trim).filter(String::isNotEmpty),
                                        active = draft.active
                                    ).toDto()
                                }).map { it.toDomain() }
                                saved = fromServer(result)
                                drafts = saved.orEmpty()
                                message = "Saved."
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (error: Exception) {
                                message = error.message ?: "Couldn't save the occasions"
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Event nights ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventsEditor(restaurantId: String, canEdit: Boolean) {
    val api = koinInject<ReservationApi>()
    val menus = koinInject<MenuRepository>()
    val coroutines = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<RestaurantEvent>>(emptyList()) }
    var menuChoices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var editing by remember { mutableStateOf<RestaurantEvent?>(null) }
    var deleting by remember { mutableStateOf<RestaurantEvent?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        runCatching { api.getEvents(restaurantId).map { it.toDomain() } }
            .onSuccess { events = it; message = null }
            .onFailure { message = it.message ?: "Couldn't load the events" }
    }

    LaunchedEffect(restaurantId) {
        load()
        menuChoices = runCatching { menus.getMenus(restaurantId = restaurantId, size = 100).items.map { it.id to it.name } }.getOrDefault(emptyList())
    }

    Card(
        "Event nights",
        "Your own nights (New Year's Eve, Valentine's…): a special menu that day. Bookings stay normal and show the event's icon."
    ) {
        if (events.isEmpty()) Text("No events planned.", fontFamily = Inter(), fontSize = 12.sp, color = Muted)
        events.forEach { event ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(event.icon, fontSize = 18.sp)
                Column(Modifier.weight(1f)) {
                    Text(event.name + if (!event.active) " (off)" else "", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
                    Text(
                        listOfNotNull(
                            if (event.startDate == event.endDate) event.startDate else "${event.startDate} – ${event.endDate}",
                            event.menuName?.let { if (event.specialMenuOnly) "only $it" else "$it + usual menus" }
                        ).joinToString(" · "),
                        fontFamily = Inter(), fontSize = 12.sp, color = Muted
                    )
                }
                if (canEdit) {
                    LinkButton("Edit") { editing = event }
                    Icon(Icons.Outlined.DeleteOutline, "Remove ${event.name}", Modifier.size(20.dp).clickable { deleting = event }, tint = Muted)
                }
            }
        }
        message?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Danger) }
        if (canEdit) LinkButton("Add event night", Icons.Outlined.Add) { editing = RestaurantEvent(name = "", icon = "🎉", startDate = "", endDate = "") }
    }

    editing?.let { event ->
        EventDialog(event, menuChoices, onDismiss = { editing = null }) { draft ->
            coroutines.launch {
                try {
                    api.saveEvent(restaurantId, draft.toDto())
                    editing = null
                    load()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    message = error.message ?: "Couldn't save the event"
                    editing = null
                }
            }
        }
    }
    deleting?.let { event ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove ${event.name}?", fontFamily = Inter(), fontWeight = FontWeight.Bold) },
            text = { Text("Bookings stay as they are. Its menu stays in Menu.", fontFamily = Inter()) },
            confirmButton = {
                TextButton(onClick = {
                    val id = event.id
                    deleting = null
                    if (id != null) coroutines.launch {
                        runCatching { api.deleteEvent(restaurantId, id) }.onFailure { message = it.message ?: "Couldn't remove the event" }
                        load()
                    }
                }) { Text("Remove", fontFamily = Inter(), color = Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep", fontFamily = Inter(), color = Green) } },
            containerColor = Color.White
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventDialog(event: RestaurantEvent, menus: List<Pair<String, String>>, onDismiss: () -> Unit, onSave: (RestaurantEvent) -> Unit) {
    var draft by remember(event) { mutableStateOf(event) }
    val start = runCatching { LocalDate.parse(draft.startDate) }.getOrNull()
    val end = runCatching { LocalDate.parse(draft.endDate.ifBlank { draft.startDate }) }.getOrNull()
    val problem = when {
        draft.name.isBlank() -> "Give the event a name."
        draft.icon.isBlank() -> "Pick an icon."
        start == null -> "Write the first day as 2027-02-14."
        end == null -> "Write the last day as 2027-02-14."
        end < start -> "The last day can't be before the first."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (event.id == null) "Add event night" else "Edit event night", fontFamily = Inter(), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallField(draft.icon, "❤️", Modifier.width(56.dp), true) { draft = draft.copy(icon = it.take(8)) }
                    SmallField(draft.name, "Name, e.g. Valentine's", Modifier.weight(1f), true) { draft = draft.copy(name = it.take(120)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallField(draft.startDate, "First day 2027-02-14", Modifier.weight(1f), true) { draft = draft.copy(startDate = it.take(10)) }
                    SmallField(draft.endDate, "Last day (same if one)", Modifier.weight(1f), true) { draft = draft.copy(endDate = it.take(10)) }
                }
                Text("Special menu", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf<Pair<String?, String>>(null to "None") + menus).forEach { (id, name) ->
                        val picked = draft.menuId == id
                        Text(
                            name,
                            Modifier.clip(RoundedCornerShape(8.dp)).background(if (picked) Color(0xFFEEF3EB) else Color.White)
                                .border(1.dp, if (picked) Green else Border, RoundedCornerShape(8.dp))
                                .clickable { draft = draft.copy(menuId = id, menuName = name.takeIf { id != null }) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            fontFamily = Inter(), fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp,
                            color = if (picked) Green else Ink
                        )
                    }
                }
                if (draft.menuId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Only the special menu that evening", Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = Ink)
                        Switch(
                            checked = draft.specialMenuOnly, onCheckedChange = { draft = draft.copy(specialMenuOnly = it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Green, checkedThumbColor = Color.White)
                        )
                    }
                    Text("The menu shows only on the event's days.", fontFamily = Inter(), fontSize = 11.sp, color = Muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("On", Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = Ink)
                    Switch(
                        checked = draft.active, onCheckedChange = { draft = draft.copy(active = it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Green, checkedThumbColor = Color.White)
                    )
                }
                problem?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (problem == null) onSave(draft.copy(name = draft.name.trim(), icon = draft.icon.trim(), endDate = draft.endDate.ifBlank { draft.startDate }))
            }) { Text("Save", fontFamily = Inter(), fontWeight = FontWeight.Bold, color = if (problem == null) Green else Muted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back", fontFamily = Inter(), color = Muted) } },
        containerColor = Color.White
    )
}

// ---- Small pieces ----

@Composable
private fun SmallField(value: String, placeholder: String, modifier: Modifier, enabled: Boolean, onChange: (String) -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF6F7F5)).border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, fontFamily = Inter(), fontSize = 13.sp, color = Color(0xFFA3A5A0))
        BasicTextField(
            value = value, onValueChange = onChange, enabled = enabled, singleLine = true,
            textStyle = TextStyle(fontFamily = Inter(), fontSize = 13.sp, color = Ink), cursorBrush = SolidColor(Green),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LinkButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon?.let { Icon(it, null, Modifier.size(16.dp), tint = Green) }
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Green)
    }
}

@Composable
private fun SaveButton(busy: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !busy, shape = RoundedCornerShape(10.dp), color = Green) {
        Text(
            if (busy) "Saving…" else "Save occasions", Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White
        )
    }
}
