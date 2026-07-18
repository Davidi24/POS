package com.saporini.mobile_desktop.pos.menu

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ZoomOutMap
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.saporini.mobile_desktop.core.theme.Inter
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import org.jetbrains.compose.resources.painterResource

private val ActiveOlive = Color(0xFF4B522A)
private val TextInk = Color(0xFF222426)
private val MutedInk = Color(0xFF747572)
private val Border = Color(0xFFE8E5E1)

@Composable
fun MenuScreen(
    modifier: Modifier = Modifier
) {
    var selectedMeal by remember { mutableStateOf("Dinner") }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchItemName by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBox(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSuggestionClick = { item ->
                    searchQuery = item.name
                    selectedCategory = item.category
                    selectedSearchItemName = item.name
                },
                modifier = Modifier.width(360.dp)
            )
            SegmentedButtons(
                items = listOf("Lunch", "Dinner", "Drinks"),
                selected = selectedMeal,
                onSelected = { selectedMeal = it }
            )
        }

        CategoryButtons(
            items = listOf("All", "Antipasti", "Pasta", "Pizza", "Dolci", "Beverages"),
            selected = selectedCategory,
            onSelected = { selectedCategory = it }
        )

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 900.dp) 2 else 4
            val visibleItems = if (selectedCategory == "All") {
                menuItems
            } else {
                menuItems.filter { it.category == selectedCategory }
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                visibleItems.chunked(columns).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rowItems.forEach { item ->
                            MenuItemCard(
                                item = item,
                                selected = item.name == selectedSearchItemName,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onSuggestionClick: (MenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(query) {
        if (query.isBlank() || !expanded) {
            emptyList()
        } else {
            menuItems
                .filter { it.name.contains(query, ignoreCase = true) }
                .take(5)
        }
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Border, RoundedCornerShape(8.dp))
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
                value = query,
                onValueChange = { value ->
                    onQueryChange(value)
                    expanded = value.isNotBlank()
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.sp,
                    color = TextInk
                ),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search menu items",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            letterSpacing = 0.sp,
                            color = Color(0xFF91918F)
                        )
                    }
                    innerTextField()
                }
            )
        }

        if (suggestions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(top = 56.dp)
                    .fillMaxWidth()
                    .zIndex(4f)
                    .shadow(5.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
            ) {
                suggestions.forEach { item ->
                    SearchSuggestionRow(
                        item = item,
                        onClick = {
                            expanded = false
                            onSuggestionClick(item)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionRow(
    item: MenuItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(Res.drawable.auth_login_img),
            contentDescription = item.name,
            modifier = Modifier
                .size(width = 58.dp, height = 42.dp)
                .clip(RoundedCornerShape(6.dp))
                .graphicsLayer(alpha = if (item.available) 1f else 0.45f),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.sp,
                color = TextInk,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = item.category,
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.sp,
                color = MutedInk,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            text = item.price,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.sp,
            color = ActiveOlive
        )
    }
}

@Composable
private fun SegmentedButtons(
    items: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    compact: Boolean = true
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { item ->
            val isSelected = item == selected
            TextButton(
                onClick = { onSelected(item) },
                modifier = Modifier
                    .height(if (compact) 48.dp else 42.dp)
                    .width(if (compact) 88.dp else 112.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ActiveOlive else Color.White)
                    .border(1.dp, Border, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSelected) Color.White else TextInk
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
private fun CategoryButtons(
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
                    .background(if (isSelected) ActiveOlive else Color.White)
                    .border(1.dp, Border, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSelected) Color.White else TextInk
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = categoryIcon(item),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isSelected) Color.White else ActiveOlive
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

@Composable
private fun MenuItemCard(
    item: MenuItem,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (item.available) 1f else 0.42f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ActiveOlive else Border,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp)
    ) {
        Column {
            Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.12f)
                .clip(RoundedCornerShape(6.dp))
            ) {
                Image(
                    painter = painterResource(Res.drawable.auth_login_img),
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = contentAlpha),
                    contentScale = ContentScale.Crop
                )

                AvailabilityButton(
                    available = item.available,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = item.name,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = 0.sp,
                color = TextInk.copy(alpha = contentAlpha),
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.description,
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                letterSpacing = 0.sp,
                color = MutedInk.copy(alpha = contentAlpha),
                minLines = 2,
                maxLines = 2
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = item.price,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                letterSpacing = 0.sp,
                color = ActiveOlive.copy(alpha = contentAlpha)
            )
        }

        ExpandImageButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 2.dp, bottom = 0.dp)
        )
    }
}

@Composable
private fun AvailabilityButton(
    available: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(if (available) Color.White else Color(0xFF6D6D6D))
            .clickable {}
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (available) "Available" else "Unavailable",
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.sp,
            color = if (available) ActiveOlive else Color.White,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ExpandImageButton(
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {},
        modifier = modifier
            .size(29.dp)
            .shadow(2.dp, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.92f))
    ) {
        Icon(
            imageVector = Icons.Outlined.ZoomOutMap,
            contentDescription = "Expand image",
            modifier = Modifier.size(18.dp),
            tint = ActiveOlive
        )
    }
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "All" -> Icons.Outlined.RestaurantMenu
    "Antipasti" -> Icons.Outlined.Restaurant
    "Pasta" -> Icons.Outlined.Restaurant
    "Pizza" -> Icons.Outlined.LocalPizza
    "Dolci" -> Icons.Outlined.Cake
    "Beverages" -> Icons.Outlined.LocalDrink
    else -> Icons.Outlined.RestaurantMenu
}

private data class MenuItem(
    val name: String,
    val description: String,
    val price: String,
    val category: String,
    val available: Boolean
)

private val menuItems = listOf(
    MenuItem("Burrata con Pomodorini", "Creamy burrata with heirloom cherry tomatoes, basil, and olive oil.", "\$14.00", "Antipasti", true),
    MenuItem("Bruschetta al Pomodoro", "Grilled sourdough topped with ripe tomatoes, garlic, basil, and olive oil.", "\$10.00", "Antipasti", true),
    MenuItem("Prosciutto e Melone", "Thinly sliced prosciutto di Parma with sweet melon.", "\$13.00", "Antipasti", true),
    MenuItem("Carpaccio di Manzo", "Thinly sliced beef tenderloin with arugula, parmesan, and lemon.", "\$15.00", "Antipasti", false),
    MenuItem("Spaghetti alla Carbonara", "Classic Roman pasta with eggs, Pecorino cheese, guanciale, and black pepper.", "\$18.00", "Pasta", true),
    MenuItem("Lasagna alla Bolognese", "Layers of pasta, slow-cooked meat sauce, bechamel, and melted mozzarella.", "from \$20.00", "Pasta", true),
    MenuItem("Risotto ai Funghi", "Creamy Arborio rice with wild mushrooms, white wine, and parmesan.", "\$19.00", "Pasta", true),
    MenuItem("Pizza Quattro Formaggi", "Mozzarella, gorgonzola, fontina, parmesan, and ricotta.", "\$16.00", "Pizza", false),
    MenuItem("Pizza Margherita", "San Marzano tomato sauce, fior di latte mozzarella, fresh basil, and olive oil.", "\$15.00", "Pizza", true),
    MenuItem("Tiramisu", "Classic Italian dessert with mascarpone cream and espresso-soaked ladyfingers.", "\$8.50", "Dolci", true),
    MenuItem("Panna Cotta", "Silky vanilla panna cotta with mixed berry compote.", "\$7.50", "Dolci", true),
    MenuItem("Lemon Soda", "Refreshing sparkling lemon soda with a hint of mint.", "\$4.50", "Beverages", true)
)
