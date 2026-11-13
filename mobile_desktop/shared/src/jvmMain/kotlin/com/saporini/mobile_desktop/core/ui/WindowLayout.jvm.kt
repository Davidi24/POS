package com.saporini.mobile_desktop.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

@Composable
internal actual fun isPhoneWindow(): Boolean {
    val width = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { width.toDp() < 600.dp }
}


@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
