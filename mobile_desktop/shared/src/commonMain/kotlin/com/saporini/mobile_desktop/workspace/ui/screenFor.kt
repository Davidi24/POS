package com.saporini.mobile_desktop.workspace.ui

import cafe.adriel.voyager.core.screen.Screen
import com.saporini.mobile_desktop.admin.ui.AdminScreen
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.session.Workspace
import com.saporini.mobile_desktop.core.session.accessibleWorkspaces
import com.saporini.mobile_desktop.kds.ui.KdsScreen
import com.saporini.mobile_desktop.pos.ui.PosScreen

fun screenFor(workspace: Workspace): Screen = when (workspace) {
    Workspace.POS -> PosScreen
    Workspace.KDS -> KdsScreen
    Workspace.ADMIN -> AdminScreen
}

fun resolveStartScreen(user: CurrentUserResponse): Screen {
    val workspaces = accessibleWorkspaces(user)
    return when {
        workspaces.isEmpty() -> NoAccessScreen
        workspaces.size == 1 -> screenFor(workspaces.first())
        else -> WorkspacePickerScreen
    }
}