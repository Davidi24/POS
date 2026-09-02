package com.saporini.mobile_desktop.pos.menu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.CormorantGaramond
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.brand_logo
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private val CoverOlive = Color(0xFF94A27F)
private val CoverGold = Color(0xFFC79435)
private val CoverInk = Color(0xFF232422)
private val CoverMuted = Color(0xFF6D706B)

@Composable
fun MenuCoverUi(
    state: MenuUiState,
    canManageMenus: Boolean,
    onOpenMenu: (String) -> Unit,
    onAddMenu: () -> Unit,
    onEditMenu: (Menu) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    hiddenMenuIds: Set<String> = emptySet(),
    deletingMenuId: String? = null
) {
    var isReordering by remember { mutableStateOf(false) }
    var orderedMenus by remember(state.menus, hiddenMenuIds) {
        mutableStateOf(state.menus.filter { it.id !in hiddenMenuIds })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Choose your Menu",
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = CoverInk
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gentle breathing scale only -- no colored glow, no border while
                // active. Clean solid pill, same spirit as the original toggle.
                val reorderPulse = rememberInfiniteTransition(label = "reorder-save-pulse")
                val reorderPulseScale by reorderPulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.045f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 750),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "reorder-save-pulse-scale"
                )
                if (canManageMenus) {
                TextButton(
                    onClick = { isReordering = !isReordering },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (isReordering) CoverInk else Color.White,
                        contentColor = if (isReordering) Color.White else CoverInk
                    ),
                    modifier = Modifier
                        .graphicsLayer {
                            if (isReordering) {
                                scaleX = reorderPulseScale
                                scaleY = reorderPulseScale
                            }
                        }
                        .then(
                            if (isReordering) {
                                Modifier.shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    clip = false,
                                    ambientColor = Color(0x33141414),
                                    spotColor = Color(0x40141414)
                                )
                            } else {
                                Modifier.border(1.dp, Color(0xFFDDD9D2), RoundedCornerShape(8.dp))
                            }
                        ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = if (isReordering) Icons.Filled.Check else Icons.Outlined.OpenWith,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isReordering) "Save Changes" else "Edit Order",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                }
                if (canManageMenus) {
                TextButton(
                    onClick = onAddMenu,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = Color(0xFF94A27F),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Create Menu",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        state.errorMessage?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF2F0), RoundedCornerShape(9.dp))
                    .border(1.dp, Color(0xFFF3C7C1), RoundedCornerShape(9.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    modifier = Modifier.weight(1f),
                    fontFamily = Inter(),
                    color = Color(0xFFB13A2F)
                )
                TextButton(onClick = onRetry) {
                    Text("Retry", fontFamily = Inter(), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        when {
            state.isLoading -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    MenuCoverSkeletonGrid(
                        columns = menuGridColumns(maxWidth),
                        availableHeight = maxHeight
                    )
                }
            }

            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    NonScrollingMenuGrid(
                        menus = orderedMenus,
                        columns = menuGridColumns(maxWidth),
                        availableHeight = maxHeight,
                        isReordering = isReordering,
                        canManageMenus = canManageMenus,
                        deletingMenuId = deletingMenuId,
                        onReorder = { orderedMenus = it },
                        onOpenMenu = onOpenMenu,
                        onEditMenu = onEditMenu,
                        onAddMenu = onAddMenu
                    )
                }
            }
        }
    }
}

private fun menuGridColumns(width: androidx.compose.ui.unit.Dp): Int = when {
    width >= 1320.dp -> 4
    width >= 960.dp -> 3
    width >= 640.dp -> 2
    else -> 1
}

