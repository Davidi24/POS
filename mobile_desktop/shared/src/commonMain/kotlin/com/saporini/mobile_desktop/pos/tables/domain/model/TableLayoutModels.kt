package com.saporini.mobile_desktop.pos.tables.domain.model

// Floors the Tables screen lets you open; tables stored on any other floor are not part of the floor plan.
val FloorPlanFloorNames = listOf("1st Floor", "2nd Floor", "3rd Floor")

// A floor exists once it has a saved layout or tables on it; there is always at least one.
fun activeFloorNames(floorLayouts: List<FloorLayout>, tables: List<LayoutTable>): List<String> =
    FloorPlanFloorNames.filter { floor ->
        floorLayouts.any { it.floorName == floor } || tables.any { it.floor == floor }
    }.ifEmpty { FloorPlanFloorNames.take(1) }

data class FloorLayout(
    val id: String,
    val restaurantId: String,
    val branchId: String,
    val floorName: String,
    val planImageKey: String?,
    val planImageUrl: String?,
    val planOffsetX: Float,
    val planOffsetY: Float,
    val planScale: Float
)

data class BranchTableLayout(
    val restaurantId: String,
    val branchId: String,
    val floors: List<FloorSummary>,
    val tables: List<LayoutTable>
)

data class FloorSummary(
    val name: String,
    val tableCount: Int,
    val positionedTableCount: Int
)

data class LayoutTable(
    val id: String,
    val mergedIntoTableId: String?,
    val mergedTableIds: List<String>,
    val tableNumber: String,
    val name: String?,
    val capacity: Int,
    val effectiveCapacity: Int,
    val floor: String?,
    val positionX: Float?,
    val positionY: Float?,
    val rotationDegrees: Float,
    val scale: Float,
    val shape: LayoutTableShape,
    val status: LayoutTableStatus,
    val guestCount: Int? = null,
    val seatedAt: String? = null,
    val nextReservationStart: String? = null,
    val nextReservationEnd: String? = null,
    val nextReservationCode: String? = null,
    val nextReservationName: String? = null,
    // The booking's state for the floor plan: "Hold ends in N min" from nextReservationHoldWarningAt.
    val nextReservationId: String? = null,
    val nextReservationStatus: String? = null,
    val nextReservationHoldUntil: String? = null,
    val nextReservationHoldWarningAt: String? = null,
    val nextReservationPartySize: Int? = null,
    val nextReservationArrivedGuests: Int? = null,
    val nextReservationOccasionIcon: String? = null,
    val nextReservationOccasionName: String? = null,
    val currentOrderId: String? = null,
    val currentOrderNumber: String? = null,
    val currentOrderStatus: String? = null,
    val currentOrderFulfillmentStatus: String? = null,
    val active: Boolean
)

enum class LayoutTableShape {
    RECTANGLE,
    ROUND,
    SQUARE,
    OVAL,
    CUSTOM
}

enum class LayoutTableStatus {
    AVAILABLE,
    RESERVED,
    OCCUPIED,
    DIRTY,
    MAINTENANCE,
    OUT_OF_SERVICE
}

data class TableSection(
    val id: String,
    val code: String,
    val name: String,
    val displayOrder: Int,
    val tableIds: Set<String>
)
