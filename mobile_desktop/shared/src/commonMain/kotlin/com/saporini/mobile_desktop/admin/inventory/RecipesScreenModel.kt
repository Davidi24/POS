package com.saporini.mobile_desktop.admin.inventory

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.admin.adminMessage
import com.saporini.mobile_desktop.admin.isDenied
import com.saporini.mobile_desktop.admin.isStale
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.menu.data.dto.IngredientChoiceDto
import com.saporini.mobile_desktop.pos.menu.ui.item.PhotoChange
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
    // Items → "Add sub-recipe" (or a made-here item opened): the editor, and what its ingredients can be picked from.
    val subRecipe: SubRecipeDraft? = null,
    // The sub-recipe as it was loaded, so Save waits until something changed (null for a new one).
    val subRecipeLoaded: SubRecipeDraft? = null,
    val choices: List<IngredientChoiceDto>? = null,
    val choicesError: String? = null,
    // Goes up after each sub-recipe save, so Items reloads its items (their names, kinds and costs follow).
    val savedTick: Int = 0,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val stale: Boolean = true,
    val notice: String? = null,
    val error: String? = null
) {
    val visible: List<RecipeDto> get() {
        val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return recipes.filter { recipe ->
            val text = listOfNotNull(recipe.name, recipe.code).joinToString(" ").lowercase()
            words.all { it in text }
        }.sortedBy { it.name.lowercase() }
    }

    /** Recipes that can go inside the open one (not itself, not archived). */
    val subRecipeChoices: List<RecipeDto> get() = recipes.filter { it.id != open?.id && it.status != "ARCHIVED" }
}

