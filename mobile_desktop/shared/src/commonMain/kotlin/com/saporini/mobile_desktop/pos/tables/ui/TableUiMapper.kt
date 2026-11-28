package com.saporini.mobile_desktop.pos.tables.ui

import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableShape
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun LayoutTable.toUiTable(now: kotlin.time.Instant = kotlin.time.Clock.System.now()): FloorPlanTable {
    val hasCurrentOrder = currentOrderNumber != null
    // Blue only once something has been ordered: an order with no dishes yet is still just guests sitting (user, 2026-11-01).
    val ordered = hasCurrentOrder && currentOrderTotalItems > 0
    // A confirmed booking's guests are late and the table's hold is running out (the warning time from settings).
    val holdEndsInMinutes = if (!hasCurrentOrder && nextReservationStatus == "CONFIRMED") {
        val warningAt = nextReservationHoldWarningAt?.let { runCatching { kotlin.time.Instant.parse(it) }.getOrNull() }
        val holdUntil = nextReservationHoldUntil?.let { runCatching { kotlin.time.Instant.parse(it) }.getOrNull() }
        if (warningAt != null && holdUntil != null && now >= warningAt && now < holdUntil) {
            (holdUntil - now).inWholeMinutes.coerceAtLeast(1)
        } else null
    } else null
    // Guests still here as the next booking nears, until its hold is over (a booking whose guests have come stays).
    val waitingBooking = nextReservationStatus == "CHECKED_IN"
    val clash = nextBookingClashAt?.let { runCatching { kotlin.time.Instant.parse(it) }.getOrNull() }
        ?.takeIf { now >= it }
        ?.takeIf { waitingBooking || nextReservationHoldUntil?.let { hold -> runCatching { kotlin.time.Instant.parse(hold) }.getOrNull() }?.let { hold -> now < hold } != false }
        ?.let {
            nextReservationStart?.let { start ->
                TableClash(
                    startsAt = start,
                    name = nextReservationName,
                    guests = nextReservationPartySize,
                    waiting = waitingBooking,
                    tableId = id,
                    reservationId = nextReservationId,
                    waitingSince = nextReservationCheckedInAt,
                    moreTime = moreTimeOptions.orEmpty().split(',').mapNotNull { minutes -> minutes.trim().toIntOrNull() }
                )
            }
        }
    val autoFreeNear = autoFreeAt?.takeIf {
        autoFreeWarningAt?.let { warning -> runCatching { kotlin.time.Instant.parse(warning) }.getOrNull() }?.let { warning -> now >= warning } == true
    }
    // The guests asked for the bill (the waiter's "Bill requested"). Everything served alone says nothing: they may still
    // be eating, or more people may come (user, 2026-10-31).
    val billPending = ordered && currentOrderBillRequested
    val visualState = when (status) {
        LayoutTableStatus.AVAILABLE -> when {
            paid -> TableVisualState.Paid
            billPending -> TableVisualState.BillPending
            ordered -> TableVisualState.Ordered
            hasCurrentOrder -> TableVisualState.Occupied
            holdEndsInMinutes != null -> TableVisualState.HoldEnding
            else -> TableVisualState.Free
        }
        LayoutTableStatus.RESERVED -> when {
            paid -> TableVisualState.Paid
            billPending -> TableVisualState.BillPending
            ordered -> TableVisualState.Ordered
            hasCurrentOrder -> TableVisualState.Occupied
            holdEndsInMinutes != null -> TableVisualState.HoldEnding
            else -> TableVisualState.Reserved
        }
        LayoutTableStatus.OCCUPIED -> when {
            paid -> TableVisualState.Paid
            billPending -> TableVisualState.BillPending
            ordered -> TableVisualState.Ordered
            else -> TableVisualState.Occupied
        }
        LayoutTableStatus.DIRTY,
        LayoutTableStatus.MAINTENANCE,
        LayoutTableStatus.OUT_OF_SERVICE -> TableVisualState.Unavailable
    }

    return FloorPlanTable(
        id = id,
        x = positionX ?: 0.5f,
        y = positionY ?: 0.5f,
        shape = when (shape) {
            LayoutTableShape.ROUND -> TableShape.Circle
            else -> TableShape.Square
        },
        seatCount = capacity,
        label = tableNumber,
        state = visualState,
        orderLabel = currentOrderNumber,
        orderId = currentOrderId,
        orderStatus = currentOrderStatus,
        foodStatus = currentOrderFulfillmentStatus,
        guestsMin = guestsMin,
        // What the ring around the table fills with: dishes served or, seated with more guests to come, a half ring (user, 2026-11-01).
        servedItems = when {
            ordered -> currentOrderServedItems
            status == LayoutTableStatus.OCCUPIED -> if (moreComing) 1 else 0
            else -> 0
        },
        totalItems = when {
            ordered -> currentOrderTotalItems
            status == LayoutTableStatus.OCCUPIED -> if (moreComing) 2 else 0
            else -> 0
        },
        moreComing = moreComing,
        bookingComing = bookingComing,
        bookingParty = nextReservationPartySize,
        guestsUsual = guestsUsual,
        guestsMax = guestsMax,
        statusText = holdEndsInMinutes?.let { "Hold ends in $it min" }
            ?: when {
                !ordered -> null
                billPending -> "Bill Pending"
                currentOrderFulfillmentStatus == "FULFILLED" -> "Served"
                else -> currentOrderFulfillmentStatus?.toTableStatusText()
            },
        guestCount = guestCount,
        seatedAt = seatedAt,
        nextReservationStart = nextReservationStart,
        nextReservationEnd = nextReservationEnd,
        nextReservationCode = nextReservationCode,
        nextReservationName = nextReservationName,
        occasionIcon = nextReservationOccasionIcon,
        occasionName = nextReservationOccasionName,
        seatedOccasion = seatedReservationOccasionIcon?.let {
            TableOccasion(it, seatedReservationOccasionName, seatedReservationName, seatedReservationStart, seatedReservationPartySize)
        },
        seatedBooking = seatedReservationId?.let {
            TableBooking(it, seatedReservationName, seatedReservationStart, seatedReservationPartySize, seatedReservationOccasionIcon, seatedReservationOccasionName)
        },
        clash = clash,
        autoFreeAt = autoFreeNear,
        movedFrom = nextReservationMovedFrom,
        bookingGuests = nextReservationArrivedGuests ?: nextReservationPartySize,
        bookingId = nextReservationId,
        scale = scale,
        rotationDegrees = rotationDegrees,
        floorName = floor ?: "Unassigned",
        active = active
    )
}

