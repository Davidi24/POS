@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.saporini.mobile_desktop.admin.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saporini.mobile_desktop.core.format.centsOrNull
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.core.format.unitShort
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.roundToLong
import kotlin.uuid.Uuid

// Suppliers as the restaurant works with them:
//   1. a supplier (who you buy from),
//   2. their places (the warehouses or branches you order from),
//   3. your existing stock items you can buy from them (linked, never copied: the same "Eggs" can come from several
//      suppliers, each with their own code and price),
//   4. orders: from one supplier place, delivered into one of your stock places; receiving a delivery adds it to Stock.
//
// Kept by the server since 2026-10-30 (`/restaurants/{r}/suppliers`): suppliers, their places and the items you buy
// from them. This object is the app's copy: it's filled from the server ([fill]) and every change is shown at once and
// sent behind it ([send]); if the server refuses, [problem] says why and the copy is loaded again.
// MOCK still: orders and their price lists (catalog) live in memory only (gone after a restart), and so do a supplier's
// picture and banner. Receiving a delivery is real (stock receipts through InventoryScreenModel.receiveDelivery). The
// server should get purchase_orders(+lines) and catalog tables, and picture uploads, the same way.

internal data class Supplier(
    val id: String,
    val name: String,
    val contactName: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    val active: Boolean = true,
    // MOCK: the supplier's own picture and banner (bytes kept in memory); without them the app shows its warehouse
    // picture and a soft green banner.
    val photo: ByteArray? = null,
    val cover: ByteArray? = null,
    // Where they are, for directions in Google Maps.
    val address: String = ""
)

/** Google Maps directions to [address] (opens the app or the website). */
internal fun mapsDirections(address: String): String =
    "https://www.google.com/maps/dir/?api=1&destination=" + address.trim().encodeURLParameter()

/** A warehouse or branch of a supplier that you order from. [deliveryDays] are ISO days (1 = Monday). */
internal data class SupplierPlace(
    val id: String,
    val supplierId: String,
    val name: String,
    val address: String = "",
    val phone: String = "",
    val deliveryDays: Set<Int> = emptySet()
)

/** One of your stock items that you can buy from a supplier, with their code and price per unit of the item. */
internal data class SupplierItem(val supplierId: String, val itemId: String, val code: String = "", val priceCents: Long? = null)

/**
 * An order goes through four steps: 1 ordered, 2 the delivery is received, 3 it's counted and put in place,
 * 4 the count is confirmed and added to stock. [step] is how many of them are done (cancelled: 0).
 */
internal enum class PurchaseStatus(val label: String, val step: Int) {
    ORDERED("Ordered", 1), ARRIVED("Arrived", 2), COUNTED("Counted", 3), RECEIVED("In stock", 4),
    // Part of it is in stock and the rest is still to come (an order can arrive in several deliveries).
    PARTLY_RECEIVED("Partly in stock", 1), CANCELLED("Cancelled", 0);

    /** Not finished yet: still to arrive, or being counted and confirmed. */
    val open get() = this == ORDERED || this == ARRIVED || this == COUNTED || this == PARTLY_RECEIVED
    val inProgress get() = this == ARRIVED || this == COUNTED || this == PARTLY_RECEIVED
}

/** The four steps' names, in order. */
internal val PurchaseSteps = listOf("Ordered", "Order received", "Counted & put in place", "Confirmed in stock")

/**
 * A line: what was ordered, what arrived with the latest delivery and what was counted of it (null until then), what's
 * already in stock, and the stock place it goes to (each item its own; null = the order's place).
 */
internal data class PurchaseLine(
    val itemId: String, val quantity: Double, val priceCents: Long?, val received: Double = 0.0, val counted: Double? = null,
    val arrived: Double? = null, val placeId: String? = null
) {
    val cents get() = priceCents?.let { (it * quantity).roundToLong() }
    /** Still to come: ordered minus what's already in stock. */
    val left get() = (quantity - received).coerceAtLeast(0.0)
}

