package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.tables.domain.model.BookingTableOption

private val MapInk = Color(0xFF1F2A1F)
private val MapMuted = Color(0xFF6B7280)
private val MapBorder = Color(0xFFE3E7E1)
private val MapGreen = Color(0xFF147A25)
private val MapGreenSoft = Color(0xFFEAF5EC)
// The colour the plan gives tables that can't be picked.
private val MapTaken = Color(0xFF7A8088)

// One window for choosing tables on the floor plan, wherever a booking or guests move (user, 2026-11-02: "it opens the
// map all the time so it's very easy for me to choose"). On a wide window the choices sit beside the map; on a narrow
// one the map comes first and the choices under it, so the plan keeps most of the room.
@Composable
fun TableMapDialog(
    title: String,
    detail: String,
    freeTableIds: Set<String>,
    selectedTableIds: Set<String>,
    onTableClick: (tableId: String, label: String, isFree: Boolean) -> Unit,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    otherSuggestions: List<Set<String>> = emptyList(),
    busy: Boolean = false,
    takenLegend: String = "Not free then",
    choices: @Composable ColumnScope.(compact: Boolean) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)), contentAlignment = Alignment.Center) {
            // Side by side only on a window wider than tall; a portrait tablet stacks too, so the plan gets the width.
            val wide = maxWidth >= 760.dp && maxWidth > maxHeight
            val phone = maxWidth < 600.dp
            val roomHigh = maxHeight
            val card = Modifier
                .padding(if (phone) 8.dp else 28.dp)
                .fillMaxWidth()
                .then(if (wide) Modifier.widthIn(max = 1280.dp) else Modifier)
                .fillMaxHeight(if (phone) 1f else 0.92f)
                .shadow(22.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, MapBorder, RoundedCornerShape(16.dp))
            val header: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.sp, color = MapInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(detail, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = MapMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Close", Modifier.size(22.dp), tint = MapInk)
                    }
                }
            }
            val buttons: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MapBorder)) {
                        Box(contentAlignment = Alignment.Center) { Text("Cancel", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = MapInk) }
                    }
                    Surface(
                        onClick = { if (confirmEnabled && !busy) onConfirm() }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp),
                        color = if (confirmEnabled) MapGreen else MapGreen.copy(alpha = 0.35f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text(confirmLabel, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Color.White, maxLines = 1)
                        }
                    }
                }
            }
            val map: @Composable (Modifier) -> Unit = { mapModifier ->
                Column(mapModifier.background(Color(0xFFF7F7F5)).padding(10.dp)) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
                            .border(1.dp, MapBorder, RoundedCornerShape(12.dp)).padding(6.dp)
                    ) {
                        ReservationTablePicker(
                            freeTableIds = freeTableIds,
                            selectedTableIds = selectedTableIds,
                            onTableClick = onTableClick,
                            otherSuggestions = otherSuggestions,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Row(Modifier.padding(top = 8.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MapLegendDot(MapGreen, "Free, tap to pick")
                        MapLegendDot(MapTaken, takenLegend)
                    }
                }
            }
            if (wide) {
                Row(card) {
                    Column(Modifier.width(360.dp).fillMaxHeight()) {
                        header()
                        HorizontalDivider(color = MapBorder)
                        ScrollColumn(Modifier.weight(1f).fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { choices(false) }
                        }
                        HorizontalDivider(color = MapBorder)
                        buttons()
                    }
                    VerticalDivider(color = MapBorder)
                    map(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                // The map is as tall as the plan needs at this width (plus the floor switch and the key), the choices
                // take what's left, so no empty band sits under a landscape plan.
                val planWidth = maxWidth - (if (phone) 16.dp else 56.dp) - 34.dp
                val mapHeight = (planWidth * (1086f / 1448f) + 110.dp).coerceAtMost(roomHigh * 0.62f)
                Column(card) {
                    header()
                    HorizontalDivider(color = MapBorder)
                    map(Modifier.height(mapHeight).fillMaxWidth())
                    HorizontalDivider(color = MapBorder)
                    ScrollColumn(Modifier.fillMaxWidth().weight(1f)) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { choices(true) }
                    }
                    HorizontalDivider(color = MapBorder)
                    buttons()
                }
            }
        }
    }
}

