package com.saporini.mobile_desktop.gallery

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.saporini.mobile_desktop.admin.inventory.InventoryRepository
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.RecipeComponentRequestDto
import com.saporini.mobile_desktop.admin.inventory.RecipeDto
import com.saporini.mobile_desktop.admin.inventory.RecipeRequestDto
import com.saporini.mobile_desktop.admin.inventory.RecipesRepository
import com.saporini.mobile_desktop.admin.inventory.RecipesScreenModel
import com.saporini.mobile_desktop.admin.inventory.StockActivityDayDto
import com.saporini.mobile_desktop.admin.inventory.StockActivityDto
import com.saporini.mobile_desktop.admin.inventory.StockCountCreateRequestDto
import com.saporini.mobile_desktop.admin.inventory.StockCountDto
import com.saporini.mobile_desktop.admin.inventory.StockCountLineRequestDto
import com.saporini.mobile_desktop.admin.inventory.StockCountPageDto
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.StockItemRequestDto
import com.saporini.mobile_desktop.admin.inventory.StockLevelDto
import com.saporini.mobile_desktop.admin.inventory.StockLocationDto
import com.saporini.mobile_desktop.admin.inventory.StockLocationRequestDto
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.admin.inventory.StockMoveRequestDto
import com.saporini.mobile_desktop.admin.inventory.StockMovementDto
import com.saporini.mobile_desktop.admin.inventory.ui.InventoryContent
import com.saporini.mobile_desktop.admin.inventory.ui.ScanBarcodeDialog
import com.saporini.mobile_desktop.admin.inventory.ui.StartCountDialog
import com.saporini.mobile_desktop.admin.inventory.ui.InventoryView
import com.saporini.mobile_desktop.admin.inventory.SubRecipeDto
import com.saporini.mobile_desktop.admin.inventory.SubRecipeIngredientDto
import com.saporini.mobile_desktop.admin.inventory.SubRecipeLine
import com.saporini.mobile_desktop.pos.menu.data.dto.IngredientChoiceDto
import com.saporini.mobile_desktop.pos.menu.data.dto.UnitCostDto
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

// Look check only (screens round): renders Inventory → Overview (with and without data) and Items at phone, tablet, desktop and
// big-screen sizes into build/reports/gallery/overview-*.png. Pictures are tall so the whole scrolling page shows.

private fun dec(text: String) = OrderDecimal(text)
private fun minutesAgo(minutes: Int) = (Clock.System.now() - minutes.minutes).toString()

