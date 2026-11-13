package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.theme.Inter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign

// The Statistics charts, drawn by hand in the app's look: thin marks with rounded data ends, solid hairline grids,
// a 2 dp gap between touching fills, a legend for two or more series, and a readout on hover (desktop) or tap.

/**
 * The series colours, always in this order (checked for colour-blind separation, chroma and contrast on white):
 * green, blue, orange, violet, mustard, magenta. A seventh series folds into "Other" in [StatsOther].
 */
internal val StatsSeries = listOf(
    Color(0xFF3A8A3A), Color(0xFF2B6CD0), Color(0xFFE07A2C), Color(0xFF7B58C8), Color(0xFFB3870F), Color(0xFFC2457F)
)
internal val StatsOther = Color(0xFFA3A6A0)
internal val StatsAccent = StatsSeries[0]
private val GridLine = Color(0xFFECEFEA)
private val AxisLine = Color(0xFFDDE1DA)

/** One hue from light to dark, for magnitude (heat maps): 0 → the palest, 1 → the darkest. */
internal fun heatColor(t: Float): Color = lerp(Color(0xFFEFF5EC), Color(0xFF2C6A2C), t.coerceIn(0f, 1f))

/** Each entity keeps its colour on every chart, whatever else is filtered (never by rank). */
internal fun typeColor(key: String): Color = when (key) {
    "DINE_IN" -> StatsSeries[0]; "TAKEAWAY" -> StatsSeries[2]; "DELIVERY" -> StatsSeries[3]; else -> StatsOther
}

internal fun methodColor(key: String): Color = when (key) {
    "CARD" -> StatsSeries[1]; "CASH" -> StatsSeries[0]; "CONTACTLESS" -> StatsSeries[2]; "DIGITAL_WALLET" -> StatsSeries[3]
    "GIFT_CARD" -> StatsSeries[4]; "BANK_TRANSFER" -> StatsSeries[5]; else -> StatsOther
}

internal fun sourceColor(key: String): Color = when (key) {
    "POS" -> StatsSeries[0]; "QR_TABLE" -> StatsSeries[1]; "WEB" -> StatsSeries[2]; "MOBILE" -> StatsSeries[3]
    "PHONE" -> StatsSeries[4]; "THIRD_PARTY" -> StatsSeries[5]; else -> StatsOther
}

internal fun seriesColor(index: Int): Color = StatsSeries.getOrElse(index) { StatsOther }

/** A clean axis top above [max] and its step: 0, step, 2·step, 3·step. */
private fun niceAxis(max: Float, steps: Int = 3): Pair<Float, Float> {
    if (max <= 0f) return 1f to 1f / steps
    val raw = max / steps
    val magnitude = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
    val step = listOf(1f, 2f, 2.5f, 5f, 10f).map { it * magnitude }.first { it >= raw }
    return step * ceil(max / step).coerceAtLeast(1f) to step
}

@Composable
private fun axisStyle() = TextStyle(fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted)

private fun DrawScope.label(measurer: TextMeasurer, text: String, style: TextStyle, x: Float, y: Float, alignEnd: Boolean = false, center: Boolean = false) {
    val layout = measurer.measure(text, style)
    val left = when {
        alignEnd -> x - layout.size.width
        center -> x - layout.size.width / 2f
        else -> x
    }
    drawText(layout, topLeft = Offset(left.coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)), y - layout.size.height / 2f))
}

/** Pointer position while hovering or after a tap; null when it leaves. */
private fun Modifier.pointerSpot(onSpot: (Offset?) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val position = event.changes.firstOrNull()?.position
            when (event.type) {
                PointerEventType.Exit -> onSpot(null)
                PointerEventType.Move, PointerEventType.Enter, PointerEventType.Press -> onSpot(position)
                else -> {}
            }
        }
    }
}

/** The readout: the title, then each value (strong) with a short line in its series colour and its name. */
@Composable
private fun Readout(title: String, rows: List<Triple<Color?, String, String>>, anchorX: Float, anchorY: Float, width: Int) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Column(
        Modifier
            .offset {
                val x = (anchorX - size.width / 2f).coerceIn(0f, (width - size.width).coerceAtLeast(0).toFloat())
                IntOffset(x.roundToInt(), (anchorY - size.height - 10.dp.toPx()).coerceAtLeast(0f).roundToInt())
            }
            .onSizeChanged { size = it }
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(8.dp)).background(Kit.Ink).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Color.White.copy(alpha = 0.72f), maxLines = 1)
        rows.forEach { (color, value, name) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                color?.let { Box(Modifier.width(10.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(it)) }
                Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, maxLines = 1)
                if (name.isNotEmpty()) Text(name, fontFamily = Inter(), fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A legend: a swatch (a dot, or a short line for line series) and the name, for two or more series. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChartLegend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier, lines: Boolean = false, dashedLast: Boolean = false) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, (color, name) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    lines && dashedLast && index == items.lastIndex -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(2) { Box(Modifier.width(5.dp).height(2.dp).background(color)) }
                    }
                    lines -> Box(Modifier.width(12.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(color))
                    else -> Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
                }
                Text(name, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
            }
        }
    }
}

