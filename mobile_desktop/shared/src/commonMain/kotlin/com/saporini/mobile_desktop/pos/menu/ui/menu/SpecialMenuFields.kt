package com.saporini.mobile_desktop.pos.menu.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEventNight
import com.saporini.mobile_desktop.pos.menu.domain.model.SpecialMenuKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

private val SpecialOlive = Color(0xFF4F7942)
private val SpecialOliveSoft = Color(0xFFEEF3EB)
private val SpecialInk = Color(0xFF242522)
private val SpecialMuted = Color(0xFF71736E)
private val SpecialBorder = Color(0xFFE2E3DE)
private val SpecialDanger = Color(0xFFB13A2F)

// The editor's special-menu answer: is it special, what it's for and, for an event menu, which event night.
internal data class SpecialMenuChoice(
    val special: Boolean = false,
    val kind: SpecialMenuKind = SpecialMenuKind.BOOKING_EXTRAS,
    val eventId: String? = null
) {
    val isEventMenu: Boolean get() = special && kind == SpecialMenuKind.EVENT
}

// The event nights for the "Event menu" choice, as loaded for the editor.
internal sealed interface EventNightsState {
    data object Loading : EventNightsState
    data class Loaded(val events: List<MenuEventNight>) : EventNightsState
    data class Failed(val message: String) : EventNightsState
}

// "14 Feb 2027", or "13 Feb – 14 Feb 2027" for more days.
internal fun eventDaysText(startDate: String, endDate: String): String {
    val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return startDate
    val end = runCatching { LocalDate.parse(endDate) }.getOrNull() ?: start
    return if (start == end) shortDate(start, withYear = true) else "${shortDate(start)} – ${shortDate(end, withYear = true)}"
}

/**
 * "Special menu": a menu for one event night. Ticking it goes straight to the event picker (the box only shows ticked
 * once an event is chosen); the chosen event then shows here as a card that can be changed. Unticking makes it a
 * regular menu again.
 */
@Composable
internal fun SpecialMenuFields(
    choice: SpecialMenuChoice,
    onChoiceChange: (SpecialMenuChoice) -> Unit,
    chosenEvent: MenuEventNight?,
    onPickEvent: () -> Unit,
    enabled: Boolean,
    eventError: Boolean,
    compact: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SpecialCheckRow(
            label = "Special menu",
            detail = "For an event night, like Valentine's. It shows only on the event's days.",
            checked = choice.special,
            enabled = enabled,
            onCheckedChange = { checked -> if (checked) onPickEvent() else onChoiceChange(choice.copy(special = false)) }
        )
        if (choice.special) {
            if (choice.eventId == null) {
                ChooseEventCard(enabled = enabled, isError = eventError, onClick = onPickEvent)
            } else {
                LinkedEventCard(
                    event = chosenEvent,
                    enabled = enabled,
                    compact = compact,
                    onChange = onPickEvent
                )
            }
        }
        if (eventError) {
            Text("Choose the event this menu is for.", fontFamily = Inter(), fontSize = 12.sp, color = SpecialDanger)
        }
    }
}

/** A special menu without its event yet (e.g. its event was deleted): one tap to choose it. */
@Composable
private fun ChooseEventCard(enabled: Boolean, isError: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White)
            .border(1.dp, if (isError) SpecialDanger else SpecialBorder, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.EventAvailable, null, Modifier.size(20.dp), tint = SpecialOlive)
        Text("Choose the event", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SpecialInk)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = SpecialMuted)
    }
}

/**
 * The event an event menu belongs to, read-only: its days are the menu's days. "Change" opens the event dialog, where
 * events are picked, added, edited and deleted.
 */
@Composable
private fun LinkedEventCard(
    event: MenuEventNight?,
    enabled: Boolean,
    compact: Boolean,
    onChange: () -> Unit
) {
    val actions: @Composable () -> Unit = {
        CardButton("Change", Icons.Outlined.SwapHoriz, enabled, onChange)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SpecialOliveSoft)
            .border(1.dp, SpecialOlive.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Text(event?.icon?.ifBlank { "🎉" } ?: "🎉", fontSize = 21.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    event?.name ?: "Event night",
                    fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SpecialInk,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.CalendarToday, null, Modifier.size(13.dp), tint = SpecialOlive)
                    Text(
                        event?.let { eventDaysWithCount(it.startDate, it.endDate) } ?: "Loading the event…",
                        fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = SpecialInk, maxLines = 1
                    )
                }
            }
            if (!compact) actions()
        }
        if (compact) actions()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(12.dp), tint = SpecialMuted)
            Text(
                if (event?.specialMenuOnly == true) "Shows only on the event's days, as the only menu at the till."
                else "Shows only on the event's days. To change them, use Change and edit the event.",
                fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = SpecialMuted
            )
        }
    }
}

// "10 Oct – 17 Oct 2026 · 8 days".
private fun eventDaysWithCount(startDate: String, endDate: String): String {
    val start = runCatching { LocalDate.parse(startDate) }.getOrNull()
    val end = runCatching { LocalDate.parse(endDate) }.getOrNull()
    val days = if (start != null && end != null) start.daysUntil(end) + 1 else 1
    return eventDaysText(startDate, endDate) + if (days > 1) " · $days days" else ""
}

@Composable
private fun CardButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, SpecialBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = SpecialInk)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = SpecialInk)
    }
}

@Composable
private fun SpecialCheckRow(label: String, detail: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(if (checked) SpecialOlive else Color.White)
                .border(if (checked) 1.dp else 1.5.dp, if (checked) SpecialOlive else Color(0xFF8B8F87), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = Color.White)
        }
        Column {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = SpecialInk)
            Text(detail, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = SpecialMuted)
        }
    }
}

/**
 * What the till shows on an event's days, as a deliberate choice (nothing is picked for a new event):
 * only the event menu, or the event menu plus the regular menus scheduled for that day and time.
 */
@Composable
internal fun TillMenuChoice(onlyEventMenu: Boolean?, onChange: (Boolean) -> Unit, isError: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("At the till on those days", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SpecialInk)
        TillOption(
            title = "Only the event menu",
            detail = "Waiters see just this event's menu.",
            selected = onlyEventMenu == true,
            isError = isError,
            onClick = { onChange(true) }
        )
        TillOption(
            title = "Event menu + regular menus",
            detail = "Waiters also see the regular menus scheduled for that day and time.",
            selected = onlyEventMenu == false,
            isError = isError,
            onClick = { onChange(false) }
        )
        if (isError) {
            Text("Choose what the till shows on those days.", fontFamily = Inter(), fontSize = 12.sp, color = SpecialDanger)
        }
    }
}

@Composable
private fun TillOption(title: String, detail: String, selected: Boolean, isError: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) SpecialOliveSoft else Color.White)
            .border(
                if (selected) 1.5.dp else 1.dp,
                when {
                    selected -> SpecialOlive
                    isError -> SpecialDanger
                    else -> SpecialBorder
                },
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(50)).background(Color.White)
                .border(if (selected) 6.dp else 1.5.dp, if (selected) SpecialOlive else Color(0xFF8B8F87), RoundedCornerShape(50))
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (selected) SpecialOlive else SpecialInk)
            Text(detail, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = SpecialMuted)
        }
    }
}
