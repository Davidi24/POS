package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.INVENTORY_ITEM_TYPES
import com.saporini.mobile_desktop.admin.inventory.INVENTORY_UNITS
import com.saporini.mobile_desktop.admin.inventory.MAX_RECIPE_INSTRUCTIONS
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_NAME
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_TEXT
import com.saporini.mobile_desktop.admin.inventory.RecipesScreenModel
import com.saporini.mobile_desktop.admin.inventory.RecipesState
import com.saporini.mobile_desktop.admin.inventory.SubRecipeDraft
import com.saporini.mobile_desktop.admin.inventory.SubRecipeLine
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToggleRow
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.data.dto.IngredientChoiceDto
import com.saporini.mobile_desktop.pos.menu.ui.item.IngredientAmountDialog
import com.saporini.mobile_desktop.pos.menu.ui.item.IngredientPickerDialog
import com.saporini.mobile_desktop.pos.menu.ui.item.IngredientPicture
import com.saporini.mobile_desktop.pos.menu.ui.item.PREPARATION_INGREDIENT
import com.saporini.mobile_desktop.pos.menu.ui.item.key
import com.saporini.mobile_desktop.pos.menu.ui.item.preferredUnit
import kotlin.math.round

// Items → "Add sub-recipe" (or a made-here item opened): something the kitchen makes from its own ingredients. It's
// filled in like a dish's ingredients in the menu: name it, say what kind of item it is and how much one batch makes,
// then add what goes in (stock items or other sub-recipes) one by one with how much. Its cost comes from them.

