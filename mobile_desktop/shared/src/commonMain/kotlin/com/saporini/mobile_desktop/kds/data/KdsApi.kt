package com.saporini.mobile_desktop.kds.data

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.kds.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

class KdsApi(private val client: HttpClient, private val baseUrl: () -> String = { ApiConfig.BASE_URL }) : KdsRepository {
    private fun path(s: KdsScope) = "${baseUrl().trimEnd('/')}/restaurants/${s.restaurantId.encodeURLPathPart()}/branches/${s.branchId.encodeURLPathPart()}/kds"
    private fun orderPath(s: KdsScope, id: String) = "${baseUrl().trimEnd('/')}/restaurants/${s.restaurantId.encodeURLPathPart()}/orders/${id.encodeURLPathPart()}/kds/tickets"
    override suspend fun board(scope: KdsScope, stationId: String?, deviceId: String?): List<KdsBoard> = client.get("${path(scope)}/board") {
        stationId?.let { parameter("stationId", it) }; deviceId?.let { parameter("deviceId", it) }
        parameter("includeCompleted", false)
    }.body()
    override suspend fun display(scope: KdsScope, deviceId: String): KdsBoard = client.get("${path(scope)}/display") { parameter("deviceId", deviceId) }.body()
    override suspend fun ticket(scope: KdsScope, ticketId: String): KdsTicket = client.get("${path(scope)}/tickets/${ticketId.encodeURLPathPart()}").body()
    override suspend fun orderTickets(scope: KdsScope, orderId: String): List<KdsTicket> = client.get(orderPath(scope, orderId)).body()
    override suspend fun syncOrder(scope: KdsScope, orderId: String): List<KdsTicket> = client.post("${orderPath(scope, orderId)}/sync").body()
    override suspend fun history(scope: KdsScope, filter: KdsHistoryFilter, stationId: String?, deviceId: String?, page: Int, size: Int): KdsTicketPage = client.get("${path(scope)}/history") {
        filter.from?.let { parameter("from", it.toString()) }; filter.to?.let { parameter("to", it.toString()) }
        filter.status?.let { parameter("status", it) }; stationId?.let { parameter("stationId", it) }; deviceId?.let { parameter("deviceId", it) }
        parameter("page", page); parameter("size", size)
    }.body()
    override suspend fun pickUp(scope: KdsScope, ticketId: String): KdsTicket =
        client.post("${path(scope)}/tickets/${ticketId.encodeURLPathPart()}/picked-up") { contentType(ContentType.Application.Json); setBody(KdsActionInput()) }.body()
    override suspend fun posTiming(scope: KdsScope): KdsPosTiming = client.get("${path(scope)}/pos-timing").body()
    override suspend fun action(scope: KdsScope, ticketId: String, itemId: String?, action: KdsAction, input: KdsActionInput): KdsTicket {
        val item = itemId?.let { "/items/${it.encodeURLPathPart()}" }.orEmpty()
        return client.post("${path(scope)}/tickets/${ticketId.encodeURLPathPart()}$item/${action.path}") { contentType(ContentType.Application.Json); setBody(input) }.body()
    }
    override suspend fun stations(scope: KdsScope): List<KdsStation> = client.get("${path(scope)}/stations") { parameter("activeOnly", false) }.body()
    override suspend fun devices(scope: KdsScope): List<KdsDevice> = client.get("${path(scope)}/devices").body()
    override suspend fun saveStation(scope: KdsScope, id: String?, input: KdsStationInput): KdsStation = client.request("${path(scope)}/stations${id?.let { "/${it.encodeURLPathPart()}" }.orEmpty()}") {
        method = if (id == null) HttpMethod.Post else HttpMethod.Put
        contentType(ContentType.Application.Json); setBody(input)
    }.body()
    override fun changes(scope: KdsScope): Flow<KdsLiveEvent> = channelFlow {
        while (currentCoroutineContext().isActive) {
            try {
                client.sse("${path(scope)}/events", request = { timeout { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS; socketTimeoutMillis = 60_000L } }, reconnectionTime = 3.seconds) {
                    incoming.collect { event ->
                        when (event.event) {
                            "connected" -> send(KdsLiveEvent.CONNECTED)
                            "orders-changed" -> send(KdsLiveEvent.CHANGED)
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // Ktor can wrap authentication failures in an SSEClientException.
                val apiError = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<ApiException>().firstOrNull()
                if (apiError?.status in setOf(401, 403)) throw requireNotNull(apiError)
            }
            send(KdsLiveEvent.DISCONNECTED)
            delay(3.seconds)
        }
    }
}
