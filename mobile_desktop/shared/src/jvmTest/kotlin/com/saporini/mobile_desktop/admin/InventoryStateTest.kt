@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop.admin

import com.saporini.mobile_desktop.admin.inventory.InventoryApi
import com.saporini.mobile_desktop.admin.inventory.InventoryConfirm
import com.saporini.mobile_desktop.admin.inventory.InventoryRepository
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryTab
import com.saporini.mobile_desktop.admin.inventory.COUNT_PAGE_SIZE
import com.saporini.mobile_desktop.admin.inventory.ItemField
import com.saporini.mobile_desktop.admin.inventory.MOVEMENT_PAGE_SIZE
import com.saporini.mobile_desktop.admin.inventory.StockCountCreateRequestDto
import com.saporini.mobile_desktop.admin.inventory.StockCountDto
import com.saporini.mobile_desktop.admin.inventory.StockCountLineDto
import com.saporini.mobile_desktop.admin.inventory.StockCountLineRequestDto
import com.saporini.mobile_desktop.admin.inventory.StockCountPageDto
import com.saporini.mobile_desktop.admin.inventory.StockItemDraft
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.StockItemRequestDto
import com.saporini.mobile_desktop.admin.inventory.StockLevelDto
import com.saporini.mobile_desktop.admin.inventory.StockLocationDto
import com.saporini.mobile_desktop.admin.inventory.StockLocationRequestDto
import com.saporini.mobile_desktop.admin.inventory.StockMoveDraft
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.admin.inventory.StockMoveRequestDto
import com.saporini.mobile_desktop.admin.inventory.StockMovementDto
import com.saporini.mobile_desktop.admin.inventory.SaleStockSourceDto
import com.saporini.mobile_desktop.admin.inventory.compareDecimals
import com.saporini.mobile_desktop.admin.inventory.countSteps
import com.saporini.mobile_desktop.admin.inventory.itemProblems
import com.saporini.mobile_desktop.admin.inventory.itemRequest
import com.saporini.mobile_desktop.admin.inventory.moveProblem
import com.saporini.mobile_desktop.admin.inventory.moveRequest
import com.saporini.mobile_desktop.admin.inventory.suppliersOf
import com.saporini.mobile_desktop.admin.inventory.typedNumber
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.PendingInventoryMoveStorage
import com.saporini.mobile_desktop.orders.user
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun stockItem(id: String, name: String, supplier: String? = null, active: Boolean = true) =
    StockItemDto(id = id, name = name, itemType = "INGREDIENT", baseUnit = "KILOGRAM", supplierName = supplier, active = active,
        costPerUnit = OrderDecimal("2.5000"))

private fun place(id: String, name: String, active: Boolean = true) = StockLocationDto(id, "branch-1", name.uppercase(), name, "KITCHEN", active = active)

private class FakeInventory : InventoryRepository {
    var items = mutableListOf(stockItem("tom", "Tomatoes", "Green Farm"), stockItem("flour", "Flour", " green  farm "),
        stockItem("oil", "Olive oil", "Oleificio"), stockItem("old", "Old stock", "Green Farm", active = false))
    var locations = mutableListOf(place("kitchen", "Kitchen"), place("walkin", "Walk-in"), place("closed", "Closed", active = false))
    val calls = mutableListOf<String>()
    val moves = mutableListOf<Pair<StockMoveKind, StockMoveRequestDto>>()
    val moveKeys = mutableListOf<String>()
    val moveAttempts = mutableListOf<String>()
    val savedItems = mutableListOf<StockItemRequestDto>()
    val saleSources = mutableListOf<SaleStockSourceDto>()
    var movementCount = 230
    var failure: Exception? = null
    var saveItemFailure: Exception? = null
    var loseNextMoveResponse = false
    var count = StockCountDto("c1", locationId = "kitchen", locationName = "Kitchen", status = "DRAFT")
    var moreCounts = mutableListOf<StockCountDto>()

    override suspend fun items(restaurantId: String, includeInactive: Boolean): List<StockItemDto> {
        calls += "items"; failure?.let { throw it }
        return items.toList()
    }

    override suspend fun searchItems(restaurantId: String, keyword: String) = items.filter { keyword in it.name }

    override suspend fun saveItem(restaurantId: String, itemId: String?, request: StockItemRequestDto): StockItemDto {
        saveItemFailure?.let { throw it }
        failure?.let { throw it }
        savedItems += request
        val saved = StockItemDto(itemId ?: "new", name = request.name, itemType = request.itemType, baseUnit = request.baseUnit,
            supplierName = request.supplierName, costPerUnit = request.costPerUnit, active = request.active)
        items.removeAll { it.id == saved.id }; items += saved
        return saved
    }

    override suspend fun deactivateItem(restaurantId: String, itemId: String) {
        calls += "deactivate $itemId"
        items.replaceAll { if (it.id == itemId) it.copy(active = false) else it }
    }

    override suspend fun locations(restaurantId: String, includeInactive: Boolean) = locations.toList()

