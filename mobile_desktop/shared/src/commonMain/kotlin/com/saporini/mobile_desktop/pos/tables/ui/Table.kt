package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class TableShape {
    Circle,
    Square
}

enum class TableVisualState {
    Free,
    Occupied,
    Reserved,
    // A booking's hold is running out: "Hold ends in N min".
    HoldEnding,
    Ordered,
    BillPending,
    // The guests have paid and are waiting to leave (user, 2026-11-01).
    Paid,
    Unavailable
}

private data class TableColors(
    val surface: Color,
    val border: Color,
    val labelBackground: Color,
    val content: Color
)

@Composable
fun Table(
    shape: TableShape,
    seatCount: Int,
    label: String,
    modifier: Modifier = Modifier,
    state: TableVisualState = TableVisualState.Free,
    orderLabel: String? = null,
    statusText: String? = null,
    servedItems: Int = 0,
    totalItems: Int = 0,
    scale: Float = 1f,
    nextBookingClash: Boolean = false,
    // A free table whose booking is coming: a yellow ring, so nobody seats walk-ins there (user, 2026-11-01).
    bookingComing: Boolean = false,
    // The guests at it (user, 2026-11-01): chairs nobody sits on are green, and more guests than seats get extra chairs.
    guests: Int? = null
) {
    val servedProgress = if (totalItems > 0) {
        servedItems.coerceIn(0, totalItems).toFloat() / totalItems
    } else 0f
    val seated = guests != null && guests > 0 && state in SeatedStates
    val chairs = if (seated) maxOf(seatCount, guests!!) else seatCount
    val takenChairs = if (seated) guests!! else chairs
    // An occupied table's ring only means more guests are coming.
    val ringAllowed = state != TableVisualState.Occupied || totalItems > 0
    val body: @Composable (TableVisualState, Color?, Modifier, Boolean, Boolean) -> Unit = { bodyState, forcedColor, bodyModifier, showRing, showChairs ->
        when (shape) {
            TableShape.Circle -> CircleTable(
                seatCount = seatCount,
                label = label,
                state = bodyState,
                servedProgress = servedProgress,
                modifier = bodyModifier,
                scale = scale,
                showRing = showRing && ringAllowed,
                forcedColor = forcedColor,
                chairs = if (showChairs) chairs else 0,
                takenChairs = takenChairs
            )

            TableShape.Square -> SquareTable(
                seatCount = seatCount,
                label = label,
                state = bodyState,
                orderLabel = orderLabel,
                statusText = statusText,
                servedProgress = servedProgress,
                modifier = bodyModifier,
                scale = scale,
                showRing = showRing && ringAllowed,
                forcedColor = forcedColor,
                chairs = if (showChairs) chairs else 0,
                takenChairs = takenChairs
            )
        }
    }
    if (!nextBookingClash) {
        if (bookingComing && state == TableVisualState.Free) {
            Box(modifier) {
                body(state, null, Modifier, true, true)
                TableRingOf(shape, seatCount, TableVisualState.HoldEnding, 1f, scale)
            }
            return
        }
        body(state, null, modifier, true, true)
        return
    }
    // Guests still here and the next booking is due (user, 2026-11-01): half the colour it has now, half the booking's
    // purple (a stronger one, so it stands apart from Ordered's blue), and the whole table pulses. Its ring stays whole on top.
    val pulse by rememberInfiniteTransition(label = "next-booking").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "next-booking-pulse"
    )
    Box(
        modifier.graphicsLayer {
            scaleX = 1f + 0.05f * pulse
            scaleY = 1f + 0.05f * pulse
            alpha = 1f - 0.35f * pulse
        }
    ) {
        // The chairs keep the table's own colours: only the table itself is half purple (user, 2026-11-01).
        body(state, null, Modifier, false, true)
        body(TableVisualState.Reserved, TableNextBookingPurple, Modifier.drawWithContent { clipRect(left = size.width / 2) { this@drawWithContent.drawContent() } }, false, false)
        if (ringAllowed) TableRingOf(shape, seatCount, state, servedProgress, scale)
    }
}

private val SeatedStates = setOf(TableVisualState.Occupied, TableVisualState.Ordered, TableVisualState.BillPending, TableVisualState.Paid)

// A chair nobody sits on: the free table's green.
private fun chairColor(index: Int, takenChairs: Int, colors: TableColors): Color =
    if (index < takenChairs) colors.surface else tableStatusColor(TableVisualState.Free)

