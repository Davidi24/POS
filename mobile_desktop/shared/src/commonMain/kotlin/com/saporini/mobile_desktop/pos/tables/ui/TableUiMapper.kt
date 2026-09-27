package com.saporini.mobile_desktop.pos.tables.ui

import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableShape
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableStatus

fun LayoutTable.toUiTable(now: kotlin.time.Instant = kotlin.time.Clock.System.now()): FloorPlanTable {
    val hasCurrentOrder = currentOrderNumber != null
    // A confirmed booking's guests are late and the table's hold is running out (the warning time from settings).
    val holdEndsInMinutes = if (!hasCurrentOrder && nextReservationStatus == "CONFIRMED") {
        val warningAt = nextReservationHoldWarningAt?.let { runCatching { kotlin.time.Instant.parse(it) }.getOrNull() }
        val holdUntil = nextReservationHoldUntil?.let { runCatching { kotlin.time.Instant.parse(it) }.getOrNull() }
        if (warningAt != null && holdUntil != null && now >= warningAt && now < holdUntil) {
            (holdUntil - now).inWholeMinutes.coerceAtLeast(1)
        } else null
    } else null
    val visualState = when (status) {
        LayoutTableStatus.AVAILABLE -> when {
            hasCurrentOrder -> TableVisualState.Occupied
            holdEndsInMinutes != null -> TableVisualState.HoldEnding
            else -> TableVisualState.Free
        }
        LayoutTableStatus.RESERVED -> when {
            hasCurrentOrder -> TableVisualState.Occupied
            holdEndsInMinutes != null -> TableVisualState.HoldEnding
            else -> TableVisualState.Reserved
        }
        LayoutTableStatus.OCCUPIED -> TableVisualState.Occupied
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
        statusText = holdEndsInMinutes?.let { "Hold ends in $it min" } ?: currentOrderFulfillmentStatus?.toTableStatusText(),
        guestCount = guestCount,
        seatedAt = seatedAt,
        nextReservationStart = nextReservationStart,
        nextReservationEnd = nextReservationEnd,
        nextReservationCode = nextReservationCode,
        nextReservationName = nextReservationName,
        scale = scale,
        rotationDegrees = rotationDegrees,
        floorName = floor ?: "Unassigned",
        active = active
    )
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
            TableVisualState.BillPending -> LayoutTableStatus.OCCUPIED
            TableVisualState.Unavailable -> LayoutTableStatus.OUT_OF_SERVICE
        },
        guestCount = original?.guestCount ?: guestCount,
        seatedAt = original?.seatedAt ?: seatedAt,
        nextReservationStart = original?.nextReservationStart ?: nextReservationStart,
        nextReservationEnd = original?.nextReservationEnd ?: nextReservationEnd,
        nextReservationCode = original?.nextReservationCode ?: nextReservationCode,
        nextReservationName = original?.nextReservationName ?: nextReservationName,
        currentOrderId = original?.currentOrderId ?: orderId,
        currentOrderNumber = original?.currentOrderNumber ?: orderLabel,
        currentOrderStatus = original?.currentOrderStatus,
        currentOrderFulfillmentStatus = original?.currentOrderFulfillmentStatus,
        active = active
    )
}

private fun String.toTableStatusText(): String {
    return lowercase()
        .split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { char -> char.uppercase() } }
}