private class OverviewInventory(private val empty: Boolean) : InventoryRepository {
    private val items = if (empty) emptyList() else listOf(
        StockItemDto("Prosecco", null, "Prosecco DOC 750 ml", itemType = "ALCOHOL", baseUnit = "BOTTLE", costPerUnit = dec("7.80")),
        StockItemDto("Wine", null, "House red wine 750 ml", itemType = "ALCOHOL", baseUnit = "BOTTLE", costPerUnit = dec("5.50")),
        StockItemDto("Ribeye", null, "Ribeye steak", itemType = "INGREDIENT", baseUnit = "KILOGRAM", costPerUnit = dec("28.00")),
        StockItemDto("Seabass", null, "Sea bass fillet", itemType = "INGREDIENT", baseUnit = "KILOGRAM", costPerUnit = dec("21.00")),
        StockItemDto("Oil", null, "Extra virgin olive oil", itemType = "INGREDIENT", baseUnit = "LITER", costPerUnit = dec("7.50")),
        StockItemDto("Eggs", null, "Free-range eggs", itemType = "INGREDIENT", baseUnit = "EACH", costPerUnit = dec("0.32")),
        StockItemDto("Crab", null, "Crab meat, pasteurised", itemType = "INGREDIENT", baseUnit = "KILOGRAM", costPerUnit = dec("38.00")),
        StockItemDto("Cola", null, "Cola 330 ml can", itemType = "BEVERAGE", baseUnit = "EACH", costPerUnit = dec("0.60")),
        StockItemDto("Boxes", null, "Takeaway box, kraft", itemType = "PACKAGING", baseUnit = "EACH", costPerUnit = dec("0.25")),
        StockItemDto("Napkins", null, "Paper napkins (pack of 500)", itemType = "SUPPLY", baseUnit = "PACK", costPerUnit = dec("6.80"), trackInventory = false),
        // Made here: its sub-recipe r1 says what goes in.
        StockItemDto("Sauce", null, "Tomato sauce", itemType = "PREPARED_COMPONENT", baseUnit = "LITER", costPerUnit = dec("2.48"), trackInventory = false,
            recipeId = "r1")
    )
    private val places = if (empty) emptyList() else listOf(
        StockLocationDto("walk", "b1", null, "Walk-in cooler", "WALK_IN"), StockLocationDto("bar", "b1", null, "Bar", "BAR"),
        StockLocationDto("kit", "b1", null, "Kitchen", "KITCHEN"), StockLocationDto("dry", "b1", null, "Dry store", "DRY_STORAGE")
    )
    private val levels = if (empty) emptyList() else listOf(
        StockLevelDto("1", "bar", "Bar", "Prosecco", "Prosecco DOC 750 ml", dec("79")),
        StockLevelDto("2", "bar", "Bar", "Wine", "House red wine 750 ml", dec("96")),
        StockLevelDto("3", "walk", "Walk-in cooler", "Ribeye", "Ribeye steak", dec("14.6")),
        StockLevelDto("4", "walk", "Walk-in cooler", "Seabass", "Sea bass fillet", dec("16.4")),
        StockLevelDto("5", "kit", "Kitchen", "Oil", "Extra virgin olive oil", dec("44")),
        StockLevelDto("6", "kit", "Kitchen", "Eggs", "Free-range eggs", dec("42"), lowStock = true),
        StockLevelDto("7", "walk", "Walk-in cooler", "Crab", "Crab meat, pasteurised", dec("1"), lowStock = true),
        StockLevelDto("8", "bar", "Bar", "Cola", "Cola 330 ml can", dec("0"), lowStock = true),
        StockLevelDto("9", "dry", "Dry store", "Boxes", "Takeaway box, kraft", dec("400"))
    )
    private val moves = if (empty) emptyList() else listOf(
        StockMovementDto("m1", "walk", "Walk-in cooler", "Eggs", "Free-range eggs", movementType = "COUNT_ADJUSTMENT", quantityDelta = dec("-6"), unit = "EACH", occurredAt = minutesAgo(60 * 16)),
        StockMovementDto("m2", "bar", "Bar", "Wine", "House red wine 750 ml", movementType = "RECEIPT", quantityDelta = dec("24"), unit = "BOTTLE", occurredAt = minutesAgo(60 * 20)),
        StockMovementDto("m3", "kit", "Kitchen", "Oil", "Extra virgin olive oil", movementType = "SALE_CONSUMPTION", quantityDelta = dec("-0.5"), unit = "LITER", occurredAt = minutesAgo(90)),
        StockMovementDto("m4", "walk", "Walk-in cooler", "Seabass", "Sea bass fillet", movementType = "WASTE", quantityDelta = dec("-0.8"), unit = "KILOGRAM", occurredAt = minutesAgo(200)),
        StockMovementDto("m5", "walk", "Walk-in cooler", "Ribeye", "Ribeye steak", movementType = "TRANSFER_IN", quantityDelta = dec("4"), unit = "KILOGRAM", occurredAt = minutesAgo(400))
    )

    override suspend fun activity(restaurantId: String, days: Int): StockActivityDto {
        if (empty) return StockActivityDto("EUR", "2026-10-12", "2026-10-25", days = (12..25).map { StockActivityDayDto("2026-10-$it") })
        val used = listOf(120, 80, 95, 140, 210, 260, 180, 110, 90, 130, 150, 240, 280, 160)
        return StockActivityDto("EUR", "2026-10-12", "2026-10-25", received = dec("1420.45"), used = dec("2245.00"), wasted = dec("53.24"), changes = 87,
            days = (12..25).mapIndexed { i, day ->
                StockActivityDayDto("2026-10-$day", received = dec(if (i % 4 == 0) "310" else "0"), used = dec("${used[i]}"), wasted = dec(if (i % 5 == 0) "12" else "0"))
            })
    }

