package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import com.saporini.mobile_desktop.core.components.ReorderableList
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Map
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.isWidePhoneWindow
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogActionStatus
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogStatusBody
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import com.saporini.mobile_desktop.pos.menu.ui.menu.isPhoneMenuWindow
import com.saporini.mobile_desktop.pos.tables.domain.model.TableSection
import kotlinx.coroutines.launch

private val SectionGreen = Color(0xFF4F7942)
private val SectionGreenSoft = Color(0xFFEEF3EB)
private val SectionInk = Color(0xFF222426)
private val SectionMuted = Color(0xFF747572)
private val SectionBorder = Color(0xFFE4E5E1)
private val SectionSurface = Color(0xFFF7F7F5)
private val SectionDanger = Color(0xFFB13A2F)
private val ListWidth = 560.dp
private val SectionPalette = listOf(
    Color(0xFF4F7942), Color(0xFF24748A), Color(0xFFC8790B), Color(0xFF7B4FA0),
    Color(0xFFB13A2F), Color(0xFF2E8B7A), Color(0xFF8A5A2B), Color(0xFFC2477A)
)
private fun sectionColor(index: Int): Color = SectionPalette[index % SectionPalette.size]
private val ExpandedWidth = 1440.dp
private val ExpandedListWidth = 420.dp

