package com.saporini.mobile_desktop.platform.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import com.saporini.mobile_desktop.workspace.ui.WorkspacePage
import com.saporini.mobile_desktop.workspace.ui.WorkspaceSections

/** The sixth app, for super admins: the entered restaurant's own details. */
object RestaurantWorkspaceScreen : Screen {
    @Composable
    override fun Content() {
        WorkspaceSections(WorkspacePage.RESTAURANT)
    }
}