    override suspend fun items(restaurantId: String, includeInactive: Boolean) = items
    override suspend fun searchItems(restaurantId: String, keyword: String) = items
    override suspend fun saveItem(restaurantId: String, itemId: String?, request: StockItemRequestDto) = items.first()
    override suspend fun deactivateItem(restaurantId: String, itemId: String) {}
    override suspend fun locations(restaurantId: String, includeInactive: Boolean) = places
    override suspend fun saveLocation(restaurantId: String, locationId: String?, request: StockLocationRequestDto) = places.first()
    override suspend fun deactivateLocation(restaurantId: String, locationId: String) {}
    override suspend fun levels(restaurantId: String, locationId: String?, itemId: String?) = levels
    override suspend fun lowStock(restaurantId: String) = levels.filter { it.lowStock }
    override suspend fun move(restaurantId: String, kind: StockMoveKind, request: StockMoveRequestDto, requestKey: String) {}
    override suspend fun movements(restaurantId: String, type: String?, itemId: String?, page: Int, size: Int) = if (page == 0) moves.take(size) else emptyList()
    private val countList = if (empty) emptyList() else listOf(
        StockCountDto("c1", "b1", "walk", "Walk-in cooler", "CNT-2026-0012", "IN_PROGRESS", createdByUserName = "Giulia Rossi", updatedAt = minutesAgo(40),
            lines = listOf(
                com.saporini.mobile_desktop.admin.inventory.StockCountLineDto("l1", "Ribeye", "Ribeye steak", dec("14.6"), dec("14.2"), dec("-0.4"), "KILOGRAM"),
                com.saporini.mobile_desktop.admin.inventory.StockCountLineDto("l2", "Seabass", "Sea bass fillet", dec("16.4"), null, null, "KILOGRAM"),
                com.saporini.mobile_desktop.admin.inventory.StockCountLineDto("l3", "Crab", "Crab meat, pasteurised", dec("1"), dec("1"), dec("0"), "KILOGRAM"))),
        StockCountDto("c2", "b1", "bar", "Bar", "CNT-2026-0011", "COMPLETED", createdByUserName = "Marco Bianchi", updatedAt = minutesAgo(60 * 20),
            varianceValue = dec("-12.40"), lines = listOf(
                com.saporini.mobile_desktop.admin.inventory.StockCountLineDto("l4", "Wine", "House red wine 750 ml", dec("96"), dec("94"), dec("-2"), "BOTTLE"))),
        StockCountDto("c3", "b1", "dry", "Dry store", "CNT-2026-0010", "APPROVED", createdByUserName = "Davide Keci", approvedByUserName = "Davide Keci",
            updatedAt = minutesAgo(60 * 50), varianceValue = dec("3.20"), lines = listOf(
                com.saporini.mobile_desktop.admin.inventory.StockCountLineDto("l5", "Boxes", "Takeaway box, kraft", dec("390"), dec("400"), dec("10"), "EACH"))),
        StockCountDto("c4", "b1", "kit", "Kitchen", "CNT-2026-0013", "DRAFT", createdByUserName = "Giulia Rossi", updatedAt = minutesAgo(10),
            notes = "Before the weekend"),
        StockCountDto("c5", "b1", "bar", "Bar", "CNT-2026-0009", "CANCELLED", createdByUserName = "Marco Bianchi", updatedAt = minutesAgo(60 * 80))
    )
    override suspend fun counts(restaurantId: String, status: String?, page: Int, size: Int) =
        countList.filter { status == null || it.status == status }.let { shown -> StockCountPageDto(shown.take(size), 0, size, shown.size.toLong(), 1, shown.size > size) }
    override suspend fun count(restaurantId: String, countId: String): StockCountDto = countList.first { it.id == countId }
    override suspend fun createCount(restaurantId: String, request: StockCountCreateRequestDto): StockCountDto = error("unused")
    override suspend fun countLine(restaurantId: String, countId: String, itemId: String, request: StockCountLineRequestDto): StockCountDto = error("unused")
    override suspend fun removeCountLine(restaurantId: String, countId: String, lineId: String): StockCountDto = error("unused")
    override suspend fun countStep(restaurantId: String, countId: String, step: String): StockCountDto = error("unused")
}

