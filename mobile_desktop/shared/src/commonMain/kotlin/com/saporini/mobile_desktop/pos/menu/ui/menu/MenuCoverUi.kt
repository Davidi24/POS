package com.saporini.mobile_desktop.pos.menu.ui.menu

import androidx.compose.material.icons.outlined.Public
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.DragIndicator
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.CormorantGaramond
import com.saporini.mobile_desktop.core.ui.HoverTooltip
import com.saporini.mobile_desktop.core.ui.isWidePhoneWindow
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.ui.justCreated
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import com.saporini.mobile_desktop.pos.menu.ui.MenuUiState
import com.saporini.mobile_desktop.pos.ui.shell.PosPhoneTopBar
import com.saporini.mobile_desktop.pos.reservations.ToolbarHeight
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.brand_logo
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.saveable.rememberSaveable
import com.saporini.mobile_desktop.core.components.RightEdgeActionButton
import com.saporini.mobile_desktop.core.components.RightEdgeActionButtonHeight
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEvent
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuWaiterView

private val CoverOlive = Color(0xFF4F7942)
private val CoverGold = Color(0xFFC79435)
private val CoverInk = Color(0xFF232422)
private val CoverMuted = Color(0xFF6D706B)

@Composable
fun MenuCoverUi(
    state: MenuUiState,
    canManageMenus: Boolean,
    onlineItemCount: Int = 0,
    onOpenMenu: (String) -> Unit,
    onAddMenu: () -> Unit,
    onEditMenu: (Menu) -> Unit,
    onRetry: () -> Unit,
    onOpenOnlineMenu: () -> Unit = {},
    // Menu → "Menus shown": which menus waiters see.
    onOpenWaiterView: () -> Unit = {},
    // A menu just created: scrolled to and marked for a moment.
    highlightedMenuId: String? = null,
    modifier: Modifier = Modifier,
    deletingMenuId: String? = null,
    profileInitials: String = "?"
) {
    // Menu order is changed in "Menus shown" now; the grid itself no longer rearranges.
    val isReordering = false
    val waiterView = state.waiterView ?: MenuWaiterView()
    // The restaurant's day starts at 06:00, so 01:00 still counts as last night.
    val serviceDay = remember {
        (kotlin.time.Clock.System.now() - kotlin.time.Duration.parse("6h"))
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    }
    val waiterMenus = remember(state.menus, state.waiterView) { waiterView.visibleMenus(state.menus, serviceDay).map { it.id }.toSet() }
    // Managers see every menu: the ones shown in POS first, the others (hidden from waiters, or switched off) after
    // the Online menu card; each group keeps its own order. Waiters get exactly the server's "on now" list.
    var orderedMenus by remember(state.menus, state.waiterView, canManageMenus) {
        mutableStateOf(
            if (canManageMenus) state.menus.sortedBy { !it.active || it.id !in waiterMenus }
            else state.menus
        )
    }
    // Where the "+ Menu" edge button sits on the right edge (it can be dragged up and down).
    var newMenuButtonPosition by rememberSaveable { mutableStateOf(.84f) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val isPhone = isPhoneMenuWindow()
        val isWidePhone = isWidePhoneWindow()
        val widePhoneActionsWidth = minOf(360.dp, maxWidth * 0.55f)
        Column(Modifier.fillMaxSize()) {
            // Only when the app itself is in phone mode; wider windows already show the main top bar.
            if (isPhone && com.saporini.mobile_desktop.core.ui.isPhoneWindow()) {
                PosPhoneTopBar(initials = profileInitials)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        start = if (isPhone) 0.dp else 28.dp,
                        end = if (isPhone) 0.dp else 28.dp,
                        top = if (isWidePhone) 8.dp else if (isPhone) 16.dp else 20.dp,
                        bottom = 0.dp
                    )
            ) {
                if (isWidePhone) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Choose your Menu",
                            modifier = Modifier.weight(1f),
                            fontFamily = Inter(), fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, lineHeight = 24.sp, color = CoverInk
                        )
                        if (canManageMenus) {
                            MenuCoverActions(
                                isPhone = true,
                                onOpenWaiterView = onOpenWaiterView,
                                modifier = Modifier.width(widePhoneActionsWidth)
                            )
                        }
                    }
                } else if (isPhone) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Choose your Menu",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            color = CoverInk
                        )
                        if (canManageMenus) {
                            MenuCoverActions(
                                isPhone = true,
                                onOpenWaiterView = onOpenWaiterView,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
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
                        if (canManageMenus) {
                            MenuCoverActions(
                                isPhone = false,
                                onOpenWaiterView = onOpenWaiterView
                            )
                        }
                    }
                }
                Spacer(Modifier.height(if (isWidePhone) 8.dp else if (isPhone) 14.dp else 16.dp))

                // Managers: which menus waiters see couldn't be loaded, so the "Hidden from waiters" labels may be off.
                if (canManageMenus && state.errorMessage == null) state.waiterViewError?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isPhone) 16.dp else 0.dp)
                            .background(Color(0xFFFFF7E8), RoundedCornerShape(9.dp))
                            .border(1.dp, Color(0xFFF0D9A8), RoundedCornerShape(9.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Couldn't load which menus waiters see, so the labels may be wrong. $message",
                            modifier = Modifier.weight(1f),
                            fontFamily = Inter(),
                            fontSize = 13.sp,
                            color = Color(0xFF8A5A00)
                        )
                        TextButton(onClick = onRetry) {
                            Text("Try again", fontFamily = Inter(), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                state.errorMessage?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isPhone) 16.dp else 0.dp)
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

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    if (state.isLoading) {
                        if (isPhone) {
                            PhoneMenuCoverSkeletons(
                                availableWidth = maxWidth,
                                availableHeight = maxHeight
                            )
                        } else {
                            MenuCoverSkeletonGrid(
                                columns = menuGridColumns(maxWidth),
                                availableHeight = maxHeight
                            )
                        }
                    } else {
                        MenuCoverCollection(
                            menus = orderedMenus,
                            columns = menuGridColumns(maxWidth),
                            availableWidth = maxWidth,
                            availableHeight = maxHeight,
                            isPhone = isPhone,
                            isReordering = isReordering,
                            canManageMenus = canManageMenus,
                            deletingMenuId = deletingMenuId,
                            onReorder = { orderedMenus = it },
                            onOpenMenu = onOpenMenu,
                            onEditMenu = onEditMenu,
                            onAddMenu = onAddMenu,
                            onOpenOnlineMenu = onOpenOnlineMenu,
                            onlineItemCount = onlineItemCount,
                            hiddenFromWaiters = { menu -> canManageMenus && menu.active && menu.id !in waiterMenus },
                            highlightedMenuId = highlightedMenuId
                        )
                    }
                }
            }
        }

        // "+ Create Menu" on the right edge, like "+ Reservation": creating a menu lives here now.
        if (canManageMenus && !isReordering) {
            val maxButtonOffset = with(LocalDensity.current) {
                (maxHeight - RightEdgeActionButtonHeight).coerceAtLeast(0.dp).toPx()
            }
            RightEdgeActionButton(
                text = "Create Menu",
                onClick = onAddMenu,
                enabled = true,
                width = 124.dp,
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(0, (newMenuButtonPosition * maxButtonOffset).roundToInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (maxButtonOffset > 0f) {
                                newMenuButtonPosition = (newMenuButtonPosition + delta / maxButtonOffset).coerceIn(0f, 1f)
                            }
                        }
                    )
            )
        }
    }
}

