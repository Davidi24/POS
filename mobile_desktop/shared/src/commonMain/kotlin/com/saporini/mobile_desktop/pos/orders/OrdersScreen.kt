package com.saporini.mobile_desktop.pos.orders

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import org.jetbrains.compose.resources.painterResource

private val ActiveOlive = Color(0xFF4B522A)
private val Ink = Color(0xFF202124)
private val Muted = Color(0xFF6F716E)
private val Border = Color(0xFFE7E1DC)
private val ReadyGreen = Color(0xFF2F8F22)
private val ProgressOrange = Color(0xFFC05E12)
private val PaymentGold = Color(0xFFC47A00)

@Composable
fun OrdersScreen(
    modifier: Modifier = Modifier,
    onPaymentRequested: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("All") }
    var selectedScope by remember { mutableStateOf("All") }
    var selectedOrderId by remember { mutableStateOf<String?>(null) }

    val visibleOrders = orderSamples.filter { order ->
        (selectedStatus == "All" || order.status == selectedStatus) &&
            (searchQuery.isBlank() ||
                order.id.contains(searchQuery, ignoreCase = true) ||
                order.customer.contains(searchQuery, ignoreCase = true) ||
                order.table.contains(searchQuery, ignoreCase = true))
    }
    val selectedOrder = selectedOrderId?.let { id -> orderSamples.firstOrNull { it.id == id } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OrdersToolbar(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            selectedStatus = selectedStatus,
            onStatusSelected = { selectedStatus = it },
            selectedScope = selectedScope,
            onScopeSelected = { selectedScope = it }
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OrdersGrid(
                orders = visibleOrders,
                selectedOrderId = selectedOrder?.id,
                onSelectOrder = { selectedOrderId = it },
                onPaymentRequested = onPaymentRequested,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            selectedOrder?.let { order ->
                OrderDetailsPanel(
                    order = order,
                    onPaymentRequested = onPaymentRequested,
                    modifier = Modifier
                        .width(490.dp)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun OrdersToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedStatus: String,
    onStatusSelected: (String) -> Unit,
    selectedScope: String,
    onScopeSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OrderSearchField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.width(340.dp)
        )
        StatusFilterButton("All", selectedStatus == "All", null) { onStatusSelected("All") }
        StatusFilterButton("In Progress", selectedStatus == "In Progress", ProgressOrange) { onStatusSelected("In Progress") }
        StatusFilterButton("Ready", selectedStatus == "Ready", ReadyGreen) { onStatusSelected("Ready") }
        StatusFilterButton("Waiting for Payment", selectedStatus == "Waiting for Payment", PaymentGold) {
            onStatusSelected("Waiting for Payment")
        }
        Spacer(Modifier.weight(1f))
        ScopeToggle(selectedScope = selectedScope, onScopeSelected = onScopeSelected)
        SortButton()
        CreateOrderButton()
    }
}

@Composable
private fun OrderSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Ink
        )
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 0.sp,
                color = Ink
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Search order # / table / name",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            letterSpacing = 0.sp,
                            color = Color(0xFF929292)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun StatusFilterButton(
    text: String,
    selected: Boolean,
    dotColor: Color?,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(44.dp)
            .width(
                when (text) {
                    "Waiting for Payment" -> 174.dp
                    "In Progress" -> 124.dp
                    "Ready" -> 92.dp
                    else -> 62.dp
                }
            )
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ActiveOlive else Color.White)
            .border(1.dp, Border, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) Color.White else Ink
        )
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ScopeToggle(
    selectedScope: String,
    onScopeSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf("Mine", "All").forEach { scope ->
            val selected = scope == selectedScope
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) ActiveOlive else Color.Transparent)
                    .clickable { onScopeSelected(scope) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = scope,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    color = if (selected) Color.White else Ink
                )
            }
        }
    }
}

