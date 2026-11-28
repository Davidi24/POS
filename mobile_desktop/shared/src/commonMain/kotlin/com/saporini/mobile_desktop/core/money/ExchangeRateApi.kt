package com.saporini.mobile_desktop.core.money

import com.saporini.mobile_desktop.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/** The restaurant's currency and what one unit of each currency is in [to] (empty when nothing is converted). */
@Serializable
data class ExchangeRatesDto(
    val currency: String? = null,
    val to: String? = null,
    val date: String? = null,
    val rates: Map<String, Double> = emptyMap()
)

/** `GET /exchange-rates?to=` (any signed-in person): only for seeing amounts in another currency, never for payments. */
class ExchangeRateApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) {
    suspend fun rates(to: String?): ExchangeRatesDto =
        client.get("${baseUrlProvider().trimEnd('/')}/exchange-rates") { if (to != null) parameter("to", to) }.body()
}
