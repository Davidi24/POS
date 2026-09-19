package com.saporini.mobile_desktop.pos.menu.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuSection
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuVariant
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
import com.saporini.mobile_desktop.pos.menu.ui.item.DraftOptionGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MenuScreenModel(
    private val repository: MenuRepository,
    private val sessionManager: SessionManager
) : ScreenModel {

    private val _state = MutableStateFlow(MenuUiState())
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadMenus()
    }

    fun loadMenus() {
        val restaurantId = currentRestaurantId() ?: return

        loadJob?.cancel()
        loadJob = screenModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null
            )

            try {
                val page = repository.getMenus(
                    restaurantId = restaurantId,
                    page = 0,
                    size = _state.value.pageSize
                )

                _state.value = _state.value.copy(
                    isLoading = false,
                    menus = page.items,
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

    fun deleteMenu(menuId: String, onSuccess: () -> Unit = {}) {
        screenModelScope.launch {
            try {
                repository.deleteMenu(menuId)
                _state.value = _state.value.copy(
                    menus = _state.value.menus.filterNot { it.id == menuId },
                    totalElements = (_state.value.totalElements - 1).coerceAtLeast(0),
                    errorMessage = null
                )
                onSuccess()
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

    fun createMenu(
        name: String,
        description: String?,
        active: Boolean,
        availableFrom: String?,
        availableUntil: String?,
        availableFromDate: String?,
        availableUntilDate: String?,
        color: String,
        onSuccess: () -> Unit = {}
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
                        color = color
                    )
                )

                _state.value = _state.value.copy(
                    isSaving = false,
                    menus = (_state.value.menus + createdMenu)
                        .sortedBy { it.displayOrder },
                    totalElements = _state.value.totalElements + 1,
                    errorMessage = null
                )
                onSuccess()
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
                        color = color
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

    suspend fun deleteSection(menuId: String, sectionId: String): Result<Unit> =
        runSuspendCatching { repository.deleteSection(menuId, sectionId) }

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

    // Option groups are restaurant-level reusable entities server-side.
    // There's no picker UI yet to reuse an existing one, so every save
    // replaces an item's linked groups with fresh ones scoped to just
    // this item (see AGENT_MEMORY.md for the trade-off). The steps run
    // sequentially inside one Result -- if any step fails, the whole
    // save is reported as failed rather than silently half-applied.
    suspend fun replaceItemOptionGroups(
        menuId: String,
        sectionId: String,
        itemId: String,
        previousLinkIds: List<String>,
        groups: List<DraftOptionGroup>
    ): Result<Unit> = runSuspendCatching {
        val restaurantId = sessionManager.currentUser.value?.restaurantId
            ?: throw IllegalStateException("No restaurant is assigned to this user")

        previousLinkIds.forEach { linkId ->
            repository.deleteItemOptionGroup(menuId, sectionId, itemId, linkId)
        }
        val typeId = repository.getOptionGroupTypes().firstOrNull()?.id
            ?: repository.createOptionGroupType(OptionGroupTypeInput(name = "General")).id
        groups.forEachIndexed { index, group ->
            val createdGroup = repository.createOptionGroup(
                CreateOptionGroupInput(
                    restaurantId = restaurantId,
                    typeId = typeId,
                    name = group.name,
                    minSelect = if (group.required) 1 else 0,
                    maxSelect = group.choices.size,
                    required = group.required,
                    displayOrder = index
                )
            )
            group.choices.forEachIndexed { choiceIndex, choice ->
                val priceDelta = choice.priceDeltaLabel
                    .filter { it.isDigit() || it == '.' }
                    .toDoubleOrNull() ?: 0.0
                repository.createOptionItem(
                    createdGroup.id,
                    OptionItemInput(name = choice.name, priceDelta = priceDelta, displayOrder = choiceIndex)
                )
            }
            repository.createItemOptionGroup(
                menuId,
                sectionId,
                itemId,
                CreateMenuItemOptionGroupInput(optionGroupId = createdGroup.id, displayOrder = index)
            )
        }
    }
}