@Composable
internal fun TableSectionsDialog(
    tables: List<FloorPlanTable>,
    onDismiss: () -> Unit,
    onLoadSections: suspend () -> Result<List<TableSection>>,
    onSaveSection: suspend (sectionId: String?, name: String, displayOrder: Int, tableIds: Set<String>) -> Result<TableSection>,
    onDeleteSection: suspend (sectionId: String) -> Result<Unit>,
    onSetSectionTables: suspend (sectionId: String, tableIds: Set<String>) -> Result<Unit>,
    onReorderSections: suspend (sectionIds: List<String>) -> Result<Unit>,
    floorPlan: @Composable (highlightedLabels: Set<String>?, outlines: List<Pair<Set<String>, Color>>, onTableClick: (FloorPlanTable) -> Unit, modifier: Modifier) -> Unit
) {
    val isPhone = isPhoneMenuWindow()
    val isWidePhone = isWidePhoneWindow()
    val scope = rememberCoroutineScope()
    var sections by remember { mutableStateOf<List<TableSection>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadToken by remember { mutableIntStateOf(0) }
    var sectionBeingEdited by remember { mutableStateOf<TableSection?>(null) }
    var addSectionOpen by remember { mutableStateOf(false) }
    var sectionToDelete by remember { mutableStateOf<TableSection?>(null) }
    var pickingSectionId by remember { mutableStateOf<String?>(null) }
    var savingTables by remember { mutableStateOf(false) }
    var pendingAdd by remember { mutableStateOf<FloorPlanTable?>(null) }
    var showMap by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }
    var workingOrder by remember { mutableStateOf<List<TableSection>>(emptyList()) }
    var savingOrder by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<Pair<FloorPlanTable, TableSection>?>(null) }
    var notificationSequence by remember { mutableIntStateOf(0) }
    var notifications by remember { mutableStateOf<List<TableNotification>>(emptyList()) }

    fun notify(message: String, tone: TableNotificationTone) {
        notificationSequence += 1
        notifications = (notifications + TableNotification(notificationSequence, message, tone)).takeLast(3)
    }

    LaunchedEffect(loadToken) {
        loadError = null
        onLoadSections().fold(
            onSuccess = { loaded -> sections = loaded.sortedBy { it.displayOrder } },
            onFailure = { error -> loadError = error.message ?: "Could not load sections." }
        )
    }

    fun upsert(saved: TableSection) {
        val current = sections.orEmpty()
        sections = if (current.any { it.id == saved.id }) {
            current.map { if (it.id == saved.id) saved else it }
        } else {
            current + saved
        }
    }

    val pickingSection = sections.orEmpty().firstOrNull { it.id == pickingSectionId }

    fun openPicker(sectionId: String?) {
        pickingSectionId = sectionId
    }

    fun saveTables(section: TableSection, tableIds: Set<String>, successMessage: String) {
        if (savingTables) return
        savingTables = true
        scope.launch {
            onSetSectionTables(section.id, tableIds).fold(
                onSuccess = {
                    sections = sections.orEmpty().map {
                        if (it.id == section.id) it.copy(tableIds = tableIds) else it.copy(tableIds = it.tableIds - tableIds)
                    }
                    notify(successMessage, TableNotificationTone.Success)
                },
                onFailure = { error ->
                    notify(error.message ?: "Could not update ${section.name}", TableNotificationTone.Error)
                }
            )
            savingTables = false
        }
    }

    // Reads live state, not composition values: the plan's click handler can outlive a single composition.
    fun onPlanTableClick(table: FloorPlanTable) {
        val tableId = table.id ?: return
        val section = sections.orEmpty().firstOrNull { it.id == pickingSectionId }
        if (section == null) {
            val owner = sections.orEmpty().firstOrNull { tableId in it.tableIds }
            notify(
                if (owner != null) "Table ${table.label} is in ${owner.name}" else "Table ${table.label} is not in a section yet",
                TableNotificationTone.Info
            )
            return
        }
        val owner = sections.orEmpty().firstOrNull { it.id != section.id && tableId in it.tableIds }
        when {
            tableId in section.tableIds ->
                notify("Table ${table.label} is already in ${section.name}", TableNotificationTone.Info)
            owner != null -> pendingMove = table to owner
            else -> pendingAdd = table
        }
    }

    fun removeTable(table: FloorPlanTable) {
        val section = sections.orEmpty().firstOrNull { it.id == pickingSectionId } ?: return
        val tableId = table.id ?: return
        saveTables(section, section.tableIds - tableId, "Table ${table.label} removed from ${section.name}")
    }

    val closeDialog: () -> Unit = onDismiss

    Dialog(onDismissRequest = closeDialog, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f))
                .then(if (isPhone) Modifier.safeDrawingPadding().imePadding() else Modifier)
                .padding(if (isWidePhone) 12.dp else 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val expanded = pickingSection != null || showMap
            val listWidth by animateDpAsState(
                targetValue = minOf(maxWidth, if (expanded) ExpandedListWidth else ListWidth),
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "sections-list-width"
            )
            val dialogWidth by animateDpAsState(
                targetValue = if (expanded) minOf(maxWidth, ExpandedWidth) else minOf(maxWidth, ListWidth),
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "sections-dialog-width"
            )
            val heightFraction by animateFloatAsState(
                targetValue = if (expanded) 0.92f else if (isWidePhone) 0.94f else 0.82f,
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "sections-dialog-height"
            )

            Box(
                modifier = Modifier.width(dialogWidth).fillMaxHeight(heightFraction)
                    .shadow(22.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                    .background(Color.White).border(1.dp, SectionBorder, RoundedCornerShape(16.dp))
            ) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(listWidth).fillMaxHeight()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Group tables", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = SectionInk)
                                Text(
                                    if (reorderMode) "Drag the six-dot handle to set the priority. The top group is used first when suggesting tables."
                                    else "Create sections like Main Salon or Terrace. Click a section to add its tables from the plan.",
                                    fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = SectionMuted
                                )
                            }
                            if (!expanded) {
                                CloseCircleButton("Close sections", closeDialog)
                            }
                        }
                        HorizontalDivider(color = SectionBorder)
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = if (isWidePhone) 10.dp else 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val loaded = sections
                            when {
                                loadError != null -> Column(
                                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(loadError.orEmpty(), fontFamily = Inter(), fontSize = 13.sp, color = SectionDanger)
                                    TextButton(onClick = { loadToken++ }) { Text("Try again", color = SectionGreen) }
                                }
                                loaded == null -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(32.dp), color = SectionGreen, strokeWidth = 3.dp)
                                }
                                reorderMode -> ReorderableList(
                                    items = workingOrder,
                                    key = { it.id },
                                    onReorder = { workingOrder = it }
                                ) { section, isDragging, dragHandle, rowModifier ->
                                    PriorityRow(
                                        priority = workingOrder.indexOfFirst { it.id == section.id } + 1,
                                        section = section,
                                        tableCount = tables.count { it.id in section.tableIds },
                                        color = sectionColor(loaded.indexOfFirst { it.id == section.id }),
                                        isDragging = isDragging,
                                        dragHandle = dragHandle,
                                        modifier = rowModifier
                                    )
                                }
                                else -> {
                                    if (loaded.isEmpty()) {
                                        Text(
                                            "No sections yet. Tap + to create the first one.",
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                            fontFamily = Inter(), fontSize = 13.sp, color = SectionMuted
                                        )
                                    }
                                    loaded.forEach { section ->
                                        val picking = section.id == pickingSectionId
                                        TableSectionRow(
                                            section = section,
                                            sectionTables = tables.filter { it.id in section.tableIds }.sortedBy { it.label },
                                            onRemoveTable = ::removeTable,
                                            picking = picking,
                                            onSelect = {
                                                if (picking) {
                                                    openPicker(null)
                                                    showMap = true
                                                } else {
                                                    openPicker(section.id)
                                                }
                                            },
                                            color = sectionColor(loaded.indexOf(section)),
                                            onEdit = { sectionBeingEdited = section },
                                            onDelete = { sectionToDelete = section }
                                        )
                                    }
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
                                    if (pickingSection != null) {
                                        Text(
                                            "Unselect all to see all groups",
                                            modifier = Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    openPicker(null)
                                                    showMap = true
                                                }
                                                .padding(vertical = 6.dp),
                                            fontFamily = Inter(), fontSize = 12.sp, color = SectionMuted,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = SectionBorder)
                        if (reorderMode) Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isWidePhone) 6.dp else 10.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { reorderMode = false }, enabled = !savingOrder) {
                                Text("Cancel", color = SectionMuted, fontFamily = Inter(), fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    savingOrder = true
                                    scope.launch {
                                        val ordered = workingOrder
                                        onReorderSections(ordered.map { it.id }).fold(
                                            onSuccess = {
                                                sections = ordered.mapIndexed { index, section -> section.copy(displayOrder = index) }
                                                reorderMode = false
                                                notify("Order saved. ${ordered.first().name} is used first for suggestions", TableNotificationTone.Success)
                                            },
                                            onFailure = { error ->
                                                notify(error.message ?: "Could not save the order", TableNotificationTone.Error)
                                            }
                                        )
                                        savingOrder = false
                                    }
                                },
                                enabled = !savingOrder,
                                shape = RoundedCornerShape(percent = 50),
                                colors = ButtonDefaults.buttonColors(containerColor = SectionGreen)
                            ) {
                                Text("Save order", fontFamily = Inter(), fontWeight = FontWeight.Bold)
                            }
                        } else Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isWidePhone) 6.dp else 10.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val canReorder = sections.orEmpty().size >= 2
                            val reorderInk = if (canReorder) SectionInk else SectionInk.copy(alpha = 0.4f)
                            Button(
                                onClick = {
                                    if (canReorder) {
                                        openPicker(null)
                                        showMap = false
                                        workingOrder = sections.orEmpty()
                                        reorderMode = true
                                    } else {
                                        notify("Add at least 2 groups to change their order", TableNotificationTone.Info)
                                    }
                                },
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(1.dp, reorderInk),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = reorderInk)
                            ) {
                                Icon(Icons.Outlined.DragIndicator, null, Modifier.size(18.dp), tint = reorderInk)
                                Spacer(Modifier.width(6.dp))
                                Text("Change position", fontFamily = Inter(), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.weight(1f))
                            Button(
                                onClick = {
                                    if (expanded) {
                                        showMap = false
                                        openPicker(null)
                                    } else {
                                        showMap = true
                                    }
                                },
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(1.dp, SectionInk),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SectionInk)
                            ) {
                                Icon(Icons.Outlined.Map, null, Modifier.size(18.dp), tint = SectionInk)
                                Spacer(Modifier.width(6.dp))
                                Text(if (expanded) "Hide map" else "Show map", fontFamily = Inter(), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(percent = 50),
                                colors = ButtonDefaults.buttonColors(containerColor = SectionGreen)
                            ) {
                                Text("Done", fontFamily = Inter(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = expanded,
                        modifier = Modifier.weight(1f, fill = false),
                        enter = fadeIn(tween(260, delayMillis = 160)),
                        exit = fadeOut(tween(140))
                    ) {
                        val section = pickingSection
                        Row(Modifier.fillMaxHeight()) {
                            VerticalDivider(color = SectionBorder)
                            val allSections = sections.orEmpty()
                            TablePickerPane(
                                section = section,
                                floorPlan = floorPlan,
                                highlightedLabels = section?.let { picked ->
                                    tables.filter { it.id in picked.tableIds }.map { it.label }.toSet()
                                },
                                outlines = if (section == null) {
                                    allSections.mapIndexed { index, each ->
                                        tables.filter { it.id in each.tableIds }.map { it.label }.toSet() to sectionColor(index)
                                    }
                                } else {
                                    emptyList()
                                },
                                onTableClick = ::onPlanTableClick,
                                onClose = { if (section != null) openPicker(null) else showMap = false }
                            )
                        }
                    }
                }

                TableNotificationQueue(
                    notifications = notifications,
                    onDismiss = { id -> notifications = notifications.filterNot { it.id == id } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
                )
            }
        }
    }

    val loaded = sections.orEmpty()
    if (addSectionOpen) {
        TableSectionNameDialog(
            section = null,
            sections = loaded,
            displayOrder = loaded.size,
            onDismiss = { addSectionOpen = false },
            onSaveSection = onSaveSection,
            onSaved = { saved ->
                upsert(saved)
                addSectionOpen = false
                openPicker(saved.id)
            }
        )
    }
    sectionBeingEdited?.let { section ->
        TableSectionNameDialog(
            section = section,
            sections = loaded,
            displayOrder = section.displayOrder,
            onDismiss = { sectionBeingEdited = null },
            onSaveSection = onSaveSection,
            onSaved = { upsert(it); sectionBeingEdited = null }
        )
    }
    pendingAdd?.let { table ->
        val section = pickingSection
        AddTableToSectionDialog(
            tableLabel = table.label,
            sectionName = section?.name.orEmpty(),
            onConfirm = {
                pendingAdd = null
                val tableId = table.id
                if (section != null && tableId != null) {
                    saveTables(section, section.tableIds + tableId, "Table ${table.label} added to ${section.name}")
                }
            },
            onCancel = { pendingAdd = null }
        )
    }
    pendingMove?.let { (table, owner) ->
        val section = pickingSection
        MoveTableDialog(
            tableLabel = table.label,
            fromSection = owner.name,
            toSection = section?.name.orEmpty(),
            onBringHere = {
                pendingMove = null
                val tableId = table.id
                if (section != null && tableId != null) {
                    saveTables(section, section.tableIds + tableId, "Table ${table.label} moved from ${owner.name} to ${section.name}")
                }
            },
            onCancel = { pendingMove = null }
        )
    }
    sectionToDelete?.let { section ->
        DeleteTableSectionDialog(
            section = section,
            onDismiss = { sectionToDelete = null },
            onDeleteSection = onDeleteSection,
            onDeleted = {
                sections = loaded.filterNot { it.id == section.id }
                if (pickingSectionId == section.id) openPicker(null)
                sectionToDelete = null
            }
        )
    }
}

