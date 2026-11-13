package com.saporini.mobile_desktop.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
internal actual fun isPhoneWindow(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.smallestScreenWidthDp in 1 until 600 || configuration.screenWidthDp < 600
}

@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
