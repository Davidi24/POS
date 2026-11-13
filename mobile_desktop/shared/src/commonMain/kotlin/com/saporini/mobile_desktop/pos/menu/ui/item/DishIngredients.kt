package com.saporini.mobile_desktop.pos.menu.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.data.dto.DishIngredientDto
import com.saporini.mobile_desktop.pos.menu.data.dto.DishIngredientRequestDto
import com.saporini.mobile_desktop.pos.menu.data.dto.DishRecipeDto
import com.saporini.mobile_desktop.admin.inventory.ui.NamedThumb
import com.saporini.mobile_desktop.pos.menu.data.dto.IngredientChoiceDto
import com.saporini.mobile_desktop.pos.menu.data.dto.UnitCostDto
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import com.saporini.mobile_desktop.pos.menu.ui.menu.isPhoneMenuWindow
import kotlin.math.round

// The dish editor's "Recipe / Ingredients": the dish's ingredients from stock (its recipe on the server, which takes
// stock when the dish is served), plus names that only tell guests what's in it. Guests see every row's name.

private val Olive = Color(0xFF4F7942)
private val Ink = Color(0xFF242522)
private val Muted = Color(0xFF71736E)
private val Border = Color(0xFFE2E3DE)
private val Surface = Color(0xFFF7F7F5)
private val Placeholder = Color(0xFFC7C8C2)
private val Danger = Color(0xFFB13A2F)

const val STOCK_INGREDIENT = "STOCK"
const val PREPARATION_INGREDIENT = "PREPARATION"
private const val MAX_INGREDIENT_QUANTITY = 1_000_000.0

/** One row: from stock ([kind] and [refId] set, with an amount), or a name guests see only. */
internal data class IngredientRow(
    val name: String,
    val kind: String? = null,
    val refId: String? = null,
    val quantity: String = "",
    val unit: String = ""
) {
    val fromStock: Boolean get() = kind != null && refId != null
    val key: String get() = if (fromStock) "$kind:$refId" else "NAME:${name.trim().lowercase()}"

    companion object {
        fun of(ingredient: DishIngredientDto) =
            IngredientRow(ingredient.name, ingredient.kind, ingredient.id, plainQuantity(ingredient.quantity), ingredient.unit)
    }
}

/** Where loading the dish's stock ingredients stands (a new dish has none to load). */
internal sealed interface DishRecipeLoad {
    data object None : DishRecipeLoad
    data object Loading : DishRecipeLoad
    data object Loaded : DishRecipeLoad
    data class Failed(val message: String) : DishRecipeLoad
}

internal val IngredientChoiceDto.key: String get() = "$kind:$id"

/**
 * The guest names already on the dish, with the recipe's amounts put on the names they match; recipe ingredients
 * no name matches come after.
 */
internal fun mergeRecipe(rows: List<IngredientRow>, recipe: DishRecipeDto): List<IngredientRow> {
    val remaining = recipe.ingredients.toMutableList()
    val merged = rows.map { row ->
        if (row.fromStock) return@map row
        val match = remaining.firstOrNull { it.name.equals(row.name.trim(), ignoreCase = true) }
        if (match == null) row else {
            remaining.remove(match)
            IngredientRow.of(match)
        }
    }
    return merged + remaining.map { IngredientRow.of(it) }
}

/** The stock part to save as the dish's recipe, in the rows' order. */
internal fun stockRequest(rows: List<IngredientRow>): List<DishIngredientRequestDto> = rows.filter { it.fromStock }.map { row ->
    DishIngredientRequestDto(
        inventoryItemId = row.refId.takeIf { row.kind == STOCK_INGREDIENT },
        preparationId = row.refId.takeIf { row.kind == PREPARATION_INGREDIENT },
        quantity = row.quantity.trim().toDouble(),
        unit = row.unit
    )
}

/** What one portion costs from the rows from stock; null when there are none. [complete] false: some cost is unknown. */
internal data class DishCost(val total: Double, val complete: Boolean)

