package com.saporini.mobile_desktop.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun PlatformVerticalScrollbar(state: ScrollState, modifier: Modifier) = Unit

@Composable
actual fun PlatformVerticalScrollbar(state: LazyListState, modifier: Modifier) = Unit

actual val PlatformScrollbarRoom: Dp = 0.dp
