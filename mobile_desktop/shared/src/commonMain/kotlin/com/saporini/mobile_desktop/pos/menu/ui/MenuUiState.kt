package com.saporini.mobile_desktop.pos.menu.ui

import com.saporini.mobile_desktop.pos.menu.domain.model.Menu

data class MenuUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,

    // Menus returned by the paginated API.
    val menus: List<Menu> = emptyList(),

    // The menu currently opened by the administrator.
    val selectedMenu: Menu? = null,

    // Current list filters.
    val searchQuery: String = "",
    val activeFilter: Boolean? = null,

    // Pagination information returned by the backend.
    val page: Int = 0,
    val pageSize: Int = 20,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,

    // Message shown when an API operation fails.
    val errorMessage: String? = null
) {
    val hasMenus: Boolean
        get() = menus.isNotEmpty()
}