    override suspend fun saveLocation(restaurantId: String, locationId: String?, request: StockLocationRequestDto): StockLocationDto {
        calls += "location ${request.name}"
        return StockLocationDto(locationId ?: "loc-new", request.branchId, request.code, request.name, request.locationType, request.notes, request.active)
            .also { saved -> locations.removeAll { it.id == saved.id }; locations += saved }
    }

    override suspend fun deactivateLocation(restaurantId: String, locationId: String) {
        calls += "deactivate place $locationId"
    }

    override suspend fun levels(restaurantId: String, locationId: String?, itemId: String?): List<StockLevelDto> {
        calls += "levels $locationId"
        return listOf(StockLevelDto(locationId = locationId ?: "kitchen", inventoryItemId = "tom", onHandQuantity = OrderDecimal("3.500"), lowStock = true))
    }

    override suspend fun lowStock(restaurantId: String) = listOf(StockLevelDto(locationId = "kitchen", inventoryItemId = "tom", lowStock = true))

    override suspend fun saleSources(restaurantId: String) = saleSources.toList()

    override suspend fun setSaleSource(restaurantId: String, branchId: String, itemId: String, locationId: String): SaleStockSourceDto {
        calls += "sale source $branchId $itemId $locationId"
        val location = locations.first { it.id == locationId }
        return SaleStockSourceDto("source-$branchId-$itemId", branchId, "Branch", itemId, items.first { it.id == itemId }.name,
            locationId, location.name).also { saved ->
            saleSources.removeAll { it.branchId == branchId && it.inventoryItemId == itemId }
            saleSources += saved
        }
    }

    override suspend fun removeSaleSource(restaurantId: String, branchId: String, itemId: String) {
        calls += "remove sale source $branchId $itemId"
        saleSources.removeAll { it.branchId == branchId && it.inventoryItemId == itemId }
    }

    override suspend fun move(restaurantId: String, kind: StockMoveKind, request: StockMoveRequestDto, requestKey: String) {
        moveAttempts += requestKey
        failure?.let { throw it }
        val previous = moveKeys.indexOf(requestKey)
        if (previous >= 0) {
            check(moves[previous] == (kind to request)) { "An idempotency key cannot be reused for a different stock movement" }
            return
        }
        moves += kind to request
        moveKeys += requestKey
        if (loseNextMoveResponse) {
            loseNextMoveResponse = false
            throw java.io.IOException("response lost after server committed")
        }
    }

    override suspend fun movements(restaurantId: String, type: String?, itemId: String?, page: Int, size: Int): List<StockMovementDto> {
        calls += "movements $page"
        return (0 until movementCount).drop(page * size).take(size).map { StockMovementDto("m$it", movementType = type ?: "RECEIPT") }
    }

    override suspend fun counts(restaurantId: String, status: String?, page: Int, size: Int): StockCountPageDto {
        calls += "counts $page"
        val all = (listOf(count) + moreCounts).distinctBy { it.id }.filter { status == null || it.status == status }
        val items = all.drop(page * size).take(size)
        val totalPages = if (all.isEmpty()) 0 else (all.size + size - 1) / size
        return StockCountPageDto(items = items, page = page, size = size, totalElements = all.size.toLong(),
            totalPages = totalPages, hasNext = page + 1 < totalPages, hasPrevious = page > 0)
    }

    override suspend fun count(restaurantId: String, countId: String) = count

    override suspend fun createCount(restaurantId: String, request: StockCountCreateRequestDto): StockCountDto {
        calls += "create count ${request.locationId} ${request.branchId}"
        return count
    }

    override suspend fun countLine(restaurantId: String, countId: String, itemId: String, request: StockCountLineRequestDto): StockCountDto {
        calls += "line $itemId ${request.countedQuantity.value}"
        count = count.copy(lines = listOf(StockCountLineDto("l1", itemId, countedQuantity = request.countedQuantity)))
        return count
    }

    override suspend fun removeCountLine(restaurantId: String, countId: String, lineId: String): StockCountDto {
        count = count.copy(lines = emptyList())
        return count
    }

    override suspend fun countStep(restaurantId: String, countId: String, step: String): StockCountDto {
        calls += "step $step"
        count = count.copy(status = mapOf("start" to "IN_PROGRESS", "complete" to "COMPLETED", "approve" to "APPROVED", "cancel" to "CANCELLED").getValue(step))
        return count
    }
}

private class FakePendingInventoryMoveStorage : PendingInventoryMoveStorage {
    var payload: String? = null
    override suspend fun loadPendingInventoryMoves() = payload
    override suspend fun savePendingInventoryMoves(payload: String?) { this.payload = payload }
}