@Composable
private fun MenuCoverSkeletonGrid(
    columns: Int,
    availableHeight: androidx.compose.ui.unit.Dp
) {
    val transition = rememberInfiniteTransition(label = "menu-cover-skeleton")
    val skeletonAlpha by transition.animateFloat(
        initialValue = 0.48f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "menu-cover-skeleton-alpha"
    )
    val coverHeight = minOf(availableHeight, 440.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(skeletonAlpha),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        repeat(2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverHeight),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                repeat(columns) {
                    MenuCoverSkeleton(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuCoverSkeleton(
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 0.dp
    )
    val block = Color(0xFFDADADA)
    val lightBlock = Color(0xFFE8E8E8)

    Box(
        modifier = modifier
            .padding(top = 8.dp, end = 28.dp)
            .clip(shape)
            .background(Color(0xFFF3F3F3))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(38.dp)
                .background(Color(0xFFCCCCCC))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 72.dp, end = 22.dp, top = 30.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.52f)
                    .height(42.dp)
                    .background(block, RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(32.dp)
                    .background(block, RoundedCornerShape(7.dp))
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(26.dp)
                    .background(block, RoundedCornerShape(7.dp))
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .width(94.dp)
                    .height(28.dp)
                    .background(lightBlock, RoundedCornerShape(5.dp))
            )
            Spacer(Modifier.weight(1f))
            repeat(2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(block, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .background(block, RoundedCornerShape(5.dp))
                    )
                }
            }
            HorizontalDivider(color = block)
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(18.dp)
                    .background(block, RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
private fun NonScrollingMenuGrid(
    menus: List<Menu>,
    columns: Int,
    availableHeight: androidx.compose.ui.unit.Dp,
    isReordering: Boolean,
    canManageMenus: Boolean,
    deletingMenuId: String?,
    onReorder: (List<Menu>) -> Unit,
    onOpenMenu: (String) -> Unit,
    onEditMenu: (Menu) -> Unit,
    onAddMenu: () -> Unit
) {
    val itemCount = menus.size + if (isReordering || !canManageMenus) 0 else 1
    val rowCount = if (itemCount == 0) 0 else (itemCount + columns - 1) / columns
    val horizontalGap = 24.dp
    val verticalGap = 20.dp
    val coverHeight = minOf(availableHeight, 440.dp)
    val compact = coverHeight < 390.dp
    val contentHeight = if (rowCount == 0) {
        0.dp
    } else {
        coverHeight * rowCount + verticalGap * (rowCount - 1)
    }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var settlingId by remember { mutableStateOf<String?>(null) }
    var draggedPosition by remember { mutableStateOf(Offset.Zero) }
    var gestureOrder by remember { mutableStateOf<List<Menu>?>(null) }
    val settleX = remember { Animatable(0f) }
    val settleY = remember { Animatable(0f) }
    val settleScope = rememberCoroutineScope()
    val latestMenus by rememberUpdatedState(menus)
    val latestOnReorder by rememberUpdatedState(onReorder)

    LaunchedEffect(isReordering) {
        if (!isReordering) {
            draggingId = null
            settlingId = null
            gestureOrder = null
            settleX.stop()
            settleY.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight)
        ) {
            val cardWidth = (maxWidth - horizontalGap * (columns - 1)) / columns
            val density = LocalDensity.current
            val cardWidthPx = with(density) { cardWidth.toPx() }
            val coverHeightPx = with(density) { coverHeight.toPx() }
            val horizontalGapPx = with(density) { horizontalGap.toPx() }
            val verticalGapPx = with(density) { verticalGap.toPx() }

            fun slotPosition(index: Int): Offset {
                val column = index % columns
                val row = index / columns
                return Offset(
                    x = column * (cardWidthPx + horizontalGapPx),
                    y = row * (coverHeightPx + verticalGapPx)
                )
            }

            fun nearestSlotIndex(position: Offset, menuCount: Int): Int? {
                if (menuCount == 0) return null
                val draggedCenter = position + Offset(cardWidthPx / 2f, coverHeightPx / 2f)
                return (0 until menuCount).minByOrNull { index ->
                    val slotCenter = slotPosition(index) + Offset(cardWidthPx / 2f, coverHeightPx / 2f)
                    (slotCenter - draggedCenter).getDistance()
                }
            }

            menus.forEachIndexed { itemIndex, menu ->
                key(menu.id) {
                    val targetSlot = slotPosition(itemIndex)
                    val targetOffset = IntOffset(targetSlot.x.roundToInt(), targetSlot.y.roundToInt())
                    val animatedSlot by animateIntOffsetAsState(
                        targetValue = targetOffset,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "menu-slot-${menu.id}"
                    )
                    val isDragging = isReordering && draggingId == menu.id
                    val isSettling = settlingId == menu.id
                    val isDeleting = deletingMenuId == menu.id
                    val deleteAlpha by animateFloatAsState(
                        targetValue = if (isDeleting) 0f else 1f,
                        animationSpec = tween(durationMillis = 420),
                        label = "menu-delete-alpha-${menu.id}"
                    )
                    val deleteScale by animateFloatAsState(
                        targetValue = if (isDeleting) 0.82f else 1f,
                        animationSpec = tween(durationMillis = 420),
                        label = "menu-delete-scale-${menu.id}"
                    )

                    fun releaseDraggedMenu() {
                        if (draggingId != menu.id) return
                        val releasedAt = draggedPosition
                        val currentIndex = (gestureOrder ?: latestMenus)
                            .indexOfFirst { it.id == menu.id }
                        val destination = slotPosition(currentIndex.coerceAtLeast(0))
                        gestureOrder = null

                        settleScope.launch {
                            settleX.snapTo(releasedAt.x)
                            settleY.snapTo(releasedAt.y)
                            if (draggingId != menu.id) return@launch
                            draggingId = null
                            settlingId = menu.id
                            val xAnimation = launch {
                                settleX.animateTo(
                                    destination.x,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            val yAnimation = launch {
                                settleY.animateTo(
                                    destination.y,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            joinAll(xAnimation, yAnimation)
                            if (settlingId == menu.id) {
                                settlingId = null
                            }
                        }
                    }

                    MenuBookCover(
                        menu = menu,
                        theme = coverTheme(menu, itemIndex),
                        compact = compact,
                        isReordering = isReordering,
                        isDragging = isDragging,
                        canManageMenus = canManageMenus,
                        onClick = { if (!isReordering) onOpenMenu(menu.id) },
                        onEdit = { onEditMenu(menu) },
                        modifier = Modifier
                            .width(cardWidth)
                            .height(coverHeight)
                            .graphicsLayer {
                                alpha = deleteAlpha
                                scaleX = deleteScale
                                scaleY = deleteScale
                            }
                            .zIndex(if (isDragging || isSettling) 10f else 0f)
                            .offset {
                                when {
                                    isDragging -> IntOffset(
                                        draggedPosition.x.roundToInt(),
                                        draggedPosition.y.roundToInt()
                                    )
                                    isSettling -> IntOffset(
                                        settleX.value.roundToInt(),
                                        settleY.value.roundToInt()
                                    )
                                    else -> animatedSlot
                                }
                            }
                            .then(
                                if (isReordering) {
                                    Modifier.pointerInput(
                                        menu.id,
                                        cardWidthPx,
                                        coverHeightPx,
                                        horizontalGapPx,
                                        verticalGapPx
                                    ) {
                                        detectDragGestures(
                                            onDragStart = {
                                                settlingId = null
                                                settleScope.launch {
                                                    settleX.stop()
                                                    settleY.stop()
                                                }
                                                val currentIndex = latestMenus.indexOfFirst { it.id == menu.id }
                                                if (currentIndex >= 0) {
                                                    gestureOrder = latestMenus
                                                    draggedPosition = slotPosition(currentIndex)
                                                    draggingId = menu.id
                                                }
                                            },
                                            onDragEnd = ::releaseDraggedMenu,
                                            onDragCancel = ::releaseDraggedMenu,
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                if (draggingId != menu.id) return@detectDragGestures

                                                draggedPosition += dragAmount
                                                val currentOrder = gestureOrder ?: latestMenus
                                                val fromIndex = currentOrder.indexOfFirst { it.id == menu.id }
                                                val toIndex = nearestSlotIndex(
                                                    position = draggedPosition,
                                                    menuCount = currentOrder.size
                                                )

                                                if (fromIndex >= 0 && toIndex != null && toIndex != fromIndex) {
                                                    val reordered = currentOrder.toMutableList()
                                                    val draggedMenu = reordered.removeAt(fromIndex)
                                                    reordered.add(toIndex, draggedMenu)
                                                    gestureOrder = reordered
                                                    latestOnReorder(reordered)
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }

            if (!isReordering && canManageMenus) {
                val addSlot = slotPosition(menus.size)
                AddMenuCover(
                    compact = compact,
                    onClick = onAddMenu,
                    modifier = Modifier
                        .offset {
                            IntOffset(addSlot.x.roundToInt(), addSlot.y.roundToInt())
                        }
                        .width(cardWidth)
                        .height(coverHeight)
                )
            }
        }
    }
}

@Composable
fun MenuCoverPreview(
    name: String,
    description: String,
    active: Boolean,
    availableFrom: String?,
    availableUntil: String?,
    color: String,
    modifier: Modifier = Modifier
) {
    val previewMenu = Menu(
        id = "menu-preview",
        restaurant = null,
        code = "PREVIEW",
        name = name.trim().ifEmpty { "New Menu" },
        description = description.trim().takeIf { it.isNotEmpty() },
        active = active,
        displayOrder = 0,
        availableFrom = availableFrom,
        availableUntil = availableUntil,
        availableFromDate = null,
        availableUntilDate = null,
        color = color,
        itemCount = 0,
        createdBy = null,
        updatedBy = null,
        createdAt = null,
        updatedAt = null,
        sections = emptyList()
    )

    MenuBookCover(
        menu = previewMenu,
        theme = coverTheme(previewMenu, 0),
        compact = true,
        isReordering = false,
        showActions = false,
        onClick = {},
        onEdit = {},
        modifier = modifier
    )
}

@Composable
private fun MenuBookCover(
    menu: Menu,
    theme: MenuCoverTheme,
    compact: Boolean,
    isReordering: Boolean,
    isDragging: Boolean = false,
    showActions: Boolean = true,
    canManageMenus: Boolean = true,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 0.dp
    )
    val itemCount = menu.itemCount ?: 0
    var showDescription by remember(menu.id) { mutableStateOf(false) }
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.035f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "menu-drag-scale-${menu.id}"
    )
    val dragElevation by animateDpAsState(
        targetValue = if (isDragging) 26.dp else 13.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "menu-drag-elevation-${menu.id}"
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, end = 28.dp)
                .graphicsLayer {
                    scaleX = dragScale
                    scaleY = dragScale
                }
                .shadow(
                    elevation = dragElevation,
                    shape = cardShape,
                    clip = false,
                    ambientColor = if (isDragging) CoverOlive.copy(alpha = 0.55f) else Color(0x33263119),
                    spotColor = if (isDragging) CoverOlive.copy(alpha = 0.55f) else Color(0x44141414)
                )
                .clip(cardShape)
                .then(
                    if (isReordering) {
                        Modifier.border(
                            width = if (isDragging) 3.dp else 2.dp,
                            color = if (isDragging) CoverOlive else CoverOlive.copy(alpha = 0.55f),
                            shape = cardShape
                        )
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (isReordering || !showActions) {
                        Modifier
                    } else {
                        Modifier.clickable(onClick = onClick)
                    }
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(theme.background)
                var x = -size.height * 0.08f
                while (x < size.width) {
                    drawLine(
                        color = theme.texture.copy(alpha = 0.16f),
                        start = Offset(x, 0f),
                        end = Offset(x + size.height * 0.14f, size.height),
                        strokeWidth = 1.2f
                    )
                    x += 8f
                }
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.06f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(if (compact) 38.dp else 44.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF151616),
                                Color(0xFF393B39),
                                Color(0xFF111212)
                            )
                        )
                    )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(
                        color = Color(0xFF050505),
                        start = Offset(size.width - 1f, 0f),
                        end = Offset(size.width - 1f, size.height),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color(0xFF898989),
                        start = Offset(size.width - 9f, 0f),
                        end = Offset(size.width - 9f, size.height),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (compact) 62.dp else 72.dp,
                        end = if (compact) 16.dp else 22.dp,
                        top = if (compact) 18.dp else 26.dp,
                        bottom = if (compact) 14.dp else 20.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Image(
                        painter = painterResource(Res.drawable.brand_logo),
                        contentDescription = "Saporini Italiano logo",
                        modifier = Modifier.size(
                            width = if (compact) 108.dp else 142.dp,
                            height = if (compact) 44.dp else 58.dp
                        ),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(if (compact) 8.dp else 13.dp))
                    Text(
                        text = coverTitle(menu.name),
                        fontFamily = CormorantGaramond(),
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 27.sp else 35.sp,
                        lineHeight = if (compact) 25.sp else 32.sp,
                        letterSpacing = 1.sp,
                        color = theme.ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
                    ) {
                        CoverMetadataRow(
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (compact) 16.dp else 20.dp)
                                )
                            },
                            text = coverSchedule(menu),
                            color = theme.ink,
                            compact = compact
                        )
                        CoverMetadataRow(
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (compact) 16.dp else 20.dp)
                                )
                            },
                            text = "$itemCount ${if (itemCount == 1) "item" else "items"}",
                            color = theme.ink,
                            compact = compact
                        )
                    }

                    Spacer(Modifier.height(if (compact) 14.dp else 20.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HorizontalDivider(color = theme.ink.copy(alpha = 0.7f))
                        Spacer(Modifier.height(if (compact) 9.dp else 15.dp))
                        Text(
                            text = "OPEN MENU  →",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 12.sp else 15.sp,
                            letterSpacing = 0.4.sp,
                            color = theme.ink
                        )
                    }
                }
            }
        }

        if (showActions && !isReordering) {
            if (canManageMenus) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = (-2).dp)
                        .zIndex(3f)
                        .size(if (compact) 34.dp else 40.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit ${menu.name}",
                        modifier = Modifier.size(if (compact) 16.dp else 19.dp),
                        tint = theme.ink
                    )
                }
            }

            if (!menu.description.isNullOrBlank()) {
                IconButton(
                    onClick = { showDescription = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(
                            x = (-4).dp,
                            y = if (canManageMenus) {
                                (if (compact) 34.dp else 40.dp) + 6.dp
                            } else {
                                (-2).dp
                            }
                        )
                        .zIndex(3f)
                        .size(if (compact) 34.dp else 40.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "About ${menu.name}",
                        modifier = Modifier.size(if (compact) 16.dp else 19.dp),
                        tint = theme.ink
                    )
                }
            }
        } else if (showActions) {
            // Reorder mode: replace the edit/info icons with a clear
            // "this is movable" grab-handle badge, shown on every card.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 10.dp)
                    .zIndex(3f)
                    .shadow(4.dp, RoundedCornerShape(50)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isDragging) CoverOlive else Color.White)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenWith,
                        contentDescription = "Drag to move ${menu.name}",
                        modifier = Modifier.size(15.dp),
                        tint = if (isDragging) Color.White else CoverOlive
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (isDragging) "Moving…" else "Drag",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = if (isDragging) Color.White else CoverOlive
                    )
                }
            }
        }
    }

    if (showDescription) {
        MenuDescriptionDialog(
            menu = menu,
            onDismiss = { showDescription = false }
        )
    }
}