internal fun dishCost(rows: List<IngredientRow>, choices: List<IngredientChoiceDto>?): DishCost? {
    val stock = rows.filter { it.fromStock }
    if (stock.isEmpty()) return null
    var total = 0.0
    var complete = true
    stock.forEach { row ->
        val unitCost = choices?.firstOrNull { it.key == row.key }?.units?.firstOrNull { it.unit == row.unit }?.cost
        val quantity = row.quantity.trim().toDoubleOrNull()
        if (unitCost == null || quantity == null) complete = false else total += quantity * unitCost
    }
    return DishCost(total, complete)
}

internal fun isValidIngredientAmount(text: String): Boolean {
    val normalized = text.trim()
    if (!Regex("^[0-9]{1,7}(\\.[0-9]{1,3})?$").matches(normalized)) return false
    return normalized.toDoubleOrNull()?.let { it > 0.0 && it <= MAX_INGREDIENT_QUANTITY } == true
}

// Measured in grams rather than kilograms (and so on) unless the item is counted in something else.
internal fun preferredUnit(choice: IngredientChoiceDto): String = when (choice.baseUnit) {
    "KILOGRAM" -> "GRAM".takeIf { unit -> choice.units.any { it.unit == unit } } ?: choice.baseUnit
    "LITER" -> "MILLILITER".takeIf { unit -> choice.units.any { it.unit == unit } } ?: choice.baseUnit
    else -> choice.baseUnit
}

internal fun plainQuantity(value: Double): String {
    val rounded = round(value * 1000) / 1000
    return if (rounded == kotlin.math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

private fun money(value: Double): String {
    val cents = round(value * 100).toLong()
    val whole = cents / 100
    val rest = (cents % 100).toString().padStart(2, '0')
    return "$whole.$rest"
}

private fun groupLabel(group: String): String = if (group == PREPARATION_INGREDIENT) "Sub-recipes" else humanize(group)

@Composable
internal fun DishIngredientsSection(
    rows: List<IngredientRow>,
    load: DishRecipeLoad,
    choices: List<IngredientChoiceDto>?,
    price: Double?,
    onAdd: () -> Unit,
    onEdit: (IngredientRow) -> Unit,
    onRemove: (IngredientRow) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (load) {
            DishRecipeLoad.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Olive, strokeWidth = 2.dp)
                Text("Loading the amounts from stock…", fontFamily = Inter(), fontSize = 12.sp, color = Muted)
            }
            is DishRecipeLoad.Failed -> Text(
                "The amounts from stock couldn't be loaded (${load.message}). Saving now only changes the names guests see.",
                fontFamily = Inter(), fontSize = 12.sp, color = Danger
            )
            else -> {}
        }
        rows.forEach { row -> IngredientRowView(row, choices, onEdit, onRemove) }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(9.dp))
                .clickable(onClick = onAdd)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Olive)
            Text("Add Ingredient", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Olive)
        }
        CostLine(rows, choices, price)
    }
}

@Composable
private fun IngredientRowView(
    row: IngredientRow,
    choices: List<IngredientChoiceDto>?,
    onEdit: (IngredientRow) -> Unit,
    onRemove: (IngredientRow) -> Unit
) {
    val choice = choices?.firstOrNull { it.key == row.key }
    val detail = when {
        !row.fromStock -> "Name only · not taken from stock"
        else -> listOfNotNull(
            if (row.kind == PREPARATION_INGREDIENT) "Sub-recipe" else "Stock",
            "${row.quantity} ${unitShort(row.unit)}",
            choice?.units?.firstOrNull { it.unit == row.unit }?.cost?.let { cost ->
                row.quantity.toDoubleOrNull()?.let { "costs ${money(it * cost)}" }
            }
        ).joinToString(" · ")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(9.dp))
            .clickable(enabled = row.fromStock) { onEdit(row) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stock items and sub-recipes show their picture; a name-only line keeps its plain badge.
        if (row.fromStock) IngredientPicture(row.name, row.kind.orEmpty(), choice?.imageUrl)
        else KindBadge(Icons.AutoMirrored.Outlined.Label, active = false)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontFamily = Inter(), fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { onRemove(row) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Remove ${row.name}", modifier = Modifier.size(15.dp), tint = Muted)
        }
    }
}

