package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.BorderStroke
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorPlanFloorNames
import com.saporini.mobile_desktop.pos.tables.domain.model.activeFloorNames
import androidx.compose.foundation.layout.requiredSize
import com.saporini.mobile_desktop.core.components.RightEdgeActionButton
import com.saporini.mobile_desktop.core.components.RightEdgeActionButtonHeight
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableStatus
import kotlinx.coroutines.delay
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import mobile_desktop.shared.generated.resources.plan
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.compose.koinInject
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TablesScreen(
    modifier: Modifier = Modifier,
    canEditLayout: Boolean = false,
    onAddItemsRequested: () -> Unit = {},
    onGoToOrders: () -> Unit = {},
    focusTableNumber: String? = null,
    onFocusHandled: () -> Unit = {}
) {
    val screenModel = koinInject<TablesScreenModel>()
    val backendState by screenModel.state.collectAsState()
    var selectedFloor by remember { mutableStateOf(FloorOption.FIRST) }
    var showEditFloors by remember { mutableStateOf(false) }
    var selectedStatuses by remember { mutableStateOf<Set<TableVisualState>>(emptySet()) }
    var selectedTables by remember { mutableStateOf<List<FloorPlanTable>>(emptyList()) }
    var moveSourceTables by remember { mutableStateOf<List<FloorPlanTable>>(emptyList()) }
    var notificationSequence by remember { mutableStateOf(0) }
    var tableNotifications by remember { mutableStateOf<List<TableNotification>>(emptyList()) }
    val pushTableNotification: (String, TableNotificationTone) -> Unit = { message, tone ->
        if (message.isNotBlank()) {
            val nextId = notificationSequence + 1
            notificationSequence = nextId
            tableNotifications = tableNotifications + TableNotification(nextId, message, tone)
        }
    }
    val dismissTableNotification: (Int) -> Unit = { id ->
        tableNotifications = tableNotifications.filterNot { it.id == id }
    }
    var actionToastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(actionToastMessage) {
        actionToastMessage?.let { message ->
            pushTableNotification(message, TableNotificationTone.Success)
            actionToastMessage = null
        }
    }
    var showEdgeAddTable by remember { mutableStateOf(false) }
    var addTableButtonPosition by rememberSaveable { mutableStateOf(.84f) }
    var mergeMode by remember { mutableStateOf(false) }
    var mergeSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mergedGroups by remember { mutableStateOf<List<Set<String>>>(emptyList()) }
    var editingMergeGroup by remember { mutableStateOf<Set<String>?>(null) }
    var showTableSections by remember { mutableStateOf(false) }
    var mergePrimaryLabel by remember { mutableStateOf<String?>(null) }
    var mergeReservedWarningLabels by remember { mutableStateOf<List<String>?>(null) }
    var mergeOccupiedWarning by remember { mutableStateOf(false) }
    var mergeBlockMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mergeBlockMessage) {
        mergeBlockMessage?.let { message ->
            pushTableNotification(message, TableNotificationTone.Error)
            mergeBlockMessage = null
        }
    }
    LaunchedEffect(backendState.errorMessage) {
        backendState.errorMessage?.let { message ->
            pushTableNotification(message, TableNotificationTone.Error)
            screenModel.clearError()
        }
    }
    var tableOrderOverrides by remember { mutableStateOf<Map<String, TableOrderOverride>>(emptyMap()) }
    var nextOrderNumber by remember { mutableStateOf(1246) }
    var newOrderTables by remember { mutableStateOf<List<FloorPlanTable>>(emptyList()) }
    var layoutTables by remember { mutableStateOf<List<FloorPlanTable>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }
    var planOnlyMode by remember { mutableStateOf(false) }
    var planToastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(planToastMessage) {
        planToastMessage?.let { message ->
            pushTableNotification(message, TableNotificationTone.Success)
            planToastMessage = null
        }
    }
    var showUnsavedEditChangesDialog by remember { mutableStateOf(false) }
    var selectedEditTableLabels by remember {
        mutableStateOf<Set<String>>(emptySet())
    }
    var editTableSnapshot by remember {
        mutableStateOf<List<FloorPlanTable>?>(null)
    }
    var customPlanBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showBundledPlan by remember { mutableStateOf(false) }
    var planOffset by remember { mutableStateOf(Offset.Zero) }
    var planMoveMode by remember { mutableStateOf(false) }
    var planScale by remember { mutableStateOf(1f) }
    val customPlan = remember(customPlanBytes) {
        customPlanBytes?.decodeToImageBitmap()
    }
    val selectedFloorLayout = backendState.floorLayouts.firstOrNull {
        it.floorName == selectedFloor.label
    }

    // Saved floor layouts plus any floor that already has tables; there is always at least one floor.
    val visibleFloors = remember(backendState.floorLayouts, backendState.tableLayout) {
        val names = activeFloorNames(backendState.floorLayouts, backendState.tableLayout?.tables.orEmpty())
        FloorOption.entries.filter { it.label in names }
    }
    LaunchedEffect(visibleFloors) {
        if (selectedFloor !in visibleFloors) selectedFloor = visibleFloors.first()
    }

    LaunchedEffect(backendState.tableLayout) {
        if (!editMode) {
            layoutTables = backendState.tableLayout
                ?.tables
                .orEmpty()
                .map { it.toUiTable() }
            mergedGroups = backendState.tableLayout
                ?.tables
                .orEmpty()
                .filter { it.mergedTableIds.isNotEmpty() }
                .map { parent ->
                    buildSet {
                        add(parent.tableNumber)
                        parent.mergedTableIds.forEach { childId ->
                            backendState.tableLayout?.tables
                                ?.firstOrNull { it.id == childId }
                                ?.tableNumber
                                ?.let(::add)
                        }
                    }
                }
                .filter { it.size >= 2 }
        }
    }

    LaunchedEffect(selectedFloor, selectedFloorLayout, backendState.planImageBytes) {
        customPlanBytes = selectedFloorLayout?.let {
            backendState.planImageBytes[it.id]
        }
        showBundledPlan = false
        planOffset = Offset(
            selectedFloorLayout?.planOffsetX ?: 0f,
            selectedFloorLayout?.planOffsetY ?: 0f
        )
        planScale = selectedFloorLayout?.planScale ?: 1f
        planMoveMode = false
        selectedEditTableLabels = emptySet()
    }

    // Opened from a reservation: show that table's floor and open the table.
    LaunchedEffect(focusTableNumber, layoutTables) {
        val target = focusTableNumber ?: return@LaunchedEffect
        val table = layoutTables.firstOrNull { it.label == target } ?: return@LaunchedEffect
        FloorOption.entries.firstOrNull { it.label == table.floorName }?.let { selectedFloor = it }
        selectedTables = listOf(table)
        onFocusHandled()
    }

    val currentTables = layoutTables.map { table ->
        tableOrderOverrides[table.label]?.applyTo(table) ?: table
    }.filter { it.floorName == selectedFloor.label }
        .filter { editMode || it.active }
        .filter { table ->
            searchQuery.isBlank() || table.label.contains(searchQuery, ignoreCase = true)
        }

    val tableLayoutChanged = editTableSnapshot != null && layoutTables != editTableSnapshot
    val planTransformChanged = selectedFloorLayout != null && (
        planOffset.x != selectedFloorLayout.planOffsetX ||
            planOffset.y != selectedFloorLayout.planOffsetY ||
            planScale != selectedFloorLayout.planScale
        )
    val hasEditChanges = tableLayoutChanged || planTransformChanged
    val closeEditMode: () -> Unit = {
        editMode = false
        editTableSnapshot = null
        selectedEditTableLabels = emptySet()
        planMoveMode = false
        planOnlyMode = false
    }
    val restoreSavedPlanUi: () -> Unit = {
        customPlanBytes = selectedFloorLayout?.let { floor ->
            backendState.planImageBytes[floor.id]
        }
        showBundledPlan = false
        planOffset = Offset(
            selectedFloorLayout?.planOffsetX ?: 0f,
            selectedFloorLayout?.planOffsetY ?: 0f
        )
        planScale = selectedFloorLayout?.planScale ?: 1f
    }
    val saveEditChanges: () -> Unit = {
        val shouldSaveTables = tableLayoutChanged
        val shouldSavePlan = planTransformChanged
        var pendingSaves = listOf(shouldSaveTables, shouldSavePlan).count { it }

        if (pendingSaves == 0) {
            closeEditMode()
        } else {
            val finishOneSave = {
                pendingSaves -= 1
                if (pendingSaves == 0) {
                    planToastMessage = if (shouldSavePlan) {
                        "Plan data saved successfully"
                    } else {
                        "Table changes saved successfully"
                    }
                    closeEditMode()
                }
            }

            if (shouldSaveTables) {
                val originalTables = backendState.tableLayout
                    ?.tables
                    .orEmpty()
                    .associateBy { table -> table.id }
                screenModel.saveTables(
                    layoutTables.map { table ->
                        table.toDomainTable(table.id?.let(originalTables::get))
                    },
                    onSuccess = finishOneSave
                )
            }
            if (shouldSavePlan) {
                selectedFloorLayout?.let { floor ->
                    screenModel.saveFloor(
                        floor.copy(
                            planOffsetX = planOffset.x,
                            planOffsetY = planOffset.y,
                            planScale = planScale
                        ),
                        onSuccess = finishOneSave
                    )
                }
            }
        }
    }
    val discardEditChanges: () -> Unit = {
        editTableSnapshot?.let { snapshot ->
            layoutTables = snapshot
        }
        restoreSavedPlanUi()
        closeEditMode()
    }
    val requestCloseEditMode: () -> Unit = {
        if (hasEditChanges) {
            showUnsavedEditChangesDialog = true
        } else {
            discardEditChanges()
        }
    }

    val finishMerge: () -> Unit = {
        val completedSelection = mergeSelection
        val groupsBeingReplaced = mergedGroups.filter { group ->
            group == editingMergeGroup || group.any { it in completedSelection }
        }
        val nextGroups = mergedGroups.filterNot { it in groupsBeingReplaced }
        val backendTables = backendState.tableLayout?.tables.orEmpty()
        val selectedBackendTables = backendTables.filter { it.tableNumber in completedSelection }
        val primaryTable = selectedBackendTables.firstOrNull { it.tableNumber == mergePrimaryLabel }
            ?: selectedBackendTables.firstOrNull()
        val replacedLabels = groupsBeingReplaced.flatten().toSet()
        val previousPrimaryTableIds = backendTables
            .filter { it.mergedTableIds.isNotEmpty() && it.tableNumber in replacedLabels }
            .map { it.id }
        val childTableIds = selectedBackendTables.filter { it.id != primaryTable?.id }.map { it.id }

        val applySuccessfulMerge = {
            mergedGroups = nextGroups + listOf(completedSelection)
            mergeMode = false
            mergeSelection = emptySet()
            editingMergeGroup = null
            mergePrimaryLabel = null
        }

        if (primaryTable != null && childTableIds.isNotEmpty()) {
            screenModel.saveTableMerge(
                primaryTableId = primaryTable.id,
                childTableIds = childTableIds,
                previousPrimaryTableIds = previousPrimaryTableIds,
                onSuccess = applySuccessfulMerge
            )
        } else {
            applySuccessfulMerge()
        }
    }

    // Merge validation: tables the staff already picked in merge mode are checked
    // against the backend's own table state before we ever call saveTableMerge.
    // Hard blocks show as an instant toast (no round trip needed, we already have
    // this data loaded); reserved/both-occupied cases need a confirm dialog first.
    val attemptMerge: () -> Unit = attempt@{
        if (mergeSelection.size < 2) return@attempt
        val backendTables = backendState.tableLayout?.tables.orEmpty()
        val selected = backendTables.filter { it.tableNumber in mergeSelection }
        val editedLabels = editingMergeGroup.orEmpty()

        if (backendState.isSaving) {
            mergeBlockMessage = "Tables are being updated. Try again in a moment."
            return@attempt
        }

        val unavailable = selected.firstOrNull {
            it.status == LayoutTableStatus.MAINTENANCE || it.status == LayoutTableStatus.OUT_OF_SERVICE
        }
        if (unavailable != null) {
            mergeBlockMessage = "${unavailable.tableNumber} is unavailable and cannot be merged."
            return@attempt
        }

        val dirty = selected.firstOrNull { it.status == LayoutTableStatus.DIRTY }
        if (dirty != null) {
            mergeBlockMessage = "${dirty.tableNumber} is waiting to be cleared before it can be merged."
            return@attempt
        }

        val alreadyChild = selected.firstOrNull { it.tableNumber !in editedLabels && it.mergedIntoTableId != null }
        if (alreadyChild != null) {
            val neighborLabel = backendTables.firstOrNull { it.id == alreadyChild.mergedIntoTableId }?.tableNumber
            mergeBlockMessage = if (neighborLabel != null) {
                "${alreadyChild.tableNumber} is already merged with $neighborLabel. Edit the existing table group instead."
            } else {
                "${alreadyChild.tableNumber} is already merged with another table. Edit the existing table group instead."
            }
            return@attempt
        }

        val alreadyParent = selected.firstOrNull { it.tableNumber !in editedLabels && it.mergedTableIds.isNotEmpty() }
        if (alreadyParent != null) {
            mergeBlockMessage = "${alreadyParent.tableNumber} already has merged tables. Edit the existing table group instead."
            return@attempt
        }

        val floors = selected.map { it.floor }.distinct()
        if (floors.size > 1) {
            mergeBlockMessage = "Tables on different floors cannot be merged."
            return@attempt
        }

        val isUnchangedExistingGroup = editingMergeGroup != null && editingMergeGroup == mergeSelection
        val reservedLabels = selected.filter { it.status == LayoutTableStatus.RESERVED }.map { table ->
            val reservationTime = table.nextReservationStart.toMergeWarningTime()
            if (reservationTime == null) {
                table.tableNumber
            } else {
                "${table.tableNumber} at $reservationTime"
            }
        }
        val occupiedCount = selected.count { it.status == LayoutTableStatus.OCCUPIED }

        when {
            reservedLabels.isNotEmpty() && !isUnchangedExistingGroup -> {
                mergeReservedWarningLabels = reservedLabels
            }
            occupiedCount >= 2 && !isUnchangedExistingGroup -> {
                mergeOccupiedWarning = true
            }
            else -> finishMerge()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(start = 22.dp, top = 12.dp, end = 22.dp, bottom = 0.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Phones: the status cards become a sideways row above the plan, so the plan gets the full width.
            val phoneLayout = maxWidth < 600.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Reserve the status sidebar's own width so the toolbar/floor
                        // plan render in the remaining space instead of underneath it
                        // (StatusFilterOverlays stays fixed in the outer viewport Box
                        // while this content scrolls). The sidebar stays visible in edit mode too,
                        // so this is no longer conditional on editMode.
                        .padding(end = if (phoneLayout) 0.dp else 182.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val editMenu: @Composable () -> Unit = {
                        var showEditMenu by remember { mutableStateOf(false) }
                Box {
                    EditLayoutIconButton(onClick = { showEditMenu = true })
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
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Table",
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF222426)
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.TableRestaurant, null, tint = Color(0xFF4F7942))
                            },
                            onClick = {
                                showEditMenu = false
                                editMode = true
                                planOnlyMode = false
                                editTableSnapshot = layoutTables
                                selectedEditTableLabels = emptySet()
                                planMoveMode = false
                                searchQuery = ""
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Plan",
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF222426)
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Map, null, tint = Color(0xFF4F7942))
                            },
                            onClick = {
                                showEditMenu = false
                                editMode = true
                                planOnlyMode = true
                                editTableSnapshot = layoutTables
                                selectedEditTableLabels = emptySet()
                                planMoveMode = false
                                searchQuery = ""
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Edit floors",
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF222426)
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Layers, null, tint = Color(0xFF4F7942))
                            },
                            onClick = {
                                showEditMenu = false
                                showEditFloors = true
                            }
                        )
                    }
                }
                    }
                    if (phoneLayout && !editMode && !mergeMode) {
                        // Phone: title, floor and edit on one line, search under it, then the status cards.
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Tables", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF232422))
                                Spacer(Modifier.weight(1f))
                                FloorSwitcher(floors = visibleFloors, selectedFloor = selectedFloor, onFloorSelected = { selectedFloor = it }, compact = true)
                                if (canEditLayout) {
                                    Spacer(Modifier.width(8.dp))
                                    editMenu()
                                }
                            }
                            com.saporini.mobile_desktop.core.components.SearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                placeholder = "Search table",
                                modifier = Modifier.fillMaxWidth(),
                                height = 40.dp
                            )
                            StatusFilterGrid(
                                tables = currentTables,
                                selectedStatuses = selectedStatuses,
                                onAllSelected = { selectedStatuses = emptySet() },
                                onStatusToggled = { status ->
                                    selectedStatuses = if (status in selectedStatuses) selectedStatuses - status else selectedStatuses + status
                                }
                            )
                        }
                    } else Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mergeMode) {
                            MergeModeToolbar(
                                selectedCount = mergeSelection.size,
                                onCancel = {
                                    mergeMode = false
                                    mergeSelection = emptySet()
                                    editingMergeGroup = null
                                    mergePrimaryLabel = null
                                },
                                onDone = attemptMerge
                            )
                        } else {
                        Row(
                            modifier = if (phoneLayout) Modifier.fillMaxSize().horizontalScroll(rememberScrollState()) else Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (editMode) Arrangement.Center else Arrangement.Start
                        ) {
                            if (!editMode) {
                                Text(
                                    text = "Tables",
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color(0xFF232422)
                                )
                                if (phoneLayout) Spacer(Modifier.width(14.dp)) else Spacer(Modifier.weight(1f))
                                FloorSwitcher(
                                    floors = visibleFloors,
                                    selectedFloor = selectedFloor,
                                    onFloorSelected = { selectedFloor = it }
                                )
                                Spacer(Modifier.width(14.dp))
                                if (canEditLayout) {
                                    editMenu()
                                    Spacer(Modifier.width(14.dp))
                                }
                            }
                            TableLayoutToolbar(
                                searchQuery = searchQuery,
                                onSearchQueryChanged = { searchQuery = it },
                                editMode = editMode,
                                planOnlyMode = planOnlyMode,
                                hasBackground = customPlan != null || showBundledPlan,
                                isSaving = backendState.isSaving,
                                hasChanges = hasEditChanges,
                                planMoveMode = planMoveMode,
                                selectedTableLabels = selectedEditTableLabels,
                                onBackRequested = requestCloseEditMode,
                                onSaveChanges = saveEditChanges,
                                onGroupTables = { showTableSections = true },
                                onAddTable = { shape, chairs ->
                                    val newTable = newPreviewTable(
                                        tables = layoutTables,
                                        shape = shape,
                                        seatCount = chairs
                                    ).copy(floorName = selectedFloor.label)
                                    screenModel.createTables(
                                        listOf(newTable.toDomainTable(null)),
                                        onSuccess = { createdTables ->
                                            val createdUiTables = createdTables.map { it.toUiTable() }
                                            val updatedTables = layoutTables + createdUiTables
                                            layoutTables = updatedTables
                                            editTableSnapshot = updatedTables
                                            selectedEditTableLabels = createdUiTables.map { it.label }.toSet()
                                        }
                                    )
                                },
                                onDuplicateSelectedTables = {
                                    val selectedTablesToDuplicate =
                                        layoutTables.filter {
                                            it.label in selectedEditTableLabels
                                        }
                                    if (selectedTablesToDuplicate.isNotEmpty()) {
                                        var updatedTables = layoutTables
                                        val duplicateTables = selectedTablesToDuplicate.map { source ->
                                            duplicatePreviewTable(
                                                tables = updatedTables,
                                                source = source
                                            ).also { duplicate ->
                                            updatedTables =
                                                updatedTables + duplicate
                                            }
                                        }
                                        screenModel.createTables(
                                            duplicateTables.map { it.toDomainTable(null) },
                                            onSuccess = { createdTables ->
                                                val createdUiTables = createdTables.map { it.toUiTable() }
                                                val nextTables = layoutTables + createdUiTables
                                                layoutTables = nextTables
                                                editTableSnapshot = nextTables
                                                selectedEditTableLabels = createdUiTables.map { it.label }.toSet()
                                            }
                                        )
                                    }
                                },
                                onRotateSelectedTable = { change ->
                                    layoutTables = layoutTables.map { table ->
                                        if (
                                            table.label in
                                            selectedEditTableLabels
                                        ) {
                                            table.copy(
                                                rotationDegrees =
                                                    (table.rotationDegrees +
                                                        change + 360f) % 360f
                                            )
                                        } else table
                                    }
                                },
                                onScaleSelectedTable = { change ->
                                    layoutTables = layoutTables.map { table ->
                                        if (
                                            table.label in
                                            selectedEditTableLabels
                                        ) {
                                            table.copy(
                                                scale = (table.scale + change)
                                                    .coerceIn(0.35f, 1.20f)
                                            )
                                        } else table
                                    }
                                },
                                onDeleteSelectedTable = { onDeleted, onDeleteFailed ->
                                    val labelsToDelete = selectedEditTableLabels
                                    val tableIdsToDelete = layoutTables
                                        .filter { it.label in labelsToDelete }
                                        .mapNotNull { it.id }
                                        .filter { it.isNotBlank() }
                                    if (tableIdsToDelete.isNotEmpty()) {
                                        screenModel.deleteTables(
                                            tableIdsToDelete,
                                            onSuccess = {
                                                val updatedTables = layoutTables.filterNot {
                                                    it.label in labelsToDelete
                                                }
                                                layoutTables = updatedTables
                                                editTableSnapshot = updatedTables
                                                mergedGroups = mergedGroups
                                                    .map { it - labelsToDelete }
                                                    .filter { it.size >= 2 }
                                                selectedEditTableLabels = emptySet()
                                                onDeleted()
                                            },
                                            onFailure = onDeleteFailed
                                        )
                                    } else {
                                        onDeleteFailed("Could not delete this table")
                                    }
                                },
                                onBackgroundSelected = { bytes, fileName, contentType ->
                                    screenModel.uploadPlanImage(
                                        floorName = selectedFloor.label,
                                        imageBytes = bytes,
                                        fileName = fileName,
                                        contentType = contentType,
                                        onSuccess = {
                                            customPlanBytes = bytes
                                            showBundledPlan = false
                                            planToastMessage = "Plan uploaded successfully"
                                        }
                                    )
                                },
                                onRemoveBackground = {
                                    selectedFloorLayout?.let { floor ->
                                        screenModel.removePlanImage(
                                            floor.id,
                                            onSuccess = {
                                                customPlanBytes = null
                                                showBundledPlan = false
                                                planToastMessage = "Plan deleted successfully"
                                            }
                                        )
                                    }
                                },
                                onPlanMoveModeChanged = { planMoveMode = it },
                                onScalePlan = { change ->
                                    planScale = (planScale + change).coerceIn(0.60f, 1.40f)
                                },
                            )
                            if (!editMode) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        }
                    }

                    // First load: a plan-shaped placeholder instead of an empty area.
                    if (backendState.isLoading && currentTables.isEmpty()) {
                        TablesPlanSkeleton(Modifier.fillMaxWidth())
                    } else ZoomablePlan(enabled = phoneLayout && !editMode && !mergeMode) {
                        FloorPlanTables(
                        tables = currentTables,
                        selectedStatuses = selectedStatuses,
                        mergeMode = mergeMode,
                        mergeSelection = mergeSelection,
                        mergedGroups = mergedGroups,
                        editingMergeGroup = editingMergeGroup,
                        editMode = editMode && canEditLayout,
                        planOnlyMode = planOnlyMode,
                        customPlan = customPlan,
                        showBundledPlan = showBundledPlan,
                        planOffset = planOffset,
                        planScale = planScale,
                        planMoveMode = planMoveMode,
                        onPlanMove = { x, y -> planOffset += Offset(x, y) },
                        selectedEditTableLabels = selectedEditTableLabels,
                        onTableMoveDelta = { label, deltaX, deltaY ->
                            val labelsToMove =
                                if (label in selectedEditTableLabels) {
                                    selectedEditTableLabels
                                } else {
                                    setOf(label)
                                }
                            if (label !in selectedEditTableLabels) {
                                selectedEditTableLabels = setOf(label)
                            }
                            layoutTables = moveSelectedTables(
                                tables = layoutTables,
                                selectedLabels = labelsToMove,
                                deltaX = deltaX,
                                deltaY = deltaY
                            )
                        },
                        onEditTableSelected = {
                            planMoveMode = false
                            selectedEditTableLabels =
                                if (it.label in selectedEditTableLabels) {
                                    selectedEditTableLabels - it.label
                                } else {
                                    selectedEditTableLabels + it.label
                                }
                        },
                        onEditSelectionCleared = {
                            selectedEditTableLabels = emptySet()
                        },
                        onTableClick = { table ->
                            val movingSources = moveSourceTables
                            if (movingSources.isNotEmpty()) {
                                if (movingSources.none { it.label == table.label } && table.state == TableVisualState.Free) {
                                    screenModel.moveGuests(
                                        sourceTableIds = movingSources.mapNotNull { it.id },
                                        destinationTableId = table.id ?: "",
                                        guestCount = movingSources.mapNotNull { it.guestCount }.sum().takeIf { it > 0 },
                                        onSuccess = { actionToastMessage = "Guests moved to ${table.label}" }
                                    )
                                    moveSourceTables = emptyList()
                                }
                            } else if (mergeMode) {
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
                }

            }

            if (!phoneLayout) StatusFilterOverlays(
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

            if (canEditLayout && !planOnlyMode && !mergeMode && selectedTables.isEmpty() && !backendState.isLoading) {
                val maxButtonOffset = with(LocalDensity.current) {
                    (maxHeight - RightEdgeActionButtonHeight).coerceAtLeast(0.dp).toPx()
                }
                RightEdgeActionButton(
                    text = "Add table",
                    onClick = { showEdgeAddTable = true },
                    enabled = !backendState.isSaving,
                    modifier = Modifier.align(Alignment.TopEnd)
                        .offset(x = 22.dp)
                        .offset { IntOffset(0, (addTableButtonPosition * maxButtonOffset).roundToInt()) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                if (maxButtonOffset > 0f) {
                                    addTableButtonPosition = (addTableButtonPosition + delta / maxButtonOffset).coerceIn(0f, 1f)
                                }
                            }
                        )
                )
            }
            if (showEditFloors) {
                EditFloorsDialog(
                    floors = visibleFloors,
                    tableCounts = FloorOption.entries.associateWith { floor ->
                        backendState.tableLayout?.tables.orEmpty().count { it.floor == floor.label }
                    },
                    isSaving = backendState.isSaving,
                    onAdd = { floor ->
                        screenModel.createFloor(floor.label)
                        selectedFloor = floor
                    },
                    onRemove = { floor ->
                        backendState.floorLayouts.firstOrNull { it.floorName == floor.label }?.let { layout ->
                            screenModel.deleteFloor(layout.id)
                        }
                    },
                    onDismiss = { showEditFloors = false }
                )
            }
            if (showTableSections) {
                TableSectionsDialog(
                    tables = layoutTables,
                    onDismiss = { showTableSections = false },
                    onLoadSections = screenModel::loadTableSections,
                    onSaveSection = screenModel::saveTableSection,
                    onDeleteSection = screenModel::deleteTableSection,
                    onSetSectionTables = screenModel::setTableSectionTables,
                    onReorderSections = screenModel::reorderTableSections,
                    floorPlan = { highlighted, outlines, onPlanTableClick, planModifier ->
                        Column(planModifier) {
                            BoxWithConstraints(
                                Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                // The plan is laid out at its normal size and scaled down, so tables keep their proportions.
                                val designWidth = 1000.dp
                                val designHeight = designWidth / (1448f / 1086f)
                                val fit = minOf(maxWidth / designWidth, maxHeight / designHeight)
                                Box(Modifier.size(designWidth * fit, designHeight * fit)) {
                                    FloorPlanTables(
                                        tables = layoutTables.filter { it.floorName == selectedFloor.label && it.active },
                                        selectedStatuses = emptySet(),
                                        mergeMode = false,
                                        mergeSelection = emptySet(),
                                        mergedGroups = emptyList(),
                                        editingMergeGroup = null,
                                        editMode = false,
                                        customPlan = customPlan,
                                        showBundledPlan = showBundledPlan,
                                        planOffset = planOffset,
                                        planScale = planScale,
                                        planMoveMode = false,
                                        onPlanMove = { _, _ -> },
                                        selectedEditTableLabels = emptySet(),
                                        onTableMoveDelta = { _, _, _ -> },
                                        onEditTableSelected = {},
                                        onEditSelectionCleared = {},
                                        onTableClick = onPlanTableClick,
                                        highlightedTableLabels = highlighted,
                                        sectionOutlines = outlines,
                                        modifier = Modifier
                                            .requiredSize(designWidth, designHeight)
                                            .graphicsLayer {
                                                scaleX = fit
                                                scaleY = fit
                                            }
                                    )
                                }
                            }
                        }
                    }
                )
            }
            if (showEdgeAddTable) {
                AddTableDialog(
                    onDismiss = { showEdgeAddTable = false },
                    onAdd = { shape, chairs ->
                        if (!editMode) editTableSnapshot = layoutTables
                        editMode = true
                        planOnlyMode = false
                        planMoveMode = false
                        searchQuery = ""
                        val newTable = newPreviewTable(layoutTables, shape, chairs)
                            .copy(floorName = selectedFloor.label)
                        screenModel.createTables(
                            listOf(newTable.toDomainTable(null)),
                            onSuccess = { createdTables ->
                                val createdUiTables = createdTables.map { it.toUiTable() }
                                val updatedTables = layoutTables + createdUiTables
                                layoutTables = updatedTables
                                editTableSnapshot = updatedTables
                                selectedEditTableLabels = createdUiTables.map { it.label }.toSet()
                            }
                        )
                        showEdgeAddTable = false
                    }
                )
            }


            if (
                !backendState.isLoading &&
                layoutTables.none {
                    it.floorName == selectedFloor.label &&
                        (editMode || it.active)
                } &&
                customPlan == null &&
                !showBundledPlan
            ) {
                EmptyFloorPlan(editMode = editMode)
            }


            if (selectedTables.isNotEmpty()) {
                val primaryTableId = backendState.tableLayout
                    ?.tables
                    .orEmpty()
                    .firstOrNull { domainTable ->
                        domainTable.mergedTableIds.isNotEmpty() &&
                            selectedTables.any { it.label == domainTable.tableNumber }
                    }?.id
                TableDetailsModal(
                    tables = selectedTables,
                    primaryTableId = primaryTableId,
                    isSaving = backendState.isSaving,
                    onDismiss = { selectedTables = emptyList() },
                    onSeatGuests = { guestCount ->
                        val label = selectedTables.firstOrNull()?.label
                        (primaryTableId ?: selectedTables.firstOrNull()?.id)?.let { tableId ->
                            screenModel.seatGuests(
                                tableId, guestCount,
                                onSuccess = {
                                    actionToastMessage = "${label ?: "Table"} seated"
                                    selectedTables = emptyList()
                                }
                            )
                        }
                    },
                    onUpdateGuestCount = { guestCount ->
                        (primaryTableId ?: selectedTables.firstOrNull()?.id)?.let { tableId ->
                            screenModel.updateGuestCount(
                                tableId, guestCount,
                                onSuccess = {
                                    actionToastMessage = "Guest count updated"
                                    selectedTables = emptyList()
                                }
                            )
                        }
                    },
                    onStartOrder = {
                        newOrderTables = selectedTables
                        selectedTables = emptyList()
                    },
                    onViewOrder = {
                        selectedTables = emptyList()
                        onGoToOrders()
                    },
                    onAddItems = {
                        selectedTables = emptyList()
                        onAddItemsRequested()
                    },
                    onMergeTables = {
                        val initialSelection = selectedTables.map { it.label }.toSet()
                        val existingGroup = mergedGroups.firstOrNull { it == initialSelection }
                        val persistedParent = backendState.tableLayout
                            ?.tables
                            .orEmpty()
                            .firstOrNull {
                                it.mergedTableIds.isNotEmpty() &&
                                    it.tableNumber in initialSelection
                            }
                        selectedTables = emptyList()
                        mergeMode = true
                        mergeSelection = initialSelection
                        editingMergeGroup = existingGroup
                        mergePrimaryLabel = persistedParent?.tableNumber
                            ?: initialSelection.firstOrNull()
                    },
                    onSeparateTables = { tableId ->
                        val label = selectedTables.joinToString("-") { it.label }
                        screenModel.separateTable(
                            tableId,
                            onSuccess = { actionToastMessage = "$label separated" }
                        )
                        selectedTables = emptyList()
                    },
                    onStartMoveGuests = {
                        moveSourceTables = selectedTables
                        selectedTables = emptyList()
                    },
                    onSetAvailability = { available ->
                        (primaryTableId ?: selectedTables.firstOrNull()?.id)?.let { tableId ->
                            val label = selectedTables.firstOrNull()?.label
                            screenModel.setTableAvailability(
                                tableId, available,
                                onSuccess = {
                                    actionToastMessage = if (available) {
                                        "${label ?: "Table"} available"
                                    } else {
                                        "${label ?: "Table"} marked unavailable"
                                    }
                                }
                            )
                        }
                        selectedTables = emptyList()
                    },
                    onGoToPayment = {
                        selectedTables = emptyList()
                        onGoToOrders()
                    }
                )
            }

            if (tableNotifications.isNotEmpty()) {
                TableNotificationQueue(
                    notifications = tableNotifications,
                    onDismiss = dismissTableNotification,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(28.dp)
                )
            }

            if (showUnsavedEditChangesDialog) {
                MenuNestedDialog(
                    onDismissRequest = { showUnsavedEditChangesDialog = false },
                    title = {
                        Text(
                            "Save changes?",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Text(
                            "You have unsaved table or plan changes. Save them before leaving edit mode?",
                            modifier = Modifier.padding(bottom = 16.dp),
                            fontFamily = Inter(),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color(0xFF71736E)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showUnsavedEditChangesDialog = false
                                saveEditChanges()
                            },
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F7942))
                        ) {
                            Text("Save changes", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showUnsavedEditChangesDialog = false
                                discardEditChanges()
                            }
                        ) {
                            Text("Discard", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color(0xFF71736E))
                        }
                    }
                )
            }

            mergeReservedWarningLabels?.let { labels ->
                val labelText = labels.joinToString(", ")
                val hasLabel = if (labels.size > 1) "have reservations" else "has a reservation"
                MenuNestedDialog(
                    onDismissRequest = { mergeReservedWarningLabels = null },
                    title = {
                        Text(
                            "Table $labelText $hasLabel",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Text(
                            "$labelText $hasLabel. Merge anyway?",
                            fontFamily = Inter()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                mergeReservedWarningLabels = null
                                finishMerge()
                            },
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F7942))
                        ) {
                            Text("Merge anyway", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { mergeReservedWarningLabels = null }) {
                            Text("Cancel", fontFamily = Inter(), color = Color(0xFF71736E))
                        }
                    }
                )
            }

            if (mergeOccupiedWarning) {
                MenuNestedDialog(
                    onDismissRequest = { mergeOccupiedWarning = false },
                    title = {
                        Text(
                            "Merge seated tables?",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Text(
                            "Both tables have seated guests. Merge them into one party? " +
                                "Existing orders and kitchen progress are kept as they are.",
                            fontFamily = Inter()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                mergeOccupiedWarning = false
                                finishMerge()
                            },
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F7942))
                        ) {
                            Text("Merge into one party", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { mergeOccupiedWarning = false }) {
                            Text("Cancel", fontFamily = Inter(), color = Color(0xFF71736E))
                        }
                    }
                )
            }

            if (moveSourceTables.isNotEmpty()) {
                val movingLabel = moveSourceTables.joinToString("-") { it.label }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .shadow(6.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE1DCD8), RoundedCornerShape(10.dp))
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Tap a free table to move $movingLabel's guests there",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = Color(0xFF242424)
                    )
                    TextButton(onClick = { moveSourceTables = emptyList() }) {
                        Text("Cancel", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color(0xFF71736E))
                    }
                }
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
    editingMergeGroup: Set<String>?,
    editMode: Boolean,
    planOnlyMode: Boolean = false,
    customPlan: ImageBitmap?,
    showBundledPlan: Boolean,
    planOffset: Offset,
    planScale: Float,
    planMoveMode: Boolean,
    onPlanMove: (Float, Float) -> Unit,
    selectedEditTableLabels: Set<String>,
    onTableMoveDelta: (String, Float, Float) -> Unit,
    onEditTableSelected: (FloorPlanTable) -> Unit,
    onEditSelectionCleared: () -> Unit,
    onTableClick: (FloorPlanTable) -> Unit,
    modifier: Modifier = Modifier,
    highlightedTableLabels: Set<String>? = null,
    sectionOutlines: List<Pair<Set<String>, Color>> = emptyList(),
    dimmedTableLabels: Set<String> = emptySet(),
    outlinedTableLabels: Set<String> = emptySet()
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1448f / 1086f)
            .clipToBounds()
            // Lets the user see exactly how much space the plan image has to
            // move/scale within, while actively managing it — hidden otherwise.
            .then(
                if (editMode && planOnlyMode) {
                    Modifier.border(2.dp, Color(0xFF4F7942), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
    ) {
        val planHeight = maxWidth / (1448f / 1086f)
        // Phones: the plan's saved offset shrinks with the plan, like the tables, so it matches desktop.
        @Suppress("NAME_SHADOWING")
        val planOffset = if (maxWidth < 600.dp) planOffset * (maxWidth / 1054.dp) else planOffset
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
                            .offset(y = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PlanMoveButton("↑") { onPlanMove(0f, -24f) }
                        PlanMoveButton("↓") { onPlanMove(0f, 24f) }
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = (-8).dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PlanMoveButton("↑") { onPlanMove(0f, -24f) }
                        PlanMoveButton("↓") { onPlanMove(0f, 24f) }
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PlanMoveButton("←") { onPlanMove(-24f, 0f) }
                        PlanMoveButton("→") { onPlanMove(24f, 0f) }
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = (-8).dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PlanMoveButton("←") { onPlanMove(-24f, 0f) }
                        PlanMoveButton("→") { onPlanMove(24f, 0f) }
                    }
                }
            }
        }

        val visibleTables = if (selectedStatuses.isEmpty()) {
            tables
        } else {
            tables.filter { it.state in selectedStatuses }
        }

        mergedGroups
            .filterNot { editMode || (mergeMode && it.any { label -> label in mergeSelection }) }
            .forEach { group ->
            MergeGroupBox(
                tables = tables.filter { it.label in group },
                planWidth = maxWidth,
                planHeight = planHeight,
                planScale = planScale,
                planOffset = planOffset,
                color = Color.Black
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
                planScale = planScale,
                planOffset = planOffset,
                color = Color.Black,
                selected = true
            )
        }

        if (removedFromEditedGroup.isNotEmpty()) {
            RemovedMergeTables(
                tables = tables.filter { it.label in removedFromEditedGroup },
                planWidth = maxWidth,
                planHeight = planHeight,
                planScale = planScale,
                planOffset = planOffset
            )
        }

        sectionOutlines.forEach { (labels, outlineColor) ->
            MergeGroupBox(
                tables = tables.filter { it.label in labels },
                planWidth = maxWidth,
                planHeight = planHeight,
                planScale = planScale,
                planOffset = planOffset,
                color = outlineColor,
                strokeWidth = 1.5.dp,
                inset = 6.dp
            )
        }

        if (outlinedTableLabels.isNotEmpty()) {
            MergeGroupBox(
                tables = tables.filter { it.label in outlinedTableLabels },
                planWidth = maxWidth,
                planHeight = planHeight,
                planScale = planScale,
                planOffset = planOffset,
                color = Color(0xFF242424),
                strokeWidth = 2.5.dp
            )
        }

        if (highlightedTableLabels != null) {
            MergeGroupBox(
                tables = tables.filter { it.label in highlightedTableLabels },
                planWidth = maxWidth,
                planHeight = planHeight,
                planScale = planScale,
                planOffset = planOffset,
                color = Color(0xFF242424),
                strokeWidth = 1.5.dp
            )
        }

        visibleTables.forEach { table ->
            val displayedTable = table.copy(scale = table.scale * planScale * tableFitScale(maxWidth))
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
                        if (highlightedTableLabels != null && table.label !in highlightedTableLabels) alpha = 0.3f
                        if (table.label in dimmedTableLabels) alpha = 0.3f
                    }
                    .then(
                        if (editMode) {
                            Modifier.border(
                                width = if (table.label in selectedEditTableLabels) 3.dp else 1.dp,
                                color = if (table.label in selectedEditTableLabels) {
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
    var covers by remember(initialTables) {
        mutableStateOf(
            initialTables.mapNotNull { it.guestCount }.maxOrNull()
                ?: initialTables.sumOf {
                    it.seatCount.coerceAtLeast(1)
                }.coerceAtLeast(1)
        )
    }
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
                                .background(if (selected) Color(0xFF4F7942) else Color.White)
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
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4F7942))
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
                    .background(if (selectedLabels.isNotEmpty()) Color(0xFF4F7942) else Color(0xFFE7E7E4)),
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
                    .background(if (isSelected) Color(0xFF4F7942) else Color.White)
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
                    .background(if (isSelected) Color(0xFF4F7942) else Color.White)
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
                        tint = if (isSelected) Color.White else Color(0xFF4F7942)
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
                color = if (selected) Color(0xFF4F7942) else Color(0xFFE7E1DC),
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
                    color = Color(0xFF4F7942)
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
                    tint = Color(0xFF4F7942)
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
                color = Color(0xFF4F7942)
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
                    tint = Color(0xFF4F7942)
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
                    color = Color(0xFF4F7942)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF4F7942))
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
            color = Color(0xFF4F7942)
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
                .border(1.dp, if (selected) Color(0xFF4F7942) else Color(0xFF9F9F9F), RoundedCornerShape(if (radio) 50 else 4))
                .background(if (selected && !radio) Color(0xFF4F7942) else Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (selected && radio) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF4F7942))
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
            .background(if (selected) Color(0xFF4F7942) else Color.White)
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
                    tint = Color(0xFF4F7942)
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
            color = Color(0xFF4F7942)
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
    planScale: Float,
    planOffset: Offset,
    color: Color,
    selected: Boolean = false,
    strokeWidth: Dp? = null,
    inset: Dp = 0.dp
) {
    if (tables.isEmpty()) return

    Canvas(
        modifier = Modifier
            .width(planWidth)
            .height(planHeight)
    ) {
        // Keep the selection outline flush with the tables instead of leaving
        // a visible box-shaped gap around the group.
        val padding = -inset.toPx()
        val tableRects = tables.map { table ->
            val displayedTable = table.copy(scale = table.scale * planScale * tableFitScale(planWidth))
            val scaledX = 0.5f + (table.x - 0.5f) * planScale
            val scaledY = 0.5f + (table.y - 0.5f) * planScale
            val centerX = size.width * scaledX + planOffset.x
            val centerY = size.height * scaledY + planOffset.y
            val halfWidth =
                displayedTable.rotatedVisualWidth().toPx() * 0.5f + padding
            val halfHeight =
                displayedTable.rotatedVisualHeight().toPx() * 0.5f + padding

            Rect(
                left = centerX - halfWidth,
                top = centerY - halfHeight,
                right = centerX + halfWidth,
                bottom = centerY + halfHeight
            )
        }
        val rects = tableRects + connectorRects(tableRects, 28.dp.toPx())
        val fillColor = color.copy(alpha = if (selected) 0.05f else 0.025f)
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
                strokeWidth = strokeWidth?.toPx() ?: if (selected) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Square
            )
        }
    }
}

