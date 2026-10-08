package com.saporini.mobile_desktop.gallery

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.saporini.mobile_desktop.admin.inventory.InventoryRepository
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryTab
import com.saporini.mobile_desktop.admin.inventory.MenuChoice
import com.saporini.mobile_desktop.admin.inventory.RecipeComponentDto
import com.saporini.mobile_desktop.admin.inventory.RecipeComponentRequestDto
import com.saporini.mobile_desktop.admin.inventory.RecipeDto
import com.saporini.mobile_desktop.admin.inventory.RecipeRequestDto
import com.saporini.mobile_desktop.admin.inventory.RecipesRepository
import com.saporini.mobile_desktop.admin.inventory.RecipesScreenModel
import com.saporini.mobile_desktop.admin.inventory.SaleStockSourceDto
import com.saporini.mobile_desktop.admin.inventory.StockCountCreateRequestDto
import com.saporini.mobile_desktop.admin.inventory.StockCountDto
import com.saporini.mobile_desktop.admin.inventory.StockCountLineDto
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
import com.saporini.mobile_desktop.admin.inventory.ui.InventoryView
import com.saporini.mobile_desktop.admin.inventory.ui.SuppliersContent
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private fun d(text: String) = OrderDecimal(text)
private fun ago(minutes: Int) = (Clock.System.now() - minutes.minutes).toString()