// Merged tables work as one table, so they always show one state and one ring (user, 2026-10-31).
fun List<LayoutTable>.toUiTables(now: kotlin.time.Instant = kotlin.time.Clock.System.now()): List<FloorPlanTable> {
    val tables = map { it.toUiTable(now) }
    val headOf = associate { it.id to (it.mergedIntoTableId ?: it.id) }
    val groups = tables.groupBy { headOf[it.id] }.filterValues { it.size > 1 }
    return tables.map { table ->
        val group = groups[headOf[table.id]] ?: return@map table
        val leader = group.minBy { it.groupOrder() }
        table.copy(
            state = leader.state,
            statusText = leader.statusText,
            orderStatus = leader.orderStatus,
            foodStatus = leader.foodStatus,
            servedItems = leader.servedItems,
            totalItems = leader.totalItems,
            clash = group.firstNotNullOfOrNull { it.clash },
            autoFreeAt = group.firstNotNullOfOrNull { it.autoFreeAt }
        )
    }
}

private fun FloorPlanTable.groupOrder(): Int = when (state) {
    TableVisualState.BillPending -> 0
    TableVisualState.Ordered -> 1
    TableVisualState.Paid -> 2
    TableVisualState.Occupied -> 3
    TableVisualState.HoldEnding -> 4
    TableVisualState.Reserved -> 5
    TableVisualState.Free -> 6
    TableVisualState.Unavailable -> 7
}

