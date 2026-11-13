package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.material.icons.outlined.Insights
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.TrendBadge
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.shortMoney
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.StatsView
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.outlined.RestaurantMenu
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.ui.RemoteImage
import com.saporini.mobile_desktop.statistics.DishFigures

// Pieces every Statistics tab is built from: figure tiles with a sparkline, rows of panels, small figure lines.

internal fun StatsView.money(cents: Long): String = moneyText(cents, currency)
internal fun StatsView.short(cents: Long): String = shortMoney(cents, currency)
internal fun StatsView.axisMoney(euros: Float): String = shortMoney((euros * 100).toLong(), currency)

/** 12,345 → "12,345". */
internal fun countText(value: Long): String = value.toString().reversed().chunked(3).joinToString(",").reversed().replace("-,", "-")

/** 0.2861 → "28.6%". */
internal fun percent1(fraction: Float): String = "${(fraction * 1000).toInt() / 10f}%"

/** True for tiles in a narrow grid (phones): a smaller picture and smaller text. */
private val LocalNarrowTile = compositionLocalOf { false }

/**
 * One figure, in the look of the Inventory's summary cards: a strip of its colour on the left, its 3D picture (or an
 * icon on a soft tile), the name, the value with how it moved against the comparison (green when better, red when
 * worse; lower is better when [inverted]) and one line of detail. Clicking opens [onClick] (the tab with the detail).
 */
