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
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.runtime.collectAsState
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
import com.saporini.mobile_desktop.hours.ClosedBookingDto
import com.saporini.mobile_desktop.hours.OpeningHoursModel
import com.saporini.mobile_desktop.hours.OpeningHoursState
import com.saporini.mobile_desktop.hours.clockAt
import com.saporini.mobile_desktop.hours.dateAt
import com.saporini.mobile_desktop.hours.hm
import kotlinx.datetime.TimeZone

/** The red used for bookings that fall when the branch is closed. */
internal val ClosedThenRed = Kit.Danger

/**
 * After the opening hours change (user, 2026-11-21): the hours are already saved, and the bookings that now fall when the
 * branch is closed are listed. They're handled in Reservations (user, 2026-11-23: "here it just says it, and sends you to
 * the bookings to handle them"; then "just the button Go to Reservations, no need for each one"). "Later" leaves them;
 * they stay marked "Branch closed then" in Reservations until moved or cancelled.
 */
@Composable
internal fun ClosedBookingsDialog(state: OpeningHoursState, model: OpeningHoursModel) {
    val list = state.closedBookings ?: return
    val navigator = cafe.adriel.voyager.navigator.LocalNavigator.current
    val center = org.koin.compose.getKoin().getOrNull<com.saporini.mobile_desktop.notifications.NotificationCenter>()
    val user by org.koin.compose.koinInject<com.saporini.mobile_desktop.core.session.SessionManager>().currentUser.collectAsState()
    val canOpen = navigator != null && center != null && user?.let { u ->
        com.saporini.mobile_desktop.core.session.Workspace.POS in com.saporini.mobile_desktop.core.session.accessibleWorkspaces(u) &&
            "RESERVATION_READ" in u.permissions
    } == true
    // Opens POS → Reservations, the way a booking's notification does (no booking picked).
    val open = {
        model.closeClosedBookings()
        center?.requestOpen(com.saporini.mobile_desktop.notifications.StaffNotification(id = "closed-bookings", eventCode = "OPEN_RESERVATIONS",
            title = "", message = "", referenceType = "RESERVATION", referenceId = null, createdAt = null, read = true, markReadAllowed = false))
        navigator?.push(com.saporini.mobile_desktop.pos.ui.PosScreen)
        Unit
    }
    val title = when (list.size) {
        0 -> "All handled"
        1 -> "A booking falls when the branch is closed"
        else -> "${list.size} bookings fall when the branch is closed"
    }
    AppDialog(
        title, model::closeClosedBookings, maxWidth = 640.dp,
        subtitle = if (list.isEmpty()) null else "The hours are saved. Handle them in Reservations: call the guests, then change the time or cancel.",
        buttons = {
            Box(Modifier.weight(1f))
            if (list.isEmpty()) KitButton("Done", model::closeClosedBookings)
            else {
                KitButton("Later", model::closeClosedBookings, style = ButtonStyle.SECONDARY)
                if (canOpen) KitButton("Go to Reservations", open, icon = Icons.Outlined.EventNote)
            }
        }
    ) {
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Kit.Green), Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(28.dp), tint = Color.White)
                }
                Text("Every booking is at an open time now.", fontFamily = Inter(), fontSize = 14.sp, color = Kit.Muted, textAlign = TextAlign.Center)
            }
        } else {
            if (!canOpen) MessageBar("Ask someone who handles bookings to move or cancel them.", MessageKind.INFO)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { booking -> ClosedBookingLine(booking, state) }
            }
        }
    }
}

/** One booking: the day as a red leaf, the guest, when and how many, their phone. */
@Composable
private fun ClosedBookingLine(booking: ClosedBookingDto, state: OpeningHoursState) {
    val date = dateAt(booking.start, state.zone)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Row(Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