/** An ingredient's picture by its name (MOCK sample pictures for now), else its kind's icon. */
@Composable
internal fun IngredientPicture(name: String, kind: String, imageUrl: String?) {
    NamedThumb(name, imageUrl, if (kind == PREPARATION_INGREDIENT) Icons.Outlined.Kitchen else Icons.Outlined.Inventory2, Olive, 38.dp)
}

@Composable
private fun KindBadge(icon: ImageVector, active: Boolean) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(if (active) Olive.copy(alpha = 0.12f) else Border.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = if (active) Olive else Muted)
    }
}

// "Cost per portion 2.10 · 23% of the price", from today's stock prices.
@Composable
private fun CostLine(rows: List<IngredientRow>, choices: List<IngredientChoiceDto>?, price: Double?) {
    val cost = dishCost(rows, choices)
    val text = when {
        cost == null -> "Pick ingredients from stock to count them out when the dish is served and see what it costs you."
        else -> buildString {
            append(if (cost.complete) "Cost per portion " else "Cost per portion about ")
            append(money(cost.total))
            if (price != null && price > 0) append(" · ${round(cost.total / price * 100).toLong()}% of the price")
        }
    }
    Text(text, fontFamily = Inter(), fontWeight = if (cost == null) FontWeight.Normal else FontWeight.SemiBold, fontSize = 12.sp,
        color = if (cost == null) Muted else Ink)
}

