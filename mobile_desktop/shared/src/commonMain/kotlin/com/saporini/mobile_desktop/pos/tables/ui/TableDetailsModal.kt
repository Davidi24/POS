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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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

@Composable
fun BoxScope.TableDetailsModal(
    tables: List<FloorPlanTable>,
    onDismiss: () -> Unit,
    onSeatGuests: (Int) -> Unit,
    onStartOrder: () -> Unit,
    onAddItems: () -> Unit,
    onMergeTables: () -> Unit
) {
    val table = tables.first()
    val tableTitle = tables.joinToString(separator = "-") { it.label }
    val covers = tables.mapNotNull { it.guestCount }.maxOrNull()
        ?: tables.sumOf { it.seatCount.coerceAtLeast(1) }
    val hasOrder = tables.any { it.orderLabel != null }
    val isSeated = !hasOrder && tables.any {
        it.state == TableVisualState.Occupied
    }
    var guestCount by remember(tables) {
        mutableStateOf(
            tables.mapNotNull { it.guestCount }.maxOrNull()
                ?: 1
        )
    }

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
                    letterSpacing = 0.sp,
                    color = Color(0xFF242424)
                )

                Spacer(Modifier.height(10.dp))

                StatusPill(
                    text = table.statusLabel(),
                    color = table.statusColor()
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(26.dp),
                    tint = Color(0xFF242424)
                )
            }
        }

        if (hasOrder || isSeated) {
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = Color(0xFFE7E1DC),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(
                        horizontal = 16.dp,
                        vertical = 14.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                DetailRow(
                    icon = Icons.Filled.Settings,
                    label = "Status",
                    value = table.statusLabel(),
                    valueColor = table.statusColor(),
                    showDot = true
                )

                DetailRow(
                    icon = Icons.Filled.LocationOn,
                    label = "Location",
                    value = "Main Salon"
                )

                DetailRow(
                    icon = Icons.Filled.Groups,
                    label = "Covers",
                    value = "$covers Guests"
                )

                DetailRow(
                    icon = Icons.Filled.AccessTime,
                    label = "Seated Since",
                    value = table.seatedSince()
                )

                if (hasOrder) {
                    DetailRow(
                        icon = Icons.Filled.Assignment,
                        label = "Current Order",
                        value = table.orderLabel.orEmpty()
                    )

                    DetailRow(
                        icon = Icons.Filled.Payments,
                        label = "Order Total",
                        value = table.orderTotal()
                    )

                    DetailRow(
                        icon = Icons.Filled.Person,
                        label = "Server",
                        value = "David K."
                    )

                    DetailRow(
                        icon = Icons.Filled.QrCode2,
                        label = "QR Session",
                        value = "Active",
                        valueColor = Color(0xFF319B48),
                        badge = true
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            if (hasOrder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DialogActionButton(
                        text = "View Order",
                        icon = Icons.Filled.List,
                        modifier = Modifier.weight(1f)
                    )

                    DialogActionButton(
                        text = "Add Items",
                        icon = Icons.Filled.AddCircle,
                        modifier = Modifier.weight(1f),
                        onClick = onAddItems
                    )

                    MoreActionsButton(
                        text = "More Actions",
                        icon = Icons.Filled.MoreHoriz,
                        modifier = Modifier.weight(1.12f),
                        onMergeTables = onMergeTables
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DialogActionButton(
                        text = "Start order",
                        icon = Icons.Filled.AddCircle,
                        modifier = Modifier.weight(1f),
                        dark = true,
                        onClick = onStartOrder
                    )

                    DialogActionButton(
                        text = "Merge tables",
                        icon = Icons.Filled.Settings,
                        modifier = Modifier.weight(1f),
                        onClick = onMergeTables
                    )
                }
            }
        } else {
            Spacer(Modifier.height(26.dp))

            Text(
                text = "Number of guests",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.sp,
                color = Color(0xFF303033)
            )

            Spacer(Modifier.height(10.dp))

            GuestCountPicker(
                guestCount = guestCount,
                onGuestCountChanged = { guestCount = it }
            )

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DialogActionButton(
                    text = "Seat guests",
                    icon = Icons.Filled.Groups,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    dark = true,
                    onClick = { onSeatGuests(guestCount) }
                )

                DialogActionButton(
                    text = "Merge tables",
                    icon = Icons.Filled.Settings,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    onClick = onMergeTables
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
            onClick = {
                onGuestCountChanged((guestCount - 1).coerceAtLeast(1))
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = Color(0xFFE1DCD8),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$guestCount ${if (guestCount == 1) "guest" else "guests"}",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = 0.sp,
                color = Color(0xFF242424)
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
            .border(
                width = 1.dp,
                color = Color(0xFFE1DCD8),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF30381E)
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.sp,
            color = Color.White
        )
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color(0xFF303033),
    showDot: Boolean = false,
    badge: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Color(0xFF696969)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontFamily = Inter(),
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            letterSpacing = 0.sp,
            color = Color(0xFF3E3E42)
        )

        if (badge) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF7BCB8B),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(
                        horizontal = 16.dp,
                        vertical = 6.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    color = valueColor
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showDot) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(50))
                            .background(valueColor)
                    )

                    Spacer(Modifier.width(9.dp))
                }

                Text(
                    text = value,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    letterSpacing = 0.sp,
                    color = valueColor
                )
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (dark) Color(0xFF30381E) else Color.White
            )
            .border(
                width = if (dark) 0.dp else 1.dp,
                color = Color(0xFFE1DCD8),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (dark) {
                Color.White
            } else {
                Color(0xFF2E2E31)
            }
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = text,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.sp,
            maxLines = 1,
            softWrap = false,
            color = if (dark) {
                Color.White
            } else {
                Color(0xFF2E2E31)
            }
        )
    }
}

@Composable
private fun MoreActionsButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onMergeTables: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        DialogActionButton(
            text = text,
            icon = icon,
            dark = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White),
            shape = RoundedCornerShape(10.dp),
            containerColor = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Merge Tables",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.sp,
                        color = Color(0xFF2E2E31)
                    )
                },
                onClick = {
                    expanded = false
                    onMergeTables()
                }
            )
        }
    }
}

private fun FloorPlanTable.statusLabel(): String =
    statusText ?: when (state) {
        TableVisualState.Free -> "Free"
        TableVisualState.Occupied ->
            if (orderLabel == null) "Seated" else "In Progress"
        TableVisualState.Reserved -> "Reserved"
        TableVisualState.BillPending -> "Bill Pending"
        TableVisualState.Unavailable -> "Unavailable"
    }

private fun FloorPlanTable.statusColor(): Color =
    when (state) {
        TableVisualState.Free -> Color(0xFF4B522A)
        TableVisualState.Occupied -> Color(0xFFB86A0B)
        TableVisualState.Reserved -> Color(0xFFC47A18)
        TableVisualState.BillPending -> Color(0xFF2F6FB1)
        TableVisualState.Unavailable -> Color(0xFF777777)
    }

private fun FloorPlanTable.seatedSince(): String =
    if (state == TableVisualState.Free) {
        "--"
    } else {
        seatedAt?.toSeatedTimeLabel() ?: "Just now"
    }

private fun String.toSeatedTimeLabel(): String {
    val time = substringAfter('T', missingDelimiterValue = "")
        .take(5)
    return if (time.length == 5) "$time UTC" else this
}

private fun FloorPlanTable.orderTotal(): String =
    if (state == TableVisualState.Free) {
        "\$0.00"
    } else {
        "\$72.30"
    }
