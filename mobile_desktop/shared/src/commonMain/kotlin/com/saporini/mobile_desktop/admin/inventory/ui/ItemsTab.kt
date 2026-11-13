package com.saporini.mobile_desktop.admin.inventory.ui

import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.TextInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.outlined.Add
import com.saporini.mobile_desktop.admin.inventory.RecipesState
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.INVENTORY_ITEM_TYPES
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.ScrollList
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.RemoteImage
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_stock_items
import kotlin.time.Clock
import androidx.compose.foundation.layout.heightIn
import com.saporini.mobile_desktop.admin.inventory.ITEM_KINDS
import com.saporini.mobile_desktop.admin.inventory.MAX_KIND_NAME
import com.saporini.mobile_desktop.admin.inventory.UNCATEGORIZED_KIND
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.Caption
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.Scale
import kotlin.math.roundToLong
import com.saporini.mobile_desktop.core.format.moneyText

// Inventory → Items: the catalogue of what the restaurant buys and counts, each item once. Creating, editing and
// checking items lives here; how much is where (places, amounts, running low) is the Stock tab's job.

private enum class ItemStatus(val label: String) { IN_USE("In use"), OFF("Not in use"), ALL("All items") }

/** Things an item may be missing; each one is a filter. */
private enum class ItemCheck(val label: String, val detail: String, val icon: ImageVector) {
    NO_COST("No cost yet", "needed for stock value and dish costs", Icons.Outlined.Euro),
    NO_SUPPLIER("No supplier", "who to order it from", Icons.Outlined.LocalShipping),
    NO_REORDER("No reorder point", "when it counts as running low", Icons.Outlined.TrackChanges),
    NO_PHOTO("No photo", "helps staff find it on the shelf", Icons.Outlined.AddPhotoAlternate),
    // User, 2026-10-30. Made-here items have no barcode to scan.
    NO_BARCODE("No barcode", "scanning finds it at once", Icons.Outlined.QrCodeScanner),
    NOT_COUNTED("Not counted", "salt, napkins and the like", Icons.Outlined.Sell);

    fun matches(item: StockItemDto): Boolean = when (this) {
        NO_COST -> item.costPerUnit.asDouble() <= 0.0
        // Made-here items have no supplier and aren't counted themselves: their ingredients are.
        NO_SUPPLIER -> item.supplierName.isNullOrBlank() && item.recipeId == null
        NO_REORDER -> item.trackInventory && item.reorderPoint == null
        NO_PHOTO -> item.imageUrl == null
        NO_BARCODE -> item.barcode.isNullOrBlank() && item.recipeId == null
        NOT_COUNTED -> !item.trackInventory && item.recipeId == null
    }
}

private enum class ItemSort(val label: String) {
    NAME("Name A–Z"), PRICEY("Most expensive"), CHEAP("Least expensive"), RECENT("Recently changed")
}

/** The kind filter's value for items made here (they have a sub-recipe), whatever their kind. */
private const val MADE_HERE = "MADE_HERE"

/** What to do with sub-recipes from Items: add one, or open a made-here item's. */
private class SubRecipeActions(val canEdit: Boolean, val recipes: RecipesState, val add: () -> Unit, val open: (String) -> Unit)

