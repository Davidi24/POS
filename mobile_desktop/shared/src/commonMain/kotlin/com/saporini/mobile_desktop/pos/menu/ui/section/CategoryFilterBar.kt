package com.saporini.mobile_desktop.pos.menu.ui.section

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.ui.MenuCategory

private val ActiveOlive = Color(0xFF94A27F)
private val TextInk = Color(0xFF222426)
private val Border = Color(0xFFE8E5E1)
private val SkeletonBlock = Color(0xFFDADADA)
private val SkeletonLight = Color(0xFFE8E8E8)

@Composable
internal fun CategoryButtons(
    items: List<MenuCategory>,
    selected: String,
    canManage: Boolean,
    onSelected: (String) -> Unit,
    onManageSections: () -> Unit
) {
    val visibleCategoryLimit = 5
    var overflowExpanded by remember { mutableStateOf(false) }
    var promotedCategory by remember { mutableStateOf<String?>(null) }
    val hasOverflow = items.size > visibleCategoryLimit
    val fixedItems = if (hasOverflow) {
        items.take(visibleCategoryLimit - 1)
    } else {
        items
    }
    val visibleTail = if (hasOverflow) {
        items.firstOrNull { it.name == selected && it !in fixedItems }
            ?: items.firstOrNull { it.name == promotedCategory && it !in fixedItems }
            ?: items[visibleCategoryLimit - 1]
    } else {
        null
    }
    val visibleItems = if (hasOverflow) fixedItems + listOfNotNull(visibleTail) else items
    val overflowItems = items.filterNot { it in visibleItems }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        visibleItems.forEach { item ->
            val isSelected = item.name == selected
            TextButton(
                onClick = { onSelected(item.name) },
                modifier = Modifier
                    .height(46.dp)
                    .width(if (item.name == "All") 78.dp else 128.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ActiveOlive else Color.White)
                    .border(1.dp, Border, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSelected) Color.White else TextInk
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = item.icon.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isSelected) Color.White else TextInk
                    )
                    Text(
                        text = item.name,
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (hasOverflow) {
            Box {
                IconButton(
                    onClick = { overflowExpanded = true },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, Border, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "More categories",
                        modifier = Modifier.size(24.dp),
                        tint = TextInk
                    )
                }

                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                    offset = DpOffset(x = 0.dp, y = 8.dp),
                    modifier = Modifier.background(Color.White),
                    shape = RoundedCornerShape(10.dp),
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 6.dp
                ) {
                    overflowItems.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = item.name,
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = TextInk
                                )
                            },
                            onClick = {
                                promotedCategory = item.name
                                overflowExpanded = false
                                onSelected(item.name)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = item.icon.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = TextInk
                                )
                            }
                        )
                    }
                }
            }
        }

        if (canManage) {
            IconButton(
                onClick = onManageSections,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color.White).border(1.dp, Border, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit menu sections",
                    modifier = Modifier.size(19.dp),
                    tint = TextInk
                )
            }
        }
    }
}

/**
 * Phone equivalent of [CategoryButtons]: a horizontally scrolling row of
 * section chips plus the manage-sections icon.
 */
@Composable
internal fun PhoneCategoryFilterRow(
    sections: List<MenuCategory>,
    selectedCategory: String,
    isReorderingItems: Boolean,
    canManage: Boolean,
    onSelect: (String) -> Unit,
    onManageSections: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(end = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sections.forEach { section ->
            val selected = selectedCategory == section.name
            TextButton(
                onClick = { if (!isReorderingItems) onSelect(section.name) },
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .background(
                        if (selected) ActiveOlive else Color.White,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (selected) ActiveOlive else Border,
                        RoundedCornerShape(8.dp)
                    ),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (selected) Color.White else TextInk
                )
            ) {
                Icon(
                    imageVector = section.icon.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) Color.White else TextInk
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    section.name,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
        if (canManage) {
            IconButton(
                onClick = onManageSections,
                enabled = !isReorderingItems,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, Border, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit menu sections",
                    modifier = Modifier.size(18.dp),
                    tint = if (isReorderingItems) {
                        TextInk.copy(alpha = 0.35f)
                    } else {
                        TextInk
                    }
                )
            }
        }
    }
}

/**
 * Shimmering placeholder chips shaped like the section filter row
 * ([CategoryButtons] on desktop, [PhoneCategoryFilterRow] on phone), shown
 * while a menu's content is still loading.
 */
@Composable
internal fun SectionFilterSkeleton(
    isPhone: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "section-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.48f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "section-skeleton-alpha"
    )
    val chipHeight = if (isPhone) 44.dp else 46.dp
    val chipWidths = if (isPhone) {
        listOf(64.dp, 100.dp, 100.dp, 96.dp)
    } else {
        listOf(78.dp, 128.dp, 128.dp, 128.dp, 128.dp)
    }

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .alpha(alpha),
        horizontalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 10.dp)
    ) {
        chipWidths.forEach { width ->
            Box(
                modifier = Modifier
                    .height(chipHeight)
                    .width(width)
                    .background(SkeletonBlock, RoundedCornerShape(8.dp))
            )
        }
        Box(
            modifier = Modifier
                .size(chipHeight)
                .background(SkeletonLight, RoundedCornerShape(8.dp))
        )
    }
}
