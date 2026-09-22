package com.saporini.mobile_desktop.pos.menu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.theme.Inter
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import org.jetbrains.compose.resources.painterResource

private val MenuSearchTextInk = Color(0xFF222426)
private val MenuSearchMutedInk = Color(0xFF747572)

@Composable
internal fun <T> MenuSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<T>,
    itemName: (T) -> String,
    itemCategory: (T) -> String,
    itemPrice: (T) -> String,
    itemAvailable: (T) -> Boolean,
    onSuggestionClick: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val suggestions = remember(query, expanded, items) {
        if (query.isBlank() || !expanded) {
            emptyList()
        } else {
            items
                .filter { itemName(it).contains(query, ignoreCase = true) }
                .take(5)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val suggestionWidth = maxWidth

        SearchField(
            query = query,
            onQueryChange = { value ->
                onQueryChange(value)
                expanded = value.isNotBlank()
            },
            placeholder = "Search menu items",
            modifier = Modifier.fillMaxWidth()
        )

        // Query non-blank but nothing matched is a real, distinct outcome from "haven't
        // typed enough yet" — show it explicitly instead of just closing the dropdown.
        val showNoResults = expanded && query.isNotBlank() && suggestions.isEmpty()

        if (suggestions.isNotEmpty() || showNoResults) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(suggestionWidth)
                    .background(Color.White),
                properties = PopupProperties(focusable = false)
            ) {
                if (showNoResults) {
                    MenuSearchNoResultsRow(query = query)
                } else {
                    suggestions.forEach { item ->
                        MenuSearchSuggestionRow(
                            name = itemName(item),
                            category = itemCategory(item),
                            price = itemPrice(item),
                            available = itemAvailable(item),
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
}

@Composable
private fun MenuSearchNoResultsRow(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = MenuSearchMutedInk
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No items found for “$query”",
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MenuSearchTextInk,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Try a different name or check the spelling.",
            fontFamily = Inter(),
            fontSize = 12.sp,
            color = MenuSearchMutedInk,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MenuSearchSuggestionRow(
    name: String,
    category: String,
    price: String,
    available: Boolean,
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
            contentDescription = name,
            modifier = Modifier
                .size(width = 58.dp, height = 42.dp)
                .clip(RoundedCornerShape(6.dp))
                .graphicsLayer(alpha = if (available) 1f else 0.45f),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.sp,
                color = MenuSearchTextInk,
                maxLines = 1,
                softWrap = false
            )

            Text(
                text = category,
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.sp,
                color = MenuSearchMutedInk,
                maxLines = 1,
                softWrap = false
            )
        }

        Text(
            text = price,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.sp,
            color = MenuSearchTextInk
        )
    }
}