@Composable
internal fun ItemsTab(
    state: InventoryState,
    model: InventoryScreenModel,
    recipes: RecipesState,
    onAddSubRecipe: () -> Unit,
    onOpenSubRecipe: (String) -> Unit,
    size: ScreenSize,
    modifier: Modifier
) {
    val subRecipes = SubRecipeActions(state.canEdit && recipes.canEdit, recipes, onAddSubRecipe, onOpenSubRecipe)
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf(ItemStatus.IN_USE) }
    var check by rememberSaveable { mutableStateOf<ItemCheck?>(null) }
    var sort by rememberSaveable { mutableStateOf(ItemSort.NAME) }
    var cards by rememberSaveable { mutableStateOf(true) }
    // Cost per unit between these (either can be open).
    var priceMin by rememberSaveable { mutableStateOf<Double?>(null) }
    var priceMax by rememberSaveable { mutableStateOf<Double?>(null) }
    // Switched-off items are always loaded; the model only needs to know when the list asks for them.
    LaunchedEffect(status) { model.showInactive(status != ItemStatus.IN_USE) }
    LaunchedEffect(state.itemKinds) {
        if (kind != null && kind != MADE_HERE && state.itemKinds.none { it.key == kind }) kind = null
    }
    // A click on an item only shows it; changing it goes through Edit (user, 2026-10-30).
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    state.item(viewing)?.let { item -> ItemViewDialog(item, state, model, subRecipes) { viewing = null } }
    val inStatus = state.items.filter {
        when (status) { ItemStatus.IN_USE -> it.active; ItemStatus.OFF -> !it.active; ItemStatus.ALL -> true }
    }
    val words = state.search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val shown = inStatus.filter { item ->
        val cost = item.costPerUnit.asDouble()
        (kind == null || (if (kind == MADE_HERE) item.recipeId != null else item.kindKey() == kind)) && (check?.matches(item) ?: true) &&
            (priceMin == null || cost >= priceMin!!) && (priceMax == null || cost <= priceMax!!) && words.all { word ->
            listOfNotNull(item.name, item.code, item.barcode, item.supplierName, item.supplierSku).joinToString(" ").lowercase().contains(word)
        }
    }.let { list ->
        when (sort) {
            ItemSort.NAME -> list.sortedBy { it.name.lowercase() }
            // Items without a cost go last either way.
            ItemSort.PRICEY -> list.sortedWith(compareBy<StockItemDto> { it.costPerUnit.asDouble() <= 0.0 }.thenByDescending { it.costPerUnit.asDouble() })
            ItemSort.CHEAP -> list.sortedWith(compareBy<StockItemDto> { it.costPerUnit.asDouble() <= 0.0 }.thenBy { it.costPerUnit.asDouble() })
            ItemSort.RECENT -> list.sortedByDescending { it.updatedAt.orEmpty() }
        }
    }
    // A phone has no side panels: show, needs a look, sort and cards/list fold into one options button.
    val phoneOptions: @Composable () -> Unit = {
        PhoneOptionsButton(Icons.Outlined.Tune, "Show and sort", active = listOfNotNull(check, status.takeIf { it != ItemStatus.IN_USE }).size) { close ->
            OptionGroup("Show")
            ItemStatus.entries.forEach { option ->
                OptionLine(option.label, status == option, { status = option; close() }, count = state.items.count {
                    when (option) { ItemStatus.IN_USE -> it.active; ItemStatus.OFF -> !it.active; ItemStatus.ALL -> true }
                })
            }
            OptionGroup("Needs a look")
            ItemCheck.entries.forEach { option ->
                val count = inStatus.count(option::matches)
                OptionLine(option.label, check == option, { check = if (check == option) null else option; close() }, icon = option.icon,
                    color = if (count > 0 && option != ItemCheck.NOT_COUNTED) Kit.Amber else Kit.Grey, count = count)
            }
            OptionGroup("Sort by")
            ItemSort.entries.forEach { option -> OptionLine(option.label, sort == option, { sort = option; close() }) }
            OptionGroup("Show as")
            OptionLine("Cards", cards, { cards = true; close() }, icon = Icons.Outlined.GridView)
            OptionLine("List", !cards, { cards = false; close() }, icon = Icons.AutoMirrored.Outlined.ViewList)
        }
    }
    val catalog: @Composable (Modifier) -> Unit = { m ->
        CatalogPanel(state, model, subRecipes, size, shown, kind, cards, sort, priceMin, priceMax, onKind = { kind = it }, onCards = { cards = it },
            onSort = { sort = it }, onPrice = { from, to -> priceMin = from; priceMax = to }, phoneOptions = phoneOptions, onView = { viewing = it }, modifier = m)
    }
    if (size.isPhone) {
        catalog(modifier)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ScrollColumn(Modifier.width(if (size.isDesktop) 300.dp else 260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // The restaurant's kinds (user, 2026-10-30): the built-in ones it kept, its own, then Uncategorized. New kind adds
            // one, a kind's ⋮ removes it (its items go to Uncategorized). The kinds scroll after about five and a half.
            var naming by remember { mutableStateOf(false) }
            var newName by remember { mutableStateOf("") }
            OverviewPanel(Icons.Outlined.Category, "Kinds", Modifier.fillMaxWidth(),
                action = if (state.canEdit) ({ SmallLink("New kind", Icons.Outlined.Add) { naming = !naming } }) else null) {
                if (naming) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextInput(newName, { newName = it }, Modifier.weight(1f), placeholder = "Spices", maxLength = MAX_KIND_NAME)
                        KitButton("Add", { model.addKind(ITEM_KINDS, newName); naming = false; newName = "" },
                            enabled = newName.isNotBlank() && !state.saving)
                    }
                }
                FilterRow(Icons.Outlined.Category, "All kinds", inStatus.size, Kit.Green, kind == null) { kind = null }
                // Things the kitchen makes (sauces, doughs), whatever kind of item they are.
                FilterRow(Icons.Outlined.SoupKitchen, "Made here", inStatus.count { it.recipeId != null }, Kit.Purple, kind == MADE_HERE,
                    detail = "with a sub-recipe") { kind = if (kind == MADE_HERE) null else MADE_HERE }
                ScrollColumn(Modifier.fillMaxWidth().heightIn(max = KindListHeight), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.itemKinds.forEach { option ->
                        val key = option.key
                        val label = option.itemLabel()
                        FilterRow(kindKeyIcon(key), label, inStatus.count { it.kindKey() == key }, kindKeyColor(key), kind == key,
                            trailing = if (state.canEdit && option.code != UNCATEGORIZED_KIND) ({
                                RowActionsMenu(listOf(RowAction("Remove kind", Icons.Outlined.Delete, danger = true) {
                                    model.askDeleteKind(ITEM_KINDS, option, label)
                                }))
                            }) else null) { kind = if (kind == key) null else key }
                    }
                }
            }
            OverviewPanel(Icons.Outlined.ToggleOff, "Show", Modifier.fillMaxWidth()) {
                ItemStatus.entries.forEach { option ->
                    val count = state.items.count { when (option) { ItemStatus.IN_USE -> it.active; ItemStatus.OFF -> !it.active; ItemStatus.ALL -> true } }
                    FilterRow(when (option) { ItemStatus.IN_USE -> Icons.Outlined.CheckCircle; ItemStatus.OFF -> Icons.Outlined.ToggleOff; ItemStatus.ALL -> Icons.Outlined.Inventory2 },
                        option.label, count, if (option == ItemStatus.OFF) Kit.Grey else Kit.Green, status == option) { status = option }
                }
            }
            OverviewPanel(Icons.Outlined.TrackChanges, "Needs a look", Modifier.fillMaxWidth()) {
                ItemCheck.entries.forEach { option ->
                    val count = inStatus.count(option::matches)
                    FilterRow(option.icon, option.label, count, if (count > 0 && option != ItemCheck.NOT_COUNTED) Kit.Amber else Kit.Grey, check == option,
                        detail = option.detail) { check = if (check == option) null else option }
                }
            }
        }
        catalog(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun CatalogPanel(
    state: InventoryState,
    model: InventoryScreenModel,
    subRecipes: SubRecipeActions,
    size: ScreenSize,
    shown: List<StockItemDto>,
    kind: String?,
    cards: Boolean,
    sort: ItemSort,
    priceMin: Double?,
    priceMax: Double?,
    onKind: (String?) -> Unit,
    onCards: (Boolean) -> Unit,
    onSort: (ItemSort) -> Unit,
    onPrice: (Double?, Double?) -> Unit,
    phoneOptions: @Composable () -> Unit,
    onView: (String) -> Unit,
    modifier: Modifier
) {
    OverviewPanel(Icons.AutoMirrored.Outlined.ViewList, "Items", modifier, count = shown.size,
        titleExtra = kind?.let { if (it == MADE_HERE) "Made here" else state.itemKinds.firstOrNull { k -> k.key == it }?.itemLabel() ?: kindShort(it) },
        bottomPadding = 0.dp, flat = size.isPhone,
        // A phone: more room between the search, Add House-made, the kinds and the cards (user, 2026-10-30).
        gap = if (size.isPhone) 14.dp else 8.dp,
        countBesideTitle = true, // the count beside "Items", away from the button (user, 2026-10-30)
        action = if (subRecipes.canEdit) ({ AddSubRecipeButton(subRecipes.add) }) else null) {
        // The search keeps a sensible width; the filters and switches sit on the right.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SearchField(state.search, model::search, Modifier.weight(1f, fill = false).widthIn(max = 380.dp).padding(end = if (size.isPhone) 8.dp else 10.dp),
                placeholder = if (size.isPhone) "Search items" else "Search name, code, barcode or supplier",
                height = if (size.isPhone) PhoneControlHeight else PageControlHeight)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (size.isPhone) 8.dp else 10.dp)) {
                PriceFilterButton(priceMin, priceMax, onPrice, compact = size.isPhone)
                if (size.isPhone) phoneOptions()
                else {
                    HeaderDropdown(sort.label, Icons.AutoMirrored.Outlined.Sort, Modifier.width(184.dp), ItemSort.entries.map { it.label },
                        { label -> ItemSort.entries.firstOrNull { it.label == label }?.let(onSort) })
                    OverviewTabs(listOf("Cards", "List"), if (cards) "Cards" else "List", { onCards(it == "Cards") }, Modifier.width(150.dp), height = PageControlHeight)
                }
            }
        }
        if (size.isPhone) {
            if (subRecipes.canEdit) AddSubRecipeButton(subRecipes.add, Modifier.fillMaxWidth())
            FilterPills(listOf<Pair<String?, String>>(null to "All kinds", MADE_HERE to "Made here") + state.itemKinds.map { it.key to it.itemLabel() }, kind, onKind)
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                if (state.items.isEmpty()) {
                    OverviewEmpty("No items yet", "Add each thing you buy once (New item, on the right), with the unit you count it in and its cost. " +
                        "Things you make, like a sauce, are added with Add House-made.", Icons.Outlined.Inventory2, image = Res.drawable.inventory_stock_items)
                } else {
                    OverviewEmpty("Nothing matches", "Try another word, kind or filter.", Icons.Outlined.Inventory2)
                }
            }
            return@OverviewPanel
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            if (cards) {
                // As many cards to a row as fit at about 210 dp each; every row is as tall as its tallest card.
                val columns = (maxWidth / 210.dp).toInt().coerceIn(if (size.isPhone) 2 else 1, 6)
                ScrollList(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(shown.chunked(columns), key = { _, row -> row.first().id }) { rowIndex, row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEachIndexed { i, item ->
                                ItemCardTile(item, state, model, subRecipes, compact = size.isPhone, number = rowIndex * columns + i + 1,
                                    onOpen = { onView(item.id) }, modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                ScrollList(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                    itemsIndexed(shown, key = { _, it -> it.id }) { index, item ->
                        ItemListRow(item, state, model, subRecipes, compact = !size.isDesktop, number = (index + 1).takeIf { !size.isPhone },
                            onOpen = { onView(item.id) })
                    }
                }
            }
        }
    }
}

private fun itemActions(item: StockItemDto, state: InventoryState, model: InventoryScreenModel, subRecipes: SubRecipeActions): List<RowAction> =
    when {
        // Made here: its sub-recipe is where it changes (name, kind, batch, ingredients, cost).
        item.recipeId != null -> listOfNotNull(RowAction(if (subRecipes.canEdit) "Edit sub-recipe" else "See sub-recipe", Icons.AutoMirrored.Outlined.MenuBook) {
            subRecipes.open(item.recipeId)
        }, RowAction("Delete", Icons.Outlined.Delete, danger = true) { model.askDeleteItem(item.id) }.takeIf { state.canEdit })
        !state.canEdit -> emptyList()
        else -> listOf(
            RowAction("Edit", Icons.Outlined.Edit) { model.editItem(item.id) },
            // A new item filled in from this one (user, 2026-10-30: "Add similar", not "Duplicate").
            RowAction("Add similar", Icons.Outlined.ContentCopy) { model.duplicateItem(item.id) },
            // Asks first; refused (with the reason) while a recipe or an open count uses it.
            RowAction("Delete", Icons.Outlined.Delete, danger = true) { model.askDeleteItem(item.id) }
        )
    }


/** "Made here · 4 ingredients": what a made-here item's sub-recipe holds, as far as it's loaded. */
private fun madeHereLine(item: StockItemDto, subRecipes: SubRecipeActions): String? {
    val recipeId = item.recipeId ?: return null
    val count = subRecipes.recipes.recipes.firstOrNull { it.id == recipeId }?.components?.size
    return listOfNotNull("Made here", count?.let { "$it ${if (it == 1) "ingredient" else "ingredients"}" }).joinToString(" · ")
}

/** The clear "Add House-made" button: for things the kitchen makes from its own ingredients. */
@Composable
private fun AddSubRecipeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.height(36.dp), shape = RoundedCornerShape(8.dp), color = Kit.GreenSoft,
        border = BorderStroke(1.dp, Kit.Green.copy(alpha = 0.5f))) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = Kit.Green)
            Text("Add House-made", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Green, maxLines = 1)
        }
    }
}

