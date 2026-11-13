package com.saporini.mobile_desktop.core.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Something just got made: what to say, and a number that changes each time so the same words play again. */
data class DoneMark(val tick: Int, val text: String)

/**
 * A short "done" moment in the middle of the screen (user, 2026-10-30: "an animation so I understand it is done"): a green
 * tick pops in with one line, stays a moment and fades. It never takes clicks. Plays once each time [mark] changes.
 */
@Composable
internal fun DoneBurst(mark: DoneMark?, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }
    // One that already played before this screen showed (e.g. on the other workspace tab) doesn't play again.
    val alreadySeen = remember { mark?.tick }
    LaunchedEffect(mark?.tick) {
        if (mark == null || mark.tick == alreadySeen) return@LaunchedEffect
        scale.snapTo(0.6f)
        alpha.snapTo(0f)
        coroutineScope {
            launch { alpha.animateTo(1f, tween(140)) }
            scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
        }
        delay(800)
        alpha.animateTo(0f, tween(280))
    }
    if (mark == null || alpha.value <= 0.01f) return
    // A popup in the middle of the window that never takes focus or clicks.
    Popup(alignment = Alignment.Center, properties = PopupProperties(focusable = false)) {
        Column(
            modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
                .shadow(18.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White)
                .widthIn(min = 180.dp, max = 320.dp).padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Kit.Green), Alignment.Center) {
                Icon(Icons.Filled.Check, null, Modifier.size(32.dp), tint = Color.White)
            }
            Text(mark.text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink, textAlign = TextAlign.Center)
        }
    }
}
