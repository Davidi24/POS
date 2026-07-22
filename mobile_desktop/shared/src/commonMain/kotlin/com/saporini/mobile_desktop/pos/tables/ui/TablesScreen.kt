package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ZoomOutMap
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import mobile_desktop.shared.generated.resources.plan
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TablesScreen(
    modifier: Modifier = Modifier,
    canEditLayout: Boolean = false,
    onAddItemsRequested: () -> Unit = {}
) {
    var selectedFloor by remember { mutableStateOf(FloorOption.FIRST) }
    var selectedStatuses by remember { mutableStateOf<Set<TableVisualState>>(emptySet()) }
    var selectedTables by remember { mutableStateOf<List<FloorPlanTable>>(emptyList()) }
    var mergeMode by remember { mutableStateOf(false) }
    var mergeSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mergedGroups by remember { mutableStateOf<List<Set<String>>>(emptyList()) }
    var mergeGroupColorIndexes by remember { mutableStateOf<Map<Set<String>, Int>>(emptyMap()) }
    var nextMergeColorIndex by remember { mutableStateOf(0) }
    var activeMergeColorIndex by remember { mutableStateOf<Int?>(null) }
    var editingMergeGroup by remember { mutableStateOf<Set<String>?>(null) }
    var tableOrderOverrides by remember { mutableStateOf<Map<String, TableOrderOverride>>(emptyMap()) }
    var nextOrderNumber by remember { mutableStateOf(1246) }
    var newOrderTables by remember { mutableStateOf<List<FloorPlanTable>>(emptyList()) }
    var layoutTables by remember { mutableStateOf(floorPlanTables) }
    var searchQuery by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }
    var selectedEditTableLabel by remember { mutableStateOf<String?>(null) }
    var customPlanBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showBundledPlan by remember { mutableStateOf(floorPlanTables.isNotEmpty()) }
    var planOffset by remember { mutableStateOf(Offset.Zero) }
    var planMoveMode by remember { mutableStateOf(false) }
    var planScale by remember { mutableStateOf(1f) }
    val customPlan = remember(customPlanBytes) {
        customPlanBytes?.decodeToImageBitmap()
    }
    val currentTables = layoutTables.map { table ->
        tableOrderOverrides[table.label]?.applyTo(table) ?: table
    }.filter { table ->
        searchQuery.isBlank() || table.label.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mergeMode) {
                            MergeModeToolbar(
                                selectedCount = mergeSelection.size,
                                onCancel = {
                                    mergeMode = false
                                    mergeSelection = emptySet()
                                    activeMergeColorIndex = null
                                    editingMergeGroup = null
                                },
                                onDone = {
                                    if (mergeSelection.size >= 2) {
                                        val nextGroups = mergedGroups
                                            .filterNot { it == editingMergeGroup }
                                            .filterNot { group -> group.any { it in mergeSelection } }
                                        val colorIndex = activeMergeColorIndex ?: nextMergeColorIndex
                                        mergedGroups = nextGroups.plus(listOf(mergeSelection))
                                        mergeGroupColorIndexes = mergeGroupColorIndexes
                                            .filterKeys { it in nextGroups }
                                            .plus(mergeSelection to colorIndex)
                                        nextMergeColorIndex = maxOf(nextMergeColorIndex, colorIndex + 1)
                                        mergeMode = false
                                        mergeSelection = emptySet()
                                        activeMergeColorIndex = null
                                        editingMergeGroup = null
                                    }
                                }
                            )
                        } else {
                            TableLayoutToolbar(
                                searchQuery = searchQuery,
                                onSearchQueryChanged = { searchQuery = it },
                                editMode = editMode,
                                hasBackground = customPlan != null || showBundledPlan,
                                planMoveMode = planMoveMode,
                                selectedTableLabel = selectedEditTableLabel,
                                onEditModeChanged = {
                                    editMode = it
                                    selectedEditTableLabel = null
                                    if (!it) planMoveMode = false
                                    if (it) searchQuery = ""
                                },
                                onAddTable = { shape, chairs ->
                                    val newTable = newPreviewTable(
                                        tables = layoutTables,
                                        shape = shape,
                                        seatCount = chairs
                                    )
                                    layoutTables = layoutTables + newTable
                                    selectedEditTableLabel = newTable.label
                                },
                                onRotateSelectedTable = { change ->
                                    selectedEditTableLabel?.let { label ->
                                        layoutTables = layoutTables.map { table ->
                                            if (table.label == label) {
                                                table.copy(
                                                    rotationDegrees =
                                                        (table.rotationDegrees + change + 360f) % 360f
                                                )
                                            } else table
                                        }
                                    }
                                },
                                onScaleSelectedTable = { change ->
                                    selectedEditTableLabel?.let { label ->
                                        layoutTables = layoutTables.map { table ->
                                            if (table.label == label) {
                                                table.copy(
                                                    scale = (table.scale + change).coerceIn(0.35f, 1.20f)
                                                )
                                            } else table
                                        }
                                    }
                                },
                                onDeleteSelectedTable = {
                                    selectedEditTableLabel?.let { label ->
                                        layoutTables = layoutTables.filterNot { it.label == label }
                                        mergedGroups = mergedGroups
                                            .map { it - label }
                                            .filter { it.size >= 2 }
                                        selectedEditTableLabel = null
                                    }
                                },
                                onBackgroundSelected = {
                                    customPlanBytes = it
                                    showBundledPlan = false
                                    planOffset = Offset.Zero
                                    planMoveMode = false
                                    planScale = 1f
                                },
                                onRemoveBackground = {
                                    customPlanBytes = null
                                    showBundledPlan = false
                                    planOffset = Offset.Zero
                                    planMoveMode = false
                                    planScale = 1f
                                },
                                onPlanMoveModeChanged = { planMoveMode = it },
                                onScalePlan = { change ->
                                    planScale = (planScale + change).coerceIn(0.60f, 1.40f)
                                },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(
                                        x = if (editMode) {
                                            0.dp
                                        } else if (canEditLayout) {
                                            (-180).dp
                                        } else {
                                            (-148).dp
                                        }
                                    )
                            )
                            if (!editMode) {
                                FloorSwitcher(
                                    selectedFloor = selectedFloor,
                                    onFloorSelected = { selectedFloor = it },
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .offset(x = if (canEditLayout) 90.dp else 128.dp)
                                )
                                if (canEditLayout) {
                                    EditLayoutIconButton(
                                        onClick = {
                                            editMode = true
                                            selectedEditTableLabel = null
                                            planMoveMode = false
                                            searchQuery = ""
                                        },
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .offset(x = 270.dp)
                                    )
                                }
                            }
                        }
                    }

                    FloorPlanTables(
                        tables = currentTables,
                        selectedStatuses = selectedStatuses,
                        mergeMode = mergeMode,
                        mergeSelection = mergeSelection,
                        mergedGroups = mergedGroups,
                        mergeGroupColorIndexes = mergeGroupColorIndexes,
                        activeMergeColorIndex = activeMergeColorIndex,
                        editingMergeGroup = editingMergeGroup,
                        editMode = editMode && canEditLayout,
                        customPlan = customPlan,
                        showBundledPlan = showBundledPlan,
                        planOffset = planOffset,
                        planScale = planScale,
                        planMoveMode = planMoveMode,
                        onPlanMove = { x, y -> planOffset += Offset(x, y) },
                        selectedEditTableLabel = selectedEditTableLabel,
                        onTableMoveDelta = { label, deltaX, deltaY ->
                            layoutTables = layoutTables.map {
                                if (it.label == label) {
                                    it.copy(
                                        x = (it.x + deltaX).coerceIn(0.04f, 0.96f),
                                        y = (it.y + deltaY).coerceIn(0.04f, 0.96f)
                                    )
                                } else it
                            }
                        },
                        onEditTableSelected = {
                            planMoveMode = false
                            selectedEditTableLabel = it.label
                        },
                        onEditSelectionCleared = {
                            selectedEditTableLabel = null
                        },
                        onTableClick = { table ->
                            if (mergeMode) {
                                val clickedGroup = mergedGroups.firstOrNull { table.label in it }
                                val labelsToToggle = if (
                                    clickedGroup != null &&
                                    clickedGroup != editingMergeGroup
                                ) {
                                    clickedGroup
                                } else {
                                    setOf(table.label)
                                }

                                mergeSelection = if (labelsToToggle.all { it in mergeSelection }) {
                                    mergeSelection - labelsToToggle
                                } else {
                                    mergeSelection + labelsToToggle
                                }
                            } else {
                                val mergedGroup = mergedGroups.firstOrNull { table.label in it }
                                selectedTables = if (mergedGroup != null) {
                                    currentTables.filter { it.label in mergedGroup }
                                } else {
                                    listOf(table)
                                }
                            }
                        }
                    )
                }

                if (!editMode) {
                    StatusFilterOverlays(
                        tables = currentTables,
                        selectedStatuses = selectedStatuses,
                        onAllSelected = { selectedStatuses = emptySet() },
                        onStatusToggled = { status ->
                            selectedStatuses = if (status in selectedStatuses) {
                                selectedStatuses - status
                            } else {
                                selectedStatuses + status
                            }
                        }
                    )
                }
            }

            if (selectedTables.isNotEmpty()) {
                TableDetailsModal(
                    tables = selectedTables,
                    onDismiss = { selectedTables = emptyList() },
                    onAddOrder = {
                        newOrderTables = selectedTables
                        selectedTables = emptyList()
                    },
                    onAddItems = {
                        selectedTables = emptyList()
                        onAddItemsRequested()
                    },
                    onMergeTables = {
                        val initialSelection = selectedTables.map { it.label }.toSet()
                        val existingGroup = mergedGroups.firstOrNull { it == initialSelection }
                        selectedTables = emptyList()
                        mergeMode = true
                        mergeSelection = initialSelection
                        activeMergeColorIndex = if (existingGroup != null) {
                            mergeGroupColorIndexes[existingGroup] ?: mergedGroups.indexOf(existingGroup)
                        } else {
                            nextMergeColorIndex.also { nextMergeColorIndex += 1 }
                        }
                        editingMergeGroup = existingGroup
                    }
                )
            }

            if (newOrderTables.isNotEmpty()) {
                NewOrderModal(
                    initialTables = newOrderTables,
                    allTables = currentTables,
                    onDismiss = { newOrderTables = emptyList() },
                    onCreateOrder = { labels ->
                        val orderLabel = "DI$nextOrderNumber"
                        tableOrderOverrides = tableOrderOverrides + labels.associateWith {
                            TableOrderOverride(
                                orderLabel = orderLabel,
                                statusText = "In Progress"
                            )
                        }
                        nextOrderNumber += 1
                        newOrderTables = emptyList()
                    }
                )
            }
        }
    }
}