internal data class PurchaseOrder(
    val id: String,
    val number: String,
    val supplierId: String,
    val placeId: String?,
    val intoLocationId: String?,
    val orderedOn: LocalDate,
    val expectedOn: LocalDate?,
    val lines: List<PurchaseLine>,
    val status: PurchaseStatus = PurchaseStatus.ORDERED,
    val note: String = "",
    // When it went into stock (step 4), and when the delivery came (step 2).
    val receivedOn: LocalDate? = null,
    val arrivedOn: LocalDate? = null
) {
    val totalCents get() = lines.sumOf { it.cents ?: 0 }
    /** The stock places its items go to. */
    val placeIds get() = lines.mapNotNull { it.placeId ?: intoLocationId }.distinct()
    /** Still to arrive and expected before today. */
    fun late(today: LocalDate) = status == PurchaseStatus.ORDERED && expectedOn != null && expectedOn < today
}

internal object SupplierBook {
    val suppliers = mutableStateListOf<Supplier>()
    val places = mutableStateListOf<SupplierPlace>()
    val links = mutableStateListOf<SupplierItem>()
    val orders = mutableStateListOf<PurchaseOrder>()
    /** Everything each supplier sells (their price lists), linked to your items or not. */
    val catalog = mutableStateListOf<CatalogProduct>()
    private var restaurantId: String? = null
    // Observable, so a screen can show its loading shapes until the suppliers are in.
    private var seeded by mutableStateOf(false)
    private var samples = false
    /** True once the restaurant's suppliers are in (the screen shows loading shapes until then). */
    val ready: Boolean get() = seeded
    /**
     * A supplier asked for from elsewhere (Stock → a line's ⋮ → "Go to <supplier>", user 2026-10-30): the Inventory
     * workspace switches to Suppliers, which opens them and clears this.
     */
    var openRequest by mutableStateOf<String?>(null)
    /** Suppliers already offered a sample banner (so one that was taken off doesn't come back). */
    val sampleCoverTried = mutableSetOf<String>()
    /** Why the server refused the last change (shown once by the screens, then cleared). */
    var problem by mutableStateOf<String?>(null)

    // Where changes go: the repository, the restaurant and the screen model's scope (set by InventoryScreenModel).
    private var server: Triple<InventoryRepository, String, CoroutineScope>? = null

    fun connect(repository: InventoryRepository, restaurant: String, scope: CoroutineScope) {
        server = Triple(repository, restaurant, scope)
    }

    /** The restaurant's suppliers as the server has them; pictures (kept in the app only) stay with their supplier. */
    fun fill(restaurant: String, list: List<SupplierDto>) {
        if (restaurant != restaurantId) reset(restaurant)
        val pictures = suppliers.associate { it.id to (it.photo to it.cover) }
        suppliers.clear(); places.clear(); links.clear()
        list.forEach { dto ->
            suppliers += Supplier(dto.id, dto.name, dto.contactName.orEmpty(), dto.phone.orEmpty(), dto.email.orEmpty(), dto.notes.orEmpty(), dto.active,
                pictures[dto.id]?.first, pictures[dto.id]?.second, dto.address.orEmpty())
            dto.places.forEach { place -> places += SupplierPlace(place.id, dto.id, place.name, place.address.orEmpty(), place.phone.orEmpty(), place.deliveryDays.toSet()) }
            dto.items.forEach { item -> links += SupplierItem(dto.id, item.itemId, item.code.orEmpty(), item.price?.centsOrNull()) }
        }
        seeded = true
    }

    private fun reset(restaurant: String) {
        restaurantId = restaurant
        suppliers.clear(); places.clear(); links.clear(); orders.clear(); catalog.clear()
        seeded = false
        samples = false
        sampleCoverTried.clear()
    }

    /** Sends a change behind what's already shown; a refusal is told and the copy is loaded again. */
    private fun send(call: suspend (InventoryRepository, String) -> Unit) {
        val (repository, restaurant, scope) = server ?: return
        scope.launch {
            try {
                call(repository, restaurant)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = com.saporini.mobile_desktop.admin.adminMessage(e, write = true)
                runCatching { fill(restaurant, repository.suppliers(restaurant)) }
            }
        }
    }

