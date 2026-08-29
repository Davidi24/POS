package com.saporini.mobile_desktop.pos.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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

private val ActiveOlive = Color(0xFF4B522A)
private val Ink = Color(0xFF202124)
private val Muted = Color(0xFF6E716C)
private val Border = Color(0xFFE7E1DC)
private val RushRed = Color(0xFFD71920)
private val NormalGreen = Color(0xFF3E7B32)
private val FiredRed = Color(0xFFD71920)
private val ProgressOrange = Color(0xFFE88228)
private val ReadyGreen = Color(0xFF5B8A4C)

@Composable
fun KitchenStatusScreen(
    modifier: Modifier = Modifier
) {
    var selectedStation by remember { mutableStateOf("All Stations") }
    var selectedPriority by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredOrders = kitchenOrders.filter { order ->
        (selectedStation == "All Stations" || order.station == selectedStation) &&
            (selectedPriority == "All" || order.priority == selectedPriority) &&
            (searchQuery.isBlank() ||
                order.id.contains(searchQuery, ignoreCase = true) ||
                order.table.contains(searchQuery, ignoreCase = true) ||
                order.items.any { it.contains(searchQuery, ignoreCase = true) })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        KitchenHeader()
        KitchenFilters(
            selectedStation = selectedStation,
            onStationSelected = { selectedStation = it },
            selectedPriority = selectedPriority,
            onPrioritySelected = { selectedPriority = it },
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it }
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            KitchenLane(
                title = "Fired",
                icon = Icons.Outlined.LocalFireDepartment,
                color = FiredRed,
                orders = filteredOrders.filter { it.stage == KitchenStage.FIRED },
                modifier = Modifier.weight(1f)
            )
            KitchenLane(
                title = "In Progress",
                icon = Icons.Filled.Restaurant,
                color = ProgressOrange,
                orders = filteredOrders.filter { it.stage == KitchenStage.IN_PROGRESS },
                modifier = Modifier.weight(1f)
            )
            KitchenLane(
                title = "Ready",
                icon = Icons.Filled.CheckCircle,
                color = ReadyGreen,
                orders = filteredOrders.filter { it.stage == KitchenStage.READY },
                modifier = Modifier.weight(1.1f)
            )
        }
    }
}

@Composable
private fun KitchenHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Kitchen Status",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 29.sp,
                letterSpacing = 0.sp,
                color = ActiveOlive
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Live overview of all orders in the kitchen.",
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                letterSpacing = 0.sp,
                color = Muted
            )
        }
        HeaderButton("All Stations", Icons.Outlined.Tune)
        Spacer(Modifier.width(14.dp))
        HeaderButton("Pause Alerts", Icons.Outlined.NotificationsOff)
    }
}

@Composable
private fun HeaderButton(
    text: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = Ink)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
        if (text == "All Stations") {
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ink)
        }
    }
}

@Composable
private fun KitchenFilters(
    selectedStation: String,
    onStationSelected: (String) -> Unit,
    selectedPriority: String,
    onPrioritySelected: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("All Stations", "Hot Kitchen", "Pizza", "Pasta", "Dessert", "Drinks").forEach { station ->
            StationButton(
                text = station,
                icon = stationIcon(station),
                selected = station == selectedStation,
                onClick = { onStationSelected(station) }
            )
        }
        Spacer(Modifier.weight(1f))
        PriorityButton("All", selectedPriority == "All", null) { onPrioritySelected("All") }
        PriorityButton("Normal", selectedPriority == "Normal", NormalGreen) { onPrioritySelected("Normal") }
        PriorityButton("Rush", selectedPriority == "Rush", RushRed) { onPrioritySelected("Rush") }
        KitchenSearchField(searchQuery, onSearchChange, Modifier.width(360.dp))
    }
}

@Composable
private fun StationButton(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(50.dp)
            .width(if (text == "All Stations") 184.dp else 156.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ActiveOlive else Color.White)
            .border(1.dp, Border, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) Color.White else Ink)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = if (selected) Color.White else Ink)
        Spacer(Modifier.width(10.dp))
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun PriorityButton(
    text: String,
    selected: Boolean,
    dotColor: Color?,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(50.dp)
            .width(if (text == "All") 100.dp else 142.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ActiveOlive else Color.White)
            .border(1.dp, Border, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) Color.White else Ink)
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun KitchenSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(22.dp), tint = Ink)
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
                            text = "Search orders...",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            letterSpacing = 0.sp,
                            color = Color(0xFF999999)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun KitchenLane(
    title: String,
    icon: ImageVector,
    color: Color,
    orders: List<KitchenOrder>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.045f))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(29.dp), tint = color)
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 23.sp,
                letterSpacing = 0.sp,
                color = Ink
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, color.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(orders.size.toString(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = color)
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            orders.forEach { order ->
                KitchenOrderCard(order = order, laneColor = color)
            }
        }
    }
}

