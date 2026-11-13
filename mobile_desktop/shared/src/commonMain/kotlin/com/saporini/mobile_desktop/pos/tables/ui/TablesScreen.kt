package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import kotlin.math.abs
import kotlin.math.PI
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.FileKitType
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.material.icons.outlined.JoinInner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import com.saporini.mobile_desktop.core.session.DeviceHints
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.key
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
    // View order: that order, opened in Orders.
    onOrderRequested: (orderId: String) -> Unit = {},
    // Start order on a table whose order has no dishes yet: that order's items.
    onOrderItemsRequested: (orderId: String) -> Unit = {},
    onStartOrderRequested: (tableId: String, guests: Int) -> Unit = { _, _ -> },
    onGoToOrders: () -> Unit = {},
    // A table's booking, opened in Reservations.
    onReservationRequested: (reservationId: String) -> Unit = {},
    // Pay on a table whose bill was requested: that order's payment.
    onPaymentRequested: (orderId: String) -> Unit = {},
    // An order the system closed: History is where it's reopened from (user, 2026-11-01).
    onOrderInHistory: (orderId: String) -> Unit = {},
    // A table's History button: its past orders.
    onTableHistory: (tableNumber: String) -> Unit = {},
    focusTableNumber: String? = null,
    // Shown on the table's pill while it stands out, e.g. the booking's guest.
    focusTableNote: String? = null,
    onFocusHandled: () -> Unit = {},
    // Closed as unpaid from Orders: this table is free now, so it stands out.
    freedTableNumber: String? = null,
    onFreedHandled: () -> Unit = {}
) {
    val screenModel = koinInject<TablesScreenModel>()
    val backendState by screenModel.state.collectAsState()
    val scope = rememberCoroutineScope()
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
    var mergeMode by remember { mutableStateOf(false) }
    var mergeSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mergedGroups by remember { mutableStateOf<List<Set<String>>>(emptyList()) }
    var editingMergeGroup by remember { mutableStateOf<Set<String>?>(null) }
    var mergePrimaryLabel by remember { mutableStateOf<String?>(null) }
    var joinBookings by remember { mutableStateOf<List<com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable>?>(null) }
    var mergeOccupiedWarning by remember { mutableStateOf(false) }
    // Unmerging only some of a group: the group, and the tables picked to leave it (the main one too; at least one stays).
    var unmergeGroup by remember { mutableStateOf<Set<String>?>(null) }
    var unmergeSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mergeBlockMessage by remember { mutableStateOf<String?>(null) }
    // Who can't be picked while joining tables, and why. A table takes in tables of its own kind or lower (user rules,
    // 2026-10-31; RestaurantTableService.mergeTable checks the same).
    val joinRefusal: (FloorPlanTable, Int) -> String? = { table, startRank ->
        when {
            table.label in editingMergeGroup.orEmpty() -> null
            mergedGroups.any { table.label in it } -> "${table.label} is already merged with another table."
            table.state == TableVisualState.Unavailable -> "${table.label} is unavailable and can't be joined."
            table.state == TableVisualState.BillPending -> "${table.label}'s bill has been requested, so it can't be joined."
            table.state == TableVisualState.Paid -> "${table.label} has paid and its guests are about to leave, so it can't be joined."
            table.joinRank() > startRank ->
                "${table.label} ${when (table.joinRank()) { 3 -> "has an order"; 2 -> "has guests seated"; else -> "is reserved" }}. Start from ${table.label} to join these tables."
            else -> null
        }
    }
    LaunchedEffect(backendState.errorMessage) {
        backendState.errorMessage?.let { message ->
            pushTableNotification(message, TableNotificationTone.Error)
            screenModel.clearError()
        }
    }
    // Starts from what the model already holds (the tables seen last), so coming back to Tables shows them at once.
    var layoutTables by remember { mutableStateOf(backendState.tableLayout?.tables.orEmpty().toUiTables()) }
    var searchQuery by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }
    var planOnlyMode by remember { mutableStateOf(false) }
    // "Select a table to edit it." each time table editing opens, until someone picks "Don't show again".
    var editHint by remember { mutableStateOf(false) }
    var planToastMessage by remember { mutableStateOf<String?>(null) }
    var specialActionsOpen by remember { mutableStateOf(false) }
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
    val startFloorLayout = remember { backendState.floorLayouts.firstOrNull { it.floorName == selectedFloor.label } }
    var customPlanBytes by remember { mutableStateOf(startFloorLayout?.let { backendState.planImageBytes[it.id] }) }
    var showBundledPlan by remember { mutableStateOf(false) }
    var planOffset by remember { mutableStateOf(Offset(startFloorLayout?.planOffsetX ?: 0f, startFloorLayout?.planOffsetY ?: 0f)) }
    var planMoveMode by remember { mutableStateOf(false) }
    var planScale by remember { mutableStateOf(startFloorLayout?.planScale ?: 1f) }
    val customPlan = remember(customPlanBytes) {
        customPlanBytes?.let(PlanPictures::of)
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

    // The layout the screen has taken in; until it matches the loaded one the plan keeps its loading shapes.
    var syncedLayout by remember { mutableStateOf(backendState.tableLayout) }
    // Bookings come nearer with time, not only when the layout changes (a hold running out, the next booking).
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            if (!editMode) layoutTables = backendState.tableLayout?.tables.orEmpty().toUiTables()
            screenModel.loadSpecialActions()
        }
    }
    LaunchedEffect(backendState.tableLayout) {
        syncedLayout = backendState.tableLayout
        screenModel.loadSpecialActions()
        if (!editMode) {
            layoutTables = backendState.tableLayout
                ?.tables
                .orEmpty()
                .toUiTables()
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
    // Closed as unpaid: says the table is free and makes it stand out for a moment (user, 2026-11-01).
    var freedTable by remember { mutableStateOf<String?>(null) }
    var freedPill by remember { mutableStateOf("Free now") }
    val showFreed: (String?) -> Unit = { label ->
        freedTable = label
        freedPill = "Free now"
        actionToastMessage = if (label != null) "$label is now free. The unpaid order is in History." else "Closed as unpaid. It's in History."
    }
    LaunchedEffect(freedTable) {
        if (freedTable != null) {
            delay(6_000)
            freedTable = null
        }
    }
    LaunchedEffect(freedTableNumber, layoutTables) {
        val target = freedTableNumber ?: return@LaunchedEffect
        val table = layoutTables.firstOrNull { it.label == target } ?: return@LaunchedEffect
        FloorOption.entries.firstOrNull { it.label == table.floorName }?.let { selectedFloor = it }
        showFreed(target)
        onFreedHandled()
    }
    LaunchedEffect(focusTableNumber, layoutTables) {
        val target = focusTableNumber ?: return@LaunchedEffect
        val table = layoutTables.firstOrNull { it.label == target } ?: return@LaunchedEffect
        FloorOption.entries.firstOrNull { it.label == table.floorName }?.let { selectedFloor = it }
        // From a booking (user, 2026-11-02: "send you there and emphasize the table"): it pulses with the guest's name and
        // the rest fades for a few seconds, instead of its panel covering it. A tap opens it as usual.
        freedTable = target
        freedPill = focusTableNote ?: "Here"
        onFocusHandled()
    }

    val freedLabels = freedTable?.let { label -> mergedGroups.firstOrNull { label in it } ?: setOf(label) }.orEmpty()
    val currentTables = layoutTables.filter { it.floorName == selectedFloor.label }
        .filter { editMode || it.active }
        .filter { table ->
            searchQuery.isBlank() || table.label.contains(searchQuery, ignoreCase = true)
        }

    // Loading shapes until the tables and the plan picture are on screen (held a moment so they never flash), then a
    // fade into the plan, like Inventory (user, 2026-10-31).
    val planWaiting = com.saporini.mobile_desktop.core.components.steadyLoading(
        !backendState.loaded ||
            (layoutTables.isEmpty() && (backendState.isLoading || (syncedLayout == null && backendState.tableLayout != null)))
    )

    val mergeStartRank = layoutTables.firstOrNull { it.label == mergePrimaryLabel }?.joinRank() ?: 0
    val uploadPlan: (ByteArray, String, String) -> Unit = { bytes, fileName, contentType ->
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
        // Only what changed goes to the server: a table already in the group has no order of its own any more (it moved
        // to the main table), so it couldn't be merged a second time.
        val addedTableIds = selectedBackendTables
            .filter { it.id != primaryTable?.id && it.mergedIntoTableId != primaryTable?.id }
            .map { it.id }
        val removedTableIds = backendTables
            .filter { it.mergedIntoTableId == primaryTable?.id && it.tableNumber !in completedSelection }
            .map { it.id }

        val applySuccessfulMerge = {
            mergedGroups = nextGroups + listOf(completedSelection)
            mergeMode = false
            mergeSelection = emptySet()
            editingMergeGroup = null
            mergePrimaryLabel = null
        }

        if (primaryTable != null && (addedTableIds.isNotEmpty() || removedTableIds.isNotEmpty())) {
            screenModel.saveTableMerge(
                primaryTableId = primaryTable.id,
                childTableIds = addedTableIds,
                tablesToUnmerge = removedTableIds,
                onSuccess = applySuccessfulMerge
            )
        } else {
            applySuccessfulMerge()
        }
    }

    // Orders moving to the start table ask first too.
    val afterJoinBookings: () -> Unit = {
        val joining = backendState.tableLayout?.tables.orEmpty().filter { it.tableNumber in mergeSelection }
        val unchanged = editingMergeGroup != null && editingMergeGroup == mergeSelection
        // Orders with dishes only: an empty one just goes (user, 2026-11-01).
        if (joining.count { it.currentOrderNumber != null && it.currentOrderTotalItems > 0 } >= 2 && !unchanged) mergeOccupiedWarning = true else finishMerge()
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

        val refused = layoutTables
            .filter { it.label in mergeSelection && it.label != mergePrimaryLabel }
            .firstNotNullOfOrNull { joinRefusal(it, mergeStartRank) }
        if (refused != null) {
            mergeBlockMessage = refused
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
        // The table it starts from keeps its booking: joining is often for that booking. Tables already in the group were asked about.
        val startBooking = selected.firstOrNull { it.tableNumber == mergePrimaryLabel }?.nextReservationId
        val bookings = selected
            .filter { it.tableNumber != mergePrimaryLabel && it.tableNumber !in editedLabels }
            .filter { it.status == LayoutTableStatus.RESERVED || it.nextBookingSoon }
            .filter { it.nextReservationId == null || it.nextReservationId != startBooking }
            .distinctBy { it.nextReservationId ?: it.id }

        if (bookings.isNotEmpty() && !isUnchangedExistingGroup) joinBookings = bookings else afterJoinBookings()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(start = PageSideRoom, top = 12.dp, end = PageSideRoom, bottom = 0.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Phones: the status cards become a sideways row above the plan, so the plan gets the full width.
            val phoneLayout = maxWidth < 600.dp
            val pageHeight = maxHeight
            // Phones: all the page left under the header belongs to the plan, down to the row at its end, and the plan stays
            // in the same place whatever bar is above it.
            var phoneHeaderHeight by remember { mutableStateOf(0) }
            val planRoom = if (phoneLayout) pageHeight - with(LocalDensity.current) { phoneHeaderHeight.toDp() } - 10.dp else 0.dp
            val zoomablePlan = phoneLayout && !editMode
            // Too narrow for the edit bar on one line, beside the status cards too (a sideways phone, a small tablet).
            val compactBar = phoneLayout || maxWidth - 192.dp < 640.dp
            val density = LocalDensity.current
            val phonePlan = remember(density) { PlanZoom(with(density) { PhonePlanBottomRoom.toPx() }) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Phones: the plan runs from edge to edge (user, 2026-11-02: "the plan has no padding left and right").
                    .then(if (phoneLayout) Modifier.bleedSides(PageSideRoom) else Modifier)
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
                        .padding(end = if (phoneLayout) 0.dp else 182.dp + com.saporini.mobile_desktop.core.components.ScrollbarGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val editMenu: @Composable () -> Unit = {
                        var showEditMenu by remember { mutableStateOf(false) }
                Box {
                    EditLayoutIconButton(onClick = { showEditMenu = true }, compact = phoneLayout)
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
                                    "Table", letterSpacing = 0.sp,
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
                                editHint = !DeviceHints.isHidden(TablesEditHintKey)
                                editTableSnapshot = layoutTables
                                selectedEditTableLabels = emptySet()
                                planMoveMode = false
                                searchQuery = ""
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Plan", letterSpacing = 0.sp,
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
                                    "Edit floors", letterSpacing = 0.sp,
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
                    if (phoneLayout && !editMode && !mergeMode && unmergeGroup == null) {
                        // Phone: title, floor and edit on one line, search under it, then the status cards.
                        Column(
                            Modifier.fillMaxWidth().onSizeChanged { phoneHeaderHeight = it.height }.padding(horizontal = PageSideRoom),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                loading = planWaiting,
                                specialCount = backendState.specialActions.size,
                                onSpecial = { specialActionsOpen = true },
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
                            .onSizeChanged { phoneHeaderHeight = it.height }
                            .padding(horizontal = if (phoneLayout) PageSideRoom else 0.dp)
                            .then(if (phoneLayout || (compactBar && editMode)) Modifier.heightIn(min = 60.dp) else Modifier.height(60.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (unmergeGroup != null) {
                            UnmergeToolbar(
                                compact = phoneLayout,
                                selectedLabels = unmergeSelection.sortedWith(compareBy({ it.length }, { it })),
                                saving = backendState.isSaving,
                                onCancel = {
                                    unmergeGroup = null
                                    unmergeSelection = emptySet()
                                },
                                onUnmerge = {
                                    val backendTables = backendState.tableLayout?.tables.orEmpty()
                                    val main = backendTables.firstOrNull { it.mergedTableIds.isNotEmpty() && it.tableNumber in unmergeGroup.orEmpty() }
                                    val leaving = backendTables.filter { it.tableNumber in unmergeSelection }.map { it.id }
                                    val labels = unmergeSelection.sorted().joinToString(", ")
                                    // The ones picked become free; the guests and the order stay with the rest.
                                    if (main != null) {
                                        screenModel.unmergeSome(main.id, leaving, null, onSuccess = {
                                            actionToastMessage = "$labels unmerged and free"
                                            unmergeGroup = null
                                            unmergeSelection = emptySet()
                                        })
                                    }
                                }
                            )
                        } else if (mergeMode) {
                            MergeModeToolbar(
                                compact = phoneLayout,
                                primaryLabel = mergePrimaryLabel,
                                selectedLabels = mergeSelection.sortedWith(compareBy({ it.length }, { it })),
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
                            modifier = if (phoneLayout || (compactBar && editMode)) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (editMode) Arrangement.Center else Arrangement.Start
                        ) {
                            // Says so on the left while the layout is being changed (user, 2026-11-01); the bar stays in the middle.
                            if (editMode && !compactBar) Box(Modifier.weight(1f)) {
                                Text("Edit mode", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF232422))
                            }
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
                                compact = compactBar,
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
                                onSelectionDone = { selectedEditTableLabels = emptySet() },
                                onAddTable = { shape, chairs ->
                                    val newTable = newPreviewTable(
                                        tables = layoutTables,
                                        shape = shape,
                                        seatCount = chairs
                                    ).copy(floorName = selectedFloor.label).withoutOverlap(layoutTables)
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
                                            ).withoutOverlap(updatedTables).also { duplicate ->
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
                                onScaleAllTables = { change ->
                                    layoutTables = layoutTables.map { table ->
                                        if (table.floorName == selectedFloor.label) {
                                            table.copy(scale = (table.scale + change).coerceIn(0.35f, 1.20f))
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
                                onBackgroundSelected = uploadPlan,
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
                            if (editMode && !compactBar) Spacer(Modifier.weight(1f))
                            if (!editMode) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        }
                    }

                    // Loading: plan-shaped shapes, then a fade into the plan.
                    androidx.compose.animation.AnimatedContent(
                        targetState = planWaiting,
                        // Big screens: the plan stops growing at about a desktop's size, centred, so it and its
                        // tables stay the size they are on a PC (user, 2026-10-31).
                        modifier = (if (phoneLayout) Modifier else Modifier.widthIn(max = PlanMaxWidth)).fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                        transitionSpec = { com.saporini.mobile_desktop.core.components.loadingSwap(targetState) },
                        label = "tables-loading"
                    ) { waiting ->
                    if (waiting) {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = planRoom).padding(bottom = if (phoneLayout) PhonePlanBottomRoom else 0.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) { TablesPlanSkeleton(Modifier.fillMaxWidth()) }
                    } else ZoomablePlan(placed = phoneLayout, enabled = zoomablePlan, plan = phonePlan, minHeight = planRoom) {
                        FloorPlanTables(
                        tables = currentTables,
                        freedTableLabels = freedLabels,
                        freedPill = freedPill,
                        dimmedTableLabels = when {
                            freedLabels.isNotEmpty() -> currentTables.filter { it.label !in freedLabels }.map { it.label }.toSet()
                            unmergeGroup != null -> currentTables.filter { it.label !in unmergeGroup.orEmpty() }.map { it.label }.toSet()
                            mergeMode -> currentTables.filter { it.label != mergePrimaryLabel && joinRefusal(it, mergeStartRank) != null }.map { it.label }.toSet()
                            // Only a free table can take the guests being moved.
                            moveSourceTables.isNotEmpty() -> currentTables
                                .filter { table -> table.state != TableVisualState.Free && moveSourceTables.none { it.label == table.label } }
                                .map { it.label }.toSet()
                            else -> emptySet()
                        },
                        movingTableLabels = moveSourceTables.map { it.label }.toSet(),
                        unmergingTableLabels = unmergeSelection,
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
                        onTableMoveEnd = { label ->
                            // Let go on top of another table, it moves off by itself. Several picked keep their shape.
                            if (selectedEditTableLabels.size <= 1) {
                                layoutTables = layoutTables.map { if (it.label == label) it.withoutOverlap(layoutTables) else it }
                            }
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
                                val movingGuests = movingSources.mapNotNull { it.guestCount }.sum().takeIf { it > 0 }
                                val tooMany = movingGuests != null && table.guestsMax != null && movingGuests > table.guestsMax
                                val tooFew = movingGuests != null && table.guestsMin != null && movingGuests < table.guestsMin
                                if (table.state == TableVisualState.Free && (tooMany || tooFew)) {
                                    // Checked before the order moves: the guests would otherwise be half moved.
                                    mergeBlockMessage = if (tooMany) {
                                        "${table.label} can take at most ${table.guestsMax} guests."
                                    } else {
                                        "${table.label} is merged and needs at least ${table.guestsMin} guests."
                                    }
                                } else if (movingSources.none { it.label == table.label } && table.state == TableVisualState.Free) {
                                    screenModel.moveGuests(
                                        sourceTableIds = movingSources.mapNotNull { it.id },
                                        destinationTableId = table.id ?: "",
                                        guestCount = movingSources.mapNotNull { it.guestCount }.sum().takeIf { it > 0 },
                                        onSuccess = { actionToastMessage = "Guests moved to ${table.label}" }
                                    )
                                    moveSourceTables = emptyList()
                                }
                            } else if (unmergeGroup != null) {
                                val main = backendState.tableLayout?.tables.orEmpty()
                                    .firstOrNull { it.mergedTableIds.isNotEmpty() && it.tableNumber in unmergeGroup.orEmpty() }?.tableNumber
                                when {
                                    table.label !in unmergeGroup.orEmpty() -> mergeBlockMessage = "${table.label} isn't in this group."
                                    // Any table can leave, the main one too (user, 2026-11-01); one has to stay for the guests.
                                    table.label !in unmergeSelection && unmergeSelection.size + 1 >= unmergeGroup.orEmpty().size ->
                                        mergeBlockMessage = "Keep at least one table for the guests."
                                    table.label in unmergeSelection -> unmergeSelection = unmergeSelection - table.label
                                    else -> unmergeSelection = unmergeSelection + table.label
                                }
                            } else if (mergeMode) {
                                val refusal = if (table.label == mergePrimaryLabel) {
                                    "${table.label} is the table you're joining the others to."
                                } else {
                                    joinRefusal(table, mergeStartRank)
                                }
                                if (refusal != null) {
                                    mergeBlockMessage = refusal
                                } else {
                                    mergeSelection = if (table.label in mergeSelection) {
                                        mergeSelection - table.label
                                    } else {
                                        mergeSelection + table.label
                                    }
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

            }

            if (!phoneLayout) StatusFilterOverlays(
                loading = planWaiting,
                // As tall as the window allows, with the Waitlist always under it (user, 2026-11-01); more cards scroll.
                maxHeight = (maxHeight - 14.dp - WaitlistRoom - 8.dp).coerceAtLeast(150.dp),
                specialCount = backendState.specialActions.size,
                onSpecial = { specialActionsOpen = true },
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
            val emptyFloor = !planWaiting &&
                layoutTables.none {
                    it.floorName == selectedFloor.label &&
                        (editMode || it.active)
                } &&
                customPlan == null &&
                !showBundledPlan
            if (emptyFloor) {
                EmptyFloorPlan(
                    topRoom = if (phoneLayout) pageHeight - planRoom else if (pageHeight < 480.dp) 70.dp else 0.dp,
                    small = phoneLayout || pageHeight < 480.dp,
                    phoneLayout = phoneLayout,
                    isSaving = backendState.isSaving,
                    onPlanPicked = if (canEditLayout) uploadPlan else null
                )
            }


            if (selectedTables.isNotEmpty()) {
                val primaryTableId = backendState.tableLayout
                    ?.tables
                    .orEmpty()
                    .firstOrNull { domainTable ->
                        domainTable.mergedTableIds.isNotEmpty() &&
                            selectedTables.any { it.label == domainTable.tableNumber }
                    }?.id
                val clashTableId = selectedTables.firstNotNullOfOrNull { it.clash }?.tableId
                // The table whose next booking can be moved: the clash's, or any table here with a booking coming.
                val bookingTableId = clashTableId ?: selectedTables.firstOrNull { it.bookingId != null }?.id
                var bookingOptions by remember(bookingTableId) { mutableStateOf(emptyList<com.saporini.mobile_desktop.pos.tables.domain.model.BookingTableOption>()) }
                var bookingOptionsLoading by remember(bookingTableId) { mutableStateOf(false) }
                var movingBooking by remember(bookingTableId) { mutableStateOf(false) }
                LaunchedEffect(bookingTableId) {
                    if (bookingTableId != null && screenModel.can("RESERVATION_APPROVE")) bookingOptions = screenModel.bookingOptions(bookingTableId)
                }
                if (movingBooking && bookingTableId != null) {
                    val booking = selectedTables.firstNotNullOfOrNull { it.nextBooking() }
                    BookingMoveDialog(
                        title = "Move the booking",
                        detail = listOfNotNull(
                            booking?.name?.takeIf { it.isNotBlank() },
                            booking?.start?.clockTime(),
                            booking?.guests?.let { if (it == 1) "1 guest" else "$it guests" },
                            "now on " + selectedTables.joinToString("-") { it.label }
                        ).joinToString(" · "),
                        options = bookingOptions,
                        loading = bookingOptionsLoading,
                        busy = backendState.isSaving,
                        onDismiss = { movingBooking = false },
                        onPick = { option ->
                            movingBooking = false
                            screenModel.moveBooking(bookingTableId, option, onSuccess = { actionToastMessage = "Booking moved to ${option.tableNumbers.joinToString("-")}" })
                            selectedTables = emptyList()
                        }
                    )
                }
                TableDetailsModal(
                    tables = selectedTables,
                    primaryTableId = primaryTableId,
                    isSaving = backendState.isSaving,
                    canMoveBooking = bookingOptions.isNotEmpty() && screenModel.can("RESERVATION_APPROVE"),
                    canMoveGuests = layoutTables.any { it.state == TableVisualState.Free },
                    onShowHistory = {
                        val head = selectedTables.firstOrNull { it.id == primaryTableId } ?: selectedTables.firstOrNull()
                        selectedTables = emptyList()
                        head?.let { onTableHistory(it.label) }
                    },
                    onViewBooking = { reservationId ->
                        selectedTables = emptyList()
                        onReservationRequested(reservationId)
                    },
                    onChooseBookingTable = {
                        // Fresh choices each time the map opens: tables fill up while the panel is open.
                        movingBooking = true
                        bookingTableId?.let { tableId ->
                            bookingOptionsLoading = true
                            scope.launch {
                                bookingOptions = screenModel.bookingOptions(tableId)
                                bookingOptionsLoading = false
                            }
                        }
                    },
                    onGiveMoreTime = { minutes ->
                        val label = selectedTables.joinToString("-") { it.label }
                        clashTableId?.let {
                            screenModel.giveMoreTime(it, minutes, onSuccess = { actionToastMessage = "$label: $minutes minutes more" })
                        }
                        selectedTables = emptyList()
                    },
                    onBookingWaits = {
                        selectedTables.firstNotNullOfOrNull { it.clash }?.reservationId?.let {
                            screenModel.bookingWaits(it, onSuccess = { actionToastMessage = "The booking is waiting" })
                        }
                        selectedTables = emptyList()
                    },
                    onStillHere = {
                        val label = selectedTables.joinToString("-") { it.label }
                        (primaryTableId ?: selectedTables.firstOrNull()?.id)?.let { tableId ->
                            screenModel.stillHere(tableId, onSuccess = { actionToastMessage = "$label: clock restarted" })
                        }
                        selectedTables = emptyList()
                    },
                    onDismiss = { selectedTables = emptyList() },
                    onSeatGuests = { guestCount, expected ->
                        val label = selectedTables.firstOrNull()?.label
                        (primaryTableId ?: selectedTables.firstOrNull()?.id)?.let { tableId ->
                            screenModel.seatGuests(
                                tableId, guestCount, expected,
                                onSuccess = {
                                    actionToastMessage = "${label ?: "Table"} seated"
                                    selectedTables = emptyList()
                                }
                            )
                        }
                    },
                    onUpdateGuestCount = { guestCount, expected ->
                        (primaryTableId ?: selectedTables.firstOrNull()?.id)?.let { tableId ->
                            screenModel.updateGuestCount(
                                tableId, guestCount, expected,
                                onSuccess = {
                                    actionToastMessage = "Guest count updated"
                                    selectedTables = emptyList()
                                }
                            )
                        }
                    },
                    onStartOrder = {
                        val tableId = primaryTableId ?: selectedTables.firstOrNull()?.id
                        val guests = selectedTables.mapNotNull { it.guestCount }.sum().coerceAtLeast(1)
                        selectedTables = emptyList()
                        tableId?.let { onStartOrderRequested(it, guests) }
                    },
                    onViewOrder = {
                        val orderId = selectedTables.firstNotNullOfOrNull { it.orderId }
                        selectedTables = emptyList()
                        if (orderId != null) onOrderRequested(orderId) else onGoToOrders()
                    },
                    onOpenEmptyOrder = {
                        val orderId = selectedTables.firstNotNullOfOrNull { it.orderId }
                        selectedTables = emptyList()
                        orderId?.let(onOrderItemsRequested)
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
                    onUnmergeSome = {
                        unmergeGroup = selectedTables.map { it.label }.toSet()
                        unmergeSelection = emptySet()
                        selectedTables = emptyList()
                    },
                    onSetBillRequested = { orderId, requested ->
                        val label = selectedTables.joinToString("-") { it.label }
                        screenModel.setBillRequested(
                            orderId,
                            requested,
                            onSuccess = { actionToastMessage = if (requested) "Bill requested for $label" else "Bill request taken back for $label" }
                        )
                        selectedTables = emptyList()
                    },
                    onUnmergeKeeping = { keepId ->
                        val keeping = selectedTables.firstOrNull { it.id == keepId }?.label
                        val leaving = selectedTables.filter { it.id != keepId }
                        (primaryTableId ?: keepId).let { headId ->
                            screenModel.unmergeSome(headId, leaving.mapNotNull { it.id }, keepId, onSuccess = {
                                actionToastMessage = "${leaving.joinToString(", ") { it.label }} free · the order is on $keeping"
                            })
                        }
                        selectedTables = emptyList()
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
                        val orderId = selectedTables.firstNotNullOfOrNull { it.orderId }
                        selectedTables = emptyList()
                        if (orderId != null) onPaymentRequested(orderId) else onGoToOrders()
                    }
                )
            }

            // What the system did by itself because nobody acted: see it and return it.
            if (specialActionsOpen) SpecialCaseActionsDialog(
                actions = backendState.specialActions,
                freeTables = layoutTables.filter { it.state == TableVisualState.Free },
                isSaving = backendState.isSaving,
                onReturn = { action, tableId ->
                    screenModel.returnSpecialAction(action.id, tableId, onSuccess = { actionToastMessage = "Returned: ${action.summary}" })
                },
                onOrderInHistory = { orderId ->
                    specialActionsOpen = false
                    onOrderInHistory(orderId)
                },
                onDismiss = { specialActionsOpen = false }
            )

            // Phones: − and + in the left corner of the page's end, the Waitlist in the right one (user, 2026-11-02). They stay
            // in view when a short phone's page scrolls.
            if (zoomablePlan && !planWaiting && !emptyFloor && selectedTables.isEmpty()) PlanZoomButtons(
                phonePlan,
                Modifier.align(Alignment.BottomStart).offset(x = PhoneCornerGap - PageSideRoom).padding(bottom = PhoneCornerGap)
            )

            // Walk-ins waiting at the door: open the waitlist and seat them at a free table.
            if (!planOnlyMode && !mergeMode && selectedTables.isEmpty()) {
                var waitlistOpen by remember { mutableStateOf(false) }
                Surface(
                    onClick = { waitlistOpen = true },
                    // On the right, in the corner under the status cards (user, 2026-11-01): they scroll when the window is short.
                    modifier = Modifier.align(Alignment.BottomEnd).then(
                        // Phones: right in the corner (user, 2026-11-02), past the page's side room. The button's 48 dp touch
                        // size leaves 4 dp under it, so its edge lines up with the zoom buttons'.
                        if (phoneLayout) Modifier.offset(x = PageSideRoom - PhoneCornerGap).padding(bottom = PhoneCornerGap - 4.dp)
                        else Modifier.padding(end = 4.dp, bottom = 16.dp)
                    ),
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE3E6E1)),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        if (phoneLayout) Modifier.height(40.dp).padding(horizontal = 16.dp) else Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Groups, null, Modifier.size(18.dp), tint = Color(0xFF4F7942))
                        Spacer(Modifier.width(8.dp))
                        Text("Waitlist", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF222426))
                    }
                }
                if (waitlistOpen) {
                    MenuNestedDialog(
                        onDismissRequest = { waitlistOpen = false },
                        title = { Text("Walk-ins waiting", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                        text = {
                            Box(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                                com.saporini.mobile_desktop.pos.reservations.WaitlistSection(Modifier.fillMaxWidth())
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { waitlistOpen = false }) { Text("Close", letterSpacing = 0.sp, fontFamily = Inter(), color = Color(0xFF4F7942)) }
                        },
                        dismissButton = {}
                    )
                }
            }

            if (editHint && editMode && !planOnlyMode && selectedEditTableLabels.isEmpty()) {
                com.saporini.mobile_desktop.core.components.BottomToast(
                    "Select a table to edit it.",
                    isTip = true,
                    actionLabel = "Don't show again",
                    onAction = { DeviceHints.hide(TablesEditHintKey) },
                    onDismiss = { editHint = false }
                )
            }
            // Once a table is picked or editing ends, the tip has done its job.
            LaunchedEffect(editMode, selectedEditTableLabels.isEmpty()) {
                if (!editMode || selectedEditTableLabels.isNotEmpty()) editHint = false
            }

            com.saporini.mobile_desktop.core.components.BottomToast(
                mergeBlockMessage,
                isWarning = true,
                onDismiss = { mergeBlockMessage = null }
            )

            // The app's one message style, bottom-left (user, 2026-11-01), one at a time.
            tableNotifications.firstOrNull().let { notice ->
                com.saporini.mobile_desktop.core.components.BottomToast(
                    notice?.message,
                    isError = notice?.tone == TableNotificationTone.Error,
                    onDismiss = { notice?.let { dismissTableNotification(it.id) } }
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
                            Text("Save changes", letterSpacing = 0.sp, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showUnsavedEditChangesDialog = false
                                discardEditChanges()
                            }
                        ) {
                            Text("Discard", letterSpacing = 0.sp, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color(0xFF71736E))
                        }
                    }
                )
            }

            joinBookings?.let { bookings ->
                JoinBookingDialog(
                    tables = bookings,
                    joiningIds = backendState.tableLayout?.tables.orEmpty().filter { it.tableNumber in mergeSelection }.map { it.id }.toSet(),
                    canMove = screenModel.can("RESERVATION_APPROVE"),
                    options = screenModel::bookingOptions,
                    onDismiss = { joinBookings = null },
                    onJoin = { moves ->
                        joinBookings = null
                        fun moveThenJoin(rest: List<Pair<String, com.saporini.mobile_desktop.pos.tables.domain.model.BookingTableOption>>) {
                            val move = rest.firstOrNull()
                            if (move == null) afterJoinBookings()
                            else screenModel.moveBooking(move.first, move.second, onSuccess = { moveThenJoin(rest.drop(1)) })
                        }
                        moveThenJoin(moves)
                    }
                )
            }

            if (mergeOccupiedWarning) {
                MenuNestedDialog(
                    onDismissRequest = { mergeOccupiedWarning = false },
                    title = {
                        Text(
                            "Merge tables with orders?",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Text(
                            "These tables have their own orders. They become one order on ${mergePrimaryLabel.orEmpty()}, and stay one if you unmerge.",
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
                            Text("Merge", letterSpacing = 0.sp, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { mergeOccupiedWarning = false }) {
                            Text("Cancel", letterSpacing = 0.sp, fontFamily = Inter(), color = Color(0xFF71736E))
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
                        // Phones: as wide as the page, the words wrapping and Cancel kept whole (user, 2026-11-02).
                        .then(if (phoneLayout) Modifier.fillMaxWidth() else Modifier)
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
                        modifier = Modifier.weight(1f, fill = false),
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = Color(0xFF242424)
                    )
                    TextButton(onClick = { moveSourceTables = emptyList() }) {
                        Text("Cancel", letterSpacing = 0.sp, maxLines = 1, softWrap = false, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color(0xFF71736E))
                    }
                }
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
    onTableMoveEnd: (String) -> Unit = {},
    onEditTableSelected: (FloorPlanTable) -> Unit,
    onEditSelectionCleared: () -> Unit,
    onTableClick: (FloorPlanTable) -> Unit,
    modifier: Modifier = Modifier,
    highlightedTableLabels: Set<String>? = null,
    sectionOutlines: List<Pair<Set<String>, Color>> = emptyList(),
    dimmedTableLabels: Set<String> = emptySet(),
    outlinedTableLabels: Set<String> = emptySet(),
    // The tables whose guests are being moved: they pulse and say so while a free table is picked.
    movingTableLabels: Set<String> = emptySet(),
    // The tables just freed by closing an order as unpaid: they pulse and say so.
    freedTableLabels: Set<String> = emptySet(),
    freedPill: String = "Free now",
    // The tables picked to leave a merged group: each stands out on its own.
    unmergingTableLabels: Set<String> = emptySet()
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
        val smallPlan = maxWidth < 600.dp
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

        // Pointing at a table (hover, or holding it down) makes it stand out and fades the rest; a merged table brings its
        // whole group (user, 2026-10-31, 2026-11-01: "all tables work like that").
        var pointedTable by remember { mutableStateOf<String?>(null) }
        val pointedGroup = pointedTable
            ?.takeIf { !editMode && !mergeMode && movingTableLabels.isEmpty() && dimmedTableLabels.isEmpty() }
            ?.let { label -> mergedGroups.firstOrNull { label in it } ?: setOf(label) }

        mergedGroups
            .filterNot { editMode || (mergeMode && it.any { label -> label in mergeSelection }) }
            .forEach { group ->
            MergeGroupBox(
                // While unmerging, the tables that stay keep the solid line; the ones leaving get a dashed one of their own.
                tables = tables.filter { it.label in group && it.label !in unmergingTableLabels },
                planWidth = maxWidth,
                planHeight = planHeight,
                planScale = planScale,
                planOffset = planOffset,
                color = if (group == pointedGroup) Color(0xFF4F7942) else Color.Black,
                strokeWidth = if (group == pointedGroup) 3.dp else null,
                opacity = if (pointedGroup != null && group != pointedGroup) 0.25f else 1f
            )
        }

        // The lines between merged tables always show (user, 2026-11-01); the pointed group's stand out.
        if (!editMode && !mergeMode) mergedGroups.forEach { group ->
            MergeGroupLinks(
                tables = tables.filter { it.label in group && it.label !in unmergingTableLabels },
                planScale = planScale,
                planOffset = planOffset,
                strong = group == pointedGroup,
                faded = pointedGroup != null && group != pointedGroup
            )
        }
        // A single table pointed at just stays clear while the rest fade: no frame (user, 2026-11-01).

        if (unmergingTableLabels.isNotEmpty()) {
            RemovedMergeTables(
                tables = tables.filter { it.label in unmergingTableLabels },
                planWidth = maxWidth,
                planHeight = planHeight,
                planScale = planScale,
                planOffset = planOffset
            )
        }

        if (movingTableLabels.isNotEmpty() || freedTableLabels.isNotEmpty()) {
            val pulse by rememberInfiniteTransition(label = "moving-tables").animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                label = "moving-tables-pulse"
            )
            MergeGroupBox(
                tables = tables.filter { it.label in movingTableLabels || it.label in freedTableLabels },
                planWidth = maxWidth,
                planHeight = planHeight,
                planScale = planScale,
                planOffset = planOffset,
                color = Color(0xFF4F7942),
                selected = true,
                strokeWidth = 4.dp,
                opacity = pulse
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
            val pointing = remember(table.label) { MutableInteractionSource() }
            val hovered by pointing.collectIsHoveredAsState()
            val held by pointing.collectIsPressedAsState()
            LaunchedEffect(hovered, held) {
                if (hovered || held) pointedTable = table.label else if (pointedTable == table.label) pointedTable = null
            }
            val groupFade by animateFloatAsState(
                targetValue = if (pointedGroup != null && table.label !in pointedGroup) 0.3f else 1f,
                animationSpec = tween(160),
                label = "merged-group-fade"
            )
            Table(
                shape = table.shape,
                seatCount = table.seatCount,
                label = table.label,
                state = table.state,
                guests = table.guestCount,
                // An orange table looks the same with an order that has no dishes yet.
                orderLabel = table.orderLabel.takeUnless { table.state == TableVisualState.Occupied },
                statusText = table.statusText,
                servedItems = table.servedItems,
                totalItems = table.totalItems,
                scale = displayedTable.scale,
                nextBookingClash = table.clash != null && !editMode && !mergeMode && movingTableLabels.isEmpty(),
                bookingComing = table.bookingComing && !editMode,
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
                        alpha = groupFade
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
                            detectDragGestures(onDragEnd = { onTableMoveEnd(table.label) }) { change, dragAmount ->
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
                    .hoverable(pointing)
                    .clickable(pointing, LocalIndication.current) {
                        if (editMode) onEditTableSelected(table) else onTableClick(table)
                    }
            )
            if (table.label in movingTableLabels || table.label in freedTableLabels) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = maxWidth * scaledX - 70.dp,
                            y = planHeight * scaledY - displayedTable.visualHeight() * 0.5f - if (smallPlan) 10.dp else 24.dp
                        )
                        .graphicsLayer {
                            translationX = planOffset.x
                            translationY = planOffset.y
                        }
                        .width(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (table.label in freedTableLabels) freedPill else "Moving",
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF4F7942))
                            .padding(horizontal = if (smallPlan) 4.dp else 10.dp, vertical = if (smallPlan) 0.5.dp else 3.dp),
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Bold,
                        fontSize = if (smallPlan) 6.sp else 11.sp,
                        letterSpacing = 0.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            // The booking's occasion at this table, e.g. 🎂 for a birthday.
            val occasionIcon = table.occasion()?.icon
            if (occasionIcon != null && !editMode) {
                Text(
                    occasionIcon,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = maxWidth * scaledX + displayedTable.visualWidth() * 0.5f - 12.dp,
                            y = planHeight * scaledY - displayedTable.visualHeight() * 0.5f - 8.dp
                        )
                        .graphicsLayer {
                            translationX = planOffset.x
                            translationY = planOffset.y
                        }
                )
            }
        }

        // The small note at each table in a special case: one for a merged group, above its highest table (user, 2026-11-01).
        // While a table is pointed at, only its own note stays (user, 2026-11-01).
        val shortNotes = maxWidth < 600.dp
        if (!editMode && !mergeMode && movingTableLabels.isEmpty()) visibleTables
            .filter { table -> table.note(shortNotes) != null }
            .groupBy { table -> mergedGroups.firstOrNull { table.label in it } ?: setOf(table.label) }
            .forEach { (labels, withNote) ->
                key(labels) {
                    val table = visibleTables.filter { it.label in labels }.minBy { it.y }
                    val displayedTable = table.copy(scale = table.scale * planScale * tableFitScale(maxWidth))
                    val scaledX = 0.5f + (table.x - 0.5f) * planScale
                    val scaledY = 0.5f + (table.y - 0.5f) * planScale
                    val angle = Math.toRadians(table.rotationDegrees.toDouble())
                    val height = displayedTable.visualHeight() * kotlin.math.abs(cos(angle)).toFloat() + displayedTable.visualWidth() * kotlin.math.abs(sin(angle)).toFloat()
                    val top = planHeight * scaledY - height * 0.5f
                    val above = top >= if (smallPlan) 11.dp else NextBookingNoteHeight
                    val (noteText, noteColor) = withNote.first().note(shortNotes)!!
                    AnimatedVisibility(
                        visible = pointedGroup == null || labels == pointedGroup,
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(160)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            // Centred over its table by the note's real width, so it's right above it (user, 2026-11-02); only
                            // the plan's edge moves it. A little into the table's empty corners, so it sits close (user, 2026-11-01).
                            .layout { measurable, constraints ->
                                val note = measurable.measure(constraints.copy(minWidth = 0))
                                val planWidth = maxWidth.roundToPx()
                                val x = ((maxWidth * scaledX).roundToPx() - note.width / 2).coerceIn(0, (planWidth - note.width).coerceAtLeast(0))
                                val y = when {
                                    !above -> (top + height - NextBookingNoteTuck).roundToPx()
                                    smallPlan -> (top + 2.dp).roundToPx() - note.height
                                    else -> (top - NextBookingNoteHeight + NextBookingNoteTuck).roundToPx()
                                }
                                layout(planWidth, note.height) { note.place(x, y) }
                            }
                            // It grows and shrinks with the plan, like the tables (user, 2026-11-02).
                            .graphicsLayer {
                                translationX = planOffset.x
                                translationY = planOffset.y
                            }
                    ) {
                        NextBookingNote(
                            text = noteText,
                            color = noteColor,
                            above = above,
                            small = smallPlan,
                            onClick = { onTableClick(table) }
                        )
                    }
                }
            }
    }
}

private val WaitlistRoom = 60.dp

private val NextBookingNoteHeight = 21.dp
private val NextBookingNoteTuck = 5.dp

internal val AutoFreeAmber = Color(0xFFB45309)

// What the table's small note says: the system stepping in first, then the next booking, then where a moved booking came from.
private fun FloorPlanTable.note(short: Boolean): Pair<String, Color>? {
    val freesAt = autoFreeAt
    val nextBooking = clash
    val from = movedFrom
    return when {
        freesAt != null -> (if (short) freesAt.clockTime() else "Frees at ${freesAt.clockTime()}") to AutoFreeAmber
        nextBooking != null -> nextBooking.noteText(short) to TableNextBookingPurple
        from != null && (state == TableVisualState.Reserved || state == TableVisualState.HoldEnding) ->
            (if (short) "From $from" else "Moved from $from") to TableNextBookingPurple
        else -> null
    }
}

// Says what's coming and opens the table's panel, where it can be handled (user, 2026-11-01).
@Composable
private fun NextBookingNote(text: String, color: Color, above: Boolean, small: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // A phone's plan is small: the note is in proportion to its tables (user, 2026-11-02: "the notification looks big").
    val pointer: @Composable () -> Unit = {
        Canvas(Modifier.size(width = if (small) 4.dp else 6.dp, height = if (small) 2.dp else 3.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                if (above) {
                    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height)
                } else {
                    moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width / 2, 0f)
                }
                close()
            }
            drawPath(path, color)
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!above) pointer()
        Row(
            Modifier
                .shadow(if (small) 1.5.dp else 3.dp, RoundedCornerShape(if (small) 4.dp else 6.dp))
                .clip(RoundedCornerShape(if (small) 4.dp else 6.dp))
                .background(color)
                .clickable(onClick = onClick)
                .padding(horizontal = if (small) 3.dp else 5.dp, vertical = if (small) 0.5.dp else 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (small) 1.5.dp else 2.dp)
        ) {
            Icon(Icons.Filled.NotificationsActive, null, Modifier.size(if (small) 6.dp else 9.dp), tint = Color.White)
            Text(
                text,
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = if (small) 5.5.sp else 8.5.sp,
                letterSpacing = 0.sp,
                color = Color.White,
                maxLines = 1
            )
        }
        if (above) pointer()
    }
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
    inset: Dp = 0.dp,
    opacity: Float = 1f
) {
    if (tables.isEmpty()) return

    Canvas(
        modifier = Modifier
            .width(planWidth)
            .height(planHeight)
            .graphicsLayer { alpha = opacity }
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
        // Tables of a group that stand apart get frames of their own, each round its table (user, 2026-11-01: "the table
        // is not in the centre of the black square"); only tables next to each other share one outline.
        // The gaps are measured at the tables' own size, so a phone's smaller plan draws the same frames (user, 2026-11-01), and
        // only small steps are evened out, so an outline round tables set a little apart still follows each of them.
        val fit = (planScale * tableFitScale(planWidth)).coerceIn(0.2f, 2f)
        val clusters = touchingClusters(tableRects, maxGap = 28.dp.toPx() * 2.25f * fit)
        val fillColor = color.copy(alpha = if (selected) 0.05f else 0.025f)
        tableRects.forEach { rect ->
            drawRect(
                color = fillColor,
                topLeft = Offset(rect.left, rect.top),
                size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
            )
        }

        clusters.flatMap { cluster ->
            orthogonalBoundarySegments(cluster + connectorRects(cluster, 28.dp.toPx() * fit), snapThreshold = if (cluster.size > 1) 8f * fit else 0f)
        }.forEach { edge ->
            drawLine(
                color = color,
                start = edge.first,
                end = edge.second,
                strokeWidth = (strokeWidth?.toPx() ?: if (selected) 3.dp.toPx() else 2.dp.toPx()) * planLineScale(planWidth, planScale),
                cap = StrokeCap.Square
            )
        }
    }
}

// Lines from a merged group's first table to the others, so tables standing apart read as one group.
@Composable
private fun BoxWithConstraintsScope.MergeGroupLinks(
    tables: List<FloorPlanTable>,
    planScale: Float,
    planOffset: Offset,
    strong: Boolean = true,
    faded: Boolean = false
) {
    if (tables.size < 2) return
    val color = if (strong) Color(0xFF4F7942) else Color(0xFF242424).copy(alpha = if (faded) 0.15f else 0.45f)
    val line = planLineScale(maxWidth, planScale)
    val width = (if (strong) 3.dp else 1.5.dp) * line
    Canvas(Modifier.width(maxWidth).height(maxHeight)) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx() * line, 7.dp.toPx() * line))
        val centers = tables.sortedBy { it.label }.map { table ->
            Offset(
                size.width * (0.5f + (table.x - 0.5f) * planScale) + planOffset.x,
                size.height * (0.5f + (table.y - 0.5f) * planScale) + planOffset.y
            )
        }
        centers.drop(1).forEach { center ->
            drawLine(color, centers.first(), center, strokeWidth = width.toPx(), pathEffect = dash, cap = StrokeCap.Round)
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
        val line = planLineScale(planWidth, planScale)
        val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx() * line, 8.dp.toPx() * line))

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
                style = Stroke(width = 2.dp.toPx() * line, pathEffect = dash)
            )
        }
    }
}

// The join and unmerge bars on a phone: the words get the whole width, the buttons share the line under them.
@Composable
private fun PhoneChoiceBar(title: String, detail: String, actions: @Composable RowScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F5F2), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE1DCD8), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Color(0xFF1F2322), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Color(0xFF777773), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalCompactToolbar provides true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

@Composable
private fun MergeModeToolbar(
    compact: Boolean = false,
    primaryLabel: String?,
    selectedLabels: List<String>,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    // Joining tables: what to do, which tables are picked so far, then Cancel and Merge (user, 2026-10-31), in the
    // edit bar's look.
    val canDone = selectedLabels.size >= 2
    if (compact) {
        PhoneChoiceBar(
            title = if (primaryLabel != null) "Select the tables you want to join with $primaryLabel" else "Select the tables you want to join",
            detail = if (canDone) "${selectedLabels.size} tables: ${selectedLabels.joinToString(", ")}" else "Tap the tables on the plan. Faded ones can't join."
        ) {
            ToolbarButton("Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
            ToolbarButton("Merge", primary = true, icon = Icons.Outlined.JoinInner, enabled = canDone, modifier = Modifier.weight(1f), onClick = onDone)
        }
        return
    }
    Row(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .background(Color(0xFFF7F5F2), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE1DCD8), RoundedCornerShape(12.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.weight(1f, fill = false).padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF4F7942).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.JoinInner, null, Modifier.size(20.dp), tint = Color(0xFF4F7942))
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = if (primaryLabel != null) "Select the tables you want to join with $primaryLabel"
                    else "Select the tables you want to join",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.sp,
                    color = Color(0xFF1F2322),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (canDone) "${selectedLabels.size} tables: ${selectedLabels.joinToString(", ")}"
                    else "Tap the tables on the plan. Faded ones can't join.",
                    fontFamily = Inter(),
                    fontSize = 12.sp,
                    letterSpacing = 0.sp,
                    color = Color(0xFF777773),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        ToolbarButton("Cancel", onClick = onCancel)
        ToolbarButton("Merge", primary = true, icon = Icons.Outlined.JoinInner, enabled = canDone, onClick = onDone)
    }
}

@Composable
private fun UnmergeToolbar(
    compact: Boolean = false,
    selectedLabels: List<String>,
    saving: Boolean,
    onCancel: () -> Unit,
    onUnmerge: () -> Unit
) {
    if (compact) {
        PhoneChoiceBar(
            title = "Select the tables that will be unmerged",
            detail = if (selectedLabels.isEmpty()) "Tap the tables on the plan" else selectedLabels.joinToString(", ")
        ) {
            ToolbarButton("Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
            ToolbarButton("Unmerge", primary = true, icon = Icons.Filled.CallSplit, enabled = selectedLabels.isNotEmpty(), isLoading = saving, modifier = Modifier.weight(1f), onClick = onUnmerge)
        }
        return
    }
    Row(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .background(Color(0xFFF7F5F2), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE1DCD8), RoundedCornerShape(12.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f, fill = false).padding(start = 10.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "Select the tables that will be unmerged",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.sp,
                color = Color(0xFF1F2322),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (selectedLabels.isEmpty()) "Tap the tables on the plan" else selectedLabels.joinToString(", "),
                fontFamily = Inter(),
                fontSize = 12.sp,
                letterSpacing = 0.sp,
                color = Color(0xFF777773),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ToolbarButton("Cancel", onClick = onCancel)
        ToolbarButton("Unmerge", primary = true, icon = Icons.Filled.CallSplit, enabled = selectedLabels.isNotEmpty(), isLoading = saving, onClick = onUnmerge)
    }
}

@Composable
private fun BoxScope.StatusFilterOverlays(
    loading: Boolean,
    maxHeight: Dp,
    specialCount: Int,
    onSpecial: () -> Unit,
    tables: List<FloorPlanTable>,
    selectedStatuses: Set<TableVisualState>,
    onAllSelected: () -> Unit,
    onStatusToggled: (TableVisualState) -> Unit
) {
    androidx.compose.animation.AnimatedContent(
        targetState = loading,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 14.dp)
            .width(166.dp + com.saporini.mobile_desktop.core.components.ScrollbarGap)
            .heightIn(max = maxHeight),
        transitionSpec = { com.saporini.mobile_desktop.core.components.loadingSwap(targetState) },
        label = "tables-status-loading"
    ) { waiting ->
        com.saporini.mobile_desktop.core.components.ScrollColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (waiting) TableStatusCardsSkeleton(Modifier)
            else TableStatusCards(tables, selectedStatuses, onAllSelected, onStatusToggled, Modifier, specialCount = specialCount, onSpecial = onSpecial)
        }
    }
}

// Phones: the same status cards, smaller, all on the page.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StatusFilterGrid(
    loading: Boolean,
    specialCount: Int,
    onSpecial: () -> Unit,
    tables: List<FloorPlanTable>,
    selectedStatuses: Set<TableVisualState>,
    onAllSelected: () -> Unit,
    onStatusToggled: (TableVisualState) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Every status on the page, three a row above the plan (user, 2026-11-02); two on the narrowest phones.
        val columns = if ((maxWidth - 12.dp) / 3 >= 96.dp) 3 else 2
        androidx.compose.animation.AnimatedContent(
            targetState = loading,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = { com.saporini.mobile_desktop.core.components.loadingSwap(targetState) },
            label = "tables-status-loading"
        ) { waiting ->
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = columns
            ) {
                if (waiting) TableStatusCardsSkeleton(Modifier.weight(1f), compact = true)
                else TableStatusCards(tables, selectedStatuses, onAllSelected, onStatusToggled, Modifier.weight(1f), specialCount = specialCount, onSpecial = onSpecial, compact = true)
                // A shorter last row keeps the cards' width.
                val cards = if (!waiting && specialCount > 0) 9 else 8
                repeat((columns - cards % columns) % columns) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// The status cards' shapes while the tables load.
@Composable
private fun TableStatusCardsSkeleton(cardModifier: Modifier, compact: Boolean = false) {
    val alpha = com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha("tables-status")
    repeat(8) {
        Row(
            cardModifier
                .fillMaxWidth()
                .height(if (compact) PhoneStatusCardHeight else 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE8E8E4), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 7.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.fillMaxWidth().alpha(alpha), verticalAlignment = Alignment.CenterVertically) {
                if (compact) {
                    com.saporini.mobile_desktop.core.components.SkeletonBox(
                        Modifier.size(20.dp),
                        com.saporini.mobile_desktop.core.components.SkeletonLight,
                        RoundedCornerShape(6.dp)
                    )
                } else {
                    com.saporini.mobile_desktop.core.components.SkeletonBox(
                        Modifier.size(28.dp),
                        com.saporini.mobile_desktop.core.components.SkeletonLight,
                        RoundedCornerShape(8.dp)
                    )
                }
                Spacer(Modifier.width(if (compact) 6.dp else 9.dp))
                com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.weight(1f).height(if (compact) 9.dp else 11.dp))
                Spacer(Modifier.width(if (compact) 10.dp else 22.dp))
                com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.width(if (compact) 10.dp else 12.dp).height(if (compact) 9.dp else 11.dp))
            }
        }
    }
}

// The note on the Paid card (user, 2026-11-01).
@Composable
private fun PaidNote() {
    Text("Paid", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.sp, color = Color(0xFF1F2322))
    Text("The guests have paid and are waiting to leave. Set the table free when they go.", fontFamily = Inter(), fontSize = 12.sp,
        lineHeight = 17.sp, letterSpacing = 0.sp, color = Color(0xFF71736E))
}

// What the rings around tables mean, opened from the note on the Ordered and Reserved cards (user, 2026-10-31).
@Composable
private fun OrderedRingLegend() {
    RingLegend(
        TableVisualState.Ordered,
        "The dark blue ring",
        "How much of the order is served. It starts empty and fills as dishes are served.",
        listOf(0f to "Nothing yet", 0.5f to "Some served", 1f to "All served")
    )
}

@Composable
private fun OccupiedRingLegend() {
    RingLegend(
        TableVisualState.Occupied,
        "The dark orange ring",
        "Half filled: more guests will come. Green chairs are free.",
        listOf(0.5f to "More will come")
    )
}

// A booking is coming to this free table (user, 2026-11-01).
@Composable
private fun FreeRingLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RingSample(TableVisualState.Free, 1f, ring = TableVisualState.HoldEnding)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("The yellow ring", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.sp, color = Color(0xFF1F2322))
            Text(
                "A booking is coming to this table soon. Don't seat walk-ins here.",
                fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp, color = Color(0xFF71736E)
            )
        }
    }
}

@Composable
private fun ReservedRingLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RingSample(TableVisualState.HoldEnding, 1f)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("The yellow ring", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.sp, color = Color(0xFF1F2322))
            Text(
                "The guests are late and the booking is about to go out.",
                fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp, color = Color(0xFF71736E)
            )
        }
    }
}