private class GalleryInventory : InventoryRepository {
    val items = listOf(
        StockItemDto("i1", "TOM-SM", "San Marzano tomatoes", itemType = "INGREDIENT", baseUnit = "KILOGRAM", supplierName = "Green Farm", supplierSku = "GF-120",
            costPerUnit = d("3.20"), reorderPoint = d("5"), parLevel = d("20")),
        StockItemDto("i2", "MOZ", "Fior di latte mozzarella", itemType = "INGREDIENT", baseUnit = "KILOGRAM", supplierName = "Caseificio Rossi", supplierSku = "CR-9",
            costPerUnit = d("9.80"), reorderPoint = d("4"), parLevel = d("12")),
        StockItemDto("i3", "FLR-00", "Flour type 00", itemType = "INGREDIENT", baseUnit = "KILOGRAM", supplierName = "Molino Bianco", costPerUnit = d("1.10"),
            reorderPoint = d("25"), parLevel = d("100")),
        StockItemDto("i4", null, "Extra virgin olive oil", itemType = "INGREDIENT", baseUnit = "LITER", supplierName = "Green Farm", costPerUnit = d("8.50"),
            reorderPoint = d("3"), parLevel = d("10")),
        StockItemDto("i5", null, "Basil", itemType = "INGREDIENT", baseUnit = "GRAM", supplierName = "Green Farm", costPerUnit = d("0.03"), reorderPoint = d("200")),
        StockItemDto("i6", "PER-33", "Peroni 33 cl", itemType = "ALCOHOL", baseUnit = "BOTTLE", supplierName = "Beverage Co", costPerUnit = d("0.95"),
            reorderPoint = d("48"), parLevel = d("144")),
        StockItemDto("i7", null, "San Pellegrino 75 cl", itemType = "BEVERAGE", baseUnit = "BOTTLE", supplierName = "Beverage Co", costPerUnit = d("0.70"),
            reorderPoint = d("24"), parLevel = d("96")),
        StockItemDto("i8", null, "Pizza boxes 33 cm", itemType = "PACKAGING", baseUnit = "EACH", costPerUnit = d("0.32"), reorderPoint = d("100"), parLevel = d("500")),
        StockItemDto("i9", null, "Tomato sauce (batch)", itemType = "PREPARED_COMPONENT", baseUnit = "LITER", costPerUnit = d("2.40"), trackInventory = false),
        StockItemDto("i10", null, "Old menu card stock", itemType = "SUPPLY", baseUnit = "PACK", costPerUnit = d("4.00"), active = false)
    )
    val places = listOf(
        StockLocationDto("l1", "branch-1", "KIT", "Kitchen", "KITCHEN"),
        StockLocationDto("l2", "branch-1", "BAR", "Bar", "BAR"),
        StockLocationDto("l3", "branch-1", "WALK", "Walk-in fridge", "WALK_IN", notes = "Keep below 4 °C"),
        StockLocationDto("l4", "branch-1", "DRY", "Dry store", "DRY_STORAGE")
    )
    val levels = listOf(
        StockLevelDto("v1", "l3", "Walk-in fridge", "i1", "San Marzano tomatoes", d("3.5"), lastMovementAt = ago(80), lowStock = true),
        StockLevelDto("v2", "l3", "Walk-in fridge", "i2", "Fior di latte mozzarella", d("8.2"), lastMovementAt = ago(30)),
        StockLevelDto("v3", "l4", "Dry store", "i3", "Flour type 00", d("62"), lastMovementAt = ago(60 * 26)),
        StockLevelDto("v4", "l1", "Kitchen", "i4", "Extra virgin olive oil", d("1.5"), lastMovementAt = ago(15), lowStock = true),
        StockLevelDto("v5", "l1", "Kitchen", "i5", "Basil", d("0"), lastMovementAt = ago(200), lowStock = true),
        StockLevelDto("v6", "l2", "Bar", "i6", "Peroni 33 cl", d("96"), lastMovementAt = ago(60 * 5)),
        StockLevelDto("v7", "l2", "Bar", "i7", "San Pellegrino 75 cl", d("18"), lastMovementAt = ago(45), lowStock = true),
        StockLevelDto("v8", "l4", "Dry store", "i8", "Pizza boxes 33 cm", d("340"))
    )
    val movements = listOf(
        StockMovementDto("m1", "l1", "Kitchen", "i4", "Extra virgin olive oil", movementType = "SALE_CONSUMPTION", quantityDelta = d("-0.25"), unit = "LITER",
            totalCostDelta = d("-2.13"), occurredAt = ago(15), createdByUserName = "Marco Bianchi"),
        StockMovementDto("m2", "l3", "Walk-in fridge", "i2", "Fior di latte mozzarella", movementType = "RECEIPT", quantityDelta = d("6"), unit = "KILOGRAM",
            totalCostDelta = d("58.80"), reason = "Delivery note 81", occurredAt = ago(30), createdByUserName = "Giulia Rossi"),
        StockMovementDto("m3", "l1", "Kitchen", "i5", "Basil", movementType = "WASTE", quantityDelta = d("-80"), unit = "GRAM", reason = "Wilted",
            occurredAt = ago(200), createdByUserName = "Marco Bianchi"),
        StockMovementDto("m4", "l2", "Bar", "i6", "Peroni 33 cl", movementType = "TRANSFER_IN", quantityDelta = d("24"), unit = "BOTTLE", occurredAt = ago(60 * 5)),
        StockMovementDto("m5", "l4", "Dry store", "i3", "Flour type 00", movementType = "COUNT_ADJUSTMENT", quantityDelta = d("-1.5"), unit = "KILOGRAM",
            reason = "Count #12", occurredAt = ago(60 * 26), createdByUserName = "Davide Keci")
    )
    val count = StockCountDto("c1", "branch-1", "l3", "Walk-in fridge", "CNT-12", "IN_PROGRESS", createdByUserName = "Giulia Rossi", updatedAt = ago(20),
        lines = listOf(
            StockCountLineDto("cl1", "i1", "San Marzano tomatoes", d("3.5"), d("3.0"), d("-0.5"), "KILOGRAM", d("3.20"), d("-1.60")),
            StockCountLineDto("cl2", "i2", "Fior di latte mozzarella", d("8.2"), null, null, "KILOGRAM")
        ))
    val counts = listOf(count, StockCountDto("c0", "branch-1", "l4", "Dry store", "CNT-11", "APPROVED", approvedByUserName = "Davide Keci",
        approvedAt = ago(60 * 26), varianceValue = d("-1.65"), updatedAt = ago(60 * 26)))

