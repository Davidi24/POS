package com.saporini.mobile_desktop.notifications

import com.saporini.mobile_desktop.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class NotificationDto(
    val id: String,
    val branchId: String? = null,
    val eventCode: String? = null,
    val priority: String? = null,
    val subject: String? = null,
    val body: String? = null,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val readAt: String? = null,
    val createdAt: String? = null
)

@Serializable
data class NotificationPageDto(
    val items: List<NotificationDto> = emptyList(),
    val totalElements: Long = 0
)

// Staff notifications (e.g. a new reservation) from the back end's notification feed.
class NotificationApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) {
    private fun path(restaurantId: String) = "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/notifications"

    // Only personal notifications: the feed also holds a technical change log that isn't meant for people.
    suspend fun latest(restaurantId: String, branchId: String, size: Int = 30): NotificationPageDto =
        client.get(path(restaurantId)) {
            parameter("branchId", branchId)
            parameter("personalOnly", true)
            parameter("size", size)
            parameter("direction", "desc")
        }.body()

    suspend fun unreadCount(restaurantId: String, branchId: String): Long =
        client.get(path(restaurantId)) {
            parameter("branchId", branchId)
            parameter("personalOnly", true)
            parameter("unreadOnly", true)
            parameter("size", 1)
        }.body<NotificationPageDto>().totalElements

    suspend fun markRead(restaurantId: String, notificationId: String) {
        client.post("${path(restaurantId)}/${notificationId.encodeURLPathPart()}/read")
    }

    suspend fun markAllRead(restaurantId: String, branchId: String) {
        client.post("${path(restaurantId)}/read-all") { parameter("branchId", branchId) }
    }

    // Emits whenever something new may be there; reconnects on its own.
    fun changes(restaurantId: String, branchId: String): Flow<Unit> = flow {
        val url = "${path(restaurantId)}/stream?branchId=${branchId.encodeURLPathPart()}"
        while (currentCoroutineContext().isActive) {
            try {
                client.sse(urlString = url, request = {
                    timeout {
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        socketTimeoutMillis = 60_000L
                    }
                }, reconnectionTime = 3.seconds) {
                    incoming.collect { event ->
                        if (event.event == "connected" || event.event == "notification") emit(Unit)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The authenticated client refreshes credentials on reconnect.
            }
            delay(3.seconds)
        }
    }
}