@Composable
private fun MenuCoverActions(
    isPhone: Boolean,
    onOpenWaiterView: () -> Unit,
    modifier: Modifier = Modifier
) {
    // "Menus shown": which menus waiters see and the order menus are shown in. Desktop matches the Reservations
    // header (Calendar button): same height, padding and text size.
    val shape = RoundedCornerShape(if (isPhone) 5.dp else 8.dp)
    Row(modifier = modifier, horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = onOpenWaiterView,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(containerColor = Color.White, contentColor = CoverInk),
            modifier = Modifier.height(if (isPhone) 40.dp else ToolbarHeight).border(1.dp, Color(0xFFDDD9D2), shape),
            contentPadding = PaddingValues(horizontal = if (isPhone) 10.dp else 14.dp, vertical = 0.dp)
        ) {
            Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(if (isPhone) 16.dp else 18.dp))
            Spacer(Modifier.width(if (isPhone) 6.dp else 7.dp))
            Text(
                text = "Menus shown",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = if (isPhone) TextUnit.Unspecified else 0.sp,
                maxLines = 1
            )
        }
    }
}

private fun menuGridColumns(width: androidx.compose.ui.unit.Dp): Int =
    menuContentColumns(width, 300.dp, 24.dp)

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
    val coverHeight = minOf(availableHeight, 480.dp)

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
                    .height(coverHeight)
                    .padding(end = CoverActionOverhang),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
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
            .padding(top = CoverFaceTop)
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
private fun MenuCoverCollection(
    menus: List<Menu>,
    columns: Int,
    availableWidth: androidx.compose.ui.unit.Dp,
    availableHeight: androidx.compose.ui.unit.Dp,
    isPhone: Boolean,
    isReordering: Boolean,
    canManageMenus: Boolean,
    deletingMenuId: String?,
    onReorder: (List<Menu>) -> Unit,
    onOpenMenu: (String) -> Unit,
    onEditMenu: (Menu) -> Unit,
    onAddMenu: () -> Unit,
    onOpenOnlineMenu: () -> Unit,
    onlineItemCount: Int,
    // Active menus a manager sees but waiters don't get a "Hidden from waiters" label.
    hiddenFromWaiters: (Menu) -> Boolean = { false },
    highlightedMenuId: String? = null
) {
    // Menus in use, then the Online menu preview (hidden while reordering), then switched-off menus, then "Add menu"
    // for managers. The menus arrive with the active ones first.
    val showOnlineCover = !isReordering
    // Menus shown in POS come before the Online card; hidden or switched-off ones after it (they arrive in that order).
    val activeCount = menus.count { it.active && !hiddenFromWaiters(it) }
    // A menu's place in the grid: switched-off menus sit after the Online menu card.
    fun menuSlotIndex(index: Int): Int = if (showOnlineCover && index >= activeCount) index + 1 else index
    val itemCount = menus.size + (if (showOnlineCover) 1 else 0) + if (isReordering || !canManageMenus) 0 else 1
    val rowCount = if (itemCount == 0) 0 else (itemCount + columns - 1) / columns
    val horizontalGap = if (isPhone) 26.dp else 32.dp
    val verticalGap = 20.dp
    val desktopLayout = desktopMenuCoverLayout(availableHeight, rowCount)
    val coverHeight = if (isPhone) {
        phoneMenuCoverHeight(availableHeight)
    } else {
        desktopLayout.height
    }
    val cardWidth = if (isPhone) {
        phoneMenuCoverWidth(availableWidth, coverHeight)
    } else {
        (availableWidth - CoverActionOverhang - horizontalGap * (columns - 1)) / columns
    }
    val contentWidth = if (isPhone && itemCount > 0) {
        cardWidth * itemCount + horizontalGap * (itemCount - 1) + 40.dp
    } else {
        availableWidth
    }
    val scrollState = rememberScrollState()
    val compact = coverHeight < 390.dp
    val phoneVerticalPadding = if (isPhone && availableHeight > coverHeight) {
        ((availableHeight - coverHeight) / 2f).coerceAtLeast(CoverActionOverhang)
    } else {
        CoverActionOverhang
    }
    val contentHeight = if (isPhone) {
        maxOf(availableHeight, coverHeight + phoneVerticalPadding * 2)
    } else if (rowCount == 0) {
        0.dp
    } else {
        // Room after the last row, so scrolling to the end doesn't leave it pressed against the bottom edge.
        coverHeight * rowCount + verticalGap * (rowCount - 1) + desktopLayout.topInset * 2 +
            if (desktopLayout.centerRow) 0.dp else DesktopGridEndSpace
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
            .then(
                if (isPhone) {
                    Modifier.verticalScroll(rememberScrollState()).horizontalScroll(scrollState)
                } else {
                    Modifier.verticalScroll(scrollState)
                }
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .width(contentWidth)
                .height(contentHeight)
                .then(
                    if (isPhone) {
                        Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = phoneVerticalPadding,
                            bottom = phoneVerticalPadding
                        )
                    } else {
                        Modifier.padding(
                            top = desktopLayout.topInset,
                            start = if (desktopLayout.centerRow) (cardWidth + horizontalGap) * (columns - itemCount) / 2f else 0.dp
                        )
                    }
                )
        ) {
            val density = LocalDensity.current
            val cardWidthPx = with(density) { cardWidth.toPx() }
            val coverHeightPx = with(density) { coverHeight.toPx() }
            val horizontalGapPx = with(density) { horizontalGap.toPx() }
            val verticalGapPx = with(density) { verticalGap.toPx() }

            fun slotPosition(index: Int): Offset {
                val column = if (isPhone) index else index % columns
                val row = if (isPhone) 0 else index / columns
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

            // Long-press dragging can move through menus beyond the visible viewport.
            LaunchedEffect(draggingId, isPhone, cardWidthPx, horizontalGapPx) {
                if (!isPhone) return@LaunchedEffect
                val viewportWidth = with(density) { availableWidth.toPx() }
                val edge = with(density) { 56.dp.toPx() }
                val inset = with(density) { 20.dp.toPx() }
                val speed = with(density) { 8.dp.toPx() }
                while (draggingId != null) {
                    withFrameNanos { }
                    val activeId = draggingId ?: break
                    val center = draggedPosition.x + inset + cardWidthPx / 2f - scrollState.value
                    val delta = when {
                        center < edge -> -speed
                        center > viewportWidth - edge -> speed
                        else -> 0f
                    }
                    if (delta != 0f) {
                        val consumed = scrollState.scrollBy(delta)
                        if (draggingId != activeId) break
                        draggedPosition += Offset(consumed, 0f)
                        val currentOrder = gestureOrder ?: latestMenus
                        val from = currentOrder.indexOfFirst { it.id == activeId }
                        val to = nearestSlotIndex(draggedPosition, currentOrder.size)
                        if (from >= 0 && to != null && from != to) {
                            val reordered = currentOrder.toMutableList()
                            reordered.add(to, reordered.removeAt(from))
                            gestureOrder = reordered
                            latestOnReorder(reordered)
                        }
                    }
                }
            }

            menus.forEachIndexed { itemIndex, menu ->
                key(menu.id) {
                    val targetSlot = slotPosition(menuSlotIndex(itemIndex))
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

                    MenuChooserCover(
                        isPhone = isPhone,
                        menu = menu,
                        theme = coverTheme(menu, itemIndex),
                        compact = compact,
                        isReordering = isReordering,
                        isDragging = isDragging,
                        canManageMenus = canManageMenus,
                        hiddenFromWaiters = hiddenFromWaiters(menu),
                        highlighted = menu.id == highlightedMenuId,
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
                                        verticalGapPx,
                                        isPhone
                                    ) {
                                        val startDrag: (Offset) -> Unit = {
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
                                        }
                                        val moveDrag: (PointerInputChange, Offset) -> Unit = { change, dragAmount ->
                                            change.consume()
                                            if (draggingId == menu.id) {
                                                draggedPosition += dragAmount
                                                val currentOrder = gestureOrder ?: latestMenus
                                                val fromIndex = currentOrder.indexOfFirst { it.id == menu.id }
                                                val toIndex = nearestSlotIndex(draggedPosition, currentOrder.size)
                                                if (fromIndex >= 0 && toIndex != null && toIndex != fromIndex) {
                                                    val reordered = currentOrder.toMutableList()
                                                    reordered.add(toIndex, reordered.removeAt(fromIndex))
                                                    gestureOrder = reordered
                                                    latestOnReorder(reordered)
                                                }
                                            }
                                        }
                                        if (isPhone) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = startDrag,
                                                onDragEnd = ::releaseDraggedMenu,
                                                onDragCancel = ::releaseDraggedMenu,
                                                onDrag = moveDrag
                                            )
                                        } else {
                                            detectDragGestures(
                                                onDragStart = startDrag,
                                                onDragEnd = ::releaseDraggedMenu,
                                                onDragCancel = ::releaseDraggedMenu,
                                                onDrag = moveDrag
                                            )
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }

            if (showOnlineCover) {
                val onlineSlot = slotPosition(activeCount)
                OnlineMenuCover(
                    compact = compact,
                    itemCount = onlineItemCount,
                    onClick = onOpenOnlineMenu,
                    modifier = Modifier
                        .offset {
                            IntOffset(onlineSlot.x.roundToInt(), onlineSlot.y.roundToInt())
                        }
                        .width(cardWidth)
                        .height(coverHeight)
                )
            }

            if (!isReordering && canManageMenus) {
                val addSlot = slotPosition(menus.size + if (showOnlineCover) 1 else 0)
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
private fun MenuChooserCover(
    isPhone: Boolean,
    menu: Menu,
    theme: MenuCoverTheme,
    compact: Boolean,
    isReordering: Boolean,
    isDragging: Boolean,
    canManageMenus: Boolean,
    hiddenFromWaiters: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier
) {
    if (isPhone) {
        PhoneMenuBookCover(
            menu = menu,
            theme = theme,
            isReordering = isReordering,
            isDragging = isDragging,
            canManageMenus = canManageMenus,
            hiddenFromWaiters = hiddenFromWaiters,
            highlighted = highlighted,
            onClick = onClick,
            onEdit = onEdit,
            modifier = modifier
        )
    } else {
        MenuBookCover(
            menu = menu,
            theme = theme,
            compact = compact,
            isReordering = isReordering,
            isDragging = isDragging,
            canManageMenus = canManageMenus,
            hiddenFromWaiters = hiddenFromWaiters,
            highlighted = highlighted,
            onClick = onClick,
            onEdit = onEdit,
            modifier = modifier
        )
    }
}

@Composable
fun MenuCoverPreview(
    name: String,
    description: String,
    // Off: the preview wears the "Hidden from waiters" veil, like the real card.
    shownInPos: Boolean,
    availableFrom: String?,
    availableUntil: String?,
    color: String,
    modifier: Modifier = Modifier,
    // An event menu's event: the preview shows its icon and days like the real card.
    event: MenuEvent? = null
) {
    val previewMenu = Menu(
        id = "menu-preview",
        restaurant = null,
        code = "PREVIEW",
        name = name.trim().ifEmpty { "New Menu" },
        description = description.trim().takeIf { it.isNotEmpty() },
        active = true,
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
        sections = emptyList(),
        special = event != null,
        event = event
    )

    MenuBookCover(
        menu = previewMenu,
        theme = coverTheme(previewMenu, 0),
        compact = true,
        isReordering = false,
        showActions = false,
        hiddenFromWaiters = !shownInPos,
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
    hiddenFromWaiters: Boolean = false,
    highlighted: Boolean = false,
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
                .padding(top = CoverFaceTop)
                .justCreated(highlighted, cardShape)
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
                    AutoSizeCoverTitle(
                        text = coverTitle(menu.name),
                        maxFontSize = if (compact) 27.sp else 35.sp,
                        minFontSize = if (compact) 16.sp else 20.sp,
                        letterSpacing = 1.sp,
                        color = theme.ink,
                        modifier = Modifier.fillMaxWidth()
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

            if (!menu.active) {
                InactiveCoverVeil(
                    compact = compact,
                    spineWidth = if (compact) 38.dp else 44.dp,
                    modifier = Modifier.matchParentSize()
                )
            } else if (hiddenFromWaiters) {
                // Waiters don't see it: washed out like an inactive menu, with the reason in the middle.
                InactiveCoverVeil(
                    compact = compact,
                    spineWidth = if (compact) 38.dp else 44.dp,
                    label = "Hidden from waiters",
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        // The edit button sits on the book face's top-right corner, sticking out CoverActionOverhang past the top
        // and right edges; the info button (when there's a description) sits right below it, lined up.
        val actionSize = if (compact) 34.dp else 40.dp
        val actionTop = CoverFaceTop - CoverActionOverhang
        // A special menu wears its event's icon on the top-left corner, like a sticker (mirrors the edit button).
        menu.event?.let { event ->
            EventIconBadge(
                event = event,
                size = actionSize,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (if (compact) 38.dp else 44.dp) + 8.dp, y = actionTop)
                    .zIndex(3f)
            )
        }
        if (showActions && !isReordering) {
            if (canManageMenus) {
                HoverTooltip(
                    text = "Edit menu",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = CoverActionOverhang, y = actionTop)
                        .zIndex(3f)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
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
            }

            if (!menu.description.isNullOrBlank()) {
                IconButton(
                    onClick = { showDescription = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(
                            x = CoverActionOverhang,
                            y = actionTop + if (canManageMenus) actionSize + 8.dp else 0.dp
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
                        imageVector = Icons.Outlined.DragIndicator,
                        contentDescription = "Drag to move ${menu.name}",
                        modifier = Modifier.size(15.dp),
                        tint = if (isDragging) Color.White else CoverInk
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
internal fun MenuDescriptionDialog(
    menu: Menu,
    onDismiss: () -> Unit
) {
    MenuNestedDialog(
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
        },
        dismissButton = {}
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
private fun OnlineMenuCover(
    compact: Boolean,
    itemCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Purple is reserved for the Online Menu; it is deliberately not part of the
    // selectable palette used by regular menu covers.
    val theme = MenuCoverTheme(
        background = Color(0xFFE3DDF1),
        texture = Color(0xFF7563A2),
        ink = Color(0xFF382D50)
    )
    val cardShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 0.dp
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = CoverFaceTop)
                .shadow(13.dp, cardShape, clip = false, spotColor = Color(0x44141414))
                .clip(cardShape)
                .clickable(onClick = onClick)
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
                        listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.06f))
                    )
                )
            }

            Box(
                modifier = Modifier.align(Alignment.CenterStart)
                    .width(if (compact) 38.dp else 44.dp)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF151616), Color(0xFF393B39), Color(0xFF111212))))
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(Color(0xFF050505), Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 2f)
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
                modifier = Modifier.fillMaxSize().padding(
                    start = if (compact) 62.dp else 72.dp,
                    end = if (compact) 16.dp else 22.dp,
                    top = if (compact) 18.dp else 26.dp,
                    bottom = if (compact) 14.dp else 20.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AutoSizeCoverTitle(
                    text = "ONLINE MENU",
                    maxFontSize = if (compact) 27.sp else 35.sp,
                    minFontSize = if (compact) 16.sp else 20.sp,
                    letterSpacing = 1.sp,
                    color = theme.ink,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = "Online menu",
                        modifier = Modifier.size(if (compact) 68.dp else 92.dp),
                        tint = theme.ink
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                    Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
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
}

@Composable
private fun AddMenuCover(
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)

    // A smaller card centred in its slot, 40dp in from every side (as it was before the online menu cover came).
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

// Space above every cover's face (book, online, create, skeleton): room for the edit button that
// straddles the face's top-right corner, and it keeps all covers lined up.
private val CoverFaceTop = 20.dp

// How far the corner buttons stick out past a cover's top and right edges (the rest of the button is on the
// cover). The grid keeps this much room on the right, so the buttons are never clipped or touching.
internal val CoverActionOverhang = 10.dp

// Empty space after the last row of the desktop grid.
private val DesktopGridEndSpace = 32.dp

internal data class MenuCoverTheme(
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

internal fun coverTitle(name: String): String {
    val words = name.trim().uppercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "MENU"
    if (words.size == 1) return "${words.first()}\nMENU"
    return "${words.dropLast(1).joinToString(" ")}\n${words.last()}"
}

internal fun coverSchedule(menu: Menu): String {
    // An event menu shows its event night; booking extras say what they are.
    menu.event?.let { return "${it.icon} ${it.name} · ${eventDaysText(it.startDate, it.endDate)}".trim() }
    if (menu.isBookingExtras) return "Booking extras"
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

/**
 * A menu that's out of use: the cover is washed out with a dark label in the middle ("Inactive", or "Hidden from
 * waiters"), so it reads as unavailable at a glance.
 */
@Composable
internal fun InactiveCoverVeil(
    compact: Boolean,
    spineWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    label: String = "Inactive"
) {
    Box(modifier.background(Color(0xFFF3F3F1).copy(alpha = 0.66f))) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = spineWidth)
                .clip(RoundedCornerShape(50))
                .background(CoverInk.copy(alpha = 0.88f))
                .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 5.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 13.dp else 16.dp),
                tint = Color.White
            )
            Text(
                text = label,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 11.sp else 13.sp,
                letterSpacing = 0.sp,
                color = Color.White
            )
        }
    }
}

/** The event's icon on a special menu's cover: a white round badge with a soft shadow, the event's name on hover. */
@Composable
internal fun EventIconBadge(event: MenuEvent, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    HoverTooltip(text = "${event.name} · ${eventDaysText(event.startDate, event.endDate)}", modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, CoverGold.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(event.icon.ifBlank { "🎉" }, fontSize = (size.value * 0.46f).sp)
        }
    }
}
