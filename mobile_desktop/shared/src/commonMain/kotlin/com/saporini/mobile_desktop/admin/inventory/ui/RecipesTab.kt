package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.RECIPE_TYPES
import com.saporini.mobile_desktop.admin.inventory.RecipeComponentDto
import com.saporini.mobile_desktop.admin.inventory.RecipeDto
import com.saporini.mobile_desktop.admin.inventory.RecipesScreenModel
import com.saporini.mobile_desktop.admin.inventory.RecipesState
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.ChartPalette
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.components.percentText
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.quantityText
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import kotlin.time.Clock

// ---- Labels ----

internal fun recipeTypeLabel(type: String?): String = when (type) {
    "FINISHED_DISH" -> "Dish"
    "PREP_BATCH" -> "Prep batch"
    "SUB_RECIPE" -> "Sub-recipe"
    else -> "Recipe"
}

internal fun recipeTypeHint(type: String): String = when (type) {
    "FINISHED_DISH" -> "A dish on the menu. Selling it takes its ingredients from stock."
    "PREP_BATCH" -> "Made ahead in a batch: sauces, doughs, stocks."
    else -> "A part used inside other recipes."
}

internal fun recipeTypeIcon(type: String?): ImageVector = when (type) {
    "FINISHED_DISH" -> Icons.Outlined.Restaurant
    "PREP_BATCH" -> Icons.Outlined.Kitchen
    else -> Icons.AutoMirrored.Outlined.MenuBook
}

internal fun recipeTypeColor(type: String?): Color = when (type) {
    "FINISHED_DISH" -> Kit.Green
    "PREP_BATCH" -> Kit.Blue
    else -> Kit.Purple
}

internal fun recipeStatusLabel(status: String?): String = when (status) {
    "ACTIVE" -> "In use"
    "ARCHIVED" -> "Archived"
    else -> "Draft"
}

internal fun recipeStatusColor(status: String?): Color = when (status) {
    "ACTIVE" -> Kit.Green
    "ARCHIVED" -> Kit.Grey
    else -> Kit.Amber
}

// ---- Cost estimates ----
// The server prices the whole recipe. For the per-ingredient split the screen does the same sum for each line,
// with the same unit rules (mass and volume convert, other units only to themselves).

private val MassInGrams = mapOf("GRAM" to 1.0, "KILOGRAM" to 1000.0, "OUNCE" to 28.349523125, "POUND" to 453.59237)
private val VolumeInMl = mapOf("MILLILITER" to 1.0, "LITER" to 1000.0)

/** The units a quantity of [base] can be written in. */
internal fun compatibleUnits(base: String?): List<String> = when (base) {
    null -> emptyList()
    in MassInGrams -> MassInGrams.keys.toList()
    in VolumeInMl -> VolumeInMl.keys.toList()
    else -> listOf(base)
}

private fun convert(quantity: Double, from: String, to: String?): Double? = when {
    to == null -> null
    from == to -> quantity
    from in MassInGrams && to in MassInGrams -> quantity * MassInGrams.getValue(from) / MassInGrams.getValue(to)
    from in VolumeInMl && to in VolumeInMl -> quantity * VolumeInMl.getValue(from) / VolumeInMl.getValue(to)
    else -> null
}

/** How much to take so that [quantity] is left after the waste. */
internal fun withWaste(quantity: Double, lossPercent: Double): Double = if (lossPercent in 0.0..99.99) quantity / (1 - lossPercent / 100) else quantity

/** What one line of [recipe] costs, in cents; null when it can't be worked out here. */
private fun lineCostCents(line: RecipeComponentDto, inventory: InventoryState, recipes: List<RecipeDto>): Long? {
    val used = withWaste(line.quantity.asDouble(), line.yieldLossPercent.asDouble())
    if (line.inventoryItemId != null) {
        val item = inventory.item(line.inventoryItemId) ?: return null
        val inBase = convert(used, line.unit, item.baseUnit) ?: return null
        return kotlin.math.round(inBase * item.costPerUnit.asDouble() * 100).toLong()
    }
    val child = recipes.firstOrNull { it.id == line.childRecipeId } ?: return null
    val yield = child.yieldQuantity.asDouble().takeIf { it > 0 } ?: return null
    val inYield = convert(used, line.unit, child.yieldUnit) ?: return null
    return child.theoreticalCost?.let { kotlin.math.round(inYield / yield * it.asDouble() * 100).toLong() }
}

