package com.saporini.mobile_desktop.admin.inventory

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.admin.adminMessage
import com.saporini.mobile_desktop.admin.isDenied
import com.saporini.mobile_desktop.admin.isStale
import com.saporini.mobile_desktop.core.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RecipesState(
    val restaurantId: String? = null,
    val canRead: Boolean = false,
    val canEdit: Boolean = false,
    val type: String? = null,
    val status: String? = null,
    val search: String = "",
    val recipes: List<RecipeDto> = emptyList(),
    val open: RecipeDto? = null,
    val draft: RecipeDraft? = null,
    val component: ComponentDraft? = null,
    val confirmArchive: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val stale: Boolean = true,
    val notice: String? = null,
    val error: String? = null
) {
    val visible: List<RecipeDto> get() {
        val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return recipes.filter { recipe ->
            val text = listOfNotNull(recipe.name, recipe.code, recipe.menuItemName).joinToString(" ").lowercase()
            words.all { it in text }
        }.sortedBy { it.name.lowercase() }
    }

    /** Recipes that can go inside the open one (not itself, not archived). */
    val subRecipeChoices: List<RecipeDto> get() = recipes.filter { it.id != open?.id && it.status != "ARCHIVED" }
}

/**
 * Admin Hub → Inventory → Recipes: the list (by type and status), one recipe with its ingredients and cost,
 * adding/editing recipes and ingredients, archiving, and re-pricing from today's stock costs.
 */