@Composable
internal fun StatTile(
    label: String,
    value: String,
    modifier: Modifier,
    change: String? = null,
    inverted: Boolean = false,
    detail: String? = null,
    icon: ImageVector? = null,
    tone: Color = StatsAccent,
    picture: DrawableResource? = null,
    onClick: (() -> Unit)? = null
) {
    val narrow = LocalNarrowTile.current
    val strip = if (narrow) 4.dp else 5.dp
    Surface(
        modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 1.dp
    ) {
        Row(
            Modifier.heightIn(min = if (narrow) 78.dp else 84.dp).drawBehind { drawRect(tone, size = Size(strip.toPx(), size.height)) }
                .padding(start = strip + (if (narrow) 9.dp else 12.dp), end = if (narrow) 9.dp else 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (picture != null) {
                Image(painterResource(picture), null, Modifier.size(if (narrow) 30.dp else 52.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(if (narrow) 7.dp else 11.dp))
            } else if (icon != null) {
                Box(Modifier.size(if (narrow) 28.dp else 44.dp).clip(RoundedCornerShape(if (narrow) 8.dp else 12.dp)).background(tone.copy(alpha = 0.12f)),
                    Alignment.Center) {
                    Icon(icon, null, Modifier.size(if (narrow) 16.dp else 22.dp), tint = tone)
                }
                Spacer(Modifier.width(if (narrow) 7.dp else 11.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (narrow) 1.dp else 2.dp)) {
                Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = if (narrow) 11.sp else 12.sp, color = Kit.Muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(value, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 16.sp else 20.sp,
                        color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!narrow) TrendBadge(change, inverted)
                }
                // A narrow tile has no room beside its value: the change goes on a line of its own.
                if (narrow) TrendBadge(change, inverted)
                detail?.let {
                    Text(it, fontFamily = Inter(), fontSize = if (narrow) 10.sp else 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One figure of a [FigureGrid]. */
internal class FigureCell(
    val label: String,
    val value: String,
    val picture: DrawableResource,
    val change: String? = null,
    val inverted: Boolean = false,
    val detail: String? = null,
    val onClick: (() -> Unit)? = null
)

/**
 * The key figures as one panel of lines in groups (user, 2026-11-03: "I don't like the grid, make it much better"): each
 * line its 3D picture, the name and one line of detail, the value at the right and how it moved in a column of its own,
 * so names are never cut and the values line up.
 */
@Composable
internal fun KeyFigures(
    groups: List<Pair<String, List<FigureCell>>>,
    modifier: Modifier,
    note: String? = null,
    title: String = "Key figures",
    icon: ImageVector = Icons.Outlined.Insights
) {
    StatsPanel(icon, title, modifier, note = note) {
        groups.forEachIndexed { index, (heading, cells) ->
            // A panel of one group has no heading: its title says it.
            if (heading.isNotBlank()) Text(heading.uppercase(), Modifier.padding(start = 6.dp, top = if (index == 0) 0.dp else 8.dp, bottom = 2.dp), fontFamily = Inter(),
                fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = Kit.Faint)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas)) {
                cells.forEachIndexed { i, cell ->
                    if (i > 0) Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(Kit.RowBorder))
                    KeyFigureLine(cell)
                }
            }
        }
    }
}

@Composable
private fun KeyFigureLine(cell: FigureCell) {
    Row(
        Modifier.fillMaxWidth().then(if (cell.onClick != null) Modifier.clickable(onClick = cell.onClick) else Modifier)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), Alignment.Center) {
            Image(painterResource(cell.picture), null, Modifier.size(32.dp), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(cell.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            cell.detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.width(8.dp))
        Text(cell.value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink, maxLines = 1)
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) { TrendBadge(cell.change, cell.inverted) }
    }
}

/**
 * Figures as one grid (user, 2026-11-03: "do a grid, no colours on the left, better icons"): one white box with the
 * cells divided by thin lines; each has its 3D picture, the name with how it moved, the value on a line of its own (so it
 * never gets cut) and one line of detail. As many a row as fit, keeping every row full: five on a wide window, two on a
 * phone or tablet.
 */
@Composable
internal fun FigureGrid(cells: List<FigureCell>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        val columns = listOf(5, 4, 3, 2).firstOrNull { cells.size % it == 0 && width / it >= 196.dp } ?: 2
        FigureGridBox(cells, columns, compact = width / columns < 236.dp, modifier = Modifier.fillMaxWidth(), fill = false)
    }
}

/**
 * The same grid with its columns given, for a row of panels (which can't hold a BoxWithConstraints): stretched to the
 * row's height, its lines share that height.
 */
@Composable
internal fun FigureGridBox(cells: List<FigureCell>, columns: Int, compact: Boolean, modifier: Modifier, fill: Boolean = true) {
    val line = Kit.Border
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, line), shadowElevation = 1.dp) {
        Column {
            cells.chunked(columns).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(line))
                Row(Modifier.fillMaxWidth().then(if (fill) Modifier.weight(1f) else Modifier).height(IntrinsicSize.Min)) {
                    row.forEachIndexed { index, cell ->
                        if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(line))
                        FigureCellView(cell, compact, Modifier.weight(1f).fillMaxHeight())
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FigureCellView(cell: FigureCell, compact: Boolean, modifier: Modifier) {
    Row(
        modifier.then(if (cell.onClick != null) Modifier.clickable(onClick = cell.onClick) else Modifier)
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(cell.picture), null, Modifier.size(if (compact) 30.dp else 46.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // A narrow cell keeps the name whole: how it moved goes under the value, beside the detail.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(cell.label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = if (compact) 11.5.sp else 12.5.sp,
                    color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) TrendBadge(cell.change, cell.inverted)
            }
            Text(cell.value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (compact) 17.sp else 21.sp, color = Kit.Ink, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (compact) TrendBadge(cell.change, cell.inverted)
            cell.detail?.let {
                Text(it, fontFamily = Inter(), fontSize = if (compact) 10.5.sp else 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The start of a section of a tab (Sales, then Orders…): its 3D picture on a soft band of the section's colour, its
 * name and what it shows.
 */
@Composable
internal fun SectionHeading(title: String, subtitle: String, picture: DrawableResource, tone: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(tone.copy(alpha = 0.13f), tone.copy(alpha = 0.035f), Color.Transparent)))
            .padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.85f)), Alignment.Center) {
            Image(painterResource(picture), null, Modifier.padding(5.dp).size(46.dp), contentScale = ContentScale.Fit)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink, maxLines = 1)
            Text(subtitle, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Figure tiles in a grid, a row's tiles one height: as many a row as fit (each at least 236 dp wide, at most [desktop]),
 * preferring a number that fills every row, so no tile is left alone at the end; two a row on a phone.
 */
@Composable
internal fun TileGrid(tiles: List<@Composable (Modifier) -> Unit>, desktop: Int = 4) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        val gap = if (width < 600.dp) 10.dp else 12.dp
        fun fits(count: Int) = (width - gap * (count - 1)) / count >= 236.dp
        val columns = when {
            width < 600.dp -> 2
            else -> (desktop downTo 2).firstOrNull { fits(it) && tiles.size % it == 0 } ?: (desktop downTo 2).firstOrNull { fits(it) } ?: 2
        }
        val narrow = (width - gap * (columns - 1)) / columns < 200.dp
        CompositionLocalProvider(LocalNarrowTile provides narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                tiles.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * True inside a row of panels kept one height (a [PanelLine] on a wide window): a panel there is stretched to the
 * row's height, and a chart given [roomLeft] grows into the room its panel has left.
 */
private val LocalPanelRow = compositionLocalOf { false }

/** A chart's modifier at the top of a panel's content: the room left when the panel is stretched to its row, else nothing. */
@Composable
internal fun ColumnScope.roomLeft(): Modifier = if (LocalPanelRow.current) Modifier.weight(1f) else Modifier

/**
 * Panels side by side on a wide window, every one as tall as the tallest of the row; in pairs on a tablet, one under
 * the other on a phone. Panel content must not use lazy lists or BoxWithConstraints (the row asks it for its height).
 */
@Composable
internal fun PanelLine(size: ScreenSize, vararg panels: Pair<Float, @Composable (Modifier) -> Unit>) {
    when {
        size.isDesktop || (size == ScreenSize.TABLET && panels.size <= 2) -> PanelRowOf(panels.toList(), pad = false)
        size == ScreenSize.TABLET -> panels.toList().chunked(2).forEach { PanelRowOf(it, pad = it.size == 1) }
        else -> panels.forEach { (_, panel) -> panel(Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun PanelRowOf(panels: List<Pair<Float, @Composable (Modifier) -> Unit>>, pad: Boolean) {
    CompositionLocalProvider(LocalPanelRow provides true) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            panels.forEach { (weight, panel) -> panel(Modifier.weight(weight).fillMaxHeight()) }
            if (pad) Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * Panels one under the other in one place of a [PanelLine]. In a row kept one height the last one stretches down to
 * the row's bottom; the others keep their own height.
 */
@Composable
internal fun PanelStack(modifier: Modifier, panels: List<@Composable (Modifier) -> Unit>) {
    val stretch = LocalPanelRow.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        panels.forEachIndexed { index, panel ->
            if (stretch && index == panels.lastIndex) panel(Modifier.fillMaxWidth().weight(1f))
            else CompositionLocalProvider(LocalPanelRow provides false) { panel(Modifier.fillMaxWidth()) }
        }
    }
}

/** A branch's colour, the same on every chart: by its place in the restaurant's list of branches, never by its sales. */
internal fun StatsView.branchColor(id: String): Color =
    state.branches.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { seriesColor(it) } ?: StatsOther

/** A panel that says so when there's nothing yet, instead of an empty chart. */
@Composable
internal fun StatsPanel(
    icon: ImageVector,
    title: String,
    modifier: Modifier,
    empty: Boolean = false,
    emptyText: String = "Figures show here once there are sales in these days.",
    note: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    OverviewPanel(icon, title, modifier, titleExtra = note, action = action) {
        if (empty) OverviewCompactEmpty("Nothing yet", emptyText, icon) else content()
    }
}

/** The big figure a panel leads with, and its change. */
@Composable
internal fun HeroLine(value: String, caption: String, change: String? = null, inverted: Boolean = false, big: Boolean = true) {
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (big) 30.sp else 22.sp, color = Kit.Ink, maxLines = 1)
        TrendBadge(change, inverted)
        Text(caption, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A line of small facts under a chart ("Best day  Sat 4 Oct  €2,140"). */
@Composable
internal fun FactLine(facts: List<Triple<String, String, String?>>, phone: Boolean) {
    val chunks = if (phone) facts.chunked(2) else listOf(facts)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunks.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value, detail) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (row.size == 1 && phone) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A small label and value in a row, for the end of a panel ("Refunded  -€64.00"). */
@Composable
internal fun FigureRow(label: String, value: String, valueColor: Color = Kit.Ink, strong: Boolean = false, detail: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
            detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (strong) 15.sp else 13.sp, color = valueColor, maxLines = 1)
    }
}

/** A quiet note under a panel (what a figure means, or where it comes from). */
@Composable
internal fun PanelNote(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Info, null, Modifier.size(13.dp).padding(top = 1.dp), tint = Kit.Faint)
        Text(text, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Muted)
    }
}

/** The filters that are on, each with a cross to take it off, and "Clear all". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterChips(chips: List<Pair<String, () -> Unit>>, onClear: () -> Unit) {
    if (chips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Showing only", Modifier.padding(end = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
        chips.forEach { (label, onRemove) ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Kit.GreenSoft).border(1.dp, Kit.Green.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .clickable(onClick = onRemove).padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
                Icon(Icons.Outlined.Close, "Remove $label", Modifier.size(14.dp), tint = Kit.Green)
            }
        }
        Text("Clear all", Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClear).padding(horizontal = 6.dp, vertical = 4.dp),
            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
    }
}

/** A key line beside a chart, in the look of the Inventory's lists: a soft row with the colour's dot, the name (and a detail) and the value. */
@Composable
internal fun KeyRow(color: Color, label: String, value: String, detail: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
    }
}

/** A dish's photo from the menu, else a soft tile with a plate in its menu section's colour. */
@Composable
internal fun DishThumb(v: StatsView, dish: DishFigures, size: Dp = 36.dp) {
    val url = dish.id?.let { v.state.dishPhotos[it] } ?: v.state.dishPhotos[dish.name]
    val tile: @Composable () -> Unit = { IconTile(Icons.Outlined.RestaurantMenu, sectionColor(v, dish.section), size) }
    if (url == null) tile()
    else RemoteImage(url, dish.name, Modifier.size(size).clip(RoundedCornerShape(9.dp)).background(Kit.Canvas)) { tile() }
}
