package com.saporini.mobile_desktop.pos.tables.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SyncAlt
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog

private val TableGreen = Color(0xFF4F7942)
private val TableDanger = Color(0xFFB13A2F)
private val TableInk = Color(0xFF242424)
private val TableMuted = Color(0xFF71736E)
private val TableBorder = Color(0xFFE1DCD8)

/**
 * Everyday actions for a table during service (seat, order, move, join/separate,
 * availability). Table-plan editing (add/resize/rotate tables, the background
 * image) lives in TableLayoutToolbar's edit mode instead — this modal is about
 * running service on the floor plan as it already stands.
 */
@Composable
fun BoxScope.TableDetailsModal(
    tables: List<FloorPlanTable>,
    primaryTableId: String?,
    onDismiss: () -> Unit,
    onSeatGuests: (Int) -> Unit,
    onUpdateGuestCount: (Int) -> Unit,
    onStartOrder: () -> Unit,
    onViewOrder: () -> Unit,
    onAddItems: () -> Unit,
    onMergeTables: () -> Unit,
    onSeparateTables: (String) -> Unit,
    onStartMoveGuests: () -> Unit,
    onSetAvailability: (Boolean) -> Unit,
    onGoToPayment: () -> Unit,
    isSaving: Boolean = false
) {
    val table = tables.toServiceSummaryTable()
    val tableTitle = tables.joinToString(separator = "-") { it.label }
    val covers = tables.mapNotNull { it.guestCount }.takeIf { it.isNotEmpty() }?.sum()
        ?: tables.sumOf { it.seatCount.coerceAtLeast(1) }
    val orderLabels = tables.mapNotNull { it.orderLabel }.distinct()
    val hasOrder = orderLabels.isNotEmpty()
    val isMergedGroup = tables.size > 1
    var mode by remember(tables) { mutableStateOf(DetailMode.MENU) }
    var guestCount by remember(tables) {
        mutableStateOf(tables.mapNotNull { it.guestCount }.maxOrNull() ?: 1)
    }
    var showAvailabilityConfirm by remember { mutableStateOf(false) }
    var showSeparateConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .width(430.dp)
            .shadow(18.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Table $tableTitle",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    color = TableInk
                )
                Spacer(Modifier.height(10.dp))
                StatusPill(text = table.statusLabel(), color = table.statusColor())
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Close, "Close", modifier = Modifier.size(26.dp), tint = TableInk)
            }
        }

        Spacer(Modifier.height(22.dp))

        when (mode) {
            DetailMode.MENU -> {
                if (table.state == TableVisualState.Occupied || table.state == TableVisualState.BillPending) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, TableBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DetailRow(icon = Icons.Filled.Groups, label = "Covers", value = "$covers Guests")
                        DetailRow(icon = Icons.Filled.AddCircle, label = "Seated since", value = table.seatedSince())
                        if (hasOrder) {
                            DetailRow(icon = Icons.Filled.Assignment, label = "Current order", value = orderLabels.joinToString(", "))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                ActionList(
                    table = table,
                    hasOrder = hasOrder,
                    isMergedGroup = isMergedGroup,
                    isSaving = isSaving,
                    onSeatCustomers = { mode = DetailMode.GUEST_COUNT },
                    onSeatReservation = { mode = DetailMode.GUEST_COUNT },
                    onChangeGuestCount = { mode = DetailMode.CHANGE_GUEST_COUNT },
                    onStartOrder = onStartOrder,
                    onViewOrder = onViewOrder,
                    onAddItems = onAddItems,
                    onMoveGuests = onStartMoveGuests,
                    onJoinTables = onMergeTables,
                    onSeparateTables = { showSeparateConfirm = true },
                    onGoToPayment = onGoToPayment,
                    onMarkUnavailable = { showAvailabilityConfirm = true },
                    onMarkAvailable = { onSetAvailability(true) }
                )
            }
            DetailMode.GUEST_COUNT -> {
                GuestCountStep(
                    label = "Number of guests",
                    guestCount = guestCount,
                    onGuestCountChanged = { guestCount = it },
                    confirmLabel = "Seat guests",
                    isSaving = isSaving,
                    onBack = { mode = DetailMode.MENU },
                    onConfirm = { onSeatGuests(guestCount) }
                )
            }
            DetailMode.CHANGE_GUEST_COUNT -> {
                GuestCountStep(
                    label = "Update guest count",
                    guestCount = guestCount,
                    onGuestCountChanged = { guestCount = it },
                    confirmLabel = "Save",
                    isSaving = isSaving,
                    onBack = { mode = DetailMode.MENU },
                    onConfirm = { onUpdateGuestCount(guestCount) }
                )
            }
        }
    }

    if (showAvailabilityConfirm) {
        MenuNestedDialog(
            onDismissRequest = { showAvailabilityConfirm = false },
            title = { Text("Mark Table $tableTitle unavailable?", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text("It won't be selectable for seating or reservations until you make it available again.", fontFamily = Inter()) },
            confirmButton = {
                Button(
                    onClick = { showAvailabilityConfirm = false; onSetAvailability(false) },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = TableDanger)
                ) { Text("Mark unavailable", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showAvailabilityConfirm = false }) {
                    Text("Cancel", fontFamily = Inter(), color = TableMuted)
                }
            }
        )
    }

    if (showSeparateConfirm) {
        MenuNestedDialog(
            onDismissRequest = { showSeparateConfirm = false },
            title = { Text("Separate Table $tableTitle?", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text("Splits this group back into individual tables. The party and its order stay on the table you started with.", fontFamily = Inter()) },
            confirmButton = {
                Button(
                    onClick = {
                        showSeparateConfirm = false
                        primaryTableId?.let(onSeparateTables)
                    },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = TableGreen)
                ) { Text("Separate", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showSeparateConfirm = false }) {
                    Text("Cancel", fontFamily = Inter(), color = TableMuted)
                }
            }
        )
    }
}