private class NoRecipes : RecipesRepository {
    private val sauce = RecipeDto("r1", name = "Tomato sauce", recipeType = "PREP_BATCH", status = "ACTIVE", version = 3, yieldQuantity = dec("5"), yieldUnit = "LITER",
        prepTimeMinutes = 15, cookTimeMinutes = 45, theoreticalCost = dec("12.40"), instructions = "Soften the garlic in oil.\nAdd tomatoes, simmer 40 minutes.\nSeason and blend.",
        components = listOf(
            com.saporini.mobile_desktop.admin.inventory.RecipeComponentDto("rc1", "INVENTORY_ITEM", "Oil", "Extra virgin olive oil", quantity = dec("0.2"), unit = "LITER", displayOrder = 0),
            com.saporini.mobile_desktop.admin.inventory.RecipeComponentDto("rc2", "INVENTORY_ITEM", "Ribeye", "Ribeye steak", quantity = dec("0.5"), unit = "KILOGRAM", yieldLossPercent = dec("10"), displayOrder = 1)
        ))
    private val dough = RecipeDto("r2", name = "Pizza dough", recipeType = "SUB_RECIPE", status = "DRAFT", yieldQuantity = dec("10"), yieldUnit = "PORTION",
        theoreticalCost = dec("3.30"))
    override suspend fun recipes(restaurantId: String, type: String?, status: String?) = listOf(sauce, dough)
    override suspend fun recipe(restaurantId: String, recipeId: String): RecipeDto = listOf(sauce, dough).first { it.id == recipeId }
    override suspend fun save(restaurantId: String, recipeId: String?, request: RecipeRequestDto): RecipeDto = error("unused")
    override suspend fun putComponent(restaurantId: String, recipeId: String, request: RecipeComponentRequestDto): RecipeDto = error("unused")
    override suspend fun removeComponent(restaurantId: String, recipeId: String, componentId: String): RecipeDto = error("unused")
    override suspend fun archive(restaurantId: String, recipeId: String): RecipeDto = error("unused")
    override suspend fun recalculateCost(restaurantId: String, recipeId: String): RecipeDto = error("unused")
    override suspend fun subRecipeChoices(restaurantId: String, forRecipeId: String?) = listOf(
        IngredientChoiceDto("STOCK", "Oil", "Extra virgin olive oil", "INGREDIENT", "LITER", listOf(UnitCostDto("LITER", 7.5), UnitCostDto("MILLILITER", 0.0075))),
        IngredientChoiceDto("STOCK", "Eggs", "Free-range eggs", "INGREDIENT", "EACH", listOf(UnitCostDto("EACH", 0.32))),
        IngredientChoiceDto("STOCK", "Ribeye", "Ribeye steak", "INGREDIENT", "KILOGRAM", listOf(UnitCostDto("KILOGRAM", 28.0), UnitCostDto("GRAM", 0.028))),
        IngredientChoiceDto("PREPARATION", "r2", "Pizza dough", "PREPARATION", "PORTION", listOf(UnitCostDto("PORTION", 0.33)))
    )
    override suspend fun subRecipe(restaurantId: String, recipeId: String) = SubRecipeDto("r1", "Sauce", "Tomato sauce", "PREPARED_COMPONENT", dec("5"), "LITER",
        listOf(SubRecipeIngredientDto("STOCK", "Oil", "Extra virgin olive oil", dec("0.2"), "LITER", dec("1.50")),
            SubRecipeIngredientDto("STOCK", "Ribeye", "Ribeye steak", dec("500"), "GRAM", dec("15.56"))),
        batchCost = dec("17.06"), costPerUnit = dec("3.41"), instructions = "Soften the garlic in oil.\nAdd tomatoes, simmer 40 minutes.")
}

