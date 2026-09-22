package com.saporini.mobile_desktop.pos.menu.ui.section

import com.saporini.mobile_desktop.pos.menu.domain.model.isUncategorizedSection
import com.saporini.mobile_desktop.pos.menu.domain.model.withUncategorizedLast
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.saporini.mobile_desktop.pos.menu.ui.MenuCategory
import com.saporini.mobile_desktop.pos.menu.ui.MenuCategoryIcon
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogActionStatus
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogStatusBody
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuValidationToast
import com.saporini.mobile_desktop.pos.menu.ui.menu.ToastPlacement
import com.saporini.mobile_desktop.pos.menu.ui.menu.isPhoneMenuWindow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SectionGreen = Color(0xFF4F7942)
private val SectionInk = Color(0xFF222426)
private val SectionMuted = Color(0xFF747572)
private val SectionBorder = Color(0xFFE4E5E1)
private val SectionSurface = Color(0xFFF7F7F5)
private val SectionDanger = Color(0xFFB13A2F)

internal enum class SectionManagerMode {
    REORDER,
    EDIT
}

/**
 * Edit mode persists every add/rename/delete immediately (each nested dialog calls its
 * backend callback itself and shows its own loading/error state); the footer button is
 * just "Done" and closes. Reorder mode is unchanged: dragging only rearranges local
 * state, and the footer's "Save order" batches the position updates on click.
 */