@Composable
private fun MenuDescriptionDialog(
    menu: Menu,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = CoverOlive
                )
            }
        },
        title = {
            Text(
                text = menu.name,
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = CoverInk
            )
        },
        text = {
            Text(
                text = menu.description.orEmpty(),
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = CoverMuted
            )
        }
    )
}



@Composable
private fun CoverMetadataRow(
    icon: @Composable () -> Unit,
    text: String,
    color: Color,
    compact: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 13.dp)
    ) {
        Box(
            modifier = Modifier.size(if (compact) 21.dp else 26.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides color
            ) {
                icon()
            }
        }
        Text(
            text = text,
            fontFamily = Inter(),
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 13.sp else 16.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AddMenuCover(
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .padding(40.dp)
            .clip(shape)
            .background(Color(0xFFF3F3F1))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFFA7A9A4),
                cornerRadius = CornerRadius(18.dp.toPx()),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(13f, 9f))
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 48.dp else 60.dp),
                tint = CoverOlive
            )
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            Text(
                text = "ADD NEW MENU",
                fontFamily = CormorantGaramond(),
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 14.sp else 18.sp,
                color = Color(0xFF666864)
            )
            Text(
                text = "Create the next menu",
                modifier = Modifier.padding(top = 3.dp),
                fontFamily = Inter(),
                fontSize = if (compact) 10.sp else 11.sp,
                color = Color(0xFF858783)
            )
        }
    }
}

