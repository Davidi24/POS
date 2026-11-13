package com.saporini.mobile_desktop.pos.reservations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationOccasion

internal val OccasionColor = Color(0xFFB0467A)

// Pick an occasion (🎂 Birthday…), then its options (Cake from us, Candles…) and a note. Tap the occasion again to
// remove it.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OccasionPicker(
    occasions: List<ReservationOccasion>,
    code: String?,
    options: List<String>,
    note: String,
    onChange: (code: String?, options: List<String>, note: String) -> Unit
) {
    if (occasions.isEmpty()) return
    val picked = occasions.firstOrNull { it.code == code }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            occasions.forEach { occasion ->
                val selected = occasion.code == code
                Chip(occasion.label, selected, OccasionColor) {
                    if (selected) onChange(null, emptyList(), "") else onChange(occasion.code, emptyList(), note)
                }
            }
        }
        if (picked != null) {
            if (picked.options.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    picked.options.forEach { option ->
                        val selected = option in options
                        Chip(option, selected, FormGreen) {
                            onChange(code, if (selected) options - option else options + option, note)
                        }
                    }
                }
            }
            InputBox(Icons.Outlined.Notes, note, "e.g. \"Cake with 30 candles at dessert\", \"It's a surprise\"") {
                onChange(code, options, it.take(300))
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) color else Color.White,
        border = BorderStroke(1.dp, if (selected) color else FormBorder)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(12.dp), tint = Color.White)
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (selected) Color.White else FormInk)
        }
    }
}

// The occasion on a booking, e.g. "🎂 Birthday" with "Cake from us · Candles" and the note.
@Composable
internal fun OccasionCard(reservation: Reservation) {
    val name = reservation.occasionName ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(OccasionColor.copy(alpha = 0.08f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            listOfNotNull(reservation.occasionIcon, name).joinToString(" "),
            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OccasionColor
        )
        if (reservation.occasionOptions.isNotEmpty()) {
            Text(reservation.occasionOptions.joinToString(" · "), fontFamily = Inter(), fontSize = 12.sp, color = FormInk)
        }
        reservation.occasionNote?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = FormInk) }
    }
}

// "🎂 Birthday: Cake from us, Candles" for short messages (e.g. when the guests arrive).
internal fun Reservation.occasionSummary(): String? = occasionName?.let { name ->
    listOfNotNull(occasionIcon, name).joinToString(" ") +
        (occasionOptions.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = ": ") ?: "")
}