    private fun request(supplier: Supplier, id: String? = null) = SupplierRequestDto(id, supplier.name.trim(), supplier.contactName.trim().ifEmpty { null },
        supplier.phone.trim().ifEmpty { null }, supplier.email.trim().ifEmpty { null }, supplier.address.trim().ifEmpty { null },
        supplier.notes.trim().ifEmpty { null }, supplier.active)

    private fun request(place: SupplierPlace, id: String? = null) = SupplierPlaceRequestDto(id, place.name.trim(), place.address.trim().ifEmpty { null },
        place.phone.trim().ifEmpty { null }, place.deliveryDays.sorted())

    private fun request(link: SupplierItem) = SupplierItemRequestDto(link.code.trim().ifEmpty { null }, link.priceCents?.let { OrderDecimal("${it / 100}.${(it % 100).toString().padStart(2, '0')}") })

    /**
     * Opens the book for a restaurant. Its suppliers come from the server (see [fill]); once its stock levels are known
     * too, sample orders and price lists (MOCK, so the screens can be seen with data).
     */
    fun open(restaurant: String?, items: List<StockItemDto>, locations: List<StockLocationDto> = emptyList(),
             levels: List<StockLevelDto> = emptyList(), today: LocalDate? = null) {
        if (restaurant == null) return
        if (restaurant != restaurantId) reset(restaurant)
        if (seeded && !samples && today != null && levels.isNotEmpty() && locations.isNotEmpty()) {
            samples = true
            seedSamples(items, locations.filter { it.active }, levels, today)
        }
    }

    // MOCK sample data: price lists, and orders in every state (on the way, late, partly here, received, cancelled) into
    // the stock place that already holds each item.
    private fun seedSamples(items: List<StockItemDto>, locations: List<StockLocationDto>, levels: List<StockLevelDto>, today: LocalDate) {
        if (locations.isEmpty()) return
        seedCatalogs(items)
        fun into(itemId: String) = levels.firstOrNull { it.inventoryItemId == itemId }?.locationId ?: locations.first().id
        fun amount(item: StockItemDto?): Double {
            val target = (item?.parLevel?.value ?: item?.reorderPoint?.value)?.toDoubleOrNull() ?: 10.0
            return kotlin.math.ceil(target.coerceAtLeast(1.0))
        }
        val plans = listOf(
            Triple(PurchaseStatus.ORDERED, -1, 1), Triple(PurchaseStatus.ORDERED, -3, -1), Triple(PurchaseStatus.ARRIVED, -2, 0),
            Triple(PurchaseStatus.ORDERED, 0, 3), Triple(PurchaseStatus.RECEIVED, -9, -7), Triple(PurchaseStatus.RECEIVED, -15, -13),
            Triple(PurchaseStatus.CANCELLED, -6, -4), Triple(PurchaseStatus.COUNTED, -1, 0), Triple(PurchaseStatus.PARTLY_RECEIVED, -5, -1)
        )
        val withItems = suppliers.filter { s -> links.any { it.supplierId == s.id } }
        plans.forEachIndexed { index, (status, orderedAgo, expectedIn) ->
            val supplier = withItems.getOrNull(index % maxOf(withItems.size, 1)) ?: return
            val supplierLinks = linksOf(supplier.id).take(3)
            if (supplierLinks.isEmpty()) return@forEachIndexed
            val lines = supplierLinks.mapIndexed { n, link ->
                val quantity = amount(items.firstOrNull { it.id == link.itemId })
                // Counted: one line came short, to show a difference.
                val counted = when (status) {
                    PurchaseStatus.COUNTED -> if (n == 0 && quantity > 1) quantity - 1 else quantity
                    else -> null
                }
                // Partly in stock: the first line came in full, the rest half.
                val received = when (status) {
                    PurchaseStatus.RECEIVED -> quantity
                    PurchaseStatus.PARTLY_RECEIVED -> if (n == 0) quantity else kotlin.math.floor(quantity / 2)
                    else -> 0.0
                }
                PurchaseLine(link.itemId, quantity, link.priceCents, received = received, counted = counted,
                    arrived = if (status == PurchaseStatus.ARRIVED || status == PurchaseStatus.COUNTED) quantity else null, placeId = into(link.itemId))
            }
            val placed = placeOrder(PurchaseOrder("", "", supplier.id, placesOf(supplier.id).firstOrNull()?.id, into(lines.first().itemId),
                today.plus(DatePeriod(days = orderedAgo)), today.plus(DatePeriod(days = expectedIn)), lines, status))
            if (status.step >= 2) replace(orders, { it.id == placed.id }) {
                it.copy(arrivedOn = it.expectedOn, receivedOn = it.expectedOn.takeIf { status == PurchaseStatus.RECEIVED })
            }
        }
    }

