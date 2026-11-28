package com.saporini.mobile_desktop.admin.inventory

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.menu.ui.item.PhotoChange
import com.saporini.mobile_desktop.pos.menu.data.dto.IngredientChoiceDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

// Admin Hub → Inventory → Preparations: what goes into a prep batch or a shared part, and what it costs.
// A dish's own recipe (FINISHED_DISH) is set in Menu → dish → "Recipe / Ingredients" and isn't shown here.

val PREPARATION_TYPES = listOf("PREP_BATCH", "SUB_RECIPE")
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
    val components: List<RecipeComponentDto> = emptyList(),
    val imageUrl: String? = null,
    // The item a sub-recipe makes (Items); null for a dish's recipe.
    val outputItemId: String? = null
)

/** A sub-recipe's ingredient for saving: exactly one of a stock item and another sub-recipe. */
@Serializable
data class SubRecipeIngredientRequestDto(
    val inventoryItemId: String? = null,
    val subRecipeId: String? = null,
    val quantity: OrderDecimal,
    val unit: String
)

/** A whole sub-recipe for saving at once: its item's name and kind, what one batch makes, and its ingredients in order. */
@Serializable
data class SubRecipeRequestDto(
    val name: String,
    val itemType: String,
    val yieldQuantity: OrderDecimal,
    val yieldUnit: String,
    val ingredients: List<SubRecipeIngredientRequestDto>,
    val description: String? = null,
    val instructions: String? = null,
    val active: Boolean? = null
)

/** One ingredient of a sub-recipe; [kind] is STOCK or PREPARATION (another sub-recipe), as in the dish editor. */
@Serializable
data class SubRecipeIngredientDto(
    val kind: String,
    val id: String,
    val name: String,
    val quantity: OrderDecimal,
    val unit: String,
    val cost: OrderDecimal? = null,
    val imageUrl: String? = null
)

/**
 * A sub-recipe: something the kitchen makes (a sauce, a dough). It's an item in Items ([itemId]) with its recipe
 * ([recipeId]): what one batch makes, what goes in, and what a batch and one unit cost from today's prices.
 */
@Serializable
data class SubRecipeDto(
    val recipeId: String,
    val itemId: String? = null,
    val name: String,
    val itemType: String,
    val yieldQuantity: OrderDecimal,
    val yieldUnit: String,
    val ingredients: List<SubRecipeIngredientDto> = emptyList(),
    val batchCost: OrderDecimal? = null,
    val costPerUnit: OrderDecimal? = null,
    val imageUrl: String? = null,
    val description: String? = null,
    val instructions: String? = null,
    val active: Boolean = true
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
    fun observeChanges(restaurantId: String): Flow<Unit> = emptyFlow()
    suspend fun recipes(restaurantId: String, type: String?, status: String?): List<RecipeDto>
    suspend fun recipe(restaurantId: String, recipeId: String): RecipeDto
    suspend fun save(restaurantId: String, recipeId: String?, request: RecipeRequestDto): RecipeDto
    suspend fun putComponent(restaurantId: String, recipeId: String, request: RecipeComponentRequestDto): RecipeDto
    suspend fun removeComponent(restaurantId: String, recipeId: String, componentId: String): RecipeDto
    suspend fun archive(restaurantId: String, recipeId: String): RecipeDto
    suspend fun recalculateCost(restaurantId: String, recipeId: String): RecipeDto
    suspend fun uploadPhoto(restaurantId: String, recipeId: String, bytes: ByteArray, fileName: String, contentType: String): RecipeDto =
        throw UnsupportedOperationException("House-made photos are unavailable")
    suspend fun removePhoto(restaurantId: String, recipeId: String): RecipeDto =
        throw UnsupportedOperationException("House-made photos are unavailable")

    // Sub-recipes, saved whole (their item and recipe together), like a dish's ingredients.
    suspend fun subRecipeChoices(restaurantId: String, forRecipeId: String?): List<IngredientChoiceDto> =
        throw UnsupportedOperationException("Sub-recipes are unavailable")
    suspend fun subRecipe(restaurantId: String, recipeId: String): SubRecipeDto =
        throw UnsupportedOperationException("Sub-recipes are unavailable")
    suspend fun saveSubRecipe(restaurantId: String, recipeId: String?, request: SubRecipeRequestDto): SubRecipeDto =
        throw UnsupportedOperationException("Sub-recipes are unavailable")
    /** A sub-recipe's photo is its item's photo. */
    suspend fun uploadItemPhoto(restaurantId: String, itemId: String, bytes: ByteArray, fileName: String, contentType: String) {
        throw UnsupportedOperationException("Photos are unavailable")
    }
    suspend fun removeItemPhoto(restaurantId: String, itemId: String) {
        throw UnsupportedOperationException("Photos are unavailable")
    }
}

class RecipesApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : RecipesRepository {

    private fun url(restaurantId: String, path: String = "") =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/recipes$path"
    private fun id(value: String) = value.encodeURLPathPart()

