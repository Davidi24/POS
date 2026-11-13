package com.saporini.mobile_desktop.platform.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Tiles in a grid that can be dragged into a new order, like the menu items' "Change items position": drag a tile
 * (long-press first on a phone) and the others make room; [onReorder] gets the new order on every move.
 */
@Composable
internal fun <T> ReorderGrid(
    items: List<T>,
    keyOf: (T) -> String,
    onReorder: (List<T>) -> Unit,
    minTileWidth: Dp,
    tileHeight: Dp,
    touch: Boolean,
    modifier: Modifier = Modifier,
    gap: Dp = 20.dp,
    tile: @Composable (item: T, dragging: Boolean) -> Unit
) {
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val latest by rememberUpdatedState(items)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val columns = maxOf(1, ((maxWidth + gap) / (minTileWidth + gap)).toInt())
        val tileWidth = (maxWidth - gap * (columns - 1)) / columns
        val rows = (items.size + columns - 1) / columns
        val widthPx = with(density) { tileWidth.toPx() }
        val heightPx = with(density) { tileHeight.toPx() }
        val gapPx = with(density) { gap.toPx() }

        fun slot(index: Int) = Offset((index % columns) * (widthPx + gapPx), (index / columns) * (heightPx + gapPx))
        fun nearest(position: Offset): Int? {
            if (latest.isEmpty()) return null
            val center = position + Offset(widthPx / 2f, heightPx / 2f)
            return latest.indices.minByOrNull { (slot(it) + Offset(widthPx / 2f, heightPx / 2f) - center).getDistance() }
        }

        Box(Modifier.fillMaxWidth().height(if (rows == 0) 0.dp else tileHeight * rows + gap * (rows - 1))) {
            items.forEachIndexed { index, item ->
                val id = keyOf(item)
                key(id) {
                    val target = slot(index).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) }
                    val animated by animateIntOffsetAsState(target, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "tile-$id")
                    val isDragging = dragging == id
                    Box(
                        Modifier.width(tileWidth).height(tileHeight).zIndex(if (isDragging) 10f else 0f)
                            .offset { if (isDragging) IntOffset(dragPosition.x.roundToInt(), dragPosition.y.roundToInt()) else animated }
                            .graphicsLayer {
                                if (isDragging) {
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                }
                            }
                            .pointerInput(id, widthPx, heightPx, touch) {
                                val start: (Offset) -> Unit = {
                                    val current = latest.indexOfFirst { keyOf(it) == id }
                                    if (current >= 0) {
                                        dragPosition = slot(current)
                                        dragging = id
                                    }
                                }
                                val end = { dragging = null }
                                val move: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit = { change, amount ->
                                    change.consume()
                                    if (dragging == id) {
                                        dragPosition += amount
                                        val from = latest.indexOfFirst { keyOf(it) == id }
                                        val to = nearest(dragPosition)
                                        if (from >= 0 && to != null && to != from) {
                                            val order = latest.toMutableList()
                                            order.add(to, order.removeAt(from))
                                            onReorder(order)
                                        }
                                    }
                                }
                                if (touch) detectDragGesturesAfterLongPress(onDragStart = start, onDragEnd = end, onDragCancel = end, onDrag = move)
                                else detectDragGestures(onDragStart = start, onDragEnd = end, onDragCancel = end, onDrag = move)
                            }
                    ) {
                        tile(item, isDragging)
                    }
                }
            }
        }
    }
}