    // MOCK sample price lists: what each supplier links to (linked) plus a few more products of their kind you don't buy yet.
    private fun seedCatalogs(items: List<StockItemDto>) {
        val extras = mapOf(
            "seafood" to listOf("Mussels, live|ADR-2001|kg|5 kg net|6.40", "Squid, cleaned|ADR-2014|kg|2 kg box|11.90", "King prawns 16/20|ADR-2030|kg|1 kg bag|18.50",
                "Octopus, whole|ADR-2042|kg|per kg|14.20", "Salmon fillet, skin on|ADR-2055|kg|per kg|17.80"),
            "baker" to listOf("Croissants, butter|BAK-311|pcs|box of 24|0.65", "Ciabatta rolls|BAK-318|pcs|bag of 12|0.40", "Focaccia sheet|BAK-325|pcs|60 × 40 cm|7.50",
                "Baguette, par-baked|BAK-340|pcs|case of 30|0.55"),
            "beverage" to listOf("Orange juice 1 l|BAL-1402|bottle|case of 12|1.35", "Iced tea peach 330 ml|BAL-1417|can|tray of 24|0.62", "Energy drink 250 ml|BAL-1433|can|tray of 24|0.95",
                "Still water 500 ml|BAL-1450|bottle|pack of 24|0.22"),
            "wholesale" to listOf("Basmati rice|CW-5011|kg|5 kg bag|2.10", "Chickpeas, tinned|CW-5024|tin|case of 12|0.85", "Tomato passata 700 g|CW-5032|bottle|case of 12|1.05",
                "Spaghetti no. 5|CW-5040|kg|case of 12 × 1 kg|1.45", "Balsamic vinegar 500 ml|CW-5068|bottle|case of 6|3.90"),
            "pack" to listOf("Paper straws|ECO-801|pack|box of 250|4.20", "Kraft soup cup 500 ml|ECO-812|pcs|case of 500|0.11", "Wooden cutlery set|ECO-826|pcs|box of 250|0.09"),
            "farm" to listOf("Zucchini|FFT-1201|kg|per kg|1.20", "Eggplant|FFT-1208|kg|per kg|1.35", "Spinach, baby|FFT-1215|kg|1 kg crate|4.80", "Strawberries|FFT-1230|kg|2 kg tray|4.20"),
            "meat" to listOf("Lamb leg, bone-in|KM-410|kg|per kg|12.40", "Pork belly|KM-422|kg|per kg|7.90", "Veal escalope|KM-436|kg|per kg|16.30", "Chicken thighs, boneless|KM-448|kg|5 kg box|6.10"),
            "dairy" to listOf("Greek yogurt 1 kg|LD-221|tub|tub|3.40", "Feta cheese|LD-236|kg|2 kg tin|8.60", "Ricotta|LD-244|kg|1.5 kg tub|5.20", "Kefir 1 l|LD-252|bottle|case of 6|1.70"),
            "coffee" to listOf("Decaf espresso beans|MRT-05|kg|1 kg bag|21.00", "Single origin Ethiopia|MRT-09|kg|1 kg bag|26.50", "Cocoa powder|MRT-14|kg|1 kg bag|9.80"),
            "olive" to listOf("Olive oil 5 l tin|OGB-50|tin|5 l|42.00", "Kalamata olives|OGB-61|kg|2 kg tin|9.40", "Olive tapenade|OGB-73|jar|case of 6 × 190 g|3.20"),
            "vino" to listOf("Shiraz 750 ml|VSD-310|bottle|case of 6|7.20", "Rosé 750 ml|VSD-322|bottle|case of 6|6.80", "Raki Skrapar 700 ml|VSD-341|bottle|case of 6|9.50",
                "Limoncello 700 ml|VSD-355|bottle|case of 6|11.30")
        )
        val generic = listOf("Sample product A|GEN-01|pcs|box of 10|2.50", "Sample product B|GEN-02|kg|per kg|4.00")
        suppliers.forEach { supplier ->
            val key = supplier.name.lowercase().let { n -> extras.keys.firstOrNull { it in n } ?: when {
                "green" in n || "fresh" in n -> "farm"; "molino" in n -> "wholesale"; "caseificio" in n -> "dairy"; else -> null } }
            linksOf(supplier.id).forEach { link ->
                val item = items.firstOrNull { it.id == link.itemId } ?: return@forEach
                catalog += CatalogProduct(newId(), supplier.id, item.name, link.code, unitShort(item.baseUnit), "", link.priceCents, linkedItemId = item.id)
            }
            (key?.let { extras[it] } ?: generic).forEach { line ->
                val (name, code, unit, pack, price) = line.split('|')
                catalog += CatalogProduct(newId(), supplier.id, name, code, unit, pack, priceToCents(price))
            }
        }
    }

