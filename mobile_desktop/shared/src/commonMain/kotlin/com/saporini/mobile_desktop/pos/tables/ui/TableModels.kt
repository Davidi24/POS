package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The booking the guests at a table are in the way of (user, 2026-11-01). Waiting: its guests have already come.
data class TableClash(
    val startsAt: String,
    val name: String?,
    val guests: Int?,
    val waiting: Boolean,
    // The table holding the booking (a merged group shares the warning), the booking, when its guests came, the "Give more time" choices.
    val tableId: String? = null,
    val reservationId: String? = null,
    val waitingSince: String? = null,
    val moreTime: List<Int> = emptyList()
)

data class FloorPlanTable(
    val x: Float,
    val y: Float,
    val shape: TableShape,
    val seatCount: Int,
    val label: String,
    val state: TableVisualState = TableVisualState.Free,
    val orderLabel: String? = null,
    val orderId: String? = null,
    // How many guests can be seated here, with its merged tables (from the server, see Settings → Tables).
    val guestsMin: Int? = null,
    val guestsUsual: Int? = null,
    val guestsMax: Int? = null,
    val statusText: String? = null,
    // The order's own statuses (server enum names), shown in the panel as Order status + Kitchen status.
    val orderStatus: String? = null,
    val foodStatus: String? = null,
    val servedItems: Int = 0,
    val totalItems: Int = 0,
    val guestCount: Int? = null,
    // Seated with more guests still to come: the half ring.
    val moreComing: Boolean = false,
    // The next booking's size: what seating it starts from.
    val bookingParty: Int? = null,
    val seatedAt: String? = null,
    val nextReservationStart: String? = null,
    val nextReservationEnd: String? = null,
    val nextReservationCode: String? = null,
    val nextReservationName: String? = null,
    // The next booking's occasion, e.g. 🎂 Birthday.
    val occasionIcon: String? = null,
    val occasionName: String? = null,
    // What the booking seated here now celebrates.
    val seatedOccasion: TableOccasion? = null,
    // The booking whose guests sit here now, to open it from the table (user, 2026-11-02).
    val seatedBooking: TableBooking? = null,
    // Guests are still here and the next booking is due; a merged group shares it.
    val clash: TableClash? = null,
    // A free table whose booking is coming: the yellow ring (user, 2026-11-01).
    val bookingComing: Boolean = false,
    // Nobody has acted for a long time: when the system steps in, once it's near (Settings → Tables → Special cases).
    val autoFreeAt: String? = null,
    // The system moved the next booking here from this table.
    val movedFrom: String? = null,
    // How many guests the next booking brings (those who've come, when it's known): what seating it starts from.
    val bookingGuests: Int? = null,
    val bookingId: String? = null,
    val scale: Float = 0.55f,
    val rotationDegrees: Float = 0f,
    val id: String? = null,
    val floorName: String = "1st Floor",
    val active: Boolean = true
) {
    fun visualWidth(): Dp =
        when (shape) {
            TableShape.Circle -> 156.dp * scale
            TableShape.Square ->
                ((if (seatCount > 4) 248.dp else 128.dp) + 48.dp) * scale
        }

    fun visualHeight(): Dp =
        when (shape) {
            TableShape.Circle -> 156.dp * scale
            TableShape.Square ->
                ((if (seatCount > 4) 112.dp else 128.dp) + 40.dp) * scale
        }
}

// A booking's occasion (🎂 Birthday) on a table, and whose booking it is.
data class TableOccasion(val icon: String, val name: String?, val bookingName: String?, val start: String?, val guests: Int?)

// A booking on a table, as the table's panel shows it and opens it in Reservations.
data class TableBooking(val id: String, val name: String?, val start: String?, val guests: Int?, val icon: String?, val occasion: String?)

// The next booking coming to this table.
internal fun FloorPlanTable.nextBooking(): TableBooking? =
    bookingId?.let { TableBooking(it, nextReservationName, nextReservationStart, bookingParty ?: bookingGuests, occasionIcon, occasionName) }

// From the yellow ring on (user, 2026-11-02: "not only in the yellow border but from the yellow border on"): the next booking's
// occasion while its ring is on, it's due or the table is held for it, then the seated guests' own while they're here. A
// booking hours away shows nothing yet.
internal fun FloorPlanTable.occasion(): TableOccasion? = when {
    seatedOccasion != null && state in setOf(TableVisualState.Occupied, TableVisualState.Ordered, TableVisualState.BillPending, TableVisualState.Paid) ->
        seatedOccasion
    occasionIcon != null && (bookingComing || clash != null || state == TableVisualState.Reserved || state == TableVisualState.HoldEnding) ->
        TableOccasion(occasionIcon, occasionName, nextReservationName, nextReservationStart, bookingGuests)
    else -> null
}