class InventoryRulesTest {
    @Test
    fun numbersAreReadExactlyWithCommasAndLimits() {
        assertEquals("1.5", typedNumber("1,5").value?.value)
        assertEquals("0.25", typedNumber(" .25 ").value?.value)
        assertEquals("1000", typedNumber("1 000").value?.value)
        assertEquals("12.3", typedNumber("0012.300").value?.value)
        assertEquals("Enter a number", typedNumber("").problem)
        assertNull(typedNumber("", required = false).value)
        assertNull(typedNumber("", required = false).problem)
        assertEquals("This isn't a number", typedNumber("1.2.3").problem)
        assertEquals("This isn't a number", typedNumber("1e5").problem)
        assertEquals("This isn't a number", typedNumber("abc").problem)
        assertEquals("Must be more than zero", typedNumber("0").problem)
        assertEquals("0", typedNumber("-0.000", allowZero = true).value?.value)
        assertEquals("Can't be below zero", typedNumber("-1").problem)
        assertEquals("-0.75", typedNumber("-0.75", allowNegative = true).value?.value)
        assertEquals("Can't be zero", typedNumber("0", allowNegative = true).problem)
        assertEquals("At most 3 decimals", typedNumber("1.2345").problem)
        assertEquals("999999999.999", typedNumber("999999999.999").value?.value)
        assertEquals("Too large", typedNumber("1000000000").problem)
        assertEquals("At most 4 decimals", typedNumber("1.23456", digits = 12, decimals = 4).problem)
    }

    @Test
    fun decimalsCompareWithoutRounding() {
        assertEquals(0, compareDecimals(OrderDecimal("1.50"), OrderDecimal("1.5")))
        assertEquals(1, compareDecimals(OrderDecimal("10"), OrderDecimal("9.999")))
        assertEquals(-1, compareDecimals(OrderDecimal("-2"), OrderDecimal("-1.5")))
        assertEquals(-1, compareDecimals(OrderDecimal("-0.1"), OrderDecimal("0")))
        assertEquals(1, compareDecimals(OrderDecimal("123456789012.0001"), OrderDecimal("123456789012")))
    }

    @Test
    fun itemsAreCheckedAndSentTrimmed() {
        val problems = itemProblems(StockItemDraft(name = " ", code = "c".repeat(81), costText = "-1", reorderText = "10", parText = "5",
            supplierName = "s".repeat(151), description = "d".repeat(2001)))
        assertEquals(setOf(ItemField.NAME, ItemField.CODE, ItemField.COST, ItemField.PAR, ItemField.SUPPLIER, ItemField.TEXT), problems.keys)
        assertEquals("The full level can't be below the reorder point", problems[ItemField.PAR])
        val draft = StockItemDraft(name = " Peperoncino 🌶️ ' OR 1=1 -- ", costText = "0,0100", supplierName = "  ", reorderText = "", parText = "20")
        assertEquals(emptyMap(), itemProblems(draft))
        val request = itemRequest(draft)
        assertEquals("Peperoncino 🌶️ ' OR 1=1 --", request.name)
        assertEquals("0.01", request.costPerUnit.value)
        assertNull(request.supplierName)
        assertNull(request.reorderPoint)
        assertEquals("20", request.parLevel?.value)
        assertTrue(request.active && request.trackInventory)
    }

    @Test
    fun stockChangesNeedTheRightFields() {
        val receive = StockMoveDraft(StockMoveKind.RECEIVE, itemId = "tom", locationId = "kitchen", quantityText = "2,5", unitCostText = "1.1")
        assertNull(moveProblem(receive))
        assertEquals("Choose an item", moveProblem(receive.copy(itemId = null)))
        assertEquals("Quantity: Must be more than zero", moveProblem(receive.copy(quantityText = "0")))
        assertEquals("Cost: At most 4 decimals", moveProblem(receive.copy(unitCostText = "1.00001")))
        assertEquals("Say why", moveProblem(receive.copy(kind = StockMoveKind.WASTE)))
        assertEquals("Say why", moveProblem(receive.copy(kind = StockMoveKind.ADJUST, quantityText = "-1", reason = "  ")))
        assertNull(moveProblem(receive.copy(kind = StockMoveKind.ADJUST, quantityText = "-1", reason = "Scale")))
        assertNull(moveProblem(receive.copy(kind = StockMoveKind.RETURN)))
        assertEquals("Choose where it goes", moveProblem(receive.copy(kind = StockMoveKind.TRANSFER)))
        assertEquals("Choose two different places", moveProblem(receive.copy(kind = StockMoveKind.TRANSFER, toLocationId = "kitchen")))
        assertEquals("The reason can be at most 2000 characters", moveProblem(receive.copy(reason = "r".repeat(2001))))

        val transfer = moveRequest(receive.copy(kind = StockMoveKind.TRANSFER, toLocationId = "walkin"))
        assertEquals(Triple(null, "kitchen", "walkin"), Triple(transfer.locationId, transfer.fromLocationId, transfer.toLocationId))
        assertNull(transfer.unitCostOverride)
        val adjust = moveRequest(receive.copy(kind = StockMoveKind.ADJUST, quantityText = "-0,75", reason = " Scale "))
        assertEquals("-0.75", adjust.quantityDelta?.value)
        assertNull(adjust.quantity)
        assertEquals("Scale", adjust.reason)
        assertEquals("1.1", moveRequest(receive).unitCostOverride?.value)
    }