// Just outside the table's edge, in the room before the chairs.
@Composable
private fun BoxScope.TableRingOf(shape: TableShape, seatCount: Int, state: TableVisualState, servedProgress: Float, scale: Float) {
    val ring = tableRing(state, servedProgress) ?: return
    val width = (TableRingWidth * scale).coerceAtLeast(TableRingMinWidth)
    when (shape) {
        TableShape.Circle -> Canvas(Modifier.align(Alignment.Center).size(86.dp * scale + (2.dp * scale + width) * 2)) {
            drawCircleRing(ring, width.toPx())
        }
        TableShape.Square -> {
            val bodyWidth = (if (seatCount > 4) 248.dp else 128.dp) * scale
            val bodyHeight = (if (seatCount > 4) 112.dp else 128.dp) * scale
            val gap = 3.dp * scale
            Canvas(Modifier.align(Alignment.Center).size(bodyWidth + (gap + width) * 2, bodyHeight + (gap + width) * 2)) {
                drawSquareRing(ring, (14.dp * scale + gap + width).toPx(), width.toPx())
            }
        }
    }
}

// Table status colors from the Saporine Italiano palette ("Table & Order Colors"); Free keeps the darker brand green.
internal fun tableStatusColor(state: TableVisualState): Color = when (state) {
    TableVisualState.Free -> Color(0xFF147A25)
    // Same burnt orange as the Orders "in preparation" badge.
    TableVisualState.Occupied -> Color(0xFFD15F00)
    TableVisualState.Reserved -> Color(0xFF8B5CF6)
    TableVisualState.HoldEnding -> Color(0xFFEAB308)
    TableVisualState.Ordered -> Color(0xFF3B82F6)
    // A deep crimson, so it never reads as Occupied's orange (user, 2026-11-01).
    TableVisualState.BillPending -> Color(0xFFBE123C)
    // A lighter green than Free: done, about to be free.
    TableVisualState.Paid -> Color(0xFF5FB86A)
    TableVisualState.Unavailable -> Color(0xFF6B7280)
}

internal val TableNextBookingPurple = Color(0xFF6D28D9)

private fun tableColors(state: TableVisualState, servedProgress: Float = 0f, forcedColor: Color? = null): TableColors {
    // A booking running out is still a reserved table; its yellow is the ring around it.
    val color = forcedColor ?: tableStatusColor(if (state == TableVisualState.HoldEnding) TableVisualState.Reserved else state)
    return TableColors(
        surface = color,
        border = color,
        labelBackground = color.copy(alpha = .14f).compositeOver(Color.White),
        content = color
    )
}

@Composable
private fun SquareTable(
    seatCount: Int,
    label: String,
    state: TableVisualState,
    orderLabel: String?,
    statusText: String?,
    servedProgress: Float,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    showRing: Boolean = true,
    forcedColor: Color? = null,
    chairs: Int = seatCount,
    takenChairs: Int = chairs
) {
    val showDetails = state != TableVisualState.Free && (orderLabel != null || statusText != null)
    val colors = tableColors(state, servedProgress, forcedColor)
    val bodyWidth = (if (seatCount > 4) 248.dp else 128.dp) * scale
    val bodyHeight = (if (seatCount > 4) 112.dp else 128.dp) * scale
    val totalWidth = bodyWidth + 48.dp * scale
    val totalHeight = bodyHeight + 40.dp * scale
    val distribution = if (chairs <= 0) SquareSeatDistribution()
        else squareSeatDistribution(seatCount).withExtraChairs(chairs - seatCount, sideRoom = if (seatCount > 4) 1 else 2)
    // Clockwise from the top: top row, right side, bottom row, left side.
    val colorOf: (Int) -> Color = { chairColor(it, takenChairs, colors) }

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(totalHeight)
    ) {
        SeatRow(
            count = distribution.top,
            firstIndex = 0,
            colorOf = colorOf,
            scale = scale,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(bodyWidth)
        )

        SeatRow(
            count = distribution.bottom,
            firstIndex = distribution.top + distribution.right,
            colorOf = colorOf,
            scale = scale,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(bodyWidth)
        )

        SeatColumn(
            count = distribution.left,
            firstIndex = distribution.top + distribution.right + distribution.bottom,
            colorOf = colorOf,
            scale = scale,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        SeatColumn(
            count = distribution.right,
            firstIndex = distribution.top,
            colorOf = colorOf,
            scale = scale,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(bodyWidth)
                .height(bodyHeight)
                .clip(RoundedCornerShape(14.dp * scale))
                .background(colors.surface)
                .border(
                    width = tableLine(scale),
                    color = colors.border,
                    shape = RoundedCornerShape(14.dp * scale)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (showDetails) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 9.dp * scale, top = 8.dp * scale)
                        .size(34.dp * scale)
                        .clip(CircleShape)
                        .background(colors.labelBackground)
                        .border(tableLine(scale), colors.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (13 * scale).sp,
                        letterSpacing = 0.sp,
                        color = colors.content
                    )
                }
                orderLabel?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 10.dp * scale, top = 10.dp * scale),
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (14 * scale).sp,
                        letterSpacing = 0.sp,
                        color = colors.content
                    )
                }

                statusText?.let {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(13.dp * scale))
                            .background(colors.labelBackground)
                            .padding(horizontal = 12.dp * scale, vertical = 6.dp * scale),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp * scale),
                            tint = colors.content
                        )
                        Text(
                            text = it,
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (14 * scale).sp,
                            letterSpacing = 0.sp,
                            color = colors.content
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp * scale)
                        .clip(CircleShape)
                        .background(colors.labelBackground)
                        .border(1.dp, colors.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (22 * scale).sp,
                        letterSpacing = 0.sp,
                        color = colors.content
                    )
                }
            }
        }
        if (showRing) TableRingOf(TableShape.Square, seatCount, state, servedProgress, scale)
    }
}

