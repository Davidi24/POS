package com.saporini.mobile_desktop.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** Phones retain their navigation and form presentation when rotated. */
@Composable
internal expect fun isPhoneWindow(): Boolean

/** Intercepts the platform back action when the current screen owns it. */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
internal fun isWidePhoneWindow(): Boolean {
    val width = LocalWindowInfo.current.containerSize.width
    return isPhoneWindow() && with(LocalDensity.current) { width.toDp() >= 600.dp }
}