// ---- Tab ----

@Composable
internal fun RecipesTab(state: RecipesState, model: RecipesScreenModel, inventory: InventoryState, size: ScreenSize, modifier: Modifier) {
    if (!state.canRead) return
    val list: @Composable (Modifier) -> Unit = { m -> RecipeList(state, model, m) }
    val detail: @Composable (Modifier) -> Unit = { m ->
        val open = state.open
        if (open == null) {
            Surface(m, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    OverviewEmpty("Choose a recipe", "Its ingredients, cost per portion and method show here.", Icons.AutoMirrored.Outlined.MenuBook,
                        image = Res.drawable.settings_orders_kitchen,
                        action = if (state.canEdit) ({ KitButton("New recipe", { model.newRecipe() }, icon = Icons.Outlined.Add) }) else null)
                }
            }
        } else RecipeDetail(open, state, model, inventory, size, m)
    }
    when {
        size.isPhone -> if (state.open == null) list(modifier) else detail(modifier)
        else -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            list(Modifier.width(if (size.isDesktop) 420.dp else 320.dp).fillMaxHeight())
            detail(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun RecipeList(state: RecipesState, model: RecipesScreenModel, modifier: Modifier) {
    val shown = state.visible
    OverviewPanel(Icons.AutoMirrored.Outlined.ViewList, "Recipes", modifier, count = shown.size) {
        SearchField(state.search, model::search, Modifier.fillMaxWidth(), placeholder = "Search recipe, code or dish", height = PageControlHeight)
        FilterPills(listOf<Pair<String?, String>>(null to "All kinds") + RECIPE_TYPES.map { it to recipeTypeLabel(it) }, state.type,
            { model.filter(it, state.status) })
        FilterPills(listOf<Pair<String?, String>>(null to "Any status", "ACTIVE" to "In use", "DRAFT" to "Draft", "ARCHIVED" to "Archived"), state.status,
            { model.filter(state.type, it) })
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty(if (state.recipes.isEmpty() && !state.loading) "No recipes yet" else "Nothing matches",
                    if (state.recipes.isEmpty()) "Write down what goes into each dish to see what it costs and keep stock right." else "Try another word or filter.",
                    Icons.AutoMirrored.Outlined.MenuBook)
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
                items(shown, key = { it.id }) { recipe ->
                    RecipeCard(recipe, selected = state.open?.id == recipe.id) { model.open(recipe.id) }
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeDto, selected: Boolean, onClick: () -> Unit) {
    val tone = recipeTypeColor(recipe.recipeType)
    StripCard(if (recipe.status == "ARCHIVED") Kit.Grey else tone, selected = selected, minHeight = 66.dp, chevron = true,
        faded = recipe.status == "ARCHIVED", onClick = onClick) {
        IconTile(recipeTypeIcon(recipe.recipeType), tone, 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(recipe.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(recipeTypeLabel(recipe.recipeType), recipe.menuItemName?.let { "for $it" },
                "${recipe.components.size} ${if (recipe.components.size == 1) "ingredient" else "ingredients"}").joinToString(" · "),
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(money(recipe.theoreticalCost, null), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
            if (recipe.status != "ACTIVE") Text(recipeStatusLabel(recipe.status), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                color = recipeStatusColor(recipe.status))
            else Text("cost", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        }
    }
}

@Composable
private fun RecipeDetail(recipe: RecipeDto, state: RecipesState, model: RecipesScreenModel, inventory: InventoryState, size: ScreenSize, modifier: Modifier) {
    val tone = recipeTypeColor(recipe.recipeType)
    val lines = recipe.components.sortedBy { it.displayOrder }
    val costs = lines.associate { it.id to lineCostCents(it, inventory, state.recipes) }
    val known = costs.values.filterNotNull().sum().coerceAtLeast(0)
    val totalCents = recipe.theoreticalCost.asDouble().let { kotlin.math.round(it * 100).toLong() }
    val editable = state.canEdit && recipe.status != "ARCHIVED"
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (size.isPhone) 14.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (size.isPhone) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Kit.Tint).clickable { model.open(null) }, Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to recipes", Modifier.size(20.dp), tint = Kit.Ink)
                    }
                }
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(tone.copy(alpha = 0.12f)), Alignment.Center) {
                    Icon(recipeTypeIcon(recipe.recipeType), null, Modifier.size(28.dp), tint = tone)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(recipe.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(recipeTypeLabel(recipe.recipeType), recipe.menuItemName?.let { "makes “$it”" }, recipe.code?.takeIf { it.isNotBlank() },
                        "version ${recipe.version}").joinToString(" · "), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                StatusPill(recipeStatusLabel(recipe.status), recipeStatusColor(recipe.status))
                if (state.canEdit && !size.isPhone) KitButton("Edit", model::editRecipe, icon = Icons.Outlined.Edit, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                RowActionsMenu(if (!state.canEdit) emptyList() else buildList {
                    if (size.isPhone) add(RowAction("Edit", Icons.Outlined.Edit, onClick = model::editRecipe))
                    if (recipe.status != "ARCHIVED") add(RowAction("Price again from today's costs", Icons.Outlined.Calculate, onClick = model::recalculateCost))
                    if (recipe.status != "ARCHIVED") add(RowAction("Archive", Icons.Outlined.Archive, danger = true, onClick = model::askArchive))
                }, busy = state.saving)
            }
            recipe.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Ink)
            }
            // Numbers
            val perUnit = recipe.yieldQuantity.asDouble().takeIf { it > 0 && recipe.theoreticalCost != null }
                ?.let { kotlin.math.round(totalCents / it).toLong() }
            val tiles: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> FactTile(Icons.Outlined.Euro, "Cost", money(recipe.theoreticalCost, null), "from today's stock costs", Kit.Green, m) },
                { m -> FactTile(Icons.Outlined.Scale, "Makes", recipe.yieldQuantity?.let { quantityText(it, recipe.yieldUnit) } ?: "1 serving",
                    perUnit?.let { "${moneyText(it, null)} per ${unitShort(recipe.yieldUnit)}" } ?: "no batch size", Kit.Blue, m) },
                { m -> FactTile(Icons.Outlined.Timer, "Time", minutesText((recipe.prepTimeMinutes ?: 0) + (recipe.cookTimeMinutes ?: 0)),
                    "${recipe.prepTimeMinutes ?: 0} prep · ${recipe.cookTimeMinutes ?: 0} cooking", Kit.Amber, m) }
            )
            if (size.isPhone) tiles.forEach { it(Modifier.fillMaxWidth()) }
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { tiles.forEach { it(Modifier.weight(1f)) } }

            // Ingredients
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ingredients", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                Spacer(Modifier.width(8.dp))
                Text("${lines.size}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                Spacer(Modifier.weight(1f))
                if (editable) KitButton("Add ingredient", { model.newComponent() }, icon = Icons.Outlined.Add, enabled = !state.saving)
            }
            if (lines.isEmpty()) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(vertical = 8.dp), Alignment.Center) {
                    OverviewEmpty("No ingredients yet", "Add the stock items and recipes that go in, with how much of each.", Icons.Outlined.Kitchen)
                }
            } else {
                if (known > 0) CostSplitBar(lines, costs, known)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lines.forEachIndexed { index, line ->
                        IngredientRow(line, ChartPalette[index % ChartPalette.size], costs[line.id], known, inventory, editable, state.saving, compact = size.isPhone,
                            onEdit = { editComponent(model, line) }, onRemove = { model.removeComponent(line.id) })
                    }
                }
                if (costs.values.any { it == null }) {
                    Text("Some lines can't be priced here (their unit doesn't convert to the stock unit, or the sub-recipe has no batch size).",
                        fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                }
            }

            // Method
            recipe.instructions?.takeIf { it.isNotBlank() }?.let { text ->
                Caption("Method")
                val steps = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    steps.forEachIndexed { index, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(tone.copy(alpha = 0.12f)), Alignment.Center) {
                                Text("${index + 1}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tone)
                            }
                            Text(step.removePrefix("${index + 1}.").removePrefix("${index + 1})").trim(), Modifier.weight(1f).padding(top = 3.dp),
                                fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Ink)
                        }
                    }
                }
            }
            recipe.updatedAt?.let {
                Text(listOfNotNull("Changed ${agoText(it, Clock.System.now(), RestaurantTime.zone)}", recipe.updatedByUserName?.let { name -> "by $name" })
                    .joinToString(" "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint)
            }
        }
    }
}