@Composable
private fun CircleTable(
    seatCount: Int,
    label: String,
    state: TableVisualState,
    servedProgress: Float,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    showRing: Boolean = true,
    forcedColor: Color? = null,
    chairs: Int = seatCount,
    takenChairs: Int = chairs
) {
    val colors = tableColors(state, servedProgress, forcedColor)
    val totalSize = 156.dp * scale
    val tableSize = 86.dp * scale
    val chairWidth = 34.dp * scale
    val chairHeight = 12.dp * scale
    val center = totalSize / 2
    val radius = 58.dp * scale

    Box(
        modifier = modifier.size(totalSize)
    ) {
        repeat(chairs.coerceAtLeast(0)) { index ->
            val angle = (-PI / 2.0) + (2.0 * PI * index / chairs.coerceAtLeast(1))
            val x = center + radius * cos(angle).toFloat() - chairWidth / 2
            val y = center + radius * sin(angle).toFloat() - chairHeight / 2

            Seat(
                width = chairWidth,
                height = chairHeight,
                color = chairColor(index, takenChairs, colors),
                borderColor = chairColor(index, takenChairs, colors),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = x, y = y)
                    .graphicsLayer(rotationZ = Math.toDegrees(angle).toFloat() + 90f)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(tableSize)
                .clip(CircleShape)
                .background(colors.surface)
                .border(
                    width = tableLine(scale),
                    color = colors.border,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp * scale)
                    .clip(CircleShape)
                    .background(colors.labelBackground)
                    .border(tableLine(scale), colors.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (18 * scale).sp,
                    letterSpacing = 0.sp,
                    color = colors.content
                )
            }
        }

        if (showRing) TableRingOf(TableShape.Circle, seatCount, state, servedProgress, scale)
    }
}

// The ring around a table (user, 2026-10-31): a stronger blue fills as the order is served, a stronger purple as the
// booking's guests come, and yellow all round in the last minutes before a late booking goes.
internal data class TableRing(val color: Color, val progress: Float, val track: Color?)

internal val TableRingServed = Color(0xFF1E3A8A)
internal val TableRingEnding = Color(0xFFFACC15)
// Seated with more guests still to come: how many are here of all who'll be.
internal val TableRingComing = Color(0xFF9A3412)
private val TableRingWidth = 5.dp
// Thin enough to stay in the room before the chairs on a phone's small tables (user, 2026-11-01).
private val TableRingMinWidth = 1.dp

// A table's own outlines: a full line down to half size, then thinner with the table, so small tables never look heavy.
private fun tableLine(scale: Float): Dp = if (scale >= 0.5f) 1.dp else 2.dp * scale