fun FloorPlanTable.toDomainTable(original: LayoutTable?): LayoutTable {
    return LayoutTable(
        id = id.orEmpty(),
        mergedIntoTableId = original?.mergedIntoTableId,
        mergedTableIds = original?.mergedTableIds.orEmpty(),
        tableNumber = label,
        name = original?.name ?: label,
        capacity = seatCount,
        effectiveCapacity = original?.effectiveCapacity ?: seatCount,
        floor = floorName,
        positionX = x,
        positionY = y,
        rotationDegrees = rotationDegrees,
        scale = scale,
        shape = when (shape) {
            TableShape.Circle -> LayoutTableShape.ROUND
            TableShape.Square -> if (seatCount > 4) {
                LayoutTableShape.RECTANGLE
            } else {
                LayoutTableShape.SQUARE
            }
        },
        status = original?.status ?: when (state) {
            TableVisualState.Free, TableVisualState.HoldEnding -> LayoutTableStatus.AVAILABLE
            TableVisualState.Reserved -> LayoutTableStatus.RESERVED
            TableVisualState.Occupied,
            TableVisualState.Ordered,
            TableVisualState.BillPending,
            TableVisualState.Paid -> LayoutTableStatus.OCCUPIED
            TableVisualState.Unavailable -> LayoutTableStatus.OUT_OF_SERVICE
        },
        guestCount = original?.guestCount ?: guestCount,
        moreComing = original?.moreComing ?: moreComing,
        seatedAt = original?.seatedAt ?: seatedAt,
        nextReservationStart = original?.nextReservationStart ?: nextReservationStart,
        nextReservationEnd = original?.nextReservationEnd ?: nextReservationEnd,
        nextReservationCode = original?.nextReservationCode ?: nextReservationCode,
        nextReservationName = original?.nextReservationName ?: nextReservationName,
        currentOrderId = original?.currentOrderId ?: orderId,
        currentOrderNumber = original?.currentOrderNumber ?: orderLabel,
        currentOrderStatus = original?.currentOrderStatus,
        currentOrderFulfillmentStatus = original?.currentOrderFulfillmentStatus,
        paid = original?.paid ?: (state == TableVisualState.Paid),
        active = active
    )
}

// What the small note at the table says (short, phones have little room), and the longer line in its panel.
internal fun TableClash.noteText(short: Boolean): String = when {
    short -> if (waiting) "Waiting" else startsAt.clockTime()
    waiting -> "Booking waiting"
    else -> "Booking ${startsAt.clockTime()}"
}

internal fun TableClash.panelText(): String {
    val who = name?.takeIf { it.isNotBlank() }?.let { "$it's booking" } ?: "The next booking"
    val size = guests?.let { " ($it ${if (it == 1) "guest" else "guests"})" }.orEmpty()
    return if (waiting) "$who$size is waiting for this table." else "$who$size is at ${startsAt.clockTime()}. These guests are still here."
}

internal fun TableClash.waitingText(now: kotlin.time.Instant = kotlin.time.Clock.System.now()): String? = waitingSince?.let { since ->
    runCatching { (now - kotlin.time.Instant.parse(since)).inWholeMinutes.coerceAtLeast(0) }.getOrNull()?.let { "Waiting since ${since.clockTime()} ($it min)." }
}

internal fun String.clockTime(): String = runCatching {
    val at = kotlin.time.Instant.parse(this).toLocalDateTime(TimeZone.currentSystemDefault())
    "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
}.getOrDefault(substringAfter('T', "").take(5))

private fun String.toTableStatusText(): String {
    return lowercase()
        .split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { char -> char.uppercase() } }
}
