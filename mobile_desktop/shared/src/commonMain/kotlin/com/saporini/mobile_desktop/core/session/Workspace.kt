package com.saporini.mobile_desktop.core.session

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse

enum class Workspace {
    POS,
    KDS,
    ADMIN,
    // Opened with STATISTICS_ACCESS (Owner, Co-Owner, Admin).
    STATISTICS,
    FRAUD_DETECTION
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

    if ("STATISTICS_ACCESS" in user.permissions) {
        workspaces.add(Workspace.STATISTICS)
    }
    // Older servers don't send FRAUD_ACCESS yet; owners and co-owners always had Fraud Detection.
    if ("FRAUD_ACCESS" in user.permissions ||
        user.roles.any { it.equals("OWNER", ignoreCase = true) || it.equals("CO_OWNER", ignoreCase = true) }) {
        workspaces.add(Workspace.FRAUD_DETECTION)
    }

    return workspaces
}