@Composable
private fun SortButton() {
    Row(
        modifier = Modifier
            .height(44.dp)
            .width(108.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .clickable {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ink)
        Spacer(Modifier.width(8.dp))
        Text("Sort", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ink)
    }
}

@Composable
private fun CreateOrderButton() {
    TextButton(
        onClick = {},
        modifier = Modifier
            .height(44.dp)
            .width(204.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ActiveOlive),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(19.dp), tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Create New Order",
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.sp
        )
    }
}

@Composable
private fun OrdersGrid(
    orders: List<OrderSample>,
    selectedOrderId: String?,
    onSelectOrder: (String) -> Unit,
    onPaymentRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val columns = if (maxWidth < 620.dp) 1 else if (maxWidth < 920.dp) 2 else 3
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            orders.chunked(columns).forEach { rowOrders ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowOrders.forEach { order ->
                        OrderCard(
                            order = order,
                            selected = order.id == selectedOrderId,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectOrder(order.id) },
                            onPaymentRequested = onPaymentRequested
                        )
                    }
                    repeat(columns - rowOrders.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(
    order: OrderSample,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPaymentRequested: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ActiveOlive else Border,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = order.id,
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = 0.sp,
                color = Ink
            )
            TypeChip(order.type)
            Spacer(Modifier.width(8.dp))
            Text(order.time, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Ink)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TableBadge(order.table, order.type)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(order.customer, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
                Text(order.guestText, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Muted)
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(order.status)
            Spacer(Modifier.width(8.dp))
            Text(
                text = order.status,
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = statusColor(order.status)
            )
            ProgressPills(order.readyCount, order.itemCount, order.status)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${order.readyCount}/${order.itemCount} ready",
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Ink
            )
        }

        order.items.take(3).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.qty.toString(), modifier = Modifier.width(18.dp), fontFamily = Inter(), fontSize = 12.sp, color = Ink)
                Text(
                    text = item.name,
                    modifier = Modifier.weight(1f),
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = Ink,
                    maxLines = 1,
                    softWrap = false
                )
                Text(item.price, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Ink)
            }
        }
        if (order.items.size > 3) {
            Text(
                text = "+ ${order.items.size - 3} more item",
                modifier = Modifier.padding(start = 18.dp),
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Muted
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total", modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(order.total, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardActionButton("See Details", false, Modifier.weight(1f))
            CardActionButton("Pay Bills", order.canPay, Modifier.weight(1f), onClick = onPaymentRequested)
        }
    }
}

@Composable
private fun OrderDetailsPanel(
    order: OrderSample,
    onPaymentRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Order ${order.id}",
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                letterSpacing = 0.sp,
                color = Ink
            )
            StatusDot(order.status)
            Spacer(Modifier.width(8.dp))
            Text(order.status, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = statusColor(order.status))
            IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(20.dp), tint = Ink)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeChip(order.type)
            Spacer(Modifier.width(8.dp))
            TableBadge(order.table, order.type, compact = true)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${order.customer}  •  ${order.guestText}  •  Server: David K.  •  ${order.time}",
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Ink,
                maxLines = 1,
                softWrap = false
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailActionButton("Add Item", false, Modifier.weight(1f))
            DetailActionButton("Send 2 New Items", true, Modifier.weight(1.25f))
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Border, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, modifier = Modifier.size(22.dp), tint = Ink)
            }
        }

        OrderItemsBox(order)
        AddNoteBox()
        OrderSummaryBox(order)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ActiveOlive)
                .clickable(onClick = onPaymentRequested),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Checkout & Collect Payment",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.White,
                letterSpacing = 0.sp
            )
        }
    }
}

@Composable
private fun OrderItemsBox(order: OrderSample) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Items", modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
            Text("${order.items.size} items", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Muted)
        }
        order.items.forEach { item ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
            OrderDetailItemRow(item)
        }
    }
}

@Composable
private fun OrderDetailItemRow(item: OrderItemSample) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .width(54.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Border, RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(item.qty.toString(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ink)
        }
        Spacer(Modifier.width(12.dp))
        Image(
            painter = painterResource(Res.drawable.auth_login_img),
            contentDescription = item.name,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink, maxLines = 1, softWrap = false)
            Text(item.note, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Muted, maxLines = 1, softWrap = false)
        }
        PreparationChip(item.status)
        Spacer(Modifier.width(12.dp))
        Text(item.price, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
        IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ink)
        }
    }
}