/**
 * Picks what to add: a stock item or a sub-recipe (by group), or, when nothing fits and [allowNameOnly], the typed name only.
 * [stockAllowed] false (the dish's amounts couldn't be loaded) offers names only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IngredientPickerDialog(
    choices: List<IngredientChoiceDto>?,
    choicesError: String?,
    stockAllowed: Boolean,
    alreadyAdded: Set<String>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onPick: (IngredientChoiceDto) -> Unit,
    onPickName: (String) -> Unit,
    // A sub-recipe is made only from stock items and other sub-recipes, so it never offers a name on its own.
    allowNameOnly: Boolean = true
) {
    val isPhone = isPhoneMenuWindow()
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val available = if (stockAllowed) choices.orEmpty().filter { it.key !in alreadyAdded } else emptyList()
    val groups = available.map { it.group }.distinct()
    val results = available
        .filter { group == null || it.group == group }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val typed = query.trim()
    val offerName = allowNameOnly && typed.isNotEmpty() && available.none { it.name.equals(typed, ignoreCase = true) } &&
        "NAME:${typed.lowercase()}" !in alreadyAdded

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .then(if (isPhone) Modifier.safeDrawingPadding().imePadding() else Modifier)
                .padding(if (isPhone) 16.dp else 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(minOf(maxWidth, 560.dp))
                    // One size whatever the filter shows, so the window doesn't jump between chips (user, 2026-10-30).
                    .height(if (isPhone) minOf(520.dp, maxHeight * 0.9f) else minOf(520.dp, maxHeight))
                    .shadow(24.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = Muted)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.take(150) },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester).padding(vertical = 14.dp),
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Inter(), fontSize = 15.sp, color = Ink),
                        cursorBrush = SolidColor(Ink),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(if (stockAllowed) "Search items and sub-recipes" else "Type an ingredient name",
                                    fontFamily = Inter(), fontSize = 15.sp, color = Placeholder)
                            }
                            inner()
                        }
                    )
                }
                HorizontalDivider(color = Border)
                if (groups.size > 1) {
                    val chips: @Composable () -> Unit = {
                        GroupChip("All", group == null) { group = null }
                        groups.forEach { candidate -> GroupChip(groupLabel(candidate), group == candidate) { group = candidate } }
                    }
                    // On a computer every chip shows, wrapping to a second line (sliding a row with a mouse is awkward);
                    // a phone keeps the row that slides under the finger.
                    if (isPhone) Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) { chips() }
                    else FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) { chips() }
                    HorizontalDivider(color = Border)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    when {
                        stockAllowed && choices == null && choicesError == null -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Olive, strokeWidth = 2.dp)
                        }
                        stockAllowed && choicesError != null -> Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Stock items couldn't be loaded: $choicesError", fontFamily = Inter(), fontSize = 13.sp, color = Danger)
                            TextButton(onClick = onRetry) { Text("Try again", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Olive) }
                        }
                        stockAllowed && choices.orEmpty().isEmpty() -> Text(
                            if (allowNameOnly) "No stock items yet. Add them in Admin Hub → Inventory, or type a name to show guests."
                            else "No items yet. Add what you buy in Inventory → Items first.",
                            Modifier.padding(12.dp), fontFamily = Inter(), fontSize = 13.sp, color = Muted
                        )
                        stockAllowed && results.isEmpty() && !offerName -> Text(
                            if (allowNameOnly) "Nothing matches. Type the name to add it for guests only." else "Nothing matches. Try another word.", Modifier.padding(12.dp),
                            fontFamily = Inter(), fontSize = 13.sp, color = Muted
                        )
                    }
                    results.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(choice) }.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IngredientPicture(choice.name, choice.kind, choice.imageUrl)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(choice.name, fontFamily = Inter(), fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(groupLabel(choice.group), fontFamily = Inter(), fontSize = 12.sp, color = Muted)
                            }
                            Text("per ${unitShort(choice.baseUnit)}", fontFamily = Inter(), fontSize = 12.sp, color = Muted)
                        }
                    }
                    if (offerName) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPickName(typed) }.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KindBadge(Icons.AutoMirrored.Outlined.Label, active = false)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Add “$typed”", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Olive)
                                Text("Name only: guests see it, nothing is taken from stock", fontFamily = Inter(), fontSize = 12.sp, color = Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Olive else Surface)
            .border(1.dp, if (selected) Olive else Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        fontFamily = Inter(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = if (selected) Color.White else Ink
    )
}

/** How much of [name] one portion (or batch: [hint]) uses, in a unit it can be measured in ([units]); shows what that amount costs. */
@Composable
internal fun IngredientAmountDialog(
    name: String,
    units: List<UnitCostDto>,
    startQuantity: String,
    startUnit: String,
    saveText: String,
    onDismiss: () -> Unit,
    onSave: (quantity: String, unit: String) -> Unit,
    hint: String = "How much one portion uses. It's taken from stock when the dish is served.",
    // Changing a line already there: Save waits until the amount or unit changed.
    onlyWhenChanged: Boolean = false
) {
    var quantity by remember { mutableStateOf(startQuantity) }
    var unit by remember { mutableStateOf(startUnit) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    val valid = isValidIngredientAmount(quantity)
    val unitCost = units.firstOrNull { it.unit == unit }?.cost
    MenuNestedDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = Ink,
        textContentColor = Muted,
        title = { Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(hint, fontFamily = Inter(), fontSize = 13.sp, color = Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.replace(',', '.').take(12) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Amount") },
                        placeholder = { Text("e.g. 200") },
                        singleLine = true,
                        isError = quantity.isNotBlank() && !valid,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Olive, unfocusedBorderColor = Border, cursorColor = Ink)
                    )
                    Box(Modifier.weight(1f)) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp).clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Border, RoundedCornerShape(8.dp))
                                .clickable(enabled = units.size > 1) { unitMenuOpen = true }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(unitShort(unit), Modifier.weight(1f), fontFamily = Inter(), fontSize = 14.sp, color = Ink)
                            if (units.size > 1) Icon(Icons.Outlined.ExpandMore, contentDescription = "Change unit", tint = Muted)
                        }
                        DropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                            units.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(unitShort(option.unit), fontFamily = Inter()) },
                                    onClick = { unit = option.unit; unitMenuOpen = false }
                                )
                            }
                        }
                    }
                }
                Text(
                    when {
                        quantity.isNotBlank() && !valid -> "Use a number above 0 with at most 3 decimals, like 200 or 0.25."
                        valid && unitCost != null -> "This amount costs ${money(quantity.trim().toDouble() * unitCost)} at today's stock price."
                        else -> " "
                    },
                    fontFamily = Inter(), fontSize = 12.sp, color = if (quantity.isNotBlank() && !valid) Danger else Muted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(quantity.trim(), unit) },
                enabled = valid && (!onlyWhenChanged || quantity.trim() != startQuantity || unit != startUnit),
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = Olive)
            ) {
                Text(saveText, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Muted)
            }
        }
    )
}