// ---- Line ----

internal class LineSeries(val name: String, val values: List<Float>, val color: Color, val dashed: Boolean = false)

/**
 * Lines over time on one axis: the first series is filled with a wash, a [LineSeries.dashed] one is the comparison.
 * At least [height] tall, it grows into the room it's given. Hovering (or tapping) snaps a crosshair to the nearest
 * day and reads out every series there.
 */
@Composable
internal fun LineChart(
    series: List<LineSeries>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    format: (Float) -> String,
    axisFormat: (Float) -> String = format,
    titleOf: (Int) -> String = { labels.getOrElse(it) { "" } },
    // The first series is filled with a wash; off for several equal lines (one per branch).
    wash: Boolean = true
) {
    val measurer = rememberTextMeasurer()
    val style = axisStyle()
    var spot by remember { mutableStateOf<Offset?>(null) }
    var width by remember { mutableStateOf(0) }
    val count = series.maxOfOrNull { it.values.size } ?: 0
    val (top, step) = niceAxis(series.flatMap { it.values }.maxOrNull() ?: 0f)
    val axisWidth = remember(top) { measurer.measure(axisFormat(top), style).size.width + 8 }
    val gutter = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.toPx() }
    // The day under the pointer, snapped to the nearest one.
    val at = spot?.let { p ->
        val plot = width - axisWidth - gutter
        if (count == 0 || plot <= 0f || p.x < axisWidth - 4) null else ((p.x - axisWidth) / plot * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }
    Box(modifier.fillMaxWidth().heightIn(min = height).onSizeChanged { width = it.width }) {
        Canvas(Modifier.matchParentSize().pointerSpot { spot = it }) {
            val left = axisWidth.toFloat()
            val bottom = size.height - 22.dp.toPx()
            val plotWidth = size.width - left - gutter
            fun x(i: Int) = left + if (count <= 1) plotWidth / 2 else plotWidth * i / (count - 1)
            fun y(v: Float) = bottom - (bottom - 8.dp.toPx()) * (v / top)
            var level = 0f
            while (level <= top + step / 2) {
                drawLine(if (level == 0f) AxisLine else GridLine, Offset(left, y(level)), Offset(left + plotWidth, y(level)), strokeWidth = 1.dp.toPx())
                label(measurer, axisFormat(level), style, left - 6.dp.toPx(), y(level), alignEnd = true)
                level += step
            }
            series.forEachIndexed { index, s ->
                if (s.values.isEmpty()) return@forEachIndexed
                val path = Path()
                s.values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                if (index == 0 && !s.dashed && wash) {
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(x(s.values.lastIndex), bottom)
                        lineTo(x(0), bottom)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.14f), s.color.copy(alpha = 0.02f)), startY = 0f, endY = bottom))
                }
                drawPath(path, s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null))
            }
            // The x labels: at most seven, spread evenly.
            if (count > 0) {
                val every = ((count + 6) / 7).coerceAtLeast(1)
                (0 until count).filter { it % every == 0 || it == count - 1 }.distinctBy { it / every }.forEach { i ->
                    label(measurer, labels.getOrElse(i) { "" }, style, x(i), bottom + 12.dp.toPx(), center = true)
                }
            }
            // The crosshair with a ringed dot on each line.
            if (at != null) {
                drawLine(Kit.Faint, Offset(x(at), 6.dp.toPx()), Offset(x(at), bottom), strokeWidth = 1.dp.toPx())
                series.forEach { s ->
                    s.values.getOrNull(at)?.let { v ->
                        drawCircle(Color.White, 6.dp.toPx(), Offset(x(at), y(v)))
                        drawCircle(s.color, 4.dp.toPx(), Offset(x(at), y(v)))
                    }
                }
            }
        }
        if (at != null && width > 0) {
            val plotWidth = width - axisWidth - gutter
            val anchor = axisWidth + if (count <= 1) plotWidth / 2 else plotWidth * at / (count - 1)
            Readout(titleOf(at), series.map { Triple(it.color, it.values.getOrNull(at)?.let(format) ?: "–", it.name) }, anchor, 0f, width)
        }
    }
}

// ---- Columns ----

internal data class ColumnDatum(
    val label: String, val value: Float, val tooltip: String, val compare: Float? = null, val highlight: Boolean = false,
    // The column's own colour when the columns are different things (payment methods, order types): every one is then full.
    val color: Color? = null
)

/**
 * Columns from one baseline (≤ 40 dp wide, rounded caps), the tallest (or [ColumnDatum.highlight]) in full colour and the
 * rest a lighter step of it, or each in its own [ColumnDatum.color]; [ColumnDatum.compare] draws a short dark tick at the
 * comparison's height, named under the chart. At least [height] tall, it grows into the room it's given. Hover or tap a
 * column for its readout.
 */
