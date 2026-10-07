package com.saporini.mobile_desktop.pos.history

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.data.api.OrderApi
import com.saporini.mobile_desktop.pos.orders.data.dto.*
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.ui.OrdersScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlin.time.Instant

data class HistoryFilter(val from: Instant? = null, val to: Instant? = null, val status: OrderStatus? = null,
    val customerId: String? = null, val staffId: String? = null, val search: String = "") {
    fun problem(): String? = when {
        from != null && to != null && from >= to -> "The end must be after the start."
        status != null && status !in setOf(OrderStatus.CLOSED, OrderStatus.CANCELLED, OrderStatus.VOIDED) -> "Select a finished order status."
        search.length > 200 -> "Search must be 200 characters or fewer."
        else -> null
    }
}
data class HistoryPage(val items: List<OrderSummary>, val page: Int, val size: Int, val totalElements: Long, val hasNext: Boolean)
interface OrderHistoryRepository {
    suspend fun page(scope: OrdersScope, filter: HistoryFilter, page: Int, size: Int): HistoryPage
    suspend fun detail(scope: OrdersScope, id: String): Order
    fun changes(scope: OrdersScope): Flow<Unit>
}
@Serializable private data class HistoryPageDto(val items: List<OrderSummaryResponseDto>, val page: Int, val size: Int,
    val totalElements: Long, val hasNext: Boolean)
class ApiOrderHistoryRepository(private val client: HttpClient, private val orders: OrderApi,
    private val baseUrl: () -> String = { ApiConfig.BASE_URL }) : OrderHistoryRepository {
    override suspend fun page(scope: OrdersScope, filter: HistoryFilter, page: Int, size: Int): HistoryPage {
        val dto = client.get("${baseUrl().trimEnd('/')}/restaurants/${scope.restaurantId.encodeURLPathPart()}/branches/${scope.branchId.encodeURLPathPart()}/orders/history/page") {
            parameter("page", page); parameter("size", size)
            filter.from?.let { parameter("from", it.toString()) }; filter.to?.let { parameter("to", it.toString()) }
            filter.status?.let { parameter("status", it.name) }; filter.customerId?.let { parameter("customerId", it) }
            filter.staffId?.let { parameter("staffId", it) }; filter.search.trim().takeIf { it.isNotEmpty() }?.let { parameter("search", it) }
        }.body<HistoryPageDto>()
        return HistoryPage(dto.items.map { it.toDomain() }, dto.page, dto.size, dto.totalElements, dto.hasNext)
    }
    override suspend fun detail(scope: OrdersScope, id: String) = orders.getOrder(scope.restaurantId, id).toDomain()
    override fun changes(scope: OrdersScope) = orders.observeOrderChanges(scope.restaurantId, scope.branchId)
}
