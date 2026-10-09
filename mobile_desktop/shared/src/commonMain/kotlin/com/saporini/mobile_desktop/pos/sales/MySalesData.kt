package com.saporini.mobile_desktop.pos.sales

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.data.api.OrderApi
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.orders.ui.OrdersScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable data class SalesReport(val restaurantId: String, val branchId: String, val staffId: String, val staffName: String,
    val timezone: String, val date: String, val from: String, val to: String, val shiftId: String? = null,
    val generatedAt: String, val shifts: List<SalesShift> = emptyList(), val currencies: List<SalesTotals> = emptyList(), val attribution: String = "")
@Serializable data class SalesShift(val id: String, val status: String, val start: String, val end: String? = null, val workedMinutes: Long = 0)
@Serializable data class SalesTotals(val currency: String, val ordersServed: Long = 0, val tablesServed: Long = 0, val guestsServed: Long = 0,
    val openOrders: Long = 0, val sales: OrderDecimal = OrderDecimal.ZERO, val subtotal: OrderDecimal = OrderDecimal.ZERO,
    val discounts: OrderDecimal = OrderDecimal.ZERO, val tax: OrderDecimal = OrderDecimal.ZERO, val serviceCharge: OrderDecimal = OrderDecimal.ZERO,
    val averageTicket: OrderDecimal = OrderDecimal.ZERO, val paymentCount: Long = 0, val recordedTips: OrderDecimal = OrderDecimal.ZERO,
    val refunds: OrderDecimal = OrderDecimal.ZERO, val collected: OrderDecimal = OrderDecimal.ZERO, val ordersWithoutPayments: Long = 0,
    val hourly: List<SalesHour> = emptyList(), val paymentMethods: List<SalesMethod> = emptyList(), val topItems: List<SalesItem> = emptyList(),
    val areas: List<SalesArea> = emptyList(), val recentPayments: List<SalesPayment> = emptyList())
@Serializable data class SalesHour(val hour: String, val sales: OrderDecimal)
@Serializable data class SalesMethod(val name: String, val collected: OrderDecimal, val count: Long)
@Serializable data class SalesItem(val name: String, val quantity: Long, val sales: OrderDecimal)
@Serializable data class SalesArea(val name: String, val tables: Long, val sales: OrderDecimal)
@Serializable data class SalesPayment(val id: String, val orderId: String, val orderNumber: String, val tableNumber: String? = null,
    val guests: Int, val paidAt: String, val method: String, val status: String, val collected: OrderDecimal)
data class SalesFilter(val date: LocalDate? = null, val staffId: String? = null, val shiftId: String? = null)
interface MySalesRepository {
    suspend fun report(scope: OrdersScope, filter: SalesFilter): SalesReport
    fun changes(scope: OrdersScope): Flow<Unit>
}
class ApiMySalesRepository(private val client: HttpClient, private val orders: OrderApi,
    private val baseUrl: () -> String = { ApiConfig.BASE_URL }) : MySalesRepository {
    override suspend fun report(scope: OrdersScope, filter: SalesFilter): SalesReport = client.get(
        "${baseUrl().trimEnd('/')}/restaurants/${scope.restaurantId.encodeURLPathPart()}/branches/${scope.branchId.encodeURLPathPart()}/sales/mine") {
        filter.date?.let { parameter("date", it.toString()) }; filter.staffId?.let { parameter("staffId", it) }; filter.shiftId?.let { parameter("shiftId", it) }
    }.body()
    override fun changes(scope: OrdersScope) = orders.observeOrderChanges(scope.restaurantId, scope.branchId)
}
