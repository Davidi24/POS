package com.saporini.mobile_desktop.pos.ui.shell

import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.ui.PosSection
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.pos_simple_logo
import org.jetbrains.compose.resources.painterResource

private val ActiveOlive = Color(0xFF4F7942)
private val Ink = Color(0xFF202426)
private val MutedInk = Color(0xFF3D4342)
private val OnlineGreen = Color(0xFF18C637)
private val AlertRed = Color(0xFFFF1414)
private val MenuSelectedBackground = Color(0xFFF3F5EF)
internal val OverflowSections = listOf(
    PosSection.KITCHEN_STATUS,
    PosSection.SHIFT,
    PosSection.MY_SALES,
    PosSection.HISTORY
)

@Composable
fun PosPhoneTopBar(initials: String) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(Res.drawable.pos_simple_logo),
                contentDescription = "Saporini",
                modifier = Modifier.size(width = 24.dp, height = 40.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnlineStatus(textColor = ActiveOlive)
                LanguageSelector()
                com.saporini.mobile_desktop.notifications.NotificationBell(tint = MutedInk)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ActiveOlive),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
        HorizontalDivider(color = Color(0xFFEDEEEB))
    }
}

// How much room the top bar has. FULL is the desktop look and is never changed by the smaller tiers.
private enum class TopBarTier { FULL, MEDIUM, COMPACT }

private val LocalTopBarTier = androidx.compose.runtime.staticCompositionLocalOf { TopBarTier.FULL }

@Composable
fun PosTopBar(
    selected: PosSection,
    onSelect: (PosSection) -> Unit,
    onLogout: () -> Unit,
    logo: @Composable () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
) {
    var menuSlotSection by remember { mutableStateOf(PosSection.MENU) }

    WorkspaceTopBar(onLogout = onLogout, logo = logo, onBackToWorkspaces = onBackToWorkspaces) {
        PosNavItem(
            section = PosSection.TABLES,
            icon = Icons.Outlined.TableRestaurant,
            selected = selected == PosSection.TABLES,
            onClick = onSelect
        )
        PosNavItem(
            section = PosSection.ORDERS,
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            selected = selected == PosSection.ORDERS,
            onClick = onSelect
        )
        PosNavItem(
            section = PosSection.RESERVATIONS,
            icon = Icons.AutoMirrored.Outlined.EventNote,
            selected = selected == PosSection.RESERVATIONS,
            onClick = onSelect
        )
        PosNavItem(
            section = menuSlotSection,
            icon = menuSlotSection.icon(),
            selected = selected == menuSlotSection,
            onClick = onSelect
        )

        MoreNavItem(
            menuSlotSection = menuSlotSection,
            selectedSection = selected,
            onSelect = { section ->
                menuSlotSection = section
                onSelect(section)
            }
        )
    }
}

// Desktop top bar frame shared by the POS and KDS workspaces; each workspace passes in its own tabs.
@Composable
internal fun WorkspaceTopBar(
    onLogout: () -> Unit,
    logo: @Composable () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
    navItems: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val tier = when {
        maxWidth >= 1100.dp -> TopBarTier.FULL
        maxWidth >= 900.dp -> TopBarTier.MEDIUM
        else -> TopBarTier.COMPACT
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalTopBarTier provides tier) {
    Surface(
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(start = if (tier == TopBarTier.FULL) 18.dp else 10.dp, end = if (tier == TopBarTier.FULL) 18.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Only for users with more than one workspace: back to "Choose your workspace".
                onBackToWorkspaces?.let { back ->
                    IconButton(onClick = back, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Reply, "Back to workspaces", Modifier.size(26.dp), tint = Ink)
                    }
                    Spacer(Modifier.width(2.dp))
                }
                Box(
                    modifier = Modifier.size(width = if (tier == TopBarTier.FULL) 52.dp else 40.dp, height = 42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    logo()
                }

                Spacer(Modifier.width(4.dp))

                navItems()
            }

            Spacer(Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnlineStatus(showLabel = tier == TopBarTier.FULL)
                LanguageSelector()
                com.saporini.mobile_desktop.notifications.NotificationBell(tint = MutedInk)
            }

            Spacer(Modifier.width(if (tier == TopBarTier.FULL) 14.dp else 6.dp))
            TopBarSeparator()
            Spacer(Modifier.width(if (tier == TopBarTier.FULL) 14.dp else 6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LogoutButton(onClick = onLogout)
                ProfileChip(initials = "DK")
            }
        }
    }
    }
    }
}

