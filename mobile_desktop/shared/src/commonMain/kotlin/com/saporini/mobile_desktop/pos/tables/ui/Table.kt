package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
    BillPending,
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
    scale: Float = 1f
) {
    val servedProgress = if (totalItems > 0) {
        servedItems.coerceIn(0, totalItems).toFloat() / totalItems
    } else 0f
    when (shape) {
        TableShape.Circle -> CircleTable(
            seatCount = seatCount,
            label = label,
            state = state,
            servedProgress = servedProgress,
            modifier = modifier,
            scale = scale
        )

        TableShape.Square -> SquareTable(
            seatCount = seatCount,
            label = label,
            state = state,
            orderLabel = orderLabel,
            statusText = statusText,
            servedProgress = servedProgress,
            modifier = modifier,
            scale = scale
        )
    }
}

private fun tableColors(state: TableVisualState, servedProgress: Float = 0f): TableColors =
    when (state) {
        TableVisualState.Free -> TableColors(
            surface = Color(0xFF4B522A),
            border = Color(0xFF4B522A),
            labelBackground = Color(0xFFF3F6EC),
            content = Color(0xFF4B522A)
        )

        TableVisualState.Occupied -> TableColors(
            surface = Color(0xFFB85E3B),
            border = Color(0xFFB85E3B),
            labelBackground = Color(0xFFFFF3EE),
            content = Color(0xFFB85E3B)
        )

        TableVisualState.Reserved -> TableColors(
            surface = Color(0xFFC47A18),
            border = Color(0xFFC47A18),
            labelBackground = Color(0xFFFFF7E8),
            content = Color(0xFFC47A18)
        )

        TableVisualState.BillPending -> TableColors(
            surface = Color(0xFF2F6FB1),
            border = Color(0xFF2F6FB1),
            labelBackground = Color(0xFFEEF6FF),
            content = Color(0xFF2F6FB1)
        )

        TableVisualState.Unavailable -> TableColors(
            surface = Color(0xFF777777),
            border = Color(0xFF777777),
            labelBackground = Color(0xFFF2F2F2),
            content = Color(0xFF4A4A4A)
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
    scale: Float = 1f
) {
    val showDetails = state != TableVisualState.Free && (orderLabel != null || statusText != null)
    val colors = tableColors(state, servedProgress)
    val bodyWidth = (if (seatCount > 4) 248.dp else 128.dp) * scale
    val bodyHeight = (if (seatCount > 4) 112.dp else 128.dp) * scale
    val totalWidth = bodyWidth + 48.dp * scale
    val totalHeight = bodyHeight + 40.dp * scale
    val distribution = squareSeatDistribution(seatCount)

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(totalHeight)
    ) {
        SeatRow(
            count = distribution.top,
            seatColor = colors.surface,
            seatBorder = colors.border,
            scale = scale,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(bodyWidth)
        )

        SeatRow(
            count = distribution.bottom,
            seatColor = colors.surface,
            seatBorder = colors.border,
            scale = scale,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(bodyWidth)
        )

        SeatColumn(
            count = distribution.left,
            seatColor = colors.surface,
            seatBorder = colors.border,
            scale = scale,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        SeatColumn(
            count = distribution.right,
            seatColor = colors.surface,
            seatBorder = colors.border,
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
                    width = 1.dp,
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
                        .border(1.dp, colors.border, CircleShape),
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
            if (state == TableVisualState.Occupied && seatCount > 4) {
                ServedProgressLine(
                    progress = servedProgress,
                    scale = scale,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp * scale)
                        .width(bodyWidth * 0.72f)
                )
            }
        }
    }
}

@Composable
private fun CircleTable(
    seatCount: Int,
    label: String,
    state: TableVisualState,
    servedProgress: Float,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    val colors = tableColors(state, servedProgress)
    val totalSize = 156.dp * scale
    val tableSize = 86.dp * scale
    val chairWidth = 34.dp * scale
    val chairHeight = 12.dp * scale
    val center = totalSize / 2
    val radius = 58.dp * scale

    Box(
        modifier = modifier.size(totalSize)
    ) {
        repeat(seatCount.coerceAtLeast(0)) { index ->
            val angle = (-PI / 2.0) + (2.0 * PI * index / seatCount.coerceAtLeast(1))
            val x = center + radius * cos(angle).toFloat() - chairWidth / 2
            val y = center + radius * sin(angle).toFloat() - chairHeight / 2

            Seat(
                width = chairWidth,
                height = chairHeight,
                color = colors.surface,
                borderColor = colors.border,
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
                    width = 1.dp,
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
                    .border(1.dp, colors.border, CircleShape),
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

        if (state == TableVisualState.Occupied) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(tableSize)
            ) {
                drawArc(
                    color = Color(0xFF6F43B5),
                    startAngle = -90f,
                    sweepAngle = 360f * servedProgress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(
                        width = 6.dp.toPx() * scale,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

@Composable
private fun ServedProgressLine(
    progress: Float,
    scale: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(5.dp * scale)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.48f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp * scale)
                .background(Color(0xFF5F35A4), RoundedCornerShape(50))
        )
    }
}

@Composable
private fun SeatRow(
    count: Int,
    seatColor: Color,
    seatBorder: Color,
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
                color = seatColor,
                borderColor = seatBorder
            )
        }
    }
}

@Composable
private fun SeatColumn(
    count: Int,
    seatColor: Color,
    seatBorder: Color,
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
                color = seatColor,
                borderColor = seatBorder
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

private data class SquareSeatDistribution(
    val top: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
    val right: Int = 0
)
