package com.saporini.mobile_desktop.pos.menu.ui

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlin.math.roundToInt

private val SectionGreen = Color(0xFF94A27F)
private val SectionInk = Color(0xFF222426)
private val SectionMuted = Color(0xFF747572)
private val SectionBorder = Color(0xFFE4E5E1)
private val SectionSurface = Color(0xFFF7F7F5)
private val SectionDanger = Color(0xFFB13A2F)

internal enum class SectionManagerMode {
    REORDER,
    EDIT
}

@Composable
internal fun OrderTypeDialog(
    onDismiss: () -> Unit,
    onMenuItems: () -> Unit,
    onSections: () -> Unit
) {
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Change order", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OrderTypeRow(
                    icon = Icons.Outlined.RestaurantMenu,
                    title = "Menu items",
                    description = "Move dishes into the order guests should see.",
                    onClick = onMenuItems
                )
                OrderTypeRow(
                    icon = Icons.Outlined.Menu,
                    title = "Sections",
                    description = "Drag and drop sections into the order guests should see.",
                    onClick = onSections
                )
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = Inter(), color = SectionMuted)
            }
        }
    )
}

@Composable
private fun OrderTypeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(SectionSurface).border(1.dp, SectionBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).background(Color.White, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = SectionInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = SectionInk)
            Text(description, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = SectionMuted)
        }
    }
}

