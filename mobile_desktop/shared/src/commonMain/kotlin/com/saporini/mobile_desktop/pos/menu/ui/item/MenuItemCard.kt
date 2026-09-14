package com.saporini.mobile_desktop.pos.menu.ui.item

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import org.jetbrains.compose.resources.painterResource

private val MenuCardActiveOlive = Color(0xFF94A27F)
private val MenuCardTextInk = Color(0xFF222426)
private val MenuCardMutedInk = Color(0xFF747572)
private val MenuCardBorder = Color(0xFFE8E5E1)

internal fun phoneMenuItemHeight(fontScale: Float) =
    164.dp * fontScale.coerceAtLeast(1f)

@Composable
internal fun MenuItemCard(
    name: String,
    description: String,
    price: String,
    category: String,
    available: Boolean,
    selected: Boolean,
    isPhone: Boolean = false,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
    onExpand: () -> Unit = {},
    onEditMenuItem: () -> Unit = {},
    onEditVariants: () -> Unit = {},
    onEditOptions: () -> Unit = {}
) {
    if (isPhone) {
        PhoneMenuItemCard(
            name = name,
            description = description,
            price = price,
            category = category,
            available = available,
            selected = selected,
            modifier = modifier,
            isReordering = isReordering,
            onExpand = onExpand,
            onEditMenuItem = onEditMenuItem,
            onEditVariants = onEditVariants,
            onEditOptions = onEditOptions
        )
        return
    }

    val contentAlpha = if (available) 1f else 0.42f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(
                width = if (selected || isReordering) 2.dp else 1.dp,
                color = if (selected || isReordering) MenuCardActiveOlive else MenuCardBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.12f)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                Image(
                    painter = painterResource(Res.drawable.auth_login_img),
                    contentDescription = name,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = contentAlpha),
                    contentScale = ContentScale.Crop
                )

                AvailabilityButton(
                    available = available,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )

                if (available && !isReordering) {
                    var showEditMenu by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    ) {
                        EditItemButton(onClick = { showEditMenu = true })

                        DropdownMenu(
                            expanded = showEditMenu,
                            onDismissRequest = { showEditMenu = false },
                            offset = DpOffset(x = 0.dp, y = 8.dp),
                            modifier = Modifier.background(Color.White),
                            shape = RoundedCornerShape(10.dp),
                            containerColor = Color.White,
                            tonalElevation = 0.dp,
                            shadowElevation = 6.dp
                        ) {
                            EditMenuItem("Menu Item") {
                                showEditMenu = false
                                onEditMenuItem()
                            }
                            EditMenuItem("Variant") {
                                showEditMenu = false
                                onEditVariants()
                            }
                            EditMenuItem("Option") {
                                showEditMenu = false
                                onEditOptions()
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = name,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = 0.sp,
                color = MenuCardTextInk.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = description,
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                letterSpacing = 0.sp,
                color = MenuCardMutedInk.copy(alpha = contentAlpha),
                minLines = 2,
                maxLines = 2
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = price,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                letterSpacing = 0.sp,
                color = MenuCardTextInk.copy(alpha = contentAlpha)
            )
        }

        if (!isReordering) {
            ExpandImageButton(
                onClick = onExpand,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 0.dp)
            )
        }
    }
}

@Composable
private fun PhoneMenuItemCard(
    name: String,
    description: String,
    price: String,
    category: String,
    available: Boolean,
    selected: Boolean,
    modifier: Modifier,
    isReordering: Boolean,
    onExpand: () -> Unit,
    onEditMenuItem: () -> Unit,
    onEditVariants: () -> Unit,
    onEditOptions: () -> Unit
) {
    val contentAlpha = if (available) 1f else 0.5f
    var editMenuOpen by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .height(phoneMenuItemHeight(LocalDensity.current.fontScale))
            .clip(cardShape)
            .background(Color.White)
            .border(
                if (selected || isReordering) 2.dp else 1.dp,
                if (selected || isReordering) MenuCardActiveOlive else MenuCardBorder,
                cardShape
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.36f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
        ) {
            Image(
                painter = painterResource(Res.drawable.auth_login_img),
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = contentAlpha),
                contentScale = ContentScale.Crop
            )

            AvailabilityButton(
                available = available,
                compact = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text(
                text = category.uppercase(),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                letterSpacing = 0.5.sp,
                color = MenuCardMutedInk.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = name,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                color = MenuCardTextInk.copy(alpha = contentAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = description,
                fontFamily = Inter(),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MenuCardMutedInk.copy(alpha = contentAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = price,
                    modifier = Modifier.weight(1f),
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MenuCardTextInk.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!isReordering) {
                    if (available) {
                        Box {
                            PhoneMenuItemAction(Icons.Outlined.Edit, "Edit item") {
                                editMenuOpen = true
                            }

                            DropdownMenu(
                                expanded = editMenuOpen,
                                onDismissRequest = { editMenuOpen = false },
                                offset = DpOffset(0.dp, 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                containerColor = Color.White,
                                tonalElevation = 0.dp,
                                shadowElevation = 6.dp
                            ) {
                                EditMenuItem("Menu Item") {
                                    editMenuOpen = false
                                    onEditMenuItem()
                                }
                                EditMenuItem("Variant") {
                                    editMenuOpen = false
                                    onEditVariants()
                                }
                                EditMenuItem("Option") {
                                    editMenuOpen = false
                                    onEditOptions()
                                }
                            }
                        }
                    }

                    PhoneMenuItemAction(
                        icon = Icons.Outlined.ZoomOutMap,
                        label = "View item details",
                        onClick = onExpand
                    )
                }
            }
        }
    }
}

@Composable
private fun EditMenuItem(
    label: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MenuCardTextInk
            )
        },
        onClick = onClick
    )
}

@Composable
private fun PhoneMenuItemAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color(0xFFF7F7F5), RoundedCornerShape(7.dp))
                .border(1.dp, MenuCardBorder, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(15.dp),
                tint = MenuCardTextInk
            )
        }
    }
}

@Composable
private fun AvailabilityButton(
    available: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(if (available) Color.White else Color(0xFF6D6D6D))
            .clickable {}
            .padding(
                horizontal = if (compact) 6.dp else 12.dp,
                vertical = if (compact) 4.dp else 6.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (available) "Available" else "Unavailable",
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 10.sp else 13.sp,
            letterSpacing = 0.sp,
                color = if (available) {
                    MenuCardActiveOlive
                } else {
                    Color.White
                },
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ExpandImageButton(
    onClick: () -> Unit,
    isPhone: Boolean = false,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(if (isPhone) 40.dp else 29.dp)
            .shadow(2.dp, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.92f))
    ) {
        Icon(
            imageVector = Icons.Outlined.ZoomOutMap,
            contentDescription = "Expand image",
            modifier = Modifier.size(18.dp),
            tint = MenuCardTextInk
        )
    }
}

@Composable
private fun EditItemButton(
    onClick: () -> Unit,
    isPhone: Boolean = false,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(if (isPhone) 40.dp else 29.dp)
            .shadow(2.dp, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.92f))
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Edit item",
            modifier = Modifier.size(16.dp),
            tint = MenuCardTextInk
        )
    }
}
