package com.saporini.mobile_desktop.pos.menu.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.admin.inventory.RecipeDto
import com.saporini.mobile_desktop.pos.menu.data.dto.DishIngredientRequestDto
import com.saporini.mobile_desktop.pos.menu.data.dto.DishRecipeDto
import com.saporini.mobile_desktop.pos.menu.data.dto.IngredientChoiceDto
import com.saporini.mobile_desktop.admin.inventory.RecipesRepository
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEventNight
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEventNightInput
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuSection
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuVariant
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuWaiterView
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenu
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenuSection
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionItem
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionGroup
import com.saporini.mobile_desktop.pos.menu.domain.model.SpecialMenuKind
import com.saporini.mobile_desktop.pos.menu.domain.repository.CreateMenuInput
import com.saporini.mobile_desktop.pos.menu.domain.repository.CreateMenuItemOptionGroupInput
import com.saporini.mobile_desktop.pos.menu.domain.repository.CreateOptionGroupInput
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuItemInput
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuSectionInput
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuVariantInput
import com.saporini.mobile_desktop.pos.menu.domain.repository.OptionGroupTypeInput
import com.saporini.mobile_desktop.pos.menu.domain.repository.OptionItemInput
import com.saporini.mobile_desktop.pos.menu.domain.repository.UpdateMenuInput
import com.saporini.mobile_desktop.pos.menu.ui.item.DraftOptionChoice
import com.saporini.mobile_desktop.pos.menu.ui.item.DraftOptionGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MenuScreenModel(
    private val repository: MenuRepository,
    private val sessionManager: SessionManager,
    private val recipesRepository: RecipesRepository? = null
) : ScreenModel {

    private val _state = MutableStateFlow(MenuUiState())
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadMenus()
    }

    // Managers see and manage every menu. Everyone else (waiters) sees only what the server says is on right now:
    // scheduled for this day and time, chosen in "Menus shown", and only the event menu on a special-menu-only event
    // night. If that can't be loaded, waiters get an error with "Try again", never a guessed list.
    private fun managesMenus(): Boolean {
        val permissions = sessionManager.currentUser.value?.permissions.orEmpty()
        return "MENUS_UPDATE" in permissions || "MENUS_CREATE" in permissions || "MENUS_DELETE" in permissions
    }

    // [quiet]: a background refresh (no skeleton; a failure keeps the last list and shows the error).
    fun loadMenus(quiet: Boolean = false) {
        val restaurantId = currentRestaurantId() ?: return

        loadJob?.cancel()
        loadJob = screenModelScope.launch {
            if (!quiet) {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            }

            if (!managesMenus()) {
                try {
                    val onNow = repository.getMenusAvailableNow(restaurantId)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        menus = onNow,
                        totalElements = onNow.size.toLong(),
                        totalPages = 1,
                        hasNext = false,
                        hasPrevious = false,
                        waiterView = null,
                        waiterViewError = null,
                        errorMessage = null
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        menus = if (quiet) _state.value.menus else emptyList(),
                        errorMessage = "Couldn't load the menus that are on now. ${error.message ?: ""}".trim()
                    )
                }
                return@launch
            }

            try {
                val page = repository.getMenus(
                    restaurantId = restaurantId,
                    page = 0,
                    size = _state.value.pageSize
                )
                // Which menus waiters see, for the "Hidden from waiters" labels. A failure is shown, not guessed.
                var waiterViewError: String? = null
                val waiterView = try {
                    repository.getWaiterView(restaurantId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    waiterViewError = error.message ?: "Couldn't load which menus waiters see"
                    null
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    menus = page.items,
                    waiterView = waiterView,
                    waiterViewError = waiterViewError,
                    page = page.page,
                    totalElements = page.totalElements,
                    totalPages = page.totalPages,
                    hasNext = page.hasNext,
                    hasPrevious = page.hasPrevious,
                    errorMessage = null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not load menus"
                )
            }
        }
    }

    fun openMenu(menuId: String) {
        // Navigate immediately using the summary already held in `menus` --
        // does not touch `isLoading` (that's the menu-list skeleton only),
        // so opening a menu goes straight to MenuDetailsContent instead of
        // flashing the list skeleton while the detail call is in flight.
        val summary = _state.value.menus.firstOrNull { it.id == menuId } ?: return
        _state.value = _state.value.copy(selectedMenu = summary, errorMessage = null)

        screenModelScope.launch {
            try {
                val menu = repository.getMenu(
                    menuId = menuId,
                    includeSections = true,
                    includeItems = true,
                    includeVariants = true,
                    includeOptionGroups = true
                )

                if (_state.value.selectedMenu?.id == menuId) {
                    _state.value = _state.value.copy(selectedMenu = menu, errorMessage = null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_state.value.selectedMenu?.id == menuId) {
                    _state.value = _state.value.copy(
                        errorMessage = error.message ?: "Could not open menu"
                    )
                }
            }
        }
    }

    fun closeMenu() {
        _state.value = _state.value.copy(selectedMenu = null)
    }

    fun deleteMenu(menuId: String, deleteItems: Boolean = false, onSuccess: () -> Unit = {}) {
        screenModelScope.launch {
            try {
                repository.deleteMenu(menuId, deleteItems)
                _state.value = _state.value.copy(
                    menus = _state.value.menus.filterNot { it.id == menuId },
                    totalElements = (_state.value.totalElements - 1).coerceAtLeast(0),
                    errorMessage = null
                )
                onSuccess()
                if (!deleteItems) {
                    // Sections may have moved to an auto-created "Uncategorized" menu that
                    // this session doesn't know about yet -- refresh to pick it up.
                    loadMenus()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    errorMessage = error.message ?: "Could not delete menu"
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    // Menu → "Menus shown" → Change position: saves the order and shows the menus in it.
    suspend fun saveMenuOrder(menuIds: List<String>): Result<Unit> {
        val restaurantId = currentRestaurantId() ?: return Result.failure(IllegalStateException("No restaurant selected"))
        return try {
            repository.saveMenuOrder(restaurantId, menuIds)
            val position = menuIds.withIndex().associate { (index, id) -> id to index }
            val rest = _state.value.menus.filter { it.id !in position }.sortedBy { it.displayOrder }
            val reordered = _state.value.menus.filter { it.id in position }.sortedBy { position.getValue(it.id) } + rest
            _state.value = _state.value.copy(menus = reordered.mapIndexed { index, menu -> menu.copy(displayOrder = index) })
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    // Menu → "Menus shown": saves which menus waiters see and marks the loaded menus to match.
    suspend fun saveWaiterView(view: MenuWaiterView): Result<MenuWaiterView> {
        val restaurantId = currentRestaurantId() ?: return Result.failure(IllegalStateException("No restaurant selected"))
        return try {
            val saved = repository.saveWaiterView(restaurantId, view)
            _state.value = _state.value.copy(
                waiterView = saved,
                menus = _state.value.menus.map { it.copy(shownToWaiters = it.id in saved.shownMenuIds) }
            )
            Result.success(saved)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    // The restaurant's event nights, for the menu editor's "Event menu" choice.
    suspend fun loadEventNights(): Result<List<MenuEventNight>> {
        val restaurantId = currentRestaurantId() ?: return Result.success(emptyList())
        return try {
            Result.success(repository.getEventNights(restaurantId))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    // Adds an event night from the menu editor's event picker.
    suspend fun createEventNight(input: MenuEventNightInput): Result<MenuEventNight> {
        val restaurantId = currentRestaurantId() ?: return Result.failure(IllegalStateException("No restaurant selected"))
        return try {
            Result.success(repository.createEventNight(restaurantId, input))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    // Changes an event night from the menu editor. Its menu's days follow, so the menus reload when it has one.
    suspend fun updateEventNight(event: MenuEventNight, input: MenuEventNightInput): Result<MenuEventNight> {
        val restaurantId = currentRestaurantId() ?: return Result.failure(IllegalStateException("No restaurant selected"))
        return try {
            val updated = repository.updateEventNight(restaurantId, event, input)
            if (event.menuId != null) loadMenus()
            Result.success(updated)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    // Deletes an event night from the event picker. Its menu (if any) is switched off by the server, so the
    // menus reload when it had one.
    suspend fun deleteEventNight(event: MenuEventNight): Result<Unit> {
        val restaurantId = currentRestaurantId() ?: return Result.failure(IllegalStateException("No restaurant selected"))
        return try {
            repository.deleteEventNight(restaurantId, event.id)
            if (event.menuId != null) loadMenus()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    fun createMenu(
        name: String,
        description: String?,
        active: Boolean,
        availableFrom: String?,
        availableUntil: String?,
        availableFromDate: String?,
        availableUntilDate: String?,
        color: String,
        special: Boolean = false,
        specialKind: SpecialMenuKind? = null,
        eventId: String? = null,
        shownToWaiters: Boolean = true,
        onSuccess: (Menu) -> Unit = {}
    ) {
        val restaurantId = currentRestaurantId() ?: return
        val cleanName = name.trim()

        if (cleanName.isEmpty() || _state.value.isSaving) {
            return
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(
                isSaving = true,
                errorMessage = null
            )

            try {
                val createdMenu = repository.createMenu(
                    CreateMenuInput(
                        restaurantId = restaurantId,
                        name = cleanName,
                        description = description?.trim()?.takeIf { it.isNotEmpty() },
                        active = active,
                        displayOrder = _state.value.menus.size,
                        availableFrom = availableFrom,
                        availableUntil = availableUntil,
                        availableFromDate = availableFromDate,
                        availableUntilDate = availableUntilDate,
                        color = color,
                        special = special,
                        specialKind = specialKind.takeIf { special },
                        eventId = eventId.takeIf { special && specialKind == SpecialMenuKind.EVENT },
                        shownToWaiters = shownToWaiters
                    )
                )

                _state.value = _state.value.copy(
                    isSaving = false,
                    menus = (_state.value.menus + createdMenu)
                        .sortedBy { it.displayOrder },
                    totalElements = _state.value.totalElements + 1,
                    errorMessage = null
                )
                onSuccess(createdMenu)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "Could not create menu"
                )
            }
        }
    }

    fun updateMenu(
        menuId: String,
        name: String,
        description: String?,
        active: Boolean,
        availableFrom: String?,
        availableUntil: String?,
        availableFromDate: String?,
        availableUntilDate: String?,
        color: String,
        special: Boolean? = null,
        specialKind: SpecialMenuKind? = null,
        eventId: String? = null,
        shownToWaiters: Boolean? = null,
        onSuccess: () -> Unit = {}
    ) {
        val currentMenu = _state.value.menus.firstOrNull { it.id == menuId }
            ?: _state.value.selectedMenu?.takeIf { it.id == menuId }
            ?: return
        val cleanName = name.trim()

        if (cleanName.isEmpty() || _state.value.isSaving) {
            return
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(
                isSaving = true,
                errorMessage = null
            )

            try {
                val updatedMenu = repository.updateMenu(
                    menuId = menuId,
                    input = UpdateMenuInput(
                        code = currentMenu.code,
                        name = cleanName,
                        description = description?.trim()?.takeIf { it.isNotEmpty() },
                        active = active,
                        displayOrder = currentMenu.displayOrder,
                        availableFrom = availableFrom,
                        availableUntil = availableUntil,
                        availableFromDate = availableFromDate,
                        availableUntilDate = availableUntilDate,
                        color = color,
                        special = special,
                        specialKind = specialKind.takeIf { special == true },
                        eventId = eventId.takeIf { special == true && specialKind == SpecialMenuKind.EVENT },
                        shownToWaiters = shownToWaiters
                    )
                )

                _state.value = _state.value.copy(
                    isSaving = false,
                    menus = _state.value.menus.map { menu ->
                        if (menu.id == updatedMenu.id) updatedMenu else menu
                    },
                    selectedMenu = _state.value.selectedMenu?.let { selected ->
                        if (selected.id == updatedMenu.id) updatedMenu else selected
                    },
                    errorMessage = null
                )
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "Could not update menu"
                )
            }
        }
    }

    private fun currentRestaurantId(): String? {
        val restaurantId = sessionManager.currentUser.value?.restaurantId
        if (restaurantId == null) {
            _state.value = _state.value.copy(
                isLoading = false,
                isSaving = false,
                errorMessage = "No restaurant is assigned to this user"
            )
        }
        return restaurantId
    }

    // --- Section/item/variant/option actions below --------------------
    //
    // These are called from bulk diff loops in MenuScreen.kt (e.g. saving
    // a whole section list, or an item reorder touching many rows at once),
    // so unlike the menu-level actions above they deliberately do NOT
    // launch their own coroutine or mutate `_state.errorMessage` directly.
    // Instead they're plain suspend functions returning Result<T>: the
    // caller runs a batch of them concurrently (coroutineScope + async +
    // awaitAll), sees exactly which ones failed, and decides what to do
    // with partial failures instead of one silently clobbering another's
    // error message or a failed call being indistinguishable from a
    // succeeded one once local state gets applied.

    private suspend fun <T> runSuspendCatching(action: suspend () -> T): Result<T> {
        return try {
            Result.success(action())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    // Copies of dishes from other menus into a section of this (special) menu.
    suspend fun importItems(menuId: String, sectionId: String, itemIds: List<String>): Result<List<com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem>> =
        runSuspendCatching { repository.importItems(menuId, sectionId, itemIds) }

    suspend fun saveAllFilterPosition(menuId: String, position: Int): Result<Unit> = runSuspendCatching {
        val current = repository.getMenu(menuId, true, true, true, true)
        repository.updateMenu(menuId, UpdateMenuInput(
            code = current.code, name = current.name, description = current.description,
            active = current.active, displayOrder = current.displayOrder,
            availableFrom = current.availableFrom, availableUntil = current.availableUntil,
            availableFromDate = current.availableFromDate, availableUntilDate = current.availableUntilDate,
            color = current.color, allFilterPosition = position
        ))
        val refreshed = current.copy(allFilterPosition = position)
        _state.value = _state.value.copy(
            selectedMenu = if (_state.value.selectedMenu?.id == menuId) refreshed else _state.value.selectedMenu,
            menus = _state.value.menus.map { if (it.id == menuId) refreshed else it }
        )
    }

    suspend fun createSection(menuId: String, name: String, displayOrder: Int): Result<MenuSection> =
        runSuspendCatching {
            repository.createSection(menuId, MenuSectionInput(name = name, displayOrder = displayOrder))
        }

    suspend fun updateSection(
        menuId: String,
        sectionId: String,
        name: String,
        displayOrder: Int
    ): Result<MenuSection> = runSuspendCatching {
        repository.updateSection(menuId, sectionId, MenuSectionInput(name = name, displayOrder = displayOrder))
    }

    suspend fun deleteSection(menuId: String, sectionId: String, deleteItems: Boolean): Result<com.saporini.mobile_desktop.pos.menu.domain.model.Menu> =
        runSuspendCatching {
            repository.deleteSection(menuId, sectionId, deleteItems)
            // Refresh in place before reporting success. openMenu briefly replaces the
            // detail with a list summary and can leave dialog-local state stale.
            val refreshed = repository.getMenu(menuId, true, true, true, true)
            _state.value = _state.value.copy(
                selectedMenu = if (_state.value.selectedMenu?.id == menuId) refreshed else _state.value.selectedMenu,
                menus = _state.value.menus.map { if (it.id == menuId) refreshed else it },
                errorMessage = null
            )
            refreshed
        }

    // --- Online menu: a preview of what customers see, plus its own sections ------

    suspend fun loadOnlineMenu(): Result<OnlineMenu> =
        runSuspendCatching { repository.getOnlineMenu(requireRestaurantId()) }

    suspend fun loadOnlineMenuSections(): Result<List<OnlineMenuSection>> =
        runSuspendCatching { repository.getOnlineMenuSections(requireRestaurantId()) }

    suspend fun renameOnlineMenuSection(sectionId: String, name: String): Result<OnlineMenuSection> =
        runSuspendCatching { repository.renameOnlineMenuSection(requireRestaurantId(), sectionId, name) }

    suspend fun reorderOnlineMenuSections(sectionIds: List<String>): Result<List<OnlineMenuSection>> =
        runSuspendCatching { repository.reorderOnlineMenuSections(requireRestaurantId(), sectionIds) }

    suspend fun reorderOnlineMenuItems(sectionId: String, itemIds: List<String>): Result<OnlineMenu> =
        runSuspendCatching { repository.reorderOnlineMenuItems(requireRestaurantId(), sectionId, itemIds) }

    suspend fun deleteOnlineMenuSection(sectionId: String): Result<Unit> =
        runSuspendCatching { repository.deleteOnlineMenuSection(requireRestaurantId(), sectionId) }

    private fun requireRestaurantId(): String =
        sessionManager.currentUser.value?.restaurantId ?: error("No restaurant is assigned to this user")

    suspend fun createItem(menuId: String, sectionId: String, input: MenuItemInput): Result<MenuItem> =
        runSuspendCatching { repository.createItem(menuId, sectionId, input) }

    suspend fun updateItem(
        menuId: String,
        sectionId: String,
        itemId: String,
        input: MenuItemInput
    ): Result<MenuItem> = runSuspendCatching { repository.updateItem(menuId, sectionId, itemId, input) }

    suspend fun deleteItem(menuId: String, sectionId: String, itemId: String): Result<Unit> =
        runSuspendCatching { repository.deleteItem(menuId, sectionId, itemId) }

    suspend fun uploadItemImage(
        menuId: String,
        sectionId: String,
        itemId: String,
        imageBytes: ByteArray,
        fileName: String,
        contentType: String
    ): Result<MenuItem> = runSuspendCatching {
        repository.uploadItemImage(menuId, sectionId, itemId, imageBytes, fileName, contentType)
    }

    suspend fun removeItemImage(menuId: String, sectionId: String, itemId: String): Result<MenuItem> =
        runSuspendCatching { repository.removeItemImage(menuId, sectionId, itemId) }

    // The dish's ingredients from stock (its recipe underneath), and the stock items and preparations to pick from.
    suspend fun loadDishRecipe(menuId: String, sectionId: String, itemId: String): Result<DishRecipeDto> =
        runSuspendCatching { repository.getDishRecipe(menuId, sectionId, itemId) }

    suspend fun saveDishRecipe(menuId: String, sectionId: String, itemId: String, ingredients: List<DishIngredientRequestDto>): Result<DishRecipeDto> =
        runSuspendCatching { repository.saveDishRecipe(menuId, sectionId, itemId, ingredients) }

    suspend fun loadIngredientChoices(menuId: String): Result<List<IngredientChoiceDto>> =
        runSuspendCatching { repository.ingredientChoices(menuId) }

    suspend fun updateItemAvailability(
        menuId: String,
        sectionId: String,
        itemId: String,
        available: Boolean
    ): Result<MenuItem> = runSuspendCatching {
        repository.updateItemAvailability(menuId, sectionId, itemId, available)
    }

    suspend fun createVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        input: MenuVariantInput
    ): Result<MenuVariant> = runSuspendCatching { repository.createVariant(menuId, sectionId, itemId, input) }

    suspend fun updateVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        variantId: String,
        input: MenuVariantInput
    ): Result<MenuVariant> = runSuspendCatching {
        repository.updateVariant(menuId, sectionId, itemId, variantId, input)
    }

    suspend fun deleteVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        variantId: String
    ): Result<Unit> = runSuspendCatching { repository.deleteVariant(menuId, sectionId, itemId, variantId) }

    suspend fun loadOptionGroupChoices(groups: List<DraftOptionGroup>): Result<List<DraftOptionGroup>> = runSuspendCatching {
        groups.map { group ->
            val groupId = group.optionGroupId ?: return@map group
            val loaded = repository.getOptionGroup(groupId, includeItems = true)
            loaded.toDraftOptionGroup(group)
        }
    }

    suspend fun loadModifierRecipes(): Result<List<RecipeDto>> = runSuspendCatching {
        val restaurantId = sessionManager.currentUser.value?.restaurantId
            ?: throw IllegalStateException("No restaurant is assigned to this user")
        val recipes = recipesRepository?.recipes(restaurantId, type = null, status = "ACTIVE").orEmpty()
        recipes.filter { it.recipeType == "PREP_BATCH" || it.recipeType == "SUB_RECIPE" }
            .sortedBy { it.name.lowercase() }
    }

    // Option groups are restaurant-level reusable entities server-side.
    // There's no picker UI yet to reuse an existing one, so every save
    // replaces an item's linked groups with fresh ones scoped to just
    // this item (see AGENT_MEMORY.md for the trade-off). The steps run
    // sequentially inside one Result -- if any step fails, the whole
    // save is reported as failed rather than silently half-applied.
    suspend fun addItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        group: DraftOptionGroup,
        displayOrder: Int
    ): Result<DraftOptionGroup> = runSuspendCatching {
        val restaurantId = sessionManager.currentUser.value?.restaurantId
            ?: throw IllegalStateException("No restaurant is assigned to this user")

        val typeId = repository.getOptionGroupTypes().firstOrNull()?.id
            ?: repository.createOptionGroupType(OptionGroupTypeInput(name = "General")).id
        val createdGroup = repository.createOptionGroup(
            CreateOptionGroupInput(
                restaurantId = restaurantId,
                typeId = typeId,
                name = group.name,
                minSelect = if (group.required) 1 else 0,
                maxSelect = group.choices.size,
                required = group.required,
                displayOrder = displayOrder
            )
        )
        val createdChoices = group.choices.mapIndexed { choiceIndex, choice ->
            val priceDelta = choice.priceDeltaLabel
                .filter { it.isDigit() || it == '.' }
                .toDoubleOrNull() ?: 0.0
            val createdItem = repository.createOptionItem(
                createdGroup.id,
                OptionItemInput(
                    name = choice.name,
                    priceDelta = priceDelta,
                    displayOrder = choiceIndex,
                    inventoryRecipeId = choice.inventoryRecipeId,
                    inventoryRecipeQuantity = choice.inventoryRecipeQuantity
                )
            )
            choice.copy(
                id = createdItem.id,
                inventoryRecipeId = createdItem.inventoryRecipeId,
                inventoryRecipeQuantity = createdItem.inventoryRecipeQuantity
            )
        }
        val link = repository.createItemOptionGroup(
            menuId,
            sectionId,
            itemId,
            CreateMenuItemOptionGroupInput(optionGroupId = createdGroup.id, displayOrder = displayOrder)
        )
        group.copy(choices = createdChoices, linkId = link.linkId, optionGroupId = createdGroup.id)
    }

    suspend fun updateItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        existing: DraftOptionGroup,
        updated: DraftOptionGroup,
        displayOrder: Int
    ): Result<DraftOptionGroup> = runSuspendCatching {
        // No partial-update path for a group's choices yet -- delete the old
        // group/items/link and recreate fresh, same as a plain add.
        deleteItemOptionGroupInternal(menuId, sectionId, itemId, existing)
        addItemOptionGroup(menuId, sectionId, itemId, updated, displayOrder).getOrThrow()
    }

    suspend fun deleteItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        group: DraftOptionGroup
    ): Result<Unit> = runSuspendCatching {
        deleteItemOptionGroupInternal(menuId, sectionId, itemId, group)
    }

    suspend fun updateOptionItem(
        groupId: String,
        itemId: String,
        input: OptionItemInput
    ): Result<OptionItem> = runSuspendCatching {
        if (currentRestaurantId() == null) {
            throw IllegalStateException("No restaurant is assigned to this user")
        }
        repository.updateOptionItem(groupId, itemId, input)
    }

    private suspend fun deleteItemOptionGroupInternal(
        menuId: String,
        sectionId: String,
        itemId: String,
        group: DraftOptionGroup
    ) {
        val groupId = group.optionGroupId
        group.choices.forEach { choice ->
            val choiceId = choice.id
            if (choiceId != null && groupId != null) {
                repository.deleteOptionItem(groupId, choiceId)
            }
        }
        val linkId = group.linkId
        if (linkId != null) {
            repository.deleteItemOptionGroup(menuId, sectionId, itemId, linkId)
        }
        if (groupId != null) {
            repository.deleteOptionGroup(groupId)
        }
    }
}

private fun formatOptionPriceDelta(value: Double): String {
    val cents = kotlin.math.round(value * 100).toLong()
    val absolute = kotlin.math.abs(cents)
    val sign = if (cents < 0) "-" else "+"
    return sign + "$" + (absolute / 100) + "." + (absolute % 100).toString().padStart(2, '0')
}

internal fun OptionGroup.toDraftOptionGroup(previous: DraftOptionGroup): DraftOptionGroup = previous.copy(
    name = name,
    required = required,
    choices = items.sortedBy { it.displayOrder }.map { item ->
        DraftOptionChoice(
            name = item.name,
            priceDeltaLabel = formatOptionPriceDelta(item.priceDelta),
            id = item.id,
            inventoryRecipeId = item.inventoryRecipeId,
            inventoryRecipeQuantity = item.inventoryRecipeQuantity
        )
    }
)