@Composable
private fun RingLegend(state: TableVisualState, title: String, text: String, steps: List<Pair<Float, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.sp, color = Color(0xFF1F2322))
        Text(text, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp, color = Color(0xFF71736E))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        steps.forEach { (progress, caption) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RingSample(state, progress)
                Text(caption, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.sp, color = Color(0xFF1F2322))
            }
        }
    }
}

// A small table with its ring, drawn the way the plan draws it.
@Composable
private fun RingSample(state: TableVisualState, progress: Float, ring: TableVisualState = state) {
    val body = tableStatusColor(if (state == TableVisualState.HoldEnding) TableVisualState.Reserved else state)
    Canvas(Modifier.size(44.dp)) {
        drawCircle(body, radius = size.minDimension * 0.40f)
        drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension * 0.2f)
        tableRing(ring, progress)?.let { drawCircleRing(it, 2.5.dp.toPx()) }
    }
}

@Composable
private fun TableStatusCards(
    tables: List<FloorPlanTable>,
    selectedStatuses: Set<TableVisualState>,
    onAllSelected: () -> Unit,
    onStatusToggled: (TableVisualState) -> Unit,
    cardModifier: Modifier,
    compact: Boolean = false,
    specialCount: Int = 0,
    onSpecial: () -> Unit = {}
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
            onClick = { onStatusToggled(TableVisualState.Free) },
            legend = { FreeRingLegend() }
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
            onClick = { onStatusToggled(TableVisualState.Occupied) },
            legend = { OccupiedRingLegend() }
        )
        TableStatusCard(
            compact = compact,
            modifier = cardModifier,
            label = "Ordered",
            count = statusCount(tables, TableVisualState.Ordered),
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            iconBackground = tableStatusColor(TableVisualState.Ordered).copy(alpha = .14f),
            iconTint = tableStatusColor(TableVisualState.Ordered),
            selected = TableVisualState.Ordered in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.Ordered) },
            legend = { OrderedRingLegend() }
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
            onClick = { onStatusToggled(TableVisualState.Reserved) },
            legend = { ReservedRingLegend() }
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
        // Hold ending has no card: the pulsing yellow ring on the table says it (user, 2026-11-01).
        TableStatusCard(
            compact = compact,
            modifier = cardModifier,
            label = "Paid",
            count = statusCount(tables, TableVisualState.Paid),
            icon = Icons.Filled.CheckCircle,
            iconBackground = tableStatusColor(TableVisualState.Paid).copy(alpha = .16f),
            iconTint = tableStatusColor(TableVisualState.Paid),
            selected = TableVisualState.Paid in selectedStatuses,
            onClick = { onStatusToggled(TableVisualState.Paid) },
            legend = { PaidNote() }
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
        // Not a state to filter by: what the system did by itself, to see and return. Only there when it did something.
        if (specialCount > 0) TableStatusCard(
            compact = compact,
            modifier = cardModifier,
            label = "No action taken",
            count = specialCount,
            icon = Icons.Filled.NotificationsActive,
            iconBackground = AutoFreeAmber.copy(alpha = .14f),
            iconTint = AutoFreeAmber,
            selected = false,
            onClick = onSpecial
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
    compact: Boolean = false,
    // What this state's mark on the plan means, behind a small note on the card.
    legend: (@Composable ColumnScope.() -> Unit)? = null
) {
    val enabled = count > 0
    val visuallySelected = selected && enabled
    val disabledContentColor = Color(0xFF7F847D)
    val contentColor = if (enabled) Color(0xFF1F2322) else disabledContentColor

    val card = modifier
        .fillMaxWidth()
        .height(if (compact) PhoneStatusCardHeight else 46.dp)
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
        .padding(horizontal = if (compact) 7.dp else 9.dp)
    val iconTile: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(if (compact) 20.dp else 28.dp)
                .clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
                .background(
                    if (enabled) iconBackground else Color(0xFFE1E4DE)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 12.dp else 17.dp),
                tint = if (enabled) iconTint else disabledContentColor
            )
        }
    }
    val name: @Composable (Modifier) -> Unit = { nameModifier ->
        Text(
            text = label,
            modifier = nameModifier,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 11.sp else 13.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            letterSpacing = 0.sp,
            color = contentColor
        )
    }
    val note: @Composable () -> Unit = {
        var legendOpen by remember { mutableStateOf(false) }
        Box {
            Box(
                Modifier
                    .size(if (compact) 18.dp else 26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { legendOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Notes,
                    "What the ring means",
                    Modifier.size(if (compact) 13.dp else 17.dp),
                    tint = Color(0xFF71736E)
                )
            }
            DropdownMenu(
                expanded = legendOpen,
                onDismissRequest = { legendOpen = false },
                shape = RoundedCornerShape(12.dp),
                containerColor = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    Modifier.width(280.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = legend ?: {}
                )
            }
        }
    }
    val countText: @Composable () -> Unit = {
        Text(
            text = count.toString(),
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 13.sp else 15.sp,
            letterSpacing = 0.sp,
            color = contentColor
        )
    }
    // Phones have three a row (user, 2026-11-02), so two lines: the icon and count, then the name and its note.
    if (compact) Column(card, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            iconTile()
            Spacer(Modifier.weight(1f))
            countText()
        }
        Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
            name(Modifier.weight(1f))
            if (legend != null) note()
        }
    } else Row(card, verticalAlignment = Alignment.CenterVertically) {
        iconTile()
        Spacer(Modifier.width(9.dp))
        name(Modifier.weight(1f))
        if (legend != null) {
            note()
            Spacer(Modifier.width(6.dp))
        }
        countText()
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
        FloorDropdown(floors, selectedFloor, onFloorSelected, modifier, compact)
        return
    }
    Row(
        modifier = modifier
            .width((if (compact) 84.dp else 118.dp) * floors.size)
            .height(if (compact) 36.dp else 44.dp)
            .clip(RoundedCornerShape(if (compact) 11.dp else 14.dp))
            .background(Color(0xFFF5F2F0))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        floors.forEach { floor ->
            val selected = floor == selectedFloor
            // Phones: a plain box, since a Material button grows to its 48 dp touch size and fills the grey track.
            if (compact) Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color(0xFF4F7942) else Color.Transparent)
                    .clickable { onFloorSelected(floor) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = floor.label,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.sp,
                    color = if (selected) Color.White else Color(0xFF666664)
                )
            } else TextButton(
                onClick = { onFloorSelected(floor) },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
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

// A floor with nothing on it yet. People who can change the layout get the way to add its picture right here, in the
// middle of where the plan will be (user, 2026-10-31).
@Composable
private fun BoxScope.EmptyFloorPlan(
    // A phone's header: the card goes under it, never over the status cards.
    topRoom: Dp = 0.dp,
    small: Boolean = false,
    phoneLayout: Boolean,
    isSaving: Boolean,
    onPlanPicked: ((ByteArray, String, String) -> Unit)?
) {
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) {
            scope.launch { onPlanPicked?.invoke(file.readBytes(), file.name, imageContentType(file.name)) }
        }
    }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(280)) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            // The middle of the plan's room, not of the page with its status cards.
            .padding(top = topRoom, end = if (phoneLayout) 0.dp else 182.dp, bottom = if (phoneLayout) PhonePlanBottomRoom else 0.dp)
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 14.dp.toPx()
            }
            .shadow(14.dp, shape)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE8E8E4), shape),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().height(if (small) 84.dp else 190.dp).background(Color(0xFFF7F4EF)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.plan),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(10.dp).alpha(0.5f),
                contentScale = ContentScale.Fit
            )
            Box(
                Modifier.size(if (small) 44.dp else 60.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Map, null, Modifier.size(if (small) 22.dp else 28.dp), tint = Color(0xFF4F7942))
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (small) 16.dp else 24.dp, vertical = if (small) 12.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (small) 4.dp else 6.dp)
        ) {
            Text(
                text = "No floor layout yet",
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = if (small) 16.sp else 20.sp,
                letterSpacing = 0.sp,
                color = Color(0xFF232422)
            )
            Text(
                text = if (onPlanPicked != null) {
                    "Add a picture of this floor, then put your tables on it."
                } else {
                    "An administrator has not configured this floor."
                },
                fontFamily = Inter(),
                fontSize = if (small) 13.sp else 14.sp,
                lineHeight = if (small) 18.sp else 20.sp,
                letterSpacing = 0.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = Color(0xFF71736E)
            )
            if (onPlanPicked != null) {
                Spacer(Modifier.height(if (small) 6.dp else 10.dp))
                com.saporini.mobile_desktop.core.components.KitButton(
                    "Add floor layout",
                    { picker.launch() },
                    icon = Icons.Outlined.Add,
                    loading = isSaving
                )
            }
        }
    }
}

