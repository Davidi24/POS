package com.saporini.mobile_desktop.pos.tables.ui

import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableShape
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableStatus

fun LayoutTable.toUiTable(): FloorPlanTable {
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
        state = when (status) {
            LayoutTableStatus.AVAILABLE -> TableVisualState.Free
            LayoutTableStatus.RESERVED -> TableVisualState.Reserved
            LayoutTableStatus.OCCUPIED -> TableVisualState.Occupied
            LayoutTableStatus.DIRTY,
            LayoutTableStatus.MAINTENANCE,
            LayoutTableStatus.OUT_OF_SERVICE -> TableVisualState.Unavailable
        },
        guestCount = guestCount,
        seatedAt = seatedAt,
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
            TableVisualState.Free -> LayoutTableStatus.AVAILABLE
            TableVisualState.Reserved -> LayoutTableStatus.RESERVED
            TableVisualState.Occupied,
            TableVisualState.BillPending -> LayoutTableStatus.OCCUPIED
            TableVisualState.Unavailable -> LayoutTableStatus.OUT_OF_SERVICE
        },
        guestCount = original?.guestCount ?: guestCount,
        seatedAt = original?.seatedAt ?: seatedAt,
        active = active
    )
}