internal fun tableRing(state: TableVisualState, progress: Float): TableRing? = when (state) {
    TableVisualState.Ordered -> TableRing(TableRingServed, progress, TableRingServed.copy(alpha = 0.16f))
    TableVisualState.HoldEnding -> TableRing(TableRingEnding, 1f, null)
    TableVisualState.Occupied -> TableRing(TableRingComing, progress, TableRingComing.copy(alpha = 0.16f))
    else -> null
}

internal fun DrawScope.drawCircleRing(ring: TableRing, width: Float) {
    val topLeft = Offset(width / 2, width / 2)
    val arcSize = Size(size.width - width, size.height - width)
    ring.track?.let { drawArc(it, 0f, 360f, false, topLeft, arcSize, style = Stroke(width)) }
    if (ring.progress > 0f) {
        drawArc(ring.color, -90f, 360f * ring.progress.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
    }
}

// The same ring along a rectangular table's edge, starting at the middle of its top side.
private fun DrawScope.drawSquareRing(ring: TableRing, corner: Float, width: Float) {
    val inset = width / 2
    val radius = (corner - inset).coerceAtLeast(0f)
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    val outline = Path().apply {
        moveTo(size.width / 2, top)
        lineTo(right - radius, top)
        arcTo(Rect(right - 2 * radius, top, right, top + 2 * radius), -90f, 90f, false)
        lineTo(right, bottom - radius)
        arcTo(Rect(right - 2 * radius, bottom - 2 * radius, right, bottom), 0f, 90f, false)
        lineTo(left + radius, bottom)
        arcTo(Rect(left, bottom - 2 * radius, left + 2 * radius, bottom), 90f, 90f, false)
        lineTo(left, top + radius)
        arcTo(Rect(left, top, left + 2 * radius, top + 2 * radius), 180f, 90f, false)
        close()
    }
    ring.track?.let { drawPath(outline, it, style = Stroke(width)) }
    if (ring.progress > 0f) {
        val measure = PathMeasure().apply { setPath(outline, false) }
        val filled = Path()
        measure.getSegment(0f, measure.length * ring.progress.coerceIn(0f, 1f), filled, true)
        drawPath(filled, ring.color, style = Stroke(width, cap = StrokeCap.Round))
    }
}

@Composable
private fun SeatRow(
    count: Int,
    firstIndex: Int,
    colorOf: (Int) -> Color,
    scale: Float = 1f,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(count.coerceAtLeast(0)) {
            Seat(
                width = 56.dp * scale,
                height = 13.dp * scale,
                color = colorOf(firstIndex + it),
                borderColor = colorOf(firstIndex + it)
            )
        }
    }
}

@Composable
private fun SeatColumn(
    count: Int,
    firstIndex: Int,
    colorOf: (Int) -> Color,
    scale: Float = 1f,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(count.coerceAtLeast(0)) {
            Seat(
                width = 13.dp * scale,
                height = 56.dp * scale,
                color = colorOf(firstIndex + it),
                borderColor = colorOf(firstIndex + it)
            )
        }
    }
}

@Composable
private fun Seat(
    width: Dp,
    height: Dp,
    color: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(1.dp, borderColor, RoundedCornerShape(50))
    )
}

private fun squareSeatDistribution(seatCount: Int): SquareSeatDistribution {
    if (seatCount <= 0) return SquareSeatDistribution()
    if (seatCount == 1) return SquareSeatDistribution(top = 1)
    if (seatCount == 2) return SquareSeatDistribution(top = 1, bottom = 1)

    val sideSeats = if (seatCount >= 4) 2 else 1
    val remaining = seatCount - sideSeats
    val top = (remaining + 1) / 2
    val bottom = remaining / 2

    return SquareSeatDistribution(
        top = top,
        bottom = bottom,
        left = 1,
        right = if (sideSeats == 2) 1 else 0
    )
}

// Chairs for guests beyond the seats: on the sides while there's room, then on the long sides.
private fun SquareSeatDistribution.withExtraChairs(extra: Int, sideRoom: Int): SquareSeatDistribution {
    var result = this
    repeat(extra.coerceAtLeast(0)) { index ->
        result = when {
            result.right < sideRoom -> result.copy(right = result.right + 1)
            result.left < sideRoom -> result.copy(left = result.left + 1)
            index % 2 == 0 -> result.copy(top = result.top + 1)
            else -> result.copy(bottom = result.bottom + 1)
        }
    }
    return result
}

private data class SquareSeatDistribution(
    val top: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
    val right: Int = 0
)