    fun supplier(id: String?) = suppliers.firstOrNull { it.id == id }
    fun place(id: String?) = places.firstOrNull { it.id == id }
    fun order(id: String?) = orders.firstOrNull { it.id == id }
    fun placesOf(supplierId: String) = places.filter { it.supplierId == supplierId }
    fun linksOf(supplierId: String) = links.filter { it.supplierId == supplierId }
    fun ordersOf(supplierId: String) = orders.filter { it.supplierId == supplierId }
    fun link(supplierId: String, itemId: String) = links.firstOrNull { it.supplierId == supplierId && it.itemId == itemId }
    /** Every supplier an item can come from. */
    fun suppliersOf(itemId: String) = links.filter { it.itemId == itemId }.mapNotNull { supplier(it.supplierId) }

    /** Adds a supplier with its places and the items you buy from them; returns its id (the server keeps the same one). */
    fun add(supplier: Supplier, newPlaces: List<SupplierPlace>, itemPrices: Map<String, Long?>): String {
        val id = newId()
        val added = supplier.copy(id = id)
        val placesAdded = newPlaces.map { it.copy(id = newId(), supplierId = id) }
        val linksAdded = itemPrices.map { (itemId, price) -> SupplierItem(id, itemId, priceCents = price) }
        suppliers += added
        places += placesAdded
        links += linksAdded
        send { repository, restaurant ->
            repository.createSupplier(restaurant, request(added, id))
            placesAdded.forEach { repository.saveSupplierPlace(restaurant, id, null, request(it, it.id)) }
            linksAdded.forEach { repository.saveSupplierItem(restaurant, id, it.itemId, request(it)) }
        }
        return id
    }

    /** Changes a supplier's details; a new picture or banner alone stays in the app (MOCK) and isn't sent. */
    fun update(supplier: Supplier) {
        val before = supplier(supplier.id) ?: return
        replace(suppliers, { it.id == supplier.id }) { supplier }
        if (before.copy(photo = null, cover = null) != supplier.copy(photo = null, cover = null)) {
            send { repository, restaurant -> repository.updateSupplier(restaurant, supplier.id, request(supplier)) }
        }
    }

    /** A supplier the server just added (from the item form): shown here too, nothing more to send. */
    fun added(dto: SupplierDto) {
        if (supplier(dto.id) != null) return
        suppliers += Supplier(dto.id, dto.name, dto.contactName.orEmpty(), dto.phone.orEmpty(), dto.email.orEmpty(), dto.notes.orEmpty(), dto.active,
            address = dto.address.orEmpty())
    }

    fun setActive(supplierId: String, active: Boolean) { supplier(supplierId)?.let { update(it.copy(active = active)) } }

