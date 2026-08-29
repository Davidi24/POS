package com.saporini.mobile_desktop.admin.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import com.saporini.mobile_desktop.workspace.ui.ComingSoonScreen

object AdminScreen : Screen {
    @Composable
    override fun Content() {
        ComingSoonScreen("Admin")
    }
}
