package com.saporini.mobile_desktop.admin.settings

import com.saporini.mobile_desktop.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// The restaurant's settings as plain JSON: each Admin Hub settings page edits a few fields of these objects and
// sends back the group of fields its endpoint expects.
class SettingsApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) {
    private fun url(restaurantId: String, path: String): String =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/settings$path"

    suspend fun getObject(restaurantId: String, path: String): JsonObject = client.get(url(restaurantId, path)).body()

    suspend fun getArray(restaurantId: String, path: String): JsonArray = client.get(url(restaurantId, path)).body()

    suspend fun put(restaurantId: String, path: String, body: JsonObject): JsonObject = client.put(url(restaurantId, path)) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body()

    suspend fun patch(restaurantId: String, path: String, body: JsonObject): JsonObject = client.patch(url(restaurantId, path)) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body()

    suspend fun post(restaurantId: String, path: String, body: JsonObject): JsonObject = client.post(url(restaurantId, path)) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body()
}