    fun savePlace(place: SupplierPlace) {
        if (places.any { it.id == place.id }) {
            replace(places, { it.id == place.id }) { place }
            send { repository, restaurant -> repository.saveSupplierPlace(restaurant, place.supplierId, place.id, request(place)) }
        } else {
            val added = place.copy(id = place.id.ifBlank { newId() })
            places += added
            send { repository, restaurant -> repository.saveSupplierPlace(restaurant, added.supplierId, null, request(added, added.id)) }
        }
    }

    fun removePlace(placeId: String) {
        val place = place(placeId) ?: return
        places.removeAll { it.id == placeId }
        send { repository, restaurant -> repository.removeSupplierPlace(restaurant, place.supplierId, placeId) }
    }

    /** Links items to a supplier (items already linked keep their code and price). */
    fun linkItems(supplierId: String, itemPrices: Map<String, Long?>) {
        itemPrices.forEach { (itemId, price) -> if (link(supplierId, itemId) == null) addLink(SupplierItem(supplierId, itemId, priceCents = price)) }
    }

    /** One more item bought from a supplier. */
    fun addLink(link: SupplierItem) {
        links += link
        send { repository, restaurant -> repository.saveSupplierItem(restaurant, link.supplierId, link.itemId, request(link)) }
    }

    fun saveLink(link: SupplierItem) {
        replace(links, { it.supplierId == link.supplierId && it.itemId == link.itemId }) { link }
        send { repository, restaurant -> repository.saveSupplierItem(restaurant, link.supplierId, link.itemId, request(link)) }
    }

    fun unlink(supplierId: String, itemId: String) {
        links.removeAll { it.supplierId == supplierId && it.itemId == itemId }
        send { repository, restaurant -> repository.removeSupplierItem(restaurant, supplierId, itemId) }
    }

    fun placeOrder(order: PurchaseOrder): PurchaseOrder {
        val placed = order.copy(id = newId(), number = "PO-" + Uuid.random().toHexString().take(5).uppercase())
        orders.add(0, placed)
        return placed
    }

    /** Step 2: a delivery came (the first, or the next one of an order that comes in parts), with how much of each item. */
    fun markArrived(orderId: String, arrived: Map<String, Double>, today: LocalDate) = replace(orders, { it.id == orderId }) { order ->
        order.copy(status = PurchaseStatus.ARRIVED, arrivedOn = today,
            lines = order.lines.map { it.copy(counted = null, arrived = (arrived[it.itemId] ?: it.left).coerceAtLeast(0.0)) })
    }

    /** Step 3: what was counted per item (in the item's unit) and the stock place each was put in. */
    fun saveCount(orderId: String, counted: Map<String, Double>, places: Map<String, String>) = replace(orders, { it.id == orderId }) { order ->
        order.copy(status = PurchaseStatus.COUNTED,
            lines = order.lines.map { line -> line.copy(counted = (counted[line.itemId] ?: 0.0).coerceAtLeast(0.0), placeId = places[line.itemId] ?: line.placeId) })
    }

    /** Back to counting (step 2) to change the count before it's confirmed. */
    fun recount(orderId: String) = replace(orders, { it.id == orderId }) { it.copy(status = PurchaseStatus.ARRIVED) }

    /**
     * Step 4: what was counted goes into stock. When everything ordered is in, the order is done; otherwise it waits,
     * partly in stock, for the next delivery.
     */
    fun confirmInStock(orderId: String, today: LocalDate) = replace(orders, { it.id == orderId }) { order ->
        val lines = order.lines.map { it.copy(received = it.received + (it.counted ?: 0.0), counted = null, arrived = null) }
        val complete = lines.all { it.left <= 0.0001 }
        order.copy(status = if (complete) PurchaseStatus.RECEIVED else PurchaseStatus.PARTLY_RECEIVED, lines = lines,
            receivedOn = if (complete) today else order.receivedOn)
    }

    /** Closes an order that came in part: what's in stock stays, the rest isn't expected any more. */
    fun finish(orderId: String, today: LocalDate) = replace(orders, { it.id == orderId }) {
        it.copy(status = PurchaseStatus.RECEIVED, receivedOn = today, lines = it.lines.map { line -> line.copy(counted = null) })
    }

    fun cancel(orderId: String) = replace(orders, { it.id == orderId }) { it.copy(status = PurchaseStatus.CANCELLED) }

