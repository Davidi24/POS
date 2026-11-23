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
    // Taxes: VAT, expenses, payroll, deadlines and returns (user, 2026-11-23: "one more app, finance and taxation").
    FINANCE,
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
    // A workspace shows when it's open to them and at least one of its places is (user, 2026-11-19: each place has its own
    // "See …").
    if (user.defaultBranchId != null && "POS_ACCESS" in user.permissions &&
        com.saporini.mobile_desktop.pos.ui.PosSection.allowed(user.permissions).isNotEmpty()) {
        workspaces.add(Workspace.POS)
    }
    if (user.defaultBranchId != null && "KDS_ACCESS" in user.permissions &&
        com.saporini.mobile_desktop.kds.ui.KdsSection.allowed(user.permissions).isNotEmpty()) {
        workspaces.add(Workspace.KDS)
    }
    if ("ADMIN_ACCESS" in user.permissions && com.saporini.mobile_desktop.admin.ui.AdminSection.allowed(user.permissions).isNotEmpty()) {
        workspaces.add(Workspace.ADMIN)
    }
    // Inventory has its own permissions since 2026-10-30: opening it, then each of its places (2026-11-19).
    if ("INVENTORY_ACCESS" in user.permissions && (com.saporini.mobile_desktop.admin.inventory.InventoryRights.of(user.permissions).sees ||
            "SUPPLIERS_READ" in user.permissions)) {
        workspaces.add(Workspace.INVENTORY)
    }

    if ("STATISTICS_ACCESS" in user.permissions &&
        com.saporini.mobile_desktop.statistics.StatsTab.entries.any { it.permission in user.permissions }) {
        workspaces.add(Workspace.STATISTICS)
    }
    // MOCK until the server has its own FINANCE_ACCESS: whoever handles the restaurant's money settings (owners by default).
    if ("FINANCE_ACCESS" in user.permissions || "SETTINGS_MONEY" in user.permissions) {
        workspaces.add(Workspace.FINANCE)
    }
    // Older servers don't send FRAUD_ACCESS yet; owners and co-owners always had Fraud Detection.
    if ("FRAUD_ACCESS" in user.permissions ||
        user.roles.any { it.equals("OWNER", ignoreCase = true) || it.equals("CO_OWNER", ignoreCase = true) }) {
        workspaces.add(Workspace.FRAUD_DETECTION)
    }

    return workspaces
}