@Composable
internal fun SectionManagerDialog(
    sections: List<String>,
    itemCounts: Map<String, Int>,
    mode: SectionManagerMode,
    onDismiss: () -> Unit,
    onSave: (List<String>, Map<String, String>) -> Unit
) {
    val isPhone = isPhoneMenuWindow()
    val isWidePhone = isWidePhoneWindow()
    val isReordering = mode == SectionManagerMode.REORDER
    var workingSections by remember(sections) { mutableStateOf(sections.distinct()) }
    var sectionOrigins by remember(sections) { mutableStateOf(sections.associateWith { it }) }
    var sectionBeingEdited by remember { mutableStateOf<String?>(null) }
    var sectionToDelete by remember { mutableStateOf<String?>(null) }
    var addSectionOpen by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f))
                .then(if (isPhone) Modifier.safeDrawingPadding().imePadding() else Modifier)
                .padding(if (isWidePhone) 12.dp else 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogWidth = if (isPhone) minOf(maxWidth, 560.dp) else minOf(maxWidth * 0.48f, 560.dp)
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
                                "Rename sections, delete empty sections, or add a new one."
                            },
                            fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = SectionMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Close section editor", tint = SectionInk)
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
                        itemCountFor = { section -> itemCounts[sectionOrigins[section] ?: section] ?: 0 },
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
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isWidePhone) 6.dp else 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = SectionMuted) }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = {
                            val renames = sectionOrigins.entries
                                .filter { (current, original) -> current != original }
                                .associate { (current, original) -> original to current }
                            onSave(workingSections, renames)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SectionGreen)
                    ) {
                        Text(
                            if (isReordering) "Save order" else "Save sections",
                            fontFamily = Inter(), fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    sectionBeingEdited?.let { section ->
        val originalSection = sectionOrigins[section] ?: section
        SectionEditorDialog(
            section = section,
            sections = workingSections,
            itemCount = itemCounts[originalSection] ?: 0,
            onDismiss = { sectionBeingEdited = null },
            onRename = { newName ->
                workingSections = workingSections.map { if (it == section) newName else it }
                sectionOrigins = sectionOrigins - section + (newName to originalSection)
                sectionBeingEdited = null
            }
        )
    }

    sectionToDelete?.let { section ->
        DeleteSectionDialog(
            section = section,
            onDismiss = { sectionToDelete = null },
            onDelete = {
                workingSections = workingSections.filterNot { it == section }
                sectionOrigins = sectionOrigins - section
                sectionToDelete = null
            }
        )
    }

    if (addSectionOpen) {
        AddSectionDialog(
            sections = workingSections,
            onDismiss = { addSectionOpen = false },
            onAdd = { section ->
                workingSections = workingSections + section
                sectionOrigins = sectionOrigins + (section to section)
                addSectionOpen = false
            }
        )
    }

}

@Composable
private fun SectionReorderList(
    sections: List<String>,
    isPhone: Boolean,
    mode: SectionManagerMode,
    itemCountFor: (String) -> Int,
    onReorder: (List<String>) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val rowHeight = 56.dp
    val rowGap = 8.dp
    val rowStep = rowHeight + rowGap
    val listHeight = if (sections.isEmpty()) 0.dp else rowStep * sections.size - rowGap
    var draggingSection by remember { mutableStateOf<String?>(null) }
    var draggedY by remember { mutableFloatStateOf(0f) }
    var gestureOrder by remember { mutableStateOf<List<String>?>(null) }
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
            return (1 until size).minByOrNull { index ->
                kotlin.math.abs((slotY(index) + rowHeightPx / 2f) - center)
            }
        }

        sections.forEachIndexed { index, section ->
            key(section) {
                val targetOffset = IntOffset(0, slotY(index).roundToInt())
                val animatedOffset by animateIntOffsetAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                    label = "section-slot-$section"
                )
                val isDragging = draggingSection == section
                val itemCount = itemCountFor(section)
                val canMove = mode == SectionManagerMode.REORDER && section != "All" && sections.size > 2
                val dragModifier = if (canMove) {
                    Modifier.pointerInput(section, rowStepPx, isPhone) {
                        val startDrag: (Offset) -> Unit = {
                            val current = latestSections.indexOf(section)
                            if (current > 0) {
                                gestureOrder = latestSections
                                draggedY = slotY(current)
                                draggingSection = section
                            }
                        }
                        val endDrag: () -> Unit = {
                            draggingSection = null
                            gestureOrder = null
                        }
                        val moveDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit = { change, amount ->
                            change.consume()
                            if (draggingSection == section) {
                                draggedY += amount.y
                                val currentOrder = gestureOrder ?: latestSections
                                val from = currentOrder.indexOf(section)
                                val to = nearestMovableSlot(draggedY, currentOrder.size)
                                if (from > 0 && to != null && from != to) {
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
                    onDelete = { if (itemCount == 0) onDelete(section) },
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
    section: String,
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
            Icon(categoryIcon(section), null, Modifier.size(18.dp), tint = SectionInk)
        }
        Spacer(Modifier.size(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                section, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = SectionInk, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (section == "All") "Default filter" else "$itemCount ${if (itemCount == 1) "item" else "items"}",
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
                    if (section == "All") Icons.Outlined.Lock else Icons.Outlined.DragIndicator,
                    if (section == "All") "All stays first" else "Drag and drop $section",
                    Modifier.size(if (section == "All") 17.dp else 24.dp),
                    tint = SectionInk
                )
            }
        } else if (section == "All") {
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
                Icon(Icons.Outlined.Edit, "Rename $section", Modifier.size(18.dp), tint = SectionInk)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (itemCount == 0) SectionDanger else SectionDanger.copy(alpha = 0.35f))
                    .then(if (itemCount == 0) Modifier.clickable(onClick = onDelete) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    if (itemCount == 0) "Delete $section" else "$section cannot be deleted while it contains items",
                    Modifier.size(18.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun SectionEditorDialog(
    section: String,
    sections: List<String>,
    itemCount: Int,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember(section) { mutableStateOf(section) }
    val trimmed = name.trim()
    val valid = trimmed.length in 2..40 && sections.none { it != section && it.equals(trimmed, ignoreCase = true) }
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit section", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Section name", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SectionInk)
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 40) name = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text(
                    if (itemCount == 0) "This section is empty and can be deleted."
                    else "$itemCount ${if (itemCount == 1) "item" else "items"} use this section. Move them before deleting it.",
                    fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = SectionMuted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(trimmed) }, enabled = valid) {
                Text("Save", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = SectionGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SectionMuted) }
        }
    )
}

@Composable
private fun DeleteSectionDialog(section: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $section?", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Text("Are you sure you want to delete this empty section?", fontFamily = Inter(), fontSize = 13.sp, color = SectionMuted)
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = SectionDanger)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SectionMuted) } }
    )
}

@Composable
private fun AddSectionDialog(sections: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val valid = trimmed.length in 2..40 && sections.none { it.equals(trimmed, ignoreCase = true) }
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add section", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Section name", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SectionInk)
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 40) name = it },
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("For example: Salads") }, singleLine = true
                )
                if (trimmed.isNotEmpty() && !valid) {
                    Text("Use a unique name with at least 2 characters.", fontFamily = Inter(), fontSize = 12.sp, color = SectionDanger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(trimmed) }, enabled = valid) {
                Text("Add", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = SectionGreen)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SectionMuted) } }
    )
}
