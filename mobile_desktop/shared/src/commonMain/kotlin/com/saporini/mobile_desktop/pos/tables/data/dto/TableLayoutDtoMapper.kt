package com.saporini.mobile_desktop.pos.tables.data.dto

import com.saporini.mobile_desktop.pos.tables.domain.model.BranchTableLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorSummary
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableShape
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableStatus

fun FloorLayoutResponseDto.toDomain(
    baseUrl: String
): FloorLayout {
    return FloorLayout(
        id = id,
        restaurantId = restaurantId,
        branchId = branchId,
        floorName = floorName,
        planImageKey = planImageKey,
        planImageUrl = planImageUrl?.toAbsoluteUrl(baseUrl),
        planOffsetX = planOffsetX.toFloat(),
        planOffsetY = planOffsetY.toFloat(),
        planScale = planScale.toFloat()
    )
}

fun FloorLayout.toRequestDto(): FloorLayoutRequestDto {
    return FloorLayoutRequestDto(
        floorName = floorName,
        planOffsetX = planOffsetX.toDouble(),
        planOffsetY = planOffsetY.toDouble(),
        planScale = planScale.toDouble()
    )
}

fun TableLayoutResponseDto.toDomain(): BranchTableLayout {
    return BranchTableLayout(
        restaurantId = restaurantId,
        branchId = branchId,
        floors = floors.map { floor ->
            FloorSummary(
                name = floor.name,
                tableCount = floor.tableCount,
                positionedTableCount = floor.positionedTableCount
            )
        },
        tables = tables.map { table ->
            LayoutTable(
                id = table.tableId,
                mergedIntoTableId = table.mergedIntoTableId,
                mergedTableIds = table.mergedTableIds,
                tableNumber = table.tableNumber,
                name = table.name,
                capacity = table.capacity,
                effectiveCapacity = table.effectiveCapacity,
                floor = table.floor,
                positionX = table.positionX?.toFloat(),
                positionY = table.positionY?.toFloat(),
                rotationDegrees = table.rotationDegrees.toFloat(),
                scale = table.layoutScale.toFloat(),
                shape = table.shape.toLayoutTableShape(),
                status = table.status.toLayoutTableStatus(),
                guestCount = table.guestCount,
                seatedAt = table.seatedAt,
                active = table.active
            )
        }
    )
}

fun LayoutTable.toRequestDto(): TableLayoutItemRequestDto {
    return TableLayoutItemRequestDto(
        tableId = id,
        floor = floor,
        positionX = positionX?.toDouble(),
        positionY = positionY?.toDouble(),
        rotationDegrees = rotationDegrees.toDouble(),
        layoutScale = scale.toDouble(),
        shape = shape.name
    )
}

private fun String.toLayoutTableShape(): LayoutTableShape {
    return LayoutTableShape.entries.firstOrNull {
        it.name == uppercase()
    } ?: LayoutTableShape.CUSTOM
}

private fun String.toLayoutTableStatus(): LayoutTableStatus {
    return LayoutTableStatus.entries.firstOrNull {
        it.name == uppercase()
    } ?: LayoutTableStatus.OUT_OF_SERVICE
}

private fun String.toAbsoluteUrl(baseUrl: String): String {
    if (startsWith("http://") || startsWith("https://")) {
        return this
    }

    return "${baseUrl.trimEnd('/')}/${trimStart('/')}"
}

fun LayoutTable.toCreateRequestDto(): TableRequestDto {
    return TableRequestDto(
        tableNumber = tableNumber,
        name = name,
        capacity = capacity,
        floor = requireNotNull(floor),
        positionX = requireNotNull(positionX).toDouble(),
        positionY = requireNotNull(positionY).toDouble(),
        rotationDegrees = rotationDegrees.toDouble(),
        layoutScale = scale.toDouble(),
        shape = shape.name,
        status = status.name,
        active = active
    )
}

fun TableResponseDto.toDomain(): LayoutTable {
    return LayoutTable(
        id = id,
        mergedIntoTableId = mergedIntoTableId,
        mergedTableIds = mergedTableIds,
        tableNumber = tableNumber,
        name = name,
        capacity = capacity,
        effectiveCapacity = effectiveCapacity,
        floor = floor,
        positionX = positionX?.toFloat(),
        positionY = positionY?.toFloat(),
        rotationDegrees = rotationDegrees.toFloat(),
        scale = layoutScale.toFloat(),
        shape = shape.toLayoutTableShape(),
        status = status.toLayoutTableStatus(),
        guestCount = guestCount,
        seatedAt = seatedAt,
        active = active
    )
}