@Composable
private fun FloorPlanTables(
    tables: List<FloorPlanTable>,
    selectedStatuses: Set<TableVisualState>,
    mergeMode: Boolean,
    mergeSelection: Set<String>,
    mergedGroups: List<Set<String>>,
    mergeGroupColorIndexes: Map<Set<String>, Int>,
    activeMergeColorIndex: Int?,
    editingMergeGroup: Set<String>?,
    editMode: Boolean,
    customPlan: ImageBitmap?,
    showBundledPlan: Boolean,
    planOffset: Offset,
    planScale: Float,
    planMoveMode: Boolean,
    onPlanMove: (Float, Float) -> Unit,
    selectedEditTableLabel: String?,
    onTableMoveDelta: (String, Float, Float) -> Unit,
    onEditTableSelected: (FloorPlanTable) -> Unit,
    onEditSelectionCleared: () -> Unit,
    onTableClick: (FloorPlanTable) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth(0.98f)
            .aspectRatio(1448f / 1086f)
            .clipToBounds()
    ) {
        val planHeight = maxWidth / (1448f / 1086f)
        val clearSelectionInteractions = remember { MutableInteractionSource() }

        if (editMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
                    .clickable(
                        interactionSource = clearSelectionInteractions,
                        indication = null,
                        onClick = onEditSelectionCleared
                    )
            )
        }

        if (customPlan != null || showBundledPlan) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clipToBounds()
                ) {
                    val imageModifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                x = planOffset.x.roundToInt(),
                                y = planOffset.y.roundToInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = planScale
                            scaleY = planScale
                        }

                    if (customPlan != null) {
                        Image(
                            bitmap = customPlan,
                            contentDescription = "Imported restaurant floor plan",
                            modifier = imageModifier,
                            contentScale = ContentScale.FillBounds
                        )
                    } else {
                        Image(
                            painter = painterResource(Res.drawable.plan),
                            contentDescription = "Restaurant floor plan",
                            modifier = imageModifier,
                            contentScale = ContentScale.FillBounds
                        )
                    }
                }

                if (editMode && planMoveMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                1.dp,
                                Color(0xFF918C84),
                                RoundedCornerShape(10.dp)
                            )
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-16).dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PlanMoveButton("↑") { onPlanMove(0f, -24f) }
                        PlanMoveButton("↓") { onPlanMove(0f, 24f) }
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PlanMoveButton("↑") { onPlanMove(0f, -24f) }
                        PlanMoveButton("↓") { onPlanMove(0f, 24f) }
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (-16).dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PlanMoveButton("←") { onPlanMove(-24f, 0f) }
                        PlanMoveButton("→") { onPlanMove(24f, 0f) }
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PlanMoveButton("←") { onPlanMove(-24f, 0f) }
                        PlanMoveButton("→") { onPlanMove(24f, 0f) }
                    }
                }
            }
        } else if (tables.isEmpty()) {
            EmptyFloorPlan(editMode = editMode)
        }

        val visibleTables = if (selectedStatuses.isEmpty()) {
            tables
        } else {
            tables.filter { it.state in selectedStatuses }
        }

        mergedGroups
            .filterNot { mergeMode && it.any { label -> label in mergeSelection } }
            .forEachIndexed { index, group ->
            MergeGroupBox(
                tables = tables.filter { it.label in group },
                planWidth = maxWidth,
                planHeight = planHeight,
                color = mergeGroupColor(mergeGroupColorIndexes[group] ?: index)
            )
        }

        val removedFromEditedGroup = if (mergeMode) {
            editingMergeGroup.orEmpty() - mergeSelection
        } else {
            emptySet()
        }

        if (mergeMode && mergeSelection.isNotEmpty()) {
            MergeGroupBox(
                tables = tables.filter { it.label in mergeSelection },
                planWidth = maxWidth,
                planHeight = planHeight,
                color = mergeGroupColor(activeMergeColorIndex ?: mergedGroups.size),
                selected = true
            )
        }

        if (removedFromEditedGroup.isNotEmpty()) {
            RemovedMergeTables(
                tables = tables.filter { it.label in removedFromEditedGroup },
                planWidth = maxWidth,
                planHeight = planHeight
            )
        }

        visibleTables.forEach { table ->
            val displayedTable = table.copy(scale = table.scale * planScale)
            val scaledX = 0.5f + (table.x - 0.5f) * planScale
            val scaledY = 0.5f + (table.y - 0.5f) * planScale
            Table(
                shape = table.shape,
                seatCount = table.seatCount,
                label = table.label,
                state = table.state,
                orderLabel = table.orderLabel,
                statusText = table.statusText,
                servedItems = table.servedItems,
                totalItems = table.totalItems,
                scale = displayedTable.scale,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = maxWidth * scaledX - displayedTable.visualWidth() * 0.5f,
                        y = planHeight * scaledY - displayedTable.visualHeight() * 0.5f
                    )
                    .graphicsLayer {
                        rotationZ = table.rotationDegrees
                        translationX = planOffset.x
                        translationY = planOffset.y
                    }
                    .then(
                        if (editMode) {
                            Modifier.border(
                                width = if (selectedEditTableLabel == table.label) 3.dp else 1.dp,
                                color = if (selectedEditTableLabel == table.label) {
                                    Color(0xFF242424)
                                } else {
                                    Color(0xFF8B8B87)
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .focusProperties { canFocus = false }
                    .pointerInput(editMode, table.label, table.rotationDegrees) {
                        if (editMode) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val angle = Math.toRadians(table.rotationDegrees.toDouble())
                                val screenDeltaX =
                                    dragAmount.x * cos(angle).toFloat() -
                                        dragAmount.y * sin(angle).toFloat()
                                val screenDeltaY =
                                    dragAmount.x * sin(angle).toFloat() +
                                        dragAmount.y * cos(angle).toFloat()
                                onTableMoveDelta(
                                    table.label,
                                    screenDeltaX / constraints.maxWidth / planScale,
                                    screenDeltaY / constraints.maxHeight / planScale
                                )
                            }
                        }
                    }
                    .clickable {
                        if (editMode) onEditTableSelected(table) else onTableClick(table)
                    }
            )
        }
    }
}