@Composable
private fun AddNoteBox() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("+  Add Note", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
        Text("Add special request or note...", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun OrderSummaryBox(order: OrderSample) {
    val subtotal = order.items.sumOf { it.amount * it.qty }
    val tax = subtotal * 0.085
    val service = subtotal * 0.10
    val total = subtotal + tax + service

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text("Order Summary", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
        SummaryRow("Subtotal", money(subtotal))
        SummaryRow("Tax (8.5%)", money(tax))
        SummaryRow("Service Charge (10%)", money(service))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total", modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Ink)
            Text(money(total), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = ProgressOrange)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Ink)
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
    }
}

@Composable
private fun TypeChip(type: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (type == "Dine In") Color(0xFFEAF3DD) else Color(0xFFF4EBD8))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(type, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (type == "Dine In") ActiveOlive else Color(0xFF7B5A14))
    }
}

@Composable
private fun TableBadge(table: String, type: String, compact: Boolean = false) {
    Box(
        modifier = Modifier
            .size(if (compact) 30.dp else 34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (type == "Dine In") ActiveOlive else Color(0xFFF4E2AA)),
        contentAlignment = Alignment.Center
    ) {
        if (type == "Dine In") {
            Text(table, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = if (compact) 12.sp else 13.sp, color = Color.White)
        } else {
            Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(if (compact) 17.dp else 20.dp), tint = Color(0xFF7B5A14))
        }
    }
}

@Composable
private fun StatusDot(status: String) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(statusColor(status))
    )
}

@Composable
private fun ProgressPills(ready: Int, total: Int, status: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total.coerceAtMost(4)) { index ->
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (index < ready) statusColor(status) else Color(0xFFE4E8DF))
            )
        }
    }
}

@Composable
private fun CardActionButton(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) ActiveOlive else Color.White)
            .border(1.dp, if (active) ActiveOlive else Color(0xFFBEB9B2), RoundedCornerShape(7.dp))
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (active) Color.White else ActiveOlive)
    }
}

@Composable
private fun DetailActionButton(text: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) ActiveOlive else Color.White)
            .border(1.dp, if (active) ActiveOlive else Border, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (active) Color.White else ActiveOlive)
            Spacer(Modifier.width(8.dp))
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (active) Color.White else Ink)
        }
    }
}

@Composable
private fun PreparationChip(status: String) {
    val background = when (status) {
        "Preparing" -> Color(0xFFFFF0CB)
        "Ready" -> Color(0xFFE9F4DD)
        else -> Color(0xFFF1F1EF)
    }
    val color = when (status) {
        "Preparing" -> PaymentGold
        "Ready" -> ReadyGreen
        else -> Color(0xFF4F4F4F)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(status, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = color)
    }
}

private fun statusColor(status: String): Color = when (status) {
    "Ready" -> ReadyGreen
    "Waiting for Payment" -> PaymentGold
    else -> ProgressOrange
}

private fun money(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100
    val text = rounded.toString()
    return "$" + if (text.substringAfter('.', "").length == 1) "${text}0" else text
}

private data class OrderItemSample(
    val qty: Int,
    val name: String,
    val note: String,
    val price: String,
    val amount: Double,
    val status: String
)

private data class OrderSample(
    val id: String,
    val type: String,
    val table: String,
    val customer: String,
    val guestText: String,
    val status: String,
    val time: String,
    val readyCount: Int,
    val itemCount: Int,
    val total: String,
    val canPay: Boolean,
    val items: List<OrderItemSample>
)

