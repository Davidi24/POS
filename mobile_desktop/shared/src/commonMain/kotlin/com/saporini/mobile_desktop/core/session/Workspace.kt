package com.saporini.mobile_desktop.core.session

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse

enum class Workspace {
    POS,
    KDS,
    ADMIN
}

fun accessibleWorkspaces(user: CurrentUserResponse): Set<Workspace> {
    val workspaces = mutableSetOf<Workspace>()

    if ("ORDER_CREATE" in user.permissions) {
        workspaces.add(Workspace.POS)
    }

    if ("USERS_READ" in user.permissions || "SETTINGS_UPDATE" in user.permissions) {
        workspaces.add(Workspace.ADMIN)
    }
    // Backend will add KDS_ACCESS permission + KITCHEN role
    if ("KDS_ACCESS" in user.permissions) {
        workspaces.add(Workspace.KDS)
    }

    return workspaces
}