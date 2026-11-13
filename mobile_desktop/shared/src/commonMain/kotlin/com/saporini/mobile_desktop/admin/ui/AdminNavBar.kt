package com.saporini.mobile_desktop.admin.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.ui.shell.NavEntry
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceNavBottomBar
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceNavTopBar
import org.koin.compose.koinInject

// The Admin Hub uses the POS bar: Shifts, Salary and Users always show, the next place is Settings or whatever was
// picked last from More, and More holds the rest.

@Composable
fun AdminTopBar(
    selected: AdminSection,
    onSelect: (AdminSection) -> Unit,
    onLogout: () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
) {
    val entries = adminNavEntries()
    WorkspaceNavTopBar(entries, fixed = entries.indexOfFirst { it.key == AdminSection.USERS } + 1, selected = selected,
        onSelect = onSelect, onLogout = onLogout, onBackToWorkspaces = onBackToWorkspaces)
}

@Composable
fun AdminBottomBar(
    selected: AdminSection,
    onSelect: (AdminSection) -> Unit,
    onLogout: () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
) {
    WorkspaceNavBottomBar(adminNavEntries(), selected, onSelect, onLogout, onBackToWorkspaces)
}

/** The Admin Hub's places in bar order, without the ones the signed-in person can't open. */
@Composable
private fun adminNavEntries(): List<NavEntry<AdminSection>> {
    val user by koinInject<SessionManager>().currentUser.collectAsState()
    val permissions = user?.permissions.orEmpty()
    val canReadShifts = permissions.any { it == "SHIFT_READ" || it == "SHIFT_MANAGE" }
    // Salary shows what people earn, so only for people who manage shifts.
    val canSeePay = "SHIFT_MANAGE" in permissions
    // Inventory and Suppliers are their own workspace (Workspace.INVENTORY), so the Admin Hub starts with Shifts.
    return listOf(AdminSection.SHIFTS, AdminSection.PAY, AdminSection.USERS, AdminSection.SETTINGS, AdminSection.DEVICES,
        AdminSection.AUDIT_LOGS, AdminSection.RESTAURANT_INFO)
        .filter { (it != AdminSection.SHIFTS || canReadShifts) && (it != AdminSection.PAY || canSeePay) }
        .map { NavEntry(it, it.label, it.icon(), it.filledIcon()) }
}

private fun AdminSection.icon(): ImageVector = when (this) {
    AdminSection.SHIFTS -> Icons.Outlined.CalendarMonth
    AdminSection.PAY -> Icons.Outlined.Payments
    AdminSection.DEVICES -> Icons.Outlined.Devices
    AdminSection.SETTINGS -> Icons.Outlined.Settings
    AdminSection.USERS -> Icons.Outlined.People
    AdminSection.AUDIT_LOGS -> Icons.Outlined.History
    AdminSection.RESTAURANT_INFO -> Icons.Outlined.Storefront
}

private fun AdminSection.filledIcon(): ImageVector = when (this) {
    AdminSection.SHIFTS -> Icons.Filled.CalendarMonth
    AdminSection.PAY -> Icons.Filled.Payments
    AdminSection.DEVICES -> Icons.Filled.Devices
    AdminSection.SETTINGS -> Icons.Filled.Settings
    AdminSection.USERS -> Icons.Filled.People
    AdminSection.AUDIT_LOGS -> Icons.Filled.History
    AdminSection.RESTAURANT_INFO -> Icons.Filled.Storefront
}