/**
 * Admin Hub → Inventory → Preparations: the list (by type and status), one preparation with its ingredients and cost,
 * adding/editing preparations and ingredients, archiving, and re-pricing from today's stock costs.
 * Dishes' own recipes are left out; they are edited in Menu.
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
    private var changeEventsJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                signIn++
                loadJob?.cancel()
                val permissions = user?.takeIf { it.isActive }?.permissions.orEmpty().toSet()
                mutable.value = RecipesState(
                    restaurantId = user?.takeIf { it.isActive }?.restaurantId,
                    canRead = "INVENTORY_READ" in permissions,
                    canEdit = "INVENTORY_UPDATE" in permissions
                )
                if (active) {
                    load()
                    observeChanges()
                }
            }
        }
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) {
            load()
            observeChanges()
        } else {
            revision++
            loadJob?.cancel()
            changeEventsJob?.cancel()
            changeEventsJob = null
            mutable.update { it.copy(loading = false) }
        }
    }

    private fun observeChanges() {
        changeEventsJob?.cancel()
        val restaurantId = state.value.restaurantId ?: return
        if (!active || !state.value.canRead) return
        changeEventsJob = work.launch {
            repository.observeChanges(restaurantId).collect {
                if (active && state.value.restaurantId == restaurantId) {
                    if (state.value.loading) loadJob?.join()
                    if (active && state.value.restaurantId == restaurantId) load()
                }
            }
        }
    }

    fun filter(type: String?, status: String?) {
        if (type != null && type !in PREPARATION_TYPES || status != null && status !in RECIPE_STATUSES) return
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
                val recipes = repository.recipes(restaurantId, current.type, current.status).filter { it.recipeType in PREPARATION_TYPES }
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

    /** A new house-made item starts in use, with one empty ingredient line to fill in. */
    fun newRecipe() = needsEdit { mutable.update { it.copy(draft = RecipeDraft(status = "ACTIVE"), notice = null) } }

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
        write(null) { restaurantId ->
            val saved = repository.save(restaurantId, draft.id, recipeRequest(draft))
            // A new one gets what it's made from right away, one line after the other. If a line fails, keep the draft
            // with its recipe id. Retry every line: the backend upserts components by ingredient reference, so a lost
            // response or an earlier successful line can't create duplicates.
            var latest = saved
            var failed: Exception? = null
            for ((index, line) in draft.ingredients.filterNot { it.blank }.withIndex()) {
                try {
                    latest = repository.putComponent(restaurantId, saved.id, ingredientRequest(line, index))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed = e
                    break
                }
            }
            var photoFailure: Exception? = null
            val withPhoto = try {
                when (val photo = draft.photo) {
                    PhotoChange.Keep -> latest
                    PhotoChange.Remove -> if (latest.imageUrl == null) latest else repository.removePhoto(restaurantId, latest.id)
                    is PhotoChange.Replace -> repository.uploadPhoto(restaurantId, latest.id, photo.bytes, photo.fileName, photo.contentType)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                photoFailure = e
                latest
            }
            mutable.update {
                val final = if (photoFailure == null) withPhoto else latest
                val retryDraft = draft.copy(
                    id = latest.id,
                    imageUrl = if (photoFailure == null) withPhoto.imageUrl else latest.imageUrl,
                    photo = if (photoFailure == null) PhotoChange.Keep else draft.photo
                )
                it.copy(draft = if (failed == null && photoFailure == null) null else retryDraft,
                    open = final,
                    notice = if (failed == null && photoFailure == null) "${draft.name.trim()} was saved" else null,
                    error = when {
                        photoFailure != null -> "${draft.name.trim()} was saved, but its photo wasn't: ${adminMessage(photoFailure, write = true)}"
                        failed != null -> "${draft.name.trim()} was saved, but not every ingredient could be added: ${adminMessage(failed, write = true)}"
                        else -> null
                    })
            }
            applyRecipe(if (photoFailure == null) withPhoto else latest)
        }
    }

    fun askArchive() = needsEdit { mutable.update { it.copy(confirmArchive = it.open?.id) } }

    fun dismissArchive() = mutable.update { it.copy(confirmArchive = null) }

    fun confirmArchive() {
        val recipeId = state.value.confirmArchive ?: return
        mutable.update { it.copy(confirmArchive = null) }
        write("The preparation was archived") { restaurantId -> applyRecipe(repository.archive(restaurantId, recipeId)) }
    }

    /** Prices the open recipe again from today's stock costs. */
    fun recalculateCost() {
        val recipeId = state.value.open?.id ?: return
        needsEdit { write("Cost updated") { restaurantId -> applyRecipe(repository.recalculateCost(restaurantId, recipeId)) } }
    }

    // ---- Sub-recipes (Items) ----

    /** "Add sub-recipe": an empty editor, with what can go in loading behind it. */
    fun newSubRecipe() = needsEdit {
        mutable.update { it.copy(subRecipe = SubRecipeDraft(), subRecipeLoaded = null, choices = null, choicesError = null, error = null, notice = null) }
        loadChoices(null)
    }

    /** A made-here item's sub-recipe: its ingredients and cost, ready to change. */
    fun openSubRecipe(recipeId: String) {
        val restaurantId = state.value.restaurantId ?: return
        mutable.update {
            it.copy(subRecipe = SubRecipeDraft(recipeId = recipeId, loading = true), subRecipeLoaded = null, choices = null, choicesError = null, error = null, notice = null)
        }
        val token = revision
        work.launch {
            try {
                val saved = repository.subRecipe(restaurantId, recipeId)
                if (token == revision) mutable.update { state ->
                    if (state.subRecipe?.recipeId == recipeId) subRecipeDraftOf(saved).let { state.copy(subRecipe = it, subRecipeLoaded = it) } else state
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(subRecipe = null, error = adminMessage(e, write = false)) }
            }
        }
        if (state.value.canEdit) loadChoices(recipeId)
    }

    fun changeSubRecipe(change: (SubRecipeDraft) -> SubRecipeDraft) = mutable.update { state ->
        val draft = state.subRecipe ?: return@update state
        state.copy(subRecipe = change(draft).copy(recipeId = draft.recipeId, itemId = draft.itemId))
    }

    fun cancelSubRecipe() = mutable.update { it.copy(subRecipe = null, subRecipeLoaded = null, error = null) }

    fun retryChoices() = loadChoices(state.value.subRecipe?.recipeId)

    private fun loadChoices(forRecipeId: String?) {
        val restaurantId = state.value.restaurantId ?: return
        work.launch {
            try {
                val choices = repository.subRecipeChoices(restaurantId, forRecipeId)
                mutable.update { if (it.subRecipe != null) it.copy(choices = choices, choicesError = null) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { if (it.subRecipe != null) it.copy(choicesError = adminMessage(e, write = false)) else it }
            }
        }
    }

    /**
     * Saves the sub-recipe whole (its item and recipe together), then its photo on its item. When the photo fails the
     * editor stays open on the saved one, so trying again never makes a second sub-recipe.
     */
    fun saveSubRecipe() {
        val draft = state.value.subRecipe ?: return
        subRecipeProblem(draft)?.let { problem ->
            mutable.update { it.copy(error = problem) }
            return
        }
        write(null) { restaurantId ->
            val saved = repository.saveSubRecipe(restaurantId, draft.recipeId, subRecipeRequest(draft))
            val photoFailure = saved.itemId?.let { itemId ->
                try {
                    when (val photo = draft.photo) {
                        PhotoChange.Keep -> Unit
                        PhotoChange.Remove -> if (saved.imageUrl != null) repository.removeItemPhoto(restaurantId, itemId)
                        is PhotoChange.Replace -> repository.uploadItemPhoto(restaurantId, itemId, photo.bytes, photo.fileName, photo.contentType)
                    }
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e
                }
            }
            mutable.update {
                it.copy(
                    subRecipe = if (photoFailure == null) null else subRecipeDraftOf(saved).copy(photo = draft.photo),
                    subRecipeLoaded = if (photoFailure == null) null else subRecipeDraftOf(saved),
                    savedTick = it.savedTick + 1,
                    notice = if (photoFailure == null) "${saved.name} was saved" else null,
                    error = photoFailure?.let { e -> "${saved.name} was saved, but its photo wasn't: ${adminMessage(e, write = true)}" }
                )
            }
            load()
        }
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
        val matches = saved.recipeType in PREPARATION_TYPES && (it.type == null || saved.recipeType == it.type) && (it.status == null || saved.status == it.status)
        it.copy(open = if (it.open?.id == saved.id) saved else it.open,
            recipes = if (matches) it.recipes.put(saved) else it.recipes.filterNot { r -> r.id == saved.id })
    }

    private fun List<RecipeDto>.put(saved: RecipeDto) = if (any { it.id == saved.id }) map { if (it.id == saved.id) saved else it } else this + saved

    private inline fun needsEdit(action: () -> Unit) {
        if (!state.value.canEdit) {
            mutable.update { it.copy(error = "You can only look at sub-recipes") }
            return
        }
        action()
    }

    private fun write(notice: String?, action: suspend (restaurantId: String) -> Unit) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (current.saving) return
        if (!current.canEdit) {
            mutable.update { it.copy(error = "You can only look at sub-recipes") }
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
        changeEventsJob?.cancel()
    }
}