    override suspend fun items(restaurantId: String, includeInactive: Boolean) = items
    override suspend fun searchItems(restaurantId: String, keyword: String) = items
    override suspend fun saveItem(restaurantId: String, itemId: String?, request: StockItemRequestDto) = items.first()
    override suspend fun deactivateItem(restaurantId: String, itemId: String) {}
    override suspend fun locations(restaurantId: String, includeInactive: Boolean) = places
    override suspend fun saveLocation(restaurantId: String, locationId: String?, request: StockLocationRequestDto) = places.first()
    override suspend fun deactivateLocation(restaurantId: String, locationId: String) {}
    override suspend fun levels(restaurantId: String, locationId: String?, itemId: String?) = levels.filter { locationId == null || it.locationId == locationId }
    override suspend fun lowStock(restaurantId: String) = levels.filter { it.lowStock }
    override suspend fun saleSources(restaurantId: String) = listOf(
        SaleStockSourceDto("s1", "branch-1", null, "i1", null, "l1", "Kitchen"),
        SaleStockSourceDto("s2", "branch-1", null, "i6", null, "l2", "Bar"))
    override suspend fun move(restaurantId: String, kind: StockMoveKind, request: StockMoveRequestDto, requestKey: String) {}
    override suspend fun movements(restaurantId: String, type: String?, itemId: String?, page: Int, size: Int) = if (page == 0) movements else emptyList()
    override suspend fun counts(restaurantId: String, status: String?, page: Int, size: Int) = StockCountPageDto(counts, 0, size, 2, 1, false)
    override suspend fun count(restaurantId: String, countId: String) = counts.first { it.id == countId }
    override suspend fun createCount(restaurantId: String, request: StockCountCreateRequestDto) = count
    override suspend fun countLine(restaurantId: String, countId: String, itemId: String, request: StockCountLineRequestDto) = count
    override suspend fun removeCountLine(restaurantId: String, countId: String, lineId: String) = count
    override suspend fun countStep(restaurantId: String, countId: String, step: String) = count
}

private class GalleryRecipes : RecipesRepository {
    val sauce = RecipeDto("r2", name = "Tomato sauce", recipeType = "PREP_BATCH", status = "ACTIVE", version = 3, yieldQuantity = d("5"), yieldUnit = "LITER",
        prepTimeMinutes = 15, cookTimeMinutes = 45, theoreticalCost = d("12.40"), components = listOf(
            RecipeComponentDto("rc5", "INVENTORY_ITEM", "i1", "San Marzano tomatoes", quantity = d("3"), unit = "KILOGRAM", yieldLossPercent = d("10")),
            RecipeComponentDto("rc6", "INVENTORY_ITEM", "i4", "Extra virgin olive oil", quantity = d("0.15"), unit = "LITER")
        ))
    val margherita = RecipeDto("r1", "menu-1", "Margherita", "PIZ-1", "Margherita", "Our classic: San Marzano, fior di latte, basil.", "FINISHED_DISH",
        "ACTIVE", 4, d("1"), "PORTION", 10, 2, "Stretch the dough to 30 cm.\nSpread the sauce, leave a 2 cm edge.\nAdd torn mozzarella.\nBake 90 seconds at 450 °C.\nFinish with basil and oil.",
        d("2.31"), "Giulia Rossi", ago(60 * 3), listOf(
            RecipeComponentDto("rc1", "INVENTORY_ITEM", "i3", "Flour type 00", quantity = d("250"), unit = "GRAM", displayOrder = 0),
            RecipeComponentDto("rc2", "SUB_RECIPE", childRecipeId = "r2", childRecipeName = "Tomato sauce", quantity = d("80"), unit = "MILLILITER", displayOrder = 1),
            RecipeComponentDto("rc3", "INVENTORY_ITEM", "i2", "Fior di latte mozzarella", quantity = d("125"), unit = "GRAM", yieldLossPercent = d("5"), displayOrder = 2),
            RecipeComponentDto("rc4", "INVENTORY_ITEM", "i5", "Basil", quantity = d("4"), unit = "GRAM", optionalComponent = true, displayOrder = 3, notes = "Fresh, torn"),
            RecipeComponentDto("rc7", "INVENTORY_ITEM", "i4", "Extra virgin olive oil", quantity = d("10"), unit = "MILLILITER", displayOrder = 4)
        ))
    val recipes = listOf(margherita, sauce,
        RecipeDto("r3", "menu-2", "Diavola", null, "Diavola", recipeType = "FINISHED_DISH", status = "DRAFT", theoreticalCost = d("2.95")),
        RecipeDto("r4", null, null, null, "Pizza dough", recipeType = "SUB_RECIPE", status = "ACTIVE", yieldQuantity = d("10"), yieldUnit = "PORTION",
            theoreticalCost = d("3.30")))
    override suspend fun recipes(restaurantId: String, type: String?, status: String?) = recipes.filter { (type == null || it.recipeType == type) && (status == null || it.status == status) }
    override suspend fun recipe(restaurantId: String, recipeId: String) = recipes.first { it.id == recipeId }
    override suspend fun save(restaurantId: String, recipeId: String?, request: RecipeRequestDto) = margherita
    override suspend fun putComponent(restaurantId: String, recipeId: String, request: RecipeComponentRequestDto) = margherita
    override suspend fun removeComponent(restaurantId: String, recipeId: String, componentId: String) = margherita
    override suspend fun archive(restaurantId: String, recipeId: String) = margherita
    override suspend fun recalculateCost(restaurantId: String, recipeId: String) = margherita
}