private fun minutesText(minutes: Int): String = when {
    minutes <= 0 -> "–"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** Opens the ingredient editor filled with [line], so saving replaces it. */
private fun editComponent(model: RecipesScreenModel, line: RecipeComponentDto) {
    model.newComponent(line.inventoryItemId, line.unit)
    model.changeComponent {
        it.copy(childRecipeId = line.childRecipeId, quantityText = plainNumber(line.quantity),
            lossText = line.yieldLossPercent?.let(::plainNumber).orEmpty(), optional = line.optionalComponent, notes = line.notes.orEmpty())
    }
}

@Composable
private fun FactTile(icon: ImageVector, label: String, value: String, detail: String, tone: Color, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(tone.copy(alpha = 0.07f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = tone)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
            Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink, maxLines = 1)
            Text(detail, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One bar split into each ingredient's share of the cost. */
@Composable
private fun CostSplitBar(lines: List<RecipeComponentDto>, costs: Map<String, Long?>, total: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Kit.Tint)) {
            lines.forEachIndexed { index, line ->
                val cents = costs[line.id] ?: 0
                if (cents > 0) Box(Modifier.weight(cents.toFloat()).fillMaxHeight().background(ChartPalette[index % ChartPalette.size]))
            }
        }
        val top = lines.withIndex().filter { (costs[it.value.id] ?: 0) > 0 }.maxByOrNull { costs[it.value.id] ?: 0 }
        top?.let { (_, line) ->
            Text("${line.name} is the biggest cost: ${percentText((costs[line.id] ?: 0).toFloat() / total)} of what can be priced here.",
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        }
    }
}

@Composable
private fun IngredientRow(
    line: RecipeComponentDto,
    color: Color,
    cents: Long?,
    total: Long,
    inventory: InventoryState,
    editable: Boolean,
    busy: Boolean,
    compact: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val sub = line.childRecipeId != null
    val loss = line.yieldLossPercent.asDouble()
    val item = inventory.item(line.inventoryItemId)
    StripCard(color, minHeight = 58.dp, onClick = if (editable) onEdit else null) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.name, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub) Tag("Recipe", Kit.Purple)
                if (line.optionalComponent) Tag("Optional", Kit.Grey)
                if (item != null && !item.active) Tag("Switched off", Kit.Danger)
            }
            Text(listOfNotNull(
                if (loss > 0) "${plainNumber(line.yieldLossPercent)}% waste, takes ${trimmed(withWaste(line.quantity.asDouble(), loss))} ${unitShort(line.unit)}" else null,
                line.notes?.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifEmpty { if (sub) "Made from its own recipe" else item?.let { itemTypeLabel(it.itemType) } ?: "Stock item" },
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(quantityText(line.quantity, line.unit), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
        if (!compact) {
            Column(Modifier.width(92.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cents?.let { moneyText(it, null) } ?: "–", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                Text(if (cents != null && total > 0) percentText(cents.toFloat() / total) else "not priced", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            }
        }
        RowActionsMenu(if (!editable) emptyList() else listOf(
            RowAction("Change", Icons.Outlined.Edit, onClick = onEdit),
            RowAction("Remove", Icons.Outlined.Delete, danger = true, onClick = onRemove)
        ), busy = busy)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = color, maxLines = 1)
}

/** 125.0 → "125", 12.3456 → "12.35". */
internal fun trimmed(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100
    val text = rounded.toString()
    return if (text.endsWith(".0")) text.dropLast(2) else text
}
