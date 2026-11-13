package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt

// A phone's top bar (logo on the left, notifications on the right) slides up out of sight while the page scrolls down
// and comes back as soon as it scrolls up (user, 2026-10-30). The page under it takes the room it gives up.

/** How far the bar has slid away; give the scrolling page [connection] with `Modifier.nestedScroll`. */
@Stable
internal class HideOnScrollState {
    internal var offset by mutableFloatStateOf(0f)
    internal var height by mutableFloatStateOf(0f)
    internal var enabled = true

    val connection = object : NestedScrollConnection {
        // Follows what the page really scrolled, so the bar stays put on a page that fits the screen (user, 2026-11-02). A
        // pull down it had no room for still brings the bar back.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (enabled && height > 0f) offset = (offset + consumed.y + available.y.coerceAtLeast(0f)).coerceIn(-height, 0f)
            return Offset.Zero
        }
    }

    /** Brings the bar back (a new page opened). */
    fun show() {
        offset = 0f
    }
}

/** [enabled] false (wider windows) keeps the bar where it is. */
@Composable
internal fun rememberHideOnScroll(enabled: Boolean = true): HideOnScrollState {
    val state = remember { HideOnScrollState() }
    state.enabled = enabled
    LaunchedEffect(enabled) { if (!enabled) state.show() }
    return state
}

/** The bar, sliding with [state]; in a column it only takes the height still showing. */
@Composable
internal fun HideOnScrollBar(state: HideOnScrollState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val shift = state.offset.roundToInt().coerceIn(-placeable.height, 0)
        layout(placeable.width, placeable.height + shift) { placeable.place(0, shift) }
    }) {
        Box(Modifier.onSizeChanged { state.height = it.height.toFloat() }) { content() }
    }
}
