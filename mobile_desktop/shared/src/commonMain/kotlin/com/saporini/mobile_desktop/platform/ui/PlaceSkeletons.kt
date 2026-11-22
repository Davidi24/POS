package com.saporini.mobile_desktop.platform.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.components.SkeletonBlock
import com.saporini.mobile_desktop.core.components.SkeletonBox
import com.saporini.mobile_desktop.core.components.SkeletonLight
import com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha

/*
 * The Super Admin pages while they load (user, 2026-11-21: "fix the loading for all pages here"): pulsing shapes laid out
 * like each page's own cards, never a spinner, then a short fade into the cards (as Users and Inventory do). In the grids
 * the shapes are grid items of their own, so they take the same places the cards will; they go quickly and the cards
 * fade in just after, so the two never show on top of each other.
 */

/** How many loading cards a grid shows: two rows on a wide window, three cards on a phone. */
internal fun shapesFor(narrow: Boolean): Int = if (narrow) 3 else 6

/** A loading card inside a lazy grid: it leaves quickly when the real cards come. */
@Composable
internal fun LazyGridItemScope.ShapeCell(content: @Composable () -> Unit) {
    Box(Modifier.animateItem(fadeInSpec = tween(160, delayMillis = 80), placementSpec = null, fadeOutSpec = tween(80))) { content() }
}

/** How a real card comes in after the shapes (and after a search or a new page): it fades in once they've gone. */
internal val CardFadeIn: FiniteAnimationSpec<Float> = tween(220, delayMillis = 90)

/** A card's placement when the grid changes (an answered card leaving, the rest closing up). */
internal val CardPlacement: FiniteAnimationSpec<IntOffset> = tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)

/** A figure in a page's banner while it loads: a block where the number goes. */
@Composable
internal fun FigureShape(width: Dp = 34.dp) {
    val alpha = rememberSkeletonAlpha("banner-figure")
    SkeletonBox(Modifier.padding(vertical = 4.dp).alpha(alpha).size(width = width, height = 22.dp), SkeletonBlock, RoundedCornerShape(6.dp))
}

// ---- Pieces ----

@Composable
private fun Bar(fraction: Float, height: Dp, color: Color = SkeletonLight) {
    SkeletonBox(Modifier.fillMaxWidth(fraction).height(height), color)
}

@Composable
private fun Pill(width: Dp, height: Dp = 24.dp, color: Color = SkeletonLight, modifier: Modifier = Modifier) {
    SkeletonBox(modifier.size(width = width, height = height), color, RoundedCornerShape(50))
}

/** The light strip of three figures the cards have under their name. */
@Composable
private fun FiguresStrip(cells: Int = 3) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(12.dp)).background(Kit.Canvas),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(cells) { index ->
            if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 12.dp).background(Kit.Border))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBox(Modifier.size(width = 34.dp, height = 14.dp))
                SkeletonBox(Modifier.size(width = 52.dp, height = 9.dp), SkeletonLight)
            }
        }
    }
}

/** A line with a small icon in front, like the cards' contact and fact lines. */
@Composable
private fun IconLine(fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SkeletonBox(Modifier.size(13.dp), SkeletonLight, CircleShape)
        Bar(fraction, 10.dp)
    }
}

/** A round face with a name and a line under it. */
@Composable
private fun FaceLines(face: Dp, first: Float, second: Float, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBox(Modifier.size(face), SkeletonLight, CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Bar(first, 12.dp, SkeletonBlock)
            Bar(second, 9.dp)
        }
    }
}

/** Two buttons side by side across a card's foot. */
@Composable
private fun TwoButtons() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBox(Modifier.weight(1f).height(40.dp), SkeletonLight, RoundedCornerShape(10.dp))
        SkeletonBox(Modifier.weight(1f).height(40.dp), SkeletonLight, RoundedCornerShape(10.dp))
    }
}

/**
 * A card with a picture band on top (its pills in the corners) and, when [logoTop] is given, the logo square on the band's
 * edge; [body] draws the lines under it.
 */