private enum class DetailMode { MENU, GUEST_COUNT, CHANGE_GUEST_COUNT }

@Composable
private fun GuestCountStep(
    label: String,
    guestCount: Int,
    onGuestCountChanged: (Int) -> Unit,
    confirmLabel: String,
    isSaving: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF303033))
    Spacer(Modifier.height(10.dp))
    GuestCountPicker(guestCount = guestCount, onGuestCountChanged = onGuestCountChanged)
    Spacer(Modifier.height(22.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DialogActionButton(
            text = "Back",
            icon = null,
            modifier = Modifier.weight(1f).height(54.dp),
            onClick = onBack
        )
        DialogActionButton(
            text = confirmLabel,
            icon = Icons.Filled.CheckCircle,
            modifier = Modifier.weight(1f).height(54.dp),
            dark = true,
            isLoading = isSaving,
            onClick = onConfirm
        )
    }
}

@Composable
private fun ActionList(
    table: FloorPlanTable,
    hasOrder: Boolean,
    isMergedGroup: Boolean,
    isSaving: Boolean,
    onSeatCustomers: () -> Unit,
    onSeatReservation: () -> Unit,
    onChangeGuestCount: () -> Unit,
    onStartOrder: () -> Unit,
    onViewOrder: () -> Unit,
    onAddItems: () -> Unit,
    onMoveGuests: () -> Unit,
    onJoinTables: () -> Unit,
    onSeparateTables: () -> Unit,
    onGoToPayment: () -> Unit,
    onMarkUnavailable: () -> Unit,
    onMarkAvailable: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (table.state) {
            TableVisualState.Free -> {
                DialogActionButton(
                    text = "Seat customers", icon = Icons.Filled.Groups,
                    modifier = Modifier.fillMaxWidth().height(50.dp), dark = true,
                    onClick = onSeatCustomers
                )
                if (isMergedGroup) {
                    DialogActionButton(
                        text = "Separate tables", icon = Icons.Filled.CallSplit,
                        modifier = Modifier.fillMaxWidth().height(50.dp), isLoading = isSaving,
                        onClick = onSeparateTables
                    )
                } else {
                    DialogActionButton(
                        text = "Join tables", icon = Icons.Filled.MergeType,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        onClick = onJoinTables
                    )
                }
                DialogActionButton(
                    text = "Mark unavailable", icon = Icons.Filled.Block,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = onMarkUnavailable
                )
            }
            TableVisualState.Reserved -> {
                DialogActionButton(
                    text = "Seat reservation", icon = Icons.Filled.Groups,
                    modifier = Modifier.fillMaxWidth().height(50.dp), dark = true,
                    onClick = onSeatReservation
                )
            }
            TableVisualState.Occupied, TableVisualState.BillPending -> {
                DialogActionButton(
                    text = if (hasOrder) "View order" else "Start order",
                    icon = Icons.Filled.List,
                    modifier = Modifier.fillMaxWidth().height(50.dp), dark = true,
                    onClick = if (hasOrder) onViewOrder else onStartOrder
                )
                if (hasOrder) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogActionButton(
                            text = "Add items", icon = Icons.Filled.AddCircle,
                            modifier = Modifier.weight(1f).height(50.dp),
                            onClick = onAddItems
                        )
                        DialogActionButton(
                            text = "Payment", icon = Icons.Filled.Payments,
                            modifier = Modifier.weight(1f).height(50.dp),
                            onClick = onGoToPayment
                        )
                    }
                }
                DialogActionButton(
                    text = "Change guest count", icon = Icons.Filled.Groups,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = onChangeGuestCount
                )
                DialogActionButton(
                    text = "Move guests", icon = Icons.Filled.SyncAlt,
                    modifier = Modifier.fillMaxWidth().height(50.dp), isLoading = isSaving,
                    onClick = onMoveGuests
                )
                if (isMergedGroup) {
                    DialogActionButton(
                        text = "Separate tables", icon = Icons.Filled.CallSplit,
                        modifier = Modifier.fillMaxWidth().height(50.dp), isLoading = isSaving,
                        onClick = onSeparateTables
                    )
                } else {
                    DialogActionButton(
                        text = "Join tables", icon = Icons.Filled.MergeType,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        onClick = onJoinTables
                    )
                }
            }
            TableVisualState.Unavailable -> {
                DialogActionButton(
                    text = "Mark available", icon = Icons.Filled.CheckCircle,
                    modifier = Modifier.fillMaxWidth().height(50.dp), dark = true, isLoading = isSaving,
                    onClick = onMarkAvailable
                )
            }
        }
    }
}

