package com.saporini.mobile_desktop.admin.inventory

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable

// Admin Hub → Inventory: the server's /restaurants/{r}/inventory API. Quantities and costs are exact decimal text.

val INVENTORY_UNITS = listOf("EACH", "GRAM", "KILOGRAM", "MILLILITER", "LITER", "OUNCE", "POUND", "CUP", "TABLESPOON",
    "TEASPOON", "PORTION", "CASE", "BOTTLE", "PACK", "TRAY")
val INVENTORY_ITEM_TYPES = listOf("INGREDIENT", "PREPARED_COMPONENT", "FINISHED_GOOD", "BEVERAGE", "ALCOHOL", "PACKAGING", "SUPPLY")
val INVENTORY_LOCATION_TYPES = listOf("BRANCH_STORAGE", "KITCHEN", "BAR", "WALK_IN", "FREEZER", "DRY_STORAGE", "CENTRAL_WAREHOUSE", "TRANSIT")
// Kinds the restaurant manages (user, 2026-10-30): the built-in codes above are there from the start and can be removed;
// its own kinds are type CUSTOM + kindId; UNCATEGORIZED is where things go when their kind or place is deleted.
const val UNCATEGORIZED_KIND = "UNCATEGORIZED"
const val CUSTOM_KIND = "CUSTOM"
const val ITEM_KINDS = "ITEM"
const val PLACE_KINDS = "PLACE"

/** A kind as `/inventory/kinds` lists it: a built-in code (named by the app) or one of the restaurant's own (code CUSTOM). */
@Serializable
data class StockKindDto(
    val code: String,
    val id: String? = null,
    val name: String? = null,
    val builtIn: Boolean = true,
    val count: Long = 0
) {
    /** What items, places and filters use to point at this kind: the code, or "CUSTOM:<id>" for an own kind. */
    val key: String get() = if (code == CUSTOM_KIND) "$CUSTOM_KIND:$id" else code
}

/** The kinds a restaurant starts with, for when the list couldn't load. */
fun defaultKinds(scope: String): List<StockKindDto> =
    ((if (scope == PLACE_KINDS) INVENTORY_LOCATION_TYPES else INVENTORY_ITEM_TYPES) + UNCATEGORIZED_KIND).map { StockKindDto(it) }

@Serializable
data class StockKindRequestDto(val appliesTo: String, val name: String)

/** Someone who changed stock or items, with how many changes, for History's person filter. */
@Serializable
data class StockPersonDto(val id: String, val name: String, val count: Long = 0)

/** Something that happened to a stock item itself (ITEM_CREATED / ITEM_UPDATED / ITEM_DELETED), for History. */
@Serializable
data class StockItemEventDto(
    val id: String,
    val itemId: String? = null,
    val itemName: String? = null,
    val action: String,
    val summary: String,
    val occurredAt: String? = null,
    val createdById: String? = null,
    val createdByUserName: String? = null
)

/** History's "Item changes" kind: the items' own events instead of stock changes. */
const val ITEM_CHANGES = "ITEM_CHANGES"
/** History mode for approved physical stock counts. */
const val COUNT_HISTORY = "COUNT_HISTORY"

@Serializable
data class StockPlaceDeleteRequestDto(val keepStock: Boolean)

val INVENTORY_MOVEMENT_TYPES = listOf("PURCHASE", "RECEIPT", "COUNT_ADJUSTMENT", "WASTE", "TRANSFER_IN", "TRANSFER_OUT",
    "SALE_CONSUMPTION", "RETURN", "VOID", "MANUAL_ADJUSTMENT", "PREP_PRODUCTION")