@Composable
private fun BandCard(band: Dp, logoTop: Dp?, bodyTop: Dp, modifier: Modifier, corner: Dp = 18.dp,
                     bandExtra: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {}, body: @Composable ColumnScope.() -> Unit) {
    val alpha = rememberSkeletonAlpha("place-card")
    Surface(modifier.fillMaxWidth().alpha(alpha), shape = RoundedCornerShape(corner), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Box {
            Column {
                Box(Modifier.fillMaxWidth().height(band).background(SkeletonLight.copy(alpha = 0.55f))) {
                    Pill(88.dp, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
                    bandExtra()
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = bodyTop, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = body)
            }
            if (logoTop != null) {
                Box(Modifier.padding(start = 20.dp, top = logoTop).size(66.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(3.dp)) {
                    SkeletonBox(Modifier.fillMaxSize(), SkeletonBlock, RoundedCornerShape(15.dp))
                }
            }
        }
    }
}

// ---- The pages' cards ----

/** A restaurant card: band, status, logo on the edge, name and place, the figures strip, then the foot with its Open pill. */
@Composable
internal fun RestaurantCardShape(modifier: Modifier = Modifier) {
    BandCard(band = 112.dp, logoTop = 78.dp, bodyTop = 40.dp, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Bar(0.58f, 16.dp, SkeletonBlock)
            IconLine(0.42f)
        }
        FiguresStrip()
        KitDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBox(Modifier.size(width = 70.dp, height = 10.dp), SkeletonLight)
            SkeletonBox(Modifier.size(width = 54.dp, height = 10.dp), SkeletonLight)
            Spacer(Modifier.weight(1f))
            Pill(96.dp, 32.dp)
        }
    }
}

/** A branch card: wide, the picture on the left with its map pin, then BRANCH and its code, name, address, facts, Work here. */
@Composable
internal fun BranchCardShape(narrow: Boolean, modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonAlpha("branch-card")
    Surface(modifier.fillMaxWidth().alpha(alpha), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Row(Modifier.height(if (narrow) 168.dp else 158.dp)) {
            Box(Modifier.width(if (narrow) 112.dp else 168.dp).fillMaxHeight().background(SkeletonLight.copy(alpha = 0.55f))) {
                SkeletonBox(Modifier.align(Alignment.BottomStart).padding(10.dp).size(36.dp), SkeletonLight, CircleShape)
            }
            Box(Modifier.width(4.dp).fillMaxHeight().background(SkeletonLight))
            Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(Modifier.size(width = 48.dp, height = 9.dp), SkeletonLight)
                    SkeletonBox(Modifier.size(width = 38.dp, height = 14.dp), SkeletonLight, RoundedCornerShape(5.dp))
                    Spacer(Modifier.weight(1f))
                    SkeletonBox(Modifier.size(width = 50.dp, height = 9.dp), SkeletonLight)
                }
                Bar(0.6f, 15.dp, SkeletonBlock)
                IconLine(0.5f)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(if (narrow) 2 else 3) { SkeletonBox(Modifier.size(width = 58.dp, height = 10.dp), SkeletonLight) }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Pill(104.dp, 32.dp) }
            }
        }
    }
}

/** An application card: band, logo, name and legal name, two lines of description, the strip, contacts, the owner, the buttons. */
@Composable
internal fun ApplicationCardShape(modifier: Modifier = Modifier) {
    BandCard(band = 150.dp, logoTop = 114.dp, bodyTop = 38.dp, modifier = modifier,
        bandExtra = { Pill(64.dp, 22.dp, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)) }) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Bar(0.55f, 16.dp, SkeletonBlock)
            Bar(0.38f, 10.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Bar(0.95f, 10.dp)
            Bar(0.7f, 10.dp)
        }
        FiguresStrip()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconLine(0.52f)
            IconLine(0.4f)
            IconLine(0.46f)
        }
        KitDivider()
        // Owner, then Reject / Approve.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBox(Modifier.size(width = 96.dp, height = 40.dp), SkeletonLight, RoundedCornerShape(10.dp))
            SkeletonBox(Modifier.weight(1f).height(40.dp), SkeletonLight, RoundedCornerShape(10.dp))
            SkeletonBox(Modifier.weight(1f).height(40.dp), SkeletonLight, RoundedCornerShape(10.dp))
        }
    }
}

/** A request card: the restaurant's picture with the kind and stage pills, the logo, who asked, the steps, the note, who it's about. */
@Composable
internal fun RequestCardShape(modifier: Modifier = Modifier) {
    BandCard(band = 116.dp, logoTop = 80.dp, bodyTop = 38.dp, modifier = modifier,
        bandExtra = { Pill(70.dp, 22.dp, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) }) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Bar(0.55f, 16.dp, SkeletonBlock)
            Bar(0.36f, 10.dp)
        }
        // Open → Assigned → In progress → Solved.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { index ->
                if (index > 0) Box(Modifier.weight(1f).height(2.dp).background(SkeletonLight))
                SkeletonBox(Modifier.size(16.dp), SkeletonLight, CircleShape)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Bar(0.9f, 10.dp)
            Bar(0.6f, 10.dp)
        }
        FaceLines(32.dp, 0.4f, 0.6f)
        KitDivider()
        TwoButtons()
    }
}

