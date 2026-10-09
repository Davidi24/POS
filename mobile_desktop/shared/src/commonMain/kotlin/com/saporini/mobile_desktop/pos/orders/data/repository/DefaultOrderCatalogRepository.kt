package com.saporini.mobile_desktop.pos.orders.data.repository

import com.saporini.mobile_desktop.pos.orders.data.api.OrderCatalogApi
import com.saporini.mobile_desktop.pos.orders.data.dto.toDomain
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderCatalogRepository

class DefaultOrderCatalogRepository(
    private val api: OrderCatalogApi,
    // On an event night set to "only the special menu" (Admin Hub), that menu's id; otherwise null.
    private val onlyMenuToday: suspend (restaurantId: String) -> String? = { null }
) : OrderCatalogRepository {
    override suspend fun getMenus(restaurantId: String, page: Int, size: Int): OrderCatalogPage {
        require(restaurantId.isNotBlank() && page >= 0 && size in 1..100)
        val menus = api.getMenus(restaurantId, page, size).toDomain()
        val only = runCatching { onlyMenuToday(restaurantId) }.getOrNull() ?: return menus
        val kept = menus.items.filter { it.id == only }
        return if (kept.isEmpty()) menus else menus.copy(items = kept, totalElements = kept.size.toLong(), totalPages = 1, hasNext = false)
    }

    override suspend fun getMenu(restaurantId: String, menuId: String): OrderCatalogMenu {
        require(restaurantId.isNotBlank() && menuId.isNotBlank())
        val menu = api.getMenu(menuId).toDomain()
        require(menu.restaurant?.id == restaurantId) { "Menu belongs to another restaurant" }
        require(menu.active) { "This menu is no longer active" }
        return menu
    }

    override suspend fun getItemChoices(restaurantId: String, menuId: String, itemId: String): OrderItemChoices {
        val menu = getMenu(restaurantId, menuId)
        val item = menu.sections.orEmpty().filter { it.active }.flatMap { it.items.orEmpty() }
            .firstOrNull { it.id == itemId && it.available }
            ?: throw IllegalArgumentException("This item is no longer available")
        // Fetch only this item's groups, sequentially, instead of downloading every option in the menu.
        val groups = item.optionGroups.orEmpty().filter { it.active }.sortedBy { it.displayOrder }.map { link ->
            val group = api.getChoiceGroup(link.optionGroupId).toDomain()
            require(group.restaurantId == restaurantId && group.id == link.optionGroupId && group.active) { "Option group is no longer available" }
            OrderConfiguredChoiceGroup(link, group)
        }
        return OrderItemChoices(item, groups)
    }
    override suspend fun getCustomers(restaurantId: String): List<OrderCustomerChoice> {
        require(restaurantId.isNotBlank())
        val pageSize = 100
        val customers = linkedMapOf<String, OrderCustomerChoice>()
        var page = 0
        while (true) {
            val result = api.getCustomers(restaurantId, page, pageSize)
            require(result.page == page) { "Customer page response did not match the requested page" }
            result.items.filter { it.active }.forEach { customers.putIfAbsent(it.id, it) }
            if (!result.hasNext) break
            require(result.items.isNotEmpty() && page + 1 < result.totalPages) { "Customer page response has invalid pagination metadata" }
            page += 1
        }
        return customers.values.toList()
    }
    override suspend fun getReservations(restaurantId: String, branchId: String) = api.getReservations(restaurantId, branchId).filter { it.status !in setOf("CANCELLED", "NO_SHOW", "COMPLETED", "EXPIRED") }
}
