package com.saporini.mobile_desktop.kds.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.pos.ui.shell.PhoneNavItem
import com.saporini.mobile_desktop.pos.ui.shell.TopBarNavItem
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceBottomBar
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceTopBar
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.pos_simple_logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun KdsTopBar(
    selected: KdsSection,
    onSelect: (KdsSection) -> Unit,
    onLogout: () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
) {
    WorkspaceTopBar(
        onLogout = onLogout,
        onBackToWorkspaces = onBackToWorkspaces,
        logo = {
            Image(
                painter = painterResource(Res.drawable.pos_simple_logo),
                contentDescription = "Saporini",
                modifier = Modifier.size(82.dp),
                contentScale = ContentScale.Fit
            )
        }
    ) {
        KdsSection.entries.forEach { section ->
            TopBarNavItem(
                label = section.label,
                icon = section.icon(),
                selected = selected == section,
                onClick = { onSelect(section) }
            )
        }
    }
}

@Composable
fun KdsBottomBar(
    selected: KdsSection,
    onSelect: (KdsSection) -> Unit,
    onLogout: () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
) {
    WorkspaceBottomBar(
        moreSelected = false,
        onLogout = onLogout,
        onBackToWorkspaces = onBackToWorkspaces
    ) {
        KdsSection.entries.forEach { section ->
            PhoneNavItem(
                modifier = Modifier.weight(1f),
                label = section.label,
                icon = if (selected == section) section.filledIcon() else section.icon(),
                selected = selected == section,
                onClick = { onSelect(section) }
            )
        }
    }
}

private fun KdsSection.icon(): ImageVector = when (this) {
    KdsSection.TICKETS -> Icons.Outlined.Receipt
    KdsSection.UPCOMING -> Icons.Outlined.Schedule
    KdsSection.MENU -> Icons.Outlined.RestaurantMenu
    KdsSection.HISTORY -> Icons.Outlined.History
}

private fun KdsSection.filledIcon(): ImageVector = when (this) {
    KdsSection.TICKETS -> Icons.Filled.Receipt
    KdsSection.UPCOMING -> Icons.Filled.Schedule
    KdsSection.MENU -> Icons.Filled.RestaurantMenu
    KdsSection.HISTORY -> Icons.Filled.History
}
