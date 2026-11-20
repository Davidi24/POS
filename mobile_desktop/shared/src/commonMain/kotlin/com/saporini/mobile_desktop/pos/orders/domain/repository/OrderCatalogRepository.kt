package com.saporini.mobile_desktop.pos.orders.domain.repository

import com.saporini.mobile_desktop.pos.orders.domain.model.*

interface OrderCatalogRepository {
    suspend fun getMenus(restaurantId: String, page: Int = 0, size: Int = 50): OrderCatalogPage
    suspend fun getMenu(restaurantId: String, menuId: String): OrderCatalogMenu
    suspend fun getItemChoices(restaurantId: String, menuId: String, itemId: String): OrderItemChoices
    suspend fun getCustomers(restaurantId: String): List<com.saporini.mobile_desktop.pos.orders.domain.model.OrderCustomerChoice> = emptyList()
    suspend fun getReservations(restaurantId: String, branchId: String): List<com.saporini.mobile_desktop.pos.orders.domain.model.OrderReservationChoice> = emptyList()
}
