package com.saporini.mobile_desktop.pos.tables.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class FloorLayoutResponseDto(
    val id: String,
    val restaurantId: String,
    val branchId: String,
    val floorName: String,
    val planImageKey: String? = null,
    val planImageUrl: String? = null,
    val planOffsetX: Double = 0.0,
    val planOffsetY: Double = 0.0,
    val planScale: Double = 1.0
)

@Serializable
data class FloorLayoutRequestDto(
    val floorName: String,
    val planOffsetX: Double = 0.0,
    val planOffsetY: Double = 0.0,
    val planScale: Double = 1.0
)