    @Test
    fun suppliersComeFromItemNamesIgnoringCaseAndSpaces() {
        val items = listOf(stockItem("a", "B item", "Green Farm"), stockItem("b", "A item", " green   farm"), stockItem("c", "C", null),
            stockItem("d", "D", "Off", active = false), stockItem("e", "E", "Bakery"))
        val suppliers = suppliersOf(items, lowStockItemIds = setOf("a"))
        assertEquals(listOf("Bakery", "Green Farm"), suppliers.map { it.name })
        assertEquals(listOf("A item", "B item"), suppliers[1].items.map { it.name })
        assertEquals(1, suppliers[1].lowStockItems)
    }

    @Test
    fun countsMoveForwardOrAreCancelledUntilApproved() {
        assertEquals(listOf("start", "cancel"), countSteps("DRAFT"))
        assertEquals(listOf("complete", "cancel"), countSteps("IN_PROGRESS"))
        assertEquals(listOf("approve", "cancel"), countSteps("COMPLETED"))
        assertTrue(countSteps("APPROVED").isEmpty() && countSteps("CANCELLED").isEmpty())
    }
}

class InventoryScreenModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)
    @AfterTest fun after() = Dispatchers.resetMain()

    private suspend fun TestScope.check(
        repo: FakeInventory = FakeInventory(),
        permissions: List<String> = listOf("SETTINGS_READ", "SETTINGS_UPDATE"),
        pendingMoveStorage: PendingInventoryMoveStorage? = null,
        block: suspend TestScope.(InventoryScreenModel, FakeInventory) -> Unit
    ) {
        val session = SessionManager().apply { signIn(user(permissions = permissions)) }
        val model = InventoryScreenModel(repo, session, pendingMoveStorage)
        try {
            runCurrent(); model.setActive(true); runCurrent(); block(model, repo)
        } finally {
            model.onDispose(); runCurrent()
        }
    }

    @Test
    fun opensWithStockLowStockItemsPlacesAndSuppliers() = runTest(dispatcher) {
        check { model, repo ->
            val state = model.state.value
            assertEquals(1, state.levels.size)
            assertEquals(listOf("tom"), state.lowStock.map { it.inventoryItemId })
            assertEquals(listOf("Flour", "Olive oil", "Tomatoes"), state.visibleItems.map { it.name })
            assertEquals(listOf("Green Farm", "Oleificio"), state.suppliers.map { it.name })
            assertEquals(1, state.suppliers.first().lowStockItems)
            assertEquals(listOf("kitchen", "walkin"), state.activeLocations.map { it.id })
            assertTrue("levels null" in repo.calls)
        }
    }

    @Test
    fun itemSearchAndInactiveItemsFilterTheList() = runTest(dispatcher) {
        check { model, _ ->
            model.search("  oil  ")
            assertEquals(listOf("Olive oil"), model.state.value.visibleItems.map { it.name })
            model.search("green tom")
            assertEquals(listOf("Tomatoes"), model.state.value.visibleItems.map { it.name })
            model.search("")
            model.showInactive(true); runCurrent()
            assertEquals(4, model.state.value.visibleItems.size)
        }
    }

    @Test
    fun historyIsPagedAndARefreshReloadsEveryPageShown() = runTest(dispatcher) {
        check { model, repo ->
            model.tab(InventoryTab.HISTORY); runCurrent()
            assertEquals(MOVEMENT_PAGE_SIZE, model.state.value.movements.size)
            model.loadMore(); runCurrent()
            model.loadMore(); runCurrent()
            assertEquals(230, model.state.value.movements.size)
            assertFalse(model.state.value.movementsHasNext)
            repo.calls.clear()
            model.refresh(); runCurrent()
            assertEquals(listOf("movements 0", "movements 1", "movements 2"), repo.calls.filter { it.startsWith("movements") })
            model.historyFilter("WASTE", "tom"); runCurrent()
            assertEquals(MOVEMENT_PAGE_SIZE, model.state.value.movements.size)
            assertEquals("WASTE", model.state.value.movements.first().movementType)
            model.historyFilter("STOLEN", null)
            assertEquals("WASTE", model.state.value.movementType)
        }
    }

    @Test
    fun countHistoryLoadsMorePagesRefreshesThemAndResetsWhenFiltered() = runTest(dispatcher) {
        val repo = FakeInventory().apply {
            moreCounts += (2..COUNT_PAGE_SIZE + 1).map { id ->
                StockCountDto("c$id", locationId = "kitchen", status = "DRAFT")
            }
        }
        check(repo) { model, fake ->
            model.tab(InventoryTab.COUNTS); runCurrent()
            assertEquals(COUNT_PAGE_SIZE, model.state.value.counts.size)
            assertEquals(1, model.state.value.countPages)
            assertTrue(model.state.value.countsHasNext)

            model.loadMoreCounts(); runCurrent()
            assertEquals(COUNT_PAGE_SIZE + 1, model.state.value.counts.size)
            assertEquals(2, model.state.value.countPages)
            assertFalse(model.state.value.countsHasNext)

            fake.calls.clear()
            model.refresh(); runCurrent()
            assertEquals(listOf("counts 0", "counts 1"), fake.calls.filter { it.startsWith("counts ") })
            assertEquals(COUNT_PAGE_SIZE + 1, model.state.value.counts.size)

            model.countFilter("IN_PROGRESS"); runCurrent()
            assertEquals("IN_PROGRESS", model.state.value.countStatus)
            assertTrue(model.state.value.counts.isEmpty())
            assertEquals(1, model.state.value.countPages)
            assertFalse(model.state.value.countsHasNext)
        }
    }

    @Test
    fun aHistoryOfExactlyOnePageAsksOnceMoreAndStops() = runTest(dispatcher) {
        val repo = FakeInventory().apply { movementCount = MOVEMENT_PAGE_SIZE }
        check(repo) { model, _ ->
            model.tab(InventoryTab.HISTORY); runCurrent()
            assertTrue(model.state.value.movementsHasNext)
            model.loadMore(); runCurrent()
            assertFalse(model.state.value.movementsHasNext)
            assertEquals(MOVEMENT_PAGE_SIZE, model.state.value.movements.size)
        }
    }

    @Test
    fun savingAnItemChecksItAndUpdatesTheList() = runTest(dispatcher) {
        check { model, repo ->
            model.newItem()
            model.saveItem(); runCurrent()
            assertTrue(ItemField.NAME in model.state.value.itemProblems)
            assertTrue(repo.savedItems.isEmpty())
            model.changeItem { it.copy(name = "Basil", costText = "3,2", supplierName = "Herb Co") }
            assertFalse(ItemField.NAME in model.state.value.itemProblems)
            model.saveItem(); runCurrent()
            assertEquals("3.2", repo.savedItems.single().costPerUnit.value)
            assertNull(model.state.value.itemDraft)
            assertTrue(model.state.value.items.any { it.name == "Basil" })
            assertEquals(listOf("Green Farm", "Herb Co", "Oleificio"), model.state.value.suppliers.map { it.name })
            assertEquals("Basil was saved", model.state.value.notice)
        }
    }

    @Test
    fun duplicateItemConflictKeepsDraftAndMessageAfterRefreshingInventory() = runTest(dispatcher) {
        check { model, repo ->
            model.newItem()
            model.changeItem { it.copy(name = "Basil", code = "BASIL", barcode = "123456", costText = "3.2") }
            val draft = model.state.value.itemDraft
            repo.saveItemFailure = ApiException(409, "This barcode is already registered in this restaurant")

            model.saveItem(); runCurrent()

            assertEquals(draft, model.state.value.itemDraft)
            assertEquals("This barcode is already registered in this restaurant", model.state.value.error)
            assertTrue("items" in repo.calls)
            assertTrue(repo.items.none { it.name == "Basil" })
            assertTrue(repo.savedItems.isEmpty())
            assertFalse(model.state.value.loading)
        }
    }

    @Test
    fun saleSourceStateRejectsWrongBranchAndUpdatesOnSaveAndRemove() = runTest(dispatcher) {
        check { model, repo ->
            repo.locations += place("other-branch", "Other branch").copy(branchId = "branch-2")
            model.setSaleSource("branch-2", "tom", "kitchen")
            assertEquals("Choose a location for this branch", model.state.value.error)
            assertTrue(repo.saleSources.isEmpty())

            model.setSaleSource("branch-1", "tom", "kitchen"); runCurrent()
            assertEquals("kitchen", model.state.value.saleSource("branch-1", "tom")?.locationId)
            assertEquals("Sale stock source saved", model.state.value.notice)

            model.removeSaleSource("branch-1", "tom"); runCurrent()
            assertNull(model.state.value.saleSource("branch-1", "tom"))
            assertEquals("Sale stock source removed", model.state.value.notice)
        }
    }

    @Test
    fun switchingItemsAndPlacesOffAsksFirst() = runTest(dispatcher) {
        check { model, repo ->
            model.askDeactivateItem("oil")
            assertIs<InventoryConfirm.DeactivateItem>(model.state.value.confirm)
            model.dismissConfirm()
            assertTrue(repo.calls.none { it.startsWith("deactivate") })
            model.askDeactivateItem("oil"); model.confirm(); runCurrent()
            assertFalse(model.state.value.item("oil")!!.active)
            model.levelsAt("walkin"); runCurrent()
            model.askDeactivateLocation("walkin"); model.confirm(); runCurrent()
            assertEquals("deactivate place walkin", repo.calls.last { it.startsWith("deactivate") })
            assertNull(model.state.value.levelLocationId)
        }
    }

    @Test
    fun placesAreSavedForTheSignedInBranch() = runTest(dispatcher) {
        check { model, repo ->
            model.newLocation()
            model.saveLocation(); runCurrent()
            assertEquals("Enter a name for the place", model.state.value.error)
            model.changeLocation { it.copy(name = " Bar fridge ", locationType = "BAR") }
            model.saveLocation(); runCurrent()
            assertEquals("location Bar fridge", repo.calls.last())
            assertEquals("branch-1", model.state.value.location("loc-new")?.branchId)
        }
    }

    @Test
    fun stockChangesAreCheckedSentAndEverythingIsReadAgain() = runTest(dispatcher) {
        check { model, repo ->
            model.startMove(StockMoveKind.WASTE, itemId = "tom", locationId = "kitchen")
            model.changeMove { it.copy(quantityText = "1,25") }
            model.saveMove(); runCurrent()
            assertEquals("Say why", model.state.value.error)
            assertTrue(repo.moves.isEmpty())
            model.changeMove { it.copy(reason = "Bruised") }
            repo.calls.clear()
            model.saveMove(); runCurrent()
            val (kind, request) = repo.moves.single()
            assertEquals(StockMoveKind.WASTE, kind)
            assertEquals("1.25", request.quantity?.value)
            assertEquals("kitchen", request.locationId)
            assertEquals(36, repo.moveKeys.single().length)
            assertNull(model.state.value.moveDraft)
            assertNull(model.state.value.moveRequestKey)
            assertEquals("Waste recorded", model.state.value.notice)
            assertTrue("items" in repo.calls && repo.calls.any { it.startsWith("levels") })
        }
    }

    @Test
    fun aStockChangeForSomethingRemovedMeanwhileIsStopped() = runTest(dispatcher) {
        check { model, repo ->
            model.startMove(StockMoveKind.RECEIVE, itemId = "ghost", locationId = "kitchen")
            model.changeMove { it.copy(quantityText = "1") }
            model.saveMove(); runCurrent()
            assertEquals("That item or place is gone. Refresh and try again.", model.state.value.error)
            assertTrue(repo.moves.isEmpty())
        }
    }

    @Test
    fun aRefusedStockChangeKeepsTheDraft() = runTest(dispatcher) {
        check { model, repo ->
            model.startMove(StockMoveKind.TRANSFER, itemId = "tom", locationId = "kitchen")
            model.changeMove { it.copy(toLocationId = "walkin", quantityText = "500") }
            repo.failure = ApiException(409, "Not enough stock at Kitchen")
            model.saveMove(); runCurrent()
            assertEquals("Not enough stock at Kitchen", model.state.value.error)
            assertEquals("500", model.state.value.moveDraft?.quantityText)
            assertNull(model.state.value.moveRequestKey)
            assertFalse(model.state.value.moveOutcomeUnknown)
            model.changeMove { it.copy(quantityText = "1") }
            assertEquals("1", model.state.value.moveDraft?.quantityText)
            assertFalse(model.state.value.saving)
        }
    }

    @Test
    fun uncertainMovementSurvivesModelRestartAndReplaysTheSameKeyOnce() = runTest(dispatcher) {
        val storage = FakePendingInventoryMoveStorage()
        val repo = FakeInventory()
        val session = SessionManager().apply { signIn(user(permissions = listOf("SETTINGS_READ", "SETTINGS_UPDATE"))) }
        val first = InventoryScreenModel(repo, session, storage)
        runCurrent(); first.setActive(true); runCurrent()
        first.startMove(StockMoveKind.RECEIVE, itemId = "tom", locationId = "kitchen")
        first.changeMove { it.copy(quantityText = "2", unitCostText = "1.25") }
        val originalDraft = first.state.value.moveDraft
        repo.loseNextMoveResponse = true
        first.saveMove(); runCurrent()
        val originalKey = first.state.value.moveRequestKey
        assertTrue(storage.payload.orEmpty().contains(originalKey.orEmpty()))
        assertEquals(1, repo.moves.size, first.state.value.toString())
        first.onDispose(); runCurrent()

        val restarted = InventoryScreenModel(repo, session, storage)
        try {
            runCurrent(); restarted.setActive(true); runCurrent()
            assertEquals(originalDraft, restarted.state.value.moveDraft)
            assertEquals(originalKey, restarted.state.value.moveRequestKey)
            assertTrue(restarted.state.value.moveOutcomeUnknown)
            assertTrue(restarted.state.value.error.orEmpty().contains("app closed"))

            restarted.saveMove(); runCurrent()
            assertEquals(listOf(originalKey, originalKey), repo.moveAttempts)
            assertEquals(listOf(originalKey), repo.moveKeys)
            assertEquals(1, repo.moves.size, "server replay must not apply stock a second time")
            assertNull(storage.payload)
            assertNull(restarted.state.value.moveDraft)
            assertFalse(restarted.state.value.moveOutcomeUnknown)
        } finally {
            restarted.onDispose(); runCurrent()
        }
    }

    @Test
    fun uncertainMovementCannotBeEditedCancelledOrReplaced() = runTest(dispatcher) {
        check(pendingMoveStorage = FakePendingInventoryMoveStorage()) { model, repo ->
            model.startMove(StockMoveKind.RECEIVE, itemId = "tom", locationId = "kitchen")
            model.changeMove { it.copy(quantityText = "2") }
            repo.loseNextMoveResponse = true
            model.saveMove(); runCurrent()
            val draft = model.state.value.moveDraft
            val key = model.state.value.moveRequestKey

            model.changeMove { it.copy(quantityText = "3") }
            model.cancelMove()
            model.startMove(StockMoveKind.WASTE)

            assertEquals(draft, model.state.value.moveDraft)
            assertEquals(key, model.state.value.moveRequestKey)
            assertTrue(model.state.value.moveOutcomeUnknown)
            assertEquals(1, repo.moves.size)
        }
    }

    @Test
    fun unreadablePendingMovementFailsClosedWithoutOverwritingIt() = runTest(dispatcher) {
        val storage = FakePendingInventoryMoveStorage().apply { payload = "not valid JSON" }
        check(pendingMoveStorage = storage) { model, repo ->
            assertTrue(model.state.value.error.orEmpty().contains("Could not read the saved stock change"), model.state.value.toString())
            assertEquals("not valid JSON", storage.payload)
            model.startMove(StockMoveKind.RECEIVE)
            assertNull(model.state.value.moveDraft)
            assertTrue(repo.moves.isEmpty())
        }
    }

    @Test
    fun anUncertainStockMovementRetryReusesItsIdempotencyKey() = runTest(dispatcher) {
        check { model, repo ->
            model.startMove(StockMoveKind.RECEIVE, itemId = "tom", locationId = "kitchen")
            model.changeMove { it.copy(quantityText = "2") }
            repo.loseNextMoveResponse = true
            model.saveMove(); runCurrent()
            val firstKey = model.state.value.moveRequestKey
            assertEquals(36, firstKey?.length)
            assertEquals("2", model.state.value.moveDraft?.quantityText)
            assertEquals(1, repo.moves.size, "server committed before the response was lost")

            model.saveMove(); runCurrent()
            assertEquals(listOf(firstKey), repo.moveKeys)
            assertEquals(1, repo.moves.size, "retry with the same key replays instead of applying stock twice")
            assertNull(model.state.value.moveRequestKey)
            assertNull(model.state.value.moveDraft)
        }
    }

    @Test
    fun aCountGoesFromDraftToApprovedAndApprovingAsksFirst() = runTest(dispatcher) {
        check { model, repo ->
            model.tab(InventoryTab.COUNTS); runCurrent()
            model.newCount("closed")
            assertEquals("Choose a place to count", model.state.value.error)
            model.newCount("kitchen", "Monthly"); runCurrent()
            assertTrue("create count kitchen branch-1" in repo.calls)
            assertEquals("c1", model.state.value.openCount?.id)
            model.askCountStep("start"); runCurrent()
            assertEquals("IN_PROGRESS", model.state.value.openCount?.status)
            model.countItem("tom", "abc")
            assertEquals("Counted: This isn't a number", model.state.value.error)
            model.countItem("tom", "23,5"); runCurrent()
            assertTrue("line tom 23.5" in repo.calls)
            assertEquals(1, model.state.value.openCount?.lines?.size)
            model.askCountStep("approve")
            assertNull(model.state.value.confirm)
            model.askCountStep("complete"); runCurrent()
            model.askCountStep("approve")
            assertIs<InventoryConfirm.CountStep>(model.state.value.confirm)
            model.confirm(); runCurrent()
            assertEquals("APPROVED", model.state.value.openCount?.status)
            assertEquals("Count approved; stock now matches what was counted", model.state.value.notice)
            model.countItem("tom", "1")
            assertEquals("This count is finished and can't change", model.state.value.error)
        }
    }

    @Test
    fun readOnlyPeopleSeeStockButChangeNothing() = runTest(dispatcher) {
        check(permissions = listOf("SETTINGS_READ")) { model, repo ->
            assertEquals(3, model.state.value.visibleItems.size)
            model.newItem()
            assertNull(model.state.value.itemDraft)
            model.startMove(StockMoveKind.RECEIVE)
            assertNull(model.state.value.moveDraft)
            model.newCount("kitchen")
            assertEquals("You can only look at inventory", model.state.value.error)
            assertTrue(repo.calls.none { it.startsWith("create") })
        }
    }

    @Test
    fun aFailedLoadKeepsWhatIsShown() = runTest(dispatcher) {
        check { model, repo ->
            repo.failure = java.io.IOException("offline")
            model.refresh(); runCurrent()
            assertEquals(3, model.state.value.visibleItems.size)
            assertTrue(model.state.value.stale)
            assertEquals("Could not load. Check the connection and try again.", model.state.value.error)
        }
    }

    @Test
    fun withoutAnyRightsNothingLoads() = runTest(dispatcher) {
        check(permissions = emptyList()) { model, repo ->
            assertTrue(repo.calls.isEmpty())
            assertFalse(model.state.value.canRead)
        }
    }
}

class InventoryApiTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val countJson = """{"id":"c1","locationId":"l","status":"IN_PROGRESS","varianceValue":-2.2500,"lines":[{"id":"x","inventoryItemId":"i","countedQuantity":23.500}]}"""
    private val countPageJson = """{"items":[$countJson],"page":1,"size":25,"totalElements":26,"totalPages":2,"hasNext":false,"hasPrevious":true}"""

    @Test
    fun pathsParametersAndExactQuantities() = runTest {
        val seen = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            val body = (request.body as? TextContent)?.text
            seen += "${request.method.value} ${request.url.encodedPath}?${request.url.encodedQuery} key=${request.headers["Idempotency-Key"]}" + (body?.let { " $it" } ?: "")
            val path = request.url.encodedPath
            val response = when {
                request.method.value == "DELETE" -> ""
                path.endsWith("/sale-sources") -> """[{"id":"s1","branchId":"b","inventoryItemId":"i","locationId":"l"}]"""
                path.contains("/sale-sources/") -> """{"id":"s1","branchId":"b","inventoryItemId":"i","locationId":"l"}"""
                path.endsWith("/items/all") -> """[{"id":"i","name":"Flour","itemType":"INGREDIENT","baseUnit":"KILOGRAM","costPerUnit":0.9000,"trackInventory":true,"active":false}]"""
                path.endsWith("/movements") -> """[{"id":"m","movementType":"WASTE","quantityDelta":-1.250}]"""
                path.endsWith("/inventory/counts") -> countPageJson
                path.contains("/counts") -> countJson
                path.endsWith("/items") || path.contains("/items/") -> """{"id":"i","name":"Flour","itemType":"INGREDIENT","baseUnit":"KILOGRAM","costPerUnit":0.9000}"""
                else -> ""
            }
            if (response.isEmpty()) {
                respond("", if (request.method.value == "DELETE") HttpStatusCode.NoContent else HttpStatusCode.Created)
            } else {
                respond(response, headers = jsonHeaders)
            }
        }) { install(ContentNegotiation) { json(json) } }
        try {
            val api = InventoryApi(client) { "http://localhost/" }
            assertEquals("l", api.saleSources("r").single().locationId)
            assertEquals("l", api.setSaleSource("r", "b", "i", "l").locationId)
            api.removeSaleSource("r", "b", "i")
            val items = api.items("r", includeInactive = true)
            assertEquals("0.9000", items.single().costPerUnit.value)
            assertFalse(items.single().active)
            api.saveItem("r", null, StockItemRequestDto(name = "Flour", itemType = "INGREDIENT", baseUnit = "KILOGRAM",
                costPerUnit = OrderDecimal("0.9000"), trackInventory = true, active = true))
            api.move("r", StockMoveKind.ADJUST, StockMoveRequestDto(locationId = "l", inventoryItemId = "i", quantityDelta = OrderDecimal("-0.750"), reason = "Scale"), "adjustment-key-001")
            api.move("r", StockMoveKind.RETURN, StockMoveRequestDto(locationId = "l", inventoryItemId = "i", quantity = OrderDecimal("2")), "return-key-000001")
            val history = api.movements("r", "WASTE", "i", 2, 100)
            assertEquals("-1.250", history.single().quantityDelta.value)
            val countPage = api.counts("r", "IN_PROGRESS", 1, 25)
            assertEquals(1, countPage.page)
            assertEquals(26, countPage.totalElements)
            assertEquals("c1", countPage.items.single().id)
            val count = api.countLine("r", "c1", "i", StockCountLineRequestDto(OrderDecimal("23.500")))
            assertEquals("23.500", count.lines.single().countedQuantity?.value)
            assertTrue(count.editable)
            api.countStep("r", "c1", "approve")

            assertTrue(seen.any { it.startsWith("GET /restaurants/r/inventory/items/all") }, seen.toString())
            assertTrue(seen.any { it.startsWith("GET /restaurants/r/inventory/sale-sources") }, seen.toString())
            assertTrue(seen.any { it.startsWith("PUT /restaurants/r/inventory/sale-sources/b/i") && "\"locationId\":\"l\"" in it }, seen.toString())
            assertTrue(seen.any { it.startsWith("DELETE /restaurants/r/inventory/sale-sources/b/i") }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /restaurants/r/inventory/items?") && "\"costPerUnit\":0.9000" in it && "\"trackInventory\":true" in it && "\"active\":true" in it }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /restaurants/r/inventory/adjustments") && "\"quantityDelta\":-0.750" in it && "\"quantity\"" !in it }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /restaurants/r/inventory/returns") }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /restaurants/r/inventory/adjustments") && "key=adjustment-key-001" in it }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /restaurants/r/inventory/returns") && "key=return-key-000001" in it }, seen.toString())
            assertTrue(seen.any { it.startsWith("GET /restaurants/r/inventory/movements?type=WASTE&itemId=i&page=2&size=100") }, seen.toString())
            assertTrue(seen.any { it.startsWith("GET /restaurants/r/inventory/counts?status=IN_PROGRESS&page=1&size=25") }, seen.toString())
            assertTrue(seen.any { it.startsWith("PUT /restaurants/r/inventory/counts/c1/lines/i") && "\"countedQuantity\":23.500" in it }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /restaurants/r/inventory/counts/c1/approve") }, seen.toString())
            kotlin.test.assertFailsWith<IllegalArgumentException> { api.countStep("r", "c1", "delete") }
        } finally {
            client.close()
        }
    }
}
