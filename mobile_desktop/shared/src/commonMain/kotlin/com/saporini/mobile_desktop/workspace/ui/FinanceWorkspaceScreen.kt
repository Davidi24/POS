package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

/** Finance & taxation (user, 2026-11-23). */
object FinanceWorkspaceScreen : Screen {
    @Composable
    override fun Content() {
        WorkspaceSections(WorkspacePage.FINANCE)
    }
}
