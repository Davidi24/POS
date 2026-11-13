package com.saporini.mobile_desktop.pos.menu.ui.menu

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.isWidePhoneWindow
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuWaiterView
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val WaiterGreen = Color(0xFF4F7942)
private val WaiterGreenSoft = Color(0xFFEEF3EB)
private val WaiterInk = Color(0xFF222426)
private val WaiterMuted = Color(0xFF747572)
private val WaiterBorder = Color(0xFFE4E5E1)
private val WaiterSurface = Color(0xFFF7F7F5)
private val WaiterDanger = Color(0xFFB13A2F)

/**
 * Menu → "Menus shown": which menus waiters see in Menu and at the till: the ticked menus. "Special menus only" shows
 * just the special (event) menus on the days one is on, and the ticked menus on other days, so waiters never see
 * nothing. Managers keep seeing every menu. Same look as the section editor.
 */
@Composable
internal fun WaiterMenusDialog(
    menus: List<Menu>,
    view: MenuWaiterView?,
    onDismiss: () -> Unit,
    onSave: suspend (MenuWaiterView) -> Result<MenuWaiterView>,
    // "Change position": the order menus are shown in (Menu, the till), saved on its own.
    onSaveOrder: suspend (List<String>) -> Result<Unit>
) {
    val isPhone = isPhoneMenuWindow()
    val isWidePhone = isWidePhoneWindow()
    val scope = rememberCoroutineScope()
    val activeMenus = menus.filter { it.active }
    val inactiveCount = menus.size - activeMenus.size
    val hasSpecialMenus = activeMenus.any { it.special }
    // "Special menus only" can't be on without a special menu.
    var specialOnly by remember { mutableStateOf(view?.specialMenusOnly == true && hasSpecialMenus) }
    var shown by remember { mutableStateOf(menus.filter { it.shownToWaiters }.map { it.id }.toSet()) }
    // Selected menus first: with "Special menus only" the special ones, otherwise the menus ticked when the dialog
    // opened (so a row doesn't jump away while you tick it).
    val tickedAtOpen = remember { menus.filter { it.shownToWaiters }.map { it.id }.toSet() }
    var attemptedSave by remember { mutableStateOf(false) }
    // "Change position" mode: drag the menus into the order they're shown in, like the section editor.
    var reordering by remember { mutableStateOf(false) }
    var workingOrder by remember(menus) { mutableStateOf(activeMenus.sortedBy { it.displayOrder }) }
    var reorderBlockedToken by remember { mutableStateOf(0) }
    var showReorderToast by remember { mutableStateOf(false) }
    LaunchedEffect(reorderBlockedToken) {
        if (reorderBlockedToken > 0) {
            showReorderToast = true
            delay(3000)
            showReorderToast = false
        }
    }
    // Bumped when the switch is tried without any special menu, to show (and later hide) why it won't turn on.
    var noSpecialToken by remember { mutableStateOf(0) }
    var showNoSpecial by remember { mutableStateOf(false) }
    LaunchedEffect(noSpecialToken) {
        if (noSpecialToken > 0) {
            showNoSpecial = true
            delay(3500)
            showNoSpecial = false
        }
    }

    fun toggleSpecialOnly(on: Boolean) {
        when {
            on && !hasSpecialMenus -> noSpecialToken++
            on -> {
                specialOnly = true
                // The ticked menus are still what waiters see on days without a special menu: never leave none.
                if (activeMenus.none { it.id in shown }) shown = shown + activeMenus.filterNot { it.special }.map { it.id }
            }
            else -> specialOnly = false
        }
    }
    var status by remember { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    val isIdle = status is DialogActionStatus.Idle

    val waiterCount = activeMenus.count { it.id in shown }
    // Ticked menus are needed even with "special menus only": they're what waiters see on days without one.
    val problem = if (waiterCount == 0) "Pick at least one menu for waiters." else null

    fun save() {
        if (problem != null) {
            attemptedSave = true
            return
        }
        status = DialogActionStatus.Loading("Saving")
        scope.launch {
            onSave(MenuWaiterView(specialMenusOnly = specialOnly, shownMenuIds = shown)).fold(
                onSuccess = { status = DialogActionStatus.Success("Waiters' menus saved") },
                onFailure = { error -> status = DialogActionStatus.Failed(message = error.message ?: "Could not save the waiters' menus.") }
            )
        }
    }

    fun saveOrder() {
        status = DialogActionStatus.Loading("Saving")
        scope.launch {
            onSaveOrder(workingOrder.map { it.id }).fold(
                onSuccess = { status = DialogActionStatus.Success("Menu order saved") },
                onFailure = { error -> status = DialogActionStatus.Failed(message = error.message ?: "Could not save the menu order.") }
            )
        }
    }

    fun startReordering() {
        if (activeMenus.size >= 2) {
            workingOrder = activeMenus.sortedBy { it.displayOrder }
            reordering = true
        } else {
            reorderBlockedToken++
        }
    }

    Dialog(onDismissRequest = { if (isIdle) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f))
                .then(if (isPhone) Modifier.safeDrawingPadding().imePadding() else Modifier)
                .padding(if (isWidePhone) 12.dp else 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.width(minOf(maxWidth, 560.dp)).widthIn(max = 560.dp)
                    .heightIn(max = maxHeight * if (isWidePhone) 0.94f else 0.86f)
                    .shadow(22.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                    .background(Color.White).border(1.dp, WaiterBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (reordering) "Change menu order" else "Menus waiters see",
                            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = WaiterInk
                        )
                        Text(
                            when {
                                !reordering -> "In Menu and at the till. Managers always see every menu."
                                isPhone -> "Press and hold the six-dot handle, then drag and drop to change the order."
                                else -> "Drag and drop the six-dot handle to change the order menus are shown in."
                            },
                            fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = WaiterMuted
                        )
                    }
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(WaiterSurface)
                            .border(1.dp, WaiterBorder, CircleShape).clickable(enabled = isIdle, onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Close, "Close", Modifier.size(18.dp), tint = WaiterInk)
                    }
                }
                HorizontalDivider(color = WaiterBorder)

                if (!isIdle) {
                    DialogStatusBody(
                        status = status,
                        onRetry = { status = DialogActionStatus.Idle },
                        onCancel = onDismiss,
                        onSuccessSettled = {
                            if (reordering) {
                                reordering = false
                                status = DialogActionStatus.Idle
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp)
                    )
                } else if (reordering) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = if (isWidePhone) 10.dp else 16.dp)
                    ) {
                        MenuReorderList(menus = workingOrder, isPhone = isPhone, onReorder = { workingOrder = it })
                    }
                    HorizontalDivider(color = WaiterBorder)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isWidePhone) 6.dp else 10.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { reordering = false }) { Text("Back", fontFamily = Inter(), color = WaiterMuted) }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { saveOrder() },
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(containerColor = WaiterGreen)
                        ) {
                            Text("Save order", fontFamily = Inter(), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = if (isWidePhone) 10.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "Special menus only": one switch that overrides the list below. Without any special menu it
                        // looks off-limits, and trying it says why.
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (specialOnly) WaiterGreenSoft else WaiterSurface)
                                .border(if (specialOnly) 1.5.dp else 1.dp, if (specialOnly) WaiterGreen else WaiterBorder, RoundedCornerShape(12.dp))
                                .clickable { toggleSpecialOnly(!specialOnly) }
                                .alpha(if (hasSpecialMenus) 1f else 0.55f)
                                .padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(19.dp), tint = WaiterGreen)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Special menus only", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = WaiterInk)
                                Text(
                                    if (!hasSpecialMenus) "No special menus yet."
                                    else "Waiters see just the special menus, on their event days.",
                                    fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = WaiterMuted
                                )
                            }
                            Switch(
                                checked = specialOnly,
                                onCheckedChange = { toggleSpecialOnly(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = WaiterGreen, checkedThumbColor = Color.White)
                            )
                        }
                        if (showNoSpecial) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF1EF))
                                    .border(1.dp, Color(0xFFF2C4BE), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = WaiterDanger)
                                Text(
                                    "No special menus to show. Make a menu special first: edit it and tick Special menu.",
                                    fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = WaiterDanger
                                )
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (specialOnly) "Menus waiters see" else "Menus waiters see · $waiterCount of ${activeMenus.size}",
                                Modifier.weight(1f),
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = WaiterInk
                            )
                            if (!specialOnly && activeMenus.isNotEmpty()) {
                                val allShown = activeMenus.all { it.id in shown }
                                Text(
                                    if (allShown) "Clear all" else "Select all",
                                    Modifier.clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            shown = if (allShown) shown - activeMenus.map { it.id }.toSet()
                                            else shown + activeMenus.map { it.id }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = WaiterGreen
                                )
                            }
                        }

                        if (activeMenus.isEmpty()) {
                            Text("There are no active menus yet.", fontFamily = Inter(), fontSize = 13.sp, color = WaiterMuted,
                                modifier = Modifier.padding(vertical = 12.dp))
                        }
                        val ordered = activeMenus.sortedBy { menu -> if (specialOnly) !menu.special else menu.id !in tickedAtOpen }
                        ordered.forEach { menu ->
                            // Only while "Special menus only" is on: the special menus are what waiters see, the
                            // others are disabled (their ticks are kept for when it's off).
                            WaiterMenuRow(
                                menu = menu,
                                checked = if (specialOnly) menu.special else menu.id in shown,
                                enabled = !specialOnly || menu.special,
                                clickable = !specialOnly,
                                onToggle = { shown = if (menu.id in shown) shown - menu.id else shown + menu.id }
                            )
                        }
                        if (specialOnly) {
                            Text(
                                "On days without a special menu, waiters see the menus you had ticked.",
                                fontFamily = Inter(), fontSize = 11.sp, color = WaiterMuted, modifier = Modifier.padding(top = 2.dp, start = 2.dp)
                            )
                        }
                        if (inactiveCount > 0) {
                            Text(
                                "$inactiveCount inactive ${if (inactiveCount == 1) "menu is" else "menus are"} never shown to waiters.",
                                fontFamily = Inter(), fontSize = 11.sp, color = WaiterMuted, modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                            )
                        }
                        if (attemptedSave && problem != null) {
                            Text(problem, fontFamily = Inter(), fontSize = 12.sp, color = WaiterDanger, modifier = Modifier.padding(start = 2.dp))
                        }
                    }

                    HorizontalDivider(color = WaiterBorder)
                    // Box (not just the Row) so the "need 2+ menus" toast floats above "Change position".
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isWidePhone) 6.dp else 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Outlined, not filled: Save is the main action of this footer.
                        val changeInk = if (activeMenus.size >= 2) WaiterInk else WaiterInk.copy(alpha = 0.4f)
                        // A small secondary button, so Save stays the clear main action.
                        Row(
                            Modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                                .border(1.dp, changeInk.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .clickable { startReordering() }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Outlined.DragIndicator, null, Modifier.size(16.dp), tint = changeInk)
                            Text("Change position", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = changeInk)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Inter(), color = WaiterMuted) }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            // Looks dimmed while something's missing; a click still says what.
                            onClick = { save() },
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (problem == null) WaiterGreen else WaiterGreen.copy(alpha = 0.45f)
                            )
                        ) {
                            Text("Save", fontFamily = Inter(), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                        MenuValidationToast(
                            visible = showReorderToast,
                            message = "Add at least 2 active menus to change their order.",
                            placement = ToastPlacement.Above,
                            arrowAlignment = Alignment.Start,
                            anchorAlignment = Alignment.TopStart,
                            offsetY = (-46).dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaiterMenuRow(menu: Menu, checked: Boolean, enabled: Boolean, clickable: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp))
            .background(if (checked && enabled) WaiterGreenSoft else WaiterSurface)
            .border(1.dp, if (checked && enabled) WaiterGreen.copy(alpha = 0.5f) else WaiterBorder, RoundedCornerShape(10.dp))
            .clickable(enabled = clickable && enabled, onClick = onToggle)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val event = menu.event
        // A special menu shows its event's icon on a white medallion.
        MenuSwatch(menu)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(menu.name, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = WaiterInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (menu.special) {
                    Text(
                        "Special",
                        Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFF6EBD3)).padding(horizontal = 7.dp, vertical = 1.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Color(0xFF8A6418)
                    )
                }
            }
            Text(
                event?.let { "${it.name} · ${eventDaysText(it.startDate, it.endDate)}" } ?: coverSchedule(menu),
                fontFamily = Inter(), fontSize = 11.sp, color = WaiterMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(if (checked) WaiterGreen else Color.White)
                .border(if (checked) 1.dp else 1.5.dp, if (checked) WaiterGreen else Color(0xFF8B8F87), RoundedCornerShape(5.dp))
                .alpha(if (enabled) 1f else 0.7f),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = Color.White)
        }
    }
}

