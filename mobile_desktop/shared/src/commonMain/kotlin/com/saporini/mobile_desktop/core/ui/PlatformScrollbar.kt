package com.saporini.mobile_desktop.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

// A thin scrollbar on desktop; touch platforms draw nothing (scrolling shows its own indicator there).
@Composable
expect fun PlatformVerticalScrollbar(state: ScrollState, modifier: Modifier = Modifier)

@Composable
expect fun PlatformVerticalScrollbar(state: LazyListState, modifier: Modifier = Modifier)

/** Room kept on the right of scrolling content for that scrollbar: none where nothing is drawn, so phones keep even sides. */
expect val PlatformScrollbarRoom: Dp
