package com.saporini.mobile_desktop.pos.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.ui.PosSection

private val ActiveOlive = Color(0xFF4B522A)
private val Ink = Color(0xFF202426)
private val MutedInk = Color(0xFF3D4342)
private val OnlineGreen = Color(0xFF18C637)
private val AlertRed = Color(0xFFFF1414)
private val MenuSelectedBackground = Color(0xFFF3F5EF)
private val OverflowSections = listOf(
    PosSection.KITCHEN_STATUS,
    PosSection.SHIFT,
    PosSection.MY_SALES,
    PosSection.HISTORY
)

@Composable
fun PosTopBar(
    selected: PosSection,
    onSelect: (PosSection) -> Unit,
    onLogout: () -> Unit,
    logo: @Composable () -> Unit,
) {
    var menuSlotSection by remember { mutableStateOf(PosSection.MENU) }

    Surface(
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(start = 18.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(width = 52.dp, height = 42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    logo()
                }

                Spacer(Modifier.width(4.dp))

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

            Spacer(Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnlineStatus()
                LanguageSelector()
                NotificationButton(count = 3)
            }

            Spacer(Modifier.width(14.dp))
            TopBarSeparator()
            Spacer(Modifier.width(14.dp))

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

@Composable
private fun PosNavItem(
    section: PosSection,
    icon: ImageVector,
    selected: Boolean,
    onClick: (PosSection) -> Unit
) {
    val color = if (selected) ActiveOlive else Ink

    Box(
        modifier = Modifier
            .height(72.dp)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(
            onClick = { onClick(section) },
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = color)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(23.dp),
                    tint = color
                )
                Text(
                    text = section.label,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    letterSpacing = 0.sp,
                    color = color
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(if (selected) 118.dp else 118.dp)
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
            .padding(horizontal = 6.dp),
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
                    fontSize = 16.sp,
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
private fun OnlineStatus() {
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
        Text(
            text = "Online",
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.sp,
            color = Ink
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
private fun LanguageSelector() {
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

private fun PosSection.icon(): ImageVector = when (this) {
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
