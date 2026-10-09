package com.saporini.mobile_desktop.admin.inventory

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

// Admin Hub → Inventory → Recipes: what goes into a dish or a prep batch, and what it costs.

val RECIPE_TYPES = listOf("FINISHED_DISH", "PREP_BATCH", "SUB_RECIPE")
val RECIPE_STATUSES = listOf("DRAFT", "ACTIVE", "ARCHIVED")

@Serializable
data class RecipeComponentDto(
    val id: String,
    val componentType: String,
    val inventoryItemId: String? = null,
    val inventoryItemName: String? = null,
    val childRecipeId: String? = null,
    val childRecipeName: String? = null,
    val componentNameSnapshot: String? = null,
    val quantity: OrderDecimal = OrderDecimal("0"),
    val unit: String,
    val yieldLossPercent: OrderDecimal? = null,
    val optionalComponent: Boolean = false,
    val displayOrder: Int = 0,
    val notes: String? = null
) {
    val name: String get() = inventoryItemName ?: childRecipeName ?: componentNameSnapshot ?: "?"
}

@Serializable
data class RecipeDto(
    val id: String,
    val menuItemId: String? = null,
    val menuItemName: String? = null,
    val code: String? = null,
    val name: String,
    val description: String? = null,
    val recipeType: String,
    val status: String = "DRAFT",
    val version: Int = 0,
    val yieldQuantity: OrderDecimal? = null,
    val yieldUnit: String? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val instructions: String? = null,
    val theoreticalCost: OrderDecimal? = null,
    val updatedByUserName: String? = null,
    val updatedAt: String? = null,
    val components: List<RecipeComponentDto> = emptyList()
)

@Serializable
data class RecipeRequestDto(
    val code: String? = null,
    val name: String,
    val description: String? = null,
    val menuItemId: String? = null,
    val recipeType: String,
    val status: String,
    val yieldQuantity: OrderDecimal? = null,
    val yieldUnit: String? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val instructions: String? = null
)

@Serializable
data class RecipeComponentRequestDto(
    val componentType: String,
    val inventoryItemId: String? = null,
    val childRecipeId: String? = null,
    val quantity: OrderDecimal,
    val unit: String,
    val yieldLossPercent: OrderDecimal? = null,
    val optionalComponent: Boolean,
    val displayOrder: Int? = null,
    val notes: String? = null
)

interface RecipesRepository {
    suspend fun recipes(restaurantId: String, type: String?, status: String?): List<RecipeDto>
    suspend fun recipe(restaurantId: String, recipeId: String): RecipeDto
    suspend fun save(restaurantId: String, recipeId: String?, request: RecipeRequestDto): RecipeDto
    suspend fun putComponent(restaurantId: String, recipeId: String, request: RecipeComponentRequestDto): RecipeDto
    suspend fun removeComponent(restaurantId: String, recipeId: String, componentId: String): RecipeDto
    suspend fun archive(restaurantId: String, recipeId: String): RecipeDto
    suspend fun recalculateCost(restaurantId: String, recipeId: String): RecipeDto
}

class RecipesApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : RecipesRepository {

    private fun url(restaurantId: String, path: String = "") =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/recipes$path"
    private fun id(value: String) = value.encodeURLPathPart()

    override suspend fun recipes(restaurantId: String, type: String?, status: String?): List<RecipeDto> = client.get(url(restaurantId)) {
        type?.let { parameter("type", it) }
        status?.let { parameter("status", it) }
    }.body()

    override suspend fun recipe(restaurantId: String, recipeId: String): RecipeDto = client.get(url(restaurantId, "/${id(recipeId)}")).body()

    override suspend fun save(restaurantId: String, recipeId: String?, request: RecipeRequestDto): RecipeDto =
        if (recipeId == null) client.post(url(restaurantId)) { contentType(ContentType.Application.Json); setBody(request) }.body()
        else client.put(url(restaurantId, "/${id(recipeId)}")) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun putComponent(restaurantId: String, recipeId: String, request: RecipeComponentRequestDto): RecipeDto =
        client.put(url(restaurantId, "/${id(recipeId)}/components")) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun removeComponent(restaurantId: String, recipeId: String, componentId: String): RecipeDto =
        client.delete(url(restaurantId, "/${id(recipeId)}/components/${id(componentId)}")).body()

    override suspend fun archive(restaurantId: String, recipeId: String): RecipeDto = client.post(url(restaurantId, "/${id(recipeId)}/archive")).body()

    override suspend fun recalculateCost(restaurantId: String, recipeId: String): RecipeDto =
        client.post(url(restaurantId, "/${id(recipeId)}/recalculate-cost")).body()
}

const val MAX_RECIPE_INSTRUCTIONS = 10000
const val MAX_RECIPE_MINUTES = 10080

data class RecipeDraft(
    val id: String? = null,
    val name: String = "",
    val code: String = "",
    val description: String = "",
    val menuItemId: String? = null,
    val recipeType: String = "FINISHED_DISH",
    val status: String = "DRAFT",
    val yieldText: String = "",
    val yieldUnit: String? = null,
    val prepText: String = "",
    val cookText: String = "",
    val instructions: String = ""
)