@Composable
private fun RemovedMergeTables(
    tables: List<FloorPlanTable>,
    planWidth: Dp,
    planHeight: Dp,
    planScale: Float,
    planOffset: Offset
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
            val displayedTable = table.copy(scale = table.scale * planScale * tableFitScale(planWidth))
            val scaledX = 0.5f + (table.x - 0.5f) * planScale
            val scaledY = 0.5f + (table.y - 0.5f) * planScale
            val centerX = size.width * scaledX + planOffset.x
            val centerY = size.height * scaledY + planOffset.y
            val width =
                displayedTable.rotatedVisualWidth().toPx() + padding * 2
            val height =
                displayedTable.rotatedVisualHeight().toPx() + padding * 2
            val left = centerX - width * 0.5f
            val top = centerY - height * 0.5f

            drawRoundRect(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx())
            )
            drawRoundRect(
                color = Color.Black,
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
                .background(if (canDone) Color(0xFF4F7942) else Color(0xFFE7E7E4)),
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
            .align(Alignment.TopEnd)
            .padding(top = 14.dp)
            .width(166.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TableStatusCards(tables, selectedStatuses, onAllSelected, onStatusToggled, Modifier)
    }
}

// Phones: the same status cards, smaller, three per row.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StatusFilterGrid(
    tables: List<FloorPlanTable>,
    selectedStatuses: Set<TableVisualState>,
    onAllSelected: () -> Unit,
    onStatusToggled: (TableVisualState) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 3
    ) {
        TableStatusCards(tables, selectedStatuses, onAllSelected, onStatusToggled, Modifier.weight(1f), compact = true)
    }
}