@Composable
private fun KitchenOrderCard(
    order: KitchenOrder,
    laneColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, laneColor.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = order.id,
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = 0.sp,
                color = Ink
            )
            Text(
                text = "Table ${order.table}",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Ink
            )
            Spacer(Modifier.width(12.dp))
            TimePill(order.elapsed, laneColor)
            if (order.stage == KitchenStage.READY) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(30.dp), tint = ReadyGreen)
            }
        }

        PriorityRow(order.priority)

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                order.items.take(2).forEach { item -> KitchenItemText(item) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                order.items.drop(2).forEach { item -> KitchenItemText(item) }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(18.dp), tint = Muted)
            Spacer(Modifier.width(8.dp))
            Text("Covers: ${order.covers}", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Muted)
            Spacer(Modifier.width(28.dp))
            Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(18.dp), tint = Muted)
            Spacer(Modifier.width(8.dp))
            Text("Server: ${order.server}", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            order.note?.let { note ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFFF8EAD9))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(note, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF8B5C18))
                }
            }
        }
    }
}

@Composable
private fun TimePill(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = color)
    }
}

@Composable
private fun PriorityRow(priority: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (priority == "Rush") RushRed else NormalGreen)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = priority,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (priority == "Rush") RushRed else NormalGreen
        )
    }
}

@Composable
private fun KitchenItemText(item: String) {
    Text(
        text = "1 x  $item",
        fontFamily = Inter(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.sp,
        color = Ink,
        maxLines = 1,
        softWrap = false
    )
}

private fun stationIcon(station: String): ImageVector = when (station) {
    "Hot Kitchen" -> Icons.Outlined.LocalFireDepartment
    "Pizza" -> Icons.Outlined.LocalPizza
    "Pasta" -> Icons.Outlined.Restaurant
    "Dessert" -> Icons.Outlined.Cake
    "Drinks" -> Icons.Outlined.LocalDrink
    else -> Icons.Filled.Restaurant
}

private enum class KitchenStage {
    FIRED,
    IN_PROGRESS,
    READY
}

private data class KitchenOrder(
    val id: String,
    val table: String,
    val station: String,
    val stage: KitchenStage,
    val priority: String,
    val elapsed: String,
    val covers: Int,
    val server: String,
    val items: List<String>,
    val note: String? = null
)

private val kitchenOrders = listOf(
    KitchenOrder("#ORD-1248", "A12", "Hot Kitchen", KitchenStage.FIRED, "Rush", "02m ago", 4, "David K.", listOf("Bruschetta", "Margherita Pizza", "Spaghetti Carbonara", "Limonata")),
    KitchenOrder("#ORD-1249", "T4", "Hot Kitchen", KitchenStage.FIRED, "Rush", "03m ago", 2, "Emma W.", listOf("Lasagna", "Caesar Salad", "Garlic Bread", "Limonata")),
    KitchenOrder("#ORD-1250", "B2", "Pizza", KitchenStage.FIRED, "Normal", "04m ago", 3, "Michael B.", listOf("Panna Arrabbiata", "Margherita Pizza", "Tiramisu")),
    KitchenOrder("#ORD-1251", "A3", "Pasta", KitchenStage.FIRED, "Rush", "05m ago", 2, "Sophia R.", listOf("Spaghetti Carbonara", "Garlic Bread", "Limonata")),
    KitchenOrder("#ORD-1245", "A6", "Hot Kitchen", KitchenStage.IN_PROGRESS, "Rush", "08m ago", 4, "David K.", listOf("Spaghetti Carbonara", "Chicken Parmesan", "Garlic Bread", "Limonata"), "No onion"),
    KitchenOrder("#ORD-1246", "T1", "Pasta", KitchenStage.IN_PROGRESS, "Normal", "07m ago", 2, "Sophia R.", listOf("Lasagna", "Caesar Salad", "Tiramisu")),
    KitchenOrder("#ORD-1247", "B1", "Pizza", KitchenStage.IN_PROGRESS, "Normal", "06m ago", 2, "James C.", listOf("Margherita Pizza", "Spaghetti Bolognese", "Limonata")),
    KitchenOrder("#ORD-1242", "B3", "Pasta", KitchenStage.IN_PROGRESS, "Normal", "09m ago", 2, "Ava G.", listOf("Fettuccine Alfredo", "Caesar Salad", "Limonata"), "Birthday table"),
    KitchenOrder("#ORD-1241", "A5", "Pizza", KitchenStage.READY, "Normal", "15m ago", 2, "Liam H.", listOf("Margherita Pizza", "Bruschetta", "Tiramisu")),
    KitchenOrder("#ORD-1240", "T3", "Hot Kitchen", KitchenStage.READY, "Rush", "14m ago", 4, "Emma W.", listOf("Lasagna", "Garlic Bread", "Limonata")),
    KitchenOrder("#ORD-1239", "B4", "Dessert", KitchenStage.READY, "Normal", "11m ago", 2, "Michael B.", listOf("Chicken Alfredo", "Caesar Salad", "Panna Cotta")),
    KitchenOrder("#ORD-1238", "B1", "Pasta", KitchenStage.READY, "Normal", "16m ago", 2, "Olivia M.", listOf("Spaghetti Carbonara", "Garlic Bread", "Limonata")),
    KitchenOrder("#ORD-1237", "T2", "Drinks", KitchenStage.READY, "Normal", "17m ago", 2, "Ethan W.", listOf("Burrata", "Limonata")),
    KitchenOrder("#ORD-1236", "R5", "Dessert", KitchenStage.READY, "Normal", "18m ago", 2, "Isabella P.", listOf("Panna Cotta", "Espresso"))
)