@Composable
private fun BoxScope.NewOrderModal(
    initialTables: List<FloorPlanTable>,
    allTables: List<FloorPlanTable>,
    onDismiss: () -> Unit,
    onCreateOrder: (Set<String>) -> Unit
) {
    var selectedLabels by remember(initialTables) { mutableStateOf(initialTables.map { it.label }.toSet()) }
    var covers by remember(initialTables) { mutableStateOf(initialTables.sumOf { it.seatCount.coerceAtLeast(1) }.coerceAtLeast(1)) }
    var guestName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedFloor by remember { mutableStateOf(FloorOption.FIRST) }

    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .width(620.dp)
            .height(660.dp)
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "New Order",
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                letterSpacing = 0.sp,
                color = Color(0xFF202124)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(22.dp),
                    tint = Color(0xFF242424)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFE7E1DC), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Step 1 - Confirm Table",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    letterSpacing = 0.sp,
                    color = Color(0xFF202124)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(FloorOption.FIRST, FloorOption.SECOND, FloorOption.THIRD).forEach { floor ->
                        val selected = selectedFloor == floor
                        TextButton(
                            onClick = { selectedFloor = floor },
                            modifier = Modifier
                                .height(38.dp)
                                .width(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color(0xFF4B522A) else Color.White)
                                .border(1.dp, Color(0xFFE7E1DC), RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (selected) Color.White else Color(0xFF242424)
                            )
                        ) {
                            Text(
                                text = floor.label,
                                fontFamily = Inter(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                letterSpacing = 0.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                val pickableTables = allTables.filter { it.state != TableVisualState.Unavailable }
                pickableTables.chunked(4).forEach { rowTables ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTables.forEach { table ->
                            OrderTableOption(
                                table = table,
                                selected = table.label in selectedLabels,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedLabels = if (table.label in selectedLabels) {
                                        selectedLabels - table.label
                                    } else {
                                        selectedLabels + table.label
                                    }
                                }
                            )
                        }
                        repeat(4 - rowTables.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFE7E1DC), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Step 2 - Details",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    letterSpacing = 0.sp,
                    color = Color(0xFF202124)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(
                            text = "Covers",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = 0.sp,
                            color = Color(0xFF303033)
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            StepperButton("-") { covers = (covers - 1).coerceAtLeast(1) }
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFE7E1DC), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = covers.toString(),
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    letterSpacing = 0.sp,
                                    color = Color(0xFF242424)
                                )
                            }
                            StepperButton("+") { covers += 1 }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Guest name (optional)",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = 0.sp,
                            color = Color(0xFF303033)
                        )
                        Spacer(Modifier.height(6.dp))
                        OrderTextField(
                            value = guestName,
                            placeholder = "e.g., John Doe",
                            onValueChange = { guestName = it },
                            singleLine = true,
                            height = 38.dp
                        )
                    }
                }
                Text(
                    text = "Note (optional)",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    color = Color(0xFF303033)
                )
                OrderTextField(
                    value = note,
                    placeholder = "Add any special requests or notes...",
                    onValueChange = { note = it },
                    singleLine = false,
                    height = 56.dp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .width(140.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFBDB8B2), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4B522A))
            ) {
                Text(
                    text = "Cancel",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.sp
                )
            }
            TextButton(
                onClick = { if (selectedLabels.isNotEmpty()) onCreateOrder(selectedLabels) },
                enabled = selectedLabels.isNotEmpty(),
                modifier = Modifier
                    .width(168.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedLabels.isNotEmpty()) Color(0xFF4B522A) else Color(0xFFE7E7E4)),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (selectedLabels.isNotEmpty()) Color.White else Color(0xFF8A8A86),
                    disabledContentColor = Color(0xFF8A8A86)
                )
            ) {
                Text(
                    text = "Create Order",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}

@Composable
internal fun BoxScope.AddItemModal(
    onDismiss: () -> Unit,
    onAddToOrder: () -> Unit
) {
    var selectedMeal by remember { mutableStateOf("Dinner") }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<OrderMenuItem?>(null) }
    var selectedVariant by remember { mutableStateOf("Small") }
    var extraCheese by remember { mutableStateOf(false) }
    var noOnion by remember { mutableStateOf(false) }
    var extraBasil by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf(1) }

    val visibleItems = orderMenuItems.filter { item ->
        (selectedCategory == "All" || item.category == selectedCategory) &&
            (searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true))
    }
    val columns = if (selectedItem == null) 4 else 3

    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.99f)
            .fillMaxHeight(0.98f)
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Add Item",
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                letterSpacing = 0.sp,
                color = Color(0xFF202124)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(22.dp),
                    tint = Color(0xFF242424)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AddItemSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.width(360.dp)
                    )
                    AddItemMealButtons(
                        items = listOf("Lunch", "Dinner", "Drinks"),
                        selected = selectedMeal,
                        onSelected = { selectedMeal = it }
                    )
                }

                Spacer(Modifier.height(14.dp))

                AddItemCategoryButtons(
                    items = listOf("All", "Antipasti", "Pasta", "Pizza", "Dolci", "Beverages"),
                    selected = selectedCategory,
                    onSelected = { selectedCategory = it }
                )

                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    visibleItems.chunked(columns).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            rowItems.forEach { item ->
                                AddItemCard(
                                    item = item,
                                    selected = selectedItem?.name == item.name,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedItem = item
                                        selectedVariant = item.variants.first().name
                                        qty = 1
                                    }
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(92.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFBDB8B2), RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Close",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            letterSpacing = 0.sp
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Items are added one by one. You can keep adding more items.",
                        modifier = Modifier.weight(1f),
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 0.sp,
                        color = Color(0xFF777777),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            selectedItem?.let { item ->
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxSize()
                        .border(0.dp, Color.Transparent)
                ) {
                    AddItemDetailsPanel(
                        item = item,
                        selectedVariant = selectedVariant,
                        onVariantSelected = { selectedVariant = it },
                        extraCheese = extraCheese,
                        onExtraCheese = { extraCheese = it },
                        noOnion = noOnion,
                        onNoOnion = { noOnion = it },
                        extraBasil = extraBasil,
                        onExtraBasil = { extraBasil = it },
                        note = note,
                        onNoteChange = { note = it },
                        qty = qty,
                        onQtyChange = { qty = it.coerceAtLeast(1) },
                        onAddToOrder = onAddToOrder
                    )
                }
            }
        }
    }
}

