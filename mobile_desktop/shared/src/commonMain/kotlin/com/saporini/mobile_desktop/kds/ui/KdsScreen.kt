package com.saporini.mobile_desktop.kds.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import com.saporini.mobile_desktop.workspace.ui.ComingSoonScreen

object KdsScreen : Screen {
    @Composable
    override fun Content() {
        ComingSoonScreen("KDS")
    }
}