@Composable
private fun TablePickerPane(
    section: TableSection?,
    floorPlan: @Composable (highlightedLabels: Set<String>?, outlines: List<Pair<Set<String>, Color>>, onTableClick: (FloorPlanTable) -> Unit, modifier: Modifier) -> Unit,
    highlightedLabels: Set<String>?,
    outlines: List<Pair<Set<String>, Color>>,
    onTableClick: (FloorPlanTable) -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(SectionSurface)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(SectionGreenSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (section == null) Icons.Outlined.Map else Icons.Outlined.TouchApp, null, Modifier.size(21.dp), tint = SectionGreen)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    buildAnnotatedString {
                        if (section == null) {
                            append("All sections on the plan")
                        } else {
                            append("Choose the tables you want to add to ")
                            withStyle(SpanStyle(color = SectionGreen)) { append(section.name) }
                        }
                    },
                    fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = SectionInk,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (section == null) "Each section has its own color. Click a section on the left to edit its tables."
                    else "Tap a table on the plan to add it. Remove tables with the × in the list on the left.",
                    fontFamily = Inter(), fontSize = 12.sp, color = SectionMuted
                )
            }
            Spacer(Modifier.width(12.dp))
            CloseCircleButton("Close plan", onClose)
        }
        HorizontalDivider(color = SectionBorder)
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(10.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color.White)
                .border(1.dp, SectionBorder, RoundedCornerShape(12.dp))
                .padding(6.dp)
        ) {
            floorPlan(highlightedLabels, outlines, onTableClick, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CloseCircleButton(description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(SectionSurface)
            .border(1.dp, SectionBorder, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Close, description, Modifier.size(18.dp), tint = SectionInk)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableSectionRow(
    section: TableSection,
    sectionTables: List<FloorPlanTable>,
    onRemoveTable: (FloorPlanTable) -> Unit,
    picking: Boolean,
    onSelect: () -> Unit,
    color: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tableLabels = sectionTables.map { it.label }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (picking) SectionGreenSoft else SectionSurface)
            .border(if (picking) 1.5.dp else 1.dp, if (picking) SectionGreen else SectionBorder, RoundedCornerShape(10.dp))
            .animateContentSize()
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(onClick = onSelect)
            .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).background(Color.White, RoundedCornerShape(8.dp))
                .border(1.5.dp, color, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.TableRestaurant, null, Modifier.size(18.dp), tint = color)
        }
        Spacer(Modifier.size(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                section.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = SectionInk, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (tableLabels.isEmpty()) "No tables yet"
                else "${tableLabels.size} ${if (tableLabels.size == 1) "table" else "tables"} · ${tableLabels.joinToString(", ")}",
                fontFamily = Inter(), fontSize = 11.sp, color = SectionMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                .border(1.dp, SectionBorder, RoundedCornerShape(8.dp)).clickable(onClick = onEdit),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Edit, "Rename ${section.name}", Modifier.size(18.dp), tint = SectionInk)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(SectionDanger)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.DeleteOutline, "Delete ${section.name}", Modifier.size(18.dp), tint = Color.White)
        }
    }
    if (picking) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sectionTables.isEmpty()) {
                Text(
                    "Tap tables on the plan to add them here.",
                    fontFamily = Inter(), fontSize = 12.sp, color = SectionMuted
                )
            }
            sectionTables.forEach { table ->
                SelectedTableChip(table.label) { onRemoveTable(table) }
            }
        }
    }
    }
}