@Composable
private fun AddItemSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE7E1DC), RoundedCornerShape(8.dp))
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = Color(0xFF303236)
        )
        Spacer(Modifier.width(14.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.sp,
                color = Color(0xFF242424)
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Search items",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            letterSpacing = 0.sp,
                            color = Color(0xFFA3A09C)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun AddItemMealButtons(
    items: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { item ->
            val isSelected = item == selected
            TextButton(
                onClick = { onSelected(item) },
                modifier = Modifier
                    .height(48.dp)
                    .width(88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF4B522A) else Color.White)
                    .border(1.dp, Color(0xFFE7E1DC), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSelected) Color.White else Color(0xFF222426)
                )
            ) {
                Text(
                    text = item,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun AddItemCategoryButtons(
    items: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            val isSelected = item == selected
            TextButton(
                onClick = { onSelected(item) },
                modifier = Modifier
                    .height(46.dp)
                    .width(128.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF4B522A) else Color.White)
                    .border(1.dp, Color(0xFFE7E1DC), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSelected) Color.White else Color(0xFF222426)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = addItemCategoryIcon(item),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isSelected) Color.White else Color(0xFF4B522A)
                    )
                    Text(
                        text = item,
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
    }
}

private fun addItemCategoryIcon(category: String): ImageVector = when (category) {
    "All" -> Icons.Outlined.RestaurantMenu
    "Antipasti" -> Icons.Outlined.Restaurant
    "Pasta" -> Icons.Outlined.Restaurant
    "Pizza" -> Icons.Outlined.LocalPizza
    "Dolci" -> Icons.Outlined.Cake
    "Beverages" -> Icons.Outlined.LocalDrink
    else -> Icons.Outlined.RestaurantMenu
}

@Composable
private fun AddItemCard(
    item: OrderMenuItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFF4B522A) else Color(0xFFE7E1DC),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.12f)
                .clip(RoundedCornerShape(6.dp))
        ) {
            Image(
                painter = painterResource(Res.drawable.auth_login_img),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Available",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.sp,
                    color = Color(0xFF4B522A)
                )
            }
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .shadow(2.dp, RoundedCornerShape(7.dp))
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.White.copy(alpha = 0.94f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.ZoomOutMap,
                    contentDescription = "Expand item",
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF4B522A)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = item.name,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = 0.sp,
            color = Color(0xFF202124),
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.description,
            fontFamily = Inter(),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.sp,
            color = Color(0xFF777777),
            minLines = 2,
            maxLines = 2
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.variants.first().price,
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                letterSpacing = 0.sp,
                color = Color(0xFF4B522A)
            )
            Row(
                modifier = Modifier
                    .width(68.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .border(1.dp, Color(0xFFD8D5D0), RoundedCornerShape(7.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AddCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF4B522A)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Add",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}

@Composable
private fun AddItemDetailsPanel(
    item: OrderMenuItem,
    selectedVariant: String,
    onVariantSelected: (String) -> Unit,
    extraCheese: Boolean,
    onExtraCheese: (Boolean) -> Unit,
    noOnion: Boolean,
    onNoOnion: (Boolean) -> Unit,
    extraBasil: Boolean,
    onExtraBasil: (Boolean) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    qty: Int,
    onQtyChange: (Int) -> Unit,
    onAddToOrder: () -> Unit
) {
    val selectedVariantPrice = item.variants.firstOrNull { it.name == selectedVariant } ?: item.variants.first()
    val extras = (if (extraCheese) 1.50 else 0.0) + (if (extraBasil) 0.0 else 0.0)
    val lineTotal = (selectedVariantPrice.amount + extras) * qty

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Image(
                painter = painterResource(Res.drawable.auth_login_img),
                contentDescription = item.name,
                modifier = Modifier
                    .size(82.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = item.name,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    letterSpacing = 0.sp,
                    color = Color(0xFF202124)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.description,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                    color = Color(0xFF555555),
                    maxLines = 3
                )
            }
        }

        AddItemDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Variants", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.sp)
            item.variants.forEach { variant ->
                OptionRow(
                    label = variant.name,
                    price = variant.price,
                    selected = variant.name == selectedVariant,
                    onClick = { onVariantSelected(variant.name) },
                    radio = true
                )
            }

            AddItemDivider()

            Text("Options", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.sp)
            OptionRow("Extra cheese", "+\$1.50", extraCheese, { onExtraCheese(!extraCheese) })
            OptionRow("No onion", "", noOnion, { onNoOnion(!noOnion) })
            OptionRow("Extra basil", "", extraBasil, { onExtraBasil(!extraBasil) })

            AddItemDivider()

            Text("Add Note", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Color(0xFF666666))
            OrderTextField(
                value = note,
                placeholder = "Add special request or note...",
                onValueChange = onNoteChange,
                singleLine = true,
                height = 42.dp
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text("Qty", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp)
                Spacer(Modifier.height(8.dp))
                AddItemQuantityStepper(
                    qty = qty,
                    onDecrease = { onQtyChange(qty - 1) },
                    onIncrease = { onQtyChange(qty + 1) }
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Line Total", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Color(0xFF666666))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$" + (kotlin.math.round(lineTotal * 100) / 100).toString().let {
                        if (it.substringAfter('.', "").length == 1) "${it}0" else it
                    },
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    letterSpacing = 0.sp,
                    color = Color(0xFF4B522A)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF4B522A))
                .clickable(onClick = onAddToOrder),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Add to Order",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = 0.sp
            )
        }
    }
}

