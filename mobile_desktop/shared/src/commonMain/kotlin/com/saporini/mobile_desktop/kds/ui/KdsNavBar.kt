package com.saporini.mobile_desktop.kds.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.ui.graphics.vector.ImageVector
import com.saporini.mobile_desktop.pos.ui.shell.NavEntry
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceNavBottomBar
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceNavTopBar

// The KDS uses the POS bar: its first three places always show, then the one picked last, and More for the rest.

@Composable
fun KdsTopBar(
    selected: KdsSection,
    onSelect: (KdsSection) -> Unit,
    onLogout: () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
) {
    WorkspaceNavTopBar(kdsNavEntries(), fixed = 3, selected = selected, onSelect = onSelect, onLogout = onLogout,
        onBackToWorkspaces = onBackToWorkspaces)
}

@Composable
fun KdsBottomBar(
    selected: KdsSection,
    onSelect: (KdsSection) -> Unit,
    onLogout: () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
) {
    WorkspaceNavBottomBar(kdsNavEntries(), selected, onSelect, onLogout, onBackToWorkspaces)
}

/** The kitchen display's places the signed-in person may open. */
@Composable
private fun kdsNavEntries(): List<NavEntry<KdsSection>> {
    val user by org.koin.compose.koinInject<com.saporini.mobile_desktop.core.session.SessionManager>().currentUser.collectAsState()
    return KdsSection.allowed(user?.permissions.orEmpty()).map { NavEntry(it, it.label, it.icon(), it.filledIcon()) }
}

private fun KdsSection.icon(): ImageVector = when (this) {
    KdsSection.TICKETS -> Icons.Outlined.Receipt
    KdsSection.UPCOMING -> Icons.Outlined.Schedule
    KdsSection.MENU -> Icons.Outlined.RestaurantMenu
    KdsSection.HISTORY -> Icons.Outlined.History
    KdsSection.SHIFT -> Icons.AutoMirrored.Outlined.EventNote
}

private fun KdsSection.filledIcon(): ImageVector = when (this) {
    KdsSection.TICKETS -> Icons.Filled.Receipt
    KdsSection.UPCOMING -> Icons.Filled.Schedule
    KdsSection.MENU -> Icons.Filled.RestaurantMenu
    KdsSection.HISTORY -> Icons.Filled.History
    KdsSection.SHIFT -> Icons.AutoMirrored.Filled.EventNote
}
