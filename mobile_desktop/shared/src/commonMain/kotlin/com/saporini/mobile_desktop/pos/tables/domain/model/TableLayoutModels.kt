package com.saporini.mobile_desktop.pos.tables.domain.model

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