@Composable
private fun AddItemDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE7E1DC))
    )
}

@Composable
private fun AddItemQuantityStepper(
    qty: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE7E1DC), RoundedCornerShape(8.dp))
    ) {
        AddItemQuantitySegment("-", Modifier.width(38.dp), onDecrease)
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(38.dp)
                .border(width = 1.dp, color = Color(0xFFE7E1DC)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = qty.toString(),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = 0.sp,
                color = Color(0xFF242424)
            )
        }
        AddItemQuantitySegment("+", Modifier.width(38.dp), onIncrease)
    }
}

@Composable
private fun AddItemQuantitySegment(
    text: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = 0.sp,
            color = Color(0xFF4B522A)
        )
    }
}

@Composable
private fun OptionRow(
    label: String,
    price: String,
    selected: Boolean,
    onClick: () -> Unit,
    radio: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(if (radio) 50 else 4))
                .border(1.dp, if (selected) Color(0xFF4B522A) else Color(0xFF9F9F9F), RoundedCornerShape(if (radio) 50 else 4))
                .background(if (selected && !radio) Color(0xFF4B522A) else Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (selected && radio) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF4B522A))
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontFamily = Inter(),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = 0.sp,
            color = Color(0xFF242424)
        )
        Text(
            text = price,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.sp,
            color = Color(0xFF242424)
        )
    }
}

@Composable
private fun OrderTableOption(
    table: FloorPlanTable,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFF4B522A) else Color.White)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = Color(0xFFE7E1DC),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = table.label,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                letterSpacing = 0.sp,
                color = if (selected) Color.White else Color(0xFF202124)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${table.seatCount} seats",
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.sp,
                color = if (selected) Color.White.copy(alpha = 0.85f) else Color(0xFF666664)
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF4B522A)
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 38.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE7E1DC), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = 0.sp,
            color = Color(0xFF4B522A)
        )
    }
}

@Composable
private fun OrderTextField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    height: Dp = 38.dp
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE7E1DC), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        singleLine = singleLine,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = Inter(),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 0.sp,
            color = Color(0xFF242424)
        ),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        letterSpacing = 0.sp,
                        color = Color(0xFFA3A09C)
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun MergeGroupBox(
    tables: List<FloorPlanTable>,
    planWidth: Dp,
    planHeight: Dp,
    color: Color,
    selected: Boolean = false
) {
    if (tables.isEmpty()) return

    Canvas(
        modifier = Modifier
            .width(planWidth)
            .height(planHeight)
    ) {
        val padding = 2.dp.toPx()
        val tableRects = tables.map { table ->
            val centerX = size.width * table.x
            val centerY = size.height * table.y
            val halfWidth = table.rotatedVisualWidth().toPx() * 0.5f + padding
            val halfHeight = table.rotatedVisualHeight().toPx() * 0.5f + padding

            Rect(
                left = centerX - halfWidth,
                top = centerY - halfHeight,
                right = centerX + halfWidth,
                bottom = centerY + halfHeight
            )
        }
        val rects = tableRects + connectorRects(tableRects, 28.dp.toPx())
        val fillColor = color.copy(alpha = if (selected) 0.12f else 0.08f)
        tableRects.forEach { rect ->
            drawRect(
                color = fillColor,
                topLeft = Offset(rect.left, rect.top),
                size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
            )
        }

        orthogonalBoundarySegments(rects, snapThreshold = 30f).forEach { edge ->
            drawLine(
                color = color,
                start = edge.first,
                end = edge.second,
                strokeWidth = if (selected) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Square
            )
        }
    }
}

@Composable
private fun RemovedMergeTables(
    tables: List<FloorPlanTable>,
    planWidth: Dp,
    planHeight: Dp
) {
    if (tables.isEmpty()) return

    Canvas(
        modifier = Modifier
            .width(planWidth)
            .height(planHeight)
    ) {
        val padding = 2.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))

        tables.forEach { table ->
            val centerX = size.width * table.x
            val centerY = size.height * table.y
            val width = table.rotatedVisualWidth().toPx() + padding * 2
            val height = table.rotatedVisualHeight().toPx() + padding * 2
            val left = centerX - width * 0.5f
            val top = centerY - height * 0.5f

            drawRoundRect(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF9A9A9A),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), pathEffect = dash)
            )
        }
    }
}

@Composable
private fun MergeModeToolbar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val canDone = selectedCount >= 2

    Row(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE8E8E4), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Select tables",
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = 0.sp,
            color = Color(0xFF1F2322),
            maxLines = 1,
            softWrap = false
        )
        TextButton(
            onClick = onCancel,
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF666664))
        ) {
            Text(
                text = "Cancel",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.sp
            )
        }
        TextButton(
            onClick = onDone,
            enabled = canDone,
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (canDone) Color(0xFF4B522A) else Color(0xFFE7E7E4)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (canDone) Color.White else Color(0xFF8A8A86),
                disabledContentColor = Color(0xFF8A8A86)
            )
        ) {
            Text(
                text = "Save",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.sp
            )
        }
    }
}

