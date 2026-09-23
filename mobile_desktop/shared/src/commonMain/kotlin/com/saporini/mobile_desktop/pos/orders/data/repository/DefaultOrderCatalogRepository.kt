package com.saporini.mobile_desktop.pos.orders.data.repository

import com.saporini.mobile_desktop.pos.orders.data.api.OrderCatalogApi
import com.saporini.mobile_desktop.pos.orders.data.dto.toDomain
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderCatalogRepository

class DefaultOrderCatalogRepository(private val api: OrderCatalogApi) : OrderCatalogRepository {
    override suspend fun getMenus(restaurantId: String, page: Int, size: Int): OrderCatalogPage {
        require(restaurantId.isNotBlank() && page >= 0 && size in 1..100)
        return api.getMenus(restaurantId, page, size).toDomain()
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
    override suspend fun getCustomers(restaurantId: String) = api.getCustomers(restaurantId).filter { it.active }
    override suspend fun getReservations(restaurantId: String, branchId: String) = api.getReservations(restaurantId, branchId).filter { it.status !in setOf("CANCELLED", "NO_SHOW", "COMPLETED") }
}
