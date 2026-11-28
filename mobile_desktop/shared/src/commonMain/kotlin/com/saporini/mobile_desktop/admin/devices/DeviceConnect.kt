package com.saporini.mobile_desktop.admin.devices

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.core.session.DeviceIdentity
import com.saporini.mobile_desktop.core.session.TillConnection
import com.saporini.mobile_desktop.core.session.deviceSystem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
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
import kotlin.time.Clock

// A till or tablet's code typed on another device waits for the person who made the code to agree (user, 2026-11-25: "when
// someone puts the code, it goes to the person who generated it; I agree, and in that moment I'm removed from the till").

/**
 * A request as both sides see it. [status]: WAITING, AGREED, REFUSED, CANCELLED, DONE or EXPIRED. [secondsLeft] is counted
 * by the server, so the two devices' clocks don't matter. [deviceInUse]: the till is connected on some device now.
 */
@Serializable
data class ConnectRequestDto(
    val id: String,
    val status: String,
    val deviceId: String,
    val deviceName: String,
    val deviceType: String? = null,
    val askedBy: String? = null,
    val askingDevice: String? = null,
    val askingSystem: String? = null,
    val madeById: String? = null,
    val madeBy: String? = null,
    val deviceInUse: Boolean = false,
    val secondsLeft: Long = 0
) {
    val open: Boolean get() = status == "WAITING" || status == "AGREED"
    /** What the asking device is called ("Linux computer (fedora)"), or plain words when it didn't say. */
    val asking: String get() = askingDevice?.takeIf { it.isNotBlank() } ?: "Another device"
}

@Serializable
data class ConnectAsked(val request: ConnectRequestDto, val claimToken: String)

@Serializable
private data class ConnectAskBody(val pairingToken: String, val kind: String? = null)

@Serializable
private data class ConnectClaimBody(val claimToken: String)

@Serializable
private data class ConnectClaimed(
    val deviceId: String,
    val restaurantId: String,
    val branchId: String? = null,
    val deviceName: String,
    val deviceType: String? = null,
    val deviceSecret: String,
    val networkSaved: Boolean = false
)

/** The connection this device got once the code's maker agreed, and whether its Wi-Fi became the restaurant's network. */
data class ConnectedByCode(val till: TillConnection, val networkSaved: Boolean)

/** `/restaurants/{r}/device-connect-requests`: asking (the new device) and answering (the code's maker). */
interface DeviceConnector {
    // [kind] is where the code is typed: TILL or TABLET (a code for the other kind doesn't work there).
    suspend fun ask(restaurantId: String, code: String, kind: String? = null): ConnectAsked
    suspend fun get(restaurantId: String, requestId: String): ConnectRequestDto
    suspend fun cancel(restaurantId: String, requestId: String): ConnectRequestDto
    suspend fun claim(restaurantId: String, requestId: String, claimToken: String): ConnectedByCode
    suspend fun waiting(restaurantId: String): List<ConnectRequestDto>
    suspend fun agree(restaurantId: String, requestId: String): ConnectRequestDto
    suspend fun refuse(restaurantId: String, requestId: String): ConnectRequestDto
}

class DeviceConnectApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : DeviceConnector {

    private fun base(restaurantId: String) =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/device-connect-requests"

    private fun one(restaurantId: String, requestId: String) = "${base(restaurantId)}/${requestId.encodeURLPathPart()}"

    override suspend fun ask(restaurantId: String, code: String, kind: String?): ConnectAsked =
        client.post(base(restaurantId)) {
            // What this device is, so the maker knows which one asks ("Linux computer (fedora)", "Linux 6.17").
            header("X-Device-Name", DeviceIdentity.name.filter { it.code in 32..126 }.trim().take(150))
            header("X-Device-System", deviceSystem())
            contentType(ContentType.Application.Json)
            setBody(ConnectAskBody(code.trim(), kind))
        }.body()

    override suspend fun get(restaurantId: String, requestId: String): ConnectRequestDto = client.get(one(restaurantId, requestId)).body()

    override suspend fun cancel(restaurantId: String, requestId: String): ConnectRequestDto = client.delete(one(restaurantId, requestId)).body()

    override suspend fun claim(restaurantId: String, requestId: String, claimToken: String): ConnectedByCode {
        val answer = client.post("${one(restaurantId, requestId)}/claim") {
            contentType(ContentType.Application.Json)
            setBody(ConnectClaimBody(claimToken))
        }.body<ConnectClaimed>()
        return ConnectedByCode(TillConnection(answer.deviceId, answer.deviceSecret, answer.restaurantId, answer.branchId,
            answer.deviceName, answer.deviceType), answer.networkSaved)
    }

    override suspend fun waiting(restaurantId: String): List<ConnectRequestDto> = client.get("${base(restaurantId)}/waiting").body()

    override suspend fun agree(restaurantId: String, requestId: String): ConnectRequestDto = client.post("${one(restaurantId, requestId)}/agree").body()

    override suspend fun refuse(restaurantId: String, requestId: String): ConnectRequestDto = client.post("${one(restaurantId, requestId)}/refuse").body()
}

/**
 * While a code made on this device can still be typed somewhere, the app looks every few seconds for someone asking with it
 * (besides the notification, which can be late when the live connection is down).
 */
object ConnectWatch {
    private const val MAX_WATCH_MS = 60 * 60_000L

    private val _until = MutableStateFlow(0L)
    val until: StateFlow<Long> = _until.asStateFlow()

    /** A code was made that lasts until [expiresAtMs] (or an hour, whichever comes first). */
    fun codeMade(expiresAtMs: Long?) {
        val now = Clock.System.now().toEpochMilliseconds()
        val end = minOf(expiresAtMs ?: (now + MAX_WATCH_MS), now + MAX_WATCH_MS)
        if (end > _until.value) _until.value = end
    }
}