class InventoryOverviewGalleryTest {
    private val sizes = listOf("phone" to (400 to 2600), "tablet" to (1024 to 1900), "desktop" to (1440 to 1300), "big" to (1920 to 1200))

    @Test
    fun overviewAtEverySize() = gallery {
        for (empty in listOf(false, true)) {
            val model = InventoryScreenModel(OverviewInventory(empty), gallerySession())
            val recipes = RecipesScreenModel(NoRecipes(), gallerySession())
            model.setActive(true); settle()
            sizes.forEach { (name, size) ->
                render("overview-${if (empty) "empty" else "data"}-$name", size.first, size.second) {
                    val state by model.state.collectAsState()
                    val recipeState by recipes.state.collectAsState()
                    InventoryContent(state, model, recipeState, recipes, InventoryView.OVERVIEW, {})
                }
            }
            recipes.onDispose(); model.onDispose()
        }
    }

    @Test
    fun stockAtEverySize() = gallery {
        val model = InventoryScreenModel(OverviewInventory(false), gallerySession())
        val recipes = RecipesScreenModel(NoRecipes(), gallerySession())
        model.setActive(true); settle()
        listOf("phone" to (400 to 1500), "tablet" to (1024 to 900), "desktop" to (1440 to 960), "big" to (1920 to 1080)).forEach { (name, size) ->
            render("stock-$name", size.first, size.second) {
                val state by model.state.collectAsState()
                val recipeState by recipes.state.collectAsState()
                InventoryContent(state, model, recipeState, recipes, InventoryView.STOCK, {})
            }
        }
        recipes.onDispose(); model.onDispose()
    }

    @Test
    fun otherTabs() = gallery {
        val model = InventoryScreenModel(OverviewInventory(false), gallerySession())
        val recipes = RecipesScreenModel(NoRecipes(), gallerySession())
        model.setActive(true); settle()
        for (view in listOf(InventoryView.HISTORY, InventoryView.COUNTS)) {
            listOf("phone" to (400 to 1200), "desktop" to (1440 to 960)).forEach { (name, size) ->
                render("${view.name.lowercase()}-$name", size.first, size.second) {
                    val state by model.state.collectAsState()
                    val recipeState by recipes.state.collectAsState()
                    InventoryContent(state, model, recipeState, recipes, view, {})
                }
            }
        }
        // Stock → Places → Manage places: the places as cards (user, 2026-11-11).
        model.managePlaces(true); settle()
        listOf("desktop" to (1440 to 960), "tablet" to (900 to 1000)).forEach { (name, size) ->
            render("manage-places-$name", size.first, size.second, required = listOf("Manage places", "Walk-in cooler")) {
                val state by model.state.collectAsState()
                val recipeState by recipes.state.collectAsState()
                InventoryContent(state, model, recipeState, recipes, InventoryView.STOCK, {})
            }
        }
        model.managePlaces(false); settle()
        recipes.onDispose(); model.onDispose()
    }

    /** Adding a sub-recipe from Items (made from stock items and another sub-recipe), and opening a made-here item's, at phone and desktop size. */
    @Test
    fun subRecipeEditor() = gallery {
        val model = InventoryScreenModel(OverviewInventory(false), gallerySession())
        val recipes = RecipesScreenModel(NoRecipes(), gallerySession())
        model.setActive(true); settle(); recipes.setActive(true); settle()
        val shot = { name: String, width: Int, height: Int ->
            render(name, width, height) {
                val state by model.state.collectAsState()
                val recipeState by recipes.state.collectAsState()
                InventoryContent(state, model, recipeState, recipes, InventoryView.ITEMS, {})
            }
        }
        shot("items-with-sub-recipes-desktop", 1440, 960)
        recipes.newSubRecipe(); settle()
        recipes.changeSubRecipe {
            it.copy(name = "Garlic oil", yieldText = "1", yieldUnit = "LITER", lines = listOf(
                SubRecipeLine("STOCK", "Oil", "Extra virgin olive oil", "900", "MILLILITER"),
                SubRecipeLine("STOCK", "Eggs", "Free-range eggs", "2", "EACH")))
        }
        settle()
        shot("sub-recipe-new-phone", 390, 844)
        shot("sub-recipe-new-desktop", 1440, 960)
        recipes.cancelSubRecipe(); recipes.openSubRecipe("r1"); settle()
        shot("sub-recipe-open-desktop", 1440, 960)
        recipes.onDispose(); model.onDispose()
    }