@Composable
private fun TableStatusCards(
    tables: List<FloorPlanTable>,
    selectedStatuses: Set<TableVisualState>,
    onAllSelected: () -> Unit,
    onStatusToggled: (TableVisualState) -> Unit,
    cardModifier: Modifier,
    compact: Boolean = false
) {
        TableStatusCard(
            compact = compact,
            modifier = cardModifier,
            label = "All",
            count = tables.size,
            icon = Icons.Filled.CheckCircle,
            iconBackground = Color(0xFF4F7942).copy(alpha = .14f),
            iconTint = Color(0xFF4F7942),
            selected = selectedStatuses.isEmpty(),
            onClick = onAllSelected
        )
        TableStatusCard(
            compact = compact,
            modifier = cardModifier,
            label = "Free",
            count = statusCount(tables, TableVisualState.Free),
            icon = Icons.Filled.CheckCircle,
            iconBackground = tableStatusColor(TableVisualState.Free).copy(alpha = .14f),
            iconTint = tableStatusColor(TableVisualState.Free),
            selected = TableVisualState.Free in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.Free) }
        )
        TableStatusCard(
            compact = compact,
            modifier = cardModifier,
            label = "Occupied",
            count = statusCount(tables, TableVisualState.Occupied),
            icon = Icons.Filled.Person,
            iconBackground = tableStatusColor(TableVisualState.Occupied).copy(alpha = .14f),
            iconTint = tableStatusColor(TableVisualState.Occupied),
            selected = TableVisualState.Occupied in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.Occupied) }
        )
        TableStatusCard(
            compact = compact,
            modifier = cardModifier,
            label = "Reserved",
            count = statusCount(tables, TableVisualState.Reserved),
            icon = Icons.Filled.Bookmark,
            iconBackground = tableStatusColor(TableVisualState.Reserved).copy(alpha = .14f),
            iconTint = tableStatusColor(TableVisualState.Reserved),
            selected = TableVisualState.Reserved in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.Reserved) }
        )
        TableStatusCard(
            compact = compact,
            modifier = cardModifier,
            label = "Bill Pending",
            count = statusCount(tables, TableVisualState.BillPending),
            icon = Icons.Filled.Payments,
            iconBackground = tableStatusColor(TableVisualState.BillPending).copy(alpha = .14f),
            iconTint = tableStatusColor(TableVisualState.BillPending),
            selected = TableVisualState.BillPending in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.BillPending) }
        )
        TableStatusCard(
            compact = compact,
            modifier = cardModifier,
            label = "Unavailable",
            count = statusCount(tables, TableVisualState.Unavailable),
            icon = Icons.Filled.Cancel,
            iconBackground = tableStatusColor(TableVisualState.Unavailable).copy(alpha = .14f),
            iconTint = tableStatusColor(TableVisualState.Unavailable),
            selected = TableVisualState.Unavailable in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.Unavailable) }
        )
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val enabled = count > 0
    val visuallySelected = selected && enabled
    val disabledContentColor = Color(0xFF7F847D)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 34.dp else 46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    visuallySelected -> iconBackground
                    enabled -> Color.White
                    else -> Color(0xFFF3F4F2)
                }
            )
            .border(
                width = if (visuallySelected) 2.dp else 1.dp,
                color = when {
                    visuallySelected -> iconTint
                    enabled -> Color(0xFFE8E8E4)
                    else -> Color(0xFFD8DBD5)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = if (compact) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (compact) {
            // Small screens: a colored dot keeps room for the full status name.
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (enabled) iconTint else disabledContentColor))
        } else Box(
            modifier = Modifier
                .size(if (compact) 22.dp else 28.dp)
                .clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
                .background(
                    if (enabled) iconBackground else Color(0xFFE1E4DE)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 13.dp else 17.dp),
                tint = if (enabled) iconTint else disabledContentColor
            )
        }
        Spacer(Modifier.width(if (compact) 6.dp else 9.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 11.sp else 13.sp,
            maxLines = 1,
            letterSpacing = 0.sp,
            color = if (enabled) {
                Color(0xFF1F2322)
            } else {
                disabledContentColor
            }
        )
        Text(
            text = count.toString(),
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 12.sp else 15.sp,
            letterSpacing = 0.sp,
            color = if (enabled) {
                Color(0xFF1F2322)
            } else {
                disabledContentColor
            }
        )
    }
}