/** A support admin's card: the band with the figure on its right, the face and name, the restaurants as chips, the buttons. */
@Composable
internal fun AdminCardShape(modifier: Modifier = Modifier) {
    BandCard(band = 118.dp, logoTop = null, bodyTop = 14.dp, modifier = modifier, corner = 18.dp,
        bandExtra = {
            SkeletonBox(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, top = 12.dp).size(width = 82.dp, height = 100.dp), SkeletonLight,
                RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
            Pill(108.dp, 22.dp, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        }) {
        FaceLines(44.dp, 0.5f, 0.7f)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SkeletonBox(Modifier.size(width = 92.dp, height = 9.dp), SkeletonLight)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(78.dp)
                Pill(64.dp)
                Pill(88.dp)
            }
        }
        Bar(0.62f, 10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.size(width = 118.dp, height = 38.dp), SkeletonLight, RoundedCornerShape(10.dp))
            SkeletonBox(Modifier.size(width = 150.dp, height = 38.dp), SkeletonLight, RoundedCornerShape(10.dp))
        }
    }
}

// ---- Inside windows ----

/** Lines with a switch at the end, as a settings window loads (a support admin's restaurants, what they can do). */
@Composable
internal fun SwitchLinesShape(rows: Int, modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonAlpha("window-lines")
    Column(modifier.fillMaxWidth().alpha(alpha)) {
        repeat(rows) { row ->
            if (row > 0) KitDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Bar(listOf(0.42f, 0.34f, 0.5f, 0.38f)[row % 4], 12.dp, SkeletonBlock)
                    Bar(listOf(0.62f, 0.55f, 0.7f, 0.48f)[row % 4], 9.dp)
                }
                SkeletonBox(Modifier.size(width = 42.dp, height = 24.dp), SkeletonLight, RoundedCornerShape(50))
            }
        }
    }
}

/** What a support admin can do, as it loads: who they are, then cards of switches. */
@Composable
internal fun PermissionCardsShape(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonAlpha("window-permissions")
    Column(modifier.fillMaxWidth().alpha(alpha), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).padding(14.dp)) { FaceLines(44.dp, 0.3f, 0.5f) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(2) {
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                    Column {
                        Row(Modifier.fillMaxWidth().background(Kit.Tint).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SkeletonBox(Modifier.size(width = 110.dp, height = 13.dp))
                            Spacer(Modifier.weight(1f))
                            Pill(54.dp, 20.dp)
                        }
                        Column(Modifier.padding(horizontal = 14.dp)) {
                            repeat(4) { row ->
                                if (row > 0) KitDivider()
                                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { Bar(listOf(0.6f, 0.45f, 0.7f, 0.52f)[row], 11.dp) }
                                    SkeletonBox(Modifier.size(width = 38.dp, height = 22.dp), SkeletonLight, RoundedCornerShape(50))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A form as it loads: the cover picture, then labelled fields two a row. */
@Composable
internal fun FormShape(fields: Int, modifier: Modifier = Modifier, cover: Boolean = true) {
    val alpha = rememberSkeletonAlpha("window-form")
    Column(modifier.fillMaxWidth().alpha(alpha), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (cover) SkeletonBox(Modifier.fillMaxWidth().height(120.dp), SkeletonLight, RoundedCornerShape(14.dp))
        repeat((fields + 1) / 2) { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(if (pair * 2 + 1 < fields) 2 else 1) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SkeletonBox(Modifier.size(width = 84.dp, height = 10.dp))
                        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                            .background(SkeletonLight.copy(alpha = 0.45f)))
                    }
                }
            }
        }
    }
}

/** The Admins page as it loads: the restaurant filter's place, then rows of admin cards (three, two or one a row). */
@Composable
internal fun AdminGridShape(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 900.dp -> 3
            maxWidth >= 560.dp -> 2
            else -> 1
        }
        val filterWidth = if (columns == 1) maxWidth else 340.dp
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val alpha = rememberSkeletonAlpha("admin-filter")
            SkeletonBox(Modifier.alpha(alpha).size(width = filterWidth, height = 40.dp), SkeletonLight, RoundedCornerShape(10.dp))
            repeat(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    repeat(columns) { AdminCardShape(Modifier.weight(1f)) }
                }
            }
        }
    }
}
