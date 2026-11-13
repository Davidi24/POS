package com.saporini.mobile_desktop.core.components

import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter

// Small charts drawn by hand so they match the rest of the app: bars with values on top, a trend line with the
// period before as a dashed line, share bars and a ring. None of them needs a chart library.

/** The colours charts cycle through, in the kit's tones. */
internal val ChartPalette = listOf(Kit.Green, Kit.Blue, Kit.Amber, Kit.Purple, Kit.Danger, Kit.Grey, Color(0xFF3E8E6A), Color(0xFFB0662E))

internal data class BarDatum(val label: String, val value: Float, val display: String = "", val highlight: Boolean = false, val tooltip: String? = null)

/**
 * Vertical bars with the value written on top and the label below. The tallest bar fills [height]; a bar with no
 * value is a thin grey line so empty hours or days still show. Bars with a [BarDatum.tooltip] show it above the bar
 * when hovered (desktop) or tapped (touch screens, tap again to hide). [tip] draws a richer tip instead (several
 * lines, on the same dark box) for the bar at that index.
 */
@Composable
internal fun BarChart(
    bars: List<BarDatum>,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    color: Color = Kit.Green,
    showValues: Boolean = true,
    tip: (@Composable ColumnScope.(index: Int) -> Unit)? = null
) {
    val most = bars.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    val labelEvery = when {
        bars.size > 40 -> 7
        bars.size > 20 -> 3
        bars.size > 14 -> 2
        else -> 1
    }
    val interactive = tip != null || bars.any { it.tooltip != null }
    var active by remember(bars.size) { mutableStateOf<Int?>(null) }
    var chartWidth by remember { mutableStateOf(0) }
    var tipSize by remember { mutableStateOf(IntSize.Zero) }
    val barHeight = { bar: BarDatum ->
        if (bar.value <= 0f) 3.dp else ((height - 40.dp) * (bar.value / most).coerceIn(0f, 1f)).coerceAtLeast(4.dp)
    }
    Box(modifier.fillMaxWidth().height(height).onSizeChanged { chartWidth = it.width }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 20) 2.dp else 6.dp), verticalAlignment = Alignment.Bottom) {
            bars.forEachIndexed { index, bar ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                if (interactive) LaunchedEffect(hovered) { if (hovered) active = index else if (active == index) active = null }
                val lit = bar.highlight || active == index
                Column(
                    Modifier.weight(1f).fillMaxHeight().then(
                        if (interactive) Modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
                            .clickable(interactionSource = interaction, indication = null) { active = if (active == index) null else index }
                        else Modifier
                    ),
                    verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showValues && bar.value > 0f && bars.size <= 16) {
                        Text(bar.display, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 9.sp,
                            color = if (bar.highlight) color else Kit.Muted, maxLines = 1, overflow = TextOverflow.Clip)
                        Spacer(Modifier.height(3.dp))
                    }
                    Box(
                        Modifier.fillMaxWidth(if (bars.size > 20) 0.9f else 0.7f)
                            .height(barHeight(bar))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(when { bar.value <= 0f -> if (active == index) Kit.Faint else Kit.Border; lit -> color; else -> color.copy(alpha = 0.5f) })
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(if (index % labelEvery == 0 || active == index) bar.label else "", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                        color = if (lit) Kit.Ink else Kit.Muted, maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center)
                }
            }
        }
        // The tooltip: centred over the bar, just above its top, kept inside the chart.
        val index = active
        val text = index?.let { bars.getOrNull(it) }?.tooltip
        if (index != null && index in bars.indices && (tip != null || text != null) && chartWidth > 0) {
            val bar = bars[index]
            val place = Modifier
                .offset {
                    val center = chartWidth * (index + 0.5f) / bars.size
                    val x = (center - tipSize.width / 2f).coerceIn(0f, (chartWidth - tipSize.width).coerceAtLeast(0).toFloat())
                    val barTop = (height - 20.dp - barHeight(bar)).roundToPx()
                    IntOffset(x.roundToInt(), (barTop - tipSize.height - 6.dp.roundToPx()).coerceAtLeast(0))
                }
                .onSizeChanged { tipSize = it }
                .clip(RoundedCornerShape(8.dp)).background(Kit.Ink)
            if (tip != null) Column(place.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { tip(index) }
            else Text(text!!, place.padding(horizontal = 10.dp, vertical = 6.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White, maxLines = 1)
        }
    }
}

/**
 * A filled trend line of [values] with an optional dashed [previous] line (the period before). Labels go under the
 * first, middle and last points.
 */
