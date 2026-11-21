package com.saporini.mobile_desktop.hours.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.hours.BookingMove
import com.saporini.mobile_desktop.hours.ClosedBookingDto
import com.saporini.mobile_desktop.hours.OpeningHoursModel
import com.saporini.mobile_desktop.hours.OpeningHoursState
import com.saporini.mobile_desktop.hours.clockAt
import com.saporini.mobile_desktop.hours.dateAt
import com.saporini.mobile_desktop.hours.hm
import kotlinx.datetime.TimeZone

/** The red used for bookings that fall when the branch is closed. */
internal val ClosedThenRed = Color(0xFFDC2626)

/**
 * After the opening hours change (user, 2026-11-21): the hours are already saved, and the bookings that now fall when the
 * branch is closed are listed, each with Change time and Cancel. "Later" leaves them for now; they stay marked "Branch closed
 * then" in Reservations until moved or cancelled.
 */
@Composable
internal fun ClosedBookingsDialog(state: OpeningHoursState, model: OpeningHoursModel) {
    val list = state.closedBookings ?: return
    val title = when (list.size) {
        0 -> "All handled"
        1 -> "A booking falls when the branch is closed"
        else -> "${list.size} bookings fall when the branch is closed"
    }
    AppDialog(
        title, model::closeClosedBookings, maxWidth = 660.dp,
        subtitle = if (list.isEmpty()) null else "The hours are saved. Call the guests first, then move or cancel each one.",
        buttons = {
            Box(Modifier.weight(1f))
            if (list.isEmpty()) KitButton("Done", model::closeClosedBookings)
            else KitButton("Later", model::closeClosedBookings, style = ButtonStyle.SECONDARY)
        }
    ) {
        state.bookingError?.takeIf { state.move == null && state.cancelling == null }?.let { MessageBar(it, MessageKind.ERROR) }
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Kit.Green), Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(28.dp), tint = Color.White)
                }
                Text("Every booking is at an open time now.", fontFamily = Inter(), fontSize = 14.sp, color = Kit.Muted, textAlign = TextAlign.Center)
            }
        } else {
            if (!state.canMoveBookings && !state.canCancelBookings) {
                MessageBar("Ask someone who manages bookings to move or cancel them.", MessageKind.INFO)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { booking -> ClosedBookingLine(booking, state, model) }
            }
        }
    }
    state.move?.let { MoveBookingDialog(it, state, model) }
    state.cancelling?.let { CancelBookingDialog(it, state, model) }
}

@Composable
private fun ClosedBookingLine(booking: ClosedBookingDto, state: OpeningHoursState, model: OpeningHoursModel) {
    val narrow = LocalNarrowDialog.current
    val date = dateAt(booking.start, state.zone)
    val buttons: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.canMoveBookings) KitButton("Change time", { model.startMove(booking) }, style = ButtonStyle.SECONDARY, enabled = !state.bookingBusy)
            if (state.canCancelBookings) KitButton("Cancel", { model.startCancel(booking) }, style = ButtonStyle.DANGER_OUTLINE, enabled = !state.bookingBusy)
        }
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The day as a small leaf, in red: it's a closed day now.
                Column(Modifier.width(46.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, ClosedThenRed.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().background(ClosedThenRed).padding(vertical = 2.dp), Alignment.Center) {
                        Text(date?.let { monthShort(it) } ?: "", fontFamily = Inter(), fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, color = Color.White, maxLines = 1)
                    }
                    Text(date?.day?.toString() ?: "?", Modifier.padding(vertical = 3.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        color = Kit.Ink, translate = false)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(booking.name ?: "Guest", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Kit.Ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, translate = booking.name == null)
                    Text(bookingLine(booking, state.zone), fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    booking.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.Phone, null, Modifier.size(13.dp), tint = Kit.Green)
                            Text(phone, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Green, translate = false)
                        }
                    }
                }
                if (!narrow) buttons()
            }
            if (narrow) buttons()
        }
    }
}

/** "Fri 25 Dec · 19:30 · 4 guests · T07". */
private fun bookingLine(booking: ClosedBookingDto, zone: TimeZone): String = listOfNotNull(
    dateAt(booking.start, zone)?.let { shortDate(it) },
    clockAt(booking.start, zone)?.hm(),
    if (booking.guests == 1) "1 guest" else "${booking.guests} guests",
    booking.tables.takeIf { it.isNotEmpty() }?.joinToString(" + "),
    if (booking.status == "PENDING") "Request" else null
).joinToString(" · ")

/** Change time: a day on the calendar, then one of its open times (the booking keeps its length). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveBookingDialog(move: BookingMove, state: OpeningHoursState, model: OpeningHoursModel) {
    val target = move.time?.let { "${shortDate(move.day)}, ${it.hm()}" }
    AppDialog(
        "Change time", model::cancelMove, busy = state.bookingBusy, maxWidth = 640.dp,
        subtitle = "${move.booking.name ?: "Guest"} · now ${bookingLine(move.booking, state.zone)}",
        buttons = {
            KitButton("Back", model::cancelMove, style = ButtonStyle.SECONDARY, enabled = !state.bookingBusy)
            KitButton(if (target == null) "Pick a time" else "Move to $target", model::saveMove, loading = state.bookingBusy, enabled = target != null)
        }
    ) {
        state.bookingError?.let { MessageBar(it, MessageKind.ERROR) }
        val narrow = LocalNarrowDialog.current
        val calendar: @Composable () -> Unit = {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))) {
                com.saporini.mobile_desktop.pos.reservations.CompactDatePicker(move.day) { model.moveDay(it) }
            }
        }
        val times: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(shortDate(move.day), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                val list = move.times
                when {
                    list == null -> Text("Loading the open times…", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                    list.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.EventBusy, null, Modifier.size(16.dp), tint = ClosedThenRed)
                        Text("Closed that day, or no time left. Pick another day.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                    }
                    else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        list.forEach { time ->
                            val picked = time == move.time
                            Surface(onClick = { model.pickMoveTime(time) }, shape = RoundedCornerShape(8.dp),
                                color = if (picked) Kit.Green else Color.White, border = BorderStroke(1.dp, if (picked) Kit.Green else Kit.Border)) {
                                Text(time.hm(), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp, color = if (picked) Color.White else Kit.Ink, translate = false)
                            }
                        }
                    }
                }
            }
        }
        if (narrow) {
            calendar()
            times()
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                calendar()
                Box(Modifier.weight(1f).height(320.dp)) {
                    com.saporini.mobile_desktop.core.components.ScrollColumn(Modifier.fillMaxWidth()) { times() }
                }
            }
        }
    }
}

/** Cancel one booking, with an optional reason. */
@Composable
private fun CancelBookingDialog(booking: ClosedBookingDto, state: OpeningHoursState, model: OpeningHoursModel) {
    var reason by remember(booking.id) { mutableStateOf("Branch closed then") }
    AppDialog(
        "Cancel this booking?", model::closeCancel, busy = state.bookingBusy, maxWidth = 520.dp,
        subtitle = "${booking.name ?: "Guest"} · ${bookingLine(booking, state.zone)}",
        buttons = {
            KitButton("Back", model::closeCancel, style = ButtonStyle.SECONDARY, enabled = !state.bookingBusy)
            KitButton("Cancel booking", { model.confirmCancel(reason) }, style = ButtonStyle.DANGER, loading = state.bookingBusy)
        }
    ) {
        state.bookingError?.let { MessageBar(it, MessageKind.ERROR) }
        TextInput(reason, { reason = it.take(500) }, Modifier.fillMaxWidth(), label = "Why", maxLength = 500)
    }
}