@Composable
private fun BoxScope.StatusFilterOverlays(
    tables: List<FloorPlanTable>,
    selectedStatuses: Set<TableVisualState>,
    onAllSelected: () -> Unit,
    onStatusToggled: (TableVisualState) -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(top = 14.dp)
            .width(166.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TableStatusCard(
            label = "All",
            count = tables.size,
            icon = Icons.Filled.CheckCircle,
            iconBackground = Color(0xFFE8ECE7),
            iconTint = Color(0xFF1F2322),
            selected = selectedStatuses.isEmpty(),
            onClick = onAllSelected
        )
        TableStatusCard(
            label = "Free",
            count = statusCount(tables, TableVisualState.Free),
            icon = Icons.Filled.CheckCircle,
            iconBackground = Color(0xFFE5EBD8),
            iconTint = Color(0xFF4B522A),
            selected = TableVisualState.Free in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.Free) }
        )
        TableStatusCard(
            label = "Occupied",
            count = statusCount(tables, TableVisualState.Occupied),
            icon = Icons.Filled.Person,
            iconBackground = Color(0xFFFFE0D5),
            iconTint = Color(0xFFB85E3B),
            selected = TableVisualState.Occupied in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.Occupied) }
        )
    }

    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 14.dp)
            .width(166.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TableStatusCard(
            label = "Reserved",
            count = statusCount(tables, TableVisualState.Reserved),
            icon = Icons.Filled.Bookmark,
            iconBackground = Color(0xFFFFE8C4),
            iconTint = Color(0xFFC47A18),
            selected = TableVisualState.Reserved in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.Reserved) }
        )
        TableStatusCard(
            label = "Bill Pending",
            count = statusCount(tables, TableVisualState.BillPending),
            icon = Icons.Filled.Payments,
            iconBackground = Color(0xFFDCEBFF),
            iconTint = Color(0xFF2F6FB1),
            selected = TableVisualState.BillPending in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.BillPending) }
        )
        TableStatusCard(
            label = "Unavailable",
            count = statusCount(tables, TableVisualState.Unavailable),
            icon = Icons.Filled.Cancel,
            iconBackground = Color(0xFFE2E2E2),
            iconTint = Color(0xFF222222),
            selected = TableVisualState.Unavailable in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.Unavailable) }
        )
    }
}

@Composable
private fun TableStatusCard(
    label: String,
    count: Int,
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) iconBackground else Color.White)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) iconTint else Color(0xFFE8E8E4),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = iconTint
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.sp,
            color = Color(0xFF1F2322)
        )
        Text(
            text = count.toString(),
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = 0.sp,
            color = Color(0xFF1F2322)
        )
    }
}

@Composable
private fun FloorSwitcher(
    selectedFloor: FloorOption,
    onFloorSelected: (FloorOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(276.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF5F2F0))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(FloorOption.FIRST, FloorOption.SECOND).forEach { floor ->
            val selected = floor == selectedFloor
            TextButton(
                onClick = { onFloorSelected(floor) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Color(0xFF4B522A) else Color.Transparent),
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (selected) Color.White else Color(0xFF666664)
                )
            ) {
                Text(
                    text = floor.label,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}

@Composable
private fun BoxScope.EmptyFloorPlan(editMode: Boolean) {
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No floor layout yet",
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            color = Color(0xFF303033)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (editMode) {
                "Import a plan or add your first table above."
            } else {
                "An administrator has not configured this floor."
            },
            fontFamily = Inter(),
            fontSize = 14.sp,
            color = Color(0xFF777777)
        )
    }
}

@Composable
private fun PlanMoveButton(
    symbol: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .shadow(5.dp, RoundedCornerShape(50))
            .background(Color(0xFF4B522A), RoundedCornerShape(50))
            .border(2.dp, Color.White, RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontFamily = Inter(),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )
    }
}

private fun newPreviewTable(
    tables: List<FloorPlanTable>,
    shape: TableShape,
    seatCount: Int
): FloorPlanTable {
    val nextNumber = (tables.mapNotNull {
        it.label.removePrefix("T").toIntOrNull()
    }.maxOrNull() ?: 0) + 1

    return FloorPlanTable(
        x = 0.5f,
        y = 0.5f,
        shape = shape,
        seatCount = seatCount,
        label = "T${nextNumber.toString().padStart(2, '0')}"
    )
}

private enum class FloorOption(val label: String) {
    FIRST("1st Floor"),
    SECOND("2nd Floor"),
    THIRD("3rd Floor")
}

private fun statusCount(tables: List<FloorPlanTable>, status: TableVisualState): Int =
    tables.count { it.state == status }

private fun mergeGroupColor(index: Int): Color {
    val colors = listOf(
        Color(0xFF111111),
        Color(0xFF2F6FB1),
        Color(0xFFC47A18),
        Color(0xFF7B3FA1),
        Color(0xFF0F8B6F),
        Color(0xFFB85E3B)
    )
    return colors[index % colors.size]
}

private fun orthogonalBoundarySegments(
    rects: List<Rect>,
    snapThreshold: Float
): List<Pair<Offset, Offset>> {
    if (rects.isEmpty()) return emptyList()

    val xMap = snapValues(rects.flatMap { listOf(it.left, it.right) }, snapThreshold)
    val yMap = snapValues(rects.flatMap { listOf(it.top, it.bottom) }, snapThreshold)
    val snappedRects = rects.map {
        Rect(
            left = xMap.getValue(it.left),
            top = yMap.getValue(it.top),
            right = xMap.getValue(it.right),
            bottom = yMap.getValue(it.bottom)
        )
    }

    val xs = snappedRects.flatMap { listOf(it.left, it.right) }.distinct().sorted()
    val ys = snappedRects.flatMap { listOf(it.top, it.bottom) }.distinct().sorted()
    if (xs.size < 2 || ys.size < 2) return emptyList()

    val filled = mutableSetOf<Pair<Int, Int>>()
    for (xIndex in 0 until xs.lastIndex) {
        for (yIndex in 0 until ys.lastIndex) {
            val centerX = (xs[xIndex] + xs[xIndex + 1]) * 0.5f
            val centerY = (ys[yIndex] + ys[yIndex + 1]) * 0.5f
            if (snappedRects.any { centerX >= it.left && centerX <= it.right && centerY >= it.top && centerY <= it.bottom }) {
                filled.add(xIndex to yIndex)
            }
        }
    }
    fillEnclosedCells(filled, xs.lastIndex, ys.lastIndex)

    val edges = mutableListOf<Pair<Offset, Offset>>()
    filled.forEach { cell ->
        val xIndex = cell.first
        val yIndex = cell.second
        val left = xs[xIndex]
        val right = xs[xIndex + 1]
        val top = ys[yIndex]
        val bottom = ys[yIndex + 1]

        if ((xIndex to yIndex - 1) !in filled) edges.add(Offset(left, top) to Offset(right, top))
        if ((xIndex + 1 to yIndex) !in filled) edges.add(Offset(right, top) to Offset(right, bottom))
        if ((xIndex to yIndex + 1) !in filled) edges.add(Offset(right, bottom) to Offset(left, bottom))
        if ((xIndex - 1 to yIndex) !in filled) edges.add(Offset(left, bottom) to Offset(left, top))
    }

    return mergeCollinearEdges(edges)
}

