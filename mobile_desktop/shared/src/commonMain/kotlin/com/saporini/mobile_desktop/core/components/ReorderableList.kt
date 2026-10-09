package com.saporini.mobile_desktop.core.components

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import kotlin.math.abs
import kotlin.math.roundToInt

// Fixed-height rows reordered by dragging the handle each row puts on dragHandle (long press on phones).
@Composable
fun <T> ReorderableList(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    rowHeight: Dp = 56.dp,
    rowGap: Dp = 8.dp,
    row: @Composable (item: T, isDragging: Boolean, dragHandle: Modifier, modifier: Modifier) -> Unit
) {
    val isPhone = isPhoneWindow()
    val rowStep = rowHeight + rowGap
    val listHeight = if (items.isEmpty()) 0.dp else rowStep * items.size - rowGap
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var draggedY by remember { mutableFloatStateOf(0f) }
    var gestureOrder by remember { mutableStateOf<List<T>?>(null) }
    val latestItems by rememberUpdatedState(items)
    val latestOnReorder by rememberUpdatedState(onReorder)

    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = listHeight, max = listHeight)) {
        val listWidth = maxWidth
        val density = LocalDensity.current
        val rowStepPx = with(density) { rowStep.toPx() }
        val rowHeightPx = with(density) { rowHeight.toPx() }
        fun slotY(index: Int) = index * rowStepPx

        items.forEachIndexed { index, item ->
            val itemKey = key(item)
            key(itemKey) {
                val animatedOffset by animateIntOffsetAsState(
                    targetValue = IntOffset(0, slotY(index).roundToInt()),
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                    label = "reorder-slot"
                )
                val isDragging = draggingKey == itemKey
                val dragHandle = Modifier.pointerInput(itemKey, rowStepPx, isPhone) {
                    val startDrag: (Offset) -> Unit = {
                        val current = latestItems.indexOfFirst { key(it) == itemKey }
                        if (current >= 0) {
                            gestureOrder = latestItems
                            draggedY = slotY(current)
                            draggingKey = itemKey
                        }
                    }
                    val endDrag: () -> Unit = {
                        draggingKey = null
                        gestureOrder = null
                    }
                    val moveDrag: (PointerInputChange, Offset) -> Unit = { change, amount ->
                        change.consume()
                        if (draggingKey == itemKey) {
                            draggedY += amount.y
                            val order = gestureOrder ?: latestItems
                            val from = order.indexOfFirst { key(it) == itemKey }
                            val center = draggedY + rowHeightPx / 2f
                            val to = order.indices.minByOrNull { abs(slotY(it) + rowHeightPx / 2f - center) }
                            if (from >= 0 && to != null && from != to) {
                                val reordered = order.toMutableList().also { it.add(to, it.removeAt(from)) }
                                gestureOrder = reordered
                                latestOnReorder(reordered)
                            }
                        }
                    }
                    if (isPhone) {
                        detectDragGesturesAfterLongPress(onDragStart = startDrag, onDragEnd = endDrag, onDragCancel = endDrag, onDrag = moveDrag)
                    } else {
                        detectDragGestures(onDragStart = startDrag, onDragEnd = endDrag, onDragCancel = endDrag, onDrag = moveDrag)
                    }
                }
                row(
                    item,
                    isDragging,
                    dragHandle,
                    Modifier.width(listWidth).heightIn(min = rowHeight, max = rowHeight)
                        .zIndex(if (isDragging) 2f else 0f)
                        .offset { if (isDragging) IntOffset(0, draggedY.roundToInt()) else animatedOffset }
                        .graphicsLayer {
                            if (isDragging) {
                                scaleX = 1.015f
                                scaleY = 1.015f
                            }
                        }
                )
            }
        }
    }
}
