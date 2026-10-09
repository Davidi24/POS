package com.saporini.mobile_desktop.admin.inventory

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import kotlinx.serialization.Serializable

// The server's limits for stock, checked as people type so the form can point at the field.

const val MAX_STOCK_NAME = 150
const val MAX_STOCK_CODE = 80
const val MAX_SUPPLIER_NAME = 150
const val MAX_SUPPLIER_SKU = 100
const val MAX_STOCK_TEXT = 2000
const val QUANTITY_DIGITS = 9
const val QUANTITY_DECIMALS = 3
const val COST_DIGITS = 12
const val COST_DECIMALS = 4

private val NUMBER_TEXT = Regex("^(-?)([0-9]+)(?:\\.([0-9]*))?$")

/** A number typed by someone: [value] when it is usable, otherwise [problem]. */
data class TypedNumber(val value: OrderDecimal? = null, val problem: String? = null)

/**
 * Reads [text] as an exact decimal ("1,5" and "1.5" both work) within [digits] whole digits and [decimals] decimals.
 * Empty text is null when [required] is false.
 */
fun typedNumber(
    text: String,
    digits: Int = QUANTITY_DIGITS,
    decimals: Int = QUANTITY_DECIMALS,
    required: Boolean = true,
    allowZero: Boolean = false,
    allowNegative: Boolean = false
): TypedNumber {
    val clean = text.trim().replace(" ", "").replace(',', '.').let {
        // ".5" and "-.5" are read as 0.5 and -0.5.
        when {
            it.startsWith("-.") -> "-0" + it.drop(1)
            it.startsWith(".") -> "0$it"
            else -> it
        }
    }
    if (clean.isEmpty()) return if (required) TypedNumber(problem = "Enter a number") else TypedNumber()
    val match = NUMBER_TEXT.matchEntire(clean) ?: return TypedNumber(problem = "This isn't a number")
    val negative = match.groupValues[1] == "-"
    val whole = match.groupValues[2].trimStart('0').ifEmpty { "0" }
    val fraction = match.groupValues[3].trimEnd('0')
    val zero = whole == "0" && fraction.isEmpty()
    return when {
        negative && !zero && !allowNegative -> TypedNumber(problem = "Can't be below zero")
        zero && !allowZero -> TypedNumber(problem = if (allowNegative) "Can't be zero" else "Must be more than zero")
        whole.length > digits -> TypedNumber(problem = "Too large")
        fraction.length > decimals -> TypedNumber(problem = "At most $decimals decimals")
        else -> TypedNumber(OrderDecimal((if (negative && !zero) "-" else "") + whole + (if (fraction.isEmpty()) "" else ".$fraction")))
    }
}

/** Compares two decimals exactly (no floating point), as -1, 0 or 1. */
fun compareDecimals(a: OrderDecimal, b: OrderDecimal): Int {
    fun parts(d: OrderDecimal): Triple<Boolean, String, String> {
        val text = d.value
        val negative = text.startsWith("-")
        val body = text.removePrefix("-")
        val whole = body.substringBefore('.').trimStart('0')
        val fraction = body.substringAfter('.', "").trimEnd('0')
        val zero = whole.isEmpty() && fraction.isEmpty()
        return Triple(negative && !zero, whole, fraction)
    }
    val (an, aw, af) = parts(a)
    val (bn, bw, bf) = parts(b)
    if (an != bn) return if (an) -1 else 1
    val width = maxOf(af.length, bf.length)
    val magnitude = compareValuesBy(aw to af.padEnd(width, '0'), bw to bf.padEnd(width, '0'), { it.first.length }, { it.first }, { it.second })
    return if (an) -magnitude else magnitude
}

enum class ItemField { NAME, CODE, BARCODE, SUPPLIER, SUPPLIER_SKU, COST, REORDER, PAR, TEXT }

data class StockItemDraft(
    val id: String? = null,
    val name: String = "",
    val code: String = "",
    val description: String = "",
    val itemType: String = "INGREDIENT",
    val baseUnit: String = "EACH",
    val barcode: String = "",
    val supplierName: String = "",
    val supplierSku: String = "",
    val costText: String = "",
    val reorderText: String = "",
    val parText: String = "",
    val trackInventory: Boolean = true,
    val active: Boolean = true,
    val storageNotes: String = ""
)

