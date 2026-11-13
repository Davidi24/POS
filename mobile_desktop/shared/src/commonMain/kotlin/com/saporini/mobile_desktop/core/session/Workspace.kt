package com.saporini.mobile_desktop.core.session

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse

enum class Workspace {
    POS,
    KDS,
    // Stock and suppliers, apart from the Admin Hub. The picker lists workspaces in this order, and Inventory comes before
    // the Admin Hub (user rule).
    INVENTORY,
    ADMIN,
    // Opened with STATISTICS_ACCESS (Owner, Co-Owner, Admin).
    STATISTICS,
    FRAUD_DETECTION,
    // The restaurant's own details (currency, time zone, branches, legal details). Super admins only, and never a card in
    // the picker (user rule, 2026-10-29): it opens from the restaurant list for a closed or branch-less restaurant, and
    // inside a restaurant the same page is Admin Hub → Restaurant info.
    RESTAURANT
}

fun accessibleWorkspaces(user: CurrentUserResponse): Set<Workspace> {
    // Super admins first pick a restaurant (the restaurant list); inside it the Super Admin opens every workspace and a
    // Co-Super-Admin the ones their ticks allow.
    if (isPlatformAdmin(user)) {
        if (user.restaurantId == null) return emptySet()
        if (user.defaultBranchId == null) return if ("RESTAURANTS_READ" in user.permissions) setOf(Workspace.RESTAURANT) else emptySet()
    }

    val workspaces = mutableSetOf<Workspace>()
    if (isPlatformAdmin(user) && "RESTAURANTS_READ" in user.permissions) {
        workspaces.add(Workspace.RESTAURANT)
    }

    // Each workspace has its own entry permission, so a role's workspaces don't depend on what it can do inside them.
    if (user.defaultBranchId != null && "POS_ACCESS" in user.permissions) {
        workspaces.add(Workspace.POS)
    }
    if (user.defaultBranchId != null && "KDS_ACCESS" in user.permissions) {
        workspaces.add(Workspace.KDS)
    }
    if ("ADMIN_ACCESS" in user.permissions) {
        workspaces.add(Workspace.ADMIN)
    }
    // Inventory has its own permissions since 2026-10-30: opening it and seeing stock.
    if ("INVENTORY_ACCESS" in user.permissions && "INVENTORY_READ" in user.permissions) {
        workspaces.add(Workspace.INVENTORY)
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