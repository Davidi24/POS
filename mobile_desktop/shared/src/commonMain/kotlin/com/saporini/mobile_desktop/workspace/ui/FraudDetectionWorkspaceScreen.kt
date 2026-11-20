package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

object FraudDetectionWorkspaceScreen : Screen {
    @Composable
    override fun Content() {
        WorkspaceSections(WorkspacePage.FRAUD_DETECTION)
    }
}
