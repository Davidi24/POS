package com.saporini.mobile_desktop.fraud.data

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// The server's /restaurants/{r}/fraud API. Alerts are worked out on the server from orders and payments;
// only an owner's verdict on each one is stored.

@Serializable
data class FraudAlertDto(
    val key: String,
    val rule: String,
    val severity: String,
    val title: String,
    val detail: String? = null,
    val occurredAt: String? = null,
    val staffId: String? = null,
    val staffName: String? = null,
    val orderId: String? = null,
    val orderNumber: String? = null,
    val paymentId: String? = null,
    val amount: OrderDecimal? = null,
    val currency: String? = null,
    val status: String = "OPEN",
    val reviewNote: String? = null,
    val reviewedBy: String? = null,
    val reviewedByName: String? = null,
    val reviewedAt: String? = null
)

@Serializable
data class FraudAlertPageDto(
    val items: List<FraudAlertDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val truncated: Boolean = false
)

@Serializable
data class FraudRuleCountDto(val rule: String, val severity: String, val title: String, val open: Long = 0, val total: Long = 0,
                             val amount: OrderDecimal = OrderDecimal.ZERO)

@Serializable
data class FraudStaffRiskDto(val staffId: String, val staffName: String? = null, val openAlerts: Long = 0, val highAlerts: Long = 0,
                             val totalAlerts: Long = 0, val amount: OrderDecimal = OrderDecimal.ZERO)

@Serializable
data class FraudDayCountDto(val date: String, val alerts: Long = 0, val high: Long = 0)

@Serializable
data class FraudOverviewDto(
    val from: String,
    val to: String,
    val timezone: String? = null,
    val currency: String? = null,
    val openAlerts: Long = 0,
    val highOpenAlerts: Long = 0,
    val totalAlerts: Long = 0,
    val amountInvolved: OrderDecimal = OrderDecimal.ZERO,
    val rules: List<FraudRuleCountDto> = emptyList(),
    val staff: List<FraudStaffRiskDto> = emptyList(),
    val days: List<FraudDayCountDto> = emptyList(),
    val latest: List<FraudAlertDto> = emptyList(),
    val truncated: Boolean = false
)

@Serializable
data class FraudActivityDto(
    val type: String,
    val at: String? = null,
    val staffId: String? = null,
    val staffName: String? = null,
    val orderId: String? = null,
    val orderNumber: String? = null,
    val amount: OrderDecimal? = null,
    val currency: String? = null,
    val detail: String? = null,
    val onShift: Boolean = true
)

@Serializable
data class FraudActivityPageDto(
    val items: List<FraudActivityDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false
)

@Serializable
data class FraudRuleSettingDto(val rule: String, val severity: String, val title: String, val description: String = "",
                               val enabled: Boolean = true, val threshold: String? = null)

@Serializable
data class FraudRulesDto(
    val discountPercent: Int = 30,
    val refundAmount: OrderDecimal = OrderDecimal.ZERO,
    val voidsPerDay: Int = 5,
    val tipPercent: Int = 30,
    val cashRefundsPerDay: Int = 2,
    val rules: List<FraudRuleSettingDto> = emptyList()
)

@Serializable
data class FraudReviewRequestDto(val status: String, val note: String? = null)

/** The fraud thresholds and switched-off checks, saved in the restaurant settings. */
@Serializable
data class FraudChecksRequestDto(
    val fraudDiscountPercent: Int,
    val fraudRefundAmount: OrderDecimal,
    val fraudVoidsPerDay: Int,
    val fraudTipPercent: Int,
    val fraudCashRefundsPerDay: Int,
    val fraudDisabledRules: List<String>
)

data class FraudQuery(val restaurantId: String, val branchId: String?, val from: LocalDate, val to: LocalDate)

data class FraudAlertFilter(val rule: String? = null, val severity: String? = null, val status: String? = null, val staffId: String? = null)

interface FraudRepository {
    suspend fun overview(query: FraudQuery): FraudOverviewDto
    suspend fun alerts(query: FraudQuery, filter: FraudAlertFilter, page: Int, size: Int): FraudAlertPageDto
    suspend fun activity(query: FraudQuery, type: String?, staffId: String?, page: Int, size: Int): FraudActivityPageDto
    suspend fun rules(restaurantId: String): FraudRulesDto
    suspend fun review(restaurantId: String, alertKey: String, request: FraudReviewRequestDto): FraudAlertDto
    suspend fun updateChecks(restaurantId: String, request: FraudChecksRequestDto)
}

class FraudApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : FraudRepository {

    private fun path(restaurantId: String, tail: String) =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/fraud/$tail"

    private fun HttpRequestBuilder.window(query: FraudQuery) {
        parameter("from", query.from.toString())
        parameter("to", query.to.toString())
        query.branchId?.let { parameter("branchId", it) }
    }

    override suspend fun overview(query: FraudQuery): FraudOverviewDto =
        client.get(path(query.restaurantId, "overview")) { window(query) }.body()

    override suspend fun alerts(query: FraudQuery, filter: FraudAlertFilter, page: Int, size: Int): FraudAlertPageDto =
        client.get(path(query.restaurantId, "alerts")) {
            window(query)
            filter.rule?.let { parameter("rule", it) }
            filter.severity?.let { parameter("severity", it) }
            filter.status?.let { parameter("status", it) }
            filter.staffId?.let { parameter("staffId", it) }
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun activity(query: FraudQuery, type: String?, staffId: String?, page: Int, size: Int): FraudActivityPageDto =
        client.get(path(query.restaurantId, "activity")) {
            window(query)
            type?.let { parameter("type", it) }
            staffId?.let { parameter("staffId", it) }
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun rules(restaurantId: String): FraudRulesDto = client.get(path(restaurantId, "rules")).body()

    override suspend fun updateChecks(restaurantId: String, request: FraudChecksRequestDto) {
        client.patch("${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/settings/fraud-checks") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun review(restaurantId: String, alertKey: String, request: FraudReviewRequestDto): FraudAlertDto =
        client.put(path(restaurantId, "alerts/${alertKey.encodeURLPathPart()}/review")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
