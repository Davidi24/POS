package com.saporini.mobile_desktop.pos.reservations.preorder.ui

import com.saporini.mobile_desktop.core.format.centsOrNull
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderCatalogItem
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderCatalogMenu
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderItemChoices
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderCatalogRepository
import kotlinx.coroutines.CancellationException

/** Where the pre-order editor finds dishes: the restaurant's active menus, and the choices of one dish. */
internal interface PreOrderDishes {
    suspend fun menus(restaurantId: String): List<OrderCatalogMenu>
    suspend fun choices(restaurantId: String, menuId: String, itemId: String): OrderItemChoices
}

/**
 * Every active menu with its dishes. Unlike the till, it doesn't narrow to tonight's event menu: the booking may be on
 * another day, and the server checks each dish against the booking's date when the pre-order is saved.
 */
internal class CatalogPreOrderDishes(private val menus: MenuRepository, private val catalog: OrderCatalogRepository) : PreOrderDishes {
    override suspend fun menus(restaurantId: String): List<OrderCatalogMenu> {
        val ids = mutableListOf<String>()
        var page = 0
        while (true) {
            val response = menus.getMenus(restaurantId = restaurantId, active = true, page = page, size = 100)
            ids += response.items.map { it.id }
            if (!response.hasNext || response.items.isEmpty()) break
            page++
        }
        // One menu that can't be read (switched off meanwhile) doesn't hide the others.
        return ids.distinct().mapNotNull { id ->
            try {
                catalog.getMenu(restaurantId, id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun choices(restaurantId: String, menuId: String, itemId: String): OrderItemChoices =
        catalog.getItemChoices(restaurantId, menuId, itemId)
}

/** A dish that can be ordered, with where it sits. */
internal data class DishEntry(val menuId: String, val menuName: String, val section: String, val item: OrderCatalogItem) {
    val needsChoices: Boolean get() = item.variants.orEmpty().any { it.active } || item.optionGroups.orEmpty().any { it.active }
    val priceCents: Long get() = item.basePrice.centsOrNull() ?: 0
}

internal fun dishesOf(menus: List<OrderCatalogMenu>): List<DishEntry> = menus.flatMap { menu ->
    menu.sections.orEmpty().filter { it.active }.sortedBy { it.displayOrder }.flatMap { section ->
        section.items.orEmpty().filter { it.available }.sortedBy { it.displayOrder }.map { DishEntry(menu.id, menu.name, section.name, it) }
    }
}.distinctBy { it.item.id }
