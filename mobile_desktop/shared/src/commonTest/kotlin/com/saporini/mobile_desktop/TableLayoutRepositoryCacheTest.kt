package com.saporini.mobile_desktop

import com.saporini.mobile_desktop.pos.tables.data.api.TableLayoutApi
import com.saporini.mobile_desktop.pos.tables.data.repository.DefaultTableLayoutRepository
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTable
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableShape
import com.saporini.mobile_desktop.pos.tables.domain.model.LayoutTableStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TableLayoutRepositoryCacheTest {

    @Test
    fun cachesLayoutAndUploadedPlanUntilAnAdminChangesThem() = runTest {
        var floorLayoutGets = 0
        var tableLayoutGets = 0
        var imageGets = 0
        var planUploads = 0
        var tableLayoutPuts = 0
        var uploadBody = ""

        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Get &&
                    path.endsWith("/floor-layouts") -> {
                    floorLayoutGets++
                    respondJson(
                        """
                        [{
                          "id": "floor-1",
                          "restaurantId": "restaurant-1",
                          "branchId": "branch-1",
                          "floorName": "1st Floor",
                          "planImageKey": null,
                          "planImageUrl": null
                        }]
                        """.trimIndent()
                    )
                }

                request.method == HttpMethod.Get &&
                    path.endsWith("/table-layout") -> {
                    tableLayoutGets++
                    respondJson(
                        """
                        {
                          "restaurantId": "restaurant-1",
                          "branchId": "branch-1",
                          "floors": [],
                          "tables": []
                        }
                        """.trimIndent()
                    )
                }

                request.method == HttpMethod.Put &&
                    path.endsWith("/plan-image") -> {
                    planUploads++
                    uploadBody = request.body
                        .toByteArray()
                        .decodeToString()
                    respondJson(
                        """
                        {
                          "id": "floor-1",
                          "restaurantId": "restaurant-1",
                          "branchId": "branch-1",
                          "floorName": "1st Floor",
                          "planImageKey": "restaurant-1/branch-1/plan.png",
                          "planImageUrl": "http://test-host/plan.png"
                        }
                        """.trimIndent()
                    )
                }

                request.method == HttpMethod.Put &&
                    path.endsWith("/table-layout") -> {
                    tableLayoutPuts++
                    respondJson(
                        """
                        {
                          "restaurantId": "restaurant-1",
                          "branchId": "branch-1",
                          "floors": [{
                            "name": "1st Floor",
                            "tableCount": 1,
                            "positionedTableCount": 1
                          }],
                          "tables": [{
                            "tableId": "table-1",
                            "tableNumber": "T01",
                            "name": "T01",
                            "capacity": 4,
                            "effectiveCapacity": 4,
                            "floor": "1st Floor",
                            "positionX": 0.25,
                            "positionY": 0.50,
                            "rotationDegrees": 0.0,
                            "layoutScale": 0.66,
                            "shape": "ROUND",
                            "status": "AVAILABLE",
                            "active": true
                          }]
                        }
                        """.trimIndent()
                    )
                }

                request.method == HttpMethod.Get &&
                    path.endsWith("/plan.png") -> {
                    imageGets++
                    respond(
                        content = byteArrayOf(9, 8, 7),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Image.PNG.toString()
                        )
                    )
                }

                else -> error(
                    "Unexpected request: ${request.method.value} $path"
                )
            }
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }

        val repository = DefaultTableLayoutRepository(
            TableLayoutApi(
                client = client,
                baseUrlProvider = { "http://test-host" }
            )
        )

        repository.getFloorLayouts("restaurant-1", "branch-1")
        repository.getFloorLayouts("restaurant-1", "branch-1")
        repository.getTableLayout("restaurant-1", "branch-1")
        repository.getTableLayout("restaurant-1", "branch-1")

        val uploadedBytes = byteArrayOf(1, 2, 3, 4)
        val saved = repository.uploadPlanImage(
            restaurantId = "restaurant-1",
            branchId = "branch-1",
            floorLayoutId = "floor-1",
            imageBytes = uploadedBytes,
            fileName = "plan.png",
            contentType = "image/png"
        )

        val cachedFloors = repository.getFloorLayouts(
            "restaurant-1",
            "branch-1"
        )
        val cachedImage = repository.downloadPlanImage(
            assertNotNull(saved.planImageUrl)
        )
        val savedTableLayout = repository.saveTableLayout(
            restaurantId = "restaurant-1",
            branchId = "branch-1",
            tables = listOf(
                LayoutTable(
                    id = "table-1",
                    mergedIntoTableId = null,
                    mergedTableIds = emptyList(),
                    tableNumber = "T01",
                    name = "T01",
                    capacity = 4,
                    effectiveCapacity = 4,
                    floor = "1st Floor",
                    positionX = 0.25f,
                    positionY = 0.50f,
                    rotationDegrees = 0f,
                    scale = 0.66f,
                    shape = LayoutTableShape.ROUND,
                    status = LayoutTableStatus.AVAILABLE,
                    active = true
                )
            )
        )
        val cachedTableLayout = repository.getTableLayout(
            "restaurant-1",
            "branch-1"
        )

        assertEquals(1, floorLayoutGets)
        assertEquals(1, tableLayoutGets)
        assertEquals(1, planUploads)
        assertEquals(1, tableLayoutPuts)
        assertEquals(0, imageGets)
        assertEquals(saved, cachedFloors.single())
        assertEquals(savedTableLayout, cachedTableLayout)
        assertContentEquals(uploadedBytes, cachedImage)
        assertTrue(uploadBody.contains("name=\"file\""))
        assertTrue(uploadBody.contains("filename=\"plan.png\""))
        assertTrue(uploadBody.contains("Content-Type: image/png"))
        assertFalse(uploadBody.contains("; file;"))

        val layoutChange = async(start = CoroutineStart.UNDISPATCHED) {
            repository.layoutChanges.first()
        }
        repository.invalidateFromServer(
            restaurantId = "restaurant-1",
            branchId = "branch-1"
        )

        assertEquals("restaurant-1", layoutChange.await().restaurantId)
        repository.getFloorLayouts("restaurant-1", "branch-1")
        repository.getTableLayout("restaurant-1", "branch-1")
        assertEquals(2, floorLayoutGets)
        assertEquals(2, tableLayoutGets)
    }

    @Test
    fun persistsMergeThenCachesTheMergedBackendLayout() = runTest {
        var unmergePosts = 0
        var mergePosts = 0
        var layoutGets = 0
        var mergeBody = ""

        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Post &&
                    path.endsWith("/old-parent/unmerge") -> {
                    unmergePosts++
                    respondJson(tableResponseJson("old-parent", "T09"))
                }

                request.method == HttpMethod.Post &&
                    path.endsWith("/parent/merge") -> {
                    mergePosts++
                    mergeBody = request.body.toByteArray().decodeToString()
                    respondJson(tableResponseJson("parent", "T01"))
                }

                request.method == HttpMethod.Get &&
                    path.endsWith("/table-layout") -> {
                    layoutGets++
                    respondJson(
                        """
                        {
                          "restaurantId": "restaurant-1",
                          "branchId": "branch-1",
                          "floors": [],
                          "tables": [{
                            "tableId": "parent",
                            "mergedIntoTableId": null,
                            "mergedTableIds": ["child"],
                            "tableNumber": "T01",
                            "capacity": 4,
                            "effectiveCapacity": 8,
                            "floor": "1st Floor",
                            "positionX": 0.25,
                            "positionY": 0.50,
                            "shape": "ROUND",
                            "status": "AVAILABLE",
                            "active": true
                          }, {
                            "tableId": "child",
                            "mergedIntoTableId": "parent",
                            "mergedTableIds": [],
                            "tableNumber": "T02",
                            "capacity": 4,
                            "effectiveCapacity": 4,
                            "floor": "1st Floor",
                            "positionX": 0.35,
                            "positionY": 0.50,
                            "shape": "ROUND",
                            "status": "AVAILABLE",
                            "active": true
                          }]
                        }
                        """.trimIndent()
                    )
                }

                else -> error(
                    "Unexpected request: ${request.method.value} $path"
                )
            }
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        val repository = DefaultTableLayoutRepository(
            TableLayoutApi(
                client = client,
                baseUrlProvider = { "http://test-host" }
            )
        )

        val saved = repository.saveTableMerge(
            restaurantId = "restaurant-1",
            branchId = "branch-1",
            primaryTableId = "parent",
            childTableIds = listOf("child"),
            previousPrimaryTableIds = listOf("old-parent")
        )
        val cached = repository.getTableLayout(
            "restaurant-1",
            "branch-1"
        )

        assertEquals(1, unmergePosts)
        assertEquals(1, mergePosts)
        assertEquals(1, layoutGets)
        assertTrue(mergeBody.contains("\"tableIds\":[\"child\"]"))
        assertEquals(listOf("child"), saved.tables.first().mergedTableIds)
        assertEquals(saved, cached)
    }

    private fun tableResponseJson(id: String, tableNumber: String): String =
        """
        {
          "id": "$id",
          "mergedTableIds": [],
          "tableNumber": "$tableNumber",
          "capacity": 4,
          "effectiveCapacity": 4,
          "floor": "1st Floor",
          "positionX": 0.25,
          "positionY": 0.50,
          "shape": "ROUND",
          "status": "AVAILABLE",
          "active": true
        }
        """.trimIndent()

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        content: String
    ) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString()
        )
    )
}
