package com.saporini.mobile_desktop.core.session

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.getPlatform
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A till or tablet says it's on, every minute while the app runs (2026-11-25, user: "online, what is not"): the server keeps
 * when it was last heard from, so Admin Hub → Devices shows it online or offline. Its own client, without the sign-in plugin,
 * so a refused heartbeat never starts a token refresh. [state] says how the last one went.
 */
object TillHeartbeat {

    enum class State {
        /** Nothing sent yet. */
        UNKNOWN,
        /** The server heard it. */
        OK,
        /** The server didn't know this connection any more: it was connected on another device, or switched off. */
        REFUSED,
        /** The server couldn't be reached. */
        UNREACHABLE
    }

    @Serializable
    private data class About(val platform: String?, val osVersion: String?)

    private val _state = MutableStateFlow(State.UNKNOWN)
    val state: StateFlow<State> = _state.asStateFlow()

    private val client by lazy {
        HttpClient {
            install(HttpTimeout) { connectTimeoutMillis = 5_000; requestTimeoutMillis = 8_000; socketTimeoutMillis = 8_000 }
            expectSuccess = false
        }
    }

    private val json = Json { encodeDefaults = true }

    suspend fun beat(till: TillConnection) {
        val platform = getPlatform()
        val version = platform.systemVersion?.trim()?.split('.', '-')?.filter { it.isNotBlank() }?.take(2)?.joinToString(".")
        val about = json.encodeToString(About.serializer(), About(platform.system.take(50), version?.take(50)))
        _state.value = try {
            val answer = client.post("${ApiConfig.BASE_URL.trimEnd('/')}/public/devices/${till.deviceId.encodeURLPathPart()}/heartbeat") {
                header("X-Device-Secret", till.secret)
                contentType(ContentType.Application.Json)
                setBody(about)
            }
            when (answer.status.value) {
                in 200..299 -> State.OK
                401, 403, 404 -> State.REFUSED
                else -> State.UNREACHABLE
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            State.UNREACHABLE
        }
    }

    fun reset() { _state.value = State.UNKNOWN }
}
