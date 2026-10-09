@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop.admin

import com.saporini.mobile_desktop.admin.inventory.ComponentDraft
import com.saporini.mobile_desktop.admin.inventory.RecipeComponentDto
import com.saporini.mobile_desktop.admin.inventory.RecipeComponentRequestDto
import com.saporini.mobile_desktop.admin.inventory.RecipeDraft
import com.saporini.mobile_desktop.admin.inventory.RecipeDto
import com.saporini.mobile_desktop.admin.inventory.RecipeRequestDto
import com.saporini.mobile_desktop.admin.inventory.RecipesApi
import com.saporini.mobile_desktop.admin.inventory.RecipesRepository
import com.saporini.mobile_desktop.admin.inventory.RecipesScreenModel
import com.saporini.mobile_desktop.admin.inventory.componentProblem
import com.saporini.mobile_desktop.admin.inventory.componentRequest
import com.saporini.mobile_desktop.admin.inventory.recipeProblem
import com.saporini.mobile_desktop.admin.inventory.recipeRequest
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.orders.user
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun recipe(id: String, name: String, status: String = "ACTIVE", components: List<RecipeComponentDto> = emptyList()) =
    RecipeDto(id = id, name = name, recipeType = "FINISHED_DISH", status = status, components = components, theoreticalCost = OrderDecimal("1.20"))

private class FakeRecipes : RecipesRepository {
    val recipes = mutableListOf(recipe("pizza", "Margherita", components = listOf(
        RecipeComponentDto("c1", "INVENTORY_ITEM", inventoryItemId = "tom", inventoryItemName = "Tomatoes", quantity = OrderDecimal("0.120"), unit = "KILOGRAM", displayOrder = 0),
        RecipeComponentDto("c2", "INVENTORY_ITEM", inventoryItemId = "moz", inventoryItemName = "Mozzarella", quantity = OrderDecimal("0.100"), unit = "KILOGRAM", displayOrder = 3)
    )), recipe("dough", "Dough", "DRAFT"), recipe("old", "Old sauce", "ARCHIVED"))
    val calls = mutableListOf<String>()
    val components = mutableListOf<RecipeComponentRequestDto>()
    val saved = mutableListOf<RecipeRequestDto>()
    var failure: Exception? = null

    override suspend fun recipes(restaurantId: String, type: String?, status: String?): List<RecipeDto> {
        calls += "list $type $status"; failure?.let { throw it }
        return recipes.filter { (type == null || it.recipeType == type) && (status == null || it.status == status) }
    }

    override suspend fun recipe(restaurantId: String, recipeId: String) = recipes.first { it.id == recipeId }

    override suspend fun save(restaurantId: String, recipeId: String?, request: RecipeRequestDto): RecipeDto {
        failure?.let { throw it }
        saved += request
        val made = RecipeDto(recipeId ?: "new", name = request.name, recipeType = request.recipeType, status = request.status)
        recipes.removeAll { it.id == made.id }; recipes += made
        return made
    }

    override suspend fun putComponent(restaurantId: String, recipeId: String, request: RecipeComponentRequestDto): RecipeDto {
        components += request
        return recipes.first { it.id == recipeId }.let { r ->
            r.copy(components = r.components + RecipeComponentDto("c${r.components.size + 1}", request.componentType, request.inventoryItemId,
                childRecipeId = request.childRecipeId, quantity = request.quantity, unit = request.unit))
        }.also { saved -> recipes.replaceAll { if (it.id == saved.id) saved else it } }
    }

    override suspend fun removeComponent(restaurantId: String, recipeId: String, componentId: String): RecipeDto {
        calls += "remove $componentId"
        return recipes.first { it.id == recipeId }.let { it.copy(components = it.components.filterNot { c -> c.id == componentId }) }
    }

    override suspend fun archive(restaurantId: String, recipeId: String): RecipeDto {
        calls += "archive $recipeId"
        return recipes.first { it.id == recipeId }.copy(status = "ARCHIVED")
    }

    override suspend fun recalculateCost(restaurantId: String, recipeId: String): RecipeDto {
        calls += "cost $recipeId"
        return recipes.first { it.id == recipeId }.copy(theoreticalCost = OrderDecimal("1.35"))
    }
}