@Composable
internal fun SectionManagerDialog(
    sections: List<MenuCategory>,
    itemCounts: Map<String, Int>,
    mode: SectionManagerMode,
    onDismiss: () -> Unit,
    onDoneEditing: (List<MenuCategory>) -> Unit,
    onSaveOrder: (List<MenuCategory>) -> Unit,
    onChangeOrder: () -> Unit,
    onCreateSection: suspend (name: String, displayOrder: Int) -> Result<String>,
    onRenameSection: suspend (sectionId: String, name: String, displayOrder: Int) -> Result<Unit>,
    onDeleteSection: suspend (sectionId: String, deleteItems: Boolean) -> Result<Unit>
) {
    val isPhone = isPhoneMenuWindow()
    val isWidePhone = isWidePhoneWindow()
    val isReordering = mode == SectionManagerMode.REORDER
    var workingSections by remember { mutableStateOf(sections.distinctBy { it.name }.inFilterOrder()) }
    LaunchedEffect(sections) {
        workingSections = sections.distinctBy { it.name }.inFilterOrder()
    }
    var sectionBeingEdited by remember { mutableStateOf<MenuCategory?>(null) }
    var sectionToDelete by remember { mutableStateOf<MenuCategory?>(null) }
    var addSectionOpen by remember { mutableStateOf(false) }
    fun realIndexOf(name: String) = workingSections.filter { it.name != "All" }.indexOfFirst { it.name == name }

    // Same "looks disabled but stays clickable" pattern as Create Menu / Edit Order:
    // reordering needs 2+ real sections, so below that the button dims but a click
    // still surfaces why instead of doing nothing.
    val canReorderSections = workingSections.count { !it.name.isUncategorizedSection() } >= 2
    var reorderBlockedToken by remember { mutableStateOf(0) }
    var showReorderToast by remember { mutableStateOf(false) }

    LaunchedEffect(reorderBlockedToken) {
        if (reorderBlockedToken > 0) {
            showReorderToast = true
            delay(3000)
            showReorderToast = false
        }
    }

    fun tryChangeOrder() {
        if (canReorderSections) {
            onChangeOrder()
        } else {
            reorderBlockedToken++
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f))
                .then(if (isPhone) Modifier.safeDrawingPadding().imePadding() else Modifier)
                .padding(if (isWidePhone) 12.dp else 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogWidth = if (isPhone) minOf(maxWidth, 560.dp) else minOf(maxWidth, 560.dp)
            Column(
                modifier = Modifier.width(dialogWidth).widthIn(max = 560.dp)
                    .fillMaxHeight(if (isWidePhone) 0.94f else 0.82f)
                    .shadow(22.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                    .background(Color.White).border(1.dp, SectionBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (isReordering) "Change section order" else "Edit menu sections",
                            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = SectionInk
                        )
                        Text(
                            if (isReordering) {
                                if (isPhone) "Press and hold the six-dot handle, then drag and drop to change the order."
                                else "Drag and drop the six-dot handle to change the order."
                            } else {
                                "Rename or delete sections, or add a new one."
                            },
                            fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = SectionMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SectionSurface)
                            .border(1.dp, SectionBorder, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close section editor",
                            modifier = Modifier.size(18.dp),
                            tint = SectionInk
                        )
                    }
                }
                HorizontalDivider(color = SectionBorder)
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = if (isWidePhone) 10.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionReorderList(
                        sections = workingSections,
                        isPhone = isPhone,
                        mode = mode,
                        itemCountFor = { section -> itemCounts[section.name] ?: 0 },
                        onReorder = { workingSections = it },
                        onEdit = { sectionBeingEdited = it },
                        onDelete = { sectionToDelete = it }
                    )
                    if (!isReordering) {
                        Spacer(Modifier.size(4.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier.size(44.dp).border(1.dp, SectionInk, RoundedCornerShape(8.dp))
                                    .clickable { addSectionOpen = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Add, "Add new section", Modifier.size(21.dp), tint = SectionInk)
                            }
                        }
                    }
                }
                HorizontalDivider(color = SectionBorder)
                // Box (not just the Row) so the "need 2+ sections" toast can float above
                // "Change position" without nudging this footer's own layout.
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isWidePhone) 6.dp else 10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isReordering) {
                            // Outlined, not filled — Done is the only green/filled action
                            // in this footer, so this one reads as secondary.
                            val changePositionInk = if (canReorderSections) SectionInk else SectionInk.copy(alpha = 0.4f)
                            Button(
                                onClick = { tryChangeOrder() },
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(1.dp, changePositionInk),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = changePositionInk
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.DragIndicator,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = changePositionInk
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Change position", fontFamily = Inter(), fontWeight = FontWeight.Bold, color = changePositionInk)
                            }
                        } else {
                            Spacer(Modifier.size(1.dp))
                        }

                        Button(
                            onClick = {
                                if (isReordering) onSaveOrder(workingSections) else onDoneEditing(workingSections)
                            },
                            shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(containerColor = SectionGreen)
                        ) {
                            Text(
                                if (isReordering) "Save order" else "Done",
                                fontFamily = Inter(), fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    MenuValidationToast(
                        visible = showReorderToast,
                        message = "Add at least 2 sections to change their order.",
                        placement = ToastPlacement.Above,
                        arrowAlignment = Alignment.Start,
                        anchorAlignment = Alignment.TopStart,
                        offsetY = (-46).dp
                    )
                }
            }
        }
    }

    sectionBeingEdited?.let { section ->
        SectionEditorDialog(
            section = section,
            sections = workingSections,
            itemCount = itemCounts[section.name] ?: 0,
            displayOrder = realIndexOf(section.name).coerceAtLeast(0),
            onDismiss = { sectionBeingEdited = null },
            onRenameSection = onRenameSection,
            onRenamed = { updated ->
                workingSections = workingSections.map { if (it.name == section.name) updated else it }.inFilterOrder()
                sectionBeingEdited = null
            }
        )
    }

    sectionToDelete?.let { section ->
        DeleteSectionDialog(
            section = section,
            itemCount = itemCounts[section.name] ?: 0,
            onDismiss = { sectionToDelete = null },
            onDeleteSection = onDeleteSection,
            onDeleted = {
                workingSections = workingSections.filterNot { it.name == section.name }
                sectionToDelete = null
            }
        )
    }

    if (addSectionOpen) {
        AddSectionDialog(
            sections = workingSections,
            displayOrder = workingSections.count { it.name != "All" },
            onDismiss = { addSectionOpen = false },
            onCreateSection = onCreateSection,
            onAdded = { newSection ->
                workingSections = (workingSections + newSection).inFilterOrder()
                addSectionOpen = false
            }
        )
    }
}

