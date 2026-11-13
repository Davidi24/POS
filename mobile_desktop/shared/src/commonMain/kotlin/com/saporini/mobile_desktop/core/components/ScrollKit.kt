package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.ui.PlatformVerticalScrollbar

// Anything that scrolls shows a thin scrollbar on desktop, so people can see there is more (user rule). Touch
// platforms draw nothing; they show their own indicator while scrolling.

/** Room kept on the right of scrolling content for the scrollbar (desktop only; touch screens draw none, so keep none). */
internal val ScrollbarGap: Dp get() = com.saporini.mobile_desktop.core.ui.PlatformScrollbarRoom

/** The scrollbar itself, along the right edge of the enclosing box. */
@Composable
internal fun BoxScope.EdgeScrollbar(state: ScrollState) {
    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
        PlatformVerticalScrollbar(state, Modifier.fillMaxHeight().width(6.dp))
    }
}

@Composable
internal fun BoxScope.EdgeScrollbar(state: LazyListState) {
    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
        PlatformVerticalScrollbar(state, Modifier.fillMaxHeight().width(6.dp))
    }
}

/**
 * On a phone-wide window the content keeps no room on its right for the scrollbar, which sits just outside it in the page's
 * side room instead, so both sides of the page look the same (user, 2026-10-30: "not 100% in the centre").
 */
@Composable
private fun scrollbarOutside(): Boolean = com.saporini.mobile_desktop.core.ui.isPhoneWindow()

private val OutsideScrollbarShift = 11.dp

/** A column that scrolls, with the scrollbar. Its height follows the content up to what [modifier] allows. */
@Composable
internal fun ScrollColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val outside = scrollbarOutside()
    Box(modifier) {
        Column(Modifier.fillMaxWidth().verticalScroll(state).padding(end = if (outside) 0.dp else ScrollbarGap), verticalArrangement, horizontalAlignment, content)
        Box(Modifier.matchParentSize().offset(x = if (outside) OutsideScrollbarShift else 0.dp)) { EdgeScrollbar(state) }
    }
}

/** A lazy list with the scrollbar. Its height follows the content up to what [modifier] allows. */
@Composable
internal fun ScrollList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(bottom = 4.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: LazyListScope.() -> Unit
) {
    val outside = scrollbarOutside()
    Box(modifier) {
        LazyColumn(Modifier.fillMaxWidth().padding(end = if (outside) 0.dp else ScrollbarGap), state, contentPadding, verticalArrangement = verticalArrangement,
            content = content)
        Box(Modifier.matchParentSize().offset(x = if (outside) OutsideScrollbarShift else 0.dp)) { EdgeScrollbar(state) }
    }
}

/**
 * Fades the left and right edges of a sideways-scrolling row where more of it waits off-screen (works on any
 * background). Put it before `horizontalScroll(state)`.
 */
internal fun Modifier.fadingEdges(state: ScrollState, width: Dp = 22.dp): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val fade = width.toPx().coerceAtMost(size.width / 2)
        if (state.canScrollBackward) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade), size = Size(fade, size.height),
                blendMode = BlendMode.DstIn)
        }
        if (state.canScrollForward) {
            drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                topLeft = Offset(size.width - fade, 0f), size = Size(fade, size.height), blendMode = BlendMode.DstIn)
        }
    }

/**
 * Keeps the chosen entry of a sideways-scrolling row in the middle of what's shown: [spans] holds where each entry sits
 * in the row (start and width in pixels, from `onGloballyPositioned`), and the row slides when the choice changes.
 */
@Composable
internal fun <K> CenterChosen(state: ScrollState, chosen: K, spans: Map<K, Pair<Int, Int>>) {
    val span = spans[chosen]
    LaunchedEffect(chosen, span, state.viewportSize) {
        span?.let { (start, width) -> state.animateScrollTo((start + width / 2 - state.viewportSize / 2).coerceIn(0, state.maxValue)) }
    }
}

