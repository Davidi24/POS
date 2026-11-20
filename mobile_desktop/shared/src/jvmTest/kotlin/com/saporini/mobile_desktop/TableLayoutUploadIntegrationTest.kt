package com.saporini.mobile_desktop

import com.saporini.mobile_desktop.pos.tables.data.api.TableLayoutApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TableLayoutUploadIntegrationTest {

    @Test
    fun uploadsAndDownloadsPlanAgainstConfiguredBackend() = runTest {
        val accessToken =
            System.getenv("POS_INTEGRATION_ACCESS_TOKEN") ?: return@runTest
        val waiterAccessToken =
            System.getenv("POS_INTEGRATION_WAITER_ACCESS_TOKEN")
                ?: accessToken
        val imagePath =
            System.getenv("POS_INTEGRATION_PLAN_IMAGE") ?: return@runTest
        val restaurantId =
            System.getenv("POS_INTEGRATION_RESTAURANT_ID") ?: return@runTest
        val branchId =
            System.getenv("POS_INTEGRATION_BRANCH_ID") ?: return@runTest
        val floorLayoutId =
            System.getenv("POS_INTEGRATION_FLOOR_LAYOUT_ID") ?: return@runTest
        val baseUrl =
            System.getenv("POS_INTEGRATION_BASE_URL")
                ?: "http://localhost:8080"

        val imageFile = File(imagePath)
        val imageBytes = imageFile.readBytes()
        val client = authenticatedClient(accessToken)
        val eventClient = authenticatedClient(waiterAccessToken)

        try {
            val api = TableLayoutApi(
                client = client,
                baseUrlProvider = { baseUrl }
            )
            val eventApi = TableLayoutApi(
                client = eventClient,
                baseUrlProvider = { baseUrl }
            )
            val layoutChanged = async(Dispatchers.IO) {
                withTimeout(15_000) {
                    eventApi.observeLayoutChanges(
                        restaurantId = restaurantId,
                        branchId = branchId
                    ).first()
                }
            }
            delay(750)
            val saved = api.uploadPlanImage(
                restaurantId = restaurantId,
                branchId = branchId,
                floorLayoutId = floorLayoutId,
                imageBytes = imageBytes,
                fileName = imageFile.name,
                contentType = "image/png"
            )
            layoutChanged.await()

            val imageKey = assertNotNull(saved.planImageKey)
            val imageUrl = assertNotNull(saved.planImageUrl)
            val reloaded = api.getFloorLayouts(
                restaurantId = restaurantId,
                branchId = branchId
            ).single { it.id == floorLayoutId }
            val downloaded = api.downloadImage(imageUrl)

            assertEquals(imageKey, reloaded.planImageKey)
            assertContentEquals(imageBytes, downloaded)
        } finally {
            eventClient.close()
            client.close()
        }
    }

    private fun authenticatedClient(accessToken: String) =
        HttpClient(OkHttp) {
            expectSuccess = true
            defaultRequest {
                headers.append(
                    HttpHeaders.Authorization,
                    "Bearer $accessToken"
                )
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
            install(SSE)
        }
}
