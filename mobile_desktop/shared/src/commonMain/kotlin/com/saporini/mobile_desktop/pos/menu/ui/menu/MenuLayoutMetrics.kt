package com.saporini.mobile_desktop.pos.menu.ui.menu

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Choose columns from actual content space, including gaps and enlarged text. */
internal fun menuContentColumns(width: Dp, minimumCardWidth: Dp, gap: Dp, fontScale: Float = 1f): Int {
    val minimum = minimumCardWidth * fontScale.coerceAtLeast(1f)
    return ((width + gap) / (minimum + gap)).toInt().coerceAtLeast(1)
}

internal fun menuDialogWidth(available: Dp, fraction: Float, maximum: Dp): Dp =
    minOf(available, maximum, maxOf(available * fraction, 560.dp))

internal data class DesktopMenuCoverLayout(val height: Dp, val topInset: Dp, val centerRow: Boolean)

/** Give a lone row breathing room, without stretching short or unusually tall windows. */
internal fun desktopMenuCoverLayout(availableHeight: Dp, rowCount: Int): DesktopMenuCoverLayout {
    val centerRow = rowCount == 1 && availableHeight in 480.dp..1120.dp
    val height = if (centerRow) minOf(520.dp, availableHeight - 32.dp) else minOf(440.dp, availableHeight)
    val inset = if (centerRow) ((availableHeight - height) / 2f).coerceIn(0.dp, 72.dp) else 0.dp
    return DesktopMenuCoverLayout(height, inset, centerRow)
}