    override fun observeChanges(restaurantId: String): Flow<Unit> = InventoryApi(client, baseUrlProvider).observeChanges(restaurantId)

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

    override suspend fun uploadPhoto(restaurantId: String, recipeId: String, bytes: ByteArray, fileName: String, contentType: String): RecipeDto =
        client.put("${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/recipes/${id(recipeId)}/image") {
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
                })
            }))
        }.body()

    override suspend fun removePhoto(restaurantId: String, recipeId: String): RecipeDto =
        client.delete("${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/recipes/${id(recipeId)}/image").body()

    private fun subRecipes(restaurantId: String, path: String = "") =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/sub-recipes$path"

    override suspend fun subRecipeChoices(restaurantId: String, forRecipeId: String?): List<IngredientChoiceDto> =
        client.get(subRecipes(restaurantId, "/choices")) { forRecipeId?.let { parameter("forRecipeId", it) } }.body()

    override suspend fun subRecipe(restaurantId: String, recipeId: String): SubRecipeDto = client.get(subRecipes(restaurantId, "/${id(recipeId)}")).body()

    override suspend fun saveSubRecipe(restaurantId: String, recipeId: String?, request: SubRecipeRequestDto): SubRecipeDto =
        if (recipeId == null) client.post(subRecipes(restaurantId)) { contentType(ContentType.Application.Json); setBody(request) }.body()
        else client.put(subRecipes(restaurantId, "/${id(recipeId)}")) { contentType(ContentType.Application.Json); setBody(request) }.body()

    private fun itemImage(restaurantId: String, itemId: String) =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/inventory/items/${id(itemId)}/image"

    override suspend fun uploadItemPhoto(restaurantId: String, itemId: String, bytes: ByteArray, fileName: String, contentType: String) {
        client.put(itemImage(restaurantId, itemId)) {
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
                })
            }))
        }.body<StockItemDto>()
    }

    override suspend fun removeItemPhoto(restaurantId: String, itemId: String) {
        client.delete(itemImage(restaurantId, itemId)).body<StockItemDto>()
    }
}

// A method can be long: a detailed prep (a laminated dough, a three-day stock) runs to several thousand characters.
const val MAX_RECIPE_INSTRUCTIONS = 20000
const val MAX_RECIPE_MINUTES = 10080

data class RecipeDraft(
    val id: String? = null,
    val name: String = "",
    val code: String = "",
    val description: String = "",
    val menuItemId: String? = null,
    val recipeType: String = "PREP_BATCH",
    val status: String = "DRAFT",
    val yieldText: String = "",
    val yieldUnit: String? = null,
    val prepText: String = "",
    val cookText: String = "",
    val instructions: String = "",
    val imageUrl: String? = null,
    // Only for a new house-made item: what it's made from, saved right after it (an existing one changes its ingredients on its page).
    val ingredients: List<IngredientDraft> = listOf(IngredientDraft(0)),
    val photo: PhotoChange = PhotoChange.Keep
)

/** One line of what a new house-made item is made from: an existing stock item or house-made item, and how much. */
data class IngredientDraft(
    val key: Int,
    val inventoryItemId: String? = null,
    val childRecipeId: String? = null,
    val quantityText: String = "",
    val unit: String = "GRAM"
) {
    /** A line nothing was filled in on; it's left out. */
    val blank: Boolean get() = inventoryItemId == null && childRecipeId == null && quantityText.isBlank()
}

/** What's wrong with the ingredient lines of a new house-made item, if anything. */
fun ingredientsProblem(lines: List<IngredientDraft>): String? {
    val filled = lines.filterNot { it.blank }
    if (filled.isEmpty()) return "Add at least one ingredient it's made from"
    filled.forEachIndexed { index, line ->
        val which = "Ingredient ${index + 1}"
        if (line.inventoryItemId == null && line.childRecipeId == null) return "$which: choose what it is"
        if (line.unit !in INVENTORY_UNITS) return "$which: choose a unit"
        typedNumber(line.quantityText).problem?.let { return "$which: $it" }
    }
    val chosen = filled.map { it.inventoryItemId ?: it.childRecipeId }
    if (chosen.size != chosen.toSet().size) return "An ingredient is in the list twice; put it once with the full amount"
    return null
}

fun ingredientRequest(line: IngredientDraft, displayOrder: Int) = componentRequest(
    ComponentDraft(inventoryItemId = line.inventoryItemId, childRecipeId = line.childRecipeId, quantityText = line.quantityText, unit = line.unit),
    displayOrder
)