private fun orthogonalUnionPath(rects: List<Rect>): Path? {
    if (rects.isEmpty()) return null

    val xs = rects.flatMap { listOf(it.left, it.right) }.distinct().sorted()
    val ys = rects.flatMap { listOf(it.top, it.bottom) }.distinct().sorted()
    if (xs.size < 2 || ys.size < 2) return null

    val filled = mutableSetOf<Pair<Int, Int>>()
    for (xIndex in 0 until xs.lastIndex) {
        for (yIndex in 0 until ys.lastIndex) {
            val centerX = (xs[xIndex] + xs[xIndex + 1]) * 0.5f
            val centerY = (ys[yIndex] + ys[yIndex + 1]) * 0.5f
            if (rects.any { centerX >= it.left && centerX <= it.right && centerY >= it.top && centerY <= it.bottom }) {
                filled.add(xIndex to yIndex)
            }
        }
    }

    val edges = mutableListOf<Pair<Offset, Offset>>()
    filled.forEach { cell ->
        val xIndex = cell.first
        val yIndex = cell.second
        val left = xs[xIndex]
        val right = xs[xIndex + 1]
        val top = ys[yIndex]
        val bottom = ys[yIndex + 1]

        if ((xIndex to yIndex - 1) !in filled) edges.add(Offset(left, top) to Offset(right, top))
        if ((xIndex + 1 to yIndex) !in filled) edges.add(Offset(right, top) to Offset(right, bottom))
        if ((xIndex to yIndex + 1) !in filled) edges.add(Offset(right, bottom) to Offset(left, bottom))
        if ((xIndex - 1 to yIndex) !in filled) edges.add(Offset(left, bottom) to Offset(left, top))
    }
    if (edges.isEmpty()) return null

    val remaining = edges.toMutableList()
    val first = remaining.removeAt(0)
    val points = mutableListOf(first.first, first.second)
    var current = first.second

    while (remaining.isNotEmpty()) {
        val nextIndex = remaining.indexOfFirst { it.first.closeTo(current) }
        if (nextIndex == -1) break
        val next = remaining.removeAt(nextIndex)
        points.add(next.second)
        current = next.second
    }

    val snappedPoints = snapNearOutlinePoints(points, threshold = 18f)
    if (snappedPoints.size < 3) return null

    val path = Path()
    path.moveTo(snappedPoints.first().x, snappedPoints.first().y)
    snappedPoints.drop(1).forEach { path.lineTo(it.x, it.y) }
    path.close()
    return path
}

private fun Offset.closeTo(other: Offset): Boolean =
    kotlin.math.abs(x - other.x) < 0.5f && kotlin.math.abs(y - other.y) < 0.5f

private fun mergeCollinearEdges(edges: List<Pair<Offset, Offset>>): List<Pair<Offset, Offset>> {
    val merged = mutableListOf<Pair<Offset, Offset>>()

    edges.groupBy { edge -> edge.first.y to edge.second.y }
        .filterKeys { it.first == it.second }
        .forEach { (key, horizontalEdges) ->
            val y = key.first
            val intervals = horizontalEdges
                .map { edge -> minOf(edge.first.x, edge.second.x) to maxOf(edge.first.x, edge.second.x) }
                .sortedBy { it.first }
            merged += mergeIntervals(intervals).map { interval ->
                Offset(interval.first, y) to Offset(interval.second, y)
            }
        }

    edges.groupBy { edge -> edge.first.x to edge.second.x }
        .filterKeys { it.first == it.second }
        .forEach { (key, verticalEdges) ->
            val x = key.first
            val intervals = verticalEdges
                .map { edge -> minOf(edge.first.y, edge.second.y) to maxOf(edge.first.y, edge.second.y) }
                .sortedBy { it.first }
            merged += mergeIntervals(intervals).map { interval ->
                Offset(x, interval.first) to Offset(x, interval.second)
            }
        }

    return merged
}

private fun fillEnclosedCells(
    filled: MutableSet<Pair<Int, Int>>,
    xCount: Int,
    yCount: Int
) {
    val outside = mutableSetOf<Pair<Int, Int>>()
    val queue = ArrayDeque<Pair<Int, Int>>()

    fun enqueue(cell: Pair<Int, Int>) {
        val x = cell.first
        val y = cell.second
        if (x !in 0 until xCount || y !in 0 until yCount) return
        if (cell in filled || cell in outside) return
        outside.add(cell)
        queue.add(cell)
    }

    for (x in 0 until xCount) {
        enqueue(x to 0)
        enqueue(x to yCount - 1)
    }
    for (y in 0 until yCount) {
        enqueue(0 to y)
        enqueue(xCount - 1 to y)
    }

    while (queue.isNotEmpty()) {
        val cell = queue.removeFirst()
        val x = cell.first
        val y = cell.second
        enqueue(x - 1 to y)
        enqueue(x + 1 to y)
        enqueue(x to y - 1)
        enqueue(x to y + 1)
    }

    for (x in 0 until xCount) {
        for (y in 0 until yCount) {
            val cell = x to y
            if (cell !in filled && cell !in outside) {
                filled.add(cell)
            }
        }
    }
}

private fun mergeIntervals(intervals: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    if (intervals.isEmpty()) return emptyList()

    val merged = mutableListOf<Pair<Float, Float>>()
    var currentStart = intervals.first().first
    var currentEnd = intervals.first().second

    intervals.drop(1).forEach { interval ->
        if (interval.first <= currentEnd + 0.5f) {
            currentEnd = maxOf(currentEnd, interval.second)
        } else {
            merged.add(currentStart to currentEnd)
            currentStart = interval.first
            currentEnd = interval.second
        }
    }
    merged.add(currentStart to currentEnd)
    return merged
}

private fun snapNearOutlinePoints(points: List<Offset>, threshold: Float): List<Offset> {
    val xMap = snapValues(points.map { it.x }, threshold)
    val yMap = snapValues(points.map { it.y }, threshold)

    return points
        .map { point -> Offset(xMap.getValue(point.x), yMap.getValue(point.y)) }
        .fold(mutableListOf<Offset>()) { result, point ->
            if (result.lastOrNull()?.closeTo(point) != true) result.add(point)
            result
        }
}

private fun snapValues(values: List<Float>, threshold: Float): Map<Float, Float> {
    val sorted = values.distinct().sorted()
    val result = mutableMapOf<Float, Float>()
    var cluster = mutableListOf<Float>()

    fun flushCluster() {
        if (cluster.isEmpty()) return
        val snapped = cluster.average().toFloat()
        cluster.forEach { result[it] = snapped }
        cluster = mutableListOf()
    }

    sorted.forEach { value ->
        val average = if (cluster.isEmpty()) value else cluster.average().toFloat()
        if (cluster.isNotEmpty() && kotlin.math.abs(value - average) > threshold) {
            flushCluster()
        }
        cluster.add(value)
    }
    flushCluster()
    return result
}

private fun connectorRects(rects: List<Rect>, thickness: Float): List<Rect> {
    if (rects.size < 2) return emptyList()

    val connectors = mutableListOf<Rect>()
    rects.zipWithNext().forEach { (from, to) ->
        connectors += straightConnectorRect(from, to, maxGap = thickness * 2.25f, thickness = thickness)
    }

    for (fromIndex in rects.indices) {
        for (toIndex in fromIndex + 1 until rects.size) {
            if (toIndex == fromIndex + 1) continue
            val from = rects[fromIndex]
            val to = rects[toIndex]
            connectors += straightConnectorRect(from, to, maxGap = thickness * 2.25f, thickness = thickness)
        }
    }

    return connectors
}

