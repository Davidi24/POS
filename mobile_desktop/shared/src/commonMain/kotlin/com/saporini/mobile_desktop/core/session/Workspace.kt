package com.saporini.mobile_desktop.core.session

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse

enum class Workspace {
    POS,
    KDS,
    ADMIN,
    // Managing all restaurants on the platform: super admins only.
    RESTAURANTS
}

fun accessibleWorkspaces(user: CurrentUserResponse): Set<Workspace> {
    // A super admin can open every workspace.
    if (user.roles.any { it.equals("SUPER_ADMIN", ignoreCase = true) }) {
        return Workspace.entries.toSet()
    }

    val workspaces = mutableSetOf<Workspace>()

    // Each workspace has its own entry permission, so a role's workspaces don't depend on what it can do inside them.
    if ("POS_ACCESS" in user.permissions) {
        workspaces.add(Workspace.POS)
    }
    if ("KDS_ACCESS" in user.permissions) {
        workspaces.add(Workspace.KDS)
    }
    if ("ADMIN_ACCESS" in user.permissions) {
        workspaces.add(Workspace.ADMIN)
    }

    return workspaces
}