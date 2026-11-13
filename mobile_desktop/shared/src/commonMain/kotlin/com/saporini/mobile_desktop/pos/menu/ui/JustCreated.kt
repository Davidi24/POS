package com.saporini.mobile_desktop.pos.menu.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val JustCreatedGreen = Color(0xFF4F7942)

// How long something just created stays marked.
internal const val JustCreatedMillis = 3_000L

/**
 * Something just created (a menu, a dish, a section): scrolled into view, then marked with a green ring, a soft glow
 * and a gentle pulse that fade out. Put it before the element's own clip so the glow isn't cut off.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.justCreated(highlighted: Boolean, shape: Shape): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            // Give the new element a frame to be laid out before scrolling to it.
            delay(60)
            requester.bringIntoView()
        }
    }
    val strength by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = tween(if (highlighted) 260 else 900),
        label = "just-created-strength"
    )
    val pulse by rememberInfiniteTransition(label = "just-created-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "just-created-scale"
    )
    val marked = if (strength <= 0.01f) {
        Modifier
    } else {
        val scale = 1f + (pulse - 1f) * strength
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = (18 * strength).dp,
                shape = shape,
                clip = false,
                ambientColor = JustCreatedGreen.copy(alpha = 0.5f * strength),
                spotColor = JustCreatedGreen.copy(alpha = 0.7f * strength)
            )
            .border(2.5.dp, JustCreatedGreen.copy(alpha = strength), shape)
    }
    return this.bringIntoViewRequester(requester).then(marked)
}

/** Holds what was just created and clears it after [JustCreatedMillis]. */
@Composable
internal fun rememberJustCreated(): MutableState<String?> {
    val state = remember { mutableStateOf<String?>(null) }
    val current = state.value
    LaunchedEffect(current) {
        if (current != null) {
            delay(JustCreatedMillis)
            if (state.value == current) state.value = null
        }
    }
    return state
}
