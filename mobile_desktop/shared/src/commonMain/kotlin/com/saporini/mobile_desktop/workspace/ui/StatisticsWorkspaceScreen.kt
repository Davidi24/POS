package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

// Reporting content will be added separately from this navigation shell.
object StatisticsWorkspaceScreen : Screen {
    @Composable
    override fun Content() {
        WorkspaceSections(WorkspacePage.STATISTICS)
    }
}
