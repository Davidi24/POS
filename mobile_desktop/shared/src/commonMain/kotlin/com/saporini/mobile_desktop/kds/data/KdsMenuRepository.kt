package com.saporini.mobile_desktop.kds.data

import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository

data class KdsMenuEntry(val menuId: String, val menuName: String, val sectionId: String, val sectionName: String, val item: MenuItem)
interface KdsMenuRepository {
    suspend fun load(restaurantId: String): List<KdsMenuEntry>
    suspend fun availability(entry: KdsMenuEntry, available: Boolean): MenuItem
}
class DefaultKdsMenuRepository(private val menus: MenuRepository) : KdsMenuRepository {
    override suspend fun load(restaurantId: String): List<KdsMenuEntry> {
        val result = mutableListOf<KdsMenuEntry>()
        var page = 0
        while (true) {
            val response = menus.getMenus(restaurantId = restaurantId, active = true, page = page, size = 100)
            check(response.page == page && (!response.hasNext || response.items.isNotEmpty())) { "Invalid menu page" }
            for (summary in response.items) {
                val menu = menus.getMenu(summary.id, includeSections = true, includeItems = true, includeVariants = true)
                require(menu.restaurant?.id == restaurantId) { "Menu belongs to another restaurant" }
                if (!menu.active) continue
                menu.sections.filter { it.active }.forEach { section ->
                    section.items.filter { it.sendToKitchen }.forEach { item -> result += KdsMenuEntry(menu.id, menu.name, section.id, section.name, item) }
                }
            }
            if (!response.hasNext) break
            page++
        }
        return result.distinctBy { it.item.id }
    }
    override suspend fun availability(entry: KdsMenuEntry, available: Boolean) = menus.updateItemAvailability(entry.menuId, entry.sectionId, entry.item.id, available)
}
