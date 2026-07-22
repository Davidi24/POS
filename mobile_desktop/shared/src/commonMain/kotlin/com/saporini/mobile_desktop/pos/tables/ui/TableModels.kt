package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class FloorPlanTable(
    val x: Float,
    val y: Float,
    val shape: TableShape,
    val seatCount: Int,
    val label: String,
    val state: TableVisualState = TableVisualState.Free,
    val orderLabel: String? = null,
    val statusText: String? = null,
    val servedItems: Int = 0,
    val totalItems: Int = 0,
    val scale: Float = 0.74f,
    val rotationDegrees: Float = 0f
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

data class TableOrderOverride(
    val orderLabel: String,
    val statusText: String
) {
    fun applyTo(table: FloorPlanTable): FloorPlanTable =
        table.copy(
            state = TableVisualState.Occupied,
            orderLabel = orderLabel,
            statusText = statusText
        )
}
