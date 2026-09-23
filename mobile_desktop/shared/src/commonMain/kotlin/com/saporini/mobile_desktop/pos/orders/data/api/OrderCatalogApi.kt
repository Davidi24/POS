package com.saporini.mobile_desktop.pos.orders.data.api

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderCustomerChoice
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderReservationChoice
import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.data.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.encodeURLPathPart

class OrderCatalogApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) {
    private fun endpoint(path: String) = "${baseUrlProvider().trimEnd('/')}$path"

    suspend fun getMenus(restaurantId: String, page: Int, size: Int): OrderCatalogPageDto =
        client.get(endpoint("/menus")) {
            parameter("restaurantId", restaurantId)
            parameter("active", true)
            parameter("page", page)
            parameter("size", size)
            parameter("sortBy", "displayOrder")
            parameter("direction", "asc")
        }.body()

    suspend fun getMenu(menuId: String): OrderCatalogMenuDto =
        client.get(endpoint("/menus/${menuId.encodeURLPathPart()}")) {
            parameter("includeSections", true)
            parameter("includeItems", true)
            parameter("includeVariants", true)
            parameter("includeOptionGroups", true)
        }.body()

    suspend fun getChoiceGroup(groupId: String): OrderChoiceGroupDto =
        client.get(endpoint("/option-groups/${groupId.encodeURLPathPart()}")) {
            parameter("includeItems", true)
        }.body()
    suspend fun getCustomers(restaurantId: String): List<OrderCustomerChoice> =
        client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/customers")).body()
    suspend fun getReservations(restaurantId: String, branchId: String): List<OrderReservationChoice> =
        client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/reservations/today")).body()
}
