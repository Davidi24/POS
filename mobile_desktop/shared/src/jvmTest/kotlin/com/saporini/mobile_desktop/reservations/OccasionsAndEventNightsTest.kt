package com.saporini.mobile_desktop.reservations

import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import com.saporini.mobile_desktop.pos.reservations.occasionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Phase 3 in the app: the occasion reminder text. ("Only the special menu" on an event night is decided by the
// server now: GET /menus/available-now.)
class OccasionsAndEventNightsTest {

    private fun booking(occasion: String?, options: List<String> = emptyList()) = Reservation(
        id = "r1", restaurantId = "rest", branchId = "b1", status = ReservationStatus.CONFIRMED, partySize = 4,
        reservationStart = "2026-09-28T17:00:00Z", reservationEnd = "2026-09-28T19:00:00Z",
        occasionName = occasion, occasionIcon = occasion?.let { "🎂" }, occasionOptions = options
    )

    @Test
    fun occasionReminderText() {
        assertEquals("🎂 Birthday: Cake from us, Candles", booking("Birthday", listOf("Cake from us", "Candles")).occasionSummary())
        assertEquals("🎂 Birthday", booking("Birthday").occasionSummary())
        assertNull(booking(null).occasionSummary())
    }
}
