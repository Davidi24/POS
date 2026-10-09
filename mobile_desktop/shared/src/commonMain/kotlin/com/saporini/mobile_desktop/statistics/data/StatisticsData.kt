package com.saporini.mobile_desktop.statistics.data

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// The server's /restaurants/{r}/statistics API: restaurant-local days from..to (inclusive), money in the
// restaurant's currency as exact decimal text.

@Serializable
data class StatsPeriodDto(
    val from: String,
    val to: String,
    val previousFrom: String? = null,
    val previousTo: String? = null,
    val timezone: String? = null,
    val currency: String = "EUR",
    val branchId: String? = null
)

/** One figure and the same figure for the period just before. */
@Serializable
data class KpiDto(val value: OrderDecimal = OrderDecimal.ZERO, val previous: OrderDecimal = OrderDecimal.ZERO)

@Serializable
data class KpisDto(
    val sales: KpiDto = KpiDto(),
    val orders: KpiDto = KpiDto(),
    val guests: KpiDto = KpiDto(),
    val averageTicket: KpiDto = KpiDto(),
    val averagePerGuest: KpiDto = KpiDto(),
    val tips: KpiDto = KpiDto(),
    val collected: KpiDto = KpiDto(),
    val refunds: KpiDto = KpiDto(),
    val discounts: KpiDto = KpiDto(),
    val removedItemsValue: KpiDto = KpiDto(),
    val cancelledOrders: KpiDto = KpiDto(),
    val voidedOrders: KpiDto = KpiDto(),
    val openOrders: Long = 0,
    val otherCurrencyOrders: Long = 0
)

@Serializable
data class DaySalesDto(val date: String, val sales: OrderDecimal, val orders: Long = 0, val guests: Long = 0)

@Serializable
data class HourSalesDto(val hour: Int, val sales: OrderDecimal, val orders: Long = 0)

@Serializable
data class WeekdaySalesDto(val isoDayOfWeek: Int, val day: String, val sales: OrderDecimal, val orders: Long = 0, val averageSales: OrderDecimal = OrderDecimal.ZERO)

@Serializable
data class ShareDto(val key: String, val sales: OrderDecimal, val orders: Long = 0)

@Serializable
data class MethodTotalDto(val method: String, val collected: OrderDecimal, val count: Long = 0, val tips: OrderDecimal = OrderDecimal.ZERO)

@Serializable
data class ItemSalesDto(val menuItemId: String? = null, val name: String, val section: String? = null, val quantity: Long = 0, val sales: OrderDecimal = OrderDecimal.ZERO)

@Serializable
data class SectionSalesDto(val section: String, val quantity: Long = 0, val sales: OrderDecimal = OrderDecimal.ZERO)

@Serializable
data class StaffStatsDto(
    val staffId: String,
    val name: String,
    val orders: Long = 0,
    val sales: OrderDecimal = OrderDecimal.ZERO,
    val averageTicket: OrderDecimal = OrderDecimal.ZERO,
    val guests: Long = 0,
    val tips: OrderDecimal = OrderDecimal.ZERO,
    val discounts: OrderDecimal = OrderDecimal.ZERO,
    val removedItems: Long = 0,
    val removedValue: OrderDecimal = OrderDecimal.ZERO,
    val refunds: OrderDecimal = OrderDecimal.ZERO,
    val voidedOrders: Long = 0,
    val hoursWorked: OrderDecimal = OrderDecimal.ZERO
)

@Serializable
data class StatsOverviewDto(
    val period: StatsPeriodDto,
    val kpis: KpisDto = KpisDto(),
    val salesByDay: List<DaySalesDto> = emptyList(),
    val paymentMethods: List<MethodTotalDto> = emptyList(),
    val orderTypes: List<ShareDto> = emptyList(),
    val topItems: List<ItemSalesDto> = emptyList()
)

@Serializable
data class StatsSalesDto(
    val period: StatsPeriodDto,
    val salesByDay: List<DaySalesDto> = emptyList(),
    val salesByHour: List<HourSalesDto> = emptyList(),
    val salesByWeekday: List<WeekdaySalesDto> = emptyList(),
    val orderTypes: List<ShareDto> = emptyList(),
    val sources: List<ShareDto> = emptyList(),
    val floors: List<ShareDto> = emptyList(),
    val sections: List<SectionSalesDto> = emptyList(),
    val topItems: List<ItemSalesDto> = emptyList(),
    val slowItems: List<ItemSalesDto> = emptyList()
)

@Serializable
data class StatsStaffDto(val period: StatsPeriodDto, val staff: List<StaffStatsDto> = emptyList())

@Serializable
data class ReportInfoDto(val code: String, val title: String, val description: String = "")

data class StatsQuery(val restaurantId: String, val branchId: String?, val from: LocalDate, val to: LocalDate)

interface StatisticsRepository {
    suspend fun overview(query: StatsQuery): StatsOverviewDto
    suspend fun sales(query: StatsQuery): StatsSalesDto
    suspend fun staff(query: StatsQuery): StatsStaffDto
    suspend fun reports(restaurantId: String): List<ReportInfoDto>

    /** The CSV text of one report (UTF-8, with a byte order mark for spreadsheets). */
    suspend fun report(query: StatsQuery, code: String): String
}

class StatisticsApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : StatisticsRepository {

    private fun path(restaurantId: String, tail: String) =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/statistics/$tail"

    private fun HttpRequestBuilder.window(query: StatsQuery) {
        parameter("from", query.from.toString())
        parameter("to", query.to.toString())
        query.branchId?.let { parameter("branchId", it) }
    }

    override suspend fun overview(query: StatsQuery): StatsOverviewDto =
        client.get(path(query.restaurantId, "overview")) { window(query) }.body()

    override suspend fun sales(query: StatsQuery): StatsSalesDto =
        client.get(path(query.restaurantId, "sales")) { window(query) }.body()

    override suspend fun staff(query: StatsQuery): StatsStaffDto =
        client.get(path(query.restaurantId, "staff")) { window(query) }.body()

    override suspend fun reports(restaurantId: String): List<ReportInfoDto> =
        client.get(path(restaurantId, "reports")).body()

    override suspend fun report(query: StatsQuery, code: String): String {
        require(code.matches(Regex("[a-z-]{1,40}"))) { "Unknown report" }
        return client.get(path(query.restaurantId, "reports/$code.csv")) { window(query) }.bodyAsText()
    }
}
