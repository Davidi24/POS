package com.saporini.mobile_desktop.admin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.saporini.mobile_desktop.core.session.SessionManager
import org.koin.compose.koinInject
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.pos.ui.shell.PhoneNavItem
import com.saporini.mobile_desktop.pos.ui.shell.TopBarNavItem
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceBottomBar
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceTopBar
import com.saporini.mobile_desktop.core.theme.Inter
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.pos_simple_logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun AdminTopBar(
    selected: AdminSection,
    onSelect: (AdminSection) -> Unit,
    onLogout: () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
) {
    val user by koinInject<SessionManager>().currentUser.collectAsState()
    val canReadShifts = user?.permissions.orEmpty().any { it == "SHIFT_READ" || it == "SHIFT_MANAGE" }
    val primary = listOf(AdminSection.SHIFTS, AdminSection.INVENTORY, AdminSection.SUPPLIERS)
        .filter { it != AdminSection.SHIFTS || canReadShifts }
    var menuSlotSection by remember { mutableStateOf(AdminSection.DEVICES) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val navigationWidth = maxWidth
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
            primary.forEach { section ->
                TopBarNavItem(
                    label = section.label,
                    icon = section.icon(),
                    selected = selected == section,
                    onClick = { onSelect(section) }
                )
            }
            TopBarNavItem(
                label = menuSlotSection.label,
                icon = menuSlotSection.icon(),
                selected = selected == menuSlotSection,
                onClick = { onSelect(menuSlotSection) }
            )
            AdminMoreNavItem(
                menuSlotSection = menuSlotSection,
                selectedSection = selected,
                navigationWidth = navigationWidth,
                onSelect = { section ->
                    menuSlotSection = section
                    onSelect(section)
                }
            )
        }
    }
}

// Mirrors the existing POS More control, including its width tiers and promoted tab.
// Keep POS navigation unchanged when adjusting the Admin Hub menu.
private val AdminOverflowSections = listOf(
    AdminSection.SETTINGS,
    AdminSection.USERS,
    AdminSection.PERMISSIONS,
    AdminSection.AUDIT_LOGS
)
private val ActiveOlive = Color(0xFF4F7942)
private val Ink = Color(0xFF202426)
private val MenuSelectedBackground = Color(0xFFF3F5EF)

@Composable
private fun AdminMoreNavItem(
    menuSlotSection: AdminSection,
    selectedSection: AdminSection,
    navigationWidth: Dp,
    onSelect: (AdminSection) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dropdownSections = remember(menuSlotSection) {
        AdminOverflowSections.map { section ->
            if (section == menuSlotSection) AdminSection.DEVICES else section
        }
    }

    Box(
        modifier = Modifier
            .height(72.dp)
            .padding(horizontal = if (navigationWidth >= 1100.dp) 6.dp else 2.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Ink)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "More",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = when { navigationWidth >= 1100.dp -> 16.sp; navigationWidth >= 900.dp -> 14.sp; else -> 12.sp },
                    letterSpacing = 0.sp,
                    color = Ink
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Ink
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White),
            shape = RoundedCornerShape(10.dp),
            containerColor = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp
        ) {
            dropdownSections.forEach { section ->
                DropdownMenuItem(
                    text = {
                        AdminMenuText(
                            text = section.label,
                            selected = section == selectedSection,
                            fontSize = 16
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(section)
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(92.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                .background(Color.Transparent)
        )
    }
}

@Composable
private fun AdminMenuText(
    text: String,
    selected: Boolean,
    fontSize: Int = 14
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MenuSelectedBackground else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        fontFamily = Inter(),
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize.sp,
        color = if (selected) ActiveOlive else Ink
    )
}

@Composable
fun AdminBottomBar(
    selected: AdminSection,
    onSelect: (AdminSection) -> Unit,
    onLogout: () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
) {
    val phoneSections = listOf(
        AdminSection.INVENTORY,
        AdminSection.SUPPLIERS,
        AdminSection.DEVICES,
        AdminSection.SETTINGS
    )
    val user by koinInject<SessionManager>().currentUser.collectAsState()
    val canReadShifts = user?.permissions.orEmpty().any { it == "SHIFT_READ" || it == "SHIFT_MANAGE" }
    val moreSections = (if (canReadShifts) listOf(AdminSection.SHIFTS) else emptyList()) + listOf(AdminSection.USERS, AdminSection.PERMISSIONS, AdminSection.AUDIT_LOGS)
    val activeColor = Color(0xFF4F7942)
    val ink = Color(0xFF202426)

    WorkspaceBottomBar(
        moreSelected = selected in moreSections,
        onLogout = onLogout,
        onBackToWorkspaces = onBackToWorkspaces,
        moreItems = { closeMenu ->
            moreSections.forEach { section ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = section.label,
                            fontFamily = Inter(),
                            fontWeight = if (selected == section) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (selected == section) activeColor else ink
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = section.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (selected == section) activeColor else ink
                        )
                    },
                    onClick = {
                        closeMenu()
                        onSelect(section)
                    }
                )
            }
        }
    ) {
        phoneSections.forEach { section ->
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

private fun AdminSection.icon(): ImageVector = when (this) {
    AdminSection.SHIFTS -> Icons.Outlined.CalendarMonth
    AdminSection.INVENTORY -> Icons.Outlined.Inventory2
    AdminSection.SUPPLIERS -> Icons.Outlined.LocalShipping
    AdminSection.DEVICES -> Icons.Outlined.Devices
    AdminSection.SETTINGS -> Icons.Outlined.Settings
    AdminSection.USERS -> Icons.Outlined.People
    AdminSection.PERMISSIONS -> Icons.Outlined.Security
    AdminSection.AUDIT_LOGS -> Icons.Outlined.History
}

private fun AdminSection.filledIcon(): ImageVector = when (this) {
    AdminSection.SHIFTS -> Icons.Filled.CalendarMonth
    AdminSection.INVENTORY -> Icons.Filled.Inventory2
    AdminSection.SUPPLIERS -> Icons.Filled.LocalShipping
    AdminSection.DEVICES -> Icons.Filled.Devices
    AdminSection.SETTINGS -> Icons.Filled.Settings
    AdminSection.USERS -> Icons.Filled.People
    AdminSection.PERMISSIONS -> Icons.Filled.Security
    AdminSection.AUDIT_LOGS -> Icons.Filled.History
}
