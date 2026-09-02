package com.saporini.mobile_desktop.pos.menu.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.menu.domain.repository.CreateMenuInput
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import com.saporini.mobile_desktop.pos.menu.domain.repository.UpdateMenuInput
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
        screenModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null
            )

            try {
                val menu = repository.getMenu(
                    menuId = menuId,
                    includeSections = true,
                    includeItems = true,
                    includeVariants = true,
                    includeOptionGroups = true
                )

                _state.value = _state.value.copy(
                    isLoading = false,
                    selectedMenu = menu,
                    errorMessage = null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not open menu"
                )
            }
        }
    }

    fun closeMenu() {
        _state.value = _state.value.copy(selectedMenu = null)
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
}
