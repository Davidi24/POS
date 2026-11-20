package com.saporini.mobile_desktop.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Width classes for the POS screens. DESKTOP keeps the original desktop layouts untouched;
// TABLET and PHONE get their own arrangements.
enum class ScreenSize {
    PHONE, TABLET, DESKTOP;

    val isDesktop: Boolean get() = this == DESKTOP
    val isPhone: Boolean get() = this == PHONE
}

fun screenSizeFor(width: Dp): ScreenSize = when {
    width < 600.dp -> ScreenSize.PHONE
    width < 1100.dp -> ScreenSize.TABLET
    else -> ScreenSize.DESKTOP
}
