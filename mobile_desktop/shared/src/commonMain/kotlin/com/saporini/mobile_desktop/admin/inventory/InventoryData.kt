package com.saporini.mobile_desktop.admin.inventory

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

// Admin Hub → Inventory: the server's /restaurants/{r}/inventory API. Quantities and costs are exact decimal text.

val INVENTORY_UNITS = listOf("EACH", "GRAM", "KILOGRAM", "MILLILITER", "LITER", "OUNCE", "POUND", "CUP", "TABLESPOON",
    "TEASPOON", "PORTION", "CASE", "BOTTLE", "PACK", "TRAY")
val INVENTORY_ITEM_TYPES = listOf("INGREDIENT", "PREPARED_COMPONENT", "FINISHED_GOOD", "BEVERAGE", "ALCOHOL", "PACKAGING", "SUPPLY")
val INVENTORY_LOCATION_TYPES = listOf("BRANCH_STORAGE", "KITCHEN", "BAR", "WALK_IN", "FREEZER", "DRY_STORAGE", "CENTRAL_WAREHOUSE", "TRANSIT")
val INVENTORY_MOVEMENT_TYPES = listOf("PURCHASE", "RECEIPT", "COUNT_ADJUSTMENT", "WASTE", "TRANSFER_IN", "TRANSFER_OUT",
    "SALE_CONSUMPTION", "RETURN", "VOID", "MANUAL_ADJUSTMENT", "PREP_PRODUCTION")

@Serializable
data class StockItemDto(
    val id: String,
    val code: String? = null,
    val name: String,
    val description: String? = null,
    val itemType: String,
    val baseUnit: String,
    val barcode: String? = null,
    val supplierName: String? = null,
    val supplierSku: String? = null,
    val costPerUnit: OrderDecimal = OrderDecimal.ZERO,
    val reorderPoint: OrderDecimal? = null,
    val parLevel: OrderDecimal? = null,
    val trackInventory: Boolean = true,
    val active: Boolean = true,
    val storageNotes: String? = null,
    val updatedByUserName: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class StockItemRequestDto(
    val code: String? = null,
    val name: String,
    val description: String? = null,
    val itemType: String,
    val baseUnit: String,
    val barcode: String? = null,
    val supplierName: String? = null,
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
    val notes: String? = null,
    val active: Boolean = true
)

@Serializable
data class StockLocationRequestDto(
    val branchId: String? = null,
    val code: String? = null,
    val name: String,
    val locationType: String,
    val notes: String? = null,
    val active: Boolean
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

interface InventoryRepository {
    suspend fun items(restaurantId: String, includeInactive: Boolean): List<StockItemDto>
    suspend fun searchItems(restaurantId: String, keyword: String): List<StockItemDto>
    suspend fun saveItem(restaurantId: String, itemId: String?, request: StockItemRequestDto): StockItemDto
    suspend fun deactivateItem(restaurantId: String, itemId: String)

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
    suspend fun movements(restaurantId: String, type: String?, itemId: String?, page: Int, size: Int): List<StockMovementDto>

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

    override suspend fun items(restaurantId: String, includeInactive: Boolean): List<StockItemDto> =
        client.get(url(restaurantId, if (includeInactive) "/items/all" else "/items")).body()

    override suspend fun searchItems(restaurantId: String, keyword: String): List<StockItemDto> =
        client.get(url(restaurantId, "/items/search")) { parameter("keyword", keyword) }.body()

    override suspend fun saveItem(restaurantId: String, itemId: String?, request: StockItemRequestDto): StockItemDto =
        if (itemId == null) client.post(url(restaurantId, "/items")) { contentType(ContentType.Application.Json); setBody(request) }.body()
        else client.put(url(restaurantId, "/items/${id(itemId)}")) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun deactivateItem(restaurantId: String, itemId: String) {
        client.delete(url(restaurantId, "/items/${id(itemId)}"))
    }

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
        client.get(url(restaurantId, "/movements")) {
            type?.let { parameter("type", it) }
            itemId?.let { parameter("itemId", it) }
            parameter("page", page)
            parameter("size", size)
        }.body()

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