@Composable
internal fun TrendChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    previous: List<Float>? = null,
    height: Dp = 180.dp,
    color: Color = Kit.Green
) {
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height - 22.dp)) {
            val all = values + previous.orEmpty()
            val top = all.maxOrNull()?.takeIf { it > 0f } ?: 1f
            val stepCount = (values.size - 1).coerceAtLeast(1)
            fun point(index: Int, value: Float) = Offset(
                x = if (values.size == 1) size.width / 2 else size.width * index / stepCount,
                y = size.height - (size.height * 0.92f * (value / top)) - 2f
            )
            // Guide lines at a quarter, half and three quarters.
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { level ->
                val y = size.height - size.height * 0.92f * level - 2f
                drawLine(Kit.Border, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            drawLine(Kit.Border, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1.5f)
            previous?.takeIf { it.size > 1 }?.let { before ->
                val path = Path()
                before.take(values.size).forEachIndexed { i, v -> point(i, v).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
                drawPath(path, Kit.Faint, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            if (values.isNotEmpty()) {
                val line = Path()
                values.forEachIndexed { i, v -> point(i, v).let { if (i == 0) line.moveTo(it.x, it.y) else line.lineTo(it.x, it.y) } }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(point(values.lastIndex, 0f).x, size.height)
                    lineTo(point(0, 0f).x, size.height)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.02f))))
                drawPath(line, color, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (values.size <= 31) values.forEachIndexed { i, v ->
                    drawCircle(Color.White, radius = 4.5f, center = point(i, v))
                    drawCircle(color, radius = 3f, center = point(i, v))
                }
            }
        }
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                val picks = listOf(0, labels.size / 2, labels.lastIndex).distinct()
                picks.forEachIndexed { index, pick ->
                    Text(labels[pick], Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Muted,
                        textAlign = when (index) { 0 -> TextAlign.Start; picks.lastIndex -> TextAlign.End; else -> TextAlign.Center }, maxLines = 1)
                }
            }
        }
    }
}

internal data class ShareDatum(val label: String, val value: Float, val display: String, val detail: String? = null)

/** Labelled horizontal bars with the share in percent, largest first. With [color] every bar is that one hue (magnitude, not identity). */
@Composable
internal fun ShareBars(items: List<ShareDatum>, modifier: Modifier = Modifier, max: Int = 8, color: Color? = null) {
    val total = items.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items.sortedByDescending { it.value }.take(max).forEachIndexed { index, item ->
            val tone = color ?: ChartPalette[index % ChartPalette.size]
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(tone))
                    Spacer(Modifier.width(8.dp))
                    Text(item.label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.detail?.let {
                        Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(item.display, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    Text("  ${percentText(item.value / total)}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(tone.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth((item.value / total).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(tone))
                }
            }
        }
    }
}

/** A ring split by share, with the total (or any text) in the middle. [colors] gives each part its own colour (e.g. a status). */
@Composable
internal fun RingChart(values: List<Float>, centerTitle: String, centerDetail: String, modifier: Modifier = Modifier, size: Dp = 140.dp,
                       colors: List<Color>? = null) {
    val total = values.sum().takeIf { it > 0f } ?: 0f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.13f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            if (total <= 0f) {
                drawArc(Kit.Border, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            } else {
                var start = -90f
                values.forEachIndexed { index, value ->
                    val sweep = 360f * value / total
                    if (value > 0f) drawArc(colors?.getOrNull(index) ?: ChartPalette[index % ChartPalette.size], start, (sweep - 1.5f).coerceAtLeast(0.5f), false,
                        topLeft, arcSize, style = Stroke(stroke))
                    start += sweep
                }
            }
        }
        val small = size < 90.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTitle, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (small) 12.sp else 17.sp, color = Kit.Ink, maxLines = 1)
            if (!small) Text(centerDetail, fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, maxLines = 1)
        }
    }
}

/** "+12%" in green, "-3%" in red, "new" in blue; nothing for no change data. Lower is better when [inverted]. */
@Composable
internal fun TrendBadge(change: String?, inverted: Boolean = false) {
    if (change == null) return
    val up = change.startsWith("+") || change == "new"
    val down = change.startsWith("-")
    val good = if (inverted) down else up
    val color = when {
        change == "new" -> Kit.Blue
        !up && !down -> Kit.Grey
        good -> Kit.Green
        else -> Kit.Danger
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f)).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            when { up && change != "new" -> Icons.AutoMirrored.Outlined.TrendingUp; down -> Icons.AutoMirrored.Outlined.TrendingDown; else -> Icons.AutoMirrored.Outlined.TrendingFlat },
            null, Modifier.size(12.dp), tint = color
        )
        Text(change, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = color, maxLines = 1)
    }
}

/** A legend dot and its label. */
@Composable
internal fun LegendDot(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dashed) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(2) { Box(Modifier.width(5.dp).height(2.dp).background(color)) } }
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        }
        Text(label, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
    }
}

/** 0.234 → "23%". */
internal fun percentText(fraction: Float): String {
    val percent = (fraction * 100).let { if (it > 0f && it < 1f) 1 else kotlin.math.round(it).toInt() }
    return "$percent%"
}
