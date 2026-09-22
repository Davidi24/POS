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