/** One item as a card: its picture on a soft background in its kind's colour, then name, code or supplier, and cost. */
@Composable
private fun ItemCardTile(item: StockItemDto, state: InventoryState, model: InventoryScreenModel, subRecipes: SubRecipeActions, compact: Boolean,
                         number: Int, onOpen: () -> Unit, modifier: Modifier) {
    val tone = if (item.active) item.kindColor() else Kit.Grey
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Surface(
        onClick = onOpen,
        modifier = modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(14.dp), color = Color.White,
        border = BorderStroke(1.dp, if (hovered) tone.copy(alpha = 0.5f) else Kit.RowBorder),
        shadowElevation = if (hovered) 4.dp else 1.dp, interactionSource = interaction
    ) {
        Column(Modifier.fillMaxHeight()) {
            Box(Modifier.fillMaxWidth().height(if (compact) 104.dp else 128.dp).background(tone.copy(alpha = 0.07f))) {
                ItemPictureLarge(item, Modifier.align(Alignment.Center).size(if (compact) 72.dp else 92.dp))
                KindChip(item, Modifier.align(Alignment.TopStart).padding(10.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { RowActionsMenu(itemActions(item, state, model, subRecipes)) }
                if (!item.active) StatusPill("Not in use", Kit.Grey, Modifier.align(Alignment.BottomStart).padding(10.dp))
                else if (item.recipeId != null) StatusPill("Made here", Kit.Purple, Modifier.align(Alignment.BottomStart).padding(10.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = if (compact) 12.dp else 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // Two lines for the name on a phone, where cards are narrow; the cost always sits at the bottom of the card.
                Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, color = Kit.Ink,
                    maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis)
                (madeHereLine(item, subRecipes) ?: listOfNotNull(item.code?.takeIf { it.isNotBlank() }, item.supplierName?.takeIf { it.isNotBlank() })
                    .joinToString(" · ")).takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(money(item.costPerUnit, null), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        color = if (item.costPerUnit.asDouble() <= 0.0) Kit.Faint else Kit.Ink, maxLines = 1)
                    Text(" / ${unitShort(item.baseUnit)}", Modifier.padding(bottom = 2.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    if (!item.trackInventory && item.recipeId == null && !compact) Tag("Not counted")
                    // Its place in the list, quietly in the corner (user, 2026-10-30: "so I know I'm at 28").
                    Text("#$number", Modifier.padding(start = 8.dp, bottom = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp, color = Kit.Faint, maxLines = 1)
                }
            }
        }
    }
}

/** One item as a list line: picture, name and kind, cost and supplier, levels, last change. */
@Composable
private fun ItemListRow(item: StockItemDto, state: InventoryState, model: InventoryScreenModel, subRecipes: SubRecipeActions, compact: Boolean,
                        number: Int?, onOpen: () -> Unit) {
    StripCard(if (!item.active) Kit.Grey else item.kindColor(), minHeight = 68.dp, faded = !item.active, onClick = onOpen) {
        // Its place in the list, like on Stock.
        number?.let { Text("$it", Modifier.width(24.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Faint,
            textAlign = TextAlign.End, maxLines = 1) }
        StockItemThumb(item)
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.kindLabel(), madeHereLine(item, subRecipes), item.code?.takeIf { it.isNotBlank() && item.recipeId == null },
                item.barcode?.takeIf { it.isNotBlank() }?.let { "barcode $it" }).joinToString(" · "),
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CardDivider()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${money(item.costPerUnit, null)} / ${unitShort(item.baseUnit)}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
            Text(if (item.recipeId != null) "cost from its ingredients" else item.supplierName?.takeIf { it.isNotBlank() } ?: "No supplier",
                fontFamily = Inter(), fontSize = 11.sp, color = if (item.recipeId == null && item.supplierName.isNullOrBlank()) Kit.Faint else Kit.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!compact) {
            CardDivider()
            Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(when {
                    item.recipeId != null -> "Made here"
                    item.trackInventory -> "Reorder at ${item.reorderPoint?.let(::plainNumber) ?: "–"}"
                    else -> "Not counted"
                }, fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
                Text(item.updatedAt?.let { "changed ${agoText(it, Clock.System.now(), RestaurantTime.zone)}" } ?: "Full at ${item.parLevel?.let(::plainNumber) ?: "–"}",
                    fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
            }
        }
        if (!item.active) StatusPill("Off", Kit.Grey)
        RowActionsMenu(itemActions(item, state, model, subRecipes))
    }
}

/** "12", "2.5", "0.35": a price without trailing zeros, for the filter's label and fields. */
private fun plainPrice(value: Double): String {
    val cents = kotlin.math.round(value * 100).toLong()
    return if (cents % 100 == 0L) "${cents / 100}" else "${cents / 100}.${(cents % 100).toString().padStart(2, '0').trimEnd('0')}"
}

private fun priceOrNull(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

/** Cost per unit from / up to: typed in, or one of the quick ranges. Green while a range is set, with a cross to clear it. */
@Composable
private fun PriceFilterButton(min: Double?, max: Double?, onChange: (Double?, Double?) -> Unit, compact: Boolean) {
    var open by remember { mutableStateOf(false) }
    var fromText by remember(open) { mutableStateOf(min?.let(::plainPrice).orEmpty()) }
    var toText by remember(open) { mutableStateOf(max?.let(::plainPrice).orEmpty()) }
    val active = min != null || max != null
    val label = when {
        min != null && max != null -> "€${plainPrice(min)} – €${plainPrice(max)}"
        min != null -> "From €${plainPrice(min)}"
        max != null -> "Up to €${plainPrice(max)}"
        else -> "Any price"
    }
    val from = priceOrNull(fromText)
    val to = priceOrNull(toText)
    val problem = when {
        fromText.isNotBlank() && from == null || toText.isNotBlank() && to == null -> "Enter amounts like 4.50"
        from != null && to != null && from > to -> "\"At least\" can't be more than \"At most\""
        else -> null
    }
    Box {
        Surface(onClick = { open = true }, modifier = Modifier.height(if (compact) PhoneControlHeight else PageControlHeight),
            shape = RoundedCornerShape(8.dp), color = if (active) Kit.GreenSoft else Color.White, border = BorderStroke(1.dp, if (active) Kit.Green else Kit.Border)) {
            // On a phone the € alone makes a square as tall as the search.
            Row(Modifier.padding(horizontal = if (compact) 11.5.dp else 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Euro, "Price", Modifier.size(17.dp), tint = if (active) Kit.Green else Kit.Ink)
                if (!compact) Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (active) Kit.Green else Kit.Ink, maxLines = 1)
                if (active) Icon(Icons.Outlined.Close, "Any price", Modifier.size(16.dp).clip(RoundedCornerShape(50)).clickable { onChange(null, null) }, tint = Kit.Green)
            }
        }
        DropdownMenu(open, { open = false }, Modifier.background(Color.White).width(320.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cost per unit", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextInput(fromText, { fromText = it.take(10) }, Modifier.weight(1f), label = "At least", placeholder = "0", suffix = "€",
                        keyboardType = KeyboardType.Decimal)
                    TextInput(toText, { toText = it.take(10) }, Modifier.weight(1f), label = "At most", placeholder = "any", suffix = "€",
                        keyboardType = KeyboardType.Decimal)
                }
                problem?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Danger) }
                // Quick ranges fill both fields.
                listOf(listOf(null to 1.0, 1.0 to 10.0), listOf(10.0 to 50.0, 50.0 to null)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (low, high) ->
                            val text = when { low == null -> "Under €${plainPrice(high!!)}"; high == null -> "Over €${plainPrice(low)}"; else -> "€${plainPrice(low)} – €${plainPrice(high)}" }
                            val picked = low == from && high == to
                            Text(text, Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (picked) Kit.Green else Kit.Tint)
                                .clickable { fromText = low?.let(::plainPrice).orEmpty(); toText = high?.let(::plainPrice).orEmpty() }
                                .padding(vertical = 8.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                color = if (picked) Color.White else Kit.Ink, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KitButton("Clear", { onChange(null, null); open = false }, Modifier.weight(1f), style = ButtonStyle.SECONDARY)
                    KitButton("Show", { onChange(from, to); open = false }, Modifier.weight(1f), enabled = problem == null)
                }
            }
        }
    }
}

/** The kind as a small white chip with its icon, on the card's picture. */
@Composable
private fun KindChip(item: StockItemDto, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(item.kindIcon(), null, Modifier.size(13.dp), tint = item.kindColor())
        Text(item.kindLabel(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Ink, maxLines = 1)
    }
}

@Composable
private fun Tag(text: String) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(horizontal = 8.dp, vertical = 3.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Muted, maxLines = 1)
}

/** The item's backend-saved photo at card size, or its kind icon when it has no server photo. */
@Composable
internal fun ItemPictureLarge(item: StockItemDto, modifier: Modifier) {
    RemoteImage(item.imageUrl, item.name, modifier.clip(RoundedCornerShape(12.dp))) { BigKindIcon(item) }
}

@Composable
private fun BigKindIcon(item: StockItemDto) {
    IconTile(item.kindIcon(), if (item.active) item.kindColor() else Kit.Grey, 56.dp)
}

// About five and a half kinds; the rest scroll (user, 2026-10-30: "a little smaller and scrollable").
private val KindListHeight = 272.dp

/** Short kind names for chips and filters ("Prepared" instead of "Prepared (sauce, dough…)"). */
internal fun kindShort(type: String?): String = when (type) {
    "PREPARED_COMPONENT" -> "Prepared"
    else -> itemTypeLabel(type)
}

/** A small green text button with an icon, for panel headers and filter rows. */
@Composable
internal fun SmallLink(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = Kit.Green)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
    }
}