    private fun <T> replace(list: MutableList<T>, match: (T) -> Boolean, change: (T) -> T) {
        val index = list.indexOfFirst(match)
        if (index >= 0) list[index] = change(list[index])
    }

    private fun newId() = Uuid.random().toString()
}

// ---- A supplier's catalog: everything they sell, linked to your stock items or not yet ----
//
// Like the "vendor catalog" of restaurant purchasing systems: you load their price list (paste it from a spreadsheet
// or pick a CSV file, the columns are recognised from the header and can be changed), see what else you could buy
// from them, and link a product to one of your stock items when you start buying it. Loading the list again updates
// prices by their code (or name). MOCK like the rest of SupplierBook.

internal data class CatalogProduct(
    val id: String,
    val supplierId: String,
    val name: String,
    val code: String = "",
    val unit: String = "",
    val pack: String = "",
    val priceCents: Long? = null,
    val category: String = "",
    /** The stock item this product is, once you buy it. */
    val linkedItemId: String? = null
)

/** The columns a price list can have. */
internal enum class CatalogField(val label: String) {
    NAME("Name"), CODE("Their code"), UNIT("Unit"), PACK("Pack size"), PRICE("Price"), CATEGORY("Category"), SKIP("Leave out");

    companion object {
        /** Guesses a column from its header ("Product", "SKU", "Price €"...), or null when it isn't a header word. */
        fun guess(header: String): CatalogField? {
            val h = header.trim().lowercase()
            return when {
                h.isEmpty() -> null
                listOf("name", "product", "item", "description", "article name", "emër", "emer", "produkt").any { h == it || h.startsWith("$it ") } -> NAME
                listOf("code", "sku", "ref", "article", "item no", "item number", "kod").any { h.contains(it) } -> CODE
                listOf("unit", "uom", "njësi", "njesi").any { h.contains(it) } -> UNIT
                listOf("pack", "case", "size", "package", "paketim").any { h.contains(it) } -> PACK
                listOf("price", "cost", "€", "eur", "lek", "all", "çmim", "cmim").any { h.contains(it) } -> PRICE
                listOf("category", "group", "kategori").any { h.contains(it) } -> CATEGORY
                else -> null
            }
        }
    }
}

/** What loading a price list did. */
internal data class CatalogImport(val added: Int, val updated: Int, val skipped: Int)

/** A table pasted from a spreadsheet or read from a CSV file: tab, semicolon or comma separated, "quoted" cells kept whole. */
internal fun parseTable(text: String): List<List<String>> {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()
    val first = lines.first()
    val separator = listOf('\t', ';', ',').maxByOrNull { sep -> first.count { it == sep } } ?: ','
    return lines.map { line ->
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == separator && !quoted -> { cells += cell.toString().trim(); cell.clear() }
                else -> cell.append(c)
            }
            i++
        }
        cells += cell.toString().trim()
        cells
    }
}

/** "€3,20", "3.20 EUR", "1 250,00" → cents, or null. */
internal fun priceToCents(text: String): Long? {
    var t = text.trim().replace(Regex("[^0-9,.-]"), "")
    if (t.isEmpty()) return null
    // "1.250,00" / "1,250.00" / "3,20": the last separator is the decimal one.
    val lastComma = t.lastIndexOf(','); val lastDot = t.lastIndexOf('.')
    t = when {
        lastComma > lastDot -> t.replace(".", "").replace(',', '.')
        else -> t.replace(",", "")
    }
    return t.toDoubleOrNull()?.let { kotlin.math.round(it * 100).toLong() }?.takeIf { it >= 0 }
}

internal fun SupplierBook.catalogOf(supplierId: String) = catalog.filter { it.supplierId == supplierId }

