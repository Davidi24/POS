package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

/** The Inventory workspace: stock (overview, items, counts, history) and suppliers. */
object InventoryWorkspaceScreen : Screen {
    @Composable
    override fun Content() {
        WorkspaceSections(WorkspacePage.INVENTORY)
    }
}