fun recipeDraftOf(recipe: RecipeDto) = RecipeDraft(
    id = recipe.id, name = recipe.name, code = recipe.code.orEmpty(), description = recipe.description.orEmpty(), menuItemId = recipe.menuItemId,
    recipeType = recipe.recipeType, status = recipe.status, yieldText = recipe.yieldQuantity?.value.orEmpty(), yieldUnit = recipe.yieldUnit,
    prepText = recipe.prepTimeMinutes?.toString().orEmpty(), cookText = recipe.cookTimeMinutes?.toString().orEmpty(),
    instructions = recipe.instructions.orEmpty()
)

private fun minutesProblem(text: String, what: String): String? {
    if (text.isBlank()) return null
    val minutes = text.trim().toIntOrNull() ?: return "$what: enter whole minutes"
    return if (minutes !in 0..MAX_RECIPE_MINUTES) "$what: 0 to $MAX_RECIPE_MINUTES minutes" else null
}

fun recipeProblem(draft: RecipeDraft): String? {
    val name = draft.name.trim()
    return when {
        name.isEmpty() -> "Enter a name"
        name.length > MAX_STOCK_NAME -> "The name can be at most $MAX_STOCK_NAME characters"
        draft.code.trim().length > MAX_STOCK_CODE -> "The code can be at most $MAX_STOCK_CODE characters"
        draft.recipeType !in RECIPE_TYPES -> "Choose what kind of recipe this is"
        draft.recipeType == "FINISHED_DISH" && draft.menuItemId == null -> "Choose the dish on the menu this recipe makes"
        draft.status !in RECIPE_STATUSES -> "Choose a status"
        draft.description.trim().length > MAX_STOCK_TEXT -> "The description can be at most $MAX_STOCK_TEXT characters"
        draft.instructions.trim().length > MAX_RECIPE_INSTRUCTIONS -> "Instructions can be at most $MAX_RECIPE_INSTRUCTIONS characters"
        else -> typedNumber(draft.yieldText, required = false).problem?.let { "Makes: $it" }
            ?: "Choose the unit it makes".takeIf { draft.yieldText.isNotBlank() && draft.yieldUnit !in INVENTORY_UNITS }
            ?: minutesProblem(draft.prepText, "Prep time")
            ?: minutesProblem(draft.cookText, "Cooking time")
    }
}

fun recipeRequest(draft: RecipeDraft) = RecipeRequestDto(
    code = draft.code.trim().ifEmpty { null },
    name = draft.name.trim(),
    description = draft.description.trim().ifEmpty { null },
    menuItemId = draft.menuItemId,
    recipeType = draft.recipeType,
    status = draft.status,
    yieldQuantity = typedNumber(draft.yieldText, required = false).value,
    yieldUnit = draft.yieldUnit.takeIf { draft.yieldText.isNotBlank() },
    prepTimeMinutes = draft.prepText.trim().toIntOrNull(),
    cookTimeMinutes = draft.cookText.trim().toIntOrNull(),
    instructions = draft.instructions.trim().ifEmpty { null }
)

/** One ingredient (a stock item) or a sub-recipe being added to a recipe. */
data class ComponentDraft(
    val inventoryItemId: String? = null,
    val childRecipeId: String? = null,
    val quantityText: String = "",
    val unit: String = "GRAM",
    val lossText: String = "",
    val optional: Boolean = false,
    val notes: String = ""
)

fun componentProblem(draft: ComponentDraft, recipeId: String): String? = when {
    (draft.inventoryItemId == null) == (draft.childRecipeId == null) -> "Choose a stock item or a recipe"
    draft.childRecipeId == recipeId -> "A recipe can't contain itself"
    draft.unit !in INVENTORY_UNITS -> "Choose a unit"
    else -> typedNumber(draft.quantityText).problem?.let { "Quantity: $it" }
        ?: typedNumber(draft.lossText, digits = 3, decimals = 2, required = false, allowZero = true).let { loss ->
            loss.problem?.let { "Waste: $it" }
                // At 100% nothing is left to use, which can't be priced.
                ?: loss.value?.takeIf { compareDecimals(it, OrderDecimal("100")) >= 0 }?.let { "Waste must be below 100%" }
        }
        ?: "Notes can be at most $MAX_STOCK_TEXT characters".takeIf { draft.notes.trim().length > MAX_STOCK_TEXT }
}

fun componentRequest(draft: ComponentDraft, displayOrder: Int) = RecipeComponentRequestDto(
    componentType = if (draft.inventoryItemId != null) "INVENTORY_ITEM" else "SUB_RECIPE",
    inventoryItemId = draft.inventoryItemId,
    childRecipeId = draft.childRecipeId,
    quantity = typedNumber(draft.quantityText).value ?: OrderDecimal("0"),
    unit = draft.unit,
    yieldLossPercent = typedNumber(draft.lossText, digits = 3, decimals = 2, required = false, allowZero = true).value,
    optionalComponent = draft.optional,
    displayOrder = displayOrder,
    notes = draft.notes.trim().ifEmpty { null }
)