// What a table is when joining: free and reserved are the lowest, then guests seated without an order, then an order.
// Free 0, reserved 1, seated 2, an order 3: a free table doesn't take in a reserved one (user, 2026-11-01).
// An order with no dishes yet stays orange and counts as guests seated (user, 2026-11-01).
private fun FloorPlanTable.joinRank(): Int = when {
    orderLabel != null && state != TableVisualState.Occupied -> 3
    state == TableVisualState.Occupied -> 2
    state == TableVisualState.Reserved || state == TableVisualState.HoldEnding -> 1
    else -> 0
}

private const val TablesEditHintKey = "tables-edit-select-table"

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

// How much two tables may touch: "a little connected is fine, not on top of each other" (user, 2026-10-31).
private const val TableTouchAllowed = 0.12f

// On a plan of 1054dp (a desktop's), tables and their places scale together, so this holds at every size.
private fun FloorPlanTable.sitsOnTopOf(atX: Float, atY: Float, other: FloorPlanTable): Boolean {
    val planWidth = 1054f
    val planHeight = planWidth / (1448f / 1086f)
    val roomX = (rotatedVisualWidth().value + other.rotatedVisualWidth().value) * 0.5f * (1f - TableTouchAllowed) / planWidth
    val roomY = (rotatedVisualHeight().value + other.rotatedVisualHeight().value) * 0.5f * (1f - TableTouchAllowed) / planHeight
    return abs(atX - other.x) < roomX && abs(atY - other.y) < roomY
}

