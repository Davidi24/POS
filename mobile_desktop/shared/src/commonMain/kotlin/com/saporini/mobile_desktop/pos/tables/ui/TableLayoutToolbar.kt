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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch

@Composable
fun TableLayoutToolbar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    editMode: Boolean,
    hasBackground: Boolean,
    planMoveMode: Boolean,
    selectedTableLabel: String?,
    onEditModeChanged: (Boolean) -> Unit,
    onAddTable: (TableShape, Int) -> Unit,
    onRotateSelectedTable: (Float) -> Unit,
    onScaleSelectedTable: (Float) -> Unit,
    onDeleteSelectedTable: () -> Unit,
    onBackgroundSelected: (ByteArray) -> Unit,
    onRemoveBackground: () -> Unit,
    onPlanMoveModeChanged: (Boolean) -> Unit,
    onScalePlan: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddTableDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) scope.launch { onBackgroundSelected(file.readBytes()) }
    }

    if (editMode) {
        Row(
            modifier = modifier
                .background(Color(0xFFF7F5F2), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFE1DCD8), RoundedCornerShape(12.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton("Done", primary = true) { onEditModeChanged(false) }
            ToolbarButton("Add table") { showAddTableDialog = true }
            if (selectedTableLabel == null) {
                UploadPlanIconButton(
                    contentDescription = if (hasBackground) "Change plan" else "Upload plan"
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
                        onScalePlan(-0.05f)
                    }
                    PlanAdjustButton(Icons.Filled.Add, "Increase plan size") {
                        onScalePlan(0.05f)
                    }
                    ToolbarButton(
                        text = "Remove plan",
                        destructive = true,
                        onClick = onRemoveBackground
                    )
                }
            }
            if (selectedTableLabel != null) {
                ToolbarButton("↶ 15°") { onRotateSelectedTable(-15f) }
                ToolbarButton("↷ 15°") { onRotateSelectedTable(15f) }
                ToolbarButton("− Size") { onScaleSelectedTable(-0.08f) }
                ToolbarButton("+ Size") { onScaleSelectedTable(0.08f) }
                ToolbarButton("Delete $selectedTableLabel", destructive = true) {
                    onDeleteSelectedTable()
                }
            }
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(searchQuery, onSearchQueryChanged)
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
}

@Composable
private fun UploadPlanIconButton(
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
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
        Icon(
            imageVector = Icons.Outlined.FileUpload,
            contentDescription = contentDescription,
            modifier = Modifier.size(23.dp),
            tint = Color(0xFF4B522A)
        )
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
            .size(46.dp)
            .background(Color(0xFF4B522A), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Edit layout",
            modifier = Modifier.size(20.dp),
            tint = Color.White
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChanged: (String) -> Unit) {
    Row(
        modifier = Modifier
            .width(235.dp)
            .height(46.dp)
            .border(1.dp, Color(0xFFD9D5D0), RoundedCornerShape(10.dp))
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, null, modifier = Modifier.size(20.dp), tint = Color(0xFF777777))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChanged,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(fontFamily = Inter(), fontSize = 14.sp, color = Color(0xFF242424)),
            decorationBox = { field ->
                if (value.isBlank()) Text("Search table", color = Color(0xFF929292), fontSize = 14.sp)
                field()
            }
        )
    }
}

@Composable
private fun AddTableDialog(
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
            .border(1.dp, if (selected) Color(0xFF4B522A) else Color(0xFFD9D5D0), RoundedCornerShape(10.dp))
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
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val background = when {
        destructive -> Color(0xFFFFEEEE)
        primary -> Color(0xFF4B522A)
        else -> Color.White
    }
    val foreground = when {
        destructive -> Color(0xFFB3261E)
        primary -> Color.White
        else -> Color(0xFF303033)
    }
    Row(
        modifier = Modifier
            .height(46.dp)
            .background(background, RoundedCornerShape(9.dp))
            .border(1.dp, if (destructive) Color(0xFFF0B7B3) else Color(0xFFD9D5D0), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = foreground
            )
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