class AdminInventoryGalleryTest {
    private val menu = listOf(MenuChoice("menu-1", "Margherita", "Dinner", "Pizza"), MenuChoice("menu-2", "Diavola", "Dinner", "Pizza"))

    @Test
    fun inventory() = gallery {
        val model = InventoryScreenModel(GalleryInventory(), gallerySession())
        val recipes = RecipesScreenModel(GalleryRecipes(), gallerySession()) { menu }
        model.setActive(true); settle()
        fun shot(name: String, view: InventoryView, width: Int = 1440, height: Int = 960, required: List<String> = emptyList()) =
            render(name, width, height, required) {
                val state by model.state.collectAsState()
                val recipeState by recipes.state.collectAsState()
                InventoryContent(state, model, recipeState, recipes, view, {})
            }
        shot("inventory-stock", InventoryView.STOCK, required = listOf("Inventory", "Stock on hand", "San Marzano tomatoes", "Running low"))
        shot("inventory-stock-phone", InventoryView.STOCK, 420, 900)
        model.tab(InventoryTab.ITEMS); settle()
        shot("inventory-items", InventoryView.ITEMS, required = listOf("Stock items", "Sold from"))
        model.tab(InventoryTab.PLACES); settle()
        shot("inventory-places", InventoryView.PLACES, required = listOf("Walk-in fridge"))
        model.tab(InventoryTab.HISTORY); settle()
        shot("inventory-history", InventoryView.HISTORY, required = listOf("Stock history", "Delivery note 81"))
        model.tab(InventoryTab.COUNTS); settle()
        shot("inventory-counts", InventoryView.COUNTS, required = listOf("CNT-12"))
        model.openCount("c1"); settle()
        shot("inventory-count-dialog", InventoryView.COUNTS)
        model.openCount(null); settle()
        recipes.setActive(true); settle(); recipes.open("r1"); settle()
        shot("inventory-recipes", InventoryView.RECIPES, required = listOf("Margherita", "Ingredients", "Tomato sauce"))
        shot("inventory-recipes-tablet", InventoryView.RECIPES, 1000, 1000)
        recipes.newComponent("i2", "GRAM"); recipes.changeComponent { it.copy(quantityText = "125", lossText = "5") }; settle()
        shot("inventory-recipe-ingredient", InventoryView.RECIPES, required = listOf("Takes"))
        recipes.cancelComponent(); recipes.editRecipe(); settle()
        shot("inventory-recipe-editor", InventoryView.RECIPES, required = listOf("Dish on the menu"))
        recipes.cancelRecipe()
        model.tab(InventoryTab.STOCK); settle()
        model.startMove(StockMoveKind.RECEIVE, "i1", "l3"); settle()
        shot("inventory-move", InventoryView.STOCK, required = listOf("Receive stock"))
        model.cancelMove(); model.editItem("i1"); settle()
        shot("inventory-item-editor", InventoryView.STOCK, required = listOf("COST AND LEVELS"))
        model.cancelItem(); model.newLocation(); settle()
        shot("inventory-place-editor", InventoryView.PLACES)
        model.cancelLocation()
        recipes.onDispose(); model.onDispose()
    }

    @Test
    fun suppliers() = gallery {
        val model = InventoryScreenModel(GalleryInventory(), gallerySession())
        model.tab(InventoryTab.SUPPLIERS); model.setActive(true); settle()
        render("suppliers", required = listOf("Suppliers", "Green Farm", "Choose a supplier")) {
            val state by model.state.collectAsState()
            SuppliersContent(state, model)
        }
        render("suppliers-detail", required = listOf("To order", "Download order list")) {
            val state by model.state.collectAsState()
            SuppliersContent(state, model, initialSupplier = "green farm")
        }
        render("suppliers-phone", 420, 900) {
            val state by model.state.collectAsState()
            SuppliersContent(state, model)
        }
        model.onDispose()
    }
}