class RecipesScreenModel(
    private val repository: RecipesRepository,
    session: SessionManager
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(RecipesState())
    val state: StateFlow<RecipesState> = mutable.asStateFlow()
    private val writes = Mutex()
    private var revision = 0L
    private var signIn = 0L
    private var active = false
    private var loadJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                signIn++
                loadJob?.cancel()
                val permissions = user?.takeIf { it.isActive }?.permissions.orEmpty().toSet()
                mutable.value = RecipesState(
                    restaurantId = user?.takeIf { it.isActive }?.restaurantId,
                    canRead = "SETTINGS_READ" in permissions,
                    canEdit = "SETTINGS_UPDATE" in permissions
                )
                if (active) load()
            }
        }
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load() else {
            revision++
            loadJob?.cancel()
            mutable.update { it.copy(loading = false) }
        }
    }

    fun filter(type: String?, status: String?) {
        if (type != null && type !in RECIPE_TYPES || status != null && status !in RECIPE_STATUSES) return
        if (state.value.type == type && state.value.status == status) return
        mutable.update { it.copy(type = type, status = status, recipes = emptyList()) }
        load()
    }

    fun search(text: String) = mutable.update { it.copy(search = text.take(MAX_STOCK_NAME)) }

    fun refresh() = load()

    private fun load() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (!active || !current.canRead) return
        val token = ++revision
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                val recipes = repository.recipes(restaurantId, current.type, current.status)
                val open = current.open?.let { repository.recipe(restaurantId, it.id) }
                if (token == revision) mutable.update { it.copy(recipes = recipes, open = open, stale = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(stale = true, error = adminMessage(e, write = false), canRead = it.canRead && !isDenied(e)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    fun open(recipeId: String?) {
        val restaurantId = state.value.restaurantId ?: return
        if (recipeId == null) {
            mutable.update { it.copy(open = null, component = null) }
            return
        }
        val token = revision
        work.launch {
            try {
                val recipe = repository.recipe(restaurantId, recipeId)
                if (token == revision) mutable.update { it.copy(open = recipe, component = null, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = adminMessage(e, write = false)) }
            }
        }
    }

    // ---- Recipes ----

    fun newRecipe(menuItemId: String? = null) = needsEdit { mutable.update { it.copy(draft = RecipeDraft(menuItemId = menuItemId), notice = null) } }

    fun editRecipe() = needsEdit {
        val recipe = state.value.open ?: return@needsEdit
        mutable.update { it.copy(draft = recipeDraftOf(recipe), notice = null) }
    }

    fun change(change: (RecipeDraft) -> RecipeDraft) = mutable.update { state ->
        val draft = state.draft ?: return@update state
        state.copy(draft = change(draft).copy(id = draft.id))
    }

    fun cancelRecipe() = mutable.update { it.copy(draft = null) }

    fun saveRecipe() {
        val draft = state.value.draft ?: return
        recipeProblem(draft)?.let { problem ->
            mutable.update { it.copy(error = problem) }
            return
        }
        write("${draft.name.trim()} was saved") { restaurantId ->
            val saved = repository.save(restaurantId, draft.id, recipeRequest(draft))
            mutable.update { it.copy(draft = null, open = saved) }
            applyRecipe(saved)
        }
    }

    fun askArchive() = needsEdit { mutable.update { it.copy(confirmArchive = it.open?.id) } }

    fun dismissArchive() = mutable.update { it.copy(confirmArchive = null) }

    fun confirmArchive() {
        val recipeId = state.value.confirmArchive ?: return
        mutable.update { it.copy(confirmArchive = null) }
        write("The recipe was archived") { restaurantId -> applyRecipe(repository.archive(restaurantId, recipeId)) }
    }

    /** Prices the open recipe again from today's stock costs. */
    fun recalculateCost() {
        val recipeId = state.value.open?.id ?: return
        needsEdit { write("Cost updated") { restaurantId -> applyRecipe(repository.recalculateCost(restaurantId, recipeId)) } }
    }

    // ---- Ingredients ----

    fun newComponent(inventoryItemId: String? = null, unit: String = "GRAM") = needsEdit {
        if (state.value.open == null) return@needsEdit
        mutable.update { it.copy(component = ComponentDraft(inventoryItemId = inventoryItemId, unit = unit), notice = null) }
    }

    fun changeComponent(change: (ComponentDraft) -> ComponentDraft) = mutable.update { state ->
        state.copy(component = state.component?.let(change))
    }

    fun cancelComponent() = mutable.update { it.copy(component = null) }

    fun saveComponent() {
        val current = state.value
        val recipe = current.open ?: return
        val draft = current.component ?: return
        componentProblem(draft, recipe.id)?.let { problem ->
            mutable.update { it.copy(error = problem) }
            return
        }
        // An ingredient already in the recipe keeps its place; a new one goes last.
        val existing = recipe.components.firstOrNull {
            (draft.inventoryItemId != null && it.inventoryItemId == draft.inventoryItemId) ||
                (draft.childRecipeId != null && it.childRecipeId == draft.childRecipeId)
        }
        val order = existing?.displayOrder ?: ((recipe.components.maxOfOrNull { it.displayOrder } ?: -1) + 1)
        write(null) { restaurantId ->
            applyRecipe(repository.putComponent(restaurantId, recipe.id, componentRequest(draft, order)))
            mutable.update { it.copy(component = null) }
        }
    }

    fun removeComponent(componentId: String) {
        val recipe = state.value.open ?: return
        if (recipe.components.none { it.id == componentId }) return
        needsEdit { write(null) { restaurantId -> applyRecipe(repository.removeComponent(restaurantId, recipe.id, componentId)) } }
    }

    // ---- Plumbing ----

    private fun applyRecipe(saved: RecipeDto) = mutable.update {
        val matches = (it.type == null || saved.recipeType == it.type) && (it.status == null || saved.status == it.status)
        it.copy(open = if (it.open?.id == saved.id) saved else it.open,
            recipes = if (matches) it.recipes.put(saved) else it.recipes.filterNot { r -> r.id == saved.id })
    }

    private fun List<RecipeDto>.put(saved: RecipeDto) = if (any { it.id == saved.id }) map { if (it.id == saved.id) saved else it } else this + saved

    private inline fun needsEdit(action: () -> Unit) {
        if (!state.value.canEdit) {
            mutable.update { it.copy(error = "You can only look at recipes") }
            return
        }
        action()
    }

    private fun write(notice: String?, action: suspend (restaurantId: String) -> Unit) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (current.saving) return
        if (!current.canEdit) {
            mutable.update { it.copy(error = "You can only look at recipes") }
            return
        }
        val token = signIn
        mutable.update { it.copy(saving = true, error = null, notice = null) }
        work.launch {
            writes.withLock {
                try {
                    if (token != signIn) return@withLock
                    action(restaurantId)
                    if (notice != null && token == signIn) mutable.update { it.copy(notice = notice) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != signIn) return@withLock
                    mutable.update { it.copy(error = adminMessage(e, write = true)) }
                    if (isStale(e)) load()
                } finally {
                    mutable.update { it.copy(saving = false) }
                }
            }
        }
    }

    fun clearMessages() = mutable.update { it.copy(error = null, notice = null) }

    override fun onDispose() {
        revision++
        active = false
        work.cancel()
    }
}