@Composable
private fun SectionReorderList(
    sections: List<MenuCategory>,
    isPhone: Boolean,
    mode: SectionManagerMode,
    itemCountFor: (MenuCategory) -> Int,
    onReorder: (List<MenuCategory>) -> Unit,
    onEdit: (MenuCategory) -> Unit,
    onDelete: (MenuCategory) -> Unit
) {
    val rowHeight = 56.dp
    val rowGap = 8.dp
    val rowStep = rowHeight + rowGap
    val listHeight = if (sections.isEmpty()) 0.dp else rowStep * sections.size - rowGap
    var draggingSection by remember { mutableStateOf<String?>(null) }
    var draggedY by remember { mutableFloatStateOf(0f) }
    var gestureOrder by remember { mutableStateOf<List<MenuCategory>?>(null) }
    val latestSections by rememberUpdatedState(sections)
    val latestOnReorder by rememberUpdatedState(onReorder)

    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = listHeight, max = listHeight)) {
        val listWidth = maxWidth
        val density = LocalDensity.current
        val rowStepPx = with(density) { rowStep.toPx() }
        val rowHeightPx = with(density) { rowHeight.toPx() }

        fun slotY(index: Int) = index * rowStepPx
        fun nearestMovableSlot(position: Float, size: Int): Int? {
            if (size <= 1) return null
            val center = position + rowHeightPx / 2f
            return (0 until size).filter { !latestSections[it].name.isUncategorizedSection() }.minByOrNull { index ->
                kotlin.math.abs((slotY(index) + rowHeightPx / 2f) - center)
            }
        }

        sections.forEachIndexed { index, section ->
            key(section.name) {
                val targetOffset = IntOffset(0, slotY(index).roundToInt())
                val animatedOffset by animateIntOffsetAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                    label = "section-slot-${section.name}"
                )
                val isDragging = draggingSection == section.name
                val itemCount = itemCountFor(section)
                val canMove = mode == SectionManagerMode.REORDER && !section.name.isUncategorizedSection() && sections.count { !it.name.isUncategorizedSection() } >= 2
                val dragModifier = if (canMove) {
                    Modifier.pointerInput(section.name, rowStepPx, isPhone) {
                        val startDrag: (Offset) -> Unit = {
                            val current = latestSections.indexOfFirst { it.name == section.name }
                            if (current >= 0) {
                                gestureOrder = latestSections
                                draggedY = slotY(current)
                                draggingSection = section.name
                            }
                        }
                        val endDrag: () -> Unit = {
                            draggingSection = null
                            gestureOrder = null
                        }
                        val moveDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit = { change, amount ->
                            change.consume()
                            if (draggingSection == section.name) {
                                draggedY += amount.y
                                val currentOrder = gestureOrder ?: latestSections
                                val from = currentOrder.indexOfFirst { it.name == section.name }
                                val to = nearestMovableSlot(draggedY, currentOrder.size)
                                if (from >= 0 && to != null && from != to) {
                                    val reordered = currentOrder.toMutableList().also { list ->
                                        list.add(to, list.removeAt(from))
                                    }
                                    gestureOrder = reordered
                                    latestOnReorder(reordered)
                                }
                            }
                        }
                        if (isPhone) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = startDrag, onDragEnd = endDrag, onDragCancel = endDrag, onDrag = moveDrag
                            )
                        } else {
                            detectDragGestures(
                                onDragStart = startDrag, onDragEnd = endDrag, onDragCancel = endDrag, onDrag = moveDrag
                            )
                        }
                    }
                } else Modifier

                SectionRow(
                    section = section,
                    itemCount = itemCount,
                    mode = mode,
                    canMove = canMove,
                    isDragging = isDragging,
                    onEdit = { onEdit(section) },
                    onDelete = { onDelete(section) },
                    dragHandleModifier = dragModifier,
                    modifier = Modifier.width(listWidth).heightIn(min = rowHeight, max = rowHeight)
                        .zIndex(if (isDragging) 2f else 0f)
                        .offset { if (isDragging) IntOffset(0, draggedY.roundToInt()) else animatedOffset }
                        .graphicsLayer {
                            if (isDragging) {
                                scaleX = 1.015f
                                scaleY = 1.015f
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun SectionRow(
    section: MenuCategory,
    itemCount: Int,
    mode: SectionManagerMode,
    canMove: Boolean,
    isDragging: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier
) {
    Row(
        modifier = modifier.shadow(if (isDragging) 9.dp else 0.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)).background(if (isDragging) Color.White else SectionSurface)
            .border(1.dp, if (isDragging) SectionGreen else SectionBorder, RoundedCornerShape(10.dp))
            .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).background(Color.White, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(section.icon.icon, null, Modifier.size(18.dp), tint = SectionInk)
        }
        Spacer(Modifier.size(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                section.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = SectionInk, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (section.name == "All") "Shows every section" else "$itemCount ${if (itemCount == 1) "item" else "items"}",
                fontFamily = Inter(), fontSize = 11.sp, color = SectionMuted
            )
        }
        Spacer(Modifier.size(8.dp))
        if (mode == SectionManagerMode.REORDER) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color.White).border(1.dp, SectionBorder, RoundedCornerShape(8.dp))
                    .then(dragHandleModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (section.name.isUncategorizedSection()) Icons.Outlined.Lock else Icons.Outlined.DragIndicator,
                    if (section.name.isUncategorizedSection()) "Uncategorized stays last" else "Drag and drop ${section.name}",
                    Modifier.size(if (section.name.isUncategorizedSection()) 17.dp else 24.dp),
                    tint = SectionInk
                )
            }
        } else if (section.name == "All") {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color.White).border(1.dp, SectionBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Lock, "All is protected", Modifier.size(17.dp), tint = SectionInk)
            }
        } else {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color.White).border(1.dp, SectionBorder, RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Edit, "Rename ${section.name}", Modifier.size(18.dp), tint = SectionInk)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                    .background(SectionDanger).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    "Delete ${section.name}",
                    Modifier.size(18.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun SectionEditorDialog(
    section: MenuCategory,
    sections: List<MenuCategory>,
    itemCount: Int,
    displayOrder: Int,
    onDismiss: () -> Unit,
    onRenameSection: suspend (sectionId: String, name: String, displayOrder: Int) -> Result<Unit>,
    onRenamed: (MenuCategory) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember(section) { mutableStateOf(section.name) }
    var icon by remember(section) { mutableStateOf(section.icon) }
    var attemptedSubmit by remember(section) { mutableStateOf(false) }
    var status by remember(section) { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    val trimmed = name.trim()
    val isDuplicate = trimmed.length >= 2 &&
        sections.any { it.name != section.name && it.name.equals(trimmed, ignoreCase = true) }
    val valid = trimmed.length in 2..40 && !isDuplicate
    val showValidationError = attemptedSubmit && !valid
    val errorText = when {
        trimmed.isEmpty() -> "Section name is required."
        trimmed.length < 2 -> "Use at least 2 characters."
        isDuplicate -> "A section with this name already exists."
        else -> null
    }
    val isBusy = status is DialogActionStatus.Loading
    val isIdle = status is DialogActionStatus.Idle

    fun submit() {
        if (!valid) {
            attemptedSubmit = true
        } else if (!isBusy) {
            status = DialogActionStatus.Loading("Saving")
            scope.launch {
                onRenameSection(section.id!!, trimmed, displayOrder).fold(
                    onSuccess = { status = DialogActionStatus.Success("$trimmed saved") },
                    onFailure = { error ->
                        status = DialogActionStatus.Failed(message = error.message ?: "Could not save this section.")
                    }
                )
            }
        }
    }

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        title = { Text("Edit section", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            if (isIdle) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Section name", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SectionInk)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= 40) name = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = showValidationError
                        )
                        if (showValidationError && errorText != null) {
                            Text(errorText, fontFamily = Inter(), fontSize = 12.sp, color = SectionDanger)
                        } else {
                            Text(
                                if (itemCount == 0) "This section is empty and can be deleted."
                                else "$itemCount ${if (itemCount == 1) "item uses" else "items use"} this section.",
                                fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = SectionMuted
                            )
                        }
                    }
                    SectionIconPicker(selected = icon, onSelect = { icon = it })
                }
            } else {
                DialogStatusBody(
                    status = status,
                    onRetry = { status = DialogActionStatus.Idle },
                    onCancel = onDismiss,
                    onSuccessSettled = { onRenamed(MenuCategory(trimmed, icon, section.id)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (isIdle) {
                Button(
                    onClick = { submit() },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = SectionGreen)
                ) {
                    Text("Save", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        },
        dismissButton = {
            if (isIdle) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = SectionMuted) }
            }
        }
    )
}

@Composable
private fun DeleteSectionDialog(
    section: MenuCategory,
    itemCount: Int,
    onDismiss: () -> Unit,
    onDeleteSection: suspend (sectionId: String, deleteItems: Boolean) -> Result<Unit>,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    val isUncategorized = section.name.isUncategorizedSection()
    var deleteItemsChecked by remember(section.id) { mutableStateOf(false) }
    val deleteContents = isUncategorized || deleteItemsChecked
    val isBusy = status is DialogActionStatus.Loading
    val isIdle = status is DialogActionStatus.Idle

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        title = {
            Text(
                "Delete ${section.name}?",
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            if (isIdle) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val message = if (itemCount == 0) {
                        "Are you sure you want to delete this section?"
                    } else if (deleteContents) {
                        "Are you sure? The section and its $itemCount " +
                            "${if (itemCount == 1) "item" else "items"} will be permanently deleted."
                    } else {
                        "Are you sure? The section will be removed. If you don't check the box below, " +
                            "you can still find its $itemCount ${if (itemCount == 1) "item" else "items"} " +
                            "in the \"Uncategorized\" section."
                    }
                    Text(message, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = SectionMuted)
                    if (itemCount > 0 && !isUncategorized) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { deleteItemsChecked = !deleteItemsChecked }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (deleteItemsChecked) SectionDanger else Color.White)
                                    .border(
                                        width = if (deleteItemsChecked) 1.dp else 1.5.dp,
                                        color = if (deleteItemsChecked) SectionDanger else Color(0xFF8B8F87),
                                        shape = RoundedCornerShape(5.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (deleteItemsChecked) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Also delete the items inside this section",
                                fontFamily = Inter(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = SectionInk
                            )
                        }
                    }
                }
            } else {
                DialogStatusBody(
                    status = status,
                    onRetry = { status = DialogActionStatus.Idle },
                    onCancel = onDismiss,
                    onSuccessSettled = onDeleted,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (isIdle) {
                Button(
                    enabled = true,
                    onClick = {
                        status = DialogActionStatus.Loading("Deleting")
                        scope.launch {
                            onDeleteSection(section.id!!, deleteContents).fold(
                                onSuccess = { status = DialogActionStatus.Removed("${section.name} deleted") },
                                onFailure = { error ->
                                    status = DialogActionStatus.Failed(message = error.message ?: "Could not delete this section.")
                                }
                            )
                        }
                    },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = SectionDanger)
                ) {
                    Text("Delete", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        },
        dismissButton = {
            if (isIdle) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = SectionMuted) }
            }
        }
    )
}

