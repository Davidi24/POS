package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.INVENTORY_UNITS
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.MAX_RECIPE_INSTRUCTIONS
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_CODE
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_NAME
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_TEXT
import com.saporini.mobile_desktop.admin.inventory.MenuChoice
import com.saporini.mobile_desktop.admin.inventory.RECIPE_TYPES
import com.saporini.mobile_desktop.admin.inventory.RecipeDto
import com.saporini.mobile_desktop.admin.inventory.RecipesScreenModel
import com.saporini.mobile_desktop.admin.inventory.RecipesState
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.SearchableSelect
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToggleRow
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter

/** The recipe editor, the ingredient editor and the archive question, driven by the recipes model. */
@Composable
internal fun RecipeDialogs(state: RecipesState, model: RecipesScreenModel, inventory: InventoryState) {
    if (state.draft != null) RecipeEditorDialog(state, model)
    if (state.component != null && state.open != null) ComponentDialog(state, model, inventory)
    if (state.confirmArchive != null) {
        ConfirmDialog("Archive ${state.open?.name ?: "this recipe"}?",
            "It stops taking stock when its dish is sold and leaves the active list. Its history and cost stay, and you can find it under Archived.",
            "Archive", model::confirmArchive, model::dismissArchive, danger = true, busy = state.saving)
    }
}