/**
 * What a click on an item shows (user, 2026-10-30: "when I click the item I just see it; only the Edit button updates it";
 * redone the same day after "this screen looks so bad", in the Inventory look): a band in the kind's colour with the
 * photo, kind and status and how much there is and what it's worth; three small tiles (cost, reorder at, full at);
 * supplier and codes; and where it is, place by place. Edit (or the sub-recipe) is a button for people who may change it.
 */
@Composable
private fun ItemViewDialog(item: StockItemDto, state: InventoryState, model: InventoryScreenModel, subRecipes: SubRecipeActions, onClose: () -> Unit) {
    val held = state.levelsByItem[item.id].orEmpty().filter { it.onHandQuantity.asDouble() > 0 }
    val unit = unitShort(item.baseUnit)
    val recipeId = item.recipeId
    val tone = if (item.active) item.kindColor() else Kit.Grey
    val cost = item.costPerUnit.asDouble()
    val total = held.sumOf { it.onHandQuantity.asDouble() }
    val worth = { amount: Double -> (amount * cost * 100).roundToLong() }
    // A house-made item's window only shows it (user, 2026-10-30: "read only, just exit"); its sub-recipe opens from the ⋮.
    val recipe = recipeId?.let { id -> subRecipes.recipes.recipes.firstOrNull { it.id == id } }
    AppDialog(
        item.name, onClose,
        subtitle = (if (recipeId != null) listOfNotNull("House-made", recipe?.components?.size?.let { "$it ${if (it == 1) "ingredient" else "ingredients"}" })
            else listOfNotNull(item.code?.takeIf { it.isNotBlank() }?.let { "Code $it" })).joinToString(" · ").ifEmpty { null },
        maxWidth = 640.dp,
        buttons = {
            KitButton("Close", onClose, style = ButtonStyle.SECONDARY)
            if (recipeId == null && state.canEdit) KitButton("Edit", { onClose(); model.editItem(item.id) }, icon = Icons.Outlined.Edit)
        }
    ) {
        val narrow = LocalNarrowDialog.current
        // The band: photo, kind and status, and the stock figures.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tone.copy(alpha = 0.08f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(if (narrow) 84.dp else 112.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), Alignment.Center) {
                ItemPictureLarge(item, Modifier.size(if (narrow) 68.dp else 92.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    KindChip(item, Modifier)
                    when {
                        !item.active -> StatusPill("Not in use", Kit.Grey)
                        recipeId != null -> StatusPill("House-made", Kit.Purple)
                        !item.trackInventory -> StatusPill("Not counted", Kit.Grey)
                        else -> StatusPill("In use", Kit.Green)
                    }
                }
                if (recipeId != null) {
                    // House-made: made in batches from the ingredients below, not kept in stock itself.
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("House-made", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 22.sp else 26.sp, color = Kit.Purple, maxLines = 1)
                    }
                    Text(listOfNotNull(recipe?.yieldQuantity?.let { "a batch makes ${plainNumber(it)} ${unitShort(recipe?.yieldUnit ?: item.baseUnit)}" },
                        recipe?.theoreticalCost?.takeIf { it.asDouble() > 0 }?.let { "costs ${money(it, null)}" }).joinToString(" · ")
                        .ifEmpty { "Made here from its ingredients" }, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
                } else if (item.trackInventory) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(trimmed(total), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 22.sp else 26.sp, color = Kit.Ink, maxLines = 1)
                        Text(" $unit in stock", Modifier.padding(bottom = 3.dp), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted, maxLines = 1)
                    }
                    Text(listOfNotNull(if (cost > 0) "worth ${moneyText(worth(total), null)}" else "no cost yet",
                        if (held.isEmpty()) "nowhere yet" else "in ${if (held.size == 1) "1 place" else "${held.size} places"}").joinToString(" · "),
                        fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
                } else {
                    Text("Not counted, so no stock is kept for it.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                }
            }
        }
        // Three small tiles: what one costs and when it counts as low or full.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ViewTile(Icons.Outlined.Euro, Kit.Green, "Cost", if (cost > 0) "${money(item.costPerUnit, null)} / $unit" else "Not set", Modifier.weight(1f).fillMaxHeight())
            ViewTile(Icons.Outlined.TrackChanges, Kit.Danger, "Reorder at", item.reorderPoint?.let { "${plainNumber(it)} $unit" } ?: "Not set",
                Modifier.weight(1f).fillMaxHeight())
            ViewTile(Icons.Outlined.Inventory2, Kit.Blue, "Full at", item.parLevel?.let { "${plainNumber(it)} $unit" } ?: "Not set",
                Modifier.weight(1f).fillMaxHeight())
        }
        // House-made: what it's made from, instead of a supplier and stock places.
        if (recipeId != null) {
            ViewSection("Made from") {
                val components = recipe?.components.orEmpty().sortedBy { it.displayOrder }
                if (components.isEmpty()) {
                    Text(if (recipe == null) "Its ingredients show once the sub-recipes have loaded." else "No ingredients yet.",
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                }
                components.forEach { part ->
                    val stockItem = part.inventoryItemId?.let { state.item(it) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (stockItem != null) StockItemThumb(stockItem, 30.dp) else IconTile(Icons.Outlined.SoupKitchen, Kit.Purple, 30.dp)
                        Text(part.name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${plainNumber(part.quantity)} ${unitShort(part.unit)}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = Kit.Ink, maxLines = 1)
                    }
                }
            }
        } else ViewSection("Supplier and codes") {
            ViewLine(Icons.Outlined.LocalShipping, "Supplier", item.supplierName?.takeIf { it.isNotBlank() } ?: if (recipeId != null) "Made here" else null)
            ViewLine(Icons.Outlined.Sell, "Their code", item.supplierSku?.takeIf { it.isNotBlank() })
            ViewLine(Icons.Outlined.QrCodeScanner, "Barcode", item.barcode?.takeIf { it.isNotBlank() })
            ViewLine(Icons.Outlined.Scale, "Counted in", unitLabel(item.baseUnit))
        }
        // Where it is (bought items; house-made ones aren't kept in stock).
        if (recipeId == null) ViewSection("Where it is") {
            if (held.isEmpty()) {
                Text(if (item.trackInventory) "Nothing in stock anywhere right now." else "Not counted, so no stock is kept for it.",
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
            }
            held.forEach { level ->
                val place = state.location(level.locationId)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (place != null) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(placeTypeColor(place.locationType).copy(alpha = 0.12f)), Alignment.Center) {
                            PlaceIconImage(place, Modifier.size(25.dp))
                        }
                    } else IconTile(placeTypeIcon(null), placeTypeColor(null), 30.dp)
                    Text(level.locationName ?: place?.name ?: "Place", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${plainNumber(level.onHandQuantity)} $unit", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                        if (cost > 0) Text(moneyText(worth(level.onHandQuantity.asDouble()), null), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                    }
                }
            }
        }
        listOfNotNull(item.description?.takeIf { it.isNotBlank() }?.let { "About it" to it },
            item.storageNotes?.takeIf { it.isNotBlank() }?.let { "Storage" to it }).forEach { (title, text) ->
            ViewSection(title) {
                Text(text, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Ink)
            }
        }
    }
}

/** A small tile in the item window: an icon in its colour, a short name and the value. */
@Composable
private fun ViewTile(icon: ImageVector, color: Color, label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconTile(icon, color, 30.dp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
            Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (value == "Not set") Kit.Faint else Kit.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A titled white box with soft lines inside, in the item window. */
@Composable
private fun ViewSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(vertical = 4.dp)) { content() }
    }
}

/** One fact in a [ViewSection]: icon, name on the left, the value (or a quiet "None") on the right. */
@Composable
private fun ViewLine(icon: ImageVector, label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(17.dp), tint = Kit.Muted)
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted, maxLines = 1)
        Text(value ?: "None", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (value == null) Kit.Faint else Kit.Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
