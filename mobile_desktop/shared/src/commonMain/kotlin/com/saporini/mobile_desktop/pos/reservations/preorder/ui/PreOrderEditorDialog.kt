package com.saporini.mobile_desktop.pos.reservations.preorder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.ChoiceChip
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.format.centsOrNull
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderCatalogMenu
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderItemChoices
import com.saporini.mobile_desktop.pos.reservations.preorder.MAX_DISH_NOTES
import com.saporini.mobile_desktop.pos.reservations.preorder.MAX_DISH_QUANTITY
import com.saporini.mobile_desktop.pos.reservations.preorder.MAX_PRE_ORDER_NOTES
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderLine
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderOptionRequestDto
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderScreenModel
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderState
import kotlinx.coroutines.CancellationException

/** One dish line's identity: the same dish with the same choices is the same line. */
private fun lineKey(menuItemId: String, variantId: String?, options: List<PreOrderOptionRequestDto>) =
    "$menuItemId|${variantId.orEmpty()}|${options.joinToString(",") { "${it.optionItemId}x${it.quantity}" }}"

private fun PreOrderLine.key() = lineKey(menuItemId, variantId, options)

/** Taking the guests' food with the booking: the menu on the left, their order on the right. */
@Composable
internal fun PreOrderEditorDialog(state: PreOrderState, model: PreOrderScreenModel, dishes: PreOrderDishes, guests: Int) {
    val restaurantId = state.restaurantId ?: return
    var menus by remember { mutableStateOf<List<OrderCatalogMenu>?>(null) }
    var loadProblem by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(restaurantId, attempt) {
        loadProblem = null
        try {
            menus = dishes.menus(restaurantId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadProblem = "Couldn't load the menu. Check the connection and try again."
        }
    }
    // Price of one of each line, for the estimate; the server prices the pre-order when it's saved.
    val unitPrices = remember { mutableStateMapOf<String, Long>() }
    LaunchedEffect(state.preOrder?.id) {
        state.preOrder?.items?.forEach { item ->
            val total = item.lineTotal?.centsOrNull() ?: return@forEach
            if (item.quantity > 0) unitPrices[lineKey(item.menuItemId, item.variantId,
                item.options.map { PreOrderOptionRequestDto(it.optionItemId, it.quantity, it.notes) })] = total / item.quantity
        }
    }
    var choosing by remember { mutableStateOf<DishEntry?>(null) }
    val add: (DishEntry) -> Unit = { dish ->
        if (dish.needsChoices) choosing = dish
        else {
            unitPrices[lineKey(dish.item.id, null, emptyList())] = dish.priceCents
            model.addDish(dish.item.id, dish.item.name)
        }
    }
    val count = state.dishCount
    AppDialog(if (state.preOrder?.open == true) "Change the pre-order" else "Take a pre-order", model::cancelEdit, busy = state.saving, maxWidth = 1000.dp,
        subtitle = "${if (guests == 1) "1 guest" else "$guests guests"} · the kitchen gets it a set time before they come",
        buttons = {
            Text("$count ${if (count == 1) "dish" else "dishes"}", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = Kit.Muted)
            KitButton("Cancel", model::cancelEdit, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton(if (state.preOrder?.open == true) "Save changes" else "Save pre-order", model::save, icon = Icons.Outlined.Check, loading = state.saving,
                enabled = state.lines.isNotEmpty())
        }) {
        state.error?.let { MessageBar(it, MessageKind.ERROR, onDismiss = model::clearMessages) }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            var phoneTab by remember { mutableStateOf("Menu") }
            val menuPane: @Composable (Modifier) -> Unit = { m -> MenuPane(menus, loadProblem, { attempt++ }, add, m) }
            val orderPane: @Composable (Modifier) -> Unit = { m -> OrderPane(state, model, unitPrices, m) }
            if (wide) {
                Row(Modifier.fillMaxWidth().height(520.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    menuPane(Modifier.weight(1.15f).fillMaxHeight())
                    orderPane(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val tabs = listOf("Menu", "Their food ($count)")
                    OverviewTabs(tabs, if (phoneTab == "Menu") tabs[0] else tabs[1], { phoneTab = if (it == tabs[0]) "Menu" else "Food" }, Modifier.fillMaxWidth())
                    if (phoneTab == "Menu") menuPane(Modifier.fillMaxWidth().height(460.dp)) else orderPane(Modifier.fillMaxWidth().height(460.dp))
                }
            }
        }
    }
    choosing?.let { dish ->
        DishChoicesDialog(restaurantId, dish, dishes, onClose = { choosing = null }) { variantId, variantName, options, optionNames, unitCents, quantity, note ->
            val name = listOfNotNull(dish.item.name, variantName).joinToString(" · ") + if (optionNames.isNotEmpty()) " + " + optionNames.joinToString(", ") else ""
            val key = lineKey(dish.item.id, variantId, options)
            unitPrices[key] = unitCents
            val before = state.lines
            val existing = before.indexOfFirst { it.key() == key && it.notes.isBlank() }
            model.addDish(dish.item.id, name, variantId, options)
            val index = if (existing >= 0) existing else before.size
            val already = if (existing >= 0) before[existing].quantity else 0
            if (quantity > 1) model.quantity(index, already + quantity)
            if (note.isNotBlank()) model.dishNotes(index, note)
            choosing = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuPane(menus: List<OrderCatalogMenu>?, problem: String?, onRetry: () -> Unit, onAdd: (DishEntry) -> Unit, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    var menuId by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Find a dish", height = 44.dp)
        when {
            problem != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(problem, fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                    RetryText(onClick = onRetry)
                }
            }
            menus == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Kit.Green, strokeWidth = 2.dp) }
            else -> {
                val all = dishesOf(menus)
                if (menus.size > 1) FilterPills(listOf<Pair<String?, String>>(null to "All menus") + menus.map { it.id to it.name }, menuId,
                    { menuId = it; section = null })
                val inMenu = all.filter { menuId == null || it.menuId == menuId }
                val sections = inMenu.map { it.section }.distinct()
                if (sections.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Everything", section == null, { section = null })
                    sections.forEach { name -> ChoiceChip(name, section == name, { section = if (section == name) null else name }) }
                }
                val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val shown = inMenu.filter { (section == null || it.section == section) &&
                    words.all { w -> "${it.item.name} ${it.section} ${it.item.description.orEmpty()}".lowercase().contains(w) } }
                if (shown.isEmpty()) {
                    OverviewCompactEmpty(if (all.isEmpty()) "No dishes" else "Nothing matches",
                        if (all.isEmpty()) "Add dishes to an active menu first." else "Try another word or section.", Icons.Outlined.RestaurantMenu)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
                        items(shown, key = { it.item.id }) { dish -> DishRow(dish, menus.size > 1 && menuId == null) { onAdd(dish) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun DishRow(dish: DishEntry, showMenu: Boolean, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp)).clickable(onClick = onAdd).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(dish.item.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(dish.section, dish.menuName.takeIf { showMenu }, if (dish.needsChoices) "choices" else null).joinToString(" · "),
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        }
        Text(moneyText(dish.priceCents, null), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        Box(Modifier.size(30.dp).clip(CircleShape).background(Kit.GreenSoft), Alignment.Center) {
            Icon(if (dish.needsChoices) Icons.Outlined.Tune else Icons.Outlined.Add, "Add ${dish.item.name}", Modifier.size(17.dp), tint = Kit.Green)
        }
    }
}

@Composable
private fun OrderPane(state: PreOrderState, model: PreOrderScreenModel, unitPrices: Map<String, Long>, modifier: Modifier) {
    var noteFor by remember { mutableStateOf<Int?>(null) }
    val known = state.lines.all { it.key() in unitPrices }
    val estimate = state.lines.sumOf { (unitPrices[it.key()] ?: 0) * it.quantity }
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Kit.Canvas, border = BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Caption("Their food")
            if (state.lines.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Text("Tap dishes on the left to add them.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(state.lines, key = { index, line -> "$index-${line.key()}" }) { index, line ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Stepper(line.quantity, { model.quantity(index, it) })
                                Column(Modifier.weight(1f)) {
                                    Text(line.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                    if (line.notes.isNotBlank() && noteFor != index) Text("“${line.notes}”", fontFamily = Inter(), fontSize = 11.sp, color = Color(0xFF8B5C18))
                                }
                                unitPrices[line.key()]?.let { Text(moneyText(it * line.quantity, null), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Ink) }
                                Box(Modifier.size(28.dp).clip(CircleShape).clickable { model.quantity(index, 0) }, Alignment.Center) {
                                    Icon(Icons.Outlined.DeleteOutline, "Remove ${line.name}", Modifier.size(17.dp), tint = Kit.Danger)
                                }
                            }
                            if (noteFor == index) {
                                TextInput(line.notes, { model.dishNotes(index, it) }, placeholder = "No onions, well done…", maxLength = MAX_DISH_NOTES)
                            } else if (line.notes.isBlank()) {
                                Text("+ note for the kitchen", Modifier.clip(RoundedCornerShape(6.dp)).clickable { noteFor = index }.padding(2.dp),
                                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Green)
                            } else {
                                Text("Change note", Modifier.clip(RoundedCornerShape(6.dp)).clickable { noteFor = index }.padding(2.dp),
                                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Green)
                            }
                        }
                    }
                }
            }
            TextInput(state.notes, model::notes, label = "Note for the whole pre-order", optional = true, maxLength = MAX_PRE_ORDER_NOTES,
                placeholder = "Serve the starters as soon as they sit down")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (known) "Total" else "About", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
                Text(moneyText(estimate, null), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink)
            }
            Text("Tax and service are added when it's saved.", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        }
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 0) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).clickable { onChange((value - 1).coerceAtLeast(min)) }, Alignment.Center) {
            Icon(Icons.Outlined.Remove, "One less", Modifier.size(15.dp), tint = Kit.Ink)
        }
        Text("$value", Modifier.width(22.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink,
            textAlign = TextAlign.Center)
        Box(Modifier.size(28.dp).clip(CircleShape).clickable { onChange((value + 1).coerceAtMost(MAX_DISH_QUANTITY)) }, Alignment.Center) {
            Icon(Icons.Outlined.Add, "One more", Modifier.size(15.dp), tint = Kit.Ink)
        }
    }
}

/** A dish's size and extras, chosen before it's added. Required choices must be made. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishChoicesDialog(
    restaurantId: String,
    dish: DishEntry,
    dishes: PreOrderDishes,
    onClose: () -> Unit,
    onAdd: (variantId: String?, variantName: String?, options: List<PreOrderOptionRequestDto>, optionNames: List<String>, unitCents: Long, quantity: Int, note: String) -> Unit
) {
    var choices by remember { mutableStateOf<OrderItemChoices?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dish.item.id) {
        try {
            choices = dishes.choices(restaurantId, dish.menuId, dish.item.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problem = e.message ?: "Couldn't load this dish's choices."
        }
    }
    val variants = dish.item.variants.orEmpty().filter { it.active }.sortedBy { it.displayOrder }
    var variantId by remember { mutableStateOf(variants.firstOrNull { it.isDefault }?.id ?: variants.firstOrNull()?.id) }
    val picked = remember { mutableStateListOf<String>() }
    var quantity by remember { mutableStateOf(1) }
    var note by remember { mutableStateOf("") }
    val groups = choices?.groups.orEmpty()
    val missing = groups.firstOrNull { group -> group.availableChoices.count { it.id in picked } < group.minimum }
    val variant = variants.firstOrNull { it.id == variantId }
    val chosen = groups.flatMap { it.availableChoices }.filter { it.id in picked }
    val unit = dish.priceCents + (variant?.priceDelta?.centsOrNull() ?: 0) + chosen.sumOf { it.priceDelta.centsOrNull() ?: 0 }
    AppDialog(dish.item.name, onClose, subtitle = listOfNotNull(dish.section, dish.item.description).joinToString(" · "), maxWidth = 560.dp,
        buttons = {
            KitButton("Back", onClose, style = ButtonStyle.SECONDARY)
            KitButton("Add ${moneyText(unit * quantity, null)}", {
                onAdd(variant?.id, variant?.name, chosen.map { PreOrderOptionRequestDto(it.id, 1) }, chosen.map { it.name }, unit, quantity, note.trim())
            }, icon = Icons.Outlined.Add, enabled = choices != null && missing == null)
        }) {
        problem?.let { MessageBar(it, MessageKind.ERROR) }
        if (variants.isNotEmpty()) {
            Caption("Size")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                variants.forEach { v ->
                    val delta = v.priceDelta.centsOrNull() ?: 0
                    ChoiceChip(v.name + if (delta != 0L) " (${if (delta > 0) "+" else ""}${moneyText(delta, null)})" else "", variantId == v.id, { variantId = v.id })
                }
            }
        }
        if (choices == null && problem == null) {
            Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = Kit.Green, strokeWidth = 2.dp) }
        }
        groups.forEach { group ->
            val max = group.maximum
            val taken = group.availableChoices.count { it.id in picked }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caption(group.group.name, Modifier.weight(1f))
                Text(when {
                    group.minimum > 0 && max == group.minimum -> "choose ${group.minimum}"
                    group.minimum > 0 -> "at least ${group.minimum}"
                    max != null -> "up to $max"
                    else -> "optional"
                }, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (taken < group.minimum) Kit.Danger else Kit.Muted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.availableChoices.forEach { choice ->
                    val delta = choice.priceDelta.centsOrNull() ?: 0
                    val on = choice.id in picked
                    ChoiceChip(choice.name + if (delta != 0L) " +${moneyText(delta, null)}" else "", on, {
                        when {
                            on -> picked.remove(choice.id)
                            max == 1 -> { picked.removeAll(group.availableChoices.map { it.id }); picked.add(choice.id) }
                            max == null || taken < max -> picked.add(choice.id)
                        }
                    })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("How many", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
            Stepper(quantity, { quantity = it.coerceAtLeast(1) }, min = 1)
        }
        TextInput(note, { note = it }, label = "Note for the kitchen", optional = true, maxLength = MAX_DISH_NOTES, placeholder = "No onions")
        missing?.let { Text("Choose ${it.group.name.lowercase()} first", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger) }
        Spacer(Modifier.height(2.dp))
    }
}