@Composable
private fun MapLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontFamily = Inter(), fontSize = 11.sp, letterSpacing = 0.sp, color = MapMuted)
    }
}

// A choice beside the map: a line on a wide window, a chip on a narrow one.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapChoiceRow(icon: ImageVector, title: String, detail: String?, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    if (compact) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) MapGreenSoft else Color.White)
                .border(if (selected) 1.5.dp else 1.dp, if (selected) MapGreen else MapBorder, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, Modifier.size(15.dp), tint = if (selected) MapGreen else MapInk)
            Text(
                listOfNotNull(title, detail).joinToString(" · "),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = MapInk, maxLines = 1
            )
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) MapGreenSoft else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) MapGreen else MapBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (selected) MapGreen else MapInk)
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = MapInk)
            detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, letterSpacing = 0.sp, color = MapMuted) }
        }
    }
}

// Choices laid out for the window: one under another beside the map, chips that wrap under it.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapChoices(compact: Boolean, content: @Composable () -> Unit) {
    if (compact) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun MapChoicesHeading(text: String) {
    Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.sp, color = MapInk)
}

@Composable
fun MapChoicesNote(text: String) {
    Text(text, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = MapMuted)
}

// Moving a booking to another free table: the free tables that fit are green on the map, the best one is picked first.
@Composable
fun BookingMoveDialog(
    title: String,
    detail: String,
    options: List<BookingTableOption>,
    onDismiss: () -> Unit,
    onPick: (BookingTableOption) -> Unit,
    loading: Boolean = false,
    busy: Boolean = false,
    confirmLabel: String = "Move here"
) {
    var picked by remember(options) { mutableStateOf(options.firstOrNull()) }
    val free = remember(options) { options.flatMap { it.tableIds }.toSet() }
    TableMapDialog(
        title = title,
        detail = detail,
        freeTableIds = free,
        selectedTableIds = picked?.tableIds.orEmpty().toSet(),
        otherSuggestions = options.filter { it != picked && it.tableIds.size > 1 }.take(4).map { it.tableIds.toSet() },
        onTableClick = { id, _, isFree ->
            if (isFree) {
                // The table on its own when it fits alone, otherwise the first choice it's part of.
                picked = options.firstOrNull { it.tableIds == listOf(id) } ?: options.firstOrNull { id in it.tableIds } ?: picked
            }
        },
        confirmLabel = picked?.let { "$confirmLabel: ${it.tableNumbers.joinToString(" + ")}" } ?: confirmLabel,
        confirmEnabled = picked != null,
        busy = busy,
        onConfirm = { picked?.let(onPick) },
        onDismiss = onDismiss,
        takenLegend = "Taken or too small"
    ) { compact ->
        MapChoicesHeading("Free tables that fit")
        when {
            loading -> CircularProgressIndicator(Modifier.size(18.dp), color = MapGreen, strokeWidth = 2.dp)
            options.isEmpty() -> MapChoicesNote("No free table fits this booking.")
        }
        MapChoices(compact) {
            options.take(if (compact) 8 else 12).forEachIndexed { index, option ->
                MapChoiceRow(
                    icon = if (index == 0) Icons.Outlined.AutoAwesome else Icons.Outlined.TableRestaurant,
                    title = (if (index == 0 && !compact) "Best fit: " else "") + option.tableNumbers.joinToString(" + "),
                    detail = option.seats?.let { "$it seats" },
                    selected = picked == option,
                    compact = compact
                ) { picked = option }
            }
        }
        if (!compact && options.isNotEmpty()) MapChoicesNote("Or tap a green table on the map.")
    }
}