fun itemDraftOf(item: StockItemDto) = StockItemDraft(
    id = item.id, name = item.name, code = item.code.orEmpty(), description = item.description.orEmpty(), itemType = item.itemType,
    baseUnit = item.baseUnit, barcode = item.barcode.orEmpty(), supplierName = item.supplierName.orEmpty(),
    supplierSku = item.supplierSku.orEmpty(), costText = item.costPerUnit.value, reorderText = item.reorderPoint?.value.orEmpty(),
    parText = item.parLevel?.value.orEmpty(), trackInventory = item.trackInventory, active = item.active,
    storageNotes = item.storageNotes.orEmpty()
)

fun itemProblems(draft: StockItemDraft): Map<ItemField, String> = buildMap {
    val name = draft.name.trim()
    when {
        name.isEmpty() -> put(ItemField.NAME, "Enter a name")
        name.length > MAX_STOCK_NAME -> put(ItemField.NAME, "At most $MAX_STOCK_NAME characters")
    }
    if (draft.code.trim().length > MAX_STOCK_CODE) put(ItemField.CODE, "At most $MAX_STOCK_CODE characters")
    if (draft.barcode.trim().length > MAX_STOCK_CODE) put(ItemField.BARCODE, "At most $MAX_STOCK_CODE characters")
    if (draft.supplierName.trim().length > MAX_SUPPLIER_NAME) put(ItemField.SUPPLIER, "At most $MAX_SUPPLIER_NAME characters")
    if (draft.supplierSku.trim().length > MAX_SUPPLIER_SKU) put(ItemField.SUPPLIER_SKU, "At most $MAX_SUPPLIER_SKU characters")
    typedNumber(draft.costText, COST_DIGITS, COST_DECIMALS, allowZero = true).problem?.let { put(ItemField.COST, it) }
    val reorder = typedNumber(draft.reorderText, required = false, allowZero = true)
    val par = typedNumber(draft.parText, required = false, allowZero = true)
    reorder.problem?.let { put(ItemField.REORDER, it) }
    par.problem?.let { put(ItemField.PAR, it) }
    if (reorder.value != null && par.value != null && compareDecimals(par.value, reorder.value) < 0) {
        put(ItemField.PAR, "The full level can't be below the reorder point")
    }
    if (draft.itemType !in INVENTORY_ITEM_TYPES || draft.baseUnit !in INVENTORY_UNITS) put(ItemField.TEXT, "Choose a type and unit")
    if (draft.description.trim().length > MAX_STOCK_TEXT || draft.storageNotes.trim().length > MAX_STOCK_TEXT) {
        put(ItemField.TEXT, "Notes can be at most $MAX_STOCK_TEXT characters")
    }
}

fun itemRequest(draft: StockItemDraft) = StockItemRequestDto(
    code = draft.code.trim().ifEmpty { null },
    name = draft.name.trim(),
    description = draft.description.trim().ifEmpty { null },
    itemType = draft.itemType,
    baseUnit = draft.baseUnit,
    barcode = draft.barcode.trim().ifEmpty { null },
    supplierName = draft.supplierName.trim().ifEmpty { null },
    supplierSku = draft.supplierSku.trim().ifEmpty { null },
    costPerUnit = typedNumber(draft.costText, COST_DIGITS, COST_DECIMALS, allowZero = true).value ?: OrderDecimal("0"),
    reorderPoint = typedNumber(draft.reorderText, required = false, allowZero = true).value,
    parLevel = typedNumber(draft.parText, required = false, allowZero = true).value,
    trackInventory = draft.trackInventory,
    active = draft.active,
    storageNotes = draft.storageNotes.trim().ifEmpty { null }
)

data class StockLocationDraft(
    val id: String? = null,
    val name: String = "",
    val code: String = "",
    val locationType: String = "KITCHEN",
    val notes: String = "",
    val branchId: String? = null,
    val active: Boolean = true
)

fun locationProblem(draft: StockLocationDraft): String? {
    val name = draft.name.trim()
    return when {
        name.isEmpty() -> "Enter a name for the place"
        name.length > MAX_STOCK_NAME -> "The name can be at most $MAX_STOCK_NAME characters"
        draft.code.trim().length > MAX_STOCK_CODE -> "The code can be at most $MAX_STOCK_CODE characters"
        draft.locationType !in INVENTORY_LOCATION_TYPES -> "Choose what kind of place this is"
        draft.notes.trim().length > MAX_STOCK_TEXT -> "Notes can be at most $MAX_STOCK_TEXT characters"
        else -> null
    }
}

