package com.saporini.mobile_desktop.pos.orders.ui

import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderCatalogRepository
import com.saporini.mobile_desktop.pos.tables.domain.model.BranchTableLayout
import com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository

/** Read menu choices and tables using the same user/branch checks as order operations. */
class OrderCatalogQueries internal constructor(
    private val model: OrdersScreenModel,
    private val catalog: OrderCatalogRepository,
    private val tables: TableLayoutRepository
) {
    suspend fun getMenus(page: Int = 0, size: Int = 50): Result<OrderCatalogPage> =
        model.query("MENUS_READ") { catalog.getMenus(it.restaurantId, page, size) }

    suspend fun getMenu(menuId: String): Result<OrderCatalogMenu> =
        model.query("MENUS_READ") { catalog.getMenu(it.restaurantId, menuId) }

    suspend fun getItemChoices(menuId: String, itemId: String): Result<OrderItemChoices> =
        model.query("MENUS_READ") { catalog.getItemChoices(it.restaurantId, menuId, itemId) }

    suspend fun getTables(): Result<BranchTableLayout> =
        model.query("ORDER_READ") { tables.getTableLayout(it.restaurantId, it.branchId) }
    suspend fun getCustomers() = model.query("SETTINGS_READ") { catalog.getCustomers(it.restaurantId) }
    suspend fun getReservations() = model.query("SETTINGS_READ") { catalog.getReservations(it.restaurantId, it.branchId) }
}
