package com.saporini.mobile_desktop.core.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.coroutines.delay

internal val ToastBackground = Color(0xFF232422)
private val ToastGreen = Color(0xFF6FCF87)
private val ToastRed = Color(0xFFE5695D)
internal val ToastYellow = Color(0xFFF2C94C)
private val ToastBlue = Color(0xFF8AB8F2)

/**
 * The app's notification: a dark card that slides up in the bottom-left corner of the window and goes away by itself
 * (after 4 s, 5 s for a warning, or 7 s for a problem), or when closed. Wherever the screen calls it from, it always shows
 * in that corner. Green with a tick: done. Red with a cross: it failed. Yellow with "!" ([isWarning]): that isn't allowed,
 * so nothing was done (user, 2026-10-31: a refusal never gets the green tick). Blue with a pointing hand ([isTip]): what
 * to do next, staying 8 s; [actionLabel] adds a small button (e.g. "Don't show again") that runs [onAction] and closes it.
 */
@Composable
internal fun BottomToast(
    message: String?,
    isError: Boolean = false,
    isWarning: Boolean = false,
    isTip: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit
) {
    if (message.isNullOrBlank()) return
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message, isError) {
        delay(when { isError -> 7_000; isTip -> 8_000; isWarning -> 5_000; else -> 4_000 })
        dismiss()
    }
    val margin = with(LocalDensity.current) { 24.dp.roundToPx() }
    val corner = remember(margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
                IntOffset(margin, windowSize.height - popupContentSize.height - margin)
        }
    }
    val appear = remember(message) { Animatable(0f) }
    LaunchedEffect(message) { appear.animateTo(1f, tween(280)) }
    Popup(popupPositionProvider = corner, properties = PopupProperties(focusable = false)) {
        Row(
            Modifier.graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 28.dp.toPx()
            }
                .widthIn(max = 480.dp).shadow(14.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(ToastBackground)
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            key(message) {
                AnimatedStatusIcon(
                    when { isError -> Icons.Outlined.Close; isWarning -> Icons.Outlined.PriorityHigh; isTip -> Icons.Outlined.TouchApp; else -> Icons.Outlined.Check },
                    when { isError -> ToastRed; isWarning -> ToastYellow; isTip -> ToastBlue; else -> ToastGreen },
                    size = 26.dp, strokeWidth = 1.5.dp)
            }
            Text(message, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                lineHeight = 18.sp, color = Color.White)
            if (actionLabel != null) {
                Text(actionLabel, Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.12f))
                    .clickable { onAction(); dismiss() }.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White, maxLines = 1)
            }
            Box(Modifier.size(28.dp).clip(CircleShape).clickable { dismiss() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Close", Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