@Composable
private fun AddSectionDialog(
    sections: List<MenuCategory>,
    displayOrder: Int,
    onDismiss: () -> Unit,
    onCreateSection: suspend (name: String, displayOrder: Int) -> Result<String>,
    onAdded: (MenuCategory) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(MenuCategoryIcon.RESTAURANT_MENU) }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    val trimmed = name.trim()
    val isDuplicate = trimmed.length >= 2 && sections.any { it.name.equals(trimmed, ignoreCase = true) }
    val valid = trimmed.length in 2..40 && !isDuplicate
    val showValidationError = attemptedSubmit && !valid
    val errorText = when {
        trimmed.isEmpty() -> "Section name is required."
        trimmed.length < 2 -> "Use at least 2 characters."
        isDuplicate -> "A section with this name already exists."
        else -> null
    }
    val isBusy = status is DialogActionStatus.Loading
    val isIdle = status is DialogActionStatus.Idle
    var createdSection by remember { mutableStateOf<MenuCategory?>(null) }

    fun submit() {
        if (!valid) {
            attemptedSubmit = true
        } else if (!isBusy) {
            status = DialogActionStatus.Loading("Adding")
            scope.launch {
                onCreateSection(trimmed, displayOrder).fold(
                    onSuccess = { newId ->
                        createdSection = MenuCategory(trimmed, icon, newId)
                        status = DialogActionStatus.Success("$trimmed added")
                    },
                    onFailure = { error ->
                        status = DialogActionStatus.Failed(message = error.message ?: "Could not create this section.")
                    }
                )
            }
        }
    }

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        title = { Text("Add section", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            if (isIdle) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Section name", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SectionInk)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= 40) name = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("For example: Salads") },
                            singleLine = true,
                            isError = showValidationError
                        )
                        if (showValidationError && errorText != null) {
                            Text(errorText, fontFamily = Inter(), fontSize = 12.sp, color = SectionDanger)
                        }
                    }
                    SectionIconPicker(selected = icon, onSelect = { icon = it })
                }
            } else {
                DialogStatusBody(
                    status = status,
                    onRetry = { status = DialogActionStatus.Idle },
                    onCancel = onDismiss,
                    onSuccessSettled = { createdSection?.let(onAdded) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (isIdle) {
                Button(
                    // Stays clickable even while invalid (enabled=true, no `enabled`
                    // override here) so a click can still reach submit()'s validation
                    // path and surface the error above — it only *looks* disabled
                    // (dimmed) until the name is filled in.
                    onClick = { submit() },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (valid) SectionGreen else SectionGreen.copy(alpha = 0.45f)
                    )
                ) {
                    Text("Add", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        },
        dismissButton = {
            if (isIdle) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = SectionMuted) }
            }
        }
    )
}

@Composable
private fun SectionIconPicker(
    selected: MenuCategoryIcon,
    onSelect: (MenuCategoryIcon) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Section icon", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SectionInk)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuCategoryIcon.entries.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        val isSelected = option == selected
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) SectionGreen else SectionSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) SectionGreen else SectionBorder,
                                    RoundedCornerShape(9.dp)
                                )
                                .clickable { onSelect(option) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = option.label,
                                modifier = Modifier.size(22.dp),
                                tint = if (isSelected) Color.White else SectionInk
                            )
                        }
                    }
                }
            }
        }
    }
}