private fun straightConnectorRect(from: Rect, to: Rect, maxGap: Float, thickness: Float): List<Rect> {
    val horizontalGap = maxOf(0f, maxOf(from.left, to.left) - minOf(from.right, to.right))
    val verticalGap = maxOf(0f, maxOf(from.top, to.top) - minOf(from.bottom, to.bottom))
    val verticalOverlap = minOf(from.bottom, to.bottom) - maxOf(from.top, to.top)
    val horizontalOverlap = minOf(from.right, to.right) - maxOf(from.left, to.left)
    val halfThickness = thickness * 0.5f

    if (horizontalGap in 0f..maxGap && verticalOverlap > thickness) {
        val centerY = (maxOf(from.top, to.top) + minOf(from.bottom, to.bottom)) * 0.5f
        return listOf(
            Rect(
                left = minOf(from.right, to.right),
                top = centerY - halfThickness,
                right = maxOf(from.left, to.left),
                bottom = centerY + halfThickness
            )
        )
    }

    if (verticalGap in 0f..maxGap && horizontalOverlap > thickness) {
        val centerX = (maxOf(from.left, to.left) + minOf(from.right, to.right)) * 0.5f
        return listOf(
            Rect(
                left = centerX - halfThickness,
                top = minOf(from.bottom, to.bottom),
                right = centerX + halfThickness,
                bottom = maxOf(from.top, to.top)
            )
        )
    }

    return emptyList()
}

private data class OrderMenuItem(
    val name: String,
    val description: String,
    val category: String,
    val variants: List<OrderMenuVariant>
)

private data class OrderMenuVariant(
    val name: String,
    val price: String,
    val amount: Double
)

private fun FloorPlanTable.rotatedVisualWidth(): Dp =
    if (isQuarterTurned()) visualHeight() else visualWidth()

private fun FloorPlanTable.rotatedVisualHeight(): Dp =
    if (isQuarterTurned()) visualWidth() else visualHeight()

private fun FloorPlanTable.isQuarterTurned(): Boolean {
    val normalizedRotation = ((rotationDegrees % 180f) + 180f) % 180f
    return normalizedRotation in 45f..135f
}

private val orderMenuItems = listOf(
    OrderMenuItem(
        name = "Bruschetta",
        description = "Toasted bread with diced tomatoes, garlic, and basil.",
        category = "Antipasti",
        variants = listOf(OrderMenuVariant("Regular", "\$7.50", 7.50))
    ),
    OrderMenuItem(
        name = "Burrata",
        description = "Creamy burrata served with cherry tomatoes and basil.",
        category = "Antipasti",
        variants = listOf(OrderMenuVariant("Regular", "\$10.00", 10.00))
    ),
    OrderMenuItem(
        name = "Margherita Pizza",
        description = "Classic Neapolitan pizza with tomato sauce, mozzarella, and basil.",
        category = "Pizza",
        variants = listOf(
            OrderMenuVariant("Small", "\$10.00", 10.00),
            OrderMenuVariant("Large", "\$13.00", 13.00)
        )
    ),
    OrderMenuItem(
        name = "Spaghetti Carbonara",
        description = "Spaghetti with creamy egg sauce, pancetta, and Parmesan.",
        category = "Pasta",
        variants = listOf(OrderMenuVariant("Regular", "\$16.00", 16.00))
    ),
    OrderMenuItem(
        name = "Lasagna",
        description = "Layers of pasta, meat sauce, ricotta, and bechamel.",
        category = "Pasta",
        variants = listOf(OrderMenuVariant("Regular", "\$15.00", 15.00))
    ),
    OrderMenuItem(
        name = "Tiramisu",
        description = "Classic Italian dessert with coffee-soaked ladyfingers and mascarpone.",
        category = "Dolci",
        variants = listOf(OrderMenuVariant("Regular", "\$8.00", 8.00))
    ),
    OrderMenuItem(
        name = "Panna Cotta",
        description = "Creamy panna cotta with berry coulis.",
        category = "Dolci",
        variants = listOf(OrderMenuVariant("Regular", "\$7.00", 7.00))
    ),
    OrderMenuItem(
        name = "Limonata",
        description = "Fresh lemonade with lemon slices and mint.",
        category = "Beverages",
        variants = listOf(OrderMenuVariant("Regular", "\$5.50", 5.50))
    )
)

private val floorPlanTables = listOf(
    FloorPlanTable(
        x = 0.28f,
        y = 0.28f,
        shape = TableShape.Square,
        seatCount = 8,
        label = "A01",
        scale = 0.40f
    ),
    FloorPlanTable(
        x = 0.28f,
        y = 0.38f,
        shape = TableShape.Square,
        seatCount = 8,
        label = "A02",
        state = TableVisualState.Occupied,
        orderLabel = "DI106",
        statusText = "In Progress",
        servedItems = 3,
        totalItems = 10,
        scale = 0.40f,
    ),
    FloorPlanTable(
        x = 0.72f,
        y = 0.13f,
        shape = TableShape.Square,
        seatCount = 8,
        label = "A03",
        state = TableVisualState.Occupied,
        orderLabel = "DI106",
        statusText = "In Progress",
        servedItems = 7,
        totalItems = 10,
        scale = 0.40f,
        rotationDegrees = 90f
    ),
    FloorPlanTable(0.135f, 0.33f, TableShape.Circle, 4, "T01"),
    FloorPlanTable(
        0.135f, 0.47f, TableShape.Circle, 4, "T02",
        TableVisualState.Occupied, servedItems = 5, totalItems = 10
    ),
    FloorPlanTable(0.18f, 0.62f, TableShape.Circle, 4, "T03", TableVisualState.Reserved),
    FloorPlanTable(0.28f, 0.70f, TableShape.Circle, 4, "T04"),
    FloorPlanTable(0.26f, 0.52f, TableShape.Circle, 4, "T05"),
    FloorPlanTable(0.38f, 0.54f, TableShape.Circle, 4, "T06", TableVisualState.BillPending),

    FloorPlanTable(0.46f, 0.71f, TableShape.Circle, 4, "T07", TableVisualState.Reserved),
    FloorPlanTable(0.55f, 0.82f, TableShape.Circle, 4, "T08"),
    FloorPlanTable(0.63f, 0.71f, TableShape.Circle, 4, "T09", TableVisualState.Unavailable),
    FloorPlanTable(0.55f, 0.62f, TableShape.Circle, 4, "T10"),

    FloorPlanTable(
        0.62f, 0.35f, TableShape.Circle, 4, "T11",
        TableVisualState.Occupied, servedItems = 2, totalItems = 8
    ),
    FloorPlanTable(0.73f, 0.35f, TableShape.Circle, 4, "T12", TableVisualState.Reserved),
    FloorPlanTable(0.62f, 0.495f, TableShape.Circle, 4, "T13"),
    FloorPlanTable(0.73f, 0.495f, TableShape.Circle, 4, "T14", TableVisualState.Unavailable),


    FloorPlanTable(0.855f, 0.30f, TableShape.Circle, 4, "T15"),
    FloorPlanTable(0.855f, 0.44f, TableShape.Circle, 4, "T16"),

    FloorPlanTable(0.82f, 0.62f, TableShape.Circle, 4, "T17")
)