@Composable
private fun SelectedTableChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(Color.White)
            .border(1.5.dp, SectionInk, RoundedCornerShape(50))
            .clickable(onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = SectionInk)
        Icon(Icons.Outlined.Close, "Remove $label", Modifier.size(14.dp), tint = SectionMuted)
    }
}

@Composable
private fun TableSectionNameDialog(
    section: TableSection?,
    sections: List<TableSection>,
    displayOrder: Int,
    onDismiss: () -> Unit,
    onSaveSection: suspend (sectionId: String?, name: String, displayOrder: Int, tableIds: Set<String>) -> Result<TableSection>,
    onSaved: (TableSection) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember(section) { mutableStateOf(section?.name.orEmpty()) }
    var attemptedSubmit by remember(section) { mutableStateOf(false) }
    var status by remember(section) { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    var savedSection by remember(section) { mutableStateOf<TableSection?>(null) }
    val trimmed = name.trim()
    val isDuplicate = trimmed.length >= 2 &&
        sections.any { it.id != section?.id && it.name.equals(trimmed, ignoreCase = true) }
    val valid = trimmed.length in 2..40 && !isDuplicate
    val errorText = when {
        trimmed.isEmpty() -> "Section name is required."
        trimmed.length < 2 -> "Use at least 2 characters."
        isDuplicate -> "A section with this name already exists."
        else -> null
    }
    val isIdle = status is DialogActionStatus.Idle

    fun submit() {
        if (!valid) {
            attemptedSubmit = true
            return
        }
        status = DialogActionStatus.Loading("Saving")
        scope.launch {
            onSaveSection(section?.id, trimmed, displayOrder, section?.tableIds.orEmpty()).fold(
                onSuccess = { saved ->
                    savedSection = saved
                    status = DialogActionStatus.Success(if (section == null) "$trimmed added" else "$trimmed saved")
                },
                onFailure = { error ->
                    status = DialogActionStatus.Failed(message = error.message ?: "Could not save this section.")
                }
            )
        }
    }

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        title = {
            Text(
                if (section == null) "Add section" else "Rename section",
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp
            )
        },
        text = {
            if (isIdle) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Section name", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SectionInk)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 40) name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("For example: Main Salon") },
                        singleLine = true,
                        isError = attemptedSubmit && !valid
                    )
                    if (attemptedSubmit && errorText != null) {
                        Text(errorText, fontFamily = Inter(), fontSize = 12.sp, color = SectionDanger)
                    } else if (section == null) {
                        Text(
                            "After you add it, pick its tables on the plan.",
                            fontFamily = Inter(), fontSize = 12.sp, color = SectionMuted
                        )
                    }
                }
            } else {
                DialogStatusBody(
                    status = status,
                    onRetry = { status = DialogActionStatus.Idle },
                    onCancel = onDismiss,
                    onSuccessSettled = { savedSection?.let(onSaved) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (isIdle) {
                Button(
                    onClick = { submit() },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (valid) SectionGreen else SectionGreen.copy(alpha = 0.45f)
                    )
                ) {
                    Text(if (section == null) "Add" else "Save", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
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
private fun DeleteTableSectionDialog(
    section: TableSection,
    onDismiss: () -> Unit,
    onDeleteSection: suspend (sectionId: String) -> Result<Unit>,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    val isIdle = status is DialogActionStatus.Idle

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        title = {
            Text(
                "Delete ${section.name}?", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            if (isIdle) {
                Text(
                    "The section will be removed. Its tables stay on the floor plan, they just won't belong to a section.",
                    fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = SectionMuted
                )
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
                    onClick = {
                        status = DialogActionStatus.Loading("Deleting")
                        scope.launch {
                            onDeleteSection(section.id).fold(
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
private fun AddTableToSectionDialog(
    tableLabel: String,
    sectionName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    MenuNestedDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                "Add table $tableLabel?", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Text(
                "Do you want to add table $tableLabel to $sectionName?",
                fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = SectionMuted
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = SectionGreen)
            ) {
                Text("Confirm", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = SectionMuted) }
        }
    )
}

@Composable
private fun MoveTableDialog(
    tableLabel: String,
    fromSection: String,
    toSection: String,
    onBringHere: () -> Unit,
    onCancel: () -> Unit
) {
    MenuNestedDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                "Table $tableLabel is in $fromSection", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Text(
                "Do you want to remove it from $fromSection and bring it to $toSection?",
                fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = SectionMuted
            )
        },
        confirmButton = {
            Button(
                onClick = onBringHere,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = SectionGreen)
            ) {
                Text("Bring it here", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = SectionMuted) }
        }
    )
}

@Composable
private fun PriorityRow(
    priority: Int,
    section: TableSection,
    tableCount: Int,
    color: Color,
    isDragging: Boolean,
    dragHandle: Modifier,
    modifier: Modifier
) {
    Row(
        modifier = modifier.shadow(if (isDragging) 9.dp else 0.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)).background(if (isDragging) Color.White else SectionSurface)
            .border(1.dp, if (isDragging) SectionGreen else SectionBorder, RoundedCornerShape(10.dp))
            .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).background(Color.White, RoundedCornerShape(8.dp)).border(1.5.dp, color, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("$priority", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
        }
        Spacer(Modifier.size(9.dp))
        Column(Modifier.weight(1f)) {
            Text(section.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = SectionInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (priority == 1) "Used first · " else "") + "$tableCount ${if (tableCount == 1) "table" else "tables"}",
                fontFamily = Inter(), fontSize = 11.sp, color = SectionMuted
            )
        }
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.White).border(1.dp, SectionBorder, RoundedCornerShape(8.dp))
                .then(dragHandle),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.DragIndicator, "Drag ${section.name}", Modifier.size(24.dp), tint = SectionInk)
        }
    }
}
