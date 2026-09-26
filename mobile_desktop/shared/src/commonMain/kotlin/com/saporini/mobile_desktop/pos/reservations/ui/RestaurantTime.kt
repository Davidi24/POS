package com.saporini.mobile_desktop.pos.reservations.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.TimeZone

// The restaurant's time zone from the server. Reservation times are shown and booked in it,
// not in the computer's own zone, so both sides always agree on what "19:00" means.
object RestaurantTime {
    var zone: TimeZone by mutableStateOf(TimeZone.currentSystemDefault())
        private set

    fun use(timezoneId: String?) {
        zone = timezoneId?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()
    }
}