// This table where it stands, or moved to the nearest spot where it isn't on top of another table on its floor.
private fun FloorPlanTable.withoutOverlap(tables: List<FloorPlanTable>): FloorPlanTable {
    val others = tables.filter { it.label != label && it.floorName == floorName && it.active }
    if (others.none { sitsOnTopOf(x, y, it) }) return this
    for (ring in 1..60) {
        val radius = ring * 0.012f
        val spots = 8 * ring
        for (spot in 0 until spots) {
            val angle = 2.0 * PI * spot / spots
            val atX = x + radius * cos(angle).toFloat()
            val atY = y + radius * (1448f / 1086f) * sin(angle).toFloat()
            if (atX in 0.04f..0.96f && atY in 0.04f..0.96f && others.none { sitsOnTopOf(atX, atY, it) }) {
                return copy(x = atX, y = atY)
            }
        }
    }
    return this
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

// The tables that touch, or nearly (within [maxGap] on one side while lined up on the other), grouped together.
private fun touchingClusters(rects: List<Rect>, maxGap: Float): List<List<Rect>> {
    fun near(a: Rect, b: Rect): Boolean {
        val gapX = maxOf(0f, maxOf(a.left, b.left) - minOf(a.right, b.right))
        val gapY = maxOf(0f, maxOf(a.top, b.top) - minOf(a.bottom, b.bottom))
        return (gapX <= maxGap && gapY == 0f) || (gapY <= maxGap && gapX == 0f)
    }
    val clusterOf = IntArray(rects.size) { it }
    fun root(i: Int): Int = if (clusterOf[i] == i) i else root(clusterOf[i]).also { clusterOf[i] = it }
    for (i in rects.indices) for (j in i + 1 until rects.size) {
        if (near(rects[i], rects[j])) clusterOf[root(i)] = root(j)
    }
    return rects.indices.groupBy { root(it) }.values.map { indices -> indices.map { rects[it] } }
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

private fun FloorPlanTable.rotatedVisualWidth(): Dp =
    if (isQuarterTurned()) visualHeight() else visualWidth()

private fun FloorPlanTable.rotatedVisualHeight(): Dp =
    if (isQuarterTurned()) visualWidth() else visualHeight()

private fun FloorPlanTable.isQuarterTurned(): Boolean {
    val normalizedRotation = ((rotationDegrees % 180f) + 180f) % 180f
    return normalizedRotation in 45f..135f
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.width(if (compact) 140.dp else 160.dp).height(if (compact) 36.dp else 44.dp),
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
        title = { Text("Edit floors", letterSpacing = 0.sp, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
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
                Text("Done", letterSpacing = 0.sp, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = {}
    )
}

// Booking areas, opened from Reservations (user, 2026-11-02: moved from Tables → Edit, where "Group tables" sat beside joining
// tables): areas like Main Salon or Terrace, their tables picked on the plan, and their order, which decides where bookings get
// their tables first.
@Composable
fun BookingAreasDialog(onDismiss: () -> Unit) {
    val screenModel = koinInject<TablesScreenModel>()
    val state by screenModel.state.collectAsState()
    val allTables = state.tableLayout?.tables.orEmpty()
    val floors = remember(state.floorLayouts, allTables) {
        val names = activeFloorNames(state.floorLayouts, allTables)
        FloorOption.entries.filter { it.label in names }
    }
    var pickedFloor by remember { mutableStateOf<FloorOption?>(null) }
    val floor = pickedFloor?.takeIf { it in floors } ?: floors.firstOrNull() ?: FloorOption.FIRST
    val layout = state.floorLayouts.firstOrNull { it.floorName == floor.label }
    val planBitmap = remember(layout?.id, state.planImageBytes) {
        layout?.let { state.planImageBytes[it.id] }?.let(PlanPictures::of)
    }
    val uiTables = remember(allTables) { allTables.map { it.toUiTable() } }
    TableSectionsDialog(
        tables = uiTables,
        onDismiss = onDismiss,
        onLoadSections = screenModel::loadTableSections,
        onSaveSection = screenModel::saveTableSection,
        onDeleteSection = screenModel::deleteTableSection,
        onSetSectionTables = screenModel::setTableSectionTables,
        onReorderSections = screenModel::reorderTableSections,
        floorPlan = { highlighted, outlines, onPlanTableClick, planModifier ->
            Column(planModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (floors.size > 1) FloorSwitcher(floors = floors, selectedFloor = floor, onFloorSelected = { pickedFloor = it }, compact = true)
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
                            tables = uiTables.filter { it.floorName == floor.label && it.active },
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
        layout?.let { state.planImageBytes[it.id] }?.let(PlanPictures::of)
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
        layout?.let { state.planImageBytes[it.id] }?.let(PlanPictures::of)
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

// The widest the plan gets (big screens); a 1920 × 1080 screen then shows all of it without scrolling.
private val PlanMaxWidth = 1100.dp

// Tables keep their full size on tablet-wide plans (760-1054dp; user, 2026-10-31: "the tablet was perfect") and grow
// and shrink exactly with the plan beyond that: phones and big screens, so they keep their place and size on it.
// 1054dp is the plan's width on a 1280 desktop, the size tables were made for.
internal fun tableFitScale(planWidth: Dp): Float = when {
    planWidth < 600.dp || planWidth > 1054.dp -> planWidth / 1054.dp
    else -> (planWidth / 760.dp).coerceIn(0.6f, 1f)
}

// Frames and lines drawn on the plan thin with the tables on a phone's small plan, so they stay in proportion, zoomed in too
// (user, 2026-11-01: "some tables do not look good with the border"). Tablets and desktops keep theirs.
private fun planLineScale(planWidth: Dp, planScale: Float): Float =
    if (planWidth < 600.dp) (planScale * tableFitScale(planWidth)).coerceAtLeast(0.4f) else 1f

private val PhoneStatusCardHeight = 50.dp

// The row at the end of a phone's page, in its corners: the Waitlist and the zoom control (10 from the edges, 40 high), plus a
// little air.
private val PhonePlanBottomRoom = 58.dp
private val PhoneCornerGap = 10.dp

// The Tables page's room on its left and right.
private val PageSideRoom = 22.dp

// Lets something run out over the side room its parent keeps.
private fun Modifier.bleedSides(side: Dp): Modifier = layout { measurable, constraints ->
    val extra = side.roundToPx()
    val width = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

// Plan pictures already decoded (the last few), so coming back to Tables doesn't decode the same picture again.
private object PlanPictures {
    private val recent = ArrayDeque<Pair<ByteArray, androidx.compose.ui.graphics.ImageBitmap>>()

    fun of(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap =
        recent.firstOrNull { it.first === bytes }?.second
            ?: bytes.decodeToImageBitmap().also {
                recent.addFirst(bytes to it)
                while (recent.size > 4) recent.removeLast()
            }
}

// Loading shapes for the plan: a light floor with its walls (rooms along the top, the main hall, the rounded terrace)
// and tables with their chairs where tables usually stand, pulsing until the real plan fades in.
@Composable
private fun TablesPlanSkeleton(modifier: Modifier) {
    val alpha = com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha("tables-plan")
    val wall = com.saporini.mobile_desktop.core.components.SkeletonBlock
    val table = com.saporini.mobile_desktop.core.components.SkeletonBlock
    val floor = Color(0xFFF4F4F2)
    Canvas(modifier.aspectRatio(1448f / 1086f).clip(RoundedCornerShape(10.dp)).alpha(alpha)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.007f)
        val corner = androidx.compose.ui.geometry.CornerRadius(w * 0.02f)
        fun at(x: Float, y: Float) = Offset(w * x, h * y)
        fun box(bw: Float, bh: Float) = androidx.compose.ui.geometry.Size(w * bw, h * bh)

        // The floor and its walls.
        drawRoundRect(floor, at(0.22f, 0.05f), box(0.62f, 0.27f), corner)
        drawRoundRect(floor, at(0.07f, 0.30f), box(0.86f, 0.50f), corner)
        drawArc(floor, 0f, 180f, true, at(0.07f, 0.55f), box(0.86f, 0.42f))
        drawRoundRect(wall, at(0.22f, 0.05f), box(0.62f, 0.27f), corner, style = stroke)
        drawRoundRect(wall, at(0.07f, 0.30f), box(0.86f, 0.50f), corner, style = stroke)
        drawArc(wall, 0f, 180f, false, at(0.07f, 0.55f), box(0.86f, 0.42f), style = stroke)
        listOf(0.37f, 0.52f, 0.68f).forEach { x -> drawLine(wall, at(x, 0.05f), at(x, 0.30f), stroke.width) }
        drawArc(wall, 0f, 180f, false, at(0.40f, 0.18f), box(0.20f, 0.26f), style = stroke)

        // Round tables with four chairs, and one long table.
        val r = w * 0.026f
        val chair = androidx.compose.ui.geometry.Size(r * 0.9f, r * 0.32f)
        val chairCorner = androidx.compose.ui.geometry.CornerRadius(chair.height / 2)
        listOf(0.63f to 0.52f, 0.73f to 0.52f, 0.63f to 0.64f, 0.73f to 0.64f, 0.46f to 0.66f, 0.38f to 0.70f, 0.18f to 0.48f, 0.16f to 0.64f)
            .forEach { (x, y) ->
                val c = at(x, y)
                drawCircle(table, r, c)
                val gap = r * 1.45f
                drawRoundRect(table, Offset(c.x - chair.width / 2, c.y - gap - chair.height / 2), chair, chairCorner)
                drawRoundRect(table, Offset(c.x - chair.width / 2, c.y + gap - chair.height / 2), chair, chairCorner)
                drawRoundRect(table, Offset(c.x - gap - chair.height / 2, c.y - chair.width / 2), androidx.compose.ui.geometry.Size(chair.height, chair.width), chairCorner)
                drawRoundRect(table, Offset(c.x + gap - chair.height / 2, c.y - chair.width / 2), androidx.compose.ui.geometry.Size(chair.height, chair.width), chairCorner)
            }
        val longTop = at(0.755f, 0.09f)
        val longSize = box(0.045f, 0.20f)
        drawRoundRect(table, longTop, longSize, androidx.compose.ui.geometry.CornerRadius(w * 0.008f))
        repeat(3) { i ->
            val y = longTop.y + longSize.height * (0.2f + i * 0.3f) - chair.width / 2
            drawRoundRect(table, Offset(longTop.x - chair.height * 2f, y), androidx.compose.ui.geometry.Size(chair.height, chair.width), chairCorner)
            drawRoundRect(table, Offset(longTop.x + longSize.width + chair.height, y), androidx.compose.ui.geometry.Size(chair.height, chair.width), chairCorner)
        }
    }
}

// A phone's plan: how far it's zoomed in and moved. The plan rests low on the page, [bottomRoom] above the box's end.
@androidx.compose.runtime.Stable
private class PlanZoom(private val bottomRoom: Float) {
    var zoom by mutableStateOf(1f)
        private set
    var pan by mutableStateOf(Offset.Zero)
        private set
    var boxSize = IntSize.Zero
    var planSize = IntSize.Zero

    // Where the plan's middle rests in the box.
    private fun restY() = boxSize.height - bottomRoom - planSize.height / 2f

    // A point in the box, from the plan's middle.
    fun focusOf(point: Offset) = point - Offset(boxSize.width / 2f, restY())

    private fun clamp(offset: Offset, scale: Float): Offset {
        val maxX = boxSize.width * (scale - 1f) / 2f
        // Up and down: a plan shorter than the box stays inside it, a taller one leaves no empty room.
        val half = planSize.height * scale / 2f
        val toTop = half - restY()
        val toBottom = boxSize.height - half - restY()
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(minOf(toTop, toBottom), maxOf(toTop, toBottom)))
    }

    // Zooms keeping [focus] (from the plan's middle) where it is on screen.
    fun zoomTo(newZoom: Float, focus: Offset = Offset.Zero) {
        val target = newZoom.coerceIn(1f, 4f)
        pan = if (target == 1f) Offset.Zero else clamp(focus - (focus - pan) * (target / zoom), target)
        zoom = target
    }

    fun moveBy(change: Offset) {
        if (zoom > 1f) pan = clamp(pan + change, zoom)
    }

    fun fit() {
        zoom = 1f
        pan = Offset.Zero
    }

    // After a pinch: all but back at the start counts as back, so Fit doesn't stay for nothing.
    fun settle() {
        if (zoom < 1.05f) fit()
    }
}

// Phones: two fingers zoom the plan (1x-4x) around the spot between them, and once zoomed one finger moves it (user,
// 2026-11-01: "you can increase with two fingers and move through it"). At 1x one finger still scrolls the page, and taps
// always reach the tables. The plan rests low on the page, just above the row at its end (user, 2026-11-01: "more down, almost
// at the end"), and zoomed in it has all of [minHeight].
@Composable
private fun ZoomablePlan(placed: Boolean, enabled: Boolean, plan: PlanZoom, minHeight: Dp = 0.dp, content: @Composable () -> Unit) {
    if (!placed) {
        content()
        return
    }
    var pinchHint by remember { mutableStateOf(!DeviceHints.isHidden(TablesPinchHintKey)) }
    // Editing moves the tables with a finger, so the plan goes back to fit.
    LaunchedEffect(enabled) { if (!enabled) plan.fit() }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clipToBounds()
            .onSizeChanged { plan.boxSize = it }
            .pointerInput(plan, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // A tap stays a tap, so tables still open when zoomed in (user, 2026-11-02): only a real move or two
                    // fingers take the touch away from what's under the finger.
                    var moving = false
                    var moved = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers > 1 || plan.zoom > 1f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (!moving) {
                                moved += panChange
                                moving = fingers > 1 || moved.getDistance() > viewConfiguration.touchSlop
                            }
                            if (moving && (zoomChange != 1f || panChange != Offset.Zero)) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                plan.zoomTo(plan.zoom * zoomChange, if (centroid == Offset.Unspecified) Offset.Zero else plan.focusOf(centroid))
                                plan.moveBy(panChange)
                                if (zoomChange != 1f) pinchHint = false
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    plan.settle()
                }
            }
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PhonePlanBottomRoom)
                .fillMaxWidth()
                .onSizeChanged { plan.planSize = it }
                .graphicsLayer {
                    scaleX = plan.zoom
                    scaleY = plan.zoom
                    translationX = plan.pan.x
                    translationY = plan.pan.y
                }
        ) { content() }
    }
    if (pinchHint && enabled) com.saporini.mobile_desktop.core.components.BottomToast(
        "Use two fingers to zoom the plan, then one finger to move it.",
        isTip = true,
        actionLabel = "Don't show again",
        onAction = { DeviceHints.hide(TablesPinchHintKey); pinchHint = false },
        onDismiss = { pinchHint = false }
    )
}

private const val TablesPinchHintKey = "tables-pinch-zoom"

// − and + together, with Fit while zoomed in.
@Composable
private fun PlanZoomButtons(plan: PlanZoom, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE3E6E1)),
            shadowElevation = 4.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZoomButton(Icons.Outlined.Remove, "Zoom out", framed = false, enabled = plan.zoom > 1f) { plan.zoomTo(plan.zoom - 0.5f) }
                Box(Modifier.width(1.dp).height(20.dp).background(Color(0xFFE3E6E1)))
                ZoomButton(Icons.Outlined.Add, "Zoom in", framed = false, enabled = plan.zoom < 4f) { plan.zoomTo(plan.zoom + 0.5f) }
            }
        }
        if (plan.zoom > 1f) ZoomButton(Icons.Outlined.FitScreen, "Fit the plan", shape = CircleShape) { plan.fit() }
    }
}

@Composable
private fun ZoomButton(
    icon: ImageVector,
    description: String,
    framed: Boolean = true,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
        shape = shape,
        color = if (framed) Color.White else Color.Transparent,
        border = if (framed) BorderStroke(1.dp, Color(0xFFE3E6E1)) else null,
        shadowElevation = if (framed) 4.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(20.dp), tint = if (enabled) Color(0xFF232422) else Color(0xFFBFC2BD))
        }
    }
}
