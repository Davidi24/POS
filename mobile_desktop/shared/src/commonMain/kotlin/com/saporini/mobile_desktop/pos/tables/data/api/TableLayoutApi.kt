package com.saporini.mobile_desktop.pos.tables.data.api

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.tables.data.dto.FloorLayoutRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.FloorLayoutResponseDto
import com.saporini.mobile_desktop.pos.tables.data.dto.ReorderTableCategoriesRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.TableAvailabilityDto
import com.saporini.mobile_desktop.pos.tables.data.dto.TableCategoryRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.TableCategoryResponseDto
import com.saporini.mobile_desktop.pos.tables.data.dto.TableLayoutResponseDto
import com.saporini.mobile_desktop.pos.tables.data.dto.UpdateTableCategoryTablesRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.TableMergeRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.TableRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.TableResponseDto
import com.saporini.mobile_desktop.pos.tables.data.dto.UpdateTableLayoutRequestDto
import com.saporini.mobile_desktop.pos.tables.data.dto.UpdateTableStatusRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

class TableLayoutApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) {

    fun observeLayoutChanges(
        restaurantId: String,
        branchId: String
    ): Flow<Unit> = flow {
        val url = "${baseUrlProvider()}/restaurants/$restaurantId" +
            "/branches/$branchId/table-layout/events"
        var hasConnected = false

        while (currentCoroutineContext().isActive) {
            try {
                client.sse(
                    urlString = url,
                    request = {
                        timeout {
                            requestTimeoutMillis =
                                HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                            socketTimeoutMillis =
                                HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        }
                    },
                    reconnectionTime = 3.seconds
                ) {
                    incoming.collect { event ->
                        when (event.event) {
                            "connected" -> {
                                if (hasConnected) {
                                    emit(Unit)
                                }
                                hasConnected = true
                            }

                            "layout-changed" -> emit(Unit)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                delay(3.seconds)
            }
        }
    }

    suspend fun downloadImage(url: String): ByteArray {
        val resolvedUrl = if (
            url.startsWith("http://") || url.startsWith("https://")
        ) {
            url
        } else {
            "${baseUrlProvider().trimEnd('/')}/${url.trimStart('/')}"
        }

        return client.get(resolvedUrl).body()
    }

    suspend fun getFloorLayouts(
        restaurantId: String,
        branchId: String
    ): List<FloorLayoutResponseDto> {
        return client.get(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                    "/branches/$branchId/floor-layouts"
        ).body()
    }

    suspend fun createFloorLayout(
        restaurantId: String,
        branchId: String,
        request: FloorLayoutRequestDto
    ): FloorLayoutResponseDto {
        return client.post(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                    "/branches/$branchId/floor-layouts"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateFloorLayout(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String,
        request: FloorLayoutRequestDto
    ): FloorLayoutResponseDto {
        return client.put(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                    "/branches/$branchId/floor-layouts/$floorLayoutId"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun uploadPlanImage(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String,
        imageBytes: ByteArray,
        fileName: String,
        contentType: String
    ): FloorLayoutResponseDto {
        return client.put(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                    "/branches/$branchId/floor-layouts" +
                    "/$floorLayoutId/plan-image"
        ) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = imageBytes,
                            headers = Headers.build {
                                append(
                                    HttpHeaders.ContentType,
                                    contentType
                                )
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"$fileName\""
                                )
                            }
                        )
                    }
                )
            )
        }.body()
    }

    suspend fun removePlanImage(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String
    ): FloorLayoutResponseDto {
        return client.delete(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                    "/branches/$branchId/floor-layouts" +
                    "/$floorLayoutId/plan-image"
        ).body()
    }

    suspend fun getTableLayout(
        restaurantId: String,
        branchId: String
    ): TableLayoutResponseDto {
        return client.get(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                    "/branches/$branchId/table-layout"
        ).body()
    }

    suspend fun createTable(
        restaurantId: String,
        branchId: String,
        request: TableRequestDto
    ): TableResponseDto {
        return client.post(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/tables"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteTable(
        restaurantId: String,
        branchId: String,
        tableId: String
    ) {
        client.delete(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/tables/$tableId"
        )
    }

    suspend fun updateTableStatus(
        restaurantId: String,
        branchId: String,
        tableId: String,
        request: UpdateTableStatusRequestDto
    ): TableResponseDto {
        return client.patch(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/tables/$tableId/status"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun mergeTables(
        restaurantId: String,
        branchId: String,
        primaryTableId: String,
        childTableIds: List<String>
    ): TableResponseDto {
        return client.post(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/tables/$primaryTableId/merge"
        ) {
            contentType(ContentType.Application.Json)
            setBody(TableMergeRequestDto(tableIds = childTableIds))
        }.body()
    }

    suspend fun unmergeTables(
        restaurantId: String,
        branchId: String,
        primaryTableId: String
    ): TableResponseDto {
        return client.post(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/tables/$primaryTableId/unmerge"
        ).body()
    }

    suspend fun updateTableLayout(
        restaurantId: String,
        branchId: String,
        request: UpdateTableLayoutRequestDto
    ): TableLayoutResponseDto {
        return client.put(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                    "/branches/$branchId/table-layout"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteFloorLayout(
        restaurantId: String,
        branchId: String,
        floorLayoutId: String
    ) {
        client.delete(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                    "/branches/$branchId/floor-layouts/$floorLayoutId"
        )
    }

    suspend fun getTables(
        restaurantId: String,
        branchId: String
    ): List<TableResponseDto> {
        return client.get(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/tables"
        ).body()
    }

    suspend fun getTableCategories(
        restaurantId: String,
        branchId: String
    ): List<TableCategoryResponseDto> {
        return client.get(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/table-categories"
        ).body()
    }

    suspend fun createTableCategory(
        restaurantId: String,
        branchId: String,
        request: TableCategoryRequestDto
    ): TableCategoryResponseDto {
        return client.post(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/table-categories"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateTableCategory(
        restaurantId: String,
        branchId: String,
        categoryId: String,
        request: TableCategoryRequestDto
    ): TableCategoryResponseDto {
        return client.put(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/table-categories/$categoryId"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun replaceTableCategoryTables(
        restaurantId: String,
        branchId: String,
        categoryId: String,
        tableIds: List<String>
    ) {
        client.put(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/table-categories/$categoryId/tables"
        ) {
            contentType(ContentType.Application.Json)
            setBody(UpdateTableCategoryTablesRequestDto(tableIds))
        }
    }

    suspend fun deleteTableCategory(
        restaurantId: String,
        branchId: String,
        categoryId: String
    ) {
        client.delete(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/table-categories/$categoryId"
        )
    }

    suspend fun getAvailableTables(
        restaurantId: String,
        branchId: String,
        from: String,
        to: String
    ): List<TableAvailabilityDto> {
        return client.get(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/tables/available"
        ) {
            parameter("from", from)
            parameter("to", to)
        }.body()
    }

    suspend fun reorderTableCategories(
        restaurantId: String,
        branchId: String,
        categoryIds: List<String>
    ) {
        client.patch(
            "${baseUrlProvider()}/restaurants/$restaurantId" +
                "/branches/$branchId/table-categories/reorder"
        ) {
            contentType(ContentType.Application.Json)
            setBody(ReorderTableCategoriesRequestDto(categoryIds))
        }
    }
}