/** A stock change being written. */
@Serializable
data class StockMoveDraft(
    val kind: StockMoveKind = StockMoveKind.RECEIVE,
    val itemId: String? = null,
    val locationId: String? = null,
    val toLocationId: String? = null,
    val quantityText: String = "",
    val unitCostText: String = "",
    val reason: String = ""
)

/** The exact account-scoped operation needed to safely replay a request after process death. */
@Serializable
data class PendingInventoryMove(
    val userId: String,
    val restaurantId: String,
    val requestKey: String,
    val draft: StockMoveDraft
)

/** The problem with [draft], or null when it can be sent. Waste and corrections always need a reason. */
fun moveProblem(draft: StockMoveDraft): String? {
    if (draft.itemId == null) return "Choose an item"
    if (draft.locationId == null) return if (draft.kind == StockMoveKind.TRANSFER) "Choose where it comes from" else "Choose a place"
    if (draft.kind == StockMoveKind.TRANSFER) {
        if (draft.toLocationId == null) return "Choose where it goes"
        if (draft.toLocationId == draft.locationId) return "Choose two different places"
    }
    val quantity = typedNumber(draft.quantityText, allowNegative = draft.kind == StockMoveKind.ADJUST)
    quantity.problem?.let { return "Quantity: $it" }
    if (draft.kind == StockMoveKind.RECEIVE) {
        typedNumber(draft.unitCostText, COST_DIGITS, COST_DECIMALS, required = false, allowZero = true).problem?.let { return "Cost: $it" }
    }
    val reason = draft.reason.trim()
    if ((draft.kind == StockMoveKind.WASTE || draft.kind == StockMoveKind.ADJUST) && reason.isEmpty()) return "Say why"
    if (reason.length > MAX_STOCK_TEXT) return "The reason can be at most $MAX_STOCK_TEXT characters"
    return null
}

fun moveRequest(draft: StockMoveDraft): StockMoveRequestDto {
    val quantity = typedNumber(draft.quantityText, allowNegative = draft.kind == StockMoveKind.ADJUST).value
    val transfer = draft.kind == StockMoveKind.TRANSFER
    return StockMoveRequestDto(
        locationId = draft.locationId.takeUnless { transfer },
        fromLocationId = draft.locationId.takeIf { transfer },
        toLocationId = draft.toLocationId.takeIf { transfer },
        inventoryItemId = draft.itemId ?: "",
        quantity = quantity.takeUnless { draft.kind == StockMoveKind.ADJUST },
        quantityDelta = quantity.takeIf { draft.kind == StockMoveKind.ADJUST },
        unitCostOverride = if (draft.kind == StockMoveKind.RECEIVE) typedNumber(draft.unitCostText, COST_DIGITS, COST_DECIMALS, required = false, allowZero = true).value else null,
        reason = draft.reason.trim().ifEmpty { null }
    )
}

/** A supplier as the restaurant knows it: the name written on its stock items. */
data class SupplierSummary(val name: String, val items: List<StockItemDto>, val lowStockItems: Int)

/** Suppliers from the active items' supplier names (spelling differences in case and spaces count as one). */
fun suppliersOf(items: List<StockItemDto>, lowStockItemIds: Set<String>): List<SupplierSummary> =
    items.filter { it.active && !it.supplierName.isNullOrBlank() }
        .groupBy { it.supplierName!!.trim().replace(Regex("\\s+"), " ").lowercase() }
        .map { (_, group) ->
            SupplierSummary(
                name = group.first().supplierName!!.trim().replace(Regex("\\s+"), " "),
                items = group.sortedBy { it.name.lowercase() },
                lowStockItems = group.count { it.id in lowStockItemIds }
            )
        }
        .sortedBy { it.name.lowercase() }

/** The next steps a count can take, in the order a screen offers them. */
fun countSteps(status: String): List<String> = when (status) {
    "DRAFT" -> listOf("start", "cancel")
    "IN_PROGRESS" -> listOf("complete", "cancel")
    "COMPLETED" -> listOf("approve", "cancel")
    else -> emptyList()
}
