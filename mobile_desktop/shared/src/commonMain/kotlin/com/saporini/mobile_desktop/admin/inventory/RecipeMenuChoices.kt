package com.saporini.mobile_desktop.admin.inventory

import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository

/** A dish on one of the restaurant's menus that a recipe can make. */
data class MenuChoice(val id: String, val name: String, val menuName: String, val sectionName: String)

/** Every dish on the restaurant's menus (all pages, each dish once), for linking a recipe to what it makes. */
suspend fun menuChoicesOf(menus: MenuRepository, restaurantId: String): List<MenuChoice> {
    val choices = mutableListOf<MenuChoice>()
    var page = 0
    while (true) {
        val response = menus.getMenus(restaurantId = restaurantId, page = page, size = 100)
        for (summary in response.items) {
            val menu = menus.getMenu(summary.id, includeSections = true, includeItems = true)
            menu.sections.forEach { section ->
                section.items.forEach { item -> choices += MenuChoice(item.id, item.name, menu.name, section.name) }
            }
        }
        if (!response.hasNext || response.items.isEmpty()) break
        page++
    }
    return choices.distinctBy { it.id }.sortedBy { it.name.lowercase() }
}
