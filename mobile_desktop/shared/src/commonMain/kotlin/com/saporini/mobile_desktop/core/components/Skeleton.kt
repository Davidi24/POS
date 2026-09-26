package com.saporini.mobile_desktop.core.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// Loading placeholders shaped like the real content, pulsing like the Menu skeletons.
val SkeletonBlock = Color(0xFFDADADA)
val SkeletonLight = Color(0xFFE8E8E8)

// The shared slow pulse; apply it once to a whole skeleton with Modifier.alpha(...).
@Composable
fun rememberSkeletonAlpha(label: String = "skeleton"): Float {
    val transition = rememberInfiniteTransition(label = label)
    val alpha by transition.animateFloat(
        initialValue = 0.48f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 850), repeatMode = RepeatMode.Reverse),
        label = "$label-alpha"
    )
    return alpha
}

@Composable
fun SkeletonBox(modifier: Modifier, color: Color = SkeletonBlock, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.background(color, shape))
}
