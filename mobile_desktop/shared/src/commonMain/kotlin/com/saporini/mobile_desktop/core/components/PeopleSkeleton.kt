package com.saporini.mobile_desktop.core.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

// Name widths that change from line to line, so the loading list doesn't look stamped out.
private val NameWidths = listOf(0.55f, 0.42f, 0.62f, 0.48f, 0.38f, 0.58f, 0.45f, 0.52f)
private val FigureWidths = listOf(0.7f, 0.5f, 0.6f, 0.45f, 0.65f)

/**
 * Loading lines shaped like the people lists (Users → a role, Salary → a team): the colour strip, a round face, the
 * name with a line under it, [figures] blocks for the columns, then [buttons] button shapes, pulsing like the other
 * skeletons. A phone's lines keep just the face, the name and one figure.
 */
@Composable
internal fun PeopleLinesSkeleton(rows: Int, modifier: Modifier, figures: Int = 3, buttons: Int = 2, phone: Boolean = false, lineHeight: Dp = 64.dp) {
    val alpha = rememberSkeletonAlpha("people-lines")
    Column(modifier.alpha(alpha).clipToBounds(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(rows) { row ->
            Surface(Modifier.fillMaxWidth().height(lineHeight), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.RowBorder)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(Modifier.width(4.dp).fillMaxHeight(), SkeletonLight, RectangleShape)
                    Row(Modifier.weight(1f).padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.weight(2.2f), verticalAlignment = Alignment.CenterVertically) {
                            SkeletonBox(Modifier.size(if (phone) 36.dp else 40.dp), SkeletonLight, CircleShape)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                SkeletonBox(Modifier.fillMaxWidth(NameWidths[row % NameWidths.size]).height(12.dp))
                                SkeletonBox(Modifier.fillMaxWidth(NameWidths[(row + 3) % NameWidths.size] + 0.15f).height(9.dp), SkeletonLight)
                            }
                        }
                        if (phone) SkeletonBox(Modifier.size(width = 64.dp, height = 13.dp))
                        else {
                            repeat(figures) { column ->
                                Box(Modifier.weight(1f)) {
                                    SkeletonBox(Modifier.fillMaxWidth(FigureWidths[(row + column) % FigureWidths.size]).height(11.dp), SkeletonLight)
                                }
                            }
                            repeat(buttons) { SkeletonBox(Modifier.size(width = 66.dp, height = 28.dp), SkeletonLight, RoundedCornerShape(8.dp)) }
                        }
                        SkeletonBox(Modifier.size(width = 4.dp, height = 16.dp), SkeletonLight, RoundedCornerShape(2.dp))
                    }
                }
            }
        }
    }
}

/**
 * [loading], held a little: once the loading shapes show they stay at least [minMillis], so a quick load doesn't flash
 * them for a moment (data, then shapes, then data again looked messy; user, 2026-10-31).
 */
@Composable
internal fun steadyLoading(loading: Boolean, minMillis: Long = 350): Boolean {
    var held by remember { mutableStateOf(false) }
    val since = remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    LaunchedEffect(loading) {
        if (loading) {
            since.value = TimeSource.Monotonic.markNow()
            held = true
        } else if (held) {
            val left = minMillis - since.value.elapsedNow().inWholeMilliseconds
            if (left > 0) delay(left)
            held = false
        }
    }
    return loading || held
}

/**
 * The swap between what's shown and the loading shapes (either way): the old one fades out quickly first, then the new
 * one fades in, so the two never show on top of each other.
 */
internal fun loadingSwap(toLoading: Boolean): ContentTransform =
    if (toLoading) fadeIn(tween(160, delayMillis = 80)) togetherWith fadeOut(tween(80))
    else fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90))