@Composable
internal fun ColumnChart(
    columns: List<ColumnDatum>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    color: Color = StatsAccent,
    axisFormat: (Float) -> String,
    peakLabel: ((Float) -> String)? = null,
    compareName: String? = null
) {
    val measurer = rememberTextMeasurer()
    val style = axisStyle()
    val peakStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Ink)
    val keyStyle = TextStyle(fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
    var spot by remember { mutableStateOf<Offset?>(null) }
    var width by remember { mutableStateOf(0) }
    val (top, step) = niceAxis(columns.flatMap { listOfNotNull(it.value, it.compare) }.maxOrNull() ?: 0f)
    val axisWidth = remember(top) { measurer.measure(axisFormat(top), style).size.width + 8 }
    val peak = columns.indices.maxByOrNull { columns[it].value }?.takeIf { columns[it].value > 0f }
    val compareKey = compareName?.takeIf { columns.any { it.compare != null } }
    val keyRoom = if (compareKey != null) 18.dp else 0.dp
    val at = spot?.let { p ->
        val slot = (width - axisWidth).toFloat() / columns.size.coerceAtLeast(1)
        if (columns.isEmpty() || slot <= 0f || p.x < axisWidth) null else ((p.x - axisWidth) / slot).toInt().coerceIn(0, columns.size - 1)
    }
    Box(modifier.fillMaxWidth().heightIn(min = height + keyRoom).onSizeChanged { width = it.width }) {
        Canvas(Modifier.matchParentSize().pointerSpot { spot = it }) {
            val left = axisWidth.toFloat()
            val bottom = size.height - 22.dp.toPx() - keyRoom.toPx()
            val plotWidth = size.width - left
            val slot = if (columns.isEmpty()) plotWidth else plotWidth / columns.size
            val bar = (slot * 0.62f).coerceAtMost(40.dp.toPx()).coerceAtLeast(2.dp.toPx())
            fun y(v: Float) = bottom - (bottom - 14.dp.toPx()) * (v / top)
            var level = 0f
            while (level <= top + step / 2) {
                drawLine(if (level == 0f) AxisLine else GridLine, Offset(left, y(level)), Offset(size.width, y(level)), strokeWidth = 1.dp.toPx())
                label(measurer, axisFormat(level), style, left - 6.dp.toPx(), y(level), alignEnd = true)
                level += step
            }
            val every = ((columns.size + 11) / 12).coerceAtLeast(1)
            columns.forEachIndexed { i, c ->
                val center = left + slot * i + slot / 2
                val own = c.color
                val fill = when {
                    own != null -> if (at == null || at == i) own else own.copy(alpha = 0.45f)
                    c.highlight || i == at || (columns.none { it.highlight } && i == peak) -> color
                    else -> color.copy(alpha = 0.42f)
                }
                if (c.value > 0f) {
                    val rect = Rect(center - bar / 2, y(c.value), center + bar / 2, bottom)
                    val radius = CornerRadius(4.dp.toPx().coerceAtMost(bar / 2))
                    drawPath(Path().apply { addRoundRect(RoundRect(rect, radius, radius, CornerRadius.Zero, CornerRadius.Zero)) }, fill)
                } else {
                    drawLine(AxisLine, Offset(center - bar / 2, bottom - 1.dp.toPx()), Offset(center + bar / 2, bottom - 1.dp.toPx()), strokeWidth = 2.dp.toPx())
                }
                c.compare?.let { v ->
                    drawLine(Kit.Ink.copy(alpha = 0.55f), Offset(center - bar / 2 - 2.dp.toPx(), y(v)), Offset(center + bar / 2 + 2.dp.toPx(), y(v)),
                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                if (i % every == 0 || i == at) label(measurer, c.label, if (i == at) peakStyle else style, center, bottom + 12.dp.toPx(), center = true)
            }
            if (peakLabel != null && peak != null && at == null) {
                val c = columns[peak]
                label(measurer, peakLabel(c.value), peakStyle, left + slot * peak + slot / 2, y(c.value) - 8.dp.toPx(), center = true)
            }
            // What the dark ticks are, under the chart.
            if (compareKey != null) {
                val keyY = size.height - keyRoom.toPx() / 2
                drawLine(Kit.Ink.copy(alpha = 0.55f), Offset(4.dp.toPx(), keyY), Offset(16.dp.toPx(), keyY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                label(measurer, compareKey, keyStyle, 22.dp.toPx(), keyY)
            }
        }
        if (at != null && width > 0) {
            val slot = (width - axisWidth).toFloat() / columns.size
            val c = columns[at]
            Readout(c.label, listOf(Triple(c.color, c.tooltip, "")), axisWidth + slot * at + slot / 2, 0f, width)
        }
    }
}

// ---- Stacked columns ----

/**
 * Columns split by series ([values] per column, one per series), stacked with a 2 dp gap between parts. Put a
 * [ChartLegend] of the series above it. At least [height] tall, it grows into the room it's given. Hover or tap a
 * column for every part and the total.
 */
@Composable
internal fun StackedColumnChart(
    labels: List<String>,
    series: List<Pair<String, Color>>,
    values: List<List<Float>>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    format: (Float) -> String,
    axisFormat: (Float) -> String = format,
    titleOf: (Int) -> String = { labels.getOrElse(it) { "" } }
) {
    val measurer = rememberTextMeasurer()
    val style = axisStyle()
    var spot by remember { mutableStateOf<Offset?>(null) }
    var width by remember { mutableStateOf(0) }
    val (top, step) = niceAxis(values.maxOfOrNull { it.sum() } ?: 0f)
    val axisWidth = remember(top) { measurer.measure(axisFormat(top), style).size.width + 8 }
    val at = spot?.let { p ->
        val slot = (width - axisWidth).toFloat() / values.size.coerceAtLeast(1)
        if (values.isEmpty() || slot <= 0f || p.x < axisWidth) null else ((p.x - axisWidth) / slot).toInt().coerceIn(0, values.size - 1)
    }
    Box(modifier.fillMaxWidth().heightIn(min = height).onSizeChanged { width = it.width }) {
        Canvas(Modifier.matchParentSize().pointerSpot { spot = it }) {
            val left = axisWidth.toFloat()
            val bottom = size.height - 22.dp.toPx()
            val plotWidth = size.width - left
            val slot = if (values.isEmpty()) plotWidth else plotWidth / values.size
            val bar = (slot * 0.62f).coerceAtMost(40.dp.toPx()).coerceAtLeast(2.dp.toPx())
            fun h(v: Float) = (bottom - 8.dp.toPx()) * (v / top)
            var level = 0f
            while (level <= top + step / 2) {
                val y = bottom - h(level)
                drawLine(if (level == 0f) AxisLine else GridLine, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                label(measurer, axisFormat(level), style, left - 6.dp.toPx(), y, alignEnd = true)
                level += step
            }
            val gap = 2.dp.toPx()
            val every = ((values.size + 11) / 12).coerceAtLeast(1)
            values.forEachIndexed { i, parts ->
                val center = left + slot * i + slot / 2
                var base = bottom
                val lastVisible = parts.indexOfLast { it > 0f }
                parts.forEachIndexed { s, v ->
                    if (v <= 0f) return@forEachIndexed
                    val partHeight = h(v)
                    val topY = base - partHeight
                    val drawnTop = if (s == lastVisible) topY else topY + gap.coerceAtMost(partHeight / 2)
                    val rect = androidx.compose.ui.geometry.Rect(center - bar / 2, drawnTop, center + bar / 2, base)
                    val radius = if (s == lastVisible) CornerRadius(4.dp.toPx().coerceAtMost(bar / 2)) else CornerRadius.Zero
                    val color = series.getOrNull(s)?.second ?: StatsOther
                    drawPath(Path().apply { addRoundRect(RoundRect(rect, radius, radius, CornerRadius.Zero, CornerRadius.Zero)) },
                        if (at == null || at == i) color else color.copy(alpha = 0.45f))
                    base = topY
                }
                if (i % every == 0 || i == at) label(measurer, labels.getOrElse(i) { "" }, style, center, bottom + 12.dp.toPx(), center = true)
            }
        }
        if (at != null && width > 0) {
            val slot = (width - axisWidth).toFloat() / values.size
            val parts = values[at]
            Readout(titleOf(at) + " · " + format(parts.sum()), series.mapIndexedNotNull { s, (name, color) ->
                parts.getOrNull(s)?.takeIf { it > 0f }?.let { Triple(color, format(it), name) }
            }, axisWidth + slot * at + slot / 2, 0f, width)
        }
    }
}

// ---- Heat map ----

/**
 * A grid of cells (rows × columns) in one hue, paler for less and darker for more, with a scale. Hover or tap a cell
 * for its value. Columns with nothing in any row are left out at both ends.
 */
@Composable
internal fun HeatMap(
    rows: List<String>,
    columns: List<String>,
    values: List<List<Float>>,
    modifier: Modifier = Modifier,
    cellHeight: Dp = 24.dp,
    format: (Float) -> String,
    titleOf: (Int, Int) -> String
) {
    val first = columns.indices.firstOrNull { c -> values.any { (it.getOrNull(c) ?: 0f) > 0f } } ?: 0
    val last = columns.indices.lastOrNull { c -> values.any { (it.getOrNull(c) ?: 0f) > 0f } } ?: (columns.size - 1)
    val shown = (first..last).toList()
    val max = values.flatMap { row -> shown.map { row.getOrNull(it) ?: 0f } }.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val measurer = rememberTextMeasurer()
    val style = axisStyle()
    var spot by remember { mutableStateOf<Offset?>(null) }
    var width by remember { mutableStateOf(0) }
    val labelWidth = remember(rows) { (rows.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0) + 10 }
    val total = cellHeight * rows.size + 22.dp
    val cellPx = with(androidx.compose.ui.platform.LocalDensity.current) { cellHeight.toPx() }
    val at = spot?.let { p ->
        val cellW = (width - labelWidth).toFloat() / shown.size.coerceAtLeast(1)
        val c = ((p.x - labelWidth) / cellW).toInt()
        val r = (p.y / cellPx).toInt()
        if (cellW > 0f && p.x >= labelWidth && c in shown.indices && r in rows.indices) r to c else null
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(total).onSizeChanged { width = it.width }) {
            Canvas(Modifier.fillMaxWidth().height(total).pointerSpot { spot = it }) {
                val left = labelWidth.toFloat()
                val cellW = (size.width - left) / shown.size.coerceAtLeast(1)
                val cellH = cellHeight.toPx()
                val gap = 2.dp.toPx()
                rows.forEachIndexed { r, name ->
                    label(measurer, name, style, 0f, r * cellH + cellH / 2)
                    shown.forEachIndexed { c, column ->
                        val v = values.getOrNull(r)?.getOrNull(column) ?: 0f
                        val color = if (v <= 0f) Color(0xFFF6F7F5) else heatColor(v / max)
                        drawRoundRect(color, Offset(left + c * cellW + gap / 2, r * cellH + gap / 2), Size(cellW - gap, cellH - gap),
                            CornerRadius(3.dp.toPx()))
                        if (at == r to c) drawRoundRect(Kit.Ink, Offset(left + c * cellW + gap / 2, r * cellH + gap / 2), Size(cellW - gap, cellH - gap),
                            CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    }
                }
                val every = ((shown.size + 11) / 12).coerceAtLeast(1)
                shown.forEachIndexed { c, column ->
                    if (c % every == 0) label(measurer, columns[column], style, left + c * cellW + cellW / 2, rows.size * cellH + 12.dp.toPx(), center = true)
                }
            }
            if (at != null && width > 0) {
                val cellW = (width - labelWidth).toFloat() / shown.size.coerceAtLeast(1)
                val column = shown[at.second]
                val v = values.getOrNull(at.first)?.getOrNull(column) ?: 0f
                Readout(titleOf(at.first, column), listOf(Triple(heatColor(v / max), format(v), "")), labelWidth + at.second * cellW + cellW / 2,
                    cellPx * at.first, width)
            }
        }
        // The scale: fewer → more.
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Fewer", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted)
            listOf(0.08f, 0.3f, 0.52f, 0.76f, 1f).forEach { Box(Modifier.size(14.dp, 10.dp).clip(RoundedCornerShape(2.dp)).background(heatColor(it))) }
            Text("More", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted)
        }
    }
}

// ---- Small pieces ----

/** A 12-point line in the quiet colour, the last point in the accent: how a figure moved over the period. */
@Composable
internal fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, color: Color = Kit.Faint, accent: Color = StatsAccent) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val points = if (values.size > 12) (0 until 12).map { i -> values[(i * (values.size - 1)) / 11] } else values
        val max = points.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val min = points.minOrNull() ?: 0f
        val span = (max - min).takeIf { it > 0f } ?: max
        fun at(i: Int) = Offset(size.width * i / (points.size - 1), size.height - 3.dp.toPx() - (size.height - 6.dp.toPx()) * ((points[i] - min) / span))
        val path = Path()
        points.indices.forEach { i -> at(i).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color.White, 4.dp.toPx(), at(points.lastIndex))
        drawCircle(accent, 2.5.dp.toPx(), at(points.lastIndex))
    }
}

internal data class Part(val key: String, val label: String, val value: Float, val color: Color, val display: String, val detail: String? = null)

/** One bar split into its parts (2 dp apart), then a line per part: its colour, name, detail, value and share. */
@Composable
internal fun SplitBar(parts: List<Part>, modifier: Modifier = Modifier, barHeight: Dp = 14.dp, rows: Boolean = true) {
    val total = parts.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(50)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.filter { it.value > 0f }.forEach { part ->
                Box(Modifier.weight(part.value / total).fillMaxHeight().background(part.color))
            }
            if (parts.all { it.value <= 0f }) Box(Modifier.weight(1f).fillMaxHeight().background(Kit.Tint))
        }
        // A soft row per part, as in the Inventory's lists: the colour's dot, the name and detail, the value and the share.
        if (rows) parts.forEach { part ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(part.color))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(part.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    part.detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Text(part.display, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                Text(com.saporini.mobile_desktop.core.components.percentText(part.value / total), Modifier.width(44.dp), fontFamily = Inter(), fontSize = 11.sp,
                    color = Kit.Muted, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}

/** Steps that narrow (booked → arrived): a bar per step against the first, with its share of the first. */
@Composable
internal fun Funnel(steps: List<Part>, modifier: Modifier = Modifier) {
    val first = steps.firstOrNull()?.value?.takeIf { it > 0f } ?: 1f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEach { step ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(step.label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    Text(step.display, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                    Text(com.saporini.mobile_desktop.core.components.percentText(step.value / first), Modifier.width(44.dp), fontFamily = Inter(), fontSize = 11.sp,
                        color = Kit.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(step.color.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth((step.value / first).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(step.color))
                }
            }
        }
    }
}

/** A share against a limit: the fill in [color] on a lighter track of the same hue. */
@Composable
internal fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

internal data class Dot(val label: String, val x: Float, val y: Float, val color: Color, val detail: String)

/**
 * Dots on two measures with the middle of each drawn across, so the plot falls into four named corners (e.g. sold a
 * lot and earn well). Up to ten dots are all named, more than that the six furthest right, each only where its name
 * covers no other name or dot. At least [height] tall, it grows into the room it's given. Hover or tap any dot for
 * its figures.
 */
@Composable
internal fun QuadrantChart(
    dots: List<Dot>,
    xName: String,
    yName: String,
    corners: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    xFormat: (Float) -> String,
    yFormat: (Float) -> String
) {
    val measurer = rememberTextMeasurer()
    val style = axisStyle()
    val nameStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Ink)
    val cornerStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp, color = Kit.Faint)
    var spot by remember { mutableStateOf<Offset?>(null) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val xMax = (dots.maxOfOrNull { it.x } ?: 1f) * 1.08f
    val yMin = ((dots.minOfOrNull { it.y } ?: 0f) - 0.04f).coerceAtLeast(0f)
    val yMax = ((dots.maxOfOrNull { it.y } ?: 1f) + 0.04f).coerceAtMost(1f)
    val xMid = dots.map { it.x }.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
    val yMid = dots.map { it.y }.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
    val named = (if (dots.size <= 10) dots else dots.sortedByDescending { it.x }.take(6)).map { it.label }.toSet()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val leftPx = with(density) { 40.dp.toPx() }
    val rightGap = with(density) { 8.dp.toPx() }
    val topPx = with(density) { 10.dp.toPx() }
    val bottomGap = with(density) { 40.dp.toPx() }
    fun dotAt(d: Dot) = Offset(
        leftPx + (box.width - rightGap - leftPx) * (d.x / xMax),
        (box.height - bottomGap) - (box.height - bottomGap - topPx) * ((d.y - yMin) / (yMax - yMin).coerceAtLeast(0.01f))
    )
    val near = spot?.let { p ->
        dots.indices.minByOrNull { (dotAt(dots[it]) - p).getDistance() }?.takeIf { (dotAt(dots[it]) - p).getDistance() < with(density) { 24.dp.toPx() } }
    }
    Box(modifier.fillMaxWidth().heightIn(min = height).onSizeChanged { box = it }) {
        Canvas(Modifier.matchParentSize().pointerSpot { spot = it }) {
            val left = 40.dp.toPx()
            val bottom = size.height - 40.dp.toPx()
            val right = size.width - 8.dp.toPx()
            fun x(v: Float) = left + (right - left) * (v / xMax)
            fun y(v: Float) = bottom - (bottom - 10.dp.toPx()) * ((v - yMin) / (yMax - yMin).coerceAtLeast(0.01f))
            drawLine(AxisLine, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
            drawLine(AxisLine, Offset(left, 10.dp.toPx()), Offset(left, bottom), strokeWidth = 1.dp.toPx())
            drawLine(Kit.Faint.copy(alpha = 0.6f), Offset(x(xMid), 10.dp.toPx()), Offset(x(xMid), bottom), strokeWidth = 1.dp.toPx())
            drawLine(Kit.Faint.copy(alpha = 0.6f), Offset(left, y(yMid)), Offset(right, y(yMid)), strokeWidth = 1.dp.toPx())
            listOf(yMin, (yMin + yMax) / 2, yMax).forEach { v -> label(measurer, yFormat(v), style, left - 6.dp.toPx(), y(v), alignEnd = true) }
            listOf(0f, xMax / 2, xMax).forEach { v -> label(measurer, xFormat(v), style, x(v), bottom + 12.dp.toPx(), center = true) }
            // The axes' names under the plot.
            label(measurer, "↑ $yName", style, 4.dp.toPx(), size.height - 8.dp.toPx())
            label(measurer, "$xName →", style, size.width, size.height - 8.dp.toPx(), alignEnd = true)
            // Names may not cover the corners' names or any dot.
            val taken = mutableListOf<Rect>()
            fun corner(text: String, at: Offset, end: Boolean) {
                val layout = measurer.measure(text.uppercase(), cornerStyle)
                val topLeft = Offset(if (end) at.x - layout.size.width else at.x, at.y - layout.size.height / 2f)
                drawText(layout, topLeft = topLeft)
                taken += Rect(topLeft, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
            }
            corners.getOrNull(0)?.let { corner(it, Offset(left + 8.dp.toPx(), 18.dp.toPx()), false) }
            corners.getOrNull(1)?.let { corner(it, Offset(right - 4.dp.toPx(), 18.dp.toPx()), true) }
            corners.getOrNull(2)?.let { corner(it, Offset(left + 8.dp.toPx(), bottom - 10.dp.toPx()), false) }
            corners.getOrNull(3)?.let { corner(it, Offset(right - 4.dp.toPx(), bottom - 10.dp.toPx()), true) }
            val ring = 6.dp.toPx()
            dots.forEachIndexed { i, d ->
                val c = Offset(x(d.x), y(d.y))
                drawCircle(Color.White, (if (i == near) 8 else 6).dp.toPx(), c)
                drawCircle(d.color, (if (i == near) 6f else 4.5f).dp.toPx(), c)
                taken += Rect(c.x - ring, c.y - ring, c.x + ring, c.y + ring)
            }
            val gap = 8.dp.toPx()
            dots.sortedByDescending { it.x }.filter { it.label in named }.forEach { d ->
                val c = Offset(x(d.x), y(d.y))
                val text = measurer.measure(d.label, nameStyle)
                val w = text.size.width.toFloat()
                val h = text.size.height.toFloat()
                listOf(Offset(c.x + gap, c.y - h + 2.dp.toPx()), Offset(c.x + gap, c.y - 2.dp.toPx()), Offset(c.x - gap - w, c.y - h + 2.dp.toPx()),
                    Offset(c.x - gap - w, c.y - 2.dp.toPx()))
                    .map { Rect(it, Size(w, h)) }
                    .firstOrNull { r -> r.left >= left && r.right <= size.width && r.top >= 0f && r.bottom <= bottom && taken.none { it.overlaps(r) } }
                    ?.let { r ->
                        drawText(text, topLeft = r.topLeft)
                        taken += r
                    }
            }
        }
        if (near != null && box.width > 0) {
            val d = dots[near]
            val c = dotAt(d)
            Readout(d.label, listOf(Triple(d.color, d.detail, "")), c.x, c.y, box.width)
        }
    }
}

/**
 * Items ranked, in the look of the Inventory's lists: a soft row each with its rank, an optional [leading] picture (a
 * dish photo, a face, an icon), the name with a detail under it, the value, and a thin bar under the name for its size
 * against the biggest. One hue when it's magnitude, or [colors] (one per item) when the items are different things with
 * a colour of their own (payment methods, branches).
 */
@Composable
internal fun RankBars(
    items: List<Triple<String, Float, String>>,
    modifier: Modifier = Modifier,
    color: Color = StatsAccent,
    details: List<String?> = emptyList(),
    max: Int = 8,
    colors: List<Color>? = null,
    leading: (@Composable (Int) -> Unit)? = null
) {
    val top = items.maxOfOrNull { it.second }?.takeIf { it > 0f } ?: 1f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(max).forEachIndexed { index, (name, value, display) ->
            val barColor = colors?.getOrNull(index) ?: color
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("${index + 1}", Modifier.width(16.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Faint,
                    textAlign = TextAlign.Center)
                leading?.invoke(index)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        Text(display, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    }
                    details.getOrNull(index)?.let {
                        Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(barColor.copy(alpha = 0.14f))) {
                        Box(Modifier.fillMaxWidth((value / top).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(barColor))
                    }
                }
            }
        }
    }
}

/**
 * Now against the comparison per measure: a dark dot (now) and a hollow one (before) on a line, with both figures. With
 * [beforeName], a key on top names the two dots.
 */
@Composable
internal fun Dumbbells(items: List<Triple<String, Pair<Float, Float?>, Pair<String, String?>>>, modifier: Modifier = Modifier, beforeName: String? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (beforeName != null && items.any { it.second.second != null }) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DotKey(true, "This period")
                DotKey(false, beforeName)
            }
        }
        items.forEach { (name, values, texts) ->
            val (now, before) = values
            val top = maxOf(now, before ?: 0f).takeIf { it > 0f } ?: 1f
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
                    Text(texts.first, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                    texts.second?.let {
                        Spacer(Modifier.width(8.dp))
                        Text("was $it", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                    val y = size.height / 2
                    drawLine(GridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    val xNow = size.width * (now / top)
                    before?.let { b ->
                        val xBefore = size.width * (b / top)
                        drawLine(if (now >= b) StatsAccent.copy(alpha = 0.5f) else Kit.Danger.copy(alpha = 0.45f), Offset(xBefore, y), Offset(xNow, y),
                            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                        drawCircle(Color.White, 5.dp.toPx(), Offset(xBefore, y))
                        drawCircle(Kit.Faint, 5.dp.toPx(), Offset(xBefore, y), style = Stroke(2.dp.toPx()))
                    }
                    drawCircle(Color.White, 6.dp.toPx(), Offset(xNow, y))
                    drawCircle(Kit.Ink, 4.5.dp.toPx(), Offset(xNow, y))
                }
            }
        }
    }
}

@Composable
private fun DotKey(filled: Boolean, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(10.dp)) {
            if (filled) drawCircle(Kit.Ink, size.minDimension / 2)
            else drawCircle(Kit.Faint, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
        Text(name, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
    }
}

/** A ring of a few parts (part-to-whole at a glance, at most six) with a figure in the middle. */
@Composable
internal fun Donut(parts: List<Part>, centerTitle: String, centerDetail: String, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val total = parts.sumOf { it.value.toDouble() }.toFloat()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = this.size.minDimension * 0.14f
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            if (total <= 0f) {
                drawArc(Kit.Border, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            parts.forEach { part ->
                val sweep = 360f * part.value / total
                if (part.value > 0f) drawArc(part.color, start, (sweep - 2f).coerceAtLeast(0.6f), false, topLeft, arc, style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTitle, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (size < 100.dp) 12.sp else 17.sp, color = Kit.Ink, maxLines = 1)
            if (size >= 100.dp) Text(centerDetail, fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, maxLines = 1)
        }
    }
}

internal enum class StepKind { TOTAL, UP, DOWN }

internal data class Step(val label: String, val value: Float, val kind: StepKind, val detail: String = "")

/**
 * A bridge from one total to another: [StepKind.TOTAL] bars stand on the baseline, [StepKind.UP] steps add (green) and
 * [StepKind.DOWN] steps take away (red) from where the last one ended, with a hairline joining them. Each step's value
 * sits on top; hover or tap a step for its detail.
 */
@Composable
internal fun Waterfall(steps: List<Step>, modifier: Modifier = Modifier, height: Dp = 250.dp, format: (Float) -> String, axisFormat: (Float) -> String) {
    val measurer = rememberTextMeasurer()
    val style = axisStyle()
    val valueStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Kit.Ink)
    var spot by remember { mutableStateOf<Offset?>(null) }
    var width by remember { mutableStateOf(0) }
    // Where each bar starts and ends.
    val spans = remember(steps) {
        var level = 0f
        steps.map { step ->
            when (step.kind) {
                StepKind.TOTAL -> { level = step.value; 0f to step.value }
                StepKind.UP -> { val from = level; level += step.value; from to level }
                StepKind.DOWN -> { val from = level; level -= step.value; level to from }
            }
        }
    }
    val (top, gridStep) = niceAxis(spans.maxOfOrNull { it.second } ?: 0f)
    val axisWidth = remember(top) { measurer.measure(axisFormat(top), style).size.width + 8 }
    val at = spot?.let { p ->
        val slot = (width - axisWidth).toFloat() / steps.size.coerceAtLeast(1)
        if (steps.isEmpty() || slot <= 0f || p.x < axisWidth) null else ((p.x - axisWidth) / slot).toInt().coerceIn(0, steps.size - 1)
    }
    Box(modifier.fillMaxWidth().height(height).onSizeChanged { width = it.width }) {
        Canvas(Modifier.fillMaxWidth().height(height).pointerSpot { spot = it }) {
            val left = axisWidth.toFloat()
            val bottom = size.height - 34.dp.toPx()
            val slot = (size.width - left) / steps.size.coerceAtLeast(1)
            val bar = (slot * 0.56f).coerceAtMost(56.dp.toPx())
            fun y(v: Float) = bottom - (bottom - 22.dp.toPx()) * (v / top)
            var level = 0f
            while (level <= top + gridStep / 2) {
                drawLine(if (level == 0f) AxisLine else GridLine, Offset(left, y(level)), Offset(size.width, y(level)), strokeWidth = 1.dp.toPx())
                label(measurer, axisFormat(level), style, left - 6.dp.toPx(), y(level), alignEnd = true)
                level += gridStep
            }
            steps.forEachIndexed { i, step ->
                val (from, to) = spans[i]
                val center = left + slot * i + slot / 2
                val color = when (step.kind) {
                    StepKind.TOTAL -> if (i == steps.lastIndex) Kit.Ink.copy(alpha = 0.82f) else StatsAccent
                    StepKind.UP -> Color(0xFF3A8A3A).copy(alpha = 0.75f)
                    StepKind.DOWN -> Kit.Danger.copy(alpha = 0.78f)
                }
                val rect = androidx.compose.ui.geometry.Rect(center - bar / 2, y(to), center + bar / 2, y(from).coerceAtLeast(y(to) + 2.dp.toPx()))
                val radius = CornerRadius(4.dp.toPx())
                drawPath(Path().apply { addRoundRect(RoundRect(rect, radius, radius, if (step.kind == StepKind.TOTAL) CornerRadius.Zero else radius,
                    if (step.kind == StepKind.TOTAL) CornerRadius.Zero else radius)) }, if (at == null || at == i) color else color.copy(alpha = color.alpha * 0.5f))
                // The hairline to the next step, at the level this one ends on.
                if (i < steps.lastIndex) {
                    val end = if (step.kind == StepKind.DOWN) from else to
                    drawLine(Kit.Faint, Offset(center + bar / 2, y(end)), Offset(center + slot - bar / 2, y(end)), strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                }
                val sign = when (step.kind) { StepKind.UP -> "+"; StepKind.DOWN -> "−"; StepKind.TOTAL -> "" }
                label(measurer, sign + axisFormat(step.value), valueStyle, center, y(to) - 9.dp.toPx(), center = true)
                label(measurer, step.label, style, center, bottom + 10.dp.toPx(), center = true)
            }
        }
        if (at != null && width > 0) {
            val slot = (width - axisWidth).toFloat() / steps.size
            val step = steps[at]
            Readout(step.label, listOf(Triple(null, format(step.value), step.detail)), axisWidth + slot * at + slot / 2, 0f, width)
        }
    }
}

/** Keeps a figure readable in axis ticks: 1,250 → "1.3k". */
internal fun compactNumber(value: Float): String {
    val v = abs(value)
    return when {
        v >= 1_000_000 -> "${(value / 100_000).roundToInt() / 10f}M".replace(".0M", "M")
        v >= 1_000 -> "${(value / 100).roundToInt() / 10f}k".replace(".0k", "k")
        v == floor(v) -> value.toInt().toString()
        else -> "${(value * 10).roundToInt() / 10f}"
    }
}