private fun menuColor(hex: String?): Color {
    val clean = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return WaiterGreen
    return clean.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: WaiterGreen
}

/** The menus as draggable rows (six-dot handle), like the section editor's "Change section order". */
@Composable
private fun MenuReorderList(menus: List<Menu>, isPhone: Boolean, onReorder: (List<Menu>) -> Unit) {
    val rowHeight = 56.dp
    val rowGap = 8.dp
    val rowStep = rowHeight + rowGap
    val listHeight = if (menus.isEmpty()) 0.dp else rowStep * menus.size - rowGap
    var draggingId by remember { mutableStateOf<String?>(null) }
    var draggedY by remember { mutableFloatStateOf(0f) }
    var gestureOrder by remember { mutableStateOf<List<Menu>?>(null) }
    val latestMenus by rememberUpdatedState(menus)
    val latestOnReorder by rememberUpdatedState(onReorder)

    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = listHeight, max = listHeight)) {
        val listWidth = maxWidth
        val density = LocalDensity.current
        val rowStepPx = with(density) { rowStep.toPx() }
        val rowHeightPx = with(density) { rowHeight.toPx() }

        fun slotY(index: Int) = index * rowStepPx
        fun nearestSlot(position: Float, size: Int): Int? {
            if (size <= 1) return null
            val center = position + rowHeightPx / 2f
            return (0 until size).minByOrNull { index -> kotlin.math.abs((slotY(index) + rowHeightPx / 2f) - center) }
        }

        menus.forEachIndexed { index, menu ->
            key(menu.id) {
                val animatedOffset by animateIntOffsetAsState(
                    targetValue = IntOffset(0, slotY(index).roundToInt()),
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                    label = "menu-order-slot-${menu.id}"
                )
                val isDragging = draggingId == menu.id
                val dragModifier = Modifier.pointerInput(menu.id, rowStepPx, isPhone) {
                    val startDrag: (Offset) -> Unit = {
                        val current = latestMenus.indexOfFirst { it.id == menu.id }
                        if (current >= 0) {
                            gestureOrder = latestMenus
                            draggedY = slotY(current)
                            draggingId = menu.id
                        }
                    }
                    val endDrag: () -> Unit = {
                        draggingId = null
                        gestureOrder = null
                    }
                    val moveDrag: (PointerInputChange, Offset) -> Unit = { change, amount ->
                        change.consume()
                        if (draggingId == menu.id) {
                            draggedY += amount.y
                            val currentOrder = gestureOrder ?: latestMenus
                            val from = currentOrder.indexOfFirst { it.id == menu.id }
                            val to = nearestSlot(draggedY, currentOrder.size)
                            if (from >= 0 && to != null && from != to) {
                                val reordered = currentOrder.toMutableList().also { list -> list.add(to, list.removeAt(from)) }
                                gestureOrder = reordered
                                latestOnReorder(reordered)
                            }
                        }
                    }
                    if (isPhone) {
                        detectDragGesturesAfterLongPress(onDragStart = startDrag, onDragEnd = endDrag, onDragCancel = endDrag, onDrag = moveDrag)
                    } else {
                        detectDragGestures(onDragStart = startDrag, onDragEnd = endDrag, onDragCancel = endDrag, onDrag = moveDrag)
                    }
                }
                Row(
                    modifier = Modifier.width(listWidth).heightIn(min = rowHeight, max = rowHeight)
                        .zIndex(if (isDragging) 2f else 0f)
                        .offset { if (isDragging) IntOffset(0, draggedY.roundToInt()) else animatedOffset }
                        .graphicsLayer {
                            if (isDragging) {
                                scaleX = 1.015f
                                scaleY = 1.015f
                            }
                        }
                        .shadow(if (isDragging) 9.dp else 0.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDragging) Color.White else WaiterSurface)
                        .border(1.dp, if (isDragging) WaiterGreen else WaiterBorder, RoundedCornerShape(10.dp))
                        .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}",
                        Modifier.width(22.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = WaiterMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    MenuSwatch(menu)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(menu.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = WaiterInk,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            menu.event?.let { "${it.name} · ${eventDaysText(it.startDate, it.endDate)}" } ?: coverSchedule(menu),
                            fontFamily = Inter(), fontSize = 11.sp, color = WaiterMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                            .border(1.dp, WaiterBorder, RoundedCornerShape(8.dp)).then(dragModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.DragIndicator, "Drag and drop ${menu.name}", Modifier.size(24.dp), tint = WaiterInk)
                    }
                }
            }
        }
    }
}

/** The menu's colour square, with its event's icon for a special menu. */
@Composable
private fun MenuSwatch(menu: Menu) {
    val event = menu.event
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(menuColor(menu.color)),
        contentAlignment = Alignment.Center
    ) {
        if (event != null) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Text(event.icon.ifBlank { "🎉" }, fontSize = 14.sp)
            }
        } else {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp), tint = Color.White)
        }
    }
}
