package com.saporini.mobile_desktop.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shows [text] on mouse hover over [content] (desktop only — touch platforms have no
 * hover concept, so [content] renders as-is there). Used for small icon-only buttons
 * (e.g. the card edit pencil) where the action isn't otherwise labeled, and for text
 * that's been truncated and needs a way to read it in full.
 *
 * [enabled] lets a caller skip attaching the tooltip altogether — e.g. only showing it
 * once a Text's own `onTextLayout` reports it actually got ellipsized, rather than
 * always paying for a tooltip that would just repeat text already fully visible.
 */
@Composable
internal expect fun HoverTooltip(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
)
