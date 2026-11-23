package com.saporini.mobile_desktop.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse

// Branch level and the whole restaurant (user, 2026-11-23, step 25: "now we work as if we had one branch, which is perfect;
// with more we need this"). Every workspace works at a branch (the one the person is in) and, where it makes sense, for
// all branches together:
// Reasoned with the user the same day ("only the ones reasonable to show for all"): what's done at a place stays at the
// branch, what an owner looks at across the business has all branches too.
//   POS — the branch only: orders, tables, bookings and payments happen at a place (menus, prices and settings are shared)
//   KDS — the branch only: a kitchen screen is that branch's kitchen
//   Inventory — the branch's own stock; all branches = every branch's stock together
//   Admin Hub — the branch's staff, devices and shifts; all branches = users, roles and branches
//   Statistics — the branch; all branches combined
//   Finance & taxation — the branch's records; all branches = consolidated
//   Fraud Detection — the branch's alerts; all branches = alerts across them

/**
 * Choosing the branch to work in (the restaurant's home after signing in, the switcher in the bars, each workspace at that
 * branch): on (user, 2026-11-23: "leave the page, just working inside the branches").
 */
const val BRANCH_CHOICE_ON = true

/**
 * Every branch together (the whole restaurant: combined inventory, statistics, finance, fraud, users and branches): "coming
 * soon", after the first launch (user, 2026-11-23: "not the gathered ones like inventory, make them coming soon"). The views are
 * built; true brings them back. The plan is step 25, `doc/production-ready/25-branches-and-whole-restaurant.md`.
 */
const val ALL_BRANCHES_ON = false

/** One branch for the switcher. */
data class ScopeBranch(val id: String, val name: String, val code: String = "", val open: Boolean = true)

/** Which branches the restaurant has, and which workspaces are looking at all of them. */
object BranchScope {
    var restaurantId: String? by mutableStateOf(null)
        private set
    var branches: List<ScopeBranch> by mutableStateOf(emptyList())
        private set

    /** The workspaces showing every branch together (the rest show the branch the person is in). Kept for the session. */
    var everywhere: Set<Workspace> by mutableStateOf(emptySet())
        private set

    fun loaded(restaurantId: String, branches: List<ScopeBranch>) {
        if (this.restaurantId != restaurantId) everywhere = emptySet()
        this.restaurantId = restaurantId
        this.branches = branches
    }

    /** Whether [workspace] shows every branch now (only with two or more). */
    fun isAll(workspace: Workspace): Boolean = ALL_BRANCHES_ON && workspace in everywhere && branches.size > 1 && workspace.globalLevel != null

    fun showAll(workspace: Workspace, all: Boolean) {
        everywhere = if (all) everywhere + workspace else everywhere - workspace
    }

    fun branch(id: String?): ScopeBranch? = id?.let { branches.firstOrNull { b -> b.id == id } }
}

/**
 * Who may choose the branch they work in and switch between branches: super admins, and anyone with "Work in every branch"
 * (`BRANCHES_ALL`; owner, co-owner and admin by default, user 2026-11-23; taken away from someone, they work only in their own
 * branch, like the demo `owner`).
 */
fun canSeeEveryBranch(user: CurrentUserResponse?): Boolean {
    if (!BRANCH_CHOICE_ON) return false
    user ?: return false
    if (isPlatformAdmin(user)) return true
    return "BRANCHES_ALL" in user.permissions
}

/** What "all branches" means in a workspace, or null when it has none (the POS works at one branch only). */
val Workspace.globalLevel: String?
    get() = when (this) {
        Workspace.POS -> null
        Workspace.KDS -> null
        Workspace.INVENTORY -> "Every branch's stock together"
        Workspace.ADMIN -> "Users, roles and branches"
        Workspace.STATISTICS -> "Every branch combined"
        Workspace.FINANCE -> "Consolidated: every branch together"
        Workspace.FRAUD_DETECTION -> "Alerts across every branch"
        Workspace.RESTAURANT -> null
    }

/** What a workspace shows at a branch. */
val Workspace.branchLevel: String
    get() = when (this) {
        Workspace.POS -> "Its orders and payments"
        Workspace.KDS -> "Its kitchen"
        Workspace.INVENTORY -> "Its own stock"
        Workspace.ADMIN -> "Its staff, devices and shifts"
        Workspace.STATISTICS -> "Its own figures"
        Workspace.FINANCE -> "Its own records"
        Workspace.FRAUD_DETECTION -> "Its own alerts"
        Workspace.RESTAURANT -> ""
    }