fun recipeDraftOf(recipe: RecipeDto) = RecipeDraft(
    id = recipe.id, name = recipe.name, code = recipe.code.orEmpty(), description = recipe.description.orEmpty(), menuItemId = recipe.menuItemId,
    recipeType = recipe.recipeType, status = recipe.status, yieldText = recipe.yieldQuantity?.value.orEmpty(), yieldUnit = recipe.yieldUnit,
    prepText = recipe.prepTimeMinutes?.toString().orEmpty(), cookText = recipe.cookTimeMinutes?.toString().orEmpty(),
    instructions = recipe.instructions.orEmpty(), imageUrl = recipe.imageUrl
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
        draft.recipeType !in PREPARATION_TYPES -> "Choose what kind of preparation this is"
        draft.status !in RECIPE_STATUSES -> "Choose a status"
        draft.description.trim().length > MAX_STOCK_TEXT -> "The description can be at most $MAX_STOCK_TEXT characters"
        draft.instructions.trim().length > MAX_RECIPE_INSTRUCTIONS -> "Instructions can be at most $MAX_RECIPE_INSTRUCTIONS characters"
        else -> typedNumber(draft.yieldText, required = false).problem?.let { "Makes: $it" }
            ?: "Choose the unit it makes".takeIf { draft.yieldText.isNotBlank() && draft.yieldUnit !in INVENTORY_UNITS }
            ?: minutesProblem(draft.prepText, "Prep time")
            ?: minutesProblem(draft.cookText, "Cooking time")
            ?: if (draft.id == null) ingredientsProblem(draft.ingredients) else null
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

// ---- Sub-recipes (Items → "Add sub-recipe") ----

/** One ingredient of a sub-recipe being edited: a stock item (STOCK) or another sub-recipe (PREPARATION), how much and in what. */
data class SubRecipeLine(
    val kind: String,
    val refId: String,
    val name: String,
    val quantity: String,
    val unit: String,
    val imageUrl: String? = null,
    // What this line cost when it was loaded, for a line whose choice isn't offered any more.
    val loadedCost: Double? = null
) {
    val key: String get() = "$kind:$refId"
}

/** The sub-recipe editor: the item's name, kind and photo, what one batch makes, and the ingredients, all saved together. */
data class SubRecipeDraft(
    val recipeId: String? = null,
    val itemId: String? = null,
    val name: String = "",
    val itemType: String = "PREPARED_COMPONENT",
    val yieldText: String = "",
    val yieldUnit: String = "KILOGRAM",
    val lines: List<SubRecipeLine> = emptyList(),
    val description: String = "",
    val instructions: String = "",
    val active: Boolean = true,
    val imageUrl: String? = null,
    val photo: PhotoChange = PhotoChange.Keep,
    // An existing one is being fetched; the editor shows its lines once it's here.
    val loading: Boolean = false
)

fun subRecipeDraftOf(saved: SubRecipeDto) = SubRecipeDraft(
    recipeId = saved.recipeId, itemId = saved.itemId, name = saved.name, itemType = saved.itemType,
    yieldText = saved.yieldQuantity.value.let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it }, yieldUnit = saved.yieldUnit,
    lines = saved.ingredients.map { line ->
        SubRecipeLine(line.kind, line.id, line.name, line.quantity.value.let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it },
            line.unit, line.imageUrl, line.cost?.value?.toDoubleOrNull())
    },
    description = saved.description.orEmpty(), instructions = saved.instructions.orEmpty(), active = saved.active, imageUrl = saved.imageUrl
)

/** What's wrong with the sub-recipe, if anything, said for the person typing it. */
fun subRecipeProblem(draft: SubRecipeDraft): String? {
    val name = draft.name.trim()
    return when {
        name.isEmpty() -> "Give it a name"
        name.length > MAX_STOCK_NAME -> "The name can be at most $MAX_STOCK_NAME characters"
        draft.itemType !in INVENTORY_ITEM_TYPES -> "Choose what kind of item it is"
        draft.yieldUnit !in INVENTORY_UNITS -> "Choose the unit a batch is measured in"
        draft.lines.isEmpty() -> "Add at least one ingredient"
        draft.description.trim().length > MAX_STOCK_TEXT -> "The description can be at most $MAX_STOCK_TEXT characters"
        draft.instructions.trim().length > MAX_RECIPE_INSTRUCTIONS -> "The method can be at most $MAX_RECIPE_INSTRUCTIONS characters"
        else -> typedNumber(draft.yieldText).problem?.let { "How much a batch makes: $it" }
            ?: draft.lines.firstNotNullOfOrNull { line -> typedNumber(line.quantity).problem?.let { "${line.name}: $it" } }
    }
}

fun subRecipeRequest(draft: SubRecipeDraft) = SubRecipeRequestDto(
    name = draft.name.trim(),
    itemType = draft.itemType,
    yieldQuantity = typedNumber(draft.yieldText).value ?: OrderDecimal("1"),
    yieldUnit = draft.yieldUnit,
    ingredients = draft.lines.map { line ->
        SubRecipeIngredientRequestDto(
            inventoryItemId = line.refId.takeIf { line.kind != "PREPARATION" },
            subRecipeId = line.refId.takeIf { line.kind == "PREPARATION" },
            quantity = typedNumber(line.quantity).value ?: OrderDecimal("0"),
            unit = line.unit
        )
    },
    description = draft.description.trim().ifEmpty { null },
    instructions = draft.instructions.trim().ifEmpty { null },
    // A new one starts in use; an existing one keeps what the switch says.
    active = if (draft.recipeId == null) null else draft.active
)
