package com.saporini.mobile_desktop.workspace.ui

import cafe.adriel.voyager.core.screen.Screen
import com.saporini.mobile_desktop.admin.ui.AdminScreen
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.session.Workspace
import com.saporini.mobile_desktop.core.session.accessibleWorkspaces
import com.saporini.mobile_desktop.core.session.isPlatformAdmin
import com.saporini.mobile_desktop.kds.ui.KdsScreen
import com.saporini.mobile_desktop.pos.ui.PosScreen

fun screenFor(workspace: Workspace): Screen = when (workspace) {
    Workspace.POS -> PosScreen
    Workspace.KDS -> KdsScreen
    Workspace.ADMIN -> AdminScreen
    Workspace.INVENTORY -> InventoryWorkspaceScreen
    Workspace.STATISTICS -> StatisticsWorkspaceScreen
    Workspace.FRAUD_DETECTION -> FraudDetectionWorkspaceScreen
    Workspace.RESTAURANT -> com.saporini.mobile_desktop.platform.ui.RestaurantWorkspaceScreen
}

fun resolveStartScreen(user: CurrentUserResponse): Screen {
    // Super admins start on the restaurant list and enter a restaurant from there.
    if (isPlatformAdmin(user)) return com.saporini.mobile_desktop.platform.ui.RestaurantListScreen
    val workspaces = accessibleWorkspaces(user)
    return when {
        workspaces.isEmpty() -> NoAccessScreen
        workspaces.size == 1 -> screenFor(workspaces.first())
        else -> WorkspacePickerScreen
    }
}