@Composable
private fun PosNavItem(
    section: PosSection,
    icon: ImageVector,
    selected: Boolean,
    onClick: (PosSection) -> Unit
) {
    TopBarNavItem(label = section.label, icon = icon, selected = selected, onClick = { onClick(section) })
}

@Composable
internal fun TopBarNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) ActiveOlive else Ink
    val tier = LocalTopBarTier.current

    if (tier == TopBarTier.COMPACT) {
        // Narrow window: icon with a small label underneath, like the phone bar.
        Box(Modifier.height(72.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
            TextButton(
                onClick = onClick,
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = color)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = color)
                    Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = color, maxLines = 1)
                }
            }
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).width(64.dp).height(4.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(if (selected) ActiveOlive else Color.Transparent)
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .height(72.dp)
            .padding(horizontal = if (tier == TopBarTier.FULL) 6.dp else 2.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(
            onClick = onClick,
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = color)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (tier == TopBarTier.FULL) 10.dp else 7.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (tier == TopBarTier.FULL) 23.dp else 21.dp),
                    tint = color
                )
                Text(
                    text = label,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (tier == TopBarTier.FULL) 16.sp else 14.sp,
                    letterSpacing = 0.sp,
                    color = color
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(if (tier == TopBarTier.FULL) 118.dp else 96.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                .background(if (selected) ActiveOlive else Color.Transparent)
        )
    }
}

@Composable
private fun MoreNavItem(
    menuSlotSection: PosSection,
    selectedSection: PosSection,
    onSelect: (PosSection) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dropdownSections = remember(menuSlotSection) {
        OverflowSections.map { section ->
            if (section == menuSlotSection) PosSection.MENU else section
        }
    }

    Box(
        modifier = Modifier
            .height(72.dp)
            .padding(horizontal = if (LocalTopBarTier.current == TopBarTier.FULL) 6.dp else 2.dp),
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
                    fontSize = when (LocalTopBarTier.current) { TopBarTier.FULL -> 16.sp; TopBarTier.MEDIUM -> 14.sp; TopBarTier.COMPACT -> 12.sp },
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
                        MenuText(
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
private fun NotificationButton(count: Int) {
    Box(contentAlignment = Alignment.TopEnd) {
        IconButton(
            onClick = {},
            modifier = Modifier.size(42.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = "Notifications",
                modifier = Modifier.size(30.dp),
                tint = MutedInk
            )
        }
        Badge(
            modifier = Modifier.offset(x = (-1).dp, y = 2.dp),
            containerColor = AlertRed,
            contentColor = Color.White
        ) {
            Text(
                text = count.toString(),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
internal fun OnlineStatus(textColor: Color = Ink, showLabel: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(OnlineGreen)
        )
        if (showLabel) Text(
            text = "Online",
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.sp,
            color = textColor
        )
    }
}

@Composable
private fun TopBarSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(Color(0xFFE3E5E1))
    )
}

private enum class LanguageOption(
    val code: String,
    val label: String
) {
    ENGLISH("EN", "English"),
    SPANISH("ES", "Spanish"),
    ITALIAN("IT", "Italian")
}

@Composable
internal fun LanguageSelector() {
    var expanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(LanguageOption.ENGLISH) }

    Box {
        TextButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Ink)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MutedInk
                )
                Text(
                    text = selectedLanguage.code,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    color = Ink
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
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
            LanguageOption.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        MenuText(
                            text = language.label,
                            selected = language == selectedLanguage
                        )
                    },
                    onClick = {
                        selectedLanguage = language
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MenuText(
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

internal fun PosSection.icon(): ImageVector = when (this) {
    PosSection.TABLES -> Icons.Outlined.TableRestaurant
    PosSection.ORDERS -> Icons.AutoMirrored.Outlined.ReceiptLong
    PosSection.RESERVATIONS -> Icons.AutoMirrored.Outlined.EventNote
    PosSection.MENU -> Icons.Outlined.RestaurantMenu
    PosSection.KITCHEN_STATUS -> Icons.Outlined.RestaurantMenu
    PosSection.SHIFT -> Icons.AutoMirrored.Outlined.EventNote
    PosSection.MY_SALES -> Icons.AutoMirrored.Outlined.ReceiptLong
    PosSection.HISTORY -> Icons.AutoMirrored.Outlined.EventNote
}

@Composable
private fun LogoutButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Logout,
            contentDescription = "Log out",
            modifier = Modifier.size(22.dp),
            tint = MutedInk
        )
    }
}

@Composable
private fun ProfileChip(initials: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ActiveOlive),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White
            )
        }
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Ink
        )
    }
}