private val orderSamples = listOf(
    OrderSample(
        id = "#ORD-1042",
        type = "Dine In",
        table = "A6",
        customer = "Laura Bianchi",
        guestText = "4 guests",
        status = "In Progress",
        time = "12:45 PM",
        readyCount = 2,
        itemCount = 4,
        total = "$78.50",
        canPay = false,
        items = listOf(
            OrderItemSample(1, "Margherita Pizza", "No onion", "$18.00", 18.00, "Preparing"),
            OrderItemSample(2, "Fettuccine Alfredo", "Extra parmesan", "$22.00", 11.00, "Ready"),
            OrderItemSample(1, "Insalata Caprese", "", "$12.00", 12.00, "Queued"),
            OrderItemSample(1, "Limonata", "No ice", "$6.50", 6.50, "Ready")
        )
    ),
    OrderSample("#ORD-1041", "Take Away", "BAG", "Marco Rossi", "3 items", "Ready", "12:30 PM", 3, 3, "$34.00", true, listOf(
        OrderItemSample(1, "Lasagna alla Bolognese", "", "$20.00", 20.00, "Ready"),
        OrderItemSample(1, "Garlic Bread", "", "$6.00", 6.00, "Ready"),
        OrderItemSample(1, "Tiramisu", "", "$8.00", 8.00, "Ready")
    )),
    OrderSample("#ORD-1040", "Dine In", "R3", "Giulia De Luca", "2 guests", "Ready", "12:10 PM", 2, 2, "$29.00", true, listOf(
        OrderItemSample(1, "Risotto ai Funghi", "", "$19.00", 19.00, "Ready"),
        OrderItemSample(1, "Tiramisu", "", "$8.00", 8.00, "Ready")
    )),
    OrderSample("#ORD-1039", "Take Away", "BAG", "Alessandro Conti", "3 items", "Waiting for Payment", "11:55 AM", 1, 3, "$46.50", true, listOf(
        OrderItemSample(2, "Margherita Pizza", "", "$36.00", 18.00, "Ready"),
        OrderItemSample(1, "Cannoli", "", "$6.50", 6.50, "Queued"),
        OrderItemSample(1, "Aranciata", "", "$4.00", 4.00, "Queued")
    )),
    OrderSample("#ORD-1038", "Dine In", "A11", "Sofia Ricci", "3 guests", "In Progress", "11:30 AM", 1, 3, "$37.00", false, listOf(
        OrderItemSample(1, "Spaghetti Carbonara", "", "$20.00", 20.00, "Preparing"),
        OrderItemSample(1, "Caesar Salad", "", "$11.00", 11.00, "Queued"),
        OrderItemSample(1, "Lemon Sorbet", "", "$6.00", 6.00, "Queued")
    )),
    OrderSample("#ORD-1037", "Take Away", "BAG", "Luca Moretti", "2 items", "Ready", "11:15 AM", 2, 2, "$27.00", true, listOf(
        OrderItemSample(1, "Pizza Diavola", "", "$19.00", 19.00, "Ready"),
        OrderItemSample(1, "Tiramisu", "", "$8.00", 8.00, "Ready")
    )),
    OrderSample("#ORD-1036", "Dine In", "R5", "Elena Verdi", "5 guests", "Waiting for Payment", "10:50 AM", 2, 4, "$58.00", true, listOf(
        OrderItemSample(2, "Lasagna alla Bolognese", "", "$40.00", 20.00, "Ready"),
        OrderItemSample(1, "Insalata Mista", "", "$8.50", 8.50, "Ready"),
        OrderItemSample(1, "Panna Cotta", "", "$5.50", 5.50, "Queued"),
        OrderItemSample(1, "Espresso", "", "$3.00", 3.00, "Queued")
    )),
    OrderSample("#ORD-1035", "Take Away", "BAG", "Francesco Romano", "3 items", "Ready", "10:35 AM", 3, 3, "$31.00", true, listOf(
        OrderItemSample(1, "Chicken Parmigiana", "", "$22.00", 22.00, "Ready"),
        OrderItemSample(1, "Garlic Bread", "", "$6.00", 6.00, "Ready"),
        OrderItemSample(1, "Coke Zero", "", "$3.00", 3.00, "Ready")
    ))
)
