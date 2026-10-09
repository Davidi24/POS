package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogActionStatus
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogStatusBody
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch

@Composable
fun TableLayoutToolbar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    editMode: Boolean,
    planOnlyMode: Boolean,
    hasBackground: Boolean,
    isSaving: Boolean = false,
    hasChanges: Boolean = true,
    planMoveMode: Boolean,
    selectedTableLabels: Set<String>,
    onBackRequested: () -> Unit,
    onSaveChanges: () -> Unit,
    onAddTable: (TableShape, Int) -> Unit,
    onGroupTables: () -> Unit,
    onDuplicateSelectedTables: () -> Unit,
    onRotateSelectedTable: (Float) -> Unit,
    onScaleSelectedTable: (Float) -> Unit,
    onDeleteSelectedTable: (onSuccess: () -> Unit, onFailure: (String) -> Unit) -> Unit,
    onBackgroundSelected: (ByteArray, String, String) -> Unit,
    onRemoveBackground: () -> Unit,
    onPlanMoveModeChanged: (Boolean) -> Unit,
    onScalePlan: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddTableDialog by remember { mutableStateOf(false) }
    var showRemovePlanConfirm by remember { mutableStateOf(false) }
    var showDeleteTableConfirm by remember { mutableStateOf(false) }
    var deleteTableStatus by remember { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) {
            scope.launch {
                val fileName = file.name
                onBackgroundSelected(
                    file.readBytes(),
                    fileName,
                    imageContentType(fileName)
                )
            }
        }
    }

    if (editMode) {
        val doneButtonText = if (hasChanges) "Save changes" else "Done"
        Row(
            modifier = modifier
                .background(Color(0xFFF7F5F2), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFE1DCD8), RoundedCornerShape(12.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackRequested, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", modifier = Modifier.size(20.dp), tint = Color(0xFF303033))
            }
            if (planOnlyMode) {
                UploadPlanIconButton(
                    contentDescription = if (hasBackground) "Change plan" else "Upload plan",
                    isLoading = isSaving
                ) {
                    onPlanMoveModeChanged(false)
                    imagePicker.launch()
                }
                if (hasBackground) {
                    ToolbarButton(
                        text = "Move plan",
                        primary = planMoveMode,
                        icon = Icons.Outlined.OpenWith
                    ) { onPlanMoveModeChanged(!planMoveMode) }
                    PlanAdjustButton(Icons.Filled.Remove, "Decrease plan size") {
                        onScalePlan(-0.02f)
                    }
                    PlanAdjustButton(Icons.Filled.Add, "Increase plan size") {
                        onScalePlan(0.02f)
                    }
                    ToolbarButton(
                        text = "Remove",
                        icon = Icons.Outlined.DeleteOutline,
                        destructive = true,
                        isLoading = isSaving,
                        onClick = { showRemovePlanConfirm = true }
                    )
                }
                ToolbarButton(doneButtonText, primary = true) { onSaveChanges() }
            } else {
                if (selectedTableLabels.isNotEmpty()) {
                    ToolbarButton(
                        text = if (selectedTableLabels.size == 1) {
                            "Delete ${selectedTableLabels.first()}"
                        } else {
                            "Delete ${selectedTableLabels.size} tables"
                        },
                        icon = Icons.Outlined.DeleteOutline,
                        destructive = true,
                        isLoading = isSaving,
                        onClick = {
                            deleteTableStatus = DialogActionStatus.Idle
                            showDeleteTableConfirm = true
                        }
                    )
                }
                ToolbarButton(
                    text = "Add table",
                    icon = Icons.Filled.Add,
                    accent = true
                ) { showAddTableDialog = true }
                ToolbarButton(
                    text = "Group tables",
                    icon = Icons.Outlined.Workspaces,
                    onClick = onGroupTables
                )
                if (selectedTableLabels.isNotEmpty()) {
                    PlanAdjustButton(
                        icon = Icons.Outlined.ContentCopy,
                        contentDescription = "Duplicate selected tables",
                        onClick = onDuplicateSelectedTables
                    )
                    ToolbarButton("↶ 5°") { onRotateSelectedTable(-5f) }
                    ToolbarButton("↷ 5°") { onRotateSelectedTable(5f) }
                    ToolbarButton("− Size") { onScaleSelectedTable(-0.03f) }
                    ToolbarButton("+ Size") { onScaleSelectedTable(0.03f) }
                }
                ToolbarButton(doneButtonText, primary = true) { onSaveChanges() }
            }
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChanged,
                placeholder = "Search table",
                modifier = Modifier.width(240.dp),
                height = 44.dp
            )
        }
    }

    if (showAddTableDialog) {
        AddTableDialog(
            onDismiss = { showAddTableDialog = false },
            onAdd = { shape, chairs ->
                onAddTable(shape, chairs)
                showAddTableDialog = false
            }
        )
    }

    if (showRemovePlanConfirm) {
        MenuNestedDialog(
            onDismissRequest = { showRemovePlanConfirm = false },
            title = {
                Text("Remove plan?", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "This removes the uploaded floor plan image. This can't be undone.",
                    fontFamily = Inter()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemovePlanConfirm = false
                        onRemoveBackground()
                    },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB13A2F))
                ) {
                    Text("Remove", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePlanConfirm = false }) {
                    Text("Cancel", fontFamily = Inter(), color = Color(0xFF71736E))
                }
            }
        )
    }

    if (showDeleteTableConfirm) {
        val isIdle = deleteTableStatus is DialogActionStatus.Idle
        MenuNestedDialog(
            onDismissRequest = {
                if (deleteTableStatus !is DialogActionStatus.Loading) {
                    showDeleteTableConfirm = false
                    deleteTableStatus = DialogActionStatus.Idle
                }
            },
            title = {
                if (isIdle) {
                    Text(
                        if (selectedTableLabels.size == 1) {
                            "Delete ${selectedTableLabels.first()}?"
                        } else {
                            "Delete ${selectedTableLabels.size} tables?"
                        },
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                if (isIdle) {
                    Text(
                        "Are you sure you want to delete this from the database?",
                        fontFamily = Inter()
                    )
                } else {
                    DialogStatusBody(
                        status = deleteTableStatus,
                        onRetry = { deleteTableStatus = DialogActionStatus.Idle },
                        onCancel = {
                            showDeleteTableConfirm = false
                            deleteTableStatus = DialogActionStatus.Idle
                        },
                        onSuccessSettled = {
                            showDeleteTableConfirm = false
                            deleteTableStatus = DialogActionStatus.Idle
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                if (isIdle) {
                    Button(
                        onClick = {
                            deleteTableStatus = DialogActionStatus.Loading("Deleting")
                            onDeleteSelectedTable(
                                {
                                    showDeleteTableConfirm = false
                                    deleteTableStatus = DialogActionStatus.Idle
                                },
                                { message ->
                                    deleteTableStatus = DialogActionStatus.Failed(
                                        title = "Table cannot be deleted",
                                        message = message.ifBlank { "This table cannot be deleted right now." }
                                    )
                                }
                            )
                        },
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB13A2F))
                    ) {
                        Text("Delete", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            },
            dismissButton = {
                if (isIdle) {
                    TextButton(
                        onClick = {
                            showDeleteTableConfirm = false
                            deleteTableStatus = DialogActionStatus.Idle
                        }
                    ) {
                        Text("Cancel", fontFamily = Inter(), color = Color(0xFF71736E))
                    }
                }
            }
        )
    }
}

private fun imageContentType(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }
}

@Composable
private fun UploadPlanIconButton(
    contentDescription: String,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clickable(enabled = !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFFAAA69F),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(7.dp.toPx(), 5.dp.toPx())
                    )
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx())
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color(0xFF303033),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.FileUpload,
                contentDescription = contentDescription,
                modifier = Modifier.size(23.dp),
                tint = Color(0xFF303033)
            )
        }
    }
}