@Serializable
data class StockItemDto(
    val id: String,
    val code: String? = null,
    val name: String,
    val description: String? = null,
    val itemType: String,
    // One of the restaurant's own kinds (itemType CUSTOM).
    val kindId: String? = null,
    val kindName: String? = null,
    val baseUnit: String,
    val barcode: String? = null,
    val supplierName: String? = null,
    // One of the restaurant's suppliers (Inventory → Suppliers); supplierName is their name.
    val supplierId: String? = null,
    val supplierSku: String? = null,
    val costPerUnit: OrderDecimal = OrderDecimal.ZERO,
    val reorderPoint: OrderDecimal? = null,
    val parLevel: OrderDecimal? = null,
    val trackInventory: Boolean = true,
    val active: Boolean = true,
    val storageNotes: String? = null,
    val imageUrl: String? = null,
    // The sub-recipe that makes it, when it's made here (a sauce, a dough); null for bought items.
    val recipeId: String? = null,
    val updatedByUserName: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class StockItemRequestDto(
    val code: String? = null,
    val name: String,
    val description: String? = null,
    val itemType: String,
    val kindId: String? = null,
    val baseUnit: String,
    val barcode: String? = null,
    val supplierName: String? = null,
    // One of the restaurant's suppliers; without it the server finds (or adds) a supplier by supplierName.
    val supplierId: String? = null,
    val supplierSku: String? = null,
    val costPerUnit: OrderDecimal,
    val reorderPoint: OrderDecimal? = null,
    val parLevel: OrderDecimal? = null,
    // No defaults on purpose: the app's JSON leaves default values out, and these must always be sent.
    val trackInventory: Boolean,
    val active: Boolean,
    val storageNotes: String? = null
)

@Serializable
data class StockLocationDto(
    val id: String,
    val branchId: String? = null,
    val code: String? = null,
    val name: String,
    val locationType: String,
    // One of the restaurant's own kinds (locationType CUSTOM).
    val kindId: String? = null,
    val kindName: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val iconCode: String? = null,
    // The restaurant's own picture of the place, shown instead of the icon (PUT …/locations/{id}/image).
    val imageUrl: String? = null
)

@Serializable
data class StockLocationRequestDto(
    val branchId: String? = null,
    val code: String? = null,
    val name: String,
    val locationType: String,
    val kindId: String? = null,
    val notes: String? = null,
    val active: Boolean,
    val iconCode: String? = null
)

@Serializable
data class StockLevelDto(
    val id: String? = null,
    val locationId: String,
    val locationName: String? = null,
    val inventoryItemId: String,
    val inventoryItemName: String? = null,
    val onHandQuantity: OrderDecimal = OrderDecimal.ZERO,
    val committedQuantity: OrderDecimal = OrderDecimal.ZERO,
    val availableQuantity: OrderDecimal = OrderDecimal.ZERO,
    val parQuantity: OrderDecimal? = null,
    val reorderQuantity: OrderDecimal? = null,
    val lastCountedAt: String? = null,
    val lastMovementAt: String? = null,
    val lowStock: Boolean = false
)

@Serializable
data class SaleStockSourceDto(
    val id: String,
    val branchId: String,
    val branchName: String? = null,
    val inventoryItemId: String,
    val inventoryItemName: String? = null,
    val locationId: String,
    val locationName: String? = null
)

@Serializable
data class SaleStockSourceRequestDto(val locationId: String)

@Serializable
data class StockMovementDto(
    val id: String,
    val locationId: String? = null,
    val locationName: String? = null,
    val inventoryItemId: String? = null,
    val inventoryItemName: String? = null,
    val orderLineItemId: String? = null,
    val movementType: String,
    val quantityDelta: OrderDecimal = OrderDecimal.ZERO,
    val unit: String? = null,
    val unitCostSnapshot: OrderDecimal? = null,
    val totalCostDelta: OrderDecimal? = null,
    val reason: String? = null,
    val occurredAt: String? = null,
    val createdByUserName: String? = null
)

/** Stock received, used by sales and wasted per day at cost (`GET /inventory/activity`), for the overview. */
@Serializable
data class StockActivityDto(
    val currency: String? = null,
    val from: String? = null,
    val to: String? = null,
    val received: OrderDecimal = OrderDecimal.ZERO,
    val used: OrderDecimal = OrderDecimal.ZERO,
    val wasted: OrderDecimal = OrderDecimal.ZERO,
    val changes: Long = 0,
    val days: List<StockActivityDayDto> = emptyList()
)

@Serializable
data class StockActivityDayDto(
    val date: String,
    val received: OrderDecimal = OrderDecimal.ZERO,
    val used: OrderDecimal = OrderDecimal.ZERO,
    val wasted: OrderDecimal = OrderDecimal.ZERO,
    val changes: Long = 0
)

@Serializable
data class StockCountLineDto(
    val id: String,
    val inventoryItemId: String,
    val itemNameSnapshot: String? = null,
    val expectedQuantity: OrderDecimal? = null,
    val countedQuantity: OrderDecimal? = null,
    val varianceQuantity: OrderDecimal? = null,
    val unit: String? = null,
    val unitCostSnapshot: OrderDecimal? = null,
    val varianceValue: OrderDecimal? = null,
    val notes: String? = null
)

@Serializable
data class StockCountDto(
    val id: String,
    val branchId: String? = null,
    val locationId: String,
    val locationName: String? = null,
    val countNumber: String? = null,
    val status: String,
    val scheduledAt: String? = null,
    val completedAt: String? = null,
    val approvedByUserName: String? = null,
    val approvedAt: String? = null,
    val varianceValue: OrderDecimal? = null,
    val notes: String? = null,
    val createdByUserName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lines: List<StockCountLineDto> = emptyList()
) {
    /** Lines can be counted only while the count is open. */
    val editable: Boolean get() = status == "DRAFT" || status == "IN_PROGRESS"
}

@Serializable
data class StockCountPageDto(
    val items: List<StockCountDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

/** One stock change: receive, waste, return, adjust (signed) or move between places. */
@Serializable
data class StockMoveRequestDto(
    val locationId: String? = null,
    val fromLocationId: String? = null,
    val toLocationId: String? = null,
    val inventoryItemId: String,
    val quantity: OrderDecimal? = null,
    val quantityDelta: OrderDecimal? = null,
    val unitCostOverride: OrderDecimal? = null,
    val reason: String? = null
)

@Serializable
data class StockCountCreateRequestDto(val locationId: String, val branchId: String? = null, val notes: String? = null)

@Serializable
data class StockCountLineRequestDto(val countedQuantity: OrderDecimal, val notes: String? = null)

@Serializable
enum class StockMoveKind(val path: String) { RECEIVE("receive"), WASTE("waste"), RETURN("returns"), ADJUST("adjustments"), TRANSFER("transfer") }

const val DEFAULT_VALUABLE_STOCK_FROM = 100.0

// The one restaurant setting Inventory reads.
@Serializable
private data class InventorySettingsDto(val valuableStockFrom: Double? = null)

@Serializable
data class InventoryCountScheduleDto(
    val frequency: Int = 1,
    val period: String = "WEEKLY",
    val startedAt: String? = null
)

interface InventoryRepository {
    /** Emits an invalidation on connect and after a committed inventory change. */
    fun observeChanges(restaurantId: String): Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun items(restaurantId: String, includeInactive: Boolean): List<StockItemDto>
    suspend fun searchItems(restaurantId: String, keyword: String): List<StockItemDto>
    suspend fun saveItem(restaurantId: String, itemId: String?, request: StockItemRequestDto): StockItemDto
    suspend fun deactivateItem(restaurantId: String, itemId: String)
    /** Deletes the item for good: what's left in stock is taken out as a correction, its history stays. 409 while a recipe or an open count uses it. */
    suspend fun deleteItem(restaurantId: String, itemId: String): Unit = throw UnsupportedOperationException("Deleting items is unavailable")
    /** The item's photo: PNG, JPEG or WebP, up to 5 MB. Answers the item with its new photo. */
    suspend fun uploadItemPhoto(restaurantId: String, itemId: String, bytes: ByteArray, fileName: String, contentType: String): StockItemDto =
        throw UnsupportedOperationException("Item photos are unavailable")
    suspend fun removeItemPhoto(restaurantId: String, itemId: String): StockItemDto =
        throw UnsupportedOperationException("Item photos are unavailable")

    /** The place's own picture: PNG, JPEG or WebP, up to 5 MB. Answers the place with its new picture. */
    suspend fun uploadLocationPhoto(restaurantId: String, locationId: String, bytes: ByteArray, fileName: String, contentType: String): StockLocationDto =
        throw UnsupportedOperationException("Place pictures are unavailable")
    suspend fun removeLocationPhoto(restaurantId: String, locationId: String): StockLocationDto =
        throw UnsupportedOperationException("Place pictures are unavailable")

    suspend fun locations(restaurantId: String, includeInactive: Boolean): List<StockLocationDto>
    suspend fun saveLocation(restaurantId: String, locationId: String?, request: StockLocationRequestDto): StockLocationDto
    suspend fun deactivateLocation(restaurantId: String, locationId: String)

    suspend fun levels(restaurantId: String, locationId: String?, itemId: String?): List<StockLevelDto>
    suspend fun lowStock(restaurantId: String): List<StockLevelDto>
    suspend fun saleSources(restaurantId: String): List<SaleStockSourceDto> = emptyList()
    suspend fun setSaleSource(restaurantId: String, branchId: String, itemId: String, locationId: String): SaleStockSourceDto =
        throw UnsupportedOperationException("Sale stock source configuration is unavailable")
    suspend fun removeSaleSource(restaurantId: String, branchId: String, itemId: String) {
        throw UnsupportedOperationException("Sale stock source configuration is unavailable")
    }

    suspend fun move(restaurantId: String, kind: StockMoveKind, request: StockMoveRequestDto, requestKey: String)
    /** What went in and out per day over the last [days] days, summed by the server. */
    suspend fun activity(restaurantId: String, days: Int): StockActivityDto = throw UnsupportedOperationException("Stock activity is unavailable")
    /** The kinds of items ([ITEM_KINDS]) or places ([PLACE_KINDS]), with how many each has. */
    suspend fun kinds(restaurantId: String, scope: String): List<StockKindDto> = defaultKinds(scope)
    suspend fun createKind(restaurantId: String, scope: String, name: String): StockKindDto = throw UnsupportedOperationException("Adding kinds is unavailable")
    /** Removes a kind ([StockKindDto.code] for a built-in one, its id for an own one); what it held goes to Uncategorized. */
    suspend fun deleteKind(restaurantId: String, scope: String, ref: String): Unit = throw UnsupportedOperationException("Removing kinds is unavailable")
    /** Deletes a place: its stock moves to Uncategorized ([keepStock]) or is taken out. */
    suspend fun deleteLocation(restaurantId: String, locationId: String, keepStock: Boolean): Unit =
        throw UnsupportedOperationException("Deleting places is unavailable")
    /** Admin Hub → Settings → Inventory: stock worth this much or more counts as valuable (100 by default). */
    suspend fun valuableStockFrom(restaurantId: String): Double = DEFAULT_VALUABLE_STOCK_FROM
    suspend fun countSchedule(restaurantId: String): InventoryCountScheduleDto = InventoryCountScheduleDto()
    suspend fun movements(restaurantId: String, type: String?, itemId: String?, page: Int, size: Int): List<StockMovementDto>
    /** The same, also for one day ([day] as YYYY-MM-DD, in the restaurant's time zone) and one person. */
    suspend fun movements(restaurantId: String, type: String?, itemId: String?, page: Int, size: Int, day: String?, personId: String?): List<StockMovementDto> =
        movements(restaurantId, type, itemId, page, size)
    /** Who changed stock or items (for one item when [itemId] is set). */
    suspend fun movementPeople(restaurantId: String, itemId: String?): List<StockPersonDto> = emptyList()
    /** What happened to items themselves, newest first (at most 500), with History's filters. */
    suspend fun itemEvents(restaurantId: String, itemId: String?, personId: String?, day: String?): List<StockItemEventDto> = emptyList()

    // Suppliers (Inventory workspace → Suppliers): kept by the server since 2026-10-30.
    suspend fun suppliers(restaurantId: String): List<SupplierDto> = throw UnsupportedOperationException("Suppliers are unavailable")
    suspend fun createSupplier(restaurantId: String, request: SupplierRequestDto): SupplierDto = throw UnsupportedOperationException("Suppliers are unavailable")
    suspend fun updateSupplier(restaurantId: String, supplierId: String, request: SupplierRequestDto): SupplierDto =
        throw UnsupportedOperationException("Suppliers are unavailable")
    suspend fun saveSupplierPlace(restaurantId: String, supplierId: String, placeId: String?, request: SupplierPlaceRequestDto): SupplierDto =
        throw UnsupportedOperationException("Suppliers are unavailable")
    suspend fun removeSupplierPlace(restaurantId: String, supplierId: String, placeId: String): SupplierDto =
        throw UnsupportedOperationException("Suppliers are unavailable")
    suspend fun saveSupplierItem(restaurantId: String, supplierId: String, itemId: String, request: SupplierItemRequestDto): SupplierDto =
        throw UnsupportedOperationException("Suppliers are unavailable")
    suspend fun removeSupplierItem(restaurantId: String, supplierId: String, itemId: String): SupplierDto =
        throw UnsupportedOperationException("Suppliers are unavailable")

    suspend fun counts(restaurantId: String, status: String?, page: Int, size: Int): StockCountPageDto
    suspend fun count(restaurantId: String, countId: String): StockCountDto
    suspend fun createCount(restaurantId: String, request: StockCountCreateRequestDto): StockCountDto
    suspend fun countLine(restaurantId: String, countId: String, itemId: String, request: StockCountLineRequestDto): StockCountDto
    suspend fun removeCountLine(restaurantId: String, countId: String, lineId: String): StockCountDto
    /** start, complete, approve or cancel. */
    suspend fun countStep(restaurantId: String, countId: String, step: String): StockCountDto
}

class InventoryApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : InventoryRepository {

    private fun url(restaurantId: String, path: String) =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/inventory$path"
    private fun id(value: String) = value.encodeURLPathPart()

    override fun observeChanges(restaurantId: String): Flow<Unit> = channelFlow {
        val endpoint = url(restaurantId, "/events")
        while (currentCoroutineContext().isActive) {
            try {
                client.sse(urlString = endpoint, request = {
                    timeout {
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        socketTimeoutMillis = 60_000L
                    }
                }, reconnectionTime = 3.seconds) {
                    incoming.collect { event ->
                        if (event.event == "connected" || event.event == "inventory-changed") send(Unit)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The authenticated client refreshes expired credentials during reconnect.
            }
            delay(3.seconds)
        }
    }

    override suspend fun items(restaurantId: String, includeInactive: Boolean): List<StockItemDto> =
        client.get(url(restaurantId, if (includeInactive) "/items/all" else "/items")).body()

    override suspend fun searchItems(restaurantId: String, keyword: String): List<StockItemDto> =
        client.get(url(restaurantId, "/items/search")) { parameter("keyword", keyword) }.body()

    override suspend fun saveItem(restaurantId: String, itemId: String?, request: StockItemRequestDto): StockItemDto =
        if (itemId == null) client.post(url(restaurantId, "/items")) { contentType(ContentType.Application.Json); setBody(request) }.body()
        else client.put(url(restaurantId, "/items/${id(itemId)}")) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun activity(restaurantId: String, days: Int): StockActivityDto =
        client.get(url(restaurantId, "/activity")) { parameter("days", days) }.body()

    override suspend fun valuableStockFrom(restaurantId: String): Double =
        client.get("${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/settings")
            .body<InventorySettingsDto>().valuableStockFrom ?: DEFAULT_VALUABLE_STOCK_FROM

    override suspend fun countSchedule(restaurantId: String): InventoryCountScheduleDto {
        return client.get(url(restaurantId, "/counts/schedule")).body()
    }

    override suspend fun deactivateItem(restaurantId: String, itemId: String) {
        client.delete(url(restaurantId, "/items/${id(itemId)}"))
    }

    override suspend fun deleteItem(restaurantId: String, itemId: String) {
        client.post(url(restaurantId, "/items/${id(itemId)}/delete"))
    }

    override suspend fun kinds(restaurantId: String, scope: String): List<StockKindDto> =
        client.get(url(restaurantId, "/kinds")) { parameter("for", scope) }.body()

    override suspend fun createKind(restaurantId: String, scope: String, name: String): StockKindDto =
        client.post(url(restaurantId, "/kinds")) {
            contentType(ContentType.Application.Json)
            setBody(StockKindRequestDto(scope, name))
        }.body()

    override suspend fun deleteKind(restaurantId: String, scope: String, ref: String) {
        client.delete(url(restaurantId, "/kinds/${id(ref)}")) { parameter("for", scope) }
    }

    override suspend fun deleteLocation(restaurantId: String, locationId: String, keepStock: Boolean) {
        client.post(url(restaurantId, "/locations/${id(locationId)}/delete")) {
            contentType(ContentType.Application.Json)
            setBody(StockPlaceDeleteRequestDto(keepStock))
        }
    }

    override suspend fun uploadItemPhoto(restaurantId: String, itemId: String, bytes: ByteArray, fileName: String, contentType: String): StockItemDto =
        client.put(url(restaurantId, "/items/${id(itemId)}/image")) {
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
                })
            }))
        }.body()

    override suspend fun removeItemPhoto(restaurantId: String, itemId: String): StockItemDto =
        client.delete(url(restaurantId, "/items/${id(itemId)}/image")).body()

    override suspend fun uploadLocationPhoto(restaurantId: String, locationId: String, bytes: ByteArray, fileName: String, contentType: String): StockLocationDto =
        client.put(url(restaurantId, "/locations/${id(locationId)}/image")) {
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
                })
            }))
        }.body()

    override suspend fun removeLocationPhoto(restaurantId: String, locationId: String): StockLocationDto =
        client.delete(url(restaurantId, "/locations/${id(locationId)}/image")).body()

    override suspend fun locations(restaurantId: String, includeInactive: Boolean): List<StockLocationDto> =
        client.get(url(restaurantId, if (includeInactive) "/locations/all" else "/locations")).body()

    override suspend fun saveLocation(restaurantId: String, locationId: String?, request: StockLocationRequestDto): StockLocationDto =
        if (locationId == null) client.post(url(restaurantId, "/locations")) { contentType(ContentType.Application.Json); setBody(request) }.body()
        else client.put(url(restaurantId, "/locations/${id(locationId)}")) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun deactivateLocation(restaurantId: String, locationId: String) {
        client.delete(url(restaurantId, "/locations/${id(locationId)}"))
    }

    override suspend fun levels(restaurantId: String, locationId: String?, itemId: String?): List<StockLevelDto> =
        client.get(url(restaurantId, "/levels")) {
            locationId?.let { parameter("locationId", it) }
            itemId?.let { parameter("itemId", it) }
        }.body()

    override suspend fun lowStock(restaurantId: String): List<StockLevelDto> = client.get(url(restaurantId, "/levels/low-stock")).body()

    override suspend fun saleSources(restaurantId: String): List<SaleStockSourceDto> =
        client.get(url(restaurantId, "/sale-sources")).body()

    override suspend fun setSaleSource(restaurantId: String, branchId: String, itemId: String, locationId: String): SaleStockSourceDto =
        client.put(url(restaurantId, "/sale-sources/${id(branchId)}/${id(itemId)}")) {
            contentType(ContentType.Application.Json)
            setBody(SaleStockSourceRequestDto(locationId))
        }.body()

    override suspend fun removeSaleSource(restaurantId: String, branchId: String, itemId: String) {
        client.delete(url(restaurantId, "/sale-sources/${id(branchId)}/${id(itemId)}"))
    }

    override suspend fun move(restaurantId: String, kind: StockMoveKind, request: StockMoveRequestDto, requestKey: String) {
        client.post(url(restaurantId, "/${kind.path}")) {
            headers.append("Idempotency-Key", requestKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun movements(restaurantId: String, type: String?, itemId: String?, page: Int, size: Int): List<StockMovementDto> =
        movements(restaurantId, type, itemId, page, size, null, null)

    override suspend fun movements(restaurantId: String, type: String?, itemId: String?, page: Int, size: Int, day: String?,
                                   personId: String?): List<StockMovementDto> =
        client.get(url(restaurantId, "/movements")) {
            type?.let { parameter("type", it) }
            itemId?.let { parameter("itemId", it) }
            day?.let { parameter("day", it) }
            personId?.let { parameter("createdBy", it) }
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun movementPeople(restaurantId: String, itemId: String?): List<StockPersonDto> =
        client.get(url(restaurantId, "/movements/people")) { itemId?.let { parameter("itemId", it) } }.body()

    override suspend fun itemEvents(restaurantId: String, itemId: String?, personId: String?, day: String?): List<StockItemEventDto> =
        client.get(url(restaurantId, "/item-events")) {
            itemId?.let { parameter("itemId", it) }
            personId?.let { parameter("createdBy", it) }
            day?.let { parameter("day", it) }
        }.body()

    private fun suppliersUrl(restaurantId: String, path: String = "") =
        "${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/suppliers$path"

    override suspend fun suppliers(restaurantId: String): List<SupplierDto> = client.get(suppliersUrl(restaurantId)).body()

    override suspend fun createSupplier(restaurantId: String, request: SupplierRequestDto): SupplierDto = client.post(suppliersUrl(restaurantId)) {
        contentType(ContentType.Application.Json); setBody(request)
    }.body()

    override suspend fun updateSupplier(restaurantId: String, supplierId: String, request: SupplierRequestDto): SupplierDto =
        client.put(suppliersUrl(restaurantId, "/${id(supplierId)}")) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun saveSupplierPlace(restaurantId: String, supplierId: String, placeId: String?, request: SupplierPlaceRequestDto): SupplierDto =
        if (placeId == null) client.post(suppliersUrl(restaurantId, "/${id(supplierId)}/places")) { contentType(ContentType.Application.Json); setBody(request) }.body()
        else client.put(suppliersUrl(restaurantId, "/${id(supplierId)}/places/${id(placeId)}")) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun removeSupplierPlace(restaurantId: String, supplierId: String, placeId: String): SupplierDto =
        client.delete(suppliersUrl(restaurantId, "/${id(supplierId)}/places/${id(placeId)}")).body()

    override suspend fun saveSupplierItem(restaurantId: String, supplierId: String, itemId: String, request: SupplierItemRequestDto): SupplierDto =
        client.put(suppliersUrl(restaurantId, "/${id(supplierId)}/items/${id(itemId)}")) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun removeSupplierItem(restaurantId: String, supplierId: String, itemId: String): SupplierDto =
        client.delete(suppliersUrl(restaurantId, "/${id(supplierId)}/items/${id(itemId)}")).body()

    override suspend fun counts(restaurantId: String, status: String?, page: Int, size: Int): StockCountPageDto =
        client.get(url(restaurantId, "/counts")) {
            status?.let { parameter("status", it) }
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun count(restaurantId: String, countId: String): StockCountDto = client.get(url(restaurantId, "/counts/${id(countId)}")).body()

    override suspend fun createCount(restaurantId: String, request: StockCountCreateRequestDto): StockCountDto =
        client.post(url(restaurantId, "/counts")) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun countLine(restaurantId: String, countId: String, itemId: String, request: StockCountLineRequestDto): StockCountDto =
        client.put(url(restaurantId, "/counts/${id(countId)}/lines/${id(itemId)}")) {
            contentType(ContentType.Application.Json); setBody(request)
        }.body()

    override suspend fun removeCountLine(restaurantId: String, countId: String, lineId: String): StockCountDto =
        client.delete(url(restaurantId, "/counts/${id(countId)}/lines/${id(lineId)}")).body()

    override suspend fun countStep(restaurantId: String, countId: String, step: String): StockCountDto {
        require(step in COUNT_STEPS) { "Unknown count step" }
        return client.post(url(restaurantId, "/counts/${id(countId)}/$step")) { contentType(ContentType.Application.Json); setBody(emptyMap<String, String>()) }.body()
    }

    companion object {
        val COUNT_STEPS = setOf("start", "complete", "approve", "cancel")
    }
}

// ---- Suppliers ----

/** A supplier with their places and the items you buy from them (their code and price per unit of the item). */
@Serializable
data class SupplierDto(
    val id: String,
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val places: List<SupplierPlaceDto> = emptyList(),
    val items: List<SupplierItemDto> = emptyList()
)

@Serializable
data class SupplierPlaceDto(val id: String, val name: String, val address: String? = null, val phone: String? = null, val deliveryDays: List<Int> = emptyList())

@Serializable
data class SupplierItemDto(val itemId: String, val code: String? = null, val price: OrderDecimal? = null)

/** A supplier's details; a new supplier sends the id the app already shows. [active] is always sent. */
@Serializable
data class SupplierRequestDto(
    val id: String? = null,
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean
)

@Serializable
data class SupplierPlaceRequestDto(val id: String? = null, val name: String, val address: String? = null, val phone: String? = null, val deliveryDays: List<Int>)

@Serializable
data class SupplierItemRequestDto(val code: String? = null, val price: OrderDecimal? = null)
