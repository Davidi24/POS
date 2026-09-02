package com.saporini.mobile_desktop.pos.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

private val BottomBarActive = Color(0xFF94A27F)
private val BottomBarInk = Color(0xFF202426)
private val BottomBarMuted = Color(0xFF737A77)
private val BottomBarDivider = Color(0xFFE8EBE5)

private val PrimaryPhoneSections = listOf(
    PosSection.TABLES,
    PosSection.ORDERS,
    PosSection.RESERVATIONS,
    PosSection.MENU
)

@Composable
fun PosBottomBar(
    selected: PosSection,
    onSelect: (PosSection) -> Unit,
    onLogout: () -> Unit
) {
    var moreExpanded by remember { mutableStateOf(false) }
    val moreSelected = selected in OverflowSections

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(74.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, BottomBarDivider)
        ) {}

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(74.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PrimaryPhoneSections.forEach { section ->
                PhoneNavItem(
                    modifier = Modifier.weight(1f),
                    label = section.label,
                    icon = if (selected == section) section.filledPhoneIcon() else section.icon(),
                    selected = selected == section,
                    onClick = { onSelect(section) }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter
            ) {
                PhoneNavItem(
                    modifier = Modifier.fillMaxWidth(),
                    label = "More",
                    icon = if (moreSelected) Icons.Filled.MoreHoriz else Icons.Outlined.MoreHoriz,
                    selected = moreSelected,
                    onClick = { moreExpanded = true }
                )

                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { moreExpanded = false },
                    modifier = Modifier.width(214.dp),
                    shape = RoundedCornerShape(18.dp),
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp
                ) {
                    OverflowSections.forEach { section ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = section.label,
                                    fontFamily = Inter(),
                                    fontWeight = if (selected == section) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Medium
                                    },
                                    fontSize = 14.sp,
                                    color = if (selected == section) BottomBarActive else BottomBarInk
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = section.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (selected == section) BottomBarActive else BottomBarMuted
                                )
                            },
                            onClick = {
                                moreExpanded = false
                                onSelect(section)
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Sign out",
                                fontFamily = Inter(),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = BottomBarInk
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = BottomBarMuted
                            )
                        },
                        onClick = {
                            moreExpanded = false
                            onLogout()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneNavItem(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) BottomBarActive else BottomBarMuted,
        label = "bottom-nav-content-$label"
    )

    Box(
        modifier = modifier
            .height(74.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(3.dp)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                .background(if (selected) BottomBarActive else Color.Transparent)
        )

        Column(
            modifier = Modifier.padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
            Text(
                text = label,
                fontFamily = Inter(),
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                color = contentColor
            )
        }
    }
}

private fun PosSection.filledPhoneIcon(): ImageVector = when (this) {
    PosSection.TABLES -> Icons.Filled.TableRestaurant
    PosSection.ORDERS -> Icons.AutoMirrored.Filled.ReceiptLong
    PosSection.RESERVATIONS -> Icons.AutoMirrored.Filled.EventNote
    PosSection.MENU -> Icons.Filled.RestaurantMenu
    else -> icon()
}
