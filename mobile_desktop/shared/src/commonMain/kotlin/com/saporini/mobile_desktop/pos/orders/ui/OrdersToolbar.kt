package com.saporini.mobile_desktop.pos.orders.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderFulfillmentStatus

@Composable
internal fun OrdersToolbar(
    query: String, onQueryChange: (String) -> Unit,
    progress: OrderFulfillmentStatus?, onProgressChange: (OrderFulfillmentStatus?) -> Unit,
    mineOnly: Boolean, onMineChange: (Boolean) -> Unit,
    history: Boolean, onHistoryChange: (Boolean) -> Unit
) {
    val phone = isPhoneWindow()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val barWidth = maxOf(maxWidth, 950.dp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Row(Modifier.width(barWidth), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Orders", fontFamily = Inter(), fontWeight = FontWeight.Bold,
                    fontSize = if (phone) 18.sp else 20.sp, color = Color(0xFF232422))
                Spacer(Modifier.weight(1f))
                HeaderDropdown(if (history) "History" else "Open orders", Icons.Outlined.ReceiptLong, Modifier.width(166.dp)) { close ->
                    DropdownMenuItem(
                        text = { HeaderText(if (history) "Open orders" else "History") },
                        onClick = { onHistoryChange(!history); close() }
                    )
                }
                Surface(
                    modifier = Modifier.width(156.dp).height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE8E5E1))
                ) {
                    Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(false to "All", true to "Mine").forEach { (mine, label) ->
                            val selected = mineOnly == mine
                            Surface(
                                onClick = { onMineChange(mine) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) Color(0xFF4F7942) else Color.White
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    HeaderText(label, color = if (selected) Color.White else Color(0xFF222426))
                                }
                            }
                        }
                    }
                }
                HeaderDropdown(progress?.label() ?: "Order progress", Icons.Outlined.Restaurant, Modifier.width(190.dp), progress?.let(::progressColor)) { close ->
                    DropdownMenuItem(text = { HeaderText("All progress") }, onClick = { onProgressChange(null); close() })
                    OrderFulfillmentStatus.entries.forEach { status ->
                        DropdownMenuItem(text = { HeaderText(status.label()) }, leadingIcon = { ProgressDot(progressColor(status)) }, onClick = { onProgressChange(status); close() })
                    }
                }
                SearchField(query, onQueryChange, Modifier.width(280.dp), placeholder = "Search orders")
            }
        }
    }
}

@Composable
private fun HeaderDropdown(label: String, icon: ImageVector, modifier: Modifier, tint: Color? = null, choices: @Composable ColumnScope.(() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE8E5E1))) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tint != null) ProgressDot(tint) else Icon(icon, null, Modifier.size(20.dp), tint = Color(0xFF222426))
                HeaderText(label, Modifier.weight(1f), tint ?: Color(0xFF222426))
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = Color(0xFF222426))
            }
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.background(Color.White)) { choices { expanded = false } }
    }
}

@Composable private fun HeaderText(text: String, modifier: Modifier = Modifier, color: Color = Color(0xFF222426)) {
    Text(text, modifier, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = color, maxLines = 1)
}
@Composable private fun ProgressDot(color: Color) { Box(Modifier.size(9.dp).background(color, RoundedCornerShape(50))) }
private fun progressColor(status: OrderFulfillmentStatus): Color = when (status) {
    OrderFulfillmentStatus.PENDING -> Color(0xFF747572)
    OrderFulfillmentStatus.IN_PREPARATION, OrderFulfillmentStatus.PARTIALLY_FULFILLED -> Color(0xFFAA5E10)
    OrderFulfillmentStatus.READY -> Color(0xFF24748A)
    OrderFulfillmentStatus.FULFILLED -> Color(0xFF4F7942)
}
