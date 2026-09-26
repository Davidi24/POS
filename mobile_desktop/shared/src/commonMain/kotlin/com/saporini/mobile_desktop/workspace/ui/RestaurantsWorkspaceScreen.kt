package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

// Super admin workspace for all restaurants; the real screens come later.
object RestaurantsWorkspaceScreen : Screen {
    @Composable
    override fun Content() {
        ComingSoonScreen("Restaurants")
    }
}