/** Loads rows (already in [CatalogField] order per row) into a supplier's catalog: new products are added, known ones (same code, else same name) get the new details. */
internal fun SupplierBook.importCatalog(supplierId: String, rows: List<Map<CatalogField, String>>): CatalogImport {
    var added = 0; var updated = 0; var skipped = 0
    rows.forEach { row ->
        val name = row[CatalogField.NAME].orEmpty().trim()
        if (name.isEmpty()) { skipped++; return@forEach }
        val code = row[CatalogField.CODE].orEmpty().trim()
        val existing = catalog.indexOfFirst { it.supplierId == supplierId && ((code.isNotEmpty() && it.code.equals(code, true)) || (code.isEmpty() && it.name.equals(name, true))) }
        val product = CatalogProduct(if (existing >= 0) catalog[existing].id else kotlin.uuid.Uuid.random().toString(), supplierId, name, code,
            row[CatalogField.UNIT].orEmpty().trim(), row[CatalogField.PACK].orEmpty().trim(), row[CatalogField.PRICE]?.let(::priceToCents),
            row[CatalogField.CATEGORY].orEmpty().trim(), if (existing >= 0) catalog[existing].linkedItemId else null)
        if (existing >= 0) { catalog[existing] = product; updated++ } else { catalog += product; added++ }
        // A product that's already one of your items keeps its link's code and price up to date.
        product.linkedItemId?.let { itemId -> link(supplierId, itemId)?.let { saveLink(it.copy(code = code.ifEmpty { it.code }, priceCents = product.priceCents ?: it.priceCents)) } }
    }
    return CatalogImport(added, updated, skipped)
}

/** Starts buying a catalog product as one of your stock items: the product is linked and the item gets this supplier's code and price. */
internal fun SupplierBook.linkProduct(productId: String, itemId: String) {
    val index = catalog.indexOfFirst { it.id == productId }
    if (index < 0) return
    val product = catalog[index]
    catalog[index] = product.copy(linkedItemId = itemId)
    val existing = link(product.supplierId, itemId)
    if (existing == null) addLink(SupplierItem(product.supplierId, itemId, product.code, product.priceCents))
    else saveLink(existing.copy(code = product.code.ifEmpty { existing.code }, priceCents = product.priceCents ?: existing.priceCents))
}

internal fun SupplierBook.removeProduct(productId: String) { catalog.removeAll { it.id == productId } }

/**
 * A catalog product as a new stock item, ready for the item form: name, unit, cost per unit, the supplier and their
 * code filled in (the photo is left to the person). The kind and unit are guessed from the product's unit and category.
 */
internal fun catalogItemDraft(product: CatalogProduct, supplierName: String): StockItemDraft {
    val unit = when (product.unit.trim().lowercase()) {
        "kg", "kilo", "kilogram", "kilograms" -> "KILOGRAM"
        "g", "gr", "gram", "grams" -> "GRAM"
        "l", "lt", "liter", "litre", "liters", "litres" -> "LITER"
        "ml", "milliliter", "millilitre" -> "MILLILITER"
        "bottle", "bottles", "btl" -> "BOTTLE"
        "pack", "packs", "pk" -> "PACK"
        "case", "cases" -> "CASE"
        "tray", "trays" -> "TRAY"
        else -> "EACH"
    }
    val words = (product.category + " " + product.name + " " + supplierName).lowercase()
    val kind = when {
        listOf("wine", "gin", "vodka", "raki", "rakia", "limoncello", "beer", "vermouth", "spirits", "prosecco", "shiraz", "rosé", "rose").any { it in words } -> "ALCOHOL"
        listOf("juice", "water", "soda", "tea", "drink", "cola", "beverage", "tonic").any { it in words } -> "BEVERAGE"
        listOf("straw", "cup", "box", "cutlery", "bag", "packaging", "napkin", "container", "lid").any { it in words } -> "PACKAGING"
        listOf("detergent", "cleaner", "gloves", "soap").any { it in words } -> "SUPPLY"
        else -> "INGREDIENT"
    }
    val cost = product.priceCents?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" }.orEmpty()
    return StockItemDraft(
        name = product.name.take(150), itemType = kind, baseUnit = unit, supplierName = supplierName,
        supplierId = product.supplierId.takeIf { SupplierBook.supplier(it) != null }, supplierSku = product.code,
        costText = cost, description = listOfNotNull(product.pack.ifBlank { null }?.let { "Comes as $it" }, product.category.ifBlank { null }).joinToString(" · "),
        filledFrom = "$supplierName's price list"
    )
}
