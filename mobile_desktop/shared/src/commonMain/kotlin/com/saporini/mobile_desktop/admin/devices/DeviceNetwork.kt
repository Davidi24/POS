package com.saporini.mobile_desktop.admin.devices

import com.saporini.mobile_desktop.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

// Tills, tablets and screens only on the restaurant's network (user, 2026-11-24), per branch: Settings → Devices & printing.

/**
 * The branch's switch, its saved networks, the network this device is on now ([yourNetwork]; null when the server can't
 * tell) and the networks devices were turned away from in the last week.
 */
@Serializable
data class DeviceNetworkView(
    val branchId: String,
    val branchName: String? = null,
    val onlyOnNetwork: Boolean = false,
    val yourNetwork: String? = null,
    val yourNetworkSaved: Boolean = false,
    val maxNetworks: Int = 5,
    val networks: List<SavedNetwork> = emptyList(),
    val refused: List<RefusedNetwork> = emptyList()
)

/** A saved network: who added it, or the device it was saved with ([learnedFrom]), and when a device last used it. */
@Serializable
data class SavedNetwork(
    val id: String,
    val address: String,
    val addedAt: String? = null,
    val addedBy: String? = null,
    val learnedFrom: String? = null,
    val lastSeenAt: String? = null,
    val yours: Boolean = false
)

/** A network devices tried from: the last time, how many times, which devices, and what they tried to do. */
@Serializable
data class RefusedNetwork(
    val address: String,
    val lastAt: String? = null,
    val times: Int = 1,
    val devices: List<String> = emptyList(),
    val connecting: Boolean = false,
    val pinSignIn: Boolean = false
)

@Serializable
data class DeviceNetworkRuleBody(val onlyOnNetwork: Boolean)

/** No address = the network this device is on; else one a device was turned away from. */
@Serializable
data class AddDeviceNetworkBody(val address: String? = null)

/** `/restaurants/{r}/branches/{b}/device-network` (DEVICES_MANAGE). */
class DeviceNetworkApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) {
    private fun url(restaurantId: String, branchId: String) =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/branches/${branchId.encodeURLPathPart()}/device-network"

    suspend fun get(restaurantId: String, branchId: String): DeviceNetworkView = client.get(url(restaurantId, branchId)).body()

    suspend fun setRule(restaurantId: String, branchId: String, on: Boolean): DeviceNetworkView =
        client.put(url(restaurantId, branchId)) { contentType(ContentType.Application.Json); setBody(DeviceNetworkRuleBody(on)) }.body()

    suspend fun add(restaurantId: String, branchId: String, address: String? = null): DeviceNetworkView =
        client.post("${url(restaurantId, branchId)}/networks") { contentType(ContentType.Application.Json); setBody(AddDeviceNetworkBody(address)) }.body()

    suspend fun remove(restaurantId: String, branchId: String, networkId: String): DeviceNetworkView =
        client.delete("${url(restaurantId, branchId)}/networks/${networkId.encodeURLPathPart()}").body()
}
