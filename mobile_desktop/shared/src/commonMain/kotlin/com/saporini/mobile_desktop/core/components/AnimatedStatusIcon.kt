package com.saporini.mobile_desktop.core.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A small ring that draws itself, then an icon that pops in with a bouncy
 * scale + color transition. Same animation language as the auth screens'
 * `AnimatedSuccessCheck`/`AnimatedLockIcon` (see AuthStatusScreens.kt),
 * generalized and re-timed for compact toast-sized use elsewhere in the app
 * instead of a full-screen confirmation moment.
 */
@Composable
internal fun AnimatedStatusIcon(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    strokeWidth: Dp = 2.dp,
    iconSize: Dp = size * 0.5f,
    neutralColor: Color = Color(0xFF111111)
) {
    var startDrawing by remember { mutableStateOf(false) }
    var iconVisible by remember { mutableStateOf(false) }

    val ringProgress by animateFloatAsState(
        targetValue = if (startDrawing) 1f else 0f,
        animationSpec = tween(durationMillis = 480),
        label = "status-icon-ring-progress"
    )
    val tintColor by animateColorAsState(
        targetValue = if (iconVisible) color else neutralColor,
        animationSpec = tween(durationMillis = 220),
        label = "status-icon-color"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = EaseOutBack),
        label = "status-icon-scale"
    )

    LaunchedEffect(icon, color) {
        startDrawing = false
        iconVisible = false
        startDrawing = true
        delay(460)
        iconVisible = true
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            drawArc(
                color = tintColor,
                startAngle = -90f,
                sweepAngle = 360f * ringProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(iconSize).scale(iconScale)
        )
    }
}