@Composable
private fun PlanAdjustButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFD9D5D0), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
            tint = Color(0xFF303033)
        )
    }
}

@Composable
fun EditLayoutIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFF4F7942), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Edit layout",
            modifier = Modifier.size(18.dp),
            tint = Color(0xFF4F7942)
        )
    }
}

@Composable
internal fun AddTableDialog(
    onDismiss: () -> Unit,
    onAdd: (TableShape, Int) -> Unit
) {
    var shape by remember { mutableStateOf(TableShape.Circle) }
    var chairs by remember { mutableStateOf(4) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add table", fontFamily = Inter(), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Shape", fontFamily = Inter(), fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChoiceButton("Round", shape == TableShape.Circle) { shape = TableShape.Circle }
                        ChoiceButton("Square", shape == TableShape.Square) { shape = TableShape.Square }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Number of chairs", fontFamily = Inter(), fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        StepButton("−", enabled = chairs > 1) { chairs-- }
                        Text(chairs.toString(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        StepButton("+", enabled = chairs < 12) { chairs++ }
                    }
                }
            }
        },
        confirmButton = { ToolbarButton("Add table", primary = true) { onAdd(shape, chairs) } },
        dismissButton = { ToolbarButton("Cancel", onClick = onDismiss) },
        containerColor = Color.White
    )
}

@Composable
private fun ChoiceButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .width(110.dp)
            .background(if (selected) Color(0xFFE9EDDE) else Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) Color(0xFF4F7942) else Color(0xFFD9D5D0), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        fontFamily = Inter(),
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF303033)
    )
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .size(40.dp)
            .background(if (enabled) Color(0xFFF2F0ED) else Color(0xFFF8F8F8), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(top = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        fontSize = 20.sp,
        color = if (enabled) Color(0xFF303033) else Color(0xFFBBBBBB)
    )
}

@Composable
private fun ToolbarButton(
    text: String,
    primary: Boolean = false,
    destructive: Boolean = false,
    accent: Boolean = false,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val background = when {
        destructive -> Color(0xFFB13A2F)
        primary -> Color(0xFF4F7942)
        else -> Color.White
    }
    val foreground = when {
        destructive -> Color.White
        primary -> Color.White
        accent -> Color(0xFF4F7942)
        else -> Color(0xFF303033)
    }
    val borderColor = if (accent) Color(0xFF4F7942) else Color(0xFFD9D5D0)
    val contentAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .height(46.dp)
            .background(background.copy(alpha = if (primary || destructive) contentAlpha else 1f), RoundedCornerShape(9.dp))
            .then(if (destructive || primary) Modifier else Modifier.border(1.dp, borderColor, RoundedCornerShape(9.dp)))
            .clickable(enabled = enabled && !isLoading, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = foreground,
                strokeWidth = 2.dp
            )
        } else {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = foreground
                )
            }
        }
        Text(
            text = text,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = foreground
        )
    }
}