@Composable
private fun GuestCountPicker(
    guestCount: Int,
    onGuestCountChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GuestCountButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Remove guest",
            onClick = { onGuestCountChanged((guestCount - 1).coerceAtLeast(1)) }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = TableBorder, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$guestCount ${if (guestCount == 1) "guest" else "guests"}",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = TableInk
            )
        }

        GuestCountButton(
            icon = Icons.Filled.Add,
            contentDescription = "Add guest",
            onClick = { onGuestCountChanged(guestCount + 1) }
        )
    }
}

@Composable
private fun GuestCountButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F3F0))
            .border(width = 1.dp, color = TableBorder, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp), tint = TableInk)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color(0xFF303033)
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF696969))
        Spacer(Modifier.width(12.dp))
        Text(
            text = label, modifier = Modifier.weight(1f),
            fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color(0xFF3E3E42)
        )
        Text(text = value, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 15.sp, color = valueColor)
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    isLoading: Boolean = false,
    onClick: () -> Unit = {}
) {
    val foreground = if (dark) Color.White else Color(0xFF2E2E31)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (dark) TableGreen else Color.White)
            .border(width = if (dark) 0.dp else 1.dp, color = TableBorder, shape = RoundedCornerShape(8.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = foreground, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = foreground)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            maxLines = 1, softWrap = false, color = foreground
        )
    }
}

private fun FloorPlanTable.statusLabel(): String =
    statusText ?: when (state) {
        TableVisualState.Free -> "Free"
        TableVisualState.Occupied -> if (orderLabel == null) "Seated" else "In Progress"
        TableVisualState.Reserved -> "Reserved"
        TableVisualState.BillPending -> "Bill Pending"
        TableVisualState.Unavailable -> "Unavailable"
    }

private fun FloorPlanTable.statusColor(): Color = tableStatusColor(state)

private fun List<FloorPlanTable>.toServiceSummaryTable(): FloorPlanTable {
    val first = first()
    val activeOrders = mapNotNull { table ->
        table.orderLabel?.let { orderLabel -> table.orderId to orderLabel }
    }.distinctBy { it.second }
    val firstOrder = activeOrders.firstOrNull()
    val orderLabelSummary = activeOrders.joinToString(", ") { it.second }.takeIf { it.isNotBlank() }
    val totalGuests = mapNotNull { it.guestCount }.takeIf { it.isNotEmpty() }?.sum()
    val summaryState = when {
        any { it.state == TableVisualState.BillPending } -> TableVisualState.BillPending
        activeOrders.isNotEmpty() || any { it.state == TableVisualState.Occupied } -> TableVisualState.Occupied
        any { it.state == TableVisualState.Reserved } -> TableVisualState.Reserved
        all { it.state == TableVisualState.Unavailable } -> TableVisualState.Unavailable
        else -> TableVisualState.Free
    }

    return first.copy(
        state = summaryState,
        orderId = firstOrder?.first,
        orderLabel = orderLabelSummary,
        statusText = when {
            activeOrders.isNotEmpty() -> first.statusText ?: "In Progress"
            summaryState == TableVisualState.Occupied -> "Seated"
            else -> first.statusText
        },
        guestCount = totalGuests,
        seatedAt = mapNotNull { it.seatedAt }.minOrNull()
    )
}

private fun FloorPlanTable.seatedSince(): String =
    if (state == TableVisualState.Free) "--" else seatedAt?.toSeatedTimeLabel() ?: "Just now"

private fun String.toSeatedTimeLabel(): String {
    val time = substringAfter('T', missingDelimiterValue = "").take(5)
    return if (time.length == 5) "$time UTC" else this
}