    /** Every tab and the main dialogs at a phone's size (390 × 844), as people will see them. */
    @Test
    fun phoneTour() = gallery {
        val model = InventoryScreenModel(OverviewInventory(false), gallerySession())
        val recipes = RecipesScreenModel(NoRecipes(), gallerySession())
        model.setActive(true); settle()
        val shot = { name: String, view: InventoryView ->
            render("phone-$name", 390, 844) {
                val state by model.state.collectAsState()
                val recipeState by recipes.state.collectAsState()
                InventoryContent(state, model, recipeState, recipes, view, {})
            }
        }
        InventoryView.entries.forEach { view ->
            if (view == InventoryView.ITEMS) { recipes.setActive(true); settle() }
            shot(view.name.lowercase(), view)
        }
        // A narrow phone too (360 wide), where tight rows show first.
        listOf(InventoryView.OVERVIEW, InventoryView.STOCK, InventoryView.HISTORY).forEach { view ->
            render("phone360-${view.name.lowercase()}", 360, 760) {
                val state by model.state.collectAsState()
                val recipeState by recipes.state.collectAsState()
                InventoryContent(state, model, recipeState, recipes, view, {})
            }
        }
        recipes.openSubRecipe("r1"); settle(); shot("sub-recipe-open", InventoryView.ITEMS); recipes.cancelSubRecipe()
        model.newItem(); settle(); shot("item-editor", InventoryView.ITEMS); model.cancelItem()
        model.startMove(com.saporini.mobile_desktop.admin.inventory.StockMoveKind.RECEIVE, "Wine", "bar"); settle(); shot("move", InventoryView.STOCK); model.cancelMove()
        model.startBulk(com.saporini.mobile_desktop.admin.inventory.StockMoveKind.WASTE, listOf("Crab" to "walk", "Eggs" to "kit")); settle(); shot("bulk", InventoryView.STOCK); model.cancelBulk()
        model.managePlaces(true); settle(); shot("manage-places", InventoryView.STOCK); model.managePlaces(false); settle()
        model.newLocation(); settle(); shot("place-editor", InventoryView.STOCK); model.cancelLocation()
        // A place with its own picture picked (user, 2026-11-11: "I can upload my own image if I want").
        model.newLocation()
        model.changeLocation {
            it.copy(name = "Wine cellar", photo = com.saporini.mobile_desktop.pos.menu.ui.item.PhotoChange.Replace(
                java.io.File("src/commonMain/composeResources/drawable/place_wine.png").readBytes(), "cellar.png", "image/png"))
        }
        settle(); shot("place-own-picture", InventoryView.STOCK); model.cancelLocation()
        model.openCount("c1"); settle(); shot("count", InventoryView.COUNTS); model.openCount(null); settle()
        render("phone-start-count", 390, 844) {
            val state by model.state.collectAsState()
            StartCountDialog(state, model) {}
        }
        render("phone-scan", 390, 844) { ScanBarcodeDialog("Scan an item", "See its stock and record a change for it.", onCode = {}, onDismiss = {}) }
        recipes.onDispose(); model.onDispose()
    }

    @Test
    fun itemsAtEverySize() = gallery {
        val model = InventoryScreenModel(OverviewInventory(false), gallerySession())
        val recipes = RecipesScreenModel(NoRecipes(), gallerySession())
        model.setActive(true); settle()
        listOf("phone" to (400 to 1500), "tablet" to (1024 to 900), "desktop" to (1440 to 960), "big" to (1920 to 1080)).forEach { (name, size) ->
            render("items-$name", size.first, size.second) {
                val state by model.state.collectAsState()
                val recipeState by recipes.state.collectAsState()
                InventoryContent(state, model, recipeState, recipes, InventoryView.ITEMS, {})
            }
        }
        recipes.onDispose(); model.onDispose()
    }
}
