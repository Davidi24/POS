package com.saporini.mobile_desktop.pos.menu.ui.item

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
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
private val MenuCardSkeletonBlock = Color(0xFFDADADA)
private val MenuCardSkeletonLight = Color(0xFFE8E8E8)
private val MenuCardDanger = Color(0xFFB13A2F)

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
    canEdit: Boolean = true,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
    onExpand: () -> Unit = {},
    onEditMenuItem: () -> Unit = {},
    onEditVariants: () -> Unit = {},
    onEditOptions: () -> Unit = {},
    onToggleAvailability: () -> Unit = {}
) {
    if (isPhone) {
        PhoneMenuItemCard(
            name = name,
            description = description,
            price = price,
            category = category,
            available = available,
            selected = selected,
            canEdit = canEdit,
            modifier = modifier,
            isReordering = isReordering,
            onExpand = onExpand,
            onEditMenuItem = onEditMenuItem,
            onEditVariants = onEditVariants,
            onEditOptions = onEditOptions,
            onToggleAvailability = onToggleAvailability
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
                    onToggle = onToggleAvailability,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )

                if (available && !isReordering && canEdit) {
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
    canEdit: Boolean,
    modifier: Modifier,
    isReordering: Boolean,
    onExpand: () -> Unit,
    onEditMenuItem: () -> Unit,
    onEditVariants: () -> Unit,
    onEditOptions: () -> Unit,
    onToggleAvailability: () -> Unit
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
                onToggle = onToggleAvailability,
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
                    if (available && canEdit) {
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

/**
 * Lays out [rows] rows of shimmering placeholder cards shaped like
 * [MenuItemCard], as a stand-in while items are loading.
 */
@Composable
internal fun MenuItemCardSkeletonGrid(
    columns: Int,
    isPhone: Boolean,
    modifier: Modifier = Modifier,
    rows: Int = if (isPhone) 3 else 2
) {
    val transition = rememberInfiniteTransition(label = "menu-item-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.48f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "menu-item-skeleton-alpha"
    )
    val phoneCardHeight = phoneMenuItemHeight(LocalDensity.current.fontScale)

    Column(
        modifier = modifier.alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(rows) {
            if (isPhone) {
                MenuItemCardSkeleton(
                    isPhone = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(phoneCardHeight)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    repeat(columns) {
                        MenuItemCardSkeleton(
                            isPhone = false,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MenuItemCardSkeleton(
    isPhone: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isPhone) {
        PhoneMenuItemCardSkeleton(modifier)
        return
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, MenuCardBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.12f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MenuCardSkeletonBlock)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .width(74.dp)
                        .height(24.dp)
                        .background(MenuCardSkeletonLight, RoundedCornerShape(50))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(29.dp)
                        .background(MenuCardSkeletonLight, RoundedCornerShape(7.dp))
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.68f)
                    .height(20.dp)
                    .background(MenuCardSkeletonBlock, RoundedCornerShape(5.dp))
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(13.dp)
                    .background(MenuCardSkeletonBlock, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(13.dp)
                    .background(MenuCardSkeletonBlock, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.32f)
                    .height(18.dp)
                    .background(MenuCardSkeletonBlock, RoundedCornerShape(5.dp))
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 2.dp)
                .size(29.dp)
                .background(MenuCardSkeletonLight, RoundedCornerShape(7.dp))
        )
    }
}

@Composable
private fun PhoneMenuItemCardSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, MenuCardBorder, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.36f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(MenuCardSkeletonBlock)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .width(40.dp)
                    .height(16.dp)
                    .background(MenuCardSkeletonLight, RoundedCornerShape(50))
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .background(MenuCardSkeletonLight, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .height(16.dp)
                    .background(MenuCardSkeletonBlock, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(16.dp)
                    .background(MenuCardSkeletonBlock, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(MenuCardSkeletonBlock, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp)
                    .background(MenuCardSkeletonBlock, RoundedCornerShape(4.dp))
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(17.dp)
                        .background(MenuCardSkeletonBlock, RoundedCornerShape(4.dp))
                )
                Box(
                    Modifier
                        .size(36.dp)
                        .background(MenuCardSkeletonLight, RoundedCornerShape(8.dp))
                )
                Box(
                    Modifier
                        .size(36.dp)
                        .background(MenuCardSkeletonLight, RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun EditMenuItem(
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (danger) MenuCardDanger else MenuCardTextInk
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
internal fun AvailabilityButton(
    available: Boolean,
    compact: Boolean = false,
    onToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(if (available) Color.White else Color(0xFF6D6D6D))
            .clickable(onClick = onToggle)
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