@Composable
private fun RecipeEditorDialog(state: RecipesState, model: RecipesScreenModel) {
    val draft = state.draft ?: return
    val dish = draft.recipeType == "FINISHED_DISH"
    AppDialog(
        if (draft.id == null) "New recipe" else "Edit ${draft.name.ifBlank { "recipe" }}", model::cancelRecipe,
        subtitle = "What goes in comes next: add ingredients after saving.", busy = state.saving, maxWidth = 680.dp,
        buttons = {
            KitButton("Cancel", model::cancelRecipe, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton(if (draft.id == null) "Create recipe" else "Save", model::saveRecipe, icon = Icons.Outlined.Check, loading = state.saving)
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        Text("Kind", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RECIPE_TYPES.forEach { type ->
                ChoiceTile(recipeTypeIcon(type), recipeTypeLabel(type), recipeTypeHint(type), recipeTypeColor(type), draft.recipeType == type,
                    Modifier.weight(1f)) { model.change { it.copy(recipeType = type, menuItemId = if (type == "FINISHED_DISH") it.menuItemId else null) } }
            }
        }
        TextInput(draft.name, { v -> model.change { it.copy(name = v) } }, label = "Name", required = true, icon = Icons.AutoMirrored.Outlined.MenuBook,
            placeholder = if (dish) "Margherita" else "Tomato sauce", maxLength = MAX_STOCK_NAME)
        if (dish) {
            val chosen = state.menuChoices.firstOrNull { it.id == draft.menuItemId }
            SearchableSelect<MenuChoice>(chosen, state.menuChoices, { it.name }, { choice ->
                model.change { it.copy(menuItemId = choice.id, name = it.name.ifBlank { choice.name }) }
            }, label = "Dish on the menu", required = true, icon = Icons.Outlined.Restaurant, detailOf = { "${it.menuName} · ${it.sectionName}" },
                placeholder = if (state.menuChoices.isEmpty()) "No dishes found on your menus" else "Choose the dish",
                hint = "Selling this dish takes the ingredients below from stock.")
        }
        FieldPair(
            { m -> TextInput(draft.code, { v -> model.change { it.copy(code = v) } }, m, label = "Code", optional = true, icon = Icons.Outlined.Badge,
                maxLength = MAX_STOCK_CODE) },
            { m -> SelectInput(draft.status, if (draft.status == "ARCHIVED") listOf("DRAFT", "ACTIVE", "ARCHIVED") else listOf("DRAFT", "ACTIVE"),
                ::recipeStatusLabel, { v -> model.change { it.copy(status = v) } }, m, label = "Status",
                hint = if (draft.status == "ACTIVE") "In use: sales take stock by it" else "Drafts don't take stock yet") }
        )
        Caption("Batch and time")
        FieldPair(
            { m -> TextInput(draft.yieldText, { v -> model.change { it.copy(yieldText = v, yieldUnit = it.yieldUnit ?: "PORTION") } }, m, label = "Makes",
                optional = true, icon = Icons.Outlined.Scale, keyboardType = KeyboardType.Decimal, placeholder = if (dish) "1" else "5",
                hint = "How much one batch gives") },
            { m -> SelectInput(draft.yieldUnit, listOf<String?>(null) + INVENTORY_UNITS, { it?.let { u -> unitShort(u) } ?: "Choose" },
                { v -> model.change { it.copy(yieldUnit = v) } }, m, label = "Unit", enabled = draft.yieldText.isNotBlank()) }
        )
        FieldPair(
            { m -> TextInput(draft.prepText, { v -> model.change { it.copy(prepText = v.filter(Char::isDigit)) } }, m, label = "Prep time", optional = true,
                icon = Icons.Outlined.Timer, suffix = "min", keyboardType = KeyboardType.Number) },
            { m -> TextInput(draft.cookText, { v -> model.change { it.copy(cookText = v.filter(Char::isDigit)) } }, m, label = "Cooking time", optional = true,
                icon = Icons.Outlined.Timer, suffix = "min", keyboardType = KeyboardType.Number) }
        )
        TextInput(draft.description, { v -> model.change { it.copy(description = v) } }, label = "Description", optional = true, singleLine = false,
            minLines = 2, maxLength = MAX_STOCK_TEXT)
        TextInput(draft.instructions, { v -> model.change { it.copy(instructions = v) } }, label = "Method", optional = true, singleLine = false,
            minLines = 5, maxLength = MAX_RECIPE_INSTRUCTIONS, placeholder = "One step per line",
            hint = "Each line shows as a numbered step.")
    }
}

/** Adds or changes one ingredient: a stock item or another recipe, how much, and how much of it is lost. */
@Composable
private fun ComponentDialog(state: RecipesState, model: RecipesScreenModel, inventory: InventoryState) {
    val draft = state.component ?: return
    val recipe = state.open ?: return
    // Which kind is being picked; only the dialog needs it until something is chosen.
    var fromRecipe by remember { mutableStateOf(draft.childRecipeId != null) }
    val item = inventory.item(draft.inventoryItemId)
    val child = state.recipes.firstOrNull { it.id == draft.childRecipeId }
    val existing = recipe.components.any {
        (draft.inventoryItemId != null && it.inventoryItemId == draft.inventoryItemId) || (draft.childRecipeId != null && it.childRecipeId == draft.childRecipeId)
    }
    // The units the chosen thing can be measured in; anything else couldn't be priced or taken from stock.
    val units = when {
        item != null -> compatibleUnits(item.baseUnit)
        child?.yieldUnit != null -> compatibleUnits(child.yieldUnit)
        else -> INVENTORY_UNITS
    }
    AppDialog(if (existing) "Change ingredient" else "Add ingredient", model::cancelComponent, subtitle = "In ${recipe.name}",
        busy = state.saving, maxWidth = 600.dp,
        buttons = {
            KitButton("Cancel", model::cancelComponent, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton(if (existing) "Save" else "Add", model::saveComponent, icon = Icons.Outlined.Check, loading = state.saving)
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        if (!existing) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceTile(Icons.Outlined.Inventory2, "Stock item", "Something you buy and count", Kit.Green, !fromRecipe, Modifier.weight(1f)) {
                    fromRecipe = false
                    model.changeComponent { it.copy(childRecipeId = null) }
                }
                ChoiceTile(Icons.AutoMirrored.Outlined.MenuBook, "Another recipe", "A sauce, dough or other prep", Kit.Purple, fromRecipe, Modifier.weight(1f)) {
                    fromRecipe = true
                    model.changeComponent { it.copy(inventoryItemId = null) }
                }
            }
        }
        if (fromRecipe) {
            SearchableSelect<RecipeDto>(child, state.subRecipeChoices, { it.name }, { picked ->
                model.changeComponent { it.copy(childRecipeId = picked.id, inventoryItemId = null,
                    unit = picked.yieldUnit?.takeIf { u -> u in INVENTORY_UNITS } ?: it.unit) }
            }, label = "Recipe", required = true, icon = Icons.AutoMirrored.Outlined.MenuBook, enabled = !existing,
                detailOf = { r -> listOfNotNull(recipeTypeLabel(r.recipeType), r.yieldQuantity?.let { "makes ${plain(it.value)} ${unitShort(r.yieldUnit)}" }).joinToString(" · ") },
                placeholder = "Choose a recipe")
        } else {
            SearchableSelect<StockItemDto>(item, inventory.items.filter { it.active }, { it.name }, { picked ->
                model.changeComponent { it.copy(inventoryItemId = picked.id, childRecipeId = null,
                    unit = if (it.unit in compatibleUnits(picked.baseUnit)) it.unit else picked.baseUnit) }
            }, label = "Stock item", required = true, icon = Icons.Outlined.Inventory2, enabled = !existing,
                detailOf = { "${itemTypeLabel(it.itemType)} · ${money(it.costPerUnit, null)} / ${unitShort(it.baseUnit)}" },
                placeholder = if (inventory.items.isEmpty()) "Add stock items first" else "Choose an item")
        }
        FieldPair(
            { m -> TextInput(draft.quantityText, { v -> model.changeComponent { it.copy(quantityText = v) } }, m, label = "How much", required = true,
                keyboardType = KeyboardType.Decimal, placeholder = "120", suffix = unitShort(draft.unit)) },
            { m -> SelectInput(draft.unit, units, { "${unitShort(it)}" }, { v -> model.changeComponent { it.copy(unit = v) } }, m, label = "Unit",
                hint = item?.let { "Stock is counted in ${unitShort(it.baseUnit)}" }) }
        )
        TextInput(draft.lossText, { v -> model.changeComponent { it.copy(lossText = v) } }, label = "Waste", optional = true, suffix = "%",
            keyboardType = KeyboardType.Decimal, placeholder = "0", hint = "Peel, bones, trimming: what's lost before it goes in.")
        // What it really takes from stock, so the waste number makes sense.
        val quantity = draft.quantityText.trim().replace(',', '.').toDoubleOrNull()
        val loss = draft.lossText.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        if (quantity != null && quantity > 0 && loss > 0 && loss < 100) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.GreenSoft).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Scale, null, Modifier.size(18.dp), tint = Kit.Green)
                Text("Takes ${trimmed(withWaste(quantity, loss))} ${unitShort(draft.unit)} from stock to end up with ${trimmed(quantity)} ${unitShort(draft.unit)}.",
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
            }
        }
        ToggleRow("Optional", draft.optional, { v -> model.changeComponent { it.copy(optional = v) } },
            detail = "Left out on request; still counted in the cost.")
        TextInput(draft.notes, { v -> model.changeComponent { it.copy(notes = v) } }, label = "Notes",
            optional = true, placeholder = "Diced, at room temperature…", maxLength = MAX_STOCK_TEXT)
    }
}

private fun plain(text: String): String = if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text

/** A big selectable tile with an icon, a title and a short line. */
@Composable
private fun ChoiceTile(icon: ImageVector, title: String, detail: String, tone: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 96.dp), shape = RoundedCornerShape(12.dp),
        color = if (selected) tone.copy(alpha = 0.08f) else Color.White, border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) tone else Kit.Border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(tone.copy(alpha = 0.14f)), Alignment.Center) {
                    Icon(icon, null, Modifier.size(17.dp), tint = tone)
                }
                Box(Modifier.weight(1f))
                if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = tone)
            }
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
            Text(detail, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Muted)
        }
    }
}