@Composable
private fun FloorSwitcher(
    floors: List<FloorOption>,
    selectedFloor: FloorOption,
    onFloorSelected: (FloorOption) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (floors.size >= 3) {
        FloorDropdown(floors, selectedFloor, onFloorSelected, modifier)
        return
    }
    Row(
        modifier = modifier
            .width((if (compact) 96.dp else 118.dp) * floors.size)
            .height(if (compact) 38.dp else 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF5F2F0))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        floors.forEach { floor ->
            val selected = floor == selectedFloor
            TextButton(
                onClick = { onFloorSelected(floor) },
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 32.dp else 38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Color(0xFF4F7942) else Color.Transparent),
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
                    fontSize = 12.sp,
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

internal enum class TableNotificationTone { Success, Error, Info }

internal data class TableNotification(
    val id: Int,
    val message: String,
    val tone: TableNotificationTone
)

@Composable
internal fun TableNotificationQueue(
    notifications: List<TableNotification>,
    onDismiss: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        notifications.forEach { notification ->
            TableNotificationCard(
                notification = notification,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun TableNotificationCard(
    notification: TableNotification,
    onDismiss: (Int) -> Unit
) {
    LaunchedEffect(notification.id) {
        delay(5000)
        onDismiss(notification.id)
    }

    val isError = notification.tone == TableNotificationTone.Error
    Row(
        modifier = Modifier
            .width(420.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isError) Color(0xFFB13A2F) else Color(0xFF232422))
            .padding(start = 20.dp, top = 15.dp, bottom = 15.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Icon(
            imageVector = when (notification.tone) {
                TableNotificationTone.Error -> Icons.Filled.Cancel
                TableNotificationTone.Info -> Icons.Filled.Info
                TableNotificationTone.Success -> Icons.Filled.CheckCircle
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = when (notification.tone) {
                TableNotificationTone.Error -> Color.White
                TableNotificationTone.Info -> Color(0xFFF2B84B)
                TableNotificationTone.Success -> Color(0xFF6FCF87)
            }
        )
        Text(
            text = notification.message,
            modifier = Modifier.weight(1f),
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Color.White
        )
        IconButton(
            onClick = { onDismiss(notification.id) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss notification",
                modifier = Modifier.size(19.dp),
                tint = Color.White.copy(alpha = 0.88f)
            )
        }
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
            .background(Color(0xFF4F7942), RoundedCornerShape(50))
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
        label = tableLabel(nextNumber),
        scale = 0.40f
    )
}

private fun duplicatePreviewTable(
    tables: List<FloorPlanTable>,
    source: FloorPlanTable
): FloorPlanTable {
    val nextNumber = (tables.mapNotNull {
        it.label.removePrefix("T").toIntOrNull()
    }.maxOrNull() ?: 0) + 1

    return source.copy(
        id = null,
        x = duplicatePosition(source.x),
        y = duplicatePosition(source.y),
        label = tableLabel(nextNumber),
        state = TableVisualState.Free,
        orderLabel = null,
        orderId = null,
        statusText = null,
        servedItems = 0,
        totalItems = 0,
        guestCount = null,
        seatedAt = null,
        active = true
    )
}

private fun duplicatePosition(value: Float): Float =
    if (value <= 0.91f) value + 0.04f else value - 0.04f

private fun moveSelectedTables(
    tables: List<FloorPlanTable>,
    selectedLabels: Set<String>,
    deltaX: Float,
    deltaY: Float
): List<FloorPlanTable> {
    val selectedTables = tables.filter {
        it.label in selectedLabels
    }
    if (selectedTables.isEmpty()) {
        return tables
    }

    val appliedDeltaX = deltaX.coerceIn(
        minimumValue = selectedTables.maxOf { 0.04f - it.x },
        maximumValue = selectedTables.minOf { 0.96f - it.x }
    )
    val appliedDeltaY = deltaY.coerceIn(
        minimumValue = selectedTables.maxOf { 0.04f - it.y },
        maximumValue = selectedTables.minOf { 0.96f - it.y }
    )

    return tables.map { table ->
        if (table.label in selectedLabels) {
            table.copy(
                x = table.x + appliedDeltaX,
                y = table.y + appliedDeltaY
            )
        } else {
            table
        }
    }
}

private fun tableLabel(number: Int): String =
    "T${number.toString().padStart(2, '0')}"

private enum class FloorOption(val label: String) {
    FIRST(FloorPlanFloorNames[0]),
    SECOND(FloorPlanFloorNames[1]),
    THIRD(FloorPlanFloorNames[2])
}

private fun statusCount(tables: List<FloorPlanTable>, status: TableVisualState): Int =
    tables.count { it.state == status }

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

private fun String?.toMergeWarningTime(): String? {
    val raw = this ?: return null
    val time = raw.substringAfter('T', missingDelimiterValue = "")
        .take(5)
    return time.takeIf {
        it.length == 5 &&
            it[2] == ':' &&
            it.take(2).all { char -> char.isDigit() } &&
            it.takeLast(2).all { char -> char.isDigit() }
    }
}

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

@Composable
private fun FloorDropdown(
    floors: List<FloorOption>,
    selectedFloor: FloorOption,
    onFloorSelected: (FloorOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.width(160.dp).height(44.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE8E5E1))
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Layers, null, Modifier.size(18.dp), tint = Color(0xFF4F7942))
                Text(
                    selectedFloor.label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, color = Color(0xFF222426), maxLines = 1
                )
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Color(0xFF222426))
            }
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.background(Color.White)) {
            floors.forEach { floor ->
                DropdownMenuItem(
                    text = {
                        Text(
                            floor.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = if (floor == selectedFloor) Color(0xFF4F7942) else Color(0xFF222426)
                        )
                    },
                    onClick = {
                        onFloorSelected(floor)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EditFloorsDialog(
    floors: List<FloorOption>,
    tableCounts: Map<FloorOption, Int>,
    isSaving: Boolean,
    onAdd: (FloorOption) -> Unit,
    onRemove: (FloorOption) -> Unit,
    onDismiss: () -> Unit
) {
    val ink = Color(0xFF222426)
    val muted = Color(0xFF747572)
    val green = Color(0xFF4F7942)
    val danger = Color(0xFFB13A2F)
    val border = Color(0xFFE4E5E1)
    val nextFloor = FloorOption.entries.firstOrNull { it !in floors }
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit floors", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Add a floor or remove one you don't use. You need at least one floor.",
                    fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = muted
                )
                floors.forEach { floor ->
                    val tableCount = tableCounts[floor] ?: 0
                    val canRemove = floors.size > 1 && tableCount == 0 && !isSaving
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F7F5))
                            .border(1.dp, border, RoundedCornerShape(10.dp))
                            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Layers, null, Modifier.size(20.dp), tint = green)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(floor.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ink)
                            Text(
                                when {
                                    tableCount > 0 -> "$tableCount ${if (tableCount == 1) "table" else "tables"} · move or delete them to remove this floor"
                                    floors.size == 1 -> "No tables · the only floor can't be removed"
                                    else -> "No tables"
                                },
                                fontFamily = Inter(), fontSize = 11.sp, color = muted
                            )
                        }
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (canRemove) danger else danger.copy(alpha = 0.3f))
                                .clickable(enabled = canRemove) { onRemove(floor) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, "Remove ${floor.label}", Modifier.size(18.dp), tint = Color.White)
                        }
                    }
                }
                if (nextFloor != null) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))
                            .border(1.dp, green, RoundedCornerShape(10.dp))
                            .clickable(enabled = !isSaving) { onAdd(nextFloor) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = green)
                        Spacer(Modifier.width(6.dp))
                        Text("Add ${nextFloor.label}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = green)
                    }
                } else {
                    Text("You can have up to 3 floors.", fontFamily = Inter(), fontSize = 12.sp, color = muted)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = green)
            ) {
                Text("Done", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = {}
    )
}