private data class MenuCoverTheme(
    val background: Color,
    val texture: Color,
    val ink: Color
)

// Exact original covers for the hand-tuned palette. A menu whose stored color
// matches one of these keeps the pixel-identical look it always had — only a
// genuinely new/custom color falls through to the algorithmic tint below.
private val KnownCoverThemesByHex = mapOf(
    "B88945" to MenuCoverTheme(Color(0xFFF4E2BD), Color(0xFFB88945), Color(0xFF513910)),
    "AEBE95" to MenuCoverTheme(Color(0xFFAEBE95), Color(0xFFAEBE95), Color(0xFF203818)),
    "315F7B" to MenuCoverTheme(Color(0xFFA9C6D9), Color(0xFF315F7B), Color(0xFF17334E))
)

private fun coverTheme(menu: Menu, index: Int): MenuCoverTheme {
    val key = "${menu.code} ${menu.name}".lowercase()
    val normalizedHex = menu.color?.trim()?.removePrefix("#")?.uppercase()

    KnownCoverThemesByHex[normalizedHex]?.let { return it }
    parseHexColor(menu.color)?.let { customColor ->
        return MenuCoverTheme(
            background = customColor.lighten(0.6f),
            texture = customColor,
            ink = customColor.darken(0.55f)
        )
    }

    return when {
        "lunch" in key -> MenuCoverTheme(
            background = Color(0xFFF4E2BD),
            texture = Color(0xFFB88945),
            ink = Color(0xFF513910)
        )
        "dinner" in key -> MenuCoverTheme(
            background = Color(0xFFB7C99C),
            texture = Color(0xFFAEBE95),
            ink = Color(0xFF203818)
        )
        "drink" in key || "beverage" in key -> MenuCoverTheme(
            background = Color(0xFFA9C6D9),
            texture = Color(0xFF315F7B),
            ink = Color(0xFF17334E)
        )
        else -> listOf(
            MenuCoverTheme(Color(0xFFF4E2BD), Color(0xFFB88945), Color(0xFF513910)),
            MenuCoverTheme(Color(0xFFAEBE95), Color(0xFFAEBE95), Color(0xFF203818)),
            MenuCoverTheme(Color(0xFFA9C6D9), Color(0xFF315F7B), Color(0xFF17334E))
        )[index % 3]
    }
}

private fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6 && cleaned.length != 8) return null
    return try {
        val parsed = cleaned.toLong(16)
        val argb = if (cleaned.length == 6) (0xFF000000L or parsed) else parsed
        Color(argb.toInt())
    } catch (error: NumberFormatException) {
        null
    }
}

private fun Color.lighten(fraction: Float): Color {
    return copy(
        red = red + (1f - red) * fraction,
        green = green + (1f - green) * fraction,
        blue = blue + (1f - blue) * fraction
    )
}

private fun Color.darken(fraction: Float): Color {
    return copy(
        red = red * (1f - fraction),
        green = green * (1f - fraction),
        blue = blue * (1f - fraction)
    )
}

private fun coverTitle(name: String): String {
    val words = name.trim().uppercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "MENU"
    if (words.size == 1) return "${words.first()}\nMENU"
    return "${words.dropLast(1).joinToString(" ")}\n${words.last()}"
}

private fun coverSchedule(menu: Menu): String {
    val from = formatTimeOfDay(menu.availableFrom)
    val until = formatTimeOfDay(menu.availableUntil)
    if (from == null || until == null) {
        return "Available all day"
    }
    return "$from \u2013 $until"
}

private fun formatTimeOfDay(time: String?): String? {
    if (time.isNullOrBlank()) return null
    val parts = time.split(":")
    val hour24 = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1) ?: "00"
    val period = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return "$hour12:$minute $period"
}