class RecipeRulesTest {
    @Test
    fun recipesAreCheckedAgainstTheServersLimits() {
        val ok = RecipeDraft(name = "Margherita", menuItemId = "menu-1", yieldText = "1", yieldUnit = "PORTION", prepText = "10", cookText = "")
        assertNull(recipeProblem(ok))
        assertEquals("Choose the dish on the menu this recipe makes", recipeProblem(ok.copy(menuItemId = null)))
        assertNull(recipeProblem(ok.copy(menuItemId = null, recipeType = "PREP_BATCH")))
        assertEquals("Enter a name", recipeProblem(ok.copy(name = " ")))
        assertEquals("The name can be at most 150 characters", recipeProblem(ok.copy(name = "N".repeat(151))))
        assertEquals("Makes: At most 3 decimals", recipeProblem(ok.copy(yieldText = "1.0001")))
        assertEquals("Choose the unit it makes", recipeProblem(ok.copy(yieldUnit = null)))
        assertEquals("Prep time: enter whole minutes", recipeProblem(ok.copy(prepText = "ten")))
        assertEquals("Cooking time: 0 to 10080 minutes", recipeProblem(ok.copy(cookText = "10081")))
        assertEquals("Instructions can be at most 10000 characters", recipeProblem(ok.copy(instructions = "i".repeat(10001))))
        assertEquals("The description can be at most 2000 characters", recipeProblem(ok.copy(description = "d".repeat(2001))))
        val request = recipeRequest(ok.copy(name = " Margherita ", code = " ", yieldText = "", prepText = " 10 "))
        assertEquals("Margherita", request.name)
        assertNull(request.code)
        assertNull(request.yieldUnit)
        assertEquals(10, request.prepTimeMinutes)
    }

    @Test
    fun ingredientsNeedOneSourceAQuantityAndAtMostAllWaste() {
        val ok = ComponentDraft(inventoryItemId = "tom", quantityText = "0,120", unit = "KILOGRAM", lossText = "12.5")
        assertNull(componentProblem(ok, "pizza"))
        assertEquals("Choose a stock item or a recipe", componentProblem(ok.copy(childRecipeId = "dough"), "pizza"))
        assertEquals("Choose a stock item or a recipe", componentProblem(ok.copy(inventoryItemId = null), "pizza"))
        assertEquals("A recipe can't contain itself", componentProblem(ComponentDraft(childRecipeId = "pizza", quantityText = "1"), "pizza"))
        assertEquals("Quantity: Must be more than zero", componentProblem(ok.copy(quantityText = "0"), "pizza"))
        assertEquals("Waste must be below 100%", componentProblem(ok.copy(lossText = "100.01"), "pizza"))
        assertEquals("Waste must be below 100%", componentProblem(ok.copy(lossText = "100"), "pizza"))
        assertNull(componentProblem(ok.copy(lossText = "99.99"), "pizza"))
        assertEquals("Waste: At most 2 decimals", componentProblem(ok.copy(lossText = "1.234"), "pizza"))
        val request = componentRequest(ok, 4)
        assertEquals("INVENTORY_ITEM", request.componentType)
        assertEquals("0.12", request.quantity.value)
        assertEquals("12.5", request.yieldLossPercent?.value)
        assertEquals(4, request.displayOrder)
        assertEquals("SUB_RECIPE", componentRequest(ComponentDraft(childRecipeId = "dough", quantityText = "1"), 0).componentType)
    }
}

class RecipesScreenModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)
    @AfterTest fun after() = Dispatchers.resetMain()

    private suspend fun TestScope.check(
        repo: FakeRecipes = FakeRecipes(),
        permissions: List<String> = listOf("SETTINGS_READ", "SETTINGS_UPDATE"),
        block: suspend TestScope.(RecipesScreenModel, FakeRecipes) -> Unit
    ) {
        val session = SessionManager().apply { signIn(user(permissions = permissions)) }
        val model = RecipesScreenModel(repo, session)
        try {
            runCurrent(); model.setActive(true); runCurrent(); block(model, repo)
        } finally {
            model.onDispose(); runCurrent()
        }
    }

    @Test
    fun listsFiltersAndSearches() = runTest(dispatcher) {
        check { model, repo ->
            assertEquals(listOf("Dough", "Margherita", "Old sauce"), model.state.value.visible.map { it.name })
            model.search("marg")
            assertEquals(listOf("Margherita"), model.state.value.visible.map { it.name })
            model.filter(null, "ACTIVE"); runCurrent()
            assertEquals("list null ACTIVE", repo.calls.last())
            model.filter("SOUP", null)
            assertEquals("ACTIVE", model.state.value.status)
        }
    }

    @Test
    fun ingredientsGoLastOrKeepTheirPlace() = runTest(dispatcher) {
        check { model, repo ->
            model.open("pizza"); runCurrent()
            assertEquals(listOf("dough"), model.state.value.subRecipeChoices.map { it.id })
            model.newComponent("basil")
            model.changeComponent { it.copy(quantityText = "5", unit = "GRAM") }
            model.saveComponent(); runCurrent()
            assertEquals(4, repo.components.last().displayOrder)
            assertEquals(3, model.state.value.open?.components?.size)
            assertNull(model.state.value.component)
            model.newComponent("tom")
            model.changeComponent { it.copy(quantityText = "0.150", unit = "KILOGRAM") }
            model.saveComponent(); runCurrent()
            assertEquals(0, repo.components.last().displayOrder)
            model.removeComponent("c2"); runCurrent()
            assertTrue("remove c2" in repo.calls)
            model.removeComponent("nope"); runCurrent()
            assertEquals(1, repo.calls.count { it.startsWith("remove") })
        }
    }

    @Test
    fun newRecipesAreCheckedSavedAndOpened() = runTest(dispatcher) {
        check { model, repo ->
            model.newRecipe(menuItemId = "menu-1")
            model.saveRecipe(); runCurrent()
            assertEquals("Enter a name", model.state.value.error)
            model.change { it.copy(name = " Calzone ", id = "hijack") }
            assertNull(model.state.value.draft?.id)
            model.saveRecipe(); runCurrent()
            assertEquals("menu-1", repo.saved.single().menuItemId)
            assertEquals("new", model.state.value.open?.id)
            assertTrue(model.state.value.recipes.any { it.name == "Calzone" })
            assertEquals("Calzone was saved", model.state.value.notice)
        }
    }

    @Test
    fun archivingAsksFirstAndLeavesAFilteredList() = runTest(dispatcher) {
        check { model, repo ->
            model.filter(null, "ACTIVE"); runCurrent()
            model.open("pizza"); runCurrent()
            model.askArchive()
            assertEquals("pizza", model.state.value.confirmArchive)
            model.confirmArchive(); runCurrent()
            assertTrue("archive pizza" in repo.calls)
            assertEquals("ARCHIVED", model.state.value.open?.status)
            assertTrue(model.state.value.recipes.none { it.id == "pizza" })
            model.recalculateCost(); runCurrent()
            assertEquals("1.35", model.state.value.open?.theoreticalCost?.value)
        }
    }

    @Test
    fun readOnlyPeopleCantChangeRecipes() = runTest(dispatcher) {
        check(permissions = listOf("SETTINGS_READ")) { model, repo ->
            model.open("pizza"); runCurrent()
            model.newRecipe()
            assertNull(model.state.value.draft)
            model.recalculateCost(); runCurrent()
            model.newComponent("tom")
            assertNull(model.state.value.component)
            assertEquals("You can only look at recipes", model.state.value.error)
            assertTrue(repo.calls.none { it.startsWith("cost") })
        }
    }

    @Test
    fun aFailedSaveKeepsTheDraft() = runTest(dispatcher) {
        check { model, repo ->
            model.newRecipe()
            model.change { it.copy(name = "Soup", recipeType = "PREP_BATCH") }
            repo.failure = ApiException(409, "A recipe with this code already exists")
            model.saveRecipe(); runCurrent()
            assertEquals("A recipe with this code already exists", model.state.value.error)
            assertEquals("Soup", model.state.value.draft?.name)
            assertFalse(model.state.value.saving)
        }
    }
}

class RecipesApiTest {
    @Test
    fun pathsAndBodies() = runTest {
        val seen = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            seen += "${request.method.value} ${request.url.encodedPath}?${request.url.encodedQuery} ${(request.body as? TextContent)?.text.orEmpty()}"
            val body = if (request.method.value == "GET" && request.url.encodedPath.endsWith("/recipes")) "[]"
            else """{"id":"r","name":"R","recipeType":"FINISHED_DISH","theoreticalCost":0.4567,"components":[{"id":"c","componentType":"INVENTORY_ITEM","quantity":0.120,"unit":"KILOGRAM"}]}"""
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            val api = RecipesApi(client) { "http://localhost/" }
            api.recipes("rest", "PREP_BATCH", "ACTIVE")
            val saved = api.putComponent("rest", "r", componentRequest(ComponentDraft(inventoryItemId = "i", quantityText = "0.120", unit = "KILOGRAM"), 2))
            assertEquals("0.120", saved.components.single().quantity.value)
            assertEquals("0.4567", saved.theoreticalCost?.value)
            api.removeComponent("rest", "r", "c")
            api.archive("rest", "r")
            api.recalculateCost("rest", "r")
            api.save("rest", "r", recipeRequest(RecipeDraft(name = "R")))
            assertTrue(seen.any { it.startsWith("GET /restaurants/rest/recipes?type=PREP_BATCH&status=ACTIVE") }, seen.toString())
            assertTrue(seen.any { it.startsWith("PUT /restaurants/rest/recipes/r/components") && "\"quantity\":0.12" in it && "\"optionalComponent\":false" in it }, seen.toString())
            assertTrue(seen.any { it.startsWith("DELETE /restaurants/rest/recipes/r/components/c") }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /restaurants/rest/recipes/r/archive") }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /restaurants/rest/recipes/r/recalculate-cost") }, seen.toString())
            assertTrue(seen.any { it.startsWith("PUT /restaurants/rest/recipes/r?") && "\"status\":\"DRAFT\"" in it }, seen.toString())
        } finally {
            client.close()
        }
    }
}