// Floor plan for choosing reservation tables: free tables are clickable, the rest are faded, the choice is outlined.
@Composable
fun ReservationTablePicker(
    freeTableIds: Set<String>,
    selectedTableIds: Set<String>,
    onTableClick: (tableId: String, label: String, isFree: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    otherSuggestions: List<Set<String>> = emptyList()
) {
    val screenModel = koinInject<TablesScreenModel>()
    val state by screenModel.state.collectAsState()
    val allTables = state.tableLayout?.tables.orEmpty()
    val floors = remember(state.floorLayouts, allTables) {
        val names = activeFloorNames(state.floorLayouts, allTables)
        FloorOption.entries.filter { it.label in names }
    }
    var pickedFloor by remember { mutableStateOf<FloorOption?>(null) }
    LaunchedEffect(selectedTableIds, allTables) {
        val selectedFloor = allTables.firstOrNull { it.id in selectedTableIds }?.floor
        FloorOption.entries.firstOrNull { it.label == selectedFloor }?.let { pickedFloor = it }
    }
    val floor = pickedFloor?.takeIf { it in floors } ?: floors.firstOrNull() ?: FloorOption.FIRST
    val layout = state.floorLayouts.firstOrNull { it.floorName == floor.label }
    val planBitmap = remember(layout?.id, state.planImageBytes) {
        layout?.let { state.planImageBytes[it.id] }?.decodeToImageBitmap()
    }
    // Colored by the reservation window, not by what is happening at the tables right now.
    val floorTables = remember(allTables, floor, freeTableIds) {
        allTables.filter { it.active && it.floor == floor.label }.map { table ->
            table.toUiTable().copy(
                state = if (table.id in freeTableIds) TableVisualState.Free else TableVisualState.Unavailable,
                orderLabel = null,
                orderId = null,
                statusText = null,
                servedItems = 0,
                totalItems = 0,
                guestCount = null
            )
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (floors.size > 1) {
            FloorSwitcher(floors = floors, selectedFloor = floor, onFloorSelected = { pickedFloor = it })
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val designWidth = 1000.dp
            val designHeight = designWidth / (1448f / 1086f)
            val fit = minOf(maxWidth / designWidth, maxHeight / designHeight)
            Box(Modifier.size(designWidth * fit, designHeight * fit)) {
                FloorPlanTables(
                    tables = floorTables,
                    selectedStatuses = emptySet(),
                    mergeMode = false,
                    mergeSelection = emptySet(),
                    mergedGroups = emptyList(),
                    editingMergeGroup = null,
                    editMode = false,
                    customPlan = planBitmap,
                    showBundledPlan = false,
                    planOffset = Offset(layout?.planOffsetX ?: 0f, layout?.planOffsetY ?: 0f),
                    planScale = layout?.planScale ?: 1f,
                    planMoveMode = false,
                    onPlanMove = { _, _ -> },
                    selectedEditTableLabels = emptySet(),
                    onTableMoveDelta = { _, _, _ -> },
                    onEditTableSelected = {},
                    onEditSelectionCleared = {},
                    onTableClick = { table -> table.id?.let { id -> onTableClick(id, table.label, id in freeTableIds) } },
                    outlinedTableLabels = floorTables.filter { it.id in selectedTableIds }.map { it.label }.toSet(),
                    sectionOutlines = otherSuggestions.map { ids ->
                        floorTables.filter { it.id in ids }.map { it.label }.toSet() to Color(0xFF4F7942)
                    },

                    modifier = Modifier
                        .requiredSize(designWidth, designHeight)
                        .graphicsLayer {
                            scaleX = fit
                            scaleY = fit
                        }
                )
            }
        }
    }
}

// Floor plan at one moment: tables booked then are red and show the guest; free tables are green.
@Composable
fun ReservationTimeMap(
    floorName: String?,
    bookings: Map<String, Pair<String, String>>,
    onTableClick: (tableNumber: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenModel = koinInject<TablesScreenModel>()
    val state by screenModel.state.collectAsState()
    val allTables = state.tableLayout?.tables.orEmpty()
    val floor = floorName ?: activeFloorNames(state.floorLayouts, allTables).first()
    val layout = state.floorLayouts.firstOrNull { it.floorName == floor }
    val planBitmap = remember(layout?.id, state.planImageBytes) {
        layout?.let { state.planImageBytes[it.id] }?.decodeToImageBitmap()
    }
    val floorTables = remember(allTables, floor, bookings) {
        allTables.filter { it.active && it.floor == floor }.map { table ->
            val booking = bookings[table.tableNumber]
            table.toUiTable().copy(
                state = if (booking != null) TableVisualState.Unavailable else TableVisualState.Free,
                orderLabel = booking?.first,
                orderId = null,
                statusText = booking?.second,
                servedItems = 0,
                totalItems = 0,
                guestCount = null
            )
        }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val designWidth = 1000.dp
        val designHeight = designWidth / (1448f / 1086f)
        val fit = minOf(maxWidth / designWidth, maxHeight / designHeight)
        Box(Modifier.size(designWidth * fit, designHeight * fit)) {
            FloorPlanTables(
                tables = floorTables,
                selectedStatuses = emptySet(),
                mergeMode = false,
                mergeSelection = emptySet(),
                mergedGroups = emptyList(),
                editingMergeGroup = null,
                editMode = false,
                customPlan = planBitmap,
                showBundledPlan = false,
                planOffset = Offset(layout?.planOffsetX ?: 0f, layout?.planOffsetY ?: 0f),
                planScale = layout?.planScale ?: 1f,
                planMoveMode = false,
                onPlanMove = { _, _ -> },
                selectedEditTableLabels = emptySet(),
                onTableMoveDelta = { _, _, _ -> },
                onEditTableSelected = {},
                onEditSelectionCleared = {},
                onTableClick = { table -> onTableClick(table.label) },
                modifier = Modifier
                    .requiredSize(designWidth, designHeight)
                    .graphicsLayer {
                        scaleX = fit
                        scaleY = fit
                    }
            )
        }
    }
}

// Tables keep their full size on wide plans and shrink with small plans (phones, small tablets)
// so neighbouring tables don't run into each other. Plans 760dp and wider are unchanged.
internal fun tableFitScale(planWidth: Dp): Float =
    // Phones: shrink exactly like the plan (1054dp is the plan's width on a 1280 desktop), so it looks the same.
    if (planWidth < 600.dp) planWidth / 1054.dp else (planWidth / 760.dp).coerceIn(0.6f, 1f)

@Composable
private fun TablesPlanSkeleton(modifier: Modifier) {
    val alpha = com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha("tables-plan")
    BoxWithConstraints(modifier.aspectRatio(1448f / 1086f).alpha(alpha)) {
        com.saporini.mobile_desktop.core.components.SkeletonBox(
            Modifier.fillMaxSize().padding(horizontal = maxWidth * 0.06f, vertical = maxHeight * 0.1f),
            com.saporini.mobile_desktop.core.components.SkeletonLight,
            RoundedCornerShape(18.dp)
        )
        // A few round tables spread over the plan.
        listOf(0.30f to 0.40f, 0.45f to 0.55f, 0.62f to 0.36f, 0.70f to 0.52f, 0.52f to 0.30f, 0.38f to 0.65f).forEach { (x, y) ->
            val size = (maxWidth * 0.06f).coerceIn(24.dp, 54.dp)
            com.saporini.mobile_desktop.core.components.SkeletonBox(
                Modifier.offset(x = maxWidth * x - size / 2, y = maxHeight * y - size / 2).size(size),
                shape = CircleShape
            )
        }
    }
}

// Phones: pinch to zoom the plan (1x-4x) and drag it once zoomed. At 1x a single finger still scrolls the page,
// and taps always reach the tables. Small +, - and reset buttons sit in the corner.
@Composable
private fun ZoomablePlan(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(offset: Offset, scale: Float): Offset {
        val maxX = boxSize.width * (scale - 1f) / 2f
        val maxY = boxSize.height * (scale - 1f) / 2f
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { boxSize = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            if (fingers > 1 || zoom > 1f) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                    pan = clamp(pan + panChange, newZoom)
                                    zoom = newZoom
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }
        ) { content() }
        Column(Modifier.align(Alignment.BottomEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ZoomButton(Icons.Outlined.Add, "Zoom in") { zoom = (zoom + 0.5f).coerceAtMost(4f); pan = clamp(pan, zoom) }
            ZoomButton(Icons.Outlined.Remove, "Zoom out") { zoom = (zoom - 0.5f).coerceAtLeast(1f); pan = clamp(pan, zoom) }
            if (zoom > 1f) ZoomButton(Icons.Outlined.FitScreen, "Reset zoom") { zoom = 1f; pan = Offset.Zero }
        }
    }
}

@Composable
private fun ZoomButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        shape = RoundedCornerShape(9.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE3E5E1)),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(18.dp), tint = Color(0xFF232422))
        }
    }
}
