package com.saporini.mobile_desktop.reservations

import com.saporini.mobile_desktop.pos.orders.data.api.OrderCatalogApi
import com.saporini.mobile_desktop.pos.orders.data.repository.DefaultOrderCatalogRepository
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import com.saporini.mobile_desktop.pos.reservations.occasionSummary
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Phase 3 in the app: the occasion reminder text, and "only the special menu" on an event night.
class OccasionsAndEventNightsTest {

    private fun booking(occasion: String?, options: List<String> = emptyList()) = Reservation(
        id = "r1", restaurantId = "rest", branchId = "b1", status = ReservationStatus.CONFIRMED, partySize = 4,
        reservationStart = "2026-09-28T17:00:00Z", reservationEnd = "2026-09-28T19:00:00Z",
        occasionName = occasion, occasionIcon = occasion?.let { "🎂" }, occasionOptions = options
    )

    @Test
    fun occasionReminderText() {
        assertEquals("🎂 Birthday: Cake from us, Candles", booking("Birthday", listOf("Cake from us", "Candles")).occasionSummary())
        assertEquals("🎂 Birthday", booking("Birthday").occasionSummary())
        assertNull(booking(null).occasionSummary())
    }

    private fun catalog(onlyMenu: String?): DefaultOrderCatalogRepository {
        val page = """
            {"items":[{"id":"dinner","name":"Dinner","active":true},{"id":"valentine","name":"Valentine's","active":true}],
             "page":0,"size":50,"totalElements":2,"totalPages":1,"hasNext":false,"hasPrevious":false}
        """.trimIndent()
        val client = HttpClient(MockEngine { respond(page, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return DefaultOrderCatalogRepository(OrderCatalogApi(client) { "http://test" }) { onlyMenu }
    }

    @Test
    fun eventNightOffersOnlyItsMenu() = runTest {
        assertEquals(listOf("valentine"), catalog("valentine").getMenus("rest").items.map { it.id })
    }

    @Test
    fun otherNightsOfferEveryMenu() = runTest {
        assertEquals(listOf("dinner", "valentine"), catalog(null).getMenus("rest").items.map { it.id })
    }
}
