package com.saporini.mobile_desktop.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** No hover concept on touch — render [content] as-is. */
@Composable
internal actual fun HoverTooltip(
    text: String,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    content()
}