@Composable
internal fun SubRecipeDialog(state: RecipesState, model: RecipesScreenModel) {
    val draft = state.subRecipe ?: return
    val isNew = draft.recipeId == null
    val editable = state.canEdit
    var picking by remember { mutableStateOf(false) }
    var amountFor by remember { mutableStateOf<Pair<IngredientChoiceDto, SubRecipeLine?>?>(null) }
    AppDialog(
        if (isNew) "Add House-made" else draft.name.ifBlank { "Sub-recipe" }, model::cancelSubRecipe,
        subtitle = if (isNew) "Something you make from your own ingredients, like a sauce or a dough. It becomes an item you can use in dishes."
        else "Made here: its cost comes from its ingredients.",
        busy = state.saving, maxWidth = 720.dp,
        buttons = {
            if (editable) {
                KitButton("Cancel", model::cancelSubRecipe, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                KitButton(if (isNew) "Create" else "Save", model::saveSubRecipe, icon = Icons.Outlined.Check, loading = state.saving,
                    enabled = !draft.loading && (isNew || draft != state.subRecipeLoaded))
            } else {
                KitButton("Close", model::cancelSubRecipe, style = ButtonStyle.SECONDARY)
            }
        }
    ) {
        if (draft.loading) {
            Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Kit.Green, strokeWidth = 2.dp)
                Text("  Opening the sub-recipe…", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
            }
            return@AppDialog
        }
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        val change: ((SubRecipeDraft) -> SubRecipeDraft) -> Unit = { if (editable) model.changeSubRecipe(it) }

        // 1. What it is
        ItemPhotoField(draft.imageUrl, draft.photo, { photo -> change { it.copy(photo = photo) } }, enabled = editable && !state.saving) {
            TextInput(draft.name, { v -> change { it.copy(name = v) } }, label = "Name", required = true, icon = Icons.AutoMirrored.Outlined.MenuBook,
                placeholder = "Tomato sauce", maxLength = MAX_STOCK_NAME, enabled = editable)
        }
        SelectInput(draft.itemType, INVENTORY_ITEM_TYPES, ::itemTypeLabel, { v -> change { it.copy(itemType = v) } }, label = "Kind of item",
            icon = Icons.Outlined.Category, enabled = editable, hint = "Where it shows in Items, next to what you buy")

        // 2. How much one batch makes
        Caption("One batch makes")
        FieldPair(
            { m -> TextInput(draft.yieldText, { v -> change { it.copy(yieldText = v) } }, m, label = "Amount", required = true, icon = Icons.Outlined.Scale,
                keyboardType = KeyboardType.Decimal, placeholder = "5", enabled = editable) },
            { m -> SelectInput(draft.yieldUnit, INVENTORY_UNITS, { "${unitLabel(it)} (${unitShort(it)})" }, { v -> change { it.copy(yieldUnit = v) } }, m,
                label = "Unit", enabled = editable, hint = "Its cost is worked out per ${unitShort(draft.yieldUnit)}") },
            wide = true
        )

        // 3. What goes in
        Caption("Ingredients for one batch")
        if (draft.lines.isEmpty()) {
            Text("Add what goes in: items you buy or other sub-recipes, with how much of each.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
        draft.lines.forEach { line ->
            val choice = state.choices?.firstOrNull { it.key == line.key }
            SubRecipeLineRow(line, choice, editable,
                onEdit = { choice?.let { amountFor = it to line } },
                onRemove = { change { d -> d.copy(lines = d.lines.filterNot { it.key == line.key }) } })
        }
        if (editable) {
            Row(Modifier.clip(RoundedCornerShape(9.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(9.dp))
                .clickable { picking = true }.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Add, null, Modifier.size(16.dp), tint = Kit.Green)
                Text("Add ingredient", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Green)
            }
        }
        BatchCostLine(draft, state.choices)

        // How it's made, then anything else worth knowing; both optional, always in sight.
        TextInput(draft.instructions, { v -> change { it.copy(instructions = v) } }, label = "Method", optional = true, singleLine = false,
            minLines = 5, maxLength = MAX_RECIPE_INSTRUCTIONS, icon = Icons.AutoMirrored.Outlined.Notes, placeholder = "One step per line",
            hint = "Each line shows as a numbered step.", enabled = editable)
        TextInput(draft.description, { v -> change { it.copy(description = v) } }, label = "Notes", optional = true, singleLine = false,
            minLines = 2, maxLength = MAX_STOCK_TEXT, placeholder = "Keeps 3 days in the fridge, allergens…", enabled = editable)
        if (!isNew) ToggleRow("In use", draft.active, { v -> change { it.copy(active = v) } },
            detail = "Switched off, it can't be added to dishes or other house-made items.")
    }

    if (picking) {
        IngredientPickerDialog(
            choices = state.choices, choicesError = state.choicesError, stockAllowed = true,
            alreadyAdded = draft.lines.map { it.key }.toSet(), onRetry = model::retryChoices,
            onDismiss = { picking = false },
            onPick = { choice -> picking = false; amountFor = choice to null },
            onPickName = {}, allowNameOnly = false
        )
    }
    amountFor?.let { (choice, line) ->
        IngredientAmountDialog(
            name = choice.name, units = choice.units,
            startQuantity = line?.quantity.orEmpty(), startUnit = line?.unit ?: preferredUnit(choice),
            saveText = if (line == null) "Add" else "Save",
            onDismiss = { amountFor = null },
            onSave = { quantity, unit ->
                amountFor = null
                val picked = SubRecipeLine(choice.kind, choice.id, choice.name, quantity, unit, choice.imageUrl)
                model.changeSubRecipe { d ->
                    d.copy(lines = if (d.lines.any { it.key == picked.key }) d.lines.map { if (it.key == picked.key) picked else it } else d.lines + picked)
                }
            },
            hint = "How much one batch uses.",
            onlyWhenChanged = line != null
        )
    }
}

/** One ingredient: its picture, name, how much and what that costs; click to change the amount, × to take it out. */
@Composable
private fun SubRecipeLineRow(line: SubRecipeLine, choice: IngredientChoiceDto?, editable: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    val cost = lineCost(line, choice)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(9.dp))
        .clickable(enabled = editable && choice != null, onClick = onEdit).padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IngredientPicture(line.name, line.kind, choice?.imageUrl ?: line.imageUrl)
        Column(Modifier.weight(1f)) {
            Text(line.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(if (line.kind == PREPARATION_INGREDIENT) "Sub-recipe" else null, "${line.quantity} ${unitShort(line.unit)}",
                cost?.let { "costs ${moneyText(round(it * 100).toLong(), null)}" }).joinToString(" · "),
                fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (editable) IconButton(onRemove, Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "Take out ${line.name}", Modifier.size(16.dp), tint = Kit.Muted) }
    }
}

private fun lineCost(line: SubRecipeLine, choice: IngredientChoiceDto?): Double? {
    val quantity = line.quantity.trim().toDoubleOrNull() ?: return null
    val unitCost = choice?.units?.firstOrNull { it.unit == line.unit }?.cost ?: return line.loadedCost
    return quantity * unitCost
}

/** "One batch costs €12.40 · €2.48 per l", from today's prices; "about" when a line can't be priced. */
@Composable
private fun BatchCostLine(draft: SubRecipeDraft, choices: List<IngredientChoiceDto>?) {
    if (draft.lines.isEmpty()) return
    val costs = draft.lines.map { line -> lineCost(line, choices?.firstOrNull { it.key == line.key }) }
    val total = costs.filterNotNull().sum()
    val complete = costs.none { it == null }
    val makes = draft.yieldText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.GreenSoft).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.Euro, null, Modifier.size(18.dp), tint = Kit.Green)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(buildString {
                append(if (complete) "One batch costs " else "One batch costs about ")
                append(moneyText(round(total * 100).toLong(), null))
                makes?.let { append(" · ${moneyText(round(total * 100 / it).toLong(), null)} per ${unitShort(draft.yieldUnit)}") }
            }, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Green)
            Text(if (makes == null) "Say how much a batch makes to see what one ${unitShort(draft.yieldUnit)} costs." else "From today's prices of its ingredients.",
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        }
    }
}
