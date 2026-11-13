package com.saporini.mobile_desktop.pos.menu.ui.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem as DomainMenuItem
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import org.koin.compose.koinInject

private val Green = Color(0xFF4F7942)
private val Ink = Color(0xFF222426)
private val Muted = Color(0xFF747572)

// "Import existing": pick dishes from the restaurant's other menus. They're added as copies with their own price,
// so changing one never changes the other.
@Composable
internal fun ImportItemsDialog(
    currentMenuId: String,
    sectionName: String,
    importing: Boolean,
    onDismiss: () -> Unit,
    onImport: (itemIds: List<String>) -> Unit
) {
    val repository = koinInject<MenuRepository>()
    val session = koinInject<SessionManager>()
    var groups by remember { mutableStateOf<List<Pair<String, List<DomainMenuItem>>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(currentMenuId) {
        val restaurantId = session.currentUser.value?.restaurantId
        runCatching {
            repository.getMenus(restaurantId = restaurantId, size = 100).items
                .filter { it.id != currentMenuId }
                .map { menu ->
                    val full = repository.getMenu(menu.id, includeSections = true, includeItems = true)
                    menu.name to full.sections.flatMap { it.items }
                }
                .filter { it.second.isNotEmpty() }
        }.onSuccess { groups = it }.onFailure { error = it.message ?: "Couldn't load the menus" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import into $sectionName", fontFamily = Inter(), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Each dish is copied with its own price: changing it here doesn't change the original.", fontFamily = Inter(), fontSize = 12.sp, color = Muted)
                OutlinedTextField(
                    value = search, onValueChange = { search = it.take(60) }, singleLine = true,
                    placeholder = { Text("Search dishes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(9.dp)
                )
                val loaded = groups
                when {
                    error != null -> Text(error.orEmpty(), fontFamily = Inter(), fontSize = 12.sp, color = Color(0xFFB13A2F))
                    loaded == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Green, strokeWidth = 2.dp)
                    }
                    else -> Column(Modifier.height(360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val query = search.trim().lowercase()
                        loaded.forEach { (menuName, items) ->
                            val shown = items.filter { query.isEmpty() || query in it.name.lowercase() }
                            if (shown.isEmpty()) return@forEach
                            Text(menuName, Modifier.padding(top = 6.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Ink)
                            shown.forEach { item ->
                                val checked = item.id in picked
                                Row(
                                    Modifier.fillMaxWidth().clickable { picked = if (checked) picked - item.id else picked + item.id },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked, { picked = if (checked) picked - item.id else picked + item.id }, colors = CheckboxDefaults.colors(checkedColor = Green))
                                    Text(item.name, Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(price(item.basePrice), fontFamily = Inter(), fontSize = 12.sp, color = Muted)
                                }
                            }
                        }
                        if (loaded.isEmpty()) Text("The other menus have no dishes yet.", fontFamily = Inter(), fontSize = 12.sp, color = Muted)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (picked.isNotEmpty() && !importing) onImport(picked.toList()) }) {
                Text(
                    if (importing) "Importing…" else if (picked.isEmpty()) "Import" else "Import ${picked.size}",
                    fontFamily = Inter(), fontWeight = FontWeight.Bold, color = if (picked.isEmpty()) Muted else Green
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back", fontFamily = Inter(), color = Muted) } },
        containerColor = Color.White
    )
}

private fun price(value: Double): String {
    val cents = kotlin.math.round(value * 100).toLong()
    return "€${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
